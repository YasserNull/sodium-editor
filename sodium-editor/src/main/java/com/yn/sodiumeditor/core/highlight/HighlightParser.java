package com.yn.sodiumeditor.core.highlight;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StringEndResult;
import com.yn.sodiumeditor.renderer.HighliteRender;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Core engine for parsing lines into highlight spans.
 */
public class HighlightParser {
    private final SodiumEditor editor;
    private final Highlite highlite;

    public HighlightParser(SodiumEditor editor, Highlite highlite) {
        this.editor = editor;
        this.highlite = highlite;
    }

    public HighliteRender.LineParseResult parseLineForSyntax(
            String line, boolean inBlock, int strState,
            HighliteRender.HighlightRule strRule, HighliteRender.HighlightRule blockRule,
            boolean collectSpans) {
        
        List<HighliteRender.HighlightSpan> spans = new ArrayList<>();
        int len = line.length();
        int i = 0;
        
        if (!highlite.isBlockCommentsEnabled) inBlock = false;
        if (strState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_BACKTICK && !highlite.isBacktickStringsEnabled) strState = 0;
        if (strState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE && !highlite.isTripleQuoteStringsEnabled) strState = 0;
        if (strState != 0 && !highlite.isMultiLineStringsEnabled && strState != com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE) strState = 0;

        while (i < len) {
            if (inBlock) {
                int end = highlite.findConfiguredBlockCommentEnd(line, i);
                if (end < 0) {
                    if (collectSpans && blockRule != null && highlite.isBlockCommentsEnabled && len > 0) spans.add(new HighliteRender.HighlightSpan(0, len, blockRule.paint));
                    return new HighliteRender.LineParseResult(spans, true, 0);
                }
                int blockEnd = end + highlite.blockCommentEndDelimiter.length();
                if (collectSpans && blockRule != null && highlite.isBlockCommentsEnabled) spans.add(new HighliteRender.HighlightSpan(0, blockEnd, blockRule.paint));
                i = blockEnd; inBlock = false; continue;
            }

            if (strState != 0) {
                StringEndResult res = findStringEndForState(line, i, strState);
                if (res.found) {
                    if (collectSpans && strRule != null) spans.add(new HighliteRender.HighlightSpan(0, res.endIndex, strRule.paint));
                    i = res.endIndex; strState = 0; continue;
                }
                if (collectSpans && strRule != null && len > 0) spans.add(new HighliteRender.HighlightSpan(0, len, strRule.paint));
                return new HighliteRender.LineParseResult(spans, false, strState);
            }

            if (isLineCommentStart(line, i)) {
                if (collectSpans && len > i) {
                    Paint p = (highlite.rules.lineCommentHighlightRule != null) ? highlite.rules.lineCommentHighlightRule.paint : ((blockRule != null) ? blockRule.paint : editor.textRender.paint);
                    spans.add(new HighliteRender.HighlightSpan(i, len, p));
                }
                return new HighliteRender.LineParseResult(spans, false, 0);
            }

            char c = line.charAt(i);
            if (isTripleQuoteStart(line, i) && !HighlightUtils.isEscaped(line, i)) {
                int end = HighlightUtils.findTripleQuoteEnd(line, i + 3);
                if (end >= 0) {
                    if (collectSpans && strRule != null) spans.add(new HighliteRender.HighlightSpan(i, end + 3, strRule.paint));
                    i = end + 3; continue;
                }
                if (highlite.isTripleQuoteStringsEnabled) {
                    if (collectSpans && strRule != null && len > 0) spans.add(new HighliteRender.HighlightSpan(i, len, strRule.paint));
                    return new HighliteRender.LineParseResult(spans, false, com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE);
                }
            }

            if (isStringDelimiter(c) && !HighlightUtils.isEscaped(line, i)) {
                int end = HighlightUtils.findStringEnd(line, i + 1, c);
                if (end >= 0) {
                    if (collectSpans && strRule != null) spans.add(new HighliteRender.HighlightSpan(i, end + 1, strRule.paint));
                    i = end + 1; continue;
                }
                if (highlite.isMultiLineStringsEnabled) {
                    if (collectSpans && strRule != null && len > 0) spans.add(new HighliteRender.HighlightSpan(i, len, strRule.paint));
                    return new HighliteRender.LineParseResult(spans, false, getStringStateForDelimiter(c));
                }
            }

            if (highlite.isBlockCommentsEnabled && highlite.isConfiguredBlockCommentStart(line, i)) {
                int end = highlite.findConfiguredBlockCommentEnd(line, i + highlite.blockCommentStartDelimiter.length());
                if (end < 0) {
                    if (collectSpans && blockRule != null && len > 0) spans.add(new HighliteRender.HighlightSpan(i, len, blockRule.paint));
                    return new HighliteRender.LineParseResult(spans, true, 0);
                }
                int blockEnd = end + highlite.blockCommentEndDelimiter.length();
                if (collectSpans && blockRule != null) spans.add(new HighliteRender.HighlightSpan(i, blockEnd, blockRule.paint));
                i = blockEnd; continue;
            }
            i++;
        }
        return new HighliteRender.LineParseResult(spans, inBlock, strState);
    }

    public boolean isLineCommentStart(String line, int idx) {
        if (idx < 0 || idx >= line.length() || highlite.rules.lineCommentDelimiters.isEmpty()) return false;
        for (String token : highlite.rules.lineCommentDelimiters) {
            int tLen = token.length();
            if (tLen == 0 || idx + tLen > line.length()) continue;
            if (line.regionMatches(idx, token, 0, tLen) && !HighlightUtils.isTokenEscaped(line, idx)) return true;
        }
        return false;
    }

    public boolean isStringDelimiter(char c) {
        return c == '"' || c == '\'' || (c == '`' && highlite.isBacktickStringsEnabled);
    }

    public boolean isTripleQuoteStart(String line, int idx) {
        return highlite.isTripleQuoteStringsEnabled && idx + 2 < line.length() && line.charAt(idx) == '"' && line.charAt(idx + 1) == '"' && line.charAt(idx + 2) == '"';
    }

    public int getStringStateForDelimiter(char c) {
        if (c == '"') return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_DOUBLE;
        if (c == '\'') return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_SINGLE;
        return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_BACKTICK;
    }

    public StringEndResult findStringEndForState(String line, int start, int state) {
        if (state == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE) {
            int end = HighlightUtils.findTripleQuoteEnd(line, start);
            return new StringEndResult(end >= 0, end >= 0 ? end + 3 : start);
        }
        char d = (state == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_SINGLE) ? '\'' : (state == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_BACKTICK ? '`' : '"');
        int end = HighlightUtils.findStringEnd(line, start, d);
        return new StringEndResult(end >= 0, end >= 0 ? end + 1 : start);
    }
}
