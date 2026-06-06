package com.yn.sodiumeditor.core.view.events;

import android.content.Context;
import android.graphics.Rect;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;

public class onFocusChanged {
    private final SodiumEditor editor;

    public onFocusChanged(SodiumEditor editor) {
        this.editor = editor;
    }

    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion on focus change
        InputMethodManager imm =
                (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (focused) {
            if (imm != null) imm.restartInput(editor);
            editor.caret.resetBlink();
        } else {
            if (editor.selection.hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
            editor.caret.mainHandler.removeCallbacks(editor.caret.blinkRunnable);
            editor.caret.isCursorVisible = true; // Make sure it's visible when not focused
            editor.ime.hasComposing = false;
            if (!editor.selection.isSelectAllActive && !editor.selection.isEntireFileSelected) {
                editor.selection.hasSelection = false;
            }
        }
    }
}
