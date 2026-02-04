package com.yn.sodiumeditor.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

final class InputManager {
  private final SodiumEditorView view;
  private final GestureDetector gestureDetector;

  InputManager(SodiumEditorView view, Context context) {
    this.view = view;
    this.gestureDetector =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                if (view.isSuggestionAcceptedThisTouch()) {
                  view.clearSuggestionAcceptedThisTouch();
                }
                view.resetScrollLockAxisForInput();
                view.setJustFinishedScaleForInput(false);
                view.commitComposing(false);
                view.abortScrollerForInput();
                view.setDownForInput(e.getX(), e.getY());
                view.setMovedSinceDown(false);
                return true;
              }

              @Override
              public void onLongPress(MotionEvent e) {
                if (view.isSuggestionAcceptedThisTouch()) return;
                if (view.isZoomMultiTouchBlockedForInput()) return;

                if (view.isPopupVisibleForInput()) {
                  int hitAction = view.getPopupActionAtForInput(e.getX(), e.getY());
                  if (hitAction != 0) {
                    view.setPopupPressedActionForInput(hitAction);
                    view.startPopupRippleHoldForInput(hitAction, e.getX(), e.getY());
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

                view.showPopupAtSelection();
                view.resetCursorBlink();
                view.invalidate();
                view.showKeyboardForInput();
                view.restartInputForInput();
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                if (view.isSuggestionAcceptedThisTouch()) return true;
                if (view.isZoomMultiTouchBlockedForInput()) return true;

                view.clearSelectionForInput();

                if (view.isCodeFoldingEnabledForInput() && view.isInLineNumberGutterForInput(e.getX())) {
                  float gy = e.getY() + view.getScrollYForInput();
                  int line = view.getGlobalLineForY(gy);
                  if (view.toggleFoldAtLineForInput(line)) {
                    view.startFoldMarkerRippleForInput(line);
                    view.hidePopup();
                    view.invalidate();
                    return true;
                  }
                }

                float y = e.getY() + view.getScrollYForInput();
                int visibleIndex = Math.max(0, (int) (y / view.getLineHeightForInput()));
                int totalVisible =
                    view.isWordWrapEnabledForInput()
                        ? view.getTotalVisualLineCountForInput()
                        : view.getVisibleLineCountForInput();

                SodiumEditorView.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;

                if (view.isCodeFoldingEnabledForInput()) {
                  String ln = view.getLineTextForRender(line);
                  float xLocal = view.viewToTextXForInput(e.getX());
                  float x;
                  if (view.isWordWrapEnabledForInput()) {
                    int[] starts = view.getWrapStartsForLineForInput(line, ln);
                    int seg =
                        view.getWrapSegmentIndexForCharForInput(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = view.getWrapSegmentStartForInput(starts, seg);
                    x = xLocal + view.measureTextWithVisualSpacesForInput(ln, 0, segStart);
                  } else {
                    x = xLocal;
                  }
                  if (view.isFoldPlaceholderHitForInput(line, ln, x)) {
                    if (view.toggleFoldAtLineForInput(line)) {
                      view.startFoldMarkerRippleForInput(line);
                    }
                    view.hidePopup();
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

                view.hidePopup();
                view.setSelectingForInput(false);
                view.invalidate();
                view.resetCursorBlink();
                view.showKeyboardForInput();
                view.restartInputForInput();
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
                if (view.isSuggestionAcceptedThisTouch()) return true;
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
                view.showPopupAtSelection();
                view.setPendingPopupAfterDoubleTap(true);
                view.post(
                    () -> {
                      if (!view.isPendingPopupAfterDoubleTap()) return;
                      view.setPendingPopupAfterDoubleTap(false);
                      if (view.hasSelectionValue()) view.showPopupAtSelection();
                    });
                view.resetCursorBlink();
                view.invalidate();
                view.showKeyboardForInput();
                view.restartInputForInput();
                return true;
              }
            });
  }

  boolean onGestureEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }

  boolean handleTouchEvent(MotionEvent event) {
    return view.handleTouchEventFromInput(event);
  }

  boolean handleKeyDown(int keyCode, android.view.KeyEvent event) {
    return view.handleKeyDownFromInput(keyCode, event);
  }
}
