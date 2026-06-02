package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards color-code background drawing behavior. */
public class ColorCodeHighlightGuardTest {

  @Test
  public void colorCodeHighlight_shouldDetectColorsAndDrawCachedBackgroundRects() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/ColorCodeHighlight.java");
    String pattern = fieldInitializer(src, "public static final Pattern COLOR_HEX_PATTERN");
    String body = methodBody(src, "drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine)");

    assertTrue("BUG: color code pattern must detect short #RGB colors.", pattern.contains("{3,4}"));
    assertTrue("BUG: color code pattern must detect #RRGGBB and #AARRGGBB colors.", pattern.contains("{6}"));
    assertTrue("BUG: color code pattern must detect 0xRRGGBB and 0xAARRGGBB colors.", pattern.contains("0x"));
    assertTrue(
        "BUG: 0xRRGGBB color codes must be made opaque before drawing.",
        body.contains("if (hex.length() == 6) hex = \"FF\" + hex"));
    assertTrue(
        "BUG: color code backgrounds must use a visible overlay alpha.",
        body.contains("(color & 0x00FFFFFF) | (0xC0 << 24)"));
    assertTrue(
        "BUG: color code drawing must cache start/end/color triples per line.",
        body.contains("tmp.add(matcher.start())")
            && body.contains("tmp.add(matcher.end())")
            && body.contains("tmp.add(backgroundColor)")
            && body.contains("colorCodeBgCache.put(globalLine, triples)"));
    assertTrue(
        "BUG: color code drawing must measure exact text bounds and draw a rectangle over the literal.",
        body.contains("editor.textRender.measureText(line, start, globalLine)")
            && body.contains("editor.textRender.measureText(line, end, globalLine)")
            && body.contains("canvas.drawRect(left, top, right, bottom, colorOverlayPaint)"));
  }

  @Test
  public void drawColorCodeBackgrounds_shouldBeCalledFromViewRenderDrawLoop() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
    String drawLoop = methodBody(src, "void drawTextContent(Canvas canvas,");
    assertTrue(
        "BUG: drawColorCodeBackgrounds must be called in the render loop, otherwise color swatches are never drawn.",
        drawLoop.contains("drawColorCodeBackgrounds(canvas, line, i)"));
  }

  @Test
  public void colorCodeHighlight_shouldSkipLinesWithoutColorPrefixes() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/ColorCodeHighlight.java");
    String body = methodBody(src, "drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine)");

    assertTrue(
        "BUG: color code drawing should skip obvious non-color lines before regex work.",
        body.contains("line.indexOf('#') < 0 && line.indexOf('0') < 0"));
    assertFalse(
        "BUG: disabled color highlighting must not continue into drawing work.",
        body.startsWith("Matcher matcher"));
  }

  private static String fieldInitializer(String src, String fieldPrefix) {
    int field = src.indexOf(fieldPrefix);
    if (field < 0) throw new IllegalStateException("Field not found: " + fieldPrefix);
    int end = src.indexOf(";", field);
    if (end < 0) throw new IllegalStateException("Field initializer not found: " + fieldPrefix);
    return src.substring(field, end + 1);
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
