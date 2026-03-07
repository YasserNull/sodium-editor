package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.IndentGuideState;

/**
 * Renderer class for indent guides.
 * Handles drawing vertical lines at indentation positions.
 */
public class IndentGuideRenderer {

    private static final float DEFAULT_STROKE_WIDTH = 2f;

    private final SodiumEditor view;
    private final IndentGuideState state;

    public final Paint indentGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float indentGuideStrokeWidth = DEFAULT_STROKE_WIDTH;

    public IndentGuideRenderer(SodiumEditor view, IndentGuideState state) {
        this.view = view;
        this.state = state;
        initDefaults(view.editorConfig.paint);
    }

    public void initDefaults(Paint basePaint) {
        state.setBaseIndentGuideTextSizePx(basePaint.getTextSize());
        indentGuidePaint.setColor(0xFF555555);
        indentGuidePaint.setStyle(Paint.Style.STROKE);
        indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
    }

    public void setIndentGuidesEnabled(boolean enabled) {
        state.setIndentGuidesEnabled(enabled);
        view.invalidate();
    }

    public boolean isIndentGuidesEnabled() {
        return state.isIndentGuidesEnabled();
    }

    public void setIndentGuidesColor(int color) {
        indentGuidePaint.setColor(color);
        view.invalidate();
    }

    public void setIndentGuidesStrokeWidth(float width) {
        if (indentGuideStrokeWidth == width) return;
        state.setBaseIndentGuideStrokeWidth(width);
        state.setBaseIndentGuideTextSizePx(view.editorConfig.paint.getTextSize());
        updateForTextSize(view.editorConfig.paint.getTextSize());
        view.invalidate();
    }

    public void updateForTextSize(float sizePx) {
        indentGuideStrokeWidth = Math.max(1f, scaleByTextSize(state.getBaseIndentGuideStrokeWidth(), state.getBaseIndentGuideTextSizePx(), sizePx));
        indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
    }

    public void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
        if (!state.isIndentGuidesEnabled()
                || !view.editorConfig.behaviorConfig.isIndentationBlocksEnabled
                || view.viewRender.textRender.isHeavyDrawSuppressed()) {
            return;
        }
        if (!isLineInIndentBlock(globalLine)) return;
        if (line == null || line.isEmpty()) return;
        int unitSpaces = 2;
        if (unitSpaces <= 0) return;

        float top = view.scrollManager.getDrawLineTop(globalLine);
        float bottom = top + view.lineHeight;
        int columns = 0;
        int nextGuide = unitSpaces;
        float x = 0f;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') break;
            float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.editorConfig.paint);
            if (c == '\t') {
                columns += 4;
            } else {
                columns += 1;
            }
            x += adv;
            while (columns >= nextGuide) {
                if (false) {
                    canvas.drawLine(x, top, x, bottom, indentGuidePaint);
                }
                nextGuide += unitSpaces;
            }
        }
    }

    public boolean isLineInIndentBlock(int globalLine) {
        if (!view.editorConfig.behaviorConfig.isIndentationBlocksEnabled) return false;
        if (state.indentGuideIntervals.isEmpty()) return false;
        int lo = 0;
        int hi = state.indentGuideIntervals.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int[] interval = state.indentGuideIntervals.get(mid);
            if (globalLine < interval[0]) {
                hi = mid - 1;
            } else if (globalLine > interval[1]) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private static float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
        if (baseTextSizePx <= 0f) return baseValue;
        return baseValue * (newTextSizePx / baseTextSizePx);
    }
}
