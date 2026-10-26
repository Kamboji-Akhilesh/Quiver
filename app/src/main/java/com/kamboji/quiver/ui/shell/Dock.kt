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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

/** The bottom bar: Home · Search · [AI] · Apps · Calendar. */
@Composable
fun Dock(state: QuiverState, modifier: Modifier = Modifier) {
    val colors = Quiver.colors
    val hub = Accents.Hub
    val active = hub.txt(colors.dark)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clip(shape)
            .background(if (colors.flat) colors.surf else if (colors.dark) Color(0xB814141F) else Color(0xC7FFFFFF))
            .border(1.dp, colors.border, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        DockItem(Icons.Outlined.Home, stringResource(R.string.dock_home), state.app == AppKey.Hub && !state.launcherOpen, active) {
            state.closeOverlays()
            state.go(AppKey.Hub)
        }
        DockItem(Icons.Outlined.Search, stringResource(R.string.dock_search), false, active) {
            state.closeOverlays(); state.searchOpen = true
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(hub.a, hub.b)))
                .clickable { state.closeOverlays(); state.aiOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, "Ask Quiver AI", Modifier.size(22.dp), tint = hub.on)
        }
        DockItem(Icons.Outlined.GridView, stringResource(R.string.dock_apps), state.launcherOpen, active) {
            state.closeOverlays(); state.launcherOpen = true
        }
        DockItem(Icons.Outlined.CalendarMonth, stringResource(R.string.dock_calendar), state.app == AppKey.Calendar, active) {
            state.go(AppKey.Calendar)
        }
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
    val tint = if (active) activeColor else colors.dim
    Column(
        Modifier
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
