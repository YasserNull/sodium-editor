package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;

/**
 * Handles file indexing for fast random access.
 */
public class FileIndexer {
    private final SodiumEditor editor;
    private final FileIO fileIO;

    public FileIndexer(SodiumEditor editor, FileIO fileIO) {
        this.editor = editor;
        this.fileIO = fileIO;
    }

    public void buildFileIndex() {
        if (fileIO.sourceFile == null || !fileIO.sourceFile.exists()) {
            fileIO.isIndexReady = false; fileIO.isIndexBuilding = false; return;
        }
        if (fileIO.isIndexDisabled) {
            if (fileIO.sourceFile.getAbsolutePath().equals(fileIO.indexDisabledPath) && fileIO.sourceFile.length() == fileIO.indexDisabledFileLength) {
                fileIO.isIndexReady = false; fileIO.isIndexBuilding = false; return;
            }
            fileIO.isIndexDisabled = false; fileIO.indexDisabledPath = null; fileIO.indexDisabledFileLength = -1L;
        }
        fileIO.isIndexBuilding = true;
        final int taskVersion = fileIO.ioTaskVersion.get();
        fileIO.ioHandler.post(() -> {
            long[] offsets = buildIndexJava(fileIO.sourceFile.getAbsolutePath());
            if (taskVersion != fileIO.ioTaskVersion.get()) { fileIO.isIndexBuilding = false; return; }
            if (offsets != null) {
                synchronized (fileIO.lineOffsetsLock) {
                    if (taskVersion == fileIO.ioTaskVersion.get()) {
                        fileIO.lineOffsets = offsets;
                        fileIO.isIndexReady = true;
                        editor.post(() -> {
                            editor.requestLayout();
                            if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.scheduleWrapMetricsBuild();
                        });
                    }
                }
            } else { synchronized (fileIO.lineOffsetsLock) { fileIO.isIndexReady = false; } }
            fileIO.isIndexBuilding = false;
        });
    }

    public long[] buildIndexJava(String filepath) {
        long numNewlines = 0;
        long fileLength;
        try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
            fileLength = raf.length();
            if (fileLength == 0) return new long[0];
            byte[] buffer = new byte[8192];
            long currentReadPos = 0;
            while (currentReadPos < fileLength) {
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) break;
                for (int i = 0; i < bytesRead; i++) if (buffer[i] == '\n') numNewlines++;
                currentReadPos += bytesRead;
            }
        } catch (Exception e) { return null; }

        if (numNewlines >= Integer.MAX_VALUE - 1) { disableIndex(filepath, fileLength); return null; }
        long lines = numNewlines + 1;
        long maxMemory = Runtime.getRuntime().maxMemory();
        long maxIndexBytes = Math.min(64L * 1024 * 1024, Math.max(16L * 1024 * 1024, maxMemory / 6));
        if ((lines * 8L) > maxIndexBytes) { disableIndex(filepath, fileLength); return null; }

        long[] offsetsArray = new long[(int) lines];
        int currentOffsetIndex = 0;
        try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
            offsetsArray[currentOffsetIndex++] = 0L;
            long currentPos = 0;
            byte[] buffer = new byte[8192];
            while (currentPos < fileLength) {
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) break;
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        if (currentOffsetIndex < offsetsArray.length) offsetsArray[currentOffsetIndex++] = currentPos + i + 1;
                    }
                }
                currentPos += bytesRead;
            }
        } catch (Exception e) { return null; }
        return offsetsArray;
    }

    private void disableIndex(String path, long len) {
        fileIO.isIndexDisabled = true; fileIO.indexDisabledPath = path; fileIO.indexDisabledFileLength = len;
    }

    public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen) throws Exception {
        long start, end;
        synchronized (fileIO.lineOffsetsLock) {
            if (line < 0 || line >= fileIO.lineOffsets.length) return 0L;
            start = fileIO.lineOffsets[line];
            end = (line + 1 < fileIO.lineOffsets.length) ? fileIO.lineOffsets[line + 1] : fileLen;
        }
        long len = Math.max(0L, end - start);
        if (len > 0L && line + 1 < fileIO.lineOffsets.length) {
            len -= 1L;
            raf.seek(Math.max(start, end - 2));
            if (raf.read() == '\r') len -= 1L;
        }
        return Math.max(0L, len);
    }
}
