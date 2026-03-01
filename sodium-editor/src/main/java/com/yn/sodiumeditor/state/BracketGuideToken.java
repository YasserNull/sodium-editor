package com.yn.sodiumeditor.state;

/**
 * Represents a bracket guide token.
 * Stores column and x-coordinate for a bracket position.
 */
public class BracketGuideToken {
    public final int column;
    public final float x;

    public BracketGuideToken(int column, float x) {
        this.column = column;
        this.x = x;
    }
}
