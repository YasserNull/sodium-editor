package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import java.util.HashMap;

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
    boolean afterEnd = editor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible);
    if (afterEnd) {
      placeCursorAtLastVisibleLineEnd(totalVisible);
      if (editor.clickAfterEndToAddLine.isClickAfterEndToAddLineEnabled
          && visibleIndex == totalVisible) {
        editor.editOperators.insertTextAtCursor("\n");
      }
      finishTapCursorPlacement(afterEnd);
      return true;
    }

    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (editor.codeFold.isCodeFoldingEnabled) {
      String ln = getLineTextForTap(line);
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
        String endLineText = getLineTextForTap(range.endLine);
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
            setCursorFromFoldTap(line, Math.max(0, range.openCharIndex), ln);
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            int targetChar = 0;
            if (closeIdx >= 0) {
              targetChar = closeIdx + 1;
            } else if (endLineText != null) {
              targetChar = endLineText.length();
            }
            setCursorFromFoldTap(range.endLine, targetChar, endLineText);
          } else {
            float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
            int idx =
                editor.wordWrap.getCharIndexForXInRange(
                    endLineText,
                    range.endLine,
                    suffixStart,
                    endLineText.length(),
                    xSuffix);
            setCursorFromFoldTap(
                range.endLine,
                Math.max(suffixStart, Math.min(idx, endLineText.length())),
                endLineText);
          }

          finishTapCursorPlacement(false);
          return true;
        }
      }
    }

    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.windowRender.getLineTextForRender(line);
    editor.cursor.setCursorPosition(line, Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length())));

    finishTapCursorPlacement(false);
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

  private String getLineTextForTap(int line) {
    String text = editor.windowRender.getLineTextForRender(line);
    if (text != null && !text.isEmpty()) return text;
    if (line < 0 || editor.fileIO.sourceFile == null) return text == null ? "" : text;
    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
  }

  private void placeCursorAtLastVisibleLineEnd(int totalVisible) {
    int visibleIndex = Math.max(0, totalVisible - 1);
    int line =
        editor.wordWrap.isWordWrapEnabled
            ? editor.wordWrap.getVisualPositionForIndex(visibleIndex).line
            : editor.codeFold.mapVisibleIndexToGlobal(visibleIndex);
    String lineText = getLineTextForTap(line);
    CodeFold.FoldRange range =
        editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getFoldRangeAtStart(line) : null;
    if (range != null && range.collapsed) {
      String endLineText = getLineTextForTap(range.endLine);
      if (endLineText == null || endLineText.isEmpty()) {
        endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
      }
      setCursorFromFoldTap(range.endLine, endLineText == null ? 0 : endLineText.length(), endLineText);
      return;
    }
    editor.cursor.cursorLine = Math.max(0, line);
    editor.cursor.cursorChar = lineText == null ? 0 : lineText.length();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }

  private void setCursorFromFoldTap(int line, int col, String knownLineText) {
    int totalLines = editor.view.getLinesCount();
    int targetLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    int maxCol = knownLineText == null ? Math.max(0, col) : knownLineText.length();
    editor.cursor.cursorLine = targetLine;
    editor.cursor.cursorChar = Math.max(0, Math.min(col, maxCol));
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }
}
