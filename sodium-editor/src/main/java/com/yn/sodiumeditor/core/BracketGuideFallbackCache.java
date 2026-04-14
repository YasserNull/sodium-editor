package com.yn.sodiumeditor.core;

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
}
