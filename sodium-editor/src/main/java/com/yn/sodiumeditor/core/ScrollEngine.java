package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

public final class ScrollEngine implements ScrollBar.ScrollBoundsProvider, ScrollHandler.ScrollBoundsProvider {
    private final SodiumEditor view;
    public final ScrollConfig config;
    public final FlingHandler flingHandler;
    public final ScrollHandler scrollHandler;
    public final ScrollBounds scrollBounds;
    public final ScrollBar scrollBar;
    public final Stretch stretch;

    // Direct field access for backward compatibility
    public float scrollY;
    public float scrollX;
    public float maxLineWidthForScroll;
    public float maxTextStartXForScroll;
    public float maxScrollXForScroll;
    public float dragMaxScrollX;
    public boolean scrollerIsScrolling;
    public float autoScrollX;
    public float autoScrollY;
    public int scrollLockAxis;
    public float stretchX;
    public float stretchY;
    public int stretchDirX;
    public int stretchDirY;
    public OverScroller scroller;
    // Scrollbar fields for backward compatibility
    public boolean scrollBarEnabled;
    public int scrollBarColor;
    public float scrollBarWidthPx;
    public float scrollBarMinThumbPx;
    public float scrollBarCornerRadiusPx;
    public float scrollBarMarginPx;
    public boolean scrollBarFadeEnabled;
    public long scrollBarFadeDelayMs;
    public long scrollBarFadeDurationMs;
    public float scrollBarAlpha;
    public int scrollBarHaloColor;
    public float scrollBarHaloSizePx;
    public boolean draggingScrollBar;
    public float scrollBarDragOffset;
    public android.graphics.RectF scrollBarThumbRect;
    public Runnable scrollBarHideRunnable;
    // Stretch settings
    public boolean stretchOverscrollEnabled;
    public float stretchOverscrollStrength;

    public ScrollEngine(SodiumEditor view) {
        this.view = view;
        this.config = new ScrollConfig();
        this.stretch = new Stretch(view, config);
        this.flingHandler = new FlingHandler(view, config);
        this.scrollBounds = new ScrollBounds(view, config, flingHandler);
        this.scrollBar = new ScrollBar(view, config, this);
        this.scrollHandler = new ScrollHandler(view, config, stretch, this);
        this.scroller = flingHandler.scroller;
        // Initialize direct fields from config
        this.scrollY = config.scrollY;
        this.scrollX = config.scrollX;
        this.stretchX = config.stretchX;
        this.stretchY = config.stretchY;
        this.stretchDirX = config.stretchDirX;
        this.stretchDirY = config.stretchDirY;
        // Initialize scrollbar fields from config
        this.scrollBarEnabled = config.scrollBarEnabled;
        this.scrollBarColor = config.scrollBarColor;
        this.scrollBarWidthPx = config.scrollBarWidthPx;
        this.scrollBarMinThumbPx = config.scrollBarMinThumbPx;
        this.scrollBarCornerRadiusPx = config.scrollBarCornerRadiusPx;
        this.scrollBarMarginPx = config.scrollBarMarginPx;
        this.scrollBarFadeEnabled = config.scrollBarFadeEnabled;
        this.scrollBarFadeDelayMs = config.scrollBarFadeDelayMs;
        this.scrollBarFadeDurationMs = config.scrollBarFadeDurationMs;
        this.scrollBarAlpha = config.scrollBarAlpha;
        this.scrollBarHaloColor = config.scrollBarHaloColor;
        this.scrollBarHaloSizePx = config.scrollBarHaloSizePx;
        this.draggingScrollBar = config.draggingScrollBar;
        this.scrollBarDragOffset = config.scrollBarDragOffset;
        this.scrollBarThumbRect = scrollBar.scrollBarThumbRect;
        this.scrollBarHideRunnable = scrollBar.scrollBarHideRunnable;
        // Initialize stretch settings
        this.stretchOverscrollEnabled = config.stretchOverscrollEnabled;
        this.stretchOverscrollStrength = config.stretchOverscrollStrength;
    }

    @Override
    public float getMaxScrollYForClamp() {
        return scrollBounds.getMaxScrollYForClamp();
    }

    @Override
    public float getMaxScrollXForClamp() {
        return scrollBounds.getMaxScrollXForClamp();
    }

    @Override
    public void clampScrollX() {
        scrollBounds.clampScrollX();
    }

    @Override
    public void clampScrollY() {
        scrollBounds.clampScrollY();
    }

    public void computeScroll() {
        if (flingHandler.scroller.computeScrollOffset()) {
            config.scrollerIsScrolling = true;
            scrollerIsScrolling = true;
            float rawY = flingHandler.scroller.getCurrY();
            float rawX = flingHandler.scroller.getCurrX();
            config.scrollY = rawY;
            config.scrollX = rawX;
            scrollY = rawY;
            scrollX = rawX;
            scrollBounds.getMaxScrollXForClamp();
            scrollBounds.getMaxScrollYForClamp();
            scrollBounds.clampScrollY();
            scrollBounds.clampScrollX();
            maxLineWidthForScroll = config.maxLineWidthForScroll;
            maxTextStartXForScroll = config.maxTextStartXForScroll;
            maxScrollXForScroll = config.maxScrollXForScroll;
            dragMaxScrollX = config.dragMaxScrollX;
            stretchX = config.stretchX;
            stretchY = config.stretchY;
            stretchDirX = config.stretchDirX;
            stretchDirY = config.stretchDirY;
            scrollBarEnabled = config.scrollBarEnabled;
            scrollBarAlpha = config.scrollBarAlpha;
            draggingScrollBar = config.draggingScrollBar;
            view.removeCallbacks(view.delayedWindowCheck);
            view.maybeKickWindowLoad(view.getGlobalLineForY(scrollY));
            view.postDelayed(view.delayedWindowCheck, 40);
            scrollBar.showScrollBar();
            view.postInvalidateOnAnimation();
        } else {
            if (scrollerIsScrolling) {
                scrollerIsScrolling = false;
                config.scrollerIsScrolling = false;
                scrollBar.showScrollBar();
                if (config.stretchOverscrollEnabled) {
                    stretch.releaseStretch();
                }
                if (config.flingBounceEnabled) {
                    int maxX = Math.round(getMaxScrollXForClamp());
                    int maxY = Math.round(getMaxScrollYForClamp());
                    if (scrollX < 0 || scrollX > maxX || scrollY < 0 || scrollY > maxY) {
                        if (flingHandler.scroller.springBack(
                                Math.round(scrollX), Math.round(scrollY), 0, maxX, 0, maxY)) {
                            scrollerIsScrolling = true;
                            config.scrollerIsScrolling = true;
                            view.postInvalidateOnAnimation();
                            return;
                        }
                    }
                }
                view.checkAndLoadWindow();
                if (view.wrapWordState.isWordWrapEnabled
                        && view.wrapWordState.wrapPrefixRebuildPending
                        && !view.wrapWordState.wrapPrefixBuilding) {
                    view.wrapWordState.wrapPrefixRebuildPending = false;
                    view.wrapWordBuilder.schedulePrefixRebuildUpToWindow(view);
                }
                if (view.selectionState.hasSelection()) view.popupTouchHandler.showPopupAtSelection();
            }
        }
    }

    public void startFlingStopAnimation(float targetX, float targetY) {
        cancelFlingStopAnimation();
        float startX = scrollX;
        float startY = scrollY;
        float dx = targetX - startX;
        float dy = targetY - startY;
        if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
            scrollX = targetX;
            scrollY = targetY;
            config.scrollX = targetX;
            config.scrollY = targetY;
            scrollBounds.clampScrollY();
            scrollBounds.clampScrollX();
            return;
        }
        view.flingStopAnimator = ValueAnimator.ofFloat(0f, 1f);
        view.flingStopAnimator.setDuration(SodiumEditor.FLING_STOP_ANIM_DURATION_MS);
        view.flingStopAnimator.setInterpolator(new DecelerateInterpolator());
        view.flingStopAnimator.addUpdateListener(
                a -> {
                    float t = (float) a.getAnimatedValue();
                    scrollX = startX + dx * t;
                    scrollY = startY + dy * t;
                    config.scrollX = scrollX;
                    config.scrollY = scrollY;
                    scrollBounds.clampScrollY();
                    scrollBounds.clampScrollX();
                    view.removeCallbacks(view.delayedWindowCheck);
                    view.maybeKickWindowLoad(view.getGlobalLineForY(scrollY));
                    view.postDelayed(view.delayedWindowCheck, 40);
                    view.postInvalidateOnAnimation();
                });
        view.flingStopAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.flingStopAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        view.flingStopAnimator = null;
                    }
                });
        view.flingStopAnimator.start();
    }

    public void cancelFlingStopAnimation() {
        if (view.flingStopAnimator != null) {
            view.flingStopAnimator.cancel();
            view.flingStopAnimator = null;
        }
    }

    public void scrollToLineFastForSelectAll(int line, int ch) {
        if (view.wrapWordState.isWordWrapEnabled
                && (!view.wrapWordMetrics.wrapMetricsReady || view.wrapWordMetrics.wrapLinePrefix == null)) {
            scrollY = Math.max(0f, (line - 5) * view.lineHeight);
            config.scrollY = scrollY;
        } else {
            int targetVisual = view.getVisualIndexForLineAndChar(line, ch);
            scrollY = Math.max(0f, (targetVisual - 5) * view.lineHeight);
            config.scrollY = scrollY;
        }
        scrollBounds.clampScrollY();
    }

    public float getDrawLineTop(int globalLine) {
        int drawIndex = globalLine;
        if (view.foldState.isCodeFoldingEnabled()) {
            drawIndex = view.getVisibleIndexForGlobalLine(globalLine);
        }
        return (drawIndex - view.drawBaseLine) * view.lineHeight;
    }

    public float getDrawLineBottom(int globalLine) {
        return getDrawLineTop(globalLine) + view.lineHeight;
    }

    public float getHitTestBaseY() {
        int baseLine = (int) (config.scrollY / view.lineHeight);
        if (baseLine < 0) baseLine = 0;
        return baseLine * view.lineHeight;
    }

    public void ensureLineInWindow(int globalLine, boolean blockingIfAbsent) {
        view.inlinePredictionState.clearActiveSuggestion();
        if (globalLine >= view.windowStartLine
                && globalLine < view.windowStartLine + view.linesWindow.size()) return;
        if (view.sourceFile != null) {
            int targetStart = Math.max(0, globalLine - view.prefetchLines);
            view.loadWindowAround(targetStart, null);
        }
    }

    private float getKeyboardBarrierPaddingInternal() {
        return Math.min(SodiumEditor.BOTTOM_SCROLL_OFFSET, view.keyboardHeight * 0.4f);
    }

    private float getBottomBarrierPaddingInternal() {
        return SodiumEditor.BOTTOM_SCROLL_OFFSET;
    }

    public void setScrollMode(int mode) {
        scrollHandler.setScrollMode(mode);
    }

    public float getScrollXValue() {
        return scrollHandler.getScrollXValue();
    }

    public float getScrollYValue() {
        return scrollHandler.getScrollYValue();
    }

    public void setScrollPosition(float x, float y) {
        scrollHandler.setScrollPosition(x, y);
    }

    public void setScrollSensitivity(float sensitivity) {
        scrollHandler.setScrollSensitivity(sensitivity);
    }

    public void setFlingSensitivity(float sensitivity) {
        flingHandler.setFlingSensitivity(sensitivity);
    }

    public void setScrollBarEnabled(boolean enabled) {
        scrollBar.setScrollBarEnabled(enabled);
        scrollBarEnabled = config.scrollBarEnabled;
    }

    public void setScrollBarColor(int color) {
        scrollBar.setScrollBarColor(color);
        scrollBarColor = config.scrollBarColor;
    }

    public void setScrollBarWidthPx(float px) {
        scrollBar.setScrollBarWidthPx(px);
        scrollBarWidthPx = config.scrollBarWidthPx;
    }

    public void setScrollBarMinThumbPx(float px) {
        scrollBar.setScrollBarMinThumbPx(px);
        scrollBarMinThumbPx = config.scrollBarMinThumbPx;
    }

    public void setScrollBarFadeEnabled(boolean enabled) {
        scrollBar.setScrollBarFadeEnabled(enabled);
        scrollBarFadeEnabled = config.scrollBarFadeEnabled;
    }

    public void setScrollBarFadeDelayMs(long ms) {
        scrollBar.setScrollBarFadeDelayMs(ms);
        scrollBarFadeDelayMs = config.scrollBarFadeDelayMs;
    }

    public void setScrollBarFadeDurationMs(long ms) {
        scrollBar.setScrollBarFadeDurationMs(ms);
        scrollBarFadeDurationMs = config.scrollBarFadeDurationMs;
    }

    public void setScrollBarHaloColor(int color) {
        scrollBar.setScrollBarHaloColor(color);
        scrollBarHaloColor = config.scrollBarHaloColor;
    }

    public void setScrollBarHaloSizePx(float px) {
        scrollBar.setScrollBarHaloSizePx(px);
        scrollBarHaloSizePx = config.scrollBarHaloSizePx;
    }

    public void setScrollBarCornerRadiusPx(float px) {
        scrollBar.setScrollBarCornerRadiusPx(px);
        scrollBarCornerRadiusPx = config.scrollBarCornerRadiusPx;
    }

    public void setScrollBarMarginPx(float px) {
        scrollBar.setScrollBarMarginPx(px);
        scrollBarMarginPx = config.scrollBarMarginPx;
    }

    public void setStretchOverscrollEnabled(boolean enabled) {
        stretch.setStretchOverscrollEnabled(enabled);
        stretchOverscrollEnabled = config.stretchOverscrollEnabled;
    }

    public void setStretchOverscrollStrength(float strength) {
        stretch.setStretchOverscrollStrength(strength);
        stretchOverscrollStrength = config.stretchOverscrollStrength;
    }

    public void setFlingBounceEnabled(boolean enabled) {
        flingHandler.setFlingBounceEnabled(enabled);
    }

    public void setFlingBounceDistancePx(int px) {
        flingHandler.setFlingBounceDistancePx(px);
    }

    public void setFlingBounceDistanceFactor(float factor) {
        flingHandler.setFlingBounceDistanceFactor(factor);
    }

    public void abortScroller() {
        flingHandler.abortScroller();
    }

    public boolean onFling(float velocityX, float velocityY) {
        return flingHandler.onFling(velocityX, velocityY);
    }

    public boolean onScroll(MotionEvent e2, float distanceX, float distanceY) {
        return scrollHandler.onScroll(e2, distanceX, distanceY);
    }

    public int getFlingOverScrollX() {
        return flingHandler.getFlingOverScrollX();
    }

    public int getFlingOverScrollY() {
        return flingHandler.getFlingOverScrollY();
    }

    public void drawScrollBar(Canvas canvas) {
        scrollBar.drawScrollBar(canvas);
    }

    public void showScrollBar() {
        scrollBar.showScrollBar();
    }

    public void keepCursorVisibleHorizontally() {
        if (view.zoomGestureHandler.isScaleInProgress()
                || view.zoomGestureHandler.isScaling()
                || view.zoomGestureHandler.isMultiTouchActive()) {
            return;
        }
        int cursorVisualIndex = view.getVisualIndexForLineAndChar(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
        float cursorYTop = cursorVisualIndex * view.lineHeight;
        float cursorYBottom = cursorYTop + view.lineHeight;
        int viewHeight = view.getHeight() - view.keyboardHeight;
        if (viewHeight <= 0) viewHeight = view.getHeight();

        float bottomPadding =
                (view.keyboardHeight > 0)
                        ? getKeyboardBarrierPaddingInternal()
                        : getBottomBarrierPaddingInternal();
        float effectiveVisibleHeight = Math.max(0f, viewHeight - bottomPadding);
        float visibleTop = scrollY;
        float visibleBottom = scrollY + effectiveVisibleHeight;

        if (cursorYBottom > visibleBottom) scrollY = cursorYBottom - (viewHeight - bottomPadding);
        else if (cursorYTop < visibleTop) scrollY = cursorYTop;

        if (view.keyboardHeight > 0) {
            float keyboardTop = view.getHeight() - view.keyboardHeight;
            float paddingAboveKeyboard = getKeyboardBarrierPaddingInternal();
            float currentCursorViewY = cursorYBottom - scrollY;
            if (currentCursorViewY >= keyboardTop - paddingAboveKeyboard) {
                scrollY =
                        cursorYBottom - (view.getHeight() - view.keyboardHeight - paddingAboveKeyboard);
            }
        }
        scrollBounds.clampScrollY();
        config.scrollY = scrollY;

        if (!view.wrapWordState.isWordWrapEnabled) {
            String line = view.getLineTextForRender(view.cursorState.getCursorLine());
            int safeChar =
                    Math.min(view.cursorState.getCursorChar(), view.getLogicalLineLength(view.cursorState.getCursorLine(), line));
            float cursorX = view.getCaretXForLine(line, view.cursorState.getCursorLine(), safeChar);

            float viewLeft = view.lineNumberRenderer.getContentViewLeft(view.isRtl);
            float viewRight = view.lineNumberRenderer.getContentViewRight(view.getWidth(), view.isRtl);
            float scrollMargin = 50f;
            float effectiveScrollX = view.getEffectiveScrollX();
            float cursorViewX = view.getTextStartX() + cursorX - effectiveScrollX;
            float minView = viewLeft + scrollMargin;
            float maxView = viewRight - scrollMargin;
            if (cursorViewX < minView) {
                effectiveScrollX = view.getTextStartX() + cursorX - minView;
            } else if (cursorViewX > maxView) {
                effectiveScrollX = view.getTextStartX() + cursorX - maxView;
            }
            float max = getMaxScrollXForClamp();
            float minEffective = view.isRtl ? -max : 0f;
            float maxEffective = view.isRtl ? 0f : max;
            if (effectiveScrollX < minEffective) effectiveScrollX = minEffective;
            if (effectiveScrollX > maxEffective) effectiveScrollX = maxEffective;
            scrollX = view.isRtl ? -effectiveScrollX : effectiveScrollX;
            config.scrollX = scrollX;
        } else {
            scrollX = 0f;
            config.scrollX = 0f;
        }

        scrollBounds.clampScrollX();
        view.invalidate();
    }

    /**
     * Aborts the scroll animation for zoom operations.
     */
    public void abortScrollAnimationForZoom() {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
    }
}
