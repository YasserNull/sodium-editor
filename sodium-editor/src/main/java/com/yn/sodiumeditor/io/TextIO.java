package com.yn.sodiumeditor.io;

import android.util.SparseIntArray;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import com.yn.sodiumeditor.SodiumEditorView;

/**
 * القراءة والكتابة والتعامل مع الأسطر الطويلة
 */
public class TextIO {
    private final SodiumEditorView view;

    // Streamed lines cache
    private final SparseIntArray streamedLineLengths = new SparseIntArray();
    private final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
    private boolean streamedSliceUpdatePending = false;
    private int streamedSliceUpdateToken = 0;
    private final int[] streamedSliceTmp = new int[2];

    // Control characters mapping
    private static final String[] CONTROL_TOKENS =
            new String[] {
                    "<NUL>", "<SOH>", "<STX>", "<ETX>", "<EOT>", "<ENQ>", "<ACK>", "<BEL>",
                    "<BS>", "<TAB>", "<LF>", "<VT>", "<FF>", "<CR>", "<SO>", "<SI>",
                    "<DLE>", "<DC1>", "<DC2>", "<DC3>", "<DC4>", "<NAK>", "<SYN>", "<ETB>",
                    "<CAN>", "<EM>", "<SUB>", "<ESC>", "<FS>", "<GS>", "<RS>", "<US>"
            };

    public TextIO(SodiumEditorView view) {
        this.view = view;
    }

    // =========================================================================
    // قراءة الأسطر (Line Reading)
    // =========================================================================

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
        return baos.toString(view.fileManager.fileCharset.name());
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
        return new String(buf, view.fileManager.fileCharset);
    }

    public SodiumEditorView.StreamedCharSlice readLineSliceByChars(
            RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
            throws Exception {
        int safeStart = Math.max(0, startChar);
        int safeEnd = Math.max(safeStart, endChar);
        CharsetDecoder decoder = view.fileManager.fileCharset.newDecoder();
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

    // =========================================================================
    // قراءة النصوص (Text Reading)
    // =========================================================================

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
        LineIndex.RangeBytes range = view.lineIndex.computeByteRangeFastOrScan(view.sourceFile, startL, startC, endL, endC);
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
            return new String(buf, view.fileManager.fileCharset);
        } catch (Exception ignore) {
            return "";
        }
    }

    public String getTextSnapshot() {
        int total = view.getLinesCount();
        if (total <= 0) return "";
        HashMap<Integer, String> direct = new HashMap<>();
        if (view.lineIndex.isIndexReady() && view.sourceFile != null && view.sourceFile.exists()) {
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

    // =========================================================================
    // الكتابة (Writing)
    // =========================================================================

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

                    LineIndex.RangeBytes range = view.lineIndex.computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
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
                            if (opToken != view.history.getEditVersion()) return;

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
                                view.fileManager.updateSourceFile(inFile);
                            } else {
                                view.fileManager.updateSourceFile(outFile);
                            }
                            view.fileManager.setFileCleared(false);

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
                            view.history.resetLineCountDelta();

                            synchronized (view.lineOffsetsLock) {
                                view.lineOffsets = new long[0];
                            }
                            view.isIndexReady = false;
                            view.isIndexBuilding = false;
                            view.isIndexDisabled = false;
                            view.indexDisabledPath = null;
                            view.indexDisabledFileLength = -1L;
                            view.fileManager.setEof(false);

                            view.ioHandler.post(view.lineIndex::buildFileIndex);
                            view.wrapWordBuilder.onLineCountChanged(view);

                            view.cursorState.setCursorPosition(Math.max(0, target.line), Math.max(0, target.ch));

                            boolean cursorInsideWindow =
                                (view.cursorState.getCursorLine() >= view.windowStartLine
                                    && view.cursorState.getCursorLine() < view.windowStartLine + view.linesWindow.size());

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
                                int targetStart = Math.max(0, view.cursorState.getCursorLine() - view.prefetchLines);
                                view.loadWindowAround(
                                    targetStart,
                                    () -> {
                                        String ln = view.getLineTextForRender(view.cursorState.getCursorLine());
                                        view.cursorNavigation.clampCharToLineLength(view.cursorState.getCursorLine());
                                        view.scrollManager.clampScrollY();
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

    public void onUndoRedoRewriteSuccess(File inFile) {
        view.fileManager.updateSourceFile(inFile);
        synchronized (view.lineOffsetsLock) {
            view.lineOffsets = new long[0];
        }
        view.isIndexReady = false;
        view.isIndexBuilding = false;
        view.isIndexDisabled = false;
        view.indexDisabledPath = null;
        view.indexDisabledFileLength = -1L;
        view.ioHandler.post(view.lineIndex::buildFileIndex);
    }

    // =========================================================================
    // الأسطر الطويلة (Streamed Lines)
    // =========================================================================

    public void clearStreamedLineCaches() {
        synchronized (view.streamedLinesLock) {
            streamedLineLengths.clear();
            streamedLineSliceStarts.clear();
        }
        streamedSliceUpdatePending = false;
        streamedSliceUpdateToken++;
    }

    public int getStreamedLineLength(int globalLine) {
        synchronized (view.streamedLinesLock) {
            return streamedLineLengths.get(globalLine, -1);
        }
    }

    public int getStreamedLineSliceStart(int globalLine) {
        synchronized (view.streamedLinesLock) {
            return streamedLineSliceStarts.get(globalLine, 0);
        }
    }

    public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
        synchronized (view.streamedLinesLock) {
            streamedLineLengths.put(globalLine, length);
            streamedLineSliceStarts.put(globalLine, sliceStart);
        }
    }

    public void clearStreamedLineInfo(int globalLine) {
        synchronized (view.streamedLinesLock) {
            streamedLineLengths.delete(globalLine);
            streamedLineSliceStarts.delete(globalLine);
        }
    }

    public boolean shouldStreamLineLength(int length) {
        if (view.wrapWordState.isWordWrapEnabled) return false;
        return length > getStreamLineThreshold();
    }

    private int getStreamLineThreshold() {
        return Math.max(4096, view.highlightState.maxSyntaxLineLength);
    }

    public int getLogicalLineLength(int globalLine, String line) {
        String mod = view.modifiedLines.get(globalLine);
        if (mod != null) return mod.length();
        int len = (line == null) ? 0 : line.length();
        int longLen = getStreamedLineLength(globalLine);
        return (longLen > len) ? longLen : len;
    }

    // =========================================================================
    // Byte Offset & Control Characters
    // =========================================================================

    public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
        if (lineText == null) return 0L;
        int safe = Math.max(0, Math.min(charIndex, lineText.length()));
        if (safe == 0) return 0L;
        return lineText.substring(0, safe).getBytes(view.fileManager.fileCharset).length;
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
}
