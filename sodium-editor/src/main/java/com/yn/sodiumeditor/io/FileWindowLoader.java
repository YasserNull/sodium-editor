package com.yn.sodiumeditor.io;

import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles loading chunks of files into the in-memory window.
 */
public class FileWindowLoader {
    private final SodiumEditor editor;
    private final FileIO fileIO;

    public FileWindowLoader(SodiumEditor editor, FileIO fileIO) {
        this.editor = editor;
        this.fileIO = fileIO;
    }

    public void checkAndLoadWindow() {
        // While there are pending structural edits (line insert/delete), the in-memory window
        // does not match the on-disk file. Reloading from disk reintroduces deleted lines.
        if (editor.editOperators.lineCountDelta != 0) return;
        if (fileIO.sourceFile == null || fileIO.isFileCleared || editor.getWidth() == 0 || editor.getHeight() == 0 || fileIO.isWindowLoading) return;
        int firstIdx = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
        int lastIdx = firstIdx + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight);
        int fL, lL;
        if (editor.wordWrap.isWordWrapEnabled) {
            fL = editor.wordWrap.getVisualPositionForIndex(firstIdx).line;
            lL = editor.wordWrap.getVisualPositionForIndex(lastIdx).line;
        } else {
            fL = editor.codeFold.mapVisibleIndexToGlobal(firstIdx);
            lL = editor.codeFold.mapVisibleIndexToGlobal(lastIdx);
        }
        int loadTarget = fL;
        if (editor.codeFold.isCodeFoldingEnabled) {
            com.yn.sodiumeditor.core.fold.CodeFold.FoldRange fr = editor.codeFold.getFoldRangeAtStart(fL);
            if (fr != null && fr.collapsed && (fr.endLine - fr.startLine) > (editor.windowRender.windowSize / 2)) loadTarget = fr.endLine + 1;
        }
        int winEnd;
        synchronized (editor.windowRender.linesWindow) { winEnd = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1; }
        boolean top = editor.windowRender.windowStartLine > 0 && loadTarget < editor.windowRender.windowStartLine + editor.windowRender.prefetchLines;
        boolean bottom = !fileIO.isEof && lL > winEnd - editor.windowRender.prefetchLines;
        boolean out = loadTarget < editor.windowRender.windowStartLine || loadTarget > winEnd;
        if (top || bottom || out) loadWindowAround(Math.max(0, loadTarget - editor.windowRender.prefetchLines), null, false);
    }

    public void loadWindowAround(int startLine, @Nullable Runnable onComplete, boolean recalcWidthSync) {
        if (editor.editOperators.lineCountDelta != 0) { if (onComplete != null) editor.post(onComplete); return; }
        if (fileIO.isWindowLoading) return;
        editor.loadingCircle.maxWidthRecalcToken++;
        if (fileIO.isFileCleared || fileIO.sourceFile == null) { if (onComplete != null) editor.post(onComplete); return; }
        fileIO.isWindowLoading = true;
        final int taskVersion = fileIO.ioTaskVersion.incrementAndGet();
        fileIO.ioHandler.post(() -> {
            try {
                if (taskVersion != fileIO.ioTaskVersion.get()) { editor.post(() -> { fileIO.isWindowLoading = false; checkAndLoadWindow(); }); return; }
                int actualStart = Math.max(0, startLine);
                if (fileIO.isIndexReady()) {
                    synchronized (fileIO.getLineOffsetsLock()) {
                        long[] offsets = fileIO.getLineOffsets();
                        if (offsets.length > 0 && actualStart >= offsets.length) actualStart = Math.max(0, offsets.length - 1);
                    }
                }
                loadWindowInternal(actualStart, taskVersion, onComplete, recalcWidthSync);
            } catch (Exception e) { editor.post(() -> { fileIO.isWindowLoading = false; if (onComplete != null) onComplete.run(); }); }
        });
    }

    private void loadWindowInternal(int actualStart, int taskVersion, @Nullable Runnable onComplete, boolean recalcWidthSync) throws Exception {
        List<String> newWin = new ArrayList<>();
        SparseIntArray newLengths = new SparseIntArray();
        SparseIntArray newSliceStarts = new SparseIntArray();
        boolean endsWithNl = false, reachedEof = false, trailingEmpty = false;

        try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
            long fileLen = raf.length();
            if (fileLen > 0) { raf.seek(fileLen - 1); endsWithNl = (raf.read() == '\n'); }
            int limit = editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);
            int lineIdx = actualStart;

            if (fileIO.isIndexReady()) {
                while (newWin.size() < limit) {
                    synchronized (fileIO.getLineOffsetsLock()) { if (lineIdx >= fileIO.getLineOffsets().length) { reachedEof = true; break; } }
                    long start = fileIO.getLineOffsets()[lineIdx];
                    long bLen = fileIO.indexer.getLineByteLengthFromIndex(raf, lineIdx, fileLen);
                    int len = (int) Math.min(Integer.MAX_VALUE, bLen);
                    if (editor.windowRender.shouldStreamLineLength(len)) {
                        int sS = 0;
                        if (editor.windowRender.isSingleByteCharset()) {
                            int sE = Math.max(1, Math.min(len, editor.textRender.getInitialStreamedSliceSize()));
                            newWin.add(fileIO.readLineSliceAtByte(raf, start, bLen, sS, sE));
                        } else {
                            StreamedCharSlice slice = fileIO.readLineSliceByChars(raf, start, sS, Math.max(1, editor.textRender.getInitialStreamedSliceSize()), true);
                            newWin.add(slice.text); len = slice.length;
                        }
                        newLengths.put(lineIdx, len); newSliceStarts.put(lineIdx, sS);
                    } else { newWin.add(fileIO.readLineUtf8AtByte(raf, start)); }
                    lineIdx++;
                }
                if (endsWithNl) { synchronized (fileIO.getLineOffsetsLock()) { trailingEmpty = fileIO.getLineOffsets().length > 0 && fileIO.getLineOffsets()[fileIO.getLineOffsets().length - 1] == fileLen; } }
            } else {
                raf.seek(0); int skipped = 0;
                while (skipped < actualStart) { if (fileIO.metadata.scanLineLength(raf).reachedEof) break; skipped++; }
                actualStart = skipped; lineIdx = actualStart;
                while (newWin.size() < limit) {
                    long start = raf.getFilePointer();
                    if (start >= fileLen) { reachedEof = true; break; }
                    FileMetadata.LineScanResult scan = fileIO.metadata.scanLineLength(raf);
                    long after = raf.getFilePointer();
                    int len = (int) Math.min(Integer.MAX_VALUE, scan.length);
                    if (editor.windowRender.shouldStreamLineLength(len)) {
                        int sS = 0;
                        if (editor.windowRender.isSingleByteCharset()) {
                            int sE = Math.max(1, Math.min(len, editor.textRender.getInitialStreamedSliceSize()));
                            newWin.add(fileIO.readLineSliceAtByte(raf, start, scan.length, sS, sE));
                        } else {
                            StreamedCharSlice slice = fileIO.readLineSliceByChars(raf, start, sS, Math.max(1, editor.textRender.getInitialStreamedSliceSize()), true);
                            newWin.add(slice.text); len = slice.length;
                        }
                        newLengths.put(lineIdx, len); newSliceStarts.put(lineIdx, sS);
                    } else {
                        raf.seek(start); byte[] buf = new byte[len]; if (len > 0) raf.readFully(buf);
                        newWin.add(len > 0 ? (editor.binaryRender.isBinarySafeRenderingEnabled() ? editor.binaryRender.bytesToControlVisibleAndCacheSpans(buf, buf.length, lineIdx) : new String(buf, fileIO.fileCharset)) : "");
                    }
                    raf.seek(after); if (scan.reachedEof) { reachedEof = true; break; }
                    lineIdx++;
                }
            }
        }

        if (newWin.isEmpty()) { newWin.add(""); actualStart = 0; }
        if (reachedEof && endsWithNl && !trailingEmpty) newWin.add("");
        final boolean finalEof = newWin.size() < editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);
        final int fStart = actualStart;

        editor.post(() -> {
            fileIO.isWindowLoading = false;
            if (taskVersion != fileIO.ioTaskVersion.get()) { checkAndLoadWindow(); return; }
            synchronized (editor.windowRender.linesWindow) {
                editor.windowRender.linesWindow.clear(); editor.windowRender.linesWindow.addAll(newWin);
                editor.windowRender.windowStartLine = fStart; fileIO.isEof = finalEof;
                synchronized (editor.windowRender.modifiedLines) {
                    for (Map.Entry<Integer, String> entry : editor.windowRender.modifiedLines.entrySet()) {
                        int line = entry.getKey();
                        if (line >= fStart && line < fStart + newWin.size()) editor.windowRender.linesWindow.set(line - fStart, entry.getValue());
                    }
                }
            }
            applyStreamedInfo(newLengths, newSliceStarts);
            editor.lineNumber.invalidateLineNumberCache();
            editor.highlite.invalidateHighlightEnsureRange();
            if (recalcWidthSync) editor.windowRender.recalculateMaxLineWidth();
            else fileIO.recalculateMaxLineWidthAsync();
            if (editor.wordWrap.isWordWrapEnabled) {
                if (!editor.wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) {
                    if (editor.getWidth() > 0) editor.wordWrap.buildWrapMetricsForWindowSnapshot();
                    editor.wordWrap.requestWrapPrefixRebuild();
                }
            }
            editor.requestLayout();
            editor.invalidate();
            if (onComplete != null) onComplete.run();
        });
    }

    private void applyStreamedInfo(SparseIntArray lengths, SparseIntArray starts) {
        synchronized (editor.windowRender.streamedLinesLock) {
            editor.windowRender.streamedLineLengths.clear(); editor.windowRender.streamedLineSliceStarts.clear();
            for (int i = 0; i < lengths.size(); i++) {
                int key = lengths.keyAt(i);
                editor.windowRender.streamedLineLengths.put(key, lengths.valueAt(i));
                editor.windowRender.streamedLineSliceStarts.put(key, starts.get(key, 0));
            }
        }
        synchronized (editor.windowRender.streamedLinesLockLinesLock) {
            editor.windowRender.streamedLinesLockLineLengths.clear(); editor.windowRender.streamedLinesLockLineSliceStarts.clear();
            for (int i = 0; i < lengths.size(); i++) {
                int key = lengths.keyAt(i);
                editor.windowRender.streamedLinesLockLineLengths.put(key, lengths.valueAt(i));
                editor.windowRender.streamedLinesLockLineSliceStarts.put(key, starts.get(key, 0));
            }
        }
    }
}
