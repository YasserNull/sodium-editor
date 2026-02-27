package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.HashMap;
import java.util.List;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.BracketGuideManager;
import com.yn.sodiumeditor.BracketMatchManager;
import com.yn.sodiumeditor.FoldManager;

/**
 * Handles all line rendering operations for the text editor.
 */
public final class LineRenderer {
    private final SodiumEditorView view;
    private final TextMeasurement textMeasurement;
    private final LineCacheManager lineCacheManager;

    public LineRenderer(SodiumEditorView view, TextMeasurement textMeasurement, LineCacheManager lineCacheManager) {
        this.view = view;
        this.textMeasurement = textMeasurement;
        this.lineCacheManager = lineCacheManager;
    }

    /**
     * Draws the editor background (color and/or bitmap).
     */
    public void drawEditorBackground(Canvas canvas) {
        if (view.hasEditorBackgroundColor) {
            canvas.drawColor(view.editorBackgroundColor);
        }
        if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
            canvas.drawBitmap(view.editorBackgroundBitmap, null, view.editorBackgroundDst, null);
        }
    }

    /**
     * Draws fold markers for visible lines.
     */
    public void drawFoldMarkersForVisibleLines(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        view.foldManager.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    /**
     * Draws a folded line with ellipsis indicator.
     */
    public void drawFoldedLine(Canvas canvas, String line, int globalLine) {
        FoldManager.FoldRange foldRange = view.foldManager.getFoldRangeAtStart(globalLine);
        if (foldRange == null) return;

        int hiddenLines = foldRange.endLine - foldRange.startLine - 1;
        if (hiddenLines <= 0) return;

        String indicatorText = "▼ ... (" + hiddenLines + " lines)";
        float x = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, 0, line.length(), view.paint);
        float y = (view.lineHeight * 0.75f);
        
        Paint foldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foldPaint.setTextSize(view.paint.getTextSize() * 0.85f);
        foldPaint.setColor(0xFF888888); // Default gray color for fold indicator
        canvas.drawText(indicatorText, x, y, foldPaint);
    }

    /**
     * Main content drawing method for non-wrapped text.
     */
    public void drawContent(Canvas canvas) {
        if (view.wrapWordState.isWordWrapEnabled) {
            drawContentWrapped(canvas);
            return;
        }
        final boolean drawDecorations = view.zoomGestureHandler.shouldDrawDecorations();

        int firstVisibleIndex = (int) (view.scrollManager.scrollY / view.lineHeight);
        if (firstVisibleIndex < 0) firstVisibleIndex = 0;
        int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5;

        int firstVisibleLine = firstVisibleIndex;
        int lastVisibleLine = lastVisibleIndex;
        if (view.foldManager.isCodeFoldingEnabled) {
            int visibleCount = view.getVisibleLineCount();
            if (visibleCount <= 0) visibleCount = 1;
            firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
            lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
            firstVisibleLine = view.mapVisibleIndexToGlobal(firstVisibleIndex);
            lastVisibleLine = view.mapVisibleIndexToGlobal(lastVisibleIndex);
            view.drawBaseLine = firstVisibleIndex;
        } else {
            view.drawBaseLine = firstVisibleIndex;
        }

        float baseY = view.drawBaseLine * view.lineHeight;
        float translateY = -view.scrollManager.scrollY + baseY;
        if (view.isEof) {
            synchronized (view.linesWindow) {
                int lastDocLine = Math.max(0, view.windowStartLine + view.linesWindow.size() - 1);
                lastVisibleLine = Math.min(lastVisibleLine, lastDocLine);
            }
        }
        if (lastVisibleLine < firstVisibleLine) lastVisibleLine = firstVisibleLine;

        view.maybeKickWindowLoad(firstVisibleLine);
        view.maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);

        drawGutter(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine, drawDecorations);

        canvas.save();
        clipContentArea(canvas);
        canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), translateY);
        applyZoomScale(canvas, translateY);

        drawVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine, drawDecorations);

        drawCursorAndHandles(canvas, firstVisibleLine, lastVisibleLine);

        canvas.restore();

        drawOverlays(canvas);
    }

    /**
     * Draws the gutter area (line numbers, fold markers).
     */
    private void drawGutter(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                            int firstVisibleLine, int lastVisibleLine, boolean drawDecorations) {
        if (view.lineNumberManager.isShowLineNumbers()) {
            canvas.drawRect(
                    view.getGutterStartX(),
                    0,
                    view.lineNumberManager.getGutterRight(view.getGutterStartX()),
                    view.getHeight(),
                    view.lineNumberManager.getGutterPaint());

            float separatorLeft;
            if (view.isRtl) {
                separatorLeft = view.getGutterStartX();
            } else {
                separatorLeft = view.lineNumberManager.getSeparatorLeft(view.getGutterStartX());
            }
            canvas.drawRect(
                    separatorLeft,
                    0,
                    separatorLeft + view.lineNumberManager.getGutterSeparatorWidth(),
                    view.getHeight(),
                    view.lineNumberManager.getGutterSeparatorPaint());
        }

        if (view.lineNumberManager.isHighlightCurrentLineInGutter()
                && view.cursorState.getCursorLine() >= firstVisibleLine
                && view.cursorState.getCursorLine() <= lastVisibleLine
                && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.cursorState.getCursorLine()))) {
            int drawIndex = view.foldManager.isCodeFoldingEnabled ? view.getVisibleIndexForGlobalLine(view.cursorState.getCursorLine()) : view.cursorState.getCursorLine();
            float top = Math.round(drawIndex * view.lineHeight - view.scrollManager.scrollY);
            float bottom = top + view.lineHeight;
            view.lineNumberManager.drawCurrentLineHighlightInGutter(canvas, top, bottom, view.highlightManager.currentLinePaint);
        }

        if (view.lineNumberManager.isShowLineNumbers()) {
            view.lineNumberManager.drawLineNumbersCachedUnwrapped(
                    canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
            if (view.foldManager.isCodeFoldingEnabled && drawDecorations) {
                drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
            }
        }
    }

    /**
     * Clips the canvas to the content area.
     */
    private void clipContentArea(Canvas canvas) {
        if (view.isRtl) {
            canvas.clipRect(
                    view.lineNumberManager.getContentClipLeft(view.isRtl),
                    0,
                    view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
                    view.getHeight());
        } else {
            canvas.clipRect(
                    view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
        }
    }

    /**
     * Applies zoom scale transformation if active.
     */
    private void applyZoomScale(Canvas canvas, float translateY) {
        if (view.zoomGestureHandler.isPinchVisualZoomActive()) {
            float pivotX = view.zoomGestureHandler.getPinchFocusX() - (view.getTextStartX() - view.getEffectiveScrollX());
            float pivotY = view.zoomGestureHandler.getPinchFocusY() - translateY;
            canvas.scale(view.zoomGestureHandler.getPinchVisualScale(), view.zoomGestureHandler.getPinchVisualScale(), pivotX, pivotY);
        }
    }

    /**
     * Draws all visible lines.
     */
    private void drawVisibleLines(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                   int firstVisibleLine, int lastVisibleLine, boolean drawDecorations) {
        HashMap<Integer, String> directLines = prepareDirectLines(firstVisibleLine, lastVisibleLine);
        BracketMatchManager.BracketMatch bracketMatch =
                view.bracketMatchManager.getMatch(firstVisibleLine, lastVisibleLine, directLines);

        prepareHighlightCache(firstVisibleLine, lastVisibleLine, directLines);

        if (view.bracketGuideManager.isEnabled() && drawDecorations) {
            view.bracketGuideManager.ensureCacheForWindow(directLines);
        }

        Paint selPaint = view.selectionState.hasSelection() ? view.selectionRenderer.getSelectionPaint() : null;

        if (view.foldManager.isCodeFoldingEnabled) {
            drawFoldedLines(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine,
                    directLines, bracketMatch, selPaint, drawDecorations);
        } else {
            drawUnfoldedLines(canvas, firstVisibleLine, lastVisibleLine, directLines, bracketMatch, selPaint, drawDecorations);
        }
    }

    /**
     * Prepares direct lines cache for file-based content.
     */
    private HashMap<Integer, String> prepareDirectLines(int firstVisibleLine, int lastVisibleLine) {
        HashMap<Integer, String> directLines = null;
        if (view.isIndexReady && view.sourceFile != null && view.sourceFile.exists()) {
            boolean needDirect =
                    (firstVisibleLine < view.windowStartLine)
                            || (firstVisibleLine >= view.windowStartLine + view.linesWindow.size())
                            || (lastVisibleLine >= view.windowStartLine + view.linesWindow.size());

            if (needDirect) {
                view.directLinesTmp.clear();
                directLines = view.directLinesTmp;
                if (firstVisibleLine < view.windowStartLine) {
                    lineCacheManager.populateDirectLinesForRange(
                            firstVisibleLine, Math.min(lastVisibleLine, view.windowStartLine - 1), directLines);
                }
                int winEnd = view.windowStartLine + view.linesWindow.size() - 1;
                if (lastVisibleLine > winEnd) {
                    lineCacheManager.populateDirectLinesForRange(
                            Math.max(firstVisibleLine, winEnd + 1), lastVisibleLine, directLines);
                }
                if (directLines.isEmpty()
                        && (firstVisibleLine < view.windowStartLine
                        || firstVisibleLine >= view.windowStartLine + view.linesWindow.size())) {
                    lineCacheManager.populateDirectLinesForRange(firstVisibleLine, lastVisibleLine, directLines);
                }
            }
        }
        return directLines;
    }

    /**
     * Prepares highlight cache for visible range.
     */
    private void prepareHighlightCache(int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
        int winEnd;
        synchronized (view.linesWindow) {
            winEnd = view.windowStartLine + view.linesWindow.size() - 1;
        }
        int prefetchForDraw = view.zoomGestureHandler.isZoomGestureActive() ? 0 : view.prefetchLines;
        int hlStart = Math.max(view.windowStartLine, Math.max(0, firstVisibleLine - prefetchForDraw));
        int hlEnd = Math.min(winEnd, lastVisibleLine + prefetchForDraw);
        view.maybeEnsureHighlightCacheForRange(hlStart, hlEnd, directLines);
    }

    /**
     * Draws lines when code folding is enabled.
     */
    private void drawFoldedLines(Canvas canvas,
                                  int firstVisibleIndex, int lastVisibleIndex,
                                  int firstVisibleLine, int lastVisibleLine,
                                  HashMap<Integer, String> directLines,
                                  BracketMatchManager.BracketMatch bracketMatch,
                                  Paint selPaint, boolean drawDecorations) {
        view.indentGuideManager.rebuildIntervalsIfNeeded();

        for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int globalLine = view.mapVisibleIndexToGlobal(v);
            String line = lineCacheManager.getLineTextForRenderWithDirect(globalLine, directLines);
            FoldManager.FoldRange foldRange = view.foldManager.getFoldRangeAtStart(globalLine);
            boolean isFoldStart = (foldRange != null);
            float lineBaseX = view.isRtl ? view.getRtlLineBaseX(line, globalLine) : 0f;
            float lineWidth =
                    view.isRtl
                            ? view.highlightManager.measureHighlightedSegmentWidth(
                            line, globalLine, 0, view.getLogicalLineLength(globalLine, line))
                            : 0f;

            drawCurrentLineHighlight(canvas, globalLine, selPaint);
            drawSelection(canvas, globalLine, line, lineBaseX, lineWidth, selPaint);

            float y = Math.round(view.scrollManager.getDrawLineTop(globalLine) + view.lineHeight - view.paint.descent());
            view.paint.setUnderlineText(false);

            canvas.save();
            if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

            float lineTop = Math.round(view.scrollManager.getDrawLineTop(globalLine));
            float lineBottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));

            view.highlightManager.drawColorCodeBackgrounds(canvas, line, globalLine, lineTop, lineBottom);

            if (isFoldStart) {
                if (view.bracketGuideManager.isEnabled() && drawDecorations) {
                    List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
                    view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
                }
                if (drawDecorations) {
                    view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
                    view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
                }
                drawFoldedLine(canvas, line, globalLine);
                canvas.restore();
                continue;
            }

            view.searchRenderer.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
            view.highlightManager.drawHighlightedLine(canvas, line, globalLine, y);
            if (drawDecorations) {
                view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
                view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
            }

            view.autoSuggestionManager.drawAutoSuggestion(canvas, line, globalLine, y);

            if (view.bracketGuideManager.isEnabled() && drawDecorations) {
                List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
                view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
            }

            if (drawDecorations) {
                view.bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
            }
            canvas.restore();
        }
    }

    /**
     * Draws lines when code folding is disabled.
     */
    private void drawUnfoldedLines(Canvas canvas,
                                    int firstVisibleLine, int lastVisibleLine,
                                    HashMap<Integer, String> directLines,
                                    BracketMatchManager.BracketMatch bracketMatch,
                                    Paint selPaint, boolean drawDecorations) {
        view.indentGuideManager.rebuildIntervalsIfNeeded();

        for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
            String line = lineCacheManager.getLineTextForRenderWithDirect(globalLine, directLines);
            if (line == null) line = "";
            float lineBaseX = view.isRtl ? view.getRtlLineBaseX(line, globalLine) : 0f;
            float lineWidth =
                    view.isRtl
                            ? view.highlightManager.measureHighlightedSegmentWidth(
                            line, globalLine, 0, view.getLogicalLineLength(globalLine, line))
                            : 0f;

            drawCurrentLineHighlight(canvas, globalLine, selPaint);
            drawSelection(canvas, globalLine, line, lineBaseX, lineWidth, selPaint);

            float y = Math.round(view.scrollManager.getDrawLineTop(globalLine) + view.lineHeight - view.paint.descent());
            view.paint.setUnderlineText(false);

            canvas.save();
            if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

            float lineTop = Math.round(view.scrollManager.getDrawLineTop(globalLine));
            float lineBottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));

            view.highlightManager.drawColorCodeBackgrounds(canvas, line, globalLine, lineTop, lineBottom);
            view.searchRenderer.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
            view.highlightManager.drawHighlightedLine(canvas, line, globalLine, y);
            if (drawDecorations) {
                view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
                view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
            }

            view.autoSuggestionManager.drawAutoSuggestion(canvas, line, globalLine, y);

            if (view.bracketGuideManager.isEnabled() && drawDecorations) {
                List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
                view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
            }

            if (drawDecorations) {
                view.bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
            }
            canvas.restore();
        }
    }

    /**
     * Draws current line highlight.
     */
    private void drawCurrentLineHighlight(Canvas canvas, int globalLine, Paint selPaint) {
        if (view.highlightManager.highlightCurrentLine && globalLine == view.cursorState.getCursorLine() && !view.selectionState.hasSelection()) {
            float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
            float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
            float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
            float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
            float left = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
            float right = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
            canvas.drawRect(left, top, right, bottom, view.highlightManager.currentLinePaint);
        }
    }

    /**
     * Draws selection highlights for a line.
     */
    private void drawSelection(Canvas canvas, int globalLine, String line,
                               float lineBaseX, float lineWidth, Paint selPaint) {
        if (!view.selectionState.hasSelection() || selPaint == null) return;

        float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
        float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
        float fullRight =
                Math.max(view.currentMaxWindowLineWidth, view.scrollManager.scrollX + (view.getWidth() - view.getTextStartX()));
        if (view.isRtl) {
            fullRight = lineBaseX + lineWidth;
        }

        if (view.selectionState.isSelectAllActive()) {
            boolean lineExists =
                    (view.isEof) ? (globalLine <= view.windowStartLine + view.linesWindow.size() - 1) : true;
            if (lineExists) {
                boolean roundTop = globalLine == view.selectionState.selStartLine;
                boolean roundBottom = globalLine == view.selectionState.selEndLine;
                float leftSel = view.isRtl ? lineBaseX : 0f;
                float rightSel = view.isRtl ? (lineBaseX + lineWidth) : fullRight;
                view.selectionRenderer.drawSelectionSegment(
                        canvas,
                        leftSel,
                        top,
                        rightSel,
                        bottom,
                        roundTop,
                        roundTop,
                        roundBottom,
                        roundBottom,
                        view.lineHeight, selPaint);
            }
        } else {
            int startLine, endLine, startChar, endChar;
            if (view.comparePos(view.selectionState.selStartLine, view.selectionState.selStartChar,
                    view.selectionState.selEndLine, view.selectionState.selEndChar) <= 0) {
                startLine = view.selectionState.selStartLine;
                startChar = view.selectionState.selStartChar;
                endLine = view.selectionState.selEndLine;
                endChar = view.selectionState.selEndChar;
            } else {
                startLine = view.selectionState.selEndLine;
                startChar = view.selectionState.selEndChar;
                endLine = view.selectionState.selStartLine;
                endChar = view.selectionState.selStartChar;
            }

            if (globalLine >= startLine && globalLine <= endLine) {
                float left, right;
                if (view.isRtl) {
                    float lineLeft = lineBaseX;
                    float lineRight = lineBaseX + lineWidth;
                    if (startLine == endLine) {
                        float x1 = textMeasurement.getCaretXForLine(line, globalLine, Math.min(startChar, line.length()));
                        float x2 = textMeasurement.getCaretXForLine(line, globalLine, Math.min(endChar, line.length()));
                        left = Math.min(x1, x2);
                        right = Math.max(x1, x2);
                    } else if (globalLine == startLine) {
                        float x = textMeasurement.getCaretXForLine(line, globalLine, Math.min(startChar, line.length()));
                        left = lineLeft;
                        right = x;
                    } else if (globalLine == endLine) {
                        float x = textMeasurement.getCaretXForLine(line, globalLine, Math.min(endChar, line.length()));
                        left = x;
                        right = lineRight;
                    } else {
                        left = lineLeft;
                        right = lineRight;
                    }
                } else {
                    if (startLine == endLine) {
                        left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                        right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                    } else {
                        if (globalLine == startLine) {
                            left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                            right = fullRight;
                        } else if (globalLine == endLine) {
                            left = 0;
                            right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                            if (line.length() == 0) right = fullRight;
                        } else {
                            left = 0;
                            right = fullRight;
                        }
                    }
                }
                if (right > left) {
                    boolean isStart = globalLine == startLine;
                    boolean isEnd = globalLine == endLine;
                    boolean roundTop = isStart;
                    boolean roundBottom = isEnd;
                    if (!isStart && !isEnd) {
                        roundTop = false;
                        roundBottom = false;
                    } else if (isStart && !isEnd) {
                        roundBottom = false;
                    } else if (!isStart && isEnd) {
                        roundTop = false;
                    }
                    view.selectionRenderer.drawSelectionSegment(
                            canvas,
                            left,
                            top,
                            right,
                            bottom,
                            roundTop,
                            roundTop,
                            roundBottom,
                            roundBottom,
                            view.lineHeight, selPaint);
                }
            }
        }
    }

    /**
     * Draws cursor and handles.
     */
    private void drawCursorAndHandles(Canvas canvas, int firstVisibleLine, int lastVisibleLine) {
        if (view.isFocused()
                && !view.isReadOnly
                && !view.selectionState.hasSelection()
                && view.cursorState.getCursorLine() >= firstVisibleLine
                && view.cursorState.getCursorLine() <= lastVisibleLine
                && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.cursorState.getCursorLine()))) {
            String cursorLineText = lineCacheManager.getLineTextForRender(view.cursorState.getCursorLine());
            int safeChar = Math.min(view.cursorState.getCursorChar(), view.getLogicalLineLength(view.cursorState.getCursorLine(), cursorLineText));
            float cursorX = textMeasurement.getCaretXForLine(cursorLineText, view.cursorState.getCursorLine(), safeChar);
            float cursorY = view.scrollManager.getDrawLineTop(view.cursorState.getCursorLine());
            view.cursorRenderer.drawCaret(canvas, cursorX, cursorY);
            float drawX = view.cursorAnimator.getCursorDrawX();
            float drawY = view.cursorAnimator.getCursorDrawY();
            view.handlesManager.drawCursorHandle(canvas, drawX, drawY, view.lineHeight);
        }

        if (view.selectionState.hasSelection() && !view.isReadOnly) {
            drawSelectionHandles(canvas, firstVisibleLine, lastVisibleLine);
        }
    }

    /**
     * Draws selection start and end handles.
     */
    private void drawSelectionHandles(Canvas canvas, int firstVisibleLine, int lastVisibleLine) {
        if (view.selectionState.selStartLine >= firstVisibleLine
                && view.selectionState.selStartLine <= lastVisibleLine
                && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.selectionState.selStartLine))) {
            String startLineText = lineCacheManager.getLineTextForRender(view.selectionState.selStartLine);
            float startX =
                    textMeasurement.getCaretXForLine(
                            startLineText,
                            view.selectionState.selStartLine,
                            Math.min(view.selectionState.selStartChar, view.getLogicalLineLength(view.selectionState.selStartLine, startLineText)));
            float startY = view.scrollManager.getDrawLineTop(view.selectionState.selStartLine) + view.lineHeight;
            view.handlesManager.drawSelectionStartHandle(canvas, startX, startY, view.isRtl);
        } else {
            if (view.isRtl) view.handlesManager.clearRightHandleRect();
            else view.handlesManager.clearLeftHandleRect();
        }
        if (view.selectionState.selEndLine >= firstVisibleLine
                && view.selectionState.selEndLine <= lastVisibleLine
                && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.selectionState.selEndLine))) {
            String endLineText = lineCacheManager.getLineTextForRender(view.selectionState.selEndLine);
            float endX =
                    textMeasurement.getCaretXForLine(
                            endLineText,
                            view.selectionState.selEndLine,
                            Math.min(view.selectionState.selEndChar, view.getLogicalLineLength(view.selectionState.selEndLine, endLineText)));
            float endY = view.scrollManager.getDrawLineTop(view.selectionState.selEndLine) + view.lineHeight;
            view.handlesManager.drawSelectionEndHandle(canvas, endX, endY, view.isRtl);
        } else {
            if (view.isRtl) view.handlesManager.clearLeftHandleRect();
            else view.handlesManager.clearRightHandleRect();
        }
    }

    /**
     * Draws overlays (popup menu, loading circle).
     */
    private void drawOverlays(Canvas canvas) {
        if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.drawPopup(canvas);
        view.loadingCircleManager.draw(canvas);
    }

    /**
     * Main content drawing method for wrapped text.
     */
    public void drawContentWrapped(Canvas canvas) {
        int wrapWidthPx = Math.max(1, Math.round(view.getWidth() - view.getTextStartX()));
        final boolean drawDecorations = view.zoomGestureHandler.shouldDrawDecorations();
        if (!view.zoomGestureHandler.isZoomGestureActive()) {
            view.wrapWordBuilder.applyPendingPrefixUpdate(view);
        }
        if (view.wrapWordBuilder.shouldSuppressForSelectAll(view)) {
            drawContentWrappedFallback(canvas, wrapWidthPx);
            return;
        }
        if (!view.wrapWordBuilder.isMetricsUsableForWindow(view, wrapWidthPx)) {
            drawContentWrappedFallback(canvas, wrapWidthPx);
            return;
        }
        // Simplified wrapped content drawing - full implementation would mirror original
        drawContentWrappedFallback(canvas, wrapWidthPx);
    }

    /**
     * Fallback drawing method for wrapped text when metrics are not ready.
     */
    public void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
        final boolean drawDecorations = view.zoomGestureHandler.shouldDrawDecorations();

        int firstLine;
        int lastLine;
        if (view.foldManager.isCodeFoldingEnabled) {
            int visibleCount = view.getVisibleLineCount();
            if (visibleCount <= 0) visibleCount = 1;
            int firstVisibleIndex = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
            int lastVisibleIndex = Math.min(visibleCount - 1, firstVisibleIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5);
            firstLine = view.mapVisibleIndexToGlobal(firstVisibleIndex);
            lastLine = view.mapVisibleIndexToGlobal(lastVisibleIndex);
        } else {
            firstLine = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
            lastLine = firstLine + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5;
        }

        int totalLines = view.getLinesCount();
        if (totalLines <= 0) totalLines = view.windowStartLine + view.linesWindow.size();
        if (totalLines <= 0) totalLines = 1;
        lastLine = Math.min(Math.max(0, totalLines - 1), lastLine);
        if (lastLine < firstLine) lastLine = firstLine;

        HashMap<Integer, String> directLines = null;
        if (view.isIndexReady && view.sourceFile != null && view.sourceFile.exists()) {
            view.directLinesTmp.clear();
            directLines = view.directLinesTmp;
            int rangeStart = Math.max(0, firstLine - 1);
            int rangeEnd = Math.min(totalLines - 1, lastLine + 1);
            lineCacheManager.populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
        }

        float baseY = firstLine * view.lineHeight;
        float translateY = -view.scrollManager.scrollY + baseY;

        canvas.save();
        canvas.translate(0, translateY);

        int saveCount = canvas.save();
        if (view.isRtl) {
            canvas.clipRect(
                    view.lineNumberManager.getContentClipLeft(view.isRtl),
                    0,
                    view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
                    view.getHeight());
        } else {
            canvas.clipRect(
                    view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
        }
        canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), 0);

        Paint selPaint = view.selectionState.hasSelection() ? view.selectionRenderer.getSelectionPaint() : null;

        int startLine = view.selectionState.selStartLine;
        int startChar = view.selectionState.selStartChar;
        int endLine = view.selectionState.selEndLine;
        int endChar = view.selectionState.selEndChar;
        if (view.selectionState.hasSelection() && view.comparePos(view.selectionState.selStartLine, view.selectionState.selStartChar, view.selectionState.selEndLine, view.selectionState.selEndChar) > 0) {
            startLine = view.selectionState.selEndLine;
            startChar = view.selectionState.selEndChar;
            endLine = view.selectionState.selStartLine;
            endChar = view.selectionState.selStartChar;
        }

        float yOffset = 0f;
        for (int line = firstLine; line <= lastLine; line++) {
            if (yOffset > view.getHeight() + view.lineHeight) break;
            String text = lineCacheManager.getLineTextForRenderWithDirect(line, directLines);
            int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, line, text, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())), view.paint);

            for (int seg = 0; seg < starts.length; seg++) {
                int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
                int segEnd = view.wrapWordEngine.getWrapSegmentEnd(starts, seg, text.length());
                float segBaseX = view.isRtl ? textMeasurement.getRtlSegmentBaseX(text, line, segStart, segEnd) : 0f;

                float top = Math.round(yOffset);
                float bottom = top + view.lineHeight;
                float y = Math.round(top + view.lineHeight - view.paint.descent());

                if (view.highlightManager.highlightCurrentLine && line == view.cursorState.getCursorLine() && !view.selectionState.hasSelection()) {
                    canvas.drawRect(-view.paddingLeft, top, Math.max(view.getWidth() - view.getTextStartX(), view.getWidth()), bottom, view.highlightManager.currentLinePaint);
                }

                int segDrawEnd = segEnd;
                if (view.wrapWordIndicatorRender.isIndicatorEnabled && segEnd < text.length()) {
                    segDrawEnd = view.wrapWordIndicatorRender.clampSegmentEndForIndicator(view, text, segStart, segEnd, wrapWidthPx);
                }
                canvas.save();
                if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
                view.searchRenderer.drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
                view.highlightManager.drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
                view.highlightManager.drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
                lineCacheManager.drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
                if (drawDecorations) {
                    view.whitespaceGuideManager.drawWhitespaceGuidesForSegment(view, canvas, text, line, segStart, segDrawEnd, y);
                }
                view.autoSuggestionManager.drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, line, y);
                canvas.restore();

                yOffset += view.lineHeight;
                if (yOffset > view.getHeight() + view.lineHeight) break;
            }
        }

        canvas.restore();
        canvas.restore();

        drawOverlays(canvas);
    }
}
