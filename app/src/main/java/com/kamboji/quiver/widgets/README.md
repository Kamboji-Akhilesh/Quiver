# Home-screen widgets

Three Glance widgets that put Quiver on your home screen, so the common things
never need the app opened at all.

## The problem
A utility app you have to *open* is a utility app you forget. Checking today's
agenda, logging a coffee, or asking a question all cost several taps — enough
friction that the habit dies.

## The solution
Glance (Compose for App Widgets) widgets that read the stores directly and stay
live: today's agenda with **tickable checkboxes**, a one-tap row of quick
actions, and this month's spend at a glance.

## The widgets
### Agenda — *today, at a glance*
Today's tasks and events, sorted done-last then by time. Task rows use a Glance
`CheckBox`; ticking one runs `ToggleTaskCallback`, which flips `done`,
reschedules the alert and refreshes the widget — all without opening the app.
Tapping anywhere else opens the Calendar.

### Quick actions — *capture in one tap*
Note · Task · Expense · **Speak**. The first three reuse the app-shortcut actions
and their icons. "Speak" adds `EXTRA_AI_MIC` so the AI panel opens *already
listening* (honoured only when `RECORD_AUDIO` is granted, so it never opens a
dead mic).

### Month spend — *did I overspend?*
This month's total plus the top three categories, read from `ExpenseStore`. Taps
open Expenses.

## How it works
- Each widget reads its store directly inside `provideGlance` — no ViewModel off
  the main thread.
- A shared dark-glass palette and an `openShellIntent` helper keep them
  consistent with the app and land the user on the right screen.
- `widget_loading.xml` is our own initial layout (we don't rely on a Glance
  internal resource name).
- **Live refresh**: `WidgetRefresher` is called from `CalendarStore.saveAll` and
  `ExpenseStore.saveAll` — the single choke point every writer (UI, AI agent,
  share router, alert receiver, backup import) already funnels through. So
  widgets update with zero per-caller wiring. Calls are fire-and-forget and
  wrapped in `runCatching`: a failing widget update can never break a save.

## Key files
- `AgendaWidget.kt` — today's list + `ToggleTaskCallback`
- `QuickActionsWidget.kt` — note / task / expense / speak
- `SpendWidget.kt` — month total + top categories
- `WidgetRefresher.kt` — the store → widget update hook
- `res/xml/widget_*.xml` — widget metadata; `res/layout/widget_loading.xml`

## Dependency
`androidx.glance:glance-appwidget`.
