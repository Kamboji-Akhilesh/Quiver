# Screenshots mini-app

Auto-cleans screenshot clutter: when you take a screenshot, it's scheduled for
deletion after a configurable delay (with a 7-day restorable trash), so you
never manually clear them again.

## How it works
1. `service/ScreenshotService` runs as a foreground service and registers
   `service/ScreenshotObserver` on `MediaStore` to detect new screenshots
   (deduped by media `_ID` so rapid captures aren't missed).
2. `notification/ScreenshotNotification` posts a "Will delete in X" notification
   (Cancel / Edit Time) and schedules `worker/DeleteWorker`.
3. On fire, `util/TrashManager` moves the file to an app-private trash and logs
   it in Room (`data/db`) for restore; `worker/CleanupWorker` prunes trash older
   than 7 days on a daily schedule.
4. `receiver/BootReceiver` restarts monitoring on boot — only if it wasn't paused.

## Key files
- `service/` — foreground service + media observer
- `notification/` — capture notification + actions
- `worker/` — `DeleteWorker` (scheduled delete), `CleanupWorker` (trash prune)
- `util/TrashManager` — soft-delete + restore
- `data/` — `SettingsManager` (delay, theme, enabled) + Room history DB
- `ui/` — main screen, history, image preview, delete-confirm, edit-delay

## Permissions
`READ_MEDIA_IMAGES`, `MANAGE_EXTERNAL_STORAGE` (to delete others' media silently),
`FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.

> Note: `MANAGE_EXTERNAL_STORAGE` is restricted on Google Play — fine for
> personal/sideloaded use.
