package com.yn.sodiumeditor.core.cursor; 
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
  public float baseCursorHandleWidthPx = cursorHandleWidth;
  public float baseCursorHandleHeightPx = cursorHandleHeight;
  public float baseCursorHandleRadiusPx = cursorHandleRadius;
  public float baseCursorHandleTextSizePx = 0f;
  
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
    float docX, docY;
    // Only follow cursorAnimation while cursor animation is actually enabled.
    // Otherwise stale cached draw coordinates can lag behind the real caret position.
    boolean zoomOrScaleTransition =
        editor.zoom.isScaling
            || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())
            || editor.onTouch.multiTouchActive
            || editor.zoom.mJustFinishedScale;
    if (!zoomOrScaleTransition
        && editor.cursorAnimation.isCursorAnimationEnabled
        && editor.cursorAnimation.cursorAnimValid
        && !Float.isNaN(editor.cursorAnimation.cursorDrawX)) {
      docX = editor.cursorAnimation.cursorDrawX;
      docY = editor.cursorAnimation.cursorDrawY;
    } else {
      docX = caret.getCaretDocumentX();
      docY = caret.getCaretDocumentY();
    }
    
    // Convert Document coordinates to Screen coordinates
    float x = editor.layout.getTextStartX() + docX - editor.scroll.scrollX;
    float y = docY - editor.scroll.scrollY;
    
    float lineHeight = editor.textRender.lineHeight;
    
    // Position handle below the cursor line, centered on x
    float handleLeft = x - cursorHandleWidth / 2;
    float handleTop = y + lineHeight;

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
    baseCursorHandleWidthPx = width;
    baseCursorHandleHeightPx = height;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public void setCursorHandleColor(int color) {
    cursorHandleColor = color;
    editor.invalidate();
  }

  public void setCursorHandleRadius(float radius) {
    if (radius < 0f) return;
    baseCursorHandleRadiusPx = radius;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public void updateHandleMetricsForTextSize(float sizePx) {
    cursorHandleWidth =
        editor.view.scaleByTextSize(
            baseCursorHandleWidthPx, baseCursorHandleTextSizePx, sizePx);
    cursorHandleHeight =
        editor.view.scaleByTextSize(
            baseCursorHandleHeightPx, baseCursorHandleTextSizePx, sizePx);
    cursorHandleRadius =
        editor.view.scaleByTextSize(
            baseCursorHandleRadiusPx, baseCursorHandleTextSizePx, sizePx);
  }

  public RectF getHandleRect() {
    updateCursorHandlePosition();
    return cursorHandleRect;
  }
}
