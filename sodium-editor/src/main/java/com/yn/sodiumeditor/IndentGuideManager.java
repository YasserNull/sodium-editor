package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.ArrayList;

public final class IndentGuideManager {
  private static final float DEFAULT_STROKE_WIDTH = 2f;

  private final SodiumEditorView view;
  private final Paint indentGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ArrayList<int[]> indentGuideIntervals = new ArrayList<>();

  private boolean isIndentGuidesEnabled = false;
  private float indentGuideStrokeWidth = DEFAULT_STROKE_WIDTH;
  private float baseIndentGuideStrokeWidth = DEFAULT_STROKE_WIDTH;
  private float baseIndentGuideTextSizePx = 0f;
  private boolean indentGuideIntervalsDirty = true;

  IndentGuideManager(SodiumEditorView view, Paint basePaint) {
    this.view = view;
    initDefaults(basePaint);
  }

  void initDefaults(Paint basePaint) {
    baseIndentGuideTextSizePx = basePaint.getTextSize();
    indentGuidePaint.setColor(0xFF555555);
    indentGuidePaint.setStyle(Paint.Style.STROKE);
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  void setIndentGuidesEnabled(boolean enabled) {
    if (isIndentGuidesEnabled == enabled) return;
    isIndentGuidesEnabled = enabled;
    view.invalidate();
  }

  void setIndentGuidesColor(int color) {
    indentGuidePaint.setColor(color);
    view.invalidate();
  }

  void setIndentGuidesStrokeWidth(float width) {
    if (indentGuideStrokeWidth == width) return;
    baseIndentGuideStrokeWidth = width;
    baseIndentGuideTextSizePx = view.getIndentGuideTextSizePx();
    updateForTextSize(view.getIndentGuideTextSizePx());
    view.invalidate();
  }

  void updateForTextSize(float sizePx) {
    indentGuideStrokeWidth = Math.max(1f, scaleByTextSize(baseIndentGuideStrokeWidth, baseIndentGuideTextSizePx, sizePx));
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  void markIntervalsDirty() {
    indentGuideIntervalsDirty = true;
  }

  public void rebuildIntervalsIfNeeded() {
    if (!indentGuideIntervalsDirty) return;
    indentGuideIntervalsDirty = false;
    indentGuideIntervals.clear();
    if (!view.isIndentationBlocksEnabledForIndentGuides() || !view.hasIndentGuideFoldRanges()) return;
    for (FoldManager.FoldRange range : view.getIndentGuideFoldRanges()) {
      if (!range.isIndentFold) continue;
      int start = range.startLine + 1;
      int end = range.endLine;
      if (end < start) continue;
      indentGuideIntervals.add(new int[] {start, end});
    }
    if (indentGuideIntervals.isEmpty()) return;
    indentGuideIntervals.sort((a, b) -> Integer.compare(a[0], b[0]));
    int write = 0;
    int[] cur = indentGuideIntervals.get(0);
    for (int i = 1; i < indentGuideIntervals.size(); i++) {
      int[] nxt = indentGuideIntervals.get(i);
      if (nxt[0] <= cur[1] + 1) {
        cur[1] = Math.max(cur[1], nxt[1]);
      } else {
        indentGuideIntervals.set(write++, cur);
        cur = nxt;
      }
    }
    indentGuideIntervals.set(write++, cur);
    while (indentGuideIntervals.size() > write) {
      indentGuideIntervals.remove(indentGuideIntervals.size() - 1);
    }
  }

  public void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
    if (!isIndentGuidesEnabled
        || !view.isIndentationBlocksEnabledForIndentGuides()
        || view.isHeavyDrawSuppressedForIndentGuides()) {
      return;
    }
    if (!isLineInIndentBlock(globalLine)) return;
    if (line == null || line.isEmpty()) return;
    int unitSpaces = view.getIndentGuideUnit().length();
    if (unitSpaces <= 0) return;

    float top = view.getIndentGuideLineTop(globalLine);
    float bottom = top + view.getIndentGuideLineHeight();
    int columns = 0;
    int nextGuide = unitSpaces;
    float x = 0f;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c != ' ' && c != '\t') break;
      float adv = view.measureTextWithVisualSpacesForIndentGuides(line, i, i + 1);
      if (c == '\t') {
        columns += view.getIndentGuideTabSize();
      } else {
        columns += 1;
      }
      x += adv;
      while (columns >= nextGuide) {
        if (view.isWhitespaceAtXForIndentGuides(line, globalLine, x)) {
          canvas.drawLine(x, top, x, bottom, indentGuidePaint);
        }
        nextGuide += unitSpaces;
      }
    }
  }

  private boolean isLineInIndentBlock(int globalLine) {
    if (!view.isIndentationBlocksEnabledForIndentGuides()) return false;
    rebuildIntervalsIfNeeded();
    if (indentGuideIntervals.isEmpty()) return false;
    int lo = 0;
    int hi = indentGuideIntervals.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int[] interval = indentGuideIntervals.get(mid);
      if (globalLine < interval[0]) {
        hi = mid - 1;
      } else if (globalLine > interval[1]) {
        lo = mid + 1;
      } else {
        return true;
      }
    }
    return false;
  }

  private static float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }
}
