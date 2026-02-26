package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.utils.ZoomUtils;

public final class ZoomPreviewRender {

  //================================================================================
  // Pinch-to-Zoom State
  //================================================================================

  private boolean pinchVisualZoomActive = false;
  private float pinchVisualScale = 1f;
  private float pinchStartTextSizePx = 0f;
  private float pinchTargetTextSizePx = 0f;
  private float pinchFocusX = 0f;
  private float pinchFocusY = 0f;
  private int pinchAnchorGlobalLine = -1;

  //================================================================================
  // Canvas Scaling
  //================================================================================

  private final Matrix pinchScaleMatrix = new Matrix();
  private boolean useMatrixScaling = false;

  //================================================================================
  // Pinch Visual Zoom
  //================================================================================

  /**
   * Starts pinch visual zoom mode.
   */
  public void startPinchVisualZoom(
      float focusX,
      float focusY,
      float startTextSizePx,
      int anchorGlobalLine) {

    pinchVisualZoomActive = true;
    pinchVisualScale = 1f;
    pinchStartTextSizePx = startTextSizePx;
    pinchTargetTextSizePx = startTextSizePx;
    pinchFocusX = focusX;
    pinchFocusY = focusY;
    pinchAnchorGlobalLine = anchorGlobalLine;
  }

  /**
   * Updates pinch visual zoom with new scale.
   */
  public void updatePinchVisualZoom(
      float scale,
      float focusX,
      float focusY,
      int anchorGlobalLine,
      float minTextSizePx,
      float maxTextSizePx,
      float zoomStepClampSp,
      SodiumEditorView view) {

    pinchFocusX = focusX;
    pinchFocusY = focusY;
    pinchAnchorGlobalLine = anchorGlobalLine;

    pinchVisualScale *= scale;
    float targetSize = pinchStartTextSizePx * pinchVisualScale;
    targetSize = ZoomUtils.clampZoomSizePx(targetSize, minTextSizePx, maxTextSizePx);

    if (zoomStepClampSp > 0f) {
      float stepPx = view.spToPxForZoom(zoomStepClampSp);
      targetSize = ZoomUtils.quantizeZoomSizePx(targetSize, stepPx);
    }

    pinchTargetTextSizePx = targetSize;
    pinchVisualScale =
        (pinchStartTextSizePx > 0f) ? (pinchTargetTextSizePx / pinchStartTextSizePx) : 1f;
  }

  /**
   * Cancels pinch visual zoom mode.
   */
  public void cancelPinchVisualZoom() {
    pinchVisualZoomActive = false;
    pinchVisualScale = 1f;
    pinchStartTextSizePx = 0f;
    pinchTargetTextSizePx = 0f;
    pinchFocusX = 0f;
    pinchFocusY = 0f;
    pinchAnchorGlobalLine = -1;
  }

  //================================================================================
  // Canvas Drawing
  //================================================================================

  /**
   * Applies pinch scale to canvas for preview rendering.
   */
  public void applyPinchScaleToCanvas(Canvas canvas, float pivotX, float pivotY) {
    if (pinchVisualZoomActive && pinchVisualScale != 1f) {
      pinchScaleMatrix.reset();
      pinchScaleMatrix.postScale(pinchVisualScale, pinchVisualScale, pivotX, pivotY);
      canvas.concat(pinchScaleMatrix);
    }
  }

  /**
   * Draws zoom preview overlay.
   */
  public void drawZoomPreview(Canvas canvas, SodiumEditorView view) {
    if (!pinchVisualZoomActive) return;

    // Optional: Draw zoom indicator or preview overlay
    // This can be extended to show a zoom level indicator
  }

  //================================================================================
  // Getters
  //================================================================================

  public boolean isPinchVisualZoomActive() {
    return pinchVisualZoomActive;
  }

  public float getPinchVisualScale() {
    return pinchVisualScale;
  }

  public float getPinchStartTextSizePx() {
    return pinchStartTextSizePx;
  }

  public float getPinchTargetTextSizePx() {
    return pinchTargetTextSizePx;
  }

  public float getPinchFocusX() {
    return pinchFocusX;
  }

  public float getPinchFocusY() {
    return pinchFocusY;
  }

  public int getPinchAnchorGlobalLine() {
    return pinchAnchorGlobalLine;
  }

  //================================================================================
  // Zoom Indicator
  //================================================================================

  /**
   * Calculates zoom level as a percentage.
   */
  public float getZoomLevelPercent() {
    if (pinchStartTextSizePx <= 0f) return 100f;
    return (pinchTargetTextSizePx / pinchStartTextSizePx) * 100f;
  }

  /**
   * Gets the current zoom display text.
   */
  public String getZoomDisplayText() {
    return String.format("%.0f%%", getZoomLevelPercent());
  }

  //================================================================================
  // Configuration
  //================================================================================

  /**
   * Enables or disables matrix-based scaling.
   */
  public void setUseMatrixScaling(boolean use) {
    this.useMatrixScaling = use;
  }

  public boolean isUsingMatrixScaling() {
    return useMatrixScaling;
  }
}
