package com.yn.sodiumeditor.core.features;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.indent.IndentGuides;

/** Handles automatic newline indentation around bracket pairs. */
public class AutoBracketNewlineIndent {
  private final SodiumEditor editor;

  public boolean isAutoBracketNewlineIndentEnabled = true;

  public AutoBracketNewlineIndent(SodiumEditor editor) {
    this.editor = editor;
  }

  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    isAutoBracketNewlineIndentEnabled = enabled;
  }

  public boolean getAutoBracketNewlineIndentEnabled() {
    return isAutoBracketNewlineIndentEnabled;
  }

  /** Inserts a newline at cursor with automatic bracket handling. */
  public void insertNewlineAtCursor() {
    if (editor.view.isReadOnly) return;

    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText("\n");
      return;
    }

    BracketPairType pairType = getCursorBracketPairType();
    if (isAutoBracketNewlineIndentEnabled && pairType != BracketPairType.NONE) {
      insertNewlineWithBracketPair(pairType);
      return;
    }

    if (editor.autoIndentAfterClosingBracket.insertNewlineAfterClosingBracket()) {
      return;
    }

    if (editor.indentGuides.isIndentationBlocksEnabled) {
      insertNewlineWithIndentationBlock();
      return;
    }

    if (editor.baseIndentOnNewline.isBaseIndentOnNewlineEnabled) {
      editor.baseIndentOnNewline.insertNewlineWithBaseIndent();
      return;
    }

    editor.editOperators.insertCharAtCursor('\n');
  }

  private void insertNewlineWithBracketPair(BracketPairType pairType) {
    String baseIndent = "";
    String innerIndent = "";
    if (editor.baseIndentOnNewline.isBaseIndentOnNewlineEnabled) {
      baseIndent = editor.view.getLineLeadingWhitespace(editor.cursor.cursorLine);
      innerIndent = baseIndent + "  ";
    }

    String closeIndent = (pairType == BracketPairType.CURLY) ? baseIndent : innerIndent;
    String insertText = "\n" + innerIndent + "\n" + closeIndent;

    int targetLine = editor.cursor.cursorLine + 1;
    int targetChar = innerIndent.length();
    editor.editOperators.insertTextAtCursor(insertText);

    editor.cursor.cursorLine = targetLine;
    editor.cursor.cursorChar = targetChar;
    editor.caret.resetBlink();
    editor.scroll.keepCursorVisibleHorizontally();
    editor.invalidate();
    editor.autoSuggestion.updateSuggestion();
  }

  private void insertNewlineWithIndentationBlock() {
    String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) ln = "";
    int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
    String before = ln.substring(0, safeChar);
    String trimmed = com.yn.sodiumeditor.utils.TextUtils.rstripWhitespace(before);
    String baseIndent = editor.view.getLineLeadingWhitespace(editor.cursor.cursorLine);
    String extraIndent = trimmed.endsWith(":") ? IndentGuides.INDENT_BLOCK_UNIT : "";
    editor.editOperators.insertTextAtCursor("\n" + baseIndent + extraIndent);
  }

  public BracketPairType getCursorBracketPairType() {
    String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) return BracketPairType.NONE;
    if (editor.cursor.cursorChar <= 0 || editor.cursor.cursorChar >= ln.length())
      return BracketPairType.NONE;

    char left = ln.charAt(editor.cursor.cursorChar - 1);
    char right = ln.charAt(editor.cursor.cursorChar);
    if (left == '{' && right == '}') return BracketPairType.CURLY;
    if (left == '(' && right == ')') return BracketPairType.ROUND;
    if (left == '[' && right == ']') return BracketPairType.SQUARE;
    return BracketPairType.NONE;
  }

  public enum BracketPairType {
    NONE,
    CURLY,
    ROUND,
    SQUARE
  }
}
