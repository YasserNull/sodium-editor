package com.yn.sodiumeditor.core.guides.symbols;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SymbolsMatchSet {
  public final String regexStart;
  public final String regexEnd;
  private final Pattern startPattern;
  private final Pattern endPattern;
  public final boolean samePattern;

  public SymbolsMatchSet(String regexStart, String regexEnd) {
    if (regexStart == null || regexStart.isEmpty()) {
      throw new IllegalArgumentException("regexStart must not be empty");
    }
    if (regexEnd == null || regexEnd.isEmpty()) {
      throw new IllegalArgumentException("regexEnd must not be empty");
    }
    this.regexStart = regexStart;
    this.regexEnd = regexEnd;
    this.startPattern = Pattern.compile(regexStart);
    this.endPattern = Pattern.compile(regexEnd);
    this.samePattern = regexStart.equals(regexEnd);
  }

  public int startLength(String text, int index) {
    if (isJavaBlockCommentStart() && index > 0 && text.charAt(index - 1) == '/') {
      return 0;
    }
    return matchLength(startPattern, text, index);
  }

  private boolean isJavaBlockCommentStart() {
    return regexStart.equals("/\\*") || regexStart.equals("/[*]") || regexStart.equals("/\\Q*\\E");
  }

  public int endLength(String text, int index) {
    return matchLength(endPattern, text, index);
  }

  private static int matchLength(Pattern pattern, String text, int index) {
    Matcher matcher = pattern.matcher(text);
    matcher.region(index, text.length());
    if (!matcher.lookingAt()) return 0;
    int end = matcher.end();
    return end > index ? end - index : 0;
  }
}
