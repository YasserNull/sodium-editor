package com.yn.sodiumeditor.core.guides.bracket; 

import com.yn.sodiumeditor.SodiumEditor;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import android.util.SparseArray;
/**
 * Caches bracket and quote positions for bracket matching.
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
        public final String text;
        public final List<BracketPosition> brackets;
        public final List<QuotePosition> quotes;
        public boolean isInBlockComment;
        public int stringState;
        public char stringQuoteChar;
        public boolean stringTriple;
        public int blockCommentStartColumn = -1;
        public int blockCommentEndColumn = -1;

        public LineBracketInfo(int line) {
            this(line, "");
        }

        public LineBracketInfo(int line, String text) {
            this.line = line;
            this.text = text == null ? "" : text;
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
        scanFileAsync(null);
    }

    public void scanFileAsync(@Nullable Runnable onComplete) {
        final int myToken = ++scanToken;
        isScanning = true;
        cacheVersion++;

        editor.fileIO.ioHandler.post(() -> {
            long startMs = android.os.SystemClock.uptimeMillis();
            if (myToken != scanToken) {
                postScanComplete(onComplete);
                return;
            }

            SparseArray<LineBracketInfo> newCache = new SparseArray<>();
            
            if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(editor.fileIO.sourceFile, "r")) {
                    
	                    boolean inBlockComment = false;
	                    int stringState = 0;
	                    char stringQuoteChar = 0;
	                    boolean stringTriple = false;
	                    int totalLines;
	                    synchronized (editor.fileIO.lineOffsetsLock) {
	                        totalLines = editor.fileIO.lineOffsets.length;
	                    }

	                    for (int lineNum = 0; lineNum < totalLines && myToken == scanToken; lineNum++) {
	                        String line = editor.bracketGuides.readIndexedLinePrefix(lineNum, raf);
	                        if (line == null) break;
	                        LineBracketInfo info =
	                            parseLineInternal(
	                                lineNum,
	                                line,
	                                inBlockComment,
	                                stringState,
	                                stringQuoteChar,
	                                stringTriple);
	                        newCache.put(lineNum, info);

	                        inBlockComment = info.isInBlockComment;
	                        stringState = info.stringState;
	                        stringQuoteChar = info.stringQuoteChar;
	                        stringTriple = info.stringTriple;
	                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (myToken == scanToken && newCache.size() > 0) {
                final int finalVersion = cacheVersion;
                editor.caret.mainHandler.post(() -> {
                    long dt = android.os.SystemClock.uptimeMillis() - startMs;
	                    if (scanToken == myToken) {
	                        lineCache.clear();
	                        for (int i = 0; i < newCache.size(); i++) {
	                            lineCache.put(newCache.keyAt(i), newCache.valueAt(i));
	                        }
                        isScanning = false;
                        editor.invalidate();
                    }
                    if (onComplete != null) onComplete.run();
                });
            } else {
                isScanning = false;
                postScanComplete(onComplete);
            }
        });
    }

    private boolean hasPendingInMemoryEdits() {
        if (editor.editOperators.lineCountDelta != 0) return true;
        synchronized (editor.windowRender.modifiedLines) {
            return !editor.windowRender.modifiedLines.isEmpty();
        }
    }

    public void ensureScannedAsync() {
        if (isScanning || lineCache.size() > 0) return;
        scanFileAsync();
    }

    private void postScanComplete(@Nullable Runnable onComplete) {
        if (onComplete == null) return;
        mainHandler.post(onComplete);
    }

    /**
     * Invalidate cache for a specific line range.
     */
    public void invalidateLines(int startLine, int endLine) {
        for (int i = startLine; i <= endLine; i++) {
            lineCache.remove(i);
        }
    }

    private LineBracketInfo parseLineInternal(
            int lineNum,
            String line,
            boolean startInBlockComment,
            int startStringState,
            char startStringQuoteChar,
            boolean startStringTriple) {
        if (line == null) line = "";
        LineBracketInfo info = new LineBracketInfo(lineNum, line);
        info.isInBlockComment = startInBlockComment;
        info.stringState = startStringState;
        info.stringQuoteChar = startStringQuoteChar;
        info.stringTriple = startStringTriple;

        int len = line.length();
        int i = 0;

        while (i < len) {
            char c = line.charAt(i);

            if (info.isInBlockComment) {
                if (i + 1 < len && c == '*' && line.charAt(i + 1) == '/') {
                    info.isInBlockComment = false;
                    info.blockCommentEndColumn = i;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            if (info.stringState != 0) {
                char quote = info.stringQuoteChar != 0 ? info.stringQuoteChar : QUOTE_DOUBLE;
                if (info.stringTriple) {
                    int end = findTripleQuoteEnd(line, i, quote);
                    if (end < 0) {
                        info.quotes.add(new QuotePosition(lineNum, 0, Math.max(0, len - 1), quote, true));
                        i = len;
                    } else {
                        info.quotes.add(new QuotePosition(lineNum, 0, end + 2, quote, true));
                        info.stringState = 0;
                        info.stringQuoteChar = 0;
                        info.stringTriple = false;
                        i = end + 3;
                    }
                } else {
                    int end = findQuoteEnd(line, i, quote);
                    if (end < 0) {
                        info.quotes.add(new QuotePosition(lineNum, 0, Math.max(0, len - 1), quote, false));
                        i = len;
                    } else {
                        info.quotes.add(new QuotePosition(lineNum, 0, end, quote, false));
                        info.stringState = 0;
                        info.stringQuoteChar = 0;
                        info.stringTriple = false;
                        i = end + 1;
                    }
                }
                continue;
            }

            if (c == ESCAPE) {
                i += 2;
                continue;
            }

            if (isLineCommentStart(line, i)) {
                break;
            }

            if (i + 1 < len && c == '/' && line.charAt(i + 1) == '*') {
                info.isInBlockComment = true;
                info.blockCommentStartColumn = i;
                i += 2;
                continue;
            }

            if (info.stringState == 0 && isQuoteChar(c)) {
                int startCol = i;
                char quoteChar = c;
                boolean isTriple = i + 2 < len && line.charAt(i + 1) == c && line.charAt(i + 2) == c;
                
                if (isTriple) {
                    int end = findTripleQuoteEnd(line, i + 3, quoteChar);
                    if (end < 0) {
                        info.quotes.add(new QuotePosition(lineNum, startCol, len - 1, quoteChar, true));
                        info.stringState = getStringState(quoteChar, true);
                        info.stringQuoteChar = quoteChar;
                        info.stringTriple = true;
                        i = len;
                    } else {
                        info.quotes.add(new QuotePosition(lineNum, startCol, end + 2, quoteChar, true));
                        i = end + 3;
                    }
                    continue;
                } else {
                    int end = findQuoteEnd(line, i + 1, quoteChar);
                    if (end < 0) {
                        info.quotes.add(new QuotePosition(lineNum, startCol, len - 1, quoteChar, false));
                        info.stringState = getStringState(quoteChar, false);
                        info.stringQuoteChar = quoteChar;
                        info.stringTriple = false;
                        i = len;
                    } else {
                        info.quotes.add(new QuotePosition(lineNum, startCol, end, quoteChar, false));
                        i = end + 1;
                    }
                    continue;
                }
            }

            if (BracketPosition.isOpeningBracket(c) || BracketPosition.isClosingBracket(c)) {
                info.brackets.add(new BracketPosition(lineNum, i, c));
            }

            i++;
        }

        return info;
    }

    private int findQuoteEnd(String line, int start, char quoteChar) {
        int len = line == null ? 0 : line.length();
        for (int i = Math.max(0, start); i < len; i++) {
            if (line.charAt(i) == quoteChar && !isEscaped(line, i)) return i;
        }
        return -1;
    }

    private int findTripleQuoteEnd(String line, int start, char quoteChar) {
        int len = line == null ? 0 : line.length();
        for (int i = Math.max(0, start); i + 2 < len; i++) {
            if (line.charAt(i) == quoteChar
                    && line.charAt(i + 1) == quoteChar
                    && line.charAt(i + 2) == quoteChar
                    && !isEscaped(line, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isLineCommentStart(String line, int index) {
        if (line == null || index < 0 || index >= line.length()) return false;
        if (line.charAt(index) == '#' && !isEscaped(line, index)) return true;
        return index + 1 < line.length()
                && line.charAt(index) == '/'
                && line.charAt(index + 1) == '/'
                && !isEscaped(line, index);
    }

    /**
     * Get bracket info for a line, parsing it if not cached.
     * Reads directly from file if not in window buffer.
     */
    public LineBracketInfo getLineInfo(int lineNum) {
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
	        char stringQuoteChar = 0;
	        boolean stringTriple = false;
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
	                stringQuoteChar = prevInfo.stringQuoteChar;
	                stringTriple = prevInfo.stringTriple;
	            }
	        }

        info = parseLineInternal(lineNum, line, inBlockComment, stringState, stringQuoteChar, stringTriple);
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

    /**
     * Find the matching opening bracket for a given closing bracket.
     */
    @Nullable
    public BracketPosition findMatchingOpeningBracket(BracketPosition close) {
        if (close == null || close.isOpening) return null;

        char openChar = BracketPosition.getMatchingBracket(close.bracket);
        int depth = 1;
        int line = close.line;
        int col = close.column - 1;

        while (line >= 0) {
            LineBracketInfo info = getLineInfo(line);
            for (int i = info.brackets.size() - 1; i >= 0; i--) {
                BracketPosition bp = info.brackets.get(i);
                if (bp.line != line || bp.column > col) continue;
                if (isInStringOrComment(bp.line, bp.column)) continue;

                if (bp.bracket == close.bracket && !bp.isOpening) {
                    depth++;
                } else if (bp.bracket == openChar && bp.isOpening) {
                    depth--;
                    if (depth == 0) {
                        return bp;
                    }
                }
            }

            line--;
            col = Integer.MAX_VALUE;
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
        scanToken++;
        isScanning = false;
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
