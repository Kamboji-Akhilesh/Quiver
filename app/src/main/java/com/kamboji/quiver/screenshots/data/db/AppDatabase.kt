package com.kamboji.quiver.screenshots.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryItem::class, ScreenshotText::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun screenshotTextDao(): ScreenshotTextDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v2 adds the OCR search index. Additive migration — existing deletion
        // history is preserved (no destructive fallback, which would wipe trash).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `screenshot_text` (" +
                        "`mediaId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `indexedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`mediaId`))",
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screenshot_cleaner_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

