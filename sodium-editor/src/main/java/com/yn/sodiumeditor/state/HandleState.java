package com.yn.sodiumeditor.state;

import android.graphics.RectF;

/**
 * State class for handle functionality.
 * Stores handle rectangles, dragging state, and drag coordinates.
 */
public final class HandleState {
    public static final int HANDLE_NONE = 0;
    public static final int HANDLE_LEFT = 1;
    public static final int HANDLE_RIGHT = 2;
    public static final int HANDLE_CURSOR = 3;

    public final RectF leftHandleRect = new RectF();
    public final RectF rightHandleRect = new RectF();
    public final RectF cursorHandleRect = new RectF();

    private int draggingHandle = HANDLE_NONE;
    private float lastDragTouchX = 0f;
    private float lastDragTouchY = 0f;
    private boolean lastDragAtLineStart = false;
    private boolean lastDragAtLineEnd = false;

    public HandleState() {}

    public int getDraggingHandle() {
        return draggingHandle;
    }

    public void setDraggingHandle(int handle) {
        draggingHandle = handle;
    }

    public boolean isDragging() {
        return draggingHandle != HANDLE_NONE;
    }

    public float getLastDragTouchX() {
        return lastDragTouchX;
    }

    public void setLastDragTouchX(float x) {
        lastDragTouchX = x;
    }

    public float getLastDragTouchY() {
        return lastDragTouchY;
    }

    public void setLastDragTouchY(float y) {
        lastDragTouchY = y;
    }

    public boolean isLastDragAtLineStart() {
        return lastDragAtLineStart;
    }

    public void setLastDragAtLineStart(boolean atStart) {
        lastDragAtLineStart = atStart;
    }

    public boolean isLastDragAtLineEnd() {
        return lastDragAtLineEnd;
    }

    public void setLastDragAtLineEnd(boolean atEnd) {
        lastDragAtLineEnd = atEnd;
    }

    public RectF getLeftHandleRect() {
        return leftHandleRect;
    }

    public RectF getRightHandleRect() {
        return rightHandleRect;
    }

    public RectF getCursorHandleRect() {
        return cursorHandleRect;
    }

    public void clearLeftHandleRect() {
        leftHandleRect.setEmpty();
    }

    public void clearRightHandleRect() {
        rightHandleRect.setEmpty();
    }

    public void clearCursorHandleRect() {
        cursorHandleRect.setEmpty();
    }

    public void clearAllHandleRects() {
        leftHandleRect.setEmpty();
        rightHandleRect.setEmpty();
        cursorHandleRect.setEmpty();
    }

    public void stopDragging() {
        draggingHandle = HANDLE_NONE;
    }
}
