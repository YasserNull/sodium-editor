package com.yn.sodiumeditor.state;

/**
 * Represents the state at the start of a line for bracket guide parsing.
 * Stores block comment and string state.
 */
public class BracketGuideLineState {
    public final boolean inBlockComment;
    public final int stringState;

    public BracketGuideLineState(boolean inBlockComment, int stringState) {
        this.inBlockComment = inBlockComment;
        this.stringState = stringState;
    }
}
