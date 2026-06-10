package com.yn.sodiumeditor.core.selection;

/**
 * Represents a text range with start and end positions for selection. Not to be confused with the
 * TextRange class used for text rendering.
 */
public class SelectionTextRange {
  public final int start;
  public final int end;

  public SelectionTextRange(int start, int end) {
    this.start = start;
    this.end = end;
  }
}
