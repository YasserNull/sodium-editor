package com.yn.sodiumeditor.io;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

/** Main facade for edit operations. Delegating to specialized components. */
public class EditOperators {
  public static final int UNDO_STACK_LIMIT = 200;
  public static final int UNDO_TEXT_LIMIT = 1_000_000;
  public static final int LARGE_PASTE_LINES = 1500;
  public static final int LARGE_PASTE_CHARS = 200_000;

  private final SodiumEditor editor;

  // Components
  public final UndoRedoHistory history = new UndoRedoHistory();
  public final ByteRangeLocator locator;
  public final LineCacheShifter shifter;
  public final FileEditHandler fileHandler;
  public final EditRecordManager recorder;
  public final EditorActions actions;
  private final Undo undo;
  private final Redo redo;

  // Stacks Aliases (to avoid breaking field access in other classes)
  public final java.util.ArrayDeque<EditOp> undoStack = history.undoStack;
  public final java.util.ArrayDeque<EditOp> redoStack = history.redoStack;
  public final java.util.ArrayDeque<EditOp> pendingEdits = history.pendingEdits;
  public final java.util.ArrayDeque<EditOp> pendingRedo = history.pendingRedo;

  // State
  public boolean isApplyingUndoRedo = false;
  public volatile long lastEditTimestamp = 0L;
  public int lineCountDelta = 0;
  public boolean fileStateDirtyAfterUndoRestore = false;
  public final java.util.concurrent.atomic.AtomicInteger editVersion =
      new java.util.concurrent.atomic.AtomicInteger(0);

  public EditOperators(SodiumEditor editor) {
    this.editor = editor;
    this.locator = new ByteRangeLocator(editor);
    this.shifter = new LineCacheShifter(editor);
    this.fileHandler = new FileEditHandler(editor, this);
    this.recorder = new EditRecordManager(editor, this);
    this.actions = new EditorActions(editor, this);
    this.undo = new Undo(editor, this);
    this.redo = new Redo(editor, this);
  }

  // ==============================
  // Bridge Methods
  // ==============================

  public boolean canUndo() {
    return history.canUndo();
  }

  public boolean canRedo() {
    return history.canRedo();
  }

  public int getUndoStackSize() {
    return history.getUndoSize();
  }

  public int getPendingEditsCount() {
    return history.getPendingSize() + (fileStateDirtyAfterUndoRestore ? 1 : 0);
  }

  public int getPendingFileEditOperationsCount() {
    return history.getPendingSize();
  }

  public void clearUndoRedoHistory() {
    history.clear();
    fileStateDirtyAfterUndoRestore = false;
  }

  public long getLastEditTimestamp() {
    return lastEditTimestamp;
  }

  public void undo() {
    undo.execute();
  }

  public void redo() {
    redo.execute();
  }

  public void insertCharAtCursor(char c) {
    actions.insertCharAtCursor(c);
  }

  public void deleteCharAtCursor() {
    actions.deleteCharAtCursor();
  }

  public void deleteForwardAtCursor() {
    actions.deleteForwardAtCursor();
  }

  public void insertStringAtCursor(String text) {
    actions.insertTextAtCursor(text);
  }

  public void insertTextAtCursor(String text) {
    actions.insertTextAtCursor(text);
  }

  public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    fileHandler.applyPendingEditsToFileAsync(onComplete);
  }

  public void markFileStateDirtyAfterUndoRestore() {
    fileStateDirtyAfterUndoRestore = true;
    lastEditTimestamp = System.currentTimeMillis();
  }

  public void clearFileStateDirtyAfterSave() {
    fileStateDirtyAfterUndoRestore = false;
  }

  public void recordEdit(EditOp op) {
    recorder.recordEdit(op);
  }

  public void recordEditNoUndo(EditOp op) {
    recorder.recordEditNoUndo(op);
  }

  public int countNewlines(@Nullable String text) {
    return recorder.countNewlines(text);
  }

  public EditOp.CursorTarget computeCursorAfterInsert(
      int baseLine, int baseChar, String insertText) {
    return recorder.computeCursorAfterInsert(baseLine, baseChar, insertText);
  }

  public int comparePos(int l1, int c1, int l2, int c2) {
    return locator.comparePos(l1, c1, l2, c2);
  }

  public EditOp.RangeBytes computeByteRangeFastOrScan(
      java.io.File file, int sL, int sC, int eL, int eC) {
    return locator.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int targetLine)
      throws Exception {
    return locator.findLineStartByteByScanning(raf, targetLine);
  }

  public void rewriteReplaceRangeAsync(
      int opToken,
      java.io.File inFile,
      int sL,
      int sC,
      int eL,
      int eC,
      String insertText,
      EditOp.CursorTarget target,
      boolean finishLargeEditUi) {
    fileHandler.rewriteReplaceRangeAsync(
        opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
  }

  // ==============================
  // Shared Logic
  // ==============================

  public void applyEditForUndoRedo(
      int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
    editor.selection.setSelectionInternal(sL, sC, eL, eC);
    editor.selection.replaceSelectionWithText(text);
    editor.cursor.setCursorPosition(cursorLine, cursorChar);
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.wordWrap.invalidateWrapMetrics(true);
      editor.wordWrap.requestWrapPrefixRebuild();
    }
    editor.lineNumber.invalidateLineNumberCache();
    editor.invalidate();
  }
}
