package com.yn.sodiumeditor.input.events;

import android.graphics.RectF;
import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.scroll.Popup;

/**
 * Handles interactions with the popup menu for SodiumEditor.
 */
public class PopupInteractionHandler {
    private final SodiumEditor editor;

    public PopupInteractionHandler(SodiumEditor editor) {
        this.editor = editor;
    }

    public boolean handleActionDown(MotionEvent event) {
        if (editor.popup.showPopup) {
            float ex = event.getX();
            float ey = event.getY();
            int hitAction = editor.popup.getPopupActionAt(ex, ey);
            if (hitAction != 0) {
                editor.popup.popupPressedAction = hitAction;
                editor.popup.startPopupRipple(hitAction, ex, ey);
                return true;
            }
        }
        return false;
    }

    public boolean handleActionMove(MotionEvent event) {
        if (editor.popup.popupPressedAction != 0) {
            float ex = event.getX();
            float ey = event.getY();
            RectF r = editor.popup.getPopupRectForAction(editor.popup.popupPressedAction);
            if (!r.contains(ex, ey)) {
                editor.popup.popupPressedAction = 0;
                editor.popup.cancelPopupRipple();
            }
            return true;
        }
        return false;
    }

    public boolean handleActionUp(MotionEvent event) {
        if (editor.popup.popupPressedAction != 0) {
            float ex = event.getX();
            float ey = event.getY();
            int actionForTap = editor.popup.popupPressedAction;
            boolean wasRippleHoldActive = editor.popup.popupRippleHoldActive;
            editor.popup.popupPressedAction = 0;
            RectF r = editor.popup.getPopupRectForAction(actionForTap);
            
            if (editor.popup.showPopup && r.contains(ex, ey)) {
                if (editor.view.isReadOnly && (actionForTap == Popup.POPUP_ACTION_CUT
                        || actionForTap == Popup.POPUP_ACTION_PASTE
                        || actionForTap == Popup.POPUP_ACTION_DELETE)) {
                    editor.popup.hidePopup();
                    return true;
                }
                
                // 1. Perform action immediately
                if (actionForTap == Popup.POPUP_ACTION_COPY) {
                    editor.selection.copyOrCutSelection(false);
                    editor.selection.clearSelection();
                    editor.invalidate();
                } else if (actionForTap == Popup.POPUP_ACTION_CUT) {
                    editor.selection.copyOrCutSelection(true);
                } else if (actionForTap == Popup.POPUP_ACTION_PASTE) {
                    editor.selection.pasteFromClipboard();
                } else if (actionForTap == Popup.POPUP_ACTION_DELETE) {
                    editor.selection.deleteSelection();
                } else if (actionForTap == Popup.POPUP_ACTION_SELECT_ALL) {
                    if (!editor.selection.isSelectAllActive) {
                        editor.selection.selectAll();
                        // Special case: select all often needs popup to stay visible but refresh
                        editor.popup.showPopupAtSelection(); 
                        return true; 
                    }
                }

                if (wasRippleHoldActive) {
                    // For long-press: keep ripple visible until finger lifts, then fade out immediately.
                    editor.popup.cancelPopupRipple();
                    editor.popup.hidePopup();
                } else {
                    // For taps: hide after ripple ends (prevents instant dismiss from selection state changes).
                    editor.popup.popupHideAfterRipple = true;
                }

            } else {
                editor.popup.cancelPopupRipple();
            }
            return true;
        }
        return false;
    }

    public void handleActionCancel() {
        editor.popup.popupPressedAction = 0;
        editor.popup.cancelPopupRipple();
    }
}
