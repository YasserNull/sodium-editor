package com.yn.sodiumeditor.core.selection;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * SelectionHandles handles the left and right selection handles for SodiumEditor.
 * This includes:
 * - Selection handle rendering
 * - Selection handle hit detection
 * - Handle position updates
 */
public class SelectionHandles {
public final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Selection handle appearance
  public float handleWidth = 40f;
  public float handleHeight = 50f;
  public int handleColor = 0xFF33B5E5;
  public int selectionHandleColor = 0xFF33B5E5;
  public float handleRadius = 30f;
  
  public float baseHandleRadiusPx = handleRadius;
  public float baseHandleTextSizePx = 0f;
  
  // Selection handle rects
  public RectF leftHandleRect = new RectF();
  public RectF rightHandleRect = new RectF();
  
  // Handle dragging state
  public int draggingHandle = 0; // 0 = none, 1 = left, 2 = right
  public boolean lastDragAtLineStart = false;
  public boolean lastDragAtLineEnd = false;

  public boolean handleMoveAnimationEnabled = true;
  private float animLeftX = Float.NaN;
  private float animLeftY = Float.NaN;
  private float animRightX = Float.NaN;
  private float animRightY = Float.NaN;
  
  private final SodiumEditor editor;
  private final Selection selection;

  public SelectionHandles(SodiumEditor editor, Selection selection) {
    this.editor = editor;
    this.selection = selection;
  }

  public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
    if (!handleMoveAnimationEnabled) {
      return new float[] {targetX, targetY};
    }
    float ax = isLeft ? animLeftX : animRightX;
    float ay = isLeft ? animLeftY : animRightY;
    if (Float.isNaN(ax) || Float.isNaN(ay)) {
      ax = targetX;
      ay = targetY;
    } else {
      float t = 0.35f;
      ax = ax + (targetX - ax) * t;
      ay = ay + (targetY - ay) * t;
    }
    if (isLeft) {
      animLeftX = ax;
      animLeftY = ay;
    } else {
      animRightX = ax;
      animRightY = ay;
    }
    return new float[] {ax, ay};
  }

  public void setHandleMoveAnimationEnabled(boolean enabled) {
    handleMoveAnimationEnabled = enabled;
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
    float leftHandleTop = startY - handleHeight + editor.textRender.lineHeight;
    leftHandleRect.set(
        leftHandleLeft,
        leftHandleTop,
        leftHandleLeft + handleWidth,
        leftHandleTop + handleHeight
    );

    // Update right handle (end)
    float rightHandleLeft = endX - handleWidth / 2;
    float rightHandleTop = endY - handleHeight + editor.textRender.lineHeight;
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
  public float getCharX(int line, int ch) {
    String lineText = editor.getLineTextForRender(line);
    if (lineText == null) return editor.getTextStartX();

    int safeChar = Math.max(0, Math.min(ch, lineText.length()));
    float textX = editor.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);

    return editor.getTextStartX() + textX - editor.scroll.scrollX;
  }

  /**
   * Get line Y position
   */
  public float getLineY(int line) {
    int visualLine = line;
    if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.getVisualIndexForLineAndChar(line, 0);
    }
    return (visualLine * editor.textRender.lineHeight) - editor.scroll.scrollY;
  }

  /**
   * Compare two positions
   */
  public int comparePos(int lineA, int charA, int lineB, int charB) {
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

public void setSelectionHandleColor(int color) {
    selectionHandleColor = color;
    editor.invalidate();
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
