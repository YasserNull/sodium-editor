package com.yn.sodiumeditor.core.selection;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
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
    private static final String TAG = "SodiumSelectionHandles";
    private static final int MAX_HANDLE_POSITION_LOGS = 240;
    public static boolean DEBUG_SELECTION_HANDLE_LOGS = true;

    public final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Selection handle appearance
    public float handleWidth = 40f;
    public float handleHeight = 40f; // Square for perfect circle
    public int handleColor = 0xFF33B5E5;
    public int selectionHandleColor = 0xFF2196F3; // Blue
    public float handleRadius = 6f;

    public float baseHandleWidthPx = handleWidth;
    public float baseHandleHeightPx = handleHeight;
    public float baseHandleRadiusPx = handleRadius;
    public float baseHandleTextSizePx = 0f;

    // Selection handle rects
    public RectF leftHandleRect = new RectF();
    public RectF rightHandleRect = new RectF();

    // Handle dragging state
    public int draggingHandle = 0; // 0 = none, 1 = left, 2 = right
    public boolean lastDragAtLineStart = false;
    public boolean lastDragAtLineEnd = false;
    public float lastHandleScrollX = Float.NaN;
    public float lastHandleScrollY = Float.NaN;

    // Animation delegate
    public final SelectionHandlesAnimation animation;

    private final SodiumEditor editor;
    private final Selection selection;
    private int drawEntryLogCount = 0;
    private int updateEntryLogCount = 0;
    private int handlePositionLogCount = 0;
    private int animationInvalidateLogCount = 0;

    public SelectionHandles(SodiumEditor editor, Selection selection) {
        this.editor = editor;
        this.selection = selection;
        this.animation = new SelectionHandlesAnimation();
    }

    public void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
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
        return animation.getAnimatedHandlePosition(isLeft, targetX, targetY);
    }

    /**
     * Enable or disable handle move animation (delegated).
     */
    public void setHandleMoveAnimationEnabled(boolean enabled) {
        animation.setHandleMoveAnimationEnabled(enabled);
    }

    public void setSelectionAnimationEnabled(boolean enabled) {
        selection.setSelectionAnimationEnabled(enabled);
        setHandleMoveAnimationEnabled(enabled);
    }

  /**
   * Update selection handles positions
   */
  public void updateHandlesPosition() {
    logUpdateEntry("updateHandlesPosition");
    if (!selection.hasSelection) {
      logUpdateEntry("updateHandlesPosition.noSelection");
      return;
    }
    float oldLeft = leftHandleRect.left;
    float oldTop = leftHandleRect.top;
    float oldRight = leftHandleRect.right;
    float oldBottom = leftHandleRect.bottom;
    float oldRLeft = rightHandleRect.left;
    float oldRTop = rightHandleRect.top;
    float oldRRight = rightHandleRect.right;
    float oldRBottom = rightHandleRect.bottom;

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

    float leftTargetY = startY + editor.textRender.lineHeight;
    float rightTargetY = endY + editor.textRender.lineHeight;
    boolean handleDragActive = draggingHandle == 1 || draggingHandle == 2;
    animation.setFastDragAnimationActive(handleDragActive);
    boolean scrollChanged =
        lastHandleScrollX != editor.scroll.scrollX || lastHandleScrollY != editor.scroll.scrollY;
    boolean bypassLeftAnimation = scrollChanged || !handleDragActive;
    boolean bypassRightAnimation = scrollChanged || !handleDragActive;
    if (bypassLeftAnimation) {
      animation.snapHandlePosition(true, startX, leftTargetY);
    }
    if (bypassRightAnimation) {
      animation.snapHandlePosition(false, endX, rightTargetY);
    }
    float[] leftPos =
        bypassLeftAnimation
            ? new float[] {startX, leftTargetY}
            : animation.getAnimatedHandlePosition(true, startX, leftTargetY);
    float[] rightPos =
        bypassRightAnimation
            ? new float[] {endX, rightTargetY}
            : animation.getAnimatedHandlePosition(false, endX, rightTargetY);
    lastHandleScrollX = editor.scroll.scrollX;
    lastHandleScrollY = editor.scroll.scrollY;

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
    logHandlePositionUpdate(
        startLine,
        startChar,
        endLine,
        endChar,
        startX,
        leftTargetY,
        endX,
        rightTargetY,
        leftPos,
        rightPos,
        scrollChanged,
        bypassLeftAnimation,
        bypassRightAnimation,
        oldLeft,
        oldTop,
        oldRight,
        oldBottom,
        oldRLeft,
        oldRTop,
        oldRRight,
        oldRBottom);
  }

  /**
   * Draw selection handles
   */
  public void drawHandles(Canvas canvas) {
    logDrawEntry();
    if (!selection.hasSelection) {
      logUpdateEntry("drawHandles.noSelection");
      return;
    }

    updateHandlesPosition();
    if (animation.isAnimating()) {
        logAnimationInvalidate();
        editor.invalidate();
    }

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(selectionHandleColor);
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha((int) (255f * Math.max(0f, Math.min(1f, selection.state.getHandleAlpha()))));

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
    int lineForVisual = line;
    int visualLine = lineForVisual;
    if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.wordWrap.getVisualIndexForLineAndChar(lineForVisual, 0);
      return (visualLine * editor.textRender.lineHeight) - editor.scroll.scrollY;
    }
    return (lineForVisual * editor.textRender.lineHeight) - editor.scroll.scrollY;
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
    baseHandleWidthPx = width;
    baseHandleHeightPx = height;
    baseHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseHandleTextSizePx);
    editor.invalidate();
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
    baseHandleRadiusPx = radius;
    baseHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseHandleTextSizePx);
    editor.invalidate();
  }

  public void updateHandleMetricsForTextSize(float sizePx) {
    handleWidth = editor.view.scaleByTextSize(baseHandleWidthPx, baseHandleTextSizePx, sizePx);
    handleHeight = editor.view.scaleByTextSize(baseHandleHeightPx, baseHandleTextSizePx, sizePx);
    handleRadius = editor.view.scaleByTextSize(baseHandleRadiusPx, baseHandleTextSizePx, sizePx);
  }

  public RectF getLeftHandleRect() {
    updateHandlesPosition();
    return leftHandleRect;
  }

  public RectF getRightHandleRect() {
    updateHandlesPosition();
    return rightHandleRect;
  }

  private void logHandlePositionUpdate(
      int startLine,
      int startChar,
      int endLine,
      int endChar,
      float leftTargetX,
      float leftTargetY,
      float rightTargetX,
      float rightTargetY,
      float[] leftPos,
      float[] rightPos,
      boolean scrollChanged,
      boolean bypassLeftAnimation,
      boolean bypassRightAnimation,
      float oldLeft,
      float oldTop,
      float oldRight,
      float oldBottom,
      float oldRLeft,
      float oldRTop,
      float oldRRight,
      float oldRBottom) {
    if (!shouldLogSelectionHandles() || handlePositionLogCount >= MAX_HANDLE_POSITION_LOGS) return;
    handlePositionLogCount++;
    Log.d(
        TAG,
        "[SodiumEditor] operation=selectionHandles.update"
            + " count="
            + handlePositionLogCount
            + " selection="
            + startLine
            + ":"
            + startChar
            + ".."
            + endLine
            + ":"
            + endChar
            + " cursor="
            + editor.cursor.cursorLine
            + ":"
            + editor.cursor.cursorChar
            + " scroll="
            + editor.scroll.scrollX
            + ","
            + editor.scroll.scrollY
            + " lastScroll="
            + lastHandleScrollX
            + ","
            + lastHandleScrollY
            + " scrollChanged="
            + scrollChanged
            + " bypassLeft="
            + bypassLeftAnimation
            + " bypassRight="
            + bypassRightAnimation
            + " draggingHandle="
            + draggingHandle
            + " animating="
            + animation.isAnimating()
            + " targetLeft="
            + leftTargetX
            + ","
            + leftTargetY
            + " targetRight="
            + rightTargetX
            + ","
            + rightTargetY
            + " drawLeft="
            + leftPos[0]
            + ","
            + leftPos[1]
            + " drawRight="
            + rightPos[0]
            + ","
            + rightPos[1]
            + " oldLeftRect="
            + oldLeft
            + ","
            + oldTop
            + ","
            + oldRight
            + ","
            + oldBottom
            + " newLeftRect="
            + leftHandleRect.left
            + ","
            + leftHandleRect.top
            + ","
            + leftHandleRect.right
            + ","
            + leftHandleRect.bottom
            + " oldRightRect="
            + oldRLeft
            + ","
            + oldRTop
            + ","
            + oldRRight
            + ","
            + oldRBottom
            + " newRightRect="
            + rightHandleRect.left
            + ","
            + rightHandleRect.top
            + ","
            + rightHandleRect.right
            + ","
            + rightHandleRect.bottom
            + " lineHeight="
            + editor.textRender.lineHeight
            + " stretch="
            + editor.scroll.stretch.stretchX
            + ","
            + editor.scroll.stretch.stretchY
            + " thread="
            + Thread.currentThread().getName());
  }

  private void logAnimationInvalidate() {
    if (!shouldLogSelectionHandles() || animationInvalidateLogCount >= MAX_HANDLE_POSITION_LOGS) return;
    animationInvalidateLogCount++;
    Log.d(
        TAG,
        "[SodiumEditor] operation=selectionHandles.animationInvalidate"
            + " count="
            + animationInvalidateLogCount
            + " scroll="
            + editor.scroll.scrollX
            + ","
            + editor.scroll.scrollY
            + " draggingHandle="
            + draggingHandle
            + " leftRect="
            + leftHandleRect.left
            + ","
            + leftHandleRect.top
            + ","
            + leftHandleRect.right
            + ","
            + leftHandleRect.bottom
            + " rightRect="
            + rightHandleRect.left
            + ","
            + rightHandleRect.top
            + ","
            + rightHandleRect.right
            + ","
            + rightHandleRect.bottom
            + " thread="
            + Thread.currentThread().getName());
  }

  private void logDrawEntry() {
    if (!shouldLogSelectionHandles() || drawEntryLogCount >= MAX_HANDLE_POSITION_LOGS) return;
    drawEntryLogCount++;
    Log.d(
        TAG,
        "[SodiumEditor] operation=selectionHandles.drawEntry"
            + " count="
            + drawEntryLogCount
            + " hasSelection="
            + selection.hasSelection
            + " stateHasSelection="
            + selection.state.hasSelection
            + " scroll="
            + editor.scroll.scrollX
            + ","
            + editor.scroll.scrollY
            + " draggingHandle="
            + draggingHandle
            + " animating="
            + animation.isAnimating()
            + " thread="
            + Thread.currentThread().getName());
  }

  private void logUpdateEntry(String operation) {
    if (!shouldLogSelectionHandles() || updateEntryLogCount >= MAX_HANDLE_POSITION_LOGS) return;
    updateEntryLogCount++;
    Log.d(
        TAG,
        "[SodiumEditor] operation=selectionHandles."
            + operation
            + " count="
            + updateEntryLogCount
            + " hasSelection="
            + selection.hasSelection
            + " stateHasSelection="
            + selection.state.hasSelection
            + " selection="
            + selection.selStartLine
            + ":"
            + selection.selStartChar
            + ".."
            + selection.selEndLine
            + ":"
            + selection.selEndChar
            + " scroll="
            + editor.scroll.scrollX
            + ","
            + editor.scroll.scrollY
            + " lastScroll="
            + lastHandleScrollX
            + ","
            + lastHandleScrollY
            + " draggingHandle="
            + draggingHandle
            + " animating="
            + animation.isAnimating()
            + " thread="
            + Thread.currentThread().getName());
  }

  private boolean shouldLogSelectionHandles() {
    return DEBUG_SELECTION_HANDLE_LOGS || SodiumEditor.DEBUG_LOGS;
  }
}
