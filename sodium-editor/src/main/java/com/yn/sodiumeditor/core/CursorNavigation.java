package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.state.CursorState;

/**
 * Core class for cursor navigation.
 * Handles cursor movement logic (left, right, up, down) and position clamping.
 */
public final class CursorNavigation {

    private final CursorState state;
    private final NavigationCallback callback;

    public interface NavigationCallback {
        @Nullable String getLineTextForRender(int line);
        boolean isEof();
        int getWindowStartLine();
        java.util.List<String> getLinesWindow();
        void setCursorPosition(int line, int ch);
        void clearSelectionKeepLineNumberState();
        void hidePopup();
        void resetCursorBlink();
        void invalidate();
        void keepCursorVisibleHorizontally();
        void inlinePredictionUpdate();
        boolean hasSelection();
        int getSelectionStartLine();
        int getSelectionStartChar();
        int getSelectionEndLine();
        int getSelectionEndChar();
        int comparePos(int sL, int sC, int eL, int eC);
    }

    public CursorNavigation(CursorState state, NavigationCallback callback) {
        this.state = state;
        this.callback = callback;
    }

    public void moveCursorLeft() {
        callback.inlinePredictionUpdate();
        
        if (callback.hasSelection()) {
            int sL = callback.getSelectionStartLine();
            int sC = callback.getSelectionStartChar();
            if (callback.comparePos(
                    callback.getSelectionStartLine(), callback.getSelectionStartChar(),
                    callback.getSelectionEndLine(), callback.getSelectionEndChar()) > 0) {
                sL = callback.getSelectionEndLine();
                sC = callback.getSelectionEndChar();
            }
            callback.setCursorPosition(sL, sC);
        } else if (state.getCursorChar() > 0) {
            state.moveCharDelta(-1);
        } else if (state.getCursorLine() > 0) {
            int nextLine = state.getCursorLine() - 1;
            String ln = callback.getLineTextForRender(nextLine);
            callback.setCursorPosition(nextLine, ln != null ? ln.length() : 0);
        }
        
        postMove();
    }

    public void moveCursorRight() {
        callback.inlinePredictionUpdate();
        
        if (callback.hasSelection()) {
            int eL = callback.getSelectionEndLine();
            int eC = callback.getSelectionEndChar();
            if (callback.comparePos(
                    callback.getSelectionStartLine(), callback.getSelectionStartChar(),
                    callback.getSelectionEndLine(), callback.getSelectionEndChar()) > 0) {
                eL = callback.getSelectionStartLine();
                eC = callback.getSelectionStartChar();
            }
            callback.setCursorPosition(eL, eC);
        } else {
            String ln = callback.getLineTextForRender(state.getCursorLine());
            int lineLen = ln != null ? ln.length() : 0;
            if (state.getCursorChar() < lineLen) {
                state.moveCharDelta(1);
            } else {
                int next = state.getCursorLine() + 1;
                if (!callback.isEof() || next < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
                    callback.setCursorPosition(next, 0);
                }
            }
        }
        
        postMove();
    }

    public void moveCursorUp() {
        callback.inlinePredictionUpdate();
        
        if (callback.hasSelection()) {
            int sL = callback.getSelectionStartLine();
            int sC = callback.getSelectionStartChar();
            if (callback.comparePos(
                    callback.getSelectionStartLine(), callback.getSelectionStartChar(),
                    callback.getSelectionEndLine(), callback.getSelectionEndChar()) > 0) {
                sL = callback.getSelectionEndLine();
                sC = callback.getSelectionStartChar();
            }
            callback.setCursorPosition(sL, sC);
        }
        
        if (state.getCursorLine() > 0) {
            int nextLine = state.getCursorLine() - 1;
            String ln = callback.getLineTextForRender(nextLine);
            int lineLen = ln != null ? ln.length() : 0;
            callback.setCursorPosition(nextLine, Math.min(state.getCursorChar(), lineLen));
        }
        
        postMove();
    }

    public void moveCursorDown() {
        callback.inlinePredictionUpdate();
        
        if (callback.hasSelection()) {
            int eL = callback.getSelectionEndLine();
            int eC = callback.getSelectionEndChar();
            if (callback.comparePos(
                    callback.getSelectionStartLine(), callback.getSelectionStartChar(),
                    callback.getSelectionEndLine(), callback.getSelectionEndChar()) > 0) {
                eL = callback.getSelectionStartLine();
                eC = callback.getSelectionStartChar();
            }
            callback.setCursorPosition(eL, eC);
        }
        
        int next = state.getCursorLine() + 1;
        if (!callback.isEof() || next < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
            String ln = callback.getLineTextForRender(next);
            int lineLen = ln != null ? ln.length() : 0;
            callback.setCursorPosition(next, Math.min(state.getCursorChar(), lineLen));
        }
        
        postMove();
    }

    private void postMove() {
        callback.clearSelectionKeepLineNumberState();
        callback.hidePopup();
        callback.resetCursorBlink();
        callback.invalidate();
        callback.keepCursorVisibleHorizontally();
        callback.inlinePredictionUpdate();
    }

    public void clampCharToLineLength(int line) {
        String lineText = callback.getLineTextForRender(line);
        if (lineText != null) {
            state.clampCharToLineLength(line, lineText.length());
        }
    }

    public void setPositionNoClear(int line, int col) {
        int targetLine = Math.max(0, line);
        int targetCol = Math.max(0, col);
        state.setCursorLine(targetLine);
        
        if (targetLine >= callback.getWindowStartLine() 
                && targetLine < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
            String lineText = callback.getLineTextForRender(targetLine);
            state.setCursorChar(Math.max(0, Math.min(targetCol, lineText != null ? lineText.length() : 0)));
        } else {
            state.setCursorChar(targetCol);
        }
        
        callback.resetCursorBlink();
        callback.keepCursorVisibleHorizontally();
        callback.invalidate();
    }

    public void setPosition(int line, int col) {
        int targetLine = Math.max(0, line);
        int targetCol = Math.max(0, col);
        
        if (callback.hasSelection()) {
            callback.clearSelectionKeepLineNumberState();
            callback.hidePopup();
        }
        
        state.setCursorLine(targetLine);
        
        if (targetLine >= callback.getWindowStartLine() 
                && targetLine < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
            String lineText = callback.getLineTextForRender(targetLine);
            state.setCursorChar(Math.max(0, Math.min(targetCol, lineText != null ? lineText.length() : 0)));
        } else {
            state.setCursorChar(targetCol);
        }
        
        callback.resetCursorBlink();
        callback.keepCursorVisibleHorizontally();
        callback.invalidate();
    }

    public void proceedGoToLineClamped(final int currentGoToLineVersion, final int targetLine, final int targetCol) {
        if (callback.hasSelection()) {
            callback.clearSelectionKeepLineNumberState();
            callback.hidePopup();
        }
        
        Runnable completionAction = () -> {
            if (currentGoToLineVersion != getCurrentGoToLineVersion()) return;

            int finalLine = targetLine;
            int finalChar;
            
            if (finalLine >= callback.getWindowStartLine() 
                    && finalLine < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
                String lineText = callback.getLineTextForRender(finalLine);
                finalChar = Math.max(0, Math.min(targetCol, lineText != null ? lineText.length() : 0));
            } else if (callback.isEof()) {
                int lastLineInDoc = callback.getWindowStartLine() + callback.getLinesWindow().size() - 1;
                if (finalLine > lastLineInDoc) finalLine = Math.max(0, lastLineInDoc);
                String lineText = callback.getLineTextForRender(finalLine);
                finalChar = Math.max(0, Math.min(targetCol, lineText != null ? lineText.length() : 0));
            } else {
                finalChar = 0;
            }
            
            callback.setCursorPosition(finalLine, finalChar);
            callback.keepCursorVisibleHorizontally();
        };

        if (targetLine >= callback.getWindowStartLine() 
                && targetLine < callback.getWindowStartLine() + callback.getLinesWindow().size()) {
            completionAction.run();
        }
    }

    private int getCurrentGoToLineVersion() {
        // This should be provided by callback if needed
        return 0;
    }

    //================================================================================
    // Go To Line
    //================================================================================

    /**
     * Navigates to a specific line and column in the document.
     * This is a high-level method that coordinates loading, selection clearing, and cursor positioning.
     * 
     * @param line the 1-based line number to navigate to
     * @param col the 1-based column number to navigate to
     * @param currentGoToLineVersion the current version token for cancellation
     * @param requestedLine the 0-based requested line
     * @param requestedCol the 0-based requested column
     * @param knownTotal the known total line count, or null if unknown
     * @param callback callback for line counting and UI operations
     */
    public void goToLine(
            int line,
            int col,
            int currentGoToLineVersion,
            int requestedLine,
            int requestedCol,
            Integer knownTotal,
            GoToLineCallback callback) {

        if (knownTotal != null) {
            int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
            proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
        } else {
            callback.countTotalLines(totalLines -> {
                if (currentGoToLineVersion != getCurrentGoToLineVersion()) return;
                int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
                int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
                proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
            });
        }
    }

    /**
     * High-level goToLine method that handles UI state and navigation.
     * 
     * @param line the 1-based line number to navigate to
     * @param col the 1-based column number to navigate to
     * @param callback callback for UI operations and line counting
     */
    public void goToLine(int line, int col, GoToLineWithUICallback callback) {
        final int currentGoToLineVersion = callback.incrementGoToLineVersion();

        // Show loading indicator and disable view
        callback.setDisable(true);
        callback.showLoadingCircle(true);

        // Clear selection if any
        if (callback.hasSelection()) {
            callback.clearSelection();
            callback.setSelecting(false);
            callback.hidePopup();
        }

        final int requestedLine = Math.max(0, line - 1);
        final int requestedCol = Math.max(0, col - 1);

        // Get known total lines
        Integer knownTotal = callback.getKnownTotalLines();

        goToLine(line, col, currentGoToLineVersion, requestedLine, requestedCol, knownTotal,
            new GoToLineCallback() {
                @Override
                public void countTotalLines(LineCountCallback lineCountCallback) {
                    callback.countTotalLines(lineCountCallback::onResult);
                }
            });
    }

    /**
     * Callback interface for goToLine operations with UI management.
     */
    public interface GoToLineCallback {
        void countTotalLines(LineCountCallback callback);
        interface LineCountCallback {
            void onResult(int count);
        }
    }

    /**
     * Extended callback interface for goToLine with UI operations.
     */
    public interface GoToLineWithUICallback extends GoToLineCallback {
        int incrementGoToLineVersion();
        void setDisable(boolean disable);
        void showLoadingCircle(boolean show);
        boolean hasSelection();
        void clearSelection();
        void setSelecting(boolean selecting);
        void hidePopup();
        Integer getKnownTotalLines();
    }
}