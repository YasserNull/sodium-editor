package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards typed multi-line insertion crossing 99->100 visible line numbers. */
public class TypingLineNumberGutterWidthGuardTest {

  @Test
  public void typedMultiLineInsert_shouldRecomputeGutterWidthWhenLineNumberDigitsGrow() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/WindowRender.java");
    String body = methodBody(src, "public void applyMultiLineReplaceInWindowNow");

    int digitChange = body.indexOf("String.valueOf(oldLineCount).length()");
    int update = body.indexOf("editor.lineNumber.updateGutterWidth()", digitChange);
    int request = body.indexOf("editor.requestLayout()", digitChange);

    assertTrue("Expected digit-count change check in typed multi-line replace.", digitChange >= 0);
    assertTrue(
        "BUG: typing/pasting enough lines to cross 99->100 must recompute gutter width; otherwise"
            + " line 100 is clipped and appears as 00.",
        update >= 0 && request >= 0 && update < request);
  }

  private static String methodBody(String src, String signaturePrefix) {
    int method = src.indexOf(signaturePrefix);
    if (method < 0) throw new IllegalStateException("Method not found: " + signaturePrefix);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + signaturePrefix);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + signaturePrefix);
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
