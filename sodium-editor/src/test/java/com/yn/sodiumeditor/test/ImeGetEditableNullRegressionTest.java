package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression: tapping a Gboard suggestion did nothing.
 *
 * <p>Gboard (and other IMEs) apply a tapped suggestion either through the InputConnection API
 * (commitText/commitCorrection/deleteSurroundingText) OR by directly editing the {@link
 * android.text.Editable} returned from {@link android.view.inputmethod.InputConnection#getEditable()}.
 * SodiumEditor's document is file-backed and streamed, so it cannot be represented by an Editable.
 * Returning a disconnected dummy Editable made Gboard take the direct-edit path: it wrote the
 * suggestion into a throwaway buffer and the document never changed.
 *
 * <p>The only correct contract for a custom, non-Editable-backed connection is to return {@code
 * null} from {@code getEditable()} so the IME falls back to the connection API.
 */
public class ImeGetEditableNullRegressionTest {

  @Test
  public void getEditable_mustReturnNullSoImeUsesConnectionApi() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/input/SodiumInputConnection.java");
    String body = methodBody(src, "public Editable getEditable()");

    assertFalse(
        "BUG: getEditable must not expose a disconnected dummy Editable; Gboard writes suggestions"
            + " into it and the document never changes. Return null instead.",
        body.contains("ime.imeEditable"));
    assertTrue(
        "BUG: a non-Editable-backed InputConnection must return null from getEditable().",
        body.contains("return null"));
  }

  @Test
  public void imeDummyEditable_mustNotBeExposedToTheIme() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/input/SodiumInputConnection.java");
    assertFalse(
        "BUG: the dummy imeEditable buffer must never escape the editor to an IME.",
        src.contains("return ime.imeEditable"));
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
