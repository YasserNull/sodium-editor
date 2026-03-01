package com.yn.sodiumeditor.state;

/**
 * Represents a fold token (bracket or comment start) in a line.
 */
public class FoldToken {
    public final int index;
    public final char openChar;
    public final boolean isBlockComment;

    public FoldToken(int index, char openChar, boolean isBlockComment) {
        this.index = index;
        this.openChar = openChar;
        this.isBlockComment = isBlockComment;
    }
}
