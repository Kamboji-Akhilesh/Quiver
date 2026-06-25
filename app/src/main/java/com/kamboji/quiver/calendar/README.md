# Calendar mini-app

A Google-Calendar-style scheduler for **tasks and events**, with alerts that can
be a normal notification or a full-screen **"call"**.

## How it works
- `data/CalendarEntry` + `data/CalendarStore` model and persist entries (tasks or
  events) as JSON in SharedPreferences. Each entry has an alert **style**
  (none / notification / call) and **lead time** (at time … 1 day before).
- `CalendarViewModel` does CRUD and, on every change, (re)schedules the alert.
- `CalendarActivity` (Compose) provides month / week / day views + a day agenda
  and a shared add/edit screen for both tasks and events.
- `alert/AlertScheduler` arms an exact `AlarmManager` alarm at `start − lead`.
  `alert/AlertReceiver` then delivers it via `alert/AlertNotifier`:
  - **notification** → heads-up notification.
  - **call** → full-screen `EventCallActivity` that rings (RingtoneManager) until
    accepted (task title hidden until accept, for privacy), then speaks it (TTS)
    with snooze (+10 min) / mark-done / dismiss.

## Key files
- `data/` — entry model + JSON store
- `CalendarViewModel.kt`, `CalendarActivity.kt` — state + month/week/day UI
- `EventCallActivity.kt` — full-screen ring-until-accept call screen
- `alert/` — `AlertScheduler`, `AlertReceiver`, `AlertNotifier`

## Permissions
`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`,
`USE_FULL_SCREEN_INTENT`.

## Not yet
Spoken-reply (speech-to-text) + on-device LLM understanding for the call alert
(currently ring + speak + buttons).
