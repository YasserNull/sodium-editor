package com.yn.sodiumeditor.core.fold;

import com.yn.sodiumeditor.SodiumEditor;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Detects and analyzes fold ranges in code (brackets, block comments, indent-based folds).
 */
public class CodeFoldDetector {

    private final SodiumEditor editor;

    // Reusable bracket stack — avoids per-call ArrayList allocation
    private final char[] bracketStack = new char[64];
    private final int[] bracketStackIdx = new int[64];
    private int bracketStackTop = 0;

    public CodeFoldDetector(SodiumEditor editor) {
        FunctionLog.f("CodeFoldDetector", "CodeFoldDetector", editor);
        this.editor = editor;
    }

    // ============================================================================
    // Public API - Fold Detection
    // ============================================================================

    /**
     * Find a fold range for a line.
     */
    public CodeFold.FoldRange findFoldRangeForLine(int line) {
        FunctionLog.f("CodeFoldDetector", "findFoldRangeForLine", line);
        if (!editor.codeFold.isCodeFoldingEnabled) return null;
        if (line < 0) return null;

        String ln = editor.windowRender.getLineTextForRender(line);
        if (ln == null) ln = "";

        RandomAccessFile raf = null;
        if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null) {
            try {
                raf = new RandomAccessFile(editor.fileIO.sourceFile, "r");
            } catch (Exception ignored) {
                raf = null;
            }
        }

        if (editor.indentGuides.isIndentationBlocksEnabled && isIndentFoldCandidate(ln)) {
            CodeFold.FoldRange indentRange = findIndentFoldRangeForLine(line, raf);
            if (indentRange != null) {
                if (raf != null) {
                    try { raf.close(); } catch (Exception ignored) {}
                }
                return indentRange;
            }
        }

        FoldToken token = findFoldTokenInLine(ln, 0);
        if (token == null) {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
            return null;
        }
        if (token.isBlockComment) {
            int endLine = findBlockCommentEndLine(line, token.index, raf);
            int endIdx = -1;
            if (endLine == line) {
                endIdx = editor.codeFold.utils.findBlockCommentEnd(ln, token.index + 2);
            }
            if (endLine > line) {
                if (raf != null) {
                    try { raf.close(); } catch (Exception ignored) {}
                }
                return new CodeFold.FoldRange(line, endLine, token.index, token.openChar, token.openChar, endIdx, true, false);
            }
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
            return null;
        }
        FoldMatch match = findMatchingBracketFrom(line, token.index, token.openChar, raf);
        if (match != null && match.endLine > line) {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
            return new CodeFold.FoldRange(line, match.endLine, token.index, token.openChar, match.closeChar, match.endChar, false, false);
        }
        if (raf != null) {
            try { raf.close(); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Check if a line is an indent fold candidate.
     */
    public boolean isIndentFoldCandidate(String line) {
        FunctionLog.f("CodeFoldDetector", "isIndentFoldCandidate", line);
        if (line == null || line.isEmpty()) return false;
        String trimmed = rstripWhitespace(line);
        if (trimmed.isEmpty() || !trimmed.endsWith(":")) return false;
        if (trimmed.length() < 2) return false;
        char beforeColon = trimmed.charAt(trimmed.length() - 2);
        if (beforeColon == '}' || beforeColon == ']' || beforeColon == ')') return false;
        return true;
    }

    // ============================================================================
    // Internal Helpers - Line Text Retrieval
    // ============================================================================

    String getLineTextForFoldScan(int line, @Nullable RandomAccessFile raf) {
        FunctionLog.f("CodeFoldDetector", "getLineTextForFoldScan", line, raf);
        if (line < 0) return null;
        String mod = editor.windowRender.modifiedLines.get(line);
        if (mod != null) return mod;
        if (line >= editor.windowRender.windowStartLine && line < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
            String text = editor.windowRender.getLineFromWindowLocal(line - editor.windowRender.windowStartLine);
            return (text != null) ? text : "";
        }
        if (raf != null && editor.fileIO.isIndexReady) {
            long offset;
            synchronized (editor.fileIO.lineOffsetsLock) {
                if (line < 0 || line >= editor.fileIO.lineOffsets.length) return null;
                offset = editor.fileIO.lineOffsets[line];
            }
            try {
                return readLineUtf8AtByte(raf, offset);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private CodeFold.FoldRange findIndentFoldRangeForLine(int line, @Nullable RandomAccessFile raf) {
        FunctionLog.f("CodeFoldDetector", "findIndentFoldRangeForLine", line, raf);
        if (!editor.indentGuides.isIndentationBlocksEnabled) return null;
        String ln = getLineTextForFoldScan(line, raf);
        if (ln == null) return null;
        String trimmed = rstripWhitespace(ln);
        if (trimmed.isEmpty() || !trimmed.endsWith(":")) return null;

        int baseIndent = getIndentWidth(ln);
        int totalLines = editor.view.getLinesCount();
        if (totalLines <= 0) totalLines = Math.max(line + 1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());

        int endLine = -1;
        int scanEnd = Math.min(totalLines, line + CodeFold.INDENT_FOLD_SCAN_LIMIT);
        for (int i = line + 1; i < scanEnd; i++) {
            String next = getLineTextForFoldScan(i, raf);
            if (next == null) break;
            String nextTrimmed = rstripWhitespace(next);
            if (nextTrimmed.isEmpty()) continue;
            int indent = getIndentWidth(next);
            if (indent <= baseIndent) {
                endLine = i - 1;
                break;
            }
            endLine = i;
        }

        if (endLine > line) {
            int openIdx = Math.max(0, trimmed.length() - 1);
            return new CodeFold.FoldRange(line, endLine, openIdx, ':', ':', -1, false, true);
        }
        return null;
    }

    private FoldToken findFoldTokenInLine(String line, int startIndex) {
        FunctionLog.f("CodeFoldDetector", "findFoldTokenInLine", line, startIndex);
        return findLastUnclosedFoldTokenInLine(line, startIndex);
    }

    private FoldToken findLastUnclosedFoldTokenInLine(String line, int startIndex) {
        FunctionLog.f("CodeFoldDetector", "findLastUnclosedFoldTokenInLine", line, startIndex);
        if (line == null || line.isEmpty()) return null;
        int len = line.length();
        int i = Math.max(0, startIndex);
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char quoteChar = 0;
        bracketStackTop = 0;

        while (i < len) {
            if (inLineComment) break;

            if (inBlockComment) {
                int end = editor.codeFold.utils.findBlockCommentEnd(line, i);
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
            if (line.charAt(i) == '#' && !editor.highlite.isEscaped(line, i)) {
                inLineComment = true;
                break;
            }

            if (editor.highlite.isBlockCommentsEnabled
                    && i + 1 < len
                    && line.charAt(i) == '/'
                    && line.charAt(i + 1) == '*'
                    && !isTokenEscaped(line, i)) {
                int end = editor.codeFold.utils.findBlockCommentEnd(line, i + 2);
                if (end < 0) break;
                i = end + 2;
                continue;
            }

            if (editor.highlite.isTripleQuoteStringsEnabled && editor.highlite.isTripleQuoteStart(line, i) && !editor.highlite.isEscaped(line, i)) {
                int end = editor.highlite.findTripleQuoteEnd(line, i + 3);
                if (end < 0) break;
                i = end + 3;
                continue;
            }

            char c = line.charAt(i);
            if ((c == '\'' || c == '"' || c == '`') && !isTokenEscaped(line, i)) {
                quoteChar = c;
                i++;
                continue;
            }

            if (!editor.highlite.isEscaped(line, i)) {
                if (c == '{' || c == '(' || c == '[') {
                    if (bracketStackTop < bracketStack.length) {
                        bracketStack[bracketStackTop] = c;
                        bracketStackIdx[bracketStackTop] = i;
                        bracketStackTop++;
                    }
                } else if (c == '}' || c == ')' || c == ']') {
                    if (bracketStackTop > 0) {
                        char open = bracketStack[bracketStackTop - 1];
                        if ((open == '{' && c == '}')
                                || (open == '(' && c == ')')
                                || (open == '[' && c == ']')) {
                            bracketStackTop--;
                        }
                    }
                }
            }
            i++;
        }

        if (bracketStackTop > 0) {
            int idx = bracketStackIdx[bracketStackTop - 1];
            return new FoldToken(idx, false, bracketStack[bracketStackTop - 1]);
        }
        return null;
    }

    private int findBlockCommentEndLine(int startLine, int startChar, @Nullable RandomAccessFile raf) {
        FunctionLog.f("CodeFoldDetector", "findBlockCommentEndLine", startLine, startChar, raf);
        String line = getLineTextForFoldScan(startLine, raf);
        if (line == null) return startLine;
        int end = editor.codeFold.utils.findBlockCommentEnd(line, startChar);
        if (end >= 0) return startLine;

        int totalLines = editor.view.getLinesCount();
        if (totalLines <= 0) totalLines = Math.max(startLine + 1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());

        for (int i = startLine + 1; i < totalLines; i++) {
            String ln = getLineTextForFoldScan(i, raf);
            if (ln == null) break;
            end = editor.codeFold.utils.findBlockCommentEnd(ln, 0);
            if (end >= 0) return i;
        }
        return startLine;
    }

    private FoldMatch findMatchingBracketFrom(int startLine, int startChar, char openBracket, @Nullable RandomAccessFile raf) {
        FunctionLog.f("CodeFoldDetector", "findMatchingBracketFrom", startLine, startChar, openBracket, raf);
        char closeBracket = getClosingBracket(openBracket);
        int depth = 1;
        String line = getLineTextForFoldScan(startLine, raf);
        if (line == null) return null;

        int i = startChar + 1;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char quoteChar = 0;

        while (true) {
            while (i < line.length()) {
                if (inLineComment) {
                    i = line.length();
                    break;
                }
                if (inBlockComment) {
                    int end = editor.codeFold.utils.findBlockCommentEnd(line, i);
                    if (end < 0) {
                        i = line.length();
                        break;
                    }
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
                    continue;
                }
                if (line.charAt(i) == '#' && !editor.highlite.isEscaped(line, i)) {
                    inLineComment = true;
                    continue;
                }
                if (editor.highlite.isBlockCommentsEnabled && i + 1 < line.length() && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
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

                if (c == openBracket && !editor.highlite.isEscaped(line, i)) {
                    depth++;
                } else if (c == closeBracket && !editor.highlite.isEscaped(line, i)) {
                    depth--;
                    if (depth == 0) {
                        return new FoldMatch(startLine, i, closeBracket);
                    }
                }
                i++;
            }

            startLine++;
            line = getLineTextForFoldScan(startLine, raf);
            if (line == null) break;
            i = 0;
            inLineComment = false;
        }
        return null;
    }

    private char getClosingBracket(char open) {
        FunctionLog.f("CodeFoldDetector", "getClosingBracket", open);
        switch (open) {
            case '(': return ')';
            case '[': return ']';
            case '{': return '}';
            default: return open;
        }
    }

    private int getIndentWidth(String line) {
        FunctionLog.f("CodeFoldDetector", "getIndentWidth", line);
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    private String rstripWhitespace(String line) {
        FunctionLog.f("CodeFoldDetector", "rstripWhitespace", line);
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }

    private boolean isTokenEscaped(String line, int index) {
        FunctionLog.f("CodeFoldDetector", "isTokenEscaped", line, index);
        if (index <= 0) return false;
        int count = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return (count % 2) != 0;
    }

    private String readLineUtf8AtByte(RandomAccessFile raf, long offset) throws Exception {
        FunctionLog.f("CodeFoldDetector", "readLineUtf8AtByte", raf, offset);
        raf.seek(offset);
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = raf.read()) != -1 && b != '\n') {
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }

    boolean shouldShowFoldMarkerFromLine(String line) {
        FunctionLog.f("CodeFoldDetector", "shouldShowFoldMarkerFromLine", line);
        if (line == null || line.isEmpty()) return false;
        return findLastUnclosedFoldTokenInLine(line, 0) != null;
    }

    /**
     * Potential fold start check.
     */
    public boolean isPotentialFoldStart(int line) {
        FunctionLog.f("CodeFoldDetector", "isPotentialFoldStart", line);
        String ln = editor.windowRender.getLineTextForRender(line);
        if (ln == null) return false;
        return shouldShowFoldMarkerFromLine(ln) || isIndentFoldCandidate(ln);
    }

    /**
     * Async detect fold range.
     */
    public void detectFoldRangeAsync(int line, com.yn.sodiumeditor.core.fold.CodeFoldDetector.OnFoldDetectedListener listener) {
        FunctionLog.f("CodeFoldDetector", "detectFoldRangeAsync", line, listener);
        editor.fileIO.ioHandler.post(() -> {
            CodeFold.FoldRange range = findFoldRangeForLine(line);
            editor.caret.mainHandler.post(() -> listener.onFoldDetected(range));
        });
    }

    public interface OnFoldDetectedListener {
        void onFoldDetected(@Nullable CodeFold.FoldRange range);
    }

    // --- FoldToken class ---
    static final class FoldToken {
        final int index;
        final boolean isBlockComment;
        final char openChar;

        FoldToken(int index, boolean isBlockComment, char openChar) {
            this.index = index;
            this.isBlockComment = isBlockComment;
            this.openChar = openChar;
        }
    }

    // --- FoldMatch class ---
    static final class FoldMatch {
        final int endLine;
        final int endChar;
        final char closeChar;

        FoldMatch(int endLine, int endChar, char closeChar) {
            this.endLine = endLine;
            this.endChar = endChar;
            this.closeChar = closeChar;
        }
    }
}
