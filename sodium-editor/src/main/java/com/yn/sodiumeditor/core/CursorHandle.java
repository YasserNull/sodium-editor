package com.yn.sodiumeditor.core; 
import com.yn.sodiumeditor.SodiumEditor;
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
  public float cursorHandleHeight = 40f; // Square for perfect circle
  public int cursorHandleColor = 0xFF2196F3; // Blue
  public float cursorHandleRadius = 6f;
  
  // Cursor handle rect
  public RectF cursorHandleRect = new RectF();
  
  private final SodiumEditor editor;
  private final Cursor cursor;
  private final Caret caret;

  public CursorHandle(SodiumEditor editor, Cursor cursor, Caret caret) {
    this.editor = editor;
    this.cursor = cursor;
    this.caret = caret;
  }

  /**
   * Update cursor handle position
   */
  public void updateCursorHandlePosition() {
    float caretX = caret.getCaretX();
    float caretY = caret.getCaretY();
    float lineHeight = editor.textRender.lineHeight;
    
    // Position handle below the cursor line, centered on caret X
    float handleLeft = caretX - cursorHandleWidth / 2;
    float handleTop = caretY + lineHeight;

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
    if (!editor.isFocused() || editor.selection.hasSelection) {
      return;
    }

    updateCursorHandlePosition();

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(cursorHandleColor);
    paint.setStyle(Paint.Style.FILL);

    // Draw handle as circle
    float centerX = cursorHandleRect.centerX();
    float centerY = cursorHandleRect.centerY();
    float radius = Math.min(cursorHandleRect.width(), cursorHandleRect.height()) / 2f;
    canvas.drawCircle(centerX, centerY, radius, paint);
  }

  /**
   * Check if point hits cursor handle
   */
  public boolean hitTest(float x, float y) {
    updateCursorHandlePosition();
    // Expand hit area for easier grabbing
    float expand = 20f;
    RectF hitRect = new RectF(
        cursorHandleRect.left - expand,
        cursorHandleRect.top - expand,
        cursorHandleRect.right + expand,
        cursorHandleRect.bottom + expand
    );
    return hitRect.contains(x, y);
  }

  /**
   * Check if cursor handle should be shown
   */
  public boolean shouldShow() {
    return editor.isFocused() && !editor.selection.hasSelection;
  }

  // Getters and Setters

  public void setCursorHandleSize(float width, float height) {
    if (width <= 0f || height <= 0f) return;
    cursorHandleWidth = width;
    cursorHandleHeight = height;
  }

  public void setCursorHandleColor(int color) {
    cursorHandleColor = color;
    editor.invalidate();
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
