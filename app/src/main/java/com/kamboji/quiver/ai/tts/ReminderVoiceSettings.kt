package com.kamboji.quiver.ai.tts

import android.content.Context
import com.kamboji.quiver.ai.VoiceLang
import com.kamboji.quiver.ai.VoiceLanguages

/**
 * Persists the language used for the reminder-call voice (both Cartesia TTS
 * output and the spoken reschedule commands).
 *
 * This is the single source of truth a future Settings screen writes to —
 * [setLanguage] / [language] are all it needs. Everything downstream
 * ([ReminderSpeaker], [CartesiaTts], [ReminderScript]) already takes the chosen
 * [VoiceLang], so adding the picker is purely a UI change.
 */
class ReminderVoiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_voice", Context.MODE_PRIVATE)

    fun language(): VoiceLang {
        val code = prefs.getString(KEY_LANG, null)
        return VoiceLanguages.firstOrNull { it.code == code } ?: VoiceLanguages.first()
    }

    fun setLanguage(lang: VoiceLang) {
        prefs.edit().putString(KEY_LANG, lang.code).apply()
    }

    private companion object {
        const val KEY_LANG = "language_code"
    }
}
