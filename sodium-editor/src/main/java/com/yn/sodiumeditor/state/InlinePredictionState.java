package com.yn.sodiumeditor.state;

import android.graphics.RectF;

/**
 * State class for inline prediction functionality.
 * Stores inline prediction state including active suggestion and settings.
 */
public class InlinePredictionState {

    private static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;

    // Active suggestion state
    public String activeSuggestion = null;
    public int activeSuggestionLine = 0;
    public int activeSuggestionCharStart = 0;
    public String activeSuggestionWordFragment = "";
    public boolean activeSuggestionIsPath = false;
    public final RectF activeSuggestionRect = new RectF();

    // Settings
    private boolean autoCompletionEnabled = false;
    private boolean autoPathCompletionEnabled = false;
    private boolean suggestionTextSizeCustom = false;
    private float suggestionTextSizeScale = 1f;

    // Touch state
    public boolean suggestionAcceptedThisTouch = false;

    // Path suggestion cache
    public String lastPathQuery = null;
    public String lastPathSuggestion = null;

    // Debounce state
    public long lastSuggestionUpdateUptime = 0L;
    public boolean suggestionUpdateScheduled = false;

    public InlinePredictionState() {
    }

    public long getSuggestionUpdateDebounceMs() {
        return SUGGESTION_UPDATE_DEBOUNCE_MS;
    }

    public boolean isAutoCompletionEnabled() {
        return autoCompletionEnabled;
    }

    public void setAutoCompletionEnabled(boolean enabled) {
        this.autoCompletionEnabled = enabled;
    }

    public boolean isAutoPathCompletionEnabled() {
        return autoPathCompletionEnabled;
    }

    public void setAutoPathCompletionEnabled(boolean enabled) {
        this.autoPathCompletionEnabled = enabled;
    }

    public boolean isSuggestionTextSizeCustom() {
        return suggestionTextSizeCustom;
    }

    public void setSuggestionTextSizeCustom(boolean custom) {
        this.suggestionTextSizeCustom = custom;
    }

    public float getSuggestionTextSizeScale() {
        return suggestionTextSizeScale;
    }

    public void setSuggestionTextSizeScale(float scale) {
        this.suggestionTextSizeScale = scale;
    }

    public void clearActiveSuggestion() {
        activeSuggestion = null;
        activeSuggestionRect.setEmpty();
        activeSuggestionIsPath = false;
    }

    public boolean hasActiveSuggestion() {
        return activeSuggestion != null;
    }

    public void clearSuggestionAcceptedThisTouch() {
        suggestionAcceptedThisTouch = false;
    }

    public void clearPathCache() {
        lastPathQuery = null;
        lastPathSuggestion = null;
    }

    public void setLastPathQuery(String query) {
        lastPathQuery = query;
    }

    public void setLastPathSuggestion(String suggestion) {
        lastPathSuggestion = suggestion;
    }

    public String getLastPathQuery() {
        return lastPathQuery;
    }

    public String getLastPathSuggestion() {
        return lastPathSuggestion;
    }
}
