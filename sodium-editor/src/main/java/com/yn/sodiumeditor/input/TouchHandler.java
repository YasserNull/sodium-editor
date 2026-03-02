package com.yn.sodiumeditor.input;

import android.content.Context;
import android.graphics.RectF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import com.yn.sodiumeditor.*;
import com.yn.sodiumeditor.config.PopupConfig;

public final class TouchHandler {
  private final SodiumEditor view;
  private final GestureDetector gestureDetector;

  public TouchHandler(SodiumEditor view, Context context) {
    this.view = view;
    this.gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) {
                  view.inlinePredictionState.clearSuggestionAcceptedThisTouch();
                }
                view.resetScrollLockAxisForInput();
                view.setJustFinishedScaleForInput(false);
                view.cursorState.setCursorPosition(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
                view.imeCompositionHandler.commitComposing(false);
                view.abortScrollerForInput();
                view.setDownForInput(e.getX(), e.getY());
                view.setMovedSinceDown(false);
                return true;
              }

              @Override
              public void onLongPress(MotionEvent e) {
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) return;
                if (view.zoomGestureHandler.isMultiTouchActive() || view.zoomGestureHandler.hadMultiTouch()) return;

                if (view.popupMenuState.showPopup) {
                  int hitAction = view.popupTouchHandler.getPopupActionAt(e.getX(), e.getY());
                  if (hitAction != 0) {
                    view.popupMenuState.setPressedAction(hitAction);
                    view.popupTouchHandler.startPopupRippleHold(hitAction, e.getX(), e.getY());
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

                SodiumEditor.CursorTarget target =
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

                view.popupTouchHandler.showPopupAtSelection();
                view.cursorAnimator.resetCursorBlink();
                view.invalidate();
                view.imeManager.showKeyboard();
                view.restartInputPublic();
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) return true;
                if (view.zoomGestureHandler.isMultiTouchActive() || view.zoomGestureHandler.hadMultiTouch()) return true;

                view.clearSelectionForInput();

                if (view.isCodeFoldingEnabledForInput() && view.isInLineNumberGutterForInput(e.getX())) {
                  float gy = e.getY() + view.getScrollYForInput();
                  int line = view.getGlobalLineForY(gy);
                  if (view.foldTouchHandler.toggleFoldAtLine(line)) {
                    view.startFoldMarkerRippleForInput(line);
                    view.popupTouchHandler.hidePopup();
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

                SodiumEditor.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;

                if (view.isCodeFoldingEnabledForInput()) {
                  String ln = view.getLineTextForRender(line);
                  float xLocal = view.viewToTextXForInput(e.getX());
                  float x;
                  if (view.wrapWordState.isWordWrapEnabled) {
                    int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, line, ln, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())), view.editorConfig.paint);
                    int seg =
                        view.wrapWordEngine.getWrapSegmentIndexForChar(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
                    x = xLocal + view.measureTextWithVisualSpacesForInput(ln, 0, segStart);
                  } else {
                    x = xLocal;
                  }
                  if (view.isFoldPlaceholderHitForInput(line, ln, x)) {
                    if (view.foldTouchHandler.toggleFoldAtLine(line)) {
                      view.startFoldMarkerRippleForInput(line);
                    }
                    view.popupTouchHandler.hidePopup();
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

                view.popupTouchHandler.hidePopup();
                view.setSelectingForInput(false);
                view.invalidate();
                view.cursorAnimator.resetCursorBlink();
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
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) return true;
                SodiumEditor.CursorTarget target =
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
                view.popupTouchHandler.showPopupAtSelection();
                view.popupMenuState.setPendingPopupAfterDoubleTap(true);
                view.post(
                    () -> {
                      if (!view.popupMenuState.pendingPopupAfterDoubleTap) return;
                      view.popupMenuState.setPendingPopupAfterDoubleTap(false);
                      if (view.selectionState.hasSelection()) view.popupTouchHandler.showPopupAtSelection();
                    });
                view.cursorAnimator.resetCursorBlink();
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
    if (view.editorConfig.behaviorConfig.isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    if (action == MotionEvent.ACTION_DOWN) {
      view.zoomGestureHandler.resetMultiTouchState();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      view.zoomGestureHandler.onPointerDown();
      view.pointerDown = false;
      view.movedSinceDown = false;
      view.handleDragHandler.stopDragging();
      view.scrollManager.dragMaxScrollX = -1f;
      view.selectionState.setSelecting(false);
      view.selectionState.setLineNumberSelecting(false, -1);
      view.handleDragHandler.stopAutoScroll();
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
      view.handleDragHandler.stopDragging();
      view.selectionState.setSelecting(false);
      view.selectionState.setLineNumberSelecting(false, -1);
      view.handleDragHandler.stopAutoScroll();
      view.scrollManager.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        view.cursorAnimator.resetCursorBlink();
        if (!view.isFocused()) view.requestFocus();
        view.pointerDown = true;
        view.setDownXPublic(ex);
        view.setDownYPublic(ey);
        view.movedSinceDown = false;
        view.inlinePredictionState.clearSuggestionAcceptedThisTouch();
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

        if (view.popupMenuState.showPopup) {
          int hitAction = view.popupTouchHandler.getPopupActionAt(ex, ey);
          if (hitAction != 0) {
            view.popupMenuState.setPressedAction(hitAction);
            view.popupTouchHandler.startPopupRipple(hitAction, ex, ey);
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
            view.handleDragHandler.hitTestHandle(gx, gy, view.selectionState.hasSelection(), view.isFocused());
        if (hitHandle != com.yn.sodiumeditor.state.HandleState.HANDLE_NONE) {
          view.handleState.setDraggingHandle(hitHandle);
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

        if (view.popupMenuState.popupPressedAction != 0) {
          int pressed = view.popupMenuState.popupPressedAction;
          RectF r = view.popupMenuRenderer.getPopupRectForAction(pressed);
          if (!r.contains(ex, ey)) {
            view.popupMenuState.clearPressedAction();
            view.popupTouchHandler.cancelPopupRipple();
          }
          return true;
        }

        if (view.selectionState.isLineNumberSelecting()) {
          float y = ey + view.scrollManager.scrollY;
          int line = view.getGlobalLineForY(y);
          view.updateLineNumberSelectionPublic(line);
          return true;
        }

        if (view.handleState.isDragging()) {
          view.handleDragHandler.onTouchMove(ex, ey);
          view.handleDragHandler.handleDrag(ex, ey);
          return true;
        }

        onGestureEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        view.handleDragHandler.stopAutoScroll();
        view.scrollManager.dragMaxScrollX = -1f;

        if (view.scrollManager.draggingScrollBar) {
          view.scrollManager.draggingScrollBar = false;
          view.scrollManager.showScrollBar();
          return true;
        }

        if (view.popupMenuState.popupPressedAction != 0) {
          int actionForTap = view.popupMenuState.popupPressedAction;
          view.popupMenuState.clearPressedAction();
          RectF r = view.popupMenuRenderer.getPopupRectForAction(actionForTap);
          if (view.popupMenuState.showPopup && r.contains(ex, ey)) {
            if (view.editorConfig.behaviorConfig.isReadOnly
                && (actionForTap == PopupConfig.POPUP_ACTION_CUT
                    || actionForTap == PopupConfig.POPUP_ACTION_PASTE
                    || actionForTap == PopupConfig.POPUP_ACTION_DELETE)) {
              view.popupTouchHandler.hidePopup();
              return true;
            }
            if (actionForTap == PopupConfig.POPUP_ACTION_COPY) {
              view.copySelectionToClipboard();
              view.selectionState.clearSelectionKeepLineNumberState();
              view.popupTouchHandler.hidePopup();
              view.invalidate();
            } else if (actionForTap == PopupConfig.POPUP_ACTION_SELECT_ALL) {
              if (!view.selectionState.isSelectAllActive()) view.selectAll();
              else view.popupTouchHandler.hidePopup();
            } else {
              view.popupTouchHandler.performPopupAction(actionForTap);
            }
          }
          else {
            view.popupTouchHandler.cancelPopupRipple();
          }
          if (view.popupMenuState.popupRippleHoldActive) {
            view.popupTouchHandler.cancelPopupRipple();
          }
          return true;
        }

        if (view.selectionState.isLineNumberSelecting()) {
          view.selectionState.setLineNumberSelecting(false, -1);
          view.selectionState.setSelecting(false);
          view.pointerDown = false;
          if (view.selectionState.hasSelection()) view.popupTouchHandler.showPopupAtSelection();
          return true;
        }

        SodiumEditor.CursorTarget target = view.getCursorTargetForPositionPublic(event.getX(), event.getY(), null);
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
            && view.inlinePredictionEngine.maybeAcceptSuggestionTap(ex, ey, line, isEmptyArea)) {
          view.pointerDown = false;
          Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
          return true;
        }

        view.pointerDown = false;

        if (view.handleState.isDragging()) {
          int draggingHandle = view.handleState.getDraggingHandle();
          if (draggingHandle == com.yn.sodiumeditor.state.HandleState.HANDLE_LEFT
              || draggingHandle == com.yn.sodiumeditor.state.HandleState.HANDLE_RIGHT) {
            view.popupTouchHandler.showPopupAtSelection();
          }
          view.handleDragHandler.stopDragging();
          view.invalidate();
          return true;
        }

        if (view.movedSinceDown && view.scrollManager.scroller.isFinished()) {
          if (view.selectionState.hasSelection()) view.popupTouchHandler.showPopupAtSelection();
          view.restartInputPublic();
          Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (view.wrapWordState.isWordWrapEnabled && view.wrapWordState.wrapPrefixRebuildPending && !view.wrapWordState.wrapPrefixBuilding) {
            view.wrapWordState.wrapPrefixRebuildPending = false;
            view.wrapWordBuilder.schedulePrefixRebuildUpToWindow(view);
          }
        }

        Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Passing to GestureDetector.ACTION_UP.");
        onGestureEvent(event);
        if (view.selectionState.hasSelection() && !view.popupMenuState.showPopup) {
          view.popupTouchHandler.showPopupAtSelection();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        view.handleDragHandler.stopAutoScroll();
        view.pointerDown = false;
        view.handleDragHandler.stopDragging();
        view.selectionState.setSelecting(false);
        view.selectionState.setLineNumberSelecting(false, -1);
        view.popupMenuState.clearPressedAction();
        view.popupTouchHandler.cancelPopupRipple();
        view.inlinePredictionState.clearActiveSuggestion();
        view.scrollManager.dragMaxScrollX = -1f;
        view.scrollManager.draggingScrollBar = false;
        if (view.scrollManager.scrollBarFadeEnabled) {
          view.mainHandler.removeCallbacks(view.scrollManager.scrollBarHideRunnable);
        }
        Log.d("SodiumEditor", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        onGestureEvent(event);
        return true;
    }

    return view.superOnTouchEventPublic(event);
  }
}
