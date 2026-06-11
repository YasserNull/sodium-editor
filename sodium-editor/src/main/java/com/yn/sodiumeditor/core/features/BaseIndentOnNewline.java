package com.yn.sodiumeditor.core.features;

import com.yn.sodiumeditor.SodiumEditor;

/** Keeps the current line indentation when inserting a plain newline. */
public class BaseIndentOnNewline {
  private final SodiumEditor editor;

  public boolean isBaseIndentOnNewlineEnabled = true;

  public BaseIndentOnNewline(SodiumEditor editor) {
    this.editor = editor;
  }

  public void setBaseIndentOnNewlineEnabled(boolean enabled) {
    isBaseIndentOnNewlineEnabled = enabled;
  }

  public boolean getBaseIndentOnNewlineEnabled() {
    return isBaseIndentOnNewlineEnabled;
  }

  public void insertNewlineWithBaseIndent() {
    String baseIndent = editor.view.getLineLeadingWhitespace(editor.cursor.cursorLine);
    editor.editOperators.insertTextAtCursor("\n" + baseIndent);
  }
}
