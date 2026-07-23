package com.kvyii.maelle.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kvyii.maelle.MainActivity
import com.kvyii.maelle.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadStatus { Running, Paused, Cancelled, Finished }

data class SeriesDownload(
    val seriesId: Long,
    val seriesName: String,
    val done: Int = 0,
    /** Null until the pending-chapter list has been resolved. */
    val total: Int? = null,
    val failed: List<String> = emptyList(),
    val status: DownloadStatus = DownloadStatus.Running,
    /** Chapter currently being fetched (or last fetched), for display. */
    val currentChapter: String? = null,
) {
    val active: Boolean get() = status == DownloadStatus.Running || status == DownloadStatus.Paused
}

/**
 * App-scoped batch download queue. One job per series; each chapter gets
 * [ATTEMPTS_PER_CHAPTER] tries with backoff. Pause gates between chapters,
 * stop cancels the job — chapters already saved always stay saved.
 *
 * While anything is active a foreground [DownloadService] runs and this manager
 * publishes an ongoing progress notification, so downloads survive the app being
 * minimized. Both the series screen banner and the Downloads tab observe
 * [downloads].
 */
class DownloadManager(
    private val context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _downloads = MutableStateFlow<Map<Long, SeriesDownload>>(emptyMap())
    val downloads: StateFlow<Map<Long, SeriesDownload>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<Long, Job>()
    private val pauseFlags = mutableMapOf<Long, MutableStateFlow<Boolean>>()

    init {
        // Keep the notification and foreground service in sync with the queue.
        scope.launch {
            downloads.collect { syncForeground(it) }
        }
    }

    /**
     * Start downloading unread, not-yet-saved chapters of a series, oldest
     * first. [limit] caps how many are queued (e.g. next 5/10/25); null = all.
     */
    fun start(seriesId: Long, limit: Int? = null) {
        if (jobs[seriesId]?.isActive == true) return
        val pauseFlag = MutableStateFlow(false)
        pauseFlags[seriesId] = pauseFlag

        jobs[seriesId] = scope.launch {
            val name = library.seriesName(seriesId) ?: "Series"
            set(seriesId) { SeriesDownload(seriesId, name) }
            try {
                val pending = library.pendingDownloads(seriesId)
                    .let { if (limit != null) it.take(limit) else it }
                set(seriesId) { it?.copy(total = pending.size) }
                val failed = mutableListOf<String>()

                pending.forEachIndexed { index, chapter ->
                    pauseFlag.first { paused -> !paused } // wait while paused
                    set(seriesId) { it?.copy(currentChapter = chapter.name) }

                    var succeeded = false
                    for (attempt in 1..ATTEMPTS_PER_CHAPTER) {
                        try {
                            library.downloadChapter(chapter)
                            succeeded = true
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (attempt < ATTEMPTS_PER_CHAPTER) delay(1000L * attempt)
                        }
                    }
                    if (!succeeded) failed += chapter.name
                    set(seriesId) { it?.copy(done = index + 1, failed = failed.toList()) }
                }
                set(seriesId) { it?.copy(status = DownloadStatus.Finished) }
            } catch (e: CancellationException) {
                set(seriesId) {
                    it?.copy(status = DownloadStatus.Cancelled, total = it.total ?: it.done)
                }
                throw e
            }
        }
    }

    fun pause(seriesId: Long) {
        pauseFlags[seriesId]?.value = true
        set(seriesId) { it?.copy(status = DownloadStatus.Paused) }
    }

    fun resume(seriesId: Long) {
        pauseFlags[seriesId]?.value = false
        set(seriesId) { it?.copy(status = DownloadStatus.Running) }
    }

    fun stop(seriesId: Long) {
        jobs[seriesId]?.cancel()
    }

    /** Remove a finished/cancelled entry from the list. */
    fun clear(seriesId: Long) {
        if (jobs[seriesId]?.isActive != true) {
            _downloads.update { it - seriesId }
            pauseFlags.remove(seriesId)
        }
    }

    // --- Notification actions (whole queue) ---

    fun pauseAll() = activeSeriesIds().forEach { pause(it) }
    fun resumeAll() = activeSeriesIds().forEach { resume(it) }
    fun stopAll() = activeSeriesIds().forEach { stop(it) }

    private fun activeSeriesIds(): List<Long> =
        _downloads.value.filterValues { it.active }.keys.toList()

    private fun set(seriesId: Long, transform: (SeriesDownload?) -> SeriesDownload?) {
        _downloads.update { map ->
            val next = transform(map[seriesId])
            if (next == null) map - seriesId else map + (seriesId to next)
        }
    }

    // --- Foreground service + notification ---

    private fun syncForeground(state: Map<Long, SeriesDownload>) {
        val active = state.values.filter { it.active }
        if (active.isNotEmpty()) {
            DownloadService.start(context)
            notify(buildNotification(context))
        } else {
            DownloadService.stop(context)
            NotificationManagerCompat.from(context).cancel(DownloadService.NOTIFICATION_ID)
        }
    }

    private fun notify(notification: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            nm.areNotificationsEnabled()
        ) {
            try {
                nm.notify(DownloadService.NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted; downloads still run.
            }
        }
    }

    /** Build the ongoing progress notification from the current queue state. */
    fun buildNotification(ctx: Context): Notification {
        ensureChannel(ctx)
        val active = _downloads.value.values.filter { it.active }
        val paused = active.isNotEmpty() && active.all { it.status == DownloadStatus.Paused }

        val done = active.sumOf { it.done }
        val total = active.sumOf { it.total ?: 0 }
        val indeterminate = total == 0

        val title = when {
            active.size > 1 -> "Downloading ${active.size} series"
            else -> active.firstOrNull()?.seriesName ?: "Downloading"
        }
        val text = when {
            paused -> "Paused"
            indeterminate -> "Preparing…"
            else -> "$done / $total chapters"
        }

        val contentIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setProgress(if (indeterminate) 0 else total, done, indeterminate)

        if (paused) {
            builder.addAction(0, "Resume", action(ctx, DownloadActionReceiver.ACTION_RESUME_ALL))
        } else {
            builder.addAction(0, "Pause", action(ctx, DownloadActionReceiver.ACTION_PAUSE_ALL))
        }
        builder.addAction(0, "Stop", action(ctx, DownloadActionReceiver.ACTION_STOP_ALL))

        return builder.build()
    }

    private fun action(ctx: Context, actionName: String): PendingIntent {
        val intent = Intent(ctx, DownloadActionReceiver::class.java).setAction(actionName)
        return PendingIntent.getBroadcast(
            ctx, actionName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Chapter download progress" },
                )
            }
        }
    }

    companion object {
        const val ATTEMPTS_PER_CHAPTER = 3
        private const val CHANNEL_ID = "downloads"
    }
}
