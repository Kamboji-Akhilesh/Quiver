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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

/**
 * Per-app screen header: back chevron, a small "QUIVER" eyebrow tinted by the
 * app [accent], the screen [title], and an optional trailing action.
 */
@Composable
fun QvTopBar(
    title: String,
    accent: Accent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Quiver.colors
    Row(
        modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onBack, contentDescription = "Back")
        Column(Modifier.weight(1f)) {
            Text(
                "QUIVER",
                color = accent.txt(colors.dark),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                title,
                color = colors.text,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Display,
            )
        }
        trailing?.invoke()
    }
}
