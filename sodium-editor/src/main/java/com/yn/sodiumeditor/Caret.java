package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

/**
 * Caret handles caret (cursor) rendering for SodiumEditor.
 * This includes:
 * - Caret blinking
 * - Caret rendering
 */
public class Caret {
  public final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Caret blink state
  public boolean isCursorVisible = true;
  public boolean caretBlinkEnabled = true;
  public long caretBlinkPeriodMs = 500;

  // Caret appearance
  public float caretWidth = 2f;
  public int caretColor = 0xFF000000;

  public final Runnable blinkRunnable;
  public final Handler mainHandler = new Handler(Looper.getMainLooper());

  private final SodiumEditor sodiumeditor;
  private final Cursor cursor;

  public Caret(SodiumEditor sodiumeditor, Cursor cursor) {
    this.sodiumeditor = sodiumeditor;
    this.cursor = cursor;

    this.blinkRunnable = new Runnable() {
      @Override
      public void run() {
        if (sodiumeditor.isFocused() && !sodiumeditor.hasSelection()) {
          isCursorVisible = !isCursorVisible;
          sodiumeditor.invalidateCursorArea();
          mainHandler.postDelayed(this, caretBlinkPeriodMs);
        }
      }
    };
  }

  /**
   * Start caret blink
   */
  public void startBlink() {
    stopBlink();
    if (caretBlinkEnabled) {
      isCursorVisible = true;
      mainHandler.postDelayed(blinkRunnable, caretBlinkPeriodMs);
    }
  }

  /**
   * Stop caret blink
   */
  public void stopBlink() {
    mainHandler.removeCallbacks(blinkRunnable);
    isCursorVisible = true;
  }

  /**
   * Reset caret blink
   */
  public void resetBlink() {
    stopBlink();
    startBlink();
  }

  /**
   * Draw caret on canvas
   */
  public void drawCaret(Canvas canvas) {
    if (!sodiumeditor.isFocused() || sodiumeditor.hasSelection()) {
      return;
    }

    if (!isCursorVisible && !sodiumeditor.cursorAnimation.cursorAnimRunning) {
      return;
    }

    float x = sodiumeditor.cursorAnimation.cursorDrawX;
    float y = sodiumeditor.cursorAnimation.cursorDrawY;
    float height = sodiumeditor.textRender.lineHeight;

    Paint paint = new Paint();
    paint.setColor(caretColor);
    paint.setStyle(Paint.Style.FILL);

    RectF caretRect = new RectF(x - caretWidth / 2, y, x + caretWidth / 2, y + height);
    canvas.drawRect(caretRect, paint);
  }

  /**
   * Get caret X position
   */
  public float getCaretX() {
    String lineText = sodiumeditor.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return sodiumeditor.getTextStartX();

    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    return sodiumeditor.getTextStartX() + sodiumeditor.measureTextWithVisualSpaces(lineText, 0, safeChar, sodiumeditor.textRender.paint) - sodiumeditor.scroll.scrollX;
  }

  /**
   * Get caret Y position
   */
  public float getCaretY() {
    int visualLine = cursor.cursorLine;
    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      visualLine = sodiumeditor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    }
    return (visualLine * sodiumeditor.textRender.lineHeight) - sodiumeditor.scroll.scrollY;
  }

  /**
   * Check if caret should be drawn
   */
  public boolean shouldDrawCaret() {
    return sodiumeditor.isFocused() && !sodiumeditor.hasSelection();
  }

  /**
   * Invalidate caret area
   */
  public void invalidateCaretArea() {
    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      sodiumeditor.invalidate();
      return;
    }
    sodiumeditor.invalidateLineGlobal(cursor.cursorLine);
  }

  // Getters and Setters

  public void setCaretBlinkEnabled(boolean enabled) {
    caretBlinkEnabled = enabled;
    if (enabled) {
      startBlink();
    } else {
      stopBlink();
    }
  }

  public void setCaretBlinkPeriodMs(long periodMs) {
    caretBlinkPeriodMs = Math.max(100, periodMs);
  }

  public void setCaretWidth(float width) {
    if (width <= 0f) return;
    caretWidth = width;
  }

  public void setCaretColor(int color) {
    caretColor = color;
    sodiumeditor.invalidate();
  }

  public boolean isBlinking() {
    return isCursorVisible;
  }

  public boolean isAnimating() {
    return sodiumeditor.cursorAnimation.cursorAnimRunning;
  }
}
