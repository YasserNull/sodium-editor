package com.yn.sodiumeditor.utils;

/**
 * Finds the range of text an IME correction (autocorrect) intends to replace on a single line.
 *
 * <p>The IME reports the misspelled text it is correcting; the editor must replace exactly that
 * text instead of recomputing the word at the cursor, which can widen the range to a preceding
 * word or to the start of the line.
 */
public final class AutocorrectRangeUtil {

  private AutocorrectRangeUtil() {}

  public static int[] findRangeForText(String line, int cursorChar, String oldText) {
    if (line == null || oldText == null || oldText.isEmpty()) return null;
    int len = line.length();
    if (len == 0 || oldText.length() > len) return null;

    int cursor = Math.max(0, Math.min(cursorChar, len));

    int best = -1;
    int from = 0;
    while (from <= len - oldText.length()) {
      int i = line.indexOf(oldText, from);
      if (i < 0) break;
      if (i + oldText.length() <= cursor) {
        best = i;
        from = i + 1;
      } else {
        break;
      }
    }

    if (best < 0) {
      best = line.indexOf(oldText);
    }
    if (best < 0) return null;
    return new int[] {best, best + oldText.length()};
  }

  /**
   * Returns the char index to compute replaceable word bounds around, or {@code -1} when the
   * cursor sits on whitespace (including a trailing space). In that case there is no word under
   * the cursor to replace and the caller must insert instead of clobbering the previous word.
   */
  public static int replaceableWordPos(String line, int cursorChar) {
    if (line == null || line.isEmpty()) return -1;
    int pos = Math.max(0, Math.min(cursorChar, line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    if (pos < line.length() && Character.isWhitespace(line.charAt(pos))) return -1;
    return pos;
  }
}
