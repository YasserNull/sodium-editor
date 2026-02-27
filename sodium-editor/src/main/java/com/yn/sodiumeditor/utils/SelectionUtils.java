package com.yn.sodiumeditor.utils;

import androidx.annotation.Nullable;

/**
 * Utility class for selection operations.
 * Contains helper functions for clamping and validation.
 */
public final class SelectionUtils {

    private SelectionUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Clamps a line number to valid selection bounds.
     * @param line The line number to clamp
     * @param isEof Whether the editor is at EOF
     * @param windowStartLine The start line of the visible window
     * @param windowSize The size of the visible window
     * @return The clamped line number
     */
    public static int clampLineForSelection(int line, boolean isEof, int windowStartLine, int windowSize) {
        if (line < 0) return 0;
        if (isEof) {
            int last = windowStartLine + windowSize - 1;
            if (last < 0) return 0;
            return Math.min(line, last);
        }
        return line;
    }

    /**
     * Checks if a line is selectable (non-empty and accessible).
     * @param lineText The text content of the line
     * @return true if the line is selectable, false otherwise
     */
    public static boolean isLineSelectable(@Nullable String lineText) {
        return lineText != null && lineText.length() > 0;
    }

    /**
     * Compares two positions in the document.
     * @param aL Line of first position
     * @param aC Character of first position
     * @param bL Line of second position
     * @param bC Character of second position
     * @return Negative if a < b, 0 if equal, positive if a > b
     */
    public static int comparePos(int aL, int aC, int bL, int bC) {
        if (aL != bL) return aL - bL;
        return aC - bC;
    }

    /**
     * Normalizes selection coordinates so start is before end.
     * @param sL Start line
     * @param sC Start character
     * @param eL End line
     * @param eC End character
     * @return Array containing [startL, startC, endL, endC] in normalized order
     */
    public static int[] normalizeSelection(int sL, int sC, int eL, int eC) {
        if (comparePos(sL, sC, eL, eC) <= 0) {
            return new int[]{sL, sC, eL, eC};
        }
        return new int[]{eL, eC, sL, sC};
    }
}
