package com.kamboji.quiver.move.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamboji.quiver.move.data.MoveDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * All-day step count, like Google Fit: the step-counter chip counts in hardware
 * whether or not Quiver runs, so Quiver just reads it every 15 minutes (and on
 * app open, and at boot) and files the difference under the right day. No
 * always-on service, no GPS. A recorded run's steps come from the same chip, so
 * they're already inside the day's total — never added twice.
 */
object Steps {
    private const val PREF = "steps"
    private const val WORK = "quiver_steps"
    private val lock = Mutex()

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun hasChip(context: Context) =
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Starts background counting (idempotent). Needs the activity-recognition permission. */
    fun start(context: Context) {
        if (!hasPermission(context) || !hasChip(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build(),
        )
        AutoDetect.register(context)
    }

    /** The chip's running total since boot, or null if it can't be read. */
    suspend fun readChip(context: Context): Long? {
        if (!hasPermission(context)) return null
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    /** Reads the chip and adds what's new to the daily totals. */
    suspend fun sample(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            val total = readChip(context) ?: return@withLock
            val now = System.currentTimeMillis()
            val cur = StepMath.Reading(now, total, Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0))
            val bootAt = now - SystemClock.elapsedRealtime()
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val zone = ZoneId.systemDefault()

            val (steps, from) = if (prefs.contains("at")) {
                StepMath.stepsBetween(StepMath.Reading(prefs.getLong("at", 0), prefs.getLong("total", 0), prefs.getInt("boot", 0)), cur, bootAt)
            } else {
                // First reading ever: only steps since a boot earlier today are certainly today's.
                (if (Instant.ofEpochMilli(bootAt).atZone(zone).toLocalDate() == LocalDate.now(zone)) total else 0L) to bootAt
            }
            val dao = MoveDatabase.get(context).steps()
            StepMath.split(steps, from, now, zone).forEach { (hour, n) -> dao.add(StepMath.key(hour), n) }
            prefs.edit().putLong("at", now).putLong("total", total).putInt("boot", cur.boot).apply()
        }
    }

    fun goal(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("goal", 8_000)

    fun setGoal(context: Context, steps: Int) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("goal", steps).apply()

    /** Weekly target of active minutes (time in recorded or auto-detected activities). 150 = WHO's weekly minimum. */
    fun weeklyMinutes(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("weekly_min", 150)

    fun setWeeklyMinutes(context: Context, minutes: Int) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("weekly_min", minutes).apply()
}

class StepWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Steps.sample(applicationContext)
        return Result.success()
    }
}
