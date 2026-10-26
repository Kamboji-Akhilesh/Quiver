package com.kamboji.quiver.ui.notes

import com.kamboji.quiver.ui.theme.Body
import android.text.format.DateUtils
import androidx.compose.material.icons.filled.Add
import com.kamboji.quiver.ui.locale.tr
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.ai.VoiceController
import com.kamboji.quiver.ai.deviceVoiceLang
import com.kamboji.quiver.notes.NotesViewModel
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvIconButton
import androidx.compose.ui.res.stringResource
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

@Composable
fun NotesScreen(state: QuiverState) {
    val vm: NotesViewModel = viewModel()
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }

    // Deep-link from Home's "New note" shortcut.
    LaunchedEffect(state.notesStartNew) {
        if (state.notesStartNew) { editId = null; editing = true; state.notesStartNew = false }
    }

    if (editing) {
        NoteEditor(vm, editId, onClose = { editing = false; editId = null })
    } else {
        NotesList(
            state, vm,
            onNew = { editId = null; editing = true },
            onOpen = { editId = it; editing = true },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesList(state: QuiverState, vm: NotesViewModel, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Notes
    val notes by vm.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<Note?>(null) }

    val filtered = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
    val pinned = filtered.filter { it.pinned }
    val others = filtered.filter { !it.pinned }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QvTopBar(
                stringResource(R.string.title_notes), ac, onBack = { state.go(AppKey.Hub) },
                trailing = { QvIconButton(Icons.Filled.Add, onNew, background = ac.a, tint = ac.on, contentDescription = tr(R.string.notes_new_note)) },
            )
            // search
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp).glass(colors, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr(R.string.notes_search_notes), color = colors.dim, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), cursorBrush = SolidColor(ac.a), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                notes.isEmpty() -> EmptyNotes(ac)
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr(R.string.notes_no_matching_notes), color = colors.dim)
                }
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(tr(R.string.notes_pinned)) }
                        items(pinned, key = { it.id }) { NoteCard(it, { onOpen(it.id) }, { actionsFor = it }) }
                    }
                    if (others.isNotEmpty()) {
                        if (pinned.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(tr(R.string.notes_others)) }
                        items(others, key = { it.id }) { NoteCard(it, { onOpen(it.id) }, { actionsFor = it }) }
                    }
                }
            }
        }

        actionsFor?.let { note ->
            NoteActionsSheet(
                note,
                onDismiss = { actionsFor = null },
                onPin = { vm.togglePin(note.id) },
                onColor = { vm.setColor(note.id, it) },
                onDelete = {
                    vm.delete(note.id)
                    state.toast(tr(R.string.notes_note_deleted), ToastKind.Error)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onLongPress: () -> Unit) {
    val colors = Quiver.colors
    val nc = NotePalette.of(note.colorId)
    // A coloured note is a light tint of its colour with a matching outline; the default is a plain card.
    val tinted = note.colorId != 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (tinted) nc.accent.copy(alpha = 0.10f) else colors.surf)
            .border(1.dp, if (tinted) nc.accent.copy(alpha = 0.35f) else colors.border, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (note.title.isNotBlank()) {
            Text(note.title.trim(), color = colors.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        }
        if (note.body.isNotBlank()) {
            val md = rememberMarkdown(note.body.trim(), 12.5.sp, colors.dim, colors.faint, nc.accent)
            Text(md, maxLines = if (note.title.isBlank()) 12 else 8, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        }
        Text(
            DateUtils.getRelativeTimeSpanString(note.updatedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            fontSize = 11.sp, color = colors.faint,
        )
    }
}

@Composable
private fun EmptyNotes(ac: com.kamboji.quiver.ui.theme.Accent) {
    val colors = Quiver.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.surf)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.StickyNote2, null, Modifier.size(24.dp), tint = ac.iconColor(colors.dark)) }
            Spacer(Modifier.height(14.dp))
            Text(tr(R.string.notes_no_notes_yet), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = colors.text)
            Text(tr(R.string.notes_tap_new_note_to_jot), fontSize = 13.5.sp, color = colors.dim)
        }
    }
}

@Composable
private fun NoteActionsSheet(
    note: Note,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onColor: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Quiver.colors
    QuiverModalSheet(onDismiss) { hide ->
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionRow(if (note.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, if (note.pinned) tr(R.string.notes_unpin) else tr(R.string.notes_pin_to_top)) { onPin(); hide() }
            // colour swatches
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotePalette.colors.forEachIndexed { i, c ->
                    val sel = i == note.colorId
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.accent)
                            .border(2.5.dp, if (sel) colors.text else Color.Transparent, CircleShape)
                            .clickable { onColor(i); hide() },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.White) }
                }
            }
            ActionRow(Icons.Filled.DeleteOutline, tr(R.string.notes_delete), tint = Color(0xFFFB7185)) { onDelete(); hide() }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint ?: colors.text)
        Text(label, color = tint ?: colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NoteEditor(vm: NotesViewModel, id: Long?, onClose: () -> Unit) {
    val dark = Quiver.colors.dark
    val context = LocalContext.current
    val existing = remember(id) { id?.let { vm.byId(it) } }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var bodyValue by remember { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    val body = bodyValue.text
    var colorId by remember { mutableStateOf(existing?.colorId ?: 0) }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var savedId by remember { mutableStateOf(id) }
    var discarded by remember { mutableStateOf(false) }
    // Existing notes open in preview; new notes open straight into editing, cursor in the title.
    var preview by remember { mutableStateOf(existing != null) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) titleFocus.requestFocus() }
    val nc = NotePalette.of(colorId)
    val colors = Quiver.colors
    // Flat themes keep the page plain; the note's colour shows on its card and swatch.
    val fg = if (colors.flat) colors.text else nc.onBg(dark)
    val ground = if (colors.flat) colors.bg else nc.bg(dark)

    // Voice dictation: platform STT (no recording kept) inserts at the cursor.
    val voice = remember { VoiceController(context) }
    DisposableEffect(Unit) { onDispose { voice.release() } }
    var listening by remember { mutableStateOf(false) }
    fun startDictation() {
        listening = true
        voice.startListening(
            deviceVoiceLang(),
            onPartial = {},
            onResult = { text -> if (text.isNotBlank()) bodyValue = insertAtCursor(bodyValue, text); listening = false },
            onError = { listening = false },
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDictation()
    }
    fun toggleMic() {
        if (listening) { voice.stopListening(); listening = false; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startDictation()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun save() {
        if (discarded) return
        if (savedId == null) {
            val created = vm.create(title, body, colorId)
            savedId = created?.id
            if (pinned && savedId != null) vm.togglePin(savedId!!)
        } else {
            vm.update(savedId!!, title, body, colorId)
        }
    }
    fun close() { save(); onClose() }
    BackHandler { close() }

    Box(Modifier.fillMaxSize().background(ground)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, { close() }, contentDescription = tr(R.string.notes_back), tint = fg)
                Spacer(Modifier.weight(1f))
                // Voice dictation — inserts the transcript at the cursor.
                if (!preview) QvIconButton(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic, { toggleMic() },
                    contentDescription = if (listening) tr(R.string.notes_stop_dictation) else tr(R.string.notes_dictate),
                    background = if (listening) Quiver.accent.a else null, tint = if (listening) Quiver.accent.on else fg,
                )
                QvIconButton(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    { pinned = !pinned; savedId?.let { vm.togglePin(it) } },
                    contentDescription = tr(R.string.notes_pin), tint = if (pinned) Quiver.accent.txt(dark) else fg,
                )
                QvIconButton(if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility, { preview = !preview }, contentDescription = tr(R.string.notes_preview), tint = fg)
                QvIconButton(
                    Icons.Filled.DeleteOutline,
                    { discarded = true; savedId?.let { vm.delete(it) }; onClose() },
                    contentDescription = tr(R.string.notes_delete), tint = fg,
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                if (preview) {
                    if (title.isNotBlank()) Text(title.trim(), color = fg, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, lineHeight = 34.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    if (body.isNotBlank()) {
                        MarkdownView(
                            body, 16.sp, fg.copy(alpha = 0.92f), fg.copy(alpha = 0.6f), nc.accent,
                            onToggleCheckbox = { bodyValue = toggleCheckboxLine(bodyValue, it) },
                        )
                    } else {
                        Text(tr(R.string.notes_nothing_to_preview_yet), color = fg.copy(alpha = 0.4f), fontSize = 16.sp)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        if (title.isEmpty()) Text(tr(R.string.notes_title), color = fg.copy(alpha = 0.4f), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display)
                        BasicTextField(title, { title = it }, textStyle = TextStyle(color = fg, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, lineHeight = 34.sp), cursorBrush = SolidColor(nc.accent), modifier = Modifier.fillMaxWidth().focusRequester(titleFocus))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth()) {
                        if (body.isEmpty()) Text(tr(R.string.notes_start_writing_markdown_supported), color = fg.copy(alpha = 0.4f), fontSize = 16.sp)
                        BasicTextField(
                            bodyValue,
                            { bodyValue = continueList(bodyValue, it) ?: it },
                            textStyle = TextStyle(color = fg.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 22.sp, fontFamily = Body),
                            cursorBrush = SolidColor(nc.accent),
                            visualTransformation = rememberMarkdownTransformation(16.sp, fg.copy(alpha = 0.9f), fg.copy(alpha = 0.4f), nc.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
            // bottom panel: note colour, then formatting (edit mode)
            Column(
                Modifier.fillMaxWidth().background(if (colors.flat) colors.surf else Color.Transparent)
                    .border(1.dp, if (colors.flat) colors.border else Color.Transparent)
                    .navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotePalette.colors.forEachIndexed { i, c ->
                        val sel = i == colorId
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(if (i == 0) colors.surf2 else c.accent)
                                .border(2.dp, if (sel) fg else if (i == 0) colors.border2 else Color.Transparent, CircleShape)
                                .clickable { colorId = i },
                        )
                    }
                }
                if (!preview) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    FmtButton(label = "H", fg = fg) { bodyValue = linePrefix(bodyValue, "# ") }
                    FmtButton(icon = Icons.Filled.FormatBold, fg = fg) { bodyValue = wrap(bodyValue, "**") }
                    FmtButton(icon = Icons.Filled.FormatItalic, fg = fg) { bodyValue = wrap(bodyValue, "*") }
                    FmtButton(icon = Icons.AutoMirrored.Filled.FormatListBulleted, fg = fg) { bodyValue = linePrefix(bodyValue, "- ") }
                    FmtButton(icon = Icons.Outlined.CheckBox, fg = fg) { bodyValue = linePrefix(bodyValue, "- [ ] ") }
                    FmtButton(icon = Icons.Filled.FormatListNumbered, fg = fg) { bodyValue = linePrefix(bodyValue, "1. ") }
                    FmtButton(icon = Icons.Filled.StrikethroughS, fg = fg) { bodyValue = wrap(bodyValue, "~~") }
                    FmtButton(icon = Icons.Filled.Code, fg = fg) { bodyValue = wrap(bodyValue, "`") }
                    FmtButton(icon = Icons.Filled.FormatQuote, fg = fg) { bodyValue = linePrefix(bodyValue, "> ") }
                }
            }
        }
    }
}

@Composable
private fun FmtButton(label: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, label, Modifier.size(20.dp), tint = fg)
        else Text(label ?: "", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Inserts [insert] at the cursor (replacing any selection), spacing it from adjacent words. */
private fun insertAtCursor(v: TextFieldValue, insert: String): TextFieldValue {
    val start = minOf(v.selection.start, v.selection.end)
    val end = maxOf(v.selection.start, v.selection.end)
    val text = v.text
    val needsLeadSpace = start > 0 && !text[start - 1].isWhitespace()
    val piece = (if (needsLeadSpace) " " else "") + insert
    val newText = text.substring(0, start) + piece + text.substring(end)
    return TextFieldValue(newText, TextRange(start + piece.length))
}

/** Wraps the current selection (or inserts at the cursor) with [marker], e.g. ** for bold. */
private fun wrap(v: TextFieldValue, marker: String): TextFieldValue {
    val text = v.text
    val start = minOf(v.selection.start, v.selection.end)
    val end = maxOf(v.selection.start, v.selection.end)
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
    val cursor = if (selected.isEmpty()) start + marker.length else end + 2 * marker.length
    return TextFieldValue(newText, TextRange(cursor))
}

/** Inserts a line-level [prefix] (e.g. "# ", "- ") at the start of the current line. */
private fun linePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val pos = v.selection.start
    val lineStart = if (pos == 0) 0 else text.lastIndexOf('\n', pos - 1).let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return TextFieldValue(newText, TextRange(pos + prefix.length))
}

private val NUMBERED = Regex("^(\\d+)\\. ")
private val BULLET = Regex("^[-*] ")

/**
 * On Enter inside a list, continues it: a new bullet, the next number, or a new
 * checkbox. Pressing Enter on an empty list item exits the list instead.
 * Returns the adjusted value, or null to use [new] unchanged.
 */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
    if (new.text.length != old.text.length + 1) return null
    val caret = new.selection.start
    if (caret < 1 || new.text[caret - 1] != '\n') return null
    val before = new.text.substring(0, caret - 1)
    val prevStart = before.lastIndexOf('\n') + 1
    val prevLine = before.substring(prevStart)
    val checkbox = CHECKBOX.find(prevLine)
    val numbered = NUMBERED.find(prevLine)
    val bullet = BULLET.find(prevLine)
    val (markerLen, nextMarker) = when {
        checkbox != null -> checkbox.value.length to "- [ ] "
        numbered != null -> numbered.value.length to "${numbered.groupValues[1].toInt() + 1}. "
        bullet != null -> bullet.value.length to "- "
        else -> return null
    }
    val content = prevLine.substring(markerLen)
    return if (content.isBlank()) {
        // Empty item → drop the marker line and the newline (exit the list).
        val t = new.text
        TextFieldValue(t.substring(0, prevStart) + t.substring(caret), TextRange(prevStart))
    } else {
        val t = new.text
        TextFieldValue(t.substring(0, caret) + nextMarker + t.substring(caret), TextRange(caret + nextMarker.length))
    }
}

/** Flips the checkbox state ([ ] <-> [x]) on the line at [index]. */
private fun toggleCheckboxLine(v: TextFieldValue, index: Int): TextFieldValue {
    val lines = v.text.split("\n").toMutableList()
    if (index !in lines.indices) return v
    val cb = CHECKBOX.find(lines[index]) ?: return v
    val checked = cb.groupValues[2].lowercase() == "x"
    val rest = lines[index].substring(cb.value.length)
    lines[index] = "${cb.groupValues[1]} [${if (checked) " " else "x"}] $rest"
    return TextFieldValue(lines.joinToString("\n"), v.selection)
}
