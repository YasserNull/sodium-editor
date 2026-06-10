package com.yn.sodiumeditor.core.cursor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * CursorHandle handles the cursor handle (draggable handle when cursor is visible without
 * selection). This includes: - Cursor handle rendering - Cursor handle hit detection
 */
public class CursorHandle {

  // Cursor handle appearance
  public float cursorHandleWidth = 40f;
  public float cursorHandleHeight = 40f; // Square for perfect circle
  public int cursorHandleColor = 0xFF2196F3; // Blue
  public float cursorHandleRadius = 16f;
  public float baseCursorHandleWidthPx = cursorHandleWidth;
  public float baseCursorHandleHeightPx = cursorHandleHeight;
  public float baseCursorHandleRadiusPx = cursorHandleRadius;
  public float baseCursorHandleTextSizePx = 0f;
  public boolean cursorHandleEnabled = true;
  public boolean hideWhileTypingEnabled = true;

  // Cursor handle rect
  public RectF cursorHandleRect = new RectF();

  private final SodiumEditor editor;
  private final Cursor cursor;
  private final Caret caret;
  private boolean hiddenByTyping = false;

  public CursorHandle(SodiumEditor editor, Cursor cursor, Caret caret) {
    this.editor = editor;
    this.cursor = cursor;
    this.caret = caret;
  }

  /** Update cursor handle position */
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
        handleLeft, handleTop, handleLeft + cursorHandleWidth, handleTop + cursorHandleHeight);
  }

  /** Draw cursor handle */
  public void drawCursorHandle(Canvas canvas) {
    if (!shouldShow()) {
      return;
    }

    updateCursorHandlePosition();

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(cursorHandleColor);
    paint.setStyle(Paint.Style.FILL);

    canvas.drawRoundRect(cursorHandleRect, cursorHandleRadius, cursorHandleRadius, paint);
  }

  /** Check if point hits cursor handle */
  public boolean hitTest(float x, float y) {
    if (!shouldShow()) return false;
    updateCursorHandlePosition();
    // Expand hit area for easier grabbing
    float expand = 20f;
    RectF hitRect =
        new RectF(
            cursorHandleRect.left - expand,
            cursorHandleRect.top - expand,
            cursorHandleRect.right + expand,
            cursorHandleRect.bottom + expand);
    return hitRect.contains(x, y);
  }

  /** Check if cursor handle should be shown */
  public boolean shouldShow() {
    return cursorHandleEnabled
        && editor.isFocused()
        && !editor.selection.hasSelection
        && !hiddenByTyping;
  }

  // Getters and Setters

  public void setCursorHandleEnabled(boolean enabled) {
    if (cursorHandleEnabled == enabled) return;
    cursorHandleEnabled = enabled;
    if (!enabled) hiddenByTyping = false;
    editor.invalidate();
  }

  public boolean isCursorHandleEnabled() {
    return cursorHandleEnabled;
  }


  public void setHideWhileTypingEnabled(boolean enabled) {
    if (hideWhileTypingEnabled == enabled) return;
    hideWhileTypingEnabled = enabled;
    if (!enabled) hiddenByTyping = false;
    editor.invalidate();
  }

  public boolean isHideWhileTypingEnabled() {
    return hideWhileTypingEnabled;
  }

  public void hideForTyping() {
    if (!cursorHandleEnabled || !hideWhileTypingEnabled || hiddenByTyping) return;
    hiddenByTyping = true;
    editor.invalidate();
  }

  public void showAfterCursorPlacement() {
    if (!hiddenByTyping) return;
    hiddenByTyping = false;
    editor.invalidate();
  }

  public boolean isHiddenByTyping() {
    return hiddenByTyping;
  }

  public void setCursorHandleSize(float width, float height) {
    if (width <= 0f || height <= 0f) return;
    baseCursorHandleWidthPx = width;
    baseCursorHandleHeightPx = height;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public void setCursorHandleWidth(float width) {
    if (width <= 0f) return;
    if (baseCursorHandleWidthPx == width
        && baseCursorHandleTextSizePx == editor.textRender.paint.getTextSize()) return;
    baseCursorHandleWidthPx = width;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public float getCursorHandleWidth() {
    return cursorHandleWidth;
  }

  public void setCursorHandleHeight(float height) {
    if (height <= 0f) return;
    if (baseCursorHandleHeightPx == height
        && baseCursorHandleTextSizePx == editor.textRender.paint.getTextSize()) return;
    baseCursorHandleHeightPx = height;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public float getCursorHandleHeight() {
    return cursorHandleHeight;
  }

  public void setCursorHandleColor(int color) {
    if (cursorHandleColor == color) return;
    cursorHandleColor = color;
    editor.invalidate();
  }

  public int getCursorHandleColor() {
    return cursorHandleColor;
  }

  public void setCursorHandleRadius(float radius) {
    if (radius < 0f) return;
    if (baseCursorHandleRadiusPx == radius
        && baseCursorHandleTextSizePx == editor.textRender.paint.getTextSize()) return;
    baseCursorHandleRadiusPx = radius;
    baseCursorHandleTextSizePx = editor.textRender.paint.getTextSize();
    updateHandleMetricsForTextSize(baseCursorHandleTextSizePx);
    editor.invalidate();
  }

  public float getCursorHandleRadius() {
    return cursorHandleRadius;
  }

  public void updateHandleMetricsForTextSize(float sizePx) {
    cursorHandleWidth =
        editor.view.scaleByTextSize(baseCursorHandleWidthPx, baseCursorHandleTextSizePx, sizePx);
    cursorHandleHeight =
        editor.view.scaleByTextSize(baseCursorHandleHeightPx, baseCursorHandleTextSizePx, sizePx);
    cursorHandleRadius =
        editor.view.scaleByTextSize(baseCursorHandleRadiusPx, baseCursorHandleTextSizePx, sizePx);
  }

  public RectF getHandleRect() {
    updateCursorHandlePosition();
    return cursorHandleRect;
  }
}
