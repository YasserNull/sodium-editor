package com.yn.sodiumeditor.core.guides.bracket;

import java.util.List;

/** Manages the main bracket guide cache with atomic swapping. */
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
  public java.util.ArrayList<BracketGuideState> bracketGuideStatesWindow =
      new java.util.ArrayList<>();
  public java.util.ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow =
      new java.util.ArrayList<>();

  // Build in progress tracking
  public boolean bracketGuideBuildInProgress = false;
  public int bracketGuidePendingStart = -1;
  public int bracketGuidePendingEnd = -1;
  public int bracketGuidePendingEditVersion = -1;
  public int bracketGuidePendingConfigHash = 0;

  public BracketGuideCache(BracketGuides editor) {
    this.editor = editor;
  }

  /** Invalidates the main cache. */
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

  /** Checks if the main cache is valid. */
  public boolean isCacheValid(int startLine, int endLine, int editVersion, int configHash) {
    return startLine == bracketGuideCacheStartLine
        && endLine == bracketGuideCacheEndLine
        && editVersion == bracketGuideCacheEditVersion
        && configHash == bracketGuideCacheConfigHash;
  }

  /** Atomically swaps the cache with new data on the UI thread. */
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

    if (editor.shouldSuppressBracketGuidesForSelectAll()) {
      bracketGuideBuildInProgress = false;
      return;
    }

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
   * Partially swaps the cache with data for a subset of lines. Merges new partial data into
   * existing cache without losing previously-built lines.
   */
  public void swapCachePartial(
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

    if (editor.shouldSuppressBracketGuidesForSelectAll()) {
      bracketGuideBuildInProgress = false;
      return;
    }

    // If no existing cache, treat as full swap
    if (bracketGuideCacheStartLine < 0 || bracketGuideCacheEndLine < bracketGuideCacheStartLine) {
      swapCache(
          newTokens,
          newStates,
          startLine,
          endLine,
          editVersion,
          configHash,
          stateAtStart,
          stateAtEnd,
          stateBeforeStart,
          fallbackCache);
      return;
    }

    // Merge new partial data into existing cache
    int mergedStart = Math.min(bracketGuideCacheStartLine, startLine);
    int mergedEnd = Math.max(bracketGuideCacheEndLine, endLine);
    int mergedSize = mergedEnd - mergedStart + 1;

    java.util.ArrayList<List<BracketGuideToken>> mergedTokens =
        new java.util.ArrayList<>(mergedSize);
    java.util.ArrayList<BracketGuideState> mergedStates = new java.util.ArrayList<>(mergedSize);

    for (int i = 0; i < mergedSize; i++) {
      int line = mergedStart + i;
      List<BracketGuideToken> token = null;
      BracketGuideState state = null;

      // Try new partial data first
      if (line >= startLine && line <= endLine) {
        int newIdx = line - startLine;
        if (newIdx >= 0 && newIdx < newTokens.size()) {
          token = newTokens.get(newIdx);
        }
        if (newIdx >= 0 && newIdx < newStates.size()) {
          state = newStates.get(newIdx);
        }
      }

      // Fall back to existing cache
      if (token == null && line >= bracketGuideCacheStartLine && line <= bracketGuideCacheEndLine) {
        int oldIdx = line - bracketGuideCacheStartLine;
        if (oldIdx >= 0 && oldIdx < bracketGuideTokensWindow.size()) {
          token = bracketGuideTokensWindow.get(oldIdx);
        }
      }
      if (state == null && line >= bracketGuideCacheStartLine && line <= bracketGuideCacheEndLine) {
        int oldIdx = line - bracketGuideCacheStartLine;
        if (oldIdx >= 0 && oldIdx < bracketGuideStatesWindow.size()) {
          state = bracketGuideStatesWindow.get(oldIdx);
        }
      }

      mergedTokens.add(token);
      mergedStates.add(state);
    }

    // Save old cache to fallback
    fallbackCache.mergeWithMainCache(
        bracketGuideTokensWindow,
        bracketGuideStatesWindow,
        bracketGuideCacheStartLine,
        bracketGuideCacheEndLine,
        bracketGuideCacheEditVersion);

    // Swap
    bracketGuideTokensWindow = mergedTokens;
    bracketGuideStatesWindow = mergedStates;
    bracketGuideCacheStartLine = mergedStart;
    bracketGuideCacheEndLine = mergedEnd;
    bracketGuideCacheEditVersion = editVersion;
    bracketGuideCacheConfigHash = configHash;
    bracketGuideCacheStateAtStart = stateAtStart;
    bracketGuideCacheStateAtEnd = stateAtEnd;
    bracketGuideCacheStateBeforeStart = stateBeforeStart;
    // Keep buildInProgress = true since more data is coming
  }

  /** Gets tokens for a line from the main cache. */
  public List<BracketGuideToken> getTokensForLine(int globalLine) {
    if (globalLine >= bracketGuideCacheStartLine && globalLine <= bracketGuideCacheEndLine) {
      int idx = globalLine - bracketGuideCacheStartLine;
      if (idx >= 0 && idx < bracketGuideTokensWindow.size()) {
        return bracketGuideTokensWindow.get(idx);
      }
    }
    return null;
  }

  /** Gets state for a line from the main cache. */
  public BracketGuideState getStateForLine(int globalLine) {
    if (globalLine >= bracketGuideCacheStartLine && globalLine <= bracketGuideCacheEndLine) {
      int idx = globalLine - bracketGuideCacheStartLine;
      if (idx >= 0 && idx < bracketGuideStatesWindow.size()) {
        return bracketGuideStatesWindow.get(idx);
      }
    }
    return null;
  }

  public void shiftCache(int startLine, int delta) {
    if (delta == 0 || bracketGuideCacheStartLine < 0) return;

    if (startLine > bracketGuideCacheEndLine) {
      // Shift occurs after our window
      return;
    }

    if (startLine <= bracketGuideCacheStartLine) {
      // Shift affects our window start
      if (delta < 0 && startLine - delta > bracketGuideCacheStartLine) {
        // Deletion overlaps or eats part of our window
        invalidateCache();
      } else {
        bracketGuideCacheStartLine += delta;
        bracketGuideCacheEndLine += delta;
      }
    } else {
      // Shift occurs inside our window
      invalidateCache(); // Too complex to shift internal ArrayLists accurately without propagate,
                         // just invalidate
    }
  }
}
