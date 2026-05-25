package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import org.junit.Assume;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class PendingEditDirtyTrackingTest {

    private SodiumEditor editor;
    private File logFile;

    @Rule
    public final TestWatcher testLog =
            new TestWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        logFile = TestFileLogger.newLogFile(
                                "sodium-editor/build/test-logs/"
                                        + description.getClassName()
                                        + "."
                                        + description.getMethodName()
                                        + ".log");
                        TestFileLogger.append(logFile, "START " + description.getDisplayName());
                    } catch (Exception ignored) {
                    }
                }

                @Override
                protected void failed(Throwable e, Description description) {
                    try {
                        TestFileLogger.append(logFile, "FAILED " + description.getDisplayName());
                        TestFileLogger.appendThrowable(logFile, e);
                    } catch (Exception ignored) {
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
            Assume.assumeNoException(e);
            return;
        }
        editor.windowRender.windowStartLine = 0;
        editor.windowRender.linesWindow.clear();
        editor.windowRender.linesWindow.add("");
    }

    @Test
    public void spaceCreatesPendingEdit() {
        assertEquals(0, editor.editOperators.getPendingEditsCount());
        assertFalse(editor.editOperators.canUndo());

        editor.editOperators.insertTextAtCursor(" ");

        assertTrue("Space should create pending edit, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);
        assertTrue(editor.editOperators.canUndo());
    }

    @Test
    public void tabCreatesPendingEdit() {
        editor.editOperators.insertTextAtCursor("\t");

        assertTrue("Tab should create pending edit, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);
    }

    @Test
    public void newlineCreatesPendingEdit() {
        editor.editOperators.insertCharAtCursor('\n');

        assertTrue("Newline should create pending edit, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);
    }

    @Test
    public void textCreatesPendingEdit() {
        editor.editOperators.insertTextAtCursor("abc");

        assertTrue("Text should create pending edit, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);
    }

    @Test
    public void undoReturnsToClean() {
        editor.editOperators.insertTextAtCursor("abc");
        assertTrue(editor.editOperators.getPendingEditsCount() > 0);

        editor.editOperators.undo();

        assertEquals(0, editor.editOperators.getPendingEditsCount());
        assertFalse(editor.editOperators.canUndo());
    }

    @Test
    public void typeSpaceDeleteThenUndoAllReturnsClean() {
        editor.editOperators.insertTextAtCursor(" ");
        editor.editOperators.deleteCharAtCursor();

        assertEquals("After type+delete, pendingEdits should be 0, got=" + editor.editOperators.getPendingEditsCount(),
                0, editor.editOperators.getPendingEditsCount());
        assertFalse("After type+delete, canUndo should be false", editor.editOperators.canUndo());
    }

    @Test
    public void typeSpaceThenDeleteReturnsCleanImmediately() {
        editor.editOperators.insertTextAtCursor(" ");
        editor.editOperators.deleteCharAtCursor();

        assertEquals("Deleting the only inserted space should leave no pending edits",
                0, editor.editOperators.getPendingEditsCount());
        assertFalse("Deleting the only inserted space should leave no undo entry",
                editor.editOperators.canUndo());
        assertFalse("Deleting the only inserted space should clear the modified line marker",
                editor.windowRender.modifiedLines.containsKey(0));
        assertEquals("", editor.windowRender.getLineTextForRender(0));
    }

    @Test
    public void typeSpacesThenDeleteAllReturnsCleanImmediately() {
        editor.editOperators.insertTextAtCursor("   ");

        editor.editOperators.deleteCharAtCursor();
        editor.editOperators.deleteCharAtCursor();
        editor.editOperators.deleteCharAtCursor();

        assertEquals("Deleting all inserted spaces should leave no pending edits",
                0, editor.editOperators.getPendingEditsCount());
        assertFalse("Deleting all inserted spaces should leave no undo entry",
                editor.editOperators.canUndo());
        assertFalse("Deleting all inserted spaces should clear the modified line marker",
                editor.windowRender.modifiedLines.containsKey(0));
        assertEquals("", editor.windowRender.getLineTextForRender(0));
    }

    @Test
    public void undoThenRedoRestoresPendingEdits() {
        editor.editOperators.insertTextAtCursor("hello");
        assertEquals(1, editor.editOperators.getPendingEditsCount());

        editor.editOperators.undo();
        assertEquals(0, editor.editOperators.getPendingEditsCount());

        editor.editOperators.redo();
        assertTrue("After redo, pendingEdits > 0, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);
    }

    @Test
    public void typeAndDeleteDownToClean() {
        editor.editOperators.insertTextAtCursor("hi");
        editor.editOperators.deleteCharAtCursor();

        assertTrue("After deleting one inserted char, pendingEdits should exist, got=" + editor.editOperators.getPendingEditsCount(),
                editor.editOperators.getPendingEditsCount() > 0);

        editor.editOperators.deleteCharAtCursor();

        assertEquals("After deleting all inserted chars, pendingEdits = 0, got=" + editor.editOperators.getPendingEditsCount(),
                0, editor.editOperators.getPendingEditsCount());
        assertFalse("canUndo = false after deleting all inserted chars", editor.editOperators.canUndo());
    }
}
