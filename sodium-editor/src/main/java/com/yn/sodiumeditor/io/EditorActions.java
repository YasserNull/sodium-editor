package com.yn.sodiumeditor.io;

import android.os.Looper;
import com.yn.sodiumeditor.SodiumEditor;

/** High-level editor actions like inserting and deleting characters or text. */
public class EditorActions {
  private final SodiumEditor editor;
  private final EditOperators operators;

  public EditorActions(SodiumEditor editor, EditOperators operators) {
    this.editor = editor;
    this.operators = operators;
  }

  public void deleteForwardAtCursor() {
    if (editor.view.isReadOnly) return;
    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.fileIO.invalidatePendingIOForEdit();
    operators.editVersion.incrementAndGet();
    editor.autoSuggestion.clearActiveSuggestion();

    if (editor.ime.hasComposing) {
      editor.ime.deleteComposing();
      return;
    }

    editor.ime.lastImeCommitText = null;
    editor.ime.lastImeCommitUptime = 0L;

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    final int requestedLine = editor.cursor.cursorLine;
    final int requestedChar = editor.cursor.cursorChar;
    editor.fileIO.ensureLineInWindow(requestedLine, true);
    if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(requestedLine)) {
      editor.post(
          () -> {
            editor.cursor.cursorLine = requestedLine;
            editor.cursor.cursorChar = requestedChar;
            deleteForwardAtCursor();
          });
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
      localIdx = handleWindowEdgeCase(localIdx);
    }

    synchronized (editor.windowRender.linesWindow) {
      String base = editor.windowRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (editor.cursor.cursorChar < base.length()) {
        int safeCursorChar = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
        int deleteEnd = nextCodePointEnd(base, safeCursorChar);
        String removed = base.substring(safeCursorChar, deleteEnd);
        android.graphics.Paint removedPaint =
            editor.highlightRender.getPaintForChar(beforeLine, safeCursorChar, base);
        String modified = base.substring(0, safeCursorChar) + base.substring(deleteEnd);
        editor.view.updateLocalLine(localIdx, modified);
        editor.windowRender.putModifiedLine(editor.cursor.cursorLine, modified);
        editor.highlight.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
        editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
        editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
        editor.charAnimation.startDeleteAnimation(
            beforeLine, safeCursorChar, removed, removedPaint);

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = safeCursorChar;
        op.endLine = beforeLine;
        op.endChar = deleteEnd;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeCursorChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        operators.recorder.recordEdit(op);
      } else {
        int nextGlobal = editor.cursor.cursorLine + 1;
        editor.fileIO.ensureLineInWindow(nextGlobal, true);

        int currentLocalIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
        int currentNextLocal = nextGlobal - editor.windowRender.windowStartLine;

        if (currentLocalIdx >= 0
            && currentLocalIdx < editor.windowRender.linesWindow.size()
            && currentNextLocal >= 0
            && currentNextLocal < editor.windowRender.linesWindow.size()) {
          String next = editor.windowRender.getLineFromWindowLocal(currentNextLocal);
          String merged = base + (next == null ? "" : next);
          editor.view.updateLocalLine(currentLocalIdx, merged);
          editor.windowRender.putModifiedLine(editor.cursor.cursorLine, merged);
          editor.windowRender.clearStreamedLineInfo(editor.cursor.cursorLine);
          editor.highlight.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
          editor.windowRender.linesWindow.remove(currentNextLocal);
          operators.shifter.shiftModifiedLines(nextGlobal, -1);

          editor.windowRender.recalculateMaxLineWidth();
          editor.view.computeWidthForLine(editor.cursor.cursorLine, merged);
          editor.lineNumber.invalidateLineNumberCache();
          editor.wordWrap.onLineCountChanged();
          editor.invalidate();
          operators.lineCountDelta -= 1;

          EditOp op = new EditOp();
          op.startLine = beforeLine;
          op.startChar = base.length();
          op.endLine = nextGlobal;
          op.endChar = 0;
          op.removedText = "\n";
          op.insertedText = "";
          op.insertedEndLine = beforeLine;
          op.insertedEndChar = base.length();
          op.cursorLineBefore = beforeLine;
          op.cursorCharBefore = beforeChar;
          op.cursorLineAfter = editor.cursor.cursorLine;
          op.cursorCharAfter = editor.cursor.cursorChar;
          op.timestamp = System.currentTimeMillis();
          operators.recorder.recordEdit(op);
        }
      }
    }
    editor.ime.updateImeSelection();
    editor.autoSuggestion.updateSuggestion();
  }

  private boolean isLineInLoadedWindow(int line) {
    return line >= editor.windowRender.windowStartLine
        && line < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
  }

  public void insertCharAtCursor(char c) {
    long opStartMs = android.os.SystemClock.uptimeMillis();
    if (editor.view.isReadOnly) return;
    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.fileIO.invalidatePendingIOForEdit();
    operators.editVersion.incrementAndGet();

    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(String.valueOf(c));
      return;
    }

    if (editor.ime.hasComposing) {
      editor.ime.onFinishComposingText();
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    int originalLine = beforeLine;
    int originalChar = beforeChar;

    long ensureStartMs = android.os.SystemClock.uptimeMillis();
    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
      if (insertCharIntoOffWindowLine(c, beforeLine, beforeChar)) {
        return;
      }
      final int retryLine = editor.cursor.cursorLine;
      final int retryChar = editor.cursor.cursorChar;
      editor.post(
          () -> {
            editor.cursor.cursorLine = retryLine;
            editor.cursor.cursorChar = retryChar;
            insertCharAtCursor(c);
          });
      return;
    }
    long ensureMs = android.os.SystemClock.uptimeMillis() - ensureStartMs;

    int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
      if (insertCharIntoOffWindowLine(c, beforeLine, beforeChar)) {
        return;
      }
      localIdx = handleWindowEdgeCase(localIdx);
    }

    boolean fullInvalidate = false;
    long editMs = 0L;
    long highlightMs = 0L;
    long widthMs = 0L;
    long invalidateMs = 0L;
    synchronized (editor.windowRender.linesWindow) {
      String base = editor.windowRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        long editStartMs = android.os.SystemClock.uptimeMillis();
        fullInvalidate = true;
        int oldLineCount = editor.view.getLinesCount();
        String before = base.substring(0, Math.min(editor.cursor.cursorChar, base.length()));
        String after = base.substring(Math.min(editor.cursor.cursorChar, base.length()));

        editor.view.updateLocalLine(localIdx, before);
        editor.windowRender.linesWindow.add(localIdx + 1, after);

        operators.shifter.shiftModifiedLines(editor.cursor.cursorLine + 1, 1);

        editor.windowRender.putModifiedLine(editor.cursor.cursorLine, before);
        editor.windowRender.putModifiedLine(editor.cursor.cursorLine + 1, after);
        operators.lineCountDelta += 1;
        editMs = android.os.SystemClock.uptimeMillis() - editStartMs;

        long widthStartMs = android.os.SystemClock.uptimeMillis();
        editor.view.computeWidthForLine(editor.cursor.cursorLine, before);
        editor.view.computeWidthForLine(editor.cursor.cursorLine + 1, after);
        widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;

        editor.lineNumber.invalidateLineNumberCache();
        editor.cursor.cursorLine++;
        editor.cursor.cursorChar = 0;

        if (editor.lineNumber.showLineNumbers
            && String.valueOf(oldLineCount).length()
                != String.valueOf(editor.view.getLinesCount()).length()) {
          editor.requestLayout();
        }
        editor.lineNumber.updateGutterWidth();
        editor.wordWrap.onLineCountChanged();
      } else {
        long editStartMs = android.os.SystemClock.uptimeMillis();
        int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          pos = editor.binaryRender.snapBinaryCursor(base, pos, editor.cursor.cursorLine);
          editor.cursor.cursorChar = pos;
        }
        String modified = base.substring(0, pos) + c + base.substring(pos);
        editor.view.updateLocalLine(localIdx, modified);
        editor.windowRender.putModifiedLine(editor.cursor.cursorLine, modified);

        synchronized (editor.windowRender.avgCharWidthCache) {
          editor.windowRender.avgCharWidthCache.remove(editor.cursor.cursorLine);
        }
        synchronized (editor.windowRender.lineWidthCache) {
          editor.windowRender.lineWidthCache.remove(editor.cursor.cursorLine);
        }

        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, pos, 1, 0);
        }
        editMs = android.os.SystemClock.uptimeMillis() - editStartMs;
        long highlightStartMs = android.os.SystemClock.uptimeMillis();
        editor.highlight.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
        highlightMs = android.os.SystemClock.uptimeMillis() - highlightStartMs;
        editor.cursor.cursorChar++;
        editor.charAnimation.startCharAnimationFromText(String.valueOf(c));
        long widthStartMs = android.os.SystemClock.uptimeMillis();
        editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
        widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;
      }
      long invalidateStartMs = android.os.SystemClock.uptimeMillis();
      if (fullInvalidate) editor.invalidate();
      else editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
      editor.scroll.keepCursorVisibleHorizontally();
    }
    editor.autoSuggestion.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    EditOp.CursorTarget insertedEnd =
        operators.recorder.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    operators.recorder.recordEdit(op);
  }

  private boolean insertCharIntoOffWindowLine(char c, int beforeLine, int beforeChar) {
    if (isLineInLoadedWindow(beforeLine)) return false;
    String base = getDirectEditableLineText(beforeLine);
    if (base == null) return false;

    long startMs = android.os.SystemClock.uptimeMillis();
    if (c == '\n') {
      int pos = Math.max(0, Math.min(beforeChar, base.length()));
      String before = base.substring(0, pos);
      String after = base.substring(pos);
      operators.shifter.shiftModifiedLines(beforeLine + 1, 1);
      editor.windowRender.putModifiedLine(beforeLine, before);
      editor.windowRender.putModifiedLine(beforeLine + 1, after);
      operators.lineCountDelta += 1;
      editor.view.computeWidthForLine(beforeLine, before);
      editor.view.computeWidthForLine(beforeLine + 1, after);
      editor.cursor.cursorLine = beforeLine + 1;
      editor.cursor.cursorChar = 0;
      editor.wordWrap.onLineCountChanged();
      editor.lineNumber.invalidateLineNumberCache();
      editor.invalidate();
    } else {
      int pos = Math.max(0, Math.min(beforeChar, base.length()));
      String modified = base.substring(0, pos) + c + base.substring(pos);
      editor.windowRender.putModifiedLine(beforeLine, modified);
      synchronized (editor.windowRender.avgCharWidthCache) {
        editor.windowRender.avgCharWidthCache.remove(beforeLine);
      }
      synchronized (editor.windowRender.lineWidthCache) {
        editor.windowRender.lineWidthCache.remove(beforeLine);
      }
      editor.highlight.invalidateHighlightCacheForLine(beforeLine);
      editor.cursor.cursorLine = beforeLine;
      editor.cursor.cursorChar = pos + 1;
      editor.view.computeWidthForLine(beforeLine, modified);
      editor.view.invalidateLineGlobal(beforeLine);
      editor.cursor.invalidateCursorArea();
      editor.scroll.keepCursorVisibleHorizontally();
    }

    editor.autoSuggestion.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    EditOp.CursorTarget insertedEnd =
        operators.recorder.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    operators.recorder.recordEdit(op);

    return true;
  }

  public void deleteCharAtCursor() {
    if (editor.view.isReadOnly) return;
    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    editor.fileIO.invalidatePendingIOForEdit();
    operators.editVersion.incrementAndGet();
    editor.autoSuggestion.clearActiveSuggestion();

    if (editor.ime.hasComposing) {
      editor.ime.deleteComposing();
      return;
    }

    editor.ime.lastImeCommitText = null;
    editor.ime.lastImeCommitUptime = 0L;

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    final int requestedLine = editor.cursor.cursorLine;
    final int requestedChar = editor.cursor.cursorChar;
    editor.fileIO.ensureLineInWindow(requestedLine, true);
    if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(requestedLine)) {
      editor.post(
          () -> {
            editor.cursor.cursorLine = requestedLine;
            editor.cursor.cursorChar = requestedChar;
            deleteCharAtCursor();
          });
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
      localIdx = handleWindowEdgeCase(localIdx);
    }

    synchronized (editor.windowRender.linesWindow) {
      String base = editor.windowRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";
      int safeCursorChar = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));

      if (safeCursorChar > 0) {
        int safeStart = previousCodePointStart(base, safeCursorChar);
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          int[] binarySpan = editor.textRender.binaryTokenSpanTmp;
          if (editor.binaryRender.findBinaryTokenSpanInSpans(
              editor.binaryRender.getBinaryTokenSpans(editor.cursor.cursorLine),
              safeCursorChar - 1,
              binarySpan)) {
            safeStart = Math.max(0, Math.min(binarySpan[0], base.length()));
            safeCursorChar = Math.max(safeStart, Math.min(binarySpan[1], base.length()));
          }
        }
        String removed = base.substring(safeStart, safeCursorChar);
        android.graphics.Paint removedPaint =
            editor.highlightRender.getPaintForChar(beforeLine, safeStart, base);

        String modified = base.substring(0, safeStart) + base.substring(safeCursorChar);
        editor.view.updateLocalLine(localIdx, modified);
        editor.windowRender.putModifiedLine(editor.cursor.cursorLine, modified);
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          editor.binaryRender.adjustBinaryTokenSpansForEdit(
              editor.cursor.cursorLine, safeStart, safeStart - safeCursorChar, 0);
        }
        editor.highlight.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
        editor.cursor.cursorChar = safeStart;
        editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
        editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
        editor.cursor.invalidateCursorArea();
        editor.charAnimation.startDeleteAnimation(beforeLine, safeStart, removed, removedPaint);

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = safeStart;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeStart;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        operators.recorder.recordEdit(op);
      } else if (editor.cursor.cursorLine > 0) {
        int deletedLine = editor.cursor.cursorLine;
        int prevGlobal = editor.cursor.cursorLine - 1;
        editor.fileIO.ensureLineInWindow(prevGlobal, true);

        int currentPrevLocal = prevGlobal - editor.windowRender.windowStartLine;
        int currentDeletedLocal = deletedLine - editor.windowRender.windowStartLine;

        String prev = editor.windowRender.getLineTextForRender(prevGlobal);
        if (prev == null) prev = "";
        String merged = prev + base;

        editor.windowRender.putModifiedLine(prevGlobal, merged);
        editor.windowRender.clearStreamedLineInfo(prevGlobal);

        synchronized (editor.windowRender.lineWidthCache) {
          editor.windowRender.lineWidthCache.remove(prevGlobal);
          editor.windowRender.lineWidthCache.remove(deletedLine);
        }
        synchronized (editor.windowRender.avgCharWidthCache) {
          editor.windowRender.avgCharWidthCache.remove(prevGlobal);
          editor.windowRender.avgCharWidthCache.remove(deletedLine);
        }

        editor.highlight.invalidateHighlightCacheForLine(prevGlobal);

        if (currentPrevLocal >= 0 && currentPrevLocal < editor.windowRender.linesWindow.size()) {
          editor.view.updateLocalLine(currentPrevLocal, merged);
        }

        if (currentDeletedLocal >= 0
            && currentDeletedLocal < editor.windowRender.linesWindow.size()) {
          editor.windowRender.linesWindow.remove(currentDeletedLocal);
        }

        // Shift after window removal to ensure consistency
        operators.shifter.shiftModifiedLines(deletedLine, -1);

        // Prevent auto-reload while state stabilizes
        editor.fileIO.invalidatePendingIOForEdit();

        editor.windowRender.recalculateMaxLineWidth();
        editor.cursor.cursorLine = prevGlobal;
        editor.cursor.cursorChar = prev.length();
        editor.view.computeWidthForLine(prevGlobal, merged);
        operators.lineCountDelta -= 1;
        editor.wordWrap.onLineCountChanged();
        editor.lineNumber.invalidateLineNumberCache();
        editor.cursor.invalidateCursorArea();
        editor.invalidate();

        EditOp op = new EditOp();
        op.startLine = prevGlobal;
        op.startChar = (prev == null ? 0 : prev.length());
        op.endLine = beforeLine;
        op.endChar = 0;
        op.removedText = "\n";
        op.insertedText = "";
        op.insertedEndLine = prevGlobal;
        op.insertedEndChar = op.startChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        operators.recorder.recordEdit(op);
      }
    }
    editor.ime.updateImeSelection();
    editor.autoSuggestion.updateSuggestion();
  }

  private int previousCodePointStart(String text, int offset) {
    int safeOffset = Math.max(0, Math.min(offset, text.length()));
    if (safeOffset <= 0) return 0;
    return text.offsetByCodePoints(safeOffset, -1);
  }

  private int nextCodePointEnd(String text, int offset) {
    int safeOffset = Math.max(0, Math.min(offset, text.length()));
    if (safeOffset >= text.length()) return text.length();
    return text.offsetByCodePoints(safeOffset, 1);
  }

  private String getEditableLineText(int line) {
    return editor.windowRender.getLineTextForRender(line);
  }

  private String getDirectEditableLineText(int line) {
    if (line < 0) return null;
    synchronized (editor.windowRender.modifiedLines) {
      String modified = editor.windowRender.getModifiedLine(line);
      if (modified != null) return modified;
    }
    String windowText = editor.windowRender.getLineTextForRender(line);
    if (isLineInLoadedWindow(line)) return windowText == null ? "" : windowText;
    if (editor.fileIO.sourceFile == null || !editor.fileIO.isIndexReady) return null;
    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    return direct.containsKey(line) ? direct.get(line) : null;
  }

  private String printableChar(char c) {
    if (c == '\n') return "\\n";
    if (c == '\t') return "\\t";
    return String.valueOf(c);
  }

  private int handleWindowEdgeCase(int localIdx) {
    synchronized (editor.windowRender.linesWindow) {
      if (editor.windowRender.linesWindow.isEmpty()) {
        editor.windowRender.linesWindow.add("");
        return 0;
      } else if (editor.fileIO.isEof && localIdx >= editor.windowRender.linesWindow.size()) {
        while (editor.windowRender.linesWindow.size() <= localIdx)
          editor.windowRender.linesWindow.add("");
        return localIdx;
      }
    }
    return Math.max(0, Math.min(localIdx, editor.windowRender.linesWindow.size() - 1));
  }

  public void insertTextAtCursor(String text) {
    if (editor.view.isReadOnly || text == null || text.isEmpty()) return;
    editor.cursorHandle.hideForTyping();
    editor.caret.pauseBlinkForTyping();
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(text);
      return;
    }
    if (text.length() == 1) {
      insertCharAtCursor(text.charAt(0));
      return;
    }

    insertTextAtCursorBatch(text);
  }

  private void insertTextAtCursorBatch(String text) {
    editor.fileIO.invalidatePendingIOForEdit();
    operators.editVersion.incrementAndGet();

    if (editor.ime.hasComposing) {
      editor.ime.onFinishComposingText();
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    editor.fileIO.ensureLineInWindow(beforeLine, true);
    if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(beforeLine)) {
      editor.post(
          () -> {
            editor.cursor.cursorLine = beforeLine;
            editor.cursor.cursorChar = beforeChar;
            insertTextAtCursorBatch(text);
          });
      return;
    }

    int localIdx = beforeLine - editor.windowRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
      localIdx = handleWindowEdgeCase(localIdx);
    }

    String base;
    synchronized (editor.windowRender.linesWindow) {
      base = editor.windowRender.getLineFromWindowLocal(localIdx);
    }
    if (base == null) base = "";
    int safeChar = Math.max(0, Math.min(beforeChar, base.length()));
    EditOp.CursorTarget target =
        operators.recorder.computeCursorAfterInsert(beforeLine, safeChar, text);

    synchronized (editor.windowRender.lineWidthCache) {
      editor.windowRender.lineWidthCache.clear();
    }
    synchronized (editor.windowRender.avgCharWidthCache) {
      editor.windowRender.avgCharWidthCache.clear();
    }
    editor.windowRender.applyMultiLineReplaceInWindowNow(
        beforeLine, safeChar, beforeLine, safeChar, text, target);

    int insertedNewlines = operators.recorder.countNewlines(text);
    operators.lineCountDelta += insertedNewlines;
    editor.highlight.invalidateHighlightCacheForLine(beforeLine);
    editor.bracketGuides.invalidateBracketGuideCache(true);
    editor.scroll.keepCursorVisibleHorizontally();
    editor.cursor.invalidateCursorArea();
    editor.invalidate();
    editor.autoSuggestion.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = safeChar;
    op.endLine = beforeLine;
    op.endChar = safeChar;
    op.removedText = "";
    op.insertedText = text;
    op.insertedEndLine = target.line;
    op.insertedEndChar = target.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    operators.recorder.recordEdit(op);
  }

  public void insertTextAt(int line, int col, String text) {
    if (text == null) return;
    if (Looper.myLooper() != Looper.getMainLooper()) {
      editor.post(() -> insertTextAt(line, col, text));
      return;
    }
    editor.cursor.setCursorPosition(line, col);
    editor.editOperators.insertTextAtCursor(text);
  }
}
