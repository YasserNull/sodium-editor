package com.yn.sodiumeditor.renderer;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.HighlightManager;

/**
 * Handles text navigation, selection, and word boundary operations.
 */
public final class TextNavigation {
    private final SodiumEditorView view;
    private final LineCacheManager lineCacheManager;

    public TextNavigation(SodiumEditorView view, LineCacheManager lineCacheManager) {
        this.view = view;
        this.lineCacheManager = lineCacheManager;
    }

    /**
     * Gets the global line number for a given Y position.
     */
    public int getGlobalLineForY(float y) {
        int idx = Math.max(0, (int) (y / view.lineHeight));
        if (view.wrapWordState.isWordWrapEnabled) {
            return view.wrapWordMapper.getVisualPositionForIndex(view, idx, Math.max(1, Math.round(view.getWidth() - view.getTextStartX()))).line;
        }
        return view.mapVisibleIndexToGlobal(idx);
    }

    /**
     * Gets the visual index for a specific line and character position.
     */
    public int getVisualIndexForLineAndChar(int line, int ch) {
        if (!view.wrapWordBuilder.isMetricsUsableForLine(view, line, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())))) {
            if (view.foldManager.isCodeFoldingEnabled) return view.getVisibleIndexForGlobalLine(line);
            return Math.max(0, line);
        }
        int totalLines = view.wrapWordMetrics.wrapLinePrefix.length - 1;
        int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
        String text = lineCacheManager.getLineTextForRender(safeLine);
        int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, safeLine, text, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())), view.paint);
        int seg = view.wrapWordEngine.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
        return view.wrapWordMetrics.wrapLinePrefix[safeLine] + seg;
    }

    /**
     * Gets the total number of visible lines (accounting for folding).
     */
    public int getVisibleLineCount() {
        int total = getLinesCount();
        if (total <= 0) total = view.windowStartLine + view.linesWindow.size();
        int visible = Math.max(1, total - view.foldManager.getHiddenLineCount(total));
        return visible;
    }

    /**
     * Maps a visible index to a global line number.
     */
    public int mapVisibleIndexToGlobal(int visibleIndex) {
        int total = getLinesCount();
        if (total <= 0) total = view.windowStartLine + view.linesWindow.size();
        return view.foldManager.mapVisibleIndexToGlobal(visibleIndex, total);
    }

    /**
     * Gets the visible index for a global line number.
     */
    public int getVisibleIndexForGlobalLine(int globalLine) {
        return view.foldManager.getVisibleIndexForGlobalLine(globalLine);
    }

    /**
     * Gets the total number of lines in the document.
     */
    public int getLinesCount() {
        if (view.fileManager.isFileCleared()) {
            return Math.max(1, view.windowStartLine + view.linesWindow.size());
        }
        int windowCount = view.windowStartLine + view.linesWindow.size();
        if (view.fileManager.isIndexReady() && view.fileManager.getLineOffsets().length > 0) {
            boolean hasEdits;
            synchronized (view.modifiedLines) {
                hasEdits = !view.modifiedLines.isEmpty();
            }
            if (!hasEdits && view.history.getLineCountDelta() == 0) {
                return view.fileManager.getLineOffsets().length;
            }
            int count = view.fileManager.getLineOffsets().length + view.history.getLineCountDelta();
            if (count < 1) count = 1;
            return Math.max(count, windowCount);
        }
        if (view.fileManager.isEof()) return view.windowStartLine + view.linesWindow.size();
        if (!view.linesWindow.isEmpty()) return view.windowStartLine + view.linesWindow.size();
        return -1;
    }

    /**
     * Checks if a character is a word character (letter, digit, underscore, or dollar sign).
     */
    public boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * Computes word bounds for a given position (basic version).
     */
    public int[] computeWordBounds(String line, int pos) {
        pos = Math.max(0, Math.min(pos, line.length()));
        if (line.length() == 0) return new int[]{0, 0};
        if (pos == line.length()) pos = Math.max(0, pos - 1);
        if (Character.isWhitespace(line.charAt(pos))) {
            int i = pos;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
            if (i >= line.length()) {
                i = pos - 1;
                while (i >= 0 && Character.isWhitespace(line.charAt(i))) i--;
            }
            if (i < 0) return new int[]{pos, pos};
            pos = i;
        }
        int start = pos;
        int end = pos;
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
        while (end < line.length() - 1 && !Character.isWhitespace(line.charAt(end + 1))) end++;
        return new int[]{start, end + 1};
    }

    /**
     * Computes word bounds for a given position (smart version with word char detection).
     */
    public int[] computeWordBoundsSmart(String line, int pos) {
        if (line == null || line.isEmpty()) return new int[]{0, 0};
        int len = line.length();
        int idx = Math.max(0, Math.min(pos, len - 1));
        if (!isWordChar(line.charAt(idx))) {
            if (idx > 0 && isWordChar(line.charAt(idx - 1))) {
                idx = idx - 1;
            } else if (idx + 1 < len && isWordChar(line.charAt(idx + 1))) {
                idx = idx + 1;
            } else {
                return new int[]{idx, idx};
            }
        }
        int start = idx;
        int end = idx;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        while (end < len - 1 && isWordChar(line.charAt(end + 1))) end++;
        return new int[]{start, end + 1};
    }

    /**
     * Applies smart double-tap selection on a word.
     */
    public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
        if (lineText == null) return false;
        int[] bounds = computeWordBoundsSmart(lineText, charIndex);
        ArrayList<SodiumEditorView.TextRange> candidates =
                buildDoubleTapCandidates(lineText, charIndex, bounds[0], bounds[1]);
        if (candidates.isEmpty()) return false;

        boolean sameAnchor =
                line == view.lastDoubleTapLine
                        && bounds[0] == view.lastDoubleTapWordStart
                        && bounds[1] == view.lastDoubleTapWordEnd;
        int currentIdx = findSelectionCandidateIndex(line, candidates);
        int nextIdx;
        if (sameAnchor) {
            if (currentIdx >= 0) {
                nextIdx = Math.min(currentIdx + 1, candidates.size() - 1);
            } else {
                nextIdx = Math.min(view.lastDoubleTapStage + 1, candidates.size() - 1);
            }
        } else {
            nextIdx = 0;
        }

        SodiumEditorView.TextRange pick = candidates.get(nextIdx);
        view.selectionState.setSelection(line, pick.start, line, pick.end, true);
        view.selectionState.setSelectAllState(false, false);
        view.cursorState.setCursorPosition(line, pick.end);
        view.lastDoubleTapLine = line;
        view.lastDoubleTapWordStart = bounds[0];
        view.lastDoubleTapWordEnd = bounds[1];
        view.lastDoubleTapStage = nextIdx;
        return true;
    }

    /**
     * Builds candidate selection ranges for double-tap (word, quotes, brackets).
     */
    public ArrayList<SodiumEditorView.TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
        ArrayList<SodiumEditorView.TextRange> out = new ArrayList<>(6);
        if (line == null) return out;
        int len = line.length();
        addSelectionCandidate(out, wStart, wEnd, len);

        SodiumEditorView.TextRange quote = findEnclosingQuoteRange(line, charIndex);
        if (quote != null) {
            addSelectionCandidate(out, quote.start + 1, quote.end, len);
            addSelectionCandidate(out, quote.start, quote.end + 1, len);
        }

        SodiumEditorView.TextRange bracket = findEnclosingBracketRange(line, charIndex);
        if (bracket != null) {
            addSelectionCandidate(out, bracket.start + 1, bracket.end, len);
            addSelectionCandidate(out, bracket.start, bracket.end + 1, len);
        }
        return out;
    }

    /**
     * Adds a selection candidate range if not already present.
     */
    public void addSelectionCandidate(List<SodiumEditorView.TextRange> out, int start, int end, int lineLen) {
        if (out == null) return;
        int s = Math.max(0, Math.min(start, lineLen));
        int e = Math.max(0, Math.min(end, lineLen));
        if (e <= s) return;
        for (SodiumEditorView.TextRange r : out) {
            if (r.start == s && r.end == e) return;
        }
        out.add(new SodiumEditorView.TextRange(s, e));
    }

    /**
     * Finds the index of the current selection in the candidate list.
     */
    public int findSelectionCandidateIndex(int line, List<SodiumEditorView.TextRange> candidates) {
        if (!view.selectionState.hasSelection() || candidates == null || candidates.isEmpty()) return -1;
        int sL = view.selectionState.selStartLine;
        int sC = view.selectionState.selStartChar;
        int eL = view.selectionState.selEndLine;
        int eC = view.selectionState.selEndChar;
        if (view.comparePos(sL, sC, eL, eC) > 0) {
            sL = view.selectionState.selEndLine;
            sC = view.selectionState.selEndChar;
            eL = view.selectionState.selStartLine;
            eC = view.selectionState.selStartChar;
        }
        if (sL != line || eL != line) return -1;
        for (int i = 0; i < candidates.size(); i++) {
            SodiumEditorView.TextRange r = candidates.get(i);
            if (r.start == sC && r.end == eC) return i;
        }
        return -1;
    }

    /**
     * Checks if a character is a quote character.
     */
    public boolean isQuoteChar(char c) {
        return c == '"' || c == '\'' || c == '`';
    }

    /**
     * Finds the enclosing quoted range for a given index.
     */
    @Nullable
    public SodiumEditorView.TextRange findEnclosingQuoteRange(String line, int index) {
        if (line == null || line.isEmpty()) return null;
        int len = line.length();
        if (index < 0 || index > len) return null;
        ArrayList<SodiumEditorView.TextRange> ranges = new ArrayList<>();
        char current = 0;
        int start = -1;
        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (current == 0) {
                if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
                    current = c;
                    start = i;
                }
            } else {
                if (c == current && !HighlightManager.isEscaped(line, i)) {
                    ranges.add(new SodiumEditorView.TextRange(start, i));
                    current = 0;
                    start = -1;
                }
            }
        }
        SodiumEditorView.TextRange best = null;
        int bestLen = Integer.MAX_VALUE;
        for (SodiumEditorView.TextRange r : ranges) {
            if (index >= r.start && index <= r.end) {
                int span = r.end - r.start;
                if (span < bestLen) {
                    bestLen = span;
                    best = r;
                }
            }
        }
        return best;
    }

    /**
     * Finds the enclosing bracket range for a given index.
     */
    @Nullable
    public SodiumEditorView.TextRange findEnclosingBracketRange(String line, int index) {
        if (line == null || line.isEmpty()) return null;
        int len = line.length();
        if (index < 0 || index > len) return null;
        ArrayList<SodiumEditorView.TextRange> ranges = new ArrayList<>();
        int[] stackIdx = new int[Math.max(8, len / 4)];
        char[] stackType = new char[stackIdx.length];
        int sp = 0;
        char currentQuote = 0;
        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (currentQuote != 0) {
                if (c == currentQuote && !HighlightManager.isEscaped(line, i)) {
                    currentQuote = 0;
                }
                continue;
            }
            if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
                currentQuote = c;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                if (sp >= stackIdx.length) {
                    int newSize = stackIdx.length * 2;
                    int[] newIdx = new int[newSize];
                    char[] newType = new char[newSize];
                    System.arraycopy(stackIdx, 0, newIdx, 0, stackIdx.length);
                    System.arraycopy(stackType, 0, newType, 0, stackIdx.length);
                    stackIdx = newIdx;
                    stackType = newType;
                }
                stackIdx[sp] = i;
                stackType[sp] = c;
                sp++;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                char want = (c == ')') ? '(' : (c == ']') ? '[' : '{';
                if (sp > 0 && stackType[sp - 1] == want) {
                    int start = stackIdx[sp - 1];
                    sp--;
                    ranges.add(new SodiumEditorView.TextRange(start, i));
                }
            }
        }
        SodiumEditorView.TextRange best = null;
        int bestLen = Integer.MAX_VALUE;
        for (SodiumEditorView.TextRange r : ranges) {
            if (index >= r.start && index <= r.end) {
                int span = r.end - r.start;
                if (span < bestLen) {
                    bestLen = span;
                    best = r;
                }
            }
        }
        return best;
    }
}
