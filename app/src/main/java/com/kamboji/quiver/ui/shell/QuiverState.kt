package com.kamboji.quiver.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kamboji.quiver.ui.theme.AppKey

enum class ToastKind { Success, Error, Info }

data class ToastMsg(val text: String, val kind: ToastKind, val id: Long)

/** Full-screen call-alert phase. */
enum class CallPhase { Ringing, Reading }

/**
 * In-memory UI state for the single-surface shell: which app is showing, which
 * overlays are open, theme, and the transient toast. Navigation state only —
 * durable data lives in the existing stores/ViewModels.
 */
class QuiverState {
    var dark by mutableStateOf(true)
    var app by mutableStateOf(AppKey.Hub)

    /** Screenshots sub-screen: "home" or "history". */
    var screenshotsScreen by mutableStateOf("home")

    var searchOpen by mutableStateOf(false)
    var aiOpen by mutableStateOf(false)
    var launcherOpen by mutableStateOf(false)
    var call by mutableStateOf<CallPhase?>(null)
    var callTitle by mutableStateOf("Reminder")
    var toast by mutableStateOf<ToastMsg?>(null)

    /** Opens the full-screen call alert for [title] in its ringing phase. */
    fun startCall(title: String) {
        callTitle = title
        call = CallPhase.Ringing
    }

    /** Bento personalization: tile span (1 or 2 columns) and pinned-to-top. */
    var editMode by mutableStateOf(false)
    val tileSize = mutableStateMapOf(
        AppKey.Screenshots to 2,
        AppKey.Currency to 1,
        AppKey.Calendar to 1,
    )
    val pinned = mutableStateMapOf(
        AppKey.Screenshots to true,
        AppKey.Currency to false,
        AppKey.Calendar to false,
    )

    fun toggleTheme() {
        dark = !dark
    }

    fun closeOverlays() {
        searchOpen = false
        aiOpen = false
        launcherOpen = false
    }

    fun go(target: AppKey) {
        app = target
        screenshotsScreen = "home"
        closeOverlays()
    }

    fun toast(text: String, kind: ToastKind = ToastKind.Success) {
        toast = ToastMsg(text, kind, System.currentTimeMillis())
    }
}
