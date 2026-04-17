package com.yn.sodiumeditor.core.wordwrap; 
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

/**
 * Manages the word wrap indicator for the SodiumEditor.
 * Handles drawing the indicator at the end of wrapped lines.
 */
public class WordWrapIndicator {

  private final SodiumEditor editor;
  private final Handler mainHandler;

  // Word wrap indicator state
  public boolean isWordWrapIndicatorEnabled = true;
  public static final String WORD_WRAP_INDICATOR_TEXT = "\u21A9"; // ↩
  public final Paint wordWrapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float wordWrapIndicatorPadPx = 0f;
  public float wordWrapIndicatorWidth = 0f;
  public float wordWrapIndicatorTextScale = 0.85f;

  public WordWrapIndicator(SodiumEditor editor) {
    this.editor = editor;
    this.mainHandler = new Handler(Looper.getMainLooper());
    initPaint();
  }

  /**
   * Initializes the indicator paint.
   */
  private void initPaint() {
    wordWrapIndicatorPaint.setColor(0xFF9E9E9E);
    wordWrapIndicatorPaint.setAlpha(180);
    wordWrapIndicatorPaint.setTextAlign(Paint.Align.LEFT);
    wordWrapIndicatorPaint.setTypeface(editor.textRender.paint.getTypeface());
    updateMetrics();
  }

  /**
   * Enables or disables the word wrap indicator.
   */
  public void setWordWrapIndicatorEnabled(boolean enabled) {
    if (this.isWordWrapIndicatorEnabled == enabled) return;
    this.isWordWrapIndicatorEnabled = enabled;
    editor.invalidate();
  }

  /**
   * Sets the color of the word wrap indicator.
   */
  public void setWordWrapIndicatorColor(int color) {
    wordWrapIndicatorPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the text size of the word wrap indicator.
   */
  public void setWordWrapIndicatorTextSize(float sizeSp) {
    if (sizeSp <= 0f) return;
    float px = editor.view.spToPx(sizeSp);
    float base = editor.textRender.paint.getTextSize();
    if (base > 0f) {
      wordWrapIndicatorTextScale = px / base;
    } else {
      wordWrapIndicatorTextScale = 0.85f;
    }
    wordWrapIndicatorPaint.setTextSize(base * wordWrapIndicatorTextScale);
    updateMetrics();
    editor.invalidate();
  }

  /**
   * Updates the indicator metrics based on current paint settings.
   */
  public void updateMetrics() {
    wordWrapIndicatorPaint.setTextSize(editor.textRender.paint.getTextSize() * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(editor.textRender.paint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
  }

  /**
   * Updates the indicator metrics when text size changes.
   */
  public void onTextSizeChanged(float sizePx) {
    wordWrapIndicatorPaint.setTextSize(sizePx * wordWrapIndicatorTextScale);
    updateMetrics();
  }

  /**
   * Updates the indicator typeface when typeface changes.
   */
  public void onTypefaceChanged() {
    wordWrapIndicatorPaint.setTypeface(editor.textRender.paint.getTypeface());
    updateMetrics();
  }

  /**
   * Gets the reserved width for the indicator (including padding).
   */
  public float getReservedWidth() {
    return wordWrapIndicatorWidth + (wordWrapIndicatorPadPx * 2f);
  }

  /**
   * Calculates the X position for the indicator.
   */
  public float getIndicatorX(float wrapWidthPx) {
    return wrapWidthPx - wordWrapIndicatorWidth - wordWrapIndicatorPadPx;
  }

  /**
   * Draws the word wrap indicator at the end of a line segment.
   */
  public void drawIndicator(Canvas canvas, float x, float y, float wrapWidthPx) {
    if (!isWordWrapIndicatorEnabled) return;
    float indicatorX = getIndicatorX(wrapWidthPx);
    canvas.drawText(WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrapIndicatorPaint);
  }

  /**
   * Checks if the indicator should be shown for a given segment.
   */
  public boolean shouldShowIndicator(String line, int segStart, int segEnd) {
    if (!isWordWrapIndicatorEnabled || line == null) return false;
    return segEnd < line.length();
  }

  /**
   * Clamps segment end to account for indicator space.
   */
  public int clampSegmentEndForIndicator(String line, int segStart, int segEnd, float wrapWidthPx) {
    if (!isWordWrapIndicatorEnabled || line == null) return segEnd;
    if (segEnd <= segStart) return segEnd;

    float reserved = getReservedWidth();
    float available = wrapWidthPx - reserved;
    if (available <= 0f) return segStart;

    float width = editor.textRender.measureTextWithVisualSpaces(line, segStart, segEnd, editor.textRender.paint);
    if (width <= available) return segEnd;

    int end = segEnd;
    while (end > segStart) {
      end--;
      float w = editor.textRender.measureTextWithVisualSpaces(line, segStart, end, editor.textRender.paint);
      if (w <= available) break;
    }
    return end;
  }
}
