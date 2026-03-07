package com.yn.sodiumeditor.io;

/**
 * Represents a slice of text read from a file by characters.
 */
public final class StreamedCharSlice {
    public final String text;
    public final int length;

    public StreamedCharSlice(String text, int length) {
        this.text = text;
        this.length = length;
    }
}
