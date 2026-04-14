package com.yn.sodiumeditor.input;

/**
 * Holds a snapshot of text surrounding the cursor to be provided to the IME.
 */
public class ImeContext {
    public final int startLine;
    public final int startChar;
    public final String text;

    public ImeContext(int startLine, int startChar, String text) {
        this.startLine = startLine;
        this.startChar = startChar;
        this.text = (text == null) ? "" : text;
    }
}
