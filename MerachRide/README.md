# Merach Ride

Android app (Kotlin + Jetpack Compose) that connects to a **Merach S29 Aura** bike over Bluetooth LE
using the standard FTMS protocol, shows live stats, and runs structured workouts by setting the
bike's resistance (levels 1–16) automatically.

> This project was written on a machine with no Android tooling, so it has **never been compiled**.
> Expect to fix a few small compile errors on the first build.

## Build and install

1. Copy this whole `MerachRide` folder to a machine with **Android Studio**.
2. Android Studio → **File → Open** → select the `MerachRide` folder (the one containing `settings.gradle.kts`).
3. Wait for Gradle sync. It downloads Gradle 8.10.2, AGP 8.7.3, and the libraries the first time.
   - The `gradlew` scripts aren't included; Android Studio doesn't need them. To get them, run
     `gradle wrapper` in this folder, or let Studio create them when it asks.
   - If Studio offers to upgrade the Android Gradle Plugin, accepting is fine.
   - If sync fails with *"Unsupported class file major version"*, go to Settings → Build → Build Tools →
     Gradle and set **Gradle JDK** to a newer version. JDK 23 is known to work (2026-11-14, on the primary dev PC).
4. On the phone, turn on **Developer options → USB debugging** and plug it in.
5. Press **Run ▶** with the `app` configuration.

Unit tests (protocol bytes, data parser, workout timing): right-click `app/src/test` → **Run Tests**.

## Using it

1. Fully close the Merach app (the bike accepts one controller at a time).
2. Grant the *Nearby devices* permission when asked.
3. **Scan for bikes** → tap `MRK-S29C-307F`. Next time, use the **Connect to …** button.
4. Use − / + to set resistance by hand, or pick a workout and press **Start**.
5. **Show connection log** at the bottom shows every step. Check it first if something misbehaves.

The connection stays up with the screen off (via a foreground notification) and automatically reconnects if
the link drops, re-applying the last resistance level.

## Bike protocol (verified 2026-11-14 with nRF Connect)

| Step | Write to `0x2AD9` | Reply (indication) |
|---|---|---|
| Request control | `00` | `80 00 01` |
| Start / resume | `07` | `80 07 01` |
| Set level N (1–16) | `04 <N×10>`, e.g. `04 50` = level 8 | `80 04 01` |

- Control: **resistance level only**. The bike doesn't accept target power, so there's no native ERG mode.
- Data (`0x2AD2`): speed, cadence, distance, resistance, power, energy, heart rate, elapsed time.
- Range (`0x2AD6`): 1.0–16.0, step 1.0.

## Code map

| Path | What |
|---|---|
| `protocol/FtmsProtocol.kt` | Control Point command bytes + response parsing |
| `protocol/IndoorBikeData.kt` | Parser for live data notifications |
| `ble/BleConnection.kt` | GATT connection, serialized operation queue, notifications |
| `BikeSession.kt` | Scan, connect + FTMS handshake, auto-reconnect, set level, log |
| `workout/` | Workout model, engine (1 s ticker → level changes), built-in workouts |
| `service/RideService.kt` | Foreground service that keeps the process alive during a ride |
| `ui/` | Compose screens |

## Not yet verified on the real bike

- Whether live data reports resistance as `8` or `80` (`ResistanceScale` detects it and writes the answer to the connection log).
- How the bike reacts when its own buttons change resistance mid-workout.
- Reconnect behaviour after the bike goes to sleep.
