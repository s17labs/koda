# AGENTS.md

Guidance for AI coding agents (OpenCode, Claude Code, etc.) working in this repository.

## Project Overview

Koda is a clean, lightweight multi-tab text editor for Android: open/edit/save text files via the
Storage Access Framework, with unsaved-changes prompts, autosave, WakeLock support and a dark UI.

- Language/stack: Kotlin 1.8 (View-based UI — AppCompat + Material Components, no Compose), Storage Access Framework / DocumentFile
- Package/module: `com.s17labs.koda`
- Toolchain: minSdk 26, compile/target SDK 33, JVM target 1.8, Gradle wrapper 7.6.4 (Kotlin DSL), ABI splits for `armeabi-v7a` + `arm64-v8a` only (no universal APK)
- Author/maintainer: yungsamd17 (https://github.com/yungsamd17)

## Build & Verify

```bash
chmod +x gradlew && ./gradlew assembleDebug      # build debug APKs (per-ABI)
./gradlew testDebugUnitTest                      # unit tests (placeholder ExampleUnitTest sourceset)
./gradlew lint                                   # Android lint (not part of any workflow)
```

- CI (`.github/workflows/ci.yml`) runs `assembleDebug` (JDK 17) on every push to `main` and every
  PR, uploading the arm64-v8a debug APK as an artifact; the `build` check is required by branch protection.
  Manual workflows remain: `.github/workflows/debug.yml` builds a debug APK artifact,
  `.github/workflows/release.yml` builds release APKs, keeps only arm64-v8a/armeabi-v7a, zips them
  as `release-apks.zip`.
- Local sandboxes often lack the Android SDK/JDK or other toolchains — if builds can't run locally,
  rely on careful code review and let CI verify. Never skip updating tests when changing shared interfaces.

## Architecture

```
app/src/main/java/com/s17labs/koda/
  KodaApp.kt             # Application: init DebugLog + global uncaught-exception crash handler
  MainActivity.kt        # editor screen: tab bar, SAF open/save/save-as, TextWatcher dirty
                         # tracking, WakeLock, intent-based file opening, locale switching
  SettingsActivity.kt    # preferences: font family/size, line wrap, auto-save, open-last,
                         # default save folder (SAF), language
  DebugLog.kt            # in-app logging singleton: ring buffer (100 entries) + crash_log.txt in filesDir
  DebugLogActivity.kt    # screen that displays the captured log/crash file
  CustomAboutDialog.kt   # custom dialogs used instead of stock Material dialogs
  CustomConfirmDialog.kt #
  CustomOptionDialog.kt  #
  CustomTextInputDialog.kt #
  CustomToggleView.kt    # custom toggle switch view for settings rows
  model/
    OpenFile.kt          # tab model: id/name/path/content/originalContent/isModified/isNew,
                         # plus pipe-delimited toJson/fromJson used to persist "last file"
    MenuItem.kt          # menu item model for the main menu
```

Key patterns:

- All app state lives in SharedPreferences `"koda_prefs"` (language, `open_last`, `open_new`,
  `last_file`). The last session is restored by serializing one tab through `OpenFile.toJson/fromJson`.
- File I/O goes through the Storage Access Framework (`DocumentFile`, content URIs) — never raw
  `File` paths against external storage.
- Unsaved-change detection compares `OpenFile.content` against `originalContent` (`isModified`),
  driven by a `TextWatcher` on the shared editor; closing/switching modified tabs must prompt first.
- Logging goes through `DebugLog` (`DebugLog.i/w/crash(...)`), not `Log` directly; crashes are both
  logged to `crash_log.txt` and forwarded to the default uncaught-exception handler.
- Locale (English/Slovak) is applied manually at activity start via `applyLocale()` reading prefs —
  new user-visible strings need entries in both locales' resource files.

## UI Conventions

- Dark theme throughout; dialogs use square corners (0 dp radius) per project convention.
- Use the existing `Custom*Dialog` classes for confirmations, text input and options rather than
  inflating new stock dialogs, so styling stays consistent.

## Commit Messages

Format: `type(scope): short imperative summary` — lowercase after type, no trailing period.
Keep commits atomic — one logical change per commit.

| Type | Use for |
|---|---|
| `feat` | new user-facing feature |
| `fix` | bug fix |
| `refactor` | code change that neither fixes nor adds behavior |
| `style` | formatting/UI polish without logic change |
| `test` | adding or fixing tests |
| `docs` | documentation only |
| `chore` | build, deps, CI, tooling |
| `release` | version bump / release tagging |

Scope is a short area name for this project (e.g. `app`, `editor`, `settings`, `ci`).
Use plain `type:` only when a change genuinely spans everything (rare).

Examples:

```
feat(editor): add find and replace
fix(editor): keep keyboard open while typing
chore(ci): upload debug apk artifact
docs(readme): document commit message types
release: v0.1.1
```

## Agent Guardrails

- Never commit or push directly to `main`; all changes land through pull requests.
- Never open a PR unless the developer explicitly asks for it.
- One concern per change. If the description says "also", split it into another branch/PR.
- Do not commit secrets, keystores, or local-only files (e.g. `.and-code/`).
- When watching CI/bot feedback on your PRs: poll checks and comments newer than the last push,
  verify each bot finding against the source before "fixing" it, dismiss false positives with a
  written reason, and stop when checks are green on the latest commit.

## Pull Requests

All changes land on `main` through pull requests.

1. Create a branch off `main`: `<type>/<short-description>` (e.g. `feat/find-replace`, `fix/tab-restore`).
2. Commit there using the format from **Commit Messages**; keep commits atomic.
3. Push the branch and open a PR against `main`.

PR rules:

- One feature/fix per PR — small and focused beats large and thorough.
- Title follows the commit message format: `type(scope): short imperative summary`
  (e.g. `feat(editor): add find and replace`) — it becomes the squash-merge commit message.
- Body stays concise, following the PR template: what changed and why, bullet list of touched areas,
  evidence if applicable, testing checklist (tick before merge).
- UI changes must include clear before/after screenshots; motion/timing changes need a short video.
  Upload evidence directly to GitHub — never commit PR-only screenshots or asset files.
- End the body with an AI attribution line stating exactly which model and agent made the changes,
  in this exact format:

  ```
  Built with {model} in the {agent} harness.
  ```

  Example: `Built with ox-alpha in the OpenCode harness.`

- Do **not** put AI attribution in GitHub Release notes — releases stay clean.
- CI must pass before merging. (No automatic CI exists here — trigger `debug.yml` manually if asked.)

## Releases

1. Ensure version metadata is correct (`versionName` / `versionCode` in `app/build.gradle.kts`).
2. Run the `Release Build` workflow manually from GitHub Actions; it reads the version from
   `app/build.gradle.kts`, builds per-ABI release APKs and uploads `release-apks.zip` as an artifact.
3. Attach artifacts to a GitHub Release created on `main`.

## Gotchas

- `OpenFile.toJson/fromJson` is a naive pipe-delimited format (`name|path|content`), not real JSON:
  file content containing `|` gets truncated on restore because parsing keeps `parts[2]`. Handle with
  care when touching session restore; fix the format before building on it.
- ABI splits are enabled and x86/x86_64 outputs are intentionally deleted during release packaging —
  do not "fix" the missing universal APK.
- The dependency set is old (appcompat 1.2.0, Kotlin stdlib 1.8.10, JVM target 1.8); keep changes
  compatible instead of modernizing versions opportunistically.
