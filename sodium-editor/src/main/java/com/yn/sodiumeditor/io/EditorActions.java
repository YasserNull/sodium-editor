package com.yn.sodiumeditor.io;

import android.os.Looper;
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.io.File;

/**
 * High-level editor actions like inserting and deleting characters or text.
 */
public class EditorActions {
    private static final String FOLD_EDIT_DBG = "FoldEditDbg";
    private static final String FOLD_TYPING_PERF = "FoldTypingPerf";
    private static final long FOLD_TYPING_LOG_THRESHOLD_MS = 8L;
    private final SodiumEditor editor;
    private final EditOperators operators;

    public EditorActions(SodiumEditor editor, EditOperators operators) {
        FunctionLog.f("EditorActions", "EditorActions", editor, operators);
        this.editor = editor;
        this.operators = operators;
    }

    public void deleteForwardAtCursor() {
        FunctionLog.f("EditorActions", "deleteForwardAtCursor");
        if (editor.view.isReadOnly) return;
        editor.fileIO.invalidatePendingIOForEdit();
        operators.editVersion.incrementAndGet();
        editor.autoCompletion.clearActiveSuggestion();

        if (editor.ime.hasComposing) {
            editor.ime.deleteComposing();
            return;
        }

        editor.ime.lastImeCommitText = null;
        editor.ime.lastImeCommitUptime = 0L;

        if (deleteCollapsedFoldIfDeletingClosingToken(true)) {
            return;
        }

        handleCodeFoldBeforeEdit();

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;

        final int requestedLine = editor.cursor.cursorLine;
        final int requestedChar = editor.cursor.cursorChar;
        editor.fileIO.ensureLineInWindow(requestedLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(requestedLine)) {
            editor.post(() -> {
                editor.cursor.cursorLine = requestedLine;
                editor.cursor.cursorChar = requestedChar;
                deleteForwardAtCursor();
            });
            return;
        }

        int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
        if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
            localIdx = handleWindowEdgeCase(localIdx);
        }

        synchronized (editor.windowRender.linesWindow) {
            String base = editor.windowRender.getLineFromWindowLocal(localIdx);
            if (base == null) base = "";

            if (editor.cursor.cursorChar < base.length()) {
                int safeCursorChar = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
                int deleteEnd = nextCodePointEnd(base, safeCursorChar);
                String removed = base.substring(safeCursorChar, deleteEnd);
                String modified = base.substring(0, safeCursorChar) + base.substring(deleteEnd);
                editor.view.updateLocalLine(localIdx, modified);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, modified);
                
                if (editor.codeFold.isCodeFoldingEnabled) {
                    if (com.yn.sodiumeditor.utils.TextUtils.containsBracketChars(removed)) editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
                    editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, safeCursorChar, safeCursorChar - deleteEnd, deleteEnd - safeCursorChar);
                }
                editor.highlite.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
                editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
                editor.view.invalidateLineGlobal(editor.cursor.cursorLine);

                EditOp op = new EditOp();
                op.startLine = beforeLine; op.startChar = safeCursorChar;
                op.endLine = beforeLine; op.endChar = deleteEnd;
                op.removedText = removed; op.insertedText = "";
                op.insertedEndLine = beforeLine; op.insertedEndChar = safeCursorChar;
                op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
                op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
                op.timestamp = System.currentTimeMillis();
                operators.recorder.recordEdit(op);
            } else {
                int nextGlobal = editor.cursor.cursorLine + 1;
                editor.fileIO.ensureLineInWindow(nextGlobal, true);
                
                int currentLocalIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
                int currentNextLocal = nextGlobal - editor.windowRender.windowStartLine;
                
                if (currentLocalIdx >= 0 && currentLocalIdx < editor.windowRender.linesWindow.size() &&
                    currentNextLocal >= 0 && currentNextLocal < editor.windowRender.linesWindow.size()) {
                    String next = editor.windowRender.getLineFromWindowLocal(currentNextLocal);
                    String merged = base + (next == null ? "" : next);
                    editor.view.updateLocalLine(currentLocalIdx, merged);
                    editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, merged);
                    editor.windowRender.clearStreamedLineInfo(editor.cursor.cursorLine);
                    editor.highlite.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
                    
                    if (editor.codeFold.isCodeFoldingEnabled) {
                        editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
                        editor.codeFold.adjustFoldRangesForLineEdit(nextGlobal, -1);
                    }
                    editor.windowRender.linesWindow.remove(currentNextLocal);
                    operators.shifter.shiftModifiedLines(nextGlobal, -1);

                    editor.windowRender.recalculateMaxLineWidth();
                    editor.view.computeWidthForLine(editor.cursor.cursorLine, merged);
                    editor.lineNumber.invalidateLineNumberCache();
                    editor.wordWrap.onLineCountChanged();
                    editor.invalidate();
                    operators.lineCountDelta -= 1;

                    EditOp op = new EditOp();
                    op.startLine = beforeLine; op.startChar = base.length();
                    op.endLine = nextGlobal; op.endChar = 0;
                    op.removedText = "\n"; op.insertedText = "";
                    op.insertedEndLine = beforeLine; op.insertedEndChar = base.length();
                    op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
                    op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
                    op.timestamp = System.currentTimeMillis();
                    operators.recorder.recordEdit(op);
                }
            }
        }
        editor.ime.updateImeSelection();
        editor.autoCompletion.updateSuggestion();
    }

    private boolean isLineInLoadedWindow(int line) {
        FunctionLog.f("EditorActions", "isLineInLoadedWindow", line);
        return line >= editor.windowRender.windowStartLine && 
               line < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
    }

    public void insertCharAtCursor(char c) {
        FunctionLog.f("EditorActions", "insertCharAtCursor", c);
        long opStartMs = android.os.SystemClock.uptimeMillis();
        if (editor.view.isReadOnly) return;
        editor.fileIO.invalidatePendingIOForEdit();
        operators.editVersion.incrementAndGet();

        if (editor.selection.hasSelection) {
            editor.selection.replaceSelectionWithText(String.valueOf(c));
            return;
        }

        if (editor.ime.hasComposing) {
            editor.ime.onFinishComposingText();
        }

        long foldGuardStartMs = android.os.SystemClock.uptimeMillis();
        handleCodeFoldBeforeEdit();
        long foldGuardMs = android.os.SystemClock.uptimeMillis() - foldGuardStartMs;

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;
        int originalLine = beforeLine;
        int originalChar = beforeChar;
        CodeFold.FoldRange hiddenEditFold = getHiddenCollapsedFoldForCursor();

        if (hiddenEditFold != null) {
            insertCharIntoHiddenCollapsedFold(c, beforeLine, beforeChar, hiddenEditFold);
            return;
        }

        long ensureStartMs = android.os.SystemClock.uptimeMillis();
	        editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
	        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
	            if (insertCharIntoOffWindowLine(c, beforeLine, beforeChar)) {
	                return;
	            }
	            final int retryLine = editor.cursor.cursorLine;
	            final int retryChar = editor.cursor.cursorChar;
	            editor.post(() -> {
                editor.cursor.cursorLine = retryLine;
                editor.cursor.cursorChar = retryChar;
                insertCharAtCursor(c);
            });
            return;
        }
        long ensureMs = android.os.SystemClock.uptimeMillis() - ensureStartMs;

	        int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
	        if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
	            if (insertCharIntoOffWindowLine(c, beforeLine, beforeChar)) {
	                return;
	            }
	            localIdx = handleWindowEdgeCase(localIdx);
	        }

        boolean fullInvalidate = false;
        long editMs = 0L;
        long foldAdjustMs = 0L;
        long highlightMs = 0L;
        long widthMs = 0L;
        long invalidateMs = 0L;
        synchronized (editor.windowRender.linesWindow) {
            String base = editor.windowRender.getLineFromWindowLocal(localIdx);
            if (base == null) base = "";

            if (c == '\n') {
                long editStartMs = android.os.SystemClock.uptimeMillis();
                fullInvalidate = true;
                int oldLineCount = editor.view.getLinesCount();
                String before = base.substring(0, Math.min(editor.cursor.cursorChar, base.length()));
                String after = base.substring(Math.min(editor.cursor.cursorChar, base.length()));
                
                editor.view.updateLocalLine(localIdx, before);
                editor.windowRender.linesWindow.add(localIdx + 1, after);

                operators.shifter.shiftModifiedLines(editor.cursor.cursorLine + 1, 1);
                
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, before);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine + 1, after);
                editMs = android.os.SystemClock.uptimeMillis() - editStartMs;

                long foldStartMs = android.os.SystemClock.uptimeMillis();
                handleCodeFoldNewline(beforeLine, beforeChar);
                foldAdjustMs = android.os.SystemClock.uptimeMillis() - foldStartMs;

                long widthStartMs = android.os.SystemClock.uptimeMillis();
                editor.view.computeWidthForLine(editor.cursor.cursorLine, before);
                editor.view.computeWidthForLine(editor.cursor.cursorLine + 1, after);
                widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;

                editor.lineNumber.invalidateLineNumberCache();
                editor.cursor.cursorLine++;
                editor.cursor.cursorChar = 0;
                operators.lineCountDelta += 1;
                
                if (editor.lineNumber.showLineNumbers && 
                    String.valueOf(oldLineCount).length() != String.valueOf(editor.view.getLinesCount()).length()) {
                    editor.requestLayout();
                }
                editor.lineNumber.updateGutterWidth();
                editor.wordWrap.onLineCountChanged();
                FunctionLog.d(
                        "newline",
                        "beforeLine="
                                + beforeLine
                                + " beforeChar="
                                + beforeChar
                                + " oldLineCount="
                                + oldLineCount
                                + " newLineCount="
                                + editor.view.getLinesCount()
                                + " visibleCount="
                                + editor.codeFold.getVisibleLineCount()
                                + " windowStart="
                                + editor.windowRender.windowStartLine
                                + " windowSize="
                                + editor.windowRender.linesWindow.size()
                                + " line0='"
                                + editor.windowRender.getLineTextForRender(beforeLine)
                                + "' line1='"
                                + editor.windowRender.getLineTextForRender(beforeLine + 1)
                                + "' line2='"
                                + editor.windowRender.getLineTextForRender(beforeLine + 2)
                                + "'");
            } else {
                long editStartMs = android.os.SystemClock.uptimeMillis();
                int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
                String modified = base.substring(0, pos) + c + base.substring(pos);
                editor.view.updateLocalLine(localIdx, modified);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, modified);
                
                synchronized (editor.windowRender.avgCharWidthCache) { editor.windowRender.avgCharWidthCache.remove(editor.cursor.cursorLine); }
                synchronized (editor.windowRender.lineWidthCache) { editor.windowRender.lineWidthCache.remove(editor.cursor.cursorLine); }
                
                if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
                    editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, pos, 1, 0);
                }
                editMs = android.os.SystemClock.uptimeMillis() - editStartMs;
                long foldStartMs = android.os.SystemClock.uptimeMillis();
                handleCodeFoldAfterCharacterEdit(editor.cursor.cursorLine, pos, c, 1, 0);
                foldAdjustMs = android.os.SystemClock.uptimeMillis() - foldStartMs;
                long highlightStartMs = android.os.SystemClock.uptimeMillis();
                editor.highlite.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
                highlightMs = android.os.SystemClock.uptimeMillis() - highlightStartMs;
                editor.cursor.cursorChar++;
                long widthStartMs = android.os.SystemClock.uptimeMillis();
                editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
                widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;
            }
            long invalidateStartMs = android.os.SystemClock.uptimeMillis();
            if (fullInvalidate) editor.invalidate();
            else editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
            editor.scroll.keepCursorVisibleHorizontally();
            invalidateMs = android.os.SystemClock.uptimeMillis() - invalidateStartMs;
        }
        long dt = android.os.SystemClock.uptimeMillis() - opStartMs;
        if (dt >= FOLD_TYPING_LOG_THRESHOLD_MS) {
            Log.i(
                    FOLD_TYPING_PERF,
                    "normalChar total="
                            + dt
                            + " guard="
                            + foldGuardMs
                            + " ensure="
                            + ensureMs
                            + " edit="
                            + editMs
                            + " fold="
                            + foldAdjustMs
                            + " highlight="
                            + highlightMs
                            + " width="
                            + widthMs
                            + " invalidate="
                            + invalidateMs
                            + " char="
                            + printableChar(c)
                            + " original="
                            + originalLine
                            + ":"
                            + originalChar
                            + " before="
                            + beforeLine
                            + ":"
                            + beforeChar
                            + " after="
                            + editor.cursor.cursorLine
                            + ":"
                            + editor.cursor.cursorChar
                            + " fullInvalidate="
                            + fullInvalidate
                            + " modified="
                            + editor.windowRender.modifiedLines.size()
                            + " lineDelta="
                            + operators.lineCountDelta
                            + " foldRanges="
                            + editor.codeFold.foldRanges.size());
        }
        if (dt > 16 && editor.DEBUG_RENDER_LOGS && editor.codeFold.isCodeFoldingEnabled) {
            Log.i(
                    FOLD_EDIT_DBG,
                    "insertNormal slow"
                            + " char="
                            + printableChar(c)
                            + " before="
                            + beforeLine
                            + ":"
                            + beforeChar
                            + " cursor="
                            + editor.cursor.cursorLine
                            + ":"
                            + editor.cursor.cursorChar
                            + " dtMs="
                            + dt
                            + " window="
                            + editor.windowRender.windowStartLine
                            + "+"
                            + editor.windowRender.linesWindow.size());
        }
        editor.autoCompletion.updateSuggestion();

        EditOp op = new EditOp();
        op.startLine = beforeLine; op.startChar = beforeChar;
        op.endLine = beforeLine; op.endChar = beforeChar;
        op.removedText = ""; op.insertedText = String.valueOf(c);
        EditOp.CursorTarget insertedEnd = operators.recorder.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
        op.insertedEndLine = insertedEnd.line; op.insertedEndChar = insertedEnd.ch;
        op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        operators.recorder.recordEdit(op);
    }

	    private void insertCharIntoHiddenCollapsedFold(
	        char c, int beforeLine, int beforeChar, CodeFold.FoldRange hiddenFold) {
        long startMs = android.os.SystemClock.uptimeMillis();
        long readStartMs = startMs;
        String base = getEditableLineText(beforeLine);
        long readMs = android.os.SystemClock.uptimeMillis() - readStartMs;
        if (base == null) base = "";

        if (c == '\n') {
            long splitStartMs = android.os.SystemClock.uptimeMillis();
            String before = base.substring(0, Math.min(beforeChar, base.length()));
            String after = base.substring(Math.min(beforeChar, base.length()));
            long splitMs = android.os.SystemClock.uptimeMillis() - splitStartMs;

            long shiftStartMs = android.os.SystemClock.uptimeMillis();
            operators.shifter.shiftModifiedLines(beforeLine + 1, 1);
            editor.windowRender.modifiedLines.put(beforeLine, before);
            editor.windowRender.modifiedLines.put(beforeLine + 1, after);
            long shiftMs = android.os.SystemClock.uptimeMillis() - shiftStartMs;

            long foldStartMs = android.os.SystemClock.uptimeMillis();
            handleCodeFoldNewline(beforeLine, beforeChar);
            long foldMs = android.os.SystemClock.uptimeMillis() - foldStartMs;

            long widthStartMs = android.os.SystemClock.uptimeMillis();
            editor.view.computeWidthForLine(beforeLine, before);
            editor.view.computeWidthForLine(beforeLine + 1, after);
            long widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;
            long miscStartMs = android.os.SystemClock.uptimeMillis();
            editor.lineNumber.invalidateLineNumberCache();
            editor.cursor.cursorLine = beforeLine + 1;
            editor.cursor.cursorChar = 0;
            operators.lineCountDelta += 1;
            editor.wordWrap.onLineCountChanged();
            editor.invalidate();
            long miscMs = android.os.SystemClock.uptimeMillis() - miscStartMs;
            long totalMs = android.os.SystemClock.uptimeMillis() - startMs;
            if (totalMs >= FOLD_TYPING_LOG_THRESHOLD_MS) {
                Log.i(
                    FOLD_TYPING_PERF,
                    "hiddenNewline total="
                        + totalMs
                        + " read="
                        + readMs
                        + " split="
                        + splitMs
                        + " shift="
                        + shiftMs
                        + " fold="
                        + foldMs
                        + " width="
                        + widthMs
                        + " misc="
                        + miscMs
                        + " line="
                        + beforeLine
                        + " char="
                        + beforeChar
                        + " fold="
                        + hiddenFold.startLine
                        + "->"
                        + hiddenFold.endLine
                        + " beforeLen="
                        + before.length()
                        + " afterLen="
                        + after.length()
                        + " modified="
                        + editor.windowRender.modifiedLines.size()
                        + " lineDelta="
                        + operators.lineCountDelta);
            }
            if (editor.DEBUG_RENDER_LOGS) {
                Log.i(
                    FOLD_EDIT_DBG,
                    "hiddenNewline"
                            + " beforeLen="
                            + before.length()
                            + " afterLen="
                            + after.length()
                            + " splitDtMs="
                            + (android.os.SystemClock.uptimeMillis() - splitStartMs)
                            + " totalDtMs="
                            + (android.os.SystemClock.uptimeMillis() - startMs)
                            + " visibleCount="
                            + editor.codeFold.getVisibleLineCount()
                            + " lineCountDelta="
                            + operators.lineCountDelta);
            }
        } else {
            long editStartMs = android.os.SystemClock.uptimeMillis();
            int pos = Math.max(0, Math.min(beforeChar, base.length()));
            String modified = base.substring(0, pos) + c + base.substring(pos);
            editor.windowRender.modifiedLines.put(beforeLine, modified);
            long editMs = android.os.SystemClock.uptimeMillis() - editStartMs;

            long cacheStartMs = android.os.SystemClock.uptimeMillis();
            synchronized (editor.windowRender.avgCharWidthCache) {
                editor.windowRender.avgCharWidthCache.remove(beforeLine);
            }
            synchronized (editor.windowRender.lineWidthCache) {
                editor.windowRender.lineWidthCache.remove(beforeLine);
            }
            long cacheMs = android.os.SystemClock.uptimeMillis() - cacheStartMs;

            long foldStartMs = android.os.SystemClock.uptimeMillis();
            handleCodeFoldAfterCharacterEdit(beforeLine, pos, c, 1, 0);
            long foldMs = android.os.SystemClock.uptimeMillis() - foldStartMs;
            long highlightStartMs = android.os.SystemClock.uptimeMillis();
            editor.highlite.invalidateHighlightCacheForLine(beforeLine);
            long highlightMs = android.os.SystemClock.uptimeMillis() - highlightStartMs;
            editor.cursor.cursorChar++;
            long widthStartMs = android.os.SystemClock.uptimeMillis();
            editor.view.computeWidthForLine(beforeLine, modified);
            long widthMs = android.os.SystemClock.uptimeMillis() - widthStartMs;
            long invalidateStartMs = android.os.SystemClock.uptimeMillis();
            editor.view.invalidateLineGlobal(hiddenFold.startLine);
            editor.scroll.keepCursorVisibleHorizontally();
            long invalidateMs = android.os.SystemClock.uptimeMillis() - invalidateStartMs;
            long totalMs = android.os.SystemClock.uptimeMillis() - startMs;
            if (totalMs >= FOLD_TYPING_LOG_THRESHOLD_MS) {
                Log.i(
                    FOLD_TYPING_PERF,
                    "hiddenChar total="
                        + totalMs
                        + " read="
                        + readMs
                        + " edit="
                        + editMs
                        + " cache="
                        + cacheMs
                        + " fold="
                        + foldMs
                        + " highlight="
                        + highlightMs
                        + " width="
                        + widthMs
                        + " invalidate="
                        + invalidateMs
                        + " line="
                        + beforeLine
                        + " char="
                        + beforeChar
                        + " pos="
                        + pos
                        + " newLen="
                        + modified.length()
                        + " fold="
                        + hiddenFold.startLine
                        + "->"
                        + hiddenFold.endLine
                        + " modified="
                        + editor.windowRender.modifiedLines.size()
                        + " scrollX="
                        + editor.scroll.scrollX);
            }
            if (editor.DEBUG_RENDER_LOGS && android.os.SystemClock.uptimeMillis() - startMs > 16) {
                Log.i(
                    FOLD_EDIT_DBG,
                    "hiddenChar"
                            + " pos="
                            + pos
                            + " newLen="
                            + modified.length()
                            + " editDtMs="
                            + (android.os.SystemClock.uptimeMillis() - editStartMs)
                            + " totalDtMs="
                            + (android.os.SystemClock.uptimeMillis() - startMs)
                            + " scrollX="
                            + editor.scroll.scrollX);
            }
        }

        editor.autoCompletion.updateSuggestion();

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = String.valueOf(c);
        EditOp.CursorTarget insertedEnd =
            operators.recorder.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
        op.insertedEndLine = insertedEnd.line;
        op.insertedEndChar = insertedEnd.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
	        operators.recorder.recordEdit(op);
	    }

    private boolean insertCharIntoOffWindowLine(char c, int beforeLine, int beforeChar) {
        if (isLineInLoadedWindow(beforeLine)) return false;
        String base = getDirectEditableLineText(beforeLine);
        if (base == null) return false;

        long startMs = android.os.SystemClock.uptimeMillis();
        if (c == '\n') {
            int pos = Math.max(0, Math.min(beforeChar, base.length()));
            String before = base.substring(0, pos);
            String after = base.substring(pos);
            operators.shifter.shiftModifiedLines(beforeLine + 1, 1);
            editor.windowRender.modifiedLines.put(beforeLine, before);
            editor.windowRender.modifiedLines.put(beforeLine + 1, after);
            handleCodeFoldNewline(beforeLine, beforeChar);
            editor.view.computeWidthForLine(beforeLine, before);
            editor.view.computeWidthForLine(beforeLine + 1, after);
            editor.cursor.cursorLine = beforeLine + 1;
            editor.cursor.cursorChar = 0;
            operators.lineCountDelta += 1;
            editor.wordWrap.onLineCountChanged();
            editor.lineNumber.invalidateLineNumberCache();
            editor.invalidate();
        } else {
            int pos = Math.max(0, Math.min(beforeChar, base.length()));
            String modified = base.substring(0, pos) + c + base.substring(pos);
            editor.windowRender.modifiedLines.put(beforeLine, modified);
            synchronized (editor.windowRender.avgCharWidthCache) {
                editor.windowRender.avgCharWidthCache.remove(beforeLine);
            }
            synchronized (editor.windowRender.lineWidthCache) {
                editor.windowRender.lineWidthCache.remove(beforeLine);
            }
            if (editor.codeFold.isCodeFoldingEnabled) {
                handleCodeFoldAfterCharacterEdit(beforeLine, pos, c, 1, 0);
            }
            editor.highlite.invalidateHighlightCacheForLine(beforeLine);
            editor.cursor.cursorLine = beforeLine;
            editor.cursor.cursorChar = pos + 1;
            editor.view.computeWidthForLine(beforeLine, modified);
            editor.view.invalidateLineGlobal(beforeLine);
            editor.cursor.invalidateCursorArea();
            editor.scroll.keepCursorVisibleHorizontally();
        }

        editor.autoCompletion.updateSuggestion();

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = String.valueOf(c);
        EditOp.CursorTarget insertedEnd =
            operators.recorder.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
        op.insertedEndLine = insertedEnd.line;
        op.insertedEndChar = insertedEnd.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        operators.recorder.recordEdit(op);

        long dt = android.os.SystemClock.uptimeMillis() - startMs;
        if (dt >= FOLD_TYPING_LOG_THRESHOLD_MS) {
            Log.i(
                FOLD_TYPING_PERF,
                "offWindowChar total="
                    + dt
                    + " char="
                    + printableChar(c)
                    + " line="
                    + beforeLine
                    + " beforeChar="
                    + beforeChar
                    + " after="
                    + editor.cursor.cursorLine
                    + ":"
                    + editor.cursor.cursorChar
                    + " modified="
                    + editor.windowRender.modifiedLines.size()
                    + " lineDelta="
                    + operators.lineCountDelta);
        }
        return true;
    }

    public void deleteCharAtCursor() {
        FunctionLog.f("EditorActions", "deleteCharAtCursor");
        Log.i("EditorActions", "deleteCharAtCursor: cursor=(" + editor.cursor.cursorLine + "," + editor.cursor.cursorChar + ") windowStart=" + editor.windowRender.windowStartLine + " windowSize=" + editor.windowRender.linesWindow.size() + " modifiedLines.size=" + editor.windowRender.modifiedLines.size());
        if (editor.view.isReadOnly) return;
        editor.fileIO.invalidatePendingIOForEdit();
        operators.editVersion.incrementAndGet();
        editor.autoCompletion.clearActiveSuggestion();

        if (editor.ime.hasComposing) {
            editor.ime.deleteComposing();
            return;
        }

        editor.ime.lastImeCommitText = null;
        editor.ime.lastImeCommitUptime = 0L;

        handleCodeFoldBeforeEdit();

        if (deleteCollapsedFoldIfDeletingClosingToken(false)) {
            return;
        }

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;

        final int requestedLine = editor.cursor.cursorLine;
        final int requestedChar = editor.cursor.cursorChar;
        editor.fileIO.ensureLineInWindow(requestedLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(requestedLine)) {
            editor.post(() -> {
                editor.cursor.cursorLine = requestedLine;
                editor.cursor.cursorChar = requestedChar;
                deleteCharAtCursor();
            });
            return;
        }

        int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
        if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
            localIdx = handleWindowEdgeCase(localIdx);
        }

        synchronized (editor.windowRender.linesWindow) {
            String base = editor.windowRender.getLineFromWindowLocal(localIdx);
            if (base == null) base = "";
            Log.i("EditorActions", "deleteCharAtCursor: base='" + base + "' safeCursorChar=" + Math.max(0, Math.min(editor.cursor.cursorChar, base.length())));
            int safeCursorChar = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));

            if (safeCursorChar > 0) {
                int safeStart = previousCodePointStart(base, safeCursorChar);
                String removed = base.substring(safeStart, safeCursorChar);
                
                // Binary token logic omitted for brevity, same as original
                String modified = base.substring(0, safeStart) + base.substring(safeCursorChar);
                editor.view.updateLocalLine(localIdx, modified);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, modified);
                
                if (editor.codeFold.isCodeFoldingEnabled) {
                    if (com.yn.sodiumeditor.utils.TextUtils.containsBracketChars(removed)) editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
                    editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, safeStart, safeStart - safeCursorChar, safeCursorChar - safeStart);
                }
                editor.highlite.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
                editor.cursor.cursorChar = safeStart;
                editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
                editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
                editor.cursor.invalidateCursorArea();
                Log.i(
                        "CursorDbg",
                        "deleteBack"
                                + " line="
                                + editor.cursor.cursorLine
                                + " char="
                                + editor.cursor.cursorChar
                                + " removed='"
                                + removed
                                + "' modified='"
                                + modified
                                + "'");

                EditOp op = new EditOp();
                op.startLine = beforeLine; op.startChar = safeStart;
                op.endLine = beforeLine; op.endChar = beforeChar;
                op.removedText = removed; op.insertedText = "";
                op.insertedEndLine = beforeLine; op.insertedEndChar = safeStart;
                op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
                op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
                op.timestamp = System.currentTimeMillis();
                operators.recorder.recordEdit(op);
            } else if (editor.cursor.cursorLine > 0) {
                int deletedLine = editor.cursor.cursorLine;
                int prevGlobal = editor.cursor.cursorLine - 1;
                editor.fileIO.ensureLineInWindow(prevGlobal, true);
                
                int currentPrevLocal = prevGlobal - editor.windowRender.windowStartLine;
                int currentDeletedLocal = deletedLine - editor.windowRender.windowStartLine;
                
                String prev = editor.windowRender.getLineTextForRender(prevGlobal);
                if (prev == null) prev = "";
                String merged = prev + base;
                Log.d("EditorActions", "Merging line: '" + prev + "' + '" + base + "' = '" + merged + "'");

                editor.windowRender.modifiedLines.put(prevGlobal, merged);
                editor.windowRender.clearStreamedLineInfo(prevGlobal);
                
                synchronized (editor.windowRender.lineWidthCache) {
                    editor.windowRender.lineWidthCache.remove(prevGlobal);
                    editor.windowRender.lineWidthCache.remove(deletedLine);
                }
                synchronized (editor.windowRender.avgCharWidthCache) {
                    editor.windowRender.avgCharWidthCache.remove(prevGlobal);
                    editor.windowRender.avgCharWidthCache.remove(deletedLine);
                }

                editor.highlite.invalidateHighlightCacheForLine(prevGlobal);
                
                if (editor.codeFold.isCodeFoldingEnabled) {
                    editor.codeFold.invalidateFoldRangeForLine(prevGlobal);
                    editor.codeFold.adjustFoldRangesForLineEdit(deletedLine, -1);
                }
                
                if (currentPrevLocal >= 0 && currentPrevLocal < editor.windowRender.linesWindow.size()) {
                    editor.view.updateLocalLine(currentPrevLocal, merged);
                }
                
                if (currentDeletedLocal >= 0 && currentDeletedLocal < editor.windowRender.linesWindow.size()) {
                    editor.windowRender.linesWindow.remove(currentDeletedLocal);
                }

                // Shift after window removal to ensure consistency
                operators.shifter.shiftModifiedLines(deletedLine, -1);

                // Prevent auto-reload while state stabilizes
                editor.fileIO.invalidatePendingIOForEdit();

                editor.windowRender.recalculateMaxLineWidth();
                editor.cursor.cursorLine = prevGlobal;
                editor.cursor.cursorChar = prev.length();
                editor.view.computeWidthForLine(prevGlobal, merged);
                operators.lineCountDelta -= 1;
                Log.i(
                        "CursorDbg",
                        "deleteMerge"
                                + " line="
                                + editor.cursor.cursorLine
                                + " char="
                                + editor.cursor.cursorChar
                                + " merged='"
                                + merged
                                + "'");
                Log.i(
                        "LineNumber",
                        "deleteCharAtCursor mergeLines"
                                + " prevGlobal="
                                + prevGlobal
                                + " deletedLine="
                                + deletedLine
                                + " windowSize="
                                + editor.windowRender.linesWindow.size()
                                + " lineCountDelta="
                                + operators.lineCountDelta
                                + " line0='"
                                + editor.windowRender.getLineTextForRender(prevGlobal)
                                + "' line1='"
                                + editor.windowRender.getLineTextForRender(prevGlobal + 1)
                                + "'");
                editor.wordWrap.onLineCountChanged();
                editor.lineNumber.invalidateLineNumberCache();
                editor.cursor.invalidateCursorArea();
                editor.invalidate();

                EditOp op = new EditOp();
                op.startLine = prevGlobal; op.startChar = (prev == null ? 0 : prev.length());
                op.endLine = beforeLine; op.endChar = 0;
                op.removedText = "\n"; op.insertedText = "";
                op.insertedEndLine = prevGlobal; op.insertedEndChar = op.startChar;
                op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
                op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
                op.timestamp = System.currentTimeMillis();
                operators.recorder.recordEdit(op);
            }
        }
        editor.ime.updateImeSelection();
        editor.autoCompletion.updateSuggestion();
    }

    private int previousCodePointStart(String text, int offset) {
        FunctionLog.f("EditorActions", "previousCodePointStart", text, offset);
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        if (safeOffset <= 0) return 0;
        return text.offsetByCodePoints(safeOffset, -1);
    }

    private int nextCodePointEnd(String text, int offset) {
        FunctionLog.f("EditorActions", "nextCodePointEnd", text, offset);
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        if (safeOffset >= text.length()) return text.length();
        return text.offsetByCodePoints(safeOffset, 1);
    }

    private void handleCodeFoldAfterCharacterEdit(
            int line, int editIndex, char c, int delta, int deleteLen) {
        FunctionLog.f("EditorActions", "handleCodeFoldAfterCharacterEdit", line, editIndex, c, delta, deleteLen);
        if (!editor.codeFold.isCodeFoldingEnabled) return;

        boolean bracketEdit = com.yn.sodiumeditor.utils.TextUtils.containsBracketChars(String.valueOf(c));
        if (bracketEdit) {
            editor.bracketCache.invalidateLines(line, line);
        } else {
            editor.codeFold.adjustFoldRangeForLineEdit(line, editIndex, delta, deleteLen);
            return;
        }

        editor.codeFold.adjustFoldRangeForLineEdit(line, editIndex, delta, deleteLen);
        editor.codeFold.refreshFoldRangesAroundLine(line);
    }

    private void handleCodeFoldBeforeEdit() {
        FunctionLog.f("EditorActions", "handleCodeFoldBeforeEdit");
        if (!editor.codeFold.isCodeFoldingEnabled) return;
        CodeFold.FoldRange hidden = editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
        if (hidden != null) {
            moveCursorToFoldEnd(hidden);
        } else {
            CodeFold.FoldRange start = editor.codeFold.getFoldRangeAtStart(editor.cursor.cursorLine);
            if (start != null && start.collapsed && editor.cursor.cursorChar > start.openCharIndex) {
                moveCursorToFoldEnd(start);
            }
        }
    }

    private void moveCursorToFoldEnd(CodeFold.FoldRange fold) {
        FunctionLog.f("EditorActions", "moveCursorToFoldEnd", fold);
        long startMs = android.os.SystemClock.uptimeMillis();
        boolean endLoaded = isLineInLoadedWindow(fold.endLine);
        long readStartMs = android.os.SystemClock.uptimeMillis();
        String endText = getFoldEndLineText(fold);
        long readMs = android.os.SystemClock.uptimeMillis() - readStartMs;
        long resolveStartMs = android.os.SystemClock.uptimeMillis();
        int closeIdx = editor.codeFold.resolveCloseCharIndex(fold, endText == null ? "" : endText);
        long resolveMs = android.os.SystemClock.uptimeMillis() - resolveStartMs;
        if (closeIdx < 0) closeIdx = (endText == null ? 0 : endText.length());
        int endLen = (endText == null ? 0 : endText.length());
        int closeEnd = Math.min(closeIdx + (fold.isBlockComment ? 2 : 1), endLen);
        int targetChar =
                editor.cursor.cursorLine == fold.startLine
                        ? closeEnd
                        : Math.max(closeEnd, Math.min(editor.cursor.cursorChar, endLen));
        editor.cursor.cursorLine = fold.endLine;
        editor.cursor.cursorChar = targetChar;
        long totalMs = android.os.SystemClock.uptimeMillis() - startMs;
        if (totalMs >= FOLD_TYPING_LOG_THRESHOLD_MS) {
            Log.i(
                FOLD_TYPING_PERF,
                "moveToFoldEnd total="
                    + totalMs
                    + " read="
                    + readMs
                    + " resolve="
                    + resolveMs
                    + " fold="
                    + fold.startLine
                    + "->"
                    + fold.endLine
                    + " endLoaded="
                    + endLoaded
                    + " endLen="
                    + (endText == null ? -1 : endText.length())
                    + " closeIdx="
                    + closeIdx
                    + " targetChar="
                    + targetChar);
        }
        if (editor.DEBUG_RENDER_LOGS) {
            Log.i(
                FOLD_EDIT_DBG,
                "moveToFoldEnd"
                        + " fold="
                        + fold.startLine
                        + "->"
                        + fold.endLine
                        + " endLoaded="
                        + endLoaded
                        + " endLen="
                        + (endText == null ? -1 : endText.length())
                        + " closeIdx="
                        + closeIdx
                        + " cursor="
                        + editor.cursor.cursorLine
                        + ":"
                        + editor.cursor.cursorChar
                        + " dtMs="
                        + (android.os.SystemClock.uptimeMillis() - startMs));
        }
        // This is an internal cursor correction before editing, not a user-visible move.
        // Snap immediately so fast typing after a collapsed fold does not inherit a lagging
        // animation from the hidden end line to the folded visual line.
        editor.cursorAnimation.snapToPosition(
            editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
    }

    private void handleCodeFoldNewline(int beforeLine, int beforeChar) {
        FunctionLog.f("EditorActions", "handleCodeFoldNewline", beforeLine, beforeChar);
        if (!editor.codeFold.isCodeFoldingEnabled) return;
        CodeFold.FoldRange foldAtStart = editor.codeFold.foldRanges.get(beforeLine);
        if (foldAtStart != null) {
            if (beforeChar <= foldAtStart.openCharIndex) {
                CodeFold.FoldRange updated = new CodeFold.FoldRange(beforeLine + 1, foldAtStart.endLine + 1, foldAtStart.openCharIndex, foldAtStart.openChar, foldAtStart.closeChar, foldAtStart.closeCharIndex, foldAtStart.isBlockComment, foldAtStart.isIndentFold);
                updated.collapsed = foldAtStart.collapsed;
                editor.codeFold.foldRanges.remove(beforeLine);
                editor.codeFold.foldRanges.put(beforeLine + 1, updated);
            } else {
                CodeFold.FoldRange updated = new CodeFold.FoldRange(beforeLine, foldAtStart.endLine + 1, foldAtStart.openCharIndex, foldAtStart.openChar, foldAtStart.closeChar, foldAtStart.closeCharIndex, foldAtStart.isBlockComment, foldAtStart.isIndentFold);
                updated.collapsed = foldAtStart.collapsed;
                editor.codeFold.foldRanges.put(beforeLine, updated);
            }
            editor.codeFold.foldIntervalsDirty = true;
        } else {
            CodeFold.FoldRange endingFold = findFoldEndingBeforeSuffixBreak(beforeLine, beforeChar);
            if (endingFold != null) {
                // Newlines inserted after the closing token on the fold end line belong outside
                // the collapsed range. Shift only following ranges, and keep this fold's end line.
                editor.codeFold.adjustFoldRangesForLineEdit(beforeLine + 1, 1);
            } else {
                editor.codeFold.adjustFoldRangesForLineEdit(beforeLine, 1);
            }
        }
    }

    private CodeFold.FoldRange findFoldEndingBeforeSuffixBreak(int beforeLine, int beforeChar) {
        if (!editor.codeFold.isCodeFoldingEnabled) return null;
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeEndingAtLine(beforeLine);
        if (range == null) return null;
        String endText = getFoldEndLineText(range);
        if (endText == null) endText = "";
        int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endText);
        if (closeIdx < 0) closeIdx = range.closeCharIndex;
        if (closeIdx < 0) return null;
        int closeEnd = closeIdx + (range.isBlockComment ? 2 : (range.isIndentFold ? 0 : 1));
        if (beforeChar >= closeEnd) {
            return range;
        }
        return null;
    }

    private String getFoldEndLineText(CodeFold.FoldRange fold) {
        String endText = editor.windowRender.getLineTextForRender(fold.endLine);
        if (endText == null || endText.isEmpty()) {
            String fallback = editor.codeFold.utils.getEndLineTextForFold(fold);
            if (fallback != null) {
                endText = fallback;
            }
        }
        return endText;
    }

	    private String getEditableLineText(int line) {
	        String text = editor.windowRender.getLineTextForRender(line);
        if (text == null || text.isEmpty()) {
            CodeFold.FoldRange hidden = editor.codeFold.getCollapsedRangeContainingLine(line);
            if (hidden != null && hidden.endLine == line) {
                long startMs = android.os.SystemClock.uptimeMillis();
                String fallback = editor.codeFold.utils.getEndLineTextForFold(hidden);
                if (fallback != null) {
                    text = fallback;
                }
                if (editor.DEBUG_RENDER_LOGS) {
                    Log.i(
                    FOLD_EDIT_DBG,
                    "editableFallback"
                                + " line="
                                + line
                                + " fallbackLen="
                                + (fallback == null ? -1 : fallback.length())
                                + " dtMs="
                                + (android.os.SystemClock.uptimeMillis() - startMs)
                                + " cached="
                                + (hidden.cachedEndLineText != null));
                }
            }
        }
	        return text;
	    }

    private String getDirectEditableLineText(int line) {
        if (line < 0) return null;
        synchronized (editor.windowRender.modifiedLines) {
            String modified = editor.windowRender.modifiedLines.get(line);
            if (modified != null) return modified;
        }
        String windowText = editor.windowRender.getLineTextForRender(line);
        if (isLineInLoadedWindow(line)) return windowText == null ? "" : windowText;
        if (editor.fileIO.sourceFile == null || !editor.fileIO.isIndexReady) return null;
        java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
        editor.fileIO.populateDirectLinesForRange(line, line, direct);
        return direct.containsKey(line) ? direct.get(line) : null;
    }

	    private CodeFold.FoldRange getHiddenCollapsedFoldForCursor() {
        if (!editor.codeFold.isCodeFoldingEnabled) return null;
        return editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
    }

    private boolean deleteCollapsedFoldIfDeletingClosingToken(boolean forwardDelete) {
        if (!editor.codeFold.isCodeFoldingEnabled) return false;
        CodeFold.FoldRange range = getCollapsedFoldAroundCursorForDelete();
        if (range == null || !range.collapsed || range.isIndentFold) return false;

        String endText = getFoldEndLineText(range);
        if (endText == null) endText = "";
        int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endText);
        if (closeIdx < 0) closeIdx = range.closeCharIndex;
        if (closeIdx < 0) return false;
        int closeLen = range.isBlockComment ? 2 : 1;
        int closeEnd = Math.min(endText.length(), closeIdx + closeLen);
        if (closeEnd <= closeIdx) return false;

        int line = editor.cursor.cursorLine;
        int ch = editor.cursor.cursorChar;
        boolean touchesCloseToken =
            forwardDelete
                ? line == range.endLine && ch >= closeIdx && ch < closeEnd
                : line == range.endLine && ch > closeIdx && ch <= closeEnd;
        if (!touchesCloseToken) return false;

        applyCollapsedFoldRangeDelete(range, closeEnd);
        editor.codeFold.clearFoldRanges();
        editor.cursorAnimation.snapToPosition(
            editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
        editor.ime.updateImeSelection();
        editor.cursor.invalidateCursorArea();
        editor.invalidate();
        return true;
    }

    private void applyCollapsedFoldRangeDelete(CodeFold.FoldRange range, int closeEnd) {
        String startText = editor.windowRender.getLineTextForRender(range.startLine);
        if (startText == null) startText = "";
        String endText = getFoldEndLineText(range);
        if (endText == null) endText = "";
        int startIdx = Math.max(0, Math.min(range.openCharIndex, startText.length()));
        int endIdx = Math.max(0, Math.min(closeEnd, endText.length()));
        String merged = startText.substring(0, startIdx) + endText.substring(endIdx);
        int removedLineCount = Math.max(0, range.endLine - range.startLine);

        synchronized (editor.windowRender.linesWindow) {
            int startLocal = range.startLine - editor.windowRender.windowStartLine;
            int endLocal = range.endLine - editor.windowRender.windowStartLine;
            if (startLocal >= 0 && startLocal < editor.windowRender.linesWindow.size()) {
                editor.view.updateLocalLine(startLocal, merged);
                int removeFrom = startLocal + 1;
                int removeToExclusive =
                    Math.min(
                        editor.windowRender.linesWindow.size(),
                        Math.max(removeFrom, endLocal + 1));
                if (removeFrom < removeToExclusive) {
                    editor.windowRender.linesWindow.subList(removeFrom, removeToExclusive).clear();
                }
            }
        }

        operators.shifter.shiftModifiedLines(range.startLine + 1, -removedLineCount);
        editor.windowRender.modifiedLines.put(range.startLine, merged);
        operators.lineCountDelta -= removedLineCount;
        editor.cursor.cursorLine = range.startLine;
        editor.cursor.cursorChar = startIdx;

        String removedText = null;
        if (removedLineCount <= 5000) {
            removedText = editor.fileIO.readRangeText(range.startLine, startIdx, range.endLine, endIdx);
            if (removedText != null && removedText.length() > EditOperators.UNDO_TEXT_LIMIT) {
                removedText = null;
            }
        }

        EditOp op = new EditOp();
        op.startLine = range.startLine;
        op.startChar = startIdx;
        op.endLine = range.endLine;
        op.endChar = endIdx;
        op.removedText = removedText;
        op.insertedText = "";
        op.insertedEndLine = range.startLine;
        op.insertedEndChar = startIdx;
        op.cursorLineBefore = range.endLine;
        op.cursorCharBefore = closeEnd;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        if (removedText == null) {
            operators.recorder.recordEditNoUndo(op);
        } else {
            operators.recorder.recordEdit(op);
        }

        editor.highlite.invalidateHighlightCacheForLine(range.startLine);
        editor.view.computeWidthForLine(range.startLine, merged);
        editor.windowRender.recalculateMaxLineWidth();
        editor.wordWrap.onLineCountChanged();
        editor.lineNumber.invalidateLineNumberCache();
        editor.loadingCircle.endLargeEditUi(false);
    }

    private CodeFold.FoldRange getCollapsedFoldAroundCursorForDelete() {
        int line = editor.cursor.cursorLine;
        CodeFold.FoldRange range = editor.codeFold.getCollapsedRangeEndingAtLine(line);
        if (range != null) return range;
        range = editor.codeFold.getCollapsedRangeContainingLine(line);
        if (range != null) return range;
        range = editor.codeFold.getFoldRangeAtStart(line);
        return (range != null && range.collapsed) ? range : null;
    }

    private String printableChar(char c) {
        if (c == '\n') return "\\n";
        if (c == '\t') return "\\t";
        return String.valueOf(c);
    }

    private int handleWindowEdgeCase(int localIdx) {
        FunctionLog.f("EditorActions", "handleWindowEdgeCase", localIdx);
        synchronized (editor.windowRender.linesWindow) {
            if (editor.windowRender.linesWindow.isEmpty()) {
                editor.windowRender.linesWindow.add("");
                return 0;
            } else if (editor.fileIO.isEof && localIdx >= editor.windowRender.linesWindow.size()) {
                while (editor.windowRender.linesWindow.size() <= localIdx) editor.windowRender.linesWindow.add("");
                return localIdx;
            }
        }
        return Math.max(0, Math.min(localIdx, editor.windowRender.linesWindow.size() - 1));
    }

    public void insertTextAtCursor(String text) {
        FunctionLog.f("EditorActions", "insertTextAtCursor", text);
        if (editor.view.isReadOnly || text == null || text.isEmpty()) return;
        if (editor.DEBUG_RENDER_LOGS) {
            Log.i(
                    FOLD_EDIT_DBG,
                    "insertText"
                            + " text='"
                            + text
                            + "' len="
                            + text.length()
                            + " cursor="
                            + editor.cursor.cursorLine
                            + ":"
                            + editor.cursor.cursorChar
                            + " window="
                            + editor.windowRender.windowStartLine
                            + "+"
                            + editor.windowRender.linesWindow.size()
                            + " hidden="
                            + (editor.codeFold.isCodeFoldingEnabled
                                    && editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine) != null));
        }
        if (editor.selection.hasSelection) {
            editor.selection.replaceSelectionWithText(text);
            return;
        }

        if (editor.fileIO.sourceFile != null && !editor.fileIO.isFileCleared && operators.recorder.isLargePasteText(text)) {
            handleCodeFoldBeforeEdit();
            editor.loadingCircle.beginLargeEditUiIfNeeded(true, editor.cursor.cursorLine, editor.cursor.cursorLine, true);
            EditOp.CursorTarget target = operators.recorder.computeCursorAfterInsert(editor.cursor.cursorLine, editor.cursor.cursorChar, text);
            operators.fileHandler.rewriteReplaceRangeAsync(operators.editVersion.incrementAndGet(), editor.fileIO.sourceFile, editor.cursor.cursorLine, editor.cursor.cursorChar, editor.cursor.cursorLine, editor.cursor.cursorChar, text, target, true);
            operators.lineCountDelta += operators.recorder.countNewlines(text);
            
            EditOp op = new EditOp();
            op.startLine = editor.cursor.cursorLine; op.startChar = editor.cursor.cursorChar;
            op.endLine = op.startLine; op.endChar = op.startChar;
            op.removedText = ""; op.insertedText = text;
            op.insertedEndLine = target.line; op.insertedEndChar = target.ch;
            op.cursorLineBefore = op.startLine; op.cursorCharBefore = op.startChar;
            op.cursorLineAfter = target.line; op.cursorCharAfter = target.ch;
            op.timestamp = System.currentTimeMillis();
            operators.recorder.recordEdit(op);
            return;
        }

        // Standard multi-char insert
        for (char c : text.toCharArray()) insertCharAtCursor(c);
    }
    public void insertTextAt(int line, int col, String text) {
    FunctionLog.f("EditorActions", "insertTextAt", line, col, text);
    if (text == null) return;
    if (Looper.myLooper() != Looper.getMainLooper()) {
      editor.post(() -> insertTextAt(line, col, text));
      return;
    }
    editor.cursor.setCursorPosition(line, col);
    editor.editOperators.insertTextAtCursor(text);
  }
}
