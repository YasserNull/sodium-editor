package com.yn.sodiumeditor.core.wordwrap;

import com.yn.sodiumeditor.SodiumEditor;

/** Owns wrap-start caching and width-based invalidation. */
public class WordWrapCache {
  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapCache(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public void clear() {
    wordWrap.wrapCache.clear();
    wordWrap.wrapWidthPx = -1;
  }

  public void removeLine(int globalLine) {
    wordWrap.wrapCache.remove(globalLine);
  }

  public int[] getWrapStartsForLine(int globalLine, String line) {
    if (!wordWrap.isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    if (wordWrap.wrapWidthPx != widthPx) {
      wordWrap.wrapWidthPx = widthPx;
      wordWrap.wrapCache.clear();
    }
    if (!isWrapCacheableForLine(globalLine)) {
      wordWrap.wrapCache.remove(globalLine);
      return wordWrap.calculator.computeWrapStarts(line, widthPx, editor.textRender.paint, true);
    }
    int[] cached = wordWrap.wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts =
        wordWrap.calculator.computeWrapStarts(line, widthPx, editor.textRender.paint, true);
    wordWrap.wrapCache.put(globalLine, starts);
    return starts;
  }

  public boolean isWrapCacheableForLine(int globalLine) {
    if (globalLine >= editor.windowRender.windowStartLine
        && globalLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
      return true;
    }
    return editor.windowRender.hasModifiedLine(globalLine);
  }
}
