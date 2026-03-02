package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.BracketGuideState;
import com.yn.sodiumeditor.state.BracketGuideToken;

import java.util.List;

/**
 * Renderer class for bracket guides.
 * Handles drawing vertical lines at bracket positions.
 */
public class BracketGuideRenderer {

    private final SodiumEditor view;
    private final BracketGuideState state;

    public final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float bracketGuideStrokeWidth = 2f;
    private float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
    private float baseBracketGuideTextSizePx = 0f;

    public BracketGuideRenderer(SodiumEditor view, BracketGuideState state) {
        this.view = view;
        this.state = state;
        bracketGuidePaint.setColor(0xFF888888);
        bracketGuidePaint.setStyle(Paint.Style.STROKE);
        bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
    }

    public void setEnabled(boolean enabled) {
        state.setBracketGuidesEnabled(enabled);
    }

    public boolean isEnabled() {
        return state.isBracketGuidesEnabled();
    }

    public void setColor(int color) {
        bracketGuidePaint.setColor(color);
    }

    public void setStrokeWidth(float width) {
        if (baseBracketGuideStrokeWidth == width) return;
        baseBracketGuideStrokeWidth = width;
        baseBracketGuideTextSizePx = view.getPaintTextSizeForBracket();
        state.invalidateCache();
    }

    public void applyScaledStrokeWidth(float scaledWidth) {
        bracketGuideStrokeWidth = scaledWidth;
        bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
    }

    public void setBaseTextSizePx(float sizePx) {
        baseBracketGuideTextSizePx = sizePx;
    }

    public float getBaseStrokeWidth() {
        return baseBracketGuideStrokeWidth;
    }

    public float getBaseTextSizePx() {
        return baseBracketGuideTextSizePx;
    }

    public Paint getPaint() {
        return bracketGuidePaint;
    }

    public void invalidateCache() {
        state.invalidateCache();
    }

    public void drawGuidesForLine(Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
        if (!state.isBracketGuidesEnabled()
                || view.isHeavyDrawSuppressedForBracket()
                || guideTokens == null
                || guideTokens.isEmpty()) return;
        if (line == null) line = "";
        state.resetGuideSeenX();
        float top = view.getDrawLineTopForBracket(globalLine);
        float bottom = top + view.getLineHeightForBracket();
        int firstNonSpace = getFirstNonSpaceIndex(line);
        boolean adjustTopGuideToClosingBrace = (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
        float closingBraceX =
                adjustTopGuideToClosingBrace ? getGuideXForColumn(line, firstNonSpace, globalLine) : 0f;

        int tokenIndex = 0;
        for (BracketGuideToken token : guideTokens) {
            float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.x;
            tokenIndex++;
            boolean seen = false;
            for (int i = 0; i < state.getGuideSeenXCount(); i++) {
                if (Math.abs(state.getGuideSeenXAt(i) - x) <= 0.5f) {
                    seen = true;
                    break;
                }
            }
            if (seen) continue;
            state.ensureGuideSeenXBuffer(state.getGuideSeenXCount() + 1);
            state.addGuideSeenX(x);

            if (!isWhitespaceAtX(line, globalLine, x)) continue;
            canvas.drawLine(x, top, x, bottom, bracketGuidePaint);
        }
    }

    public float getGuideXForColumn(String line, int column, int globalLine) {
        if (line == null) line = "";
        if (column <= line.length()) {
            return view.highlightRenderer.measureText(line, column, globalLine);
        }
        float base = view.highlightRenderer.measureText(line, line.length(), globalLine);
        float spaceWidth = view.whitespaceGuideRenderer.getVisualSpaceWidth(view.paint);
        return base + spaceWidth * (column - line.length());
    }

    public boolean isWhitespaceAtX(String line, int globalLine, float x) {
        if (line == null || line.isEmpty()) return true;
        if (x <= 0f) return Character.isWhitespace(line.charAt(0));

        List<com.yn.sodiumeditor.state.HighlightSpan> spans =
                view.highlightState.highlightCache.get(globalLine);
        if (spans == null) {
            spans = view.highlightRenderer.calculateSpansForLine(line, globalLine);
            view.highlightState.highlightCache.put(globalLine, spans);
        }

        final int len = line.length();
        float currentX = 0f;
        boolean prevWhitespace = false;
        final float eps = 0.25f;

        int pos = 0;
        if (spans != null && !spans.isEmpty()) {
            for (com.yn.sodiumeditor.state.HighlightSpan span : spans) {
                if (pos >= len) break;
                if (span.end <= pos) continue;
                if (span.start > pos) {
                    for (int i = pos; i < Math.min(span.start, len); i++) {
                        float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                        if (x >= currentX - eps && x <= currentX + adv + eps) {
                            return Character.isWhitespace(line.charAt(i));
                        }
                        currentX += adv;
                    }
                }
                int start = Math.max(pos, span.start);
                int end = Math.min(len, span.end);
                for (int i = start; i < end; i++) {
                    float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                    if (x >= currentX - eps && x <= currentX + adv + eps) {
                        return Character.isWhitespace(line.charAt(i));
                    }
                    currentX += adv;
                }
                pos = Math.max(pos, end);
            }
        }

        if (pos < len) {
            for (int i = pos; i < len; i++) {
                float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                if (x >= currentX - eps && x <= currentX + adv + eps) {
                    return Character.isWhitespace(line.charAt(i));
                }
                currentX += adv;
            }
        }

        return prevWhitespace;
    }

    private static int getFirstNonSpaceIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) return i;
        }
        return -1;
    }
}
