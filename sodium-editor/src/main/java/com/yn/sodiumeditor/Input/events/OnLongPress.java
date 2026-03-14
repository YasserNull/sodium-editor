package com.yn.sodiumeditor.Input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnLongPress handles onLongPress() gesture event for SodiumEditor.
 */
public class OnLongPress {

  private final SodiumEditor sodiumeditor;

  public OnLongPress(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Handle onLongPress event
   */
  public void onLongPress(MotionEvent e) {
    if (sodiumeditor.suggestionAcceptedThisTouch) return;
    if (sodiumeditor.multiTouchActive || sodiumeditor.hadMultiTouch) return;

    if (sodiumeditor.popup.showPopup) {
      int hitAction = sodiumeditor.popup.getPopupActionAt(e.getX(), e.getY());
      if (hitAction != 0) {
        sodiumeditor.popup.popupPressedAction = hitAction;
        sodiumeditor.popup.startPopupRippleHold(hitAction, e.getX(), e.getY());
        return;
      }
    }

    if (sodiumeditor.movedSinceDown) return;

    if (sodiumeditor.lineNumber.lineNumberSelectionEnabled && sodiumeditor.lineNumber.isInLineNumberGutter(e.getX())) {
      float y = e.getY() + sodiumeditor.scroll.scrollY;
      int line = sodiumeditor.getGlobalLineForY(y);
      sodiumeditor.lineNumber.beginLineNumberSelection(line);
      return;
    }

    // Position calculation
    SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    sodiumeditor.ensureLineInWindow(line, true); // Make sure line data is available

    String ln = sodiumeditor.getLineFromWindowLocal(line - sodiumeditor.windowStartLine);
    if (ln == null) ln = sodiumeditor.getLineTextForRender(line);
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

    // Set cursor position
    sodiumeditor.cursor.cursorLine = line;
    sodiumeditor.cursor.cursorChar = charIndex;

    // Try smart selection, but show minimal popup even if it fails (e.g., empty line)
    boolean hasSelection = sodiumeditor.applySmartDoubleTapSelection(line, charIndex, ln);
    
    if (hasSelection) {
      sodiumeditor.popup.showPopupAtSelection();
    } else {
      // No selection (empty line or smart selection failed), show minimal popup with Select All and Paste
      sodiumeditor.popup.showMinimalPopupAtCursor();
    }
    sodiumeditor.caret.resetBlink();
    sodiumeditor.invalidate();
    sodiumeditor.showKeyboard();
    sodiumeditor.restartInput();
  }

  /**
   * Called when smart double tap selection fails - delegates to OnSingleTapUp
   */
  public void onSingleTapUpFallback(MotionEvent e, OnSingleTapUp onSingleTapUp) {
    onSingleTapUp.onSingleTapUp(e);
  }
}
