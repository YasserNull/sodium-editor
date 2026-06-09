package com.yn.sodiumeditor.core.binary;

import java.io.File;

/** Fixed-row binary document model. */
public final class BinaryDocument {
    public static final int BYTES_PER_ROW = 256;

    private final File file;
    private final long fileLength;

    public BinaryDocument(File file) {
        this.file = file;
        this.fileLength = (file == null || !file.exists()) ? 0L : file.length();
    }

    public File getFile() {
        return file;
    }

    public long getFileLength() {
        return fileLength;
    }

    public int getRowCount() {
        long rows = (fileLength + BYTES_PER_ROW - 1L) / BYTES_PER_ROW;
        if (rows <= 0L) return 1;
        return rows > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rows;
    }

    public long getOffsetForRow(int row) {
        return Math.max(0L, (long) Math.max(0, row) * BYTES_PER_ROW);
    }
}
