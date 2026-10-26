package com.kamboji.quiver.backup

import android.content.Context
import android.net.Uri
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.currency.data.RateAlertStore
import com.kamboji.quiver.currency.worker.RateAlertWorker
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.move.data.MoveDatabase
import com.kamboji.quiver.notes.data.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reads and writes backup files. Off the main thread: key derivation takes a few seconds by design. */
object BackupIO {

    /** The file is encrypted and no password was given. */
    class NeedsPassword : Exception()

    data class Counts(val notes: Int, val calendar: Int, val expenses: Int, val alerts: Int, val moves: Int) {
        val total get() = notes + calendar + expenses + alerts + moves
    }

    suspend fun export(context: Context, uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val dao = MoveDatabase.get(context).dao()
        val doc = QuiverBackup.export(
            NotesStore(context).getAll(),
            CalendarStore(context).getAll(),
            ExpenseStore(context).getAll(),
            RateAlertStore(context).getAll(),
            dao.allFinished().map { QuiverBackup.Move(it, dao.points(it.id)) },
        )
        val sealed = BackupCrypto.encrypt(doc.toString().toByteArray(), password)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(sealed.toString(2).toByteArray()) }
    }

    /** Reads [uri]; throws [NeedsPassword] for an encrypted file when [password] is null. */
    suspend fun read(context: Context, uri: Uri, password: CharArray?): QuiverBackup.Imported = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        if (!BackupCrypto.isEncrypted(json)) return@withContext QuiverBackup.parse(text) // older, plain backups
        if (password == null) throw NeedsPassword()
        QuiverBackup.parse(String(BackupCrypto.decrypt(json, password)))
    }

    /** Merges [imported] into this phone: nothing existing is overwritten. */
    suspend fun merge(context: Context, imported: QuiverBackup.Imported): Counts = withContext(Dispatchers.IO) {
        val notesStore = NotesStore(context)
        val notes = QuiverBackup.mergeNotes(notesStore.getAll(), imported.notes) { notesStore.nextId() }
        notesStore.saveAll(notes.merged)

        val calStore = CalendarStore(context)
        val cal = QuiverBackup.mergeEntries(calStore.getAll(), imported.entries) { calStore.nextId() }
        calStore.saveAll(cal.merged)
        // Imported reminders should fire like locally-created ones.
        cal.added.forEach { AlertScheduler.reschedule(context, it) }

        val expStore = ExpenseStore(context)
        val exp = QuiverBackup.mergeExpenses(expStore.getAll(), imported.expenses) { expStore.nextId() }
        expStore.saveAll(exp.merged)

        val alertStore = RateAlertStore(context)
        val alerts = QuiverBackup.mergeRateAlerts(alertStore.getAll(), imported.rateAlerts) { alertStore.nextId() }
        alertStore.saveAll(alerts.merged)
        // Imported alerts need the background check running to ever fire.
        if (alerts.added.any { !it.triggered }) RateAlertWorker.ensureScheduled(context)

        val dao = MoveDatabase.get(context).dao()
        val moves = QuiverBackup.newMoves(dao.allFinished(), imported.moves)
        moves.forEach { dao.insertWithPoints(it.move, it.points) }

        Counts(notes.added.size, cal.added.size, exp.added.size, alerts.added.size, moves.size)
    }
}
