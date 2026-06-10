package com.yn.sodiumeditor.core.selection;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.io.EditOperators;
import java.util.concurrent.atomic.AtomicInteger;

/** Handles complex selection actions like selectAll and multi-line replacement. */
public class SelectionActionHandler {
  private static final String TAG = "SodiumSelectionEdit";
  private static final int MAX_SELECTION_EDIT_LOGS = 400;
  private static final long SELECT_ALL_INDEX_WAIT_TIMEOUT_MS = 120_000L;
  public static boolean DEBUG_SELECT_ALL_LOGS = false;
  public static boolean DEBUG_SELECTION_EDIT_LOGS = false;

  private final SodiumEditor editor;
  private final Selection selection;
  private int selectionEditLogCount = 0;
  private final AtomicInteger selectAllRequestToken = new AtomicInteger(0);

  public SelectionActionHandler(SodiumEditor editor, Selection selection) {
    this.editor = editor;
    this.selection = selection;
  }

  public void selectAll() {
    final int requestToken = selectAllRequestToken.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();
    final boolean keyboardWasVisible = editor.view.keyboardHeight > 0;
    if (editor.wordWrap.isWordWrapEnabled) {
      int widthPx = Math.max(1, Math.round(editor.wordWrap.getWrapWidth()));
      if (editor.wordWrap.isWrapMetricsUsableForWindow(widthPx))
        editor.wordWrap.cancelWrapWorkForPriority();
    }
    editor.view.setDisable(true);
    editor.loadingCircle.showLoadingCircle(true);

    selection.isSelectAllActive = true;
    selection.isEntireFileSelected = true;
    selection.hasSelection = true;
    selection.selStartLine = 0;
    selection.selStartChar = 0;
    selection.selEndLine = Math.max(0, editor.cursor.cursorLine);
    selection.selEndChar = Math.max(0, editor.cursor.cursorChar);
    selection.syncToState();

    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      synchronized (editor.windowRender.linesWindow) {
        if (editor.windowRender.linesWindow.isEmpty()) editor.windowRender.linesWindow.add("");
        editor.windowRender.windowStartLine = 0;
        editor.fileIO.isEof = true;
      }
      int endLine =
          Math.max(
              0, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1);
      String lastText = editor.windowRender.getLineTextForRender(endLine);
      int endChar = lastText.length();
      selection.setSelection(0, 0, endLine, endChar);
      editor.cursor.cursorLine = selection.selEndLine;
      editor.cursor.cursorChar = selection.selEndChar;
      editor.scroll.scrollToLineFastForSelectAll(selection.selEndLine, selection.selEndChar);
      finishSelectAll(keyboardWasVisible);
      return;
    }

    if (editor.fileIO.isEof) {
      int winLast =
          Math.max(
              0, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1);
      String lastText = editor.windowRender.getLineTextForRender(winLast);
      int endChar = lastText.length();
      selection.setSelection(0, 0, winLast, endChar);
      editor.cursor.cursorLine = winLast;
      editor.cursor.cursorChar = selection.selEndChar;
      editor.scroll.scrollToLineFastForSelectAll(winLast, selection.selEndChar);
      finishSelectAll(keyboardWasVisible);
      return;
    }

    Runnable goToEndUsingIndex =
        () -> {
          if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null) return;
          int fileLast;
          synchronized (editor.fileIO.lineOffsetsLock) {
            fileLast = Math.max(0, editor.fileIO.lineOffsets.length - 1);
          }
          editor.fileIO.loadWindowAround(
              Math.max(0, fileLast - editor.windowRender.prefetchLines),
              () ->
                  editor.post(
                      () -> {
                        if (requestToken != selectAllRequestToken.get()) {
                          return;
                        }
                        String lastText = editor.windowRender.getLineTextForRender(fileLast);
                        int endChar = (lastText != null ? lastText.length() : 0);
                        selection.setSelection(0, 0, fileLast, endChar);
                        editor.cursor.cursorLine = fileLast;
                        editor.cursor.cursorChar = selection.selEndChar;
                        editor.scroll.scrollToLineFastForSelectAll(fileLast, selection.selEndChar);
                        finishSelectAll(keyboardWasVisible);
                      }),
              false);
        };

    Runnable goToEndWithoutIndex =
        () -> {
          editor.fileIO.countTotalLines(
              total -> {
                if (requestToken != selectAllRequestToken.get()) {
                  return;
                }
                int fileLast = Math.max(0, total > 0 ? total - 1 : 0);
                editor.fileIO.loadTailWindowForSelectAll(
                    fileLast,
                    () ->
                        editor.post(
                            () -> {
                              if (requestToken != selectAllRequestToken.get()) {
                                return;
                              }
                              String lastText = editor.windowRender.getLineTextForRender(fileLast);
                              int endChar = (lastText != null ? lastText.length() : 0);
                              selection.setSelection(0, 0, fileLast, endChar);
                              editor.cursor.cursorLine = fileLast;
                              editor.cursor.cursorChar = selection.selEndChar;
                              editor.scroll.scrollToLineFastForSelectAll(
                                  fileLast, selection.selEndChar);
                              if (DEBUG_SELECT_ALL_LOGS) {
                                Log.d(TAG, "[SodiumEditor] operation=select-all complete-no-index");
                              }
                              finishSelectAll(keyboardWasVisible);
                            }));
              });
        };

    if (editor.fileIO.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }
    if (editor.fileIO.isIndexDisabled) {
      goToEndWithoutIndex.run();
      return;
    }
    if (!editor.fileIO.isIndexBuilding && !editor.fileIO.isIndexDisabled) {
      editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
    }

    final int ticket = requestToken;
    final long waitStartedAt = android.os.SystemClock.uptimeMillis();
    Runnable poll =
        new Runnable() {
          @Override
          public void run() {
            if (ticket != selectAllRequestToken.get()) {
              return;
            }
            if (editor.fileIO.sourceFile == null) {
              abortPendingSelectAll(keyboardWasVisible, "source-null");
              return;
            }
            if (editor.fileIO.isIndexReady) {
              goToEndUsingIndex.run();
              return;
            }
            if (editor.fileIO.isIndexDisabled) {
              goToEndWithoutIndex.run();
              return;
            }
            if (!editor.fileIO.isIndexBuilding) {
              editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
            }
            if (android.os.SystemClock.uptimeMillis() - waitStartedAt
                > SELECT_ALL_INDEX_WAIT_TIMEOUT_MS) {
              abortPendingSelectAll(keyboardWasVisible, "index-timeout");
              return;
            }
            editor.caret.mainHandler.postDelayed(this, 80);
          }
        };
    editor.caret.mainHandler.post(poll);
  }

  private void abortPendingSelectAll(boolean keyboardWasVisible, String reason) {
    selection.hasSelection = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    selection.syncToState();
    editor.view.setDisable(false);
    editor.loadingCircle.showLoadingCircle(false);
    editor.invalidate();
    editor.popup.showPopupAtSelection();
    if (keyboardWasVisible) editor.ime.showKeyboard();
  }

  private void finishSelectAll(boolean keyboardWasVisible) {
    selection.syncToState();
    editor.view.setDisable(false);
    editor.loadingCircle.showLoadingCircle(false);
    editor.invalidate();
    editor.requestFocus();
    editor.popup.showPopupAtSelection();
    editor.post(
        () -> {
          editor.requestFocus();
          if (keyboardWasVisible) editor.ime.showKeyboard();
          InputMethodManager imm =
              (InputMethodManager)
                  editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          if (imm != null) imm.restartInput(editor);
        });
  }

  public void replaceSelectionWithText(String insertText) {
    final int originalSelectionStartLine = selection.selStartLine;
    final int originalSelectionStartChar = selection.selStartChar;
    final int originalSelectionEndLine = selection.selEndLine;
    final int originalSelectionEndChar = selection.selEndChar;
    if (editor.view.isReadOnly) return;
    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.fileIO.invalidatePendingIOForEdit();
    final int opToken = editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();
    if (insertText == null) insertText = "";
    if (!selection.hasSelection) {
      if (!insertText.isEmpty()) editor.editOperators.insertTextAtCursor(insertText);
      editor.autoCompletion.updateSuggestion();
      return;
    }

    int sL = selection.selStartLine,
        sC = selection.selStartChar,
        eL = selection.selEndLine,
        eC = selection.selEndChar;
    if (selection.state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = editor.cursor.cursorLine, beforeChar = editor.cursor.cursorChar;
    final boolean selectAllLike = selection.isSelectAllActive || selection.isEntireFileSelected;
    int insertedNl = editor.editOperators.countNewlines(insertText);
    if (selectAllLike) {
      if (editor.fileIO.sourceFile != null) {
        replaceEntireFileSelectionFast(insertText);
        return;
      }
      int removedNl = Math.max(0, eL - sL);
      editor.loadingCircle.beginLargeEditUiIfNeeded(true, sL, eL, true);
      handleSelectAllReplace(
          insertText, sL, sC, eL, eC, null, beforeLine, beforeChar, removedNl, insertedNl);
      return;
    }

    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = editor.fileIO.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > EditOperators.UNDO_TEXT_LIMIT)
        removedText = null;
    }
    int removedNl = editor.editOperators.countNewlines(removedText);
    if (removedText == null && eL >= sL) removedNl = Math.max(0, eL - sL);
    editor.loadingCircle.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    if (sL == eL && insertText.indexOf('\n') < 0) {
      handleSingleLineReplace(
          insertText, sL, sC, eL, eC, removedText, beforeLine, beforeChar, removedNl, insertedNl);
      return;
    }

    final EditOp.CursorTarget target =
        editor.editOperators.computeCursorAfterInsert(sL, sC, insertText);
    boolean fullyInWindow =
        (sL >= editor.windowRender.windowStartLine)
            && (eL < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
    if (fullyInWindow)
      editor.windowRender.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
    else {
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    }

    selection.clearSelectionStateAfterDelete();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.loadingCircle.endLargeEditUi(false);

    if (!fullyInWindow) {
      editor.fileIO.ensureLineInWindow(sL, true);
      editor.fileIO.ensureLineInWindow(eL, true);
      if (!editor.fileIO.isWindowLoading) {
        editor.windowRender.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
    }

    finalizeAction(
        removedNl, insertedNl, sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
    invalidateFeatureStateForReplace(sL, target.line);
  }

  public void deleteEntireFileSelectionFast() {
    if (editor.view.isReadOnly || !selection.hasSelection) return;
    if (editor.fileIO.sourceFile != null
        && (selection.isSelectAllActive || selection.isEntireFileSelected)) {
      replaceEntireFileSelectionFast("");
      return;
    }
    if (editor.fileIO.sourceFile == null) {
      replaceSelectionWithText("");
      return;
    }
    if (!selection.isSelectAllActive && !selection.isEntireFileSelected) {
      replaceSelectionWithText("");
      return;
    }

    int sL = selection.selStartLine, sC = selection.selStartChar;
    int eL = selection.selEndLine, eC = selection.selEndChar;
    if (selection.state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    final int removedNl = Math.max(0, eL - sL);

    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.add("");
      editor.windowRender.windowStartLine = 0;
      editor.fileIO.isEof = true;
    }
    editor.windowRender.clearModifiedLines();
    editor.cursor.cursorLine = 0;
    editor.cursor.cursorChar = 0;
    selection.selStartLine = 0;
    selection.selStartChar = 0;
    selection.selEndLine = 0;
    selection.selEndChar = 0;
    selection.clearSelectionStateAfterDelete();
    editor.scroll.scrollY = 0;
    editor.scroll.scrollX = 0;
    editor.editOperators.lineCountDelta += -removedNl;
    EditOp op = new EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = null;
    op.insertedText = "";
    op.insertedEndLine = 0;
    op.insertedEndChar = 0;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    op.entireFileDelete = true;
    editor.highlight.markTyping();
    editor.editOperators.undoStack.addLast(op);
    while (editor.editOperators.undoStack.size() > EditOperators.UNDO_STACK_LIMIT) {
      editor.editOperators.undoStack.removeFirst();
    }
    editor.editOperators.redoStack.clear();
    editor.editOperators.pendingEdits.addLast(op);
    editor.editOperators.pendingRedo.clear();
    editor.editOperators.lastEditTimestamp = op.timestamp;
    editor.requestLayout();
    editor.invalidate();
    editor.postDelayed(
        () -> {
          editor.fileIO.invalidatePendingIOVersionForEdit();
          synchronized (editor.fileIO.directLineCache) {
            editor.fileIO.directLineCache.clear();
          }
          synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.clear();
          }
          editor.windowRender.clearStreamedLineCaches();
          editor.windowRender.currentMaxWindowLineWidth = 0f;
          editor.windowRender.globalMaxLineWidth = 0f;
          editor.fileIO.isFileCleared = editor.fileIO.sourceFile == null;
          synchronized (editor.fileIO.lineOffsetsLock) {
            editor.fileIO.lineOffsets = new long[0];
          }
          editor.fileIO.isIndexReady = false;
          editor.fileIO.isIndexBuilding = false;
          editor.wordWrap.onLineCountChanged();
          invalidateFeatureStateForReplace(0, 0);
          editor.lineNumber.invalidateLineNumberCache();
          editor.requestLayout();
          editor.invalidate();
        },
        32L);
  }

  public void replaceEntireFileSelectionFast(String insertText) {
    if (editor.view.isReadOnly || !selection.hasSelection) return;
    if (insertText == null) insertText = "";
    if (editor.fileIO.sourceFile == null
        || (!selection.isSelectAllActive && !selection.isEntireFileSelected)) {
      replaceSelectionWithText(insertText);
      return;
    }

    int sL = selection.selStartLine, sC = selection.selStartChar;
    int eL = selection.selEndLine, eC = selection.selEndChar;
    if (selection.state.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    final int insertedNl = editor.editOperators.countNewlines(insertText);
    int removedNl = Math.max(0, eL - sL);
    if (editor.fileIO.isIndexReady) {
      synchronized (editor.fileIO.lineOffsetsLock) {
        removedNl = Math.max(0, editor.fileIO.lineOffsets.length - 1);
      }
    }

    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.add("");
      editor.windowRender.windowStartLine = 0;
      editor.fileIO.isEof = true;
    }
    editor.windowRender.clearModifiedLines();

    EditOp.CursorTarget target = editor.editOperators.computeCursorAfterInsert(0, 0, insertText);
    if (!insertText.isEmpty()) {
      if (shouldUseBoundedSelectAllPreview(insertText, insertedNl)) {
        populateBoundedSelectAllPreview(insertText, target.line);
      } else {
        String[] lines = insertText.split("\n", -1);
        synchronized (editor.windowRender.linesWindow) {
          editor.windowRender.linesWindow.clear();
          editor.windowRender.windowStartLine = 0;
          for (String line : lines) editor.windowRender.linesWindow.add(line);
        }
        for (int i = 0; i < lines.length; i++) editor.windowRender.putModifiedLine(i, lines[i]);
      }
    }

    editor.cursor.cursorLine = Math.max(0, target.line);
    editor.cursor.cursorChar = Math.max(0, target.ch);
    selection.selStartLine = 0;
    selection.selStartChar = 0;
    selection.selEndLine = 0;
    selection.selEndChar = 0;
    selection.clearSelectionStateAfterDelete();
    editor.scroll.scrollY = 0;
    editor.scroll.scrollX = 0;
    editor.editOperators.lineCountDelta += insertedNl - removedNl;
    synchronized (editor.fileIO.lineOffsetsLock) {
      editor.fileIO.lineOffsets = new long[0];
    }
    editor.fileIO.isIndexReady = false;
    editor.fileIO.isIndexBuilding = false;

    EditOp op = new EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = null;
    op.insertedText = insertText;
    op.insertedEndLine = target.line;
    op.insertedEndChar = target.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    op.entireFileDelete = true;
    editor.highlight.markTyping();
    editor.editOperators.undoStack.addLast(op);
    while (editor.editOperators.undoStack.size() > EditOperators.UNDO_STACK_LIMIT) {
      editor.editOperators.undoStack.removeFirst();
    }
    editor.editOperators.redoStack.clear();
    editor.editOperators.pendingEdits.addLast(op);
    editor.editOperators.pendingRedo.clear();
    editor.editOperators.lastEditTimestamp = op.timestamp;
    editor.requestLayout();
    editor.invalidate();
    editor.postDelayed(
        () -> {
          editor.fileIO.invalidatePendingIOVersionForEdit();
          synchronized (editor.fileIO.directLineCache) {
            editor.fileIO.directLineCache.clear();
          }
          synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.clear();
          }
          editor.windowRender.clearStreamedLineCaches();
          editor.windowRender.currentMaxWindowLineWidth = 0f;
          editor.windowRender.globalMaxLineWidth = 0f;
          editor.fileIO.isFileCleared = false;
          editor.wordWrap.onLineCountChanged();
          invalidateFeatureStateForReplace(0, target.line);
          editor.lineNumber.invalidateLineNumberCache();
          editor.requestLayout();
          editor.invalidate();
        },
        32L);
  }

  private void handleSelectAllReplace(
      String insertText,
      int sL,
      int sC,
      int eL,
      int eC,
      String removedText,
      int beforeL,
      int beforeC,
      int remNl,
      int insNl) {
    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.add("");
      editor.windowRender.windowStartLine = 0;
      editor.fileIO.isEof = true;
    }
    synchronized (editor.fileIO.directLineCache) {
      editor.fileIO.directLineCache.clear();
    }
    editor.windowRender.clearModifiedLines();
    synchronized (editor.windowRender.lineWidthCache) {
      editor.windowRender.lineWidthCache.clear();
    }
    editor.windowRender.clearStreamedLineCaches();
    editor.bracketGuides.invalidateBracketGuideCache(true);
    editor.windowRender.currentMaxWindowLineWidth = 0f;
    editor.windowRender.globalMaxLineWidth = 0f;
    editor.fileIO.isFileCleared = editor.fileIO.sourceFile == null;
    synchronized (editor.fileIO.lineOffsetsLock) {
      editor.fileIO.lineOffsets = new long[0];
    }
    editor.fileIO.isIndexReady = false;
    editor.fileIO.isIndexBuilding = false;
    editor.cursor.cursorLine = 0;
    editor.cursor.cursorChar = 0;
    selection.selStartLine = 0;
    selection.selEndLine = 0;
    selection.selStartChar = 0;
    selection.selEndChar = 0;
    editor.scroll.scrollY = 0;
    editor.scroll.scrollX = 0;
    selection.clearSelectionStateAfterDelete();

    int insertedEndLine = 0;
    if (!insertText.isEmpty()) {
      EditOp.CursorTarget nPos = editor.editOperators.computeCursorAfterInsert(0, 0, insertText);
      editor.cursor.cursorLine = nPos.line;
      editor.cursor.cursorChar = nPos.ch;
      insertedEndLine = nPos.line;
      if (shouldUseBoundedSelectAllPreview(insertText, insNl)) {
        populateBoundedSelectAllPreview(insertText, insertedEndLine);
      } else {
        String[] lines = insertText.split("\n", -1);
        synchronized (editor.windowRender.linesWindow) {
          editor.windowRender.linesWindow.set(0, lines[0]);
          for (int i = 1; i < lines.length; i++) editor.windowRender.linesWindow.add(i, lines[i]);
        }
      }
    }
    editor.wordWrap.onLineCountChanged();
    editor.loadingCircle.endLargeEditUi(true);
    editor.windowRender.recalculateMaxLineWidth();
    editor.requestLayout();
    finalizeAction(remNl, insNl, sL, sC, eL, eC, removedText, insertText, beforeL, beforeC);
    invalidateFeatureStateForReplace(0, insertedEndLine);
  }

  private boolean shouldUseBoundedSelectAllPreview(String insertText, int insertedNewlines) {
    return insertText != null
        && (insertText.length() > EditOperators.LARGE_PASTE_CHARS
            || insertedNewlines > EditOperators.LARGE_PASTE_LINES);
  }

  private void populateBoundedSelectAllPreview(String insertText, int insertedEndLine) {
    int maxLines = Math.max(64, Math.min(512, editor.windowRender.prefetchLines * 2 + 1));
    java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>(maxLines);
    int start = 0;
    for (int i = 0; i <= insertText.length(); i++) {
      if (i == insertText.length() || insertText.charAt(i) == '\n') {
        if (tail.size() == maxLines) tail.removeFirst();
        tail.addLast(insertText.substring(start, i));
        start = i + 1;
      }
    }
    int windowStart = Math.max(0, insertedEndLine - tail.size() + 1);
    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.windowStartLine = windowStart;
      if (tail.isEmpty()) {
        editor.windowRender.linesWindow.add("");
      } else {
        editor.windowRender.linesWindow.addAll(tail);
      }
    }
    int line = windowStart;
    for (String previewLine : tail) {
      editor.windowRender.putModifiedLine(line++, previewLine);
    }
  }

  private void handleSingleLineReplace(
      String insertText,
      int sL,
      int sC,
      int eL,
      int eC,
      String removedText,
      int beforeL,
      int beforeC,
      int remNl,
      int insNl) {
    String beforeLineText = getLineSnapshot(sL);
    editor.fileIO.ensureLineInWindow(sL, true);
    if (editor.fileIO.isWindowLoading
        && (sL < editor.windowRender.windowStartLine
            || sL
                >= editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size())) {
      editor.post(() -> replaceSelectionWithText(insertText));
      return;
    }
    int local = sL - editor.windowRender.windowStartLine;
    if (local >= 0 && local < editor.windowRender.linesWindow.size()) {
      synchronized (editor.windowRender.linesWindow) {
        String line = editor.windowRender.getLineFromWindowLocal(local);
        if (line == null) line = "";
        int a = Math.max(0, Math.min(sC, line.length())),
            b = Math.max(0, Math.min(eC, line.length()));
        if (b < a) {
          int t = a;
          a = b;
          b = t;
        }
        String merged = line.substring(0, a) + insertText + line.substring(b);
        editor.view.updateLocalLine(local, merged);
        editor.windowRender.putModifiedLine(sL, merged);
        editor.cursor.cursorLine = sL;
        editor.cursor.cursorChar = a + insertText.length();
        editor.view.computeWidthForLine(sL, merged);
        editor.windowRender.recalculateMaxLineWidth();
      }
    }
    if (!insertText.isEmpty()) editor.charAnimation.startCharAnimationFromText(insertText);
    selection.clearSelectionStateAfterDelete();
    editor.invalidate();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.loadingCircle.endLargeEditUi(false);
    invalidateFeatureStateForReplace(sL, eL);
    finalizeAction(remNl, insNl, sL, sC, eL, eC, removedText, insertText, beforeL, beforeC);
  }

  private void invalidateFeatureStateForReplace(int startLine, int endLine) {
    editor.highlight.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache(true);
  }

  private void finalizeAction(
      int remNl,
      int insNl,
      int sL,
      int sC,
      int eL,
      int eC,
      String rem,
      String ins,
      int bL,
      int bC) {
    editor.autoCompletion.updateSuggestion();
    editor.editOperators.lineCountDelta += (insNl - remNl);
    selection.recordReplaceSelectionEdit(sL, sC, eL, eC, rem, ins, bL, bC);
  }

  private String getLineSnapshot(int line) {
    if (line < 0) return "";
    String text = editor.windowRender.getLineTextForRender(line);
    return text == null ? "" : text;
  }
}
