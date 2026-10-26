package com.kamboji.quiver.move.steps

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.kamboji.quiver.move.MoveRecorder
import com.kamboji.quiver.move.MoveType
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.move.data.MoveEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Logs walks, runs and rides by themselves, like Google Fit: Play services'
 * activity recognition (on the phone, low power — no GPS) reports when an
 * activity starts and stops, and anything long enough becomes a Move activity
 * with its time and steps. No route: recording GPS automatically would cost
 * real battery, so routes stay a manual Start.
 */
object AutoDetect {
    private const val PREF = "auto_detect"

    private val TYPES = mapOf(
        DetectedActivity.WALKING to MoveType.Walk,
        DetectedActivity.RUNNING to MoveType.Run,
        DetectedActivity.ON_BICYCLE to MoveType.Ride,
    )

    /** Shorter than this isn't an activity — it's walking to the kitchen. */
    internal fun minMs(type: MoveType): Long = when (type) {
        MoveType.Run -> 5 * 60_000L
        else -> 10 * 60_000L
    }

    /** Asks Play services for start/stop events. Lost on reboot and app update, so boot re-registers. */
    fun register(context: Context) {
        if (!Steps.hasPermission(context)) return
        val transitions = TYPES.keys.flatMap { type ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(it).build()
            }
        }
        runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
                .addOnFailureListener { Log.w(TAG, "transitions not registered: $it") }
        }.onFailure { Log.w(TAG, "activity recognition unavailable: $it") }
    }

    // Mutable: Play services adds the result to it.
    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, TransitionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    internal suspend fun onTransition(context: Context, activity: Int, enter: Boolean, elapsedNanos: Long) {
        val type = TYPES[activity] ?: return
        val at = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - elapsedNanos) / 1_000_000
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val chip = Steps.readChip(context)
        if (enter) {
            prefs.edit().putString("type", type.name).putLong("at", at).putLong("chip", chip ?: -1).apply()
            return
        }
        if (prefs.getString("type", null) != type.name) return
        val startedAt = prefs.getLong("at", 0)
        val startChip = prefs.getLong("chip", -1)
        prefs.edit().clear().apply()
        if (at - startedAt < minMs(type)) return
        // A manual recording already covers it.
        if (MoveRecorder.live.value != null) return
        val dao = MoveDatabase.get(context).dao()
        if (dao.allFinished().any { it.startedAt < at && (it.endedAt ?: 0) > startedAt }) return
        val steps = if (chip != null && startChip >= 0 && chip >= startChip) (chip - startChip).toInt() else 0
        dao.insert(MoveEntity(type = type.name, startedAt = startedAt, endedAt = at, movingMs = at - startedAt, steps = steps, auto = true))
        Steps.sample(context)
    }

    private const val TAG = "QuiverAutoDetect"
}

class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                result.transitionEvents.forEach {
                    AutoDetect.onTransition(context, it.activityType, it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER, it.elapsedRealTimeNanos)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
