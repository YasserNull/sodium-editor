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
            int tl = sL, tc = sC;
            sL = eL;
            sC = eC;
            eL = tl;
            eC = tc;
        }

        if (editor.fileIO.isIndexReady && file != null) {
            EditOp.RangeBytes fast = computeByteRangeUsingIndex(file, sL, sC, eL, eC);
            if (fast != null) return fast;
        }

        return computeByteRangeByScanning(file, sL, sC, eL, eC);
    }

    public EditOp.RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long startLineByte, endLineByte;
            synchronized (editor.fileIO.lineOffsetsLock) {
                if (!editor.fileIO.isIndexReady) return null;
                if (sL < 0 || eL < 0) return null;
                if (sL >= editor.fileIO.lineOffsets.length || eL >= editor.fileIO.lineOffsets.length) return null;
                startLineByte = editor.fileIO.lineOffsets[sL];
                endLineByte = editor.fileIO.lineOffsets[eL];
            }

            String startLineText = editor.fileIO.readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : editor.fileIO.readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + editor.computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + editor.computeByteOffsetInLineUtf8(endLineText, eC);

            return new EditOp.RangeBytes(startByte, endByte);
        } catch (Exception ignore) {
            return null;
        }
    }

    public EditOp.RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
        if (comparePos(sL, sC, eL, eC) > 0) {
            int tl = sL, tc = sC;
            sL = eL;
            sC = eC;
            eL = tl;
            eC = tc;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long[] starts = findTwoLineStartBytesByScanning(raf, sL, eL);
            long startLineByte = starts[0];
            long endLineByte = starts[1];

            String startLineText = editor.fileIO.readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : editor.fileIO.readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + editor.computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + editor.computeByteOffsetInLineUtf8(endLineText, eC);

            return new EditOp.RangeBytes(startByte, endByte);
        } catch (Exception e) {
            return null;
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

        int a = Math.min(lineA, lineB);
        int b = Math.max(lineA, lineB);

        long offA = (a == 0) ? 0L : -1L;
        long offB = (b == 0) ? 0L : -1L;

        raf.seek(0);
        byte[] buf = new byte[8192];
        long pos = 0;
        int line = 0;

        while (true) {
            int n = raf.read(buf);
            if (n <= 0) break;

            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    line++;
                    long nextLineStart = pos + i + 1;

                    if (line == a && offA < 0) offA = nextLineStart;
                    if (line == b && offB < 0) offB = nextLineStart;

                    if (offA >= 0 && offB >= 0) {
                        if (lineA <= lineB) return new long[] {offA, offB};
                        return new long[] {offB, offA};
                    }
                }
            }
            pos += n;
        }

        long len = raf.length();
        if (offA < 0) offA = len;
        if (offB < 0) offB = len;

        if (lineA <= lineB) return new long[] {offA, offB};
        return new long[] {offB, offA};
    }

    public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
        if (targetLine <= 0) return 0L;
        long[] starts = findTwoLineStartBytesByScanning(raf, targetLine, targetLine);
        return (starts != null && starts.length > 0) ? starts[0] : 0L;
    }
}
