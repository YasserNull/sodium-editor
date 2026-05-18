package com.yn.sodiumeditor.core.features; 
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import java.util.HashMap;
/**
 * Manages the click-after-end-to-add-line functionality for the SodiumEditor.
 * Allows users to tap after the end of the last line to add a new line.
 */
public class ClickAfterEndToAddLine {

  private final SodiumEditor editor;

  // Click after end state
  public boolean isClickAfterEndToAddLineEnabled = false;

  public ClickAfterEndToAddLine(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables the click-after-end-to-add-line feature.
   */
  public void setClickAfterEndToAddLineEnabled(boolean enabled) {
    this.isClickAfterEndToAddLineEnabled = enabled;
  }

  /**
   * Checks if the click is after the end of the document.
   */
  public boolean isClickAfterEnd(int visibleIndex, int totalVisible) {
    return totalVisible > 0 && visibleIndex >= totalVisible;
  }

  /**
   * Handles the click after end action.
   * Returns true if the click was handled, false otherwise.
   */
  public boolean handleClickAfterEnd(int visibleIndex, int totalVisible) {
    if (!isClickAfterEndToAddLineEnabled) {
      return false;
    }

    if (!isClickAfterEnd(visibleIndex, totalVisible)) {
      return false;
    }

    int lastLineIndex = getLastVisibleContentLine(totalVisible);

    // Only add a new line if the user taps exactly on the first empty line after
    // the text
    if (visibleIndex == totalVisible) {
      moveCursorToEndOfLastLine(lastLineIndex);
      editor.editOperators.insertTextAtCursor("\n");
    } else {
      // If tapped further down, just move cursor to end of text without adding
      // lines
      moveCursorToEndOfLastLine(lastLineIndex);
    }

    return true;
  }

  /**
   * Handles the click after end action without adding a line.
   * Just moves the cursor to the end of the last line.
   */
  public void handleDefaultAfterEnd() {
    int totalVisible =
        editor.wordWrap.isWordWrapEnabled
            ? editor.wordWrap.getTotalVisualLineCount()
            : editor.codeFold.getVisibleLineCount();
    moveCursorToEndOfLastLine(getLastVisibleContentLine(totalVisible));
  }

  private int getLastVisibleContentLine(int totalVisible) {
    int lastVisibleIndex = Math.max(0, totalVisible - 1);
    while (lastVisibleIndex > 0) {
      int line = mapVisibleIndexToLine(lastVisibleIndex);
      if (!getLineTextForAfterEnd(line).isEmpty() || isCollapsedFoldStart(line)) {
        return line;
      }
      lastVisibleIndex--;
    }
    return mapVisibleIndexToLine(lastVisibleIndex);
  }

  private int mapVisibleIndexToLine(int visibleIndex) {
    if (editor.wordWrap.isWordWrapEnabled) {
      return editor.wordWrap.getVisualPositionForIndex(visibleIndex).line;
    }
    return editor.codeFold.mapVisibleIndexToGlobal(visibleIndex);
  }

  private boolean isCollapsedFoldStart(int line) {
    CodeFold.FoldRange range =
        editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getFoldRangeAtStart(line) : null;
    return range != null && range.collapsed;
  }

  private String getLineTextForAfterEnd(int line) {
    String text = editor.windowRender.getLineTextForRender(line);
    if (text != null && !text.isEmpty()) return text;

    HashMap<Integer, String> direct = new HashMap<>();
    editor.fileIO.populateDirectLinesForRange(line, line, direct);
    String directText = editor.windowRender.getLineTextForRenderWithDirect(line, direct);
    return directText == null ? "" : directText;
  }

  private void moveCursorToEndOfLastLine(int lastLineIndex) {
    int line = Math.max(0, lastLineIndex);
    String lastLineText = editor.windowRender.getLineTextForRender(line);
    CodeFold.FoldRange hidden =
        editor.codeFold.isCodeFoldingEnabled
            ? editor.codeFold.getCollapsedRangeContainingLine(line)
            : null;
    if (hidden != null && hidden.collapsed) {
      line = hidden.endLine;
      String endText = editor.codeFold.utils.getEndLineTextForFold(hidden);
      if (endText != null) {
        lastLineText = endText;
      }
    }
    if ((lastLineText == null || lastLineText.isEmpty()) && editor.codeFold.isCodeFoldingEnabled) {
      CodeFold.FoldRange endingFold = editor.codeFold.getCollapsedRangeEndingAtLine(line);
      if (endingFold != null) {
        String endText = editor.codeFold.utils.getEndLineTextForFold(endingFold);
        if (endText != null) lastLineText = endText;
      }
    }
    if (lastLineText == null) lastLineText = "";
    editor.cursor.cursorLine = line;
    editor.cursor.cursorChar = lastLineText.length();
  }
}
