package com.yn.sodiumeditor.core.cursor;

import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.utils.FunctionLog;

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
  public float caretWidth = 4f;
  public int caretColor = 0xFF000000;

  public final Runnable blinkRunnable;
  public final Handler mainHandler = new Handler(Looper.getMainLooper());

  private final SodiumEditor editor;
  private final Cursor cursor;

  public Caret(SodiumEditor editor, Cursor cursor) {
    FunctionLog.f("Caret", "Caret", editor, cursor);
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
    FunctionLog.f("Caret", "startBlink");
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
    FunctionLog.f("Caret", "stopBlink");
    mainHandler.removeCallbacks(blinkRunnable);
    isCursorVisible = true;
  }

  /**
   * Reset caret blink
   */
  public void resetBlink() {
    FunctionLog.f("Caret", "resetBlink");
    stopBlink();
    startBlink();
  }

  /**
   * Get caret X position relative to document start (no scroll)
   */
  public float getCaretDocumentX() {
    FunctionLog.f("Caret", "getCaretDocumentX");
    String lineText = editor.windowRender.getLineTextForRender(cursor.cursorLine);
    if (lineText == null) return 0f;
    int safeChar = Math.max(0, Math.min(cursor.cursorChar, lineText.length()));
    return editor.textRender.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);
  }

  /**
   * Get caret Y position relative to document start (no scroll)
   */
  public float getCaretDocumentY() {
    FunctionLog.f("Caret", "getCaretDocumentY");
    int visualLine = editor.wordWrap.getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    return visualLine * editor.textRender.lineHeight;
  }

  /**
   * Draw caret on canvas
   */
  public void drawCaret(Canvas canvas) {
    FunctionLog.f("Caret", "drawCaret", canvas);
    if (!editor.isFocused() || editor.selection.hasSelection) {
      return;
    }

    if (!isCursorVisible) {
      return;
    }

    float cx = getCaretDocumentX();
    float cy = getCaretDocumentY();

    float top = cy - editor.scroll.scrollY;
    float bottom = top + editor.textRender.lineHeight;
    float left = cx - editor.scroll.scrollX;
    float right = left + caretWidth;

    if (editor.textRender.isRtl) {
      left = (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) - cx + editor.scroll.scrollX;
      right = left - caretWidth;
    } else {
      left += editor.lineNumber.lineNumbersGutterWidth;
      right += editor.lineNumber.lineNumbersGutterWidth;
    }

    Log.i(
        "CursorDbg",
        "caretDraw"
            + " docX="
            + cx
            + " docY="
            + cy
            + " left="
            + left
            + " top="
            + top
            + " right="
            + right
            + " bottom="
            + bottom
            + " cursorLine="
            + cursor.cursorLine
            + " cursorChar="
            + cursor.cursorChar);
    caretPaint.setColor(caretColor);
    canvas.drawRect(left, top, right, bottom, caretPaint);
  }

  /**
   * Updates caret appearance
   */
  public void updateCaretAppearance() {
    FunctionLog.f("Caret", "updateCaretAppearance");
    caretPaint.setColor(caretColor);
  }

  /**
   * Get caret X for wrapped mode
   */
  public float getCaretXForSegment(String line, int globalLine, int segStart, int segEnd, int index) {
    FunctionLog.f("Caret", "getCaretXForSegment", line, globalLine, segStart, segEnd, index);
    if (line == null) return 0f;
    int safeIndex = Math.max(0, Math.min(index, line.length()));
    int safeSegStart = Math.max(0, Math.min(segStart, line.length()));
    
    float xRel = editor.textRender.measureTextWithVisualSpaces(line, safeSegStart, safeIndex, editor.textRender.paint);
    
    if (!editor.textRender.isRtl) return xRel;
    
    float w = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = editor.layout.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  /**
   * Get caret X for a specific line and column.
   */
  public float getCaretXForLine(String lineText, int line, int col) {
    FunctionLog.f("Caret", "getCaretXForLine", lineText, line, col);
    if (lineText == null) return 0f;
    int safeChar = Math.max(0, Math.min(col, lineText.length()));
    return editor.textRender.measureTextWithVisualSpaces(lineText, 0, safeChar, editor.textRender.paint);
  }
}
