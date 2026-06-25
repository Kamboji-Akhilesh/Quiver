package com.kamboji.quiver.ui.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.MainActivity
import com.kamboji.quiver.ui.calendar.CalendarActivity
import com.kamboji.quiver.ui.currency.CurrencyActivity
import com.kamboji.quiver.ui.hub.theme.AppTheme
import java.util.Calendar

/** Launcher: a bento grid of mini-apps. */
class HubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { HubScreen(this) } }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun HubScreen(context: Context) {
    val scheme = MaterialTheme.colorScheme
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(greeting(), color = scheme.onSurface.copy(alpha = 0.6f), fontSize = 15.sp)
            Text("Super Hub", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = scheme.onSurface)
            Spacer(Modifier.height(20.dp))

            // Bento: Calendar hero on top, then a row of two.
            BentoTile(
                title = "Calendar",
                subtitle = "Tasks, events & call alerts",
                icon = Icons.Filled.CalendarMonth,
                colors = listOf(scheme.primary, scheme.tertiary),
                onColor = scheme.onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) { context.launch(CalendarActivity::class.java) }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth()) {
                BentoTile(
                    title = "Currency",
                    subtitle = "Live & offline rates",
                    icon = Icons.Filled.CurrencyExchange,
                    colors = listOf(scheme.secondary, scheme.primary),
                    onColor = scheme.onSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp),
                ) { context.launch(CurrencyActivity::class.java) }

                Spacer(Modifier.size(14.dp))

                BentoTile(
                    title = "Screenshots",
                    subtitle = "Auto-clean clutter",
                    icon = Icons.Filled.PhotoLibrary,
                    colors = listOf(scheme.tertiary, scheme.secondary),
                    onColor = scheme.onTertiary,
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp),
                ) { context.launch(MainActivity::class.java) }
            }

            Spacer(Modifier.height(14.dp))
            BentoTile(
                title = "More soon",
                subtitle = "New mini-apps land here",
                icon = Icons.Filled.AutoAwesome,
                colors = listOf(scheme.surfaceVariant, scheme.surfaceVariant),
                onColor = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                onClick = null,
            )
        }
    }
}

private fun Context.launch(target: Class<*>) = startActivity(Intent(this, target))

@Composable
private fun BentoTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    onColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = {},
) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = onColor.copy(alpha = 0.14f),
            modifier = Modifier
                .size(96.dp)
                .padding(8.dp),
        )
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(onColor.copy(alpha = 0.18f))
                    .padding(8.dp),
            ) { Icon(icon, contentDescription = null, tint = onColor) }
            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(40.dp))
            Text(title, color = onColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = onColor.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}
