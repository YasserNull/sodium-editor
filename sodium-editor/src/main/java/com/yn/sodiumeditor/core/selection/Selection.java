package com.yn.sodiumeditor.core.selection;

import java.io.File;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.io.SelectionTextBuilder;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.cursor.Cursor;
import com.yn.sodiumeditor.core.selection.SelectionState;
import com.yn.sodiumeditor.core.selection.SelectionTextRange;
import com.yn.sodiumeditor.utils.SelectionWordFinder;
import com.yn.sodiumeditor.utils.SelectionQuoteFinder;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Main facade for selection management in SodiumEditor.
 */
public class Selection {
    private final SodiumEditor editor;
    private final Cursor cursor;

    // Components
    public final SelectionState state;
    public final SelectionWordFinder wordFinder;
    public final SelectionQuoteFinder quoteFinder;
    public final SelectionTextBuilder textBuilder;
    public final SmartSelection smart;
    public final SelectionClipboard clipboard;
    public final SelectionActionHandler actions;

    // --- State (Kept as fields for project compatibility) ---
    public boolean hasSelection;
    public int selStartLine, selStartChar;
    public int selEndLine, selEndChar;
    public boolean selecting;
    public boolean isSelectAllActive;
    public boolean isEntireFileSelected;
    public int selectionColor;
    public int selectionHighlightColor;
    public int selectionHandleColor;
    public android.graphics.Paint selectionPaint;
    public android.graphics.RectF selectionHighlightRect;
    public android.graphics.RectF selectionRectTmp;
    public android.graphics.Path selectionPathTmp;
    public float[] selectionRadiiTmp;
    public boolean isLineNumberSelecting;
    public int lineNumberSelectAnchorLine;
    public boolean longPressSelecting;
    public boolean longPressFreeForm;
    public int longPressAnchorLine;
    public int longPressAnchorChar;
    public int longPressEndPointerId;
    public int lastDoubleTapLine;
    public int lastDoubleTapWordStart;
    public int lastDoubleTapWordEnd;
    public int lastDoubleTapStage;
    public long copyCutMaxLines;
    public int copyCutMaxChars;
    public int hideCopyCutMaxLines;
    public int replaceAllMaxCount;
    public boolean hideKeyboardOnFocusLoss;

    public Selection(SodiumEditor editor, Cursor cursor) {
        this.editor = editor;
        this.cursor = cursor;
        this.state = new SelectionState(editor);
        this.wordFinder = new SelectionWordFinder(editor);
        this.quoteFinder = new SelectionQuoteFinder(editor);
        this.textBuilder = new SelectionTextBuilder(editor);
        this.smart = new SmartSelection(editor, this);
        this.clipboard = new SelectionClipboard(editor, this);
        this.actions = new SelectionActionHandler(editor, this);
        syncFromState();
    }

    public void syncFromState() {
        hasSelection = state.hasSelection; selStartLine = state.selStartLine; selStartChar = state.selStartChar;
        selEndLine = state.selEndLine; selEndChar = state.selEndChar; selecting = state.selecting;
        isSelectAllActive = state.isSelectAllActive; isEntireFileSelected = state.isEntireFileSelected;
        selectionColor = state.selectionColor; selectionHighlightColor = state.selectionHighlightColor;
        selectionHandleColor = state.selectionHandleColor; selectionPaint = state.selectionPaint;
        selectionHighlightRect = state.selectionHighlightRect; selectionRectTmp = state.selectionRectTmp;
        selectionPathTmp = state.selectionPathTmp; selectionRadiiTmp = state.selectionRadiiTmp;
        isLineNumberSelecting = state.isLineNumberSelecting; lineNumberSelectAnchorLine = state.lineNumberSelectAnchorLine;
        longPressSelecting = state.longPressSelecting; longPressFreeForm = state.longPressFreeForm;
        longPressAnchorLine = state.longPressAnchorLine; longPressAnchorChar = state.longPressAnchorChar;
        longPressEndPointerId = state.longPressEndPointerId; lastDoubleTapLine = state.lastDoubleTapLine;
        lastDoubleTapWordStart = state.lastDoubleTapWordStart; lastDoubleTapWordEnd = state.lastDoubleTapWordEnd;
        lastDoubleTapStage = state.lastDoubleTapStage; copyCutMaxLines = state.copyCutMaxLines;
        copyCutMaxChars = state.copyCutMaxChars; hideCopyCutMaxLines = state.hideCopyCutMaxLines;
        replaceAllMaxCount = state.replaceAllMaxCount; hideKeyboardOnFocusLoss = state.hideKeyboardOnFocusLoss;
    }

    public void syncToState() {
        state.hasSelection = hasSelection; state.selStartLine = selStartLine; state.selStartChar = selStartChar;
        state.selEndLine = selEndLine; state.selEndChar = selEndChar; state.selecting = selecting;
        state.isSelectAllActive = isSelectAllActive; state.isEntireFileSelected = isEntireFileSelected;
    }

    // ==============================
    // Bridge Methods (Delegated)
    // ==============================

    public void setSelection(int sL, int sC, int eL, int eC) {
        state.setSelection(sL, sC, eL, eC); syncFromState();
    }

    public void clearSelection() {
        state.clearSelection(); syncFromState();
    }

    public void selectAll() {
        actions.selectAll(); syncFromState();
    }

    public void selectWordAtCursor() {
        smart.selectWordAtCursor();
    }

    public void selectLineAtCursor() {
        smart.selectLineAtCursor();
    }

    public String getSelectedText() {
        if (!hasSelection) return null;
        int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
        if (state.comparePos(sL, sC, eL, eC) > 0) { int tL = sL, tC = sC; sL = eL; sC = eC; eL = tL; eC = tC; }
        return textBuilder.buildSelectedTextFromWindow(sL, sC, eL, eC, copyCutMaxChars);
    }

    public void copyOrCutSelection(boolean cut) {
        clipboard.copyOrCutSelection(cut);
    }

    public void deleteSelection() {
        clipboard.deleteSelection();
    }

    public void pasteFromClipboard() {
        clipboard.pasteFromClipboard(); syncFromState();
    }

    public void replaceSelectionWithText(String t) {
        actions.replaceSelectionWithText(t); syncFromState();
    }

    public void deleteEntireFileSelectionFast() {
        actions.deleteEntireFileSelectionFast(); syncFromState();
    }

    public boolean applySmartDoubleTapSelection(int l, int c, String t) {
        boolean r = smart.applySmartDoubleTapSelection(l, c, t); syncFromState(); return r;
    }

    public ArrayList<SelectionTextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
        return smart.buildDoubleTapCandidates(line, charIndex, wStart, wEnd);
    }

    public int comparePos(int lA, int cA, int lB, int cB) {
        return state.comparePos(lA, cA, lB, cB);
    }

    public boolean contains(int l, int c) {
        return state.contains(l, c);
    }

    public void setSelectionInternal(int sL, int sC, int eL, int eC) {
        state.setSelectionInternal(sL, sC, eL, eC); syncFromState();
    }

    public void clearSelectionStateAfterDelete() {
        state.clearSelectionStateAfterDelete(); syncFromState();
    }
    
    public void beginLongPressSelection(int l, int c) {
        state.beginLongPressSelection(l, c); syncFromState();
    }

    public void updateLongPressSelection(int l, int c) {
        state.updateLongPressSelection(l, c); syncFromState();
    }

    public void updateLongPressSelectionFromSelectionEnd(int l, int c) {
        state.updateLongPressSelectionFromSelectionEnd(l, c); syncFromState();
    }

    public void endLongPressSelection() {
        state.endLongPressSelection(); syncFromState();
    }

    public boolean shouldHideCopyCutForSelection() {
        return state.shouldHideCopyCutForSelection();
    }

    public int findSelectionCandidateIndex(int l, List<SelectionTextRange> c) {
        return wordFinder.findSelectionCandidateIndex(l, c, selStartLine, selStartChar, selEndLine, selEndChar);
    }

    public void setSelectionAnimationEnabled(boolean enabled) {
        state.setSelectionAnimationEnabled(enabled);
    }

    public boolean isPositionInsideSelection(int line, int ch) {
        return state.isPositionInsideSelection(line, ch);
    }

    public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int max) {
        return textBuilder.buildSelectedTextFromWindow(sL, sC, eL, eC, max);
    }

    public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int max) {
        return textBuilder.buildSelectedTextBlocking(sL, sC, eL, eC, max);
    }

    public void recordReplaceSelectionEdit(int sL, int sC, int eL, int eC, @Nullable String rem, @Nullable String ins, int bL, int bC) {
        String insert = (ins == null) ? "" : ins;
        EditOp op = new EditOp(); op.startLine = sL; op.startChar = sC; op.endLine = eL; op.endChar = eC; op.removedText = rem; op.insertedText = insert;
        EditOp.CursorTarget end = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
        op.insertedEndLine = end.line; op.insertedEndChar = end.ch; op.cursorLineBefore = bL; op.cursorCharBefore = bC;
        op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar; op.timestamp = System.currentTimeMillis();
        if (rem == null || rem.length() > EditOperators.UNDO_TEXT_LIMIT || insert.length() > EditOperators.UNDO_TEXT_LIMIT) editor.editOperators.recordEditNoUndo(op);
        else editor.editOperators.recordEdit(op);
    }
}
