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
import java.util.HashMap;

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
    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange hidden = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
      if (hidden != null) {
        return getCollapsedFoldCaretDocumentX(hidden, cursor.cursorChar);
      }

      CodeFold.FoldRange start = editor.codeFold.getFoldRangeAtStart(cursor.cursorLine);
      if (start != null && start.collapsed && cursor.cursorChar > start.openCharIndex) {
        return getCollapsedFoldCaretDocumentX(start, cursor.cursorChar);
      }
    }

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
    int lineForVisual = cursor.cursorLine;
    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange hidden = editor.codeFold.getCollapsedRangeContainingLine(cursor.cursorLine);
      if (hidden != null) {
        lineForVisual = hidden.startLine;
      } else {
        CodeFold.FoldRange start = editor.codeFold.getFoldRangeAtStart(cursor.cursorLine);
        if (start != null && start.collapsed && cursor.cursorChar > start.openCharIndex) {
          lineForVisual = start.startLine;
        }
      }
    }
    int visualLine = editor.wordWrap.getVisualIndexForLineAndChar(lineForVisual, cursor.cursorChar);
    return visualLine * editor.textRender.lineHeight;
  }

  private float getCollapsedFoldCaretDocumentX(CodeFold.FoldRange fold, int cursorChar) {
    String startLineText = getLineTextForCollapsedFoldCaret(fold.startLine);

    int prefixEnd;
    if (fold.isBlockComment) {
      prefixEnd = Math.min(fold.openCharIndex + 2, startLineText.length());
    } else if (fold.isIndentFold) {
      prefixEnd = startLineText.length();
    } else {
      prefixEnd = Math.min(fold.openCharIndex + 1, startLineText.length());
    }

    float x = editor.highlite.measureHighlightedSegmentWidth(startLineText, fold.startLine, 0, prefixEnd);
    x += Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));

    String endLineText = getLineTextForCollapsedFoldCaret(fold.endLine);
    if (endLineText == null || endLineText.isEmpty()) {
      String foldEndText = editor.codeFold.utils.getEndLineTextForFold(fold);
      if (foldEndText != null) endLineText = foldEndText;
    }
    if (endLineText == null) endLineText = "";

    int closeIdx = editor.codeFold.resolveCloseCharIndex(fold, endLineText);
    if (closeIdx < 0) closeIdx = fold.closeCharIndex;
    if (closeIdx < 0) closeIdx = endLineText.length();

    if (fold.isBlockComment) {
      String close = "*/";
      float closeWidth = editor.textRender.paint.measureText(close);
      int closeEnd = Math.min(endLineText.length(), Math.max(0, closeIdx + 2));
      if (cursorChar <= closeEnd) {
        return x + Math.min(closeWidth, editor.textRender.paint.measureText(close, 0, Math.min(close.length(), Math.max(0, cursorChar - Math.max(0, closeIdx)))));
      }
      x += closeWidth;
      int suffixStart = closeEnd;
      int safeChar = Math.max(suffixStart, Math.min(cursorChar, endLineText.length()));
      return x
          + editor.highlite.measureHighlightedSegmentWidth(
              endLineText, fold.endLine, suffixStart, safeChar);
    }

    if (!fold.isIndentFold) {
      String close = String.valueOf(fold.closeChar);
      float closeWidth = editor.textRender.paint.measureText(close);
      int closeEnd = Math.min(endLineText.length(), Math.max(0, closeIdx + 1));
      if (cursorChar <= closeEnd) {
        return x + Math.min(closeWidth, editor.textRender.paint.measureText(close, 0, Math.min(close.length(), Math.max(0, cursorChar - Math.max(0, closeIdx)))));
      }
      x += closeWidth;
      int suffixStart = closeEnd;
      int safeChar = Math.max(suffixStart, Math.min(cursorChar, endLineText.length()));
      return x
          + editor.highlite.measureHighlightedSegmentWidth(
              endLineText, fold.endLine, suffixStart, safeChar);
    }

    return x;
  }

  private String getLineTextForCollapsedFoldCaret(int line) {
    String text = editor.windowRender.getLineTextForRender(line);
    if (text != null && !text.isEmpty()) return text;
    if (line < 0 || editor.fileIO.sourceFile == null) return text == null ? "" : text;
    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
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
