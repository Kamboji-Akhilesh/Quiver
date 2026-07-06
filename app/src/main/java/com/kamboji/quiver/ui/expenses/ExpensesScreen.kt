package com.kamboji.quiver.ui.expenses

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.expenses.ExpensesViewModel
import com.kamboji.quiver.expenses.capture.PaymentCaptureService
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.ui.components.QuiverComposerSheet
import com.kamboji.quiver.ui.components.QvDatePickerDialog
import com.kamboji.quiver.ui.components.QvIconButton
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun catColor(c: ExpenseCategory): Color = when (c) {
    ExpenseCategory.FOOD -> Color(0xFFFB923C)
    ExpenseCategory.GROCERIES -> Color(0xFF34D399)
    ExpenseCategory.TRANSPORT -> Color(0xFF38BDF8)
    ExpenseCategory.SHOPPING -> Color(0xFFF472B6)
    ExpenseCategory.BILLS -> Color(0xFFA78BFA)
    ExpenseCategory.HEALTH -> Color(0xFFF87171)
    ExpenseCategory.ENTERTAINMENT -> Color(0xFFFBBF24)
    ExpenseCategory.OTHER -> Color(0xFF94A3B8)
}

private fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun ExpensesScreen(state: QuiverState) {
    val ac = Accents.Expenses
    val colors = Quiver.colors
    val vm: ExpensesViewModel = viewModel()
    val all by vm.expenses.collectAsState()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var sheet by remember { mutableStateOf<ExpSheet?>(null) }
    var showEnable by remember { mutableStateOf(false) }

    // Deep-link from Home's "Add expense" quick action.
    LaunchedEffect(state.expensesStartNew) {
        if (state.expensesStartNew) { sheet = ExpSheet.New; state.expensesStartNew = false }
    }

    val monthList = ExpenseMath.inMonth(all, month)
    val summary = ExpenseMath.summarize(monthList)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QvTopBar(
                "Expenses", ac, onBack = { state.go(AppKey.Hub) },
                trailing = {
                    QvIconButton(
                        Icons.Filled.Add, { sheet = ExpSheet.New }, size = 42.dp,
                        background = colors.surf, tint = colors.text,
                    )
                },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(bottom = 150.dp),
            ) {
                // month summary card
                Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            Text(month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                            Text("${month.year}", fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = Display, color = colors.dim)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, { month = month.minusMonths(1) }, size = 34.dp, iconSize = 17.dp)
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, { month = month.plusMonths(1) }, size = 34.dp, iconSize = 17.dp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(ExpenseMath.formatPaise(summary.totalPaise), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text("${monthList.size} ${if (monthList.size == 1) "expense" else "expenses"} this month", fontSize = 12.5.sp, color = colors.dim)
                    if (summary.totalPaise > 0) {
                        Spacer(Modifier.height(14.dp))
                        // stacked category bar
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                            summary.byCategory.forEach { (cat, paise) ->
                                val frac = paise.toFloat() / summary.totalPaise
                                if (frac > 0.01f) Box(Modifier.weight(frac).fillMaxSize().background(catColor(cat)))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().horizontalScrollIfNeeded(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            summary.byCategory.take(4).forEach { (cat, paise) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(catColor(cat)))
                                    Text("${cat.label} ${ExpenseMath.formatPaise(paise)}", fontSize = 11.5.sp, color = colors.dim, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Auto-capture: offer to enable, or surface captures needing review.
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                val focused = LocalWindowInfo.current.isWindowFocused // re-check on return from Settings
                val captureOn = remember(focused) { PaymentCaptureService.isEnabled(context) }
                val toReview = all.filter { it.needsReview }
                if (!captureOn) {
                    AutoTrackCard(ac) { showEnable = true }
                } else if (toReview.isNotEmpty()) {
                    ReviewBanner(toReview.size) { sheet = ExpSheet.Edit(toReview.first()) }
                }

                Spacer(Modifier.height(16.dp))
                if (monthList.isEmpty()) {
                    EmptyExpenses(ac)
                } else {
                    val byDay = monthList.groupBy { localDate(it.atMillis) }.toSortedMap(compareByDescending { it })
                    byDay.forEach { (day, dayItems) ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (day == LocalDate.now()) "Today" else day.format(DAY_FMT),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text,
                            )
                            Text(ExpenseMath.formatPaise(dayItems.sumOf { it.amountPaise }), fontSize = 12.sp, fontFamily = Mono, color = colors.dim)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dayItems.forEach { e -> ExpenseRow(e) { sheet = ExpSheet.Edit(e) } }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        when (val s = sheet) {
            is ExpSheet.New -> ExpenseSheet(
                editing = null, onDismiss = { sheet = null },
                onInvalid = { state.toast("Enter a valid amount first", ToastKind.Info) },
                onDelete = null,
            ) { paise, cat, note, at ->
                vm.add(paise, cat, note, at)
                state.toast("Added ${ExpenseMath.formatPaise(paise)} · ${cat.label}", ToastKind.Success)
            }
            is ExpSheet.Edit -> ExpenseSheet(
                editing = s.expense, onDismiss = { sheet = null },
                onInvalid = { state.toast("Enter a valid amount first", ToastKind.Info) },
                onDelete = {
                    vm.delete(s.expense.id)
                    state.toast("Expense deleted", ToastKind.Error)
                },
            ) { paise, cat, note, at ->
                vm.update(s.expense.id, paise, cat, note, at)
                state.toast("Saved", ToastKind.Success)
            }
            null -> Unit
        }

        if (showEnable) EnableCaptureDialog(onDismiss = { showEnable = false })
    }
}

/**
 * Explains the notification-access grant BEFORE bouncing to system settings.
 * There's no system popup for this (it's special app access, not a runtime
 * permission), and on Android 13+ sideloaded apps additionally need
 * "Allow restricted settings" from App info before the toggle even enables —
 * so the dialog walks through both, with a direct button for each.
 */
@Composable
private fun EnableCaptureDialog(onDismiss: () -> Unit) {
    val colors = Quiver.colors
    val ac = Accents.Expenses
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(if (colors.dark) Color(0xFF14141F) else Color(0xFFFAFBFE))
                .border(1.dp, colors.border, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Allow notification access", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(
                "Quiver reads payment notifications (GPay, PhonePe, bank SMS) to log expenses automatically. Everything is parsed on this phone — nothing is uploaded, ever.",
                fontSize = 13.sp, color = colors.dim, lineHeight = 18.sp,
            )
            Text("In settings, turn ON “Quiver expense capture”.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(
                "Toggle greyed out? Android locks this for sideloaded apps: open App info → tap ⋮ (top right) → “Allow restricted settings”, then come back and try again.",
                fontSize = 12.sp, color = colors.faint, lineHeight = 16.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(100.dp))
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("App info", color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(Brush.linearGradient(listOf(ac.a, ac.b)))
                        .clickable { openListenerSettings(context); onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Open settings", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Jump straight to Quiver's own listener toggle where the OS supports it. */
private fun openListenerSettings(context: android.content.Context) {
    val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            PaymentCaptureService.component(context).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    runCatching { context.startActivity(direct) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}

/** Row modifier no-op placeholder — legend fits 4 chips; avoids a scroll dep. */
private fun Modifier.horizontalScrollIfNeeded(): Modifier = this

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    val colors = Quiver.colors
    val col = catColor(e.category)
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(col.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text(e.category.emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f)) {
            Text(e.note.ifBlank { e.category.label }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text, maxLines = 1)
            Text(if (e.auto) "${e.category.label} · auto" else e.category.label, fontSize = 11.5.sp, color = colors.dim)
        }
        // Amber dot: auto-captured, category not confirmed yet.
        if (e.needsReview) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFBBF24)))
        Text(ExpenseMath.formatPaise(e.amountPaise), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
    }
}

@Composable
private fun AutoTrackCard(ac: com.kamboji.quiver.ui.theme.Accent, onEnable: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(ac.a.copy(alpha = if (colors.dark) 0.14f else 0.10f))
            .border(1.dp, ac.a.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onEnable).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Notifications, null, Modifier.size(20.dp), tint = ac.txt(colors.dark))
        }
        Column(Modifier.weight(1f)) {
            Text("Auto-track UPI payments", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text("Reads payment notifications on this phone only — nothing leaves the device.", fontSize = 11.5.sp, color = colors.dim, lineHeight = 15.sp)
        }
        Text("Enable", color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    val colors = Quiver.colors
    val amber = Color(0xFFFBBF24)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(amber.copy(alpha = if (colors.dark) 0.13f else 0.15f))
            .border(1.dp, amber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(amber))
        Text(
            "$count auto-detected ${if (count == 1) "payment" else "payments"} — tap to review",
            Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text,
        )
    }
}

@Composable
private fun EmptyExpenses(ac: com.kamboji.quiver.ui.theme.Accent) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(ac.a.copy(alpha = 0.12f))
                .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Payments, null, Modifier.size(40.dp), tint = ac.txt(colors.dark)) }
        Spacer(Modifier.height(16.dp))
        Text("No spends this month", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
        Text("Tap + to add one, or tell Quiver AI “spent 250 on lunch”.", fontSize = 13.5.sp, color = colors.dim)
    }
}

// --- add/edit sheet ---

private sealed interface ExpSheet {
    data object New : ExpSheet
    data class Edit(val expense: Expense) : ExpSheet
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseSheet(
    editing: Expense?,
    onDismiss: () -> Unit,
    onInvalid: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (paise: Long, cat: ExpenseCategory, note: String, atMillis: Long) -> Unit,
) {
    val ac = Accents.Expenses
    val colors = Quiver.colors
    var amount by remember {
        mutableStateOf(editing?.let { if (it.amountPaise % 100 == 0L) "${it.amountPaise / 100}" else "${it.amountPaise / 100.0}" } ?: "")
    }
    var category by remember { mutableStateOf(editing?.category ?: ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var date by remember { mutableStateOf(editing?.let { localDate(it.atMillis) } ?: LocalDate.now()) }
    var showDate by remember { mutableStateOf(false) }
    val isEdit = editing != null

    // Shared keyboard-safe composer panel (see QuiverComposerSheet for why).
    QuiverComposerSheet(
        title = if (isEdit) "Edit expense" else "New expense",
        onDismiss = onDismiss,
        maxFieldsHeight = 360.dp,
        footer = {
            // save (pinned outside the scroll so it's always visible)
            val paise = amount.toDoubleOrNull()?.let { ExpenseMath.toPaise(it) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    // graphicsLayer, NOT Modifier.alpha: conditional alpha() adds/
                    // removes the layer node when it crosses 1f, and some OEM skins
                    // skip the redraw — the button stayed clickable but invisible.
                    .graphicsLayer { alpha = if (paise != null) 1f else 0.5f }
                    .clickable {
                        if (paise == null) { onInvalid(); return@clickable }
                        val at = editing?.atMillis?.takeIf { localDate(it) == date }
                            ?: if (date == LocalDate.now()) System.currentTimeMillis()
                            else date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onSave(paise, category, note.trim(), at)
                        onDismiss()
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (isEdit) "Save changes" else "Add expense", color = Color(0xFF06121A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        },
    ) {
                // amount
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("₹", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ac.txt(colors.dark))
                    Box(Modifier.weight(1f)) {
                        if (amount.isEmpty()) Text("0", color = colors.dim, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                        BasicTextField(
                            amount,
                            { new -> if (new.length <= 10 && new.count { it == '.' } <= 1 && new.all { it.isDigit() || it == '.' }) amount = new },
                            textStyle = TextStyle(color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono),
                            cursorBrush = SolidColor(ac.a),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // category
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        val sel = category == cat
                        Row(
                            Modifier.clip(RoundedCornerShape(100.dp))
                                .background(if (sel) catColor(cat).copy(alpha = 0.18f) else colors.surf)
                                .border(1.dp, if (sel) catColor(cat).copy(alpha = 0.55f) else colors.border, RoundedCornerShape(100.dp))
                                .clickable { category = cat }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(cat.emoji, fontSize = 13.sp)
                            Text(cat.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (sel) colors.text else colors.dim)
                        }
                    }
                }
                // note
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    if (note.isEmpty()) Text("What was it? (optional)", color = colors.dim, fontSize = 14.5.sp)
                    BasicTextField(
                        note, { note = it },
                        textStyle = TextStyle(color = colors.text, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(ac.a), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                // date
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable { showDate = true }.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(17.dp), tint = ac.txt(colors.dark))
                    Text(
                        if (date == LocalDate.now()) "Today" else date.format(DAY_FMT),
                        color = colors.text, fontSize = 14.sp,
                    )
                }
                if (onDelete != null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFB7185).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFFB7185).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { onDelete(); onDismiss() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Delete expense", color = Color(0xFFFB7185), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
    }

    if (showDate) {
        QvDatePickerDialog(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), ac,
            onConfirm = { utc -> date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate() },
            onDismiss = { showDate = false },
        )
    }
}
