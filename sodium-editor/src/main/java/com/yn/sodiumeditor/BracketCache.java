package com.yn.sodiumeditor;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches bracket and quote positions for fast fold detection and bracket matching.
 * This avoids re-scanning the entire file on every edit.
 */
public class BracketCache {

    // Bracket types
    public static final char BRACKET_CURLY_OPEN = '{';
    public static final char BRACKET_CURLY_CLOSE = '}';
    public static final char BRACKET_PAREN_OPEN = '(';
    public static final char BRACKET_PAREN_CLOSE = ')';
    public static final char BRACKET_SQUARE_OPEN = '[';
    public static final char BRACKET_SQUARE_CLOSE = ']';
    public static final char QUOTE_DOUBLE = '"';
    public static final char QUOTE_SINGLE = '\'';
    public static final char QUOTE_BACKTICK = '`';
    public static final char ESCAPE = '\\';

    // Cached data per line
    public static class LineBracketInfo {
        public final int line;
        public final List<BracketPosition> brackets;
        public final List<QuotePosition> quotes;
        public boolean isInBlockComment;
        public int stringState;

        public LineBracketInfo(int line) {
            this.line = line;
            this.brackets = new ArrayList<>();
            this.quotes = new ArrayList<>();
        }

        public void clear() {
            brackets.clear();
            quotes.clear();
        }
    }

    public static class BracketPosition {
        public final int line;
        public final int column;
        public final char bracket;
        public boolean isOpening;
        public int matchingLine = -1;
        public int matchingColumn = -1;

        public BracketPosition(int line, int column, char bracket) {
            this.line = line;
            this.column = column;
            this.bracket = bracket;
            this.isOpening = isOpeningBracket(bracket);
        }

        public static boolean isOpeningBracket(char c) {
            return c == BRACKET_CURLY_OPEN || c == BRACKET_PAREN_OPEN || c == BRACKET_SQUARE_OPEN;
        }

        public static boolean isClosingBracket(char c) {
            return c == BRACKET_CURLY_CLOSE || c == BRACKET_PAREN_CLOSE || c == BRACKET_SQUARE_CLOSE;
        }

        public static char getMatchingBracket(char c) {
            switch (c) {
                case BRACKET_CURLY_OPEN: return BRACKET_CURLY_CLOSE;
                case BRACKET_CURLY_CLOSE: return BRACKET_CURLY_OPEN;
                case BRACKET_PAREN_OPEN: return BRACKET_PAREN_CLOSE;
                case BRACKET_PAREN_CLOSE: return BRACKET_PAREN_OPEN;
                case BRACKET_SQUARE_OPEN: return BRACKET_SQUARE_CLOSE;
                case BRACKET_SQUARE_CLOSE: return BRACKET_SQUARE_OPEN;
                default: return c;
            }
        }
    }

    public static class QuotePosition {
        public final int line;
        public final int startColumn;
        public final int endColumn;
        public final char quoteChar;
        public final boolean isMultiline;

        public QuotePosition(int line, int startColumn, int endColumn, char quoteChar, boolean isMultiline) {
            this.line = line;
            this.startColumn = startColumn;
            this.endColumn = endColumn;
            this.quoteChar = quoteChar;
            this.isMultiline = isMultiline;
        }
    }

    private final SodiumEditor editor;
    private final Map<Integer, LineBracketInfo> lineCache = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private volatile boolean isScanning = false;
    private volatile int scanToken = 0;
    private volatile int cacheVersion = 0;

    public BracketCache(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Pre-scan the entire file for brackets and quotes.
     * Should be called in background thread BEFORE showing the file.
     * Reads directly from file, not from window buffer.
     * Blocks until complete.
     */
    public void scanFileAsync() {
        final int myToken = ++scanToken;
        isScanning = true;
        cacheVersion++;

        editor.ioHandler.post(() -> {
            if (myToken != scanToken) return;

            Map<Integer, LineBracketInfo> newCache = new HashMap<>();
            
            // Read directly from file using line offsets
            if (editor.isIndexReady && editor.sourceFile != null) {
                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(editor.sourceFile, "r");
                    
                    boolean inBlockComment = false;
                    int stringState = 0;
                    int totalLines = editor.lineOffsets.length;

                    for (int lineNum = 0; lineNum < totalLines && myToken == scanToken; lineNum++) {
                        long offset = editor.lineOffsets[lineNum];
                        raf.seek(offset);
                        String line = readLine(raf);
                        if (line == null) line = "";

                        LineBracketInfo info = parseLine(lineNum, line, inBlockComment, stringState);
                        newCache.put(lineNum, info);

                        inBlockComment = info.isInBlockComment;
                        stringState = info.stringState;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (raf != null) {
                        try { raf.close(); } catch (Exception ignored) {}
                    }
                }
            }

            if (myToken == scanToken && !newCache.isEmpty()) {
                // Rebuild folds in background, not on UI thread
                rebuildFoldRangesInBg(newCache);
                
                final int finalVersion = cacheVersion;
                editor.caret.mainHandler.post(() -> {
                    if (scanToken == myToken) {
                        lineCache.clear();
                        lineCache.putAll(newCache);
                        isScanning = false;
                        editor.invalidate();
                    }
                });
            } else {
                isScanning = false;
            }
        });
    }

    /**
     * Rebuild fold ranges in background thread.
     */
    private void rebuildFoldRangesInBg(Map<Integer, LineBracketInfo> cache) {
        editor.codeFold.foldRanges.clear();
        
        for (Map.Entry<Integer, LineBracketInfo> entry : cache.entrySet()) {
            int lineNum = entry.getKey();
            LineBracketInfo info = entry.getValue();
            
            for (BracketPosition bp : info.brackets) {
                if (bp.isOpening && !isInStringOrCommentQuick(info, bp.column)) {
                    BracketPosition match = findMatchingBracketQuick(cache, bp);
                    if (match != null && match.line > bp.line) {
                        CodeFold.FoldRange range = new CodeFold.FoldRange(
                            bp.line, match.line, bp.column, bp.bracket, match.bracket, false, false
                        );
                        editor.codeFold.foldRanges.put(bp.line, range);
                    }
                }
            }
        }
        
        editor.codeFold.foldIntervalsDirty = true;
    }

    /**
     * Quick check if column is in string/comment using pre-parsed info.
     */
    private boolean isInStringOrCommentQuick(LineBracketInfo info, int column) {
        for (QuotePosition quote : info.quotes) {
            if (column >= quote.startColumn && column <= quote.endColumn) {
                return true;
            }
        }
        return info.isInBlockComment;
    }

    /**
     * Find matching bracket using cached data.
     */
    @Nullable
    private BracketPosition findMatchingBracketQuick(Map<Integer, LineBracketInfo> cache, BracketPosition open) {
        if (!open.isOpening) return null;

        char closeChar = BracketPosition.getMatchingBracket(open.bracket);
        int depth = 1;
        int line = open.line;
        int col = open.column + 1;

        while (line < cache.size()) {
            LineBracketInfo info = cache.get(line);
            if (info == null) break;

            for (BracketPosition bp : info.brackets) {
                if (bp.line == line && bp.column >= col) {
                    if (!isInStringOrCommentQuick(info, bp.column)) {
                        if (bp.bracket == open.bracket && bp.isOpening) {
                            depth++;
                        } else if (bp.bracket == closeChar && !bp.isOpening) {
                            depth--;
                            if (depth == 0) {
                                return bp;
                            }
                        }
                    }
                }
            }

            line++;
            col = 0;
        }

        return null;
    }

    private String readLine(RandomAccessFile raf) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = raf.read()) != -1 && b != '\n') {
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }

    /**
     * Parse a single line for brackets and quotes.
     */
    public LineBracketInfo parseLine(int lineNum, String line, boolean startInBlockComment, int startStringState) {
        LineBracketInfo info = new LineBracketInfo(lineNum);
        info.isInBlockComment = startInBlockComment;
        info.stringState = startStringState;

        int len = line.length();
        int i = 0;

        while (i < len) {
            char c = line.charAt(i);

            // Check for escape
            if (c == ESCAPE) {
                i += 2; // Skip escaped character
                continue;
            }

            // Check for block comment start
            if (!info.isInBlockComment && i + 1 < len && c == '/' && line.charAt(i + 1) == '*') {
                info.isInBlockComment = true;
                i += 2;
                continue;
            }

            // Check for block comment end
            if (info.isInBlockComment) {
                if (i + 1 < len && c == '*' && line.charAt(i + 1) == '/') {
                    info.isInBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            // Check for string start
            if (info.stringState == 0 && isQuoteChar(c)) {
                int startCol = i;
                char quoteChar = c;
                boolean isTriple = i + 2 < len && line.charAt(i + 1) == c && line.charAt(i + 2) == c;
                
                if (isTriple) {
                    // Find triple quote end
                    int end = i + 3;
                    while (end < len) {
                        if (line.charAt(end) == c && end + 2 < len && 
                            line.charAt(end + 1) == c && line.charAt(end + 2) == c) {
                            info.quotes.add(new QuotePosition(lineNum, startCol, end + 2, quoteChar, true));
                            i = end + 3;
                            break;
                        }
                        end++;
                    }
                    if (end >= len) {
                        // Multiline string continues
                        info.quotes.add(new QuotePosition(lineNum, startCol, len - 1, quoteChar, true));
                        info.stringState = getStringState(quoteChar, true);
                        i = len;
                    }
                    continue;
                } else {
                    // Find single quote end
                    int end = i + 1;
                    while (end < len) {
                        if (line.charAt(end) == quoteChar && !isEscaped(line, end)) {
                            info.quotes.add(new QuotePosition(lineNum, startCol, end, quoteChar, false));
                            i = end + 1;
                            break;
                        }
                        end++;
                    }
                    if (end >= len) {
                        // String continues to next line
                        info.quotes.add(new QuotePosition(lineNum, startCol, len - 1, quoteChar, false));
                        info.stringState = getStringState(quoteChar, false);
                        i = len;
                    }
                    continue;
                }
            }

            // If inside multiline string, skip
            if (info.stringState != 0) {
                i++;
                continue;
            }

            // Check for line comment
            if (i + 1 < len && c == '/' && line.charAt(i + 1) == '/') {
                break; // Rest of line is comment
            }

            // Check for brackets
            if (BracketPosition.isOpeningBracket(c) || BracketPosition.isClosingBracket(c)) {
                info.brackets.add(new BracketPosition(lineNum, i, c));
            }

            i++;
        }

        return info;
    }

    /**
     * Invalidate cache for a specific line range.
     */
    public void invalidateLines(int startLine, int endLine) {
        for (int i = startLine; i <= endLine; i++) {
            lineCache.remove(i);
        }
        // Also invalidate any folds that might be affected
        if (editor.codeFold.isCodeFoldingEnabled) {
            editor.codeFold.foldIntervalsDirty = true;
        }
    }

    /**
     * Get bracket info for a line, parsing it if not cached.
     * Reads directly from file if not in window buffer.
     */
    public LineBracketInfo getLineInfo(int lineNum) {
        LineBracketInfo info = lineCache.get(lineNum);
        if (info != null) return info;

        // Try to read directly from file using line offsets
        String line = null;
        if (editor.isIndexReady && editor.sourceFile != null) {
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(editor.sourceFile, "r");
                if (lineNum >= 0 && lineNum < editor.lineOffsets.length) {
                    raf.seek(editor.lineOffsets[lineNum]);
                    line = readLine(raf);
                }
            } catch (Exception ignored) {
            } finally {
                if (raf != null) {
                    try { raf.close(); } catch (Exception ignored) {}
                }
            }
        }
        
        // Fallback to window buffer
        if (line == null) {
            line = editor.getLineTextForRender(lineNum);
        }
        if (line == null) line = "";

        // Get previous line's state by reading from file or cache
        boolean inBlockComment = false;
        int stringState = 0;
        if (lineNum > 0) {
            LineBracketInfo prevInfo = getLineInfo(lineNum - 1);
            inBlockComment = prevInfo.isInBlockComment;
            stringState = prevInfo.stringState;
        }

        info = parseLine(lineNum, line, inBlockComment, stringState);
        lineCache.put(lineNum, info);
        return info;
    }

    /**
     * Check if a position is inside a string or comment.
     */
    public boolean isInStringOrComment(int line, int column) {
        LineBracketInfo info = getLineInfo(line);
        
        // Check quotes
        for (QuotePosition quote : info.quotes) {
            if (column >= quote.startColumn && column <= quote.endColumn) {
                return true;
            }
        }

        // Check block comment
        if (info.isInBlockComment) return true;

        return false;
    }

    /**
     * Find opening brackets in a line (not in strings/comments).
     */
    public List<BracketPosition> getOpeningBrackets(int lineNum) {
        LineBracketInfo info = getLineInfo(lineNum);
        List<BracketPosition> result = new ArrayList<>();
        for (BracketPosition bp : info.brackets) {
            if (bp.isOpening && !isInStringOrComment(lineNum, bp.column)) {
                result.add(bp);
            }
        }
        return result;
    }

    /**
     * Find the matching bracket for a given opening bracket.
     */
    @Nullable
    public BracketPosition findMatchingBracket(BracketPosition open) {
        if (!open.isOpening) return null;

        char closeChar = BracketPosition.getMatchingBracket(open.bracket);
        int depth = 1;
        
        // Start from next position
        int line = open.line;
        int col = open.column + 1;

        LineBracketInfo info = getLineInfo(line);
        
        while (line < editor.getLinesCount()) {
            // Get brackets from current line starting from col
            for (BracketPosition bp : info.brackets) {
                if (bp.line == line && bp.column >= col) {
                    if (!isInStringOrComment(bp.line, bp.column)) {
                        if (bp.bracket == open.bracket && bp.isOpening) {
                            depth++;
                        } else if (bp.bracket == closeChar && !bp.isOpening) {
                            depth--;
                            if (depth == 0) {
                                return bp;
                            }
                        }
                    }
                }
            }

            // Move to next line
            line++;
            col = 0;
            if (line < editor.getLinesCount()) {
                info = getLineInfo(line);
            }
        }

        return null;
    }

    private boolean isQuoteChar(char c) {
        return c == QUOTE_DOUBLE || c == QUOTE_SINGLE || c == QUOTE_BACKTICK;
    }

    private int getStringState(char quoteChar, boolean isTriple) {
        if (isTriple) {
            if (quoteChar == QUOTE_BACKTICK) return SodiumEditor.STRING_STATE_TRIPLE;
            return SodiumEditor.STRING_STATE_TRIPLE;
        }
        return 1; // Simple string state
    }

    private boolean isEscaped(String line, int index) {
        if (index <= 0) return false;
        int count = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return (count % 2) == 1;
    }

    /**
     * Clear the entire cache.
     */
    public void clear() {
        lineCache.clear();
        cacheVersion++;
    }

    /**
     * Check if currently scanning.
     */
    public boolean isScanning() {
        return isScanning;
    }
}
