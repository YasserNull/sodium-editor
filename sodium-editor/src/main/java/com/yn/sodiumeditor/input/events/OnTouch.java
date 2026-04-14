package com.yn.sodiumeditor.input.events;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.CodeFold;
import com.yn.sodiumeditor.core.Popup;

/**
 * OnTouch handles all touch event logic for SodiumEditor by delegating to specialized handlers.
 */
public class OnTouch {

  private final SodiumEditor editor;
  private final ScrollBarHandler scrollBarHandler;
  private final PopupInteractionHandler popupHandler;
  private final DragSelectionHandler dragSelectionHandler;
  private final GestureHandler gestureHandler;

  public OnTouch(SodiumEditor editor) {
    this.editor = editor;
    this.scrollBarHandler = new ScrollBarHandler(editor);
    this.popupHandler = new PopupInteractionHandler(editor);
    this.dragSelectionHandler = new DragSelectionHandler(editor);
    this.gestureHandler = new GestureHandler(editor);
  }

  public void updateHandlePosition(float touchX, float touchY) {
    dragSelectionHandler.updateHandlePosition(touchX, touchY);
  }

  public boolean onTouchEvent(MotionEvent event) {
    if (editor.isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    // 1. Initial Gesture Handling
    if (action == MotionEvent.ACTION_DOWN) {
      gestureHandler.handleActionDown(event);
    } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
      gestureHandler.handleActionPointerDown(event);
    } else if (action == MotionEvent.ACTION_POINTER_UP) {
      gestureHandler.handleActionPointerUp(event);
    }

    if (gestureHandler.processGestures(event)) {
      return true;
    }

    if (editor.hadMultiTouch && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      editor.pointerDown = false;
      editor.selectionHandles.draggingHandle = 0;
      editor.selection.selecting = false;
      editor.selection.isLineNumberSelecting = false;
      editor.selection.lineNumberSelectAnchorLine = -1;
      editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);
      editor.scroll.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();
    editor.lastTouchX = ex;
    editor.lastTouchY = ey;

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        editor.caret.resetBlink();
        if (!editor.isFocused()) editor.requestFocus();
        editor.pointerDown = true;
        editor.downX = ex;
        editor.downY = ey;
        editor.movedSinceDown = false;
        editor.autoCompletion.suggestionAcceptedThisTouch = false;
        editor.scroll.dragMaxScrollX = editor.wordWrap.isWordWrapEnabled ? -1f : editor.scroll.getMaxScrollXForClamp();

        editor.scroll.showScrollBar();
        if (scrollBarHandler.handleActionDown(event)) return true;
        if (popupHandler.handleActionDown(event)) return true;

        if (!editor.scroll.scroller.isFinished()) {
          editor.scroll.scroller.computeScrollOffset();
          editor.scroll.startFlingStopAnimation(editor.scroll.scroller.getCurrX(), editor.scroll.scroller.getCurrY());
        } else {
          editor.scroll.cancelFlingStopAnimation();
        }

        if (dragSelectionHandler.handleActionDown(event)) return true;

        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (editor.scroll.flingStopAnimator != null) editor.scroll.cancelFlingStopAnimation();
        if (Math.abs(ex - editor.downX) > editor.touchSlop || Math.abs(ey - editor.downY) > editor.touchSlop)
          editor.movedSinceDown = true;

        if (scrollBarHandler.handleActionMove(event)) return true;
        if (popupHandler.handleActionMove(event)) return true;

        if (editor.selection.isLineNumberSelecting) {
          editor.lineNumber.updateLineNumberSelection(editor.wordWrap.getGlobalLineForY(ey + editor.scroll.scrollY));
          return true;
        }

        if (dragSelectionHandler.handleActionMove(event)) return true;

        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        dragSelectionHandler.handleActionUpOrCancel();
        scrollBarHandler.handleActionUpOrCancel();
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();
        editor.selection.selecting = false;

        if (popupHandler.handleActionUp(event)) return true;

        if (editor.selection.isLineNumberSelecting) {
          editor.lineNumber.endLineNumberSelection();
          editor.selection.selecting = false;
          editor.pointerDown = false;
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          return true;
        }

        if (editor.selection.longPressSelecting) {
          editor.selection.endLongPressSelection();
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          else if (editor.popup.isMinimalPopup) editor.popup.showMinimalPopupAtCursor();
        }

        // Handle Suggestion Tap
        if (handleSuggestionTap(ex, ey)) return true;

        editor.pointerDown = false;
        editor.selection.selecting = false;

        if (editor.movedSinceDown && editor.scroll.scroller.isFinished()) {
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          editor.view.restartInput();
          if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixRebuildPending && !editor.wordWrap.wrapPrefixBuilding) {
            editor.wordWrap.wrapPrefixRebuildPending = false;
            editor.wordWrap.scheduleWrapPrefixRebuildUpToWindow();
          }
        }

        editor.scroll.gestureDetector.onTouchEvent(event);
        if (editor.selection.hasSelection && !editor.popup.showPopup) editor.popup.showPopupAtSelection();
        return true;

      case MotionEvent.ACTION_CANCEL:
        dragSelectionHandler.handleActionUpOrCancel();
        scrollBarHandler.handleActionUpOrCancel();
        popupHandler.handleActionCancel();
        editor.pointerDown = false;
        editor.selection.selecting = false;
        editor.selection.endLongPressSelection();
        editor.selection.isLineNumberSelecting = false;
        editor.selection.lineNumberSelectAnchorLine = -1;
        editor.autoCompletion.clearActiveSuggestion();
        editor.scroll.dragMaxScrollX = -1f;
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();
        if (editor.scroll.scrollBarFadeEnabled) editor.caret.mainHandler.removeCallbacks(editor.scroll.scrollBarHideRunnable);
        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;
    }

    return editor.callSuperOnTouchEvent(event);
  }

  private boolean handleSuggestionTap(float ex, float ey) {
    com.yn.sodiumeditor.io.EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(ex, ey, null);
    String ln = editor.textRender.getLineTextForRender(target.line);
    int charIndex = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));
    boolean isEmptyArea = (ln == null || ln.isEmpty() || charIndex >= ln.length());

    boolean allowSuggestionTap = editor.autoCompletion.activeSuggestionIsPath ? editor.autoPathCompletion.isAutoPathCompletionEnabled : editor.autoCompletion.isAutoCompletionEnabled;
    if (!editor.movedSinceDown && allowSuggestionTap && editor.autoCompletion.activeSuggestion != null && !editor.autoCompletion.activeSuggestionRect.isEmpty()) {
      if (editor.autoCompletion.activeSuggestionRect.contains(ex, ey) || (isEmptyArea && target.line == editor.cursor.cursorLine)) {
        editor.autoCompletion.acceptAutoCompletion();
        editor.pointerDown = false;
        return true;
      }
    }
    return false;
  }

  public void drawSelectionSegment(Canvas canvas, float left, float top, float right, float bottom, boolean roundTopLeft, boolean roundTopRight, boolean roundBottomRight, boolean roundBottomLeft, Paint paint) {
    if (right <= left || bottom <= top) return;
    float radius = Math.min(12f, Math.max(2f, editor.textRender.lineHeight * 0.22f));
    float insetX = 0.5f;
    editor.selection.selectionRectTmp.set(left + insetX, top, right - insetX, bottom);
    if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
      canvas.drawRect(editor.selection.selectionRectTmp, paint);
      return;
    }
    float tl = roundTopLeft ? radius : 0f, tr = roundTopRight ? radius : 0f, br = roundBottomRight ? radius : 0f, bl = roundBottomLeft ? radius : 0f;
    editor.selection.selectionRadiiTmp[0] = tl; editor.selection.selectionRadiiTmp[1] = tl; editor.selection.selectionRadiiTmp[2] = tr; editor.selection.selectionRadiiTmp[3] = tr;
    editor.selection.selectionRadiiTmp[4] = br; editor.selection.selectionRadiiTmp[5] = br; editor.selection.selectionRadiiTmp[6] = bl; editor.selection.selectionRadiiTmp[7] = bl;
    editor.selection.selectionPathTmp.reset(); editor.selection.selectionPathTmp.addRoundRect(editor.selection.selectionRectTmp, editor.selection.selectionRadiiTmp, Path.Direction.CW);
    canvas.drawPath(editor.selection.selectionPathTmp, paint);
  }
}
