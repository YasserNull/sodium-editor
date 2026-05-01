package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RealFileDeleteLineInstrumentedTest {
    private SodiumEditor editor;
    private File logFile;
    private Context context;

    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        logFile = new File(context.getFilesDir(), "test-logs/real-file-delete-instrumented.log");
        File parent = logFile.getParentFile();
        if (parent != null) parent.mkdirs();
        writeUtf8(logFile, "");

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor = new SodiumEditor(context, null);
                            editor.layout(0, 0, 1080, 1920);
                        });
        log("LOG_FILE=" + logFile.getAbsolutePath());
    }

    @Test
    public void deleteLineFromRealFile_doesNotReappearAfterWindowReloadCheck() throws Exception {
        File input = new File(context.getCacheDir(), "real_delete_test.txt");
        writeUtf8(input, "AAA\nBBB\nCCC");
        log("Input file: " + input.getAbsolutePath());

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> editor.fileIO.loadFromFile(input));
        drainUiAndIo();

        String before = editor.fileIO.getTextSnapshot();
        log("Before snapshot:\n" + before);
        assertEquals("AAA\nBBB\nCCC", before);
        assertEquals(3, editor.view.getLinesCount());
        assertWindowRenderHasNoPhantomText("BBB");

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor.cursor.setCursorPosition(1, 3);
                            editor.editOperators.deleteCharAtCursor();
                            editor.editOperators.deleteCharAtCursor();
                            editor.editOperators.deleteCharAtCursor();
                        });
        drainUiAndIo();

        String clearedLine = editor.windowRender.getLineTextForRender(1);
        log("After clearing chars, line[1] for render: '" + clearedLine + "'");
        assertEquals("Deleted line should render empty (no phantom text)", "", clearedLine);
        String afterClearSnapshot = editor.fileIO.getTextSnapshot();
        log("After clearing chars snapshot:\n" + afterClearSnapshot);
        assertEquals("AAA\n\nCCC", afterClearSnapshot);
        assertWindowRenderHasNoPhantomText("BBB");

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor.cursor.setCursorPosition(1, 0);
                            editor.editOperators.deleteCharAtCursor(); // delete line (merge)
                        });
        drainUiAndIo();

        String after = editor.fileIO.getTextSnapshot();
        log("After snapshot:\n" + after);
        assertEquals(2, editor.view.getLinesCount());
        assertEquals("AAA\nCCC", after);
        assertFalse(after.contains("BBB"));

        assertNoPhantomRenderText("BBB");
        assertWindowRenderHasNoPhantomText("BBB");
    }

    /**
     * This test intentionally catches the "phantom render" issue:
     * after deleting all lines in-memory (unsaved), ViewRender can still
     * populate direct lines from the on-disk file and draw stale text.
     *
     * Expected: direct-line reads should not be used while there are pending edits.
     * Current behavior (bug): direct-line reads can still pull old text.
     */
    @Test
    public void deleteAllLines_thenDirectLineReadMustNotReturnOldFileText() throws Exception {
        File input = new File(context.getCacheDir(), "real_delete_all_test.txt");
        writeUtf8(
                input,
                "# Null Code IDE\n\n```js\nconst name = \"Yasser\";\nconst age = \"18\";\nalert(\n  {\n  \n  }\n");

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> editor.fileIO.loadFromFile(input));
        drainUiAndIo();

        // Delete everything WITHOUT saving to disk.
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor.selection.selectAll();
                            editor.selection.replaceSelectionWithText("");
                        });
        drainUiAndIo();

        // Sanity: in-memory view says "empty document".
        String snap = editor.fileIO.getTextSnapshot();
        log("After delete-all snapshot:\n" + snap);

        // Force ViewRender into "needDirect" path by making the window irrelevant.
        // We don't need to actually draw; we just directly call populateDirectLinesForRange.
        java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
        editor.fileIO.populateDirectLinesForRange(0, 8, direct);
        log("Direct lines after delete-all (from file): " + direct);

        // This assertion should FAIL today if phantom render bug exists:
        // direct line reads must not return old file text while there are pending edits.
        for (java.util.Map.Entry<Integer, String> e : direct.entrySet()) {
            int line = e.getKey();
            String text = e.getValue();
            if (text != null && !text.isEmpty()) {
                throw new AssertionError(
                        "BUG: direct line read returned stale text after delete-all: line="
                                + line
                                + " text='"
                                + text
                                + "'");
            }
        }
    }

    private void drainUiAndIo() throws Exception {
        for (int i = 0; i < 80; i++) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (!editor.fileIO.isWindowLoading) return;
            TimeUnit.MILLISECONDS.sleep(25);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void assertNoPhantomRenderText(String forbidden) throws Exception {
        int linesCount = editor.view.getLinesCount();
        log("Render check: linesCount=" + linesCount + " windowStart=" + editor.windowRender.windowStartLine + " windowSize=" + editor.windowRender.linesWindow.size());

        List<String> windowSnapshot = new ArrayList<>();
        synchronized (editor.windowRender.linesWindow) {
            windowSnapshot.addAll(editor.windowRender.linesWindow);
        }
        log("Render check: windowSnapshot.size=" + windowSnapshot.size());
        for (int i = 0; i < windowSnapshot.size(); i++) {
            String v = windowSnapshot.get(i);
            if (v != null && v.contains(forbidden)) {
                throw new AssertionError("Phantom text found in linesWindow at localIdx=" + i + " value='" + v + "'");
            }
        }

        // Verify "non-existent" lines (>= linesCount) render as empty, not old/deleted text.
        for (int i = 0; i < linesCount + 6; i++) {
            String text = editor.windowRender.getLineTextForRender(i);
            log("Render check: getLineTextForRender(" + i + ")='" + text + "'");
            if (text != null && text.contains(forbidden)) {
                throw new AssertionError("Phantom text rendered at globalLine=" + i + " value='" + text + "'");
            }
            if (i >= linesCount && text != null && !text.isEmpty()) {
                throw new AssertionError("Non-existent line rendered non-empty at globalLine=" + i + " value='" + text + "'");
            }
        }

        // Also verify some clearly-out-of-range indexes.
        int[] outOfRange = new int[] {linesCount, linesCount + 1, linesCount + 10, 9999};
        for (int idx : outOfRange) {
            String text = editor.windowRender.getLineTextForRender(idx);
            log("Render check: OOR getLineTextForRender(" + idx + ")='" + text + "'");
            if (text != null && !text.isEmpty()) {
                throw new AssertionError("Out-of-range line rendered non-empty at globalLine=" + idx + " value='" + text + "'");
            }
        }
    }

    private void assertWindowRenderHasNoPhantomText(String forbidden) throws Exception {
        int linesCount = editor.view.getLinesCount();
        int winStart = editor.windowRender.windowStartLine;
        List<String> win = new ArrayList<>();
        synchronized (editor.windowRender.linesWindow) {
            win.addAll(editor.windowRender.linesWindow);
        }
        log("WindowRender check: linesCount=" + linesCount + " winStart=" + winStart + " winSize=" + win.size());

        // 1) Ensure the current window buffer itself doesn't still contain deleted text.
        for (int local = 0; local < win.size(); local++) {
            int global = winStart + local;
            String text = win.get(local);
            log("WindowRender check: local=" + local + " global=" + global + " text='" + text + "'");
            if (text != null && text.contains(forbidden)) {
                throw new AssertionError("Phantom text found in linesWindow local=" + local + " global=" + global + " value='" + text + "'");
            }
            // If the window includes lines beyond logical EOF, those must be empty.
            if (global >= linesCount && text != null && !text.isEmpty()) {
                throw new AssertionError("EOF window line is non-empty local=" + local + " global=" + global + " value='" + text + "'");
            }
        }

        // 2) Ensure modifiedLines doesn't still hold deleted text (it always overrides render).
        synchronized (editor.windowRender.modifiedLines) {
            for (java.util.Map.Entry<Integer, String> e : editor.windowRender.modifiedLines.entrySet()) {
                int line = e.getKey();
                String text = e.getValue();
                log("WindowRender check: modifiedLines[" + line + "]='" + text + "'");
                if (text != null && text.contains(forbidden)) {
                    throw new AssertionError("Phantom text found in modifiedLines at global=" + line + " value='" + text + "'");
                }
                if (line >= linesCount && text != null && !text.isEmpty()) {
                    throw new AssertionError("Out-of-range modifiedLines non-empty at global=" + line + " value='" + text + "'");
                }
            }
        }
    }

    private void log(String msg) throws Exception {
        appendUtf8(logFile, msg + "\n");
    }

    private static void writeUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    private static void appendUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }
}
