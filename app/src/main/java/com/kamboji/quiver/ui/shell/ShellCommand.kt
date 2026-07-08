package com.kamboji.quiver.ui.shell

import android.content.Intent
import com.kamboji.quiver.ui.theme.AppKey

/**
 * A one-shot instruction carried into the shell by a launch intent. App
 * shortcuts and the share router use these to land the user on the right
 * screen (optionally opening its composer or the AI panel) instead of the Hub.
 */
data class ShellCommand(
    val target: AppKey? = null,
    val startNew: Boolean = false,
    val openAi: Boolean = false,
    val aiPrefill: String? = null,
    val aiMic: Boolean = false,
    val toast: String? = null,
    /** Distinguishes two identical commands so the shell re-applies both. */
    val nonce: Long = System.currentTimeMillis(),
) {
    fun applyTo(state: QuiverState) {
        target?.let { state.go(it) }
        if (startNew) when (target) {
            AppKey.Notes -> state.notesStartNew = true
            AppKey.Calendar -> state.calendarStartNew = true
            AppKey.Expenses -> state.expensesStartNew = true
            else -> {}
        }
        if (openAi) {
            state.closeOverlays()
            state.aiPrefill = aiPrefill
            state.aiStartMic = aiMic
            state.aiOpen = true
        }
        toast?.let { state.toast(it) }
    }

    companion object {
        // Static app shortcuts (res/xml/shortcuts.xml) target these actions.
        const val ACTION_NEW_NOTE = "com.kamboji.quiver.NEW_NOTE"
        const val ACTION_NEW_TASK = "com.kamboji.quiver.NEW_TASK"
        const val ACTION_ADD_EXPENSE = "com.kamboji.quiver.ADD_EXPENSE"
        const val ACTION_ASK_AI = "com.kamboji.quiver.ASK_AI"

        // Generic "open this screen" used by the share router after it has
        // already written the shared data into a store.
        const val ACTION_OPEN = "com.kamboji.quiver.OPEN"
        const val EXTRA_TARGET = "quiver.target"
        const val EXTRA_START_NEW = "quiver.start_new"
        const val EXTRA_TOAST = "quiver.toast"
        const val EXTRA_AI_PREFILL = "quiver.ai_prefill"
        const val EXTRA_AI_MIC = "quiver.ai_mic"

        fun fromIntent(intent: Intent?): ShellCommand? = when (intent?.action) {
            ACTION_NEW_NOTE -> ShellCommand(AppKey.Notes, startNew = true)
            ACTION_NEW_TASK -> ShellCommand(AppKey.Calendar, startNew = true)
            ACTION_ADD_EXPENSE -> ShellCommand(AppKey.Expenses, startNew = true)
            ACTION_ASK_AI -> ShellCommand(
                openAi = true,
                aiPrefill = intent.getStringExtra(EXTRA_AI_PREFILL),
                aiMic = intent.getBooleanExtra(EXTRA_AI_MIC, false),
            )
            ACTION_OPEN -> ShellCommand(
                target = intent.getStringExtra(EXTRA_TARGET)
                    ?.let { t -> AppKey.entries.firstOrNull { it.name == t } },
                startNew = intent.getBooleanExtra(EXTRA_START_NEW, false),
                toast = intent.getStringExtra(EXTRA_TOAST),
            )
            else -> null
        }
    }
}
