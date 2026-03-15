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
    if (sodiumeditor.codeFold.isCodeFoldingEnabled && sodiumeditor.lineNumber.isInLineNumberGutter(e.getX())) {
      float gy = e.getY() + sodiumeditor.scroll.scrollY;
      int line = sodiumeditor.getGlobalLineForY(gy);
      if (sodiumeditor.codeFold.toggleFoldAtLine(line)) {
        sodiumeditor.codeFold.startFoldMarkerRipple(line);
        sodiumeditor.popup.hidePopup();
        sodiumeditor.invalidate();
        return true;
      }
    }
    float y = e.getY() + sodiumeditor.scroll.scrollY;
    int visibleIndex = Math.max(0, (int) (y / sodiumeditor.lineHeight));
    int totalVisible =
        sodiumeditor.wordWrap.isWordWrapEnabled ? sodiumeditor.getTotalVisualLineCount() : sodiumeditor.codeFold.getVisibleLineCount();
    SodiumEditor.CursorTarget target = sodiumeditor.getCursorTargetForPosition(e.getX(), e.getY(), null);
    int line = target.line;

    if (sodiumeditor.codeFold.isCodeFoldingEnabled) {
      String ln = sodiumeditor.getLineTextForRender(line);
      float xLocal = sodiumeditor.viewToTextX(e.getX());
      float x;
      if (sodiumeditor.wordWrap.isWordWrapEnabled) {
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
        if (sodiumeditor.codeFold.toggleFoldAtLine(line)) {
          sodiumeditor.codeFold.startFoldMarkerRipple(line);
        }
        sodiumeditor.popup.hidePopup();
        sodiumeditor.invalidate();
        return true;
      }
    }

    boolean afterEnd = sodiumeditor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible);

    if (afterEnd) {
      if (!sodiumeditor.clickAfterEndToAddLine.handleClickAfterEnd(visibleIndex, totalVisible)) {
        sodiumeditor.clickAfterEndToAddLine.handleDefaultAfterEnd();
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
