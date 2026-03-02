package com.yn.sodiumeditor.io;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * IO manager for SodiumEditor.
 * Handles file operations, loading, and background IO tasks.
 */
public class EditorIOManager {

    private final HandlerThread ioThread;
    public final Handler ioHandler;
    public final Document document;
    public final LineIndex lineIndex;
    public final TextIO textIO;
    // Legacy reference for backward compatibility
    public final Document fileManager;

    public final AtomicInteger ioTaskVersion = new AtomicInteger(0);
    public File sourceFile = null;
    public boolean isFileCleared = false;
    public BufferedReader readerForFile = null;
    
    // Backward compatibility fields
    public final Object lineOffsetsLock;
    public Charset fileCharset = StandardCharsets.UTF_8;
    public boolean streamedSliceUpdatePending = false;

    public EditorIOManager(SodiumEditor view) {
        this.ioThread = new HandlerThread("SodiumEditorIO");
        this.ioThread.start();
        this.ioHandler = new Handler(ioThread.getLooper());
        this.document = new Document(view);
        this.lineIndex = new LineIndex(view);
        this.textIO = new TextIO(view);
        this.fileManager = document; // Legacy reference
        this.lineOffsetsLock = view.lineOffsetsLock;
    }

    public void cleanup() {
        if (ioThread != null) {
            ioThread.quitSafely();
        }
        if (readerForFile != null) {
            try {
                readerForFile.close();
            } catch (Exception ignored) {
            }
            readerForFile = null;
        }
    }

    public void setSourceFile(File file) {
        sourceFile = file;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void clearFile() {
        isFileCleared = true;
        sourceFile = null;
        if (readerForFile != null) {
            try {
                readerForFile.close();
            } catch (Exception ignored) {
            }
            readerForFile = null;
        }
    }

    public int getIoTaskVersion() {
        return ioTaskVersion.get();
    }

    public void incrementIoTaskVersion() {
        ioTaskVersion.incrementAndGet();
    }

    public boolean isFileCleared() {
        return isFileCleared;
    }

    // Backward compatibility methods for fileManager
    public boolean isIndexReady() {
        return document.isIndexReady();
    }

    public long[] getLineOffsets() {
        return document.getLineOffsets();
    }

    public boolean shouldStreamLineLength(int length) {
        return textIO.shouldStreamLineLength(length);
    }

    public boolean isSingleByteCharset() {
        return textIO.isSingleByteCharset();
    }

    public int getStreamedLineLength(int globalLine) {
        return textIO.getStreamedLineLength(globalLine);
    }

    public int getStreamedLineSliceStart(int globalLine) {
        return textIO.getStreamedLineSliceStart(globalLine);
    }

    public String readLineSliceAtByte(
        RandomAccessFile raf, long lineStart, long lineByteLen, int sliceStart, int sliceEnd) throws Exception {
        return document.readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
    }

    public SodiumEditor.StreamedCharSlice readLineSliceByChars(
        RandomAccessFile raf, long lineStart, int sliceStart, int sliceEnd, boolean trim) throws Exception {
        return document.readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, trim);
    }

    public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
        textIO.setStreamedLineInfo(globalLine, length, sliceStart);
    }

    public void updateSourceFile(File file) {
        sourceFile = file;
    }

    public void setFileCleared(boolean cleared) {
        isFileCleared = cleared;
    }

    public void setEof(boolean eof) {
        document.isEof = eof;
    }

    public String readLineUtf8AtByte(RandomAccessFile raf, long offset) throws Exception {
        return textIO.readLineUtf8AtByte(raf, offset);
    }

    public void rewriteReplaceRangeAsync(
        int opToken, File inFile, int sL, int sC, int eL, int eC, String text, 
        SodiumEditor.CursorTarget target, boolean finishLargeEdit) throws Exception {
        textIO.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, text, target, finishLargeEdit);
    }

    public boolean isEof() {
        return document.isEof;
    }

    public void setLineOffsets(long[] offsets) {
        document.setLineOffsets(offsets);
    }

    // Backward compatibility fields (direct access for legacy code)
    public boolean isIndexReady;
    public boolean isIndexBuilding;
    public boolean isIndexDisabled;
    public String indexDisabledPath;
    public long indexDisabledFileLength;

    public void syncIndexFieldsToView() {
        // Sync from document to view
        isIndexReady = document.isIndexReady;
        isIndexBuilding = document.isIndexBuilding;
        isIndexDisabled = document.isIndexDisabled;
        indexDisabledPath = document.indexDisabledPath;
        indexDisabledFileLength = document.indexDisabledFileLength;
    }

    public BufferedReader getReaderForFile() {
        return readerForFile;
    }

    public void setReaderForFile(BufferedReader reader) {
        readerForFile = reader;
    }

    public Document getDocument() {
        return document;
    }

    public LineIndex getLineIndex() {
        return lineIndex;
    }

    public TextIO getTextIO() {
        return textIO;
    }

    public Handler getIoHandler() {
        return ioHandler;
    }

    public HandlerThread getIoThread() {
        return ioThread;
    }
}
