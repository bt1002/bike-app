# CLAUDE.md

Android app (Kotlin + Jetpack Compose) that controls a Merach S29 Aura bike over Bluetooth LE using FTMS.
Read `MerachRide/HANDOFF.md` first. It has the current status, the discovery log and the next steps.

## Constraints

- **Build setup (since 2026-11-14):** Android Studio is installed on the primary dev PC, and the user now has
  admin rights there. Gradle sync needs **Gradle JDK 23** (Settings → Build → Build Tools → Gradle).
  The agent can build, run unit tests (`:app:testDebugUnitTest`) and install on the user's phone (paired over
  Wireless debugging) through the Android Studio tools. adb is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - Don't claim code works until it has actually been built or tested. Otherwise say it's unverified.
  - Edits to `build.gradle.kts` from the agent tend to time out (and may land later). Avoid new dependencies
    where you can; after a build file change, run a Gradle sync before deploying.
  - The phone's logcat buffer rolls over quickly. Read the app's lines soon after a ride:
    `adb logcat -d -v time -s MerachRide:*`.
  - The user's phone is a Pixel 11 Pro, already paired. If `adb devices` doesn't list it, `adb mdns services`
    shows its address and `adb connect <ip:port>` reconnects it (no new pairing). The `deploy` tool installs
    on whichever device is selected in Studio (often the emulator); to target the phone explicitly, build
    `:app:assembleDebug` and `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
  - There's also an emulator (Pixel 10). It can't reach the real bike or watch; use the simulated bike.
  - Long-running or network shell commands (e.g. `git push`) sometimes time out in the foreground; run
    them with `runInBackground` and wait on the session.
  - Don't upgrade the pins casually: AGP 8.7.3, Gradle 8.10.2, Kotlin 2.0.21, Compose BOM 2024.12.01.
  - Check Kotlin precedence carefully: `a and b == c` parses as `a and (b == c)`. Parenthesize bit masks.
  - Ask before installing system-wide software. Use the built-in tools (PowerShell plus .NET
    `System.Drawing`) for image work.
- Bike behaviour is only "verified" once the user has tested it on the real bike. Mark everything else
  as unverified in code comments and in `MerachRide/HANDOFF.md`.

## Verified bike protocol (do not change without new evidence)

- Device name `MRK-S29C-307F`, FTMS service `0x1826`.
- Control Point `0x2AD9` (Write + Indicate): enable indications, then `00` (request control) and `07` (start).
  Set level N with `04 <N*10>` as a uint8 (level 8 = `04 50`, level 16 = `04 A0`).
- Range is levels 1–16. Resistance target only; no power/ERG target.
- Proprietary service `59554c55-…-4d4552414348` ("MERACH") is a fallback that hasn't been explored.
  **Never write to `02f00000-…-fe00`**, which is probably firmware update (OTA).
- Indoor Bike Data (`0x2AD2`) reports resistance in **whole levels** (level 14 = raw `14`), verified on the
  bike 2026-11-14. It also reports changes made with the bike's own buttons, which the app follows via
  `protocol/ManualChangeDetector`. A level sent while the rider has stopped pedalling can be undone by the bike
  when they resume (seen 2026-11-16, issue #1); `ManualChangeDetector.shouldResend` makes the app send it again. `protocol/ResistanceScale` still auto-detects the unit as a safeguard.

## Code layout (`MerachRide/app/src/main/java/app/merachride/`)

- `protocol/`: pure Kotlin with no Android imports, unit-tested. FTMS bytes, data parser, `BikeController`,
  `ResistanceScale`, `ManualChangeDetector`.
- `ble/BleConnection.kt`: one GATT connection with a serialized operation queue (one op in flight).
- `BikeSession.kt`: app-wide singleton. Handles scan, connect, the FTMS handshake, auto-reconnect,
  resistance commands and the in-app log.
- `HeartRateMonitor.kt`: optional second BLE link to a standard heart rate monitor (`0x180D`), e.g. the user's
  Pixel Watch via Connected Fitness (verified with the user's Pixel Watch 2, 2026-11-15). Its bpm overrides
  the bike's in the UI and in recorded rides.
  Parser in `protocol/HeartRate.kt`. Reconnects to the saved monitor in the background while the bike is
  connected (`startAutoConnect`, waits until the watch shares; unverified). A device picked by hand gives
  up after 3 tries. The Ride tab's Watch light is amber while waiting (`autoWaiting`).
- `WatchLink.kt` + `wear/` module: the MerachRide watch app reads heart rate with Health Services and sends
  it over the Wear OS Data Layer; the phone opens it with `RemoteActivityHelper` when the bike connects.
  Message format in `protocol/WatchProtocol.kt`, copied in `wear/.../WatchProtocol.kt` (keep in sync). Both
  apps share applicationId `app.merachride`. Builds (2026-11-16); not yet run on the watch. `wear/` depends on
  full Guava for `ListenableFuture`: the standalone `listenablefuture:1.0` artifact resolves to an empty jar.
- `workout/`: model (segments hold a level or ramp via `endLevel`), engine (1 s ticker on the Main dispatcher;
  sends a level whenever `Workout.adjustedLevelAt(t, levelOffset)` changes). `WorkoutState.levelOffset` is the
  rider's change for the rest of the workout, set two ways: `followRiderLevel` (changing the level mid-workout
  with the app's −/+ or the bike's buttons) and `bumpLevels` (the workout card's −/+, also while paused);
  `resetLevelOffset` (tap the offset) undoes it. Also built-in workouts, editor draft (`WorkoutDraft`),
  `WorkoutCodec` (`workouts.json`) and `WorkoutImport` (merge an imported file by id).
- `history/`: `RideRecorder` (pure: ride stats from 1 Hz samples, handles bike counter restarts),
  `RideTracker` (ride start/finish/save, Main thread), `RideCodec` (`rides.json`) and `PaceTarget`
  (speed/HR target from the last 5 rides of the same workout, matched by `Workout.key`).
- `data/`: `MiniJson` (dependency-free JSON, so save formats are JVM-testable) and `JsonFileStore`
  (StateFlow list saved with `AtomicFile`).
- `report/IssueReport.kt`: pure builder for the "Report issue" link (GitHub new-issue URL with description,
  details and the log, trimmed to fit ~7.5 KB). UI in `ui/ReportIssueDialog.kt`, opened from the log sheet.
  No GitHub token in the app: the user submits in the browser.
- `sim/`: **simulated bike for testing without hardware (debug builds only).** `BikeSimulator` (pure, tested)
  takes level commands and emits real-format `0x2AD2` packets that go through `BikeSession.onBikeData`;
  `TimeScale` speeds up the workout timer and ride recording (always 1 with the real bike).
  `BikeSession.connectSimulated()` plus `ui/SimulatorPanel` (🔧 icon). Use it in the emulator to test UI and
  logic; its power/speed/kcal formulas are made up, and it proves nothing about the real Bluetooth link.
- `MerachRideApp.kt`: creates the app-wide singletons (session, workout store, ride history, ride tracker).
- `service/RideService.kt`: foreground service that keeps the process alive during a ride.
- `ui/`: Compose screens. `MainScreen` has Ride / Workouts / History tabs (no navigation library).
  Display units are **imperial** (mph, miles) via `ui/Units.kt`; everything stored or calculated stays metric.
  The Ride tab is tight in portrait: when adding to it, check on the Pixel 10 emulator that the workout
  card's profile chart and Start/Skip/End buttons still fit.
- Tests are in `MerachRide/app/src/test/`. Keep new logic in pure Kotlin classes so it stays testable.
- App icon: generated by `tools/icon/make_icon.py` (repo root) into both modules' `res/` (adaptive icon with
  monochrome layer, plus the `ic_stat_merachride` notification icon) and `branding/logo.svg`. Edit the script and
  re-run it rather than editing the generated XML.
- `tools/workout-builder.html` (repo root, outside the Android project): single-page web workout builder.
  It mirrors `Segment.levelAt`, the built-in workouts and the `workouts.json` format. **If you change any of
  those in Kotlin, update the builder to match.**

## Working conventions

- After meaningful progress, add a dated line to the **Log** in `MerachRide/HANDOFF.md` and update **Status**.
- Git: the remote is `https://bt1002@github.com/bt1002/bike-app.git` (the username in the URL makes Git
  sign in as the personal account). Commit as `bt1002 <bradtrammell@gmail.com>` (set in the repo's local
  config). Branch `main`.
- `screenshots/Screenshot_20261114-174353.png` is git-ignored on purpose because it shows other people's
  nearby devices. Don't commit scans that list third-party devices.
