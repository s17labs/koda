# Contributing to Koda

## Project Structure

```
app/
  ├── java/com/s17labs/koda/
  │   ├── model/              # Data models
  │   ├── MainActivity.kt     # Main editor screen
  │   ├── SettingsActivity.kt # Settings screen
  │   ├── Custom*.kt          # Custom UI components
  │   └── DebugLog*.kt        # Debug logging
  ├── res/
  │   ├── layout/             # XML layouts
  │   ├── values/             # Strings, colors, themes
  │   ├── values-sk/          # Slovak translations
  │   └── drawable/           # Icons and drawables
  └── AndroidManifest.xml
```

## Tech Stack

| Aspect | Details |
|--------|---------|
| Language | Kotlin 1.8.10 |
| SDK | compileSdk 35, minSdk 26 |
| Build | Gradle 7.4.2 with Kotlin DSL |

## General Guidelines

- **Commit Messages**: Use clear, concise commit messages that describe what changed and why. Follow the format:
  - `feat: add new feature` for new features
  - `fix: resolve issue with...` for bug fixes
  - `refactor: restructure...` for code refactoring
  - `docs: update README` for documentation changes
  - `chore: update dependencies` for maintenance tasks

- **Code Style**: Follow Kotlin conventions used in the project. Use meaningful variable and function names.

- **Testing**: Test your changes thoroughly before submitting. For language additions, verify all UI elements display correctly.

## Adding New Languages

To add a new language to Koda, you need to make changes in three places:

### 1. Update SettingsActivity.kt

Add the language to the `languages` list and handle the locale:

```kotlin
// Add to languages list (around line 39)
private val languages = listOf("English", "French", "German", "Japanese", "YourLanguage")

// Add to applyLocale() function (around line 57)
private fun applyLocale() {
    val language = prefs.getString("language", "English")
    val locale = when (language) {
        "French" -> Locale("fr")
        "German" -> Locale("de")
        "Japanese" -> Locale("ja")
        "YourLanguage" -> Locale("your_language_code")  // e.g., "pt" for Portuguese
        else -> Locale.ENGLISH
    }
    // ... rest of function
}
```

### 2. Update MainActivity.kt

Add the same locale mapping in `applyLocale()`:

```kotlin
// Around line 154
private fun applyLocale() {
    val language = prefs.getString("language", "English")
    val locale = when (language) {
        "French" -> Locale("fr")
        "German" -> Locale("de")
        "Japanese" -> Locale("ja")
        "YourLanguage" -> Locale("your_language_code")
        else -> Locale.ENGLISH
    }
    // ... rest of function
}
```

### 3. Create Language Resource Directory

Create a new directory in `app/src/main/res/` following this pattern:

```
values-xx/
```

Where `xx` is the [ISO 639-1 language code](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes). For example:
- `values-fr` for French
- `values-de` for German  
- `values-ja` for Japanese
- `values-pt` for Portuguese

Inside this directory, create a `strings.xml` file with your translations. You can copy the English `strings.xml` and translate the values.

### Example: Creating French translations

1. Create directory: `app/src/main/res/values-fr/`
2. Create file: `app/src/main/res/values-fr/strings.xml`
3. Translate strings (example):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Koda</string>
    <string name="start_typing">Commencez à écrire…</string>
    <string name="menu_new">Nouveau</string>
    <string name="menu_open">Ouvrir</string>
    <string name="menu_save">Enregistrer</string>
    <string name="menu_settings">Paramètres</string>
    <!-- Continue translating all strings -->
</resources>
```

## Testing

After adding a new language:
1. Build and run the app
2. Go to Settings
3. Change language to your new language
4. Verify all UI elements display correctly
5. Check that the app persists the language after restart
