# Changelog

Each `## x.y.z` section becomes the notes of the matching GitHub release. To publish a release, bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and merge to `main`.

## 1.1.0 — 2026-10-08

### What's new
- Redesigned main screen built around the next prayer: large time, live countdown and Jamaat time
- Today's times in an aligned Azaan / Jamaat table, with the next prayer highlighted and finished prayers dimmed
- Friday Jumu'ah sessions in their own card
- Simpler alert settings: one row per prayer with a switch; sound and test options open when you tap the row
- Banners with a one-tap fix when notifications or exact alarms are off, or times fail to load
- Alerts are rescheduled automatically after you allow exact alarms
- Fixed the title sitting under the status bar on Android 15 and newer
- Buttons and switches use the app's gold and navy instead of default purple

## 1.0.0 — 2026-08-04

### Features
- Fetches daily times from Awqat (MGM schedule)
- Azaan notifications with Jamaat time in the text
- Per-prayer enable/disable + custom sounds
- Friday: Dhuhr + Jumu'ah #1/#2/#3
- Nightly 3:00 AM refresh
