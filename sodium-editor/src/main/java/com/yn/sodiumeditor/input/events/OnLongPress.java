package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import java.util.HashMap;

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
    if (editor.onTouch.multiTouchActive || editor.onTouch.hadMultiTouch) return;

    if (editor.popup.showPopup) {
      int hitAction = editor.popup.getPopupActionAt(e.getX(), e.getY());
      if (hitAction != 0) {
        editor.popup.popupPressedAction = hitAction;
        editor.popup.startPopupRippleHold(hitAction, e.getX(), e.getY());
        return;
      }
    }

    if (editor.lineNumber.lineNumberSelectionEnabled && editor.lineNumber.isInLineNumberGutter(e.getX())) {
      float y = e.getY() + editor.scroll.scrollY;
      int line = editor.wordWrap.getGlobalLineForY(y);
      editor.lineNumber.beginLineNumberSelection(line);
      return;
    }

    // Position calculation
    EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;
    editor.fileIO.ensureLineInWindow(line, true); // Make sure line data is available

    String ln = getLineTextForLongPress(line);
    int cursorLine = line;
    int charIndex = Math.max(0, Math.min(target.ch, ln.length()));
    float xLocal = editor.scroll.viewToTextX(e.getX());
    if (editor.wordWrap.isWordWrapEnabled) {
      int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
      int seg =
          editor.wordWrap.getWrapSegmentIndexForChar(
              starts, Math.max(0, Math.min(target.ch, ln.length())));
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      xLocal = xLocal + editor.textRender.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
    }

    float textWidth = editor.textRender.measureTextWithVisualSpaces(ln, 0, ln.length(), editor.textRender.paint);
    if (xLocal > textWidth) {
      // Correctly clear selection and sync state
      editor.selection.clearSelection();
      
      setCursorFromFoldLongPress(cursorLine, charIndex, ln);
      
      // Record anchor point so finger movement can start selection from here
      editor.selection.beginLongPressSelection(cursorLine, charIndex);
      editor.selection.state.longPressFreeForm = false; // Require movement threshold
      editor.selection.syncFromState();
      
      // Show minimal popup (Paste/Select All)
      editor.popup.showMinimalPopupAtCursor();
      
      editor.caret.resetBlink();
      editor.invalidate();
      editor.ime.showKeyboard();
      editor.view.restartInput();
      return;
    }

    // Set cursor position
    setCursorFromFoldLongPress(cursorLine, charIndex, ln);

    // Check if the long press is directly on a character (not on whitespace)
    boolean isOnText = false;
    if (ln != null && charIndex < ln.length()) {
      char c = ln.charAt(charIndex);
      isOnText = !Character.isWhitespace(c);
    }

    boolean isInsideSelection = editor.selection.isPositionInsideSelection(cursorLine, charIndex);

    // Only try smart selection if pressing directly on text OR inside an existing selection,
    // AND the finger hasn't moved yet.
    boolean smartSelected = false;
    if ((isOnText || isInsideSelection) && !editor.onTouch.movedSinceDown) {
      smartSelected = editor.selection.applySmartDoubleTapSelection(cursorLine, charIndex, ln);
    }

    // Always begin long press selection tracking so finger movement extends selection
    // The long press anchor is set to the touch point for free-form selection
    editor.selection.beginLongPressSelection(cursorLine, charIndex);

    // If smart selection was active, the selection is already set to the word/quote/bracket
    if (smartSelected) {
      editor.selection.state.longPressFreeForm = false; // Normal range selection cycle
      editor.selection.syncFromState();
      editor.popup.showPopupAtSelection();
    } else {
      // If long pressed on existing selection but no smart cycle was found (e.g. at end of cycle)
      // and we haven't moved yet, just show the popup.
      if (isInsideSelection && !editor.onTouch.movedSinceDown) {
        editor.selection.state.longPressFreeForm = false;
        editor.selection.syncFromState();
        editor.popup.showPopupAtSelection();
      } else {
        // No smart selection and not a stationary click on selection: start fresh drag selection
        // Clear any previous selection so long press drag starts fresh
        editor.selection.state.longPressFreeForm = true; // Drag selection
        editor.selection.syncFromState();
        if (editor.selection.hasSelection) {
          editor.selection.clearSelection();
        }
        editor.selection.selecting = true;
        // Show minimal popup with Paste and Select All options
        editor.popup.showMinimalPopupAtCursor();
      }
    }

    editor.caret.resetBlink();
    editor.invalidate();
    editor.ime.showKeyboard();
    editor.view.restartInput();
  }

  /**
   * Called when smart double tap selection fails - delegates to OnSingleTapUp
   */
  public void onSingleTapUpFallback(MotionEvent e, OnSingleTapUp onSingleTapUp) {
    onSingleTapUp.onSingleTapUp(e);
  }

  private String getLineTextForLongPress(int line) {
    String text = editor.windowRender.getLineTextForRender(line);
    if (text != null && !text.isEmpty()) return text;
    if (line < 0 || editor.fileIO.sourceFile == null) return text == null ? "" : text;
    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
  }

  private void setCursorFromFoldLongPress(int line, int col, String knownLineText) {
    int totalLines = editor.view.getLinesCount();
    int targetLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    int maxCol = knownLineText == null ? Math.max(0, col) : knownLineText.length();
    editor.cursor.cursorLine = targetLine;
    editor.cursor.cursorChar = Math.max(0, Math.min(col, maxCol));
    editor.caret.resetBlink();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
  }
}
