package com.yn.sodiumeditor.input.events;

import android.view.GestureDetector;
import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

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

  private final SodiumEditor editor;
  private final GestureDetector gestureDetector;
  
  private final OnDown onDown;
  private final OnLongPress onLongPress;
  private final OnSingleTapUp onSingleTapUp;
  private final OnFling onFling;
  private final OnDoubleTap onDoubleTap;

  public OnScroll(SodiumEditor editor) {
    FunctionLog.f("OnScroll", "OnScroll", editor);
    this.editor = editor;
    
    // Initialize event handlers (note: OnDoubleTap depends on OnSingleTapUp)
    this.onDown = new OnDown(editor);
    this.onLongPress = new OnLongPress(editor);
    this.onSingleTapUp = new OnSingleTapUp(editor);
    this.onFling = new OnFling(editor);
    this.onDoubleTap = new OnDoubleTap(editor, onSingleTapUp);
    
    this.gestureDetector = new GestureDetector(editor.getContext(), this);
  }

  public GestureDetector getGestureDetector() {
    FunctionLog.f("OnScroll", "getGestureDetector");
    return gestureDetector;
  }

  @Override
  public boolean onDown(MotionEvent e) {
    FunctionLog.f("OnScroll", "onDown", e);
    return onDown.onDown(e);
  }

  @Override
  public void onLongPress(MotionEvent e) {
    FunctionLog.f("OnScroll", "onLongPress", e);
    onLongPress.onLongPress(e);
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e) {
    FunctionLog.f("OnScroll", "onSingleTapUp", e);
    return onSingleTapUp.onSingleTapUp(e);
  }

  @Override
  public boolean onScroll(
      MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    FunctionLog.f("OnScroll", "onScroll", e1, e2, distanceX, distanceY);
    if (e2.getPointerCount() > 1) return true;
    if (editor.zoom.isScaling || editor.scaleGestureDetector.isInProgress()) return true;
    if (editor.zoom.mJustFinishedScale) return true;
    if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixBuilding) {
      editor.wordWrap.cancelWrapPrefixRebuildForInteraction();
    }
    if (editor.autoCompletion.suggestionAcceptedThisTouch)
      return false; // Don't process if suggestion was accepted

    editor.onTouch.movedSinceDown = true;

    // Delegate to Scroll
    return editor.scroll.handleScroll(e1, e2, distanceX, distanceY);
  }

  @Override
  public boolean onFling(
      MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    FunctionLog.f("OnScroll", "onFling", e1, e2, velocityX, velocityY);
    return onFling.onFling(e1, e2, velocityX, velocityY);
  }

  @Override
  public boolean onDoubleTap(MotionEvent e) {
    FunctionLog.f("OnScroll", "onDoubleTap", e);
    return onDoubleTap.onDoubleTap(e);
  }
}
