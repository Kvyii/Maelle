package com.kvyii.maelle.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A novel the user has added to their library. [url] + [apiName] uniquely
 * identify a series across providers.
 */
@Entity(
    tableName = "series",
    indices = [Index(value = ["url", "apiName"], unique = true)],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val apiName: String,
    val name: String,
    val author: String? = null,
    val posterUrl: String? = null,
    val synopsis: String? = null,
    val tags: String? = null,
    val status: String? = null,
    val inLibrary: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long = 0,
    val lastReadChapterId: Long? = null,
    /** When the user last opened a chapter of this series; drives library order. */
    val lastReadAt: Long = 0,
    /** Pixel offset into [lastReadChapterId]'s body, so Resume returns mid-chapter. */
    val lastReadScrollOffset: Int = 0,
)

/**
 * One chapter of a series. [readAt] non-null means read. [downloadPath] non-null
 * means the chapter's content is stored locally for offline reading.
 */
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["seriesId", "url"], unique = true),
        Index(value = ["seriesId"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val url: String,
    val name: String,
    val dateOfRelease: String? = null,
    /** Position in the source's ordering; 0 = first/oldest chapter. */
    val orderIndex: Int,
    val readAt: Long? = null,
    val downloadPath: String? = null,
) {
    val isRead: Boolean get() = readAt != null
    val isDownloaded: Boolean get() = downloadPath != null
}
