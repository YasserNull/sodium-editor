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
        if (!operators.history.pendingRedo.isEmpty()) {
            operators.history.pendingRedo.removeLast();
            operators.history.pendingEdits.addLast(op);
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
}
