package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Manages character fade animations for SodiumEditor.
 * Handles animations for typed characters and deleted characters.
 */
public class CharAnimation {

  // Animation configuration
  public boolean isCharAnimationEnabled = true;
  public int charAnimationDurationMs = 200;
  public int charAnimFastDurationMs = 60;
  public long charAnimFastThresholdMs = 80;

  // Typed character animation state
  public long lastCharAnimUptime = 0L;
  public int charAnimLine = -1;
  public int charAnimStartChar = 0;
  public int charAnimEndChar = 0;
  public float charAnimAlpha = 0f;
  @Nullable public ValueAnimator charAnimAnimator;
  public final Paint charAnimTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Deleted character animation state
  public int delAnimLine = -1;
  public int delAnimAtChar = 0;
  @Nullable public String delAnimText;
  @Nullable public Paint delAnimPaint;
  public float delAnimAlpha = 0f;
  @Nullable public ValueAnimator delAnimAnimator;

  // Reference to parent editor
  private final SodiumEditor editor;

  public CharAnimation(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enable or disable character animations.
   * @param enabled true to enable, false to disable
   * @param durationMs Animation duration in milliseconds
   */
  public void setCharAnimation(boolean enabled, int durationMs) {
    isCharAnimationEnabled = enabled;
    if (durationMs > 0) charAnimationDurationMs = durationMs;
    if (!enabled) {
      cancelAllAnimations();
      editor.invalidate();
    }
  }

  /**
   * Check if character animations are enabled.
   * @return true if enabled
   */
  public boolean isCharAnimationEnabled() {
    return isCharAnimationEnabled;
  }

  /**
   * Set animation duration parameters.
   * @param normalDurationMs Duration for normal typing (ms)
   * @param fastDurationMs Duration for fast typing (ms)
   * @param fastThresholdMs Threshold for detecting fast typing (ms)
   */
  public void setAnimationParameters(int normalDurationMs, int fastDurationMs, long fastThresholdMs) {
    this.charAnimationDurationMs = normalDurationMs;
    this.charAnimFastDurationMs = fastDurationMs;
    this.charAnimFastThresholdMs = fastThresholdMs;
  }

  /**
   * Start fade-in animation for typed characters.
   * @param committedText The text that was committed
   */
  public void startCharAnimationFromText(CharSequence committedText) {
    if (!isCharAnimationEnabled) return;
    if (committedText == null) return;

    final int targetLine = editor.cursor.cursorLine;
    final int targetEndChar = editor.cursor.cursorChar;

    // Extract the last non-whitespace code point from the committed text
    int extractedCodePoint = -1;
    int extractedCharCount = 0;
    int i = committedText.length();
    while (i > 0) {
      int codePoint = Character.codePointBefore(committedText, i);
      i -= Character.charCount(codePoint);

      if (codePoint == '\n' || codePoint == '\r') continue;
      if (Character.isWhitespace(codePoint)) continue;

      extractedCodePoint = codePoint;
      extractedCharCount = Character.charCount(codePoint);
      break;
    }
    if (extractedCodePoint == -1) return;

    final int finalCharCount = extractedCharCount;
    long now = SystemClock.uptimeMillis();
    long delta = (lastCharAnimUptime == 0L) ? Long.MAX_VALUE : (now - lastCharAnimUptime);
    lastCharAnimUptime = now;
    
    final int animDuration =
        (delta <= charAnimFastThresholdMs)
            ? Math.max(1, Math.min(charAnimationDurationMs, charAnimFastDurationMs))
            : Math.max(1, charAnimationDurationMs);

    Runnable start =
        () -> {
          // Cancel any existing animations
          if (delAnimAnimator != null) delAnimAnimator.cancel();
          if (charAnimAnimator != null) charAnimAnimator.cancel();
          
          charAnimLine = targetLine;
          charAnimEndChar = Math.max(0, targetEndChar);
          charAnimStartChar = Math.max(0, charAnimEndChar - finalCharCount);
          charAnimAlpha = 0.2f;
          editor.invalidateLineGlobal(charAnimLine);

          charAnimAnimator = ValueAnimator.ofFloat(0.2f, 1f);
          charAnimAnimator.setDuration(animDuration);
          charAnimAnimator.addUpdateListener(
              a -> {
                Object v = a.getAnimatedValue();
                charAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                editor.invalidateLineGlobal(charAnimLine);
              });
          charAnimAnimator.addListener(
              new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                  charAnimAlpha = 0f;
                  charAnimLine = -1;
                  editor.invalidate();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                  charAnimAlpha = 0f;
                  charAnimLine = -1;
                  editor.invalidate();
                }
              });
          charAnimAnimator.start();
        };
    
    if (Looper.myLooper() == Looper.getMainLooper()) {
      start.run();
    } else {
      editor.post(start);
    }
  }

  /**
   * Start fade-out animation for deleted characters.
   * @param targetLine The line where deletion occurred
   * @param atChar The character position where deletion occurred
   * @param removedText The text that was removed
   * @param paintToUse Optional paint to use for rendering
   */
  public void startDeleteAnimation(
      int targetLine, int atChar, @Nullable String removedText, @Nullable Paint paintToUse) {
    if (!isCharAnimationEnabled) return;
    if (removedText == null || removedText.isEmpty()) return;

    final int lineForAnim = targetLine;
    final int atForAnim = Math.max(0, atChar);
    final String textForAnim = removedText;
    final Paint p = (paintToUse != null) ? paintToUse : editor.textRender.paint;
    
    long now = SystemClock.uptimeMillis();
    long delta = (lastCharAnimUptime == 0L) ? Long.MAX_VALUE : (now - lastCharAnimUptime);
    lastCharAnimUptime = now;
    
    final int animDuration =
        (delta <= charAnimFastThresholdMs)
            ? Math.max(1, Math.min(charAnimationDurationMs, charAnimFastDurationMs))
            : Math.max(1, charAnimationDurationMs);

    Runnable start =
        () -> {
          // Cancel any existing animations
          if (charAnimAnimator != null) charAnimAnimator.cancel();
          if (delAnimAnimator != null) delAnimAnimator.cancel();
          
          delAnimLine = lineForAnim;
          delAnimAtChar = atForAnim;
          delAnimText = textForAnim;
          delAnimPaint = p;
          delAnimAlpha = 1f;
          editor.invalidateLineGlobal(lineForAnim);

          delAnimAnimator = ValueAnimator.ofFloat(1f, 0f);
          delAnimAnimator.setDuration(animDuration);
          delAnimAnimator.addUpdateListener(
              a -> {
                Object v = a.getAnimatedValue();
                delAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                editor.invalidateLineGlobal(lineForAnim);
              });
          delAnimAnimator.addListener(
              new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                  delAnimAlpha = 0f;
                  delAnimLine = -1;
                  delAnimAtChar = 0;
                  delAnimText = null;
                  delAnimPaint = null;
                  editor.invalidate();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                  delAnimAlpha = 0f;
                  delAnimLine = -1;
                  delAnimAtChar = 0;
                  delAnimText = null;
                  delAnimPaint = null;
                  editor.invalidate();
                }
              });
          delAnimAnimator.start();
        };

    if (Looper.myLooper() == Looper.getMainLooper()) {
      start.run();
    } else {
      editor.post(start);
    }
  }

  /**
   * Cancel all character animations.
   */
  public void cancelAllAnimations() {
    if (charAnimAnimator != null) {
      charAnimAnimator.cancel();
      charAnimAnimator = null;
    }
    charAnimAlpha = 0f;
    charAnimLine = -1;
    charAnimStartChar = 0;
    charAnimEndChar = 0;

    if (delAnimAnimator != null) {
      delAnimAnimator.cancel();
      delAnimAnimator = null;
    }
    delAnimAlpha = 0f;
    delAnimLine = -1;
    delAnimAtChar = 0;
    delAnimText = null;
    delAnimPaint = null;
  }

  /**
   * Cancel the typed character animation.
   */
  public void cancelCharAnimation() {
    if (charAnimAnimator != null) {
      charAnimAnimator.cancel();
      charAnimAnimator = null;
    }
    charAnimAlpha = 0f;
    charAnimLine = -1;
    charAnimStartChar = 0;
    charAnimEndChar = 0;
  }

  /**
   * Cancel the delete animation.
   */
  public void cancelDeleteAnimation() {
    if (delAnimAnimator != null) {
      delAnimAnimator.cancel();
      delAnimAnimator = null;
    }
    delAnimAlpha = 0f;
    delAnimLine = -1;
    delAnimAtChar = 0;
    delAnimText = null;
    delAnimPaint = null;
  }

  /**
   * Check if a typed character animation is currently running.
   * @return true if running
   */
  public boolean isCharAnimationRunning() {
    return charAnimAnimator != null && charAnimAlpha > 0f;
  }

  /**
   * Check if a delete animation is currently running.
   * @return true if running
   */
  public boolean isDeleteAnimationRunning() {
    return delAnimAnimator != null && delAnimAlpha > 0f;
  }

  /**
   * Get the alpha value for the typed character animation.
   * @return Alpha value (0.0 to 1.0)
   */
  public float getCharAnimAlpha() {
    return charAnimAlpha;
  }

  /**
   * Get the alpha value for the delete animation.
   * @return Alpha value (0.0 to 1.0)
   */
  public float getDelAnimAlpha() {
    return delAnimAlpha;
  }

  /**
   * Get the line number for the typed character animation.
   * @return Line number, or -1 if no animation
   */
  public int getCharAnimLine() {
    return charAnimLine;
  }

  /**
   * Get the line number for the delete animation.
   * @return Line number, or -1 if no animation
   */
  public int getDelAnimLine() {
    return delAnimLine;
  }
}
