package com.yn.sodiumeditor;

import android.view.MotionEvent;

public final class ScrollHandler {
    private final SodiumEditorView view;
    private final ScrollConfig config;
    private final Stretch stretch;
    private final ScrollBoundsProvider scrollBoundsProvider;

    public interface ScrollBoundsProvider {
        float getMaxScrollXForClamp();
        float getMaxScrollYForClamp();
        void clampScrollX();
        void clampScrollY();
    }

    public ScrollHandler(SodiumEditorView view, ScrollConfig config, Stretch stretch, ScrollBoundsProvider scrollBoundsProvider) {
        this.view = view;
        this.config = config;
        this.stretch = stretch;
        this.scrollBoundsProvider = scrollBoundsProvider;
    }

    public boolean onScroll(MotionEvent e2, float distanceX, float distanceY) {
        if (e2.getPointerCount() > 1) return true;
        if (view.zoomGestureHandler.isScaling() || view.zoomGestureHandler.isScaleInProgress()) return true;
        if (view.zoomGestureHandler.isJustFinishedScale()) return true;
        if (view.wrapWordState.isWordWrapEnabled && view.wrapWordState.wrapPrefixBuilding) {
            view.wrapWordBuilder.cancelPrefixRebuildForInteraction();
        }
        if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return false;

        view.movedSinceDown = true;
        float dx = distanceX * config.scrollSensitivity;
        float dy = distanceY * config.scrollSensitivity;
        if (config.scrollMode == ScrollConfig.SCROLL_MODE_SINGLE_AXIS) {
            if (config.scrollLockAxis == 0) {
                config.scrollLockAxis = (Math.abs(dx) >= Math.abs(dy)) ? 1 : 2;
            }
            if (config.scrollLockAxis == 1) dy = 0f;
            else dx = 0f;
        } else if (config.scrollMode == ScrollConfig.SCROLL_MODE_GRID) {
            if (Math.abs(dx) >= Math.abs(dy)) dy = 0f;
            else dx = 0f;
        }
        if (view.wrapWordState.isWordWrapEnabled) {
            dx = 0f;
        }
        if (view.isRtl && !view.wrapWordState.isWordWrapEnabled) {
            dx = -dx;
        }
        float maxX = 0f;
        if (!view.wrapWordState.isWordWrapEnabled) {
            if (config.dragMaxScrollX < 0f) {
                config.dragMaxScrollX = scrollBoundsProvider.getMaxScrollXForClamp();
                view.scrollManager.dragMaxScrollX = config.dragMaxScrollX;
            } else {
                float freshMax = scrollBoundsProvider.getMaxScrollXForClamp();
                if (freshMax > config.dragMaxScrollX) config.dragMaxScrollX = freshMax;
                view.scrollManager.dragMaxScrollX = config.dragMaxScrollX;
            }
            maxX = config.dragMaxScrollX;
        }
        float maxY = scrollBoundsProvider.getMaxScrollYForClamp();

        float nextX = config.scrollX + dx;
        float nextY = config.scrollY + dy;
        if (config.stretchOverscrollEnabled) {
            if (!view.wrapWordState.isWordWrapEnabled) {
                if (nextX < 0f && dx < 0f) {
                    stretch.pullStretchX(dx, false);
                    nextX = 0f;
                } else if (nextX > maxX && dx > 0f) {
                    stretch.pullStretchX(dx, true);
                    nextX = maxX;
                }
            }
            if (nextY < 0f && dy < 0f) {
                stretch.pullStretchY(dy, false);
                nextY = 0f;
            } else if (nextY > maxY && dy > 0f) {
                stretch.pullStretchY(dy, true);
                nextY = maxY;
            }
        } else {
            if (!view.wrapWordState.isWordWrapEnabled) {
                if ((config.scrollX <= 0f && dx < 0f) || (config.scrollX >= maxX && dx > 0f)) {
                    dx = 0f;
                    nextX = config.scrollX;
                }
            }
        }

        config.scrollY = nextY;
        config.scrollX = nextX;
        scrollBoundsProvider.clampScrollY();
        scrollBoundsProvider.clampScrollX();

        view.removeCallbacks(view.delayedWindowCheck);
        if (Math.abs(distanceY) > view.lineHeight * 6f) {
            view.checkAndLoadWindow();
        } else {
            view.postDelayed(view.delayedWindowCheck, 60);
        }

        if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.hidePopup();
        view.cursorAnimator.resetCursorBlink();
        view.invalidate();
        return true;
    }

    public void setScrollMode(int mode) {
        if (mode != ScrollConfig.SCROLL_MODE_SINGLE_AXIS && mode != ScrollConfig.SCROLL_MODE_GRID && mode != ScrollConfig.SCROLL_MODE_FREE) {
            return;
        }
        config.scrollMode = mode;
    }

    public void setScrollSensitivity(float sensitivity) {
        if (sensitivity <= 0f) return;
        config.scrollSensitivity = sensitivity;
    }

    public float getScrollXValue() {
        return config.scrollX;
    }

    public float getScrollYValue() {
        return config.scrollY;
    }

    public void setScrollPosition(float x, float y) {
        config.scrollX = x;
        config.scrollY = y;
        scrollBoundsProvider.clampScrollX();
        scrollBoundsProvider.clampScrollY();
        view.invalidate();
    }
}
