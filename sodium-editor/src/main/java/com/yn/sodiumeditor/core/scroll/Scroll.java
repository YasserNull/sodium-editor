package com.yn.sodiumeditor.core.scroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Main facade for scrolling and scroll bar management.
 */
public class Scroll {
    private final SodiumEditor editor;

    // Components
    public final OverScroller scroller;
    public final Stretch stretch;
    public final Edge edge;
    public final ScrollBar bar;
    public final ScrollBounds bounds;
    public final ScrollHandler handler;

    // --- State (Kept as fields for project compatibility) ---
    public android.view.GestureDetector gestureDetector;
    public float scrollY = 0f;
    public float scrollX = 0f;
    public int scrollMode = SCROLL_MODE_FREE;
    public static final int SCROLL_MODE_SINGLE_AXIS = 0;
    public static final int SCROLL_MODE_GRID = 1;
    public static final int SCROLL_MODE_FREE = 2;
    
    public float scrollSensitivity = 1f;
    public float flingSensitivity = 1f;
    public boolean flingBounceEnabled = false;
    public int flingBounceOverScrollPx = -1;
    public float flingBounceOverScrollFactor = 0.2f;

    public boolean scrollBarEnabled = true;
    public int scrollBarColor = 0x80FFFFFF;
    public float scrollBarWidthPx = 6f;
    public float scrollBarMinThumbPx = 24f;
    public float scrollBarCornerRadiusPx = 6f;
    public float scrollBarMarginPx = 2f;
    public boolean scrollBarFadeEnabled = true;
    public long scrollBarFadeDelayMs = 1000;
    public long scrollBarFadeDurationMs = 200;
    public float scrollBarAlpha = 0f;
    public int scrollBarHaloColor = 0x40FFFFFF;
    public float scrollBarHaloSizePx = 8f;
    public boolean draggingScrollBar = false;
    public float scrollBarDragOffset = 0f;
    
    // Aliases for ScrollBar fields
    public android.graphics.RectF scrollBarThumbRect;
    public Runnable scrollBarHideRunnable;

    public int scrollLockAxis = 0;
    public float maxLineWidthForScroll = 0f;
    public float maxTextStartXForScroll = 0f;
    public float maxScrollXForScroll = 0f;
    public float dragMaxScrollX = -1f;
    public boolean scrollerIsScrolling = false;

    @Nullable public ValueAnimator flingStopAnimator;
    public static final long FLING_STOP_ANIM_DURATION_MS = 90;
    public final Runnable delayedWindowCheck;

    public final Runnable autoScrollRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (editor.selectionHandles.draggingHandle == 0) return;
                    if (autoScrollX != 0 || autoScrollY != 0) {
                        scrollY += autoScrollX;
                        float nextY =  scrollY + autoScrollY;
                        if (!editor.fileIO.isIndexReady && !editor.fileIO.isEof && editor.fileIO.isWindowLoading) {
                            float effectiveHeight =
                                    (editor.view.keyboardHeight > 0) ? editor.getHeight() - editor.view.keyboardHeight : editor.getHeight();
                            float winTop = editor.windowRender.windowStartLine * editor.textRender.lineHeight;
                            float winBottom = (editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) * editor.textRender.lineHeight;
                            float maxY = Math.max(0f, winBottom - effectiveHeight);
                            if (autoScrollY > 0 && nextY > maxY) nextY = maxY;
                            if (autoScrollY < 0 && nextY < winTop) nextY = winTop;
                        }
                        scrollY = nextY;
                        clampScrollX();
                        clampScrollY();
                        editor.onTouch.updateHandlePosition(editor.onTouch.lastTouchX, editor.onTouch.lastTouchY);
                        editor.fileIO.checkAndLoadWindow();
                        editor.invalidate();
                        editor.caret.mainHandler.postDelayed(this, 16);
                    }
                }
            };

    public float autoScrollX = 0f, autoScrollY = 0f;

    public Scroll(SodiumEditor editor) {
        this.editor = editor;
        this.delayedWindowCheck = () -> editor.fileIO.checkAndLoadWindow();
        this.scroller = new OverScroller(editor.getContext());
        this.stretch = new Stretch(editor);
        this.edge = new Edge(editor);
        this.bar = new ScrollBar(editor, this);
        this.bounds = new ScrollBounds(editor, this);
        this.handler = new ScrollHandler(editor, this);
        
        // Initialize aliases
        this.scrollBarThumbRect = bar.thumbRect;
        this.scrollBarHideRunnable = bar.hideRunnable;
    }

    // ==============================
    // Bridge Methods (Delegated)
    // ==============================

    public boolean handleScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) { return handler.handleScroll(e1, e2, dX, dY); }
    public boolean handleFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return handler.handleFling(e1, e2, vX, vY); }
    
    public void drawStretch(android.graphics.Canvas c) { stretch.drawStretch(c); }
    public void drawEdge(android.graphics.Canvas c) { edge.draw(c); }
    
    public float getMaxScrollXForClamp() { return bounds.getMaxScrollXForClamp(); }
    public float getMaxScrollYForClamp() { return bounds.getMaxScrollYForClamp(); }
    public void clampScrollX() { bounds.clampScrollX(); }
    public void clampScrollY() { bounds.clampScrollY(); }
    public float getBottomBarrierPadding() { return bounds.getBottomBarrierPadding(); }
    public float getKeyboardBarrierPadding() { return bounds.getKeyboardBarrierPadding(); }

    public void drawScrollBar(android.graphics.Canvas c) { bar.draw(c); }
    public void showScrollBar() { bar.show(); }
    public void startScrollBarFadeOut() { bar.startFadeOut(); }
    public void cancelScrollBarFade() { bar.cancelFade(); }

    public void scrollTo(float x, float y) { scrollX = x; scrollY = y; clampScrollX(); clampScrollY(); }
    public void smoothScrollTo(float targetX, float targetY) {
        abortAnimation();
        float startX = scrollX;
        float startY = scrollY;
        float maxX = getMaxScrollXForClamp();
        float maxY = getMaxScrollYForClamp();
        float tx = Math.max(0, Math.min(targetX, maxX));
        float ty = Math.max(0, Math.min(targetY, maxY));
        
        if (Math.abs(tx - startX) < 1f && Math.abs(ty - startY) < 1f) {
            scrollX = tx; scrollY = ty;
            editor.invalidate(); return;
        }

        flingStopAnimator = ValueAnimator.ofFloat(0f, 1f);
        flingStopAnimator.setDuration(250);
        flingStopAnimator.setInterpolator(new DecelerateInterpolator());
        flingStopAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            scrollX = startX + (tx - startX) * t;
            scrollY = startY + (ty - startY) * t;
            editor.invalidate();
        });
        flingStopAnimator.start();
    }

    public void abortAnimation() { if (!scroller.isFinished()) scroller.abortAnimation(); scrollerIsScrolling = false; cancelFlingStopAnimation(); }
    public void cancelFlingStopAnimation() { if (flingStopAnimator != null) { flingStopAnimator.cancel(); flingStopAnimator = null; } }

    public int getFlingOverScrollX() { return (flingBounceEnabled) ? (flingBounceOverScrollPx >= 0 ? flingBounceOverScrollPx : Math.max(24, Math.round(editor.getWidth() * flingBounceOverScrollFactor))) : 0; }
    public int getFlingOverScrollY() { return (flingBounceEnabled) ? (flingBounceOverScrollPx >= 0 ? flingBounceOverScrollPx : Math.max(24, Math.round(editor.getHeight() * flingBounceOverScrollFactor))) : 0; }

    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            float oldX = scrollX, oldY = scrollY;
            scrollX = scroller.getCurrX(); scrollY = scroller.getCurrY();
            scrollerIsScrolling = true;
            float mX = getMaxScrollXForClamp(), mY = getMaxScrollYForClamp(), v = scroller.getCurrVelocity();
            if (edge.edgeEffectEnabled) {
                if (scrollY <= 0 && oldY > 0) edge.absorbTop(v); else if (scrollY >= mY && oldY < mY) edge.absorbBottom(v);
                if (scrollX <= 0 && oldX > 0) edge.absorbLeft(v); else if (scrollX >= mX && oldX < mX) edge.absorbRight(v);
            }
            if (stretch.stretchOverscrollEnabled) {
                if (scrollY <= 0 && oldY > 0) stretch.absorbStretchY(v, false); else if (scrollY >= mY && oldY < mY) stretch.absorbStretchY(v, true);
                if (scrollX <= 0 && oldX > 0) stretch.absorbStretchX(v, false); else if (scrollX >= mX && oldX < mX) stretch.absorbStretchX(v, true);
            }
            editor.removeCallbacks(delayedWindowCheck);
            editor.windowRender.maybeKickWindowLoad(editor.wordWrap.getGlobalLineForY(scrollY));
            editor.postDelayed(delayedWindowCheck, 40);
            showScrollBar(); editor.postInvalidateOnAnimation();
        } else if (scrollerIsScrolling) {
            scrollerIsScrolling = false; showScrollBar();
            if (stretch.stretchOverscrollEnabled) stretch.releaseStretch();
            edge.releaseAll();
            if (flingBounceEnabled) {
                int mX = Math.round(getMaxScrollXForClamp()), mY = Math.round(getMaxScrollYForClamp());
                if (scrollX < 0 || scrollX > mX || scrollY < 0 || scrollY > mY) {
                    if (scroller.springBack(Math.round(scrollX), Math.round(scrollY), 0, mX, 0, mY)) { scrollerIsScrolling = true; editor.postInvalidateOnAnimation(); return; }
                }
            }
            editor.fileIO.checkAndLoadWindow();
            if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixRebuildPending && !editor.wordWrap.wrapPrefixBuilding) {
                editor.wordWrap.wrapPrefixRebuildPending = false; editor.wordWrap.scheduleWrapPrefixRebuildUpToWindow();
            }
            if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
        }
    }

    public void scrollToLineFastForSelectAll(int l, int c) {
        if (editor.wordWrap.isWordWrapEnabled && (!editor.wordWrap.wrapMetricsReady || editor.wordWrap.wrapLinePrefix == null)) scrollY = Math.max(0f, (l - 5) * editor.textRender.lineHeight);
        else scrollY = Math.max(0f, (editor.wordWrap.getVisualIndexForLineAndChar(l, c) - 5) * editor.textRender.lineHeight);
        clampScrollY();
    }

    public void keepCursorVisibleHorizontally() {
        if (editor.zoom.isScaling || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress()) || editor.onTouch.multiTouchActive) return;
        float oldX = scrollX, oldY = scrollY;
        if (editor.codeFold.isCodeFoldingEnabled) editor.codeFold.rebuildFoldIntervalsIfNeeded();
        int vIdx = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine) : editor.wordWrap.getVisualIndexForLineAndChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
        if (vIdx < 0) return;
        float yT = vIdx * editor.textRender.lineHeight, yB = yT + editor.textRender.lineHeight;
        int vH = editor.getHeight() - editor.view.keyboardHeight; if (vH <= 0) vH = editor.getHeight();
        float bP = (editor.view.keyboardHeight > 0) ? getKeyboardBarrierPadding() : getBottomBarrierPadding();
        float evH = Math.max(0f, vH - bP), vT = scrollY, vB = scrollY + evH;
        float nY = scrollY;
        if (yB > vB) nY = yB - evH; else if (yT < vT) nY = yT;
        if (editor.view.keyboardHeight > 0) {
            float kbT = editor.getHeight() - editor.view.keyboardHeight, pAK = getKeyboardBarrierPadding(), cCVY = yB - scrollY;
            if (cCVY >= kbT - pAK) nY = yB - (editor.getHeight() - editor.view.keyboardHeight - pAK);
        }
        float nX = scrollX;
        if (!editor.wordWrap.isWordWrapEnabled) {
            String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
            float cX = editor.caret.getCaretXForLine(ln, editor.cursor.cursorLine, Math.min(editor.cursor.cursorChar, editor.view.getLogicalLineLength(editor.cursor.cursorLine, ln)));
            float vL = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
            float vR = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
            float sM = 50f, eSX = getEffectiveScrollX(), cVX = editor.layout.getTextStartX() + cX - eSX, minV = vL + sM, maxV = vR - sM;
            if (cVX < minV) eSX = editor.layout.getTextStartX() + cX - minV; else if (cVX > maxV) eSX = editor.layout.getTextStartX() + cX - maxV;
            float max = getMaxScrollXForClamp(); eSX = Math.max(editor.textRender.isRtl ? -max : 0f, Math.min(eSX, editor.textRender.isRtl ? 0f : max));
            nX = editor.textRender.isRtl ? -eSX : eSX;
        } else nX = 0f;
        if (Math.abs(nX - oldX) > 1f || Math.abs(nY - oldY) > 1f) smoothScrollTo(nX, nY); else editor.cursor.invalidateCursorArea();
    }

    public float getEffectiveScrollX() { return editor.textRender.isRtl ? -scrollX : scrollX; }
    public float viewToTextX(float vX) { return vX + getEffectiveScrollX() - editor.layout.getTextStartX(); }

    public void startFlingStopAnimation(float targetX, float targetY) {
        cancelFlingStopAnimation();
        float startX = scrollX, startY = scrollY;
        float dx = targetX - startX, dy = targetY - startY;
        if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) { scrollX = targetX; scrollY = targetY; clampScrollY(); clampScrollX(); return; }
        flingStopAnimator = ValueAnimator.ofFloat(0f, 1f);
        flingStopAnimator.setDuration(FLING_STOP_ANIM_DURATION_MS);
        flingStopAnimator.setInterpolator(new DecelerateInterpolator());
        flingStopAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            scrollX = startX + dx * t; scrollY = startY + dy * t;
            clampScrollY(); clampScrollX();
            editor.removeCallbacks(delayedWindowCheck);
            editor.windowRender.maybeKickWindowLoad(editor.wordWrap.getGlobalLineForY(scrollY));
            editor.postDelayed(delayedWindowCheck, 40);
            editor.postInvalidateOnAnimation();
        });
        flingStopAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { flingStopAnimator = null; }
            @Override public void onAnimationCancel(Animator a) { flingStopAnimator = null; }
        });
        flingStopAnimator.start();
    }

    // Setters
    public void setScrollMode(int m) { if (m == SCROLL_MODE_SINGLE_AXIS || m == SCROLL_MODE_GRID || m == SCROLL_MODE_FREE) this.scrollMode = m; }
    public void setScrollSensitivity(float s) { if (s > 0) this.scrollSensitivity = s; }
    public void setFlingSensitivity(float s) { if (s > 0) this.flingSensitivity = s; }
    public void setScrollBarEnabled(boolean e) { scrollBarEnabled = e; editor.invalidate(); }
    public void setScrollBarFadeEnabled(boolean e) { scrollBarFadeEnabled = e; cancelScrollBarFade(); scrollBarAlpha = e ? 0f : 1f; editor.invalidate(); }
    public void setScrollBarColor(int color) { scrollBarColor = color; editor.invalidate(); }
    public void setScrollBarWidthPx(float px) { if (px > 0) scrollBarWidthPx = px; editor.invalidate(); }
    public void setScrollBarMinThumbPx(float px) { if (px > 0) scrollBarMinThumbPx = px; editor.invalidate(); }
    public void setScrollBarFadeDelayMs(long ms) { scrollBarFadeDelayMs = Math.max(0, ms); }
    public void setScrollBarFadeDurationMs(long ms) { scrollBarFadeDurationMs = Math.max(0, ms); }
    public void setScrollBarHaloColor(int color) { scrollBarHaloColor = color; editor.invalidate(); }
    public void setScrollBarHaloSizePx(float px) { if (px >= 0) scrollBarHaloSizePx = px; editor.invalidate(); }
    public void setScrollBarCornerRadiusPx(float px) { if (px >= 0) scrollBarCornerRadiusPx = px; editor.invalidate(); }
    public void setScrollBarMarginPx(float px) { if (px >= 0) scrollBarMarginPx = px; editor.invalidate(); }
    public void setStretchOverscrollEnabled(boolean e) { stretch.setStretchOverscrollEnabled(e); }
    public void setStretchOverscrollStrength(float s) { stretch.setStretchOverscrollStrength(s); }
    public void setFlingBounceEnabled(boolean e) { this.flingBounceEnabled = e; }
    public void setFlingBounceDistancePx(int px) { this.flingBounceOverScrollPx = Math.max(0, px); }
    public void setFlingBounceDistanceFactor(float f) { if (f > 0) this.flingBounceOverScrollFactor = f; }
}
