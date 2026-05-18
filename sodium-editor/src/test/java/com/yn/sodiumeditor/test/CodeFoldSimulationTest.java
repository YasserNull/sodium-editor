package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
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
}
