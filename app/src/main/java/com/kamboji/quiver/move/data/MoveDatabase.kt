package com.kamboji.quiver.move.data

import android.content.Context
import androidx.room.Dao
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.Upsert
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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
    /** Logged by activity recognition (no GPS, so no route or distance) rather than recorded. */
    @ColumnInfo(defaultValue = "0") val auto: Boolean = false,
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

    @Query("SELECT * FROM moves WHERE endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun allFinished(): List<MoveEntity>

    /** Restores a backed-up activity under a fresh id. */
    @Transaction
    suspend fun insertWithPoints(move: MoveEntity, points: List<PointEntity>) {
        val id = insert(move.copy(id = 0))
        insertPoints(points.map { it.copy(id = 0, moveId = id) })
    }
}

/** Steps in one local clock hour ("2026-10-26T13"), from the phone's step counter (move/steps). */
@Entity(tableName = "step_hours")
data class StepHour(@PrimaryKey val hour: String, val steps: Long) {
    val day: String get() = hour.substring(0, 10)
}

@Dao
interface StepsDao {
    @Query("SELECT * FROM step_hours WHERE hour = :hour") fun get(hour: String): StepHour?
    /** Hours from the start of [fromDay] up to (not including) [toDay]. */
    @Query("SELECT * FROM step_hours WHERE hour >= :fromDay AND hour < :toDay ORDER BY hour")
    fun between(fromDay: String, toDay: String): Flow<List<StepHour>>
    @Query("SELECT * FROM step_hours WHERE hour >= :fromDay AND hour < :toDay ORDER BY hour")
    suspend fun betweenOnce(fromDay: String, toDay: String): List<StepHour>
    @Upsert fun put(row: StepHour)

    @Transaction
    fun add(hour: String, steps: Long) = put(StepHour(hour, (get(hour)?.steps ?: 0) + steps))
}

/** Separate from the screenshots database so neither needs a migration for the other. */
@Database(entities = [MoveEntity::class, PointEntity::class, StepHour::class], version = 2, exportSchema = false)
abstract class MoveDatabase : RoomDatabase() {
    abstract fun dao(): MoveDao
    abstract fun steps(): StepsDao

    companion object {
        @Volatile private var instance: MoveDatabase? = null

        /** v2: hourly step totals, and auto-detected activities. */
        internal val V1_TO_V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS step_hours (hour TEXT NOT NULL PRIMARY KEY, steps INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE moves ADD COLUMN auto INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): MoveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MoveDatabase::class.java, "move.db")
                .addMigrations(V1_TO_V2)
                .build().also { instance = it }
        }
    }
}
