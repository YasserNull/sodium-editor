package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles scroll bar dragging logic for SodiumEditor.
 */
public class ScrollBarHandler {
    private final SodiumEditor editor;

    public ScrollBarHandler(SodiumEditor editor) {
        this.editor = editor;
    }

    public boolean handleActionDown(MotionEvent event) {
        if (!editor.scroll.bar.enabled) return false;
        
        float ex = event.getX();
        float ey = event.getY();
        
        // Increase hit area for easier grabbing
        float hitSlop = 30f * editor.getContext().getResources().getDisplayMetrics().density;
        float left = editor.scroll.bar.thumbRect.left - hitSlop;
        float right = editor.scroll.bar.thumbRect.right + hitSlop;
        float top = editor.scroll.bar.thumbRect.top - hitSlop;
        float bottom = editor.scroll.bar.thumbRect.bottom + hitSlop;

        if (ex >= left && ex <= right && ey >= top && ey <= bottom) {
            editor.scroll.bar.dragging = true;
            editor.scroll.bar.dragOffset = ey - editor.scroll.bar.thumbRect.top;
            editor.scroll.showScrollBar();
            return true;
        }
        return false;
    }

    public boolean handleActionMove(MotionEvent event) {
        if (!editor.scroll.bar.dragging) return false;

        float ey = event.getY();
        float maxScroll = editor.scroll.getMaxScrollYForClamp();
        
        float h = editor.getHeight();
        float trackHeight = h;
        float contentHeight = maxScroll + h;
        float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
        
        if (thumbHeight < editor.scroll.bar.minThumbPx) {
            thumbHeight = editor.scroll.bar.minThumbPx;
        }
        if (thumbHeight > trackHeight) thumbHeight = trackHeight;
        
        float thumbRange = Math.max(1f, trackHeight - thumbHeight);
        float targetTop = Math.max(0f, Math.min(trackHeight - thumbHeight, ey - editor.scroll.bar.dragOffset));
        
        if (maxScroll > 0f) {
            editor.scroll.scrollY = (targetTop / thumbRange) * maxScroll;
            editor.scroll.clampScrollY();
            editor.invalidate();
        }
        editor.scroll.showScrollBar();
        return true;
    }

    public void handleActionUpOrCancel() {
        if (editor.scroll.bar.dragging) {
            editor.scroll.bar.dragging = false;
            editor.scroll.showScrollBar();
        }
    }
}
