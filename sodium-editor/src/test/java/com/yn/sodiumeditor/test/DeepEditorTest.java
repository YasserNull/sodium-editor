package com.yn.sodiumeditor.test;

import static org.junit.Assert.*;

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
public class DeepEditorTest {

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
  public void setup() {
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
    editor.windowRender.windowStartLine = 0;
    editor.windowRender.linesWindow.clear();
    editor.windowRender.linesWindow.add("");
  }

  @Test
  public void testSimulatedUserDeletionAndMerge() {
    editor.cursor.setCursorPosition(0, 0);
    editor.editOperators.insertTextAtCursor("ABC");
    editor.editOperators.insertCharAtCursor('\n');
    editor.editOperators.insertTextAtCursor("DEF");

    assertEquals(2, editor.view.getLinesCount());
    assertEquals("DEF", editor.windowRender.getLineTextForRender(1));

    try {
      TestFileLogger.append(logFile, "Line count before delete: " + editor.view.getLinesCount());
    } catch (Exception ignored) {
    }
    editor.cursor.setCursorPosition(1, 0);
    editor.editOperators.deleteCharAtCursor();
    try {
      TestFileLogger.append(logFile, "Line count after delete: " + editor.view.getLinesCount());
      TestFileLogger.append(
          logFile, "Line 0 content: " + editor.windowRender.getLineTextForRender(0));
      TestFileLogger.append(
          logFile, "Line 1 content: " + editor.windowRender.getLineTextForRender(1));
    } catch (Exception ignored) {
    }

    assertEquals(1, editor.view.getLinesCount());
    assertEquals("ABCDEF", editor.windowRender.getLineTextForRender(0));
    assertEquals(
        "Deleted line should render empty (no phantom text)",
        "",
        editor.windowRender.getLineTextForRender(1));
    assertFalse(editor.windowRender.modifiedLines.containsKey(1));
  }
}
