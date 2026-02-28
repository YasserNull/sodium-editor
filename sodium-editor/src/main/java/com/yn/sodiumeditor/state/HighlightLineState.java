package com.yn.sodiumeditor.state;

/**
 * Represents the highlight state at the start of a line.
 */
public class HighlightLineState {
    public final boolean inBlockComment;
    public final int stringState;

    public HighlightLineState(boolean inBlockComment, int stringState) {
        this.inBlockComment = inBlockComment;
        this.stringState = stringState;
    }
}
