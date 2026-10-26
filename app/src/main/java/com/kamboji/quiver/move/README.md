# Move — run / walk / ride / hike tracker

A private Strava-style recorder: routes never leave the phone.

## The problem
Strava and Google Fit upload every route you record — where you live, your
routine, when you're out. Location is about the most sensitive data a phone
produces.

## The solution
Move records on-device with the same location engine Strava/Google Fit use
(Google fused location, 1 Hz), so its numbers land close to theirs, and keeps
everything local. Only the map background is fetched (OpenStreetMap tiles,
while viewing). GPX export lets you hand any activity to Strava yourself.

## How it works
- `MoveService` — a location-type foreground service (keeps recording with the
  screen off; Pause / Finish in its notification). Reads the step-counter chip
  (Walk/Run/Hike) and the barometer when present (far better climbs than GPS
  altitude). Raw points are flushed to Room in batches of 15.
- `TrackMath.kt` — the accuracy core, pure Kotlin and unit-tested against
  simulated GPS (correlated drift, harsh noise, outliers, gaps, corners):
  - drop fixes worse than 25 m; reject superhuman jumps — but re-seed if
    several "jumps" agree (a stale cached first fix otherwise locks the track);
  - constant-velocity Kalman filter (no lag on straights, little on corners);
  - distance in ~15–20 m steps with Douglas–Peucker corner splitting, so noise
    isn't counted as distance and street corners aren't cut;
  - the chip's Doppler speed decides "stopped" — unless it's stuck at 0 or
    contradicts clearly-advancing positions (the emulator does both);
  - the last partial step is counted on Finish/Pause.
- `TrackTuning` + the per-activity fields on `MoveType` hold every tuning
  value. They were chosen by sweeps over route shapes; change them only after
  comparing with real side-by-side recordings.
- Raw points are kept (not just totals), so history can be recomputed after
  recalibration (`MoveSummary.replay`). `MoveRecovery` closes activities the
  OS killed mid-recording, from their saved points.

## All-day steps (`steps/`)
Like Google Fit, without an always-on service: the step-counter chip counts in
hardware, so `Steps.sample` reads it every 15 min (WorkManager), on app open
and at boot, and files the difference per local hour (`step_hours`; days,
weeks and months are sums). Reboots are detected via the boot count; steps are
spread by time across hour boundaries (`StepMath`, unit-tested). Recorded
activities read the same chip, so their steps are already inside the day's
total. `AutoDetect` logs walks/rides of 10+ min and runs of 5+ min from Play
services' on-device activity recognition — time and steps, no GPS route.
Screens: home card (today's ring, 7-day goal rings, weekly active-minutes
target), My activity (Day/Week/Month chart + that period's activities).

## Personal records
`BestEfforts`: the fastest stretch of each standard distance anywhere inside
an activity (Strava's run distances: 400 m … marathon; 1–20 km walk/hike;
5–100 km ride), stored per activity (`moves.bests`) and backfilled for older
ones. A detail screen marks efforts that were a PR when set ("was 25:03"); the
replay announces them at the point on the route where they happened.

## Map
Pace-coloured route (`PaceColours`: smoothed speed, 10th–90th percentile
scale), replay (~20 s, follows a dot with distance/time), live trail while
recording, and a personal heatmap of every route (from stored thumbnails).
Spoken km splits use the phone's own text-to-speech.

## Accuracy
Simulated: ~1% average distance error across route shapes, up to ~2% on long
noisy straights (GPS error wanders, so every tracker's path snakes a little).
Emulator end-to-end, a 1.000 km square: 992 m, moving time +3 s. Steps match
the phone's step chip exactly; calories are an estimate (every app's differs).

**Calibrate on your phone:** record the same activity in Move and Strava at
the same time, or upload Move's GPX to Strava and compare.

## Backup
Activities and their raw points are in the encrypted Hub backup (merged by type
+ start time on import). They're not in Android auto-backup: `move.db` grows
with every recording and would eventually blow its 25 MB quota for the whole app.

## Not yet
No home-screen widget yet.
