package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.FoldRange;
import com.yn.sodiumeditor.state.IndentGuideState;

/**
 * Engine class for indent guides.
 * Handles building and merging indent guide intervals.
 */
public class IndentGuideEngine {

    private final SodiumEditor view;
    private final IndentGuideState state;

    public IndentGuideEngine(SodiumEditor view, IndentGuideState state) {
        this.view = view;
        this.state = state;
    }

    public void markIntervalsDirty() {
        state.markIntervalsDirty();
    }

    public void rebuildIntervalsIfNeeded() {
        if (!state.isIndentGuideIntervalsDirty()) return;
        state.setIndentGuideIntervalsDirty(false);
        state.clearIntervals();
        if (!view.isIndentationBlocksEnabledForIndentGuides() || !view.hasIndentGuideFoldRanges()) return;
        for (FoldRange range : view.getIndentGuideFoldRanges()) {
            if (!range.isIndentFold) continue;
            int start = range.startLine + 1;
            int end = range.endLine;
            if (end < start) continue;
            state.addInterval(new int[] {start, end});
        }
        if (!state.hasIntervals()) return;

        state.sortIntervals();
        int write = 0;
        int[] cur = state.getIntervalAt(0);
        for (int i = 1; i < state.getIntervalsCount(); i++) {
            int[] nxt = state.getIntervalAt(i);
            if (nxt[0] <= cur[1] + 1) {
                cur[1] = Math.max(cur[1], nxt[1]);
            } else {
                state.setIntervalAt(write++, cur);
                cur = nxt;
            }
        }
        state.setIntervalAt(write++, cur);
        while (state.getIntervalsCount() > write) {
            state.removeIntervalAt(state.getIntervalsCount() - 1);
        }
    }

    /**
     * Gets the leading whitespace from a line.
     * @param line the line text
     * @return the leading whitespace string
     */
    public static String getLineLeadingWhitespace(String line) {
        if (line == null || line.isEmpty()) return "";
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') break;
            i++;
        }
        return (i == 0) ? "" : line.substring(0, i);
    }

    /**
     * Gets the indent width of a line in spaces.
     * @param line the line text
     * @return the indent width
     */
    public static int getIndentWidth(String line) {
        if (line == null || line.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
            } else {
                break;
            }
        }
        return width;
    }

    /**
     * Gets the index of the first non-space character in a line.
     * @param line the line text
     * @return the index of the first non-space character, or -1 if not found
     */
    public static int getFirstNonSpaceIndex(String line) {
        if (line == null || line.isEmpty()) return -1;
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) return i;
        }
        return -1;
    }

    /**
     * Calculates the brace guide column for a line containing a bracket.
     * @param line the line text
     * @param globalLine the global line index
     * @param braceIndex the column index of the brace
     * @param firstNonSpace the column index of the first non-space character
     * @return the column index for the brace guide
     */
    public int getBraceGuideColumnForLine(
            String line, int globalLine, int braceIndex, int firstNonSpace) {
        int column = (firstNonSpace >= 0) ? firstNonSpace : braceIndex;
        if (firstNonSpace >= 0 && braceIndex > firstNonSpace) {
            char first = line.charAt(firstNonSpace);
            if (first == ')' || first == ']') {
                int prevIndent = getPreviousNonEmptyIndentColumn(globalLine - 1);
                if (prevIndent >= 0) {
                    column = prevIndent;
                }
            }
        }
        return column;
    }

    /**
     * Gets the previous non-empty indent column.
     * @param line the line index to search from (going backwards)
     * @return the indent column of the previous non-empty line, or -1 if not found
     */
    public int getPreviousNonEmptyIndentColumn(int line) {
        for (int l = line; l >= 0; l--) {
            String prev = view.getLineTextForRender(l);
            if (prev == null) continue;
            int idx = getFirstNonSpaceIndex(prev);
            if (idx >= 0) return idx;
        }
        return -1;
    }
}
