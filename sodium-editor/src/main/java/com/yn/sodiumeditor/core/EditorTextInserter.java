package com.yn.sodiumeditor.core;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.state.CursorState;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Core class for inserting text into the editor.
 * Handles text insertion, large paste operations, and edit recording.
 */
public final class EditorTextInserter {

    private final CursorState state;
    private final InsertionCallback callback;

    public interface InsertionCallback {
        boolean isReadOnly();
        void invalidatePendingIOForEdit();
        int incrementEditVersion();
        boolean isFileCleared();
        @Nullable File getSourceFile();
        boolean isLargePasteText(String text);
        void beginLargeEditUiIfNeeded(boolean isSelectAll, int sL, int eL, boolean selectAllLike);
        Handler getMainHandler();
        Runnable getLargeEditUiWatchdog();
        void postDelayed(Runnable r, long delayMillis);
        CursorTarget computeCursorAfterInsert(int line, int ch, String text);
        void rewriteReplaceRangeAsync(int opToken, File inFile, int sL, int sC, int eL, int eC, String text, CursorTarget target, boolean finishLargeEdit);
        void inlinePredictionUpdate();
        void addLineCountDelta(int delta);
        int getUndoTextLimit();
        void recordEdit(EditOp op);
        void ensureLineInWindow(int line, boolean center);
        boolean isWindowLoading();
        int getWindowStartLine();
        List<String> getLinesWindow();
        String getLineFromWindowLocal(int local);
        void updateLocalLine(int local, String newLine);
        java.util.HashMap<Integer, String> getModifiedLines();
        void removeLineWidthCache(int line);
        void clearLineWidthCache();
        void addLinesWindowAll(int index, List<String> lines);
        void setCursorPosition(int line, int ch);
        int getCursorLine();
        int getCursorChar();
        void moveCharDelta(int delta);
        void setLineAndChar(int line, int ch);
        int getLinesCount();
        boolean isShowLineNumbers();
        void requestLayout();
        void onLineCountChanged(int delta);
        void recalculateMaxLineWidth();
        void keepCursorVisibleHorizontally();
        void resetCursorBlink();
        void invalidate();
        int getLineLength(int line);
    }

    public static final class CursorTarget {
        public final int line;
        public final int ch;

        public CursorTarget(int line, int ch) {
            this.line = line;
            this.ch = ch;
        }
    }

    public EditorTextInserter(CursorState state, InsertionCallback callback) {
        this.state = state;
        this.callback = callback;
    }

    public void insertTextAtCursor(String text) {
        if (callback.isReadOnly()) return;
        callback.invalidatePendingIOForEdit();
        final int opToken = callback.incrementEditVersion();

        if (text == null) return;
        if (text.isEmpty()) return;

        if (callback.isLargePasteText(text)) {
            handleLargePaste(text, opToken);
            return;
        }

        handleNormalInsert(text, opToken);
    }

    private void handleLargePaste(String text, int opToken) {
        callback.beginLargeEditUiIfNeeded(true, state.getCursorLine(), state.getCursorLine(), true);
        
        callback.getMainHandler().removeCallbacks(callback.getLargeEditUiWatchdog());
        callback.postDelayed(callback.getLargeEditUiWatchdog(), 30_000);
        
        CursorTarget target = callback.computeCursorAfterInsert(state.getCursorLine(), state.getCursorChar(), text);
        File inFile = callback.getSourceFile();
        
        callback.rewriteReplaceRangeAsync(opToken, inFile, state.getCursorLine(), state.getCursorChar(), 
                state.getCursorLine(), state.getCursorChar(), text, target, true);
        callback.inlinePredictionUpdate();
        callback.addLineCountDelta(countNewlines(text));
        
        if (text.length() <= callback.getUndoTextLimit()) {
            recordInsertEdit(state.getCursorLine(), state.getCursorChar(), text, target);
        }
    }

    private void handleNormalInsert(String text, int opToken) {
        callback.ensureLineInWindow(state.getCursorLine(), true);
        
        if (callback.isWindowLoading()
                && (state.getCursorLine() < callback.getWindowStartLine() 
                    || state.getCursorLine() >= callback.getWindowStartLine() + callback.getLinesWindow().size())) {
            // Post to retry after window loads
            return;
        }

        int local = state.getCursorLine() - callback.getWindowStartLine();
        if (local < 0 || local >= callback.getLinesWindow().size()) {
            synchronized (callback.getLinesWindow()) {
                if (callback.getLinesWindow().isEmpty()) {
                    callback.getLinesWindow().add("");
                    local = 0;
                } else {
                    local = Math.max(0, Math.min(local, callback.getLinesWindow().size() - 1));
                }
            }
        }

        synchronized (callback.getLinesWindow()) {
            int oldLineCount = callback.getLinesCount();
            String base = callback.getLineFromWindowLocal(local);
            if (base == null) base = "";
            int pos = Math.max(0, Math.min(state.getCursorChar(), base.length()));
            String left = base.substring(0, pos);
            String right = base.substring(pos);

            String[] parts = text.split("\n", -1);

            if (parts.length == 1) {
                String modified = left + parts[0] + right;
                callback.updateLocalLine(local, modified);
                callback.getModifiedLines().put(state.getCursorLine(), modified);
                callback.removeLineWidthCache(state.getCursorLine());
                state.moveCharDelta(parts[0].length());
            } else {
                callback.clearLineWidthCache();
                String firstLine = left + parts[0];
                callback.updateLocalLine(local, firstLine);
                callback.getModifiedLines().put(state.getCursorLine(), firstLine);

                List<String> linesToInsert = new ArrayList<>();
                for (int p = 1; p < parts.length - 1; p++) {
                    linesToInsert.add(parts[p]);
                }

                String lastPart = parts[parts.length - 1];
                linesToInsert.add(lastPart + right);

                if (!linesToInsert.isEmpty()) {
                    callback.addLinesWindowAll(local + 1, linesToInsert);
                }
                for (int i = 0; i < linesToInsert.size(); i++) {
                    callback.getModifiedLines().put(state.getCursorLine() + 1 + i, linesToInsert.get(i));
                }

                callback.setLineAndChar(state.getCursorLine() + (parts.length - 1), lastPart.length());
                callback.addLineCountDelta(parts.length - 1);
            }

            int newLineCount = callback.getLinesCount();
            if (callback.isShowLineNumbers()
                    && oldLineCount > 0
                    && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
                callback.requestLayout();
            }
            if (parts.length > 1) {
                callback.onLineCountChanged(parts.length - 1);
            }

            callback.recalculateMaxLineWidth();
            callback.keepCursorVisibleHorizontally();
            callback.resetCursorBlink();
            callback.invalidate();
        }
        callback.inlinePredictionUpdate();

        CursorTarget insertedEnd = callback.computeCursorAfterInsert(
                state.getCursorLine(), state.getCursorChar(), text);
        recordInsertEdit(state.getCursorLine(), state.getCursorChar(), text, insertedEnd);
    }

    private void recordInsertEdit(int beforeLine, int beforeChar, String text, CursorTarget insertedEnd) {
        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = text;
        op.insertedEndLine = insertedEnd.line;
        op.insertedEndChar = insertedEnd.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = state.getCursorLine();
        op.cursorCharAfter = state.getCursorChar();
        op.timestamp = System.currentTimeMillis();
        callback.recordEdit(op);
    }

    public int countNewlines(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    public CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
        if (insertText == null) insertText = "";
        int newLines = 0;

        int lastNl = insertText.lastIndexOf('\n');
        if (lastNl >= 0) {
            for (int i = 0; i < insertText.length(); i++) {
                if (insertText.charAt(i) == '\n') newLines++;
            }
            int lastSegLen = insertText.length() - lastNl - 1;
            return new CursorTarget(baseLine + newLines, lastSegLen);
        }
        return new CursorTarget(baseLine, baseChar + insertText.length());
    }

    public void insertTextAt(int line, int col, String text) {
        if (text == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            callback.getMainHandler().post(() -> insertTextAt(line, col, text));
            return;
        }
        callback.setLineAndChar(line, col);
        insertTextAtCursor(text);
    }
}
