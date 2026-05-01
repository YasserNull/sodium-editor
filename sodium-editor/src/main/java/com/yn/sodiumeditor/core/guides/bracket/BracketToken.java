package com.yn.sodiumeditor.core.guides.bracket;

import com.yn.sodiumeditor.utils.FunctionLog;

public final class BracketToken {
  public final int line;
  public final int ch;
  public final char bracket;

  public BracketToken(int line, int ch, char bracket) {
    FunctionLog.f("BracketToken", "BracketToken", line, ch, bracket);
    this.line = line;
    this.ch = ch;
    this.bracket = bracket;
  }
}
