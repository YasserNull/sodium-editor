package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Map;

import com.yn.sodiumeditor.SodiumEditorView;

/**
 * Handles line caching and file-based line retrieval operations.
 */
public final class LineCacheManager {
    private final SodiumEditorView view;
    private TextMeasurement textMeasurement;

    public LineCacheManager(SodiumEditorView view) {
        this.view = view;
    }

    public void setTextMeasurement(TextMeasurement textMeasurement) {
        this.textMeasurement = textMeasurement;
    }

    /**
     * Gets line text for rendering, checking multiple cache sources.
     */
    @Nullable
    public String getLineTextForRender(int line) {
        return getLineTextForRenderWithDirect(line, null);
    }

    /**
     * Gets line text for rendering with direct file access support.
     */
    @Nullable
    public String getLineTextForRenderWithDirect(int line, @Nullable Map<Integer, String> direct) {
        if (line < 0) return null;
        if (direct != null) {
            String cached = direct.get(line);
            if (cached != null) return cached;
        }
        String mod = view.modifiedLines.get(line);
        if (mod != null) return mod;
        if (line >= view.windowStartLine && line < view.windowStartLine + view.linesWindow.size()) {
            String text = getLineFromWindowLocal(line - view.windowStartLine);
            return (text != null) ? text : "";
        }
        if (view.fileManager.getSourceFile() != null && view.fileManager.isIndexReady()) {
            long offset;
            synchronized (view.fileManager.lineOffsetsLock) {
                if (line < 0 || line >= view.fileManager.getLineOffsets().length) return null;
                offset = view.fileManager.getLineOffsets()[line];
            }
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
                return view.fileManager.readLineUtf8AtByte(raf, offset);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Populates direct lines cache for a range of lines.
     */
    public void populateDirectLinesForRange(int startLine, int endLine, Map<Integer, String> direct) {
        if (direct == null) return;
        int s = Math.max(0, Math.min(startLine, endLine));
        int e = Math.max(startLine, endLine);
        for (int line = s; line <= e; line++) {
            if (direct.containsKey(line)) continue;
            String text = getLineTextForRender(line);
            if (text == null) text = "";
            direct.put(line, text);
        }
    }

    /**
     * Gets a line from the window cache by local index.
     */
    public String getLineFromWindowLocal(int localIdx) {
        if (localIdx < 0 || localIdx >= view.linesWindow.size()) return null;
        return view.linesWindow.get(localIdx);
    }

    /**
     * Updates a line in the window cache.
     */
    public void updateLocalLine(int localIdx, String text) {
        if (localIdx >= 0 && localIdx < view.linesWindow.size()) {
            view.linesWindow.set(localIdx, text);
            view.wrapWordBuilder.onLineContentChanged(view, view.windowStartLine + localIdx, text);
            view.clearStreamedLineInfo(view.windowStartLine + localIdx);
        }
    }

    /**
     * Invalidates a specific global line for redraw.
     */
    public void invalidateLineGlobal(int globalLine) {
        if (view.wrapWordState.isWordWrapEnabled) {
            view.invalidate();
            return;
        }
        int idx = view.foldManager.isCodeFoldingEnabled ? view.getVisibleIndexForGlobalLine(globalLine) : globalLine;
        float top = (idx * view.lineHeight) - view.scrollManager.scrollY;
        view.invalidate(0, (int) Math.floor(top), view.getWidth(), (int) Math.ceil(top + view.lineHeight));
    }

    /**
     * Draws delete animation for a segment of text.
     */
    public void drawDeleteAnimationForSegment(
            Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        if (!view.charAnimationConfig.isEnabled()) return;
        if (globalLine != view.charAnimator.getDelAnimLine()
                || view.charAnimator.getDelAnimText() == null
                || view.charAnimator.getDelAnimText().isEmpty()
                || view.charAnimator.getDelAnimAlpha() <= 0f) return;
        if (line == null) line = "";
        int at = Math.max(0, Math.min(view.charAnimator.getDelAnimAtChar(), line.length()));
        if (at < segStart || at > segEnd) return;
        float x = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, at, view.paint);
        Paint ghostPaint = (view.charAnimator.getDelAnimPaint() != null) ? view.charAnimator.getDelAnimPaint() : view.paint;
        Paint tempPaint = view.charAnimator.getTempPaint();
        tempPaint.set(ghostPaint);
        tempPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimator.getDelAnimAlpha()))));
        canvas.drawText(view.charAnimator.getDelAnimText(), x, y, tempPaint);
    }

    /**
     * Recalculates the maximum line width for the current window.
     */
    public void recalculateMaxLineWidth() {
        final int startLine;
        final ArrayList<String> snapshot;
        synchronized (view.linesWindow) {
            startLine = view.windowStartLine;
            snapshot = new ArrayList<>(view.linesWindow);
        }
        if (snapshot.isEmpty()) return;

        float mx = 0f;
        for (int i = 0; i < snapshot.size(); i++) {
            String line = snapshot.get(i);
            if (line == null) line = "";
            float w = getWidthForLine(startLine + i, line);
            synchronized (view.lineWidthCache) {
                view.lineWidthCache.put(startLine + i, w);
            }
            if (w > mx) mx = w;
        }
        view.currentMaxWindowLineWidth = mx;
        view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, mx);
        view.scrollManager.clampScrollX();
        view.invalidate();
    }

    /**
     * Gets the cached width for a line (delegates to TextMeasurement).
     */
    private float getWidthForLine(int globalIndex, String line) {
        if (textMeasurement != null) {
            return textMeasurement.getWidthForLine(globalIndex, line);
        }
        // Fallback: compute directly
        String safe = (line == null) ? "" : line;
        float w;
        int logicalLen = view.getLogicalLineLength(globalIndex, safe);
        if (logicalLen > view.highlightState.maxSyntaxLineLength) {
            w = view.highlightRenderer.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
        } else {
            w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, safe, 0, safe.length(), view.paint);
        }
        return w;
    }

    /**
     * Recalculates the maximum line width asynchronously (chunked).
     */
    public void recalculateMaxLineWidthAsync() {
        final int token = ++view.maxWidthRecalcToken;
        final int startLine;
        final ArrayList<String> snapshot;
        synchronized (view.linesWindow) {
            startLine = view.windowStartLine;
            snapshot = new ArrayList<>(view.linesWindow);
        }
        if (snapshot.isEmpty()) return;

        final int chunkSize = 120;
        view.post(
                new Runnable() {
                    int index = 0;
                    float mx = 0f;

                    @Override
                    public void run() {
                        if (token != view.maxWidthRecalcToken) return;
                        int end = Math.min(snapshot.size(), index + chunkSize);
                        for (int i = index; i < end; i++) {
                            String line = snapshot.get(i);
                            if (line == null) line = "";
                            float w = getWidthForLine(startLine + i, line);
                            synchronized (view.lineWidthCache) {
                                view.lineWidthCache.put(startLine + i, w);
                            }
                            if (w > mx) mx = w;
                        }
                        view.currentMaxWindowLineWidth = mx;
                        view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, mx);
                        index = end;
                        if (index < snapshot.size()) {
                            view.post(this);
                        } else {
                            view.scrollManager.clampScrollX();
                            view.invalidate();
                        }
                    }
                });
    }
}
