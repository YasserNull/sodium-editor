package com.yn.sodiumeditor.core.guides;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.highlight.Highlight;
import com.yn.sodiumeditor.core.guides.bracket.BracketCache;
import com.yn.sodiumeditor.core.guides.bracket.BracketToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Manages regex-backed symbol matching for the SodiumEditor. */
public class SymbolsMatch {

  private static final SymbolsMatchSet BRACE_MATCH = new SymbolsMatchSet("\\{", "\\}");
  private static final SymbolsMatchSet PAREN_MATCH = new SymbolsMatchSet("\\(", "\\)");
  private static final SymbolsMatchSet BRACKET_MATCH = new SymbolsMatchSet("\\[", "\\]");
  private static final SymbolsMatchSet DOUBLE_QUOTE_MATCH = new SymbolsMatchSet("\"", "\"");
  private static final SymbolsMatchSet SINGLE_QUOTE_MATCH = new SymbolsMatchSet("'", "'");
  private static final SymbolsMatchSet BACKTICK_MATCH = new SymbolsMatchSet("`", "`");
  private static final SymbolsMatchSet JAVA_BLOCK_COMMENT_MATCH =
      new SymbolsMatchSet("/\\*", "\\*/");

  private final SodiumEditor editor;

  public boolean BracketsMatch = true;
  public boolean StringsMatch = true; // ' and "
  public boolean TupleStringsMatch = true; // `
  public boolean JavaCommentsMatch = true; // like that /**/
  public final List<SymbolsMatchSet> customSymbolsMatchSets = new ArrayList<>();

  // Symbols matching state
  public boolean isSymbolsMatchingEnabled = true;
  public final Paint symbolsMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float symbolsMatchStrokeWidth = 3f;
  public float baseSymbolsMatchStrokeWidth = symbolsMatchStrokeWidth;
  public float baseSymbolsMatchTextSizePx = 0f;
  public final RectF symbolsMatchRect = new RectF();
  public int symbolsMatchColor = 0x302196F3; // Opaque Yellow
  // Symbols match cache
  @Nullable public SymbolsMatchRange cachedSymbolsMatch = null;
  public int cachedSymbolsMatchCursorLine = -1;
  public int cachedSymbolsMatchCursorChar = -1;
  public int cachedSymbolsMatchEditVersion = -1;

  public static final class SymbolsMatchSet {
    public final String regexStart;
    public final String regexEnd;
    private final Pattern startPattern;
    private final Pattern endPattern;
    private final boolean samePattern;

    public SymbolsMatchSet(String regexStart, String regexEnd) {
      if (regexStart == null || regexStart.isEmpty()) {
        throw new IllegalArgumentException("regexStart must not be empty");
      }
      if (regexEnd == null || regexEnd.isEmpty()) {
        throw new IllegalArgumentException("regexEnd must not be empty");
      }
      this.regexStart = regexStart;
      this.regexEnd = regexEnd;
      this.startPattern = Pattern.compile(regexStart);
      this.endPattern = Pattern.compile(regexEnd);
      this.samePattern = regexStart.equals(regexEnd);
    }

    private int startLength(String text, int index) {
      if (isJavaBlockCommentStart() && index > 0 && text.charAt(index - 1) == '/') {
        return 0;
      }
      return matchLength(startPattern, text, index);
    }

    private boolean isJavaBlockCommentStart() {
      return regexStart.equals("/\\*")
          || regexStart.equals("/[*]")
          || regexStart.equals("/\\Q*\\E");
    }

    private int endLength(String text, int index) {
      return matchLength(endPattern, text, index);
    }

    private static int matchLength(Pattern pattern, String text, int index) {
      Matcher matcher = pattern.matcher(text);
      matcher.region(index, text.length());
      if (!matcher.lookingAt()) return 0;
      int end = matcher.end();
      return end > index ? end - index : 0;
    }
  }

  public static final class SymbolsMatchResult {
    public final int openLine;
    public final int openChar;
    public final int closeLine;
    public final int closeChar;
    public final int openLength;
    public final int closeLength;

    public SymbolsMatchResult(
        int openLine,
        int openChar,
        int closeLine,
        int closeChar,
        int openLength,
        int closeLength) {
      this.openLine = openLine;
      this.openChar = openChar;
      this.closeLine = closeLine;
      this.closeChar = closeChar;
      this.openLength = Math.max(1, openLength);
      this.closeLength = Math.max(1, closeLength);
    }
  }

  private static final class SymbolToken {
    final int line;
    final int ch;
    final int length;
    final SymbolsMatchSet set;
    final boolean opening;

    SymbolToken(int line, int ch, int length, SymbolsMatchSet set, boolean opening) {
      this.line = line;
      this.ch = ch;
      this.length = length;
      this.set = set;
      this.opening = opening;
    }

    boolean touchesCursor(int cursorLine, int cursorChar) {
      return line == cursorLine && cursorChar >= ch && cursorChar <= ch + length;
    }
  }

  public SymbolsMatch(SodiumEditor editor) {
    this.editor = editor;
    symbolsMatchPaint.setColor(symbolsMatchColor);
    symbolsMatchPaint.setStyle(Paint.Style.STROKE);
    symbolsMatchPaint.setStrokeWidth(symbolsMatchStrokeWidth);
  }

  public static SymbolsMatchResult findMatchInLines(
      List<String> lines, int cursorLine, int cursorChar, List<SymbolsMatchSet> sets) {
    if (lines == null || sets == null || sets.isEmpty()) return null;
    if (cursorLine < 0 || cursorLine >= lines.size()) return null;

    ArrayDeque<SymbolToken> stack = new ArrayDeque<>();
    for (int line = 0; line < lines.size(); line++) {
      String text = lines.get(line);
      if (text == null) text = "";
      for (int i = 0; i < text.length(); ) {
        SymbolToken token = findTokenAt(text, line, i, sets, stack);
        if (token == null) {
          i++;
          continue;
        }

        SymbolToken open = null;
        SymbolToken close = null;
        if (token.opening) {
          stack.push(token);
        } else if (!stack.isEmpty() && stack.peek().set == token.set) {
          open = stack.pop();
          close = token;
        }

        if (open != null
            && (open.touchesCursor(cursorLine, cursorChar)
                || close.touchesCursor(cursorLine, cursorChar))) {
          return new SymbolsMatchResult(
              open.line, open.ch, close.line, close.ch, open.length, close.length);
        }
        i += token.length;
      }
    }
    return null;
  }

  private static SymbolToken findTokenAt(
      String text,
      int line,
      int index,
      List<SymbolsMatchSet> sets,
      ArrayDeque<SymbolToken> stack) {
    SymbolToken best = null;
    for (SymbolsMatchSet set : sets) {
      int startLength = set.startLength(text, index);
      int endLength = set.endLength(text, index);
      if (set.samePattern && startLength > 0) {
        boolean opening = stack.isEmpty() || stack.peek().set != set;
        best = longer(best, new SymbolToken(line, index, startLength, set, opening));
        continue;
      }
      if (endLength > 0) {
        best = longer(best, new SymbolToken(line, index, endLength, set, false));
      }
      if (startLength > 0) {
        best = longer(best, new SymbolToken(line, index, startLength, set, true));
      }
    }
    return best;
  }

  private static SymbolToken longer(SymbolToken current, SymbolToken candidate) {
    if (current == null) return candidate;
    return candidate.length > current.length ? candidate : current;
  }

  public List<SymbolsMatchSet> getEnabledSymbolsMatchSets() {
    ArrayList<SymbolsMatchSet> sets = new ArrayList<>();
    if (BracketsMatch) {
      sets.add(BRACE_MATCH);
      sets.add(PAREN_MATCH);
      sets.add(BRACKET_MATCH);
    }
    if (StringsMatch) {
      sets.add(DOUBLE_QUOTE_MATCH);
      sets.add(SINGLE_QUOTE_MATCH);
    }
    if (TupleStringsMatch) {
      sets.add(BACKTICK_MATCH);
    }
    if (JavaCommentsMatch) {
      sets.add(JAVA_BLOCK_COMMENT_MATCH);
    }
    sets.addAll(customSymbolsMatchSets);
    return Collections.unmodifiableList(sets);
  }

  public void addSymbolsMatchSet(SymbolsMatchSet set) {
    if (set == null) return;
    customSymbolsMatchSets.add(set);
    clearSymbolsMatchCache();
    editor.invalidate();
  }

  public void addSymbolsMatchSet(String regexStart, String regexEnd) {
    addSymbolsMatchSet(new SymbolsMatchSet(regexStart, regexEnd));
  }

  public void clearCustomSymbolsMatchSets() {
    if (customSymbolsMatchSets.isEmpty()) return;
    customSymbolsMatchSets.clear();
    clearSymbolsMatchCache();
    editor.invalidate();
  }

  /** Enables or disables symbols matching. */
  public void setSymbolsMatchingEnabled(boolean enabled) {
    if (this.isSymbolsMatchingEnabled == enabled) return;
    this.isSymbolsMatchingEnabled = enabled;
    if (enabled) editor.bracketCache.ensureScannedAsync();
    clearSymbolsMatchCache();
    editor.invalidate();
  }

  /** Sets the symbols match color. */
  public void setSymbolsMatchColor(int color) {
    symbolsMatchColor = color;
    symbolsMatchPaint.setColor(color);
    editor.invalidate();
  }

  /** Sets the symbols match stroke width. */
  public void setSymbolsMatchStrokeWidth(float width) {
    if (this.symbolsMatchStrokeWidth == width) return;
    this.baseSymbolsMatchStrokeWidth = width;
    this.baseSymbolsMatchTextSizePx = editor.textRender.paint.getTextSize();
    updateStrokeWidth();
    editor.invalidate();
  }

  /** Updates stroke width based on text size. */
  public void updateStrokeWidth() {
    float sizePx = editor.textRender.paint.getTextSize();
    symbolsMatchStrokeWidth =
        Math.max(
            1f,
            editor.view.scaleByTextSize(
                baseSymbolsMatchStrokeWidth, baseSymbolsMatchTextSizePx, sizePx));
    symbolsMatchPaint.setStrokeWidth(symbolsMatchStrokeWidth);
  }

  /** Clears the symbols match cache. */
  public void clearSymbolsMatchCache() {
    cachedSymbolsMatch = null;
    cachedSymbolsMatchCursorLine = -1;
    cachedSymbolsMatchCursorChar = -1;
    cachedSymbolsMatchEditVersion = -1;
  }

  /**
   * Finds and caches symbols match for the current cursor position. Searches the configured range to
   * find matching symbols.
   */
  public SymbolsMatchRange findAndCacheSymbolsMatch(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!isSymbolsMatchingEnabled) return null;

    int v = editor.editOperators.editVersion.get();
    if (cachedSymbolsMatch != null
        && cachedSymbolsMatchCursorLine == editor.cursor.cursorLine
        && cachedSymbolsMatchCursorChar == editor.cursor.cursorChar
        && cachedSymbolsMatchEditVersion == v) {
      return cachedSymbolsMatch;
    }

    // Search entire document for matching symbols, not just visible range.
    SymbolsMatchRange match = findSymbolsMatchInDocument();
    if (match != null) {
      cachedSymbolsMatch = match;
      cachedSymbolsMatchCursorLine = editor.cursor.cursorLine;
      cachedSymbolsMatchCursorChar = editor.cursor.cursorChar;
      cachedSymbolsMatchEditVersion = v;
    }
    return match;
  }

  /** Finds symbols match in visible range. */
  public SymbolsMatchRange findSymbolsMatchInVisible(
      int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (!isSymbolsMatchingEnabled) {
      return null;
    }
    if (editor.cursor.cursorLine < firstVisibleLine || editor.cursor.cursorLine > lastVisibleLine) {
      return null;
    }

    if (isCursorOnConfiguredNonBracketSymbol(directLines)) {
      return findSymbolsMatchInRange(firstVisibleLine, lastVisibleLine, directLines);
    }

    String cursorLineText =
        editor.windowRender.getLineTextForRenderWithDirect(editor.cursor.cursorLine, directLines);
    if (cursorLineText == null) {
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
      return null;
    }

    com.yn.sodiumeditor.renderer.HighlightRender.HighlightLineState startState =
        editor.highlight.getLineStateAtStart(firstVisibleLine);
    boolean inBlockComment = startState.inBlockComment && editor.highlight.isBlockCommentsEnabled;
    int stringState = startState.stringState;
    if (!editor.highlight.isBlockCommentsEnabled) inBlockComment = false;
    if (!editor.highlight.isMultiLineStringsEnabled
        && stringState != com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE)
      stringState = 0;
    if (!editor.highlight.isBacktickStringsEnabled
        && stringState == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_BACKTICK)
      stringState = 0;
    if (!editor.highlight.isTripleQuoteStringsEnabled
        && stringState == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE)
      stringState = 0;

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
          int end = Highlight.findBlockCommentEnd(text, i);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos)
            return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          com.yn.sodiumeditor.core.StringEndResult endResult =
              editor.highlight.findStringEndForState(text, i, stringState);
          int endPos = endResult.found ? endResult.endIndex : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos)
            return null;
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (editor.highlight.isLineCommentStart(text, i)) {
          if (line == editor.cursor.cursorLine && targetIndex >= i) return null;
          inLineComment = true;
          break;
        }

        if (editor.highlight.isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !Highlight.isTokenEscaped(text, i)) {
          int end = Highlight.findBlockCommentEnd(text, i + 2);
          int endPos = (end < 0) ? len : end + 2;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos)
            return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (editor.highlight.isTripleQuoteStart(text, i) && !Highlight.isEscaped(text, i)) {
          int end = Highlight.findTripleQuoteEnd(text, i + 3);
          int endPos = end >= 0 ? end + 3 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos)
            return null;
          if (end < 0) {
            if (editor.highlight.isTripleQuoteStringsEnabled) {
              stringState = com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE;
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (editor.highlight.isStringDelimiter(c) && !Highlight.isEscaped(text, i)) {
          int end = Highlight.findStringEnd(text, i + 1, c);
          int endPos = end >= 0 ? end + 1 : len;
          if (line == editor.cursor.cursorLine && targetIndex >= i && targetIndex < endPos)
            return null;
          if (end < 0) {
            if (editor.highlight.isMultiLineStringsEnabled) {
              stringState = editor.highlight.getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c) && !Highlight.isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (com.yn.sodiumeditor.utils.TextUtils.isOpeningBracket(c)) {
            stack.push(token);
          } else if (com.yn.sodiumeditor.utils.TextUtils.isClosingBracket(c)) {
            if (!stack.isEmpty()
                && stack.peek().bracket == com.yn.sodiumeditor.utils.TextUtils.matchingBracket(c)) {
              BracketToken open = stack.pop();
              if (line == editor.cursor.cursorLine && i == targetIndex) {
                return new SymbolsMatchRange(open.line, open.ch, line, i);
              }
              if (open.line == editor.cursor.cursorLine && open.ch == targetIndex) {
                return new SymbolsMatchRange(open.line, open.ch, line, i);
              }
            }
          }
        }

        i++;
      }
    }
    return new SymbolsMatchRange(
        editor.cursor.cursorLine, targetIndex, editor.cursor.cursorLine, targetIndex);
  }

  /**
   * Finds symbols match in the ENTIRE document. This ensures matching works even when the matching
   * symbol is outside the visible range.
   */
  public SymbolsMatchRange findSymbolsMatchInDocument() {
    if (!isSymbolsMatchingEnabled) return null;

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
          return (targetBp.isOpening)
              ? new SymbolsMatchRange(targetBp.line, targetBp.column, match.line, match.column)
              : new SymbolsMatchRange(match.line, match.column, targetBp.line, targetBp.column);
        }
      }
    }

    // Limit search to 5000 lines around cursor if synchronously scanning
    int maxSearchLines = 5000;
    if (totalLines > maxSearchLines) {
      // For very large files, only scan a limited range synchronously
      int start = Math.max(0, editor.cursor.cursorLine - (maxSearchLines / 2));
      int end = Math.min(totalLines - 1, start + maxSearchLines);
      return findSyntaxAwareSymbolsMatchInRange(start, end);
    }

    return findSyntaxAwareSymbolsMatchInRange(0, totalLines - 1);
  }

  private SymbolsMatchRange findSyntaxAwareSymbolsMatchInRange(int startLine, int endLine) {
    if (isCursorOnConfiguredNonBracketSymbol(null)) {
      return findSymbolsMatchInRange(startLine, endLine, null);
    }

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
    com.yn.sodiumeditor.renderer.HighlightRender.HighlightLineState startState =
        editor.highlight.getLineStateAtStart(startLine);
    boolean inBlockComment = startState.inBlockComment && editor.highlight.isBlockCommentsEnabled;
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
          int end = Highlight.findBlockCommentEnd(text, i);
          if (line == editor.cursor.cursorLine
              && targetIndex >= i
              && targetIndex < (end < 0 ? len : end + 2)) return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }
        if (stringState != 0) {
          com.yn.sodiumeditor.core.StringEndResult endResult =
              editor.highlight.findStringEndForState(text, i, stringState);
          if (line == editor.cursor.cursorLine
              && targetIndex >= i
              && targetIndex < (endResult.found ? endResult.endIndex : len)) return null;
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }
        if (editor.highlight.isLineCommentStart(text, i)) {
          if (line == editor.cursor.cursorLine && targetIndex >= i) return null;
          break;
        }
        if (editor.highlight.isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !Highlight.isTokenEscaped(text, i)) {
          int end = Highlight.findBlockCommentEnd(text, i + 2);
          if (line == editor.cursor.cursorLine
              && targetIndex >= i
              && targetIndex < (end < 0 ? len : end + 2)) return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }
        if (editor.highlight.isTripleQuoteStart(text, i) && !Highlight.isEscaped(text, i)) {
          int end = Highlight.findTripleQuoteEnd(text, i + 3);
          if (line == editor.cursor.cursorLine
              && targetIndex >= i
              && targetIndex < (end >= 0 ? end + 3 : len)) return null;
          if (end < 0) {
            if (editor.highlight.isTripleQuoteStringsEnabled)
              stringState = com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE;
            break;
          }
          i = end + 3;
          continue;
        }
        char c = text.charAt(i);
        if (editor.highlight.isStringDelimiter(c) && !Highlight.isEscaped(text, i)) {
          int end = Highlight.findStringEnd(text, i + 1, c);
          if (line == editor.cursor.cursorLine
              && targetIndex >= i
              && targetIndex < (end >= 0 ? end + 1 : len)) return null;
          if (end < 0) {
            if (editor.highlight.isMultiLineStringsEnabled)
              stringState = editor.highlight.getStringStateForDelimiter(c);
            break;
          }
          i = end + 1;
          continue;
        }
        if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(c) && !Highlight.isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (com.yn.sodiumeditor.utils.TextUtils.isOpeningBracket(c)) {
            stack.push(token);
          } else if (com.yn.sodiumeditor.utils.TextUtils.isClosingBracket(c)) {
            if (!stack.isEmpty()
                && stack.peek().bracket == com.yn.sodiumeditor.utils.TextUtils.matchingBracket(c)) {
              BracketToken open = stack.pop();
              if ((line == editor.cursor.cursorLine && i == targetIndex)
                  || (open.line == editor.cursor.cursorLine && open.ch == targetIndex)) {
                return new SymbolsMatchRange(open.line, open.ch, line, i);
              }
            }
          }
        }
        i++;
      }
    }
    return new SymbolsMatchRange(
        editor.cursor.cursorLine, targetIndex, editor.cursor.cursorLine, targetIndex);
  }

  private SymbolsMatchRange findSymbolsMatchInRange(
      int startLine, int endLine, @Nullable HashMap<Integer, String> directLines) {
    List<SymbolsMatchSet> sets = getEnabledSymbolsMatchSets();
    if (sets.isEmpty()) return null;

    ArrayList<String> lines = new ArrayList<>(Math.max(0, endLine - startLine + 1));
    for (int line = startLine; line <= endLine; line++) {
      String text =
          directLines != null
              ? editor.windowRender.getLineTextForRenderWithDirect(line, directLines)
              : editor.windowRender.getLineTextForRender(line);
      lines.add(text == null ? "" : text);
    }

    SymbolsMatchResult result =
        findMatchInLines(
            lines,
            editor.cursor.cursorLine - startLine,
            editor.cursor.cursorChar,
            sets);
    return result == null
        ? null
        : new SymbolsMatchRange(
            result.openLine + startLine,
            result.openChar,
            result.closeLine + startLine,
            result.closeChar,
            result.openLength,
            result.closeLength);
  }

  private boolean isCursorOnConfiguredNonBracketSymbol(
      @Nullable HashMap<Integer, String> directLines) {
    String text =
        directLines != null
            ? editor.windowRender.getLineTextForRenderWithDirect(
                editor.cursor.cursorLine, directLines)
            : editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (text == null) return false;
    int cursor = editor.cursor.cursorChar;
    List<SymbolsMatchSet> sets = getEnabledSymbolsMatchSets();
    for (int i = Math.max(0, cursor - 4); i <= Math.min(cursor, text.length() - 1); i++) {
      SymbolToken token = findTokenAt(text, editor.cursor.cursorLine, i, sets, new ArrayDeque<>());
      if (token == null) continue;
      char first = text.charAt(token.ch);
      if (com.yn.sodiumeditor.utils.TextUtils.isBracketChar(first)) continue;
      if (token.touchesCursor(editor.cursor.cursorLine, cursor)) return true;
    }
    return false;
  }

  /** Draws symbols match for a line. */
  public void drawSymbolsMatchForLine(
      Canvas canvas, String line, int globalLine, SymbolsMatchRange match) {
    if (match == null) return;
    if (globalLine != match.openLine && globalLine != match.closeLine) return;
    if (line == null || line.isEmpty()) return;

    if (match.openLine == match.closeLine) {
      if (match.openChar == match.closeChar) {
        drawSymbolsBoxRange(
            canvas, line, globalLine, match.openChar, match.openChar + match.openLength - 1);
        return;
      }

      if (match.openChar + match.openLength == match.closeChar) {
        int leftIndex = Math.min(match.openChar, match.closeChar);
        int rightIndex =
            Math.max(
                match.openChar + match.openLength - 1,
                match.closeChar + match.closeLength - 1);
        drawSymbolsBoxRange(canvas, line, globalLine, leftIndex, rightIndex);
      } else {
        drawSymbolsBoxRange(
            canvas, line, globalLine, match.openChar, match.openChar + match.openLength - 1);
        drawSymbolsBoxRange(
            canvas, line, globalLine, match.closeChar, match.closeChar + match.closeLength - 1);
      }
      return;
    }

    int index = (globalLine == match.openLine) ? match.openChar : match.closeChar;
    int length = (globalLine == match.openLine) ? match.openLength : match.closeLength;
    drawSymbolsBoxRange(canvas, line, globalLine, index, index + length - 1);
  }

  /** Draws symbols match for a line segment (wrapped mode). */
  public void drawSymbolsMatchForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float segBaseX,
      float top,
      SymbolsMatchRange match) {
    if (match == null) return;
    if (globalLine != match.openLine && globalLine != match.closeLine) return;
    if (line == null || line.isEmpty()) return;

    if (match.openLine == match.closeLine) {
      if (match.openChar == match.closeChar) {
        if (match.openChar >= segStart && match.openChar < segEnd) {
          drawSymbolsBoxRangeSegment(
              canvas,
              line,
              globalLine,
              segStart,
              segEnd,
              segBaseX,
              top,
              match.openChar,
              match.openChar + match.openLength - 1);
        }
        return;
      }

      if (globalLine == match.openLine) {
        if (match.openChar >= segStart && match.openChar < segEnd) {
          drawSymbolsBoxRangeSegment(
              canvas,
              line,
              globalLine,
              segStart,
              segEnd,
              segBaseX,
              top,
              match.openChar,
              match.openChar + match.openLength - 1);
        }
        if (match.closeChar >= segStart && match.closeChar < segEnd) {
          drawSymbolsBoxRangeSegment(
              canvas,
              line,
              globalLine,
              segStart,
              segEnd,
              segBaseX,
              top,
              match.closeChar,
              match.closeChar + match.closeLength - 1);
        }
      }
      return;
    }

    int charIdx = (globalLine == match.openLine) ? match.openChar : match.closeChar;
    int length = (globalLine == match.openLine) ? match.openLength : match.closeLength;
    if (charIdx >= segStart && charIdx < segEnd) {
      drawSymbolsBoxRangeSegment(
          canvas, line, globalLine, segStart, segEnd, segBaseX, top, charIdx, charIdx + length - 1);
    }
  }

  /** Draws bracket box for a single character in a segment. */
  public void drawSymbolsBoxSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float segBaseX,
      float top,
      int index) {
    if (index < 0 || index >= line.length()) return;

    float left = editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, index);
    float right = editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, index + 1);

    // Adjust for RTL where getCaretXForSegment includes segBaseX
    if (editor.textRender.isRtl) {
      left -= segBaseX;
      right -= segBaseX;
    }

    drawSymbolsBoxRectAtY(canvas, top, left, right);
  }

  public void drawSymbolsBoxRangeSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float segBaseX,
      float top,
      int startIndex,
      int endIndex) {
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    startIndex = Math.max(startIndex, segStart);
    endIndex = Math.min(endIndex, segEnd - 1);
    if (endIndex < startIndex) return;

    float left = editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, startIndex);
    float right =
        editor.caret.getCaretXForSegment(line, globalLine, segStart, segEnd, endIndex + 1);

    if (editor.textRender.isRtl) {
      left -= segBaseX;
      right -= segBaseX;
    }

    drawSymbolsBoxRectAtY(canvas, top, left, right);
  }

  /** Draws bracket box for a single character. */
  public void drawSymbolsBox(Canvas canvas, String line, int globalLine, int index) {
    if (index < 0 || index >= line.length()) return;

    float left = editor.textRender.measureText(line, index, globalLine);
    float right = editor.textRender.measureText(line, index + 1, globalLine);
    if (right <= left)
      right =
          left
              + editor.textRender.measureTextWithVisualSpaces(
                  line, index, index + 1, editor.textRender.paint);

    drawSymbolsBoxRect(canvas, globalLine, left, right);
  }

  /** Draws bracket box for a range of characters. */
  public void drawSymbolsBoxRange(
      Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    if (endIndex < startIndex) return;

    float left = editor.textRender.measureText(line, startIndex, globalLine);
    float right = editor.textRender.measureText(line, endIndex + 1, globalLine);
    if (right <= left)
      right =
          left
              + editor.textRender.measureTextWithVisualSpaces(
                  line, startIndex, endIndex + 1, editor.textRender.paint);
    drawSymbolsBoxRect(canvas, globalLine, left, right);
  }

  /** Draws bracket box rectangle. */
  public void drawSymbolsBoxRect(Canvas canvas, int globalLine, float left, float right) {
    final float padding = 1f;
    final float top = editor.textRender.getDrawLineTop(globalLine) + padding;
    drawSymbolsBoxRectAtY(canvas, top, left, right);
  }

  /** Draws bracket box rectangle at specific Y position. */
  public void drawSymbolsBoxRectAtY(Canvas canvas, float top, float left, float right) {
    final float padding = 1f;
    final float bottom = top + editor.textRender.lineHeight - (padding * 2f);

    float l = left - padding;
    float r = right + padding;
    if (r <= l) return;

    symbolsMatchRect.set(l, top, r, bottom);
    float radius = Math.max(2f, symbolsMatchStrokeWidth + 1f);
    canvas.drawRoundRect(symbolsMatchRect, radius, radius, symbolsMatchPaint);
  }
}
