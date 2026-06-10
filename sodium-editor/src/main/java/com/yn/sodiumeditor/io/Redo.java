package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;

public class Redo {
  private final SodiumEditor editor;
  private final EditOperators operators;

  public Redo(SodiumEditor editor, EditOperators operators) {
    this.editor = editor;
    this.operators = operators;
  }

  public void execute() {
    EditOp op = operators.history.popRedo();
    if (op == null) return;
    operators.history.pushUndo(op);
    EditOp lastPending = operators.history.pendingEdits.peekLast();
    if (lastPending != null && lastPending.pendingUndoOfSavedOp && lastPending.originalOp == op) {
      operators.history.pendingEdits.removeLast();
    } else if (!operators.history.pendingRedo.isEmpty()) {
      operators.history.pendingRedo.removeLast();
      operators.history.pendingEdits.addLast(op);
    } else if (!operators.history.pendingEdits.isEmpty()) {
      operators.history.pendingEdits.removeLast();
      operators.history.pendingEdits.addLast(op);
    } else {
      operators.history.pendingEdits.addLast(op);
    }
    operators.clearFileStateDirtyAfterSave();
    if (op.entireFileDelete && (op.insertedText == null || op.insertedText.isEmpty())) {
      applyEntireFileDeleteForRedo(op);
      return;
    }
    operators.isApplyingUndoRedo = true;
    operators.applyEditForUndoRedo(
        op.startLine,
        op.startChar,
        op.endLine,
        op.endChar,
        op.insertedText == null ? "" : op.insertedText,
        op.cursorLineAfter,
        op.cursorCharAfter);
    operators.isApplyingUndoRedo = false;
  }

  private void applyEntireFileDeleteForRedo(EditOp op) {
    operators.isApplyingUndoRedo = true;
    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.add("");
      editor.windowRender.windowStartLine = 0;
      editor.fileIO.isEof = true;
    }
    editor.windowRender.clearModifiedLines();
    synchronized (editor.fileIO.directLineCache) {
      editor.fileIO.directLineCache.clear();
    }
    synchronized (editor.windowRender.lineWidthCache) {
      editor.windowRender.lineWidthCache.clear();
    }
    editor.windowRender.clearStreamedLineCaches();
    editor.windowRender.currentMaxWindowLineWidth = 0f;
    editor.windowRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.scrollY = 0f;
    editor.scroll.scrollX = 0f;
    synchronized (editor.fileIO.lineOffsetsLock) {
      editor.fileIO.lineOffsets = new long[0];
    }
    editor.fileIO.isIndexReady = false;
    editor.fileIO.isIndexBuilding = false;
    editor.cursor.setCursorPosition(
        Math.max(0, op.cursorLineAfter), Math.max(0, op.cursorCharAfter));
    editor.selection.clearSelection();
    operators.lineCountDelta = 0;
    editor.wordWrap.onLineCountChanged();
    editor.lineNumber.invalidateLineNumberCache();
    editor.highlight.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache(true);
    editor.requestLayout();
    editor.invalidate();
    operators.isApplyingUndoRedo = false;
  }
}
