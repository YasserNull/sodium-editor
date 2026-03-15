package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * SelectionHandles handles the left and right selection handles for SodiumEditor.
 * This includes:
 * - Selection handle rendering
 * - Selection handle hit detection
 * - Handle position updates
 */
public class SelectionHandles {

  // Selection handle appearance
  public float handleWidth = 40f;
  public float handleHeight = 50f;
  public int handleColor = 0xFF33B5E5;
  public float handleRadius = 6f;
  
  // Selection handle rects
  public RectF leftHandleRect = new RectF();
  public RectF rightHandleRect = new RectF();
  
  // Handle dragging state
  public int draggingHandle = 0; // 0 = none, 1 = left, 2 = right
  public boolean lastDragAtLineStart = false;
  public boolean lastDragAtLineEnd = false;
  
  private final SodiumEditor sodiumeditor;
  private final Selection selection;

  public SelectionHandles(SodiumEditor sodiumeditor, Selection selection) {
    this.sodiumeditor = sodiumeditor;
    this.selection = selection;
  }

  /**
   * Update selection handles positions
   */
  public void updateHandlesPosition() {
    if (!selection.hasSelection) {
      return;
    }
    
    // Get start and end positions (normalized)
    int startLine, startChar, endLine, endChar;
    if (comparePos(selection.selStartLine, selection.selStartChar, 
                   selection.selEndLine, selection.selEndChar) > 0) {
      startLine = selection.selEndLine;
      startChar = selection.selEndChar;
      endLine = selection.selStartLine;
      endChar = selection.selStartChar;
    } else {
      startLine = selection.selStartLine;
      startChar = selection.selStartChar;
      endLine = selection.selEndLine;
      endChar = selection.selEndChar;
    }
    
    // Calculate start position
    float startX = getCharX(startLine, startChar);
    float startY = getLineY(startLine);
    
    // Calculate end position
    float endX = getCharX(endLine, endChar);
    float endY = getLineY(endLine);
    
    // Update left handle (start)
    float leftHandleLeft = startX - handleWidth / 2;
    float leftHandleTop = startY - handleHeight + sodiumeditor.lineHeight;
    leftHandleRect.set(
        leftHandleLeft,
        leftHandleTop,
        leftHandleLeft + handleWidth,
        leftHandleTop + handleHeight
    );
    
    // Update right handle (end)
    float rightHandleLeft = endX - handleWidth / 2;
    float rightHandleTop = endY - handleHeight + sodiumeditor.lineHeight;
    rightHandleRect.set(
        rightHandleLeft,
        rightHandleTop,
        rightHandleLeft + handleWidth,
        rightHandleTop + handleHeight
    );
  }

  /**
   * Draw selection handles
   */
  public void drawHandles(Canvas canvas) {
    if (!selection.hasSelection) {
      return;
    }
    
    updateHandlesPosition();
    
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(handleColor);
    paint.setStyle(Paint.Style.FILL);
    
    // Draw left handle
    canvas.drawRoundRect(
        leftHandleRect,
        handleRadius,
        handleRadius,
        paint
    );
    
    // Draw right handle
    canvas.drawRoundRect(
        rightHandleRect,
        handleRadius,
        handleRadius,
        paint
    );
  }

  /**
   * Check if point hits left handle
   */
  public boolean hitTestLeft(float x, float y) {
    updateHandlesPosition();
    return leftHandleRect.contains(x, y);
  }

  /**
   * Check if point hits right handle
   */
  public boolean hitTestRight(float x, float y) {
    updateHandlesPosition();
    return rightHandleRect.contains(x, y);
  }

  /**
   * Get character X position
   */
  private float getCharX(int line, int ch) {
    String lineText = sodiumeditor.getLineTextForRender(line);
    if (lineText == null) return sodiumeditor.getTextStartX();
    
    int safeChar = Math.max(0, Math.min(ch, lineText.length()));
    float textX = sodiumeditor.measureTextWithVisualSpaces(lineText, 0, safeChar, sodiumeditor.paint);
    
    return sodiumeditor.getTextStartX() + textX - sodiumeditor.scroll.scrollX;
  }

  /**
   * Get line Y position
   */
  private float getLineY(int line) {
    int visualLine = line;
    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      visualLine = sodiumeditor.getVisualIndexForLineAndChar(line, 0);
    }
    return (visualLine * sodiumeditor.lineHeight) - sodiumeditor.scroll.scrollY;
  }

  /**
   * Compare two positions
   */
  private int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  /**
   * Check if handles should be shown
   */
  public boolean shouldShow() {
    return selection.hasSelection;
  }

  /**
   * Start dragging left handle
   */
  public void startDragLeft() {
    draggingHandle = 1;
  }

  /**
   * Start dragging right handle
   */
  public void startDragRight() {
    draggingHandle = 2;
  }

  /**
   * Stop dragging
   */
  public void stopDrag() {
    draggingHandle = 0;
  }

  /**
   * Check if currently dragging a handle
   */
  public boolean isDragging() {
    return draggingHandle != 0;
  }

  /**
   * Check if dragging left handle
   */
  public boolean isDraggingLeft() {
    return draggingHandle == 1;
  }

  /**
   * Check if dragging right handle
   */
  public boolean isDraggingRight() {
    return draggingHandle == 2;
  }

  // Getters and Setters

  public void setHandleSize(float width, float height) {
    if (width <= 0f || height <= 0f) return;
    handleWidth = width;
    handleHeight = height;
  }

  public void setHandleColor(int color) {
    handleColor = color;
  }

  public void setHandleRadius(float radius) {
    if (radius < 0f) return;
    handleRadius = radius;
  }

  public RectF getLeftHandleRect() {
    updateHandlesPosition();
    return leftHandleRect;
  }

  public RectF getRightHandleRect() {
    updateHandlesPosition();
    return rightHandleRect;
  }
}
