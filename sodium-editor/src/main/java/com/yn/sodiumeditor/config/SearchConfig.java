package com.yn.sodiumeditor.config;

import java.util.regex.Pattern;

/**
 * Configuration class for search functionality.
 * Manages search settings including colors, flags, and search pattern.
 */
public final class SearchConfig {
    private String searchQuery = "";
    private boolean searchUseRegex = false;
    private boolean searchCaseSensitive = false;
    private boolean searchWrap = true;
    private boolean searchHighlightEnabled = true;
    private int searchHighlightColor = 0x66FFD54F;
    private Pattern searchPattern = null;
    private boolean highlightCurrentSearchMatch = false;
    private int currentSearchMatchColor = 0x9933B5E5;

    public SearchConfig() {}

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public boolean isSearchUseRegex() {
        return searchUseRegex;
    }

    public void setSearchUseRegex(boolean searchUseRegex) {
        this.searchUseRegex = searchUseRegex;
    }

    public boolean isSearchCaseSensitive() {
        return searchCaseSensitive;
    }

    public void setSearchCaseSensitive(boolean searchCaseSensitive) {
        this.searchCaseSensitive = searchCaseSensitive;
    }

    public boolean isSearchWrap() {
        return searchWrap;
    }

    public void setSearchWrap(boolean searchWrap) {
        this.searchWrap = searchWrap;
    }

    public boolean isSearchHighlightEnabled() {
        return searchHighlightEnabled;
    }

    public void setSearchHighlightEnabled(boolean searchHighlightEnabled) {
        this.searchHighlightEnabled = searchHighlightEnabled;
    }

    public int getSearchHighlightColor() {
        return searchHighlightColor;
    }

    public void setSearchHighlightColor(int searchHighlightColor) {
        this.searchHighlightColor = searchHighlightColor;
    }

    public Pattern getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(Pattern searchPattern) {
        this.searchPattern = searchPattern;
    }

    public boolean isHighlightCurrentSearchMatch() {
        return highlightCurrentSearchMatch;
    }

    public void setHighlightCurrentSearchMatch(boolean highlightCurrentSearchMatch) {
        this.highlightCurrentSearchMatch = highlightCurrentSearchMatch;
    }

    public int getCurrentSearchMatchColor() {
        return currentSearchMatchColor;
    }

    public void setCurrentSearchMatchColor(int currentSearchMatchColor) {
        this.currentSearchMatchColor = currentSearchMatchColor;
    }

    public boolean isSearchActive() {
        if (searchQuery == null || searchQuery.isEmpty()) return false;
        if (searchUseRegex) return searchPattern != null;
        return true;
    }

    public String getCacheKey() {
        return searchQuery
                + "|"
                + (searchUseRegex ? "r" : "t")
                + "|"
                + (searchCaseSensitive ? "c" : "i");
    }
}
