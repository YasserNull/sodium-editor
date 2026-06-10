package com.yn.sodiumeditor.core.guides.bracket;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.List;

/** Handles asynchronous bracket guide cache building with visible line priority. */
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
   * Builds the bracket guide cache asynchronously. Builds sequentially (correct state propagation)
   * with a partial UI update after visible lines are ready, then completes the rest.
   */
  public void buildCacheAsync(
      int startLine,
      int endLine,
      int visibleStart,
      int visibleEnd,
      int v,
      int cfg,
      long startTime,
      @Nullable java.util.Map<Integer, String> directLines) {
    if (editor.selection.isSelectAllActive
        || editor.selection.isEntireFileSelected
        || editor.selection.state.isSelectAllActive
        || editor.selection.state.isEntireFileSelected) {
      mainCache.bracketGuideBuildInProgress = false;
      return;
    }
    BracketGuideState state = new BracketGuideState(editor.highlight.isBlockCommentsEnabled, 0);
    BracketGuideState stateBeforeStart = BracketGuides.copyState(state);
    BracketGuideState stateAtStart = null;
    java.util.ArrayList<List<BracketGuideToken>> tokensWindow = new java.util.ArrayList<>();
    java.util.ArrayList<BracketGuideState> statesWindow = new java.util.ArrayList<>();
    int totalLines = endLine - startLine + 1;
    tokensWindow.ensureCapacity(totalLines);
    statesWindow.ensureCapacity(totalLines);
    int stickyColumn = -1;
    boolean stickyActive = false;

    try {
      // Use smaller checkpoint step during fast scroll for quicker initial build
      int originalCheckpointStep = checkpoint.bracketGuideCheckpointStep;
      boolean fastScroll =
          editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
      if (fastScroll && bracketGuides.useFastBuildDuringFastScroll) {
        checkpoint.bracketGuideCheckpointStep =
            Math.min(checkpoint.bracketGuideCheckpointStepFast, endLine - startLine + 1);
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

      if (currentLine < startLine
          && editor.fileIO.isIndexReady
          && editor.fileIO.sourceFile != null
          && editor.fileIO.sourceFile.exists()
          && (directLines == null || directLines.isEmpty())) {
        try (java.io.RandomAccessFile raf =
            new java.io.RandomAccessFile(editor.fileIO.sourceFile, "r")) {
          while (currentLine < startLine) {
            String text = bracketGuides.readIndexedLinePrefix(currentLine, raf);
            if (text == null) break;
            bracketGuides.updateBracketGuideStateForLine(text, currentLine, state);
            currentLine++;
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

      // Pre-allocate arrays
      for (int i = 0; i < totalLines; i++) {
        tokensWindow.add(null);
        statesWindow.add(null);
      }

      // SEQUENTIAL BUILD: correct state propagation
      int clampedVisibleEnd = Math.min(visibleEnd, endLine);
      boolean partialUpdatePosted = false;

      for (int line = startLine; line <= endLine; line++) {
        // Check if edit version changed during build - abort if so
        if (editor.editOperators.editVersion.get() != v
            || bracketGuides.getBracketGuideCacheConfigHash() != cfg) {
          checkpoint.bracketGuideCheckpointStep = originalCheckpointStep;
          mainCache.bracketGuideBuildInProgress = false;
          return;
        }

        String text = bracketGuides.getLineTextForGuideScan(line, directLines, null);
        if (text == null) text = "";
        List<BracketGuideToken> tokens =
            bracketGuides.updateBracketGuideStateForLine(text, line, state);
        int arrayIdx = line - startLine;
        tokensWindow.set(arrayIdx, tokens);
        // Skip state copy during async build — states are never read from cache
        // (getBracketGuideStateForLine is unused in the render path).
        // This eliminates N BracketGuideState allocations per build.
        statesWindow.set(arrayIdx, null);
        if (line == startLine) stateAtStart = BracketGuides.copyState(state);

        // After visible range is built, post a partial update immediately
        if (!partialUpdatePosted && line >= clampedVisibleEnd) {
          partialUpdatePosted = true;
          final int partialEnd = line;
          final java.util.ArrayList<List<BracketGuideToken>> partialTokens =
              new java.util.ArrayList<>(tokensWindow.subList(0, partialEnd - startLine + 1));
          // States are null — no copy needed
          final java.util.ArrayList<BracketGuideState> partialStates =
              new java.util.ArrayList<>(partialTokens.size());
          for (int s = 0; s < partialTokens.size(); s++) partialStates.add(null);
          final BracketGuideState partialStateAtStart =
              (stateAtStart != null) ? BracketGuides.copyState(stateAtStart) : null;
          final BracketGuideState partialStateBeforeStart =
              BracketGuides.copyState(stateBeforeStart);
          final BracketGuideState partialState = BracketGuides.copyState(state);

          editor.post(
              () -> {
                mainCache.swapCachePartial(
                    partialTokens,
                    partialStates,
                    startLine,
                    partialEnd,
                    v,
                    cfg,
                    partialStateAtStart,
                    partialState,
                    partialStateBeforeStart,
                    fallbackCache);
                editor.invalidate();
              });
        }
      }

      // Restore original checkpoint step
      checkpoint.bracketGuideCheckpointStep = originalCheckpointStep;
    } catch (Exception e) {
      // Ignore exception
    }

    // Final full cache swap on UI thread
    // States are null — no copy needed
    BracketGuideState finalStateAtStart =
        (stateAtStart != null)
            ? BracketGuides.copyState(stateAtStart)
            : BracketGuides.copyState(state);
    BracketGuideState finalState = BracketGuides.copyState(state);
    BracketGuideState finalStateBeforeStart = BracketGuides.copyState(stateBeforeStart);

    // Build null states list to match tokens size
    java.util.ArrayList<BracketGuideState> nullStates =
        new java.util.ArrayList<>(tokensWindow.size());
    for (int s = 0; s < tokensWindow.size(); s++) nullStates.add(null);

    editor.post(
        () -> {
          mainCache.swapCache(
              tokensWindow,
              nullStates,
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
