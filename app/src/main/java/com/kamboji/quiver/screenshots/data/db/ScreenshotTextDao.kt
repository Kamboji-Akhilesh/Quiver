package com.kamboji.quiver.screenshots.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotTextDao {

    /** Re-OCR of the same MediaStore id overwrites the old text. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ScreenshotText)

    /**
     * Cheap SQL prefilter by one token — the [ScreenshotSearch] helper does the
     * real multi-token ranking + snippet extraction over these candidates. The
     * cap keeps a huge library from loading entirely into memory.
     */
    @Query("SELECT * FROM screenshot_text WHERE LOWER(text) LIKE :like ORDER BY capturedAt DESC LIMIT 300")
    suspend fun candidates(like: String): List<ScreenshotText>

    @Query("SELECT mediaId FROM screenshot_text")
    suspend fun indexedIds(): List<Long>

    @Query("SELECT COUNT(*) FROM screenshot_text")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshot_text")
    suspend fun count(): Int

    @Query("DELETE FROM screenshot_text WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: Long)
}
