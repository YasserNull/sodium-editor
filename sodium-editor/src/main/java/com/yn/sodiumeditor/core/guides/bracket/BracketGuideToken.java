package com.yn.sodiumeditor.core.guides.bracket;

import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Bracket guide token class.
 * Stores column index instead of X position to remain stable during zoom.
 */
public class BracketGuideToken {
  public final int column;
  public final char bracket;
  // Note: x is calculated at draw time based on current text size/zoom

  public BracketGuideToken(int column, float x, char bracket) {
    FunctionLog.f("BracketGuideToken", "BracketGuideToken", column, x, bracket);
    this.column = column;
    this.bracket = bracket;
  }

  /**
   * Calculates X position at draw time based on current zoom level.
   */
  public float getX(BracketGuides guides, String line, int globalLine) {
    FunctionLog.f("BracketGuideToken", "getX", guides, line, globalLine);
    return guides.getGuideX(line, column, globalLine);
  }
}
