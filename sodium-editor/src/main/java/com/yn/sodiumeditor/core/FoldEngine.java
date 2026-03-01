package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.state.FoldMatch;
import com.yn.sodiumeditor.state.FoldRange;
import com.yn.sodiumeditor.state.FoldToken;
import com.yn.sodiumeditor.state.HighlightLineState;
import com.yn.sodiumeditor.state.HighlightState;

import java.io.RandomAccessFile;

/**
 * Engine class for code folding.
 * Handles finding fold ranges through file scanning.
 */
public class FoldEngine {

    private final SodiumEditorView view;

    public FoldEngine(SodiumEditorView view) {
        this.view = view;
    }

    public FoldRange findFoldRangeForLine(int line) {
        if (!view.foldState.isCodeFoldingEnabled()) return null;
        if (line < 0) return null;

        RandomAccessFile raf = null;
        try {
            if (view.sourceFile != null && view.isIndexReady) {
                raf = new RandomAccessFile(view.sourceFile, "r");
            }

            String ln = getLineTextForFoldScan(line, raf);
            if (ln == null) return null;

            HighlightLineState startState = view.highlightState.getLineStateAtStart(line);
            boolean inBlockComment =
                    startState.inBlockComment && view.isBlockCommentsEnabledForBracket();
            int stringState = startState.stringState;
            if (!view.isBlockCommentsEnabledForBracket()) inBlockComment = false;
            if (!view.isMultiLineStringsEnabled
                    && stringState != HighlightState.STRING_STATE_TRIPLE) stringState = 0;
            if (!view.isBacktickStringsEnabled
                    && stringState == HighlightState.STRING_STATE_BACKTICK) stringState = 0;
            if (!view.isTripleQuoteStringsEnabled
                    && stringState == HighlightState.STRING_STATE_TRIPLE) stringState = 0;

            if (inBlockComment || stringState != 0) return null;

            if (view.isIndentationBlocksEnabled && isIndentFoldCandidate(ln)) {
                FoldRange indentRange = findIndentFoldRangeForLine(line, raf);
                if (indentRange != null) return indentRange;
            }

            int scanIndex = 0;
            while (true) {
                FoldToken token = findFoldTokenInLine(ln, scanIndex);
                if (token == null) return null;

                if (token.isBlockComment) {
                    int endLine = findBlockCommentEndLine(line, token.index, raf);
                    if (endLine > line) {
                        return new FoldRange(line, endLine, token.index, '/', '/', true, false);
                    }
                    scanIndex = token.index + 2;
                    continue;
                }

                FoldMatch match = findMatchingBracketFrom(line, token.index, token.openChar, raf);
                if (match != null && match.endLine > line) {
                    return new FoldRange(
                            line, match.endLine, token.index, token.openChar, match.closeChar, false, false);
                }

                scanIndex = token.index + 1;
                if (scanIndex >= ln.length()) return null;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String getLineTextForFoldScan(int line, @Nullable RandomAccessFile raf) {
        if (line < 0) return null;
        String mod = view.modifiedLines.get(line);
        if (mod != null) return mod;
        if (line >= view.windowStartLine && line < view.windowStartLine + view.linesWindow.size()) {
            String text = view.getLineFromWindowLocal(line - view.windowStartLine);
            return (text != null) ? text : "";
        }
        if (raf != null && view.isIndexReady) {
            long offset;
            synchronized (view.lineOffsetsLock) {
                if (line < 0 || line >= view.lineOffsets.length) return null;
                offset = view.lineOffsets[line];
            }
            try {
                return view.readLineUtf8AtByte(raf, offset);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public FoldRange findIndentFoldRangeForLine(int line, @Nullable RandomAccessFile raf) {
        if (!view.isIndentationBlocksEnabled) return null;
        String ln = getLineTextForFoldScan(line, raf);
        if (ln == null) return null;
        String trimmed = rstripWhitespace(ln);
        if (trimmed.isEmpty() || !trimmed.endsWith(":")) return null;

        int baseIndent = view.getIndentWidth(ln);
        int totalLines = view.getLinesCount();
        if (totalLines <= 0)
            totalLines =
                    view.wrapWordState.isWordWrapEnabled
                            ? view.wrapWordMapper.getTotalVisualLineCount(view, view.getVisibleLineCount())
                            : view.getVisibleLineCount();
        int endLine = line + 1;
        int maxScan = Math.min(line + 500, totalLines);
        while (endLine < maxScan) {
            String nextLn = getLineTextForFoldScan(endLine, raf);
            if (nextLn == null) break;
            if (nextLn.trim().isEmpty()) {
                endLine++;
                continue;
            }
            int nextIndent = view.getIndentWidth(nextLn);
            if (nextIndent <= baseIndent) break;
            endLine++;
        }
        endLine = Math.max(line + 1, endLine - 1);
        if (endLine <= line) return null;
        return new FoldRange(line, endLine, trimmed.length() - 1, ' ', ' ', false, true);
    }

    private static String rstripWhitespace(String text) {
        if (text == null || text.isEmpty()) return "";
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c != ' ' && c != '\t') break;
            end--;
        }
        return (end <= 0) ? "" : text.substring(0, end);
    }

    private boolean isIndentFoldCandidate(String line) {
        if (line == null || line.isEmpty()) return false;
        String trimmed = rstripWhitespace(line);
        return !trimmed.isEmpty() && trimmed.endsWith(":");
    }

    public FoldToken findFoldTokenInLine(String line, int fromIndex) {
        if (line == null || line.isEmpty() || fromIndex >= line.length()) return null;

        int blockStart = -1;
        int braceStart = -1;
        int parenStart = -1;
        int bracketStart = -1;

        for (int i = fromIndex; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                if (blockStart < 0 || i < blockStart) blockStart = i;
            } else if (c == '{' && braceStart < 0) {
                braceStart = i;
            } else if (c == '(' && parenStart < 0) {
                parenStart = i;
            } else if (c == '[' && bracketStart < 0) {
                bracketStart = i;
            }
        }

        if (blockStart >= 0) {
            if (braceStart < 0 || blockStart < braceStart) {
                if (parenStart < 0 || blockStart < parenStart) {
                    if (bracketStart < 0 || blockStart < bracketStart) {
                        return new FoldToken(blockStart, '/', true);
                    }
                }
            }
        }

        if (braceStart >= 0) {
            if (parenStart < 0 || braceStart < parenStart) {
                if (bracketStart < 0 || braceStart < bracketStart) {
                    return new FoldToken(braceStart, '{', false);
                }
            }
        }

        if (parenStart >= 0) {
            if (bracketStart < 0 || parenStart < bracketStart) {
                return new FoldToken(parenStart, '(', false);
            }
        }

        if (bracketStart >= 0) {
            return new FoldToken(bracketStart, '[', false);
        }

        return null;
    }

    public int findBlockCommentEndLine(int startLine, int startIndex, @Nullable RandomAccessFile raf) {
        String ln = getLineTextForFoldScan(startLine, raf);
        if (ln == null) return startLine;
        int endIdx = ln.indexOf("*/", startIndex + 2);
        if (endIdx >= 0) return startLine;

        int line = startLine + 1;
        int maxScan = startLine + 500;
        while (line < maxScan) {
            ln = getLineTextForFoldScan(line, raf);
            if (ln == null) break;
            endIdx = ln.indexOf("*/");
            if (endIdx >= 0) return line;
            line++;
        }
        return startLine;
    }

    public FoldMatch findMatchingBracketFrom(
            int startLine, int startIndex, char openChar, @Nullable RandomAccessFile raf) {
        char closeChar = getCloseChar(openChar);
        int depth = 1;
        String ln = getLineTextForFoldScan(startLine, raf);
        if (ln == null) return null;

        for (int i = startIndex + 1; i < ln.length(); i++) {
            char c = ln.charAt(i);
            if (c == openChar) depth++;
            else if (c == closeChar) {
                depth--;
                if (depth == 0) return new FoldMatch(startLine, closeChar);
            }
        }

        int line = startLine + 1;
        int maxScan = startLine + 500;
        while (line < maxScan) {
            ln = getLineTextForFoldScan(line, raf);
            if (ln == null) break;
            for (int i = 0; i < ln.length(); i++) {
                char c = ln.charAt(i);
                if (c == openChar) depth++;
                else if (c == closeChar) {
                    depth--;
                    if (depth == 0) return new FoldMatch(line, closeChar);
                }
            }
            line++;
        }
        return null;
    }

    private static char getCloseChar(char openChar) {
        switch (openChar) {
            case '{': return '}';
            case '(': return ')';
            case '[': return ']';
            default: return openChar;
        }
    }
}
