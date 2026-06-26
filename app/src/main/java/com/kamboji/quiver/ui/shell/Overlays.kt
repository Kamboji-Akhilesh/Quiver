package com.kamboji.quiver.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.calendar.CalendarViewModel
import com.kamboji.quiver.currency.CurrencyViewModel
import com.kamboji.quiver.currency.Ui
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/** App launcher bottom sheet. */
@Composable
fun Launcher(state: QuiverState) {
    val colors = Quiver.colors
    QuiverModalSheet(onDismiss = { state.launcherOpen = false }) { _ ->
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your apps", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.fillMaxWidth())
            Text("Jump straight into any mini-app", fontSize = 13.sp, color = colors.dim, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LauncherTile(AppKey.Screenshots, Icons.Outlined.Image, Modifier.weight(1f)) { state.go(AppKey.Screenshots) }
                LauncherTile(AppKey.Currency, Icons.Outlined.SwapHoriz, Modifier.weight(1f)) { state.go(AppKey.Currency) }
                LauncherTile(AppKey.Calendar, Icons.Outlined.CalendarMonth, Modifier.weight(1f)) { state.go(AppKey.Calendar) }
            }
        }
    }
}

@Composable
private fun LauncherTile(app: AppKey, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    val name = when (app) {
        AppKey.Screenshots -> "Screenshots"; AppKey.Currency -> "Currency"; AppKey.Calendar -> "Calendar"; AppKey.Hub -> "Quiver"
    }
    Column(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(ac.a.copy(alpha = if (colors.dark) 0.2f else 0.16f), ac.b.copy(alpha = if (colors.dark) 0.08f else 0.06f))))
            .border(1.dp, ac.a.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(ac.a.copy(alpha = 0.2f))
                .border(1.dp, ac.a.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(26.dp), tint = ac.txt(colors.dark)) }
        Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
    }
}

private data class SearchAction(val label: String, val icon: ImageVector, val app: AppKey, val accent: Accent, val screen: String = "home")

/** Full-screen universal search. */
@Composable
fun SearchOverlay(state: QuiverState) {
    val colors = Quiver.colors
    var query by remember { mutableStateOf("") }
    val actions = remember {
        listOf(
            SearchAction("Clean screenshots now", Icons.Outlined.Image, AppKey.Screenshots, Accents.Screenshots),
            SearchAction("Convert 100 USD to EUR", Icons.Outlined.SwapHoriz, AppKey.Currency, Accents.Currency),
            SearchAction("Add a new event", Icons.Filled.Add, AppKey.Calendar, Accents.Calendar),
            SearchAction("Open deletion history", Icons.Outlined.History, AppKey.Screenshots, Accents.Screenshots, "history"),
        )
    }
    val filtered = actions.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().background(if (colors.dark) Color(0xEB080810) else Color(0xF2F4F5FA))
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(colors.surf)
                    .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(19.dp), tint = Accents.Hub.txt(colors.dark))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps, actions, rates…", color = colors.dim, fontSize = 15.5.sp)
                    BasicTextField(
                        query, { query = it },
                        textStyle = TextStyle(color = colors.text, fontSize = 15.5.sp),
                        cursorBrush = SolidColor(Accents.Hub.a),
                        singleLine = true,
                    )
                }
            }
            Text("Cancel", color = colors.dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { state.searchOpen = false })
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            SectionLabel(if (query.isBlank()) "Quick actions" else "Actions")
            Spacer(Modifier.height(8.dp))
            filtered.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(18.dp)).clickable { state.go(a.app); if (a.screen == "history") state.screenshotsScreen = "history" }
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(a.accent.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(a.icon, null, Modifier.size(19.dp), tint = a.accent.txt(colors.dark))
                    }
                    Text(a.label, Modifier.weight(1f), color = colors.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp), tint = colors.faint)
                }
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFA78BFA).copy(alpha = if (colors.dark) 0.22f else 0.16f), Color(0xFF22D3EE).copy(alpha = 0.12f))))
                    .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable { state.searchOpen = false; state.aiOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(21.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (query.isBlank()) "Ask Quiver AI" else "“$query”", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text("Let AI handle it across your apps", fontSize = 12.5.sp, color = colors.dim)
                }
            }
        }
    }
}

private data class Suggestion(val title: String, val sub: String, val icon: ImageVector, val app: AppKey)

/** AI assistant bottom sheet. Suggestions are built from real app state. */
@Composable
fun AiPanel(state: QuiverState) {
    val colors = Quiver.colors
    val context = LocalContext.current
    val calVm: CalendarViewModel = viewModel()
    val curVm: CurrencyViewModel = viewModel()
    val entries by calVm.entries.collectAsState()
    val todayCount = entries.count {
        Instant.ofEpochMilli(it.startMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    }
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val trash by remember { dao.getAllHistory() }.collectAsState(emptyList())
    val converter by curVm.converter.collectAsState()
    val rate = curVm.rateOf(curVm.to, (converter as? Ui.Data)?.value?.data ?: emptyList())
    val sug = buildList {
        if (trash.isNotEmpty()) add(Suggestion("Review your trash", "${trash.size} screenshot${if (trash.size == 1) "" else "s"} can be restored or cleared", Icons.Outlined.Image, AppKey.Screenshots))
        if (todayCount > 0) add(Suggestion("Today's agenda", "$todayCount ${if (todayCount == 1) "item" else "items"} on your calendar today", Icons.Outlined.CalendarMonth, AppKey.Calendar))
        if (rate != null) add(Suggestion("${curVm.from} → ${curVm.to}", "1 ${curVm.from} = ${"%.2f".format(rate)} ${curVm.to} right now", Icons.Outlined.SwapHoriz, AppKey.Currency))
        if (isEmpty()) add(Suggestion("All caught up", "Nothing needs attention — explore your mini-apps", Icons.Filled.AutoAwesome, AppKey.Hub))
    }
    QuiverModalSheet(onDismiss = { state.aiOpen = false }) { hide ->
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(26.dp), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Quiver AI", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        Text("● Online · understands all your apps", fontSize = 12.5.sp, color = Accents.Hub.txt(colors.dark), fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(colors.surf).border(1.dp, colors.border, CircleShape).clickable { hide() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = colors.text) }
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Text("Here’s what stands out right now — tap any card and I’ll take you there.", fontSize = 14.5.sp, color = colors.text, lineHeight = 22.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Suggested for you")
                }
                items(sug) { x ->
                    val ac = Accents.of(x.app)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(ac.a.copy(alpha = if (colors.dark) 0.16f else 0.12f), Color.Transparent)))
                            .border(1.dp, ac.a.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .clickable { state.go(x.app) }.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(x.icon, null, Modifier.size(21.dp), tint = ac.txt(colors.dark))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(x.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                            Text(x.sub, fontSize = 12.5.sp, color = colors.dim)
                        }
                        Box(Modifier.clip(RoundedCornerShape(100.dp)).background(ac.a.copy(alpha = 0.18f)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                            Text("Do it", color = ac.txt(colors.dark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(100.dp)).background(colors.surf).border(1.dp, colors.border, RoundedCornerShape(100.dp))
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Ask anything…", Modifier.weight(1f), color = colors.dim, fontSize = 14.5.sp)
                Icon(Icons.Filled.Mic, null, Modifier.size(22.dp), tint = colors.dim)
                Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF22D3EE)))).clickable { state.toast("Thinking…", ToastKind.Info) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}
