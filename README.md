# MGM Prayer Alerts (Android)

Kotlin Android app for Melbourne Grand Mosque prayer notifications.

**Project path (canonical):** `C:\Users\User\Projects\MgmPrayerAlerts`  
> Do not build from a folder path that contains spaces (e.g. `Mosque app`) — Android resource tooling fails on Windows.

## What it does

- Pulls today’s MGM times from Awqat (`wtimes` + `iqamafixed`)
- Shows Azaan + Jamaat for Fajr / Dhuhr / Asr / Maghrib / Isha
- On Friday, also shows Jumu’ah #1/#2/#3
- Schedules local notifications at **Azaan** time (Jamaat shown in the text)
- Per-target enable/disable toggles
- Per-target custom notification sound (upload) with default fallback
- Immediate **Test** notification
- Clears/reschedules at **3:00 AM** and whenever settings change
- Restores schedules after device reboot

## Install the debug APK

Built APK:

`C:\Users\User\Projects\MgmPrayerAlerts\app\build\outputs\apk\debug\app-debug.apk`

On your phone:

1. Enable install from unknown sources / USB debugging
2. Copy the APK and install, **or** from a PC with the phone connected:
   ```bat
   adb install -r "C:\Users\User\Projects\MgmPrayerAlerts\app\build\outputs\apk\debug\app-debug.apk"
   ```
3. Open **MGM Prayer Alerts**
4. Allow **notifications**
5. Allow **exact alarms** if Android asks (required for on-time prayer alerts)

## Rebuild

```bat
cd /d C:\Users\User\Projects\MgmPrayerAlerts
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

Or open the folder in Android Studio and Run.

## Test checklist

- [ ] Today’s times match https://awqat.com.au/mgm/ (and MGM website)
- [ ] Toggle Asr off → no Asr alert later today
- [ ] Test button fires immediately with chosen sound
- [ ] Custom sound upload works per prayer
- [ ] Friday: Dhuhr + three Jumu’ah targets appear / can notify

## Requirements

See `FUNCTIONAL_REQUIREMENTS.md` in the Mosque app workspace.
