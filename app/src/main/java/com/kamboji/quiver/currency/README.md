# Currency mini-app

An offline-first currency converter and rate list, powered by the free,
key-less [Frankfurter](https://www.frankfurter.app) API — no backend.

## How it works
- `data/CurrencyRepository` is offline-first:
  1. **Online** → fetch via OkHttp, **save the raw JSON** to a SharedPreferences
     cache, return fresh data.
  2. **Offline / request fails** → return the last saved copy, flagged stale.
  3. **Neither** → a clear "no internet and nothing cached" error.
- `CurrencyViewModel` exposes the rates + conversion state; `CurrencyActivity`
  (Compose) renders the amount field, from/to pickers, live result, and the
  rate list, with an "offline – showing saved rates" banner when stale.

## Key files
- `data/CurrencyRepository.kt` — API client + cache + offline-first logic + models
- `CurrencyViewModel.kt` — state + conversion
- `CurrencyActivity.kt` — Compose UI

## Permissions
`INTERNET`, `ACCESS_NETWORK_STATE`.

## Not yet
Historical-rate chart (the Flutter version had one).
