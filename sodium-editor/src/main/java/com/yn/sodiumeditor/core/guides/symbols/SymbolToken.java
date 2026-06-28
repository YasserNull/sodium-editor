package com.yn.sodiumeditor.core.guides.symbols;

public final class SymbolToken {
  public final int line;
  public final int ch;
  public final int length;
  public final SymbolsMatchSet set;
  public final boolean opening;

  public SymbolToken(int line, int ch, int length, SymbolsMatchSet set, boolean opening) {
    this.line = line;
    this.ch = ch;
    this.length = length;
    this.set = set;
    this.opening = opening;
  }

  public boolean touchesCursor(int cursorLine, int cursorChar) {
    return line == cursorLine && cursorChar >= ch && cursorChar <= ch + length;
  }
}
