package com.kamboji.quiver.ui.focus

import com.kamboji.quiver.ui.locale.trPlural

import com.kamboji.quiver.ui.locale.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.R
import com.kamboji.quiver.focus.FocusGate
import com.kamboji.quiver.focus.FocusPrefs
import com.kamboji.quiver.focus.FocusTarget
import com.kamboji.quiver.ui.components.EnableAccessDialog
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QvToggle
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver

@Composable
fun FocusScreen(state: QuiverState) {
    val ac = Accents.Focus
    val colors = Quiver.colors
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }
    var showEnable by remember { mutableStateOf(false) }

    // Re-read on return from Settings / from a blocked app.
    val focused = LocalWindowInfo.current.isWindowFocused
    val gateReady = remember(focused) { FocusGate.ready(context) }
    // With the gate, the service is deliberately off outside YouTube/Instagram, yet blocking is on.
    val serviceOn = remember(focused) { gateReady || FocusPrefs.isServiceEnabled(context) }
    val today = remember(focused) { prefs.blockedOn() }
    val total = remember(focused) { prefs.blockedTotal }
    val blocked = remember { mutableStateMapOf<FocusTarget, Boolean>().apply { FocusTarget.entries.forEach { put(it, prefs.isBlocked(it)) } } }
    var closeApp by remember { mutableStateOf(prefs.closeApp) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QvTopBar(stringResource(R.string.title_focus), ac, onBack = { state.go(AppKey.Hub) })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(bottom = 150.dp),
            ) {
                // Status + counter
                Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (serviceOn) ac.a else colors.faint))
                        Text(
                            if (serviceOn) tr(R.string.focus_blocking_is_on) else tr(R.string.focus_blocking_is_off),
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (serviceOn) ac.txt(colors.dark) else colors.dim,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("$today", fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text(
                        tr(R.string.focus_all_time, trPlural(R.plurals.focus_feeds_blocked_today, today), total),
                        fontSize = 12.5.sp, color = colors.dim,
                    )
                }

                if (!serviceOn) {
                    Spacer(Modifier.height(12.dp))
                    EnableCard(onEnable = { showEnable = true })
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(tr(R.string.focus_bank_safe))
                BankSafeCard(focused)

                Spacer(Modifier.height(18.dp))
                SectionLabel(tr(R.string.focus_block))
                Column(Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp))) {
                    FocusTarget.entries.forEachIndexed { i, t ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                            QvToggle(blocked[t] == true, {
                                val now = blocked[t] != true
                                blocked[t] = now
                                prefs.setBlocked(t, now)
                            }, ac)
                        }
                        if (i < FocusTarget.entries.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(tr(R.string.focus_when_blocked))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(tr(R.string.focus_go_back), !closeApp, ac, { closeApp = false; prefs.closeApp = false })
                    Pill(tr(R.string.focus_close_the_app), closeApp, ac, { closeApp = true; prefs.closeApp = true })
                }
                Text(
                    if (closeApp) tr(R.string.focus_sends_you_to_the_home)
                    else tr(R.string.focus_presses_back_you_stay_in),
                    fontSize = 12.sp, color = colors.dim, modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp).padding(top = 2.dp), tint = colors.faint)
                    Text(
                        tr(R.string.focus_android_only_lets_quiver_see),
                        fontSize = 12.sp, color = colors.faint, lineHeight = 16.sp,
                    )
                }
            }
        }

        if (showEnable) EnableAccessDialog(
            title = tr(R.string.focus_allow_accessibility_access),
            body = tr(R.string.focus_quiver_needs_accessibility_access_to),
            step = tr(R.string.focus_in_settings_open_quiver_focus),
            accent = ac,
            onOpenSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
            onDismiss = { showEnable = false },
        )
    }
}

@Composable
private fun EnableCard(onEnable: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Focus
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(ac.a.copy(alpha = if (colors.dark) 0.14f else 0.10f))
            .border(1.dp, ac.a.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onEnable).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SelfImprovement, null, Modifier.size(20.dp), tint = ac.txt(colors.dark))
        }
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.focus_turn_on_shorts_reels_blocking), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(tr(R.string.focus_one_time_accessibility_permission_works), fontSize = 11.5.sp, color = colors.dim, lineHeight = 15.sp)
        }
        Text(tr(R.string.focus_enable), color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Setup for [FocusGate]: two one-time grants, after which banking apps never see the blocker. */
@Composable
private fun BankSafeCard(focused: Boolean) {
    val colors = Quiver.colors
    val ac = Accents.Focus
    val context = LocalContext.current
    val usage = remember(focused) { FocusGate.hasUsageAccess(context) }
    val toggle = remember(focused) { FocusGate.canToggleService(context) }
    // Starts the gate as soon as both grants are in place.
    LaunchedEffect(usage, toggle) { FocusGate.sync(context) }

    Column(Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AccountBalance, null, Modifier.size(18.dp), tint = ac.txt(colors.dark))
            Text(
                if (usage && toggle) tr(R.string.focus_bank_on) else tr(R.string.focus_bank_why),
                fontSize = 12.5.sp, color = colors.dim, lineHeight = 17.sp,
            )
        }
        if (usage && toggle) return@Column
        BankStep(tr(R.string.focus_bank_step_usage), tr(R.string.focus_bank_step_usage_why), usage, tr(R.string.focus_bank_open)) {
            runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        val cmd = FocusGate.grantCommand(context)
        BankStep(tr(R.string.focus_bank_step_adb), tr(R.string.focus_bank_step_adb_why), toggle, tr(R.string.focus_bank_copy)) {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", cmd))
            Toast.makeText(context, tr(R.string.focus_bank_copied), Toast.LENGTH_SHORT).show()
        }
        if (!toggle) Text(
            cmd, fontFamily = Mono, fontSize = 11.sp, color = colors.text, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.border).padding(10.dp),
        )
    }
}

@Composable
private fun BankStep(title: String, why: String, done: Boolean, action: String, onAction: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Focus
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(why, fontSize = 11.5.sp, color = colors.dim, lineHeight = 15.sp)
        }
        if (done) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
        else Text(action, color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(8.dp))
    }
}
