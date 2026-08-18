# Android Multi-Profile Web Workspace App

This is a native Android application that provides an isolated, multi-profile web workspace. It uses the modern AndroidX WebKit `ProfileStore` API to keep web sessions (cookies, cache, local storage) completely separate between profiles.

## Project Structure
- `app/` - The Android Application module
- `build.gradle.kts` - Project level Gradle configuration
- `settings.gradle.kts` - Gradle settings
- `app/src/main/java/com/webworkspace/app/` - Kotlin source code
  - `data/` - Room database and Profile models
  - `ui/` - Activities and UI logic

## Prerequisites
To build this project, you need:
- Java JDK 17 or newer installed.
- Android SDK (typically installed via Android Studio).
- Android Studio (Recommended for easiest setup).

## How to Build and Run (Command Line)

### 1. Opening the project
Open this folder (`WebWorkspace`) in Android Studio or navigate to it in your terminal.

### 2. Syncing Gradle
If you are using Android Studio, it will automatically sync.
If you are using the command line and you have Gradle installed globally, you can generate the wrapper:
```bash
gradle wrapper
```
(If you open it in Android Studio first, it will generate the `gradlew` script for you).

### 3. Building the Debug APK
Run the following command to compile the project and build the debug APK:
```bash
# On Windows
.\gradlew assembleDebug

# On macOS/Linux
./gradlew assembleDebug
```

### 4. Building the Release APK
To build a release APK:
```bash
.\gradlew assembleRelease
```
*Note: To actually install a release APK on a device, it needs to be signed with a keystore. If it's unsigned, you might have to sign it first.*

### 5. Locating the generated APK
After the build succeeds, you can find the APK in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

### 6. Installing the APK on an Android phone
Connect your Android phone with USB Debugging enabled, and run:
```bash
# For Debug
adb install app\build\outputs\apk\debug\app-debug.apk
```

## Features
- **Multi-Profile Isolation**: Uses `androidx.webkit.ProfileStore` to completely isolate sessions.
- **Desktop Mode**: Toggle between mobile and desktop user agents and viewports.
- **In-App Navigation**: Prevents websites from kicking you out to external browsers.
- **Media Support**: Full support for file picking/uploading via `WebChromeClient`.
