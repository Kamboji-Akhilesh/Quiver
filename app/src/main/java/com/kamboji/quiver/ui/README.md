# The shell & design system

How five mini-apps and an assistant manage to feel like **one** app.

## The problem
An "app of apps" usually looks like one: each tool has its own Activity, its own
navigation, its own idea of what a card looks like. Every launch is a jarring
white flash and a different visual language. Meanwhile the assistant, the search
and the alerts all need to appear *over* whatever you're doing — which Activity
stacks make painful.

## The solution
Quiver is **one surface**. A single Activity hosts a single composable
(`shell/QuiverApp`) with a single state object (`shell/QuiverState`). Switching
mini-apps swaps a screen inside that surface — no Activity transition, no flash —
and the whole app's **accent colour animates** to the one the new mini-app owns.
Overlays (AI, search, launcher, the full-screen call) are just booleans on the
same state, so they can appear anywhere, and `BackHandler` unwinds them in order.

## The shell (`shell/`)
- **`QuiverApp`** — the surface: aurora background → active screen → floating
  dock → overlays → toast. Back unwinds overlays, then sub-screens, then returns
  to the Hub; only "Hub, nothing open" falls through and exits.
- **`QuiverState`** — *navigation state only* (durable data lives in each
  mini-app's store): which app is showing, which overlays are open, the theme, the
  toast, the pinned bento tiles, and a few **one-shot flags** (`notesStartNew`,
  `aiPrefill`, `aiStartMic`, …) that external entry points set.
- **`QuiverActivity`** — `singleTask` + `onNewIntent`, so a shortcut tap re-enters
  the running app rather than stacking a copy.
- **`ShellCommand`** — the one-shot bridge from an intent (app shortcut, share
  router, widget) into those state flags. See [`share/`](../share/README.md).
- **`Dock`** — the floating app switcher; only drawn on the Hub, so a mini-app's
  sheets are never covered by it.
- **`Overlays`** (launcher, search), **`Toast`** — the shared chrome.

## The design system (`theme/`, `components/`)
- **`theme/Tokens`** — two brightness palettes (`DarkColors` / `LightColors`) of
  neutral chrome tokens: backgrounds, translucent "glass" surfaces, borders, and
  three text weights (`text` / `dim` / `faint`).
- **`Accent`** — the saturated per-app colour: two gradient stops, a `deep` shade
  for legible text in light mode, and a `glow` for shadows. `Accents.of(AppKey)`
  maps each mini-app to its own — Hub violet→cyan, Screenshots purple, Currency
  green, Calendar blue, Notes amber, Expenses rose.
- **`QuiverTheme`** provides both through `CompositionLocal`s (`Quiver.colors`,
  `Quiver.accent`) and lays a minimal Material 3 scheme underneath so ripples and
  text-selection handles tint correctly.
- **`components/Aurora`** — the soft, drifting accent wash behind everything; it's
  what makes an accent change feel like a mood change.
- **`components/`** also holds the shared vocabulary every screen reuses:
  `Components` (glass cards, buttons), `QuiverSheet` / `ComposerSheet` (bottom
  sheets), `Pickers`, `TopBar`.

## The Hub (`hub/`)
A **bento grid** launcher: tiles of different sizes, a greeting, quick-action
chips, and the "Your data" (backup) section. Enter edit mode and **pin** a tile —
pinned tiles sort to the top *and* render at the large featured size; unpinned
ones stay small and pair into rows. Persisted via `hub/HubPrefs`.

## Localization (`locale/`)
`AppLocale` applies a per-app language by wrapping the `Configuration` in
`attachBaseContext` — this works on a plain `ComponentActivity` at every API level
(chosen over `AppCompatDelegate`, since these activities aren't AppCompat). The
in-app picker (globe in the Hub header) persists the choice and calls `recreate()`.
Switching back to *System* resets `Locale.getDefault()` from the base config.

## Onboarding (`onboarding/`)
`OnboardingOverlay` covers the shell on first run only: what Quiver is →
notification/media permissions asked **with context** → an offer to install the AI
model. It's the *only* thing shown on first launch; the routine permission and
settings prompts are deferred to later launches.

## Screens
`calendar/`, `notes/`, `expenses/`, `screenshots/`, `currency/` and `ai/` hold the
Compose UI for each mini-app. The data, logic and background work for each live in
its own top-level package (e.g. `com.kamboji.quiver.expenses`) — the split keeps
stores usable from the agent, widgets, share router and workers without dragging
UI along.
