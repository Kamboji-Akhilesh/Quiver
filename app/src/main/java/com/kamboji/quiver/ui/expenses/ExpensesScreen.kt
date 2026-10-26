package com.kamboji.quiver.ui.expenses

import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Category
import androidx.compose.ui.graphics.vector.ImageVector
import com.kamboji.quiver.ui.locale.trPlural

import com.kamboji.quiver.ui.locale.displayName
import com.kamboji.quiver.ui.locale.monthName

import com.kamboji.quiver.ui.locale.tr
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.R
import com.kamboji.quiver.expenses.ExpensesViewModel
import com.kamboji.quiver.expenses.capture.PaymentCaptureService
import com.kamboji.quiver.expenses.capture.SmsCapture
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.ui.components.EnableAccessDialog
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

private fun catIcon(c: ExpenseCategory): ImageVector = when (c) {
    ExpenseCategory.FOOD -> Icons.Outlined.Restaurant
    ExpenseCategory.GROCERIES -> Icons.Outlined.ShoppingCart
    ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsCar
    ExpenseCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    ExpenseCategory.BILLS -> Icons.AutoMirrored.Outlined.ReceiptLong
    ExpenseCategory.HEALTH -> Icons.Outlined.LocalHospital
    ExpenseCategory.ENTERTAINMENT -> Icons.Outlined.Movie
    ExpenseCategory.OTHER -> Icons.Outlined.Category
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
                stringResource(R.string.title_expenses), ac, onBack = { state.go(AppKey.Hub) },
                trailing = {
                    QvIconButton(
                        Icons.Filled.Add, { sheet = ExpSheet.New },
                        background = ac.a, tint = ac.on, contentDescription = "Add expense",
                    )
                },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 150.dp),
            ) {
                // month summary card
                Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${monthName(month.month)} ${month.year}", fontSize = 13.5.sp, color = colors.dim)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, { month = month.minusMonths(1) }, size = 32.dp, iconSize = 16.dp, background = Color.Transparent, contentDescription = "Previous month")
                            QvIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, { month = month.plusMonths(1) }, size = 32.dp, iconSize = 16.dp, background = Color.Transparent, contentDescription = "Next month")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(ExpenseMath.formatPaise(summary.totalPaise), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                    Text(trPlural(R.plurals.expenses_this_month, monthList.size), fontSize = 12.5.sp, color = colors.dim)
                    if (summary.totalPaise > 0) {
                        Spacer(Modifier.height(14.dp))
                        // stacked category bar
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            summary.byCategory.forEach { (cat, paise) ->
                                val frac = paise.toFloat() / summary.totalPaise
                                if (frac > 0.01f) Box(Modifier.weight(frac).fillMaxSize().background(catColor(cat)))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            summary.byCategory.take(4).forEach { (cat, paise) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(catColor(cat)))
                                    Text("${cat.displayName} ${ExpenseMath.formatPaise(paise)}", fontSize = 12.sp, color = colors.dim, maxLines = 1)
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
                val smsOn = remember(focused) { SmsCapture.hasPermission(context) }
                val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    if (result.values.all { it }) {
                        SmsCapture.enqueueScan(context)
                        state.toast(tr(R.string.expenses_bank_sms_tracking_on), ToastKind.Success)
                    } else {
                        state.toast(tr(R.string.expenses_sms_permission_denied_allow_it), ToastKind.Info)
                    }
                }
                // Catch up on anything that arrived while the app was closed.
                LaunchedEffect(smsOn) { if (smsOn) SmsCapture.enqueueScan(context) }
                val toReview = all.filter { it.needsReview }
                // Bank SMS first: every debit sends one. UPI-app notifications are the extra.
                if (!smsOn) {
                    AutoTrackCard(ac, Icons.Outlined.Sms, tr(R.string.expenses_auto_track_bank_sms), tr(R.string.expenses_reads_only_messages_from_bank)) {
                        smsPermission.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                } else if (!captureOn) {
                    AutoTrackCard(ac, Icons.Outlined.Notifications, tr(R.string.expenses_also_catch_gpay_phonepe), tr(R.string.expenses_reads_upi_app_payment_notifications)) { showEnable = true }
                }
                if (toReview.isNotEmpty()) {
                    if (!smsOn || !captureOn) Spacer(Modifier.height(10.dp))
                    ReviewBanner(toReview.size) { sheet = ExpSheet.Edit(toReview.first()) }
                }

                Spacer(Modifier.height(16.dp))
                if (monthList.isEmpty()) {
                    EmptyExpenses(ac)
                } else {
                    val byDay = monthList.groupBy { localDate(it.atMillis) }.toSortedMap(compareByDescending { it })
                    byDay.forEach { (day, dayItems) ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (day == LocalDate.now()) tr(R.string.common_today) else day.format(DAY_FMT),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text,
                            )
                            Text(ExpenseMath.formatPaise(dayItems.sumOf { it.amountPaise }), fontSize = 13.sp, fontFamily = Mono, color = colors.dim)
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
                onInvalid = { state.toast(tr(R.string.expenses_enter_a_valid_amount_first), ToastKind.Info) },
                onDelete = null,
            ) { paise, cat, note, at ->
                vm.add(paise, cat, note, at)
                state.toast(tr(R.string.expenses_added_x_x, ExpenseMath.formatPaise(paise), cat.displayName), ToastKind.Success)
            }
            is ExpSheet.Edit -> ExpenseSheet(
                editing = s.expense, onDismiss = { sheet = null },
                onInvalid = { state.toast(tr(R.string.expenses_enter_a_valid_amount_first), ToastKind.Info) },
                onDelete = {
                    vm.delete(s.expense.id)
                    state.toast(tr(R.string.expenses_expense_deleted), ToastKind.Error)
                },
            ) { paise, cat, note, at ->
                vm.update(s.expense.id, paise, cat, note, at)
                state.toast(tr(R.string.expenses_saved), ToastKind.Success)
            }
            null -> Unit
        }

        if (showEnable) EnableCaptureDialog(onDismiss = { showEnable = false })
    }
}

@Composable
private fun EnableCaptureDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    EnableAccessDialog(
        title = tr(R.string.expenses_allow_notification_access),
        body = tr(R.string.expenses_quiver_reads_payment_notifications_gpay),
        step = tr(R.string.expenses_in_settings_turn_on_quiver),
        accent = Accents.Expenses,
        onOpenSettings = { openListenerSettings(context) },
        onDismiss = onDismiss,
    )
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

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    val colors = Quiver.colors
    val col = catColor(e.category)
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(col.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(catIcon(e.category), null, Modifier.size(18.dp), tint = col) }
        Column(Modifier.weight(1f)) {
            Text(e.note.ifBlank { e.category.displayName }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text, maxLines = 1)
            Text(if (e.auto) tr(R.string.expenses_x_auto, e.category.displayName) else e.category.displayName, fontSize = 12.sp, color = colors.dim)
        }
        // Amber dot: auto-captured, category not confirmed yet.
        if (e.needsReview) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFBBF24)))
        Text(ExpenseMath.formatPaise(e.amountPaise), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono, color = colors.text)
    }
}

@Composable
private fun AutoTrackCard(ac: com.kamboji.quiver.ui.theme.Accent, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onEnable: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(16.dp))
            .clickable(onClick = onEnable).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(ac.iconColor(colors.dark).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = ac.iconColor(colors.dark))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(subtitle, fontSize = 11.5.sp, color = colors.dim, lineHeight = 15.sp)
        }
        Text(tr(R.string.expenses_enable), color = ac.txt(colors.dark), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    val colors = Quiver.colors
    val amber = Quiver.accent.txt(colors.dark)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(amber.copy(alpha = 0.08f))
            .border(1.dp, amber, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(amber))
        Text(
            trPlural(R.plurals.expenses_auto_detected, count),
            Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text,
        )
    }
}

@Composable
private fun EmptyExpenses(ac: com.kamboji.quiver.ui.theme.Accent) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.surf)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Payments, null, Modifier.size(24.dp), tint = ac.iconColor(colors.dark)) }
        Spacer(Modifier.height(14.dp))
        Text(tr(R.string.expenses_no_spends_this_month), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = colors.text)
        Text(tr(R.string.expenses_tap_to_add_one_or), fontSize = 13.5.sp, color = colors.dim)
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
        title = if (isEdit) tr(R.string.expenses_edit_expense) else tr(R.string.expenses_new_expense),
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
            ) { Text(if (isEdit) tr(R.string.expenses_save_changes) else tr(R.string.expenses_add_expense), color = Quiver.accent.on, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
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
                            Text(cat.displayName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (sel) colors.text else colors.dim)
                        }
                    }
                }
                // note
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surf)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    if (note.isEmpty()) Text(tr(R.string.expenses_what_was_it_optional), color = colors.dim, fontSize = 14.5.sp)
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
                        if (date == LocalDate.now()) tr(R.string.common_today) else date.format(DAY_FMT),
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
                    ) { Text(tr(R.string.expenses_delete_expense), color = Color(0xFFFB7185), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
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
