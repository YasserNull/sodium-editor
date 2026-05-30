package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import java.util.HashMap;

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
    if (editor.onTouch.multiTouchActive || editor.onTouch.hadMultiTouch) return true;

    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
    }
    float y = e.getY() + editor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
    int totalVisible =
        editor.wordWrap.isWordWrapEnabled ? editor.wordWrap.getTotalVisualLineCount() : Math.max(1, editor.view.getLinesCount());
    boolean afterEnd = editor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible);
    if (afterEnd) {
      placeCursorAtLastVisibleLineEnd(totalVisible);
      if (editor.clickAfterEndToAddLine.isClickAfterEndToAddLineEnabled
          && visibleIndex == totalVisible) {
        editor.editOperators.insertTextAtCursor("\n");
      }
      finishTapCursorPlacement(afterEnd);
      return true;
    }

    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.windowRender.getLineTextForRender(line);
    editor.cursor.setCursorPosition(line, Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length())));

    finishTapCursorPlacement(false);
    return true;
  }

  private void finishTapCursorPlacement(boolean snapCursorAnimation) {
    if (snapCursorAnimation) {
      editor.cursorAnimation.snapToPosition(
          editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
    }
    editor.selection.selecting = false;
    editor.cursorHandle.showAfterCursorPlacement();
    editor.caret.resumeBlinkAfterCursorPlacement();
    editor.invalidate();
    editor.caret.resetBlink();
    editor.ime.showKeyboard();
    editor.view.restartInput();
    editor.autoCompletion.updateSuggestion();
  }

  private String getLineTextForTap(int line) {
    String text = editor.windowRender.getLineTextForRender(line);
    if (text != null && !text.isEmpty()) return text;
    if (line < 0 || editor.fileIO.sourceFile == null) return text == null ? "" : text;
    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
  }

  private void placeCursorAtLastVisibleLineEnd(int totalVisible) {
    int visibleIndex = Math.max(0, totalVisible - 1);
    int line =
        editor.wordWrap.isWordWrapEnabled
            ? editor.wordWrap.getVisualPositionForIndex(visibleIndex).line
            : visibleIndex;
    String lineText = getLineTextForTap(line);
    editor.cursor.cursorLine = Math.max(0, line);
    editor.cursor.cursorChar = lineText == null ? 0 : lineText.length();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }

  private void setCursorFromFoldTap(int line, int col, String knownLineText) {
    int totalLines = editor.view.getLinesCount();
    int targetLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    int maxCol = knownLineText == null ? Math.max(0, col) : knownLineText.length();
    editor.cursor.cursorLine = targetLine;
    editor.cursor.cursorChar = Math.max(0, Math.min(col, maxCol));
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }
}
