package com.yn.sodiumeditor.core;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles asynchronous bracket guide cache building with visible line priority.
 */
public class BracketGuideAsyncBuilder {
  private final SodiumEditor editor;
  private final BracketGuides bracketGuides;
  private final BracketGuideCache mainCache;
  private final BracketGuideFallbackCache fallbackCache;
  private final BracketGuideCheckpoint checkpoint;

  public BracketGuideAsyncBuilder(
      SodiumEditor editor,
      BracketGuides bracketGuides,
      BracketGuideCache mainCache,
      BracketGuideFallbackCache fallbackCache,
      BracketGuideCheckpoint checkpoint) {
    this.editor = editor;
    this.bracketGuides = bracketGuides;
    this.mainCache = mainCache;
    this.fallbackCache = fallbackCache;
    this.checkpoint = checkpoint;
  }

  /**
   * Builds line order array with visible lines first for prioritized rendering.
   */
  private int[] buildPrioritizedLineOrder(int startLine, int endLine, int visibleStart, int visibleEnd) {
    int totalLines = endLine - startLine + 1;
    int[] buildOrder = new int[totalLines];
    int idx = 0;

    // First: Add visible lines (prioritized)
    int visibleStartClamped = Math.max(startLine, Math.min(visibleStart, endLine));
    int visibleEndClamped = Math.max(startLine, Math.min(visibleEnd, endLine));
    for (int line = visibleStartClamped; line <= visibleEndClamped && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }

    // Second: Add lines before visible range (top to bottom)
    for (int line = startLine; line < visibleStartClamped && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }

    // Third: Add lines after visible range (top to bottom)
    for (int line = visibleEndClamped + 1; line <= endLine && idx < totalLines; line++) {
      buildOrder[idx++] = line;
    }

    return buildOrder;
  }

  /**
   * Builds the bracket guide cache asynchronously.
   */
  public void buildCacheAsync(
      int startLine, int endLine, int visibleStart, int visibleEnd, int v, int cfg, long startTime, @Nullable java.util.Map<Integer, String> directLines) {
    BracketGuideState state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
    BracketGuideState stateBeforeStart = BracketGuides.copyState(state);
    BracketGuideState stateAtStart = null;
    java.util.ArrayList<List<BracketGuideToken>> tokensWindow = new java.util.ArrayList<>();
    java.util.ArrayList<BracketGuideState> statesWindow = new java.util.ArrayList<>();
    tokensWindow.ensureCapacity(endLine - startLine + 1);
    statesWindow.ensureCapacity(endLine - startLine + 1);
    int stickyColumn = -1;
    boolean stickyActive = false;

    try {
      // Use smaller checkpoint step during fast scroll for quicker initial build
      int originalCheckpointStep = checkpoint.bracketGuideCheckpointStep;
      boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
      if (fastScroll && bracketGuides.useFastBuildDuringFastScroll) {
        checkpoint.bracketGuideCheckpointStep = Math.min(checkpoint.bracketGuideCheckpointStepFast, endLine - startLine + 1);
      }

      checkpoint.ensureCheckpointsUpTo(endLine, directLines);

      int checkpointIdx = checkpoint.getCheckpointIndexForLine(startLine);
      int currentLine;
      if (checkpointIdx >= 0) {
        state = BracketGuides.copyState(checkpoint.getCheckpointState(checkpointIdx));
        currentLine = checkpoint.getCheckpointLine(checkpointIdx);
      } else {
        currentLine = 0;
      }

      // Read from checkpoint to startLine using buffered stream if possible
      if (currentLine < startLine && editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists() && (directLines == null || directLines.isEmpty())) {
        long offset;
        synchronized (editor.fileIO.lineOffsetsLock) {
          offset = editor.fileIO.lineOffsets[currentLine];
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile)) {
          fis.getChannel().position(offset);
          try (java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
               java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {
            while (currentLine < startLine) {
              String text = reader.readLine();
              if (text == null) break;
              bracketGuides.updateBracketGuideStateForLine(text, currentLine, state);
              currentLine++;
            }
          }
        }
      } else {
        while (currentLine < startLine) {
          String text = bracketGuides.getLineTextForGuideScan(currentLine, directLines, null);
          if (text == null) text = "";
          bracketGuides.updateBracketGuideStateForLine(text, currentLine, state);
          currentLine++;
        }
      }

      stateBeforeStart = BracketGuides.copyState(state);
      if (!stateBeforeStart.stack.isEmpty()) {
        BracketGuideToken top = stateBeforeStart.stack.peek();
        if (top != null && top.bracket == '{') {
          stickyColumn = top.column;
          stickyActive = true;
        }
      }

      // PRIORITY BUILD: First build visible lines for faster perceived rendering
      int[] buildOrder = buildPrioritizedLineOrder(startLine, endLine, visibleStart, visibleEnd);

      // Pre-allocate arrays
      for (int i = 0; i <= endLine - startLine; i++) {
        tokensWindow.add(null);
        statesWindow.add(null);
      }

      // Build lines in prioritized order
      for (int buildIdx = 0; buildIdx < buildOrder.length; buildIdx++) {
        int line = buildOrder[buildIdx];

        // Check if edit version changed during build - abort if so
        if (editor.editOperators.editVersion.get() != v || bracketGuides.getBracketGuideCacheConfigHash() != cfg) {
          checkpoint.bracketGuideCheckpointStep = originalCheckpointStep;
          mainCache.bracketGuideBuildInProgress = false;
          return;
        }

        String text = bracketGuides.getLineTextForGuideScan(line, directLines, null);
        if (text == null) text = "";
        List<BracketGuideToken> tokens = bracketGuides.updateBracketGuideStateForLine(text, line, state);
        int arrayIdx = line - startLine;
        tokensWindow.set(arrayIdx, tokens);
        statesWindow.set(arrayIdx, BracketGuides.copyState(state));
        if (line == startLine) stateAtStart = BracketGuides.copyState(state);
      }

      // Restore original checkpoint step
      checkpoint.bracketGuideCheckpointStep = originalCheckpointStep;
    } catch (Exception e) {
      // Ignore exception
    }

    BracketGuideState finalStateAtStart = (stateAtStart != null) ? stateAtStart : BracketGuides.copyState(state);
    BracketGuideState finalState = BracketGuides.copyState(state);
    BracketGuideState finalStateBeforeStart = stateBeforeStart;

    // Double buffering: swap caches atomically on UI thread
    editor.post(() -> {
      mainCache.swapCache(
          tokensWindow,
          statesWindow,
          startLine,
          endLine,
          v,
          cfg,
          finalStateAtStart,
          finalState,
          finalStateBeforeStart,
          fallbackCache);

      editor.invalidate();
    });
  }
}
