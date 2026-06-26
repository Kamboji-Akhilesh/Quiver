package com.kamboji.quiver.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamboji.quiver.ui.components.Aurora
import com.kamboji.quiver.ui.hub.HubScreen
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
fun QuiverApp() {
    val state = remember { QuiverState() }
    val accent = Accents.of(state.app)
    QuiverTheme(dark = state.dark, accent = accent) {
        val colors = Quiver.colors
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Aurora(accent)

            // Active screen (sits under the dock; screens reserve bottom padding).
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (state.app) {
                    AppKey.Hub -> HubScreen(state)
                    AppKey.Screenshots -> ScreenshotsPlaceholder(state)
                    AppKey.Currency -> CurrencyPlaceholder(state)
                    AppKey.Calendar -> CalendarPlaceholder(state)
                }
            }

            // Dock
            Dock(
                state,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            )

            // Overlays (each draws its own scrim / sheet)
            if (state.launcherOpen) Launcher(state)
            if (state.searchOpen) SearchOverlay(state)
            if (state.aiOpen) AiPanel(state)

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
