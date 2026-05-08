package com.yn.sodiumeditor.core.cursor; 
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.utils.FunctionLog;

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
    FunctionLog.f("CursorHandle", "CursorHandle", editor, cursor, caret);
    this.editor = editor;
    this.cursor = cursor;
    this.caret = caret;
  }

  /**
   * Update cursor handle position
   */
  public void updateCursorHandlePosition() {
    FunctionLog.f("CursorHandle", "updateCursorHandlePosition");
    float docX, docY;
    // Only follow cursorAnimation while cursor animation is actually enabled.
    // Otherwise stale cached draw coordinates can lag behind the real caret position.
    boolean draggingCursorHandle = editor.selectionHandles.draggingHandle == 3;
    boolean zoomOrScaleTransition =
        editor.zoom.isScaling
            || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())
            || editor.onTouch.multiTouchActive
            || editor.zoom.mJustFinishedScale;
    if (!draggingCursorHandle
        && !zoomOrScaleTransition
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
    Log.i(
        "CursorDbg",
        "update"
            + " docX="
            + docX
            + " docY="
            + docY
            + " screenX="
            + x
            + " screenY="
            + y
            + " rect="
            + cursorHandleRect.left
            + ","
            + cursorHandleRect.top
            + ","
            + cursorHandleRect.right
            + ","
            + cursorHandleRect.bottom
            + " animValid="
            + editor.cursorAnimation.cursorAnimValid
            + " animRunning="
            + editor.cursorAnimation.cursorAnimRunning
            + " draggingCursorHandle="
            + draggingCursorHandle
            + " zoomOrScaleTransition="
            + zoomOrScaleTransition);
  }

  /**
   * Draw cursor handle
   */
  public void drawCursorHandle(Canvas canvas) {
    FunctionLog.f("CursorHandle", "drawCursorHandle", canvas);
    if (!editor.isFocused() || editor.selection.hasSelection) {
      return;
    }

    updateCursorHandlePosition();
    Log.i(
        "CursorDbg",
        "draw"
            + " rect="
            + cursorHandleRect.left
            + ","
            + cursorHandleRect.top
            + ","
            + cursorHandleRect.right
            + ","
            + cursorHandleRect.bottom
            + " cursorLine="
            + cursor.cursorLine
            + " cursorChar="
            + cursor.cursorChar);

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
    FunctionLog.f("CursorHandle", "hitTest", x, y);
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
    FunctionLog.f("CursorHandle", "shouldShow");
    return editor.isFocused() && !editor.selection.hasSelection;
  }

  // Getters and Setters

  public void setCursorHandleSize(float width, float height) {
    FunctionLog.f("CursorHandle", "setCursorHandleSize", width, height);
    if (width <= 0f || height <= 0f) return;
    cursorHandleWidth = width;
    cursorHandleHeight = height;
  }

  public void setCursorHandleColor(int color) {
    FunctionLog.f("CursorHandle", "setCursorHandleColor", color);
    cursorHandleColor = color;
    editor.invalidate();
  }

  public void setCursorHandleRadius(float radius) {
    FunctionLog.f("CursorHandle", "setCursorHandleRadius", radius);
    if (radius < 0f) return;
    cursorHandleRadius = radius;
  }

  public RectF getHandleRect() {
    FunctionLog.f("CursorHandle", "getHandleRect");
    updateCursorHandlePosition();
    return cursorHandleRect;
  }
}
