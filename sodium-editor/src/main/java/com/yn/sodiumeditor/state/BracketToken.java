package com.yn.sodiumeditor.state;

/**
 * Represents a bracket token during matching.
 * Stores the position and bracket character.
 */
public class BracketToken {
    public final int line;
    public final int ch;
    public final char bracket;

    public BracketToken(int line, int ch, char bracket) {
        this.line = line;
        this.ch = ch;
        this.bracket = bracket;
    }
}
