package com.yn.sodiumeditor.state;

import android.graphics.RectF;
import androidx.annotation.Nullable;

/**
 * Represents a bracket match.
 * Stores the positions of matching opening and closing brackets.
 */
public class BracketMatch {
    public final int openLine;
    public final int openChar;
    public final int closeLine;
    public final int closeChar;

    public BracketMatch(int openLine, int openChar, int closeLine, int closeChar) {
        this.openLine = openLine;
        this.openChar = openChar;
        this.closeLine = closeLine;
        this.closeChar = closeChar;
    }
}
