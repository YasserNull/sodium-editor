package com.yn.sodiumeditor.input.events;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.core.scroll.Popup;

/**
 * OnTouch handles all touch event logic for SodiumEditor by delegating to specialized handlers.
 */
public class OnTouch {

  private final SodiumEditor editor;
  public boolean pointerDown = false;
  public boolean movedSinceDown = false;
  public float downX = 0f, downY = 0f;
  public final int touchSlop;
  public boolean multiTouchActive = false;
  public boolean hadMultiTouch = false;
  public float lastTouchX = 0f, lastTouchY = 0f;

  private final ScrollBarHandler scrollBarHandler;
  private final PopupInteractionHandler popupHandler;
  private final DragSelectionHandler dragSelectionHandler;
  private final GestureHandler gestureHandler;

  public OnTouch(SodiumEditor editor) {
    this.editor = editor;
    this.touchSlop = android.view.ViewConfiguration.get(editor.getContext()).getScaledTouchSlop();
    this.scrollBarHandler = new ScrollBarHandler(editor);
    this.popupHandler = new PopupInteractionHandler(editor);
    this.dragSelectionHandler = new DragSelectionHandler(editor);
    this.gestureHandler = new GestureHandler(editor);
  }

  public void updateHandlePosition(float touchX, float touchY) {
    dragSelectionHandler.updateHandlePosition(touchX, touchY);
  }

  public boolean onTouchEvent(MotionEvent event) {
    if (editor.view.isDisabled) return true;

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

    if (editor.onTouch.hadMultiTouch && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      editor.onTouch.pointerDown = false;
      editor.selectionHandles.draggingHandle = 0;
      editor.selection.selecting = false;
      editor.selection.isLineNumberSelecting = false;
      editor.selection.lineNumberSelectAnchorLine = -1;
      editor.caret.mainHandler.removeCallbacks(editor.scroll.autoScrollRunnable);
      editor.scroll.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();
    editor.onTouch.lastTouchX = ex;
    editor.onTouch.lastTouchY = ey;

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        editor.caret.resetBlink();
        if (!editor.isFocused()) editor.requestFocus();
        editor.onTouch.pointerDown = true;
        editor.onTouch.downX = ex;
        editor.onTouch.downY = ey;
        editor.onTouch.movedSinceDown = false;
        editor.autoCompletion.suggestionAcceptedThisTouch = false;
        editor.scroll.dragMaxScrollX = editor.wordWrap.isWordWrapEnabled ? -1f : editor.scroll.getMaxScrollXForClamp();

        editor.scroll.showScrollBar();
        if (popupHandler.handleActionDown(event)) return true;
        if (scrollBarHandler.handleActionDown(event)) return true;

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
        if (Math.abs(ex - editor.onTouch.downX) > editor.onTouch.touchSlop || Math.abs(ey - editor.onTouch.downY) > editor.onTouch.touchSlop)
          editor.onTouch.movedSinceDown = true;

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
        boolean wasDraggingSelectionHandle =
            editor.selectionHandles.draggingHandle == 1 || editor.selectionHandles.draggingHandle == 2;
        dragSelectionHandler.handleActionUpOrCancel();
        scrollBarHandler.handleActionUpOrCancel();
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();
        editor.selection.selecting = false;

        if (popupHandler.handleActionUp(event)) return true;

        if (editor.selection.isLineNumberSelecting) {
          editor.lineNumber.endLineNumberSelection();
          editor.selection.selecting = false;
          editor.onTouch.pointerDown = false;
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

        editor.onTouch.pointerDown = false;
        editor.selection.selecting = false;

        if (wasDraggingSelectionHandle) {
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          editor.view.restartInput();
          return true;
        }

        if (editor.onTouch.movedSinceDown && editor.scroll.scroller.isFinished()) {
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
        editor.onTouch.pointerDown = false;
        editor.selection.selecting = false;
        editor.selection.endLongPressSelection();
        editor.selection.isLineNumberSelecting = false;
        editor.selection.lineNumberSelectAnchorLine = -1;
        editor.autoCompletion.clearActiveSuggestion();
        editor.scroll.dragMaxScrollX = -1f;
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();
        if (editor.scroll.bar.fadeEnabled) editor.caret.mainHandler.removeCallbacks(editor.scroll.bar.hideRunnable);        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;
    }

    return editor.callSuperOnTouchEvent(event);
  }

  private boolean handleSuggestionTap(float ex, float ey) {
    com.yn.sodiumeditor.io.EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(ex, ey, null);
    String ln = editor.windowRender.getLineTextForRender(target.line);
    int charIndex = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));
    boolean isEmptyArea = (ln == null || ln.isEmpty() || charIndex >= ln.length());

    boolean allowSuggestionTap = editor.autoCompletion.activeSuggestionIsPath ? editor.autoPathCompletion.isAutoPathCompletionEnabled : editor.autoCompletion.isAutoCompletionEnabled;
    if (!editor.onTouch.movedSinceDown && allowSuggestionTap && editor.autoCompletion.activeSuggestion != null && !editor.autoCompletion.activeSuggestionRect.isEmpty()) {
      if (editor.autoCompletion.activeSuggestionRect.contains(ex, ey) || (isEmptyArea && target.line == editor.cursor.cursorLine)) {
        if (editor.autoCompletion.activeSuggestionIsPath) {
          editor.autoPathCompletion.acceptPathCompletion();
        } else {
          editor.autoCompletion.acceptAutoCompletion();
        }
        editor.onTouch.pointerDown = false;
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
