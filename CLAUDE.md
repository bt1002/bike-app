# CLAUDE.md

Android app (Kotlin + Jetpack Compose) that controls a Merach S29 Aura bike over Bluetooth LE using FTMS.
Read `HANDOFF.md` first. It has the current status, the discovery log and the next steps.

## Constraints

- **The primary dev PC cannot build Android.** It has no Android Studio, SDK, JDK or adb, and no admin
  rights to install them. Code written there is never compiled. The user builds on another machine and pastes
  errors back. So:
  - Don't claim code compiles or works. Say it's unverified.
  - Prefer library versions you are sure exist over the newest ones. Current pins are AGP 8.7.3,
    Gradle 8.10.2, Kotlin 2.0.21 and Compose BOM 2024.12.01.
  - Check Kotlin precedence carefully: `a and b == c` parses as `a and (b == c)`. Parenthesize bit masks.
  - Don't use pip, npm or similar installers on that PC. Use the built-in tools (PowerShell plus .NET
    `System.Drawing` for image work).
- Bike behaviour is only "verified" once the user has tested it on the real bike. Mark everything else
  as unverified in code comments and in `HANDOFF.md`.

## Verified bike protocol (do not change without new evidence)

- Device name `MRK-S29C-307F`, FTMS service `0x1826`.
- Control Point `0x2AD9` (Write + Indicate): enable indications, then `00` (request control) and `07` (start).
  Set level N with `04 <N*10>` as a uint8 (level 8 = `04 50`, level 16 = `04 A0`).
- Range is levels 1–16. Resistance target only; no power/ERG target.
- Proprietary service `59554c55-…-4d4552414348` ("MERACH") is a fallback that hasn't been explored.
  **Never write to `02f00000-…-fe00`**, which is probably firmware update (OTA).
- Unverified: whether Indoor Bike Data (`0x2AD2`) reports resistance as `8` or `80`.
  `IndoorBikeDataParser.normalizeResistance` guesses.

## Code layout (`MerachRide/app/src/main/java/app/merachride/`)

- `protocol/`: pure Kotlin with no Android imports, unit-tested. FTMS bytes, data parser, `BikeController`.
- `ble/BleConnection.kt`: one GATT connection with a serialized operation queue (one op in flight).
- `BikeSession.kt`: app-wide singleton. Handles scan, connect, the FTMS handshake, auto-reconnect,
  resistance commands and the in-app log.
- `workout/`: model, engine (1 s ticker on the Main dispatcher) and built-in workouts.
- `service/RideService.kt`: foreground service that keeps the process alive during a ride.
- `ui/`: Compose screens.
- Tests are in `MerachRide/app/src/test/`. Keep new protocol logic in `protocol/` so it stays testable.

## Working conventions

- After meaningful progress, add a dated line to the **Log** in `HANDOFF.md` and update **Status**.
- Git: the remote is `https://bt1002@github.com/bt1002/bike-app.git` (the username in the URL makes Git
  sign in as the personal account). Commit as `bt1002 <bradtrammell@gmail.com>` (set in the repo's local
  config). Branch `main`.
- `screenshots/Screenshot_20261114-174353.png` is git-ignored on purpose because it shows other people's
  nearby devices. Don't commit scans that list third-party devices.
