package com.yn.sodiumeditor.core.fold;

import com.yn.sodiumeditor.SodiumEditor;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.core.guides.bracket.BracketCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages code folding functionality for the SodiumEditor.
 * Delegates detection, animation, and utilities to specialized classes.
 */
public class CodeFold {
    public static final boolean DEBUG_FOLD_LOGS = false;
    public static final String FOLD_LOG_TAG = "SodiumFoldDeep";
    private static final String FOLD_TYPING_PERF = "FoldTypingPerf";
    private static final long FOLD_TYPING_LOG_THRESHOLD_MS = 8L;

    // --- Code Fold State ---
    public boolean isCodeFoldingEnabled = false;
    public final ConcurrentHashMap<Integer, FoldRange> foldRanges = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, Boolean> pendingFoldComputations = new ConcurrentHashMap<>();
    public final ArrayList<int[]> foldIntervals = new ArrayList<>();
    private final ArrayList<FoldRange> collapsedFoldRangesByStart = new ArrayList<>();
    private final android.util.SparseArray<FoldRange> collapsedFoldRangesByEnd = new android.util.SparseArray<>();
    public boolean foldIntervalsDirty = true;
    private int foldIntervalsBuiltForTotalLines = -1;

    // Fold marker visibility cache — avoids per-frame full parse of each visible line
    private final android.util.SparseArray<Byte> foldMarkerVisibilityCache = new android.util.SparseArray<>();

    // Cached bracket state after each collapsed fold range — avoids per-frame re-scan of hidden lines
    public final java.util.HashMap<Integer, com.yn.sodiumeditor.core.guides.bracket.BracketGuideState> cachedBracketStateAfterFold = new java.util.HashMap<>();

    // Delegate classes
    public final CodeFoldDetector detector;
    public final com.yn.sodiumeditor.renderer.animation.CodeFoldAnimation animation;
    public final com.yn.sodiumeditor.utils.CodeFoldUtils utils;

    // Constants
    public static final String FOLD_PLACEHOLDER_TEXT = "<—>";

    private final SodiumEditor editor;

    public CodeFold(SodiumEditor editor) {
        this.editor = editor;
        this.detector = new CodeFoldDetector(this.editor);
        this.animation = new com.yn.sodiumeditor.renderer.animation.CodeFoldAnimation(this.editor);
        this.utils = new com.yn.sodiumeditor.utils.CodeFoldUtils(this.editor);
        log("init enabled=" + isCodeFoldingEnabled);
    }

    public static void log(String message) {
        if (DEBUG_FOLD_LOGS) {
            Log.d(FOLD_LOG_TAG, message);
        }
    }

    /**
     * Enable or disable code folding.
     */
    public void setCodeFoldingEnabled(boolean enabled) {
        log("setCodeFoldingEnabled requested=" + enabled + " current=" + isCodeFoldingEnabled
                + " lineNumbers=" + editor.lineNumber.showLineNumbers);
        if (this.isCodeFoldingEnabled == enabled) return;
        if (enabled && !editor.lineNumber.showLineNumbers) {
            log("setCodeFoldingEnabled ignored: line numbers hidden");
            return;
        }
        this.isCodeFoldingEnabled = enabled;
        if (!enabled) {
            foldRanges.clear();
            foldIntervals.clear();
        } else {
            animation.foldMarkerTextScale = 1f;
            animation.foldMarkerPaint.setTextSize(editor.textRender.paint.getTextSize() * animation.foldMarkerSizeMultiplier);
            animation.foldMarkerPendingPaint.setTextSize(editor.textRender.paint.getTextSize() * animation.foldMarkerSizeMultiplier);
            editor.bracketCache.ensureScannedAsync();
        }
        foldIntervalsDirty = true;
        editor.windowRender.recalculateMaxLineWidth();
        if (isCodeFoldingEnabled) editor.invalidate();
        log("setCodeFoldingEnabled applied=" + isCodeFoldingEnabled
                + " ranges=" + foldRanges.size()
                + " dirty=" + foldIntervalsDirty);
    }

    /**
     * Check if code folding is enabled.
     */
    public boolean isCodeFoldingEnabled() {
        return isCodeFoldingEnabled;
    }

    /**
     * Toggle fold at the specified line.
     */
    public boolean toggleFoldAtLine(int line) {
        log("toggleFoldAtLine line=" + line + " enabled=" + isCodeFoldingEnabled);
        if (!isCodeFoldingEnabled) return false;
        FoldRange existing = foldRanges.get(line);
        if (existing != null) {
            existing.collapsed = !existing.collapsed;
            log("toggleFoldAtLine range=" + existing.describe()
                    + " collapsedNow=" + existing.collapsed);
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
            
            // Check if we need to load new window content for the new visible area
            editor.fileIO.checkAndLoadWindow();
            
            editor.invalidate();
            return true;
        }
        log("toggleFoldAtLine no range line=" + line);
        return false;
    }

    /**
     * Invalidate internal fold caches (e.g., after an edit).
     */
    public void invalidateFoldCaches() {
        log("invalidateFoldCaches ranges=" + foldRanges.size()
                + " intervals=" + foldIntervals.size()
                + " cachedBracketStates=" + cachedBracketStateAfterFold.size());
        foldIntervalsDirty = true;
        foldIntervalsBuiltForTotalLines = -1;
        foldMarkerVisibilityCache.clear();
        cachedBracketStateAfterFold.clear();
        collapsedFoldRangesByStart.clear();
        collapsedFoldRangesByEnd.clear();
    }

    /**
     * Mark intervals as dirty.
     */
    public void markIntervalsDirty() {
        foldIntervalsDirty = true;
    }

    /**
     * Get total number of visible lines (accounting for collapsed folds).
     */
    public synchronized int getVisibleLineCount() {
        int totalLines = Math.max(1, editor.view.getLinesCount());
        if (!isCodeFoldingEnabled) return totalLines;
        rebuildFoldIntervalsIfNeeded();
        if (foldIntervals.isEmpty()) return totalLines;
        int[] last = foldIntervals.get(foldIntervals.size() - 1);
        return Math.max(1, Math.min(last[3] + 1, totalLines));
    }

    /**
     * Maps a visible line index to its global document line number.
     */
    public synchronized int mapVisibleIndexToGlobal(int visibleIndex) {
        int totalLines = Math.max(1, editor.view.getLinesCount());
        if (!isCodeFoldingEnabled) return Math.max(0, Math.min(visibleIndex, totalLines - 1));
        rebuildFoldIntervalsIfNeeded();
        if (foldIntervals.isEmpty()) return Math.max(0, Math.min(visibleIndex, totalLines - 1));
        
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
                return Math.max(0, Math.min(interval[0] + (visibleIndex - interval[2]), totalLines - 1));
            }
        }
        return Math.max(0, Math.min(visibleIndex, totalLines - 1));
    }

    /**
     * Maps a global line number to its visible index.
     */
    public synchronized int getVisibleIndexForGlobalLine(int globalLine) {
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
    public synchronized boolean isLineHidden(int globalLine) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
        return getVisibleIndexForGlobalLine(globalLine) < 0;
    }

    /**
     * Check if a line is the start of a fold range.
     */
    public boolean isFoldStart(int globalLine) {
        if (!isCodeFoldingEnabled) return false;
        
        Byte cached = foldMarkerVisibilityCache.get(globalLine);
        if (cached != null) return cached == 1;

        boolean isStart = foldRanges.containsKey(globalLine);
        foldMarkerVisibilityCache.put(globalLine, isStart ? (byte)1 : (byte)0);
        return isStart;
    }

    /**
     * Get fold range starting at the specified line.
     */
    public FoldRange getFoldRangeAtStart(int line) {
        return foldRanges.get(line);
    }

    /**
     * Clear all fold ranges.
     */
    public void clearFoldRanges() {
        foldRanges.clear();
        invalidateFoldCaches();
    }

    /**
     * Invalidates fold ranges in the specified global line range.
     */
    public void invalidateFoldRangesInRange(int start, int end) {
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
    public synchronized void rebuildFoldIntervalsIfNeeded() {
        int currentTotal = editor.view.getLinesCount();
        if (!foldIntervalsDirty && foldIntervalsBuiltForTotalLines == currentTotal) return;
        
        long startMs = SystemClock.uptimeMillis();
        log("rebuildFoldIntervals start total=" + currentTotal
                + " ranges=" + foldRanges.size()
                + " dirty=" + foldIntervalsDirty);
        foldIntervals.clear();
        collapsedFoldRangesByStart.clear();
        collapsedFoldRangesByEnd.clear();
        int total = currentTotal;
        if (total <= 0) {
            foldIntervalsDirty = false;
            foldIntervalsBuiltForTotalLines = total;
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
            collapsedFoldRangesByStart.add(range);
            collapsedFoldRangesByEnd.put(range.endLine, range);
            log("rebuildFoldIntervals collapsedRange=" + range.describe());

            if (startLine > currentLine) {
                int len = startLine - currentLine;
                foldIntervals.add(new int[]{currentLine, startLine - 1, currentVisible, currentVisible + len - 1});
                log("rebuildFoldIntervals visibleInterval global="
                        + currentLine + "->" + (startLine - 1)
                        + " visible=" + currentVisible + "->" + (currentVisible + len - 1));
                currentVisible += len;
            }
            
            // Interval for the collapsed line itself
            foldIntervals.add(new int[]{startLine, startLine, currentVisible, currentVisible});
            log("rebuildFoldIntervals foldedInterval global=" + startLine
                    + " visible=" + currentVisible);
            currentVisible++;
            currentLine = range.endLine + 1;
        }

        if (currentLine < total) {
            int len = total - currentLine;
            foldIntervals.add(new int[]{currentLine, total - 1, currentVisible, currentVisible + len - 1});
            log("rebuildFoldIntervals tailInterval global="
                    + currentLine + "->" + (total - 1)
                    + " visible=" + currentVisible + "->" + (currentVisible + len - 1));
        }

        foldIntervalsDirty = false;
        foldIntervalsBuiltForTotalLines = total;
        long dt = SystemClock.uptimeMillis() - startMs;
        log("rebuildFoldIntervals done total=" + total
                + " intervals=" + foldIntervals.size()
                + " collapsed=" + collapsedFoldRangesByStart.size()
                + " dtMs=" + dt);
        if (dt >= FOLD_TYPING_LOG_THRESHOLD_MS) {
        }
    }

    /**
     * Hit test for fold placeholders.
     */
    public boolean isFoldPlaceholderHit(int line, String ln, float x) {
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
     * Measure the visual width of a collapsed fold line as it is actually rendered.
     */
    public float getCollapsedFoldVisualWidth(FoldRange range, @Nullable String startLineText, @Nullable String endLineText) {
        long startMs = SystemClock.uptimeMillis();
        if (range == null || !range.collapsed) return 0f;

        String startText = (startLineText != null) ? startLineText : editor.windowRender.getLineTextForRender(range.startLine);
        if (startText == null) startText = "";

        int prefixEnd =
                range.isBlockComment
                        ? Math.min(range.openCharIndex + 2, startText.length())
                        : range.isIndentFold
                                ? startText.length()
                                : Math.min(range.openCharIndex + 1, startText.length());

        float width = editor.highlite.measureHighlightedSegmentWidth(startText, range.startLine, 0, prefixEnd);
        width += Math.max(0f, editor.textRender.paint.measureText(FOLD_PLACEHOLDER_TEXT));

        if (range.isIndentFold) {
            return width;
        }

        String close = range.isBlockComment ? "*/" : String.valueOf(range.closeChar);
        width += editor.textRender.paint.measureText(close);

        String endText = (endLineText != null) ? endLineText : editor.windowRender.getLineTextForRender(range.endLine);
        if (endText == null) endText = "";

        int closeIdx = resolveCloseCharIndex(range, endText);
        if (closeIdx < 0) closeIdx = range.closeCharIndex;

        int suffixStart;
        if (range.isBlockComment) {
            suffixStart = Math.min(endText.length(), Math.max(0, closeIdx + 2));
        } else {
            suffixStart = Math.min(endText.length(), Math.max(0, closeIdx + 1));
        }

        if (suffixStart < endText.length()) {
            width += editor.highlite.measureHighlightedSegmentWidth(
                    endText, range.endLine, suffixStart, endText.length());
        }
        long dt = SystemClock.uptimeMillis() - startMs;
        if (dt >= FOLD_TYPING_LOG_THRESHOLD_MS) {
        }
        return width;
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
        if (editor.indentGuides.isIndentationBlocksEnabled == enabled) return;
        editor.indentGuides.isIndentationBlocksEnabled = enabled;
        if (isCodeFoldingEnabled) {
            editor.bracketCache.clear();
            clearFoldRanges();
            editor.bracketCache.scanFileAsync();
        }
    }

    /**
     * Get the fold marker symbol (+ or -) for a line.
     */
    public String getFoldMarkerForLine(int line, String text) {
        if (!isCodeFoldingEnabled) {
            log("getFoldMarker line=" + line + " disabled");
            return null;
        }
        FoldRange range = foldRanges.get(line);
        boolean isRtl = editor.textRender.isRtl;
        if (range != null) {
            if (range.collapsed) {
                // Closed/Collapsed -> Down (v)
                log("getFoldMarker line=" + line
                        + " marker=v"
                        + " textLen=" + (text == null ? -1 : text.length())
                        + " range=" + range.describe());
                return "v";
            } else {
                // Open/Expanded -> > (or < in RTL)
                String marker = isRtl ? "<" : ">";
                log("getFoldMarker line=" + line
                        + " marker=" + marker
                        + " textLen=" + (text == null ? -1 : text.length())
                        + " range=" + range.describe());
                return marker;
            }
        }
        log("getFoldMarker line=" + line
                + " marker=null"
                + " textLen=" + (text == null ? -1 : text.length())
                + " ranges=" + foldRanges.size());
        return null;
    }

    /**
     * Invalidates the fold range starting at the specified line.
     */
    public void invalidateFoldRangeForLine(int line) {
        boolean changed = false;
        for (Integer start : new ArrayList<>(foldRanges.keySet())) {
            FoldRange range = foldRanges.get(start);
            if (range == null) continue;
            if (line >= range.startLine && line <= range.endLine) {
                foldRanges.remove(start);
                changed = true;
            }
        }
        if (changed) {
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
        }
    }

    /**
     * Rebuild confirmed bracket fold ranges affected by a recently edited line.
     */
    public void refreshFoldRangesAroundLine(int line) {
        if (!isCodeFoldingEnabled) return;

        BracketCache.LineBracketInfo info = editor.bracketCache.getLineInfo(line);
        boolean changed = false;
        for (BracketCache.BracketPosition bp : info.brackets) {
            if (editor.bracketCache.isInStringOrComment(bp.line, bp.column)) continue;
            if (bp.isOpening) {
                BracketCache.BracketPosition close = editor.bracketCache.findMatchingBracket(bp);
                changed |= putConfirmedBracketFoldRange(bp, close);
            } else {
                BracketCache.BracketPosition open = editor.bracketCache.findMatchingOpeningBracket(bp);
                changed |= putConfirmedBracketFoldRange(open, bp);
            }
        }

        if (changed) {
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
            editor.invalidate();
        }
    }

    public void refreshFoldRangesAroundRange(int startLine, int endLine) {
        if (!isCodeFoldingEnabled) return;
        int start = Math.max(0, Math.min(startLine, endLine));
        int end = Math.max(startLine, endLine);
        int total = Math.max(1, editor.view.getLinesCount());
        end = Math.min(Math.max(start, end), total - 1);

        editor.bracketCache.invalidateLines(start, end);
        boolean changed = false;
        for (int line = start; line <= end; line++) {
            BracketCache.LineBracketInfo info = editor.bracketCache.getLineInfo(line);
            for (BracketCache.BracketPosition bp : info.brackets) {
                if (editor.bracketCache.isInStringOrComment(bp.line, bp.column)) continue;
                if (bp.isOpening) {
                    BracketCache.BracketPosition close = editor.bracketCache.findMatchingBracket(bp);
                    changed |= putConfirmedBracketFoldRange(bp, close);
                } else {
                    BracketCache.BracketPosition open = editor.bracketCache.findMatchingOpeningBracket(bp);
                    changed |= putConfirmedBracketFoldRange(open, bp);
                }
            }
        }

        if (changed) {
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
            editor.invalidate();
        }
    }

    private boolean putConfirmedBracketFoldRange(
            @Nullable BracketCache.BracketPosition open,
            @Nullable BracketCache.BracketPosition close) {
        if (open == null || close == null) return false;
        if (!open.isOpening || close.isOpening) return false;
        if (BracketCache.BracketPosition.getMatchingBracket(open.bracket) != close.bracket) return false;
        if (close.line <= open.line) return false;

        FoldRange old = foldRanges.get(open.line);
        if (old != null
                && old.endLine == close.line
                && old.openCharIndex == open.column
                && old.openChar == open.bracket
                && old.closeChar == close.bracket
                && old.closeCharIndex == close.column
                && !old.isBlockComment
                && !old.isIndentFold) {
            return false;
        }

        FoldRange updated =
                new FoldRange(
                        open.line,
                        close.line,
                        open.column,
                        open.bracket,
                        close.bracket,
                        close.column,
                        false,
                        false);
        if (old != null) {
            updated.collapsed = old.collapsed;
        }
        foldRanges.put(open.line, updated);
        return true;
    }

    /**
     * Invalidates all folds that intersect a changed line range.
     */
    public void invalidateFoldRangesIntersectingRange(int startLine, int endLine) {
        if (foldRanges.isEmpty()) return;
        int start = Math.min(startLine, endLine);
        int end = Math.max(startLine, endLine);
        boolean changed = false;
        for (Integer foldStart : new ArrayList<>(foldRanges.keySet())) {
            FoldRange range = foldRanges.get(foldStart);
            if (range == null) continue;
            if (range.startLine <= end && range.endLine >= start) {
                foldRanges.remove(foldStart);
                changed = true;
            }
        }
        if (changed) {
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
        if (lineDelta == 0) return;
        if (foldRanges.isEmpty()) {
            // Structural edits still change visible-line mapping even when no folds exist yet.
            invalidateFoldCaches();
            rebuildFoldIntervalsIfNeeded();
            return;
        }

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
    public synchronized FoldRange getCollapsedRangeContainingLine(int line) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return null;
        rebuildFoldIntervalsIfNeeded();
        int lo = 0;
        int hi = collapsedFoldRangesByStart.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            FoldRange range = collapsedFoldRangesByStart.get(mid);
            if (line <= range.startLine) {
                hi = mid - 1;
            } else if (line > range.endLine) {
                lo = mid + 1;
            } else {
                return range;
            }
        }
        return null;
    }

    public synchronized FoldRange getCollapsedRangeEndingAtLine(int line) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return null;
        rebuildFoldIntervalsIfNeeded();
        return collapsedFoldRangesByEnd.get(line);
    }

    public FoldRange getFoldRangeEndingAtLine(int line) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return null;
        FoldRange best = null;
        for (FoldRange range : foldRanges.values()) {
            if (range == null || range.endLine != line) continue;
            if (best == null || range.startLine > best.startLine) {
                best = range;
            }
        }
        return best;
    }

    /**
     * Start animation for fold marker.
     */
    public void startFoldMarkerRipple(int line) {
        animation.startFoldMarkerRipple(line);
    }

    /**
     * Start animation for fold placeholder.
     */
    public void startFoldPlaceholderRipple(int line, float left, float right) {
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
            this.startLine = sL;
            this.endLine = eL;
            this.openCharIndex = oCI;
            this.openChar = oC;
            this.closeChar = cC;
            this.closeCharIndex = cCI;
            this.isBlockComment = isBC;
            this.isIndentFold = isIF;
            CodeFold.log("FoldRange created " + describe());
        }

        public String describe() {
            return startLine
                    + "->"
                    + endLine
                    + " openIdx="
                    + openCharIndex
                    + " open="
                    + openChar
                    + " close="
                    + closeChar
                    + " closeIdx="
                    + closeCharIndex
                    + " blockComment="
                    + isBlockComment
                    + " indent="
                    + isIndentFold
                    + " collapsed="
                    + collapsed;
        }
    }
}
