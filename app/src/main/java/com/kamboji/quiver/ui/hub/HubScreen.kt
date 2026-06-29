package com.kamboji.quiver.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.calendar.CalendarViewModel
import com.kamboji.quiver.currency.CurrencyViewModel
import com.kamboji.quiver.currency.Ui
import com.kamboji.quiver.notes.NotesViewModel
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Body
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

private data class AppMeta(val name: String, val tag: String, val icon: ImageVector)

private fun meta(app: AppKey): AppMeta = when (app) {
    AppKey.Screenshots -> AppMeta("Screenshots", "clear the clutter", Icons.Outlined.Image)
    AppKey.Currency -> AppMeta("Currency", "live & offline rates", Icons.Outlined.SwapHoriz)
    AppKey.Calendar -> AppMeta("Calendar", "tasks, events & calls", Icons.Outlined.CalendarMonth)
    AppKey.Notes -> AppMeta("Notes", "quick thoughts & lists", Icons.Outlined.StickyNote2)
    AppKey.Hub -> AppMeta("Quiver", "your apps", Icons.Filled.AutoAwesome)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun timeAgo(ts: Long): String {
    val mins = (System.currentTimeMillis() - ts) / 60000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 1440 -> "${mins / 60}h"
        else -> "${mins / 1440}d"
    }
}

private fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HubScreen(state: QuiverState) {
    val colors = Quiver.colors
    val context = LocalContext.current
    val calVm: CalendarViewModel = viewModel()
    val curVm: CurrencyViewModel = viewModel()
    val notesVm: NotesViewModel = viewModel()

    // ---- real data ----
    val entries by calVm.entries.collectAsState()
    val today = LocalDate.now()
    val todayCount = entries.count { localDate(it.startMillis) == today }
    val noteList by notesVm.notes.collectAsState()
    val notesCount = noteList.size
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val trash by remember { dao.getAllHistory() }.collectAsState(emptyList())
    val monitoring = remember(trash.size) { SettingsManager.isServiceEnabled(context) }
    val converter by curVm.converter.collectAsState()
    val curList = (converter as? Ui.Data)?.value?.data ?: emptyList()
    val curRate = curVm.rateOf(curVm.to, curList)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 140.dp),
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(greeting(), color = colors.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row {
                    Text("Your ", fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                    Text(
                        "Quiver",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Display,
                        style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIcon(if (colors.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode) { state.toggleTheme() }
                HeaderIcon(Icons.Outlined.Notifications) { state.toast("You're all caught up", ToastKind.Info) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))),
                    contentAlignment = Alignment.Center,
                ) { Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Display) }
            }
        }

        // ---- search trigger ----
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(100.dp))
                .clickable { state.closeOverlays(); state.searchOpen = true }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(19.dp), tint = colors.dim)
            Text("Search apps, actions, rates…", color = colors.dim, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        }

        // ---- AI suggestion banner (real, adaptive) ----
        Spacer(Modifier.height(14.dp))
        val (bannerText, bannerAction) = hubSuggestion(state, trash.size, todayCount, monitoring)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFA78BFA).copy(alpha = if (colors.dark) 0.22f else 0.18f), Color(0xFF22D3EE).copy(alpha = 0.14f)),
                    ),
                )
                .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .clickable { bannerAction() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(24.dp), tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("QUIVER AI", color = Accents.Hub.txt(colors.dark), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(bannerText, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.dim)
        }

        // ---- quick actions ----
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            QuickAction("Clean now", Icons.Outlined.Image, Accents.Screenshots) { state.go(AppKey.Screenshots) }
            QuickAction("Convert", Icons.Outlined.SwapHoriz, Accents.Currency) { state.go(AppKey.Currency) }
            QuickAction("New event", Icons.Filled.Add, Accents.Calendar) { state.go(AppKey.Calendar); state.calendarStartNew = true }
            QuickAction("Ask AI", Icons.Filled.AutoAwesome, Accents.Hub) { state.closeOverlays(); state.aiOpen = true }
        }

        // ---- mini apps ----
        Spacer(Modifier.height(22.dp))
        SectionLabel("Mini apps") {
            Row(
                Modifier.clip(RoundedCornerShape(100.dp))
                    .background(if (state.editMode) Accents.Hub.glow.copy(alpha = 0.15f) else colors.surf)
                    .border(1.dp, if (state.editMode) Accents.Hub.glow.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(100.dp))
                    .clickable { state.editMode = !state.editMode }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (state.editMode) Icons.Filled.Check else Icons.Outlined.Tune, null, Modifier.size(15.dp), tint = if (state.editMode) Accents.Hub.txt(colors.dark) else colors.dim)
                Text(if (state.editMode) "Done" else "Customize", color = if (state.editMode) Accents.Hub.txt(colors.dark) else colors.dim, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (state.editMode) {
            Text("Pin a card to feature it large · pinned cards sort to the top", color = colors.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        val order = listOf(AppKey.Screenshots, AppKey.Currency, AppKey.Calendar, AppKey.Notes)
            .sortedByDescending { state.pinned[it] == true }
        BentoGrid(state, order, monitoring, todayCount, curRate, curVm.to, notesCount)

        // ---- recent activity (real) ----
        Spacer(Modifier.height(22.dp))
        SectionLabel("Recent activity")
        Spacer(Modifier.height(4.dp))
        val activity = buildHubActivity(trash, entries)
        Column(Modifier.fillMaxWidth().glass(colors).padding(6.dp)) {
            if (activity.isEmpty()) {
                Text(
                    "Nothing yet — clean a screenshot or add an event to see it here.",
                    color = colors.dim, fontSize = 13.sp, modifier = Modifier.padding(14.dp),
                )
            } else {
                activity.forEachIndexed { i, a ->
                    ActivityRow(a.icon, a.title, a.app, timeAgo(a.ts), i < activity.lastIndex) { state.go(a.app) }
                }
            }
        }
    }
}

private fun hubSuggestion(state: QuiverState, trashCount: Int, todayCount: Int, monitoring: Boolean): Pair<String, () -> Unit> = when {
    trashCount > 0 -> "You have $trashCount screenshot${if (trashCount == 1) "" else "s"} in trash — restore or clear them." to {
        state.go(AppKey.Screenshots); state.screenshotsScreen = "history"
    }
    todayCount > 0 -> "$todayCount ${if (todayCount == 1) "item is" else "items are"} on your calendar today." to { state.go(AppKey.Calendar) }
    monitoring -> "Auto-clean is on — new screenshots are tidied for you." to { state.go(AppKey.Screenshots) }
    else -> "All clear. Tap to ask Quiver AI for anything." to { state.closeOverlays(); state.aiOpen = true }
}

private data class HubActivity(val icon: ImageVector, val title: String, val app: AppKey, val ts: Long)

private fun buildHubActivity(
    trash: List<com.kamboji.quiver.screenshots.data.db.HistoryItem>,
    entries: List<com.kamboji.quiver.calendar.data.CalendarEntry>,
): List<HubActivity> = buildList {
    trash.take(3).forEach { add(HubActivity(Icons.Outlined.Image, "Trashed ${it.fileName}", AppKey.Screenshots, it.deletedAt)) }
    entries.sortedByDescending { it.createdAtMillis }.take(3).forEach {
        add(HubActivity(Icons.Outlined.CalendarMonth, "Added “${it.title}”", AppKey.Calendar, it.createdAtMillis))
    }
}.sortedByDescending { it.ts }.take(3)

@Composable
private fun HeaderIcon(icon: ImageVector, onClick: () -> Unit) {
    val colors = Quiver.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(colors.surf)
            .border(1.dp, colors.border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(21.dp), tint = colors.text) }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, accent: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.glass(colors, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = accent.txt(colors.dark))
        Text(label, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Body)
    }
}

@Composable
private fun BentoGrid(state: QuiverState, order: List<AppKey>, monitoring: Boolean, todayCount: Int, curRate: Double?, curTo: String, notesCount: Int) {
    val colors = Quiver.colors
    val rows = packTiles(order) { state.pinned[it] == true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { app ->
                    val big = state.pinned[app] == true
                    BentoTile(state, app, big, monitoring, todayCount, curRate, curTo, notesCount, Modifier.weight(1f))
                }
                if (row.size == 1 && state.pinned[row[0]] != true) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(24.dp))
                .background(colors.surf)
                .border(1.5.dp, colors.border2, RoundedCornerShape(24.dp))
                .clickable { state.closeOverlays(); state.launcherOpen = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(20.dp), tint = colors.dim)
            Spacer(Modifier.width(10.dp))
            Text("More mini-apps land here", color = colors.dim, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Greedily groups apps into rows: a big (pinned) tile fills its own row. */
private fun packTiles(order: List<AppKey>, isBig: (AppKey) -> Boolean): List<List<AppKey>> {
    val rows = mutableListOf<List<AppKey>>()
    var i = 0
    while (i < order.size) {
        val a = order[i]
        if (isBig(a)) {
            rows.add(listOf(a)); i++
        } else if (i + 1 < order.size && !isBig(order[i + 1])) {
            rows.add(listOf(a, order[i + 1])); i += 2
        } else {
            rows.add(listOf(a)); i++
        }
    }
    return rows
}

@Composable
private fun BentoTile(
    state: QuiverState,
    app: AppKey,
    big: Boolean,
    monitoring: Boolean,
    todayCount: Int,
    curRate: Double?,
    curTo: String,
    notesCount: Int,
    modifier: Modifier,
) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    val m = meta(app)
    Box(
        modifier
            .height(if (big) 184.dp else 178.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        ac.a.copy(alpha = if (colors.dark) 0.22f else 0.18f),
                        ac.b.copy(alpha = if (colors.dark) 0.10f else 0.07f),
                    ),
                ),
            )
            .border(1.dp, ac.a.copy(alpha = if (colors.dark) 0.28f else 0.32f), RoundedCornerShape(28.dp))
            .clickable { if (state.editMode) togglePin(state, app) else state.go(app) }
            .padding(18.dp),
    ) {
        Icon(
            m.icon, null, tint = ac.a.copy(alpha = 0.14f),
            modifier = Modifier.align(Alignment.BottomEnd).size(if (big) 110.dp else 96.dp),
        )
        if (state.editMode) {
            Box(
                Modifier.align(Alignment.TopEnd).size(30.dp).clip(CircleShape)
                    .background(if (state.pinned[app] == true) ac.a else colors.surf2)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { togglePin(state, app) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PushPin, null, Modifier.size(15.dp), tint = if (state.pinned[app] == true) Color.White else colors.dim) }
        }
        Column(Modifier.fillMaxWidth(if (big) 0.62f else 1f)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(ac.a.copy(alpha = if (colors.dark) 0.2f else 0.18f))
                    .border(1.dp, ac.a.copy(alpha = 0.4f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(m.icon, null, Modifier.size(24.dp), tint = ac.txt(colors.dark)) }
            Spacer(Modifier.weight(1f))
            Text(m.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(m.tag, fontSize = 12.5.sp, color = colors.dim)
            Spacer(Modifier.height(11.dp))
            TileSummary(app, ac, monitoring, todayCount, curRate, curTo, notesCount)
        }
    }
}

private fun togglePin(state: QuiverState, app: AppKey) {
    val now = !(state.pinned[app] ?: false)
    state.pinned[app] = now
    state.toast(if (now) "Pinned to top" else "Unpinned", ToastKind.Info)
}

@Composable
private fun TileSummary(app: AppKey, ac: Accent, monitoring: Boolean, todayCount: Int, curRate: Double?, curTo: String, notesCount: Int) {
    val colors = Quiver.colors
    when (app) {
        AppKey.Screenshots -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (monitoring) ac.a else colors.faint))
            Text(if (monitoring) "Monitoring active" else "Paused", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (monitoring) ac.txt(colors.dark) else colors.dim)
        }
        AppKey.Currency -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(curRate?.let { "%.2f".format(it) } ?: "—", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
            Text("USD→$curTo", fontSize = 12.sp, color = colors.dim)
        }
        AppKey.Calendar -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$todayCount", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
            Text(if (todayCount == 1) "item today" else "items today", fontSize = 12.sp, color = colors.dim)
        }
        AppKey.Notes -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$notesCount", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
            Text(if (notesCount == 1) "note" else "notes", fontSize = 12.sp, color = colors.dim)
        }
        AppKey.Hub -> Unit
    }
}

@Composable
private fun ActivityRow(icon: ImageVector, title: String, app: AppKey, time: String, divider: Boolean, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(ac.a.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(18.dp), tint = ac.txt(colors.dark)) }
            Text(title, Modifier.weight(1f), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text, maxLines = 1)
            Text(time, fontSize = 11.sp, color = colors.faint, fontFamily = Mono)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
