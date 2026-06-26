package com.kamboji.quiver.ui.currency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.currency.CurrencyViewModel
import com.kamboji.quiver.currency.HistoryRange
import com.kamboji.quiver.currency.Ui
import com.kamboji.quiver.currency.data.Cached
import com.kamboji.quiver.currency.data.Currency
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
    var tab by remember { mutableStateOf("convert") }
    var pickerFor by remember { mutableStateOf<String?>(null) } // "from" | "to" | null

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
            QvTopBar("Currency", ac, onBack = { state.go(AppKey.Hub) })
            Column(Modifier.padding(horizontal = 18.dp)) {
                Segmented(listOf("convert" to "Convert", "rates" to "Rates", "info" to "Info"), tab, ac) { tab = it }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    "convert" -> ConvertTab(vm, ac) { pickerFor = it }
                    "rates" -> RatesTab(vm, ac)
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
private fun Segmented(options: List<Pair<String, String>>, active: String, ac: Accent, onSelect: (String) -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (colors.dark) Color(0x38000000) else Color(0x0F12162D))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (k, label) ->
            val sel = k == active
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Brush.linearGradient(listOf(ac.a, ac.b)) else SolidColor(Color.Transparent))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(k) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) Color(0xFF08120D) else colors.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
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
        // amount card
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(ac.a.copy(alpha = if (colors.dark) 0.2f else 0.16f), ac.b.copy(alpha = 0.1f))))
                .border(1.dp, ac.a.copy(alpha = 0.3f), RoundedCornerShape(26.dp)).padding(20.dp),
        ) {
            Text("You send", fontSize = 12.sp, color = colors.dim, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.from, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ac.txt(colors.dark), fontFamily = Mono)
                BasicTextField(
                    vm.amount, { vm.amount = it.filter { c -> c.isDigit() || c == '.' } },
                    textStyle = TextStyle(color = colors.text, fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = Mono),
                    cursorBrush = SolidColor(ac.a), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("100", "500", "1000").forEach { a ->
                    val sel = vm.amount == a
                    Box(
                        Modifier.clip(RoundedCornerShape(100.dp))
                            .background(if (sel) ac.a.copy(alpha = 0.18f) else Color.Transparent)
                            .border(1.dp, colors.border, RoundedCornerShape(100.dp))
                            .clickable { vm.amount = a }.padding(horizontal = 13.dp, vertical = 6.dp),
                    ) { Text(a, color = if (sel) ac.txt(colors.dark) else colors.dim, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = Mono) }
                }
            }
        }

        // from / swap / to
        Spacer(Modifier.height(8.dp))
        CurrencyRow(vm.from, ac) { openPicker("from") }
        Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ac.a, ac.b)))
                    .border(3.dp, colors.bg, CircleShape).clickable { vm.swap() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SwapVert, "Swap", Modifier.size(22.dp), tint = Color(0xFF08120D)) }
        }
        CurrencyRow(vm.to, ac) { openPicker("to") }

        // result
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().glass(colors).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("They receive", fontSize = 12.sp, color = colors.dim, fontWeight = FontWeight.SemiBold)
            Text(
                if (result != null) "${fmt(result)} ${vm.to}" else "—",
                fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = ac.txt(colors.dark),
            )
            if (rate != null) Text("1 ${vm.from} = ${fmt4(rate)} ${vm.to}", fontSize = 12.5.sp, color = colors.dim, fontFamily = Mono)
        }

        // status badge
        Spacer(Modifier.height(14.dp))
        val stale = cached?.isStale == true
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (stale) Color(0xFFFBBF24) else Color(0xFF34D399)))
            Spacer(Modifier.size(8.dp))
            Text(
                if (stale) "Offline · showing last saved rates" else "Live rates · updated just now",
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (stale) Color(0xFFFBBF24) else colors.dim,
            )
        }

        // real historical trend for the from→to pair
        Spacer(Modifier.height(16.dp))
        TrendSection(vm, ac)
    }
}

@Composable
private fun TrendSection(vm: CurrencyViewModel, ac: Accent) {
    val colors = Quiver.colors
    val seriesUi by vm.series.collectAsState()
    val points = (seriesUi as? Ui.Data)?.value?.data ?: emptyList()
    val first = points.firstOrNull()?.rate
    val last = points.lastOrNull()?.rate
    val changePct = if (first != null && last != null && first != 0.0) (last - first) / first * 100 else null
    val up = (changePct ?: 0.0) >= 0
    val lineColor = if (up) Color(0xFF34D399) else Color(0xFFFB7185)

    Column(Modifier.fillMaxWidth().glass(colors).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${vm.from} → ${vm.to}", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                Text("${vm.range.label} trend", fontSize = 11.5.sp, color = colors.dim)
            }
            if (changePct != null) {
                Text(
                    (if (up) "▲ " else "▼ ") + "%.2f%%".format(kotlin.math.abs(changePct)),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = lineColor,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text(if (seriesUi is Ui.Loading) "Loading trend…" else "No trend data", color = colors.faint, fontSize = 12.sp)
            }
        } else {
            Sparkline(points.map { it.rate }, lineColor, Modifier.fillMaxWidth().height(80.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryRange.entries.forEach { r ->
                val sel = vm.range == r
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp))
                        .background(if (sel) ac.a.copy(alpha = 0.18f) else Color.Transparent)
                        .border(1.dp, if (sel) ac.a.copy(alpha = 0.4f) else colors.border, RoundedCornerShape(100.dp))
                        .clickable { vm.selectRange(r) }.padding(horizontal = 13.dp, vertical = 6.dp),
                ) { Text(r.label, color = if (sel) ac.txt(colors.dark) else colors.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    val fill = color.copy(alpha = 0.14f)
    Canvas(modifier) {
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
    }
}

@Composable
private fun CurrencyRow(code: String, ac: Accent, onClick: () -> Unit) {
    val colors = Quiver.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (colors.dark) Color(0x2E000000) else Color(0x8CFFFFFF))
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
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

    Column {
        Row(
            Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(100.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = colors.dim)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search currency…", color = colors.dim, fontSize = 14.sp)
                BasicTextField(query, { query = it }, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), cursorBrush = SolidColor(ac.a), singleLine = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Base: 1 ${vm.from} ${flag(vm.from)}", fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().glass(colors, RoundedCornerShape(20.dp))
                        .clickable { vm.selectTo(c.symbol) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(flag(c.symbol), fontSize = 26.sp)
                    Column(Modifier.weight(1f)) {
                        Text(c.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text)
                        Text(c.name, fontSize = 11.5.sp, color = colors.dim, maxLines = 1)
                    }
                    Text(fmtRate(c.rate), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = colors.text)
                }
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
        InfoCard(Icons.Outlined.CloudQueue, "Offline-first", "Rates fetch live when you're online and fall back to the last saved snapshot when you're not.", ac)
        InfoCard(Icons.Outlined.TrendingUp, "Frankfurter API", "European Central Bank reference rates. No key, no account, updated every business day.", ac)
        InfoCard(Icons.Outlined.Schedule, "Last updated", "$updated${if (cached?.isStale == true) " · showing saved copy" else ""}", ac)
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, ac: Accent) {
    val colors = Quiver.colors
    Row(Modifier.fillMaxWidth().glass(colors).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ac.a.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = ac.txt(colors.dark))
        }
        Column {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.text)
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
            Text("Select currency", fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = colors.text, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
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

private fun fmt(n: Double) = "%,.2f".format(n)
private fun fmt4(n: Double) = "%.4f".format(n)
private fun fmtRate(n: Double) = if (n < 5) "%.4f".format(n) else "%,.2f".format(n)

// Minimal English names for the codes we show flags for; falls back to the code.
private fun currencyName(code: String): String = NAMES[code] ?: code
private val NAMES = mapOf(
    "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound", "JPY" to "Japanese Yen",
    "INR" to "Indian Rupee", "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar",
    "CHF" to "Swiss Franc", "CNY" to "Chinese Yuan", "SGD" to "Singapore Dollar",
)
