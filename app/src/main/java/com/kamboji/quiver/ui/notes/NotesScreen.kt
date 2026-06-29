package com.kamboji.quiver.ui.notes

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.notes.NotesViewModel
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvIconButton
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
            QvTopBar("Notes", ac, onBack = { state.go(AppKey.Hub) })
            // search
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).glass(colors, RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search notes", color = colors.dim, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), cursorBrush = SolidColor(ac.a), singleLine = true)
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                notes.isEmpty() -> EmptyNotes(ac)
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching notes", color = colors.dim)
                }
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) { SectionLabel("Pinned") }
                        items(pinned, key = { it.id }) { NoteCard(it, { onOpen(it.id) }, { actionsFor = it }) }
                    }
                    if (others.isNotEmpty()) {
                        if (pinned.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { SectionLabel("Others") }
                        items(others, key = { it.id }) { NoteCard(it, { onOpen(it.id) }, { actionsFor = it }) }
                    }
                }
            }
        }

        // New-note button
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(100.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                .clickable(onClick = onNew).padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.StickyNote2, null, Modifier.size(20.dp), tint = Color(0xFF1A1408))
            Text("New note", color = Color(0xFF1A1408), fontWeight = FontWeight.Bold)
        }

        actionsFor?.let { note ->
            NoteActionsSheet(
                note,
                onDismiss = { actionsFor = null },
                onPin = { vm.togglePin(note.id) },
                onColor = { vm.setColor(note.id, it) },
                onDelete = {
                    vm.delete(note.id)
                    state.toast("Note deleted", ToastKind.Error)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onLongPress: () -> Unit) {
    val dark = Quiver.colors.dark
    val nc = NotePalette.of(note.colorId)
    val fg = nc.onBg(dark)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(nc.bg(dark))
            .border(1.dp, fg.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (note.pinned) {
            Icon(Icons.Filled.PushPin, null, Modifier.align(Alignment.End).size(15.dp), tint = nc.accent)
        }
        if (note.title.isNotBlank()) {
            Text(note.title.trim(), color = fg, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            if (note.body.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (note.body.isNotBlank()) {
            val md = rememberMarkdown(note.body.trim(), 13.5.sp, fg.copy(alpha = 0.78f), fg.copy(alpha = 0.55f), nc.accent)
            Text(md, maxLines = if (note.title.isBlank()) 12 else 8, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun EmptyNotes(ac: com.kamboji.quiver.ui.theme.Accent) {
    val colors = Quiver.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(ac.a.copy(alpha = 0.12f))
                    .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.StickyNote2, null, Modifier.size(40.dp), tint = ac.txt(colors.dark)) }
            Spacer(Modifier.height(16.dp))
            Text("No notes yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text("Tap “New note” to jot something down.", fontSize = 13.5.sp, color = colors.dim)
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
            ActionRow(if (note.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, if (note.pinned) "Unpin" else "Pin to top") { onPin(); hide() }
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
            ActionRow(Icons.Filled.DeleteOutline, "Delete", tint = Color(0xFFFB7185)) { onDelete(); hide() }
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
    val existing = remember(id) { id?.let { vm.byId(it) } }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var colorId by remember { mutableStateOf(existing?.colorId ?: 0) }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var savedId by remember { mutableStateOf(id) }
    var discarded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val nc = NotePalette.of(colorId)
    val fg = nc.onBg(dark)

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

    Box(Modifier.fillMaxSize().background(nc.bg(dark))) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, { close() }, contentDescription = "Back", tint = fg)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable { preview = !preview },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility, "Preview", Modifier.size(21.dp), tint = fg) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable {
                        pinned = !pinned
                        savedId?.let { vm.togglePin(it) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin", Modifier.size(21.dp), tint = if (pinned) nc.accent else fg) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable {
                        discarded = true
                        savedId?.let { vm.delete(it) }
                        onClose()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.DeleteOutline, "Delete", Modifier.size(21.dp), tint = fg) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                if (preview) {
                    if (title.isNotBlank()) Text(title.trim(), color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    if (body.isNotBlank()) {
                        Text(rememberMarkdown(body.trim(), 16.sp, fg.copy(alpha = 0.92f), fg.copy(alpha = 0.6f), nc.accent), lineHeight = 24.sp)
                    } else {
                        Text("Nothing to preview yet.", color = fg.copy(alpha = 0.4f), fontSize = 16.sp)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        if (title.isEmpty()) Text("Title", color = fg.copy(alpha = 0.4f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        BasicTextField(title, { title = it }, textStyle = TextStyle(color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp), cursorBrush = SolidColor(nc.accent), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth()) {
                        if (body.isEmpty()) Text("Start writing… (Markdown supported)", color = fg.copy(alpha = 0.4f), fontSize = 16.sp)
                        BasicTextField(body, { body = it }, textStyle = TextStyle(color = fg.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(nc.accent), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
            // colour strip
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NotePalette.colors.forEachIndexed { i, c ->
                    val sel = i == colorId
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.accent)
                            .border(2.5.dp, if (sel) fg else Color.Transparent, CircleShape)
                            .clickable { colorId = i },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.White) }
                }
            }
        }
    }
}
