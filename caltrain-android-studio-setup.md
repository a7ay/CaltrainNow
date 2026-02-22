# CaltrainNow — Android Studio Setup Guide

## Prerequisites

Before we start, make sure your machine meets these requirements:

- **OS:** Windows 10+, macOS 10.14+, or Linux (64-bit)
- **RAM:** 8 GB minimum, 16 GB recommended
- **Disk:** ~10 GB free (Android Studio + SDK + emulator images)
- **Internet:** Needed for initial setup and Gradle dependency downloads

---

## Step 1: Install Android Studio

1. Go to https://developer.android.com/studio
2. Download the latest stable version (Ladybug or newer).
3. Run the installer:
   - **Mac:** Open the `.dmg`, drag Android Studio to Applications.
   - **Windows:** Run the `.exe` installer, follow prompts.
   - **Linux:** Extract the `.tar.gz`, run `studio.sh` from the `bin/` folder.
4. On first launch, choose **Standard** setup — this installs the Android SDK, emulator, and recommended settings automatically.
5. Let the initial setup wizard finish downloading components. This takes a few minutes.

---

## Step 2: Verify SDK and Tools

After Android Studio opens:

1. Go to **Settings** (or **Preferences** on Mac):
   - Windows/Linux: `File → Settings → Languages & Frameworks → Android SDK`
   - Mac: `Android Studio → Settings → Languages & Frameworks → Android SDK`

2. Under the **SDK Platforms** tab, make sure these are checked and installed:
   - **Android 14 (API 34)** — our target/compile SDK
   - **Android 8.0 (API 26)** — our minimum SDK (no need to install this, just noting it)

3. Under the **SDK Tools** tab, make sure these are checked and installed:
   - Android SDK Build-Tools (latest)
   - Android SDK Command-line Tools
   - Android SDK Platform-Tools
   - Android Emulator
   - Google Play services (needed for FusedLocationProvider)

4. Click **Apply** if anything needed to be installed.

---

## Step 3: Install JDK

Android Studio bundles a JDK, but let's verify it's set up:

1. Go to `File → Project Structure → SDK Location` (or `Settings → Build, Execution, Deployment → Build Tools → Gradle`).
2. Under **Gradle JDK**, it should say **jbr-21** or similar (JetBrains Runtime, JDK 17+).
3. If it says "No JDK" or something older, select **Download JDK** and choose version 17 or 21.

---

## Step 4: Create a New Project (or Import Ours)

### Option A: I'll provide a complete project structure (recommended)

When I build the code, I'll give you a complete Gradle project. To import it:

1. Copy the project folder to your preferred location (e.g., `~/projects/CaltrainNow/`).
2. Open Android Studio → **File → Open** → navigate to the project folder → click **Open**.
3. Android Studio will detect it as a Gradle project and start syncing.
4. Wait for the Gradle sync to complete (first time takes 2–5 minutes as it downloads dependencies).
5. You'll see "BUILD SUCCESSFUL" in the Build output when it's ready.

### Option B: Create from scratch and paste code in

1. **File → New → New Project**
2. Select **Empty Activity** (Compose).
3. Configure:
   - Name: `CaltrainNow`
   - Package name: `com.caltrainnow`
   - Save location: your preferred path
   - Language: **Kotlin**
   - Minimum SDK: **API 26 (Android 8.0)**
   - Build configuration language: **Kotlin DSL**
4. Click **Finish**.
5. Then replace/add files as I provide them.

---

## Step 5: Set Up an Emulator (for testing without a physical phone)

1. Go to **Tools → Device Manager** (or click the phone icon in the toolbar).
2. Click **Create Virtual Device**.
3. Choose a device profile:
   - **Pixel 7** or **Pixel 8** are good choices.
   - Click **Next**.
4. Select a system image:
   - Choose **API 34** (Android 14) with **Google Play** (important for Maps intents).
   - If it says "Download", click it and wait for the download.
   - Click **Next**.
5. Name it whatever you like, click **Finish**.
6. Click the **Play** button (▶) next to the device to launch it.

### Using a Physical Android Phone Instead

If you prefer testing on your actual phone:

1. On your phone: go to **Settings → About Phone** → tap **Build Number** 7 times to enable Developer Mode.
2. Go to **Settings → Developer Options** → enable **USB Debugging**.
3. Connect your phone via USB.
4. Your phone should appear in the device dropdown at the top of Android Studio.
5. If prompted on your phone to "Allow USB debugging?", tap **Allow**.

---

## Step 6: Verify Everything Works

Let's make sure the toolchain is working before we add our code:

1. With the default project open and an emulator running (or phone connected):
2. Click the green **Run** button (▶) in the toolbar, or press `Shift + F10`.
3. The app should build, install, and launch on the emulator/phone.
4. You should see a basic "Hello Android" screen.
5. If this works, your setup is good to go.

### Common Issues and Fixes

| Problem | Fix |
|---------|-----|
| Gradle sync fails with "SDK not found" | Go to `File → Project Structure → SDK Location` and set the Android SDK path |
| "License not accepted" error | Run `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses` in terminal and accept all |
| Emulator is extremely slow | Enable hardware acceleration: check HAXM (Intel) or Hypervisor (AMD) in SDK Manager → SDK Tools tab |
| "INSTALL_FAILED_INSUFFICIENT_STORAGE" on emulator | Wipe emulator data: Device Manager → click ▼ arrow → Wipe Data |
| Build fails with "Unsupported class file version" | Check Gradle JDK is set to 17+ (Step 3) |
| USB debugging not detecting phone | Try a different USB cable (some are charge-only), or switch USB mode to "File Transfer" on the phone |

---

## Step 7: Understand the Project Files I'll Provide

When I build Phase 1, here's what you'll receive and where it goes:

```
CaltrainNow/
├── build.gradle.kts              # Root build file (Gradle plugins)
├── settings.gradle.kts           # Project settings
├── gradle.properties             # Gradle configuration
├── local.properties              # SDK path (auto-generated, don't commit)
│
├── app/
│   ├── build.gradle.kts          # App-level dependencies (Room, Hilt, OkHttp, etc.)
│   │
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Permissions (INTERNET, LOCATION)
│   │   └── java/com/caltrainnow/
│   │       ├── core/             # ★ Pure Kotlin logic
│   │       ├── data/             # ★ Room DB, downloader, repository
│   │       ├── di/               # Hilt modules
│   │       ├── ui/               # Phase 2 (placeholder for now)
│   │       └── util/             # Navigation utils
│   │
│   ├── src/test/                 # ★ Unit tests (run on JVM)
│   └── src/androidTest/          # ★ Integration tests (run on emulator)
```

### How to Run Tests

**Unit tests** (fast, no emulator needed):
- Right-click the `src/test/` folder → **Run Tests**
- Or in terminal: `./gradlew test`

**Integration tests** (needs emulator or phone):
- Start an emulator first
- Right-click the `src/androidTest/` folder → **Run Tests**
- Or in terminal: `./gradlew connectedAndroidTest`

### How to Build an APK

For a debug APK (for personal testing):
1. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Find it at: `app/build/outputs/apk/debug/app-debug.apk`
3. Transfer to your phone and install (enable "Install from unknown sources" if prompted).

For a release APK (for distribution):
1. **Build → Generate Signed Bundle / APK**
2. Choose **APK**.
3. Create a new keystore (first time only):
   - Click **Create new...**
   - Choose a file location and password (save these — you'll need them for every update)
   - Fill in the certificate info (name, org — can be anything for personal use)
4. Select **release** build type.
5. Click **Finish**.
6. Find it at: `app/build/outputs/apk/release/app-release.apk`

---

## Step 8: Optional But Recommended

### Install the Kotlin Plugin (usually pre-installed)
- `Settings → Plugins` → search "Kotlin" → verify it's installed and up to date.

### Enable Auto-Import
- `Settings → Editor → General → Auto Import`
- Check **Add unambiguous imports on the fly**
- Check **Optimize imports on the fly**

### Increase IDE Memory (if your machine has 16+ GB RAM)
- `Help → Edit Custom VM Options`
- Change `-Xmx` to `4096m`:
  ```
  -Xmx4096m
  ```
- Restart Android Studio.

---

## Checklist Before We Start Building

Run through this to confirm you're ready:

- [ ] Android Studio installed and opens without errors
- [ ] Android SDK (API 34) installed
- [ ] SDK Build-Tools, Platform-Tools, Command-line Tools installed
- [ ] Google Play services SDK installed
- [ ] Gradle JDK is version 17 or 21
- [ ] Emulator created (Pixel 7/8, API 34, Google Play) OR physical phone with USB debugging
- [ ] Default "Hello Android" app builds and runs successfully

Once all boxes are checked, let me know and we'll start building!
