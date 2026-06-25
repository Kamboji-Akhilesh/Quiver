package com.kamboji.quiver.currency

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.currency.data.Cached
import com.kamboji.quiver.currency.data.Currency

@Composable
fun ConverterTab(vm: CurrencyViewModel) {
    val scheme = MaterialTheme.colorScheme
    val converter by vm.converter.collectAsState()
    val focus = LocalFocusManager.current

    val symbols = (converter as? Ui.Data)?.value?.data?.map { it.symbol }
        ?: listOf(vm.from, vm.to)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        // Amount field with the "from" code beside it.
        Box(
            Modifier
                .padding(start = 32.dp, top = 32.dp, end = 32.dp)
                .fillMaxWidth()
                .border(1.dp, Color(0xFFC9C9C9)),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = vm.amount,
                    onValueChange = { vm.amount = it.filter { c -> c.isDigit() || c == '.' } },
                    textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = scheme.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Text(vm.from, fontSize = 24.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        // From / swap / To / GO.
        Row(
            Modifier.padding(start = 32.dp, top = 32.dp, end = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyDropdown(vm.from, symbols, { vm.selectFrom(it) })
            Box(contentAlignment = Alignment.Center) {
                Text("to")
                IconButton(onClick = { vm.swap() }) {
                    Icon(Icons.Filled.Repeat, "Swap currencies",
                        Modifier.size(48.dp), tint = scheme.primary)
                }
            }
            CurrencyDropdown(vm.to, symbols, { vm.selectTo(it) })
            Button(
                onClick = { focus.clearFocus() },
                shape = CircleShape,
                contentPadding = PaddingValues(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.secondary,
                    contentColor = scheme.onSecondary,
                ),
            ) { Text("GO", fontSize = 18.sp) }
        }

        ConversionResult(vm, converter)

        Text(
            "Historical Data",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 32.dp, end = 32.dp),
        )
        Row(
            Modifier.padding(start = 32.dp, top = 16.dp, end = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HistoryRange.entries.forEach { r ->
                RangeRadio(r.label, vm.range == r) { vm.selectRange(r) }
            }
        }
        Text(
            "Value of 1 ${vm.from} in ${vm.to} for the past ${vm.range.days} days",
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        HistoryChart(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConversionResult(vm: CurrencyViewModel, converter: Ui<Cached<List<Currency>>>) {
    when (converter) {
        is Ui.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            CircularProgressIndicator()
        }
        is Ui.Error -> Box(Modifier.padding(top = 16.dp)) {
            ErrorRetry(converter.message) { vm.loadConverter() }
        }
        is Ui.Data -> {
            val cached = converter.value
            val rate = vm.rateOf(vm.to, cached.data)
            if (rate == null) {
                Text("${vm.from} → ${vm.to} is not supported.",
                    Modifier.padding(32.dp))
            } else {
                val amount = vm.amount.toDoubleOrNull() ?: 0.0
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cached.isStale) {
                        Spacer(Modifier.height(16.dp))
                        OfflineBanner(cached.fetchedAtMillis)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("${fmtMoney(amount * rate)} ${vm.to}",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("1 ${vm.from} = ${fmtRate(rate)} ${vm.to}",
                        fontSize = 16.sp, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(vm: CurrencyViewModel) {
    val series by vm.series.collectAsState()
    when (val s = series) {
        is Ui.Loading -> Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
            CircularProgressIndicator()
        }
        is Ui.Error -> Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
            ErrorRetry(s.message) { vm.loadSeries() }
        }
        is Ui.Data -> {
            val cached = s.value
            val points = cached.data
            if (points.size < 2) {
                Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
                    Text("Not enough historical data.")
                }
            } else {
                Column {
                    if (cached.isStale) OfflineBanner(cached.fetchedAtMillis)
                    Spacer(Modifier.height(16.dp))
                    RateLineChart(points, vm.to)
                }
            }
        }
    }
}
