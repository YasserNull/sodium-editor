package com.yn.sodiumeditor.core;

import java.io.File;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.SelectionTextBuilder;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.Cursor;
import com.yn.sodiumeditor.core.SelectionState;
import com.yn.sodiumeditor.core.SelectionTextRange;
import com.yn.sodiumeditor.utils.SelectionWordFinder;
import com.yn.sodiumeditor.utils.SelectionQuoteFinder;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Selection handles text selection logic for SodiumEditor.
 * Delegates to specialized components for state, word finding, quote/bracket finding, and text building.
 */
public class Selection {

  private final SodiumEditor editor;
  private final Cursor cursor;

  // Component delegates
  public final SelectionState state;
  public final SelectionWordFinder wordFinder;
  public final SelectionQuoteFinder quoteFinder;
  public final SelectionTextBuilder textBuilder;

  // Convenience accessors for state fields (backward compatibility)
  public boolean hasSelection;
  public int selStartLine, selStartChar;
  public int selEndLine, selEndChar;
  public boolean selecting;
  public boolean isSelectAllActive;
  public boolean isEntireFileSelected;
  public int selectionColor;
  public int selectionHighlightColor;
  public int selectionHandleColor;
  public android.graphics.Paint selectionPaint;
  public android.graphics.RectF selectionHighlightRect;
  public android.graphics.RectF selectionRectTmp;
  public android.graphics.Path selectionPathTmp;
  public float[] selectionRadiiTmp;
  public boolean isLineNumberSelecting;
  public int lineNumberSelectAnchorLine;
  public boolean longPressSelecting;
  public int longPressAnchorLine;
  public int longPressAnchorChar;
  public int lastDoubleTapLine;
  public int lastDoubleTapWordStart;
  public int lastDoubleTapWordEnd;
  public int lastDoubleTapStage;
  public long copyCutMaxLines;
  public int copyCutMaxChars;
  public int hideCopyCutMaxLines;
  public int replaceAllMaxCount;
  public boolean hideKeyboardOnFocusLoss;

  public Selection(SodiumEditor editor, Cursor cursor) {
    this.editor = editor;
    this.cursor = cursor;

    // Initialize components
    state = new SelectionState(editor);
    wordFinder = new SelectionWordFinder(editor);
    quoteFinder = new SelectionQuoteFinder(editor);
    textBuilder = new SelectionTextBuilder(editor);

    // Sync convenience fields
    syncFromState();
  }

  private void syncFromState() {
    hasSelection = state.hasSelection;
    selStartLine = state.selStartLine;
    selStartChar = state.selStartChar;
    selEndLine = state.selEndLine;
    selEndChar = state.selEndChar;
    selecting = state.selecting;
    isSelectAllActive = state.isSelectAllActive;
    isEntireFileSelected = state.isEntireFileSelected;
    selectionColor = state.selectionColor;
    selectionHighlightColor = state.selectionHighlightColor;
    selectionHandleColor = state.selectionHandleColor;
    selectionPaint = state.selectionPaint;
    selectionHighlightRect = state.selectionHighlightRect;
    selectionRectTmp = state.selectionRectTmp;
    selectionPathTmp = state.selectionPathTmp;
    selectionRadiiTmp = state.selectionRadiiTmp;
    isLineNumberSelecting = state.isLineNumberSelecting;
    lineNumberSelectAnchorLine = state.lineNumberSelectAnchorLine;
    longPressSelecting = state.longPressSelecting;
    longPressAnchorLine = state.longPressAnchorLine;
    longPressAnchorChar = state.longPressAnchorChar;
    lastDoubleTapLine = state.lastDoubleTapLine;
    lastDoubleTapWordStart = state.lastDoubleTapWordStart;
    lastDoubleTapWordEnd = state.lastDoubleTapWordEnd;
    lastDoubleTapStage = state.lastDoubleTapStage;
    copyCutMaxLines = state.copyCutMaxLines;
    copyCutMaxChars = state.copyCutMaxChars;
    hideCopyCutMaxLines = state.hideCopyCutMaxLines;
    replaceAllMaxCount = state.replaceAllMaxCount;
    hideKeyboardOnFocusLoss = state.hideKeyboardOnFocusLoss;
  }

  private void syncToState() {
    state.hasSelection = hasSelection;
    state.selStartLine = selStartLine;
    state.selStartChar = selStartChar;
    state.selEndLine = selEndLine;
    state.selEndChar = selEndChar;
    state.selecting = selecting;
    state.isSelectAllActive = isSelectAllActive;
    state.isEntireFileSelected = isEntireFileSelected;
  }

  public float getSelectionAlpha() { return state.getSelectionAlpha(); }
  public float getHandleAlpha() { return state.getHandleAlpha(); }
  public boolean isSelectionAnimationEnabled() { return state.isSelectionAnimationEnabled(); }

  public void setSelection(int startLine, int startChar, int endLine, int endChar) {
    state.setSelection(startLine, startChar, endLine, endChar);
    syncFromState();
  }

  public void clearSelection() {
    state.clearSelection();
    syncFromState();
  }

  public void selectAll() {
    editor.autoCompletion.clearActiveSuggestion();
    final boolean keyboardWasVisible = editor.keyboardHeight > 0;
    if (editor.wordWrap.isWordWrapEnabled) {
      int widthPx = Math.max(1, Math.round(editor.wordWrap.getWrapWidth()));
      if (editor.wordWrap.isWrapMetricsUsableForWindow(widthPx)) {
        editor.wordWrap.cancelWrapWorkForPriority();
      }
    }
    editor.setDisable(true);
    editor.loadingCircle.showLoadingCircle(true);

    isSelectAllActive = true;
    isEntireFileSelected = true;
    hasSelection = true;

    selStartLine = 0;
    selStartChar = 0;
    editor.popup.hidePopup();

    // In-memory mode
    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      synchronized (editor.textRender.linesWindow) {
        if (editor.textRender.linesWindow.isEmpty()) editor.textRender.linesWindow.add("");
        if (editor.textRender.windowStartLine != 0) editor.textRender.windowStartLine = 0;
        editor.fileIO.isEof = true;
      }

      selEndLine = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
      String lastLineText = editor.getLineTextForRender(selEndLine);
      selEndChar = lastLineText.length();
      editor.cursor.cursorLine = selEndLine;
      editor.cursor.cursorChar = selEndChar;

      editor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);
      finishSelectAll(keyboardWasVisible);
      return;
    }

    if (editor.fileIO.isEof) {
      int windowLast = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
      selEndLine = windowLast;
      String lastLineText = editor.getLineTextForRender(windowLast);
      selEndChar = lastLineText.length();
      editor.cursor.cursorLine = windowLast;
      editor.cursor.cursorChar = selEndChar;

      editor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);
      finishSelectAll(keyboardWasVisible);
      return;
    }

    Runnable goToEndUsingIndex = () -> {
      if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null) return;

      int fileLastLine;
      synchronized (editor.fileIO.lineOffsetsLock) {
        fileLastLine = Math.max(0, editor.fileIO.lineOffsets.length - 1);
      }

      if (editor.fileIO.isEof) {
        int windowLast = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
        if (windowLast > fileLastLine) {
          selEndLine = windowLast;
          String lastLineText = editor.getLineTextForRender(windowLast);
          selEndChar = lastLineText.length();
          editor.cursor.cursorLine = windowLast;
          editor.cursor.cursorChar = selEndChar;

          editor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);
          finishSelectAll(keyboardWasVisible);
          return;
        }
      }

      selEndLine = fileLastLine;
      int targetStart = Math.max(0, fileLastLine - editor.textRender.prefetchLines);

      editor.fileIO.loadWindowAround(
          targetStart,
          () -> editor.post(() -> {
            String lastLineText = editor.getLineTextForRender(fileLastLine);
            selEndChar = lastLineText.length();
            editor.cursor.cursorLine = fileLastLine;
            editor.cursor.cursorChar = selEndChar;

            editor.scroll.scrollToLineFastForSelectAll(fileLastLine, selEndChar);
            finishSelectAll(keyboardWasVisible);
          }),
          false);
    };

    if (editor.fileIO.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    if (!editor.fileIO.isIndexBuilding && !editor.fileIO.isIndexDisabled) {
      editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
    }

    editor.fileIO.countTotalLines(totalLines -> {
      int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
      selEndLine = Math.max(0, lastLine);

      Runnable goToEndWithoutIndex = () -> {
        int targetStart = Math.max(0, selEndLine - editor.textRender.prefetchLines);
        editor.fileIO.loadWindowAround(
            targetStart,
            () -> editor.post(() -> {
              String lastLineText = editor.getLineTextForRender(selEndLine);
              selEndChar = lastLineText.length();
              editor.cursor.cursorLine = selEndLine;
              editor.cursor.cursorChar = selEndChar;

              editor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);
              finishSelectAll(keyboardWasVisible);
            }),
            false);
      };

      if (editor.fileIO.isIndexDisabled) {
        goToEndWithoutIndex.run();
        return;
      }

      final int ticket = editor.editOperators.editVersion.incrementAndGet();
      Runnable poll = new Runnable() {
        @Override
        public void run() {
          if (ticket != editor.editOperators.editVersion.get()) return;
          if (editor.fileIO.sourceFile == null) {
            editor.setDisable(false);
            editor.loadingCircle.showLoadingCircle(false);
            editor.invalidate();
            editor.popup.showPopupAtSelection();
            if (keyboardWasVisible) editor.showKeyboard();
            return;
          }
          if (editor.fileIO.isIndexDisabled) {
            goToEndWithoutIndex.run();
          } else if (editor.fileIO.isIndexReady) {
            goToEndUsingIndex.run();
          } else {
            editor.caret.mainHandler.postDelayed(this, 80);
          }
        }
      };
      editor.caret.mainHandler.post(poll);
    });
  }

  private void finishSelectAll(boolean keyboardWasVisible) {
    syncToState();
    editor.setDisable(false);
    editor.loadingCircle.showLoadingCircle(false);
    editor.invalidate();
    editor.requestFocus();
    editor.popup.showPopupAtSelection();
    editor.post(() -> {
      editor.requestFocus();
      if (keyboardWasVisible) editor.showKeyboard();
      InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.restartInput(editor);
    });
  }

  public void selectWordAtCursor() {
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return;

    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return;
    if (Character.isWhitespace(line.charAt(pos))) return;

    int[] bounds = editor.computeWordBounds(line, pos);
    if (bounds != null && bounds[0] != bounds[1]) {
      setSelection(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
    }
  }

  public void selectLineAtCursor() {
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null) return;
    setSelection(editor.cursor.cursorLine, 0, editor.cursor.cursorLine, line.length());
  }

  public String getSelectedText() {
    if (!hasSelection) return null;

    int sL = selStartLine, sC = selStartChar;
    int eL = selEndLine, eC = selEndChar;

    if (state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    StringBuilder sb = new StringBuilder();
    for (int line = sL; line <= eL; line++) {
      String lineText = editor.getLineTextForRender(line);
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

  public boolean contains(int line, int ch) {
    return state.contains(line, ch);
  }

  public int getStartLine() { return state.getStartLine(); }
  public int getStartChar() { return state.getStartChar(); }
  public int getEndLine() { return state.getEndLine(); }
  public int getEndChar() { return state.getEndChar(); }
  public int getLineCount() { return state.getLineCount(); }
  public boolean isEmpty() { return state.isEmpty(); }
  public int comparePos(int lineA, int charA, int lineB, int charB) { return state.comparePos(lineA, charA, lineB, charB); }

  public void setSelectionColor(int color) { state.setSelectionColor(color); }
  public void setSelectionHandleColor(int color) { state.setSelectionHandleColor(color); }
  public void setSelectionHighlightColor(int color) { state.setSelectionHighlightColor(color); }
  public void setSelectionAnimationEnabled(boolean enabled) { state.setSelectionAnimationEnabled(enabled); }

  public boolean hasSelection() { return state.hasSelection(); }
  public boolean isSelectAll() { return state.isSelectAll(); }

  public boolean isPositionInsideSelection(int line, int ch) { return state.isPositionInsideSelection(line, ch); }
  public int clampLineForSelection(int line) { return state.clampLineForSelection(line); }
  public boolean isLineSelectable(int line) { return state.isLineSelectable(line); }

  public void beginLongPressSelection(int line, int ch) { state.beginLongPressSelection(line, ch); }
  public void updateLongPressSelection(int line, int ch) { state.updateLongPressSelection(line, ch); }
  public void endLongPressSelection() { state.endLongPressSelection(); }

  public void clearSelectionStateAfterDelete() { state.clearSelectionStateAfterDelete(); }
  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    state.restoreSelection(sL, sC, eL, eC, cursorLine, cursorChar);
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    state.setSelectionInternal(sL, sC, eL, eC);
    syncFromState();
  }

  public void addSelectionCandidate(List<SelectionTextRange> out, int start, int end, int lineLen) {
    wordFinder.addSelectionCandidate(out, start, end, lineLen);
  }

  public int findSelectionCandidateIndex(int line, List<SelectionTextRange> candidates) {
    return wordFinder.findSelectionCandidateIndex(line, candidates, selStartLine, selStartChar, selEndLine, selEndChar);
  }

  public ArrayList<SelectionTextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
    ArrayList<SelectionTextRange> out = new ArrayList<>(6);
    if (line == null) return out;
    int len = line.length();
    wordFinder.addSelectionCandidate(out, wStart, wEnd, len);

    SelectionTextRange quote = quoteFinder.findEnclosingQuoteRange(line, charIndex);
    if (quote != null) {
      wordFinder.addSelectionCandidate(out, quote.start + 1, quote.end, len);
      wordFinder.addSelectionCandidate(out, quote.start, quote.end + 1, len);
    }

    SelectionTextRange bracket = quoteFinder.findEnclosingBracketRange(line, charIndex);
    if (bracket != null) {
      wordFinder.addSelectionCandidate(out, bracket.start + 1, bracket.end, len);
      wordFinder.addSelectionCandidate(out, bracket.start, bracket.end + 1, len);
    }
    return out;
  }

  public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    ArrayList<SelectionTextRange> candidates = wordFinder.buildSmartWordCandidates(lineText, charIndex);
    if (candidates.isEmpty()) return false;
    int wStart = candidates.get(0).start;
    int wEnd = candidates.get(0).end;

    SelectionTextRange quote = quoteFinder.findEnclosingQuoteRange(lineText, charIndex);
    if (quote != null) {
      wordFinder.addSelectionCandidate(candidates, quote.start + 1, quote.end, lineText.length());
      wordFinder.addSelectionCandidate(candidates, quote.start, quote.end + 1, lineText.length());
    }

    SelectionTextRange bracket = quoteFinder.findEnclosingBracketRange(lineText, charIndex);
    if (bracket != null) {
      wordFinder.addSelectionCandidate(candidates, bracket.start + 1, bracket.end, lineText.length());
      wordFinder.addSelectionCandidate(candidates, bracket.start, bracket.end + 1, lineText.length());
    }
    if (candidates.isEmpty()) return false;

    boolean sameAnchor = line == lastDoubleTapLine && wStart == lastDoubleTapWordStart && wEnd == lastDoubleTapWordEnd;
    int currentIdx = findSelectionCandidateIndex(line, candidates);
    int nextIdx;
    if (sameAnchor) {
      if (currentIdx >= 0) {
        nextIdx = Math.min(currentIdx + 1, candidates.size() - 1);
      } else {
        nextIdx = Math.min(lastDoubleTapStage + 1, candidates.size() - 1);
      }
    } else {
      nextIdx = 0;
    }

    SelectionTextRange pick = candidates.get(nextIdx);
    selStartLine = selEndLine = line;
    selStartChar = pick.start;
    selEndChar = pick.end;
    hasSelection = true;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = true;
    editor.cursor.cursorLine = line;
    editor.cursor.cursorChar = selEndChar;
    lastDoubleTapLine = line;
    lastDoubleTapWordStart = wStart;
    lastDoubleTapWordEnd = wEnd;
    lastDoubleTapStage = nextIdx;
    syncToState();
    return true;
  }

  public boolean shouldHideCopyCutForSelection() { return state.shouldHideCopyCutForSelection(); }

  public void copyOrCutSelection(final boolean cut) {
    if (!hasSelection) return;
    editor.autoCompletion.clearActiveSuggestion();

    if (state.shouldHideCopyCutForSelection()) return;

    int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
    if (state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    long lines = (long) eL - (long) sL + 1L;
    if (lines > copyCutMaxLines) return;

    final int fsL = sL, fsC = sC, feL = eL, feC = eC;

    boolean fullyInWindow = (fsL >= editor.textRender.windowStartLine) && (feL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
    if (fullyInWindow) {
      String text = textBuilder.buildSelectedTextFromWindow(fsL, fsC, feL, feC, copyCutMaxChars);
      ClipboardManager cm = (ClipboardManager) editor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
      if (cut) {
        deleteSelection();
      }
      return;
    }

    if (editor.wordWrap.isWordWrapEnabled) {
      editor.wordWrap.cancelWrapWorkForPriority();
    }

    editor.fileIO.ioHandler.post(() -> {
      final String text = textBuilder.buildSelectedTextBlocking(fsL, fsC, feL, feC, copyCutMaxChars);
      editor.post(() -> {
        ClipboardManager cm = (ClipboardManager) editor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
        if (cut) {
          deleteSelection();
        }
      });
    });
  }

  public void deleteSelection() {
    editor.autoCompletion.clearActiveSuggestion();
    replaceSelectionWithText("");
  }

  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }

  public void pasteFromClipboard() {
    state.pasteFromClipboard();
  }

  public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
    return textBuilder.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
  }

  public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
    return textBuilder.buildSelectedTextBlocking(sL, sC, eL, eC, maxChars);
  }

  public void recordReplaceSelectionEdit(
      int sL, int sC, int eL, int eC,
      @Nullable String removedText, @Nullable String insertText,
      int beforeLine, int beforeChar) {
    String insert = (insertText == null) ? "" : insertText;
    if (removedText == null) {
      EditOperators.EditOp op = new EditOperators.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine;
      op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.recordEditNoUndo(op);
      return;
    }
    if (removedText.length() > EditOperators.UNDO_TEXT_LIMIT || insert.length() > EditOperators.UNDO_TEXT_LIMIT) {
      EditOperators.EditOp op = new EditOperators.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine;
      op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.recordEditNoUndo(op);
      return;
    }
    EditOperators.EditOp op = new EditOperators.EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = removedText;
    op.insertedText = insert;
    EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    editor.editOperators.recordEdit(op);
  }

  public void replaceSelectionWithText(String insertText) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    final int opToken = editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    if (insertText == null) insertText = "";

    if (!hasSelection) {
      if (!insertText.isEmpty()) editor.editOperators.insertTextAtCursor(insertText);
      editor.autoCompletion.updateSuggestion();
      return;
    }

    int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
    if (state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = editor.fileIO.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > EditOperators.UNDO_TEXT_LIMIT) {
        removedText = null;
      }
    }
    int removedNewlines = editor.editOperators.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = editor.editOperators.countNewlines(insertText);

    final boolean selectAllLike = isSelectAllActive || isEntireFileSelected;
    editor.loadingCircle.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    if (selectAllLike) {
      synchronized (editor.textRender.linesWindow) {
        editor.textRender.linesWindow.clear();
        editor.textRender.linesWindow.add("");
        editor.textRender.windowStartLine = 0;
        editor.fileIO.isEof = true;
      }
      synchronized (editor.fileIO.directLineCache) {
        editor.fileIO.directLineCache.clear();
      }
      synchronized (editor.textRender.modifiedLines) {
        editor.textRender.modifiedLines.clear();
      }
      synchronized (editor.textRender.lineWidthCache) {
        editor.textRender.lineWidthCache.clear();
      }
      editor.clearStreamedLineCaches();
      editor.bracketGuides.invalidateBracketGuideCache(true);
      if (editor.codeFold.isCodeFoldingEnabled) {
        editor.codeFold.foldRanges.clear();
        editor.codeFold.foldIntervals.clear();
        editor.codeFold.invalidateFoldCaches();
      }
      editor.textRender.currentMaxWindowLineWidth = 0f;
      editor.textRender.globalMaxLineWidth = 0f;
      editor.scroll.maxLineWidthForScroll = 0f;
      editor.scroll.maxTextStartXForScroll = 0f;
      editor.scroll.maxScrollXForScroll = 0f;

      editor.fileIO.isFileCleared = true;
      synchronized (editor.fileIO.lineOffsetsLock) {
        editor.fileIO.lineOffsets = new long[0];
      }
      editor.fileIO.isIndexReady = false;
      editor.fileIO.isIndexBuilding = false;
      editor.fileIO.isIndexDisabled = false;
      editor.fileIO.indexDisabledPath = null;
      editor.fileIO.indexDisabledFileLength = -1L;

      editor.cursor.cursorLine = 0;
      editor.cursor.cursorChar = 0;
      selStartLine = 0;
      selEndLine = 0;
      selStartChar = 0;
      selEndChar = 0;
      editor.scroll.scrollY = 0;
      editor.scroll.scrollX = 0;
      state.clearSelectionStateAfterDelete();

      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (editor.textRender.linesWindow) {
          editor.textRender.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            editor.textRender.linesWindow.add(i, newLines[i]);
          }
        }
        EditOperators.CursorTarget newPos = editor.editOperators.computeCursorAfterInsert(0, 0, insertText);
        editor.cursor.cursorLine = newPos.line;
        editor.cursor.cursorChar = newPos.ch;
      }

      editor.wordWrap.onLineCountChanged();
      editor.loadingCircle.endLargeEditUi(true);
      editor.recalculateMaxLineWidth();
      editor.keepCursorVisibleHorizontally();
      editor.requestLayout();
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    if (sL == eL && insertText.indexOf('\n') < 0) {
      editor.fileIO.ensureLineInWindow(sL, true);
      if (editor.fileIO.isWindowLoading && (sL < editor.textRender.windowStartLine || sL >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
        final String txtFinal = insertText;
        editor.post(() -> replaceSelectionWithText(txtFinal));
        return;
      }

      int local = sL - editor.textRender.windowStartLine;
      if (local >= 0 && local < editor.textRender.linesWindow.size()) {
        synchronized (editor.textRender.linesWindow) {
          String line = editor.getLineFromWindowLocal(local);
          if (line == null) line = "";

          int a = Math.max(0, Math.min(sC, line.length()));
          int b = Math.max(0, Math.min(eC, line.length()));
          if (b < a) { int t = a; a = b; b = t; }

          String merged = line.substring(0, a) + insertText + line.substring(b);
          editor.updateLocalLine(local, merged);
          editor.textRender.modifiedLines.put(sL, merged);

          editor.cursor.cursorLine = sL;
          editor.cursor.cursorChar = a + insertText.length();

          editor.computeWidthForLine(sL, merged);
          editor.recalculateMaxLineWidth();
        }
      }

      state.clearSelectionStateAfterDelete();
      editor.invalidate();
      editor.keepCursorVisibleHorizontally();
      editor.loadingCircle.endLargeEditUi(false);
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final EditOperators.CursorTarget target = editor.editOperators.computeCursorAfterInsert(sL, sC, insertText);

    boolean fullyInWindow = (sL >= editor.textRender.windowStartLine) && (eL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
    if (fullyInWindow) {
      editor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
    } else {
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    }

    state.clearSelectionStateAfterDelete();
    editor.keepCursorVisibleHorizontally();
    editor.loadingCircle.endLargeEditUi(false);

    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      if (!fullyInWindow) {
        editor.fileIO.ensureLineInWindow(sL, true);
        editor.fileIO.ensureLineInWindow(eL, true);
        editor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = editor.fileIO.sourceFile;
    editor.editOperators.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    editor.autoCompletion.updateSuggestion();
    editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
    recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }
}
