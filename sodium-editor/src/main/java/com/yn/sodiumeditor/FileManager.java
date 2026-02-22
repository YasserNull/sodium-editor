package com.yn.sodiumeditor;

import android.util.Log;
import android.util.SparseIntArray;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Map;

public class FileManager {
    private final SodiumEditorView view;
    
    // File-related fields
    public Charset fileCharset = StandardCharsets.UTF_8;
    private boolean showLoadingOnFileOpen = true;
    private boolean isInitialFileOpenLoading = false;
    private int initialFileOpenToken = 0;
    private Runnable initialFileOpenShowSpinner = null;
    public final Object lineOffsetsLock = new Object();
    private final Object streamedLinesLock = new Object();
    private final SparseIntArray streamedLineLengths = new SparseIntArray();
    private final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
    public boolean streamedSliceUpdatePending = false;
    private int streamedSliceUpdateToken = 0;
    private final int[] streamedSliceTmp = new int[2];
    public volatile boolean isIndexReady = false;
    public volatile boolean isIndexBuilding = false;
    public volatile boolean isIndexDisabled = false;
    public volatile String indexDisabledPath = null;
    public volatile long indexDisabledFileLength = -1L;
    private long[] lineOffsets = new long[0];
    public volatile boolean isEof = false;
    private boolean isFileOpened = false;

    public FileManager(SodiumEditorView view) {
        this.view = view;
    }

    public void setFileCharset(Charset charset) {
        Charset safe = (charset == null) ? StandardCharsets.UTF_8 : charset;
        if (safe.equals(fileCharset)) return;
        fileCharset = safe;
        if (view.readerForFile != null) {
            try {
                view.readerForFile.close();
            } catch (Exception ignored) {
            }
            view.readerForFile = null;
        }
        synchronized (view.lineWidthCache) {
            view.lineWidthCache.clear();
        }
        view.currentMaxWindowLineWidth = 0f;
        view.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;
        view.invalidateHighlightEnsureRange();
        view.bracketGuideManager.invalidateCache();
        if (view.wordWrapManager.isWordWrapEnabled) view.wordWrapManager.invalidateWrapMetrics(view, true);
        view.wordWrapManager.requestWrapPrefixRebuild(view);
        view.reloadWindowAroundVisible(false);
        view.invalidate();
    }

    public void setFileEncoding(String charsetName) {
        Charset cs = StandardCharsets.UTF_8;
        if (charsetName != null) {
            try {
                cs = Charset.forName(charsetName.trim());
            } catch (Exception ignored) {
            }
        }
        setFileCharset(cs);
    }

    public void clearContent() {
        view.invalidatePendingIOForEdit();
        view.sourceFile = null;
        view.isFileCleared = true;
        view.selectionManager.setSelectAllState(false, false);
        isIndexReady = false;
        isIndexDisabled = false;
        indexDisabledPath = null;
        indexDisabledFileLength = -1L;

        // Force clear wrap metrics as content is being cleared
        view.wordWrapManager.wrapMetricsReady = false;
        view.wordWrapManager.wrapLineCounts = null;
        view.wordWrapManager.wrapLinePrefix = null;
        view.wordWrapManager.totalWrapVisualLines = 0;
        view.wordWrapManager.wrapPrefixValidUpToLine = -1;

        synchronized (view.linesWindow) {
            view.linesWindow.clear();
            view.linesWindow.add("");
        }
        synchronized (view.modifiedLines) {
            view.modifiedLines.clear();
        }
        synchronized (view.lineWidthCache) {
            view.lineWidthCache.clear();
        }
        clearStreamedLineCaches();
        view.highlightManager.clearHighlightCaches();
        view.currentMaxWindowLineWidth = 0f;
        view.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;

        view.cursorManager.setLineAndChar(0, 0);
        isEof = true;
        view.scrollManager.scrollY = 0;
        view.scrollManager.scrollX = 0;

        view.recalculateMaxLineWidth();
        view.requestLayout();
        view.invalidate();
    }

    public void loadFromFile(final File file) {
        view.invalidatePendingIOForEdit();
        view.isFileCleared = false;
        view.selectionManager.setSelectAllState(false, false);
        view.lineNumberManager.invalidateCache();

        // Force clear wrap metrics for new file
        view.wordWrapManager.wrapMetricsReady = false;
        view.wordWrapManager.wrapLineCounts = null;
        view.wordWrapManager.wrapLinePrefix = null;
        view.wordWrapManager.totalWrapVisualLines = 0;
        view.wordWrapManager.wrapPrefixValidUpToLine = -1;

        final int token = ++initialFileOpenToken;
        isInitialFileOpenLoading = true;
        if (showLoadingOnFileOpen) {
            if (initialFileOpenShowSpinner != null) {
                view.mainHandler.removeCallbacks(initialFileOpenShowSpinner);
            }
            initialFileOpenShowSpinner =
                    () -> {
                        if (!showLoadingOnFileOpen) return;
                        if (!isInitialFileOpenLoading) return;
                        if (token != initialFileOpenToken) return;
                        view.setDisable(true);
                        view.loadingCircleManager.show(true);
                    };
            view.mainHandler.postDelayed(initialFileOpenShowSpinner, 80);
        }

        view.sourceFile = file;
        view.windowStartLine = 0;
        synchronized (view.linesWindow) {
            view.linesWindow.clear();
        }
        synchronized (view.modifiedLines) {
            view.modifiedLines.clear();
        }
        synchronized (view.lineWidthCache) {
            view.lineWidthCache.clear();
        }
        clearStreamedLineCaches();
        view.highlightManager.clearHighlightCaches();
        view.currentMaxWindowLineWidth = 0f;
        view.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;
        synchronized (lineOffsetsLock) {
            lineOffsets = new long[0];
        }
        isIndexReady = false;
        isIndexDisabled = false;
        indexDisabledPath = null;
        indexDisabledFileLength = -1L;

        view.cursorManager.setLineAndChar(0, 0);
        isEof = false;
        view.scrollManager.scrollY = 0;
        view.scrollManager.scrollX = 0;
        view.undoRedo.resetLineCountDelta();

        view.loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
        view.ioHandler.post(this::buildFileIndex);
        view.requestLayout();
        view.invalidate();
    }

    public void updateSourceFile(File file) {
        view.sourceFile = file;
    }

    private void finishInitialFileOpenWarmup(final int token) {
        if (!isInitialFileOpenLoading) return;
        if (token != initialFileOpenToken) return;
        if (view.getHeight() <= 0 || view.lineHeight <= 0f) {
            view.postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
            return;
        }

        int firstVisibleLine = Math.max(0, view.getGlobalLineForY(view.scrollManager.scrollY));
        int viewHeight = view.getHeight() - view.keyboardHeight;
        if (viewHeight <= 0) viewHeight = view.getHeight();
        int visibleLines = Math.max(1, (int) Math.ceil(viewHeight / view.lineHeight) + 2);
        int lastVisibleLine = firstVisibleLine + visibleLines;

        view.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, null);
        isInitialFileOpenLoading = false;
        if (initialFileOpenShowSpinner != null) {
            view.mainHandler.removeCallbacks(initialFileOpenShowSpinner);
            initialFileOpenShowSpinner = null;
        }
        view.setDisable(false);
        view.loadingCircleManager.show(false);
        view.invalidate();

        ArrayList<Runnable> callbacks;
        synchronized (view.initialLoadCallbacks) {
            if (view.initialLoadCallbacks.isEmpty()) return;
            callbacks = new ArrayList<>(view.initialLoadCallbacks);
            view.initialLoadCallbacks.clear();
        }
        for (Runnable cb : callbacks) {
            view.post(cb);
        }
    }

    public void buildFileIndex() {
        if (view.sourceFile == null || !view.sourceFile.exists()) {
            isIndexReady = false;
            isIndexBuilding = false;
            return;
        }
        if (isIndexDisabled) {
            String path = view.sourceFile.getAbsolutePath();
            long len = view.sourceFile.length();
            if (path.equals(indexDisabledPath) && len == indexDisabledFileLength) {
                isIndexReady = false;
                isIndexBuilding = false;
                return;
            }
            isIndexDisabled = false;
            indexDisabledPath = null;
            indexDisabledFileLength = -1L;
        }
        isIndexBuilding = true;
        final int taskVersion = view.ioTaskVersion.get();
        view.ioHandler.post(
                () -> {
                    long[] offsets = buildIndexJava(view.sourceFile.getAbsolutePath());
                    if (taskVersion != view.ioTaskVersion.get()) {
                        isIndexBuilding = false;
                        return;
                    }
                    if (offsets != null) {
                        synchronized (lineOffsetsLock) {
                            if (taskVersion == view.ioTaskVersion.get()) {
                                lineOffsets = offsets;
                                isIndexReady = true;
                                // When index is ready, we know the true line count.
                                // We must re-measure to calculate the correct gutter width.
                                view.post(view::requestLayout);
                                if (view.wordWrapManager.isWordWrapEnabled) view.post(() -> view.wordWrapManager.scheduleWrapMetricsBuild(view));
                            }
                        }
                    } else {
                        synchronized (lineOffsetsLock) {
                            isIndexReady = false;
                        }
                    }
                    isIndexBuilding = false;
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

    public BufferedReader reopenReaderAtStart() {
        try {
            if (view.readerForFile != null) {
                try {
                    view.readerForFile.close();
                } catch (Exception ignored) {
                }
                view.readerForFile = null;
            }
            if (view.sourceFile != null) {
                view.readerForFile =
                        new BufferedReader(new InputStreamReader(new FileInputStream(view.sourceFile), fileCharset));
                return view.readerForFile;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setShowLoadingOnFileOpen(boolean enabled) {
        showLoadingOnFileOpen = enabled;
    }

    public boolean isFileCleared() {
        return view.isFileCleared;
    }

    public void setFileCleared(boolean cleared) {
        view.isFileCleared = cleared;
    }

    public File getSourceFile() {
        return view.sourceFile;
    }

    public boolean isIndexReady() {
        return isIndexReady;
    }

    public boolean isIndexBuilding() {
        return isIndexBuilding;
    }

    public boolean isIndexDisabled() {
        return isIndexDisabled;
    }

    public void setIndexDisabled(boolean disabled) {
        isIndexDisabled = disabled;
    }

    public long[] getLineOffsets() {
        synchronized (lineOffsetsLock) {
            return lineOffsets;
        }
    }

    public void setLineOffsets(long[] offsets) {
        synchronized (lineOffsetsLock) {
            lineOffsets = offsets;
        }
    }

    public boolean isEof() {
        return isEof;
    }

    public void setEof(boolean eof) {
        isEof = eof;
    }

    public void clearStreamedLineCaches() {
        synchronized (streamedLinesLock) {
            streamedLineLengths.clear();
            streamedLineSliceStarts.clear();
        }
        streamedSliceUpdatePending = false;
        streamedSliceUpdateToken++;
    }

    public int getStreamedLineLength(int globalLine) {
        synchronized (streamedLinesLock) {
            return streamedLineLengths.get(globalLine, -1);
        }
    }

    public int getStreamedLineSliceStart(int globalLine) {
        synchronized (streamedLinesLock) {
            return streamedLineSliceStarts.get(globalLine, 0);
        }
    }

    public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
        synchronized (streamedLinesLock) {
            streamedLineLengths.put(globalLine, length);
            streamedLineSliceStarts.put(globalLine, sliceStart);
        }
    }

    public void clearStreamedLineInfo(int globalLine) {
        synchronized (streamedLinesLock) {
            streamedLineLengths.delete(globalLine);
            streamedLineSliceStarts.delete(globalLine);
        }
    }

    public boolean shouldStreamLineLength(int length) {
        if (view.wordWrapManager.isWordWrapEnabled) return false;
        return length > getStreamLineThreshold();
    }

    private int getStreamLineThreshold() {
        return Math.max(4096, view.highlightManager.maxSyntaxLineLength);
    }

    public int getLogicalLineLength(int globalLine, String line) {
        String mod = view.modifiedLines.get(globalLine);
        if (mod != null) return mod.length();
        int len = (line == null) ? 0 : line.length();
        int longLen = getStreamedLineLength(globalLine);
        return (longLen > len) ? longLen : len;
    }

    public String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
        raf.seek(byteOffset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        byte[] buf = new byte[1024];
        boolean seenAny = false;

        while (true) {
            int n = raf.read(buf);
            if (n <= 0) break;

            int stop = -1;
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    stop = i;
                    break;
                }
            }

            if (stop >= 0) {
                seenAny = true;
                if (stop > 0 && buf[stop - 1] == '\r') {
                    baos.write(buf, 0, stop - 1);
                } else {
                    baos.write(buf, 0, stop);
                }
                break;
            } else {
                seenAny = true;
                baos.write(buf, 0, n);
            }

            if (baos.size() > 2_000_000) break;
        }

        if (!seenAny) return "";
        if (view.binarySafeRenderingEnabled) {
            byte[] data = baos.toByteArray();
            return bytesToControlVisible(data, data.length);
        }
        return baos.toString(fileCharset.name());
    }

    public String bytesToControlVisible(byte[] buf, int len) {
        if (len <= 0) return "";
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            int b = buf[i] & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                sb.append((char) b);
            } else if (b <= 0x1F) {
                sb.append(CONTROL_TOKENS[b]);
            } else if (b == 0x7F) {
                sb.append("<DEL>");
            } else {
                sb.append("<0x");
                String hx = Integer.toHexString(b).toUpperCase();
                if (hx.length() < 2) sb.append('0');
                sb.append(hx).append('>');
            }
        }
        return sb.toString();
    }

    private static final String[] CONTROL_TOKENS =
            new String[] {
                    "<NUL>", "<SOH>", "<STX>", "<ETX>", "<EOT>", "<ENQ>", "<ACK>", "<BEL>",
                    "<BS>", "<TAB>", "<LF>", "<VT>", "<FF>", "<CR>", "<SO>", "<SI>",
                    "<DLE>", "<DC1>", "<DC2>", "<DC3>", "<DC4>", "<NAK>", "<SYN>", "<ETB>",
                    "<CAN>", "<EM>", "<SUB>", "<ESC>", "<FS>", "<GS>", "<RS>", "<US>"
            };

    public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
            throws Exception {
        long start;
        long end;
        synchronized (lineOffsetsLock) {
            if (line < 0 || line >= lineOffsets.length) return 0L;
            start = lineOffsets[line];
            end = (line + 1 < lineOffsets.length) ? lineOffsets[line + 1] : fileLen;
        }
        long len = Math.max(0L, end - start);
        if (len <= 0L) return 0L;
        if (line + 1 < lineOffsets.length) {
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

    public String readLineSliceAtByte(
            RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
            throws Exception {
        int safeStart = Math.max(0, Math.min(startChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
        int safeEnd = Math.max(safeStart, Math.min(endChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
        int len = safeEnd - safeStart;
        if (len <= 0) return "";
        long startByte = lineStart + safeStart;
        raf.seek(startByte);
        byte[] buf = new byte[len];
        raf.readFully(buf);
        if (view.binarySafeRenderingEnabled) {
            return bytesToControlVisible(buf, buf.length);
        }
        return new String(buf, fileCharset);
    }

    public SodiumEditorView.StreamedCharSlice readLineSliceByChars(
            RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
            throws Exception {
        int safeStart = Math.max(0, startChar);
        int safeEnd = Math.max(safeStart, endChar);
        CharsetDecoder decoder = fileCharset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        StringBuilder sb = new StringBuilder(Math.max(0, safeEnd - safeStart));
        byte[] buf = new byte[8192];
        CharBuffer charBuf = CharBuffer.allocate(4096);
        int charIndex = 0;
        boolean done = false;
        raf.seek(lineStart);

        while (!done) {
            int n = raf.read(buf);
            if (n <= 0) break;

            int limit = n;
            boolean hitNewline = false;
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    limit = i;
                    if (limit > 0 && buf[limit - 1] == '\r') limit -= 1;
                    hitNewline = true;
                    break;
                }
            }

            ByteBuffer byteBuf = ByteBuffer.wrap(buf, 0, limit);
            while (true) {
                CoderResult cr = decoder.decode(byteBuf, charBuf, hitNewline);
                charBuf.flip();
                int remaining = charBuf.remaining();
                for (int i = 0; i < remaining; i++) {
                    char c = charBuf.get();
                    if (charIndex >= safeStart && charIndex < safeEnd) {
                        sb.append(c);
                    }
                    charIndex++;
                }
                charBuf.clear();
                if (!cr.isOverflow()) break;
            }

            if (hitNewline) {
                done = true;
            } else if (!needTotalLength && charIndex >= safeEnd) {
                return new SodiumEditorView.StreamedCharSlice(sb.toString(), -1);
            }
        }

        decoder.flush(charBuf);
        charBuf.flip();
        while (charBuf.hasRemaining()) {
            char c = charBuf.get();
            if (charIndex >= safeStart && charIndex < safeEnd) {
                sb.append(c);
            }
            charIndex++;
        }

        return new SodiumEditorView.StreamedCharSlice(sb.toString(), charIndex);
    }

    public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
        if (lineText == null) return 0L;
        int safe = Math.max(0, Math.min(charIndex, lineText.length()));
        if (safe == 0) return 0L;
        return lineText.substring(0, safe).getBytes(fileCharset).length;
    }

    public boolean isSingleByteCharset() {
        try {
            if (view.binarySafeRenderingEnabled) return true;
            return fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
        } catch (Exception ignored) {
            return true;
        }
    }

    public boolean isFileOpened() {
        return isFileOpened;
    }

    public void setFileOpened(boolean opened) {
        isFileOpened = opened;
    }

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
                    synchronized (lineOffsetsLock) {
                        view.post(() -> callback.onResult(getLineOffsets().length));
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

    public String readRangeText(int sL, int sC, int eL, int eC) {
        int startL = sL, startC = sC, endL = eL, endC = eC;
        if (view.comparePos(startL, startC, endL, endC) > 0) {
            int tL = startL, tC = startC;
            startL = endL;
            startC = endC;
            endL = tL;
            endC = tC;
        }

        if (startL >= view.windowStartLine && endL < view.windowStartLine + view.linesWindow.size()) {
            StringBuilder sb = new StringBuilder();
            for (int line = startL; line <= endL; line++) {
                String ln = view.getLineFromWindowLocal(line - view.windowStartLine);
                if (ln == null) ln = "";
                int from = (line == startL) ? Math.min(startC, ln.length()) : 0;
                int to = (line == endL) ? Math.min(endC, ln.length()) : ln.length();
                if (from < to) sb.append(ln, from, to);
                if (line < endL) sb.append('\n');
            }
            return sb.toString();
        }

        if (view.sourceFile == null || !view.sourceFile.exists()) return "";
        FileManager.RangeBytes range = view.fileManager.computeByteRangeFastOrScan(view.sourceFile, startL, startC, endL, endC);
        if (range == null) return "";
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.sourceFile, "r")) {
            long len = raf.length();
            long startByte = Math.max(0, Math.min(range.startByte, len));
            long endByte = Math.max(0, Math.min(range.endByte, len));
            if (endByte < startByte) {
                long t = startByte;
                startByte = endByte;
                endByte = t;
            }
            int size = (int) Math.min(Integer.MAX_VALUE, endByte - startByte);
            byte[] buf = new byte[size];
            raf.seek(startByte);
            raf.readFully(buf);
            return new String(buf, fileCharset);
        } catch (Exception ignore) {
            return "";
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
        SodiumEditorView.CursorTarget target,
        boolean finishLargeEditUi) {
        view.ioHandler.post(
            () -> {
                try {
                    if (inFile == null || !inFile.exists()) {
                        view.post(
                            () -> {
                                if (finishLargeEditUi) view.endLargeEditUiPublic(true);
                            });
                        return;
                    }

                    FileManager.RangeBytes range = view.fileManager.computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
                    if (range == null) {
                        view.post(
                            () -> {
                                if (finishLargeEditUi) view.endLargeEditUiPublic(true);
                            });
                        return;
                    }

                    File outFile = File.createTempFile("popedit_", ".tmp", view.getContext().getCacheDir());
                    byte[] insertBytes =
                        (insertText == null) ? new byte[0] : insertText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    try (java.io.RandomAccessFile rafIn = new java.io.RandomAccessFile(inFile, "r");
                        java.nio.channels.FileChannel inCh = rafIn.getChannel();
                        java.io.RandomAccessFile rafOut = new java.io.RandomAccessFile(outFile, "rw");
                        java.nio.channels.FileChannel outCh = rafOut.getChannel()) {

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
                            outCh.write(java.nio.ByteBuffer.wrap(insertBytes));
                        }

                        transferRange(inCh, outCh, endByte, fileLen - endByte);
                        outCh.force(true);
                    }

                    view.post(
                        () -> {
                            if (opToken != view.undoRedo.getEditVersion()) return;

                            view.invalidatePendingIO();

                            if (inFile != null) {
                                try (java.io.FileInputStream fis = new java.io.FileInputStream(outFile);
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(inFile)) {
                                    byte[] buf = new byte[8192];
                                    int r;
                                    while ((r = fis.read(buf)) > 0) {
                                        fos.write(buf, 0, r);
                                    }
                                    fos.flush();
                                } catch (Exception ignore) {
                                }
                                outFile.delete();
                                updateSourceFile(inFile);
                            } else {
                                updateSourceFile(outFile);
                            }
                            setFileCleared(false);

                            synchronized (view.modifiedLines) {
                                view.modifiedLines.clear();
                            }
                            synchronized (view.lineWidthCache) {
                                view.lineWidthCache.clear();
                            }
                            view.currentMaxWindowLineWidth = 0f;
                            view.globalMaxLineWidth = 0f;
                            view.scrollManager.maxLineWidthForScroll = 0f;
                            view.scrollManager.maxTextStartXForScroll = 0f;
                            view.scrollManager.maxScrollXForScroll = 0f;
                            view.undoRedo.resetLineCountDelta();

                            synchronized (view.lineOffsetsLock) {
                                view.lineOffsets = new long[0];
                            }
                            isIndexReady = false;
                            isIndexBuilding = false;
                            isIndexDisabled = false;
                            indexDisabledPath = null;
                            indexDisabledFileLength = -1L;
                            setEof(false);

                            view.ioHandler.post(view::buildFileIndex);
                            view.wordWrapManager.onLineCountChanged(view);

                            view.cursorManager.setLineAndChar(Math.max(0, target.line), Math.max(0, target.ch));

                            boolean cursorInsideWindow =
                                (view.cursorManager.getLine() >= view.windowStartLine
                                    && view.cursorManager.getLine() < view.windowStartLine + view.linesWindow.size());

                            if (cursorInsideWindow) {
                                synchronized (view.linesWindow) {
                                    view.isEof = view.linesWindow.size() < view.windowSize + (view.prefetchLines * 2);
                                }
                                view.recalculateMaxLineWidth();
                                view.requestFocus();
                                android.view.inputmethod.InputMethodManager imm =
                                    (android.view.inputmethod.InputMethodManager)
                                        view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.restartInput(view);
                                if (finishLargeEditUi) view.endLargeEditUiPublic(false);
                                view.invalidate();
                            } else {
                                int targetStart = Math.max(0, view.cursorManager.getLine() - view.prefetchLines);
                                view.loadWindowAround(
                                    targetStart,
                                    () -> {
                                        String ln = view.getLineTextForRender(view.cursorManager.getLine());
                                        view.cursorManager.clampCharToLineLength(view.cursorManager.getLine());
                                        view.clampScrollY();
                                        view.scrollManager.keepCursorVisibleHorizontally();
                                        view.requestFocus();
                                        android.view.inputmethod.InputMethodManager imm =
                                            (android.view.inputmethod.InputMethodManager)
                                                view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                                        if (imm != null) imm.restartInput(view);
                                        if (finishLargeEditUi) view.endLargeEditUiPublic(false);
                                    });
                            }
                        });
                } catch (Exception ignore) {
                    view.post(
                        () -> {
                            if (finishLargeEditUi) view.endLargeEditUiPublic(true);
                        });
                }
            });
    }

    private void transferRange(java.nio.channels.FileChannel inCh, java.nio.channels.FileChannel outCh, long position, long count)
        throws Exception {
        long remaining = count;
        long pos = position;
        while (remaining > 0) {
            long sent = inCh.transferTo(pos, remaining, outCh);
            if (sent <= 0) break;
            pos += sent;
            remaining -= sent;
        }
    }

    public static final class RangeBytes {
        final long startByte, endByte;

        RangeBytes(long s, long e) {
            startByte = s;
            endByte = e;
        }
    }

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
            synchronized (lineOffsetsLock) {
                if (!isIndexReady()) return null;
                if (sL < 0 || eL < 0) return null;
                if (sL >= getLineOffsets().length || eL >= getLineOffsets().length) return null;
                startLineByte = getLineOffsets()[sL];
                endLineByte = getLineOffsets()[eL];
            }

            String startLineText = readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + computeByteOffsetInLineUtf8(endLineText, eC);

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

            String startLineText = readLineUtf8AtByte(raf, startLineByte);
            String endLineText = (eL == sL) ? startLineText : readLineUtf8AtByte(raf, endLineByte);

            long startByte = startLineByte + computeByteOffsetInLineUtf8(startLineText, sC);
            long endByte = endLineByte + computeByteOffsetInLineUtf8(endLineText, eC);

            return new RangeBytes(startByte, endByte);
        } catch (Exception e) {
            return null;
        }
    }

    public void onUndoRedoRewriteSuccess(File inFile) {
        updateSourceFile(inFile);
        synchronized (lineOffsetsLock) {
            setLineOffsets(new long[0]);
        }
        isIndexReady = false;
        isIndexBuilding = false;
        isIndexDisabled = false;
        indexDisabledPath = null;
        indexDisabledFileLength = -1L;
        view.ioHandler.post(view::buildFileIndex);
    }

    public String getTextSnapshot() {
        int total = view.getLinesCount();
        if (total <= 0) return "";
        java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
        if (isIndexReady() && view.sourceFile != null && view.sourceFile.exists()) {
            view.populateDirectLinesForRange(0, total - 1, direct);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            String line = view.getLineTextForRenderWithDirect(i, direct);
            if (line == null) line = "";
            sb.append(line);
            if (i < total - 1) sb.append('\n');
        }
        return sb.toString();
    }
}