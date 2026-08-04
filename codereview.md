# Code Review — MGM Prayer Alerts

**Project:** `C:\Users\User\Projects\MgmPrayerAlerts`  
**Reviewed against:** `FUNCTIONAL_REQUIREMENTS.md`  
**Date:** 4 August 2026  

Findings ordered by severity. No code changes were made as part of this review.

---

## Critical

### 1. Afternoon/evening prayers schedule at the wrong clock time (AM/PM lost)

`formatShort()` writes times like `5:40` / `3:16` / `6:49` with no meridian. `clearAndReschedule()` later re-parses them with `H:mm`, so:

| Prayer | Real time | Stored/display | Re-parsed as |
|--------|-----------|----------------|--------------|
| Maghrib | 17:40 | `5:40` | **05:40** |
| Asr | 15:16 | `3:16` | **03:16** |
| Isha | 18:49 | `6:49` | **06:49** |
| Jumu’ah #2 | 13:30 | `1:30` | **01:30** |
| Jumu’ah #3 | 14:15 | `2:15` | **02:15** |

Fajr/Dhuhr often “look” fine, which makes this easy to miss in casual testing.

**Files:** `TimeParse.kt`, `AwqatPrayerRepository.kt`, `PrayerScheduler.kt`

---

## High

### 2. Failed 3:00 AM refresh can stop future nightly jobs

`DailyRefreshReceiver` only calls `ensureDailyRefreshScheduled()` after a successful `refreshAndReschedule()`. The 3 AM alarm is one-shot; if the network fetch throws, the next 3 AM is never re-armed.

**File:** `DailyRefreshReceiver.kt`

### 3. Stale schedule still rescheduled onto “today”

On fetch failure, UI/boot paths call `clearAndReschedule(cached)` without checking `dateKey == today`. Yesterday’s clock strings get attached to today’s date (and hit the AM/PM bug above). FR asked to keep times only if still valid for today.

**Files:** `MainActivity.kt`, `BootReceiver.kt`, `PrayerScheduler.kt`

### 4. Exact-alarm denial fails silently

`setExact()` returns with no schedule and no signal if `canScheduleExactAlarms()` is false. After the user leaves settings without granting, prayer alarms simply never fire.

**File:** `PrayerScheduler.kt`

---

## Medium

### 5. Custom notification sounds likely flaky across OEMs

Channel sound via `FileProvider` + `grantUriPermission("com.android.systemui", …)` is not reliable; many devices ignore or can’t read that URI for channel audio.

**File:** `NotificationHelper.kt`

### 6. `goAsync()` + unbounded IO coroutine

Nightly/boot refresh does multiple HTTP calls inside `goAsync()`. On slow networks the process can be killed mid-refresh before scheduling completes. Prefer a short `WorkManager` job or tighter timeout/retry.

**Files:** `DailyRefreshReceiver.kt`, `BootReceiver.kt`

### 7. No automated tests for the riskiest logic

Zero coverage for `TimeParse`, wtimes/iqama parsing, Friday Jumu’ah handling, or scheduling. The AM/PM bug would have been caught by a single unit test.

### 8. Notifications may be posted without permission check

`showPrayerNotification()` never verifies `POST_NOTIFICATIONS`; on Android 13+ (incl. 16) posts can no-op after denial with little feedback at alarm time.

**File:** `NotificationHelper.kt`

---

## Low

### 9. `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM`

Declaring both can draw Play policy scrutiny; a prayer reminder app usually only needs `SCHEDULE_EXACT_ALARM` with a clear settings prompt.

**File:** `AndroidManifest.xml`

### 10. UI shows ambiguous 12-hour times

Same `formatShort` issue: Maghrib “5:40” reads like morning. Display should keep AM/PM or use 24-hour.

---

## What’s solid

- Fixed MGM/Awqat data path
- Sunrise excluded from alerts
- Per-target toggles + clear/reschedule on change
- Friday Dhuhr + 3 Jumu’ah model
- Test fires immediately
- Reboot receiver present

---

## Recommended fix order

1. Store and schedule times in unambiguous 24-hour form (or always keep AM/PM).
2. Re-arm the 3 AM job in a `finally` block.
3. Refuse to schedule a schedule whose `dateKey` isn’t today.
4. Surface exact-alarm / notification permission failures in the UI.
5. Harden custom sounds (and add unit tests for time parse + scheduling).

---

## Fix status (implemented 4 Aug 2026)

| Item | Status |
|------|--------|
| 1 Critical AM/PM / 24h storage | Fixed |
| 2 3 AM re-arm in `finally` | Fixed |
| 3 Stale schedule guard | Fixed |
| 4 Exact-alarm UX | Fixed |
| 5 Custom sound hardening | Improved (external files + broader grants) |
| 6 goAsync / WorkManager | Partially addressed (failure paths safer; WorkManager not added) |
| 7 Unit tests | Added `TimeParseTest` |
| 8 Notification permission check | Fixed |
| 9 Drop `USE_EXACT_ALARM` | Fixed |
| 10 UI AM/PM display | Fixed |

Rebuilt APK: `C:\Users\User\Projects\MgmPrayerAlerts\app\build\outputs\apk\debug\app-debug.apk`
