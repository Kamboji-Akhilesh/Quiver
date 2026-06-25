# 🏹 Quiver

### *Your everyday utilities, in one quiver*

Quiver is a personal **app aggregator** for Android — a single native app
(Jetpack Compose) whose home is a **bento grid** of small, focused mini-apps.
Instead of a dozen separate installs, your handy tools live together and launch
from one place.

It started life as a screenshot cleaner and is growing into a hub. The long-term
aim is a hands-free, on-device assistant that can drive these tools by voice.

---

## The mini-apps

### 📸 Screenshots — *clear the clutter*
Auto-deletes screenshots after a delay you choose, with a 7-day restorable
trash. Take a screenshot → get a "Will delete in X" notification → ignore it
(auto-deletes), cancel (keep), or edit the time.
→ [`screenshots/`](app/src/main/java/com/kamboji/quiver/screenshots/README.md)

### 💱 Currency — *live & offline rates*
An offline-first currency converter and rate list (Frankfurter API, no key/
backend). Fetches live when online, falls back to the last saved rates when
offline.
→ [`currency/`](app/src/main/java/com/kamboji/quiver/currency/README.md)

### 📅 Calendar — *tasks, events & call alerts*
A Google-Calendar-style scheduler (month / week / day) for tasks and events.
Each alert can be a normal notification or a full-screen **"call"** that rings
until you answer, then reads the item aloud.
→ [`calendar/`](app/src/main/java/com/kamboji/quiver/calendar/README.md)

---

## Project structure

Each mini-app is a self-contained package; the hub ties them together.

```
app/src/main/java/com/kamboji/quiver/
├── hub/          # bento-grid launcher (HubActivity) + Compose theme
├── screenshots/  # screenshot auto-cleaner (service, worker, ui, data, …)
├── currency/     # offline-first converter (ui, viewmodel, data/repository)
└── calendar/     # tasks/events + notification & call alerts (ui, alert/, data/)
```

## Build

- **JDK 21** (e.g. Android Studio's bundled JBR), Android Gradle Plugin 8.9.1,
  Gradle 8.11.1, Kotlin 2.1.0, compileSdk 36, minSdk 29.
- `./gradlew :app:assembleDebug`
- Open in Android Studio and Run, or `./gradlew installDebug` to a device.

## Roadmap

- Spoken-reply (speech-to-text) + on-device LLM for the calendar call alert.
- Currency historical-rate chart.
- An on-device assistant layer (e.g. `VoiceInteractionService`) so the mini-apps
  can be driven hands-free.
