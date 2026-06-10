package com.yn.sodiumeditor.core.view.events;

import com.yn.sodiumeditor.SodiumEditor;

public class onMeasure {
  private final SodiumEditor editor;

  public onMeasure(SodiumEditor editor) {
    this.editor = editor;
  }

  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    float oldGutterWidth = editor.lineNumber.lineNumbersGutterWidth;
    if (editor.lineNumber.showLineNumbers) {
      int maxLines = editor.lineNumber.utils.estimateLineCountForGutter();
      String maxLineNum = String.valueOf(maxLines);
      float baseWidth =
          editor.lineNumber.lineNumbersPaint.measureText(maxLineNum)
              + (editor.lineNumber.GUTTER_TEXT_PADDING * 2);
      editor.lineNumber.lineNumbersGutterWidth = baseWidth + editor.lineNumber.gutterSeparatorWidth;
    } else {
      editor.lineNumber.lineNumbersGutterWidth = 0f;
    }

    if (editor.wordWrap.isWordWrapEnabled
        && Math.abs(editor.lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      editor.wordWrap.invalidateWrapMetrics(true);
      editor.wordWrap.requestWrapPrefixRebuild();
    }
    if (Math.abs(editor.lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      editor.lineNumber.invalidateLineNumberCache();
    }
  }
}
