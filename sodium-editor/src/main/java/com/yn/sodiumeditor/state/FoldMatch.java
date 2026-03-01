package com.yn.sodiumeditor.state;

/**
 * Represents a matching bracket found during fold scanning.
 */
public class FoldMatch {
    public final int endLine;
    public final char closeChar;

    public FoldMatch(int endLine, char closeChar) {
        this.endLine = endLine;
        this.closeChar = closeChar;
    }
}
