package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;

import android.os.Looper;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Reproduces: Select All + Delete on a huge file leaves many empty lines instead of one. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class SelectAllDeleteHugeFileRegressionTest {

  private SodiumEditor editor;

  @Before
  public void setup() {
    try {
      editor = new SodiumEditor(RuntimeEnvironment.getApplication(), null);
    } catch (UnsatisfiedLinkError e) {
      Assume.assumeNoException(e);
      return;
    }
    editor.layout(0, 0, 1080, 1920);
  }

  @Test
  public void selectAllDeleteOnHugeFile_shouldLeaveSingleEmptyLine() throws Exception {
    final int totalLines = 150000;
    File input = new File(RuntimeEnvironment.getApplication().getCacheDir(), "huge_select_delete.txt");
    try (OutputStreamWriter w =
        new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
      for (int i = 0; i < totalLines; i++) {
        w.write("line " + i);
        w.write('\n');
      }
    }

    editor.fileIO.loadFromFile(input);
    settle(60);

    // Wait for the line index so select-all can use the indexed fast path.
    long waitMs = 0;
    while (!editor.fileIO.isIndexReady && waitMs < 30_000L) {
      settle(1);
      waitMs += 100L;
      if (editor.fileIO.isIndexDisabled) break;
    }

    assertEquals("index must cover whole file", totalLines, editor.fileIO.lineOffsets.length);

    editor.selection.selectAll();
    settle(30);

    assertEquals("select-all must select the whole file", 0, editor.selection.selStartLine);
    assertEquals("select-all must reach the last line", totalLines - 1, editor.selection.selEndLine);

    editor.selection.deleteEntireFileSelectionFast();
    settle(10);

    assertEquals(
        "BUG: select-all delete on a huge file must leave exactly one line, not empty lines.",
        1,
        editor.view.getLinesCount());

    for (int i = 0; i < 6; i++) {
      assertEquals(
          "BUG: non-existent line " + i + " must render empty.",
          "",
          editor.windowRender.getLineTextForRender(i));
    }
  }

  private void settle(int rounds) {
    for (int i = 0; i < rounds; i++) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
      try {
        TimeUnit.MILLISECONDS.sleep(2);
      } catch (InterruptedException ignored) {
      }
    }
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }
}