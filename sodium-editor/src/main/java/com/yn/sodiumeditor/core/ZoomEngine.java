package com.yn.sodiumeditor.core;

import android.os.Handler;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.ZoomUtils;

public final class ZoomEngine {

  //================================================================================
  // Constants
  //================================================================================

  private static final float SIZE_CHANGE_THRESHOLD = 0.1f;

  //================================================================================
  // Zoom Operations
  //================================================================================

  /**
   * Performs a zoom operation with the given scale factor.
   */
  public void performZoom(
      float scale,
      float focusX,
      float focusY,
      float minTextSizePx,
      float maxTextSizePx,
      float zoomStepClampSp,
      SodiumEditor view) {

    int anchorGlobalLineAtFocus = -1;
    if (view.wrapWordState.isWordWrapEnabled) {
      anchorGlobalLineAtFocus = view.getGlobalLineForY(view.scrollManager.scrollY + focusY);
    }

    float oldLineHeight = view.getPaintFontSpacingPxForZoom();
    float currentSize = view.getPaintTextSizePxForZoom();
    float newSize = currentSize * scale;

    // Clamp and quantize
    newSize = ZoomUtils.clampZoomSizePx(newSize, minTextSizePx, maxTextSizePx);
    if (zoomStepClampSp > 0f) {
      float stepPx = view.spToPxForZoom(zoomStepClampSp);
      newSize = ZoomUtils.quantizeZoomSizePx(newSize, stepPx);
    }

    if (ZoomUtils.isSignificantSizeChange(newSize, currentSize, SIZE_CHANGE_THRESHOLD)) {
      view.applyZoomTextSizePx(newSize);

      float newLineHeight = view.getPaintFontSpacingPxForZoom();
      float effectiveScaleY = ZoomUtils.calculateScaleFactor(oldLineHeight, newLineHeight);

      float effectiveScrollX = view.getEffectiveScrollX();
      effectiveScrollX = ZoomUtils.calculateEffectiveScrollX(
          effectiveScrollX,
          focusX,
          view.getTextStartX(),
          scale,
          view.isRtl);
      view.scrollManager.scrollX = view.isRtl ? -effectiveScrollX : effectiveScrollX;

      view.scrollManager.scrollY = ZoomUtils.calculateEffectiveScrollY(
          view.scrollManager.scrollY,
          focusY,
          effectiveScaleY);

      if (view.wrapWordState.isWordWrapEnabled) {
        view.zoomConfig.pendingZoomScrollAdjustGlobalLine = anchorGlobalLineAtFocus;
        view.zoomConfig.pendingZoomScrollAdjustFocusY = focusY;
      }
    }
  }

  /**
   * Applies the pinch visual zoom result.
   */
  public void applyPinchVisualZoom(
      float targetSizePx,
      float focusX,
      float focusY,
      int anchorLine,
      SodiumEditor view) {

    float oldSize = view.getPaintTextSizePxForZoom();
    float oldLineHeight = view.getPaintFontSpacingPxForZoom();

    float stepPx = view.spToPxForZoom(0.2f); // Default step
    float targetSize = ZoomUtils.quantizeZoomSizePx(targetSizePx, stepPx);
    targetSize = ZoomUtils.clampZoomSizePx(targetSize, view.zoomConfig.minZoomTextSizePx, view.zoomConfig.maxZoomTextSizePx);

    if (ZoomUtils.isSignificantSizeChange(targetSize, oldSize, SIZE_CHANGE_THRESHOLD)) {
      float scaleX = ZoomUtils.calculateScaleFactor(oldSize, targetSize);

      view.applyZoomTextSizePx(targetSize);

      float newLineHeight = view.getPaintFontSpacingPxForZoom();
      float effectiveScaleY = ZoomUtils.calculateScaleFactor(oldLineHeight, newLineHeight);

      float effectiveScrollX = view.getEffectiveScrollX();
      effectiveScrollX = ZoomUtils.calculateEffectiveScrollX(
          effectiveScrollX,
          focusX,
          view.getTextStartX(),
          scaleX,
          view.isRtl);
      view.scrollManager.scrollX = view.isRtl ? -effectiveScrollX : effectiveScrollX;

      view.scrollManager.scrollY = ZoomUtils.calculateEffectiveScrollY(
          view.scrollManager.scrollY,
          focusY,
          effectiveScaleY);

      if (view.wrapWordState.isWordWrapEnabled && anchorLine >= 0) {
        view.zoomConfig.pendingZoomScrollAdjustGlobalLine = anchorLine;
        view.zoomConfig.pendingZoomScrollAdjustFocusY = focusY;
      }

      view.scrollManager.clampScrollX();
      view.clampScrollY();
      view.invalidate();
    }
  }

  /**
   * Applies pending scroll adjustment after wrap metrics are ready.
   */
  public void applyPendingScrollAdjustment(
      int targetGlobalLine,
      float targetFocusY,
      SodiumEditor view) {

    final int targetLine = targetGlobalLine;
    final float focusY = targetFocusY;

    view.mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (view.wrapWordMetrics.wrapMetricsReady) {
              int visualIndex = view.getVisualIndexForLineAndChar(targetLine, 0);
              view.scrollManager.scrollY = visualIndex * view.lineHeight - focusY;
              view.clampScrollY();
              view.invalidate();
            } else {
              view.mainHandler.postDelayed(this, 50);
            }
          }
        });
  }

  //================================================================================
  // Zoom Range
  //================================================================================

  /**
   * Sets the zoom text size range.
   */
  public void setZoomTextSizeRange(
      float minSp,
      float maxSp,
      SodiumEditor view) {

    float minPx = view.spToPxForZoom(minSp);
    float maxPx = view.spToPxForZoom(maxSp);

    if (minPx > maxPx) {
      float tmp = minPx;
      minPx = maxPx;
      maxPx = tmp;
    }

    view.zoomConfig.minZoomTextSizePx = minPx;
    view.zoomConfig.maxZoomTextSizePx = maxPx;
  }

  /**
   * Validates and clamps the zoom range.
   */
  public boolean isValidZoomRange(float minPx, float maxPx) {
    return ZoomUtils.isValidZoomRange(minPx, maxPx);
  }

  //================================================================================
  // Scroll Calculations
  //================================================================================

  /**
   * Calculates the scroll adjustment needed to keep a line visible after zoom.
   */
  public float calculateScrollAdjustmentForLine(
      int globalLine,
      float focusY,
      float oldLineHeight,
      float newLineHeight) {

    float effectiveScaleY = ZoomUtils.calculateScaleFactor(oldLineHeight, newLineHeight);
    return ZoomUtils.calculateEffectiveScrollY(0, focusY, effectiveScaleY);
  }

  /**
   * Calculates the visual index for a line after zoom.
   */
  public int calculateVisualIndexForLine(int globalLine, SodiumEditor view) {
    if (view.wrapWordState.isWordWrapEnabled && view.wrapWordMetrics.wrapMetricsReady) {
      return view.getVisualIndexForLineAndChar(globalLine, 0);
    }
    return globalLine;
  }

  //================================================================================
  // Text Size Dependent Metrics
  //================================================================================

  /**
   * Updates all text size dependent metrics across the editor.
   * This includes handle radius, cursor width, bracket stroke widths, etc.
   */
  public void updateTextSizeDependentMetrics(SodiumEditor view) {
    float sizePx = view.editorConfig.paint.getTextSize();
    float baseTextSizePx = view.editorConfig.visualConfig.baseCursorTextSizePx;

    // Update handle renderer metrics
    view.handleRenderer.setHandleRadius(
        Math.max(
            4f,
            scaleByTextSize(
                view.handleRenderer.getBaseHandleRadiusPx(),
                baseTextSizePx,
                sizePx)));
    view.handleRenderer.setCursorWidth(
        Math.max(1f, scaleByTextSize(view.handleRenderer.getBaseCursorWidthPx(), baseTextSizePx, sizePx)));

    // Update bracket match renderer stroke width
    view.bracketMatchRenderer.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(
            view.bracketMatchRenderer.getBaseStrokeWidth(),
            view.bracketMatchRenderer.getBaseTextSizePx(),
            sizePx)));

    // Update bracket guide renderer stroke width
    view.bracketGuideRenderer.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(
            view.bracketGuideRenderer.getBaseStrokeWidth(),
            view.bracketGuideRenderer.getBaseTextSizePx(),
            sizePx)));

    // Update indent guide renderer for text size
    view.indentGuideRenderer.updateForTextSize(sizePx);
  }

  /**
   * Scales a value proportionally based on text size change.
   */
  private float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }
}
