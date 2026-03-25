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
          int suffixStart =
              range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1)
                  : (closeIdx >= 0 ? closeIdx + 1 : -1);

          if (x <= xStart) {
            editor.cursor.cursorLine = line;
            editor.cursor.cursorChar = Math.max(0, range.openCharIndex);
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            editor.cursor.cursorLine = range.endLine;
            editor.cursor.cursorChar = (closeIdx >= 0) ? (closeIdx + 1) : 0;
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
