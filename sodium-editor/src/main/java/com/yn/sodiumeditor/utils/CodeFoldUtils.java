package com.yn.sodiumeditor.utils;

import android.util.Log;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.CodeFold;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility methods for code folding: text retrieval, bracket/comment finding, fold range adjustment.
 */
public class CodeFoldUtils {

    private final SodiumEditor editor;

    public CodeFoldUtils(SodiumEditor editor) {
        this.editor = editor;
    }

    // ============================================================================
    // Fold Range Text Retrieval
    // ============================================================================

    /**
     * Get end line text for fold range.
     */
    public String getEndLineTextForFold(CodeFold.FoldRange range) {
        if (range == null) return null;
        synchronized (editor.textRender.modifiedLines) {
            String mod = editor.textRender.modifiedLines.get(range.endLine);
            if (mod != null) return mod;
        }
        String text = editor.textRender.getLineTextForRender(range.endLine);
        if (text != null) {
            if (text.length() > 0) {
                return text;
            }
            int winStart = editor.textRender.windowStartLine;
            int winEnd = winStart + editor.textRender.linesWindow.size();
            if (range.endLine >= winStart && range.endLine < winEnd) {
                return text;
            }
        }
        synchronized (editor.fileIO.directLineCache) {
            String cached = editor.fileIO.directLineCache.get(range.endLine);
            if (cached != null) return cached;
        }
        if (range.cachedEndLineText != null) return range.cachedEndLineText;

        if (editor.fileIO.sourceFile == null) return null;

        if (!editor.fileIO.isIndexReady) {
            String scannedText = readLineByScanningFile(range.endLine);
            if (scannedText != null) {
                range.cachedEndLineText = scannedText;
                synchronized (editor.fileIO.directLineCache) {
                    editor.fileIO.directLineCache.put(range.endLine, scannedText);
                }
                return scannedText;
            }
            return null;
        }

        RandomAccessFile raf = null;
        try {
            long offset;
            synchronized (editor.fileIO.lineOffsetsLock) {
                int offsetsLength = editor.fileIO.lineOffsets.length;
                if (range.endLine < 0 || range.endLine >= offsetsLength) {
                    if (editor.DEBUG_RENDER_LOGS) {
                        Log.d("SodiumRender", "getEndLineTextForFold line out of range endLine=" + range.endLine + " offsetsLength=" + offsetsLength);
                    }
                    return null;
                }
                offset = editor.fileIO.lineOffsets[range.endLine];
            }
            raf = new RandomAccessFile(editor.fileIO.sourceFile, "r");
            if (editor.DEBUG_RENDER_LOGS) {
                Log.d("SodiumRender", "getEndLineTextForFold reading endLine=" + range.endLine + " offset=" + offset);
            }
            text = editor.fileIO.readLineUtf8AtByte(raf, offset);
            if (text != null) {
                range.cachedEndLineText = text;
                synchronized (editor.fileIO.directLineCache) {
                    editor.fileIO.directLineCache.put(range.endLine, text);
                }
                if (editor.DEBUG_RENDER_LOGS) {
                    Log.d("SodiumRender", "getEndLineTextForFold success endLine=" + range.endLine + " textLen=" + text.length());
                }
            } else {
                if (editor.DEBUG_RENDER_LOGS) {
                    Log.d("SodiumRender", "getEndLineTextForFold returned null for endLine=" + range.endLine);
                }
            }
            range.cachedEndLineTextAttempted = true;
            return text;
        } catch (Exception e) {
            if (editor.DEBUG_RENDER_LOGS) {
                Log.d("SodiumRender", "getEndLineTextForFold failed endLine=" + range.endLine + " err=" + e.getMessage() + " " + android.util.Log.getStackTraceString(e));
            }
            range.cachedEndLineTextAttempted = true;
            return null;
        } finally {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Read a line by scanning the file sequentially (fallback when index is not ready).
     */
    public String readLineByScanningFile(int targetLine) {
        if (editor.fileIO.sourceFile == null || targetLine < 0) return null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(editor.fileIO.sourceFile, "r");
            raf.seek(0);
            int currentLine = 0;
            StringBuilder sb = new StringBuilder(256);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = raf.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    byte b = buffer[i];
                    if (b == '\n') {
                        if (currentLine == targetLine) {
                            return sb.toString();
                        }
                        currentLine++;
                        sb.setLength(0);
                    } else if (b != '\r') {
                        sb.append((char) b);
                    }
                }
            }
            if (currentLine == targetLine) {
                return sb.toString();
            }
            return null;
        } catch (Exception e) {
            if (editor.DEBUG_RENDER_LOGS) {
                Log.d("SodiumRender", "readLineByScanningFile failed line=" + targetLine + " err=" + e.getMessage());
            }
            return null;
        } finally {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ============================================================================
    // Bracket & Comment Finding
    // ============================================================================

    /**
     * Find block comment end in a line.
     */
    public int findBlockCommentEnd(String line, int startIndex) {
        int i = startIndex;
        while (i + 1 < line.length()) {
            if (line.charAt(i) == '*' && line.charAt(i + 1) == '/') {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Find closing bracket in a line.
     */
    public int findClosingBracketInLine(String line, int startChar, char openBracket, char closeBracket) {
        if (line == null) return -1;
        int i = Math.max(0, startChar);
        int depth = 1;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char quoteChar = 0;

        while (i < line.length()) {
            if (inLineComment) break;
            if (inBlockComment) {
                int end = findBlockCommentEnd(line, i);
                if (end < 0) break;
                i = end + 2;
                inBlockComment = false;
                continue;
            }
            if (quoteChar != 0) {
                char qc = line.charAt(i);
                if (qc == quoteChar && !isTokenEscaped(line, i)) {
                    quoteChar = 0;
                }
                i++;
                continue;
            }

            if (editor.highlite.isLineCommentStart(line, i)) {
                inLineComment = true;
                break;
            }
            if (line.charAt(i) == '#' && !isTokenEscaped(line, i)) {
                inLineComment = true;
                break;
            }
            if (editor.highlite.isBlockCommentsEnabled
                    && i + 1 < line.length()
                    && line.charAt(i) == '/'
                    && line.charAt(i + 1) == '*'
                    && !isTokenEscaped(line, i)) {
                inBlockComment = true;
                i += 2;
                continue;
            }

            char c = line.charAt(i);
            if ((c == '\'' || c == '"' || c == '`') && !isTokenEscaped(line, i)) {
                quoteChar = c;
                i++;
                continue;
            }

            if (c == openBracket && !isTokenEscaped(line, i)) {
                depth++;
            } else if (c == closeBracket && !isTokenEscaped(line, i)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private boolean isTokenEscaped(String line, int index) {
        if (index <= 0) return false;
        int count = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return (count % 2) != 0;
    }

    // ============================================================================
    // Fold Range Adjustment
    // ============================================================================

    /**
     * Adjust fold range indices after a line edit on the fold start line.
     */
    public void adjustFoldRangeForLineEdit(int line, int editIndex, int delta, int deleteLen) {
        CodeFold.FoldRange range = editor.codeFold.foldRanges.get(line);
        if (range == null) return;
        if (deleteLen > 0 && editIndex <= range.openCharIndex && (editIndex + deleteLen) > range.openCharIndex) {
            editor.codeFold.invalidateFoldRangeForLine(line);
            return;
        }
        int newOpen = range.openCharIndex;
        if (editIndex <= newOpen) newOpen = Math.max(0, newOpen + delta);
        int newClose = range.closeCharIndex;
        if (range.endLine == line && newClose >= 0 && editIndex <= newClose) {
            newClose = Math.max(0, newClose + delta);
        }
        CodeFold.FoldRange updated =
            new CodeFold.FoldRange(
                range.startLine,
                range.endLine,
                newOpen,
                range.openChar,
                range.closeChar,
                newClose,
                range.isBlockComment,
                range.isIndentFold);
        updated.collapsed = range.collapsed;
        editor.codeFold.foldRanges.put(range.startLine, updated);
        editor.codeFold.foldIntervalsDirty = true;
    }

    /**
     * Resolve close char index for a folded range on its end line.
     */
    public int resolveCloseCharIndex(CodeFold.FoldRange range, @Nullable String endLineText) {
        if (range == null || endLineText == null) return -1;
        if (range.isBlockComment) {
            int idx = range.closeCharIndex;
            if (idx < 0 || idx >= endLineText.length()) {
                idx = findBlockCommentEnd(endLineText, Math.max(0, range.openCharIndex + 2));
            }
            return idx;
        }
        if (range.closeCharIndex >= 0 && range.closeCharIndex < endLineText.length()) {
            return range.closeCharIndex;
        }
        return findClosingBracketInLine(
            endLineText,
            Math.max(0, range.openCharIndex + 1),
            range.openChar,
            range.closeChar);
    }

    /**
     * Check if local X position hits a fold placeholder
     */
    public boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
        if (!editor.codeFold.isCodeFoldingEnabled) return false;
        CodeFold.FoldRange range = editor.codeFold.foldRanges.get(globalLine);
        if (range == null || !range.collapsed) return false;
        if (line == null) line = "";

        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }
        float xStart = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
        float left = xStart;
        float right = xStart + placeholderWidth;
        return localX >= left && localX <= right;
    }

    /**
     * Get placeholder bounds for a folded line.
     * outBounds[0]=left, outBounds[1]=right
     */
    public boolean getFoldPlaceholderBounds(int globalLine, String line, float[] outBounds) {
        if (outBounds == null || outBounds.length < 2) return false;
        if (!editor.codeFold.isCodeFoldingEnabled) return false;
        CodeFold.FoldRange range = editor.codeFold.foldRanges.get(globalLine);
        if (range == null || !range.collapsed) return false;
        if (line == null) line = "";

        int prefixEnd;
        if (range.isBlockComment) {
            prefixEnd = Math.min(range.openCharIndex + 2, line.length());
        } else if (range.isIndentFold) {
            prefixEnd = line.length();
        } else {
            prefixEnd = Math.min(range.openCharIndex + 1, line.length());
        }
        float xStart = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
        float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
        outBounds[0] = xStart;
        outBounds[1] = xStart + placeholderWidth;
        return true;
    }
}
