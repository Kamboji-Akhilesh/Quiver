# 🏹 Quiver

### *Your everyday utilities, in one quiver*

Quiver is a personal **app aggregator** for Android — a single native app
(Jetpack Compose) whose home is a **bento grid** of small, focused mini-apps.
Instead of a dozen separate installs, your handy tools live together and launch
from one place.

What ties them together is **Quiver AI**: a language model that runs *entirely on
your phone* and can drive every mini-app for you. Ask it to save a shopping list
and remind you tomorrow evening, and it actually does both.

> **The thesis:** private, offline utilities, glued together by one on-device
> assistant. A feature earns its place if the agent makes it more useful than a
> standalone alternative.

<!-- 📸 Screenshots go here -->

---

## Quiver AI — the assistant that runs on your phone

**The problem.** Cloud assistants send everything you say to a server, need a
connection, and can't touch your private notes or calendar. Phone-sized models,
meanwhile, are unreliable: a small model will happily *say* it set a reminder
without setting one, or emit malformed JSON that crashes the caller.

**The solution.** A hybrid design. The model only ever **plans** — it emits a JSON
list of tool calls, constrained by a **GBNF grammar** that makes invalid output
physically impossible at decode time. Then plain Kotlin **executes** those steps
against the real mini-app stores. The model writes the *content* (a recipe, a
checklist); code owns the dates, storage and side effects. When it needs facts it
doesn't have, it runs a tool first (web search, your calendar, your notes) and
re-plans with the real results.

So an AI-created note is indistinguishable from one you typed, it works offline,
and it can't silently no-op. It runs as a foreground service, so it keeps working
if you close the app.

14 tools today: notes, tasks, events, expenses, currency, screenshot search, web
search.
→ [`ai/`](app/src/main/java/com/kamboji/quiver/ai/README.md) ·
fine-tuning pipeline in [`training/`](training/README.md)

---

## The mini-apps

### 📸 Screenshots — *clear the clutter, keep the contents*
**Problem:** screenshots pile up by the hundreds — you use them as a disposable
clipboard, then can't delete them because occasionally you need what was *inside*
one.
**Solution:** every screenshot is auto-deleted after a delay you choose (7-day
restorable trash) — but first, **on-device OCR** reads its text and indexes it. The
picture goes; searching "wifi" still finds the password.
→ [`screenshots/`](app/src/main/java/com/kamboji/quiver/screenshots/README.md)

### 💱 Currency — *live rates, offline, plus alerts*
**Problem:** converters fail exactly when you need them — abroad, roaming off,
staring at a spinner. And watching for a rate to move means checking obsessively.
**Solution:** offline-first rates (fresh when online, last-saved and clearly
labelled when not), a historical chart, and **rate alerts** that watch in the
background and notify you on a crossing.
→ [`currency/`](app/src/main/java/com/kamboji/quiver/currency/README.md)

### 📅 Calendar — *reminders you can't ignore*
**Problem:** a notification you can swipe away on autopilot is a reminder you'll
miss.
**Solution:** a month/week/day scheduler for tasks and events whose alert can be a
full-screen **"call"** — it rings on the alarm stream until you answer, then
**reads the reminder aloud**. Plus one-line natural-language quick-add ("dentist
next friday 6pm").
→ [`calendar/`](app/src/main/java/com/kamboji/quiver/calendar/README.md)

### 📝 Notes — *Markdown that renders while you type*
**Problem:** notes apps are either too heavy (accounts, sync, ads) or too dumb
(plain text that turns a shopping list into a wall of dashes).
**Solution:** a local-only editor that styles Markdown **live**, with tappable
checkboxes, automatic list continuation, colour tags, pinning — and a mic button
so you can dictate instead of type.
→ [`notes/`](app/src/main/java/com/kamboji/quiver/notes/README.md)

### 💸 Expenses — *tracks itself*
**Problem:** manual expense tracking never lasts, and every "free" tracker wants
your bank credentials.
**Solution:** Quiver reads your **UPI/bank payment notifications on-device** and
turns them into expense entries you just confirm. Nothing is uploaded; money is
stored in paise as integers, so the math is never wrong. Month totals, per-category
bars, day-grouped list.
→ [`expenses/`](app/src/main/java/com/kamboji/quiver/expenses/README.md)

---

## Ways in — Quiver, without opening Quiver

**Problem:** capture has to happen where the thought happens. A utility you must
*open* is a utility you forget.

### 🔗 Share sheet & app shortcuts
Share any text into Quiver → save as note, add as task, log expense, or hand it to
Quiver AI. Share a gallery image → it enters the screenshot cleanup flow.
Long-press the launcher icon for **New note · New task · Add expense · Ask AI**.
→ [`share/`](app/src/main/java/com/kamboji/quiver/share/README.md)

### 🧩 Home-screen widgets
Three Glance widgets: **Agenda** (today's tasks, with checkboxes you can tick
without opening the app), **Quick actions** (note / task / expense / speak — the
mic one opens the AI panel already listening), and **Month spend**. They refresh
themselves because the stores notify them on every write.
→ [`widgets/`](app/src/main/java/com/kamboji/quiver/widgets/README.md)

### 🤖 The OS assistant (Gemini)
On Android 16+ (SDK 36), Quiver exposes `createNote`, `createTask` and
`addExpense` through **AndroidX AppFunctions**, so the system assistant can write
into Quiver. Same stores, so the item shows up in-app identically.
→ [`appfunctions/`](app/src/main/java/com/kamboji/quiver/appfunctions/README.md)

---

## Across the whole app

### 💾 Backup & export
**Problem:** everything lives only on your phone — and Android's auto-backup
silently dies on the ~720 MB AI model.
**Solution:** a versioned JSON export you own (Hub → "Your data"), whose import
**merges by content, never by id**, and reschedules restored reminders. Auto-backup
rules are scoped to `sharedpref` only, so the quota is never blown.
→ [`backup/`](app/src/main/java/com/kamboji/quiver/backup/README.md)

### 🌐 Localization
The app can run in **English, Hindi, Bengali, Tamil, Telugu and Marathi**, picked
in-app (globe icon in the Hub header) independent of the system language. Voice
dictation follows the chosen language. Onboarding, the Hub, the dock and the
mini-app titles are translated; the per-screen bodies are an in-progress,
mechanical pass. The AI is trained on all six languages — a Hindi request produces
a Hindi note and a Hindi reply.

### 👋 Onboarding
First run explains what Quiver is, asks for notification/media permissions **with
context** (rather than a cold system prompt), and offers to install the AI model.

### 🎨 The shell & design system
The whole app is **one surface**: an aurora-washed background, the active mini-app
screen, a floating dock, and overlays (search / AI / launcher / call) sharing a
single `QuiverState`. Each mini-app owns an **accent colour** that the theme,
glow and dock animate to as you move between them. The Hub is a **bento grid** you
can personalise — pin a tile and it floats to the top and renders large.
→ [`ui/`](app/src/main/java/com/kamboji/quiver/ui/README.md)

---

## Project structure

Each mini-app is a self-contained package (model + store + logic); the shell and
the `ui/` package render them.

```
app/src/main/java/com/kamboji/quiver/
├── ui/
│   ├── shell/        # QuiverActivity + QuiverApp: one surface, one state, the dock
│   ├── hub/          # bento-grid launcher
│   ├── theme/        # design tokens, per-app accents, typography
│   ├── components/   # shared glass cards, sheets, pickers, top bar, aurora
│   ├── ai/           # the Quiver AI chat panel
│   ├── onboarding/   # first-run flow
│   ├── locale/       # per-app language switching
│   └── {calendar,notes,expenses,screenshots,currency}/   # the screens
│
├── ai/               # on-device agent: engine, tools, planner, TTS, model mgmt
├── screenshots/      # auto-cleaner + OCR index & search
├── currency/         # offline-first converter + rate alerts
├── calendar/         # tasks/events, alarms, full-screen call alerts
├── notes/            # note model + store
├── expenses/         # expense model + store + UPI notification capture
│
├── widgets/          # Glance home-screen widgets
├── share/            # share-sheet router + shortcut/intent bridge
├── appfunctions/     # Gemini / OS-assistant integration
└── backup/           # export / merge-import

training/             # dataset generation, fine-tuning, eval for the bundled model
docs/ROADMAP.md       # what shipped, phase by phase, and why
```

## Build

- **JDK 21** (e.g. Android Studio's bundled JBR), Android Gradle Plugin 8.9.1,
  Gradle 8.11.1, Kotlin 2.1.0, compileSdk/targetSdk 36, minSdk 29.
- The native AI engine builds llama.cpp via CMake — the submodule is pinned:
  `git submodule update --init --recursive`
- `./gradlew :app:assembleDebug`
- `./gradlew test` for the unit tests (money math, payment parsing, date
  resolution, screenshot search ranking, backup merge, quick-add).
- Open in Android Studio and Run, or `./gradlew installDebug` to a device.

> ⚠️ `androidx.appfunctions` is pinned to **alpha08**: alpha09+ needs AGP 9.1.0+,
> and bumping AGP risks the llama.cpp CMake build. Bump the two together.

## Roadmap & history

[`docs/ROADMAP.md`](docs/ROADMAP.md) records every phase — what was built, what was
deliberately *not* built, and why.

**Next up:** retrain the bundled model on the current tool surface (the dataset
already covers `search_screenshots` and `add_rate_alert`), finish the per-screen
localization pass, and a `VoiceInteractionService` so Quiver can be driven
fully hands-free.
