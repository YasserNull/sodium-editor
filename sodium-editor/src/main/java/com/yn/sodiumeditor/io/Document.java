package com.yn.sodiumeditor.io;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * إدارة محتوى النص والترميز والتحميل
 */
public class Document {
    private final SodiumEditor view;

    // Proxy fields for backward compatibility (point to SodiumEditor fields)
    public final Object lineOffsetsLock;
    public volatile boolean streamedSliceUpdatePending = false;
    
    // Index state proxy fields
    public boolean isIndexReady;
    public boolean isIndexBuilding;
    public boolean isIndexDisabled;
    public String indexDisabledPath;
    public long indexDisabledFileLength;

    // الترميز
    public Charset fileCharset = StandardCharsets.UTF_8;

    // حالة التحميل
    private boolean showLoadingOnFileOpen = true;
    private boolean isInitialFileOpenLoading = false;
    private int initialFileOpenToken = 0;
    private Runnable initialFileOpenShowSpinner = null;

    // حالة الملف
    public volatile boolean isEof = false;
    private boolean isFileOpened = false;

    public Document(SodiumEditor view) {
        this.view = view;
        this.lineOffsetsLock = view.lineOffsetsLock;
        // Initialize proxy fields from view
        syncIndexFieldsFromView();
    }

    // Sync proxy fields with view fields
    public void syncIndexFieldsFromView() {
        isIndexReady = view.isIndexReady;
        isIndexBuilding = view.isIndexBuilding;
        isIndexDisabled = view.isIndexDisabled;
        indexDisabledPath = view.indexDisabledPath;
        indexDisabledFileLength = view.indexDisabledFileLength;
    }

    public void syncIndexFieldsToView() {
        view.isIndexReady = isIndexReady;
        view.isIndexBuilding = isIndexBuilding;
        view.isIndexDisabled = isIndexDisabled;
        view.indexDisabledPath = indexDisabledPath;
        view.indexDisabledFileLength = indexDisabledFileLength;
    }

    // =========================================================================
    // Proxy methods for backward compatibility
    // =========================================================================

    public boolean isIndexReady() {
        syncIndexFieldsFromView();
        return isIndexReady;
    }

    public boolean isIndexBuilding() {
        syncIndexFieldsFromView();
        return isIndexBuilding;
    }

    public boolean isIndexDisabled() {
        syncIndexFieldsFromView();
        return isIndexDisabled;
    }

    public void setIndexDisabled(boolean disabled) {
        isIndexDisabled = disabled;
        syncIndexFieldsToView();
    }

    public long[] getLineOffsets() {
        synchronized (lineOffsetsLock) {
            return view.lineOffsets;
        }
    }

    public void setLineOffsets(long[] offsets) {
        synchronized (lineOffsetsLock) {
            view.lineOffsets = offsets;
        }
    }

    public boolean shouldStreamLineLength(int length) {
        return view.textIO.shouldStreamLineLength(length);
    }

    public int getStreamedLineLength(int globalLine) {
        return view.textIO.getStreamedLineLength(globalLine);
    }

    public int getStreamedLineSliceStart(int globalLine) {
        return view.textIO.getStreamedLineSliceStart(globalLine);
    }

    public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
        view.textIO.setStreamedLineInfo(globalLine, length, sliceStart);
    }

    public void clearStreamedLineInfo(int globalLine) {
        view.textIO.clearStreamedLineInfo(globalLine);
    }

    public void clearStreamedLineCaches() {
        view.textIO.clearStreamedLineCaches();
    }

    public int getLogicalLineLength(int globalLine, String line) {
        return view.textIO.getLogicalLineLength(globalLine, line);
    }

    public boolean isSingleByteCharsetInternal() {
        try {
            if (view.binarySafeRenderingEnabled) return true;
            return fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
        } catch (Exception ignored) {
            return true;
        }
    }

    // =========================================================================
    // الترميز (Encoding)
    // =========================================================================

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
        view.highlightState.resetEnsureRange();
        view.bracketGuideRenderer.invalidateCache();
        if (view.wrapWordState.isWordWrapEnabled) view.wrapWordBuilder.invalidate(true, true);
        view.wrapWordBuilder.requestPrefixRebuild(view);
        view.viewRender.reloadWindowAroundVisible(false);
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

    public boolean isSingleByteCharset() {
        return isSingleByteCharsetInternal();
    }

    // =========================================================================
    // Proxy methods for IO operations (delegate to TextIO)
    // =========================================================================

    public String readLineUtf8AtByte(java.io.RandomAccessFile raf, long byteOffset) throws Exception {
        return view.textIO.readLineUtf8AtByte(raf, byteOffset);
    }

    public String readLineSliceAtByte(
            java.io.RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
            throws Exception {
        return view.textIO.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar);
    }

    public com.yn.sodiumeditor.io.StreamedCharSlice readLineSliceByChars(
            java.io.RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
            throws Exception {
        return view.textIO.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength);
    }

    public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
        return view.textIO.computeByteOffsetInLineUtf8(lineText, charIndex);
    }

    public String readRangeText(int sL, int sC, int eL, int eC) {
        return view.textIO.readRangeText(sL, sC, eL, eC);
    }

    public String getTextSnapshot() {
        return view.textIO.getTextSnapshot();
    }

    public void rewriteReplaceRangeAsync(
        int opToken,
        File inFile,
        int sL,
        int sC,
        int eL,
        int eC,
        String insertText,
        com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target,
        boolean finishLargeEditUi) {
        view.textIO.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
    }

    public void onUndoRedoRewriteSuccess(File inFile) {
        view.textIO.onUndoRedoRewriteSuccess(inFile);
    }

    public void countTotalLines(LineIndex.LineCountCallback callback) {
        view.lineIndex.countTotalLines(callback);
    }

    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int targetLine) throws Exception {
        return view.lineIndex.findLineStartByteByScanning(raf, targetLine);
    }

    public long getLineByteLengthFromIndex(java.io.RandomAccessFile raf, int line, long fileLen)
            throws Exception {
        return view.lineIndex.getLineByteLengthFromIndex(raf, line, fileLen);
    }

    public String bytesToControlVisible(byte[] buf, int len) {
        return view.textIO.bytesToControlVisible(buf, len);
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

    // =========================================================================
    // حالة الملف (File State)
    // =========================================================================

    public void clearContent() {
        view.editorIO.invalidatePendingIOForEdit();
        view.sourceFile = null;
        view.isFileCleared = true;
        view.selectionState.setSelectAllState(false, false);
        
        // Force clear wrap metrics as content is being cleared
        view.wrapWordMetrics.wrapMetricsReady = false;
        view.wrapWordMetrics.wrapLineCounts = null;
        view.wrapWordMetrics.wrapLinePrefix = null;
        view.wrapWordMetrics.totalWrapVisualLines = 0;
        view.wrapWordMetrics.wrapPrefixValidUpToLine = -1;

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
        view.editorIO.textIO.clearStreamedLineCaches();
        view.highlightState.clearHighlightCaches();
        view.currentMaxWindowLineWidth = 0f;
        view.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;

        view.cursorState.setCursorPosition(0, 0);
        isEof = true;
        view.scrollManager.scrollY = 0;
        view.scrollManager.scrollX = 0;

        view.viewRender.textRender.recalculateMaxLineWidth();
        view.requestLayout();
        view.invalidate();
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

    public void updateSourceFile(File file) {
        view.sourceFile = file;
    }

    public boolean isFileOpened() {
        return isFileOpened;
    }

    public void setFileOpened(boolean opened) {
        isFileOpened = opened;
    }

    public boolean isEof() {
        return isEof;
    }

    public void setEof(boolean eof) {
        isEof = eof;
    }

    // =========================================================================
    // تحميل الملف (File Loading)
    // =========================================================================

    public void loadFromFile(final File file) {
        view.editorIO.invalidatePendingIOForEdit();
        view.isFileCleared = false;
        view.selectionState.setSelectAllState(false, false);
        view.lineNumberRenderer.invalidateCache();

        // Force clear wrap metrics for new file
        view.wrapWordMetrics.wrapMetricsReady = false;
        view.wrapWordMetrics.wrapLineCounts = null;
        view.wrapWordMetrics.wrapLinePrefix = null;
        view.wrapWordMetrics.totalWrapVisualLines = 0;
        view.wrapWordMetrics.wrapPrefixValidUpToLine = -1;

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
                        view.loadingCircleAnimator.show(true);
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
        view.editorIO.textIO.clearStreamedLineCaches();
        view.highlightState.clearHighlightCaches();
        view.currentMaxWindowLineWidth = 0f;
        view.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;
        
        synchronized (view.lineOffsetsLock) {
            view.lineOffsets = new long[0];
        }
        isIndexReady = false;
        isIndexBuilding = false;
        isIndexDisabled = false;
        indexDisabledPath = null;
        indexDisabledFileLength = -1L;
        syncIndexFieldsToView();

        view.cursorState.setCursorPosition(0, 0);
        isEof = false;
        view.scrollManager.scrollY = 0;
        view.scrollManager.scrollX = 0;
        view.history.resetLineCountDelta();

        view.viewRender.loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
        view.ioHandler.post(this::buildFileIndex);
        view.requestLayout();
        view.invalidate();
    }

    public void setShowLoadingOnFileOpen(boolean enabled) {
        showLoadingOnFileOpen = enabled;
    }

    private void finishInitialFileOpenWarmup(final int token) {
        if (!isInitialFileOpenLoading) return;
        if (token != initialFileOpenToken) return;
        if (view.getHeight() <= 0 || view.lineHeight <= 0f) {
            view.postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
            return;
        }

        int firstVisibleLine = Math.max(0, view.viewRender.textRender.getGlobalLineForY(view.scrollManager.scrollY));
        int viewHeight = view.getHeight() - view.keyboardHeight;
        if (viewHeight <= 0) viewHeight = view.getHeight();
        int visibleLines = Math.max(1, (int) Math.ceil(viewHeight / view.lineHeight) + 2);
        int lastVisibleLine = firstVisibleLine + visibleLines;

        view.highlightRenderer.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, null);
        isInitialFileOpenLoading = false;
        if (initialFileOpenShowSpinner != null) {
            view.mainHandler.removeCallbacks(initialFileOpenShowSpinner);
            initialFileOpenShowSpinner = null;
        }
        view.setDisable(false);
        view.loadingCircleAnimator.show(false);
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
            syncIndexFieldsToView();
            return;
        }
        syncIndexFieldsFromView();
        if (isIndexDisabled) {
            String path = view.sourceFile.getAbsolutePath();
            long len = view.sourceFile.length();
            if (path.equals(indexDisabledPath) && len == indexDisabledFileLength) {
                isIndexReady = false;
                isIndexBuilding = false;
                syncIndexFieldsToView();
                return;
            }
            isIndexDisabled = false;
            indexDisabledPath = null;
            indexDisabledFileLength = -1L;
            syncIndexFieldsToView();
        }
        isIndexBuilding = true;
        syncIndexFieldsToView();
        final int taskVersion = view.ioTaskVersion.get();
        view.ioHandler.post(
                () -> {
                    long[] offsets = buildIndexJava(view.sourceFile.getAbsolutePath());
                    if (taskVersion != view.ioTaskVersion.get()) {
                        isIndexBuilding = false;
                        syncIndexFieldsToView();
                        return;
                    }
                    if (offsets != null) {
                        synchronized (view.lineOffsetsLock) {
                            if (taskVersion == view.ioTaskVersion.get()) {
                                view.lineOffsets = offsets;
                                isIndexReady = true;
                                syncIndexFieldsToView();
                                view.post(view::requestLayout);
                                if (view.wrapWordState.isWordWrapEnabled) view.post(() -> view.wrapWordBuilder.scheduleBuild(view));
                            }
                        }
                    } else {
                        synchronized (view.lineOffsetsLock) {
                            isIndexReady = false;
                            syncIndexFieldsToView();
                        }
                    }
                    isIndexBuilding = false;
                    syncIndexFieldsToView();
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
}
