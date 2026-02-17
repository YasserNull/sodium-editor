package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class HandlesManager {
  static final int HANDLE_NONE = 0;
  static final int HANDLE_LEFT = 1;
  static final int HANDLE_RIGHT = 2;
  static final int HANDLE_CURSOR = 3;

  private final RectF leftHandleRect = new RectF();
  private final RectF rightHandleRect = new RectF();
  private final RectF cursorHandleRect = new RectF();
  private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path teardropPath = new Path();
  private final SodiumEditorView view;

  private float handleRadius = 30f;
  private float cursorWidth = 6f;
  private float baseHandleRadiusPx = handleRadius;
  private float baseCursorWidthPx = cursorWidth;
  private float baseHandleTextSizePx = 0f;

  private int cursorAndHandlesColor = 0xFF2196F3;
  private int caretColor = cursorAndHandlesColor;
  private int cursorHandleColor = cursorAndHandlesColor;
  private int selectionHandleColor = cursorAndHandlesColor;
  private int draggingHandle = HANDLE_NONE;
  private float lastDragTouchX = 0f;
  private float lastDragTouchY = 0f;
  private boolean lastDragAtLineStart = false;
  private boolean lastDragAtLineEnd = false;

  private final Runnable autoScrollRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!isDragging()) return;
          if (view.scrollManager.autoScrollX != 0 || view.scrollManager.autoScrollY != 0) {
            view.scrollManager.scrollX += view.scrollManager.autoScrollX;
            float nextY = view.scrollManager.scrollY + view.scrollManager.autoScrollY;
            if (!view.isIndexReady && !view.isEof && view.isWindowLoading) {
              float effectiveHeight =
                  (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
              float winTop = view.windowStartLine * view.lineHeight;
              float winBottom = (view.windowStartLine + view.linesWindow.size()) * view.lineHeight;
              float maxY = Math.max(0f, winBottom - effectiveHeight);
              if (view.scrollManager.autoScrollY > 0 && nextY > maxY) nextY = maxY;
              if (view.scrollManager.autoScrollY < 0 && nextY < winTop) nextY = winTop;
            }
            view.scrollManager.scrollY = nextY;
            view.scrollManager.clampScrollX();
            view.clampScrollY();
            updateHandlePosition(lastDragTouchX, lastDragTouchY);
            int handle = getDraggingHandle();
            if (handle == HANDLE_LEFT || handle == HANDLE_RIGHT) {
              view.popupMenuManager.showPopupAtSelection();
            }
            view.checkAndLoadWindow();
            view.invalidate();
            view.mainHandler.postDelayed(this, 16);
          }
        }
      };

  HandlesManager(SodiumEditorView view) {
    this.view = view;
    handlePaint.setStyle(Paint.Style.FILL);
  }

  void initBaseHandleTextSize(float textSizePx) {
    baseHandleTextSizePx = textSizePx;
  }

  float getBaseHandleTextSizePx() {
    return baseHandleTextSizePx;
  }

  RectF getLeftHandleRect() {
    return leftHandleRect;
  }

  RectF getRightHandleRect() {
    return rightHandleRect;
  }

  RectF getCursorHandleRect() {
    return cursorHandleRect;
  }

  float getHandleRadius() {
    return handleRadius;
  }

  void setHandleRadius(float radius) {
    handleRadius = radius;
  }

  float getCursorWidth() {
    return cursorWidth;
  }

  void setCursorWidth(float width) {
    baseCursorWidthPx = width;
    view.invalidate();
  }

  float getBaseHandleRadiusPx() {
    return baseHandleRadiusPx;
  }

  void setBaseHandleRadiusPx(float px) {
    baseHandleRadiusPx = px;
  }

  float getBaseCursorWidthPx() {
    return baseCursorWidthPx;
  }

  void setBaseCursorWidthPx(float px) {
    baseCursorWidthPx = px;
  }

  int getCursorAndHandlesColor() {
    return cursorAndHandlesColor;
  }

  void setCursorAndHandlesColor(int color) {
    cursorAndHandlesColor = color;
  }

  int getCaretColor() {
    return caretColor;
  }

  void setCaretColor(int color) {
    caretColor = color;
  }

  int getCursorHandleColor() {
    return cursorHandleColor;
  }

  void setCursorHandleColor(int color) {
    cursorHandleColor = color;
  }

  int getSelectionHandleColor() {
    return selectionHandleColor;
  }

  void setSelectionHandleColor(int color) {
    selectionHandleColor = color;
  }

  int getDraggingHandle() {
    return draggingHandle;
  }

  void setDraggingHandle(int handle) {
    draggingHandle = handle;
  }

  boolean isDragging() {
    return draggingHandle != HANDLE_NONE;
  }

  void clearCursorHandleRect() {
    cursorHandleRect.setEmpty();
  }

  void clearLeftHandleRect() {
    leftHandleRect.setEmpty();
  }

  void clearRightHandleRect() {
    rightHandleRect.setEmpty();
  }

  void drawCursorHandle(Canvas canvas, float drawX, float drawY, float lineHeight) {
    handlePaint.setColor(cursorHandleColor);
    drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
    cursorHandleRect.set(
        drawX - handleRadius,
        drawY + lineHeight,
        drawX + handleRadius,
        drawY + lineHeight + handleRadius * 2);
  }

  void drawSelectionStartHandle(Canvas canvas, float x, float y, boolean isRtl) {
    handlePaint.setColor(selectionHandleColor);
    drawTeardropHandle(canvas, x, y, handlePaint);
    if (isRtl) {
      rightHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
    } else {
      leftHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
    }
  }

  void drawSelectionEndHandle(Canvas canvas, float x, float y, boolean isRtl) {
    handlePaint.setColor(selectionHandleColor);
    drawTeardropHandle(canvas, x, y, handlePaint);
    if (isRtl) {
      leftHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
    } else {
      rightHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
    }
  }

  int hitTestHandle(float gx, float gy, boolean hasSelection, boolean focused) {
    if (hasSelection && leftHandleRect.contains(gx, gy)) return HANDLE_LEFT;
    if (hasSelection && rightHandleRect.contains(gx, gy)) return HANDLE_RIGHT;
    if (focused && !hasSelection && cursorHandleRect.contains(gx, gy)) return HANDLE_CURSOR;
    return HANDLE_NONE;
  }

  void stopDragging() {
    draggingHandle = HANDLE_NONE;
    view.scrollManager.autoScrollX = 0;
    view.scrollManager.autoScrollY = 0;
    view.mainHandler.removeCallbacks(autoScrollRunnable);
  }

  void stopAutoScroll() {
    view.scrollManager.autoScrollX = 0;
    view.scrollManager.autoScrollY = 0;
    view.mainHandler.removeCallbacks(autoScrollRunnable);
  }

  void handleDrag(float touchX, float touchY) {
    updateHandlePosition(touchX, touchY);
    int handle = getDraggingHandle();
    if (handle == HANDLE_LEFT || handle == HANDLE_RIGHT) {
      view.popupMenuManager.showPopupAtSelection();
    }

    float scrollMargin = view.lineHeight * 2f;
    float scrollSpeed = Math.max(4f, view.lineHeight * 0.35f);
    view.scrollManager.autoScrollY = 0;
    view.scrollManager.autoScrollX = 0;
    if (touchY < scrollMargin) view.scrollManager.autoScrollY = -scrollSpeed;
    else if (touchY > (view.getHeight() - view.keyboardHeight) - scrollMargin) {
      view.scrollManager.autoScrollY = scrollSpeed;
    }
    if (touchX < scrollMargin) view.scrollManager.autoScrollX = -scrollSpeed;
    else if (touchX > view.getWidth() - scrollMargin) view.scrollManager.autoScrollX = scrollSpeed;
    if (view.isRtl && !view.wordWrapManager.isWordWrapEnabled) view.scrollManager.autoScrollX = -view.scrollManager.autoScrollX;

    // Prevent horizontal auto-scroll when the handle is already at the line boundary.
    if (view.scrollManager.autoScrollX > 0 && lastDragAtLineEnd) view.scrollManager.autoScrollX = 0;
    if (view.scrollManager.autoScrollX < 0 && lastDragAtLineStart) view.scrollManager.autoScrollX = 0;

    if (view.scrollManager.autoScrollX != 0 || view.scrollManager.autoScrollY != 0) {
      view.mainHandler.post(autoScrollRunnable);
    } else {
      view.mainHandler.removeCallbacks(autoScrollRunnable);
    }

    view.invalidate();
  }

  void onTouchMove(float x, float y) {
    lastDragTouchX = x;
    lastDragTouchY = y;
  }

  private void updateHandlePosition(float touchX, float touchY) {
    // FIX: Any manual adjustment of the selection handles must deactivate ALL "Select All" flags.
    // This prevents the editor from deleting all content when the user has reduced the selection.
    if (view.selectionManager.isSelectAllActive() || view.selectionManager.isEntireFileSelected()) {
      view.selectionManager.setSelectAllState(false, false);
      // The popup needs to be redrawn as "Copy" and "Cut" might become available again.
      view.popupMenuManager.showPopupAtSelection();
    }

    // Correctly calculate X coordinate relative to the text area, accounting for the gutter.
    SodiumEditorView.CursorTarget target = view.getCursorTargetForHandles(touchX, touchY);
    int line = target.line;

    if (view.isEof) {
      int lastValidLine = view.windowStartLine + view.linesWindow.size() - 1;
      if (line > lastValidLine) line = lastValidLine;
    }

    view.scrollManager.ensureLineInWindow(line, true);
    String ln = view.getLineTextForRender(line);
    int clamped = Math.max(0, Math.min(target.ch, ln.length()));
    lastDragAtLineStart = clamped == 0;
    lastDragAtLineEnd = clamped == ln.length();

    int handle = getDraggingHandle();
    if (handle == HANDLE_LEFT) {
      if (view.isRtl) {
        view.selectionManager.selEndLine = line;
        view.selectionManager.selEndChar = clamped;
      } else {
        view.selectionManager.selStartLine = line;
        view.selectionManager.selStartChar = clamped;
      }
    } else if (handle == HANDLE_RIGHT) {
      if (view.isRtl) {
        view.selectionManager.selStartLine = line;
        view.selectionManager.selStartChar = clamped;
      } else {
        view.selectionManager.selEndLine = line;
        view.selectionManager.selEndChar = clamped;
      }
    } else if (handle == HANDLE_CURSOR) {
      view.cursorManager.setLineAndChar(line, clamped);
      view.scrollManager.keepCursorVisibleHorizontally();
    }
  }

  private void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
    Paint.Style prevStyle = paint.getStyle();
    float prevStroke = paint.getStrokeWidth();
    Paint.Cap prevCap = paint.getStrokeCap();

    paint.setStyle(Paint.Style.FILL);
    teardropPath.reset();
    teardropPath.addOval(
        cx - handleRadius, cy, cx + handleRadius, cy + handleRadius * 2, Path.Direction.CW);
    canvas.drawPath(teardropPath, paint);

    paint.setStyle(prevStyle);
    paint.setStrokeWidth(prevStroke);
    paint.setStrokeCap(prevCap);
  }
}
