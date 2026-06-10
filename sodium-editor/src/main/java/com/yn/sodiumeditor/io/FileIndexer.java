package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;
import java.util.Arrays;

/** Handles file indexing for fast random access. */
public class FileIndexer {
  private final SodiumEditor editor;
  private final FileIO fileIO;

  public FileIndexer(SodiumEditor editor, FileIO fileIO) {
    this.editor = editor;
    this.fileIO = fileIO;
  }

  public void buildFileIndex() {
    if (fileIO.sourceFile == null || !fileIO.sourceFile.exists()) {
      fileIO.isIndexReady = false;
      fileIO.isIndexBuilding = false;
      return;
    }
    if (fileIO.isIndexDisabled) {
      if (fileIO.sourceFile.getAbsolutePath().equals(fileIO.indexDisabledPath)
          && fileIO.sourceFile.length() == fileIO.indexDisabledFileLength) {
        fileIO.isIndexReady = false;
        fileIO.isIndexBuilding = false;
        return;
      }
      fileIO.isIndexDisabled = false;
      fileIO.indexDisabledPath = null;
      fileIO.indexDisabledFileLength = -1L;
    }
    fileIO.isIndexBuilding = true;
    final int taskVersion = fileIO.ioTaskVersion.get();
    fileIO.ioHandler.post(
        () -> {
          long[] offsets = buildIndexJava(fileIO.sourceFile.getAbsolutePath());
          if (taskVersion != fileIO.ioTaskVersion.get()) {
            fileIO.isIndexBuilding = false;
            return;
          }
          if (offsets != null) {
            synchronized (fileIO.lineOffsetsLock) {
              if (taskVersion == fileIO.ioTaskVersion.get()) {
                fileIO.lineOffsets = offsets;
                fileIO.isIndexReady = true;
                fileIO.checkHeavyFeaturesAfterIndexReady();
                editor.post(
                    () -> {
                      editor.requestLayout();
                      if (editor.wordWrap.isWordWrapEnabled)
                        editor.wordWrap.scheduleWrapMetricsBuild();
                    });
              }
            }
          } else {
            synchronized (fileIO.lineOffsetsLock) {
              fileIO.isIndexReady = false;
            }
          }
          fileIO.isIndexBuilding = false;
        });
  }

  public long[] buildIndexJava(String filepath) {
    long fileLength = 0L;
    try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
      fileLength = raf.length();
      if (fileLength == 0) return new long[0];

      long maxMemory = Runtime.getRuntime().maxMemory();
      long maxIndexBytes = Math.min(64L * 1024 * 1024, Math.max(16L * 1024 * 1024, maxMemory / 6));
      int maxIndexEntries = (int) Math.min(Integer.MAX_VALUE - 1L, maxIndexBytes / 8L);
      LongArrayBuilder offsets = new LongArrayBuilder(Math.min(4096, maxIndexEntries));
      if (!offsets.add(0L, maxIndexEntries)) {
        disableIndex(filepath, fileLength);
        return null;
      }

      byte[] buffer = new byte[64 * 1024];
      long currentReadPos = 0;
      while (currentReadPos < fileLength) {
        int bytesRead = raf.read(buffer);
        if (bytesRead == -1) break;
        for (int i = 0; i < bytesRead; i++) {
          if (buffer[i] == '\n') {
            if (!offsets.add(currentReadPos + i + 1, maxIndexEntries)) {
              disableIndex(filepath, fileLength);
              return null;
            }
          }
        }
        currentReadPos += bytesRead;
      }
      return offsets.toArray();
    } catch (Exception e) {
      return null;
    }
  }

  private void disableIndex(String path, long len) {
    fileIO.isIndexDisabled = true;
    fileIO.indexDisabledPath = path;
    fileIO.indexDisabledFileLength = len;
  }

  public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
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

  private static final class LongArrayBuilder {
    private long[] values;
    private int size;

    LongArrayBuilder(int initialCapacity) {
      values = new long[Math.max(1, initialCapacity)];
    }

    boolean add(long value, int maxSize) {
      if (size >= maxSize) return false;
      if (size == values.length) {
        int nextCapacity = values.length + (values.length >> 1);
        if (nextCapacity <= values.length) nextCapacity = values.length + 1;
        if (nextCapacity > maxSize) nextCapacity = maxSize;
        values = Arrays.copyOf(values, nextCapacity);
      }
      values[size++] = value;
      return true;
    }

    long[] toArray() {
      return Arrays.copyOf(values, size);
    }
  }
}
