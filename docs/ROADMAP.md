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

## Phase 2 — Entry points: share sheet, shortcuts, quick-add, alert actions  ✅ DONE (2026-07-07)

Makes Quiver part of daily flows instead of a destination.

- **Share sheet**: `share/ShareActivity` (ACTION_SEND) — shared text → note /
  task / expense / "ask Quiver AI" chooser (Quiver-styled bottom card); writes
  happen in the router, then the shell opens on the right screen via a one-shot
  `ShellCommand`. Shared image → the existing screenshot-cleanup flow
  (MediaStore images only — other providers' URIs can't be deleted later).
  Expense amounts need a currency marker (₹/Rs/INR/rupees) and land as
  `needsReview`; text without an amount opens the add sheet instead.
- **Static app shortcuts**: `shortcuts.xml` (New note, New task, Add expense,
  Ask AI) → action-coded intents into QuiverActivity (now `singleTask` +
  `onNewIntent`), mapped by `ShellCommand.fromIntent` onto the shell's
  one-shot flags. "Ask AI" supports a prefill (used by share-to-AI).
- **Calendar natural-language quick-add**: field above the month view;
  `QuickAddParser` (unit-tested) splits title/when fragments, `WhenResolver`
  (now weekday-aware, benefiting the agent too) resolves them. Clock time or
  daypart → event; date only → task. No date → the selected day.
- **Notification actions on alerts**: already shipped earlier (Mark as done +
  Remind in 5 min in AlertNotifier/AlertActionReceiver) — kept 5 min, no change.

## Phase 3 — Deferred product hygiene: export/backup + onboarding  ✅ DONE (2026-07-07)

- **Export/backup**: `backup/QuiverBackup` (unit-tested) — versioned JSON
  envelope reusing each model's toJson/fromJson; Hub "Your data" section wires
  it to CreateDocument/OpenDocument. Import MERGES: items matched by content
  (never id — sequences differ across installs), newcomers get fresh ids from
  the receiving store, imported reminders are rescheduled.
- **Auto-backup rules**: were still the empty templates. With default
  "everything", `files/llm-models` (~800 MB GGUF) blows the 25 MB quota and
  kills backup outright — now both rule files include only `sharedpref` (all
  three stores + settings live there); the screenshot history DB is
  device-specific and deliberately excluded.
- **Onboarding**: `ui/onboarding/OnboardingOverlay` — what Quiver is →
  notifications/media permission asked with context → AI model install offer
  (lands in the AI panel's installer). First run shows ONLY onboarding;
  QuiverActivity's permission/settings asks now run on later launches only.

## Phase 4 — Home-screen widgets (Glance)  ✅ DONE (2026-07-07)

- `androidx.glance:glance-appwidget` 1.1.1. All three live in `widgets/`, each
  reading its store directly in `provideGlance` (no ViewModel off the main
  thread). Shared dark-glass palette + `openShellIntent` helper; own
  `widget_loading.xml` initial layout (didn't rely on Glance's internal
  `glance_default_loading_layout` resource name).
- **Agenda widget**: today's entries sorted done-last then by time; task rows
  use Glance `CheckBox` → `ToggleTaskCallback` (ActionCallback runs in a
  Glance-managed broadcast) which flips `done`, reschedules the alert and
  refreshes. Tap anywhere opens the Calendar.
- **Quick-actions widget**: note / task / expense / speak — reuses the app
  shortcut actions + their vector icons; "Speak" adds `EXTRA_AI_MIC` so the AI
  panel opens already listening (new `QuiverState.aiStartMic`, honored in
  `ChatBody` only when RECORD_AUDIO is already granted).
- **Month spend widget**: this month's total + top-3 categories from
  ExpenseStore; taps open Expenses.
- **Live refresh**: `WidgetRefresher` is called from `CalendarStore.saveAll` /
  `ExpenseStore.saveAll` — the single choke point every writer (UI, agent,
  share router, alert receiver, backup import) already funnels through, so
  widgets update with no per-caller wiring. Fire-and-forget + `runCatching`
  so a widget update can never break a save.

## Phase 5 — Screenshots OCR + search  ✅ DONE (2026-07-07)

- **On-device OCR**: bundled ML Kit `text-recognition` (Latin) +
  `text-recognition-devanagari` — models ship in the APK, no Play Services, no
  network. `ScreenshotOcr` runs both scripts and merges the text; the Task→
  coroutine bridge is a tiny `suspendCancellableCoroutine` (no extra dep).
  Runs at detection time from `ScreenshotNotification.handleScreenshot` (the
  image is guaranteed present then, so text survives auto-clean) via a
  fire-and-forget `OcrIndexer`.
- **Index**: new `screenshot_text` Room table keyed by MediaStore `_ID`
  (AppDatabase v2 + additive `MIGRATION_1_2`, no destructive fallback so trash
  history is preserved). Kept independent of deletion history — text stays
  searchable after the file is gone (the wifi-password case).
- **Search**: `ScreenshotSearch` pure helper (tokenize → DAO LIKE prefilter →
  multi-token ranking + snippet, unit-tested); a search sub-screen in the
  Screenshots screen with live results that open the image (or explain it's
  been cleaned but the text is kept).
- **Backfill**: `OcrBackfillWorker` (CoroutineWorker, `requiresCharging`,
  unique KEEP work, interruptible) triggered from a card; indexes existing
  screenshots not yet in the table.
- **Agent tool**: `search_screenshots(query)` INFO tool (round-1-only, results
  fed back). Full training sync per the standing rule — AgentTools + QuiverAgent
  (INFO_TOOLS / actionLabel / both prompt phrases), generate_dataset.py
  (SPEC_TEXT / rule line / findings block / STEP_LABELS), validate_dataset.py
  (TOOLS / INFO_TOOLS), a 6-language paired `search_ss` scenario in
  scenario_bank.py (+weight), evalset cases; dataset regenerated (2521 rows)
  and `validate_dataset.py` passes. Grammar unchanged (it's tool-agnostic).

## Phase 6 — AppFunctions: Quiver tools inside Gemini assistant  ✅ DONE (2026-07-07)

Exposes Quiver actions to the OS assistant layer (Gemini, SDK 36+). Everything
is isolated in `appfunctions/QuiverAppFunctions.kt`.

- **Version pin — important**: `androidx.appfunctions` is pinned to **alpha08**,
  NOT the latest. alpha09/alpha10 require **AGP 9.1.0+**; this module is on AGP
  8.9.1 and upgrading AGP risks the llama.cpp CMake native build. Bump the two
  together. alpha08 API quirk: `@AppFunction` lives in `androidx.appfunctions
  .service` (the `appfunctions-service` artifact); `AppFunctionContext` /
  `@AppFunctionSerializable` are in `androidx.appfunctions`. alpha08's KSP does
  NOT accept `LocalDate`/`LocalTime` params (later alphas do) — `createTask`
  takes `date`/`time` as strings and resolves them through the same
  `WhenResolver` the agent's `add_task` uses.
- **Functions** (`@AppFunction(isDescribedByKDoc = true)`, suspend, first param
  `AppFunctionContext`): `createNote`, `addExpense`, `createTask` — each writes
  the exact same store the UI/agent use, so an assistant-created item shows up
  in-app identically. Returns are `@AppFunctionSerializable` data classes with
  inline-KDoc properties. No-arg class so the runtime instantiates it without DI.
- **KSP**: `ksp("appfunctions:aggregateAppFunctions"="true")` — generates the
  inventory/invokers/serializable factories and `assets/app_functions_v2.xml`
  (verified to carry all three function ids); the service + schema metadata are
  merged into the manifest by the library, no manual wiring.
- **SDK gating is automatic**: the library's `enablePlatformAppFunctionService`
  bool is `false` in `values/` and `true` only in `values-v36/`, so below API 36
  the service Gemini binds to is disabled — the app behaves identically on older
  devices. Our function code calls no SDK-36 APIs, so no extra guards.
- **Not verifiable here**: end-to-end execution needs an SDK-36 device with
  Gemini as assistant (AppFunctionManager self-execution). Build/schema-gen are
  green; live invocation still depends on Google's rollout — timeboxed, and it
  did not block earlier phases.

## Phase 7 — Localization + round-up  ◑ PARTIAL (2026-07-07)

- **Voice notes**  ✅ — mic button in the note editor toolbar reuses the
  existing `VoiceController` (platform STT, nothing recorded); the transcript
  inserts at the cursor. Dictation language follows the chosen UI language
  (`AppLang.voiceLang()`).
- **Currency rate alerts**  ✅ — `RateAlert` model + store + pure
  `RateAlertLogic` (crossing/direction, unit-tested). `RateAlertWorker`
  (periodic ~6h, network-constrained) checks each alert via
  `CurrencyRepository.rateBetween` and notifies on a cross, cancelling itself
  when none remain. Managed from a new **Alerts** tab in the Currency screen.
  Agent tool `add_rate_alert(from, to, threshold)` (direction inferred from the
  live rate) with the full training sync — SPEC_TEXT / STEP_LABELS / validator /
  6-language `rate_alert` scenario + weight / eval cases; dataset regenerated
  (2519 rows) and `validate_dataset.py` passes.
- **UI localization**  ◑ scoped to **infra + key screens** (deliberate; the rest
  is incremental so it never blocked phases 1–6):
  - Infra: `ui/locale/AppLocale` per-app locale via a `Configuration` wrap in
    `attachBaseContext` (works on ComponentActivity, all API levels; chosen over
    AppCompatDelegate since activities aren't AppCompat). In-app language picker
    (globe in the Hub header) → persist + `recreate()`.
  - Translated to hi/bn/ta/te/mr (`values-{hi,bn,ta,te,mr}/strings.xml`):
    onboarding (all 3 screens + buttons), the dock, the language picker, and the
    Hub — greeting, all six quick-action chips, section labels, caught-up toast —
    plus the five mini-app top-bar titles (Calendar/Notes/Expenses/Screenshots/
    Currency).
  - **Remaining (follow-up)**: the per-screen *bodies* of Calendar / Notes /
    Expenses / Screenshots / Currency / AI panel (list rows, sheets, empty
    states) are still English-literal. The machinery is in place, so each screen
    is now a mechanical extract-and-translate pass.

## Engine switch: llama.cpp/GGUF → MediaPipe/.task (2026-07-08)

Chosen to fix first-token latency: the int4 `.task` is ~530 MB (vs ~720 MB GGUF)
and MediaPipe can run it on the **GPU**, where llama.cpp here was CPU-only.

- `MediaPipeEngine` (`com.google.mediapipe:tasks-genai:0.10.27`): one
  `LlmInferenceSession` per request (no state leaks between agent rounds),
  streaming via `ProgressListener` deltas, **GPU→CPU backend fallback**, and real
  cancellation (`cancelGenerateResponseAsync`) — unlike llama.cpp's
  uninterruptible prefill.
- **llama.cpp is gone.** `LlamaCppEngine`, the JNI bridge, the CMake build, the
  pinned llama.cpp submodule and the `quiver_tools.gbnf` asset were all removed
  once `.task` shipped: APK 109.0 → 100.8 MB and no NDK/CMake step (build time
  roughly halved). `EngineHolder` now constructs `MediaPipeEngine` directly.
  Restoring the GGUF path means reverting that commit.
- `maxTokens` budgets **input + output together**. The agent prompt (14 tool
  specs) is ~1k tokens, so we ask for 2048 and fall back to 1280 (some Gemma
  `.task` bundles bake a 1280 KV cache). A prompt that won't fit raises a precise
  error instead of silently truncating the plan.
- **Grammar is gone.** MediaPipe has no GBNF, so valid tool-call JSON is no
  longer *guaranteed*. The net is low temperature + `PlanJson.repair` + the
  agent's correction round. Watch for malformed plans until the fine-tune lands.
- The model repo (`litert-community/Gemma3-1B-IT`) is **gated**: the download
  needs `HF_TOKEN=hf_...` in `local.properties` (sent as a Bearer header); a 401/403
  now reports exactly that.
- Follow-ups: MediaPipe LLM Inference is in maintenance mode (Google points to
  LiteRT-LM; the same repo ships `.litertlm`). And `training/` still exports a
  **GGUF** while the runtime loads a **`.task`** — the fine-tune pipeline needs a
  `.task` bundling step before a trained model can ship.

## Fixes (post-roadmap hardening)

- **AI stuck on "Thinking…" forever**: the first output token never arrived
  because the model was wedged in *prefill* (processing the prompt), which was a
  single uninterruptible native call. The agent's `withTimeout` couldn't fire —
  structured concurrency joins the stuck child before the timeout can propagate.
  Fix: `LlamaCppEngine` now sets a ggml `abort_callback` (flag flipped by a new
  `nativeCancel`, called from the flow's `awaitClose`), so a cancelled/timed-out
  request bails out mid-prefill and surfaces a real error. Also set
  `n_threads_batch` = all cores (prefill is compute-bound; the default 4 left
  cores idle) and dropped the per-round timeout 10 min → 4 min. Needs on-device
  timing to confirm first-token latency; the hang-forever behavior is fixed.
- Onboarding buttons were left English while the strings existed → now wired.
- `AppLocale` didn't reset `Locale.getDefault()` when switching back to System →
  fixed (reads the device locale from the base config).

## Not done (out of this session's scope)

- **Model retrain**: `finetune_lora.py` + `eval.py` + shipping a new GGUF and
  bumping `AiModel.FILE_NAME`. The dataset now covers `search_screenshots` and
  `add_rate_alert` and validates, but training needs a GPU — run it before
  shipping so the model actually knows the two new tools.

---

## Continuous (every phase)

- New real-world agent failures → `training/dataset/seed.jsonl`.
- Retrain + `eval.py` when tool surface changes; ship model only on improved
  pass-rate; bump `AiModel.FILE_NAME` version on every shipped retrain.
