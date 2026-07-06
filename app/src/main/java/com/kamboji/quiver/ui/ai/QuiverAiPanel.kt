package com.kamboji.quiver.ui.ai

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
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private val AiA = Color(0xFFA78BFA)
private val AiB = Color(0xFF22D3EE)

@Composable
fun QuiverAiPanel(onClose: () -> Unit) {
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
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(26.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Quiver AI", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                    Text(
                        when (val s = state) {
                            is ModelState.Ready -> "● On-device · ${s.label}"
                            is ModelState.Downloading -> "Installing model…"
                            else -> "Install to start"
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
                    ) { Icon(Icons.Outlined.Tune, "Manage model", Modifier.size(18.dp), tint = if (showManage) Accents.Hub.txt(colors.dark) else colors.text) }
                }
                Box(Modifier.size(38.dp).clip(CircleShape).background(colors.surf).border(1.dp, colors.border, CircleShape).clickable { hide() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = colors.text)
                }
            }
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is ModelState.Ready ->
                    if (showManage) SetupBody(vm, null, current = s.label, onBack = { showManage = false })
                    else ChatBody(vm)
                is ModelState.Downloading -> Progress("Installing ${AiModel.DISPLAY_NAME}", s.progress, AiModel.SIZE_LABEL)
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
        Text("Downloads in the background — you can close the app. It resumes if the connection drops.", fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(top = 14.dp))
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
                    Text("INSTALLED MODEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = colors.dim)
                    Text(current, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (onBack != null) PillButton("Back to chat", filled = true) { onBack() }
                        PillButton("Remove", filled = false) { vm.deleteModel(); onBack?.invoke() }
                    }
                }
            }
            item { Text("Removing frees ${AiModel.SIZE_LABEL} of storage. You can reinstall anytime.", fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp) }
            // Local-only log of what the model actually produced — sharing a bad
            // turn is how it becomes a training example for the next fine-tune.
            item {
                PillButton("Share AI debug log", filled = false) {
                    AgentDebugLog.shareIntent(context)?.let { context.startActivity(it) }
                }
            }
            item { Text("The log stays on your phone. Share it after a bad answer so it can be turned into training data.", fontSize = 12.sp, color = colors.faint, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        } else {
            item {
                Text("Quiver AI runs entirely on your phone — your messages and voice never leave the device. One model, tuned for Quiver's actions, installed once.", fontSize = 14.sp, color = colors.text, lineHeight = 20.sp)
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
                        "Adds notes, tasks and events, searches the web, checks rates — in English, हिन्दी, বাংলা, தமிழ், తెలుగు and मराठी.",
                        fontSize = 12.5.sp, color = colors.dim, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    if (!confirm) {
                        PillButton("Install", filled = true) { confirm = true }
                    } else {
                        Text("Download ${AiModel.SIZE_LABEL} now? Uses your data connection — Wi-Fi recommended. The model stays on your phone.", fontSize = 12.5.sp, color = colors.dim, lineHeight = 17.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Not now", filled = false) { confirm = false }
                            PillButton("Download", filled = true) { vm.download(); confirm = false }
                        }
                    }
                }
            }
            item {
                Text("If you skip this, Quiver AI can't answer questions or take voice commands — everything else in the app works normally. You can install it anytime from here.", fontSize = 12.sp, color = colors.faint, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun ChatBody(vm: AiViewModel) {
    val colors = Quiver.colors
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }

    Column(Modifier.fillMaxHeight()) {
        // messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.messages.isEmpty()) {
                item { Text("Ask me anything, or tell me to do it — “add a note”, “remind me tomorrow evening”, “what's USD to INR”, “what's in my screenshot trash”. Tasks keep running even if you close the app.", fontSize = 14.sp, color = colors.dim, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 12.dp)) }
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
                if (input.isEmpty() && !vm.listening) Text("Ask anything…", color = colors.dim, fontSize = 14.5.sp)
                if (vm.listening) Text("Listening…", color = AiA, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(input, { input = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.5.sp), cursorBrush = SolidColor(AiA), modifier = Modifier.fillMaxWidth())
            }
            // mic
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (vm.listening) AiA else colors.surf2).clickable {
                    if (vm.listening) vm.stopListening()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.startListening()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }, contentAlignment = Alignment.Center,
            ) { Icon(if (vm.listening) Icons.Filled.Stop else Icons.Filled.Mic, "Voice", Modifier.size(20.dp), tint = if (vm.listening) Color.White else colors.dim) }
            // send
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AiA, AiB))).clickable {
                    if (input.isNotBlank() && !vm.generating) { vm.send(input); input = "" }
                }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Send", Modifier.size(20.dp), tint = Color.White) }
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
                    color = if (msg.fromUser) Color.White else colors.text, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
            if (!msg.fromUser && !msg.streaming && msg.text.isNotBlank()) {
                Icon(Icons.Outlined.VolumeUp, "Speak", Modifier.size(16.dp).padding(top = 4.dp).clickable(onClick = onSpeak), tint = colors.dim)
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
    val status = lines.firstOrNull() ?: "Working…"
    val doneSteps = lines.drop(1)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TypingDots()
        Text(status, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    }
    doneSteps.forEach { line ->
        Text(line, color = colors.dim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Text(
        "You can close the app — I'll keep working and notify you when it's done.",
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
    ) { Text(label, color = if (filled) Color.White else colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
