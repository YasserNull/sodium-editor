package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/** OnFling handles onFling() gesture event for SodiumEditor. */
public class OnFling {

  private final SodiumEditor editor;

  public OnFling(SodiumEditor editor) {
    this.editor = editor;
  }

  /** Handle onFling event */
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (editor.zoom.isScaling || editor.scaleGestureDetector.isInProgress()) return true;
    if (editor.zoom.mJustFinishedScale) return true;
    if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixBuilding) {
      editor.wordWrap.cancelWrapPrefixRebuildForInteraction();
    }
    if (editor.autoCompletion.suggestionAcceptedThisTouch)
      return false; // Don't process if suggestion was accepted

    // Delegate to Scroll
    return editor.scroll.handleFling(e1, e2, velocityX, velocityY);
  }
}
