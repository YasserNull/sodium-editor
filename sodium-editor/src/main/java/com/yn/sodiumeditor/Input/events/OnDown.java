package com.yn.sodiumeditor.Input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnDown handles onDown() gesture event for SodiumEditor.
 */
public class OnDown {

  private final SodiumEditor sodiumeditor;

  public OnDown(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Handle onDown event
   */
  public boolean onDown(MotionEvent e) {
    // If a suggestion was just accepted, clear the flag and allow normal onDown
    // processing
    if (sodiumeditor.suggestionAcceptedThisTouch) {
      sodiumeditor.suggestionAcceptedThisTouch = false; // Reset the flag
      // DON'T return false; proceed with normal onDown logic
    }
    sodiumeditor.scroll.scrollLockAxis = 0;
    sodiumeditor.zoom.mJustFinishedScale = false;
    sodiumeditor.ime.commitComposing(false); // End any active composing when user touches view.
    if (!sodiumeditor.scroll.scroller.isFinished()) {
      sodiumeditor.scroll.scroller.computeScrollOffset();
      sodiumeditor.scroll.scrollX = sodiumeditor.scroll.scroller.getCurrX();
      sodiumeditor.scroll.scrollY = sodiumeditor.scroll.scroller.getCurrY();
      sodiumeditor.scroll.scroller.abortAnimation();
    }
    sodiumeditor.downX = e.getX();
    sodiumeditor.downY = e.getY();
    sodiumeditor.movedSinceDown = false;
    return true;
  }
}
