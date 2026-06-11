package com.yn.sodiumeditor.core.features;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.indent.IndentGuides;

/** Handles automatic indentation when pressing enter after opening or closing braces. */
public class AutoIndentAfterClosingBracket {
  private final SodiumEditor editor;

  public boolean isAutoIndentAfterClosingBracketEnabled = true;

  public AutoIndentAfterClosingBracket(SodiumEditor editor) {
    this.editor = editor;
  }

  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    isAutoIndentAfterClosingBracketEnabled = enabled;
  }

  public boolean getAutoIndentAfterClosingBracketEnabled() {
    return isAutoIndentAfterClosingBracketEnabled;
  }

  public boolean insertNewlineAfterClosingBracket() {
    if (!isAutoIndentAfterClosingBracketEnabled) return false;

    String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) ln = "";
    int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
    String before = ln.substring(0, safeChar);
    int prevNonWs =
        com.yn.sodiumeditor.utils.TextUtils.findPrevNonWhitespaceIndex(before, before.length() - 1);

    if (prevNonWs >= 0) {
      char c = before.charAt(prevNonWs);
      if (c == '{' || c == '}') {
        String baseIndent = editor.view.getLineLeadingWhitespace(editor.cursor.cursorLine);
        int baseWidth = editor.view.getIndentWidth(baseIndent);
        int unit = IndentGuides.INDENT_BLOCK_UNIT.length();
        int targetWidth = baseWidth;

        if (c == '{') {
          int firstNonSpace = com.yn.sodiumeditor.utils.TextUtils.getFirstNonSpaceIndex(before);
          boolean startsWithClosingParenOrBracket =
              firstNonSpace >= 0
                  && (before.charAt(firstNonSpace) == ')' || before.charAt(firstNonSpace) == ']');
          if (!startsWithClosingParenOrBracket) {
            targetWidth = baseWidth + unit;
          }
        } else {
          targetWidth = Math.max(0, baseWidth - unit);
        }

        editor.editOperators.insertTextAtCursor(
            "\n" + com.yn.sodiumeditor.core.view.EditorView.buildIndentFromWidth(targetWidth));
        return true;
      }
    }
    return false;
  }
}
