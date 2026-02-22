package com.yn.sodiumeditor;

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
  public float scrollY = 0f;
  public float scrollX = 0f;
  public float maxLineWidthForScroll = 0f;
  public float maxTextStartXForScroll = 0f;
  public float maxScrollXForScroll = 0f;
  public float dragMaxScrollX = -1f;
  public final OverScroller scroller;
  public boolean scrollerIsScrolling = false;
  public float autoScrollX = 0f;
  public float autoScrollY = 0f;
  public int scrollMode = SCROLL_MODE_FREE;
  public float scrollSensitivity = 1f;
  public float flingSensitivity = 1f;
  public boolean flingBounceEnabled = false;
  public int flingBounceOverScrollPx = -1;
  public float flingBounceOverScrollFactor = 0.2f;
  public boolean stretchOverscrollEnabled = false;
  public float stretchOverscrollStrength = 1f;
  public float stretchX = 0f;
  public float stretchY = 0f;
  public int stretchDirX = 0;
  public int stretchDirY = 0;
  @Nullable ValueAnimator stretchReleaseAnimator;
  public int scrollLockAxis = 0;

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
  @Nullable ValueAnimator scrollBarFadeAnimator;
  public final Runnable scrollBarHideRunnable;
  public final Paint scrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint scrollBarHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF scrollBarThumbRect = new RectF();
  public boolean draggingScrollBar = false;
  public float scrollBarDragOffset = 0f;

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
          releaseStretch();
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
        if (view.wordWrapManager.isWordWrapEnabled
            && view.wordWrapManager.wrapPrefixRebuildPending
            && !view.wordWrapManager.wrapPrefixBuilding) {
          view.wordWrapManager.wrapPrefixRebuildPending = false;
          view.wordWrapManager.scheduleWrapPrefixRebuildUpToWindow(view);
        }
        if (view.selectionManager.hasSelection()) view.popupMenuManager.showPopupAtSelection();
      }
    }
  }

  public boolean onFling(float velocityX, float velocityY) {
    if (view.zoomManager.isScaling() || view.zoomManager.isScaleInProgress()) return true;
    if (view.zoomManager.isJustFinishedScale()) return true;
    if (view.wordWrapManager.isWordWrapEnabled && view.wordWrapManager.wrapPrefixBuilding) {
      view.wordWrapManager.cancelWrapPrefixRebuildForInteraction();
    }
    if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return false;

    int startX = Math.round(scrollX);
    int startY = Math.round(scrollY);
    int minX = 0;
    int maxX =
        view.wordWrapManager.isWordWrapEnabled
            ? 0
            : Math.max(
                0,
                Math.round(getMaxLineWidthInWindowInternal() - (view.getWidth() - view.getTextStartX())));
    int minY = 0;

    float maxScrollYFloat;
    float effectiveHeight =
        (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();

    int lineCount =
        view.wordWrapManager.isWordWrapEnabled
            ? view.wordWrapManager.getTotalVisualLineCount(view)
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
    if (view.wordWrapManager.isWordWrapEnabled) {
      vx = 0f;
    }
    if (view.isRtl && !view.wordWrapManager.isWordWrapEnabled) {
      vx = -vx;
    }
    int overX = 0;
    int overY = 0;
    if (flingBounceEnabled) {
      if (!view.wordWrapManager.isWordWrapEnabled) overX = Math.max(overX, getFlingOverScrollX());
      overY = Math.max(overY, getFlingOverScrollY());
    }
    scroller.fling(
        startX, startY, (int) -vx, (int) -vy, minX, maxX, minY, maxY, overX, overY);
    view.postInvalidateOnAnimation();
    return true;
  }

  public boolean onScroll(MotionEvent e2, float distanceX, float distanceY) {
    if (e2.getPointerCount() > 1) return true;
    if (view.zoomManager.isScaling() || view.zoomManager.isScaleInProgress()) return true;
    if (view.zoomManager.isJustFinishedScale()) return true;
    if (view.wordWrapManager.isWordWrapEnabled && view.wordWrapManager.wrapPrefixBuilding) {
      view.wordWrapManager.cancelWrapPrefixRebuildForInteraction();
    }
    if (view.autoSuggestionManager.isSuggestionAcceptedThisTouch()) return false;

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
    if (view.wordWrapManager.isWordWrapEnabled) {
      dx = 0f;
    }
    if (view.isRtl && !view.wordWrapManager.isWordWrapEnabled) {
      dx = -dx;
    }
    float maxX = 0f;
    if (!view.wordWrapManager.isWordWrapEnabled) {
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
      if (!view.wordWrapManager.isWordWrapEnabled) {
        if (nextX < 0f && dx < 0f) {
          pullStretchX(dx, false);
          nextX = 0f;
        } else if (nextX > maxX && dx > 0f) {
          pullStretchX(dx, true);
          nextX = maxX;
        }
      }
      if (nextY < 0f && dy < 0f) {
        pullStretchY(dy, false);
        nextY = 0f;
      } else if (nextY > maxY && dy > 0f) {
        pullStretchY(dy, true);
        nextY = maxY;
      }
    } else {
      if (!view.wordWrapManager.isWordWrapEnabled) {
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

    if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
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

  public float getMaxScrollYForClamp() {
    if (view.wordWrapManager.isWordWrapEnabled
        && !view.wordWrapManager.wrapMetricsReady
        && (view.zoomManager.isScaling() || view.zoomManager.isJustFinishedScale())) {
      return scrollY;
    }

    float effectiveHeight = (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
    int lineCount =
        view.wordWrapManager.isWordWrapEnabled
            ? view.wordWrapManager.getTotalVisualLineCount(view)
            : (view.foldManager.isCodeFoldingEnabled
                ? view.getVisibleLineCount()
                : Math.max(1, view.getLinesCount()));
    if (view.wordWrapManager.isWordWrapEnabled && (view.selectionManager.isSelectAllActive() || view.selectionManager.isEntireFileSelected())) {
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
    if (!view.wordWrapManager.isWordWrapEnabled && view.isWindowLoading && scrollY < view.windowStartLine * view.lineHeight) {
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

  public float getMaxScrollXForClamp() {
    if (view.wordWrapManager.isWordWrapEnabled) return 0f;
    float rawMaxWidth = getMaxLineWidthInWindowInternal();
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

  public void clampScrollX() {
    if (view.wordWrapManager.isWordWrapEnabled) {
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

  public void drawScrollBar(Canvas canvas) {
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

  public void showScrollBar() {
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

  public void startScrollBarFadeOut() {
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

  public void keepCursorVisibleHorizontally() {
    if (view.zoomManager.isScaleInProgress()
        || view.zoomManager.isScaling()
        || view.zoomManager.isMultiTouchActive()) {
      return;
    }
    int cursorVisualIndex = view.getVisualIndexForLineAndChar(view.cursorManager.getLine(), view.cursorManager.getChar());
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
    clampScrollY();

    if (!view.wordWrapManager.isWordWrapEnabled) {
      String line = view.getLineTextForRender(view.cursorManager.getLine());
      int safeChar =
          Math.min(view.cursorManager.getChar(), view.getLogicalLineLength(view.cursorManager.getLine(), line));
      float cursorX = view.getCaretXForLine(line, view.cursorManager.getLine(), safeChar);

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
    if (view.wordWrapManager.isWordWrapEnabled
        && (!view.wordWrapManager.wrapMetricsReady || view.wordWrapManager.wrapLinePrefix == null)) {
      scrollY = Math.max(0f, (line - 5) * view.lineHeight);
    } else {
      int targetVisual = view.getVisualIndexForLineAndChar(line, ch);
      scrollY = Math.max(0f, (targetVisual - 5) * view.lineHeight);
    }
    clampScrollY();
  }

  public float getDrawLineTop(int globalLine) {
    int drawIndex = globalLine;
    if (view.foldManager.isCodeFoldingEnabled) {
      drawIndex = view.getVisibleIndexForGlobalLine(globalLine);
    }
    return (drawIndex - view.drawBaseLine) * view.lineHeight;
  }

  public float getDrawLineBottom(int globalLine) {
    return getDrawLineTop(globalLine) + view.lineHeight;
  }

  public float getHitTestBaseY() {
    int baseLine = (int) (scrollY / view.lineHeight);
    if (baseLine < 0) baseLine = 0;
    return baseLine * view.lineHeight;
  }

  public void ensureLineInWindow(int globalLine, boolean blockingIfAbsent) {
    view.autoSuggestionManager.clearActiveSuggestion();
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
      cancelStretchRelease();
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

  private float getMaxLineWidthInWindowInternal() {
    return Math.max(view.currentMaxWindowLineWidth, view.globalMaxLineWidth);
  }

  private float getKeyboardBarrierPaddingInternal() {
    return Math.min(SodiumEditorView.BOTTOM_SCROLL_OFFSET, view.keyboardHeight * 0.4f);
  }

  private float getBottomBarrierPaddingInternal() {
    return SodiumEditorView.BOTTOM_SCROLL_OFFSET;
  }

  void cancelStretchRelease() {
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
      stretchReleaseAnimator = null;
    }
  }

  void releaseStretch() {
    if (!stretchOverscrollEnabled) return;
    if (stretchX == 0f && stretchY == 0f) return;
    cancelStretchRelease();
    final float startX = stretchX;
    final float startY = stretchY;
    stretchReleaseAnimator = ValueAnimator.ofFloat(0f, 1f);
    stretchReleaseAnimator.setDuration(220);
    stretchReleaseAnimator.setInterpolator(new DecelerateInterpolator());
    stretchReleaseAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          float inv = 1f - t;
          stretchX = startX * inv;
          stretchY = startY * inv;
          if (stretchX == 0f) stretchDirX = 0;
          if (stretchY == 0f) stretchDirY = 0;
          view.invalidate();
        });
    stretchReleaseAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            stretchReleaseAnimator = null;
            stretchX = 0f;
            stretchY = 0f;
            stretchDirX = 0;
            stretchDirY = 0;
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            stretchReleaseAnimator = null;
          }
        });
    stretchReleaseAnimator.start();
  }

  void pullStretchX(float deltaPx, boolean toRight) {
    if (!stretchOverscrollEnabled || view.wordWrapManager.isWordWrapEnabled) return;
    if (view.getWidth() <= 0) return;
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) view.getWidth();
    float gain = norm * 0.6f * stretchOverscrollStrength;
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(1f, stretchX + gain);
  }

  void pullStretchY(float deltaPx, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    if (view.getHeight() <= 0) return;
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) view.getHeight();
    float gain = norm * 0.6f * stretchOverscrollStrength;
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(1f, stretchY + gain);
  }

  void absorbStretchX(float velocityPxPerSec, boolean toRight) {
    if (!stretchOverscrollEnabled || view.wordWrapManager.isWordWrapEnabled) return;
    cancelStretchRelease();
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(1f, stretchX + v * 0.8f * stretchOverscrollStrength);
  }

  void absorbStretchY(float velocityPxPerSec, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    cancelStretchRelease();
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(1f, stretchY + v * 0.8f * stretchOverscrollStrength);
  }

  public void abortScroller() {
    if (!scroller.isFinished()) {
      scroller.computeScrollOffset();
      scrollX = scroller.getCurrX();
      scrollY = scroller.getCurrY();
      scroller.abortAnimation();
    }
  }
}
