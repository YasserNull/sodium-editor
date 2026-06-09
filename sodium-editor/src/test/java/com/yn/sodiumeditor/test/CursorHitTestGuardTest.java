package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards normal text cursor hit-testing from binary rendering shortcuts. */
public class CursorHitTestGuardTest {

  @Test
  public void textHitTest_shouldOnlyUseBinaryMappingWhenLineHasBinarySpans() throws Exception {
    String textRender = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/TextRender.java");
    String body = methodBody(textRender, "public int getCharIndexForX(");

    assertTrue(body.contains("shouldUseBinaryRenderingForLine(globalLine)"));
    assertFalse(
        "BUG: normal text hit-testing must not use binary average-width mapping globally.",
        body.contains("if (editor.binaryRender.isBinarySafeRenderingEnabled())"));
  }

  @Test
  public void tapCursorTarget_shouldOnlySnapBinaryCursorForBinaryLines() throws Exception {
    String pos =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/wordwrap/WordWrapPosition.java");
    String body = methodBody(pos, "public EditOp.CursorTarget getCursorTargetForPosition(");

    assertTrue(body.contains("shouldUseBinaryRenderingForLine(pos.line)"));
    assertFalse(
        "BUG: normal text taps must not run through binary cursor snapping.",
        body.contains("isBinarySafeRenderingEnabled() ? editor.binaryRender.snapBinaryCursor"));
  }

  @Test
  public void singleTap_shouldLogRawTapAndResolvedCursorWhenDebugLogsEnabled() throws Exception {
    String tap =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnSingleTapUp.java");

    assertTrue(tap.contains("SodiumEditor.DEBUG_LOGS"));
    assertTrue(tap.contains("operation=tap.cursor"));
    assertTrue(tap.contains("viewX="));
    assertTrue(tap.contains("textX="));
    assertTrue(tap.contains("finalCursor="));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
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
