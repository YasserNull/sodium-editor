package com.yn.sodiumeditor;

import android.animation.ValueAnimator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;
import androidx.annotation.Nullable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.view.animation.DecelerateInterpolator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;

/**
 * Scroll handles all scrolling logic for SodiumEditor.
 * This includes:
 * - Scroll gesture handling (scroll and fling)
 * - Overscroll and stretch effects
 * - Scroll bars
 * - Scroll clamping and bounds
 *
 * Note: Scroll state (scrollX, scrollY) is maintained by the parent view.
 */
public class Scroll {

  // Scroller and gestures
  public final OverScroller scroller;
  public GestureDetector gestureDetector;
  @Nullable public ValueAnimator flingStopAnimator;
  public static final long FLING_STOP_ANIM_DURATION_MS = 90;
  public float scrollY = 0f;
  public float scrollX = 0f;
  // Scroll configuration
  public int scrollMode = SCROLL_MODE_FREE;
 
  public static final int SCROLL_MODE_SINGLE_AXIS = 0;
  public static final int SCROLL_MODE_GRID = 1;
  public static final int SCROLL_MODE_FREE = 2;
  
  public float scrollSensitivity = 1f;
  public float flingSensitivity = 1f;
  public boolean flingBounceEnabled = false;
  public int flingBounceOverScrollPx = -1; // -1 => auto
  public float flingBounceOverScrollFactor = 0.2f;

  // Stretch delegate
  public final Stretch stretch;

  // Scroll bar state
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
  @Nullable public ValueAnimator scrollBarFadeAnimator;
  public final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  public final Runnable scrollBarHideRunnable = this::startScrollBarFadeOut;
public final Paint scrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint scrollBarHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF scrollBarThumbRect = new RectF();
  // Drag state
  
  public boolean draggingScrollBar = false;
  public float scrollBarDragOffset = 0f;

  // Lock axis for single-axis scrolling
  public int scrollLockAxis = 0; // 0 = none, 1 = horizontal, 2 = vertical
public float maxLineWidthForScroll = 0f;
  public float maxTextStartXForScroll = 0f;
  public float maxScrollXForScroll = 0f;
  public float dragMaxScrollX = -1f;
public boolean scrollerIsScrolling = false;
  
  // sodiumeditors interface
  

  private final SodiumEditor sodiumeditor;
  
  public Scroll(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;


    android.content.Context ctx = sodiumeditor.getContext();

  scroller = new OverScroller(ctx);

    ViewConfiguration config = ViewConfiguration.get(ctx);
    int touchSlop = config.getScaledTouchSlop();

    // Initialize Stretch
    stretch = new Stretch(sodiumeditor);

  }

  /**
   * Handle scroll gesture
   */
  public boolean handleScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    if (e2.getPointerCount() > 1) return true;
    
    float dx = distanceX * scrollSensitivity;
    float dy = distanceY * scrollSensitivity;
    
    // Apply scroll mode
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
    
    // Word wrap disables horizontal scroll
    if (sodiumeditor.isWordWrapEnabled) {
      dx = 0f;
    }
    
    // RTL support
    if (sodiumeditor.isRtl && !sodiumeditor.isWordWrapEnabled) {
      dx = -dx;
    }
    
    float maxX = 0f;
    if (!sodiumeditor.isWordWrapEnabled) {
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

    // Stretch overscroll
    if (stretch.stretchOverscrollEnabled) {
      if (!sodiumeditor.isWordWrapEnabled) {
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
      if (!sodiumeditor.isWordWrapEnabled) {
        if ((scrollX <= 0f && dx < 0f) || (scrollX >= maxX && dx > 0f)) {
          dx = 0f;
          nextX = scrollX;
        }
      }
    }

    scrollY=nextY;
    scrollX=nextX;
    clampScrollY();
    clampScrollX();
    showScrollBar();

    sodiumeditor.removeCallbacks(delayedWindowCheck);
    if (Math.abs(distanceY) > sodiumeditor.lineHeight * 6f) {
      sodiumeditor.checkAndLoadWindow();
    } else {
      sodiumeditor.postDelayed(delayedWindowCheck, 60);
    }

    sodiumeditor.invalidate();
    return true;
  }
  
  private final Runnable delayedWindowCheck = new Runnable() {
    @Override
    public void run() {
      sodiumeditor.checkAndLoadWindow();
    }
  };

  /**
   * Handle fling gesture
   */
  public boolean handleFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    int startX = Math.round(scrollX);
    int startY = Math.round(scrollY);
    int minX = 0;
    int maxX =
        sodiumeditor.isWordWrapEnabled
            ? 0
            : Math.max(
                0,
                Math.round(sodiumeditor.globalMaxLineWidth - (sodiumeditor.getWidth() - sodiumeditor.getTextStartX())));
    int minY = 0;

    float maxScrollYFloat;
    float effectiveHeight =
        (sodiumeditor.keyboardHeight > 0) ? sodiumeditor.getHeight() - sodiumeditor.keyboardHeight : sodiumeditor.getHeight();

    int lineCount =
        sodiumeditor.isWordWrapEnabled
            ? sodiumeditor.getTotalVisualLineCount()
            : (sodiumeditor.isEof
                ? sodiumeditor.getVisibleLineCount()
                : Math.max(1, sodiumeditor.getLinesCount()));
    
    if (sodiumeditor.isEof) {
      float paddingToUse =
          (sodiumeditor.keyboardHeight > 0)
              ? Math.min(100f, sodiumeditor.keyboardHeight * 0.4f)
              : 100f;
      maxScrollYFloat =
          Math.max(0f, lineCount * sodiumeditor.lineHeight - (effectiveHeight - paddingToUse));
    } else {
      float virtualExtraSpace = Math.max(sodiumeditor.prefetchLines * sodiumeditor.lineHeight, 2000f);
      maxScrollYFloat =
          Math.max(0f, lineCount * sodiumeditor.lineHeight + virtualExtraSpace - effectiveHeight);
    }
    int maxY = Math.max(0, Math.round(maxScrollYFloat));

    float vx = velocityX * flingSensitivity;
    float vy = velocityY * flingSensitivity;
    
    // Apply scroll mode
    if (scrollMode == SCROLL_MODE_SINGLE_AXIS) {
      int axis = scrollLockAxis;
      if (axis == 0) axis = (Math.abs(vx) >= Math.abs(vy)) ? 1 : 2;
      if (axis == 1) vy = 0f;
      else vx = 0f;
    } else if (scrollMode == SCROLL_MODE_GRID) {
      if (Math.abs(vx) >= Math.abs(vy)) vy = 0f;
      else vx = 0f;
    }
    
    // Word wrap disables horizontal fling
    if (sodiumeditor.isWordWrapEnabled) {
      vx = 0f;
    }
    
    // RTL support
    if (sodiumeditor.isRtl && !sodiumeditor.isWordWrapEnabled) {
      vx = -vx;
    }
    
    int overX = 0;
    int overY = 0;
    if (flingBounceEnabled) {
      if (!sodiumeditor.isWordWrapEnabled) overX = Math.max(overX, getFlingOverScrollX());
      overY = Math.max(overY, getFlingOverScrollY());
    }
    
    scroller.fling(
        startX, startY, (int) -vx, (int) -vy, minX, maxX, minY, maxY, overX, overY);
    sodiumeditor.postInvalidateOnAnimation();
    return true;
  }

  /**
   * Get maximum scroll X for clamping
   */
  

  /**
   * Get maximum scroll Y for clamping
   */
  
  

  /**
   * Clamp scroll X to valid bounds
   */
  
  /**
   * Get fling overscroll amount for X axis
   */
  public int getFlingOverScrollX() {
    if (!flingBounceEnabled) return 0;
    if (flingBounceOverScrollPx >= 0) return flingBounceOverScrollPx;
    return Math.max(24, Math.round(sodiumeditor.getWidth() * flingBounceOverScrollFactor));
  }

  /**
   * Get fling overscroll amount for Y axis
   */
  public int getFlingOverScrollY() {
    if (!flingBounceEnabled) return 0;
    if (flingBounceOverScrollPx >= 0) return flingBounceOverScrollPx;
    return Math.max(24, Math.round(sodiumeditor.getHeight() * flingBounceOverScrollFactor));
  }

  /**
   * Apply stretch effect for X axis
   */
  public void pullStretchX(float deltaPx, boolean toRight) {
    stretch.pullStretchX(deltaPx, toRight);
  }

  /**
   * Apply stretch effect for Y axis
   */
  public void pullStretchY(float deltaPx, boolean toBottom) {
    stretch.pullStretchY(deltaPx, toBottom);
  }

  /**
   * Absorb stretch from fling velocity for X axis
   */
  public void absorbStretchX(float velocityPxPerSec, boolean toRight) {
    stretch.absorbStretchX(velocityPxPerSec, toRight);
  }

  /**
   * Absorb stretch from fling velocity for Y axis
   */
  public void absorbStretchY(float velocityPxPerSec, boolean toBottom) {
    stretch.absorbStretchY(velocityPxPerSec, toBottom);
  }

  /**
   * Draw stretch effect
   */
  public void drawStretch(android.graphics.Canvas canvas) {
    stretch.drawStretch(canvas);
  }

  /**
   * Draw the scroll bar
   */
  public void drawScrollBar(android.graphics.Canvas canvas) {
    if (!scrollBarEnabled) return;
    if (scrollBarFadeEnabled && scrollBarAlpha <= 0f) return;
    int w = sodiumeditor.getWidth();
    int h = sodiumeditor.getHeight();
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
    android.graphics.RectF thumbRect = new android.graphics.RectF(left, thumbTop, right, thumbTop + thumbHeight);
    
    int baseColor = scrollBarColor;
    int alpha = (int) (Math.min(1f, scrollBarAlpha) * 255);
    int color = (baseColor & 0x00FFFFFF) | (alpha << 24);
    
    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    paint.setColor(color);
    
    if (draggingScrollBar) {
      int haloAlpha = (int) (alpha * 0.6f);
      int haloColor = (scrollBarHaloColor & 0x00FFFFFF) | (haloAlpha << 24);
      android.graphics.Paint haloPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      haloPaint.setColor(haloColor);
      float inset = Math.max(0f, scrollBarHaloSizePx);
      android.graphics.RectF halo =
          new android.graphics.RectF(
              thumbRect.left - inset,
              thumbRect.top - inset,
              thumbRect.right + inset,
              thumbRect.bottom + inset);
      float haloRadius = scrollBarCornerRadiusPx + inset;
      canvas.drawRoundRect(halo, haloRadius, haloRadius, haloPaint);
    }
    
    canvas.drawRoundRect(
        thumbRect,
        scrollBarCornerRadiusPx,
        scrollBarCornerRadiusPx,
        paint);
  }

  /**
   * Show scroll bar with fade effect
   */
  public void showScrollBar() {
    if (!scrollBarEnabled) return;
    if (!scrollBarFadeEnabled) {
      scrollBarAlpha = 1f;
      return;
    }
    cancelScrollBarFade();
    scrollBarAlpha = 1f;
    sodiumeditor.invalidate();
    mainHandler.removeCallbacks(scrollBarHideRunnable);
    mainHandler.postDelayed(scrollBarHideRunnable, scrollBarFadeDelayMs);
  }

  /**
   * Start fade out animation for scroll bar
   */
  public void startScrollBarFadeOut() {
    if (!scrollBarFadeEnabled || draggingScrollBar) return;
    if (scrollBarFadeAnimator != null) {
      scrollBarFadeAnimator.cancel();
    }
    scrollBarFadeAnimator = ValueAnimator.ofFloat(scrollBarAlpha, 0f);
    scrollBarFadeAnimator.setDuration(scrollBarFadeDurationMs);
    scrollBarFadeAnimator.addUpdateListener(
        animation -> {
          scrollBarAlpha = (float) animation.getAnimatedValue();
          sodiumeditor.invalidate();
        });
    scrollBarFadeAnimator.addListener(
        new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            scrollBarFadeAnimator = null;
          }
        });
    scrollBarFadeAnimator.start();
  }

  /**
   * Cancel scroll bar fade animation
   */
  public void cancelScrollBarFade() {
    if (scrollBarFadeAnimator != null) {
      scrollBarFadeAnimator.cancel();
      scrollBarFadeAnimator = null;
    }
  }

  /**
   * Update fling animation
   */
  

  /**
   * Abort any ongoing scroll animation
   */
  public void abortAnimation() {
    if (!scroller.isFinished()) {
      scroller.abortAnimation();
    }
    cancelFlingStopAnimation();
  }

  /**
   * Cancel fling stop animation
   */
  

  // Getters and Setters

  public void scrollTo(float x, float y) {
    scrollX=x;
    scrollY=y;
    clampScrollX();
    clampScrollY();
  }
  
  public void setScrollMode(int mode) {
    if (mode != SCROLL_MODE_SINGLE_AXIS && mode != SCROLL_MODE_GRID && mode != SCROLL_MODE_FREE) {
      return;
    }
    this.scrollMode = mode;
  }

  public void setScrollPosition(float x, float y) {
    scrollX =x;
     scrollY =y;
    clampScrollX();
    clampScrollY();
    sodiumeditor.invalidate();
  }

  public void setScrollSensitivity(float sensitivity) {
    if (sensitivity <= 0f) return;
    this.scrollSensitivity = sensitivity;
  }

  public void setFlingSensitivity(float sensitivity) {
    if (sensitivity <= 0f) return;
    this.flingSensitivity = sensitivity;
  }

  public void setScrollBarEnabled(boolean enabled) {
    if (scrollBarEnabled == enabled) return;
    scrollBarEnabled = enabled;
    sodiumeditor.invalidate();
  }

  public void setScrollBarColor(int color) {
    scrollBarColor = color;
    sodiumeditor.invalidate();
  }

  public void setScrollBarWidthPx(float px) {
    if (px <= 0f) return;
    scrollBarWidthPx = px;
    sodiumeditor.invalidate();
  }

  public void setScrollBarMinThumbPx(float px) {
    if (px <= 0f) return;
    scrollBarMinThumbPx = px;
    sodiumeditor.invalidate();
  }
  public void setScrollBarFadeEnabled(boolean enabled) {
    scrollBarFadeEnabled = enabled;
    if (!enabled) {
      cancelScrollBarFade();
      scrollBarAlpha = 1f;
      sodiumeditor.invalidate();
    } else {
      cancelScrollBarFade();
      scrollBarAlpha = 0f;
      sodiumeditor.invalidate();
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
    sodiumeditor.invalidate();
  }

  public void setScrollBarHaloSizePx(float px) {
    if (px < 0f) return;
    scrollBarHaloSizePx = px;
    sodiumeditor.invalidate();
  }

  public void setScrollBarCornerRadiusPx(float px) {
    if (px < 0f) return;
    scrollBarCornerRadiusPx = px;
    sodiumeditor.invalidate();
  }

  public void setScrollBarMarginPx(float px) {
    if (px < 0f) return;
    scrollBarMarginPx = px;
    sodiumeditor.invalidate();
  }

  public void setStretchOverscrollEnabled(boolean enabled) {
    stretch.setStretchOverscrollEnabled(enabled);
  }

  public void setStretchOverscrollStrength(float strength) {
    stretch.setStretchOverscrollStrength(strength);
  }

  public void setFlingBounceEnabled(boolean enabled) {
    this.flingBounceEnabled = enabled;
  }

  public void setFlingBounceDistancePx(int px) {
    this.flingBounceOverScrollPx = Math.max(0, px);
  }

  public void setFlingBounceDistanceFactor(float factor) {
    if (factor <= 0f) return;
    this.flingBounceOverScrollFactor = factor;
  }
  public void clampScrollX() {
    if (sodiumeditor.isWordWrapEnabled) {
     scrollX =0f;
      return;
    }
    float max = (sodiumeditor.pointerDown && dragMaxScrollX >= 0f) ? dragMaxScrollX : getMaxScrollXForClamp();
    boolean allowFlingOverscroll = flingBounceEnabled && scrollerIsScrolling;
    if (allowFlingOverscroll) {
      int over = getFlingOverScrollX();
      if ( scrollX < -over) scrollX =-over;
      if ( scrollX > max + over) scrollX =max + over;
      return;
    }
    if ( scrollX < 0) scrollX =0;
    if ( scrollX > max) scrollX =max;
  }

  

  
  public float getMaxScrollXForClamp() {
    if (sodiumeditor.isWordWrapEnabled) return 0f;
    float rawMaxWidth = sodiumeditor.globalMaxLineWidth;
    if (rawMaxWidth > maxLineWidthForScroll) {
      maxLineWidthForScroll = rawMaxWidth;
    }
    float textStartX = sodiumeditor.getTextStartX();
    if (textStartX > maxTextStartXForScroll) {
      maxTextStartXForScroll = textStartX;
    }
    float effectiveTextStartX = Math.max(textStartX, maxTextStartXForScroll);
    float candidateMax = Math.max(0f, maxLineWidthForScroll - (sodiumeditor.getWidth() - effectiveTextStartX));
    if (candidateMax > maxScrollXForScroll) {
      maxScrollXForScroll = candidateMax;
    }
    return maxScrollXForScroll;
  }
  public void scrollToLineFastForSelectAll(int line, int ch) {
    if (sodiumeditor.isWordWrapEnabled && (!sodiumeditor.wrapMetricsReady || sodiumeditor.wrapLinePrefix == null)) {
       scrollY =Math.max(0f, (line - 5) * sodiumeditor.lineHeight);
    } else {
      int targetVisual = sodiumeditor.getVisualIndexForLineAndChar(line, ch);
       scrollY =Math.max(0f, (targetVisual - 5) * sodiumeditor.lineHeight);
    }
    clampScrollY();
  }
  public void startFlingStopAnimation(float targetX, float targetY) {
    cancelFlingStopAnimation();
    float startX =  scrollX;
    float startY =  scrollY;
    float dx = targetX - startX;
    float dy = targetY - startY;
    if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
      scrollX =targetX;
       scrollY =targetY;
      clampScrollY();
      clampScrollX();
      return;
    }
    flingStopAnimator = ValueAnimator.ofFloat(0f, 1f);
    flingStopAnimator.setDuration(FLING_STOP_ANIM_DURATION_MS);
    flingStopAnimator.setInterpolator(new DecelerateInterpolator());
    flingStopAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          scrollX =startX + dx * t;
           scrollY = startY + dy * t;
          clampScrollY();
          clampScrollX();
          sodiumeditor.removeCallbacks(delayedWindowCheck);
          sodiumeditor.maybeKickWindowLoad(sodiumeditor.getGlobalLineForY( scrollY));
          sodiumeditor.postDelayed(delayedWindowCheck, 40);
          sodiumeditor.postInvalidateOnAnimation();
        });
    flingStopAnimator.addListener(
        new AnimatorListenerAdapter() {
        
          public void onAnimationEnd(Animator animation) {
            flingStopAnimator = null;
          }

         
          public void onAnimationCancel(Animator animation) {
            flingStopAnimator = null;
          }
        });
    flingStopAnimator.start();
  }

  public void cancelFlingStopAnimation() {
    if (flingStopAnimator != null) {
      flingStopAnimator.cancel();
      flingStopAnimator = null;
    }
  }

 
  public void computeScroll() {
    // Delegate to Scroll for basic scroll handling
    if (scroller.computeScrollOffset()) {
      scrollX = scroller.getCurrX();
      scrollY = scroller.getCurrY();
      scrollerIsScrolling = true;
      sodiumeditor.removeCallbacks(delayedWindowCheck);
      sodiumeditor.maybeKickWindowLoad(sodiumeditor.getGlobalLineForY( scrollY));
      sodiumeditor.postDelayed(delayedWindowCheck, 40);
      showScrollBar();
      sodiumeditor.postInvalidateOnAnimation();
    } else {
      if (scrollerIsScrolling) {
        scrollerIsScrolling = false;
        showScrollBar();
        if (stretch.stretchOverscrollEnabled) {
          stretch.releaseStretch();
        }
        if (flingBounceEnabled) {
          int maxX = Math.round(getMaxScrollXForClamp());
          int maxY = Math.round(getMaxScrollYForClamp());
          if ( scrollX < 0 ||  scrollX > maxX ||  scrollY < 0 ||  scrollY > maxY) {
            if (scroller.springBack(
                Math.round( scrollX), Math.round( scrollY), 0, maxX, 0, maxY)) {
              scrollerIsScrolling = true;
              sodiumeditor.postInvalidateOnAnimation();
              return;
            }
          }
        }
        sodiumeditor.checkAndLoadWindow();
        if (sodiumeditor.isWordWrapEnabled && sodiumeditor.wrapPrefixRebuildPending && !sodiumeditor.wrapPrefixBuilding) {
          sodiumeditor.wrapPrefixRebuildPending = false;
          sodiumeditor.scheduleWrapPrefixRebuildUpToWindow();
        }
        if (sodiumeditor.selection.hasSelection) sodiumeditor.popup.showPopupAtSelection();
      }
    }
  }

  
  public float getMaxScrollYForClamp() {
    // When word-wrap metrics are rebuilding (common after sodiumeditor.zoom), the temporary visual line count
    // can be underestimated, which would incorrectly clamp  scrollY and cause a visible "jump".
    if (sodiumeditor.isWordWrapEnabled && !sodiumeditor.wrapMetricsReady && (sodiumeditor.zoom.isScaling || sodiumeditor.zoom.mJustFinishedScale)) {
      return  scrollY;
    }

    float effectiveHeight = (sodiumeditor.keyboardHeight > 0) ? sodiumeditor.getHeight() - sodiumeditor.keyboardHeight : sodiumeditor.getHeight();
    int lineCount =
        sodiumeditor.isWordWrapEnabled
            ? sodiumeditor.getTotalVisualLineCount()
            : (sodiumeditor.isCodeFoldingEnabled
                ? sodiumeditor.getVisibleLineCount()
                : Math.max(1, sodiumeditor.getLinesCount()));
    if (sodiumeditor.isWordWrapEnabled && (sodiumeditor.selection.isSelectAllActive || sodiumeditor.selection.isEntireFileSelected)) {
      // Allow select-all jumps even when total visual metrics aren't ready yet.
      lineCount = Math.max(lineCount, sodiumeditor.selection.selEndLine + 1);
    }
    if (sodiumeditor.isEof) {
      float paddingToUse =
          (sodiumeditor.keyboardHeight > 0) ? sodiumeditor.getKeyboardBarrierPadding() : sodiumeditor.getBottomBarrierPadding();
      return Math.max(0f, lineCount * sodiumeditor.lineHeight - (effectiveHeight - paddingToUse));
    }
    float virtualExtraSpace = Math.max(sodiumeditor.prefetchLines * sodiumeditor.lineHeight, 2000f);
    return Math.max(0f, lineCount * sodiumeditor.lineHeight + virtualExtraSpace - effectiveHeight);
  }

  public void clampScrollY() {
    if (!sodiumeditor.isWordWrapEnabled && sodiumeditor.isWindowLoading &&  scrollY < sodiumeditor.windowStartLine * sodiumeditor.lineHeight) {
      boolean allowAboveWindow = scrollerIsScrolling || flingStopAnimator != null;
      if (!allowAboveWindow) {
         scrollY =sodiumeditor.windowStartLine * sodiumeditor.lineHeight;
        if (!scroller.isFinished()) scroller.abortAnimation();
      }
    }

    float maxScroll = getMaxScrollYForClamp();
    boolean allowFlingOverscroll = flingBounceEnabled && scrollerIsScrolling;
    if (allowFlingOverscroll) {
      int over = getFlingOverScrollY();
      if ( scrollY < -over)  scrollY =-over;
      if ( scrollY > maxScroll + over)  scrollY =maxScroll + over;
      return;
    }

    if ( scrollY < 0)  scrollY =0;
    if ( scrollY > maxScroll) {
       scrollY =maxScroll;
      if (sodiumeditor.isEof && !scroller.isFinished()) scroller.abortAnimation();
    }
  }
  public void cancelStretchRelease() {
    stretch.cancelStretchRelease();
  }

  public void releaseStretch() {
    stretch.releaseStretch();
  }


  

  
}
