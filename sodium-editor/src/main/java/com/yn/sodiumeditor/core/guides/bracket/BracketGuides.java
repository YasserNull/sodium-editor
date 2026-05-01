package com.yn.sodiumeditor.core.guides.bracket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.draw.BracketGuideDraw;
import com.yn.sodiumeditor.utils.BracketGuideScanner;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.Collections;
import java.util.List;

/**
 * Manages bracket guides for the SodiumEditor.
 * Draws vertical guide lines for matching braces.
 * This is the main entry point that delegates to specialized components.
 */
public class BracketGuides {

  private final SodiumEditor editor;

  // Bracket guides state
  public boolean isBracketGuidesEnabled = false;
  public final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketGuideStrokeWidth = 4f;
  public float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
  public float baseBracketGuideTextSizePx = 0f;

  // Option: continue drawing guides when lines go off-screen (uses fallback cache)
  public boolean drawGuidesForOffScreenLines = false;

  // Performance options
  public boolean skipGuidesDuringFastScroll = false;
  public long minRebuildIntervalMs = 80;
  private long lastRebuildTimeMs = 0;

  public boolean showGuidesDuringFastScroll = true;
  public boolean useFastBuildDuringFastScroll = true;

  // Component delegates
  public final BracketGuideCache mainCache;
  public final BracketGuideFallbackCache fallbackCache;
  public final BracketGuideCheckpoint checkpoint;
  public final BracketGuideSpanCache spanCache;
  public final BracketGuideAsyncBuilder asyncBuilder;
  public final BracketGuideDraw draw;
  public final BracketGuideScanner scanner;

  public BracketGuides(SodiumEditor editor) {
    FunctionLog.f("BracketGuides", "BracketGuides", editor);
    this.editor = editor;
    bracketGuidePaint.setColor(0xFFCCCCCC);
    bracketGuidePaint.setStyle(Paint.Style.STROKE);
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);

    // Initialize components
    mainCache = new BracketGuideCache(this);
    fallbackCache = new BracketGuideFallbackCache();
    checkpoint = new BracketGuideCheckpoint(editor, this);
    spanCache = new BracketGuideSpanCache(editor, this);
    draw = new BracketGuideDraw(editor, this);
    scanner = new BracketGuideScanner(editor, draw);
    asyncBuilder = new BracketGuideAsyncBuilder(editor, this, mainCache, fallbackCache, checkpoint);
  }

  /**
   * Enables or disables bracket guides.
   */
  public void setBracketGuidesEnabled(boolean enabled) {
    FunctionLog.f("BracketGuides", "setBracketGuidesEnabled", enabled);
    if (this.isBracketGuidesEnabled == enabled) return;
    this.isBracketGuidesEnabled = enabled;
    invalidateBracketGuideCache();
    editor.invalidate();
  }

  /**
   * Enables or disables drawing guides for off-screen lines.
   */
  public void setDrawGuidesForOffScreenLines(boolean enabled) {
    FunctionLog.f("BracketGuides", "setDrawGuidesForOffScreenLines", enabled);
    if (this.drawGuidesForOffScreenLines == enabled) return;
    this.drawGuidesForOffScreenLines = enabled;
    editor.invalidate();
  }

  /**
   * Check if drawing guides for off-screen lines is enabled.
   */
  public boolean isDrawGuidesForOffScreenLinesEnabled() {
    FunctionLog.f("BracketGuides", "isDrawGuidesForOffScreenLinesEnabled");
    return drawGuidesForOffScreenLines;
  }

  /**
   * Enable or disable skipping guide rebuilds during fast scroll.
   */
  public void setSkipGuidesDuringFastScroll(boolean enabled) {
    FunctionLog.f("BracketGuides", "setSkipGuidesDuringFastScroll", enabled);
    this.skipGuidesDuringFastScroll = enabled;
  }

  /**
   * Set minimum interval between cache rebuilds (in milliseconds).
   */
  public void setMinRebuildIntervalMs(long ms) {
    FunctionLog.f("BracketGuides", "setMinRebuildIntervalMs", ms);
    this.minRebuildIntervalMs = Math.max(0, ms);
  }

  /**
   * Sets the bracket guides color.
   */
  public void setBracketGuidesColor(int color) {
    FunctionLog.f("BracketGuides", "setBracketGuidesColor", color);
    bracketGuidePaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the bracket guides stroke width.
   */
  public void setBracketGuidesStrokeWidth(float width) {
    FunctionLog.f("BracketGuides", "setBracketGuidesStrokeWidth", width);
    if (this.bracketGuideStrokeWidth == width) return;
    this.baseBracketGuideStrokeWidth = width;
    this.baseBracketGuideTextSizePx = editor.textRender.paint.getTextSize();
    updateStrokeWidth();
    invalidateBracketGuideCache(true);
    editor.invalidate();
  }

  /**
   * Updates stroke width based on text size.
   */
  public void updateStrokeWidth() {
    FunctionLog.f("BracketGuides", "updateStrokeWidth");
    float sizePx = editor.textRender.paint.getTextSize();
    bracketGuideStrokeWidth = Math.max(
        1f,
        editor.view.scaleByTextSize(baseBracketGuideStrokeWidth, baseBracketGuideTextSizePx, sizePx));
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
  }

  /**
   * Invalidates bracket guide cache.
   */
  public void invalidateBracketGuideCache() {
    FunctionLog.f("BracketGuides", "invalidateBracketGuideCache");
    invalidateBracketGuideCache(false);
  }

  /**
   * Invalidates bracket guide cache.
   */
  public void invalidateBracketGuideCache(boolean configChanged) {
    FunctionLog.f("BracketGuides", "invalidateBracketGuideCache", configChanged);
    // Save current cache to fallback before invalidating
    if (!configChanged && mainCache.bracketGuideCacheStartLine >= 0 && mainCache.bracketGuideCacheEndLine >= mainCache.bracketGuideCacheStartLine
        && mainCache.bracketGuideTokensWindow.size() > 0) {
      fallbackCache.mergeWithMainCache(
          mainCache.bracketGuideTokensWindow,
          mainCache.bracketGuideStatesWindow,
          mainCache.bracketGuideCacheStartLine,
          mainCache.bracketGuideCacheEndLine,
          mainCache.bracketGuideCacheEditVersion);
    }

    // Invalidate main cache
    mainCache.invalidateCache();

    // Invalidate span cache
    spanCache.invalidate();

    // Clear fallback cache only if config changed
    if (configChanged) {
      fallbackCache.invalidate();
    }

    // Reset draw tracking
    draw.resetDrawTracking();
  }

  /**
   * Controls whether guides are drawn during fast scroll/fling.
   */
  public void setShowGuidesDuringFastScroll(boolean enabled) {
    FunctionLog.f("BracketGuides", "setShowGuidesDuringFastScroll", enabled);
    if (this.showGuidesDuringFastScroll == enabled) return;
    this.showGuidesDuringFastScroll = enabled;
    editor.invalidate();
  }

  /**
   * Gets the bracket guide cache config hash.
   */
  public int getBracketGuideCacheConfigHash() {
    FunctionLog.f("BracketGuides", "getBracketGuideCacheConfigHash");
    int h = 1;
    h = 31 * h + Float.floatToIntBits(bracketGuideStrokeWidth);
    h = 31 * h + bracketGuidePaint.getColor();
    return h;
  }

  // ========================================================================
  // Synchronous Bracket Guide Calculation
  // ========================================================================

  /**
   * Calculate bracket guide state for a single line synchronously.
   */
  public BracketGuideState calculateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState prevState) {
    FunctionLog.f("BracketGuides", "calculateBracketGuideStateForLine", line, globalLine, prevState);
    if (prevState == null) {
      prevState = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    }

    BracketGuideState state = BracketGuideScanner.copyState(prevState);
    scanner.updateBracketGuideStateForLine(line != null ? line : "", globalLine, state);

    return state;
  }

  /**
   * Calculate bracket guide state from a starting line to the target line.
   */
  public BracketGuideState calculateBracketGuideStateFromWindowStart(
      int targetLine, int windowStart, int windowEnd,
      @Nullable java.util.Map<Integer, String> directLines) {
    FunctionLog.f("BracketGuides", "calculateBracketGuideStateFromWindowStart", targetLine, windowStart, windowEnd, directLines);

    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);

    int checkpointIdx = checkpoint.getCheckpointIndexForLine(targetLine);
    int startLine = 0;

    if (checkpointIdx >= 0) {
      state = BracketGuideScanner.copyState(checkpoint.getCheckpointState(checkpointIdx));
      startLine = checkpoint.getCheckpointLine(checkpointIdx);
      if (startLine == targetLine) {
        return state;
      }
    } else {
      startLine = 0;
    }

    // Read lines and calculate state from startLine to targetLine (exclusive)
    if (startLine < targetLine && editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      try {
        long offset;
        synchronized (editor.fileIO.lineOffsetsLock) {
          offset = (startLine < editor.fileIO.lineOffsets.length) ? editor.fileIO.lineOffsets[startLine] : 0;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile)) {
          fis.getChannel().position(offset);
          try (java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
               java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {

            int currentLine = startLine;
            String text;
            while ((text = reader.readLine()) != null && currentLine < targetLine) {
              String mod = editor.windowRender.modifiedLines.get(currentLine);
              if (mod != null) text = mod;

              scanner.updateBracketGuideStateForLine(text, currentLine, state);
              currentLine++;
            }
          }
        }
      } catch (Exception e) {
        for (int line = startLine; line < targetLine; line++) {
          String text = scanner.getLineTextForGuideScan(line, directLines, null);
          scanner.updateBracketGuideStateForLine(text != null ? text : "", line, state);
        }
      }
    } else {
      for (int line = startLine; line < targetLine; line++) {
        String text = scanner.getLineTextForGuideScan(line, directLines, null);
        scanner.updateBracketGuideStateForLine(text != null ? text : "", line, state);
      }
    }

    return state;
  }

  /**
   * Ensures bracket guide cache for window.
   */
  public void ensureBracketGuideCacheForWindow(
      int startLine, int endLine, @Nullable java.util.Map<Integer, String> directLines) {
    FunctionLog.f("BracketGuides", "ensureBracketGuideCacheForWindow", startLine, endLine, directLines);
    ensureBracketGuideCacheForWindow(startLine, endLine, startLine, endLine, directLines);
  }

  /**
   * Ensures bracket guide cache for window with visible line priority.
   */
  public void ensureBracketGuideCacheForWindow(
      int startLine, int endLine, int visibleStart, int visibleEnd, @Nullable java.util.Map<Integer, String> directLines) {
    FunctionLog.f("BracketGuides", "ensureBracketGuideCacheForWindow", startLine, endLine, visibleStart, visibleEnd, directLines);
    long startTime = SystemClock.uptimeMillis();

    if (!isBracketGuidesEnabled) {
      return;
    }
    if (startLine > endLine) {
      return;
    }
    if (startLine < 0) {
      invalidateBracketGuideCache(true);
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();

    // Check if cache is already valid
    if (mainCache.isCacheValid(startLine, endLine, v, cfg)) {
      return;
    }

    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;

    if (skipGuidesDuringFastScroll && fastScroll) {
      return;
    }

    // Rate limit rebuilds
    long now = SystemClock.uptimeMillis();
    if (now - lastRebuildTimeMs < minRebuildIntervalMs) {
      return;
    }

    // Don't rebuild if already in progress for same range
    if (mainCache.bracketGuideBuildInProgress
        && mainCache.bracketGuidePendingStart == startLine
        && mainCache.bracketGuidePendingEnd == endLine
        && mainCache.bracketGuidePendingEditVersion == v
        && mainCache.bracketGuidePendingConfigHash == cfg) {
      return;
    }

    // Use faster checkpoint step during fast scroll
    if (fastScroll && useFastBuildDuringFastScroll) {
      checkpoint.bracketGuideCheckpointStep = checkpoint.bracketGuideCheckpointStepFast;
    } else if (!fastScroll) {
      checkpoint.bracketGuideCheckpointStep = 500;
    }

    lastRebuildTimeMs = now;
    final int finalStart = startLine;
    final int finalEnd = endLine;
    final java.util.Map<Integer, String> finalDirectLines = (directLines != null) ? new java.util.HashMap<>(directLines) : null;
    mainCache.bracketGuideBuildInProgress = true;
    mainCache.bracketGuidePendingStart = finalStart;
    mainCache.bracketGuidePendingEnd = finalEnd;
    mainCache.bracketGuidePendingEditVersion = v;
    mainCache.bracketGuidePendingConfigHash = cfg;

    // Post to IO thread for async building
    editor.fileIO.ioHandler.post(() -> asyncBuilder.buildCacheAsync(finalStart, finalEnd, visibleStart, visibleEnd, v, cfg, startTime, finalDirectLines));
  }

  /**
   * Gets bracket guide tokens for a line.
   */
  public List<BracketGuideToken> getBracketGuideTokensForLine(int globalLine) {
    FunctionLog.f("BracketGuides", "getBracketGuideTokensForLine", globalLine);
    if (!isBracketGuidesEnabled) return Collections.emptyList();

    // Try main cache first
    List<BracketGuideToken> tokens = mainCache.getTokensForLine(globalLine);
    if (tokens != null) {
      return tokens;
    }

    // Fallback to fallback cache
    if (fallbackCache.containsLine(globalLine)) {
      tokens = fallbackCache.getTokensForLine(globalLine);
      if (tokens != null) {
        return tokens;
      }
    }

    return Collections.emptyList();
  }

  /**
   * Gets bracket guide state for a line.
   */
  public BracketGuideState getBracketGuideStateForLine(int globalLine) {
    FunctionLog.f("BracketGuides", "getBracketGuideStateForLine", globalLine);
    // Try main cache first
    BracketGuideState state = mainCache.getStateForLine(globalLine);
    if (state != null) {
      return state;
    }

    // Fallback to fallback cache
    if (fallbackCache.containsLine(globalLine)) {
      state = fallbackCache.getStateForLine(globalLine);
      if (state != null) {
        return state;
      }
    }

    return null;
  }

  /**
   * Updates bracket guide state for a line (delegates to scanner).
   */
  public List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    FunctionLog.f("BracketGuides", "updateBracketGuideStateForLine", line, globalLine, state);
    return scanner.updateBracketGuideStateForLine(line, globalLine, state);
  }

  /**
   * Scans a line for spans (delegates to scanner).
   */
  public void scanLineForSpans(String line, int globalLine, com.yn.sodiumeditor.utils.BracketGuideScanner.BracketSpanScanState state, BracketGuideScanner.SpanCollector collector) {
    FunctionLog.f("BracketGuides", "scanLineForSpans", line, globalLine, state, collector);
    scanner.scanLineForSpans(line, globalLine, state, collector);
  }

  /**
   * Gets line text for guide scanning (delegates to scanner).
   */
  public String getLineTextForGuideScan(
      int line, java.util.Map<Integer, String> directLines, java.io.RandomAccessFile raf) {
    FunctionLog.f("BracketGuides", "getLineTextForGuideScan", line, directLines, raf);
    return scanner.getLineTextForGuideScan(line, directLines, raf);
  }

  /**
   * Copy bracket guide state (delegates to scanner).
   */
  public static BracketGuideState copyState(BracketGuideState src) {
    FunctionLog.f("BracketGuides", "copyState", src);
    return BracketGuideScanner.copyState(src);
  }

  /**
   * Gets guide tokens from stack (delegates to scanner).
   */
  public static List<BracketGuideToken> getGuideTokensFromStack(
      java.util.ArrayDeque<BracketGuideToken> stack) {
    FunctionLog.f("BracketGuides", "getGuideTokensFromStack", stack);
    return BracketGuideScanner.getGuideTokensFromStack(stack);
  }

  /**
   * Gets the guide X position (delegates to draw).
   */
  public float getGuideX(String line, int column, int globalLine) {
    FunctionLog.f("BracketGuides", "getGuideX", line, column, globalLine);
    return draw.getGuideX(line, column, globalLine);
  }

  // ========================================================================
  // Drawing methods (delegate to BracketGuideDraw)
  // ========================================================================

  /**
   * Call at start of render pass to track current state and visible lines
   */
  public void beginRenderFrame(int windowStart, int windowEnd, int visibleStart, int visibleEnd) {
    FunctionLog.f("BracketGuides", "beginRenderFrame", windowStart, windowEnd, visibleStart, visibleEnd);
    draw.beginRenderFrame(windowStart, windowEnd, visibleStart, visibleEnd);

    // Ensure cache is built for visible range (async)
    ensureBracketGuideCacheForWindow(windowStart, windowEnd, visibleStart, visibleEnd, null);
  }

  /**
   * Call at start of render pass (backward compatibility)
   */
  public void beginRenderFrame(int windowStart, int windowEnd) {
    FunctionLog.f("BracketGuides", "beginRenderFrame", windowStart, windowEnd);
    draw.beginRenderFrame(windowStart, windowEnd);
  }

  /**
   * Update fast-scroll state for the current frame.
   */
  public void setFrameFastScroll(boolean fastScroll) {
    FunctionLog.f("BracketGuides", "setFrameFastScroll", fastScroll);
    draw.setFrameFastScroll(fastScroll);
  }

  /**
   * Check if bracket guides can be drawn (cache is valid)
   */
  public boolean canDrawBracketGuides() {
    FunctionLog.f("BracketGuides", "canDrawBracketGuides");
    return draw.canDrawBracketGuides();
  }

  /**
   * Check if a line is currently visible on screen
   */
  public boolean isLineVisible(int globalLine) {
    FunctionLog.f("BracketGuides", "isLineVisible", globalLine);
    return draw.isLineVisible(globalLine);
  }

  /**
   * Draws bracket guides for a line.
   */
  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    FunctionLog.f("BracketGuides", "drawBracketGuidesForLine", canvas, line, globalLine, guideTokens);
    draw.drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
  }

  /**
   * Draws bracket guides for a line directly from the stack.
   */
  public void drawBracketGuidesForLineFromStack(
      Canvas canvas, String line, int globalLine, java.util.ArrayDeque<BracketGuideToken> stack) {
    FunctionLog.f("BracketGuides", "drawBracketGuidesForLineFromStack", canvas, line, globalLine, stack);
    draw.drawBracketGuidesForLineFromStack(canvas, line, globalLine, stack);
  }

  /**
   * Draws bracket guides for visible range using span cache.
   */
  public void drawBracketGuidesForVisibleRange(Canvas canvas, int visibleStart, int visibleEnd) {
    FunctionLog.f("BracketGuides", "drawBracketGuidesForVisibleRange", canvas, visibleStart, visibleEnd);
    if (!isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;

    // Try span cache first (fastest)
    if (spanCache.canDraw()) {
      spanCache.drawBracketGuidesForVisibleRange(canvas, visibleStart, visibleEnd);
      return;
    }

    // Fallback: draw line-by-line using main cache or fallback cache only.
    // No synchronous calculation — if a line is not cached, skip it this frame.
    // The async builder will populate the cache shortly.
    int linesDrawn = 0;
    int linesCount = editor.view.getLinesCount();
    for (int line = visibleStart; line <= visibleEnd; line++) {
      if (line < 0 || line >= linesCount) continue;

      // Try main cache first
      List<BracketGuideToken> tokens = mainCache.getTokensForLine(line);
      if (tokens == null) {
        // Try fallback cache
        tokens = fallbackCache.getTokensForLine(line);
      }

      if (tokens == null || tokens.isEmpty()) {
        // No cache available — skip this frame for this line.
        continue;
      }

      String lineText = scanner.getLineTextForGuideScan(line, null, null);
      if (lineText == null) lineText = "";

      draw.drawBracketGuidesForLine(canvas, lineText, line, tokens);
      linesDrawn++;
    }
    if (SodiumEditor.DEBUG_RENDER_LOGS && linesDrawn > 0) {
      android.util.Log.d("BracketGuides", "drew bracket guides for " + linesDrawn + " lines (visibleStart=" + visibleStart + ", visibleEnd=" + visibleEnd + ")");
    }
  }

  /**
   * Log per-frame stats.
   */
  public void endRenderFrameMaybeLog() {
    FunctionLog.f("BracketGuides", "endRenderFrameMaybeLog");
    draw.endRenderFrameMaybeLog();
  }

  // ========================================================================
  // Span cache methods (delegate to BracketGuideSpanCache)
  // ========================================================================

  /**
   * Ensures span cache for window.
   */
  public void ensureBracketGuideSpanCacheForWindow(
      int startLine, int endLine, int visibleStart, int visibleEnd, @Nullable java.util.Map<Integer, String> directLines) {
    FunctionLog.f("BracketGuides", "ensureBracketGuideSpanCacheForWindow", startLine, endLine, visibleStart, visibleEnd, directLines);
    long startTime = SystemClock.uptimeMillis();
    if (!isBracketGuidesEnabled) return;
    if (startLine > endLine) return;
    if (startLine < 0) {
      invalidateBracketGuideCache(true);
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();

    if (spanCache.isCacheValid(startLine, endLine, v, cfg)) {
      return;
    }

    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
    if (skipGuidesDuringFastScroll && fastScroll) {
      return;
    }

    long now = SystemClock.uptimeMillis();
    if (now - lastRebuildTimeMs < minRebuildIntervalMs) {
      return;
    }

    if (spanCache.bracketGuideSpanBuildInProgress
        && spanCache.bracketGuideSpanPendingStart == startLine
        && spanCache.bracketGuideSpanPendingEnd == endLine
        && spanCache.bracketGuideSpanPendingEditVersion == v
        && spanCache.bracketGuideSpanPendingConfigHash == cfg) {
      return;
    }

    lastRebuildTimeMs = now;
    final int finalStart = startLine;
    final int finalEnd = endLine;
    final java.util.Map<Integer, String> finalDirectLines = (directLines != null) ? new java.util.HashMap<>(directLines) : null;
    spanCache.bracketGuideSpanBuildInProgress = true;
    spanCache.bracketGuideSpanPendingStart = finalStart;
    spanCache.bracketGuideSpanPendingEnd = finalEnd;
    spanCache.bracketGuideSpanPendingEditVersion = v;
    spanCache.bracketGuideSpanPendingConfigHash = cfg;

    spanCache.buildSpanCacheAsync(finalStart, finalEnd, v, cfg, startTime, finalDirectLines);
  }

  // ========================================================================
  // Checkpoint methods (delegate to BracketGuideCheckpoint)
  // ========================================================================

  /**
   * Ensure bracket guide checkpoints are built up to the specified line.
   */
  public void ensureBracketGuideCheckpointsUpTo(
      int endLine, @Nullable java.util.Map<Integer, String> directLines, @Nullable java.io.RandomAccessFile ignoredRaf) {
    FunctionLog.f("BracketGuides", "ensureBracketGuideCheckpointsUpTo", endLine, directLines, ignoredRaf);
    checkpoint.ensureCheckpointsUpTo(endLine, directLines);
  }

  /**
   * Get checkpoint index for a line.
   */
  public int getCheckpointIndexForLine(int line) {
    FunctionLog.f("BracketGuides", "getCheckpointIndexForLine", line);
    return checkpoint.getCheckpointIndexForLine(line);
  }

  public void shiftBracketGuideCaches(int startLine, int delta) {
    FunctionLog.f("BracketGuides", "shiftBracketGuideCaches", startLine, delta);
    if (delta == 0) return;
    mainCache.shiftCache(startLine, delta);
    fallbackCache.shiftCache(startLine, delta);
    checkpoint.shiftCheckpoints(startLine, delta);
    spanCache.invalidate(); // Span cache is too complex to shift, just invalidate
  }
}
