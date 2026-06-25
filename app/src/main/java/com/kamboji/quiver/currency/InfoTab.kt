package com.kamboji.quiver.currency

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kamboji.quiver.currency.data.Cached
import com.kamboji.quiver.currency.data.Currency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InfoTab(vm: CurrencyViewModel) {
    val rates by vm.rates.collectAsState()
    val online = vm.online()
    val lastUpdated = ((rates as? Ui.Data<Cached<List<Currency>>>)?.value)?.fetchedAtMillis

    LazyColumn(Modifier.padding(16.dp)) {
        item {
            InfoCard(
                if (online) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                "Connection",
                if (online) "Online – fetching live rates" else "Offline – showing saved rates",
            )
        }
        if (lastUpdated != null) {
            item {
                InfoCard(
                    Icons.Filled.Update,
                    "Rates last updated",
                    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastUpdated)),
                )
            }
        }
        item {
            InfoCard(Icons.Filled.Dataset, "Data source",
                "Frankfurter API (European Central Bank reference rates)")
        }
        item {
            InfoCard(Icons.Filled.Save, "Offline storage",
                "Every response is saved on-device. Without internet, the most " +
                    "recently saved data is shown automatically.")
        }
        item {
            InfoCard(Icons.Filled.Info, "About",
                "Rates are indicative and provided for reference only.")
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, subtitle: String) {
    Card(Modifier.padding(vertical = 4.dp)) {
        ListItem(
            leadingContent = { Icon(icon, null) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
        )
    }
}
