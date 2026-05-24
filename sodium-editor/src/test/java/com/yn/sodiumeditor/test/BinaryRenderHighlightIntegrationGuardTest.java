package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards URL/path underline and measurement behavior while binary rendering is enabled. */
public class BinaryRenderHighlightIntegrationGuardTest {

  @Test
  public void highliteRender_shouldDrawUrlAndPathUnderlinesInBinaryFastPath() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighliteRender.java");
    String body = methodBody(src, "drawHighlightedLine(Canvas canvas, String line, int globalLine, float y)");

    assertTrue(
        "BUG: binary fast path must not bypass URL/path underlines.",
        body.contains("drawUrlAndPathUnderlinesForBinaryLine(canvas, line, globalLine, y)"));
    assertTrue(
        "BUG: URL/path underlines should be drawn before returning from binary fast path.",
        body.indexOf("drawUrlAndPathUnderlinesForBinaryLine(canvas, line, globalLine, y)")
            < body.indexOf("return;"));
  }

  @Test
  public void binaryUnderlineHelper_shouldCollectBothUrlAndPathSpans() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighliteRender.java");
    String body =
        methodBody(
            src,
            "drawUrlAndPathUnderlinesForBinaryLine(Canvas canvas, String line, int globalLine, float y)");

    assertTrue(
        "BUG: binary underline helper must collect URL underline spans.",
        body.contains("editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine)"));
    assertTrue(
        "BUG: binary underline helper must collect path underline spans.",
        body.contains("editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine)"));
    assertTrue(
        "BUG: binary underline helper must use binary-aware text measurement for underline X.",
        body.contains("editor.textRender.measureText(line, start, globalLine)"));
  }

  @Test
  public void textRender_shouldMeasureRealTextWhenBinaryModeHasNoTokenSpans() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/TextRender.java");
    String body = methodBody(src, "measureText(String line, int length, int globalLine)");

    assertTrue(
        "BUG: binary mode with no token spans must measure real text, not assume M-width characters.",
        body.contains("return measureTextWithVisualSpaces(line, 0, safeLen, paint)"));
    assertTrue(
        "BUG: M-width fallback causes color-code backgrounds to shift for Arabic and proportional text.",
        !body.contains("effectiveAvgWidth"));
  }

  @Test
  public void binaryLineDrawer_shouldAdvanceNormalTextWithPaintMeasurement() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/draw/BinaryLineDrawer.java");
    String xBody = methodBody(src, "getXForCharBinary(String line, int charIndex, Paint paint, int[] spans, float padX)");
    String drawBody =
        methodBody(
            src,
            "drawBinaryLineSlice(\n            Canvas canvas,\n            String line,\n            int globalLine,\n            int relStart,\n            int relEnd,\n            int sliceStart,\n            float y,\n            Paint defaultPaint,\n            android.util.SparseArray<int[]> binaryTokenSpans,\n            int fadeStart,\n            int fadeEnd,\n            float fadeAlpha)");

    assertTrue(
        "BUG: binary X calculation must measure normal text with Paint for Unicode/proportional glyphs.",
        xBody.contains("paint.measureText(line, pos, idx)")
            && xBody.contains("paint.measureText(line, pos, s)"));
    assertTrue(
        "BUG: binary drawing must advance normal text by measured width, not fixed char count.",
        drawBody.contains("defaultPaint.measureText(line, idx, safeS)"));
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
