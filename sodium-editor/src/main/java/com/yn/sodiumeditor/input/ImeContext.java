package com.yn.sodiumeditor.input;

import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Holds a snapshot of text surrounding the cursor to be provided to the IME.
 */
public class ImeContext {
    public final int startLine;
    public final int startChar;
    public final String text;

    public ImeContext(int startLine, int startChar, String text) {
        FunctionLog.f("ImeContext", "ImeContext", startLine, startChar, text);
        this.startLine = startLine;
        this.startChar = startChar;
        this.text = (text == null) ? "" : text;
    }
}
