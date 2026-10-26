package com.kamboji.quiver.ui.move

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.R
import com.kamboji.quiver.move.MoveFormat
import com.kamboji.quiver.move.MoveSummary
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.move.data.MoveEntity
import com.kamboji.quiver.move.steps.Steps
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.locale.tr
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Day / week / month view of steps and activities, like Google Fit's "My activity". */
enum class Span { Day, Week, Month }

/** The [Span] containing [anchor]: its first day and the day after its last. */
internal fun range(span: Span, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (span) {
    Span.Day -> anchor to anchor.plusDays(1)
    Span.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
    Span.Month -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1) }
}

@Composable
fun ActivityScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    BackHandler(onBack = onBack)
    val ac = Accents.Move
    val colors = Quiver.colors
    val context = LocalContext.current
    val today = LocalDate.now()
    var span by remember { mutableStateOf(Span.Day) }
    var anchor by remember { mutableStateOf(today) }
    var editGoals by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf(Steps.goal(context)) }

    val (from, to) = range(span, anchor)
    val hours by remember(from, to) { MoveDatabase.get(context).steps().between(from.toString(), to.toString()) }.collectAsState(emptyList())
    val moves by remember { MoveDatabase.get(context).dao().finished() }.collectAsState(emptyList())
    val zone = ZoneId.systemDefault()
    val inSpan = moves.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().let { d -> !d.isBefore(from) && d.isBefore(to) } }
        .sortedByDescending { it.startedAt }
    val total = hours.sumOf { it.steps }

    // Bars: hours of the day, or days of the week/month.
    val bars: List<Long> = when (span) {
        Span.Day -> List(24) { h -> hours.firstOrNull { it.hour.endsWith("T%02d".format(h)) }?.steps ?: 0 }
        else -> generateSequence(from) { it.plusDays(1) }.takeWhile { it.isBefore(to) }
            .map { d -> hours.filter { it.day == d.toString() }.sumOf { it.steps } }.toList()
    }
    val labels: List<String> = when (span) {
        Span.Day -> List(24) { if (it % 4 == 0) "$it" else "" }
        Span.Week -> (0 until 7).map { from.plusDays(it.toLong()).dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()) }
        Span.Month -> bars.indices.map { if (it % 5 == 0) "${it + 1}" else "" }
    }
    val title = when (span) {
        Span.Day -> if (anchor == today) tr(R.string.activity_today_x, anchor.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")))
            else anchor.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
        Span.Week -> "${from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${to.minusDays(1).format(DateTimeFormatter.ofPattern("MMM d"))}"
        Span.Month -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    fun shift(by: Long) {
        anchor = when (span) {
            Span.Day -> anchor.plusDays(by)
            Span.Week -> anchor.plusWeeks(by)
            Span.Month -> anchor.plusMonths(by)
        }
    }
    val canNext = to.isBefore(today.plusDays(1))

    Column(Modifier.fillMaxSize()) {
        QvTopBar(tr(R.string.activity_title), ac, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 150.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Span.entries.forEach { s ->
                    Pill(tr(when (s) { Span.Day -> R.string.activity_day; Span.Week -> R.string.activity_week; Span.Month -> R.string.activity_month }), s == span, ac, { span = s; anchor = today })
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable { shift(-1) }, tint = colors.text)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                        Text(tr(R.string.activity_steps_x, "%,d".format(total)), fontSize = 12.5.sp, color = colors.dim)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = canNext) { shift(1) },
                        tint = if (canNext) colors.text else colors.faint,
                    )
                }
                Spacer(Modifier.height(14.dp))
                BarChart(bars, labels, goal = if (span == Span.Day) null else goal.toLong(), ac.a, Modifier.fillMaxWidth().height(170.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                tr(R.string.activity_goals_x, "%,d".format(goal), Steps.weeklyMinutes(context)),
                fontSize = 12.sp, color = colors.dim, modifier = Modifier.padding(start = 2.dp).clickable { editGoals = true },
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel(tr(R.string.move_activities))
            if (inSpan.isEmpty()) Text(tr(R.string.activity_none), fontSize = 13.sp, color = colors.dim, modifier = Modifier.padding(top = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                inSpan.forEach { m -> TimelineRow(m) { onOpen(m.id) } }
            }
        }
    }

    if (editGoals) GoalsSheet(onDismiss = { editGoals = false }) { steps, minutes ->
        Steps.setGoal(context, steps)
        Steps.setWeeklyMinutes(context, minutes)
        goal = steps
        editGoals = false
    }
}

/** Bars with a few axis labels and an optional dashed goal line. */
@Composable
private fun BarChart(values: List<Long>, labels: List<String>, goal: Long?, color: Color, modifier: Modifier) {
    val colors = Quiver.colors
    val top = maxOf(values.maxOrNull() ?: 0, goal ?: 0, 100).toFloat()
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                val slot = size.width / values.size
                val bar = (slot * 0.55f).coerceAtMost(14.dp.toPx())
                listOf(0f, 0.5f, 1f).forEach { f ->
                    drawLine(colors.border, Offset(0f, size.height * (1 - f)), Offset(size.width, size.height * (1 - f)), 1.dp.toPx())
                }
                values.forEachIndexed { i, v ->
                    if (v <= 0) return@forEachIndexed
                    val h = (v / top * size.height).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        if (goal != null && v >= goal) color else color.copy(alpha = if (goal == null) 1f else 0.5f),
                        Offset(i * slot + (slot - bar) / 2, size.height - h), Size(bar, h), CornerRadius(bar / 2),
                    )
                }
                goal?.let {
                    val y = size.height * (1 - it / top)
                    drawLine(colors.dim, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
            // Spread top to bottom so the labels sit on the gridlines.
            Column(Modifier.padding(start = 6.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(compact(top.toLong()), fontSize = 10.sp, color = colors.faint)
                Text(compact(top.toLong() / 2), fontSize = 10.sp, color = colors.faint)
                Text("0", fontSize = 10.sp, color = colors.faint)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 28.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = colors.faint, maxLines = 1) }
        }
    }
}

private fun compact(n: Long) = if (n >= 1000) "%.1fk".format(n / 1000.0).replace(".0k", "k") else "$n"

/** One activity in the day's timeline: time, title, duration · steps. */
@Composable
private fun TimelineRow(m: MoveEntity, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Move
    val time = Instant.ofEpochMilli(m.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE · h:mm a"))
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp)).then(if (m.auto) Modifier else Modifier.clickable(onClick = onClick)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, fontSize = 11.5.sp, color = colors.dim)
            Text(MoveSummary.title(m.moveType, m.startedAt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text(
                listOfNotNull(
                    MoveFormat.duration(m.movingMs),
                    m.distanceM.takeIf { it > 0 }?.let { MoveFormat.km(it) },
                    m.steps.takeIf { it > 0 }?.let { tr(R.string.activity_n_steps, "%,d".format(it)) },
                    if (m.auto) tr(R.string.activity_auto) else null,
                ).joinToString(" · "),
                fontSize = 12.sp, fontFamily = Mono, color = colors.text,
            )
        }
        if (!m.auto) RouteThumb(
            MoveSummary.parsePreview(m.preview), ac.a,
            Modifier.size(54.dp).clip(RoundedCornerShape(27.dp)).background(ac.a.copy(alpha = 0.10f)),
        )
    }
}

@Composable
internal fun GoalsSheet(onDismiss: () -> Unit, onSave: (steps: Int, weeklyMinutes: Int) -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Move
    val context = LocalContext.current
    var steps by remember { mutableStateOf(Steps.goal(context).toString()) }
    var minutes by remember { mutableStateOf(Steps.weeklyMinutes(context).toString()) }
    val s = steps.toIntOrNull()?.takeIf { it in 500..100_000 }
    val m = minutes.toIntOrNull()?.takeIf { it in 10..5_000 }
    QuiverModalSheet(onDismiss) { _ ->
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.activity_goals), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.text)
            NumberField(steps, { steps = it }, tr(R.string.activity_goal_steps))
            NumberField(minutes, { minutes = it }, tr(R.string.activity_goal_minutes))
            Text(tr(R.string.activity_goal_minutes_why), fontSize = 12.sp, color = colors.dim, lineHeight = 16.sp)
            Pill(tr(R.string.move_save), s != null && m != null, ac, { if (s != null && m != null) onSave(s, m) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(colors.surf)
            .border(1.dp, colors.border, RoundedCornerShape(100.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, { new -> onChange(new.filter { it.isDigit() }.take(6)) }, singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp), cursorBrush = SolidColor(Accents.Move.a),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        Text(label, fontSize = 13.sp, color = colors.dim)
    }
}
