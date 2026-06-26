package com.kamboji.quiver.ui.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.calendar.CalendarViewModel
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvIconButton
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
import java.time.format.DateTimeFormatter

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

private fun timeLabel(e: CalendarEntry): String =
    if (e.allDay) "all-day"
    else Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault())
        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun CalendarScreen(state: QuiverState) {
    val ac = Accents.Calendar
    val colors = Quiver.colors
    val vm: CalendarViewModel = viewModel()
    val entries by vm.entries.collectAsState()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var sheet by remember { mutableStateOf<CalSheet?>(null) }

    val today = LocalDate.now()
    val byDay = remember(entries) { entries.groupBy { localDate(it.startMillis) } }
    val dayItems = (byDay[selected] ?: emptyList())
        .sortedWith(compareByDescending<CalendarEntry> { it.allDay }.thenBy { it.startMillis })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 150.dp)) {
            QvTopBar(
                "Calendar", ac, onBack = { state.go(AppKey.Hub) },
                trailing = {
                    QvIconButton(
                        Icons.Filled.Add, { sheet = CalSheet.New }, size = 42.dp,
                        background = colors.surf, tint = colors.text,
                    )
                },
            )
            Column(Modifier.padding(horizontal = 18.dp)) {
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
                    MonthGrid(month, selected, today, byDay, ac) { selected = it }
                }

                // agenda header
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${selected.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
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
                            EventRow(e, ac,
                                onClick = {
                                    when (kindOf(e)) {
                                        Kind.Call -> state.startCall(e.title)
                                        Kind.Task -> { vm.toggleDone(e.id); state.toast(if (!e.done) "Task completed ✓" else "Task reopened", if (!e.done) ToastKind.Success else ToastKind.Info) }
                                        Kind.Event -> sheet = CalSheet.View(e)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        when (val s = sheet) {
            is CalSheet.New -> NewEventSheet(selected, ac, onDismiss = { sheet = null }) { type, kind, titleText, start ->
                vm.upsert(
                    CalendarEntry(
                        id = 0, type = type, title = titleText, startMillis = start,
                        endMillis = if (type == EntryType.EVENT) start + 3_600_000 else null,
                        allDay = false, done = false,
                        alertStyle = if (kind == Kind.Call) AlertStyle.CALL else AlertStyle.NOTIFICATION,
                        alertLead = AlertLead.MIN10, createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                val d = localDate(start)
                state.toast("Added to ${d.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${d.dayOfMonth}", ToastKind.Success)
            }
            is CalSheet.View -> EventViewSheet(s.entry, ac, onDismiss = { sheet = null }) {
                vm.delete(s.entry.id); state.toast("Event deleted", ToastKind.Error)
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
    byDay: Map<LocalDate, List<CalendarEntry>>,
    ac: Accent,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = Quiver.colors
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
                        if (date != null) DayCell(date, date == selected, date == today, byDay[date].orEmpty(), ac) { onSelect(date) }
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
private fun EventRow(e: CalendarEntry, ac: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    val kind = kindOf(e)
    val col = when (kind) { Kind.Call -> CallPink; Kind.Task -> TaskAmber; Kind.Event -> ac.a }
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(col))
        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeLabel(e), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
            Text(kind.name.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.faint, letterSpacing = 0.5.sp)
        }
        if (kind == Kind.Task) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (e.done) TaskAmber else Color.Transparent)
                    .border(2.dp, if (e.done) TaskAmber else colors.border2, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { if (e.done) Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color(0xFF1A1408)) }
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (kind == Kind.Call) Icons.Outlined.Phone else Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = col)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (e.done) colors.dim else colors.text, textDecoration = if (e.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            Text(
                when (kind) { Kind.Call -> "Tap to preview call alert"; Kind.Task -> if (e.done) "Completed" else "Tap to complete"; Kind.Event -> "Event · Quiver Calendar" },
                fontSize = 12.sp, color = colors.dim,
            )
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

@Composable
private fun NewEventSheet(day: LocalDate, ac: Accent, onDismiss: () -> Unit, onAdd: (EntryType, Kind, String, Long) -> Unit) {
    val colors = Quiver.colors
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.Event) }
    var date by remember { mutableStateOf(day) }
    var hour by remember { mutableStateOf(14) }
    var minute by remember { mutableStateOf(0) }

    fun pickDate() {
        DatePickerDialog(
            context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
            date.year, date.monthValue - 1, date.dayOfMonth,
        ).show()
    }
    fun pickTime() {
        TimePickerDialog(context, { _, h, mnt -> hour = h; minute = mnt }, hour, minute, true).show()
    }

    SheetScaffold(onDismiss, "New ${kind.name.lowercase()}") { hide ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (title.isEmpty()) Text("${kind.name} title", color = colors.dim, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(title, { title = it }, textStyle = TextStyle(color = colors.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(ac.a), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChip(Icons.Outlined.CalendarMonth, "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.dayOfMonth}", ac, Modifier.weight(1f)) { pickDate() }
                InfoChip(Icons.Outlined.Schedule, "%02d:%02d".format(hour, minute), ac, Modifier.weight(1f)) { pickTime() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("Event", Kind.Event, kind, ac, Modifier.weight(1f)) { kind = it }
                TypeChip("Task", Kind.Task, kind, ac, Modifier.weight(1f)) { kind = it }
                TypeChip("Call", Kind.Call, kind, ac, Modifier.weight(1f)) { kind = it }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    .clickable(enabled = title.isNotBlank()) {
                        val start = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onAdd(if (kind == Kind.Task) EntryType.TASK else EntryType.EVENT, kind, title.trim(), start); hide()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Add ${kind.name.lowercase()}", color = Color(0xFF06121A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
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
private fun TypeChip(label: String, value: Kind, current: Kind, ac: Accent, modifier: Modifier, onSelect: (Kind) -> Unit) {
    val colors = Quiver.colors
    val sel = value == current
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) ac.a.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (sel) ac.a.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(14.dp))
            .clickable { onSelect(value) }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (sel) ac.txt(colors.dark) else colors.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventViewSheet(e: CalendarEntry, ac: Accent, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val colors = Quiver.colors
    SheetScaffold(onDismiss, e.title) { hide ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(21.dp), tint = ac.txt(colors.dark))
                }
                Column {
                    Text("${localDate(e.startMillis).month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${localDate(e.startMillis).dayOfMonth} · ${timeLabel(e)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text(if (e.alertStyle == AlertStyle.NONE) "No reminder" else "Reminder ${e.alertLead.label.lowercase()}", fontSize = 12.5.sp, color = colors.dim)
                }
            }
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
