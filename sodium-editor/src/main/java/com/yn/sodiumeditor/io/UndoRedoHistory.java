package com.yn.sodiumeditor.io;

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
        undoStack.clear();
        redoStack.clear();
        pendingEdits.clear();
        pendingRedo.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int getUndoSize() {
        return undoStack.size();
    }

    public int getPendingSize() {
        return pendingEdits.size();
    }

    public void pushUndo(EditOp op) {
        undoStack.addLast(op);
        while (undoStack.size() > UNDO_STACK_LIMIT) {
            undoStack.removeFirst();
        }
    }

    public EditOp popUndo() {
        return undoStack.isEmpty() ? null : undoStack.removeLast();
    }

    public void pushRedo(EditOp op) {
        redoStack.addLast(op);
    }

    public EditOp popRedo() {
        return redoStack.isEmpty() ? null : redoStack.removeLast();
    }
}
