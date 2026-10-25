package com.kamboji.quiver.move.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.kamboji.quiver.move.Fix
import com.kamboji.quiver.move.MoveType
import kotlinx.coroutines.flow.Flow

/** One recorded activity. [endedAt] is null while recording (or if the app died mid-record). */
@Entity(tableName = "moves")
data class MoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceM: Double = 0.0,
    val movingMs: Long = 0,
    val elevationGainM: Double = 0.0,
    val steps: Int = 0,
    val calories: Int = 0,
    /** Moving time of each completed km, comma-separated. */
    val splitsMs: String = "",
    /** A thinned "lat,lon;lat,lon…" route for list thumbnails (no point table read). */
    val preview: String = "",
) {
    val moveType: MoveType get() = MoveType.valueOf(type)
    val elapsedMs: Long get() = (endedAt ?: startedAt) - startedAt
    val splits: List<Long> get() = splitsMs.split(',').mapNotNull { it.toLongOrNull() }
}

/**
 * Every raw fix, unfiltered. Keeping raw points (not just totals) means the
 * whole history can be recomputed if [com.kamboji.quiver.move.TrackTuning] is
 * recalibrated against Strava later.
 */
@Entity(
    tableName = "move_points",
    foreignKeys = [ForeignKey(MoveEntity::class, ["id"], ["moveId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("moveId")],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moveId: Long,
    val t: Long,
    val lat: Double,
    val lon: Double,
    val accM: Float,
    val altM: Double?,
    val speedMps: Float?,
    /** A manual pause ended right before this point. */
    val resumed: Boolean = false,
) {
    fun toFix() = Fix(t, lat, lon, accM, altM, speedMps)
}

@Dao
interface MoveDao {
    @Insert suspend fun insert(move: MoveEntity): Long
    @Update suspend fun update(move: MoveEntity)
    @Insert suspend fun insertPoints(points: List<PointEntity>)

    @Query("SELECT * FROM moves WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finished(): Flow<List<MoveEntity>>

    @Query("SELECT * FROM moves WHERE id = :id")
    suspend fun byId(id: Long): MoveEntity?

    @Query("SELECT * FROM moves WHERE endedAt IS NULL")
    suspend fun unfinished(): List<MoveEntity>

    @Query("SELECT * FROM move_points WHERE moveId = :moveId ORDER BY t")
    suspend fun points(moveId: Long): List<PointEntity>

    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM moves WHERE endedAt IS NOT NULL AND startedAt >= :since")
    fun distanceSince(since: Long): Flow<Double>

    @Query("DELETE FROM moves WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Separate from the screenshots database so neither needs a migration for the other. */
@Database(entities = [MoveEntity::class, PointEntity::class], version = 1, exportSchema = false)
abstract class MoveDatabase : RoomDatabase() {
    abstract fun dao(): MoveDao

    companion object {
        @Volatile private var instance: MoveDatabase? = null

        fun get(context: Context): MoveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MoveDatabase::class.java, "move.db")
                .build().also { instance = it }
        }
    }
}
