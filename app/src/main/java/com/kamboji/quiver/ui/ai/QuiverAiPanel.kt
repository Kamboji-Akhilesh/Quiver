package com.kamboji.quiver.ui.ai

import com.kamboji.quiver.R
import androidx.compose.runtime.ReadOnlyComposable

import com.kamboji.quiver.ui.locale.tr
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.ai.AiModel
import com.kamboji.quiver.ai.AiViewModel
import com.kamboji.quiver.ai.ChatMsg
import com.kamboji.quiver.ai.ModelState
import com.kamboji.quiver.ai.VoiceLanguages
import com.kamboji.quiver.ai.agent.AgentDebugLog
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

// The brand colours follow the theme: Glass keeps purple→cyan, flat themes use their accent.
private val AiA: Color @Composable @ReadOnlyComposable get() = Accents.Hub.a
private val AiB: Color @Composable @ReadOnlyComposable get() = Accents.Hub.b
private val AiOn: Color @Composable @ReadOnlyComposable get() = Accents.Hub.on

@Composable
fun QuiverAiPanel(onClose: () -> Unit, initialInput: String? = null, startMic: Boolean = false) {
    val vm: AiViewModel = viewModel()
    val state by vm.modelState.collectAsState()
    val colors = Quiver.colors
    var showManage by remember { mutableStateOf(false) }

    QuiverModalSheet(onDismiss = onClose) { hide ->
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(AiA, AiB))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(26.dp), tint = AiOn)
                }
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.ai_quiver_ai), fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                    Text(
                        when (val s = state) {
                            is ModelState.Ready -> tr(R.string.ai_on_device_x, s.label)
                            is ModelState.Downloading -> tr(R.string.ai_installing_model)
                            else -> tr(R.string.ai_install_to_start)
                        },
                        fontSize = 12.5.sp, color = Accents.Hub.txt(colors.dark), fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state is ModelState.Ready) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(if (showManage) AiA.copy(alpha = 0.2f) else colors.surf)
                            .border(1.dp, colors.border, CircleShape).clickable { showManage = !showManage },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Tune, tr(R.string.ai_manage_model), Modifier.size(18.dp), tint = if (showManage) Accents.Hub.txt(colors.dark) else colors.text) }
                }
                Box(Modifier.size(38.dp).clip(CircleShape).background(colors.surf).border(1.dp, colors.border, CircleShape).clickable { hide() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = colors.text)
                }
            }
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is ModelState.Ready ->
                    if (showManage) SetupBody(vm, null, current = s.label, onBack = { showManage = false })
                    else ChatBody(vm, initialInput, startMic)
                is ModelState.Downloading -> Progress(tr(R.string.ai_installing_x, AiModel.DISPLAY_NAME), s.progress, AiModel.SIZE_LABEL)
                is ModelState.Error -> SetupBody(vm, s.message)
                ModelState.None -> SetupBody(vm, null)
            }
        }
    }
}

@Composable
private fun Progress(label: String, progress: Float, size: String?) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        CircularProgressIndicator(color = AiA, trackColor = colors.surf2)
        Spacer(Modifier.height(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
        if (size != null) Text(size, fontSize = 12.5.sp, color = colors.dim)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { progress }, color = AiA, trackColor = colors.surf2, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = colors.dim)
        Text(tr(R.string.ai_downloads_in_the_background_you), fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun SetupBody(vm: AiViewModel, error: String?, current: String? = null, onBack: (() -> Unit)? = null) {
    val colors = Quiver.colors
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (current != null) {
            // Manage mode: the one installed model, with remove / back.
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text(tr(R.string.ai_installed_model), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = colors.dim)
                    Text(current, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (onBack != null) PillButton(tr(R.string.ai_back_to_chat), filled = true) { onBack() }
                        PillButton(tr(R.string.ai_remove), filled = false) { vm.deleteModel(); onBack?.invoke() }
                    }
                }
            }
            item { Text(tr(R.string.ai_removing_frees_x_of_storage, AiModel.SIZE_LABEL), fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp) }
            // Local-only log of what the model actually produced — sharing a bad
            // turn is how it becomes a training example for the next fine-tune.
            item {
                PillButton(tr(R.string.ai_share_ai_debug_log), filled = false) {
                    AgentDebugLog.shareIntent(context)?.let { context.startActivity(it) }
                }
            }
            item { Text(tr(R.string.ai_the_log_stays_on_your), fontSize = 12.sp, color = colors.faint, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        } else {
            item {
                Text(tr(R.string.ai_quiver_ai_runs_entirely_on), fontSize = 14.sp, color = colors.text, lineHeight = 20.sp)
            }
            if (error != null) {
                item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFB7185)).border(1.dp, Color(0xFFFB7185).copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text(error, fontSize = 13.sp, color = Color(0xFFFB7185))
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surf)
                        .border(1.dp, AiA.copy(alpha = 0.45f), RoundedCornerShape(18.dp)).padding(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(AiModel.DISPLAY_NAME, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        Text(AiModel.SIZE_LABEL, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Accents.Hub.txt(colors.dark))
                    }
                    Text(
                        tr(R.string.ai_adds_notes_tasks_and_events),
                        fontSize = 12.5.sp, color = colors.dim, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    if (!confirm) {
                        PillButton(tr(R.string.ai_install), filled = true) { confirm = true }
                    } else {
                        Text(tr(R.string.ai_download_x_now_uses_your, AiModel.SIZE_LABEL), fontSize = 12.5.sp, color = colors.dim, lineHeight = 17.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(tr(R.string.ai_not_now), filled = false) { confirm = false }
                            PillButton(tr(R.string.ai_download), filled = true) { vm.download(); confirm = false }
                        }
                    }
                }
            }
            item {
                Text(tr(R.string.ai_if_you_skip_this_quiver), fontSize = 12.sp, color = colors.faint, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun ChatBody(vm: AiViewModel, initialInput: String? = null, startMic: Boolean = false) {
    val colors = Quiver.colors
    val context = LocalContext.current
    // Shared-in text lands here pre-typed so the user can review before sending.
    var input by remember { mutableStateOf(initialInput.orEmpty()) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }
    // Widget mic button: arrive already listening. No permission prompt from
    // here — without the grant the user just taps the mic like normal.
    LaunchedEffect(Unit) {
        if (startMic && !vm.listening &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            vm.startListening()
        }
    }

    Column(Modifier.fillMaxHeight()) {
        // messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.messages.isEmpty()) {
                item { Text(tr(R.string.ai_ask_me_anything_or_tell), fontSize = 14.sp, color = colors.dim, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            items(vm.messages, key = { it.id }) { msg -> Bubble(msg) { vm.speak(msg.text) } }
            if (vm.listening && vm.partialTranscript.isNotBlank()) {
                item { Bubble(ChatMsg(-1, true, vm.partialTranscript, streaming = true)) {} }
            }
        }

        // language chips
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceLanguages.forEach { l ->
                val sel = l == vm.lang
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(if (sel) AiA.copy(alpha = 0.18f) else colors.surf)
                        .border(1.dp, if (sel) AiA.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(100.dp))
                        .clickable { vm.setLanguage(l) }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(l.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Accents.Hub.txt(colors.dark) else colors.dim) }
            }
        }

        // input bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp, top = 2.dp)
                .clip(RoundedCornerShape(100.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(100.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty() && !vm.listening) Text(tr(R.string.ai_ask_anything), color = colors.dim, fontSize = 14.5.sp)
                if (vm.listening) Text(tr(R.string.ai_listening), color = AiA, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(input, { input = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.5.sp), cursorBrush = SolidColor(AiA), modifier = Modifier.fillMaxWidth())
            }
            // mic
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (vm.listening) AiA else colors.surf2).clickable {
                    if (vm.listening) vm.stopListening()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.startListening()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }, contentAlignment = Alignment.Center,
            ) { Icon(if (vm.listening) Icons.Filled.Stop else Icons.Filled.Mic, tr(R.string.ai_voice), Modifier.size(20.dp), tint = if (vm.listening) AiOn else colors.dim) }
            // send
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AiA, AiB))).clickable {
                    if (input.isNotBlank() && !vm.generating) { vm.send(input); input = "" }
                }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, tr(R.string.ai_send), Modifier.size(20.dp), tint = AiOn) }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMsg, onSpeak: () -> Unit) {
    val colors = Quiver.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                .background(if (msg.fromUser) Brush.linearGradient(listOf(AiA, AiB)) else SolidColor(colors.surf2))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!msg.fromUser && msg.streaming) {
                WorkingBubbleContent(msg.text)
            } else {
                Text(
                    msg.text.ifEmpty { if (msg.streaming) "…" else "" },
                    color = if (msg.fromUser) AiOn else colors.text, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
            if (!msg.fromUser && !msg.streaming && msg.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, tr(R.string.ai_speak), Modifier.size(16.dp).padding(top = 4.dp).clickable(onClick = onSpeak), tint = colors.dim)
            }
        }
    }
}

/**
 * Live view of a running agent turn: bouncing dots + what it's doing right now
 * ("Searching the web…"), the steps finished so far, and a reminder that the
 * work survives closing the app.
 */
@Composable
private fun WorkingBubbleContent(text: String) {
    val colors = Quiver.colors
    val lines = text.lines().filter { it.isNotBlank() }
    val status = lines.firstOrNull() ?: tr(R.string.ai_working)
    val doneSteps = lines.drop(1)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TypingDots()
        Text(status, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    }
    doneSteps.forEach { line ->
        Text(line, color = colors.dim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Text(
        tr(R.string.ai_you_can_close_the_app),
        color = colors.faint, fontSize = 11.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp),
    )
}

/** Three dots bobbing up and down with a staggered phase. */
@Composable
private fun TypingDots(color: Color = AiA) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 120),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .offset(y = (2f - 4f * phase).dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.45f + 0.55f * phase)),
            )
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = Quiver.colors
    Box(
        Modifier.widthIn(min = 110.dp).clip(RoundedCornerShape(100.dp))
            .background(if (filled) Brush.linearGradient(listOf(AiA, AiB)) else SolidColor(colors.surf))
            .border(1.dp, if (filled) Color.Transparent else colors.border, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) AiOn else colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
