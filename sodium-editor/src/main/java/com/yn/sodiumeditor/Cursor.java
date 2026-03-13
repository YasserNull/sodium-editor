package com.yn.sodiumeditor;

/**
 * Cursor handles cursor state and position for SodiumEditor.
 * This includes:
 * - Cursor position (line and character)
 * - Cursor state tracking
 */
public class Cursor {

  // Cursor position
  public int cursorLine = 0;
  public int cursorChar = 0;

  private final SodiumEditor sodiumeditor;

  public Cursor(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Set cursor position
   */
  public void setCursorPosition(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    if (sodiumeditor.selection.hasSelection) {
      sodiumeditor.selection.hasSelection = false;
      sodiumeditor.selection.isSelectAllActive = false;
      sodiumeditor.selection.isEntireFileSelected = false;
      sodiumeditor.selection.selecting = false;
      sodiumeditor.popup.hidePopup();
    }
    cursorLine = targetLine;
    if (cursorLine >= sodiumeditor.windowStartLine && cursorLine < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size()) {
      String lineText = sodiumeditor.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorChar = targetCol;
    }
    sodiumeditor.caret.resetBlink();
    sodiumeditor.keepCursorVisibleHorizontally();
    sodiumeditor.invalidate();
  }

  /**
   * Move cursor to line
   */
  public void moveToLine(int line) {
    cursorLine = line;
  }

  /**
   * Move cursor to character position
   */
  public void moveToChar(int ch) {
    cursorChar = ch;
  }

  /**
   * Clamp cursor to valid document bounds
   */
  public void clampToDocument() {
    int totalLines = sodiumeditor.getLinesCount();
    if (totalLines <= 0) {
      cursorLine = 0;
      cursorChar = 0;
      return;
    }
    
    cursorLine = Math.max(0, Math.min(cursorLine, totalLines - 1));
    
    String lineText = sodiumeditor.getLineTextForRender(cursorLine);
    if (lineText != null) {
      cursorChar = Math.max(0, Math.min(cursorChar, lineText.length()));
    } else {
      cursorChar = 0;
    }
  }

  /**
   * Get cursor line
   */
  public int getLine() {
    return cursorLine;
  }

  /**
   * Get cursor character
   */
  public int getChar() {
    return cursorChar;
  }

  /**
   * Reset cursor to beginning
   */
  public void reset() {
    cursorLine = 0;
    cursorChar = 0;
  }

  /**
   * Check if cursor is at end of line
   */
  public boolean isAtEndOfLine() {
    String lineText = sodiumeditor.getLineTextForRender(cursorLine);
    return lineText == null || cursorChar >= lineText.length();
  }

  /**
   * Check if cursor is at start of line
   */
  public boolean isAtStartOfLine() {
    return cursorChar <= 0;
  }

  /**
   * Check if cursor is at end of document
   */
  public boolean isAtEndOfDocument() {
    int totalLines = sodiumeditor.getLinesCount();
    if (totalLines <= 0) return true;
    
    if (cursorLine < totalLines - 1) return false;
    
    String lineText = sodiumeditor.getLineTextForRender(cursorLine);
    return lineText == null || cursorChar >= lineText.length();
  }

  /**
   * Check if cursor is at start of document
   */
  public boolean isAtStartOfDocument() {
    return cursorLine <= 0 && cursorChar <= 0;
  }
}
