package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards binary/APK opens from entering expensive text-file warmup paths. */
public class BinaryOpenPerformanceGuardTest {

  @Test
  public void binaryOpen_shouldSkipTextIndexerAndUseSmallRenderWindow() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String body = methodBody(src, "loadFromFile(File file)");
    int branch = body.indexOf("if (binaryFile)");
    int indexPost = body.indexOf("indexer.buildFileIndex()");

    assertTrue("BUG: binary files need a dedicated open branch.", branch >= 0);
    assertTrue(
        "BUG: binary open must return before text indexer warmup.",
        branch < indexPost && body.indexOf("return;", branch) < indexPost);
    assertTrue(
        "BUG: binary open should disable line index state instead of scanning the APK/ZIP.",
        body.contains("isIndexDisabled = true"));
    assertTrue(
        "BUG: binary open should use a much smaller text fallback window.",
        body.contains("editor.windowRender.setRenderWindow(80, 40, false)"));
    assertTrue(
        "BUG: binary open should disable expensive per-token boxes.",
        body.contains("editor.binaryRender.setBinaryTokenBoxEnabled(false)"));
  }

  @Test
  public void binaryPolicy_shouldAvoidDuplicateWindowReloadDuringFileOpen() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/BinaryRender.java");
    String setter =
        methodBody(
            src, "public void setBinarySafeRenderingEnabled(boolean enabled, boolean reload)");
    String policy = methodBody(src, "applyBinaryFileFeaturePolicy(boolean active)");

    assertTrue(
        "BUG: binary safe setter needs a reload flag so loadFromFile can avoid duplicate reloads.",
        setter.contains("if (reload)"));
    assertTrue(
        "BUG: binary feature policy during open should not reload before loadWindowAround.",
        policy.contains("setBinarySafeRenderingEnabled(false, false)"));
  }

  @Test
  public void resetForNewFile_shouldClearBinarySpans() throws Exception {
    String fileIO = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String binaryRender =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/BinaryRender.java");
    String reset = methodBody(fileIO, "private void resetStateForNewFile()");

    assertTrue(binaryRender.contains("public void clearBinaryTokenSpans()"));
    assertTrue(
        "BUG: binary token spans from an old file must not survive into the next file.",
        reset.contains("editor.binaryRender.clearBinaryTokenSpans()"));
  }

  @Test
  public void binaryLineScan_shouldHaveSmallCap() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileMetadata.java");
    String body = methodBody(src, "public LineScanResult scanLineLength(");

    assertTrue(src.contains("MAX_BINARY_LINE_SCAN_BYTES"));
    assertTrue(
        "BUG: binary fallback line scanning must stop quickly when no newline exists.",
        body.contains("binaryFileFeaturePolicyActive")
            && body.contains("MAX_BINARY_LINE_SCAN_BYTES"));
  }

  @Test
  public void binaryWindowLoader_shouldUseFixedRowsNotLineScanning() throws Exception {
    String loader =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    String binaryBody = methodBody(loader, "private void loadBinaryWindowInternal(");
    String normalBody = methodBody(loader, "private void loadWindowInternal(");
    String document =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/binary/BinaryDocument.java");

    assertTrue(document.contains("public static final int BYTES_PER_ROW = 256"));
    assertTrue(
        normalBody.contains(
            "loadBinaryWindowInternal(actualStart, taskVersion, onComplete, recalcWidthSync)"));
    assertTrue(binaryBody.contains("document.getOffsetForRow(row)"));
    assertTrue(binaryBody.contains("BinaryDocument.BYTES_PER_ROW"));
    assertTrue(
        "BUG: binary fast path should still display normal decoded chunks, not hex rows.",
        binaryBody.contains("new String(rowBuffer, 0, len, fileIO.fileCharset)"));
    assertFalse(
        "BUG: binary fast path must not force the ugly hex renderer.",
        binaryBody.contains("rawBytesToHexAsciiLine"));
    assertFalse(
        "BUG: binary/APK window load must not skip rows by scanning newlines from the start.",
        binaryBody.contains("scanLineLength"));
  }

  @Test
  public void binaryScrollAndLineCount_shouldUseBinaryRowCount() throws Exception {
    String view = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/view/View.java");
    String scroll =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/scroll/ScrollBounds.java");
    String gutter =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/GutterUtils.java");
    String fileIO = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");

    assertTrue(
        methodBody(view, "public int getLinesCount()").contains("binaryDocument.getRowCount()"));
    assertTrue(
        "BUG: binary files have a known end even without text index offsets.",
        methodBody(scroll, "public float getMaxScrollYForClamp()")
            .contains("binaryDocument != null"));
    assertTrue(
        "BUG: gutter width should use binary row count instead of fake unknown count.",
        methodBody(gutter, "public int estimateLineCountForGutter()")
            .contains("binaryDocument.getRowCount()"));
    assertTrue(
        "BUG: go-to/select-all line counting must not scan binary files.",
        methodBody(fileIO, "public void countTotalLines(")
            .contains("binaryDocument.getRowCount()"));
  }

  @Test
  public void byteTokenConverter_shouldPreserveTextPathButExposeRawBytePath() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/binary/BinaryTokenConverter.java");

    assertTrue(
        "BUG: text-safe conversion must stay available for UTF-8 text opened in binary-safe mode.",
        src.contains("charsToControlVisible(new String(buf, 0, len, safeCharset))"));
    assertTrue(
        "BUG: raw binary conversion should avoid UTF-8 decode/replacement churn.",
        src.contains("rawBytesToControlVisible("));
    assertFalse(
        "BUG: raw binary byte path must not decode bytes through String first.",
        methodBody(src, "rawBytesToControlVisible(byte[] buf, int len)")
            .contains("new String(buf"));
  }

  @Test
  public void binaryOpen_shouldDisableBinaryRendering() throws Exception {
    String binaryRender =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/BinaryRender.java");
    String policy = methodBody(binaryRender, "applyBinaryFileFeaturePolicy(boolean active)");

    assertTrue(
        "BUG: opening binary files should use the fast binary document model without binary token"
            + " rendering.",
        policy.contains("setBinarySafeRenderingEnabled(false, false)"));
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
