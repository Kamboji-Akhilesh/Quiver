# Calendar mini-app

A Google-Calendar-style scheduler for **tasks and events**, whose alerts can be a
normal notification or a full-screen **"call"** that rings until you answer, then
*reads the reminder aloud*.

## The problem
A reminder you can swipe away without reading is a reminder you'll miss. Silent
banners get dismissed on autopilot — which is exactly how you forget the
medicine, the meeting, the bill. And adding an event usually means a form with
six fields when you already know it in one sentence.

## The solution
Two ideas:
1. **Reminders that behave like a phone call.** The alert rings on the alarm
   stream, fills the screen, and won't go away until you accept — then it *speaks*
   the reminder to you. You can snooze, mark it done, or dismiss.
2. **Quick-add in one line.** Type "dentist next friday 6pm" above the month view
   and Quiver works out the rest.

## How it works
### Data & scheduling
- `data/CalendarEntry` + `data/CalendarStore` — the model and its JSON store.
  Each entry is a **task** or an **event**, with an alert **style**
  (none / notification / call) and a **lead time** (at time … 1 day before).
  `saveAll` also refreshes the agenda widget.
- `CalendarViewModel` — CRUD; every change (re)schedules the alert.
- `alert/AlertScheduler` arms an exact `AlarmManager` alarm at `start − lead`.
  `alert/AlertReceiver` delivers it via `alert/AlertNotifier`:
  - **notification** → a heads-up notification, with **Mark as done** and
    **Remind in 5 min** actions (`alert/AlertActionReceiver`).
  - **call** → the full-screen `ui/calendar/CallAlert` (or `EventCallActivity`
    when the app is closed): rings via `RingtoneManager`, keeps the task title
    **hidden until you accept** (privacy on a lock screen), then speaks it and
    offers snooze +10 min / mark done / dismiss.
- `alert/AlertDiagnostics` — surfaces the OEM battery-optimisation and
  exact-alarm settings that silently break alarms.

### The spoken voice
Speech comes from [`ai/tts/ReminderSpeaker`](../ai/README.md): **Cartesia** cloud
TTS when it's configured and reachable, falling back to the on-device
`TextToSpeech` otherwise — so a reminder always speaks, even offline. Audio plays
on the **alarm stream**, so it's loud, independent of media volume, and not
silenced by the ringer.

### Natural-language quick-add
- `QuickAddParser` (unit-tested) splits a line into a title and a "when" fragment.
- `ai/agent/WhenResolver` (weekday-aware, shared with the AI agent and the share
  router) resolves that fragment to a timestamp.
- Rule of thumb: a clock time or daypart → an **event**; a date only → a **task**;
  nothing at all → the currently selected day.

> **Known gap:** the "when" vocabulary is **English-only** (`tomorrow`, `friday`,
> `6pm`, `evening`), and the digit class is ASCII-only, so Indic day-words and
> numerals aren't recognised — the line just becomes the title. The localized
> placeholder strings therefore show a *code-mixed* example (translated title +
> English tokens), which is what actually parses. `QuickAddParserTest` pins both
> behaviours. Teaching the parser the other five languages would let those
> examples be translated in full. (The AI agent has no such limit — it understands
> all six languages and resolves the date itself.)

### UI
`ui/calendar/CalendarScreen` — month / week / day views plus a day agenda, the
quick-add field, and a shared add/edit composer for tasks and events.

## Key files
- `data/` — `CalendarEntry` + `CalendarStore`
- `CalendarViewModel.kt`, `QuickAddParser.kt`
- `alert/` — `AlertScheduler`, `AlertReceiver`, `AlertNotifier`,
  `AlertActionReceiver`, `AlertDiagnostics`
- `ui/calendar/CalendarScreen.kt`, `ui/calendar/CallAlert.kt`
- `EventCallActivity.kt` — the full-screen call when the app isn't open

## Permissions
`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`,
`USE_FULL_SCREEN_INTENT`.

## How other parts use it
- **Agenda widget** shows today's entries and lets you tick tasks off.
- **Share sheet** "Add as task" and the **New task** shortcut write here.
- **Quiver AI** `add_task` / `add_event` / `list_agenda` use the same store.
- **Gemini** can create tasks via [`appfunctions/`](../appfunctions/README.md).
