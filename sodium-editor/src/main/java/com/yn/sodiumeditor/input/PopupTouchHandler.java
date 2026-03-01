package com.yn.sodiumeditor.input;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.config.PopupConfig;
import com.yn.sodiumeditor.state.PopupMenuState;

/**
 * Input handler for popup menu touch interactions.
 * Handles hit testing, ripple animations, and action execution.
 */
public class PopupTouchHandler {

    private final SodiumEditorView view;
    private final PopupMenuState state;

    public PopupTouchHandler(SodiumEditorView view, PopupMenuState state) {
        this.view = view;
        this.state = state;
    }

    public int getPopupActionAt(float x, float y) {
        if (state.btnCopyRect.contains(x, y)) return PopupConfig.POPUP_ACTION_COPY;
        if (state.btnCutRect.contains(x, y)) return PopupConfig.POPUP_ACTION_CUT;
        if (state.btnPasteRect.contains(x, y)) return PopupConfig.POPUP_ACTION_PASTE;
        if (state.btnDeleteRect.contains(x, y)) return PopupConfig.POPUP_ACTION_DELETE;
        if (state.btnSelectAllRect.contains(x, y)) return PopupConfig.POPUP_ACTION_SELECT_ALL;
        return 0;
    }

    public void startPopupRipple(int action, float x, float y) {
        RectF r = getPopupRectForAction(action);
        if (r.isEmpty()) return;
        state.popupRippleHoldActive = false;
        state.popupRippleRect.set(r);
        state.popupRippleX = Math.max(r.left, Math.min(x, r.right));
        state.popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
        state.popupRippleRadius = 0f;
        state.popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
        state.popupRippleAlpha = 0.22f;
        state.popupRippleActive = true;
        if (state.popupRippleAnimator != null) state.popupRippleAnimator.cancel();
        state.popupRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        state.popupRippleAnimator.setDuration(220);
        state.popupRippleAnimator.setInterpolator(new DecelerateInterpolator());
        state.popupRippleAnimator.addUpdateListener(
                a -> {
                    float t = (a.getAnimatedValue() instanceof Float) ? (Float) a.getAnimatedValue() : 1f;
                    state.popupRippleRadius = state.popupRippleMaxRadius * t;
                    state.popupRippleAlpha = 0.22f * (1f - t);
                    view.invalidate();
                });
        state.popupRippleAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        state.popupRippleActive = false;
                        state.popupRippleAlpha = 0f;
                        state.popupRippleRect.setEmpty();
                        view.invalidate();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        state.popupRippleActive = false;
                        state.popupRippleAlpha = 0f;
                        state.popupRippleRect.setEmpty();
                        view.invalidate();
                    }
                });
        state.popupRippleAnimator.start();
    }

    public void startPopupRippleHold(int action, float x, float y) {
        RectF r = getPopupRectForAction(action);
        if (r.isEmpty()) return;
        state.popupRippleHoldActive = true;
        state.popupRippleRect.set(r);
        state.popupRippleX = Math.max(r.left, Math.min(x, r.right));
        state.popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
        state.popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
        state.popupRippleRadius = state.popupRippleMaxRadius;
        state.popupRippleAlpha = 0.22f;
        state.popupRippleActive = true;
        if (state.popupRippleAnimator != null) state.popupRippleAnimator.cancel();
        view.invalidate();
    }

    public void cancelPopupRipple() {
        if (state.popupRippleAnimator != null) state.popupRippleAnimator.cancel();
        state.popupRippleHoldActive = false;
        state.popupRippleActive = false;
        state.popupRippleAlpha = 0f;
        state.popupRippleRect.setEmpty();
        view.invalidate();
    }

    public void performPopupAction(int action) {
        switch (action) {
            case PopupConfig.POPUP_ACTION_COPY:
                view.copySelectionToClipboard();
                break;
            case PopupConfig.POPUP_ACTION_CUT:
                view.cutSelectionToClipboard();
                break;
            case PopupConfig.POPUP_ACTION_PASTE:
                view.pasteFromClipboard();
                break;
            case PopupConfig.POPUP_ACTION_DELETE:
                view.deleteSelection();
                break;
            case PopupConfig.POPUP_ACTION_SELECT_ALL:
                view.selectAll();
                break;
            default:
                break;
        }
    }

    private RectF getPopupRectForAction(int action) {
        switch (action) {
            case PopupConfig.POPUP_ACTION_COPY:
                return state.btnCopyRect;
            case PopupConfig.POPUP_ACTION_CUT:
                return state.btnCutRect;
            case PopupConfig.POPUP_ACTION_PASTE:
                return state.btnPasteRect;
            case PopupConfig.POPUP_ACTION_DELETE:
                return state.btnDeleteRect;
            default:
                return state.btnSelectAllRect;
        }
    }

    public void showMinimalPopupAtCursor() {
        if (view.selectionState.hasSelection()) return;
        state.isMinimalPopup = true;
        if (view.popupMenuRenderer != null) {
            view.popupMenuRenderer.showPopupAnimated();
        }
    }

    public void showPopupAtSelection() {
        if (!view.selectionState.hasSelection()) return;
        state.isMinimalPopup = false;
        if (view.popupMenuRenderer != null) {
            view.popupMenuRenderer.showPopupAnimated();
        }
    }

    public void hidePopup() {
        if (view.popupMenuRenderer != null) {
            view.popupMenuRenderer.hidePopup();
        }
    }
}
