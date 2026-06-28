package com.yn.sodiumeditor.core.guides.symbols;

import java.util.ArrayDeque;
import java.util.List;

public final class RegexSymbolsMatcher {
  private RegexSymbolsMatcher() {}

  public static SymbolsMatchResult findMatchInLines(
      List<String> lines, int cursorLine, int cursorChar, List<SymbolsMatchSet> sets) {
    if (lines == null || sets == null || sets.isEmpty()) return null;
    if (cursorLine < 0 || cursorLine >= lines.size()) return null;

    ArrayDeque<SymbolToken> stack = new ArrayDeque<>();
    for (int line = 0; line < lines.size(); line++) {
      String text = lines.get(line);
      if (text == null) text = "";
      for (int i = 0; i < text.length(); ) {
        SymbolToken token = findTokenAt(text, line, i, sets, stack);
        if (token == null) {
          i++;
          continue;
        }

        SymbolToken open = null;
        SymbolToken close = null;
        if (token.opening) {
          stack.push(token);
        } else if (!stack.isEmpty() && stack.peek().set == token.set) {
          open = stack.pop();
          close = token;
        }

        if (open != null
            && (open.touchesCursor(cursorLine, cursorChar)
                || close.touchesCursor(cursorLine, cursorChar))) {
          return new SymbolsMatchResult(
              open.line, open.ch, close.line, close.ch, open.length, close.length);
        }
        i += token.length;
      }
    }
    return null;
  }

  public static SymbolToken findTokenAt(
      String text,
      int line,
      int index,
      List<SymbolsMatchSet> sets,
      ArrayDeque<SymbolToken> stack) {
    SymbolToken best = null;
    for (SymbolsMatchSet set : sets) {
      int startLength = set.startLength(text, index);
      int endLength = set.endLength(text, index);
      if (set.samePattern && startLength > 0) {
        boolean opening = stack.isEmpty() || stack.peek().set != set;
        best = longer(best, new SymbolToken(line, index, startLength, set, opening));
        continue;
      }
      if (endLength > 0) {
        best = longer(best, new SymbolToken(line, index, endLength, set, false));
      }
      if (startLength > 0) {
        best = longer(best, new SymbolToken(line, index, startLength, set, true));
      }
    }
    return best;
  }

  private static SymbolToken longer(SymbolToken current, SymbolToken candidate) {
    if (current == null) return candidate;
    return candidate.length > current.length ? candidate : current;
  }
}
