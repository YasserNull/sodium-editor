package com.yn.sodiumeditor;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Manages binary-safe rendering for the SodiumEditor.
 * Handles rendering of binary files with control character visualization.
 */
public class BinaryRender {

  private final SodiumEditor editor;

  // Binary rendering state
  public boolean binarySafeRenderingEnabled = true;

  // Control character tokens
  public static final String[] CONTROL_TOKENS =
      new String[] {
        "<NUL>", "<SOH>", "<STX>", "<ETX>", "<EOT>", "<ENQ>", "<ACK>", "<BEL>",
        "<BS>", "<TAB>", "<LF>", "<VT>", "<FF>", "<CR>", "<SO>", "<SI>",
        "<DLE>", "<DC1>", "<DC2>", "<DC3>", "<DC4>", "<NAK>", "<SYN>", "<ETB>",
        "<CAN>", "<EM>", "<SUB>", "<ESC>", "<FS>", "<GS>", "<RS>", "<US>"
      };

  public BinaryRender(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables binary-safe rendering.
   */
  public void setBinarySafeRenderingEnabled(boolean enabled) {
    if (this.binarySafeRenderingEnabled == enabled) return;
    this.binarySafeRenderingEnabled = enabled;
    editor.invalidate();
  }

  /**
   * Checks if binary-safe rendering is enabled.
   */
  public boolean isBinarySafeRenderingEnabled() {
    return binarySafeRenderingEnabled;
  }

  /**
   * Converts bytes to control-visible string representation.
   * Non-printable characters are shown as <XX> or control tokens.
   */
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

  /**
   * Reads a line from file with binary-safe rendering.
   */
  public String readLineWithBinarySafe(
      RandomAccessFile raf, int line, long fileLen, Charset fileCharset) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    long start;
    long end;
    synchronized (editor.lineOffsetsLock) {
      if (line < 0 || line >= editor.lineOffsets.length) return "";
      start = editor.lineOffsets[line];
      end = (line + 1 < editor.lineOffsets.length) ? editor.lineOffsets[line + 1] : fileLen;
    }

    if (start >= end) return "";
    raf.seek(start);
    byte[] buf = new byte[8192];
    boolean seenAny = false;
    boolean hitNewline = false;

    while (!hitNewline) {
      int n = raf.read(buf);
      if (n <= 0) break;

      for (int i = 0; i < n; i++) {
        byte b = buf[i];
        if (b == '\n') {
          hitNewline = true;
          break;
        }
        if (b == '\r') {
          long nextPos = raf.getFilePointer() - n + i + 1;
          if (nextPos < end) {
            raf.seek(nextPos);
            int next = raf.read();
            if (next == '\n') {
              hitNewline = true;
              break;
            }
            raf.seek(nextPos);
          }
          break;
        }
        baos.write(b);
        seenAny = true;
        if (baos.size() > 2_000_000) break;
      }
    }

    if (!seenAny) return "";
    if (binarySafeRenderingEnabled) {
      byte[] data = baos.toByteArray();
      return bytesToControlVisible(data, data.length);
    }
    return baos.toString(fileCharset.name());
  }

  /**
   * Reads a line slice at byte position with binary-safe rendering.
   */
  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar, Charset fileCharset)
      throws Exception {
    int safeStart = Math.max(0, Math.min(startChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
    int safeEnd = Math.max(safeStart, Math.min(endChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
    int len = safeEnd - safeStart;
    if (len <= 0) return "";
    long startByte = lineStart + safeStart;
    raf.seek(startByte);
    byte[] buf = new byte[len];
    raf.readFully(buf);
    if (binarySafeRenderingEnabled) {
      return bytesToControlVisible(buf, buf.length);
    }
    return new String(buf, fileCharset);
  }

  /**
   * Reads a line slice by characters with binary-safe rendering.
   */
  public SodiumEditor.StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength, Charset fileCharset)
      throws Exception {
    int safeStart = Math.max(0, startChar);
    int safeEnd = Math.max(safeStart, endChar);
    CharsetDecoder decoder = fileCharset.newDecoder();
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
        charBuf.clear();
        java.nio.charset.CoderResult cr = decoder.decode(byteBuf, charBuf, hitNewline);
        charBuf.flip();
        int remaining = charBuf.remaining();
        for (int i = 0; i < remaining; i++) {
          char c = charBuf.get();
          if (charIndex >= safeStart && charIndex < safeEnd) {
            if (binarySafeRenderingEnabled && (c < 0x20 || c > 0x7E)) {
              sb.append(escapeControlChar(c));
            } else {
              sb.append(c);
            }
          }
          charIndex++;
          if (charIndex >= safeEnd) {
            done = true;
            break;
          }
        }
        if (hitNewline || !cr.isUnderflow()) {
          done = true;
          break;
        }
      }

      if (hitNewline) break;
    }

    int totalLength = needTotalLength ? charIndex : -1;
    return new SodiumEditor.StreamedCharSlice(sb.toString(), totalLength);
  }

  /**
   * Escapes a control character for binary-safe rendering.
   */
  public String escapeControlChar(char c) {
    int b = (int) c;
    if (b <= 0x1F) {
      return CONTROL_TOKENS[b];
    } else if (b == 0x7F) {
      return "<DEL>";
    } else if (b >= 0x80 && b <= 0xFF) {
      String hx = Integer.toHexString(b).toUpperCase();
      if (hx.length() < 2) hx = '0' + hx;
      return "<0x" + hx + ">";
    }
    return String.valueOf(c);
  }

  /**
   * Checks if a character needs escaping in binary-safe mode.
   */
  public boolean needsEscaping(char c) {
    return c < 0x20 || c > 0x7E;
  }

  /**
   * Gets the display width of a character in binary-safe mode.
   */
  public int getDisplayWidth(char c) {
    if (!binarySafeRenderingEnabled) return 1;
    if (needsEscaping(c)) {
      int b = (int) c;
      if (b <= 0x1F) {
        return CONTROL_TOKENS[b].length();
      } else if (b == 0x7F) {
        return 5; // "<DEL>"
      } else if (b >= 0x80 && b <= 0xFF) {
        return 6; // "<0xXX>"
      }
    }
    return 1;
  }
}
