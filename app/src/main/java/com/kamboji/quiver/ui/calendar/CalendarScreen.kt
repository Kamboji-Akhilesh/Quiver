package com.kamboji.quiver.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.ai.agent.WhenResolver
import com.kamboji.quiver.calendar.CalendarViewModel
import com.kamboji.quiver.calendar.QuickAddParser
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.calendar.data.RepeatUnit
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QuiverComposerSheet
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvDatePickerDialog
import com.kamboji.quiver.ui.components.QvIconButton
import com.kamboji.quiver.ui.components.QvTimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import androidx.compose.runtime.collectAsState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CallPink = Color(0xFFFB7185)
private val TaskAmber = Color(0xFFFBBF24)

private enum class Kind { Event, Task, Call }

private fun kindOf(e: CalendarEntry): Kind = when {
    e.alertStyle == AlertStyle.CALL -> Kind.Call
    e.isTask -> Kind.Task
    else -> Kind.Event
}

private fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun localTimeMin(millis: Long): Int {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return t.hour * 60 + t.minute
}

private val TIME_12H = DateTimeFormatter.ofPattern("h:mm a")
private val TIME_HM = DateTimeFormatter.ofPattern("h:mm")
private val TIME_AMPM = DateTimeFormatter.ofPattern("a")

private fun timeLabel(e: CalendarEntry): String =
    if (e.allDay) "all-day"
    else Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault())
        .toLocalTime().format(TIME_12H)

private fun fmt12(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour, minute).format(TIME_12H)

@Composable
fun CalendarScreen(state: QuiverState) {
    val ac = Accents.Calendar
    val colors = Quiver.colors
    val vm: CalendarViewModel = viewModel()
    val entries by vm.entries.collectAsState()
    val context = LocalContext.current

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var sheet by remember { mutableStateOf<CalSheet?>(null) }
    var quickText by remember { mutableStateOf("") }

    // Natural-language quick-add: "dentist tomorrow 6pm" → parsed and saved
    // without opening the composer. Deterministic — no AI involved.
    fun quickAdd() {
        val q = QuickAddParser.parse(quickText)
        if (q == null) {
            state.toast(context.getString(R.string.quick_add_hint), ToastKind.Info)
            return
        }
        // No date in the text → the day selected in the month view.
        val start = WhenResolver.resolve(q.dateText ?: selected.toString(), q.timeText)
        // A clock time (or daypart) reads as an appointment; otherwise a to-do.
        val type = if (q.timeText != null) EntryType.EVENT else EntryType.TASK
        vm.upsert(
            CalendarEntry(
                id = 0L, type = type, title = q.title, startMillis = start,
                endMillis = if (type == EntryType.EVENT) start + 3_600_000 else null,
                allDay = false, done = false,
                alertStyle = AlertStyle.NOTIFICATION, alertLead = AlertLead.AT_TIME,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        val d = localDate(start)
        selected = d
        month = YearMonth.from(d)
        // Locale.getDefault() is set by AppLocale.wrap, so the month name follows
        // the chosen app language rather than being an English enum name.
        val whenLabel = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()))
        val added = if (type == EntryType.TASK) R.string.quick_add_added_task else R.string.quick_add_added_event
        state.toast(context.getString(added, whenLabel), ToastKind.Success)
        quickText = ""
    }

    val today = LocalDate.now()
    val dayItems = entries.filter { it.occursOn(selected) }
        .sortedWith(compareByDescending<CalendarEntry> { it.allDay }.thenBy { localTimeMin(it.startMillis) })

    // Deep-link from Home's "New event" quick action.
    LaunchedEffect(state.calendarStartNew) {
        if (state.calendarStartNew) { sheet = CalSheet.New; state.calendarStartNew = false }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Pinned header so the Add button never scrolls away.
            QvTopBar(
                stringResource(R.string.title_calendar), ac, onBack = { state.go(AppKey.Hub) },
                trailing = {
                    QvIconButton(
                        Icons.Filled.Add, { sheet = CalSheet.New }, size = 42.dp,
                        background = colors.surf, tint = colors.text,
                    )
                },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(bottom = 150.dp),
            ) {
                // quick add — one line, no composer
                Row(
                    Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp))
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (quickText.isEmpty()) {
                            Text(stringResource(R.string.quick_add_placeholder), color = colors.dim, fontSize = 13.5.sp)
                        }
                        BasicTextField(
                            quickText, { quickText = it }, singleLine = true,
                            textStyle = TextStyle(color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(ac.a),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { quickAdd() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    QvIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward, { quickAdd() },
                        size = 36.dp, iconSize = 18.dp, background = colors.surf, tint = ac.txt(colors.dark),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // month card
                Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            Text(month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                            Text("${month.year}", fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = Display, color = colors.dim)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, { month = month.minusMonths(1) }, size = 34.dp, iconSize = 17.dp)
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, { month = month.plusMonths(1) }, size = 34.dp, iconSize = 17.dp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                            Text(it, Modifier.weight(1f), color = colors.faint, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(month, selected, today, entries, ac) { selected = it }
                }

                // agenda header
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        Text("${selected.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${selected.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        if (selected == today) Text("  · Today", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ac.txt(colors.dark))
                    }
                    Text("${dayItems.size} ${if (dayItems.size == 1) "item" else "items"}", fontSize = 12.5.sp, color = colors.dim)
                }
                Spacer(Modifier.height(12.dp))

                if (dayItems.isEmpty()) {
                    EmptyAgenda(ac)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dayItems.forEach { e ->
                            EventRow(
                                e, ac,
                                // Tapping the row opens details for both tasks and events.
                                onClick = { sheet = CalSheet.View(e) },
                                // The checkbox toggles a task's done state.
                                onToggle = {
                                    vm.toggleDone(e.id)
                                    state.toast(if (!e.done) "Task completed ✓" else "Task reopened", if (!e.done) ToastKind.Success else ToastKind.Info)
                                },
                            )
                        }
                    }
                }
            }
        }

        when (val s = sheet) {
            is CalSheet.New -> NewEventSheet(
                selected, ac, onDismiss = { sheet = null },
                onInvalid = { state.toast("Enter a title first", ToastKind.Info) },
            ) { draft ->
                vm.upsert(draft)
                val d = localDate(draft.startMillis)
                state.toast("Added to ${d.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${d.dayOfMonth}", ToastKind.Success)
            }
            is CalSheet.View -> EventViewSheet(
                s.entry, ac, onDismiss = { sheet = null },
                onEdit = { sheet = CalSheet.Edit(s.entry) },
                onToggleDone = {
                    vm.toggleDone(s.entry.id)
                    state.toast(if (!s.entry.done) "Task completed ✓" else "Task reopened", if (!s.entry.done) ToastKind.Success else ToastKind.Info)
                },
                onDelete = { vm.delete(s.entry.id); state.toast(if (s.entry.isTask) "Task deleted" else "Event deleted", ToastKind.Error) },
            )
            is CalSheet.Edit -> NewEventSheet(
                selected, ac, editing = s.entry, onDismiss = { sheet = null },
                onInvalid = { state.toast("Enter a title first", ToastKind.Info) },
            ) { draft ->
                vm.upsert(draft)
                state.toast("Saved", ToastKind.Success)
            }
            null -> Unit
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    entries: List<CalendarEntry>,
    ac: Accent,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sun-first leading blanks
    val days = month.lengthOfMonth()
    val cells = buildList {
        repeat(lead) { add(null) }
        for (d in 1..days) add(month.atDay(d))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until 7) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val items = entries.filter { it.occursOn(date) }
                            DayCell(date, date == selected, date == today, items, ac) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSel: Boolean, isToday: Boolean, items: List<CalendarEntry>, ac: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp))
            .background(if (isSel) Brush.linearGradient(listOf(ac.a, ac.b)) else SolidColor(Color.Transparent))
            .border(1.dp, if (isToday && !isSel) ac.a.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(13.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = if (isSel) Color(0xFF06121A) else if (isToday) ac.txt(colors.dark) else colors.text,
            fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp, fontFamily = Mono,
        )
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.take(3).forEach { e ->
                Box(
                    Modifier.size(4.dp).clip(RoundedCornerShape(4.dp)).background(
                        if (isSel) Color(0xFF06121A) else when (kindOf(e)) {
                            Kind.Call -> CallPink; Kind.Task -> TaskAmber; Kind.Event -> ac.a
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEntry, ac: Accent, onClick: () -> Unit, onToggle: () -> Unit) {
    val colors = Quiver.colors
    val col = if (e.alertStyle == AlertStyle.CALL) CallPink else if (e.isTask) TaskAmber else ac.a
    val subtitle = when {
        e.isTask && e.done -> "Completed"
        else -> buildList {
            if (e.repeats) add(e.repeatLabel())
            when (e.alertStyle) {
                AlertStyle.CALL -> add("Call reminder")
                AlertStyle.NOTIFICATION -> add("Notification")
                AlertStyle.NONE -> {}
            }
        }.joinToString(" · ").ifEmpty { if (e.isTask) "Tap to complete" else "Event" }
    }
    val lt = if (e.allDay) null else Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(col))
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (lt == null) "All" else lt.format(TIME_HM), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1)
            Text(if (lt == null) "day" else lt.format(TIME_AMPM), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.faint, letterSpacing = 0.5.sp, maxLines = 1)
        }
        if (e.isTask) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (e.done) TaskAmber else Color.Transparent)
                    .border(2.dp, if (e.done) TaskAmber else colors.border2, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (e.done) Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color(0xFF1A1408)) }
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = col)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (e.done) colors.dim else colors.text, textDecoration = if (e.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, maxLines = 1)
            Text(subtitle, fontSize = 12.sp, color = colors.dim, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyAgenda(ac: Accent) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(ac.a.copy(alpha = 0.12f))
                .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(40.dp), tint = ac.txt(colors.dark)) }
        Spacer(Modifier.height(16.dp))
        Text("Nothing scheduled", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
        Text("Tap + to add an event or task for this day.", fontSize = 13.5.sp, color = colors.dim)
    }
}

// --- sheets ---

private sealed interface CalSheet {
    data object New : CalSheet
    data class View(val entry: CalendarEntry) : CalSheet
    data class Edit(val entry: CalendarEntry) : CalSheet
}

@Composable
private fun SheetScaffold(onDismiss: () -> Unit, title: String, content: @Composable (hide: () -> Unit) -> Unit) {
    val colors = Quiver.colors
    QuiverModalSheet(onDismiss) { hide ->
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            content(hide)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewEventSheet(day: LocalDate, ac: Accent, editing: CalendarEntry? = null, onDismiss: () -> Unit, onInvalid: () -> Unit, onAdd: (CalendarEntry) -> Unit) {
    val colors = Quiver.colors
    val startTime = editing?.let { Instant.ofEpochMilli(it.startMillis).atZone(ZoneId.systemDefault()).toLocalTime() }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var type by remember { mutableStateOf(editing?.type ?: EntryType.EVENT) }
    var date by remember { mutableStateOf(editing?.let { localDate(it.startMillis) } ?: day) }
    // Default to the next hour so a fresh entry is in the future (not already past).
    var hour by remember { mutableStateOf(startTime?.hour ?: ((java.time.LocalTime.now().hour + 1) % 24)) }
    var minute by remember { mutableStateOf(startTime?.minute ?: 0) }
    var alert by remember { mutableStateOf(editing?.alertStyle ?: AlertStyle.NOTIFICATION) }
    var lead by remember { mutableStateOf(editing?.alertLead ?: AlertLead.AT_TIME) }
    var repeatUnit by remember { mutableStateOf(editing?.repeatUnit ?: RepeatUnit.NONE) }
    var repeatInterval by remember { mutableStateOf(editing?.repeatInterval ?: 1) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val isEdit = editing != null

    // Shared keyboard-safe composer panel (see QuiverComposerSheet for why).
    QuiverComposerSheet(
        title = "${if (isEdit) "Edit" else "New"} ${if (type == EntryType.TASK) "task" else "event"}",
        onDismiss = onDismiss,
        footer = {
            // add (pinned outside the scroll so it's always visible)
            val ready = title.isNotBlank()
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    // graphicsLayer, NOT Modifier.alpha: conditional alpha() adds/
                    // removes the layer node when it crosses 1f, and some OEM skins
                    // skip the redraw — the button stayed clickable but invisible.
                    .graphicsLayer { alpha = if (ready) 1f else 0.5f }
                    .clickable {
                        if (!ready) { onInvalid(); return@clickable }
                        val start = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onAdd(
                            CalendarEntry(
                                id = editing?.id ?: 0L, type = type, title = title.trim(), startMillis = start,
                                endMillis = if (type == EntryType.EVENT) start + 3_600_000 else null,
                                allDay = false, done = editing?.done ?: false, alertStyle = alert, alertLead = lead,
                                createdAtMillis = editing?.createdAtMillis ?: System.currentTimeMillis(),
                                repeatUnit = repeatUnit, repeatInterval = repeatInterval,
                            ),
                        )
                        onDismiss()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (isEdit) "Save changes" else if (type == EntryType.TASK) "Add task" else "Add event", color = Color(0xFF06121A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        },
    ) {
            // title
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (title.isEmpty()) Text(if (type == EntryType.TASK) "Task title" else "Event title", color = colors.dim, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(title, { title = it }, textStyle = TextStyle(color = colors.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(ac.a), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            // type (Event / Task)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Event", type == EntryType.EVENT, ac, Modifier.weight(1f)) { type = EntryType.EVENT }
                SelectChip("Task", type == EntryType.TASK, ac, Modifier.weight(1f)) { type = EntryType.TASK }
            }
            // date + time
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChip(Icons.Outlined.CalendarMonth, "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.dayOfMonth}", ac, Modifier.weight(1f)) { showDate = true }
                InfoChip(Icons.Outlined.Schedule, fmt12(hour, minute), ac, Modifier.weight(1f)) { showTime = true }
            }
            // alert style
            FieldLabel("Alert")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("None", alert == AlertStyle.NONE, ac, Modifier.weight(1f)) { alert = AlertStyle.NONE }
                SelectChip("Notify", alert == AlertStyle.NOTIFICATION, ac, Modifier.weight(1f)) { alert = AlertStyle.NOTIFICATION }
                SelectChip("Call", alert == AlertStyle.CALL, ac, Modifier.weight(1f)) { alert = AlertStyle.CALL }
            }
            // lead time
            if (alert != AlertStyle.NONE) {
                FieldLabel("Remind")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertLead.entries.forEach { l -> Pill(l.label.replace(" before", ""), lead == l, ac, { lead = l }) }
                }
            }
            // repeat
            FieldLabel("Repeat")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Never", repeatUnit == RepeatUnit.NONE, ac, { repeatUnit = RepeatUnit.NONE })
                Pill("Daily", repeatUnit == RepeatUnit.DAILY, ac, { repeatUnit = RepeatUnit.DAILY })
                Pill("Weekly", repeatUnit == RepeatUnit.WEEKLY, ac, { repeatUnit = RepeatUnit.WEEKLY })
                Pill("Monthly", repeatUnit == RepeatUnit.MONTHLY, ac, { repeatUnit = RepeatUnit.MONTHLY })
            }
            if (repeatUnit != RepeatUnit.NONE) {
                IntervalStepper(
                    repeatInterval, repeatUnit, ac,
                    onMinus = { if (repeatInterval > 1) repeatInterval-- },
                    onPlus = { if (repeatInterval < 30) repeatInterval++ },
                )
            }
    }

    if (showTime) {
        QvTimePickerDialog(hour, minute, ac, onConfirm = { h, m -> hour = h; minute = m }, onDismiss = { showTime = false })
    }
    if (showDate) {
        QvDatePickerDialog(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), ac,
            onConfirm = { utc -> date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate() },
            onDismiss = { showDate = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), color = Quiver.colors.faint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, ac: Accent, modifier: Modifier, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = ac.txt(colors.dark))
        Text(label, color = colors.text, fontSize = 14.sp)
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, ac: Accent, modifier: Modifier, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) ac.a.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) ac.a.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) ac.txt(colors.dark) else colors.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IntervalStepper(value: Int, unit: RepeatUnit, ac: Accent, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Every", color = colors.dim, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepperButton("−", ac, onMinus)
            Text("$value ${unit.label}${if (value > 1) "s" else ""}", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            StepperButton("+", ac, onPlus)
        }
    }
}

@Composable
private fun StepperButton(label: String, ac: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(ac.a.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = ac.txt(colors.dark), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun EventViewSheet(e: CalendarEntry, ac: Accent, onDismiss: () -> Unit, onEdit: () -> Unit, onToggleDone: () -> Unit, onDelete: () -> Unit) {
    val colors = Quiver.colors
    SheetScaffold(onDismiss, e.title) { hide ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(21.dp), tint = ac.txt(colors.dark))
                }
                Column {
                    Text("${localDate(e.startMillis).month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${localDate(e.startMillis).dayOfMonth} · ${timeLabel(e)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text(
                        buildString {
                            append(if (e.isTask) "Task" else "Event")
                            if (e.repeats) append(" · ${e.repeatLabel()}")
                            append(if (e.alertStyle == AlertStyle.NONE) " · No reminder" else " · ${e.alertStyle.label} ${e.alertLead.label.lowercase()}")
                        },
                        fontSize = 12.5.sp, color = colors.dim,
                    )
                }
            }
            // Tasks get a done toggle.
            if (e.isTask) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (e.done) colors.surf else TaskAmber.copy(alpha = 0.16f))
                        .border(1.dp, if (e.done) colors.border else TaskAmber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable { onToggleDone(); hide() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val doneColor = if (e.done) colors.text else if (colors.dark) TaskAmber else Color(0xFF7A5A00)
                    Text(if (e.done) "Mark as not done" else "Mark as done", color = doneColor, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    .clickable { onEdit() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Edit", color = Color(0xFF06121A), fontSize = 14.5.sp, fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(14.dp)).clickable { hide() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Close", color = colors.text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CallPink.copy(alpha = 0.12f)).border(1.dp, CallPink.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).clickable { onDelete(); hide() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Delete", color = CallPink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
