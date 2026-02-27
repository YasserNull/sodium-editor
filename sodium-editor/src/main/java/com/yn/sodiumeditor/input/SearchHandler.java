package com.yn.sodiumeditor.input;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.config.SearchConfig;
import com.yn.sodiumeditor.core.SearchEngine;
import com.yn.sodiumeditor.state.SearchMatch;

/**
 * Handles user interaction with search functionality.
 * Manages cursor movement and selection based on search results.
 */
public final class SearchHandler {

    private final SearchConfig config;
    private final SearchEngine engine;
    private final SearchInteractionCallback callback;

    public interface SearchInteractionCallback {
        int getCursorLine();
        int getCursorChar();
        void ensureLineInWindowForSearch(int line, boolean center);
        void setCursorPosition(int line, int ch);
        void setSelectionInternal(int startLine, int startCh, int endLine, int endCh);
        void setCursorPositionNoClear(int line, int ch);
        boolean hasSelection();
        int comparePos(int line1, int ch1, int line2, int ch2);
        void invalidate();
    }

    public SearchHandler(SearchConfig config, SearchEngine engine, SearchInteractionCallback callback) {
        this.config = config;
        this.engine = engine;
        this.callback = callback;
    }

    public boolean goToNextSearchMatch() {
        return goToSearchMatch(true);
    }

    public boolean goToPrevSearchMatch() {
        return goToSearchMatch(false);
    }

    public boolean selectNextSearchMatch() {
        return selectSearchMatch(true);
    }

    public boolean selectPrevSearchMatch() {
        return selectSearchMatch(false);
    }

    public boolean selectNextSearchMatchInclusive() {
        return selectSearchMatchInclusive(true);
    }

    public boolean selectPrevSearchMatchInclusive() {
        return selectSearchMatchInclusive(false);
    }

    public boolean selectSearchMatchAtCursorOrNext() {
        SearchMatch atCursor = engine.findSearchMatchAtCursor(callback.getCursorLine(), callback.getCursorChar());
        if (atCursor != null) {
            callback.ensureLineInWindowForSearch(atCursor.line, true);
            callback.setSelectionInternal(atCursor.line, atCursor.start, atCursor.line, atCursor.end);
            callback.setCursorPositionNoClear(atCursor.line, atCursor.end);
            return true;
        }
        return selectSearchMatchInclusive(true);
    }

    private boolean goToSearchMatch(boolean forward) {
        if (!config.isSearchActive()) return false;

        int startLine = Math.max(0, callback.getCursorLine());
        int startChar = Math.max(0, callback.getCursorChar());

        SearchMatch match = forward
                ? engine.findNextSearchMatchFrom(startLine, startChar)
                : engine.findPrevSearchMatchFrom(startLine, startChar);
        if (match == null) return false;

        callback.ensureLineInWindowForSearch(match.line, true);
        callback.setCursorPosition(match.line, match.start);
        return true;
    }

    private boolean selectSearchMatch(boolean forward) {
        if (!config.isSearchActive()) return false;

        int startLine = Math.max(0, callback.getCursorLine());
        int startChar = Math.max(0, callback.getCursorChar());

        SearchMatch match = forward
                ? engine.findNextSearchMatchFrom(startLine, startChar)
                : engine.findPrevSearchMatchFrom(startLine, startChar);
        if (match == null) return false;

        callback.ensureLineInWindowForSearch(match.line, true);
        callback.setSelectionInternal(match.line, match.start, match.line, match.end);
        callback.setCursorPositionNoClear(match.line, match.end);
        return true;
    }

    private boolean selectSearchMatchInclusive(boolean forward) {
        if (!config.isSearchActive()) return false;

        int startLine = Math.max(0, callback.getCursorLine());
        int startChar = Math.max(0, callback.getCursorChar());
        if (forward) {
            startChar = Math.max(-1, startChar - 1);
        } else {
            startChar = startChar + 1;
        }

        SearchMatch match = forward
                ? engine.findNextSearchMatchFrom(startLine, startChar)
                : engine.findPrevSearchMatchFrom(startLine, startChar);
        if (match == null) return false;

        if (callback.comparePos(match.line, match.start, startLine, startChar) > 0
                || callback.comparePos(match.line, match.end, startLine, startChar) >= 0) {
            callback.ensureLineInWindowForSearch(match.line, true);
            callback.setSelectionInternal(match.line, match.start, match.line, match.end);
            callback.setCursorPositionNoClear(match.line, match.end);
            return true;
        }
        return false;
    }

    public void setSearchQuery(@Nullable String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
        String safe = (query == null) ? "" : query;
        if (safe.equals(config.getSearchQuery())
                && config.isSearchUseRegex() == useRegex
                && config.isSearchCaseSensitive() == caseSensitive
                && config.isSearchWrap() == wrapAround) {
            return;
        }
        config.setSearchQuery(safe);
        config.setSearchUseRegex(useRegex);
        config.setSearchCaseSensitive(caseSensitive);
        config.setSearchWrap(wrapAround);
        config.setSearchPattern(null);
        if (config.isSearchUseRegex() && !config.getSearchQuery().isEmpty()) {
            int flags = java.util.regex.Pattern.MULTILINE;
            if (!config.isSearchCaseSensitive()) flags |= (java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
            try {
                config.setSearchPattern(java.util.regex.Pattern.compile(config.getSearchQuery(), flags));
            } catch (Exception e) {
                config.setSearchPattern(null);
            }
        }
        engine.clearCache();
        callback.invalidate();
    }

    public void setSearchHighlightEnabled(boolean enabled) {
        config.setSearchHighlightEnabled(enabled);
        callback.invalidate();
    }

    public void setSearchHighlightColor(int color) {
        config.setSearchHighlightColor(color);
        callback.invalidate();
    }

    public void setHighlightCurrentSearchMatchEnabled(boolean enabled) {
        config.setHighlightCurrentSearchMatch(enabled);
        callback.invalidate();
    }

    public void setCurrentSearchMatchColor(int color) {
        if (config.getCurrentSearchMatchColor() == color) return;
        config.setCurrentSearchMatchColor(color);
        if (config.isHighlightCurrentSearchMatch()) {
            callback.invalidate();
        }
    }
}
