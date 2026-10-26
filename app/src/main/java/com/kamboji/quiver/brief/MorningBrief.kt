package com.kamboji.quiver.brief

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamboji.quiver.R
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.focus.FocusGate
import com.kamboji.quiver.focus.FocusPrefs
import com.kamboji.quiver.focus.ScreenTime
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.ui.locale.tr
import com.kamboji.quiver.ui.locale.trPlural
import com.kamboji.quiver.widgets.openShellIntent
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * One notification each morning: today's calendar, yesterday's spend, screen
 * time and blocked feeds, and this week's Move distance. Built on the phone
 * from Quiver's own data; nothing is fetched.
 */
object MorningBrief {

    /** Everything the brief says, gathered by [MorningBriefWorker]. */
    data class Inputs(
        val today: List<String>,
        val spentYesterdayPaise: Long,
        val screenYesterdayMs: Long?,
        val blockedYesterday: Int,
        val kmThisWeek: Double,
    )

    /** Which lines the brief shows, in order. Pure, so it's unit-tested. */
    internal fun keys(i: Inputs): List<String> = buildList {
        add(if (i.today.isEmpty()) "nothing_today" else "today")
        if (i.spentYesterdayPaise > 0) add("spent")
        if ((i.screenYesterdayMs ?: 0) >= 60_000) add("screen")
        if (i.blockedYesterday > 0) add("blocked")
        if (i.kmThisWeek >= 0.1) add("moved")
    }

    fun lines(i: Inputs): List<String> = keys(i).map {
        when (it) {
            "nothing_today" -> tr(R.string.brief_nothing_today)
            "today" -> tr(R.string.brief_today_x, trPlural(R.plurals.count_items, i.today.size), i.today.take(4).joinToString(", "))
            "spent" -> tr(R.string.brief_spent_x, ExpenseMath.formatPaise(i.spentYesterdayPaise))
            "screen" -> tr(R.string.brief_screen_x, ScreenTime.hm(i.screenYesterdayMs!!))
            "blocked" -> tr(R.string.brief_blocked_x, trPlural(R.plurals.count_feeds, i.blockedYesterday))
            else -> tr(R.string.brief_moved_x, "%.1f".format(i.kmThisWeek))
        }
    }

    private const val PREF = "morning_brief"
    private const val WORK = "quiver_morning_brief"
    private const val CHANNEL = "morning_brief"

    fun isOn(context: Context) = prefs(context).getBoolean("on", false)
    fun time(context: Context): LocalTime = LocalTime.of(prefs(context).getInt("h", 7), prefs(context).getInt("m", 30))

    fun set(context: Context, on: Boolean, time: LocalTime = time(context)) {
        prefs(context).edit().putBoolean("on", on).putInt("h", time.hour).putInt("m", time.minute).apply()
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Arms the next brief. A one-shot that re-arms itself keeps the time exact; periodic work drifts. */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        if (!isOn(context)) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time(context))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<MorningBriefWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, req)
    }

    internal suspend fun gather(context: Context): Inputs {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
        val agenda = CalendarStore(context).getAll()
            .filter { it.occursOn(today) && !it.done }
            .sortedBy { if (it.allDay) -1 else Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalTime().toSecondOfDay() }
            .map { e ->
                if (e.allDay || e.isTask) e.title
                else "${e.title} ${Instant.ofEpochMilli(e.startMillis).atZone(zone).toLocalTime().format(timeFmt)}"
            }
        val spent = ExpenseStore(context).getAll()
            .filter { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() == yesterday }
            .sumOf { it.amountPaise }
        val screen = if (FocusGate.hasUsageAccess(context)) ScreenTime.forDay(context, yesterday).sumOf { it.second } else null
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        val metres = MoveDatabase.get(context).dao().distanceSince(weekStart).first()
        return Inputs(agenda, spent, screen, FocusPrefs(context).blockedOn(yesterday), metres / 1000)
    }

    internal fun post(context: Context, lines: List<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.brief_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(context, 0, openShellIntent(context, null), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.brief_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setGroup(CHANNEL) // never let Android bundle Quiver's notifications — see ExpenseCapture
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private const val NOTIF_ID = 4200
}

class MorningBriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!MorningBrief.isOn(applicationContext)) return Result.success()
        runCatching { MorningBrief.post(applicationContext, MorningBrief.lines(MorningBrief.gather(applicationContext))) }
        // Append: REPLACE from inside the running worker would cancel it.
        MorningBrief.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
