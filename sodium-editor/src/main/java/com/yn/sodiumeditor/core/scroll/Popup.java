package com.yn.sodiumeditor.core.scroll; 
import com.yn.sodiumeditor.SodiumEditor;
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
import com.yn.sodiumeditor.utils.FunctionLog;
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
  public static final long POPUP_FADE_IN_MS = 450;
  public static final long POPUP_FADE_OUT_MS = 350;
  public static final long POPUP_RIPPLE_DELAY_MS = 500;
  public static final long POPUP_RIPPLE_MS = 250;

  // Popup state
  public boolean showPopup = false;
  public boolean isFadingOut = false;
  public boolean isMinimalPopup = false;
  public boolean isDeleteButtonEnabled = true;
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
  public float fadeTargetAlpha = 0f;
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
  public boolean popupHideAfterRipple = false;
  public final List<Integer> popupLastDrawnActions = new ArrayList<>();

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

  // Components
  public final com.yn.sodiumeditor.renderer.animation.PopupAnimation animation;
  public final com.yn.sodiumeditor.renderer.draw.PopupMenu menu;

  // Reference to parent editor
  private final SodiumEditor editor;

  public Popup(SodiumEditor editor) {
    FunctionLog.f("Popup", "Popup", editor);
    this.editor = editor;
    this.animation = new com.yn.sodiumeditor.renderer.animation.PopupAnimation(editor, this);
    this.menu = new com.yn.sodiumeditor.renderer.draw.PopupMenu(editor, this);
  }

  /**
   * Initialize popup configuration based on display metrics.
   * Should be called when the editor is ready or configuration changes.
   */
  public void applyPopupConfig() {
    FunctionLog.f("Popup", "applyPopupConfig");
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
    FunctionLog.f("Popup", "setPopupBackgroundColor", color);
    popupBackgroundColor = color;
    popupBgPaint.setColor(color);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text color.
   * @param color The color to use for popup button text
   */
  public void setPopupTextColor(int color) {
    FunctionLog.f("Popup", "setPopupTextColor", color);
    popupTextColor = color;
    popupTextPaint.setColor(color);
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text size in scaled pixels.
   * @param sp The text size in scaled pixels
   */
  public void setPopupTextSize(float sp) {
    FunctionLog.f("Popup", "setPopupTextSize", sp);
    popupTextSizeSp = sp;
    popupTextPaint.setTextSize(spToPx(sp));
    if (showPopup) editor.invalidate();
  }

  /**
   * Set popup text size in pixels.
   * @param sizePx The text size in pixels
   */
  public void setPopupTextSizePx(float sizePx) {
    FunctionLog.f("Popup", "setPopupTextSizePx", sizePx);
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
    FunctionLog.f("Popup", "setPopupTextFollowsEditorTypeface", follow);
    popupTextFollowsEditorTypeface = follow;
    if (follow) {
      popupTextPaint.setTypeface(editor.textRender.paint.getTypeface());
    }
    if (showPopup) editor.invalidate();
  }

  /**
   * Set a custom typeface for popup text.
   * @param typeface The typeface to use, or null for default
   */
  public void setPopupTextTypeface(@Nullable android.graphics.Typeface typeface) {
    FunctionLog.f("Popup", "setPopupTextTypeface", typeface);
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
    FunctionLog.f("Popup", "setPopupLabels", copy, cut, paste, delete, selectAll);
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
    FunctionLog.f("Popup", "showPopupAtSelection");
    if (!editor.selection.hasSelection) return;
    isMinimalPopup = false;
    showPopupAnimated();
  }

  /**
   * Show a minimal popup at the cursor position.
   */
  public void showMinimalPopupAtCursor() {
    FunctionLog.f("Popup", "showMinimalPopupAtCursor");
    isMinimalPopup = true;
    showPopupAnimated();
  }

  /**
   * Hide the popup menu.
   */
  public void hidePopup() {
    FunctionLog.f("Popup", "hidePopup");
    hidePopupAnimated();
  }

  /**
   * Show the popup with fade-in animation.
   */
  private void showPopupAnimated() {
    FunctionLog.f("Popup", "showPopupAnimated");
    if (showPopup && !isFadingOut && popupAlpha >= 0.95f && fadeTargetAlpha >= 0.95f) {
      return;
    }
    if (!showPopup || popupAlpha < 0.95f) {
      android.util.Log.d("Popup", "Action: SHOW (current alpha=" + popupAlpha + ")");
      showPopup = true;
      popupAlpha = 0f;
    }
    isFadingOut = false;
    animation.startFade(1f);
  }

  /**
   * Hide the popup with fade-out animation.
   */
  private void hidePopupAnimated() {
    FunctionLog.f("Popup", "hidePopupAnimated");
    if (!showPopup || isFadingOut) return;
    android.util.Log.d("Popup", "Action: HIDE (current alpha=" + popupAlpha + ")");
    isFadingOut = true;
    popupPressedAction = 0;
    popupHideAfterRipple = false;
    animation.cancelRipple();
    animation.startFade(0f);
  }

  public boolean shouldKeepVisible() {
    FunctionLog.f("Popup", "shouldKeepVisible");
    if (popupRippleActive || popupRippleHoldActive) return true;
    return popupHideAfterRipple;
  }

  /**
   * Draw the popup menu on the canvas.
   * @param canvas The canvas to draw on
   */
  public void drawPopup(Canvas canvas) {
    FunctionLog.f("Popup", "drawPopup", canvas);
    menu.drawPopup(canvas);
  }

  /**
   * Check if copy/cut should be hidden for large selections.
   * @return true if copy/cut buttons should be hidden
   */
  public boolean shouldHideCopyCutForSelection() {
    FunctionLog.f("Popup", "shouldHideCopyCutForSelection");
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
    FunctionLog.f("Popup", "getPopupRectForAction", action);
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
    FunctionLog.f("Popup", "getPopupLabelForAction", action);
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
    FunctionLog.f("Popup", "getPopupActionAt", x, y);
    if (btnCopyRect.contains(x, y)) return POPUP_ACTION_COPY;
    if (btnCutRect.contains(x, y)) return POPUP_ACTION_CUT;
    if (btnPasteRect.contains(x, y)) return POPUP_ACTION_PASTE;
    if (btnDeleteRect.contains(x, y)) return POPUP_ACTION_DELETE;
    if (btnSelectAllRect.contains(x, y)) return POPUP_ACTION_SELECT_ALL;
    return 0;
  }

  /**
   * Start ripple animation for a button press.
   * @param action The action being pressed
   * @param x X coordinate of the press
   * @param y Y coordinate of the press
   */
  public void startPopupRipple(int action, float x, float y) {
    FunctionLog.f("Popup", "startPopupRipple", action, x, y);
    animation.startRipple(action, x, y);
  }

  /**
   * Start ripple hold animation for a long press.
   * @param action The action being pressed
   * @param x X coordinate of the press
   * @param y Y coordinate of the press
   */
  public void startPopupRippleHold(int action, float x, float y) {
    FunctionLog.f("Popup", "startPopupRippleHold", action, x, y);
    animation.startRippleHold(action, x, y);
  }

  /**
   * Cancel any active ripple animation.
   */
  public void cancelPopupRipple() {
    FunctionLog.f("Popup", "cancelPopupRipple");
    animation.cancelRipple();
  }

  /**
   * Convert scaled pixels to pixels.
   * @param sp The value in scaled pixels
   * @return The value in pixels
   */
  private float spToPx(float sp) {
    FunctionLog.f("Popup", "spToPx", sp);
    return sp * editor.getResources().getDisplayMetrics().scaledDensity;
  }
}
