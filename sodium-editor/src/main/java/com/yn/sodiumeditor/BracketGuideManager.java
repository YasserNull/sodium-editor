package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BracketGuideManager {
  private final SodiumEditorView view;

  private boolean isBracketGuidesEnabled = false;
  private final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float bracketGuideStrokeWidth = 2f;
  private float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
  private float baseBracketGuideTextSizePx = 0f;

  private int bracketGuideCacheStartLine = -1;
  private int bracketGuideCacheEndLine = -1;
  private int bracketGuideCacheEditVersion = -1;
  private int bracketGuideCacheConfigHash = 0;
  private final ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow = new ArrayList<>();

  private float[] guideSeenXBuffer;
  private int guideSeenXCount = 0;

  BracketGuideManager(SodiumEditorView view) {
    this.view = view;
    bracketGuidePaint.setColor(0xFF888888);
    bracketGuidePaint.setStyle(Paint.Style.STROKE);
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
  }

  void setEnabled(boolean enabled) {
    if (isBracketGuidesEnabled == enabled) return;
    isBracketGuidesEnabled = enabled;
    invalidateCache();
  }

  public boolean isEnabled() {
    return isBracketGuidesEnabled;
  }

  void setColor(int color) {
    bracketGuidePaint.setColor(color);
  }

  void setStrokeWidth(float width) {
    if (baseBracketGuideStrokeWidth == width) return;
    baseBracketGuideStrokeWidth = width;
    baseBracketGuideTextSizePx = view.getPaintTextSizeForBracket();
    invalidateCache();
  }

  void applyScaledStrokeWidth(float scaledWidth) {
    bracketGuideStrokeWidth = scaledWidth;
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
  }

  void setBaseTextSizePx(float sizePx) {
    baseBracketGuideTextSizePx = sizePx;
  }

  float getBaseStrokeWidth() {
    return baseBracketGuideStrokeWidth;
  }

  float getBaseTextSizePx() {
    return baseBracketGuideTextSizePx;
  }

  Paint getPaint() {
    return bracketGuidePaint;
  }

  public void invalidateCache() {
    bracketGuideCacheStartLine = -1;
    bracketGuideCacheEndLine = -1;
    bracketGuideCacheEditVersion = -1;
    bracketGuideCacheConfigHash = 0;
    bracketGuideTokensWindow.clear();
  }

  public void ensureCacheForWindow(@Nullable java.util.Map<Integer, String> directLines) {
    int start = view.getWindowStartLineForBracket();
    int end = view.getWindowEndLineForBracket();
    if (start < 0 || end < start) {
      invalidateCache();
      return;
    }
    int v = view.getEditVersionForBracket();
    int cfg = getBracketGuideCacheConfigHash();
    if (start == bracketGuideCacheStartLine
        && end == bracketGuideCacheEndLine
        && v == bracketGuideCacheEditVersion
        && cfg == bracketGuideCacheConfigHash) {
      return;
    }

    HighlightManager.HighlightLineState hlState = view.highlightManager.getLineStateAtStart(start);
    BracketGuideLineState guideStart =
        new BracketGuideLineState(hlState.inBlockComment, hlState.stringState);
    boolean guideBlock = guideStart.inBlockComment && view.isBlockCommentsEnabledForBracket();
    int guideString = guideStart.stringState;
    if (!view.isBlockCommentsEnabledForBracket()) guideBlock = false;
    if (!view.isMultiLineStringsEnabledForBracket() && guideString != view.getStringStateTripleForBracket())
      guideString = 0;
    if (!view.isBacktickStringsEnabledForBracket() && guideString == view.getStringStateBacktickForBracket())
      guideString = 0;
    if (!view.isTripleQuoteStringsEnabledForBracket() && guideString == view.getStringStateTripleForBracket())
      guideString = 0;

    BracketGuideState state = new BracketGuideState(guideBlock, guideString);
    bracketGuideTokensWindow.clear();
    bracketGuideTokensWindow.ensureCapacity(end - start + 1);

    for (int line = start; line <= end; line++) {
      String text = view.getLineTextForRenderWithDirectForBracket(line, directLines);
      List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, state);
      bracketGuideTokensWindow.add(tokens);
    }

    bracketGuideCacheStartLine = start;
    bracketGuideCacheEndLine = end;
    bracketGuideCacheEditVersion = v;
    bracketGuideCacheConfigHash = cfg;
  }

  public List<BracketGuideToken> getTokensForLine(int globalLine) {
    if (!isBracketGuidesEnabled) return Collections.emptyList();
    int start = bracketGuideCacheStartLine;
    int end = bracketGuideCacheEndLine;
    if (start < 0 || globalLine < start || globalLine > end) return Collections.emptyList();
    int idx = globalLine - start;
    if (idx < 0 || idx >= bracketGuideTokensWindow.size()) return Collections.emptyList();
    List<BracketGuideToken> tokens = bracketGuideTokensWindow.get(idx);
    return (tokens != null) ? tokens : Collections.emptyList();
  }

  public void drawGuidesForLine(Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    if (!isBracketGuidesEnabled
        || view.isHeavyDrawSuppressedForBracket()
        || guideTokens == null
        || guideTokens.isEmpty()) return;
    if (line == null) line = "";
    guideSeenXCount = 0;
    float top = view.getDrawLineTopForBracket(globalLine);
    float bottom = top + view.getLineHeightForBracket();
    int firstNonSpace = getFirstNonSpaceIndex(line);
    boolean adjustTopGuideToClosingBrace = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    float closingBraceX =
        adjustTopGuideToClosingBrace ? getGuideXForColumn(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : guideTokens) {
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.x;
      tokenIndex++;
      boolean seen = false;
      for (int i = 0; i < guideSeenXCount; i++) {
        if (Math.abs(guideSeenXBuffer[i] - x) <= 0.5f) {
          seen = true;
          break;
        }
      }
      if (seen) continue;
      if (guideSeenXBuffer == null || guideSeenXBuffer.length < guideSeenXCount + 1) {
        float[] next = new float[Math.max(16, guideSeenXCount + 8)];
        if (guideSeenXBuffer != null && guideSeenXCount > 0) {
          System.arraycopy(guideSeenXBuffer, 0, next, 0, guideSeenXCount);
        }
        guideSeenXBuffer = next;
      }
      guideSeenXBuffer[guideSeenXCount++] = x;

      if (!isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuidePaint);
    }
  }

  private List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = getFirstNonSpaceIndex(line);
    List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = HighlightManager.findBlockCommentEnd(line, i);
        if (end < 0) return tokensToDraw;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        HighlightManager.StringEndResult endResult =
            view.highlightManager.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) return tokensToDraw;
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (view.highlightManager.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (view.isBlockCommentsEnabledForBracket()
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !HighlightManager.isTokenEscaped(line, i)) {
        int end = HighlightManager.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          return tokensToDraw;
        }
        i = end + 2;
        continue;
      }

      if (view.highlightManager.isTripleQuoteStart(line, i) && !HighlightManager.isEscaped(line, i)) {
        int end = HighlightManager.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (view.isTripleQuoteStringsEnabledForBracket()) {
            state.stringState = view.getStringStateTripleForBracket();
          }
          return tokensToDraw;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (view.highlightManager.isStringDelimiter(c) && !HighlightManager.isEscaped(line, i)) {
        int end = HighlightManager.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (view.isMultiLineStringsEnabledForBracket()) {
            state.stringState = view.highlightManager.getStringStateForDelimiter(c);
          }
          return tokensToDraw;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}') && !HighlightManager.isEscaped(line, i)) {
        if (c == '{') {
          int column = view.getBraceGuideColumnForLineForBracket(line, globalLine, i, firstNonSpace);
          float x = getGuideXForColumn(line, column, globalLine);
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

  private void advanceBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = getFirstNonSpaceIndex(line);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = HighlightManager.findBlockCommentEnd(line, i);
        if (end < 0) return;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        HighlightManager.StringEndResult endResult =
            view.highlightManager.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) return;
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (view.highlightManager.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (view.isBlockCommentsEnabledForBracket()
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !HighlightManager.isTokenEscaped(line, i)) {
        int end = HighlightManager.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          return;
        }
        i = end + 2;
        continue;
      }

      if (view.highlightManager.isTripleQuoteStart(line, i) && !HighlightManager.isEscaped(line, i)) {
        int end = HighlightManager.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (view.isTripleQuoteStringsEnabledForBracket()) {
            state.stringState = view.getStringStateTripleForBracket();
          }
          return;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (view.highlightManager.isStringDelimiter(c) && !HighlightManager.isEscaped(line, i)) {
        int end = HighlightManager.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (view.isMultiLineStringsEnabledForBracket()) {
            state.stringState = view.highlightManager.getStringStateForDelimiter(c);
          }
          return;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}') && !HighlightManager.isEscaped(line, i)) {
        if (c == '{') {
          int column = view.getBraceGuideColumnForLineForBracket(line, globalLine, i, firstNonSpace);
          float x = getGuideXForColumn(line, column, globalLine);
          state.stack.push(new BracketGuideToken(column, x));
        } else if (c == '}') {
          if (!state.stack.isEmpty()) {
            state.stack.pop();
          }
        }
      }

      i++;
    }
  }

  private static List<BracketGuideToken> getGuideTokensFromStack(ArrayDeque<BracketGuideToken> stack) {
    List<BracketGuideToken> tokens = new ArrayList<>();
    for (BracketGuideToken token : stack) {
      tokens.add(token);
    }
    return tokens;
  }

  private int getBracketGuideCacheConfigHash() {
    int h = 17;
    h = 31 * h + (view.isBlockCommentsEnabledForBracket() ? 1 : 0);
    h = 31 * h + (view.isMultiLineStringsEnabledForBracket() ? 1 : 0);
    h = 31 * h + (view.isBacktickStringsEnabledForBracket() ? 1 : 0);
    h = 31 * h + (view.isTripleQuoteStringsEnabledForBracket() ? 1 : 0);
    List<String> delimiters = view.highlightManager.lineCommentDelimiters;
    for (int i = 0; i < delimiters.size(); i++) {
      h = 31 * h + delimiters.get(i).hashCode();
    }
    h = 31 * h + (view.isWhitespaceGuidesEnabledForBracket() ? 1 : 0);
    h = 31 * h + view.getWhitespaceGuideSpaceStepForBracket();
    h = 31 * h + Float.floatToIntBits(view.getPaintTextSizeForBracket());
    h = 31 * h + (view.isRtlForBracket() ? 1 : 0);
    return h;
  }

  private float getGuideXForColumn(String line, int column, int globalLine) {
    if (line == null) line = "";
    if (column <= line.length()) {
      return view.highlightManager.measureText(line, column, globalLine);
    }
    float base = view.highlightManager.measureText(line, line.length(), globalLine);
    float spaceWidth = view.whitespaceGuideManager.getVisualSpaceWidth(view.paint);
    return base + spaceWidth * (column - line.length());
  }

  private boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    List<HighlightManager.HighlightSpan> spans =
        view.highlightManager.highlightCache.get(globalLine);
    if (spans == null) {
      spans = view.highlightManager.calculateSpansForLine(line, globalLine);
      view.highlightManager.highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    boolean prevWhitespace = false;
    final float eps = 0.25f;

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighlightManager.HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          for (int i = pos; i < Math.min(span.start, len); i++) {
            float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
            if (x >= currentX - eps && x <= currentX + adv + eps) {
              return Character.isWhitespace(line.charAt(i));
            }
            currentX += adv;
          }
        }
        int start = Math.max(pos, span.start);
        int end = Math.min(len, span.end);
        for (int i = start; i < end; i++) {
          float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
          if (x >= currentX - eps && x <= currentX + adv + eps) {
            return Character.isWhitespace(line.charAt(i));
          }
          currentX += adv;
        }
        pos = Math.max(pos, end);
      }
    }

    if (pos < len) {
      for (int i = pos; i < len; i++) {
        float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
        if (x >= currentX - eps && x <= currentX + adv + eps) {
          return Character.isWhitespace(line.charAt(i));
        }
        currentX += adv;
      }
    }

    return prevWhitespace;
  }

  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }

  private static final class BracketGuideState {
    boolean inBlockComment;
    int stringState;
    final ArrayDeque<BracketGuideToken> stack = new ArrayDeque<>();

    BracketGuideState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public static final class BracketGuideToken {
    public final int column;
    public final float x;

    public BracketGuideToken(int column, float x) {
      this.column = column;
      this.x = x;
    }
  }

  static final class BracketGuideLineState {
    final boolean inBlockComment;
    final int stringState;

    BracketGuideLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public void setBracketGuidesEnabled(SodiumEditorView view, boolean enabled) {
    this.setEnabled(enabled);
    view.invalidate();
  }

  public void setBracketGuidesColor(SodiumEditorView view, int color) {
    this.setColor(color);
    view.invalidate();
  }

  public void setBracketGuidesStrokeWidth(SodiumEditorView view, float width) {
    this.setStrokeWidth(width);
    view.updateTextSizeDependentMetrics();
    view.invalidate();
  }
}
