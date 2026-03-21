package com.yn.sodiumeditor.core.highlite;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.yn.sodiumeditor.renderer.TextRender;
/**
 * Manages indent guides for the SodiumEditor.
 * Draws vertical guide lines for indentation blocks.
 */
public class IndentGuides {

  // Indent block unit constant
  public static final String INDENT_BLOCK_UNIT = "    ";

  private final SodiumEditor editor;

  // Indent guides state
  public boolean isIndentGuidesEnabled = false;
  public final Paint indentGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float indentGuideStrokeWidth = 2f;
  public float baseIndentGuideStrokeWidth = indentGuideStrokeWidth;
  public float baseIndentGuideTextSizePx = 0f;

  // Indent guide intervals
  public final List<int[]> indentGuideIntervals = new ArrayList<>();
  public boolean indentGuideIntervalsDirty = true;

  // Buffers for drawing
  public float[] guideSeenXBuffer;
  public int guideSeenXCount = 0;

  public IndentGuides(SodiumEditor editor) {
    this.editor = editor;
    initPaint();
  }

  private void initPaint() {
    indentGuidePaint.setColor(0xFF555555);
    indentGuidePaint.setStyle(Paint.Style.STROKE);
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  /**
   * Enables or disables indent guides.
   */
  public void setIndentGuidesEnabled(boolean enabled) {
    if (this.isIndentGuidesEnabled == enabled) return;
    this.isIndentGuidesEnabled = enabled;
    editor.invalidate();
  }

  /**
   * Sets the indent guides color.
   */
  public void setIndentGuidesColor(int color) {
    indentGuidePaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets the indent guides stroke width.
   */
  public void setIndentGuidesStrokeWidth(float width) {
    if (this.indentGuideStrokeWidth == width) return;
    this.baseIndentGuideStrokeWidth = width;
    this.baseIndentGuideTextSizePx = editor.textRender.paint.getTextSize();
    updateStrokeWidth();
    editor.invalidate();
  }

  /**
   * Updates stroke width based on text size.
   */
  public void updateStrokeWidth() {
    float sizePx = editor.textRender.paint.getTextSize();
    indentGuideStrokeWidth = Math.max(
        1f,
        editor.scaleByTextSize(baseIndentGuideStrokeWidth, baseIndentGuideTextSizePx, sizePx));
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  /**
   * Marks indent guide intervals as dirty.
   */
  public void markIntervalsDirty() {
    indentGuideIntervalsDirty = true;
  }

  /**
   * Rebuilds indent guide intervals if needed.
   */
  public void rebuildIndentGuideIntervalsIfNeeded() {
    if (!indentGuideIntervalsDirty) return;
    indentGuideIntervalsDirty = false;
    indentGuideIntervals.clear();

    int totalLines = editor.getLinesCount();
    if (totalLines <= 0) return;

    int start = -1;
    for (int i = 0; i < totalLines; i++) {
      String line = editor.getLineTextForRender(i);
      if (line == null || line.trim().isEmpty()) continue;

      int spaces = 0;
      for (int j = 0; j < line.length(); j++) {
        char c = line.charAt(j);
        if (c == ' ') spaces++;
        else if (c == '\t') spaces += TextRender.DEFAULT_TAB_SIZE_SPACES;
        else break;
      }

      if (spaces >= INDENT_BLOCK_UNIT.length()) {
        if (start < 0) start = i;
      } else {
        if (start >= 0) {
          indentGuideIntervals.add(new int[] {start, i - 1});
          start = -1;
        }
      }
    }

    if (start >= 0) {
      indentGuideIntervals.add(new int[] {start, totalLines - 1});
    }

    if (indentGuideIntervals.isEmpty()) return;
    Collections.sort(indentGuideIntervals, (a, b) -> Integer.compare(a[0], b[0]));

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
    while (indentGuideIntervals.size() > write)
      indentGuideIntervals.remove(indentGuideIntervals.size() - 1);
  }

  /**
   * Checks if a line is in an indent block.
   */
  public boolean isLineInIndentBlock(int globalLine) {
    if (!isIndentGuidesEnabled || !editor.isIndentationBlocksEnabled) return false;
    rebuildIndentGuideIntervalsIfNeeded();
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

  /**
   * Draws indent guides for a line.
   */
  public void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
    if (!isIndentGuidesEnabled || !editor.isIndentationBlocksEnabled || editor.isHeavyDrawSuppressed()) return;
    if (!isLineInIndentBlock(globalLine)) return;
    if (line == null || line.isEmpty()) return;

    int unitSpaces = INDENT_BLOCK_UNIT.length();
    if (unitSpaces <= 0) return;

    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    int columns = 0;
    int nextGuide = unitSpaces;
    float x = 0f;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c != ' ' && c != '\t') break;
      float adv = editor.measureTextWithVisualSpaces(line, i, i + 1, editor.textRender.paint);
      if (c == '\t') {
        columns += TextRender.DEFAULT_TAB_SIZE_SPACES;
      } else {
        columns += 1;
      }
      x += adv;
      while (columns >= nextGuide) {
        if (editor.isWhitespaceAtX(line, globalLine, x)) {
          canvas.drawLine(x, top, x, bottom, indentGuidePaint);
        }
        nextGuide += unitSpaces;
      }
    }
  }

  /**
   * Clears indent guide intervals.
   */
  public void clearIntervals() {
    indentGuideIntervals.clear();
    indentGuideIntervalsDirty = true;
  }
}
