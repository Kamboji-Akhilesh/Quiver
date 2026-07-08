package com.kamboji.quiver.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.kamboji.quiver.ai.agent.WhenResolver
import com.kamboji.quiver.calendar.QuickAddParser
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.notes.data.NotesStore
import com.kamboji.quiver.screenshots.notification.ScreenshotNotification
import com.kamboji.quiver.ui.shell.QuiverActivity
import com.kamboji.quiver.ui.shell.ShellCommand
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver
import com.kamboji.quiver.ui.theme.QuiverTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN_FMT = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

/**
 * ACTION_SEND router: shared text is offered as note / task / expense /
 * AI question; a shared gallery image goes straight into the screenshot-cleanup
 * flow. Writes happen here (stores are synchronous), then the shell opens on
 * the right screen via a one-shot ShellCommand.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        if (intent.type.orEmpty().startsWith("image/")) {
            routeImage()
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        // Browsers often put the page title in EXTRA_SUBJECT and only the URL
        // in EXTRA_TEXT — keep both.
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val shared = if (subject.isNotEmpty() && subject !in text) "$subject\n$text".trim() else text
        if (shared.isEmpty()) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            QuiverTheme(dark = true, accent = Accents.Hub) {
                ShareChooser(
                    shared,
                    onNote = { saveNote(shared) },
                    onTask = { saveTask(shared) },
                    onExpense = { saveExpense(shared) },
                    onAi = { openShell(aiPrefill = shared) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    /** Shared image → the same scheduled-deletion flow a fresh screenshot gets. */
    private fun routeImage() {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        when {
            uri == null -> {}
            // Deletion needs a MediaStore item; other providers (cloud photos,
            // messaging apps) hand out URIs we can't delete later.
            uri.authority == MediaStore.AUTHORITY ->
                {
                    ScreenshotNotification.handleScreenshot(this, uri)
                    Toast.makeText(this, "Cleanup scheduled — adjust it from the notification", Toast.LENGTH_LONG).show()
                }
            else ->
                Toast.makeText(this, "Only gallery images can be scheduled for cleanup", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun saveNote(text: String) {
        val (title, body) = SharedText.noteSplit(text)
        val store = NotesStore(this)
        val now = System.currentTimeMillis()
        store.saveAll(store.getAll() + Note(store.nextId(), title, body, 0, false, now, now))
        openShell(AppKey.Notes, toast = "Note saved")
    }

    private fun saveTask(text: String) {
        val line = SharedText.oneLine(text, 120)
        val q = QuickAddParser.parse(line)
        // No "when" in the text → tomorrow morning, so the reminder can still fire.
        val start = WhenResolver.resolve(
            q?.dateText ?: if (q?.timeText == null) "tomorrow" else null,
            q?.timeText,
        )
        val store = CalendarStore(this)
        val entry = CalendarEntry(
            id = store.nextId(), type = EntryType.TASK, title = q?.title ?: line,
            startMillis = start, endMillis = null, allDay = false, done = false,
            alertStyle = AlertStyle.NOTIFICATION, alertLead = AlertLead.AT_TIME,
            createdAtMillis = System.currentTimeMillis(),
        )
        store.saveAll(store.getAll() + entry)
        AlertScheduler.reschedule(this, entry)
        val label = WHEN_FMT.format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))
        openShell(AppKey.Calendar, toast = "Task added · $label")
    }

    private fun saveExpense(text: String) {
        val paise = SharedText.amountPaise(text)
        if (paise == null) {
            openShell(AppKey.Expenses, startNew = true, toast = "No amount found — enter it manually")
            return
        }
        val store = ExpenseStore(this)
        store.saveAll(
            store.getAll() + Expense(
                id = store.nextId(), amountPaise = paise,
                category = ExpenseCategory.parse(text),
                note = SharedText.oneLine(text, 60),
                atMillis = System.currentTimeMillis(),
                needsReview = true, // category is a guess until the user confirms
            ),
        )
        openShell(AppKey.Expenses, toast = "Added ${ExpenseMath.formatPaise(paise)} — review category")
    }

    private fun openShell(
        target: AppKey? = null,
        toast: String? = null,
        startNew: Boolean = false,
        aiPrefill: String? = null,
    ) {
        val i = Intent(this, QuiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (aiPrefill != null) {
            i.action = ShellCommand.ACTION_ASK_AI
            i.putExtra(ShellCommand.EXTRA_AI_PREFILL, aiPrefill)
        } else {
            i.action = ShellCommand.ACTION_OPEN
            target?.let { i.putExtra(ShellCommand.EXTRA_TARGET, it.name) }
            toast?.let { i.putExtra(ShellCommand.EXTRA_TOAST, it) }
            if (startNew) i.putExtra(ShellCommand.EXTRA_START_NEW, true)
        }
        startActivity(i)
        finish()
    }
}

@Composable
private fun ShareChooser(
    text: String,
    onNote: () -> Unit,
    onTask: () -> Unit,
    onExpense: () -> Unit,
    onAi: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Quiver.colors
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                // Swallow taps so they don't fall through to the scrim.
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add to Quiver", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(colors.surf).padding(12.dp),
            ) {
                Text(text, fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            ShareOption(Icons.Outlined.StickyNote2, "Save as note", "Keep the text in Notes", Accents.Notes, onNote)
            ShareOption(Icons.Outlined.TaskAlt, "Add as task", "Reminder on your calendar", Accents.Calendar, onTask)
            ShareOption(Icons.Outlined.Payments, "Log expense", "Record the amount in Expenses", Accents.Expenses, onExpense)
            ShareOption(Icons.Filled.AutoAwesome, "Ask Quiver AI", "Hand it to the on-device assistant", Accents.Hub, onAi)
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, label: String, detail: String, accent: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surf)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent.a.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = accent.txt(colors.dark)) }
        Column {
            Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text(detail, fontSize = 12.sp, color = colors.dim)
        }
    }
}
