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
}
