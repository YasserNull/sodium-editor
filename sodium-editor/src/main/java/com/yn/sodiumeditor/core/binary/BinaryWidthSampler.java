package com.yn.sodiumeditor.core.binary;

import java.util.List;

/**
 * Picks the few widest candidates among fixed-size binary rows so the max line width can be
 * estimated without measuring every row of the render window (which is expensive for garbage
 * glyphs and runs on the UI thread).
 */
public final class BinaryWidthSampler {
  /** Rows measured per recalculation. */
  public static final int MAX_SAMPLES = 8;

  private BinaryWidthSampler() {}

  /**
   * Returns indices of up to {@code max} longest rows, longest first; ties keep the earliest row
   * first. O(rows * max) with no allocation besides the result.
   */
  public static int[] longestRowIndices(List<String> rows, int max) {
    if (rows == null || max <= 0 || rows.isEmpty()) return new int[0];
    int cap = Math.min(max, rows.size());
    int[] idx = new int[cap];
    int[] len = new int[cap];
    int count = 0;
    for (int i = 0; i < rows.size(); i++) {
      String row = rows.get(i);
      int l = row == null ? 0 : row.length();
      if (count == cap && l <= len[count - 1]) continue;
      int pos = Math.min(count, cap - 1);
      if (count < cap) count++;
      while (pos > 0 && len[pos - 1] < l) {
        idx[pos] = idx[pos - 1];
        len[pos] = len[pos - 1];
        pos--;
      }
      idx[pos] = i;
      len[pos] = l;
    }
    return count == cap ? idx : java.util.Arrays.copyOf(idx, count);
  }
}
