package com.yn.sodiumeditor.core.highlite;

import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manages bracket guides for the SodiumEditor.
 * Draws vertical guide lines for matching braces.
 */
public class BracketGuides {

  private final SodiumEditor editor;

  // Bracket guides state
  public boolean isBracketGuidesEnabled = true;
  public final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketGuideStrokeWidth = 4f;
  public float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
  public float baseBracketGuideTextSizePx = 0f;
  
  // Option: continue drawing guides when lines go off-screen (uses fallback cache)
  // Default: true for smooth rendering during scroll
  public boolean drawGuidesForOffScreenLines = true;
  
  // Performance options
  public boolean skipGuidesDuringFastScroll = false; // Keep guides during fast scroll for consistency
  public long minRebuildIntervalMs = 80; // Minimum time between cache rebuilds
  private long lastRebuildTimeMs = 0;

  // Bracket guide cache
  public int bracketGuideCacheStartLine = -1;
  public int bracketGuideCacheEndLine = -1;
  public int bracketGuideCacheEditVersion = -1;
  public int bracketGuideCacheConfigHash = 0;
  public BracketGuideState bracketGuideCacheStateAtStart = null;
  public BracketGuideState bracketGuideCacheStateAtEnd = null;
  public BracketGuideState bracketGuideCacheStateBeforeStart = null;
  public java.util.ArrayList<BracketGuideState> bracketGuideStatesWindow =
      new java.util.ArrayList<>();
  public final java.util.ArrayList<Integer> bracketGuideCheckpointLines = new java.util.ArrayList<>();
  public final java.util.ArrayList<BracketGuideState> bracketGuideCheckpointStates = new java.util.ArrayList<>();
  public int bracketGuideCheckpointEditVersion = -1;
  public int bracketGuideCheckpointConfigHash = 0;
  public int bracketGuideCheckpointMaxLine = -1;
  public int bracketGuideCheckpointStep = 500;
  public int bracketGuideCheckpointStepFast = 100;
  public boolean showGuidesDuringFastScroll = true;
  public boolean bracketGuideBuildInProgress = false;
  public boolean useFastBuildDuringFastScroll = true;
  public int bracketGuidePendingStart = -1;
  public int bracketGuidePendingEnd = -1;
  public int bracketGuidePendingEditVersion = -1;
  public int bracketGuidePendingConfigHash = 0;
  public java.util.ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow =
      new java.util.ArrayList<>();

  // Span-based guide cache (performance-optimized)
  public int bracketGuideSpanCacheStartLine = -1;
  public int bracketGuideSpanCacheEndLine = -1;
  public int bracketGuideSpanCacheEditVersion = -1;
  public int bracketGuideSpanCacheConfigHash = 0;
  public int bracketGuideSpanCount = 0;
  private int bracketGuideSpanCapacity = 0;
  private int[] bracketGuideSpanColumns;
  private int[] bracketGuideSpanStartLines;
  private int[] bracketGuideSpanEndLines;
  private char[] bracketGuideSpanBrackets;
  private boolean bracketGuideSpanBuildInProgress = false;
  private int bracketGuideSpanPendingStart = -1;
  private int bracketGuideSpanPendingEnd = -1;
  private int bracketGuideSpanPendingEditVersion = -1;
  private int bracketGuideSpanPendingConfigHash = 0;
  private float[] bracketGuideSpanDrawPts;
  private int bracketGuideSpanDrawPtsCapacity = 0;
  
  // Fallback cache to prevent flickering during window changes
  private int fallbackCacheStartLine = -1;
  private int fallbackCacheEndLine = -1;
  private int fallbackCacheEditVersion = -1;
  private final java.util.ArrayList<List<BracketGuideToken>> fallbackTokens = new java.util.ArrayList<>();
  private final java.util.ArrayList<BracketGuideState> fallbackStates = new java.util.ArrayList<>();

  public BracketGuides(SodiumEditor editor) {
    this.editor = editor;
    bracketGuidePaint.setColor(0xFFCCCCCC);
    bracketGuidePaint.setStyle(Paint.Style.STROKE);
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
  }

  /**
   * Enables or disables bracket guides.
   */
  public void setBracketGuidesEnabled(boolean enabled) {
    if (this.isBracketGuidesEnabled == enabled) return;
    this.isBracketGuidesEnabled = enabled;
    invalidateBracketGuideCache();
    editor.invalidate();
  }

  /**
   * Enables or disables drawing guides for off-screen lines.
   * When enabled, guides continue to be drawn even when lines scroll out of view.
   * When disabled, guides only appear for lines currently in the visible window.
   */
  public void setDrawGuidesForOffScreenLines(boolean enabled) {
    if (this.drawGuidesForOffScreenLines == enabled) return;
    this.drawGuidesForOffScreenLines = enabled;
    editor.invalidate();
  }

  /**
   * Check if drawing guides for off-screen lines is enabled.
   */
  public boolean isDrawGuidesForOffScreenLinesEnabled() {
    return drawGuidesForOffScreenLines;
  }

  /**
   * Enable or disable skipping guide rebuilds during fast scroll.
   * When enabled, improves scroll performance by reusing existing cache.
   */
  public void setSkipGuidesDuringFastScroll(boolean enabled) {
    this.skipGuidesDuringFastScroll = enabled;
  }

  /**
   * Set minimum interval between cache rebuilds (in milliseconds).
   * Higher values improve performance but may cause guides to appear slower.
   */
  public void setMinRebuildIntervalMs(long ms) {
    this.minRebuildIntervalMs = Math.max(0, ms);
  }

  /**
   * Sets the bracket guides color.
   */
  public void setBracketGuidesColor(int color) {
    bracketGuidePaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the bracket guides stroke width.
   */
  public void setBracketGuidesStrokeWidth(float width) {
    if (this.bracketGuideStrokeWidth == width) return;
    this.baseBracketGuideStrokeWidth = width;
    this.baseBracketGuideTextSizePx = editor.textRender.paint.getTextSize();
    updateStrokeWidth();
    invalidateBracketGuideCache(true); // config changed
    editor.invalidate();
  }

  /**
   * Updates stroke width based on text size.
   */
  public void updateStrokeWidth() {
    float sizePx = editor.textRender.paint.getTextSize();
    bracketGuideStrokeWidth = Math.max(
        1f,
        editor.scaleByTextSize(baseBracketGuideStrokeWidth, baseBracketGuideTextSizePx, sizePx));
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
  }

  /**
   * Invalidates bracket guide cache.
   */
  public void invalidateBracketGuideCache() {
    invalidateBracketGuideCache(false);
  }
  
  /**
   * Invalidates bracket guide cache.
   * @param configChanged if true, also clear fallback cache (color/stroke changed)
   */
  public void invalidateBracketGuideCache(boolean configChanged) {
    // Save current cache to fallback before invalidating (prevents flickering)
    // Only save if main cache is valid (prevents overwriting fallback with empty data)
    if (!configChanged && bracketGuideCacheStartLine >= 0 && bracketGuideCacheEndLine >= bracketGuideCacheStartLine
        && bracketGuideTokensWindow.size() > 0) {
      int oldFallbackStart = fallbackCacheStartLine;
      int oldFallbackEnd = fallbackCacheEndLine;

      // Calculate union of ranges
      int newFallbackStart = (oldFallbackStart < 0) ? bracketGuideCacheStartLine : Math.min(oldFallbackStart, bracketGuideCacheStartLine);
      int newFallbackEnd = (oldFallbackEnd < 0) ? bracketGuideCacheEndLine : Math.max(oldFallbackEnd, bracketGuideCacheEndLine);

      // Create merged token lists
      java.util.ArrayList<List<BracketGuideToken>> mergedTokens = new java.util.ArrayList<>();
      java.util.ArrayList<BracketGuideState> mergedStates = new java.util.ArrayList<>();

      for (int line = newFallbackStart; line <= newFallbackEnd; line++) {
        // Try to get from main cache first, then old fallback
        List<BracketGuideToken> token = null;
        BracketGuideState state = null;

        if (line >= bracketGuideCacheStartLine && line <= bracketGuideCacheEndLine) {
          int mainIdx = line - bracketGuideCacheStartLine;
          if (mainIdx >= 0 && mainIdx < bracketGuideTokensWindow.size()) {
            token = bracketGuideTokensWindow.get(mainIdx);
          }
          if (mainIdx >= 0 && mainIdx < bracketGuideStatesWindow.size()) {
            state = bracketGuideStatesWindow.get(mainIdx);
          }
        }

        if ((token == null || state == null) && oldFallbackStart >= 0 && line >= oldFallbackStart && line <= oldFallbackEnd) {
          int fallbackIdx = line - oldFallbackStart;
          if (fallbackIdx >= 0 && fallbackIdx < fallbackTokens.size()) {
            token = fallbackTokens.get(fallbackIdx);
          }
          if (fallbackIdx >= 0 && fallbackIdx < fallbackStates.size()) {
            state = fallbackStates.get(fallbackIdx);
          }
        }

        mergedTokens.add(token);
        mergedStates.add(state);
      }

      fallbackCacheStartLine = newFallbackStart;
      fallbackCacheEndLine = newFallbackEnd;
      fallbackCacheEditVersion = bracketGuideCacheEditVersion;
      fallbackTokens.clear();
      fallbackTokens.addAll(mergedTokens);
      fallbackStates.clear();
      fallbackStates.addAll(mergedStates);
    }

    // Now invalidate main cache
    bracketGuideCacheStartLine = -1;
    bracketGuideCacheEndLine = -1;
    bracketGuideCacheEditVersion = -1;
    bracketGuideCacheConfigHash = 0;
    bracketGuideCacheStateAtStart = null;
    bracketGuideCacheStateAtEnd = null;
    bracketGuideCacheStateBeforeStart = null;
    bracketGuideStatesWindow.clear();
    // IMPORTANT: Do NOT clear checkpoints here!
    // Checkpoints are needed for synchronous rendering during cache rebuilds.
    // They will be cleared only when edit version or config changes.
    // bracketGuideCheckpointLines.clear();
    // bracketGuideCheckpointStates.clear();
    // bracketGuideCheckpointEditVersion = -1;
    // bracketGuideCheckpointConfigHash = 0;
    // bracketGuideCheckpointMaxLine = -1;
    bracketGuideBuildInProgress = false;
    bracketGuidePendingStart = -1;
    bracketGuidePendingEnd = -1;
    bracketGuidePendingEditVersion = -1;
    bracketGuidePendingConfigHash = 0;
    bracketGuideTokensWindow.clear();
    bracketGuideSpanCacheStartLine = -1;
    bracketGuideSpanCacheEndLine = -1;
    bracketGuideSpanCacheEditVersion = -1;
    bracketGuideSpanCacheConfigHash = 0;
    bracketGuideSpanCount = 0;
    bracketGuideSpanBuildInProgress = false;
    bracketGuideSpanPendingStart = -1;
    bracketGuideSpanPendingEnd = -1;
    bracketGuideSpanPendingEditVersion = -1;
    bracketGuideSpanPendingConfigHash = 0;

    // Clear fallback cache only if config changed (color/stroke)
    if (configChanged) {
      fallbackCacheStartLine = -1;
      fallbackCacheEndLine = -1;
      fallbackCacheEditVersion = -1;
      fallbackTokens.clear();
      fallbackStates.clear();
    }
  }

  /**
   * Controls whether guides are drawn during fast scroll/fling.
   */
  public void setShowGuidesDuringFastScroll(boolean enabled) {
    if (this.showGuidesDuringFastScroll == enabled) return;
    this.showGuidesDuringFastScroll = enabled;
    editor.invalidate();
  }

  /**
   * Gets the bracket guide cache config hash.
   */
  public int getBracketGuideCacheConfigHash() {
    int h = 1;
    h = 31 * h + Float.floatToIntBits(bracketGuideStrokeWidth);
    h = 31 * h + bracketGuidePaint.getColor();
    return h;
  }

  // ========================================================================
  // Synchronous Bracket Guide Calculation (for integrated rendering)
  // ========================================================================

  /**
   * Calculate bracket guide state for a single line synchronously.
   * This method calculates bracket guides on-demand during line rendering,
   * using the same algorithm as the cached version but without async building.
   * 
   * @param line The line text
   * @param globalLine The global line number
   * @param prevState The bracket guide state from the previous line (or initial state for first line)
   * @return The updated bracket guide state after processing this line
   */
  public BracketGuideState calculateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState prevState) {
    if (prevState == null) {
      prevState = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    }

    // Make a copy to avoid mutating the input state
    BracketGuideState state = copyState(prevState);

    // Calculate tokens for this line (updates the state)
    updateBracketGuideStateForLine(line != null ? line : "", globalLine, state);

    return state;
  }

  /**
   * Calculate bracket guide state from a starting line to the target line.
   * This is used when we need to compute bracket guides for a line but don't
   * have the previous state. It walks from the nearest checkpoint or line 0.
   *
   * @param targetLine The target line to calculate state for
   * @param windowStart The current window start line
   * @param windowEnd The current window end line
   * @param directLines Direct line mappings for off-window lines
   * @return The bracket guide state at the target line (BEFORE processing targetLine)
   */
  public BracketGuideState calculateBracketGuideStateFromWindowStart(
      int targetLine, int windowStart, int windowEnd,
      @Nullable java.util.Map<Integer, String> directLines) {

    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);

    // Try to use checkpoint if available
    int checkpointIdx = getCheckpointIndexForLine(targetLine);
    int startLine = 0;

    if (checkpointIdx >= 0) {
      state = copyState(bracketGuideCheckpointStates.get(checkpointIdx));
      startLine = bracketGuideCheckpointLines.get(checkpointIdx);
      // If checkpoint is exactly at targetLine, return the state as-is (state BEFORE targetLine)
      if (startLine == targetLine) {
        return state;
      }
      // Otherwise, start from the checkpoint line to process up to targetLine - 1
    } else {
      // No checkpoint available - must start from line 0 to get correct bracket state
      // This is slower but ensures correctness
      startLine = 0;
    }

    // Read lines and calculate state from startLine to targetLine (exclusive)
    if (startLine < targetLine && editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      // Use buffered stream for efficiency
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
              // Check for modified lines
              String mod = editor.textRender.modifiedLines.get(currentLine);
              if (mod != null) text = mod;

              updateBracketGuideStateForLine(text, currentLine, state);
              currentLine++;
            }
          }
        }
      } catch (Exception e) {
        // Fallback to simple method on error
        for (int line = startLine; line < targetLine; line++) {
          String text = getLineTextForGuideScan(line, directLines, null);
          updateBracketGuideStateForLine(text != null ? text : "", line, state);
        }
      }
    } else {
      // Simple fallback
      for (int line = startLine; line < targetLine; line++) {
        String text = getLineTextForGuideScan(line, directLines, null);
        updateBracketGuideStateForLine(text != null ? text : "", line, state);
      }
    }

    return state;
  }

  /**
   * Ensures bracket guide cache for window.
   * Performance-optimized: builds visible lines first for faster perceived rendering.
   */
  public void ensureBracketGuideCacheForWindow(
      int startLine, int endLine, @Nullable java.util.Map<Integer, String> directLines) {
    ensureBracketGuideCacheForWindow(startLine, endLine, startLine, endLine, directLines);
  }
  
  /**
   * Ensures bracket guide cache for window with visible line priority.
   * @param startLine Window start line
   * @param endLine Window end line
   * @param visibleStart First visible line on screen
   * @param visibleEnd Last visible line on screen
   * @param directLines Direct line mappings
   */
  public void ensureBracketGuideCacheForWindow(
      int startLine, int endLine, int visibleStart, int visibleEnd, @Nullable java.util.Map<Integer, String> directLines) {
    long startTime = SystemClock.uptimeMillis();

    if (!isBracketGuidesEnabled) {
      return;
    }
    if (startLine > endLine) {
      return;
    }
    if (startLine < 0) {
      invalidateBracketGuideCache(true); // clear everything for invalid range
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();

    // Check if cache is already valid
    if (startLine == bracketGuideCacheStartLine
        && endLine == bracketGuideCacheEndLine
        && v == bracketGuideCacheEditVersion
        && cfg == bracketGuideCacheConfigHash) {
      return;
    }

    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;

    // Skip rebuild during fast scroll - keep using existing cache (main or fallback)
    if (skipGuidesDuringFastScroll && fastScroll) {
      return; // Use existing cache during fast scroll
    }

    // Rate limit rebuilds
    long now = SystemClock.uptimeMillis();
    if (now - lastRebuildTimeMs < minRebuildIntervalMs) {
      return; // Too soon, skip this rebuild
    }

    // Don't rebuild if already in progress for same range
    if (bracketGuideBuildInProgress
        && bracketGuidePendingStart == startLine
        && bracketGuidePendingEnd == endLine
        && bracketGuidePendingEditVersion == v
        && bracketGuidePendingConfigHash == cfg) {
      return;
    }

    // Use faster checkpoint step during fast scroll
    if (fastScroll && useFastBuildDuringFastScroll) {
      bracketGuideCheckpointStep = bracketGuideCheckpointStepFast;
    } else if (!fastScroll) {
      bracketGuideCheckpointStep = 500;
    }

    lastRebuildTimeMs = now;
    final int finalStart = startLine;
    final int finalEnd = endLine;
    final java.util.Map<Integer, String> finalDirectLines = (directLines != null) ? new java.util.HashMap<>(directLines) : null;
    bracketGuideBuildInProgress = true;
    bracketGuidePendingStart = finalStart;
    bracketGuidePendingEnd = finalEnd;
    bracketGuidePendingEditVersion = v;
    bracketGuidePendingConfigHash = cfg;
    // Pass visible range for prioritized building
    editor.fileIO.ioHandler.post(() -> buildBracketGuideCacheAsync(finalStart, finalEnd, visibleStart, visibleEnd, v, cfg, startTime, finalDirectLines));
  }

  /**
   * Gets bracket guide tokens for a line.
   */
  public List<BracketGuideToken> getBracketGuideTokensForLine(int globalLine) {
    if (!isBracketGuidesEnabled) return Collections.emptyList();

    // Try main cache first
    int start = bracketGuideCacheStartLine;
    int end = bracketGuideCacheEndLine;
    if (globalLine >= start && globalLine <= end) {
      int idx = globalLine - start;
      if (idx >= 0 && idx < bracketGuideTokensWindow.size()) {
        List<BracketGuideToken> tokens = bracketGuideTokensWindow.get(idx);
        if (tokens != null) {
          return tokens;
        }
      }
    }

    // Fallback to fallback cache to prevent flickering
    // Use fallback whenever main cache is unavailable (regardless of drawGuidesForOffScreenLines setting)
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackTokens.size()) {
        List<BracketGuideToken> tokens = fallbackTokens.get(idx);
        if (tokens != null) {
          return tokens;
        }
      }
    }

    return Collections.emptyList();
  }

  public BracketGuideState getBracketGuideStateForLine(int globalLine) {
    // Try main cache first
    int start = bracketGuideCacheStartLine;
    int end = bracketGuideCacheEndLine;
    if (globalLine >= start && globalLine <= end) {
      int idx = globalLine - start;
      if (idx >= 0 && idx < bracketGuideStatesWindow.size()) {
        BracketGuideState state = bracketGuideStatesWindow.get(idx);
        return state;
      }
    }

    // Fallback to fallback cache - use whenever main cache is unavailable
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackStates.size()) {
        BracketGuideState state = fallbackStates.get(idx);
        return state;
      }
    }

    return null;
  }

  /**
   * Updates bracket guide state for a line.
   */
  public List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    if (state.stringState != 0 && !editor.highlite.isMultiLineStringsEnabled && state.stringState != Highlite.STRING_STATE_TRIPLE) {
      state.stringState = 0;
    }

    List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = SodiumEditor.findBlockCommentEnd(line, i);
        if (end < 0) break;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) {
          i = length;
          break;
        }
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (editor.highlite.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (editor.highlite.isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !Highlite.isTokenEscaped(line, i)) {
        int end = SodiumEditor.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          break;
        }
        i = end + 2;
        continue;
      }

      if (editor.highlite.isTripleQuoteStart(line, i) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (editor.highlite.isTripleQuoteStringsEnabled) {
            state.stringState = SodiumEditor.STRING_STATE_TRIPLE;
          }
          break;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (editor.highlite.isMultiLineStringsEnabled) {
            state.stringState = editor.getStringStateForDelimiter(c);
          }
          break;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') && !Highlite.isEscaped(line, i)) {
        if (c == '{' || c == '(' || c == '[') {
          int column = (c == '{') ? editor.getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace) : i;
          float x = getGuideX(line, column, globalLine); // Keep x for backward compatibility, but use column for rendering
          state.stack.push(new BracketGuideToken(column, x, c));
        } else {
          char open = (c == '}') ? '{' : (c == ')' ? '(' : '[');
          // Only pop if the top of stack matches - this ensures guides only end at matching brackets
          if (!state.stack.isEmpty() && state.stack.peek().bracket == open) {
            state.stack.pop();
          }
          // If top doesn't match, don't pop - the guide continues
        }
      }

      i++;
    }

    return tokensToDraw;
  }

  // Track last rendered state to avoid redundant draws
  private int lastRenderedEditVersion = -1;
  private int lastRenderedConfigHash = 0;
  private int lastRenderedWindowStart = -1;
  private int lastRenderedWindowEnd = -1;
  
  // Track visible lines for prioritized drawing
  private int visibleStartLine = -1;
  private int visibleEndLine = -1;
  private boolean frameFastScroll = false;
  private long frameStartNs = 0L;
  private int frameLineCount = 0;
  private int frameTokenCount = 0;

  /**
   * Call at start of render pass to track current state and visible lines
   */
  public void beginRenderFrame(int windowStart, int windowEnd, int visibleStart, int visibleEnd) {
    lastRenderedWindowStart = windowStart;
    lastRenderedWindowEnd = windowEnd;
    visibleStartLine = visibleStart;
    visibleEndLine = visibleEnd;
    if (SodiumEditor.DEBUG_RENDER_LOGS) {
      frameStartNs = System.nanoTime();
      frameLineCount = 0;
      frameTokenCount = 0;
    }
  }
  
  /**
   * Call at start of render pass (backward compatibility)
   */
  public void beginRenderFrame(int windowStart, int windowEnd) {
    beginRenderFrame(windowStart, windowEnd, windowStart, windowEnd);
  }
  
  /**
   * Update fast-scroll state for the current frame.
   */
  public void setFrameFastScroll(boolean fastScroll) {
    this.frameFastScroll = fastScroll;
  }

  /**
   * Check if bracket guides can be drawn (cache is valid)
   */
  public boolean canDrawBracketGuides() {
    // Can draw if main cache is valid OR fallback cache is valid
    boolean mainValid = (bracketGuideCacheStartLine >= 0 && bracketGuideCacheEndLine >= bracketGuideCacheStartLine);
    boolean fallbackValid = (fallbackCacheStartLine >= 0 && fallbackCacheEndLine >= fallbackCacheStartLine);
    boolean spanValid = (bracketGuideSpanCacheStartLine >= 0 && bracketGuideSpanCacheEndLine >= bracketGuideSpanCacheStartLine);
    return mainValid || fallbackValid || spanValid;
  }
  
  /**
   * Check if a line is currently visible on screen
   */
  public boolean isLineVisible(int globalLine) {
    return globalLine >= visibleStartLine && globalLine <= visibleEndLine;
  }
  
  /**
   * Reset draw tracking (called when cache is invalidated)
   */
  private void resetDrawTracking() {
    lastRenderedEditVersion = -1;
    lastRenderedConfigHash = 0;
    lastRenderedWindowStart = -1;
    lastRenderedWindowEnd = -1;
  }

  /**
   * Draws bracket guides for a line.
   * @param canvas Canvas to draw on
   * @param line The line text
   * @param globalLine The global line number
   * @param guideTokens Pre-calculated bracket guide tokens (from synchronous rendering)
   */
  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    if (globalLine < 0 || globalLine >= editor.getLinesCount()) return;
    if (!isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;

    // For synchronous rendering, we rely on the passed tokens directly
    // The tokens are calculated from the bracket state during line rendering
    List<BracketGuideToken> tokensToDraw = (guideTokens != null && !guideTokens.isEmpty()) ? guideTokens : null;

    if (tokensToDraw == null || tokensToDraw.isEmpty()) {
      return;
    }
    if (SodiumEditor.DEBUG_RENDER_LOGS) {
      frameLineCount++;
      frameTokenCount += tokensToDraw.size();
    }

    if (line == null) line = "";
    editor.indentGuides.guideSeenXCount = 0;
    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    // Only adjust to closing brace if we have window-cached tokens (representing state at start of line)
    boolean isClosingBraceLine = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    boolean adjustTopGuideToClosingBrace = (guideTokens != null && !guideTokens.isEmpty() && isClosingBraceLine);
    float closingBraceX = adjustTopGuideToClosingBrace ? getGuideX(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : tokensToDraw) {
      // Draw guides for all bracket types: {}, (), []
      if (token.bracket != '{' && token.bracket != '(' && token.bracket != '[') {
        tokenIndex++;
        continue;
      }
      // Calculate X at draw time to account for zoom level changes
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.getX(this, line, globalLine);
      tokenIndex++;

      boolean seen = false;
      for (int i = 0; i < editor.indentGuides.guideSeenXCount; i++) {
        if (Math.abs(editor.indentGuides.guideSeenXBuffer[i] - x) <= 0.5f) {
          seen = true;
          break;
        }
      }
      if (seen) continue;

      if (editor.indentGuides.guideSeenXBuffer == null || editor.indentGuides.guideSeenXBuffer.length < editor.indentGuides.guideSeenXCount + 1) {
        float[] next = new float[Math.max(16, editor.indentGuides.guideSeenXCount + 8)];
        if (editor.indentGuides.guideSeenXBuffer != null && editor.indentGuides.guideSeenXCount > 0) {
          System.arraycopy(editor.indentGuides.guideSeenXBuffer, 0, next, 0, editor.indentGuides.guideSeenXCount);
        }
        editor.indentGuides.guideSeenXBuffer = next;
      }
      editor.indentGuides.guideSeenXBuffer[editor.indentGuides.guideSeenXCount++] = x;

      if (!editor.isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuidePaint);
    }
  }

  // ========================================================================
  // Span-based bracket guides (performance-first)
  // ========================================================================

  private static final class BracketSpanStack {
    int[] columns = new int[32];
    int[] startLines = new int[32];
    char[] brackets = new char[32];
    int size = 0;

    void clear() { size = 0; }

    void push(int column, int startLine, char bracket) {
      if (size >= columns.length) {
        int newCap = Math.max(32, size * 2);
        int[] newCols = new int[newCap];
        int[] newStarts = new int[newCap];
        char[] newBrackets = new char[newCap];
        System.arraycopy(columns, 0, newCols, 0, size);
        System.arraycopy(startLines, 0, newStarts, 0, size);
        System.arraycopy(brackets, 0, newBrackets, 0, size);
        columns = newCols;
        startLines = newStarts;
        brackets = newBrackets;
      }
      columns[size] = column;
      startLines[size] = startLine;
      brackets[size] = bracket;
      size++;
    }

    void pop() { if (size > 0) size--; }

    int topColumn() { return columns[size - 1]; }
    int topStartLine() { return startLines[size - 1]; }
    char topBracket() { return brackets[size - 1]; }
  }

  private static final class BracketSpanScanState {
    boolean inBlockComment;
    int stringState;
    final BracketSpanStack stack = new BracketSpanStack();
  }

  private static final class SpanCollector {
    int[] columns;
    int[] startLines;
    int[] endLines;
    char[] brackets;
    int count;

    SpanCollector(int initialCap) {
      int cap = Math.max(32, initialCap);
      columns = new int[cap];
      startLines = new int[cap];
      endLines = new int[cap];
      brackets = new char[cap];
      count = 0;
    }

    void add(int column, int startLine, int endLine, char bracket) {
      if (startLine > endLine) return;
      if (count >= columns.length) {
        int newCap = columns.length * 2;
        columns = java.util.Arrays.copyOf(columns, newCap);
        startLines = java.util.Arrays.copyOf(startLines, newCap);
        endLines = java.util.Arrays.copyOf(endLines, newCap);
        brackets = java.util.Arrays.copyOf(brackets, newCap);
      }
      columns[count] = column;
      startLines[count] = startLine;
      endLines[count] = endLine;
      brackets[count] = bracket;
      count++;
    }
  }

  private void ensureSpanCapacity(int desired) {
    if (desired <= bracketGuideSpanCapacity) return;
    int newCap = Math.max(64, desired * 2);
    bracketGuideSpanCapacity = newCap;
    bracketGuideSpanColumns = (bracketGuideSpanColumns == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanColumns, newCap);
    bracketGuideSpanStartLines = (bracketGuideSpanStartLines == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanStartLines, newCap);
    bracketGuideSpanEndLines = (bracketGuideSpanEndLines == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanEndLines, newCap);
    bracketGuideSpanBrackets = (bracketGuideSpanBrackets == null) ? new char[newCap] : java.util.Arrays.copyOf(bracketGuideSpanBrackets, newCap);
  }

  private void addSpan(int column, int startLine, int endLine, char bracket) {
    if (startLine > endLine) return;
    ensureSpanCapacity(bracketGuideSpanCount + 1);
    int idx = bracketGuideSpanCount++;
    bracketGuideSpanColumns[idx] = column;
    bracketGuideSpanStartLines[idx] = startLine;
    bracketGuideSpanEndLines[idx] = endLine;
    bracketGuideSpanBrackets[idx] = bracket;
  }

  private float getGuideXApproxFromColumn(int column) {
    float spaceWidth = editor.getVisualSpaceWidth(editor.textRender.paint);
    return spaceWidth * Math.max(0, column);
  }

  private void scanLineForSpans(String line, int globalLine, BracketSpanScanState state, SpanCollector collector) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    if (state.stringState != 0 && !editor.highlite.isMultiLineStringsEnabled && state.stringState != Highlite.STRING_STATE_TRIPLE) {
      state.stringState = 0;
    }

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = SodiumEditor.findBlockCommentEnd(line, i);
        if (end < 0) break;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) {
          i = length;
          break;
        }
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (editor.highlite.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (editor.highlite.isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !Highlite.isTokenEscaped(line, i)) {
        int end = SodiumEditor.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          break;
        }
        i = end + 2;
        continue;
      }

      if (editor.highlite.isTripleQuoteStart(line, i) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (editor.highlite.isTripleQuoteStringsEnabled) {
            state.stringState = SodiumEditor.STRING_STATE_TRIPLE;
          }
          break;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (editor.highlite.isMultiLineStringsEnabled) {
            state.stringState = editor.getStringStateForDelimiter(c);
          }
          break;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') && !Highlite.isEscaped(line, i)) {
        if (c == '{' || c == '(' || c == '[') {
          int column = (c == '{') ? editor.getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace) : i;
          state.stack.push(column, globalLine, c);
        } else {
          char open = (c == '}') ? '{' : (c == ')' ? '(' : '[');
          if (state.stack.size > 0 && state.stack.topBracket() == open) {
            int column = state.stack.topColumn();
            int openLine = state.stack.topStartLine();
            state.stack.pop();
            int spanStart = openLine + 1;
            int spanEnd = globalLine - 1;
            collector.add(column, spanStart, spanEnd, open);
          }
        }
      }

      i++;
    }
  }

  public void ensureBracketGuideSpanCacheForWindow(
      int startLine, int endLine, int visibleStart, int visibleEnd, @Nullable java.util.Map<Integer, String> directLines) {
    long startTime = SystemClock.uptimeMillis();
    if (!isBracketGuidesEnabled) return;
    if (startLine > endLine) return;
    if (startLine < 0) {
      invalidateBracketGuideCache(true);
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();

    if (startLine == bracketGuideSpanCacheStartLine
        && endLine == bracketGuideSpanCacheEndLine
        && v == bracketGuideSpanCacheEditVersion
        && cfg == bracketGuideSpanCacheConfigHash) {
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

    if (bracketGuideSpanBuildInProgress
        && bracketGuideSpanPendingStart == startLine
        && bracketGuideSpanPendingEnd == endLine
        && bracketGuideSpanPendingEditVersion == v
        && bracketGuideSpanPendingConfigHash == cfg) {
      return;
    }

    lastRebuildTimeMs = now;
    final int finalStart = startLine;
    final int finalEnd = endLine;
    final java.util.Map<Integer, String> finalDirectLines = (directLines != null) ? new java.util.HashMap<>(directLines) : null;
    bracketGuideSpanBuildInProgress = true;
    bracketGuideSpanPendingStart = finalStart;
    bracketGuideSpanPendingEnd = finalEnd;
    bracketGuideSpanPendingEditVersion = v;
    bracketGuideSpanPendingConfigHash = cfg;

    editor.fileIO.ioHandler.post(() -> buildBracketGuideSpanCacheAsync(finalStart, finalEnd, v, cfg, startTime, finalDirectLines));
  }

  private void buildBracketGuideSpanCacheAsync(
      int startLine, int endLine, int v, int cfg, long startTime, @Nullable java.util.Map<Integer, String> directLines) {
    BracketSpanScanState state = new BracketSpanScanState();
    SpanCollector collector = new SpanCollector(256);

    try {
      // Seed state from checkpoint stack (approximate opens before startLine)
      int checkpointIdx = getCheckpointIndexForLine(startLine);
      if (checkpointIdx >= 0) {
        BracketGuideState cp = bracketGuideCheckpointStates.get(checkpointIdx);
        state.inBlockComment = cp.inBlockComment;
        state.stringState = cp.stringState;
        String seedLine = getLineTextForGuideScan(startLine, directLines, null);
        for (BracketGuideToken token : cp.stack) {
          state.stack.push(token.column, startLine - 1, token.bracket);
        }
      }

      for (int line = startLine; line <= endLine; line++) {
        if (editor.editOperators.editVersion.get() != v || getBracketGuideCacheConfigHash() != cfg) {
          bracketGuideSpanBuildInProgress = false;
          return;
        }
        String text = getLineTextForGuideScan(line, directLines, null);
        scanLineForSpans(text, line, state, collector);
      }

      // Close any remaining spans to endLine
      while (state.stack.size > 0) {
        int column = state.stack.topColumn();
        int openLine = state.stack.topStartLine();
        char bracket = state.stack.topBracket();
        state.stack.pop();
        int spanStart = openLine + 1;
        int spanEnd = endLine;
        collector.add(column, spanStart, spanEnd, bracket);
      }
    } catch (Exception ignored) {
    }

    editor.post(() -> {
      bracketGuideSpanCount = 0;
      ensureSpanCapacity(collector.count);
      for (int i = 0; i < collector.count; i++) {
        int column = collector.columns[i];
        int s = collector.startLines[i];
        int e = collector.endLines[i];
        char b = collector.brackets[i];
        addSpan(column, s, e, b);
      }
      bracketGuideSpanCacheStartLine = startLine;
      bracketGuideSpanCacheEndLine = endLine;
      bracketGuideSpanCacheEditVersion = v;
      bracketGuideSpanCacheConfigHash = cfg;
      bracketGuideSpanBuildInProgress = false;
      editor.invalidate();
    });
  }

  public void drawBracketGuidesForVisibleRange(Canvas canvas, int visibleStart, int visibleEnd) {
    if (!isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;
    if (bracketGuideSpanCacheStartLine < 0 || bracketGuideSpanCacheEndLine < bracketGuideSpanCacheStartLine) return;
    if (bracketGuideSpanCount <= 0) return;

    int start = Math.max(visibleStart, bracketGuideSpanCacheStartLine);
    int end = Math.min(visibleEnd, bracketGuideSpanCacheEndLine);
    if (start > end) return;

    int maxPts = bracketGuideSpanCount * 4;
    if (bracketGuideSpanDrawPtsCapacity < maxPts) {
      bracketGuideSpanDrawPtsCapacity = Math.max(64, maxPts * 2);
      bracketGuideSpanDrawPts = new float[bracketGuideSpanDrawPtsCapacity];
    }

    int p = 0;
    for (int i = 0; i < bracketGuideSpanCount; i++) {
      int s = bracketGuideSpanStartLines[i];
      int e = bracketGuideSpanEndLines[i];
      if (e < start || s > end) continue;
      int drawStart = Math.max(start, s);
      int drawEnd = Math.min(end, e);
      if (drawStart > drawEnd) continue;
      float top = editor.textRender.getDrawLineTop(drawStart);
      float bottom = editor.textRender.getDrawLineTop(drawEnd) + editor.textRender.lineHeight;
      float x = getGuideXApproxFromColumn(bracketGuideSpanColumns[i]);
      if (SodiumEditor.DEBUG_RENDER_LOGS) {
        frameLineCount++;
        frameTokenCount++;
      }
      bracketGuideSpanDrawPts[p++] = x;
      bracketGuideSpanDrawPts[p++] = top;
      bracketGuideSpanDrawPts[p++] = x;
      bracketGuideSpanDrawPts[p++] = bottom;
    }

    if (p > 0) {
      canvas.drawLines(bracketGuideSpanDrawPts, 0, p, bracketGuidePaint);
    }
  }
  
  /**
   * Draws bracket guides for a line directly from the stack (avoids allocations).
   */
  public void drawBracketGuidesForLineFromStack(
      Canvas canvas, String line, int globalLine, java.util.ArrayDeque<BracketGuideToken> stack) {
    if (globalLine < 0 || globalLine >= editor.getLinesCount()) return;
    if (!isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;
    if (stack == null || stack.isEmpty()) return;

    if (SodiumEditor.DEBUG_RENDER_LOGS) {
      frameLineCount++;
      frameTokenCount += stack.size();
    }

    if (line == null) line = "";
    editor.indentGuides.guideSeenXCount = 0;
    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    boolean isClosingBraceLine = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    boolean adjustTopGuideToClosingBrace = (isClosingBraceLine && !stack.isEmpty());
    float closingBraceX = adjustTopGuideToClosingBrace ? getGuideX(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : stack) {
      if (token.bracket != '{' && token.bracket != '(' && token.bracket != '[') {
        tokenIndex++;
        continue;
      }
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.getX(this, line, globalLine);
      tokenIndex++;

      boolean seen = false;
      for (int i = 0; i < editor.indentGuides.guideSeenXCount; i++) {
        if (Math.abs(editor.indentGuides.guideSeenXBuffer[i] - x) <= 0.5f) {
          seen = true;
          break;
        }
      }
      if (seen) continue;

      if (editor.indentGuides.guideSeenXBuffer == null || editor.indentGuides.guideSeenXBuffer.length < editor.indentGuides.guideSeenXCount + 1) {
        float[] next = new float[Math.max(16, editor.indentGuides.guideSeenXCount + 8)];
        if (editor.indentGuides.guideSeenXBuffer != null && editor.indentGuides.guideSeenXCount > 0) {
          System.arraycopy(editor.indentGuides.guideSeenXBuffer, 0, next, 0, editor.indentGuides.guideSeenXCount);
        }
        editor.indentGuides.guideSeenXBuffer = next;
      }
      editor.indentGuides.guideSeenXBuffer[editor.indentGuides.guideSeenXCount++] = x;

      if (!editor.isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuidePaint);
    }
  }
  
  /**
   * Log per-frame stats to help diagnose bracket guide performance.
   */
  public void endRenderFrameMaybeLog() {
    if (!SodiumEditor.DEBUG_RENDER_LOGS || frameStartNs == 0L) return;
    long dtMs = (System.nanoTime() - frameStartNs) / 1_000_000L;
    editor.logRender(
        "bracketGuidesFrame",
        "bracketGuides frameMs=" + dtMs
            + " lines=" + frameLineCount
            + " tokens=" + frameTokenCount
            + " fastScroll=" + frameFastScroll
            + " showDuringFast=" + showGuidesDuringFastScroll,
        500);
  }

  /**
   * Gets the guide X position at the START of the character (not center).
   * This ensures guides are drawn at the beginning of braces for better visual alignment.
   */
  private float getGuideX(String line, int column, int globalLine) {
    return editor.getGuideXForColumn(line, column, globalLine);
  }

  /**
   * Builds line order array with visible lines first for prioritized rendering.
   * @return Array of line numbers in build order (visible lines first)
   */
  private int[] buildPrioritizedLineOrder(int startLine, int endLine, int visibleStart, int visibleEnd) {
    int totalLines = endLine - startLine + 1;
    int[] buildOrder = new int[totalLines];
    int idx = 0;
    
    // First: Add visible lines (prioritized)
    int visibleStartClamped = Math.max(startLine, Math.min(visibleStart, endLine));
    int visibleEndClamped = Math.max(startLine, Math.min(visibleEnd, endLine));
    for (int line = visibleStartClamped; line <= visibleEndClamped && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }
    
    // Second: Add lines before visible range (top to bottom)
    for (int line = startLine; line < visibleStartClamped && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }
    
    // Third: Add lines after visible range (top to bottom)
    for (int line = visibleEndClamped + 1; line <= endLine && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }
    
    return buildOrder;
  }

  private void buildBracketGuideCacheAsync(int startLine, int endLine, int visibleStart, int visibleEnd, int v, int cfg, long startTime, @Nullable java.util.Map<Integer, String> directLines) {
    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    BracketGuideState stateBeforeStart = copyState(state);
    BracketGuideState stateAtStart = null;
    java.util.ArrayList<List<BracketGuideToken>> tokensWindow = new java.util.ArrayList<>();
    java.util.ArrayList<BracketGuideState> statesWindow = new java.util.ArrayList<>();
    tokensWindow.ensureCapacity(endLine - startLine + 1);
    statesWindow.ensureCapacity(endLine - startLine + 1);
    int stickyColumn = -1;
    boolean stickyActive = false;

    try {
      // Use smaller checkpoint step during fast scroll for quicker initial build
      int originalCheckpointStep = bracketGuideCheckpointStep;
      boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
      if (fastScroll && useFastBuildDuringFastScroll) {
        bracketGuideCheckpointStep = Math.min(bracketGuideCheckpointStepFast, endLine - startLine + 1);
      }

      ensureBracketGuideCheckpointsUpTo(endLine, directLines, null);

      int checkpointIdx = getCheckpointIndexForLine(startLine);
      int currentLine;
      if (checkpointIdx >= 0) {
        state = copyState(bracketGuideCheckpointStates.get(checkpointIdx));
        currentLine = bracketGuideCheckpointLines.get(checkpointIdx);
      } else {
        currentLine = 0;
      }

      // Read from checkpoint to startLine using buffered stream if possible
      if (currentLine < startLine && editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists() && (directLines == null || directLines.isEmpty())) {
        long offset;
        synchronized (editor.fileIO.lineOffsetsLock) {
          offset = editor.fileIO.lineOffsets[currentLine];
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile)) {
          fis.getChannel().position(offset);
          try (java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
               java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {
            while (currentLine < startLine) {
              String text = reader.readLine();
              if (text == null) break;
              updateBracketGuideStateForLine(text, currentLine, state);
              currentLine++;
            }
          }
        }
      } else {
        while (currentLine < startLine) {
          String text = getLineTextForGuideScan(currentLine, directLines, null);
          if (text == null) text = "";
          updateBracketGuideStateForLine(text, currentLine, state);
          currentLine++;
        }
      }

      stateBeforeStart = copyState(state);
      if (!stateBeforeStart.stack.isEmpty()) {
        BracketGuideToken top = stateBeforeStart.stack.peek();
        if (top != null && top.bracket == '{') {
          stickyColumn = top.column;
          stickyActive = true;
        }
      }

      // PRIORITY BUILD: First build visible lines for faster perceived rendering
      // Then build remaining lines in the background
      int[] buildOrder = buildPrioritizedLineOrder(startLine, endLine, visibleStart, visibleEnd);

      // Pre-allocate arrays
      for (int i = 0; i <= endLine - startLine; i++) {
        tokensWindow.add(null);
        statesWindow.add(null);
      }

      // Build lines in prioritized order
      for (int buildIdx = 0; buildIdx < buildOrder.length; buildIdx++) {
        int line = buildOrder[buildIdx];

        // Check if edit version changed during build - abort if so
        if (editor.editOperators.editVersion.get() != v || getBracketGuideCacheConfigHash() != cfg) {
          bracketGuideBuildInProgress = false;
          bracketGuideCheckpointStep = originalCheckpointStep;
          return;
        }

        String text = getLineTextForGuideScan(line, directLines, null);
        if (text == null) text = "";
        List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, state);
        int arrayIdx = line - startLine;
        tokensWindow.set(arrayIdx, tokens);
        statesWindow.set(arrayIdx, copyState(state));
        if (line == startLine) stateAtStart = copyState(state);
      }

      // Restore original checkpoint step
      bracketGuideCheckpointStep = originalCheckpointStep;
    } catch (Exception e) {
      // Ignore exception
    }
    
    BracketGuideState finalStateAtStart = (stateAtStart != null) ? stateAtStart : copyState(state);
    BracketGuideState finalState = copyState(state);
    BracketGuideState finalStateBeforeStart = stateBeforeStart;
    int finalStickyColumn = stickyColumn;
    boolean finalStickyActive = stickyActive;

    // Double buffering: swap caches atomically on UI thread
    editor.post(() -> {
      long postTime = SystemClock.uptimeMillis();


      // Save old cache to fallback before swapping (prevents flickering)
      // IMPORTANT: Merge old main cache with existing fallback cache
      // This ensures bracket guides remain visible when brackets are outside the visible window
      // but still within the prefetch range
      if (bracketGuideCacheStartLine >= 0 && bracketGuideCacheEndLine >= bracketGuideCacheStartLine
          && bracketGuideTokensWindow.size() > 0) {
        int oldFallbackStart = fallbackCacheStartLine;
        int oldFallbackEnd = fallbackCacheEndLine;

        // Calculate union of ranges
        int newFallbackStart = (oldFallbackStart < 0) ? bracketGuideCacheStartLine : Math.min(oldFallbackStart, bracketGuideCacheStartLine);
        int newFallbackEnd = (oldFallbackEnd < 0) ? bracketGuideCacheEndLine : Math.max(oldFallbackEnd, bracketGuideCacheEndLine);

        // Create merged token lists
        java.util.ArrayList<List<BracketGuideToken>> mergedTokens = new java.util.ArrayList<>();
        java.util.ArrayList<BracketGuideState> mergedStates = new java.util.ArrayList<>();

        for (int line = newFallbackStart; line <= newFallbackEnd; line++) {
          // Try to get from old fallback first, then old main cache
          List<BracketGuideToken> token = null;
          BracketGuideState mergedState = null;

          if (oldFallbackStart >= 0 && line >= oldFallbackStart && line <= oldFallbackEnd) {
            int fallbackIdx = line - oldFallbackStart;
            if (fallbackIdx >= 0 && fallbackIdx < fallbackTokens.size()) {
              token = fallbackTokens.get(fallbackIdx);
            }
            if (fallbackIdx >= 0 && fallbackIdx < fallbackStates.size()) {
              mergedState = fallbackStates.get(fallbackIdx);
            }
          }

          if ((token == null || mergedState == null) && line >= bracketGuideCacheStartLine && line <= bracketGuideCacheEndLine) {
            int mainIdx = line - bracketGuideCacheStartLine;
            if (mainIdx >= 0 && mainIdx < bracketGuideTokensWindow.size()) {
              token = bracketGuideTokensWindow.get(mainIdx);
            }
            if (mainIdx >= 0 && mainIdx < bracketGuideStatesWindow.size()) {
              mergedState = bracketGuideStatesWindow.get(mainIdx);
            }
          }

          mergedTokens.add(token);
          mergedStates.add(mergedState);
        }

        fallbackCacheStartLine = newFallbackStart;
        fallbackCacheEndLine = newFallbackEnd;
        fallbackCacheEditVersion = bracketGuideCacheEditVersion;
        fallbackTokens.clear();
        fallbackTokens.addAll(mergedTokens);
        fallbackStates.clear();
        fallbackStates.addAll(mergedStates);
      }

      // Atomic swap
      bracketGuideTokensWindow = tokensWindow;
      bracketGuideStatesWindow = statesWindow;
      bracketGuideCacheStartLine = startLine;
      bracketGuideCacheEndLine = endLine;
      bracketGuideCacheEditVersion = v;
      bracketGuideCacheConfigHash = cfg;
      bracketGuideCacheStateAtStart = finalStateAtStart;
      bracketGuideCacheStateAtEnd = finalState;
      bracketGuideCacheStateBeforeStart = finalStateBeforeStart;
      bracketGuideBuildInProgress = false;

      editor.invalidate();
    });
  }

  /**
   * Ensure bracket guide checkpoints are built up to the specified line.
   * Public for use in ViewRender for synchronous rendering.
   */
  public void ensureBracketGuideCheckpointsUpTo(
      int endLine, @Nullable java.util.Map<Integer, String> directLines, @Nullable java.io.RandomAccessFile ignoredRaf) {
    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();
    if (v != bracketGuideCheckpointEditVersion || cfg != bracketGuideCheckpointConfigHash) {
      bracketGuideCheckpointLines.clear();
      bracketGuideCheckpointStates.clear();
      bracketGuideCheckpointEditVersion = v;
      bracketGuideCheckpointConfigHash = cfg;
      bracketGuideCheckpointMaxLine = -1;
    }
    if (endLine <= bracketGuideCheckpointMaxLine) {
      return;
    }

    BracketGuideState state;
    int startLine = bracketGuideCheckpointMaxLine + 1;
    if (bracketGuideCheckpointStates.isEmpty()) {
      state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
      startLine = 0;
    } else {
      state = copyState(bracketGuideCheckpointStates.get(bracketGuideCheckpointStates.size() - 1));
    }

    if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      long startOffset;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (startLine >= 0 && startLine < editor.fileIO.lineOffsets.length) {
          startOffset = editor.fileIO.lineOffsets[startLine];
        } else {
          startOffset = -1;
        }
      }

      if (startOffset >= 0) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile)) {
          fis.getChannel().position(startOffset);
          try (java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
               java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {
            
            for (int line = startLine; line <= endLine; line++) {
              if (line % bracketGuideCheckpointStep == 0) {
                bracketGuideCheckpointLines.add(line);
                bracketGuideCheckpointStates.add(copyState(state));
              }
              String text = reader.readLine();
              if (text == null) break;
              updateBracketGuideStateForLine(text, line, state);
              bracketGuideCheckpointMaxLine = line;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        return;
      }
    }

    // Fallback if index not ready
    for (int line = startLine; line <= endLine; line++) {
      if (line % bracketGuideCheckpointStep == 0) {
        bracketGuideCheckpointLines.add(line);
        bracketGuideCheckpointStates.add(copyState(state));
      }
      String text = getLineTextForGuideScan(line, directLines, null);
      if (text == null) text = "";
      updateBracketGuideStateForLine(text, line, state);
      bracketGuideCheckpointMaxLine = line;
    }
  }

  /**
   * Get checkpoint index for a line (public for use in ViewRender).
   * Returns the index of the checkpoint with the largest line number <= the requested line.
   */
  public int getCheckpointIndexForLine(int line) {
    if (bracketGuideCheckpointLines.isEmpty()) return -1;
    
    // Special case: if line is 0 or less, return checkpoint at index 0 if it exists
    if (line <= 0) {
      return bracketGuideCheckpointLines.get(0) == 0 ? 0 : -1;
    }
    
    int lo = 0;
    int hi = bracketGuideCheckpointLines.size() - 1;
    int best = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int v = bracketGuideCheckpointLines.get(mid);
      if (v <= line) {  // Changed from < to <= to include exact matches
        best = mid;
        if (v == line) {
          // Exact match found
          break;
        }
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return best;
  }

  private String getLineTextForGuideScan(
      int line, @Nullable java.util.Map<Integer, String> directLines, @Nullable java.io.RandomAccessFile raf) {
    if (directLines != null) {
      String direct = directLines.get(line);
      if (direct != null) return direct;
    }
    String mod = editor.textRender.modifiedLines.get(line);
    if (mod != null) return mod;
    int winStart = editor.textRender.windowStartLine;
    int winEnd = winStart + editor.textRender.linesWindow.size();
    if (line >= winStart && line < winEnd) {
      String w = editor.getLineFromWindowLocal(line - winStart);
      if (w != null) return w;
    }
    if (raf != null && editor.fileIO.isIndexReady) {
      long offset;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (line < 0 || line >= editor.fileIO.lineOffsets.length) return "";
        offset = editor.fileIO.lineOffsets[line];
      }
      try {
        return editor.fileIO.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return "";
      }
    }
    return "";
  }

  /**
   * Copy bracket guide state (public for use in ViewRender).
   */
  public static BracketGuideState copyState(BracketGuideState src) {
    BracketGuideState out = new BracketGuideState(src.inBlockComment, src.stringState);
    for (BracketGuideToken token : src.stack) {
      out.stack.addLast(new BracketGuideToken(token.column, 0f, token.bracket)); // x is calculated at draw time
    }
    return out;
  }

  /**
   * Gets guide tokens from stack.
   */
  public static List<BracketGuideToken> getGuideTokensFromStack(
      java.util.ArrayDeque<BracketGuideToken> stack) {
    List<BracketGuideToken> tokens = new ArrayList<>();
    for (BracketGuideToken token : stack) {
      tokens.add(token);
    }
    return tokens;
  }

  /**
   * Bracket guide state class.
   */
  public static class BracketGuideState {
    public boolean inBlockComment;
    public int stringState;
    public final java.util.ArrayDeque<BracketGuideToken> stack = new java.util.ArrayDeque<>();

    public BracketGuideState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  /**
   * Bracket guide token class.
   * Stores column index instead of X position to remain stable during zoom.
   */
  public static class BracketGuideToken {
    public final int column;
    public final char bracket;
    // Note: x is calculated at draw time based on current text size/zoom

    public BracketGuideToken(int column, float x, char bracket) {
      this.column = column;
      this.bracket = bracket;
    }
    
    /**
     * Calculates X position at draw time based on current zoom level.
     */
    public float getX(BracketGuides guides, String line, int globalLine) {
      return guides.getGuideX(line, column, globalLine);
    }
  }
}
