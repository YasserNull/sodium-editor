# Sodium Editor

A high-performance, feature-rich code editor for Android, designed to handle large files efficiently using streaming techniques.

## Project Overview

- **Type:** Android Project (Java-only)
- **Architecture:** Multi-module Gradle project
  - `:app`: A simple wrapper application demonstrating the editor's capabilities.
  - `:sodium-editor`: The core library containing the editor implementation.
- **Key Technologies:**
  - Android SDK (Java 17)
  - Gradle (KTS and Groovy)
  - JUnit 4 & Robolectric for testing

## Key Features

- **Large File Support:** Uses `RandomAccessFile` and `FileChannel` for streaming content without loading the entire file into memory.
- **Syntax Highlighting:** Support for various languages (inferred from `core.highlight` package).
- **Code Intelligence:** Autocompletion, code folding, and bracket matching.
- **UI/UX:** Smooth scrolling (with `OverScroller`), zooming, and customizable line numbers.
- **Search & Selection:** Integrated search functionality and text selection.

## Building and Running

Ensure you have the Android SDK and Gradle installed (or use the provided `gradlew` wrapper).

### Core Commands

- **Build:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Install & Run:**
  ```bash
  ./gradlew installDebug
  ```
- **Run Unit Tests:**
  ```bash
  ./gradlew test
  ```
- **Run Robolectric Tests:**
  Some tests in `:sodium-editor` require Robolectric and are disabled by default. Run them with:
  ```bash
  ./gradlew :sodium-editor:testDebugUnitTest -PsodiumEditorRunRobolectric=true
  ```

## Development Conventions

- **Language:** The project is strictly Java-only. Kotlin standard libraries are explicitly excluded in `app/build.gradle.kts`.
- **Formatting:** Adhere to standard Java/Android coding conventions.
- **Testing:** New features should include unit tests. Use Robolectric for tests that require Android components.
- **Large File Handling:** When modifying the core editor, ensure that changes do not compromise the streaming architecture for large files.
