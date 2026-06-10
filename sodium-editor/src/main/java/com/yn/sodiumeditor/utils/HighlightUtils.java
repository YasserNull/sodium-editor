package com.yn.sodiumeditor.utils;

import com.yn.sodiumeditor.renderer.HighlightRender;
import java.util.List;

/** Static utility methods for syntax highlighting logic. */
public class HighlightUtils {

  public static boolean hasOverlap(
      HighlightRender.HighlightSpan span, List<HighlightRender.HighlightSpan> spans) {
    for (HighlightRender.HighlightSpan other : spans) {
      if (span.start < other.end && other.start < span.end) return true;
    }
    return false;
  }

  public static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    return r.startsWith("//")
        || r.startsWith("^//")
        || r.startsWith("^\\s*//")
        || r.startsWith("\\s*//");
  }

  public static boolean isTokenEscaped(String line, int index) {
    if (isEscaped(line, index)) return true;
    int next = index + 1;
    return next < line.length() && isEscaped(line, next);
  }

  public static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  public static int findStringEnd(String line, int start, char delimiter) {
    for (int i = start; i < line.length(); i++) {
      if (line.charAt(i) == delimiter && !isEscaped(line, i)) return i;
    }
    return -1;
  }

  public static int findTripleQuoteEnd(String line, int start) {
    for (int i = start; i + 2 < line.length(); i++) {
      if (line.charAt(i) == '"'
          && line.charAt(i + 1) == '"'
          && line.charAt(i + 2) == '"'
          && !isEscaped(line, i)) return i;
    }
    return -1;
  }

  public static int findBlockCommentEnd(String line, int start) {
    return findTokenEnd(line, start, "*/");
  }

  public static int findTokenEnd(String line, int start, String token) {
    if (line == null || token == null || token.isEmpty()) return -1;
    int max = line.length() - token.length();
    for (int i = Math.max(0, start); i <= max; i++) {
      if (line.regionMatches(i, token, 0, token.length()) && !isTokenEscaped(line, i)) return i;
    }
    return -1;
  }

  public static boolean isTokenStart(String line, int start, String token) {
    if (line == null || token == null || token.isEmpty()) return false;
    if (start < 0 || start + token.length() > line.length()) return false;
    return line.regionMatches(start, token, 0, token.length()) && !isTokenEscaped(line, start);
  }
}
