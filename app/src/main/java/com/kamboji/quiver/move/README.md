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
