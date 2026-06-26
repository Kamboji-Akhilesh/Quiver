package com.kamboji.quiver.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

// Temporary scaffolds — replaced by the real screens in later increments.

@Composable
fun ScreenshotsPlaceholder(state: QuiverState) =
    PlaceholderScreen(state, "Screenshots", Accents.Screenshots, Icons.Outlined.Image)

@Composable
fun CurrencyPlaceholder(state: QuiverState) =
    PlaceholderScreen(state, "Currency", Accents.Currency, Icons.Outlined.SwapHoriz)

@Composable
fun CalendarPlaceholder(state: QuiverState) =
    PlaceholderScreen(state, "Calendar", Accents.Calendar, Icons.Outlined.CalendarMonth)

@Composable
private fun PlaceholderScreen(state: QuiverState, title: String, accent: Accent, icon: ImageVector) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxSize()) {
        QvTopBar(title, accent, onBack = { state.go(AppKey.Hub) })
        Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(accent.a.copy(alpha = 0.12f))
                        .border(1.dp, accent.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(40.dp), tint = accent.txt(colors.dark)) }
                Text("$title — coming together", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                Text("This screen lands in the next build.", fontSize = 13.5.sp, color = colors.dim)
            }
        }
    }
}
