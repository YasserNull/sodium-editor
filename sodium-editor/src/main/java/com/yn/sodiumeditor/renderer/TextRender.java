package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Main coordinator for text rendering operations.
 * Delegates specific tasks to specialized renderer classes.
 */
public final class TextRender {
    private final SodiumEditor view;
    private TextMeasurement textMeasurement;
    private LineCacheManager lineCacheManager;
    private TextNavigation textNavigation;
    private LineRenderer lineRenderer;
    private CursorTargeting cursorTargeting;

    public TextRender(SodiumEditor view) {
        this.view = view;
        initializeDelegates();
    }

    private void initializeDelegates() {
        textMeasurement = new TextMeasurement(view);
        lineCacheManager = new LineCacheManager(view);
        textNavigation = new TextNavigation(view, lineCacheManager);
        lineRenderer = new LineRenderer(view, textMeasurement, lineCacheManager);
        cursorTargeting = new CursorTargeting(view, textMeasurement, lineCacheManager);
    }

    // Delegate getters for use by other managers
    public TextMeasurement getTextMeasurement() {
        return textMeasurement;
    }

    public LineCacheManager getLineCacheManager() {
        return lineCacheManager;
    }

    public TextNavigation getTextNavigation() {
        return textNavigation;
    }

    public LineRenderer getLineRenderer() {
        return lineRenderer;
    }

    public CursorTargeting getCursorTargeting() {
        return cursorTargeting;
    }

    /**
     * Main draw method - coordinates all rendering operations.
     */
    public void onDraw(Canvas canvas) {
        view.superOnDraw(canvas);
        lineRenderer.drawEditorBackground(canvas);
        
        if (view.scrollManager.stretchOverscrollEnabled
                && (view.scrollManager.stretchX != 0f || view.scrollManager.stretchY != 0f)) {
            drawWithStretch(canvas);
        } else {
            lineRenderer.drawContent(canvas);
        }
        
        view.scrollManager.drawScrollBar(canvas);
    }

    /**
     * Draws content with stretch overscroll effect.
     */
    private void drawWithStretch(Canvas canvas) {
        float sx = 1f + (view.scrollManager.stretchX * 0.12f * view.scrollManager.stretchOverscrollStrength);
        float sy = 1f + (view.scrollManager.stretchY * 0.12f * view.scrollManager.stretchOverscrollStrength);
        float pivotX =
                (view.scrollManager.stretchDirX < 0)
                        ? 0f
                        : (view.scrollManager.stretchDirX > 0 ? view.getWidth() : view.getWidth() * 0.5f);
        float pivotY =
                (view.scrollManager.stretchDirY < 0)
                        ? 0f
                        : (view.scrollManager.stretchDirY > 0 ? view.getHeight() : view.getHeight() * 0.5f);
        canvas.save();
        canvas.scale(sx, sy, pivotX, pivotY);
        lineRenderer.drawContent(canvas);
        canvas.restore();
    }

    /**
     * Calls the view's super onDraw method.
     */
    void superOnDraw(Canvas canvas) {
        view.superOnDraw(canvas);
    }

    /**
     * Invalidates a specific global line for redraw.
     */
    public void invalidateLineGlobal(int globalLine) {
        lineCacheManager.invalidateLineGlobal(globalLine);
    }

    /**
     * Checks if heavy drawing is suppressed (always returns false).
     */
    public boolean isHeavyDrawSuppressed() {
        return false;
    }

    /**
     * Checks if the character at a given X position is whitespace.
     */
    public boolean isWhitespaceAtX(String line, int globalLine, float x) {
        return textMeasurement.isWhitespaceAtX(line, globalLine, x);
    }

    /**
     * Draws fold markers for visible lines.
     */
    public void drawFoldMarkersForVisibleLines(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        lineRenderer.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    /**
     * Draws delete animation for a segment of text.
     */
    public void drawDeleteAnimationForSegment(
            Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        lineCacheManager.drawDeleteAnimationForSegment(canvas, line, globalLine, segStart, segEnd, y);
    }

    /**
     * Gets the visible character range for a line.
     */
    public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
        textMeasurement.getVisibleCharRangeForLine(line, globalLine, out);
    }

    /**
     * Gets the visible character range for a line (fast version).
     */
    public void getVisibleCharRangeForLineFast(
            String line, int globalLine, int lineLength, int[] out) {
        textMeasurement.getVisibleCharRangeForLineFast(line, globalLine, lineLength, out);
    }

    /**
     * Gets the character index at a given X position.
     */
    public int getCharIndexForX(String text, float x, int globalLine) {
        return textMeasurement.getCharIndexForX(text, x, globalLine);
    }

    /**
     * Computes and caches the width for a line.
     */
    public void computeWidthForLine(int globalIndex, String line) {
        textMeasurement.computeWidthForLine(globalIndex, line);
    }

    /**
     * Gets the cached width for a line.
     */
    public float getWidthForLine(int globalIndex, String line) {
        return textMeasurement.getWidthForLine(globalIndex, line);
    }

    /**
     * Gets line text for rendering.
     */
    public String getLineTextForRender(int line) {
        return lineCacheManager.getLineTextForRender(line);
    }

    /**
     * Gets line text for rendering with direct file access.
     */
    public String getLineTextForRenderWithDirect(int line, java.util.Map<Integer, String> direct) {
        return lineCacheManager.getLineTextForRenderWithDirect(line, direct);
    }

    /**
     * Gets the length of a line.
     * @param line the line index
     * @return the line length, or 0 if the line is null
     */
    public int getLineLength(int line) {
        String ln = getLineTextForRender(line);
        return ln != null ? ln.length() : 0;
    }

    /**
     * Populates direct lines cache for a range.
     */
    public void populateDirectLinesForRange(int startLine, int endLine, java.util.Map<Integer, String> direct) {
        lineCacheManager.populateDirectLinesForRange(startLine, endLine, direct);
    }

    /**
     * Gets the global line number for a Y position.
     */
    public int getGlobalLineForY(float y) {
        return textNavigation.getGlobalLineForY(y);
    }

    /**
     * Gets the visual index for a line and character position.
     */
    public int getVisualIndexForLineAndChar(int line, int ch) {
        return textNavigation.getVisualIndexForLineAndChar(line, ch);
    }

    /**
     * Gets the total number of visible lines.
     */
    public int getVisibleLineCount() {
        return textNavigation.getVisibleLineCount();
    }

    /**
     * Maps a visible index to a global line number.
     */
    public int mapVisibleIndexToGlobal(int visibleIndex) {
        return textNavigation.mapVisibleIndexToGlobal(visibleIndex);
    }

    /**
     * Gets the visible index for a global line number.
     */
    public int getVisibleIndexForGlobalLine(int globalLine) {
        return textNavigation.getVisibleIndexForGlobalLine(globalLine);
    }

    /**
     * Gets the total number of lines in the document.
     */
    public int getLinesCount() {
        return textNavigation.getLinesCount();
    }

    /**
     * Updates a line in the window cache.
     */
    public void updateLocalLine(int localIdx, String text) {
        lineCacheManager.updateLocalLine(localIdx, text);
    }

    /**
     * Gets a line from the window cache.
     */
    public String getLineFromWindowLocal(int localIdx) {
        return lineCacheManager.getLineFromWindowLocal(localIdx);
    }

    /**
     * Checks if a character is a word character.
     */
    public boolean isWordChar(char c) {
        return textNavigation.isWordChar(c);
    }

    /**
     * Computes word bounds for a position.
     */
    public int[] computeWordBounds(String line, int pos) {
        return textNavigation.computeWordBounds(line, pos);
    }

    /**
     * Computes word bounds using smart detection.
     */
    public int[] computeWordBoundsSmart(String line, int pos) {
        return textNavigation.computeWordBoundsSmart(line, pos);
    }

    /**
     * Applies smart double-tap selection.
     */
    public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
        return textNavigation.applySmartDoubleTapSelection(line, charIndex, lineText);
    }

    /**
     * Builds candidate selection ranges for double-tap.
     */
    public java.util.ArrayList<com.yn.sodiumeditor.utils.TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
        return textNavigation.buildDoubleTapCandidates(line, charIndex, wStart, wEnd);
    }

    /**
     * Adds a selection candidate range.
     */
    public void addSelectionCandidate(java.util.List<com.yn.sodiumeditor.utils.TextRange> out, int start, int end, int lineLen) {
        textNavigation.addSelectionCandidate(out, start, end, lineLen);
    }

    /**
     * Finds the current selection index in candidates.
     */
    public int findSelectionCandidateIndex(int line, java.util.List<com.yn.sodiumeditor.utils.TextRange> candidates) {
        return textNavigation.findSelectionCandidateIndex(line, candidates);
    }

    /**
     * Checks if a character is a quote character.
     */
    public boolean isQuoteChar(char c) {
        return textNavigation.isQuoteChar(c);
    }

    /**
     * Finds the enclosing quoted range for an index.
     */
    public com.yn.sodiumeditor.utils.TextRange findEnclosingQuoteRange(String line, int index) {
        return textNavigation.findEnclosingQuoteRange(line, index);
    }

    /**
     * Finds the enclosing bracket range for an index.
     */
    public com.yn.sodiumeditor.utils.TextRange findEnclosingBracketRange(String line, int index) {
        return textNavigation.findEnclosingBracketRange(line, index);
    }

    /**
     * Gets the RTL line base X position.
     */
    public float getRtlLineBaseX(String line, int globalLine) {
        return textMeasurement.getRtlLineBaseX(line, globalLine);
    }

    /**
     * Gets the RTL segment base X position.
     */
    public float getRtlSegmentBaseX(String line, int globalLine, int segStart, int segEnd) {
        return textMeasurement.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    }

    /**
     * Gets the caret X position for a line and character.
     */
    public float getCaretXForLine(String line, int globalLine, int charIndex) {
        return textMeasurement.getCaretXForLine(line, globalLine, charIndex);
    }

    /**
     * Gets the caret X position for a segment.
     */
    public float getCaretXForSegment(String line, int globalLine, int segStart, int segEnd, int charIndex) {
        return textMeasurement.getCaretXForSegment(line, globalLine, segStart, segEnd, charIndex);
    }

    /**
     * Gets the character index at X position within a range.
     */
    public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
        return textMeasurement.getCharIndexForXInRange(text, globalLine, start, end, x);
    }

    /**
     * Gets the cursor target for a view position.
     */
    public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget getCursorTargetForPosition(
            float viewX, float viewY, java.util.Map<Integer, String> directLines) {
        return cursorTargeting.getCursorTargetForPosition(viewX, viewY, directLines);
    }

    /**
     * Recalculates the maximum line width.
     */
    public void recalculateMaxLineWidth() {
        lineCacheManager.recalculateMaxLineWidth();
    }

    /**
     * Recalculates the maximum line width asynchronously.
     */
    public void recalculateMaxLineWidthAsync() {
        lineCacheManager.recalculateMaxLineWidthAsync();
    }

    /**
     * Draws content (delegated to LineRenderer).
     */
    public void drawContent(Canvas canvas) {
        lineRenderer.drawContent(canvas);
    }

    /**
     * Draws wrapped content (delegated to LineRenderer).
     */
    public void drawContentWrapped(Canvas canvas) {
        lineRenderer.drawContentWrapped(canvas);
    }

    /**
     * Draws wrapped content with fallback (delegated to LineRenderer).
     */
    public void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
        lineRenderer.drawContentWrappedFallback(canvas, wrapWidthPx);
    }
}
