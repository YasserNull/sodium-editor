package com.yn.sodiumeditor.utils;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import java.util.Map;

public final class WrapWordUtils {

  private WrapWordUtils() {
    // Utility class
  }

  //================================================================================
  // Scroll Helpers
  //================================================================================

  public static float getScrollY(SodiumEditorView view) {
    return view.scrollManager.scrollY;
  }

  public static void abortScrollAnimation(android.widget.Scroller scroller) {
    if (!scroller.isFinished()) {
      scroller.abortAnimation();
    }
  }

  public static void abortScrollAnimation(android.widget.OverScroller scroller) {
    if (!scroller.isFinished()) {
      scroller.abortAnimation();
    }
  }

  public static void addScrollY(SodiumEditorView view, float delta) {
    view.scrollManager.scrollY += delta;
  }

  //================================================================================
  // Modified Lines Helpers
  //================================================================================

  public static String getModifiedLine(Map<Integer, String> modifiedLines, int lineIndex) {
    synchronized (modifiedLines) {
      return modifiedLines.get(lineIndex);
    }
  }

  public static boolean isModifiedLine(Map<Integer, String> modifiedLines, int globalLine) {
    synchronized (modifiedLines) {
      return modifiedLines.containsKey(globalLine);
    }
  }

  //================================================================================
  // Wrap Width
  //================================================================================

  public static float calculateWrapWidth(SodiumEditorView view) {
    return Math.max(1f, view.getWidth() - view.getTextStartX());
  }

  //================================================================================
  // Visual Line Position
  //================================================================================

  public static class VisualLinePosition {
    public final int line;
    public final int segment;

    public VisualLinePosition(int line, int segment) {
      this.line = line;
      this.segment = segment;
    }
  }
}
