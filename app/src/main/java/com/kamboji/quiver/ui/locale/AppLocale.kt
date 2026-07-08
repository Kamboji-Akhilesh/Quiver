package com.kamboji.quiver.ui.locale

import android.content.Context
import android.content.res.Configuration
import com.kamboji.quiver.ai.VoiceLang
import com.kamboji.quiver.ai.VoiceLanguages
import java.util.Locale

/** A selectable UI language. [tag] is the BCP-47 language tag; blank = follow the device. */
enum class AppLang(val tag: String, val label: String) {
    SYSTEM("", "System default"),
    EN("en", "English"),
    HI("hi", "हिन्दी"),
    BN("bn", "বাংলা"),
    TA("ta", "தமிழ்"),
    TE("te", "తెలుగు"),
    MR("mr", "मराठी");

    /** Spoken-language match for voice dictation, falling back to English (India). */
    fun voiceLang(): VoiceLang {
        val code = tag.ifBlank { Locale.getDefault().language }
        return VoiceLanguages.firstOrNull { it.code.substringBefore('-') == code }
            ?: VoiceLanguages.first()
    }
}

/**
 * Per-app UI language, applied by wrapping each Activity's base context with an
 * overridden locale in `attachBaseContext`. Chosen over
 * `AppCompatDelegate.setApplicationLocales` because the app's activities extend
 * ComponentActivity (not AppCompatActivity), and this works identically on every
 * supported API level. The choice persists in the shared prefs the rest of the
 * app already uses.
 */
object AppLocale {
    private const val PREF = "quiver_prefs"
    private const val KEY = "app_lang"

    fun current(context: Context): AppLang {
        val tag = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return AppLang.entries.firstOrNull { it.tag == tag } ?: AppLang.SYSTEM
    }

    /** Persists [lang]; the caller then recreates the activity to re-wrap it. */
    fun set(context: Context, lang: AppLang) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, lang.tag).apply()
    }

    /**
     * Wraps [base] so its resources resolve in the chosen language. Call from an
     * Activity's attachBaseContext. [base] always carries the device's system
     * configuration, so SYSTEM resolves back to the real device locale — this
     * also lets `Locale.setDefault` (used by number/date formatters) revert when
     * the user switches away from a fixed language back to System.
     */
    fun wrap(base: Context): Context {
        val cfg = base.resources.configuration
        val lang = current(base)
        val locale = if (lang == AppLang.SYSTEM) {
            cfg.locales.get(0) ?: Locale.getDefault()
        } else {
            Locale.forLanguageTag(lang.tag)
        }
        Locale.setDefault(locale)
        val config = Configuration(cfg).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
