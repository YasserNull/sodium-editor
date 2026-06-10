package com.yn.sodiumeditor.core.highlight;

import android.graphics.Canvas;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighlightCacheManager;
import com.yn.sodiumeditor.renderer.HighlightRender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main facade for syntax highlighting in SodiumEditor. */
public class Highlight {
  private final SodiumEditor editor;

  // Components
  public final HighlightRules rules;
  public final HighlightParser parser;
  public final HighlightCacheManager cache;

  // --- State (Kept as fields for project compatibility) ---
  public boolean isMultiLineStringsEnabled = false;
  public boolean isTripleQuoteStringsEnabled = false;
  public boolean isBacktickStringsEnabled = false;
  public boolean isBlockCommentsEnabled = false;
  public boolean isSyntaxHighlightingEnabled = true;
  public String blockCommentStartDelimiter = "/*";
  public String blockCommentEndDelimiter = "*/";

  public static final int STRING_STATE_DOUBLE = 1;
  public static final int STRING_STATE_SINGLE = 2;
  public static final int STRING_STATE_BACKTICK = 3;
  public static final int STRING_STATE_TRIPLE = 4;
  public static final String RULE_STRING = "__STRING__";
  public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";

  public int lastHighlightEnsureStartLine = -1;
  public int lastHighlightEnsureEndLine = -1;
  public int lastHighlightEnsureEditVersion = -1;
  private long lastHighlightInvalidateMs = 0L;
  private static final long HIGHLIGHT_ENSURE_THROTTLE_MS = 50L;
  private static final long HIGHLIGHT_INVALIDATE_THROTTLE_MS = 50L;
  private long lastTypingMs = 0L;
  private static final long HIGHLIGHT_TYPING_WINDOW_MS = 180L;
  private static final int CUSTOM_STRING_STATE_BASE = 10;
  private int nextCustomStringState = CUSTOM_STRING_STATE_BASE;

  // --- Syntax Highlighting State ---
  // Deprecated: Use highlight instead
  @Deprecated
  public java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();

  @Deprecated @Nullable public HighlightRender.HighlightRule lineCommentHighlightRule;
  @Deprecated public List<HighlightRender.HighlightRule> highlightRules = new ArrayList<>();
  @Deprecated public HighlightRender.HighlightRule stringHighlightRule;
  @Deprecated public HighlightRender.HighlightRule blockCommentHighlightRule;

  @Deprecated
  public ArrayList<HighlightRender.HighlightRule> regexHighlightRules = new ArrayList<>();

  @Deprecated
  public LinkedHashMap<Integer, List<HighlightRender.HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighlightRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            Map.Entry<Integer, List<HighlightRender.HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };

  @Deprecated
  public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };

  @Deprecated
  public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  public final LinkedHashMap<String, StringHighlightConfig> stringHighlightConfigs =
      new LinkedHashMap<>();
  public final LinkedHashMap<Integer, StringHighlightConfig> stringHighlightConfigsByState =
      new LinkedHashMap<>();

  public static class StringHighlightConfig {
    public final String delimiter;
    public final boolean multiLine;
    public final int state;
    public final HighlightRender.HighlightRule rule;

    public StringHighlightConfig(
        String delimiter, boolean multiLine, int state, HighlightRender.HighlightRule rule) {
      this.delimiter = delimiter;
      this.multiLine = multiLine;
      this.state = state;
      this.rule = rule;
    }
  }

  public Highlight(SodiumEditor editor) {
    this.editor = editor;
    this.rules = new HighlightRules(editor, this);
    this.parser = new HighlightParser(editor, this);
    this.cache = new HighlightCacheManager(editor, this);

    this.highlightRules = rules.highlightRules;
    this.highlightCache = cache.highlightCache;
    this.lineCommentDelimiters = rules.lineCommentDelimiters;

    syncRulesFromComponent();
  }

  public void markTyping() {
    lastTypingMs = android.os.SystemClock.uptimeMillis();
  }

  public void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    if (startLine > endLine) return;
    int v = editor.editOperators.editVersion.get();
    long now = android.os.SystemClock.uptimeMillis();
    if (v != lastHighlightEnsureEditVersion && (now - lastTypingMs) < HIGHLIGHT_TYPING_WINDOW_MS) {
      int line = Math.max(0, editor.cursor.cursorLine);
      startLine = line;
      endLine = line;
    }
    if (v != lastHighlightEnsureEditVersion
        && (now - lastHighlightInvalidateMs) < HIGHLIGHT_ENSURE_THROTTLE_MS) {
      return;
    }
    if (startLine == lastHighlightEnsureStartLine
        && endLine == lastHighlightEnsureEndLine
        && v == lastHighlightEnsureEditVersion) {
      return;
    }
    lastHighlightEnsureStartLine = startLine;
    lastHighlightEnsureEndLine = endLine;
    lastHighlightEnsureEditVersion = v;

    syncRulesToComponent();
    cache.ensureHighlightCacheForVisibleRange(startLine, endLine, directLines);
  }

  public void invalidateHighlightEnsureRange() {
    long now = android.os.SystemClock.uptimeMillis();
    if ((now - lastHighlightInvalidateMs) < HIGHLIGHT_INVALIDATE_THROTTLE_MS) {
      return;
    }
    lastHighlightEnsureStartLine = -1;
    lastHighlightEnsureEndLine = -1;
    lastHighlightEnsureEditVersion = -1;
    lastHighlightInvalidateMs = now;
  }

  private void syncRulesFromComponent() {
    stringHighlightRule = rules.stringHighlightRule;
    blockCommentHighlightRule = rules.blockCommentHighlightRule;
    lineCommentHighlightRule = rules.lineCommentHighlightRule;
  }

  private void syncRulesToComponent() {
    rules.stringHighlightRule = stringHighlightRule;
    rules.blockCommentHighlightRule = blockCommentHighlightRule;
    rules.lineCommentHighlightRule = lineCommentHighlightRule;
  }

  public void addHighlightRule(String regex, int style, int color) {
    addHighlightRule(regex, style, color, false);
  }

  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    rules.addHighlightRule(regex, style, color, underline);
    syncRulesFromComponent();
  }

  public void clearHighlightRules() {
    clearStringsHighlight();
    rules.clearHighlightRules();
    syncRulesFromComponent();
  }

  public void setLineCommentDelimiter(String d, int s, int c) {
    syncRulesToComponent();
    if (d == null || d.isEmpty()) {
      if (rules.lineCommentHighlightRule != null) {
        rules.highlightRules.remove(rules.lineCommentHighlightRule);
        rules.lineCommentHighlightRule = null;
        clearHighlightCaches();
      }
      syncRulesFromComponent();
      return;
    }
    setSingleLineCommentSyntax(true, s, c, d);
  }

  public void setStringHighlightColor(int color) {
    syncRulesToComponent();
    if (rules.stringHighlightRule == null)
      addHighlightRule(RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
    else {
      rules.stringHighlightRule.paint.setColor(color);
      clearHighlightCaches();
    }
    syncRulesFromComponent();
  }

  public void setBlockCommentHighlight(int style, int color) {
    syncRulesToComponent();
    rules.blockCommentHighlightRule =
        new HighlightRender.HighlightRule(
            "",
            style,
            color,
            editor.textRender.paint.getTextSize(),
            editor.textRender.paint.getTypeface(),
            false,
            HighlightRender.HighlightRuleType.BLOCK_COMMENT);
    clearHighlightCaches();
    syncRulesFromComponent();
  }

  public void onTextSizeChanged(float size) {
    syncRulesToComponent();
    for (HighlightRender.HighlightRule r : rules.highlightRules) r.updateTextSize(size);
    if (rules.lineCommentHighlightRule != null) rules.lineCommentHighlightRule.updateTextSize(size);
    if (rules.whitespaceStringRule != null) rules.whitespaceStringRule.updateTextSize(size);
    if (rules.whitespaceCommentRule != null) rules.whitespaceCommentRule.updateTextSize(size);
    clearHighlightCaches();
    syncRulesFromComponent();
  }

  public void onTypefaceChanged(Typeface tf) {
    syncRulesToComponent();
    if (rules.lineCommentHighlightRule != null) rules.lineCommentHighlightRule.updateTypeface(tf);
    for (HighlightRender.HighlightRule r : rules.highlightRules) r.updateTypeface(tf);
    clearHighlightCaches();
    syncRulesFromComponent();
  }

  // --- Instance Bridges ---
  public HighlightRender.HighlightLineState getLineStateAtStart(int gl) {
    return cache.getLineStateAtStart(gl);
  }

  public boolean isLineCommentStart(String line, int idx) {
    return parser.isLineCommentStart(line, idx);
  }

  public boolean isStringDelimiter(char c) {
    return parser.isStringDelimiter(c);
  }

  public boolean isTripleQuoteStart(String line, int idx) {
    return parser.isTripleQuoteStart(line, idx);
  }

  public int getStringStateForDelimiter(char c) {
    return parser.getStringStateForDelimiter(c);
  }

  public com.yn.sodiumeditor.core.StringEndResult findStringEndForState(
      String line, int start, int state) {
    return parser.findStringEndForState(line, start, state);
  }

  public HighlightRender.LineParseResult parseLineForSyntax(
      String line,
      boolean inBlock,
      int strState,
      HighlightRender.HighlightRule strRule,
      HighlightRender.HighlightRule blockRule,
      boolean collectSpans) {
    return parser.parseLineForSyntax(line, inBlock, strState, strRule, blockRule, collectSpans);
  }

  // --- Static Bridges (Delegating to HighlightUtils) ---
  public static boolean isEscaped(String line, int index) {
    return com.yn.sodiumeditor.utils.HighlightUtils.isEscaped(line, index);
  }

  public static boolean isTokenEscaped(String line, int index) {
    return com.yn.sodiumeditor.utils.HighlightUtils.isTokenEscaped(line, index);
  }

  public static int findStringEnd(String line, int start, char delimiter) {
    return com.yn.sodiumeditor.utils.HighlightUtils.findStringEnd(line, start, delimiter);
  }

  public static int findTripleQuoteEnd(String line, int start) {
    return com.yn.sodiumeditor.utils.HighlightUtils.findTripleQuoteEnd(line, start);
  }

  public static int findBlockCommentEnd(String line, int start) {
    return com.yn.sodiumeditor.utils.HighlightUtils.findBlockCommentEnd(line, start);
  }

  public int findConfiguredBlockCommentEnd(String line, int start) {
    return com.yn.sodiumeditor.utils.HighlightUtils.findTokenEnd(
        line, start, blockCommentEndDelimiter);
  }

  public boolean isConfiguredBlockCommentStart(String line, int start) {
    return com.yn.sodiumeditor.utils.HighlightUtils.isTokenStart(
        line, start, blockCommentStartDelimiter);
  }

  public int findConfiguredStringEnd(String line, int start, String delimiter) {
    return com.yn.sodiumeditor.utils.HighlightUtils.findTokenEnd(line, start, delimiter);
  }

  public List<HighlightRender.HighlightSpan> getHighlightSpansForLine(String line, int gl) {
    if (!isSyntaxHighlightingEnabled) return new ArrayList<>();
    if (editor.view.getLogicalLineLength(gl, line) > editor.highlightRender.maxSyntaxLineLength)
      return new ArrayList<>();
    List<HighlightRender.HighlightSpan> spans = cache.highlightCache.get(gl);
    if (spans == null) {
      spans = calculateSpansForLine(line, gl);
      cache.highlightCache.put(gl, spans);
    }
    return spans;
  }

  public List<HighlightRender.HighlightSpan> calculateSpansForLine(String line, int gl) {
    syncRulesToComponent();
    List<HighlightRender.HighlightSpan> spans = new ArrayList<>();
    if (!isSyntaxHighlightingEnabled) return spans;
    if (editor.view.getLogicalLineLength(gl, line) > editor.highlightRender.maxSyntaxLineLength
        || rules.isEmpty()) return spans;

    HighlightRender.HighlightLineState sState = cache.getLineStateAtStart(gl);
    HighlightRender.HighlightRule sRule = rules.stringHighlightRule;
    HighlightRender.HighlightRule bRule = rules.blockCommentHighlightRule;

    HighlightRender.LineParseResult res =
        parser.parseLineForSyntax(
            line, sState.inBlockComment, sState.stringState, sRule, bRule, true);
    spans.addAll(res.spans);

    if (!rules.regexHighlightRules.isEmpty() && line != null && !line.isEmpty()) {
      for (HighlightRender.HighlightRule rule : rules.regexHighlightRules) {
        java.util.regex.Matcher m = rule.pattern.matcher(line);
        while (m.find()) {
          HighlightRender.HighlightSpan span =
              new HighlightRender.HighlightSpan(m.start(), m.end(), rule.paint);
          if (!com.yn.sodiumeditor.utils.HighlightUtils.hasOverlap(span, spans)) spans.add(span);
        }
      }
    }
    if (spans.size() > 1)
      java.util.Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    return spans;
  }

  public void clearHighlightCaches() {
    cache.highlightCache.clear();
    cache.blockCommentEndStateCache.clear();
    cache.stringEndStateCache.clear();
    editor.colorCodeHighlight.clearColorCodeCaches();
    editor.urlUnderline.clearUrlUnderlineCache();
    editor.pathUnderline.clearPathUnderlineCache();
    invalidateHighlightEnsureRange();
  }

  public void invalidateHighlightCacheForLine(int line) {
    removeCachedHighlightStateFromLine(line);
    editor.colorCodeHighlight.clearColorCodeCacheForLine(line);
    editor.urlUnderline.clearUrlUnderlineCacheForLine(line);
    editor.pathUnderline.clearPathUnderlineCacheForLine(line);
    invalidateHighlightEnsureRange();
  }

  private void removeCachedHighlightStateFromLine(int line) {
    java.util.Iterator<Integer> highlightKeys = cache.highlightCache.keySet().iterator();
    while (highlightKeys.hasNext()) {
      if (highlightKeys.next() >= line) highlightKeys.remove();
    }
    java.util.Iterator<Integer> blockKeys = cache.blockCommentEndStateCache.keySet().iterator();
    while (blockKeys.hasNext()) {
      if (blockKeys.next() >= line) blockKeys.remove();
    }
    java.util.Iterator<Integer> stringKeys = cache.stringEndStateCache.keySet().iterator();
    while (stringKeys.hasNext()) {
      if (stringKeys.next() >= line) stringKeys.remove();
    }
  }

  public void setStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(
          Highlight.RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    editor.invalidate();
  }

  public void setMultiLineStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(
          Highlight.RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    editor.invalidate();
  }

  public void setBacktickStringsEnabled(boolean enabled) {
    if (isBacktickStringsEnabled == enabled) return;
    isBacktickStringsEnabled = enabled;
    clearHighlightCaches();
    editor.invalidate();
  }

  public void setMultiLineComments(boolean enabled, int style, int color) {
    boolean needsInvalidate = false;
    if (blockCommentHighlightRule == null || blockCommentHighlightRule.style != style) {
      if (blockCommentHighlightRule != null) {
        highlightRules.remove(blockCommentHighlightRule);
      }
      blockCommentHighlightRule =
          new HighlightRender.HighlightRule(
              Highlight.RULE_BLOCK_COMMENT,
              style,
              color,
              editor.textRender.paint.getTextSize(),
              editor.textRender.paint.getTypeface(),
              false,
              HighlightRender.HighlightRuleType.BLOCK_COMMENT);
      highlightRules.add(blockCommentHighlightRule);
      needsInvalidate = true;
    } else {
      if (blockCommentHighlightRule.paint.getColor() != color) {
        blockCommentHighlightRule.paint.setColor(color);
        needsInvalidate = true;
      }
    }
    if (isBlockCommentsEnabled != enabled) {
      isBlockCommentsEnabled = enabled;
      needsInvalidate = true;
    }
    if (needsInvalidate) {
      clearHighlightCaches();
      editor.invalidate();
    }
  }

  public void setMultiCommentsHighlight(
      String startDelimiter, String endDelimiter, int color, int style) {
    if (startDelimiter == null
        || startDelimiter.isEmpty()
        || endDelimiter == null
        || endDelimiter.isEmpty()) {
      return;
    }
    boolean delimitersChanged =
        !startDelimiter.equals(blockCommentStartDelimiter)
            || !endDelimiter.equals(blockCommentEndDelimiter);
    blockCommentStartDelimiter = startDelimiter;
    blockCommentEndDelimiter = endDelimiter;
    setMultiLineComments(true, style, color);
    if (delimitersChanged) {
      clearHighlightCaches();
      editor.invalidate();
    }
  }

  public void setSingleLineCommentDelimiters(String... delimiters) {
    lineCommentDelimiters.clear();
    if (delimiters != null) {
      for (String d : delimiters) {
        if (d == null) continue;
        String trimmed = d.trim();
        if (trimmed.isEmpty()) continue;
        if (!lineCommentDelimiters.contains(trimmed)) {
          lineCommentDelimiters.add(trimmed);
        }
      }
    }
    lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
    clearHighlightCaches();
    editor.invalidate();
  }

  public void ensureLineCommentDelimiter(String delimiter) {
    if (delimiter == null) return;
    String trimmed = delimiter.trim();
    if (trimmed.isEmpty()) return;
    if (!lineCommentDelimiters.contains(trimmed)) {
      lineCommentDelimiters.add(trimmed);
      lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
      clearHighlightCaches();
      editor.invalidate();
    }
  }

  public void setSingleLineCommentsHighlight(boolean enabled, int style, int color) {
    if (!enabled) {
      boolean hadRule = lineCommentHighlightRule != null || rules.lineCommentHighlightRule != null;
      if (lineCommentHighlightRule != null) highlightRules.remove(lineCommentHighlightRule);
      if (rules.lineCommentHighlightRule != null)
        rules.highlightRules.remove(rules.lineCommentHighlightRule);
      rules.lineCommentHighlightRule = null;
      lineCommentHighlightRule = null;
      if (hadRule) {
        clearHighlightCaches();
        editor.invalidate();
      }
      return;
    }

    if (lineCommentHighlightRule == null || lineCommentHighlightRule.style != style) {
      if (lineCommentHighlightRule != null) highlightRules.remove(lineCommentHighlightRule);
      lineCommentHighlightRule =
          new HighlightRender.HighlightRule(
              "",
              style,
              color,
              editor.textRender.paint.getTextSize(),
              editor.textRender.paint.getTypeface(),
              false,
              HighlightRender.HighlightRuleType.LINE_COMMENT);
    } else {
      lineCommentHighlightRule.paint.setColor(color);
    }
    if (rules.lineCommentHighlightRule != null
        && rules.lineCommentHighlightRule != lineCommentHighlightRule) {
      rules.highlightRules.remove(rules.lineCommentHighlightRule);
    }
    rules.lineCommentHighlightRule = lineCommentHighlightRule;
    if (!highlightRules.contains(lineCommentHighlightRule)) {
      highlightRules.add(lineCommentHighlightRule);
    }
    clearHighlightCaches();
    editor.invalidate();
  }

  public void setSingleLineCommentSyntax(
      boolean enabled, int style, int color, String... delimiters) {
    setSingleLineCommentDelimiters(delimiters);
    setSingleLineCommentsHighlight(enabled, style, color);
  }

  public void setSingleCommentsHighlight(String delimiter, int color, int style) {
    setSingleLineCommentSyntax(true, style, color, delimiter);
  }

  public void setStringsHighlight(String delimiter, boolean multiLine, int color, int style) {
    if (delimiter == null || delimiter.isEmpty()) return;
    isSyntaxHighlightingEnabled = true;
    StringHighlightConfig old = stringHighlightConfigs.remove(delimiter);
    if (old != null) {
      stringHighlightConfigsByState.remove(old.state);
      highlightRules.remove(old.rule);
    }

    int state = getOrCreateStringStateForDelimiter(delimiter);
    HighlightRender.HighlightRule rule =
        new HighlightRender.HighlightRule(
            "",
            style,
            color,
            editor.textRender.paint.getTextSize(),
            editor.textRender.paint.getTypeface(),
            false,
            HighlightRender.HighlightRuleType.STRING);
    StringHighlightConfig config = new StringHighlightConfig(delimiter, multiLine, state, rule);
    stringHighlightConfigs.put(delimiter, config);
    stringHighlightConfigsByState.put(state, config);
    highlightRules.add(rule);
    clearHighlightCaches();
    editor.invalidate();
  }

  public void clearStringsHighlight() {
    if (stringHighlightConfigs.isEmpty()) return;
    for (StringHighlightConfig config : stringHighlightConfigs.values()) {
      highlightRules.remove(config.rule);
    }
    stringHighlightConfigs.clear();
    stringHighlightConfigsByState.clear();
    clearHighlightCaches();
    editor.invalidate();
  }

  public StringHighlightConfig getStringHighlightConfigForState(int state) {
    return stringHighlightConfigsByState.get(state);
  }

  public StringHighlightConfig findStringHighlightStart(String line, int index) {
    if (line == null || stringHighlightConfigs.isEmpty()) return null;
    StringHighlightConfig match = null;
    for (StringHighlightConfig config : stringHighlightConfigs.values()) {
      if (match != null && match.delimiter.length() >= config.delimiter.length()) continue;
      if (com.yn.sodiumeditor.utils.HighlightUtils.isTokenStart(line, index, config.delimiter)) {
        match = config;
      }
    }
    return match;
  }

  private int getOrCreateStringStateForDelimiter(String delimiter) {
    if ("\"".equals(delimiter)) return STRING_STATE_DOUBLE;
    if ("'".equals(delimiter)) return STRING_STATE_SINGLE;
    if ("`".equals(delimiter)) return STRING_STATE_BACKTICK;
    if ("\"\"\"".equals(delimiter)) return STRING_STATE_TRIPLE;
    for (StringHighlightConfig config : stringHighlightConfigs.values()) {
      if (delimiter.equals(config.delimiter)) return config.state;
    }
    return nextCustomStringState++;
  }

  public void setTripleQuoteStringsEnabled(boolean enabled) {
    if (isTripleQuoteStringsEnabled == enabled) return;
    isTripleQuoteStringsEnabled = enabled;
    clearHighlightCaches();
    editor.invalidate();
  }

  public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    if (line == null || line.isEmpty() || start >= end) return 0f;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return 0f;

    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        float padX =
            editor.binaryRender.binaryCaretNotationEnabled
                ? 0f
                : editor.binaryRender.binaryTokenPaddingX;
        float x1 =
            editor.binaryRender.getXForCharBinary(
                line, start, editor.textRender.paint, spans, padX);
        float x2 =
            editor.binaryRender.getXForCharBinary(line, end, editor.textRender.paint, spans, padX);
        return x2 - x1;
      }
    }

    if (rules.isEmpty()) {
      return editor.textRender.paint.measureText(line, start, end);
    }

    List<HighlightRender.HighlightSpan> spans = cache.highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      cache.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return editor.textRender.paint.measureText(line, start, end);
    }

    float total = 0f;
    int lastEnd = start;

    for (HighlightRender.HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        total += editor.textRender.paint.measureText(line, lastEnd, span.start);
      }

      int safeSpanEnd = Math.min(span.end, end);
      if (safeSpanEnd > span.start) {
        total += span.paint.measureText(line, span.start, safeSpanEnd);
      }
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < end) {
      total += editor.textRender.paint.measureText(line, lastEnd, end);
    }

    return total;
  }

  public void drawHighlightedSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float x, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    if (rules.isEmpty()) {
      editor.textRender.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, editor.textRender.paint);
      return;
    }

    List<HighlightRender.HighlightSpan> spans = cache.highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      cache.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      editor.textRender.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, editor.textRender.paint);
      return;
    }

    float currentX = x;
    int lastEnd = start;

    for (HighlightRender.HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        editor.textRender.paint.setUnderlineText(false);
        canvas.drawText(line, lastEnd, span.start, currentX, y, editor.textRender.paint);
        currentX += editor.textRender.paint.measureText(line, lastEnd, span.start);
      }

      int safeSpanEnd = Math.min(span.end, end);
      if (safeSpanEnd > span.start) {
        span.paint.setUnderlineText(false);
        canvas.drawText(line, span.start, safeSpanEnd, currentX, y, span.paint);
        currentX += span.paint.measureText(line, span.start, safeSpanEnd);
      }
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < end) {
      editor.textRender.paint.setUnderlineText(false);
      canvas.drawText(line, lastEnd, end, currentX, y, editor.textRender.paint);
    }
  }

  public float measureTextInRange(String line, int start, int end, int globalLine) {
    if (line == null || start >= end) return 0f;
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        float padX =
            editor.binaryRender.binaryCaretNotationEnabled
                ? 0f
                : editor.binaryRender.binaryTokenPaddingX;
        float x1 =
            editor.binaryRender.getXForCharBinary(
                line, start, editor.textRender.paint, spans, padX);
        float x2 =
            editor.binaryRender.getXForCharBinary(line, end, editor.textRender.paint, spans, padX);
        return x2 - x1;
      }
    }
    return measureHighlightedSegmentWidth(line, globalLine, start, end);
  }
}
