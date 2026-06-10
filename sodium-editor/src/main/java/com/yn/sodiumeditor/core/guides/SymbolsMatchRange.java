package com.yn.sodiumeditor.core.guides;

public final class SymbolsMatchRange {
  public final int openLine;
  public final int openChar;
  public final int closeLine;
  public final int closeChar;
  public final int openLength;
  public final int closeLength;

  public SymbolsMatchRange(int openLine, int openChar, int closeLine, int closeChar) {
    this(openLine, openChar, closeLine, closeChar, 1, 1);
  }

  public SymbolsMatchRange(
      int openLine, int openChar, int closeLine, int closeChar, int openLength, int closeLength) {
    this.openLine = openLine;
    this.openChar = openChar;
    this.closeLine = closeLine;
    this.closeChar = closeChar;
    this.openLength = Math.max(1, openLength);
    this.closeLength = Math.max(1, closeLength);
  }
}
