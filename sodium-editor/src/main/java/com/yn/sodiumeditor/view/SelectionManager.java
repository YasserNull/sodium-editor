package com.yn.sodiumeditor.view;

final class SelectionManager {
  boolean hasSelection = false;
  int selStartLine = 0;
  int selStartChar = 0;
  int selEndLine = 0;
  int selEndChar = 0;
  boolean selecting = false;
  boolean isSelectAllActive = false;
  boolean isEntireFileSelected = false;
  boolean isLineNumberSelecting = false;
  int lineNumberSelectAnchorLine = -1;

  boolean hasSelection() {
    return hasSelection;
  }

  boolean isSelectAllActive() {
    return isSelectAllActive;
  }

  boolean isEntireFileSelected() {
    return isEntireFileSelected;
  }

  boolean isSelecting() {
    return selecting;
  }

  boolean isLineNumberSelecting() {
    return isLineNumberSelecting;
  }

  int getLineNumberSelectAnchorLine() {
    return lineNumberSelectAnchorLine;
  }

  void clearSelection() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
    isLineNumberSelecting = false;
    lineNumberSelectAnchorLine = -1;
  }

  void clearSelectionKeepLineNumberState() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
  }

  void setSelection(
      int startLine, int startChar, int endLine, int endChar, boolean selectingNow) {
    selStartLine = startLine;
    selStartChar = startChar;
    selEndLine = endLine;
    selEndChar = endChar;
    hasSelection = !(startLine == endLine && startChar == endChar);
    selecting = selectingNow;
  }

  void setSelectAllState(boolean selectAll, boolean entireFile) {
    isSelectAllActive = selectAll;
    isEntireFileSelected = entireFile;
  }

  void setSelecting(boolean selectingNow) {
    selecting = selectingNow;
  }

  void setLineNumberSelecting(boolean enabled, int anchorLine) {
    isLineNumberSelecting = enabled;
    lineNumberSelectAnchorLine = enabled ? anchorLine : -1;
  }
}
