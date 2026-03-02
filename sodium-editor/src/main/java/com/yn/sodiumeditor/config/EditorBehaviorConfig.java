package com.yn.sodiumeditor.config;

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
}
