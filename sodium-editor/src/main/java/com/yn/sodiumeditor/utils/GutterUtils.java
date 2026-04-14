package com.yn.sodiumeditor.utils;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.LineNumber;

/**
 * Utility methods for gutter calculations and formatting.
 */
public class GutterUtils {
    private final SodiumEditor editor;
    private final LineNumber lineNumber;

    public GutterUtils(SodiumEditor editor, LineNumber lineNumber) {
        this.editor = editor;
        this.lineNumber = lineNumber;
    }

    public int writeIntToChars(int value, char[] chars) {
        if (chars == null || chars.length == 0) return 0;
        if (value == 0) {
            chars[chars.length - 1] = '0';
            return chars.length - 1;
        }
        int negative = value < 0 ? 1 : 0;
        value = Math.abs(value);
        int len = 0;
        int temp = value;
        while (temp > 0) { len++; temp /= 10; }
        len += negative;
        int start = chars.length - len;
        int idx = chars.length - 1;
        while (value > 0) {
            chars[idx--] = (char) ('0' + (value % 10));
            value /= 10;
        }
        if (negative > 0) chars[idx] = '-';
        return start;
    }

    public float calculateGutterWidth() {
        if (!lineNumber.showLineNumbers) return 0f;
        int total = editor.getLinesCount();
        String maxLineNum = String.valueOf(Math.max(1, total));
        float baseWidth = lineNumber.lineNumbersPaint.measureText(maxLineNum) + (LineNumber.GUTTER_TEXT_PADDING * 2);
        float foldMarkerGutterWidth = 0f;
        if (editor.codeFold.isCodeFoldingEnabled) {
            foldMarkerGutterWidth = editor.codeFold.animation.foldMarkerPaint.measureText("v")
                    + editor.codeFold.animation.foldMarkerSpacing
                    + editor.codeFold.animation.foldMarkerEdgePadding;
        }
        return baseWidth + foldMarkerGutterWidth + lineNumber.gutterSeparatorWidth;
    }
}
