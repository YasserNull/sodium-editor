package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards scroll bounds after collapsing large folds. */
public class CodeFoldScrollBoundsGuardTest {

  @Test
  public void indexedFile_shouldClampScrollToVisibleFoldedContentWithoutVirtualTail() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/scroll/ScrollBounds.java");

    String body = methodBody(src, "getMaxScrollYForClamp()");
    assertTrue(
        "BUG: once the file index is ready, scroll bounds know the real content end. Collapsed large folds must clamp to visibleLineCount without adding virtualExtra blank scroll space.",
        body.contains("contentEndKnown")
            && body.contains("editor.fileIO.isEof || editor.fileIO.isIndexReady")
            && body.contains("if (contentEndKnown)")
            && body.indexOf("if (contentEndKnown)") < body.indexOf("virtualExtra"));
    assertTrue(
        "BUG: folded scroll bounds must use codeFold.getVisibleLineCount() for non-wrapped content.",
        body.contains("editor.codeFold.getVisibleLineCount()"));
  }

  private static String methodBody(String src, String methodName) {
    int method = src.indexOf(methodName);
    if (method < 0) throw new IllegalStateException("Method not found: " + methodName);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + methodName);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + methodName);
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
    Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
    if (Files.exists(candidate)) return candidate;
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
