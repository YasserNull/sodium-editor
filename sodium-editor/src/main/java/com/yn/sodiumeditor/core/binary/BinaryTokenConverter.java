package com.yn.sodiumeditor.core.binary;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * BinaryTokenConverter handles conversion of binary bytes to visible tokens. This includes: -
 * Lookup tables for byte tokens - Byte-to-visible string conversion - Token span caching - Control
 * character escaping
 */
public class BinaryTokenConverter {

  // ── Lookup tables ──────────────────────────────────────────────────────────
  public static final String[] BYTE_TOKEN = new String[256];
  private static final String[] HEX2 = new String[256];
  // Pre-computed token lengths — avoids .length() call in hot loop
  private static final byte[] BYTE_TOKEN_LEN = new byte[256];

  public static final String[] CONTROL_TOKENS = {
    "NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL",
    "BS", "TAB", "LF", "VT", "FF", "CR", "SO", "SI",
    "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB",
    "CAN", "EM", "SUB", "ESC", "FS", "GS", "RS", "US"
  };

  // Dynamic span list with efficient growth strategy
  private static final ThreadLocal<SpanList> TL_SPAN_LIST = ThreadLocal.withInitial(SpanList::new);

  private boolean binaryHexTokensEnabled = true;
  private boolean binaryCaretNotationEnabled = false;

  public BinaryTokenConverter() {
    initLookupTables();
  }

  private void initLookupTables() {
    for (int i = 0; i < 256; i++) {
      String hx = Integer.toHexString(i).toUpperCase();
      HEX2[i] = (hx.length() < 2) ? "0" + hx : hx;
    }
    rebuildTokenTable();
  }

  public void rebuildTokenTable() {
    for (int i = 0; i < 256; i++) {
      if (i >= 0x20 && i <= 0x7E) {
        BYTE_TOKEN[i] = String.valueOf((char) i);
      } else if (i <= 0x1F) {
        if (binaryCaretNotationEnabled) {
          char caret = (char) ('@' + i);
          BYTE_TOKEN[i] = "^" + caret;
        } else {
          BYTE_TOKEN[i] = CONTROL_TOKENS[i];
        }
      } else if (i == 0x7F) {
        BYTE_TOKEN[i] = binaryCaretNotationEnabled ? "^?" : "DEL";
      } else {
        BYTE_TOKEN[i] = binaryHexTokensEnabled ? ("0x" + HEX2[i]) : ".";
      }
      BYTE_TOKEN_LEN[i] = (byte) BYTE_TOKEN[i].length();
    }
  }

  // ── Configuration ──────────────────────────────────────────────────────────
  public void setBinaryHexTokensEnabled(boolean enabled) {
    binaryHexTokensEnabled = enabled;
    rebuildTokenTable();
  }

  public boolean isBinaryHexTokensEnabled() {
    return binaryHexTokensEnabled;
  }

  public void setBinaryCaretNotationEnabled(boolean enabled) {
    binaryCaretNotationEnabled = enabled;
    rebuildTokenTable();
  }

  public boolean isBinaryCaretNotationEnabled() {
    return binaryCaretNotationEnabled;
  }

  // ── Core: bytes → visible string (hot path) ────────────────────────────────
  /**
   * O(n) single-pass, zero extra allocation. Uses pre-computed lengths to size the StringBuilder
   * exactly once.
   */
  public String bytesToControlVisible(byte[] buf, int len) {
    return bytesToControlVisible(buf, len, StandardCharsets.UTF_8);
  }

  public String bytesToControlVisible(byte[] buf, int len, Charset charset) {
    if (len <= 0) return "";
    Charset safeCharset = charset != null ? charset : StandardCharsets.UTF_8;
    return charsToControlVisible(new String(buf, 0, len, safeCharset));
  }

  public String rawBytesToControlVisible(byte[] buf, int len) {
    if (buf == null || len <= 0) return "";
    int safeLen = Math.min(len, buf.length);
    int outLen = 0;
    for (int i = 0; i < safeLen; i++) {
      outLen += BYTE_TOKEN_LEN[buf[i] & 0xFF];
    }

    StringBuilder sb = TL_SB.get();
    sb.setLength(0);
    sb.ensureCapacity(outLen);
    for (int i = 0; i < safeLen; i++) {
      sb.append(BYTE_TOKEN[buf[i] & 0xFF]);
    }
    return sb.toString();
  }

  public String rawBytesToHexAsciiLine(long offset, byte[] buf, int len) {
    if (buf == null || len <= 0) return offsetPrefix(offset);
    int safeLen = Math.min(len, buf.length);
    StringBuilder sb = TL_SB.get();
    sb.setLength(0);
    sb.ensureCapacity(78);
    sb.append(offsetPrefix(offset)).append("  ");
    for (int i = 0; i < 16; i++) {
      if (i < safeLen) {
        sb.append(HEX2[buf[i] & 0xFF]);
      } else {
        sb.append("  ");
      }
      if (i == 7) sb.append("  ");
      else sb.append(' ');
    }
    sb.append(" |");
    for (int i = 0; i < safeLen; i++) {
      int b = buf[i] & 0xFF;
      sb.append((b >= 0x20 && b <= 0x7E) ? (char) b : '.');
    }
    for (int i = safeLen; i < 16; i++) sb.append(' ');
    sb.append('|');
    return sb.toString();
  }

  private String offsetPrefix(long offset) {
    String hex = Long.toHexString(Math.max(0L, offset)).toUpperCase();
    int width = hex.length() > 8 ? 16 : 8;
    StringBuilder sb = TL_SB.get();
    int originalLength = sb.length();
    for (int i = hex.length(); i < width; i++) sb.append('0');
    sb.append(hex);
    String out = sb.substring(originalLength);
    sb.setLength(originalLength);
    return out;
  }

  public String charsToControlVisible(String text) {
    if (text == null || text.isEmpty()) return "";

    int outLen = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      outLen += needsEscaping(c) ? escapeControlChar(c).length() : 1;
    }

    StringBuilder sb = TL_SB.get();
    sb.setLength(0);
    sb.ensureCapacity(outLen);

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      sb.append(needsEscaping(c) ? escapeControlChar(c) : String.valueOf(c));
    }

    return sb.toString();
  }

  public String bytesToControlVisibleAndCacheSpans(
      byte[] buf, int len, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans) {
    return bytesToControlVisibleAndCacheSpans(
        buf, len, lineIndex, binaryTokenSpans, StandardCharsets.UTF_8);
  }

  public String bytesToControlVisibleAndCacheSpans(
      byte[] buf,
      int len,
      int lineIndex,
      android.util.SparseArray<int[]> binaryTokenSpans,
      Charset charset) {
    if (len <= 0) {
      binaryTokenSpans.remove(lineIndex);
      return "";
    }
    Charset safeCharset = charset != null ? charset : StandardCharsets.UTF_8;
    return charsToControlVisibleAndCacheSpans(
        new String(buf, 0, len, safeCharset), lineIndex, binaryTokenSpans);
  }

  public String rawBytesToControlVisibleAndCacheSpans(
      byte[] buf, int len, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans) {
    if (buf == null || len <= 0) {
      binaryTokenSpans.remove(lineIndex);
      return "";
    }
    int safeLen = Math.min(len, buf.length);
    int outLen = 0;
    for (int i = 0; i < safeLen; i++) {
      outLen += BYTE_TOKEN_LEN[buf[i] & 0xFF];
    }

    StringBuilder sb = TL_SB.get();
    sb.setLength(0);
    sb.ensureCapacity(outLen);

    SpanList spanList = TL_SPAN_LIST.get();
    spanList.reset();

    int pos = 0;
    for (int i = 0; i < safeLen; i++) {
      int b = buf[i] & 0xFF;
      String tok = BYTE_TOKEN[b];
      int tokLen = tok.length();
      if (tokLen > 1) {
        spanList.add(pos);
        spanList.add(pos + tokLen);
      }
      sb.append(tok);
      pos += tokLen;
    }

    if (spanList.size == 0) {
      binaryTokenSpans.remove(lineIndex);
    } else {
      int[] existing = binaryTokenSpans.get(lineIndex);
      if (existing == null || existing.length != spanList.size) {
        int[] packed = new int[spanList.size];
        System.arraycopy(spanList.data, 0, packed, 0, spanList.size);
        binaryTokenSpans.put(lineIndex, packed);
      } else {
        System.arraycopy(spanList.data, 0, existing, 0, spanList.size);
      }
    }
    return sb.toString();
  }

  public String charsToControlVisibleAndCacheSpans(
      String text, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans) {
    if (text == null || text.isEmpty()) {
      binaryTokenSpans.remove(lineIndex);
      return "";
    }
    int outLen = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      outLen += needsEscaping(c) ? escapeControlChar(c).length() : 1;
    }

    StringBuilder sb = TL_SB.get();
    sb.setLength(0);
    sb.ensureCapacity(outLen);

    // Use dynamic span list with better growth strategy
    SpanList spanList = TL_SPAN_LIST.get();
    spanList.reset();

    int pos = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      String tok = needsEscaping(c) ? escapeControlChar(c) : String.valueOf(c);
      int tokLen = tok.length();
      if (needsEscaping(c)) {
        spanList.add(pos);
        spanList.add(pos + tokLen);
      }
      sb.append(tok);
      pos += tokLen;
    }

    if (spanList.size == 0) {
      binaryTokenSpans.remove(lineIndex);
    } else {
      // Copy to compact array only if changed
      int[] existing = binaryTokenSpans.get(lineIndex);
      if (existing == null || existing.length != spanList.size) {
        int[] packed = new int[spanList.size];
        System.arraycopy(spanList.data, 0, packed, 0, spanList.size);
        binaryTokenSpans.put(lineIndex, packed);
      } else {
        // Reuse existing array if same size
        System.arraycopy(spanList.data, 0, existing, 0, spanList.size);
      }
    }
    return sb.toString();
  }

  // ThreadLocal StringBuilder
  private static final ThreadLocal<StringBuilder> TL_SB =
      ThreadLocal.withInitial(() -> new StringBuilder(8192));

  // ── Helpers ────────────────────────────────────────────────────────────────
  public String escapeControlChar(char c) {
    int b = c & 0xFFFF;
    if (b <= 0x1F) return binaryCaretNotationEnabled ? ("^" + (char) ('@' + b)) : CONTROL_TOKENS[b];
    if (b == 0x7F) return binaryCaretNotationEnabled ? "^?" : "DEL";
    if (b >= 0x80 && b <= 0xFF) return binaryHexTokensEnabled ? ("0x" + HEX2[b]) : ".";
    return String.valueOf(c);
  }

  public boolean needsEscaping(char c) {
    return c != '\t' && (c < 0x20 || c == 0x7F);
  }

  public int getDisplayWidth(char c, boolean binarySafeRenderingEnabled) {
    if (!binarySafeRenderingEnabled) return 1;
    if (c == '\t') return 1;
    if (needsEscaping(c)) {
      int b = c & 0xFF;
      if (b <= 0x1F) return BYTE_TOKEN_LEN[b];
      if (b == 0x7F) return BYTE_TOKEN_LEN[b];
      if (b >= 0x80 && b <= 0xFF) return BYTE_TOKEN_LEN[b];
    }
    return 1;
  }

  public int matchBinaryToken(String line, int index) {
    if (line == null) return 0;
    int len = line.length();
    if (index < 0 || index >= len) return 0;
    char c = line.charAt(index);
    if (binaryCaretNotationEnabled) {
      if (c == '^' && index + 1 < len) {
        char n = line.charAt(index + 1);
        if (n == '?' || (n >= '@' && n <= '_')) return 2;
      }
      if (binaryHexTokensEnabled && c == '0') {
        if (index + 3 < len
            && line.charAt(index + 1) == 'x'
            && isHex(line.charAt(index + 2))
            && isHex(line.charAt(index + 3))) {
          return 4;
        }
      }
      return 0;
    }
    if (c == '0') {
      if (!binaryHexTokensEnabled) return 0;
      if (index + 3 < len
          && line.charAt(index + 1) == 'x'
          && isHex(line.charAt(index + 2))
          && isHex(line.charAt(index + 3))) {
        return 4;
      }
      return 0;
    }
    if (c < 'A' || c > 'Z') return 0;
    if (c == 'D'
        && index + 2 < len
        && line.charAt(index + 1) == 'E'
        && line.charAt(index + 2) == 'L') {
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

  private static boolean isHex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }

  // Dynamic span list with efficient growth strategy
  public static final class SpanList {
    public int[] data = new int[64]; // Start with reasonable capacity
    public int size = 0;

    public void reset() {
      size = 0;
    }

    public void add(int val) {
      if (size == data.length) {
        // Grow by 1.5x instead of 2x to reduce memory waste
        int newCapacity = data.length + (data.length >> 1);
        data = java.util.Arrays.copyOf(data, newCapacity);
      }
      data[size++] = val;
    }
  }
}
