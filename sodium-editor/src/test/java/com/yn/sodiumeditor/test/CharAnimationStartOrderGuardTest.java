package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static guard that inserted characters register animation before invalidation can draw them. */
public class CharAnimationStartOrderGuardTest {

  @Test
  public void insertChar_shouldStartAnimationBeforeLineInvalidation() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "public void insertCharAtCursor(char c)");
    int startAnim =
        body.indexOf("editor.charAnimation.startCharAnimationFromText(String.valueOf(c));");
    int invalidate = body.indexOf("editor.view.invalidateLineGlobal(editor.cursor.cursorLine)");

    assertTrue("Expected insertCharAtCursor to start char animation.", startAnim >= 0);
    assertTrue("Expected insertCharAtCursor to invalidate the changed line.", invalidate >= 0);
    assertTrue(
        "BUG: typed char animation must be registered before line invalidation can render the final"
            + " glyph.",
        startAnim < invalidate);
  }

  @Test
  public void selectionReplace_shouldStartAnimationBeforeInvalidate() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "private void handleSingleLineReplace");
    int startAnim = body.indexOf("editor.charAnimation.startCharAnimationFromText(insertText)");
    int invalidate = body.indexOf("editor.invalidate()");

    assertTrue("Expected single-line selection replace to start char animation.", startAnim >= 0);
    assertTrue("Expected single-line selection replace to invalidate.", invalidate >= 0);
    assertTrue(
        "BUG: selection replacement animation must be registered before invalidation.",
        startAnim < invalidate);
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
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + signature);
  }

  private static String readSource(String relativePath) throws Exception {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(relativePath);
      if (Files.exists(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
      }
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }

    throw new IllegalStateException("Could not locate source file: " + relativePath);
  }
}
