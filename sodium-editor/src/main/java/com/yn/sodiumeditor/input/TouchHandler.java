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
                view.scrollManager.scrollLockAxis = 0;
                view.zoomGestureHandler.setJustFinishedScale(false);
                view.cursorState.setCursorPosition(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
                view.imeCompositionHandler.commitComposing(false);
                view.scrollManager.abortScroller();
                view.editorInputState.downX = e.getX();
                view.editorInputState.downY = e.getY();
                view.editorInputState.movedSinceDown = false;
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

                if (view.editorInputState.movedSinceDown) return;

                if (view.lineNumberState.isLineNumberSelectionEnabled()
                    && view.lineNumberRenderer.isInLineNumberGutter(e.getX(), view.isRtl ? 0f : view.editorConfig.paddingLeft)) {
                  float y = e.getY() + view.scrollManager.scrollY;
                  int line = view.viewRender.textRender.getGlobalLineForY(y);
                  view.selectionHandler.beginLineNumberSelection(line);
                  return;
                }

                com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target =
                    view.getCursorTargetForPosition(e.getX(), e.getY(), null);
                int line = target.line;
                view.scrollManager.ensureLineInWindow(line, true);

                String ln = view.viewRender.textRender.getLineFromWindowLocal(line - view.editorState.windowStartLine);
                if (ln == null) ln = view.viewRender.textRender.getLineTextForRender(line);
                int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

                if (!view.viewRender.textRender.applySmartDoubleTapSelection(line, charIndex, ln)) {
                  onSingleTapUp(e);
                  return;
                }

                view.popupTouchHandler.showPopupAtSelection();
                view.cursorAnimator.resetCursorBlink();
                view.invalidate();
                view.imeManager.showKeyboard();
                view.restartInput();
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) return true;
                if (view.zoomGestureHandler.isMultiTouchActive() || view.zoomGestureHandler.hadMultiTouch()) return true;

                if (view.selectionState.hasSelection()) {
                  view.selectionState.clearSelectionKeepLineNumberState();
                }

                if (view.foldState.isCodeFoldingEnabled && view.lineNumberRenderer.isInLineNumberGutter(e.getX(), view.isRtl ? 0f : view.editorConfig.paddingLeft)) {
                  float gy = e.getY() + view.scrollManager.scrollY;
                  int line = view.viewRender.textRender.getGlobalLineForY(gy);
                  if (view.foldTouchHandler.toggleFoldAtLine(line)) {
                    view.foldTouchHandler.startFoldMarkerRipple(line);
                    view.popupTouchHandler.hidePopup();
                    view.invalidate();
                    return true;
                  }
                }

                float y = e.getY() + view.scrollManager.scrollY;
                int visibleIndex = Math.max(0, (int) (y / view.editorConfig.lineHeight));
                int totalVisible =
                    view.wrapWordState.isWordWrapEnabled
                        ? view.wrapWordMapper.getTotalVisualLineCount(view, view.editorState.linesWindow.size())
                        : view.editorState.linesWindow.size();

                com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target =
                    view.getCursorTargetForPosition(e.getX(), e.getY(), null);
                int line = target.line;

                if (view.foldState.isCodeFoldingEnabled) {
                  String ln = view.viewRender.textRender.getLineTextForRender(line);
                  float xLocal = e.getX() + (view.isRtl ? -view.scrollManager.scrollX : view.scrollManager.scrollX) - view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl);
                  float x;
                  if (view.wrapWordState.isWordWrapEnabled) {
                    int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, line, ln, Math.max(1, Math.round(view.getWidth() - view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl))), view.editorConfig.paint);
                    int seg =
                        view.wrapWordEngine.getWrapSegmentIndexForChar(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
                    x = xLocal + view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, ln, 0, segStart, view.editorConfig.paint);
                  } else {
                    x = xLocal;
                  }
                  if (view.foldTouchHandler.isFoldPlaceholderHit(line, ln, x)) {
                    if (view.foldTouchHandler.toggleFoldAtLine(line)) {
                      view.foldTouchHandler.startFoldMarkerRipple(line);
                    }
                    view.popupTouchHandler.hidePopup();
                    view.invalidate();
                    return true;
                  }
                }

                boolean afterEnd =
                    view.editorState.isEof
                        && line >= view.editorState.windowStartLine + view.editorState.linesWindow.size()
                        && !view.editorState.linesWindow.isEmpty();
                if (view.foldState.isCodeFoldingEnabled) {
                  afterEnd = visibleIndex >= totalVisible;
                }

                if (afterEnd) {
                  if (view.editorConfig.performanceConfig.isClickAfterEndToAddLineEnabled) {
                    int lastLineIndex = view.editorState.windowStartLine + view.editorState.linesWindow.size() - 1;
                    if (visibleIndex == totalVisible) {
                      view.cursorState.setCursorPosition(lastLineIndex, view.viewRender.textRender.getLineTextForRender(lastLineIndex).length());
                      view.cursorState.setCursorPosition(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
                      view.editorTextInserter.insertTextAtCursor("\n");
                    } else {
                      view.cursorState.setCursorPosition(lastLineIndex, view.viewRender.textRender.getLineTextForRender(lastLineIndex).length());
                    }
                  } else {
                    int lastLineIndex = view.editorState.windowStartLine + view.editorState.linesWindow.size() - 1;
                    view.cursorState.setCursorPosition(lastLineIndex, view.viewRender.textRender.getLineTextForRender(lastLineIndex).length());
                  }
                } else {
                  view.scrollManager.ensureLineInWindow(line, true);
                  String ln = view.viewRender.textRender.getLineTextForRender(line);
                  view.cursorState.setCursorPosition(line, Math.max(0, Math.min(target.ch, ln.length())));
                }

                view.popupTouchHandler.hidePopup();
                view.selectionState.setSelecting(false);
                view.invalidate();
                view.cursorAnimator.resetCursorBlink();
                view.imeManager.showKeyboard();
                view.restartInput();
                view.inlinePredictionEngine.updateSuggestion();
                return true;
              }

              @Override
              public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return view.scrollManager.onScroll(e2, distanceX, distanceY);
              }

              @Override
              public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return view.scrollManager.onFling(velocityX, velocityY);
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                if (view.inlinePredictionState.suggestionAcceptedThisTouch) return true;
                com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target =
                    view.getCursorTargetForPosition(e.getX(), e.getY(), null);
                int line = target.line;
                view.scrollManager.ensureLineInWindow(line, true);
                String ln = view.viewRender.textRender.getLineTextForRender(line);
                if (ln == null || ln.isEmpty()) {
                  return onSingleTapUp(e);
                }
                int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
                if (!view.viewRender.textRender.applySmartDoubleTapSelection(line, charIndex, ln)) {
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
                view.restartInput();
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
      view.flingStopAnimator.cancel();
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
        view.editorInputState.downX = ex;
        view.editorInputState.downY = ey;
        view.editorInputState.movedSinceDown = false;
        view.inlinePredictionState.clearSuggestionAcceptedThisTouch();
        view.scrollManager.dragMaxScrollX = view.wrapWordState.isWordWrapEnabled ? -1f : view.scrollManager.getMaxScrollXForClamp();

        view.scrollManager.showScrollBar();
        if (view.scrollManager.scrollBarEnabled) {
          float maxScroll = view.scrollManager.getMaxScrollYForClamp();
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
          view.scrollManager.startFlingStopAnimation(targetX, targetY);
        } else {
          view.flingStopAnimator.cancel();
        }

        float gx = ex + (view.isRtl ? -view.scrollManager.scrollX : view.scrollManager.scrollX) - view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl);
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
        if (view.flingStopAnimator != null) view.flingStopAnimator.cancel();
        if (Math.abs(ex - view.editorInputState.downX) > view.editorInputState.touchSlop || Math.abs(ey - view.editorInputState.downY) > view.editorInputState.touchSlop)
          view.editorInputState.movedSinceDown = true;

        if (view.scrollManager.draggingScrollBar) {
          float maxScroll = view.scrollManager.getMaxScrollYForClamp();
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
          int line = view.viewRender.textRender.getGlobalLineForY(y);
          view.selectionHandler.updateLineNumberSelection(line);
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

        com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target = view.getCursorTargetForPosition(event.getX(), event.getY(), null);
        int line = target.line;

        String ln = view.viewRender.textRender.getLineFromWindowLocal(line - view.windowStartLine);
        if (ln == null) ln = view.viewRender.textRender.getLineTextForRender(line);

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
          view.restartInput();
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

    return view.superOnTouchEvent(event);
  }
}
