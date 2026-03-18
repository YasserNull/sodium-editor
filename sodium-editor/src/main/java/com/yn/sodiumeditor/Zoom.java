package com.yn.sodiumeditor;

import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import androidx.annotation.Nullable;
import android.graphics.Paint;
import android.text.TextPaint;
/**
 * ZoomManager handles all zoom logic for SodiumEditor.
 * This includes:
 * - Pinch-to-zoom gestures
 * - Zoom bounds (min/max text size)
 * - Zoom step quantization
 * - Visual zoom during pinch (deferred wrap reflow)
 * - Scroll adjustment during zoom
 */
public class Zoom {

  // Zoom constants
  public static final float MIN_TEXT_SIZE = 8f;
  public static final float MAX_TEXT_SIZE = 56f;

  // Zoom configuration
  public boolean isZoomEnabled = true;
  public boolean hideDecorationsWhileZooming = true;
  public float minZoomTextSizePx = MIN_TEXT_SIZE;
  public float maxZoomTextSizePx = MAX_TEXT_SIZE;
  public float zoomStepClampSp = 0.2f;
  public boolean deferWrapReflowDuringPinch = true;

  // Pinch zoom state
  public boolean pinchVisualZoomActive = false;
  public float pinchVisualScale = 1f;
  public float pinchStartTextSizePx = 0f;
  public float pinchTargetTextSizePx = 0f;
  public float pinchFocusX = 0f;
  public float pinchFocusY = 0f;
  public int pinchAnchorGlobalLineAtFocus = -1;

  // Wrap prefix state for deferred reflow
  @Nullable public int[] pendingWrapPrefixCounts = null;
  @Nullable public int[] pendingWrapPrefixPrefix = null;
  public int pendingWrapPrefixTotalVisualLines = 0;
  public int pendingWrapPrefixWidthPx = -1;
  public int pendingWrapPrefixValidUpToLine = -1;
  public boolean pendingApplyWrapPrefixUpdate = false;

  // Zoom scroll adjustment for word wrap
  public int pendingZoomScrollAdjustGlobalLine = -1;
  public float pendingZoomScrollAdjustFocusY = -1f;

  // State flags
  public boolean mJustFinishedScale = false;
  public boolean isScaling = false;
  public float lastFocusX, lastFocusY;


private final SodiumEditor sodiumeditor;

  public Zoom(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
    }
  

  /**
   * Create ScaleGestureDetector.OnScaleGestureListener for ZoomManager
   */
  public ScaleGestureDetector.SimpleOnScaleGestureListener createScaleListener() {
    return new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      @Override
      public boolean onScaleBegin(ScaleGestureDetector detector) {
        mJustFinishedScale = false;
        isScaling = true;
        lastFocusX = detector.getFocusX();
        lastFocusY = detector.getFocusY();

        if (sodiumeditor.wordWrap.isWordWrapEnabled && deferWrapReflowDuringPinch) {
          pinchVisualZoomActive = true;
          pinchVisualScale = 1f;
          pinchStartTextSizePx = sodiumeditor.textRender.paint.getTextSize();
          pinchTargetTextSizePx = pinchStartTextSizePx;
          pinchFocusX = lastFocusX;
          pinchFocusY = lastFocusY;
          pinchAnchorGlobalLineAtFocus = sodiumeditor.getGlobalLineForY(sodiumeditor.scroll.scrollY + pinchFocusY);
        } else {
          pinchVisualZoomActive = false;
          pinchVisualScale = 1f;
          pinchStartTextSizePx = 0f;
          pinchTargetTextSizePx = 0f;
          pinchAnchorGlobalLineAtFocus = -1;
        }
        return true;
      }

      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        if (!isZoomEnabled) {
          return false;
        }

        float scale = detector.getScaleFactor();
        float focusX = detector.getFocusX();
        float focusY = detector.getFocusY();

        if (pinchVisualZoomActive) {
          pinchFocusX = focusX;
          pinchFocusY = focusY;
          pinchAnchorGlobalLineAtFocus = sodiumeditor.getGlobalLineForY(sodiumeditor.scroll.scrollY + focusY);

          pinchVisualScale *= scale;
          float targetSize = pinchStartTextSizePx * pinchVisualScale;
          targetSize = Math.max(minZoomTextSizePx, Math.min(targetSize, maxZoomTextSizePx));
          targetSize = quantizeZoomSizePx(targetSize);
          pinchTargetTextSizePx = targetSize;
          pinchVisualScale =
              (pinchStartTextSizePx > 0f)
                  ? (pinchTargetTextSizePx / pinchStartTextSizePx)
                  : 1f;
          sodiumeditor.invalidate();
          return true;
        }

        int anchorGlobalLineAtFocus = -1;
        if (sodiumeditor.wordWrap.isWordWrapEnabled) {
          anchorGlobalLineAtFocus = sodiumeditor.getGlobalLineForY(sodiumeditor.scroll.scrollY + focusY);
        }

        // Zoom
        float oldLineHeight = sodiumeditor.textRender.paint.getFontSpacing();
        float currentSize = sodiumeditor.textRender.paint.getTextSize();
        float newSize = currentSize * scale;

        newSize = Math.max(minZoomTextSizePx, Math.min(newSize, maxZoomTextSizePx));
        newSize = quantizeZoomSizePx(newSize);

        if (Math.abs(newSize - currentSize) > 0.1f) {
          sodiumeditor.applyTextSizePx(newSize);
          float newLineHeight = sodiumeditor.textRender.paint.getFontSpacing();
          float effectiveScaleY = (oldLineHeight > 0) ? newLineHeight / oldLineHeight : 1f;

          // Adjust scroll to make zoom appear centered on the focal point.
          float effectiveScrollX = sodiumeditor.getEffectiveScrollX();
          effectiveScrollX =
              (effectiveScrollX + focusX - sodiumeditor.getTextStartX()) * scale
                  - (focusX - sodiumeditor.getTextStartX());
          sodiumeditor.scroll.scrollX = (sodiumeditor.textRender.isRtl ? -effectiveScrollX : effectiveScrollX);
          sodiumeditor.scroll.scrollY = (sodiumeditor.scroll.scrollY + focusY) * effectiveScaleY - focusY;

          if (sodiumeditor.wordWrap.isWordWrapEnabled) {
            pendingZoomScrollAdjustGlobalLine = anchorGlobalLineAtFocus;
            pendingZoomScrollAdjustFocusY = focusY;
          }
        }

        lastFocusX = focusX;
        lastFocusY = focusY;

        sodiumeditor.scroll.clampScrollX();
        sodiumeditor.scroll.clampScrollY();
        sodiumeditor.invalidate();
        return true;
      }

      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
        mJustFinishedScale = true;
        isScaling = false;
        if (pinchVisualZoomActive) {
          pinchVisualZoomActive = false;
          pinchVisualScale = 1f;

          float oldSize = sodiumeditor.textRender.paint.getTextSize();
          float oldLineHeight = sodiumeditor.textRender.paint.getFontSpacing();
          float targetSize = quantizeZoomSizePx(pinchTargetTextSizePx);
          float focusX = pinchFocusX;
          float focusY = pinchFocusY;
          int anchorLine = pinchAnchorGlobalLineAtFocus;

          // Commit the final zoom once, then rebuild wrap metrics.
          if (Math.abs(targetSize - oldSize) > 0.1f) {
            sodiumeditor.applyTextSizePx(targetSize, sodiumeditor.wordWrap.isWordWrapEnabled);
            float newLineHeight = sodiumeditor.textRender.paint.getFontSpacing();
            float effectiveScaleY =
                (oldLineHeight > 0) ? newLineHeight / oldLineHeight : 1f;

            float effectiveScrollX = sodiumeditor.getEffectiveScrollX();
            effectiveScrollX =
                (effectiveScrollX + focusX - sodiumeditor.getTextStartX()) * targetSize / oldSize
                    - (focusX - sodiumeditor.getTextStartX());
            sodiumeditor.scroll.scrollX = (sodiumeditor.textRender.isRtl ? -effectiveScrollX : effectiveScrollX);
            sodiumeditor.scroll.scrollY = (sodiumeditor.scroll.scrollY + focusY) * effectiveScaleY - focusY;

            if (sodiumeditor.wordWrap.isWordWrapEnabled && anchorLine >= 0) {
              pendingZoomScrollAdjustGlobalLine = anchorLine;
              pendingZoomScrollAdjustFocusY = focusY;
            }
            sodiumeditor.scroll.clampScrollX();
            sodiumeditor.scroll.clampScrollY();
            sodiumeditor.invalidate();
          }
        }
        if (sodiumeditor.wordWrap.wrapPrefixBuilding) {
          sodiumeditor.wordWrap.wrapPrefixRebuildPending = true;
          sodiumeditor.scheduleWrapPrefixRebuildUpToWindow();
        }

        // Perform delayed scroll adjustment for word wrap after scaling, if pending.
        if (sodiumeditor.wordWrap.isWordWrapEnabled && pendingZoomScrollAdjustGlobalLine != -1) {
          final int targetGlobalLine = pendingZoomScrollAdjustGlobalLine;
          final float targetFocusY = pendingZoomScrollAdjustFocusY;

          // Reset pending flags immediately to prevent multiple adjustments
          pendingZoomScrollAdjustGlobalLine = -1;
          pendingZoomScrollAdjustFocusY = -1f;

          // Use android.os.Handler for posting
          new android.os.Handler(android.os.Looper.getMainLooper()).post(
              new Runnable() {
                @Override
                public void run() {
                  // Check if wrap metrics are ready
                  // Note: This requires additional sodiumeditor methods for wrap metrics
                  // For now, this is a simplified version
                }
              });
        }
      }
    };
  }

  /**
   * Quantize zoom size to step increments
   */
  public float quantizeZoomSizePx(float sizePx) {
    if (zoomStepClampSp <= 0f) return sizePx;
    float stepPx = sodiumeditor.spToPx(zoomStepClampSp);
    if (stepPx <= 0f) return sizePx;
    return Math.round(sizePx / stepPx) * stepPx;
  }

  /**
   * Check if zoom gesture is active
   */
  public boolean isZoomGestureActive() {
    return isScaling
        || pinchVisualZoomActive
        || (sodiumeditor != null && false); // multiTouchActive check would be in sodiumeditor
  }

  /**
   * Check if decorations should be drawn during zoom
   */
  public boolean shouldDrawDecorations() {
    return !(hideDecorationsWhileZooming && isZoomGestureActive());
  }

  // Getters and Setters

  public void setZoomEnabled(boolean enabled) {
    isZoomEnabled = enabled;
  }

  // When enabled (default), pinch-zoom with word wrap avoids reflow during the gesture and
  // rebuilds wrapping once the user releases their fingers.
  public void setDeferWordWrapReflowDuringZoom(boolean enabled) {
    deferWrapReflowDuringPinch = enabled;
  }

  public void setZoomTextSizeRange(float minSp, float maxSp) {
    float minPx = sodiumeditor.spToPx(minSp);
    float maxPx = sodiumeditor.spToPx(maxSp);
    float min = Math.max(1f, Math.min(minPx, maxPx));
    float max = Math.max(min, maxPx);
    minZoomTextSizePx = min;
    maxZoomTextSizePx = max;
  }

  public void setZoomStepClamp(float maxStep) {
    zoomStepClampSp = Math.max(0f, maxStep);
  }

  public void setHideDecorationsWhileZooming(boolean enabled) {
    hideDecorationsWhileZooming = enabled;
    sodiumeditor.invalidate();
  }
  
}
