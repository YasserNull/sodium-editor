package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.config.SearchConfig;
import com.yn.sodiumeditor.core.SearchEngine;

/**
 * Renders search highlights on the canvas.
 * Handles visual representation of search matches.
 */
public final class SearchRenderer {

    private final SearchConfig config;
    private final SearchEngine engine;
    private final RenderCallback callback;

    private final Paint searchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentSearchMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface RenderCallback {
        float measureTextForSearch(String line, int index, int globalLine);
        float measureTextWithVisualSpacesForSearch(String line, int start, int end);
        int getCursorLine();
        int getCursorChar();
        boolean hasSelection();
    }

    public SearchRenderer(SearchConfig config, SearchEngine engine, RenderCallback callback) {
        this.config = config;
        this.engine = engine;
        this.callback = callback;

        searchHighlightPaint.setStyle(Paint.Style.FILL);
        searchHighlightPaint.setColor(config.getSearchHighlightColor());
        currentSearchMatchPaint.setColor(config.getCurrentSearchMatchColor());
    }

    public void drawSearchHighlightsForLine(Canvas canvas, String line, int globalLine, float top, float bottom) {
        int[] spans = engine.getSearchMatchSpansForLine(line, globalLine);
        if (spans.length == 0) return;

        for (int i = 0; i + 1 < spans.length; i += 2) {
            int start = spans[i];
            int end = spans[i + 1];
            if (end <= start) continue;

            float left = callback.measureTextForSearch(line, start, globalLine);
            float right = callback.measureTextForSearch(line, end, globalLine);

            boolean isCurrentMatch = config.isHighlightCurrentSearchMatch()
                    && !callback.hasSelection()
                    && globalLine == callback.getCursorLine()
                    && callback.getCursorChar() >= start
                    && callback.getCursorChar() <= end;

            Paint paintToUse = isCurrentMatch ? currentSearchMatchPaint : searchHighlightPaint;
            canvas.drawRect(left, top, right, bottom, paintToUse);
        }
    }

    public void drawSearchHighlightsForSegment(
            Canvas canvas,
            String line,
            int globalLine,
            int segStart,
            int segEnd,
            float top,
            float bottom) {
        int[] spans = engine.getSearchMatchSpansForLine(line, globalLine);
        if (spans.length == 0) return;

        for (int i = 0; i + 1 < spans.length; i += 2) {
            int start = spans[i];
            int end = spans[i + 1];
            if (end <= start) continue;

            int s = Math.max(segStart, start);
            int e = Math.min(segEnd, end);
            if (e <= s) continue;

            float left = callback.measureTextWithVisualSpacesForSearch(line, segStart, s);
            float right = left + callback.measureTextWithVisualSpacesForSearch(line, s, e);

            boolean isCurrentMatch = config.isHighlightCurrentSearchMatch()
                    && !callback.hasSelection()
                    && globalLine == callback.getCursorLine()
                    && callback.getCursorChar() >= start
                    && callback.getCursorChar() <= end;

            Paint paintToUse = isCurrentMatch ? currentSearchMatchPaint : searchHighlightPaint;
            canvas.drawRect(left, top, right, bottom, paintToUse);
        }
    }

    public void updateColors() {
        searchHighlightPaint.setColor(config.getSearchHighlightColor());
        currentSearchMatchPaint.setColor(config.getCurrentSearchMatchColor());
    }
}
