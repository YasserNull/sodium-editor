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
  public boolean isBracketGuidesEnabled = false;
  public final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketGuideStrokeWidth = 2f;
  public float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
  public float baseBracketGuideTextSizePx = 0f;

  // Bracket guide cache
  public int bracketGuideCacheStartLine = -1;
  public int bracketGuideCacheEndLine = -1;
  public int bracketGuideCacheEditVersion = -1;
  public int bracketGuideCacheConfigHash = 0;
  public final java.util.ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow =
      new java.util.ArrayList<>();

  public BracketGuides(SodiumEditor editor) {
    this.editor = editor;
    bracketGuidePaint.setColor(0xFF888888);
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
    invalidateBracketGuideCache();
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
    bracketGuideCacheStartLine = -1;
    bracketGuideCacheEndLine = -1;
    bracketGuideCacheEditVersion = -1;
    bracketGuideCacheConfigHash = 0;
    bracketGuideTokensWindow.clear();
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
      invalidateBracketGuideCache();
      return;
    }

    int v = editor.editOperators.editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();
    if (startLine == bracketGuideCacheStartLine
        && endLine == bracketGuideCacheEndLine
        && v == bracketGuideCacheEditVersion
        && cfg == bracketGuideCacheConfigHash) {
      return;
    }

    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    bracketGuideTokensWindow.clear();
    bracketGuideTokensWindow.ensureCapacity(endLine - startLine + 1);

    for (int line = startLine; line <= endLine; line++) {
      String text = editor.getLineTextForRenderWithDirect(line, directLines);
      if (text == null) text = "";
      List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, state);
      bracketGuideTokensWindow.add(tokens);
    }

    bracketGuideCacheStartLine = startLine;
    bracketGuideCacheEndLine = endLine;
    bracketGuideCacheEditVersion = v;
    bracketGuideCacheConfigHash = cfg;
  }

  /**
   * Gets bracket guide tokens for a line.
   */
  public List<BracketGuideToken> getBracketGuideTokensForLine(int globalLine) {
    if (!isBracketGuidesEnabled) return Collections.emptyList();
    int start = bracketGuideCacheStartLine;
    int end = bracketGuideCacheEndLine;
    if (globalLine < start || globalLine > end) return Collections.emptyList();
    int idx = globalLine - start;
    if (idx < 0 || idx >= bracketGuideTokensWindow.size()) return Collections.emptyList();
    List<BracketGuideToken> tokens = bracketGuideTokensWindow.get(idx);
    return tokens != null ? tokens : Collections.emptyList();
  }

  /**
   * Updates bracket guide state for a line.
   */
  public List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);
    List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = SodiumEditor.findBlockCommentEnd(line, i);
        if (end < 0) return tokensToDraw;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) return tokensToDraw;
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
          return tokensToDraw;
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
          return tokensToDraw;
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
          return tokensToDraw;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}') && !Highlite.isEscaped(line, i)) {
        if (c == '{') {
          int column = editor.getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace);
          float x = editor.getGuideXForColumn(line, column, globalLine);
          state.stack.push(new BracketGuideToken(column, x));
        } else if (c == '}') {
          if (!state.stack.isEmpty()) {
            state.stack.pop();
          }
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
    if (!isBracketGuidesEnabled
        || editor.isHeavyDrawSuppressed()
        || guideTokens == null
        || guideTokens.isEmpty()) return;
    if (line == null) line = "";
    editor.indentGuides.guideSeenXCount = 0;
    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);
    boolean adjustTopGuideToClosingBrace =
        (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    float closingBraceX =
        adjustTopGuideToClosingBrace ? editor.getGuideXForColumn(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : guideTokens) {
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.x;
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
   */
  public static class BracketGuideToken {
    public final int column;
    public final float x;

    public BracketGuideToken(int column, float x) {
      this.column = column;
      this.x = x;
    }
  }
}
