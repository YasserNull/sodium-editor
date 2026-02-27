package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.config.SearchConfig;
import com.yn.sodiumeditor.state.SearchMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Core search engine responsible for search calculations and matching.
 * Handles cache management and pattern matching without direct UI interaction.
 */
public final class SearchEngine {

    private final SearchConfig config;
    private final SearchCallback callback;

    private String searchCacheKey = null;
    private int searchCacheEditVersion = -1;
    private final HashMap<Integer, int[]> searchMatchCache = new HashMap<>();

    public interface SearchCallback {
        int getEditVersionForSearch();
        int getLinesCount();
        int getWindowStartLineForSearch();
        int getWindowSizeForSearch();
        boolean isIndexReadyForSearch();
        boolean getSourceFileForSearchExists();
        void populateDirectLinesForRangeForSearch(int start, int end, HashMap<Integer, String> direct);
        String getLineTextForRenderWithDirectForSearch(int line, HashMap<Integer, String> direct);
        String getLineTextForRender(int line);
    }

    public SearchEngine(SearchConfig config, SearchCallback callback) {
        this.config = config;
        this.callback = callback;
    }

    public void clearCache() {
        searchMatchCache.clear();
        searchCacheEditVersion = -1;
        searchCacheKey = null;
    }

    public int[] getSearchMatchSpansForLine(String line, int globalLine) {
        if (!config.isSearchHighlightEnabled() || !config.isSearchActive() || line == null || line.isEmpty()) {
            return new int[0];
        }

        int version = callback.getEditVersionForSearch();
        String key = config.getCacheKey();
        if (searchCacheEditVersion != version || (searchCacheKey != null && !searchCacheKey.equals(key))) {
            searchMatchCache.clear();
            searchCacheEditVersion = version;
            searchCacheKey = key;
        }

        int[] cached = searchMatchCache.get(globalLine);
        if (cached != null) return cached;

        ArrayList<Integer> tmp = null;
        if (config.isSearchUseRegex() && config.getSearchPattern() != null) {
            Matcher matcher = config.getSearchPattern().matcher(line);
            while (matcher.find()) {
                if (matcher.start() == matcher.end()) continue;
                if (tmp == null) tmp = new ArrayList<>();
                tmp.add(matcher.start());
                tmp.add(matcher.end());
            }
        } else {
            String haystack = config.isSearchCaseSensitive() ? line : line.toLowerCase(Locale.ROOT);
            String needle = config.isSearchCaseSensitive() ? config.getSearchQuery() : config.getSearchQuery().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty()) {
                int idx = haystack.indexOf(needle, 0);
                while (idx >= 0) {
                    if (tmp == null) tmp = new ArrayList<>();
                    tmp.add(idx);
                    tmp.add(idx + needle.length());
                    idx = haystack.indexOf(needle, idx + Math.max(1, needle.length()));
                }
            }
        }

        int[] spans;
        if (tmp == null || tmp.isEmpty()) {
            spans = new int[0];
        } else {
            spans = new int[tmp.size()];
            for (int i = 0; i < tmp.size(); i++) spans[i] = tmp.get(i);
        }
        searchMatchCache.put(globalLine, spans);
        return spans;
    }

    @Nullable
    public SearchMatch findNextSearchMatchFrom(int line, int charIndex) {
        int total = callback.getLinesCount();
        if (total <= 0) return null;

        HashMap<Integer, String> direct = new HashMap<>();

        SearchMatch m = findNextSearchMatchInRange(line, total - 1, charIndex + 1, null, direct);
        if (m != null) return m;
        if (config.isSearchWrap() && line > 0) {
            return findNextSearchMatchInRange(0, line, 0, charIndex, direct);
        }
        return null;
    }

    @Nullable
    public SearchMatch findPrevSearchMatchFrom(int line, int charIndex) {
        int total = callback.getLinesCount();
        if (total <= 0) return null;

        HashMap<Integer, String> direct = new HashMap<>();

        SearchMatch m = findPrevSearchMatchInRange(line, 0, charIndex - 1, null, direct);
        if (m != null) return m;
        if (config.isSearchWrap() && line < total - 1) {
            return findPrevSearchMatchInRange(total - 1, line, Integer.MAX_VALUE, charIndex - 1, direct);
        }
        return null;
    }

    @Nullable
    private SearchMatch findNextSearchMatchInRange(
            int startLine,
            int endLine,
            int startCharExclusive,
            @Nullable Integer maxStartInclusive,
            HashMap<Integer, String> direct) {
        int step = (startLine <= endLine) ? 1 : -1;
        int line = startLine;
        int total = callback.getLinesCount();
        int chunkSize = 200;
        while (true) {
            if (line < 0 || line >= total) break;
            if (line < callback.getWindowStartLineForSearch()
                    || line >= callback.getWindowStartLineForSearch() + callback.getWindowSizeForSearch()) {
                if (callback.isIndexReadyForSearch() && callback.getSourceFileForSearchExists()) {
                    int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
                    int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
                    callback.populateDirectLinesForRangeForSearch(rangeStart, rangeEnd, direct);
                }
            }
            String lineText = callback.getLineTextForRenderWithDirectForSearch(line, direct);
            if (lineText == null) lineText = "";
            int from = (line == startLine) ? Math.max(0, Math.min(startCharExclusive, lineText.length())) : 0;
            Integer limit = (maxStartInclusive != null && line == endLine) ? maxStartInclusive : null;
            SearchMatch m = findMatchForwardInLine(lineText, from, limit);
            if (m != null) {
                m.line = line;
                return m;
            }
            if (line == endLine) break;
            line += step;
        }
        return null;
    }

    @Nullable
    private SearchMatch findPrevSearchMatchInRange(
            int startLine,
            int endLine,
            int startCharExclusive,
            @Nullable Integer minStartInclusive,
            HashMap<Integer, String> direct) {
        int step = (startLine >= endLine) ? -1 : 1;
        int line = startLine;
        int total = callback.getLinesCount();
        int chunkSize = 200;
        while (true) {
            if (line < 0 || line >= total) break;
            if (line < callback.getWindowStartLineForSearch()
                    || line >= callback.getWindowStartLineForSearch() + callback.getWindowSizeForSearch()) {
                if (callback.isIndexReadyForSearch() && callback.getSourceFileForSearchExists()) {
                    int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
                    int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
                    callback.populateDirectLinesForRangeForSearch(rangeStart, rangeEnd, direct);
                }
            }
            String lineText = callback.getLineTextForRenderWithDirectForSearch(line, direct);
            if (lineText == null) lineText = "";
            int from = (line == startLine) ? Math.max(0, Math.min(startCharExclusive, lineText.length())) : lineText.length();
            Integer limit = (minStartInclusive != null && line == endLine) ? minStartInclusive : null;
            SearchMatch m = findMatchBackwardInLine(lineText, from, limit);
            if (m != null) {
                m.line = line;
                return m;
            }
            if (line == endLine) break;
            line += step;
        }
        return null;
    }

    @Nullable
    private SearchMatch findMatchForwardInLine(String line, int fromIndex, @Nullable Integer maxStartInclusive) {
        if (line == null || line.isEmpty()) return null;
        if (config.isSearchUseRegex() && config.getSearchPattern() != null) {
            Matcher m = config.getSearchPattern().matcher(line);
            if (m.find(fromIndex)) {
                if (maxStartInclusive != null && m.start() > maxStartInclusive) return null;
                return new SearchMatch(-1, m.start(), m.end());
            }
            return null;
        }
        String haystack = config.isSearchCaseSensitive() ? line : line.toLowerCase(Locale.ROOT);
        String needle = config.isSearchCaseSensitive() ? config.getSearchQuery() : config.getSearchQuery().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return null;
        int idx = haystack.indexOf(needle, fromIndex);
        if (idx < 0) return null;
        if (maxStartInclusive != null && idx > maxStartInclusive) return null;
        return new SearchMatch(-1, idx, idx + needle.length());
    }

    @Nullable
    private SearchMatch findMatchBackwardInLine(String line, int fromIndex, @Nullable Integer minStartInclusive) {
        if (line == null || line.isEmpty()) return null;
        if (config.isSearchUseRegex() && config.getSearchPattern() != null) {
            Matcher m = config.getSearchPattern().matcher(line);
            SearchMatch last = null;
            while (m.find()) {
                if (m.start() > fromIndex) break;
                if (minStartInclusive != null && m.start() < minStartInclusive) continue;
                last = new SearchMatch(-1, m.start(), m.end());
            }
            return last;
        }
        String haystack = config.isSearchCaseSensitive() ? line : line.toLowerCase(Locale.ROOT);
        String needle = config.isSearchCaseSensitive() ? config.getSearchQuery() : config.getSearchQuery().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return null;
        int idx = haystack.lastIndexOf(needle, Math.min(fromIndex, haystack.length()));
        if (idx < 0) return null;
        if (minStartInclusive != null && idx < minStartInclusive) return null;
        return new SearchMatch(-1, idx, idx + needle.length());
    }

    @Nullable
    public SearchMatch findSearchMatchAtCursor(int line, int ch) {
        if (!config.isSearchActive()) return null;
        String lineText = callback.getLineTextForRender(line);
        if (lineText == null) lineText = "";

        if (config.isSearchUseRegex()) {
            if (config.getSearchPattern() == null) return null;
            Matcher matcher = config.getSearchPattern().matcher(lineText);
            while (matcher.find()) {
                int s = matcher.start();
                int e = matcher.end();
                if (ch >= s && ch < e) {
                    return new SearchMatch(line, s, e);
                }
            }
            return null;
        }

        String needle = config.getSearchQuery() == null ? "" : config.getSearchQuery();
        if (needle.isEmpty()) return null;
        if (!config.isSearchCaseSensitive()) {
            lineText = lineText.toLowerCase(Locale.ROOT);
            needle = needle.toLowerCase(Locale.ROOT);
        }
        int idx = lineText.lastIndexOf(needle, Math.min(ch, lineText.length()));
        if (idx < 0) return null;
        int end = idx + needle.length();
        if (ch >= idx && ch < end) {
            return new SearchMatch(line, idx, end);
        }
        return null;
    }
}
