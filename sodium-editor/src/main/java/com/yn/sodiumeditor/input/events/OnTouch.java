package com.yn.sodiumeditor.input.events;

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
import com.yn.sodiumeditor.core.CodeFold;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.core.selection.Popup;
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

  private final SodiumEditor editor;

  public OnTouch(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handle touch event
   */
  public boolean onTouchEvent(MotionEvent event) {
    if (editor.isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    if (action == MotionEvent.ACTION_DOWN) {
      editor.multiTouchActive = false;
      editor.hadMultiTouch = false;
      // Stop fling when user touches screen
      if (!editor.scroll.scroller.isFinished()) editor.scroll.scroller.abortAnimation();
      editor.scroll.scroller.abortAnimation();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      editor.multiTouchActive = true;
      editor.hadMultiTouch = true;
      editor.zoom.mJustFinishedScale = true;
      editor.pointerDown = false;
      editor.movedSinceDown = false;
      editor.selectionHandles.draggingHandle = 0;
      editor.scroll.dragMaxScrollX = -1f;
      editor.selection.selecting = false;
      editor.selection.isLineNumberSelecting = false;
      editor.selection.lineNumberSelectAnchorLine = -1;
      editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);
      if (!editor.scroll.scroller.isFinished()) {
        editor.scroll.scroller.computeScrollOffset();
        editor.scroll.scrollX = editor.scroll.scroller.getCurrX();
        editor.scroll.scrollY = editor.scroll.scroller.getCurrY();
        editor.scroll.scroller.abortAnimation();
      }
      editor.scroll.cancelFlingStopAnimation();
    }

    if (action == MotionEvent.ACTION_POINTER_UP) {
      if (pointerCount - 1 <= 1) {
        editor.multiTouchActive = false;
        editor.zoom.mJustFinishedScale = true;
        editor.scroll.dragMaxScrollX = -1f;
      }
    }

    if (editor.zoom.isZoomEnabled) {
      editor.scaleGestureDetector.onTouchEvent(event);
    }

    if (editor.scaleGestureDetector.isInProgress()
        || editor.multiTouchActive
        || pointerCount > 1
        || editor.zoom.isScaling
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
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

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        editor.caret.resetBlink();
        if (!editor.isFocused()) editor.requestFocus();
        editor.pointerDown = true;
        editor.downX = ex;
        editor.downY = ey;
        editor.movedSinceDown = false;
        editor.autoCompletion.suggestionAcceptedThisTouch = false; // Reset flag for new touch sequence
        editor.scroll.dragMaxScrollX = editor.wordWrap.isWordWrapEnabled ? -1f : editor.scroll.getMaxScrollXForClamp();

        editor.scroll.showScrollBar();
        if (editor.scroll.scrollBarEnabled) {
          float maxScroll = editor.scroll.getMaxScrollYForClamp();
          if (maxScroll > 0f && editor.scroll.scrollBarThumbRect.contains(ex, ey)) {
            editor.scroll.draggingScrollBar = true;
            editor.scroll.scrollBarDragOffset = ey - editor.scroll.scrollBarThumbRect.top;
            editor.scroll.showScrollBar();
            return true;
          }
        }

        if (editor.popup.showPopup) {
          int hitAction = editor.popup.getPopupActionAt(ex, ey);
          if (hitAction != 0) {
            editor.popup.popupPressedAction = hitAction;
            editor.popup.startPopupRipple(hitAction, ex, ey);
            return true;
          }
        }

        if (!editor.scroll.scroller.isFinished()) {
          editor.scroll.scroller.computeScrollOffset();
          float targetX = editor.scroll.scroller.getCurrX();
          float targetY = editor.scroll.scroller.getCurrY();
          editor.scroll.scroller.abortAnimation();
          editor.scroll.startFlingStopAnimation(targetX, targetY);
        } else {
          editor.scroll.cancelFlingStopAnimation();
        }

        // FIX: Use getTextStartX() to correctly calculate touch coordinates relative to the text
        // area.
        float gx = ex + editor.getEffectiveScrollX() - editor.getTextStartX();
        float gy = ey + editor.scroll.scrollY - editor.textRender.getHitTestBaseY();
        if (editor.selection.hasSelection && editor.selectionHandles.leftHandleRect.contains(gx, gy)) {
          editor.selectionHandles.draggingHandle = 1;
          return true;
        } else if (editor.selection.hasSelection && editor.selectionHandles.rightHandleRect.contains(gx, gy)) {
          editor.selectionHandles.draggingHandle = 2;
          return true;
        } else if (editor.isFocused() && !editor.selection.hasSelection && editor.cursorHandle.cursorHandleRect.contains(gx, gy)) {
          editor.selectionHandles.draggingHandle = 3;
          return true;
        }

        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (editor.scroll.flingStopAnimator != null) editor.scroll.cancelFlingStopAnimation();
        if (Math.abs(ex - editor.downX) > editor.touchSlop || Math.abs(ey - editor.downY) > editor.touchSlop)
          editor.movedSinceDown = true;

        if (editor.scroll.draggingScrollBar) {
          float maxScroll = editor.scroll.getMaxScrollYForClamp();
          if (maxScroll > 0f) {
            float h = editor.getHeight();
            float trackHeight = h;
            float contentHeight = maxScroll + h;
            float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
            if (thumbHeight < editor.scroll.scrollBarMinThumbPx) thumbHeight = editor.scroll.scrollBarMinThumbPx;
            if (thumbHeight > trackHeight) thumbHeight = trackHeight;
            float thumbRange = Math.max(1f, trackHeight - thumbHeight);
            float targetTop = Math.max(0f, Math.min(trackHeight - thumbHeight, ey - editor.scroll.scrollBarDragOffset));
            editor.scroll.scrollY = (targetTop / thumbRange) * maxScroll;
            editor.scroll.clampScrollY();
            editor.invalidate();
          }
          editor.scroll.showScrollBar();
          return true;
        }

        if (editor.popup.popupPressedAction != 0) {
          RectF r = editor.popup.getPopupRectForAction(editor.popup.popupPressedAction);
          if (!r.contains(ex, ey)) {
            editor.popup.popupPressedAction = 0;
            editor.popup.cancelPopupRipple();
          }
          return true;
        }

        if (editor.selection.isLineNumberSelecting) {
          float y = ey + editor.scroll.scrollY;
          int line = editor.getGlobalLineForY(y);
          editor.lineNumber.updateLineNumberSelection(line);
          return true;
        }

        if (editor.selection.longPressSelecting
            && editor.selectionHandles.draggingHandle == 0
            && !editor.selection.isLineNumberSelecting) {
          if (Math.abs(ex - editor.downX) > editor.touchSlop || Math.abs(ey - editor.downY) > editor.touchSlop) {
            EditOperators.CursorTarget target = editor.getCursorTargetForPosition(ex, ey, null);
            int line = target.line;
            editor.fileIO.ensureLineInWindow(line, true);
            String ln = editor.getLineTextForRender(line);
            int clamped = Math.max(0, Math.min(target.ch, ln.length()));
            if (editor.codeFold.isCodeFoldingEnabled) {
              CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
              if (range != null && range.collapsed) {
                float[] bounds = new float[2];
                if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
                  float x = editor.viewToTextX(ex);
                  if (editor.wordWrap.isWordWrapEnabled) {
                    int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
                    int seg =
                        editor.wordWrap.getWrapSegmentIndexForChar(
                            starts, Math.max(0, Math.min(target.ch, ln.length())));
                    int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
                    x = x + editor.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
                  }
                  float xStart = bounds[0];
                  float placeholderWidth =
                      Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
                  float closeStart = xStart + placeholderWidth;
                  String endLineText = editor.getLineTextForRender(range.endLine);
                  float closeWidth = editor.textRender.paint.measureText(String.valueOf(range.closeChar));
                  int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
                  int suffixStart =
                      range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1)
                          : (closeIdx >= 0 ? closeIdx + 1 : -1);
                  if (x <= xStart) {
                    clamped = Math.max(0, range.openCharIndex);
                  } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
                    clamped = (closeIdx >= 0) ? (closeIdx + 1) : 0;
                  } else {
                    float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
                    int idx =
                        editor.getCharIndexForXInRange(
                            endLineText,
                            range.endLine,
                            suffixStart,
                            endLineText.length(),
                            xSuffix);
                    clamped = Math.max(suffixStart, Math.min(idx, endLineText.length()));
                  }
                }
              }
            }
            editor.selection.updateLongPressSelection(line, clamped);
            editor.popup.hidePopup();
            editor.invalidate();
            return true;
          }
        }

        if (editor.selectionHandles.draggingHandle != 0) {
          updateHandlePosition(ex, ey);
          if (editor.selectionHandles.draggingHandle == 1 || editor.selectionHandles.draggingHandle == 2) editor.popup.showPopupAtSelection();

          float scrollMargin = editor.textRender.lineHeight * 2f;
          float scrollSpeed = Math.max(4f, editor.textRender.lineHeight * 0.35f);
          editor.autoScrollY = 0;
          editor.autoScrollX = 0;
          if (ey < scrollMargin) editor.autoScrollY = -scrollSpeed;
          else if (ey > (editor.getHeight() - editor.keyboardHeight) - scrollMargin) editor.autoScrollY = scrollSpeed;
          if (ex < scrollMargin) editor.autoScrollX = -scrollSpeed;
          else if (ex > editor.getWidth() - scrollMargin) editor.autoScrollX = scrollSpeed;
          if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) editor.autoScrollX = -editor.autoScrollX;

          // Prevent horizontal auto-scroll when the handle is already at the line boundary.
          if (editor.autoScrollX > 0 && editor.selectionHandles.lastDragAtLineEnd) editor.autoScrollX = 0;
          if (editor.autoScrollX < 0 && editor.selectionHandles.lastDragAtLineStart) editor.autoScrollX = 0;

          if (editor.autoScrollX != 0 || editor.autoScrollY != 0) editor.caret.mainHandler.post(editor.autoScrollRunnable);
          else editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);

          editor.invalidate();
          return true;
        }

        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);
        editor.scroll.dragMaxScrollX = -1f;
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();

        if (editor.scroll.draggingScrollBar) {
          editor.scroll.draggingScrollBar = false;
          editor.scroll.showScrollBar();
          return true;
        }

        if (editor.popup.popupPressedAction != 0) {
          int actionForTap = editor.popup.popupPressedAction;
          editor.popup.popupPressedAction = 0;
          RectF r = editor.popup.getPopupRectForAction(actionForTap);
          if (editor.popup.showPopup && r.contains(ex, ey)) {
            if (editor.isReadOnly
                && (actionForTap == Popup.POPUP_ACTION_CUT
                    || actionForTap == Popup.POPUP_ACTION_PASTE
                    || actionForTap == Popup.POPUP_ACTION_DELETE)) {
              editor.popup.hidePopup();
              return true;
            }
            if (actionForTap == Popup.POPUP_ACTION_COPY) {
              editor.selection.copyOrCutSelection(false);
              editor.selection.hasSelection = false;
              editor.selection.isSelectAllActive = false;
              editor.popup.hidePopup();
              editor.invalidate();
            } else if (actionForTap == Popup.POPUP_ACTION_CUT) {
              editor.selection.copyOrCutSelection(true);
            } else if (actionForTap == Popup.POPUP_ACTION_PASTE) {
              editor.pasteFromClipboard();
            } else if (actionForTap == Popup.POPUP_ACTION_DELETE) {
              editor.selection.deleteSelection();
            } else if (actionForTap == Popup.POPUP_ACTION_SELECT_ALL) {
              if (!editor.selection.isSelectAllActive) editor.selection.selectAll();
              else editor.popup.hidePopup();
            }
          } else {
            editor.popup.cancelPopupRipple();
          }
          if (editor.popup.popupRippleHoldActive) {
            editor.popup.cancelPopupRipple();
          }
          return true;
        }

        if (editor.selection.isLineNumberSelecting) {
          editor.selection.isLineNumberSelecting = false;
          editor.selection.lineNumberSelectAnchorLine = -1;
          editor.selection.selecting = false;
          editor.pointerDown = false;
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          return true;
        }

        if (editor.selection.longPressSelecting) {
          editor.selection.endLongPressSelection();
          if (editor.selection.hasSelection) {
            editor.popup.showPopupAtSelection();
          }
        }

        // --- Check for tap on suggestion FIRST and consume if it's a clean tap ---
        EditOperators.CursorTarget target = editor.getCursorTargetForPosition(event.getX(), event.getY(), null);
        int line = target.line;

        // Get line text safely
        String ln = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
        if (ln == null) ln = editor.getLineTextForRender(line);

        int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

        // Check if the long press was on an "empty" area
        boolean isEmptyArea = false;
        if (ln.isEmpty()) {
          isEmptyArea = true;
        } else if (charIndex >= ln.length()) {
          isEmptyArea = true; // Tapped on empty space after the text on a line
        }

        boolean allowSuggestionTap =
            editor.autoCompletion.activeSuggestionIsPath ? editor.autoPathCompletion.isAutoPathCompletionEnabled : editor.autoCompletion.isAutoCompletionEnabled;
        if (!editor.movedSinceDown
            && allowSuggestionTap
            && editor.autoCompletion.activeSuggestion != null
            && !editor.autoCompletion.activeSuggestionRect.isEmpty()) {

          if (editor.autoCompletion.activeSuggestionRect.contains(ex, ey)) {
            Log.d(
                "SodiumEditor",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            editor.autoCompletion.acceptAutoCompletion(); // Call synchronously
            editor.pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          } else if (isEmptyArea && line == editor.cursor.cursorLine) {
            Log.d(
                "SodiumEditor",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            editor.autoCompletion.acceptAutoCompletion(); // Call synchronously
            editor.pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          }
        }
        // --- END Check ---

        editor.pointerDown = false;
        // editor.autoCompletion.clearActiveSuggestion();

        if (editor.selectionHandles.draggingHandle != 0) {
          if (editor.selectionHandles.draggingHandle == 1 || editor.selectionHandles.draggingHandle == 2) editor.popup.showPopupAtSelection();
          editor.selectionHandles.draggingHandle = 0;
          editor.invalidate();
          return true;
        }

        if (editor.movedSinceDown && editor.scroll.scroller.isFinished()) { // Just finished a scroll/drag
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
          editor.restartInput(); // Sync IME state
          Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixRebuildPending && !editor.wordWrap.wrapPrefixBuilding) {
            editor.wordWrap.wrapPrefixRebuildPending = false;
            editor.wordWrap.scheduleWrapPrefixRebuildUpToWindow();
          }
        }

        Log.d("SodiumEditor", "onTouchEvent.ACTION_UP: Passing to GestureDetector.ACTION_UP.");
        editor.scroll.gestureDetector.onTouchEvent(event);
        if (editor.selection.hasSelection && !editor.popup.showPopup) {
          editor.popup.showPopupAtSelection();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        editor.caret.mainHandler.removeCallbacks(editor.autoScrollRunnable);
        editor.pointerDown = false;
        editor.selectionHandles.draggingHandle = 0;
        editor.selection.selecting = false;
        editor.selection.isLineNumberSelecting = false;
        editor.selection.lineNumberSelectAnchorLine = -1;
        editor.popup.popupPressedAction = 0;
        editor.popup.cancelPopupRipple();
        editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion on touch cancel
        editor.scroll.dragMaxScrollX = -1f;
        editor.scroll.draggingScrollBar = false;
        editor.scroll.edge.releaseAll();
        editor.scroll.stretch.releaseStretch();
        if (editor.scroll.scrollBarFadeEnabled) {
          editor.caret.mainHandler.removeCallbacks(editor.scroll.scrollBarHideRunnable);
        }
        Log.d("SodiumEditor", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        editor.scroll.gestureDetector.onTouchEvent(event);
        return true;
    }

    return editor.callSuperOnTouchEvent(event);
  }

  /**
   * Update handle position during drag
   */
  public void updateHandlePosition(float touchX, float touchY) {
    // FIX: Any manual adjustment of the selection handles must deactivate ALL "Select All" flags.
    // This prevents the editor from deleting all content when the user has reduced the selection.
    if (editor.selection.isSelectAllActive || editor.selection.isEntireFileSelected) {
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      // The popup needs to be redrawn as "Copy" and "Cut" might become available again.
      editor.popup.showPopupAtSelection();
    }

    // Correctly calculate X coordinate relative to the text area, accounting for the gutter.
    EditOperators.CursorTarget target = editor.getCursorTargetForPosition(touchX, touchY, null);
    int line = target.line;

    if (editor.fileIO.isEof) {
      int lastValidLine = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
      if (line > lastValidLine) line = lastValidLine;
    }

    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.getLineTextForRender(line);
    int clamped = Math.max(0, Math.min(target.ch, ln.length()));

    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
      if (range != null && range.collapsed) {
        float xLocal = editor.viewToTextX(touchX);
        float x;
        if (editor.wordWrap.isWordWrapEnabled) {
          int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
          int seg =
              editor.wordWrap.getWrapSegmentIndexForChar(
                  starts, Math.max(0, Math.min(clamped, ln.length())));
          int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
          x = xLocal + editor.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
        } else {
          x = xLocal;
        }

        int prefixEnd;
        if (range.isBlockComment) {
          prefixEnd = Math.min(range.openCharIndex + 2, ln.length());
        } else if (range.isIndentFold) {
          prefixEnd = ln.length();
        } else {
          prefixEnd = Math.min(range.openCharIndex + 1, ln.length());
        }
        float xStart =
            editor.measureHighlightedSegmentWidth(ln, line, 0, prefixEnd);
        float placeholderWidth =
            Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
        float closeStart = xStart + placeholderWidth;
        float closeWidth =
            range.isBlockComment
                ? editor.textRender.paint.measureText("*/")
                : editor.textRender.paint.measureText(String.valueOf(range.closeChar));
        String endLineText = editor.getLineTextForRender(range.endLine);
        int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
        int suffixStart =
            range.isBlockComment
                ? (closeIdx >= 0 ? closeIdx + 2 : (endLineText != null ? endLineText.length() : 0))
                : (closeIdx >= 0 ? closeIdx + 1 : (endLineText != null ? endLineText.length() : 0));

        if (x <= xStart) {
          line = range.startLine;
          clamped = Math.max(0, range.openCharIndex);
        } else if (x <= closeStart + closeWidth || endLineText == null) {
          line = range.endLine;
          clamped = (closeIdx >= 0) ? (closeIdx + 1) : 0;
        } else {
          float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
          int idx =
              editor.getCharIndexForXInRange(
                  endLineText,
                  range.endLine,
                  Math.max(0, Math.min(suffixStart, endLineText.length())),
                  endLineText.length(),
                  xSuffix);
          line = range.endLine;
          clamped = Math.max(suffixStart, Math.min(idx, endLineText.length()));
        }
        ln = editor.getLineTextForRender(line);
        if (ln == null) ln = "";
        clamped = Math.max(0, Math.min(clamped, ln.length()));
      }
    }
    editor.selectionHandles.lastDragAtLineStart = clamped == 0;
    editor.selectionHandles.lastDragAtLineEnd = clamped == ln.length();

    if (editor.selectionHandles.draggingHandle == 1) {
      if (editor.textRender.isRtl) {
        editor.selection.selEndLine = line;
        editor.selection.selEndChar = clamped;
      } else {
        editor.selection.selStartLine = line;
        editor.selection.selStartChar = clamped;
      }
    } else if (editor.selectionHandles.draggingHandle == 2) {
      if (editor.textRender.isRtl) {
        editor.selection.selStartLine = line;
        editor.selection.selStartChar = clamped;
      } else {
        editor.selection.selEndLine = line;
        editor.selection.selEndChar = clamped;
      }
    } else if (editor.selectionHandles.draggingHandle == 3) {
      editor.cursor.cursorLine = line;
      editor.cursor.cursorChar = clamped;
      editor.keepCursorVisibleHorizontally();
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

    float radius = Math.min(12f, Math.max(2f, editor.textRender.lineHeight * 0.22f));
    // Keep vertical edges flush between lines to avoid "seam" lines when selecting multiple lines.
    float insetX = 0.5f;
    editor.selection.selectionRectTmp.set(left + insetX, top, right - insetX, bottom);

    if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
      canvas.drawRect(editor.selection.selectionRectTmp, paint);
      return;
    }

    float tl = roundTopLeft ? radius : 0f;
    float tr = roundTopRight ? radius : 0f;
    float br = roundBottomRight ? radius : 0f;
    float bl = roundBottomLeft ? radius : 0f;

    editor.selection.selectionRadiiTmp[0] = tl;
    editor.selection.selectionRadiiTmp[1] = tl;
    editor.selection.selectionRadiiTmp[2] = tr;
    editor.selection.selectionRadiiTmp[3] = tr;
    editor.selection.selectionRadiiTmp[4] = br;
    editor.selection.selectionRadiiTmp[5] = br;
    editor.selection.selectionRadiiTmp[6] = bl;
    editor.selection.selectionRadiiTmp[7] = bl;

    editor.selection.selectionPathTmp.reset();
    editor.selection.selectionPathTmp.addRoundRect(editor.selection.selectionRectTmp, editor.selection.selectionRadiiTmp, Path.Direction.CW);
    canvas.drawPath(editor.selection.selectionPathTmp, paint);
  }
}
