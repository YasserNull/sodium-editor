package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;

public class Undo {
    private final SodiumEditor editor;
    private final EditOperators operators;

    public Undo(SodiumEditor editor, EditOperators operators) {
        this.editor = editor;
        this.operators = operators;
    }

    public void execute() {
        EditOp op = operators.history.popUndo();
        if (op == null) return;
        operators.history.pushRedo(op);
        if (!operators.history.pendingEdits.isEmpty()) {
            operators.history.pendingEdits.removeLast();
            operators.history.pendingRedo.addLast(op);
        }
        operators.isApplyingUndoRedo = true;
        operators.applyEditForUndoRedo(
                op.startLine,
                op.startChar,
                op.insertedEndLine,
                op.insertedEndChar,
                op.removedText == null ? "" : op.removedText,
                op.cursorLineBefore,
                op.cursorCharBefore);
        operators.isApplyingUndoRedo = false;
    }
}
