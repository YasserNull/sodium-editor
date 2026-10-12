package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression: tapping a Gboard suggestion did nothing.
 *
 * <p>On Android 13+ (API 33+), IMEs apply a tapped suggestion through {@link
 * android.view.inputmethod.InputConnection#replaceText(int, int, CharSequence, int,
 * android.view.inputmethod.TextAttribute)}. The default {@code BaseInputConnection}
 * implementation edits {@link android.view.inputmethod.InputConnection#getEditable()}, which is
 * null/disconnected for a file-backed editor, so the suggestion was silently dropped.
 *
 * <p>SodiumInputConnection must override replaceText and route it through the Ime document logic.
 */
public class ImeReplaceTextSuggestionRegressionTest {

  @Test
  public void sodiumInputConnection_mustOverrideReplaceText() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/input/SodiumInputConnection.java");
    String body = methodBody(src, "public boolean replaceText(");

    assertTrue(
        "BUG: replaceText is not overridden, so Gboard suggestion taps fall through to the"
            + " BaseInputConnection default which edits a null Editable and does nothing.",
        body.contains("ime.onReplaceText("));
  }

  @Test
  public void ime_mustImplementOnReplaceTextAgainstTheDocument() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");
    String body = methodBody(src, "public boolean onReplaceText(");

    assertTrue(
        "BUG: onReplaceText must map the IME offsets onto document line/char positions.",
        body.contains("offsetToLineCharInContext")
            && body.contains("replaceSelectionWithText"));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static Path findPath(String rel) {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(rel);
      if (Files.exists(candidate)) return candidate;
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }
    throw new IllegalStateException("Could not locate file: " + rel);
  }

  private static String methodBody(String src, String signature) {
    int method = src.indexOf(signature);
    if (method < 0) throw new IllegalStateException("Method not found: " + signature);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + signature);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unbalanced braces for: " + signature);
  }
}
