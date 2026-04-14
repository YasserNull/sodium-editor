package com.yn.sodiumeditor.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.SelectionHandlesAnimation;

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
    public float handleHeight = 40f; // Square for perfect circle
    public int handleColor = 0xFF33B5E5;
    public int selectionHandleColor = 0xFF2196F3; // Blue
    public float handleRadius = 6f;

    public float baseHandleRadiusPx = handleRadius;
    public float baseHandleTextSizePx = 0f;

    // Selection handle rects
    public RectF leftHandleRect = new RectF();
    public RectF rightHandleRect = new RectF();

    // Handle dragging state
    public int draggingHandle = 0; // 0 = none, 1 = left, 2 = right
    public boolean lastDragAtLineStart = false;
    public boolean lastDragAtLineEnd = false;

    // Animation delegate
    public final SelectionHandlesAnimation animation;

    private final SodiumEditor editor;
    private final Selection selection;

    public SelectionHandles(SodiumEditor editor, Selection selection) {
        this.editor = editor;
        this.selection = selection;
        this.animation = new SelectionHandlesAnimation();
    }

    /**
     * Get animated handle position (delegated to animation class).
     */
    public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
        return animation.getAnimatedHandlePosition(isLeft, targetX, targetY);
    }

    /**
     * Enable or disable handle move animation (delegated).
     */
    public void setHandleMoveAnimationEnabled(boolean enabled) {
        animation.setHandleMoveAnimationEnabled(enabled);
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
    if (editor.editOperators.comparePos(selection.selStartLine, selection.selStartChar,
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

    // Apply animation if enabled
    float[] leftPos = animation.getAnimatedHandlePosition(true, startX, startY + editor.textRender.lineHeight);
    float[] rightPos = animation.getAnimatedHandlePosition(false, endX, endY + editor.textRender.lineHeight);

    // Update left handle (start) - position below the line
    float leftHandleLeft = leftPos[0] - handleWidth / 2;
    float leftHandleTop = leftPos[1];
    leftHandleRect.set(
        leftHandleLeft,
        leftHandleTop,
        leftHandleLeft + handleWidth,
        leftHandleTop + handleHeight
    );

    // Update right handle (end) - position below the line
    float rightHandleLeft = rightPos[0] - handleWidth / 2;
    float rightHandleTop = rightPos[1];
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
    if (animation.isAnimating()) {
        editor.invalidate();
    }

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(selectionHandleColor);
    paint.setStyle(Paint.Style.FILL);

    // Draw left handle as circle
    float leftCenterX = leftHandleRect.centerX();
    float leftCenterY = leftHandleRect.centerY();
    float leftRadius = Math.min(leftHandleRect.width(), leftHandleRect.height()) / 2f;
    canvas.drawCircle(leftCenterX, leftCenterY, leftRadius, paint);

    // Draw right handle as circle
    float rightCenterX = rightHandleRect.centerX();
    float rightCenterY = rightHandleRect.centerY();
    float rightRadius = Math.min(rightHandleRect.width(), rightHandleRect.height()) / 2f;
    canvas.drawCircle(rightCenterX, rightCenterY, rightRadius, paint);
  }

  /**
   * Check if point hits left handle
   */
  public boolean hitTestLeft(float x, float y) {
    updateHandlesPosition();
    // Expand hit area for easier grabbing
    float expand = 20f;
    RectF hitRect = new RectF(
        leftHandleRect.left - expand,
        leftHandleRect.top - expand,
        leftHandleRect.right + expand,
        leftHandleRect.bottom + expand
    );
    return hitRect.contains(x, y);
  }

  /**
   * Check if point hits right handle
   */
  public boolean hitTestRight(float x, float y) {
    updateHandlesPosition();
    // Expand hit area for easier grabbing
    float expand = 20f;
    RectF hitRect = new RectF(
        rightHandleRect.left - expand,
        rightHandleRect.top - expand,
        rightHandleRect.right + expand,
        rightHandleRect.bottom + expand
    );
    return hitRect.contains(x, y);
  }

  /**
   * Get character X position
   */
  public float getCharX(int line, int ch) {
    String lineText = editor.textRender.getLineTextForRender(line);
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
      visualLine = editor.wordWrap.getVisualIndexForLineAndChar(line, 0);
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
