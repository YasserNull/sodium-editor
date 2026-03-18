package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * CursorHandle handles the cursor handle (draggable handle when cursor is visible without selection).
 * This includes:
 * - Cursor handle rendering
 * - Cursor handle hit detection
 */
public class CursorHandle {

  
  // Cursor handle appearance
  public float cursorHandleWidth = 40f;
  public float cursorHandleHeight = 50f;
  public int cursorHandleColor = 0xFF33B5E5;
  public float cursorHandleRadius = 6f;
  
  // Cursor handle rect
  public RectF cursorHandleRect = new RectF();
  
  private final SodiumEditor sodiumeditor;
  private final Cursor cursor;
  private final Caret caret;

  public CursorHandle(SodiumEditor sodiumeditor, Cursor cursor, Caret caret) {
    this.sodiumeditor = sodiumeditor;
    this.cursor = cursor;
    this.caret = caret;
  }

  /**
   * Update cursor handle position
   */
  public void updateCursorHandlePosition() {
    float caretX = caret.getCaretX();
    float caretY = caret.getCaretY();
    float handleLeft = caretX - cursorHandleWidth / 2;
    float handleTop = caretY - cursorHandleHeight + sodiumeditor.textRender.lineHeight;
    
    cursorHandleRect.set(
        handleLeft,
        handleTop,
        handleLeft + cursorHandleWidth,
        handleTop + cursorHandleHeight
    );
  }

  /**
   * Draw cursor handle
   */
  public void drawCursorHandle(Canvas canvas) {
    if (!sodiumeditor.isFocused() || sodiumeditor.hasSelection()) {
      return;
    }
    
    updateCursorHandlePosition();
    
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(cursorHandleColor);
    paint.setStyle(Paint.Style.FILL);
    
    // Draw handle as rounded rectangle
    canvas.drawRoundRect(
        cursorHandleRect,
        cursorHandleRadius,
        cursorHandleRadius,
        paint
    );
  }

  /**
   * Check if point hits cursor handle
   */
  public boolean hitTest(float x, float y) {
    updateCursorHandlePosition();
    return cursorHandleRect.contains(x, y);
  }

  /**
   * Check if cursor handle should be shown
   */
  public boolean shouldShow() {
    return sodiumeditor.isFocused() && !sodiumeditor.hasSelection();
  }

  // Getters and Setters

  public void setCursorHandleSize(float width, float height) {
    if (width <= 0f || height <= 0f) return;
    cursorHandleWidth = width;
    cursorHandleHeight = height;
  }

  public void setCursorHandleColor(int color) {
    cursorHandleColor = color;
    sodiumeditor.invalidate();
  }

  public void setCursorHandleRadius(float radius) {
    if (radius < 0f) return;
    cursorHandleRadius = radius;
  }

  public RectF getHandleRect() {
    updateCursorHandlePosition();
    return cursorHandleRect;
  }
}
