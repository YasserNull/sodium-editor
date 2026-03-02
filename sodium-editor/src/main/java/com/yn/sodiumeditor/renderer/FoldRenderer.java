package com.yn.sodiumeditor.renderer;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.FoldRange;
import com.yn.sodiumeditor.state.FoldState;

/**
 * Renderer class for code folding.
 * Handles drawing fold markers and folded line placeholders.
 */
public class FoldRenderer {

    private final SodiumEditor view;
    private final FoldState state;

    public final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF foldPlaceholderRect = new RectF();

    public float foldMarkerGutterWidth = 0f;
    public float foldMarkerTextScale = 1f;
    public float foldMarkerSpacing = 0f;
    public float foldMarkerEdgePadding = 4f;
    public float foldPlaceholderCorner = 3f;
    public float foldPlaceholderPadX = 3f;
    public float foldPlaceholderPadY = 2f;

    public FoldRenderer(SodiumEditor view, FoldState state) {
        this.view = view;
        this.state = state;
    }

    public void init(float density) {
        foldMarkerPaint.setTextAlign(Paint.Align.CENTER);
        foldMarkerPaint.setTextSize(view.editorConfig.paint.getTextSize() * foldMarkerTextScale);
        foldPlaceholderPaint.setStyle(Paint.Style.FILL);
        foldRipplePaint.setStyle(Paint.Style.FILL);
        foldMarkerSpacing = 0f;
        foldMarkerEdgePadding = 4f * density;
        foldPlaceholderCorner = 3f * density;
        foldPlaceholderPadX = 3f * density;
        foldPlaceholderPadY = 2f * density;
    }

    public void setFoldPlaceholderColor(int color) {
        foldPlaceholderPaint.setColor(color);
        if (state.isCodeFoldingEnabled()) view.invalidate();
    }

    public void setFoldMarkerColor(int color) {
        foldMarkerPaint.setColor(color);
        if (state.isCodeFoldingEnabled()) view.invalidate();
    }

    public void setFoldMarkerTextSize(float size) {
        float base = view.editorConfig.paint.getTextSize();
        if (base <= 0f) return;
        foldMarkerTextScale = size / base;
        foldMarkerPaint.setTextSize(base * foldMarkerTextScale);
        view.requestLayout();
        if (view.wrapWordState.isWordWrapEnabled) view.wrapWordBuilder.invalidate(true, true);
        view.invalidate();
    }

    public void setFoldMarkerGutterWidth(float width) {
        foldMarkerGutterWidth = width;
    }

    public float getFoldMarkerGutterWidth() {
        return foldMarkerGutterWidth;
    }

    public float getFoldMarkerSpacing() {
        return foldMarkerSpacing;
    }

    public float getFoldMarkerEdgePadding() {
        return foldMarkerEdgePadding;
    }

    public String buildFoldDisplayLine(String line, FoldRange range, int[] placeholderBoundsOut) {
        return buildFoldDisplayLineInternal(line, range, placeholderBoundsOut);
    }

    public String buildFoldDisplayLineInternal(String line, FoldRange range, int[] placeholderBoundsOut) {
        if (line == null) line = "";
        int placeholderStart = 0;
        int placeholderEnd = 0;
        String display;

        if (range.isBlockComment) {
            int safeIdx = Math.max(0, Math.min(range.openCharIndex, line.length()));
            String prefix = line.substring(0, safeIdx);
            placeholderStart = prefix.length() + 2;
            placeholderEnd = placeholderStart + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
            display = prefix + "/*" + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT + "*/";
        } else if (range.isIndentFold) {
            String prefix = line;
            placeholderStart = prefix.length();
            placeholderEnd = placeholderStart + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
            display = prefix + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT;
        } else {
            int safeIdx = Math.max(0, Math.min(range.openCharIndex, Math.max(0, line.length() - 1)));
            String prefix = line.substring(0, safeIdx + 1);
            placeholderStart = prefix.length();
            placeholderEnd = placeholderStart + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
            display = prefix + view.editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT + range.closeChar;
        }

        if (placeholderBoundsOut != null && placeholderBoundsOut.length >= 2) {
            placeholderBoundsOut[0] = placeholderStart;
            placeholderBoundsOut[1] = placeholderEnd;
        }
        return display;
    }

    public String getFoldMarkerForLine(int line, String lineText) {
        if (!state.isCodeFoldingEnabled()) return null;
        FoldRange range = state.getFoldRange(line);
        if (range != null) return range.collapsed ? ">" : "v";
        if (lineText == null) return null;
        boolean isIndentCandidate = view.isIndentationBlocksEnabled && isIndentFoldCandidate(lineText);
        if (!isIndentCandidate && !shouldShowFoldMarkerFromLine(lineText)) return null;
        FoldRange found = view.foldEngine.findFoldRangeForLine(line);
        if (found == null) return null;
        state.putFoldRange(found.startLine, found);
        if (found.isIndentFold) view.indentGuideEngine.markIntervalsDirty();
        return "v";
    }

    public boolean isIndentFoldCandidate(String line) {
        if (line == null || line.isEmpty()) return false;
        String trimmed = rstripWhitespace(line);
        return !trimmed.isEmpty() && trimmed.endsWith(":");
    }

    private static String rstripWhitespace(String text) {
        if (text == null || text.isEmpty()) return "";
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c != ' ' && c != '\t') break;
            end--;
        }
        return (end <= 0) ? "" : text.substring(0, end);
    }

    public boolean shouldShowFoldMarkerFromLine(String line) {
        if (line == null || line.isEmpty()) return false;
        int blockStart = line.indexOf("/*");
        if (blockStart >= 0) {
            int blockEnd = line.indexOf("*/", blockStart + 2);
            if (blockEnd < 0) return true;
        }

        int idx = line.indexOf('{');
        if (idx >= 0) return true;
        idx = line.indexOf('(');
        if (idx >= 0) return true;
        idx = line.indexOf('[');
        return idx >= 0;
    }

    public void drawFoldMarkersForVisibleLines(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!state.isCodeFoldingEnabled()) return;

        float markerX =
                view.isRtl
                        ? (view.getGutterStartX()
                        + view.lineNumberConfig.getGutterSeparatorWidth()
                        + foldMarkerEdgePadding)
                        : (view.lineNumberRenderer.getSeparatorLeft(view.getGutterStartX())
                        - foldMarkerEdgePadding);

        for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int line = view.mapVisibleIndexToGlobal(v);
            String marker = getFoldMarkerForLine(line, view.getLineTextForRender(line));
            if (marker == null) continue;
            float y =
                    Math.round(
                            v * view.lineHeight - view.scrollManager.scrollY + view.lineHeight - view.editorConfig.paint.descent());
            if (line == state.foldRippleLine && state.foldRippleAlpha > 0f) {
                int base = foldMarkerPaint.getColor();
                int alpha = Math.min(255, Math.max(0, (int) (255f * state.foldRippleAlpha)));
                foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
                float centerY =
                        Math.round(v * view.lineHeight - view.scrollManager.scrollY + view.lineHeight * 0.5f);
                canvas.drawCircle(markerX, centerY, state.foldRippleRadius, foldRipplePaint);
            }
            canvas.drawText(marker, markerX, y, foldMarkerPaint);
        }
    }

    public void drawFoldedLine(Canvas canvas, String line, int globalLine) {
        FoldRange range = state.getFoldRange(globalLine);
        if (range == null) return;
        if (line == null) line = "";

        float y =
                Math.round(
                        view.scrollManager.getDrawLineTop(globalLine) + view.lineHeight - view.editorConfig.paint.descent());
        view.editorConfig.paint.getTextBounds(
                SodiumEditor.FOLD_PLACEHOLDER_TEXT,
                0,
                SodiumEditor.FOLD_PLACEHOLDER_TEXT.length(),
                view.textBounds);
        float top = Math.round(y + view.textBounds.top - foldPlaceholderPadY);
        float bottom = Math.round(y + view.textBounds.bottom + foldPlaceholderPadY);

        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }

        float xStart = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, view.editorConfig.paint.measureText(SodiumEditor.FOLD_PLACEHOLDER_TEXT));
        foldPlaceholderRect.set(xStart, top, xStart + placeholderWidth, bottom);
        canvas.drawRoundRect(foldPlaceholderRect, foldPlaceholderCorner, foldPlaceholderCorner, foldPlaceholderPaint);

        view.highlightRenderer.drawHighlightedSegment(canvas, line, globalLine, 0, prefixEnd, 0f, y);

        view.editorConfig.paint.setUnderlineText(false);
        canvas.drawText(SodiumEditor.FOLD_PLACEHOLDER_TEXT, xStart, y, view.editorConfig.paint);

        float xAfter = xStart + placeholderWidth;
        if (range.isBlockComment) {
            Paint commentPaint =
                    (view.highlightState.blockCommentHighlightRule != null)
                            ? view.highlightState.blockCommentHighlightRule.paint
                            : view.editorConfig.paint;
            commentPaint.setUnderlineText(false);
            canvas.drawText("*/", xAfter, y, commentPaint);
        } else if (!range.isIndentFold) {
            canvas.drawText(String.valueOf(range.closeChar), xAfter, y, view.editorConfig.paint);
        }
    }

    public boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
        if (!state.isCodeFoldingEnabled()) return false;
        FoldRange range = state.getFoldRange(globalLine);
        if (range == null || !range.collapsed) return false;
        if (line == null) line = "";

        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }
        float xStart = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, view.editorConfig.paint.measureText(SodiumEditor.FOLD_PLACEHOLDER_TEXT));
        float pad = Math.max(0f, foldPlaceholderPadX);
        float left = xStart - pad;
        float right = xStart + placeholderWidth + pad;
        return localX >= left && localX <= right;
    }

    public void startFoldMarkerRipple(int line) {
        if (!state.isCodeFoldingEnabled() || !view.lineNumberState.isShowLineNumbers()) return;
        state.foldRippleLine = line;
        float gutterWidth = foldMarkerGutterWidth;
        if (gutterWidth <= 0f) {
            gutterWidth =
                    foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
        }
        state.foldRippleMaxRadius =
                Math.max(view.lineHeight * 0.35f, Math.min(view.lineHeight * 0.6f, gutterWidth * 0.6f));
        if (state.foldRippleAnimator != null) state.foldRippleAnimator.cancel();
        state.foldRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        state.foldRippleAnimator.setDuration(220);
        state.foldRippleAnimator.addUpdateListener(
                a -> {
                    float t = (float) a.getAnimatedValue();
                    state.foldRippleRadius = state.foldRippleMaxRadius * t;
                    state.foldRippleAlpha = 0.35f * (1f - t);
                    view.invalidate();
                });
        state.foldRippleAnimator.addListener(
                new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        state.foldRippleAlpha = 0f;
                        state.foldRippleRadius = 0f;
                        state.foldRippleLine = -1;
                        view.invalidate();
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        state.foldRippleAlpha = 0f;
                        state.foldRippleRadius = 0f;
                        state.foldRippleLine = -1;
                        view.invalidate();
                    }
                });
        state.foldRippleAnimator.start();
    }
}
