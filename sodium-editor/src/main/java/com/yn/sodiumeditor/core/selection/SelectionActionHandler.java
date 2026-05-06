package com.yn.sodiumeditor.core.selection;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.io.File;

/**
 * Handles complex selection actions like selectAll and multi-line replacement.
 */
public class SelectionActionHandler {
    private final SodiumEditor editor;
    private final Selection selection;

    public SelectionActionHandler(SodiumEditor editor, Selection selection) {
        FunctionLog.f("SelectionActionHandler", "SelectionActionHandler", editor, selection);
        this.editor = editor;
        this.selection = selection;
    }

    public void selectAll() {
        FunctionLog.f("SelectionActionHandler", "selectAll");
        editor.autoCompletion.clearActiveSuggestion();
        final boolean keyboardWasVisible = editor.view.keyboardHeight > 0;
        if (editor.wordWrap.isWordWrapEnabled) {
            int widthPx = Math.max(1, Math.round(editor.wordWrap.getWrapWidth()));
            if (editor.wordWrap.isWrapMetricsUsableForWindow(widthPx)) editor.wordWrap.cancelWrapWorkForPriority();
        }
        editor.view.setDisable(true); editor.loadingCircle.showLoadingCircle(true);

        selection.isSelectAllActive = true;
        selection.isEntireFileSelected = true;
        selection.hasSelection = true;
        selection.selStartLine = 0; selection.selStartChar = 0;

        if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
            synchronized (editor.windowRender.linesWindow) {
                if (editor.windowRender.linesWindow.isEmpty()) editor.windowRender.linesWindow.add("");
                editor.windowRender.windowStartLine = 0; editor.fileIO.isEof = true;
            }
            selection.selEndLine = Math.max(0, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1);
            String lastText = editor.windowRender.getLineTextForRender(selection.selEndLine);
            selection.selEndChar = lastText.length();
            editor.cursor.cursorLine = selection.selEndLine; editor.cursor.cursorChar = selection.selEndChar;
            editor.scroll.scrollToLineFastForSelectAll(selection.selEndLine, selection.selEndChar);
            finishSelectAll(keyboardWasVisible);
            return;
        }

        if (editor.fileIO.isEof) {
            int winLast = Math.max(0, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1);
            selection.selEndLine = winLast;
            String lastText = editor.windowRender.getLineTextForRender(winLast);
            selection.selEndChar = lastText.length();
            editor.cursor.cursorLine = winLast; editor.cursor.cursorChar = selection.selEndChar;
            editor.scroll.scrollToLineFastForSelectAll(winLast, selection.selEndChar);
            finishSelectAll(keyboardWasVisible);
            return;
        }

        Runnable goToEndUsingIndex = () -> {
            if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null) return;
            int fileLast; synchronized (editor.fileIO.lineOffsetsLock) { fileLast = Math.max(0, editor.fileIO.lineOffsets.length - 1); }
            selection.selEndLine = fileLast;
            editor.fileIO.loadWindowAround(Math.max(0, fileLast - editor.windowRender.prefetchLines), () -> editor.post(() -> {
                String lastText = editor.windowRender.getLineTextForRender(fileLast);
                selection.selEndChar = (lastText != null ? lastText.length() : 0);
                editor.cursor.cursorLine = fileLast; editor.cursor.cursorChar = selection.selEndChar;
                editor.scroll.scrollToLineFastForSelectAll(fileLast, selection.selEndChar);
                finishSelectAll(keyboardWasVisible);
            }), false);
        };

        if (editor.fileIO.isIndexReady) { goToEndUsingIndex.run(); return; }
        if (!editor.fileIO.isIndexBuilding && !editor.fileIO.isIndexDisabled) editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);

        editor.fileIO.countTotalLines(total -> {
            int lastLine = Math.max(0, total > 0 ? total - 1 : 0);
            selection.selEndLine = lastLine;
            if (editor.fileIO.isIndexDisabled) {
                editor.fileIO.loadWindowAround(Math.max(0, lastLine - editor.windowRender.prefetchLines), () -> editor.post(() -> {
                    String lastText = editor.windowRender.getLineTextForRender(lastLine);
                    selection.selEndChar = (lastText != null ? lastText.length() : 0);
                    editor.cursor.cursorLine = lastLine; editor.cursor.cursorChar = selection.selEndChar;
                    editor.scroll.scrollToLineFastForSelectAll(lastLine, selection.selEndChar);
                    finishSelectAll(keyboardWasVisible);
                }), false);
                return;
            }
            final int ticket = editor.editOperators.editVersion.incrementAndGet();
            Runnable poll = new Runnable() {
                @Override public void run() {
                    if (ticket != editor.editOperators.editVersion.get()) return;
                    if (editor.fileIO.sourceFile == null) { editor.view.setDisable(false); editor.loadingCircle.showLoadingCircle(false); editor.invalidate(); editor.popup.showPopupAtSelection(); if (keyboardWasVisible) editor.ime.showKeyboard(); return; }
                    if (editor.fileIO.isIndexDisabled) { /* repeat goToEndWithoutIndex logic */ }
                    else if (editor.fileIO.isIndexReady) goToEndUsingIndex.run();
                    else editor.caret.mainHandler.postDelayed(this, 80);
                }
            };
            editor.caret.mainHandler.post(poll);
        });
    }

    private void finishSelectAll(boolean keyboardWasVisible) {
        FunctionLog.f("SelectionActionHandler", "finishSelectAll", keyboardWasVisible);
        selection.syncToState();
        editor.view.setDisable(false); editor.loadingCircle.showLoadingCircle(false);
        editor.invalidate(); editor.requestFocus(); editor.popup.showPopupAtSelection();
        editor.post(() -> {
            editor.requestFocus(); if (keyboardWasVisible) editor.ime.showKeyboard();
            InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(editor);
        });
    }

    public void replaceSelectionWithText(String insertText) {
        FunctionLog.f("SelectionActionHandler", "replaceSelectionWithText", insertText);
        if (editor.view.isReadOnly) return;
        editor.fileIO.invalidatePendingIOForEdit();
        final int opToken = editor.editOperators.editVersion.incrementAndGet();
        editor.autoCompletion.clearActiveSuggestion();
        if (insertText == null) insertText = "";
        if (!selection.hasSelection) { if (!insertText.isEmpty()) editor.editOperators.insertTextAtCursor(insertText); editor.autoCompletion.updateSuggestion(); return; }

        int sL = selection.selStartLine, sC = selection.selStartChar, eL = selection.selEndLine, eC = selection.selEndChar;
        if (selection.state.comparePos(sL, sC, eL, eC) > 0) { int tL = sL, tC = sC; sL = eL; sC = eC; eL = tL; eC = tC; }
        final int beforeLine = editor.cursor.cursorLine, beforeChar = editor.cursor.cursorChar;
        String removedText = null;
        if (Math.abs(eL - sL) <= 5000) { removedText = editor.fileIO.readRangeText(sL, sC, eL, eC); if (removedText != null && removedText.length() > EditOperators.UNDO_TEXT_LIMIT) removedText = null; }
        int removedNl = editor.editOperators.countNewlines(removedText);
        if (removedText == null && eL >= sL) removedNl = Math.max(0, eL - sL);
        int insertedNl = editor.editOperators.countNewlines(insertText);

        final boolean selectAllLike = selection.isSelectAllActive || selection.isEntireFileSelected;
        editor.loadingCircle.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

        if (selectAllLike) {
            handleSelectAllReplace(insertText, sL, sC, eL, eC, removedText, beforeLine, beforeChar, removedNl, insertedNl);
            return;
        }

        if (sL == eL && insertText.indexOf('\n') < 0) {
            handleSingleLineReplace(insertText, sL, sC, eL, eC, removedText, beforeLine, beforeChar, removedNl, insertedNl);
            return;
        }

        final EditOp.CursorTarget target = editor.editOperators.computeCursorAfterInsert(sL, sC, insertText);
        boolean fullyInWindow = (sL >= editor.windowRender.windowStartLine) && (eL < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
        if (fullyInWindow) editor.windowRender.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
        else { editor.cursor.cursorLine = sL; editor.cursor.cursorChar = sC; }

        selection.state.clearSelectionStateAfterDelete();
        editor.scroll.keepCursorVisibleHorizontally(); editor.loadingCircle.endLargeEditUi(false);

        if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
            if (!fullyInWindow) { editor.fileIO.ensureLineInWindow(sL, true); editor.fileIO.ensureLineInWindow(eL, true); editor.windowRender.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target); }
            finalizeAction(removedNl, insertedNl, sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
            return;
        }

        editor.editOperators.rewriteReplaceRangeAsync(opToken, editor.fileIO.sourceFile, sL, sC, eL, eC, insertText, target, false);
        finalizeAction(removedNl, insertedNl, sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
    }

    private void handleSelectAllReplace(String insertText, int sL, int sC, int eL, int eC, String removedText, int beforeL, int beforeC, int remNl, int insNl) {
        FunctionLog.f("SelectionActionHandler", "handleSelectAllReplace", insertText, sL, sC, eL, eC, removedText, beforeL, beforeC, remNl, insNl);
        synchronized (editor.windowRender.linesWindow) { editor.windowRender.linesWindow.clear(); editor.windowRender.linesWindow.add(""); editor.windowRender.windowStartLine = 0; editor.fileIO.isEof = true; }
        synchronized (editor.fileIO.directLineCache) { editor.fileIO.directLineCache.clear(); }
        synchronized (editor.windowRender.modifiedLines) { editor.windowRender.modifiedLines.clear(); }
        synchronized (editor.windowRender.lineWidthCache) { editor.windowRender.lineWidthCache.clear(); }
        editor.windowRender.clearStreamedLineCaches(); editor.bracketGuides.invalidateBracketGuideCache(true);
        if (editor.codeFold.isCodeFoldingEnabled) { editor.codeFold.foldRanges.clear(); editor.codeFold.foldIntervals.clear(); editor.codeFold.invalidateFoldCaches(); }
        editor.windowRender.currentMaxWindowLineWidth = 0f; editor.windowRender.globalMaxLineWidth = 0f;
        editor.fileIO.isFileCleared = true;
        synchronized (editor.fileIO.lineOffsetsLock) { editor.fileIO.lineOffsets = new long[0]; }
        editor.fileIO.isIndexReady = false; editor.fileIO.isIndexBuilding = false;
        editor.cursor.cursorLine = 0; editor.cursor.cursorChar = 0;
        selection.selStartLine = 0; selection.selEndLine = 0; selection.selStartChar = 0; selection.selEndChar = 0;
        editor.scroll.scrollY = 0; editor.scroll.scrollX = 0; selection.state.clearSelectionStateAfterDelete();

        if (!insertText.isEmpty()) {
            String[] lines = insertText.split("\n", -1);
            synchronized (editor.windowRender.linesWindow) { editor.windowRender.linesWindow.set(0, lines[0]); for (int i = 1; i < lines.length; i++) editor.windowRender.linesWindow.add(i, lines[i]); }
            EditOp.CursorTarget nPos = editor.editOperators.computeCursorAfterInsert(0, 0, insertText);
            editor.cursor.cursorLine = nPos.line; editor.cursor.cursorChar = nPos.ch;
        }
        editor.wordWrap.onLineCountChanged(); editor.loadingCircle.endLargeEditUi(true);
        editor.windowRender.recalculateMaxLineWidth(); editor.requestLayout();
        finalizeAction(remNl, insNl, sL, sC, eL, eC, removedText, insertText, beforeL, beforeC);
    }

    private void handleSingleLineReplace(String insertText, int sL, int sC, int eL, int eC, String removedText, int beforeL, int beforeC, int remNl, int insNl) {
        FunctionLog.f("SelectionActionHandler", "handleSingleLineReplace", insertText, sL, sC, eL, eC, removedText, beforeL, beforeC, remNl, insNl);
        editor.fileIO.ensureLineInWindow(sL, true);
        if (editor.fileIO.isWindowLoading && (sL < editor.windowRender.windowStartLine || sL >= editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size())) {
            editor.post(() -> replaceSelectionWithText(insertText)); return;
        }
        int local = sL - editor.windowRender.windowStartLine;
        if (local >= 0 && local < editor.windowRender.linesWindow.size()) {
            synchronized (editor.windowRender.linesWindow) {
                String line = editor.windowRender.getLineFromWindowLocal(local); if (line == null) line = "";
                int a = Math.max(0, Math.min(sC, line.length())), b = Math.max(0, Math.min(eC, line.length()));
                if (b < a) { int t = a; a = b; b = t; }
                String merged = line.substring(0, a) + insertText + line.substring(b);
                editor.view.updateLocalLine(local, merged); editor.windowRender.modifiedLines.put(sL, merged);
                editor.cursor.cursorLine = sL; editor.cursor.cursorChar = a + insertText.length();
                editor.view.computeWidthForLine(sL, merged); editor.windowRender.recalculateMaxLineWidth();
            }
        }
        selection.state.clearSelectionStateAfterDelete(); editor.invalidate(); editor.scroll.keepCursorVisibleHorizontally(); editor.loadingCircle.endLargeEditUi(false);
        finalizeAction(remNl, insNl, sL, sC, eL, eC, removedText, insertText, beforeL, beforeC);
    }

    private void finalizeAction(int remNl, int insNl, int sL, int sC, int eL, int eC, String rem, String ins, int bL, int bC) {
        FunctionLog.f("SelectionActionHandler", "finalizeAction", remNl, insNl, sL, sC, eL, eC, rem, ins, bL, bC);
        editor.autoCompletion.updateSuggestion(); editor.editOperators.lineCountDelta += (insNl - remNl);
        Log.i(
                "LineNumber",
                "finalizeAction"
                        + " remNl="
                        + remNl
                        + " insNl="
                        + insNl
                        + " newDelta="
                        + editor.editOperators.lineCountDelta
                        + " line0='"
                        + editor.windowRender.getLineTextForRender(0)
                        + "' line1='"
                        + editor.windowRender.getLineTextForRender(1)
                        + "' windowSize="
                        + editor.windowRender.linesWindow.size());
        selection.recordReplaceSelectionEdit(sL, sC, eL, eC, rem, ins, bL, bC);
    }
}
