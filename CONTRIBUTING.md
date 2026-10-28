# Contributing to Koda

## Project structure

```text
app/src/
  main/
    java/com/s17labs/koda/
      model/                 # File and menu models
      MainActivity.kt        # Editor screen and file workflows
      EditorScrollView.kt    # Two-axis editor viewport
      SelectionEditText.kt   # Selection handles and continuous edge scrolling
      SettingsActivity.kt    # Preferences and locale selection
      Custom*Dialog.kt       # Shared custom dialogs
      DialogActionLayout.kt  # Responsive dialog action placement
      CustomToggleView.kt    # Settings switches
      DebugLog*.kt           # Debug logging
    res/
      layout/                # Screen and dialog layouts
      values/                # English strings, arrays, colors, styles and themes
      values-sk/             # Slovak translations
      drawable/              # Icons, radio indicators and button backgrounds
    AndroidManifest.xml
  test/                      # JVM tests
  androidTest/               # Device tests and test-only manifest
```

## Build and test

Use JDK 17, Android SDK 33 and the included Gradle 7.6.4 wrapper. The app uses Kotlin 1.8.10 and supports Android API 26 and newer.

```bash
./gradlew assembleDebug testDebugUnitTest
# With a compatible emulator or device connected:
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

Debug APKs are written under `app/build/outputs/apk/debug/`. The repository enables ABI-specific outputs and does not configure a separate debug application ID by default. Installing a normal debug build may conflict with an installed release; use a separate application ID when preparing a side-by-side test build.

CI builds debug APKs and runs JVM tests. Device tests and lint require separate execution. Lint currently reports existing issues with the menu tint attribute, the Slovak `file_exists` format string and two app-link URL declarations; distinguish these from regressions in your changes.

`EditorScrollViewTest` covers scrolling on both axes, diagonal gestures, wrapping, viewport/font changes, caret visibility, pointer switching, fling reset, mouse input, accessibility and saved state. `SelectionDragTest` uses real touch gestures against the editor to cover continuous handle dragging, initial long-press drags in both directions, keyboard resizing, reaching the final character, viewport stability after release and independently dragging both handles on short selections near either screen edge. When changing the editor viewport or selection handling, run the device tests as well as JVM tests.

For UI changes, check English and Slovak, narrow screens, larger text, keyboard resizing and both Android light/dark system modes. Koda retains its dark palette in both modes. Confirm that long dialog actions fit or stack, only one action is primary in a two-button dialog, and selected radio indicators keep the same outer size as unselected ones. See [editor and settings](docs/editor-and-settings.md) for expected behavior.

## Code and review guidelines

Follow existing Kotlin conventions and reuse the custom dialogs, `DialogActionLayout` and shared `KodaDialogButton` styles. Keep square corners and press feedback, consistent spacing and the settings background. Use `EditorScrollView` as the editor viewport rather than nesting horizontal and vertical scroll containers.

Keep commits focused and use `type(scope): short imperative summary`, for example `fix(editor): preserve caret visibility` or `docs(app): describe settings controls`. Open a PR against `main`; include validation results and before/after UI evidence uploaded directly to GitHub. Do not commit APKs or review-only screenshots. See [AGENTS.md](AGENTS.md) for repository-specific review and release conventions.

## Translations and preference compatibility

English strings live in `app/src/main/res/values/strings.xml`; Slovak strings live in `values-sk/strings.xml`. Add new user-visible strings to both, preserving resource names and format placeholders. The `font_size_labels` array references localized strings, so its order must match the stored font-size keys: `XS`, `S`, `M`, `ML`, `L`, `XL`.

Language preference keys and display labels are separate. The stored key `Slovak` is displayed as **Slovak (Slovenčina)**. Do not rename existing stored keys when changing visible labels.

To add a language:

1. Create `app/src/main/res/values-xx/strings.xml` for its Android locale qualifier and translate the English resources, including font-size labels.
2. Add its stable preference key to `languages` and corresponding display label to `languageLabels` in `SettingsActivity`. Keep the lists in the same order.
3. Add the locale mapping in `applyLocale()` in both `SettingsActivity` and `MainActivity`, preserving English and Slovak mappings.
4. Build, select the language in Settings, inspect dialogs and labels for clipping, and verify the choice persists after restart. Check format placeholders and existing saved font-size selections.
