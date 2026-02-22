package com.yn.sodiumeditor.input;

import android.content.Context;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.animation.ValueAnimator; // Added for ValueAnimator type
import com.yn.sodiumeditor.*;

public final class InputManager {
  private final SodiumEditorView view;
  private final GestureDetector gestureDetector;

  public InputManager(SodiumEditorView view, Context context) {
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
                if (view.zoomManager.isMultiTouchActive() || view.zoomManager.hadMultiTouch()) return;

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
                view.restartInputPublic(); // Changed
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return true;
                if (view.zoomManager.isMultiTouchActive() || view.zoomManager.hadMultiTouch()) return true;

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
                    view.wordWrapManager.isWordWrapEnabled
                        ? view.getTotalVisualLineCountForInput()
                        : view.getVisibleLineCountForInput();

                SodiumEditorView.CursorTarget target =
                    view.getCursorTargetForInput(e.getX(), e.getY());
                int line = target.line;

                if (view.isCodeFoldingEnabledForInput()) {
                  String ln = view.getLineTextForRender(line);
                  float xLocal = view.viewToTextXForInput(e.getX());
                  float x;
                  if (view.wordWrapManager.isWordWrapEnabled) {
                    int[] starts = view.wordWrapManager.getWrapStartsForLine(view, line, ln);
                    int seg =
                        view.wordWrapManager.getWrapSegmentIndexForChar(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
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
                view.restartInputPublic(); // Changed
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
                view.restartInputPublic(); // Changed
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
      view.zoomManager.resetMultiTouchState();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      view.zoomManager.onPointerDown();
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
      view.zoomManager.onPointerUp(pointerCount - 1);
      if (pointerCount - 1 <= 1) view.scrollManager.dragMaxScrollX = -1f;
    }

    view.zoomManager.onScaleTouchEvent(event);

    if (view.zoomManager.isScaleInProgress()
        || view.zoomManager.isMultiTouchActive()
        || pointerCount > 1
        || view.zoomManager.isScaling()
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      return true;
    }

    if (view.zoomManager.hadMultiTouch()
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
        view.autoSuggestionManager.clearSuggestionAcceptedThisTouch(); // Reset flag for new touch sequence
        view.scrollManager.dragMaxScrollX = view.wordWrapManager.isWordWrapEnabled ? -1f : view.scrollManager.getMaxScrollXForClamp(); // Checked: getMaxScrollXForClamp is package-private in ScrollManager. It's accessible.

        view.scrollManager.showScrollBar();
        if (view.scrollManager.scrollBarEnabled) {
          float maxScroll = view.getMaxScrollYForClampPublic(); // Changed
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
          view.startFlingStopAnimationPublic(targetX, targetY); // Changed
        } else {
          view.cancelFlingStopAnimationPublic(); // Changed
        }

        // FIX: Use getTextStartX() to correctly calculate touch coordinates relative to the text
        // area.
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
        if (view.getFlingStopAnimatorPublic() != null) view.cancelFlingStopAnimationPublic(); // Changed
        if (Math.abs(ex - view.getDownXPublic()) > view.getTouchSlopPublic() || Math.abs(ey - view.getDownYPublic()) > view.getTouchSlopPublic()) // Changed
          view.movedSinceDown = true;

        if (view.scrollManager.draggingScrollBar) {
          float maxScroll = view.getMaxScrollYForClampPublic(); // Changed
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
          view.updateLineNumberSelectionPublic(line); // Changed
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

        // --- Check for tap on suggestion FIRST and consume if it's a clean tap ---
        SodiumEditorView.CursorTarget target = view.getCursorTargetForPositionPublic(event.getX(), event.getY(), null); // Changed
        int line = target.line;

        // Get line text safely
        String ln = view.getLineFromWindowLocal(line - view.windowStartLine);
        if (ln == null) ln = view.getLineTextForRender(line);

        int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

        // Check if the long press was on an "empty" area
        boolean isEmptyArea = false;
        if (ln.isEmpty()) {
          isEmptyArea = true;
        } else if (charIndex >= ln.length()) {
          isEmptyArea = true; // Tapped on empty space after the text on a line
        }

        if (!view.movedSinceDown
            && view.autoSuggestionManager.maybeAcceptSuggestionTap(ex, ey, line, isEmptyArea)) {
          view.pointerDown = false; // Reset pointerDown state
          Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
          return true; // Consume the event, preventing further processing
        }
        // --- END Check ---

        view.pointerDown = false;
        // view.autoSuggestionManager.clearActiveSuggestion();

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

        if (view.movedSinceDown && view.scrollManager.scroller.isFinished()) { // Just finished a scroll/drag
          if (view.selectionManager.hasSelection()) view.popupMenuManager.showPopupAtSelection();
          view.restartInputPublic(); // Changed
          Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (view.wordWrapManager.isWordWrapEnabled && view.wordWrapManager.wrapPrefixRebuildPending && !view.wordWrapManager.wrapPrefixBuilding) {
            view.wordWrapManager.wrapPrefixRebuildPending = false;
            view.wordWrapManager.scheduleWrapPrefixRebuildUpToWindow(view);
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
        view.autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion on touch cancel
        view.scrollManager.dragMaxScrollX = -1f;
        view.scrollManager.draggingScrollBar = false;
        if (view.scrollManager.scrollBarFadeEnabled) {
          view.mainHandler.removeCallbacks(view.scrollManager.scrollBarHideRunnable);
        }
        Log.d("SodiumEditorView", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        onGestureEvent(event);
        return true;
    }

    return view.superOnTouchEventPublic(event); // Changed
  }

  public boolean handleKeyDown(int keyCode, KeyEvent event) {
    if (view.isDisabled) return true;
    if (view.isReadOnly) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          view.cursorManager.moveCursorLeft();
          return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          view.cursorManager.moveCursorRight();
          return true;
        case KeyEvent.KEYCODE_DPAD_UP:
          view.cursorManager.moveCursorUp();
          return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          view.cursorManager.moveCursorDown();
          return true;
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_FORWARD_DEL:
        case KeyEvent.KEYCODE_ENTER:
          return true;
      }
      if (event.isPrintingKey()) return true;
    }

    if (view.selectionManager.hasSelection() && event.isPrintingKey()) {
      int uc = event.getUnicodeChar();
      if (uc != 0) {
        String s = String.valueOf((char) uc);
        view.replaceSelectionWithText(s);
        view.charAnimationManager.startCharAnimationFromText(s);
      } else {
        view.replaceSelectionWithText("");
      }
      return true;
    }

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        view.cursorManager.moveCursorLeft();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        view.cursorManager.moveCursorRight();
        return true;
      case KeyEvent.KEYCODE_DPAD_UP:
        view.cursorManager.moveCursorUp();
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        view.cursorManager.moveCursorDown();
        return true;

      case KeyEvent.KEYCODE_DEL:
        if (view.selectionManager.hasSelection()) view.replaceSelectionWithText("");
        else view.deleteCharAtCursor();
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (view.selectionManager.hasSelection()) view.replaceSelectionWithText("");
        else view.deleteForwardAtCursor();
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (view.selectionManager.hasSelection()) view.replaceSelectionWithText("\n");
        else view.insertNewlineAtCursor();
        return true;
    }
    return view.superOnKeyDown(keyCode, event);
  }

  public void insertCharAtCursor(char c) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.undoRedo.incrementEditVersion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.setHasComposing(false);
      view.cursorManager.setComposingLength(0);
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) view.linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, view.linesWindow.size() - 1));
    }

    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        int oldLineCount = view.getLinesCount();
        String before = base.substring(0, Math.min(view.cursorManager.getChar(), base.length()));
        String after = base.substring(Math.min(view.cursorManager.getChar(), base.length()));
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());

        view.updateLocalLinePublic(localIdx, before);
        view.linesWindow.add(localIdx + 1, after);

        view.modifiedLines.put(view.cursorManager.getLine(), before);
        view.modifiedLines.put(view.cursorManager.getLine() + 1, after);

        view.computeWidthForLinePublic(view.cursorManager.getLine(), before);
        view.computeWidthForLinePublic(view.cursorManager.getLine() + 1, after);

        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.highlightManager.clearHighlightCaches();
        view.cursorManager.setLineAndChar(view.cursorManager.getLine() + 1, 0);
        view.undoRedo.addLineCountDelta(1);

        int newLineCount = view.getLinesCount();
        if (view.lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wordWrapManager.onLineCountChanged(view);
      } else {
        int pos = Math.max(0, Math.min(view.cursorManager.getChar(), base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.highlightManager.invalidateHighlightCacheForLine(view.cursorManager.getLine());
        view.cursorManager.moveCharDelta(1);
        float newWidth =
            view.whitespaceGuideManager.measureTextWithVisualSpaces(
                view, modified, 0, modified.length(), view.paint);
        synchronized (view.lineWidthCache) {
          view.lineWidthCache.put(view.cursorManager.getLine(), newWidth);
        }
        view.currentMaxWindowLineWidth = Math.max(view.currentMaxWindowLineWidth, newWidth);
        view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, view.currentMaxWindowLineWidth);
      }
      view.invalidate();
      view.scrollManager.keepCursorVisibleHorizontally();
    }
    view.autoSuggestionManager.updateSuggestion();

    UndoRedo.EditOp op = new UndoRedo.EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    SodiumEditorView.CursorTarget insertedEnd = view.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = view.cursorManager.getLine();
    op.cursorCharAfter = view.cursorManager.getChar();
    op.timestamp = System.currentTimeMillis();
    view.recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (view.isReadOnly) return;
    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText("\n");
      return;
    }

    CursorManager.BracketPairType pairType = view.cursorManager.getCursorBracketPairType();
    if (view.isAutoBracketNewlineEnabled && pairType != CursorManager.BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (view.isAutoBracketNewlineIndentEnabled) {
        baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == CursorManager.BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = view.cursorManager.getLine() + 1;
      int targetChar = innerIndent.length();
      view.insertTextAtCursor(insertText);

      view.cursorManager.setLineAndChar(targetLine, targetChar);
      view.cursorAnimationManager.resetCursorBlink();
      view.scrollManager.keepCursorVisibleHorizontally();
      view.invalidate();
      view.autoSuggestionManager.updateSuggestion();
      return;
    }

    if (view.isAutoIndentAfterClosingBracketEnabled) {
      String ln = view.getLineTextForRender(view.cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
          int baseWidth = view.getIndentWidth(baseIndent);
          int unit = SodiumEditorView.INDENT_BLOCK_UNIT.length();
          int targetWidth = baseWidth;
          if (c == '{') {
            int firstNonSpace = getFirstNonSpaceIndex(before);
            boolean startsWithClosingParenOrBracket =
                firstNonSpace >= 0
                    && (before.charAt(firstNonSpace) == ')' || before.charAt(firstNonSpace) == ']');
            if (!startsWithClosingParenOrBracket) {
              targetWidth = baseWidth + unit;
            }
          } else {
            targetWidth = Math.max(0, baseWidth - unit);
          }
          view.insertTextAtCursor("\n" + buildIndentFromWidth(targetWidth));
          return;
        }
      }
    }

    if (view.isIndentationBlocksEnabled) {
      String ln = view.getLineTextForRender(view.cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
      String extraIndent = trimmed.endsWith(":") ? SodiumEditorView.INDENT_BLOCK_UNIT : "";
      view.insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (view.isAutoBracketNewlineIndentEnabled) {
      String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
      view.insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }

  public void deleteCharAtCursor() {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.undoRedo.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) return;

    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorManager.getChar() > 0) {
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());
        int safeStart = Math.max(0, view.cursorManager.getChar() - 1);
        String removed = base.substring(safeStart, Math.min(view.cursorManager.getChar(), base.length()));
        boolean atLineEnd = view.cursorManager.getChar() >= base.length();
        if (view.charAnimationManager.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightManager.getPaintForChar(view.cursorManager.getLine(), safeStart, base);
          view.charAnimationManager.startDeleteAnimation(view.cursorManager.getLine(), safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(view.cursorManager.getChar());
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.highlightManager.invalidateHighlightCacheForLine(view.cursorManager.getLine());
        view.cursorManager.setChar(safeStart);
        view.computeWidthForLinePublic(view.cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.invalidateLineGlobal(view.cursorManager.getLine());

        UndoRedo.EditOp op = new UndoRedo.EditOp();
        op.startLine = beforeLine;
        op.startChar = safeStart;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeStart;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      } else if (view.cursorManager.getLine() > 0) {
        int oldLineCount = view.getLinesCount();
        int prevGlobal = view.cursorManager.getLine() - 1;
        view.scrollManager.ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - view.windowStartLine;
        if (prevLocal < 0 || prevLocal >= view.linesWindow.size()) return;

        String prev = view.getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        view.updateLocalLinePublic(prevLocal, merged);
        view.modifiedLines.put(prevGlobal, merged);
        view.highlightManager.clearHighlightCaches();

        if (localIdx < view.linesWindow.size()) view.linesWindow.remove(localIdx);

        view.recalculateMaxLineWidth();
        view.cursorManager.setLineAndChar(prevGlobal, prev.length());
        view.computeWidthForLinePublic(prevGlobal, merged);
        view.undoRedo.addLineCountDelta(-1);

        int newLineCount = view.getLinesCount();
        if (view.lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wordWrapManager.onLineCountChanged(view);
        view.invalidate();

        UndoRedo.EditOp op = new UndoRedo.EditOp();
        op.startLine = prevGlobal;
        op.startChar = prev.length();
        op.endLine = beforeLine;
        op.endChar = 0;
        op.removedText = "\n";
        op.insertedText = "";
        op.insertedEndLine = prevGlobal;
        op.insertedEndChar = prev.length();
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      }
    }
    view.autoSuggestionManager.updateSuggestion();
  }

  public void deleteForwardAtCursor() {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.undoRedo.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorManager.getChar() < base.length()) {
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());
        String removed = base.substring(view.cursorManager.getChar(), Math.min(view.cursorManager.getChar() + 1, base.length()));
        boolean atLineEnd = view.cursorManager.getChar() == base.length() - 1;
        if (view.charAnimationManager.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightManager.getPaintForChar(view.cursorManager.getLine(), view.cursorManager.getChar(), base);
          view.charAnimationManager.startDeleteAnimation(view.cursorManager.getLine(), view.cursorManager.getChar(), removed, p);
        }
        String modified = base.substring(0, view.cursorManager.getChar()) + base.substring(view.cursorManager.getChar() + 1);
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.computeWidthForLinePublic(view.cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.invalidateLineGlobal(view.cursorManager.getLine());

        UndoRedo.EditOp op = new UndoRedo.EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar + 1;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = beforeChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      } else {
        int nextGlobal = view.cursorManager.getLine() + 1;
        if (view.isEof && nextGlobal >= view.windowStartLine + view.linesWindow.size()) return;

        view.scrollManager.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - view.windowStartLine;
        if (nextLocal >= 0 && nextLocal < view.linesWindow.size()) {
          String next = view.getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          view.updateLocalLinePublic(localIdx, merged);
          view.linesWindow.remove(nextLocal);
          view.modifiedLines.put(view.cursorManager.getLine(), merged);
          view.recalculateMaxLineWidth();
          view.computeWidthForLinePublic(view.cursorManager.getLine(), merged);
          view.wordWrapManager.onLineCountChanged(view);
          view.invalidate();
          view.undoRedo.addLineCountDelta(-1);

          UndoRedo.EditOp op = new UndoRedo.EditOp();
          op.startLine = beforeLine;
          op.startChar = base.length();
          op.endLine = nextGlobal;
          op.endChar = 0;
          op.removedText = "\n";
          op.insertedText = "";
          op.insertedEndLine = beforeLine;
          op.insertedEndChar = base.length();
          op.cursorLineBefore = beforeLine;
          op.cursorCharBefore = beforeChar;
          op.cursorLineAfter = view.cursorManager.getLine();
          op.cursorCharAfter = view.cursorManager.getChar();
          op.timestamp = System.currentTimeMillis();
          view.recordEdit(op);
        }
      }
    }
    view.autoSuggestionManager.updateSuggestion();
  }

  private static String rstripWhitespace(String text) {
    if (text == null || text.isEmpty()) return "";
    int end = text.length();
    while (end > 0) {
      char c = text.charAt(end - 1);
      if (c != ' ' && c != '\t') break;
      end--;
    }
    return (end == text.length()) ? text : text.substring(0, end);
  }

  private static int findPrevNonWhitespaceIndex(String text, int start) {
    if (text == null || text.isEmpty()) return -1;
    for (int i = Math.min(start, text.length() - 1); i >= 0; i--) {
      if (!Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }

  private static String buildIndentFromWidth(int width) {
    if (width <= 0) return "";
    char[] buf = new char[width];
    for (int i = 0; i < width; i++) buf[i] = ' ';
    return new String(buf);
  }

  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }

  public void replaceSelectionWithText(String insertText) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    final int opToken = view.undoRedo.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (insertText == null) insertText = "";

    if (!view.selectionManager.hasSelection()) {
      if (!insertText.isEmpty()) view.cursorManager.insertTextAtCursor(insertText);
      view.autoSuggestionManager.updateSuggestion();
      return;
    }

    int sL = view.selectionManager.selStartLine, sC = view.selectionManager.selStartChar, eL = view.selectionManager.selEndLine, eC = view.selectionManager.selEndChar;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = view.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > view.undoRedo.getUndoTextLimit()) {
        removedText = null;
      }
    }
    int removedNewlines = view.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = view.countNewlines(insertText);

    final boolean selectAllLike =
        view.selectionManager.isSelectAllActive() || view.selectionManager.isEntireFileSelected();
    view.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    if (selectAllLike) {
      synchronized (view.linesWindow) {
        view.linesWindow.clear();
        view.linesWindow.add("");
        view.windowStartLine = 0;
        view.isEof = true;
      }
      synchronized (view.modifiedLines) {
        view.modifiedLines.clear();
      }
      synchronized (view.lineWidthCache) {
        view.lineWidthCache.clear();
      }
      view.currentMaxWindowLineWidth = 0f;
      view.globalMaxLineWidth = 0f;
      view.scrollManager.maxLineWidthForScroll = 0f;
      view.scrollManager.maxTextStartXForScroll = 0f;
      view.scrollManager.maxScrollXForScroll = 0f;

      view.fileManager.setFileCleared(true);
      synchronized (view.fileManager.lineOffsetsLock) {
        view.fileManager.setLineOffsets(new long[0]);
      }
      view.fileManager.isIndexReady = false;
      view.fileManager.isIndexBuilding = false;
      view.fileManager.isIndexDisabled = false;
      view.fileManager.indexDisabledPath = null;
      view.fileManager.indexDisabledFileLength = -1L;

      view.cursorManager.setLineAndChar(0, 0);
      view.selectionManager.setSelection(0, 0, 0, 0, false);
      view.scrollManager.scrollY = 0;
      view.scrollManager.scrollX = 0;
      view.clearSelectionStateAfterDeletePublic();

      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (view.linesWindow) {
          view.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            view.linesWindow.add(i, newLines[i]);
          }
        }
        SodiumEditorView.CursorTarget newPos = view.computeCursorAfterInsert(0, 0, insertText);
        view.cursorManager.setLineAndChar(newPos.line, newPos.ch);
      }

      view.wordWrapManager.onLineCountChanged(view);
      view.recalculateMaxLineWidth();
    } else {
      view.fileManager.rewriteReplaceRangeAsync(opToken, view.fileManager.getSourceFile(), sL, sC, eL, eC, insertText, view.computeCursorAfterInsert(sL, sC, insertText), false);
    }

    view.undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
    view.recordReplaceSelectionEditPublic(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
    view.autoSuggestionManager.updateSuggestion();
  }

  public void handleAutoPairing(String text) {
    if (!view.isAutoPairingEnabled || text == null || text.length() == 0 || text.length() >= 100) return;

    char c = text.charAt(text.length() - 1);
    String closing = null;
    if (c == '(') closing = ")";
    else if (c == '{') closing = "}";
    else if (c == '[') closing = "]";
    else if (c == '"') closing = "\"";
    else if (c == '\'') closing = "'";
    else if (c == '`') closing = "`";
    else if (c == '*') {
      if (view.cursorManager.getChar() >= 2) {
        String ln = view.getLineTextForRender(view.cursorManager.getLine());
        if (ln != null && ln.length() >= view.cursorManager.getChar() && ln.charAt(view.cursorManager.getChar() - 2) == '/') {
          closing = "*/";
        }
      }
    }

    if (closing != null) {
      view.cursorManager.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        view.cursorManager.moveCursorLeft();
      }
    }
  }
}
