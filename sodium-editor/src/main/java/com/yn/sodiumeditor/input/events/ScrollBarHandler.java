package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/** Handles scroll bar dragging logic for SodiumEditor. */
public class ScrollBarHandler {
  private final SodiumEditor editor;

  public ScrollBarHandler(SodiumEditor editor) {
    this.editor = editor;
  }

  public boolean handleActionDown(MotionEvent event) {
    if (!editor.scroll.bar.scrollBarEnabled) return false;

    float ex = event.getX();
    float ey = event.getY();

    // Increase hit area for easier grabbing
    float hitSlop = 30f * editor.getContext().getResources().getDisplayMetrics().density;
    float left = editor.scroll.bar.thumbRect.left - hitSlop;
    float right = editor.scroll.bar.thumbRect.right + hitSlop;
    float top = editor.scroll.bar.thumbRect.top - hitSlop;
    float bottom = editor.scroll.bar.thumbRect.bottom + hitSlop;

    if (ex >= left && ex <= right && ey >= top && ey <= bottom) {
      editor.scroll.bar.scrollBarDragging = true;
      editor.scroll.bar.scrollBarDragOffset = ey - editor.scroll.bar.thumbRect.top;
      editor.scroll.showScrollBar();
      return true;
    }
    return false;
  }

  public boolean handleActionMove(MotionEvent event) {
    if (!editor.scroll.bar.scrollBarDragging) return false;

    float ey = event.getY();
    float maxScroll = editor.scroll.getMaxScrollYForClamp();

    float h = editor.getHeight();
    float trackHeight = h;
    float contentHeight = maxScroll + h;
    float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);

    if (thumbHeight < editor.scroll.bar.scrollBarMinThumbPx) {
      thumbHeight = editor.scroll.bar.scrollBarMinThumbPx;
    }
    if (thumbHeight > trackHeight) thumbHeight = trackHeight;

    float thumbRange = Math.max(1f, trackHeight - thumbHeight);
    float targetTop =
        Math.max(0f, Math.min(trackHeight - thumbHeight, ey - editor.scroll.bar.scrollBarDragOffset));

    if (maxScroll > 0f) {
      editor.scroll.scrollY = (targetTop / thumbRange) * maxScroll;
      editor.scroll.clampScrollY();
      editor.removeCallbacks(editor.scroll.delayedWindowCheck);
      editor.windowRender.maybeKickWindowLoad(
          editor.wordWrap.getGlobalLineForY(editor.scroll.scrollY));
      editor.postDelayed(editor.scroll.delayedWindowCheck, 60);
      editor.invalidate();
    }
    editor.scroll.showScrollBar();
    return true;
  }

  public void handleActionUpOrCancel() {
    if (editor.scroll.bar.scrollBarDragging) {
      editor.scroll.bar.scrollBarDragging = false;
      editor.scroll.showScrollBar();
      editor.removeCallbacks(editor.scroll.delayedWindowCheck);
      editor.fileIO.checkAndLoadWindow();
      if (editor.wordWrap.isWordWrapEnabled
          && editor.wordWrap.wrapPrefixRebuildPending
          && !editor.wordWrap.wrapPrefixBuilding) {
        editor.wordWrap.wrapPrefixRebuildPending = false;
        editor.wordWrap.scheduleWrapPrefixRebuildUpToWindow();
      }
      editor.invalidate();
    }
  }
}
