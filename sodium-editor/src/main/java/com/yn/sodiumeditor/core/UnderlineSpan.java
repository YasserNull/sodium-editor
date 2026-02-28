package com.yn.sodiumeditor.core;

/**
 * Represents an underline span for URL or path underlining.
 */
public class UnderlineSpan {
    public final int start;
    public final int end;
    public final boolean isPath;

    public UnderlineSpan(int start, int end, boolean isPath) {
        this.start = start;
        this.end = end;
        this.isPath = isPath;
    }
}
