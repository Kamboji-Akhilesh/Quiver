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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Row(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(if (colors.dark) Color(0xB814141F) else Color(0xC7FFFFFF))
                .border(1.dp, colors.border, RoundedCornerShape(30.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DockItem(Icons.Outlined.Home, "Home", state.app == AppKey.Hub, Accents.of(state.app).txt(colors.dark)) {
                state.go(AppKey.Hub)
            }
            DockItem(Icons.Outlined.Search, "Search", false, colors.dim) {
                state.closeOverlays(); state.searchOpen = true
            }
            // Center AI button
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .offset(y = (-22).dp)
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
            DockItem(Icons.Outlined.GridView, "Apps", false, colors.dim) {
                state.closeOverlays(); state.launcherOpen = true
            }
            DockItem(
                Icons.Outlined.CalendarMonth, "Calendar",
                state.app == AppKey.Calendar, Accents.Calendar.txt(colors.dark),
            ) { state.go(AppKey.Calendar) }
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
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (active) activeColor else colors.dim)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) activeColor else colors.dim,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
