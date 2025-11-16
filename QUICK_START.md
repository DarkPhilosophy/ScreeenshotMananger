# Screenshot Manager Quick Start

This guide will get you up and running with the Screenshot Manager Android app quickly.

## ⚡ Fastest Path to APK (Using Android Studio)

### Step 1: Install Android Studio (15-20 minutes)
1. Download Android Studio from: https://developer.android.com/studio
2. Run the installer and follow the setup wizard
3. Wait for initial SDK components to download

### Step 2: Open Project (2-3 minutes)
1. Launch Android Studio
2. Click "Open" and select the `screenshot-manager` folder
3. Wait for Gradle sync to complete (may take 5-10 minutes first time)

### Step 3: Build APK (1-2 minutes)
1. Click **Build** → **Build Bundle(s)/APK(s)** → **Build APK(s)**
2. Wait for build completion
3. Click "locate" in the notification to find `app-debug.apk`

**Total Time: ~20-25 minutes**

---

## 🚀 Alternative: Command Line Build

### Prerequisites
- JDK 17+ installed
- Android SDK installed

### Quick Commands
```bash
cd screenshot-manager

# Windows
echo sdk.dir=C:\\Users\\%USERNAME%\\AppData\\Local\\Android\\Sdk > local.properties
.\\gradlew.bat assembleDebug

# macOS/Linux
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
```

---

## 📱 Testing the App

### Android Emulator
1. In Android Studio: **Tools** → **Device Manager**
2. Create device (Pixel 5, API 35 recommended)
3. Click green **Run** button (▶️)

### Physical Device
1. Enable Developer Options: Settings → About → Tap "Build Number" 7x
2. Enable USB Debugging: Settings → System → Developer Options
3. Connect via USB and select device in Android Studio

### Manual Install
1. Transfer `app-debug.apk` to device
2. Open file and install (enable "Install unknown apps" if needed)

---

## 🎯 What You Should See

When the app launches:
- **Main Screen**: Tabbed interface (Marked/Kept/All screenshots)
- **Service Toggle**: Switch to enable/disable monitoring
- **Settings FAB**: Floating button for configuration
- **Empty State**: "No screenshots found" if no screenshots yet

### First Run Setup
1. Grant storage permission (for reading screenshots)
2. Grant notification permission (for deletion timers)
3. Grant "Display over other apps" permission (for manual mode overlay)
4. Choose Manual or Automatic mode in settings

---

## ❓ Common Issues & Troubleshooting

### Build Issues
- **"SDK location not found"**: Create `local.properties` with correct SDK path
- **"Gradle sync failed"**: Check internet, invalidate caches, retry
- **"Build failed"**: Ensure JDK 17+, clean and rebuild

### Runtime Issues
- **"App won't install"**: Enable "Install unknown apps", check Android 7.0+
- **"Permissions denied"**: Go to app settings and grant all permissions
- **"Overlay not working"**: Enable "Display over other apps" in system settings

### App Functionality
- **No screenshots detected**: Check storage permissions and screenshot folder
- **Notifications not showing**: Enable notification permission
- **Manual overlay not appearing**: Verify overlay permission and Manual mode

---

## 🔄 Making Changes

### Customize Settings
1. Edit `app/src/main/res/values/strings.xml` for text
2. Edit `app/src/main/res/values/colors.xml` for colors
3. Modify settings in the drawer for new preferences

### Add Features
1. Check `MainActivity.kt` for UI changes
2. Update `ScreenshotMonitorService.kt` for detection logic
3. Modify `OverlayService.kt` for manual mode features

---

## 📊 Build Variants

### Debug APK (Development)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (Production)
```bash
./gradlew assembleRelease
# Requires signing config for distribution
```

---

## 🎓 Next Steps

1. Read the full **README.md** for detailed feature documentation
2. Explore the code structure in `PROJECT_STRUCTURE.md`
3. Customize settings and behavior to your needs
4. Check **CONTRIBUTING.md** for development guidelines

---

## 📞 Need Help?

- Check **README.md** for detailed troubleshooting
- Review **Debug Console** in app settings for logs
- Open GitHub Issues for bugs/features
- Ensure device meets requirements (Android 7.0+)

---

**Happy Screenshot Managing! 📸**
