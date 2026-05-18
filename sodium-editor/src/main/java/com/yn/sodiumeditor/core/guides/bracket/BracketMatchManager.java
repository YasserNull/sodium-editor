package com.yn.sodiumeditor.core.guides.bracket; 

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.bracket.BracketCache;
import com.yn.sodiumeditor.core.guides.bracket.BracketMatch;
import com.yn.sodiumeditor.core.guides.bracket.BracketToken;
import com.yn.sodiumeditor.core.highlight.Highlite;
import com.yn.sodiumeditor.utils.FunctionLog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.utils.TextUtils;
/**
 * Manages bracket matching for the SodiumEditor.
 * Finds and highlights matching bracket pairs.
 */
public class BracketMatchManager {

  private final SodiumEditor editor;

  // Bracket matching state
  public boolean isBracketMatchingEnabled = false;
  public final Paint bracketMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketMatchStrokeWidth = 3f;
  public float baseBracketMatchStrokeWidth = bracketMatchStrokeWidth;
  public float baseBracketMatchTextSizePx = 0f;
  public final RectF bracketMatchRect = new RectF();
  public int bracketMatchColor = 0x302196F3; // Opaque Yellow
  // Bracket match cache
  @Nullable public BracketMatch cachedBracketMatch = null;
  public int cachedBracketMatchCursorLine = -1;
  public int cachedBracketMatchCursorChar = -1;
  public int cachedBracketMatchEditVersion = -1;

  public BracketMatchManager(SodiumEditor editor) {
    FunctionLog.f("BracketMatchManager", "BracketMatchManager", editor);
    this.editor = editor;
    bracketMatchPaint.setColor(bracketMatchColor);
    bracketMatchPaint.setStyle(Paint.Style.STROKE);
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);
  }

  /**
   * Enables or disables bracket matching.
   */
  public void setBracketMatchingEnabled(boolean enabled) {
    FunctionLog.f("BracketMatchManager", "setBracketMatchingEnabled", enabled);
    if (this.isBracketMatchingEnabled == enabled) return;
    this.isBracketMatchingEnabled = enabled;
    if (enabled) editor.bracketCache.ensureScannedAsync();
    clearBracketMatchCache();
    editor.invalidate();
  }

  /**
   * Sets the bracket match color.
   */
  public void setBracketMatchColor(int color) {
    FunctionLog.f("BracketMatchManager", "setBracketMatchColor", color);
    bracketMatchColor = color;
    bracketMatchPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the bracket match stroke width.
   */
  public void setBracketMatchStrokeWidth(float width) {
    FunctionLog.f("BracketMatchManager", "setBracketMatchStrokeWidth", width);
    if (this.bracketMatchStrokeWidth == width) return;
    this.baseBracketMatchStrokeWidth = width;
    this.baseBracketMatchTextSizePx = editor.textRender.paint.getTextSize();
    updateStrokeWidth();
    editor.invalidate();
  }

  /**
   * Updates stroke width based on text size.
   */
  public void updateStrokeWidth() {
    FunctionLog.f("BracketMatchManager", "updateStrokeWidth");
    float sizePx = editor.textRender.paint.getTextSize();
    bracketMatchStrokeWidth = Math.max(
        1f,
        editor.view.scaleByTextSize(baseBracketMatchStrokeWidth, baseBracketMatchTextSizePx, sizePx));
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);
  }

  /**
   * Clears the bracket match cache.
   */
  public void clearBracketMatchCache() {
    FunctionLog.f("BracketMatchManager", "clearBracketMatchCache");
    cachedBracketMatch = null;
    cachedBracketMatchCursorLine = -1;
    cachedBracketMatchCursorChar = -1;
    cachedBracketMatchEditVersion = -1;
  }

  /**
   * Finds and caches bracket match for the current cursor position.
   * Searches the ENTIRE document to find matching brackets, not just visible range.
   */
  public BracketMatch findAndCacheBracketMatch(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    FunctionLog.f("BracketMatchManager", "findAndCacheBracketMatch", firstVisibleLine, lastVisibleLine, directLines);
    if (!isBracketMatchingEnabled) return null;

    int v = editor.editOperators.editVersion.get();
    if (cachedBracketMatch != null
        && cachedBracketMatchCursorLine == editor.cursor.cursorLine
        && cachedBracketMatchCursorChar == editor.cursor.cursorChar
        && cachedBracketMatchEditVersion == v) {
      return cachedBracketMatch;
    }

    // Search entire document for matching bracket, not just visible range
    BracketMatch match = findBracketMatchInDocument();
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
  public BracketMatch findBracketMatchInVisible(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    FunctionLog.f("BracketMatchManager", "findBracketMatchInVisible", firstVisibleLine, lastVisibleLine, directLines);
    if (!isBracketMatchingEnabled) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketMatch", "findBracketMatchInVisible: matching disabled");
      }
      return null;
    }
    if (editor.cursor.cursorLine < firstVisibleLine || editor.cursor.cursorLine > lastVisibleLine) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketMatch", "findBracketMatchInVisible: cursor line " + editor.cursor.cursorLine + " not in visible range [" + firstVisibleLine + "," + lastVisibleLine + "]");
      }
      return null;
    }

    String cursorLineText = editor.windowRender.getLineTextForRenderWithDirect(editor.cursor.cursorLine, directLines);
    if (cursorLineText == null) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketMatch", "findBracketMatchInVisible: cursor line text is null");
      }
      return null;
    }

    int targetIndex = -1;
    char targetChar = 0;
    if (editor.cursor.cursorChar > 0 && editor.cursor.cursorChar - 1 < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar - 1);
      if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar - 1;
        targetChar = c;
      }
    }
    if (targetIndex < 0 && editor.cursor.cursorChar < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar);
      if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar;
        targetChar = c;
      }
    }
    if (targetIndex < 0) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("BracketMatch", "findBracketMatchInVisible: no bracket at cursor cursorLine=" + editor.cursor.cursorLine + " cursorChar=" + editor.cursor.cursorChar + " lineText=\"" + cursorLineText + "\"");
      }
      return null;
    }

    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("BracketMatch", "findBracketMatchInVisible: found bracket at line=" + editor.cursor.cursorLine + " index=" + targetIndex + " char='" + targetChar + "'");
    }

    com.yn.sodiumeditor.renderer.HighliteRender.HighlightLineState startState = editor.highlite.getLineStateAtStart(firstVisibleLine);
    boolean inBlockComment = startState.inBlockComment && editor.highlite.isBlockCommentsEnabled;
    int stringState = startState.stringState;
    if (!editor.highlite.isBlockCommentsEnabled) inBlockComment = false;
    if (!editor.highlite.isMultiLineStringsEnabled && stringState != com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE) stringState = 0;
    if (!editor.highlite.isBacktickStringsEnabled && stringState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_BACKTICK) stringState = 0;
    if (!editor.highlite.isTripleQuoteStringsEnabled && stringState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE) stringState = 0;

    ArrayDeque<BracketToken> stack = new ArrayDeque<>();

    for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
      String text = editor.windowRender.getLineTextForRenderWithDirect(line, directLines);
      if (text == null) text = "";
      int len = text.length();
      int i = 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = Highlite.findBlockCommentEnd(text, i);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          com.yn.sodiumeditor.core.StringEndResult endResult =
              editor.highlite.findStringEndForState(text, i, stringState);
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

        if (editor.highlite.isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !Highlite.isTokenEscaped(text, i)) {
          int end = Highlite.findBlockCommentEnd(text, i + 2);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (editor.highlite.isTripleQuoteStart(text, i) && !Highlite.isEscaped(text, i)) {
          int end = Highlite.findTripleQuoteEnd(text, i + 3);
          int endPos = end >= 0 ? end + 3 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (editor.highlite.isTripleQuoteStringsEnabled) {
              stringState = com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE;
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(text, i)) {
          int end = Highlite.findStringEnd(text, i + 1, c);
          int endPos = end >= 0 ? end + 1 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (editor.highlite.isMultiLineStringsEnabled) {
              stringState = editor.highlite.getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c) && !Highlite.isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (com.yn.sodiumeditor.utils.TextUtils.isOpeningBracket(c)) {
            stack.push(token);
          } else if (com.yn.sodiumeditor.utils.TextUtils.isClosingBracket(c)) {
            if (!stack.isEmpty() && stack.peek().bracket == com.yn.sodiumeditor.utils.TextUtils.matchingBracket(c)) {
              BracketToken open = stack.pop();
              if (line == editor.cursor.cursorLine && i == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
              if (open.line == editor.cursor.cursorLine && open.ch == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
            }
          }
        }

        i++;
      }
    }
    return new BracketMatch(editor.cursor.cursorLine, targetIndex, editor.cursor.cursorLine, targetIndex);
  }

  /**
   * Finds bracket match in the ENTIRE document.
   * This ensures matching works even when the matching bracket is outside the visible range.
   */
  public BracketMatch findBracketMatchInDocument() {
    FunctionLog.f("BracketMatchManager", "findBracketMatchInDocument");
    if (!isBracketMatchingEnabled) return null;

    int totalLines = editor.view.getLinesCount();
    if (totalLines == 0) return null;

    // Use BracketCache if available and ready - much faster
    if (editor.bracketCache != null && !editor.bracketCache.isScanning()) {
      int cursorLine = editor.cursor.cursorLine;
      int cursorChar = editor.cursor.cursorChar;
      
      // Look for bracket at or before cursor
      BracketCache.LineBracketInfo info = editor.bracketCache.getLineInfo(cursorLine);
      BracketCache.BracketPosition targetBp = null;
      
      for (BracketCache.BracketPosition bp : info.brackets) {
        if (bp.column == cursorChar || bp.column == cursorChar - 1) {
          targetBp = bp;
          break;
        }
      }
      
      if (targetBp != null) {
        BracketCache.BracketPosition match = editor.bracketCache.findMatchingBracket(targetBp);
        if (match != null) {
          return (targetBp.isOpening) ?
              new BracketMatch(targetBp.line, targetBp.column, match.line, match.column) :
              new BracketMatch(match.line, match.column, targetBp.line, targetBp.column);
        }
      }
    }

    // Limit search to 5000 lines around cursor if synchronously scanning
    int maxSearchLines = 5000;
    if (totalLines > maxSearchLines) {
      // For very large files, only scan a limited range synchronously
      int start = Math.max(0, editor.cursor.cursorLine - (maxSearchLines / 2));
      int end = Math.min(totalLines - 1, start + maxSearchLines);
      return findBracketMatchInRange(start, end);
    }

    return findBracketMatchInRange(0, totalLines - 1);
  }

  private BracketMatch findBracketMatchInRange(int startLine, int endLine) {
    FunctionLog.f("BracketMatchManager", "findBracketMatchInRange", startLine, endLine);
    String cursorLineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (cursorLineText == null) return null;

    int targetIndex = -1;
    char targetChar = 0;
    if (editor.cursor.cursorChar > 0 && editor.cursor.cursorChar - 1 < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar - 1);
      if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar - 1;
        targetChar = c;
      }
    }
    if (targetIndex < 0 && editor.cursor.cursorChar < cursorLineText.length()) {
      char c = cursorLineText.charAt(editor.cursor.cursorChar);
      if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c)) {
        targetIndex = editor.cursor.cursorChar;
        targetChar = c;
      }
    }
    if (targetIndex < 0) return null;

    // Get syntax state at start of range (approximated if not line 0)
    com.yn.sodiumeditor.renderer.HighliteRender.HighlightLineState startState = editor.highlite.getLineStateAtStart(startLine);
    boolean inBlockComment = startState.inBlockComment && editor.highlite.isBlockCommentsEnabled;
    int stringState = startState.stringState;

    ArrayDeque<BracketToken> stack = new ArrayDeque<>();
    for (int line = startLine; line <= endLine; line++) {
      String text = editor.windowRender.getLineTextForRender(line);
      if (text == null) text = "";
      int len = text.length();
      int i = 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;
        if (inBlockComment) {
          int end = Highlite.findBlockCommentEnd(text, i);
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < (end < 0 ? len : end + 2)) return null;
          if (end < 0) break;
          i = end + 2; inBlockComment = false; continue;
        }
        if (stringState != 0) {
          com.yn.sodiumeditor.core.StringEndResult endResult =
              editor.highlite.findStringEndForState(text, i, stringState);
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < (endResult.found ? endResult.endIndex : len)) return null;
          if (!endResult.found) break;
          i = endResult.endIndex; stringState = 0; continue;
        }
        if (editor.highlite.isLineCommentStart(text, i)) {
          if (line == editor.cursor.cursorLine && targetIndex >= i) return null;
          break;
        }
        if (editor.highlite.isBlockCommentsEnabled && i + 1 < len && text.charAt(i) == '/' && text.charAt(i + 1) == '*' && !Highlite.isTokenEscaped(text, i)) {
          int end = Highlite.findBlockCommentEnd(text, i + 2);
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < (end < 0 ? len : end + 2)) return null;
          if (end < 0) { inBlockComment = true; break; }
          i = end + 2; continue;
        }
        if (editor.highlite.isTripleQuoteStart(text, i) && !Highlite.isEscaped(text, i)) {
          int end = Highlite.findTripleQuoteEnd(text, i + 3);
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < (end >= 0 ? end + 3 : len)) return null;
          if (end < 0) { if (editor.highlite.isTripleQuoteStringsEnabled) stringState = com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE; break; }
          i = end + 3; continue;
        }
        char c = text.charAt(i);
        if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(text, i)) {
          int end = Highlite.findStringEnd(text, i + 1, c);
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < (end >= 0 ? end + 1 : len)) return null;
          if (end < 0) { if (editor.highlite.isMultiLineStringsEnabled) stringState = editor.highlite.getStringStateForDelimiter(c); break; }
          i = end + 1; continue;
        }
        if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c) && !Highlite.isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (com.yn.sodiumeditor.utils.TextUtils.isOpeningBracket(c)) {
            stack.push(token);
          } else if (com.yn.sodiumeditor.utils.TextUtils.isClosingBracket(c)) {
            if (!stack.isEmpty() && stack.peek().bracket == com.yn.sodiumeditor.utils.TextUtils.matchingBracket(c)) {
              BracketToken open = stack.pop();
              if ((line == editor.cursor.cursorLine && i == targetIndex) || (open.line == editor.cursor.cursorLine && open.ch == targetIndex)) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
            }
          }
        }
        i++;
      }
    }
    return new BracketMatch(editor.cursor.cursorLine, targetIndex, editor.cursor.cursorLine, targetIndex);
  }

  /**
   * Draws bracket match for a line.
   */
  public void drawBracketMatchForLine(
      Canvas canvas, String line, int globalLine, BracketMatch match) {
    FunctionLog.f("BracketMatchManager", "drawBracketMatchForLine", canvas, line, globalLine, match);
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
   * Draws bracket match for a line segment (wrapped mode).
   */
  public void drawBracketMatchForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float segBaseX,
      float top, BracketMatch match) {
    FunctionLog.f("BracketMatchManager", "drawBracketMatchForSegment", canvas, line, globalLine, segStart, segEnd, segBaseX, top, match);
    if (match == null) return;
    if (globalLine != match.openLine && globalLine != match.closeLine) return;
    if (line == null || line.isEmpty()) return;

    if (match.openLine == match.closeLine) {
      if (match.openChar == match.closeChar) {
        if (match.openChar >= segStart && match.openChar < segEnd) {
          drawBracketBoxSegment(canvas, line, globalLine, segStart, segEnd, segBaseX, top, match.openChar);
        }
        return;
      }

      if (globalLine == match.openLine) {
        if (match.openChar >= segStart && match.openChar < segEnd) {
          drawBracketBoxSegment(canvas, line, globalLine, segStart, segEnd, segBaseX, top, match.openChar);
        }
        if (match.closeChar >= segStart && match.closeChar < segEnd) {
          drawBracketBoxSegment(canvas, line, globalLine, segStart, segEnd, segBaseX, top, match.closeChar);
        }
      }
      return;
    }

    int charIdx = (globalLine == match.openLine) ? match.openChar : match.closeChar;
    if (charIdx >= segStart && charIdx < segEnd) {
      drawBracketBoxSegment(canvas, line, globalLine, segStart, segEnd, segBaseX, top, charIdx);
    }
  }

  /**
   * Draws bracket box for a single character in a segment.
   */
  public void drawBracketBoxSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float segBaseX,
      float top, int index) {
    FunctionLog.f("BracketMatchManager", "drawBracketBoxSegment", canvas, line, globalLine, segStart, segEnd, segBaseX, top, index);
    if (index < 0 || index >= line.length()) return;

    float left = editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, index);
    float right = editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, index + 1);
    
    // Adjust for RTL where getCaretXForSegment includes segBaseX
    if (editor.textRender.isRtl) {
      left -= segBaseX;
      right -= segBaseX;
    }

    drawBracketBoxRectAtY(canvas, top, left, right);
  }

  /**
   * Draws bracket box for a single character.
   */
  public void drawBracketBox(Canvas canvas, String line, int globalLine, int index) {
    FunctionLog.f("BracketMatchManager", "drawBracketBox", canvas, line, globalLine, index);
    if (index < 0 || index >= line.length()) return;

    float left = editor.textRender.measureText(line, index, globalLine);
    float right = editor.textRender.measureText(line, index + 1, globalLine);
    if (right <= left) right = left + editor.textRender.measureTextWithVisualSpaces(line, index, index + 1, editor.textRender.paint);

    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  /**
   * Draws bracket box for a range of characters.
   */
  public void drawBracketBoxRange(
      Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
    FunctionLog.f("BracketMatchManager", "drawBracketBoxRange", canvas, line, globalLine, startIndex, endIndex);
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    if (endIndex < startIndex) return;

    float left = editor.textRender.measureText(line, startIndex, globalLine);
    float right = editor.textRender.measureText(line, endIndex + 1, globalLine);
    if (right <= left)
      right = left + editor.textRender.measureTextWithVisualSpaces(line, startIndex, endIndex + 1, editor.textRender.paint);
    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  /**
   * Draws bracket box rectangle.
   */
  public void drawBracketBoxRect(Canvas canvas, int globalLine, float left, float right) {
    FunctionLog.f("BracketMatchManager", "drawBracketBoxRect", canvas, globalLine, left, right);
    final float padding = 1f;
    final float top = editor.textRender.getDrawLineTop(globalLine) + padding;
    drawBracketBoxRectAtY(canvas, top, left, right);
  }

  /**
   * Draws bracket box rectangle at specific Y position.
   */
  public void drawBracketBoxRectAtY(Canvas canvas, float top, float left, float right) {
    FunctionLog.f("BracketMatchManager", "drawBracketBoxRectAtY", canvas, top, left, right);
    final float padding = 1f;
    final float bottom = top + editor.textRender.lineHeight - (padding * 2f);

    float l = left - padding;
    float r = right + padding;
    if (r <= l) return;

    bracketMatchRect.set(l, top, r, bottom);
    float radius = Math.max(2f, bracketMatchStrokeWidth + 1f);
    canvas.drawRoundRect(bracketMatchRect, radius, radius, bracketMatchPaint);
  }
}
