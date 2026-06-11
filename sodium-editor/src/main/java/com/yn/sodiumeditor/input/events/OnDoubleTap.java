package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;

/** OnDoubleTap handles onDoubleTap() gesture event for SodiumEditor. */
public class OnDoubleTap {

  private final SodiumEditor editor;
  private final OnSingleTapUp onSingleTapUp;

  public OnDoubleTap(SodiumEditor editor, OnSingleTapUp onSingleTapUp) {
    this.editor = editor;
    this.onSingleTapUp = onSingleTapUp;
  }

  /** Handle onDoubleTap event */
  public boolean onDoubleTap(MotionEvent e) {
    if (editor.autoSuggestion.suggestionAcceptedThisTouch)
      return true; // Don't process if suggestion was accepted
    EditOp.CursorTarget target =
        editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.windowRender.getLineTextForRender(line);
    if (ln == null || ln.isEmpty()) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
    if (!editor.selection.applySmartDoubleTapSelection(line, charIndex, ln)) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    editor.popup.showPopupAtSelection();
    editor.popup.pendingPopupAfterDoubleTap = true;
    editor.post(
        () -> {
          if (!editor.popup.pendingPopupAfterDoubleTap) return;
          editor.popup.pendingPopupAfterDoubleTap = false;
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
        });
    editor.caret.resetBlink();
    editor.invalidate();
    editor.ime.showKeyboard();
    editor.view.restartInput();
    return true;
  }
}
