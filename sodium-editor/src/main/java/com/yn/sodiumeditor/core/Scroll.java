package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.Stretch;
import com.yn.sodiumeditor.core.Edge;
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
  // Edge effect delegate
  public final Edge edge;

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
  
  // editors interface
  

  private final SodiumEditor editor;
  
  public Scroll(SodiumEditor editor) {
    this.editor = editor;


    android.content.Context ctx = editor.getContext();

  scroller = new OverScroller(ctx);

    ViewConfiguration config = ViewConfiguration.get(ctx);
    int touchSlop = config.getScaledTouchSlop();

    // Initialize Stretch
    stretch = new Stretch(editor);
    edge = new Edge(editor);

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
    if (editor.wordWrap.isWordWrapEnabled) {
      dx = 0f;
    }
    
    // RTL support
    if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) {
      dx = -dx;
    }
    
    float maxX = 0f;
    if (!editor.wordWrap.isWordWrapEnabled) {
      // نحدث الحد الأقصى دائماً ليشمل المساحة الوهمية
      maxX = getMaxScrollXForClamp();
      dragMaxScrollX = maxX;
    }
    
    float maxY = getMaxScrollYForClamp();
    float nextX = scrollX + dx;
    float nextY = scrollY + dy;

    // Stretch overscroll
    float minX = 0f; // العودة للصفر (لا مساحة قبل البداية)
    if (stretch.stretchOverscrollEnabled || edge.edgeEffectEnabled) {
      if (!editor.wordWrap.isWordWrapEnabled) {
        if (nextX < minX && dx < 0f) {
          android.util.Log.d("SodiumScroll", "Trigger pullLeft: dx=" + dx);
          stretch.pullStretchX(dx, false);
          edge.pullLeft(dx, e2.getY());
          nextX = minX;
        } else if (nextX > maxX && dx > 0f) {
          android.util.Log.d("SodiumScroll", "Trigger pullRight: dx=" + dx);
          stretch.pullStretchX(dx, true);
          edge.pullRight(dx, e2.getY());
          nextX = maxX;
        } else {
          edge.releaseHorizontal();
        }
      }
      if (nextY < 0f && dy < 0f) {
        android.util.Log.d("SodiumScroll", "Trigger pullTop: dy=" + dy);
        stretch.pullStretchY(dy, false);
        edge.pullTop(dy, e2.getX());
        nextY = 0f;
      } else if (nextY > maxY && dy > 0f) {
        android.util.Log.d("SodiumScroll", "Trigger pullBottom: dy=" + dy);
        stretch.pullStretchY(dy, true);
        edge.pullBottom(dy, e2.getX());
        nextY = maxY;
      } else {
        edge.releaseVertical();
      }
    } else {
      if (!editor.wordWrap.isWordWrapEnabled) {
        if ((scrollX <= 0f && dx < 0f) || (scrollX >= maxX && dx > 0f)) {
          dx = 0f;
          nextX = scrollX;
        }
      }
      edge.releaseVertical();
      edge.releaseHorizontal();
    }

    scrollY=nextY;
    scrollX=nextX;
    clampScrollY();
    clampScrollX();
    showScrollBar();

    editor.removeCallbacks(delayedWindowCheck);
    if (Math.abs(distanceY) > editor.textRender.lineHeight * 6f) {
      editor.fileIO.checkAndLoadWindow();
    } else {
      editor.postDelayed(delayedWindowCheck, 60);
    }

    editor.invalidate();
    return true;
  }
  
  private final Runnable delayedWindowCheck = new Runnable() {
    @Override
    public void run() {
      editor.fileIO.checkAndLoadWindow();
    }
  };

  /**
   * Handle fling gesture
   */
  public boolean handleFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    int startX = Math.round(scrollX);
    int startY = Math.round(scrollY);
    int minX = 0;
    int maxX = Math.round(getMaxScrollXForClamp());
    int minY = 0;

    float maxScrollYFloat;
    float effectiveHeight =
        (editor.keyboardHeight > 0) ? editor.getHeight() - editor.keyboardHeight : editor.getHeight();

    int lineCount =
        editor.wordWrap.isWordWrapEnabled
            ? editor.wordWrap.getTotalVisualLineCount()
            : (editor.fileIO.isEof
                ? editor.codeFold.getVisibleLineCount()
                : Math.max(1, editor.getLinesCount()));
    
    if (editor.fileIO.isEof) {
      float paddingToUse =
          (editor.keyboardHeight > 0)
              ? Math.min(100f, editor.keyboardHeight * 0.4f)
              : 100f;
      maxScrollYFloat =
          Math.max(0f, lineCount * editor.textRender.lineHeight - (effectiveHeight - paddingToUse));
    } else {
      float virtualExtraSpace = Math.max(editor.textRender.prefetchLines * editor.textRender.lineHeight, 2000f);
      maxScrollYFloat =
          Math.max(0f, lineCount * editor.textRender.lineHeight + virtualExtraSpace - effectiveHeight);
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
    if (editor.wordWrap.isWordWrapEnabled) {
      vx = 0f;
    }
    
    // RTL support
    if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) {
      vx = -vx;
    }
    
    int overX = 0;
    int overY = 0;
    if (flingBounceEnabled) {
      if (!editor.wordWrap.isWordWrapEnabled) overX = Math.max(overX, getFlingOverScrollX());
      overY = Math.max(overY, getFlingOverScrollY());
    }
    
    scroller.fling(
        startX, startY, (int) -vx, (int) -vy, minX, maxX, minY, maxY, overX, overY);
    editor.postInvalidateOnAnimation();
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
    return Math.max(24, Math.round(editor.getWidth() * flingBounceOverScrollFactor));
  }

  /**
   * Get fling overscroll amount for Y axis
   */
  public int getFlingOverScrollY() {
    if (!flingBounceEnabled) return 0;
    if (flingBounceOverScrollPx >= 0) return flingBounceOverScrollPx;
    return Math.max(24, Math.round(editor.getHeight() * flingBounceOverScrollFactor));
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

  public void drawEdge(android.graphics.Canvas canvas) {
    edge.draw(canvas);
  }

  /**
   * Draw the scroll bar
   */
  public void drawScrollBar(android.graphics.Canvas canvas) {
    if (!scrollBarEnabled) return;
    if (scrollBarFadeEnabled && scrollBarAlpha <= 0f) return;
    int w = editor.getWidth();
    int h = editor.getHeight();
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
    editor.invalidate();
    editor.caret.mainHandler.removeCallbacks(scrollBarHideRunnable);
    editor.caret.mainHandler.postDelayed(scrollBarHideRunnable, scrollBarFadeDelayMs);
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
          editor.invalidate();
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
    scrollerIsScrolling = false;
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
    smoothScrollTo(x, y);
  }

  public void smoothScrollTo(float targetX, float targetY) {
    abortAnimation();
    
    float startX = scrollX;
    float startY = scrollY;
    
    // Clamp targets
    float maxX = getMaxScrollXForClamp();
    float maxY = getMaxScrollYForClamp();
    float tx = Math.max(0, Math.min(targetX, maxX));
    float ty = Math.max(0, Math.min(targetY, maxY));
    
    if (Math.abs(tx - startX) < 1f && Math.abs(ty - startY) < 1f) {
        scrollX = tx;
        scrollY = ty;
        editor.invalidate();
        return;
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
    editor.invalidate();
  }

  public void setScrollBarColor(int color) {
    scrollBarColor = color;
    editor.invalidate();
  }

  public void setScrollBarWidthPx(float px) {
    if (px <= 0f) return;
    scrollBarWidthPx = px;
    editor.invalidate();
  }

  public void setScrollBarMinThumbPx(float px) {
    if (px <= 0f) return;
    scrollBarMinThumbPx = px;
    editor.invalidate();
  }
  public void setScrollBarFadeEnabled(boolean enabled) {
    scrollBarFadeEnabled = enabled;
    if (!enabled) {
      cancelScrollBarFade();
      scrollBarAlpha = 1f;
      editor.invalidate();
    } else {
      cancelScrollBarFade();
      scrollBarAlpha = 0f;
      editor.invalidate();
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
    editor.invalidate();
  }

  public void setScrollBarHaloSizePx(float px) {
    if (px < 0f) return;
    scrollBarHaloSizePx = px;
    editor.invalidate();
  }

  public void setScrollBarCornerRadiusPx(float px) {
    if (px < 0f) return;
    scrollBarCornerRadiusPx = px;
    editor.invalidate();
  }

  public void setScrollBarMarginPx(float px) {
    if (px < 0f) return;
    scrollBarMarginPx = px;
    editor.invalidate();
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
    if (editor.wordWrap.isWordWrapEnabled) {
     scrollX =0f;
      return;
    }
    float min = 0f;
    float max = (editor.pointerDown && dragMaxScrollX >= 0f) ? dragMaxScrollX : getMaxScrollXForClamp();
    boolean allowFlingOverscroll = flingBounceEnabled && scrollerIsScrolling;
    if (allowFlingOverscroll) {
      int over = getFlingOverScrollX();
      if ( scrollX < min - over) scrollX = min - over;
      if ( scrollX > max + over) scrollX =max + over;
      return;
    }
    if ( scrollX < min) scrollX = min;
    if ( scrollX > max) scrollX =max;
  }

  

  
  public float getMaxScrollXForClamp() {
    if (editor.wordWrap.isWordWrapEnabled) return 0f;
    
    // استخدم العرض الأقصى المتاح حالياً
    float rawMaxWidth = editor.textRender.globalMaxLineWidth;
    float textStartX = editor.getTextStartX();
    
    float extraSpace = 100f; // المساحة الوهمية الإضافية
    float candidateMax = Math.max(0f, (rawMaxWidth + extraSpace) - (editor.getWidth() - textStartX));
    
    // نقوم بتحديث القيم المخزنة فقط إذا زاد حجم النص فعلياً
    if (rawMaxWidth > maxLineWidthForScroll) maxLineWidthForScroll = rawMaxWidth;
    
    // نرجع القيمة المحسوبة مباشرة لضمان الدقة في الأنيميشن
    return candidateMax;
  }
  public void scrollToLineFastForSelectAll(int line, int ch) {
    if (editor.wordWrap.isWordWrapEnabled && (!editor.wordWrap.wrapMetricsReady || editor.wordWrap.wrapLinePrefix == null)) {
       scrollY =Math.max(0f, (line - 5) * editor.textRender.lineHeight);
    } else {
      int targetVisual = editor.getVisualIndexForLineAndChar(line, ch);
       scrollY =Math.max(0f, (targetVisual - 5) * editor.textRender.lineHeight);
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
          editor.removeCallbacks(delayedWindowCheck);
          editor.maybeKickWindowLoad(editor.getGlobalLineForY( scrollY));
          editor.postDelayed(delayedWindowCheck, 40);
          editor.postInvalidateOnAnimation();
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
      float oldX = scrollX;
      float oldY = scrollY;
      scrollX = scroller.getCurrX();
      scrollY = scroller.getCurrY();
      scrollerIsScrolling = true;
      
      // Hit boundaries during fling: absorb for edge effect and stretch
      float maxX = getMaxScrollXForClamp();
      float maxY = getMaxScrollYForClamp();
      float velocity = scroller.getCurrVelocity();
      
      if (edge.edgeEffectEnabled) {
        if (scrollY <= 0 && oldY > 0) {
          android.util.Log.d("SodiumScroll", "Fling absorbTop: velocity=" + velocity);
          edge.absorbTop(velocity);
        } else if (scrollY >= maxY && oldY < maxY) {
          android.util.Log.d("SodiumScroll", "Fling absorbBottom: velocity=" + velocity);
          edge.absorbBottom(velocity);
        }
        if (scrollX <= 0 && oldX > 0) {
          android.util.Log.d("SodiumScroll", "Fling absorbLeft: velocity=" + velocity);
          edge.absorbLeft(velocity);
        } else if (scrollX >= maxX && oldX < maxX) {
          android.util.Log.d("SodiumScroll", "Fling absorbRight: velocity=" + velocity);
          edge.absorbRight(velocity);
        }
      }
      if (stretch.stretchOverscrollEnabled) {
        if (scrollY <= 0 && oldY > 0) stretch.absorbStretchY(velocity, false);
        else if (scrollY >= maxY && oldY < maxY) stretch.absorbStretchY(velocity, true);
        if (scrollX <= 0 && oldX > 0) stretch.absorbStretchX(velocity, false);
        else if (scrollX >= maxX && oldX < maxX) stretch.absorbStretchX(velocity, true);
      }

      editor.removeCallbacks(delayedWindowCheck);
      editor.maybeKickWindowLoad(editor.getGlobalLineForY( scrollY));
      editor.postDelayed(delayedWindowCheck, 40);
      showScrollBar();
      editor.postInvalidateOnAnimation();
    } else {
      if (scrollerIsScrolling) {
        scrollerIsScrolling = false;
        showScrollBar();
        if (stretch.stretchOverscrollEnabled) {
          stretch.releaseStretch();
        }
        edge.releaseAll();
        if (flingBounceEnabled) {
          int maxX = Math.round(getMaxScrollXForClamp());
          int maxY = Math.round(getMaxScrollYForClamp());
          if ( scrollX < 0 ||  scrollX > maxX ||  scrollY < 0 ||  scrollY > maxY) {
            if (scroller.springBack(
                Math.round( scrollX), Math.round( scrollY), 0, maxX, 0, maxY)) {
              scrollerIsScrolling = true;
              editor.postInvalidateOnAnimation();
              return;
            }
          }
        }
        editor.fileIO.checkAndLoadWindow();
        if (editor.wordWrap.isWordWrapEnabled && editor.wordWrap.wrapPrefixRebuildPending && !editor.wordWrap.wrapPrefixBuilding) {
          editor.wordWrap.wrapPrefixRebuildPending = false;
          editor.wordWrap.scheduleWrapPrefixRebuildUpToWindow();
        }
        if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
      }
    }
  }


  public float getMaxScrollYForClamp() {
    // When word-wrap metrics are rebuilding (common after editor.zoom), the temporary visual line count
    // can be underestimated, which would incorrectly clamp  scrollY and cause a visible "jump".
    if (editor.wordWrap.isWordWrapEnabled && !editor.wordWrap.wrapMetricsReady && (editor.zoom.isScaling || editor.zoom.mJustFinishedScale)) {
      return  scrollY;
    }

    float effectiveHeight = (editor.keyboardHeight > 0) ? editor.getHeight() - editor.keyboardHeight : editor.getHeight();
    int lineCount =
        editor.wordWrap.isWordWrapEnabled
            ? editor.wordWrap.getTotalVisualLineCount()
            : (editor.codeFold.isCodeFoldingEnabled
                ? editor.codeFold.getVisibleLineCount()
                : Math.max(1, editor.getLinesCount()));
    if (editor.wordWrap.isWordWrapEnabled && (editor.selection.isSelectAllActive || editor.selection.isEntireFileSelected)) {
      // Allow select-all jumps even when total visual metrics aren't ready yet.
      lineCount = Math.max(lineCount, editor.selection.selEndLine + 1);
    }
    if (editor.fileIO.isEof) {
      float paddingToUse =
          (editor.keyboardHeight > 0) ? editor.getKeyboardBarrierPadding() : editor.getBottomBarrierPadding();
      return Math.max(0f, lineCount * editor.textRender.lineHeight - (effectiveHeight - paddingToUse));
    }
    float virtualExtraSpace = Math.max(editor.textRender.prefetchLines * editor.textRender.lineHeight, 2000f);
    return Math.max(0f, lineCount * editor.textRender.lineHeight + virtualExtraSpace - effectiveHeight);
  }

  public void clampScrollY() {
    if (!editor.wordWrap.isWordWrapEnabled && editor.fileIO.isWindowLoading &&  scrollY < editor.textRender.windowStartLine * editor.textRender.lineHeight) {
      boolean allowAboveWindow = scrollerIsScrolling || flingStopAnimator != null;
      if (!allowAboveWindow) {
         scrollY =editor.textRender.windowStartLine * editor.textRender.lineHeight;
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
      if (editor.fileIO.isEof && !scroller.isFinished()) scroller.abortAnimation();
    }
  }
  public void cancelStretchRelease() {
    stretch.cancelStretchRelease();
  }

  public void releaseStretch() {
    stretch.releaseStretch();
  }

  public void releaseEdge() {
    edge.releaseAll();
  }

  // ========================================================================
  // Scroll Helper Methods
  // ========================================================================

  /**
   * Get effective scroll X (handles RTL)
   */
  public float getEffectiveScrollX() {
    return editor.textRender.isRtl ? -scrollX : scrollX;
  }

  /**
   * Convert view X to text X
   */
  public float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - editor.getTextStartX();
  }

  /**
   * Keep cursor visible horizontally
   */
  public void keepCursorVisibleHorizontally() {
    if (editor.scaleGestureDetector != null
        && (editor.zoom.isScaling || editor.scaleGestureDetector.isInProgress() || editor.multiTouchActive)) {
      return;
    }
    float oldScrollX = scrollX;
    float oldScrollY = scrollY;
    
    // Rebuild fold intervals if dirty before calculating cursor position
    if (editor.codeFold.isCodeFoldingEnabled) {
      editor.codeFold.rebuildFoldIntervalsIfNeeded();
    }
    
    // Get visual index accounting for code folding
    int cursorVisualIndex;
    if (editor.codeFold.isCodeFoldingEnabled) {
      cursorVisualIndex = editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
      if (cursorVisualIndex < 0) return; // Line is hidden by fold
    } else {
      cursorVisualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
    }
    
    float cursorYTop = cursorVisualIndex * editor.textRender.lineHeight;
    float cursorYBottom = cursorYTop + editor.textRender.lineHeight;
    int viewHeight = editor.getHeight() - editor.keyboardHeight;
    if (viewHeight <= 0) viewHeight = editor.getHeight();

    float bottomPadding =
        (editor.keyboardHeight > 0) ? editor.getKeyboardBarrierPadding() : editor.getBottomBarrierPadding();
    float effectiveVisibleHeight = Math.max(0f, viewHeight - bottomPadding);
    float visibleTop = scrollY;
    float visibleBottom = scrollY + effectiveVisibleHeight;

    float newScrollY = scrollY;
    if (cursorYBottom > visibleBottom) newScrollY = cursorYBottom - (viewHeight - bottomPadding);
    else if (cursorYTop < visibleTop) newScrollY = cursorYTop;

    if (editor.keyboardHeight > 0) {
      float keyboardTop = editor.getHeight() - editor.keyboardHeight;
      float paddingAboveKeyboard = editor.getKeyboardBarrierPadding();
      float currentCursorViewY = cursorYBottom - scrollY;
      if (currentCursorViewY >= keyboardTop - paddingAboveKeyboard) {
        newScrollY = cursorYBottom - (editor.getHeight() - editor.keyboardHeight - paddingAboveKeyboard);
      }
    }
    
    float newScrollX = scrollX;
    if (!editor.wordWrap.isWordWrapEnabled) {
      String line = editor.getLineTextForRender(editor.cursor.cursorLine);
      int safeChar = Math.min(editor.cursor.cursorChar, editor.getLogicalLineLength(editor.cursor.cursorLine, line));
      float cursorX = editor.getCaretXForLine(line, editor.cursor.cursorLine, safeChar);

      float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
      float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
      float scrollMargin = 50f;
      float effectiveScrollX = editor.getEffectiveScrollX();
      float cursorViewX = editor.getTextStartX() + cursorX - effectiveScrollX;
      float minView = viewLeft + scrollMargin;
      float maxView = viewRight - scrollMargin;
      if (cursorViewX < minView) {
        effectiveScrollX = editor.getTextStartX() + cursorX - minView;
      } else if (cursorViewX > maxView) {
        effectiveScrollX = editor.getTextStartX() + cursorX - maxView;
      }
      float max = getMaxScrollXForClamp();
      float minEffective = editor.textRender.isRtl ? -max : 0f;
      float maxEffective = editor.textRender.isRtl ? 0f : max;
      if (effectiveScrollX < minEffective) effectiveScrollX = minEffective;
      if (effectiveScrollX > maxEffective) effectiveScrollX = maxEffective;
      newScrollX = editor.textRender.isRtl ? -effectiveScrollX : effectiveScrollX;
    } else {
      newScrollX = 0f;
    }

    if (Math.abs(newScrollX - oldScrollX) > 1f || Math.abs(newScrollY - oldScrollY) > 1f) {
        smoothScrollTo(newScrollX, newScrollY);
    } else {
        editor.invalidateCursorArea();
    }
  }

  /**
   * Get bottom barrier padding for scroll
   */
  public float getBottomBarrierPadding() {
    float base = com.yn.sodiumeditor.renderer.TextRender.BOTTOM_SCROLL_OFFSET;
    float minSpace = com.yn.sodiumeditor.renderer.TextRender.MIN_BOTTOM_VISIBLE_SPACE;
    if (editor.textRender.lineHeight > 0f) {
      base = Math.max(base, editor.textRender.lineHeight * 2f);
      minSpace = Math.max(minSpace, editor.textRender.lineHeight * 2f);
    }
    return Math.max(base, minSpace);
  }

  /**
   * Get keyboard barrier padding for scroll
   */
  public float getKeyboardBarrierPadding() {
    if (editor.keyboardHeight <= 0) return 0f;
    float minPad = (editor.textRender.lineHeight > 0f) ? editor.textRender.lineHeight * 2f : com.yn.sodiumeditor.renderer.TextRender.MIN_BOTTOM_VISIBLE_SPACE;
    float maxPad = (editor.textRender.lineHeight > 0f) ? editor.textRender.lineHeight * 3.5f : com.yn.sodiumeditor.renderer.TextRender.BOTTOM_SCROLL_OFFSET;
    float kbPad = editor.keyboardHeight * 0.4f;
    return Math.max(minPad, Math.min(maxPad, kbPad));
  }

}
