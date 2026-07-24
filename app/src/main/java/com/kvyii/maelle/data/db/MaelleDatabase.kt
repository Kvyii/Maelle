package com.kvyii.maelle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SeriesEntity::class, ChapterEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class MaelleDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var instance: MaelleDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE series ADD COLUMN lastReadScrollOffset INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): MaelleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MaelleDatabase::class.java,
                    "maelle.db",
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
