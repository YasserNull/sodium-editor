package com.yn.sodiumeditor.core.fold;

import com.yn.sodiumeditor.SodiumEditor;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
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

    // Visible-to-global line lookup — O(1) instead of O(intervals) per call
    // visibleToGlobalLookup[vi] = globalLine
    private int[] visibleToGlobalLookup = new int[0];
    private int visibleToGlobalLookupVersion = -1;

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
        this.editor = editor;
        this.detector = new CodeFoldDetector(this.editor);
        this.animation = new com.yn.sodiumeditor.renderer.animation.CodeFoldAnimation(this.editor);
        this.utils = new com.yn.sodiumeditor.utils.CodeFoldUtils(this.editor);
    }

    /**
     * Enable or disable code folding.
     */
    public void setCodeFoldingEnabled(boolean enabled) {
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
        return isCodeFoldingEnabled;
    }

    /**
     * Toggle fold at the specified line.
     */
    public boolean toggleFoldAtLine(int line) {
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
        editor.fileIO.ioHandler.post(() -> {
            FoldRange created = findFoldRangeForLine(line);
            pendingFoldComputations.remove(line);
            if (created == null) return;
            created.collapsed = true;
            
            // Pre-fetch end line text to avoid frame-time I/O if the end line is outside the window.
            utils.getEndLineTextForFold(created);
            
            editor.caret.mainHandler.post(() -> {
                foldRanges.put(created.startLine, created);
                if (created.isIndentFold) editor.indentGuides.markIntervalsDirty();
                invalidateFoldCaches();
                rebuildFoldIntervalsIfNeeded();
                
                // Check if we need to load new window content for the new visible area
                editor.fileIO.checkAndLoadWindow();
                
                editor.invalidate();
            });
        });
        return true;
    }

    /**
     * Collapse all folds.
     */
    public void collapseAllFolds() {
        if (!isCodeFoldingEnabled) return;
        for (FoldRange range : foldRanges.values()) {
            range.collapsed = true;
        }
        foldIntervalsDirty = true;
        editor.invalidate();
    }

    /**
     * Expand all folds.
     */
    public void expandAllFolds() {
        if (!isCodeFoldingEnabled) return;
        for (FoldRange range : foldRanges.values()) {
            range.collapsed = false;
        }
        foldIntervalsDirty = true;
        editor.invalidate();
    }

    /**
     * Get the fold range at the specified line.
     */
    public FoldRange getFoldRangeAtStart(int line) {
        if (!isCodeFoldingEnabled) return null;
        FoldRange range = foldRanges.get(line);
        return (range != null && range.collapsed) ? range : null;
    }

    /**
     * Check if a line is hidden by a fold.
     */
    public boolean isLineHiddenByFold(int line) {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
        rebuildFoldIntervalsIfNeeded();
        for (int[] interval : foldIntervals) {
            if (line < interval[0]) return false;
            if (line <= interval[1]) return true;
        }
        return false;
    }

    /**
     * Get the number of hidden lines.
     */
    public int getHiddenLineCount() {
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return 0;
        rebuildFoldIntervalsIfNeeded();
        int total = getFoldAwareTotalLines();
        // Check cache
        if (cachedHiddenLineCount >= 0 && !foldIntervalsDirty) {
            return cachedHiddenLineCount;
        }
        int hidden = 0;
        for (int[] interval : foldIntervals) {
            int s = interval[0];
            int e = Math.min(interval[1], total - 1);
            if (e >= s) hidden += (e - s + 1);
        }
        cachedHiddenLineCount = hidden;
        return hidden;
    }

    /**
     * Get the number of visible lines.
     */
    public int getVisibleLineCount() {
        int total = getFoldAwareTotalLines();
        int hidden = getHiddenLineCount();
        int visible = Math.max(1, total - hidden);
        return visible;
    }

    private long lastMapLogMs = 0L;

    /**
     * Map a visible index to a global line number. O(1) via lookup array.
     */
    public int mapVisibleIndexToGlobal(int visibleIndex) {
        if (!isCodeFoldingEnabled) return visibleIndex;

        rebuildFoldIntervalsIfNeeded();

        int vi = Math.max(0, visibleIndex);
        if (visibleToGlobalLookupVersion == lastVersionForTotalLines && vi < visibleToGlobalLookup.length) {
            return visibleToGlobalLookup[vi];
        }

        // Fallback: walk intervals (should rarely happen)
        int totalLines = getFoldAwareTotalLines();
        int visibleTotal = getVisibleLineCount();
        int globalLine = Math.max(0, Math.min(vi, Math.max(0, visibleTotal - 1)));
        for (int[] interval : foldIntervals) {
            int firstHiddenLine = interval[0];
            int lastHiddenLine = interval[1];
            int hiddenLineCount = lastHiddenLine - firstHiddenLine + 1;
            if (globalLine >= firstHiddenLine) {
                globalLine += hiddenLineCount;
            }
        }
        return Math.max(0, Math.min(globalLine, totalLines - 1));
    }

    /**
     * Get the visible index for a global line.
     */
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

    /**
     * Get the fold marker for a line.
     */
    public String getFoldMarkerForLine(int line, @Nullable String lineText) {
        if (!isCodeFoldingEnabled) return null;
        FoldRange range = foldRanges.get(line);
        if (range != null) return range.collapsed ? ">" : "v";
        if (lineText == null) return null;

        // Check visibility cache
        Byte cached = foldMarkerVisibilityCache.get(line);
        if (cached != null) {
            return cached == 1 ? "v" : null;
        }

        boolean isIndentCandidate = editor.indentGuides.isIndentationBlocksEnabled && isIndentFoldCandidate(lineText);
        boolean shows = isIndentCandidate || detector.shouldShowFoldMarkerFromLine(lineText);
        foldMarkerVisibilityCache.put(line, (byte) (shows ? 1 : 0));
        return shows ? "v" : null;
    }

    /**
     * Invalidate fold marker visibility cache for a specific line.
     */
    public void invalidateFoldMarkerVisibilityCache(int line) {
        foldMarkerVisibilityCache.remove(line);
    }

    /**
     * Clear all fold marker visibility cache.
     */
    public void clearFoldMarkerVisibilityCache() {
        foldMarkerVisibilityCache.clear();
    }

    /**
     * Build the display line for a folded range.
     */
    public String buildFoldDisplayLine(String line, FoldRange range, int[] placeholderBoundsOut) {
        if (range == null) return line;
        int hiddenCount = range.endLine - range.startLine;
        String suffix = " … (" + hiddenCount + ") ";
        String trimmed = line.substring(0, Math.min(range.openCharIndex + 1, line.length())).trim();
        return trimmed + suffix;
    }

    /**
     * Start a ripple animation on the fold marker.
     */
    public void startFoldMarkerRipple(int line) {
        animation.startFoldMarkerRipple(line);
    }

    /**
     * Start a ripple animation on the folded placeholder button.
     */
    public void startFoldPlaceholderRipple(int line, float left, float right) {
        animation.startFoldPlaceholderRipple(line, left, right);
    }

    /**
     * Rebuild fold intervals if needed.
     */
    public void rebuildFoldIntervalsIfNeeded() {
        if (!foldIntervalsDirty) return;
        long startMs = SystemClock.uptimeMillis();
        foldIntervalsDirty = false;
        foldIntervals.clear();
        if (!isCodeFoldingEnabled || foldRanges.isEmpty()) {
            editor.lineNumber.invalidateLineNumberCache();
            visibleToGlobalLookupVersion = -1;
            return;
        }

        boolean clampToKnownLines = editor.fileIO.isIndexReady || editor.fileIO.isEof;
        int maxLine = -1;
        if (clampToKnownLines) {
            int totalLines = editor.view.getLinesCount();
            if (totalLines <= 0) {
                totalLines = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
            }
            maxLine = Math.max(0, totalLines - 1);
        }

        for (FoldRange range : foldRanges.values()) {
            if (!range.collapsed) continue;
            int start = range.startLine + 1;
            int end = clampToKnownLines ? Math.min(range.endLine, maxLine) : range.endLine;
            if (end < start) continue;
            foldIntervals.add(new int[] {start, end});
        }
        if (foldIntervals.isEmpty()) {
            visibleToGlobalLookupVersion = -1;
            return;
        }

        Collections.sort(foldIntervals, (a, b) -> Integer.compare(a[0], b[0]));
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

        // Build visibleToGlobalLookup: walk global lines, skip hidden, assign visible indices
        int totalLines = getFoldAwareTotalLines();
        int visibleCount = getVisibleLineCount();
        if (visibleToGlobalLookup.length < visibleCount) {
            visibleToGlobalLookup = new int[visibleCount + 64];
        }
        int vi = 0;
        int intervalIdx = 0;
        for (int gi = 0; gi < totalLines && vi < visibleCount; gi++) {
            // Skip if this global line is hidden by any fold interval
            boolean hidden = false;
            while (intervalIdx < foldIntervals.size()) {
                int[] interval = foldIntervals.get(intervalIdx);
                if (gi < interval[0]) break; // past this interval
                if (gi <= interval[1]) {
                    hidden = true;
                    break;
                }
                intervalIdx++;
            }
            if (!hidden) {
                visibleToGlobalLookup[vi] = gi;
                vi++;
            }
        }
        // Fill remaining with identity
        while (vi < visibleCount) {
            visibleToGlobalLookup[vi] = vi;
            vi++;
        }
        visibleToGlobalLookupVersion = lastVersionForTotalLines;

        long dt = SystemClock.uptimeMillis() - startMs;
        if (dt > 8 && editor.DEBUG_RENDER_LOGS) {
            Log.d("SodiumRender", "foldIntervals rebuild dtMs=" + dt + " ranges=" + foldRanges.size());
        }
        // Line numbers depend on fold intervals, so invalidate cache
        editor.lineNumber.invalidateLineNumberCache();
    }

    private int cachedFileLineCount = -1;
    private int cachedFoldAwareTotalLines = -1;
    private int cachedFoldAwareTotalLinesVersion = -1;
    private int lastVersionForTotalLines = 0;
    private int cachedHiddenLineCount = -1;
    private int cachedHiddenLineCountVersion = -1;
    private int cachedVisibleLineCount = -1;
    private int cachedVisibleLineCountVersion = -1;

    public void invalidateFoldCaches() {
        foldIntervalsDirty = true;
        lastVersionForTotalLines++;
        cachedFoldAwareTotalLines = -1;
        cachedFoldAwareTotalLinesVersion = -1;
        cachedHiddenLineCount = -1;
        cachedHiddenLineCountVersion = -1;
        cachedVisibleLineCount = -1;
        cachedVisibleLineCountVersion = -1;
        cachedFileLineCount = -1;
        foldMarkerVisibilityCache.clear();
        visibleToGlobalLookupVersion = -1;
        cachedBracketStateAfterFold.clear();

        // Invalidate line number cache since fold state changed
        editor.lineNumber.invalidateLineNumberCache();
    }

    private int getFoldAwareTotalLines() {
        // Check cache validity
        int currentVersion = foldIntervalsDirty ? lastVersionForTotalLines + 1 : lastVersionForTotalLines;
        if (cachedFoldAwareTotalLines >= 0 && cachedFoldAwareTotalLinesVersion == currentVersion) {
            return cachedFoldAwareTotalLines;
        }

        int total = editor.view.getLinesCount();
        int windowEnd = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        if (!editor.fileIO.isIndexReady && !editor.fileIO.isEof) {
            // Try to get actual line count from file
            if (cachedFileLineCount < 0 && editor.fileIO.sourceFile != null) {
                cachedFileLineCount = countLinesInFile();
            }
            if (cachedFileLineCount > 0) {
                total = cachedFileLineCount;
            } else {
                // Fallback: estimate from fold ranges
                int maxFoldEnd = windowEnd;
                for (FoldRange range : foldRanges.values()) {
                    if (range.endLine > maxFoldEnd) maxFoldEnd = range.endLine;
                }
                total = Math.max(total, maxFoldEnd + 1);
            }
        }
        if (total <= 0) total = windowEnd;

        // Cache result
        lastVersionForTotalLines = currentVersion;
        cachedFoldAwareTotalLines = total;
        cachedFoldAwareTotalLinesVersion = currentVersion;
        return total;
    }

    private int countLinesInFile() {
        if (editor.fileIO.sourceFile == null) return -1;
        java.io.RandomAccessFile raf = null;
        try {
            raf = new java.io.RandomAccessFile(editor.fileIO.sourceFile, "r");
            long len = raf.length();
            if (len == 0) return 1;
            int count = 1;
            byte[] buf = new byte[8192];
            long pos = 0;
            int n;
            while (pos < len && (n = raf.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\n') count++;
                }
                pos += n;
            }
            return count;
        } catch (Exception e) {
            return -1;
        } finally {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Invalidate fold ranges for a specific line and nearby lines.
     */
    public void invalidateFoldRangeForLine(int line) {
        if (!isCodeFoldingEnabled) return;

        // Remove fold range for this line
        foldRanges.remove(line);

        // Remove any fold that STARTS at this line or ENDS at this line
        foldRanges.entrySet().removeIf(e -> {
            FoldRange range = e.getValue();
            return range.startLine == line || range.endLine == line;
        });

        // Also remove any fold that contains this line (startLine < line < endLine)
        foldRanges.entrySet().removeIf(e -> {
            FoldRange range = e.getValue();
            return range.startLine < line && range.endLine > line;
        });

        foldMarkerVisibilityCache.remove(line);
        invalidateFoldCaches();
    }

    /**
     * Adjust all fold ranges after a line insertion or deletion.
     * @param line The line that was inserted (delta=+1) or deleted (delta=-1)
     * @param delta +1 for insertion, -1 for deletion
     */
    public void adjustFoldRangesForLineEdit(int line, int delta) {
        java.util.List<Integer> keysToAdjust = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, FoldRange> entry : foldRanges.entrySet()) {
            FoldRange r = entry.getValue();
            if (r.startLine > line || r.endLine > line) {
                keysToAdjust.add(entry.getKey());
            }
        }
        for (Integer key : keysToAdjust) {
            FoldRange r = foldRanges.get(key);
            int newStart = r.startLine > line ? r.startLine + delta : r.startLine;
            int newEnd = r.endLine > line ? r.endLine + delta : r.endLine;
            // Ensure valid range
            if (newEnd < newStart) newEnd = newStart;
            int adjustedKey = key > line ? key + delta : key;
            FoldRange updated = new FoldRange(
                newStart, newEnd, r.openCharIndex, r.openChar,
                r.closeChar, r.closeCharIndex, r.isBlockComment, r.isIndentFold);
            updated.collapsed = r.collapsed;
            foldRanges.remove(key);
            if (adjustedKey >= 0) {
                foldRanges.put(adjustedKey, updated);
            }
        }
        if (!keysToAdjust.isEmpty()) {
            foldIntervalsDirty = true;
        }
    }

    /**
     * Clear all fold ranges.
     */
    public void clearAllFolds() {
        foldRanges.clear();
        foldIntervals.clear();
        invalidateFoldCaches();
    }

    /**
     * Find a fold range for a line.
     */
    public FoldRange findFoldRangeForLine(int line) {
        return detector.findFoldRangeForLine(line);
    }

    /**
     * Check if a line is an indent fold candidate.
     */
    public boolean isIndentFoldCandidate(String line) {
        return detector.isIndentFoldCandidate(line);
    }

    // ========================================================================
    // FoldRange class
    // ========================================================================

    public static final class FoldRange {
        public final int startLine;
        public final int endLine;
        public final int openCharIndex;
        public final char openChar;
        public final char closeChar;
        public final int closeCharIndex;
        public final boolean isBlockComment;
        public final boolean isIndentFold;
        public boolean collapsed;
        public String cachedEndLineText = null;
        public boolean cachedEndLineTextAttempted = false;

        public FoldRange(int startLine, int endLine, int openCharIndex, char openChar, char closeChar, int closeCharIndex, boolean isBlockComment, boolean isIndentFold) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.openCharIndex = openCharIndex;
            this.openChar = openChar;
            this.closeChar = closeChar;
            this.closeCharIndex = closeCharIndex;
            this.isBlockComment = isBlockComment;
            this.isIndentFold = isIndentFold;
            this.collapsed = false;
        }
    }

    // ========================================================================
    // Code Fold Helper Methods
    // ========================================================================

    /**
     * Check if local X position hits a fold placeholder
     */
    public boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
        return utils.isFoldPlaceholderHit(globalLine, line, localX);
    }

    /**
     * Get placeholder bounds for a folded line.
     * outBounds[0]=left, outBounds[1]=right
     */
    public boolean getFoldPlaceholderBounds(int globalLine, String line, float[] outBounds) {
        return utils.getFoldPlaceholderBounds(globalLine, line, outBounds);
    }

    /**
     * Adjust fold range indices after a line edit on the fold start line.
     */
    public void adjustFoldRangeForLineEdit(int line, int editIndex, int delta, int deleteLen) {
        utils.adjustFoldRangeForLineEdit(line, editIndex, delta, deleteLen);
    }

    /**
     * Resolve close char index for a folded range on its end line.
     */
    public int resolveCloseCharIndex(FoldRange range, @Nullable String endLineText) {
        return utils.resolveCloseCharIndex(range, endLineText);
    }

    /**
     * Get the collapsed fold range that hides the given line.
     */
    public FoldRange getCollapsedRangeContainingLine(int line) {
        if (!isCodeFoldingEnabled) return null;
        for (FoldRange range : foldRanges.values()) {
            if (range.collapsed && line > range.startLine && line <= range.endLine) {
                return range;
            }
        }
        return null;
    }

    /**
     * Clear fold ripple animation
     */
    public void clearFoldRipple() {
        animation.clearFoldRipple();
    }

    /**
     * Set fold placeholder color
     */
    public void setFoldPlaceholderColor(int color) {
        animation.foldPlaceholderPaint.setColor(color);
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Set fold marker color
     */
    public void setFoldMarkerColor(int color) {
        animation.foldMarkerColor = color;
        animation.foldMarkerPaint.setColor(color);
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Set fold marker color while fold is being computed.
     */
    public void setFoldMarkerPendingColor(int color) {
        animation.foldMarkerPendingColor = color;
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Set fold marker text size
     */
    public void setFoldMarkerTextSize(float size) {
        float base = editor.textRender.paint.getTextSize();
        if (base <= 0f) return;
        animation.foldMarkerTextScale = size / base;
        animation.updateTextSize(base * animation.foldMarkerTextScale);
        editor.requestLayout();
        if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
        editor.invalidate();
    }

    /**
     * Set indentation blocks enabled
     */
    public void setIndentationBlocksEnabled(boolean enabled) {
        if (!enabled) {
            foldRanges.entrySet().removeIf(e -> e.getValue().isIndentFold);
        }
        editor.indentGuides.markIntervalsDirty();
        foldIntervalsDirty = true;
        editor.invalidate();
    }
}
