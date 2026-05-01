package com.yn.sodiumeditor.core.selection;

import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Represents a text range with start and end positions for selection.
 * Not to be confused with the TextRange class used for text rendering.
 */
public class SelectionTextRange {
  public final int start;
  public final int end;

  public SelectionTextRange(int start, int end) {
    FunctionLog.f("SelectionTextRange", "SelectionTextRange", start, end);
    this.start = start;
    this.end = end;
  }
}
