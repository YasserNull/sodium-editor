package com.yn.sodiumeditor.io;
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Undo {
    private static final String TAG = "SodiumEditor";
    private final SodiumEditor editor;
    private final EditOperators operators;

    public Undo(SodiumEditor editor, EditOperators operators) {
        this.editor = editor;
        this.operators = operators;
    }

    public void execute() {
        EditOp op = operators.history.popUndo();
        if (op == null) return;
        logUndo("undo.start", describeOp(op));
        operators.history.pushRedo(op);
        boolean undonePendingFileEdit = !operators.history.pendingEdits.isEmpty()
                && operators.history.pendingEdits.peekLast() == op;
        if (undonePendingFileEdit) {
            operators.history.pendingEdits.removeLast();
            operators.history.pendingRedo.addLast(op);
        } else if (op.removedText != null) {
            operators.history.pendingEdits.addLast(createPendingUndoSaveOp(op));
            operators.history.pendingRedo.clear();
        }
        if (op.removedText == null && (op.entireFileDelete || op.insertedText == null || op.insertedText.isEmpty())) {
            if (restoreDeletedSelectionFromBackup(op)) return;
            if (restoreDeletedFileBackedSelection(op)) return;
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

    private EditOp createPendingUndoSaveOp(EditOp op) {
        EditOp inverse = new EditOp();
        inverse.startLine = op.startLine;
        inverse.startChar = op.startChar;
        inverse.endLine = op.insertedEndLine;
        inverse.endChar = op.insertedEndChar;
        inverse.removedText = op.insertedText == null ? "" : op.insertedText;
        inverse.insertedText = op.removedText == null ? "" : op.removedText;
        EditOp.CursorTarget end =
                operators.recorder.computeCursorAfterInsert(
                        inverse.startLine, inverse.startChar, inverse.insertedText);
        inverse.insertedEndLine = end.line;
        inverse.insertedEndChar = end.ch;
        inverse.cursorLineBefore = op.cursorLineAfter;
        inverse.cursorCharBefore = op.cursorCharAfter;
        inverse.cursorLineAfter = op.cursorLineBefore;
        inverse.cursorCharAfter = op.cursorCharBefore;
        inverse.timestamp = System.currentTimeMillis();
        return inverse;
    }

    private boolean restoreDeletedSelectionFromBackup(EditOp op) {
        if (op.removedTextBackupFile == null || !op.removedTextBackupFile.exists()) return false;
        if (editor.fileIO.sourceFile == null) return false;
        if (op.startLine != 0 || op.startChar != 0) return false;
        logUndo("undo.restore.backup.start", describeOp(op));
        editor.fileIO.ioHandler.post(() -> {
            boolean ok = true;
            try (FileInputStream fis = new FileInputStream(op.removedTextBackupFile);
                 FileOutputStream fos = new FileOutputStream(editor.fileIO.sourceFile, false)) {
                byte[] buf = new byte[1024 * 1024];
                int read;
                while ((read = fis.read(buf)) > 0) {
                    fos.write(buf, 0, read);
                }
                fos.flush();
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            editor.post(() -> {
                if (!success) {
                    logUndo("undo.restore.backup.failed", describeOp(op));
                    return;
                }
                operators.markFileStateDirtyAfterUndoRestore();
                operators.lineCountDelta = 0;
                clearRenderAndFileCaches();
                editor.cursor.cursorLine = Math.max(0, op.cursorLineBefore);
                editor.cursor.cursorChar = Math.max(0, op.cursorCharBefore);
                editor.selection.clearSelection();
                editor.fileIO.isFileCleared = false;
                editor.fileIO.isIndexReady = false;
                editor.fileIO.isIndexBuilding = false;
                synchronized (editor.fileIO.lineOffsetsLock) {
                    editor.fileIO.lineOffsets = new long[0];
                }
                int reloadStart = Math.max(0, editor.cursor.cursorLine - editor.windowRender.prefetchLines);
                editor.fileIO.loadWindowAround(reloadStart, () -> {
                    logUndo(
                            "undo.restore.backup.loaded",
                            describeOp(op)
                                    + " fileLength="
                                    + editor.fileIO.sourceFile.length()
                                    + " window="
                                    + editor.windowRender.windowStartLine
                                    + "+"
                                    + editor.windowRender.linesWindow.size());
                    editor.wordWrap.onLineCountChanged();
                    editor.lineNumber.invalidateLineNumberCache();
                    editor.lineNumber.updateGutterWidth();
                    editor.highlite.invalidateHighlightEnsureRange();
                    editor.bracketGuides.invalidateBracketGuideCache(true);
                    editor.requestLayout();
                    editor.invalidate();
                }, false);
            });
        });
        return true;
    }

    private boolean restoreDeletedFileBackedSelection(EditOp op) {
        if (editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) return false;
        logUndo("undo.restore.source.start", describeOp(op));
        if (op.entireFileDelete) {
            operators.lineCountDelta = 0;
        } else {
            operators.lineCountDelta += Math.max(0, op.endLine - op.startLine);
        }
        clearRenderAndFileCaches();
        editor.cursor.cursorLine = Math.max(0, op.cursorLineBefore);
        editor.cursor.cursorChar = Math.max(0, op.cursorCharBefore);
        editor.selection.clearSelection();
        editor.fileIO.isFileCleared = false;
        editor.fileIO.isIndexReady = false;
        editor.fileIO.isIndexBuilding = false;
        synchronized (editor.fileIO.lineOffsetsLock) {
            editor.fileIO.lineOffsets = new long[0];
        }
        int reloadStart = Math.max(0, editor.cursor.cursorLine - editor.windowRender.prefetchLines);
        editor.fileIO.loadWindowAround(reloadStart, () -> {
            logUndo(
                    "undo.restore.source.loaded",
                    describeOp(op)
                            + " fileLength="
                            + editor.fileIO.sourceFile.length()
                            + " window="
                            + editor.windowRender.windowStartLine
                            + "+"
                            + editor.windowRender.linesWindow.size());
            editor.wordWrap.onLineCountChanged();
            editor.lineNumber.invalidateLineNumberCache();
            editor.lineNumber.updateGutterWidth();
            editor.highlite.invalidateHighlightEnsureRange();
            editor.bracketGuides.invalidateBracketGuideCache(true);
            editor.requestLayout();
            editor.invalidate();
        }, false);
        return true;
    }

    private void clearRenderAndFileCaches() {
        synchronized (editor.windowRender.modifiedLines) {
            editor.windowRender.modifiedLines.clear();
        }
        synchronized (editor.fileIO.directLineCache) {
            editor.fileIO.directLineCache.clear();
        }
        synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.clear();
        }
        editor.windowRender.clearStreamedLineCaches();
    }

    private void logUndo(String operation, String details) {
        if (!SodiumEditor.DEBUG_LOGS) return;
        Log.d(
                TAG,
                "[SodiumEditor] operation="
                        + operation
                        + " cursor="
                        + editor.cursor.cursorLine
                        + ":"
                        + editor.cursor.cursorChar
                        + " pendingEdits="
                        + operators.getPendingEditsCount()
                        + " undo="
                        + operators.canUndo()
                        + " redo="
                        + operators.canRedo()
                        + " thread="
                        + Thread.currentThread().getName()
                        + " "
                        + details);
    }

    private String describeOp(EditOp op) {
        if (op == null) return "op=<null>";
        return "range="
                + op.startLine
                + ":"
                + op.startChar
                + ".."
                + op.endLine
                + ":"
                + op.endChar
                + " insertedEnd="
                + op.insertedEndLine
                + ":"
                + op.insertedEndChar
                + " entireFileDelete="
                + op.entireFileDelete
                + " removedText="
                + (op.removedText == null ? "<file-backed>" : "chars=" + op.removedText.length())
                + " insertedChars="
                + (op.insertedText == null ? 0 : op.insertedText.length())
                + " backup="
                + (op.removedTextBackupFile == null ? "<null>" : op.removedTextBackupFile.getAbsolutePath());
    }
}
