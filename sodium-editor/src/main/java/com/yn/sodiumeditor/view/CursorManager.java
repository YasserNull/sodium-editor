package com.yn.sodiumeditor.view;

final class CursorManager {
  private final SodiumEditorView view;

  int cursorLine = 0;
  int cursorChar = 0;

  CursorManager(SodiumEditorView view) {
    this.view = view;
  }

  int getLine() {
    return cursorLine;
  }

  int getChar() {
    return cursorChar;
  }

  void setLine(int line) {
    cursorLine = line;
  }

  void setChar(int ch) {
    cursorChar = ch;
  }

  void setPositionNoClear(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    cursorLine = targetLine;
    if (cursorLine >= view.windowStartLine && cursorLine < view.windowStartLine + view.linesWindow.size()) {
      String lineText = view.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorChar = targetCol;
    }
    view.resetCursorBlink();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.invalidate();
    view.updateImeSelectionForCursorManager();
  }

  void setPosition(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    if (view.hasSelectionForCursorManager()) {
      view.clearSelectionForCursorManager();
      view.hidePopup();
    }
    cursorLine = targetLine;
    if (cursorLine >= view.windowStartLine && cursorLine < view.windowStartLine + view.linesWindow.size()) {
      String lineText = view.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorChar = targetCol;
    }
    view.resetCursorBlink();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.invalidate();
  }
}
