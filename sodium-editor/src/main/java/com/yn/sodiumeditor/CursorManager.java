package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.core.EditOp;

public final class CursorManager {
  private final SodiumEditorView view;
  private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private int cursorLine = 0;
  private int cursorChar = 0;

  // Composing (IME) state
  boolean hasComposing = false;
  int composingLine = 0, composingOffset = 0, composingLength = 0;
  int composingStartLine = -1;
  int composingStartChar = 0;
  boolean composingStartActive = false;

  CursorManager(SodiumEditorView view) {
    this.view = view;
    caretPaint.setStyle(Paint.Style.STROKE);
    caretPaint.setStrokeCap(Paint.Cap.BUTT);
  }

  public int getLine() {
    return cursorLine;
  }

  public int getChar() {
    return cursorChar;
  }

  void setLine(int line) {
    cursorLine = line;
  }

  public void setChar(int ch) {
    cursorChar = ch;
  }

  public void moveCharDelta(int delta) {
    cursorChar = Math.max(0, cursorChar + delta);
  }

  public void clampCharToLineLength(int line) {
    String lineText = view.getLineTextForRender(line);
    if (lineText != null) {
      cursorChar = Math.min(cursorChar, lineText.length());
    }
  }

  public void setLineAndChar(int line, int ch) {
    cursorLine = Math.max(0, line);
    cursorChar = Math.max(0, ch);
  }

  // Getters for composing state
  public boolean getHasComposing() {
    return hasComposing;
  }

  public int getComposingLine() {
    return composingLine;
  }

  public int getComposingOffset() {
    return composingOffset;
  }

  public int getComposingLength() {
    return composingLength;
  }

  public int getComposingStartLine() {
    return composingStartLine;
  }

  public int getComposingStartChar() {
    return composingStartChar;
  }

  public boolean getComposingStartActive() {
    return composingStartActive;
  }

  // Setters for composing state
  public void setHasComposing(boolean value) {
    hasComposing = value;
  }

  public void setComposingLine(int value) {
    composingLine = value;
  }

  public void setComposingOffset(int value) {
    composingOffset = value;
  }

  public void setComposingLength(int value) {
    composingLength = value;
  }

  public void setComposingStartLine(int value) {
    composingStartLine = value;
  }

  public void setComposingStartChar(int value) {
    composingStartChar = value;
  }

  public void setComposingStartActive(boolean value) {
    composingStartActive = value;
  }

  public void deleteComposing() {
    if (!hasComposing) return;
    this.replaceComposingWith(""); // This will also update composingLength and hasComposing
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    view.charAnimationManager.clearLastComposingTextForCharAnim();
  }

  public void commitComposing(boolean keepInText) {
    if (!hasComposing) return;
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    view.clearComposingPendingOpPublic();
    view.charAnimationManager.clearLastComposingTextForCharAnim();
    view.invalidate();
    view.autoSuggestionManager.updateSuggestion();
  }

  public void replaceComposingWith(CharSequence textSeq) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEditPublic();
    view.incrementEditVersionPublic();

    view.scrollManager.ensureLineInWindow(composingLine, true);
    if (view.isWindowLoading
        && (composingLine < view.windowStartLine || composingLine >= view.windowStartLine + view.linesWindow.size())) {
      view.post(() -> replaceComposingWith(textSeq));
      return;
    }
    int local = composingLine - view.windowStartLine;
    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(local);
      if (base == null) base = "";
      int start = Math.max(0, Math.min(composingOffset, base.length()));
      int end = Math.max(0, Math.min(composingOffset + composingLength, base.length()));
      if (view.charAnimationManager.isEnabled()) {
        String oldComposing = base.substring(start, end);
        String newComposing = (textSeq == null) ? "" : textSeq.toString();
        if (newComposing.length() < oldComposing.length()) {
          String removed = null;
          int at = start;
          if (oldComposing.startsWith(newComposing)) {
            removed = oldComposing.substring(newComposing.length());
            at = start + newComposing.length();
          } else if (oldComposing.endsWith(newComposing)) {
            removed = oldComposing.substring(0, oldComposing.length() - newComposing.length());
            at = start;
          }

          if (removed != null && !removed.isEmpty()) {
            Paint p = view.highlightManager.getPaintForChar(composingLine, at, base);
            view.charAnimationManager.startDeleteAnimation(composingLine, at, removed, p);
          }
        }
      }
      String newLine = base.substring(0, start) + textSeq + base.substring(end);
      view.updateLocalLinePublic(local, newLine);
      view.modifiedLines.put(composingLine, newLine);
      composingLength = textSeq.length();
      setLineAndChar(composingLine, composingOffset + composingLength);
      view.computeWidthForLinePublic(composingLine, newLine);
      view.recalculateMaxLineWidth();
      view.invalidate();
    }
    view.autoSuggestionManager.updateSuggestion();
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

  public void setPosition(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
          if (view.selectionManager.hasSelection()) {      view.selectionManager.clearSelectionKeepLineNumberState();
      view.popupMenuManager.hidePopup();
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

  public void drawCaret(Canvas canvas, float cursorX, float cursorY) {
    view.cursorAnimationManager.updateCursorDrawPosition(cursorX, cursorY);
    float drawX = view.cursorAnimationManager.getCursorDrawX();
    float drawY = view.cursorAnimationManager.getCursorDrawY();
    if (view.cursorAnimationManager.isCursorVisible()) {
      HandlesManager handles = view.getHandlesManagerForCursor();
      caretPaint.setColor(handles.getCaretColor());
      caretPaint.setStrokeWidth(handles.getCursorWidth());
      canvas.drawLine(drawX, drawY, drawX, drawY + view.lineHeight, caretPaint);
    }
  }



  public void moveCursorLeft() {
    view.autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (view.selectionManager.hasSelection()) {
      int sL = view.selectionManager.selStartLine, sC = view.selectionManager.selStartChar;
      if (comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
        sL = view.selectionManager.selEndLine;
        sC = view.selectionManager.selEndChar;
      }
      setLineAndChar(sL, sC);
    } else if (getChar() > 0) moveCharDelta(-1);
    else if (getLine() > 0) {
      int nextLine = getLine() - 1;
      String ln = view.getLineTextForRender(nextLine);
      setLineAndChar(nextLine, ln.length());
    }
    view.selectionManager.clearSelectionKeepLineNumberState();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
    view.invalidate();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.autoSuggestionManager.updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorRight() {
    view.autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (view.selectionManager.hasSelection()) {
      int eL = view.selectionManager.selEndLine, eC = view.selectionManager.selEndChar;
      if (comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
        eL = view.selectionManager.selStartLine;
        eC = view.selectionManager.selStartChar;
      }
      setLineAndChar(eL, eC);
    } else {
      String ln = view.getLineTextForRender(getLine());
      if (getChar() < ln.length()) moveCharDelta(1);
      else {
        int next = getLine() + 1;
        if (!view.isEof || next < view.windowStartLine + view.linesWindow.size()) {
          setLineAndChar(next, 0);
        }
      }
    }
    view.selectionManager.clearSelectionKeepLineNumberState();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
    view.invalidate();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.autoSuggestionManager.updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorUp() {
    view.autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (view.selectionManager.hasSelection()) {
      int sL = view.selectionManager.selStartLine, sC = view.selectionManager.selStartChar;
      if (comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
        sL = view.selectionManager.selEndLine;
        sC = view.selectionManager.selStartChar; // Should be selStartChar for sC
      }
      setLineAndChar(sL, sC);
    }
    if (getLine() > 0) {
      int nextLine = getLine() - 1;
      String ln = view.getLineTextForRender(nextLine);
      setLineAndChar(nextLine, Math.min(getChar(), ln.length()));
    }
    view.selectionManager.clearSelectionKeepLineNumberState();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
    view.invalidate();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.autoSuggestionManager.updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorDown() {
    view.autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (view.selectionManager.hasSelection()) {
      int eL = view.selectionManager.selEndLine, eC = view.selectionManager.selEndChar;
      if (comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
        eL = view.selectionManager.selStartLine;
        eC = view.selectionManager.selStartChar;
      }
     setLineAndChar(eL, eC);
    }
    int next = getLine() + 1;
    if (!view.isEof || next < view.windowStartLine + view.linesWindow.size()) {
      String ln = view.getLineTextForRender(next);
      setLineAndChar(next, Math.min(getChar(), ln.length()));
    }
    view.selectionManager.clearSelectionKeepLineNumberState();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
    view.invalidate();
    view.scrollManager.keepCursorVisibleHorizontally();
    view.autoSuggestionManager.updateSuggestion(); // Update suggestion after cursor move
  }

  private int comparePos(int sL, int sC, int eL, int eC) {
    if (sL != eL) return sL - eL;
    return sC - eC;
  }


  
  void invalidateCursorArea() {
    if (view.wordWrapManager.isWordWrapEnabled) {
      view.invalidate();
      return;
    }
    view.invalidateLineGlobal(cursorLine);
  }

  public enum BracketPairType {
    NONE,
    CURLY,
    ROUND,
    SQUARE
  }
  public BracketPairType getCursorBracketPairType() {
    String ln = view.getLineTextForRender(cursorLine);
    if (ln == null) return BracketPairType.NONE;
    if (cursorChar <= 0 || cursorChar >= ln.length()) return BracketPairType.NONE;

    char left = ln.charAt(cursorChar - 1);
    char right = ln.charAt(cursorChar);
    if (left == '{' && right == '}') return BracketPairType.CURLY;
    if (left == '(' && right == ')') return BracketPairType.ROUND;
    if (left == '[' && right == ']') return BracketPairType.SQUARE;
    return BracketPairType.NONE;
  }

  public void insertTextAtCursor(String text) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEditPublic();
    final int opToken = view.incrementEditVersionPublic();

    if (text == null) return;
    if (text.isEmpty() && !view.selectionManager.hasSelection()) return;

    // FIX: لو فيه تحديد، لازم يكون replace ذري
    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText(text);
      return;
    }

    if (getHasComposing()) {
      setHasComposing(false);
      setComposingLength(0);
    }

    if (text.isEmpty()) {
      view.invalidate();
      return;
    }

    final int beforeLine = getLine();
    final int beforeChar = getChar();

    // For very large pastes into a file-backed document, avoid expanding the in-memory window and
    // doing
    // expensive per-line work on the UI thread. Instead, apply the insert via the file rewrite
    // path.
    if (view.sourceFile != null && !view.isFileCleared && SodiumEditorView.isLargePasteText(text)) {
      view.beginLargeEditUiIfNeeded(true, getLine(), getLine(), true);
      // Extend the watchdog for large paste operations; they can legitimately take longer than
      // the default safety timeout.
      view.mainHandler.removeCallbacks(view.largeEditUiWatchdog);
      view.mainHandler.postDelayed(view.largeEditUiWatchdog, 30_000);
      SodiumEditorView.CursorTarget target = view.computeCursorAfterInsert(getLine(), getChar(), text);
      final java.io.File inFile = view.sourceFile;
      view.rewriteReplaceRangeAsyncPublic(
          opToken, inFile, getLine(), getChar(), getLine(), getChar(), text, target, true);
      view.autoSuggestionManager.updateSuggestion();
      view.history.addLineCountDelta(view.countNewlines(text));
      if (text.length() <= view.history.getUndoTextLimit()) {
        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = text;
        op.insertedEndLine = target.line;
        op.insertedEndChar = target.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = target.line;
        op.cursorCharAfter = target.ch;
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      }
      return;
    }

    String[] parts = text.split("\n", -1);
    view.scrollManager.ensureLineInWindow(getLine(), true);
    if (view.isWindowLoading
        && (getLine() < view.windowStartLine || getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.post(() -> insertTextAtCursor(text));
      return;
    }

    int local = getLine() - view.windowStartLine;
    if (local < 0 || local >= view.linesWindow.size()) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) {
          view.linesWindow.add("");
          local = 0;
        } else local = Math.max(0, Math.min(local, view.linesWindow.size() - 1));
      }
    }

    synchronized (view.linesWindow) {
      int oldLineCount = view.getLinesCount();
      String base = view.getLineFromWindowLocal(local);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(getChar(), base.length()));
      String left = base.substring(0, pos);
      String right = base.substring(pos);

      if (parts.length == 1) {
        String modified = left + parts[0] + right;
        view.updateLocalLinePublic(local, modified);
        view.modifiedLines.put(getLine(), modified);
        view.lineWidthCache.remove(getLine());
        moveCharDelta(parts[0].length());
      } else {
        view.lineWidthCache.clear();
        String firstLine = left + parts[0];
        view.updateLocalLinePublic(local, firstLine);
        view.modifiedLines.put(getLine(), firstLine);

        java.util.List<String> linesToInsert = new java.util.ArrayList<>();
        for (int p = 1; p < parts.length - 1; p++) linesToInsert.add(parts[p]);

        String lastPart = parts[parts.length - 1];
        linesToInsert.add(lastPart + right);

        if (!linesToInsert.isEmpty()) view.linesWindow.addAll(local + 1, linesToInsert);
        for (int i = 0; i < linesToInsert.size(); i++) {
          view.modifiedLines.put(getLine() + 1 + i, linesToInsert.get(i));
        }

        setLineAndChar(getLine() + (parts.length - 1), lastPart.length());
        view.history.addLineCountDelta((parts.length - 1));
      }

      int newLineCount = view.getLinesCount();
      if (view.lineNumberManager.isShowLineNumbers()
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        view.requestLayout();
      }
      if (parts.length > 1) {
        view.wordWrapManager.onLineCountChanged(view);
      }

      view.recalculateMaxLineWidth();
      view.scrollManager.keepCursorVisibleHorizontally();
      view.cursorAnimationManager.resetCursorBlink();
      view.invalidate();
    }
    view.autoSuggestionManager.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = text;
    SodiumEditorView.CursorTarget insertedEnd = view.computeCursorAfterInsert(beforeLine, beforeChar, text);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = getLine();
    op.cursorCharAfter = getChar();
    op.timestamp = System.currentTimeMillis();
    view.recordEdit(op);
  }

  public void proceedGoToLineClamped(
      final int currentGoToLineVersion, final int targetLine, final int targetCol) {
    if (view.isWindowLoading
        && view.fileManager.getSourceFile() != null
        && !(targetLine >= view.windowStartLine && targetLine < view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.postDelayed(
          () -> {
            if (currentGoToLineVersion != view.getGoToLineVersion()) return;
            proceedGoToLineClamped(currentGoToLineVersion, targetLine, targetCol);
          },
          30);
      return;
    }

    Runnable completionAction =
        () -> {
          if (currentGoToLineVersion != view.getGoToLineVersion()) return;

          int finalLine = targetLine;
          int finalChar;
          if (finalLine >= view.windowStartLine && finalLine < view.windowStartLine + view.linesWindow.size()) {
            String lineText = view.getLineTextForRender(finalLine);
            finalChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (view.isEof) {
            int lastLineInDoc = view.windowStartLine + view.linesWindow.size() - 1;
            if (finalLine > lastLineInDoc) finalLine = Math.max(0, lastLineInDoc);
            String lineText = view.getLineTextForRender(finalLine);
            finalChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else {
            finalChar = 0;
          }
          setLineAndChar(finalLine, finalChar);

          view.scrollManager.keepCursorVisibleHorizontally();
          view.setDisable(false);
          view.loadingCircleManager.show(false);

          view.requestFocus();
          view.post(
              () -> {
                view.imeManager.showKeyboard();
                view.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                        view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.restartInput(view);
              });
        };

    if (view.fileManager.isFileCleared()
        || view.fileManager.getSourceFile() == null
        || (targetLine >= view.windowStartLine && targetLine < view.windowStartLine + view.linesWindow.size())) {
      completionAction.run();
    } else {
      int targetStart = Math.max(0, targetLine - view.prefetchLines);
      view.loadWindowAround(targetStart, completionAction);
    }
  }

  public void insertTextAt(int line, int col, String text) {
    if (text == null) return;
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
      view.mainHandler.post(() -> insertTextAt(line, col, text));
      return;
    }
    setPosition(line, col);
    insertTextAtCursor(text);
  }
}
