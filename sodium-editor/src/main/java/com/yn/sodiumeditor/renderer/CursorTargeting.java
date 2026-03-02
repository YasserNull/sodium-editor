package com.yn.sodiumeditor.renderer;

import androidx.annotation.Nullable;

import java.util.Map;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles cursor targeting and position calculations.
 */
public final class CursorTargeting {
    private final SodiumEditor view;
    private final TextMeasurement textMeasurement;
    private final LineCacheManager lineCacheManager;

    public CursorTargeting(SodiumEditor view, TextMeasurement textMeasurement, LineCacheManager lineCacheManager) {
        this.view = view;
        this.textMeasurement = textMeasurement;
        this.lineCacheManager = lineCacheManager;
    }

    /**
     * Gets the cursor target (line and character) for a given view position.
     */
    public SodiumEditor.CursorTarget getCursorTargetForPosition(
            float viewX, float viewY, @Nullable Map<Integer, String> directLines) {
        float y = viewY + view.scrollManager.scrollY;
        int visualIndex = Math.max(0, (int) (y / view.lineHeight));
        SodiumEditor.VisualLinePosition pos =
                view.wrapWordState.isWordWrapEnabled
                        ? view.wrapWordMapper.getVisualPositionForIndex(view, visualIndex, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())))
                        : new SodiumEditor.VisualLinePosition(view.mapVisibleIndexToGlobal(visualIndex), 0);
        String line = lineCacheManager.getLineTextForRenderWithDirect(pos.line, directLines);
        if (!view.wrapWordState.isWordWrapEnabled) {
            float x = view.viewToTextXPublic(viewX);
            int charIndex = textMeasurement.getCharIndexForX(line, x, pos.line);
            int clamped = Math.max(0, Math.min(charIndex, view.getLogicalLineLength(pos.line, line)));
            return new SodiumEditor.CursorTarget(pos.line, clamped);
        }
        int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, pos.line, line, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())), view.editorConfig.paint);
        int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
        int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
        int segEnd = view.wrapWordEngine.getWrapSegmentEnd(starts, seg, line.length());
        float x = view.viewToTextXPublic(viewX);
        int charIndex = textMeasurement.getCharIndexForXInRange(line, pos.line, segStart, segEnd, x);
        int clamped = Math.max(0, Math.min(charIndex, line.length()));
        return new SodiumEditor.CursorTarget(pos.line, clamped);
    }
}
