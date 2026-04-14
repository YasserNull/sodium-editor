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
        if (!editor.scroll.scrollBarEnabled) return false;
        
        float ex = event.getX();
        float ey = event.getY();
        float maxScroll = editor.scroll.getMaxScrollYForClamp();
        
        if (maxScroll > 0f && editor.scroll.scrollBarThumbRect.contains(ex, ey)) {
            editor.scroll.draggingScrollBar = true;
            editor.scroll.scrollBarDragOffset = ey - editor.scroll.scrollBarThumbRect.top;
            editor.scroll.showScrollBar();
            return true;
        }
        return false;
    }

    public boolean handleActionMove(MotionEvent event) {
        if (!editor.scroll.draggingScrollBar) return false;

        float ey = event.getY();
        float maxScroll = editor.scroll.getMaxScrollYForClamp();
        
        if (maxScroll > 0f) {
            float h = editor.getHeight();
            float trackHeight = h;
            float contentHeight = maxScroll + h;
            float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
            
            if (thumbHeight < editor.scroll.scrollBarMinThumbPx) {
                thumbHeight = editor.scroll.scrollBarMinThumbPx;
            }
            if (thumbHeight > trackHeight) thumbHeight = trackHeight;
            
            float thumbRange = Math.max(1f, trackHeight - thumbHeight);
            float targetTop = Math.max(0f, Math.min(trackHeight - thumbHeight, ey - editor.scroll.scrollBarDragOffset));
            
            editor.scroll.scrollY = (targetTop / thumbRange) * maxScroll;
            editor.scroll.clampScrollY();
            editor.invalidate();
        }
        editor.scroll.showScrollBar();
        return true;
    }

    public void handleActionUpOrCancel() {
        if (editor.scroll.draggingScrollBar) {
            editor.scroll.draggingScrollBar = false;
            editor.scroll.showScrollBar();
        }
    }
}
