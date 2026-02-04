package com.yn.sodiumeditor.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;

final class CharAnimationManager {
  private final SodiumEditorView view;

  private boolean isCharAnimationEnabled = false;
  private int charAnimationDurationMs = 200;
  private int charAnimFastDurationMs = 60;
  private long charAnimFastThresholdMs = 80;
  private long lastCharAnimUptime = 0L;
  @Nullable private String lastComposingTextForCharAnim;

  private int charAnimLine = -1;
  private int charAnimStartChar = 0;
  private int charAnimEndChar = 0;
  private float charAnimAlpha = 0f;
  @Nullable private ValueAnimator charAnimAnimator;
  private final Paint charAnimTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private int delAnimLine = -1;
  private int delAnimAtChar = 0;
  @Nullable private String delAnimText;
  @Nullable private Paint delAnimPaint;
  private float delAnimAlpha = 0f;
  @Nullable private ValueAnimator delAnimAnimator;

  CharAnimationManager(SodiumEditorView view) {
    this.view = view;
  }

  boolean isEnabled() {
    return isCharAnimationEnabled;
  }

  void setEnabled(boolean enabled, int durationMs) {
    isCharAnimationEnabled = enabled;
    if (durationMs > 0) charAnimationDurationMs = durationMs;
    if (!enabled) {
      if (charAnimAnimator != null) charAnimAnimator.cancel();
      charAnimAnimator = null;
      charAnimAlpha = 0f;
      charAnimLine = -1;
      charAnimStartChar = 0;
      charAnimEndChar = 0;
      lastComposingTextForCharAnim = null;
      if (delAnimAnimator != null) delAnimAnimator.cancel();
      delAnimAnimator = null;
      delAnimAlpha = 0f;
      delAnimLine = -1;
      delAnimAtChar = 0;
      delAnimText = null;
      view.invalidate();
    }
  }

  void startCharAnimationFromText(@Nullable CharSequence committedText) {
    if (!isCharAnimationEnabled) return;
    if (committedText == null) return;

    final int targetLine = view.getCursorLine();
    final int targetEndChar = view.getCursorChar();

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
          if (delAnimAnimator != null) delAnimAnimator.cancel();
          if (charAnimAnimator != null) charAnimAnimator.cancel();
          charAnimLine = targetLine;
          charAnimEndChar = Math.max(0, targetEndChar);
          charAnimStartChar = Math.max(0, charAnimEndChar - finalCharCount);
          charAnimAlpha = 0.2f;
          view.invalidateLineGlobalForCharAnim(charAnimLine);

          charAnimAnimator = ValueAnimator.ofFloat(0.2f, 1f);
          charAnimAnimator.setDuration(animDuration);
          charAnimAnimator.addUpdateListener(
              a -> {
                Object v = a.getAnimatedValue();
                charAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                view.invalidateLineGlobalForCharAnim(charAnimLine);
              });
          charAnimAnimator.addListener(
              new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                  charAnimAlpha = 0f;
                  charAnimLine = -1;
                  view.invalidate();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                  charAnimAlpha = 0f;
                  charAnimLine = -1;
                  view.invalidate();
                }
              });
          charAnimAnimator.start();
        };
    if (Looper.myLooper() == Looper.getMainLooper()) start.run();
    else view.post(start);
  }

  void startDeleteAnimation(
      int targetLine, int atChar, @Nullable String removedText, @Nullable Paint paintToUse) {
    if (!isCharAnimationEnabled) return;
    if (removedText == null || removedText.isEmpty()) return;

    final int lineForAnim = targetLine;
    final int atForAnim = Math.max(0, atChar);
    final String textForAnim = removedText;
    final Paint p = (paintToUse != null) ? paintToUse : view.getPaintForCharAnim();
    long now = SystemClock.uptimeMillis();
    long delta = (lastCharAnimUptime == 0L) ? Long.MAX_VALUE : (now - lastCharAnimUptime);
    lastCharAnimUptime = now;
    final int animDuration =
        (delta <= charAnimFastThresholdMs)
            ? Math.max(1, Math.min(charAnimationDurationMs, charAnimFastDurationMs))
            : Math.max(1, charAnimationDurationMs);

    Runnable start =
        () -> {
          if (charAnimAnimator != null) charAnimAnimator.cancel();
          if (delAnimAnimator != null) delAnimAnimator.cancel();
          delAnimLine = lineForAnim;
          delAnimAtChar = atForAnim;
          delAnimText = textForAnim;
          delAnimPaint = p;
          delAnimAlpha = 1f;
          view.invalidateLineGlobalForCharAnim(lineForAnim);

          delAnimAnimator = ValueAnimator.ofFloat(1f, 0f);
          delAnimAnimator.setDuration(animDuration);
          delAnimAnimator.addUpdateListener(
              a -> {
                Object v = a.getAnimatedValue();
                delAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                view.invalidateLineGlobalForCharAnim(lineForAnim);
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
                  view.invalidate();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                  delAnimAlpha = 0f;
                  delAnimLine = -1;
                  delAnimAtChar = 0;
                  delAnimText = null;
                  delAnimPaint = null;
                  view.invalidate();
                }
              });
          delAnimAnimator.start();
        };

    if (Looper.myLooper() == Looper.getMainLooper()) start.run();
    else view.post(start);
  }

  void release() {
    if (charAnimAnimator != null) charAnimAnimator.cancel();
    if (delAnimAnimator != null) delAnimAnimator.cancel();
  }

  @Nullable
  String getLastComposingTextForCharAnim() {
    return lastComposingTextForCharAnim;
  }

  void setLastComposingTextForCharAnim(@Nullable String text) {
    lastComposingTextForCharAnim = text;
  }

  void clearLastComposingTextForCharAnim() {
    lastComposingTextForCharAnim = null;
  }

  int getCharAnimLine() {
    return charAnimLine;
  }

  int getCharAnimStartChar() {
    return charAnimStartChar;
  }

  int getCharAnimEndChar() {
    return charAnimEndChar;
  }

  float getCharAnimAlpha() {
    return charAnimAlpha;
  }

  int getDelAnimLine() {
    return delAnimLine;
  }

  int getDelAnimAtChar() {
    return delAnimAtChar;
  }

  @Nullable
  String getDelAnimText() {
    return delAnimText;
  }

  @Nullable
  Paint getDelAnimPaint() {
    return delAnimPaint;
  }

  float getDelAnimAlpha() {
    return delAnimAlpha;
  }

  Paint getTempPaint() {
    return charAnimTmpPaint;
  }
}
