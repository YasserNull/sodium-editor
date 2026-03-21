package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;

/**
 * OnSingleTapUp handles onSingleTapUp() gesture event for SodiumEditor.
 */
public class OnSingleTapUp {

  private final SodiumEditor editor;

  public OnSingleTapUp(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handle onSingleTapUp event
   */
  public boolean onSingleTapUp(MotionEvent e) {
    if (editor.autoCompletion.suggestionAcceptedThisTouch) return true;
    if (editor.multiTouchActive || editor.hadMultiTouch) return true;

    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
    }
    if (editor.codeFold.isCodeFoldingEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float gy = e.getY() + editor.scroll.scrollY;
      int line = editor.getGlobalLineForY(gy);
      if (editor.codeFold.toggleFoldAtLine(line)) {
        editor.codeFold.startFoldMarkerRipple(line);
        editor.popup.hidePopup();
        editor.invalidate();
        return true;
      }
    }
    float y = e.getY() + editor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
    int totalVisible =
        editor.wordWrap.isWordWrapEnabled ? editor.wordWrap.getTotalVisualLineCount() : editor.codeFold.getVisibleLineCount();
    EditOperators.CursorTarget target = editor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (editor.codeFold.isCodeFoldingEnabled) {
      String ln = editor.getLineTextForRender(line);
      float xLocal = editor.viewToTextX(e.getX());
      float x;
      if (editor.wordWrap.isWordWrapEnabled) {
        int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
        int seg =
            editor.wordWrap.getWrapSegmentIndexForChar(
                starts, Math.max(0, Math.min(target.ch, ln.length())));
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        x = xLocal + editor.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
      } else {
        x = xLocal;
      }
      if (editor.isFoldPlaceholderHit(line, ln, x)) {
        if (editor.codeFold.toggleFoldAtLine(line)) {
          editor.codeFold.startFoldMarkerRipple(line);
        }
        editor.popup.hidePopup();
        editor.invalidate();
        return true;
      }
    }

    boolean afterEnd = editor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible);

    if (afterEnd) {
      if (!editor.clickAfterEndToAddLine.handleClickAfterEnd(visibleIndex, totalVisible)) {
        editor.clickAfterEndToAddLine.handleDefaultAfterEnd();
      }
    } else {
      editor.fileIO.ensureLineInWindow(line, true);
      String ln = editor.getLineTextForRender(line);
      editor.cursor.cursorLine = line;
      editor.cursor.cursorChar = Math.max(0, Math.min(target.ch, ln.length()));
    }

    editor.popup.hidePopup();
    editor.selection.selecting = false;
    editor.invalidate();
    editor.caret.resetBlink();
    editor.showKeyboard();
    editor.restartInput();
    editor.autoCompletion.updateSuggestion();
    return true;
  }
}
