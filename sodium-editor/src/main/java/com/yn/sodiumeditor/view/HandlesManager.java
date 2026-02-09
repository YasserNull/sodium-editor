package com.yn.sodiumeditor.view;

import android.graphics.RectF;

final class HandlesManager {
  static final int HANDLE_NONE = 0;
  static final int HANDLE_LEFT = 1;
  static final int HANDLE_RIGHT = 2;
  static final int HANDLE_CURSOR = 3;

  private final RectF leftHandleRect = new RectF();
  private final RectF rightHandleRect = new RectF();
  private final RectF cursorHandleRect = new RectF();

  private float handleRadius = 30f;
  private float cursorWidth = 6f;
  private float baseHandleRadiusPx = handleRadius;
  private float baseCursorWidthPx = cursorWidth;

  private int cursorAndHandlesColor = 0xFF2196F3;
  private int caretColor = cursorAndHandlesColor;
  private int cursorHandleColor = cursorAndHandlesColor;
  private int selectionHandleColor = cursorAndHandlesColor;
  private int draggingHandle = HANDLE_NONE;

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
    cursorWidth = width;
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
}
