package com.yn.sodiumeditor.core.cursor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Caret handles caret (cursor) rendering for SodiumEditor. This includes: - Caret blinking - Caret
 * rendering
 */
public class Caret {
  public final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Caret blink state
  public boolean caretEnabled = true;
  public boolean isCursorVisible = true;
  public boolean caretBlinkEnabled = true;
  public boolean pauseBlinkWhileTypingEnabled = true;
  public long caretBlinkPeriodMs = 500;
  public long caretTypingResumeDelayMs = 650;

  // Caret appearance
  public float caretWidth = 4f;
  public int caretColor = 0xFF000000;

  public final Runnable blinkRunnable;
  public final Runnable resumeBlinkAfterTypingRunnable;
  public final Handler mainHandler = new Handler(Looper.getMainLooper());

  private final SodiumEditor editor;
  private final Cursor cursor;
  private boolean blinkPausedByTyping = false;

  public Caret(SodiumEditor editor, Cursor cursor) {
    this.editor = editor;
    this.cursor = cursor;

    this.blinkRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (editor.isFocused() && !editor.selection.hasSelection) {
              isCursorVisible = !isCursorVisible;
              editor.cursor.invalidateCursorArea();
              mainHandler.postDelayed(this, caretBlinkPeriodMs);
            }
          }
        };

    this.resumeBlinkAfterTypingRunnable =
        new Runnable() {
          @Override
          public void run() {
            resumeBlinkAfterTyping();
          }
        };
  }

  /** Start caret blink */
  public void startBlink() {
    stopBlink();
    if (caretEnabled && caretBlinkEnabled && !blinkPausedByTyping) {
      isCursorVisible = true;
      mainHandler.postDelayed(blinkRunnable, caretBlinkPeriodMs);
    }
  }

  /** Stop caret blink */
  public void stopBlink() {
    mainHandler.removeCallbacks(blinkRunnable);
    isCursorVisible = true;
  }

  /** Reset caret blink */
  public void resetBlink() {
    stopBlink();
    startBlink();
  }

  public void setCaretEnabled(boolean enabled) {
    if (caretEnabled == enabled) return;
    caretEnabled = enabled;
    if (enabled) {
      resetBlink();
    } else {
      stopBlink();
    }
    editor.invalidate();
  }

  public boolean isCaretEnabled() {
    return caretEnabled;
  }


  public void setCaretBlinkEnabled(boolean enabled) {
    if (caretBlinkEnabled == enabled) return;
    caretBlinkEnabled = enabled;
    resetBlink();
    editor.invalidate();
  }

  public boolean isCaretBlinkEnabled() {
    return caretBlinkEnabled;
  }


  public void setCaretBlinkPeriodMs(long periodMs) {
    long safePeriodMs = Math.max(1L, periodMs);
    if (caretBlinkPeriodMs == safePeriodMs) return;
    caretBlinkPeriodMs = safePeriodMs;
    resetBlink();
  }

  public long getCaretBlinkPeriodMs() {
    return caretBlinkPeriodMs;
  }

  public void setCaretTypingResumeDelayMs(long delayMs) {
    long safeDelayMs = Math.max(0L, delayMs);
    if (caretTypingResumeDelayMs == safeDelayMs) return;
    caretTypingResumeDelayMs = safeDelayMs;
    if (blinkPausedByTyping) {
      mainHandler.removeCallbacks(resumeBlinkAfterTypingRunnable);
      mainHandler.postDelayed(resumeBlinkAfterTypingRunnable, caretTypingResumeDelayMs);
    }
  }

  public long getCaretTypingResumeDelayMs() {
    return caretTypingResumeDelayMs;
  }

  public void setCaretWidth(float width) {
    float safeWidth = Math.max(1f, width);
    if (caretWidth == safeWidth) return;
    caretWidth = safeWidth;
    editor.cursor.invalidateCursorArea();
  }

  public float getCaretWidth() {
    return caretWidth;
  }

  public void setCaretColor(int color) {
    if (caretColor == color) return;
    caretColor = color;
    updateCaretAppearance();
    editor.cursor.invalidateCursorArea();
  }

  public int getCaretColor() {
    return caretColor;
  }

  public void setPauseBlinkWhileTypingEnabled(boolean enabled) {
    if (pauseBlinkWhileTypingEnabled == enabled) return;
    pauseBlinkWhileTypingEnabled = enabled;
    if (!enabled) {
      mainHandler.removeCallbacks(resumeBlinkAfterTypingRunnable);
      blinkPausedByTyping = false;
      resetBlink();
    }
  }

  public boolean isPauseBlinkWhileTypingEnabled() {
    return pauseBlinkWhileTypingEnabled;
  }

  public void pauseBlinkForTyping() {
    if (!caretEnabled || !pauseBlinkWhileTypingEnabled) return;
    blinkPausedByTyping = true;
    stopBlink();
    mainHandler.removeCallbacks(resumeBlinkAfterTypingRunnable);
    mainHandler.postDelayed(resumeBlinkAfterTypingRunnable, caretTypingResumeDelayMs);
    editor.invalidate();
  }

  public void resumeBlinkAfterCursorPlacement() {
    mainHandler.removeCallbacks(resumeBlinkAfterTypingRunnable);
    resumeBlinkAfterTyping();
  }

  public void resumeBlinkAfterTyping() {
    if (!blinkPausedByTyping) return;
    blinkPausedByTyping = false;
    resetBlink();
  }

  /** Get caret X position relative to document start (no scroll) */
  public float getCaretDocumentX() {
    String lineText = editor.windowRender.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return 0f;
    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    return editor.textRender.measureTextWithVisualSpaces(
        lineText, 0, safeChar, editor.textRender.paint);
  }

  /** Get caret Y position relative to document start (no scroll) */
  public float getCaretDocumentY() {
    int lineForVisual = cursor.cursorLine;
    int visualLine = editor.wordWrap.getVisualIndexForLineAndChar(lineForVisual, cursor.cursorChar);
    return visualLine * editor.textRender.lineHeight;
  }

  /** Draw caret on canvas */
  public void drawCaret(Canvas canvas) {
    if (!caretEnabled) {
      return;
    }

    if (!editor.isFocused() || editor.selection.hasSelection) {
      return;
    }

    if (!isCursorVisible) {
      return;
    }

    boolean zoomOrScaleTransition =
        editor.zoom.isScaling
            || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())
            || editor.onTouch.multiTouchActive
            || editor.zoom.mJustFinishedScale;
    float cx;
    float cy;
    if (!zoomOrScaleTransition
        && editor.cursorAnimation.isCursorAnimationEnabled
        && editor.cursorAnimation.cursorAnimValid
        && !Float.isNaN(editor.cursorAnimation.cursorDrawX)) {
      cx = editor.cursorAnimation.cursorDrawX;
      cy = editor.cursorAnimation.cursorDrawY;
    } else {
      cx = getCaretDocumentX();
      cy = getCaretDocumentY();
    }

    float top = cy - editor.scroll.scrollY;
    float bottom = top + editor.textRender.lineHeight;
    float left = cx - editor.scroll.scrollX;
    float right = left + caretWidth;

    if (editor.textRender.isRtl) {
      left =
          (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth)
              - cx
              + editor.scroll.scrollX;
      right = left - caretWidth;
    } else {
      left += editor.layout.getTextStartX();
      right += editor.layout.getTextStartX();
    }
    caretPaint.setColor(caretColor);
    canvas.drawRect(left, top, right, bottom, caretPaint);
  }

  /** Updates caret appearance */
  public void updateCaretAppearance() {
    caretPaint.setColor(caretColor);
  }

  /** Get caret X for wrapped mode */
  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int index) {
    if (line == null) return 0f;
    int safeIndex = Math.max(0, Math.min(index, line.length()));
    int safeSegStart = Math.max(0, Math.min(segStart, line.length()));

    float xRel =
        editor.textRender.measureTextWithVisualSpaces(
            line, safeSegStart, safeIndex, editor.textRender.paint);

    if (!editor.textRender.isRtl) return xRel;

    float w = editor.highlight.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = editor.layout.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  /** Get caret X for a specific line and column. */
  public float getCaretXForLine(String lineText, int line, int col) {
    if (lineText == null) return 0f;
    int safeChar = Math.max(0, Math.min(col, lineText.length()));
    return editor.textRender.measureTextWithVisualSpaces(
        lineText, 0, safeChar, editor.textRender.paint);
  }
}
