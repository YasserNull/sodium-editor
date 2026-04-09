package com.yn.sodiumeditor.core;

import java.util.List;

/**
 * Manages the fallback cache to prevent flickering during window changes.
 */
public class BracketGuideFallbackCache {
  private int fallbackCacheStartLine = -1;
  private int fallbackCacheEndLine = -1;
  private int fallbackCacheEditVersion = -1;
  private final java.util.ArrayList<List<BracketGuideToken>> fallbackTokens = new java.util.ArrayList<>();
  private final java.util.ArrayList<BracketGuideState> fallbackStates = new java.util.ArrayList<>();

  /**
   * Merges the current main cache with the existing fallback cache.
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

    int oldFallbackStart = fallbackCacheStartLine;
    int oldFallbackEnd = fallbackCacheEndLine;

    // Calculate union of ranges
    int newFallbackStart = (oldFallbackStart < 0) ? mainStartLine : Math.min(oldFallbackStart, mainStartLine);
    int newFallbackEnd = (oldFallbackEnd < 0) ? mainEndLine : Math.max(oldFallbackEnd, mainEndLine);

    // Create merged token lists
    java.util.ArrayList<List<BracketGuideToken>> mergedTokens = new java.util.ArrayList<>();
    java.util.ArrayList<BracketGuideState> mergedStates = new java.util.ArrayList<>();

    for (int line = newFallbackStart; line <= newFallbackEnd; line++) {
      List<BracketGuideToken> token = null;
      BracketGuideState state = null;

      // Try main cache first
      if (line >= mainStartLine && line <= mainEndLine) {
        int mainIdx = line - mainStartLine;
        if (mainIdx >= 0 && mainIdx < mainTokens.size()) {
          token = mainTokens.get(mainIdx);
        }
        if (mainIdx >= 0 && mainIdx < mainStates.size()) {
          state = mainStates.get(mainIdx);
        }
      }

      // Then old fallback
      if ((token == null || state == null) && oldFallbackStart >= 0 && line >= oldFallbackStart && line <= oldFallbackEnd) {
        int fallbackIdx = line - oldFallbackStart;
        if (fallbackIdx >= 0 && fallbackIdx < fallbackTokens.size()) {
          token = fallbackTokens.get(fallbackIdx);
        }
        if (fallbackIdx >= 0 && fallbackIdx < fallbackStates.size()) {
          state = fallbackStates.get(fallbackIdx);
        }
      }

      mergedTokens.add(token);
      mergedStates.add(state);
    }

    fallbackCacheStartLine = newFallbackStart;
    fallbackCacheEndLine = newFallbackEnd;
    fallbackCacheEditVersion = mainEditVersion;
    fallbackTokens.clear();
    fallbackTokens.addAll(mergedTokens);
    fallbackStates.clear();
    fallbackStates.addAll(mergedStates);
  }

  /**
   * Invalidates the fallback cache.
   */
  public void invalidate() {
    fallbackCacheStartLine = -1;
    fallbackCacheEndLine = -1;
    fallbackCacheEditVersion = -1;
    fallbackTokens.clear();
    fallbackStates.clear();
  }

  /**
   * Gets tokens for a line from the fallback cache.
   */
  public List<BracketGuideToken> getTokensForLine(int globalLine) {
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackTokens.size()) {
        return fallbackTokens.get(idx);
      }
    }
    return null;
  }

  /**
   * Gets state for a line from the fallback cache.
   */
  public BracketGuideState getStateForLine(int globalLine) {
    if (globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine) {
      int idx = globalLine - fallbackCacheStartLine;
      if (idx >= 0 && idx < fallbackStates.size()) {
        return fallbackStates.get(idx);
      }
    }
    return null;
  }

  /**
   * Checks if fallback cache contains a line.
   */
  public boolean containsLine(int globalLine) {
    return globalLine >= fallbackCacheStartLine && globalLine <= fallbackCacheEndLine;
  }

  /**
   * Gets the start line of the fallback cache.
   */
  public int getStartLine() {
    return fallbackCacheStartLine;
  }

  /**
   * Gets the end line of the fallback cache.
   */
  public int getEndLine() {
    return fallbackCacheEndLine;
  }
}
