package com.yn.sodiumeditor.core.highlight;

import android.graphics.Canvas;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.core.view.View;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighliteRender;
import com.yn.sodiumeditor.renderer.HighlightCacheManager;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Main facade for syntax highlighting in SodiumEditor.
 */
public class Highlite {
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

    // --- Syntax Highlighting State ---
    // Deprecated: Use highlite instead
    @Deprecated public java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();
    @Deprecated @Nullable public HighliteRender.HighlightRule lineCommentHighlightRule;
    @Deprecated public List<HighliteRender.HighlightRule> highlightRules = new ArrayList<>();
    @Deprecated public HighliteRender.HighlightRule stringHighlightRule;
    @Deprecated public HighliteRender.HighlightRule blockCommentHighlightRule;
    @Deprecated public ArrayList<HighliteRender.HighlightRule> regexHighlightRules = new ArrayList<>();
    @Deprecated public LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<HighliteRender.HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
    @Deprecated public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
    @Deprecated public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

    public Highlite(SodiumEditor editor) {
        FunctionLog.f("Highlite", "Highlite", editor);
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
        FunctionLog.f("Highlite", "markTyping");
        lastTypingMs = android.os.SystemClock.uptimeMillis();
    }
  
    public void maybeEnsureHighlightCacheForRange(
        int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
        FunctionLog.f("Highlite", "maybeEnsureHighlightCacheForRange", startLine, endLine, directLines);
        if (startLine > endLine) return;
        int v = editor.editOperators.editVersion.get();
        long now = android.os.SystemClock.uptimeMillis();
        if (v != lastHighlightEnsureEditVersion
            && (now - lastTypingMs) < HIGHLIGHT_TYPING_WINDOW_MS) {
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
        FunctionLog.f("Highlite", "invalidateHighlightEnsureRange");
        long now = android.os.SystemClock.uptimeMillis();
        if ((now - lastHighlightInvalidateMs) < HIGHLIGHT_INVALIDATE_THROTTLE_MS) {
            return;
        }
        lastHighlightEnsureStartLine = -1;
        lastHighlightEnsureEndLine = -1;
        lastHighlightEnsureEditVersion = -1;
        lastHighlightInvalidateMs = now;
        if (SodiumEditor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("SodiumRender", "highlightEnsureInvalidate");
        }
    }
  
    private void syncRulesFromComponent() {
        FunctionLog.f("Highlite", "syncRulesFromComponent");
        stringHighlightRule = rules.stringHighlightRule;
        blockCommentHighlightRule = rules.blockCommentHighlightRule;
        lineCommentHighlightRule = rules.lineCommentHighlightRule;
    }

    private void syncRulesToComponent() {
        FunctionLog.f("Highlite", "syncRulesToComponent");
        rules.stringHighlightRule = stringHighlightRule;
        rules.blockCommentHighlightRule = blockCommentHighlightRule;
        rules.lineCommentHighlightRule = lineCommentHighlightRule;
    }

    public void addHighlightRule(String regex, int style, int color) { addHighlightRule(regex, style, color, false); }
    public void addHighlightRule(String regex, int style, int color, boolean underline) { 
        FunctionLog.f("Highlite", "addHighlightRule", regex, style, color, underline);
        rules.addHighlightRule(regex, style, color, underline); 
        syncRulesFromComponent();
    }
    public void clearHighlightRules() {
        FunctionLog.f("Highlite", "clearHighlightRules");
        rules.clearHighlightRules(); syncRulesFromComponent();
    }
    
    public void setLineCommentDelimiter(String d, int s, int c) {
        FunctionLog.f("Highlite", "setLineCommentDelimiter", d, s, c);
        syncRulesToComponent();
        if (d == null || d.isEmpty()) { if (rules.lineCommentHighlightRule != null) { rules.lineCommentHighlightRule = null; clearHighlightCaches(); } syncRulesFromComponent(); return; }
        rules.lineCommentHighlightRule = new HighliteRender.HighlightRule("", s, c, editor.textRender.paint.getTextSize(), editor.textRender.paint.getTypeface(), false, HighliteRender.HighlightRuleType.LINE_COMMENT);
        clearHighlightCaches();
        syncRulesFromComponent();
    }

    public void setStringHighlightColor(int color) {
        FunctionLog.f("Highlite", "setStringHighlightColor", color);
        syncRulesToComponent();
        if (rules.stringHighlightRule == null) addHighlightRule(RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
        else { rules.stringHighlightRule.paint.setColor(color); clearHighlightCaches(); }
        syncRulesFromComponent();
    }

    public void setBlockCommentHighlight(int style, int color) {
        FunctionLog.f("Highlite", "setBlockCommentHighlight", style, color);
        syncRulesToComponent();
        rules.blockCommentHighlightRule = new HighliteRender.HighlightRule("", style, color, editor.textRender.paint.getTextSize(), editor.textRender.paint.getTypeface(), false, HighliteRender.HighlightRuleType.BLOCK_COMMENT);
        clearHighlightCaches();
        syncRulesFromComponent();
    }

    public void onTextSizeChanged(float size) {
        FunctionLog.f("Highlite", "onTextSizeChanged", size);
        syncRulesToComponent();
        for (HighliteRender.HighlightRule r : rules.highlightRules) r.updateTextSize(size);
        if (rules.lineCommentHighlightRule != null) rules.lineCommentHighlightRule.updateTextSize(size);
        if (rules.whitespaceStringRule != null) rules.whitespaceStringRule.updateTextSize(size);
        if (rules.whitespaceCommentRule != null) rules.whitespaceCommentRule.updateTextSize(size);
        clearHighlightCaches();
        syncRulesFromComponent();
    }

    public void onTypefaceChanged(Typeface tf) {
        FunctionLog.f("Highlite", "onTypefaceChanged", tf);
        syncRulesToComponent();
        if (rules.lineCommentHighlightRule != null) rules.lineCommentHighlightRule.updateTypeface(tf);
        for (HighliteRender.HighlightRule r : rules.highlightRules) r.updateTypeface(tf);
        clearHighlightCaches();
        syncRulesFromComponent();
    }

    // --- Instance Bridges ---
    public HighliteRender.HighlightLineState getLineStateAtStart(int gl) {
        FunctionLog.f("Highlite", "getLineStateAtStart", gl);
        return cache.getLineStateAtStart(gl);
    }
    public boolean isLineCommentStart(String line, int idx) {
        FunctionLog.f("Highlite", "isLineCommentStart", line, idx);
        return parser.isLineCommentStart(line, idx);
    }
    public boolean isStringDelimiter(char c) {
        FunctionLog.f("Highlite", "isStringDelimiter", c);
        return parser.isStringDelimiter(c);
    }
    public boolean isTripleQuoteStart(String line, int idx) {
        FunctionLog.f("Highlite", "isTripleQuoteStart", line, idx);
        return parser.isTripleQuoteStart(line, idx);
    }
    public int getStringStateForDelimiter(char c) {
        FunctionLog.f("Highlite", "getStringStateForDelimiter", c);
        return parser.getStringStateForDelimiter(c);
    }
    public com.yn.sodiumeditor.core.StringEndResult findStringEndForState(String line, int start, int state) {
        FunctionLog.f("Highlite", "findStringEndForState", line, start, state);
        return parser.findStringEndForState(line, start, state);
    }

    public HighliteRender.LineParseResult parseLineForSyntax(String line, boolean inBlock, int strState, HighliteRender.HighlightRule strRule, HighliteRender.HighlightRule blockRule, boolean collectSpans) {
        FunctionLog.f("Highlite", "parseLineForSyntax", line, inBlock, strState, strRule, blockRule, collectSpans);
        return parser.parseLineForSyntax(line, inBlock, strState, strRule, blockRule, collectSpans);
    }

    // --- Static Bridges (Delegating to HighlightUtils) ---
    public static boolean isEscaped(String line, int index) { return com.yn.sodiumeditor.utils.HighlightUtils.isEscaped(line, index); }
    public static boolean isTokenEscaped(String line, int index) { return com.yn.sodiumeditor.utils.HighlightUtils.isTokenEscaped(line, index); }
    public static int findStringEnd(String line, int start, char delimiter) { return com.yn.sodiumeditor.utils.HighlightUtils.findStringEnd(line, start, delimiter); }
    public static int findTripleQuoteEnd(String line, int start) { return com.yn.sodiumeditor.utils.HighlightUtils.findTripleQuoteEnd(line, start); }
    public static int findBlockCommentEnd(String line, int start) { return com.yn.sodiumeditor.utils.HighlightUtils.findBlockCommentEnd(line, start); }

    public List<HighliteRender.HighlightSpan> getHighlightSpansForLine(String line, int gl) {
        FunctionLog.f("Highlite", "getHighlightSpansForLine", line, gl);
        if (editor.view.getLogicalLineLength(gl, line) > editor.highliteRender.maxSyntaxLineLength) return new ArrayList<>();
        List<HighliteRender.HighlightSpan> spans = cache.highlightCache.get(gl);
        if (spans == null) { spans = calculateSpansForLine(line, gl); cache.highlightCache.put(gl, spans); }
        return spans;
    }

    public List<HighliteRender.HighlightSpan> calculateSpansForLine(String line, int gl) {
        FunctionLog.f("Highlite", "calculateSpansForLine", line, gl);
        syncRulesToComponent();
        List<HighliteRender.HighlightSpan> spans = new ArrayList<>();
        if (editor.view.getLogicalLineLength(gl, line) > editor.highliteRender.maxSyntaxLineLength || rules.highlightRules.isEmpty()) return spans;

        HighliteRender.HighlightLineState sState = cache.getLineStateAtStart(gl);
        HighliteRender.HighlightRule sRule = rules.stringHighlightRule != null ? rules.stringHighlightRule : rules.whitespaceStringRule;
        HighliteRender.HighlightRule bRule = rules.blockCommentHighlightRule != null ? rules.blockCommentHighlightRule : rules.whitespaceCommentRule;

        HighliteRender.LineParseResult res = parser.parseLineForSyntax(line, sState.inBlockComment, sState.stringState, sRule, bRule, true);
        spans.addAll(res.spans);

        if (!rules.regexHighlightRules.isEmpty() && line != null && !line.isEmpty()) {
            for (HighliteRender.HighlightRule rule : rules.regexHighlightRules) {
                java.util.regex.Matcher m = rule.pattern.matcher(line);
                while (m.find()) {
                    HighliteRender.HighlightSpan span = new HighliteRender.HighlightSpan(m.start(), m.end(), rule.paint);
                    if (!com.yn.sodiumeditor.utils.HighlightUtils.hasOverlap(span, spans)) spans.add(span);
                }
            }
        }
        if (spans.size() > 1) java.util.Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
        return spans;
    }

    public void clearHighlightCaches() {
        FunctionLog.f("Highlite", "clearHighlightCaches");
        cache.highlightCache.clear();
        cache.blockCommentEndStateCache.clear();
        cache.stringEndStateCache.clear();
        editor.colorCodeHighlight.clearColorCodeCaches();
        editor.urlUnderline.clearUrlUnderlineCache();
        editor.pathUnderline.clearPathUnderlineCache();
        invalidateHighlightEnsureRange();
    }

    public void invalidateHighlightCacheForLine(int line) {
        FunctionLog.f("Highlite", "invalidateHighlightCacheForLine", line);
        cache.highlightCache.remove(line);
        editor.colorCodeHighlight.clearColorCodeCacheForLine(line);
        editor.urlUnderline.clearUrlUnderlineCacheForLine(line);
        editor.pathUnderline.clearPathUnderlineCacheForLine(line);
        invalidateHighlightEnsureRange();
    }

    public void setStringsHighlight(boolean enabled, int color) {
        FunctionLog.f("Highlite", "setStringsHighlight", enabled, color);
        if (stringHighlightRule == null) {
            addHighlightRule(Highlite.RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
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
        FunctionLog.f("Highlite", "setMultiLineStringsHighlight", enabled, color);
        if (stringHighlightRule == null) {
            addHighlightRule(Highlite.RULE_STRING, com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL, color);
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
        FunctionLog.f("Highlite", "setBacktickStringsEnabled", enabled);
        if (isBacktickStringsEnabled == enabled) return;
        isBacktickStringsEnabled = enabled;
        clearHighlightCaches();
        editor.invalidate();
    }

    public void setMultiLineComments(boolean enabled, int style, int color) {
        FunctionLog.f("Highlite", "setMultiLineComments", enabled, style, color);
        boolean needsInvalidate = false;
        if (blockCommentHighlightRule == null || blockCommentHighlightRule.style != style) {
            if (blockCommentHighlightRule != null) {
                highlightRules.remove(blockCommentHighlightRule);
            }
            blockCommentHighlightRule =
                new HighliteRender.HighlightRule(
                    Highlite.RULE_BLOCK_COMMENT,
                    style,
                    color,
                    editor.textRender.paint.getTextSize(),
                    editor.textRender.paint.getTypeface(),
                    false,
                    HighliteRender.HighlightRuleType.BLOCK_COMMENT);
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

    public void setSingleLineCommentDelimiters(String... delimiters) {
        FunctionLog.f("Highlite", "setSingleLineCommentDelimiters", (Object) delimiters);
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
        FunctionLog.f("Highlite", "ensureLineCommentDelimiter", delimiter);
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
        FunctionLog.f("Highlite", "setSingleLineCommentsHighlight", enabled, style, color);
        if (!enabled) {
            if (lineCommentHighlightRule != null) {
                lineCommentHighlightRule = null;
                clearHighlightCaches();
                editor.invalidate();
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
        } else {
            lineCommentHighlightRule.paint.setColor(color);
        }
        clearHighlightCaches();
        editor.invalidate();
    }

    public void setSingleLineCommentSyntax(
        boolean enabled, int style, int color, String... delimiters) {
        FunctionLog.f("Highlite", "setSingleLineCommentSyntax", enabled, style, color, (Object) delimiters);
        setSingleLineCommentDelimiters(delimiters);
        setSingleLineCommentsHighlight(enabled, style, color);
    }

    public void setTripleQuoteStringsEnabled(boolean enabled) {
        FunctionLog.f("Highlite", "setTripleQuoteStringsEnabled", enabled);
        if (isTripleQuoteStringsEnabled == enabled) return;
        isTripleQuoteStringsEnabled = enabled;
        clearHighlightCaches();
        editor.invalidate();
    }

    public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
        FunctionLog.f("Highlite", "measureHighlightedSegmentWidth", line, globalLine, start, end);
        if (line == null || line.isEmpty() || start >= end) return 0f;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return 0f;

        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
            int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
            if (spans != null && spans.length > 0) {
                float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
                float x1 = editor.binaryRender.getXForCharBinary(line, start, editor.textRender.paint, spans, padX);
                float x2 = editor.binaryRender.getXForCharBinary(line, end,   editor.textRender.paint, spans, padX);
                return x2 - x1;
            }
        }

        if (rules.highlightRules.isEmpty()) {
            return editor.textRender.paint.measureText(line, start, end);
        }

        List<HighliteRender.HighlightSpan> spans = cache.highlightCache.get(globalLine);
        if (spans == null) {
            spans = calculateSpansForLine(line, globalLine);
            cache.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            return editor.textRender.paint.measureText(line, start, end);
        }

        float total = 0f;
        int lastEnd = start;

        for (HighliteRender.HighlightSpan span : spans) {
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
        FunctionLog.f("Highlite", "drawHighlightedSegment", canvas, line, globalLine, start, end, x, y);
        if (line == null || line.isEmpty() || start >= end) return;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return;

        if (rules.highlightRules.isEmpty()) {
            editor.textRender.paint.setUnderlineText(false);
            canvas.drawText(line, start, end, x, y, editor.textRender.paint);
            return;
        }

        List<HighliteRender.HighlightSpan> spans = cache.highlightCache.get(globalLine);
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

        for (HighliteRender.HighlightSpan span : spans) {
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
        FunctionLog.f("Highlite", "measureTextInRange", line, start, end, globalLine);
        if (line == null || start >= end) return 0f;
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
            int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
            if (spans != null && spans.length > 0) {
                float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
                float x1 = editor.binaryRender.getXForCharBinary(line, start, editor.textRender.paint, spans, padX);
                float x2 = editor.binaryRender.getXForCharBinary(line, end,   editor.textRender.paint, spans, padX);
                return x2 - x1;
            }
        }
        return measureHighlightedSegmentWidth(line, globalLine, start, end);
    }
}
