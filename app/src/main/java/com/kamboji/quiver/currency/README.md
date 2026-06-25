# Currency mini-app

An offline-first currency converter, ported to match the original Super App UI:
a 3-tab screen (**Converter / Rates / Info**) with a gradient app bar and
triangular tab indicator. Powered by the free, key-less
[Frankfurter](https://www.frankfurter.app) API — no backend.

## Tabs
- **Converter** — amount field + from/to pickers + swap + GO, a live converted
  result ("1 FROM = rate TO"), a 30/60/90-day range selector and a gradient
  **historical line chart** (Frankfurter time-series).
- **Rates** — "Value of 1 [base] in other currencies" with a base picker, the
  full rate list, and pull-to-refresh.
- **Info** — connection status, when rates were last updated, the data source,
  and offline-storage notes.

## Offline-first
`data/CurrencyRepository`:
1. **Online** → fetch via OkHttp, **save the raw JSON** to a SharedPreferences
   cache, return fresh data.
2. **Offline / request fails** → return the last saved copy, flagged stale
   (an "offline – showing saved data" banner appears).
3. **Neither** → a clear "no internet and nothing cached" error with retry.

## Key files
- `data/CurrencyRepository.kt` — API client + cache + offline-first logic +
  `ratesWithNames` / `timeSeries` + models (`Currency`, `RatePoint`, `Cached`)
- `CurrencyViewModel.kt` — converter / rates / series state
- `CurrencyActivity.kt` — tabbed scaffold + triangle indicator
- `ConverterTab.kt`, `RatesTab.kt`, `InfoTab.kt` — the three tabs
- `CurrencyUi.kt` — shared widgets: offline banner, currency dropdown, range
  radio, and the Canvas line chart

## Permissions
`INTERNET`, `ACCESS_NETWORK_STATE`.
