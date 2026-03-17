package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the popup menu functionality for SodiumEditor.
 * Handles drawing, positioning, and user interaction with the popup menu.
 */
public class Popup {

  // Popup action constants
  public static final int POPUP_ACTION_COPY = 1;
  public static final int POPUP_ACTION_CUT = 2;
  public static final int POPUP_ACTION_PASTE = 3;
  public static final int POPUP_ACTION_DELETE = 4;
  public static final int POPUP_ACTION_SELECT_ALL = 5;

  // Animation duration constants
  public static final long POPUP_FADE_IN_MS = 140;
  public static final long POPUP_FADE_OUT_MS = 110;

  // Popup state
  public boolean showPopup = false;
  public boolean isMinimalPopup = false;
  public RectF popupRect = new RectF();
  public RectF btnCopyRect = new RectF();
  public RectF btnCutRect = new RectF();
  public RectF btnPasteRect = new RectF();
  public RectF btnDeleteRect = new RectF();
  public RectF btnSelectAllRect = new RectF();

  // Popup configuration (dp)
  public float popupPaddingDp = 5f;
  public float popupCornerDp = 60f;
  public float btnSpacingDp = 5f;
  public float btnHeightDp = 30f;
  public float btnWidthDp = 55f;
  public float popupLabelPaddingDp = 5f;
  public float popupTextSizeSp = 12f;

  // Popup colors
  public int popupTextColor = 0xFFFFFFFF;
  public int popupBackgroundColor = 0xFF424242;

  // Popup behavior
  public boolean popupFitToLabel = true;
  public boolean popupTextFollowsEditorTypeface = true;

  // Popup labels
  public String popupLabelCopy = "Copy";
  public String popupLabelCut = "Cut";
  public String popupLabelPaste = "Paste";
  public String popupLabelDelete = "Delete";
  public String popupLabelSelectAll = "Select All";

  // Popup animation state
  public float popupAlpha = 0f;
  @Nullable public ValueAnimator popupFadeAnimator;

  // Popup interaction state
  public int popupPressedAction = 0;
  public boolean pendingPopupAfterDoubleTap = false;
  public boolean popupRippleActive = false;
  public RectF popupRippleRect = new RectF();
  public float popupRippleX = 0f;
  public float popupRippleY = 0f;
  public float popupRippleRadius = 0f;
  public float popupRippleMaxRadius = 0f;
  public float popupRippleAlpha = 0f;
  public boolean popupRippleHoldActive = false;
  @Nullable public ValueAnimator popupRippleAnimator;

  // Paint objects
  public final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint popupRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Path popupRippleClipPath = new Path();

  // Computed values (pixels)
  public float popupPadding = 0f;
  public float popupCorner = 0f;
  public float btnSpacing = 0f;
  public float btnHeight = 0f;
  public float btnWidth = 0f;
  public float popupLabelPadding = 0f;

  // Reference to parent editor
  private final SodiumEditor editor;

  public Popup(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Initialize popup configuration based on display metrics.
   * Should be called when the editor is ready or configuration changes.
   */
  public void applyPopupConfig() {
    float density = editor.getResources().getDisplayMetrics().density;
    popupPadding = popupPaddingDp * density;
    popupCorner = popupCornerDp * density;
    btnSpacing = btnSpacingDp * density;
    btnHeight = btnHeightDp * density;
    btnWidth = btnWidthDp * density;
    popupLabelPadding = popupLabelPaddingDp * density;
    popupBgPaint.setColor(popupBackgroundColor);
    popupTextPaint.setColor(popupTextColor);
    popupTextPaint.setTextSize(spToPx(popupTextSizeSp));
  }

  /**
   * Set popup background color.
   * @param color The color to use for the popup background
   */
  public void setPopupBackgroundColor(int color) {
    popupBackgroundColor = color;
    popupBgPaint.setColor(color);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text color.
   * @param color The color to use for popup button text
   */
  public void setPopupTextColor(int color) {
    popupTextColor = color;
    popupTextPaint.setColor(color);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text size in scaled pixels.
   * @param sp The text size in scaled pixels
   */
  public void setPopupTextSize(float sp) {
    popupTextSizeSp = sp;
    popupTextPaint.setTextSize(spToPx(sp));
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text size in pixels.
   * @param sizePx The text size in pixels
   */
  public void setPopupTextSizePx(float sizePx) {
    float scaledDensity = editor.getResources().getDisplayMetrics().scaledDensity;
    popupTextSizeSp = (scaledDensity > 0f) ? (sizePx / scaledDensity) : popupTextSizeSp;
    popupTextPaint.setTextSize(sizePx);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set whether popup text follows the editor typeface.
   * @param follow true if popup text should use the same typeface as the editor
   */
  public void setPopupTextFollowsEditorTypeface(boolean follow) {
    popupTextFollowsEditorTypeface = follow;
    if (follow) {
      popupTextPaint.setTypeface(editor.paint.getTypeface());
    }
    if (showPopup) editor.invalidate();
  }

  /**
   * Set a custom typeface for popup text.
   * @param typeface The typeface to use, or null for default
   */
  public void setPopupTextTypeface(@Nullable android.graphics.Typeface typeface) {
    popupTextFollowsEditorTypeface = false;
    popupTextPaint.setTypeface((typeface != null) ? typeface : android.graphics.Typeface.DEFAULT);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set custom labels for all popup buttons.
   * @param copy Label for copy button
   * @param cut Label for cut button
   * @param paste Label for paste button
   * @param delete Label for delete button
   * @param selectAll Label for select all button
   */
  public void setPopupLabels(
      String copy, String cut, String paste, String delete, String selectAll) {
    popupLabelCopy = copy;
    popupLabelCut = cut;
    popupLabelPaste = paste;
    popupLabelDelete = delete;
    popupLabelSelectAll = selectAll;
    if (showPopup) editor.invalidate();
  }

  /**
   * Show the popup at the current selection.
   */
  public void showPopupAtSelection() {
    if (!editor.selection.hasSelection) return;
    isMinimalPopup = false;
    showPopupAnimated();
  }

  /**
   * Show a minimal popup at the cursor position.
   */
  public void showMinimalPopupAtCursor() {
    isMinimalPopup = true;
    showPopupAnimated();
  }

  /**
   * Hide the popup menu.
   */
  public void hidePopup() {
    hidePopupAnimated();
  }

  /**
   * Show the popup with fade-in animation.
   */
  private void showPopupAnimated() {
    if (!showPopup) {
      showPopup = true;
    }
    startPopupFade(1f);
  }

  /**
   * Hide the popup with fade-out animation.
   */
  private void hidePopupAnimated() {
    popupPressedAction = 0;
    cancelPopupRipple();
    startPopupFade(0f);
  }

  /**
   * Start fade animation to target alpha.
   * @param targetAlpha The target alpha value (0f for hide, 1f for show)
   */
  private void startPopupFade(float targetAlpha) {
    if (popupFadeAnimator != null) popupFadeAnimator.cancel();
    float startAlpha = popupAlpha;
    long duration = (targetAlpha > startAlpha) ? POPUP_FADE_IN_MS : POPUP_FADE_OUT_MS;
    popupFadeAnimator = ValueAnimator.ofFloat(startAlpha, targetAlpha);
    popupFadeAnimator.setDuration(duration);
    popupFadeAnimator.setInterpolator(new DecelerateInterpolator());
    popupFadeAnimator.addUpdateListener(
        a -> {
          Object v = a.getAnimatedValue();
          popupAlpha = (v instanceof Float) ? (Float) v : targetAlpha;
          editor.invalidate();
        });
    popupFadeAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (popupAlpha <= 0f) {
              showPopup = false;
              isMinimalPopup = false;
            }
            editor.invalidate();
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            if (popupAlpha <= 0f) {
              showPopup = false;
              isMinimalPopup = false;
            }
            editor.invalidate();
          }
        });
    popupFadeAnimator.start();
  }

  /**
   * Draw the popup menu on the canvas.
   * @param canvas The canvas to draw on
   */
  public void drawPopup(Canvas canvas) {
    if (popupAlpha <= 0f) return;
    applyPopupConfig();
    Paint bgPaint = popupBgPaint;

    // Reset rects (so .contains() is safe when buttons are hidden)
    btnCopyRect.setEmpty();
    btnCutRect.setEmpty();
    btnPasteRect.setEmpty();
    btnDeleteRect.setEmpty();
    btnSelectAllRect.setEmpty();

    // Buttons order
    final List<Integer> actions = new ArrayList<>();
    if (isMinimalPopup) {
      actions.add(POPUP_ACTION_SELECT_ALL);
      if (!editor.isReadOnly) {
        actions.add(POPUP_ACTION_PASTE);
      }
    } else {
      final boolean hideCopyCut = shouldHideCopyCutForSelection();
      actions.add(POPUP_ACTION_SELECT_ALL);
      if (!hideCopyCut) {
        if (!editor.isReadOnly) {
          actions.add(POPUP_ACTION_CUT);
        }
        actions.add(POPUP_ACTION_COPY);
      }
      if (!editor.isReadOnly) {
        actions.add(POPUP_ACTION_PASTE);
        actions.add(POPUP_ACTION_DELETE);
      }
    }

    if (actions.isEmpty()) {
      hidePopup();
      return;
    }

    final int btnCount = actions.size();
    float density = editor.getResources().getDisplayMetrics().density;
    float labelPad = popupLabelPadding;
    float[] btnWidths = new float[btnCount];
    float totalBtnWidths = 0f;
    for (int i = 0; i < btnCount; i++) {
      int action = actions.get(i);
      String label = getPopupLabelForAction(action);
      float labelWidth = popupTextPaint.measureText(label);
      float w = popupFitToLabel ? (labelWidth + labelPad) : btnWidth;
      btnWidths[i] = Math.max(0f, w);
      totalBtnWidths += btnWidths[i];
    }
    float localBtnHeight = btnHeight;
    float localBtnSpacing = btnSpacing;
    float localPopupPadding = popupPadding;

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

    if (isMinimalPopup || !editor.selection.hasSelection) {
      // Anchor to cursor
      String cursorLineText = editor.getLineTextForRender(editor.cursor.cursorLine);
      anchorX = editor.getViewXForLineChar(cursorLineText, editor.cursor.cursorLine, editor.cursor.cursorChar);
      anchorY_top = editor.getViewYTopForLineChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
      anchorY_bottom = anchorY_top + editor.lineHeight;
    } else {
      // Anchor to selection (existing logic)
      int nStartLine, nEndLine, nEndChar;
      String endLineText;
      if (editor.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, editor.selection.selEndLine, editor.selection.selEndChar) <= 0) {
        nStartLine = editor.selection.selStartLine;
        nEndLine = editor.selection.selEndLine;
        nEndChar = editor.selection.selEndChar;
        endLineText = editor.getLineTextForRender(nEndLine);
      } else {
        nStartLine = editor.selection.selEndLine;
        nEndLine = editor.selection.selStartLine;
        nEndChar = editor.selection.selStartChar;
        endLineText = editor.getLineTextForRender(nEndLine);
      }

      anchorY_top = editor.getViewYTopForLineChar(nStartLine, 0);
      anchorY_bottom = editor.getViewYTopForLineChar(nEndLine, nEndChar) + editor.lineHeight;
      anchorX = editor.getViewXForLineChar(endLineText, nEndLine, nEndChar);
    }

    float proposedLeft = anchorX - totalWidth / 2f;
    if (proposedLeft < 0) proposedLeft = 0;
    if (proposedLeft + totalWidth > editor.getWidth()) proposedLeft = editor.getWidth() - totalWidth;
    if (proposedLeft < 0) proposedLeft = 0;

    final float popupVerticalPadding = editor.lineHeight * 0.75f;

    float topAbove = anchorY_top - totalHeight - popupVerticalPadding;
    float topBelow = anchorY_bottom + popupVerticalPadding;

    float finalTop;
    float visibleBottomBound = editor.getHeight() - editor.keyboardHeight;

    if (topAbove >= 0) {
      finalTop = topAbove;
    } else if (topBelow + totalHeight <= visibleBottomBound) {
      finalTop = topBelow;
    } else {
      finalTop = Math.max(0, visibleBottomBound - totalHeight - popupPadding);
    }

    popupRect.set(proposedLeft, finalTop, proposedLeft + totalWidth, finalTop + totalHeight);
    int bgBaseAlpha = bgPaint.getAlpha();
    int textBaseAlpha = popupTextPaint.getAlpha();
    bgPaint.setAlpha((int) (bgBaseAlpha * popupAlpha));
    popupTextPaint.setAlpha((int) (textBaseAlpha * popupAlpha));
    canvas.drawRoundRect(popupRect, popupCorner, popupCorner, bgPaint);

    float bx = popupRect.left + localPopupPadding;
    float by = popupRect.top + localPopupPadding;

    if (popupRippleActive && popupRippleAlpha > 0f && !popupRippleRect.isEmpty()) {
      int rippleAlpha = (int) (255f * Math.max(0f, Math.min(1f, popupRippleAlpha * popupAlpha)));
      int base = popupRipplePaint.getColor();
      popupRipplePaint.setColor((base & 0x00FFFFFF) | (rippleAlpha << 24));
      canvas.save();
      float rippleCorner = Math.min(popupCorner, localBtnHeight * 0.5f);
      popupRippleClipPath.reset();
      popupRippleClipPath.addRoundRect(
          popupRippleRect, rippleCorner, rippleCorner, Path.Direction.CW);
      canvas.clipPath(popupRippleClipPath);
      canvas.drawCircle(popupRippleX, popupRippleY, popupRippleRadius, popupRipplePaint);
      canvas.restore();
      popupRipplePaint.setColor(base);
    }

    for (int i = 0; i < btnCount; i++) {
      int action = actions.get(i);
      RectF r = getPopupRectForAction(action);
      float localBtnWidth = btnWidths[i];
      r.set(bx, by, bx + localBtnWidth, by + localBtnHeight);
      String label = getPopupLabelForAction(action);
      float maxTextWidth = Math.max(0f, localBtnWidth - labelPad);
      drawButton(canvas, r, label, popupTextPaint, maxTextWidth);
      bx += localBtnWidth + localBtnSpacing;
    }
    bgPaint.setAlpha(bgBaseAlpha);
    popupTextPaint.setAlpha(textBaseAlpha);
  }

  /**
   * Check if copy/cut should be hidden for large selections.
   * @return true if copy/cut buttons should be hidden
   */
  public boolean shouldHideCopyCutForSelection() {
    if (!editor.selection.hasSelection) return true;

    int sL = editor.selection.selStartLine, eL = editor.selection.selEndLine;
    if (sL > eL) {
      int t = sL;
      sL = eL;
      eL = t;
    }
    long lines = (long) eL - (long) sL + 1L;
    return lines > editor.selection.hideCopyCutMaxLines;
  }

  /**
   * Get the RectF for a specific popup action button.
   * @param action The action constant
   * @return The RectF for the button
   */
  public RectF getPopupRectForAction(int action) {
    switch (action) {
      case POPUP_ACTION_COPY:
        return btnCopyRect;
      case POPUP_ACTION_CUT:
        return btnCutRect;
      case POPUP_ACTION_PASTE:
        return btnPasteRect;
      case POPUP_ACTION_DELETE:
        return btnDeleteRect;
      default:
        return btnSelectAllRect;
    }
  }

  /**
   * Get the label for a specific popup action button.
   * @param action The action constant
   * @return The label string
   */
  public String getPopupLabelForAction(int action) {
    switch (action) {
      case POPUP_ACTION_COPY:
        return popupLabelCopy;
      case POPUP_ACTION_CUT:
        return popupLabelCut;
      case POPUP_ACTION_PASTE:
        return popupLabelPaste;
      case POPUP_ACTION_DELETE:
        return popupLabelDelete;
      default:
        return popupLabelSelectAll;
    }
  }

  /**
   * Get the action at a specific coordinate.
   * @param x X coordinate
   * @param y Y coordinate
   * @return The action constant, or 0 if no action
   */
  public int getPopupActionAt(float x, float y) {
    if (btnCopyRect.contains(x, y)) return POPUP_ACTION_COPY;
    if (btnCutRect.contains(x, y)) return POPUP_ACTION_CUT;
    if (btnPasteRect.contains(x, y)) return POPUP_ACTION_PASTE;
    if (btnDeleteRect.contains(x, y)) return POPUP_ACTION_DELETE;
    if (btnSelectAllRect.contains(x, y)) return POPUP_ACTION_SELECT_ALL;
    return 0;
  }

  /**
   * Draw a single popup button.
   * @param canvas The canvas to draw on
   * @param r The button rect
   * @param label The button label
   * @param txtPaint The text paint
   * @param maxTextWidth Maximum text width for ellipsizing
   */
  public void drawButton(
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

  /**
   * Start ripple animation for a button press.
   * @param action The action being pressed
   * @param x X coordinate of the press
   * @param y Y coordinate of the press
   */
  public void startPopupRipple(int action, float x, float y) {
    RectF r = getPopupRectForAction(action);
    if (r.isEmpty()) return;
    popupRippleHoldActive = false;
    popupRippleRect.set(r);
    popupRippleX = Math.max(r.left, Math.min(x, r.right));
    popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
    popupRippleRadius = 0f;
    popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
    popupRippleAlpha = 0.22f;
    popupRippleActive = true;
    if (popupRippleAnimator != null) popupRippleAnimator.cancel();
    popupRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
    popupRippleAnimator.setDuration(220);
    popupRippleAnimator.setInterpolator(new DecelerateInterpolator());
    popupRippleAnimator.addUpdateListener(
        a -> {
          float t = (a.getAnimatedValue() instanceof Float) ? (Float) a.getAnimatedValue() : 1f;
          popupRippleRadius = popupRippleMaxRadius * t;
          popupRippleAlpha = 0.22f * (1f - t);
          editor.invalidate();
        });
    popupRippleAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            popupRippleActive = false;
            popupRippleAlpha = 0f;
            popupRippleRect.setEmpty();
            editor.invalidate();
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            popupRippleActive = false;
            popupRippleAlpha = 0f;
            popupRippleRect.setEmpty();
            editor.invalidate();
          }
        });
    popupRippleAnimator.start();
  }

  /**
   * Start ripple hold animation for a long press.
   * @param action The action being pressed
   * @param x X coordinate of the press
   * @param y Y coordinate of the press
   */
  public void startPopupRippleHold(int action, float x, float y) {
    RectF r = getPopupRectForAction(action);
    if (r.isEmpty()) return;
    popupRippleHoldActive = true;
    popupRippleRect.set(r);
    popupRippleX = Math.max(r.left, Math.min(x, r.right));
    popupRippleY = Math.max(r.top, Math.min(y, r.bottom));
    popupRippleMaxRadius = (float) Math.hypot(r.width(), r.height());
    popupRippleRadius = popupRippleMaxRadius;
    popupRippleAlpha = 0.22f;
    popupRippleActive = true;
    if (popupRippleAnimator != null) popupRippleAnimator.cancel();
    editor.invalidate();
  }

  /**
   * Cancel any active ripple animation.
   */
  public void cancelPopupRipple() {
    if (popupRippleAnimator != null) popupRippleAnimator.cancel();
    popupRippleHoldActive = false;
    popupRippleActive = false;
    popupRippleAlpha = 0f;
    popupRippleRect.setEmpty();
    editor.invalidate();
  }

  /**
   * Convert scaled pixels to pixels.
   * @param sp The value in scaled pixels
   * @return The value in pixels
   */
  private float spToPx(float sp) {
    return sp * editor.getResources().getDisplayMetrics().scaledDensity;
  }
}
