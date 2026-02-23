package com.yn.sodiumeditor.renderer;

import androidx.annotation.Nullable;

import java.util.Map;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.WordWrapManager;

/**
 * Handles cursor targeting and position calculations.
 */
public final class CursorTargeting {
    private final SodiumEditorView view;
    private final TextMeasurement textMeasurement;
    private final LineCacheManager lineCacheManager;

    public CursorTargeting(SodiumEditorView view, TextMeasurement textMeasurement, LineCacheManager lineCacheManager) {
        this.view = view;
        this.textMeasurement = textMeasurement;
        this.lineCacheManager = lineCacheManager;
    }

    /**
     * Gets the cursor target (line and character) for a given view position.
     */
    public SodiumEditorView.CursorTarget getCursorTargetForPosition(
            float viewX, float viewY, @Nullable Map<Integer, String> directLines) {
        float y = viewY + view.scrollManager.scrollY;
        int visualIndex = Math.max(0, (int) (y / view.lineHeight));
        SodiumEditorView.VisualLinePosition pos =
                view.wordWrapManager.isWordWrapEnabled
                        ? view.wordWrapManager.getVisualPositionForIndex(view, visualIndex)
                        : new SodiumEditorView.VisualLinePosition(view.mapVisibleIndexToGlobal(visualIndex), 0);
        String line = lineCacheManager.getLineTextForRenderWithDirect(pos.line, directLines);
        if (!view.wordWrapManager.isWordWrapEnabled) {
            float x = view.viewToTextXPublic(viewX);
            int charIndex = textMeasurement.getCharIndexForX(line, x, pos.line);
            int clamped = Math.max(0, Math.min(charIndex, view.getLogicalLineLength(pos.line, line)));
            return new SodiumEditorView.CursorTarget(pos.line, clamped);
        }
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, pos.line, line);
        int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, line.length());
        float x = view.viewToTextXPublic(viewX);
        int charIndex = textMeasurement.getCharIndexForXInRange(line, pos.line, segStart, segEnd, x);
        int clamped = Math.max(0, Math.min(charIndex, line.length()));
        return new SodiumEditorView.CursorTarget(pos.line, clamped);
    }
}
