package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.CodeFold;
import com.yn.sodiumeditor.io.EditOperators;

/**
 * OnSingleTapUp handles onSingleTapUp() gesture event for SodiumEditor.
 */
public class OnSingleTapUp {

  private final SodiumEditor editor;

  public OnSingleTapUp(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handle onSingleTapUp event
   */
  public boolean onSingleTapUp(MotionEvent e) {
    if (editor.autoCompletion.suggestionAcceptedThisTouch) return true;
    if (editor.multiTouchActive || editor.hadMultiTouch) return true;

    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
    }
    if (editor.codeFold.isCodeFoldingEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float gy = e.getY() + editor.scroll.scrollY;
      int line = editor.getGlobalLineForY(gy);
      if (editor.codeFold.toggleFoldAtLine(line)) {
        editor.codeFold.startFoldMarkerRipple(line);
        editor.popup.hidePopup();
        editor.invalidate();
        return true;
      }
    }
    float y = e.getY() + editor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
    int totalVisible =
        editor.wordWrap.isWordWrapEnabled ? editor.wordWrap.getTotalVisualLineCount() : editor.codeFold.getVisibleLineCount();
    EditOperators.CursorTarget target = editor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (editor.codeFold.isCodeFoldingEnabled) {
      String ln = editor.getLineTextForRender(line);
      float xLocal = editor.viewToTextX(e.getX());
      float x;
      if (editor.wordWrap.isWordWrapEnabled) {
        int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
        int seg =
            editor.wordWrap.getWrapSegmentIndexForChar(
                starts, Math.max(0, Math.min(target.ch, ln.length())));
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        x = xLocal + editor.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
      } else {
        x = xLocal;
      }
      if (editor.isFoldPlaceholderHit(line, ln, x)) {
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          editor.codeFold.startFoldPlaceholderRipple(line, bounds[0], bounds[1]);
        }
        if (editor.codeFold.toggleFoldAtLine(line)) {
          editor.codeFold.startFoldMarkerRipple(line);
        }
        editor.popup.hidePopup();
        editor.invalidate();
        return true;
      }
      CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
      if (range != null && range.collapsed) {
        String endLineText = editor.getLineTextForRender(range.endLine);
        if (endLineText == null || endLineText.isEmpty()) {
          endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
        }
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          float xStart =
              editor.measureHighlightedSegmentWidth(
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
            editor.cursor.cursorLine = line;
            editor.cursor.cursorChar = Math.max(0, range.openCharIndex);
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            editor.cursor.cursorLine = range.endLine;
            if (closeIdx >= 0) {
              editor.cursor.cursorChar = closeIdx + 1;
            } else if (endLineText != null) {
              editor.cursor.cursorChar = endLineText.length();
            } else {
              editor.cursor.cursorChar = 0;
            }
          } else {
            float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
            int idx =
                editor.getCharIndexForXInRange(
                    endLineText,
                    range.endLine,
                    suffixStart,
                    endLineText.length(),
                    xSuffix);
            editor.cursor.cursorLine = range.endLine;
            editor.cursor.cursorChar = Math.max(suffixStart, Math.min(idx, endLineText.length()));
          }

          editor.popup.hidePopup();
          editor.invalidate();
          editor.caret.resetBlink();
          editor.showKeyboard();
          editor.restartInput();
          editor.autoCompletion.updateSuggestion();
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
      String ln = editor.getLineTextForRender(line);
      editor.cursor.cursorLine = line;
      editor.cursor.cursorChar = Math.max(0, Math.min(target.ch, ln.length()));
    }

    editor.popup.hidePopup();
    editor.selection.selecting = false;
    editor.invalidate();
    editor.caret.resetBlink();
    editor.showKeyboard();
    editor.restartInput();
    editor.autoCompletion.updateSuggestion();
    return true;
  }
}
