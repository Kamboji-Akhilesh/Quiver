package com.kamboji.quiver.expenses.capture

import com.kamboji.quiver.ui.locale.displayName

import com.kamboji.quiver.ui.locale.tr
import com.kamboji.quiver.ui.locale.trPlural
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.kamboji.quiver.R
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.widgets.openShellIntent

/** The one place a captured payment becomes an expense — shared by the SMS reader and the notification listener. */
object ExpenseCapture {

    private const val CHANNEL = "quiver_expense_capture"
    private const val NOTIF_BASE = 3_200_000

    /** Saves [p] as a needs-review expense unless it's a duplicate; returns whether it was saved. */
    fun record(context: Context, p: PaymentParser.ParsedPayment, atMillis: Long): Boolean {
        if (!CaptureDedup.firstSeen(context, p, atMillis)) return false
        val store = ExpenseStore(context)
        val expense = expense(store.nextId(), p, atMillis, needsReview = true)
        store.put(expense)
        notifyCaptured(context, expense)
        return true
    }

    /**
     * Past payments (oldest first), saved in one write with one summary
     * notification. They skip the review queue — hundreds of old payments
     * there would bury the new ones. Returns how many were saved.
     */
    fun recordHistory(context: Context, payments: List<Pair<PaymentParser.ParsedPayment, Long>>): Int {
        val seen = CaptureDedup.recent(context).toMutableList()
        val store = ExpenseStore(context)
        val added = payments.mapNotNull { (p, at) ->
            if (CaptureDedup.duplicate(seen, p, at)) return@mapNotNull null
            seen += CaptureDedup.Seen(at, p.amountPaise, p.ref)
            expense(store.nextId(), p, at, needsReview = false)
        }
        if (added.isEmpty()) return 0
        store.putAll(added)
        CaptureDedup.save(context, seen)
        notify(context, NOTIF_BASE - 1, tr(R.string.capture_history_title, trPlural(R.plurals.count_expenses, added.size)), tr(R.string.capture_history_text))
        return added.size
    }

    private fun expense(id: Long, p: PaymentParser.ParsedPayment, atMillis: Long, needsReview: Boolean) = Expense(
        id = id,
        amountPaise = p.amountPaise,
        category = ExpenseCategory.parse(p.payee),
        note = p.payee.ifBlank { tr(R.string.capture_upi_payment) },
        atMillis = atMillis,
        auto = true,
        needsReview = needsReview,
    )

    private fun notifyCaptured(context: Context, e: Expense) = notify(
        context, (e.id % 1000).toInt() + NOTIF_BASE,
        tr(R.string.capture_logged_x_x, ExpenseMath.formatPaise(e.amountPaise), e.category.displayName),
        tr(R.string.capture_x_tap_to_review_in, e.note),
    )

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.capture_auto_tracked_expenses), NotificationManager.IMPORTANCE_LOW),
        )
        val pi = PendingIntent.getActivity(context, 0, openShellIntent(context, AppKey.Expenses), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            // Every Quiver notification sets its own group: ungrouped ones get auto-bundled by
            // Android, and tapping the bundle opens the app's main screen instead of this target.
            .setGroup(CHANNEL)
            .build()
        nm.notify(id, n)
    }
}
