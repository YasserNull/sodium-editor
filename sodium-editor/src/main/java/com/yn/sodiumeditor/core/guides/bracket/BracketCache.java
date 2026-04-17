package com.yn.sodiumeditor.core.guides.bracket; 

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.util.SparseArray;

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
    private final SparseArray<LineBracketInfo> lineCache = new SparseArray<>();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
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

        editor.fileIO.ioHandler.post(() -> {
            long startMs = android.os.SystemClock.uptimeMillis();
            if (myToken != scanToken) return;

            SparseArray<LineBracketInfo> newCache = new SparseArray<>();
            
            // Read sequentially using BufferedReader for speed
            if (editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile);
                     java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
                     java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {
                    
                    boolean inBlockComment = false;
                    int stringState = 0;
                    int lineNum = 0;
                    String line;

                    while ((line = reader.readLine()) != null && myToken == scanToken) {
                        LineBracketInfo info = parseLine(lineNum, line, inBlockComment, stringState);
                        newCache.put(lineNum, info);

                        inBlockComment = info.isInBlockComment;
                        stringState = info.stringState;
                        lineNum++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (myToken == scanToken && newCache.size() > 0) {
                // Rebuild folds in background, not on UI thread
                rebuildFoldRangesInBg(newCache);

                final int finalVersion = cacheVersion;
                editor.caret.mainHandler.post(() -> {
                    long dt = android.os.SystemClock.uptimeMillis() - startMs;
                    if (editor.DEBUG_RENDER_LOGS && dt > 8) {
                        android.util.Log.d("SodiumRender", "bracketScan dtMs=" + dt + " lines=" + newCache.size());
                    }
                    if (scanToken == myToken) {
                        lineCache.clear();
                        for (int i = 0; i < newCache.size(); i++) {
                            lineCache.put(newCache.keyAt(i), newCache.valueAt(i));
                        }
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
     * Rebuild fold ranges in background thread using efficient O(N) stack-based matching.
     */
    private void rebuildFoldRangesInBg(SparseArray<LineBracketInfo> cache) {
        editor.codeFold.foldRanges.clear();

        // Use stacks for each bracket type to match them in a single pass
        java.util.ArrayDeque<BracketPosition> curlyStack = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<BracketPosition> parenStack = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<BracketPosition> squareStack = new java.util.ArrayDeque<>();

        int totalLines = cache.size();
        for (int i = 0; i < totalLines; i++) {
            int lineNum = cache.keyAt(i);
            LineBracketInfo info = cache.valueAt(i);
            if (info == null) continue;
            
            for (BracketPosition bp : info.brackets) {
                if (isInStringOrCommentQuick(info, bp.column)) continue;
                
                if (bp.isOpening) {
                    if (bp.bracket == BRACKET_CURLY_OPEN) curlyStack.push(bp);
                    else if (bp.bracket == BRACKET_PAREN_OPEN) parenStack.push(bp);
                    else if (bp.bracket == BRACKET_SQUARE_OPEN) squareStack.push(bp);
                } else {
                    java.util.ArrayDeque<BracketPosition> stack = null;
                    if (bp.bracket == BRACKET_CURLY_CLOSE) stack = curlyStack;
                    else if (bp.bracket == BRACKET_PAREN_CLOSE) stack = parenStack;
                    else if (bp.bracket == BRACKET_SQUARE_CLOSE) stack = squareStack;
                    
                    if (stack != null && !stack.isEmpty()) {
                        BracketPosition open = stack.pop();
                        if (lineNum > open.line) {
                            CodeFold.FoldRange range = new CodeFold.FoldRange(
                                open.line, lineNum, open.column, open.bracket, bp.bracket, bp.column, false, false
                            );
                            editor.codeFold.foldRanges.put(open.line, range);
                        }
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
        long startMs = android.os.SystemClock.uptimeMillis();
        for (int i = startLine; i <= endLine; i++) {
            lineCache.remove(i);
        }
        // Also invalidate any folds that might be affected
        if (editor.codeFold.isCodeFoldingEnabled) {
            editor.codeFold.foldIntervalsDirty = true;
        }
        if (editor.DEBUG_RENDER_LOGS) {
            long dt = android.os.SystemClock.uptimeMillis() - startMs;
            if (dt > 2) {
                android.util.Log.d("SodiumRender", "bracketInvalidate dtMs=" + dt
                        + " lines=" + (endLine - startLine + 1));
            }
        }
    }

    /**
     * Get bracket info for a line, parsing it if not cached.
     * Reads directly from file if not in window buffer.
     */
    public LineBracketInfo getLineInfo(int lineNum) {
        long startMs = android.os.SystemClock.uptimeMillis();
        LineBracketInfo info = lineCache.get(lineNum);
        if (info != null) {
            return info;
        }

        // Try to read directly from file using line offsets
        String line = null;
        boolean isMainThread = android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        if (!isMainThread && editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null) {
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(editor.fileIO.sourceFile, "r");
                if (lineNum >= 0 && lineNum < editor.fileIO.lineOffsets.length) {
                    line = editor.fileIO.readLineUtf8AtByte(raf, editor.fileIO.lineOffsets[lineNum]);
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
            line = editor.windowRender.getLineTextForRender(lineNum);
        }
        if (line == null) line = "";

        // Get previous line's state by reading from file or cache
        boolean inBlockComment = false;
        int stringState = 0;
        if (lineNum > 0) {
            // Avoid deep recursion and file I/O on the main thread.
            LineBracketInfo prevInfo = null;
            int lookback = 0;
            int prevLine = lineNum - 1;
            while (prevLine >= 0 && lookback < 200) {
                prevInfo = lineCache.get(prevLine);
                if (prevInfo != null) break;
                prevLine--;
                lookback++;
            }
            if (prevInfo == null && !isMainThread) {
                prevInfo = getLineInfo(lineNum - 1);
            }
            if (prevInfo != null) {
                inBlockComment = prevInfo.isInBlockComment;
                stringState = prevInfo.stringState;
            }
        }

        info = parseLine(lineNum, line, inBlockComment, stringState);
        lineCache.put(lineNum, info);
        if (editor.DEBUG_RENDER_LOGS) {
            long dt = android.os.SystemClock.uptimeMillis() - startMs;
            if (dt > 2) {
                android.util.Log.d("SodiumRender", "bracketLineParse dtMs=" + dt
                        + " line=" + lineNum + " len=" + line.length());
            }
        }
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
        
        while (line < editor.view.getLinesCount()) {
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
            if (line < editor.view.getLinesCount()) {
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
            if (quoteChar == QUOTE_BACKTICK) return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE;
            return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE;
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
