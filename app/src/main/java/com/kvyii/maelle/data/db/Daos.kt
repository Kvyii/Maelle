package com.kvyii.maelle.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Row for the Downloads tab: how much of each series is saved locally. */
data class SeriesDownloadCount(
    val seriesId: Long,
    val name: String,
    val downloaded: Int,
    val total: Int,
    /** Name of the newest (highest-order) chapter that has been downloaded. */
    val lastDownloadedChapter: String?,
)

@Dao
interface SeriesDao {
    // Most-recently-read first; unread-yet series fall back to when they were
    // added, then alphabetical.
    @Query(
        "SELECT * FROM series WHERE inLibrary = 1 " +
            "ORDER BY lastReadAt DESC, addedAt DESC, name COLLATE NOCASE"
    )
    fun observeLibrary(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeSeries(id: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeries(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE url = :url AND apiName = :apiName LIMIT 1")
    suspend fun findByUrl(url: String, apiName: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(series: SeriesEntity): Long

    @Update
    suspend fun update(series: SeriesEntity)

    @Query("UPDATE series SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: Long, inLibrary: Boolean)

    @Query("UPDATE series SET lastReadChapterId = :chapterId, lastReadAt = :readAt WHERE id = :seriesId")
    suspend fun setLastReadChapter(seriesId: Long, chapterId: Long, readAt: Long)

    /** Insert or return the existing row id for this series. */
    @Transaction
    suspend fun upsertReturningId(series: SeriesEntity): Long {
        val existing = findByUrl(series.url, series.apiName)
        if (existing != null) {
            update(series.copy(id = existing.id, addedAt = existing.addedAt))
            return existing.id
        }
        return insert(series)
    }
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId ORDER BY orderIndex DESC")
    fun observeChaptersNewestFirst(seriesId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId ORDER BY orderIndex ASC")
    suspend fun getChaptersOldestFirst(seriesId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Query("SELECT COUNT(*) FROM chapters WHERE seriesId = :seriesId AND readAt IS NOT NULL")
    fun observeReadCount(seriesId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Query("UPDATE chapters SET readAt = :readAt WHERE id = :id")
    suspend fun setRead(id: Long, readAt: Long?)

    @Query("UPDATE chapters SET downloadPath = :path WHERE id = :id")
    suspend fun setDownloadPath(id: Long, path: String?)

    /**
     * Set read state for the pressed chapter and everything older (inclusive).
     * "Below" in the newest-first list = smaller orderIndex.
     */
    @Query(
        "UPDATE chapters SET readAt = :readAt " +
            "WHERE seriesId = :seriesId AND orderIndex <= :orderIndex"
    )
    suspend fun setReadBelow(seriesId: Long, orderIndex: Int, readAt: Long?)

    /**
     * Set read state for the pressed chapter and everything newer (inclusive).
     * "Above" in the newest-first list = larger orderIndex.
     */
    @Query(
        "UPDATE chapters SET readAt = :readAt " +
            "WHERE seriesId = :seriesId AND orderIndex >= :orderIndex"
    )
    suspend fun setReadAbove(seriesId: Long, orderIndex: Int, readAt: Long?)

    @Query(
        "SELECT * FROM chapters WHERE seriesId = :seriesId " +
            "AND readAt IS NULL AND downloadPath IS NULL ORDER BY orderIndex ASC"
    )
    suspend fun getUnreadUndownloaded(seriesId: Long): List<ChapterEntity>

    /** Per-series downloaded-chapter tallies, for the Downloads tab. */
    @Query(
        "SELECT s.id AS seriesId, s.name AS name, " +
            "SUM(CASE WHEN c.downloadPath IS NOT NULL THEN 1 ELSE 0 END) AS downloaded, " +
            "COUNT(c.id) AS total, " +
            "(SELECT d.name FROM chapters d WHERE d.seriesId = s.id AND d.downloadPath IS NOT NULL " +
            "ORDER BY d.orderIndex DESC LIMIT 1) AS lastDownloadedChapter " +
            "FROM series s JOIN chapters c ON c.seriesId = s.id " +
            "GROUP BY s.id HAVING downloaded > 0 ORDER BY s.name COLLATE NOCASE"
    )
    fun observeDownloadCounts(): Flow<List<SeriesDownloadCount>>

    @Query("UPDATE chapters SET readAt = :readAt WHERE seriesId = :seriesId")
    suspend fun markAllRead(seriesId: Long, readAt: Long?)

    /** Replace the chapter list for a series, preserving read/download state by url. */
    @Transaction
    suspend fun syncChapters(seriesId: Long, incoming: List<ChapterEntity>) {
        val existing = getChaptersOldestFirst(seriesId).associateBy { it.url }
        val merged = incoming.map { fresh ->
            val prior = existing[fresh.url]
            if (prior != null) {
                fresh.copy(id = prior.id, readAt = prior.readAt, downloadPath = prior.downloadPath)
            } else fresh
        }
        insertAll(merged.filter { existing[it.url] == null })
        merged.filter { existing[it.url] != null }.forEach { update(it) }
    }
}
