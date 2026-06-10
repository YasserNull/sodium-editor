package com.yn.sodiumeditor.core.wordwrap;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.utils.WordWrapUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Main facade for Word Wrap functionality in SodiumEditor. */
public class WordWrap {
  private final SodiumEditor editor;
  public final Handler mainHandler;

  // Components
  public final WordWrapEngine engine;
  public final WordWrapIndicator indicator;
  public final WordWrapUtils calculator;
  public final WordWrapMetrics metrics;
  public final WordWrapSnapshotMetrics snapshotMetrics;
  public final WordWrapPrefixBuilder prefixBuilder;
  public final WordWrapPosition position;
  public final WordWrapCache cache;
  public final WordWrapFileLineReader fileLineReader;

  // --- State (Kept as fields for project compatibility) ---
  public boolean isWordWrapEnabled = false;
  public int wrapWidthPx = -1;
  public final HashMap<Integer, int[]> wrapCache = new HashMap<>();
  public volatile int[] wrapLineCounts = null;
  public volatile int[] wrapLinePrefix = null;
  public volatile int wrapPrefixValidUpToLine = -1;
  public volatile int totalWrapVisualLines = 0;
  public volatile boolean wrapMetricsReady = false;
  public volatile int wrapMetricsWidth = -1;
  public final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  public volatile boolean wrapMetricsBuilding = false;
  public final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  public volatile boolean wrapSnapshotBuilding = false;
  public volatile int wrapSnapshotWidth = -1;
  public volatile int wrapSnapshotStart = -1;
  public volatile int wrapSnapshotSize = -1;
  public final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  public volatile boolean wrapPrefixBuilding = false;
  public volatile int wrapPrefixWidth = -1;
  public volatile int wrapPrefixTargetLine = -1;
  public boolean wrapPrefixRebuildPending = false;

  public static class VisualLinePosition {
    public final int line, segment;

    public VisualLinePosition(int l, int s) {
      line = l;
      segment = s;
    }
  }

  public WordWrap(SodiumEditor editor) {
    this.editor = editor;
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.engine = new WordWrapEngine(editor, this);
    this.indicator = new WordWrapIndicator(editor);
    this.calculator = new WordWrapUtils(editor);
    this.metrics = new WordWrapMetrics(editor, this);
    this.snapshotMetrics = new WordWrapSnapshotMetrics(editor, this);
    this.prefixBuilder = new WordWrapPrefixBuilder(editor, this);
    this.position = new WordWrapPosition(editor, this);
    this.cache = new WordWrapCache(editor, this);
    this.fileLineReader = new WordWrapFileLineReader(editor, this);
  }

  // ==============================
  // Public API
  // ==============================

  public void setWordWrapEnabled(boolean e) {
    if (this.isWordWrapEnabled == e) return;
    this.isWordWrapEnabled = e;
    invalidateWrapMetrics();
    if (e) {
      editor.scroll.scrollX = 0f;
      editor.scroll.clampScrollX();
      editor.windowRender.clearStreamedLineCaches();
      editor.windowRender.reloadWindowAroundVisible(false);
    }
    editor.requestLayout();
    editor.invalidate();
  }

  public void setWordWrapIndicatorEnabled(boolean enabled) {
    indicator.setWordWrapIndicatorEnabled(enabled);
  }

  public boolean isWordWrapIndicatorEnabled() {
    return indicator.isWordWrapIndicatorEnabled;
  }

  public boolean getWordWrapIndicatorEnabled() {
    return indicator.isWordWrapIndicatorEnabled;
  }

  public void setWordWrapIndicatorColor(int color) {
    indicator.setWordWrapIndicatorColor(color);
  }

  public void setWordWrapIndicatorText(String text) {
    indicator.setWordWrapIndicatorText(text);
  }

  public String getWordWrapIndicatorText() {
    return indicator.getWordWrapIndicatorText();
  }

  public void setWordWrapIndicatorTextSize(float sizeSp) {
    indicator.setWordWrapIndicatorTextSize(sizeSp);
  }

  public float getWordWrapIndicatorTextSize() {
    return indicator.getWordWrapIndicatorTextSize();
  }

  public float getWrapWidth() {
    return Math.max(1f, editor.getWidth() - editor.layout.getTextStartX());
  }

  public void invalidateWrapMetrics() {
    invalidateWrapMetrics(true, true);
  }

  public void invalidateWrapMetrics(boolean clear) {
    invalidateWrapMetrics(clear, true);
  }

  public void invalidateWrapMetrics(boolean clear, boolean fullRebuild) {
    cache.clear();
    wrapMetricsWidth = -1;
    wrapMetricsToken.incrementAndGet();
    wrapPrefixValidUpToLine = -1;
    if (clear) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
    }
    buildWrapMetricsForWindowSnapshot();
    if (isWordWrapEnabled) {
      if (fullRebuild) scheduleWrapMetricsBuild();
      else {
        int w = Math.max(1, Math.round(getWrapWidth()));
        scheduleWrapMetricsSnapshotIfNeeded(w);
        scheduleWrapPrefixRebuildUpToWindow();
      }
    }
  }

  // ==============================
  // Bridge Methods (Delegated)
  // ==============================

  public void scheduleWrapMetricsBuild() {
    metrics.scheduleWrapMetricsBuild();
  }

  public void buildWrapMetricsForWindowSnapshot() {
    snapshotMetrics.buildWrapMetricsForWindowSnapshot();
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(int w) {
    snapshotMetrics.scheduleWrapMetricsSnapshotIfNeeded(w);
  }

  public void scheduleWrapPrefixRebuildUpToWindow() {
    prefixBuilder.scheduleWrapPrefixRebuildUpToWindow();
  }

  public void requestWrapPrefixRebuild() {
    prefixBuilder.requestWrapPrefixRebuild();
  }

  public void cancelWrapPrefixRebuildForInteraction() {
    prefixBuilder.cancelWrapPrefixRebuildForInteraction();
  }

  public void cancelWrapWorkForPriority() {
    prefixBuilder.cancelWrapWorkForPriority();
  }

  public boolean shouldSuppressWrapMetricsForFastSelectAll() {
    if (!isWordWrapEnabled
        || (!editor.selection.isSelectAllActive && !editor.selection.isEntireFileSelected))
      return false;
    return !isWrapMetricsUsableForWindow(Math.max(1, Math.round(getWrapWidth())));
  }

  public void onLineContentChanged(int gl, @Nullable String text) {
    metrics.onLineContentChanged(gl, text);
  }

  public void onLineCountChanged() {
    metrics.onLineCountChanged();
  }

  public int computeWrapCountForLine(String line, int w) {
    return engine.computeWrapCountForLine(line, w);
  }

  public int[] getWrapStartsForLine(int gl, String line) {
    return cache.getWrapStartsForLine(gl, line);
  }

  public boolean isWrapCacheableForLine(int gl) {
    return cache.isWrapCacheableForLine(gl);
  }

  public int getWrapSegmentIndexForChar(int[] s, int ci) {
    return engine.getWrapSegmentIndexForChar(s, ci);
  }

  public int getWrapSegmentStart(int[] s, int si) {
    return engine.getWrapSegmentStart(s, si);
  }

  public int getWrapSegmentEnd(int[] s, int si, int len) {
    return engine.getWrapSegmentEnd(s, si, len);
  }

  public int getTotalVisualLineCount() {
    return metrics.getTotalVisualLineCount();
  }

  public int getWrapRangeCount(int s, int e) {
    return metrics.getWrapRangeCount(s, e);
  }

  public VisualLinePosition getVisualPositionForIndex(int vi) {
    return position.getVisualPositionForIndex(vi);
  }

  public int findLineForVisualIndex(int vi) {
    return position.findLineForVisualIndex(vi);
  }

  public int getVisualIndexForLineAndChar(int l, int c) {
    return position.getVisualIndexForLineAndChar(l, c);
  }

  public EditOp.CursorTarget getCursorTargetForPosition(float x, float y, Map<Integer, String> dl) {
    return position.getCursorTargetForPosition(x, y, dl);
  }

  public boolean isWrapMetricsUsableForWindow(int w) {
    return metrics.isWrapMetricsUsableForWindow(w);
  }

  public boolean isWrapMetricsUsableForLine(int l) {
    return metrics.isWrapMetricsUsableForLine(l);
  }

  public boolean patchWrapMetricsForVisualRange(
      int fv, int lv, @Nullable Map<Integer, String> dl, int w) {
    return metrics.patchWrapMetricsForVisualRange(fv, lv, dl, w);
  }

  public int clampSegmentEndForWrapIndicator(String line, int ss, int se) {
    return engine.clampSegmentEndForWrapIndicator(line, ss, se);
  }

  public int clampSegmentEndForWrapIndicator(String line, int ss, int se, int w) {
    return engine.clampSegmentEndForWrapIndicator(line, ss, se, w);
  }

  public int getCharIndexForXInRange(String text, int gl, int s, int e, float x) {
    return engine.getCharIndexForXInRange(text, gl, s, e, x);
  }

  public void applyPendingWrapPrefixUpdateIfAny() {
    prefixBuilder.applyPendingWrapPrefixUpdateIfAny();
  }

  public int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / editor.textRender.lineHeight));
    return isWordWrapEnabled ? getVisualPositionForIndex(idx).line : idx;
  }
}
