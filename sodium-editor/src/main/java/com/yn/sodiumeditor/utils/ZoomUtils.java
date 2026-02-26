package com.yn.sodiumeditor.utils;

public final class ZoomUtils {

  private ZoomUtils() {
    // Utility class
  }

  //================================================================================
  // Zoom Quantization
  //================================================================================

  /**
   * Quantizes a zoom size to the nearest step.
   * @param sizePx The size in pixels
   * @param stepPx The step size in pixels
   * @return The quantized size
   */
  public static float quantizeZoomSizePx(float sizePx, float stepPx) {
    if (stepPx <= 0f) return sizePx;
    return Math.round(sizePx / stepPx) * stepPx;
  }

  /**
   * Clamps a zoom size within min/max bounds.
   * @param sizePx The size in pixels
   * @param minPx Minimum size in pixels
   * @param maxPx Maximum size in pixels
   * @return The clamped size
   */
  public static float clampZoomSizePx(float sizePx, float minPx, float maxPx) {
    return Math.max(minPx, Math.min(sizePx, maxPx));
  }

  /**
   * Quantizes and clamps a zoom size.
   * @param sizePx The size in pixels
   * @param minPx Minimum size in pixels
   * @param maxPx Maximum size in pixels
   * @param stepPx The step size in pixels
   * @return The quantized and clamped size
   */
  public static float quantizeAndClampZoomSizePx(float sizePx, float minPx, float maxPx, float stepPx) {
    float clamped = clampZoomSizePx(sizePx, minPx, maxPx);
    return quantizeZoomSizePx(clamped, stepPx);
  }

  //================================================================================
  // Scale Calculations
  //================================================================================

  /**
   * Calculates the scale factor between two sizes.
   * @param oldSize The old size
   * @param newSize The new size
   * @return The scale factor (newSize / oldSize)
   */
  public static float calculateScaleFactor(float oldSize, float newSize) {
    if (oldSize <= 0f) return 1f;
    return newSize / oldSize;
  }

  /**
   * Calculates the effective scroll position after scaling.
   * @param currentScroll The current scroll position
   * @param focusPoint The focus point of the scale gesture
   * @param textStartX The text start X position
   * @param scale The scale factor
   * @param isRtl Whether the text is RTL
   * @return The new scroll position
   */
  public static float calculateEffectiveScrollX(
      float currentScroll,
      float focusPoint,
      float textStartX,
      float scale,
      boolean isRtl) {
    float effectiveScroll = currentScroll;
    effectiveScroll =
        (effectiveScroll + focusPoint - textStartX) * scale - (focusPoint - textStartX);
    return isRtl ? -effectiveScroll : effectiveScroll;
  }

  /**
   * Calculates the effective scroll Y position after scaling.
   * @param currentScrollY The current scroll Y position
   * @param focusY The focus Y of the scale gesture
   * @param effectiveScaleY The effective Y scale factor
   * @return The new scroll Y position
   */
  public static float calculateEffectiveScrollY(float currentScrollY, float focusY, float effectiveScaleY) {
    return (currentScrollY + focusY) * effectiveScaleY - focusY;
  }

  //================================================================================
  // Rounding & Quantizing
  //================================================================================

  /**
   * Rounds a value to the nearest multiple of a step.
   * @param value The value to round
   * @param step The step size
   * @return The rounded value
   */
  public static float roundToStep(float value, float step) {
    if (step <= 0f) return value;
    return Math.round(value / step) * step;
  }

  /**
   * Quantizes a value with a minimum threshold.
   * @param value The value to quantize
   * @param threshold The minimum threshold for change
   * @return The quantized value
   */
  public static float quantizeWithThreshold(float value, float threshold) {
    if (threshold <= 0f) return value;
    return Math.round(value / threshold) * threshold;
  }

  //================================================================================
  // Validation
  //================================================================================

  /**
   * Validates zoom limits.
   * @param minPx Minimum size in pixels
   * @param maxPx Maximum size in pixels
   * @return True if the limits are valid
   */
  public static boolean isValidZoomRange(float minPx, float maxPx) {
    return minPx > 0f && maxPx > 0f && minPx <= maxPx;
  }

  /**
   * Checks if a size change is significant.
   * @param oldSize The old size
   * @param newSize The new size
   * @param threshold The threshold for significance
   * @return True if the change is significant
   */
  public static boolean isSignificantSizeChange(float oldSize, float newSize, float threshold) {
    return Math.abs(newSize - oldSize) > threshold;
  }
}
