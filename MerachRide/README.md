# Merach Ride

Android app (Kotlin + Jetpack Compose) that connects to a **Merach S29 Aura** bike over Bluetooth LE
using the standard FTMS protocol. It shows live stats, runs structured workouts by setting the bike's
resistance (levels 1–16) automatically, and keeps a history of your rides.

**Status (2026-11-15):** builds in Android Studio, 69 unit tests pass, and it runs on a phone. Connecting,
setting levels from the app and following the bike's own buttons are verified on the bike. The newer
features (ride layout, custom workouts, ramps, history) are installed but not yet tried on a ride.

## Build and install

1. Open this `MerachRide` folder (the one containing `settings.gradle.kts`) in **Android Studio**.
2. Wait for Gradle sync. The first time, it downloads Gradle 8.10.2, AGP 8.7.3 and the libraries.
   - If sync fails with *"Unsupported class file major version"*, go to Settings → Build → Build Tools →
     Gradle and set **Gradle JDK** to a newer version. JDK 23 works.
   - The `gradlew` scripts aren't included; Android Studio doesn't need them.
3. Connect the phone. **Wireless debugging** needs no USB drivers (Android 11 or later):
   - On the phone: Settings → About phone → tap **Build number** 7 times, then Developer options →
     **Wireless debugging** on.
   - In Studio: device dropdown (right of the `app` dropdown) → **Pair Devices Using Wi-Fi**, then on the
     phone tap **Pair device with QR code** and scan.
4. Press **Run ▶** with the `app` configuration.

Unit tests: right-click `app/src/test` → **Run Tests**, or Gradle task `app:testDebugUnitTest`.

## Using it

1. Fully close the Merach app (the bike accepts one controller at a time).
2. Grant the *Nearby devices* permission when asked.
3. **Ride** tab → **Scan for bikes** → tap `MRK-S29C-307F`. Next time, use the **Connect to …** button.
4. Set the level with − / +, or with the bike's own buttons (the app follows them).
5. **Workouts** tab: tap a workout to load it, then press **Start** on the Ride tab.
   - **New** creates your own workout; **Copy** turns a built-in one into an editable copy.
   - Each segment has a time, a level, and optionally a **ramp** to another level, which the app steps
     through one level at a time.
   - **Import** / **Export** load and save `workouts.json`, so you can build workouts on a computer with
     the web builder (`tools/workout-builder.html` in the repo) and copy the file to the phone. Import
     replaces workouts with the same id and adds the rest.
6. A ride is recorded automatically from your first pedal stroke. Tap **Finish ride** (or disconnect) to
   save it to the **History** tab. Rides under a minute aren't saved.
7. The ⓘ icon at the top of the Ride tab opens the connection log. Check it first if something misbehaves.

The connection stays up with the screen off (via a foreground notification) and reconnects automatically
if the link drops, re-applying the last level. A running workout pauses while the link is down and resumes
after reconnecting.

Built-in workouts: Quick Test (2 min, levels 2 → 8 → 16 → 1), Easy Spin, 1:1 Intervals, Hill Pyramid, and
8 / 12 Ramps (30 min, ramping 8 → 12 → 8 every 5 minutes).

## Bike protocol (verified 2026-11-14)

| Step | Write to `0x2AD9` | Reply (indication) |
|---|---|---|
| Request control | `00` | `80 00 01` |
| Start / resume | `07` | `80 07 01` |
| Set level N (1–16) | `04 <N×10>`, e.g. `04 50` = level 8 | `80 04 01` |

- Control: **resistance level only**. The bike doesn't accept target power, so there's no native ERG mode.
- Data (`0x2AD2`): speed, cadence, distance, resistance, power, energy, heart rate, elapsed time.
  Resistance is reported in **whole levels** (level 14 = `14`), including changes made with the bike's buttons.
- Range (`0x2AD6`): 1.0–16.0, step 1.0.

## Code map (`app/src/main/java/app/merachride/`)

| Path | What |
|---|---|
| `protocol/` | Pure Kotlin: FTMS command bytes, live data parser, `ResistanceScale`, `ManualChangeDetector` |
| `ble/BleConnection.kt` | GATT connection, serialized operation queue, notifications |
| `BikeSession.kt` | Scan, connect + FTMS handshake, auto-reconnect, set level, connection log |
| `workout/` | Workout model (segments, ramps), engine (1 s ticker), built-in workouts, editor draft, JSON codec |
| `history/` | `RideRecorder` (ride stats), `RideTracker` (start/finish/save), JSON codec |
| `data/` | `MiniJson` (small JSON reader/writer) and `JsonFileStore` (list saved to a file) |
| `service/RideService.kt` | Foreground service that keeps the process alive during a ride |
| `ui/` | Compose screens: `MainScreen` (tabs), `RideScreen`, `WorkoutsScreen` (list + editor), `HistoryScreen` |

Custom workouts and rides are saved in the app's private storage as `workouts.json` and `rides.json`.
Reinstalling from Android Studio keeps them; uninstalling deletes them.

## Not yet verified on the real bike

- Reconnecting after the bike goes to sleep, and resuming the workout.
- The new UI, ramp workouts, and whether History's distance and kcal match the bike's display.
- Importing a file from the web builder on the phone (the file format is unit-tested).
- One unexplained jump to level 16 during the last segment of Quick Test (seen once; parked).
