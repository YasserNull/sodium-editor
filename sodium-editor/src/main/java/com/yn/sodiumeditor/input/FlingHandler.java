package com.yn.sodiumeditor;

import android.widget.OverScroller;

public final class FlingHandler {
    private final SodiumEditorView view;
    private final ScrollConfig config;
    public final OverScroller scroller;

    public FlingHandler(SodiumEditorView view, ScrollConfig config) {
        this.view = view;
        this.config = config;
        this.scroller = new OverScroller(view.getContext());
    }

    public boolean onFling(float velocityX, float velocityY) {
        if (view.zoomManager.isScaling() || view.zoomManager.isScaleInProgress()) return true;
        if (view.zoomManager.isJustFinishedScale()) return true;
        if (view.wrapWordState.isWordWrapEnabled && view.wrapWordState.wrapPrefixBuilding) {
            view.wrapWordBuilder.cancelPrefixRebuildForInteraction();
        }
        if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return false;

        int startX = Math.round(config.scrollX);
        int startY = Math.round(config.scrollY);
        int minX = 0;
        int maxX =
                view.wrapWordState.isWordWrapEnabled
                        ? 0
                        : Math.max(
                        0,
                        Math.round(getMaxLineWidthInWindowInternal() - (view.getTextStartX() - view.lineNumberManager.getContentViewLeft(view.isRtl))));
        int minY = 0;

        float maxScrollYFloat;
        float effectiveHeight =
                (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();

        int lineCount =
                view.wrapWordState.isWordWrapEnabled
                        ? view.wrapWordMapper.getTotalVisualLineCount(view, view.getVisibleLineCount())
                        : (view.foldManager.isCodeFoldingEnabled
                        ? view.getVisibleLineCount()
                        : Math.max(1, view.getLinesCount()));
        if (view.isEof) {
            float paddingToUse =
                    (view.keyboardHeight > 0)
                            ? Math.min(SodiumEditorView.BOTTOM_SCROLL_OFFSET, view.keyboardHeight * 0.4f)
                            : SodiumEditorView.BOTTOM_SCROLL_OFFSET;
            maxScrollYFloat =
                    Math.max(0f, lineCount * view.lineHeight - (effectiveHeight - paddingToUse));
        } else {
            float virtualExtraSpace = Math.max(view.prefetchLines * view.lineHeight, 2000f);
            maxScrollYFloat =
                    Math.max(0f, lineCount * view.lineHeight + virtualExtraSpace - effectiveHeight);
        }
        int maxY = Math.max(0, Math.round(maxScrollYFloat));

        view.removeCallbacks(view.delayedWindowCheck);
        float vx = velocityX * config.flingSensitivity;
        float vy = velocityY * config.flingSensitivity;
        if (config.scrollMode == ScrollConfig.SCROLL_MODE_SINGLE_AXIS) {
            int axis = config.scrollLockAxis;
            if (axis == 0) axis = (Math.abs(vx) >= Math.abs(vy)) ? 1 : 2;
            if (axis == 1) vy = 0f;
            else vx = 0f;
        } else if (config.scrollMode == ScrollConfig.SCROLL_MODE_GRID) {
            if (Math.abs(vx) >= Math.abs(vy)) vy = 0f;
            else vx = 0f;
        }
        if (view.wrapWordState.isWordWrapEnabled) {
            vx = 0f;
        }
        if (view.isRtl && !view.wrapWordState.isWordWrapEnabled) {
            vx = -vx;
        }
        int overX = 0;
        int overY = 0;
        if (config.flingBounceEnabled) {
            if (!view.wrapWordState.isWordWrapEnabled) overX = Math.max(overX, getFlingOverScrollX());
            overY = Math.max(overY, getFlingOverScrollY());
        }
        scroller.fling(
                startX, startY, (int) -vx, (int) -vy, minX, maxX, minY, maxY, overX, overY);
        view.postInvalidateOnAnimation();
        return true;
    }

    public int getFlingOverScrollX() {
        if (!config.flingBounceEnabled) return 0;
        if (config.flingBounceOverScrollPx >= 0) return config.flingBounceOverScrollPx;
        return Math.max(24, Math.round(view.getWidth() * config.flingBounceOverScrollFactor));
    }

    public int getFlingOverScrollY() {
        if (!config.flingBounceEnabled) return 0;
        if (config.flingBounceOverScrollPx >= 0) return config.flingBounceOverScrollPx;
        return Math.max(24, Math.round(view.getHeight() * config.flingBounceOverScrollFactor));
    }

    private float getMaxLineWidthInWindowInternal() {
        return Math.max(view.currentMaxWindowLineWidth, view.globalMaxLineWidth);
    }

    public void setFlingSensitivity(float sensitivity) {
        if (sensitivity <= 0f) return;
        config.flingSensitivity = sensitivity;
    }

    public void setFlingBounceEnabled(boolean enabled) {
        config.flingBounceEnabled = enabled;
    }

    public void setFlingBounceDistancePx(int px) {
        config.flingBounceOverScrollPx = Math.max(0, px);
    }

    public void setFlingBounceDistanceFactor(float factor) {
        if (factor <= 0f) return;
        config.flingBounceOverScrollFactor = factor;
    }

    public boolean isScrolling() {
        return config.scrollerIsScrolling;
    }

    public void abortScroller() {
        if (!scroller.isFinished()) {
            scroller.computeScrollOffset();
            config.scrollX = scroller.getCurrX();
            config.scrollY = scroller.getCurrY();
            scroller.abortAnimation();
        }
    }
}
