package com.yn.sodiumeditor.config;

public final class WrapWordConfig {

  //================================================================================
  // Wrap Word Settings
  //================================================================================

  public boolean isWordWrapEnabled = false;
  public boolean isWordWrapIndicatorEnabled = false;

  // Wrap width calculation
  public boolean useFixedWrapWidth = false;
  public int fixedWrapWidthChars = 80;

  //================================================================================
  // Indicator Settings
  //================================================================================

  public int indicatorColor = 0xFF9E9E9E;
  public float indicatorTextSizeSp = 10f;
  public float indicatorPaddingPx = 4f;
  public float indicatorTextScale = 0.85f;
  public int indicatorAlpha = 180;

  //================================================================================
  // Indicator Text
  //================================================================================

  public static final String DEFAULT_INDICATOR_TEXT = "\u21A9"; // ↩
  public String indicatorText = DEFAULT_INDICATOR_TEXT;

  //================================================================================
  // Metrics State
  //================================================================================

  public boolean wrapMetricsReady = false;
  public int wrapMetricsWidth = -1;
  public int wrapPrefixValidUpToLine = -1;

  //================================================================================
  // Async Build State
  //================================================================================

  public boolean wrapMetricsBuilding = false;
  public boolean wrapSnapshotBuilding = false;
  public boolean wrapPrefixBuilding = false;

  //================================================================================
  // Methods
  //================================================================================

  public void setWordWrapEnabled(boolean enabled) {
    this.isWordWrapEnabled = enabled;
  }

  public boolean isWordWrapEnabled() {
    return isWordWrapEnabled;
  }

  public void setIndicatorEnabled(boolean enabled) {
    this.isWordWrapIndicatorEnabled = enabled;
  }

  public boolean isIndicatorEnabled() {
    return isWordWrapIndicatorEnabled;
  }

  public void setIndicatorColor(int color) {
    this.indicatorColor = color;
  }

  public int getIndicatorColor() {
    return indicatorColor;
  }

  public void setIndicatorTextSizeSp(float sizeSp) {
    this.indicatorTextSizeSp = sizeSp;
  }

  public float getIndicatorTextSizeSp() {
    return indicatorTextSizeSp;
  }

  public void setIndicatorPaddingPx(float paddingPx) {
    this.indicatorPaddingPx = paddingPx;
  }

  public float getIndicatorPaddingPx() {
    return indicatorPaddingPx;
  }

  public void setIndicatorTextScale(float scale) {
    this.indicatorTextScale = scale;
  }

  public float getIndicatorTextScale() {
    return indicatorTextScale;
  }

  public void setIndicatorAlpha(int alpha) {
    this.indicatorAlpha = alpha;
  }

  public int getIndicatorAlpha() {
    return indicatorAlpha;
  }

  public void setFixedWrapWidthChars(int chars) {
    this.fixedWrapWidthChars = chars;
    this.useFixedWrapWidth = chars > 0;
  }

  public int getFixedWrapWidthChars() {
    return fixedWrapWidthChars;
  }

  public boolean shouldUseFixedWrapWidth() {
    return useFixedWrapWidth && fixedWrapWidthChars > 0;
  }

  public void resetMetricsState() {
    wrapMetricsReady = false;
    wrapMetricsWidth = -1;
    wrapPrefixValidUpToLine = -1;
  }

  public void resetBuildState() {
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  public boolean isBuilding() {
    return wrapMetricsBuilding || wrapSnapshotBuilding || wrapPrefixBuilding;
  }
}
