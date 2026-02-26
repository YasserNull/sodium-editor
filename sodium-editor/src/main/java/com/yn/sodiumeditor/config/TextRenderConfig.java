package com.yn.sodiumeditor.config;

public final class TextRenderConfig {

  //================================================================================
  // Text Rendering Settings
  //================================================================================

  public boolean isAntiAliasEnabled = true;
  public boolean isSubpixelTextEnabled = true;
  public boolean isLinearTextEnabled = false;
  public boolean isFakeBoldTextEnabled = false;

  //================================================================================
  // Text Measurement
  //================================================================================

  public boolean useTextBoundsForMeasurement = false;
  public boolean cacheTextMeasurements = true;
  public int textMeasurementCacheSize = 1000;

  //================================================================================
  // Line Rendering
  //================================================================================

  public float lineHeightMultiplier = 1.0f;
  public float lineSpacingPx = 0f;
  public boolean showLineBounds = false;

  //================================================================================
  // Character Rendering
  //================================================================================

  public boolean enableCharAnimations = true;
  public int charAnimationDurationMs = 150;
  public boolean enableTypewriterEffect = false;

  //================================================================================
  // Text Display
  //================================================================================

  public boolean showWhitespaceGuides = false;
  public boolean showIndentGuides = false;
  public boolean showLineNumbers = true;

  //================================================================================
  // Methods
  //================================================================================

  public void setAntiAliasEnabled(boolean enabled) {
    this.isAntiAliasEnabled = enabled;
  }

  public boolean isAntiAliasEnabled() {
    return isAntiAliasEnabled;
  }

  public void setSubpixelTextEnabled(boolean enabled) {
    this.isSubpixelTextEnabled = enabled;
  }

  public boolean isSubpixelTextEnabled() {
    return isSubpixelTextEnabled;
  }

  public void setLinearTextEnabled(boolean enabled) {
    this.isLinearTextEnabled = enabled;
  }

  public boolean isLinearTextEnabled() {
    return isLinearTextEnabled;
  }

  public void setFakeBoldTextEnabled(boolean enabled) {
    this.isFakeBoldTextEnabled = enabled;
  }

  public boolean isFakeBoldTextEnabled() {
    return isFakeBoldTextEnabled;
  }

  public void setLineHeightMultiplier(float multiplier) {
    this.lineHeightMultiplier = Math.max(0.5f, Math.min(2.0f, multiplier));
  }

  public float getLineHeightMultiplier() {
    return lineHeightMultiplier;
  }

  public void setLineSpacingPx(float spacingPx) {
    this.lineSpacingPx = Math.max(0f, spacingPx);
  }

  public float getLineSpacingPx() {
    return lineSpacingPx;
  }

  public void setCharAnimationsEnabled(boolean enabled) {
    this.enableCharAnimations = enabled;
  }

  public boolean isCharAnimationsEnabled() {
    return enableCharAnimations;
  }

  public void setCharAnimationDurationMs(int durationMs) {
    this.charAnimationDurationMs = Math.max(0, durationMs);
  }

  public int getCharAnimationDurationMs() {
    return charAnimationDurationMs;
  }

  public void setWhitespaceGuidesEnabled(boolean enabled) {
    this.showWhitespaceGuides = enabled;
  }

  public boolean isWhitespaceGuidesEnabled() {
    return showWhitespaceGuides;
  }

  public void setIndentGuidesEnabled(boolean enabled) {
    this.showIndentGuides = enabled;
  }

  public boolean isIndentGuidesEnabled() {
    return showIndentGuides;
  }

  public void setLineNumbersEnabled(boolean enabled) {
    this.showLineNumbers = enabled;
  }

  public boolean isLineNumbersEnabled() {
    return showLineNumbers;
  }
}
