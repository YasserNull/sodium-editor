package com.yn.sodiumeditor.input;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.config.ZoomConfig;
import com.yn.sodiumeditor.core.ZoomEngine;
import com.yn.sodiumeditor.renderer.ZoomPreviewRender;

public final class ZoomGestureHandler {

  //================================================================================
  // Dependencies
  //================================================================================

  private final SodiumEditorView view;
  private final ZoomConfig config;
  private final ZoomEngine engine;
  private final ZoomPreviewRender previewRender;
  private final ScaleGestureDetector scaleGestureDetector;

  //================================================================================
  // State
  //================================================================================

  private float lastFocusX;
  private float lastFocusY;

  //================================================================================
  // Constructor
  //================================================================================

  public ZoomGestureHandler(
      SodiumEditorView view,
      ZoomConfig config,
      ZoomEngine engine,
      ZoomPreviewRender previewRender,
      Context ctx) {
    this.view = view;
    this.config = config;
    this.engine = engine;
    this.previewRender = previewRender;

    this.scaleGestureDetector =
        new ScaleGestureDetector(
            ctx,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector detector) {
                return ZoomGestureHandler.this.onScaleBegin(detector);
              }

              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                return ZoomGestureHandler.this.onScale(detector);
              }

              @Override
              public void onScaleEnd(ScaleGestureDetector detector) {
                ZoomGestureHandler.this.onScaleEnd(detector);
              }
            });
  }

  //================================================================================
  // Scale Gesture Callbacks
  //================================================================================

  private boolean onScaleBegin(ScaleGestureDetector detector) {
    config.mJustFinishedScale = false;
    config.isScaling = true;
    lastFocusX = detector.getFocusX();
    lastFocusY = detector.getFocusY();

    view.abortScrollAnimationForZoom();

    if (config.isZoomEnabled && config.deferWrapReflowDuringPinch) {
      previewRender.startPinchVisualZoom(
          detector.getFocusX(),
          detector.getFocusY(),
          view.getPaintTextSizePxForZoom(),
          view.getGlobalLineForY(view.scrollManager.scrollY + detector.getFocusY()));
    } else {
      previewRender.cancelPinchVisualZoom();
    }

    return true;
  }

  private boolean onScale(ScaleGestureDetector detector) {
    if (!config.isZoomEnabled) {
      return false;
    }

    float scale = detector.getScaleFactor();
    float focusX = detector.getFocusX();
    float focusY = detector.getFocusY();

    if (previewRender.isPinchVisualZoomActive()) {
      previewRender.updatePinchVisualZoom(
          scale,
          focusX,
          focusY,
          view.getGlobalLineForY(view.scrollManager.scrollY + focusY),
          config.minZoomTextSizePx,
          config.maxZoomTextSizePx,
          config.zoomStepClampSp,
          view);

      config.pinchFocusX = previewRender.getPinchFocusX();
      config.pinchFocusY = previewRender.getPinchFocusY();
      config.pinchVisualScale = previewRender.getPinchVisualScale();
      config.pinchTargetTextSizePx = previewRender.getPinchTargetTextSizePx();
      config.pinchAnchorGlobalLineAtFocus = previewRender.getPinchAnchorGlobalLine();

      view.invalidate();
      return true;
    }

    // Perform real-time zoom
    engine.performZoom(
        scale,
        focusX,
        focusY,
        config.minZoomTextSizePx,
        config.maxZoomTextSizePx,
        config.zoomStepClampSp,
        view);

    lastFocusX = focusX;
    lastFocusY = focusY;

    view.scrollManager.clampScrollX();
    view.clampScrollY();
    view.invalidate();
    return true;
  }

  private void onScaleEnd(ScaleGestureDetector detector) {
    config.mJustFinishedScale = true;
    config.isScaling = false;

    if (previewRender.isPinchVisualZoomActive()) {
      engine.applyPinchVisualZoom(
          previewRender.getPinchTargetTextSizePx(),
          previewRender.getPinchFocusX(),
          previewRender.getPinchFocusY(),
          previewRender.getPinchAnchorGlobalLine(),
          view);

      previewRender.cancelPinchVisualZoom();
    }

    if (config.pendingZoomScrollAdjustGlobalLine != -1) {
      engine.applyPendingScrollAdjustment(
          config.pendingZoomScrollAdjustGlobalLine,
          config.pendingZoomScrollAdjustFocusY,
          view);

      config.pendingZoomScrollAdjustGlobalLine = -1;
      config.pendingZoomScrollAdjustFocusY = -1f;
    }
  }

  //================================================================================
  // Public API
  //================================================================================

  public boolean isMultiTouchActive() {
    return config.multiTouchActive;
  }

  public boolean hadMultiTouch() {
    return config.hadMultiTouch;
  }

  public ScaleGestureDetector getScaleGestureDetector() {
    return scaleGestureDetector;
  }

  public boolean onTouchEvent(MotionEvent event) {
    if (config.isZoomEnabled) {
      return scaleGestureDetector.onTouchEvent(event);
    }
    return false;
  }

  public boolean isZoomGestureActive() {
    return config.isZoomGestureActive()
        || (scaleGestureDetector != null && scaleGestureDetector.isInProgress());
  }

  public boolean isScaleInProgress() {
    return scaleGestureDetector != null && scaleGestureDetector.isInProgress();
  }

  public void resetMultiTouchState() {
    config.resetMultiTouchState();
  }

  public void onPointerDown() {
    config.multiTouchActive = true;
    config.hadMultiTouch = true;
    config.mJustFinishedScale = true;
  }

  public void onPointerUp(int remainingPointerCount) {
    if (remainingPointerCount <= 1) {
      config.multiTouchActive = false;
      config.mJustFinishedScale = true;
    }
  }

  //================================================================================
  // Getters
  //================================================================================

  public boolean isZoomEnabled() {
    return config.isZoomEnabled;
  }

  public void setZoomEnabled(boolean enabled) {
    config.isZoomEnabled = enabled;
  }

  public boolean shouldDrawDecorations() {
    return config.shouldDrawDecorations();
  }

  public boolean isScaling() {
    return config.isScaling;
  }

  public boolean isJustFinishedScale() {
    return config.mJustFinishedScale;
  }

  public void setJustFinishedScale(boolean finished) {
    config.mJustFinishedScale = finished;
  }

  public boolean isPinchVisualZoomActive() {
    return previewRender.isPinchVisualZoomActive();
  }

  public float getPinchVisualScale() {
    return previewRender.getPinchVisualScale();
  }

  public float getPinchFocusX() {
    return previewRender.getPinchFocusX();
  }

  public float getPinchFocusY() {
    return previewRender.getPinchFocusY();
  }
}
