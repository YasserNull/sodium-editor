package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class HighlightManager {
  private final SodiumEditorView view;
  static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");

  final java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();
  HighlightRule lineCommentHighlightRule;
  final java.util.List<HighlightRule> highlightRules = new java.util.ArrayList<>();
  HighlightRule stringHighlightRule;
  HighlightRule blockCommentHighlightRule;
  final java.util.ArrayList<HighlightRule> regexHighlightRules = new java.util.ArrayList<>();
  final java.util.LinkedHashMap<Integer, java.util.List<HighlightSpan>> highlightCache =
      new java.util.LinkedHashMap<Integer, java.util.List<HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            java.util.Map.Entry<Integer, java.util.List<HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  final java.util.LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new java.util.LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  final java.util.LinkedHashMap<Integer, Integer> stringEndStateCache =
      new java.util.LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  boolean isUrlUnderliningEnabled = false;
  Pattern urlUnderlinePattern = DEFAULT_URL_UNDERLINE_PATTERN;
  final Paint urlUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final java.util.LinkedHashMap<Integer, java.util.List<UnderlineSpan>> urlUnderlineCache =
      new java.util.LinkedHashMap<Integer, java.util.List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            java.util.Map.Entry<Integer, java.util.List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };

  boolean isPathUnderliningEnabled = false;
  Pattern pathUnderlinePattern = java.util.regex.Pattern.compile("/[^\\\\s,;()'\\\"]+");
  final Paint pathUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final java.util.LinkedHashMap<Integer, java.util.List<UnderlineSpan>> pathUnderlineCache =
      new java.util.LinkedHashMap<Integer, java.util.List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            java.util.Map.Entry<Integer, java.util.List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };
  final java.util.concurrent.ConcurrentHashMap<String, Boolean> pathValidationCache =
      new java.util.concurrent.ConcurrentHashMap<>();
  final java.util.Set<String> pendingPathValidations =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  int errorUnderlineColor = 0xFFE53935;
  boolean errorUnderlineEnabled = true;
  final Paint errorUnderlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final android.graphics.Path errorUnderlinePath = new android.graphics.Path();
  float errorUnderlineHeightScale = 0.18f;
  float errorUnderlineWaveLengthScale = 0.70f;
  float errorUnderlineStrokeScale = 0.08f;
  float errorUnderlineSmoothness = 3f;
  final java.util.LinkedHashMap<Integer, java.util.List<ErrorUnderlineSpan>> errorUnderlineMap =
      new java.util.LinkedHashMap<Integer, java.util.List<ErrorUnderlineSpan>>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            java.util.Map.Entry<Integer, java.util.List<ErrorUnderlineSpan>> eldest) {
          return size() > 2000;
        }
      };

  final java.util.LinkedHashMap<Integer, int[]> colorCodeBgCache =
      new java.util.LinkedHashMap<Integer, int[]>(600, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, int[]> eldest) {
          return size() > 600;
        }
      };

  boolean isColorHighlightingEnabled = false;
  private static final Pattern COLOR_HEX_PATTERN =
      Pattern.compile(
          "(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))\\b|(\\b0x[a-fA-F0-9]{6,8}\\b)",
          Pattern.CASE_INSENSITIVE);
  final Paint colorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  int lastHighlightEnsureStartLine = -1;
  int lastHighlightEnsureEndLine = -1;
  int lastHighlightEnsureEditVersion = -1;

  HighlightManager(SodiumEditorView view) {
    this.view = view;
    urlUnderlineTmpPaint.setStrokeWidth(1f);
    pathUnderlineTmpPaint.setStrokeWidth(1f);
    errorUnderlinePaint.setUnderlineText(false);
  }

  void drawColorCodeBackgrounds(
      Canvas canvas, String line, int globalLine, float lineTop, float lineBottom) {
    if (!isColorHighlightingEnabled || line.isEmpty()) {
      return;
    }

    if (line.indexOf('#') < 0 && line.indexOf('0') < 0) return;

    int[] triples = colorCodeBgCache.get(globalLine);
    if (triples == null) {
      ArrayList<Integer> tmp = null;
      java.util.regex.Matcher matcher = COLOR_HEX_PATTERN.matcher(line);
      while (matcher.find()) {
        String colorString = matcher.group(0);
        if (colorString == null || colorString.isEmpty()) continue;

        int color;
        try {
          if (colorString.startsWith("0x") || colorString.startsWith("0X")) {
            String hex = colorString.substring(2);
            if (hex.length() == 6) hex = "FF" + hex;
            color = (int) Long.parseLong(hex, 16);
          } else {
            color = android.graphics.Color.parseColor(colorString);
          }
        } catch (Exception e) {
          continue;
        }

        int backgroundColor = (color & 0x00FFFFFF) | (0xC0 << 24);
        if (tmp == null) tmp = new ArrayList<>();
        tmp.add(matcher.start());
        tmp.add(matcher.end());
        tmp.add(backgroundColor);
      }
      if (tmp == null || tmp.isEmpty()) {
        triples = new int[0];
      } else {
        triples = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) triples[i] = tmp.get(i);
      }
      colorCodeBgCache.put(globalLine, triples);
    }

    if (triples.length == 0) return;

    colorOverlayPaint.setStyle(Paint.Style.FILL);
    for (int i = 0; i + 2 < triples.length; i += 3) {
      int start = triples[i];
      int end = triples[i + 1];
      int backgroundColor = triples[i + 2];

      float left = view.measureTextForHighlight(line, start, globalLine);
      float right = view.measureTextForHighlight(line, end, globalLine);
      colorOverlayPaint.setColor(backgroundColor);
      canvas.drawRect(left, lineTop, right, lineBottom, colorOverlayPaint);
    }
  }

  void resetEnsureRange() {
    lastHighlightEnsureStartLine = -1;
    lastHighlightEnsureEndLine = -1;
    lastHighlightEnsureEditVersion = -1;
  }

  void clearCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.clear();
    urlUnderlineCache.clear();
    pathUnderlineCache.clear();
  }

  void invalidateLine(int line) {
    highlightCache.remove(line);
    colorCodeBgCache.remove(line);
    urlUnderlineCache.remove(line);
    pathUnderlineCache.remove(line);
  }

  void clearHighlightCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.clear();
    urlUnderlineCache.clear();
    pathUnderlineCache.clear();
    resetEnsureRange();
    view.invalidateBracketGuideCacheForHighlight();
  }

  void invalidateHighlightCacheForLine(int line) {
    highlightCache.remove(line);
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.remove(line);
    urlUnderlineCache.remove(line);
    pathUnderlineCache.remove(line);
    resetEnsureRange();
    view.invalidateBracketGuideCacheForHighlight();
  }

  void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      java.util.HashMap<Integer, String> directLines) {
    if (highlightRules.isEmpty()) return;
    if (firstVisibleLine > lastVisibleLine) return;

    HighlightRule stringRule = stringHighlightRule;
    HighlightRule blockRule = blockCommentHighlightRule;
    boolean needSyntax = stringRule != null || blockRule != null;
    boolean needRegex = !regexHighlightRules.isEmpty();
    if (!needSyntax && !needRegex) return;

    boolean inBlock = false;
    int stringState = 0;
    final int localWindowStart = view.windowStartLine;
    final int localWindowEnd;
    synchronized (view.linesWindow) {
      localWindowEnd = localWindowStart + view.linesWindow.size();
    }

    if (needSyntax) {
      int prevLine = firstVisibleLine - 1;
      Boolean cachedBlockPrev = blockCommentEndStateCache.get(prevLine);
      Integer cachedStringPrev = stringEndStateCache.get(prevLine);
      if (cachedBlockPrev != null && cachedStringPrev != null) {
        inBlock = cachedBlockPrev;
        stringState = cachedStringPrev;
      } else {
        int seedStart = localWindowStart;
        int seedEnd = Math.min(firstVisibleLine, localWindowEnd);
        for (int line = seedStart; line < seedEnd; line++) {
          String seedLine = view.getLineTextForRenderWithDirect(line, directLines);
          if (seedLine == null) seedLine = "";
          LineParseResult seedResult = parseLineForSyntax(seedLine, inBlock, stringState, null, null, false);
          inBlock = seedResult.endsInBlockComment;
          stringState = seedResult.endsInStringState;
          if (line >= localWindowStart && line < localWindowEnd) {
            if (view.isBlockCommentsEnabledForHighlight()) blockCommentEndStateCache.put(line, inBlock);
            stringEndStateCache.put(line, stringState);
          }
          if (line + 1 == firstVisibleLine) break;
        }
      }
    }

    for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
      java.util.List<HighlightSpan> cachedSpans = highlightCache.get(globalLine);
      boolean hasCachedState = true;
      Boolean cachedBlock = null;
      Integer cachedString = null;
      if (needSyntax && globalLine >= localWindowStart && globalLine < localWindowEnd) {
        cachedBlock = blockCommentEndStateCache.get(globalLine);
        cachedString = stringEndStateCache.get(globalLine);
        hasCachedState = cachedBlock != null && cachedString != null;
      }
      if (cachedSpans != null && (!needSyntax || hasCachedState)) {
        if (needSyntax && cachedBlock != null && cachedString != null) {
          inBlock = cachedBlock;
          stringState = cachedString;
        }
        continue;
      }

      String line = view.getLineTextForRenderWithDirect(globalLine, directLines);
      if (line == null) line = "";

      java.util.List<HighlightSpan> spans;
      if (needSyntax) {
        LineParseResult parseResult =
            parseLineForSyntax(line, inBlock, stringState, stringRule, blockRule, true);
        spans = parseResult.spans;
        inBlock = parseResult.endsInBlockComment;
        stringState = parseResult.endsInStringState;
        if (globalLine >= localWindowStart && globalLine < localWindowEnd) {
          if (view.isBlockCommentsEnabledForHighlight()) blockCommentEndStateCache.put(globalLine, inBlock);
          stringEndStateCache.put(globalLine, stringState);
        }
      } else {
        spans = new java.util.ArrayList<>();
      }

      if (needRegex && !line.isEmpty()) {
        for (HighlightRule rule : regexHighlightRules) {
          java.util.regex.Matcher matcher = rule.pattern.matcher(line);
          while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            HighlightSpan span = new HighlightSpan(matcher.start(), matcher.end(), rule.paint);
            if (hasOverlap(span, spans)) continue;
            spans.add(span);
          }
        }
      }

      if (spans.size() > 1) {
        java.util.Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
      }
      highlightCache.put(globalLine, spans);
    }
  }

  void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, java.util.HashMap<Integer, String> directLines) {
    if (startLine > endLine) return;
    int v = view.getEditVersionValue();
    if (startLine == lastHighlightEnsureStartLine
        && endLine == lastHighlightEnsureEndLine
        && v == lastHighlightEnsureEditVersion) {
      return;
    }
    lastHighlightEnsureStartLine = startLine;
    lastHighlightEnsureEndLine = endLine;
    lastHighlightEnsureEditVersion = v;
    ensureHighlightCacheForVisibleRange(startLine, endLine, directLines);
  }

  HighlightLineState getLineStateAtStart(int globalLine) {
    if (globalLine <= view.windowStartLine) return new HighlightLineState(false, 0);
    int windowEnd = view.windowStartLine + view.linesWindow.size();
    if (globalLine > windowEnd) return new HighlightLineState(false, 0);
    int prev = globalLine - 1;
    Boolean cachedBlockPrev = blockCommentEndStateCache.get(prev);
    Integer cachedStringPrev = stringEndStateCache.get(prev);
    if (cachedBlockPrev != null && cachedStringPrev != null) {
      return new HighlightLineState(cachedBlockPrev, cachedStringPrev);
    }
    boolean inBlock = false;
    int stringState = 0;
    for (int line = view.windowStartLine; line < globalLine; line++) {
      String lineText = view.getLineTextForRender(line);
      if (lineText == null) lineText = "";
      LineParseResult result = parseLineForSyntax(lineText, inBlock, stringState, null, null, false);
      inBlock = result.endsInBlockComment;
      stringState = result.endsInStringState;
      if (line >= view.windowStartLine && line < windowEnd) {
        if (view.isBlockCommentsEnabledForHighlight()) blockCommentEndStateCache.put(line, inBlock);
        stringEndStateCache.put(line, stringState);
      }
    }
    return new HighlightLineState(inBlock, stringState);
  }

  java.util.List<HighlightSpan> calculateSpansForLine(String line, int globalLine) {
    java.util.List<HighlightSpan> spans = new java.util.ArrayList<>();
    if (highlightRules.isEmpty()) {
      return spans;
    }
    HighlightRule stringRule = stringHighlightRule;
    HighlightRule blockCommentRule = blockCommentHighlightRule;
    java.util.List<HighlightSpan> exclusionSpans = new java.util.ArrayList<>();

    if (view.isMultiLineStringsEnabledForHighlight() || view.isBlockCommentsEnabledForHighlight() || lineCommentHighlightRule != null) {
      HighlightLineState startState = getLineStateAtStart(globalLine);
      HighlightRule parseStringRule =
          (stringRule != null) ? stringRule : view.getStringHighlightRuleForWhitespace();
      HighlightRule parseBlockRule =
          (blockCommentRule != null) ? blockCommentRule : view.getBlockCommentHighlightRuleForWhitespace();
      LineParseResult parseResult =
          parseLineForSyntax(line, startState.inBlockComment, startState.stringState, parseStringRule, parseBlockRule, true);
      if (parseResult != null && parseResult.spans != null) {
        exclusionSpans.addAll(parseResult.spans);
      }
    }

    if (!regexHighlightRules.isEmpty() && !line.isEmpty()) {
      for (HighlightRule rule : regexHighlightRules) {
        java.util.regex.Matcher matcher = rule.pattern.matcher(line);
        while (matcher.find()) {
          if (matcher.start() == matcher.end()) continue;
          HighlightSpan span = new HighlightSpan(matcher.start(), matcher.end(), rule.paint);
          if (hasOverlap(span, exclusionSpans)) continue;
          spans.add(span);
        }
      }
    }

    if (stringRule != null || blockCommentRule != null || lineCommentHighlightRule != null) {
      LineParseResult parseResult =
          parseLineForSyntax(line, false, 0, stringRule, blockCommentRule, true);
      if (parseResult != null && parseResult.spans != null) {
        spans.addAll(parseResult.spans);
      }
    }

    if (spans.size() > 1) {
      java.util.Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  LineParseResult parseLineForSyntax(
      String line,
      boolean inBlockComment,
      int stringState,
      HighlightRule stringRule,
      HighlightRule blockCommentRule,
      boolean allowLineComment) {
    int length = line.length();
    java.util.List<HighlightSpan> spans = new java.util.ArrayList<>();
    int i = 0;

    while (i < length) {
      char c = line.charAt(i);

      if (inBlockComment) {
        int end = line.indexOf("*/", i);
        if (end == -1) {
          if (blockCommentRule != null) {
            spans.add(new HighlightSpan(0, length, blockCommentRule.paint));
          }
          return new LineParseResult(spans, true, 0);
        }
        if (blockCommentRule != null) {
          spans.add(new HighlightSpan(0, end + 2, blockCommentRule.paint));
        }
        inBlockComment = false;
        i = end + 2;
        continue;
      }

      if (stringState != 0) {
        StringEndResult endResult = findStringEndForState(line, i, stringState);
        if (endResult.found) {
          if (stringRule != null) {
            spans.add(new HighlightSpan(0, endResult.endIndex, stringRule.paint));
          }
          stringState = 0;
          i = endResult.endIndex;
          continue;
        }
        if (stringRule != null) {
          spans.add(new HighlightSpan(0, length, stringRule.paint));
        }
        return new LineParseResult(spans, false, stringState);
      }

      if (allowLineComment && lineCommentHighlightRule != null) {
        int lineCommentStart = findLineCommentStart(line, i);
        if (lineCommentStart >= 0) {
          Paint commentPaint = lineCommentHighlightRule.paint;
          spans.add(new HighlightSpan(lineCommentStart, length, commentPaint));
          return new LineParseResult(spans, false, 0);
        }
      }

      if (view.isTripleQuoteStringsEnabledForHighlight() && i + 2 < length) {
        if (line.startsWith("\"\"\"", i)) {
          int end = line.indexOf("\"\"\"", i + 3);
          if (end >= 0) {
            if (stringRule != null) spans.add(new HighlightSpan(i, end + 3, stringRule.paint));
            i = end + 3;
            continue;
          }
          if (stringRule != null) spans.add(new HighlightSpan(i, length, stringRule.paint));
          return new LineParseResult(spans, false, STRING_STATE_TRIPLE);
        }
      }

      if (view.isBacktickStringsEnabledForHighlight() && c == '`') {
        int end = line.indexOf('`', i + 1);
        if (end >= 0) {
          if (stringRule != null) spans.add(new HighlightSpan(i, end + 1, stringRule.paint));
          i = end + 1;
          continue;
        }
        if (stringRule != null) spans.add(new HighlightSpan(i, length, stringRule.paint));
        return new LineParseResult(spans, false, getStringStateForDelimiter(c));
      }

      if (c == '"' || c == '\'') {
        StringEndResult end = findStringEndForState(line, i + 1, getStringStateForDelimiter(c));
        if (end.found) {
          if (stringRule != null) spans.add(new HighlightSpan(i, end.endIndex, stringRule.paint));
          i = end.endIndex;
          continue;
        }
        if (stringRule != null) spans.add(new HighlightSpan(i, length, stringRule.paint));
        return new LineParseResult(spans, false, getStringStateForDelimiter(c));
      }

      if (view.isBlockCommentsEnabledForHighlight() && i + 1 < length && c == '/' && line.charAt(i + 1) == '*') {
        int end = line.indexOf("*/", i + 2);
        if (end == -1) {
          if (blockCommentRule != null) {
            spans.add(new HighlightSpan(i, length, blockCommentRule.paint));
          }
          return new LineParseResult(spans, true, 0);
        }
        if (blockCommentRule != null) {
          spans.add(new HighlightSpan(i, end + 2, blockCommentRule.paint));
        }
        i = end + 2;
        continue;
      }
      i++;
    }
    return new LineParseResult(spans, inBlockComment, stringState);
  }

  static boolean hasOverlap(HighlightSpan span, java.util.List<HighlightSpan> spans) {
    for (HighlightSpan other : spans) {
      if (span.start < other.end && span.end > other.start) return true;
    }
    return false;
  }

  static final int STRING_STATE_DOUBLE = 1;
  static final int STRING_STATE_SINGLE = 2;
  static final int STRING_STATE_BACKTICK = 3;
  static final int STRING_STATE_TRIPLE = 4;

  static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    if (r.startsWith("//")) return true;
    if (r.startsWith("^//")) return true;
    if (r.startsWith("^\\s*//")) return true;
    if (r.startsWith("\\s*//")) return true;
    return false;
  }

  boolean isStringDelimiter(char c) {
    if (c == '"') return true;
    if (c == '\'') return true;
    return c == '`' && view.isBacktickStringsEnabledForHighlight();
  }

  static boolean isTokenEscaped(String line, int index) {
    if (isEscaped(line, index)) return true;
    int next = index + 1;
    return next < line.length() && isEscaped(line, next);
  }

  static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  static int findStringEnd(String line, int start, char delimiter) {
    for (int i = start; i < line.length(); i++) {
      if (line.charAt(i) == delimiter && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  boolean isTripleQuoteStart(String line, int index) {
    if (!view.isTripleQuoteStringsEnabledForHighlight()) return false;
    if (index + 2 >= line.length()) return false;
    return line.charAt(index) == '"'
        && line.charAt(index + 1) == '"'
        && line.charAt(index + 2) == '"';
  }

  static int findTripleQuoteEnd(String line, int start) {
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

  int getStringStateForDelimiter(char delimiter) {
    if (delimiter == '"') return STRING_STATE_DOUBLE;
    if (delimiter == '\'') return STRING_STATE_SINGLE;
    return STRING_STATE_BACKTICK;
  }

  StringEndResult findStringEndForState(String line, int start, int state) {
    if (state == STRING_STATE_TRIPLE) {
      int end = findTripleQuoteEnd(line, start);
      return new StringEndResult(end >= 0, end >= 0 ? end + 3 : start);
    }
    char delimiter = '"';
    if (state == STRING_STATE_SINGLE) delimiter = '\'';
    if (state == STRING_STATE_BACKTICK) delimiter = '`';
    int end = findStringEnd(line, start, delimiter);
    return new StringEndResult(end >= 0, end >= 0 ? end + 1 : start);
  }

  static class StringEndResult {
    final boolean found;
    final int endIndex;

    StringEndResult(boolean found, int endIndex) {
      this.found = found;
      this.endIndex = endIndex;
    }
  }

  static int findBlockCommentEnd(String line, int start) {
    for (int i = start; i + 1 < line.length(); i++) {
      if (line.charAt(i) == '*' && line.charAt(i + 1) == '/' && !isTokenEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  
  int findLineCommentStart(String line, int from) {
    if (lineCommentDelimiters.isEmpty()) return -1;
    int len = line.length();
    for (int i = Math.max(0, from); i < len; i++) {
      if (isLineCommentStart(line, i)) return i;
    }
    return -1;
  }

  boolean isLineCommentStart(String line, int index) {
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

  void drawUnderlineSegmentWithFade(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float baselineY,
      float lineTop,
      float lineBottom,
      Paint textPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha,
      boolean isPath) {
    if (start >= end) return;

    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float underlineY = baselineY + (fm.descent * 0.5f);
    underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

    float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
    thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

    Paint tmpPaintToUse = isPath ? pathUnderlineTmpPaint : urlUnderlineTmpPaint;
    tmpPaintToUse.set(textPaint);
    tmpPaintToUse.setStyle(Paint.Style.STROKE);
    tmpPaintToUse.setStrokeWidth(thickness);
    tmpPaintToUse.setUnderlineText(false);

    boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
    if (!hasFade || end <= fadeStart || start >= fadeEnd) {
      float w = view.measureTextWithVisualSpacesForHighlight(line, start, end, textPaint);
      if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
      return;
    }

    float currentX = x;
    int baseAlpha = textPaint.getAlpha();

    int beforeEnd = Math.min(end, fadeStart);
    if (start < beforeEnd) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = view.measureTextWithVisualSpacesForHighlight(line, start, beforeEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int fadeSegStart = Math.max(start, fadeStart);
    int fadeSegEnd = Math.min(end, fadeEnd);
    if (fadeSegStart < fadeSegEnd) {
      tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      float w = view.measureTextWithVisualSpacesForHighlight(line, fadeSegStart, fadeSegEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = view.measureTextWithVisualSpacesForHighlight(line, afterStart, end, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
    }
  }

  @Nullable
  List<UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
    if (!isUrlUnderliningEnabled || urlUnderlinePattern == null) return null;
    List<UnderlineSpan> cached = urlUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    ArrayList<UnderlineSpan> spans = new ArrayList<>();
    java.util.regex.Matcher matcher = urlUnderlinePattern.matcher(line);
    while (matcher.find()) {
      int start = matcher.start();
      int end = matcher.end();
      end = trimUrlUnderlineEnd(line, start, end);
      if (end > start) {
        spans.add(new UnderlineSpan(start, end, false));
      }
    }
    urlUnderlineCache.put(globalLine, spans);
    return spans;
  }

  @Nullable
  List<UnderlineSpan> getPathUnderlineSpansForLine(String line, int globalLine) {
    if (!isPathUnderliningEnabled || pathUnderlinePattern == null) return null;
    List<UnderlineSpan> cached = pathUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    ArrayList<UnderlineSpan> spans = new ArrayList<>();
    java.util.regex.Matcher matcher = pathUnderlinePattern.matcher(line);
    while (matcher.find()) {
      String potentialPath = matcher.group();
      if (potentialPath != null && !potentialPath.isEmpty()) {
        Boolean exists = pathValidationCache.get(potentialPath);
        if (Boolean.TRUE.equals(exists)) {
          spans.add(new UnderlineSpan(matcher.start(), matcher.end(), true));
        } else if (exists == null) {
          view.validatePathInBackgroundForHighlight(potentialPath, globalLine);
        }
      }
    }
    pathUnderlineCache.put(globalLine, spans);
    return spans;
  }

  static int trimUrlUnderlineEnd(String line, int start, int end) {
    int e = Math.min(end, line.length());
    while (e > start) {
      char c = line.charAt(e - 1);
      if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')'
          || c == ']' || c == '}' || c == '>' || c == '\"' || c == '\'') {
        e--;
        continue;
      }
      break;
    }
    return e;
  }

  void drawErrorUnderlinesForLine(
      Canvas canvas,
      String line,
      int globalLine,
      float baselineY,
      float lineTop,
      float lineBottom) {
    if (!errorUnderlineEnabled) return;
    List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
    if (spans == null || spans.isEmpty()) return;
    List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
    int len = line.length();
    for (ErrorUnderlineSpan span : snapshot) {
      int start = Math.max(0, Math.min(span.start, len));
      int end = Math.max(start, Math.min(span.end, len));
      if (start >= end) continue;
      float xStart = view.measureTextForHighlight(line, start, globalLine);
      float xEnd = view.measureTextForHighlight(line, end, globalLine);
      drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
    }
  }

  void drawErrorUnderlinesForLineRange(
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float baselineY,
      float lineTop,
      float lineBottom) {
    if (!errorUnderlineEnabled) return;
    List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
    if (spans == null || spans.isEmpty()) return;
    List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
    int len = line.length();
    start = Math.max(0, Math.min(start, len));
    end = Math.max(start, Math.min(end, len));
    if (start >= end) return;
    for (ErrorUnderlineSpan span : snapshot) {
      int s = Math.max(start, Math.max(0, Math.min(span.start, len)));
      int e = Math.min(end, Math.max(s, Math.min(span.end, len)));
      if (s >= e) continue;
      float xStart = view.measureTextForHighlight(line, s, globalLine);
      float xEnd = view.measureTextForHighlight(line, e, globalLine);
      drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
    }
  }

  void drawErrorUnderlinesForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float baselineY,
      float lineTop,
      float lineBottom) {
    if (!errorUnderlineEnabled) return;
    List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
    if (spans == null || spans.isEmpty()) return;
    List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
    int len = line.length();
    for (ErrorUnderlineSpan span : snapshot) {
      int start = Math.max(segStart, Math.max(0, Math.min(span.start, len)));
      int end = Math.min(segEnd, Math.max(start, Math.min(span.end, len)));
      if (start >= end) continue;
      float xStart = view.measureTextWithVisualSpacesForHighlight(line, segStart, start, view.getTextPaintForHighlight());
      float w = view.measureTextWithVisualSpacesForHighlight(line, start, end, view.getTextPaintForHighlight());
      if (w <= 0f) continue;
      drawErrorSquiggle(canvas, xStart, xStart + w, baselineY, lineTop, lineBottom);
    }
  }

  void drawErrorSquiggle(
      Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
    if (xEnd <= xStart) return;
    Paint basePaint = view.getTextPaintForHighlight();
    float lineH = Math.max(1f, lineBottom - lineTop);
    float textSize = basePaint.getTextSize();
    float y = baselineY + (basePaint.getFontMetrics().descent * 0.55f);
    float maxY = lineBottom - 2f;
    float minY = lineTop + 1f;
    y = Math.max(minY, Math.min(y, maxY));
    float amplitude = Math.max(1f, Math.min(lineH * 0.22f, textSize * errorUnderlineHeightScale));
    float roomTop = y - minY;
    float roomBottom = maxY - y;
    float room = Math.max(0f, Math.min(roomTop, roomBottom));
    amplitude = Math.min(amplitude, Math.max(1f, room));
    float waveLen = Math.max(textSize * errorUnderlineWaveLengthScale, amplitude * 2f);
    float thickness = Math.max(1f, textSize * errorUnderlineStrokeScale);

    errorUnderlinePaint.setColor(errorUnderlineColor);
    errorUnderlinePaint.setStyle(Paint.Style.STROKE);
    errorUnderlinePaint.setStrokeWidth(thickness);
    errorUnderlinePaint.setUnderlineText(false);
    errorUnderlinePaint.setStrokeCap(Paint.Cap.ROUND);
    errorUnderlinePaint.setStrokeJoin(Paint.Join.ROUND);
    if (errorUnderlineSmoothness > 0f) {
      errorUnderlinePaint.setPathEffect(new android.graphics.CornerPathEffect(errorUnderlineSmoothness));
    } else {
      errorUnderlinePaint.setPathEffect(null);
    }

    errorUnderlinePath.reset();
    errorUnderlinePath.moveTo(xStart, y);
    float x = xStart;
    boolean up = true;
    while (x < xEnd) {
      float midX = Math.min(xEnd, x + waveLen * 0.5f);
      float endX = Math.min(xEnd, x + waveLen);
      float ctrlY = up ? (y - amplitude) : (y + amplitude);
      errorUnderlinePath.quadTo(midX, ctrlY, endX, y);
      up = !up;
      x = endX;
    }
    canvas.drawPath(errorUnderlinePath, errorUnderlinePaint);
  }
  static class HighlightSpan {
    final int start;
    final int end;
    final Paint paint;

    HighlightSpan(int start, int end, Paint paint) {
      this.start = start;
      this.end = end;
      this.paint = paint;
    }
  }

  static class UnderlineSpan {
    final int start;
    final int end;
    final boolean isPath;

    UnderlineSpan(int start, int end, boolean isPath) {
      this.start = start;
      this.end = end;
      this.isPath = isPath;
    }
  }

  static class ErrorUnderlineSpan {
    final int start;
    final int end;

    ErrorUnderlineSpan(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  static class LineParseResult {
    final List<HighlightSpan> spans;
    final boolean endsInBlockComment;
    final int endsInStringState;

    LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
      this.spans = spans;
      this.endsInBlockComment = endsInBlockComment;
      this.endsInStringState = endsInStringState;
    }
  }

  static class HighlightLineState {
    final boolean inBlockComment;
    final int stringState;

    HighlightLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  enum HighlightRuleType {
    REGEX,
    STRING,
    BLOCK_COMMENT,
    LINE_COMMENT
  }

  static class HighlightRule {
    final HighlightRuleType type;
    final Pattern pattern;
    final Paint paint;
    final int style;
    final boolean underline;

    HighlightRule(
        String regex,
        int style,
        int color,
        float textSize,
        Typeface typeface,
        boolean underline,
        HighlightRuleType type) {
      this.type = type;
      if (type == HighlightRuleType.REGEX) {
        this.pattern = Pattern.compile(regex);
      } else {
        this.pattern = null;
      }
      this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      this.paint.setColor(color);
      this.paint.setTextSize(textSize);
      this.style = style;
      this.underline = underline;

      int typefaceStyle;
      switch (style) {
        case SodiumEditorView.STYLE_BOLD:
          typefaceStyle = Typeface.BOLD;
          break;
        case SodiumEditorView.STYLE_ITALIC:
          typefaceStyle = Typeface.ITALIC;
          break;
        case SodiumEditorView.STYLE_BOLD_ITALIC:
          typefaceStyle = Typeface.BOLD_ITALIC;
          break;
        default:
          typefaceStyle = Typeface.NORMAL;
          break;
      }
      this.paint.setTypeface(Typeface.create(typeface, typefaceStyle));
      this.paint.setUnderlineText(underline);
    }

    void updateTextSize(float sizePx) {
      paint.setTextSize(sizePx);
    }

    void updateTypeface(Typeface typeface) {
      int typefaceStyle;
      switch (style) {
        case SodiumEditorView.STYLE_BOLD:
          typefaceStyle = Typeface.BOLD;
          break;
        case SodiumEditorView.STYLE_ITALIC:
          typefaceStyle = Typeface.ITALIC;
          break;
        case SodiumEditorView.STYLE_BOLD_ITALIC:
          typefaceStyle = Typeface.BOLD_ITALIC;
          break;
        default:
          typefaceStyle = Typeface.NORMAL;
          break;
      }
      paint.setTypeface(Typeface.create(typeface, typefaceStyle));
    }
  }
}
