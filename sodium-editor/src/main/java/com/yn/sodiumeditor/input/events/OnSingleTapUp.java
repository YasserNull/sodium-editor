package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * OnSingleTapUp handles onSingleTapUp() gesture event for SodiumEditor.
 */
public class OnSingleTapUp {

  private final SodiumEditor editor;

  public OnSingleTapUp(SodiumEditor editor) {
    FunctionLog.f("OnSingleTapUp", "OnSingleTapUp", editor);
    this.editor = editor;
  }

  /**
   * Handle onSingleTapUp event
   */
  public boolean onSingleTapUp(MotionEvent e) {
    FunctionLog.f("OnSingleTapUp", "onSingleTapUp", e);
    if (editor.autoCompletion.suggestionAcceptedThisTouch) return true;
    if (editor.onTouch.multiTouchActive || editor.onTouch.hadMultiTouch) return true;

    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
    }
    if (editor.codeFold.isCodeFoldingEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float gy = e.getY() + editor.scroll.scrollY;
      int line = editor.wordWrap.getGlobalLineForY(gy);
        if (editor.codeFold.toggleFoldAtLine(line)) {
          editor.codeFold.startFoldMarkerRipple(line);
          editor.invalidate();
          return true;
      }
    }
    float y = e.getY() + editor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
    int totalVisible =
        editor.wordWrap.isWordWrapEnabled ? editor.wordWrap.getTotalVisualLineCount() : editor.codeFold.getVisibleLineCount();
    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (editor.codeFold.isCodeFoldingEnabled) {
      String ln = editor.windowRender.getLineTextForRender(line);
      float xLocal = editor.scroll.viewToTextX(e.getX());
      float x;
      if (editor.wordWrap.isWordWrapEnabled) {
        int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
        int seg =
            editor.wordWrap.getWrapSegmentIndexForChar(
                starts, Math.max(0, Math.min(target.ch, ln.length())));
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        x = xLocal + editor.textRender.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
      } else {
        x = xLocal;
      }
      if (editor.codeFold.isFoldPlaceholderHit(line, ln, x)) {
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          editor.codeFold.startFoldPlaceholderRipple(line, bounds[0], bounds[1]);
        }
        if (editor.codeFold.toggleFoldAtLine(line)) {
          editor.codeFold.startFoldMarkerRipple(line);
        }
        editor.invalidate();
        return true;
      }
      CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
      if (range != null && range.collapsed) {
        String endLineText = editor.windowRender.getLineTextForRender(range.endLine);
        if (endLineText == null || endLineText.isEmpty()) {
          endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
        }
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          float xStart =
              editor.highlite.measureHighlightedSegmentWidth(
                  ln,
                  line,
                  0,
                  range.isBlockComment ? Math.min(range.openCharIndex + 2, ln.length())
                      : range.isIndentFold ? ln.length()
                      : Math.min(range.openCharIndex + 1, ln.length()));
          float placeholderWidth =
              Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
          float closeStart = xStart + placeholderWidth;
          float closeWidth = editor.textRender.paint.measureText(String.valueOf(range.closeChar));
          int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
          if (closeIdx < 0 && range.closeCharIndex >= 0) {
            closeIdx = range.closeCharIndex;
          }
          int suffixStart =
              range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1)
                  : (closeIdx >= 0 ? closeIdx + 1 : -1);

          if (x <= xStart) {
            editor.cursor.setCursorPosition(line, Math.max(0, range.openCharIndex));
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            int targetChar = 0;
            if (closeIdx >= 0) {
              targetChar = closeIdx + 1;
            } else if (endLineText != null) {
              targetChar = endLineText.length();
            }
            editor.cursor.setCursorPosition(range.endLine, targetChar);
          } else {
            float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
            int idx =
                editor.wordWrap.getCharIndexForXInRange(
                    endLineText,
                    range.endLine,
                    suffixStart,
                    endLineText.length(),
                    xSuffix);
            editor.cursor.setCursorPosition(range.endLine, Math.max(suffixStart, Math.min(idx, endLineText.length())));
          }

          finishTapCursorPlacement(false);
          return true;
        }
      }
    }

    boolean afterEnd = editor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible);

    if (afterEnd) {
      if (!editor.clickAfterEndToAddLine.handleClickAfterEnd(visibleIndex, totalVisible)) {
        editor.clickAfterEndToAddLine.handleDefaultAfterEnd();
      }
    } else {
      editor.fileIO.ensureLineInWindow(line, true);
      String ln = editor.windowRender.getLineTextForRender(line);
      editor.cursor.setCursorPosition(line, Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length())));
    }

    finishTapCursorPlacement(afterEnd);
    return true;
  }

  private void finishTapCursorPlacement(boolean snapCursorAnimation) {
    if (snapCursorAnimation) {
      editor.cursorAnimation.snapToPosition(
          editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
    }
    editor.selection.selecting = false;
    editor.invalidate();
    editor.caret.resetBlink();
    editor.ime.showKeyboard();
    editor.view.restartInput();
    editor.autoCompletion.updateSuggestion();
  }
}
