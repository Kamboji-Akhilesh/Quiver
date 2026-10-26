package com.kamboji.quiver.ui.shell

import com.kamboji.quiver.R

import com.kamboji.quiver.ui.locale.tr
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.kamboji.quiver.ui.theme.titleRes
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/** App launcher bottom sheet. */
@Composable
fun Launcher(state: QuiverState) {
    val colors = Quiver.colors
    val items = listOf(
        AppKey.Screenshots to Icons.Outlined.Image,
        AppKey.Currency to Icons.Outlined.SwapHoriz,
        AppKey.Calendar to Icons.Outlined.CalendarMonth,
        AppKey.Notes to Icons.AutoMirrored.Outlined.StickyNote2,
        AppKey.Expenses to Icons.Outlined.Payments,
        AppKey.Focus to Icons.Outlined.SelfImprovement,
        AppKey.Move to Icons.AutoMirrored.Outlined.DirectionsRun,
    )
    QuiverModalSheet(onDismiss = { state.launcherOpen = false }) { _ ->
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(R.string.shell_your_apps), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.fillMaxWidth())
            Text(tr(R.string.shell_jump_straight_into_any_mini), fontSize = 13.sp, color = colors.dim, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp))
            // 3 per row
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (app, icon) -> LauncherTile(app, icon, Modifier.weight(1f)) { state.go(app) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(tr(R.string.shell_more_coming_soon), color = colors.faint, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LauncherTile(app: AppKey, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.of(app)
    val name = stringResource(app.titleRes)
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Not remembered: the accents follow the colour theme, and the list is tiny.
    val actions = run {
        listOf(
            SearchAction(tr(R.string.shell_clean_screenshots_now), Icons.Outlined.Image, AppKey.Screenshots, Accents.Screenshots),
            SearchAction(tr(R.string.shell_convert_100_usd_to_eur), Icons.Outlined.SwapHoriz, AppKey.Currency, Accents.Currency),
            SearchAction(tr(R.string.shell_add_a_new_event), Icons.Filled.Add, AppKey.Calendar, Accents.Calendar),
            SearchAction(tr(R.string.shell_new_note), Icons.AutoMirrored.Outlined.StickyNote2, AppKey.Notes, Accents.Notes),
            SearchAction(tr(R.string.shell_add_an_expense), Icons.Outlined.Payments, AppKey.Expenses, Accents.Expenses),
            SearchAction(tr(R.string.shell_open_deletion_history), Icons.Outlined.History, AppKey.Screenshots, Accents.Screenshots, "history"),
        )
    }
    val filtered = actions.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().background(if (colors.dark) Color(0xEB080810) else Color(0xF2F4F5FA))
            .statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(colors.surf)
                    .border(1.dp, Accents.Hub.a.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(19.dp), tint = Accents.Hub.txt(colors.dark))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr(R.string.shell_search_apps_actions_rates), color = colors.dim, fontSize = 15.5.sp)
                    BasicTextField(
                        query, { query = it },
                        textStyle = TextStyle(color = colors.text, fontSize = 15.5.sp),
                        cursorBrush = SolidColor(Accents.Hub.a),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Text(tr(R.string.shell_cancel), color = colors.dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { state.searchOpen = false })
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            SectionLabel(if (query.isBlank()) tr(R.string.shell_quick_actions) else tr(R.string.shell_actions))
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
                    .background(Brush.linearGradient(listOf(Accents.Hub.a.copy(alpha = if (colors.dark) 0.22f else 0.16f), Accents.Hub.b.copy(alpha = 0.12f))))
                    .border(1.dp, Accents.Hub.a.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable { state.searchOpen = false; state.aiOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Accents.Hub.a, Accents.Hub.b))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(21.dp), tint = Accents.Hub.on)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (query.isBlank()) tr(R.string.shell_ask_quiver_ai) else "“$query”", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(tr(R.string.shell_let_ai_handle_it_across), fontSize = 12.5.sp, color = colors.dim)
                }
            }
        }
    }
}

