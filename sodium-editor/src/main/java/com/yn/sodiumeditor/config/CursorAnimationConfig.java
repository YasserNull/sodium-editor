package com.yn.sodiumeditor.config;

public final class CursorAnimationConfig {
    private boolean isCursorVisible = true;
    private boolean isCursorAnimationEnabled = false;
    private float cursorAnimNormalTauMs = 80f;
    private float cursorAnimFastTauMs = 35f;
    private long cursorAnimFastThresholdMs = 85;

    public boolean isCursorVisible() {
        return isCursorVisible;
    }

    public void setCursorVisible(boolean visible) {
        isCursorVisible = visible;
    }

    public boolean isCursorAnimationEnabled() {
        return isCursorAnimationEnabled;
    }

    public void setCursorAnimationEnabled(boolean enabled) {
        isCursorAnimationEnabled = enabled;
    }

    public float getNormalTauMs() {
        return cursorAnimNormalTauMs;
    }

    public void setNormalTauMs(float tauMs) {
        this.cursorAnimNormalTauMs = tauMs;
    }

    public float getFastTauMs() {
        return cursorAnimFastTauMs;
    }

    public void setFastTauMs(float tauMs) {
        this.cursorAnimFastTauMs = tauMs;
    }

    public long getFastThresholdMs() {
        return cursorAnimFastThresholdMs;
    }

    public void setFastThresholdMs(long thresholdMs) {
        this.cursorAnimFastThresholdMs = thresholdMs;
    }
}
