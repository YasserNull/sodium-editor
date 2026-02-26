package com.yn.sodiumeditor.config;

public final class ViewRenderConfig {

  //================================================================================
  // View Rendering Settings
  //================================================================================

  public boolean isHardwareAccelerationEnabled = true;
  public boolean isSoftwareRenderingFallback = true;
  public int renderingPriority = 0; // 0 = balanced, 1 = quality, 2 = performance

  //================================================================================
  // Frame Rate & Performance
  //================================================================================

  public int targetFrameRate = 60;
  public long frameTimeoutMs = 16; // ~60fps
  public boolean useChoreographer = true;
  public boolean enableVsync = true;

  //================================================================================
  // Drawing Optimization
  //================================================================================

  public boolean enableLayerType = true;
  public int layerType = 2; // LAYER_TYPE_HARDWARE
  public boolean useDisplayList = true;
  public boolean enableDirtyRectTracking = true;

  //================================================================================
  // View Bounds & Clipping
  //================================================================================

  public boolean enableClipping = true;
  public float clipPaddingPx = 0f;
  public boolean clipChildren = true;
  public boolean clipToPadding = true;

  //================================================================================
  // Animation Settings
  //================================================================================

  public boolean animationsEnabled = true;
  public float animationScale = 1.0f;
  public long animationDurationMs = 250;
  public int animationInterpolator = 0; // 0 = decelerate, 1 = accelerate, 2 = linear

  //================================================================================
  // Background Rendering
  //================================================================================

  public boolean drawBackground = true;
  public int backgroundColor = 0xFFFFFFFF;
  public boolean useBitmapBackground = false;
  public String backgroundBitmapPath = null;

  //================================================================================
  // Loading State
  //================================================================================

  public boolean showLoadingIndicator = true;
  public int loadingIndicatorColor = 0xFF2196F3;
  public float loadingIndicatorSizePx = 48f;
  public int loadingIndicatorStyle = 0; // 0 = circle, 1 = bar

  //================================================================================
  // Debug Settings
  //================================================================================

  public boolean debugDrawBounds = false;
  public boolean debugDrawLayers = false;
  public boolean debugDrawInvalidation = false;
  public boolean showFpsCounter = false;

  //================================================================================
  // Methods
  //================================================================================

  public void setHardwareAccelerationEnabled(boolean enabled) {
    this.isHardwareAccelerationEnabled = enabled;
  }

  public boolean isHardwareAccelerationEnabled() {
    return isHardwareAccelerationEnabled;
  }

  public void setTargetFrameRate(int fps) {
    this.targetFrameRate = Math.max(30, Math.min(120, fps));
    this.frameTimeoutMs = 1000 / fps;
  }

  public int getTargetFrameRate() {
    return targetFrameRate;
  }

  public void setAnimationsEnabled(boolean enabled) {
    this.animationsEnabled = enabled;
  }

  public boolean isAnimationsEnabled() {
    return animationsEnabled;
  }

  public void setAnimationScale(float scale) {
    this.animationScale = Math.max(0f, Math.min(2.0f, scale));
  }

  public float getAnimationScale() {
    return animationScale;
  }

  public void setAnimationDurationMs(long durationMs) {
    this.animationDurationMs = Math.max(0, durationMs);
  }

  public long getAnimationDurationMs() {
    return animationDurationMs;
  }

  public void setBackgroundColor(int color) {
    this.backgroundColor = color;
  }

  public int getBackgroundColor() {
    return backgroundColor;
  }

  public void setBitmapBackgroundEnabled(boolean enabled, String path) {
    this.useBitmapBackground = enabled;
    this.backgroundBitmapPath = enabled ? path : null;
  }

  public boolean isBitmapBackgroundEnabled() {
    return useBitmapBackground;
  }

  public void setLoadingIndicatorEnabled(boolean enabled) {
    this.showLoadingIndicator = enabled;
  }

  public boolean isLoadingIndicatorEnabled() {
    return showLoadingIndicator;
  }

  public void setDebugModeEnabled(boolean enabled) {
    this.debugDrawBounds = enabled;
    this.debugDrawLayers = enabled;
    this.debugDrawInvalidation = enabled;
  }

  public boolean isDebugModeEnabled() {
    return debugDrawBounds || debugDrawLayers || debugDrawInvalidation;
  }
}
