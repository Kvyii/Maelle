package com.kvyii.maelle.data

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
) {
    val active: Boolean get() = status == DownloadStatus.Running || status == DownloadStatus.Paused
}

/**
 * App-scoped batch download queue. One job per series; each chapter gets
 * [ATTEMPTS_PER_CHAPTER] tries with backoff. Pause gates between chapters,
 * stop cancels the job — chapters already saved always stay saved.
 * Both the series screen banner and the Downloads tab observe [downloads].
 */
class DownloadManager(
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _downloads = MutableStateFlow<Map<Long, SeriesDownload>>(emptyMap())
    val downloads: StateFlow<Map<Long, SeriesDownload>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<Long, Job>()
    private val pauseFlags = mutableMapOf<Long, MutableStateFlow<Boolean>>()

    /** Start downloading every unread, not-yet-saved chapter of a series. */
    fun start(seriesId: Long) {
        if (jobs[seriesId]?.isActive == true) return
        val pauseFlag = MutableStateFlow(false)
        pauseFlags[seriesId] = pauseFlag

        jobs[seriesId] = scope.launch {
            val name = library.seriesName(seriesId) ?: "Series"
            set(seriesId) { SeriesDownload(seriesId, name) }
            try {
                val pending = library.pendingDownloads(seriesId)
                set(seriesId) { it?.copy(total = pending.size) }
                val failed = mutableListOf<String>()

                pending.forEachIndexed { index, chapter ->
                    pauseFlag.first { paused -> !paused } // wait while paused

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

    private fun set(seriesId: Long, transform: (SeriesDownload?) -> SeriesDownload?) {
        _downloads.update { map ->
            val next = transform(map[seriesId])
            if (next == null) map - seriesId else map + (seriesId to next)
        }
    }

    companion object {
        const val ATTEMPTS_PER_CHAPTER = 3
    }
}
