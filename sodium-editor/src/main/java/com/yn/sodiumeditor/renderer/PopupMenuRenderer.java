package com.yn.sodiumeditor.renderer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.config.PopupConfig;
import com.yn.sodiumeditor.state.PopupMenuState;

/**
 * Renderer class for popup menu.
 * Handles drawing the popup menu with buttons, labels, and ripple effect.
 */
public class PopupMenuRenderer {

    private final SodiumEditor view;
    private final PopupConfig config;
    private final PopupMenuState state;

    private final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path popupRippleClipPath = new Path();

    private float popupPadding = 0f;
    private float popupCorner = 0f;
    private float btnSpacing = 0f;
    private float btnHeight = 0f;
    private float btnWidth = 0f;
    private float popupLabelPadding = 0f;

    public PopupMenuRenderer(SodiumEditor view, PopupConfig config, PopupMenuState state) {
        this.view = view;
        this.config = config;
        this.state = state;
        popupTextPaint.setTextAlign(Paint.Align.LEFT);
        popupTextPaint.setTypeface(getEditorTypefaceForPopup());
        popupRipplePaint.setColor(0xFFFFFFFF);
        applyPopupConfig();
    }

    public void applyPopupConfig() {
        float density = view.getResources().getDisplayMetrics().density;
        popupPadding = config.popupPaddingDp * density;
        popupCorner = config.popupCornerDp * density;
        btnSpacing = config.btnSpacingDp * density;
        btnHeight = config.btnHeightDp * density;
        btnWidth = config.btnWidthDp * density;
        popupLabelPadding = config.popupLabelPaddingDp * density;
        popupBgPaint.setColor(config.popupBackgroundColor);
        popupTextPaint.setColor(config.popupTextColor);
        popupTextPaint.setTextSize(spToPx(config.popupTextSizeSp));
    }

    public void setPopupTextFollowsEditorTypeface(boolean follow) {
        config.popupTextFollowsEditorTypeface = follow;
        if (follow) {
            popupTextPaint.setTypeface(getEditorTypefaceForPopup());
        }
        if (state.showPopup) view.invalidate();
    }

    public void setPopupTextTypeface(@Nullable android.graphics.Typeface typeface) {
        config.popupTextFollowsEditorTypeface = false;
        popupTextPaint.setTypeface((typeface != null) ? typeface : android.graphics.Typeface.DEFAULT);
        if (state.showPopup) view.invalidate();
    }

    public void onEditorTypefaceChanged(@Nullable android.graphics.Typeface tf) {
        if (config.popupTextFollowsEditorTypeface) {
            popupTextPaint.setTypeface((tf != null) ? tf : android.graphics.Typeface.DEFAULT);
        }
    }

    public void drawPopup(Canvas canvas) {
        if (state.popupAlpha <= 0f) return;
        applyPopupConfig();
        Paint bgPaint = popupBgPaint;

        state.resetButtonRects();

        final java.util.List<Integer> actions = new java.util.ArrayList<>();
        if (state.isMinimalPopup) {
            actions.add(PopupConfig.POPUP_ACTION_SELECT_ALL);
            if (!view.editorConfig.behaviorConfig.isReadOnly) {
                actions.add(PopupConfig.POPUP_ACTION_PASTE);
            }
        } else {
            final boolean hideCopyCut = shouldHideCopyCutForSelection();
            actions.add(PopupConfig.POPUP_ACTION_SELECT_ALL);
            if (!hideCopyCut) {
                if (!view.editorConfig.behaviorConfig.isReadOnly) {
                    actions.add(PopupConfig.POPUP_ACTION_CUT);
                }
                actions.add(PopupConfig.POPUP_ACTION_COPY);
            }
            if (!view.editorConfig.behaviorConfig.isReadOnly) {
                actions.add(PopupConfig.POPUP_ACTION_PASTE);
                actions.add(PopupConfig.POPUP_ACTION_DELETE);
            }
        }

        if (actions.isEmpty()) {
            hidePopup();
            return;
        }

        final int btnCount = actions.size();
        float labelPad = popupLabelPadding;
        float[] btnWidths = new float[btnCount];
        float totalBtnWidths = 0f;
        for (int i = 0; i < btnCount; i++) {
            int action = actions.get(i);
            String label = config.getLabelForAction(action);
            float labelWidth = popupTextPaint.measureText(label);
            float w = config.popupFitToLabel ? (labelWidth + labelPad) : btnWidth;
            btnWidths[i] = Math.max(0f, w);
            totalBtnWidths += btnWidths[i];
        }
        float localBtnHeight = btnHeight;
        float localBtnSpacing = btnSpacing;
        float localPopupPadding = popupPadding;

        float totalWidth =
                totalBtnWidths + (localBtnSpacing * (btnCount - 1)) + (localPopupPadding * 2);
        float totalHeight = localBtnHeight + (localPopupPadding * 2);
        float availableWidth = view.getWidth() - (localPopupPadding * 2);
        if (availableWidth > 0f && totalWidth > availableWidth) {
            float availableForButtons = availableWidth - (localBtnSpacing * (btnCount - 1));
            if (availableForButtons < 0f) {
                localBtnSpacing = 0f;
                availableForButtons = availableWidth;
            }
            float scale = (totalBtnWidths > 0f) ? (availableForButtons / totalBtnWidths) : 1f;
            if (scale < 1f) {
                totalBtnWidths = 0f;
                for (int i = 0; i < btnCount; i++) {
                    btnWidths[i] = Math.max(0f, btnWidths[i] * scale);
                    totalBtnWidths += btnWidths[i];
                }
            }
            totalWidth = totalBtnWidths + (localBtnSpacing * (btnCount - 1)) + (localPopupPadding * 2);
        }

        float anchorX, anchorYTop, anchorYBottom;
        if (state.isMinimalPopup || !view.selectionState.hasSelection()) {
            String cursorLineText = view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine());
            anchorX = getViewXForLineChar(cursorLineText, view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
            anchorYTop = getViewYTopForLineChar(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
            anchorYBottom = anchorYTop + view.lineHeight;
        } else {
            int nStartLine, nEndLine, nEndChar;
            String endLineText;
            if (view.comparePos(view.selectionState.selStartLine, view.selectionState.selStartChar, view.selectionState.selEndLine, view.selectionState.selEndChar)
                    <= 0) {
                nStartLine = view.selectionState.selStartLine;
                nEndLine = view.selectionState.selEndLine;
                nEndChar = view.selectionState.selEndChar;
                endLineText = view.viewRender.textRender.getLineTextForRender(nEndLine);
            } else {
                nStartLine = view.selectionState.selEndLine;
                nEndLine = view.selectionState.selStartLine;
                nEndChar = view.selectionState.selStartChar;
                endLineText = view.viewRender.textRender.getLineTextForRender(nEndLine);
            }

            anchorYTop = getViewYTopForLineChar(nStartLine, 0);
            anchorYBottom = getViewYTopForLineChar(nEndLine, nEndChar) + view.lineHeight;
            anchorX = getViewXForLineChar(endLineText, nEndLine, nEndChar);
        }

        float proposedLeft = anchorX - totalWidth / 2f;
        if (proposedLeft < 0) proposedLeft = 0;
        if (proposedLeft + totalWidth > view.getWidth()) proposedLeft = view.getWidth() - totalWidth;
        if (proposedLeft < 0) proposedLeft = 0;

        final float popupVerticalPadding = view.lineHeight * 0.75f;

        float topAbove = anchorYTop - totalHeight - popupVerticalPadding;
        float topBelow = anchorYBottom + popupVerticalPadding;

        float finalTop;
        float visibleBottomBound = view.getHeight() - view.keyboardHeight;

        if (topAbove >= 0) {
            finalTop = topAbove;
        } else if (topBelow + totalHeight <= visibleBottomBound) {
            finalTop = topBelow;
        } else {
            finalTop = Math.max(0, visibleBottomBound - totalHeight - popupPadding);
        }

        state.popupRect.set(proposedLeft, finalTop, proposedLeft + totalWidth, finalTop + totalHeight);
        int bgBaseAlpha = bgPaint.getAlpha();
        int textBaseAlpha = popupTextPaint.getAlpha();
        bgPaint.setAlpha((int) (bgBaseAlpha * state.popupAlpha));
        popupTextPaint.setAlpha((int) (textBaseAlpha * state.popupAlpha));
        canvas.drawRoundRect(state.popupRect, popupCorner, popupCorner, bgPaint);

        float bx = state.popupRect.left + localPopupPadding;
        float by = state.popupRect.top + localPopupPadding;

        if (state.popupRippleActive && state.popupRippleAlpha > 0f && !state.popupRippleRect.isEmpty()) {
            int rippleAlpha = (int) (255f * Math.max(0f, Math.min(1f, state.popupRippleAlpha * state.popupAlpha)));
            int base = popupRipplePaint.getColor();
            popupRipplePaint.setColor((base & 0x00FFFFFF) | (rippleAlpha << 24));
            canvas.save();
            float rippleCorner = Math.min(popupCorner, localBtnHeight * 0.5f);
            popupRippleClipPath.reset();
            popupRippleClipPath.addRoundRect(
                    state.popupRippleRect, rippleCorner, rippleCorner, Path.Direction.CW);
            canvas.clipPath(popupRippleClipPath);
            canvas.drawCircle(state.popupRippleX, state.popupRippleY, state.popupRippleRadius, popupRipplePaint);
            canvas.restore();
            popupRipplePaint.setColor(base);
        }

        for (int i = 0; i < btnCount; i++) {
            int action = actions.get(i);
            RectF r = getPopupRectForAction(action);
            float localBtnWidth = btnWidths[i];
            r.set(bx, by, bx + localBtnWidth, by + localBtnHeight);
            String label = config.getLabelForAction(action);
            float maxTextWidth = Math.max(0f, localBtnWidth - labelPad);
            drawButton(canvas, r, label, popupTextPaint, maxTextWidth);
            bx += localBtnWidth + localBtnSpacing;
        }
        bgPaint.setAlpha(bgBaseAlpha);
        popupTextPaint.setAlpha(textBaseAlpha);
    }

    public RectF getPopupRectForAction(int action) {
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

    private void drawButton(Canvas canvas, RectF r, String label, Paint txtPaint, float maxTextWidth) {
        String drawLabel = label;
        if (maxTextWidth > 0f) {
            TextPaint ellipsizePaint =
                    (txtPaint instanceof TextPaint) ? (TextPaint) txtPaint : new TextPaint(txtPaint);
            drawLabel =
                    TextUtils.ellipsize(label, ellipsizePaint, maxTextWidth, TextUtils.TruncateAt.END)
                            .toString();
        }
        float textWidth = txtPaint.measureText(drawLabel);
        float cx = r.centerX();
        float cy = r.centerY() - ((txtPaint.descent() + txtPaint.ascent()) / 2f);
        canvas.drawText(drawLabel, cx - textWidth / 2f, cy, txtPaint);
    }

    private float spToPx(float sp) {
        return sp * view.getResources().getDisplayMetrics().scaledDensity;
    }

    @Nullable
    private android.graphics.Typeface getEditorTypefaceForPopup() {
        return view.editorConfig.paint.getTypeface();
    }

    private boolean shouldHideCopyCutForSelection() {
        return view.selectionHandler.shouldHideCopyCutForSelection();
    }

    private float getViewXForLineChar(String line, int globalLine, int ch) {
        if (line == null) line = "";
        int safeChar = Math.max(0, Math.min(ch, view.editorIO.textIO.getLogicalLineLength(globalLine, line)));
        if (!view.wrapWordState.isWordWrapEnabled) {
            return view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl)
                    + view.highlightRenderer.measureText(line, safeChar, globalLine)
                    - view.getEffectiveScrollX();
        }
        int[] starts = view.wrapWordEngine.getWrapStartsForLine(view, globalLine, line, Math.max(1, Math.round(view.getWidth() - view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl))), view.editorConfig.paint);
        int seg = view.wrapWordEngine.getWrapSegmentIndexForChar(starts, safeChar);
        int segStart = view.wrapWordEngine.getWrapSegmentStart(starts, seg);
        float x =
                view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
                        view, line, segStart, safeChar, view.editorConfig.paint);
        return view.lineNumberRenderer.getTextStartX(view.editorConfig.paddingLeft, view.isRtl) + x - view.getEffectiveScrollX();
    }

    private float getViewYTopForLineChar(int globalLine, int ch) {
        int v = view.viewRender.textRender.getVisualIndexForLineAndChar(globalLine, ch);
        return v * view.lineHeight - view.scrollManager.scrollY;
    }

    public void showPopupAnimated() {
        if (!state.showPopup) {
            state.showPopup = true;
        }
        startPopupFade(1f);
    }

    public void hidePopupAnimated() {
        state.popupPressedAction = 0;
        state.clearRippleState();
        startPopupFade(0f);
    }

    private void startPopupFade(float targetAlpha) {
        if (state.popupFadeAnimator != null) state.popupFadeAnimator.cancel();
        float startAlpha = state.popupAlpha;
        long duration = (targetAlpha > startAlpha) ? PopupConfig.POPUP_FADE_IN_MS : PopupConfig.POPUP_FADE_OUT_MS;
        state.popupFadeAnimator = ValueAnimator.ofFloat(startAlpha, targetAlpha);
        state.popupFadeAnimator.setDuration(duration);
        state.popupFadeAnimator.setInterpolator(new DecelerateInterpolator());
        state.popupFadeAnimator.addUpdateListener(
                a -> {
                    Object v = a.getAnimatedValue();
                    state.popupAlpha = (v instanceof Float) ? (Float) v : targetAlpha;
                    view.invalidate();
                });
        state.popupFadeAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (state.popupAlpha <= 0f) {
                            state.showPopup = false;
                            state.isMinimalPopup = false;
                        }
                        view.invalidate();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (state.popupAlpha <= 0f) {
                            state.showPopup = false;
                            state.isMinimalPopup = false;
                        }
                        view.invalidate();
                    }
                });
        state.popupFadeAnimator.start();
    }

    public void hidePopup() {
        hidePopupAnimated();
    }
}
