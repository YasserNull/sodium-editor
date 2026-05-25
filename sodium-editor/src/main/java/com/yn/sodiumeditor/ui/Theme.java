package com.yn.sodiumeditor.ui;

import android.graphics.Color;
import com.yn.sodiumeditor.SodiumEditor;

public class Theme {

  public static final int THEME_WHITE = 0;
  public static final int THEME_DARK = 1;
  public static final int THEME_BLACK = 2;

  public int editorBackground;
  public int textColor;
  public int caretColor;
  public int gutterBackground;
  public int gutterSeparator;
  public int lineNumberColor;
  public int currentLineNumberColor;

  private Theme() {}

  public static Theme white() {
    Theme t = new Theme();
    t.editorBackground = Color.WHITE;
    t.textColor = Color.BLACK;
    t.caretColor = 0xFF000000;
    t.gutterBackground = 0xFFF5F5F5;
    t.gutterSeparator = 0xFFDDDDDD;
    t.lineNumberColor = 0xFF888888;
    t.currentLineNumberColor = 0xFF1976D2;
    return t;
  }

  public static Theme dark() {
    Theme t = new Theme();
    t.editorBackground = 0xFF1E1E1E;
    t.textColor = 0xFFD4D4D4;
    t.caretColor = 0xFFFFFFFF;
    t.gutterBackground = 0xFF252525;
    t.gutterSeparator = 0xFF444444;
    t.lineNumberColor = 0xFF888888;
    t.currentLineNumberColor = 0xFFBBBBBB;
    return t;
  }

  public static Theme black() {
    Theme t = new Theme();
    t.editorBackground = Color.BLACK;
    t.textColor = 0xFFCCCCCC;
    t.caretColor = 0xFFFFFFFF;
    t.gutterBackground = Color.BLACK;
    t.gutterSeparator = 0xFF333333;
    t.lineNumberColor = 0xFF666666;
    t.currentLineNumberColor = 0xFF999999;
    return t;
  }

  public void apply(SodiumEditor editor) {
    editor.textRender.setEditorBackgroundColor(editorBackground);
    editor.textRender.paint.setColor(textColor);
    editor.caret.caretColor = caretColor;
    editor.lineNumber.setGutterBackgroundColor(gutterBackground);
    editor.lineNumber.setGutterSeparatorColor(gutterSeparator);
    editor.lineNumber.setLineNumberColor(lineNumberColor);
    editor.lineNumber.setCurrentLineNumberColor(currentLineNumberColor);
    editor.invalidate();
  }
}
