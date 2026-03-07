package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.InlinePredictionState;

/**
 * Renderer class for inline predictions.
 * Handles drawing prediction suggestions on the canvas.
 */
public class InlinePredictionRenderer {

    private final SodiumEditor view;
    private final InlinePredictionState state;

    public final Paint suggestionPaint = new Paint();

    public InlinePredictionRenderer(SodiumEditor view, InlinePredictionState state) {
        this.view = view;
        this.state = state;
        suggestionPaint.setAntiAlias(true);
        suggestionPaint.setSubpixelText(true);
        suggestionPaint.setHinting(Paint.HINTING_ON);
    }

    public void initPaints(Paint basePaint) {
        suggestionPaint.set(basePaint);
        suggestionPaint.setColor(0xFFAAAAAA); // Default faint gray
        state.setSuggestionTextSizeCustom(false);
        state.setSuggestionTextSizeScale(1f);
    }

    public void onEditorTypefaceChanged(android.graphics.Typeface typeface) {
        suggestionPaint.setTypeface(typeface);
    }

    public void onTextSizeChanged(float sizePx) {
        if (!state.isSuggestionTextSizeCustom()) {
            state.setSuggestionTextSizeScale(1f);
        }
        suggestionPaint.setTextSize(sizePx * state.getSuggestionTextSizeScale());
    }

    public void setSuggestionTextSize(float sizeSp) {
        state.setSuggestionTextSizeCustom(true);
        float px = sizeSp * view.getResources().getDisplayMetrics().scaledDensity;
        float base = view.editorConfig.paint.getTextSize();
        if (base > 0f) {
            state.setSuggestionTextSizeScale(px / base);
        } else {
            state.setSuggestionTextSizeScale(1f);
        }
        suggestionPaint.setTextSize(base * state.getSuggestionTextSizeScale());
        view.invalidate();
    }

    public void setSuggestionColor(int color) {
        suggestionPaint.setColor(color);
    }

    public void drawInlinePrediction(Canvas canvas, String lineContent, int globalLine, float textBaselineY) {
        boolean allowSuggestion =
                state.activeSuggestionIsPath ? state.isAutoPathCompletionEnabled() : state.isAutoCompletionEnabled();
        if (!allowSuggestion || !state.hasActiveSuggestion() || globalLine != state.activeSuggestionLine) {
            return;
        }
        if (lineContent == null) lineContent = "";

        int cursorPositionInLine = state.activeSuggestionCharStart + state.activeSuggestionWordFragment.length();
        if (cursorPositionInLine < 0 || cursorPositionInLine > lineContent.length()) return;

        float suggestionStartX =
                view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
                        view, lineContent, 0, cursorPositionInLine, view.editorConfig.paint);
        canvas.drawText(state.activeSuggestion, suggestionStartX, textBaselineY, suggestionPaint);

        float suggestionTextWidth = suggestionPaint.measureText(state.activeSuggestion);
        float leftView = suggestionStartX + view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl) - (view.isRtl ? -view.scrollManager.scrollX : view.scrollManager.scrollX);
        float rightView = leftView + suggestionTextWidth;
        float topView = view.scrollManager.getDrawLineTop(globalLine);
        float bottomView = topView + view.lineHeight;

        state.activeSuggestionRect.set(leftView, topView, rightView, bottomView);
    }

    public void drawInlinePredictionWrapped(
            Canvas canvas,
            String lineContent,
            int globalLine,
            int segStart,
            int segEnd,
            int visualIndex,
            float textBaselineY) {
        boolean allowSuggestion =
                state.activeSuggestionIsPath ? state.isAutoPathCompletionEnabled() : state.isAutoCompletionEnabled();
        if (!allowSuggestion || !state.hasActiveSuggestion() || globalLine != state.activeSuggestionLine) {
            return;
        }

        int cursorPositionInLine = state.activeSuggestionCharStart + state.activeSuggestionWordFragment.length();
        if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

        float suggestionStartX_canvas =
                view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
                        view, lineContent, segStart, cursorPositionInLine, view.editorConfig.paint);
        canvas.drawText(state.activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

        float suggestionTextWidth = suggestionPaint.measureText(state.activeSuggestion);

        float left_view = suggestionStartX_canvas + view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl) - (view.isRtl ? -view.scrollManager.scrollX : view.scrollManager.scrollX);
        float right_view = left_view + suggestionTextWidth;
        if (view.isRtl) {
            float baseX = view.viewRender.textRender.getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);
            left_view += baseX;
            right_view += baseX;
        }
        float top_view = visualIndex * view.lineHeight - view.scrollManager.scrollY;
        float bottom_view = top_view + view.lineHeight;

        state.activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
    }
}
