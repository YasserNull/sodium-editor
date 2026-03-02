package com.yn.sodiumeditor.input;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.config.SelectionConfig;
import com.yn.sodiumeditor.core.SelectionTextBuilder;
import com.yn.sodiumeditor.state.SelectionState;

/**
 * Handler class for selection interactions.
 * Manages copy/cut, select all, and selection manipulation.
 */
public final class SelectionHandler {

    private final SelectionConfig config;
    private final SelectionState state;
    private final SelectionTextBuilder textBuilder;
    private final SelectionInteractionCallback callback;

    public interface SelectionInteractionCallback {
        int comparePos(int sL, int sC, int eL, int eC);
        void invalidate();
        void setSelection(int startLine, int startChar, int endLine, int endChar, boolean selecting);
        void setSelectAllState(boolean selectAll, boolean entireFile);
        void setSelecting(boolean selecting);
        void setLineNumberSelecting(boolean enabled, int anchorLine);
        void clearSelectionKeepLineNumberState();
        boolean shouldHideCopyCutForSelection();
        void deleteSelection();
        void post(Runnable r);
        void postDelayed(Runnable r, long delayMillis);
        Context getContext();
        boolean isFileCleared();
        @Nullable java.io.File getSourceFile();
        boolean isIndexReady();
        long[] getLineOffsets();
        Object getLineOffsetsLock();
        long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception;
        java.util.HashMap<Integer, String> getModifiedLines();
        int getWindowStartLine();
        java.util.List<String> getLinesWindow();
        String getLineTextForRender(int line);
        int getCopyCutMaxChars();
        java.nio.charset.Charset getFileCharset();
        void setDisable(boolean disabled);
        void setCursorLineAndChar(int line, int ch);
        void scrollToLineFastForSelectAll(int line, int ch);
        void showLoadingCircle(boolean show);
        void showPopupAtSelection();
        void hidePopup();
        void requestFocus();
        void showKeyboard();
        void restartInput();
        void clearActiveSuggestion();
        int getPrefetchLines();
        boolean isEof();
        boolean isIndexBuilding();
        boolean isIndexDisabled();
        void buildFileIndex();
        void loadWindowAround(int targetStart, Runnable onComplete);
        void countTotalLines(OnTotalLinesCounted callback);
        int incrementEditVersion();
        int getEditVersion();
        int getWidth();
        float getTextStartX();
        boolean isWordWrapEnabled();
        void cancelWrapWordWorkForPriority();
        boolean isWrapWordMetricsUsableForWindow(int widthPx);
        int getCursorLine();
        int getCursorChar();
        void setCursorPositionNoClear(int line, int ch);
        int getSelectionHandleColor();
        void setSelectionHandleColor(int color);
        void invalidatePendingIOForEdit();
        void insertTextAtCursor(String text);
        void inlinePredictionUpdate();
        int getHideCopyCutMaxLines();
    }

    public interface OnTotalLinesCounted {
        void onCounted(int totalLines);
    }

    public SelectionHandler(SelectionConfig config, SelectionState state, SelectionTextBuilder textBuilder, SelectionInteractionCallback callback) {
        this.config = config;
        this.state = state;
        this.textBuilder = textBuilder;
        this.callback = callback;
    }

    public boolean hasSelection() {
        return state.hasSelection();
    }

    public boolean isSelectAllActive() {
        return state.isSelectAllActive();
    }

    public boolean isEntireFileSelected() {
        return state.isEntireFileSelected();
    }

    public boolean isSelecting() {
        return state.selecting;
    }

    public boolean isLineNumberSelecting() {
        return state.isLineNumberSelecting();
    }

    public int getLineNumberSelectAnchorLine() {
        return state.lineNumberSelectAnchorLine;
    }

    public void clearSelection() {
        state.clearSelection();
    }

    public void clearSelectionKeepLineNumberState() {
        state.clearSelectionKeepLineNumberState();
    }

    public void setSelection(int startLine, int startChar, int endLine, int endChar, boolean selectingNow) {
        state.setSelection(startLine, startChar, endLine, endChar, selectingNow);
    }

    public void setSelectAllState(boolean selectAll, boolean entireFile) {
        state.setSelectAllState(selectAll, entireFile);
    }

    public void setSelecting(boolean selectingNow) {
        state.setSelecting(selectingNow);
    }

    public void setLineNumberSelecting(boolean enabled, int anchorLine) {
        state.setLineNumberSelecting(enabled, anchorLine);
    }

    public void setSelectionHighlightColor(int color) {
        config.setSelectionHighlightColor(color);
        callback.invalidate();
    }

    public void setSelectionHandleColor(int color) {
        if (callback.getSelectionHandleColor() == color) return;
        callback.setSelectionHandleColor(color);
        callback.invalidate();
    }

    public void copyOrCutSelection(final boolean cut) {
        if (!hasSelection()) return;
        callback.clearActiveSuggestion();

        if (callback.shouldHideCopyCutForSelection()) return;

        int sL = state.selStartLine, sC = state.selStartChar, eL = state.selEndLine, eC = state.selEndChar;
        if (callback.comparePos(sL, sC, eL, eC) > 0) {
            int tL = sL, tC = sC;
            sL = eL;
            sC = eC;
            eL = tL;
            eC = tC;
        }

        long lines = (long) eL - (long) sL + 1L;
        if (lines > callback.getCopyCutMaxChars()) return;

        final int fsL = sL, fsC = sC, feL = eL, feC = eC;

        boolean fullyInWindow = (fsL >= callback.getWindowStartLine()) && (feL < callback.getWindowStartLine() + callback.getLinesWindow().size());
        if (fullyInWindow) {
            String text = buildSelectedTextFromWindow(fsL, fsC, feL, feC);
            ClipboardManager cm = (ClipboardManager) callback.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
            if (cut) {
                callback.deleteSelection();
            }
            return;
        }

        if (callback.isWordWrapEnabled()) {
            callback.cancelWrapWordWorkForPriority();
        }

        callback.postDelayed(
                () -> {
                    final String text = textBuilder.buildSelectedTextBlocking(fsL, fsC, feL, feC);
                    callback.post(
                            () -> {
                                ClipboardManager cm = (ClipboardManager) callback.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null)
                                    cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));

                                if (cut) {
                                    callback.deleteSelection();
                                }
                            });
                }, 0);
    }

    private String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC) {
        StringBuilder sb = new StringBuilder();
        synchronized (callback.getLinesWindow()) {
            for (int L = sL; L <= eL; L++) {
                int local = L - callback.getWindowStartLine();
                String ln = (local >= 0 && local < callback.getLinesWindow().size()) ? callback.getLinesWindow().get(local) : "";
                if (ln == null) ln = "";
                int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
                int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
                if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
                if (L < eL) sb.append('\n');

                if (sb.length() > callback.getCopyCutMaxChars()) return sb.substring(0, callback.getCopyCutMaxChars());
            }
        }
        return sb.toString();
    }

    public void selectAll() {
        callback.clearActiveSuggestion();
        final boolean keyboardWasVisible = false;

        if (callback.isWordWrapEnabled()) {
            int widthPx = Math.max(1, Math.round(callback.getWidth() - callback.getTextStartX()));
            if (callback.isWrapWordMetricsUsableForWindow(widthPx)) {
                callback.cancelWrapWordWorkForPriority();
            }
        }

        callback.setDisable(true);
        callback.showLoadingCircle(true);

        state.setSelectAllState(true, true);
        state.setSelection(0, 0, 0, 0, false);
        callback.hidePopup();

        if (callback.getSourceFile() == null || callback.isFileCleared()) {
            handleSelectAllNoFile(keyboardWasVisible);
            return;
        }

        if (callback.isEof()) {
            handleSelectAllEof(keyboardWasVisible);
            return;
        }

        Runnable goToEndUsingIndex = () -> {
            if (!callback.isIndexReady() || callback.getSourceFile() == null) return;

            int fileLastLine;
            synchronized (callback.getLineOffsetsLock()) {
                fileLastLine = Math.max(0, callback.getLineOffsets().length - 1);
            }

            if (callback.isEof()) {
                int windowLast = Math.max(0, callback.getWindowStartLine() + callback.getLinesWindow().size() - 1);
                if (windowLast > fileLastLine) {
                    handleSelectAllAtLine(windowLast, keyboardWasVisible);
                    return;
                }
            }

            state.selEndLine = fileLastLine;
            int targetStart = Math.max(0, fileLastLine - callback.getPrefetchLines());

            callback.loadWindowAround(
                    targetStart,
                    () -> callback.post(() -> handleSelectAllAtLine(fileLastLine, keyboardWasVisible)));
        };

        if (callback.isIndexReady()) {
            goToEndUsingIndex.run();
            return;
        }

        if (!callback.isIndexBuilding() && !callback.isIndexDisabled()) {
            callback.postDelayed(callback::buildFileIndex, 0);
        }

        callback.countTotalLines(totalLines -> {
            int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
            state.selEndLine = Math.max(0, lastLine);

            Runnable goToEndWithoutIndex = () -> {
                int targetStart = Math.max(0, state.selEndLine - callback.getPrefetchLines());
                callback.loadWindowAround(
                        targetStart,
                        () -> callback.post(() -> handleSelectAllAtLine(state.selEndLine, keyboardWasVisible)));
            };

            if (callback.isIndexDisabled()) {
                goToEndWithoutIndex.run();
                return;
            }

            final int ticket = callback.incrementEditVersion();
            Runnable poll = new Runnable() {
                @Override
                public void run() {
                    if (ticket != callback.getEditVersion()) return;

                    if (callback.getSourceFile() == null) {
                        callback.setDisable(false);
                        callback.showLoadingCircle(false);
                        callback.invalidate();
                        callback.showPopupAtSelection();
                        if (keyboardWasVisible) callback.showKeyboard();
                        return;
                    }

                    if (callback.isIndexDisabled()) {
                        goToEndWithoutIndex.run();
                    } else if (callback.isIndexReady()) {
                        goToEndUsingIndex.run();
                    } else {
                        callback.postDelayed(this, 80);
                    }
                }
            };
            callback.postDelayed(poll, 80);
        });
    }

    private void handleSelectAllNoFile(boolean keyboardWasVisible) {
        synchronized (callback.getLinesWindow()) {
            if (callback.getLinesWindow().isEmpty()) callback.getLinesWindow().add("");
            if (callback.getWindowStartLine() != 0) callback.getWindowStartLine();
        }

        state.selEndLine = Math.max(0, callback.getWindowStartLine() + callback.getLinesWindow().size() - 1);
        String lastLineText = callback.getLineTextForRender(state.selEndLine);
        state.selEndChar = lastLineText.length();
        callback.setCursorLineAndChar(state.selEndLine, state.selEndChar);

        callback.scrollToLineFastForSelectAll(state.selEndLine, state.selEndChar);

        callback.setDisable(false);
        callback.showLoadingCircle(false);
        callback.invalidate();
        callback.requestFocus();
        callback.showPopupAtSelection();

        callback.post(() -> {
            callback.requestFocus();
            if (keyboardWasVisible) callback.showKeyboard();
            callback.restartInput();
        });
    }

    private void handleSelectAllEof(boolean keyboardWasVisible) {
        int windowLast = Math.max(0, callback.getWindowStartLine() + callback.getLinesWindow().size() - 1);
        state.selEndLine = windowLast;
        String lastLineText = callback.getLineTextForRender(windowLast);
        state.selEndChar = lastLineText.length();
        callback.setCursorLineAndChar(windowLast, state.selEndChar);

        callback.scrollToLineFastForSelectAll(windowLast, state.selEndChar);

        callback.setDisable(false);
        callback.showLoadingCircle(false);
        callback.invalidate();
        callback.requestFocus();
        callback.showPopupAtSelection();

        callback.post(() -> {
            callback.requestFocus();
            if (keyboardWasVisible) callback.showKeyboard();
            callback.restartInput();
        });
    }

    private void handleSelectAllAtLine(int fileLastLine, boolean keyboardWasVisible) {
        callback.post(() -> {
            String lastLineText = callback.getLineTextForRender(fileLastLine);
            state.selEndChar = lastLineText.length();
            callback.setCursorLineAndChar(fileLastLine, state.selEndChar);

            callback.scrollToLineFastForSelectAll(fileLastLine, state.selEndChar);

            callback.setDisable(false);
            callback.showLoadingCircle(false);
            callback.invalidate();
            callback.requestFocus();
            callback.showPopupAtSelection();

            callback.post(() -> {
                callback.requestFocus();
                if (keyboardWasVisible) callback.showKeyboard();
                callback.restartInput();
            });
        });
    }

    public boolean isPositionInsideSelection(int line, int ch) {
        if (!hasSelection()) return false;
        int sL = state.selStartLine;
        int sC = state.selStartChar;
        int eL = state.selEndLine;
        int eC = state.selEndChar;
        if (callback.comparePos(sL, sC, eL, eC) > 0) {
            sL = state.selEndLine;
            sC = state.selEndChar;
            eL = state.selStartLine;
            eC = state.selStartChar;
        }
        if (callback.comparePos(line, ch, sL, sC) < 0) return false;
        return callback.comparePos(line, ch, eL, eC) <= 0;
    }

    public void setSelectionInternal(int sL, int sC, int eL, int eC) {
        int startL = sL, startC = sC, endL = eL, endC = eC;
        if (callback.comparePos(startL, startC, endL, endC) > 0) {
            int tL = startL, tC = startC;
            startL = endL;
            startC = endC;
            endL = tL;
            endC = tC;
        }
        setSelection(startL, Math.max(0, startC), endL, Math.max(0, endC), false);
        setSelectAllState(false, false);
        callback.hidePopup();
    }

    public void clearSelectionStateAfterDelete() {
        clearSelection();
        callback.hidePopup();
    }

    public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
        setSelectionInternal(sL, sC, eL, eC);
        callback.setCursorPositionNoClear(cursorLine, cursorChar);
    }

    public int getSelStartLine() {
        return state.selStartLine;
    }

    public int getSelStartChar() {
        return state.selStartChar;
    }

    public int getSelEndLine() {
        return state.selEndLine;
    }

    public int getSelEndChar() {
        return state.selEndChar;
    }

    public void beginLineNumberSelection(int line) {
        int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(
                line, callback.isEof(), callback.getWindowStartLine(), callback.getLinesWindow().size());
        if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(callback.getLineTextForRender(clamped))) return;
        callback.clearActiveSuggestion();
        state.setLineNumberSelecting(true, clamped);
        state.setSelectAllState(false, false);
        String lineText = callback.getLineTextForRender(clamped);
        setSelection(clamped, 0, clamped, lineText.length(), true);
        callback.setCursorPositionNoClear(clamped, state.selEndChar);
        callback.hidePopup();
        callback.invalidate();
    }

    public void updateLineNumberSelection(int line) {
        if (!state.isLineNumberSelecting()) return;
        int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(
                line, callback.isEof(), callback.getWindowStartLine(), callback.getLinesWindow().size());
        if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(callback.getLineTextForRender(clamped))) return;
        int anchorLine = state.lineNumberSelectAnchorLine;
        int startLine = Math.min(anchorLine, clamped);
        int endLine = Math.max(anchorLine, clamped);
        callback.scrollToLineFastForSelectAll(endLine, 0);
        String endLineText = callback.getLineTextForRender(endLine);
        setSelection(startLine, 0, endLine, endLineText.length(), true);
        callback.setCursorPositionNoClear(endLine, state.selEndChar);
        state.setLineNumberSelecting(true, anchorLine);
        callback.hidePopup();
        callback.invalidate();
    }

    @Nullable
    public String getSelectedText() {
        if (!state.hasSelection()) return null;
        if (shouldHideCopyCutForSelection()) return null;

        int sL = state.selStartLine, sC = state.selStartChar, eL = state.selEndLine, eC = state.selEndChar;
        if (callback.comparePos(sL, sC, eL, eC) > 0) {
            int tL = sL, tC = sC;
            sL = eL;
            sC = eC;
            eL = tL;
            eC = tC;
        }
        return textBuilder.buildSelectedTextBlocking(sL, sC, eL, eC);
    }

    public boolean shouldHideCopyCutForSelection() {
        if (!state.hasSelection()) return true;

        int sL = state.selStartLine, eL = state.selEndLine;
        if (sL > eL) {
            int t = sL;
            sL = eL;
            eL = t;
        }
        long lines = (long) eL - (long) sL + 1L;
        return lines > callback.getHideCopyCutMaxLines();
    }

    public void pasteFromClipboard() {
        callback.invalidatePendingIOForEdit();
        callback.incrementEditVersion();
        callback.clearActiveSuggestion();

        ClipboardManager cm = (ClipboardManager) callback.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData cd = cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) return;
        CharSequence txt = cd.getItemAt(0).coerceToText(callback.getContext());
        if (txt == null) return;
        callback.setCursorLineAndChar(callback.getCursorLine(), callback.getCursorChar());
        callback.insertTextAtCursor(txt.toString());
        callback.inlinePredictionUpdate();
    }
}
