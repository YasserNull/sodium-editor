package com.yn.sodiumeditor.state;

import android.animation.ValueAnimator;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * State class for code folding functionality.
 * Stores fold ranges, intervals, and ripple animation state.
 */
public class FoldState {

    public boolean isCodeFoldingEnabled = false;
    
    // Fold ranges storage
    public final HashMap<Integer, FoldRange> foldRanges = new HashMap<>();
    public final ArrayList<int[]> foldIntervals = new ArrayList<>();
    public boolean foldIntervalsDirty = true;

    // Ripple animation state
    public int foldRippleLine = -1;
    public float foldRippleRadius = 0f;
    public float foldRippleAlpha = 0f;
    public float foldRippleMaxRadius = 0f;
    public ValueAnimator foldRippleAnimator;

    public FoldState() {
    }

    public boolean isCodeFoldingEnabled() {
        return isCodeFoldingEnabled;
    }

    public void setCodeFoldingEnabled(boolean enabled) {
        isCodeFoldingEnabled = enabled;
        if (!enabled) {
            clear();
        }
        foldIntervalsDirty = true;
    }

    public boolean hasFoldRanges() {
        return !foldRanges.isEmpty();
    }

    public Iterable<FoldRange> getFoldRanges() {
        return foldRanges.values();
    }

    public FoldRange getFoldRange(int line) {
        return foldRanges.get(line);
    }

    public void putFoldRange(int line, FoldRange range) {
        foldRanges.put(line, range);
        foldIntervalsDirty = true;
    }

    public void removeFoldRange(int line) {
        foldRanges.remove(line);
        foldIntervalsDirty = true;
    }

    public void removeIndentFolds() {
        foldRanges.entrySet().removeIf(e -> e.getValue().isIndentFold);
        foldIntervalsDirty = true;
    }

    public void clear() {
        foldRanges.clear();
        foldIntervalsDirty = true;
        clearFoldRipple();
    }

    public void clearFoldRipple() {
        if (foldRippleAnimator != null) {
            foldRippleAnimator.cancel();
            foldRippleAnimator = null;
        }
        foldRippleAlpha = 0f;
        foldRippleRadius = 0f;
        foldRippleLine = -1;
    }

    public void rebuildFoldIntervalsIfNeeded() {
        if (!foldIntervalsDirty) return;
        foldIntervalsDirty = false;
        foldIntervals.clear();
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return;

        for (FoldRange range : foldRanges.values()) {
            if (!range.collapsed) continue;
            int start = range.startLine + 1;
            int end = range.endLine;
            if (end < start) continue;
            foldIntervals.add(new int[] {start, end});
        }
        if (foldIntervals.isEmpty()) return;

        java.util.Collections.sort(foldIntervals, (a, b) -> Integer.compare(a[0], b[0]));
        int write = 0;
        int[] cur = foldIntervals.get(0);
        for (int i = 1; i < foldIntervals.size(); i++) {
            int[] nxt = foldIntervals.get(i);
            if (nxt[0] <= cur[1] + 1) {
                cur[1] = Math.max(cur[1], nxt[1]);
            } else {
                foldIntervals.set(write++, cur);
                cur = nxt;
            }
        }
        foldIntervals.set(write++, cur);
        while (foldIntervals.size() > write) foldIntervals.remove(foldIntervals.size() - 1);
    }

    public int getHiddenLineCount(int totalLines) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return 0;
        rebuildFoldIntervalsIfNeeded();
        int hidden = 0;
        for (int[] interval : foldIntervals) {
            int s = interval[0];
            int e = Math.min(interval[1], totalLines - 1);
            if (e >= s) hidden += (e - s + 1);
        }
        return hidden;
    }

    public int mapVisibleIndexToGlobal(int visibleIndex, int totalLines) {
        if (!isCodeFoldingEnabled) return visibleIndex;
        int visibleTotal = Math.max(1, totalLines - getHiddenLineCount(totalLines));
        int clamped = Math.max(0, Math.min(visibleIndex, Math.max(0, visibleTotal - 1)));
        int global = clamped;
        rebuildFoldIntervalsIfNeeded();
        for (int[] interval : foldIntervals) {
            if (global < interval[0]) break;
            global += (interval[1] - interval[0] + 1);
        }
        return Math.max(0, Math.min(global, totalLines - 1));
    }

    public int getVisibleIndexForGlobalLine(int globalLine) {
        if (!isCodeFoldingEnabled) return globalLine;
        rebuildFoldIntervalsIfNeeded();
        int visible = globalLine;
        for (int[] interval : foldIntervals) {
            if (globalLine < interval[0]) break;
            if (globalLine <= interval[1]) return Math.max(0, interval[0] - 1);
            visible -= (interval[1] - interval[0] + 1);
        }
        return Math.max(0, visible);
    }

    public boolean isLineHiddenByFold(int line) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
        rebuildFoldIntervalsIfNeeded();
        for (int[] interval : foldIntervals) {
            if (line < interval[0]) return false;
            if (line <= interval[1]) return true;
        }
        return false;
    }

    public FoldRange getFoldRangeAtStart(int line) {
        if (!isCodeFoldingEnabled) return null;
        FoldRange range = foldRanges.get(line);
        return (range != null && range.collapsed) ? range : null;
    }
}
