# CLAUDE.md

Android app (Kotlin + Jetpack Compose) that controls a Merach S29 Aura bike over Bluetooth LE using FTMS.
Read `MerachRide/HANDOFF.md` first. It has the current status, the discovery log and the next steps.

## Constraints

- **Build setup (since 2026-11-14):** Android Studio is installed on the primary dev PC, and the user now has
  admin rights there. Gradle sync needs **Gradle JDK 23** (Settings → Build → Build Tools → Gradle).
  The first successful build hasn't happened yet, so check the Status in `MerachRide/HANDOFF.md` before assuming anything compiles.
  - Don't claim code compiles or works until it has actually been built or tested. Otherwise say it's unverified.
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
  `protocol/ManualChangeDetector`. `protocol/ResistanceScale` still auto-detects the unit as a safeguard.

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

- After meaningful progress, add a dated line to the **Log** in `MerachRide/HANDOFF.md` and update **Status**.
- Git: the remote is `https://bt1002@github.com/bt1002/bike-app.git` (the username in the URL makes Git
  sign in as the personal account). Commit as `bt1002 <bradtrammell@gmail.com>` (set in the repo's local
  config). Branch `main`.
- `screenshots/Screenshot_20261114-174353.png` is git-ignored on purpose because it shows other people's
  nearby devices. Don't commit scans that list third-party devices.
