# Screenshot Manager Project Structure

This document explains the organization and purpose of each file and folder in the Screenshot
Manager Android project.

## 📁 Root Directory Structure

```
Screenshot Manager/
├── app/                          # Main application module
├── core/                         # Shared data layer module
├── buildSrc/                     # Build configuration source
├── gradle/                       # Gradle wrapper files
├── .github/                      # GitHub configuration (workflows, templates)
├── build.gradle.kts             # Root build configuration
├── settings.gradle.kts          # Project settings
├── gradle.properties            # Gradle properties
├── local.properties.example     # Example SDK configuration
├── .gitignore                   # Git ignore rules
├── README.md                    # Main documentation
├── QUICK_START.md               # Quick start guide
├── CONTRIBUTING.md              # Contribution guidelines
├── LICENSE                      # MIT License
└── PROJECT_STRUCTURE.md        # This file
```

---

## 📱 App Module Structure

```
app/
├── src/
│   ├── main/
│   │   ├── kotlin/com/araara/screenapp/    # Kotlin source code
│   │   │   ├── data/             # Data layer (Room, Preferences) - app-specific
│   │   │   ├── di/               # Dependency injection modules
│   │   │   ├── events/           # App events
│   │   │   ├── receiver/         # Broadcast receivers
│   │   │   ├── service/          # Background services
│   │   │   ├── ui/               # UI layer (Activities, Composables, ViewModels)
│   │   │   │   ├── components/    # Reusable UI components
│   │   │   ├── util/             # Utilities (Notification, Time, etc.)
│   │   │   ├── worker/           # WorkManager workers
│   │   │   ├── ScreenshotApp.kt  # Application class
│   │   │   └── MainActivity.kt   # Main activity
│   │   ├── res/                  # Android resources
│   │   └── AndroidManifest.xml   # App manifest
│   └── androidTest/              # Instrumented tests
├── build.gradle.kts             # App build configuration
└── proguard-rules.pro           # Code obfuscation rules
```

---

## 🔧 Core Module Structure

```
core/
├── src/
│   ├── main/
│   │   ├── kotlin/com/araara/screenapp/    # Shared data classes
│   │   │   ├── data/             # Data layer (Room, Preferences)
│   │   │   │   ├── dao/          # Data access objects
│   │   │   │   ├── database/     # Room database setup
│   │   │   │   ├── entity/       # Data entities
│   │   │   │   ├── preferences/  # DataStore preferences
│   │   │   │   └── repository/   # Repository interfaces
│   │   │   ├── di/               # Dependency injection modules
│   │   │   ├── events/           # Shared events
│   │   │   └── util/             # Shared utilities
│   │   └── AndroidManifest.xml   # Core manifest
├── build.gradle.kts             # Core build configuration
└── proguard-rules.pro           # Code obfuscation rules
```

---

## 🔧 Configuration Files

### Root Level

#### `build.gradle.kts`
**Purpose**: Root-level build configuration for the entire project.

**Key Contents**:
- Plugin versions (Android Gradle Plugin, Kotlin)
- Common build configurations
- Clean task definition

**When to Edit**:
- Updating Kotlin version
- Updating Android Gradle Plugin version
- Adding project-wide repositories

---

#### `settings.gradle.kts`
**Purpose**: Defines project structure and module inclusion.

**Key Contents**:
- Repository configurations (Google, Maven Central)
- Module inclusion (`:app`)
- Plugin management

**When to Edit**:
- Adding new modules
- Changing repository sources
- Configuring plugin repositories

---

#### `gradle.properties`
**Purpose**: Project-wide Gradle configuration properties.

**Key Contents**:
- JVM memory settings
- AndroidX enablement
- Kotlin code style
- Build optimization flags

**When to Edit**:
- Adjusting build performance
- Enabling/disabling features
- Memory allocation issues

---

#### `local.properties` (Not in version control)
**Purpose**: Local machine-specific configuration.

**Key Contents**:
- Android SDK location
- NDK location (if needed)
- Machine-specific paths

**When to Create**:
- First time setup
- After cloning repository
- When SDK location changes

**Example**:
```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

---

#### `.gitignore`
**Purpose**: Specifies files and folders to exclude from version control.

**Key Exclusions**:
- Build outputs (`build/`, `*.apk`)
- IDE files (`.idea/`, `*.iml`)
- Local configuration (`local.properties`)
- Generated files

---

### App Module Level

#### `app/build.gradle.kts`
**Purpose**: App-specific build configuration.

**Key Sections**:

1. **Plugins**:
   ```kotlin
   plugins {
       id("com.android.application")
       id("org.jetbrains.kotlin.android")
   }
   ```

2. **Android Configuration**:
   - `namespace`: App package name
   - `compileSdk`: SDK version to compile against
   - `defaultConfig`: App metadata and version info
   - `buildTypes`: Debug and release configurations
   - `compileOptions`: Java compatibility settings
   - `kotlinOptions`: Kotlin compiler settings

3. **Dependencies**:
   - AndroidX libraries
   - Material Design components
   - Testing frameworks

**When to Edit**:
- Changing app version
- Adding new dependencies
- Modifying build configurations
- Adjusting SDK versions

---

#### `app/proguard-rules.pro`
**Purpose**: Code shrinking and obfuscation rules for release builds.

**Key Contents**:
- Keep rules for important classes
- Obfuscation exceptions
- Optimization settings

**When to Edit**:
- Adding libraries that need special keep rules
- Fixing crashes in release builds
- Customizing obfuscation

---

## 📄 Source Code Files

### Core Classes

#### `ScreenshotApp.kt`
**Purpose**: Application class managing global state and initialization.

**Key Responsibilities**:
- Initialize Hilt dependency injection
- Setup DataStore preferences
- Create notification channels
- Handle application-level configurations

#### `MainActivity.kt`
**Purpose**: Main activity with tabbed screenshot browser.

**Key Features**:
- Tab layout for Marked/Kept/All screenshots
- Service enable/disable toggle
- Paged RecyclerView with screenshot list
- Settings FAB navigation
- Permission handling and requests
- Welcome dialog for first launch

#### Settings in Drawer (MenuContent.kt)
**Purpose**: Configuration screen for app settings.

**Features**:
- Manual vs Automatic mode toggle
- Deletion time selection
- Custom folder configuration
- Notification preferences
- Debug console access
- Language selection

#### `DebugConsoleActivity.kt`
**Purpose**: Developer tools for logging and troubleshooting.

**Features**:
- Real-time log display with filtering
- Log export functionality
- Log clearing capabilities

### Services

#### `ScreenshotMonitorService.kt`
**Purpose**: Background service monitoring for new screenshots.

**Key Functions**:
- ContentObserver for MediaStore changes
- Screenshot detection and processing
- Mode-based handling (Manual/Automatic)
- Existing screenshot scanning on startup

#### `OverlayService.kt`
**Purpose**: System overlay for manual mode screenshot decisions.

**Features**:
- Full-screen overlay with action buttons
- Animated show/hide transitions
- Keep or set deletion timer options
- Permission-aware operation

### Data Layer

#### Database Classes
- `ScreenshotDatabase.kt`: Room database setup
- `ScreenshotDao.kt`: Data access operations
- `ScreenshotRepository.kt`: Repository pattern implementation
- `Screenshot.kt`: Entity model
- `AppPreferences.kt`: DataStore preferences wrapper

#### Workers
- `ScreenshotDeletionWorker.kt`: Scheduled screenshot deletion

### Utilities
- `NotificationHelper.kt`: Notification creation and management
- `DebugLogger.kt`: Custom logging system
- `WorkManagerScheduler.kt`: Background task scheduling
- `TimeUtils.kt`: Time formatting utilities
- `PermissionUtils.kt`: Permission checking helpers

### UI Components
- `ScreenshotAdapter.kt`: RecyclerView adapter for screenshots
- `LogAdapter.kt`: RecyclerView adapter for debug logs

### Receivers
- `BootReceiver.kt`: Restarts service after device boot
- `NotificationActionReceiver.kt`: Handles notification button actions

### Dependency Injection
- `AppModule.kt`: Hilt module for providing dependencies
- `ReceiverEntryPoint.kt`: Hilt entry point for receivers

### Events
- `AppEvents.kt`: Shared event system for communication between components

---

## 🎨 Resource Files

### Layout Files

#### `app/src/main/res/layout/activity_main.xml`

**Purpose**: Defines the UI layout for MainActivity.

**Structure**:
```xml
<ConstraintLayout>
    <MaterialButton id="pressButton" />  <!-- Centered button -->
    <TextView id="attributionText" />    <!-- Bottom-right text -->
</ConstraintLayout>
```

**Key Elements**:
- **ConstraintLayout**: Flexible positioning system
- **MaterialButton**: Main interactive element
- **TextView**: Attribution display

**When to Edit**:
- Changing UI layout
- Adding new UI elements
- Modifying button appearance

---

### Value Files

#### `app/src/main/res/values/strings.xml`

**Purpose**: Stores all text strings used in the app.

**Contents**:
```xml
<resources>
    <string name="app_name">Ko</string>
    <string name="button_press">PRESS</string>
    <string name="attribution">by Adalbert Alexandru</string>
</resources>
```

**Benefits**:
- Easy localization
- Centralized text management
- No hardcoded strings

**When to Edit**:
- Changing any displayed text
- Adding new text elements
- Creating translations

---

#### `app/src/main/res/values/colors.xml`

**Purpose**: Defines color palette for the app.

**Contents**:
- Primary colors (button, theme)
- Background colors
- Text colors
- Status bar colors

**When to Edit**:
- Changing app color scheme
- Adjusting theme colors
- Adding new colors

---

#### `app/src/main/res/values/themes.xml`

**Purpose**: Defines the light theme for the app.

**Key Attributes**:
- Material Design 3 base theme
- Color mappings
- Status bar configuration

**When to Edit**:
- Changing overall app theme
- Modifying Material Design colors
- Adjusting system UI appearance

---

#### `app/src/main/res/values-night/themes.xml`

**Purpose**: Defines the dark theme for the app.

**Behavior**: Automatically applied when device is in dark mode.

**When to Edit**:
- Customizing dark mode appearance
- Adjusting dark theme colors

---

### Drawable Files

#### `app/src/main/res/drawable/ic_launcher_foreground.xml`

**Purpose**: Vector drawable for app icon foreground.

**Format**: XML vector graphics

**When to Edit**:
- Changing app icon design
- Creating custom launcher icon

---

### Mipmap Files

#### `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
#### `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Purpose**: Adaptive icon configuration for Android 8.0+.

**Components**:
- Background layer
- Foreground layer

**Benefits**:
- Adapts to different device icon shapes
- Supports dynamic theming

---

## 📋 Manifest File

### `app/src/main/AndroidManifest.xml`

**Purpose**: Declares app components and configuration.

**Key Sections**:

1. **Application Tag**:
   - App icon
   - App name
   - Theme
   - Backup settings

2. **Activity Declaration**:
   - MainActivity configuration
   - Launch intent filter
   - Screen orientation lock
   - Configuration change handling

**When to Edit**:
- Adding new activities
- Declaring permissions
- Changing app metadata
- Adding services or receivers

---

## 🔨 Gradle Wrapper

### `gradle/wrapper/gradle-wrapper.properties`

**Purpose**: Specifies Gradle version for the project.

**Key Property**:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

**Benefits**:
- Ensures consistent Gradle version across machines
- No need to install Gradle separately
- Automatic download on first build

---

## 📊 Build Output Structure

After building, the following structure is created:

```
app/build/
├── outputs/
│   └── apk/
│       ├── debug/
│       │   └── app-debug.apk           # Debug APK
│       └── release/
│           └── app-release.apk         # Release APK
├── intermediates/                       # Intermediate build files
└── tmp/                                 # Temporary build files
```

---

## 🎯 File Relationships

```
MainActivity.kt
    ↓ (inflates)
activity_main.xml
    ↓ (references)
strings.xml, colors.xml, themes.xml
    ↓ (styled by)
themes.xml
    ↓ (configured in)
AndroidManifest.xml
    ↓ (built by)
build.gradle.kts
```

---

## 📝 Best Practices

### File Organization
- Keep related files together
- Use meaningful names
- Follow Android naming conventions

### Resource Naming
- **Layouts**: `activity_*.xml`, `fragment_*.xml`
- **IDs**: `camelCase` (e.g., `pressButton`)
- **Strings**: `snake_case` (e.g., `button_press`)
- **Colors**: `snake_case` (e.g., `primary_color`)

### Code Organization
- One class per file
- Package by feature (for larger apps)
- Keep activities focused and simple

---

## 🔄 Common Modifications

### Adding a New Screen
1. Create new Activity in `kotlin/com/ko/app/`
2. Create layout XML in `res/layout/`
3. Declare activity in `AndroidManifest.xml`
4. Add navigation logic in MainActivity

### Adding Dependencies
1. Open `app/build.gradle.kts`
2. Add dependency in `dependencies` block
3. Sync Gradle
4. Import in Kotlin files

### Changing App Icon
1. Replace files in `res/mipmap-*` folders
2. Update `ic_launcher_foreground.xml`
3. Update `ic_launcher_background.xml`
4. Rebuild app

---

## 📚 Further Reading

- [Android Project Structure](https://developer.android.com/studio/projects)
- [Gradle Build Configuration](https://developer.android.com/build)
- [Android Resources](https://developer.android.com/guide/topics/resources/providing-resources)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)

---

**Last Updated**: 2025-11-03

