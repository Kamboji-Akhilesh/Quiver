package com.kamboji.quiver.ai.agent

import com.kamboji.quiver.ui.locale.tr
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.kamboji.quiver.R
import com.kamboji.quiver.ai.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs a [QuiverAgent] request as a foreground service so it keeps working even
 * if the user closes the app right after asking. Progress is mirrored to
 * [AgentBus] (for the in-app UI) and to an ongoing notification (for when the app
 * is gone); the final result replaces it with a dismissible notification.
 */
class AiAgentService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(tr(R.string.agentsvc_quiver_ai), tr(R.string.agentsvc_working_on_your_request), ongoing = true))

        if (text.isEmpty()) { finishUp(); return START_NOT_STICKY }

        val modelPath = ModelManager(this).installedFile()?.absolutePath
        if (modelPath == null) {
            AgentBus.error(tr(R.string.agentsvc_set_up_an_ai_model))
            notify(tr(R.string.agentsvc_quiver_ai), tr(R.string.agentsvc_no_ai_model_installed_yet), ongoing = false)
            finishUp()
            return START_NOT_STICKY
        }

        AgentBus.start(text)
        scope.launch {
            try {
                val result = QuiverAgent(applicationContext, modelPath).run(
                    text,
                    progress = { status ->
                        AgentBus.status(status)
                        notify(tr(R.string.agentsvc_quiver_ai), status, ongoing = true)
                    },
                    log = { line -> AgentBus.log(line) },
                )
                AgentBus.done(result.reply)
                notify(tr(R.string.agentsvc_quiver_ai), result.reply.lineSequence().firstOrNull() ?: tr(R.string.agentsvc_done), ongoing = false)
            } catch (e: Exception) {
                AgentBus.error(tr(R.string.agentsvc_sorry_that_didn_t_work, e.message ?: e.javaClass.simpleName))
                notify(tr(R.string.agentsvc_quiver_ai), tr(R.string.agentsvc_couldn_t_finish_that_request), ongoing = false)
            } finally {
                finishUp()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishUp() {
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notify(title: String, text: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(title, text, ongoing))
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = open?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(CHANNEL) // never let Android bundle Quiver's notifications — see ExpenseCapture
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.agentsvc_quiver_ai_tasks), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "quiver_ai_tasks"
        private const val NOTIF_ID = 3_100_001
        private const val EXTRA_TEXT = "text"

        /** Kicks off an agent request that survives the app being closed. */
        fun start(context: Context, text: String) {
            val intent = Intent(context, AiAgentService::class.java).putExtra(EXTRA_TEXT, text)
            context.startForegroundService(intent)
        }
    }
}
