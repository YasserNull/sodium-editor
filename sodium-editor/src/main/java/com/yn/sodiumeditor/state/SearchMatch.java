package com.yn.sodiumeditor.state;

/**
 * Represents a search match result.
 * Contains the line number and character positions for a match.
 */
public final class SearchMatch {
    public int line;
    public int start;
    public int end;

    public SearchMatch(int line, int start, int end) {
        this.line = line;
        this.start = start;
        this.end = end;
    }
}
