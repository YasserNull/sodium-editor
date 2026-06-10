package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.input.SodiumInputConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CharByCharTypingInstrumentedTest {
  private SodiumEditor editor;
  private Context context;
  private File logFile;
  private SodiumInputConnection inputConnection;

  @Before
  public void setup() throws Exception {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    logFile = new File(context.getFilesDir(), "test-logs/char-by-char-typing.log");
    File parent = logFile.getParentFile();
    if (parent != null) parent.mkdirs();
    writeUtf8(logFile, "");

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              editor = new SodiumEditor(context, null);
              editor.layout(0, 0, 1080, 600);
              editor.fileIO.clearContent();
              editor.lineNumber.setShowLineNumbers(false);
              inputConnection = new SodiumInputConnection(editor, editor.ime);
            });
    log("LOG_FILE=" + logFile.getAbsolutePath());
  }

  @Test
  public void typeEnglishAndArabic_charByChar_singleLine() throws Exception {
    final String text = "Hello مرحبا World العالم";

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              editor.cursor.setCursorPosition(0, 0);
              for (int i = 0; i < text.length(); i++) {
                inputConnection.commitText(String.valueOf(text.charAt(i)), 1);
                String current = editor.windowRender.getLineTextForRender(0);
                String expectedPrefix = text.substring(0, i + 1);
                if (!expectedPrefix.equals(current)) {
                  throw new AssertionError(
                      "Typing progress mismatch at i="
                          + i
                          + " expectedPrefix='"
                          + expectedPrefix
                          + "' current='"
                          + current
                          + "'");
                }
              }
            });
    drainUi();

    String rendered = editor.windowRender.getLineTextForRender(0);
    log("Rendered[0]='" + rendered + "'");
    assertEquals(text, rendered);
    assertEquals(text, editor.fileIO.getTextSnapshot());
  }

  @Test
  public void typeArabicOnly_charByChar_mustInsertEveryChar() throws Exception {
    final String text = "مرحبا بالعالم";

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              editor.cursor.setCursorPosition(0, 0);
              for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                inputConnection.commitText(ch, 1);

                String current = editor.windowRender.getLineTextForRender(0);
                String expectedPrefix = text.substring(0, i + 1);
                if (!expectedPrefix.equals(current)) {
                  throw new AssertionError(
                      "BUG: Arabic char not inserted at i="
                          + i
                          + " committed='"
                          + ch
                          + "' expectedPrefix='"
                          + expectedPrefix
                          + "' current='"
                          + current
                          + "'");
                }
              }
            });
    drainUi();

    String rendered = editor.windowRender.getLineTextForRender(0);
    log("ArabicOnly Rendered[0]='" + rendered + "'");
    assertEquals(text, rendered);
    assertTrue(
        "Snapshot must contain Arabic text", editor.fileIO.getTextSnapshot().contains("مرحبا"));
  }

  @Test
  public void typeArabic_viaComposingTextSequence_mustEndUpInserted() throws Exception {
    // Many Arabic keyboards use composing updates (setComposingText) then finishComposingText,
    // not commitText per character. This test reproduces that path.
    final String target = "مرحبا";

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              editor.cursor.setCursorPosition(0, 0);
              for (int i = 0; i < target.length(); i++) {
                String composing = target.substring(0, i + 1);
                inputConnection.setComposingText(composing, 1);
                String current = editor.windowRender.getLineTextForRender(0);
                if (current == null) current = "";
                if (!current.endsWith(composing)) {
                  throw new AssertionError(
                      "BUG: composing Arabic not reflected at i="
                          + i
                          + " composing='"
                          + composing
                          + "' current='"
                          + current
                          + "'");
                }
              }
              inputConnection.finishComposingText();
            });
    drainUi();

    String rendered = editor.windowRender.getLineTextForRender(0);
    log("ComposingArabic Rendered[0]='" + rendered + "'");
    assertEquals(target, rendered);
  }

  @Test
  public void typeEnglishAndArabic_charByChar_multiLine() throws Exception {
    final String l0 = "Line1 سطر١";
    final String l1 = "Line2 سطر٢";

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              editor.cursor.setCursorPosition(0, 0);
              for (int i = 0; i < l0.length(); i++)
                inputConnection.commitText(String.valueOf(l0.charAt(i)), 1);
              inputConnection.commitText("\n", 1);
              for (int i = 0; i < l1.length(); i++)
                inputConnection.commitText(String.valueOf(l1.charAt(i)), 1);
            });
    drainUi();

    assertEquals(l0, editor.windowRender.getLineTextForRender(0));
    assertEquals(l1, editor.windowRender.getLineTextForRender(1));
    assertEquals(l0 + "\n" + l1, editor.fileIO.getTextSnapshot());
  }

  private void drainUi() throws Exception {
    for (int i = 0; i < 20; i++) {
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      TimeUnit.MILLISECONDS.sleep(5);
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
