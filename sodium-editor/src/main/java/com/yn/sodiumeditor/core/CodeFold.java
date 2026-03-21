package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.cache.*;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages code folding functionality for the SodiumEditor.
 */
public class CodeFold {

    // --- Code Fold State ---
    public boolean isCodeFoldingEnabled =true;
    public final ConcurrentHashMap<Integer, FoldRange> foldRanges = new ConcurrentHashMap<>();
    public final ArrayList<int[]> foldIntervals = new ArrayList<>();
    public boolean foldIntervalsDirty = true;

    // Fold marker appearance
    public float foldMarkerGutterWidth = 0f;
    public float foldMarkerTextScale = 1f;
    public float foldMarkerSpacing = 0f;
    public float foldMarkerEdgePadding = 4f;
    public float foldPlaceholderCorner = 3f;
    public float foldPlaceholderPadX = 3f;
    public float foldPlaceholderPadY = 2f;
    public final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF foldPlaceholderRect = new RectF();

    // Fold ripple animation
    public ValueAnimator foldRippleAnimator;
    public int foldRippleLine = -1;
    public float foldRippleRadius = 0f;
    public float foldRippleAlpha = 0f;
    public float foldRippleMaxRadius = 0f;

    // Constants
    public static final int INDENT_FOLD_SCAN_LIMIT = 2000;
    public static final String FOLD_PLACEHOLDER_TEXT = "<—>";

    private final SodiumEditor editor;

    public CodeFold(SodiumEditor editor) {
        this.editor = editor;

        foldPlaceholderPaint.setColor(0xFFE0E0E0);
        foldPlaceholderPaint.setStyle(Paint.Style.FILL);
        foldMarkerPaint.setColor(0xFF888888);
        foldMarkerPaint.setTextAlign(editor.textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
        foldMarkerPaint.setTextSize(editor.textRender.paint.getTextSize());
        foldRipplePaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Enable or disable code folding.
     */
    public void setCodeFoldingEnabled(boolean enabled) {
        if (this.isCodeFoldingEnabled == enabled) return;
        this.isCodeFoldingEnabled = enabled;
        if (!enabled) {
            foldRanges.clear();
            foldIntervals.clear();
        }
        foldIntervalsDirty = true;
        editor.updateTextSizeDependentMetrics();
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
        FoldRange existing = foldRanges.get(line);
        if (existing != null) {
            existing.collapsed = !existing.collapsed;
            foldIntervalsDirty = true;
            editor.invalidate();
            return true;
        }

        FoldRange created = findFoldRangeForLine(line);
        if (created == null) return false;
        created.collapsed = true;
        foldRanges.put(created.startLine, created);
        if (created.isIndentFold) editor.indentGuides.markIntervalsDirty();
        foldIntervalsDirty = true;
        editor.invalidate();
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
        int total = editor.getLinesCount();
        if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
        int hidden = 0;
        for (int[] interval : foldIntervals) {
            int s = interval[0];
            int e = Math.min(interval[1], total - 1);
            if (e >= s) hidden += (e - s + 1);
        }
        return hidden;
    }

    /**
     * Get the number of visible lines.
     */
    public int getVisibleLineCount() {
        int total = editor.getLinesCount();
        if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
        int visible = Math.max(1, total - getHiddenLineCount());
        return visible;
    }

    /**
     * Map a visible index to a global line number.
     */
    public int mapVisibleIndexToGlobal(int visibleIndex) {
        if (!isCodeFoldingEnabled) return visibleIndex;

        // Rebuild fold intervals if they are marked as dirty.
        rebuildFoldIntervalsIfNeeded();

        // Calculate the total number of lines in the document.
        int totalLines = editor.getLinesCount();
                    if (totalLines <= 0) totalLines = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();        
        // Get the total number of lines that are currently visible (not hidden by folds).
        int visibleTotal = getVisibleLineCount();
        
        // Clamp the input visibleIndex to ensure it's within the valid range of visible lines.
        // If visibleTotal is 0 or 1, it ensures we don't go out of bounds.
        int clampedVisibleIndex = Math.max(0, Math.min(visibleIndex, Math.max(0, visibleTotal - 1)));

        // Initialize the global line number with the clamped visible index.
        // This is the starting point, assuming no folds affect it initially.
        int globalLine = clampedVisibleIndex;

        // Iterate through all the collapsed fold intervals.
        // Each interval represents a range of HIDDEN lines: [firstHiddenLine, lastHiddenLine].
        for (int[] interval : foldIntervals) {
            int firstHiddenLine = interval[0];
            int lastHiddenLine = interval[1];
            int hiddenLineCount = lastHiddenLine - firstHiddenLine + 1;

            // If the current calculated globalLine is less than the start of this hidden interval,
            // it means this interval and subsequent ones do not affect the current globalLine calculation.
            // We can break the loop as intervals are sorted by their start line.
            if (globalLine < firstHiddenLine) {
                break;
            }

            // If the current globalLine is greater than or equal to the start of the hidden interval,
            // it implies that the target visible line falls *after* some hidden lines.
            // We need to advance the globalLine number by the count of lines hidden in this interval.
            // This effectively skips over the hidden lines.
            globalLine += hiddenLineCount;
        }
        
        // Finally, clamp the calculated globalLine to ensure it's within the actual document bounds.
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
        boolean isIndentCandidate = editor.isIndentationBlocksEnabled && isIndentFoldCandidate(lineText);
        if (!isIndentCandidate && !shouldShowFoldMarkerFromLine(lineText)) return null;
        FoldRange found = findFoldRangeForLine(line);
        if (found == null) return null;
        foldRanges.put(found.startLine, found);
        if (found.isIndentFold) editor.indentGuides.markIntervalsDirty();
        foldIntervalsDirty = true;
        return "v";
    }

    /**
     * Draw fold markers for visible lines.
     */
    public void drawFoldMarkersForVisibleLines(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!isCodeFoldingEnabled) return;

        float markerX = editor.textRender.isRtl
                ? (editor.lineNumber.getGutterStartX() + editor.lineNumber.gutterSeparatorWidth + foldMarkerEdgePadding)
                : (editor.lineNumber.getGutterStartX()
                + editor.lineNumber.lineNumbersGutterWidth
                - editor.lineNumber.gutterSeparatorWidth
                - foldMarkerEdgePadding);

        for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int line = mapVisibleIndexToGlobal(v);
            String marker = getFoldMarkerForLine(line, editor.getLineTextForRender(line));
            if (marker == null) continue;
            float y = Math.round(v * editor.textRender.lineHeight - editor.scroll.scrollY + editor.textRender.lineHeight - editor.textRender.paint.descent());
            if (line == foldRippleLine && foldRippleAlpha > 0f) {
                int base = foldMarkerPaint.getColor();
                int alpha = Math.min(255, Math.max(0, (int) (255f * foldRippleAlpha)));
                foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
                float centerY = Math.round(v * editor.textRender.lineHeight - editor.scroll.scrollY + editor.textRender.lineHeight * 0.5f);
                canvas.drawCircle(markerX, centerY, foldRippleRadius, foldRipplePaint);
            }
            canvas.drawText(marker, markerX, y, foldMarkerPaint);
        }
    }

    /**
     * Draw a folded line placeholder.
     */
    public void drawFoldedLine(Canvas canvas, String line, int globalLine) {
        FoldRange range = foldRanges.get(globalLine);
        if (range == null || !range.collapsed) return;
        if (line == null) line = "";

        // Calculate prefix end based on fold type
        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }

        // Get Y position using editor's draw coordinate system
        float lineTop = editor.textRender.getDrawLineTop(globalLine);
        float lineBottom = editor.textRender.getDrawLineBottom(globalLine);
        float y = lineTop + editor.textRender.lineHeight - editor.textRender.paint.descent();

        // Draw prefix (the part before the fold)
        editor.drawHighlightedSegment(canvas, line, globalLine, 0, prefixEnd, 0f, y);

        // Draw placeholder button
        String placeholderText = FOLD_PLACEHOLDER_TEXT;
        editor.textRender.paint.getTextBounds(placeholderText, 0, placeholderText.length(), editor.textRender.textBounds);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(placeholderText));
        float xStart = editor.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float padY = foldPlaceholderPadY;
        float placeholderLeft = xStart;
        float placeholderTop = lineTop + padY;
        float placeholderRight = xStart + placeholderWidth;
        float placeholderBottom = lineBottom - padY;

        foldPlaceholderRect.set(placeholderLeft, placeholderTop, placeholderRight, placeholderBottom);
        foldMarkerPaint.setColor(0xFFE0E0E0);
        canvas.drawRoundRect(foldPlaceholderRect, foldPlaceholderCorner, foldPlaceholderCorner, foldMarkerPaint);
        editor.textRender.paint.setUnderlineText(false);
        canvas.drawText(placeholderText, placeholderLeft, y, editor.textRender.paint);

        // Draw suffix (closing bracket or */)
        float xAfter = placeholderLeft + placeholderWidth;
        if (range.isBlockComment) {
            Paint commentPaint = (editor.blockCommentHighlightRule != null) ? editor.blockCommentHighlightRule.paint : editor.textRender.paint;
            commentPaint.setUnderlineText(false);
            canvas.drawText("*/", xAfter, y, commentPaint);
        } else if (!range.isIndentFold) {
            canvas.drawText(String.valueOf(range.closeChar), xAfter, y, editor.textRender.paint);
        }
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
        if (!isCodeFoldingEnabled || !editor.lineNumber.showLineNumbers) return;
        foldRippleLine = line;
        float gutterWidth = foldMarkerGutterWidth;
        if (gutterWidth <= 0f) {
            gutterWidth = foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
        }
        foldRippleMaxRadius = Math.max(editor.textRender.lineHeight * 0.35f, Math.min(editor.textRender.lineHeight * 0.6f, gutterWidth * 0.6f));
        if (foldRippleAnimator != null) foldRippleAnimator.cancel();
        foldRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        foldRippleAnimator.setDuration(220);
        foldRippleAnimator.setInterpolator(new DecelerateInterpolator());
        foldRippleAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            foldRippleRadius = foldRippleMaxRadius * t;
            foldRippleAlpha = 0.35f * (1f - t);
            editor.invalidate();
        });
        foldRippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                foldRippleAlpha = 0f;
                foldRippleLine = -1;
            }
        });
        foldRippleAnimator.start();
    }

    /**
     * Rebuild fold intervals if needed.
     */
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
        
        // Invalidate bracket cache for this line and nearby
        editor.bracketCache.invalidateLines(Math.max(0, line - 1), line + 2);
        foldIntervalsDirty = true;
    }

    /**
     * Clear all fold ranges.
     */
    public void clearAllFolds() {
        foldRanges.clear();
        foldIntervals.clear();
        foldIntervalsDirty = true;
    }

    /**
     * Find a fold range for a line.
     */
    public FoldRange findFoldRangeForLine(int line) {
        if (!isCodeFoldingEnabled) return null;
        if (line < 0) return null;

        // First check indent-based folds
        String ln = editor.getLineTextForRender(line);
        if (ln == null) ln = "";

        if (editor.isIndentationBlocksEnabled && isIndentFoldCandidate(ln)) {
            FoldRange indentRange = findIndentFoldRangeForLine(line, null);
            if (indentRange != null) return indentRange;
        }

        // Use bracket cache for bracket-based folds
        BracketCache.LineBracketInfo info = editor.bracketCache.getLineInfo(line);
        for (BracketCache.BracketPosition bp : info.brackets) {
            if (bp.isOpening && !editor.bracketCache.isInStringOrComment(line, bp.column)) {
                BracketCache.BracketPosition match = editor.bracketCache.findMatchingBracket(bp);
                if (match != null && match.line > line) {
                    return new FoldRange(line, match.line, bp.column, bp.bracket, match.bracket, false, false);
                }
            }
        }

        return null;
    }

    /**
     * Check if a line is an indent fold candidate.
     */
    public boolean isIndentFoldCandidate(String line) {
        if (line == null || line.isEmpty()) return false;
        String trimmed = rstripWhitespace(line);
        return !trimmed.isEmpty() && trimmed.endsWith(":");
    }

    // --- Helper methods ---

    private String getLineTextForFoldScan(int line, @Nullable RandomAccessFile raf) {
        if (line < 0) return null;
        String mod = editor.textRender.modifiedLines.get(line);
        if (mod != null) return mod;
        if (line >= editor.textRender.windowStartLine && line < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
            String text = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
            return (text != null) ? text : "";
        }
        if (raf != null && editor.fileIO.isIndexReady) {
            long offset;
            synchronized (editor.fileIO.lineOffsetsLock) {
                if (line < 0 || line >= editor.fileIO.lineOffsets.length) return null;
                offset = editor.fileIO.lineOffsets[line];
            }
            try {
                return readLineUtf8AtByte(raf, offset);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private FoldRange findIndentFoldRangeForLine(int line, @Nullable RandomAccessFile raf) {
        if (!editor.isIndentationBlocksEnabled) return null;
        String ln = getLineTextForFoldScan(line, raf);
        if (ln == null) return null;
        String trimmed = rstripWhitespace(ln);
        if (trimmed.isEmpty() || !trimmed.endsWith(":")) return null;

        int baseIndent = getIndentWidth(ln);
        int totalLines = editor.getLinesCount();
        if (totalLines <= 0) totalLines = Math.max(line + 1, editor.textRender.windowStartLine + editor.textRender.linesWindow.size());

        int endLine = -1;
        int scanEnd = Math.min(totalLines, line + INDENT_FOLD_SCAN_LIMIT);
        for (int i = line + 1; i < scanEnd; i++) {
            String next = getLineTextForFoldScan(i, raf);
            if (next == null) break;
            String nextTrimmed = rstripWhitespace(next);
            if (nextTrimmed.isEmpty()) continue;
            int indent = getIndentWidth(next);
            if (indent <= baseIndent) {
                endLine = i - 1;
                break;
            }
            endLine = i;
        }

        if (endLine > line) {
            int openIdx = Math.max(0, trimmed.length() - 1);
            return new FoldRange(line, endLine, openIdx, ':', ':', false, true);
        }
        return null;
    }

    private FoldToken findFoldTokenInLine(String line, int startIndex) {
        if (line == null || line.isEmpty()) return null;
        int len = line.length();
        int i = Math.max(0, startIndex);
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int stringState = 0;

        while (i < len) {
            if (inLineComment) break;

            if (inBlockComment) {
                int end = findBlockCommentEnd(line, i);
                if (end < 0) return null;
                i = end + 2;
                inBlockComment = false;
                continue;
            }

            if (stringState != 0) {
                SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, stringState);
                if (!endResult.found) return null;
                i = endResult.endIndex;
                stringState = 0;
                continue;
            }

            if (editor.highlite.isLineCommentStart(line, i)) {
                inLineComment = true;
                break;
            }

            if (editor.highlite.isBlockCommentsEnabled
                    && i + 1 < len
                    && line.charAt(i) == '/'
                    && line.charAt(i + 1) == '*'
                    && !isTokenEscaped(line, i)) {
                return new FoldToken(i, true, '/');
            }

            if (editor.highlite.isTripleQuoteStringsEnabled && editor.highlite.isTripleQuoteStart(line, i) && !editor.highlite.isEscaped(line, i)) {
                int end = editor.highlite.findTripleQuoteEnd(line, i + 3);
                if (end < 0) return null;
                i = end + 3;
                continue;
            }

            char c = line.charAt(i);
            if (editor.highlite.isStringDelimiter(c) && !editor.highlite.isEscaped(line, i)) {
                int end = editor.highlite.findStringEnd(line, i + 1, c);
                if (end < 0) return null;
                i = end + 1;
                continue;
            }

            // Only check for OPENING brackets, not closing brackets
            if (!editor.highlite.isEscaped(line, i)) {
                if (c == '{') return new FoldToken(i, false, c);
            }
            i++;
        }
        // Second pass for ( and [ only (not closing brackets)
        for (int j = Math.max(0, startIndex); j < len; j++) {
            char c = line.charAt(j);
            if (!editor.highlite.isEscaped(line, j)) {
                if (c == '(' || c == '[') return new FoldToken(j, false, c);
            }
        }
        return null;
    }

    private int findBlockCommentEndLine(int startLine, int startChar, @Nullable RandomAccessFile raf) {
        String line = getLineTextForFoldScan(startLine, raf);
        if (line == null) return startLine;
        int end = findBlockCommentEnd(line, startChar);
        if (end >= 0) return startLine;

        int totalLines = editor.getLinesCount();
        if (totalLines <= 0) totalLines = Math.max(startLine + 1, editor.textRender.windowStartLine + editor.textRender.linesWindow.size());

        for (int i = startLine + 1; i < totalLines; i++) {
            String ln = getLineTextForFoldScan(i, raf);
            if (ln == null) break;
            end = findBlockCommentEnd(ln, 0);
            if (end >= 0) return i;
        }
        return startLine;
    }

    private int findBlockCommentEnd(String line, int startIndex) {
        int i = startIndex;
        while (i + 1 < line.length()) {
            if (line.charAt(i) == '*' && line.charAt(i + 1) == '/') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private FoldMatch findMatchingBracketFrom(int startLine, int startChar, char openBracket, @Nullable RandomAccessFile raf) {
        char closeBracket = getClosingBracket(openBracket);
        int depth = 1;
        String line = getLineTextForFoldScan(startLine, raf);
        if (line == null) return null;

        int i = startChar + 1;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int stringState = 0;

        while (true) {
            while (i < line.length()) {
                if (inLineComment) {
                    i = line.length();
                    break;
                }
                if (inBlockComment) {
                    int end = findBlockCommentEnd(line, i);
                    if (end < 0) {
                        i = line.length();
                        break;
                    }
                    i = end + 2;
                    inBlockComment = false;
                    continue;
                }
                if (stringState != 0) {
                    SodiumEditor.StringEndResult result = editor.findStringEndForState(line, i, stringState);
                    if (!result.found) {
                        i = line.length();
                        break;
                    }
                    i = result.endIndex;
                    stringState = 0;
                    continue;
                }

                if (editor.highlite.isLineCommentStart(line, i)) {
                    inLineComment = true;
                    continue;
                }
                if (editor.highlite.isBlockCommentsEnabled && i + 1 < line.length() && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i += 2;
                    continue;
                }

                char c = line.charAt(i);
                if (editor.highlite.isStringDelimiter(c) && !editor.highlite.isEscaped(line, i)) {
                    stringState = editor.getStringStateForDelimiter(c);
                    if (stringState == 0) {
                        int end = editor.highlite.findStringEnd(line, i + 1, c);
                        if (end < 0) {
                            i = line.length();
                            break;
                        }
                        i = end + 1;
                    }
                    continue;
                }

                if (c == openBracket && !editor.highlite.isEscaped(line, i)) {
                    depth++;
                } else if (c == closeBracket && !editor.highlite.isEscaped(line, i)) {
                    depth--;
                    if (depth == 0) {
                        return new FoldMatch(startLine, closeBracket);
                    }
                }
                i++;
            }

            startLine++;
            line = getLineTextForFoldScan(startLine, raf);
            if (line == null) break;
            i = 0;
            inLineComment = false;
        }
        return null;
    }

    private char getClosingBracket(char open) {
        switch (open) {
            case '(': return ')';
            case '[': return ']';
            case '{': return '}';
            default: return open;
        }
    }

    private int getIndentWidth(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    private String rstripWhitespace(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }

    private boolean shouldShowFoldMarkerFromLine(String line) {
        if (line == null || line.isEmpty()) return false;
        // Only show fold marker if line has an OPENING bracket (not escaped)
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // Skip escaped characters
            if (isTokenEscaped(line, i)) continue;
            // Skip string content
            if (editor.highlite.isStringDelimiter(c)) {
                int end = editor.highlite.findStringEnd(line, i + 1, c);
                if (end < 0) return false;
                i = end;
                continue;
            }
            // Check for opening brackets only
            if (c == '{' || c == '(' || c == '[') return true;
        }
        return false;
    }

    private boolean isTokenEscaped(String line, int index) {
        if (index <= 0) return false;
        int count = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return (count % 2) != 0;
    }

    private String readLineUtf8AtByte(RandomAccessFile raf, long offset) throws Exception {
        raf.seek(offset);
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = raf.read()) != -1 && b != '\n') {
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }

    // --- FoldRange class ---
    public static final class FoldRange {
        public final int startLine;
        public final int endLine;
        public final int openCharIndex;
        public final char openChar;
        public final char closeChar;
        public final boolean isBlockComment;
        public final boolean isIndentFold;
        public boolean collapsed;

        public FoldRange(int startLine, int endLine, int openCharIndex, char openChar, char closeChar, boolean isBlockComment, boolean isIndentFold) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.openCharIndex = openCharIndex;
            this.openChar = openChar;
            this.closeChar = closeChar;
            this.isBlockComment = isBlockComment;
            this.isIndentFold = isIndentFold;
            this.collapsed = false;
        }
    }

    // --- FoldToken class ---
    public static final class FoldToken {
        final int index;
        final boolean isBlockComment;
        final char openChar;

        FoldToken(int index, boolean isBlockComment, char openChar) {
            this.index = index;
            this.isBlockComment = isBlockComment;
            this.openChar = openChar;
        }
    }

    // --- FoldMatch class ---
    public static final class FoldMatch {
        final int endLine;
        final char closeChar;

        FoldMatch(int endLine, char closeChar) {
            this.endLine = endLine;
            this.closeChar = closeChar;
        }
    }

    // ========================================================================
    // Code Fold Helper Methods
    // ========================================================================

    /**
     * Check if local X position hits a fold placeholder
     */
    public boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
        if (!isCodeFoldingEnabled) return false;
        FoldRange range = foldRanges.get(globalLine);
        if (range == null || !range.collapsed) return false;
        if (line == null) line = "";

        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }
        float xStart = editor.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(FOLD_PLACEHOLDER_TEXT));
        float pad = Math.max(0f, foldPlaceholderPadX);
        float left = xStart - pad;
        float right = xStart + placeholderWidth + pad;
        return localX >= left && localX <= right;
    }

    /**
     * Clear fold ripple animation
     */
    public void clearFoldRipple() {
        if (foldRippleAnimator != null) foldRippleAnimator.cancel();
        foldRippleAlpha = 0f;
        foldRippleRadius = 0f;
        foldRippleLine = -1;
    }

    /**
     * Set fold placeholder color
     */
    public void setFoldPlaceholderColor(int color) {
        foldPlaceholderPaint.setColor(color);
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Set fold marker color
     */
    public void setFoldMarkerColor(int color) {
        foldMarkerPaint.setColor(color);
        if (isCodeFoldingEnabled) editor.invalidate();
    }

    /**
     * Set fold marker text size
     */
    public void setFoldMarkerTextSize(float size) {
        float base = editor.textRender.paint.getTextSize();
        if (base <= 0f) return;
        foldMarkerTextScale = size / base;
        foldMarkerPaint.setTextSize(base * foldMarkerTextScale);
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
