package com.kamboji.quiver.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.hub.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class CalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { CalendarScreen() } }
    }
}

private enum class CalView { MONTH, WEEK, DAY }

private sealed interface EditTarget {
    data class New(val date: LocalDate) : EditTarget
    data class Edit(val entry: CalendarEntry) : EditTarget
}

private fun millisToDateTime(millis: Long): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun toMillis(date: LocalDate, time: LocalTime): Long =
    LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToDate(millis: Long): LocalDate = millisToDateTime(millis).toLocalDate()

private fun List<CalendarEntry>.forDay(date: LocalDate): List<CalendarEntry> =
    filter { millisToDate(it.startMillis) == date }
        .sortedWith(compareByDescending<CalendarEntry> { it.allDay }.thenBy { it.startMillis })

private fun List<CalendarEntry>.markedDates(): Set<LocalDate> =
    map { millisToDate(it.startMillis) }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val entries by vm.entries.collectAsState()
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var view by remember { mutableStateOf(CalView.MONTH) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }

    editing?.let { target ->
        EntryEditScreen(
            vm = vm,
            target = target,
            onClose = { editing = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                },
                actions = { ViewMenu(view) { view = it } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = EditTarget.New(selected) }) {
                Icon(Icons.Filled.Event, contentDescription = "Add")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (view) {
                CalView.MONTH -> MonthGrid(
                    month = visibleMonth,
                    selected = selected,
                    marked = entries.markedDates(),
                    onSelect = { selected = it },
                    onPrev = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                )
                CalView.WEEK -> WeekStrip(selected, entries.markedDates()) { selected = it }
                CalView.DAY -> DayHeader(
                    selected,
                    onPrev = { selected = selected.minusDays(1) },
                    onNext = { selected = selected.plusDays(1) },
                )
            }
            HorizontalDivider()
            AgendaList(
                entries = entries.forDay(selected),
                onToggle = vm::toggleDone,
                onClick = { editing = EditTarget.Edit(it) },
                onDelete = vm::delete,
            )
        }
    }
}

@Composable
private fun ViewMenu(current: CalView, onPick: (CalView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "View")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        CalView.entries.forEach { v ->
            DropdownMenuItem(
                text = { Text(v.name.lowercase().replaceFirstChar { it.uppercase() }) },
                onClick = { onPick(v); open = false },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    marked: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrev) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
            Spacer(Modifier.weight(1f))
            IconButton(onNext) { Icon(Icons.Filled.ChevronRight, "Next month") }
        }
        Row {
            for (d in listOf("M", "T", "W", "T", "F", "S", "S")) {
                Text(
                    d,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - 1) // Monday = 0
        val days = month.lengthOfMonth()
        var day = 1
        for (week in 0 until 6) {
            if (day > days) break
            Row {
                for (col in 0 until 7) {
                    val cellIndex = week * 7 + col
                    if (cellIndex < lead || day > days) {
                        Box(Modifier.weight(1f).height(44.dp))
                    } else {
                        val date = month.atDay(day)
                        DayCell(date, date == selected, date in marked, Modifier.weight(1f)) {
                            onSelect(date)
                        }
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, marked: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    Row(Modifier.padding(8.dp)) {
        for (i in 0 until 7) {
            val date = monday.plusDays(i.toLong())
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                    style = MaterialTheme.typography.labelSmall,
                )
                DayCell(date, date == selected, date in marked, Modifier.fillMaxWidth()) {
                    onSelect(date)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    marked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val today = date == LocalDate.now()
    Box(modifier.height(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(
                    when {
                        selected -> scheme.primary
                        today -> scheme.primary.copy(alpha = 0.25f)
                        else -> Color.Transparent
                    }
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                )
            }
            Box(
                Modifier.size(5.dp).clip(CircleShape)
                    .background(if (marked) scheme.secondary else Color.Transparent)
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPrev) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onNext) { Icon(Icons.Filled.ChevronRight, "Next day") }
    }
}

@Composable
private fun AgendaList(
    entries: List<CalendarEntry>,
    onToggle: (Long) -> Unit,
    onClick: (CalendarEntry) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing scheduled. Tap + to add.")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.id }) { e -> EntryRow(e, onToggle, onClick, onDelete) }
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun EntryRow(
    e: CalendarEntry,
    onToggle: (Long) -> Unit,
    onClick: (CalendarEntry) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val time = when {
        e.allDay -> "All day"
        e.isEvent && e.endMillis != null ->
            "${millisToDateTime(e.startMillis).format(timeFmt)} – ${millisToDateTime(e.endMillis).format(timeFmt)}"
        else -> millisToDateTime(e.startMillis).format(timeFmt)
    }
    val alert = when (e.alertStyle) {
        AlertStyle.NONE -> "No alert"
        AlertStyle.NOTIFICATION -> "Notify ${e.alertLead.label.lowercase()}"
        AlertStyle.CALL -> "Call ${e.alertLead.label.lowercase()}"
    }
    Row(
        Modifier.fillMaxWidth().clickable { onClick(e) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.isTask) {
            Checkbox(checked = e.done, onCheckedChange = { onToggle(e.id) })
        } else {
            Icon(Icons.Filled.Event, contentDescription = null, tint = scheme.primary,
                modifier = Modifier.padding(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                fontWeight = FontWeight.Medium,
                textDecoration = if (e.done) TextDecoration.LineThrough else null,
            )
            Text("$time  ·  $alert", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onDelete(e.id) }) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete")
        }
    }
}

// --- Editor ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditScreen(
    vm: CalendarViewModel,
    target: EditTarget,
    onClose: () -> Unit,
) {
    val existing = (target as? EditTarget.Edit)?.entry
    val initialDate = (target as? EditTarget.New)?.date
        ?: existing?.let { millisToDateTime(it.startMillis).toLocalDate() }
        ?: LocalDate.now()

    var type by remember { mutableStateOf(existing?.type ?: EntryType.EVENT) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember { mutableStateOf(initialDate) }
    var start by remember {
        mutableStateOf(existing?.let { millisToDateTime(it.startMillis).toLocalTime() } ?: LocalTime.of(9, 0))
    }
    var end by remember {
        mutableStateOf(
            existing?.endMillis?.let { millisToDateTime(it).toLocalTime() } ?: LocalTime.of(10, 0)
        )
    }
    var allDay by remember { mutableStateOf(existing?.allDay ?: false) }
    var alertStyle by remember { mutableStateOf(existing?.alertStyle ?: AlertStyle.NOTIFICATION) }
    var alertLead by remember { mutableStateOf(existing?.alertLead ?: AlertLead.AT_TIME) }
    val context = LocalContext.current

    fun save() {
        if (title.isBlank()) return
        val startMillis = if (allDay) toMillis(date, LocalTime.of(9, 0)) else toMillis(date, start)
        val endMillis = if (type == EntryType.EVENT && !allDay) toMillis(date, end) else null
        vm.upsert(
            CalendarEntry(
                id = existing?.id ?: 0L,
                type = type,
                title = title.trim(),
                startMillis = startMillis,
                endMillis = endMillis,
                allDay = allDay,
                done = existing?.done ?: false,
                alertStyle = alertStyle,
                alertLead = alertLead,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            )
        )
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New" else "Edit") },
                navigationIcon = {
                    IconButton(onClose) { Icon(Icons.Filled.ChevronLeft, "Back") }
                },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EntryType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, EntryType.entries.size),
                    ) { Text(if (t == EntryType.EVENT) "Event" else "Task") }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (type == EntryType.EVENT) "Event title" else "Task title") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerButton(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f)) {
                    pickDate(context, date) { date = it }
                }
                if (!allDay) {
                    PickerButton("${if (type == EntryType.EVENT) "Start" else "Due"} ${start.format(timeFmt)}", Modifier.weight(1f)) {
                        pickTime(context, start) { start = it }
                    }
                }
            }
            if (type == EntryType.EVENT) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    PickerButton("End ${end.format(timeFmt)}", Modifier.fillMaxWidth()) {
                        pickTime(context, end) { end = it }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DropdownField("Alert", alertStyle.label, AlertStyle.entries.map { it.label }) { idx ->
                alertStyle = AlertStyle.entries[idx]
            }
            if (alertStyle != AlertStyle.NONE) {
                Spacer(Modifier.height(8.dp))
                DropdownField("When", alertLead.label, AlertLead.entries.map { it.label }) { idx ->
                    alertLead = AlertLead.entries[idx]
                }
            }
            if (existing != null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { vm.delete(existing.id); onClose() }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PickerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) { Text(text, color = scheme.onSurfaceVariant) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(i); expanded = false })
            }
        }
    }
}

private fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}

private fun pickTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, min -> onPick(LocalTime.of(h, min)) },
        initial.hour, initial.minute, false,
    ).show()
}
