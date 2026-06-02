package com.yn.sodiumeditor.core.features;

import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayDeque;

public class BracketErrorScanner {

  private final SodiumEditor editor;
  private int lastScanEditVersion = -1;

  public boolean unclosedBracketUnderlineEnabled = true;

  private static class BracketPos {
    final int line;
    final int col;
    final char opening;
    BracketPos(int line, int col, char opening) {
      this.line = line;
      this.col = col;
      this.opening = opening;
    }
  }

  public BracketErrorScanner(SodiumEditor editor) {
    this.editor = editor;
  }

  public void scanForErrors() {
    if (!unclosedBracketUnderlineEnabled) return;
    if (!editor.errorUnderline.isErrorUnderlineEnabled()) return;
    int editVersion = editor.editOperators.editVersion.get();
    if (editVersion == lastScanEditVersion) return;
    lastScanEditVersion = editVersion;

    editor.errorUnderline.clearErrorUnderlines();

    int totalLines = editor.view.getLinesCount();
    if (totalLines <= 0) return;

    int firstLine = Math.max(0, editor.viewRender.drawBaseLine);
    int visCount = (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 1;
    int lastLine = Math.min(totalLines - 1, firstLine + visCount);

    ArrayDeque<BracketPos> openStack = new ArrayDeque<>();
    boolean inString = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    char stringDelim = 0;

    for (int line = firstLine; line <= lastLine; line++) {
      String text = editor.windowRender.getLineTextForRender(line);
      if (text == null) continue;

      inLineComment = false;

      for (int col = 0; col < text.length(); col++) {
        char c = text.charAt(col);
        boolean escaped = col > 0 && isEscaped(text, col);

        if (inLineComment) break;

        if (!escaped && !inBlockComment && !inString) {
          if (col + 1 < text.length() && c == '/' && text.charAt(col + 1) == '/') {
            inLineComment = true;
            break;
          }
          if (col + 1 < text.length() && c == '/' && text.charAt(col + 1) == '*') {
            inBlockComment = true;
            col++;
            continue;
          }
        }

        if (!escaped && inBlockComment) {
          if (col + 1 < text.length() && c == '*' && text.charAt(col + 1) == '/') {
            inBlockComment = false;
            col++;
          }
          continue;
        }

        if (inBlockComment) continue;

        if (!escaped && (c == '"' || c == '\'' || c == '`') && !inCharContext(text, col)) {
          if (!inString) {
            inString = true;
            stringDelim = c;
            openStack.push(new BracketPos(line, col, c));
          } else if (c == stringDelim) {
            inString = false;
            stringDelim = 0;
            if (!openStack.isEmpty() && openStack.peek().opening == c) {
              openStack.pop();
            }
          }
          continue;
        }

        if (inString) continue;

        switch (c) {
          case '(':
            openStack.push(new BracketPos(line, col, '('));
            break;
          case ')':
            if (!openStack.isEmpty() && openStack.peek().opening == '(') {
              openStack.pop();
            } else {
              editor.errorUnderline.setErrorUnderline(line, col, 1);
            }
            break;
          case '{':
            openStack.push(new BracketPos(line, col, '{'));
            break;
          case '}':
            if (!openStack.isEmpty() && openStack.peek().opening == '{') {
              openStack.pop();
            } else {
              editor.errorUnderline.setErrorUnderline(line, col, 1);
            }
            break;
          case '[':
            openStack.push(new BracketPos(line, col, '['));
            break;
          case ']':
            if (!openStack.isEmpty() && openStack.peek().opening == '[') {
              openStack.pop();
            } else {
              editor.errorUnderline.setErrorUnderline(line, col, 1);
            }
            break;
        }
      }

      if (inString) {
        if (!openStack.isEmpty() && openStack.peek().opening == stringDelim) {
          BracketPos quotePos = openStack.peek();
          editor.errorUnderline.setErrorUnderline(quotePos.line, quotePos.col, 1);
        }
      }
    }

    for (BracketPos pos : openStack) {
      if (pos.opening == '"' || pos.opening == '\'' || pos.opening == '`') continue;
      editor.errorUnderline.setErrorUnderline(pos.line, pos.col, 1);
    }
  }

  private static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  private static boolean inCharContext(String line, int col) {
    if (col <= 0) return false;
    char prev = line.charAt(col - 1);
    return prev == '\'';
  }
}
