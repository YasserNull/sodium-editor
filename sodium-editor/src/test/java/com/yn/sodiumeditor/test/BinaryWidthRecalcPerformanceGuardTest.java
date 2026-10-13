package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.core.binary.BinaryWidthSampler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Binary documents use fixed 256-byte rows. After every window reload the max line width used to be
 * recomputed by measuring ALL window rows (~750 x 256 garbage glyphs) on the UI thread, which drops
 * frames while scrolling a large binary file. Only the longest few rows need to be measured.
 */
public class BinaryWidthRecalcPerformanceGuardTest {

  @Test
  public void sampler_returnsAtMostMaxSamples_longestFirstStableOnTies() {
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < 750; i++) rows.add(repeat('a', 256));
    rows.set(10, repeat('b', 300));
    rows.set(400, repeat('c', 290));

    int[] picked = BinaryWidthSampler.longestRowIndices(rows, 4);

    assertEquals(4, picked.length);
    assertEquals(10, picked[0]);
    assertEquals(400, picked[1]);
    // Ties (256 chars) resolve to the earliest rows.
    assertArrayEquals(new int[] {10, 400, 0, 1}, picked);
  }

  @Test
  public void sampler_handlesSmallAndEmptyInput() {
    assertEquals(0, BinaryWidthSampler.longestRowIndices(new ArrayList<String>(), 8).length);
    assertEquals(0, BinaryWidthSampler.longestRowIndices(null, 8).length);
    assertArrayEquals(
        new int[] {1, 0},
        BinaryWidthSampler.longestRowIndices(Arrays.asList("ab", "abc"), 8));
    assertEquals(0, BinaryWidthSampler.longestRowIndices(Arrays.asList("ab"), 0).length);
  }

  @Test
  public void windowRecalc_shouldSampleRowsInBinaryDocumentMode() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/WindowRender.java");
    String body = methodBody(src, "public void recalculateMaxLineWidth()");
    assertTrue(
        "BUG: recalculateMaxLineWidth must not measure every row of a binary document.",
        body.contains("BinaryWidthSampler.longestRowIndices"));
  }

  @Test
  public void asyncRecalc_shouldNotChunkMeasureAllRowsInBinaryDocumentMode() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String body = methodBody(src, "public void recalculateMaxLineWidthAsync()");
    int sampler = body.indexOf("BinaryWidthSampler.longestRowIndices");
    int chunk = body.indexOf("idx + 120");
    assertTrue(
        "BUG: async width recalc must sample binary rows instead of chunk-measuring them all.",
        sampler >= 0 && (chunk < 0 || sampler < chunk));
  }

  private static String repeat(char c, int n) {
    char[] a = new char[n];
    Arrays.fill(a, c);
    return new String(a);
  }

  private static String readSource(String rel) throws Exception {
    Path p = Paths.get(rel);
    if (!Files.exists(p)) p = Paths.get("..").resolve(rel);
    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  private static String methodBody(String src, String signature) {
    int start = src.indexOf(signature);
    assertTrue("method not found: " + signature, start >= 0);
    int open = src.indexOf('{', start);
    int depth = 0;
    for (int i = open; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
    }
    throw new IllegalStateException("unbalanced braces for " + signature);
  }
}
