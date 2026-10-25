# Share sheet & entry points

Ways into Quiver that don't involve opening Quiver.

## The problem
Capture has to happen where the thought happens — in a browser, a chat, a
receipt. If saving something means switching apps, finding the right mini-app and
retyping, you just… don't.

## The solution
Quiver shows up in Android's **share sheet**, in the launcher's **long-press
shortcuts**, and on the **home screen** — each one writing straight into the
right store and then landing you on the right screen.

## Share sheet (`ShareActivity`)
Registered for `ACTION_SEND`.

- **Shared text** → a Quiver-styled bottom card offering four destinations:
  - *Save as note* — `SharedText.noteSplit` splits a title off the first line.
  - *Add as task* — parsed by `QuickAddParser` + `WhenResolver`; if the text has
    no "when", it defaults to **tomorrow morning** so the reminder can still fire.
  - *Log expense* — needs a currency marker (₹ / Rs / INR / rupees). The category
    is guessed, so the entry lands as `needsReview`. Text with no amount opens the
    add sheet instead of silently guessing.
  - *Ask Quiver AI* — hands the text to the on-device assistant, pre-filled.
  - Browsers often put the page title in `EXTRA_SUBJECT` and only the URL in
    `EXTRA_TEXT` — both are kept.
- **Shared image** → straight into the screenshot cleanup flow. Only
  `MediaStore` images are accepted: URIs from cloud/messaging providers can't be
  deleted later, so scheduling a deletion for them would be a lie.

Writes happen in the router (the stores are synchronous), then the shell is
opened with a one-shot [`ShellCommand`](../ui/shell/ShellCommand.kt).

## App shortcuts (`res/xml/shortcuts.xml`)
Long-press the launcher icon: **New note · New task · Add expense · Ask AI**.
Each fires an action-coded intent into `QuiverActivity` (which is `singleTask` +
`onNewIntent`), mapped by `ShellCommand.fromIntent` onto the shell's one-shot
flags. "Ask AI" supports a text prefill — that's how share-to-AI works.

## ShellCommand — the one-shot bridge
A single data class carrying "open this screen / open its composer / open the AI
panel (maybe listening) / show this toast". Every external entry point produces
one; the shell applies it exactly once and clears it. The `nonce` makes two
identical commands distinct so the shell re-applies both.

## Also see
- **Home-screen widgets** → [`widgets/`](../widgets/README.md)
- **Calendar natural-language quick-add** → [`calendar/`](../calendar/README.md)
- **OS assistant integration (Gemini)** → [`appfunctions/`](../appfunctions/README.md)

## Key files
- `ShareActivity.kt` — the `ACTION_SEND` router + chooser card
- `SharedText.kt` — title split, one-lining, amount extraction
- `../ui/shell/ShellCommand.kt` — the intent → shell-state bridge
