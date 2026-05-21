package com.yn.sodiumeditor.core.guides.bracket;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.List;

/**
 * Manages bracket guide checkpoints for faster state calculation.
 */
public class BracketGuideCheckpoint {
  private final SodiumEditor editor;
  private final BracketGuides bracketGuides;

  private int[] checkpointLines = new int[64];
  private int checkpointCount = 0;
  public final java.util.ArrayList<BracketGuideState> bracketGuideCheckpointStates = new java.util.ArrayList<>();
  public int bracketGuideCheckpointEditVersion = -1;
  public int bracketGuideCheckpointConfigHash = 0;
  public int bracketGuideCheckpointMaxLine = -1;
  public int bracketGuideCheckpointStep = 500;
  public int bracketGuideCheckpointStepFast = 100;

  public BracketGuideCheckpoint(SodiumEditor editor, BracketGuides bracketGuides) {
    this.editor = editor;
    this.bracketGuides = bracketGuides;
  }

  private void ensureCheckpointCapacity(int min) {
    if (checkpointLines.length >= min) return;
    int newCap = Math.max(checkpointLines.length * 2, min);
    checkpointLines = java.util.Arrays.copyOf(checkpointLines, newCap);
  }

  /**
   * Ensures checkpoints are built up to the specified line.
   */
  public void ensureCheckpointsUpTo(
      int endLine, @Nullable java.util.Map<Integer, String> directLines) {
    int v = editor.editOperators.editVersion.get();
    int cfg = bracketGuides.getBracketGuideCacheConfigHash();
    if (v != bracketGuideCheckpointEditVersion || cfg != bracketGuideCheckpointConfigHash) {
      checkpointCount = 0;
      bracketGuideCheckpointStates.clear();
      bracketGuideCheckpointEditVersion = v;
      bracketGuideCheckpointConfigHash = cfg;
      bracketGuideCheckpointMaxLine = -1;
    }
    if (endLine <= bracketGuideCheckpointMaxLine) {
      return;
    }

    BracketGuideState state;
    int startLine = bracketGuideCheckpointMaxLine + 1;
    if (bracketGuideCheckpointStates.isEmpty()) {
      state = new BracketGuideState(editor.highlite.isBlockCommentsEnabled, 0);
      startLine = 0;
    } else {
      state = BracketGuides.copyState(bracketGuideCheckpointStates.get(bracketGuideCheckpointStates.size() - 1));
    }

    if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      long startOffset;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (startLine >= 0 && startLine < editor.fileIO.lineOffsets.length) {
          startOffset = editor.fileIO.lineOffsets[startLine];
        } else {
          startOffset = -1;
        }
      }

      if (startOffset >= 0) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(editor.fileIO.sourceFile)) {
          fis.getChannel().position(startOffset);
          try (java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, editor.fileIO.fileCharset);
               java.io.BufferedReader reader = new java.io.BufferedReader(isr, 65536)) {

            for (int line = startLine; line <= endLine; line++) {
              if (line % bracketGuideCheckpointStep == 0) {
                ensureCheckpointCapacity(checkpointCount + 1);
                checkpointLines[checkpointCount] = line;
                bracketGuideCheckpointStates.add(BracketGuides.copyState(state));
                checkpointCount++;
              }
              String text = reader.readLine();
              if (text == null) break;
              bracketGuides.updateBracketGuideStateForLine(text, line, state);
              bracketGuideCheckpointMaxLine = line;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        return;
      }
    }

    // Fallback if index not ready
    for (int line = startLine; line <= endLine; line++) {
      if (line % bracketGuideCheckpointStep == 0) {
        ensureCheckpointCapacity(checkpointCount + 1);
        checkpointLines[checkpointCount] = line;
        bracketGuideCheckpointStates.add(BracketGuides.copyState(state));
        checkpointCount++;
      }
      String text = bracketGuides.getLineTextForGuideScan(line, directLines, null);
      if (text == null) text = "";
      bracketGuides.updateBracketGuideStateForLine(text, line, state);
      bracketGuideCheckpointMaxLine = line;
    }
  }

  /**
   * Gets checkpoint index for a line.
   * Returns the index of the checkpoint with the largest line number <= the requested line.
   */
  public int getCheckpointIndexForLine(int line) {
    if (checkpointCount == 0) return -1;

    if (line <= 0) {
      return checkpointLines[0] == 0 ? 0 : -1;
    }

    int lo = 0;
    int hi = checkpointCount - 1;
    int best = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int v = checkpointLines[mid];
      if (v <= line) {
        best = mid;
        if (v == line) {
          break;
        }
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return best;
  }

  /**
   * Gets checkpoint state by index.
   */
  public BracketGuideState getCheckpointState(int index) {
    if (index >= 0 && index < bracketGuideCheckpointStates.size()) {
      return bracketGuideCheckpointStates.get(index);
    }
    return null;
  }

  /**
   * Gets checkpoint line by index.
   */
  public int getCheckpointLine(int index) {
    if (index >= 0 && index < checkpointCount) {
      return checkpointLines[index];
    }
    return -1;
  }

  /**
   * Clears all checkpoints.
   */
  public void clear() {
    checkpointCount = 0;
    bracketGuideCheckpointStates.clear();
    bracketGuideCheckpointEditVersion = -1;
    bracketGuideCheckpointConfigHash = 0;
    bracketGuideCheckpointMaxLine = -1;
  }

  public void shiftCheckpoints(int startLine, int delta) {
    if (delta == 0 || checkpointCount == 0) return;
    
    // Simplest: invalidate checkpoints after startLine
    int firstAffected = -1;
    for (int i = 0; i < checkpointCount; i++) {
        if (checkpointLines[i] >= startLine) {
            firstAffected = i;
            break;
        }
    }
    
    if (firstAffected >= 0) {
        // Remove all checkpoints from this point onwards as states are now invalid due to shift/edit
        for (int i = checkpointCount - 1; i >= firstAffected; i--) {
            bracketGuideCheckpointStates.remove(i);
        }
        checkpointCount = firstAffected;
        bracketGuideCheckpointMaxLine = (checkpointCount > 0) ? checkpointLines[checkpointCount - 1] : -1;
    }
  }
}
