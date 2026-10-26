package com.kamboji.quiver.ui.components

import com.kamboji.quiver.R

import com.kamboji.quiver.ui.locale.tr
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

/**
 * Explains a special-access grant (notification access, accessibility) BEFORE
 * bouncing to system settings. There's no system popup for these, and on
 * Android 13+ sideloaded apps additionally need "Allow restricted settings"
 * from App info before the toggle even enables — so the dialog walks through
 * both, with a direct button for each.
 */
@Composable
fun EnableAccessDialog(
    title: String,
    body: String,
    step: String,
    accent: Accent,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Quiver.colors
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(if (colors.dark) Color(0xFF14141F) else Color(0xFFFAFBFE))
                .border(1.dp, colors.border, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(body, fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp)
            Text(step, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(
                tr(R.string.access_toggle_greyed_out_android_locks),
                fontSize = 12.sp, color = colors.faint, lineHeight = 16.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(100.dp))
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.access_app_info), color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(Brush.linearGradient(listOf(accent.a, accent.b)))
                        .clickable { onOpenSettings(); onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.access_open_settings), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
