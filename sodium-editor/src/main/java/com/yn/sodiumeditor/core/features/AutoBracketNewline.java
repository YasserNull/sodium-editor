package com.yn.sodiumeditor.core.features;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.highlite.IndentGuides;
/**
 * Manages automatic bracket newline and indentation for the SodiumEditor.
 * Automatically adds newlines and indentation when typing brackets.
 */
public class AutoBracketNewline {

  private final SodiumEditor editor;

  // Auto-bracket-newline state
  public boolean isAutoBracketNewlineEnabled = false;
  public boolean isAutoBracketNewlineIndentEnabled = false;
  public boolean isAutoIndentAfterClosingBracketEnabled = false;

  public AutoBracketNewline(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables auto-bracket-newline.
   */
  public void setAutoBracketNewlineEnabled(boolean enabled) {
    this.isAutoBracketNewlineEnabled = enabled;
  }

  /**
   * Enables or disables auto-bracket-newline indentation.
   */
  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    this.isAutoBracketNewlineIndentEnabled = enabled;
  }

  /**
   * Enables or disables auto-indent after closing bracket.
   */
  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    this.isAutoIndentAfterClosingBracketEnabled = enabled;
  }

  /**
   * Inserts a newline at cursor with automatic bracket handling.
   */
  public void insertNewlineAtCursor() {
    if (editor.isReadOnly) return;
    
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText("\n");
      return;
    }

    BracketPairType pairType = getCursorBracketPairType();
    if (isAutoBracketNewlineEnabled && pairType != BracketPairType.NONE) {
      insertNewlineWithBracketPair(pairType);
      return;
    }

    if (isAutoIndentAfterClosingBracketEnabled) {
      if (insertNewlineAfterClosingBracket()) {
        return;
      }
    }

    if (editor.isIndentationBlocksEnabled) {
      insertNewlineWithIndentationBlock();
      return;
    }

    if (isAutoBracketNewlineIndentEnabled) {
      insertNewlineWithBaseIndent();
      return;
    }

    editor.editOperators.insertCharAtCursor('\n');
  }

  /**
   * Inserts newline with bracket pair handling.
   */
  private void insertNewlineWithBracketPair(BracketPairType pairType) {
    String baseIndent = "";
    String innerIndent = "";
    if (isAutoBracketNewlineIndentEnabled) {
      baseIndent = editor.getLineLeadingWhitespace(editor.cursor.cursorLine);
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
    editor.keepCursorVisibleHorizontally();
    editor.invalidate();
    editor.autoCompletion.updateSuggestion();
  }

  /**
   * Inserts newline after closing bracket with proper indentation.
   */
  private boolean insertNewlineAfterClosingBracket() {
    String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) ln = "";
    int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
    String before = ln.substring(0, safeChar);
    int prevNonWs = SodiumEditor.findPrevNonWhitespaceIndex(before, before.length() - 1);
    
    if (prevNonWs >= 0) {
      char c = before.charAt(prevNonWs);
      if (c == '{' || c == '}') {
        String baseIndent = editor.getLineLeadingWhitespace(editor.cursor.cursorLine);
        int baseWidth = editor.getIndentWidth(baseIndent);
        int unit = IndentGuides.INDENT_BLOCK_UNIT.length();
        int targetWidth = baseWidth;

        if (c == '{') {
          int firstNonSpace = SodiumEditor.getFirstNonSpaceIndex(before);
          boolean startsWithClosingParenOrBracket =
              firstNonSpace >= 0
                  && (before.charAt(firstNonSpace) == ')' || before.charAt(firstNonSpace) == ']');
          if (!startsWithClosingParenOrBracket) {
            targetWidth = baseWidth + unit;
          }
        } else {
          targetWidth = Math.max(0, baseWidth - unit);
        }

        editor.editOperators.insertTextAtCursor("\n" + SodiumEditor.buildIndentFromWidth(targetWidth));
        return true;
      }
    }
    return false;
  }

  /**
   * Inserts newline with indentation block handling.
   */
  private void insertNewlineWithIndentationBlock() {
    String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) ln = "";
    int safeChar = Math.max(0, Math.min(editor.cursor.cursorChar, ln.length()));
    String before = ln.substring(0, safeChar);
    String trimmed = SodiumEditor.rstripWhitespace(before);
    String baseIndent = editor.getLineLeadingWhitespace(editor.cursor.cursorLine);
    String extraIndent = trimmed.endsWith(":") ? IndentGuides.INDENT_BLOCK_UNIT : "";
    editor.editOperators.insertTextAtCursor("\n" + baseIndent + extraIndent);
  }

  /**
   * Inserts newline with base indentation.
   */
  private void insertNewlineWithBaseIndent() {
    String baseIndent = editor.getLineLeadingWhitespace(editor.cursor.cursorLine);
    editor.editOperators.insertTextAtCursor("\n" + baseIndent);
  }

  /**
   * Gets the bracket pair type at cursor position.
   */
  public BracketPairType getCursorBracketPairType() {
    String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) return BracketPairType.NONE;
    if (editor.cursor.cursorChar <= 0 || editor.cursor.cursorChar >= ln.length()) return BracketPairType.NONE;

    char left = ln.charAt(editor.cursor.cursorChar - 1);
    char right = ln.charAt(editor.cursor.cursorChar);
    if (left == '{' && right == '}') return BracketPairType.CURLY;
    if (left == '(' && right == ')') return BracketPairType.ROUND;
    if (left == '[' && right == ']') return BracketPairType.SQUARE;
    return BracketPairType.NONE;
  }

  /**
   * Bracket pair types.
   */
  public enum BracketPairType {
    NONE,
    CURLY,
    ROUND,
    SQUARE
  }
}
