package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards editing behavior around visible binary tokens such as NUL/STX/DEL. */
public class BinaryTokenEditGuardTest {

  @Test
  public void insertCharAtCursor_shouldSnapOutOfBinaryTokenBeforeInserting() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "insertCharAtCursor(char c)");

    assertTrue(
        "BUG: typing inside a visible binary token must snap to a token edge so the colored token span does not stretch.",
        body.contains("pos = editor.binaryRender.snapBinaryCursor(base, pos, editor.cursor.cursorLine)")
            && body.indexOf("snapBinaryCursor(base, pos, editor.cursor.cursorLine)")
                < body.indexOf("String modified = base.substring(0, pos) + c + base.substring(pos)"));
    assertTrue(
        "BUG: binary token spans must still be shifted after insertion beside a token.",
        body.contains("editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, pos, 1, 0)"));
  }

  @Test
  public void deleteCharAtCursor_shouldDeleteWholeBinaryTokenAndItsSpan() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "deleteCharAtCursor()");

    assertTrue(
        "BUG: backspace on a visible binary token must detect the full cached token span.",
        body.contains("findBinaryTokenSpanInSpans(")
            && body.contains("safeCursorChar - 1"));
    assertTrue(
        "BUG: backspace on a binary token must expand the removed range to the token span.",
        body.contains("safeStart = Math.max(0, Math.min(binarySpan[0], base.length()))")
            && body.contains("safeCursorChar = Math.max(safeStart, Math.min(binarySpan[1], base.length()))"));
    assertTrue(
        "BUG: deleting a binary token must update/remove its colored span.",
        body.contains("editor.binaryRender.adjustBinaryTokenSpansForEdit(")
            && body.contains("safeStart, safeStart - safeCursorChar, 0"));
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
}
