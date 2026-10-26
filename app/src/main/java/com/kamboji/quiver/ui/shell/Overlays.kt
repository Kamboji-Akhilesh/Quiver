package com.kamboji.quiver.ui.shell

import com.kamboji.quiver.ui.theme.Body
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import com.kamboji.quiver.ui.components.Pill
import androidx.compose.material.icons.outlined.Edit
import com.kamboji.quiver.ui.components.QvIconButton
import com.kamboji.quiver.ui.hub.meta
import com.kamboji.quiver.ui.hub.HubPrefs
import com.kamboji.quiver.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.kamboji.quiver.ui.screenshots.openImage
import com.kamboji.quiver.notes.data.NotesStore
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.screenshots.search.ScreenshotMatch
import com.kamboji.quiver.screenshots.search.ScreenshotSearch

import com.kamboji.quiver.ui.locale.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.titleRes
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/** Every mini-app, as a page under the dock; the ones on Home are marked. */
@Composable
fun Launcher(state: QuiverState) {
    val colors = Quiver.colors
    val context = LocalContext.current
    val onHome = remember { HubPrefs(context).dashboardApps().toSet() }
    val apps = listOf(AppKey.Screenshots, AppKey.Calendar, AppKey.Focus, AppKey.Notes, AppKey.Expenses, AppKey.Move, AppKey.Currency)
    Column(
        Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) {}
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.dock_apps), Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = colors.text)
            QvIconButton(Icons.Outlined.Edit, { state.launcherOpen = false; state.editMode = true }, contentDescription = tr(R.string.hub_customize))
        }
        Text(tr(R.string.shell_apps_sub), fontSize = 13.sp, color = colors.dim, modifier = Modifier.padding(top = 0.dp))
        (apps.map { it as AppKey? } + null).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app ->
                    if (app != null) LauncherTile(app, app in onHome, Modifier.weight(1f)) { state.go(app) }
                    else Box(
                        Modifier.weight(1f).height(132.dp).border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr(R.string.shell_more_coming_soon), fontSize = 12.sp, color = colors.dim) }
                }
            }
        }
    }
}

@Composable
private fun LauncherTile(app: AppKey, onHome: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    val m = meta(app)
    Box(modifier.height(132.dp).glass(colors, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ac.iconColor(colors.dark).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(m.icon, null, Modifier.size(20.dp), tint = ac.iconColor(colors.dark)) }
            Text(stringResource(app.titleRes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(m.tag, fontSize = 12.sp, color = colors.dim, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onHome) Text(
            tr(R.string.shell_on_home), Modifier.align(Alignment.TopEnd),
            fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Accents.Hub.txt(colors.dark),
        )
    }
}

private data class SearchAction(val label: String, val icon: ImageVector, val app: AppKey, val accent: Accent, val screen: String = "home")

/** Full-screen universal search. */
@Composable
fun SearchOverlay(state: QuiverState) {
    val colors = Quiver.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Not remembered: the accents follow the colour theme, and the list is tiny.
    val actions = run {
        listOf(
            SearchAction(tr(R.string.shell_clean_screenshots_now), Icons.Outlined.Image, AppKey.Screenshots, Accents.Screenshots),
            SearchAction(tr(R.string.shell_convert_100_usd_to_eur), Icons.Outlined.SwapHoriz, AppKey.Currency, Accents.Currency),
            SearchAction(tr(R.string.shell_add_a_new_event), Icons.Filled.Add, AppKey.Calendar, Accents.Calendar),
            SearchAction(tr(R.string.shell_new_note), Icons.AutoMirrored.Outlined.StickyNote2, AppKey.Notes, Accents.Notes),
            SearchAction(tr(R.string.shell_add_an_expense), Icons.Outlined.Payments, AppKey.Expenses, Accents.Expenses),
            SearchAction(tr(R.string.shell_open_deletion_history), Icons.Outlined.History, AppKey.Screenshots, Accents.Screenshots, "history"),
        )
    }
    val filtered = actions.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    // Everything else Quiver holds: the text inside screenshots, and notes.
    val context = LocalContext.current
    var shots by remember { mutableStateOf<List<ScreenshotMatch>>(emptyList()) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { shots = emptyList(); notes = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(180)
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(context).screenshotTextDao()
            shots = ScreenshotSearch.rank(q, dao.candidates(ScreenshotSearch.likeArg(q))).take(3)
            notes = NotesStore(context).getAll()
                .filter { it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true) }
                .sortedByDescending { it.updatedAtMillis }.take(3)
        }
    }

    var filter by remember { mutableStateOf("all") }
    val showShots = filter == "all" || filter == "shots"
    val showNotes = filter == "all" || filter == "notes"
    val showActions = filter == "all" || filter == "actions"

    Column(
        Modifier.fillMaxSize().background(if (colors.flat) colors.bg else if (colors.dark) Color(0xEB080810) else Color(0xF2F4F5FA))
            .statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(40.dp, 48.dp).clip(RoundedCornerShape(12.dp)).clickable { state.searchOpen = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr(R.string.shell_cancel), Modifier.size(22.dp), tint = colors.text) }
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(colors.surf)
                    .border(1.dp, Accents.Hub.txt(colors.dark), RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr(R.string.shell_search_apps_actions_rates), color = colors.dim, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        query, { query = it },
                        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = Body),
                        cursorBrush = SolidColor(Accents.Hub.a),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.border).clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Clear", Modifier.size(16.dp), tint = colors.text) }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (query.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val total = shots.size + notes.size + filtered.size + 1
                Pill(tr(R.string.search_filter_all) + " · $total", filter == "all", Accents.Hub, { filter = "all" })
                Pill(tr(R.string.title_screenshots), filter == "shots", Accents.Hub, { filter = "shots" })
                Pill(tr(R.string.title_notes), filter == "notes", Accents.Hub, { filter = "notes" })
                Pill(tr(R.string.shell_actions), filter == "actions", Accents.Hub, { filter = "actions" })
            }
            if (showShots && shots.isNotEmpty()) {
                SectionLabel(tr(R.string.search_in_screenshots))
                ResultCard(shots) { i, m ->
                    ResultRow(Icons.Outlined.Image, Accents.Screenshots, m.row.fileName, m.snippet, i < shots.lastIndex) { openImage(context, m, state) }
                }
            }
            if (showNotes && notes.isNotEmpty()) {
                SectionLabel(tr(R.string.title_notes))
                ResultCard(notes) { i, n ->
                    ResultRow(Icons.AutoMirrored.Outlined.StickyNote2, Accents.Notes, n.title.ifBlank { tr(R.string.hub_untitled_note) }, n.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "", i < notes.lastIndex) {
                        state.searchOpen = false
                        state.go(AppKey.Notes)
                    }
                }
            }
            if (showActions) {
                SectionLabel(if (query.isBlank()) tr(R.string.shell_quick_actions) else tr(R.string.shell_actions))
                Column(Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                    ResultRow(
                        Icons.Filled.AutoAwesome, Accents.Hub,
                        if (query.isBlank()) tr(R.string.shell_ask_quiver_ai) else tr(R.string.search_ask_ai_about_x, query),
                        tr(R.string.shell_let_ai_handle_it_across), filtered.isNotEmpty(),
                    ) { state.searchOpen = false; state.aiOpen = true }
                    filtered.forEachIndexed { i, a ->
                        ResultRow(a.icon, a.accent, a.label, "", i < filtered.lastIndex) {
                            state.go(a.app); if (a.screen == "history") state.screenshotsScreen = "history"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ResultCard(items: List<T>, row: @Composable (Int, T) -> Unit) {
    Column(Modifier.fillMaxWidth().glass(Quiver.colors, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp)) {
        items.forEachIndexed { i, t -> row(i, t) }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, accent: Accent, title: String, sub: String, divider: Boolean, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(accent.iconColor(colors.dark).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = accent.iconColor(colors.dark))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = colors.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
