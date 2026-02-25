package com.yn.sodiumeditor;

public final class ScrollBounds {
    private final SodiumEditorView view;
    private final ScrollConfig config;
    private final FlingHandler flingHandler;

    public ScrollBounds(SodiumEditorView view, ScrollConfig config, FlingHandler flingHandler) {
        this.view = view;
        this.config = config;
        this.flingHandler = flingHandler;
    }

    public float getMaxScrollYForClamp() {
        if (view.wrapWordState.isWordWrapEnabled
                && !view.wrapWordMetrics.wrapMetricsReady
                && (view.zoomManager.isScaling() || view.zoomManager.isJustFinishedScale())) {
            return config.scrollY;
        }

        float effectiveHeight = (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
        int lineCount =
                view.wrapWordState.isWordWrapEnabled
                        ? view.wrapWordMapper.getTotalVisualLineCount(view, view.getVisibleLineCount())
                        : (view.foldManager.isCodeFoldingEnabled
                        ? view.getVisibleLineCount()
                        : Math.max(1, view.getLinesCount()));
        if (view.wrapWordState.isWordWrapEnabled && (view.selectionManager.isSelectAllActive() || view.selectionManager.isEntireFileSelected())) {
            lineCount = Math.max(lineCount, view.selectionManager.selEndLine + 1);
        }
        if (view.isEof) {
            float paddingToUse =
                    (view.keyboardHeight > 0)
                            ? getKeyboardBarrierPaddingInternal()
                            : getBottomBarrierPaddingInternal();
            return Math.max(0f, lineCount * view.lineHeight - (effectiveHeight - paddingToUse));
        }
        float virtualExtraSpace = Math.max(view.prefetchLines * view.lineHeight, 2000f);
        return Math.max(0f, lineCount * view.lineHeight + virtualExtraSpace - effectiveHeight);
    }

    public void clampScrollY() {
        if (!view.wrapWordState.isWordWrapEnabled && view.isWindowLoading && config.scrollY < view.windowStartLine * view.lineHeight) {
            boolean allowAboveWindow = config.scrollerIsScrolling || view.flingStopAnimator != null;
            if (!allowAboveWindow) {
                config.scrollY = view.windowStartLine * view.lineHeight;
                view.scrollManager.scrollY = config.scrollY;
                if (!flingHandler.scroller.isFinished()) flingHandler.scroller.abortAnimation();
            }
        }

        float maxScroll = getMaxScrollYForClamp();
        boolean allowFlingOverscroll = config.flingBounceEnabled && config.scrollerIsScrolling;
        if (allowFlingOverscroll) {
            int over = flingHandler.getFlingOverScrollY();
            if (config.scrollY < -over) config.scrollY = -over;
            if (config.scrollY > maxScroll + over) config.scrollY = maxScroll + over;
            view.scrollManager.scrollY = config.scrollY;
            return;
        }

        if (config.scrollY < 0) config.scrollY = 0;
        if (config.scrollY > maxScroll) {
            config.scrollY = maxScroll;
            view.scrollManager.scrollY = config.scrollY;
            if (view.isEof && !flingHandler.scroller.isFinished()) flingHandler.scroller.abortAnimation();
        }
    }

    public float getMaxScrollXForClamp() {
        if (view.wrapWordState.isWordWrapEnabled) return 0f;
        float rawMaxWidth = getMaxLineWidthInWindowInternal();
        if (rawMaxWidth > config.maxLineWidthForScroll) {
            config.maxLineWidthForScroll = rawMaxWidth;
            view.scrollManager.maxLineWidthForScroll = rawMaxWidth;
        }
        float textStartX = view.getTextStartX();
        if (textStartX > config.maxTextStartXForScroll) {
            config.maxTextStartXForScroll = textStartX;
            view.scrollManager.maxTextStartXForScroll = textStartX;
        }
        float effectiveTextStartX = Math.max(textStartX, config.maxTextStartXForScroll);
        float candidateMax = Math.max(0f, config.maxLineWidthForScroll - (view.getWidth() - effectiveTextStartX));
        if (candidateMax > config.maxScrollXForScroll) {
            config.maxScrollXForScroll = candidateMax;
            view.scrollManager.maxScrollXForScroll = candidateMax;
        }
        return config.maxScrollXForScroll;
    }

    public void clampScrollX() {
        if (view.wrapWordState.isWordWrapEnabled) {
            config.scrollX = 0f;
            view.scrollManager.scrollX = 0f;
            return;
        }
        float max = (view.pointerDown && config.dragMaxScrollX >= 0f) ? config.dragMaxScrollX : getMaxScrollXForClamp();
        boolean allowFlingOverscroll = config.flingBounceEnabled && config.scrollerIsScrolling;
        if (allowFlingOverscroll) {
            int over = flingHandler.getFlingOverScrollX();
            if (config.scrollX < -over) config.scrollX = -over;
            if (config.scrollX > max + over) config.scrollX = max + over;
            view.scrollManager.scrollX = config.scrollX;
            return;
        }
        if (config.scrollX < 0) config.scrollX = 0;
        if (config.scrollX > max) config.scrollX = max;
        view.scrollManager.scrollX = config.scrollX;
    }

    private float getMaxLineWidthInWindowInternal() {
        return Math.max(view.currentMaxWindowLineWidth, view.globalMaxLineWidth);
    }

    private float getKeyboardBarrierPaddingInternal() {
        return Math.min(SodiumEditorView.BOTTOM_SCROLL_OFFSET, view.keyboardHeight * 0.4f);
    }

    private float getBottomBarrierPaddingInternal() {
        return SodiumEditorView.BOTTOM_SCROLL_OFFSET;
    }
}
