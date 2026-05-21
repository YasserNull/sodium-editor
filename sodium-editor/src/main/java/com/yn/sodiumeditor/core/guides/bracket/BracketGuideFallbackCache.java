package com.yn.sodiumeditor.core.guides.bracket;

import android.util.SparseArray;
import java.util.List;

/**
 * Manages the fallback cache to prevent flickering during window changes.
 * Uses SparseArray for efficient sparse storage without range tracking.
 */
public class BracketGuideFallbackCache {
  private final SparseArray<List<BracketGuideToken>> fallbackTokens = new SparseArray<>();
  private final SparseArray<BracketGuideState> fallbackStates = new SparseArray<>();
  private int fallbackCacheEditVersion = -1;

  /**
   * Merges the current main cache with the existing fallback cache.
   * Directly puts main cache entries into SparseArray — no list creation, no loop over ranges.
   */
  public void mergeWithMainCache(
      java.util.ArrayList<List<BracketGuideToken>> mainTokens,
      java.util.ArrayList<BracketGuideState> mainStates,
      int mainStartLine,
      int mainEndLine,
      int mainEditVersion) {

    // Only save if main cache is valid
    if (mainStartLine < 0 || mainEndLine < mainStartLine || mainTokens.size() == 0) {
      return;
    }

    // Put main cache entries directly into SparseArray by line number
    for (int i = 0; i < mainTokens.size(); i++) {
      int line = mainStartLine + i;
      if (i < mainTokens.size()) {
        fallbackTokens.put(line, mainTokens.get(i));
      }
      if (i < mainStates.size()) {
        fallbackStates.put(line, mainStates.get(i));
      }
    }

    fallbackCacheEditVersion = mainEditVersion;
  }

  /**
   * Invalidates the fallback cache.
   */
  public void invalidate() {
    fallbackCacheEditVersion = -1;
    fallbackTokens.clear();
    fallbackStates.clear();
  }

  /**
   * Gets tokens for a line from the fallback cache.
   */
  public List<BracketGuideToken> getTokensForLine(int globalLine) {
    return fallbackTokens.get(globalLine);
  }

  /**
   * Gets state for a line from the fallback cache.
   */
  public BracketGuideState getStateForLine(int globalLine) {
    return fallbackStates.get(globalLine);
  }

  /**
   * Checks if fallback cache contains a line.
   */
  public boolean containsLine(int globalLine) {
    return fallbackTokens.get(globalLine) != null;
  }

  /**
   * Gets the edit version of the fallback cache.
   */
  public int getEditVersion() {
    return fallbackCacheEditVersion;
  }

  public void shiftCache(int startLine, int delta) {
    if (delta == 0 || (fallbackTokens.size() == 0 && fallbackStates.size() == 0)) return;

    SparseArray<List<BracketGuideToken>> shiftedTokens = new SparseArray<>(fallbackTokens.size());
    for (int i = 0; i < fallbackTokens.size(); i++) {
        int line = fallbackTokens.keyAt(i);
        List<BracketGuideToken> val = fallbackTokens.valueAt(i);
        if (line < startLine) {
            shiftedTokens.put(line, val);
        } else {
            if (delta < 0 && line < startLine - delta) continue;
            int newLine = line + delta;
            if (newLine >= 0) shiftedTokens.put(newLine, val);
        }
    }
    fallbackTokens.clear();
    for (int i = 0; i < shiftedTokens.size(); i++) fallbackTokens.put(shiftedTokens.keyAt(i), shiftedTokens.valueAt(i));

    SparseArray<BracketGuideState> shiftedStates = new SparseArray<>(fallbackStates.size());
    for (int i = 0; i < fallbackStates.size(); i++) {
        int line = fallbackStates.keyAt(i);
        BracketGuideState val = fallbackStates.valueAt(i);
        if (line < startLine) {
            shiftedStates.put(line, val);
        } else {
            if (delta < 0 && line < startLine - delta) continue;
            int newLine = line + delta;
            if (newLine >= 0) shiftedStates.put(newLine, val);
        }
    }
    fallbackStates.clear();
    for (int i = 0; i < shiftedStates.size(); i++) fallbackStates.put(shiftedStates.keyAt(i), shiftedStates.valueAt(i));
  }
}
