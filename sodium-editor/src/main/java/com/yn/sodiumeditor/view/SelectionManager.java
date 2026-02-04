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
}
