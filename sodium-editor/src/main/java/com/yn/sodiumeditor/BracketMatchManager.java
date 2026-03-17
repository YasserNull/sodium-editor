package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Manages bracket matching for the SodiumEditor.
 * Finds and highlights matching bracket pairs.
 */
public class BracketMatchManager {

  private final SodiumEditor editor;

  // Bracket matching state
  public boolean isBracketMatchingEnabled = true;
  public final Paint bracketMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketMatchStrokeWidth = 2f;
  public float baseBracketMatchStrokeWidth = bracketMatchStrokeWidth;
  public float baseBracketMatchTextSizePx = 0f;
  public final RectF bracketMatchRect = new RectF();

  // Bracket match cache
  @Nullable public SodiumEditor.BracketMatch cachedBracketMatch = null;
  public int cachedBracketMatchCursorLine = -1;
  public int cachedBracketMatchCursorChar = -1;
  public int cachedBracketMatchEditVersion = -1;

  public BracketMatchManager(SodiumEditor editor) {
    this.editor = editor;
    bracketMatchPaint.setColor(editor.cursorAndHandlesColor);
    bracketMatchPaint.setStyle(Paint.Style.STROKE);
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);
  }

  /**
   * Enables or disables bracket matching.
   */
  public void setBracketMatchingEnabled(boolean enabled) {
    if (this.isBracketMatchingEnabled == enabled) return;
    this.isBracketMatchingEnabled = enabled;
    clearBracketMatchCache();
    editor.invalidate();
  }

  /**
   * Sets the bracket match color.
   */
  public void setBracketMatchColor(int color) {
    bracketMatchPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the bracket match stroke width.
   */
  public void setBracketMatchStrokeWidth(float width) {
    if (this.bracketMatchStrokeWidth == width) return;
    this.baseBracketMatchStrokeWidth = width;
    this.baseBracketMatchTextSizePx = editor.paint.getTextSize();
    updateStrokeWidth();
    editor.invalidate();
  }

  /**
   * Updates stroke width based on text size.
   */
  public void updateStrokeWidth() {
    float sizePx = editor.paint.getTextSize();
    bracketMatchStrokeWidth = Math.max(
        1f,
        editor.scaleByTextSize(baseBracketMatchStrokeWidth, baseBracketMatchTextSizePx, sizePx));
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);
  }

  /**
   * Clears the bracket match cache.
   */
  public void clearBracketMatchCache() {
    cachedBracketMatch = null;
    cachedBracketMatchCursorLine = -1;
    cachedBracketMatchCursorChar = -1;
    cachedBracketMatchEditVersion = -1;
  }

  /**
   * Finds and caches bracket match for the current cursor position.
   */
  public SodiumEditor.BracketMatch findAndCacheBracketMatch(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!isBracketMatchingEnabled) return null;

    int v = editor.editVersion.get();
    if (cachedBracketMatch != null
        && cachedBracketMatchCursorLine == editor.cursor.cursorLine
        && cachedBracketMatchCursorChar == editor.cursor.cursorChar
        && cachedBracketMatchEditVersion == v) {
      return cachedBracketMatch;
    }

    SodiumEditor.BracketMatch match = findBracketMatchInVisible(firstVisibleLine, lastVisibleLine, directLines);
    if (match != null) {
      cachedBracketMatch = match;
      cachedBracketMatchCursorLine = editor.cursor.cursorLine;
      cachedBracketMatchCursorChar = editor.cursor.cursorChar;
      cachedBracketMatchEditVersion = v;
    }
    return match;
  }

  /**
   * Finds bracket match in visible range.
   */
  public SodiumEditor.BracketMatch findBracketMatchInVisible(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!isBracketMatchingEnabled) return null;
    if (editor.cursor.cursorLine < firstVisibleLine || editor.cursor.cursorLine > lastVisibleLine) return null;

    String cursorLineText = editor.getLineTextForRenderWithDirect(editor.cursor.cursorLine, directLines);
    if (cursorLineText == null) return null;

    int targetIndex = -1;
    char targetChar = 0;
    if (editor.cursor.cursorChar > 0 && editor.cursor.cursorChar - 1 < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar - 1);
      if (SodiumEditor.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar - 1;
        targetChar = c;
      }
    }
    if (targetIndex < 0 && editor.cursor.cursorChar < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar);
      if (SodiumEditor.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar;
        targetChar = c;
      }
    }
    if (targetIndex < 0) return null;

    SodiumEditor.HighlightLineState startState = editor.highlite.getLineStateAtStart(firstVisibleLine);
    boolean inBlockComment = startState.inBlockComment && editor.isBlockCommentsEnabled;
    int stringState = startState.stringState;
    if (!editor.isBlockCommentsEnabled) inBlockComment = false;
    if (!editor.isMultiLineStringsEnabled && stringState != SodiumEditor.STRING_STATE_TRIPLE) stringState = 0;
    if (!editor.isBacktickStringsEnabled && stringState == SodiumEditor.STRING_STATE_BACKTICK) stringState = 0;
    if (!editor.isTripleQuoteStringsEnabled && stringState == SodiumEditor.STRING_STATE_TRIPLE) stringState = 0;

    ArrayDeque<SodiumEditor.BracketToken> stack = new ArrayDeque<>();

    for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
      String text = editor.getLineTextForRenderWithDirect(line, directLines);
      if (text == null) text = "";
      int len = text.length();
      int i = 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = SodiumEditor.findBlockCommentEnd(text, i);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          SodiumEditor.StringEndResult endResult = editor.findStringEndForState(text, i, stringState);
          int endPos = endResult.found ? endResult.endIndex : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (editor.highlite.isLineCommentStart(text, i)) {
          if (line == editor.cursor.cursorLine && targetIndex >= i) return null;
          inLineComment = true;
          break;
        }

        if (editor.isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !SodiumEditor.isTokenEscaped(text, i)) {
          int end = SodiumEditor.findBlockCommentEnd(text, i + 2);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (editor.isTripleQuoteStart(text, i) && !SodiumEditor.isEscaped(text, i)) {
          int end = SodiumEditor.findTripleQuoteEnd(text, i + 3);
          int endPos = end >= 0 ? end + 3 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (editor.isTripleQuoteStringsEnabled) {
              stringState = SodiumEditor.STRING_STATE_TRIPLE;
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (editor.isStringDelimiter(c) && !SodiumEditor.isEscaped(text, i)) {
          int end = SodiumEditor.findStringEnd(text, i + 1, c);
          int endPos = end >= 0 ? end + 1 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (editor.isMultiLineStringsEnabled) {
              stringState = editor.getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (SodiumEditor.isBracketChar(c) && !SodiumEditor.isEscaped(text, i)) {
          SodiumEditor.BracketToken token = new SodiumEditor.BracketToken(line, i, c);
          if (SodiumEditor.isOpeningBracket(c)) {
            stack.push(token);
          } else if (SodiumEditor.isClosingBracket(c)) {
            if (!stack.isEmpty() && stack.peek().bracket == SodiumEditor.matchingBracket(c)) {
              SodiumEditor.BracketToken open = stack.pop();
              if (line == editor.cursor.cursorLine && i == targetIndex) {
                return new SodiumEditor.BracketMatch(open.line, open.ch, line, i);
              }
              if (open.line == editor.cursor.cursorLine && open.ch == targetIndex) {
                return new SodiumEditor.BracketMatch(open.line, open.ch, line, i);
              }
            }
          }
        }

        i++;
      }
    }
    return new SodiumEditor.BracketMatch(editor.cursor.cursorLine, targetIndex, editor.cursor.cursorLine, targetIndex);
  }

  /**
   * Draws bracket match for a line.
   */
  public void drawBracketMatchForLine(
      Canvas canvas, String line, int globalLine, SodiumEditor.BracketMatch match) {
    if (match == null) return;
    if (globalLine != match.openLine && globalLine != match.closeLine) return;
    if (line == null || line.isEmpty()) return;

    if (match.openLine == match.closeLine) {
      if (match.openChar == match.closeChar) {
        drawBracketBox(canvas, line, globalLine, match.openChar);
        return;
      }

      if (Math.abs(match.openChar - match.closeChar) == 1) {
        int leftIndex = Math.min(match.openChar, match.closeChar);
        int rightIndex = Math.max(match.openChar, match.closeChar);
        drawBracketBoxRange(canvas, line, globalLine, leftIndex, rightIndex);
      } else {
        drawBracketBox(canvas, line, globalLine, match.openChar);
        drawBracketBox(canvas, line, globalLine, match.closeChar);
      }
      return;
    }

    int index = (globalLine == match.openLine) ? match.openChar : match.closeChar;
    drawBracketBox(canvas, line, globalLine, index);
  }

  /**
   * Draws bracket box for a single character.
   */
  public void drawBracketBox(Canvas canvas, String line, int globalLine, int index) {
    if (index < 0 || index >= line.length()) return;

    float left = editor.measureText(line, index, globalLine);
    float right = editor.measureText(line, index + 1, globalLine);
    if (right <= left) right = left + editor.measureTextWithVisualSpaces(line, index, index + 1, editor.paint);

    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  /**
   * Draws bracket box for a range of characters.
   */
  public void drawBracketBoxRange(
      Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    if (endIndex < startIndex) return;

    float left = editor.measureText(line, startIndex, globalLine);
    float right = editor.measureText(line, endIndex + 1, globalLine);
    if (right <= left)
      right = left + editor.measureTextWithVisualSpaces(line, startIndex, endIndex + 1, editor.paint);
    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  /**
   * Draws bracket box rectangle.
   */
  public void drawBracketBoxRect(Canvas canvas, int globalLine, float left, float right) {
    final float padding = 1f;
    final float top = editor.getDrawLineTop(globalLine) + padding;
    final float bottom = top + editor.lineHeight - (padding * 2f);

    float l = left - padding;
    float r = right + padding;
    if (r <= l) return;

    bracketMatchRect.set(l, top, r, bottom);
    float radius = Math.max(2f, bracketMatchStrokeWidth + 1f);
    canvas.drawRoundRect(bracketMatchRect, radius, radius, bracketMatchPaint);
  }
}
