package com.kamboji.quiver.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.ui.shell.QuiverActivity
import com.kamboji.quiver.ui.shell.ShellCommand
import com.kamboji.quiver.ui.theme.AppKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Quiver dark-glass look, independent of the launcher theme.
internal val WidgetBg = ColorProvider(Color(0xFF0D1B26))
internal val WidgetText = ColorProvider(Color(0xFFEAF2F8))
internal val WidgetDim = ColorProvider(Color(0xFF8FA3B0))
internal val WidgetAmber = ColorProvider(Color(0xFFFBBF24))
internal val WidgetSky = ColorProvider(Color(0xFF38BDF8))

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")

/** Deep link into the shell, same route the app shortcuts use. */
internal fun openShellIntent(context: Context, target: AppKey?): Intent =
    Intent(context, QuiverActivity::class.java)
        .setAction(ShellCommand.ACTION_OPEN)
        .apply { target?.let { putExtra(ShellCommand.EXTRA_TARGET, it.name) } }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Today's tasks and events; tasks are checkable right on the widget. */
class AgendaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        fun localMinutes(millis: Long): Int {
            val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
            return t.hour * 60 + t.minute
        }
        val items = CalendarStore(context).getAll()
            .filter { it.occursOn(today) }
            .sortedWith(
                compareBy<CalendarEntry> { it.done }
                    .thenByDescending { it.allDay }
                    .thenBy { localMinutes(it.startMillis) },
            )
        provideContent { Agenda(context, today, items) }
    }

    @Composable
    private fun Agenda(context: Context, today: LocalDate, items: List<CalendarEntry>) {
        Column(
            GlanceModifier.fillMaxSize().background(WidgetBg).cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(openShellIntent(context, AppKey.Calendar))),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TODAY",
                    style = TextStyle(color = WidgetSky, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    today.format(DAY_FMT),
                    style = TextStyle(color = WidgetDim, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
            }
            Spacer(GlanceModifier.height(6.dp))

            if (items.isEmpty()) {
                Text(
                    "Nothing scheduled — enjoy it.",
                    style = TextStyle(color = WidgetDim, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(vertical = 10.dp),
                )
            } else {
                items.take(5).forEach { e -> EntryRow(e) }
                if (items.size > 5) {
                    Text(
                        "+${items.size - 5} more",
                        style = TextStyle(color = WidgetDim, fontSize = 11.sp),
                        modifier = GlanceModifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun EntryRow(e: CalendarEntry) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (e.isTask) {
                CheckBox(
                    checked = e.done,
                    onCheckedChange = actionRunCallback<ToggleTaskCallback>(
                        actionParametersOf(ToggleTaskCallback.EntryId to e.id),
                    ),
                    colors = CheckboxDefaults.colors(checkedColor = WidgetAmber, uncheckedColor = WidgetDim),
                )
            } else {
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    e.title,
                    style = TextStyle(
                        color = if (e.done) WidgetDim else WidgetText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            Text(
                if (e.allDay) "all-day"
                else Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT),
                style = TextStyle(color = if (e.isTask) WidgetAmber else WidgetSky, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Widget checkbox → toggle the task, reschedule its alert, refresh widgets. */
class ToggleTaskCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[EntryId] ?: return
        val store = CalendarStore(context)
        val list = store.getAll().map { if (it.id == id) it.copy(done = !it.done) else it }
        store.saveAll(list)
        list.firstOrNull { it.id == id }?.let { AlertScheduler.reschedule(context, it) }
        AgendaWidget().updateAll(context)
    }

    companion object {
        val EntryId = ActionParameters.Key<Long>("entryId")
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
