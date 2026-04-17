package com.yn.sodiumeditor.core.search;

public final class SearchMatch {
  public int line;
  public int start;
  public int end;

  public SearchMatch(int line, int start, int end) {
    this.line = line;
    this.start = start;
    this.end = end;
  }
}

