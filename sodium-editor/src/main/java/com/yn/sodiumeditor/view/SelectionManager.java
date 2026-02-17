package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class SelectionManager {
  private final SodiumEditorView view;
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

  SelectionManager(SodiumEditorView view) {
    this.view = view;
  }

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

  public void copyOrCutSelection(SodiumEditorView view, final boolean cut) {
    if (!hasSelection()) return;
    view.autoSuggestionManager.clearActiveSuggestion();

    if (view.shouldHideCopyCutForSelection()) return;

    int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    long lines = (long) eL - (long) sL + 1L;
    if (lines > view.getCopyCutMaxLines()) return;

    final int fsL = sL, fsC = sC, feL = eL, feC = eC;

    boolean fullyInWindow =
        (fsL >= view.windowStartLine) && (feL < view.windowStartLine + view.linesWindow.size());
    if (fullyInWindow) {
      String text = buildSelectedTextFromWindow(view, fsL, fsC, feL, feC, view.getCopyCutMaxChars());
      android.content.ClipboardManager cm =
          (android.content.ClipboardManager) view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("text", (text == null) ? "" : text));
      if (cut) {
        view.deleteSelection();
      }
      return;
    }

    if (view.wordWrapManager.isWordWrapEnabled) {
      view.wordWrapManager.cancelWrapWorkForPriority(view);
    }

    view.ioHandler.post(
        () -> {
          final String text = buildSelectedTextBlocking(view, fsL, fsC, feL, feC, view.getCopyCutMaxChars());
          view.post(
              () -> {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null)
                  cm.setPrimaryClip(android.content.ClipData.newPlainText("text", (text == null) ? "" : text));

                if (cut) {
                  view.deleteSelection();
                }
              });
        });
  }

  public String buildSelectedTextBlocking(SodiumEditorView view, int sL, int sC, int eL, int eC, int maxChars) {
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    if (view.fileManager.getSourceFile() == null || view.fileManager.isFileCleared()) {
      return buildSelectedTextFromWindow(view, sL, sC, eL, eC, maxChars);
    }

    boolean fullyInWindow = (sL >= view.windowStartLine) && (eL < view.windowStartLine + view.linesWindow.size());
    if (fullyInWindow) {
      return buildSelectedTextFromWindow(view, sL, sC, eL, eC, maxChars);
    }

    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
      long startByte;
      if (view.fileManager.isIndexReady()) {
        synchronized (view.fileManager.lineOffsetsLock) {
          if (sL >= 0 && sL < view.fileManager.getLineOffsets().length) startByte = view.fileManager.getLineOffsets()[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = view.findLineStartByteByScanningPublic(raf, sL);
      }

      raf.seek(startByte);
      try (java.io.BufferedReader br =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(new java.io.FileInputStream(raf.getFD()), view.fileManager.fileCharset), 8192)) {

        StringBuilder sb = new StringBuilder();
        for (int L = sL; L <= eL; L++) {
          String fileLine = br.readLine();
          if (fileLine == null) fileLine = "";

          String ln;
          synchronized (view.modifiedLines) {
            ln = view.modifiedLines.containsKey(L) ? view.modifiedLines.get(L) : fileLine;
          }
          if (ln == null) ln = "";

          int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
          int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
          if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
          if (L < eL) sb.append('\n');

          if (sb.length() > maxChars) return sb.substring(0, maxChars);
        }
        return sb.toString();
      }
    } catch (Exception e) {
      return null;
    }
  }

  private String buildSelectedTextFromWindow(SodiumEditorView view, int sL, int sC, int eL, int eC, int maxChars) {
    StringBuilder sb = new StringBuilder();
    synchronized (view.linesWindow) {
      for (int L = sL; L <= eL; L++) {
        int local = L - view.windowStartLine;
        String ln = (local >= 0 && local < view.linesWindow.size()) ? view.linesWindow.get(local) : "";
        if (ln == null) ln = "";
        int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
        int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
        if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
        if (L < eL) sb.append('\n');

        if (sb.length() > maxChars) return sb.substring(0, maxChars);
      }
    }
    return sb.toString();
  }

  public void selectAll(SodiumEditorView view) {
    view.autoSuggestionManager.clearActiveSuggestion();
    final boolean keyboardWasVisible = view.keyboardHeight > 0;
    if (view.wordWrapManager.isWordWrapEnabled) {
      int widthPx = Math.max(1, Math.round(view.wordWrapManager.getWrapWidth(view)));
      if (view.wordWrapManager.isWrapMetricsUsableForWindow(view, widthPx)) {
        view.wordWrapManager.cancelWrapWorkForPriority(view);
      }
    }
    view.setDisable(true);
    view.loadingCircleManager.show(true);

    view.selectionManager.setSelectAllState(true, true);
    view.selectionManager.setSelection(0, 0, 0, 0, false);
    view.popupMenuManager.hidePopup();

    if (view.sourceFile == null || view.isFileCleared) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) view.linesWindow.add("");
        if (view.windowStartLine != 0) view.windowStartLine = 0;
        view.isEof = true;
      }

      view.selectionManager.selEndLine = Math.max(0, view.windowStartLine + view.linesWindow.size() - 1);
      String lastLineText = view.getLineTextForRender(view.selectionManager.selEndLine);
      view.selectionManager.selEndChar = lastLineText.length();
      view.cursorManager.setLineAndChar(view.selectionManager.selEndLine, view.selectionManager.selEndChar);

      view.scrollManager.scrollToLineFastForSelectAll(view.selectionManager.selEndLine, view.selectionManager.selEndChar);

      view.setDisable(false);
      view.loadingCircleManager.show(false);
      view.invalidate();
      view.requestFocus();
      view.popupMenuManager.showPopupAtSelection();

      view.post(
          () -> {
            view.requestFocus();
            if (keyboardWasVisible) view.imeManager.showKeyboard();
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                    view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(view);
          });
      return;
    }

    if (view.isEof) {
      int windowLast = Math.max(0, view.windowStartLine + view.linesWindow.size() - 1);
      view.selectionManager.selEndLine = windowLast;
      String lastLineText = view.getLineTextForRender(windowLast);
      view.selectionManager.selEndChar = lastLineText.length();
      view.cursorManager.setLineAndChar(windowLast, view.selectionManager.selEndChar);

      view.scrollManager.scrollToLineFastForSelectAll(windowLast, view.selectionManager.selEndChar);

      view.setDisable(false);
      view.loadingCircleManager.show(false);
      view.invalidate();
      view.requestFocus();
      view.popupMenuManager.showPopupAtSelection();

      view.post(
          () -> {
            view.requestFocus();
            if (keyboardWasVisible) view.imeManager.showKeyboard();
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                    view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(view);
          });
      return;
    }

    Runnable goToEndUsingIndex =
        () -> {
          if (!view.fileManager.isIndexReady() || view.sourceFile == null) return;

          int fileLastLine;
          synchronized (view.lineOffsetsLock) {
            fileLastLine = Math.max(0, view.lineOffsets.length - 1);
          }

          if (view.isEof) {
            int windowLast = Math.max(0, view.windowStartLine + view.linesWindow.size() - 1);
            if (windowLast > fileLastLine) {
              view.selectionManager.selEndLine = windowLast;
              String lastLineText = view.getLineTextForRender(windowLast);
              view.selectionManager.selEndChar = lastLineText.length();
              view.cursorManager.setLineAndChar(windowLast, view.selectionManager.selEndChar);

              view.scrollManager.scrollToLineFastForSelectAll(windowLast, view.selectionManager.selEndChar);

              view.setDisable(false);
              view.loadingCircleManager.show(false);
              view.invalidate();
              view.requestFocus();
              view.popupMenuManager.showPopupAtSelection();

              view.post(
                  () -> {
                    view.requestFocus();
                    if (keyboardWasVisible) view.imeManager.showKeyboard();
                    android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                            view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(view);
                  });
              return;
            }
          }

          view.selectionManager.selEndLine = fileLastLine;
          int targetStart = Math.max(0, fileLastLine - view.prefetchLines);

          view.loadWindowAround(
              targetStart,
              () ->
                  view.post(
                      () -> {
                        String lastLineText = view.getLineTextForRender(fileLastLine);
                        view.selectionManager.selEndChar = lastLineText.length();
                        view.cursorManager.setLineAndChar(fileLastLine, view.selectionManager.selEndChar);

                        view.scrollManager.scrollToLineFastForSelectAll(fileLastLine, view.selectionManager.selEndChar);

                        view.setDisable(false);
                        view.loadingCircleManager.show(false);
                        view.invalidate();
                        view.requestFocus();
                        view.popupMenuManager.showPopupAtSelection();

                        view.post(
                            () -> {
                              view.requestFocus();
                              if (keyboardWasVisible) view.imeManager.showKeyboard();
                              android.view.inputmethod.InputMethodManager imm =
                                  (android.view.inputmethod.InputMethodManager)
                                      view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                              if (imm != null) imm.restartInput(view);
                            });
                      }));
        };

    if (view.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    if (!view.isIndexBuildingPublic() && !view.isIndexDisabledPublic()) view.ioHandler.post(view::buildFileIndex);

    view.fileManager.countTotalLines(
        totalLines -> {
          int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
          view.selectionManager.selEndLine = Math.max(0, lastLine);

          Runnable goToEndWithoutIndex =
              () -> {
                int targetStart = Math.max(0, view.selectionManager.selEndLine - view.prefetchLines);
                view.loadWindowAround(
                    targetStart,
                    () ->
                        view.post(
                            () -> {
                              String lastLineText = view.getLineTextForRender(view.selectionManager.selEndLine);
                              view.selectionManager.selEndChar = lastLineText.length();
                              view.cursorManager.setLineAndChar(view.selectionManager.selEndLine, view.selectionManager.selEndChar);

                              view.scrollManager.scrollToLineFastForSelectAll(view.selectionManager.selEndLine, view.selectionManager.selEndChar);

                              view.setDisable(false);
                              view.loadingCircleManager.show(false);
                              view.invalidate();
                              view.requestFocus();
                              view.popupMenuManager.showPopupAtSelection();

                              view.post(
                                  () -> {
                                    view.requestFocus();
                                    if (keyboardWasVisible) view.imeManager.showKeyboard();
                                    android.view.inputmethod.InputMethodManager imm =
                                        (android.view.inputmethod.InputMethodManager)
                                            view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.restartInput(view);
                                  });
                            }));
              };

          if (view.isIndexDisabledPublic()) {
            goToEndWithoutIndex.run();
            return;
          }

          final int ticket = view.undoRedo.incrementEditVersion();
          Runnable poll =
              new Runnable() {
                @Override
                public void run() {
                  if (ticket != view.undoRedo.getEditVersion()) return;

                  if (view.fileManager.getSourceFile() == null) {
                    view.setDisable(false);
                    view.loadingCircleManager.show(false);
                    view.invalidate();
                    view.popupMenuManager.showPopupAtSelection();
                    if (keyboardWasVisible) view.imeManager.showKeyboard();
                    return;
                  }

                  if (view.isIndexDisabledPublic()) {
                    goToEndWithoutIndex.run();
                  } else if (view.isIndexReady) {
                    goToEndUsingIndex.run();
                  } else {
                    view.mainHandler.postDelayed(this, 80);
                  }
                }
              };
          view.mainHandler.post(poll);
        });
  }

  public boolean isPositionInsideSelection(int line, int ch) {
    if (!hasSelection()) return false;
    int sL = selStartLine;
    int sC = selStartChar;
    int eL = selEndLine;
    int eC = selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selEndLine;
      sC = selEndChar;
      eL = selStartLine;
      eC = selStartChar;
    }
    if (comparePos(line, ch, sL, sC) < 0) return false;
    return comparePos(line, ch, eL, eC) <= 0;
  }

  private int comparePos(int aL, int aC, int bL, int bC) {
    if (aL != bL) return aL - bL;
    return aC - bC;
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }
    setSelection(
        startL,
        Math.max(0, startC),
        endL,
        Math.max(0, endC),
        false);
    setSelectAllState(false, false);
    view.popupMenuManager.hidePopup();
  }

  public void clearSelectionStateAfterDelete(SodiumEditorView view) {
    clearSelection();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
  }

  public int clampLineForSelection(SodiumEditorView view, int line) {
    if (line < 0) return 0;
    if (view.isEof) {
      int last = view.windowStartLine + view.linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  public boolean isLineSelectable(SodiumEditorView view, int line) {
    view.scrollManager.ensureLineInWindow(line, true);
    String ln = view.getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }

  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    view.setSelectionInternal(sL, sC, eL, eC);
    view.cursorManager.setPositionNoClear(cursorLine, cursorChar);
  }
}
