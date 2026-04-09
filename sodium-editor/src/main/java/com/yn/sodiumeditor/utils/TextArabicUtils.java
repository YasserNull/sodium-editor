package com.yn.sodiumeditor.utils;

/**
 * TextArabicUtils provides utility methods for handling Arabic and RTL text.
 * This includes:
 * - Detecting Arabic script in text
 * - Checking for mixed direction text
 * - Identifying RTL and Latin script blocks
 */
public class TextArabicUtils {

    /**
     * Check if text contains Arabic script.
     */
    public static boolean containsArabicScript(CharSequence text, int start, int end) {
        if (text == null || start >= end) return false;
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(text.length(), end);
        for (int i = safeStart; i < safeEnd; ) {
            int codePoint = Character.codePointAt(text, i);
            i += Character.charCount(codePoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == Character.UnicodeBlock.ARABIC
                    || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                    || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                    || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if text is mixed direction (contains both RTL and LTR scripts).
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
     * Check if block is RTL script.
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
     * Check if block is Latin script.
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
