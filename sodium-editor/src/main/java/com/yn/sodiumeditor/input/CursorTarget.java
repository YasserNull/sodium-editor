package com.yn.sodiumeditor.input;

/**
 * Represents a specific line and character position in the document for IME operations.
 */
public class CursorTarget {
    public final int line;
    public final int ch;

    public CursorTarget(int line, int ch) {
        this.line = line;
        this.ch = ch;
    }
}
