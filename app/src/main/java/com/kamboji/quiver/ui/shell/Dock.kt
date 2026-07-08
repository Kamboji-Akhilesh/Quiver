package com.kamboji.quiver.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Quiver

/** The floating glass dock: Home · Search · [AI] · Apps · Calendar. */
@Composable
fun Dock(state: QuiverState, modifier: Modifier = Modifier) {
    val colors = Quiver.colors
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The raised AI button overflows the pill's top edge, so it lives in a
        // Box (which doesn't clip) overlaid on the row, with top space reserved
        // for the protrusion. The row reserves a center gap for it.
        Box(contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (colors.dark) Color(0xB814141F) else Color(0xC7FFFFFF))
                    .border(1.dp, colors.border, RoundedCornerShape(30.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DockItem(Icons.Outlined.Home, stringResource(R.string.dock_home), state.app == AppKey.Hub, Accents.of(state.app).txt(colors.dark)) {
                    state.go(AppKey.Hub)
                }
                DockItem(Icons.Outlined.Search, stringResource(R.string.dock_search), false, colors.dim) {
                    state.closeOverlays(); state.searchOpen = true
                }
                // Reserved gap that the floating AI button sits over.
                Spacer(Modifier.width(60.dp))
                DockItem(Icons.Outlined.GridView, stringResource(R.string.dock_apps), false, colors.dim) {
                    state.closeOverlays(); state.launcherOpen = true
                }
                DockItem(
                    Icons.Outlined.CalendarMonth, stringResource(R.string.dock_calendar),
                    state.app == AppKey.Calendar, Accents.Calendar.txt(colors.dark),
                ) { state.go(AppKey.Calendar) }
            }
            // Floating AI button — drawn on top, protruding above the pill.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE))))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { state.closeOverlays(); state.aiOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, "Quiver AI", Modifier.size(26.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .width(116.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (colors.dark) Color(0x59FFFFFF) else Color(0x5212162D)),
        )
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val colors = Quiver.colors
    Column(
        Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (active) activeColor else colors.dim)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) activeColor else colors.dim,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
