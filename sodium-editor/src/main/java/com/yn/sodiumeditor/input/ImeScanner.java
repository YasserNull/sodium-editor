package com.yn.sodiumeditor.input;

import android.util.Log;
import android.view.inputmethod.ExtractedText;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.io.RandomAccessFile;

/**
 * Handles text scanning and coordinate mapping for IME.
 */
public class ImeScanner {
    private final SodiumEditor editor;

    public ImeScanner(SodiumEditor editor) {
        FunctionLog.f("ImeScanner", "ImeScanner", editor);
        this.editor = editor;
    }

    public ImeContext buildImeContext(int beforeChars, int afterChars) {
        FunctionLog.f("ImeScanner", "buildImeContext", beforeChars, afterChars);
        int before = Math.max(0, beforeChars);
        int after = Math.max(0, afterChars);
        RandomAccessFile raf = openImeRandomAccessFile();
        try {
            CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -before, raf);
            CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, after, raf);
            String text = buildRangeTextForIme(start, end, raf);
            Log.i("ImeContext", "buildImeContext: cursor=(" + editor.cursor.cursorLine + "," + editor.cursor.cursorChar + ") text.length=" + text.length() + " text preview: '" + text.substring(0, Math.min(50, text.length())) + "'");
            return new ImeContext(start.line, start.ch, text);
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public ExtractedText buildExtractedTextFromContext(ImeContext ctx) {
        FunctionLog.f("ImeScanner", "buildExtractedTextFromContext", ctx);
        ExtractedText et = new ExtractedText();
        et.text = ctx.text;
        et.startOffset = 0;
        et.partialStartOffset = -1;
        et.partialEndOffset = -1;

        int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
        int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
        if (editor.selection.hasSelection) {
            sLine = editor.selection.selStartLine;
            sChar = editor.selection.selStartChar;
            eLine = editor.selection.selEndLine;
            eChar = editor.selection.selEndChar;
            if (comparePos(sLine, sChar, eLine, eChar) > 0) {
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

    public String getImeTextBeforeCursor(int length) {
        FunctionLog.f("ImeScanner", "getImeTextBeforeCursor", length);
        if (length <= 0) return "";
        RandomAccessFile raf = openImeRandomAccessFile();
        try {
            CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -length, raf);
            return buildRangeTextForIme(start, new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), raf);
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public String getImeTextAfterCursor(int length) {
        FunctionLog.f("ImeScanner", "getImeTextAfterCursor", length);
        if (length <= 0) return "";
        RandomAccessFile raf = openImeRandomAccessFile();
        try {
            CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, length, raf);
            return buildRangeTextForIme(new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), end, raf);
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {}
            }
        }
    }

    @Nullable
    private RandomAccessFile openImeRandomAccessFile() {
        FunctionLog.f("ImeScanner", "openImeRandomAccessFile");
        // Don't use file-based random access when there are pending edits
        // The pending edits are in modifiedLines and will be read correctly
        // by getLineTextForImeScan() which checks modifiedLines first.
        // Reading from the file directly would return stale content.
        if (!editor.windowRender.modifiedLines.isEmpty()) {
            Log.i("ImeScanner", "openImeRandomAccessFile: returning null because modifiedLines is not empty");
            return null;
        }
        if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) return null;
        try {
            return new RandomAccessFile(editor.fileIO.sourceFile, "r");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getLineTextForImeScan(int line, @Nullable RandomAccessFile raf) {
        FunctionLog.f("ImeScanner", "getLineTextForImeScan", line, raf);
        if (line < 0) return "";
        String mod = editor.windowRender.modifiedLines.get(line);
        if (mod != null) {
            Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from modifiedLines: '" + mod + "'");
            return mod;
        }
        if (line >= editor.windowRender.windowStartLine && line < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
            String text = editor.windowRender.getLineFromWindowLocal(line - editor.windowRender.windowStartLine);
            Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from linesWindow: '" + text + "'");
            return (text != null) ? text : "";
        }
        if (raf != null && editor.fileIO.isIndexReady) {
            long offset;
            synchronized (editor.fileIO.lineOffsetsLock) {
                if (line < 0 || line >= editor.fileIO.lineOffsets.length) return "";
                offset = editor.fileIO.lineOffsets[line];
            }
            try {
                String fromFile = editor.fileIO.readLineUtf8AtByte(raf, offset);
                Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from file: '" + fromFile + "'");
                return fromFile;
            } catch (Exception ignored) {
                return "";
            }
        }
        Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " returning empty");
        return "";
    }

    private CursorTarget clampLineCharToDocument(int line, int ch, @Nullable RandomAccessFile raf) {
        FunctionLog.f("ImeScanner", "clampLineCharToDocument", line, ch, raf);
        int total = editor.view.getLinesCount();
        if (total <= 0) return new CursorTarget(0, 0);
        int clampedLine = Math.max(0, Math.min(line, total - 1));
        String ln = getLineTextForImeScan(clampedLine, raf);
        int len = (ln == null) ? 0 : ln.length();
        int clampedChar = Math.max(0, Math.min(ch, len));
        return new CursorTarget(clampedLine, clampedChar);
    }

    public CursorTarget moveCursorByCharsForIme(int line, int ch, int delta, @Nullable RandomAccessFile raf) {
        FunctionLog.f("ImeScanner", "moveCursorByCharsForIme", line, ch, delta, raf);
        CursorTarget base = clampLineCharToDocument(line, ch, raf);
        int curLine = base.line;
        int curChar = base.ch;
        int totalLines = editor.view.getLinesCount();
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
            return new CursorTarget(curLine, curChar);
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
        return new CursorTarget(curLine, curChar);
    }

    public String buildRangeTextForIme(CursorTarget start, CursorTarget end, @Nullable RandomAccessFile raf) {
        FunctionLog.f("ImeScanner", "buildRangeTextForIme", start, end, raf);
        int sL = start.line, sC = start.ch, eL = end.line, eC = end.ch;
        if (comparePos(sL, sC, eL, eC) > 0) {
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
            Log.i("ImeContext", "buildRangeTextForIme: line " + line + " = '" + ln + "' (modifiedLines.contains=" + editor.windowRender.modifiedLines.containsKey(line) + ")");
            int from = (line == sL) ? Math.min(sC, ln.length()) : 0;
            int to = (line == eL) ? Math.min(eC, ln.length()) : ln.length();
            if (from < to) sb.append(ln, from, to);
            if (line < eL) sb.append('\n');
        }
        return sb.toString();
    }

    public CursorTarget offsetToLineCharInContext(ImeContext ctx, int offset) {
        FunctionLog.f("ImeScanner", "offsetToLineCharInContext", ctx, offset);
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
        return new CursorTarget(line, ch);
    }

    public int lineCharToOffsetInContext(ImeContext ctx, int line, int ch) {
        FunctionLog.f("ImeScanner", "lineCharToOffsetInContext", ctx, line, ch);
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

    public int comparePos(int lineA, int charA, int lineB, int charB) {
        FunctionLog.f("ImeScanner", "comparePos", lineA, charA, lineB, charB);
        if (lineA != lineB) return Integer.compare(lineA, lineB);
        return Integer.compare(charA, charB);
    }
}
