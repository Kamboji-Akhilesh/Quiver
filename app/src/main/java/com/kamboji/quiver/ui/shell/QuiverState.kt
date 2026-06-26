package com.kamboji.quiver.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kamboji.quiver.ui.theme.AppKey

enum class ToastKind { Success, Error, Info }

data class ToastMsg(val text: String, val kind: ToastKind, val id: Long)

/** Which bottom sheet is open, if any. */
sealed interface Sheet {
    /** Custom auto-delete delay picker (screenshots). */
    data object Delay : Sheet

    /** Currency picker; [forFrom] true = "from" slot, false = "to". */
    data class CurrencyPicker(val forFrom: Boolean) : Sheet

    /** New event/task/call composer (calendar). */
    data object NewEvent : Sheet

    /** Read-only event detail (calendar). */
    data class EventView(val title: String, val time: String) : Sheet
}

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
    var sheet by mutableStateOf<Sheet?>(null)
    var call by mutableStateOf<CallPhase?>(null)
    var toast by mutableStateOf<ToastMsg?>(null)

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
        sheet = null
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
