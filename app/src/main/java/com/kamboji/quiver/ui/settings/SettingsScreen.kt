package com.kamboji.quiver.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.BuildConfig
import com.kamboji.quiver.R
import com.kamboji.quiver.expenses.capture.PaymentCaptureService
import com.kamboji.quiver.expenses.capture.SmsCapture
import com.kamboji.quiver.focus.FocusGate
import com.kamboji.quiver.move.MovePrefs
import com.kamboji.quiver.move.steps.Steps
import com.kamboji.quiver.ui.components.QvSegmented
import com.kamboji.quiver.ui.components.QvToggle
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.hub.BriefRow
import com.kamboji.quiver.ui.hub.DataSection
import com.kamboji.quiver.ui.locale.tr
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Body
import com.kamboji.quiver.ui.theme.Palette
import com.kamboji.quiver.ui.theme.Quiver
import com.kamboji.quiver.ui.theme.ThemeMode
import com.kamboji.quiver.ui.theme.ThemeSettings
import com.kamboji.quiver.ui.theme.accentFor
import com.kamboji.quiver.ui.theme.colorsFor

/** Appearance, daily features, permissions and backups in one place. */
@Composable
fun SettingsScreen(state: QuiverState) {
    BackHandler { state.settingsOpen = false }
    val colors = Quiver.colors
    val ac = Quiver.accent
    val context = LocalContext.current
    // Re-read grants when coming back from Android's settings screens.
    val focused = LocalWindowInfo.current.isWindowFocused
    var refresh by remember { mutableStateOf(0) }
    val grants = remember(focused, refresh) { Grants.read(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (it && Steps.hasPermission(context)) Steps.start(context)
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        QvTopBar(tr(R.string.settings_title), ac, onBack = { state.settingsOpen = false })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(tr(R.string.settings_appearance))
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QvSegmented(
                    listOf(ThemeMode.System to tr(R.string.settings_mode_system), ThemeMode.Light to tr(R.string.settings_mode_light), ThemeMode.Dark to tr(R.string.settings_mode_dark)),
                    ThemeSettings.mode,
                    track = if (colors.flat) colors.bg else null,
                ) { ThemeSettings.choose(mode = it) }
                Text(tr(R.string.settings_colour_theme), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeCard(Palette.Quiver, R.string.settings_theme_quiver, R.string.settings_theme_quiver_sub, Modifier.weight(1f))
                    ThemeCard(Palette.Dusk, R.string.settings_theme_dusk, R.string.settings_theme_dusk_sub, Modifier.weight(1f))
                    ThemeCard(Palette.Glass, R.string.settings_theme_glass, R.string.settings_theme_glass_sub, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(tr(R.string.hub_daily))
            Column(Modifier.fillMaxWidth().glass(colors).padding(horizontal = 16.dp, vertical = 2.dp)) {
                BriefRow()
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                var voice by remember { mutableStateOf(MovePrefs(context).voiceSplits) }
                SwitchRow(tr(R.string.move_voice_setting), tr(R.string.settings_voice_sub), voice) {
                    voice = !voice
                    MovePrefs(context).voiceSplits = voice
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                var labels by remember { mutableStateOf(MovePrefs(context).mapLabels) }
                SwitchRow(tr(R.string.settings_map_labels), tr(R.string.settings_map_labels_sub), labels) {
                    labels = !labels
                    MovePrefs(context).mapLabels = labels
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(tr(R.string.settings_permissions))
            Column(Modifier.fillMaxWidth().glass(colors).padding(horizontal = 16.dp, vertical = 2.dp)) {
                GrantRow(tr(R.string.settings_perm_sms), tr(R.string.settings_perm_sms_sub), grants.sms, true) {
                    if (grants.sms) openAppSettings(context) else ask.launch(Manifest.permission.READ_SMS)
                }
                GrantRow(tr(R.string.settings_perm_notifications), tr(R.string.settings_perm_notifications_sub), grants.notifications, true) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
                GrantRow(tr(R.string.settings_perm_usage), tr(R.string.settings_perm_usage_sub), grants.usage, true) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
                GrantRow(tr(R.string.settings_perm_activity), tr(R.string.settings_perm_activity_sub), grants.activity, true) {
                    if (grants.activity) openAppSettings(context) else ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                GrantRow(tr(R.string.settings_perm_banksafe), tr(R.string.settings_perm_banksafe_sub), grants.bankSafe, false) {
                    // Its setup (usage access + a one-time adb grant) lives on the Focus screen.
                    state.settingsOpen = false
                    state.go(AppKey.Focus)
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(tr(R.string.section_your_data))
            DataSection(state)

            Text(
                tr(R.string.settings_version_x, BuildConfig.VERSION_NAME),
                fontSize = 11.5.sp, color = colors.faint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** What Quiver has been allowed to do, read fresh each time Settings shows. */
private data class Grants(val sms: Boolean, val notifications: Boolean, val usage: Boolean, val activity: Boolean, val bankSafe: Boolean) {
    companion object {
        fun read(context: Context) = Grants(
            sms = SmsCapture.hasPermission(context),
            notifications = PaymentCaptureService.isEnabled(context),
            usage = FocusGate.hasUsageAccess(context),
            activity = Steps.hasPermission(context),
            bankSafe = FocusGate.ready(context),
        )
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

/** A tappable miniature of [palette] in the chosen mode: dark, light, or both side by side for System. */
@Composable
private fun ThemeCard(palette: Palette, name: Int, sub: Int, modifier: Modifier) {
    val colors = Quiver.colors
    val selected = ThemeSettings.palette == palette
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) Quiver.accent.txt(colors.dark) else colors.border, RoundedCornerShape(14.dp))
            .clickable { ThemeSettings.choose(palette = palette) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, colors.border, RoundedCornerShape(8.dp))) {
            if (ThemeSettings.mode != ThemeMode.Light) Mini(palette, dark = true, Modifier.weight(1f))
            if (ThemeSettings.mode != ThemeMode.Dark) Mini(palette, dark = false, Modifier.weight(1f))
        }
        Text(tr(name), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text, fontFamily = Body)
        Text(tr(sub), fontSize = 11.sp, color = colors.dim)
    }
}

@Composable
private fun Mini(palette: Palette, dark: Boolean, modifier: Modifier) {
    val c = colorsFor(palette, dark)
    val a = accentFor(AppKey.Hub, palette, dark)
    val ground = when {
        palette != Palette.Glass -> Brush.linearGradient(listOf(c.bg, c.bg))
        dark -> Brush.linearGradient(listOf(Color(0xFF2A1F4E), c.bg, Color(0xFF0B2A33)))
        else -> Brush.linearGradient(listOf(Color(0xFFE9E1FF), c.bg, Color(0xFFD9F3F8)))
    }
    Column(modifier.fillMaxSize().background(ground).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(c.surf).border(1.dp, c.border, RoundedCornerShape(3.dp))) }
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(a.a, a.b))))
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(sub, fontSize = 12.sp, color = colors.dim)
        }
        QvToggle(on, onToggle, Quiver.accent)
    }
}

/** A permission with its real state; the switch opens the place Android lets you change it. */
@Composable
private fun GrantRow(title: String, sub: String, granted: Boolean, divider: Boolean, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(sub, fontSize = 12.sp, color = colors.dim)
        }
        Spacer(Modifier.width(10.dp))
        QvToggle(granted, onClick, Quiver.accent)
    }
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
