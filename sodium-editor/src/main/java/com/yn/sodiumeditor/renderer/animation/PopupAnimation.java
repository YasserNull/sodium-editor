package com.yn.sodiumeditor.renderer.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.scroll.Popup;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Handles fade and ripple animations for the popup menu.
 */
public class PopupAnimation {
    private static final String ANIM_DBG = "AnimDbg";
    private final SodiumEditor editor;
    private final Popup popup;

    public PopupAnimation(SodiumEditor editor, Popup popup) {
        FunctionLog.f("PopupAnimation", "PopupAnimation", editor, popup);
        this.editor = editor;
        this.popup = popup;
    }

    public void startFade(float targetAlpha) {
        FunctionLog.f("PopupAnimation", "startFade", targetAlpha);
        android.util.Log.i(
                ANIM_DBG,
                "popupFade request target="
                        + targetAlpha
                        + " current="
                        + popup.popupAlpha
                        + " show="
                        + popup.showPopup
                        + " minimal="
                        + popup.isMinimalPopup);
        if (popup.popupFadeAnimator != null) {
            if (popup.fadeTargetAlpha == targetAlpha && popup.popupFadeAnimator.isRunning()) return;
            popup.popupFadeAnimator.cancel();
        }
        popup.fadeTargetAlpha = targetAlpha;

        float startAlpha = popup.popupAlpha;
        if (Math.abs(startAlpha - targetAlpha) < 0.001f) {
            popup.popupAlpha = targetAlpha;
            if (targetAlpha <= 0.01f) {
                popup.showPopup = false;
                popup.isMinimalPopup = false;
                editor.invalidate();
            }
            android.util.Log.i(ANIM_DBG, "popupFade skip noop alpha=" + targetAlpha);
            return;
        }
        long duration = (targetAlpha > startAlpha) ? Popup.POPUP_FADE_IN_MS : Popup.POPUP_FADE_OUT_MS;
        
        ValueAnimator animator = ValueAnimator.ofFloat(startAlpha, targetAlpha);
        popup.popupFadeAnimator = animator;
        
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(
                a -> {
                    Object v = a.getAnimatedValue();
                    popup.popupAlpha = (v instanceof Float) ? (Float) v : targetAlpha;
                    android.util.Log.i(ANIM_DBG, "popupFade frame alpha=" + popup.popupAlpha + " target=" + targetAlpha);
                    editor.invalidate();
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation != popup.popupFadeAnimator) return;
                        
                        popup.isFadingOut = false;
                        if (popup.popupAlpha <= 0.01f) {
                            android.util.Log.d("Popup", "State: HIDDEN");
                            android.util.Log.i(ANIM_DBG, "popupFade end hidden");
                            popup.showPopup = false;
                            popup.isMinimalPopup = false;
                            popup.popupAlpha = 0f;
                        } else {
                            android.util.Log.d("Popup", "State: VISIBLE");
                            android.util.Log.i(ANIM_DBG, "popupFade end visible alpha=" + popup.popupAlpha);
                        }
                        editor.invalidate();
                    }
                });
        animator.start();
    }

    public void startRipple(int action, float x, float y) {
        FunctionLog.f("PopupAnimation", "startRipple", action, x, y);
        android.util.Log.i(ANIM_DBG, "popupRipple start action=" + action + " x=" + x + " y=" + y);
        RectF r = popup.getPopupRectForAction(action);
        if (r.isEmpty()) return;
        popup.popupRippleHoldActive = false;
        popup.popupHideAfterRipple = false;
        popup.popupRippleRect.set(r);
        popup.popupRippleX = Math.max(r.left, Math.min(x, r.right));
        popup.popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
        popup.popupRippleRadius = 0f;
        popup.popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
        popup.popupRippleAlpha = 0.22f;
        popup.popupRippleActive = true;
        if (popup.popupRippleAnimator != null) popup.popupRippleAnimator.cancel();
        popup.popupRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        popup.popupRippleAnimator.setDuration(Popup.POPUP_RIPPLE_MS);
        popup.popupRippleAnimator.setInterpolator(new DecelerateInterpolator());
        popup.popupRippleAnimator.addUpdateListener(
                a -> {
                    float t = (a.getAnimatedValue() instanceof Float) ? (Float) a.getAnimatedValue() : 1f;
                    popup.popupRippleRadius = popup.popupRippleMaxRadius * t;
                    popup.popupRippleAlpha = 0.22f * (1f - t);
                    android.util.Log.i(ANIM_DBG, "popupRipple frame t=" + t + " alpha=" + popup.popupRippleAlpha);
                    editor.invalidate();
                });
        popup.popupRippleAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!popup.popupRippleHoldActive) {
                            popup.popupRippleActive = false;
                            popup.popupRippleAlpha = 0f;
                            popup.popupRippleRect.setEmpty();
                        }
                        if (!popup.popupRippleHoldActive && popup.popupHideAfterRipple && popup.showPopup) {
                            popup.popupHideAfterRipple = false;
                            popup.hidePopup();
                        }
                        editor.invalidate();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (!popup.popupRippleHoldActive) {
                            popup.popupRippleActive = false;
                            popup.popupRippleAlpha = 0f;
                            popup.popupRippleRect.setEmpty();
                        }
                        if (!popup.popupRippleHoldActive) {
                            popup.popupHideAfterRipple = false;
                        }
                        editor.invalidate();
                    }
                });
        popup.popupRippleAnimator.start();
    }

    public void startRippleHold(int action, float x, float y) {
        FunctionLog.f("PopupAnimation", "startRippleHold", action, x, y);
        android.util.Log.i(ANIM_DBG, "popupRippleHold action=" + action + " x=" + x + " y=" + y);
        RectF r = popup.getPopupRectForAction(action);
        if (r.isEmpty()) return;
        popup.popupRippleHoldActive = true;
        popup.popupHideAfterRipple = false;
        popup.popupRippleRect.set(r);
        popup.popupRippleX = Math.max(r.left, Math.min(x, r.right));
        popup.popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
        popup.popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
        popup.popupRippleRadius = popup.popupRippleMaxRadius;
        popup.popupRippleAlpha = 0.22f;
        popup.popupRippleActive = true;
        if (popup.popupRippleAnimator != null) popup.popupRippleAnimator.cancel();
        editor.invalidate();
    }

    public void cancelRipple() {
        FunctionLog.f("PopupAnimation", "cancelRipple");
        if (popup.popupRippleAnimator != null) popup.popupRippleAnimator.cancel();
        popup.popupRippleHoldActive = false;
        popup.popupRippleActive = false;
        popup.popupRippleAlpha = 0f;
        popup.popupRippleRect.setEmpty();
        popup.popupHideAfterRipple = false;
        editor.invalidate();
    }
}
