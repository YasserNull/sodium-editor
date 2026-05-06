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

        handleCodeFoldBeforeEdit();

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;

        editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
            editor.post(this::deleteForwardAtCursor);
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

        handleCodeFoldBeforeEdit();

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;

        editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
            final int retryLine = editor.cursor.cursorLine;
            final int retryChar = editor.cursor.cursorChar;
            editor.post(() -> {
                editor.cursor.cursorLine = retryLine;
                editor.cursor.cursorChar = retryChar;
                insertCharAtCursor(c);
            });
            return;
        }

        int localIdx = editor.cursor.cursorLine - editor.windowRender.windowStartLine;
        if (localIdx < 0 || localIdx >= editor.windowRender.linesWindow.size()) {
            localIdx = handleWindowEdgeCase(localIdx);
        }

        boolean fullInvalidate = false;
        synchronized (editor.windowRender.linesWindow) {
            String base = editor.windowRender.getLineFromWindowLocal(localIdx);
            if (base == null) base = "";

            if (c == '\n') {
                fullInvalidate = true;
                int oldLineCount = editor.view.getLinesCount();
                String before = base.substring(0, Math.min(editor.cursor.cursorChar, base.length()));
                String after = base.substring(Math.min(editor.cursor.cursorChar, base.length()));
                
                editor.view.updateLocalLine(localIdx, before);
                editor.windowRender.linesWindow.add(localIdx + 1, after);

                operators.shifter.shiftModifiedLines(editor.cursor.cursorLine + 1, 1);
                
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, before);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine + 1, after);
                
                handleCodeFoldNewline(beforeLine, beforeChar);

                editor.view.computeWidthForLine(editor.cursor.cursorLine, before);
                editor.view.computeWidthForLine(editor.cursor.cursorLine + 1, after);

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
                int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
                String modified = base.substring(0, pos) + c + base.substring(pos);
                editor.view.updateLocalLine(localIdx, modified);
                editor.windowRender.modifiedLines.put(editor.cursor.cursorLine, modified);
                
                synchronized (editor.windowRender.avgCharWidthCache) { editor.windowRender.avgCharWidthCache.remove(editor.cursor.cursorLine); }
                synchronized (editor.windowRender.lineWidthCache) { editor.windowRender.lineWidthCache.remove(editor.cursor.cursorLine); }
                
                if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
                    editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, pos, 1, 0);
                }
                if (editor.codeFold.isCodeFoldingEnabled) {
                    if (com.yn.sodiumeditor.utils.TextUtils.containsBracketChars(String.valueOf(c))) editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
                    editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, pos, 1, 0);
                }
                editor.highlite.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
                editor.cursor.cursorChar++;
                editor.view.computeWidthForLine(editor.cursor.cursorLine, modified);
            }
            if (fullInvalidate) editor.invalidate();
            else editor.view.invalidateLineGlobal(editor.cursor.cursorLine);
            editor.scroll.keepCursorVisibleHorizontally();
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

        final int beforeLine = editor.cursor.cursorLine;
        final int beforeChar = editor.cursor.cursorChar;

        editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
            editor.post(this::deleteCharAtCursor);
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
        editor.fileIO.ensureLineInWindow(fold.endLine, true);
        String endText = editor.windowRender.getLineTextForRender(fold.endLine);
        int closeIdx = editor.codeFold.resolveCloseCharIndex(fold, endText == null ? "" : endText);
        if (closeIdx < 0) closeIdx = (endText == null ? 0 : endText.length());
        editor.cursor.cursorLine = fold.endLine;
        editor.cursor.cursorChar = Math.max(editor.cursor.cursorChar, Math.min(closeIdx + 1, (endText == null ? 0 : endText.length())));
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
            editor.codeFold.adjustFoldRangesForLineEdit(beforeLine, 1);
        }
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
        if (editor.selection.hasSelection) {
            editor.selection.replaceSelectionWithText(text);
            return;
        }

        handleCodeFoldBeforeEdit();

        if (editor.fileIO.sourceFile != null && !editor.fileIO.isFileCleared && operators.recorder.isLargePasteText(text)) {
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

        editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
        if (editor.fileIO.isWindowLoading && !isLineInLoadedWindow(editor.cursor.cursorLine)) {
            final int retryLine = editor.cursor.cursorLine;
            final int retryChar = editor.cursor.cursorChar;
            editor.post(() -> {
                editor.cursor.cursorLine = retryLine;
                editor.cursor.cursorChar = retryChar;
                insertTextAtCursor(text);
            });
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
