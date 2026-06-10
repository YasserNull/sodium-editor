package com.yn.sodiumeditor.utils;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.selection.SelectionTextRange;
import java.util.ArrayList;
import java.util.List;

/** Handles word finding logic for selection (double-tap, smart word selection). */
public class SelectionWordFinder {

  private final SodiumEditor editor;

  public SelectionWordFinder(SodiumEditor editor) {
    this.editor = editor;
  }

  private boolean isSmartWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  private boolean isSmartSeparator(char c) {
    switch (c) {
      case '.':
      case '٫':
      case ':':
      case '؛':
      case '-':
      case '+':
      case '\\':
      case '|':
      case ',':
      case '،':
        return true;
      default:
        return false;
    }
  }

  private int[] findWordBounds(String line, int pos) {
    int len = line.length();
    int idx = Math.max(0, Math.min(pos, len - 1));
    if (!isSmartWordChar(line.charAt(idx))) return new int[] {idx, idx};
    int start = idx;
    int end = idx;
    while (start > 0 && isSmartWordChar(line.charAt(start - 1))) start--;
    while (end < len - 1 && isSmartWordChar(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  private void addCandidateUnique(ArrayList<SelectionTextRange> out, int start, int end, int len) {
    int s = Math.max(0, Math.min(start, len));
    int e = Math.max(0, Math.min(end, len));
    if (e <= s) return;
    for (int i = 0; i < out.size(); i++) {
      SelectionTextRange r = out.get(i);
      if (r.start == s && r.end == e) return;
    }
    out.add(new SelectionTextRange(s, e));
  }

  public ArrayList<SelectionTextRange> buildSmartWordCandidates(String line, int charIndex) {
    ArrayList<SelectionTextRange> out = new ArrayList<>(6);
    if (line == null || line.isEmpty()) return out;
    int len = line.length();
    int idx = Math.max(0, Math.min(charIndex, len - 1));
    char c = line.charAt(idx);

    int[] base = null;
    if (isSmartWordChar(c)) {
      base = findWordBounds(line, idx);
    } else {
      int left = idx - 1;
      int right = idx + 1;
      while (left >= 0 && Character.isWhitespace(line.charAt(left))) left--;
      while (right < len && Character.isWhitespace(line.charAt(right))) right++;
      if (left >= 0 && isSmartWordChar(line.charAt(left))) {
        base = findWordBounds(line, left);
      } else if (right < len && isSmartWordChar(line.charAt(right))) {
        base = findWordBounds(line, right);
      } else {
        return out;
      }
    }

    addCandidateUnique(out, base[0], base[1], len);

    int curStart = base[0];
    int curEnd = base[1];

    while (curEnd < len && isSmartSeparator(line.charAt(curEnd))) {
      int rightWordStart = curEnd + 1;
      while (rightWordStart < len && Character.isWhitespace(line.charAt(rightWordStart)))
        rightWordStart++;
      int rightWordEnd = rightWordStart;
      while (rightWordEnd < len && isSmartWordChar(line.charAt(rightWordEnd))) rightWordEnd++;
      if (rightWordEnd <= rightWordStart) break;
      curEnd = rightWordEnd;
      addCandidateUnique(out, curStart, curEnd, len);
    }

    while (curStart > 0 && isSmartSeparator(line.charAt(curStart - 1))) {
      int leftWordEnd = curStart - 1;
      int leftWordStart = leftWordEnd - 1;
      while (leftWordStart >= 0 && isSmartWordChar(line.charAt(leftWordStart))) leftWordStart--;
      leftWordStart += 1;
      if (leftWordStart >= leftWordEnd) break;
      curStart = leftWordStart;
      addCandidateUnique(out, curStart, curEnd, len);
    }

    return out;
  }

  public int findSelectionCandidateIndex(
      int line,
      List<SelectionTextRange> candidates,
      int selStartLine,
      int selStartChar,
      int selEndLine,
      int selEndChar) {
    if (candidates == null || candidates.isEmpty()) return -1;
    int sL = selStartLine;
    int sC = selStartChar;
    int eL = selEndLine;
    int eC = selEndChar;
    if (editor.selection.comparePos(sL, sC, eL, eC) > 0) {
      sL = selEndLine;
      sC = selEndChar;
      eL = selStartLine;
      eC = selStartChar;
    }
    if (sL != line || eL != line) return -1;
    for (int i = 0; i < candidates.size(); i++) {
      SelectionTextRange r = candidates.get(i);
      if (r.start == sC && r.end == eC) return i;
    }
    return -1;
  }

  public void addSelectionCandidate(List<SelectionTextRange> out, int start, int end, int lineLen) {
    if (out == null) return;
    int s = Math.max(0, Math.min(start, lineLen));
    int e = Math.max(0, Math.min(end, lineLen));
    if (e <= s) return;
    for (SelectionTextRange r : out) {
      if (r.start == s && r.end == e) return;
    }
    out.add(new SelectionTextRange(s, e));
  }
}
