package com.yn.sodiumeditor.state;

import android.graphics.RectF;
import androidx.annotation.Nullable;

/**
 * State class for bracket match functionality.
 * Stores bracket match state including cache fields and styling.
 */
public class BracketMatchState {

    private static final float DEFAULT_STROKE_WIDTH = 2f;

    private boolean enabled = false;
    private float strokeWidth = DEFAULT_STROKE_WIDTH;
    private float baseStrokeWidth = DEFAULT_STROKE_WIDTH;
    private float baseTextSizePx = 0f;

    public final RectF rect = new RectF();

    @Nullable public BracketMatch cached = null;
    private int cachedCursorLine = -1;
    private int cachedCursorChar = -1;
    private int cachedEditVersion = -1;

    public BracketMatchState() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) clearCache();
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    public float getBaseStrokeWidth() {
        return baseStrokeWidth;
    }

    public void setBaseStrokeWidth(float baseStrokeWidth) {
        this.baseStrokeWidth = baseStrokeWidth;
    }

    public float getBaseTextSizePx() {
        return baseTextSizePx;
    }

    public void setBaseTextSizePx(float baseTextSizePx) {
        this.baseTextSizePx = baseTextSizePx;
    }

    public RectF getRect() {
        return rect;
    }

    @Nullable
    public BracketMatch getCached() {
        return cached;
    }

    public void setCached(@Nullable BracketMatch cached) {
        this.cached = cached;
    }

    public int getCachedCursorLine() {
        return cachedCursorLine;
    }

    public void setCachedCursorLine(int cachedCursorLine) {
        this.cachedCursorLine = cachedCursorLine;
    }

    public int getCachedCursorChar() {
        return cachedCursorChar;
    }

    public void setCachedCursorChar(int cachedCursorChar) {
        this.cachedCursorChar = cachedCursorChar;
    }

    public int getCachedEditVersion() {
        return cachedEditVersion;
    }

    public void setCachedEditVersion(int cachedEditVersion) {
        this.cachedEditVersion = cachedEditVersion;
    }

    public void clearCache() {
        cached = null;
        cachedCursorLine = -1;
        cachedCursorChar = -1;
        cachedEditVersion = -1;
    }

    public boolean isCacheValid(int cursorLine, int cursorChar, int editVersion) {
        return cached != null
                && cachedCursorLine == cursorLine
                && cachedCursorChar == cursorChar
                && cachedEditVersion == editVersion;
    }

    public void updateCache(BracketMatch match, int cursorLine, int cursorChar, int editVersion) {
        if (match != null) {
            cached = match;
            cachedCursorLine = cursorLine;
            cachedCursorChar = cursorChar;
            cachedEditVersion = editVersion;
        } else {
            clearCache();
        }
    }
}
