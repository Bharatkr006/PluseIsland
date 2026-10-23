# PulseIsland 🏝️

**PulseIsland** is a lightweight, personalized, interactive Dynamic Island native Android application built using Kotlin, Jetpack Compose, and the Android `WindowManager` API.

Designed from the ground up for **extreme battery efficiency**, PulseIsland provides an interactive floating pill positioned near the top-center of the screen that expands into a rich status card upon tapping—without polling, background loops, or permanent CPU wake locks.

---

## 📱 Features in Version 1

* **Interactive Floating Pill:**
  * Uses `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
  * Compact idle state (`120dp` pill) with glowing status indicators.
  * Hardware-accelerated morph transition on tap into an expanded panel.
  * Tapping again collapses the island back into its compact idle state.
  * Direct "Stop" button in the expanded view to dismiss the island immediately.
* **Non-Blocking Touch Architecture:**
  * Uses `WRAP_CONTENT` window bounds so touches outside the pill pass through to whatever app is running underneath.
  * Safe top positioning accounting for status bar height and front camera cutouts (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`).
* **Fully Customizable Size & Fixed Position:**
  * **Vertical Offset Slider (0–90 dp)**: Align the pill precisely around or below any camera cutout or teardrop notch.
  * **Horizontal Offset Slider (-120 to +120 dp)**: Accommodate corner/left-aligned hole-punch cameras or centered setups.
  * **Size Scale Slider (80%–130%)**: Resize the pill between ultra-compact and large high-visibility modes.
  * **Drag-and-Drop Repositioning**: Touch and drag the floating island anywhere on screen with your finger.
  * **Fixed Persistence**: Once customized, position and size are permanently stored in `SharedPreferences` and remain fixed across app launches and reboots until changed.
* **Modern Jetpack Compose Setup App:**
  * Minimalist dark theme (`#0A0B0E`) with vibrant cyan and emerald accents.
  * Interactive in-app live preview reflecting exact scale and expand/collapse animations.
  * Real-time reactive permission status ("Granted" / "Required").
  * Dedicated **Start Island** and **Stop Island** controls with live service status indicators.
* **Battery-Efficient Service Lifecycle:**
  * No background polling, recurring coroutines, or alarms.
  * Zero permanent wake locks.
  * 100% offline—no network permissions, analytics, or tracking.
  * Animations run strictly during user interactions and stop immediately afterwards (0% idle CPU usage).
* **Automated Cloud Build:**
  * GitHub Actions workflow that automatically compiles and packages a downloadable `app-debug.apk` on push or manual trigger.

---

## 🛠️ Tech Stack & Requirements

| Component | Specification |
| :--- | :--- |
| **Language** | Kotlin 2.0.20 |
| **UI Framework** | Jetpack Compose (Material 3 BOM 2024.09.02) |
| **Overlay Window** | Native Android `WindowManager` (`TYPE_APPLICATION_OVERLAY`) |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Target / Compile SDK** | API 34 (Android 14) |
| **Build Tooling** | Gradle 8.10.2 + Android Gradle Plugin 8.6.0 |
| **JDK** | Java 17 |

---

## 🚀 Pushing to GitHub

To push this repository to GitHub and trigger the automated APK build workflow:

1. **Initialize Git (if not already done) & stage all files:**
   ```bash
   git init
   git add .
   git commit -m "feat: initial commit of PulseIsland v1.0"
   ```

2. **Rename default branch to `main`:**
   ```bash
   git branch -M main
   ```

3. **Link to your GitHub repository:**
   *(Create an empty repository on GitHub first, then run:)*
   ```bash
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPOSITORY_NAME>.git
   ```

4. **Push to GitHub:**
   ```bash
   git push -u origin main
   ```

---

## ⚙️ Running GitHub Actions & Downloading the APK

Once pushed, GitHub Actions automatically handles building the APK without needing Android Studio installed locally on your computer:

1. Open your repository on GitHub in a web browser.
2. Click on the **Actions** tab at the top.
3. You will see the **Build Android APK** workflow running.
4. *(Optional manual run)*: In the left sidebar, click **Build Android APK**, click **Run workflow**, select the `main` branch, and click **Run workflow**.
5. Once the run completes (green checkmark), click on the workflow run.
6. Scroll down to the **Artifacts** section at the bottom of the summary page.
7. Click **`PulseIsland-debug-apk`** to download the ZIP file containing `app-debug.apk`.

---

## 📲 Installing & Permissions Setup

### 1. Installation
1. Extract the downloaded `PulseIsland-debug-apk.zip` on your phone or transfer `app-debug.apk` via USB / cloud storage.
2. Open the APK on your Android device. If prompted, allow "Install unknown apps" for your file manager or browser.

### 2. Granting Overlay Permission
Android strictly controls floating windows to protect user privacy:
1. Launch **PulseIsland** from your app drawer.
2. The main screen will display **Overlay Permission: Required**.
3. Tap **Grant Overlay Permission** (or **Manage System Permission**).
4. In Android's system settings list, find **PulseIsland** and toggle **Allow display over other apps** to **ON**.
5. Press the back button to return to PulseIsland. The status will immediately update to **Granted**.

### 3. Starting the Island
1. Tap **Start Island**.
2. A sleek black pill will float near the top of your screen.
3. Tap the pill to expand it into the PulseIsland status panel.
4. Tap again to collapse it, or tap the **✕** button to dismiss it.
5. You can also tap **Stop Island** inside the main app at any time.

### 4. Revoking Permission
To revoke permission at any point:
* Open **Android Settings** > **Apps** > **Special app access** > **Display over other apps** > **PulseIsland** > toggle **OFF**.

---

## 🔋 Battery Efficiency & Android Execution Trade-Offs

### Background Execution Realities
Android operating system releases (API 26+) enforce strict background limitations to conserve battery life:
* Standard background services are terminated within seconds after the user exits an application.
* To provide a stable overlay, PulseIsland runs a lightweight **Foreground Service** (`specialUse` service type on Android 14+) with a low-priority notification.
* This notification ensures the operating system does not immediately kill the overlay process while you switch between apps.

### Manufacturer Battery Optimizations
Certain Android manufacturers (e.g., Xiaomi/MIUI, Samsung/OneUI, Huawei, OnePlus/OxygenOS) implement aggressive proprietary battery killers that terminate background processes regardless of standard Android lifecycle rules:
* If you find the island closes unexpectedly after a few hours, add PulseIsland to your device's **Battery Optimization Exclusions** (**Settings** > **Battery** > **Unrestricted / Do not optimize**).
* **Honest Design Principle:** PulseIsland deliberately does not employ invasive battery hacks (such as fake silent audio playback, continuous wake locks, or accessibility service abuse). When the overlay is stopped or the process is cleared by the OS, it cleanly releases all memory and CPU resources.

---

## 🔧 Troubleshooting

| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **"Grant overlay permission first" message** | Permission not enabled in system settings | Tap **Grant Overlay Permission** in the app and toggle **Allow display over other apps**. |
| **Island not visible on screen** | Position offset behind status bar or camera cutout | PulseIsland targets `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`. On devices with large teardrop notches, ensure the top margin is visible; on punch-hole devices it aligns neatly below/around the cutout. |
| **GitHub Actions build fails with Gradle permission denied** | Git did not preserve the executable bit for `gradlew` | Run `git update-index --chmod=+x gradlew` locally, commit, and push. (Handled automatically by the workflow via `chmod +x gradlew`). |
| **"App not installed" on device** | Existing installation with conflicting signature | Uninstall any older version of PulseIsland before installing the newly downloaded APK. |

---

## 📄 License
Open source under the [Apache 2.0 License](LICENSE).
