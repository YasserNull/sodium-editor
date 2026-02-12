package com.yn.sodiumeditor.view;

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
import java.util.ArrayList;
import java.util.List;

public final class PopupMenuManager {
  static final int POPUP_ACTION_COPY = 1;
  static final int POPUP_ACTION_CUT = 2;
  static final int POPUP_ACTION_PASTE = 3;
  static final int POPUP_ACTION_DELETE = 4;
  static final int POPUP_ACTION_SELECT_ALL = 5;
  private static final long POPUP_FADE_IN_MS = 140;
  private static final long POPUP_FADE_OUT_MS = 110;

  private final SodiumEditorView view;

  boolean showPopup = false;
  private boolean isMinimalPopup = false;
  private final RectF popupRect = new RectF();
  private final RectF btnCopyRect = new RectF();
  private final RectF btnCutRect = new RectF();
  private final RectF btnPasteRect = new RectF();
  private final RectF btnDeleteRect = new RectF();
  private final RectF btnSelectAllRect = new RectF();

  public float popupPaddingDp = 5f;
  public float popupCornerDp = 60f;
  public float btnSpacingDp = 5f;
  public float btnHeightDp = 30f;
  public float btnWidthDp = 55f;
  public float popupLabelPaddingDp = 5f;
  public float popupTextSizeSp = 12f;
  public int popupTextColor = 0xFFFFFFFF;
  public int popupBackgroundColor = 0xFF424242;
  public boolean popupFitToLabel = true;

  private float popupPadding = 0f;
  private float popupCorner = 0f;
  private float btnSpacing = 0f;
  private float btnHeight = 0f;
  private float btnWidth = 0f;
  private float popupLabelPadding = 0f;

  private boolean popupTextFollowsEditorTypeface = true;
  private String popupLabelCopy = "Copy";
  private String popupLabelCut = "Cut";
  private String popupLabelPaste = "Paste";
  private String popupLabelDelete = "Delete";
  private String popupLabelSelectAll = "Select All";

  private float popupAlpha = 0f;
  @Nullable private ValueAnimator popupFadeAnimator;
  private int popupPressedAction = 0;

  private boolean popupRippleActive = false;
  private final RectF popupRippleRect = new RectF();
  private float popupRippleX = 0f;
  private float popupRippleY = 0f;
  private float popupRippleRadius = 0f;
  private float popupRippleMaxRadius = 0f;
  private float popupRippleAlpha = 0f;
  private boolean popupRippleHoldActive = false;
  @Nullable private ValueAnimator popupRippleAnimator;

  private final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path popupRippleClipPath = new Path();

  PopupMenuManager(SodiumEditorView view) {
    this.view = view;
    popupTextPaint.setTextAlign(Paint.Align.LEFT);
    popupTextPaint.setTypeface(view.getEditorTypefaceForPopup());
    popupRipplePaint.setColor(0xFFFFFFFF);
    applyPopupConfig();
  }

  void applyPopupConfig() {
    float density = view.getResources().getDisplayMetrics().density;
    popupPadding = popupPaddingDp * density;
    popupCorner = popupCornerDp * density;
    btnSpacing = btnSpacingDp * density;
    btnHeight = btnHeightDp * density;
    btnWidth = btnWidthDp * density;
    popupLabelPadding = popupLabelPaddingDp * density;
    popupBgPaint.setColor(popupBackgroundColor);
    popupTextPaint.setColor(popupTextColor);
    popupTextPaint.setTextSize(view.spToPxForPopup(popupTextSizeSp));
  }

  public void setPopupBackgroundColor(int color) {
    popupBackgroundColor = color;
    popupBgPaint.setColor(color);
    if (showPopup) view.invalidate();
  }

  public void setPopupTextColor(int color) {
    popupTextColor = color;
    popupTextPaint.setColor(color);
    if (showPopup) view.invalidate();
  }

  public void setPopupTextSize(float sp) {
    popupTextSizeSp = sp;
    popupTextPaint.setTextSize(view.spToPxForPopup(sp));
    if (showPopup) view.invalidate();
  }

  public void setPopupTextSizePx(float sizePx) {
    float scaledDensity = view.getResources().getDisplayMetrics().scaledDensity;
    popupTextSizeSp = (scaledDensity > 0f) ? (sizePx / scaledDensity) : popupTextSizeSp;
    popupTextPaint.setTextSize(sizePx);
    if (showPopup) view.invalidate();
  }

  public void setPopupTextFollowsEditorTypeface(boolean follow) {
    popupTextFollowsEditorTypeface = follow;
    if (follow) {
      popupTextPaint.setTypeface(view.getEditorTypefaceForPopup());
    }
    if (showPopup) view.invalidate();
  }

  public void setPopupTextTypeface(@Nullable android.graphics.Typeface typeface) {
    popupTextFollowsEditorTypeface = false;
    popupTextPaint.setTypeface((typeface != null) ? typeface : android.graphics.Typeface.DEFAULT);
    if (showPopup) view.invalidate();
  }

  void setPopupLabels(String copy, String cut, String paste, String delete, String selectAll) {
    popupLabelCopy = copy;
    popupLabelCut = cut;
    popupLabelPaste = paste;
    popupLabelDelete = delete;
    popupLabelSelectAll = selectAll;
    if (showPopup) view.invalidate();
  }

  void onEditorTypefaceChanged(@Nullable android.graphics.Typeface tf) {
    if (popupTextFollowsEditorTypeface) {
      popupTextPaint.setTypeface((tf != null) ? tf : android.graphics.Typeface.DEFAULT);
    }
  }

  void drawPopup(Canvas canvas) {
    if (popupAlpha <= 0f) return;
    applyPopupConfig();
    Paint bgPaint = popupBgPaint;

    btnCopyRect.setEmpty();
    btnCutRect.setEmpty();
    btnPasteRect.setEmpty();
    btnDeleteRect.setEmpty();
    btnSelectAllRect.setEmpty();

    final List<Integer> actions = new ArrayList<>();
    if (isMinimalPopup) {
      actions.add(POPUP_ACTION_SELECT_ALL);
      if (!view.isReadOnly) {
        actions.add(POPUP_ACTION_PASTE);
      }
    } else {
      final boolean hideCopyCut = view.shouldHideCopyCutForPopup();
      actions.add(POPUP_ACTION_SELECT_ALL);
      if (!hideCopyCut) {
        if (!view.isReadOnly) {
          actions.add(POPUP_ACTION_CUT);
        }
        actions.add(POPUP_ACTION_COPY);
      }
      if (!view.isReadOnly) {
        actions.add(POPUP_ACTION_PASTE);
        actions.add(POPUP_ACTION_DELETE);
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
    if (isMinimalPopup || !view.selectionManager.hasSelection()) {
      String cursorLineText = view.getLineTextForRender(view.cursorManager.getLine());
      anchorX = view.getViewXForPopup(cursorLineText, view.cursorManager.getLine(), view.cursorManager.getChar());
      anchorYTop = view.getViewYTopForPopup(view.cursorManager.getLine(), view.cursorManager.getChar());
      anchorYBottom = anchorYTop + view.lineHeight;
    } else {
      int nStartLine, nEndLine, nEndChar;
      String endLineText;
      if (view.comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar)
          <= 0) {
        nStartLine = view.selectionManager.selStartLine;
        nEndLine = view.selectionManager.selEndLine;
        nEndChar = view.selectionManager.selEndChar;
        endLineText = view.getLineTextForRender(nEndLine);
      } else {
        nStartLine = view.selectionManager.selEndLine;
        nEndLine = view.selectionManager.selStartLine;
        nEndChar = view.selectionManager.selStartChar;
        endLineText = view.getLineTextForRender(nEndLine);
      }

      anchorYTop = view.getViewYTopForPopup(nStartLine, 0);
      anchorYBottom = view.getViewYTopForPopup(nEndLine, nEndChar) + view.lineHeight;
      anchorX = view.getViewXForPopup(endLineText, nEndLine, nEndChar);
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

  int getPopupActionAt(float x, float y) {
    if (btnCopyRect.contains(x, y)) return POPUP_ACTION_COPY;
    if (btnCutRect.contains(x, y)) return POPUP_ACTION_CUT;
    if (btnPasteRect.contains(x, y)) return POPUP_ACTION_PASTE;
    if (btnDeleteRect.contains(x, y)) return POPUP_ACTION_DELETE;
    if (btnSelectAllRect.contains(x, y)) return POPUP_ACTION_SELECT_ALL;
    return 0;
  }

  void startPopupRipple(int action, float x, float y) {
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
          view.invalidate();
        });
    popupRippleAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            popupRippleActive = false;
            popupRippleAlpha = 0f;
            popupRippleRect.setEmpty();
            view.invalidate();
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            popupRippleActive = false;
            popupRippleAlpha = 0f;
            popupRippleRect.setEmpty();
            view.invalidate();
          }
        });
    popupRippleAnimator.start();
  }

  void startPopupRippleHold(int action, float x, float y) {
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
    view.invalidate();
  }

  void cancelPopupRipple() {
    if (popupRippleAnimator != null) popupRippleAnimator.cancel();
    popupRippleHoldActive = false;
    popupRippleActive = false;
    popupRippleAlpha = 0f;
    popupRippleRect.setEmpty();
    view.invalidate();
  }

  void showMinimalPopupAtCursor() {
    if (view.selectionManager.hasSelection()) return;
    isMinimalPopup = true;
    showPopupAnimated();
  }

  void showPopupAtSelection() {
    if (!view.selectionManager.hasSelection()) return;
    isMinimalPopup = false;
    showPopupAnimated();
  }

  void hidePopup() {
    hidePopupAnimated();
  }

  boolean isPopupRippleHoldActive() {
    return popupRippleHoldActive;
  }

  boolean isPopupVisible() {
    return showPopup;
  }

  int getPressedAction() {
    return popupPressedAction;
  }

  void setPressedAction(int action) {
    popupPressedAction = action;
  }

  void clearPressedAction() {
    popupPressedAction = 0;
  }

  RectF getPopupRectForAction(int action) {
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

  void performPopupAction(int action) {
    switch (action) {
      case POPUP_ACTION_COPY:
        view.copySelectionToClipboard();
        break;
      case POPUP_ACTION_CUT:
        view.cutSelectionToClipboard();
        break;
      case POPUP_ACTION_PASTE:
        view.pasteFromClipboard();
        break;
      case POPUP_ACTION_DELETE:
        view.deleteSelection();
        break;
      case POPUP_ACTION_SELECT_ALL:
        view.selectAll();
        break;
      default:
        break;
    }
  }

  private void showPopupAnimated() {
    if (!showPopup) {
      showPopup = true;
    }
    startPopupFade(1f);
  }

  private void hidePopupAnimated() {
    popupPressedAction = 0;
    cancelPopupRipple();
    startPopupFade(0f);
  }

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
          view.invalidate();
        });
    popupFadeAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (popupAlpha <= 0f) {
              showPopup = false;
              isMinimalPopup = false;
            }
            view.invalidate();
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            if (popupAlpha <= 0f) {
              showPopup = false;
              isMinimalPopup = false;
            }
            view.invalidate();
          }
        });
    popupFadeAnimator.start();
  }

  String getPopupLabelForAction(int action) {
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
}
