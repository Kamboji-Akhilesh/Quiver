# Notes mini-app

A fast, private notepad with just enough Markdown to make lists and headings
look right — and a voice button so you can talk instead of type.

## The problem
Most notes apps are either too heavy (accounts, sync, ads) or too dumb (plain
text that turns a shopping list into a wall of dashes). You want to jot a
thought, tick items off a checklist, and read it back cleanly — offline, with
nothing leaving the phone.

## The solution
A local-only editor that renders a **useful subset of Markdown live** (headings,
**bold**, *italic*, `code`, ~~strike~~, and `- [ ] ` checkboxes you can tap),
continues lists for you as you type, and lets you **dictate** with the mic. Notes
are pinnable, colour-tagged, and searchable — and Quiver AI can read and write
them for you (see [`ai/`](../ai/README.md)).

## How it works
- `data/Note` + `data/NotesStore` — the note model and a JSON-in-SharedPreferences
  store (the same pattern every mini-app uses). Each note carries a title, body,
  colour tag, pinned flag and created/updated timestamps.
- `NotesViewModel` — CRUD, pin/unpin, colour, and "most recently updated first"
  ordering.
- `ui/notes/NotesScreen` — the list (pinned notes float to the top) and the
  full-screen editor.
- `ui/notes/Markdown` — a `VisualTransformation` that styles the raw text *in
  place* as you type: heading sizes, inline emphasis, and checkbox glyphs. No
  syntax is hidden from you; it just stops looking like syntax. Pressing Enter
  inside a `-` / `1.` / `- [ ]` line **continues the list**; an empty bullet ends it.
- **Preview vs. edit** — an existing note **opens in preview** (rendered, with
  tappable checkboxes that flip `- [ ] ` ⇄ `- [x] ` in the underlying text); a new
  note opens straight into the editor. The eye/pencil button toggles between them.
- `ui/notes/NotePalette` — the note colour swatches.
- **Voice notes** — the mic button in the editor toolbar reuses the shared
  [`VoiceController`](../ai/README.md) (platform speech-to-text, nothing is
  recorded to disk); the transcript is inserted at the cursor. Dictation language
  follows the app's chosen UI language.

## Key files
- `data/` — `Note` model + `NotesStore`
- `NotesViewModel.kt` — state + CRUD
- `ui/notes/NotesScreen.kt` — list + editor
- `ui/notes/Markdown.kt` — live Markdown rendering + checkbox toggling
- `ui/notes/NotePalette.kt` — colour tags

## How other parts use it
- **Share sheet** → "Save as note" writes here (see [`share/`](../share/README.md)).
- **App shortcut / widget** "New note" opens the editor straight away.
- **Quiver AI** `add_note` / `append_note` / `read_note` tools write and read
  the exact same store.
