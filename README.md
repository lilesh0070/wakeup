# WakeUp Buddy ⏰

An alarm that **actually gets you out of bed.** It won't turn off and its volume can't be
lowered — the only way to stop it is to get up, **wash your face, and show the camera a live
face with both eyes open.** A sleepy, closed‑eye photo will not work.

Everything runs **100% offline** — the app has **no INTERNET permission at all**. Your proof
selfies stay in the app's private storage and **auto‑delete after 2 days (48h)**.

---

## Features

- ⏰ Multiple alarms with per‑day repeat (Sun–Sat, one‑time, weekdays)
- 🔊 Rings at **full alarm volume**; volume‑down and the Back button are blocked
- 📵 Full‑screen alarm that shows **over the lock screen**
- 🤳 **Dismiss only by a selfie** where an on‑device model detects a face with **both eyes open**, held steady for ~1.5s (liveness)
- 🖼️ Proof gallery with **manual delete** + automatic 48h cleanup
- 🔒 **Fully offline** — no internet permission, nothing is uploaded
- ⬆️ In‑app **Update** button (opens the latest GitHub release in your browser — the app itself never touches the network)

## Requirements

- Android 8.0 (API 26) or newer
- For reliable ringing, grant: **Notifications**, **Alarms & reminders** (exact alarm),
  **Display over other apps**, **Camera**, and set the app to **not** be battery‑optimized.
  On MIUI/ColorOS phones also enable **Autostart** and **Show on lock screen**.

## Install (sideload)

1. Download the APK from the [latest release](https://github.com/lilesh0070/wakeup/releases/latest).
2. Open it on your phone and allow "Install unknown apps" for your browser/file manager.
3. Install, open, and grant the permissions above.

## Build from source

Requires JDK 17 and the Android SDK (platform 35, build‑tools 36).

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Publishing an update

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build the APK.
3. Create a **GitHub Release**, attach the APK as an asset, and publish.

The app's **Update** button points at `/releases/latest`, so anyone can grab the newest build
from their browser. Set your repo URL in `app/src/main/java/com/wakeupbuddy/Config.kt`.

## Honest limitations

- No third‑party app can be *truly* unstoppable — a determined user can still force‑stop it from
  Settings, reboot, or use safe mode. WakeUp Buddy makes stopping it as hard as a normal app can.
- "Washing your face" itself cannot be detected. The app enforces the strongest practical proxy:
  a **live face with both eyes open**, held briefly — so you have to actually be up and awake.

## Tech

Kotlin · CameraX · ML Kit Face Detection (bundled, offline) · WorkManager · AlarmManager ·
foreground service · view binding.

## License

Personal project. Use at your own risk.
