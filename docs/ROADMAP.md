# Quiver roadmap

The product thesis every phase serves: **private, offline utilities glued
together by one on-device assistant.** A feature earns its place if the agent
makes it more useful than a standalone alternative. Phases are ordered by
user value ÷ risk, sized to ship independently, and each ends green: compiles,
unit tests pass, `training/` regenerated + validated whenever tools change.

**Standing rule for every phase that adds/changes agent tools:** update all
three synced artifacts together — `QuiverAgent.prompt()` ⇄
`training/generate_dataset.py` ⇄ `grammar/quiver_tools.gbnf` (+ assets copy) —
then add scenarios to `scenario_bank.py`, cases to `evalset.jsonl`, rerun
`generate_dataset.py` + `validate_dataset.py`, and eventually retrain + `eval.py`.

---

## Phase 1 — Expense tracker mini-app  ✅ DONE (2026-07-06)

The felt pain. Offline, private, agent-first.

- `expenses/data/` — `Expense` (id, amountPaise:Long, category, note, atMillis)
  + `ExpenseStore` following the NotesStore/CalendarStore JSON-store pattern.
  Amounts stored in paise (Long) — never float money.
- Categories: food, groceries, transport, shopping, bills, health,
  entertainment, other (enum with label + color).
- `ui/expenses/ExpensesScreen.kt` — month header (total + per-category bars),
  day-grouped list, add/edit sheet (amount, category chips, note, date), delete.
  Follows the Calendar/Notes visual patterns (QvTopBar, glass cards, pinned
  action button with the weight(fill=false) trick).
- Shell wiring: `AppKey.Expenses`, hub card, dock entry, accent color.
- Agent tools: `add_expense(amount, category, note, date)` +
  `list_expenses(period "YYYY-MM")` as an INFO tool (feeds a findings round so
  the model can answer "what did I spend on food this month?").
- Training sync per the standing rule (all 6 languages: expense phrasings,
  spend-summary paired rounds).
- Unit tests for the pure logic (category parsing, month summaries, paise math).

## Phase 1b — UPI auto-capture  ✅ DONE (2026-07-06)

Payments logged automatically from notifications — fully on-device:

- `PaymentCaptureService` (NotificationListenerService): UPI + SMS apps parsed
  in full; any other app's notification must contain "debited" (covers bank
  apps without whitelisting them).
- `PaymentParser` (unit-tested, bias against false positives): amount → paise
  without floats, payee/VPA, UTR; rejects credits, refunds, promos, requests,
  failures, OTPs, balance alerts. Unparsed real payments → add as a test case,
  extend patterns.
- `CaptureDedup`: same payment notifying twice (UPI app + bank SMS) collapses
  by UTR, or by amount within a 3-minute window.
- Captures land as `auto` + `needsReview`; Expenses screen shows an enable
  card (notification access) or a review banner; editing/saving confirms.
- NOT done (deliberate): READ_SMS fallback — the listener already sees SMS-app
  notifications; revisit only if a bank proves silent.

## Phase 2 — Entry points: share sheet, shortcuts, quick-add, alert actions

Makes Quiver part of daily flows instead of a destination.

- **Share sheet**: `ACTION_SEND` router activity — shared text → note / task /
  expense / "ask Quiver AI" chooser; shared image → schedule for screenshot
  cleanup. This is the highest-leverage single feature in the phase.
- **Static app shortcuts** (long-press icon): New note, New task, Add expense,
  Ask AI. `shortcuts.xml` + deep-link handling in QuiverActivity.
- **Calendar natural-language quick-add**: one text field above the month view
  ("dentist tomorrow 6pm") parsed by `WhenResolver` + a small title/time
  splitter. The AI path must not be the only smart path.
- **Notification actions on alerts**: Done / Snooze 10 min buttons (extend
  AlertNotifier/AlertActionReceiver).

## Phase 3 — Deferred product hygiene: export/backup + onboarding

- **Export/backup**: single "Export data" (notes + calendar + expenses →
  one JSON file via ACTION_CREATE_DOCUMENT) and matching import with merge.
  Also verify Android auto-backup rules actually cover the stores.
- **Onboarding**: 3-screen first-run flow (what Quiver is → screenshot
  monitoring + notification permission with context → AI model install offer).
  Stop firing all permission dialogs at once from QuiverActivity.onCreate.

## Phase 4 — Home-screen widgets (Glance)

- Today's agenda widget (tasks checkable via broadcast).
- Quick-actions widget (note / task / expense / mic-to-AI).
- Month spend widget once Phase 1 is in.
- Dependency: `androidx.glance:glance-appwidget`.

## Phase 5 — Screenshots OCR + search

- ML Kit on-device text recognition at detection time (latin + devanagari
  models to start), text stored in a new Room table keyed by MediaStore id.
- Search field in the Screenshots screen.
- Agent INFO tool `search_screenshots(query)` → the "wifi password" use case.
- Backfill action for existing screenshots (manual, batched, charger-friendly).

## Phase 6 — AppFunctions: Quiver tools inside Gemini assistant

Expose the same capabilities to the OS assistant layer (Android 16+):

- `androidx.appfunctions` (KSP processor + schema lib). **Alpha API — pin the
  version, expect churn, isolate everything in an `appfunctions/` package.**
- Start with the **predefined schemas** Gemini actually invokes today
  (Notes: createNote/…); map them onto NotesStore.
- Free-form functions for `addExpense`, `createTask` — discoverable now,
  assistant support arrives as Google widens schema coverage.
- Guard everything behind SDK 36 checks; the app must behave identically on
  older devices. Test with AppFunctionManager self-execution before relying on
  Gemini rollout (device must have Gemini as assistant).
- Risk note: this phase depends on Google's rollout pace — timebox it, don't
  block later phases on it.

## Phase 7 — Localization + round-up

- **UI localization** for hi/bn/ta/te/mr: extract hardcoded Compose strings to
  `strings.xml`, translate, in-app language override (AppCompatDelegate
  per-app locales). Done last deliberately: it touches every screen, so doing
  it earlier would conflict with phases 1–6.
- **Voice notes**: mic button in the note editor → existing STT → text.
- **Currency rate alerts**: "alert me when USD→INR crosses 90" — periodic
  WorkManager check + notification, managed from the Currency screen; agent
  tool `add_rate_alert(from, to, threshold)`.

---

## Continuous (every phase)

- New real-world agent failures → `training/dataset/seed.jsonl`.
- Retrain + `eval.py` when tool surface changes; ship model only on improved
  pass-rate; bump `AiModel.FILE_NAME` version on every shipped retrain.
