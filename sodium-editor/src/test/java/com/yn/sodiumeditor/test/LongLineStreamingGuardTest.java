package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards long-line streaming behavior for file-backed rendering. */
public class LongLineStreamingGuardTest {

  @Test
  public void textRange_usesViewportBoundedStreamedSlices() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/TextRange.java");
    String sliceBody = methodBody(src, "public void computeStreamedSliceBounds(");
    String initialBody = methodBody(src, "public int getInitialStreamedSliceSize(");

    assertTrue(
        "BUG: streamed slice bounds must depend on horizontal scroll.",
        sliceBody.contains("getEffectiveScrollX()"));
    assertTrue(
        "BUG: streamed slice bounds must include padding around the viewport.",
        sliceBody.contains("padding"));
    assertFalse(
        "BUG: streamed slice bounds must not always request the whole line.",
        sliceBody.contains("out[0] = 0;\n    out[1] = lineLength;"));
    assertTrue(
        "BUG: initial streamed slice size should be viewport-based and non-zero.",
        initialBody.contains("visibleCols") && initialBody.contains("2048"));
    assertFalse(
        "BUG: initial streamed slice size must not be zero.", initialBody.contains("return 0;"));
  }

  @Test
  public void viewRender_updatesStreamedSlicesBeforeDrawingVisibleLines() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
    String body = methodBody(src, "private void drawTextContent(");
    int update =
        body.indexOf("maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine)");
    int loop = body.indexOf("for (int i = firstVisibleLine; i <= lastVisibleLine; i++)");
    assertTrue(
        "BUG: ViewRender should request visible streamed slices before drawing lines.",
        update >= 0);
    assertTrue("BUG: streamed slice update should happen before the draw loop.", update < loop);
  }

  @Test
  public void sodiumEditor_heavyDrawSuppressionReflectsRuntimeState() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/SodiumEditor.java");
    String body = methodBody(src, "public boolean isHeavyDrawSuppressed(");
    assertTrue(body.contains("scrollerIsScrolling"));
    assertTrue(body.contains("isZoomGestureActive()"));
    assertTrue(body.contains("heavyFeaturesThreshold"));
    assertTrue(body.contains("isWindowLoading"));
    assertFalse(
        "BUG: heavy draw suppression must not be permanently disabled.",
        body.contains("return false;"));
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
    Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
    if (Files.exists(candidate)) return candidate;
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
