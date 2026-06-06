package com.yn.sodiumeditor.io;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles recording of edit operations and merging consecutive insertions.
 */
public class EditRecordManager {
    public static final int UNDO_TEXT_LIMIT = 1_000_000;
    
    private final SodiumEditor editor;
    private final EditOperators operators;

    public EditRecordManager(SodiumEditor editor, EditOperators operators) {
        this.editor = editor;
        this.operators = operators;
    }

    public void recordEdit(EditOp op) {
        if (operators.isApplyingUndoRedo) return;
        if (op == null) return;
        editor.highlite.markTyping();
        
        boolean tooLarge = (op.removedText != null && op.removedText.length() > UNDO_TEXT_LIMIT)
                || (op.insertedText != null && op.insertedText.length() > UNDO_TEXT_LIMIT);
        if (tooLarge) {
            recordEditNoUndo(op);
            return;
        }

        if (tryAbsorbDeleteIntoPendingInsert(op)) {
            operators.history.redoStack.clear();
            operators.history.pendingRedo.clear();
            operators.lastEditTimestamp = op.timestamp;
            return;
        }

        boolean insertOnly = (op.removedText == null || op.removedText.isEmpty())
                && op.insertedText != null && !op.insertedText.isEmpty();

        if (insertOnly) {
            EditOp lastPending = operators.history.pendingEdits.peekLast();
            if (lastPending != null
                    && (lastPending.removedText == null || lastPending.removedText.isEmpty())
                    && lastPending.insertedText != null && !lastPending.insertedText.isEmpty()
                    && lastPending.insertedEndLine == op.startLine
                    && lastPending.insertedEndChar == op.startChar) {
                
                String beforeText = lastPending.insertedText;
                lastPending.insertedText = lastPending.insertedText + op.insertedText;
                EditOp.CursorTarget newEnd = computeCursorAfterInsert(
                        lastPending.startLine, lastPending.startChar, lastPending.insertedText);
                lastPending.insertedEndLine = newEnd.line;
                lastPending.insertedEndChar = newEnd.ch;
                lastPending.cursorLineAfter = op.cursorLineAfter;
                lastPending.cursorCharAfter = op.cursorCharAfter;
                lastPending.timestamp = op.timestamp;

                EditOp lastUndo = operators.history.undoStack.peekLast();
                if (lastUndo != null
                        && lastUndo.startLine == lastPending.startLine
                        && lastUndo.startChar == lastPending.startChar
                        && lastUndo.endLine == lastPending.endLine
                        && lastUndo.endChar == lastPending.endChar
                        && lastUndo.insertedText != null
                        && lastUndo.insertedText.equals(beforeText)) {
                    lastUndo.insertedText = lastPending.insertedText;
                    lastUndo.insertedEndLine = lastPending.insertedEndLine;
                    lastUndo.insertedEndChar = lastPending.insertedEndChar;
                    lastUndo.cursorLineAfter = lastPending.cursorLineAfter;
                    lastUndo.cursorCharAfter = lastPending.cursorCharAfter;
                    lastUndo.timestamp = lastPending.timestamp;
                }

                operators.history.redoStack.clear();
                operators.history.pendingRedo.clear();
                operators.lastEditTimestamp = op.timestamp;
                return;
            }
        }

        operators.history.pushUndo(op);
        operators.history.redoStack.clear();
        operators.history.pendingEdits.addLast(op);
        operators.history.pendingRedo.clear();
        operators.lastEditTimestamp = op.timestamp;
    }

    private boolean tryAbsorbDeleteIntoPendingInsert(EditOp op) {
        if (!isDeleteOnly(op)) return false;

        EditOp lastPending = operators.history.pendingEdits.peekLast();
        if (!isSingleLineInsertOnly(lastPending)) return false;
        if (lastPending.startLine != op.startLine
                || lastPending.startLine != op.endLine
                || lastPending.startChar > op.startChar
                || lastPending.insertedEndChar < op.endChar) {
            return false;
        }

        int relativeStart = op.startChar - lastPending.startChar;
        int relativeEnd = op.endChar - lastPending.startChar;
        String inserted = lastPending.insertedText;
        if (relativeStart < 0 || relativeEnd > inserted.length() || relativeStart >= relativeEnd) {
            return false;
        }
        String deletedFromPendingInsert = inserted.substring(relativeStart, relativeEnd);
        if (!deletedFromPendingInsert.equals(op.removedText)) return false;

        String remainingInsert = inserted.substring(0, relativeStart) + inserted.substring(relativeEnd);
        if (remainingInsert.isEmpty()) {
            operators.history.pendingEdits.removeLast();
            removeMatchingUndo(lastPending);
            if (operators.history.pendingEdits.isEmpty() && operators.lineCountDelta == 0) {
                synchronized (editor.windowRender.modifiedLines) {
                    editor.windowRender.removeModifiedLine(op.startLine);
                }
            }
        } else {
            lastPending.insertedText = remainingInsert;
            lastPending.insertedEndChar = lastPending.startChar + remainingInsert.length();
            lastPending.cursorLineAfter = op.cursorLineAfter;
            lastPending.cursorCharAfter = op.cursorCharAfter;
            lastPending.timestamp = op.timestamp;
        }
        return true;
    }

    private boolean isDeleteOnly(EditOp op) {
        return op != null
                && (op.insertedText == null || op.insertedText.isEmpty())
                && op.removedText != null
                && !op.removedText.isEmpty();
    }

    private boolean isSingleLineInsertOnly(EditOp op) {
        return op != null
                && (op.removedText == null || op.removedText.isEmpty())
                && op.insertedText != null
                && !op.insertedText.isEmpty()
                && op.startLine == op.endLine
                && op.startLine == op.insertedEndLine;
    }

    private void removeMatchingUndo(EditOp op) {
        if (operators.history.undoStack.peekLast() == op) {
            operators.history.undoStack.removeLast();
        } else {
            operators.history.undoStack.remove(op);
        }
    }

    public void recordEditNoUndo(EditOp op) {
        if (operators.isApplyingUndoRedo) return;
        if (op == null) return;
        editor.highlite.markTyping();
        operators.history.pendingEdits.addLast(op);
        operators.history.pendingRedo.clear();
        operators.history.redoStack.clear();
        operators.lastEditTimestamp = op.timestamp;
    }

    public EditOp.CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
        if (insertText == null) insertText = "";
        int newLines = 0;
        int lastNewlineIndex = -1;
        for (int i = 0; i < insertText.length(); i++) {
            if (insertText.charAt(i) == '\n') {
                newLines++;
                lastNewlineIndex = i;
            }
        }
        if (newLines == 0) {
            return new EditOp.CursorTarget(baseLine, baseChar + insertText.length());
        } else {
            return new EditOp.CursorTarget(baseLine + newLines, insertText.length() - lastNewlineIndex - 1);
        }
    }

    public int countNewlines(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    public boolean isLargePasteText(String text) {
        if (text == null) return false;
        return text.length() > UNDO_TEXT_LIMIT / 2;
    }
}
