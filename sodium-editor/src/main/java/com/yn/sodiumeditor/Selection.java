package com.yn.sodiumeditor;

   import android.graphics.RectF;
     import android.graphics.Path;
     import android.view.inputmethod.InputMethodManager;
     import android.content.Context;
/**
 * Selection handles text selection logic for SodiumEditor.
 * This includes:
 * - Selection state (start/end positions)
 * - Selection range operations
 * - Select all functionality
 */
public class Selection {

  // Selection state
  public boolean hasSelection = false;
  public int selStartLine = 0, selStartChar = 0;
  public int selEndLine = 0, selEndChar = 0;
  public boolean selecting = false;
  public boolean isSelectAllActive = false;
  public boolean isEntireFileSelected = false;
  
  // Selection appearance
  public int selectionColor = 0x4033B5E5;
  public int selectionHandleColor = 0xFF33B5E5;
  
  private final SodiumEditor sodiumeditor;
  private final Cursor cursor;
public final RectF selectionRectTmp = new RectF();
  public final Path selectionPathTmp = new Path();
  public final float[] selectionRadiiTmp = new float[8];
  
public boolean isLineNumberSelecting = false;
  public int lineNumberSelectAnchorLine = -1;
  
  // Selection state
        
         // Double tap selection state
       
       public int lastDoubleTapLine = -1;                              
         public int lastDoubleTapWordStart = -1;
      public int lastDoubleTapWordEnd = -1;
      public int lastDoubleTapStage = 0;
     
  public Selection(SodiumEditor sodiumeditor, Cursor cursor) {
    this.sodiumeditor = sodiumeditor;
    this.cursor = cursor;
  }

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
  }

  /**
   * Clear selection
   */
  public void clearSelection() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
    
    // Sync with cursor
    selStartLine = sodiumeditor.cursor.cursorLine;
    selStartChar = sodiumeditor.cursor.cursorChar;
    selEndLine = sodiumeditor.cursor.cursorLine;
    selEndChar = sodiumeditor.cursor.cursorChar;
  }

  /**
   * Select all text
   */
  public void selectAll() {
    sodiumeditor.clearActiveSuggestion(); // Clear suggestion when selecting all
    final boolean keyboardWasVisible = sodiumeditor.keyboardHeight > 0;
    if (sodiumeditor.isWordWrapEnabled) {
      // Free the IO thread from wrap rebuilds so select-all can jump to end quickly.
      int widthPx = Math.max(1, Math.round(sodiumeditor.getWrapWidth()));
      if (sodiumeditor.isWrapMetricsUsableForWindow(widthPx)) {
        sodiumeditor.cancelWrapWorkForPriority();
      }
    }
    sodiumeditor.setDisable(true);
    sodiumeditor.showLoadingCircle(true);

    isSelectAllActive = true;
    isEntireFileSelected = true;
    hasSelection = true;

    selStartLine = 0;
    selStartChar = 0;
    sodiumeditor.hidePopup();

    // =========================
    // In-memory mode (no file):
    // - Happens after "select all -> delete" (file cleared), then user types new text
    // - Also covers scenarios where content is edited but not persisted to disk
    // =========================
    if (sodiumeditor.sourceFile == null || sodiumeditor.isFileCleared) {
      synchronized (sodiumeditor.linesWindow) {
        if (sodiumeditor.linesWindow.isEmpty()) sodiumeditor.linesWindow.add("");
        // With no file backing, treat current window as the whole document.
        if (sodiumeditor.windowStartLine != 0) sodiumeditor.windowStartLine = 0;
        sodiumeditor.isEof = true;
      }

      selEndLine = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
      String lastLineText = sodiumeditor.getLineTextForRender(selEndLine);
      selEndChar = lastLineText.length();
      sodiumeditor.cursor.cursorLine = selEndLine;
      sodiumeditor.cursor.cursorChar = selEndChar;

      sodiumeditor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

      sodiumeditor.setDisable(false);
      sodiumeditor.showLoadingCircle(false);
      sodiumeditor.invalidate();
      sodiumeditor.requestFocus();
      sodiumeditor.showPopupAtSelection();

      sodiumeditor.post(
          () -> {
            sodiumeditor.requestFocus();
            if (keyboardWasVisible) sodiumeditor.showKeyboard();
            InputMethodManager imm =
                (InputMethodManager)
                    sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(sodiumeditor);
          });
      return;
    }

    // If we're already at EOF, we can select to the current visible logical end
    // without waiting for the index (important when user appended lines after EOF).
    if (sodiumeditor.isEof) {
      int windowLast = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
      selEndLine = windowLast;
      String lastLineText = sodiumeditor.getLineTextForRender(windowLast);
      selEndChar = lastLineText.length();
      sodiumeditor.cursor.cursorLine = windowLast;
      sodiumeditor.cursor.cursorChar = selEndChar;

      sodiumeditor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

      sodiumeditor.setDisable(false);
      sodiumeditor.showLoadingCircle(false);
      sodiumeditor.invalidate();
      sodiumeditor.requestFocus();
      sodiumeditor.showPopupAtSelection();

      sodiumeditor.post(
          () -> {
            sodiumeditor.requestFocus();
            if (keyboardWasVisible) sodiumeditor.showKeyboard();
            InputMethodManager imm =
                (InputMethodManager)
                    sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(sodiumeditor);
          });
      return;
    }

    // الأفضل: لو index جاهز نروح نهاية الملف بدقة (بدون قفزة غلط)
    Runnable goToEndUsingIndex =
        () -> {
          if (!sodiumeditor.isIndexReady || sodiumeditor.sourceFile == null) return;

          int fileLastLine;
          synchronized (sodiumeditor.lineOffsetsLock) {
            fileLastLine = Math.max(0, sodiumeditor.lineOffsets.length - 1);
          }

          // If the current window actually goes beyond file end (due to appended in-memory lines),
          // prefer the window end and DO NOT reload from file (reload would drop the appended
          // lines).
          if (sodiumeditor.isEof) {
            int windowLast = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
            if (windowLast > fileLastLine) {
              selEndLine = windowLast;
              String lastLineText = sodiumeditor.getLineTextForRender(windowLast);
              selEndChar = lastLineText.length();
              sodiumeditor.cursor.cursorLine = windowLast;
              sodiumeditor.cursor.cursorChar = selEndChar;

              sodiumeditor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

              sodiumeditor.setDisable(false);
              sodiumeditor.showLoadingCircle(false);
              sodiumeditor.invalidate();
              sodiumeditor.requestFocus();
              sodiumeditor.showPopupAtSelection();

              sodiumeditor.post(
                  () -> {
                    sodiumeditor.requestFocus();
                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                    InputMethodManager imm =
                        (InputMethodManager)
                            sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(sodiumeditor);
                  });
              return;
            }
          }

          selEndLine = fileLastLine;

          int targetStart = Math.max(0, fileLastLine - sodiumeditor.prefetchLines);

          sodiumeditor.loadWindowAround(
              targetStart,
              () ->
                  sodiumeditor.post(
                      () -> {
                        String lastLineText = sodiumeditor.getLineTextForRender(fileLastLine);
                        selEndChar = lastLineText.length();
                        sodiumeditor.cursor.cursorLine = fileLastLine;
                        sodiumeditor.cursor.cursorChar = selEndChar;

                        sodiumeditor.scroll.scrollToLineFastForSelectAll(fileLastLine, selEndChar);

                        sodiumeditor.setDisable(false);
                        sodiumeditor.showLoadingCircle(false);
                        sodiumeditor.invalidate();
                        sodiumeditor.requestFocus();
                        sodiumeditor.showPopupAtSelection();

                        sodiumeditor.post(
                            () -> {
                              sodiumeditor.requestFocus();
                              if (keyboardWasVisible) sodiumeditor.showKeyboard();
                              InputMethodManager imm =
                                  (InputMethodManager)
                                      sodiumeditor
                                          .getContext()
                                          .getSystemService(Context.INPUT_METHOD_SERVICE);
                              if (imm != null) imm.restartInput(sodiumeditor);
                            });
                      }));
        };

    if (sodiumeditor.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    // لو index مو جاهز: ابدأ بناءه ثم انتظر جاهزيته (بدل "قرب النهاية" الغلط)
    if (!sodiumeditor.isIndexBuilding && !sodiumeditor.isIndexDisabled) {
      sodiumeditor.ioHandler.post(sodiumeditor::buildFileIndex);
    }

    // نحدد selEndLine مؤقتاً للهايلايت بواسطة countTotalLines (سريع)
    sodiumeditor.countTotalLines(
        totalLines -> {
          int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
          selEndLine = Math.max(0, lastLine);

          Runnable goToEndWithoutIndex =
              () -> {
                int targetStart = Math.max(0, selEndLine - sodiumeditor.prefetchLines);
                sodiumeditor.loadWindowAround(
                    targetStart,
                    () ->
                        sodiumeditor.post(
                            () -> {
                              String lastLineText = sodiumeditor.getLineTextForRender(selEndLine);
                              selEndChar = lastLineText.length();
                              sodiumeditor.cursor.cursorLine = selEndLine;
                              sodiumeditor.cursor.cursorChar = selEndChar;

                              sodiumeditor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

                              sodiumeditor.setDisable(false);
                              sodiumeditor.showLoadingCircle(false);
                              sodiumeditor.invalidate();
                              sodiumeditor.requestFocus();
                              sodiumeditor.showPopupAtSelection();

                              sodiumeditor.post(
                                  () -> {
                                    sodiumeditor.requestFocus();
                                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                                    InputMethodManager imm =
                                        (InputMethodManager)
                                            sodiumeditor
                                                .getContext()
                                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.restartInput(sodiumeditor);
                                  });
                            }));
              };

          if (sodiumeditor.isIndexDisabled) {
            goToEndWithoutIndex.run();
            return;
          }

          final int ticket = sodiumeditor.editVersion.incrementAndGet();
          Runnable poll =
              new Runnable() {
                @Override
                public void run() {
                  if (ticket != sodiumeditor.editVersion.get()) return;

                  // Important: if file became unavailable (e.g. cleared and switched to memory),
                  // stop waiting to avoid infinite spinner.
                  if (sodiumeditor.sourceFile == null) {
                    sodiumeditor.setDisable(false);
                    sodiumeditor.showLoadingCircle(false);
                    sodiumeditor.invalidate();
                    sodiumeditor.showPopupAtSelection();
                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                    return;
                  }

                  if (sodiumeditor.isIndexDisabled) {
                    goToEndWithoutIndex.run();
                  } else if (sodiumeditor.isIndexReady) {
                    goToEndUsingIndex.run();
                  } else {
                    sodiumeditor.mainHandler.postDelayed(this, 80);
                  }
                }
              };
          sodiumeditor.mainHandler.post(poll);
        });
  }


  /**
   * Select word at cursor
   */
  public void selectWordAtCursor() {
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return;
    
    int pos = Math.max(0, Math.min(sodiumeditor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return;
    if (Character.isWhitespace(line.charAt(pos))) return;
    
    int[] bounds = sodiumeditor.computeWordBounds(line, pos);
    if (bounds != null && bounds[0] != bounds[1]) {
      setSelection(sodiumeditor.cursor.cursorLine, bounds[0], sodiumeditor.cursor.cursorLine, bounds[1]);
    }
  }

  /**
   * Select line at cursor
   */
  public void selectLineAtCursor() {
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null) return;
    
    setSelection(sodiumeditor.cursor.cursorLine, 0, sodiumeditor.cursor.cursorLine, line.length());
  }

  /**
   * Get selected text
   */
  public String getSelectedText() {
    if (!hasSelection) return null;
    
    int sL = selStartLine, sC = selStartChar;
    int eL = selEndLine, eC = selEndChar;
    
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    
    StringBuilder sb = new StringBuilder();
    for (int line = sL; line <= eL; line++) {
      String lineText = sodiumeditor.getLineTextForRender(line);
      if (lineText == null) lineText = "";
      
      int from = (line == sL) ? Math.max(0, Math.min(sC, lineText.length())) : 0;
      int to = (line == eL) ? Math.max(0, Math.min(eC, lineText.length())) : lineText.length();
      
      if (from < to) {
        sb.append(lineText, from, to);
      }
      if (line < eL) {
        sb.append('\n');
      }
    }
    
    return sb.toString();
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
   * Get selection start line
   */
  public int getStartLine() {
    return selStartLine;
  }

  /**
   * Get selection start character
   */
  public int getStartChar() {
    return selStartChar;
  }

  /**
   * Get selection end line
   */
  public int getEndLine() {
    return selEndLine;
  }

  /**
   * Get selection end character
   */
  public int getEndChar() {
    return selEndChar;
  }

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
   * Compare two positions
   */
  private int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  // Getters and Setters

  public void setSelectionColor(int color) {
    selectionColor = color;
  }

  public void setSelectionHandleColor(int color) {
    selectionHandleColor = color;
  }

  public boolean hasSelection() {
    return hasSelection;
  }

  public boolean isSelectAll() {
    return isSelectAllActive || isEntireFileSelected;
  }
}
