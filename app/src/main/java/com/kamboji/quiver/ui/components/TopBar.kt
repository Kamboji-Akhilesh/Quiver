package com.kamboji.quiver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

/**
 * Per-app screen header: back chevron, the screen [title], and an optional
 * trailing action. [accent] is kept for callers; the header itself is neutral.
 */
@Composable
fun QvTopBar(
    title: String,
    accent: Accent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Quiver.colors
    Row(
        modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onBack, contentDescription = "Back")
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.text,
                fontSize = if (subtitle == null) 26.sp else 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Display,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, color = colors.dim, fontSize = 12.sp, maxLines = 1)
        }
        trailing?.invoke()
    }
}
