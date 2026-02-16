package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class SelectionManager {
  private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int selectionHighlightColor = 0x8033B5E5;
  private final RectF selectionRectTmp = new RectF();
  private final Path selectionPathTmp = new Path();
  private final float[] selectionRadiiTmp = new float[8];

  boolean hasSelection = false;
  int selStartLine = 0;
  int selStartChar = 0;
  int selEndLine = 0;
  int selEndChar = 0;
  boolean selecting = false;
  boolean isSelectAllActive = false;
  boolean isEntireFileSelected = false;
  boolean isLineNumberSelecting = false;
  int lineNumberSelectAnchorLine = -1;

  boolean hasSelection() {
    return hasSelection;
  }

  boolean isSelectAllActive() {
    return isSelectAllActive;
  }

  boolean isEntireFileSelected() {
    return isEntireFileSelected;
  }

  boolean isSelecting() {
    return selecting;
  }

  boolean isLineNumberSelecting() {
    return isLineNumberSelecting;
  }

  int getLineNumberSelectAnchorLine() {
    return lineNumberSelectAnchorLine;
  }

  void clearSelection() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
    isLineNumberSelecting = false;
    lineNumberSelectAnchorLine = -1;
  }

  void clearSelectionKeepLineNumberState() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
  }

  void setSelection(
      int startLine, int startChar, int endLine, int endChar, boolean selectingNow) {
    selStartLine = startLine;
    selStartChar = startChar;
    selEndLine = endLine;
    selEndChar = endChar;
    hasSelection = !(startLine == endLine && startChar == endChar);
    selecting = selectingNow;
  }

  void setSelectAllState(boolean selectAll, boolean entireFile) {
    isSelectAllActive = selectAll;
    isEntireFileSelected = entireFile;
  }

  void setSelecting(boolean selectingNow) {
    selecting = selectingNow;
  }

  void setLineNumberSelecting(boolean enabled, int anchorLine) {
    isLineNumberSelecting = enabled;
    lineNumberSelectAnchorLine = enabled ? anchorLine : -1;
  }

  void initPaints() {
    selectionPaint.setStyle(Paint.Style.FILL);
  }

  boolean setSelectionHighlightColor(int color) {
    if (selectionHighlightColor == color) return false;
    selectionHighlightColor = color;
    return true;
  }

  Paint getSelectionPaint() {
    selectionPaint.setColor(selectionHighlightColor);
    return selectionPaint;
  }

  void drawSelectionSegment(
      Canvas canvas,
      float left,
      float top,
      float right,
      float bottom,
      boolean roundTopLeft,
      boolean roundTopRight,
      boolean roundBottomRight,
      boolean roundBottomLeft,
      float lineHeight,
      Paint paint) {
    if (right <= left || bottom <= top) return;

    float radius = Math.min(12f, Math.max(2f, lineHeight * 0.22f));
    // Keep vertical edges flush between lines to avoid "seam" lines when selecting multiple lines.
    float insetX = 0.5f;
    selectionRectTmp.set(left + insetX, top, right - insetX, bottom);

    if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
      canvas.drawRect(selectionRectTmp, paint);
      return;
    }

    float tl = roundTopLeft ? radius : 0f;
    float tr = roundTopRight ? radius : 0f;
    float br = roundBottomRight ? radius : 0f;
    float bl = roundBottomLeft ? radius : 0f;

    selectionRadiiTmp[0] = tl;
    selectionRadiiTmp[1] = tl;
    selectionRadiiTmp[2] = tr;
    selectionRadiiTmp[3] = tr;
    selectionRadiiTmp[4] = br;
    selectionRadiiTmp[5] = br;
    selectionRadiiTmp[6] = bl;
    selectionRadiiTmp[7] = bl;

    selectionPathTmp.reset();
    selectionPathTmp.addRoundRect(selectionRectTmp, selectionRadiiTmp, Path.Direction.CW);
    canvas.drawPath(selectionPathTmp, paint);
  }

  public void setSelectionHandleColor(SodiumEditorView view, int color) {
    if (view.handlesManager.getSelectionHandleColor() == color) return;
    view.handlesManager.setSelectionHandleColor(color);
    view.invalidate();
  }

  public void setSelectionHighlightColor(SodiumEditorView view, int color) {
    if (this.setSelectionHighlightColor(color)) {
      if (this.hasSelection) view.invalidate();
    }
  }
}
