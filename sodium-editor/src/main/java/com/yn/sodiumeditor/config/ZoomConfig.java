package com.yn.sodiumeditor.config;

public final class ZoomConfig {

  //================================================================================
  // Zoom Limits
  //================================================================================

  public static final float DEFAULT_MIN_TEXT_SIZE_SP = 8f;
  public static final float DEFAULT_MAX_TEXT_SIZE_SP = 56f;

  public float minZoomTextSizePx = DEFAULT_MIN_TEXT_SIZE_SP;
  public float maxZoomTextSizePx = DEFAULT_MAX_TEXT_SIZE_SP;

  //================================================================================
  // Zoom Settings
  //================================================================================

  public boolean isZoomEnabled = true;
  public boolean hideDecorationsWhileZooming = true;
  public boolean deferWrapReflowDuringPinch = true;

  //================================================================================
  // Zoom Step
  //================================================================================

  public float zoomStepClampSp = 0.2f;
  public boolean useZoomStepQuantization = true;

  //================================================================================
  // Pinch-to-Zoom State
  //================================================================================

  public boolean pinchVisualZoomActive = false;
  public float pinchVisualScale = 1f;
  public float pinchStartTextSizePx = 0f;
  public float pinchTargetTextSizePx = 0f;
  public float pinchFocusX = 0f;
  public float pinchFocusY = 0f;
  public int pinchAnchorGlobalLineAtFocus = -1;

  //================================================================================
  // Scaling State
  //================================================================================

  public boolean isScaling = false;
  public boolean mJustFinishedScale = false;

  //================================================================================
  // Multi-Touch State
  //================================================================================

  public boolean multiTouchActive = false;
  public boolean hadMultiTouch = false;

  //================================================================================
  // Pending Adjustments
  //================================================================================

  public int pendingZoomScrollAdjustGlobalLine = -1;
  public float pendingZoomScrollAdjustFocusY = -1f;

  //================================================================================
  // Methods
  //================================================================================

  public void setZoomEnabled(boolean enabled) {
    this.isZoomEnabled = enabled;
  }

  public boolean isZoomEnabled() {
    return isZoomEnabled;
  }

  public void setMinZoomTextSizePx(float minPx) {
    this.minZoomTextSizePx = minPx;
  }

  public float getMinZoomTextSizePx() {
    return minZoomTextSizePx;
  }

  public void setMaxZoomTextSizePx(float maxPx) {
    this.maxZoomTextSizePx = maxPx;
  }

  public float getMaxZoomTextSizePx() {
    return maxZoomTextSizePx;
  }

  public void setZoomTextSizeRangePx(float minPx, float maxPx) {
    if (minPx > maxPx) {
      float tmp = minPx;
      minPx = maxPx;
      maxPx = tmp;
    }
    this.minZoomTextSizePx = minPx;
    this.maxZoomTextSizePx = maxPx;
  }

  public void setZoomTextSizeRangeSp(float minSp, float maxSp) {
    this.minZoomTextSizePx = minSp; // Will be converted by caller
    this.maxZoomTextSizePx = maxSp;
  }

  public void setZoomStepClampSp(float stepSp) {
    this.zoomStepClampSp = Math.max(0f, stepSp);
  }

  public float getZoomStepClampSp() {
    return zoomStepClampSp;
  }

  public void setUseZoomStepQuantization(boolean use) {
    this.useZoomStepQuantization = use;
  }

  public boolean shouldUseZoomStepQuantization() {
    return useZoomStepQuantization && zoomStepClampSp > 0f;
  }

  public void setHideDecorationsWhileZooming(boolean enabled) {
    this.hideDecorationsWhileZooming = enabled;
  }

  public boolean shouldHideDecorationsWhileZooming() {
    return hideDecorationsWhileZooming;
  }

  public void setDeferWrapReflowDuringPinch(boolean enabled) {
    this.deferWrapReflowDuringPinch = enabled;
  }

  public boolean shouldDeferWrapReflowDuringPinch() {
    return deferWrapReflowDuringPinch;
  }

  public void resetPinchState() {
    pinchVisualZoomActive = false;
    pinchVisualScale = 1f;
    pinchStartTextSizePx = 0f;
    pinchTargetTextSizePx = 0f;
    pinchFocusX = 0f;
    pinchFocusY = 0f;
    pinchAnchorGlobalLineAtFocus = -1;
  }

  public void resetScalingState() {
    isScaling = false;
    mJustFinishedScale = false;
  }

  public void resetMultiTouchState() {
    multiTouchActive = false;
    hadMultiTouch = false;
  }

  public void resetPendingAdjustments() {
    pendingZoomScrollAdjustGlobalLine = -1;
    pendingZoomScrollAdjustFocusY = -1f;
  }

  public void resetAllState() {
    resetPinchState();
    resetScalingState();
    resetMultiTouchState();
    resetPendingAdjustments();
  }

  public boolean isZoomGestureActive() {
    return isScaling
        || pinchVisualZoomActive
        || multiTouchActive;
  }

  public boolean shouldDrawDecorations() {
    return !(hideDecorationsWhileZooming && isZoomGestureActive());
  }
}
