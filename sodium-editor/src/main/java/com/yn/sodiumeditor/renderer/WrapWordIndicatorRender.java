package com.yn.sodiumeditor.renderer;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.WhitespaceGuideState;

public final class WrapWordIndicatorRender {

  //================================================================================
  // Constants
  //================================================================================

  public static final String INDICATOR_TEXT = "\u21A9"; // ↩
  public static final int DEFAULT_INDICATOR_COLOR = 0xFF9E9E9E;
  public static final float DEFAULT_TEXT_SCALE = 0.85f;

  //================================================================================
  // Paint & Properties
  //================================================================================

  public final Paint indicatorPaint;
  public float indicatorPadPx = 0f;
  public float indicatorWidth = 0f;
  public float indicatorTextScale = DEFAULT_TEXT_SCALE;
  public boolean isIndicatorEnabled = false;

  //================================================================================
  // Constructor
  //================================================================================

  public WrapWordIndicatorRender() {
    this.indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    this.indicatorPaint.setColor(DEFAULT_INDICATOR_COLOR);
    this.indicatorPaint.setAlpha(180);
    this.indicatorPaint.setTextAlign(Paint.Align.LEFT);
  }

  //================================================================================
  // Initialization
  //================================================================================

  public void init(Paint basePaint, float density) {
    indicatorPadPx = 4f * density;
    indicatorPaint.setTextSize(basePaint.getTextSize() * indicatorTextScale);
    indicatorPaint.setTypeface(basePaint.getTypeface());
    indicatorWidth = indicatorPaint.measureText(INDICATOR_TEXT);
  }

  public void updateTextSize(float baseTextSizePx, float targetPx) {
    if (baseTextSizePx > 0f && targetPx > 0f) {
      indicatorTextScale = targetPx / baseTextSizePx;
    } else {
      indicatorTextScale = DEFAULT_TEXT_SCALE;
    }
    indicatorPaint.setTextSize(baseTextSizePx * indicatorTextScale);
    indicatorWidth = indicatorPaint.measureText(INDICATOR_TEXT);
  }

  public void updatePaintForTextSize(float textSizePx, Paint basePaint) {
    indicatorPaint.setTextSize(textSizePx * indicatorTextScale);
    indicatorPaint.setTypeface(basePaint.getTypeface());
    indicatorWidth = indicatorPaint.measureText(INDICATOR_TEXT);
  }

  public void updateTypeface(Paint basePaint) {
    indicatorPaint.setTypeface(basePaint.getTypeface());
    indicatorWidth = indicatorPaint.measureText(INDICATOR_TEXT);
  }

  //================================================================================
  // Public API
  //================================================================================

  public void setEnabled(boolean enabled) {
    this.isIndicatorEnabled = enabled;
  }

  public void setColor(int color) {
    indicatorPaint.setColor(color);
  }

  public void setTextSize(SodiumEditor view, float sizeSp) {
    if (sizeSp <= 0f) return;
    float px = sizeSp * view.getResources().getDisplayMetrics().density;
    float base = view.paint.getTextSize();
    updateTextSize(base, px);
  }

  //================================================================================
  // Helper
  //================================================================================

  public int clampSegmentEndForIndicator(
      SodiumEditor view,
      String line,
      int segStart,
      int segEnd,
      int wrapWidthPx) {
    if (line == null || wrapWidthPx <= 0) return segEnd;

    float available = wrapWidthPx - indicatorWidth - indicatorPadPx;
    if (available <= 0f) return segStart;

    int end = Math.max(segStart, Math.min(segEnd, line.length()));
    while (end > segStart) {
      float width = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
          view, line, segStart, end, view.paint);
      if (width <= available) break;
      end--;
    }
    return end;
  }

  //================================================================================
  // Getters
  //================================================================================

  public boolean isEnabled() {
    return isIndicatorEnabled;
  }

  public float getIndicatorWidth() {
    return indicatorWidth;
  }

  public float getIndicatorPadding() {
    return indicatorPadPx;
  }

  public Paint getPaint() {
    return indicatorPaint;
  }
}
