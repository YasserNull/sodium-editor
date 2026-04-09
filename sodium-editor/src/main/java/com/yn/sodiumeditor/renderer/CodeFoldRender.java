package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.CodeFold;
import com.yn.sodiumeditor.core.BracketGuides;
import com.yn.sodiumeditor.core.BracketGuideState;
import com.yn.sodiumeditor.core.BracketGuideToken;
import java.util.HashMap;

/**
 * CodeFoldRender handles all code fold rendering logic for SodiumEditor.
 * This includes drawing fold markers, folded line placeholders, and managing
 * the visual representation of collapsed code regions.
 */
public class CodeFoldRender {

    // Reference to the parent SodiumEditor
    private final SodiumEditor editor;

    // Temporary RectF for fold placeholder drawing
    private final RectF foldPlaceholderRect;

    public CodeFoldRender(SodiumEditor editor) {
        this.editor = editor;
        this.foldPlaceholderRect = new RectF();
    }

    // ============================================================================
    // Fold Marker Drawing
    // ============================================================================

    /**
     * Draw fold markers for visible lines in the gutter.
     */
    public void drawFoldMarkersForVisibleLines(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!editor.codeFold.isCodeFoldingEnabled) return;
        long startMs = android.os.SystemClock.uptimeMillis();

        float markerX = editor.textRender.isRtl
                ? (editor.lineNumber.getGutterStartX() + editor.lineNumber.gutterSeparatorWidth + editor.codeFold.animation.foldMarkerEdgePadding)
                : (editor.lineNumber.getGutterStartX()
                + editor.lineNumber.lineNumbersGutterWidth
                - editor.lineNumber.gutterSeparatorWidth
                - editor.codeFold.animation.foldMarkerEdgePadding);

        for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int line = editor.codeFold.mapVisibleIndexToGlobal(v);
            int baseColor = editor.codeFold.animation.foldMarkerPaint.getColor();
            if (editor.codeFold.pendingFoldComputations.containsKey(line)) {
                editor.codeFold.animation.foldMarkerPaint.setColor(editor.codeFold.animation.foldMarkerPendingColor);
            } else {
                editor.codeFold.animation.foldMarkerPaint.setColor(editor.codeFold.animation.foldMarkerColor);
            }
            String marker = editor.codeFold.getFoldMarkerForLine(line, editor.getLineTextForRender(line));
            if (marker == null) continue;
            float y = Math.round(v * editor.textRender.lineHeight - editor.scroll.scrollY + editor.textRender.lineHeight - editor.textRender.paint.descent());
            if (line == editor.codeFold.animation.foldRippleLine && editor.codeFold.animation.foldRippleAlpha > 0f) {
                int base = editor.codeFold.animation.foldMarkerPaint.getColor();
                int alpha = Math.min(255, Math.max(0, (int) (255f * editor.codeFold.animation.foldRippleAlpha)));
                editor.codeFold.animation.foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
                float centerY = Math.round(v * editor.textRender.lineHeight - editor.scroll.scrollY + editor.textRender.lineHeight * 0.5f);
                canvas.drawCircle(markerX, centerY, editor.codeFold.animation.foldRippleRadius, editor.codeFold.animation.foldRipplePaint);
            }
            canvas.drawText(marker, markerX, y, editor.codeFold.animation.foldMarkerPaint);
            editor.codeFold.animation.foldMarkerPaint.setColor(baseColor);
        }
        long dt = android.os.SystemClock.uptimeMillis() - startMs;
        if (dt > 8 && editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("SodiumRender", "foldMarkers draw dtMs=" + dt
                    + " first=" + firstVisibleIndex + " last=" + lastVisibleIndex);
        }
    }

    // ============================================================================
    // Folded Line Drawing
    // ============================================================================

    /**
     * Draw a folded line with placeholder and suffix.
     */
    public void drawFoldedLine(Canvas canvas, String line, int globalLine, HashMap<Integer, String> directLines) {
        long startMs = android.os.SystemClock.uptimeMillis();
        CodeFold.FoldRange range = editor.codeFold.foldRanges.get(globalLine);
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
        String placeholderText = CodeFold.FOLD_PLACEHOLDER_TEXT;
        editor.textRender.paint.getTextBounds(placeholderText, 0, placeholderText.length(), editor.textRender.textBounds);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(placeholderText));
        float xStart = editor.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float padY = editor.codeFold.animation.foldPlaceholderPadY;
        float placeholderLeft = xStart;
        float placeholderTop = lineTop + padY;
        float placeholderRight = xStart + placeholderWidth;
        float placeholderBottom = lineBottom - padY;

        foldPlaceholderRect.set(placeholderLeft, placeholderTop, placeholderRight, placeholderBottom);
        canvas.drawRoundRect(foldPlaceholderRect, editor.codeFold.animation.foldPlaceholderCorner, editor.codeFold.animation.foldPlaceholderCorner, editor.codeFold.animation.foldPlaceholderPaint);
        if (globalLine == editor.codeFold.animation.foldPlaceholderRippleLine && editor.codeFold.animation.foldPlaceholderRippleAlpha > 0f) {
            int base = editor.codeFold.animation.foldPlaceholderPaint.getColor();
            int alpha = Math.min(255, Math.max(0, (int) (255f * editor.codeFold.animation.foldPlaceholderRippleAlpha)));
            editor.codeFold.animation.foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
            float centerX = (placeholderLeft + placeholderRight) * 0.5f;
            float centerY = (placeholderTop + placeholderBottom) * 0.5f;
            int save = canvas.save();
            canvas.clipRect(foldPlaceholderRect);
            canvas.drawCircle(centerX, centerY, editor.codeFold.animation.foldPlaceholderRippleRadius, editor.codeFold.animation.foldRipplePaint);
            canvas.restoreToCount(save);
        }
        editor.textRender.paint.setUnderlineText(false);
        canvas.drawText(placeholderText, placeholderLeft, y, editor.textRender.paint);

        // Draw suffix (closing bracket or */)
        float xAfter = placeholderLeft + placeholderWidth;
        if (range.isBlockComment) {
            Paint commentPaint = (editor.blockCommentHighlightRule != null) ? editor.blockCommentHighlightRule.paint : editor.textRender.paint;
            commentPaint.setUnderlineText(false);
            String close = "*/";
            canvas.drawText(close, xAfter, y, commentPaint);
            float closeWidth = commentPaint.measureText(close);
            int closeIdx = range.closeCharIndex;
            String endLineText = (range.endLine == globalLine) ? line : getLineTextForRenderWithDirect(range.endLine, directLines);
            if (endLineText != null) {
                if (closeIdx < 0 || closeIdx >= endLineText.length()) {
                    closeIdx = findBlockCommentEnd(endLineText, Math.max(0, range.openCharIndex + 2));
                }
                if (closeIdx >= 0) {
                    int suffixStart = Math.min(endLineText.length(), closeIdx + 2);
                    if (suffixStart < endLineText.length()) {
                        float sx = xAfter + closeWidth;
                        editor.drawHighlightedSegment(canvas, endLineText, range.endLine, suffixStart, endLineText.length(), sx, y);
                    }
                }
            }
        } else if (!range.isIndentFold) {
            String close = String.valueOf(range.closeChar);
            canvas.drawText(close, xAfter, y, editor.textRender.paint);
            float closeWidth = editor.textRender.paint.measureText(close);

            String endLineText;
            if (range.endLine == globalLine) {
                endLineText = line;
            } else {
                endLineText = getLineTextForRenderWithDirect(range.endLine, directLines);
            }

            if (endLineText != null) {
                int closeIdx = range.closeCharIndex;
                if (closeIdx < 0 || closeIdx >= endLineText.length()) {
                    int start = (range.endLine == globalLine) ? Math.max(0, range.openCharIndex + 1) : 0;
                    closeIdx = findClosingBracketInLine(endLineText, start, range.openChar, range.closeChar);
                    if (closeIdx < 0) closeIdx = endLineText.lastIndexOf(range.closeChar);
                    if (closeIdx < 0) closeIdx = endLineText.indexOf(range.closeChar);
                }

                if (editor.DEBUG_RENDER_LOGS && (System.currentTimeMillis() % 2000 < 20)) {
                    android.util.Log.d("codefold", "foldSuffix endLine=" + range.endLine
                            + " closeIdx=" + closeIdx
                            + " textLen=" + endLineText.length()
                            + " text=\"" + endLineText + "\"");
                }

                if (closeIdx >= 0) {
                    int suffixStart = Math.min(endLineText.length(), closeIdx + 1);
                    if (suffixStart < endLineText.length()) {
                        float sx = xAfter + closeWidth;
                        editor.drawHighlightedSegment(canvas, endLineText, range.endLine, suffixStart, endLineText.length(), sx, y);
                    }
                }
            }
        }
        long dt = android.os.SystemClock.uptimeMillis() - startMs;
        if (dt > 4 && editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("SodiumRender", "foldedLine draw dtMs=" + dt + " line=" + globalLine);
        }
    }

    // ============================================================================
    // Folded Content Drawing
    // ============================================================================

    /**
     * Draw folded content for visible lines.
     */
    public void drawFoldedContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                   int firstVisibleLine, int lastVisibleLine,
                                   HashMap<Integer, String> directLines, Paint selPaint,
                                   SodiumEditor.BracketMatch bracketMatchResult, boolean drawDecorations, boolean drawBracketGuides,
                                   BracketGuideState initialBracketState) {
        if (drawDecorations && editor.indentGuides.indentGuideIntervalsDirty) {
            editor.indentGuides.rebuildIndentGuideIntervalsIfNeeded();
        }

        // Track bracket guide state for synchronous rendering
        BracketGuideState bracketState = initialBracketState;
        int windowStart = editor.textRender.windowStartLine;
        int windowEnd = windowStart + editor.textRender.linesWindow.size() - 1;

        for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int globalLine = editor.codeFold.mapVisibleIndexToGlobal(v);
            if (editor.DEBUG_RENDER_LOGS) {
                android.util.Log.d("CodeFold", "draw v=" + v + " global=" + globalLine);
            }
            String line = getLineTextForRenderWithDirect(globalLine, directLines);
            CodeFold.FoldRange foldRange = editor.codeFold.getFoldRangeAtStart(globalLine);
            boolean isFoldStart = (foldRange != null);
            float lineBaseX = editor.textRender.isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
            float lineWidth =
                editor.textRender.isRtl
                    ? measureHighlightedSegmentWidth(
                        line, globalLine, 0, getLogicalLineLength(globalLine, line))
                    : 0f;

            // Draw selection highlight for this line
            if (editor.selection.hasSelection && selPaint != null) {
                // Normalize selection bounds
                int selStartLine = editor.selection.selStartLine;
                int selStartChar = editor.selection.selStartChar;
                int selEndLine = editor.selection.selEndLine;
                int selEndChar = editor.selection.selEndChar;
                if (editor.editOperators.comparePos(selStartLine, selStartChar, selEndLine, selEndChar) > 0) {
                    int tmpL = selStartLine;
                    int tmpC = selStartChar;
                    selStartLine = selEndLine;
                    selStartChar = selEndChar;
                    selEndLine = tmpL;
                    selEndChar = tmpC;
                }
                
                // Check if this line is within selection
                if (globalLine >= selStartLine && globalLine <= selEndLine) {
                    float lineTop = editor.textRender.getDrawLineTop(globalLine);
                    float lineBottom = lineTop + editor.textRender.lineHeight;
                    
                    int startChar = (globalLine == selStartLine) ? selStartChar : 0;
                    int endChar = (globalLine == selEndLine) ? selEndChar : line.length();
                    
                    float startX = editor.measureTextWithVisualSpaces(line, 0, startChar, editor.textRender.paint);
                    float endX = editor.measureTextWithVisualSpaces(line, 0, endChar, editor.textRender.paint);
                    
                    float textStartX = editor.getTextStartX() - editor.lineNumber.lineNumbersGutterWidth;
                    editor.selection.selectionHighlightRect.set(
                        textStartX + startX, lineTop,
                        textStartX + endX, lineBottom);
                    canvas.drawRect(editor.selection.selectionHighlightRect, selPaint);
                }
            }

            float y = Math.round(editor.textRender.getDrawLineTop(globalLine) + editor.textRender.lineHeight - editor.textRender.paint.descent());
            editor.textRender.paint.setUnderlineText(false);

            canvas.save();
            if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

            // Draw color code backgrounds underneath the text
            editor.viewRenderer.drawColorCodeBackgrounds(canvas, line, globalLine);

            if (isFoldStart) {
                CodeFold.FoldRange range = editor.codeFold.foldRanges.get(globalLine);
                boolean isCollapsed = (range != null && range.collapsed);
                // For folded lines, we need to process all hidden lines to maintain correct bracket state
                if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
                    // Calculate bracket state if needed
                    if (bracketState == null) {
                        bracketState = editor.bracketGuides.calculateBracketGuideStateFromWindowStart(
                            globalLine, windowStart, windowEnd, directLines);
                    }
                    // Get tokens from state BEFORE processing this line
                    if (!isCollapsed) {
                        editor.bracketGuides.drawBracketGuidesForLineFromStack(canvas, line, globalLine, bracketState.stack);
                    }
                    // Then: update state for THIS line
                    bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(line, globalLine, bracketState);
                    // If collapsed, advance state through hidden lines so next visible line is correct.
                    if (isCollapsed && range.endLine > globalLine) {
                        for (int ln = globalLine + 1; ln <= range.endLine; ln++) {
                            String lnText = getLineTextForRenderWithDirect(ln, directLines);
                            bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(lnText, ln, bracketState);
                        }
                    }
                }

                if (drawDecorations) {
                    editor.textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
                    editor.indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
                }
                drawFoldedLine(canvas, line, globalLine, directLines);
                canvas.restore();
                continue;
            }

            float lineTop = Math.round(editor.textRender.getDrawLineTop(globalLine));
            float lineBottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
            editor.viewRenderer.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
            editor.textRender.drawHighlightedLine(canvas, line, globalLine, y);
            if (drawDecorations) {
                editor.textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
                editor.indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
            }

            // Draw auto-completion suggestion
            editor.autoCompletion.drawAutoSuggestion(canvas, line, globalLine, y);

            // Draw bracket guides synchronously with line rendering
            if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
                // Calculate bracket state if needed
                if (bracketState == null) {
                    bracketState = editor.bracketGuides.calculateBracketGuideStateFromWindowStart(
                        globalLine, windowStart, windowEnd, directLines);
                }

                // Get tokens from the state BEFORE processing this line
                // This gives us the bracket guides that should be drawn for this line
                editor.bracketGuides.drawBracketGuidesForLineFromStack(canvas, line, globalLine, bracketState.stack);

                // Update state for next line (process this line's brackets)
                bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(line, globalLine, bracketState);
            }

            if (drawDecorations) {
                editor.bracketMatchManager.drawBracketMatchForLine(canvas, line, globalLine, bracketMatchResult);
            }
            canvas.restore();
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Get line text for render with direct lines cache.
     */
    private String getLineTextForRenderWithDirect(int globalLine, HashMap<Integer, String> directLines) {
        // Always honor modified lines (including empty strings) over cached/file data.
        synchronized (editor.textRender.modifiedLines) {
            if (editor.textRender.modifiedLines.containsKey(globalLine)) {
                String mod = editor.textRender.modifiedLines.get(globalLine);
                return mod != null ? mod : "";
            }
        }

        if (directLines != null) {
            String cached = directLines.get(globalLine);
            if (cached != null) return cached;
        }

        // Try standard lookup
        String text = editor.getLineTextForRender(globalLine);
        if (text != null && !text.isEmpty()) {
            return text;
        }

        // If the line is within the window, an empty string is authoritative.
        int winStart = editor.textRender.windowStartLine;
        int winEnd = winStart + editor.textRender.linesWindow.size();
        if (globalLine >= winStart && globalLine < winEnd) {
            return text != null ? text : "";
        }

        // Try direct line cache (only for off-window lines)
        synchronized (editor.fileIO.directLineCache) {
            String cached = editor.fileIO.directLineCache.get(globalLine);
            if (cached != null) return cached;
        }

        // If this is the end line of a collapsed fold, load it from file
        if (editor.codeFold.isCodeFoldingEnabled) {
            for (CodeFold.FoldRange range : editor.codeFold.foldRanges.values()) {
                if (range.collapsed && range.endLine == globalLine) {
                    return editor.codeFold.utils.getEndLineTextForFold(range);
                }
            }
        }

        return text != null ? text : "";
    }

    /**
     * Get logical line length.
     */
    private int getLogicalLineLength(int globalLine, String line) {
        if (line == null) return 0;
        return editor.getLogicalLineLength(globalLine, line);
    }

    /**
     * Measure highlighted segment width.
     */
    private float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
        return editor.measureHighlightedSegmentWidth(line, globalLine, start, end);
    }

    /**
     * Get RTL line base X position.
     */
    private float getRtlLineBaseX(String line, int globalLine) {
        float totalWidth = editor.textRender.globalMaxLineWidth;
        float lineWidth = measureHighlightedSegmentWidth(line, globalLine, 0, getLogicalLineLength(globalLine, line));
        return Math.max(0f, totalWidth - lineWidth);
    }

    // ============================================================================
    // Helper Methods - Delegated to ViewRender
    // ============================================================================

    /**
     * Find block comment end in a line.
     */
    private int findBlockCommentEnd(String line, int startIndex) {
        return editor.codeFold.utils.findBlockCommentEnd(line, startIndex);
    }

    /**
     * Find closing bracket in a line.
     */
    private int findClosingBracketInLine(String line, int startChar, char openBracket, char closeBracket) {
        return editor.codeFold.utils.findClosingBracketInLine(line, startChar, openBracket, closeBracket);
    }

    /**
     * Get end line text for fold range.
     */
    private String getEndLineTextForFold(CodeFold.FoldRange range) {
        return editor.codeFold.utils.getEndLineTextForFold(range);
    }
}
