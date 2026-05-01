package com.yn.sodiumeditor.io;

import android.util.Log;
import androidx.annotation.Nullable;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Main facade for edit operations. Delegating to specialized components.
 */
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
    public final java.util.concurrent.atomic.AtomicInteger editVersion = new java.util.concurrent.atomic.AtomicInteger(0);

    public EditOperators(SodiumEditor editor) {
        FunctionLog.f("EditOperators", "EditOperators", editor);
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

    public boolean canUndo() { FunctionLog.f("EditOperators", "canUndo"); return history.canUndo(); }
    public boolean canRedo() { FunctionLog.f("EditOperators", "canRedo"); return history.canRedo(); }
    public int getUndoStackSize() { FunctionLog.f("EditOperators", "getUndoStackSize"); return history.getUndoSize(); }
    public int getPendingEditsCount() { FunctionLog.f("EditOperators", "getPendingEditsCount"); return history.getPendingSize(); }
    public void clearUndoRedoHistory() { FunctionLog.f("EditOperators", "clearUndoRedoHistory"); history.clear(); }
    public long getLastEditTimestamp() { FunctionLog.f("EditOperators", "getLastEditTimestamp"); return lastEditTimestamp; }
    
    public void undo() { FunctionLog.f("EditOperators", "undo"); undo.execute(); }
    public void redo() { FunctionLog.f("EditOperators", "redo"); redo.execute(); }

    public void insertCharAtCursor(char c) { FunctionLog.f("EditOperators", "insertCharAtCursor", c); actions.insertCharAtCursor(c); }
    public void deleteCharAtCursor() { FunctionLog.f("EditOperators", "deleteCharAtCursor"); actions.deleteCharAtCursor(); }
    public void deleteForwardAtCursor() { FunctionLog.f("EditOperators", "deleteForwardAtCursor"); actions.deleteForwardAtCursor(); }
    public void insertStringAtCursor(String text) { FunctionLog.f("EditOperators", "insertStringAtCursor", text); actions.insertTextAtCursor(text); }
    public void insertTextAtCursor(String text) { FunctionLog.f("EditOperators", "insertTextAtCursor", text); actions.insertTextAtCursor(text); }
    public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) { FunctionLog.f("EditOperators", "applyPendingEditsToFileAsync", onComplete); fileHandler.applyPendingEditsToFileAsync(onComplete); }

    public void recordEdit(EditOp op) { FunctionLog.f("EditOperators", "recordEdit", op); recorder.recordEdit(op); }
    public void recordEditNoUndo(EditOp op) { FunctionLog.f("EditOperators", "recordEditNoUndo", op); recorder.recordEditNoUndo(op); }
    public int countNewlines(@Nullable String text) { FunctionLog.f("EditOperators", "countNewlines", text); return recorder.countNewlines(text); }
    public EditOp.CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
        FunctionLog.f("EditOperators", "computeCursorAfterInsert", baseLine, baseChar, insertText);
        return recorder.computeCursorAfterInsert(baseLine, baseChar, insertText);
    }

    public int comparePos(int l1, int c1, int l2, int c2) { FunctionLog.f("EditOperators", "comparePos", l1, c1, l2, c2); return locator.comparePos(l1, c1, l2, c2); }
    public EditOp.RangeBytes computeByteRangeFastOrScan(java.io.File file, int sL, int sC, int eL, int eC) {
        FunctionLog.f("EditOperators", "computeByteRangeFastOrScan", file, sL, sC, eL, eC);
        return locator.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
    }
    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int targetLine) throws Exception {
        FunctionLog.f("EditOperators", "findLineStartByteByScanning", raf, targetLine);
        return locator.findLineStartByteByScanning(raf, targetLine);
    }

    public void rewriteReplaceRangeAsync(int opToken, java.io.File inFile, int sL, int sC, int eL, int eC, String insertText, EditOp.CursorTarget target, boolean finishLargeEditUi) {
        FunctionLog.f("EditOperators", "rewriteReplaceRangeAsync", opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
        fileHandler.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
    }

    // ==============================
    // Shared Logic
    // ==============================

    public void applyEditForUndoRedo(int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
        FunctionLog.f("EditOperators", "applyEditForUndoRedo", sL, sC, eL, eC, text, cursorLine, cursorChar);
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
