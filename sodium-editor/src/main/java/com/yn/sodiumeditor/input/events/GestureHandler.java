package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles gestures and zooming for SodiumEditor.
 */
public class GestureHandler {
    private final SodiumEditor editor;

    public GestureHandler(SodiumEditor editor) {
        this.editor = editor;
    }

    public void handleActionDown(MotionEvent event) {
        editor.multiTouchActive = false;
        editor.hadMultiTouch = false;
        if (!editor.scroll.scroller.isFinished()) {
            editor.scroll.scroller.abortAnimation();
        }
        editor.scroll.scroller.abortAnimation();
    }

    public void handleActionPointerDown(MotionEvent event) {
        editor.multiTouchActive = true;
        editor.hadMultiTouch = true;
        editor.zoom.mJustFinishedScale = true;
        editor.pointerDown = false;
        editor.movedSinceDown = false;
        editor.selectionHandles.draggingHandle = 0;
        editor.scroll.dragMaxScrollX = -1f;

        if (editor.selection.longPressSelecting) {
            int pointerIndex = event.getActionIndex();
            int id = event.getPointerId(pointerIndex);
            editor.selection.state.longPressEndPointerId = id;
            
            float px = event.getX(pointerIndex);
            float py = event.getY(pointerIndex);
            com.yn.sodiumeditor.io.EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(px, py, null);
            editor.selection.updateLongPressSelection(target.line, target.ch);
            
            editor.selection.selecting = true;
            editor.selection.hasSelection = true;
            editor.invalidate();
        } else {
            editor.selection.selecting = false;
            editor.selection.isLineNumberSelecting = false;
            editor.selection.lineNumberSelectAnchorLine = -1;
        }

        editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);
        if (!editor.scroll.scroller.isFinished()) {
            editor.scroll.scroller.computeScrollOffset();
            editor.scroll.scrollX = editor.scroll.scroller.getCurrX();
            editor.scroll.scrollY = editor.scroll.scroller.getCurrY();
            editor.scroll.scroller.abortAnimation();
        }
        editor.scroll.cancelFlingStopAnimation();
    }

    public void handleActionPointerUp(MotionEvent event) {
        if (editor.selection.longPressSelecting && editor.selection.state.longPressEndPointerId != -1) {
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);
            if (pointerId == editor.selection.state.longPressEndPointerId) {
                editor.selection.state.longPressEndPointerId = -1;
                editor.selection.endLongPressSelection();
                if (editor.selection.hasSelection) {
                    editor.popup.showPopupAtSelection();
                }
            }
        }

        if (event.getPointerCount() - 1 <= 1) {
            editor.multiTouchActive = false;
            editor.zoom.mJustFinishedScale = true;
            editor.scroll.dragMaxScrollX = -1f;
        }
    }

    public boolean processGestures(MotionEvent event) {
        if (editor.zoom.isZoomEnabled) {
            editor.scaleGestureDetector.onTouchEvent(event);
        }

        if (editor.scaleGestureDetector.isInProgress()
            || (editor.multiTouchActive && !editor.selection.longPressSelecting)
            || (event.getPointerCount() > 1 && !editor.selection.longPressSelecting)
            || editor.zoom.isScaling
            || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
            || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            return true;
        }
        return false;
    }
}
