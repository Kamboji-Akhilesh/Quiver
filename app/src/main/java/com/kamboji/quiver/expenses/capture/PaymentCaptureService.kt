package com.kamboji.quiver.expenses.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Auto-captures payments from UPI-app notifications — entirely on-device.
 * Bank SMS are read from the inbox by [SmsCapture] (reliable, never redacted);
 * this listener covers GPay/PhonePe/Paytm, bank apps, and — only when SMS
 * permission isn't granted — SMS-app notifications as a fallback.
 * [CaptureFilter] decides what may be parsed, so a friend's chat message with
 * an amount in it never becomes an expense.
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

        val source = when (sbn.packageName) {
            in UPI_APPS -> CaptureFilter.Source.UpiApp
            in SMS_APPS -> CaptureFilter.Source.SmsApp
            else -> CaptureFilter.Source.Other
        }
        val isChat = sbn.notification.category == Notification.CATEGORY_MESSAGE
        if (!CaptureFilter.shouldParseNotification(source, title, body, isChat, SmsCapture.hasPermission(this))) return

        val payment = PaymentParser.parse(body) ?: return
        ExpenseCapture.record(this, payment, System.currentTimeMillis())
    }

    companion object {
        /** UPI apps whose notifications we always try to parse. */
        val UPI_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",                      // BHIM
            "com.dreamplug.androidapp",                // CRED
            "in.amazon.mShop.android.shopping",        // Amazon (Pay)
            // Not WhatsApp: its notifications are mostly chats ("I paid ₹500 for dinner").
        )

        /** Stock/common SMS apps — bank debit SMS arrive as their notifications. */
        val SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.oneplus.mms",
            "com.truecaller",
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
