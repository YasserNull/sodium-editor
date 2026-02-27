package com.yn.sodiumeditor.utils;

import androidx.annotation.Nullable;

/**
 * Utility class for finding bracket pairs.
 */
public final class BracketFinder {

    public enum BracketPairType {
        NONE,
        CURLY,
        ROUND,
        SQUARE
    }

    private BracketFinder() {
        // Utility class, prevent instantiation
    }

    /**
     * Checks if the character at the given position is between a bracket pair.
     * @param line The line text
     * @param charIndex The character index to check
     * @return The type of bracket pair, or NONE if not between brackets
     */
    public static BracketPairType getBracketPairAt(@Nullable String line, int charIndex) {
        if (line == null) return BracketPairType.NONE;
        if (charIndex <= 0 || charIndex >= line.length()) return BracketPairType.NONE;

        char left = line.charAt(charIndex - 1);
        char right = line.charAt(charIndex);
        
        if (left == '{' && right == '}') return BracketPairType.CURLY;
        if (left == '(' && right == ')') return BracketPairType.ROUND;
        if (left == '[' && right == ']') return BracketPairType.SQUARE;
        
        return BracketPairType.NONE;
    }

    /**
     * Checks if a character is an opening bracket.
     */
    public static boolean isOpeningBracket(char c) {
        return c == '(' || c == '{' || c == '[';
    }

    /**
     * Checks if a character is a closing bracket.
     */
    public static boolean isClosingBracket(char c) {
        return c == ')' || c == '}' || c == ']';
    }

    /**
     * Gets the matching closing bracket for an opening bracket.
     */
    public static char getMatchingClosingBracket(char open) {
        switch (open) {
            case '(': return ')';
            case '{': return '}';
            case '[': return ']';
            default: return '\0';
        }
    }

    /**
     * Gets the matching opening bracket for a closing bracket.
     */
    public static char getMatchingOpeningBracket(char close) {
        switch (close) {
            case ')': return '(';
            case '}': return '{';
            case ']': return '[';
            default: return '\0';
        }
    }
}
