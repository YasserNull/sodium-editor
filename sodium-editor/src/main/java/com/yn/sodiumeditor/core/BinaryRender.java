package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import android.util.SparseArray;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import com.yn.sodiumeditor.SodiumEditor;

public class BinaryRender {

  private final SodiumEditor editor;

  public boolean binarySafeRenderingEnabled = true;
  public boolean binaryTokenBoxEnabled = true;
  public int binaryTokenFillColor = 0xFFFF0000;
  public int binaryTokenStrokeColor = 0xFF000000;
  public float binaryTokenStrokeWidth = 1f;
  public float binaryTokenPaddingX = 2f;
  public float binaryTokenPaddingY = 2f;
  public boolean binaryHexTokensEnabled = true;
  public float binaryTokenCornerRadius = 10f;
  private final Paint binaryTokenFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint binaryTokenStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final SparseArray<int[]> binaryTokenSpans = new SparseArray<>();

  // ── Lookup tables ──────────────────────────────────────────────────────────
  private static final String[] BYTE_TOKEN  = new String[256];
  private static final String[] HEX2        = new String[256];
  // Pre-computed token lengths — avoids .length() call in hot loop
  private static final byte[]   BYTE_TOKEN_LEN = new byte[256];

  public static final String[] CONTROL_TOKENS = {
    "NUL","SOH","STX","ETX","EOT","ENQ","ACK","BEL",
    "BS" ,"TAB","LF" ,"VT" ,"FF" ,"CR" ,"SO" ,"SI" ,
    "DLE","DC1","DC2","DC3","DC4","NAK","SYN","ETB",
    "CAN","EM" ,"SUB","ESC","FS" ,"GS" ,"RS" ,"US"
  };

  // ── Thread-local pool: ONE object per thread, reused every call ────────────
  private static final int  READ_BUF_SIZE = 64 * 1024;   // 64 KB read window
  private static final int  CHAR_BUF_SIZE = 32 * 1024;
  private static final int  SB_INIT_CAP   = 8 * 1024;

  private static final ThreadLocal<ThreadResources> TL_RES =
      ThreadLocal.withInitial(ThreadResources::new);

  private static final class ThreadResources {
    final byte[]        readBuf  = new byte[READ_BUF_SIZE];
    final ByteBuffer    readBB   = ByteBuffer.wrap(readBuf);
    final CharBuffer    charBuf  = CharBuffer.allocate(CHAR_BUF_SIZE);
    final StringBuilder sb       = new StringBuilder(SB_INIT_CAP);
    // Scratch byte buffer for readLineSliceAtByte (max 1 MB slice)
    byte[]              sliceBuf = new byte[4096];

    byte[] sliceBuf(int needed) {
      if (sliceBuf.length < needed) sliceBuf = new byte[needed];
      return sliceBuf;
    }
  }

  // ── Init ───────────────────────────────────────────────────────────────────
  public BinaryRender(SodiumEditor editor) {
    this.editor = editor;
    initLookupTables();
    binaryTokenFillPaint.setStyle(Paint.Style.FILL);
    binaryTokenFillPaint.setColor(binaryTokenFillColor);
    binaryTokenStrokePaint.setStyle(Paint.Style.STROKE);
    binaryTokenStrokePaint.setColor(binaryTokenStrokeColor);
    binaryTokenStrokePaint.setStrokeWidth(binaryTokenStrokeWidth);
  }

  private static void initLookupTables() {
    if (BYTE_TOKEN[0] != null) return;
    for (int i = 0; i < 256; i++) {
      String hx = Integer.toHexString(i).toUpperCase();
      HEX2[i] = (hx.length() < 2) ? "0" + hx : hx;
    }
    for (int i = 0; i < 256; i++) {
      if      (i >= 0x20 && i <= 0x7E) BYTE_TOKEN[i] = String.valueOf((char) i);
      else if (i <= 0x1F)              BYTE_TOKEN[i] = CONTROL_TOKENS[i];
      else if (i == 0x7F)              BYTE_TOKEN[i] = "DEL";
      else                             BYTE_TOKEN[i] = "0x" + HEX2[i];
      BYTE_TOKEN_LEN[i] = (byte) BYTE_TOKEN[i].length();
    }
  }

  // ── Public API ─────────────────────────────────────────────────────────────
  public void setBinarySafeRenderingEnabled(boolean enabled) {
    binarySafeRenderingEnabled = enabled;
    synchronized (editor.textRender.lineWidthCache) {
      editor.textRender.lineWidthCache.clear();
    }
    editor.textRender.currentMaxWindowLineWidth = 0f;
    editor.textRender.globalMaxLineWidth        = 0f;
    editor.scroll.maxLineWidthForScroll         = 0f;
    editor.scroll.maxTextStartXForScroll        = 0f;
    editor.scroll.maxScrollXForScroll           = 0f;
    editor.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled)
      editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    editor.textRender.reloadWindowAroundVisible(false);
    editor.invalidate();
  }

  public boolean isBinarySafeRenderingEnabled() { return binarySafeRenderingEnabled; }

  public void setBinaryTokenBoxEnabled(boolean enabled) {
    binaryTokenBoxEnabled = enabled;
  }

  public void setBinaryTokenFillColor(int color) {
    binaryTokenFillColor = color;
    binaryTokenFillPaint.setColor(color);
  }

  public void setBinaryTokenStrokeColor(int color) {
    binaryTokenStrokeColor = color;
    binaryTokenStrokePaint.setColor(color);
  }

  public void setBinaryTokenStrokeWidth(float widthPx) {
    binaryTokenStrokeWidth = Math.max(0.5f, widthPx);
    binaryTokenStrokePaint.setStrokeWidth(binaryTokenStrokeWidth);
  }

  public void setBinaryTokenBoxPadding(float paddingX, float paddingY) {
    binaryTokenPaddingX = Math.max(0f, paddingX);
    binaryTokenPaddingY = Math.max(0f, paddingY);
  }

  public void setBinaryHexTokensEnabled(boolean enabled) {
    binaryHexTokensEnabled = enabled;
  }

  public void setBinaryTokenCornerRadius(float radiusPx) {
    binaryTokenCornerRadius = Math.max(0f, radiusPx);
  }

  public Paint getBinaryTokenFillPaint() { return binaryTokenFillPaint; }

  public Paint getBinaryTokenStrokePaint() { return binaryTokenStrokePaint; }

  public int[] getBinaryTokenSpans(int lineIndex) {
    return binaryTokenSpans.get(lineIndex);
  }

  public void clearBinaryTokenSpansForLine(int lineIndex) {
    binaryTokenSpans.remove(lineIndex);
  }

  // ── Core: bytes → visible string (hot path) ────────────────────────────────
  /**
   * O(n) single-pass, zero extra allocation.
   * Uses pre-computed lengths to size the StringBuilder exactly once.
   */
  public String bytesToControlVisible(byte[] buf, int len) {
    if (len <= 0) return "";

    // 1. Compute exact output length — avoids ANY StringBuilder resize
    int outLen = 0;
    for (int i = 0; i < len; i++) outLen += BYTE_TOKEN_LEN[buf[i] & 0xFF];

    StringBuilder sb = TL_RES.get().sb;
    sb.setLength(0);
    sb.ensureCapacity(outLen);

    // 2. Append tokens
    for (int i = 0; i < len; i++) {
      int b = buf[i] & 0xFF;
      if (!binaryHexTokensEnabled && b >= 0x80) {
        sb.append('.');
      } else {
        sb.append(BYTE_TOKEN[b]);
      }
    }

    return sb.toString();
  }

  public String bytesToControlVisibleAndCacheSpans(byte[] buf, int len, int lineIndex) {
    if (len <= 0) {
      binaryTokenSpans.remove(lineIndex);
      return "";
    }

    int outLen = 0;
    for (int i = 0; i < len; i++) {
      int b = buf[i] & 0xFF;
      if (!binaryHexTokensEnabled && b >= 0x80) {
        outLen += 1;
      } else {
        outLen += BYTE_TOKEN_LEN[b];
      }
    }

    StringBuilder sb = TL_RES.get().sb;
    sb.setLength(0);
    sb.ensureCapacity(outLen);

    int[] spans = new int[32];
    int spanCount = 0;
    int pos = 0;
    for (int i = 0; i < len; i++) {
      int b = buf[i] & 0xFF;
      if (!binaryHexTokensEnabled && b >= 0x80) {
        sb.append('.');
        pos += 1;
        continue;
      }
      String tok = BYTE_TOKEN[b];
      int tokLen = tok.length();
      if (b <= 0x1F || b == 0x7F || b >= 0x80) {
        if (spanCount + 2 > spans.length) {
          int[] grow = new int[spans.length * 2];
          System.arraycopy(spans, 0, grow, 0, spans.length);
          spans = grow;
        }
        spans[spanCount++] = pos;
        spans[spanCount++] = pos + tokLen;
      }
      sb.append(tok);
      pos += tokLen;
    }
    if (spanCount == 0) {
      binaryTokenSpans.remove(lineIndex);
    } else {
      int[] packed = new int[spanCount];
      System.arraycopy(spans, 0, packed, 0, spanCount);
      binaryTokenSpans.put(lineIndex, packed);
    }
    return sb.toString();
  }

  // ── readLineWithBinarySafe ─────────────────────────────────────────────────
  /**
   * Uses FileChannel + direct ByteBuffer read into a reused array.
   * Eliminates ByteArrayOutputStream and its internal byte[] copies.
   */
  public String readLineWithBinarySafe(
      RandomAccessFile raf, int line, long fileLen, Charset fileCharset) throws Exception {

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

    if (binarySafeRenderingEnabled) return bytesToControlVisible(sink, used);
    return new String(sink, 0, used, fileCharset);
  }

  // ── readLineSliceAtByte ────────────────────────────────────────────────────
  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen,
      int startChar, int endChar, Charset fileCharset) throws Exception {

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

    if (binarySafeRenderingEnabled) return bytesToControlVisible(buf, len);
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
  public SodiumEditor.StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart,
      int startChar, int endChar,
      boolean needTotalLength, Charset fileCharset) throws Exception {

    int safeStart = Math.max(0, startChar);
    int safeEnd   = Math.max(safeStart, endChar);

    ThreadResources res     = TL_RES.get();
    byte[]          readBuf = res.readBuf;
    CharBuffer      charBuf = res.charBuf;
    StringBuilder   sb      = res.sb;
    sb.setLength(0);

    CharsetDecoder decoder = fileCharset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);

    FileChannel ch       = raf.getChannel();
    ch.position(lineStart);

    int     charIndex = 0;
    boolean sliceDone = false;
    boolean done      = false;

    outer:
    while (!done) {
      int n = raf.read(readBuf);     // still uses RAF for compat; FileChannel.read also fine
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
      decoder.reset();

      while (true) {
        charBuf.clear();
        java.nio.charset.CoderResult cr = decoder.decode(byteBuf, charBuf, hitNL);
        charBuf.flip();

        int rem = charBuf.remaining();
        for (int i = 0; i < rem; i++) {
          char c = charBuf.get();

          if (!sliceDone && charIndex >= safeStart) {
            // Hot path: lookup table instead of method call
            if (binarySafeRenderingEnabled && (c < 0x20 || c > 0x7E)) {
              int b = c & 0xFF;
              if (!binaryHexTokensEnabled && b >= 0x80) {
                sb.append('.');
              } else {
                sb.append(b < BYTE_TOKEN.length ? BYTE_TOKEN[b] : escapeControlChar(c));
              }
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

    return new SodiumEditor.StreamedCharSlice(
        sb.toString(), needTotalLength ? charIndex : -1);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  public String escapeControlChar(char c) {
    int b = c & 0xFFFF;
    if (b <= 0x1F)              return CONTROL_TOKENS[b];
    if (b == 0x7F)              return "DEL";
    if (b >= 0x80 && b <= 0xFF) return binaryHexTokensEnabled ? ("0x" + HEX2[b]) : ".";
    return String.valueOf(c);
  }

  public boolean needsEscaping(char c) { return c < 0x20 || c > 0x7E; }

  public int getDisplayWidth(char c) {
    if (!binarySafeRenderingEnabled) return 1;
    if (needsEscaping(c)) {
      int b = c & 0xFF;
      if (b <= 0x1F)              return BYTE_TOKEN_LEN[b];
      if (b == 0x7F)              return 3;
      if (b >= 0x80 && b <= 0xFF) return binaryHexTokensEnabled ? 4 : 1;
    }
    return 1;
  }

  public int matchBinaryToken(String line, int index) {
    if (line == null) return 0;
    int len = line.length();
    if (index < 0 || index >= len) return 0;
    char c = line.charAt(index);
    if (c == '0') {
      if (!binaryHexTokensEnabled) return 0;
      if (index + 3 < len && line.charAt(index + 1) == 'x'
          && isHex(line.charAt(index + 2)) && isHex(line.charAt(index + 3))) {
        return 4;
      }
      return 0;
    }
    if (c < 'A' || c > 'Z') return 0;
    if (c == 'D' && index + 2 < len
        && line.charAt(index + 1) == 'E' && line.charAt(index + 2) == 'L') {
      return 3;
    }
    for (String tok : CONTROL_TOKENS) {
      if (tok.charAt(0) != c) continue;
      int tlen = tok.length();
      if (index + tlen <= len && line.regionMatches(index, tok, 0, tlen)) {
        return tlen;
      }
    }
    return 0;
  }

  public boolean findBinaryTokenSpan(String line, int index, int[] outStartEnd) {
    if (outStartEnd == null || outStartEnd.length < 2) return false;
    if (line == null) return false;
    int len = line.length();
    if (index < 0 || index >= len) return false;
    int start = Math.max(0, index - 3);
    for (int s = start; s <= index; s++) {
      int tlen = matchBinaryToken(line, s);
      if (tlen > 0 && index < s + tlen) {
        outStartEnd[0] = s;
        outStartEnd[1] = s + tlen;
        return true;
      }
    }
    return false;
  }

  public int snapBinaryCursor(String line, int index) {
    return index;
  }

  public int snapBinaryCursor(String line, int index, int lineIndex) {
    int[] spans = binaryTokenSpans.get(lineIndex);
    int[] span = new int[2];
    if (spans != null && findBinaryTokenSpanInSpans(spans, index, span)) {
      int start = span[0];
      int end = span[1];
      if (index <= start) return start;
      if (index >= end) return end;
      int leftDist = index - start;
      int rightDist = end - index;
      return (leftDist <= rightDist) ? start : end;
    }
    if (!findBinaryTokenSpan(line, index, span)) return index;
    int start = span[0];
    int end = span[1];
    if (index <= start) return start;
    if (index >= end) return end;
    int leftDist = index - start;
    int rightDist = end - index;
    return (leftDist <= rightDist) ? start : end;
  }

  public boolean findBinaryTokenSpanInSpans(int[] spans, int index, int[] outStartEnd) {
    if (spans == null || outStartEnd == null || outStartEnd.length < 2) return false;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int s = spans[i];
      int e = spans[i + 1];
      if (index >= s && index < e) {
        outStartEnd[0] = s;
        outStartEnd[1] = e;
        return true;
      }
    }
    return false;
  }

  public void adjustBinaryTokenSpansForEdit(int lineIndex, int editIndex, int delta, int deleteLen) {
    int[] spans = binaryTokenSpans.get(lineIndex);
    if (spans == null || spans.length == 0) return;
    int delStart = editIndex;
    int delEnd = editIndex + Math.max(0, deleteLen);
    int[] out = new int[spans.length];
    int outCount = 0;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int s = spans[i];
      int e = spans[i + 1];
      if (deleteLen > 0) {
        if (e <= delStart) {
          // keep
        } else if (s >= delEnd) {
          s += delta;
          e += delta;
        } else {
          continue;
        }
      } else if (delta > 0) {
        if (editIndex > s && editIndex < e) {
          continue;
        }
        if (s >= editIndex) {
          s += delta;
          e += delta;
        }
      }
      out[outCount++] = s;
      out[outCount++] = e;
    }
    if (outCount == 0) {
      binaryTokenSpans.remove(lineIndex);
    } else {
      int[] packed = new int[outCount];
      System.arraycopy(out, 0, packed, 0, outCount);
      binaryTokenSpans.put(lineIndex, packed);
    }
  }

  private boolean isHex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }
}
