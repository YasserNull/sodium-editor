package com.yn.sodiumeditor.state;

import java.util.HashMap;

public final class WrapWordMetrics {

  //================================================================================
  // Wrap Metrics Data
  //================================================================================

  public int[] wrapLineCounts = null;
  public int[] wrapLinePrefix = null;
  public int wrapPrefixValidUpToLine = -1;
  public int totalWrapVisualLines = 0;
  public boolean wrapMetricsReady = false;
  public int wrapMetricsWidth = -1;
  public int wrapWidthPx = -1;

  //================================================================================
  // Wrap Cache
  //================================================================================

  public final HashMap<Integer, int[]> wrapCache = new HashMap<>();

  //================================================================================
  // Pending Updates
  //================================================================================

  public int[] pendingWrapPrefixCounts = null;
  public int[] pendingWrapPrefixPrefix = null;
  public int pendingWrapPrefixTotalVisualLines = 0;
  public int pendingWrapPrefixWidthPx = -1;
  public int pendingWrapPrefixValidUpToLine = -1;
  public boolean pendingApplyWrapPrefixUpdate = false;

  //================================================================================
  // Methods
  //================================================================================

  public void clear() {
    wrapCache.clear();
    wrapLineCounts = null;
    wrapLinePrefix = null;
    wrapPrefixValidUpToLine = -1;
    totalWrapVisualLines = 0;
    wrapMetricsReady = false;
    wrapMetricsWidth = -1;
  }

  public void clearCache() {
    wrapCache.clear();
  }

  public void applyPendingUpdate() {
    if (!pendingApplyWrapPrefixUpdate) return;
    if (pendingWrapPrefixCounts == null || pendingWrapPrefixPrefix == null) {
      pendingApplyWrapPrefixUpdate = false;
      return;
    }

    wrapLineCounts = pendingWrapPrefixCounts;
    wrapLinePrefix = pendingWrapPrefixPrefix;
    totalWrapVisualLines = pendingWrapPrefixTotalVisualLines;
    wrapMetricsWidth = pendingWrapPrefixWidthPx;
    wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, pendingWrapPrefixValidUpToLine);
    wrapMetricsReady = true;

    pendingWrapPrefixCounts = null;
    pendingWrapPrefixPrefix = null;
    pendingApplyWrapPrefixUpdate = false;
  }

  public boolean isUsable(int widthPx, int totalLines) {
    if (!wrapMetricsReady) return false;
    if (wrapLinePrefix == null || wrapLineCounts == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    if (totalLines <= 0) return false;
    if (wrapLineCounts.length != totalLines) return false;
    if (wrapLinePrefix.length != totalLines + 1) return false;
    return true;
  }

  public int getVisualOffsetForLine(int line) {
    if (wrapLinePrefix == null || line < 0 || line >= wrapLinePrefix.length) {
      return line;
    }
    return wrapLinePrefix[line];
  }

  public boolean hasPendingUpdate() {
    return pendingApplyWrapPrefixUpdate;
  }

  public int getLineCount(int lineIndex) {
    if (wrapLineCounts == null || lineIndex < 0 || lineIndex >= wrapLineCounts.length) {
      return 1;
    }
    return wrapLineCounts[lineIndex];
  }

  public void setLineCount(int lineIndex, int count) {
    if (wrapLineCounts != null && lineIndex >= 0 && lineIndex < wrapLineCounts.length) {
      wrapLineCounts[lineIndex] = count;
    }
  }

  public void adjustPrefixFromLine(int fromLine, int delta) {
    if (wrapLinePrefix == null) return;
    for (int i = fromLine + 1; i < wrapLinePrefix.length; i++) {
      wrapLinePrefix[i] += delta;
    }
  }
}
