package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.SelectionAnimation;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;

/**
 * Manages selection state, appearance, and animation for SodiumEditor.
 */
public class SelectionState {

  // Selection state
  public boolean hasSelection = false;
  public int selStartLine = 0, selStartChar = 0;
  public int selEndLine = 0, selEndChar = 0;
  public boolean selecting = false;
  public boolean isSelectAllActive = false;
  public boolean isEntireFileSelected = false;

  // Selection appearance
  public int selectionColor = 0x6633B5E5;
  public int selectionHighlightColor = 0x8033B5E5;
  public int selectionHandleColor = 0xFF2196F3;

  // Selection paint and drawing objects
  public final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF selectionHighlightRect = new RectF();
  public final RectF selectionRectTmp = new RectF();
  public final Path selectionPathTmp = new Path();
  public final float[] selectionRadiiTmp = new float[8];

  // Line selection state
  public boolean isLineNumberSelecting = false;
  public int lineNumberSelectAnchorLine = -1;
  public boolean longPressSelecting = false;
  public boolean longPressFreeForm = false;
  public int longPressAnchorLine = -1;
  public int longPressAnchorChar = -1;
  public int longPressEndPointerId = -1;

  // Double tap selection state
  public int lastDoubleTapLine = -1;
  public int lastDoubleTapWordStart = -1;
  public int lastDoubleTapWordEnd = -1;
  public int lastDoubleTapStage = 0;

  // Copy/cut limits
  public static final long COPY_CUT_MAX_LINES = 20000L;
  public static final int COPY_CUT_MAX_CHARS = 8_000_000;
  public long copyCutMaxLines = COPY_CUT_MAX_LINES;
  public int copyCutMaxChars = COPY_CUT_MAX_CHARS;
  public int hideCopyCutMaxLines = 20000;
  public int replaceAllMaxCount = 100000;
  public boolean hideKeyboardOnFocusLoss = true;

  // Animation delegate
  public final SelectionAnimation animation;

  private final SodiumEditor editor;

  public SelectionState(SodiumEditor editor) {
    this.editor = editor;
    this.animation = new SelectionAnimation(editor);

    selectionPaint.setStyle(Paint.Style.FILL);
    selectionPaint.setColor(selectionColor);
    selectionPaint.setAlpha(102);
  }

  public float getSelectionAlpha() { return animation.selectionAlpha; }
  public float getHandleAlpha() { return animation.handleAlpha; }
  public boolean isSelectionAnimationEnabled() { return animation.selectionAnimationEnabled; }

  /**
   * Set selection range
   */
  public void setSelection(int startLine, int startChar, int endLine, int endChar) {
    selStartLine = startLine;
    selStartChar = startChar;
    selEndLine = endLine;
    selEndChar = endChar;
    hasSelection = !(selStartLine == selEndLine && selStartChar == selEndChar);
    selecting = false;

    // Reset handle animation state when selection changes
    editor.selectionHandles.animation.resetAnimationState();
  }

  /**
   * Clear selection
   */
  public void clearSelection() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
    longPressSelecting = false;
    longPressAnchorLine = -1;
    longPressAnchorChar = -1;
    longPressEndPointerId = -1;

    // Sync with cursor
    selStartLine = editor.cursor.cursorLine;
    selStartChar = editor.cursor.cursorChar;
    selEndLine = editor.cursor.cursorLine;
    selEndChar = editor.cursor.cursorChar;
    updateSelectionVisibility(false);
  }

  /**
   * Set selection internal (normalized)
   */
  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }
    selStartLine = startL;
    selStartChar = Math.max(0, startC);
    selEndLine = endL;
    selEndChar = Math.max(0, endC);
    hasSelection = !(selStartLine == selEndLine && selStartChar == selEndChar);
    selecting = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    updateSelectionVisibility(hasSelection);
  }

  /**
   * Compare two positions
   */
  public int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  /**
   * Check if position is within selection
   */
  public boolean contains(int line, int ch) {
    if (!hasSelection) return false;

    int sL = selStartLine, sC = selStartChar;
    int eL = selEndLine, eC = selEndChar;

    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    if (line < sL || line > eL) return false;
    if (line == sL && ch < sC) return false;
    if (line == eL && ch > eC) return false;

    return true;
  }

  /**
   * Check if copy/cut should be hidden for current selection
   */
  public boolean shouldHideCopyCutForSelection() {
    if (!hasSelection) return true;

    int sL = selStartLine, eL = selEndLine;
    if (sL > eL) {
      int t = sL;
      sL = eL;
      eL = t;
    }
    long lines = (long) eL - (long) sL + 1L;
    return lines > copyCutMaxLines;
  }

  /**
   * Get selection start line
   */
  public int getStartLine() { return selStartLine; }

  /**
   * Get selection start character
   */
  public int getStartChar() { return selStartChar; }

  /**
   * Get selection end line
   */
  public int getEndLine() { return selEndLine; }

  /**
   * Get selection end character
   */
  public int getEndChar() { return selEndChar; }

  /**
   * Get selection line count
   */
  public int getLineCount() {
    if (!hasSelection) return 0;
    return Math.abs(selEndLine - selStartLine) + 1;
  }

  /**
   * Check if selection is empty
   */
  public boolean isEmpty() {
    return !hasSelection || (selStartLine == selEndLine && selStartChar == selEndChar);
  }

  /**
   * Check if selection is active
   */
  public boolean hasSelection() { return hasSelection; }

  /**
   * Check if select all is active
   */
  public boolean isSelectAll() { return isSelectAllActive || isEntireFileSelected; }

  /**
   * Set selection color
   */
  public void setSelectionColor(int color) {
    selectionColor = color;
    selectionPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Set selection handle color
   */
  public void setSelectionHandleColor(int color) {
    selectionHandleColor = color;
  }

  /**
   * Set selection highlight color
   */
  public void setSelectionHighlightColor(int color) {
    if (this.selectionHighlightColor == color) return;
    this.selectionHighlightColor = color;
    if (hasSelection) editor.invalidate();
  }

  /**
   * Set selection animation enabled
   */
  public void setSelectionAnimationEnabled(boolean enabled) {
    animation.setSelectionAnimationEnabled(enabled);
  }

  /**
   * Update selection visibility
   */
  public void updateSelectionVisibility(boolean nowHasSelection) {
    animation.updateSelectionVisibility(nowHasSelection);
  }

  /**
   * Begin long press selection
   */
  public void beginLongPressSelection(int line, int ch) {
    longPressSelecting = true;
    longPressAnchorLine = Math.max(0, line);
    longPressAnchorChar = Math.max(0, ch);
  }

  /**
   * Update long press selection
   */
  public void updateLongPressSelection(int line, int ch) {
    if (!longPressSelecting) return;
    setSelectionInternal(longPressAnchorLine, longPressAnchorChar, line, ch);
    selecting = true;
  }

  /**
   * Update long press selection extending from current selection end
   * Used when smart selection was active before long press drag
   */
  public void updateLongPressSelectionFromSelectionEnd(int line, int ch) {
    if (!longPressSelecting) return;
    // Use the current selection end as the anchor point for extension
    setSelectionInternal(selStartLine, selStartChar, line, ch);
    selecting = true;
  }

  /**
   * End long press selection
   */
  public void endLongPressSelection() {
    longPressSelecting = false;
    selecting = false;
    longPressEndPointerId = -1;
  }

  /**
   * Clear selection state after delete
   */
  public void clearSelectionStateAfterDelete() {
    hasSelection = false;
    selecting = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    editor.caret.resetBlink();
  }

  /**
   * Clamp line for selection
   */
  public int clampLineForSelection(int line) {
    if (line < 0) return 0;
    if (editor.fileIO.isEof) {
      int last = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  /**
   * Check if line is selectable
   */
  public boolean isLineSelectable(int line) {
    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.textRender.getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }

  /**
   * Check if position is inside selection
   */
  public boolean isPositionInsideSelection(int line, int ch) {
    if (!hasSelection) return false;
    int sL = selStartLine;
    int sC = selStartChar;
    int eL = selEndLine;
    int eC = selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selEndLine;
      sC = selEndChar;
      eL = selStartLine;
      eC = selStartChar;
    }
    if (comparePos(line, ch, sL, sC) < 0) return false;
    return comparePos(line, ch, eL, eC) <= 0;
  }

  /**
   * Restore selection
   */
  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    int targetLine = Math.max(0, cursorLine);
    int targetChar = Math.max(0, editor.cursor.cursorChar);
    cursorLine = targetLine;
    if (cursorLine >= editor.textRender.windowStartLine
        && cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      String lineText = editor.textRender.getLineTextForRender(cursorLine);
      this.editor.cursor.cursorChar = Math.max(0, Math.min(targetChar, lineText.length()));
    } else {
      this.editor.cursor.cursorChar = targetChar;
    }
    editor.caret.resetBlink();
    editor.invalidate();
  }

  /**
   * Paste text from clipboard
   */
  public void pasteFromClipboard() {
    editor.fileIO.invalidatePendingIOForEdit();
    editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    android.content.ClipboardManager cm =
        (android.content.ClipboardManager) editor.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    android.content.ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(editor.getContext());
    if (txt == null) return;
    editor.editOperators.insertTextAtCursor(txt.toString());
    editor.autoCompletion.updateSuggestion();
  }
}
