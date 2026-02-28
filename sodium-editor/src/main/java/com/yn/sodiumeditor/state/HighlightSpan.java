package com.yn.sodiumeditor.state;

import android.graphics.Paint;

/**
 * Represents a highlighted span of text.
 */
public class HighlightSpan {
    public final int start;
    public final int end;
    public final Paint paint;

    public HighlightSpan(int start, int end, Paint paint) {
        this.start = start;
        this.end = end;
        this.paint = paint;
    }
}
