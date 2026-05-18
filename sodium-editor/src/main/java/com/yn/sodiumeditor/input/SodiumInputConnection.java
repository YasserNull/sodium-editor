package com.yn.sodiumeditor.input;

import android.text.Editable;
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Implementation of InputConnection for SodiumEditor.
 */
public class SodiumInputConnection extends BaseInputConnection {
    private static final String FOLD_TYPING_PERF = "FoldTypingPerf";
    private final SodiumEditor editor;
    private final Ime ime;

    public SodiumInputConnection(SodiumEditor editor, Ime ime) {
        super(editor, true);
        FunctionLog.f("SodiumInputConnection", "SodiumInputConnection", editor, ime);
        this.editor = editor;
        this.ime = ime;
    }

    @Override
    public Editable getEditable() {
        FunctionLog.f("SodiumInputConnection", "getEditable");
        return ime.imeEditable;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        FunctionLog.f("SodiumInputConnection", "getExtractedText", request, flags);
        if (editor.view.isDisabled || editor.view.isReadOnly) return null;
        long startMs = android.os.SystemClock.uptimeMillis();
        ExtractedText result = ime.onGetExtractedText(request, flags);
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.getExtractedText total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " flags="
                        + flags
                        + " textLen="
                        + (result == null || result.text == null ? -1 : result.text.length()));
        return result;
    }

    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        FunctionLog.f("SodiumInputConnection", "getTextBeforeCursor", length, flags);
        if (editor.view.isDisabled || editor.view.isReadOnly) return "";
        long startMs = android.os.SystemClock.uptimeMillis();
        CharSequence result = ime.scanner.getImeTextBeforeCursor(length);
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.getTextBefore total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " req="
                        + length
                        + " len="
                        + (result == null ? -1 : result.length()));
        return result;
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        FunctionLog.f("SodiumInputConnection", "getTextAfterCursor", length, flags);
        if (editor.view.isDisabled || editor.view.isReadOnly) return "";
        long startMs = android.os.SystemClock.uptimeMillis();
        CharSequence result = ime.scanner.getImeTextAfterCursor(length);
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.getTextAfter total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " req="
                        + length
                        + " len="
                        + (result == null ? -1 : result.length()));
        return result;
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        FunctionLog.f("SodiumInputConnection", "getSelectedText", flags);
        if (editor.view.isDisabled || editor.view.isReadOnly) return "";
        long startMs = android.os.SystemClock.uptimeMillis();
        CharSequence result = editor.selection.getSelectedText();
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.getSelected total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " len="
                        + (result == null ? -1 : result.length()));
        return result;
    }

    @Override
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        FunctionLog.f("SodiumInputConnection", "getSurroundingText", beforeLength, afterLength, flags);
        if (editor.view.isDisabled || editor.view.isReadOnly) return null;
        int before = Math.max(0, beforeLength);
        int after = Math.max(0, afterLength);
        long startMs = android.os.SystemClock.uptimeMillis();
        long ctxStartMs = startMs;
        ImeContext ctx = ime.scanner.buildImeContext(before, after);
        long ctxMs = android.os.SystemClock.uptimeMillis() - ctxStartMs;
        
        int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
        int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
        if (editor.selection.hasSelection) {
            sLine = editor.selection.selStartLine;
            sChar = editor.selection.selStartChar;
            eLine = editor.selection.selEndLine;
            eChar = editor.selection.selEndChar;
            if (ime.scanner.comparePos(sLine, sChar, eLine, eChar) > 0) {
                int tL = sLine, tC = sChar;
                sLine = eLine; sChar = eChar;
                eLine = tL; eChar = tC;
            }
        }
        int selStart = ime.scanner.lineCharToOffsetInContext(ctx, sLine, sChar);
        int selEnd = ime.scanner.lineCharToOffsetInContext(ctx, eLine, eChar);
        SurroundingText result = new SurroundingText(ctx.text, selStart, selEnd, 0);
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.getSurrounding total="
                        + (android.os.SystemClock.uptimeMillis() - startMs)
                        + " ctx="
                        + ctxMs
                        + " beforeReq="
                        + beforeLength
                        + " afterReq="
                        + afterLength
                        + " textLen="
                        + ctx.text.length());
        return result;
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        FunctionLog.f("SodiumInputConnection", "getCursorCapsMode", reqModes);
        CharSequence before = getTextBeforeCursor(2048, 0);
        int len = (before == null) ? 0 : before.length();
        return TextUtils.getCapsMode(before, len, reqModes);
    }

    @Override
    public boolean setSelection(int start, int end) {
        FunctionLog.f("SodiumInputConnection", "setSelection", start, end);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        return ime.onSetSelection(start, end);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        FunctionLog.f("SodiumInputConnection", "setComposingRegion", start, end);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        return ime.onSetComposingRegion(start, end);
    }

    @Override
    public boolean finishComposingText() {
        FunctionLog.f("SodiumInputConnection", "finishComposingText");
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        ime.onFinishComposingText();
        return true;
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        FunctionLog.f("SodiumInputConnection", "commitCompletion", text);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (text == null || text.getText() == null) return true;
        return ime.onCommitCompletion(text.getText());
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        FunctionLog.f("SodiumInputConnection", "commitCorrection", correctionInfo);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
        return ime.onCommitCorrection(correctionInfo.getNewText());
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        FunctionLog.f("SodiumInputConnection", "commitText", text, newCursorPosition);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        if (text == null) return super.commitText(text, newCursorPosition);
        int beforeLine = editor.cursor.cursorLine;
        int beforeChar = editor.cursor.cursorChar;
        boolean foldHidden =
                editor.codeFold.isCodeFoldingEnabled
                        && editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine) != null;
        long startMs = android.os.SystemClock.uptimeMillis();
        boolean result = ime.onCommitText(text, newCursorPosition);
        long totalMs = android.os.SystemClock.uptimeMillis() - startMs;
        android.util.Log.i(
                FOLD_TYPING_PERF,
                "ic.commitText total="
                        + totalMs
                        + " textLen="
                        + text.length()
                        + " before="
                        + beforeLine
                        + ":"
                        + beforeChar
                        + " after="
                        + editor.cursor.cursorLine
                        + ":"
                        + editor.cursor.cursorChar
                        + " window="
                        + editor.windowRender.windowStartLine
                        + "+"
                        + editor.windowRender.linesWindow.size()
                        + " foldHidden="
                        + foldHidden
                        + " modified="
                        + editor.windowRender.modifiedLines.size()
                        + " lineDelta="
                        + editor.editOperators.lineCountDelta);
        return result;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        FunctionLog.f("SodiumInputConnection", "setComposingText", text, newCursorPosition);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        if (text == null) return true;
        return ime.onSetComposingText(text, newCursorPosition);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        FunctionLog.f("SodiumInputConnection", "deleteSurroundingText", beforeLength, afterLength);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        return ime.onDeleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        FunctionLog.f("SodiumInputConnection", "deleteSurroundingTextInCodePoints", beforeLength, afterLength);
        if (editor.view.isDisabled || editor.view.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        return ime.onDeleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }
}
