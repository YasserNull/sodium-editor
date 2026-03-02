package com.yn.sodiumeditor.io;

import android.util.Log;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Performs physical byte-level modifications on file content.
 * Used for persisting edit operations to disk.
 */
public final class FileBufferModifier {

    private static final int BUF_SIZE = 1024 * 1024;

    private final SodiumEditor view;

    public FileBufferModifier(SodiumEditor view) {
        this.view = view;
    }

    /**
     * Rewrites a range of bytes in the file with new content.
     * Blocks until the operation completes.
     *
     * @param inFile The file to modify
     * @param sL Start line
     * @param sC Start character
     * @param eL End line
     * @param eC End character
     * @param insertText Text to insert (can be null)
     * @return true if successful, false otherwise
     */
    public boolean rewriteReplaceRangeBlocking(
            File inFile, int sL, int sC, int eL, int eC, @Nullable String insertText) {
        if (inFile == null || !inFile.exists()) return false;
        try {
            LineIndex.RangeBytes range =
                    view.lineIndex.computeByteRangeFastOrScanForUndo(inFile, sL, sC, eL, eC);
            if (range == null) return false;
            byte[] insertBytes =
                    (insertText == null)
                            ? new byte[0]
                            : insertText.getBytes(StandardCharsets.UTF_8);

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

            view.onUndoRedoRewriteSuccess(inFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
