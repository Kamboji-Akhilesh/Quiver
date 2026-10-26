package com.kamboji.quiver.expenses.capture

import com.kamboji.quiver.ui.locale.displayName

import com.kamboji.quiver.ui.locale.tr
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
        if (!CaptureDedup.firstSeen(context, p)) return false
        val store = ExpenseStore(context)
        val expense = Expense(
            id = store.nextId(),
            amountPaise = p.amountPaise,
            category = ExpenseCategory.parse(p.payee),
            note = p.payee.ifBlank { tr(R.string.capture_upi_payment) },
            atMillis = atMillis,
            auto = true,
            needsReview = true,
        )
        store.saveAll(store.getAll() + expense)
        notifyCaptured(context, expense)
        return true
    }

    private fun notifyCaptured(context: Context, e: Expense) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.capture_auto_tracked_expenses), NotificationManager.IMPORTANCE_LOW),
        )
        val pi = PendingIntent.getActivity(context, 0, openShellIntent(context, AppKey.Expenses), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.capture_logged_x_x, ExpenseMath.formatPaise(e.amountPaise), e.category.displayName))
            .setContentText(tr(R.string.capture_x_tap_to_review_in, e.note))
            .setAutoCancel(true)
            .setContentIntent(pi)
            // Every Quiver notification sets its own group: ungrouped ones get auto-bundled by
            // Android, and tapping the bundle opens the app's main screen instead of this target.
            .setGroup(CHANNEL)
            .build()
        nm.notify((e.id % 1000).toInt() + NOTIF_BASE, n)
    }
}
