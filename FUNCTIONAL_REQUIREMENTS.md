# Mosque Prayer Notifications — Functional Requirements

**Product:** Prayer-time notification app for Melbourne Grand Mosque (MGM), Tarneit  
**Website source:** [https://mgm.org.au/prayer-timings/](https://mgm.org.au/prayer-timings/)  
**Primary platform (v1):** Android  
**Future platform:** Apple (iOS) — same functional behaviour unless noted under Platform notes  
**Status:** Agreed requirements (pre-build)

---

## 1. Goal

Users should receive Azaan-time alerts for MGM prayer times **without opening the website**, with full control over which alerts fire and what sound each alert uses.

---

## 2. Scope

### In scope

- Fetch and display MGM prayer times for the current day
- Schedule local notifications at Azaan times
- Show Jamaat time in each Azaan notification
- Per-target enable/disable toggles
- Per-target custom notification sounds (with default fallback)
- Immediate “Test notification”
- Nightly refresh + clear/reschedule of scheduled alerts
- Immediate clear/reschedule when the user changes notification settings

### Out of scope (v1)

- Location-based prayer calculation
- Push notifications from a server
- Restoring settings/sounds after uninstall/reinstall
- Sunrise as a notifiable prayer
- Building a full mosque website replacement
- Multi-mosque support

---

## 3. Prayer time source

| Rule | Detail |
|------|--------|
| Mosque | Fixed: Melbourne Grand Mosque (Tarneit) only |
| Source of truth | MGM prayer timings page / embedded schedule UI |
| Data to extract | Azaan (ADHAN) time and Jamaat (IQAMA) time per prayer |
| Refresh schedule | Every day at **3:00 AM** local device time |
| Failure behaviour | If fetch fails, keep previously scheduled times if still valid for today; surface a clear error in the app UI (user can retry) |

### Time meanings

| Label on site | Meaning in app | Notify? |
|---------------|----------------|---------|
| Top time (ADHAN / Azaan) | Prayer start / Azaan | **Yes** — notification fires at this time |
| Bottom time (IQAMA / Jamaat) | Congregation start | **No separate alert** — shown inside the Azaan notification text |
| Sunrise | End of Fajr time window | **No** — not a prayer notification target |

### Daily prayer targets (non-Friday)

| ID | Name | Azaan source | Jamaat shown in notification |
|----|------|--------------|------------------------------|
| `fajr` | Fajr | Grid Azaan | Grid Jamaat |
| `dhuhr` | Dhuhr (Dohr) | Grid Azaan | Grid Jamaat |
| `asr` | Asr | Grid Azaan | Grid Jamaat |
| `maghrib` | Maghrib | Grid Azaan | Grid Jamaat |
| `isha` | Isha | Grid Azaan | Grid Jamaat |

### Friday extras

On Friday, in addition to normal **Dhuhr** (from the grid):

| ID | Name | Notify at | Notes |
|----|------|-----------|-------|
| `jumuah_1` | Jumu’ah #1 | 12:30 PM | Independent toggle |
| `jumuah_2` | Jumu’ah #2 | 1:30 PM | Independent toggle |
| `jumuah_3` | Jumu’ah #3 | 2:15 PM | Independent toggle |

Friday rules:

- Keep **Dhuhr Azaan** from the grid as its own notification target.
- Dhuhr notification text still shows **Dhuhr Jamaat from the grid** (not Jumu’ah #1).
- All three Jumu’ah session times are notifiable and independently toggleable.
- Observed site line for reference: `JUMU'AH 12:30PM & 1:30PM & 2:15PM` (values may change if MGM updates them; app should prefer parsed live values when available, otherwise use the known MGM Friday session times from the schedule source).

---

## 4. Notifications

### Behaviour

1. Notification fires at the **Azaan** time for each enabled target.
2. Notification content must include at least:
   - Prayer / target name
   - Azaan time
   - Jamaat time (for grid-based prayers)
3. For Jumu’ah #1/#2/#3, content must identify which session it is (e.g. “Jumu’ah #2”).
4. No automatic notification at Jamaat time.

### Example notification text (illustrative)

> **Fajr Azaan** — 5:46  
> Jamaat: 6:16

### Enable / disable

- Every notification target has its **own** on/off toggle.
- Full toggle list:
  - Fajr
  - Dhuhr
  - Asr
  - Maghrib
  - Isha
  - Jumu’ah #1
  - Jumu’ah #2
  - Jumu’ah #3
- If a target is **OFF**, it stays OFF until the user turns it ON again (no auto-re-enable on nightly refresh).
- Changing a toggle mid-day must **clear and reschedule remaining notifications for today** immediately so the change takes effect at once.

Example: Asr OFF at 2:00 PM → no Asr notification at 3:16 PM today.

### Sounds

- User can upload/select a custom sound **per notification target**.
- If a target has no custom sound, use the **default** notification sound.
- Default sound is used until the user sets a custom one for that target.

### Test notification

- Settings include a **Test** action.
- Test fires a notification **immediately** (not at next prayer time).
- Test should use the selected sound for the target being tested (or default if none set).

### Scheduling hygiene

| Event | Required behaviour |
|-------|--------------------|
| Daily 3:00 AM job | Fetch today’s times → **clear** existing scheduled prayer notifications → **reschedule** enabled targets for today |
| User changes toggles / sounds | Clear and reschedule remaining notifications for today |
| App launch (optional hardening) | May verify today’s schedule is present; if missing/stale, refresh and reschedule |

---

## 5. Settings & persistence

### Must persist while app remains installed

- Per-target enable/disable state
- Per-target custom sound selection (and stored copy of uploaded audio as required by platform)
- Default sound choice (if applicable)

### Not required (v1)

- Restore settings or custom sounds after uninstall/reinstall

---

## 6. UI (minimum)

1. **Today’s prayer times** — show Azaan + Jamaat for Fajr, Dhuhr, Asr, Maghrib, Isha (and Friday Jumu’ah sessions when applicable).
2. **Notification settings** — toggle list for every target; sound picker / upload per target; Test button.
3. **Status / errors** — last successful fetch time; clear message if fetch failed.

Visual design polish is not a v1 functional blocker.

---

## 7. Permissions & platform notes

### Android (v1)

- Request notification permission (required on modern Android, including Android 16).
- Use **local** notifications (not server push).
- Prefer a **separate notification channel per target** so each can have its own sound.
- Uploaded audio must be selected via the system picker and stored in app-accessible storage for notification use.
- Background scheduling must survive typical device sleep (exact alarm / reliable scheduler as allowed by OS policies).

### Apple / iOS (later)

- Same functional behaviour: local notifications, Azaan triggers, Jamaat in body, per-target toggles, per-target custom sounds with default fallback, 3:00 AM refresh, clear/reschedule on settings change.
- Sound and background-task mechanics will differ by iOS APIs; implementers must map FR behaviour to iOS equivalents without changing user-facing rules above.

---

## 8. Non-functional expectations (light)

| Area | Expectation |
|------|-------------|
| Reliability | Missed alerts due to OS battery restrictions should be minimized with best-practice scheduling |
| Privacy | No account required for v1; no server of our own required for v1 |
| Offline | Once today’s times are scheduled, notifications for remaining times should still fire without network |
| Maintainability | Parsing should target stable fields/IDs from the MGM schedule resource where possible (e.g. Azaan `s0`–`s5`, Iqama `qm1`–`qm6` in the observed embedded UI), with awareness that site structure can change |

---

## 9. Acceptance criteria (v1 Android)

- [ ] App shows today’s MGM Azaan + Jamaat times from the website source
- [ ] At ~3:00 AM, times refresh and notifications are cleared then rescheduled
- [ ] Notifications fire at Azaan times only
- [ ] Notification text includes Jamaat time for grid-based prayers
- [ ] Sunrise never generates a prayer notification
- [ ] Fajr / Dhuhr / Asr / Maghrib / Isha each have independent toggles
- [ ] Jumu’ah #1 / #2 / #3 each have independent toggles
- [ ] On Friday, Dhuhr Azaan can still notify using grid Azaan + grid Jamaat text
- [ ] On Friday, all three Jumu’ah sessions can notify when enabled
- [ ] Turning a target OFF stops that target’s remaining alerts today without waiting for 3:00 AM
- [ ] Custom sound can be uploaded per target; unset targets use default
- [ ] Test notification fires immediately with the expected sound
- [ ] Uninstall restore is not promised

---

## 10. Open items (non-blocking)

- Exact iOS background-fetch / notification-sound packaging details (decide at Apple build time)
- Exact fallback if MGM changes HTML structure (manual update vs alternate data endpoint)
- Whether Jumu’ah times should always be parsed live vs hard-coded fallbacks when parse fails

---

## 11. Decision log (from discovery)

| Decision | Choice |
|----------|--------|
| Platform first | Android (Kotlin) |
| Time source | Website (MGM), not device GPS calculation |
| Notify on | Azaan (top) time |
| Show in notification | Jamaat (below) time |
| Sunrise | Not a notification target |
| Friday Dhuhr | Keep grid Dhuhr Azaan; Jamaat text from grid |
| Friday Jumu’ah | All 3 sessions, separate toggles |
| Sounds | Upload per target; default if missing |
| Channels (Android) | Separate channel per target |
| Test | Immediate |
| Mid-day toggle change | Clear/reschedule immediately |
| Reinstall restore | Not required |
