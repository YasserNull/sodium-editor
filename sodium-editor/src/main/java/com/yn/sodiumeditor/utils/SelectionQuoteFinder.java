package com.yn.sodiumeditor.utils;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.SelectionTextRange;
import androidx.annotation.Nullable;
import java.util.ArrayList;

/**
 * Handles quote and bracket finding logic for selection.
 */
public class SelectionQuoteFinder {

  private final SodiumEditor editor;

  public SelectionQuoteFinder(SodiumEditor editor) {
    this.editor = editor;
  }

  public boolean isQuoteChar(char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  @Nullable
  public SelectionTextRange findEnclosingQuoteRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<SelectionTextRange> ranges = new ArrayList<>();
    char current = 0;
    int start = -1;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (current == 0) {
        if (isQuoteChar(c) && !editor.highlite.isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !editor.highlite.isEscaped(line, i)) {
          ranges.add(new SelectionTextRange(start, i));
          current = 0;
          start = -1;
        }
      }
    }
    SelectionTextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (SelectionTextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }

  @Nullable
  public SelectionTextRange findEnclosingBracketRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<SelectionTextRange> ranges = new ArrayList<>();
    int[] stackIdx = new int[Math.max(8, len / 4)];
    char[] stackType = new char[stackIdx.length];
    int sp = 0;
    char currentQuote = 0;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (currentQuote != 0) {
        if (c == currentQuote && !editor.highlite.isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !editor.highlite.isEscaped(line, i)) {
        currentQuote = c;
        continue;
      }
      if (c == '(' || c == '[' || c == '{') {
        if (sp >= stackIdx.length) {
          int newSize = stackIdx.length * 2;
          int[] newIdx = new int[newSize];
          char[] newType = new char[newSize];
          System.arraycopy(stackIdx, 0, newIdx, 0, stackIdx.length);
          System.arraycopy(stackType, 0, newType, 0, stackType.length);
          stackIdx = newIdx;
          stackType = newType;
        }
        stackIdx[sp] = i;
        stackType[sp] = c;
        sp++;
        continue;
      }
      if (c == ')' || c == ']' || c == '}') {
        char want = (c == ')') ? '(' : (c == ']') ? '[' : '{';
        if (sp > 0 && stackType[sp - 1] == want) {
          int start = stackIdx[sp - 1];
          sp--;
          ranges.add(new SelectionTextRange(start, i));
        }
      }
    }
    SelectionTextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (SelectionTextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }
}
