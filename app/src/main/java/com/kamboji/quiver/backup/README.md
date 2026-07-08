# Backup & export

Your data, in a file you own.

## The problem
Everything in Quiver lives only on your phone — that's the point. But it also
means a lost or wiped phone loses your notes, calendar and spending history. And
Android's own auto-backup silently *fails* if the app's data exceeds a 25 MB
quota, which Quiver's ~530 MB downloaded AI model blows through instantly.

## The solution
Two independent safety nets:

### 1. Manual export / import (`QuiverBackup`)
A versioned JSON envelope that reuses each model's existing `toJson`/`fromJson`.
Wired into the Hub's **"Your data"** section via `CreateDocument` /
`OpenDocument`, so the backup lands wherever you choose (Drive, USB, anywhere).

Import **merges**, it doesn't overwrite:
- Items are matched **by content, never by id** — id sequences differ across
  installs, so matching on them would corrupt or duplicate data.
- Newcomers get fresh ids from the receiving store.
- Imported reminders are **rescheduled**, so a restored task still rings.

The pure merge logic is unit-tested.

### 2. Android auto-backup rules
`res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` include
**only `sharedpref`** — where all three stores and the settings live. Deliberately
excluded:
- `files/llm-models` — the ~530 MB `.task` model bundle would break the 25 MB
  quota and kill backup entirely. It's re-downloadable with one tap.
- The screenshot history/OCR database — device-specific (it references
  `MediaStore` ids that mean nothing on another phone).

## Key files
- `QuiverBackup.kt` — envelope, export, content-matched merge import
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`
- Hub → "Your data" (`ui/hub/HubScreen.kt`) — the export/import buttons
