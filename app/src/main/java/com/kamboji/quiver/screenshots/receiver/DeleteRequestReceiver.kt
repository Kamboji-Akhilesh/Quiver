package com.kamboji.quiver.screenshots.receiver

import android.app.RecoverableSecurityException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.kamboji.quiver.screenshots.ui.DeleteConfirmActivity

class DeleteRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SS_APP", "DeleteRequestReceiver: onReceive called")

        val uriString = intent.getStringExtra("uri") ?: run {
            Log.e("SS_APP", "DeleteRequestReceiver: uri is null")
            return
        }
        val uri = Uri.parse(uriString)
        Log.d("SS_APP", "DeleteRequestReceiver: deleting uri=$uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use MediaStore.createDeleteRequest
            try {
                val deleteIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )

                deleteIntent.intentSender.let { sender ->
                    Log.d("SS_APP", "DeleteRequestReceiver: starting DeleteConfirmActivity")
                    val activityIntent = Intent(context, DeleteConfirmActivity::class.java)
                    activityIntent.putExtra("sender", sender)
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(activityIntent)
                }
            } catch (e: Exception) {
                Log.e("SS_APP", "DeleteRequestReceiver: Error creating delete request", e)
            }
        } else {
            // Android 10 (API 29): Try direct delete, handle RecoverableSecurityException
            try {
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                Log.d("SS_APP", "DeleteRequestReceiver: deleted $rowsDeleted rows")
            } catch (e: SecurityException) {
                Log.d("SS_APP", "DeleteRequestReceiver: SecurityException, trying recovery")
                if (e is RecoverableSecurityException) {
                    val sender = e.userAction.actionIntent.intentSender
                    val activityIntent = Intent(context, DeleteConfirmActivity::class.java)
                    activityIntent.putExtra("sender", sender)
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(activityIntent)
                }
            }
        }
    }
}

