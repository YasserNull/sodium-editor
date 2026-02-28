package com.yn.sodiumeditor.state;

import android.graphics.Paint;
import android.graphics.Path;
import com.yn.sodiumeditor.core.HighlightRule;
import com.yn.sodiumeditor.core.UnderlineSpan;
import com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * State class for highlight functionality.
 * Stores highlight rules, caches, and underline state.
 */
public class HighlightState {

    public static final String RULE_STRING = "__STRING__";
    public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";
    public static final String RULE_LINE_COMMENT = "__LINE_COMMENT__";
    public static final int STRING_STATE_DOUBLE = 1;
    public static final int STRING_STATE_SINGLE = 2;
    public static final int STRING_STATE_BACKTICK = 3;
    public static final int STRING_STATE_TRIPLE = 4;

    public static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");
    public static final Pattern COLOR_HEX_PATTERN =
            Pattern.compile(
                    "(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))\\b|(\\b0x[a-fA-F0-9]{6,8}\\b)",
                    Pattern.CASE_INSENSITIVE);
    public final Paint colorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Highlight rules
    public final List<String> lineCommentDelimiters = new ArrayList<>();
    public HighlightRule lineCommentHighlightRule;
    public final List<HighlightRule> highlightRules = new ArrayList<>();
    public HighlightRule stringHighlightRule;
    public HighlightRule blockCommentHighlightRule;
    public final List<HighlightRule> regexHighlightRules = new ArrayList<>();

    // Highlight caches
    public final LinkedHashMap<Integer, List<HighlightSpan>> highlightCache =
            new LinkedHashMap<Integer, List<HighlightSpan>>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<HighlightSpan>> eldest) {
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

    public final LinkedHashMap<Integer, int[]> colorCodeBgCache =
            new LinkedHashMap<Integer, int[]>(600, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
                    return size() > 600;
                }
            };

    // URL underline state
    public boolean isUrlUnderliningEnabled = false;
    public Pattern urlUnderlinePattern = DEFAULT_URL_UNDERLINE_PATTERN;
    public final Paint urlUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final LinkedHashMap<Integer, List<UnderlineSpan>> urlUnderlineCache =
            new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
                    return size() > 1000;
                }
            };

    // Path underline state
    public boolean isPathUnderliningEnabled = false;
    public Pattern pathUnderlinePattern = Pattern.compile("/[^\\\\s,;()'\\\"]+");
    public final Paint pathUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final LinkedHashMap<Integer, List<UnderlineSpan>> pathUnderlineCache =
            new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
                    return size() > 1000;
                }
            };
    public final ConcurrentHashMap<String, Boolean> pathValidationCache = new ConcurrentHashMap<>();
    public final Set<String> pendingPathValidations =
            Collections.synchronizedSet(new HashSet<>());

    // Error underline state
    public int errorUnderlineColor = 0xFFE53935;
    public boolean errorUnderlineEnabled = true;
    public final Paint errorUnderlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Path errorUnderlinePath = new Path();
    public float errorUnderlineHeightScale = 0.18f;
    public float errorUnderlineWaveLengthScale = 0.70f;
    public float errorUnderlineStrokeScale = 0.08f;
    public float errorUnderlineSmoothness = 3f;
    public final LinkedHashMap<Integer, List<com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan>> errorUnderlineMap =
            new LinkedHashMap<Integer, List<com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan>>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan>> eldest) {
                    return size() > 2000;
                }
            };

    // Color highlight state
    public boolean isColorHighlightingEnabled = false;

    // Current line highlight
    public boolean highlightCurrentLine = true;
    public int currentLineHighlightColor = 0x202196F3;
    public final Paint currentLinePaint = new Paint();

    // Syntax settings
    public boolean isMultiLineStringsEnabled = false;
    public boolean isBacktickStringsEnabled = false;
    public boolean isBlockCommentsEnabled = false;
    public boolean isTripleQuoteStringsEnabled = false;
    public int maxSyntaxLineLength = 4096;

    // Ensure range tracking
    public int lastHighlightEnsureStartLine = -1;
    public int lastHighlightEnsureEndLine = -1;
    public int lastHighlightEnsureEditVersion = -1;

    public HighlightState() {
        currentLinePaint.setColor(currentLineHighlightColor);
    }

    public void clearHighlightCaches() {
        highlightCache.clear();
        blockCommentEndStateCache.clear();
        stringEndStateCache.clear();
        colorCodeBgCache.clear();
        urlUnderlineCache.clear();
        pathUnderlineCache.clear();
        resetEnsureRange();
    }

    public void resetEnsureRange() {
        lastHighlightEnsureStartLine = -1;
        lastHighlightEnsureEndLine = -1;
        lastHighlightEnsureEditVersion = -1;
    }

    public void invalidateLine(int line) {
        highlightCache.remove(line);
        colorCodeBgCache.remove(line);
        urlUnderlineCache.remove(line);
        pathUnderlineCache.remove(line);
    }

    public int getStringStateForDelimiter(char delimiter) {
        if (delimiter == '"') return STRING_STATE_DOUBLE;
        if (delimiter == '\'') return STRING_STATE_SINGLE;
        return STRING_STATE_BACKTICK;
    }

    public void setHighlightCurrentLine(boolean enabled) {
        highlightCurrentLine = enabled;
    }

    public void setMaxSyntaxLineLength(int maxChars) {
        maxSyntaxLineLength = Math.max(512, maxChars);
    }

    public void setBacktickStringsEnabled(boolean enabled) {
        isBacktickStringsEnabled = enabled;
    }

    public void setTripleQuoteStringsEnabled(boolean enabled) {
        isTripleQuoteStringsEnabled = enabled;
    }

    public void setCurrentLineHighlightColor(int color) {
        currentLineHighlightColor = color;
        currentLinePaint.setColor(color);
    }

    public void setMultiLineComments(boolean enabled, int style, int color) {
        isBlockCommentsEnabled = enabled;
        if (blockCommentHighlightRule != null) {
            blockCommentHighlightRule.style = style;
            blockCommentHighlightRule.paint.setColor(color);
        }
    }

    public void setStringsHighlight(boolean enabled, int color) {
        isMultiLineStringsEnabled = enabled;
        if (stringHighlightRule != null) {
            stringHighlightRule.paint.setColor(color);
        }
    }

    public void setMultiLineStringsHighlight(boolean enabled, int color) {
        isMultiLineStringsEnabled = enabled;
        if (stringHighlightRule != null) {
            stringHighlightRule.paint.setColor(color);
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
    }

    public void setSingleLineCommentsHighlight(boolean enabled, int style, int color) {
        if (!enabled) {
            lineCommentHighlightRule = null;
            return;
        }
        if (lineCommentHighlightRule == null) {
            lineCommentHighlightRule = new com.yn.sodiumeditor.core.HighlightRule(
                "", style, color, 14f, android.graphics.Typeface.MONOSPACE, false,
                com.yn.sodiumeditor.core.HighlightRule.HighlightRuleType.LINE_COMMENT);
        } else {
            lineCommentHighlightRule.style = style;
            lineCommentHighlightRule.paint.setColor(color);
        }
    }

    public void setSingleLineCommentSyntax(boolean enabled, int style, int color, String... delimiters) {
        setSingleLineCommentDelimiters(delimiters);
        setSingleLineCommentsHighlight(enabled, style, color);
    }

    public void ensureLineCommentDelimiter(String delimiter) {
        if (delimiter == null) return;
        String trimmed = delimiter.trim();
        if (trimmed.isEmpty()) return;
        if (!lineCommentDelimiters.contains(trimmed)) {
            lineCommentDelimiters.add(trimmed);
            lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
        }
    }

    public void clearHighlightRules() {
        highlightRules.clear();
        stringHighlightRule = null;
        blockCommentHighlightRule = null;
        regexHighlightRules.clear();
        lineCommentHighlightRule = null;
    }

    public void addHighlightRule(String regex, int style, int color) {
        addHighlightRule(regex, style, color, false);
    }

    public void addHighlightRule(String regex, int style, int color, boolean underline) {
        // Simplified - actual implementation needs type detection
        HighlightRule rule = new HighlightRule(
            regex, style, color, 14f, android.graphics.Typeface.MONOSPACE, underline,
            com.yn.sodiumeditor.core.HighlightRule.HighlightRuleType.REGEX);
        regexHighlightRules.add(rule);
    }

    public void invalidateHighlightCacheForLine(int line) {
        invalidateLine(line);
        blockCommentEndStateCache.clear();
        stringEndStateCache.clear();
    }

    public void drawColorCodeBackgrounds(android.graphics.Canvas canvas, String line, int globalLine, float lineTop, float lineBottom) {
        // Simplified - actual implementation in HighlightRenderer
    }

    public java.util.List<com.yn.sodiumeditor.state.HighlightSpan> calculateSpansForLine(String line, int globalLine) {
        // Simplified - actual implementation in HighlightRenderer
        return new java.util.ArrayList<>();
    }

    public void ensureHighlightCacheForVisibleRange(int firstVisibleLine, int lastVisibleLine, java.util.HashMap<Integer, String> directLines) {
        // Simplified - actual implementation in HighlightRenderer
    }

    public void maybeEnsureHighlightCacheForRange(int startLine, int endLine, java.util.HashMap<Integer, String> directLines) {
        // Simplified
    }

    public com.yn.sodiumeditor.state.HighlightLineState getLineStateAtStart(int globalLine) {
        // Simplified - actual implementation in HighlightRenderer
        return new com.yn.sodiumeditor.state.HighlightLineState(false, 0);
    }

    public boolean isStringDelimiter(char c) {
        if (c == '"') return true;
        if (c == '\'') return true;
        return c == '`' && isBacktickStringsEnabled;
    }

    public boolean isLineCommentStart(String line, int index) {
        if (index < 0 || index >= line.length()) return false;
        for (String token : lineCommentDelimiters) {
            int len = token.length();
            if (len == 0) continue;
            if (index + len > line.length()) continue;
            if (len == 1) {
                if (line.charAt(index) == token.charAt(0) && !com.yn.sodiumeditor.core.HighlightParser.isTokenEscaped(line, index)) {
                    return true;
                }
            } else {
                if (line.regionMatches(index, token, 0, len) && !com.yn.sodiumeditor.core.HighlightParser.isTokenEscaped(line, index)) {
                    return true;
                }
            }
        }
        return false;
    }
}
