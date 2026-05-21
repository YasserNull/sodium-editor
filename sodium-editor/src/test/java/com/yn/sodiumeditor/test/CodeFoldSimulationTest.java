package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import android.os.Looper;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;

/**
 * Simulation test that reproduces the fold marker bug:
 * After inserting a newline at the end of a line with an opening bracket,
 * fold markers appear on lines that have NO opening brackets (empty lines or closing brackets).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class CodeFoldSimulationTest {

    private SodiumEditor editor;
    private File logFile;
    private File tempFile;

    @Rule
    public final TestWatcher testLog =
            new TestWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        logFile =
                                TestFileLogger.newLogFile(
                                        "sodium-editor/build/test-logs/"
                                                + description.getClassName()
                                                + "."
                                                + description.getMethodName()
                                                + ".log");
                        TestFileLogger.append(logFile, "START " + description.getDisplayName());
                        System.err.println("TEST_LOG_FILE=" + logFile.getAbsolutePath());
                    } catch (Exception ignored) {
                        System.err.println("TEST_LOG_FILE=FAILED_TO_CREATE");
                    }
                }

                @Override
                protected void failed(Throwable e, Description description) {
                    try {
                        TestFileLogger.append(logFile, "FAILED " + description.getDisplayName());
                        TestFileLogger.appendThrowable(logFile, e);
                    } catch (Exception ignored) {
                        e.printStackTrace();
                    }
                }

                @Override
                protected void succeeded(Description description) {
                    try {
                        TestFileLogger.append(logFile, "OK " + description.getDisplayName());
                    } catch (Exception ignored) {
                    }
                }
            };

    @Before
    public void setup() {
        try {
            editor = new SodiumEditor(RuntimeEnvironment.getApplication(), null);
        } catch (UnsatisfiedLinkError e) {
            assumeNoException(e);
            return;
        }
    }

    @Test
    public void foldMarker_shouldNotAppearOnEmptyLineAfterNewlineAtEndOfOpenBracketLine() throws Exception {
        log("=== Test: fold marker on empty line after newline ===");

        // Step 1: Type a line with opening bracket
        editor.cursor.setCursorPosition(0, 0);
        editor.editOperators.insertTextAtCursor("if (true) {");

        log("Line 0 after typing 'if (true) {': [" + editor.windowRender.getLineTextForRender(0) + "]");

        // Step 2: Go to end of line 0 and press enter
        editor.editOperators.insertCharAtCursor('\n');

        log("Line count after newline: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");

        // Step 3: Check fold markers
        editor.codeFold.invalidateFoldCaches();
        editor.codeFold.rebuildFoldIntervalsIfNeeded();

        String markerLine0 = editor.codeFold.getFoldMarkerForLine(0, editor.windowRender.getLineTextForRender(0));
        String markerLine1 = editor.codeFold.getFoldMarkerForLine(1, editor.windowRender.getLineTextForRender(1));

        log("Fold marker line 0: " + markerLine0);
        log("Fold marker line 1: " + markerLine1);

        // Line 1 is empty - it MUST NOT have a fold marker
        assertFalse(
                "BUG: fold marker '" + markerLine1 + "' appeared on empty line 1 after inserting newline. "
                        + "Empty lines should never show fold markers.",
                markerLine1 != null);
    }

    @Test
    public void foldMarker_shouldNotAppearOnClosingBracketLine() throws Exception {
        log("=== Test: fold marker on closing bracket line ===");

        // Step 1: Type opening bracket line
        editor.editOperators.insertTextAtCursor("if (true) {");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");

        editor.codeFold.invalidateFoldCaches();
        editor.codeFold.rebuildFoldIntervalsIfNeeded();

        String markerLine0 = editor.codeFold.getFoldMarkerForLine(0, editor.windowRender.getLineTextForRender(0));
        String markerLine1 = editor.codeFold.getFoldMarkerForLine(1, editor.windowRender.getLineTextForRender(1));

        log("Fold marker line 0: " + markerLine0);
        log("Fold marker line 1: " + markerLine1);

        assertNotNull(
                "BUG: typing a matching closing bracket on the next line must create a confirmed fold marker on the opening bracket line.",
                markerLine0);

        // Line 1 has just "}" - it MUST NOT have a fold marker
        assertFalse(
                "BUG: fold marker '" + markerLine1 + "' appeared on closing bracket line 1. "
                        + "Lines with only closing brackets should never show fold markers.",
                markerLine1 != null);
    }

    @Test
    public void foldMarker_shouldAppearWhenTypedClosingBracketIsFinalLineWithoutTrailingNewline()
            throws Exception {
        log("=== Test: fold marker when closing bracket is final line without trailing newline ===");

        String content = "if (true) {";
        tempFile = File.createTempFile("fold_final_line_", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        editor.fileIO.sourceFile = tempFile;
        editor.fileIO.isIndexReady = true;
        editor.fileIO.isEof = true;
        editor.fileIO.lineOffsets = new long[] {0};
        editor.windowRender.windowStartLine = 0;
        editor.windowRender.linesWindow.clear();
        editor.windowRender.linesWindow.add(content);
        editor.cursor.setCursorPosition(0, content.length());

        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("lineCountDelta: " + editor.editOperators.lineCountDelta);

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertNotNull(
                "BUG: fold marker disappears when the matching closing bracket is the final line with no trailing newline.",
                markerLine0);
        assertNotNull(
                "BUG: fold range was not created for a block whose closing bracket is the final line.",
                range);
        assertEquals(
                "BUG: fold range must end on the final closing-bracket line.",
                1,
                range.endLine);
    }

    @Test
    public void foldMarker_shouldAppearOnLoadedFileWhenClosingBracketIsFinalLineWithoutTrailingNewline()
            throws Exception {
        log("=== Test: loaded file fold marker when final line is closing bracket ===");

        String content = "if (true) {\n}";
        tempFile = File.createTempFile("fold_loaded_final_line_", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        editor.fileIO.sourceFile = tempFile;
        editor.fileIO.isIndexReady = true;
        editor.fileIO.isEof = true;
        editor.fileIO.lineOffsets = new long[] {0, "if (true) {\n".getBytes(StandardCharsets.UTF_8).length};
        editor.windowRender.windowStartLine = 0;
        editor.windowRender.linesWindow.clear();
        editor.windowRender.linesWindow.add("if (true) {");
        editor.windowRender.linesWindow.add("}");

        editor.codeFold.refreshFoldRangesAroundRange(0, 1);

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertNotNull(
                "BUG: loaded files must keep the fold marker when the matching closing bracket is the final line without a trailing newline.",
                markerLine0);
        assertNotNull(
                "BUG: loaded final-line closing bracket did not create a fold range.",
                range);
        assertEquals(
                "BUG: fold range must end on the final line.",
                1,
                range.endLine);
    }

    @Test
    public void loadFromFile_shouldBuildFoldMarkerWhenClosingBracketIsFinalLineWithoutTrailingNewline()
            throws Exception {
        log("=== Test: real load fold marker when final line is closing bracket ===");

        String content = "if (true) {\n}";
        tempFile = File.createTempFile("fold_real_load_final_line_", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        editor.layout(0, 0, 1080, 1920);
        editor.fileIO.loadFromFile(tempFile);
        drainUiAndIo();

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertNotNull(
                "BUG: loadFromFile loses the fold marker when the matching closing bracket is the final line without a trailing newline.",
                markerLine0);
        assertNotNull(
                "BUG: loadFromFile did not build a fold range for a final-line closing bracket.",
                range);
        assertEquals(
                "BUG: fold range must end on the final line after loadFromFile.",
                1,
                range.endLine);
    }

    @Test
    public void deletingTrailingNewlineAfterClosingBracket_shouldKeepFoldMarkerOnOpeningLine()
            throws Exception {
        log("=== Test: fold marker after deleting trailing newline after closing bracket ===");

        editor.editOperators.insertTextAtCursor("if (true) {");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");
        editor.editOperators.insertCharAtCursor('\n');

        CodeFold.FoldRange before = editor.codeFold.getFoldRangeAtStart(0);
        log("Before delete fold range: " + (before == null ? "null" : before.describe()));
        assertNotNull("Test setup failed: fold range should exist before deleting trailing newline.", before);

        editor.cursor.setCursorPosition(2, 0);
        editor.editOperators.deleteCharAtCursor();

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertNotNull(
                "BUG: deleting the trailing newline after the closing bracket must not remove the fold marker.",
                markerLine0);
        assertNotNull(
                "BUG: deleting the trailing newline after the closing bracket removed the fold range.",
                range);
        assertEquals(
                "BUG: fold range should still end on the closing bracket line.",
                1,
                range.endLine);
    }

    @Test
    public void deletingMiddleNestedBraces_shouldKeepOuterFoldMarker() throws Exception {
        log("=== Test: fold marker after deleting middle nested braces ===");

        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");

        CodeFold.FoldRange beforeOuter = editor.codeFold.getFoldRangeAtStart(0);
        CodeFold.FoldRange beforeInner = editor.codeFold.getFoldRangeAtStart(1);
        log("Before delete outer range: " + (beforeOuter == null ? "null" : beforeOuter.describe()));
        log("Before delete inner range: " + (beforeInner == null ? "null" : beforeInner.describe()));
        assertNotNull("Test setup failed: outer fold range must exist before deleting middle braces.", beforeOuter);
        assertNotNull("Test setup failed: inner fold range must exist before deleting middle braces.", beforeInner);

        editor.selection.setSelection(1, 0, 3, 0);
        editor.selection.replaceSelectionWithText("");

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertEquals("BUG: deleting middle braces should leave only the outer block.", 2, editor.view.getLinesCount());
        assertEquals("{", editor.windowRender.getLineTextForRender(0));
        assertEquals("}", editor.windowRender.getLineTextForRender(1));
        assertNotNull(
                "BUG: deleting nested middle braces removed the fold marker for the remaining outer block.",
                markerLine0);
        assertNotNull(
                "BUG: deleting nested middle braces removed the fold range for the remaining outer block.",
                range);
        assertEquals(
                "BUG: remaining outer fold range should end on the closing bracket line.",
                1,
                range.endLine);
    }

    @Test
    public void deletingMiddleNestedBraceCharacters_shouldKeepOuterFoldMarker() throws Exception {
        log("=== Test: fold marker after deleting middle nested brace characters ===");

        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");

        CodeFold.FoldRange beforeOuter = editor.codeFold.getFoldRangeAtStart(0);
        CodeFold.FoldRange beforeInner = editor.codeFold.getFoldRangeAtStart(1);
        assertNotNull("Test setup failed: outer fold range must exist before deleting middle braces.", beforeOuter);
        assertNotNull("Test setup failed: inner fold range must exist before deleting middle braces.", beforeInner);

        editor.cursor.setCursorPosition(1, 1);
        editor.editOperators.deleteCharAtCursor();
        editor.cursor.setCursorPosition(2, 1);
        editor.editOperators.deleteCharAtCursor();

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Line 3: [" + editor.windowRender.getLineTextForRender(3) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertEquals("{", editor.windowRender.getLineTextForRender(0));
        assertEquals("", editor.windowRender.getLineTextForRender(1));
        assertEquals("", editor.windowRender.getLineTextForRender(2));
        assertEquals("}", editor.windowRender.getLineTextForRender(3));
        assertNotNull(
                "BUG: deleting nested middle brace characters removed the fold marker for the remaining outer block.",
                markerLine0);
        assertNotNull(
                "BUG: deleting nested middle brace characters removed the fold range for the remaining outer block.",
                range);
        assertEquals(
                "BUG: remaining outer fold range should end on the closing bracket line.",
                3,
                range.endLine);
    }

    @Test
    public void deletingOneMiddleNestedBrace_shouldKeepOuterFoldMarker() throws Exception {
        log("=== Test: fold marker after deleting one middle nested brace ===");

        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");

        CodeFold.FoldRange beforeOuter = editor.codeFold.getFoldRangeAtStart(0);
        CodeFold.FoldRange beforeInner = editor.codeFold.getFoldRangeAtStart(1);
        assertNotNull("Test setup failed: outer fold range must exist before deleting middle brace.", beforeOuter);
        assertNotNull("Test setup failed: inner fold range must exist before deleting middle brace.", beforeInner);

        editor.cursor.setCursorPosition(1, 1);
        editor.editOperators.deleteCharAtCursor();

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Line 3: [" + editor.windowRender.getLineTextForRender(3) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertEquals("{", editor.windowRender.getLineTextForRender(0));
        assertEquals("", editor.windowRender.getLineTextForRender(1));
        assertEquals("}", editor.windowRender.getLineTextForRender(2));
        assertEquals("}", editor.windowRender.getLineTextForRender(3));
        assertNotNull(
                "BUG: deleting one nested middle brace removed the fold marker for the still-valid outer block.",
                markerLine0);
        assertNotNull(
                "BUG: deleting one nested middle brace removed the fold range for the still-valid outer block.",
                range);
        assertEquals(
                "BUG: outer fold range should still end on the original final closing bracket line.",
                3,
                range.endLine);
    }

    @Test
    public void deletingMiddleNestedBracesFromLoadedFile_shouldKeepOuterFoldMarker() throws Exception {
        log("=== Test: loaded file fold marker after deleting middle nested braces ===");

        String content = "{\n{\n}\n}";
        tempFile = File.createTempFile("fold_loaded_nested_delete_", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        editor.layout(0, 0, 1080, 1920);
        editor.fileIO.loadFromFile(tempFile);
        drainUiAndIo();

        CodeFold.FoldRange beforeOuter = editor.codeFold.getFoldRangeAtStart(0);
        CodeFold.FoldRange beforeInner = editor.codeFold.getFoldRangeAtStart(1);
        log("Before delete outer range: " + (beforeOuter == null ? "null" : beforeOuter.describe()));
        log("Before delete inner range: " + (beforeInner == null ? "null" : beforeInner.describe()));
        assertNotNull("Test setup failed: loaded outer fold range must exist before deleting middle braces.", beforeOuter);
        assertNotNull("Test setup failed: loaded inner fold range must exist before deleting middle braces.", beforeInner);

        editor.selection.setSelection(1, 0, 3, 0);
        editor.selection.replaceSelectionWithText("");
        drainUiAndIo();

        String markerLine0 =
                editor.codeFold.getFoldMarkerForLine(
                        0, editor.windowRender.getLineTextForRender(0));
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Fold marker line 0: " + markerLine0);
        log("Fold range line 0: " + (range == null ? "null" : range.describe()));

        assertEquals("BUG: deleting middle braces from loaded file should leave only the outer block.", 2, editor.view.getLinesCount());
        assertEquals("{", editor.windowRender.getLineTextForRender(0));
        assertEquals("}", editor.windowRender.getLineTextForRender(1));
        assertNotNull(
                "BUG: deleting nested middle braces from a loaded file removed the fold marker for the remaining outer block.",
                markerLine0);
        assertNotNull(
                "BUG: deleting nested middle braces from a loaded file removed the fold range for the remaining outer block.",
                range);
        assertEquals(
                "BUG: remaining outer fold range should end on the closing bracket line after deleting from loaded file.",
                1,
                range.endLine);
    }

    @Test
    public void deletingNestedBracesInMiddleOfManyBlocks_shouldKeepRemainingFoldMarkers()
            throws Exception {
        log("=== Test: remaining fold markers after deleting nested braces in middle of many blocks ===");

        String content = "{\n}\n{\n{\n}\n}\n{\n}";
        tempFile = File.createTempFile("fold_many_blocks_nested_delete_", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        editor.layout(0, 0, 1080, 1920);
        editor.fileIO.loadFromFile(tempFile);
        drainUiAndIo();

        assertNotNull("Test setup failed: first block marker missing.", editor.codeFold.getFoldMarkerForLine(0, "{"));
        assertNotNull("Test setup failed: middle outer block marker missing.", editor.codeFold.getFoldMarkerForLine(2, "{"));
        assertNotNull("Test setup failed: middle inner block marker missing.", editor.codeFold.getFoldMarkerForLine(3, "{"));
        assertNotNull("Test setup failed: last block marker missing.", editor.codeFold.getFoldMarkerForLine(6, "{"));

        editor.selection.setSelection(3, 0, 5, 0);
        editor.selection.replaceSelectionWithText("");
        drainUiAndIo();

        String markerFirst = editor.codeFold.getFoldMarkerForLine(0, editor.windowRender.getLineTextForRender(0));
        String markerMiddle = editor.codeFold.getFoldMarkerForLine(2, editor.windowRender.getLineTextForRender(2));
        String markerLast = editor.codeFold.getFoldMarkerForLine(4, editor.windowRender.getLineTextForRender(4));
        CodeFold.FoldRange firstRange = editor.codeFold.getFoldRangeAtStart(0);
        CodeFold.FoldRange middleRange = editor.codeFold.getFoldRangeAtStart(2);
        CodeFold.FoldRange lastRange = editor.codeFold.getFoldRangeAtStart(4);

        log("Line count: " + editor.view.getLinesCount());
        for (int i = 0; i < editor.view.getLinesCount(); i++) {
            log("Line " + i + ": [" + editor.windowRender.getLineTextForRender(i) + "]");
        }
        log("Markers: first=" + markerFirst + " middle=" + markerMiddle + " last=" + markerLast);
        log("Ranges: first=" + (firstRange == null ? "null" : firstRange.describe())
                + " middle=" + (middleRange == null ? "null" : middleRange.describe())
                + " last=" + (lastRange == null ? "null" : lastRange.describe()));

        assertEquals("BUG: deleting nested middle braces should leave three two-line blocks.", 6, editor.view.getLinesCount());
        assertNotNull("BUG: first fold marker disappeared after deleting middle braces.", markerFirst);
        assertNotNull("BUG: middle outer fold marker disappeared after deleting its inner braces.", markerMiddle);
        assertNotNull("BUG: later fold marker disappeared or did not shift after deleting middle braces.", markerLast);
        assertEquals(1, firstRange.endLine);
        assertEquals(3, middleRange.endLine);
        assertEquals(5, lastRange.endLine);
    }

    @Test
    public void foldingMatchedTwoLineBlock_shouldNotHideFollowingEmptyLines() throws Exception {
        log("=== Test: fold range should stop at matching closing bracket ===");

        editor.editOperators.insertTextAtCursor("{");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertTextAtCursor("}");
        editor.editOperators.insertCharAtCursor('\n');
        editor.editOperators.insertCharAtCursor('\n');

        log("Line count: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");
        log("Line 2: [" + editor.windowRender.getLineTextForRender(2) + "]");
        log("Line 3: [" + editor.windowRender.getLineTextForRender(3) + "]");

        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(0);
        assertNotNull(
                "BUG: typing a matching closing bracket on line 1 must create a fold range starting at line 0.",
                range);
        assertEquals(
                "BUG: fold range must end on the matching closing bracket line, not include later empty lines.",
                1,
                range.endLine);

        assertTrue(
                "BUG: expected fold toggle on line 0 to succeed after creating the matched range.",
                editor.codeFold.toggleFoldAtLine(0));
        assertFalse("BUG: line 2 must remain outside the folded block.", editor.codeFold.isLineHidden(2));
        assertFalse("BUG: line 3 must remain outside the folded block.", editor.codeFold.isLineHidden(3));
        assertEquals(
                "BUG: with 4 lines and only line 1 hidden, visible line count should be 3.",
                3,
                editor.codeFold.getVisibleLineCount());
        assertEquals(0, editor.codeFold.mapVisibleIndexToGlobal(0));
        assertEquals(2, editor.codeFold.mapVisibleIndexToGlobal(1));
        assertEquals(3, editor.codeFold.mapVisibleIndexToGlobal(2));
    }

    @Test
    public void foldMarker_shouldNotAppearOnLinesWithIndentColonLikeColon() throws Exception {
        log("=== Test: fold marker on '}: ' line ===");

        // Simulate a scenario where a line ends with "}" followed by ":"
        // This is NOT a valid fold candidate because there's no open bracket context
        editor.editOperators.insertTextAtCursor("}:");

        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");

        editor.codeFold.invalidateFoldCaches();
        editor.codeFold.rebuildFoldIntervalsIfNeeded();

        String markerLine0 = editor.codeFold.getFoldMarkerForLine(0, editor.windowRender.getLineTextForRender(0));

        log("Fold marker line 0: " + markerLine0);

        assertFalse(
                "BUG: fold marker '" + markerLine0 + "' appeared on '}: '. "
                        + "Lines ending with closing bracket before colon should not be fold candidates.",
                markerLine0 != null);
    }

    @Test
    public void foldMarker_shouldNotAppearOnFileLoadedWithClosingBracket() throws Exception {
        log("=== Test: fold marker on file loaded with closing bracket ===");

        // Create a temp file with content:
        // if (true) {
        // }
        // empty line
        String content = "if (true) {\n}\n";
        tempFile = File.createTempFile("fold_test_", ".txt");
        tempFile.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.write(content.getBytes(StandardCharsets.UTF_8));
        }

        editor.fileIO.sourceFile = tempFile;
        editor.fileIO.isIndexReady = true;
        editor.fileIO.lineOffsets = new long[]{0, 13};

        log("Total lines: " + editor.view.getLinesCount());
        log("Line 0: [" + editor.windowRender.getLineTextForRender(0) + "]");
        log("Line 1: [" + editor.windowRender.getLineTextForRender(1) + "]");

        editor.codeFold.invalidateFoldCaches();
        editor.codeFold.rebuildFoldIntervalsIfNeeded();

        String markerLine1 = editor.codeFold.getFoldMarkerForLine(1, editor.windowRender.getLineTextForRender(1));

        log("Fold marker line 1: " + markerLine1);

        assertFalse(
                "BUG: fold marker '" + markerLine1 + "' appeared on closing bracket line 1. "
                        + "This line has no opening bracket.",
                markerLine1 != null);
    }

    private void log(String msg) {
        System.err.println(msg);
        try {
            if (logFile != null) {
                TestFileLogger.append(logFile, msg);
            }
        } catch (Exception ignored) {
        }
    }

    private void drainUiAndIo() {
        for (int i = 0; i < 25; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!editor.fileIO.isWindowLoading && !editor.loadingCircle.isInitialFileOpenLoading) return;
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
