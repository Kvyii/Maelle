package com.kvyii.maelle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SeriesEntity::class, ChapterEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MaelleDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var instance: MaelleDatabase? = null

        fun get(context: Context): MaelleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MaelleDatabase::class.java,
                    "maelle.db",
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
