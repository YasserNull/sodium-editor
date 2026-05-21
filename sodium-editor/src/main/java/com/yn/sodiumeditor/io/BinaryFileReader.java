package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import com.yn.sodiumeditor.core.binary.BinaryTokenConverter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * BinaryFileReader handles reading binary files with safe conversion.
 * This includes:
 * - Reading lines with binary-safe conversion
 * - Reading byte slices
 * - Reading character slices with streaming
 * - Thread-local resource pooling
 */
public class BinaryFileReader {

    private final SodiumEditor editor;
    private final BinaryTokenConverter tokenConverter;

    // ── Thread-local pool: ONE object per thread, reused every call ────────────
    private static final int  READ_BUF_SIZE = 64 * 1024;   // 64 KB read window
    private static final int  CHAR_BUF_SIZE = 32 * 1024;
    private static final int  SB_INIT_CAP   = 8 * 1024;

    private static final ThreadLocal<ThreadResources> TL_RES =
        ThreadLocal.withInitial(ThreadResources::new);

    // ThreadLocal decoder pool to avoid creating new decoders
    private static final ThreadLocal<CharsetDecoder> TL_DECODER =
        ThreadLocal.withInitial(() -> StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE));

    public BinaryFileReader(SodiumEditor editor, BinaryTokenConverter tokenConverter) {
        this.editor = editor;
        this.tokenConverter = tokenConverter;
    }

    // ── readLineWithBinarySafe ─────────────────────────────────────────────────
    /**
     * Uses FileChannel + direct ByteBuffer read into a reused array.
     * Eliminates ByteArrayOutputStream and its internal byte[] copies.
     */
    public String readLineWithBinarySafe(
        RandomAccessFile raf, int line, long fileLen, Charset fileCharset, boolean binarySafeRenderingEnabled) throws Exception {

        long start, end;
        synchronized (editor.fileIO.lineOffsetsLock) {
            if (line < 0 || line >= editor.fileIO.lineOffsets.length) return "";
            start = editor.fileIO.lineOffsets[line];
            end   = (line + 1 < editor.fileIO.lineOffsets.length)
                    ? editor.fileIO.lineOffsets[line + 1] : fileLen;
        }
        if (start >= end) return "";

        long lineByteLen = end - start;
        // Cap at 2 MB safety limit
        int  cap   = (int) Math.min(lineByteLen, 2_000_000L);
        ThreadResources res = TL_RES.get();
        byte[] sink = res.sliceBuf(cap);

        FileChannel ch     = raf.getChannel();
        ByteBuffer  direct = ByteBuffer.wrap(sink);
        direct.limit(cap);
        direct.position(0);

        ch.position(start);
        // Read whole line in as few syscalls as possible (64 KB chunks)
        int totalRead = 0;
        while (totalRead < cap) {
            int got = ch.read(direct);
            if (got <= 0) break;
            totalRead += got;
        }

        // Trim trailing \r\n
        int used = totalRead;
        while (used > 0 && (sink[used - 1] == '\n' || sink[used - 1] == '\r')) used--;

        if (used <= 0) return "";

        if (binarySafeRenderingEnabled) return tokenConverter.bytesToControlVisible(sink, used, fileCharset);
        return new String(sink, 0, used, fileCharset);
    }

    // ── readLineSliceAtByte ────────────────────────────────────────────────────
    public String readLineSliceAtByte(
        RandomAccessFile raf, long lineStart, long lineByteLen,
        int startChar, int endChar, Charset fileCharset, boolean binarySafeRenderingEnabled) throws Exception {

        int safeStart = (int) Math.max(0, Math.min(startChar, Math.min(Integer.MAX_VALUE, lineByteLen)));
        int safeEnd   = (int) Math.max(safeStart, Math.min(endChar, Math.min(Integer.MAX_VALUE, lineByteLen)));
        int len = safeEnd - safeStart;
        if (len <= 0) return "";

        ThreadResources res = TL_RES.get();
        byte[] buf = res.sliceBuf(len);

        raf.getChannel().position(lineStart + safeStart);
        ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
        while (bb.hasRemaining()) {
            int got = raf.getChannel().read(bb);
            if (got <= 0) break;
        }

        if (binarySafeRenderingEnabled) return tokenConverter.bytesToControlVisible(buf, len, fileCharset);
        return new String(buf, 0, len, fileCharset);
    }

    // ── readLineSliceByChars ───────────────────────────────────────────────────
    /**
     * Key improvements:
     *  • Reuses ThreadLocal byte[], CharBuffer, StringBuilder
     *  • Early exit the moment safeEnd chars collected (when !needTotalLength)
     *  • Single decoder instance per call (no per-chunk recreation)
     *  • Binary-safe path uses lookup table, not escapeControlChar()
     */
    public StreamedCharSlice readLineSliceByChars(
        RandomAccessFile raf, long lineStart,
        int startChar, int endChar,
        boolean needTotalLength, Charset fileCharset, boolean binarySafeRenderingEnabled,
        boolean binaryHexTokensEnabled) throws Exception {

        int safeStart = Math.max(0, startChar);
        int safeEnd   = Math.max(safeStart, endChar);

        ThreadResources res     = TL_RES.get();
        byte[]          readBuf = res.readBuf;
        CharBuffer      charBuf = res.charBuf;
        StringBuilder   sb      = res.sb;
        sb.setLength(0);

        // Reuse decoder from pool instead of creating new one
        CharsetDecoder decoder = TL_DECODER.get();
        decoder.reset();

        // Reconfigure decoder for the target charset
        decoder.onMalformedInput(CodingErrorAction.REPLACE)
               .onUnmappableCharacter(CodingErrorAction.REPLACE);

        FileChannel ch       = raf.getChannel();
        ch.position(lineStart);

        int     charIndex = 0;
        boolean sliceDone = false;
        boolean done      = false;

        outer:
        while (!done) {
            int n = raf.read(readBuf);
            if (n <= 0) break;

            // Find newline boundary in this chunk
            int limit      = n;
            boolean hitNL  = false;
            for (int i = 0; i < n; i++) {
                if (readBuf[i] == '\n') {
                    limit = i;
                    if (limit > 0 && readBuf[limit - 1] == '\r') limit--;
                    hitNL = true;
                    break;
                }
            }

            ByteBuffer byteBuf = ByteBuffer.wrap(readBuf, 0, limit);

            while (true) {
                charBuf.clear();
                java.nio.charset.CoderResult cr = decoder.decode(byteBuf, charBuf, hitNL);
                charBuf.flip();

                int rem = charBuf.remaining();
                for (int i = 0; i < rem; i++) {
                    char c = charBuf.get();

                    if (!sliceDone && charIndex >= safeStart) {
                        // Hot path: only escape actual control chars or DEL in binary mode.
                        // International characters (above 0x7F) should be rendered normally.
                        if (binarySafeRenderingEnabled && (c < 0x20 || c == 0x7F)) {
                            int b = c & 0xFF;
                            String[] BYTE_TOKEN = BinaryTokenConverter.BYTE_TOKEN;
                            sb.append(b < BYTE_TOKEN.length ? BYTE_TOKEN[b] : tokenConverter.escapeControlChar(c));
                        } else {
                            sb.append(c);
                        }
                    }

                    charIndex++;

                    if (!sliceDone && charIndex >= safeEnd) {
                        if (!needTotalLength) { done = true; break; }
                        sliceDone = true;
                    }
                }
                if (done) break outer;
                if (cr.isOverflow())  continue;
                if (cr.isUnderflow()) break;
            }
            if (hitNL) break;
        }

        return new StreamedCharSlice(
            sb.toString(), needTotalLength ? charIndex : -1);
    }

    // Thread-local resources
    public static final class ThreadResources {
        public final byte[]        readBuf  = new byte[READ_BUF_SIZE];
        public final ByteBuffer    readBB   = ByteBuffer.wrap(readBuf);
        public final CharBuffer    charBuf  = CharBuffer.allocate(CHAR_BUF_SIZE);
        public final StringBuilder sb       = new StringBuilder(SB_INIT_CAP);
        // Scratch byte buffer for readLineSliceAtByte (max 1 MB slice)
        public byte[]              sliceBuf = new byte[4096];

        public byte[] sliceBuf(int needed) {
            if (sliceBuf.length < needed) sliceBuf = new byte[needed];
            return sliceBuf;
        }
    }
}
