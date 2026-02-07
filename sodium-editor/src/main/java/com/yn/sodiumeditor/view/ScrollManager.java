package com.yn.sodiumeditor.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;
import androidx.annotation.Nullable;

public final class ScrollManager {
  public static final int SCROLL_MODE_SINGLE_AXIS = 0;
  public static final int SCROLL_MODE_GRID = 1;
  public static final int SCROLL_MODE_FREE = 2;
  private final SodiumEditorView view;
  float scrollY = 0f;
  float scrollX = 0f;
  float maxLineWidthForScroll = 0f;
  float maxTextStartXForScroll = 0f;
  float maxScrollXForScroll = 0f;
  float dragMaxScrollX = -1f;
  final OverScroller scroller;
  boolean scrollerIsScrolling = false;
  float autoScrollX = 0f;
  float autoScrollY = 0f;
  int scrollMode = SCROLL_MODE_FREE;
  float scrollSensitivity = 1f;
  float flingSensitivity = 1f;
  boolean flingBounceEnabled = false;
  int flingBounceOverScrollPx = -1;
  float flingBounceOverScrollFactor = 0.2f;
  boolean stretchOverscrollEnabled = false;
  float stretchOverscrollStrength = 1f;
  float stretchX = 0f;
  float stretchY = 0f;
  int stretchDirX = 0;
  int stretchDirY = 0;
  @Nullable ValueAnimator stretchReleaseAnimator;
  int scrollLockAxis = 0;

  boolean scrollBarEnabled = true;
  int scrollBarColor = 0x80FFFFFF;
  float scrollBarWidthPx = 6f;
  float scrollBarMinThumbPx = 24f;
  float scrollBarCornerRadiusPx = 6f;
  float scrollBarMarginPx = 2f;
  boolean scrollBarFadeEnabled = true;
  long scrollBarFadeDelayMs = 1000;
  long scrollBarFadeDurationMs = 200;
  float scrollBarAlpha = 0f;
  int scrollBarHaloColor = 0x40FFFFFF;
  float scrollBarHaloSizePx = 8f;
  @Nullable ValueAnimator scrollBarFadeAnimator;
  final Runnable scrollBarHideRunnable;
  final Paint scrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint scrollBarHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final RectF scrollBarThumbRect = new RectF();
  boolean draggingScrollBar = false;
  float scrollBarDragOffset = 0f;

  ScrollManager(SodiumEditorView view) {
    this.view = view;
    this.scroller = new OverScroller(view.getContext());
    this.scrollBarHideRunnable = this::startScrollBarFadeOut;
  }

  void startFlingStopAnimation(float targetX, float targetY) {
    cancelFlingStopAnimation();
    float startX = scrollX;
    float startY = scrollY;
    float dx = targetX - startX;
    float dy = targetY - startY;
    if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
      scrollX = targetX;
      scrollY = targetY;
      clampScrollY();
      clampScrollX();
      return;
    }
    view.flingStopAnimator = ValueAnimator.ofFloat(0f, 1f);
    view.flingStopAnimator.setDuration(SodiumEditorView.FLING_STOP_ANIM_DURATION_MS);
    view.flingStopAnimator.setInterpolator(new DecelerateInterpolator());
    view.flingStopAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          scrollX = startX + dx * t;
          scrollY = startY + dy * t;
          clampScrollY();
          clampScrollX();
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

  void cancelFlingStopAnimation() {
    if (view.flingStopAnimator != null) {
      view.flingStopAnimator.cancel();
      view.flingStopAnimator = null;
    }
  }

  void computeScroll() {
    if (scroller.computeScrollOffset()) {
      scrollerIsScrolling = true;
      float rawY = scroller.getCurrY();
      float rawX = scroller.getCurrX();
      scrollY = rawY;
      scrollX = rawX;
      getMaxScrollXForClamp();
      getMaxScrollYForClamp();
      clampScrollY();
      clampScrollX();
      view.removeCallbacks(view.delayedWindowCheck);
      view.maybeKickWindowLoad(view.getGlobalLineForY(scrollY));
      view.postDelayed(view.delayedWindowCheck, 40);
      showScrollBar();
      view.postInvalidateOnAnimation();
    } else {
      if (scrollerIsScrolling) {
        scrollerIsScrolling = false;
        showScrollBar();
        if (stretchOverscrollEnabled) {
          view.releaseStretch();
        }
        if (flingBounceEnabled) {
          int maxX = Math.round(getMaxScrollXForClamp());
          int maxY = Math.round(getMaxScrollYForClamp());
          if (scrollX < 0 || scrollX > maxX || scrollY < 0 || scrollY > maxY) {
            if (scroller.springBack(
                Math.round(scrollX), Math.round(scrollY), 0, maxX, 0, maxY)) {
              scrollerIsScrolling = true;
              view.postInvalidateOnAnimation();
              return;
            }
          }
        }
        view.checkAndLoadWindow();
        if (view.isWordWrapEnabled
            && view.isWrapPrefixRebuildPendingForScroll()
            && !view.isWrapPrefixBuildingForScroll()) {
          view.clearWrapPrefixRebuildPendingForScroll();
          view.scheduleWrapPrefixRebuildUpToWindow();
        }
        if (view.hasSelectionValue()) view.showPopupAtSelection();
      }
    }
  }

  boolean onFling(float velocityX, float velocityY) {
    if (view.getZoomManager().isScaling() || view.getZoomManager().isScaleInProgress()) return true;
    if (view.getZoomManager().isJustFinishedScale()) return true;
    if (view.isWordWrapEnabled && view.isWrapPrefixBuildingForScroll()) {
      view.cancelWrapPrefixRebuildForInteraction();
    }
    if (view.suggestionAcceptedThisTouch) return false;

    int startX = Math.round(scrollX);
    int startY = Math.round(scrollY);
    int minX = 0;
    int maxX =
        view.isWordWrapEnabled
            ? 0
            : Math.max(
                0,
                Math.round(view.getMaxLineWidthInWindow() - (view.getWidth() - view.getTextStartX())));
    int minY = 0;

    float maxScrollYFloat;
    float effectiveHeight =
        (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();

    int lineCount =
        view.isWordWrapEnabled
            ? view.getTotalVisualLineCount()
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
    float vx = velocityX * flingSensitivity;
    float vy = velocityY * flingSensitivity;
    if (scrollMode == SCROLL_MODE_SINGLE_AXIS) {
      int axis = scrollLockAxis;
      if (axis == 0) axis = (Math.abs(vx) >= Math.abs(vy)) ? 1 : 2;
      if (axis == 1) vy = 0f;
      else vx = 0f;
    } else if (scrollMode == SCROLL_MODE_GRID) {
      if (Math.abs(vx) >= Math.abs(vy)) vy = 0f;
      else vx = 0f;
    }
    if (view.isWordWrapEnabled) {
      vx = 0f;
    }
    if (view.isRtl && !view.isWordWrapEnabled) {
      vx = -vx;
    }
    int overX = 0;
    int overY = 0;
    if (flingBounceEnabled) {
      if (!view.isWordWrapEnabled) overX = Math.max(overX, getFlingOverScrollX());
      overY = Math.max(overY, getFlingOverScrollY());
    }
    scroller.fling(
        startX, startY, (int) -vx, (int) -vy, minX, maxX, minY, maxY, overX, overY);
    view.postInvalidateOnAnimation();
    return true;
  }

  boolean onScroll(MotionEvent e2, float distanceX, float distanceY) {
    if (e2.getPointerCount() > 1) return true;
    if (view.getZoomManager().isScaling() || view.getZoomManager().isScaleInProgress()) return true;
    if (view.getZoomManager().isJustFinishedScale()) return true;
    if (view.isWordWrapEnabled && view.isWrapPrefixBuildingForScroll()) {
      view.cancelWrapPrefixRebuildForInteraction();
    }
    if (view.suggestionAcceptedThisTouch) return false;

    view.movedSinceDown = true;
    float dx = distanceX * scrollSensitivity;
    float dy = distanceY * scrollSensitivity;
    if (scrollMode == SCROLL_MODE_SINGLE_AXIS) {
      if (scrollLockAxis == 0) {
        scrollLockAxis = (Math.abs(dx) >= Math.abs(dy)) ? 1 : 2;
      }
      if (scrollLockAxis == 1) dy = 0f;
      else dx = 0f;
    } else if (scrollMode == SCROLL_MODE_GRID) {
      if (Math.abs(dx) >= Math.abs(dy)) dy = 0f;
      else dx = 0f;
    }
    if (view.isWordWrapEnabled) {
      dx = 0f;
    }
    if (view.isRtl && !view.isWordWrapEnabled) {
      dx = -dx;
    }
    float maxX = 0f;
    if (!view.isWordWrapEnabled) {
      if (dragMaxScrollX < 0f) {
        dragMaxScrollX = getMaxScrollXForClamp();
      } else {
        float freshMax = getMaxScrollXForClamp();
        if (freshMax > dragMaxScrollX) dragMaxScrollX = freshMax;
      }
      maxX = dragMaxScrollX;
    }
    float maxY = getMaxScrollYForClamp();

    float nextX = scrollX + dx;
    float nextY = scrollY + dy;
    if (stretchOverscrollEnabled) {
      if (!view.isWordWrapEnabled) {
        if (nextX < 0f && dx < 0f) {
          view.pullStretchX(dx, false);
          nextX = 0f;
        } else if (nextX > maxX && dx > 0f) {
          view.pullStretchX(dx, true);
          nextX = maxX;
        }
      }
      if (nextY < 0f && dy < 0f) {
        view.pullStretchY(dy, false);
        nextY = 0f;
      } else if (nextY > maxY && dy > 0f) {
        view.pullStretchY(dy, true);
        nextY = maxY;
      }
    } else {
      if (!view.isWordWrapEnabled) {
        if ((scrollX <= 0f && dx < 0f) || (scrollX >= maxX && dx > 0f)) {
          dx = 0f;
          nextX = scrollX;
        }
      }
    }

    scrollY = nextY;
    scrollX = nextX;
    clampScrollY();
    clampScrollX();
    showScrollBar();

    view.removeCallbacks(view.delayedWindowCheck);
    if (Math.abs(distanceY) > view.lineHeight * 6f) {
      view.checkAndLoadWindow();
    } else {
      view.postDelayed(view.delayedWindowCheck, 60);
    }

    if (view.isPopupVisibleForScroll()) view.hidePopup();
    view.resetCursorBlink();
    view.invalidate();
    return true;
  }

  int getFlingOverScrollX() {
    if (!flingBounceEnabled) return 0;
    if (flingBounceOverScrollPx >= 0) return flingBounceOverScrollPx;
    return Math.max(24, Math.round(view.getWidth() * flingBounceOverScrollFactor));
  }

  int getFlingOverScrollY() {
    if (!flingBounceEnabled) return 0;
    if (flingBounceOverScrollPx >= 0) return flingBounceOverScrollPx;
    return Math.max(24, Math.round(view.getHeight() * flingBounceOverScrollFactor));
  }

  float getMaxScrollYForClamp() {
    if (view.isWordWrapEnabled
        && !view.isWrapMetricsReadyForScroll()
        && (view.getZoomManager().isScaling() || view.getZoomManager().isJustFinishedScale())) {
      return scrollY;
    }

    float effectiveHeight = (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
    int lineCount =
        view.isWordWrapEnabled
            ? view.getTotalVisualLineCount()
            : (view.foldManager.isCodeFoldingEnabled
                ? view.getVisibleLineCount()
                : Math.max(1, view.getLinesCount()));
    if (view.isWordWrapEnabled && (view.isSelectAllActiveValue() || view.isEntireFileSelectedValue())) {
      lineCount = Math.max(lineCount, view.getSelectionEndLineValue() + 1);
    }
    if (view.isEof) {
      float paddingToUse =
          (view.keyboardHeight > 0) ? view.getKeyboardBarrierPadding() : view.getBottomBarrierPadding();
      return Math.max(0f, lineCount * view.lineHeight - (effectiveHeight - paddingToUse));
    }
    float virtualExtraSpace = Math.max(view.prefetchLines * view.lineHeight, 2000f);
    return Math.max(0f, lineCount * view.lineHeight + virtualExtraSpace - effectiveHeight);
  }

  void clampScrollY() {
    if (!view.isWordWrapEnabled && view.isWindowLoading && scrollY < view.windowStartLine * view.lineHeight) {
      boolean allowAboveWindow = scrollerIsScrolling || view.flingStopAnimator != null;
      if (!allowAboveWindow) {
        scrollY = view.windowStartLine * view.lineHeight;
        if (!scroller.isFinished()) scroller.abortAnimation();
      }
    }

    float maxScroll = getMaxScrollYForClamp();
    boolean allowFlingOverscroll = flingBounceEnabled && scrollerIsScrolling;
    if (allowFlingOverscroll) {
      int over = getFlingOverScrollY();
      if (scrollY < -over) scrollY = -over;
      if (scrollY > maxScroll + over) scrollY = maxScroll + over;
      return;
    }

    if (scrollY < 0) scrollY = 0;
    if (scrollY > maxScroll) {
      scrollY = maxScroll;
      if (view.isEof && !scroller.isFinished()) scroller.abortAnimation();
    }
  }

  float getMaxScrollXForClamp() {
    if (view.isWordWrapEnabled) return 0f;
    float rawMaxWidth = view.getMaxLineWidthInWindow();
    if (rawMaxWidth > maxLineWidthForScroll) {
      maxLineWidthForScroll = rawMaxWidth;
    }
    float textStartX = view.getTextStartX();
    if (textStartX > maxTextStartXForScroll) {
      maxTextStartXForScroll = textStartX;
    }
    float effectiveTextStartX = Math.max(textStartX, maxTextStartXForScroll);
    float candidateMax = Math.max(0f, maxLineWidthForScroll - (view.getWidth() - effectiveTextStartX));
    if (candidateMax > maxScrollXForScroll) {
      maxScrollXForScroll = candidateMax;
    }
    return maxScrollXForScroll;
  }

  void clampScrollX() {
    if (view.isWordWrapEnabled) {
      scrollX = 0f;
      return;
    }
    float max = (view.pointerDown && dragMaxScrollX >= 0f) ? dragMaxScrollX : getMaxScrollXForClamp();
    boolean allowFlingOverscroll = flingBounceEnabled && scrollerIsScrolling;
    if (allowFlingOverscroll) {
      int over = getFlingOverScrollX();
      if (scrollX < -over) scrollX = -over;
      if (scrollX > max + over) scrollX = max + over;
      return;
    }
    if (scrollX < 0) scrollX = 0;
    if (scrollX > max) scrollX = max;
  }

  void drawScrollBar(Canvas canvas) {
    if (!scrollBarEnabled) return;
    if (scrollBarFadeEnabled && scrollBarAlpha <= 0f) return;
    int w = view.getWidth();
    int h = view.getHeight();
    if (w <= 0 || h <= 0) return;
    float maxScroll = getMaxScrollYForClamp();
    if (maxScroll <= 0f) return;

    float trackHeight = h;
    float contentHeight = maxScroll + h;
    float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
    if (thumbHeight < scrollBarMinThumbPx) thumbHeight = scrollBarMinThumbPx;
    if (thumbHeight > trackHeight) thumbHeight = trackHeight;
    float thumbRange = Math.max(1f, trackHeight - thumbHeight);
    float thumbTop = (scrollY / maxScroll) * thumbRange;

    float right = w - scrollBarMarginPx;
    float left = right - scrollBarWidthPx;
    scrollBarThumbRect.set(left, thumbTop, right, thumbTop + thumbHeight);
    int baseColor = scrollBarColor;
    int alpha = (int) (Math.min(1f, scrollBarAlpha) * 255);
    int color = (baseColor & 0x00FFFFFF) | (alpha << 24);
    scrollBarPaint.setColor(color);
    if (draggingScrollBar) {
      int haloAlpha = (int) (alpha * 0.6f);
      int haloColor = (scrollBarHaloColor & 0x00FFFFFF) | (haloAlpha << 24);
      scrollBarHaloPaint.setColor(haloColor);
      float inset = Math.max(0f, scrollBarHaloSizePx);
      RectF halo = new RectF(
          scrollBarThumbRect.left - inset,
          scrollBarThumbRect.top - inset,
          scrollBarThumbRect.right + inset,
          scrollBarThumbRect.bottom + inset);
      float haloRadius = scrollBarCornerRadiusPx + inset;
      canvas.drawRoundRect(halo, haloRadius, haloRadius, scrollBarHaloPaint);
    }
    canvas.drawRoundRect(
        scrollBarThumbRect,
        scrollBarCornerRadiusPx,
        scrollBarCornerRadiusPx,
        scrollBarPaint);
  }

  void showScrollBar() {
    if (!scrollBarEnabled) return;
    if (!scrollBarFadeEnabled) {
      scrollBarAlpha = 1f;
      return;
    }
    cancelScrollBarFade();
    scrollBarAlpha = 1f;
    view.invalidate();
    view.mainHandler.removeCallbacks(scrollBarHideRunnable);
    view.mainHandler.postDelayed(scrollBarHideRunnable, scrollBarFadeDelayMs);
  }

  void startScrollBarFadeOut() {
    if (!scrollBarFadeEnabled || draggingScrollBar) return;
    cancelScrollBarFade();
    final float start = scrollBarAlpha;
    if (start <= 0f) return;
    scrollBarFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
    scrollBarFadeAnimator.setDuration(scrollBarFadeDurationMs);
    scrollBarFadeAnimator.setInterpolator(new DecelerateInterpolator());
    scrollBarFadeAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          scrollBarAlpha = Math.max(0f, start * (1f - t));
          view.invalidate();
        });
    scrollBarFadeAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            scrollBarFadeAnimator = null;
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            scrollBarFadeAnimator = null;
          }
        });
    scrollBarFadeAnimator.start();
  }

  void cancelScrollBarFade() {
    if (scrollBarFadeAnimator != null) {
      scrollBarFadeAnimator.cancel();
      scrollBarFadeAnimator = null;
    }
  }

  void keepCursorVisibleHorizontally() {
    if (view.getZoomManager().isScaleInProgress()
        || view.getZoomManager().isScaling()
        || view.getZoomManager().isMultiTouchActive()) {
      return;
    }
    int cursorVisualIndex = view.getVisualIndexForLineAndChar(view.getCursorLine(), view.getCursorChar());
    float cursorYTop = cursorVisualIndex * view.lineHeight;
    float cursorYBottom = cursorYTop + view.lineHeight;
    int viewHeight = view.getHeight() - view.keyboardHeight;
    if (viewHeight <= 0) viewHeight = view.getHeight();

    float bottomPadding =
        (view.keyboardHeight > 0) ? view.getKeyboardBarrierPadding() : view.getBottomBarrierPadding();
    float effectiveVisibleHeight = Math.max(0f, viewHeight - bottomPadding);
    float visibleTop = scrollY;
    float visibleBottom = scrollY + effectiveVisibleHeight;

    if (cursorYBottom > visibleBottom) scrollY = cursorYBottom - (viewHeight - bottomPadding);
    else if (cursorYTop < visibleTop) scrollY = cursorYTop;

    if (view.keyboardHeight > 0) {
      float keyboardTop = view.getHeight() - view.keyboardHeight;
      float paddingAboveKeyboard = view.getKeyboardBarrierPadding();
      float currentCursorViewY = cursorYBottom - scrollY;
      if (currentCursorViewY >= keyboardTop - paddingAboveKeyboard) {
        scrollY =
            cursorYBottom - (view.getHeight() - view.keyboardHeight - paddingAboveKeyboard);
      }
    }
    clampScrollY();

    if (!view.isWordWrapEnabled) {
      String line = view.getLineTextForRender(view.getCursorLine());
      int safeChar =
          Math.min(view.getCursorChar(), view.getLogicalLineLength(view.getCursorLine(), line));
      float cursorX = view.getCaretXForLine(line, view.getCursorLine(), safeChar);

      float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
      float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
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
    } else {
      scrollX = 0f;
    }

    clampScrollX();
    view.invalidate();
  }

  void scrollToLineFastForSelectAll(int line, int ch) {
    if (view.isWordWrapEnabled
        && (!view.isWrapMetricsReadyForScroll() || view.getWrapLinePrefixForScroll() == null)) {
      scrollY = Math.max(0f, (line - 5) * view.lineHeight);
    } else {
      int targetVisual = view.getVisualIndexForLineAndChar(line, ch);
      scrollY = Math.max(0f, (targetVisual - 5) * view.lineHeight);
    }
    clampScrollY();
  }

  float getDrawLineTop(int globalLine) {
    int drawIndex = globalLine;
    if (view.foldManager.isCodeFoldingEnabled) {
      drawIndex = view.getVisibleIndexForGlobalLine(globalLine);
    }
    return (drawIndex - view.drawBaseLine) * view.lineHeight;
  }

  float getDrawLineBottom(int globalLine) {
    return getDrawLineTop(globalLine) + view.lineHeight;
  }

  float getHitTestBaseY() {
    int baseLine = (int) (scrollY / view.lineHeight);
    if (baseLine < 0) baseLine = 0;
    return baseLine * view.lineHeight;
  }

  void ensureLineInWindow(int globalLine, boolean blockingIfAbsent) {
    view.clearActiveSuggestion();
    if (globalLine >= view.windowStartLine
        && globalLine < view.windowStartLine + view.linesWindow.size()) return;
    if (view.sourceFile != null) {
      int targetStart = Math.max(0, globalLine - view.prefetchLines);
      view.loadWindowAround(targetStart, null);
    }
  }

  public void setScrollMode(int mode) {
    if (mode != SCROLL_MODE_SINGLE_AXIS && mode != SCROLL_MODE_GRID && mode != SCROLL_MODE_FREE) {
      return;
    }
    scrollMode = mode;
  }

  public float getScrollXValue() {
    return scrollX;
  }

  public float getScrollYValue() {
    return scrollY;
  }

  public void setScrollPosition(float x, float y) {
    scrollX = x;
    scrollY = y;
    clampScrollX();
    clampScrollY();
    view.invalidate();
  }

  public void setScrollSensitivity(float sensitivity) {
    if (sensitivity <= 0f) return;
    scrollSensitivity = sensitivity;
  }

  public void setFlingSensitivity(float sensitivity) {
    if (sensitivity <= 0f) return;
    flingSensitivity = sensitivity;
  }

  public void setScrollBarEnabled(boolean enabled) {
    if (scrollBarEnabled == enabled) return;
    scrollBarEnabled = enabled;
    view.invalidate();
  }

  public void setScrollBarColor(int color) {
    scrollBarColor = color;
    view.invalidate();
  }

  public void setScrollBarWidthPx(float px) {
    if (px <= 0f) return;
    scrollBarWidthPx = px;
    view.invalidate();
  }

  public void setScrollBarMinThumbPx(float px) {
    if (px <= 0f) return;
    scrollBarMinThumbPx = px;
    view.invalidate();
  }

  public void setScrollBarFadeEnabled(boolean enabled) {
    scrollBarFadeEnabled = enabled;
    if (!enabled) {
      cancelScrollBarFade();
      scrollBarAlpha = 1f;
      view.invalidate();
    } else {
      cancelScrollBarFade();
      scrollBarAlpha = 0f;
      view.invalidate();
    }
  }

  public void setScrollBarFadeDelayMs(long ms) {
    scrollBarFadeDelayMs = Math.max(0, ms);
  }

  public void setScrollBarFadeDurationMs(long ms) {
    scrollBarFadeDurationMs = Math.max(0, ms);
  }

  public void setScrollBarHaloColor(int color) {
    scrollBarHaloColor = color;
    view.invalidate();
  }

  public void setScrollBarHaloSizePx(float px) {
    if (px < 0f) return;
    scrollBarHaloSizePx = px;
    view.invalidate();
  }

  public void setScrollBarCornerRadiusPx(float px) {
    if (px < 0f) return;
    scrollBarCornerRadiusPx = px;
    view.invalidate();
  }

  public void setScrollBarMarginPx(float px) {
    if (px < 0f) return;
    scrollBarMarginPx = px;
    view.invalidate();
  }

  public void setStretchOverscrollEnabled(boolean enabled) {
    if (stretchOverscrollEnabled == enabled) return;
    stretchOverscrollEnabled = enabled;
    if (!enabled) {
      stretchX = 0f;
      stretchY = 0f;
      stretchDirX = 0;
      stretchDirY = 0;
      view.cancelStretchRelease();
      view.invalidate();
    }
  }

  public void setStretchOverscrollStrength(float strength) {
    if (strength <= 0f) return;
    stretchOverscrollStrength = strength;
  }

  public void setFlingBounceEnabled(boolean enabled) {
    flingBounceEnabled = enabled;
  }

  public void setFlingBounceDistancePx(int px) {
    flingBounceOverScrollPx = Math.max(0, px);
  }

  public void setFlingBounceDistanceFactor(float factor) {
    if (factor <= 0f) return;
    flingBounceOverScrollFactor = factor;
  }
}
