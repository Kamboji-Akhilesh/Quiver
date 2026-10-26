package com.kamboji.quiver.ui.hub

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import com.kamboji.quiver.move.steps.Steps
import androidx.compose.material.icons.outlined.Edit
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import com.kamboji.quiver.ui.components.QvTimePickerDialog
import com.kamboji.quiver.ui.components.QvToggle
import com.kamboji.quiver.brief.MorningBrief
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kamboji.quiver.backup.BackupCrypto
import com.kamboji.quiver.backup.BackupIO
import com.kamboji.quiver.ui.components.Pill
import kotlinx.coroutines.launch
import com.kamboji.quiver.ui.locale.trPlural

import com.kamboji.quiver.ui.locale.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.R
import com.kamboji.quiver.calendar.CalendarViewModel
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.currency.CurrencyViewModel
import com.kamboji.quiver.currency.Ui
import com.kamboji.quiver.expenses.ExpensesViewModel
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.focus.FocusPrefs
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.ui.move.startOfWeekMillis
import com.kamboji.quiver.notes.NotesViewModel
import com.kamboji.quiver.notes.data.NotesStore
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.titleRes
import com.kamboji.quiver.ui.theme.Body
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

internal data class AppMeta(val tag: String, val icon: ImageVector)

internal fun meta(app: AppKey): AppMeta = when (app) {
    AppKey.Screenshots -> AppMeta(tr(R.string.hub_clear_the_clutter), Icons.Outlined.Image)
    AppKey.Currency -> AppMeta(tr(R.string.hub_live_offline_rates), Icons.Outlined.SwapHoriz)
    AppKey.Calendar -> AppMeta(tr(R.string.hub_tasks_events_calls), Icons.Outlined.CalendarMonth)
    AppKey.Notes -> AppMeta(tr(R.string.hub_quick_thoughts_lists), Icons.AutoMirrored.Outlined.StickyNote2)
    AppKey.Expenses -> AppMeta(tr(R.string.hub_where_money_goes), Icons.Outlined.Payments)
    AppKey.Focus -> AppMeta(tr(R.string.hub_block_shorts_reels), Icons.Outlined.SelfImprovement)
    AppKey.Move -> AppMeta(tr(R.string.hub_run_walk_ride_hike), Icons.AutoMirrored.Outlined.DirectionsRun)
    AppKey.Hub -> AppMeta(tr(R.string.hub_your_apps), Icons.Filled.AutoAwesome)
}

private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> R.string.greeting_morning
    in 12..16 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

private fun timeAgo(ts: Long): String {
    val mins = (System.currentTimeMillis() - ts) / 60000
    return when {
        mins < 1 -> tr(R.string.hub_now)
        mins < 60 -> tr(R.string.hub_ago_minutes, mins)
        mins < 1440 -> tr(R.string.hub_ago_hours, mins / 60)
        else -> tr(R.string.hub_ago_days, mins / 1440)
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
    val expVm: ExpensesViewModel = viewModel()

    // ---- real data ----
    val entries by calVm.entries.collectAsState()
    val today = LocalDate.now()
    val todayCount = entries.count { localDate(it.startMillis) == today }
    val noteList by notesVm.notes.collectAsState()
    val notesCount = noteList.size
    val expenseList by expVm.expenses.collectAsState()
    val monthSpend = ExpenseMath.formatPaise(
        ExpenseMath.summarize(ExpenseMath.inMonth(expenseList, java.time.YearMonth.now())).totalPaise,
    )

    // Dashboard apps are a user-curated subset (default 2); the rest can be added.
    val hubPrefs = remember { HubPrefs(context) }
    // Restore pinned tiles (otherwise pins reset to unpinned each cold start).
    LaunchedEffect(Unit) {
        val saved = hubPrefs.pinnedApps()
        state.pinned.keys.toList().forEach { state.pinned[it] = it in saved }
    }
    val dashApps = remember { mutableStateListOf<AppKey>().also { it.addAll(hubPrefs.dashboardApps()) } }
    var showAddPicker by remember { mutableStateOf(false) }
    val allApps = listOf(AppKey.Screenshots, AppKey.Currency, AppKey.Calendar, AppKey.Notes, AppKey.Expenses, AppKey.Focus, AppKey.Move)

    // Only hit the currency API if its tile is actually on the dashboard.
    val currencyOnDash = AppKey.Currency in dashApps
    LaunchedEffect(currencyOnDash) { if (currencyOnDash) curVm.ensureLoaded() }
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
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 140.dp),
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(greetingRes()), color = colors.dim, fontSize = 13.sp)
                Text(
                    tr(R.string.hub_quiver),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    fontFamily = Display,
                    // Glass keeps its gradient wordmark; flat themes use plain text.
                    style = if (colors.flat) TextStyle(color = colors.text) else TextStyle(brush = Brush.linearGradient(listOf(Accents.Hub.a, Accents.Hub.b))),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val caughtUp = stringResource(R.string.hub_caught_up)
                HeaderIcon(Icons.Outlined.Notifications, tr(R.string.hub_notifications)) { state.toast(caughtUp, ToastKind.Info) }
                HeaderIcon(Icons.Outlined.Settings, tr(R.string.settings_title)) { state.settingsOpen = true }
            }
        }

        // ---- search trigger ----
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp).glass(colors, RoundedCornerShape(14.dp))
                .clickable { state.closeOverlays(); state.searchOpen = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
            Text(tr(R.string.hub_search_apps_actions_rates), color = colors.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }

        // ---- AI suggestion banner (real, adaptive) ----
        Spacer(Modifier.height(14.dp))
        val (bannerText, bannerAction) = hubSuggestion(state, trash.size, todayCount, monitoring)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(
                    if (colors.flat) SolidColor(colors.surf)
                    else Brush.linearGradient(listOf(Accents.Hub.a.copy(alpha = if (colors.dark) 0.22f else 0.18f), Accents.Hub.b.copy(alpha = 0.14f))),
                )
                .border(1.dp, if (colors.flat) colors.border else Accents.Hub.a.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { bannerAction() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Accents.Hub.a, Accents.Hub.b))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = Accents.Hub.on) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr(R.string.hub_quiver_ai), color = Accents.Hub.txt(colors.dark), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(bannerText, color = colors.text, fontSize = 14.sp, lineHeight = 19.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.dim)
        }

        // ---- quick actions ----
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAction(stringResource(R.string.qa_clean), Icons.Outlined.Image, Accents.Screenshots) { state.go(AppKey.Screenshots) }
            QuickAction(stringResource(R.string.qa_convert), Icons.Outlined.SwapHoriz, Accents.Currency) { state.go(AppKey.Currency) }
            QuickAction(stringResource(R.string.qa_new_event), Icons.Filled.Add, Accents.Calendar) { state.go(AppKey.Calendar); state.calendarStartNew = true }
            QuickAction(stringResource(R.string.qa_new_note), Icons.AutoMirrored.Outlined.StickyNote2, Accents.Notes) { state.go(AppKey.Notes); state.notesStartNew = true }
            QuickAction(stringResource(R.string.qa_add_expense), Icons.Outlined.Payments, Accents.Expenses) { state.go(AppKey.Expenses); state.expensesStartNew = true }
            QuickAction(tr(R.string.qa_start_run), Icons.AutoMirrored.Outlined.DirectionsRun, Accents.Move) { state.go(AppKey.Move) }
            QuickAction(tr(R.string.qa_screen_time), Icons.Outlined.SelfImprovement, Accents.Focus) { state.go(AppKey.Focus) }
            QuickAction(stringResource(R.string.qa_ask_ai), Icons.Filled.AutoAwesome, Accents.Hub) { state.closeOverlays(); state.aiOpen = true }
        }

        // ---- mini apps ----
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.section_mini_apps)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (state.editMode) Accents.Hub.a else colors.surf)
                    .border(1.dp, if (state.editMode) Accents.Hub.a else colors.border, RoundedCornerShape(10.dp))
                    .clickable { state.editMode = !state.editMode },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.editMode) Icons.Filled.Check else Icons.Outlined.Edit,
                    if (state.editMode) tr(R.string.hub_done) else tr(R.string.hub_customize),
                    Modifier.size(16.dp), tint = if (state.editMode) Accents.Hub.on else colors.text,
                )
            }
        }
        if (state.editMode) {
            Text(tr(R.string.hub_pin_a_card_to_feature), color = colors.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        val order = dashApps.sortedByDescending { state.pinned[it] == true }
        BentoGrid(
            state, order, monitoring, trash.size, todayCount, curRate, curVm.to, notesCount, monthSpend, hubPrefs,
            onAddApp = { showAddPicker = true },
            onRemoveApp = { app ->
                dashApps.remove(app)
                state.pinned[app] = false
                hubPrefs.setDashboardApps(dashApps.toList())
                hubPrefs.setPinnedApps(state.pinned.filterValues { it }.keys)
            },
        )

        // ---- recent activity (real) ----
        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.section_recent))
        Spacer(Modifier.height(4.dp))
        val activity = buildHubActivity(trash, entries, noteList)
        Column(Modifier.fillMaxWidth().glass(colors).padding(6.dp)) {
            if (activity.isEmpty()) {
                Text(
                    tr(R.string.hub_nothing_yet_clean_a_screenshot),
                    color = colors.dim, fontSize = 13.sp, modifier = Modifier.padding(14.dp),
                )
            } else {
                activity.forEachIndexed { i, a ->
                    ActivityRow(a.icon, a.title, a.app, timeAgo(a.ts), i < activity.lastIndex) { state.go(a.app) }
                }
            }
        }

    }

    if (showAddPicker) {
        AddAppSheet(
            available = allApps.filter { it !in dashApps },
            onAdd = { app ->
                if (app !in dashApps) { dashApps.add(app); hubPrefs.setDashboardApps(dashApps.toList()) }
            },
            onDismiss = { showAddPicker = false },
        )
    }
}

@Composable
private fun AddAppSheet(available: List<AppKey>, onAdd: (AppKey) -> Unit, onDismiss: () -> Unit) {
    val colors = Quiver.colors
    QuiverModalSheet(onDismiss) { hide ->
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.hub_add_to_dashboard), fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.padding(bottom = 8.dp))
            if (available.isEmpty()) {
                Text(tr(R.string.hub_all_mini_apps_are_already), color = colors.dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                available.forEach { app ->
                    val ac = Accents.of(app)
                    val m = meta(app)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf)
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .clickable { onAdd(app); hide() }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ac.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(m.icon, null, Modifier.size(20.dp), tint = ac.txt(colors.dark))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(app.titleRes), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                            Text(m.tag, fontSize = 12.sp, color = colors.dim)
                        }
                        Text(tr(R.string.hub_add), color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun hubSuggestion(state: QuiverState, trashCount: Int, todayCount: Int, monitoring: Boolean): Pair<String, () -> Unit> = when {
    trashCount > 0 -> trPlural(R.plurals.hub_trash_banner, trashCount) to {
        state.go(AppKey.Screenshots); state.screenshotsScreen = "history"
    }
    todayCount > 0 -> trPlural(R.plurals.hub_calendar_banner, todayCount) to { state.go(AppKey.Calendar) }
    monitoring -> tr(R.string.hub_auto_clean_is_on_new) to { state.go(AppKey.Screenshots) }
    else -> tr(R.string.hub_all_clear_tap_to_ask) to { state.closeOverlays(); state.aiOpen = true }
}

private data class HubActivity(val icon: ImageVector, val title: String, val app: AppKey, val ts: Long)

private fun buildHubActivity(
    trash: List<com.kamboji.quiver.screenshots.data.db.HistoryItem>,
    entries: List<com.kamboji.quiver.calendar.data.CalendarEntry>,
    notes: List<com.kamboji.quiver.notes.data.Note>,
): List<HubActivity> = buildList {
    trash.take(3).forEach { add(HubActivity(Icons.Outlined.Image, tr(R.string.hub_trashed_x, it.fileName), AppKey.Screenshots, it.deletedAt)) }
    entries.sortedByDescending { it.createdAtMillis }.take(3).forEach {
        add(HubActivity(Icons.Outlined.CalendarMonth, tr(R.string.hub_added_x, it.title), AppKey.Calendar, it.createdAtMillis))
    }
    notes.sortedByDescending { it.updatedAtMillis }.take(3).forEach {
        val name = it.title.trim().ifBlank { it.body.trim().lineSequence().firstOrNull()?.take(30) ?: tr(R.string.hub_untitled_note) }
        add(HubActivity(Icons.AutoMirrored.Outlined.StickyNote2, tr(R.string.hub_edited_x, name), AppKey.Notes, it.updatedAtMillis))
    }
}.sortedByDescending { it.ts }.take(3)

/**
 * Export/import of everything (notes, calendar, expenses, alerts, Move) as one
 * password-encrypted file. SAF pickers keep this permissionless: the user
 * chooses where the backup goes / comes from.
 */
@Composable
internal fun DataSection(state: QuiverState) {
    val colors = Quiver.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Which password sheet is open; the password waits here while the file picker is up.
    var asking by remember { mutableStateOf<Uri?>(null) }
    var askExport by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pw = exportPassword ?: return@rememberLauncherForActivityResult
        exportPassword = null
        if (uri == null) { pw.fill(' '); return@rememberLauncherForActivityResult }
        scope.launch {
            busy = true
            runCatching { BackupIO.export(context, uri, pw) }
                .onSuccess { state.toast(tr(R.string.hub_backup_saved)) }
                .onFailure { state.toast(tr(R.string.hub_export_failed), ToastKind.Error) }
            pw.fill(' ')
            busy = false
        }
    }

    fun import(uri: Uri, password: CharArray?) = scope.launch {
        busy = true
        wrong = false
        runCatching { BackupIO.merge(context, BackupIO.read(context, uri, password)) }
            .onSuccess { c ->
                asking = null
                state.toast(
                    if (c.total == 0) tr(R.string.hub_everything_was_already_here)
                    else tr(
                        R.string.hub_imported_x,
                        listOfNotNull(
                            trPlural(R.plurals.count_notes, c.notes),
                            trPlural(R.plurals.count_calendar_items, c.calendar),
                            trPlural(R.plurals.count_expenses, c.expenses),
                            c.alerts.takeIf { it > 0 }?.let { trPlural(R.plurals.count_alerts, it) },
                            c.moves.takeIf { it > 0 }?.let { trPlural(R.plurals.count_activities, it) },
                        ).joinToString(" · "),
                    ),
                )
            }
            .onFailure { e ->
                when (e) {
                    is BackupIO.NeedsPassword -> { wrong = false; asking = uri }
                    is BackupCrypto.WrongPassword -> wrong = true
                    else -> { asking = null; state.toast(tr(R.string.hub_that_file_isn_t_a), ToastKind.Error) }
                }
            }
        password?.fill(' ')
        busy = false
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) import(uri, null)
    }

    Column(Modifier.fillMaxWidth().glass(colors).padding(6.dp)) {
        DataRow(Icons.Outlined.FileUpload, tr(R.string.hub_export_data), tr(R.string.hub_notes_calendar_expenses_alerts_one), true) {
            askExport = true
        }
        DataRow(Icons.Outlined.FileDownload, tr(R.string.hub_import_data), tr(R.string.hub_merge_a_backup_into_this), false) {
            importer.launch(arrayOf("application/json"))
        }
    }

    if (askExport) PasswordSheet(
        title = tr(R.string.backup_set_password), detail = tr(R.string.backup_set_password_why),
        confirm = true, busy = false, error = null, onDismiss = { askExport = false },
    ) { pw ->
        exportPassword = pw
        askExport = false
        exporter.launch("quiver-backup-${LocalDate.now()}.json")
    }
    asking?.let { uri ->
        PasswordSheet(
            title = tr(R.string.backup_enter_password), detail = tr(R.string.backup_enter_password_why),
            confirm = false, busy = busy, error = if (wrong) tr(R.string.backup_wrong_password) else null,
            onDismiss = { asking = null },
        ) { pw -> import(uri, pw) }
    }
}

/** Morning brief on/off and its time. */
@Composable
internal fun BriefRow() {
    val colors = Quiver.colors
    val context = LocalContext.current
    var on by remember { mutableStateOf(MorningBrief.isOn(context)) }
    var time by remember { mutableStateOf(MorningBrief.time(context)) }
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { picking = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(R.string.brief_setting), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(
                if (on) tr(R.string.brief_setting_on, time.format(DateTimeFormatter.ofPattern("h:mm a"))) else tr(R.string.brief_setting_off),
                fontSize = 12.sp, color = colors.dim, lineHeight = 16.sp,
            )
        }
        QvToggle(on, { on = !on; MorningBrief.set(context, on, time) }, Accents.Hub)
    }
    if (picking) QvTimePickerDialog(time.hour, time.minute, Accents.Hub, { h, m ->
        time = LocalTime.of(h, m)
        on = true
        MorningBrief.set(context, true, time)
    }, { picking = false })
}

@Composable
private fun PasswordSheet(
    title: String,
    detail: String,
    confirm: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    val colors = Quiver.colors
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val problem = when {
        pw.length < MIN_PASSWORD -> tr(R.string.backup_password_too_short, MIN_PASSWORD)
        confirm && again != pw -> tr(R.string.backup_passwords_differ)
        else -> null
    }
    QuiverModalSheet(onDismiss) { _ ->
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(detail, fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp)
            PasswordField(pw, { pw = it }, tr(R.string.backup_password))
            if (confirm) PasswordField(again, { again = it }, tr(R.string.backup_password_again))
            val shown = error ?: problem?.takeIf { pw.isNotEmpty() && (!confirm || again.isNotEmpty()) }
            if (shown != null) Text(shown, fontSize = 12.5.sp, color = Color(0xFFE5484D))
            Pill(
                if (busy) tr(R.string.backup_working) else tr(R.string.backup_continue),
                problem == null && !busy, Accents.Hub,
                { if (problem == null && !busy) onSubmit(pw.toCharArray()) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, hint: String) {
    val colors = Quiver.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(colors.surf)
            .border(1.dp, colors.border, RoundedCornerShape(100.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = colors.faint)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp), cursorBrush = SolidColor(Accents.Hub.a),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MIN_PASSWORD = 8

@Composable
private fun DataRow(icon: ImageVector, title: String, detail: String, divider: Boolean, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(detail, fontSize = 12.sp, color = colors.dim)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.dim)
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(colors.border))
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = Quiver.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surf)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(18.dp), tint = colors.text) }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, accent: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = accent.iconColor(colors.dark))
        Text(label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Body)
    }
}

@Composable
private fun BentoGrid(state: QuiverState, order: List<AppKey>, monitoring: Boolean, trashCount: Int, todayCount: Int, curRate: Double?, curTo: String, notesCount: Int, monthSpend: String, hubPrefs: HubPrefs, onAddApp: () -> Unit, onRemoveApp: (AppKey) -> Unit) {
    val colors = Quiver.colors
    val rows = packTiles(order) { state.pinned[it] == true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app ->
                    val big = state.pinned[app] == true
                    BentoTile(state, app, big, monitoring, trashCount, todayCount, curRate, curTo, notesCount, monthSpend, hubPrefs, { onRemoveApp(app) }, Modifier.weight(1f))
                }
                if (row.size == 1 && state.pinned[row[0]] != true) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(18.dp))
                .background(colors.surf)
                .border(1.5.dp, colors.border2, RoundedCornerShape(18.dp))
                .clickable(onClick = onAddApp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(20.dp), tint = colors.dim)
            Spacer(Modifier.width(10.dp))
            Text(tr(R.string.hub_more_mini_apps_land_here), color = colors.dim, fontWeight = FontWeight.SemiBold)
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
    trashCount: Int,
    todayCount: Int,
    curRate: Double?,
    curTo: String,
    notesCount: Int,
    monthSpend: String,
    hubPrefs: HubPrefs,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    val m = meta(app)
    Box(
        modifier
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (colors.flat) SolidColor(colors.surf)
                else Brush.linearGradient(
                    listOf(
                        ac.a.copy(alpha = if (colors.dark) 0.22f else 0.18f),
                        ac.b.copy(alpha = if (colors.dark) 0.10f else 0.07f),
                    ),
                ),
            )
            .border(1.dp, if (colors.flat) colors.border else ac.a.copy(alpha = if (colors.dark) 0.28f else 0.32f), RoundedCornerShape(18.dp))
            .clickable { if (state.editMode) togglePin(state, app, hubPrefs) else state.go(app) }
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                        .background(ac.iconColor(colors.dark).copy(alpha = if (colors.dark) 0.14f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(m.icon, null, Modifier.size(17.dp), tint = ac.iconColor(colors.dark)) }
                Text(
                    stringResource(app.titleRes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            TileSummary(app, ac, monitoring, trashCount, todayCount, curRate, curTo, notesCount, monthSpend)
        }
        if (state.editMode) {
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(colors.surf2)
                        .border(1.dp, colors.border, CircleShape).clickable { onRemove() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, null, Modifier.size(15.dp), tint = Color(0xFFFB7185)) }
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(if (state.pinned[app] == true) ac.a else colors.surf2)
                        .border(1.dp, colors.border, CircleShape)
                        .clickable { togglePin(state, app, hubPrefs) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.PushPin, null, Modifier.size(15.dp), tint = if (state.pinned[app] == true) ac.on else colors.dim) }
            }
        }
    }
}

private fun togglePin(state: QuiverState, app: AppKey, hubPrefs: HubPrefs) {
    val now = !(state.pinned[app] ?: false)
    state.pinned[app] = now
    hubPrefs.setPinnedApps(state.pinned.filterValues { it }.keys)
    state.toast(if (now) tr(R.string.hub_pinned_to_top) else tr(R.string.hub_unpinned), ToastKind.Info)
}

/** A tile's figure: the number on its own line, the label beneath, both one line. */
@Composable
private fun Stat(value: String, label: String) {
    val colors = Quiver.colors
    Column {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TileSummary(app: AppKey, ac: Accent, monitoring: Boolean, trashCount: Int, todayCount: Int, curRate: Double?, curTo: String, notesCount: Int, monthSpend: String) {
    val context = LocalContext.current
    when (app) {
        AppKey.Screenshots -> Stat("$trashCount", tr(if (monitoring) R.string.hub_tile_trash_monitoring else R.string.hub_tile_trash_paused))
        AppKey.Currency -> Stat(curRate?.let { "%.2f".format(it) } ?: "—", tr(R.string.hub_usd_x, curTo))
        AppKey.Calendar -> Stat("$todayCount", trPlural(R.plurals.hub_items_today_label, todayCount))
        AppKey.Notes -> Stat("$notesCount", trPlural(R.plurals.hub_notes_label, notesCount))
        AppKey.Expenses -> Stat(monthSpend, tr(R.string.hub_this_month))
        AppKey.Focus -> {
            val focused = LocalWindowInfo.current.isWindowFocused // refresh on return from a blocked app
            val blocked = remember(focused) { FocusPrefs(context).blockedOn() }
            Stat("$blocked", tr(R.string.hub_blocked_today))
        }
        AppKey.Move -> {
            if (Steps.hasPermission(context) && Steps.hasChip(context)) {
                val today = LocalDate.now()
                val hours by remember { MoveDatabase.get(context).steps().between(today.toString(), today.plusDays(1).toString()) }.collectAsState(emptyList())
                val steps = hours.sumOf { it.steps }
                Stat("%,d".format(steps), tr(R.string.hub_tile_steps_x, (steps * 100 / Steps.goal(context)).toInt()))
            } else {
                val weekKm by remember { MoveDatabase.get(context).dao().distanceSince(startOfWeekMillis()) }.collectAsState(0.0)
                Stat("%.1f".format(weekKm / 1000), tr(R.string.hub_km_this_week))
            }
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
