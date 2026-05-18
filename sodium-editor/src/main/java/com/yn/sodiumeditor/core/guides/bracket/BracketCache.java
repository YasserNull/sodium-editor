package com.yn.sodiumeditor.core.guides.bracket; 

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.utils.FunctionLog;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
            FunctionLog.f("LineBracketInfo", "LineBracketInfo", line);
            this.line = line;
            this.text = text == null ? "" : text;
            this.brackets = new ArrayList<>();
            this.quotes = new ArrayList<>();
        }

        public void clear() {
            FunctionLog.f("LineBracketInfo", "clear");
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
            FunctionLog.f("BracketPosition", "BracketPosition", line, column, bracket);
            this.line = line;
            this.column = column;
            this.bracket = bracket;
            this.isOpening = isOpeningBracket(bracket);
        }

        public static boolean isOpeningBracket(char c) {
            FunctionLog.f("BracketPosition", "isOpeningBracket", c);
            return c == BRACKET_CURLY_OPEN || c == BRACKET_PAREN_OPEN || c == BRACKET_SQUARE_OPEN;
        }

        public static boolean isClosingBracket(char c) {
            FunctionLog.f("BracketPosition", "isClosingBracket", c);
            return c == BRACKET_CURLY_CLOSE || c == BRACKET_PAREN_CLOSE || c == BRACKET_SQUARE_CLOSE;
        }

        public static char getMatchingBracket(char c) {
            FunctionLog.f("BracketPosition", "getMatchingBracket", c);
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
            FunctionLog.f("QuotePosition", "QuotePosition", line, startColumn, endColumn, quoteChar, isMultiline);
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
        FunctionLog.f("BracketCache", "BracketCache", editor);
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
        FunctionLog.f("BracketCache", "scanFileAsync");
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
            
            // Read sequentially using BufferedReader for speed
            if (editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile);
                     java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
                     java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {
                    
	                    boolean inBlockComment = false;
	                    int stringState = 0;
	                    char stringQuoteChar = 0;
	                    boolean stringTriple = false;
	                    int lineNum = 0;
	                    String line;

	                    while ((line = reader.readLine()) != null && myToken == scanToken) {
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
	                        lineNum++;
	                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (myToken == scanToken && newCache.size() > 0) {
                ConcurrentHashMap<Integer, CodeFold.FoldRange> newFoldRanges =
                    buildFoldRangesFromCache(newCache);

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
	                        ConcurrentHashMap<Integer, CodeFold.FoldRange> previousRanges =
	                            new ConcurrentHashMap<>(editor.codeFold.foldRanges);
	                        for (CodeFold.FoldRange range : newFoldRanges.values()) {
	                            CodeFold.FoldRange old = previousRanges.get(range.startLine);
	                            if (old != null
	                                    && old.endLine == range.endLine
	                                    && old.openCharIndex == range.openCharIndex
	                                    && old.openChar == range.openChar
	                                    && old.closeChar == range.closeChar
	                                    && old.isBlockComment == range.isBlockComment
	                                    && old.isIndentFold == range.isIndentFold) {
	                                range.collapsed = old.collapsed;
	                            }
	                        }
	                        editor.codeFold.foldRanges.clear();
	                        if (editor.codeFold.isCodeFoldingEnabled) {
	                            editor.codeFold.foldRanges.putAll(newFoldRanges);
	                        }
                        editor.codeFold.invalidateFoldCaches();
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

    public void ensureScannedAsync() {
        FunctionLog.f("BracketCache", "ensureScannedAsync");
        if (isScanning || lineCache.size() > 0) return;
        scanFileAsync();
    }

    /**
     * Rebuild fold ranges in background thread using efficient O(N) stack-based matching.
     */
    private ConcurrentHashMap<Integer, CodeFold.FoldRange> buildFoldRangesFromCache(
        SparseArray<LineBracketInfo> cache) {
        FunctionLog.f("BracketCache", "buildFoldRangesFromCache", cache);
        ConcurrentHashMap<Integer, CodeFold.FoldRange> ranges = new ConcurrentHashMap<>();

	        // Use one stack so cross-type nesting cannot create false fold pairs.
	        java.util.ArrayDeque<BracketPosition> bracketStack = new java.util.ArrayDeque<>();
	        BracketPosition blockCommentStart = null;

        int totalLines = cache.size();
        for (int i = 0; i < totalLines; i++) {
            int lineNum = cache.keyAt(i);
            LineBracketInfo info = cache.valueAt(i);
            if (info == null) continue;
            
            for (BracketPosition bp : info.brackets) {
	                if (bp.isOpening) {
	                    bracketStack.push(bp);
	                } else if (!bracketStack.isEmpty()) {
	                    BracketPosition open = bracketStack.peek();
	                    if (BracketPosition.getMatchingBracket(open.bracket) == bp.bracket) {
	                        bracketStack.pop();
	                        if (lineNum > open.line) {
	                            CodeFold.FoldRange range =
	                                new CodeFold.FoldRange(
	                                    open.line,
	                                    lineNum,
	                                    open.column,
	                                    open.bracket,
	                                    bp.bracket,
	                                    bp.column,
	                                    false,
	                                    false);
	                            ranges.put(open.line, range);
	                        }
	                    }
	                }
	            }

	            if (info.blockCommentStartColumn >= 0) {
	                blockCommentStart = new BracketPosition(lineNum, info.blockCommentStartColumn, '/');
	            }
	            if (blockCommentStart != null && info.blockCommentEndColumn >= 0 && lineNum > blockCommentStart.line) {
	                CodeFold.FoldRange range =
	                    new CodeFold.FoldRange(
	                        blockCommentStart.line,
	                        lineNum,
	                        blockCommentStart.column,
	                        '/',
	                        '/',
	                        info.blockCommentEndColumn,
	                        true,
	                        false);
	                ranges.putIfAbsent(blockCommentStart.line, range);
	                blockCommentStart = null;
	            }
	        }
	        if (editor.indentGuides.isIndentationBlocksEnabled) {
	            addIndentFoldRanges(cache, ranges);
	        }
	        return ranges;
	    }

    private void postScanComplete(@Nullable Runnable onComplete) {
        if (onComplete == null) return;
        mainHandler.post(onComplete);
    }

    /**
     * Quick check if column is in string/comment using pre-parsed info.
     */
    private boolean isInStringOrCommentQuick(LineBracketInfo info, int column) {
        FunctionLog.f("BracketCache", "isInStringOrCommentQuick", info, column);
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
        FunctionLog.f("BracketCache", "parseLine", lineNum, line, startInBlockComment, startStringState);
        return parseLineInternal(lineNum, line, startInBlockComment, startStringState, (char) 0, false);
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

    private void addIndentFoldRanges(
            SparseArray<LineBracketInfo> cache, ConcurrentHashMap<Integer, CodeFold.FoldRange> ranges) {
        int total = cache.size();
        for (int i = 0; i < total; i++) {
            int lineNum = cache.keyAt(i);
            LineBracketInfo info = cache.valueAt(i);
            if (info == null || info.isInBlockComment || info.stringState != 0) continue;
            String line = info.text;
            String trimmed = rstripWhitespace(line);
            if (!isIndentFoldCandidate(trimmed)) continue;
            int baseIndent = getIndentWidth(line);
            int endLine = -1;
            for (int j = i + 1; j < total; j++) {
                LineBracketInfo nextInfo = cache.valueAt(j);
                if (nextInfo == null) break;
                String next = nextInfo.text;
                String nextTrimmed = rstripWhitespace(next);
                if (nextTrimmed.isEmpty()) continue;
                if (getIndentWidth(next) <= baseIndent) break;
                endLine = cache.keyAt(j);
            }
            if (endLine > lineNum) {
                int openIdx = Math.max(0, trimmed.length() - 1);
                ranges.putIfAbsent(
                        lineNum,
                        new CodeFold.FoldRange(lineNum, endLine, openIdx, ':', ':', -1, false, true));
            }
        }
    }

    private boolean isIndentFoldCandidate(String trimmed) {
        if (trimmed == null || trimmed.length() < 2 || !trimmed.endsWith(":")) return false;
        char beforeColon = trimmed.charAt(trimmed.length() - 2);
        return beforeColon != '}' && beforeColon != ']' && beforeColon != ')';
    }

    private int getIndentWidth(String line) {
        if (line == null) return 0;
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
        if (line == null || line.isEmpty()) return "";
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }

    /**
     * Invalidate cache for a specific line range.
     */
    public void invalidateLines(int startLine, int endLine) {
        FunctionLog.f("BracketCache", "invalidateLines", startLine, endLine);
        long startMs = android.os.SystemClock.uptimeMillis();
        for (int i = startLine; i <= endLine; i++) {
            lineCache.remove(i);
        }
	        // Also invalidate any folds that might be affected
	        if (editor.codeFold.isCodeFoldingEnabled) {
	            editor.codeFold.invalidateFoldRangesIntersectingRange(startLine, endLine);
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
        FunctionLog.f("BracketCache", "getLineInfo", lineNum);
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
        FunctionLog.f("BracketCache", "isInStringOrComment", line, column);
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
        FunctionLog.f("BracketCache", "getOpeningBrackets", lineNum);
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
        FunctionLog.f("BracketCache", "findMatchingBracket", open);
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
        FunctionLog.f("BracketCache", "findMatchingOpeningBracket", close);
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
        FunctionLog.f("BracketCache", "isQuoteChar", c);
        return c == QUOTE_DOUBLE || c == QUOTE_SINGLE || c == QUOTE_BACKTICK;
    }

    private int getStringState(char quoteChar, boolean isTriple) {
        FunctionLog.f("BracketCache", "getStringState", quoteChar, isTriple);
        if (isTriple) {
            if (quoteChar == QUOTE_BACKTICK) return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE;
            return com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE;
        }
        return 1; // Simple string state
    }

    private boolean isEscaped(String line, int index) {
        FunctionLog.f("BracketCache", "isEscaped", line, index);
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
        FunctionLog.f("BracketCache", "clear");
        scanToken++;
        isScanning = false;
        lineCache.clear();
        cacheVersion++;
    }

    /**
     * Check if currently scanning.
     */
    public boolean isScanning() {
        FunctionLog.f("BracketCache", "isScanning");
        return isScanning;
    }
}
