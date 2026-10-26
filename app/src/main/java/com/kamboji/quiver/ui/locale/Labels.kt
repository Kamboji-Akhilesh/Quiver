package com.kamboji.quiver.ui.locale

import com.kamboji.quiver.R
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.RepeatUnit
import com.kamboji.quiver.currency.HistoryRange
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.move.MoveType
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Translated display names for data enums. The enums keep their English
 * `label`s for logic, storage and the AI's prompts; screens use these.
 */

val ExpenseCategory.displayName: String
    get() = tr(
        when (this) {
            ExpenseCategory.FOOD -> R.string.cat_food
            ExpenseCategory.GROCERIES -> R.string.cat_groceries
            ExpenseCategory.TRANSPORT -> R.string.cat_transport
            ExpenseCategory.SHOPPING -> R.string.cat_shopping
            ExpenseCategory.BILLS -> R.string.cat_bills
            ExpenseCategory.HEALTH -> R.string.cat_health
            ExpenseCategory.ENTERTAINMENT -> R.string.cat_fun
            ExpenseCategory.OTHER -> R.string.cat_other
        },
    )

val MoveType.displayName: String
    get() = tr(
        when (this) {
            MoveType.Run -> R.string.move_type_run
            MoveType.Walk -> R.string.move_type_walk
            MoveType.Ride -> R.string.move_type_ride
            MoveType.Hike -> R.string.move_type_hike
        },
    )

val AlertStyle.displayName: String
    get() = tr(
        when (this) {
            AlertStyle.NONE -> R.string.alert_style_none
            AlertStyle.NOTIFICATION -> R.string.alert_style_notification
            AlertStyle.CALL -> R.string.alert_style_call
        },
    )

/** "5 minutes before" — or "At time". */
val AlertLead.displayName: String
    get() = if (this == AlertLead.AT_TIME) tr(R.string.lead_at_time) else tr(R.string.lead_before, durationText(minutes))

/** Chip form: "5 min", "1 hour", "At time". */
val AlertLead.shortName: String
    get() = when {
        this == AlertLead.AT_TIME -> tr(R.string.lead_at_time)
        minutes < 60 -> trPlural(R.plurals.duration_minutes_short, minutes.toInt())
        else -> durationText(minutes)
    }

/** "2 weeks" — the count and unit shown in the repeat stepper. */
fun repeatUnitCount(unit: RepeatUnit, count: Int): String = when (unit) {
    RepeatUnit.NONE -> tr(R.string.repeat_none)
    RepeatUnit.DAILY -> trPlural(R.plurals.duration_days, count)
    RepeatUnit.WEEKLY -> trPlural(R.plurals.duration_weeks, count)
    RepeatUnit.MONTHLY -> trPlural(R.plurals.duration_months, count)
}

/** "Every day", "Every 2 weeks", "Does not repeat". */
val CalendarEntry.repeatSummary: String
    get() = when {
        repeatUnit == RepeatUnit.NONE -> tr(R.string.repeat_none)
        repeatInterval <= 1 -> tr(
            when (repeatUnit) {
                RepeatUnit.DAILY -> R.string.repeat_every_day
                RepeatUnit.WEEKLY -> R.string.repeat_every_week
                else -> R.string.repeat_every_month
            },
        )
        else -> tr(R.string.repeat_every_n, repeatUnitCount(repeatUnit, repeatInterval))
    }

val HistoryRange.displayName: String get() = trPlural(R.plurals.duration_days, days)

/** "2 minutes", "1 hour", "2 days". */
val DeleteDelay.displayName: String
    get() = when (unit) {
        DelayUnit.MINUTES -> trPlural(R.plurals.duration_minutes, value.toInt())
        DelayUnit.HOURS -> trPlural(R.plurals.duration_hours, value.toInt())
        DelayUnit.DAYS -> trPlural(R.plurals.duration_days, value.toInt())
    }

private fun durationText(minutes: Long): String = when {
    minutes % 1440 == 0L -> trPlural(R.plurals.duration_days, (minutes / 1440).toInt())
    minutes % 60 == 0L -> trPlural(R.plurals.duration_hours, (minutes / 60).toInt())
    else -> trPlural(R.plurals.duration_minutes, minutes.toInt())
}


/** "Oct 26" — never built from English enum names. */
fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

/** "October" as a standalone title. */
fun monthName(month: Month): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
