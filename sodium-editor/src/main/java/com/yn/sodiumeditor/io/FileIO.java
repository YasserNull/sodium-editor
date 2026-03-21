package com.yn.sodiumeditor.io;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * File I/O operations for SodiumEditor.
 * Handles file reading, writing, indexing, and window loading.
 */
public class FileIO {

    private final SodiumEditor editor;

    // IO thread and handler
    public final HandlerThread ioThread;
    public final Handler ioHandler;
    public BufferedReader readerForFile = null;
    public File sourceFile = null;
    public boolean isEof = false;
    public final AtomicInteger ioTaskVersion = new AtomicInteger(0);
    public boolean isFileCleared = false;

    // File charset
    public Charset fileCharset = StandardCharsets.UTF_8;

    // Index state
    public final Object lineOffsetsLock = new Object();
    public long[] lineOffsets = new long[0];
    public volatile boolean isIndexReady = false;
    public volatile boolean isIndexBuilding = false;
    public volatile boolean isIndexDisabled = false;
    public volatile String indexDisabledPath = null;
    public volatile long indexDisabledFileLength = -1L;
    public static final long MAX_INDEX_BYTES_HARD = 64L * 1024 * 1024;

    // Window loading state
    public volatile boolean isWindowLoading = false;

    // Direct read cache for fast fling rendering
    public final LinkedHashMap<Integer, String> directLineCache =
            new LinkedHashMap<Integer, String>(600, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > 600;
                }
            };

    public FileIO(SodiumEditor editor) {
        this.editor = editor;
        ioThread = new HandlerThread("SodiumEditor-IO");
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
    }

    /**
     * Check and load window around visible area.
     */
    public void checkAndLoadWindow() {
        if (sourceFile == null || isFileCleared) return;
        if (editor.getWidth() == 0 || editor.getHeight() == 0) return;
        if (isWindowLoading) return;

        int firstVisibleIndex = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
        int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight);
        int firstVisibleLine;
        int lastVisibleLine;
        if (editor.wordWrap.isWordWrapEnabled) {
            firstVisibleLine = editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
            lastVisibleLine = editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
        } else {
            firstVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
            lastVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
        }
        firstVisibleLine = Math.max(0, firstVisibleLine);
        lastVisibleLine = Math.max(firstVisibleLine, lastVisibleLine);
        int winEnd;
        synchronized (editor.textRender.linesWindow) {
            winEnd = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
        }

        int topMargin = Math.max(0, editor.textRender.prefetchLines);
        int bottomMargin = Math.max(0, editor.textRender.prefetchLines);

        boolean needTop = editor.textRender.windowStartLine > 0 && firstVisibleLine < editor.textRender.windowStartLine + topMargin;
        boolean needBottom = !isEof && lastVisibleLine > winEnd - bottomMargin;
        boolean outside = firstVisibleLine < editor.textRender.windowStartLine || firstVisibleLine > winEnd;

        if (needTop || needBottom || outside) {
            int targetStart = Math.max(0, firstVisibleLine - editor.textRender.prefetchLines);
            loadWindowAround(targetStart, null, false);
        }
    }

    /**
     * Load window around specified line.
     */
    

    /**
     * Load window around specified line with optional width recalculation.
     */
    public void loadWindowAround(int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
        if (isWindowLoading) return;
        editor.loadingCircle.maxWidthRecalcToken++;
        editor.logRender(
                "loadWindowAround-start",
                "loadWindowAround startLine=" + startLine
                        + " isIndexReady=" + isIndexReady
                        + " windowStart=" + editor.textRender.windowStartLine
                        + " windowSize=" + editor.textRender.windowSize
                        + " prefetch=" + editor.textRender.prefetchLines,
                500);

        if (isFileCleared) {
            if (onComplete != null) {
                editor.post(onComplete);
            }
            return;
        }

        if (sourceFile == null) {
            if (onComplete != null) editor.post(onComplete);
            return;
        }

        isWindowLoading = true;
        final int taskVersion = ioTaskVersion.incrementAndGet();
        final int requestedStart = Math.max(0, startLine);

        ioHandler.post(() -> {
            try {
                if (taskVersion != ioTaskVersion.get()) {
                    editor.post(() -> {
                        isWindowLoading = false;
                        checkAndLoadWindow();
                    });
                    return;
                }

                int actualStart = requestedStart;

                if (isIndexReady) {
                    synchronized (lineOffsetsLock) {
                        if (lineOffsets.length > 0 && actualStart >= lineOffsets.length) {
                            actualStart = Math.max(0, lineOffsets.length - 1);
                        }
                    }
                }

                List<String> newWin = new ArrayList<>();
                SparseIntArray newStreamedLengths = new SparseIntArray();
                SparseIntArray newStreamedSliceStarts = new SparseIntArray();
                boolean fileEndsWithNewline = false;
                boolean reachedEof = false;
                boolean trailingEmptyFromIndex = false;
                int debugLineLogs = 0;

                if (isIndexReady) {
                    try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                        long fileLen = raf.length();
                        editor.logRender(
                                "loadWindowAround-io",
                                "ioLoad indexReady start=" + actualStart + " fileLen=" + fileLen,
                                500);
                        if (fileLen > 0) {
                            raf.seek(fileLen - 1);
                            fileEndsWithNewline = (raf.read() == '\n');
                        }
                        int limit = editor.textRender.windowSize + (editor.textRender.prefetchLines * 2);
                        int lineIndex = actualStart;
                        int maxLine;
                        synchronized (lineOffsetsLock) {
                            maxLine = lineOffsets.length;
                        }
                        while (newWin.size() < limit) {
                            if (lineIndex >= maxLine) {
                                reachedEof = true;
                                editor.logRender(
                                        "loadWindowAround-eof",
                                        "eof indexReady lineIndex=" + lineIndex + " maxLine=" + maxLine,
                                        500);
                                break;
                            }
                            long lineStart;
                            synchronized (lineOffsetsLock) {
                                lineStart = lineOffsets[lineIndex];
                            }
                            long lineByteLen = getLineByteLengthFromIndex(raf, lineIndex, fileLen);
                            int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                            if (debugLineLogs < 5) {
                                editor.logRender(
                                        "loadWindowAround-line",
                                        "line idx=" + lineIndex
                                                + " start=" + lineStart
                                                + " bytes=" + lineByteLen
                                                + " len=" + lineLen,
                                        0);
                                debugLineLogs++;
                            }
                            if (editor.shouldStreamLineLength(lineLen)) {
                                int sliceStart = 0;
                                int sliceEnd = Math.max(1, Math.min(lineLen, editor.textRender.getInitialStreamedSliceSize()));
                                if (editor.isSingleByteCharset()) {
                                    String slice = readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                                    if (debugLineLogs < 5) {
                                        String preview = slice;
                                        if (preview.length() > 80) preview = preview.substring(0, 80);
                                        editor.logRender(
                                                "loadWindowAround-lineText",
                                                "lineText idx=" + lineIndex + " slice=\"" + preview + "\"",
                                                0);
                                    }
                                    newWin.add(slice);
                                    newStreamedLengths.put(lineIndex, lineLen);
                                    newStreamedSliceStarts.put(lineIndex, sliceStart);
                                } else {
                                    sliceEnd = Math.max(1, editor.textRender.getInitialStreamedSliceSize());
                                    SodiumEditor.StreamedCharSlice slice =
                                            readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                                    if (debugLineLogs < 5) {
                                        String preview = slice.text;
                                        if (preview.length() > 80) preview = preview.substring(0, 80);
                                        editor.logRender(
                                                "loadWindowAround-lineText",
                                                "lineText idx=" + lineIndex + " slice=\"" + preview + "\"",
                                                0);
                                    }
                                    newWin.add(slice.text);
                                    newStreamedLengths.put(lineIndex, slice.length);
                                    newStreamedSliceStarts.put(lineIndex, sliceStart);
                                }
                            } else {
                                String ln = readLineUtf8AtByte(raf, lineStart);
                                if (debugLineLogs < 5) {
                                    String preview = ln;
                                    if (preview.length() > 80) preview = preview.substring(0, 80);
                                    editor.logRender(
                                            "loadWindowAround-lineText",
                                            "lineText idx=" + lineIndex + " text=\"" + preview + "\"",
                                            0);
                                }
                                newWin.add(ln);
                            }
                            lineIndex++;
                        }
                        if (fileEndsWithNewline) {
                            synchronized (lineOffsetsLock) {
                                trailingEmptyFromIndex =
                                        lineOffsets.length > 0 && lineOffsets[lineOffsets.length - 1] == fileLen;
                            }
                        }
                    }
                } else {
                    try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                        long fileLen = raf.length();
                        editor.logRender(
                                "loadWindowAround-io",
                                "ioLoad noIndex start=" + actualStart + " fileLen=" + fileLen,
                                500);
                        if (fileLen > 0) {
                            raf.seek(fileLen - 1);
                            fileEndsWithNewline = (raf.read() == '\n');
                        }
                        raf.seek(0);
                        int skipped = 0;
                        while (skipped < actualStart) {
                            LineScanResult scan = scanLineLength(raf);
                            if (scan.reachedEof) break;
                            skipped++;
                        }
                        actualStart = skipped;

                        int limit = editor.textRender.windowSize + (editor.textRender.prefetchLines * 2);
                        int lineIndex = actualStart;
                        while (newWin.size() < limit) {
                            long lineStart = raf.getFilePointer();
                            if (lineStart >= fileLen) {
                                reachedEof = true;
                                editor.logRender(
                                        "loadWindowAround-eof",
                                        "eof noIndex lineIndex=" + lineIndex + " fileLen=" + fileLen,
                                        500);
                                break;
                            }
                            LineScanResult scan = scanLineLength(raf);
                            long afterPos = raf.getFilePointer();
                            long lineByteLen = scan.length;
                            int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                            if (debugLineLogs < 5) {
                                editor.logRender(
                                        "loadWindowAround-line",
                                        "line idx=" + lineIndex
                                                + " start=" + lineStart
                                                + " after=" + afterPos
                                                + " bytes=" + lineByteLen
                                                + " len=" + lineLen
                                                + " eof=" + scan.reachedEof,
                                        0);
                                debugLineLogs++;
                            }
                            if (editor.shouldStreamLineLength(lineLen)) {
                                int sliceStart = 0;
                                int sliceEnd = Math.max(1, Math.min(lineLen, editor.textRender.getInitialStreamedSliceSize()));
                                if (editor.isSingleByteCharset()) {
                                    String slice = readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                                    if (debugLineLogs < 5) {
                                        String preview = slice;
                                        if (preview.length() > 80) preview = preview.substring(0, 80);
                                        editor.logRender(
                                                "loadWindowAround-lineText",
                                                "lineText idx=" + lineIndex + " slice=\"" + preview + "\"",
                                                0);
                                    }
                                    newWin.add(slice);
                                    newStreamedLengths.put(lineIndex, lineLen);
                                    newStreamedSliceStarts.put(lineIndex, sliceStart);
                                } else {
                                    sliceEnd = Math.max(1, editor.textRender.getInitialStreamedSliceSize());
                                    SodiumEditor.StreamedCharSlice slice =
                                            readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                                    if (debugLineLogs < 5) {
                                        String preview = slice.text;
                                        if (preview.length() > 80) preview = preview.substring(0, 80);
                                        editor.logRender(
                                                "loadWindowAround-lineText",
                                                "lineText idx=" + lineIndex + " slice=\"" + preview + "\"",
                                                0);
                                    }
                                    newWin.add(slice.text);
                                    newStreamedLengths.put(lineIndex, slice.length);
                                    newStreamedSliceStarts.put(lineIndex, sliceStart);
                                }
                            } else {
                                raf.seek(lineStart);
                                byte[] buf = new byte[lineLen];
                                if (lineLen > 0) raf.readFully(buf);
                                String ln;
                                if (lineLen > 0) {
                                    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
                                        ln = editor.binaryRender.bytesToControlVisible(buf, buf.length);
                                    } else {
                                        ln = new String(buf, fileCharset);
                                    }
                                } else {
                                    ln = "";
                                }
                                if (debugLineLogs < 5) {
                                    String preview = ln;
                                    if (preview.length() > 80) preview = preview.substring(0, 80);
                                    editor.logRender(
                                            "loadWindowAround-lineText",
                                            "lineText idx=" + lineIndex + " text=\"" + preview + "\"",
                                            0);
                                }
                                newWin.add(ln);
                            }
                            raf.seek(afterPos);
                            if (scan.reachedEof) {
                                reachedEof = true;
                                editor.logRender(
                                        "loadWindowAround-eof",
                                        "eof scanReached lineIndex=" + lineIndex,
                                        500);
                                break;
                            }
                            lineIndex++;
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (newWin.isEmpty()) {
                    newWin.add("");
                    actualStart = 0;
                }
                if (reachedEof && fileEndsWithNewline && !trailingEmptyFromIndex) {
                    newWin.add("");
                }

                boolean eof = newWin.size() < editor.textRender.windowSize + (editor.textRender.prefetchLines * 2);

                synchronized (editor.textRender.modifiedLines) {
                    for (int i = 0; i < newWin.size(); i++) {
                        int globalLineNum = actualStart + i;
                        if (editor.textRender.modifiedLines.containsKey(globalLineNum)) {
                            String modifiedLine = editor.textRender.modifiedLines.get(globalLineNum);
                            if (modifiedLine != null) newWin.set(i, modifiedLine);
                            newStreamedLengths.delete(globalLineNum);
                            newStreamedSliceStarts.delete(globalLineNum);
                        }
                    }
                }

                if (taskVersion != ioTaskVersion.get()) {
                    editor.post(() -> {
                        isWindowLoading = false;
                        checkAndLoadWindow();
                    });
                    return;
                }

                final int finalStart = actualStart;
                final SparseIntArray finalStreamedLengths = newStreamedLengths;
                final SparseIntArray finalStreamedSliceStarts = newStreamedSliceStarts;
                editor.post(() -> {
                    isWindowLoading = false;
                    if (taskVersion != ioTaskVersion.get()) {
                        checkAndLoadWindow();
                        return;
                    }
                    synchronized (editor.textRender.linesWindow) {
                        editor.textRender.linesWindow.clear();
                        editor.textRender.linesWindow.addAll(newWin);
                        editor.textRender.windowStartLine = finalStart;
                        isEof = eof;
                    }
                    synchronized (editor.textRender.streamedLinesLock) {
                        editor.textRender.streamedLineLengths.clear();
                        editor.textRender.streamedLineSliceStarts.clear();
                        for (int i = 0; i < finalStreamedLengths.size(); i++) {
                            int key = finalStreamedLengths.keyAt(i);
                            editor.textRender.streamedLineLengths.put(key, finalStreamedLengths.valueAt(i));
                            editor.textRender.streamedLineSliceStarts.put(key, finalStreamedSliceStarts.get(key, 0));
                        }
                    }
                    synchronized (editor.textRender.streamedLinesLockLinesLock) {
                        editor.textRender.streamedLinesLockLineLengths.clear();
                        editor.textRender.streamedLinesLockLineSliceStarts.clear();
                        for (int i = 0; i < finalStreamedLengths.size(); i++) {
                            int key = finalStreamedLengths.keyAt(i);
                            editor.textRender.streamedLinesLockLineLengths.put(key, finalStreamedLengths.valueAt(i));
                            editor.textRender.streamedLinesLockLineSliceStarts.put(key, finalStreamedSliceStarts.get(key, 0));
                        }
                    }
                    editor.lineNumber.invalidateLineNumberCache();
                    editor.invalidateHighlightEnsureRange();
                    editor.bracketGuides.invalidateBracketGuideCache();
                    editor.logRender(
                            "loadWindowAround-end",
                            "windowLoaded start=" + finalStart
                                    + " size=" + editor.textRender.linesWindow.size()
                                    + " eof=" + isEof
                                    + " streamed=" + finalStreamedLengths.size(),
                            500);
                    if (recalculateWidthSync) {
                        editor.recalculateMaxLineWidth();
                    } else {
                        synchronized (editor.textRender.lineWidthCache) {
                            editor.textRender.lineWidthCache.clear();
                        }
                        editor.textRender.currentMaxWindowLineWidth = 0f;
                        editor.textRender.globalMaxLineWidth = 0f;
                        recalculateMaxLineWidthAsync();
                    }
                    if (editor.wordWrap.isWordWrapEnabled) {
                        if (editor.wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) {
                            editor.wordWrap.wrapMetricsReady = false;
                        } else {
                            if (!editor.wordWrap.wrapMetricsReady || editor.wordWrap.wrapLineCounts == null || editor.wordWrap.wrapLinePrefix == null) {
                                if (editor.getWidth() > 0) {
                                    editor.wordWrap.buildWrapMetricsForWindowSnapshot();
                                }
                            }
                            editor.wordWrap.scheduleWrapMetricsSnapshotIfNeeded(Math.max(1, Math.round(editor.wordWrap.getWrapWidth())));
                            editor.wordWrap.requestWrapPrefixRebuild();
                        }
                    }
                    editor.invalidate();
                    if (onComplete != null) onComplete.run();
                });
            } catch (Exception e) {
                e.printStackTrace();
                editor.post(() -> {
                    isWindowLoading = false;
                    if (onComplete != null) onComplete.run();
                });
            }
        });
    }

    /**
     * Build file index for fast random access.
     */
    public void buildFileIndex() {
        if (sourceFile == null || !sourceFile.exists()) {
            isIndexReady = false;
            isIndexBuilding = false;
            return;
        }
        if (isIndexDisabled) {
            String path = sourceFile.getAbsolutePath();
            long len = sourceFile.length();
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
        final int taskVersion = ioTaskVersion.get();
        ioHandler.post(() -> {
            long[] offsets = buildIndexJava(sourceFile.getAbsolutePath());
            if (taskVersion != ioTaskVersion.get()) {
                isIndexBuilding = false;
                return;
            }
            if (offsets != null) {
                synchronized (lineOffsetsLock) {
                    if (taskVersion == ioTaskVersion.get()) {
                        lineOffsets = offsets;
                        isIndexReady = true;
                        editor.post(() -> {
                            editor.requestLayout();
                            int total;
                            synchronized (lineOffsetsLock) {
                                total = lineOffsets.length;
                            }
                            int expected = Math.min(
                                    total,
                                    editor.textRender.windowSize + (editor.textRender.prefetchLines * 2));
                            int current;
                            synchronized (editor.textRender.linesWindow) {
                                current = editor.textRender.linesWindow.size();
                            }
                            editor.logRender(
                                    "index-ready",
                                    "indexReady total=" + total + " currentWindow=" + current + " expected=" + expected,
                                    0);
                            if (expected > 0 && current < expected && !isWindowLoading) {
                                loadWindowAround(Math.max(0, editor.textRender.windowStartLine), null, false);
                            }
                        });
                        if (editor.wordWrap.isWordWrapEnabled) editor.post(() -> editor.wordWrap.scheduleWrapMetricsBuild());
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

    /**
     * Build index by scanning for newlines.
     */
    public long[] buildIndexJava(String filepath) {
        long numNewlines = 0;
        long fileLength = 0;

        try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
            fileLength = raf.length();
            if (fileLength == 0) {
                return new long[0];
            }

            byte[] buffer = new byte[8192];
            long currentReadPos = 0;
            while (currentReadPos < fileLength) {
                raf.seek(currentReadPos);
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) break;

                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        numNewlines++;
                    }
                }
                currentReadPos += bytesRead;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (numNewlines >= Integer.MAX_VALUE - 1) {
            isIndexDisabled = true;
            indexDisabledPath = filepath;
            indexDisabledFileLength = fileLength;
            return null;
        }
        long lines = numNewlines + 1;
        long bytesRequired = lines * (long) Long.BYTES;
        long maxMemory = Runtime.getRuntime().maxMemory();
        long maxIndexBytes = Math.min(MAX_INDEX_BYTES_HARD, Math.max(16L * 1024 * 1024, maxMemory / 6));
        if (bytesRequired > maxIndexBytes) {
            isIndexDisabled = true;
            indexDisabledPath = filepath;
            indexDisabledFileLength = fileLength;
            return null;
        }

        long[] offsetsArray = new long[(int) lines];
        int currentOffsetIndex = 0;

        try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
            offsetsArray[currentOffsetIndex++] = 0L;
            long currentPos = 0;
            byte[] buffer = new byte[8192];
            while (currentPos < fileLength) {
                raf.seek(currentPos);
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) break;

                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        long nextStart = currentPos + i + 1;
                        if (currentOffsetIndex < offsetsArray.length) {
                            offsetsArray[currentOffsetIndex++] = nextStart;
                        } else {
                            break;
                        }
                    }
                }
                currentPos += bytesRead;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return offsetsArray;
    }

    /**
     * Read line at byte offset.
     */
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
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
            byte[] data = baos.toByteArray();
            return editor.binaryRender.bytesToControlVisible(data, data.length);
        }
        return baos.toString(fileCharset.name());
    }

    /**
     * Get line byte length from index.
     */
    public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen) throws Exception {
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
            len -= 1L;
            if (len > 0L) {
                raf.seek(Math.max(start, end - 2));
                int last = raf.read();
                if (last == '\r') {
                    len -= 1L;
                }
            }
        }
        return Math.max(0L, len);
    }

    /**
     * Read line slice at byte range.
     */



    /**
     * Read line slice by chars.
     */
    public SodiumEditor.StreamedCharSlice readLineSliceByChars(RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength) throws Exception {
        return editor.binaryRender.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength, fileCharset);
    }

    /**
     * Scan line length.
     */
    public static class LineScanResult {
        public final long length;
        public final boolean reachedEof;

        public LineScanResult(long length, boolean reachedEof) {
            this.length = length;
            this.reachedEof = reachedEof;
        }
    }

    /**
     * Scan line length from current position.
     */
    public LineScanResult scanLineLength(RandomAccessFile raf) throws IOException {
        long start = raf.getFilePointer();
        long len = 0;
        boolean eof = false;
        byte[] buf = new byte[1024];

        while (true) {
            int n = raf.read(buf);
            if (n <= 0) {
                eof = true;
                break;
            }
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    int lineLen = i;
                    if (i > 0 && buf[i - 1] == '\r') lineLen -= 1;
                    len += Math.max(0, lineLen);
                    long newPos = start + (len + 1);
                    raf.seek(newPos);
                    return new LineScanResult(len, false);
                }
            }
            len += n;
        }
        return new LineScanResult(len, eof);
    }

    /**
     * Reopen reader at start.
     */
    public BufferedReader reopenReaderAtStart() {
        try {
            if (readerForFile != null) {
                try {
                    readerForFile.close();
                } catch (Exception ignored) {
                }
                readerForFile = null;
            }
            if (sourceFile != null) {
                readerForFile = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile), fileCharset));
                return readerForFile;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Cancel and close reader.
     */
    public void cancelAndCloseReader() {
        ioHandler.post(() -> {
            try {
                if (readerForFile != null) {
                    readerForFile.close();
                    readerForFile = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Invalidate pending IO.
     */
    public void invalidatePendingIO() {
        ioTaskVersion.incrementAndGet();
        ioHandler.removeCallbacksAndMessages(null);
        editor.clearHighlightCaches();
        if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics();
        if (editor.codeFold.isCodeFoldingEnabled) {
            editor.codeFold.foldIntervalsDirty = true;
        }
    }

    /**
     * Read range text from file.
     */
    public String readRangeText(int sL, int sC, int eL, int eC) {
        int startL = sL, startC = sC, endL = eL, endC = eC;
        if (editor.editOperators.comparePos(startL, startC, endL, endC) > 0) {
            int tL = startL, tC = startC;
            startL = endL;
            startC = endC;
            endL = tL;
            endC = tC;
        }

        if (startL >= editor.textRender.windowStartLine && endL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
            StringBuilder sb = new StringBuilder();
            for (int line = startL; line <= endL; line++) {
                String ln = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
                if (ln == null) ln = "";
                int from = (line == startL) ? Math.min(startC, ln.length()) : 0;
                int to = (line == endL) ? Math.min(endC, ln.length()) : ln.length();
                if (from < to) sb.append(ln, from, to);
                if (line < endL) sb.append('\n');
            }
            return sb.toString();
        }

        if (sourceFile == null || !sourceFile.exists()) return "";
        EditOperators.RangeBytes range = editor.editOperators.computeByteRangeFastOrScan(sourceFile, startL, startC, endL, endC);
        if (range == null) return "";
        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
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

    /**
     * Count total lines asynchronously.
     */
    public void countTotalLines(LineCountCallback callback) {
        final int taskVersion = ioTaskVersion.get();
        ioHandler.post(() -> {
            if (taskVersion != ioTaskVersion.get()) {
                editor.post(() -> callback.onResult(-1));
                return;
            }
            if (isIndexReady && sourceFile != null) {
                synchronized (lineOffsetsLock) {
                    editor.post(() -> callback.onResult(lineOffsets.length));
                }
                return;
            }
            int count = 0;
            if (sourceFile != null && sourceFile.exists()) {
                try (FileInputStream is = new FileInputStream(sourceFile)) {
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
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalCount));
        });
    }

    /**
     * Line count callback interface.
     */
    public interface LineCountCallback {
        void onResult(int count);
    }

    /**
     * Populate direct lines for range.
     */
    public void populateDirectLinesForRange(int startLine, int endLineInclusive, java.util.Map<Integer, String> out) {
        if (out == null) return;
        if (sourceFile == null || !sourceFile.exists()) return;
        if (!isIndexReady) return;

        int start = Math.max(0, startLine);
        int end = Math.max(start, endLineInclusive);

        int maxLine = -1;
        synchronized (lineOffsetsLock) {
            maxLine = lineOffsets.length - 1;
        }
        if (maxLine < 0) return;
        if (start > maxLine) return;
        if (end > maxLine) end = maxLine;

        synchronized (directLineCache) {
            for (int l = start; l <= end; l++) {
                String c = directLineCache.get(l);
                if (c != null) out.put(l, c);
            }
        }

        int l = start;
        while (l <= end) {
            if (out.containsKey(l)) {
                l++;
                continue;
            }

            int segStart = l;
            int segEnd = l;
            while (segEnd + 1 <= end && !out.containsKey(segEnd + 1)) segEnd++;

            try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                long fileLen = raf.length();
                for (int cur = segStart; cur <= segEnd; cur++) {
                    long lineStart;
                    synchronized (lineOffsetsLock) {
                        if (cur >= lineOffsets.length) break;
                        lineStart = lineOffsets[cur];
                    }
                    long lineByteLen = getLineByteLengthFromIndex(raf, cur, fileLen);
                    int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                    String ln;
                    if (editor.shouldStreamLineLength(lineLen)) {
                        editor.textRender.computeStreamedSliceBounds(null, cur, lineLen, editor.textRender.streamedSliceTmp);
                        int sliceStart = editor.textRender.streamedSliceTmp[0];
                        int sliceEnd = editor.textRender.streamedSliceTmp[1];
                        if (editor.isSingleByteCharset()) {
                            ln = readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                            editor.setStreamedLineInfo(cur, lineLen, sliceStart);
                        } else {
                            SodiumEditor.StreamedCharSlice slice =
                                    readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                            ln = slice.text;
                            editor.setStreamedLineInfo(cur, slice.length, sliceStart);
                        }
                    } else {
                        ln = readLineUtf8AtByte(raf, lineStart);
                    }
                    out.put(cur, (ln == null) ? "" : ln);
                }
            } catch (Exception ignored) {
            }

            l = segEnd + 1;
        }

        synchronized (directLineCache) {
            for (java.util.Map.Entry<Integer, String> e : out.entrySet()) {
                if (e.getKey() >= start && e.getKey() <= end) {
                    directLineCache.put(e.getKey(), (e.getValue() == null) ? "" : e.getValue());
                }
            }
        }
    }

    /**
     * Ensure line is in window.
     */
    public void ensureLineInWindow(int globalLine, boolean blockingIfAbsent) {
        editor.autoCompletion.clearActiveSuggestion();
        if (globalLine >= editor.textRender.windowStartLine && globalLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) return;
        if (sourceFile != null) {
            int targetStart = Math.max(0, globalLine - editor.textRender.prefetchLines);
            loadWindowAround(targetStart, null, false);
        }
    }

    /**
     * Invalidate pending IO for edit operations.
     */
    public void invalidatePendingIOForEdit() {
        ioTaskVersion.incrementAndGet();
        ioHandler.removeCallbacksAndMessages(null);
        editor.clearHighlightCaches();
        if (editor.codeFold.isCodeFoldingEnabled) {
            editor.codeFold.foldIntervalsDirty = true;
            editor.indentGuides.markIntervalsDirty();
        }
    }
      public void loadFromFile(final File file) {
    invalidatePendingIOForEdit();
    isFileCleared = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.lineNumber.invalidateLineNumberCache();

    // Force clear wrap metrics for new file
    editor.wordWrap.wrapMetricsReady = false;
    editor.wordWrap.wrapLineCounts = null;
    editor.wordWrap.wrapLinePrefix = null;
    editor.wordWrap.totalWrapVisualLines = 0;
    editor.wordWrap.wrapPrefixValidUpToLine = -1;

    final int token = ++editor.loadingCircle.initialFileOpenToken;
    editor.loadingCircle.isInitialFileOpenLoading = true;
    if (editor.loadingCircle.showLoadingOnFileOpen) {
      if (editor.loadingCircle.initialFileOpenShowSpinner != null) {
        editor.caret.mainHandler.removeCallbacks(editor.loadingCircle.initialFileOpenShowSpinner);
      }
      editor.loadingCircle.initialFileOpenShowSpinner =
          () -> {
            if (!editor.loadingCircle.showLoadingOnFileOpen) return;
            if (!editor.loadingCircle.isInitialFileOpenLoading) return;
            if (token != editor.loadingCircle.initialFileOpenToken) return;
            editor.setDisable(true);
            editor.loadingCircle.showLoadingCircle(true);
          };
      editor.caret.mainHandler.postDelayed(editor.loadingCircle.initialFileOpenShowSpinner, 80);
    }

    sourceFile = file;
    editor.logRender(
            "loadFromFile",
            "loadFromFile path=" + file.getAbsolutePath()
                    + " size=" + file.length()
                    + " windowSize=" + editor.textRender.windowSize
                    + " prefetch=" + editor.textRender.prefetchLines,
            0);
    editor.textRender.windowStartLine = 0;
    synchronized (editor.textRender.linesWindow) {
      editor.textRender.linesWindow.clear();
    }
    synchronized (editor.textRender.modifiedLines) {
      editor.textRender.modifiedLines.clear();
    }
    synchronized (editor.textRender.lineWidthCache) {
      editor.textRender.lineWidthCache.clear();
    }
    editor.clearStreamedLineCaches();
    editor.clearHighlightCaches();
    editor.textRender.currentMaxWindowLineWidth = 0f;
    editor.textRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;
    synchronized (lineOffsetsLock) {
      lineOffsets = new long[0];
    }
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    editor.cursor.cursorLine = 0;
    editor.cursor.cursorChar = 0;
    isEof = false;
    editor.scroll.abortAnimation();
    editor.scroll.scrollY = 0f;
    editor.scroll.scrollX = 0f;
    editor.logRender(
            "loadFromFile-scroll",
            "loadFromFile scrollX=" + editor.scroll.scrollX + " scrollY=" + editor.scroll.scrollY,
            0);
    editor.editOperators.lineCountDelta = 0;

    loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
    ioHandler.post(() -> buildFileIndex());
    editor.requestLayout();
    editor.invalidate();
  }
    
  

  
  public void finishInitialFileOpenWarmup(final int token) {
    if (!editor.loadingCircle.isInitialFileOpenLoading) return;
    if (token != editor.loadingCircle.initialFileOpenToken) return;
    if (editor.getHeight() <= 0 || editor.textRender.lineHeight <= 0f) {
      editor.caret.mainHandler.postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
      return;
    }

    int firstVisibleLine = Math.max(0, editor.getGlobalLineForY( editor.scroll.scrollY));
    int viewHeight = editor.getHeight() - editor.keyboardHeight;
    if (viewHeight <= 0) viewHeight = editor.getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(viewHeight / editor.textRender.lineHeight) + 2);
    int lastVisibleLine = firstVisibleLine + visibleLines;

    editor.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, null);
    
    // Scan brackets for fold detection BEFORE showing the file
    if (editor.codeFold.isCodeFoldingEnabled && editor.bracketCache != null) {
      // Start scan and wait for it to complete
      editor.bracketCache.scanFileAsync();
      // Poll until scan is complete (max 5 seconds)
      pollScanCompletion(token, 0);
    } else {
      finishFileOpen(token);
    }
  }

  private void pollScanCompletion(final int token, int attempts) {
    if (token != editor.loadingCircle.initialFileOpenToken) return;
    if (attempts > 300) { // 5 seconds max
      finishFileOpen(token);
      return;
    }
    if (!editor.bracketCache.isScanning()) {
      finishFileOpen(token);
      return;
    }
    ioHandler.postDelayed(() ->pollScanCompletion(token, attempts + 1), 16);
  }

  private void finishFileOpen(final int token) {
    if (token != editor.loadingCircle.initialFileOpenToken) return;
    
    editor.loadingCircle.isInitialFileOpenLoading = false;
    if (editor.loadingCircle.initialFileOpenShowSpinner != null) {
      editor.caret.mainHandler.removeCallbacks(editor.loadingCircle.initialFileOpenShowSpinner);
      editor.loadingCircle.initialFileOpenShowSpinner = null;
    }
    editor.setDisable(false);
    editor.loadingCircle.showLoadingCircle(false);
    editor.invalidate();

    java.util.ArrayList<Runnable> callbacks;
    synchronized (editor.loadingCircle.initialLoadCallbacks) {
      if (editor.loadingCircle.initialLoadCallbacks.isEmpty()) return;
      callbacks = new java.util.ArrayList<>(editor.loadingCircle.initialLoadCallbacks);
      editor.loadingCircle.initialLoadCallbacks.clear();
    }
    for (Runnable cb : callbacks) {
      editor.post(cb);
    }
  }

  public void runAfterInitialLoad(@Nullable Runnable action) {
    if (action == null) return;
    if (!editor.loadingCircle.isInitialFileOpenLoading) {
      editor.post(action);
      return;
    }
    synchronized (editor.loadingCircle.initialLoadCallbacks) {
      editor.loadingCircle.initialLoadCallbacks.add(action);
    }
  }

  public void recalculateMaxLineWidthAsync() {
    final int token = ++editor.loadingCircle.maxWidthRecalcToken;
    final int startLine;
    final ArrayList<String> snapshot;
    synchronized (editor.textRender.linesWindow) {
      startLine = editor.textRender.windowStartLine;
      snapshot = new ArrayList<>(editor.textRender.linesWindow);
    }
    if (snapshot.isEmpty()) return;

    final int chunkSize = 120;
    editor.post(
        new Runnable() {
          int index = 0;
          float mx = 0f;

          @Override
          public void run() {
            if (token != editor.loadingCircle.maxWidthRecalcToken) return;
            int end = Math.min(snapshot.size(), index + chunkSize);
            for (int i = index; i < end; i++) {
              String line = snapshot.get(i);
              if (line == null) line = "";
              float w = editor.getWidthForLine(startLine + i, line);
              synchronized (editor.textRender.lineWidthCache) {
                editor.textRender.lineWidthCache.put(startLine + i, w);
              }
              if (w > mx) mx = w;
            }
            editor.textRender.currentMaxWindowLineWidth = mx;
            editor.textRender.globalMaxLineWidth = Math.max(editor.textRender.globalMaxLineWidth, mx);
            index = end;
            if (index < snapshot.size()) {
              editor.post(this);
            } else {
              editor.scroll.clampScrollX();
              editor.invalidate();
            }
          }
        });
  }

  

  public void clearContent() {
    invalidatePendingIOForEdit();
    sourceFile = null;
    isFileCleared = true;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    // Clear bracket cache and folds
    if (editor.codeFold.isCodeFoldingEnabled) {
      editor.bracketCache.clear();
      editor.codeFold.clearAllFolds();
    }

    // Force clear wrap metrics as content is being cleared
    editor.wordWrap.wrapMetricsReady = false;
    editor.wordWrap.wrapLineCounts = null;
    editor.wordWrap.wrapLinePrefix = null;
    editor.wordWrap.totalWrapVisualLines = 0;
    editor.wordWrap.wrapPrefixValidUpToLine = -1;

    synchronized (editor.textRender.linesWindow) {
      editor.textRender.linesWindow.clear();
      editor.textRender.linesWindow.add("");
    }
    synchronized (editor.textRender.modifiedLines) {
      editor.textRender.modifiedLines.clear();
    }
    synchronized (editor.textRender.lineWidthCache) {
      editor.textRender.lineWidthCache.clear();
    }
    editor.clearStreamedLineCaches();
    editor.clearHighlightCaches();
    editor.textRender.currentMaxWindowLineWidth = 0f;
    editor.textRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;

    editor.cursor.cursorLine = 0;
    editor.cursor.cursorChar = 0;
    isEof = true;
     editor.scroll.scrollY =0;
    editor.scroll.scrollX =0;

    editor.recalculateMaxLineWidth();
    editor.requestLayout();
    editor.invalidate();
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
    if (editor.binaryRender.binarySafeRenderingEnabled) {
      return editor.binaryRender.bytesToControlVisible(buf, buf.length);
    }
    return new String(buf, fileCharset);
  }

  // ========================================================================
  // File Helper Methods
  // ========================================================================

  /**
   * Get text snapshot of entire document
   */
  public String getTextSnapshot() {
    int total = editor.getLinesCount();
    if (total <= 0) return "";
    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      populateDirectLinesForRange(0, total - 1, direct);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < total; i++) {
      String line = editor.getLineTextForRenderWithDirect(i, direct);
      if (line == null) line = "";
      sb.append(line);
      if (i < total - 1) sb.append('\n');
    }
    return sb.toString();
  }

}
