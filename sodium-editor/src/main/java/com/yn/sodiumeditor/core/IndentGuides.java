package com.yn.sodiumeditor.core; 
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
  private boolean buildInProgress = false;

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
    if (enabled) {
      markIntervalsDirty();
    }
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
    if (!indentGuideIntervalsDirty || buildInProgress) return;
    if (!isIndentGuidesEnabled || !editor.isIndentationBlocksEnabled) return;
    
    buildInProgress = true;
    final int totalLines = editor.getLinesCount();
    if (totalLines <= 0) {
      buildInProgress = false;
      indentGuideIntervalsDirty = false;
      indentGuideIntervals.clear();
      return;
    }

    editor.fileIO.ioHandler.post(() -> {
      List<int[]> newIntervals = new ArrayList<>();
      int start = -1;
      
      // Use direct file access for faster scan if possible
      java.io.RandomAccessFile raf = null;
      try {
        if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
          raf = new java.io.RandomAccessFile(editor.fileIO.sourceFile, "r");
        }

        for (int i = 0; i < totalLines; i++) {
          String line = getLineTextForScan(i, raf);
          if (line == null || line.trim().isEmpty()) {
            if (start >= 0) {
              newIntervals.add(new int[] {start, i - 1});
              start = -1;
            }
            continue;
          }

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
              newIntervals.add(new int[] {start, i - 1});
              start = -1;
            }
          }
        }

        if (start >= 0) {
          newIntervals.add(new int[] {start, totalLines - 1});
        }

        if (!newIntervals.isEmpty()) {
          Collections.sort(newIntervals, (a, b) -> Integer.compare(a[0], b[0]));

          int write = 0;
          int[] cur = newIntervals.get(0);
          for (int i = 1; i < newIntervals.size(); i++) {
            int[] nxt = newIntervals.get(i);
            if (nxt[0] <= cur[1] + 1) {
              cur[1] = Math.max(cur[1], nxt[1]);
            } else {
              newIntervals.set(write++, cur);
              cur = nxt;
            }
          }
          newIntervals.set(write++, cur);
          while (newIntervals.size() > write)
            newIntervals.remove(newIntervals.size() - 1);
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (raf != null) {
          try { raf.close(); } catch (Exception ignored) {}
        }
      }

      final List<int[]> finalIntervals = newIntervals;
      editor.post(() -> {
        indentGuideIntervals.clear();
        indentGuideIntervals.addAll(finalIntervals);
        indentGuideIntervalsDirty = false;
        buildInProgress = false;
        editor.invalidate();
      });
    });
  }

  private String getLineTextForScan(int line, java.io.RandomAccessFile raf) {
    String mod = editor.textRender.modifiedLines.get(line);
    if (mod != null) return mod;
    int winStart = editor.textRender.windowStartLine;
    int winEnd = winStart + editor.textRender.linesWindow.size();
    if (line >= winStart && line < winEnd) {
      return editor.getLineFromWindowLocal(line - winStart);
    }
    if (raf != null && editor.fileIO.isIndexReady) {
      try {
        long offset = editor.fileIO.lineOffsets[line];
        return editor.fileIO.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {}
    }
    return null;
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
