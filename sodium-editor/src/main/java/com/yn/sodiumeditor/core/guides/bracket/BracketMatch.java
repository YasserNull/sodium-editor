package com.yn.sodiumeditor.core.guides.bracket;

public final class BracketMatch {
  public final int openLine;
  public final int openChar;
  public final int closeLine;
  public final int closeChar;

  public BracketMatch(int openLine, int openChar, int closeLine, int closeChar) {
    this.openLine = openLine;
    this.openChar = openChar;
    this.closeLine = closeLine;
    this.closeChar = closeChar;
  }
}

