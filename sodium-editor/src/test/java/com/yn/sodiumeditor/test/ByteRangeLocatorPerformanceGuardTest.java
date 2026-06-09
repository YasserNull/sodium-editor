package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards byte-range performance fixes from optimize.md. */
public class ByteRangeLocatorPerformanceGuardTest {

  @Test
  public void byteRangeLocator_usesReadyLineIndexAndBufferedScanning() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/ByteRangeLocator.java");
    String fastBody = methodBody(src, "public EditOp.RangeBytes computeByteRangeFastOrScan(");
    String twoLineScanBody = methodBody(src, "public long[] findTwoLineStartBytesByScanning(");
    String oneLineScanBody = methodBody(src, "public long findLineStartByteByScanning(");

    assertTrue(
        "BUG: byte-range computation should use lineOffsets when the file index is ready.",
        fastBody.contains("computeByteRangeFromReadyIndex"));
    assertTrue(
        "BUG: two-line fallback scanning should read chunks into a reusable byte buffer.",
        twoLineScanBody.contains("byte[] buffer") && twoLineScanBody.contains("raf.read(buffer)"));
    assertTrue(
        "BUG: single-line fallback scanning should read chunks into a reusable byte buffer.",
        oneLineScanBody.contains("byte[] buffer") && oneLineScanBody.contains("raf.read(buffer)"));
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
