package com.kamboji.quiver.currency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesTab(vm: CurrencyViewModel) {
    val rates by vm.rates.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    when (val s = rates) {
        is Ui.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is Ui.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            ErrorRetry(s.message) { vm.loadRates() }
        }
        is Ui.Data -> {
            val cached = s.value
            val list = cached.data
            val symbols = (list.map { it.symbol } + vm.ratesBase)
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch { refreshing = true; vm.refreshRates(); refreshing = false }
                },
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (cached.isStale) OfflineBanner(cached.fetchedAtMillis)
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp, 16.dp, 8.dp, 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Value of 1 ")
                        CurrencyDropdown(vm.ratesBase, symbols, { vm.selectRatesBase(it) })
                        Text(" in other currencies")
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.symbol }) { c ->
                            ListItem(
                                headlineContent = { Text(c.name) },
                                supportingContent = { Text(c.symbol) },
                                trailingContent = {
                                    Text("${fmtRate(c.rate)} ${vm.ratesBase}",
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
