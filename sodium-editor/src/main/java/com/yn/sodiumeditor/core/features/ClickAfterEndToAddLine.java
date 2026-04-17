package com.yn.sodiumeditor.core.features; 
import com.yn.sodiumeditor.SodiumEditor;
/**
 * Manages the click-after-end-to-add-line functionality for the SodiumEditor.
 * Allows users to tap after the end of the last line to add a new line.
 */
public class ClickAfterEndToAddLine {

  private final SodiumEditor editor;

  // Click after end state
  public boolean isClickAfterEndToAddLineEnabled = true;

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
    if (editor.fileIO.isEof && visibleIndex >= editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() && !editor.windowRender.linesWindow.isEmpty()) {
      return true;
    }
    if (editor.codeFold.isCodeFoldingEnabled) {
      return visibleIndex >= totalVisible;
    }
    return false;
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

    int lastLineIndex = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;

    // Only add a new line if the user taps exactly on the first empty line after
    // the text
    if (visibleIndex == totalVisible) {
      editor.cursor.cursorLine = lastLineIndex;
      String lastLineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = lastLineText.length();
      editor.editOperators.insertTextAtCursor("\n");
    } else {
      // If tapped further down, just move cursor to end of text without adding
      // lines
      editor.cursor.cursorLine = lastLineIndex;
      String lastLineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
      editor.cursor.cursorChar = lastLineText.length();
    }

    return true;
  }

  /**
   * Handles the click after end action without adding a line.
   * Just moves the cursor to the end of the last line.
   */
  public void handleDefaultAfterEnd() {
    editor.cursor.cursorLine = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;
    String lastLineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    editor.cursor.cursorChar = lastLineText.length();
  }
}
