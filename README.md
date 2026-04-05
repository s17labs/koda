# Koda

A clean and lightweight Android text editor built with Kotlin.

## ✨ Features

### File Management
- 📄 Create, open, and edit text files
- 💾 Save files with auto-incrementing names (koda-file_1.txt, etc.)
- 💾 Save As with custom filename input
- 📂 Open single or multiple files at once
- 📁 Customizable default save folder
- 🔄 File exists confirmation when replacing files
- 📝 Unsaved changes detection with save prompts

### Interface
- 🗂️ Multiple tabs for working with several files simultaneously
- 📱 Start page for quick access to common actions
- ⚫ Dark theme with custom-styled dialogs
- 📜 Scrollable content for all screen orientations

### Settings
- ⚙️ Auto Save - automatically save files when leaving the app
- 🔄 Open New File - start with a new empty file on launch
- 🔄 Open Last File - continue editing the last opened file
- 📦 Wrap Lines - toggle line wrapping
- 🔒 WakeLock - prevent device from sleeping while editing
- 📂 Save Folder - select a default folder for new files
- 🔤 Font - choose between Monospace, Serif, or Sans Serif
- 📏 Font Size - select from XS, S, M, ML, L, or XL
- 🌍 Language - switch between English and Slovak

### Developer Tools
- 📋 Debug Log - view and export application logs

## 🛠 Tech Stack

| Aspect | Details |
|--------|---------|
| SDK | compileSdk 35, minSdk 26 |
| Language | Kotlin 1.8.10 |
| Build | Gradle 7.4.2 with Kotlin DSL |

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/s17labs/koda.git
   ```
2. Open the project in Android Studio
3. Build & run on an emulator or device

## 📁 Project Structure

```
app/
  ├── java/com/s17labs/koda
  │   ├── model/         # Data models
  │   ├── MainActivity.kt     # Main editor screen
  │   ├── SettingsActivity.kt # Settings screen
  │   ├── Custom*.kt         # Custom UI components
  │   └── DebugLog*.kt       # Debug logging
  ├── res/
  │   ├── layout/        # XML layouts
  │   ├── values/        # Strings, colors, themes
  │   ├── values-sk/     # Slovak translations
  │   └── drawable/      # Icons and drawables
  └── AndroidManifest.xml
```

## 🤝 Contributing

Contributions are welcome! Please read the [Contributing Guide](CONTRIBUTING.md) for details on how to add translations and other improvements.

## 📄 License

This project is licensed under the MIT License.
