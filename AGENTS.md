# Repository Guidelines

## Project Structure & Module Organization

This is an Android Gradle project named `simple-sodium-editor`.

- `app/` contains the sample Android application used to run the editor.
- `sodium-editor/` contains the reusable editor library.
- `sodium-editor/src/main/java/com/yn/sodiumeditor/` contains production Java code.
- `sodium-editor/src/test/java/com/yn/sodiumeditor/test/` contains JVM unit and guard tests.
- `sodium-editor/src/androidTest/` contains instrumentation tests.
- Root Gradle files are `settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties`.

Avoid editing generated files under `build/`.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root.

```sh
./gradlew :app:assembleDebug
```

Builds the debug APK for the sample app.

```sh
./gradlew :sodium-editor:assembleDebug
```

Builds the editor library.

```sh
./gradlew :sodium-editor:testDebugUnitTest
```

Runs default JVM unit tests. Some Robolectric-heavy tests are excluded by default because native library loading can fail in Termux-like environments.

```sh
./gradlew :sodium-editor:testDebugUnitTest -PsodiumEditorRunRobolectric=true
```

Runs the full unit test set, including excluded Robolectric tests.

```sh
./gradlew clean
```

Removes build outputs for all modules.

## Coding Style & Naming Conventions

Production code is Java 17 for Android. Follow the existing package layout and keep changes scoped to the affected subsystem, such as `core/selection`, `renderer`, `input/events`, or `io`.

Use 2-space indentation where existing files use it. Keep class names in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer existing helper APIs over duplicating editor logic.

## Testing Guidelines

Tests use JUnit 4, Robolectric, and AndroidX Test. Name regression tests by behavior, for example `SelectionHandleReleaseGuardTest` or `TypingScrollRegressionTest`.

For narrow rendering or state regressions, source-level guard tests are acceptable when Android runtime setup is unreliable. Add tests under `sodium-editor/src/test/java/com/yn/sodiumeditor/test/`.

## Commit & Pull Request Guidelines

Recent history uses short fix-oriented commit messages, for example `fix selection bug` and `fix scroll bug`. Keep commits focused and describe the user-visible behavior fixed.

Pull requests should include:

- A concise summary of the issue and fix.
- Tests added or updated.
- Manual verification notes for editor interactions.
- Screenshots or logs when changing rendering, selection, cursor, or scrolling behavior.

## Agent-Specific Instructions

Preserve user changes in the working tree. Do not revert unrelated files. Prefer small patches and verify with focused Gradle tasks when possible.
