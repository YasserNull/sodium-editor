package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
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
  public float caretWidth = 8f;
  public int caretColor = 0xFF000000;

  public final Runnable blinkRunnable;
  public final Handler mainHandler = new Handler(Looper.getMainLooper());

  private final SodiumEditor editor;
  private final Cursor cursor;

  public Caret(SodiumEditor editor, Cursor cursor) {
    this.editor = editor;
    this.cursor = cursor;

    this.blinkRunnable = new Runnable() {
      @Override
      public void run() {
        if (editor.isFocused() && !editor.selection.hasSelection) {
          isCursorVisible = !isCursorVisible;
          editor.invalidateCursorArea();
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
    if (!editor.isFocused() || editor.selection.hasSelection) {
      return;
    }

    if (!isCursorVisible && !editor.cursorAnimation.cursorAnimRunning) {
      return;
    }

    // Use animated position if animation is running, otherwise use actual position
    float x, y;
    if (editor.cursorAnimation.cursorAnimRunning) {
      x = editor.cursorAnimation.cursorDrawX;
      y = editor.cursorAnimation.cursorDrawY;
    } else {
      x = getCaretX();
      y = getCaretY();
    }
    float height = editor.textRender.lineHeight;

    // Use caretWidth from settings, ensure minimum width for visibility
    float width = Math.max(2f, caretWidth);
    
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(caretColor);
    paint.setStyle(Paint.Style.FILL);

    // Draw caret rectangle with proper positioning
    RectF caretRect = new RectF(x - width / 2, y, x + width / 2, y + height);
    canvas.drawRect(caretRect, paint);
  }

  /**
   * Get caret X position in screen coordinates
   */
  public float getCaretX() {
    CodeFold.FoldRange collapsed = null;
    if (editor.codeFold.isCodeFoldingEnabled) {
      collapsed = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
    }
    if (collapsed != null) {
      return getFoldedCaretX(collapsed, false);
    }

    String lineText = editor.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return editor.getTextStartX();

    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    float textWidth = editor.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);
    return editor.getTextStartX() + textWidth - editor.scroll.scrollX;
  }

  /**
   * Get caret Y position in screen coordinates
   */
  public float getCaretY() {
    int visualLine = cursor.cursorLine;
    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange collapsed = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
      if (collapsed != null) {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(collapsed.startLine);
      } else if (editor.wordWrap.isWordWrapEnabled) {
        visualLine = editor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
      } else {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(cursor.cursorLine);
      }
    } else if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    }
    return (visualLine * editor.textRender.lineHeight) - editor.scroll.scrollY;
  }

  /**
   * Get caret X position relative to translated canvas origin
   */
  public float getCaretCanvasX() {
    CodeFold.FoldRange collapsed = null;
    if (editor.codeFold.isCodeFoldingEnabled) {
      collapsed = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
    }
    if (collapsed != null) {
      return getFoldedCaretX(collapsed, true);
    }

    String lineText = editor.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return 0f;

    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    return editor.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);
  }

  /**
   * Get caret Y position relative to translated canvas origin
   */
  public float getCaretCanvasY() {
    int visualLine = cursor.cursorLine;
    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange collapsed = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
      if (collapsed != null) {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(collapsed.startLine);
      } else if (editor.wordWrap.isWordWrapEnabled) {
        visualLine = editor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
      } else {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(cursor.cursorLine);
      }
    } else if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    }
    return (visualLine - editor.drawBaseLine) * editor.textRender.lineHeight;
  }

  private float getFoldedCaretX(CodeFold.FoldRange range, boolean canvasSpace) {
    String startLineText = editor.getLineTextForRender(range.startLine);
    if (startLineText == null) startLineText = "";

    int prefixEnd;
    if (range.isBlockComment) {
      prefixEnd = Math.min(range.openCharIndex + 2, startLineText.length());
    } else if (range.isIndentFold) {
      prefixEnd = startLineText.length();
    } else {
      prefixEnd = Math.min(range.openCharIndex + 1, startLineText.length());
    }

    float xStart = editor.measureHighlightedSegmentWidth(startLineText, range.startLine, 0, prefixEnd);
    float placeholderWidth =
        Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
    float xAfter = xStart + placeholderWidth;

    if (range.isIndentFold) {
      float x = xAfter;
      return canvasSpace ? x : editor.getTextStartX() + x - editor.scroll.scrollX;
    }

    String closeText = range.isBlockComment ? "*/" : String.valueOf(range.closeChar);
    float closeWidth = editor.textRender.paint.measureText(closeText);

    String endLineText = editor.getLineTextForRender(range.endLine);
    if (endLineText == null || endLineText.isEmpty()) {
      endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
    }
    int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
    if (closeIdx < 0 && range.closeCharIndex >= 0) closeIdx = range.closeCharIndex;
    int suffixStart = -1;
    if (closeIdx >= 0) {
      suffixStart = range.isBlockComment ? closeIdx + 2 : closeIdx + 1;
    }

    float x = xAfter + closeWidth;
    if (endLineText != null && suffixStart >= 0 && cursor.cursorChar > suffixStart) {
      int safeChar = Math.max(suffixStart, Math.min(cursor.cursorChar, endLineText.length()));
      float suffixWidth =
          editor.measureHighlightedSegmentWidth(endLineText, range.endLine, suffixStart, safeChar);
      x += suffixWidth;
    }

    return canvasSpace ? x : editor.getTextStartX() + x - editor.scroll.scrollX;
  }

  /**
   * Check if caret should be drawn
   */
  public boolean shouldDrawCaret() {
    return editor.isFocused() && !editor.selection.hasSelection;
  }

  /**
   * Invalidate caret area
   */
  public void invalidateCaretArea() {
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.invalidate();
      return;
    }
    editor.invalidateLineGlobal(cursor.cursorLine);
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
    editor.invalidate();
  }

  public boolean isBlinking() {
    return isCursorVisible;
  }

  public boolean isAnimating() {
    return editor.cursorAnimation.cursorAnimRunning;
  }
}
