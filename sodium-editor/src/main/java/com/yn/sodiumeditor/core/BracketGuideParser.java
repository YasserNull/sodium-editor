package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.state.BracketGuideLineState;
import com.yn.sodiumeditor.state.BracketGuideState;
import com.yn.sodiumeditor.state.BracketGuideToken;
import com.yn.sodiumeditor.state.HighlightLineState;
import com.yn.sodiumeditor.renderer.BracketGuideRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser class for bracket guides.
 * Handles parsing of lines to find bracket positions.
 */
public class BracketGuideParser {

    private final SodiumEditorView view;
    private final BracketGuideState state;
    private final BracketGuideRenderer renderer;

    public BracketGuideParser(SodiumEditorView view, BracketGuideState state, BracketGuideRenderer renderer) {
        this.view = view;
        this.state = state;
        this.renderer = renderer;
    }

    public void ensureCacheForWindow(java.util.Map<Integer, String> directLines) {
        int start = view.getWindowStartLineForBracket();
        int end = view.getWindowEndLineForBracket();
        if (start < 0 || end < start) {
            state.invalidateCache();
            return;
        }
        int v = view.getEditVersionForBracket();
        int cfg = getBracketGuideCacheConfigHash();
        if (state.isCacheValid(start, end, v, cfg)) {
            return;
        }

        HighlightLineState hlState = view.highlightState.getLineStateAtStart(start);
        BracketGuideState.BracketGuideStateInner guideStart =
                new BracketGuideState.BracketGuideStateInner(hlState.inBlockComment, hlState.stringState);
        boolean guideBlock = guideStart.inBlockComment && view.isBlockCommentsEnabledForBracket();
        int guideString = guideStart.stringState;
        if (!view.isBlockCommentsEnabledForBracket()) guideBlock = false;
        if (!view.isMultiLineStringsEnabledForBracket() && guideString != view.getStringStateTripleForBracket())
            guideString = 0;
        if (!view.isBacktickStringsEnabledForBracket() && guideString == view.getStringStateBacktickForBracket())
            guideString = 0;
        if (!view.isTripleQuoteStringsEnabledForBracket() && guideString == view.getStringStateTripleForBracket())
            guideString = 0;

        BracketGuideState.BracketGuideStateInner bracketGuideState = new BracketGuideState.BracketGuideStateInner(guideBlock, guideString);
        state.clearTokensWindow();
        state.ensureTokensWindowCapacity(end - start + 1);

        for (int line = start; line <= end; line++) {
            String text = view.getLineTextForRenderWithDirectForBracket(line, directLines);
            List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, bracketGuideState);
            state.addTokensToWindow(tokens);
        }

        state.setCacheStartLine(start);
        state.setCacheEndLine(end);
        state.setCacheEditVersion(v);
        state.setCacheConfigHash(cfg);
    }

    public List<BracketGuideToken> getTokensForLine(int globalLine) {
        if (!state.isBracketGuidesEnabled()) return java.util.Collections.emptyList();
        int start = state.getCacheStartLine();
        int end = state.getCacheEndLine();
        if (start < 0 || globalLine < start || globalLine > end) return java.util.Collections.emptyList();
        List<BracketGuideToken> tokens = state.getTokensForLine(globalLine, start, end);
        return (tokens != null) ? tokens : java.util.Collections.emptyList();
    }

    private List<BracketGuideToken> updateBracketGuideStateForLine(
            String line, int globalLine, BracketGuideState.BracketGuideStateInner state) {
        if (line == null) line = "";
        int length = line.length();
        int firstNonSpace = getFirstNonSpaceIndex(line);
        List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

        int i = 0;
        boolean inLineComment = false;

        while (i < length) {
            if (inLineComment) break;

            if (state.inBlockComment) {
                int end = HighlightParser.findBlockCommentEnd(line, i);
                if (end < 0) return tokensToDraw;
                i = end + 2;
                state.inBlockComment = false;
                continue;
            }

            if (state.stringState != 0) {
                HighlightParser.StringEndResult endResult =
                        view.highlightParser.findStringEndForState(line, i, state.stringState);
                if (!endResult.found) return tokensToDraw;
                i = endResult.endIndex;
                state.stringState = 0;
                continue;
            }

            if (view.highlightState.isLineCommentStart(line, i)) {
                inLineComment = true;
                break;
            }

            if (view.isBlockCommentsEnabledForBracket()
                    && i + 1 < length
                    && line.charAt(i) == '/'
                    && line.charAt(i + 1) == '*'
                    && !HighlightParser.isTokenEscaped(line, i)) {
                int end = HighlightParser.findBlockCommentEnd(line, i + 2);
                if (end < 0) {
                    state.inBlockComment = true;
                    return tokensToDraw;
                }
                i = end + 2;
                continue;
            }

            if (view.highlightState.isTripleQuoteStringsEnabled && line.startsWith(line, i) && !HighlightParser.isEscaped(line, i)) {
                int end = HighlightParser.findTripleQuoteEnd(line, i + 3);
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
            if (view.highlightState.isStringDelimiter(c) && !HighlightParser.isEscaped(line, i)) {
                int end = HighlightParser.findStringEnd(line, i + 1, c);
                if (end < 0) {
                    if (view.isMultiLineStringsEnabledForBracket()) {
                        state.stringState = view.highlightState.getStringStateForDelimiter(c);
                    }
                    return tokensToDraw;
                }
                i = end + 1;
                continue;
            }

            if ((c == '{' || c == '}') && !HighlightParser.isEscaped(line, i)) {
                if (c == '{') {
                    int column = view.getBraceGuideColumnForLineForBracket(line, globalLine, i, firstNonSpace);
                    float x = renderer.getGuideXForColumn(line, column, globalLine);
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

    private static List<BracketGuideToken> getGuideTokensFromStack(java.util.ArrayDeque<BracketGuideToken> stack) {
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
        List<String> delimiters = view.highlightState.lineCommentDelimiters;
        for (int i = 0; i < delimiters.size(); i++) {
            h = 31 * h + delimiters.get(i).hashCode();
        }
        h = 31 * h + (view.isWhitespaceGuidesEnabledForBracket() ? 1 : 0);
        h = 31 * h + view.getWhitespaceGuideSpaceStepForBracket();
        h = 31 * h + Float.floatToIntBits(view.getPaintTextSizeForBracket());
        h = 31 * h + (view.isRtlForBracket() ? 1 : 0);
        return h;
    }

    private static int getFirstNonSpaceIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) return i;
        }
        return -1;
    }
}
