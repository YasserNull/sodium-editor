package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Renderer class for handle functionality.
 * Handles drawing cursor and selection handles on the canvas.
 */
public final class HandleRenderer {

    private final Paint handlePaint;
    private final Path teardropPath;

    private float handleRadius = 30f;
    private float cursorWidth = 6f;
    private float baseHandleRadiusPx = handleRadius;
    private float baseCursorWidthPx = cursorWidth;

    private int cursorAndHandlesColor = 0xFF2196F3;
    private int caretColor = cursorAndHandlesColor;
    private int cursorHandleColor = cursorAndHandlesColor;
    private int selectionHandleColor = cursorAndHandlesColor;

    public HandleRenderer() {
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setStyle(Paint.Style.FILL);
        teardropPath = new Path();
    }

    public void drawCursorHandle(Canvas canvas, float drawX, float drawY, float lineHeight, RectF outRect) {
        handlePaint.setColor(cursorHandleColor);
        drawTeardropHandle(canvas, drawX, drawY + lineHeight);
        outRect.set(
                drawX - handleRadius,
                drawY + lineHeight,
                drawX + handleRadius,
                drawY + lineHeight + handleRadius * 2);
    }

    public void drawSelectionStartHandle(Canvas canvas, float x, float y, boolean isRtl, RectF leftRect, RectF rightRect) {
        handlePaint.setColor(selectionHandleColor);
        drawTeardropHandle(canvas, x, y);
        if (isRtl) {
            rightRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        } else {
            leftRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        }
    }

    public void drawSelectionEndHandle(Canvas canvas, float x, float y, boolean isRtl, RectF leftRect, RectF rightRect) {
        handlePaint.setColor(selectionHandleColor);
        drawTeardropHandle(canvas, x, y);
        if (isRtl) {
            leftRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        } else {
            rightRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        }
    }

    private void drawTeardropHandle(Canvas canvas, float cx, float cy) {
        teardropPath.reset();
        teardropPath.addOval(
                cx - handleRadius, cy, cx + handleRadius, cy + handleRadius * 2, Path.Direction.CW);
        canvas.drawPath(teardropPath, handlePaint);
    }

    public float getHandleRadius() {
        return handleRadius;
    }

    public void setHandleRadius(float radius) {
        handleRadius = radius;
    }

    public float getCursorWidth() {
        return cursorWidth;
    }

    public void setCursorWidth(float width) {
        baseCursorWidthPx = width;
    }

    public float getBaseHandleRadiusPx() {
        return baseHandleRadiusPx;
    }

    public void setBaseHandleRadiusPx(float px) {
        baseHandleRadiusPx = px;
    }

    public float getBaseCursorWidthPx() {
        return baseCursorWidthPx;
    }

    public void setBaseCursorWidthPx(float px) {
        baseCursorWidthPx = px;
    }

    public int getCursorAndHandlesColor() {
        return cursorAndHandlesColor;
    }

    public void setCursorAndHandlesColor(int color) {
        cursorAndHandlesColor = color;
    }

    public int getCaretColor() {
        return caretColor;
    }

    public void setCaretColor(int color) {
        caretColor = color;
    }

    public int getCursorHandleColor() {
        return cursorHandleColor;
    }

    public void setCursorHandleColor(int color) {
        cursorHandleColor = color;
    }

    public int getSelectionHandleColor() {
        return selectionHandleColor;
    }

    public void setSelectionHandleColor(int color) {
        selectionHandleColor = color;
    }
}
