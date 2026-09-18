# Per-App Volume

An Android app that remembers a separate volume level for each app, so
switching from a quiet podcast to a loud game doesn't blast your ears —
Android normally shares one volume across everything.

**© All rights reserved.** This is proprietary software — see [LICENSE](LICENSE).
It is not open source; the source code is here for build/audit purposes only.

---

## What it does

- **Per-app volume memory** — turn the volume up or down while using an app,
  and the app remembers that level next time it's active.
- **Custom volume panel** — replaces the plain system volume bar with a
  panel that shows which app you're adjusting, plus every app currently
  playing audio in the background.
- **Three modes**
  - **Live** — records new volume changes as you make them
  - **Play** — only applies previously saved levels, doesn't record new ones
  - **Stop** — fully disabled, standard Android behavior
- **Edge pull-tab** *(optional)* — a small tab at the screen edge; drag it
  inward to open the volume panel without touching the hardware buttons.
- **Persistent floating button** *(optional)* — a button that stays on
  screen; press and swipe up/down on it to adjust one chosen volume (media,
  ringtone, alarm, etc.) directly. Customizable shape, size, and
  transparency, and it can be dragged to reposition.
- **Per-app overrides** — a screen listing every app the volume has been
  changed for, so any of them can be excluded from volume memory.
- **Diagnostics** — checks whether the background services are still
  running, since some phone brands (Xiaomi, OnePlus, Realme, Huawei)
  aggressively kill background apps to save battery.

---

## Installing it (for users)

This app is **not on the Play Store**. You install the `.apk` file directly.

1. Go to this repo's **Actions** tab → the latest successful **Build and
   sign APK** run → download the `PerAppVolume-release-apk` file under
   **Artifacts**, and unzip it to get the `.apk`.
   (Or, if a `.apk` has been attached to a **Release** on this repo instead,
   download it from the **Releases** page — whichever the repo owner used.)
2. On your phone, open the downloaded `.apk`. Android will warn that
   installing apps from outside the Play Store is blocked by default —
   tap **Settings** on that warning and allow installs from that source
   (usually your file manager or browser), then go back and tap **Install**.
3. Open the app. It walks you through a short setup:
   - **Draw over other apps** — lets it show the volume panel on top of
     whatever you're using
   - **Accessibility service** — lets it notice which app is active and
     catch volume button presses (it does not read your screen content)
   - **Media session access** — lets Android tell it what's playing in the
     background (Spotify, YouTube, etc.); it only reads the app name and
     play/pause state, never notification text
   - **Usage access** — used for the app list screen
   - **Battery optimization / autostart** — stops your phone from silently
     killing the app in the background
4. Once setup is done, just use your phone normally — turn the volume up or
   down per app like usual, and it'll be remembered automatically.

**Uninstalling:** remove it like any other app from Settings → Apps. This
also removes all saved volume levels, since everything is stored on your
device only — nothing is sent anywhere.

---

## Privacy

Everything this app tracks (which app you're using, saved volume levels)
stays on your device in a local database. The app has no internet
permission and sends nothing anywhere.

---

## Building it yourself (for developers)

This is a standard Gradle/Kotlin Android project. Since it depends on
Google's Maven repositories, you'll need internet access to build it.

### Option A — GitHub Actions (recommended, builds a signed APK automatically)

1. **Create a signing keystore** (once, on your own computer):
   ```
   keytool -genkeypair -v -keystore release.keystore \
     -alias per_app_volume -keyalg RSA -keysize 2048 -validity 10000
   ```
   Keep `release.keystore` and its passwords private — don't commit them.

2. **Convert it to text** so it can go in a GitHub secret:
   - Mac/Linux: `base64 -w0 release.keystore`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))`

3. **Add 4 repository secrets** (Settings → Secrets and variables → Actions
   → New repository secret):

   | Secret name | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | the text from step 2 |
   | `SIGNING_STORE_PASSWORD` | the keystore password from step 1 |
   | `SIGNING_KEY_ALIAS` | `per_app_volume` |
   | `SIGNING_KEY_PASSWORD` | the key password from step 1 |

4. **Run the workflow**: Actions tab → **Build and sign APK** → **Run
   workflow**. When it finishes, download the signed APK from the run's
   **Artifacts** section.

The workflow (`.github/workflows/build-and-sign.yml`) also runs
automatically on every push to `main`.

### Option B — Build locally

Requires Android Studio (or the Android SDK + JDK 17 on the command line).

```
./gradlew assembleRelease
```

The unsigned/signed APK will be in `app/build/outputs/apk/release/`.
Without the `SIGNING_*` environment variables set, Gradle produces an
unsigned APK you'd need to sign manually with `apksigner`.

---

## Project structure

```
app/src/main/java/com/perappvolume/app/
├── data/        Room database + DataStore settings (per-app volumes, overlay & button config)
├── model/       Plain data classes / enums (OperatingMode, OverlayConfig, FloatingButtonConfig)
├── service/     AccessibilityService, NotificationListenerService, the foreground overlay
│                service, and the floating-button controller
├── overlay/     Compose UI for the volume panel
├── ui/          Onboarding, Settings, App list, Diagnostics, Floating button settings screens
└── util/        Service-health checks and OEM-specific battery/autostart deep links
```

---

## License

All rights reserved — see [LICENSE](LICENSE). You may not copy, modify,
redistribute, or reuse this code without written permission from the
copyright holder. Compiled APKs distributed by the copyright holder may be
installed and used for personal use only.
