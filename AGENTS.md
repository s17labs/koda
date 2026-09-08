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

- CI (`.github/workflows/ci.yml`) runs `assembleDebug` + `testDebugUnitTest` (JDK 17) on every push to `main` and every
  PR, uploading the arm64-v8a debug APK as an artifact; the `build` check is required by branch protection.
  `debug.yml` remains a manual `assembleDebug` artifact builder (now with `wrapper-validation` + `setup-gradle` caching like CI/release).
  `release.yml` is now **tag-triggered** (`push: tags: ['v*']`, plus `workflow_dispatch`): it validates the Gradle wrapper,
  sets up JDK 17 + Android SDK, configures signing (secrets `ANDROID_KEYSTORE_B64`/`ANDROID_KEYSTORE_PASSWORD`/`ANDROID_KEY_ALIAS`/`ANDROID_KEY_PASSWORD` win, else the committed public `signing/release.keystore` — alias `koda`, password `koda-public`), builds `assembleDebug` + `assembleRelease` (`signingConfig` from `app/build.gradle.kts:39-71`), stages `Koda.<version>.arm64-v8a.apk` + `Koda.<version>.armeabi-v7a.apk` and publishes them directly to the GitHub Release via `softprops/action-gh-release`. No `release-apks.zip` or `checksums.txt` is produced.
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
- CI must pass before merging.

## Releases

1. Ensure version metadata is correct (`versionName` / `versionCode` in `app/build.gradle.kts`) — bump for each release.
2. Merge the version bump to `main` via PR, then cut a release by pushing a tag: `git tag vX.Y.Z && git push origin vX.Y.Z` (or use `workflow_dispatch` — it falls back to `versionName` from `app/build.gradle.kts`). The `Release` workflow (`.github/workflows/release.yml`) validates the wrapper, sets up JDK 17 + Android SDK + Gradle cache, configures signing (secrets `ANDROID_KEYSTORE_B64` etc. win, else the committed public `signing/release.keystore` — alias `koda`, password `koda-public`), builds `assembleDebug` + `assembleRelease` (`signingConfig` from `app/build.gradle.kts:39-71`), stages `Koda.<version>.arm64-v8a.apk` + `Koda.<version>.armeabi-v7a.apk` and publishes them directly to the GitHub Release via `softprops/action-gh-release` (with `generate_release_notes: true`). No `release-apks.zip` or `checksums.txt` is produced.
3. Verify: `gh release view vX.Y.Z --json assets --jq '.assets[].name'` should show the two signed APKs; `python -c "b'APK Sig Block 42' in open('Koda.X.Y.Z.arm64-v8a.apk','rb').read()"` should be `True` (signed). Install on device to confirm.

## Gotchas

- `OpenFile.toJson/fromJson` is pipe-delimited (`name|path|content`) with escaping (`\|` / `\\`) and backward-compatible re-joining of `parts[2..]` (`app/src/main/java/com/s17labs/koda/model/OpenFile.kt:33-76`), not real JSON. Content containing `|` is now preserved; still handle with care when touching session restore and keep escaping in sync.
- ABI splits are enabled and x86/x86_64 outputs are intentionally deleted during release packaging (`app/build.gradle.kts:74-79`, `.github/workflows/release.yml:111-113`) — do not "fix" the missing universal APK.
- Release APKs are signed via `signing/release.keystore` (PKCS12, alias `koda`, password `koda-public`, committed publicly like `s17labs/pebbledo`) with optional override via `keystore.properties` / `ANDROID_KEYSTORE_B64` secrets (`app/build.gradle.kts:13-57`). Switching signing keys later requires uninstall/reinstall for users.
- The dependency set is old (appcompat 1.2.0, Kotlin stdlib 1.8.10, JVM target 1.8); keep changes compatible instead of modernizing versions opportunistically.
