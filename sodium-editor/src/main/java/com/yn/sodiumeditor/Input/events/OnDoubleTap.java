package com.yn.sodiumeditor.Input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnDoubleTap handles onDoubleTap() gesture event for SodiumEditor.
 */
public class OnDoubleTap {

  private final SodiumEditor sodiumeditor;
  private final OnSingleTapUp onSingleTapUp;

  public OnDoubleTap(SodiumEditor sodiumeditor, OnSingleTapUp onSingleTapUp) {
    this.sodiumeditor = sodiumeditor;
    this.onSingleTapUp = onSingleTapUp;
  }

  /**
   * Handle onDoubleTap event
   */
  public boolean onDoubleTap(MotionEvent e) {
    if (sodiumeditor.suggestionAcceptedThisTouch)
      return true; // Don't process if suggestion was accepted
    SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    sodiumeditor.ensureLineInWindow(line, true);
    String ln = sodiumeditor.getLineTextForRender(line);
    if (ln == null || ln.isEmpty()) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
    if (!sodiumeditor.applySmartDoubleTapSelection(line, charIndex, ln)) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    sodiumeditor.showPopupAtSelection();
    sodiumeditor.pendingPopupAfterDoubleTap = true;
    sodiumeditor.post(
        () -> {
          if (!sodiumeditor.pendingPopupAfterDoubleTap) return;
          sodiumeditor.pendingPopupAfterDoubleTap = false;
          if (sodiumeditor.selection.hasSelection) sodiumeditor.showPopupAtSelection();
        });
    sodiumeditor.caret.resetBlink();
    sodiumeditor.invalidate();
    sodiumeditor.showKeyboard();
    sodiumeditor.restartInput();
    return true;
  }
}
