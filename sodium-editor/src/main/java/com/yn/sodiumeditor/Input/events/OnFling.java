package com.yn.sodiumeditor.Input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnFling handles onFling() gesture event for SodiumEditor.
 */
public class OnFling {

  private final SodiumEditor sodiumeditor;

  public OnFling(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Handle onFling event
   */
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (sodiumeditor.zoom.isScaling || sodiumeditor.scaleGestureDetector.isInProgress()) return true;
    if (sodiumeditor.zoom.mJustFinishedScale) return true;
    if (sodiumeditor.isWordWrapEnabled && sodiumeditor.wrapPrefixBuilding) {
      sodiumeditor.cancelWrapPrefixRebuildForInteraction();
    }
    if (sodiumeditor.suggestionAcceptedThisTouch)
      return false; // Don't process if suggestion was accepted

    // Delegate to Scroll
    return sodiumeditor.scroll.handleFling(e1, e2, velocityX, velocityY);
  }
}
