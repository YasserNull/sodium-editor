package com.yn.sodiumeditor.core.view.events;

import com.yn.sodiumeditor.SodiumEditor;

public class onSizeChanged {
  private final SodiumEditor editor;

  public onSizeChanged(SodiumEditor editor) {
    this.editor = editor;
  }

  public void onSizeChanged(int w, int h, int oldw, int oldh) {
    if (w != oldw || h != oldh) {
      editor.lineNumber.invalidateLineNumberCache();
    }
    if (w != oldw) {
      editor.scroll.maxScrollXForScroll = 0f;
      editor.scroll.maxTextStartXForScroll = 0f;
    }
    int minWindow = editor.windowRender.computeMinWindowSize();
    if (editor.windowRender.windowSize < minWindow) {
      editor.windowRender.windowSize = minWindow;
      editor.windowRender.reloadWindowAroundVisible(false);
    }
    if (editor.wordWrap.isWordWrapEnabled && w != oldw) {
      editor.wordWrap.invalidateWrapMetrics(true);
      editor.wordWrap.requestWrapPrefixRebuild();
    }
  }
}
