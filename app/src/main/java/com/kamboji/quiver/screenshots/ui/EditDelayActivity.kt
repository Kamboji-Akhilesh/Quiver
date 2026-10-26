package com.kamboji.quiver.screenshots.ui

import com.kamboji.quiver.ui.locale.displayName

import com.kamboji.quiver.R

import com.kamboji.quiver.ui.locale.tr
import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkManager
import com.kamboji.quiver.screenshots.data.models.DELAY_PRESETS
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.notification.ScreenshotNotification
import com.kamboji.quiver.screenshots.worker.DeleteWorker
import com.kamboji.quiver.ui.components.Pill
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver
import com.kamboji.quiver.ui.theme.QuiverTheme
import java.util.UUID

/**
 * The screenshot notification's "Edit time" action: a Quiver-styled sheet that
 * floats over whatever app the user is in (translucent window), so changing
 * the delay never pulls them into Quiver.
 */
class EditDelayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workId = intent.getStringExtra("work_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val notificationId = intent.getIntExtra("notification_id", -1)
        val uri = intent.getStringExtra("uri")?.let(Uri::parse)
        if (workId == null || uri == null) {
            Log.e("SS_APP", "EditDelayActivity: Missing data")
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            QuiverTheme(dark = true, accent = Accents.Screenshots) {
                QuiverModalSheet(onDismiss = { finish() }) { hide ->
                    DelayPicker(
                        onPick = { rescheduleDelete(workId, notificationId, uri, it); hide() },
                        onInvalid = { Toast.makeText(this, tr(R.string.delay_enter_a_number_of_minutes), Toast.LENGTH_SHORT).show() },
                    )
                }
            }
        }
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

        Toast.makeText(this, tr(R.string.delay_will_delete_in_x, newDelay.displayName), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelayPicker(onPick: (DeleteDelay) -> Unit, onInvalid: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Screenshots
    var minutes by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(tr(R.string.delay_delete_screenshot_after), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DELAY_PRESETS.forEach { d -> Pill(d.displayName, false, ac, { onPick(d) }) }
        }
        Text(tr(R.string.delay_or_a_custom_time), fontSize = 13.sp, color = colors.dim)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(colors.surf)
                    .border(1.dp, colors.border, RoundedCornerShape(100.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (minutes.isEmpty()) Text(tr(R.string.delay_minutes), color = colors.faint, fontSize = 14.sp)
                BasicTextField(
                    minutes, { new -> minutes = new.filter(Char::isDigit).take(5) }, singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(ac.a),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(100.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    .clickable {
                        val m = minutes.toLongOrNull()
                        if (m != null && m > 0) onPick(DeleteDelay(m, DelayUnit.MINUTES)) else onInvalid()
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) { Text(tr(R.string.delay_set), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
