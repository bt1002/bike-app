# bike-app

An Android app that controls a **Merach S29 Aura** exercise bike over Bluetooth LE. It shows live
ride stats and runs structured workouts by changing the bike's resistance automatically.

**Status:** v0.1. The bike protocol is verified by hand; the app code is written but **not yet compiled
or tested on a phone**.

## Why

Third-party apps have trouble connecting to this bike and changing resistance mid-ride, and Merach
publishes no protocol documentation. Probing the bike showed that it speaks the standard Bluetooth
**Fitness Machine Service (FTMS)**, so no reverse engineering was needed.

## What's verified

Tested on the bike with nRF Connect (2026-11-14):

| Action | Write to Control Point `0x2AD9` | Bike reply |
|---|---|---|
| Take control | `00` | `80 00 01` (success) |
| Start | `07` | `80 07 01` (success) |
| Set resistance level N (1–16) | `04 <N×10>`, e.g. `04 50` → level 8 | level changes on the bike's display |

- The bike accepts **resistance level only**. It has no target-power (ERG) control.
- It reports speed, cadence, distance, resistance, power, calories, heart rate and elapsed time.
- The bike's built-in screen does not block the phone from taking control.

## Repo layout

| Path | Contents |
|---|---|
| [`MerachRide/`](MerachRide/) | Android Studio project (Kotlin + Jetpack Compose). See [its README](MerachRide/README.md) for build steps. |
| [`MerachRide/HANDOFF.md`](MerachRide/HANDOFF.md) | Project status, discovery log, open questions and next steps |
| [`screenshots/`](screenshots/) | nRF Connect captures of the bike's Bluetooth services |

## Quick start

1. Clone the repo and open the `MerachRide` folder in Android Studio.
2. Plug in an Android phone (8.0 or later) with USB debugging on, and press **Run**.
3. Close the Merach app, pedal to wake the bike, then **Scan for bikes** in the app and tap `MRK-S29C-…`.
4. Try the **Quick Test** workout. It steps through levels 2 → 8 → 16 → 1.
