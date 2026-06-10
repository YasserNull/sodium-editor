package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards remaining high-impact performance/correctness fixes. */
public class RemainingPerformanceCorrectnessGuardTest {

  @Test
  public void heavyFeatureCheck_runsAfterIndexIsReady() throws Exception {
    String fileIO = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String load = methodBody(fileIO, "public void loadFromFile(");
    String indexer =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIndexer.java");
    String build = methodBody(indexer, "public void buildFileIndex(");

    assertFalse(
        "BUG: checkHeavyFeatures should not run immediately after async buildFileIndex post.",
        load.contains("checkHeavyFeatures();"));
    assertTrue(
        "BUG: FileIndexer should apply heavy feature policy after lineOffsets are ready.",
        build.contains("checkHeavyFeaturesAfterIndexReady"));
  }

  @Test
  public void modifiedLines_doesNotDropUnsavedEditsAtFixedLimit() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/WindowRender.java");
    String field =
        src.substring(
            src.indexOf("public final java.util.LinkedHashMap<Integer, String> modifiedLines"));
    field = field.substring(0, field.indexOf("private int firstModifiedLine"));
    assertFalse(
        "BUG: modifiedLines must not evict unsaved edits at 1000 entries.",
        field.contains("size() > 1000"));
    assertFalse(
        "BUG: modifiedLines should not implement removeEldestEntry eviction.",
        field.contains("removeEldestEntry"));
  }

  @Test
  public void readLineByScanningFile_decodesUtf8InsteadOfCastingBytes() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileCache.java");
    String body = methodBody(src, "public String readLineByScanningFile(");
    assertTrue(body.contains("ByteArrayOutputStream"));
    assertTrue(body.contains("new String(") && body.contains("fileIO.fileCharset"));
    assertFalse("BUG: byte-to-char casts corrupt UTF-8 text.", body.contains("sb.append((char)"));
  }

  @Test
  public void bracketSpanCacheDrawsVisibleIntersectionsAtFrameTime() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/guides/bracket/BracketGuideSpanCache.java");
    String draw = methodBody(src, "public void drawBracketGuidesForVisibleRange(");
    String build = methodBody(src, "public void buildSpanCacheAsync(");
    assertTrue(draw.contains("Math.max(spanStart, visibleStart)"));
    assertTrue(draw.contains("Math.min(spanEnd, visibleEnd)"));
    assertTrue(draw.contains("canvas.drawLine"));
    assertFalse(
        "BUG: span cache should not draw all precomputed segments regardless of visibility.",
        draw.contains("canvas.drawLines"));
    assertFalse(
        "BUG: span cache build should not compute frame-dependent Y coordinates in background.",
        build.contains("getDrawLineTop"));
  }

  @Test
  public void viewRenderDoesNotDoDiskBackedDirectReadsDuringDraw() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
    String body = methodBody(src, "private void drawTextContent(");
    assertFalse(
        "BUG: draw path should not call disk-backed direct line reads.",
        body.contains("populateDirectLinesForRange"));
    assertTrue(
        "BUG: draw path should ask the async window loader to catch up.",
        body.contains("checkAndLoadWindow()"));
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
