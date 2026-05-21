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
   public void setCursorWidth(float width) {
    if (baseCursorWidthPx == width && baseCursorTextSizePx == editor.textRender.paint.getTextSize()) return;
    baseCursorWidthPx = width;
    baseCursorTextSizePx = editor.textRender.paint.getTextSize();
    editor.windowRender.recalculateMaxLineWidth();
    editor.invalidate();
  }
  public void setCursorPosition(int line, int col) {
    int totalLines = editor.view.getLinesCount();
    int maxLine = Math.max(0, totalLines - 1);
    int targetLine = Math.max(0, Math.min(line, maxLine));
    int targetCol = Math.max(0, col);
    if (editor.selection.hasSelection) {
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      editor.selection.selecting = false;
    }
    cursorLine = targetLine;
    if (cursorLine >= editor.windowRender.windowStartLine && cursorLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
      String lineText = editor.windowRender.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        cursorChar = editor.binaryRender.snapBinaryCursor(lineText, cursorChar, cursorLine);
      }
    } else {
      cursorChar = targetCol;
    }
    editor.caret.resetBlink();
    editor.scroll.keepCursorVisibleHorizontally();
    // Trigger cursor animation
    editor.cursor.invalidateCursorArea();
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
    int totalLines = editor.view.getLinesCount();
    if (totalLines <= 0) {
      cursorLine = 0;
      cursorChar = 0;
      return;
    }
    
    cursorLine = Math.max(0, Math.min(cursorLine, totalLines - 1));
    
    String lineText = editor.windowRender.getLineTextForRender(cursorLine);
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
    String lineText = editor.windowRender.getLineTextForRender(cursorLine);
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
    int totalLines = editor.view.getLinesCount();
    if (totalLines <= 0) return true;
    
    if (cursorLine < totalLines - 1) return false;
    
    String lineText = editor.windowRender.getLineTextForRender(cursorLine);
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
      String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
      editor.cursor.cursorChar = safeChar > 0 ? ln.offsetByCodePoints(safeChar, -1) : 0;
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        int[] span = new int[2];
        if (editor.binaryRender.findBinaryTokenSpanInSpans(
                editor.binaryRender.getBinaryTokenSpans(editor.cursor.cursorLine),
                editor.cursor.cursorChar,
                span)) {
          editor.cursor.cursorChar = span[0];
        }
      }
      skipForbiddenBracePositions(false);
    } else if (editor.cursor.cursorLine > 0) {
      editor.cursor.cursorLine--;
      String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = ln.length();
      skipForbiddenBracePositions(false);
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.cursor.invalidateCursorArea();
    editor.scroll.keepCursorVisibleHorizontally();
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
      String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      if (ln == null) ln = "";
      if (editor.cursor.cursorChar < ln.length()) {
        int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
        editor.cursor.cursorChar = ln.offsetByCodePoints(safeChar, 1);
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          int[] span = new int[2];
          if (editor.binaryRender.findBinaryTokenSpanInSpans(
                  editor.binaryRender.getBinaryTokenSpans(editor.cursor.cursorLine),
                  editor.cursor.cursorChar,
                  span)) {
            editor.cursor.cursorChar = span[1];
          }
        }
        skipForbiddenBracePositions(true);
      } else {
        int next = editor.cursor.cursorLine + 1;
        if (!editor.fileIO.isEof || next < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
          editor.cursor.cursorLine = next;
          editor.cursor.cursorChar = 0;
          skipForbiddenBracePositions(true);
        }
      }
    }
    
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.cursor.invalidateCursorArea();
    editor.scroll.keepCursorVisibleHorizontally();
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

    if (editor.codeFold.isCodeFoldingEnabled && !editor.wordWrap.isWordWrapEnabled) {
      int visible = editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
      int targetVisible = visible - 1;
      if (targetVisible >= 0) {
        int targetLine = editor.codeFold.mapVisibleIndexToGlobal(targetVisible);
        editor.cursor.cursorLine = targetLine;
        editor.fileIO.ensureLineInWindow(targetLine, true);
        String ln = editor.windowRender.getLineTextForRender(targetLine);
        editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
      }
    } else if (editor.cursor.cursorLine > 0) {
      editor.cursor.cursorLine--;
      String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
    }

    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.cursor.invalidateCursorArea();
    editor.scroll.keepCursorVisibleHorizontally();
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

    if (editor.codeFold.isCodeFoldingEnabled && !editor.wordWrap.isWordWrapEnabled) {
      int visible = editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
      int targetVisible = visible + 1;
      if (targetVisible < editor.codeFold.getVisibleLineCount()) {
        int targetLine = editor.codeFold.mapVisibleIndexToGlobal(targetVisible);
        editor.cursor.cursorLine = targetLine;
        editor.fileIO.ensureLineInWindow(targetLine, true);
        String ln = editor.windowRender.getLineTextForRender(targetLine);
        editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
        skipForbiddenBracePositions(true);
      }
    } else {
      int next = editor.cursor.cursorLine + 1;
      if (!editor.fileIO.isEof || next < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
        editor.cursor.cursorLine = next;
        String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
        editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
        skipForbiddenBracePositions(true);
      }
    }

    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.caret.resetBlink();
    editor.cursor.invalidateCursorArea();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.autoCompletion.updateSuggestion();
  }

  // ========================================================================
  // Cursor Helper Methods
  // ========================================================================

  /**
   * Set cursor position without clearing caches
   */
  public void setCursorPositionNoClear(int line, int col) {
    int totalLines = editor.view.getLinesCount();
    int maxLine = Math.max(0, totalLines - 1);
    int targetLine = Math.max(0, Math.min(line, maxLine));
    int targetCol = Math.max(0, col);
    cursorLine = targetLine;
    if (cursorLine >= editor.windowRender.windowStartLine && cursorLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
      String lineText = editor.windowRender.getLineTextForRender(cursorLine);
      cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorChar = targetCol;
    }
    editor.caret.resetBlink();
    editor.scroll.keepCursorVisibleHorizontally();
    // Trigger cursor animation
    editor.cursor.invalidateCursorArea();
    editor.ime.updateImeSelection();
  }

  private void skipForbiddenBracePositions(boolean movingRight) {
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) return;
    String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) return;
    int len = ln.length();
    if (len == 0) return;
    int guard = 0;
    while (guard < 4) {
      // Only forbid the exact middle position of an empty pair "{}"
      boolean inEmptyPair = editor.cursor.cursorChar > 0
          && editor.cursor.cursorChar < len
          && ln.charAt(editor.cursor.cursorChar - 1) == '{'
          && ln.charAt(editor.cursor.cursorChar) == '}';
      if (!inEmptyPair) break;
      if (movingRight) {
        if (editor.cursor.cursorChar < len) {
          editor.cursor.cursorChar++;
        } else {
          break;
        }
      } else {
        if (editor.cursor.cursorChar > 0) {
          editor.cursor.cursorChar--;
        } else {
          break;
        }
      }
      guard++;
    }
  }

  /**
   * Invalidate cursor area for redraw
   */
  public void invalidateCursorArea() {
    RectF oldHandleRect = new RectF(editor.cursorHandle.cursorHandleRect);
    // Update animation target immediately regardless of current state
    // The animation system now handles redirection internally.
    float targetX = editor.caret.getCaretDocumentX();
    float targetY = editor.caret.getCaretDocumentY();
    editor.cursorAnimation.updateCursorDrawPosition(targetX, targetY);
    editor.cursorHandle.updateCursorHandlePosition();
    
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.invalidate();
      return;
    }
    int idx = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(cursorLine) : cursorLine;
    float top = (idx * editor.textRender.lineHeight) - editor.scroll.scrollY;
    Rect dirty = new Rect(
            0,
            (int) Math.floor(top),
            editor.getWidth(),
            (int) Math.ceil(top + editor.textRender.lineHeight));
    dirty.union(
            (int) Math.floor(oldHandleRect.left),
            (int) Math.floor(oldHandleRect.top),
            (int) Math.ceil(oldHandleRect.right),
            (int) Math.ceil(oldHandleRect.bottom));
    RectF newHandleRect = editor.cursorHandle.cursorHandleRect;
    dirty.union(
            (int) Math.floor(newHandleRect.left),
            (int) Math.floor(newHandleRect.top),
            (int) Math.ceil(newHandleRect.right),
            (int) Math.ceil(newHandleRect.bottom));
    editor.invalidate(dirty);
  }

}
