package com.yn.sodiumeditor.Input.events;

import android.view.GestureDetector;
import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnScroll handles all scroll gesture logic for SodiumEditor.
 * This class delegates to specialized event handlers:
 * - OnDown for onDown() events
 * - OnLongPress for onLongPress() events
 * - OnSingleTapUp for onSingleTapUp() events
 * - OnFling for onFling() events
 * - OnDoubleTap for onDoubleTap() events
 */
public class OnScroll extends GestureDetector.SimpleOnGestureListener {

  private final SodiumEditor sodiumeditor;
  private final GestureDetector gestureDetector;
  
  private final OnDown onDown;
  private final OnLongPress onLongPress;
  private final OnSingleTapUp onSingleTapUp;
  private final OnFling onFling;
  private final OnDoubleTap onDoubleTap;

  public OnScroll(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
    
    // Initialize event handlers (note: OnDoubleTap depends on OnSingleTapUp)
    this.onDown = new OnDown(sodiumeditor);
    this.onLongPress = new OnLongPress(sodiumeditor);
    this.onSingleTapUp = new OnSingleTapUp(sodiumeditor);
    this.onFling = new OnFling(sodiumeditor);
    this.onDoubleTap = new OnDoubleTap(sodiumeditor, onSingleTapUp);
    
    this.gestureDetector = new GestureDetector(sodiumeditor.getContext(), this);
  }

  public GestureDetector getGestureDetector() {
    return gestureDetector;
  }

  @Override
  public boolean onDown(MotionEvent e) {
    return onDown.onDown(e);
  }

  @Override
  public void onLongPress(MotionEvent e) {
    onLongPress.onLongPress(e);
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e) {
    return onSingleTapUp.onSingleTapUp(e);
  }

  @Override
  public boolean onScroll(
      MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    if (e2.getPointerCount() > 1) return true;
    if (sodiumeditor.zoom.isScaling || sodiumeditor.scaleGestureDetector.isInProgress()) return true;
    if (sodiumeditor.zoom.mJustFinishedScale) return true;
    if (sodiumeditor.isWordWrapEnabled && sodiumeditor.wrapPrefixBuilding) {
      sodiumeditor.cancelWrapPrefixRebuildForInteraction();
    }
    if (sodiumeditor.suggestionAcceptedThisTouch)
      return false; // Don't process if suggestion was accepted

    sodiumeditor.movedSinceDown = true;

    // Delegate to Scroll
    return sodiumeditor.scroll.handleScroll(e1, e2, distanceX, distanceY);
  }

  @Override
  public boolean onFling(
      MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return onFling.onFling(e1, e2, velocityX, velocityY);
  }

  @Override
  public boolean onDoubleTap(MotionEvent e) {
    return onDoubleTap.onDoubleTap(e);
  }
}
