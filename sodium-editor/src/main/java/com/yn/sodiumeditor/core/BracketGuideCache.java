package com.yn.sodiumeditor.core;

import java.util.List;

/**
 * Manages the main bracket guide cache with atomic swapping.
 */
public class BracketGuideCache {
  private final BracketGuides editor;

  // Main cache state
  public int bracketGuideCacheStartLine = -1;
  public int bracketGuideCacheEndLine = -1;
  public int bracketGuideCacheEditVersion = -1;
  public int bracketGuideCacheConfigHash = 0;
  public BracketGuideState bracketGuideCacheStateAtStart = null;
  public BracketGuideState bracketGuideCacheStateAtEnd = null;
  public BracketGuideState bracketGuideCacheStateBeforeStart = null;
  public java.util.ArrayList<BracketGuideState> bracketGuideStatesWindow = new java.util.ArrayList<>();
  public java.util.ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow = new java.util.ArrayList<>();

  // Build in progress tracking
  public boolean bracketGuideBuildInProgress = false;
  public int bracketGuidePendingStart = -1;
  public int bracketGuidePendingEnd = -1;
  public int bracketGuidePendingEditVersion = -1;
  public int bracketGuidePendingConfigHash = 0;

  public BracketGuideCache(BracketGuides editor) {
    this.editor = editor;
  }

  /**
   * Invalidates the main cache.
   */
  public void invalidateCache() {
    bracketGuideCacheStartLine = -1;
    bracketGuideCacheEndLine = -1;
    bracketGuideCacheEditVersion = -1;
    bracketGuideCacheConfigHash = 0;
    bracketGuideCacheStateAtStart = null;
    bracketGuideCacheStateAtEnd = null;
    bracketGuideCacheStateBeforeStart = null;
    bracketGuideStatesWindow.clear();
    bracketGuideBuildInProgress = false;
    bracketGuidePendingStart = -1;
    bracketGuidePendingEnd = -1;
    bracketGuidePendingEditVersion = -1;
    bracketGuidePendingConfigHash = 0;
    bracketGuideTokensWindow.clear();
  }

  /**
   * Checks if the main cache is valid.
   */
  public boolean isCacheValid(int startLine, int endLine, int editVersion, int configHash) {
    return startLine == bracketGuideCacheStartLine
        && endLine == bracketGuideCacheEndLine
        && editVersion == bracketGuideCacheEditVersion
        && configHash == bracketGuideCacheConfigHash;
  }

  /**
   * Atomically swaps the cache with new data on the UI thread.
   */
  public void swapCache(
      java.util.ArrayList<List<BracketGuideToken>> newTokens,
      java.util.ArrayList<BracketGuideState> newStates,
      int startLine,
      int endLine,
      int editVersion,
      int configHash,
      BracketGuideState stateAtStart,
      BracketGuideState stateAtEnd,
      BracketGuideState stateBeforeStart,
      BracketGuideFallbackCache fallbackCache) {

    // Save old cache to fallback before swapping (prevents flickering)
    fallbackCache.mergeWithMainCache(
        bracketGuideTokensWindow,
        bracketGuideStatesWindow,
        bracketGuideCacheStartLine,
        bracketGuideCacheEndLine,
        bracketGuideCacheEditVersion);

    // Atomic swap
    bracketGuideTokensWindow = newTokens;
    bracketGuideStatesWindow = newStates;
    bracketGuideCacheStartLine = startLine;
    bracketGuideCacheEndLine = endLine;
    bracketGuideCacheEditVersion = editVersion;
    bracketGuideCacheConfigHash = configHash;
    bracketGuideCacheStateAtStart = stateAtStart;
    bracketGuideCacheStateAtEnd = stateAtEnd;
    bracketGuideCacheStateBeforeStart = stateBeforeStart;
    bracketGuideBuildInProgress = false;
  }

  /**
   * Gets tokens for a line from the main cache.
   */
  public List<BracketGuideToken> getTokensForLine(int globalLine) {
    if (globalLine >= bracketGuideCacheStartLine && globalLine <= bracketGuideCacheEndLine) {
      int idx = globalLine - bracketGuideCacheStartLine;
      if (idx >= 0 && idx < bracketGuideTokensWindow.size()) {
        return bracketGuideTokensWindow.get(idx);
      }
    }
    return null;
  }

  /**
   * Gets state for a line from the main cache.
   */
  public BracketGuideState getStateForLine(int globalLine) {
    if (globalLine >= bracketGuideCacheStartLine && globalLine <= bracketGuideCacheEndLine) {
      int idx = globalLine - bracketGuideCacheStartLine;
      if (idx >= 0 && idx < bracketGuideStatesWindow.size()) {
        return bracketGuideStatesWindow.get(idx);
      }
    }
    return null;
  }
}
