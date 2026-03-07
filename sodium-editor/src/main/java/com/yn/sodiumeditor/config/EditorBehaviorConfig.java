package com.yn.sodiumeditor.config;

import android.view.inputmethod.InputMethodManager;

/**
 * Configuration class for editor auto-behavior settings.
 */
public class EditorBehaviorConfig {

    // Auto-pairing and auto-bracket settings
    public boolean isAutoPairingEnabled = true;
    public boolean isAutoBracketNewlineEnabled = true;
    public boolean isAutoBracketNewlineIndentEnabled = true;
    public boolean isAutoIndentAfterClosingBracketEnabled = true;

    // Indentation settings
    public boolean isIndentationBlocksEnabled = false;

    // String and comment highlighting
    public boolean isMultiLineStringsEnabled = false;
    public boolean isBacktickStringsEnabled = false;
    public boolean isBlockCommentsEnabled = false;
    public boolean isTripleQuoteStringsEnabled = false;

    // Other behavior settings
    public boolean binarySafeRenderingEnabled = false;
    
    // Editor state
    public boolean isDisabled = false;
    public boolean isReadOnly = false;

    public EditorBehaviorConfig() {
    }

    public boolean isAutoPairingEnabled() {
        return isAutoPairingEnabled;
    }

    public void setAutoPairingEnabled(boolean enabled) {
        isAutoPairingEnabled = enabled;
    }

    public boolean isAutoBracketNewlineEnabled() {
        return isAutoBracketNewlineEnabled;
    }

    public void setAutoBracketNewlineEnabled(boolean enabled) {
        isAutoBracketNewlineEnabled = enabled;
    }

    public boolean isAutoBracketNewlineIndentEnabled() {
        return isAutoBracketNewlineIndentEnabled;
    }

    public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
        isAutoBracketNewlineIndentEnabled = enabled;
    }

    public boolean isAutoIndentAfterClosingBracketEnabled() {
        return isAutoIndentAfterClosingBracketEnabled;
    }

    public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
        isAutoIndentAfterClosingBracketEnabled = enabled;
    }

    public boolean isIndentationBlocksEnabled() {
        return isIndentationBlocksEnabled;
    }

    public void setIndentationBlocksEnabled(boolean enabled) {
        isIndentationBlocksEnabled = enabled;
    }

    public boolean isMultiLineStringsEnabled() {
        return isMultiLineStringsEnabled;
    }

    public void setMultiLineStringsEnabled(boolean enabled) {
        isMultiLineStringsEnabled = enabled;
    }

    public boolean isBacktickStringsEnabled() {
        return isBacktickStringsEnabled;
    }

    public void setBacktickStringsEnabled(boolean enabled) {
        isBacktickStringsEnabled = enabled;
    }

    public boolean isBlockCommentsEnabled() {
        return isBlockCommentsEnabled;
    }

    public void setBlockCommentsEnabled(boolean enabled) {
        isBlockCommentsEnabled = enabled;
    }

    public boolean isTripleQuoteStringsEnabled() {
        return isTripleQuoteStringsEnabled;
    }

    public void setTripleQuoteStringsEnabled(boolean enabled) {
        isTripleQuoteStringsEnabled = enabled;
    }

    public boolean isBinarySafeRenderingEnabled() {
        return binarySafeRenderingEnabled;
    }

    public void setBinarySafeRenderingEnabled(boolean enabled) {
        binarySafeRenderingEnabled = enabled;
    }

    /**
     * Sets binary safe rendering enabled and triggers necessary invalidations.
     * @param enabled whether binary safe rendering is enabled
     * @param editor the SodiumEditor instance to invalidate
     */
    public void setBinarySafeRenderingEnabled(boolean enabled, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.binarySafeRenderingEnabled == enabled) return;
        this.binarySafeRenderingEnabled = enabled;
        synchronized (editor.editorState.lineWidthCache) {
            editor.editorState.lineWidthCache.clear();
        }
        editor.editorState.currentMaxWindowLineWidth = 0f;
        editor.editorState.globalMaxLineWidth = 0f;
        editor.scrollManager.maxLineWidthForScroll = 0f;
        editor.scrollManager.maxTextStartXForScroll = 0f;
        editor.scrollManager.maxScrollXForScroll = 0f;
        editor.highlightState.resetEnsureRange();
        editor.bracketGuideRenderer.invalidateCache();
        if (editor.wrapWordState.isWordWrapEnabled) editor.wrapWordBuilder.invalidate(true, true);
        editor.wrapWordBuilder.requestPrefixRebuild(editor);
        editor.viewRender.reloadWindowAroundVisible(false);
        editor.invalidate();
    }

    /**
     * Sets indentation blocks enabled and triggers necessary updates.
     * @param enabled whether indentation blocks are enabled
     * @param editor the SodiumEditor instance to update
     */
    public void setIndentationBlocksEnabled(boolean enabled, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.isIndentationBlocksEnabled == enabled) return;
        this.isIndentationBlocksEnabled = enabled;
        if (!enabled) {
            editor.foldTouchHandler.removeIndentFolds();
        }
        editor.indentGuideEngine.markIntervalsDirty();
        editor.foldState.foldIntervalsDirty = true;
        editor.invalidate();
    }

    /**
     * Sets read-only mode and triggers necessary updates.
     * @param readOnly whether read-only mode is enabled
     * @param editor the SodiumEditor instance to update
     */
    public void setReadOnly(boolean readOnly, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.isReadOnly == readOnly) return;
        this.isReadOnly = readOnly;
        if (readOnly) {
            editor.inlinePredictionState.clearActiveSuggestion();
            editor.selectionState.clearSelectionKeepLineNumberState();
            editor.popupTouchHandler.hidePopup();
            InputMethodManager imm =
                (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
        editor.imeManager.restartInput();
        editor.invalidate();
    }
}
