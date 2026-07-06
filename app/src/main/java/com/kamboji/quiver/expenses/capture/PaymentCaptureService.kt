package com.kamboji.quiver.expenses.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kamboji.quiver.R
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore

/**
 * Auto-captures UPI/bank payments from their notifications — entirely
 * on-device (this is the private alternative to fintech apps uploading your
 * SMS). UPI and SMS apps are parsed with the full [PaymentParser]; notifications
 * from any other app must additionally contain "debited" (that covers the long
 * tail of bank apps without whitelisting hundreds of them, while keeping random
 * apps' promo notifications out).
 *
 * One payment often notifies twice (UPI app + bank SMS) — [CaptureDedup]
 * collapses them by reference number, or by amount within a short window when
 * no ref is present.
 */
class PaymentCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = title + "\n" + (if (big.length >= text.length) big else text)

        val trusted = sbn.packageName in UPI_APPS || sbn.packageName in SMS_APPS
        if (!trusted && "debited" !in body.lowercase()) return

        val payment = PaymentParser.parse(body) ?: return
        if (!CaptureDedup.firstSeen(this, payment)) return

        val store = ExpenseStore(this)
        val category = ExpenseCategory.parse(payment.payee)
        val expense = Expense(
            id = store.nextId(),
            amountPaise = payment.amountPaise,
            category = category,
            note = payment.payee.ifBlank { "UPI payment" },
            atMillis = System.currentTimeMillis(),
            auto = true,
            needsReview = true,
        )
        store.saveAll(store.getAll() + expense)
        notifyCaptured(expense)
    }

    private fun notifyCaptured(e: Expense) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Auto-tracked expenses", NotificationManager.IMPORTANCE_LOW),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = open?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Logged ${ExpenseMath.formatPaise(e.amountPaise)} · ${e.category.label}")
            .setContentText("${e.note} — tap to review in Expenses")
            .setAutoCancel(true)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
        nm.notify((e.id % 1000).toInt() + NOTIF_BASE, n)
    }

    companion object {
        private const val CHANNEL = "quiver_expense_capture"
        private const val NOTIF_BASE = 3_200_000

        /** UPI apps whose notifications we always try to parse. */
        val UPI_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",                      // BHIM
            "com.dreamplug.androidapp",                // CRED
            "in.amazon.mShop.android.shopping",        // Amazon (Pay)
            "com.whatsapp",                            // WhatsApp Pay
        )

        /** Stock/common SMS apps — bank debit SMS arrive as their notifications. */
        val SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.oneplus.mms",
        )

        /** Whether the user has granted notification access to Quiver. */
        fun isEnabled(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(context.packageName) == true

        /** The component to highlight in the notification-access settings page. */
        fun component(context: Context): ComponentName =
            ComponentName(context, PaymentCaptureService::class.java)
    }
}
