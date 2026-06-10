package com.yn.sodiumeditor.core.view.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

public class onGenericMotionEvent {
  private final SodiumEditor editor;

  public onGenericMotionEvent(SodiumEditor editor) {
    this.editor = editor;
  }

  public boolean onGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0) {
      if (event.getAction() == MotionEvent.ACTION_SCROLL) {
        float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (hScroll != 0f || vScroll != 0f) {
          // Use standard multiplier for scroll wheel
          float mult = 64f * editor.getResources().getDisplayMetrics().density;
          // distanceX and distanceY are added to scrollX and scrollY in handleScroll.
          // For wheel, vScroll > 0 is AWAY (up), we want scrollY to decrease.
          // For wheel, hScroll > 0 is RIGHT, we want scrollX to increase.
          editor.scroll.handleScroll(null, event, hScroll * mult, -vScroll * mult);
          return true;
        }
      }
    }
    return false;
  }
}
