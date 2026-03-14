package com.yn.sodiumeditor.Input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * OnSingleTapUp handles onSingleTapUp() gesture event for SodiumEditor.
 */
public class OnSingleTapUp {

  private final SodiumEditor sodiumeditor;

  public OnSingleTapUp(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Handle onSingleTapUp event
   */
  public boolean onSingleTapUp(MotionEvent e) {
    if (sodiumeditor.suggestionAcceptedThisTouch) return true;
    if (sodiumeditor.multiTouchActive || sodiumeditor.hadMultiTouch) return true;

    if (sodiumeditor.selection.hasSelection) {
      sodiumeditor.selection.hasSelection = false;
      sodiumeditor.selection.isSelectAllActive = false;
      sodiumeditor.selection.isEntireFileSelected = false;
    }
    if (sodiumeditor.isCodeFoldingEnabled && sodiumeditor.lineNumber.isInLineNumberGutter(e.getX())) {
      float gy = e.getY() + sodiumeditor.scroll.scrollY;
      int line = sodiumeditor.getGlobalLineForY(gy);
      if (sodiumeditor.toggleFoldAtLine(line)) {
        sodiumeditor.startFoldMarkerRipple(line);
        sodiumeditor.popup.hidePopup();
        sodiumeditor.invalidate();
        return true;
      }
    }
    float y = e.getY() + sodiumeditor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / sodiumeditor.lineHeight));
    int totalVisible =
        sodiumeditor.isWordWrapEnabled ? sodiumeditor.getTotalVisualLineCount() : sodiumeditor.getVisibleLineCount();
    SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (sodiumeditor.isCodeFoldingEnabled) {
      String ln = sodiumeditor.getLineTextForRender(line);
      float xLocal = sodiumeditor.viewToTextX(e.getX());
      float x;
      if (sodiumeditor.isWordWrapEnabled) {
        int[] starts = sodiumeditor.getWrapStartsForLine(line, ln);
        int seg =
            sodiumeditor.getWrapSegmentIndexForChar(
                starts, Math.max(0, Math.min(target.ch, ln.length())));
        int segStart = sodiumeditor.getWrapSegmentStart(starts, seg);
        x = xLocal + sodiumeditor.measureTextWithVisualSpaces(ln, 0, segStart, sodiumeditor.paint);
      } else {
        x = xLocal;
      }
      if (sodiumeditor.isFoldPlaceholderHit(line, ln, x)) {
        if (sodiumeditor.toggleFoldAtLine(line)) {
          sodiumeditor.startFoldMarkerRipple(line);
        }
        sodiumeditor.popup.hidePopup();
        sodiumeditor.invalidate();
        return true;
      }
    }

    boolean afterEnd =
        sodiumeditor.isEof && line >= sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() && !sodiumeditor.linesWindow.isEmpty();
    if (sodiumeditor.isCodeFoldingEnabled) {
      afterEnd = visibleIndex >= totalVisible;
    }

    if (afterEnd) {
      if (sodiumeditor.isClickAfterEndToAddLineEnabled) {
        int lastLineIndex = sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1;
        // Only add a new line if the user taps exactly on the first empty line after
        // the text
        if (visibleIndex == totalVisible) {
          sodiumeditor.cursor.cursorLine = lastLineIndex;
          String lastLineText = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
          sodiumeditor.cursor.cursorChar = lastLineText.length();
          sodiumeditor.insertTextAtCursor("\n");
        } else {
          // If tapped further down, just move cursor to end of text without adding
          // lines
          sodiumeditor.cursor.cursorLine = lastLineIndex;
          String lastLineText = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
          sodiumeditor.cursor.cursorChar = lastLineText.length();
        }
      } else {
        sodiumeditor.cursor.cursorLine = sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1;
        String lastLineText = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
        sodiumeditor.cursor.cursorChar = lastLineText.length();
      }
    } else {
      sodiumeditor.ensureLineInWindow(line, true);
      String ln = sodiumeditor.getLineTextForRender(line);
      sodiumeditor.cursor.cursorLine = line;
      sodiumeditor.cursor.cursorChar = Math.max(0, Math.min(target.ch, ln.length()));
    }

    sodiumeditor.popup.hidePopup();
    sodiumeditor.selection.selecting = false;
    sodiumeditor.invalidate();
    sodiumeditor.caret.resetBlink();
    sodiumeditor.showKeyboard();
    sodiumeditor.restartInput();
    sodiumeditor.updateSuggestion();
    return true;
  }
}
