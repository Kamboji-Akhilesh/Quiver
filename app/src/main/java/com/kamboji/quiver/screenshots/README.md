# Screenshots mini-app

Auto-cleans screenshot clutter — and, before deleting anything, **reads the text
inside each screenshot** so you can still search for it afterwards.

## The problem
Screenshots pile up by the hundreds. You take them as a *disposable clipboard* —
a wifi password, an address, an OTP, a receipt — and then never clean them up,
so the gallery becomes unusable. But you can't just delete them either, because
occasionally you need what was *in* one.

## The solution
Two halves that make each other safe:
1. **Automatic cleanup.** Every new screenshot is scheduled for deletion after a
   delay you choose, with a 7-day restorable trash. You do nothing; it stays clean.
2. **Text survives the image.** At capture time, on-device OCR reads the text out
   of the screenshot and indexes it. So when the picture is long gone, searching
   "wifi" still finds the password.

## How it works
### Cleanup
1. `service/ScreenshotService` runs as a foreground service and registers
   `service/ScreenshotObserver` on `MediaStore` to detect new screenshots
   (deduped by media `_ID` so rapid captures aren't missed).
2. `notification/ScreenshotNotification` posts a **"Will delete in X"**
   notification (Cancel / Edit time) and schedules `worker/DeleteWorker`.
   Ignore it → it goes. Cancel → it stays. Edit → pick a new delay.
3. On fire, `util/TrashManager` moves the file to an app-private trash and logs
   it in Room (`data/db`) so it can be restored. `worker/CleanupWorker` prunes
   trash older than 7 days, daily.
4. `receiver/BootReceiver` restarts monitoring on boot — unless you'd paused it.

### OCR + search
- **On-device OCR** (`search/ScreenshotOcr`) — bundled ML Kit `text-recognition`
  (Latin) **and** `text-recognition-devanagari`. The models ship inside the APK:
  no Play Services, no network, nothing uploaded. Both scripts are run and their
  text merged.
- **When** — at *detection* time, from `ScreenshotNotification.handleScreenshot`
  (the image is guaranteed to still exist then), via a fire-and-forget
  `search/OcrIndexer`. That's what makes the text outlive the auto-clean.
- **Index** — a `screenshot_text` Room table keyed by the MediaStore `_ID`
  (`AppDatabase` v2 + an additive `MIGRATION_1_2`; no destructive fallback, so
  trash history is preserved). It's deliberately **independent of the deletion
  history**: the text stays searchable after the file is gone.
- **Search** — `search/ScreenshotSearch` is a pure, unit-tested helper (tokenize →
  DAO `LIKE` prefilter → multi-token ranking + snippet). The Screenshots screen
  has a search sub-screen with live results that open the image — or explain that
  it's been cleaned but the text was kept.
- **Backfill** — `worker/OcrBackfillWorker` (a `CoroutineWorker`, `requiresCharging`,
  unique `KEEP` work, interruptible) indexes screenshots you already had. Kicked
  off from an "Index older screenshots" card.

## Key files
- `service/` — foreground service + media observer
- `notification/` — capture notification + actions
- `worker/` — `DeleteWorker`, `CleanupWorker` (trash prune), `OcrBackfillWorker`
- `util/TrashManager` — soft-delete + restore
- `search/` — `ScreenshotOcr`, `OcrIndexer`, `ScreenshotSearch`
- `data/` — `SettingsManager` (delay, theme, enabled) + Room DB (history + text)
- `ui/screenshots/ScreenshotsScreen.kt` — home, history, search, preview

## Permissions
`READ_MEDIA_IMAGES`, `MANAGE_EXTERNAL_STORAGE` (to delete others' media silently),
`FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.

> `MANAGE_EXTERNAL_STORAGE` is restricted on Google Play — fine for
> personal/sideloaded use.

## How other parts use it
- **Share sheet** — sharing a gallery image schedules it for cleanup the same way.
- **Quiver AI** — `search_screenshots` (searches the OCR text and answers from it)
  and `check_trash`.
