package com.kamboji.quiver.ai.tts

/**
 * The words the reminder call speaks, per language. English and Hindi are
 * provided; any other language falls back to English copy (and English voice)
 * so nothing is ever spoken in a half-translated mix.
 *
 * When the Settings language picker lands, adding a language is just one more
 * entry in [PHRASES] — the rest of the pipeline already threads the language
 * through.
 */
object ReminderScript {

    private data class Phrases(
        val greeting: String,
        val event: (String) -> String,
        val task: (String) -> String,
        val prompt: String,
        val snoozeConfirm: (Int) -> String,
        val didntCatch: String,
        val howToReschedule: String,
    )

    private val PHRASES: Map<String, Phrases> = mapOf(
        "en" to Phrases(
            greeting = "Hi! This is your Quiver reminder.",
            event = { "Your event: $it." },
            task = { "Your task: $it." },
            prompt = "Tap the mic to snooze or reschedule by voice, or pick a time below.",
            snoozeConfirm = { "Okay, I'll remind you again in $it minutes." },
            didntCatch = "I didn't catch that. Please try again.",
            howToReschedule = "Say something like: remind me in 15 minutes.",
        ),
        "hi" to Phrases(
            greeting = "नमस्ते! यह आपका क्विवर रिमाइंडर है।",
            event = { "आपका इवेंट: $it।" },
            task = { "आपका काम: $it।" },
            prompt = "दोबारा याद दिलाने या समय बदलने के लिए माइक दबाएँ, या नीचे से समय चुनें।",
            snoozeConfirm = { "ठीक है, मैं आपको $it मिनट में फिर याद दिलाऊँगा।" },
            didntCatch = "माफ़ कीजिए, समझ नहीं आया। कृपया दोबारा कोशिश करें।",
            howToReschedule = "ऐसे कहें: मुझे 15 मिनट में याद दिलाओ।",
        ),
    )

    /** The base language that actually has copy for [code] ("ta-IN" -> "en"). */
    fun effectiveLanguage(code: String): String {
        val base = code.substringBefore('-').lowercase()
        return if (PHRASES.containsKey(base)) base else "en"
    }

    /** The full spoken message for an entry, in [code]'s effective language. */
    fun full(title: String, isTask: Boolean, code: String): String {
        val p = PHRASES.getValue(effectiveLanguage(code))
        val body = if (isTask) p.task(title) else p.event(title)
        return listOf(p.greeting, body, body, p.prompt).joinToString(" ")
    }

    fun snoozeConfirm(minutes: Int, code: String): String =
        PHRASES.getValue(effectiveLanguage(code)).snoozeConfirm(minutes)

    fun didntCatch(code: String): String = PHRASES.getValue(effectiveLanguage(code)).didntCatch

    fun howToReschedule(code: String): String = PHRASES.getValue(effectiveLanguage(code)).howToReschedule
}
