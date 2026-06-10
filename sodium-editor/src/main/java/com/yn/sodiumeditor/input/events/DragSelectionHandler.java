package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import java.util.HashMap;

/** Handles selection dragging and auto-scroll for SodiumEditor. */
public class DragSelectionHandler {
  private final SodiumEditor editor;

  public DragSelectionHandler(SodiumEditor editor) {
    this.editor = editor;
  }

  public boolean handleActionDown(MotionEvent event) {
    float ex = event.getX(), ey = event.getY();
    if (editor.selection.hasSelection) {
      editor.selectionHandles.updateHandlesPosition();
    }
    editor.cursorHandle.updateCursorHandlePosition();

    if (editor.selection.hasSelection && editor.selectionHandles.hitTestLeft(ex, ey)) {
      editor.selectionHandles.draggingHandle = 1;
      return true;
    } else if (editor.selection.hasSelection && editor.selectionHandles.hitTestRight(ex, ey)) {
      editor.selectionHandles.draggingHandle = 2;
      return true;
    } else if (editor.isFocused()
        && !editor.selection.hasSelection
        && editor.cursorHandle.hitTest(ex, ey)) {
      editor.selectionHandles.draggingHandle = 3;
      return true;
    }
    return false;
  }

  public boolean handleActionMove(MotionEvent event) {
    float ex = event.getX(), ey = event.getY();

    if (editor.selection.longPressSelecting
        && editor.selectionHandles.draggingHandle == 0
        && !editor.selection.isLineNumberSelecting) {

      float moveX, moveY;
      boolean isTwoFinger = editor.selection.state.longPressEndPointerId != -1;

      if (isTwoFinger) {
        int pointerIndex = event.findPointerIndex(editor.selection.state.longPressEndPointerId);
        if (pointerIndex == -1) return true;
        moveX = event.getX(pointerIndex);
        moveY = event.getY(pointerIndex);
      } else {
        if (!editor.selection.longPressFreeForm) {
          float dx = ex - editor.onTouch.downX;
          float dy = ey - editor.onTouch.downY;
          if (dx * dx + dy * dy > editor.onTouch.touchSlop * editor.onTouch.touchSlop * 4) {
            editor.selection.state.longPressFreeForm = true;
            editor.selection.syncFromState();
            editor.selection.hasSelection = false;
            editor.selection.selecting = true;
          } else {
            return true;
          }
        }
        moveX = ex;
        moveY = ey;
      }

      EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(moveX, moveY, null);
      int line = target.line;
      line = clampDragLineToRealContent(line);
      editor.fileIO.ensureLineInWindow(line, true);
      String ln = getLineTextForDrag(line);
      int clamped = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));

      editor.selection.updateLongPressSelection(line, clamped);
      updateAutoScroll(moveX, moveY);
      editor.invalidate();
      return true;
    }

    if (editor.selectionHandles.draggingHandle != 0) {
      int handle = editor.selectionHandles.draggingHandle;
      updateHandlePosition(ex, ey);
      if (editor.selectionHandles.draggingHandle == 1
          || editor.selectionHandles.draggingHandle == 2) {
        editor.popup.showPopupAtSelection();
      }
      updateAutoScroll(ex, ey);
      editor.invalidate();
      return true;
    }
    return false;
  }

  public void handleActionUpOrCancel() {
    editor.caret.mainHandler.removeCallbacks(editor.scroll.autoScrollRunnable);
    if (editor.selectionHandles.draggingHandle != 0) {
      if (editor.selectionHandles.draggingHandle == 3) {
        updateHandlePosition(editor.onTouch.lastTouchX, editor.onTouch.lastTouchY);
        editor.cursorAnimation.snapToPosition(
            editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
      }
      if (editor.selectionHandles.draggingHandle == 1
          || editor.selectionHandles.draggingHandle == 2) {
        editor.popup.showPopupAtSelection();
      }
      editor.selectionHandles.draggingHandle = 0;
      editor.invalidate();
    }
  }

  private void updateAutoScroll(float x, float y) {
    float scrollMargin = editor.textRender.lineHeight * 2f;
    float scrollSpeed = Math.max(4f, editor.textRender.lineHeight * 0.35f);
    editor.scroll.autoScrollY = 0;
    editor.scroll.autoScrollX = 0;

    if (y < scrollMargin) editor.scroll.autoScrollY = -scrollSpeed;
    else if (y > (editor.getHeight() - editor.view.keyboardHeight) - scrollMargin)
      editor.scroll.autoScrollY = scrollSpeed;

    if (x < scrollMargin) editor.scroll.autoScrollX = -scrollSpeed;
    else if (x > editor.getWidth() - scrollMargin) editor.scroll.autoScrollX = scrollSpeed;

    if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled)
      editor.scroll.autoScrollX = -editor.scroll.autoScrollX;

    if (editor.selectionHandles.draggingHandle != 0) {
      if (editor.scroll.autoScrollX > 0 && editor.selectionHandles.lastDragAtLineEnd)
        editor.scroll.autoScrollX = 0;
      if (editor.scroll.autoScrollX < 0 && editor.selectionHandles.lastDragAtLineStart)
        editor.scroll.autoScrollX = 0;
    }

    if (editor.scroll.autoScrollX != 0 || editor.scroll.autoScrollY != 0) {
      editor.caret.mainHandler.post(editor.scroll.autoScrollRunnable);
    } else {
      editor.caret.mainHandler.removeCallbacks(editor.scroll.autoScrollRunnable);
    }
  }

  public void updateHandlePosition(float touchX, float touchY) {
    if (editor.selection.isSelectAllActive || editor.selection.isEntireFileSelected) {
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      editor.popup.showPopupAtSelection();
    }

    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(touchX, touchY, null);
    int line = target.line;
    line = clampDragLineToRealContent(line);

    editor.fileIO.ensureLineInWindow(line, true);
    String ln = getLineTextForDrag(line);
    int clamped = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));
    editor.selectionHandles.lastDragAtLineStart = (clamped == 0);
    editor.selectionHandles.lastDragAtLineEnd = (ln != null && clamped == ln.length());

    if (editor.selectionHandles.draggingHandle == 1) {
      if (editor.editOperators.comparePos(
              line, clamped, editor.selection.selEndLine, editor.selection.selEndChar)
          >= 0) {
        int[] beforeEnd =
            getPreviousSelectionPosition(editor.selection.selEndLine, editor.selection.selEndChar);
        line = beforeEnd[0];
        clamped = beforeEnd[1];
      }
      if (editor.textRender.isRtl) {
        editor.selection.selEndLine = line;
        editor.selection.selEndChar = clamped;
      } else {
        editor.selection.selStartLine = line;
        editor.selection.selStartChar = clamped;
      }
    } else if (editor.selectionHandles.draggingHandle == 2) {
      if (editor.editOperators.comparePos(
              line, clamped, editor.selection.selStartLine, editor.selection.selStartChar)
          <= 0) {
        int[] afterStart =
            getNextSelectionPosition(editor.selection.selStartLine, editor.selection.selStartChar);
        line = afterStart[0];
        clamped = afterStart[1];
      }
      if (editor.textRender.isRtl) {
        editor.selection.selStartLine = line;
        editor.selection.selStartChar = clamped;
      } else {
        editor.selection.selEndLine = line;
        editor.selection.selEndChar = clamped;
      }
    } else if (editor.selectionHandles.draggingHandle == 3) {
      setCursorFromDrag(line, clamped, ln);
      editor.scroll.keepCursorVisibleHorizontally();
    }
  }

  private int[] getPreviousSelectionPosition(int line, int ch) {
    if (ch > 0) return new int[] {line, ch - 1};
    if (line <= 0) return new int[] {line, ch};
    int prevLine = line - 1;
    String prevText = editor.windowRender.getLineTextForRender(prevLine);
    return new int[] {prevLine, prevText == null ? 0 : prevText.length()};
  }

  private int[] getNextSelectionPosition(int line, int ch) {
    String lineText = editor.windowRender.getLineTextForRender(line);
    int lineLength = lineText == null ? 0 : lineText.length();
    if (ch < lineLength) return new int[] {line, ch + 1};
    int nextLine = line + 1;
    int maxLine = editor.view.getLinesCount() - 1;
    if (nextLine > maxLine) return new int[] {line, ch};
    return new int[] {nextLine, 0};
  }

  private String getLineTextForDrag(int line) {
    String ln = editor.windowRender.getLineTextForRender(line);
    if (ln != null && !ln.isEmpty()) return ln;
    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
  }

  private int clampDragLineToRealContent(int line) {
    int totalLines = editor.view.getLinesCount();
    int maxLine = Math.max(0, totalLines - 1);
    if (editor.fileIO.isEof) {
      int loadedLastLine =
          editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;
      if (loadedLastLine >= 0) maxLine = Math.min(maxLine, loadedLastLine);
    }
    return Math.max(0, Math.min(line, maxLine));
  }

  private void setCursorFromDrag(int line, int col, String knownLineText) {
    int totalLines = editor.view.getLinesCount();
    int targetLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    int maxCol = knownLineText == null ? Math.max(0, col) : knownLineText.length();
    editor.cursor.cursorLine = targetLine;
    editor.cursor.cursorChar = Math.max(0, Math.min(col, maxCol));
    editor.caret.resetBlink();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }
}
