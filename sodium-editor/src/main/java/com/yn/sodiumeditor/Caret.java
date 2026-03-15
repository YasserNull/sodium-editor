package com.yn.sodiumeditor;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;

/**
 * Caret handles caret (cursor) rendering and animation for SodiumEditor.
 * This includes:
 * - Caret blinking
 * - Caret animation (smooth movement)
 * - Caret rendering
 */
public class Caret {

  // Caret blink state
  public boolean isCursorVisible = true;
  public boolean caretBlinkEnabled = true;
  public long caretBlinkPeriodMs = 500;
  
  // Caret animation state
  public boolean isCursorAnimationEnabled = false;
  public float cursorAnimNormalTauMs = 80f;
  public float cursorAnimFastTauMs = 35f;
  public long cursorAnimFastThresholdMs = 85;
  public int lastCursorAnimLine = -1;
  public int lastCursorAnimChar = -1;
  public long lastCursorMoveUptime = 0L;
  public long cursorAnimLastFrameUptime = 0L;
  public float cursorAnimX = 0f;
  public float cursorAnimY = 0f;
  public float cursorAnimTargetX = 0f;
  public float cursorAnimTargetY = 0f;
  public float cursorDrawX = 0f;
  public float cursorDrawY = 0f;
  public boolean cursorAnimValid = false;
  public boolean cursorAnimRunning = false;
  
  // Caret appearance
  public float caretWidth = 2f;
  public int caretColor = 0xFF000000;
  
  private final Runnable blinkRunnable;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  @Nullable public ValueAnimator cursorAnimAnimator;
  
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
    
    if (!isCursorVisible && !cursorAnimRunning) {
      return;
    }
    
    updateCaretAnimation();
    
    float x = cursorAnimRunning ? cursorDrawX : getCaretX();
    float y = cursorAnimRunning ? cursorDrawY : getCaretY();
    float height = sodiumeditor.lineHeight;
    
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
    return sodiumeditor.getTextStartX() + sodiumeditor.measureTextWithVisualSpaces(lineText, 0, safeChar, sodiumeditor.paint) - sodiumeditor.scroll.scrollX;
  }

  /**
   * Get caret Y position
   */
  public float getCaretY() {
    int visualLine = cursor.cursorLine;
    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      visualLine = sodiumeditor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    }
    return (visualLine * sodiumeditor.lineHeight) - sodiumeditor.scroll.scrollY;
  }

  /**
   * Update caret animation
   */
  public void updateCaretAnimation() {
    if (!isCursorAnimationEnabled) {
      cursorAnimRunning = false;
      return;
    }
    
    long now = SystemClock.uptimeMillis();
    if (cursorAnimLastFrameUptime == 0L) {
      cursorAnimLastFrameUptime = now;
    }
    long dtMs = Math.max(1, now - cursorAnimLastFrameUptime);
    cursorAnimLastFrameUptime = now;
    
    if (cursorAnimRunning) {
      float tau = (now - lastCursorMoveUptime < cursorAnimFastThresholdMs) 
          ? cursorAnimFastTauMs 
          : cursorAnimNormalTauMs;
      float alpha = dtMs / tau;
      
      float dx = cursorAnimTargetX - cursorAnimX;
      float dy = cursorAnimTargetY - cursorAnimY;
      
      cursorAnimX += dx * alpha;
      cursorAnimY += dy * alpha;
      
      if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
        cursorAnimX = cursorAnimTargetX;
        cursorAnimY = cursorAnimTargetY;
        cursorAnimRunning = false;
      }
      
      cursorDrawX = cursorAnimX;
      cursorDrawY = cursorAnimY;
    } else {
      cursorDrawX = getCaretX();
      cursorDrawY = getCaretY();
    }
  }

  /**
   * Start caret animation to new position
   */
  public void animateToPosition(int newLine, int newChar) {
    if (!isCursorAnimationEnabled) {
      return;
    }
    
    long now = SystemClock.uptimeMillis();
    long delta = (lastCursorMoveUptime == 0L) ? Long.MAX_VALUE : (now - lastCursorMoveUptime);
    lastCursorMoveUptime = now;
    
    if (newLine == lastCursorAnimLine && newChar == lastCursorAnimChar) {
      return;
    }
    
    lastCursorAnimLine = newLine;
    lastCursorAnimChar = newChar;
    
    cursorAnimTargetX = getCaretX();
    cursorAnimTargetY = getCaretY();
    
    if (!cursorAnimRunning) {
      cursorAnimX = cursorDrawX;
      cursorAnimY = cursorDrawY;
      cursorAnimRunning = true;
      cursorAnimValid = true;
    }
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

  public void setCursorAnimationEnabled(boolean enabled) {
    isCursorAnimationEnabled = enabled;
  }

  public void setCaretWidth(float width) {
    if (width <= 0f) return;
    caretWidth = width;
  }

  public void setCaretColor(int color) {
    caretColor = color;
  }

  public boolean isBlinking() {
    return isCursorVisible;
  }

  public boolean isAnimating() {
    return cursorAnimRunning;
  }
}
