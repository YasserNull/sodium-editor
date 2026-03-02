package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.state.HighlightLineState;
import com.yn.sodiumeditor.state.HighlightSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser class for syntax highlighting.
 * Handles parsing of lines for strings, comments, and other syntax elements.
 */
public class HighlightParser {

    public static final int STRING_STATE_DOUBLE = 1;
    public static final int STRING_STATE_SINGLE = 2;
    public static final int STRING_STATE_BACKTICK = 3;
    public static final int STRING_STATE_TRIPLE = 4;

    private final HighlightParserCallback callback;

    public interface HighlightParserCallback {
        boolean isTripleQuoteStringsEnabled();
        boolean isBacktickStringsEnabled();
        boolean isBlockCommentsEnabled();
        List<String> getLineCommentDelimiters();
        boolean isLineCommentStart(String line, int index);
    }

    public HighlightParser(HighlightParserCallback callback) {
        this.callback = callback;
    }

    public LineParseResult parseLineForSyntax(
            String line,
            boolean inBlockComment,
            int stringState,
            com.yn.sodiumeditor.core.HighlightRule stringRule,
            com.yn.sodiumeditor.core.HighlightRule blockCommentRule,
            boolean allowLineComment) {
        int length = line.length();
        List<HighlightSpan> spans = new ArrayList<>();
        int i = 0;

        while (i < length) {
            char c = line.charAt(i);

            if (inBlockComment) {
                int end = findBlockCommentEnd(line, i);
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

            if (allowLineComment && callback.getLineCommentDelimiters() != null && !callback.getLineCommentDelimiters().isEmpty()) {
                int lineCommentStart = findLineCommentStart(line, i);
                if (lineCommentStart >= 0) {
                    if (callback.isLineCommentStart(line, lineCommentStart)) {
                        HighlightRule lineCommentRule = getLineCommentRule();
                        if (lineCommentRule != null) {
                            spans.add(new HighlightSpan(lineCommentStart, length, lineCommentRule.paint));
                        }
                        return new LineParseResult(spans, false, 0);
                    }
                }
            }

            if (callback.isTripleQuoteStringsEnabled() && i + 2 < length) {
                if (line.startsWith("\"\"\"", i)) {
                    int end = findTripleQuoteEnd(line, i + 3);
                    if (end >= 0) {
                        if (stringRule != null) spans.add(new HighlightSpan(i, end + 3, stringRule.paint));
                        i = end + 3;
                        continue;
                    }
                    if (stringRule != null) spans.add(new HighlightSpan(i, length, stringRule.paint));
                    return new LineParseResult(spans, false, STRING_STATE_TRIPLE);
                }
            }

            if (callback.isBacktickStringsEnabled() && c == '`') {
                int end = findStringEnd(line, i + 1, '`');
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

            if (callback.isBlockCommentsEnabled() && i + 1 < length && c == '/' && line.charAt(i + 1) == '*') {
                int end = findBlockCommentEnd(line, i + 2);
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

    private HighlightRule getLineCommentRule() {
        // This should be provided by the callback or manager
        return null;
    }

    private int findLineCommentStart(String line, int from) {
        List<String> delimiters = callback.getLineCommentDelimiters();
        if (delimiters == null || delimiters.isEmpty()) return -1;
        int len = line.length();
        for (int i = Math.max(0, from); i < len; i++) {
            if (callback.isLineCommentStart(line, i)) return i;
        }
        return -1;
    }

    public static int findBlockCommentEnd(String line, int start) {
        for (int i = start; i + 1 < line.length(); i++) {
            if (line.charAt(i) == '*' && line.charAt(i + 1) == '/' && !isTokenEscaped(line, i)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isEscaped(String line, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (line.charAt(i) != '\\') break;
            backslashes++;
        }
        return (backslashes % 2) == 1;
    }

    public static boolean isTokenEscaped(String line, int index) {
        if (isEscaped(line, index)) return true;
        int next = index + 1;
        return next < line.length() && isEscaped(line, next);
    }

    public static int findStringEnd(String line, int start, char delimiter) {
        for (int i = start; i < line.length(); i++) {
            if (line.charAt(i) == delimiter && !isEscaped(line, i)) {
                return i;
            }
        }
        return -1;
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

    public StringEndResult findStringEndForState(String line, int start, int state) {
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

    public static boolean hasOverlap(HighlightSpan span, List<HighlightSpan> spans) {
        for (HighlightSpan other : spans) {
            if (span.start < other.end && span.end > other.start) return true;
        }
        return false;
    }

    public static class LineParseResult {
        public final List<HighlightSpan> spans;
        public final boolean endsInBlockComment;
        public final int endsInStringState;

        public LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
            this.spans = spans;
            this.endsInBlockComment = endsInBlockComment;
            this.endsInStringState = endsInStringState;
        }
    }

    public static class StringEndResult {
        public final boolean found;
        public final int endIndex;

        public StringEndResult(boolean found, int endIndex) {
            this.found = found;
            this.endIndex = endIndex;
        }
    }

    /**
     * Checks if the given index is the start of a line comment.
     * @param line the line text
     * @param index the index to check
     * @param lineCommentDelimiters the line comment delimiters
     * @return true if the index is the start of a line comment
     */
    public static boolean isLineCommentStart(String line, int index, List<String> lineCommentDelimiters) {
        if (line == null || lineCommentDelimiters == null || index < 0 || index >= line.length()) return false;
        for (String token : lineCommentDelimiters) {
            if (token == null || token.isEmpty()) continue;
            int len = token.length();
            if (index + len > line.length()) continue;
            if (len == 1) {
                if (line.charAt(index) == token.charAt(0) && !isTokenEscaped(line, index)) {
                    return true;
                }
            } else {
                if (line.regionMatches(index, token, 0, len) && !isTokenEscaped(line, index)) {
                    return true;
                }
            }
        }
        return false;
    }
}
