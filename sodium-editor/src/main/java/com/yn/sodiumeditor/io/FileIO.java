package com.yn.sodiumeditor.io;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main facade for File I/O operations in SodiumEditor.
 */
public class FileIO {
    private static final String LOG_TAG = "SodiumEditor";
    private static final int SAVE_LOG_MAX_CHARS = 64 * 1024;
    private final SodiumEditor editor;

    // Sub-components
    public final FileMetadata metadata;
    public final FileIndexer indexer;
    public final FileCache cache;
    public final FileWindowLoader windowLoader;

    // Infrastructure
    public final HandlerThread ioThread;
    public final Handler ioHandler;
    public final AtomicInteger ioTaskVersion = new AtomicInteger(0);

    // --- Core State Fields (Kept as fields for compatibility with other classes) ---
    public File sourceFile = null;
    public boolean isEof = false;
    public boolean isFileCleared = false;
    
    public volatile boolean isWindowLoading = false;
    private volatile boolean initialWindowWarmupDone = false;
    private volatile boolean initialBracketWarmupDone = true;

    // readLineSliceByChars already exists below (kept for compatibility).
    public volatile boolean isIndexReady = false;
    public volatile boolean isIndexBuilding = false;
    public long[] lineOffsets = new long[0];
    public final Object lineOffsetsLock = new Object();
    public java.nio.charset.Charset fileCharset = java.nio.charset.StandardCharsets.UTF_8;

    // Binary file detection
    public boolean autoDetectBinaryFiles = true;
    public int binaryDetectionSampleSize = 8192;
    public double binaryDetectionThreshold = 0.3;

    // Direct read cache
    public final java.util.LinkedHashMap<Integer, String> directLineCache =
        new java.util.LinkedHashMap<Integer, String>(250, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(java.util.Map.Entry<Integer, String> eldest) {
            int maxCacheSize = false ? 500 : 250;
            if (size() <= maxCacheSize) return false;
            int firstIdx = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
            int lastIdx = firstIdx + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5;
            for (int v = firstIdx; v <= lastIdx; v++) {
                if (eldest.getKey().equals(v)) return false;
            }
            return true;
          }
        };

    // Track when we need to clear modifiedLines after a file rewrite + window reload
    public volatile boolean clearModifiedLinesAfterRewrite = false;
    
    // Indexing disabled tracking (moved from component to here for central access)
    public volatile boolean isIndexDisabled = false;
    public volatile String indexDisabledPath = null;
    public volatile long indexDisabledFileLength = -1L;

    public FileIO(SodiumEditor editor) {
        this.editor = editor;
        this.ioThread = new HandlerThread("SodiumEditor-IO");
        this.ioThread.start();
        this.ioHandler = new Handler(ioThread.getLooper());

        this.metadata = new FileMetadata(editor, this);
        this.indexer = new FileIndexer(editor, this);
        this.cache = new FileCache(editor, this);
        this.windowLoader = new FileWindowLoader(editor, this);
    }

    // Compatibility methods for callers who might use them
    public boolean isWindowLoading() { return isWindowLoading; }
    public boolean isIndexReady() { return isIndexReady; }
    public long[] getLineOffsets() { return lineOffsets; }
    public Object getLineOffsetsLock() { return lineOffsetsLock; }
    public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen) throws Exception {
        return indexer.getLineByteLengthFromIndex(raf, line, fileLen);
    }

    // ==============================
    // Public Operations
    // ==============================

    public void loadFromFile(File file) {
        invalidatePendingIOForEdit();
        isFileCleared = false;
        sourceFile = file;
        editor.selection.clearSelection();
        editor.autoBracketPair.clearBalanceCache();
        editor.lineNumber.invalidateLineNumberCache();
        editor.editOperators.clearUndoRedoHistory();
        editor.autoCompletion.clearActiveSuggestion();
        isIndexReady = false;
        isIndexBuilding = false;
        
        // Manual reset of wrap metrics
        editor.wordWrap.wrapMetricsReady = false;
        editor.wordWrap.wrapLineCounts = null;
        editor.wordWrap.wrapLinePrefix = null;
        editor.wordWrap.totalWrapVisualLines = 0;
        editor.wordWrap.wrapPrefixValidUpToLine = -1;
        final boolean binaryFile = metadata.isBinaryFile(file);
        editor.binaryRender.applyBinaryFileFeaturePolicy(binaryFile);

        final int token = ++editor.loadingCircle.initialFileOpenToken;
        final boolean needsBracketWarmup = shouldWarmBracketIndexForOpen();
        editor.loadingCircle.isInitialFileOpenLoading = true;
        if (editor.loadingCircle.showLoadingOnFileOpen) {
            editor.view.setDisable(true);
            editor.loadingCircle.showLoadingCircle(true);
        }
        
        resetStateForNewFile();
        initialWindowWarmupDone = false;
        initialBracketWarmupDone = !needsBracketWarmup;
        loadWindowAround(0, () -> markInitialWindowWarmupDone(token), false);
        
        ioHandler.post(() -> {
            indexer.buildFileIndex();
            checkHeavyFeatures();
            if (needsBracketWarmup) {
                editor.bracketCache.scanFileAsync(() -> markInitialBracketWarmupDone(token));
            } else {
                editor.post(() -> markInitialBracketWarmupDone(token));
            }
        });
        editor.requestLayout(); editor.invalidate();
    }

    public void clearContent() {
        invalidatePendingIOForEdit();
        sourceFile = null; isFileCleared = true;
        editor.binaryRender.applyBinaryFileFeaturePolicy(false);
        editor.selection.clearSelection();
        editor.autoBracketPair.clearBalanceCache();
        isIndexReady = false;
        
        // Manual reset of wrap metrics
        editor.wordWrap.wrapMetricsReady = false;
        editor.wordWrap.wrapLineCounts = null;
        editor.wordWrap.wrapLinePrefix = null;
        editor.wordWrap.totalWrapVisualLines = 0;
        editor.wordWrap.wrapPrefixValidUpToLine = -1;

        synchronized (editor.windowRender.linesWindow) { editor.windowRender.linesWindow.clear(); editor.windowRender.linesWindow.add(""); }
        editor.windowRender.clearModifiedLines();
        editor.windowRender.clearStreamedLineCaches();
        editor.scroll.scrollY = 0; editor.scroll.scrollX = 0;
        editor.windowRender.recalculateMaxLineWidth(); editor.requestLayout(); editor.invalidate();
    }

    // ==============================
    // Bridge Methods
    // ==============================

    public void checkAndLoadWindow() { windowLoader.checkAndLoadWindow(); }
    public void loadWindowAround(int sL, Runnable cb, boolean sync) { 
        windowLoader.loadWindowAround(sL, cb, sync); 
    }
    public void loadTailWindowForSelectAll(int lastLine, Runnable cb) {
        windowLoader.loadTailWindowForSelectAll(lastLine, cb);
    }
    public void buildFileIndex() { 
        indexer.buildFileIndex(); 
    }
    public void populateDirectLinesForRange(int s, int e, Map<Integer, String> out) { 
        cache.populateDirectLinesForRange(s, e, out); 
    }
    public String readRangeText(int sL, int sC, int eL, int eC) { 
        return metadata.readRangeText(sourceFile, sL, sC, eL, eC); 
    }
    public void ensureLineInWindow(int gL, boolean block) { windowLoader.loadWindowAround(Math.max(0, gL - editor.windowRender.prefetchLines), null, false); }
    public void invalidatePendingIO() { 
        ioTaskVersion.incrementAndGet(); ioHandler.removeCallbacksAndMessages(null); 
    }
    public void invalidatePendingIOForEdit() { 
        invalidatePendingIO(); editor.highlite.clearHighlightCaches(); 
    }
    public void invalidatePendingIOVersionForEdit() {
        ioTaskVersion.incrementAndGet();
        editor.highlite.clearHighlightCaches();
    }

    public void cancelAndCloseReader() {
        // Kept for release() compatibility. File-backed readers are now short-lived RandomAccessFile instances.
    }

    public void countTotalLines(LineCountCallback callback) {
        final int taskVersion = ioTaskVersion.get();
        ioHandler.post(() -> {
            if (taskVersion != ioTaskVersion.get()) { editor.post(() -> callback.onResult(-1)); return; }
            if (isIndexReady && sourceFile != null) {
                synchronized (lineOffsetsLock) { editor.post(() -> callback.onResult(lineOffsets.length)); }
                return;
            }
            int count = 0;
            if (sourceFile != null && sourceFile.exists()) {
                try (FileInputStream is = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[8192]; int len; boolean empty = true;
                    while ((len = is.read(buffer)) != -1) {
                        empty = false;
                        for (int i = 0; i < len; i++) if (buffer[i] == '\n') count++;
                    }
                    if (!empty) count++;
                } catch (Exception e) { count = -1; }
            }
            final int finalCount = count;
            editor.post(() -> callback.onResult(finalCount));
        });
    }

    public interface LineCountCallback { void onResult(int count); }

    // ==============================
    // Low-level IO (Used by components)
    // ==============================

    public String readLineUtf8AtByte(RandomAccessFile raf, long offset) throws Exception {
        raf.seek(offset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        byte[] buf = new byte[1024];
        while (true) {
            int n = raf.read(buf);
            if (n <= 0) break;
            int stop = -1;
            for (int i = 0; i < n; i++) if (buf[i] == '\n') { stop = i; break; }
            if (stop >= 0) {
                if (stop > 0 && buf[stop - 1] == '\r') baos.write(buf, 0, stop - 1);
                else baos.write(buf, 0, stop);
                break;
            }
            baos.write(buf, 0, n);
            if (baos.size() > 2_000_000) break;
        }
        byte[] data = baos.toByteArray();
        return editor.binaryRender.isBinarySafeRenderingEnabled() ? editor.binaryRender.bytesToControlVisible(data, data.length, fileCharset) : new String(data, fileCharset);
    }

    public String readLinePrefixUtf8AtByte(RandomAccessFile raf, long offset, int maxBytes) throws Exception {
        int limit = Math.max(0, maxBytes);
        if (limit == 0) return "";
        raf.seek(offset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(4096, limit));
        byte[] buf = new byte[Math.min(8192, limit)];
        int remaining = limit;
        while (remaining > 0) {
            int n = raf.read(buf, 0, Math.min(buf.length, remaining));
            if (n <= 0) break;
            int stop = -1;
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    stop = i;
                    break;
                }
            }
            if (stop >= 0) {
                int count = stop;
                if (count > 0 && buf[count - 1] == '\r') count--;
                if (count > 0) baos.write(buf, 0, count);
                break;
            }
            baos.write(buf, 0, n);
            remaining -= n;
        }
        byte[] data = baos.toByteArray();
        return editor.binaryRender.isBinarySafeRenderingEnabled()
                ? editor.binaryRender.bytesToControlVisible(data, data.length, fileCharset)
                : new String(data, fileCharset);
    }

    public String readLineSliceAtByte(RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar) throws Exception {
        int s = Math.max(0, Math.min(startChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
        int e = Math.max(s, Math.min(endChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
        int len = e - s; if (len <= 0) return "";
        raf.seek(lineStart + s);
        byte[] buf = new byte[len]; raf.readFully(buf);
        return editor.binaryRender.isBinarySafeRenderingEnabled() ? editor.binaryRender.bytesToControlVisible(buf, buf.length, fileCharset) : new String(buf, fileCharset);
    }

    public StreamedCharSlice readLineSliceByChars(RandomAccessFile raf, long lineStart, int sC, int eC, boolean needTotal) throws Exception {
        return editor.binaryRender.readLineSliceByChars(raf, lineStart, sC, eC, needTotal, fileCharset);
    }

    // ==============================
    // Private Helpers
    // ==============================

    private void resetStateForNewFile() {
        editor.windowRender.windowStartLine = 0;
        synchronized (editor.windowRender.linesWindow) { editor.windowRender.linesWindow.clear(); }
        editor.windowRender.clearModifiedLines();
        synchronized (editor.windowRender.lineWidthCache) { editor.windowRender.lineWidthCache.clear(); }
        synchronized (editor.windowRender.avgCharWidthCache) { editor.windowRender.avgCharWidthCache.clear(); }
        synchronized (directLineCache) { directLineCache.clear(); }
        editor.bracketCache.clear();
        
        editor.windowRender.currentMaxWindowLineWidth = 0f;
        editor.windowRender.globalMaxLineWidth = 0f;
        editor.scroll.maxLineWidthForScroll = 0f;
        
        editor.windowRender.clearStreamedLineCaches();
        editor.cursor.setCursorPosition(0, 0);
        editor.scroll.scrollY = 0; editor.scroll.scrollX = 0;
    }

    private void checkHeavyFeatures() {
        int total; synchronized (lineOffsetsLock) { total = lineOffsets.length; }
        if (total > editor.view.heavyFeaturesThreshold) {
            editor.indentGuides.setIndentGuidesEnabled(false);
        }
    }

    private boolean shouldWarmBracketIndexForOpen() {
        return false
                || editor.bracketGuides.isBracketGuidesEnabled
                || editor.bracketMatchManager.isBracketMatchingEnabled;
    }

    private void markInitialWindowWarmupDone(final int token) {
        initialWindowWarmupDone = true;
        finishInitialFileOpenWarmupIfReady(token);
    }

    private void markInitialBracketWarmupDone(final int token) {
        initialBracketWarmupDone = true;
        finishInitialFileOpenWarmupIfReady(token);
    }

    private void finishInitialFileOpenWarmupIfReady(final int token) {
        if (!editor.loadingCircle.isInitialFileOpenLoading || token != editor.loadingCircle.initialFileOpenToken) return;
        if (!initialWindowWarmupDone || !initialBracketWarmupDone) return;
        editor.loadingCircle.isInitialFileOpenLoading = false;
        editor.view.setDisable(false); editor.loadingCircle.showLoadingCircle(false);
        editor.invalidate();
    }

    public void recalculateMaxLineWidthAsync() {
        final int token = ++editor.loadingCircle.maxWidthRecalcToken;
        final int start; final ArrayList<String> snapshot;
        synchronized (editor.windowRender.linesWindow) {
            start = editor.windowRender.windowStartLine;
            snapshot = new ArrayList<>(editor.windowRender.linesWindow);
        }
        if (snapshot.isEmpty()) return;
        editor.post(new Runnable() {
            int idx = 0; float currentMax = 0f;
            @Override public void run() {
                if (token != editor.loadingCircle.maxWidthRecalcToken) return;
                int end = Math.min(snapshot.size(), idx + 120);
                for (int i = idx; i < end; i++) {
                    float w = editor.view.getWidthForLine(start + i, snapshot.get(i));
                    if (w > currentMax) currentMax = w;
                }
                idx = end;
                if (idx < snapshot.size()) {
                    editor.post(this);
                } else {
                    editor.windowRender.currentMaxWindowLineWidth = currentMax;
                    editor.windowRender.globalMaxLineWidth = currentMax;
                    editor.scroll.clampScrollX();
                    editor.invalidate();
                }
            }
        });
    }

    public String getTextSnapshot() {
        int total = editor.view.getLinesCount();
        if (total <= 0) return "";
        java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
        if (isIndexReady && sourceFile != null) populateDirectLinesForRange(0, total - 1, direct);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            String ln = editor.windowRender.getLineTextForRenderWithDirect(i, direct);
            sb.append(ln == null ? "" : ln);
            if (i < total - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public String readSavedFileContentForLog() {
        if (!SodiumEditor.DEBUG_LOGS || sourceFile == null || !sourceFile.exists()) return "";
        try (FileInputStream is = new FileInputStream(sourceFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(SAVE_LOG_MAX_CHARS, (int) Math.min(Integer.MAX_VALUE, sourceFile.length())))) {
            byte[] buffer = new byte[8192];
            int remaining = SAVE_LOG_MAX_CHARS;
            int read;
            while (remaining > 0 && (read = is.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, read);
                remaining -= read;
            }
            String text = editor.binaryRender.isBinarySafeRenderingEnabled()
                    ? editor.binaryRender.bytesToControlVisible(out.toByteArray(), out.size(), fileCharset)
                    : new String(out.toByteArray(), fileCharset);
            if (sourceFile.length() > out.size()) {
                text += "\n...[truncated save log at " + SAVE_LOG_MAX_CHARS + " bytes]";
            }
            return text;
        } catch (Exception e) {
            return "<error reading saved file for log: " + e.getClass().getSimpleName() + ">";
        }
    }

    private String getRenderedTextForSaveLog() {
        int total = editor.view.getLinesCount();
        if (total <= 0) return "";
        java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
        int maxLines = Math.min(total, 2000);
        if (isIndexReady && sourceFile != null) populateDirectLinesForRange(0, maxLines - 1, direct);
        StringBuilder sb = new StringBuilder(Math.min(SAVE_LOG_MAX_CHARS, 4096));
        for (int i = 0; i < maxLines && sb.length() < SAVE_LOG_MAX_CHARS; i++) {
            String ln = editor.windowRender.getLineTextForRenderWithDirect(i, direct);
            if (ln != null) {
                int remaining = SAVE_LOG_MAX_CHARS - sb.length();
                sb.append(ln, 0, Math.min(ln.length(), Math.max(0, remaining)));
            }
            if (i < total - 1 && sb.length() < SAVE_LOG_MAX_CHARS) sb.append('\n');
        }
        if (total > maxLines || sb.length() >= SAVE_LOG_MAX_CHARS) {
            sb.append("\n...[truncated rendered save log at ")
                    .append(SAVE_LOG_MAX_CHARS)
                    .append(" chars]");
        }
        return sb.toString();
    }

    private String printableForSaveLog(@Nullable String text) {
        if (text == null) return "<null>";
        if (text.length() <= SAVE_LOG_MAX_CHARS) return text;
        return text.substring(0, SAVE_LOG_MAX_CHARS)
                + "\n...[truncated save log at " + SAVE_LOG_MAX_CHARS + " chars]";
    }
}
