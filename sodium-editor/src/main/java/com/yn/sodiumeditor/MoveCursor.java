package com.yn.sodiumeditor;

/**
 * Manages cursor movement for the SodiumEditor.
 * Handles moving the cursor left, right, up, and down.
 */
public class MoveCursor {

  private final SodiumEditor editor;

  public MoveCursor(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Moves the cursor left.
   * If selection is active, moves to the start of selection.
   * If at beginning of line, moves to end of previous line.
   */
  public void moveCursorLeft() {
    editor.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int sL = editor.selection.selStartLine, sC = editor.selection.selStartChar;
      if (editor.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        sL = editor.selection.selEndLine;
        sC = editor.selection.selEndChar;
      }
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    } else if (editor.cursor.cursorChar > 0) {
      editor.cursor.cursorChar--;
    } else if (editor.cursor.cursorLine > 0) {
      editor.cursor.cursorLine--;
      String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = ln.length();
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.invalidate();
    editor.keepCursorVisibleHorizontally();
    editor.updateSuggestion();
  }

  /**
   * Moves the cursor right.
   * If selection is active, moves to the end of selection.
   * If at end of line, moves to beginning of next line.
   */
  public void moveCursorRight() {
    editor.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int eL = editor.selection.selEndLine, eC = editor.selection.selEndChar;
      if (editor.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        eL = editor.selection.selStartLine;
        eC = editor.selection.selStartChar;
      }
      editor.cursor.cursorLine = eL;
      editor.cursor.cursorChar = eC;
    } else {
      String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
      if (editor.cursor.cursorChar < ln.length()) {
        editor.cursor.cursorChar++;
      } else {
        int next = editor.cursor.cursorLine + 1;
        if (!editor.isEof || next < editor.windowStartLine + editor.linesWindow.size()) {
          editor.cursor.cursorLine = next;
          editor.cursor.cursorChar = 0;
        }
      }
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.invalidate();
    editor.keepCursorVisibleHorizontally();
    editor.updateSuggestion();
  }

  /**
   * Moves the cursor up one line.
   * If selection is active, moves to the start of selection.
   * Maintains column position when possible.
   */
  public void moveCursorUp() {
    editor.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int sL = editor.selection.selStartLine, sC = editor.selection.selStartChar;
      if (editor.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        sL = editor.selection.selEndLine;
        sC = editor.selection.selEndChar;
      }
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    }
    
    if (editor.cursor.cursorLine > 0) {
      editor.cursor.cursorLine--;
      String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.invalidate();
    editor.keepCursorVisibleHorizontally();
    editor.updateSuggestion();
  }

  /**
   * Moves the cursor down one line.
   * If selection is active, moves to the end of selection.
   * Maintains column position when possible.
   */
  public void moveCursorDown() {
    editor.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int eL = editor.selection.selEndLine, eC = editor.selection.selEndChar;
      if (editor.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        eL = editor.selection.selStartLine;
        eC = editor.selection.selStartChar;
      }
      editor.cursor.cursorLine = eL;
      editor.cursor.cursorChar = eC;
    }
    
    int next = editor.cursor.cursorLine + 1;
    if (!editor.isEof || next < editor.windowStartLine + editor.linesWindow.size()) {
      editor.cursor.cursorLine = next;
      String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.invalidate();
    editor.keepCursorVisibleHorizontally();
    editor.updateSuggestion();
  }
}
