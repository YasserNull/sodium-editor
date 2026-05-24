package com.yn.sodiumeditor.renderer.draw;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.utils.TextArabicUtils;
import java.util.List;

/**
 * TextLineDraw handles all text line drawing operations for SodiumEditor.
 * This includes:
 * - Drawing text segments with fade effects
 * - Drawing text with visual spaces
 * - Drawing underlines with fade effects
 * - Drawing delete animations
 */
public class TextLineDraw {
    private static final String TAG = "SodiumCharAnim";
    private static final int MAX_DRAW_TRACE_LOGS = 300;

    private final SodiumEditor editor;
    private int charFadeDrawLogCount = 0;
    private int charDrawTraceLogCount = 0;

    public TextLineDraw(SodiumEditor editor) {
        this.editor = editor;
    }

    // ========================================================================
    // Text Drawing with Visual Spaces and Fade Effects
    // ========================================================================

    /**
     * Draw text segment with fade effect
     */
    public float drawTextSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha) {
        if (start >= end) return 0f;
        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (hasFade && start < fadeEnd && end > fadeStart) {
            logCharDrawTraceAlways(
                    "ENTER_TEXT_SEGMENT",
                    line,
                    start,
                    end,
                    fadeAlpha,
                    x,
                    y,
                    fadeStart,
                    fadeEnd,
                    "drawTextSegmentWithFade");
        }
        if (hasFade
                && !editor.binaryRender.isBinarySafeRenderingEnabled()
                && TextArabicUtils.containsArabicScript(line, start, end)) {
            return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
        }
        final int spaceScale = editor.textRender.getVisualSpaceScale();
        if (spaceScale > 1) {
            if (!hasFade || end <= fadeStart || start >= fadeEnd) {
                logCharDrawTrace(
                        "DRAW_VISUAL_BASE",
                        line,
                        start,
                        end,
                        1f,
                        x,
                        y,
                        fadeStart,
                        fadeEnd,
                        "no-overlap-or-no-fade");
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }

            float currentX = x;

            int beforeEnd = Math.min(end, fadeStart);
            if (start < beforeEnd) {
                logCharDrawTrace(
                        "DRAW_VISUAL_BASE_BEFORE",
                        line,
                        start,
                        beforeEnd,
                        1f,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "before-anim");
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
            }

            int fadeSegStart = Math.max(start, fadeStart);
            int fadeSegEnd = Math.min(end, fadeEnd);
            if (fadeSegStart < fadeSegEnd) {
                // Reserve the glyph's advance in the base text stream, then draw only the animated glyph.
                logCharDrawTrace(
                        "DRAW_VISUAL_ANIM",
                        line,
                        fadeSegStart,
                        fadeSegEnd,
                        fadeAlpha,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "animated-visual-spaces");
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
            }

            int afterStart = Math.max(start, fadeEnd);
            if (afterStart < end) {
                logCharDrawTrace(
                        "DRAW_VISUAL_BASE_AFTER",
                        line,
                        afterStart,
                        end,
                        1f,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "after-anim");
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, afterStart, end, currentX, y, segmentPaint, 1f);
            }

            return currentX - x;
        }
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            logCharDrawTrace(
                    "DRAW_BASE_DIRECT",
                    line,
                    start,
                    end,
                    1f,
                    x,
                    y,
                    fadeStart,
                    fadeEnd,
                    "no-overlap-or-no-fade");
            canvas.drawText(line, start, end, x, y, segmentPaint);
            return segmentPaint.measureText(line, start, end);
        }

        float currentX = x;

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            logCharDrawTrace(
                    "DRAW_BASE_BEFORE",
                    line,
                    start,
                    beforeEnd,
                    1f,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "before-anim");
            canvas.drawText(line, start, beforeEnd, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, start, beforeEnd);
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            logCharDrawTrace(
                    "DRAW_ANIM",
                    line,
                    fadeSegStart,
                    fadeSegEnd,
                    fadeAlpha,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "animated-glyph");
            logCharFadeDraw(line, fadeSegStart, fadeSegEnd, fadeAlpha, currentX, y, false);
            editor.charAnimation.charAnimTmpPaint.set(segmentPaint);
            int baseAlpha = segmentPaint.getAlpha();
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            canvas.drawText(
                    line,
                    fadeSegStart,
                    fadeSegEnd,
                    currentX,
                    y + getCharAnimOffsetY(fadeAlpha, segmentPaint),
                    editor.charAnimation.charAnimTmpPaint);
            currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            logCharDrawTrace(
                    "DRAW_BASE_AFTER",
                    line,
                    afterStart,
                    end,
                    1f,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "after-anim");
            canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, afterStart, end);
        }

        return currentX - x;
    }

    /**
     * Draw text segment with fade and underlines
     */
    public float drawTextSegmentWithFadeAndUnderlines(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha,
            @Nullable List<TextRender.UnderlineSpan> underlines, float lineTop, float lineBottom) {
        if (start >= end) return 0f;
        boolean anyUnderliningActive = editor.urlUnderline.isUrlUnderliningActive()
                || editor.pathUnderline.isPathUnderliningActive();
        if (underlines == null || underlines.isEmpty() || !anyUnderliningActive) {
            return drawTextSegmentWithFade(
                    canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        float currentX = x;
        int pos = start;

        for (TextRender.UnderlineSpan span : underlines) {
            if (span.end <= pos) continue;
            if (span.start >= end) break;

            int plainEnd = Math.min(end, Math.max(pos, span.start));
            if (pos < plainEnd) {
                currentX += drawTextSegmentWithFade(
                        canvas, line, pos, plainEnd, currentX, y, segmentPaint,
                        fadeStart, fadeEnd, fadeAlpha);
                pos = plainEnd;
            }

            int underlineStart = Math.max(pos, span.start);
            int underlineEnd = Math.min(end, span.end);
            if (underlineStart < underlineEnd) {
                float underlineXStart = currentX;
                currentX += drawTextSegmentWithFade(
                        canvas, line, underlineStart, underlineEnd, currentX, y, segmentPaint,
                        fadeStart, fadeEnd, fadeAlpha);
                drawUnderlineSegmentWithFade(
                        canvas, line, underlineStart, underlineEnd, underlineXStart, y,
                        lineTop, lineBottom, segmentPaint, fadeStart, fadeEnd, fadeAlpha, span.isPath);
                pos = underlineEnd;
            }
        }

        if (pos < end) {
            currentX += drawTextSegmentWithFade(
                    canvas, line, pos, end, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        return currentX - x;
    }

    /**
     * Draw underline segment with fade
     */
    public void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float baselineY,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha, boolean isPath) {
        if (start >= end) return;

        Paint.FontMetrics fm = TextRender.TL_FONT_METRICS.get();
        textPaint.getFontMetrics(fm);
        float underlineY = baselineY + (fm.descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
        thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

        Paint tmpPaintToUse = isPath ? editor.pathUnderline.getPathUnderlinePaint() : editor.urlUnderline.urlUnderlineTmpPaint;
        tmpPaintToUse.set(textPaint);
        tmpPaintToUse.setStyle(Paint.Style.STROKE);
        tmpPaintToUse.setStrokeWidth(thickness);
        tmpPaintToUse.setUnderlineText(false);

        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            float w = editor.textRender.measureTextWithVisualSpaces(line, start, end, textPaint);
            if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
            return;
        }

        float currentX = x;
        int baseAlpha = textPaint.getAlpha();

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = editor.textRender.measureTextWithVisualSpaces(line, start, beforeEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            float w = editor.textRender.measureTextWithVisualSpaces(line, fadeSegStart, fadeSegEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = editor.textRender.measureTextWithVisualSpaces(line, afterStart, end, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
        }
    }

    /**
     * Draw text segment with visual spaces
     */
    public float drawTextSegmentWithVisualSpaces(
          Canvas canvas,
          String line,
          int start,
          int end,
          float x,
          float y,
          Paint segmentPaint,
          float alphaMultiplier) {
        if (start >= end) return 0f;

        Paint drawPaint = segmentPaint;
        float drawY = y;
        if (alphaMultiplier < 1f) {
          logCharFadeDraw(line, start, end, alphaMultiplier, x, y, true);
          editor.charAnimation.charAnimTmpPaint.set(segmentPaint);
          int baseAlpha = segmentPaint.getAlpha();
          editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
          drawPaint = editor.charAnimation.charAnimTmpPaint;
          drawY = y + getCharAnimOffsetY(alphaMultiplier, segmentPaint);
        }

        int len = end - start;
        if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < len) {
          editor.view.measureWidthBuffer = new float[len];
        }
        segmentPaint.getTextWidths(line, start, end, editor.view.measureWidthBuffer);

        float currentX = x;
        int runStart = start;
        float runX = currentX;

        for (int i = 0; i < len; i++) {
          int charIndex = start + i;
          char c = line.charAt(charIndex);
          float adv = editor.textRender.getCharAdvanceWidth(c, editor.view.measureWidthBuffer[i], segmentPaint);
          boolean isVirtualSpace = (c == ' ' || c == '\t');
          if (isVirtualSpace) {
            if (runStart < charIndex) {
              canvas.drawText(line, runStart, charIndex, runX, drawY, drawPaint);
            }
            currentX += adv;
            runStart = charIndex + 1;
            runX = currentX;
          } else {
            currentX += adv;
          }
        }

        if (runStart < end) {
          canvas.drawText(line, runStart, end, runX, drawY, drawPaint);
        }
        return currentX - x;
    }

    private float getCharAnimOffsetY(float alpha, Paint paint) {
        return 0f;
    }

    private float drawArabicTextSegmentWithFade(
            Canvas canvas,
            String line,
            int start,
            int end,
            float x,
            float y,
            Paint segmentPaint,
            int fadeStart,
            int fadeEnd,
            float fadeAlpha) {
        if (end <= fadeStart || start >= fadeEnd) {
            if (editor.textRender.getVisualSpaceScale() > 1) {
                logCharDrawTrace(
                        "DRAW_ARABIC_VISUAL_BASE",
                        line,
                        start,
                        end,
                        1f,
                        x,
                        y,
                        fadeStart,
                        fadeEnd,
                        "arabic-no-overlap-visual");
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }
            logCharDrawTrace(
                    "DRAW_ARABIC_BASE_DIRECT",
                    line,
                    start,
                    end,
                    1f,
                    x,
                    y,
                    fadeStart,
                    fadeEnd,
                    "arabic-no-overlap");
            canvas.drawText(line, start, end, x, y, segmentPaint);
            return segmentPaint.measureText(line, start, end);
        }

        if (editor.textRender.getVisualSpaceScale() > 1) {
            float currentX = x;
            int beforeEnd = Math.min(end, fadeStart);
            if (start < beforeEnd) {
                logCharDrawTrace(
                        "DRAW_ARABIC_VISUAL_BASE_BEFORE",
                        line,
                        start,
                        beforeEnd,
                        1f,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "before-anim");
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
            }
            int fadeSegStart = Math.max(start, fadeStart);
            int fadeSegEnd = Math.min(end, fadeEnd);
            if (fadeSegStart < fadeSegEnd) {
                logCharDrawTrace(
                        "DRAW_ARABIC_VISUAL_ANIM",
                        line,
                        fadeSegStart,
                        fadeSegEnd,
                        fadeAlpha,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "animated-visual");
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
            }
            int afterStart = Math.max(start, fadeEnd);
            if (afterStart < end) {
                logCharDrawTrace(
                        "DRAW_ARABIC_VISUAL_BASE_AFTER",
                        line,
                        afterStart,
                        end,
                        1f,
                        currentX,
                        y,
                        fadeStart,
                        fadeEnd,
                        "after-anim");
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, afterStart, end, currentX, y, segmentPaint, 1f);
            }
            return currentX - x;
        }

        float currentX = x;

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            logCharDrawTrace(
                    "DRAW_ARABIC_BASE_BEFORE",
                    line,
                    start,
                    beforeEnd,
                    1f,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "context-before-anim");
            drawTextRunWithContext(canvas, line, start, beforeEnd, start, end, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, start, beforeEnd);
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            float alpha = Math.max(0f, Math.min(1f, fadeAlpha));
            logCharDrawTrace(
                    "DRAW_ARABIC_ANIM",
                    line,
                    fadeSegStart,
                    fadeSegEnd,
                    alpha,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "context-animated-glyph");
            logCharFadeDraw(line, fadeSegStart, fadeSegEnd, alpha, currentX, y, false);
            editor.charAnimation.charAnimTmpPaint.set(segmentPaint);
            int baseAlpha = segmentPaint.getAlpha();
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * alpha));
            drawTextRunWithContext(
                    canvas,
                    line,
                    fadeSegStart,
                    fadeSegEnd,
                    start,
                    end,
                    currentX,
                    y + getCharAnimOffsetY(alpha, segmentPaint),
                    editor.charAnimation.charAnimTmpPaint);
            currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            logCharDrawTrace(
                    "DRAW_ARABIC_BASE_AFTER",
                    line,
                    afterStart,
                    end,
                    1f,
                    currentX,
                    y,
                    fadeStart,
                    fadeEnd,
                    "context-after-anim");
            drawTextRunWithContext(canvas, line, afterStart, end, start, end, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, afterStart, end);
        }

        return currentX - x;
    }

    private void drawTextRunWithContext(
            Canvas canvas,
            String line,
            int start,
            int end,
            int contextStart,
            int contextEnd,
            float x,
            float y,
            Paint paint) {
        canvas.drawTextRun(
                line,
                start,
                end,
                Math.max(0, contextStart),
                Math.min(line.length(), contextEnd),
                x,
                y,
                editor.textRender.isRtl,
                paint);
    }

    private void logCharFadeDraw(
            String line, int start, int end, float alpha, float x, float y, boolean visualSpaces) {
        if (!SodiumEditor.DEBUG_LOGS) return;
        if (charFadeDrawLogCount >= 80) return;
        charFadeDrawLogCount++;
        String text = "";
        if (line != null && start >= 0 && end >= start && end <= line.length()) {
            text = line.substring(start, end).replace("\n", "\\n").replace("\r", "\\r");
            if (text.length() > 24) text = text.substring(0, 24) + "...";
        }
        Log.d(
                TAG,
                "draw fade segment start="
                        + start
                        + " end="
                        + end
                        + " alpha="
                        + alpha
                        + " x="
                        + x
                        + " y="
                        + y
                        + " visualSpaces="
                        + visualSpaces
                        + " text='"
                        + text
                        + "'");
    }

    private void logCharDrawTrace(
            String phase,
            String line,
            int start,
            int end,
            float alpha,
            float x,
            float y,
            int fadeStart,
            int fadeEnd,
            String note) {
        if (!SodiumEditor.DEBUG_LOGS) return;
        if (charDrawTraceLogCount >= MAX_DRAW_TRACE_LOGS) return;
        if (!isTraceRelevant(start, end, fadeStart, fadeEnd, alpha)) return;
        charDrawTraceLogCount++;
        boolean overlapsAnim =
                fadeStart >= 0 && fadeEnd > fadeStart && start < fadeEnd && end > fadeStart;
        Log.d(
                TAG,
                phase
                        + " range="
                        + start
                        + ".."
                        + end
                        + " animRange="
                        + fadeStart
                        + ".."
                        + fadeEnd
                        + " overlapsAnim="
                        + overlapsAnim
                        + " alpha="
                        + alpha
                        + " x="
                        + x
                        + " y="
                        + y
                        + " note="
                        + note
                        + " text='"
                        + summarizeLine(line, start, end)
                        + "'");
    }

    private void logCharDrawTraceAlways(
            String phase,
            String line,
            int start,
            int end,
            float alpha,
            float x,
            float y,
            int fadeStart,
            int fadeEnd,
            String note) {
        if (!SodiumEditor.DEBUG_LOGS) return;
        boolean overlapsAnim =
                fadeStart >= 0 && fadeEnd > fadeStart && start < fadeEnd && end > fadeStart;
        Log.d(
                TAG,
                phase
                        + " range="
                        + start
                        + ".."
                        + end
                        + " animRange="
                        + fadeStart
                        + ".."
                        + fadeEnd
                        + " overlapsAnim="
                        + overlapsAnim
                        + " alpha="
                        + alpha
                        + " x="
                        + x
                        + " y="
                        + y
                        + " note="
                        + note
                        + " text='"
                        + summarizeLine(line, start, end)
                        + "'");
    }

    private boolean isTraceRelevant(int start, int end, int fadeStart, int fadeEnd, float alpha) {
        if (alpha < 1f) return true;
        return fadeStart >= 0 && fadeEnd > fadeStart && start < fadeEnd && end > fadeStart;
    }

    private String summarizeLine(String line, int start, int end) {
        if (line == null) return "null";
        int safeStart = Math.max(0, Math.min(start, line.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, line.length()));
        String text = line.substring(safeStart, safeEnd).replace("\n", "\\n").replace("\r", "\\r");
        return text.length() > 24 ? text.substring(0, 24) + "..." : text;
    }

    /**
     * Draw delete animation for segment
     */
    public void drawDeleteAnimationForSegment(Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        if (!editor.charAnimation.isCharAnimationEnabled) return;
        if (globalLine != editor.charAnimation.delAnimLine
                || editor.charAnimation.delAnimText == null
                || editor.charAnimation.delAnimText.isEmpty()
                || editor.charAnimation.delAnimAlpha <= 0f) return;
        if (line == null) line = "";
        int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
        if (at < segStart || at > segEnd) return;
        float x = editor.textRender.measureTextWithVisualSpaces(line, segStart, at, editor.textRender.paint);
        Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : editor.textRender.paint;
        editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
        editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
        canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
    }
}
