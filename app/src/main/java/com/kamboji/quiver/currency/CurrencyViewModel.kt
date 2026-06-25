package com.kamboji.quiver.currency

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamboji.quiver.currency.data.Cached
import com.kamboji.quiver.currency.data.Connectivity
import com.kamboji.quiver.currency.data.Currency
import com.kamboji.quiver.currency.data.CurrencyRepository
import com.kamboji.quiver.currency.data.RatePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Generic async UI state. */
sealed interface Ui<out T> {
    data object Loading : Ui<Nothing>
    data class Error(val message: String) : Ui<Nothing>
    data class Data<T>(val value: T) : Ui<T>
}

enum class HistoryRange(val days: Int, val label: String) {
    D30(30, "30 days"),
    D60(60, "60 days"),
    D90(90, "90 days"),
}

class CurrencyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CurrencyRepository(app)

    // --- Converter tab ---
    var amount by mutableStateOf("250")
    var from by mutableStateOf("USD")
        private set
    var to by mutableStateOf("EUR")
        private set
    var range by mutableStateOf(HistoryRange.D30)
        private set

    private val _converter = MutableStateFlow<Ui<Cached<List<Currency>>>>(Ui.Loading)
    val converter = _converter.asStateFlow()
    private val _series = MutableStateFlow<Ui<Cached<List<RatePoint>>>>(Ui.Loading)
    val series = _series.asStateFlow()

    // --- Rates tab ---
    var ratesBase by mutableStateOf("INR")
        private set
    private val _rates = MutableStateFlow<Ui<Cached<List<Currency>>>>(Ui.Loading)
    val rates = _rates.asStateFlow()

    init {
        loadConverter()
        loadSeries()
        loadRates()
    }

    fun selectFrom(code: String) { from = code; loadConverter(); loadSeries() }
    fun selectTo(code: String) { to = code; loadSeries() }
    fun swap() { val f = from; from = to; to = f; loadConverter(); loadSeries() }
    fun selectRange(r: HistoryRange) { range = r; loadSeries() }
    fun selectRatesBase(code: String) { ratesBase = code; loadRates() }

    fun loadConverter() {
        _converter.value = Ui.Loading
        viewModelScope.launch { _converter.value = attempt { repo.ratesWithNames(from) } }
    }

    fun loadSeries() {
        _series.value = Ui.Loading
        viewModelScope.launch { _series.value = attempt { repo.timeSeries(from, to, range.days) } }
    }

    fun loadRates() {
        _rates.value = Ui.Loading
        viewModelScope.launch { _rates.value = attempt { repo.ratesWithNames(ratesBase) } }
    }

    /** Pull-to-refresh: reload without flashing the full-screen spinner. */
    suspend fun refreshRates() {
        _rates.value = attempt { repo.ratesWithNames(ratesBase) }
    }

    fun online(): Boolean = Connectivity.isOnline(getApplication())

    /** Rate of 1 [from] in [to], from the loaded converter list. */
    fun rateOf(to: String, list: List<Currency>): Double? =
        if (to == from) 1.0 else list.firstOrNull { it.symbol == to }?.rate

    private suspend fun <T> attempt(block: suspend () -> T): Ui<T> =
        try {
            Ui.Data(block())
        } catch (e: Exception) {
            Ui.Error(e.message ?: "Couldn't load rates.")
        }
}
