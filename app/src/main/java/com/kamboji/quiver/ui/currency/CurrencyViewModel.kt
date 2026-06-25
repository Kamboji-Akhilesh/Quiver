package com.kamboji.quiver.ui.currency

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamboji.quiver.ui.currency.data.Cached
import com.kamboji.quiver.ui.currency.data.Currency
import com.kamboji.quiver.ui.currency.data.CurrencyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CurrencyUi {
    data object Loading : CurrencyUi
    data class Error(val message: String) : CurrencyUi
    data class Data(val cached: Cached<List<Currency>>) : CurrencyUi
}

class CurrencyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CurrencyRepository(app)

    var from by mutableStateOf("INR")
        private set
    var to by mutableStateOf("USD")
    var amount by mutableStateOf("100")

    private val _ui = MutableStateFlow<CurrencyUi>(CurrencyUi.Loading)
    val ui = _ui.asStateFlow()

    init { load() }

    fun selectFrom(value: String) {
        from = value
        load()
    }

    fun swap() {
        val f = from
        from = to
        to = f
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = CurrencyUi.Loading
            _ui.value = try {
                CurrencyUi.Data(repo.ratesWithNames(from))
            } catch (e: Exception) {
                CurrencyUi.Error(e.message ?: "Couldn't load rates.")
            }
        }
    }

    /** Converted value for the current amount/to, from the loaded rates. */
    fun convert(currencies: List<Currency>): Double? {
        val amt = amount.toDoubleOrNull() ?: return null
        val rate = if (to == from) 1.0 else currencies.firstOrNull { it.symbol == to }?.rate
        return rate?.let { amt * it }
    }
}
