package com.yn.sodiumeditor.utils;

/**
 * Utility class for text operations.
 * Provides methods for text direction detection and script analysis.
 */
public final class TextUtils {

    private TextUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if the text contains mixed direction scripts (RTL and LTR).
     *
     * @param text  the text to check
     * @param start the start index
     * @param end   the end index
     * @return true if the text contains mixed direction scripts
     */
    public static boolean isMixedDirectionText(CharSequence text, int start, int end) {
        if (text == null || start >= end) return false;
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(text.length(), end);
        boolean hasRtl = false;
        boolean hasLtr = false;
        for (int i = safeStart; i < safeEnd; ) {
            int codePoint = Character.codePointAt(text, i);
            i += Character.charCount(codePoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == null) continue;
            if (isRtlScriptBlock(block)) {
                hasRtl = true;
            } else if (isLatinScriptBlock(block)) {
                hasLtr = true;
            }
            if (hasRtl && hasLtr) return true;
        }
        return false;
    }

    /**
     * Checks if the Unicode block is a RTL script.
     *
     * @param block the Unicode block to check
     * @return true if the block is a RTL script
     */
    public static boolean isRtlScriptBlock(Character.UnicodeBlock block) {
        return block == Character.UnicodeBlock.ARABIC
                || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS
                || block == Character.UnicodeBlock.HEBREW;
    }

    /**
     * Checks if the Unicode block is a Latin script.
     *
     * @param block the Unicode block to check
     * @return true if the block is a Latin script
     */
    public static boolean isLatinScriptBlock(Character.UnicodeBlock block) {
        return block == Character.UnicodeBlock.BASIC_LATIN
                || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.LATIN_EXTENDED_B
                || block == Character.UnicodeBlock.LATIN_EXTENDED_C
                || block == Character.UnicodeBlock.LATIN_EXTENDED_D
                || block == Character.UnicodeBlock.LATIN_EXTENDED_E
                || block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL;
    }
}
