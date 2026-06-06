package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.RandomAccessFile;

/**
 * Handles byte range computation and line scanning in files.
 */
public class ByteRangeLocator {
    private final SodiumEditor editor;

    public ByteRangeLocator(SodiumEditor editor) {
        this.editor = editor;
    }

    public EditOp.RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
        if (comparePos(sL, sC, eL, eC) > 0) {
            int tmpL = sL; sL = eL; eL = tmpL;
            int tmpC = sC; sC = eC; eC = tmpC;
        }
        return computeByteRangeByScanning(file, sL, sC, eL, eC);
    }

    public EditOp.RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = findLineStartByteByScanning(raf, sL) + sC;
            long end = findLineStartByteByScanning(raf, eL) + eC;
            return new EditOp.RangeBytes(start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return new EditOp.RangeBytes(0, 0);
        }
    }

    public EditOp.RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
        if (comparePos(sL, sC, eL, eC) > 0) {
            int tmpL = sL; sL = eL; eL = tmpL;
            int tmpC = sC; sC = eC; eC = tmpC;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long[] offsets = findTwoLineStartBytesByScanning(raf, sL, eL);
            return new EditOp.RangeBytes(offsets[0] + sC, offsets[1] + eC);
        } catch (Exception e) {
            e.printStackTrace();
            return new EditOp.RangeBytes(0, 0);
        }
    }

    public int comparePos(int l1, int c1, int l2, int c2) {
        if (l1 < l2) return -1;
        if (l1 > l2) return 1;
        return Integer.compare(c1, c2);
    }

    public long[] findTwoLineStartBytesByScanning(RandomAccessFile raf, int lineA, int lineB)
            throws Exception {
        if (lineA < 0) lineA = 0;
        if (lineB < 0) lineB = 0;
        long[] result = new long[2];
        int currentLine = 0;
        raf.seek(0);

        long lineStart = 0;
        int b;
        while ((b = raf.read()) != -1) {
            if (currentLine == lineB) {
                if (currentLine == lineA) result[0] = lineStart;
                result[1] = lineStart;
                return result;
            }
            if (currentLine == lineA) result[0] = lineStart;
            if (b == '\n') {
                currentLine++;
                lineStart = raf.getFilePointer();
            }
        }
        long eof = raf.getFilePointer();
        if (currentLine == lineA) result[0] = lineStart;
        else if (currentLine < lineA) result[0] = eof;
        if (currentLine == lineB) result[1] = lineStart;
        else if (currentLine < lineB) result[1] = eof;
        return result;
    }

    public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
        if (targetLine <= 0) return 0L;
        int currentLine = 0;
        raf.seek(0);
        long offset = 0;
        int b;
        while ((b = raf.read()) != -1) {
            if (b == '\n') {
                currentLine++;
                if (currentLine == targetLine) return offset + 1;
            }
            offset++;
        }
        return offset;
    }
}
