package com.yn.sodiumeditor.io;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * فهرسة الأسطر والـ byte offsets
 */
public class LineIndex {
    private final SodiumEditor view;

    public LineIndex(SodiumEditor view) {
        this.view = view;
    }

    // =========================================================================
    // بناء الفهرس (Index Building)
    // =========================================================================

    public void buildFileIndex() {
        if (view.sourceFile == null || !view.sourceFile.exists()) {
            view.isIndexReady = false;
            view.isIndexBuilding = false;
            return;
        }
        if (view.isIndexDisabled) {
            String path = view.sourceFile.getAbsolutePath();
            long len = view.sourceFile.length();
            if (path.equals(view.indexDisabledPath) && len == view.indexDisabledFileLength) {
                view.isIndexReady = false;
                view.isIndexBuilding = false;
                return;
            }
            view.isIndexDisabled = false;
            view.indexDisabledPath = null;
            view.indexDisabledFileLength = -1L;
        }
        view.isIndexBuilding = true;
        final int taskVersion = view.ioTaskVersion.get();
        view.ioHandler.post(
                () -> {
                    long[] offsets = buildIndexJava(view.sourceFile.getAbsolutePath());
                    if (taskVersion != view.ioTaskVersion.get()) {
                        view.isIndexBuilding = false;
                        return;
                    }
                    if (offsets != null) {
                        synchronized (view.lineOffsetsLock) {
                            if (taskVersion == view.ioTaskVersion.get()) {
                                view.lineOffsets = offsets;
                                view.isIndexReady = true;
                                view.post(view::requestLayout);
                                if (view.wrapWordState.isWordWrapEnabled) view.post(() -> view.wrapWordBuilder.scheduleBuild(view));
                            }
                        }
                    } else {
                        synchronized (view.lineOffsetsLock) {
                            view.isIndexReady = false;
                        }
                    }
                    view.isIndexBuilding = false;
                });
    }

    private long[] buildIndexJava(String path) {
        if (path == null) return null;
        java.io.File file = new java.io.File(path);
        if (!file.exists()) return null;
        ArrayList<Long> offsets = new ArrayList<>();
        offsets.add(0L);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long pos = 0L;
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = raf.read(buf)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buf[i] == '\n') {
                        offsets.add(pos + i + 1);
                    }
                }
                pos += read;
            }
        } catch (Exception ignored) {
            return null;
        }
        long[] out = new long[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) out[i] = offsets.get(i);
        return out;
    }

    // =========================================================================
    // Line Offsets Access
    // =========================================================================

    public long[] getLineOffsets() {
        synchronized (view.lineOffsetsLock) {
            return view.lineOffsets;
        }
    }

    public void setLineOffsets(long[] offsets) {
        synchronized (view.lineOffsetsLock) {
            view.lineOffsets = offsets;
        }
    }

    // =========================================================================
    // Index State
    // =========================================================================

    public boolean isIndexReady() {
        return view.isIndexReady;
    }

    public boolean isIndexBuilding() {
        return view.isIndexBuilding;
    }

    public boolean isIndexDisabled() {
        return view.isIndexDisabled;
    }

    public void setIndexDisabled(boolean disabled) {
        view.isIndexDisabled = disabled;
    }

    // =========================================================================
    // Byte Range Computation
    // =========================================================================

    public RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
        if (view.comparePos(sL, sC, eL, eC) > 0) {
            int tl = sL, tc = sC;
            sL = eL;
            sC = eC;
            eL = tl;
            eC = tc;
        }

        if (view.isIndexReady && file != null) {
            RangeBytes fast = computeByteRangeUsingIndex(file, sL, sC, eL, eC);
            if (fast != null) return fast;
        }

        return computeByteRangeByScanning(file, sL, sC, eL, eC);
    }

    public RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
        return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
    }

    private RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long startLineByte, endLineByte;
            synchronized (view.lineOffsetsLock) {
                if (!isIndexReady()) return null;
                if (sL < 0 || eL < 0) return null;
                if (sL >= getLineOffsets().length || eL >= getLineOffsets().length) return null;
                startLineByte = getLineOffsets()[sL];
                endLineByte = getLineOffsets()[eL];
            }

            String startLineText = view.textIO.readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : view.textIO.readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + view.textIO.computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + view.textIO.computeByteOffsetInLineUtf8(endLineText, eC);

            return new RangeBytes(startByte, endByte);
        } catch (Exception ignore) {
            return null;
        }
    }

    private RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
        if (view.comparePos(sL, sC, eL, eC) > 0) {
            int tl = sL, tc = sC;
            sL = eL;
            sC = eC;
            eL = tl;
            eC = tc;
        }

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long[] starts = findTwoLineStartBytesByScanning(raf, sL, eL);
            long startLineByte = starts[0];
            long endLineByte = starts[1];

            String startLineText = view.textIO.readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : view.textIO.readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + view.textIO.computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + view.textIO.computeByteOffsetInLineUtf8(endLineText, eC);

            return new RangeBytes(startByte, endByte);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Line Scanning
    // =========================================================================

    public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
        if (targetLine <= 0) return 0L;
        long[] starts = findTwoLineStartBytesByScanning(raf, targetLine, targetLine);
        return (starts != null && starts.length > 0) ? starts[0] : 0L;
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

    // =========================================================================
    // Line Byte Length
    // =========================================================================

    public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
            throws Exception {
        long start;
        long end;
        synchronized (view.lineOffsetsLock) {
            if (line < 0 || line >= view.lineOffsets.length) return 0L;
            start = view.lineOffsets[line];
            end = (line + 1 < view.lineOffsets.length) ? view.lineOffsets[line + 1] : fileLen;
        }
        long len = Math.max(0L, end - start);
        if (len <= 0L) return 0L;
        if (line + 1 < view.lineOffsets.length) {
            len -= 1L; // drop '\n'
            if (len > 0L) {
                raf.seek(Math.max(start, end - 2));
                int last = raf.read();
                if (last == '\r') {
                    len -= 1L; // drop '\r' in CRLF
                }
            }
        }
        return Math.max(0L, len);
    }

    // =========================================================================
    // Line Count
    // =========================================================================

    public interface LineCountCallback {
        void onResult(int count);
    }

    public void countTotalLines(LineCountCallback callback) {
        final int taskVersion = view.ioTaskVersion.get();
        view.ioHandler.post(
            () -> {
                if (taskVersion != view.ioTaskVersion.get()) {
                    view.post(() -> callback.onResult(-1));
                    return;
                }
                if (isIndexReady() && view.sourceFile != null) {
                    synchronized (view.lineOffsetsLock) {
                        view.post(() -> callback.onResult(view.lineOffsets.length));
                    }
                    return;
                }
                int count = 0;
                if (view.sourceFile != null && view.sourceFile.exists()) {
                    try (java.io.FileInputStream is = new java.io.FileInputStream(view.sourceFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        boolean empty = true;
                        while ((len = is.read(buffer)) != -1) {
                            empty = false;
                            for (int i = 0; i < len; i++) if (buffer[i] == '\n') count++;
                        }
                        if (!empty) count++;
                    } catch (Exception e) {
                        count = -1;
                    }
                }
                final int finalCount = count;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(finalCount));
            });
    }

    // =========================================================================
    // RangeBytes Class
    // =========================================================================

    public static final class RangeBytes {
        public final long startByte, endByte;

        public RangeBytes(long s, long e) {
            startByte = s;
            endByte = e;
        }
    }
}
