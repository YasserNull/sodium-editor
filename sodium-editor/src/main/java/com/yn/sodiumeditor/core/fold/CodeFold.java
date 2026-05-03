package com.yn.sodiumeditor.core.fold;

import com.yn.sodiumeditor.SodiumEditor;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages code folding functionality for the SodiumEditor.
 * Delegates detection, animation, and utilities to specialized classes.
 */
public class CodeFold {

    // --- Code Fold State ---
    public boolean isCodeFoldingEnabled = true;
    public final ConcurrentHashMap<Integer, FoldRange> foldRanges = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, Boolean> pendingFoldComputations = new ConcurrentHashMap<>();
    public final ArrayList<int[]> foldIntervals = new ArrayList<>();
    public boolean foldIntervalsDirty = true;

    // Fold marker visibility cache — avoids per-frame full parse of each visible line
    private final android.util.SparseArray<Byte> foldMarkerVisibilityCache = new android.util.SparseArray<>();

    // Cached bracket state after each collapsed fold range — avoids per-frame re-scan of hidden lines
    public final java.util.HashMap<Integer, com.yn.sodiumeditor.core.guides.bracket.BracketGuideState> cachedBracketStateAfterFold = new java.util.HashMap<>();

    // Delegate classes
    public final CodeFoldDetector detector;
    public final com.yn.sodiumeditor.renderer.animation.CodeFoldAnimation animation;
    public final com.yn.sodiumeditor.utils.CodeFoldUtils utils;

    // Constants
    public static final int INDENT_FOLD_SCAN_LIMIT = 2000;
    public static final String FOLD_PLACEHOLDER_TEXT = "<—>";

    private final SodiumEditor editor;

    public CodeFold(SodiumEditor editor) {
        FunctionLog.f("CodeFold", "CodeFold", editor);
        this.editor = editor;
        this.detector = new CodeFoldDetector(this.editor);
        this.animation = new com.yn.sodiumeditor.renderer.animation.CodeFoldAnimation(this.editor);
        this.utils = new com.yn.sodiumeditor.utils.CodeFoldUtils(this.editor);
    }

    /**
     * Enable or disable code folding.
     */
    public void setCodeFoldingEnabled(boolean enabled) {
        FunctionLog.f("CodeFold", "setCodeFoldingEnabled", enabled);
        if (this.isCodeFoldingEnabled == enabled || editor.lineNumber.showLineNumbers) return;
        this.isCodeFoldingEnabled = enabled;
        if (!enabled) {
            foldRanges.clear();
            foldIntervals.clear();
        } else {
            animation.foldMarkerTextScale = 1f;
            animation.foldMarkerPaint.setTextSize(editor.textRender.paint.getTextSize());
        }
        foldIntervalsDirty = true;
        editor.windowRender.recalculateMaxLineWidth();
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Check if code folding is enabled.
     */
    public boolean isCodeFoldingEnabled() {
        FunctionLog.f("CodeFold", "isCodeFoldingEnabled");
        return isCodeFoldingEnabled;
    }

    /**
     * Toggle fold at the specified line.
     */
    public boolean toggleFoldAtLine(int line) {
        FunctionLog.f("CodeFold", "toggleFoldAtLine", line);
        if (!isCodeFoldingEnabled) return false;
        if (editor.DEBUG_RENDER_LOGS) {
            Log.d("SodiumRender", "toggleFold line=" + line);
        }
        FoldRange existing = foldRanges.get(line);
        if (existing != null) {
            existing.collapsed = !existing.collapsed;
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
            
            // Check if we need to load new window content for the new visible area
            editor.fileIO.checkAndLoadWindow();
            
            editor.invalidate();
            return true;
        }
        if (pendingFoldComputations.putIfAbsent(line, Boolean.TRUE) != null) {
            return false;
        }

        detector.detectFoldRangeAsync(line, range -> {
            pendingFoldComputations.remove(line);
            if (range != null) {
                range.collapsed = true;
                foldRanges.put(line, range);
                invalidateFoldCaches();
                rebuildFoldIntervalsIfNeeded();
                editor.fileIO.checkAndLoadWindow();
                editor.invalidate();
            }
        });
        return false;
    }

    /**
     * Invalidate internal fold caches (e.g., after an edit).
     */
    public void invalidateFoldCaches() {
        FunctionLog.f("CodeFold", "invalidateFoldCaches");
        foldIntervalsDirty = true;
        foldMarkerVisibilityCache.clear();
        cachedBracketStateAfterFold.clear();
    }

    /**
     * Mark intervals as dirty.
     */
    public void markIntervalsDirty() {
        FunctionLog.f("CodeFold", "markIntervalsDirty");
        foldIntervalsDirty = true;
    }

    /**
     * Get total number of visible lines (accounting for collapsed folds).
     */
    public int getVisibleLineCount() {
        FunctionLog.f("CodeFold", "getVisibleLineCount");
        if (!isCodeFoldingEnabled) return editor.view.getLinesCount();
        rebuildFoldIntervalsIfNeeded();
        if (foldIntervals.isEmpty()) return editor.view.getLinesCount();
        int[] last = foldIntervals.get(foldIntervals.size() - 1);
        return last[3] + 1;
    }

    /**
     * Maps a visible line index to its global document line number.
     */
    public int mapVisibleIndexToGlobal(int visibleIndex) {
        FunctionLog.f("CodeFold", "mapVisibleIndexToGlobal", visibleIndex);
        if (!isCodeFoldingEnabled) return visibleIndex;
        rebuildFoldIntervalsIfNeeded();
        if (foldIntervals.isEmpty()) return visibleIndex;
        
        // Binary search over visible intervals. Avoids line-count-sized lookup arrays.
        int lo = 0, hi = foldIntervals.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int[] interval = foldIntervals.get(mid);
            if (visibleIndex < interval[2]) {
                hi = mid - 1;
            } else if (visibleIndex > interval[3]) {
                lo = mid + 1;
            } else {
                return interval[0] + (visibleIndex - interval[2]);
            }
        }
        return visibleIndex;
    }

    /**
     * Maps a global line number to its visible index.
     */
    public int getVisibleIndexForGlobalLine(int globalLine) {
        FunctionLog.f("CodeFold", "getVisibleIndexForGlobalLine", globalLine);
        if (!isCodeFoldingEnabled) return globalLine;
        rebuildFoldIntervalsIfNeeded();
        if (foldIntervals.isEmpty()) return globalLine;

        // Binary search over global intervals. Lines inside collapsed folds are absent.
        int lo = 0, hi = foldIntervals.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int[] interval = foldIntervals.get(mid);
            if (globalLine < interval[0]) {
                hi = mid - 1;
            } else if (globalLine > interval[1]) {
                lo = mid + 1;
            } else {
                return interval[2] + (globalLine - interval[0]);
            }
        }
        return -1; // Hidden
    }

    /**
     * Check if a line is hidden by a collapsed fold.
     */
    public boolean isLineHidden(int globalLine) {
        FunctionLog.f("CodeFold", "isLineHidden", globalLine);
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
        return getVisibleIndexForGlobalLine(globalLine) < 0;
    }

    /**
     * Check if a line is the start of a fold range.
     */
    public boolean isFoldStart(int globalLine) {
        FunctionLog.f("CodeFold", "isFoldStart", globalLine);
        if (!isCodeFoldingEnabled) return false;
        
        Byte cached = foldMarkerVisibilityCache.get(globalLine);
        if (cached != null) return cached == 1;

        boolean isStart = foldRanges.containsKey(globalLine) || detector.isPotentialFoldStart(globalLine);
        foldMarkerVisibilityCache.put(globalLine, isStart ? (byte)1 : (byte)0);
        return isStart;
    }

    /**
     * Get fold range starting at the specified line.
     */
    public FoldRange getFoldRangeAtStart(int line) {
        FunctionLog.f("CodeFold", "getFoldRangeAtStart", line);
        return foldRanges.get(line);
    }

    /**
     * Clear all fold ranges.
     */
    public void clearFoldRanges() {
        FunctionLog.f("CodeFold", "clearFoldRanges");
        foldRanges.clear();
        invalidateFoldCaches();
    }

    /**
     * Invalidates fold ranges in the specified global line range.
     */
    public void invalidateFoldRangesInRange(int start, int end) {
        FunctionLog.f("CodeFold", "invalidateFoldRangesInRange", start, end);
        if (foldRanges.isEmpty()) return;
        boolean changed = false;
        for (int line = start; line <= end; line++) {
            if (foldRanges.remove(line) != null) changed = true;
        }
        if (changed) {
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
        }
    }

    /**
     * Rebuild visible intervals based on collapsed fold ranges.
     * Efficiently maps global line space to visible index space.
     */
    public void rebuildFoldIntervalsIfNeeded() {
        FunctionLog.f("CodeFold", "rebuildFoldIntervalsIfNeeded");
        if (!foldIntervalsDirty) return;
        
        long startMs = SystemClock.uptimeMillis();
        foldIntervals.clear();
        int total = editor.view.getLinesCount();
        if (total <= 0) {
            foldIntervalsDirty = false;
            return;
        }

        ArrayList<Integer> starts = new ArrayList<>(foldRanges.keySet());
        Collections.sort(starts);

        int currentLine = 0;
        int currentVisible = 0;

        for (Integer startLine : starts) {
            if (startLine < currentLine) continue;
            FoldRange range = foldRanges.get(startLine);
            if (range == null || !range.collapsed || range.endLine <= range.startLine) continue;

            if (startLine > currentLine) {
                int len = startLine - currentLine;
                foldIntervals.add(new int[]{currentLine, startLine - 1, currentVisible, currentVisible + len - 1});
                currentVisible += len;
            }
            
            // Interval for the collapsed line itself
            foldIntervals.add(new int[]{startLine, startLine, currentVisible, currentVisible});
            currentVisible++;
            currentLine = range.endLine + 1;
        }

        if (currentLine < total) {
            int len = total - currentLine;
            foldIntervals.add(new int[]{currentLine, total - 1, currentVisible, currentVisible + len - 1});
        }

        foldIntervalsDirty = false;
        
        if (editor.DEBUG_RENDER_LOGS) {
            Log.d("SodiumRender", "rebuildFoldIntervals dtMs=" + (SystemClock.uptimeMillis() - startMs) + " count=" + foldIntervals.size());
        }
    }

    /**
     * Hit test for fold placeholders.
     */
    public boolean isFoldPlaceholderHit(int line, String ln, float x) {
        FunctionLog.f("CodeFold", "isFoldPlaceholderHit", line, ln, x);
        if (!isCodeFoldingEnabled || ln == null || ln.isEmpty()) return false;
        FoldRange range = foldRanges.get(line);
        if (range == null || !range.collapsed) return false;

        float[] bounds = new float[2];
        if (getFoldPlaceholderBounds(line, ln, bounds)) {
            return x >= bounds[0] && x <= bounds[1];
        }
        return false;
    }

    /**
     * Get visual bounds (X coordinates) of fold placeholder.
     */
    public boolean getFoldPlaceholderBounds(int line, String ln, float[] outBounds) {
        FunctionLog.f("CodeFold", "getFoldPlaceholderBounds", line, ln, outBounds);
        FoldRange range = foldRanges.get(line);
        if (range == null || !range.collapsed) return false;

        int prefixEnd = range.isBlockComment ? Math.min(range.openCharIndex + 2, ln.length())
                      : range.isIndentFold ? ln.length()
                      : Math.min(range.openCharIndex + 1, ln.length());
        
        float xStart = editor.highlite.measureHighlightedSegmentWidth(ln, line, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(FOLD_PLACEHOLDER_TEXT));
        
        outBounds[0] = xStart;
        outBounds[1] = xStart + placeholderWidth;
        return true;
    }

    /**
     * Resolve the actual character index of the closing bracket in the end line.
     */
    public int resolveCloseCharIndex(FoldRange range, String endLineText) {
        return utils.resolveCloseCharIndex(range, endLineText);
    }

    /**
     * Clear all folds.
     */
    public void clearAllFolds() {
        FunctionLog.f("CodeFold", "clearAllFolds");
        foldRanges.clear();
        invalidateFoldCaches();
        rebuildFoldIntervalsIfNeeded();
        editor.invalidate();
    }

    /**
     * Check if a line is hidden by a collapsed fold.
     */
    public boolean isLineHiddenByFold(int globalLine) {
        return isLineHidden(globalLine);
    }

    /**
     * Set whether indentation-based folding blocks are enabled.
     */
    public void setIndentationBlocksEnabled(boolean enabled) {
        FunctionLog.f("CodeFold", "setIndentationBlocksEnabled", enabled);
    }

    /**
     * Get the fold marker symbol (+ or -) for a line.
     */
    public String getFoldMarkerForLine(int line, String text) {
        if (!isCodeFoldingEnabled) return null;
        FoldRange range = foldRanges.get(line);
        if (range != null) {
            return range.collapsed ? "+" : "-";
        }
        if (detector.isPotentialFoldStart(line)) {
            return "-";
        }
        return null;
    }

    /**
     * Invalidates the fold range starting at the specified line.
     */
    public void invalidateFoldRangeForLine(int line) {
        FunctionLog.f("CodeFold", "invalidateFoldRangeForLine", line);
        if (foldRanges.remove(line) != null) {
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
        }
    }

    /**
     * Adjust fold range indices after a line edit.
     */
    public void adjustFoldRangeForLineEdit(int line, int editIndex, int delta, int deleteLen) {
        utils.adjustFoldRangeForLineEdit(line, editIndex, delta, deleteLen);
    }

    /**
     * Adjust all fold ranges after lines are inserted or deleted.
     */
    public void adjustFoldRangesForLineEdit(int startLine, int lineDelta) {
        FunctionLog.f("CodeFold", "adjustFoldRangesForLineEdit", startLine, lineDelta);
        if (lineDelta == 0 || foldRanges.isEmpty()) return;

        ConcurrentHashMap<Integer, FoldRange> newRanges = new ConcurrentHashMap<>();
        boolean changed = false;

        for (FoldRange range : foldRanges.values()) {
            int newStart = range.startLine;
            int newEnd = range.endLine;

            if (range.startLine >= startLine) {
                newStart += lineDelta;
                newEnd += lineDelta;
                changed = true;
            } else if (range.endLine >= startLine) {
                newEnd += lineDelta;
                changed = true;
            }

            if (newEnd > newStart) {
                FoldRange newRange = new FoldRange(newStart, newEnd, range.openCharIndex, range.openChar, range.closeChar, range.closeCharIndex, range.isBlockComment, range.isIndentFold);
                newRange.collapsed = range.collapsed;
                newRange.cachedEndLineText = range.cachedEndLineText;
                newRange.cachedEndLineTextAttempted = range.cachedEndLineTextAttempted;
                newRanges.put(newStart, newRange);
            } else {
                changed = true;
            }
        }

        if (changed) {
            foldRanges.clear();
            foldRanges.putAll(newRanges);
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
        }
    }

    /**
     * Get the collapsed fold range that contains the specified line (exclusive of start).
     */
    public FoldRange getCollapsedRangeContainingLine(int line) {
        for (FoldRange range : foldRanges.values()) {
            if (range.collapsed && line > range.startLine && line <= range.endLine) {
                return range;
            }
        }
        return null;
    }

    /**
     * Start animation for fold marker.
     */
    public void startFoldMarkerRipple(int line) {
        FunctionLog.f("CodeFold", "startFoldMarkerRipple", line);
        animation.startFoldMarkerRipple(line);
    }

    /**
     * Start animation for fold placeholder.
     */
    public void startFoldPlaceholderRipple(int line, float left, float right) {
        FunctionLog.f("CodeFold", "startFoldPlaceholderRipple", line, left, right);
        animation.startFoldPlaceholderRipple(line, left, right);
    }

    /**
     * Fold range class.
     */
    public static class FoldRange {
        public final int startLine;
        public final int endLine;
        public final int openCharIndex;
        public final char openChar;
        public final char closeChar;
        public final int closeCharIndex;
        public final boolean isBlockComment;
        public final boolean isIndentFold;
        public boolean collapsed = false;

        // Cached end line text to avoid frequent file I/O during rendering
        public String cachedEndLineText = null;
        public boolean cachedEndLineTextAttempted = false;

        public FoldRange(int sL, int eL, int oCI, char oC, char cC, int cCI, boolean isBC, boolean isIF) {
            FunctionLog.f("FoldRange", "FoldRange", sL, eL, oCI, oC, cC, cCI, isBC, isIF);
            this.startLine = sL;
            this.endLine = eL;
            this.openCharIndex = oCI;
            this.openChar = oC;
            this.closeChar = cC;
            this.closeCharIndex = cCI;
            this.isBlockComment = isBC;
            this.isIndentFold = isIF;
        }
    }
}
