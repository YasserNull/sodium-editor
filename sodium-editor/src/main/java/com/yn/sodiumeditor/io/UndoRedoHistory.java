package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.ArrayDeque;

/**
 * Manages the history of edit operations for undo and redo.
 */
public class UndoRedoHistory {
    public static final int UNDO_STACK_LIMIT = 200;

    public final ArrayDeque<EditOp> undoStack = new ArrayDeque<>();
    public final ArrayDeque<EditOp> redoStack = new ArrayDeque<>();
    public final ArrayDeque<EditOp> pendingEdits = new ArrayDeque<>();
    public final ArrayDeque<EditOp> pendingRedo = new ArrayDeque<>();

    public void clear() {
        FunctionLog.f("UndoRedoHistory", "clear");
        undoStack.clear();
        redoStack.clear();
        pendingEdits.clear();
        pendingRedo.clear();
    }

    public boolean canUndo() {
        FunctionLog.f("UndoRedoHistory", "canUndo");
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        FunctionLog.f("UndoRedoHistory", "canRedo");
        return !redoStack.isEmpty();
    }

    public int getUndoSize() {
        FunctionLog.f("UndoRedoHistory", "getUndoSize");
        return undoStack.size();
    }

    public int getPendingSize() {
        FunctionLog.f("UndoRedoHistory", "getPendingSize");
        return pendingEdits.size();
    }

    public void pushUndo(EditOp op) {
        FunctionLog.f("UndoRedoHistory", "pushUndo", op);
        undoStack.addLast(op);
        while (undoStack.size() > UNDO_STACK_LIMIT) {
            undoStack.removeFirst();
        }
    }

    public EditOp popUndo() {
        FunctionLog.f("UndoRedoHistory", "popUndo");
        return undoStack.isEmpty() ? null : undoStack.removeLast();
    }

    public void pushRedo(EditOp op) {
        FunctionLog.f("UndoRedoHistory", "pushRedo", op);
        redoStack.addLast(op);
    }

    public EditOp popRedo() {
        FunctionLog.f("UndoRedoHistory", "popRedo");
        return redoStack.isEmpty() ? null : redoStack.removeLast();
    }
}
