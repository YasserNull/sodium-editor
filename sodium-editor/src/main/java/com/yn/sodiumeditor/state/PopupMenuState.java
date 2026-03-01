package com.yn.sodiumeditor.state;

import android.animation.ValueAnimator;
import android.graphics.RectF;
import androidx.annotation.Nullable;

/**
 * State class for popup menu functionality.
 * Stores popup menu state including rectangles, alpha, and ripple animation state.
 */
public class PopupMenuState {

    public boolean showPopup = false;
    public boolean isMinimalPopup = false;

    // Button rectangles
    public final RectF popupRect = new RectF();
    public final RectF btnCopyRect = new RectF();
    public final RectF btnCutRect = new RectF();
    public final RectF btnPasteRect = new RectF();
    public final RectF btnDeleteRect = new RectF();
    public final RectF btnSelectAllRect = new RectF();

    // Fade animation state
    public float popupAlpha = 0f;
    @Nullable public ValueAnimator popupFadeAnimator;

    // Interaction state
    public int popupPressedAction = 0;
    public boolean pendingPopupAfterDoubleTap = false;

    // Ripple animation state
    public boolean popupRippleActive = false;
    public final RectF popupRippleRect = new RectF();
    public float popupRippleX = 0f;
    public float popupRippleY = 0f;
    public float popupRippleRadius = 0f;
    public float popupRippleMaxRadius = 0f;
    public float popupRippleAlpha = 0f;
    public boolean popupRippleHoldActive = false;
    @Nullable public ValueAnimator popupRippleAnimator;

    public PopupMenuState() {
    }

    public void resetButtonRects() {
        btnCopyRect.setEmpty();
        btnCutRect.setEmpty();
        btnPasteRect.setEmpty();
        btnDeleteRect.setEmpty();
        btnSelectAllRect.setEmpty();
    }

    public void clearRippleState() {
        popupRippleActive = false;
        popupRippleX = 0f;
        popupRippleY = 0f;
        popupRippleRadius = 0f;
        popupRippleMaxRadius = 0f;
        popupRippleAlpha = 0f;
        popupRippleHoldActive = false;
        if (popupRippleAnimator != null) {
            popupRippleAnimator.cancel();
            popupRippleAnimator = null;
        }
    }

    public void stopPopup() {
        showPopup = false;
        popupPressedAction = 0;
        resetButtonRects();
        clearRippleState();
        if (popupFadeAnimator != null) {
            popupFadeAnimator.cancel();
            popupFadeAnimator = null;
        }
    }

    public void setPressedAction(int action) {
        popupPressedAction = action;
    }

    public int getPressedAction() {
        return popupPressedAction;
    }

    public void clearPressedAction() {
        popupPressedAction = 0;
    }

    public void setPendingPopupAfterDoubleTap(boolean pending) {
        pendingPopupAfterDoubleTap = pending;
    }
}
