package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.state.BracketMatch;
import com.yn.sodiumeditor.state.BracketMatchLineState;
import com.yn.sodiumeditor.state.BracketMatchState;
import com.yn.sodiumeditor.state.BracketToken;
import com.yn.sodiumeditor.state.HighlightLineState;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Engine class for bracket matching.
 * Handles finding matching brackets through parsing.
 */
public class BracketMatchEngine {

    private final SodiumEditorView view;
    private final BracketMatchState state;

    public BracketMatchEngine(SodiumEditorView view, BracketMatchState state) {
        this.view = view;
        this.state = state;
    }

    @Nullable
    public BracketMatch getMatch(int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
        if (!state.isEnabled()) return null;
        int v = view.getEditVersionForMatch();
        int line = view.cursorState.getCursorLine();
        int ch = view.cursorState.getCursorChar();
        if (state.isCacheValid(line, ch, v)) {
            return state.getCached();
        }
        BracketMatch match = findBracketMatchInVisible(firstVisibleLine, lastVisibleLine, directLines);
        state.updateCache(match, line, ch, v);
        return match;
    }

    @Nullable
    private BracketMatch findBracketMatchInVisible(
            int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
        if (!state.isEnabled()) return null;
        int cursorLine = view.cursorState.getCursorLine();
        int cursorChar = view.cursorState.getCursorChar();
        if (cursorLine < firstVisibleLine || cursorLine > lastVisibleLine) return null;

        String cursorLineText = view.getLineTextForRenderWithDirectForMatch(cursorLine, directLines);
        if (cursorLineText == null) return null;

        int targetIndex = -1;
        char targetChar = 0;
        if (cursorChar > 0 && cursorChar - 1 < cursorLineText.length()) {
            char c = cursorLineText.charAt(cursorChar - 1);
            if (isBracketChar(c)) {
                targetIndex = cursorChar - 1;
                targetChar = c;
            }
        }
        if (targetIndex < 0 && cursorChar < cursorLineText.length()) {
            char c = cursorLineText.charAt(cursorChar);
            if (isBracketChar(c)) {
                targetIndex = cursorChar;
                targetChar = c;
            }
        }
        if (targetIndex < 0) return null;

        HighlightLineState hlState = view.highlightState.getLineStateAtStart(firstVisibleLine);
        BracketMatchLineState startState = new BracketMatchLineState(hlState.inBlockComment, hlState.stringState);
        boolean inBlockComment = startState.inBlockComment && view.isBlockCommentsEnabledForMatch();
        int stringState = startState.stringState;
        if (!view.isBlockCommentsEnabledForMatch()) inBlockComment = false;
        if (!view.isMultiLineStringsEnabledForMatch() && stringState != view.getStringStateTripleForMatch())
            stringState = 0;
        if (!view.isBacktickStringsEnabledForMatch() && stringState == view.getStringStateBacktickForMatch())
            stringState = 0;
        if (!view.isTripleQuoteStringsEnabledForMatch() && stringState == view.getStringStateTripleForMatch())
            stringState = 0;

        ArrayDeque<BracketToken> stack = new ArrayDeque<>();

        for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
            String text = view.getLineTextForRenderWithDirectForMatch(line, directLines);
            if (text == null) text = "";
            int len = text.length();
            int i = 0;
            boolean inLineComment = false;

            while (i < len) {
                if (inLineComment) break;

                if (inBlockComment) {
                    int end = HighlightParser.findBlockCommentEnd(text, i);
                    int endPos = (end < 0) ? len : end + 2;
                    if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
                    if (end < 0) break;
                    i = end + 2;
                    inBlockComment = false;
                    continue;
                }

                if (stringState != 0) {
                    HighlightParser.StringEndResult endResult =
                            view.highlightParser.findStringEndForState(text, i, stringState);
                    int endPos = endResult.found ? endResult.endIndex : len;
                    if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
                    if (!endResult.found) break;
                    i = endResult.endIndex;
                    stringState = 0;
                    continue;
                }

                if (view.highlightState.isLineCommentStart(text, i)) {
                    if (line == cursorLine && targetIndex >= i) return null;
                    inLineComment = true;
                    break;
                }

                if (view.isBlockCommentsEnabledForMatch()
                        && i + 1 < len
                        && text.charAt(i) == '/'
                        && text.charAt(i + 1) == '*'
                        && !HighlightParser.isTokenEscaped(text, i)) {
                    int end = HighlightParser.findBlockCommentEnd(text, i + 2);
                    int endPos = (end < 0) ? len : end + 2;
                    if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
                    if (end < 0) {
                        inBlockComment = true;
                        break;
                    }
                    i = end + 2;
                    continue;
                }

                if (view.highlightState.isTripleQuoteStringsEnabled && text.startsWith("\"\"\"", i) && !HighlightParser.isEscaped(text, i)) {
                    int end = HighlightParser.findTripleQuoteEnd(text, i + 3);
                    int endPos = end >= 0 ? end + 3 : len;
                    if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
                    if (end < 0) {
                        if (view.isTripleQuoteStringsEnabledForMatch()) {
                            stringState = view.getStringStateTripleForMatch();
                        }
                        break;
                    }
                    i = end + 3;
                    continue;
                }

                char c = text.charAt(i);
                if (view.highlightState.isStringDelimiter(c) && !HighlightParser.isEscaped(text, i)) {
                    int end = HighlightParser.findStringEnd(text, i + 1, c);
                    int endPos = end >= 0 ? end + 1 : len;
                    if (line == cursorLine && targetIndex >= i && targetIndex < endPos) return null;
                    if (end < 0) {
                        if (view.isMultiLineStringsEnabledForMatch()) {
                            stringState = view.highlightState.getStringStateForDelimiter(c);
                        }
                        break;
                    }
                    i = end + 1;
                    continue;
                }

                if (isBracketChar(c) && !HighlightParser.isEscaped(text, i)) {
                    BracketToken token = new BracketToken(line, i, c);
                    if (isOpeningBracket(c)) {
                        stack.push(token);
                    } else if (isClosingBracket(c)) {
                        if (!stack.isEmpty() && stack.peek().bracket == matchingBracket(c)) {
                            BracketToken open = stack.pop();
                            if (line == cursorLine && i == targetIndex) {
                                return new BracketMatch(open.line, open.ch, line, i);
                            }
                            if (open.line == cursorLine && open.ch == targetIndex) {
                                return new BracketMatch(open.line, open.ch, line, i);
                            }
                        }
                    }
                }

                i++;
            }
        }
        return new BracketMatch(cursorLine, targetIndex, cursorLine, targetIndex);
    }

    public static boolean isBracketChar(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    public static boolean isOpeningBracket(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    public static boolean isClosingBracket(char c) {
        return c == ')' || c == ']' || c == '}';
    }

    public static char matchingBracket(char c) {
        switch (c) {
            case '(':
                return ')';
            case ')':
                return '(';
            case '[':
                return ']';
            case ']':
                return '[';
            case '{':
                return '}';
            case '}':
                return '{';
            default:
                return 0;
        }
    }
}
