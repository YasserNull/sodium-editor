package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;

public final class BracketMatchManager {
  private final SodiumEditorView view;

  private boolean enabled = false;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float strokeWidth = 2f;
  private float baseStrokeWidth = strokeWidth;
  private float baseTextSizePx = 0f;
  private final RectF rect = new RectF();

  @Nullable private BracketMatch cached = null;
  private int cachedCursorLine = -1;
  private int cachedCursorChar = -1;
  private int cachedEditVersion = -1;

  BracketMatchManager(SodiumEditorView view) {
    this.view = view;
    paint.setStyle(Paint.Style.STROKE);
  }

  void setEnabled(boolean value) {
    if (enabled == value) return;
    enabled = value;
    if (!enabled) clearCache();
  }

  boolean isEnabled() {
    return enabled;
  }

  void setColor(int color) {
    paint.setColor(color);
  }

  void setStrokeWidth(float width) {
    if (baseStrokeWidth == width) return;
    baseStrokeWidth = width;
    baseTextSizePx = view.getPaintTextSizeForMatch();
  }

  void setBaseTextSizePx(float sizePx) {
    baseTextSizePx = sizePx;
  }

  float getBaseStrokeWidth() {
    return baseStrokeWidth;
  }

  float getBaseTextSizePx() {
    return baseTextSizePx;
  }

  void applyScaledStrokeWidth(float scaledWidth) {
    strokeWidth = scaledWidth;
    paint.setStrokeWidth(strokeWidth);
  }

  void clearCache() {
    cached = null;
    cachedCursorLine = -1;
    cachedCursorChar = -1;
    cachedEditVersion = -1;
  }

  @Nullable
  public BracketMatch getMatch(int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!enabled) return null;
    int v = view.getEditVersionForMatch();
    int line = view.cursorManager.getLine();
    int ch = view.cursorManager.getChar();
    if (cached != null
        && cachedCursorLine == line
        && cachedCursorChar == ch
        && cachedEditVersion == v) {
      return cached;
    }
    BracketMatch match = findBracketMatchInVisible(firstVisibleLine, lastVisibleLine, directLines);
    if (match != null) {
      cached = match;
      cachedCursorLine = line;
      cachedCursorChar = ch;
      cachedEditVersion = v;
    } else {
      cached = null;
    }
    return match;
  }

  public void drawMatchForLine(Canvas canvas, String line, int globalLine, BracketMatch match) {
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

  private BracketMatch findBracketMatchInVisible(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!enabled) return null;
    int cursorLine = view.cursorManager.getLine();
    int cursorChar = view.cursorManager.getChar();
    if (cursorLine < firstVisibleLine || cursorLine > lastVisibleLine) return null;

    String cursorLineText = view.getLineTextForRenderWithDirectForMatch(cursorLine, directLines);
    if (cursorLineText == null) return null;

    int targetIndex = -1;
    char targetChar = 0;
    if (cursorChar > 0 && cursorChar - 1 < cursorLineText.length()) {
      char c = cursorLineText.charAt(cursorChar - 1);
      if (isBracketChar(c)) {
        targetIndex = cursorChar - 1;
        targetChar = c;
      }
    }
    if (targetIndex < 0 && cursorChar < cursorLineText.length()) {
      char c = cursorLineText.charAt(cursorChar);
      if (isBracketChar(c)) {
        targetIndex = cursorChar;
        targetChar = c;
      }
    }
    if (targetIndex < 0) return null;

    HighlightManager.HighlightLineState hlState = view.highlightManager.getLineStateAtStart(firstVisibleLine);
    BracketMatchLineState startState = new BracketMatchLineState(hlState.inBlockComment, hlState.stringState);
    boolean inBlockComment = startState.inBlockComment && view.isBlockCommentsEnabledForMatch();
    int stringState = startState.stringState;
    if (!view.isBlockCommentsEnabledForMatch()) inBlockComment = false;
    if (!view.isMultiLineStringsEnabledForMatch() && stringState != view.getStringStateTripleForMatch())
      stringState = 0;
    if (!view.isBacktickStringsEnabledForMatch() && stringState == view.getStringStateBacktickForMatch())
      stringState = 0;
    if (!view.isTripleQuoteStringsEnabledForMatch() && stringState == view.getStringStateTripleForMatch())
      stringState = 0;

    ArrayDeque<BracketToken> stack = new ArrayDeque<>();

    for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
      String text = view.getLineTextForRenderWithDirectForMatch(line, directLines);
      if (text == null) text = "";
      int len = text.length();
      int i = 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = HighlightManager.findBlockCommentEnd(text, i);
          int endPos = (end < 0) ? len : end + 2;
          if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          HighlightManager.StringEndResult endResult =
              view.highlightManager.findStringEndForState(text, i, stringState);
          int endPos = endResult.found ? endResult.endIndex : len;
          if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (view.highlightManager.isLineCommentStart(text, i)) {
          if (line == cursorLine && targetIndex >= i) return null;
          inLineComment = true;
          break;
        }

        if (view.isBlockCommentsEnabledForMatch()
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !HighlightManager.isTokenEscaped(text, i)) {
          int end = HighlightManager.findBlockCommentEnd(text, i + 2);
          int endPos = (end < 0) ? len : end + 2;
          if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (view.highlightManager.isTripleQuoteStart(text, i) && !HighlightManager.isEscaped(text, i)) {
          int end = HighlightManager.findTripleQuoteEnd(text, i + 3);
          int endPos = end >= 0 ? end + 3 : len;
          if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (view.isTripleQuoteStringsEnabledForMatch()) {
              stringState = view.getStringStateTripleForMatch();
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (view.highlightManager.isStringDelimiter(c) && !HighlightManager.isEscaped(text, i)) {
          int end = HighlightManager.findStringEnd(text, i + 1, c);
          int endPos = end >= 0 ? end + 1 : len;
          if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (view.isMultiLineStringsEnabledForMatch()) {
              stringState = view.highlightManager.getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (isBracketChar(c) && !HighlightManager.isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (isOpeningBracket(c)) {
            stack.push(token);
          } else if (isClosingBracket(c)) {
            if (!stack.isEmpty() && stack.peek().bracket == matchingBracket(c)) {
              BracketToken open = stack.pop();
              if (line == cursorLine && i == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
              if (open.line == cursorLine && open.ch == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
            }
          }
        }

        i++;
      }
    }
    return new BracketMatch(cursorLine, targetIndex, cursorLine, targetIndex);
  }

  private void drawBracketBox(Canvas canvas, String line, int globalLine, int index) {
    if (index < 0 || index >= line.length()) return;

    float left = view.highlightManager.measureText(line, index, globalLine);
    float right = view.highlightManager.measureText(line, index + 1, globalLine);
    if (right <= left)
      right = left + view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, index, index + 1, view.paint);

    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  private void drawBracketBoxRange(
      Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    if (endIndex < startIndex) return;

    float left = view.highlightManager.measureText(line, startIndex, globalLine);
    float right = view.highlightManager.measureText(line, endIndex + 1, globalLine);
    if (right <= left)
      right = left + view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, startIndex, endIndex + 1, view.paint);
    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  private void drawBracketBoxRect(Canvas canvas, int globalLine, float left, float right) {
    final float padding = 1f;
    final float top = view.getDrawLineTopForMatch(globalLine) + padding;
    final float bottom = top + view.getLineHeightForMatch() - (padding * 2f);

    float l = left - padding;
    float r = right + padding;
    if (r <= l) return;

    rect.set(l, top, r, bottom);
    float radius = Math.max(2f, strokeWidth + 1f);
    canvas.drawRoundRect(rect, radius, radius, paint);
  }

  private static boolean isBracketChar(char c) {
    return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
  }

  public static boolean isOpeningBracket(char c) {
    return c == '(' || c == '[' || c == '{';
  }

  public static boolean isClosingBracket(char c) {
    return c == ')' || c == ']' || c == '}';
  }

  public static char matchingBracket(char c) {
    switch (c) {
      case '(':
        return ')';
      case ')':
        return '(';
      case '[':
        return ']';
      case ']':
        return '[';
      case '{':
        return '}';
      case '}':
        return '{';
      default:
        return 0;
    }
  }

  private static final class BracketToken {
    final int line;
    final int ch;
    final char bracket;

    BracketToken(int line, int ch, char bracket) {
      this.line = line;
      this.ch = ch;
      this.bracket = bracket;
    }
  }

  public static final class BracketMatch {
    final int openLine;
    final int openChar;
    final int closeLine;
    final int closeChar;

    BracketMatch(int openLine, int openChar, int closeLine, int closeChar) {
      this.openLine = openLine;
      this.openChar = openChar;
      this.closeLine = closeLine;
      this.closeChar = closeChar;
    }
  }

  static final class BracketMatchLineState {
    final boolean inBlockComment;
    final int stringState;

    BracketMatchLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public void setBracketMatchingEnabled(SodiumEditorView view, boolean enabled) {
    this.setEnabled(enabled);
    view.invalidate();
  }

  public void setBracketMatchColor(SodiumEditorView view, int color) {
    this.setColor(color);
    view.invalidate();
  }

  public void setBracketMatchStrokeWidth(SodiumEditorView view, float width) {
    this.setStrokeWidth(width);
    view.updateTextSizeDependentMetrics();
    view.invalidate();
  }
}
