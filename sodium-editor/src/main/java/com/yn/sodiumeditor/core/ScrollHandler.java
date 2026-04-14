package com.yn.sodiumeditor.core;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles user gestures and fling animations.
 */
public class ScrollHandler {
    private final SodiumEditor editor;
    private final Scroll scroll;

    public ScrollHandler(SodiumEditor editor, Scroll scroll) {
        this.editor = editor;
        this.scroll = scroll;
    }

    public boolean handleScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (e2.getPointerCount() > 1) return true;
        float dx = distanceX * scroll.scrollSensitivity;
        float dy = distanceY * scroll.scrollSensitivity;

        if (scroll.scrollMode == Scroll.SCROLL_MODE_SINGLE_AXIS) {
            if (scroll.scrollLockAxis == 0) scroll.scrollLockAxis = (Math.abs(dx) >= Math.abs(dy)) ? 1 : 2;
            if (scroll.scrollLockAxis == 1) dy = 0f; else dx = 0f;
        } else if (scroll.scrollMode == Scroll.SCROLL_MODE_GRID) {
            if (Math.abs(dx) >= Math.abs(dy)) dy = 0f; else dx = 0f;
        }

        if (editor.wordWrap.isWordWrapEnabled) dx = 0f;
        if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) dx = -dx;

        float maxX = !editor.wordWrap.isWordWrapEnabled ? (scroll.dragMaxScrollX = scroll.getMaxScrollXForClamp()) : 0f;
        float maxY = scroll.getMaxScrollYForClamp();
        float nextX = scroll.scrollX + dx;
        float nextY = scroll.scrollY + dy;

        if (scroll.stretch.stretchOverscrollEnabled || scroll.edge.edgeEffectEnabled) {
            if (!editor.wordWrap.isWordWrapEnabled) {
                if (nextX < 0f && dx < 0f) { scroll.stretch.pullStretchX(dx, false); scroll.edge.pullLeft(dx, e2.getY()); nextX = 0f; }
                else if (nextX > maxX && dx > 0f) { scroll.stretch.pullStretchX(dx, true); scroll.edge.pullRight(dx, e2.getY()); nextX = maxX; }
                else scroll.edge.releaseHorizontal();
            }
            if (nextY < 0f && dy < 0f) { scroll.stretch.pullStretchY(dy, false); scroll.edge.pullTop(dy, e2.getX()); nextY = 0f; }
            else if (nextY > maxY && dy > 0f) { scroll.stretch.pullStretchY(dy, true); scroll.edge.pullBottom(dy, e2.getX()); nextY = maxY; }
            else scroll.edge.releaseVertical();
        } else {
            if (!editor.wordWrap.isWordWrapEnabled && ((scroll.scrollX <= 0f && dx < 0f) || (scroll.scrollX >= maxX && dx > 0f))) { dx = 0f; nextX = scroll.scrollX; }
            scroll.edge.releaseVertical(); scroll.edge.releaseHorizontal();
        }

        scroll.scrollY = nextY; scroll.scrollX = nextX;
        scroll.clampScrollY(); scroll.clampScrollX(); scroll.showScrollBar();

        editor.removeCallbacks(scroll.delayedWindowCheck);
        if (Math.abs(distanceY) > editor.textRender.lineHeight * 6f) editor.fileIO.checkAndLoadWindow();
        else editor.postDelayed(scroll.delayedWindowCheck, 60);

        editor.invalidate(); return true;
    }

    public boolean handleFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        int sX = Math.round(scroll.scrollX), sY = Math.round(scroll.scrollY);
        int maxX = Math.round(scroll.getMaxScrollXForClamp());
        int maxY = Math.round(scroll.getMaxScrollYForClamp());

        float vx = velocityX * scroll.flingSensitivity, vy = velocityY * scroll.flingSensitivity;
        if (scroll.scrollMode == Scroll.SCROLL_MODE_SINGLE_AXIS) {
            int axis = scroll.scrollLockAxis; if (axis == 0) axis = (Math.abs(vx) >= Math.abs(vy)) ? 1 : 2;
            if (axis == 1) vy = 0f; else vx = 0f;
        } else if (scroll.scrollMode == Scroll.SCROLL_MODE_GRID) {
            if (Math.abs(vx) >= Math.abs(vy)) vy = 0f; else vx = 0f;
        }

        if (editor.wordWrap.isWordWrapEnabled) vx = 0f;
        if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) vx = -vx;

        int oX = (scroll.flingBounceEnabled && !editor.wordWrap.isWordWrapEnabled) ? scroll.getFlingOverScrollX() : 0;
        int oY = scroll.flingBounceEnabled ? scroll.getFlingOverScrollY() : 0;

        scroll.scroller.fling(sX, sY, (int) -vx, (int) -vy, 0, maxX, 0, maxY, oX, oY);
        editor.postInvalidateOnAnimation(); return true;
    }
}
