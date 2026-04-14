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
          editor.cursor.invalidateCursorArea();
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
   * Get caret X position relative to document start (no scroll)
   */
  public float getCaretDocumentX() {
    String lineText = editor.textRender.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return 0f;
    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    return editor.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);
  }

  /**
   * Get caret Y position relative to document start (no scroll)
   */
  public float getCaretDocumentY() {
    int visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    return visualLine * editor.textRender.lineHeight;
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

    // Use animated Document position if available
    float docX, docY;
    if (editor.cursorAnimation.cursorAnimValid && !Float.isNaN(editor.cursorAnimation.cursorDrawX)) {
      docX = editor.cursorAnimation.cursorDrawX;
      docY = editor.cursorAnimation.cursorDrawY;
    } else {
      docX = getCaretDocumentX();
      docY = getCaretDocumentY();
    }
    
    // Convert Document coordinates to Screen coordinates for drawing
    float x = editor.getTextStartX() + docX - editor.scroll.scrollX;
    float y = docY - editor.scroll.scrollY;
    
    float height = editor.textRender.lineHeight;
    float width = Math.max(2f, caretWidth);
    
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(caretColor);
    paint.setStyle(Paint.Style.FILL);

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

    String lineText = editor.textRender.getLineTextForRender(cursor.cursorLine);
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
        visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
      } else {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(cursor.cursorLine);
      }
    } else if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
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

    String lineText = editor.textRender.getLineTextForRender(cursor.cursorLine);
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
        visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
      } else {
        visualLine = editor.codeFold.getVisibleIndexForGlobalLine(cursor.cursorLine);
      }
    } else if (editor.wordWrap.isWordWrapEnabled) {
      visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    }
    return (visualLine - editor.drawBaseLine) * editor.textRender.lineHeight;
  }

  private float getFoldedCaretX(CodeFold.FoldRange range, boolean canvasSpace) {
    String startLineText = editor.textRender.getLineTextForRender(range.startLine);
    if (startLineText == null) startLineText = "";

    int prefixEnd;
    if (range.isBlockComment) {
      prefixEnd = Math.min(range.openCharIndex + 2, startLineText.length());
    } else if (range.isIndentFold) {
      prefixEnd = startLineText.length();
    } else {
      prefixEnd = Math.min(range.openCharIndex + 1, startLineText.length());
    }

    float xStart = editor.highlite.measureHighlightedSegmentWidth(startLineText, range.startLine, 0, prefixEnd);
    float placeholderWidth =
        Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
    float xAfter = xStart + placeholderWidth;

    if (range.isIndentFold) {
      float x = xAfter;
      return canvasSpace ? x : editor.getTextStartX() + x - editor.scroll.scrollX;
    }

    String closeText = range.isBlockComment ? "*/" : String.valueOf(range.closeChar);
    float closeWidth = editor.textRender.paint.measureText(closeText);

    String endLineText = editor.textRender.getLineTextForRender(range.endLine);
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
          editor.highlite.measureHighlightedSegmentWidth(endLineText, range.endLine, suffixStart, safeChar);
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
  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x;
    if (editor.binaryRender.isBinarySafeRenderingEnabled() && !editor.textRender.isRtl) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
        x = editor.binaryRender.getXForCharBinary(line, charIndex, editor.textRender.paint, spans, padX);
      } else {
        x = editor.measureText(line, charIndex, globalLine);
      }
    } else {
      x = editor.measureText(line, charIndex, globalLine);
    }
    if (!editor.textRender.isRtl) return x;
    int logicalLen = editor.getLogicalLineLength(globalLine, line);
    float w = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = editor.getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel;
    if (editor.binaryRender.isBinarySafeRenderingEnabled() && !editor.textRender.isRtl) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
        float x1 = editor.binaryRender.getXForCharBinary(line, segStart, editor.textRender.paint, spans, padX);
        float x2 = editor.binaryRender.getXForCharBinary(line, charIndex, editor.textRender.paint, spans, padX);
        xRel = Math.max(0f, x2 - x1);
      } else {
        xRel = editor.measureTextWithVisualSpaces(line, segStart, charIndex, editor.textRender.paint);
      }
    } else {
      xRel = editor.measureTextWithVisualSpaces(line, segStart, charIndex, editor.textRender.paint);
    }
    if (!editor.textRender.isRtl) return xRel;
    float w = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = editor.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }
  
}

