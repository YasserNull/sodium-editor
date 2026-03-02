package com.yn.sodiumeditor.input;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.CursorState;
import com.yn.sodiumeditor.state.InlinePredictionState;
import com.yn.sodiumeditor.state.PopupMenuState;
import com.yn.sodiumeditor.state.SelectionState;
import com.yn.sodiumeditor.renderer.animation.CursorAnimator;

/**
 * Handler class for focus change events.
 * Manages cursor animation, keyboard, and selection state on focus changes.
 */
public final class FocusChangeHandler {

    private final SodiumEditor view;
    private final CursorState cursorState;
    private final SelectionState selectionState;
    private final InlinePredictionState inlinePredictionState;
    private final PopupMenuState popupMenuState;
    private final CursorAnimator cursorAnimator;
    private boolean hideKeyboardOnFocusLoss = true;

    public FocusChangeHandler(
            SodiumEditor view,
            CursorState cursorState,
            SelectionState selectionState,
            InlinePredictionState inlinePredictionState,
            PopupMenuState popupMenuState,
            CursorAnimator cursorAnimator) {
        this.view = view;
        this.cursorState = cursorState;
        this.selectionState = selectionState;
        this.inlinePredictionState = inlinePredictionState;
        this.popupMenuState = popupMenuState;
        this.cursorAnimator = cursorAnimator;
    }

    /**
     * Handles focus change events.
     * 
     * @param focused true if the view gained focus, false otherwise
     */
    public void onFocusChanged(boolean focused) {
        inlinePredictionState.clearActiveSuggestion();

        InputMethodManager imm = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        if (focused) {
            if (imm != null) imm.restartInput(view);
            cursorAnimator.onFocusChanged(true);
        } else {
            if (hideKeyboardOnFocusLoss && imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            cursorAnimator.onFocusChanged(false);
            cursorState.setHasComposing(false);
            selectionState.clearSelectionKeepLineNumberState();
            hidePopup();
        }
    }

    private void hidePopup() {
        // Delegate to popup handler
        view.popupTouchHandler.hidePopup();
    }

    public void setHideKeyboardOnFocusLoss(boolean enabled) {
        this.hideKeyboardOnFocusLoss = enabled;
    }

    public boolean isHideKeyboardOnFocusLoss() {
        return hideKeyboardOnFocusLoss;
    }
}
