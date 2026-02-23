package com.yn.sodiumeditor.input;

import android.view.inputmethod.ExtractedText;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;
import com.yn.sodiumeditor.SodiumEditorView;

final class ImeTextHelper {
  public static final int IME_CONTEXT_BEFORE_CHARS = 2048;
  public static final int IME_CONTEXT_AFTER_CHARS = 2048;

  private final SodiumEditorView view;

  ImeTextHelper(SodiumEditorView view) {
    this.view = view;
  }

  @Nullable
  RandomAccessFile openImeRandomAccessFile() {
    if (!view.isIndexReady || view.sourceFile == null || !view.sourceFile.exists()) return null;
    try {
      return new RandomAccessFile(view.sourceFile, "r");
    } catch (Exception ignored) {
      return null;
    }
  }

  String getLineTextForImeScan(int line, @Nullable RandomAccessFile raf) {
    if (line < 0) return "";
    String mod = view.modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= view.windowStartLine && line < view.windowStartLine + view.linesWindow.size()) {
      String text = view.getLineFromWindowLocal(line - view.windowStartLine);
      return (text != null) ? text : "";
    }
    if (raf != null && view.isIndexReady) {
      long offset;
      synchronized (view.lineOffsetsLock) {
        if (line < 0 || line >= view.lineOffsets.length) return "";
        offset = view.lineOffsets[line];
      }
      try {
        return view.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return "";
      }
    }
    return "";
  }

  SodiumEditorView.CursorTarget clampLineCharToDocument(
      int line, int ch, @Nullable RandomAccessFile raf) {
    int total = view.getLinesCount();
    if (total <= 0) return new SodiumEditorView.CursorTarget(0, 0);
    int clampedLine = Math.max(0, Math.min(line, total - 1));
    String ln = getLineTextForImeScan(clampedLine, raf);
    int len = (ln == null) ? 0 : ln.length();
    int clampedChar = Math.max(0, Math.min(ch, len));
    return new SodiumEditorView.CursorTarget(clampedLine, clampedChar);
  }

  SodiumEditorView.CursorTarget moveCursorByCharsForIme(
      int line, int ch, int delta, @Nullable RandomAccessFile raf) {
    SodiumEditorView.CursorTarget base = clampLineCharToDocument(line, ch, raf);
    int curLine = base.line;
    int curChar = base.ch;
    int totalLines = view.getLinesCount();
    if (totalLines <= 0) totalLines = 1;
    if (delta == 0) return base;

    if (delta < 0) {
      int remaining = -delta;
      while (remaining > 0) {
        String ln = getLineTextForImeScan(curLine, raf);
        int len = (ln == null) ? 0 : ln.length();
        curChar = Math.min(curChar, len);
        if (curChar >= remaining) {
          curChar -= remaining;
          remaining = 0;
          break;
        }
        remaining -= curChar;
        if (curLine <= 0) {
          curChar = 0;
          remaining = 0;
          break;
        }
        remaining -= 1;
        curLine--;
        ln = getLineTextForImeScan(curLine, raf);
        curChar = (ln == null) ? 0 : ln.length();
        if (remaining < 0) remaining = 0;
      }
      return new SodiumEditorView.CursorTarget(curLine, curChar);
    }

    int remaining = delta;
    while (remaining > 0) {
      String ln = getLineTextForImeScan(curLine, raf);
      int len = (ln == null) ? 0 : ln.length();
      curChar = Math.min(curChar, len);
      int available = len - curChar;
      if (available >= remaining) {
        curChar += remaining;
        remaining = 0;
        break;
      }
      remaining -= available;
      if (curLine >= totalLines - 1) {
        curChar = len;
        remaining = 0;
        break;
      }
      remaining -= 1;
      curLine++;
      curChar = 0;
      if (remaining < 0) remaining = 0;
    }
    return new SodiumEditorView.CursorTarget(curLine, curChar);
  }

  String buildRangeTextForIme(
      SodiumEditorView.CursorTarget start,
      SodiumEditorView.CursorTarget end,
      @Nullable RandomAccessFile raf) {
    int sL = start.line, sC = start.ch, eL = end.line, eC = end.ch;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    StringBuilder sb = new StringBuilder();
    for (int line = sL; line <= eL; line++) {
      String ln = getLineTextForImeScan(line, raf);
      if (ln == null) ln = "";
      int from = (line == sL) ? Math.min(sC, ln.length()) : 0;
      int to = (line == eL) ? Math.min(eC, ln.length()) : ln.length();
      if (from < to) sb.append(ln, from, to);
      if (line < eL) sb.append('\n');
    }
    return sb.toString();
  }

  ImeContext buildImeContext(int beforeChars, int afterChars) {
    int before = Math.max(0, beforeChars);
    int after = Math.max(0, afterChars);
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget start =
          moveCursorByCharsForIme(view.cursorManager.getLine(), view.cursorManager.getChar(), -before, raf);
      SodiumEditorView.CursorTarget end =
          moveCursorByCharsForIme(view.cursorManager.getLine(), view.cursorManager.getChar(), after, raf);
      String text = buildRangeTextForIme(start, end, raf);
      return new ImeContext(start.line, start.ch, text);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  ExtractedText buildExtractedTextFromContext(ImeContext ctx) {
    ExtractedText et = new ExtractedText();
    et.text = ctx.text;
    et.startOffset = 0;
    et.partialStartOffset = -1;
    et.partialEndOffset = -1;

    int sLine = view.cursorManager.getLine(), sChar = view.cursorManager.getChar(), eLine = view.cursorManager.getLine(), eChar = view.cursorManager.getChar();
    if (view.selectionManager.hasSelection()) {
      sLine = view.selectionManager.selStartLine;
      sChar = view.selectionManager.selStartChar;
      eLine = view.selectionManager.selEndLine;
      eChar = view.selectionManager.selEndChar;
      if (view.comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar;
        sLine = eLine;
        sChar = eChar;
        eLine = tL;
        eChar = tC;
      }
    }
    int selStart = lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = lineCharToOffsetInContext(ctx, eLine, eChar);
    et.selectionStart = selStart;
    et.selectionEnd = selEnd;
    return et;
  }

  SodiumEditorView.CursorTarget offsetToLineCharInContext(ImeContext ctx, int offset) {
    int safeOffset = Math.max(0, Math.min(offset, ctx.text.length()));
    int line = ctx.startLine;
    int ch = ctx.startChar;
    for (int i = 0; i < safeOffset; i++) {
      char c = ctx.text.charAt(i);
      if (c == '\n') {
        line++;
        ch = 0;
      } else {
        ch++;
      }
    }
    return new SodiumEditorView.CursorTarget(line, ch);
  }

  int lineCharToOffsetInContext(ImeContext ctx, int line, int ch) {
    int offset = 0;
    int curLine = ctx.startLine;
    int curChar = ctx.startChar;
    int len = ctx.text.length();
    for (int i = 0; i < len; i++) {
      if (curLine == line && curChar == ch) return offset;
      char c = ctx.text.charAt(i);
      if (c == '\n') {
        curLine++;
        curChar = 0;
      } else {
        curChar++;
      }
      offset++;
    }
    return Math.max(0, Math.min(offset, len));
  }

  String getImeTextBeforeCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget start =
          moveCursorByCharsForIme(view.cursorManager.getLine(), view.cursorManager.getChar(), -length, raf);
      return buildRangeTextForIme(start, new SodiumEditorView.CursorTarget(view.cursorManager.getLine(), view.cursorManager.getChar()), raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  String getImeTextAfterCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget end =
          moveCursorByCharsForIme(view.cursorManager.getLine(), view.cursorManager.getChar(), length, raf);
      return buildRangeTextForIme(new SodiumEditorView.CursorTarget(view.cursorManager.getLine(), view.cursorManager.getChar()), end, raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  int[] getWordBoundsAtCursor() {
    String line = view.getLineTextForRender(view.cursorManager.getLine());
    if (line == null || line.isEmpty()) return null;
    int pos = Math.max(0, Math.min(view.cursorManager.getChar(), line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return null;
    if (Character.isWhitespace(line.charAt(pos))) return null;
    return view.computeWordBounds(line, pos);
  }

  static final class ImeContext {
    final int startLine;
    final int startChar;
    final String text;

    ImeContext(int startLine, int startChar, String text) {
      this.startLine = startLine;
      this.startChar = startChar;
      this.text = (text == null) ? "" : text;
    }
  }
}
