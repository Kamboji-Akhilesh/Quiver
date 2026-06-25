package com.kamboji.quiver.ui.currency

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.ui.currency.data.Cached
import com.kamboji.quiver.ui.currency.data.Currency
import com.kamboji.quiver.ui.hub.theme.AppTheme
import androidx.compose.foundation.text.KeyboardOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CurrencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { CurrencyScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyScreen(vm: CurrencyViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Currency") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is CurrencyUi.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CurrencyUi.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message)
                    TextButton(onClick = vm::load) { Text("Retry") }
                }
                is CurrencyUi.Data -> Loaded(vm, s.cached)
            }
        }
    }
}

@Composable
private fun Loaded(vm: CurrencyViewModel, cached: Cached<List<Currency>>) {
    val list = cached.data
    val symbols = remember(list, vm.from) {
        (listOf(vm.from) + list.map { it.symbol }).distinct().sorted()
    }
    val result = vm.convert(list)

    Column(Modifier.fillMaxSize()) {
        if (cached.isStale) OfflineBanner(cached.fetchedAtMillis)
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = vm.amount,
                onValueChange = { vm.amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CurrencyPicker("From", vm.from, symbols, Modifier.weight(1f)) { vm.selectFrom(it) }
                IconButton(onClick = vm::swap) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap")
                }
                CurrencyPicker("To", vm.to, symbols, Modifier.weight(1f)) { vm.to = it }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (result != null) "${"%,.2f".format(result)} ${vm.to}" else "—",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        HorizontalDivider()
        Text(
            "Rates · 1 ${vm.from} =",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(list) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.symbol) },
                    trailingContent = { Text("%,.4f".format(c.rate)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onPick(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner(fetchedAtMillis: Long?) {
    val scheme = MaterialTheme.colorScheme
    val when_ = fetchedAtMillis?.let {
        " · updated " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""
    Box(Modifier.fillMaxWidth().padding(0.dp)) {
        Text(
            "Offline – showing saved rates$when_",
            color = scheme.onTertiaryContainer,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 6.dp),
        )
    }
}
