package com.yn.sodiumeditor.core.cursor;
import android.graphics.Rect;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * Cursor handles cursor state and position for SodiumEditor.
 * This includes:
 * - Cursor position (line and character)
 * - Cursor state tracking
 */
public class Cursor {

  public float cursorWidth = 6f;
public float baseCursorWidthPx = cursorWidth;
  public float baseCursorTextSizePx = 0f;
  // Cursor position
  public int cursorLine = 0;
  public int cursorChar = 0;

  private final SodiumEditor editor;

  public Cursor(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Set cursor position
   */
  public void setCursorPosition(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      editor.selection.selecting = false;
      editor.popup.hidePopup();
    }
    cursorLine = targetLine;
    if (cursorLine >= editor.textRender.windowStartLine && cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      String lineText = editor.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        cursorChar = editor.binaryRender.snapBinaryCursor(lineText, cursorChar, cursorLine);
      }
    } else {
      cursorChar = targetCol;
    }
    editor.caret.resetBlink();
    editor.keepCursorVisibleHorizontally();
    editor.invalidate();
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
    int totalLines = editor.getLinesCount();
    if (totalLines <= 0) {
      cursorLine = 0;
      cursorChar = 0;
      return;
    }
    
    cursorLine = Math.max(0, Math.min(cursorLine, totalLines - 1));
    
    String lineText = editor.getLineTextForRender(cursorLine);
    if (lineText != null) {
      cursorChar = Math.max(0, Math.min(cursorChar, lineText.length()));
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        cursorChar = editor.binaryRender.snapBinaryCursor(lineText, cursorChar, cursorLine);
      }
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
    String lineText = editor.getLineTextForRender(cursorLine);
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
    int totalLines = editor.getLinesCount();
    if (totalLines <= 0) return true;
    
    if (cursorLine < totalLines - 1) return false;
    
    String lineText = editor.getLineTextForRender(cursorLine);
    return lineText == null || cursorChar >= lineText.length();
  }

  /**
   * Check if cursor is at start of document
   */
  public boolean isAtStartOfDocument() {
    return cursorLine <= 0 && cursorChar <= 0;
  }
    /**
   * Moves the cursor left.
   * If selection is active, moves to the start of selection.
   * If at beginning of line, moves to end of previous line.
   */
  public void moveCursorLeft() {
    editor.autoCompletion.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int sL = editor.selection.selStartLine, sC = editor.selection.selStartChar;
      if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        sL = editor.selection.selEndLine;
        sC = editor.selection.selEndChar;
      }
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    } else if (editor.cursor.cursorChar > 0) {
      editor.cursor.cursorChar--;
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
        int[] span = new int[2];
        if (editor.binaryRender.findBinaryTokenSpanInSpans(
                editor.binaryRender.getBinaryTokenSpans(editor.cursor.cursorLine),
                editor.cursor.cursorChar,
                span)) {
          editor.cursor.cursorChar = span[0];
        }
      }
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
    editor.autoCompletion.updateSuggestion();
  }

  /**
   * Moves the cursor right.
   * If selection is active, moves to the end of selection.
   * If at end of line, moves to beginning of next line.
   */
  public void moveCursorRight() {
    editor.autoCompletion.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int eL = editor.selection.selEndLine, eC = editor.selection.selEndChar;
      if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
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
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          int[] span = new int[2];
          if (editor.binaryRender.findBinaryTokenSpanInSpans(
                  editor.binaryRender.getBinaryTokenSpans(editor.cursor.cursorLine),
                  editor.cursor.cursorChar,
                  span)) {
            editor.cursor.cursorChar = span[1];
          }
        }
      } else {
        int next = editor.cursor.cursorLine + 1;
        if (!editor.fileIO.isEof || next < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
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
    editor.autoCompletion.updateSuggestion();
  }

  /**
   * Moves the cursor up one line.
   * If selection is active, moves to the start of selection.
   * Maintains column position when possible.
   */
  public void moveCursorUp() {
    editor.autoCompletion.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int sL = editor.selection.selStartLine, sC = editor.selection.selStartChar;
      if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
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
    editor.autoCompletion.updateSuggestion();
  }

  /**
   * Moves the cursor down one line.
   * If selection is active, moves to the end of selection.
   * Maintains column position when possible.
   */
  public void moveCursorDown() {
    editor.autoCompletion.clearActiveSuggestion();
    
    if (editor.selection.hasSelection) {
      int eL = editor.selection.selEndLine, eC = editor.selection.selEndChar;
      if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, 
          editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
        eL = editor.selection.selStartLine;
        eC = editor.selection.selStartChar;
      }
      editor.cursor.cursorLine = eL;
      editor.cursor.cursorChar = eC;
    }
    
    int next = editor.cursor.cursorLine + 1;
    if (!editor.fileIO.isEof || next < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
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
    editor.autoCompletion.updateSuggestion();
  }

  // ========================================================================
  // Cursor Helper Methods
  // ========================================================================

  /**
   * Set cursor position without clearing caches
   */
  public void setCursorPositionNoClear(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    cursorLine = targetLine;
    if (cursorLine >= editor.textRender.windowStartLine && cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      String lineText = editor.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorChar = targetCol;
    }
    editor.caret.resetBlink();
    editor.keepCursorVisibleHorizontally();
    editor.invalidate();
    editor.ime.updateImeSelection();
  }

  /**
   * Invalidate cursor area for redraw
   */
  public void invalidateCursorArea() {
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.invalidate();
      return;
    }
    int idx = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(cursorLine) : cursorLine;
    float top = (idx * editor.textRender.lineHeight) - editor.scroll.scrollY;
    editor.invalidate(0, (int) Math.floor(top), editor.getWidth(), (int) Math.ceil(top + editor.textRender.lineHeight));
  }

}
