package com.yn.sodiumeditor.core;

/**
 * Bracket guide state class.
 * Tracks the parsing state for bracket guide calculation.
 */
public class BracketGuideState {
  public boolean inBlockComment;
  public int stringState;
  public final java.util.ArrayDeque<BracketGuideToken> stack = new java.util.ArrayDeque<>();

  public BracketGuideState(boolean inBlockComment, int stringState) {
    this.inBlockComment = inBlockComment;
    this.stringState = stringState;
  }
}
