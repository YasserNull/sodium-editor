package com.yn.sodiumeditor.core.selection;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.SelectionHandlesAnimation;
import com.yn.sodiumeditor.utils.FunctionLog;

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
        FunctionLog.f("SelectionHandles", "SelectionHandles", editor, selection);
        this.editor = editor;
        this.selection = selection;
        this.animation = new SelectionHandlesAnimation();
    }

    public void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
        FunctionLog.f("SelectionHandles", "drawTeardropHandle", canvas, cx, cy, paint);
        Paint.Style prevStyle = paint.getStyle();
        int prevColor = paint.getColor();
        float prevStroke = paint.getStrokeWidth();
        Paint.Cap prevCap = paint.getStrokeCap();

        paint.setStyle(Paint.Style.FILL);
        editor.view.teardropPath.reset();
        editor.view.teardropPath.addOval(
                cx - handleRadius,
                cy,
                cx + handleRadius,
                cy + handleRadius * 2,
                Path.Direction.CW);
        canvas.drawPath(editor.view.teardropPath, paint);

        paint.setStyle(prevStyle);
        paint.setColor(prevColor);
        paint.setStrokeWidth(prevStroke);
        paint.setStrokeCap(prevCap);
    }

    /**
     * Get animated handle position (delegated to animation class).
     */
    public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
        FunctionLog.f("SelectionHandles", "getAnimatedHandlePosition", isLeft, targetX, targetY);
        return animation.getAnimatedHandlePosition(isLeft, targetX, targetY);
    }

    /**
     * Enable or disable handle move animation (delegated).
     */
    public void setHandleMoveAnimationEnabled(boolean enabled) {
        FunctionLog.f("SelectionHandles", "setHandleMoveAnimationEnabled", enabled);
        animation.setHandleMoveAnimationEnabled(enabled);
    }

    public void setSelectionAnimationEnabled(boolean enabled) {
        FunctionLog.f("SelectionHandles", "setSelectionAnimationEnabled", enabled);
        selection.setSelectionAnimationEnabled(enabled);
        setHandleMoveAnimationEnabled(enabled);
    }

  /**
   * Update selection handles positions
   */
  public void updateHandlesPosition() {
    FunctionLog.f("SelectionHandles", "updateHandlesPosition");
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
    FunctionLog.f("SelectionHandles", "drawHandles", canvas);
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
    FunctionLog.f("SelectionHandles", "hitTestLeft", x, y);
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
    FunctionLog.f("SelectionHandles", "hitTestRight", x, y);
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
    FunctionLog.f("SelectionHandles", "getCharX", line, ch);
    String lineText = editor.windowRender.getLineTextForRender(line);
    if (lineText == null) return editor.layout.getTextStartX();

    int safeChar = Math.max(0, Math.min(ch, lineText.length()));
    float textX = editor.textRender.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);

    return editor.layout.getTextStartX() + textX - editor.scroll.scrollX;
  }

  /**
   * Get line Y position
   */
  public float getLineY(int line) {
    FunctionLog.f("SelectionHandles", "getLineY", line);
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
    FunctionLog.f("SelectionHandles", "comparePos", lineA, charA, lineB, charB);
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  /**
   * Check if handles should be shown
   */
  public boolean shouldShow() {
    FunctionLog.f("SelectionHandles", "shouldShow");
    return selection.hasSelection;
  }

  /**
   * Start dragging left handle
   */
  public void startDragLeft() {
    FunctionLog.f("SelectionHandles", "startDragLeft");
    draggingHandle = 1;
  }

  /**
   * Start dragging right handle
   */
  public void startDragRight() {
    FunctionLog.f("SelectionHandles", "startDragRight");
    draggingHandle = 2;
  }

  /**
   * Stop dragging
   */
  public void stopDrag() {
    FunctionLog.f("SelectionHandles", "stopDrag");
    draggingHandle = 0;
  }

  /**
   * Check if currently dragging a handle
   */
  public boolean isDragging() {
    FunctionLog.f("SelectionHandles", "isDragging");
    return draggingHandle != 0;
  }

  /**
   * Check if dragging left handle
   */
  public boolean isDraggingLeft() {
    FunctionLog.f("SelectionHandles", "isDraggingLeft");
    return draggingHandle == 1;
  }

  /**
   * Check if dragging right handle
   */
  public boolean isDraggingRight() {
    FunctionLog.f("SelectionHandles", "isDraggingRight");
    return draggingHandle == 2;
  }

  // Getters and Setters

  public void setHandleSize(float width, float height) {
    FunctionLog.f("SelectionHandles", "setHandleSize", width, height);
    if (width <= 0f || height <= 0f) return;
    handleWidth = width;
    handleHeight = height;
  }

  public void setHandleColor(int color) {
    FunctionLog.f("SelectionHandles", "setHandleColor", color);
    handleColor = color;
  }

public void setSelectionHandleColor(int color) {
    FunctionLog.f("SelectionHandles", "setSelectionHandleColor", color);
    selectionHandleColor = color;
    editor.invalidate();
  }
  public void setHandleRadius(float radius) {
    FunctionLog.f("SelectionHandles", "setHandleRadius", radius);
    if (radius < 0f) return;
    handleRadius = radius;
  }

  public RectF getLeftHandleRect() {
    FunctionLog.f("SelectionHandles", "getLeftHandleRect");
    updateHandlesPosition();
    return leftHandleRect;
  }

  public RectF getRightHandleRect() {
    FunctionLog.f("SelectionHandles", "getRightHandleRect");
    updateHandlesPosition();
    return rightHandleRect;
  }
}
