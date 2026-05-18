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
    private static final String FOLD_TYPING_PERF = "FoldTypingPerf";
    private final SodiumEditor editor;

    public ImeScanner(SodiumEditor editor) {
        FunctionLog.f("ImeScanner", "ImeScanner", editor);
        this.editor = editor;
    }

    public ImeContext buildImeContext(int beforeChars, int afterChars) {
        FunctionLog.f("ImeScanner", "buildImeContext", beforeChars, afterChars);
        long startMs = android.os.SystemClock.uptimeMillis();
        int before = Math.max(0, beforeChars);
        int after = Math.max(0, afterChars);
        long openStartMs = android.os.SystemClock.uptimeMillis();
        RandomAccessFile raf = openImeRandomAccessFile();
        long openMs = android.os.SystemClock.uptimeMillis() - openStartMs;
        try {
            long moveBeforeStartMs = android.os.SystemClock.uptimeMillis();
            CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -before, raf);
            long moveBeforeMs = android.os.SystemClock.uptimeMillis() - moveBeforeStartMs;
            long moveAfterStartMs = android.os.SystemClock.uptimeMillis();
            CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, after, raf);
            long moveAfterMs = android.os.SystemClock.uptimeMillis() - moveAfterStartMs;
            long rangeStartMs = android.os.SystemClock.uptimeMillis();
            String text = buildRangeTextForIme(start, end, raf);
            long rangeMs = android.os.SystemClock.uptimeMillis() - rangeStartMs;
            android.util.Log.i(
                    FOLD_TYPING_PERF,
                    "scanner.buildContext total="
                            + (android.os.SystemClock.uptimeMillis() - startMs)
                            + " open="
                            + openMs
                            + " moveBefore="
                            + moveBeforeMs
                            + " moveAfter="
                            + moveAfterMs
                            + " range="
                            + rangeMs
                            + " beforeReq="
                            + beforeChars
                            + " afterReq="
                            + afterChars
                            + " start="
                            + start.line
                            + ":"
                            + start.ch
                            + " end="
                            + end.line
                            + ":"
                            + end.ch
                            + " textLen="
                            + text.length()
                            + " modified="
                            + editor.windowRender.modifiedLines.size()
                            + " raf="
                            + (raf != null));
            if (editor.DEBUG_RENDER_LOGS) {
                Log.i("ImeContext", "buildImeContext: cursor=(" + editor.cursor.cursorLine + "," + editor.cursor.cursorChar + ") text.length=" + text.length() + " text preview: '" + text.substring(0, Math.min(50, text.length())) + "'");
            }
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
        long startMs = android.os.SystemClock.uptimeMillis();
        RandomAccessFile raf = openImeRandomAccessFile();
        try {
            long moveStartMs = android.os.SystemClock.uptimeMillis();
            CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -length, raf);
            long moveMs = android.os.SystemClock.uptimeMillis() - moveStartMs;
            long rangeStartMs = android.os.SystemClock.uptimeMillis();
            String result = buildRangeTextForIme(start, new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), raf);
            long rangeMs = android.os.SystemClock.uptimeMillis() - rangeStartMs;
            android.util.Log.i(
                    FOLD_TYPING_PERF,
                    "scanner.before total="
                            + (android.os.SystemClock.uptimeMillis() - startMs)
                            + " move="
                            + moveMs
                            + " range="
                            + rangeMs
                            + " req="
                            + length
                            + " len="
                            + result.length()
                            + " start="
                            + start.line
                            + ":"
                            + start.ch
                            + " modified="
                            + editor.windowRender.modifiedLines.size()
                            + " raf="
                            + (raf != null));
            return result;
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
        long startMs = android.os.SystemClock.uptimeMillis();
        RandomAccessFile raf = openImeRandomAccessFile();
        try {
            long moveStartMs = android.os.SystemClock.uptimeMillis();
            CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, length, raf);
            long moveMs = android.os.SystemClock.uptimeMillis() - moveStartMs;
            long rangeStartMs = android.os.SystemClock.uptimeMillis();
            String result = buildRangeTextForIme(new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), end, raf);
            long rangeMs = android.os.SystemClock.uptimeMillis() - rangeStartMs;
            android.util.Log.i(
                    FOLD_TYPING_PERF,
                    "scanner.after total="
                            + (android.os.SystemClock.uptimeMillis() - startMs)
                            + " move="
                            + moveMs
                            + " range="
                            + rangeMs
                            + " req="
                            + length
                            + " len="
                            + result.length()
                            + " end="
                            + end.line
                            + ":"
                            + end.ch
                            + " modified="
                            + editor.windowRender.modifiedLines.size()
                            + " raf="
                            + (raf != null));
            return result;
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
            if (editor.DEBUG_RENDER_LOGS) {
                Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from modifiedLines: '" + mod + "'");
            }
            return mod;
        }
        if (line >= editor.windowRender.windowStartLine && line < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
            String text = editor.windowRender.getLineFromWindowLocal(line - editor.windowRender.windowStartLine);
            if (editor.DEBUG_RENDER_LOGS) {
                Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from linesWindow: '" + text + "'");
            }
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
                if (editor.DEBUG_RENDER_LOGS) {
                    Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " from file: '" + fromFile + "'");
                }
                return fromFile;
            } catch (Exception ignored) {
                return "";
            }
        }
        if (editor.DEBUG_RENDER_LOGS) {
            Log.i("ImeScanner", "getLineTextForImeScan: line " + line + " returning empty");
        }
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
        long startMs = android.os.SystemClock.uptimeMillis();
        CursorTarget base = clampLineCharToDocument(line, ch, raf);
        int curLine = base.line;
        int curChar = base.ch;
        int totalLines = editor.view.getLinesCount();
        if (totalLines <= 0) totalLines = 1;
        if (delta == 0) {
            logMoveCursor(startMs, line, ch, delta, base, totalLines, 0);
            return base;
        }

        if (delta < 0) {
            int remaining = -delta;
            int visited = 0;
            while (remaining > 0) {
                visited++;
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
            CursorTarget result = new CursorTarget(curLine, curChar);
            logMoveCursor(startMs, line, ch, delta, result, totalLines, visited);
            return result;
        }

        int remaining = delta;
        int visited = 0;
        while (remaining > 0) {
            visited++;
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
        CursorTarget result = new CursorTarget(curLine, curChar);
        logMoveCursor(startMs, line, ch, delta, result, totalLines, visited);
        return result;
    }

    private void logMoveCursor(
            long startMs,
            int line,
            int ch,
            int delta,
            CursorTarget result,
            int totalLines,
            int visitedLines) {
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "scanner.move total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " from="
                        + line
                        + ":"
                        + ch
                        + " delta="
                        + delta
                        + " to="
                        + result.line
                        + ":"
                        + result.ch
                        + " visited="
                        + visitedLines
                        + " totalLines="
                        + totalLines
                        + " modified="
                        + editor.windowRender.modifiedLines.size());
    }

    public String buildRangeTextForIme(CursorTarget start, CursorTarget end, @Nullable RandomAccessFile raf) {
        FunctionLog.f("ImeScanner", "buildRangeTextForIme", start, end, raf);
        long startMs = android.os.SystemClock.uptimeMillis();
        int sL = start.line, sC = start.ch, eL = end.line, eC = end.ch;
        if (comparePos(sL, sC, eL, eC) > 0) {
            int tL = sL, tC = sC;
            sL = eL;
            sC = eC;
            eL = tL;
            eC = tC;
        }
        StringBuilder sb = new StringBuilder();
        int linesRead = 0;
        for (int line = sL; line <= eL; line++) {
            linesRead++;
            String ln = getLineTextForImeScan(line, raf);
            if (ln == null) ln = "";
            if (editor.DEBUG_RENDER_LOGS) {
                Log.i("ImeContext", "buildRangeTextForIme: line " + line + " = '" + ln + "' (modifiedLines.contains=" + editor.windowRender.modifiedLines.containsKey(line) + ")");
            }
            int from = (line == sL) ? Math.min(sC, ln.length()) : 0;
            int to = (line == eL) ? Math.min(eC, ln.length()) : ln.length();
            if (from < to) sb.append(ln, from, to);
            if (line < eL) sb.append('\n');
        }
        String result = sb.toString();
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "scanner.range total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " lines="
                        + linesRead
                        + " start="
                        + sL
                        + ":"
                        + sC
                        + " end="
                        + eL
                        + ":"
                        + eC
                        + " len="
                        + result.length()
                        + " modified="
                        + editor.windowRender.modifiedLines.size()
                        + " raf="
                        + (raf != null));
        return result;
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
