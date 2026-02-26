package com.yn.sodiumeditor.input;

import android.content.Context;
import android.graphics.RectF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import com.yn.sodiumeditor.*;

public final class TouchHandler {
  private final SodiumEditorView view;
  private final GestureDetector gestureDetector;

  public TouchHandler(SodiumEditorView view, Context context) {
    this.view = view;
    this.gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) {
                  view.autoSuggestionManager.clearSuggestionAcceptedThisTouch();
                }
                view.resetScrollLockAxisForInput();
                view.setJustFinishedScaleForInput(false);
                view.cursorManager.commitComposing(false);
                view.abortScrollerForInput();
                view.setDownForInput(e.getX(), e.getY());
                view.setMovedSinceDown(false);
                return true;
              }

              @Override
              public void onLongPress(MotionEvent e) {
                if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return;
                if (view.zoomGestureHandler.isMultiTouchActive() || view.zoomGestureHandler.hadMultiTouch()) return;

                if (view.popupMenuManager.isPopupVisible()) {
                  int hitAction = view.popupMenuManager.getPopupActionAt(e.getX(), e.getY());
                  if (hitAction != 0) {
                    view.popupMenuManager.setPressedAction(hitAction);
                    view.popupMenuManager.startPopupRippleHold(hitAction, e.getX(), e.getY());
                    return;
                  }
                }

                if (view.isMovedSinceDown()) return;

                if (view.isLineNumberSelectionEnabledForInput()
                    && view.isInLineNumberGutterForInput(e.getX())) {
                  float y = e.getY() + view.getScrollYForInput();
                  int line = view.getGlobalLineForY(y);
                  view.beginLineNumberSelectionForInput(line);
                  return;
                }

                SodiumEditorView.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;
                view.ensureLineInWindowForInput(line, true);

                String ln = view.getLineFromWindowLocalForInput(line - view.getWindowStartLineForInput());
                if (ln == null) ln = view.getLineTextForRender(line);
                int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

                if (!view.applySmartDoubleTapSelectionForInput(line, charIndex, ln)) {
                  onSingleTapUp(e);
                  return;
                }

                view.popupMenuManager.showPopupAtSelection();
                view.cursorAnimationManager.resetCursorBlink();
                view.invalidate();
                view.imeManager.showKeyboard();
                view.restartInputPublic();
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return true;
                if (view.zoomGestureHandler.isMultiTouchActive() || view.zoomGestureHandler.hadMultiTouch()) return true;

                view.clearSelectionForInput();

                if (view.isCodeFoldingEnabledForInput() && view.isInLineNumberGutterForInput(e.getX())) {
                  float gy = e.getY() + view.getScrollYForInput();
                  int line = view.getGlobalLineForY(gy);
                  if (view.foldManager.toggleFoldAtLine(line)) {
                    view.startFoldMarkerRippleForInput(line);
                    view.popupMenuManager.hidePopup();
                    view.invalidate();
                    return true;
                  }
                }

                float y = e.getY() + view.getScrollYForInput();
                int visibleIndex = Math.max(0, (int) (y / view.getLineHeightForInput()));
                int totalVisible =
                    view.wrapWordState.isWordWrapEnabled
                        ? view.getTotalVisualLineCountForInput()
                        : view.getVisibleLineCountForInput();

                SodiumEditorView.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;

                if (view.isCodeFoldingEnabledForInput()) {
                  String ln = view.getLineTextForRender(line);
                  float xLocal = view.viewToTextXForInput(e.getX());
                  float x;
                  if (view.wrapWordState.isWordWrapEnabled) {
                    int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, line, ln, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())), view.paint);
                    int seg =
                        view.wrapWordEngine.getWrapSegmentIndexForChar(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
                    x = xLocal + view.measureTextWithVisualSpacesForInput(ln, 0, segStart);
                  } else {
                    x = xLocal;
                  }
                  if (view.isFoldPlaceholderHitForInput(line, ln, x)) {
                    if (view.foldManager.toggleFoldAtLine(line)) {
                      view.startFoldMarkerRippleForInput(line);
                    }
                    view.popupMenuManager.hidePopup();
                    view.invalidate();
                    return true;
                  }
                }

                boolean afterEnd =
                    view.isEofForInput()
                        && line >= view.getWindowStartLineForInput() + view.getLinesWindowSizeForInput()
                        && !view.isLinesWindowEmptyForInput();
                if (view.isCodeFoldingEnabledForInput()) {
                  afterEnd = visibleIndex >= totalVisible;
                }

                if (afterEnd) {
                  if (view.isClickAfterEndToAddLineEnabledForInput()) {
                    int lastLineIndex = view.getWindowStartLineForInput() + view.getLinesWindowSizeForInput() - 1;
                    if (visibleIndex == totalVisible) {
                      view.setCursorPositionForInput(lastLineIndex, view.getLineTextForRender(lastLineIndex).length());
                      view.insertTextAtCursorForInput("\n");
                    } else {
                      view.setCursorPositionForInput(lastLineIndex, view.getLineTextForRender(lastLineIndex).length());
                    }
                  } else {
                    int lastLineIndex = view.getWindowStartLineForInput() + view.getLinesWindowSizeForInput() - 1;
                    view.setCursorPositionForInput(lastLineIndex, view.getLineTextForRender(lastLineIndex).length());
                  }
                } else {
                  view.ensureLineInWindowForInput(line, true);
                  String ln = view.getLineTextForRender(line);
                  view.setCursorPositionForInput(line, Math.max(0, Math.min(target.ch, ln.length())));
                }

                view.popupMenuManager.hidePopup();
                view.setSelectingForInput(false);
                view.invalidate();
                view.cursorAnimationManager.resetCursorBlink();
                view.imeManager.showKeyboard();
                view.restartInputPublic();
                view.updateSuggestionForInput();
                return true;
              }

              @Override
              public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return view.handleScrollFromInput(e2, distanceX, distanceY);
              }

              @Override
              public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return view.handleFlingFromInput(velocityX, velocityY);
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return true;
                SodiumEditorView.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;
                view.ensureLineInWindowForInput(line, true);
                String ln = view.getLineTextForRender(line);
                if (ln == null || ln.isEmpty()) {
                  return onSingleTapUp(e);
                }
                int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
                if (!view.applySmartDoubleTapSelectionForInput(line, charIndex, ln)) {
                  return onSingleTapUp(e);
                }
                view.popupMenuManager.showPopupAtSelection();
                view.popupMenuManager.setPendingPopupAfterDoubleTap(true);
                view.post(
                    () -> {
                      if (!view.popupMenuManager.isPendingPopupAfterDoubleTap()) return;
                      view.popupMenuManager.setPendingPopupAfterDoubleTap(false);
                      if (view.selectionManager.hasSelection()) view.popupMenuManager.showPopupAtSelection();
                    });
                view.cursorAnimationManager.resetCursorBlink();
                view.invalidate();
                view.imeManager.showKeyboard();
                view.restartInputPublic();
                return true;
              }
            });
  }

  boolean onGestureEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }

  public boolean handleTouchEvent(MotionEvent event) {
    if (view.isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    if (action == MotionEvent.ACTION_DOWN) {
      view.zoomGestureHandler.resetMultiTouchState();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      view.zoomGestureHandler.onPointerDown();
      view.pointerDown = false;
      view.movedSinceDown = false;
      view.handlesManager.stopDragging();
      view.scrollManager.dragMaxScrollX = -1f;
      view.selectionManager.setSelecting(false);
      view.selectionManager.setLineNumberSelecting(false, -1);
      view.handlesManager.stopAutoScroll();
      if (!view.scrollManager.scroller.isFinished()) {
        view.scrollManager.scroller.computeScrollOffset();
        view.scrollManager.scrollX = view.scrollManager.scroller.getCurrX();
        view.scrollManager.scrollY = view.scrollManager.scroller.getCurrY();
        view.scrollManager.scroller.abortAnimation();
      }
      view.cancelFlingStopAnimationPublic();
    }

    if (action == MotionEvent.ACTION_POINTER_UP) {
      view.zoomGestureHandler.onPointerUp(pointerCount - 1);
      if (pointerCount - 1 <= 1) view.scrollManager.dragMaxScrollX = -1f;
    }

    view.zoomGestureHandler.onTouchEvent(event);

    if (view.zoomGestureHandler.isScaleInProgress()
        || view.zoomGestureHandler.isMultiTouchActive()
        || pointerCount > 1
        || view.zoomGestureHandler.isScaling()
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      return true;
    }

    if (view.zoomGestureHandler.hadMultiTouch()
        && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      view.pointerDown = false;
      view.handlesManager.stopDragging();
      view.selectionManager.setSelecting(false);
      view.selectionManager.setLineNumberSelecting(false, -1);
      view.handlesManager.stopAutoScroll();
      view.scrollManager.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        view.cursorAnimationManager.resetCursorBlink();
        if (!view.isFocused()) view.requestFocus();
        view.pointerDown = true;
        view.setDownXPublic(ex);
        view.setDownYPublic(ey);
        view.movedSinceDown = false;
        view.autoSuggestionManager.clearSuggestionAcceptedThisTouch();
        view.scrollManager.dragMaxScrollX = view.wrapWordState.isWordWrapEnabled ? -1f : view.scrollManager.getMaxScrollXForClamp();

        view.scrollManager.showScrollBar();
        if (view.scrollManager.scrollBarEnabled) {
          float maxScroll = view.getMaxScrollYForClampPublic();
          if (maxScroll > 0f && view.scrollManager.scrollBarThumbRect.contains(ex, ey)) {
            view.scrollManager.draggingScrollBar = true;
            view.scrollManager.scrollBarDragOffset = ey - view.scrollManager.scrollBarThumbRect.top;
            view.scrollManager.showScrollBar();
            return true;
          }
        }

        if (view.popupMenuManager.isPopupVisible()) {
          int hitAction = view.popupMenuManager.getPopupActionAt(ex, ey);
          if (hitAction != 0) {
            view.popupMenuManager.setPressedAction(hitAction);
            view.popupMenuManager.startPopupRipple(hitAction, ex, ey);
            return true;
          }
        }

        if (!view.scrollManager.scroller.isFinished()) {
          view.scrollManager.scroller.computeScrollOffset();
          float targetX = view.scrollManager.scroller.getCurrX();
          float targetY = view.scrollManager.scroller.getCurrY();
          view.scrollManager.scroller.abortAnimation();
          view.startFlingStopAnimationPublic(targetX, targetY);
        } else {
          view.cancelFlingStopAnimationPublic();
        }

        float gx = ex + view.getEffectiveScrollX() - view.getTextStartX();
        float gy = ey + view.scrollManager.scrollY - view.scrollManager.getHitTestBaseY();
        int hitHandle =
            view.handlesManager.hitTestHandle(gx, gy, view.selectionManager.hasSelection(), view.isFocused());
        if (hitHandle != HandlesManager.HANDLE_NONE) {
          view.handlesManager.setDraggingHandle(hitHandle);
          return true;
        }

        onGestureEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (view.getFlingStopAnimatorPublic() != null) view.cancelFlingStopAnimationPublic();
        if (Math.abs(ex - view.getDownXPublic()) > view.getTouchSlopPublic() || Math.abs(ey - view.getDownYPublic()) > view.getTouchSlopPublic())
          view.movedSinceDown = true;

        if (view.scrollManager.draggingScrollBar) {
          float maxScroll = view.getMaxScrollYForClampPublic();
          if (maxScroll > 0f && view.scrollManager.scrollBarThumbRect.contains(ex, ey)) {
            view.scrollManager.draggingScrollBar = true;
            view.scrollManager.scrollBarDragOffset = ey - view.scrollManager.scrollBarThumbRect.top;
            view.scrollManager.showScrollBar();
            return true;
          }
        }

        if (view.popupMenuManager.getPressedAction() != 0) {
          int pressed = view.popupMenuManager.getPressedAction();
          RectF r = view.popupMenuManager.getPopupRectForAction(pressed);
          if (!r.contains(ex, ey)) {
            view.popupMenuManager.clearPressedAction();
            view.popupMenuManager.cancelPopupRipple();
          }
          return true;
        }

        if (view.selectionManager.isLineNumberSelecting()) {
          float y = ey + view.scrollManager.scrollY;
          int line = view.getGlobalLineForY(y);
          view.updateLineNumberSelectionPublic(line);
          return true;
        }

        if (view.handlesManager.isDragging()) {
          view.handlesManager.onTouchMove(ex, ey);
          view.handlesManager.handleDrag(ex, ey);
          return true;
        }

        onGestureEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        view.handlesManager.stopAutoScroll();
        view.scrollManager.dragMaxScrollX = -1f;

        if (view.scrollManager.draggingScrollBar) {
          view.scrollManager.draggingScrollBar = false;
          view.scrollManager.showScrollBar();
          return true;
        }

        if (view.popupMenuManager.getPressedAction() != 0) {
          int actionForTap = view.popupMenuManager.getPressedAction();
          view.popupMenuManager.clearPressedAction();
          RectF r = view.popupMenuManager.getPopupRectForAction(actionForTap);
          if (view.popupMenuManager.isPopupVisible() && r.contains(ex, ey)) {
            if (view.isReadOnly
                && (actionForTap == PopupMenuManager.POPUP_ACTION_CUT
                    || actionForTap == PopupMenuManager.POPUP_ACTION_PASTE
                    || actionForTap == PopupMenuManager.POPUP_ACTION_DELETE)) {
              view.popupMenuManager.hidePopup();
              return true;
            }
            if (actionForTap == PopupMenuManager.POPUP_ACTION_COPY) {
              view.copySelectionToClipboard();
              view.selectionManager.clearSelectionKeepLineNumberState();
              view.popupMenuManager.hidePopup();
              view.invalidate();
            } else if (actionForTap == PopupMenuManager.POPUP_ACTION_SELECT_ALL) {
              if (!view.selectionManager.isSelectAllActive()) view.selectAll();
              else view.popupMenuManager.hidePopup();
            } else {
              view.popupMenuManager.performPopupAction(actionForTap);
            }
          }
          else {
            view.popupMenuManager.cancelPopupRipple();
          }
          if (view.popupMenuManager.isPopupRippleHoldActive()) {
            view.popupMenuManager.cancelPopupRipple();
          }
          return true;
        }

        if (view.selectionManager.isLineNumberSelecting()) {
          view.selectionManager.setLineNumberSelecting(false, -1);
          view.selectionManager.setSelecting(false);
          view.pointerDown = false;
          if (view.selectionManager.hasSelection()) view.popupMenuManager.showPopupAtSelection();
          return true;
        }

        SodiumEditorView.CursorTarget target = view.getCursorTargetForPositionPublic(event.getX(), event.getY(), null);
        int line = target.line;

        String ln = view.getLineFromWindowLocal(line - view.windowStartLine);
        if (ln == null) ln = view.getLineTextForRender(line);

        int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

        boolean isEmptyArea = false;
        if (ln.isEmpty()) {
          isEmptyArea = true;
        } else if (charIndex >= ln.length()) {
          isEmptyArea = true;
        }

        if (!view.movedSinceDown
            && view.autoSuggestionManager.maybeAcceptSuggestionTap(ex, ey, line, isEmptyArea)) {
          view.pointerDown = false;
          Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
          return true;
        }

        view.pointerDown = false;

        if (view.handlesManager.isDragging()) {
          int draggingHandle = view.handlesManager.getDraggingHandle();
          if (draggingHandle == HandlesManager.HANDLE_LEFT
              || draggingHandle == HandlesManager.HANDLE_RIGHT) {
            view.popupMenuManager.showPopupAtSelection();
          }
          view.handlesManager.stopDragging();
          view.invalidate();
          return true;
        }

        if (view.movedSinceDown && view.scrollManager.scroller.isFinished()) {
          if (view.selectionManager.hasSelection()) view.popupMenuManager.showPopupAtSelection();
          view.restartInputPublic();
          Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (view.wrapWordState.isWordWrapEnabled && view.wrapWordState.wrapPrefixRebuildPending && !view.wrapWordState.wrapPrefixBuilding) {
            view.wrapWordState.wrapPrefixRebuildPending = false;
            view.wrapWordBuilder.schedulePrefixRebuildUpToWindow(view);
          }
        }

        Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Passing to GestureDetector.ACTION_UP.");
        onGestureEvent(event);
        if (view.selectionManager.hasSelection() && !view.popupMenuManager.isPopupVisible()) {
          view.popupMenuManager.showPopupAtSelection();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        view.handlesManager.stopAutoScroll();
        view.pointerDown = false;
        view.handlesManager.stopDragging();
        view.selectionManager.setSelecting(false);
        view.selectionManager.setLineNumberSelecting(false, -1);
        view.popupMenuManager.clearPressedAction();
        view.popupMenuManager.cancelPopupRipple();
        view.autoSuggestionManager.clearActiveSuggestion();
        view.scrollManager.dragMaxScrollX = -1f;
        view.scrollManager.draggingScrollBar = false;
        if (view.scrollManager.scrollBarFadeEnabled) {
          view.mainHandler.removeCallbacks(view.scrollManager.scrollBarHideRunnable);
        }
        Log.d("SodiumEditorView", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        onGestureEvent(event);
        return true;
    }

    return view.superOnTouchEventPublic(event);
  }
}
