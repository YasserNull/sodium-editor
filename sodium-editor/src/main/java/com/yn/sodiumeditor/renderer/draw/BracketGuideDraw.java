package com.yn.sodiumeditor.renderer.draw;

import android.graphics.Canvas;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.bracket.BracketGuideToken;
import com.yn.sodiumeditor.core.guides.bracket.BracketGuides;
import java.util.List;

/**
 * Handles drawing bracket guides for lines.
 */
public class BracketGuideDraw {
  private final SodiumEditor editor;
  private final BracketGuides bracketGuides;

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

  public BracketGuideDraw(SodiumEditor editor, BracketGuides bracketGuides) {
    this.editor = editor;
    this.bracketGuides = bracketGuides;
  }

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
    // Can draw if main cache is valid OR fallback cache has any entries OR span cache is valid
    boolean mainValid = (bracketGuides.mainCache.bracketGuideCacheStartLine >= 0 && bracketGuides.mainCache.bracketGuideCacheEndLine >= bracketGuides.mainCache.bracketGuideCacheStartLine);
    boolean fallbackValid = bracketGuides.fallbackCache.containsLine(0) || bracketGuides.fallbackCache.getEditVersion() >= 0;
    boolean spanValid = bracketGuides.spanCache.canDraw();
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
  public void resetDrawTracking() {
    lastRenderedEditVersion = -1;
    lastRenderedConfigHash = 0;
    lastRenderedWindowStart = -1;
    lastRenderedWindowEnd = -1;
  }

  /**
   * Draws bracket guides for a line.
   */
  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    if (globalLine < 0 || globalLine >= editor.view.getLinesCount()) return;
    if (!bracketGuides.isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;

    // For synchronous rendering, we rely on the passed tokens directly
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
    int firstNonSpace = com.yn.sodiumeditor.utils.TextUtils.getFirstNonSpaceIndex(line);

    // Only adjust to closing brace if we have window-cached tokens (representing state at start of line)
    boolean isClosingBraceLine = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    boolean adjustTopGuideToClosingBrace = (guideTokens != null && !guideTokens.isEmpty() && isClosingBraceLine);
    float closingBraceX = adjustTopGuideToClosingBrace ? bracketGuides.getGuideX(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : tokensToDraw) {
      // Draw guides for all bracket types: {}, (), []
      if (token.bracket != '{' && token.bracket != '(' && token.bracket != '[') {
        tokenIndex++;
        continue;
      }
      // Calculate X at draw time to account for zoom level changes
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.getX(bracketGuides, line, globalLine);
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

      if (!editor.layout.isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuides.bracketGuidePaint);
    }
  }

  /**
   * Draws bracket guides for a line directly from the stack (avoids allocations).
   */
  public void drawBracketGuidesForLineFromStack(
      Canvas canvas, String line, int globalLine, java.util.ArrayDeque<BracketGuideToken> stack) {
    if (globalLine < 0 || globalLine >= editor.view.getLinesCount()) return;
    if (!bracketGuides.isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;
    if (stack == null || stack.isEmpty()) return;

    if (SodiumEditor.DEBUG_RENDER_LOGS) {
      frameLineCount++;
      frameTokenCount += stack.size();
    }

    if (line == null) line = "";
    editor.indentGuides.guideSeenXCount = 0;
    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    int firstNonSpace = com.yn.sodiumeditor.utils.TextUtils.getFirstNonSpaceIndex(line);

    boolean isClosingBraceLine = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    boolean adjustTopGuideToClosingBrace = (isClosingBraceLine && !stack.isEmpty());
    float closingBraceX = adjustTopGuideToClosingBrace ? bracketGuides.getGuideX(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : stack) {
      if (token.bracket != '{' && token.bracket != '(' && token.bracket != '[') {
        tokenIndex++;
        continue;
      }
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.getX(bracketGuides, line, globalLine);
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

      if (!editor.layout.isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuides.bracketGuidePaint);
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
            + " showDuringFast=" + bracketGuides.showGuidesDuringFastScroll,
        500);
  }

  /**
   * Gets the guide X position at the START of the character (not center).
   */
  public float getGuideX(String line, int column, int globalLine) {
    return editor.layout.getGuideXForColumn(line, column, globalLine);
  }
}
