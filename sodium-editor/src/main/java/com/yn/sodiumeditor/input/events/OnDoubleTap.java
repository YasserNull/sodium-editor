package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * OnDoubleTap handles onDoubleTap() gesture event for SodiumEditor.
 */
public class OnDoubleTap {

  private final SodiumEditor editor;
  private final OnSingleTapUp onSingleTapUp;

  public OnDoubleTap(SodiumEditor editor, OnSingleTapUp onSingleTapUp) {
    FunctionLog.f("OnDoubleTap", "OnDoubleTap", editor, onSingleTapUp);
    this.editor = editor;
    this.onSingleTapUp = onSingleTapUp;
  }

  /**
   * Handle onDoubleTap event
   */
  public boolean onDoubleTap(MotionEvent e) {
    FunctionLog.f("OnDoubleTap", "onDoubleTap", e);
    if (editor.autoCompletion.suggestionAcceptedThisTouch)
      return true; // Don't process if suggestion was accepted
    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.windowRender.getLineTextForRender(line);
    if (ln == null || ln.isEmpty()) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
    if (editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
      if (range != null && range.collapsed) {
        float xLocal = editor.scroll.viewToTextX(e.getX());
        float x = xLocal;
        if (editor.wordWrap.isWordWrapEnabled) {
          int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
          int seg =
              editor.wordWrap.getWrapSegmentIndexForChar(
                  starts, Math.max(0, Math.min(charIndex, ln.length())));
          int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
          x = x + editor.textRender.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
        }
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          float xStart = bounds[0];
          float placeholderWidth =
              Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
          float closeStart = xStart + placeholderWidth;
          String endLineText = editor.windowRender.getLineTextForRender(range.endLine);
          if (endLineText == null || endLineText.isEmpty()) {
            endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
          }
          float closeWidth =
              range.isBlockComment
                  ? editor.textRender.paint.measureText("*/")
                  : editor.textRender.paint.measureText(String.valueOf(range.closeChar));
          int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
          int suffixStart =
              range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1)
                  : (closeIdx >= 0 ? closeIdx + 1 : -1);

          if (x <= xStart) {
            line = range.startLine;
            charIndex = Math.max(0, range.openCharIndex);
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            line = range.endLine;
            charIndex = (closeIdx >= 0) ? (closeIdx + 1) : 0;
            ln = (endLineText != null) ? endLineText : "";
          } else {
            float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
            int idx =
                editor.wordWrap.getCharIndexForXInRange(
                    endLineText, range.endLine, suffixStart, endLineText.length(), xSuffix);
            line = range.endLine;
            charIndex = Math.max(suffixStart, Math.min(idx, endLineText.length()));
            ln = (endLineText != null) ? endLineText : "";
          }
        }
      }
    }
    if (!editor.selection.applySmartDoubleTapSelection(line, charIndex, ln)) {
      return onSingleTapUp.onSingleTapUp(e);
    }
    editor.popup.showPopupAtSelection();
    editor.popup.pendingPopupAfterDoubleTap = true;
    editor.post(
        () -> {
          if (!editor.popup.pendingPopupAfterDoubleTap) return;
          editor.popup.pendingPopupAfterDoubleTap = false;
          if (editor.selection.hasSelection) editor.popup.showPopupAtSelection();
        });
    editor.caret.resetBlink();
    editor.invalidate();
    editor.ime.showKeyboard();
    editor.view.restartInput();
    return true;
  }
}
