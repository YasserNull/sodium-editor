package com.yn.sodiumeditor.state;

/**
 * Represents the state at the start of a line for bracket matching.
 * Stores block comment and string state.
 */
public class BracketMatchLineState {
    public final boolean inBlockComment;
    public final int stringState;

    public BracketMatchLineState(boolean inBlockComment, int stringState) {
        this.inBlockComment = inBlockComment;
        this.stringState = stringState;
    }
}
