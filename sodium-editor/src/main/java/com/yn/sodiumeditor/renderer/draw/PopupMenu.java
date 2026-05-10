package com.yn.sodiumeditor.renderer.draw;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.text.TextPaint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.scroll.Popup;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles drawing logic for the popup menu.
 */
public class PopupMenu {
    private final SodiumEditor editor;
    private final Popup popup;

    public PopupMenu(SodiumEditor editor, Popup popup) {
        this.editor = editor;
        this.popup = popup;
    }

    public void drawPopup(Canvas canvas) {
        if (popup.popupAlpha <= 0f) return;
        popup.applyPopupConfig();
        Paint bgPaint = popup.popupBgPaint;

        // Reset rects (so .contains() is safe when buttons are hidden)
        popup.btnCopyRect.setEmpty();
        popup.btnCutRect.setEmpty();
        popup.btnPasteRect.setEmpty();
        popup.btnDeleteRect.setEmpty();
        popup.btnSelectAllRect.setEmpty();

        // Buttons order
        final List<Integer> actions = new ArrayList<>();
        if (popup.isMinimalPopup) {
            actions.add(Popup.POPUP_ACTION_SELECT_ALL);
            if (!editor.view.isReadOnly) {
                actions.add(Popup.POPUP_ACTION_PASTE);
            }
        } else if (editor.selection.hasSelection) {
            final boolean hideCopyCut = popup.shouldHideCopyCutForSelection();
            actions.add(Popup.POPUP_ACTION_SELECT_ALL);
            if (!hideCopyCut) {
                if (!editor.view.isReadOnly) {
                    actions.add(Popup.POPUP_ACTION_CUT);
                }
                actions.add(Popup.POPUP_ACTION_COPY);
            }
            if (!editor.view.isReadOnly) {
              actions.add(Popup.POPUP_ACTION_PASTE);
              if (popup.isDeleteButtonEnabled) {
                actions.add(Popup.POPUP_ACTION_DELETE);
              }
            }        }

        if (actions.isEmpty()) {
            if ((popup.isFadingOut || popup.shouldKeepVisible()) && !popup.popupLastDrawnActions.isEmpty()) {
                actions.addAll(popup.popupLastDrawnActions);
            } else {
                if (popup.showPopup && !popup.isFadingOut) popup.hidePopup();
                return;
            }
        } else {
            popup.popupLastDrawnActions.clear();
            popup.popupLastDrawnActions.addAll(actions);
        }

        final int btnCount = actions.size();
        float labelPad = popup.popupLabelPadding;
        float[] btnWidths = new float[btnCount];
        float totalBtnWidths = 0f;
        for (int i = 0; i < btnCount; i++) {
            int action = actions.get(i);
            String label = popup.getPopupLabelForAction(action);
            float labelWidth = popup.popupTextPaint.measureText(label);
            float w = popup.popupFitToLabel ? (labelWidth + labelPad) : popup.btnWidth;
            btnWidths[i] = Math.max(0f, w);
            totalBtnWidths += btnWidths[i];
        }
        float localBtnHeight = popup.btnHeight;
        float localBtnSpacing = popup.btnSpacing;
        float localPopupPadding = popup.popupPadding;

        float totalWidth =
                totalBtnWidths + (localBtnSpacing * (btnCount - 1)) + (localPopupPadding * 2);
        float totalHeight = localBtnHeight + (localPopupPadding * 2);
        float availableWidth = editor.getWidth() - (localPopupPadding * 2);
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

        // --- POPUP POSITIONING LOGIC ---

        float anchorX, anchorY_top, anchorY_bottom;

        if (popup.isMinimalPopup || !editor.selection.hasSelection) {
            // Anchor to cursor
            String cursorLineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
            anchorX = editor.layout.getViewXForLineChar(cursorLineText, editor.cursor.cursorLine, editor.cursor.cursorChar);
            anchorY_top = editor.layout.getViewYTopForLineChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
            anchorY_bottom = anchorY_top + editor.textRender.lineHeight;
        } else {
            // Anchor to selection
            int nStartLine, nEndLine, nEndChar;
            String endLineText;
            if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, editor.selection.selEndLine, editor.selection.selEndChar) <= 0) {
                nStartLine = editor.selection.selStartLine;
                nEndLine = editor.selection.selEndLine;
                nEndChar = editor.selection.selEndChar;
                endLineText = editor.windowRender.getLineTextForRender(nEndLine);
            } else {
                nStartLine = editor.selection.selEndLine;
                nEndLine = editor.selection.selStartLine;
                nEndChar = editor.selection.selStartChar;
                endLineText = editor.windowRender.getLineTextForRender(nEndLine);
            }

            anchorY_top = editor.layout.getViewYTopForLineChar(nStartLine, 0);
            anchorY_bottom = editor.layout.getViewYTopForLineChar(nEndLine, nEndChar) + editor.textRender.lineHeight;
            anchorX = editor.layout.getViewXForLineChar(endLineText, nEndLine, nEndChar);
        }

        float proposedLeft = anchorX - totalWidth / 2f;
        if (proposedLeft < 0) proposedLeft = 0;
        if (proposedLeft + totalWidth > editor.getWidth()) proposedLeft = editor.getWidth() - totalWidth;
        if (proposedLeft < 0) proposedLeft = 0;

        final float popupVerticalPadding = editor.textRender.lineHeight * 0.75f;
        final float handleClearance = Math.max(12f, editor.textRender.lineHeight * 0.35f);

        float obstacleTop = anchorY_top;
        float obstacleBottom = anchorY_bottom;
        if (!popup.isMinimalPopup && editor.selection.hasSelection) {
            editor.selectionHandles.updateHandlesPosition();
            if (!editor.selectionHandles.leftHandleRect.isEmpty()) {
                obstacleTop = Math.min(
                        obstacleTop,
                        Math.min(
                                editor.selectionHandles.leftHandleRect.top,
                                editor.selectionHandles.rightHandleRect.top));
                obstacleBottom = Math.max(
                        obstacleBottom,
                        Math.max(
                                editor.selectionHandles.leftHandleRect.bottom,
                                editor.selectionHandles.rightHandleRect.bottom));
            }
        }

        float topAbove = obstacleTop - totalHeight - popupVerticalPadding - handleClearance;
        float topBelow = obstacleBottom + popupVerticalPadding + handleClearance;

        float finalTop;
        float visibleBottomBound = editor.getHeight() - editor.view.keyboardHeight;

        if (topAbove >= 0) {
            finalTop = topAbove;
        } else if (topBelow + totalHeight <= visibleBottomBound) {
            finalTop = topBelow;
        } else {
            finalTop = Math.max(0, visibleBottomBound - totalHeight - popup.popupPadding);
        }

        popup.popupRect.set(proposedLeft, finalTop, proposedLeft + totalWidth, finalTop + totalHeight);
        int bgBaseAlpha = bgPaint.getAlpha();
        int textBaseAlpha = popup.popupTextPaint.getAlpha();
        bgPaint.setAlpha((int) (bgBaseAlpha * popup.popupAlpha));
        popup.popupTextPaint.setAlpha((int) (textBaseAlpha * popup.popupAlpha));
        canvas.drawRoundRect(popup.popupRect, popup.popupCorner, popup.popupCorner, bgPaint);

        float bx = popup.popupRect.left + localPopupPadding;
        float by = popup.popupRect.top + localPopupPadding;

        if (popup.popupRippleActive && popup.popupRippleAlpha > 0f && !popup.popupRippleRect.isEmpty()) {
            int rippleAlpha = (int) (255f * Math.max(0f, Math.min(1f, popup.popupRippleAlpha * popup.popupAlpha)));
            int base = popup.popupRipplePaint.getColor();
            popup.popupRipplePaint.setColor((base & 0x00FFFFFF) | (rippleAlpha << 24));
            canvas.save();
            float rippleCorner = Math.min(popup.popupCorner, localBtnHeight * 0.5f);
            popup.popupRippleClipPath.reset();
            popup.popupRippleClipPath.addRoundRect(
                    popup.popupRippleRect, rippleCorner, rippleCorner, Path.Direction.CW);
            canvas.clipPath(popup.popupRippleClipPath);
            canvas.drawCircle(popup.popupRippleX, popup.popupRippleY, popup.popupRippleRadius, popup.popupRipplePaint);
            canvas.restore();
            popup.popupRipplePaint.setColor(base);
        }

        for (int i = 0; i < btnCount; i++) {
            int action = actions.get(i);
            RectF r = popup.getPopupRectForAction(action);
            float localBtnWidth = btnWidths[i];
            r.set(bx, by, bx + localBtnWidth, by + localBtnHeight);
            String label = popup.getPopupLabelForAction(action);
            float maxTextWidth = Math.max(0f, localBtnWidth - labelPad);
            drawButton(canvas, r, label, popup.popupTextPaint, maxTextWidth);
            bx += localBtnWidth + localBtnSpacing;
        }
        bgPaint.setAlpha(bgBaseAlpha);
        popup.popupTextPaint.setAlpha(textBaseAlpha);
    }

    private void drawButton(
            Canvas canvas, RectF r, String label, Paint txtPaint, float maxTextWidth) {
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
}
