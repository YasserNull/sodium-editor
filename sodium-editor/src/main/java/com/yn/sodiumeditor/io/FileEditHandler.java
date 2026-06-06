package com.yn.sodiumeditor.io;
import androidx.annotation.Nullable;
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Handles asynchronous and blocking file rewrite operations.
 */
public class FileEditHandler {
    private static final String TAG = "SodiumEditor";
    private final SodiumEditor editor;
    private final EditOperators operators;

    public FileEditHandler(SodiumEditor editor, EditOperators operators) {
        this.editor = editor;
        this.operators = operators;
    }

    public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
        if (editor.fileIO.sourceFile == null) {
            if (onComplete != null) editor.post(onComplete);
            return;
        }
        if (editor.ime.hasComposing) {
            editor.ime.commitComposing(true);
        }
        final ArrayList<EditOp> ops = new ArrayList<>();
        synchronized (operators.history.pendingEdits) {
            ops.addAll(operators.history.pendingEdits);
            operators.history.pendingEdits.clear();
            operators.history.pendingRedo.clear();
        }
        if (ops.isEmpty()) {
            if (operators.fileStateDirtyAfterUndoRestore) {
                operators.clearFileStateDirtyAfterSave();
                if (onComplete != null) editor.post(onComplete);
                return;
            }
            if (SodiumEditor.DEBUG_LOGS) {
                editor.fileIO.ioHandler.post(() -> {
                    final String savedFileContent = editor.fileIO.readSavedFileContentForLog();
                    editor.post(() -> {
                        editor.fileIO.logSaveContentComparison(savedFileContent);
                        if (onComplete != null) onComplete.run();
                    });
                });
            } else if (onComplete != null) {
                editor.post(onComplete);
            }
            return;
        }
        logSaveUndo("save.pending.start", "ops=" + ops.size() + " file=" + safeFilePath(editor.fileIO.sourceFile));
        editor.fileIO.ioHandler.post(() -> {
            boolean ok = true;
            for (EditOp op : ops) {
                logSaveUndo("save.pending.op", describeOp(op));
                if (!ensureRemovedTextBackupForUndo(op)) {
                    logSaveUndo("save.backup.failed", describeOp(op));
                    ok = false;
                    break;
                }
                boolean rewritten = op.entireFileDelete
                        ? rewriteEntireFileReplaceBlocking(editor.fileIO.sourceFile, op.insertedText)
                        : rewriteReplaceRangeBlocking(
                                editor.fileIO.sourceFile, op.startLine, op.startChar, op.endLine, op.endChar, op.insertedText);
                if (!rewritten) {
                    logSaveUndo("save.rewrite.failed", describeOp(op));
                    ok = false;
                    break;
                }
            }
            final boolean success = ok;
            final String savedFileContent = success ? editor.fileIO.readSavedFileContentForLog() : "";
            editor.post(() -> {
                if (!success) {
                    operators.history.pendingEdits.addAll(ops);
                    logSaveUndo("save.pending.restore", "ops=" + ops.size());
                } else {
                    operators.clearFileStateDirtyAfterSave();
                    logSaveUndo("save.pending.success", "ops=" + ops.size() + " fileLength=" + editor.fileIO.sourceFile.length());
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
                    operators.lineCountDelta = 0;
                    editor.lineNumber.invalidateLineNumberCache();
                    int reloadStart = Math.max(0, editor.cursor.cursorLine - editor.windowRender.prefetchLines);
                    editor.fileIO.loadWindowAround(reloadStart, () -> {
                        editor.fileIO.logSaveContentComparison(savedFileContent);
                        editor.requestLayout();
                        editor.invalidate();
                        if (onComplete != null) onComplete.run();
                    }, false);
                    return;
                }
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private boolean ensureRemovedTextBackupForUndo(EditOp op) {
        if (op == null || op.removedText != null || editor.fileIO.sourceFile == null) return true;
        if (op.removedTextBackupFile != null && op.removedTextBackupFile.exists()) return true;
        if (!op.entireFileDelete
                && (op.endLine < op.startLine || (op.endLine == op.startLine && op.endChar <= op.startChar))) {
            return true;
        }
        try {
            File backup = File.createTempFile("sodium_removed_", ".bak", editor.getContext().getCacheDir());
            try (RandomAccessFile rafIn = new RandomAccessFile(editor.fileIO.sourceFile, "r");
                 FileChannel inCh = rafIn.getChannel();
                 RandomAccessFile rafOut = new RandomAccessFile(backup, "rw");
                 FileChannel outCh = rafOut.getChannel()) {
                long fileLen = editor.fileIO.sourceFile.length();
                long startByte = 0L;
                long endByte = fileLen;
                if (!op.entireFileDelete) {
                    EditOp.RangeBytes range = operators.locator.computeByteRangeFastOrScan(
                            editor.fileIO.sourceFile, op.startLine, op.startChar, op.endLine, op.endChar);
                    if (range == null) return false;
                    startByte = Math.max(0, Math.min(range.startByte, fileLen));
                    endByte = Math.max(0, Math.min(range.endByte, fileLen));
                }
                if (endByte < startByte) {
                    long t = startByte;
                    startByte = endByte;
                    endByte = t;
                }
                transferRange(inCh, outCh, startByte, endByte - startByte);
                outCh.force(true);
            }
            op.removedTextBackupFile = backup;
            logSaveUndo(
                    "save.backup.created",
                    describeOp(op) + " backup=" + safeFilePath(backup) + " bytes=" + backup.length());
            return true;
        } catch (Exception e) {
            logSaveUndo("save.backup.exception", e.getClass().getSimpleName() + " " + describeOp(op));
            return false;
        }
    }

    public boolean rewriteEntireFileReplaceBlocking(File inFile, @Nullable String insertText) {
        if (inFile == null || !inFile.exists()) return false;
        try (RandomAccessFile raf = new RandomAccessFile(inFile, "rw");
             FileChannel ch = raf.getChannel()) {
            byte[] insertBytes = (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);
            raf.setLength(0L);
            if (insertBytes.length > 0) {
                ch.write(ByteBuffer.wrap(insertBytes), 0L);
            }
            ch.force(true);
            editor.fileIO.sourceFile = inFile;
            synchronized (editor.fileIO.lineOffsetsLock) {
                editor.fileIO.lineOffsets = new long[0];
            }
            editor.fileIO.isIndexReady = false;
            editor.fileIO.isIndexBuilding = false;
            editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean rewriteReplaceRangeBlocking(
            File inFile, int sL, int sC, int eL, int eC, @Nullable String insertText) {
        if (inFile == null || !inFile.exists()) return false;
        try {
            EditOp.RangeBytes range = operators.locator.computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
            if (range == null) return false;
            byte[] insertBytes = (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);
            final int BUF_SIZE = 1024 * 1024;

            try (RandomAccessFile raf = new RandomAccessFile(inFile, "rw");
                 FileChannel ch = raf.getChannel()) {

                long fileLen = ch.size();
                long startByte = Math.max(0, Math.min(range.startByte, fileLen));
                long endByte = Math.max(0, Math.min(range.endByte, fileLen));
                if (endByte < startByte) {
                    long t = startByte;
                    startByte = endByte;
                    endByte = t;
                }

                long removeLen = endByte - startByte;
                long diff = (long) insertBytes.length - removeLen;

                if (diff > 0) {
                    raf.setLength(fileLen + diff);
                    ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
                    for (long pos = fileLen; pos > endByte; ) {
                        long readPos = Math.max(endByte, pos - BUF_SIZE);
                        int size = (int) (pos - readPos);
                        buf.clear();
                        buf.limit(size);
                        ch.read(buf, readPos);
                        buf.flip();
                        ch.write(buf, readPos + diff);
                        pos = readPos;
                    }
                } else if (diff < 0) {
                    ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
                    for (long pos = endByte; pos < fileLen; ) {
                        int size = (int) Math.min(BUF_SIZE, fileLen - pos);
                        buf.clear();
                        buf.limit(size);
                        ch.read(buf, pos);
                        buf.flip();
                        ch.write(buf, pos + diff);
                        pos += size;
                    }
                    raf.setLength(fileLen + diff);
                }

                if (insertBytes.length > 0) {
                    ch.write(ByteBuffer.wrap(insertBytes), startByte);
                }
                ch.force(true);
            }

            editor.fileIO.sourceFile = inFile;
            synchronized (editor.fileIO.lineOffsetsLock) {
                editor.fileIO.lineOffsets = new long[0];
            }
            editor.fileIO.isIndexReady = false;
            editor.fileIO.isIndexBuilding = false;
            editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
            // Don't clear modifiedLines here - keep edited content for IME
            // until the window is reloaded from the rewritten file.
            // Clearing them causes the old file content in linesWindow to be
            // returned by getLineTextForRender, making deleted lines reappear.
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void rewriteReplaceRangeAsync(
            int opToken,
            File inFile,
            int sL,
            int sC,
            int eL,
            int eC,
            String insertText,
            EditOp.CursorTarget target,
            boolean finishLargeEditUi) {
        editor.fileIO.ioHandler.post(() -> {
            try {
                if (inFile == null || !inFile.exists()) {
                    editor.post(() -> {
                        if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                    });
                    return;
                }

                EditOp.RangeBytes range = operators.locator.computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
                if (range == null) {
                    editor.post(() -> {
                        if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                    });
                    return;
                }

                File outFile = File.createTempFile("popedit_", ".tmp", editor.getContext().getCacheDir());
                byte[] insertBytes = (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);

                try (RandomAccessFile rafIn = new RandomAccessFile(inFile, "r");
                     FileChannel inCh = rafIn.getChannel();
                     RandomAccessFile rafOut = new RandomAccessFile(outFile, "rw");
                     FileChannel outCh = rafOut.getChannel()) {

                    long fileLen = rafIn.length();
                    long startByte = Math.max(0, Math.min(range.startByte, fileLen));
                    long endByte = Math.max(0, Math.min(range.endByte, fileLen));
                    if (endByte < startByte) {
                        long t = startByte;
                        startByte = endByte;
                        endByte = t;
                    }

                    transferRange(inCh, outCh, 0, startByte);
                    if (insertBytes.length > 0) {
                        outCh.write(ByteBuffer.wrap(insertBytes));
                    }
                    transferRange(inCh, outCh, endByte, fileLen - endByte);
                    outCh.force(true);
                }

                editor.post(() -> {
                    if (opToken != operators.editVersion.get()) return;
                    editor.fileIO.invalidatePendingIO();

                    if (inFile != null) {
                        try (FileInputStream fis = new FileInputStream(outFile);
                             FileOutputStream fos = new FileOutputStream(inFile)) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = fis.read(buf)) > 0) {
                                fos.write(buf, 0, r);
                            }
                            fos.flush();
                        } catch (Exception ignore) {
                        }
                        outFile.delete();
                        editor.fileIO.sourceFile = inFile;
                    } else {
                        editor.fileIO.sourceFile = outFile;
                    }
                    
                    synchronized (editor.windowRender.modifiedLines) {
                        editor.windowRender.modifiedLines.clear();
                    }
                    synchronized (editor.windowRender.lineWidthCache) {
                        editor.windowRender.lineWidthCache.clear();
                    }
                    operators.lineCountDelta = 0;

                    synchronized (editor.fileIO.lineOffsetsLock) {
                        editor.fileIO.lineOffsets = new long[0];
                    }
                    editor.fileIO.isIndexReady = false;
                    editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
                    
                    editor.cursor.cursorLine = Math.max(0, target.line);
                    editor.cursor.cursorChar = Math.max(0, target.ch);

                    boolean cursorInsideWindow = (editor.cursor.cursorLine >= editor.windowRender.windowStartLine
                            && editor.cursor.cursorLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());

                    if (cursorInsideWindow) {
                        editor.windowRender.recalculateMaxLineWidth();
                        editor.requestFocus();
                        if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(false);
                        editor.invalidate();
                    } else {
                        int targetStart = Math.max(0, editor.cursor.cursorLine - editor.windowRender.prefetchLines);
                        editor.fileIO.loadWindowAround(targetStart, () -> {
                            if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(false);
                            editor.invalidate();
                        }, false);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                editor.post(() -> {
                    if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                });
            }
        });
    }

    private void transferRange(FileChannel inCh, FileChannel outCh, long position, long count) throws Exception {
        long remaining = count;
        long pos = position;
        while (remaining > 0) {
            long sent = inCh.transferTo(pos, remaining, outCh);
            if (sent <= 0) break;
            pos += sent;
            remaining -= sent;
        }
    }

    private void logSaveUndo(String operation, String details) {
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
                + safeFilePath(op.removedTextBackupFile);
    }

    private String safeFilePath(@Nullable File file) {
        return file == null ? "<null>" : file.getAbsolutePath();
    }
}
