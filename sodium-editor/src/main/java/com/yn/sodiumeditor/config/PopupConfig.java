package com.yn.sodiumeditor.config;

import android.graphics.Typeface;
import android.text.TextUtils;

/**
 * Configuration class for popup menu.
 * Stores popup menu settings such as dimensions, colors, and labels.
 */
public class PopupConfig {

    // Action constants
    public static final int POPUP_ACTION_COPY = 1;
    public static final int POPUP_ACTION_CUT = 2;
    public static final int POPUP_ACTION_PASTE = 3;
    public static final int POPUP_ACTION_DELETE = 4;
    public static final int POPUP_ACTION_SELECT_ALL = 5;

    // Dimensions (in dp/sp)
    public float popupPaddingDp = 5f;
    public float popupCornerDp = 60f;
    public float btnSpacingDp = 5f;
    public float btnHeightDp = 30f;
    public float btnWidthDp = 55f;
    public float popupLabelPaddingDp = 5f;
    public float popupTextSizeSp = 12f;

    // Colors
    public int popupTextColor = 0xFFFFFFFF;
    public int popupBackgroundColor = 0xFF424242;

    // Behavior
    public boolean popupFitToLabel = true;
    public boolean popupTextFollowsEditorTypeface = true;

    // Labels
    public String popupLabelCopy = "Copy";
    public String popupLabelCut = "Cut";
    public String popupLabelPaste = "Paste";
    public String popupLabelDelete = "Delete";
    public String popupLabelSelectAll = "Select All";

    // Animation timings
    public static final long POPUP_FADE_IN_MS = 140;
    public static final long POPUP_FADE_OUT_MS = 110;

    public PopupConfig() {
    }

    public void setPopupLabels(String copy, String cut, String paste, String deleteLabel, String selectAll) {
        popupLabelCopy = copy;
        popupLabelCut = cut;
        popupLabelPaste = paste;
        popupLabelDelete = deleteLabel;
        popupLabelSelectAll = selectAll;
    }

    public String getLabelForAction(int action) {
        switch (action) {
            case POPUP_ACTION_COPY: return popupLabelCopy;
            case POPUP_ACTION_CUT: return popupLabelCut;
            case POPUP_ACTION_PASTE: return popupLabelPaste;
            case POPUP_ACTION_DELETE: return popupLabelDelete;
            case POPUP_ACTION_SELECT_ALL: return popupLabelSelectAll;
            default: return "";
        }
    }

    public boolean isActionEnabled(int action, boolean isReadOnly, boolean hasSelection, boolean shouldHideCopyCut) {
        if (isReadOnly) {
            return action == POPUP_ACTION_COPY || action == POPUP_ACTION_SELECT_ALL;
        }
        if (shouldHideCopyCut && (action == POPUP_ACTION_COPY || action == POPUP_ACTION_CUT)) {
            return false;
        }
        return true;
    }
}
