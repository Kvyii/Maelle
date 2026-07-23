package com.kvyii.maelle.data

import android.content.Context
import com.kvyii.maelle.core.ChapterData
import com.kvyii.maelle.core.EpubResponse
import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.StreamResponse
import com.kvyii.maelle.core.stripHtml
import com.kvyii.maelle.data.db.ChapterEntity
import com.kvyii.maelle.data.db.MaelleDatabase
import com.kvyii.maelle.data.db.SeriesEntity
import com.kvyii.maelle.data.db.SeriesDownloadCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Single entry point the UI uses for search, library, chapters, and downloads. */
class LibraryRepository(context: Context, private val settings: SettingsRepository) {
    private val db = MaelleDatabase.get(context)
    private val seriesDao = db.seriesDao()
    private val chapterDao = db.chapterDao()
    private val internalDownloadsDir = File(context.filesDir, "chapters")

    /** Resolve the configured download directory, falling back to internal storage. */
    private suspend fun downloadsDir(): File {
        val custom = settings.readerPreferences.first().downloadPath
        val dir = if (custom.isBlank()) internalDownloadsDir else File(custom)
        return if (dir.mkdirs() || dir.isDirectory) dir else internalDownloadsDir.apply { mkdirs() }
    }

    fun observeLibrary(): Flow<List<SeriesEntity>> = seriesDao.observeLibrary()
    fun observeSeries(id: Long): Flow<SeriesEntity?> = seriesDao.observeSeries(id)
    fun observeChapters(seriesId: Long): Flow<List<ChapterEntity>> =
        chapterDao.observeChaptersNewestFirst(seriesId)
    fun observeReadCount(seriesId: Long): Flow<Int> = chapterDao.observeReadCount(seriesId)

    suspend fun search(providerName: String, query: String): List<SearchResponse> =
        withContext(Dispatchers.IO) {
            Providers.byName(providerName)?.search(query).orEmpty()
        }

    /**
     * Load a series from its provider, upsert it and its chapters into the DB,
     * and return the local series id. [addToLibrary] controls the library flag.
     */
    suspend fun openSeries(
        apiName: String,
        url: String,
        addToLibrary: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val api = Providers.byName(apiName) ?: error("Unknown provider $apiName")
        val load = api.load(url) ?: error("Failed to load $url")

        val seriesId = seriesDao.upsertReturningId(load.toEntity(apiName, addToLibrary))
        val chapters = load.chapters().mapIndexed { index, ch ->
            ChapterEntity(
                seriesId = seriesId,
                url = ch.url,
                name = ch.name,
                dateOfRelease = ch.dateOfRelease,
                orderIndex = index,
            )
        }
        chapterDao.syncChapters(seriesId, chapters)
        if (addToLibrary) seriesDao.setInLibrary(seriesId, true)
        seriesId
    }

    suspend fun setInLibrary(seriesId: Long, inLibrary: Boolean) =
        seriesDao.setInLibrary(seriesId, inLibrary)

    suspend fun setChapterRead(chapterId: Long, read: Boolean) =
        chapterDao.setRead(chapterId, if (read) System.currentTimeMillis() else null)

    /**
     * Set read state for the pressed chapter plus all older ([below]=true) or
     * all newer ([below]=false) chapters — always inclusive of the pressed one.
     */
    suspend fun markRange(seriesId: Long, orderIndex: Int, below: Boolean, read: Boolean) {
        val readAt = if (read) System.currentTimeMillis() else null
        if (below) {
            chapterDao.setReadBelow(seriesId, orderIndex, readAt)
        } else {
            chapterDao.setReadAbove(seriesId, orderIndex, readAt)
        }
    }

    suspend fun markAllRead(seriesId: Long, read: Boolean) =
        chapterDao.markAllRead(seriesId, if (read) System.currentTimeMillis() else null)

    /** Fetch and clean a chapter's text, using the cached file when downloaded. */
    suspend fun chapterText(chapterId: Long): String = withContext(Dispatchers.IO) {
        val chapter = chapterDao.getChapter(chapterId) ?: error("No chapter $chapterId")
        chapter.downloadPath?.let { path ->
            val file = File(path)
            if (file.exists()) return@withContext file.readText()
        }
        downloadChapter(chapter)
    }

    /** Download one chapter to local storage and record its path. */
    suspend fun downloadChapter(chapter: ChapterEntity): String = withContext(Dispatchers.IO) {
        val series = seriesDao.getSeries(chapter.seriesId) ?: error("No series")
        val api = Providers.byName(series.apiName) ?: error("Unknown provider ${series.apiName}")
        val raw = api.loadHtml(chapter.url) ?: error("No content for ${chapter.url}")
        val cleaned = stripHtml(raw, chapter.name, chapter.orderIndex, stripAuthorNotes = true)

        val file = File(downloadsDir(), "${chapter.seriesId}_${chapter.id}.html")
        file.writeText(cleaned)
        chapterDao.setDownloadPath(chapter.id, file.absolutePath)
        cleaned
    }

    /** Unread, not-yet-downloaded chapters, oldest first — the batch-download queue. */
    suspend fun pendingDownloads(seriesId: Long): List<ChapterEntity> =
        chapterDao.getUnreadUndownloaded(seriesId)

    suspend fun seriesName(seriesId: Long): String? = seriesDao.getSeries(seriesId)?.name

    fun observeDownloadCounts(): Flow<List<SeriesDownloadCount>> =
        chapterDao.observeDownloadCounts()

    suspend fun undownloadChapter(chapterId: Long) = withContext(Dispatchers.IO) {
        val chapter = chapterDao.getChapter(chapterId) ?: return@withContext
        chapter.downloadPath?.let { File(it).delete() }
        chapterDao.setDownloadPath(chapterId, null)
    }

    suspend fun setLastReadChapter(seriesId: Long, chapterId: Long) =
        seriesDao.setLastReadChapter(seriesId, chapterId)

    private fun LoadResponse.chapters(): List<ChapterData> = when (this) {
        is StreamResponse -> data
        is EpubResponse -> emptyList()
        else -> emptyList()
    }

    private fun LoadResponse.toEntity(apiName: String, inLibrary: Boolean) = SeriesEntity(
        url = url,
        apiName = apiName,
        name = name,
        author = author,
        posterUrl = posterUrl,
        synopsis = synopsis,
        tags = tags?.joinToString(", "),
        status = status?.name,
        inLibrary = inLibrary,
        lastCheckedAt = System.currentTimeMillis(),
    )
}
