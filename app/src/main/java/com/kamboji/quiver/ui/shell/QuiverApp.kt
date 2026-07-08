package com.kamboji.quiver.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamboji.quiver.ui.calendar.CalendarScreen
import com.kamboji.quiver.ui.calendar.CallAlert
import com.kamboji.quiver.ui.components.Aurora
import com.kamboji.quiver.ui.currency.CurrencyScreen
import com.kamboji.quiver.ui.expenses.ExpensesScreen
import com.kamboji.quiver.ui.hub.HubScreen
import com.kamboji.quiver.ui.ai.QuiverAiPanel
import com.kamboji.quiver.ui.notes.NotesScreen
import com.kamboji.quiver.ui.onboarding.OnboardingOverlay
import com.kamboji.quiver.ui.screenshots.ScreenshotsScreen
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Quiver
import com.kamboji.quiver.ui.theme.QuiverTheme

/**
 * The whole app as one surface: an aurora-washed background, the active mini-app
 * screen, the floating dock, and any open overlays (search / AI / launcher /
 * sheet / call) plus the toast — all sharing one [QuiverState].
 */
@Composable
fun QuiverApp(
    command: ShellCommand? = null,
    onCommandConsumed: () -> Unit = {},
    showOnboarding: Boolean = false,
    onOnboarded: () -> Unit = {},
) {
    val state = remember { QuiverState() }
    val accent = Accents.of(state.app)
    var onboarding by remember { mutableStateOf(showOnboarding) }

    // Apply a deep-link command (app shortcut / share router) exactly once.
    LaunchedEffect(command) {
        if (command != null) {
            command.applyTo(state)
            onCommandConsumed()
        }
    }

    // System back: unwind overlays/screens instead of leaving the app. Disabled
    // (falls through to exit) only when sitting on the Hub with nothing open.
    val onHubIdle = state.app == AppKey.Hub && !state.searchOpen && !state.aiOpen &&
        !state.launcherOpen && state.call == null
    BackHandler(enabled = !onHubIdle) {
        when {
            state.call != null -> state.call = null
            state.searchOpen -> state.searchOpen = false
            state.aiOpen -> state.aiOpen = false
            state.launcherOpen -> state.launcherOpen = false
            state.app == AppKey.Screenshots && state.screenshotsScreen != "home" -> state.screenshotsScreen = "home"
            state.app != AppKey.Hub -> state.go(AppKey.Hub)
        }
    }

    QuiverTheme(dark = state.dark, accent = accent) {
        val colors = Quiver.colors
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Aurora(accent)

            // Active screen (sits under the dock; screens reserve bottom padding).
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (state.app) {
                    AppKey.Hub -> HubScreen(state)
                    AppKey.Screenshots -> ScreenshotsScreen(state)
                    AppKey.Currency -> CurrencyScreen(state)
                    AppKey.Calendar -> CalendarScreen(state)
                    AppKey.Notes -> NotesScreen(state)
                    AppKey.Expenses -> ExpensesScreen(state)
                }
            }

            // Dock — only on Home, so sub-screen sheets are never covered by it.
            AnimatedVisibility(
                visible = state.app == AppKey.Hub,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                Dock(state)
            }

            // Overlays (each draws its own scrim / sheet, above the dock)
            if (state.launcherOpen) Launcher(state)
            if (state.searchOpen) SearchOverlay(state)
            if (state.aiOpen) QuiverAiPanel(
                initialInput = state.aiPrefill,
                startMic = state.aiStartMic,
                onClose = { state.aiOpen = false; state.aiPrefill = null; state.aiStartMic = false },
            )

            // Full-screen call alert sits above everything but the toast.
            if (state.call != null) CallAlert(state)

            // First-run onboarding covers the whole shell until dismissed.
            if (onboarding) {
                OnboardingOverlay { openAi ->
                    onboarding = false
                    onOnboarded()
                    if (openAi) {
                        state.closeOverlays()
                        state.aiOpen = true
                    }
                }
            }

            // Toast on top of everything
            ToastHost(
                state,
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth(),
            )
        }
    }
}
