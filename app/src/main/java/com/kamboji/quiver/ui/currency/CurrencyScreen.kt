package com.kamboji.quiver.ui.currency

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.kamboji.quiver.ui.components.QvSegmented
import com.kamboji.quiver.ui.locale.displayName
import androidx.compose.foundation.layout.fillMaxHeight

import com.kamboji.quiver.ui.locale.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.kamboji.quiver.R
import com.kamboji.quiver.currency.CurrencyViewModel
import com.kamboji.quiver.currency.HistoryRange
import com.kamboji.quiver.currency.Ui
import com.kamboji.quiver.currency.data.Cached
import com.kamboji.quiver.currency.data.Currency
import com.kamboji.quiver.currency.data.RateAlert
import com.kamboji.quiver.currency.data.RateAlertLogic
import com.kamboji.quiver.currency.data.RateAlertStore
import com.kamboji.quiver.currency.data.RatePoint
import com.kamboji.quiver.currency.worker.RateAlertWorker
import com.kamboji.quiver.ui.components.QuiverModalSheet
import com.kamboji.quiver.ui.components.QvTopBar
import com.kamboji.quiver.ui.components.SectionLabel
import com.kamboji.quiver.ui.components.glass
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Mono
import com.kamboji.quiver.ui.theme.Quiver
import androidx.compose.runtime.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val FLAGS = mapOf(
    "USD" to "🇺🇸", "EUR" to "🇪🇺", "GBP" to "🇬🇧", "JPY" to "🇯🇵", "INR" to "🇮🇳",
    "AUD" to "🇦🇺", "CAD" to "🇨🇦", "CHF" to "🇨🇭", "CNY" to "🇨🇳", "SGD" to "🇸🇬",
    "HKD" to "🇭🇰", "NZD" to "🇳🇿", "SEK" to "🇸🇪", "NOK" to "🇳🇴", "DKK" to "🇩🇰",
    "ZAR" to "🇿🇦", "BRL" to "🇧🇷", "MXN" to "🇲🇽", "KRW" to "🇰🇷", "THB" to "🇹🇭",
    "PLN" to "🇵🇱", "TRY" to "🇹🇷", "IDR" to "🇮🇩", "MYR" to "🇲🇾", "PHP" to "🇵🇭",
    "CZK" to "🇨🇿", "HUF" to "🇭🇺", "RON" to "🇷🇴", "ISK" to "🇮🇸", "BGN" to "🇧🇬", "ILS" to "🇮🇱",
)

private fun flag(code: String) = FLAGS[code] ?: "🏳️"

@Composable
fun CurrencyScreen(state: QuiverState) {
    val ac = Accents.Currency
    val vm: CurrencyViewModel = viewModel()
    LaunchedEffect(Unit) { vm.ensureLoaded() }
    var tab by remember { mutableStateOf("convert") }
    var pickerFor by remember { mutableStateOf<String?>(null) } // "from" | "to" | null

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
            QvTopBar(stringResource(R.string.title_currency), ac, onBack = { state.go(AppKey.Hub) })
            Column(Modifier.padding(horizontal = 20.dp)) {
                QvSegmented(listOf("convert" to tr(R.string.currency_convert), "rates" to tr(R.string.currency_rates), "alerts" to tr(R.string.currency_alerts), "info" to tr(R.string.currency_info)), tab) { tab = it }
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    "convert" -> ConvertTab(vm, ac) { pickerFor = it }
                    "rates" -> RatesTab(vm, ac)
                    "alerts" -> AlertsTab(vm, ac)
                    else -> InfoTab(vm, ac)
                }
            }
        }
        if (pickerFor != null) {
            CurrencyPicker(
                vm = vm,
                forFrom = pickerFor == "from",
                onPick = { code -> if (pickerFor == "from") vm.selectFrom(code) else vm.selectTo(code) },
                onDismiss = { pickerFor = null },
            )
        }
    }
}

@Composable
private fun ConvertTab(vm: CurrencyViewModel, ac: Accent, openPicker: (String) -> Unit) {
    val colors = Quiver.colors
    val converter by vm.converter.collectAsState()
    val list = (converter as? Ui.Data)?.value?.data ?: emptyList()
    val cached = (converter as? Ui.Data)?.value
    val rate = vm.rateOf(vm.to, list)
    val amount = vm.amount.toDoubleOrNull() ?: 0.0
    val result = if (rate != null) amount * rate else null

    Column {
        // send / receive, with the swap button over their seam
        Box(contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountCard(tr(R.string.currency_you_send), vm.from, { openPicker("from") }) {
                    BasicTextField(
                        vm.amount, { vm.amount = it.filter { c -> c.isDigit() || c == '.' } },
                        textStyle = TextStyle(color = colors.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = Mono),
                        cursorBrush = SolidColor(ac.a), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AmountCard(tr(R.string.currency_they_receive), vm.to, { openPicker("to") }) {
                    Text(
                        if (result != null) fmt(result) else "—",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text, maxLines = 1,
                    )
                }
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.bg).padding(4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b))).clickable { vm.swap() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SwapVert, tr(R.string.currency_swap), Modifier.size(18.dp), tint = ac.on) }
        }

        // rate + freshness
        Spacer(Modifier.height(12.dp))
        val stale = cached?.isStale == true
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (stale) Color(0xFFFBBF24) else upColor(colors.flat)))
            Text(
                listOfNotNull(
                    rate?.let { "1 ${vm.from} = ${fmt4(it)} ${vm.to}" },
                    if (stale) tr(R.string.currency_offline_showing_last_saved_rates) else tr(R.string.currency_live_rates_updated_just_now),
                ).joinToString(" · "),
                fontSize = 12.5.sp, color = if (stale) Color(0xFFFBBF24) else colors.dim,
            )
        }

        // real historical trend for the from→to pair
        Spacer(Modifier.height(12.dp))
        TrendSection(vm, ac)
    }
}

/** One side of the conversion: a label, the amount, and the currency it's in. */
@Composable
private fun AmountCard(label: String, code: String, onPickCode: () -> Unit, amount: @Composable () -> Unit) {
    val colors = Quiver.colors
    Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 12.5.sp, color = colors.dim)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { amount() }
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(11.dp)).background(if (colors.flat) colors.bg else colors.surf2)
                    .border(1.dp, colors.border, RoundedCornerShape(11.dp)).clickable(onClick = onPickCode).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(code, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = colors.dim)
            }
        }
        Text(currencyName(code), fontSize = 12.sp, color = colors.faint)
    }
}

private fun upColor(flat: Boolean) = if (flat) Color(0xFF8FD88A) else Color(0xFF34D399)
private fun downColor(flat: Boolean) = if (flat) Color(0xFFF08F9E) else Color(0xFFFB7185)

@Composable
private fun TrendSection(vm: CurrencyViewModel, ac: Accent) {
    val colors = Quiver.colors
    val seriesUi by vm.series.collectAsState()
    val points = (seriesUi as? Ui.Data)?.value?.data ?: emptyList()
    val first = points.firstOrNull()?.rate
    val last = points.lastOrNull()?.rate
    val changePct = if (first != null && last != null && first != 0.0) (last - first) / first * 100 else null
    val up = (changePct ?: 0.0) >= 0
    val lineColor = if (up) upColor(colors.flat) else downColor(colors.flat)

    Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${vm.from} → ${vm.to}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            if (changePct != null) {
                Text(
                    (if (up) "+" else "−") + "%.2f%%".format(kotlin.math.abs(changePct)) + " · " + vm.range.displayName,
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = lineColor,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text(if (seriesUi is Ui.Loading) tr(R.string.currency_loading_trend) else tr(R.string.currency_no_trend_data), color = colors.faint, fontSize = 12.sp)
            }
        } else {
            Sparkline(points, vm.to, lineColor, Modifier.fillMaxWidth().height(96.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HistoryRange.entries.forEach { r ->
                val sel = vm.range == r
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) colors.border else Color.Transparent)
                        .clickable { vm.selectRange(r) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(r.displayName, color = if (sel) colors.text else colors.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<RatePoint>, toCode: String, color: Color, modifier: Modifier) {
    val colors = Quiver.colors
    val values = points.map { it.rate }
    val axisDate = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    // Small axes: high / mid / low rate on the right, first / middle / last date below.
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            SparklinePlot(points, toCode, color, Modifier.weight(1f).fillMaxHeight())
            if (values.isNotEmpty()) Column(Modifier.fillMaxHeight().padding(start = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                val hi = values.max()
                val lo = values.min()
                listOf(hi, (hi + lo) / 2, lo).forEach { Text(fmtRate(it), fontSize = 10.sp, fontFamily = Mono, color = colors.faint) }
            }
        }
        if (points.size > 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, points.size / 2, points.lastIndex).forEach {
                Text(axisDate.format(Date(points[it].dateMillis)), fontSize = 10.sp, fontFamily = Mono, color = colors.faint)
            }
        }
    }
}

@Composable
private fun SparklinePlot(points: List<RatePoint>, toCode: String, color: Color, modifier: Modifier) {
    val colors = Quiver.colors
    val fill = color.copy(alpha = 0.14f)
    val values = remember(points) { points.map { it.rate } }
    var active by remember(points) { mutableStateOf<Int?>(null) }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    BoxWithConstraints(modifier) {
        val widthDp = maxWidth
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { active = idxAt(it.x, size.width.toFloat(), values.size) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onDrag = { change, _ ->
                            active = idxAt(change.position.x, size.width.toFloat(), values.size)
                            change.consume()
                        },
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = {
                            active = idxAt(it.x, size.width.toFloat(), values.size)
                            tryAwaitRelease()
                            active = null
                        },
                    )
                },
        ) {
            val min = values.min()
            val max = values.max()
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            val w = size.width
            val h = size.height
            val dx = if (values.size > 1) w / (values.size - 1) else w
            fun pt(i: Int): Offset {
                val norm = ((values[i] - min) / span).toFloat()
                return Offset(i * dx, h - norm * (h * 0.86f) - h * 0.07f)
            }
            val line = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(fill, Color.Transparent)))
            drawPath(line, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            active?.let { i ->
                val p = pt(i)
                drawLine(color.copy(alpha = 0.45f), Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 1.dp.toPx())
                drawCircle(color, radius = 4.5.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = p)
            }
        }
        // Tooltip: price + date for the touched point.
        active?.let { i ->
            val frac = if (values.size > 1) i.toFloat() / (values.size - 1) else 0f
            val raw = widthDp * frac - 52.dp
            val x = raw.coerceIn(0.dp, (widthDp - 104.dp).coerceAtLeast(0.dp))
            Column(
                Modifier.offset(x = x, y = 0.dp).width(104.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (colors.dark) Color(0xF21A2230) else Color(0xF2FFFFFF))
                    .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${fmt4(values[i])} $toCode", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                Text(dateFmt.format(Date(points[i].dateMillis)), fontSize = 10.sp, color = colors.dim)
            }
        }
    }
}

private fun idxAt(x: Float, width: Float, n: Int): Int {
    if (n <= 1) return 0
    val dx = width / (n - 1)
    return (x / dx).roundToInt().coerceIn(0, n - 1)
}

@Composable
private fun CurrencyRow(code: String, ac: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(flag(code), fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(code, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
            Text(currencyName(code), fontSize = 12.sp, color = colors.dim)
        }
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp), tint = colors.dim)
    }
}

@Composable
private fun RatesTab(vm: CurrencyViewModel, ac: Accent) {
    val colors = Quiver.colors
    val ratesUi by vm.converter.collectAsState()
    var query by remember { mutableStateOf("") }
    val all = (ratesUi as? Ui.Data)?.value?.data ?: emptyList()
    val list = all.filter { query.isBlank() || it.symbol.contains(query, true) || it.name.contains(query, true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(R.string.currency_base_1_x_x, vm.from), fontSize = 13.sp, color = colors.dim)
        Row(
            Modifier.fillMaxWidth().height(44.dp).glass(colors, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(tr(R.string.currency_search_currency), color = colors.dim, fontSize = 14.sp)
                BasicTextField(query, { query = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), cursorBrush = SolidColor(ac.a), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (list.isNotEmpty()) Column(Modifier.fillMaxWidth().glass(colors).padding(horizontal = 14.dp)) {
            list.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().height(58.dp).clickable { vm.selectTo(c.symbol) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.width(46.dp).height(30.dp).clip(RoundedCornerShape(8.dp)).background(if (colors.flat) colors.bg else colors.surf2)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text) }
                    Text(c.name, Modifier.weight(1f), fontSize = 13.5.sp, color = colors.text, maxLines = 1)
                    Text(fmtRate(c.rate), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono, color = colors.text)
                }
                if (i < list.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

@Composable
private fun InfoTab(vm: CurrencyViewModel, ac: Accent) {
    val colors = Quiver.colors
    val converter by vm.converter.collectAsState()
    val cached = (converter as? Ui.Data)?.value
    val updated = cached?.fetchedAtMillis?.let { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(it)) } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(Icons.Outlined.CloudQueue, tr(R.string.currency_offline_first), tr(R.string.currency_rates_fetch_live_when_you), ac)
        InfoCard(Icons.AutoMirrored.Outlined.TrendingUp, tr(R.string.currency_frankfurter_api), tr(R.string.currency_european_central_bank_reference_rates), ac)
        InfoCard(Icons.Outlined.Schedule, tr(R.string.currency_last_updated), "$updated${if (cached?.isStale == true) tr(R.string.currency_showing_saved_copy) else ""}", ac)
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, ac: Accent) {
    val colors = Quiver.colors
    Row(Modifier.fillMaxWidth().glass(colors).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(ac.iconColor(colors.dark).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = ac.iconColor(colors.dark))
        }
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(body, fontSize = 12.5.sp, color = colors.dim, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CurrencyPicker(vm: CurrencyViewModel, forFrom: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Quiver.colors
    val converter by vm.converter.collectAsState()
    val list = (converter as? Ui.Data)?.value?.data ?: emptyList()
    // The base ("from") isn't in its own rate list; include it so it's pickable.
    val codes = (listOf(Currency(vm.from, currencyName(vm.from), 1.0)) + list).distinctBy { it.symbol }.sortedBy { it.symbol }
    val current = if (forFrom) vm.from else vm.to
    val ac = Accents.Currency

    QuiverModalSheet(onDismiss) { hide ->
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(tr(R.string.currency_select_currency), fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(codes, key = { it.symbol }) { c ->
                    val sel = c.symbol == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (sel) ac.a.copy(alpha = 0.14f) else Color.Transparent)
                            .border(1.dp, if (sel) ac.a.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { onPick(c.symbol); hide() }.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Text(flag(c.symbol), fontSize = 26.sp)
                        Column(Modifier.weight(1f)) {
                            Text(c.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                            Text(c.name, fontSize = 12.sp, color = colors.dim)
                        }
                        if (sel) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = ac.txt(colors.dark))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsTab(vm: CurrencyViewModel, ac: Accent) {
    val colors = Quiver.colors
    val context = LocalContext.current
    val store = remember { RateAlertStore(context) }
    var alerts by remember { mutableStateOf(store.getAll()) }
    val converter by vm.converter.collectAsState()
    val list = (converter as? Ui.Data)?.value?.data ?: emptyList()
    val rate = vm.rateOf(vm.to, list)
    var threshold by remember { mutableStateOf("") }

    fun reload() { alerts = store.getAll() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // create card for the currently-selected pair
        Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.currency_alert_me_when), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeChip(vm.from)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = colors.dim)
                CodeChip(vm.to)
                Text(tr(R.string.currency_crosses), fontSize = 13.sp, color = colors.dim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (colors.flat) colors.bg else colors.surf2)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (threshold.isEmpty()) Text(rate?.let { fmt(it) } ?: "90.00", color = colors.faint, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    BasicTextField(
                        threshold, { threshold = it.filter { c -> c.isDigit() || c == '.' } },
                        textStyle = TextStyle(color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Mono),
                        cursorBrush = SolidColor(ac.a), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val ready = threshold.toDoubleOrNull() != null && rate != null
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                        .graphicsLayer { alpha = if (ready) 1f else 0.5f }
                        .clickable {
                            val t = threshold.toDoubleOrNull() ?: return@clickable
                            val cur = rate ?: return@clickable
                            store.saveAll(
                                store.getAll() + RateAlert(
                                    store.nextId(), vm.from, vm.to, t,
                                    RateAlertLogic.directionAbove(cur, t), System.currentTimeMillis(), cur,
                                ),
                            )
                            RateAlertWorker.ensureScheduled(context)
                            reload(); threshold = ""
                        }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.currency_add_alert), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ac.on) }
            }
            Text(
                if (rate != null) tr(R.string.currency_now_x, fmt4(rate), vm.to) + " · " + tr(R.string.currency_checked_every_few_hours) else tr(R.string.currency_rate_unavailable),
                fontSize = 12.sp, color = colors.faint,
            )
        }

        if (alerts.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(80.dp).clip(RoundedCornerShape(28.dp)).background(ac.a.copy(alpha = 0.12f))
                        .border(1.dp, ac.a.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(36.dp), tint = ac.txt(colors.dark)) }
                Spacer(Modifier.height(14.dp))
                Text(tr(R.string.currency_no_rate_alerts), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                Text(tr(R.string.currency_we_check_every_few_hours), fontSize = 12.5.sp, color = colors.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            SectionLabel(tr(R.string.currency_your_alerts))
            alerts.forEach { a -> AlertRow(a) { store.saveAll(store.getAll().filterNot { it.id == a.id }); reload() } }
        }
    }
}

@Composable
private fun CodeChip(code: String) {
    val colors = Quiver.colors
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (colors.flat) colors.bg else colors.surf2)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(code, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text) }
}

@Composable
private fun AlertRow(alert: RateAlert, onDelete: () -> Unit) {
    val colors = Quiver.colors
    val done = alert.triggered
    Row(
        Modifier.fillMaxWidth().graphicsLayer { alpha = if (done) 0.7f else 1f }.glass(colors, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (done) colors.faint else Accents.Currency.txt(colors.dark)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${alert.from} → ${alert.to} ${if (alert.above) tr(R.string.currency_above) else tr(R.string.currency_below)} ${fmtRate(alert.threshold)}",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono, color = colors.text,
            )
            Text(
                if (done) tr(R.string.currency_triggered_last_x, fmt4(alert.lastRate)) else tr(R.string.currency_watching_now_x, fmt4(alert.lastRate)),
                fontSize = 12.sp, color = colors.dim,
            )
        }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, colors.border, RoundedCornerShape(11.dp)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.DeleteOutline, tr(R.string.currency_delete_alert), Modifier.size(18.dp), tint = colors.dim) }
    }
}

private fun fmt(n: Double) = "%,.2f".format(n)
private fun fmt4(n: Double) = "%.4f".format(n)
private fun fmtRate(n: Double) = if (n < 5) "%.4f".format(n) else "%,.2f".format(n)

// Names for the codes we show flags for (resource ids, resolved per call so a
// language switch applies); falls back to the code.
private fun currencyName(code: String): String = NAMES[code]?.let { tr(it) } ?: code
private val NAMES = mapOf(
    "USD" to R.string.currency_us_dollar, "EUR" to R.string.currency_euro, "GBP" to R.string.currency_british_pound, "JPY" to R.string.currency_japanese_yen,
    "INR" to R.string.currency_indian_rupee, "AUD" to R.string.currency_australian_dollar, "CAD" to R.string.currency_canadian_dollar,
    "CHF" to R.string.currency_swiss_franc, "CNY" to R.string.currency_chinese_yuan, "SGD" to R.string.currency_singapore_dollar,
)
