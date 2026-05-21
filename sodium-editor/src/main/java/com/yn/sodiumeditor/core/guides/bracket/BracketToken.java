package com.yn.sodiumeditor.core.guides.bracket;

public final class BracketToken {
  public final int line;
  public final int ch;
  public final char bracket;

  public BracketToken(int line, int ch, char bracket) {
    this.line = line;
    this.ch = ch;
    this.bracket = bracket;
  }
}
