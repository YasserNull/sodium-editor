package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;

/**
 * OnLongPress handles onLongPress() gesture event for SodiumEditor.
 */
public class OnLongPress {

  private final SodiumEditor editor;

  public OnLongPress(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handle onLongPress event
   */
  public void onLongPress(MotionEvent e) {
    if (editor.autoCompletion.suggestionAcceptedThisTouch) return;
    if (editor.multiTouchActive || editor.hadMultiTouch) return;

    if (editor.popup.showPopup) {
      int hitAction = editor.popup.getPopupActionAt(e.getX(), e.getY());
      if (hitAction != 0) {
        editor.popup.popupPressedAction = hitAction;
        editor.popup.startPopupRippleHold(hitAction, e.getX(), e.getY());
        return;
      }
    }

    if (editor.movedSinceDown) return;

    if (editor.lineNumber.lineNumberSelectionEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float y = e.getY() + editor.scroll.scrollY;
      int line = editor.getGlobalLineForY(y);
      editor.lineNumber.beginLineNumberSelection(line);
      return;
    }

    // Position calculation
    EditOperators.CursorTarget target = editor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true); // Make sure line data is available

    String ln = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
    if (ln == null) ln = editor.getLineTextForRender(line);
    int cursorLine = line;
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
    float xLocal = editor.viewToTextX(e.getX());
    if (editor.wordWrap.isWordWrapEnabled) {
      int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
      int seg =
          editor.wordWrap.getWrapSegmentIndexForChar(
              starts, Math.max(0, Math.min(target.ch, ln.length())));
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      xLocal = xLocal + editor.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
    }

    if (editor.codeFold.isCodeFoldingEnabled) {
      com.yn.sodiumeditor.core.CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
      if (range != null && range.collapsed) {
        float[] bounds = new float[2];
        if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
          float x = xLocal;
          float xStart = bounds[0];
          float placeholderWidth =
              Math.max(0f, editor.textRender.paint.measureText(com.yn.sodiumeditor.core.CodeFold.FOLD_PLACEHOLDER_TEXT));
          float closeStart = xStart + placeholderWidth;
          String endLineText = editor.getLineTextForRender(range.endLine);
          if (endLineText == null || endLineText.isEmpty()) {
            endLineText = editor.codeFold.utils.getEndLineTextForFold(range);
          }
          float closeWidth = editor.textRender.paint.measureText(String.valueOf(range.closeChar));
          int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
          int suffixStart =
              range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1)
                  : (closeIdx >= 0 ? closeIdx + 1 : -1);
          if (x <= xStart) {
            charIndex = Math.max(0, range.openCharIndex);
          } else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) {
            cursorLine = range.endLine;
            charIndex = (closeIdx >= 0) ? (closeIdx + 1) : 0;
          } else {
            float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
            int idx =
                editor.getCharIndexForXInRange(
                    endLineText,
                    range.endLine,
                    suffixStart,
                    endLineText.length(),
                    xSuffix);
            cursorLine = range.endLine;
            charIndex = Math.max(suffixStart, Math.min(idx, endLineText.length()));
          }

          float renderedWidth = xStart + placeholderWidth;
          if (!range.isIndentFold) {
            String closeText = range.isBlockComment ? "*/" : String.valueOf(range.closeChar);
            float closeW = editor.textRender.paint.measureText(closeText);
            renderedWidth += closeW;
            if (endLineText != null && suffixStart >= 0 && suffixStart < endLineText.length()) {
              renderedWidth +=
                  editor.measureHighlightedSegmentWidth(
                      endLineText, range.endLine, suffixStart, endLineText.length());
            }
          }

          if (xLocal > renderedWidth) {
            if (range.isIndentFold) {
              cursorLine = range.startLine;
              charIndex = ln.length();
            } else {
              cursorLine = range.endLine;
              charIndex = (endLineText != null) ? endLineText.length() : 0;
            }
            editor.selection.hasSelection = false;
            editor.selection.isSelectAllActive = false;
            editor.selection.isEntireFileSelected = false;
            editor.selection.selecting = false;
            editor.cursor.cursorLine = cursorLine;
            editor.cursor.cursorChar = charIndex;
            editor.popup.hidePopup();
            editor.caret.resetBlink();
            editor.invalidate();
            editor.showKeyboard();
            editor.restartInput();
            return;
          }
        }
      }
    }

    float textWidth = editor.measureTextWithVisualSpaces(ln, 0, ln.length(), editor.textRender.paint);
    if (xLocal > textWidth) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      editor.selection.selecting = false;
      editor.cursor.cursorLine = line;
      editor.cursor.cursorChar = ln.length();
      editor.popup.hidePopup();
      editor.caret.resetBlink();
      editor.invalidate();
      editor.showKeyboard();
      editor.restartInput();
      return;
    }

    // Set cursor position
    editor.cursor.cursorLine = cursorLine;
    editor.cursor.cursorChar = charIndex;

    // Try smart selection, but show minimal popup even if it fails (e.g., empty line)
    boolean hasSelection = editor.selection.applySmartDoubleTapSelection(line, charIndex, ln);
    editor.selection.beginLongPressSelection(line, charIndex);
    
    if (hasSelection) {
      editor.popup.showPopupAtSelection();
    } else {
      // No selection (empty line or smart selection failed), show minimal popup with Select All and Paste
      editor.popup.showMinimalPopupAtCursor();
    }
    editor.caret.resetBlink();
    editor.invalidate();
    editor.showKeyboard();
    editor.restartInput();
  }

  /**
   * Called when smart double tap selection fails - delegates to OnSingleTapUp
   */
  public void onSingleTapUpFallback(MotionEvent e, OnSingleTapUp onSingleTapUp) {
    onSingleTapUp.onSingleTapUp(e);
  }
}
