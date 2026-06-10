package com.yn.sodiumeditor.core.guides.bracket;

/** Bracket guide state class. Tracks the parsing state for bracket guide calculation. */
public class BracketGuideState {
  public boolean inBlockComment;
  public int stringState;
  public final java.util.ArrayDeque<BracketGuideToken> stack = new java.util.ArrayDeque<>();

  public BracketGuideState(boolean inBlockComment, int stringState) {
    this.inBlockComment = inBlockComment;
    this.stringState = stringState;
  }

  /** Create a deep copy of this state. */
  public BracketGuideState cloneState() {
    BracketGuideState copy = new BracketGuideState(inBlockComment, stringState);
    copy.stack.addAll(this.stack);
    return copy;
  }
}
