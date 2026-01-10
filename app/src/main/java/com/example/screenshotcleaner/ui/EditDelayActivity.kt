package com.example.screenshotcleaner.ui

import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import com.example.screenshotcleaner.data.models.DeleteDelay
import com.example.screenshotcleaner.data.models.DelayUnit
import com.example.screenshotcleaner.notification.ScreenshotNotification
import com.example.screenshotcleaner.worker.DeleteWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID

class EditDelayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("SS_APP", "EditDelayActivity: onCreate")

        // Get the data from the intent
        val workIdString = intent.getStringExtra("work_id")
        val notificationId = intent.getIntExtra("notification_id", -1)
        val uriString = intent.getStringExtra("uri")

        if (workIdString == null || uriString == null) {
            Log.e("SS_APP", "EditDelayActivity: Missing data")
            finish()
            return
        }

        val uri = Uri.parse(uriString)
        val workId = UUID.fromString(workIdString)

        // Show the time picker dialog
        showTimePickerDialog(workId, notificationId, uri)
    }

    private fun showTimePickerDialog(oldWorkId: UUID, notificationId: Int, uri: Uri) {
        val options = arrayOf(
            "1 minute",
            "2 minutes",
            "5 minutes",
            "10 minutes",
            "30 minutes",
            "1 hour",
            "2 hours",
            "Custom time..."
        )

        val delays = arrayOf(
            DeleteDelay(1, DelayUnit.MINUTES),
            DeleteDelay(2, DelayUnit.MINUTES),
            DeleteDelay(5, DelayUnit.MINUTES),
            DeleteDelay(10, DelayUnit.MINUTES),
            DeleteDelay(30, DelayUnit.MINUTES),
            DeleteDelay(1, DelayUnit.HOURS),
            DeleteDelay(2, DelayUnit.HOURS),
            null // Custom
        )

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Delete screenshot after...")
            .setItems(options) { _, which ->
                if (which == options.size - 1) {
                    // Custom time selected
                    showCustomTimeDialog(oldWorkId, notificationId, uri)
                } else {
                    val selectedDelay = delays[which]!!
                    rescheduleDelete(oldWorkId, notificationId, uri, selectedDelay)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun showCustomTimeDialog(oldWorkId: UUID, notificationId: Int, uri: Uri) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val inputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputFilledStyle).apply {
            hint = "Minutes"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputLayout.addView(editText)
        layout.addView(inputLayout)

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Custom time")
            .setMessage("Enter the number of minutes before deletion")
            .setView(layout)
            .setPositiveButton("Set") { _, _ ->
                val minutes = editText.text.toString().toLongOrNull()
                if (minutes != null && minutes > 0) {
                    val customDelay = DeleteDelay(minutes, DelayUnit.MINUTES)
                    rescheduleDelete(oldWorkId, notificationId, uri, customDelay)
                } else {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun rescheduleDelete(oldWorkId: UUID, notificationId: Int, uri: Uri, newDelay: DeleteDelay) {
        // Cancel the old scheduled work
        WorkManager.getInstance(this).cancelWorkById(oldWorkId)
        Log.d("SS_APP", "EditDelayActivity: cancelled old work $oldWorkId")

        // Schedule new deletion with the selected delay (pass notificationId so worker can dismiss it)
        val newWorkId = DeleteWorker.schedule(this, uri, newDelay, notificationId)
        Log.d("SS_APP", "EditDelayActivity: scheduled new work $newWorkId with delay ${newDelay.value} ${newDelay.unit.name}")

        // Dismiss the old notification
        if (notificationId != -1) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        // Show a new notification with updated time
        ScreenshotNotification.showUpdatedNotification(this, uri, newWorkId, newDelay)

        Toast.makeText(this, "Will delete in ${newDelay.value} ${newDelay.unit.name.lowercase()}", Toast.LENGTH_SHORT).show()
        finish()
    }
}

