# Merach Ride

Android app (Kotlin + Jetpack Compose) that connects to a **Merach S29 Aura** bike over Bluetooth LE
using the standard FTMS protocol. It shows live stats, runs structured workouts by setting the bike's
resistance (levels 1–16) automatically, reads heart rate from a Pixel Watch, and keeps a history of
your rides with pace targets.

**Status (2026-11-15):** builds in Android Studio, 92 unit tests pass, and it runs on a phone. Verified on
real hardware: connecting and setting levels, following the bike's own buttons, Pixel Watch 2 heart rate,
and importing workouts from the web builder. The rest (ramps, pace targets, history numbers, status
lights, level offset) has been checked in the emulator with the simulated bike but not yet on a ride.

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
   - Pairing is a one-time step. Later, turn Wireless debugging back on and the phone reappears; if not,
     `adb mdns services` shows its address and `adb connect <address>` reconnects it.
4. Pick the phone (or an emulator) in the device dropdown and press **Run ▶** with the `app` configuration.

Unit tests: right-click `app/src/test` → **Run Tests**, or Gradle task `app:testDebugUnitTest`.

## Using it

1. Fully close the Merach app (the bike accepts one controller at a time).
2. Grant the *Nearby devices* permission when asked.
3. **Ride** tab → **Scan for bikes** → tap `MRK-S29C-307F`. Next time, one **Connect to …** button
   connects the bike and your last heart rate monitor (the watch) together.
4. The **● Bike** and **● Watch** lights at the top show what's connected: green connected, amber
   connecting, red when the bike is connected but the watch isn't (a warning line explains what to do).
5. Set the level with − / +, or with the bike's own buttons (the app follows them).
6. **Workouts** tab: tap a workout to load it, then press **Start** on the Ride tab.
   - **New** creates your own workout; **Copy** turns a built-in one into an editable copy.
   - Each segment has a time, a level, and optionally a **ramp** to another level, which the app steps
     through one level at a time.
   - **Import** / **Export** load and save `workouts.json`, so you can build workouts on a computer with
     the web builder (`tools/workout-builder.html` in the repo) and copy the file to the phone. Import
     replaces workouts with the same id and adds the rest.
7. During a workout, the card shows the segment countdown, what's next and the profile chart.
   **− ±0 +** in its title row makes the rest of the workout a level easier or harder per tap.
8. The pace panel shows speed (mph) and heart rate: now, this ride's average, and a **target** from your
   last 5 rides of the same workout. After 30 s, a green ▲ means you're ahead of your average pace and a
   red ▼ means pick it up. The first ride of a workout sets its target.
9. A ride is recorded automatically from your first pedal stroke. Tap **Finish ride** (or disconnect) to
   save it to the **History** tab. Rides under a minute aren't saved. History has a 🗑 on each ride and a
   **Reset history** button.
10. Heart rate: on a **Pixel Watch**, turn on quick settings → **Connected Fitness** (or Settings →
    Connectivity → Share Heart Rate). After Connect, the app keeps looking for the watch for 3 minutes, so
    you can turn sharing on afterwards. To set up a watch or chest strap the first time, tap **Watch** at
    the top of the Ride tab, then scan or pick it under "Paired with this phone".
11. The ⓘ icon at the top of the Ride tab opens the connection log. Check it first if something misbehaves.

The connection stays up with the screen off (via a foreground notification) and reconnects automatically
if the link drops, re-applying the last level. A running workout pauses while the link is down and resumes
after reconnecting.

Built-in workouts: Quick Test (2 min, levels 2 → 8 → 16 → 1), Easy Spin, 1:1 Intervals, Hill Pyramid,
8 / 12 Ramps (30 min, 8 → 12 → 8 every 5 minutes) and Stairs (30 min, 6 → 12 → 6 every 5 minutes).

## Testing without the bike

Debug builds have a **simulated bike**, handy in the emulator (which can't reach real Bluetooth devices):

1. Ride tab → **Connect to simulated bike (debug)**.
2. The 🔧 icon opens its controls: the bike's own +/− buttons, pedalling on/off, cadence, **time speed**
   (1/5/10/30× for the workout timer and ride recording), drop the connection, and reset the bike's counters.

It sends data in the same format as the real bike, so workouts, ride recording, pace targets and reconnects
all run through the normal code. Its power, speed and kcal numbers are made up, and it proves nothing about
the real Bluetooth link.

## Bike protocol (verified 2026-11-14)

| Step | Write to `0x2AD9` | Reply (indication) |
|---|---|---|
| Request control | `00` | `80 00 01` |
| Start / resume | `07` | `80 07 01` |
| Set level N (1–16) | `04 <N×10>`, e.g. `04 50` = level 8 | `80 04 01` |

- Control: **resistance level only**. The bike doesn't accept target power, so there's no native ERG mode.
- Data (`0x2AD2`): speed, cadence, distance, resistance, power, energy, heart rate, elapsed time.
  Resistance is reported in **whole levels** (level 14 = `14`), including changes made with the bike's buttons.
  Power is the bike's own estimate (probably from level and cadence), not a measured value.
- Range (`0x2AD6`): 1.0–16.0, step 1.0.

## Code map (`app/src/main/java/app/merachride/`)

| Path | What |
|---|---|
| `protocol/` | Pure Kotlin: FTMS command bytes, live data and heart rate parsers, `ResistanceScale`, `ManualChangeDetector` |
| `ble/BleConnection.kt` | GATT connection, serialized operation queue, notifications |
| `BikeSession.kt` | Scan, connect + FTMS handshake, auto-reconnect, set level, connection log, simulated bike hook |
| `HeartRateMonitor.kt` | Second BLE link to a heart rate monitor (Pixel Watch, chest strap) |
| `workout/` | Workout model (segments, ramps, level offset), engine (1 s ticker), built-in workouts, editor draft, JSON codec, import |
| `history/` | `RideRecorder` (ride stats), `RideTracker` (start/finish/save), `PaceTarget`, JSON codec |
| `data/` | `MiniJson` (small JSON reader/writer) and `JsonFileStore` (list saved to a file) |
| `sim/` | Simulated bike and time speed-up (debug builds) |
| `service/RideService.kt` | Foreground service that keeps the process alive during a ride |
| `ui/` | Compose screens: `MainScreen` (tabs), `RideScreen`, `WorkoutsScreen` (list + editor), `HistoryScreen`, heart rate and simulator sheets, `Units` (imperial display) |

Custom workouts and rides are saved in the app's private storage as `workouts.json` and `rides.json`
(in metric; only the display is imperial). Reinstalling from Android Studio keeps them; uninstalling
deletes them.

## Not yet verified on the real bike

- Reconnecting after the bike goes to sleep, and resuming the workout.
- Ramp workouts, the level offset, pace targets, and whether History's distance, kcal and speed match the bike.
- Whether the Pixel Watch keeps heart rate sharing on between workouts, and reconnecting if it drops.
- One unexplained jump to level 16 during the last segment of Quick Test (seen once; parked).
