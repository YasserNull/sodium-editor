package com.yn.sodiumeditor.core.highlite;

import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayList;
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
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "invalidateBracketGuideCache configChanged=" + configChanged + " current=[" + bracketGuideCacheStartLine + "," + bracketGuideCacheEndLine + "]");
    }
    
    // Save current cache to fallback before invalidating (prevents flickering)
    if (!configChanged && bracketGuideCacheStartLine >= 0 && bracketGuideCacheEndLine >= bracketGuideCacheStartLine) {
      fallbackCacheStartLine = bracketGuideCacheStartLine;
      fallbackCacheEndLine = bracketGuideCacheEndLine;
      fallbackCacheEditVersion = bracketGuideCacheEditVersion;
      fallbackTokens.clear();
      fallbackTokens.addAll(bracketGuideTokensWindow);
      fallbackStates.clear();
      fallbackStates.addAll(bracketGuideStatesWindow);
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "invalidateBracketGuideCache SAVED to fallback [" + fallbackCacheStartLine + "," + fallbackCacheEndLine + "]");
      }
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
    bracketGuideCheckpointLines.clear();
    bracketGuideCheckpointStates.clear();
    bracketGuideCheckpointEditVersion = -1;
    bracketGuideCheckpointConfigHash = 0;
    bracketGuideCheckpointMaxLine = -1;
    bracketGuideBuildInProgress = false;
    bracketGuidePendingStart = -1;
    bracketGuidePendingEnd = -1;
    bracketGuidePendingEditVersion = -1;
    bracketGuidePendingConfigHash = 0;
    bracketGuideTokensWindow.clear();
    
    // Clear fallback cache only if config changed (color/stroke)
    if (configChanged) {
      fallbackCacheStartLine = -1;
      fallbackCacheEndLine = -1;
      fallbackCacheEditVersion = -1;
      fallbackTokens.clear();
      fallbackStates.clear();
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "invalidateBracketGuideCache CLEARED fallback (config changed)");
      }
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

  /**
   * Ensures bracket guide cache for window.
   */
  public void ensureBracketGuideCacheForWindow(
      int startLine, int endLine, @Nullable java.util.Map<Integer, String> directLines) {
    if (!isBracketGuidesEnabled) return;
    if (startLine > endLine) return;
    if (startLine < 0) {
      invalidateBracketGuideCache(true); // clear everything for invalid range
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();

    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "ensureCache start=" + startLine + " end=" + endLine + " v=" + v + " current=[" + bracketGuideCacheStartLine + "," + bracketGuideCacheEndLine + "] editVer=" + bracketGuideCacheEditVersion + " fastScroll=" + (editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null));
    }

    if (startLine == bracketGuideCacheStartLine
        && endLine == bracketGuideCacheEndLine
        && v == bracketGuideCacheEditVersion
        && cfg == bracketGuideCacheConfigHash) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "ensureCache SKIP - cache already valid");
      }
      return;
    }

    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;

    // During fast scroll, use faster checkpoint step for quicker cache build
    if (fastScroll && useFastBuildDuringFastScroll) {
      bracketGuideCheckpointStep = bracketGuideCheckpointStepFast;
    } else if (!fastScroll) {
      bracketGuideCheckpointStep = 500;
    }

    // Always build cache, but use fast mode during fling
    // Don't rebuild if already building for same range
    if (bracketGuideBuildInProgress
        && bracketGuidePendingStart == startLine
        && bracketGuidePendingEnd == endLine
        && bracketGuidePendingEditVersion == v
        && bracketGuidePendingConfigHash == cfg) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "ensureCache SKIP - build in progress");
      }
      return;
    }

    // Keep existing cache during build - don't invalidate until new cache is ready
    // This prevents guides from disappearing during fling stop
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "ensureCache START BUILD for [" + startLine + "," + endLine + "]");
    }
    bracketGuideBuildInProgress = true;
    bracketGuidePendingStart = startLine;
    bracketGuidePendingEnd = endLine;
    bracketGuidePendingEditVersion = v;
    bracketGuidePendingConfigHash = cfg;
    editor.fileIO.ioHandler.post(() -> buildBracketGuideCacheAsync(startLine, endLine, v, cfg));
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
          if (editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("BracketGuides", "getTokens line=" + globalLine + " from MAIN cache, tokens=" + tokens.size());
          }
          return tokens;
        }
      }
    }
    
    // Fallback to fallback cache to prevent flickering
    // Always use fallback if line is in range, regardless of edit version
    // This prevents guides from disappearing during text input
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackTokens.size()) {
        List<BracketGuideToken> tokens = fallbackTokens.get(idx);
        if (tokens != null) {
          if (editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("BracketGuides", "getTokens line=" + globalLine + " from FALLBACK cache, tokens=" + tokens.size());
          }
          return tokens;
        }
      }
    }
    
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "getTokens line=" + globalLine + " NO CACHE, main=[" + start + "," + end + "] fallback=[" + fallbackCacheStartLine + "," + fallbackCacheEndLine + "]");
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
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("BracketGuides", "getState line=" + globalLine + " from MAIN cache, stack=" + (state != null ? state.stack.size() : "null"));
        }
        return state;
      }
    }
    
    // Fallback to fallback cache to prevent flickering
    // Always use fallback if line is in range, regardless of edit version
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackStates.size()) {
        BracketGuideState state = fallbackStates.get(idx);
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("BracketGuides", "getState line=" + globalLine + " from FALLBACK cache, stack=" + (state != null ? state.stack.size() : "null"));
        }
        return state;
      }
    }
    
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "getState line=" + globalLine + " NO CACHE");
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

  /**
   * Draws bracket guides for a line.
   */
  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    if (globalLine < 0 || globalLine >= editor.getLinesCount()) return;
    if (!isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;

    BracketGuideState st = getBracketGuideStateForLine(globalLine);
    if (editor.DEBUG_RENDER_LOGS) {
      int stackSize = (st != null) ? st.stack.size() : -1;
      int tokenSize = (guideTokens != null) ? guideTokens.size() : -1;
      android.util.Log.d(
          "SodiumRender",
          "bracketGuides line=" + globalLine + " stack=" + stackSize + " tokens=" + tokenSize);
    }
    if (st != null && st.stack.isEmpty()) return;

    // Use window-cached tokens if available, otherwise fallback to the line's stack state.
    // This solves "disappearing lines" when scrolling while ensuring lines stop correctly.
    List<BracketGuideToken> tokensToDraw =
        (guideTokens != null && !guideTokens.isEmpty()) ? guideTokens : null;

    if (tokensToDraw == null && st != null && !st.stack.isEmpty()) {
      tokensToDraw = getGuideTokensFromStack(st.stack);
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "draw line=" + globalLine + " using state stack, tokens=" + tokensToDraw.size());
      }
    }

    if (tokensToDraw == null || tokensToDraw.isEmpty()) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketGuides", "draw line=" + globalLine + " SKIPPED - no tokens, guideTokens=" + (guideTokens != null ? guideTokens.size() : "null") + " state=" + (st != null ? "has" : "null"));
      }
      return;
    }

    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketGuides", "draw line=" + globalLine + " DRAWING " + tokensToDraw.size() + " guides");
    }

    // During fast scroll, always draw if we have tokens (don't skip)
    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
    if (!showGuidesDuringFastScroll && fastScroll) return;

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

  /**
   * Gets the guide X position at the START of the character (not center).
   * This ensures guides are drawn at the beginning of braces for better visual alignment.
   */
  private float getGuideX(String line, int column, int globalLine) {
    return editor.getGuideXForColumn(line, column, globalLine);
  }

  private void buildBracketGuideCacheAsync(int startLine, int endLine, int v, int cfg) {
    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    BracketGuideState stateBeforeStart = copyState(state);
    BracketGuideState stateAtStart = null;
    java.util.ArrayList<List<BracketGuideToken>> tokensWindow = new java.util.ArrayList<>();
    java.util.ArrayList<BracketGuideState> statesWindow = new java.util.ArrayList<>();
    tokensWindow.ensureCapacity(endLine - startLine + 1);
    statesWindow.ensureCapacity(endLine - startLine + 1);
    int stickyColumn = -1;
    boolean stickyActive = false;
    java.io.RandomAccessFile raf = null;
    try {
      if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
        raf = new java.io.RandomAccessFile(editor.fileIO.sourceFile, "r");
      }

      // Use smaller checkpoint step during fast scroll for quicker initial build
      int originalCheckpointStep = bracketGuideCheckpointStep;
      boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
      if (fastScroll && useFastBuildDuringFastScroll) {
        bracketGuideCheckpointStep = Math.min(bracketGuideCheckpointStepFast, endLine - startLine + 1);
      }

      ensureBracketGuideCheckpointsUpTo(endLine, null, raf);
      int checkpointIdx = getCheckpointIndexForLine(startLine);
      if (checkpointIdx >= 0) {
        state = copyState(bracketGuideCheckpointStates.get(checkpointIdx));
        int checkpointLine = bracketGuideCheckpointLines.get(checkpointIdx);
        for (int line = checkpointLine + 1; line < startLine; line++) {
          String text = getLineTextForGuideScan(line, null, raf);
          if (text == null) text = "";
          updateBracketGuideStateForLine(text, line, state);
        }
      } else if (startLine > 0) {
        for (int line = 0; line < startLine; line++) {
          String text = getLineTextForGuideScan(line, null, raf);
          if (text == null) text = "";
          updateBracketGuideStateForLine(text, line, state);
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
      for (int line = startLine; line <= endLine; line++) {
        // Check if edit version changed during build - abort if so
        if (editor.editOperators.editVersion.get() != v || getBracketGuideCacheConfigHash() != cfg) {
          bracketGuideBuildInProgress = false;
          bracketGuideCheckpointStep = originalCheckpointStep;
          return;
        }

        String text = getLineTextForGuideScan(line, null, raf);
        if (text == null) text = "";
        List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, state);
        statesWindow.add(copyState(state));
        if (line == startLine) stateAtStart = copyState(state);
        tokensWindow.add(tokens);
      }

      // Restore original checkpoint step
      bracketGuideCheckpointStep = originalCheckpointStep;
    } catch (Exception ignored) {
      // fall back to empty state
    } finally {
      if (raf != null) {
        try { raf.close(); } catch (Exception ignored) {}
      }
    }
    BracketGuideState finalStateAtStart = (stateAtStart != null) ? stateAtStart : copyState(state);
    BracketGuideState finalState = copyState(state);
    BracketGuideState finalStateBeforeStart = stateBeforeStart;
    int finalStickyColumn = stickyColumn;
    boolean finalStickyActive = stickyActive;
    
    // Double buffering: swap caches atomically on UI thread
    // This prevents guides from disappearing during cache update
    editor.post(() -> {
      if (editor.editOperators.editVersion.get() != v || getBracketGuideCacheConfigHash() != cfg) {
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("BracketGuides", "build ABORTED - version/config mismatch");
        }
        bracketGuideBuildInProgress = false;
        return;
      }

      // Save old cache to fallback before swapping (prevents flickering)
      if (bracketGuideCacheStartLine >= 0 && bracketGuideCacheEndLine >= bracketGuideCacheStartLine) {
        fallbackCacheStartLine = bracketGuideCacheStartLine;
        fallbackCacheEndLine = bracketGuideCacheEndLine;
        fallbackCacheEditVersion = bracketGuideCacheEditVersion;
        fallbackTokens.clear();
        fallbackTokens.addAll(bracketGuideTokensWindow);
        fallbackStates.clear();
        fallbackStates.addAll(bracketGuideStatesWindow);
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("BracketGuides", "build SWAP - saved old cache [" + bracketGuideCacheStartLine + "," + bracketGuideCacheEndLine + "] to fallback, new=[" + startLine + "," + endLine + "]");
        }
      } else {
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("BracketGuides", "build SWAP - no old cache to save, new=[" + startLine + "," + endLine + "]");
        }
      }

      // Atomic swap - old cache is replaced with new in one step
      // Keep the old cache valid until new one is ready
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

  private void ensureBracketGuideCheckpointsUpTo(
      int endLine, @Nullable java.util.Map<Integer, String> directLines, @Nullable java.io.RandomAccessFile raf) {
    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();
    if (v != bracketGuideCheckpointEditVersion || cfg != bracketGuideCheckpointConfigHash) {
      bracketGuideCheckpointLines.clear();
      bracketGuideCheckpointStates.clear();
      bracketGuideCheckpointEditVersion = v;
      bracketGuideCheckpointConfigHash = cfg;
      bracketGuideCheckpointMaxLine = -1;
    }
    if (endLine <= bracketGuideCheckpointMaxLine) return;
    BracketGuideState state;
    int startLine = bracketGuideCheckpointMaxLine + 1;
    if (bracketGuideCheckpointStates.isEmpty()) {
      state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    } else {
      state = copyState(bracketGuideCheckpointStates.get(bracketGuideCheckpointStates.size() - 1));
    }
    for (int line = startLine; line <= endLine; line++) {
      String text = getLineTextForGuideScan(line, directLines, raf);
      if (text == null) text = "";
      updateBracketGuideStateForLine(text, line, state);
      if (line % bracketGuideCheckpointStep == 0) {
        bracketGuideCheckpointLines.add(line);
        bracketGuideCheckpointStates.add(copyState(state));
      }
      bracketGuideCheckpointMaxLine = line;
    }
  }

  private int getCheckpointIndexForLine(int line) {
    if (bracketGuideCheckpointLines.isEmpty()) return -1;
    int lo = 0;
    int hi = bracketGuideCheckpointLines.size() - 1;
    int best = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int v = bracketGuideCheckpointLines.get(mid);
      if (v < line) {
        best = mid;
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

  private static BracketGuideState copyState(BracketGuideState src) {
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
