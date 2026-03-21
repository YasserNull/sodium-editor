package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;

/**
 * OnLongPress handles onLongPress() gesture event for SodiumEditor.
 */
public class OnLongPress {

  private final SodiumEditor editor;

  public OnLongPress(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handle onLongPress event
   */
  public void onLongPress(MotionEvent e) {
    if (editor.autoCompletion.suggestionAcceptedThisTouch) return;
    if (editor.multiTouchActive || editor.hadMultiTouch) return;

    if (editor.popup.showPopup) {
      int hitAction = editor.popup.getPopupActionAt(e.getX(), e.getY());
      if (hitAction != 0) {
        editor.popup.popupPressedAction = hitAction;
        editor.popup.startPopupRippleHold(hitAction, e.getX(), e.getY());
        return;
      }
    }

    if (editor.movedSinceDown) return;

    if (editor.lineNumber.lineNumberSelectionEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float y = e.getY() + editor.scroll.scrollY;
      int line = editor.getGlobalLineForY(y);
      editor.lineNumber.beginLineNumberSelection(line);
      return;
    }

    // Position calculation
    EditOperators.CursorTarget target = editor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true); // Make sure line data is available

    String ln = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
    if (ln == null) ln = editor.getLineTextForRender(line);
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

    // Set cursor position
    editor.cursor.cursorLine = line;
    editor.cursor.cursorChar = charIndex;

    // Try smart selection, but show minimal popup even if it fails (e.g., empty line)
    boolean hasSelection = editor.selection.applySmartDoubleTapSelection(line, charIndex, ln);
    editor.selection.beginLongPressSelection(line, charIndex);
    
    if (hasSelection) {
      editor.popup.showPopupAtSelection();
    } else {
      // No selection (empty line or smart selection failed), show minimal popup with Select All and Paste
      editor.popup.showMinimalPopupAtCursor();
    }
    editor.caret.resetBlink();
    editor.invalidate();
    editor.showKeyboard();
    editor.restartInput();
  }

  /**
   * Called when smart double tap selection fails - delegates to OnSingleTapUp
   */
  public void onSingleTapUpFallback(MotionEvent e, OnSingleTapUp onSingleTapUp) {
    onSingleTapUp.onSingleTapUp(e);
  }
}
