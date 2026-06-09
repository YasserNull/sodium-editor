package com.yn.sodiumeditor.core.scroll;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Calculates scroll boundaries and clamping logic.
 */
public class ScrollBounds {
    private final SodiumEditor editor;
    private final Scroll scroll;

    public ScrollBounds(SodiumEditor editor, Scroll scroll) {
        this.editor = editor;
        this.scroll = scroll;
    }

    public float getMaxScrollXForClamp() {
        if (editor.wordWrap.isWordWrapEnabled) return 0f;
        float rawMaxWidth = editor.windowRender.globalMaxLineWidth;
        float textStartX = editor.layout.getTextStartX();
        float extraSpace = 100f;
        float candidateMax = Math.max(0f, (rawMaxWidth + extraSpace) - (editor.getWidth() - textStartX));
        if (rawMaxWidth > scroll.maxLineWidthForScroll) scroll.maxLineWidthForScroll = rawMaxWidth;
        return candidateMax;
    }

    public float getMaxScrollYForClamp() {
        if (editor.wordWrap.isWordWrapEnabled && !editor.wordWrap.wrapMetricsReady && (editor.zoom.isScaling || editor.zoom.mJustFinishedScale)) return scroll.scrollY;
        float effectiveHeight = (editor.view.keyboardHeight > 0) ? editor.getHeight() - editor.view.keyboardHeight : editor.getHeight();
        int lineCount = editor.wordWrap.isWordWrapEnabled ? editor.wordWrap.getTotalVisualLineCount() : Math.max(1, editor.view.getLinesCount());
        if (editor.wordWrap.isWordWrapEnabled && (editor.selection.isSelectAllActive || editor.selection.isEntireFileSelected)) lineCount = Math.max(lineCount, editor.selection.selEndLine + 1);
        boolean contentEndKnown = editor.fileIO.isEof
                || editor.fileIO.isIndexReady
                || (editor.binaryRender.binaryFileFeaturePolicyActive && editor.binaryRender.binaryDocument != null);
        if (contentEndKnown) {
            float pad = (editor.view.keyboardHeight > 0) ? getKeyboardBarrierPadding() : getBottomBarrierPadding();
            return Math.max(0f, lineCount * editor.textRender.lineHeight - (effectiveHeight - pad));
        }
        float virtualExtra = Math.max(editor.windowRender.prefetchLines * editor.textRender.lineHeight, 2000f);
        return Math.max(0f, lineCount * editor.textRender.lineHeight + virtualExtra - effectiveHeight);
    }

    public void clampScrollX() {
        if (editor.wordWrap.isWordWrapEnabled) { scroll.scrollX = 0f; return; }
        float max = (editor.onTouch.pointerDown && scroll.dragMaxScrollX >= 0f) ? scroll.dragMaxScrollX : getMaxScrollXForClamp();
        if (scroll.flingBounceEnabled && scroll.scrollerIsScrolling) {
            int over = scroll.getFlingOverScrollX();
            if (scroll.scrollX < -over) scroll.scrollX = -over;
            if (scroll.scrollX > max + over) scroll.scrollX = max + over;
            return;
        }
        scroll.scrollX = Math.max(0f, Math.min(scroll.scrollX, max));
    }

    public void clampScrollY() {
        if (!editor.wordWrap.isWordWrapEnabled && editor.fileIO.isWindowLoading && scroll.scrollY < editor.windowRender.windowStartLine * editor.textRender.lineHeight) {
            if (!(scroll.scrollerIsScrolling || scroll.flingStopAnimator != null)) {
                scroll.scrollY = editor.windowRender.windowStartLine * editor.textRender.lineHeight;
                if (!scroll.scroller.isFinished()) scroll.scroller.abortAnimation();
            }
        }
        float maxScroll = getMaxScrollYForClamp();
        if (scroll.flingBounceEnabled && scroll.scrollerIsScrolling) {
            int over = scroll.getFlingOverScrollY();
            if (scroll.scrollY < -over) scroll.scrollY = -over;
            if (scroll.scrollY > maxScroll + over) scroll.scrollY = maxScroll + over;
            return;
        }
        if (scroll.scrollY < 0) scroll.scrollY = 0;
        if (scroll.scrollY > maxScroll) {
            scroll.scrollY = maxScroll;
            if (editor.fileIO.isEof && !scroll.scroller.isFinished()) scroll.scroller.abortAnimation();
        }
    }

    public float getBottomBarrierPadding() {
        float base = Math.max(com.yn.sodiumeditor.renderer.TextRender.BOTTOM_SCROLL_OFFSET, editor.textRender.lineHeight * 2f);
        float min = Math.max(com.yn.sodiumeditor.renderer.TextRender.MIN_BOTTOM_VISIBLE_SPACE, editor.textRender.lineHeight * 2f);
        return Math.max(base, min);
    }

    public float getKeyboardBarrierPadding() {
        if (editor.view.keyboardHeight <= 0) return 0f;
        float min = (editor.textRender.lineHeight > 0f) ? editor.textRender.lineHeight * 2f : com.yn.sodiumeditor.renderer.TextRender.MIN_BOTTOM_VISIBLE_SPACE;
        float max = (editor.textRender.lineHeight > 0f) ? editor.textRender.lineHeight * 3.5f : com.yn.sodiumeditor.renderer.TextRender.BOTTOM_SCROLL_OFFSET;
        return Math.max(min, Math.min(max, editor.view.keyboardHeight * 0.4f));
    }
}
