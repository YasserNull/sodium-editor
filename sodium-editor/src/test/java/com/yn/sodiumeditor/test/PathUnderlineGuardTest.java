package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards path underline drawing behavior. */
public class PathUnderlineGuardTest {

  @Test
  public void pathUnderline_shouldOnlyDrawValidatedExistingPaths() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/PathUnderline.java");
    String body = methodBody(src, "getPathUnderlineSpansForLine(String line, int globalLine)");

    assertTrue(
        "BUG: path underline must check the validation cache before drawing an underline.",
        body.contains("Boolean exists = pathValidationCache.get(path)"));
    assertTrue(
        "BUG: path underline must only add a drawn span for paths validated as existing.",
        body.contains("exists != null && exists"));
    assertTrue(
        "BUG: path underline spans must be marked as path spans so drawing can style them correctly.",
        body.contains("new TextRender.UnderlineSpan(s, e, true)"));
    assertTrue(
        "BUG: path underline must queue background validation for paths missing from the cache.",
        body.contains("validatePathInBackground(path, globalLine)"));
  }

  @Test
  public void pathUnderline_shouldTrimTrailingPunctuationBeforeValidation() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/PathUnderline.java");
    String body = methodBody(src, "getPathUnderlineSpansForLine(String line, int globalLine)");

    assertTrue(
        "BUG: path underline must trim punctuation before using the path for validation and drawing.",
        body.indexOf("while (e > s)") < body.indexOf("String path = line.substring(s, e)"));
    assertTrue(body.contains("c == '.'"));
    assertTrue(body.contains("c == ','"));
    assertTrue(body.contains("c == ';'"));
    assertTrue(body.contains("c == ':'"));
    assertTrue(body.contains("c == '!'"));
    assertTrue(body.contains("c == '?'"));
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
