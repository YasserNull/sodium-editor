package com.yn.sodiumeditor.core.selection;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles clipboard operations for selection (Copy, Cut, Paste).
 */
public class SelectionClipboard {
    private final SodiumEditor editor;
    private final Selection selection;

    public SelectionClipboard(SodiumEditor editor, Selection selection) {
        this.editor = editor;
        this.selection = selection;
    }

    public void copyOrCutSelection(final boolean cut) {
        if (!selection.hasSelection) return;
        editor.autoCompletion.clearActiveSuggestion();
        if (selection.shouldHideCopyCutForSelection()) return;

        int sL = selection.selStartLine, sC = selection.selStartChar, eL = selection.selEndLine, eC = selection.selEndChar;
        if (selection.state.comparePos(sL, sC, eL, eC) > 0) {
            int tL = sL, tC = sC; sL = eL; sC = eC; eL = tL; eC = tC;
        }

        long lines = (long) eL - (long) sL + 1L;
        if (lines > selection.copyCutMaxLines) return;

        final int fsL = sL, fsC = sC, feL = eL, feC = eC;
        boolean fullyInWindow = (fsL >= editor.windowRender.windowStartLine) && (feL < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
        
        if (fullyInWindow) {
            String text = selection.textBuilder.buildSelectedTextFromWindow(fsL, fsC, feL, feC, selection.copyCutMaxChars);
            setPrimaryClip(text);
            if (cut) selection.deleteSelection();
            return;
        }

        if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.cancelWrapWorkForPriority();
        editor.fileIO.ioHandler.post(() -> {
            final String text = selection.textBuilder.buildSelectedTextBlocking(fsL, fsC, feL, feC, selection.copyCutMaxChars);
            editor.post(() -> {
                setPrimaryClip(text);
                if (cut) selection.deleteSelection();
            });
        });
    }

    private void setPrimaryClip(String text) {
        ClipboardManager cm = (ClipboardManager) editor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
    }

    public void pasteFromClipboard() {
        selection.state.pasteFromClipboard();
    }

    public void deleteSelection() {
        editor.autoCompletion.clearActiveSuggestion();
        selection.replaceSelectionWithText("");
    }
}
