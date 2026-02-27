package com.yn.sodiumeditor.config;

public final class CharAnimationConfig {
    private boolean isCharAnimationEnabled = false;
    private int charAnimationDurationMs = 200;
    private int charAnimFastDurationMs = 60;
    private long charAnimFastThresholdMs = 80;

    public boolean isEnabled() {
        return isCharAnimationEnabled;
    }

    public void setEnabled(boolean enabled) {
        isCharAnimationEnabled = enabled;
    }

    public int getDurationMs() {
        return charAnimationDurationMs;
    }

    public void setDurationMs(int durationMs) {
        if (durationMs > 0) {
            this.charAnimationDurationMs = durationMs;
        }
    }

    public int getFastDurationMs() {
        return charAnimFastDurationMs;
    }

    public void setFastDurationMs(int durationMs) {
        this.charAnimFastDurationMs = durationMs;
    }

    public long getFastThresholdMs() {
        return charAnimFastThresholdMs;
    }

    public void setFastThresholdMs(long thresholdMs) {
        this.charAnimFastThresholdMs = thresholdMs;
    }
}
