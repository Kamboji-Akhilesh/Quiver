package com.kamboji.quiver.ui.screenshots

import com.kamboji.quiver.ui.locale.displayName
import androidx.compose.material.icons.filled.Close

import com.kamboji.quiver.ui.locale.tr
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.res.stringResource
import com.kamboji.quiver.R
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.db.HistoryItem
import com.kamboji.quiver.screenshots.data.models.DELAY_PRESETS
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.search.ScreenshotMatch
import com.kamboji.quiver.screenshots.search.ScreenshotSearch
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.screenshots.util.TrashManager
import com.kamboji.quiver.screenshots.worker.OcrBackfillWorker
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



/** Routes between the screenshots home and its history / search sub-screens. */
@Composable
fun ScreenshotsScreen(state: QuiverState) {
    when (state.screenshotsScreen) {
        "history" -> HistoryScreen(state)
        "search" -> SearchScreen(state)
        else -> HomeScreen(state)
    }
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
    val textDao = remember { AppDatabase.getDatabase(context).screenshotTextDao() }

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
        state.toast(if (on) tr(R.string.shots_monitoring_resumed) else tr(R.string.shots_monitoring_paused), if (on) ToastKind.Success else ToastKind.Info)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
        QvTopBar(stringResource(R.string.title_screenshots), ac, onBack = { state.go(AppKey.Hub) })
        Column(Modifier.padding(horizontal = 18.dp)) {
            // status hero
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(ac.a.copy(alpha = if (colors.dark) 0.22f else 0.18f), ac.b.copy(alpha = 0.10f))))
                    .border(1.dp, ac.a.copy(alpha = 0.32f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    // "Tap to resume" means it: the label resumes too. Active-state taps do nothing (no accidental pause).
                    Row(
                        Modifier.weight(1f).clickable(enabled = !enabled) { setService(true) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(ac.a.copy(alpha = 0.2f))
                                .border(1.dp, ac.a.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Icon(Icons.Outlined.Shield, null, Modifier.size(28.dp), tint = ac.txt(colors.dark)) }
                        Column {
                            Text(if (enabled) tr(R.string.shots_protection_on) else tr(R.string.shots_paused), color = ac.txt(colors.dark), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(if (enabled) tr(R.string.shots_monitoring_active) else tr(R.string.shots_not_monitoring), fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                            Text(if (enabled) tr(R.string.shots_new_screenshots_auto_clean) else tr(R.string.shots_tap_to_resume), fontSize = 12.5.sp, color = colors.dim)
                        }
                    }
                    QvToggle(enabled, { setService(!enabled) }, ac)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${history.size}", tr(R.string.shots_in_trash), ac, Modifier.weight(1f))
                    StatTile(delay.displayName, tr(R.string.shots_auto_delete), ac, Modifier.weight(1f))
                    StatTile(tr(R.string.shots_7_days), tr(R.string.shots_retention), ac, Modifier.weight(1f))
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
                    Text(tr(R.string.shots_how_auto_clean_works), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(tr(R.string.shots_new_screenshots_are_moved_to), fontSize = 12.sp, color = colors.dim)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(tr(R.string.shots_auto_delete_after))
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                // Equal columns that fill the card (a wrapping row left a ragged gap at the end).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DELAY_PRESETS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { d ->
                                val on = delay == d
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (on) ac.a.copy(alpha = 0.14f) else colors.surf2)
                                        .border(1.dp, if (on) ac.a else colors.border, RoundedCornerShape(12.dp))
                                        .clickable {
                                            delay = d
                                            SettingsManager.saveDelay(context, d)
                                            state.toast(tr(R.string.shots_auto_delete_set_to_x, d.displayName), ToastKind.Success)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(d.displayName, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) ac.txt(colors.dark) else colors.text, maxLines = 1)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // search-your-screenshots entry
            val indexedCount by remember { textDao.observeCount() }.collectAsState(0)
            Row(
                Modifier.fillMaxWidth().glass(colors).clickable { state.screenshotsScreen = "search" }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Outlined.Search, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.shots_search_screenshots), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(
                        if (indexedCount == 0) tr(R.string.shots_search_the_text_inside_your) else tr(R.string.shots_x_searchable_by_their_text, indexedCount),
                        fontSize = 12.5.sp, color = colors.dim,
                    )
                }
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.dim)
            }

            Spacer(Modifier.height(12.dp))
            // backfill older screenshots (charger-friendly)
            Row(
                Modifier.fillMaxWidth().glass(colors).clickable {
                    OcrBackfillWorker.enqueue(context)
                    state.toast(tr(R.string.shots_indexing_older_screenshots_while_charging), ToastKind.Info)
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Outlined.Bolt, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.shots_index_older_screenshots), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(tr(R.string.shots_reads_existing_shots_for_search), fontSize = 12.5.sp, color = colors.dim)
                }
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.dim)
            }

            Spacer(Modifier.height(12.dp))
            // history link
            Row(
                Modifier.fillMaxWidth().glass(colors).clickable { state.screenshotsScreen = "history" }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Outlined.History, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.shots_deletion_history), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(tr(R.string.shots_x_in_trash_restorable_for, history.size), fontSize = 12.5.sp, color = colors.dim)
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
    var preview by remember { mutableStateOf<HistoryItem?>(null) }
    // The list's restore/delete for each item, so the preview can run the same ones.
    val previewActions = remember { mutableMapOf<Long, Pair<() -> Unit, () -> Unit>>() }

    Column(Modifier.fillMaxSize()) {
        QvTopBar(tr(R.string.shots_deletion_history), ac, onBack = { state.screenshotsScreen = "home" })
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
                    Text(tr(R.string.shots_files_are_kept_7_days), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ac.txt(colors.dark))
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
                        Text(tr(R.string.shots_trash_is_empty), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        Text(tr(R.string.shots_cleaned_screenshots_appear_here_for), fontSize = 13.5.sp, color = colors.dim)
                    }
                }
            } else {
                items(history, key = { it.id }) { item ->
                    val restore = {
                        preview = null
                        scope.launch {
                            TrashManager.restoreFromTrash(context, item)
                            dao.delete(item)
                        }
                        state.toast(tr(R.string.shots_screenshot_restored), ToastKind.Success)
                    }
                    val delete = {
                        preview = null
                        scope.launch {
                            TrashManager.permanentlyDelete(context, item)
                            dao.delete(item)
                        }
                        state.toast(tr(R.string.shots_permanently_deleted), ToastKind.Error)
                    }
                    previewActions[item.id] = restore to delete
                    TrashRow(
                        item, ac,
                        onPreview = { preview = item },
                        onRestore = {
                            preview = null
                            scope.launch {
                                TrashManager.restoreFromTrash(context, item)
                                dao.delete(item)
                            }
                            state.toast(tr(R.string.shots_screenshot_restored), ToastKind.Success)
                        },
                        onDelete = {
                            preview = null
                            scope.launch {
                                TrashManager.permanentlyDelete(context, item)
                                dao.delete(item)
                            }
                            state.toast(tr(R.string.shots_permanently_deleted), ToastKind.Error)
                        },
                    )
                }
            }
        }
    }
    preview?.let { item ->
        val (restore, delete) = previewActions[item.id] ?: return@let
        TrashPreview(item, ac, onRestore = { restore() }, onDelete = { delete() }) { preview = null }
    }
}

/** Decodes a trash file near [maxPx] on its short side — screenshots are big, rows are small. */
private fun loadTrashBitmap(path: String?, maxPx: Int): ImageBitmap? {
    if (path == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

@Composable
private fun rememberTrashBitmap(path: String?, maxPx: Int): ImageBitmap? =
    produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { loadTrashBitmap(path, maxPx) } }.value

/** Full-screen look at a trashed screenshot, with Restore / Delete forever right there. */
@Composable
private fun TrashPreview(item: HistoryItem, ac: com.kamboji.quiver.ui.theme.Accent, onRestore: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val image = rememberTrashBitmap(item.trashPath, 1080)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xF20A0B0C)).padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(Icons.Filled.Close, tr(R.string.calendar_close), Modifier.size(18.dp), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(item.fileName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text(tr(R.string.shots_deleted_x, formatWhen(item.deletedAt)), fontSize = 12.sp, color = Color(0xB3FFFFFF))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, item.fileName, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                else Text(tr(R.string.shots_preview_unavailable), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0x66FB7185), RoundedCornerShape(14.dp)).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.shots_delete_forever), color = Color(0xFFFB7185), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b))).clickable(onClick = onRestore),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.shots_restore), color = ac.on, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: HistoryItem,
    ac: com.kamboji.quiver.ui.theme.Accent,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Quiver.colors
    val hue = remember(item.id) { ((item.fileName.hashCode() % 360) + 360) % 360 }
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(22.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        val thumb = rememberTrashBitmap(item.trashPath, 160)
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color.hsv(hue.toFloat(), 0.6f, 0.6f), Color.hsv(((hue + 40) % 360).toFloat(), 0.55f, 0.45f))))
                .clickable(onClick = onPreview),
        ) {
            // The gradient stays as the placeholder while decoding (or if the file is gone).
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f)) {
            Text(item.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1)
            Text(tr(R.string.shots_deleted_x, formatWhen(item.deletedAt)), fontSize = 12.sp, color = colors.dim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(tr(R.string.shots_restore), Icons.Outlined.Restore, ac.a, ac.txt(colors.dark), ac.a.copy(alpha = 0.14f), onRestore)
                ActionChip(tr(R.string.shots_delete), Icons.Filled.DeleteOutline, Color(0xFFFB7185), Color(0xFFFB7185), Color(0x1FFB7185), onDelete)
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

/** Full-text search over the on-device OCR index. */
@Composable
private fun SearchScreen(state: QuiverState) {
    val colors = Quiver.colors
    val ac = Accents.Screenshots
    val context = LocalContext.current
    val textDao = remember { AppDatabase.getDatabase(context).screenshotTextDao() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScreenshotMatch>>(emptyList()) }
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { queryFocus.requestFocus() }

    // Debounced query → DAO prefilter → in-memory ranking. Re-runs on each keystroke.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(180)
        val candidates = textDao.candidates(ScreenshotSearch.likeArg(q))
        results = ScreenshotSearch.rank(q, candidates)
    }

    Column(Modifier.fillMaxSize()) {
        QvTopBar(tr(R.string.shots_search_screenshots), ac, onBack = { state.screenshotsScreen = "home" })
        Column(Modifier.padding(horizontal = 18.dp)) {
            // search field
            Row(
                Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(100.dp)).padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.Search, null, Modifier.size(19.dp), tint = colors.dim)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr(R.string.shots_wifi_password_otp_address), color = colors.dim, fontSize = 14.5.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = colors.text, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(ac.a), modifier = Modifier.fillMaxWidth().focusRequester(queryFocus),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (query.isBlank()) {
            SearchHint(ac, tr(R.string.shots_search_the_text_inside_your), tr(R.string.shots_everything_is_read_on_your))
        } else if (results.isEmpty()) {
            SearchHint(ac, tr(R.string.shots_no_matches), tr(R.string.shots_try_different_words_or_index))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results, key = { it.row.mediaId }) { match ->
                    ResultRow(match, ac) { openImage(context, match, state) }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(ac: com.kamboji.quiver.ui.theme.Accent, title: String, body: String) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().padding(top = 60.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(ac.a.copy(alpha = 0.12f))
                .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(Icons.Outlined.Search, null, Modifier.size(40.dp), tint = ac.txt(colors.dark)) }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.5.sp, color = colors.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp)
    }
}

@Composable
private fun ResultRow(match: ScreenshotMatch, ac: com.kamboji.quiver.ui.theme.Accent, onOpen: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Outlined.Image, null, Modifier.size(22.dp), tint = ac.txt(colors.dark))
        }
        Column(Modifier.weight(1f)) {
            Text(match.snippet, fontSize = 13.sp, color = colors.text, maxLines = 2, lineHeight = 18.sp)
            Text(
                "${match.row.fileName} · ${formatWhen(match.row.capturedAt)}",
                fontSize = 11.5.sp, color = colors.dim, fontFamily = Mono, maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.dim)
    }
}

/** Opens the matched screenshot in the system viewer; toasts if the file is gone. */
internal fun openImage(context: android.content.Context, match: ScreenshotMatch, state: QuiverState) {
    val opened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(match.row.uri), "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
    if (!opened) state.toast(tr(R.string.shots_that_screenshot_is_no_longer), ToastKind.Info)
}

