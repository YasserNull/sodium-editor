package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.Looper;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class RealFileDeleteLineTest {

    private SodiumEditor editor;
    private File logFile;

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
                        System.err.println("TEST_LOG_APPEND=FAILED");
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
    public void setup() throws Exception {
        try {
            editor = new SodiumEditor(RuntimeEnvironment.getApplication(), null);
        } catch (UnsatisfiedLinkError e) {
            try {
                if (logFile != null) {
                    TestFileLogger.append(logFile, "SKIP: UnsatisfiedLinkError during SodiumEditor init");
                    TestFileLogger.appendThrowable(logFile, e);
                }
            } catch (Exception ignored) {
            }
            Assume.assumeNoException(e);
            return;
        }
        // Non-zero size so checkAndLoadWindow() can run (this is needed to reproduce the bug).
        editor.layout(0, 0, 1080, 1920);
    }

    @Test
    public void deleteLineFromRealFile_doesNotReappearAfterWindowReloadCheck() throws Exception {
        File input = new File(RuntimeEnvironment.getApplication().getCacheDir(), "real_delete_test.txt");
        writeUtf8(input, "AAA\nBBB\nCCC");

        log("Input file: " + input.getAbsolutePath());
        log("Input content: " + readUtf8(input));

        editor.fileIO.loadFromFile(input);
        drainUiAndIo();

        String before = editor.fileIO.getTextSnapshot();
        log("Before snapshot:\n" + before);
        assertEquals("AAA\nBBB\nCCC", before);
        assertEquals(3, editor.view.getLinesCount());

        // Simulate: delete characters of line 2 ("BBB"), then delete the now-empty line by backspacing at column 0.
        editor.cursor.setCursorPosition(1, 3);
        editor.editOperators.deleteCharAtCursor(); // delete 'B'
        editor.editOperators.deleteCharAtCursor(); // delete 'B'
        editor.editOperators.deleteCharAtCursor(); // delete 'B'
        assertEquals(0, editor.cursor.cursorChar);
        assertEquals(1, editor.cursor.cursorLine);

        String emptied = editor.windowRender.getLineTextForRender(1);
        log("After clearing chars, line[1] for render: '" + emptied + "'");
        assertEquals("Deleted line should render empty (no phantom text)", "", emptied);
        String afterClearSnapshot = editor.fileIO.getTextSnapshot();
        log("After clearing chars snapshot:\n" + afterClearSnapshot);
        assertEquals("AAA\n\nCCC", afterClearSnapshot);

        editor.editOperators.deleteCharAtCursor(); // backspace at start => merge with previous => delete line
        drainUiAndIo();

        String after = editor.fileIO.getTextSnapshot();
        log("After snapshot:\n" + after);

        assertEquals(2, editor.view.getLinesCount());
        assertEquals("AAA\nCCC", after);
        assertFalse("Deleted line text reappeared after edits", after.contains("BBB"));

        // Deep render verification: non-existent lines must not render stale text.
        int count = editor.view.getLinesCount();
        for (int i = 0; i < count + 6; i++) {
            String text = editor.windowRender.getLineTextForRender(i);
            log("Render check: getLineTextForRender(" + i + ")='" + text + "'");
            assertFalse("Phantom text rendered at line " + i, text != null && text.contains("BBB"));
            if (i >= count) {
                assertEquals("Non-existent line should render empty at line " + i, "", text);
            }
        }
    }

    private void drainUiAndIo() {
        // File IO posts work to its HandlerThread, which then posts UI mutations to main.
        for (int i = 0; i < 15; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!editor.fileIO.isWindowLoading) return;
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void log(String msg) throws Exception {
        TestFileLogger.append(logFile, msg);
    }

    private static void writeUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    private static String readUtf8(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
