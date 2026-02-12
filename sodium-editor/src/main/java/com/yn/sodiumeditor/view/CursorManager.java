package com.yn.sodiumeditor.view;

final class CursorManager {
  private final SodiumEditorView view;

  private int cursorLine = 0;
  private int cursorChar = 0;

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

  void moveCharDelta(int delta) {
    cursorChar = Math.max(0, cursorChar + delta);
  }

  void clampCharToLineLength(int line) {
    String lineText = view.getLineTextForRender(line);
    if (lineText != null) {
      cursorChar = Math.min(cursorChar, lineText.length());
    }
  }

  void setLineAndChar(int line, int ch) {
    cursorLine = Math.max(0, line);
    cursorChar = Math.max(0, ch);
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
    view.cursorAnimationManager.resetCursorBlink();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.invalidate();
    view.imeManager.updateImeSelection();
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
    view.cursorAnimationManager.resetCursorBlink();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.invalidate();
    view.imeManager.updateImeSelection();
  }
}
