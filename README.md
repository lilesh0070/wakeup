# WakeUp Buddy ⏰

An alarm that **actually gets you out of bed.** It won't turn off and its volume can't be
lowered — the only way to stop it is to get up, **wash your face, and show the camera a live
face with both eyes open.** A sleepy, closed‑eye photo will not work.

The **alarm works 100% offline.** The app touches the internet only when you tap **Update** —
it downloads and installs the newest version from GitHub, in‑app. Your proof selfies stay in the
app's private storage and **auto‑delete after 2 days (48h)**.

---

## Features

- ⏰ Multiple alarms with per‑day repeat (Sun–Sat, one‑time, weekdays)
- 🔊 Rings at **full alarm volume**; volume‑down and the Back button are blocked
- 📵 Full‑screen alarm that shows **over the lock screen**
- 🤳 **Dismiss only by a selfie** where an on‑device model detects a face with **both eyes open**, held steady briefly (liveness)
- 🖼️ Proof gallery with **manual delete** + automatic 48h cleanup
- 🔒 **Offline alarm** — nothing is uploaded; the internet is used only to fetch updates
- ⬆️ In‑app **Update** — downloads & installs the latest GitHub release inside the app (no browser), plus a shareable **QR**

## Requirements

- Android 8.0 (API 26) or newer
- For reliable ringing, grant: **Notifications**, **Alarms & reminders** (exact alarm),
  **Display over other apps**, **Camera**, and set the app to **not** be battery‑optimized.
  For in‑app updates, also allow **Install unknown apps** when prompted.
  On MIUI/ColorOS phones also enable **Autostart** and **Show on lock screen**.

## Install (first time)

1. Download the APK from the [latest release](https://github.com/lilesh0070/wakeup/releases/latest).
2. Open it on your phone and allow "Install unknown apps" for your browser/file manager.
3. Install, open, and grant the permissions above.

After that, use the in‑app **Update** button — it always fetches the newest release.

## Build from source

Requires JDK 17 and the Android SDK (platform 35, build‑tools 36).

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Publishing an update

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build the APK.
3. Create a **GitHub Release** whose tag is `v<versionName>` (e.g. `v1.3`) and attach the APK as an asset named **`WakeUpBuddy.apk`**, then publish.

The in‑app updater reads `releases/latest`, compares the tag to the installed version, and (if newer)
downloads and installs `WakeUpBuddy.apk`. So every phone always gets the newest build.

## Privacy / network

The app uses the internet **only** in the Update flow (GitHub API + APK download). It has no
analytics or tracking, and proof photos never leave the device.

## Honest limitations

- No third‑party app can be *truly* unstoppable — a determined user can still force‑stop it from
  Settings, reboot, or use safe mode. WakeUp Buddy makes stopping it as hard as a normal app can.
- "Washing your face" itself cannot be detected. The app enforces the strongest practical proxy:
  a **live face with both eyes open**, held briefly — so you have to actually be up and awake.

## Tech

Kotlin · CameraX · ML Kit Face Detection (bundled, offline) · ZXing (QR) · WorkManager ·
AlarmManager · foreground service · FileProvider · view binding.

## License

Personal project. Use at your own risk.
