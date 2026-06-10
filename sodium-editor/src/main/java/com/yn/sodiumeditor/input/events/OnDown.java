package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/** OnDown handles onDown() gesture event for SodiumEditor. */
public class OnDown {

  private final SodiumEditor editor;

  public OnDown(SodiumEditor editor) {
    this.editor = editor;
  }

  /** Handle onDown event */
  public boolean onDown(MotionEvent e) {
    // If a suggestion was just accepted, clear the flag and allow normal onDown
    // processing
    if (editor.autoCompletion.suggestionAcceptedThisTouch) {
      editor.autoCompletion.suggestionAcceptedThisTouch = false; // Reset the flag
      // DON'T return false; proceed with normal onDown logic
    }
    editor.scroll.scrollLockAxis = 0;
    editor.zoom.mJustFinishedScale = false;
    editor.ime.commitComposing(false); // End any active composing when user touches view.
    if (!editor.scroll.scroller.isFinished()) {
      editor.scroll.scroller.computeScrollOffset();
      editor.scroll.scrollX = editor.scroll.scroller.getCurrX();
      editor.scroll.scrollY = editor.scroll.scroller.getCurrY();
      editor.scroll.scroller.abortAnimation();
    }
    editor.onTouch.downX = e.getX();
    editor.onTouch.downY = e.getY();
    editor.onTouch.movedSinceDown = false;
    return true;
  }
}
