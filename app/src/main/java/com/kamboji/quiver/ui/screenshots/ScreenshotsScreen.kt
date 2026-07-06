package com.kamboji.quiver.ui.screenshots

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.db.HistoryItem
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.screenshots.util.TrashManager
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QvToggle
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DELAYS = listOf(
    "2 min" to DeleteDelay(2, DelayUnit.MINUTES),
    "5 min" to DeleteDelay(5, DelayUnit.MINUTES),
    "30 min" to DeleteDelay(30, DelayUnit.MINUTES),
    "1 hour" to DeleteDelay(1, DelayUnit.HOURS),
    "5 hours" to DeleteDelay(5, DelayUnit.HOURS),
    "10 hours" to DeleteDelay(10, DelayUnit.HOURS),
    "1 day" to DeleteDelay(1, DelayUnit.DAYS),
    "2 days" to DeleteDelay(2, DelayUnit.DAYS),
)

private fun labelFor(d: DeleteDelay): String =
    DELAYS.firstOrNull { it.second == d }?.first ?: "${d.value} ${d.unit.name.lowercase()}"

/** Routes between the screenshots home and its deletion-history sub-screen. */
@Composable
fun ScreenshotsScreen(state: QuiverState) {
    if (state.screenshotsScreen == "history") HistoryScreen(state) else HomeScreen(state)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(state: QuiverState) {
    val colors = Quiver.colors
    val ac = Accents.Screenshots
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsManager.isServiceEnabled(context)) }
    var delay by remember { mutableStateOf(SettingsManager.getDelay(context)) }
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val history by remember { dao.getAllHistory() }.collectAsState(emptyList())

    fun setService(on: Boolean) {
        enabled = on
        SettingsManager.saveServiceEnabled(context, on)
        val intent = Intent(context, ScreenshotService::class.java)
        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } else {
            context.stopService(intent)
        }
        state.toast(if (on) "Monitoring resumed" else "Monitoring paused", if (on) ToastKind.Success else ToastKind.Info)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
        QvTopBar("Screenshots", ac, onBack = { state.go(AppKey.Hub) })
        Column(Modifier.padding(horizontal = 18.dp)) {
            // status hero
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(ac.a.copy(alpha = if (colors.dark) 0.22f else 0.18f), ac.b.copy(alpha = 0.10f))))
                    .border(1.dp, ac.a.copy(alpha = 0.32f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(ac.a.copy(alpha = 0.2f))
                                .border(1.dp, ac.a.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Icon(Icons.Outlined.Shield, null, Modifier.size(28.dp), tint = ac.txt(colors.dark)) }
                        Column {
                            Text(if (enabled) "PROTECTION ON" else "PAUSED", color = ac.txt(colors.dark), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(if (enabled) "Monitoring active" else "Not monitoring", fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                            Text(if (enabled) "New screenshots auto-clean" else "Tap to resume", fontSize = 12.5.sp, color = colors.dim)
                        }
                    }
                    QvToggle(enabled, { setService(!enabled) }, ac)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${history.size}", "In trash", ac, Modifier.weight(1f))
                    StatTile(labelFor(delay), "Auto-delete", ac, Modifier.weight(1f))
                    StatTile("7 days", "Retention", ac, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))
            // notification preview (decorative)
            Row(
                Modifier.fillMaxWidth().glass(colors).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Outlined.Notifications, null, Modifier.size(20.dp), tint = ac.txt(colors.dark))
                }
                Column(Modifier.weight(1f)) {
                    Text("How auto-clean works", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text("New screenshots are moved to trash after the delay below.", fontSize = 12.sp, color = colors.dim)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Auto-delete after")
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    DELAYS.forEach { (label, d) ->
                        Pill(label, delay == d, ac, {
                            delay = d
                            SettingsManager.saveDelay(context, d)
                            state.toast("Auto-delete set to $label", ToastKind.Success)
                        })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // history link
            Row(
                Modifier.fillMaxWidth().glass(colors).clickable { state.screenshotsScreen = "history" }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Outlined.History, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
                }
                Column(Modifier.weight(1f)) {
                    Text("Deletion history", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text("${history.size} in trash · restorable for 7 days", fontSize = 12.5.sp, color = colors.dim)
                }
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.dim)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, ac: com.kamboji.quiver.ui.theme.Accent, modifier: Modifier) {
    val colors = Quiver.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (colors.dark) Color(0x2E000000) else Color(0x80FFFFFF))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = ac.txt(colors.dark), maxLines = 1)
        Text(label, fontSize = 11.sp, color = colors.dim)
    }
}

@Composable
private fun HistoryScreen(state: QuiverState) {
    val colors = Quiver.colors
    val ac = Accents.Screenshots
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val history by remember { dao.getAllHistory() }.collectAsState(emptyList())

    Column(Modifier.fillMaxSize()) {
        QvTopBar("Deletion history", ac, onBack = { state.screenshotsScreen = "home" })
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ac.a.copy(alpha = 0.12f))
                        .border(1.dp, ac.a.copy(alpha = 0.28f), RoundedCornerShape(16.dp)).padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = ac.txt(colors.dark))
                    Text("Files are kept 7 days before permanent deletion", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ac.txt(colors.dark))
                }
            }
            if (history.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(ac.a.copy(alpha = 0.12f))
                                .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Icon(Icons.Outlined.Shield, null, Modifier.size(40.dp), tint = ac.txt(colors.dark)) }
                        Spacer(Modifier.height(20.dp))
                        Text("Trash is empty", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        Text("Cleaned screenshots appear here for 7 days.", fontSize = 13.5.sp, color = colors.dim)
                    }
                }
            } else {
                items(history, key = { it.id }) { item ->
                    TrashRow(
                        item, ac,
                        onRestore = {
                            scope.launch {
                                TrashManager.restoreFromTrash(context, item)
                                dao.delete(item)
                            }
                            state.toast("Screenshot restored", ToastKind.Success)
                        },
                        onDelete = {
                            scope.launch {
                                TrashManager.permanentlyDelete(context, item)
                                dao.delete(item)
                            }
                            state.toast("Permanently deleted", ToastKind.Error)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: HistoryItem,
    ac: com.kamboji.quiver.ui.theme.Accent,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Quiver.colors
    val hue = remember(item.id) { ((item.fileName.hashCode() % 360) + 360) % 360 }
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(22.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color.hsv(hue.toFloat(), 0.6f, 0.6f), Color.hsv(((hue + 40) % 360).toFloat(), 0.55f, 0.45f)))),
        )
        Column(Modifier.weight(1f)) {
            Text(item.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1)
            Text("Deleted ${formatWhen(item.deletedAt)}", fontSize = 12.sp, color = colors.dim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Restore", Icons.Outlined.Restore, ac.a, ac.txt(colors.dark), ac.a.copy(alpha = 0.14f), onRestore)
                ActionChip("Delete", Icons.Filled.DeleteOutline, Color(0xFFFB7185), Color(0xFFFB7185), Color(0x1FFB7185), onDelete)
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    border: Color,
    content: Color,
    bg: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp)).background(bg).border(1.dp, border.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(15.dp), tint = content)
        Text(label, color = content, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatWhen(ts: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))

