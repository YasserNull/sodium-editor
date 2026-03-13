package com.yn.sodiumeditor.Input.events;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnTouch handles all touch event logic for SodiumEditor.
 * This includes:
 * - Touch gesture handling
 * - Handle dragging (selection handles)
 * - Scroll bar dragging
 * - Popup menu interaction
 * - Suggestion tap handling
 * - Line number selection
 */
public class OnTouch {

  private final SodiumEditor sodiumeditor;

  public OnTouch(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Handle touch event
   */
  public boolean onTouchEvent(MotionEvent event) {
    if (sodiumeditor.isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    if (action == MotionEvent.ACTION_DOWN) {
      sodiumeditor.multiTouchActive = false;
      sodiumeditor.hadMultiTouch = false;
      // Stop fling when user touches screen
      if (!sodiumeditor.scroll.scroller.isFinished()) sodiumeditor.scroll.scroller.abortAnimation();
      sodiumeditor.scroll.scroller.abortAnimation();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      sodiumeditor.multiTouchActive = true;
      sodiumeditor.hadMultiTouch = true;
      sodiumeditor.zoom.mJustFinishedScale = true;
      sodiumeditor.pointerDown = false;
      sodiumeditor.movedSinceDown = false;
      sodiumeditor.draggingHandle = 0;
      sodiumeditor.scroll.dragMaxScrollX = -1f;
      sodiumeditor.selection.selecting = false;
      sodiumeditor.selection.isLineNumberSelecting = false;
      sodiumeditor.selection.lineNumberSelectAnchorLine = -1;
      sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.autoScrollRunnable);
      if (!sodiumeditor.scroll.scroller.isFinished()) {
        sodiumeditor.scroll.scroller.computeScrollOffset();
        sodiumeditor.scroll.scrollX = sodiumeditor.scroll.scroller.getCurrX();
        sodiumeditor.scroll.scrollY = sodiumeditor.scroll.scroller.getCurrY();
        sodiumeditor.scroll.scroller.abortAnimation();
      }
      sodiumeditor.scroll.cancelFlingStopAnimation();
    }

    if (action == MotionEvent.ACTION_POINTER_UP) {
      if (pointerCount - 1 <= 1) {
        sodiumeditor.multiTouchActive = false;
        sodiumeditor.zoom.mJustFinishedScale = true;
        sodiumeditor.scroll.dragMaxScrollX = -1f;
      }
    }

    if (sodiumeditor.zoom.isZoomEnabled) {
      sodiumeditor.scaleGestureDetector.onTouchEvent(event);
    }

    if (sodiumeditor.scaleGestureDetector.isInProgress()
        || sodiumeditor.multiTouchActive
        || pointerCount > 1
        || sodiumeditor.zoom.isScaling
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      return true;
    }

    if (sodiumeditor.hadMultiTouch && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      sodiumeditor.pointerDown = false;
      sodiumeditor.draggingHandle = 0;
      sodiumeditor.selection.selecting = false;
      sodiumeditor.selection.isLineNumberSelecting = false;
      sodiumeditor.selection.lineNumberSelectAnchorLine = -1;
      sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.autoScrollRunnable);
      sodiumeditor.scroll.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();
    sodiumeditor.lastTouchX = ex;
    sodiumeditor.lastTouchY = ey;

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        sodiumeditor.caret.resetBlink();
        if (!sodiumeditor.isFocused()) sodiumeditor.requestFocus();
        sodiumeditor.pointerDown = true;
        sodiumeditor.downX = ex;
        sodiumeditor.downY = ey;
        sodiumeditor.movedSinceDown = false;
        sodiumeditor.suggestionAcceptedThisTouch = false; // Reset flag for new touch sequence
        sodiumeditor.scroll.dragMaxScrollX = sodiumeditor.isWordWrapEnabled ? -1f : sodiumeditor.scroll.getMaxScrollXForClamp();

        sodiumeditor.scroll.showScrollBar();
        if (sodiumeditor.scroll.scrollBarEnabled) {
          float maxScroll = sodiumeditor.scroll.getMaxScrollYForClamp();
          if (maxScroll > 0f && sodiumeditor.scroll.scrollBarThumbRect.contains(ex, ey)) {
            sodiumeditor.scroll.draggingScrollBar = true;
            sodiumeditor.scroll.scrollBarDragOffset = ey - sodiumeditor.scroll.scrollBarThumbRect.top;
            sodiumeditor.scroll.showScrollBar();
            return true;
          }
        }

        if (sodiumeditor.showPopup) {
          int hitAction = sodiumeditor.getPopupActionAt(ex, ey);
          if (hitAction != 0) {
            sodiumeditor.popupPressedAction = hitAction;
            sodiumeditor.startPopupRipple(hitAction, ex, ey);
            return true;
          }
        }

        if (!sodiumeditor.scroll.scroller.isFinished()) {
          sodiumeditor.scroll.scroller.computeScrollOffset();
          float targetX = sodiumeditor.scroll.scroller.getCurrX();
          float targetY = sodiumeditor.scroll.scroller.getCurrY();
          sodiumeditor.scroll.scroller.abortAnimation();
          sodiumeditor.scroll.startFlingStopAnimation(targetX, targetY);
        } else {
          sodiumeditor.scroll.cancelFlingStopAnimation();
        }

        // FIX: Use getTextStartX() to correctly calculate touch coordinates relative to the text
        // area.
        float gx = ex + sodiumeditor.getEffectiveScrollX() - sodiumeditor.getTextStartX();
        float gy = ey + sodiumeditor.scroll.scrollY - sodiumeditor.getHitTestBaseY();
        if (sodiumeditor.selection.hasSelection && sodiumeditor.leftHandleRect.contains(gx, gy)) {
          sodiumeditor.draggingHandle = 1;
          return true;
        } else if (sodiumeditor.selection.hasSelection && sodiumeditor.rightHandleRect.contains(gx, gy)) {
          sodiumeditor.draggingHandle = 2;
          return true;
        } else if (sodiumeditor.isFocused() && !sodiumeditor.selection.hasSelection && sodiumeditor.cursorHandleRect.contains(gx, gy)) {
          sodiumeditor.draggingHandle = 3;
          return true;
        }

        sodiumeditor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (sodiumeditor.scroll.flingStopAnimator != null) sodiumeditor.scroll.cancelFlingStopAnimation();
        if (Math.abs(ex - sodiumeditor.downX) > sodiumeditor.touchSlop || Math.abs(ey - sodiumeditor.downY) > sodiumeditor.touchSlop)
          sodiumeditor.movedSinceDown = true;

        if (sodiumeditor.scroll.draggingScrollBar) {
          float maxScroll = sodiumeditor.scroll.getMaxScrollYForClamp();
          if (maxScroll > 0f) {
            float h = sodiumeditor.getHeight();
            float trackHeight = h;
            float contentHeight = maxScroll + h;
            float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
            if (thumbHeight < sodiumeditor.scroll.scrollBarMinThumbPx) thumbHeight = sodiumeditor.scroll.scrollBarMinThumbPx;
            if (thumbHeight > trackHeight) thumbHeight = trackHeight;
            float thumbRange = Math.max(1f, trackHeight - thumbHeight);
            float targetTop = Math.max(0f, Math.min(trackHeight - thumbHeight, ey - sodiumeditor.scroll.scrollBarDragOffset));
            sodiumeditor.scroll.scrollY = (targetTop / thumbRange) * maxScroll;
            sodiumeditor.scroll.clampScrollY();
            sodiumeditor.invalidate();
          }
          sodiumeditor.scroll.showScrollBar();
          return true;
        }

        if (sodiumeditor.popupPressedAction != 0) {
          RectF r = sodiumeditor.getPopupRectForAction(sodiumeditor.popupPressedAction);
          if (!r.contains(ex, ey)) {
            sodiumeditor.popupPressedAction = 0;
            sodiumeditor.cancelPopupRipple();
          }
          return true;
        }

        if (sodiumeditor.selection.isLineNumberSelecting) {
          float y = ey + sodiumeditor.scroll.scrollY;
          int line = sodiumeditor.getGlobalLineForY(y);
          sodiumeditor.updateLineNumberSelection(line);
          return true;
        }

        if (sodiumeditor.draggingHandle != 0) {
          updateHandlePosition(ex, ey);
          if (sodiumeditor.draggingHandle == 1 || sodiumeditor.draggingHandle == 2) sodiumeditor.showPopupAtSelection();

          float scrollMargin = sodiumeditor.lineHeight * 2f;
          float scrollSpeed = Math.max(4f, sodiumeditor.lineHeight * 0.35f);
          sodiumeditor.autoScrollY = 0;
          sodiumeditor.autoScrollX = 0;
          if (ey < scrollMargin) sodiumeditor.autoScrollY = -scrollSpeed;
          else if (ey > (sodiumeditor.getHeight() - sodiumeditor.keyboardHeight) - scrollMargin) sodiumeditor.autoScrollY = scrollSpeed;
          if (ex < scrollMargin) sodiumeditor.autoScrollX = -scrollSpeed;
          else if (ex > sodiumeditor.getWidth() - scrollMargin) sodiumeditor.autoScrollX = scrollSpeed;
          if (sodiumeditor.isRtl && !sodiumeditor.isWordWrapEnabled) sodiumeditor.autoScrollX = -sodiumeditor.autoScrollX;

          // Prevent horizontal auto-scroll when the handle is already at the line boundary.
          if (sodiumeditor.autoScrollX > 0 && sodiumeditor.lastDragAtLineEnd) sodiumeditor.autoScrollX = 0;
          if (sodiumeditor.autoScrollX < 0 && sodiumeditor.lastDragAtLineStart) sodiumeditor.autoScrollX = 0;

          if (sodiumeditor.autoScrollX != 0 || sodiumeditor.autoScrollY != 0) sodiumeditor.mainHandler.post(sodiumeditor.autoScrollRunnable);
          else sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.autoScrollRunnable);

          sodiumeditor.invalidate();
          return true;
        }

        sodiumeditor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.autoScrollRunnable);
        sodiumeditor.scroll.dragMaxScrollX = -1f;

        if (sodiumeditor.scroll.draggingScrollBar) {
          sodiumeditor.scroll.draggingScrollBar = false;
          sodiumeditor.scroll.showScrollBar();
          return true;
        }

        if (sodiumeditor.popupPressedAction != 0) {
          int actionForTap = sodiumeditor.popupPressedAction;
          sodiumeditor.popupPressedAction = 0;
          RectF r = sodiumeditor.getPopupRectForAction(actionForTap);
          if (sodiumeditor.showPopup && r.contains(ex, ey)) {
            if (sodiumeditor.isReadOnly
                && (actionForTap == SodiumEditor.POPUP_ACTION_CUT
                    || actionForTap == SodiumEditor.POPUP_ACTION_PASTE
                    || actionForTap == SodiumEditor.POPUP_ACTION_DELETE)) {
              sodiumeditor.hidePopup();
              return true;
            }
            if (actionForTap == SodiumEditor.POPUP_ACTION_COPY) {
              sodiumeditor.copySelectionToClipboard();
              sodiumeditor.selection.hasSelection = false;
              sodiumeditor.selection.isSelectAllActive = false;
              sodiumeditor.hidePopup();
              sodiumeditor.invalidate();
            } else if (actionForTap == SodiumEditor.POPUP_ACTION_CUT) {
              sodiumeditor.cutSelectionToClipboard();
            } else if (actionForTap == SodiumEditor.POPUP_ACTION_PASTE) {
              sodiumeditor.pasteFromClipboard();
            } else if (actionForTap == SodiumEditor.POPUP_ACTION_DELETE) {
              sodiumeditor.deleteSelection();
            } else if (actionForTap == SodiumEditor.POPUP_ACTION_SELECT_ALL) {
              if (!sodiumeditor.selection.isSelectAllActive) sodiumeditor.selection.selectAll();
              else sodiumeditor.hidePopup();
            }
          } else {
            sodiumeditor.cancelPopupRipple();
          }
          if (sodiumeditor.popupRippleHoldActive) {
            sodiumeditor.cancelPopupRipple();
          }
          return true;
        }

        if (sodiumeditor.selection.isLineNumberSelecting) {
          sodiumeditor.selection.isLineNumberSelecting = false;
          sodiumeditor.selection.lineNumberSelectAnchorLine = -1;
          sodiumeditor.selection.selecting = false;
          sodiumeditor.pointerDown = false;
          if (sodiumeditor.selection.hasSelection) sodiumeditor.showPopupAtSelection();
          return true;
        }

        // --- Check for tap on suggestion FIRST and consume if it's a clean tap ---
        SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(event.getX(), event.getY(), null);
        int line = target.line;

        // Get line text safely
        String ln = sodiumeditor.getLineFromWindowLocal(line - sodiumeditor.windowStartLine);
        if (ln == null) ln = sodiumeditor.getLineTextForRender(line);

        int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

        // Check if the long press was on an "empty" area
        boolean isEmptyArea = false;
        if (ln.isEmpty()) {
          isEmptyArea = true;
        } else if (charIndex >= ln.length()) {
          isEmptyArea = true; // Tapped on empty space after the text on a line
        }

        boolean allowSuggestionTap =
            sodiumeditor.activeSuggestionIsPath ? sodiumeditor.isAutoPathCompletionEnabled : sodiumeditor.isAutoCompletionEnabled;
        if (!sodiumeditor.movedSinceDown
            && allowSuggestionTap
            && sodiumeditor.activeSuggestion != null
            && !sodiumeditor.activeSuggestionRect.isEmpty()) {

          if (sodiumeditor.activeSuggestionRect.contains(ex, ey)) {
            Log.d(
                "SodiumEditor",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            sodiumeditor.acceptAutoCompletion(); // Call synchronously
            sodiumeditor.pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          } else if (isEmptyArea && line == sodiumeditor.cursor.cursorLine) {
            Log.d(
                "SodiumEditor",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            sodiumeditor.acceptAutoCompletion(); // Call synchronously
            sodiumeditor.pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          }
        }
        // --- END Check ---

        sodiumeditor.pointerDown = false;
        // sodiumeditor.clearActiveSuggestion();

        if (sodiumeditor.draggingHandle != 0) {
          if (sodiumeditor.draggingHandle == 1 || sodiumeditor.draggingHandle == 2) sodiumeditor.showPopupAtSelection();
          sodiumeditor.draggingHandle = 0;
          sodiumeditor.invalidate();
          return true;
        }

        if (sodiumeditor.movedSinceDown && sodiumeditor.scroll.scroller.isFinished()) { // Just finished a scroll/drag
          if (sodiumeditor.selection.hasSelection) sodiumeditor.showPopupAtSelection();
          sodiumeditor.restartInput(); // Sync IME state
          Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (sodiumeditor.isWordWrapEnabled && sodiumeditor.wrapPrefixRebuildPending && !sodiumeditor.wrapPrefixBuilding) {
            sodiumeditor.wrapPrefixRebuildPending = false;
            sodiumeditor.scheduleWrapPrefixRebuildUpToWindow();
          }
        }

        Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Passing to GestureDetector.ACTION_UP.");
        sodiumeditor.scroll.gestureDetector.onTouchEvent(event);
        if (sodiumeditor.selection.hasSelection && !sodiumeditor.showPopup) {
          sodiumeditor.showPopupAtSelection();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.autoScrollRunnable);
        sodiumeditor.pointerDown = false;
        sodiumeditor.draggingHandle = 0;
        sodiumeditor.selection.selecting = false;
        sodiumeditor.selection.isLineNumberSelecting = false;
        sodiumeditor.selection.lineNumberSelectAnchorLine = -1;
        sodiumeditor.popupPressedAction = 0;
        sodiumeditor.cancelPopupRipple();
        sodiumeditor.clearActiveSuggestion(); // Clear suggestion on touch cancel
        sodiumeditor.scroll.dragMaxScrollX = -1f;
        sodiumeditor.scroll.draggingScrollBar = false;
        if (sodiumeditor.scroll.scrollBarFadeEnabled) {
          sodiumeditor.mainHandler.removeCallbacks(sodiumeditor.scroll.scrollBarHideRunnable);
        }
        Log.d("SodiumEditor", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        sodiumeditor.scroll.gestureDetector.onTouchEvent(event);
        return true;
    }

    return sodiumeditor.onTouchEventSuper(event);
  }

  /**
   * Update handle position during drag
   */
  public void updateHandlePosition(float touchX, float touchY) {
    // FIX: Any manual adjustment of the selection handles must deactivate ALL "Select All" flags.
    // This prevents the editor from deleting all content when the user has reduced the selection.
    if (sodiumeditor.selection.isSelectAllActive || sodiumeditor.selection.isEntireFileSelected) {
      sodiumeditor.selection.isSelectAllActive = false;
      sodiumeditor.selection.isEntireFileSelected = false;
      // The popup needs to be redrawn as "Copy" and "Cut" might become available again.
      sodiumeditor.showPopupAtSelection();
    }

    // Correctly calculate X coordinate relative to the text area, accounting for the gutter.
    SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(touchX, touchY, null);
    int line = target.line;

    if (sodiumeditor.isEof) {
      int lastValidLine = sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1;
      if (line > lastValidLine) line = lastValidLine;
    }

    sodiumeditor.ensureLineInWindow(line, true);
    String ln = sodiumeditor.getLineTextForRender(line);
    int clamped = Math.max(0, Math.min(target.ch, ln.length()));
    sodiumeditor.lastDragAtLineStart = clamped == 0;
    sodiumeditor.lastDragAtLineEnd = clamped == ln.length();

    if (sodiumeditor.draggingHandle == 1) {
      if (sodiumeditor.isRtl) {
        sodiumeditor.selection.selEndLine = line;
        sodiumeditor.selection.selEndChar = clamped;
      } else {
        sodiumeditor.selection.selStartLine = line;
        sodiumeditor.selection.selStartChar = clamped;
      }
    } else if (sodiumeditor.draggingHandle == 2) {
      if (sodiumeditor.isRtl) {
        sodiumeditor.selection.selStartLine = line;
        sodiumeditor.selection.selStartChar = clamped;
      } else {
        sodiumeditor.selection.selEndLine = line;
        sodiumeditor.selection.selEndChar = clamped;
      }
    } else if (sodiumeditor.draggingHandle == 3) {
      sodiumeditor.cursor.cursorLine = line;
      sodiumeditor.cursor.cursorChar = clamped;
      sodiumeditor.keepCursorVisibleHorizontally();
    }
  }

  /**
   * Draw selection segment
   */
  public void drawSelectionSegment(
      Canvas canvas,
      float left,
      float top,
      float right,
      float bottom,
      boolean roundTopLeft,
      boolean roundTopRight,
      boolean roundBottomRight,
      boolean roundBottomLeft,
      Paint paint) {
    if (right <= left || bottom <= top) return;

    float radius = Math.min(12f, Math.max(2f, sodiumeditor.lineHeight * 0.22f));
    // Keep vertical edges flush between lines to avoid "seam" lines when selecting multiple lines.
    float insetX = 0.5f;
    sodiumeditor.selection.selectionRectTmp.set(left + insetX, top, right - insetX, bottom);

    if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
      canvas.drawRect(sodiumeditor.selection.selectionRectTmp, paint);
      return;
    }

    float tl = roundTopLeft ? radius : 0f;
    float tr = roundTopRight ? radius : 0f;
    float br = roundBottomRight ? radius : 0f;
    float bl = roundBottomLeft ? radius : 0f;

    sodiumeditor.selection.selectionRadiiTmp[0] = tl;
    sodiumeditor.selection.selectionRadiiTmp[1] = tl;
    sodiumeditor.selection.selectionRadiiTmp[2] = tr;
    sodiumeditor.selection.selectionRadiiTmp[3] = tr;
    sodiumeditor.selection.selectionRadiiTmp[4] = br;
    sodiumeditor.selection.selectionRadiiTmp[5] = br;
    sodiumeditor.selection.selectionRadiiTmp[6] = bl;
    sodiumeditor.selection.selectionRadiiTmp[7] = bl;

    sodiumeditor.selection.selectionPathTmp.reset();
    sodiumeditor.selection.selectionPathTmp.addRoundRect(sodiumeditor.selection.selectionRectTmp, sodiumeditor.selection.selectionRadiiTmp, Path.Direction.CW);
    canvas.drawPath(sodiumeditor.selection.selectionPathTmp, paint);
  }
}
