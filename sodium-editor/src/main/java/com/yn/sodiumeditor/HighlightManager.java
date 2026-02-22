package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class HighlightManager {
  private final SodiumEditorView view;
  static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");

  // --- Constants copied from SodiumEditorView ---
  public static final String RULE_STRING = "__STRING__";
  public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";
  public static final String RULE_LINE_COMMENT = "__LINE_COMMENT__";
  public static final int STRING_STATE_DOUBLE = 1;
  public static final int STRING_STATE_SINGLE = 2;
  public static final int STRING_STATE_BACKTICK = 3;
  public static final int STRING_STATE_TRIPLE = 4;

  // --- Fields copied from SodiumEditorView ---
  boolean highlightCurrentLine = true;
  private int currentLineHighlightColor = 0x202196F3; // Default: translucent gray (more visible)
  final Paint currentLinePaint = new Paint();
  boolean isMultiLineStringsEnabled = false;
  boolean isBacktickStringsEnabled = false;
  boolean isBlockCommentsEnabled = false;
  boolean isTripleQuoteStringsEnabled = false;
  int maxSyntaxLineLength = 4096;
  private int prefetchCols = 512;

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
    currentLinePaint.setColor(currentLineHighlightColor); // Initialize currentLinePaint
  }

  void validatePathInBackground(final String path, final int lineToInvalidate) {
    if (pendingPathValidations.contains(path)) {
      return;
    }
    pendingPathValidations.add(path);

    view.ioHandler.post(
        () -> {
          boolean exists = false;
          try {
            java.io.File file = new java.io.File(path);
            exists = file.exists();
          } catch (Exception e) {
            // Ignore errors
          } finally {
            pathValidationCache.put(path, exists);
            pendingPathValidations.remove(path);

            if (exists) {
              view.mainHandler.post(
                  () -> {
                    pathUnderlineCache.remove(lineToInvalidate);
                    view.invalidate();
                  });
            }
          }
        });
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

      float left = measureText(line, start, globalLine);
      float right = measureText(line, end, globalLine);
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

  public void setHighlightCurrentLine(boolean enabled) {
    if (this.highlightCurrentLine == enabled) return;
    this.highlightCurrentLine = enabled;
    view.invalidate();
  }

  public void setCurrentLineHighlightColor(int color) {
    this.currentLineHighlightColor = color;
    this.currentLinePaint.setColor(color);
    if (highlightCurrentLine) view.invalidate();
  }

  public void addHighlightRule(String regex, int style, int color) {
    addHighlightRule(regex, style, color, false);
  }

  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    HighlightRuleType type = HighlightRuleType.REGEX;
    if (RULE_STRING.equals(regex)) {
      type = HighlightRuleType.STRING;
    } else if (RULE_BLOCK_COMMENT.equals(regex)) {
      type = HighlightRuleType.BLOCK_COMMENT;
    } else if (isLineCommentRegex(regex)) {
      type = HighlightRuleType.LINE_COMMENT;
    }

    HighlightRule rule =
        new HighlightRule(
            regex, style, color, view.paint.getTextSize(), view.paint.getTypeface(), underline, type);
    if (type == HighlightRuleType.LINE_COMMENT) {
      ensureLineCommentDelimiter("//");
      lineCommentHighlightRule = rule;
    } else {
      highlightRules.add(rule);
      if (type == HighlightRuleType.STRING) {
        stringHighlightRule = rule;
      } else if (type == HighlightRuleType.BLOCK_COMMENT) {
        blockCommentHighlightRule = rule;
      } else {
        regexHighlightRules.add(rule);
      }
    }
    clearHighlightCaches();
    view.invalidate();
  }

  public void clearHighlightRules() {
    highlightRules.clear();
    stringHighlightRule = null;
    blockCommentHighlightRule = null;
    regexHighlightRules.clear();
    lineCommentHighlightRule = null;
    clearHighlightCaches();
    view.invalidate();
  }

  public void setUrlUnderliningEnabled(boolean enabled) {
    if (this.isUrlUnderliningEnabled == enabled) return;
    this.isUrlUnderliningEnabled = enabled;
    urlUnderlineCache.clear();
    view.invalidate();
  }

  public void setUrlUnderliningRegex(@Nullable String regex) {
    if (regex == null || regex.trim().isEmpty()) {
      this.urlUnderlinePattern = null;
    } else {
      this.urlUnderlinePattern = Pattern.compile(regex);
    }
    urlUnderlineCache.clear();
    view.invalidate();
  }

  public void setPathUnderliningEnabled(boolean enabled) {
    if (this.isPathUnderliningEnabled == enabled) return;
    this.isPathUnderliningEnabled = enabled;
    // Clear all caches when state changes to ensure fresh checks.
    pathUnderlineCache.clear();
    pathValidationCache.clear();
    pendingPathValidations.clear();
    view.invalidate();
  }

  void setMaxSyntaxLineLength(int maxChars) {
    int safe = Math.max(512, maxChars);
    if (maxSyntaxLineLength == safe) return;
    maxSyntaxLineLength = safe;
    clearHighlightCaches();
    view.invalidate();
  }

  public void setErrorUnderlineColor(int color) {
    if (this.errorUnderlineColor == color) return;
    this.errorUnderlineColor = color;
    view.invalidate();
  }

  public void setErrorUnderlineEnabled(boolean enabled) {
    if (errorUnderlineEnabled == enabled) return;
    errorUnderlineEnabled = enabled;
    view.invalidate();
  }

  public void setErrorUnderlineHeightScale(float scale) {
    float safe = Math.max(0f, scale);
    if (errorUnderlineHeightScale == safe) return;
    errorUnderlineHeightScale = safe;
    view.invalidate();
  }

  public void setErrorUnderlineWaveLengthScale(float scale) {
    float safe = Math.max(0.1f, scale);
    if (errorUnderlineWaveLengthScale == safe) return;
    errorUnderlineWaveLengthScale = safe;
    view.invalidate();
  }

  public void setErrorUnderlineStrokeScale(float scale) {
    float safe = Math.max(0f, scale);
    if (errorUnderlineStrokeScale == safe) return;
    errorUnderlineStrokeScale = safe;
    view.invalidate();
  }

  public void setErrorUnderlineSmoothness(float radiusPx) {
    float safe = Math.max(0f, radiusPx);
    if (errorUnderlineSmoothness == safe) return;
    errorUnderlineSmoothness = safe;
    view.invalidate();
  }

  public void setErrorUnderline(int line, int col, int length) {
    if (line < 0) return;
    if (length <= 0) {
      errorUnderlineMap.remove(line);
      view.invalidate();
      return;
    }
    int start = Math.max(0, col);
    int end = Math.max(start, start + length);
    List<ErrorUnderlineSpan> list = errorUnderlineMap.get(line);
    if (list == null) {
      list = new ArrayList<>();
      errorUnderlineMap.put(line, list);
    }
    list.add(new ErrorUnderlineSpan(start, end));
    view.invalidate();
  }

  public void setStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(RULE_STRING, SodiumEditorView.STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    view.invalidate();
  }

  public void setMultiLineStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(RULE_STRING, SodiumEditorView.STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    view.invalidate();
  }

  public void setColorCodeHighlightingEnabled(boolean enabled) {
    if (isColorHighlightingEnabled == enabled) return;
    isColorHighlightingEnabled = enabled;
    colorCodeBgCache.clear();
    view.invalidate();
  }

  public void setBacktickStringsEnabled(boolean enabled) {
    if (isBacktickStringsEnabled == enabled) return;
    isBacktickStringsEnabled = enabled;
    clearHighlightCaches();
    view.invalidate();
  }

  public void setMultiLineComments(boolean enabled, int style, int color) {
    boolean needsInvalidate = false;
    if (blockCommentHighlightRule == null || blockCommentHighlightRule.style != style) {
      if (blockCommentHighlightRule != null) {
        highlightRules.remove(blockCommentHighlightRule);
      }
      blockCommentHighlightRule =
          new HighlightRule(
              RULE_BLOCK_COMMENT,
              style,
              color,
              view.paint.getTextSize(),
              view.paint.getTypeface(),
              false,
              HighlightRuleType.BLOCK_COMMENT);
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
      view.invalidate();
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
    // Prefer longer delimiters first (e.g. '//' before '/')
    lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
    clearHighlightCaches();
    view.invalidate();
  }

  public void ensureLineCommentDelimiter(String delimiter) {
    if (delimiter == null) return;
    String trimmed = delimiter.trim();
    if (trimmed.isEmpty()) return;
    if (!lineCommentDelimiters.contains(trimmed)) {
      lineCommentDelimiters.add(trimmed);
      lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
      clearHighlightCaches();
      view.invalidate();
    }
  }

  public void setSingleLineCommentsHighlight(boolean enabled, int style, int color) {
    if (!enabled) {
      if (lineCommentHighlightRule != null) {
        lineCommentHighlightRule = null;
        clearHighlightCaches();
        view.invalidate();
      }
      return;
    }

    if (lineCommentHighlightRule == null || lineCommentHighlightRule.style != style) {
      lineCommentHighlightRule =
          new HighlightRule(
              "",
              style,
              color,
              view.paint.getTextSize(),
              view.paint.getTypeface(),
              false,
              HighlightRuleType.LINE_COMMENT);
    } else {
      lineCommentHighlightRule.paint.setColor(color);
    }
    clearHighlightCaches();
    view.invalidate();
  }

  public void setSingleLineCommentSyntax(
      boolean enabled, int style, int color, String... delimiters) {
    setSingleLineCommentDelimiters(delimiters);
    setSingleLineCommentsHighlight(enabled, style, color);
  }

  public void setTripleQuoteStringsEnabled(boolean enabled) {
    if (isTripleQuoteStringsEnabled == enabled) return;
    isTripleQuoteStringsEnabled = enabled;
    clearHighlightCaches();
    view.invalidate();
  }



  public void clearHighlightCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.clear();
    urlUnderlineCache.clear();
    pathUnderlineCache.clear();
    resetEnsureRange();
    view.bracketGuideManager.invalidateCache();
  }

  public void invalidateHighlightCacheForLine(int line) {
    highlightCache.remove(line);
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.remove(line);
    urlUnderlineCache.remove(line);
    pathUnderlineCache.remove(line);
    resetEnsureRange();
    view.bracketGuideManager.invalidateCache();
  }
  static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    if (r.startsWith("//")) return true;
    if (r.startsWith("^//")) return true;
    if (r.startsWith("^\\s*//")) return true;
    if (r.startsWith("\\s*//")) return true;
    return false;
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
            if (view.isBlockCommentsEnabled) blockCommentEndStateCache.put(line, inBlock);
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
          if (view.isBlockCommentsEnabled) blockCommentEndStateCache.put(globalLine, inBlock);
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
        if (view.isBlockCommentsEnabled) blockCommentEndStateCache.put(line, inBlock);
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

    if (view.isMultiLineStringsEnabled || view.isBlockCommentsEnabled || lineCommentHighlightRule != null) {
      HighlightLineState startState = getLineStateAtStart(globalLine);
      HighlightRule parseStringRule =
          (stringRule != null) ? stringRule : view.highlightManager.stringHighlightRule;
      HighlightRule parseBlockRule =
          (blockCommentRule != null) ? blockCommentRule : view.highlightManager.blockCommentHighlightRule;
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

      if (view.isTripleQuoteStringsEnabled && i + 2 < length) {
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

      if (view.isBacktickStringsEnabled && c == '`') {
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

      if (view.isBlockCommentsEnabled && i + 1 < length && c == '/' && line.charAt(i + 1) == '*') {
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

  boolean isStringDelimiter(char c) {
    if (c == '"') return true;
    if (c == '\'') return true;
    return c == '`' && view.isBacktickStringsEnabled;
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
    if (!view.isTripleQuoteStringsEnabled) return false;
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
      float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, start, end, textPaint);
      if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
      return;
    }

    float currentX = x;
    int baseAlpha = textPaint.getAlpha();

    int beforeEnd = Math.min(end, fadeStart);
    if (start < beforeEnd) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, start, beforeEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int fadeSegStart = Math.max(start, fadeStart);
    int fadeSegEnd = Math.min(end, fadeEnd);
    if (fadeSegStart < fadeSegEnd) {
      tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, fadeSegStart, fadeSegEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, afterStart, end, textPaint);
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
          validatePathInBackground(potentialPath, globalLine);
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
      float xStart = measureText(line, start, globalLine);
      float xEnd = measureText(line, end, globalLine);
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
      float xStart = measureText(line, s, globalLine);
      float xEnd = measureText(line, e, globalLine);
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
      float xStart = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, start, view.paint);
      float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, start, end, view.paint);
      if (w <= 0f) continue;
      drawErrorSquiggle(canvas, xStart, xStart + w, baselineY, lineTop, lineBottom);
    }
  }

  void drawErrorSquiggle(
      Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
    if (xEnd <= xStart) return;
    Paint basePaint = view.paint;
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

  float measureText(String line, int length, int globalLine) {
    int logicalLen = view.getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));
    if (logicalLen > maxSyntaxLineLength) {
      float avg = getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, 0, safeLen, view.paint);
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, 0, safeLen, view.paint);
    }

    float totalWidth = 0;
    int lastEnd = 0;

    for (HighlightSpan span : spans) {
      if (lastEnd >= safeLen) break;
      if (span.start >= safeLen) break;
      if (span.start < lastEnd) continue;

      // Measure part before the span
      if (span.start > lastEnd) {
        int measureEnd = Math.min(span.start, safeLen);
        totalWidth += view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, lastEnd, measureEnd, view.paint);
      }

      lastEnd = span.start;

      // Measure the span itself
      int measureEnd = Math.min(span.end, safeLen);
      totalWidth += view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, lastEnd, measureEnd, span.paint);

      lastEnd = span.end;
    }

    // Measure remaining part
    if (lastEnd < safeLen) {
      totalWidth += view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, lastEnd, safeLen, view.paint);
    }

    return totalWidth;
  }
  
  public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
    List<HighlightSpan> spans = highlightCache.get(lineIndex);
    if (spans == null) {
      spans = calculateSpansForLine(lineText, lineIndex);
      highlightCache.put(lineIndex, spans);
    }
    for (HighlightSpan span : spans) {
      if (charIndex >= span.start && charIndex < span.end) {
        return span.paint;
      }
    }
    return view.paint;
  }

  public float getAverageCharWidthForLine(String line, int lineIndex) {
    if (line == null || line.isEmpty()) return view.paint.measureText(" ");
    if (lineIndex >= 0) {
      synchronized (view.avgCharWidthCache) {
        Float cached = view.avgCharWidthCache.get(lineIndex);
        if (cached != null) return cached;
      }
    }
    int sampleLen = Math.min(line.length(), 256);
    float w = (sampleLen > 0) ? view.paint.measureText(line, 0, sampleLen) : view.paint.measureText(" ");
    float avg = (sampleLen > 0) ? (w / sampleLen) : w;
    if (lineIndex >= 0) {
      synchronized (view.avgCharWidthCache) {
        if (view.isStableGlyphPositionsEnabled && view.avgCharWidthCache.containsKey(lineIndex)) {
          return view.avgCharWidthCache.get(lineIndex);
        }
        view.avgCharWidthCache.put(lineIndex, avg);
      }
    }
    return avg;
  }

  void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
    if (line == null || line.isEmpty()) {
      if (view.charAnimationManager.isEnabled()
          && globalLine == view.charAnimationManager.getDelAnimLine()
          && view.charAnimationManager.getDelAnimText() != null
          && !view.charAnimationManager.getDelAnimText().isEmpty()
          && view.charAnimationManager.getDelAnimAlpha() > 0f) {
        Paint ghostPaint = (view.charAnimationManager.getDelAnimPaint() != null) ? view.charAnimationManager.getDelAnimPaint() : view.paint;
        view.charAnimationManager.getTempPaint().set(ghostPaint);
        view.charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        view.charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(view.charAnimationManager.getDelAnimText(), 0f, y, view.charAnimationManager.getTempPaint());
      }
      return;
    }

    view.getVisibleCharRangeForLine(line, globalLine, view.visibleCharRangeTmp);
    int visibleStart = view.visibleCharRangeTmp[0];
    int visibleEnd = view.visibleCharRangeTmp[1];
    int len = view.getLogicalLineLength(globalLine, line);
    if (len > maxSyntaxLineLength) {
      if (visibleEnd > visibleStart) {
        int sliceStart = view.getStreamedLineSliceStart(globalLine);
        int sliceEnd = sliceStart + line.length();
        int drawStart = Math.max(visibleStart, sliceStart);
        int drawEnd = Math.min(visibleEnd, sliceEnd);
        if (drawEnd > drawStart) {
          float avg = getAverageCharWidthForLine(line, globalLine);
          float x = avg * drawStart;
          canvas.drawText(line, drawStart - sliceStart, drawEnd - sliceStart, x, y, view.paint);
        }
      }
      return;
    }
    if (visibleStart > 0 || visibleEnd < len) {
      drawHighlightedLineRange(canvas, line, globalLine, visibleStart, visibleEnd, y);
      return;
    }

    List<UnderlineSpan> combinedUnderlines = new ArrayList<>();

    List<UnderlineSpan> urlSpans =
        getUrlUnderlineSpansForLine(line, globalLine);
    if (urlSpans != null) combinedUnderlines.addAll(urlSpans);

    List<UnderlineSpan> pathSpans =
        getPathUnderlineSpansForLine(line, globalLine);
    if (pathSpans != null) combinedUnderlines.addAll(pathSpans);

    // Sort combined underlines by start position
    if (!combinedUnderlines.isEmpty()) {
      java.util.Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (view.charAnimationManager.isEnabled()
        && globalLine == view.charAnimationManager.getCharAnimLine()
        && view.charAnimationManager.getCharAnimEndChar() > view.charAnimationManager.getCharAnimStartChar()
        && view.charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart = Math.max(0, Math.min(view.charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd = Math.max(0, Math.min(view.charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, view.charAnimationManager.getCharAnimAlpha()));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = view.scrollManager.getDrawLineTop(globalLine);
    float lineBottom = lineTop + view.lineHeight;

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          0,
          line.length(),
          0f,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
      if (view.charAnimationManager.isEnabled()
          && globalLine == view.charAnimationManager.getDelAnimLine()
          && view.charAnimationManager.getDelAnimText() != null
          && !view.charAnimationManager.getDelAnimText().isEmpty()
          && view.charAnimationManager.getDelAnimAlpha() > 0f) {
        int at = Math.max(0, Math.min(view.charAnimationManager.getDelAnimAtChar(), line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (view.charAnimationManager.getDelAnimPaint() != null) ? view.charAnimationManager.getDelAnimPaint() : view.paint;
        view.charAnimationManager.getTempPaint().set(ghostPaint);
        view.charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        view.charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(view.charAnimationManager.getDelAnimText(), x, y, view.charAnimationManager.getTempPaint());
      }
      drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          0,
          line.length(),
          0f,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
      if (view.charAnimationManager.isEnabled()
          && globalLine == view.charAnimationManager.getDelAnimLine()
          && view.charAnimationManager.getDelAnimText() != null
          && !view.charAnimationManager.getDelAnimText().isEmpty()
          && view.charAnimationManager.getDelAnimAlpha() > 0f) {
        int at = Math.max(0, Math.min(view.charAnimationManager.getDelAnimAtChar(), line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (view.charAnimationManager.getDelAnimPaint() != null) ? view.charAnimationManager.getDelAnimPaint() : view.paint;
        view.charAnimationManager.getTempPaint().set(ghostPaint);
        view.charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        view.charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(view.charAnimationManager.getDelAnimText(), x, y, view.charAnimationManager.getTempPaint());
      }
      drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
      return;
    }

    float currentX = 0f;
    int lastEnd = 0;

    for (HighlightSpan span : spans) {
      if (span.start < lastEnd) continue;

      if (span.start >= line.length()) break;
      int safeSpanEnd = Math.min(span.end, line.length());

      if (span.start > lastEnd) {
        currentX +=
            drawTextSegmentWithFadeAndUnderlines(
                canvas,
                line,
                lastEnd,
                span.start,
                currentX,
                y,
                view.paint,
                fadeStart,
                fadeEnd,
                fadeAlpha,
                combinedUnderlines,
                lineTop,
                lineBottom);
      }

      currentX +=
          drawTextSegmentWithFadeAndUnderlines(
              canvas,
              line,
              span.start,
              safeSpanEnd,
              currentX,
              y,
              span.paint,
              fadeStart,
              fadeEnd,
              fadeAlpha,
              combinedUnderlines,
              lineTop,
              lineBottom);
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < line.length()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          lastEnd,
          line.length(),
          currentX,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
    }

    if (view.charAnimationManager.isEnabled()
        && globalLine == view.charAnimationManager.getDelAnimLine()
        && view.charAnimationManager.getDelAnimText() != null
        && !view.charAnimationManager.getDelAnimText().isEmpty()
        && view.charAnimationManager.getDelAnimAlpha() > 0f) {
      int at = Math.max(0, Math.min(view.charAnimationManager.getDelAnimAtChar(), line.length()));
      float x = measureText(line, at, globalLine);
      Paint ghostPaint = (view.charAnimationManager.getDelAnimPaint() != null) ? view.charAnimationManager.getDelAnimPaint() : view.paint;
      view.charAnimationManager.getTempPaint().set(ghostPaint);
      view.charAnimationManager.getTempPaint().setUnderlineText(false);
      int baseAlpha = ghostPaint.getAlpha();
      view.charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
      canvas.drawText(view.charAnimationManager.getDelAnimText(), x, y, view.charAnimationManager.getTempPaint());
    }
    drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
  }

  void drawHighlightedSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float x, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    if (highlightRules.isEmpty()) {
      view.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, view.paint);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      view.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, view.paint);
      return;
    }

    float currentX = x;
    int lastEnd = start;

    for (HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        view.paint.setUnderlineText(false);
        canvas.drawText(line, lastEnd, span.start, currentX, y, view.paint);
        currentX += view.paint.measureText(line, lastEnd, span.start);
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
      view.paint.setUnderlineText(false);
      canvas.drawText(line, lastEnd, end, currentX, y, view.paint);
    }
  }

  float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    if (line == null || line.isEmpty() || start >= end) return 0f;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return 0f;

    if (highlightRules.isEmpty()) {
      return view.paint.measureText(line, start, end);
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return view.paint.measureText(line, start, end);
    }

    float total = 0f;
    int lastEnd = start;

    for (HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        total += view.paint.measureText(line, lastEnd, span.start);
      }

      int safeSpanEnd = Math.min(span.end, end);
      if (safeSpanEnd > span.start) {
        total += span.paint.measureText(line, span.start, safeSpanEnd);
      }
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < end) {
      total += view.paint.measureText(line, lastEnd, end);
    }

    return total;
  }

  void drawHighlightedLineRange(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (line == null || line.isEmpty()) return;
    int len = line.length();
    start = Math.max(0, Math.min(start, len));
    end = Math.max(start, Math.min(end, len));
    if (start >= end) return;

    List<UnderlineSpan> combinedUnderlines = new ArrayList<>();
    List<UnderlineSpan> urlSpans = getUrlUnderlineSpansForLine(line, globalLine);
    if (urlSpans != null) combinedUnderlines.addAll(urlSpans);

    List<UnderlineSpan> pathSpans = getPathUnderlineSpansForLine(line, globalLine);
    if (pathSpans != null) combinedUnderlines.addAll(pathSpans);
    if (!combinedUnderlines.isEmpty()) {
      Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (view.charAnimationManager.isEnabled()
        && globalLine == view.charAnimationManager.getCharAnimLine()
        && view.charAnimationManager.getCharAnimEndChar() > view.charAnimationManager.getCharAnimStartChar()
        && view.charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart =
          Math.max(0, Math.min(view.charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd =
          Math.max(0, Math.min(view.charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, view.charAnimationManager.getCharAnimAlpha()));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = view.scrollManager.getDrawLineTop(globalLine);
    float lineBottom = lineTop + view.lineHeight;
    float currentX = measureText(line, start, globalLine);
    int lastEnd = start;

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          start,
          end,
          currentX,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
    } else {
      List<HighlightSpan> spans = highlightCache.get(globalLine);
      if (spans == null) {
        spans = calculateSpansForLine(line, globalLine);
        highlightCache.put(globalLine, spans);
      }
      for (HighlightSpan span : spans) {
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  view.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  combinedUnderlines,
                  lineTop,
                  lineBottom);
        }
        if (segEnd > segStart) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  combinedUnderlines,
                  lineTop,
                  lineBottom);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
      if (lastEnd < end) {
        drawTextSegmentWithFadeAndUnderlines(
            canvas,
            line,
            lastEnd,
            end,
            currentX,
            y,
            view.paint,
            fadeStart,
            fadeEnd,
            fadeAlpha,
            combinedUnderlines,
            lineTop,
            lineBottom);
      }
    }

    if (view.charAnimationManager.isEnabled()
        && globalLine == view.charAnimationManager.getDelAnimLine()
        && view.charAnimationManager.getDelAnimText() != null
        && !view.charAnimationManager.getDelAnimText().isEmpty()
        && view.charAnimationManager.getDelAnimAlpha() > 0f) {
      int at = Math.max(0, Math.min(view.charAnimationManager.getDelAnimAtChar(), line.length()));
      if (at >= start && at <= end) {
        float x = measureText(line, at, globalLine);
        Paint ghostPaint =
            (view.charAnimationManager.getDelAnimPaint() != null)
                ? view.charAnimationManager.getDelAnimPaint()
                : view.paint;
        view.charAnimationManager.getTempPaint().set(ghostPaint);
        view.charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        view.charAnimationManager
            .getTempPaint()
            .setAlpha(
                (int)
                    (baseAlpha
                        * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(view.charAnimationManager.getDelAnimText(), x, y, view.charAnimationManager.getTempPaint());
      }
    }
    drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
  }

  void drawHighlightedLineSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float y,
      float lineTop,
      float lineBottom) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    final List<UnderlineSpan> urlUnderlines = getUrlUnderlineSpansForLine(line, globalLine);
    final List<UnderlineSpan> pathUnderlines = getPathUnderlineSpansForLine(line, globalLine);
    List<UnderlineSpan> combinedUnderlines = urlUnderlines;
    if (pathUnderlines != null && !pathUnderlines.isEmpty()) {
      combinedUnderlines = new ArrayList<>();
      if (urlUnderlines != null) combinedUnderlines.addAll(urlUnderlines);
      combinedUnderlines.addAll(pathUnderlines);
      Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (view.charAnimationManager.isEnabled()
        && globalLine == view.charAnimationManager.getCharAnimLine()
        && view.charAnimationManager.getCharAnimEndChar() > view.charAnimationManager.getCharAnimStartChar()
        && view.charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart =
          Math.max(0, Math.min(view.charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd =
          Math.max(0, Math.min(view.charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, view.charAnimationManager.getCharAnimAlpha()));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          start,
          end,
          0f,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    float currentX = 0f;
    int lastEnd = start;

    if (!spans.isEmpty()) {
      for (HighlightSpan span : spans) {
        if (lastEnd >= end) break;
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  view.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  urlUnderlines,
                  lineTop,
                  lineBottom);
        }

        if (segEnd > segStart) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  urlUnderlines,
                  lineTop,
                  lineBottom);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          lastEnd,
          end,
          currentX,
          y,
          view.paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          urlUnderlines,
          lineTop,
          lineBottom);
    }
  }

  float drawTextSegmentWithFadeAndUnderlines(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha,
      @Nullable List<UnderlineSpan> underlines,
      float lineTop,
      float lineBottom) {
    if (start >= end) return 0f;
    boolean anyUnderliningActive =
        (isUrlUnderliningEnabled && urlUnderlinePattern != null)
            || (isPathUnderliningEnabled && pathUnderlinePattern != null);
    if (underlines == null || underlines.isEmpty() || !anyUnderliningActive) {
      return drawTextSegmentWithFade(
          canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
    }

    float currentX = x;
    int pos = start;

    for (UnderlineSpan span : underlines) {
      if (span.end <= pos) continue;
      if (span.start >= end) break;

      int plainEnd = Math.min(end, Math.max(pos, span.start));
      if (pos < plainEnd) {
        currentX +=
            drawTextSegmentWithFade(
                canvas,
                line,
                pos,
                plainEnd,
                currentX,
                y,
                segmentPaint,
                fadeStart,
                fadeEnd,
                fadeAlpha);
        pos = plainEnd;
      }

      int underlineStart = Math.max(pos, span.start);
      int underlineEnd = Math.min(end, span.end);
      if (underlineStart < underlineEnd) {
        float underlineXStart = currentX;
        currentX +=
            drawTextSegmentWithFade(
                canvas,
                line,
                underlineStart,
                underlineEnd,
                currentX,
                y,
                segmentPaint,
                fadeStart,
                fadeEnd,
                fadeAlpha);
        drawUnderlineSegmentWithFade(
            canvas,
            line,
            underlineStart,
            underlineEnd,
            underlineXStart,
            y,
            lineTop,
            lineBottom,
            segmentPaint,
            fadeStart,
            fadeEnd,
            fadeAlpha,
            span.isPath);
        pos = underlineEnd;
      }
    }

    if (pos < end) {
      currentX +=
          drawTextSegmentWithFade(
              canvas, line, pos, end, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
    }

    return currentX - x;
  }

  private float drawTextSegmentWithFade(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha) {
    if (start >= end) return 0f;
    boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
    if (hasFade && containsArabicScript(line, start, end)) {
      int spaceScale = view.getVisualSpaceScale();
      if (spaceScale > 1 || line.indexOf('\t', start) >= 0) {
        return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
      }
      canvas.drawText(line, start, end, x, y, segmentPaint);
      return segmentPaint.measureText(line, start, end);
    }
    final int spaceScale = view.getVisualSpaceScale();
    if (spaceScale > 1) {
      if (!hasFade || end <= fadeStart || start >= fadeEnd) {
        return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
      }

      float currentX = x;

      int beforeEnd = Math.min(end, fadeStart);
      if (start < beforeEnd) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
                canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
      }

      int fadeSegStart = Math.max(start, fadeStart);
      int fadeSegEnd = Math.min(end, fadeEnd);
      if (fadeSegStart < fadeSegEnd) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
                canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
      }

      int afterStart = Math.max(start, fadeEnd);
      if (afterStart < end) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
                canvas, line, afterStart, end, currentX, y, segmentPaint, 1f);
      }

      return currentX - x;
    }
    if (!hasFade || end <= fadeStart || start >= fadeEnd) {
      canvas.drawText(line, start, end, x, y, segmentPaint);
      return segmentPaint.measureText(line, start, end);
    }

    float currentX = x;

    int beforeEnd = Math.min(end, fadeStart);
    if (start < beforeEnd) {
      canvas.drawText(line, start, beforeEnd, currentX, y, segmentPaint);
      currentX += segmentPaint.measureText(line, start, beforeEnd);
    }

    int fadeSegStart = Math.max(start, fadeStart);
    int fadeSegEnd = Math.min(end, fadeEnd);
    if (fadeSegStart < fadeSegEnd) {
      view.charAnimationManager.getTempPaint().set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      view.charAnimationManager
          .getTempPaint()
          .setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      canvas.drawText(line, fadeSegStart, fadeSegEnd, currentX, y, view.charAnimationManager.getTempPaint());
      currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
      currentX += segmentPaint.measureText(line, afterStart, end);
    }

    return currentX - x;
  }

  private float drawTextSegmentWithVisualSpaces(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      float alphaMultiplier) {
    if (start >= end) return 0f;

    Paint drawPaint = segmentPaint;
    if (alphaMultiplier < 1f) {
      view.charAnimationManager.getTempPaint().set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      view.charAnimationManager
          .getTempPaint()
          .setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
      drawPaint = view.charAnimationManager.getTempPaint();
    }

    int len = end - start;
    float[] widths = view.whitespaceGuideManager.ensureMeasureWidthBuffer(len);
    segmentPaint.getTextWidths(line, start, end, widths);

    float currentX = x;
    int runStart = start;
    float runX = currentX;

    for (int i = 0; i < len; i++) {
      int charIndex = start + i;
      char c = line.charAt(charIndex);
      float adv =
          view.whitespaceGuideManager.getCharAdvanceWidth(
              c, widths[i], segmentPaint, WordWrapManager.DEFAULT_TAB_SIZE_SPACES);
      boolean isVirtualSpace = (c == ' ' || c == '\t');
      if (isVirtualSpace) {
        if (runStart < charIndex) {
          canvas.drawText(line, runStart, charIndex, runX, y, drawPaint);
        }
        currentX += adv;
        runStart = charIndex + 1;
        runX = currentX;
      } else {
        currentX += adv;
      }
    }

    if (runStart < end) {
      canvas.drawText(line, runStart, end, runX, y, drawPaint);
    }
    return currentX - x;
  }

  private boolean containsArabicScript(CharSequence text, int start, int end) {
    if (text == null || start >= end) return false;
    int safeStart = Math.max(0, start);
    int safeEnd = Math.min(text.length(), end);
    for (int i = safeStart; i < safeEnd; ) {
      int codePoint = Character.codePointAt(text, i);
      i += Character.charCount(codePoint);
      Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
      if (block == Character.UnicodeBlock.ARABIC
          || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
          || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
          || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
          || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
          || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS) {
        return true;
      }
    }
    return false;
  }
  public static class HighlightSpan {
    final int start;
    final int end;
    final Paint paint;

    HighlightSpan(int start, int end, Paint paint) {
      this.start = start;
      this.end = end;
      this.paint = paint;
    }
  }

  public static class UnderlineSpan {
    final int start;
    final int end;
    final boolean isPath;

    UnderlineSpan(int start, int end, boolean isPath) {
      this.start = start;
      this.end = end;
      this.isPath = isPath;
    }
  }

  public static class ErrorUnderlineSpan {
    final int start;
    final int end;

    ErrorUnderlineSpan(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  public static class LineParseResult {
    final List<HighlightSpan> spans;
    final boolean endsInBlockComment;
    final int endsInStringState;

    LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
      this.spans = spans;
      this.endsInBlockComment = endsInBlockComment;
      this.endsInStringState = endsInStringState;
    }
  }

  public static class HighlightLineState {
    final boolean inBlockComment;
    final int stringState;

    HighlightLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public enum HighlightRuleType {
    REGEX,
    STRING,
    BLOCK_COMMENT,
    LINE_COMMENT
  }

  public static class HighlightRule {
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
