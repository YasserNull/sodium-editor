package com.yn.sodiumeditor.view;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public final class ZoomManager {
  private static final float MIN_TEXT_SIZE = 8f;
  private static final float MAX_TEXT_SIZE = 56f;

  private final SodiumEditorView view;
  private final ScaleGestureDetector scaleGestureDetector;

  private boolean isZoomEnabled = true;
  private boolean hideDecorationsWhileZooming = true;
  boolean mJustFinishedScale = false;
  boolean isScaling = false;
  private float lastFocusX;
  private float lastFocusY;
  private float minZoomTextSizePx = MIN_TEXT_SIZE;
  private float maxZoomTextSizePx = MAX_TEXT_SIZE;
  private float zoomStepClampSp = 0.2f;
  private boolean deferWrapReflowDuringPinch = true;
  private boolean pinchVisualZoomActive = false;
  private float pinchVisualScale = 1f;
  private float pinchStartTextSizePx = 0f;
  private float pinchTargetTextSizePx = 0f;
  private float pinchFocusX = 0f;
  private float pinchFocusY = 0f;
  private int pinchAnchorGlobalLineAtFocus = -1;
  private int pendingZoomScrollAdjustGlobalLine = -1;
  private float pendingZoomScrollAdjustFocusY = -1f;
  private boolean multiTouchActive = false;
  private boolean hadMultiTouch = false;

  ZoomManager(SodiumEditorView view, Context ctx) {
    this.view = view;
    this.scaleGestureDetector =
        new ScaleGestureDetector(
            ctx,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector detector) {
                mJustFinishedScale = false;
                isScaling = true;
                lastFocusX = detector.getFocusX();
                lastFocusY = detector.getFocusY();
                view.abortScrollAnimationForZoom();
                if (view.wordWrapManager.isWordWrapEnabled && deferWrapReflowDuringPinch) {
                  pinchVisualZoomActive = true;
                  pinchVisualScale = 1f;
                  pinchStartTextSizePx = view.getPaintTextSizePxForZoom();
                  pinchTargetTextSizePx = pinchStartTextSizePx;
                  pinchFocusX = lastFocusX;
                  pinchFocusY = lastFocusY;
                  pinchAnchorGlobalLineAtFocus =
                      view.getGlobalLineForY(view.scrollManager.scrollY + pinchFocusY);
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
                  pinchAnchorGlobalLineAtFocus =
                      view.getGlobalLineForY(view.scrollManager.scrollY + focusY);

                  pinchVisualScale *= scale;
                  float targetSize = pinchStartTextSizePx * pinchVisualScale;
                  targetSize = Math.max(minZoomTextSizePx, Math.min(targetSize, maxZoomTextSizePx));
                  targetSize = quantizeZoomSizePx(targetSize);
                  pinchTargetTextSizePx = targetSize;
                  pinchVisualScale =
                      (pinchStartTextSizePx > 0f)
                          ? (pinchTargetTextSizePx / pinchStartTextSizePx)
                          : 1f;
                  view.invalidate();
                  return true;
                }

                int anchorGlobalLineAtFocus = -1;
                if (view.wordWrapManager.isWordWrapEnabled) {
                  anchorGlobalLineAtFocus = view.getGlobalLineForY(view.scrollManager.scrollY + focusY);
                }

                float oldLineHeight = view.getPaintFontSpacingPxForZoom();
                float currentSize = view.getPaintTextSizePxForZoom();
                float newSize = currentSize * scale;

                newSize = Math.max(minZoomTextSizePx, Math.min(newSize, maxZoomTextSizePx));
                newSize = quantizeZoomSizePx(newSize);

                if (Math.abs(newSize - currentSize) > 0.1f) {
                  view.applyZoomTextSizePx(newSize);
                  float newLineHeight = view.getPaintFontSpacingPxForZoom();
                  float effectiveScaleY = (oldLineHeight > 0) ? newLineHeight / oldLineHeight : 1f;

                  float effectiveScrollX = view.getEffectiveScrollX();
                  effectiveScrollX =
                      (effectiveScrollX + focusX - view.getTextStartX()) * scale
                          - (focusX - view.getTextStartX());
                  view.scrollManager.scrollX = view.isRtl ? -effectiveScrollX : effectiveScrollX;
                  view.scrollManager.scrollY =
                      (view.scrollManager.scrollY + focusY) * effectiveScaleY - focusY;
                  if (view.wordWrapManager.isWordWrapEnabled) {
                    pendingZoomScrollAdjustGlobalLine = anchorGlobalLineAtFocus;
                    pendingZoomScrollAdjustFocusY = focusY;
                  }
                }

                lastFocusX = focusX;
                lastFocusY = focusY;

                view.scrollManager.clampScrollX();
                view.clampScrollY();
                view.invalidate();
                return true;
              }

              @Override
              public void onScaleEnd(ScaleGestureDetector detector) {
                mJustFinishedScale = true;
                isScaling = false;
                if (pinchVisualZoomActive) {
                  pinchVisualZoomActive = false;
                  pinchVisualScale = 1f;

                  float oldSize = view.getPaintTextSizePxForZoom();
                  float oldLineHeight = view.getPaintFontSpacingPxForZoom();
                  float targetSize = quantizeZoomSizePx(pinchTargetTextSizePx);
                  float focusX = pinchFocusX;
                  float focusY = pinchFocusY;
                  int anchorLine = pinchAnchorGlobalLineAtFocus;

                  if (Math.abs(targetSize - oldSize) > 0.1f) {
                    float scaleX = (oldSize > 0f) ? (targetSize / oldSize) : 1f;
                    view.applyZoomTextSizePx(targetSize, view.wordWrapManager.isWordWrapEnabled);
                    float newLineHeight = view.getPaintFontSpacingPxForZoom();
                    float effectiveScaleY =
                        (oldLineHeight > 0) ? newLineHeight / oldLineHeight : 1f;

                    float effectiveScrollX = view.getEffectiveScrollX();
                    effectiveScrollX =
                        (effectiveScrollX + focusX - view.getTextStartX()) * scaleX
                            - (focusX - view.getTextStartX());
                    view.scrollManager.scrollX = view.isRtl ? -effectiveScrollX : effectiveScrollX;
                    view.scrollManager.scrollY =
                        (view.scrollManager.scrollY + focusY) * effectiveScaleY - focusY;

                    if (view.wordWrapManager.isWordWrapEnabled && anchorLine >= 0) {
                      pendingZoomScrollAdjustGlobalLine = anchorLine;
                      pendingZoomScrollAdjustFocusY = focusY;
                    }
                    view.scrollManager.clampScrollX();
                    view.clampScrollY();
                    view.invalidate();
                  }
                }
                if (view.wordWrapManager.wrapPrefixRebuildPending) {
                  view.wordWrapManager.wrapPrefixRebuildPending = false;
                  view.wordWrapManager.scheduleWrapPrefixRebuildUpToWindow(view);
                }

                view.wordWrapManager.applyPendingWrapPrefixUpdateForZoom(view);

                if (view.wordWrapManager.isWordWrapEnabled && pendingZoomScrollAdjustGlobalLine != -1) {
                  final int targetGlobalLine = pendingZoomScrollAdjustGlobalLine;
                  final float targetFocusY = pendingZoomScrollAdjustFocusY;

                  pendingZoomScrollAdjustGlobalLine = -1;
                  pendingZoomScrollAdjustFocusY = -1f;

                  view.mainHandler.post(
                      new Runnable() {
                        @Override
                        public void run() {
                          if (view.wordWrapManager.wrapMetricsReady) {
                            int visualIndex = view.getVisualIndexForLineAndChar(targetGlobalLine, 0);
                            view.scrollManager.scrollY = visualIndex * view.lineHeight - targetFocusY;
                            view.clampScrollY();
                            view.invalidate();
                          } else {
                            view.mainHandler.postDelayed(this, 50);
                          }
                        }
                      });
                }
              }
            });
  }

  ScaleGestureDetector getScaleGestureDetector() {
    return scaleGestureDetector;
  }

  boolean isZoomGestureActive() {
    return isScaling
        || pinchVisualZoomActive
        || multiTouchActive
        || (scaleGestureDetector != null && scaleGestureDetector.isInProgress());
  }

  boolean shouldDrawDecorations() {
    return !(hideDecorationsWhileZooming && isZoomGestureActive());
  }

  boolean isPinchVisualZoomActive() {
    return pinchVisualZoomActive;
  }

  float getPinchVisualScale() {
    return pinchVisualScale;
  }

  float getPinchFocusX() {
    return pinchFocusX;
  }

  float getPinchFocusY() {
    return pinchFocusY;
  }

  boolean isScaling() {
    return isScaling;
  }

  boolean isJustFinishedScale() {
    return mJustFinishedScale;
  }

  void setJustFinishedScale(boolean finished) {
    mJustFinishedScale = finished;
  }

  boolean isMultiTouchActive() {
    return multiTouchActive;
  }

  boolean hadMultiTouch() {
    return hadMultiTouch;
  }

  void resetMultiTouchState() {
    multiTouchActive = false;
    hadMultiTouch = false;
  }

  void onPointerDown() {
    multiTouchActive = true;
    hadMultiTouch = true;
    mJustFinishedScale = true;
  }

  void onPointerUp(int remainingPointerCount) {
    if (remainingPointerCount <= 1) {
      multiTouchActive = false;
      mJustFinishedScale = true;
    }
  }

  void onScaleTouchEvent(MotionEvent event) {
    if (isZoomEnabled) {
      scaleGestureDetector.onTouchEvent(event);
    }
  }

  boolean isScaleInProgress() {
    return scaleGestureDetector != null && scaleGestureDetector.isInProgress();
  }

  public void setZoomEnabled(boolean enabled) {
    isZoomEnabled = enabled;
  }

  public void setDeferWordWrapReflowDuringZoom(boolean enabled) {
    deferWrapReflowDuringPinch = enabled;
  }

  public void setZoomTextSizeRange(float minSp, float maxSp) {
    float minPx = view.spToPxForZoom(minSp);
    float maxPx = view.spToPxForZoom(maxSp);
    if (minPx > maxPx) {
      float tmp = minPx;
      minPx = maxPx;
      maxPx = tmp;
    }
    minZoomTextSizePx = minPx;
    maxZoomTextSizePx = maxPx;
  }

  public void setZoomStepClamp(float maxStep) {
    zoomStepClampSp = Math.max(0f, maxStep);
  }

  public void setZoomFocusSmoothing(float alpha) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setZoomLockToInitialFocus(boolean enabled) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setZoomScaleSmoothing(float alpha) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setHideDecorationsWhileZooming(boolean enabled) {
    hideDecorationsWhileZooming = enabled;
  }

  private float quantizeZoomSizePx(float sizePx) {
    if (zoomStepClampSp <= 0f) return sizePx;
    float stepPx = view.spToPxForZoom(zoomStepClampSp);
    if (stepPx <= 0f) return sizePx;
    return Math.round(sizePx / stepPx) * stepPx;
  }
}
