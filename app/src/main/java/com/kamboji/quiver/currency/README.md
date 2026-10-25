# Currency mini-app

An offline-first currency converter, rate list, historical chart and **rate
alerts** — powered by the free, key-less [Frankfurter](https://www.frankfurter.app)
API. No account, no backend.

## The problem
Converters fail you at exactly the wrong moment: you're abroad, roaming is off,
and the app shows a spinner. And when you're waiting for a rate to move in your
favour, you have to remember to check it — which means you check it obsessively,
or you miss it.

## The solution
1. **Offline-first**, not online-only: rates are cached, so the app always answers
   — clearly labelling when the number is a saved one.
2. **Rate alerts**: tell Quiver the number you're waiting for and it watches in
   the background and notifies you when the rate crosses it.

## The tabs
- **Converter** — amount + from/to pickers + swap, the live converted result
  ("1 FROM = rate TO"), a 30/60/90-day range selector, and a gradient
  **historical line chart** (Frankfurter time-series).
- **Rates** — "value of 1 [base] in other currencies", with a base picker, the
  full rate list and pull-to-refresh.
- **Alerts** — create and manage rate alerts.
- **Info** — connection status, when rates were last updated, the data source, and
  offline-storage notes.

## Offline-first (`data/CurrencyRepository`)
1. **Online** → fetch via OkHttp, **save the raw JSON** to a SharedPreferences
   cache, return fresh data.
2. **Offline / request fails** → return the last saved copy, flagged stale (an
   *"offline – showing saved data"* banner appears).
3. **Neither** → a clear "no internet and nothing cached" error, with retry.

## Rate alerts
- `data/RateAlert` — the alert model + store.
- `data/RateAlertLogic` — pure, unit-tested crossing/direction logic. The
  **direction is inferred** from the live rate when the alert is created: if the
  rate is below your threshold, you want to know when it *rises past* it, and vice
  versa.
- `worker/RateAlertWorker` — a periodic (~6 h), network-constrained worker that
  checks each alert via `CurrencyRepository.rateBetween` and notifies on a cross.
  It **cancels itself** when no alerts remain, so it costs nothing when unused.

## Key files
- `data/CurrencyRepository.kt` — API client + cache + offline-first logic +
  `ratesWithNames` / `timeSeries` / `rateBetween` + models
- `data/RateAlert.kt` — `RateAlert`, `RateAlertStore`, `RateAlertLogic`
- `worker/RateAlertWorker.kt` — the background check
- `CurrencyViewModel.kt` — converter / rates / series / alerts state
- `ui/currency/CurrencyScreen.kt` — the tabbed screen
- `CurrencyUi.kt` — shared widgets: offline banner, currency dropdown, range
  radio, and the Canvas line chart

## Permissions
`INTERNET`, `ACCESS_NETWORK_STATE`.

## How other parts use it
**Quiver AI** exposes `get_rate`, `convert` and `add_rate_alert` — so "tell me
when the dollar goes above 90 rupees" sets a real alert.
