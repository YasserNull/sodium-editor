package com.yn.sodiumeditor.core; 
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.renderer.HighliteRender;

/**
 * Manages syntax highlighting for the SodiumEditor.
 * Handles highlight rules, caches, and span calculation.
 */
public class Highlite {

  private final SodiumEditor editor;

  // Highlight rules
  public final List<HighliteRender.HighlightRule> highlightRules = new ArrayList<>();
  public HighliteRender.HighlightRule stringHighlightRule;
  public HighliteRender.HighlightRule blockCommentHighlightRule;
  public final ArrayList<HighliteRender.HighlightRule> regexHighlightRules = new ArrayList<>();
  @Nullable public HighliteRender.HighlightRule lineCommentHighlightRule;
  public HighliteRender.HighlightRule whitespaceStringRule;
  public HighliteRender.HighlightRule whitespaceCommentRule;

  // Highlight caches
  public final LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<HighliteRender.HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  // Line comment delimiters
  public final ArrayList<String> lineCommentDelimiters = new ArrayList<>();

  // String highlighting options
  public boolean isMultiLineStringsEnabled = false;
  public boolean isTripleQuoteStringsEnabled = false;
  public boolean isBacktickStringsEnabled = false;
  public boolean isBlockCommentsEnabled = false;

  // String state constants
  public static final int STRING_STATE_DOUBLE = 1;
  public static final int STRING_STATE_SINGLE = 2;
  public static final int STRING_STATE_BACKTICK = 3;
  public static final int STRING_STATE_TRIPLE = 4;
  
  public Highlite(SodiumEditor editor) {
    this.editor = editor;
    initWhitespaceRules();
  }

  private void initWhitespaceRules() {
    whitespaceStringRule =
        new HighliteRender.HighlightRule(
            "",
            SodiumEditor.STYLE_NORMAL,
            0xFF000000,
            editor.textRender.paint.getTextSize(),
            editor.textRender.paint.getTypeface(),
            false,
            HighliteRender.HighlightRuleType.STRING);
    whitespaceCommentRule =
        new HighliteRender.HighlightRule(
            "",
            SodiumEditor.STYLE_NORMAL,
            0xFF000000,
            editor.textRender.paint.getTextSize(),
            editor.textRender.paint.getTypeface(),
            false,
            HighliteRender.HighlightRuleType.BLOCK_COMMENT);
  }

  /**
   * Adds a highlight rule.
   */
  public void addHighlightRule(String regex, int style, int color) {
    addHighlightRule(regex, style, color, false);
  }

  /**
   * Adds a highlight rule with underline option.
   */
  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    HighliteRender.HighlightRuleType type = HighliteRender.HighlightRuleType.REGEX;
    if (regex.equals(Highlite.RULE_STRING)) {
      type = HighliteRender.HighlightRuleType.STRING;
    } else if (regex.equals(Highlite.RULE_BLOCK_COMMENT)) {
      type = HighliteRender.HighlightRuleType.BLOCK_COMMENT;
    } else if (isLineCommentRegex(regex)) {
      type = HighliteRender.HighlightRuleType.LINE_COMMENT;
    }

    HighliteRender.HighlightRule rule =
        new HighliteRender.HighlightRule(
            regex, style, color, editor.textRender.paint.getTextSize(), editor.textRender.paint.getTypeface(), underline, type);
    if (type == HighliteRender.HighlightRuleType.LINE_COMMENT) {
      addLineCommentDelimiter(extractLineCommentDelimiter(regex));
      lineCommentHighlightRule = rule;
    } else {
      highlightRules.add(rule);
      if (type == HighliteRender.HighlightRuleType.STRING) {
        stringHighlightRule = rule;
      } else if (type == HighliteRender.HighlightRuleType.BLOCK_COMMENT) {
        blockCommentHighlightRule = rule;
      } else {
        regexHighlightRules.add(rule);
      }
    }

    clearHighlightCaches();
  }

  /**
   * Clears all highlight rules.
   */
  public void clearHighlightRules() {
    highlightRules.clear();
    stringHighlightRule = null;
    blockCommentHighlightRule = null;
    regexHighlightRules.clear();
    lineCommentHighlightRule = null;
    lineCommentDelimiters.clear();
    clearHighlightCaches();
  }

  /**
   * Clears highlight caches.
   */
  public void clearHighlightCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
  }

  /**
   * Invalidates highlight cache for a specific line.
   */
  public void invalidateHighlightCacheForLine(int line) {
    highlightCache.remove(line);
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("SodiumRender", "highlightInvalidate line=" + line);
    }
  }

  /**
   * Sets line comment delimiter.
   */
  public void setLineCommentDelimiter(String delimiter, int style, int color) {
    if (delimiter == null || delimiter.isEmpty()) {
      if (lineCommentHighlightRule != null) {
        lineCommentHighlightRule = null;
        clearHighlightCaches();
      }
      return;
    }

    if (lineCommentHighlightRule == null || lineCommentHighlightRule.style != style) {
      lineCommentHighlightRule =
          new HighliteRender.HighlightRule(
              "",
              style,
              color,
              editor.textRender.paint.getTextSize(),
              editor.textRender.paint.getTypeface(),
              false,
              HighliteRender.HighlightRuleType.LINE_COMMENT);
      lineCommentHighlightRule.paint.setColor(color);
      clearHighlightCaches();
      return;
    }

    if (lineCommentHighlightRule.paint.getColor() != color) {
      lineCommentHighlightRule.paint.setColor(color);
      clearHighlightCaches();
    }
  }

  /**
   * Sets string highlight color.
   */
  public void setStringHighlightColor(int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(RULE_STRING, SodiumEditor.STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
      clearHighlightCaches();
    }
  }

  /**
   * Sets block comment highlight style.
   */
  public void setBlockCommentHighlight(int style, int color) {
    if (blockCommentHighlightRule == null || blockCommentHighlightRule.style != style) {
      if (blockCommentHighlightRule != null) {
        highlightRules.remove(blockCommentHighlightRule);
      }
      blockCommentHighlightRule =
          new HighliteRender.HighlightRule(
              "",
              style,
              color,
              editor.textRender.paint.getTextSize(),
              editor.textRender.paint.getTypeface(),
              false,
              HighliteRender.HighlightRuleType.BLOCK_COMMENT);
      highlightRules.add(blockCommentHighlightRule);
    } else {
      if (blockCommentHighlightRule.paint.getColor() != color) {
        blockCommentHighlightRule.paint.setColor(color);
        clearHighlightCaches();
      }
    }
  }

  /**
   * Updates highlight rules when text size changes.
   */
  public void onTextSizeChanged(float sizePx) {
    for (HighliteRender.HighlightRule rule : highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTextSize(sizePx);
    if (whitespaceStringRule != null) whitespaceStringRule.updateTextSize(sizePx);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTextSize(sizePx);
    clearHighlightCaches();
  }

  /**
   * Updates highlight rules when typeface changes.
   */
  public void onTypefaceChanged(Typeface baseTypeface) {
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTypeface(baseTypeface);
    for (HighliteRender.HighlightRule rule : highlightRules) {
      rule.updateTypeface(baseTypeface);
    }
    clearHighlightCaches();
  }

  /**
   * Ensures highlight cache for visible range.
   */
  public void ensureHighlightCacheForVisibleRange(
      int startLine, int endLine, @Nullable Map<Integer, String> directLines) {
    if (highlightRules.isEmpty()) return;
    long startMs = android.os.SystemClock.uptimeMillis();
    int parsed = 0;

    HighliteRender.HighlightRule stringRule = stringHighlightRule;
    HighliteRender.HighlightRule blockRule = blockCommentHighlightRule;
    boolean inBlock = false;
    int stringState = 0;
    boolean needRegex = !regexHighlightRules.isEmpty();

    for (int i = startLine; i <= endLine; i++) {
      List<HighliteRender.HighlightSpan> cachedSpans = highlightCache.get(i);
      if (cachedSpans != null) {
        if (!needRegex) continue;
        boolean cachedInBlock = false;
        int cachedStringState = 0;
        Boolean blockState = blockCommentEndStateCache.get(i);
        Integer strState = stringEndStateCache.get(i);
        if (blockState != null) cachedInBlock = blockState;
        if (strState != null) cachedStringState = strState;
        if (cachedInBlock == inBlock && cachedStringState == stringState) continue;
      }

      String line = editor.getLineTextForRenderWithDirect(i, directLines);
      if (line == null) line = "";

      List<HighliteRender.HighlightSpan> spans = new ArrayList<>();
      List<HighliteRender.HighlightSpan> exclusionSpans = new ArrayList<>();

      HighliteRender.HighlightLineState startState = getLineStateAtStart(i);
      HighliteRender.HighlightRule parseStringRule = stringRule != null ? stringRule : whitespaceStringRule;
      HighliteRender.HighlightRule parseBlockRule = blockRule != null ? blockRule : whitespaceCommentRule;

      HighliteRender.LineParseResult parseResult =
          parseLineForSyntax(
              line,
              startState.inBlockComment,
              startState.stringState,
              parseStringRule,
              parseBlockRule,
              true);

      if (stringRule != null || blockRule != null || lineCommentHighlightRule != null) {
        spans.addAll(parseResult.spans);
      } else {
        exclusionSpans.addAll(parseResult.spans);
      }

      if (needRegex && !line.isEmpty()) {
        for (HighliteRender.HighlightRule rule : regexHighlightRules) {
          Matcher matcher = rule.pattern.matcher(line);
          while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            HighliteRender.HighlightSpan span = new HighliteRender.HighlightSpan(matcher.start(), matcher.end(), rule.paint);
            if (hasOverlap(span, spans) || hasOverlap(span, exclusionSpans)) continue;
            spans.add(span);
          }
        }
      }

      if (spans.size() > 1) {
        Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
      }

      highlightCache.put(i, spans);
      if (editor.highlite.isBlockCommentsEnabled) {
        blockCommentEndStateCache.put(i, parseResult.endsInBlockComment);
      }
      stringEndStateCache.put(i, parseResult.endsInStringState);

      inBlock = parseResult.endsInBlockComment;
      stringState = parseResult.endsInStringState;
      parsed++;
    }

    if (editor.DEBUG_RENDER_LOGS) {
      long dt = android.os.SystemClock.uptimeMillis() - startMs;
      if (dt > 4) {
        android.util.Log.d(
            "SodiumRender",
            "highlightEnsure dtMs=" + dt + " lines=" + (endLine - startLine + 1) + " parsed=" + parsed);
      }
    }
  }

  /**
   * Gets or calculates highlight spans for a line.
   */
  public List<HighliteRender.HighlightSpan> getHighlightSpansForLine(String line, int globalLine) {
    if (editor.getLogicalLineLength(globalLine, line) > editor.highliteRender.maxSyntaxLineLength) {
      return new ArrayList<>();
    }

    List<HighliteRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }
    return spans;
  }

  /**
   * Calculates highlight spans for a line.
   */
  public List<HighliteRender.HighlightSpan> calculateSpansForLine(String line, int globalLine) {
    List<HighliteRender.HighlightSpan> spans = new ArrayList<>();
    if (editor.getLogicalLineLength(globalLine, line) > editor.highliteRender.maxSyntaxLineLength) {
      return spans;
    }
    if (highlightRules.isEmpty()) {
      return spans;
    }

    HighliteRender.HighlightRule stringRule = stringHighlightRule;
    HighliteRender.HighlightRule blockCommentRule = blockCommentHighlightRule;
    List<HighliteRender.HighlightSpan> exclusionSpans = new ArrayList<>();

    if (isBlockCommentsEnabled
        || !lineCommentDelimiters.isEmpty()
        || isMultiLineStringsEnabled
        || isTripleQuoteStringsEnabled
        || isBacktickStringsEnabled
        || stringRule != null
        || blockCommentRule != null
        || lineCommentHighlightRule != null) {
      HighliteRender.HighlightLineState startState = getLineStateAtStart(globalLine);
      HighliteRender.HighlightRule parseStringRule = stringRule != null ? stringRule : whitespaceStringRule;
      HighliteRender.HighlightRule parseBlockRule =
          blockCommentRule != null ? blockCommentRule : whitespaceCommentRule;
      HighliteRender.LineParseResult parseResult =
          parseLineForSyntax(
              line,
              startState.inBlockComment,
              startState.stringState,
              parseStringRule,
              parseBlockRule,
              true);
      if (stringRule != null || blockCommentRule != null || lineCommentHighlightRule != null) {
        spans.addAll(parseResult.spans);
      } else {
        exclusionSpans.addAll(parseResult.spans);
      }

      if (globalLine >= editor.textRender.windowStartLine && globalLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
        if (isBlockCommentsEnabled) {
          blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
        }
        stringEndStateCache.put(globalLine, parseResult.endsInStringState);
      }
    }

    if (!regexHighlightRules.isEmpty() && !line.isEmpty()) {
      for (HighliteRender.HighlightRule rule : regexHighlightRules) {
        Matcher matcher = rule.pattern.matcher(line);
        while (matcher.find()) {
          if (matcher.start() == matcher.end()) continue;
          HighliteRender.HighlightSpan span = new HighliteRender.HighlightSpan(matcher.start(), matcher.end(), rule.paint);
          if (hasOverlap(span, spans) || hasOverlap(span, exclusionSpans)) continue;
          spans.add(span);
        }
      }
    }

    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  /**
   * Gets the highlight line state at the start of a line.
   */
  public HighliteRender.HighlightLineState getLineStateAtStart(int globalLine) {
    if (globalLine <= editor.textRender.windowStartLine) return new HighliteRender.HighlightLineState(false, 0);
    int windowEnd = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
    if (globalLine > windowEnd) return new HighliteRender.HighlightLineState(false, 0);

    Boolean cachedBlockPrev = blockCommentEndStateCache.get(globalLine - 1);
    Integer cachedStringPrev = stringEndStateCache.get(globalLine - 1);
    if (cachedBlockPrev != null && cachedStringPrev != null) {
      return new HighliteRender.HighlightLineState(cachedBlockPrev, cachedStringPrev);
    }

    boolean inBlock = false;
    int stringState = 0;
    for (int line = editor.textRender.windowStartLine; line < globalLine; line++) {
      Boolean cachedBlock = blockCommentEndStateCache.get(line);
      Integer cachedString = stringEndStateCache.get(line);
      if (cachedBlock != null && cachedString != null) {
        inBlock = cachedBlock;
        stringState = cachedString;
        continue;
      }
      String lineText = editor.getLineTextForRender(line);
      if (lineText == null) lineText = "";
      HighliteRender.LineParseResult result =
          parseLineForSyntax(lineText, inBlock, stringState, null, null, false);
      inBlock = result.endsInBlockComment;
      stringState = result.endsInStringState;
      blockCommentEndStateCache.put(line, inBlock);
      stringEndStateCache.put(line, stringState);
    }
    return new HighliteRender.HighlightLineState(inBlock, stringState);
  }

  /**
   * Parses a line for syntax highlighting.
   */
  public HighliteRender.LineParseResult parseLineForSyntax(
      String line,
      boolean inBlockComment,
      int stringState,
      HighliteRender.HighlightRule stringRule,
      HighliteRender.HighlightRule blockCommentRule,
      boolean collectSpans) {
    List<HighliteRender.HighlightSpan> spans = new ArrayList<>();
    int length = line.length();
    int i = 0;
    if (!isBlockCommentsEnabled) {
      inBlockComment = false;
    }
    if (stringState == STRING_STATE_BACKTICK && !isBacktickStringsEnabled) {
      stringState = 0;
    }
    if (stringState == STRING_STATE_TRIPLE && !isTripleQuoteStringsEnabled) {
      stringState = 0;
    }
    if (stringState != 0 && !isMultiLineStringsEnabled && stringState != STRING_STATE_TRIPLE) {
      stringState = 0;
    }

    while (i < length) {
      if (inBlockComment) {
        int end = findBlockCommentEnd(line, i);
        if (end < 0) {
          if (collectSpans && blockCommentRule != null && isBlockCommentsEnabled && length > 0) {
            spans.add(new HighliteRender.HighlightSpan(0, length, blockCommentRule.paint));
          }
          return new HighliteRender.LineParseResult(spans, true, 0);
        }
        if (collectSpans && blockCommentRule != null && isBlockCommentsEnabled) {
          spans.add(new HighliteRender.HighlightSpan(0, end + 2, blockCommentRule.paint));
        }
        i = end + 2;
        inBlockComment = false;
        continue;
      }

      if (stringState != 0) {
        SodiumEditor.StringEndResult endResult = findStringEndForState(line, i, stringState);
        if (endResult.found) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighliteRender.HighlightSpan(0, endResult.endIndex, stringRule.paint));
          }
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }
        if (collectSpans && stringRule != null && length > 0) {
          spans.add(new HighliteRender.HighlightSpan(0, length, stringRule.paint));
        }
        return new HighliteRender.LineParseResult(spans, false, stringState);
      }

      if (isLineCommentStart(line, i)) {
        if (collectSpans && length > i) {
          Paint commentPaint =
              (lineCommentHighlightRule != null)
                  ? lineCommentHighlightRule.paint
                  : ((blockCommentRule != null) ? blockCommentRule.paint : editor.textRender.paint);
          spans.add(new HighliteRender.HighlightSpan(i, length, commentPaint));
        }
        return new HighliteRender.LineParseResult(spans, false, 0);
      }

      char c = line.charAt(i);
      if (isTripleQuoteStart(line, i) && !isEscaped(line, i)) {
        int end = findTripleQuoteEnd(line, i + 3);
        if (end >= 0) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighliteRender.HighlightSpan(i, end + 3, stringRule.paint));
          }
          i = end + 3;
          continue;
        }
        if (isTripleQuoteStringsEnabled) {
          if (collectSpans && stringRule != null && length > 0) {
            spans.add(new HighliteRender.HighlightSpan(i, length, stringRule.paint));
          }
          return new HighliteRender.LineParseResult(spans, false, STRING_STATE_TRIPLE);
        }
      }

      if (isStringDelimiter(c) && !isEscaped(line, i)) {
        int end = findStringEnd(line, i + 1, c);
        if (end >= 0) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighliteRender.HighlightSpan(i, end + 1, stringRule.paint));
          }
          i = end + 1;
          continue;
        }
        if (isMultiLineStringsEnabled) {
          if (collectSpans && stringRule != null && length > 0) {
            spans.add(new HighliteRender.HighlightSpan(i, length, stringRule.paint));
          }
          return new HighliteRender.LineParseResult(spans, false, getStringStateForDelimiter(c));
        }
      }

      if (isBlockCommentsEnabled
          && c == '/'
          && i + 1 < length
          && line.charAt(i + 1) == '*'
          && !isTokenEscaped(line, i)) {
        int end = findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          if (collectSpans && blockCommentRule != null && length > 0) {
            spans.add(new HighliteRender.HighlightSpan(i, length, blockCommentRule.paint));
          }
          return new HighliteRender.LineParseResult(spans, true, 0);
        }
        if (collectSpans && blockCommentRule != null) {
          spans.add(new HighliteRender.HighlightSpan(i, end + 2, blockCommentRule.paint));
        }
        i = end + 2;
        continue;
      }

      i++;
    }

    return new HighliteRender.LineParseResult(spans, inBlockComment, stringState);
  }

  // Helper methods
  public static boolean hasOverlap(HighliteRender.HighlightSpan span, List<HighliteRender.HighlightSpan> spans) {
    for (HighliteRender.HighlightSpan other : spans) {
      if (span.start < other.end && other.start < span.end) {
        return true;
      }
    }
    return false;
  }

  public static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    if (r.startsWith("//")) return true;
    if (r.startsWith("^//")) return true;
    if (r.startsWith("^\\s*//")) return true;
    if (r.startsWith("\\s*//")) return true;
    return false;
  }

  private String extractLineCommentDelimiter(String regex) {
    if (regex == null) return "";
    if (regex.contains("//")) return "//";
    if (regex.contains("#")) return "#";
    if (regex.contains("--")) return "--";
    if (regex.contains(";")) return ";";
    return "";
  }

  private void addLineCommentDelimiter(String delimiter) {
    if (delimiter != null && !delimiter.isEmpty() && !lineCommentDelimiters.contains(delimiter)) {
      lineCommentDelimiters.add(delimiter);
    }
  }

  public boolean isStringDelimiter(char c) {
    if (c == '"') return true;
    if (c == '\'') return true;
    return c == '`' && isBacktickStringsEnabled;
  }

  public static boolean isTokenEscaped(String line, int index) {
    if (isEscaped(line, index)) return true;
    int next = index + 1;
    return next < line.length() && isEscaped(line, next);
  }

  public static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  public static int findStringEnd(String line, int start, char delimiter) {
    for (int i = start; i < line.length(); i++) {
      if (line.charAt(i) == delimiter && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public boolean isTripleQuoteStart(String line, int index) {
    if (!isTripleQuoteStringsEnabled) return false;
    if (index + 2 >= line.length()) return false;
    return line.charAt(index) == '"'
        && line.charAt(index + 1) == '"'
        && line.charAt(index + 2) == '"';
  }

  public static int findTripleQuoteEnd(String line, int start) {
    for (int i = start; i + 2 < line.length(); i++) {
      if (line.charAt(i) == '"'
          && line.charAt(i + 1) == '"'
          && line.charAt(i + 2) == '"'
          && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public int getStringStateForDelimiter(char delimiter) {
    if (delimiter == '"') return STRING_STATE_DOUBLE;
    if (delimiter == '\'') return STRING_STATE_SINGLE;
    return STRING_STATE_BACKTICK;
  }

  public SodiumEditor.StringEndResult findStringEndForState(String line, int start, int state) {
    if (state == STRING_STATE_TRIPLE) {
      int end = findTripleQuoteEnd(line, start);
      return new SodiumEditor.StringEndResult(end >= 0, end >= 0 ? end + 3 : start);
    }
    char delimiter = '"';
    if (state == STRING_STATE_SINGLE) delimiter = '\'';
    if (state == STRING_STATE_BACKTICK) delimiter = '`';
    int end = findStringEnd(line, start, delimiter);
    return new SodiumEditor.StringEndResult(end >= 0, end >= 0 ? end + 1 : start);
  }

  public static int findBlockCommentEnd(String line, int start) {
    for (int i = start; i + 1 < line.length(); i++) {
      if (line.charAt(i) == '*' && line.charAt(i + 1) == '/' && !isTokenEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public boolean isLineCommentStart(String line, int index) {
    if (index < 0 || index >= line.length()) return false;
    if (lineCommentDelimiters.isEmpty()) return false;
    for (int t = 0; t < lineCommentDelimiters.size(); t++) {
      String token = lineCommentDelimiters.get(t);
      int len = token.length();
      if (len == 0) continue;
      if (index + len > line.length()) continue;
      boolean match;
      if (len == 1) {
        match = line.charAt(index) == token.charAt(0);
      } else {
        match = line.regionMatches(index, token, 0, len);
      }
      if (match && !isTokenEscaped(line, index)) {
        return true;
      }
    }
    return false;
  }

  // Constants
  public static final String RULE_STRING = "__STRING__";
  public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";
  public static final String RULE_LINE_COMMENT = "__LINE_COMMENT__";
}
