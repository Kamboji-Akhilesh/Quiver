package com.kamboji.quiver.move

import com.kamboji.quiver.ui.locale.displayName

import com.kamboji.quiver.ui.locale.tr
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kamboji.quiver.R
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.move.data.MoveEntity
import com.kamboji.quiver.move.data.PointEntity
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.widgets.openShellIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** What the Move screen shows while recording. Published by [MoveService]. */
object MoveRecorder {
    data class Live(
        val moveId: Long,
        val type: MoveType,
        val startedAt: Long,
        val paused: Boolean,
        val distanceM: Double,
        val movingMs: Long,
        val elevationGainM: Double,
        val steps: Int,
        val track: List<Fix>,
        /** Accuracy of the latest fix; null until the first one arrives. */
        val accuracyM: Float?,
        /** Smoothed current speed, for current pace. */
        val speedMps: Double?,
    )

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    /** Id of the activity just finished, so the UI can open its summary once. */
    val finished = MutableStateFlow<Long?>(null)

    internal fun publish(live: Live?) {
        _live.value = live
    }

    fun start(context: Context, type: MoveType) = send(context, ACTION_START) { putExtra(EXTRA_TYPE, type.name) }
    fun pause(context: Context) = send(context, ACTION_PAUSE)
    fun resume(context: Context) = send(context, ACTION_RESUME)
    fun finish(context: Context) = send(context, ACTION_FINISH)
    fun discard(context: Context) = send(context, ACTION_DISCARD)

    private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        val i = Intent(context, MoveService::class.java).setAction(action).apply(extras)
        if (action == ACTION_START) context.startForegroundService(i) else context.startService(i)
    }

    internal const val ACTION_START = "com.kamboji.quiver.move.START"
    internal const val ACTION_PAUSE = "com.kamboji.quiver.move.PAUSE"
    internal const val ACTION_RESUME = "com.kamboji.quiver.move.RESUME"
    internal const val ACTION_FINISH = "com.kamboji.quiver.move.FINISH"
    internal const val ACTION_DISCARD = "com.kamboji.quiver.move.DISCARD"
    internal const val EXTRA_TYPE = "type"
}

/**
 * Records one activity: fused location at 1 Hz (the engine Strava/Google Fit
 * use), as a location-type foreground service so it keeps going with the
 * screen off. Raw points are flushed to Room in small batches — if the OS
 * kills the process, [MoveRecovery] rebuilds the activity from them.
 */
class MoveService : Service(), SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { MoveDatabase.get(this).dao() }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var move: MoveEntity? = null
    private var filter: TrackFilter? = null
    private val buffer = mutableListOf<PointEntity>()
    private var paused = false
    private var resumedNext = false
    private var stepBase: Float? = null
    private var steps = 0
    private var pressureAltM: Double? = null
    private var accuracyM: Float? = null
    private var lastNotifyAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) = result.locations.forEach(::onLocation)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MoveRecorder.ACTION_START -> start(MoveType.valueOf(intent.getStringExtra(MoveRecorder.EXTRA_TYPE) ?: MoveType.Run.name))
            MoveRecorder.ACTION_PAUSE -> setPaused(true)
            MoveRecorder.ACTION_RESUME -> setPaused(false)
            MoveRecorder.ACTION_FINISH -> stop(keep = true)
            MoveRecorder.ACTION_DISCARD -> stop(keep = false)
        }
        // Not sticky: a restarted service would have lost its filter state; recovery handles it instead.
        return START_NOT_STICKY
    }

    private fun start(type: MoveType) {
        if (move != null) return
        ensureChannel()
        startForeground(NOTIF_ID, notification(type, tr(R.string.movesvc_waiting_for_gps)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        val now = System.currentTimeMillis()
        val entity = MoveEntity(type = type.name, startedAt = now)
        move = entity.copy(id = runBlocking(Dispatchers.IO) { dao.insert(entity) })
        filter = TrackFilter(type)
        MovePrefs(this).lastType = type

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
        if (type.countsSteps && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        // A barometer measures climbs far better than GPS altitude; most phones that have one use it this way.
        sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        publish()
    }

    private fun onLocation(loc: Location) {
        val f = filter ?: return
        val m = move ?: return
        accuracyM = if (loc.hasAccuracy()) loc.accuracy else null
        val fix = Fix(
            t = loc.time,
            lat = loc.latitude,
            lon = loc.longitude,
            accM = accuracyM ?: Float.MAX_VALUE,
            altM = pressureAltM ?: loc.altitude.takeIf { loc.hasAltitude() },
            speedMps = loc.speed.takeIf { loc.hasSpeed() },
        )
        if (!paused) {
            if (resumedNext) f.breakSegment()
            f.add(fix)
            buffer += PointEntity(
                moveId = m.id, t = fix.t, lat = fix.lat, lon = fix.lon, accM = fix.accM,
                altM = fix.altM, speedMps = fix.speedMps, resumed = resumedNext,
            )
            resumedNext = false
            if (buffer.size >= FLUSH_EVERY) flush()
        }
        publish()
    }

    private fun setPaused(value: Boolean) {
        if (move == null || paused == value) return
        paused = value
        if (!value) resumedNext = true
        publish(forceNotify = true)
    }

    private fun stop(keep: Boolean) {
        val m = move ?: run { stopSelf(); return }
        fused.removeLocationUpdates(callback)
        sensors.unregisterListener(this)
        val pending = buffer.toList()
        buffer.clear()
        val finishedSteps = steps
        val weight = MovePrefs(this).weightKg
        move = null
        filter = null
        runBlocking(Dispatchers.IO) {
            if (pending.isNotEmpty()) dao.insertPoints(pending)
            if (keep) {
                dao.update(MoveSummary.finish(m, dao.points(m.id), System.currentTimeMillis(), finishedSteps, weight))
            } else {
                dao.delete(m.id)
            }
        }
        if (keep) MoveRecorder.finished.value = m.id
        MoveRecorder.publish(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun flush() {
        val batch = buffer.toList()
        buffer.clear()
        scope.launch { dao.insertPoints(batch) }
    }

    private fun publish(forceNotify: Boolean = false) {
        val m = move ?: return
        val f = filter ?: return
        val live = MoveRecorder.Live(
            moveId = m.id,
            type = m.moveType,
            startedAt = m.startedAt,
            paused = paused,
            distanceM = f.distanceM,
            movingMs = f.movingMs,
            elevationGainM = f.elevationGainM,
            steps = steps,
            track = f.track,
            accuracyM = accuracyM,
            speedMps = if (paused) null else f.currentSpeedMps,
        )
        MoveRecorder.publish(live)
        val now = SystemClock.elapsedRealtime()
        if (forceNotify || now - lastNotifyAt > NOTIFY_EVERY_MS) {
            lastNotifyAt = now
            val text = if (paused) tr(R.string.movesvc_paused_x, MoveFormat.km(f.distanceM))
            else tr(R.string.movesvc_x_x_moving, MoveFormat.km(f.distanceM), MoveFormat.duration(f.movingMs))
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(m.moveType, text))
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0]
                val base = stepBase ?: total.also { stepBase = it }
                steps = (total - base).toInt()
            }
            Sensor.TYPE_PRESSURE -> {
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
                // Light low-pass: raw pressure jitters by ~1 m.
                pressureAltM = pressureAltM?.let { it * 0.9 + alt * 0.1 } ?: alt
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        sensors.unregisterListener(this)
        // Killed without Finish: keep what we have; MoveRecovery closes it next time.
        if (buffer.isNotEmpty()) runBlocking(Dispatchers.IO) { dao.insertPoints(buffer.toList()) }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.movesvc_activity_recording), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(type: MoveType, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, openShellIntent(this, AppKey.Move), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(label: String, action: String, code: Int) = Notification.Action.Builder(
            null, label,
            PendingIntent.getService(this, code, Intent(this, MoveService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(CHANNEL) // never let Android bundle Quiver's notifications — see ExpenseCapture
            .setContentTitle(tr(R.string.movesvc_recording_x, type.displayName.lowercase()))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(if (paused) action(tr(R.string.movesvc_resume), MoveRecorder.ACTION_RESUME, 1) else action(tr(R.string.movesvc_pause), MoveRecorder.ACTION_PAUSE, 1))
            .addAction(action(tr(R.string.movesvc_finish), MoveRecorder.ACTION_FINISH, 2))
            .build()
    }

    private companion object {
        const val CHANNEL = "quiver_move"
        const val NOTIF_ID = 4_100_001
        const val FLUSH_EVERY = 15
        const val NOTIFY_EVERY_MS = 5_000L
    }
}

/** Closes activities whose recording was killed by the OS, from their saved points. */
object MoveRecovery {
    suspend fun finishOrphans(context: Context) {
        if (MoveRecorder.live.value != null) return
        val dao = MoveDatabase.get(context).dao()
        val weight = MovePrefs(context).weightKg
        for (m in dao.unfinished()) {
            val points = dao.points(m.id)
            if (points.isEmpty()) dao.delete(m.id)
            else dao.update(MoveSummary.finish(m, points, points.last().t, steps = 0, weightKg = weight))
        }
    }
}

class MovePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("move", Context.MODE_PRIVATE)

    /** For calories. */
    var weightKg: Double
        get() = prefs.getFloat("weight_kg", 70f).toDouble()
        set(v) = prefs.edit().putFloat("weight_kg", v.toFloat()).apply()

    var lastType: MoveType
        get() = runCatching { MoveType.valueOf(prefs.getString("last_type", null) ?: "") }.getOrDefault(MoveType.Run)
        set(v) = prefs.edit().putString("last_type", v.name).apply()
}

/** Display helpers shared by the notification and the screens. */
object MoveFormat {
    fun km(m: Double): String = tr(R.string.unit_km, "%.2f".format(java.util.Locale.ROOT, m / 1000))

    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** "5:42 /km" */
    fun pace(secPerKm: Long?): String =
        secPerKm?.let { tr(R.string.unit_pace, "%d:%02d".format(it / 60, it % 60)) } ?: tr(R.string.unit_pace_none)

    fun speed(kmh: Double): String = tr(R.string.unit_kmh, "%.1f".format(java.util.Locale.ROOT, kmh))

    /** Rides read in speed, everything else in pace — like Strava. */
    fun rate(type: MoveType, distanceM: Double, movingMs: Long): String =
        if (type == MoveType.Ride) speed(TrackMath.speedKmh(distanceM, movingMs))
        else pace(TrackMath.paceSecPerKm(distanceM, movingMs))
}
