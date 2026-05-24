# AGENTS.md

# Sodium Editor Agent Rules

This repository contains a file-backed Android code editor named `simple-sodium-editor`.

The editor does NOT load entire files into RAM. Most editor operations work directly on file-backed storage and streamed text access.

Agents must understand this architecture before modifying code.

---

# Core Mission

Your job is to:

1. Detect logical bugs.
2. Reproduce glitches reliably.
3. Create failing tests FIRST.
4. Verify failure is real.
5. Fix root cause.
6. Verify tests pass after fix.
7. Prevent regressions.

Do NOT blindly patch symptoms.

---

# Mandatory Debugging Workflow

When a user reports a bug:

## STEP 1 — Understand Expected Behavior

Describe:

- expected behavior
- actual behavior
- affected subsystem
- possible state divergence

Never assume behavior is correct because no crash occurs.

---

## STEP 2 — Reproduce The Bug

Always attempt reproduction first.

Create:
- unit test
- regression test
- guard test
- instrumentation test

Choose the smallest reliable reproduction possible.

Bug reproduction is considered SUCCESS.

If the bug cannot be reproduced:
- add targeted logs
- inspect state transitions
- inspect async ordering
- inspect file-backed state sync

Do NOT immediately patch code without reproduction.

---

## STEP 3 — Create FAILING TEST FIRST

Before fixing:

Create a test that FAILS because of the bug.

The test must:
- clearly expose incorrect behavior
- fail consistently
- isolate the subsystem
- avoid unrelated editor systems

Preferred locations:

```text
sodium-editor/src/test/java/com/yn/sodiumeditor/test/
```

Naming examples:

```text
FileBackedCursorRegressionTest
UndoStateCorruptionTest
LargeFileSelectionRegressionTest
StreamingLexerStateTest
```

A failing regression test is REQUIRED before major fixes.

---

## STEP 4 — Investigate Root Cause

Trace:

- state mutations
- cursor movement
- selection state
- file offsets
- line cache state
- rendering invalidation
- async ordering
- IO synchronization

Focus on:
- off-by-one bugs
- stale cache usage
- invalid offsets
- race conditions
- desync between renderer and file state
- partial file reads
- chunk boundary bugs
- UTF-8 boundary corruption
- incorrect incremental parsing

Do NOT stop at surface symptoms.

---

## STEP 5 — Apply Minimal Fix

Rules:

- prefer minimal patch
- preserve architecture
- avoid unnecessary refactors
- avoid introducing RAM-heavy logic
- preserve streaming/file-backed design
- do not replace efficient systems with full-memory buffers

Do NOT:
- rewrite unrelated systems
- add hacks hiding invalid state
- bypass tests

---

## STEP 6 — Verify Fix

After fixing:

Run:

```sh
./gradlew :sodium-editor:testDebugUnitTest
```

If needed:

```sh
./gradlew :sodium-editor:testDebugUnitTest -PsodiumEditorRunRobolectric=true
```

Verify:
- failing test now passes
- nearby systems still behave correctly
- no regression introduced

---

# File-Backed Editor Rules

The editor is NOT a normal RAM-backed text editor.

Always assume:

- file content may be partially loaded
- offsets may reference file positions directly
- line mapping may be cached
- rendering may depend on streamed chunks
- edits may invalidate chunk metadata
- large files must remain efficient

Never introduce:
- full-file string copies
- full-file normalization passes
- full-file reparsing on small edits
- memory-heavy snapshots

Avoid O(file_size) operations during typing.

---

# Critical Systems To Inspect

## Cursor / Selection
Check for:
- invalid offsets
- reversed selection corruption
- cursor outside visible chunk
- stale visual position
- wrong line-column mapping

## Rendering
Check for:
- stale invalidation
- chunk desync
- incorrect line rendering
- horizontal scroll mismatch
- partial redraw corruption

## Streaming IO
Check for:
- incorrect chunk boundaries
- stale reads
- partial write corruption
- invalid seek logic
- line index corruption

## Undo / Redo
Check for:
- offset drift
- stale references
- invalid restore order
- desync after external file modification

## Syntax Highlighting
Check for:
- lexer state crossing chunk boundaries
- multiline token corruption
- stale token cache
- invalid incremental parsing

## Unicode / UTF-8
Check for:
- broken surrogate handling
- multibyte split corruption
- invalid cursor movement
- incorrect glyph width assumptions

---

# Logging Rules

Use targeted logs only.

All newly added logs must be guarded by an explicit working debug flag.
Prefer one global/central flag per debug family so logs can be disabled in one place.
For editor-wide diagnostics, use `SodiumEditor.DEBUG_LOGS` as the shared switch.
Do not add unconditional `Log.d`, `Log.w`, `Log.e`, or equivalent calls.

Log:
- cursor offsets
- line indexes
- chunk ranges
- file offsets
- cache invalidation
- renderer invalidation
- edit operations

Avoid noisy spam logs.

Preferred format:

```text
[SodiumEditor]
operation=
cursor=
selection=
offset=
chunk=
line=
thread=
```

---

# Performance Rules

Editor performance is critical.

Never introduce:
- excessive allocations
- repeated String rebuilding
- repeated regex parsing
- full redraws on tiny edits
- full file scans during typing

Prefer:
- incremental updates
- chunk-local fixes
- cache-aware operations

---

# Patch Rules

DO:
- keep patches small
- preserve coding style
- preserve user changes
- add regression coverage

DO NOT:
- revert unrelated files
- reformat unrelated code
- rename files unnecessarily
- modify architecture without evidence

---

# Testing Philosophy

A bug is NOT fixed unless:

1. it is reproduced,
2. a failing test exists,
3. the fix passes,
4. regression coverage exists.

Passing tests after reproduction are mandatory.

---

# Build Commands

Build sample app:

```sh
./gradlew :app:assembleDebug
```

Build library:

```sh
./gradlew :sodium-editor:assembleDebug
```

Run tests:

```sh
./gradlew :sodium-editor:testDebugUnitTest
```

Full tests:

```sh
./gradlew :sodium-editor:testDebugUnitTest -PsodiumEditorRunRobolectric=true
```

Clean:

```sh
./gradlew clean
```

---

# Repository Structure

- `app/` sample Android application
- `sodium-editor/` reusable editor library
- `sodium-editor/src/main/java/com/yn/sodiumeditor/` production code
- `sodium-editor/src/test/java/com/yn/sodiumeditor/test/` JVM tests
- `sodium-editor/src/androidTest/` instrumentation tests

Avoid editing generated files under `build/`.

---

# Final Rule

The goal is NOT merely preventing crashes.

The goal is detecting:
- logical corruption
- invalid editor state
- rendering glitches
- file synchronization bugs
- silent failures
- state desynchronization
- regression risks
- performance regressions
- edge-case corruption
