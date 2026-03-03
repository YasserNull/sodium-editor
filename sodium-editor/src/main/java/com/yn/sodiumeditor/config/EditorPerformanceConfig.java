package com.yn.sodiumeditor.config;

/**
 * Configuration class for editor performance and caching.
 */
public class EditorPerformanceConfig {

    // Cache sizes
    public int lineWidthCacheSize = 200;
    public int colsWidthCacheSize = 256;
    public int directLineCacheSize = 600;

    // Prefetch settings
    public int prefetchLines = 10;
    public int prefetchCols = 512;

    // Window settings
    public int windowSize = 30;

    // Index limits
    public long maxIndexBytesHard = 64L * 1024 * 1024;

    // Large edit thresholds
    public int largeEditLines = 8000;
    public int hideCopyCutLines = 20000;

    // Animation durations
    public long flingStopAnimDurationMs = 90;

    // Scroll settings
    public float bottomScrollOffset = 100f;
    public float minBottomVisibleSpace = 50f;

    // Indent fold settings
    public int indentFoldScanLimit = 2000;

    // Performance mode settings
    public boolean isPerformanceModeEnabled = false;
    public boolean isStableGlyphPositionsEnabled = false;
    public boolean isClickAfterEndToAddLineEnabled = false;

    public EditorPerformanceConfig() {
    }

    public int getLineWidthCacheSize() {
        return lineWidthCacheSize;
    }

    public void setLineWidthCacheSize(int size) {
        this.lineWidthCacheSize = size;
    }

    public int getColsWidthCacheSize() {
        return colsWidthCacheSize;
    }

    public void setColsWidthCacheSize(int size) {
        this.colsWidthCacheSize = size;
    }

    public int getDirectLineCacheSize() {
        return directLineCacheSize;
    }

    public void setDirectLineCacheSize(int size) {
        this.directLineCacheSize = size;
    }

    public int getPrefetchLines() {
        return prefetchLines;
    }

    public void setPrefetchLines(int lines) {
        this.prefetchLines = lines;
    }

    public int getPrefetchCols() {
        return prefetchCols;
    }

    public void setPrefetchCols(int cols) {
        this.prefetchCols = cols;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int size) {
        this.windowSize = size;
    }

    public long getMaxIndexBytesHard() {
        return maxIndexBytesHard;
    }

    public int getLargeEditLines() {
        return largeEditLines;
    }

    public int getHideCopyCutLines() {
        return hideCopyCutLines;
    }

    public long getFlingStopAnimDurationMs() {
        return flingStopAnimDurationMs;
    }

    public float getBottomScrollOffset() {
        return bottomScrollOffset;
    }

    public float getMinBottomVisibleSpace() {
        return minBottomVisibleSpace;
    }

    public int getIndentFoldScanLimit() {
        return indentFoldScanLimit;
    }

    public boolean isPerformanceModeEnabled() {
        return isPerformanceModeEnabled;
    }

    public void setPerformanceModeEnabled(boolean enabled) {
        isPerformanceModeEnabled = enabled;
    }

    public boolean isStableGlyphPositionsEnabled() {
        return isStableGlyphPositionsEnabled;
    }

    public void setStableGlyphPositionsEnabled(boolean enabled) {
        isStableGlyphPositionsEnabled = enabled;
    }

    public boolean isClickAfterEndToAddLineEnabled() {
        return isClickAfterEndToAddLineEnabled;
    }

    public void setClickAfterEndToAddLineEnabled(boolean enabled) {
        isClickAfterEndToAddLineEnabled = enabled;
    }

    /**
     * Sets stable glyph positions enabled and triggers invalidation.
     * @param enabled whether stable glyph positions are enabled
     * @param editor the SodiumEditor instance to invalidate
     */
    public void setStableGlyphPositionsEnabled(boolean enabled, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.isStableGlyphPositionsEnabled == enabled) return;
        this.isStableGlyphPositionsEnabled = enabled;
        editor.invalidate();
    }

    /**
     * Sets performance mode enabled and disables visual features for better performance.
     * @param enabled whether performance mode is enabled
     * @param editor the SodiumEditor instance to update
     */
    public void setPerformanceModeEnabled(boolean enabled, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.isPerformanceModeEnabled == enabled) return;
        this.isPerformanceModeEnabled = enabled;
        
        if (enabled) {
            editor.urlUnderlineRenderer.setUrlUnderliningEnabled(false);
            editor.pathUnderlineRenderer.setPathUnderliningEnabled(false);
            editor.highlightState.isColorHighlightingEnabled = false;
            editor.bracketMatchState.setEnabled(false);
            editor.bracketGuideRenderer.setEnabled(false);
            editor.indentGuideRenderer.setIndentGuidesEnabled(false);
            editor.whitespaceGuideState.setWhitespaceGuidesEnabled(false);
            editor.wrapWordIndicatorRender.setEnabled(false);
            editor.inlinePredictionState.setAutoCompletionEnabled(false);
            editor.inlinePredictionState.setAutoPathCompletionEnabled(false);
            editor.charAnimationConfig.setEnabled(false);
            editor.highlightState.setHighlightCurrentLine(false);
            editor.editorConfig.behaviorConfig.setIndentationBlocksEnabled(false, editor);
            editor.foldState.setCodeFoldingEnabled(false);
        }
        editor.invalidate();
    }
}
