package com.yn.sodiumeditor.io;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles line caching for fast random rendering during scrolling.
 */
public class FileCache {
    private final SodiumEditor editor;
    private final FileIO fileIO;

    public FileCache(SodiumEditor editor, FileIO fileIO) {
        FunctionLog.f("FileCache", "FileCache", editor, fileIO);
        this.editor = editor;
        this.fileIO = fileIO;
    }

    public void populateDirectLinesForRange(int startLine, int endLineInclusive, Map<Integer, String> out) {
        FunctionLog.f("FileCache", "populateDirectLinesForRange", startLine, endLineInclusive, out);
        if (out == null || fileIO.sourceFile == null || !fileIO.sourceFile.exists()) return;
        // Avoid reading from disk while in-memory edits are pending (phantom render risk).
        if (editor.editOperators.lineCountDelta != 0) return;
        synchronized (editor.windowRender.modifiedLines) {
            if (!editor.windowRender.modifiedLines.isEmpty()) return;
        }

        int start = Math.max(0, startLine);
        int end = Math.max(start, endLineInclusive);

        synchronized (fileIO.directLineCache) {
            for (int l = start; l <= end; l++) {
                String c = fileIO.directLineCache.get(l);
                if (c != null) out.put(l, c);
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
            long fileLen = raf.length();
            for (int cur = start; cur <= end; cur++) {
                if (out.containsKey(cur)) continue;
                long lineStart;
                synchronized (fileIO.lineOffsetsLock) {
                    if (cur >= fileIO.lineOffsets.length) break;
                    lineStart = fileIO.lineOffsets[cur];
                }
                long lineByteLen = fileIO.indexer.getLineByteLengthFromIndex(raf, cur, fileLen);
                int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                String ln;
                if (editor.windowRender.shouldStreamLineLength(lineLen)) {
                    editor.textRender.computeStreamedSliceBounds(null, cur, lineLen, editor.windowRender.streamedSliceTmp);
                    int sS = editor.windowRender.streamedSliceTmp[0];
                    int sE = editor.windowRender.streamedSliceTmp[1];
                    if (editor.windowRender.isSingleByteCharset()) {
                        ln = fileIO.readLineSliceAtByte(raf, lineStart, lineByteLen, sS, sE);
                        editor.windowRender.setStreamedLineInfo(cur, lineLen, sS);
                    } else {
                        StreamedCharSlice slice = editor.fileIO.readLineSliceByChars(raf, lineStart, sS, sE, true);
                        ln = slice.text; editor.windowRender.setStreamedLineInfo(cur, slice.length, sS);
                    }
                } else {
                    ln = fileIO.readLineUtf8AtByte(raf, lineStart);
                }
                out.put(cur, ln == null ? "" : ln);
            }
        } catch (Exception ignored) {}

        synchronized (fileIO.directLineCache) {
            for (Map.Entry<Integer, String> e : out.entrySet()) {
                if (e.getKey() >= start && e.getKey() <= end) fileIO.directLineCache.put(e.getKey(), e.getValue());
            }
        }
    }

    public String readLineByScanningFile(int targetLine) {
        if (fileIO.sourceFile == null || targetLine < 0) return null;
        try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
            raf.seek(0);
            int currentLine = 0;
            StringBuilder sb = new StringBuilder(256);
            byte[] buffer = new byte[4096];
            int n;
            while ((n = raf.read(buffer)) != -1) {
                for (int i = 0; i < n; i++) {
                    if (buffer[i] == '\n') {
                        if (currentLine == targetLine) return sb.toString();
                        currentLine++; sb.setLength(0);
                    } else if (buffer[i] != '\r') sb.append((char) buffer[i]);
                }
                if (currentLine > targetLine) break;
            }
            return (currentLine == targetLine) ? sb.toString() : null;
        } catch (Exception e) { return null; }
    }
}
