package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.bracket.BracketMatch;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.core.wordwrap.WordWrap;
import com.yn.sodiumeditor.core.guides.bracket.BracketGuides;
import java.util.List;
import java.util.HashMap;

public class ViewRender {

  private final SodiumEditor editor;
  public int drawBaseLine = 0;
  private final Paint selectionPaint;
  private final RectF tempRectF;
  private int frameCounter = 0;

  public ViewRender(SodiumEditor editor) {
    this.editor = editor;
    this.selectionPaint = new Paint();
    this.selectionPaint.setStyle(Paint.Style.FILL);
    this.tempRectF = new RectF();
  }

  public void drawContent(Canvas canvas) {
    int windowStart = editor.windowRender.windowStartLine;
    int windowEnd = windowStart + editor.windowRender.linesWindow.size() - 1;
    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
    
    int firstVisibleIndex = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 1;
    
    int visibleStart = firstVisibleIndex;
    int visibleEnd = lastVisibleIndex;
    if (editor.codeFold.isCodeFoldingEnabled) {
      int visibleCount = editor.codeFold.getVisibleLineCount();
      if (visibleCount > 0) {
        visibleStart = Math.max(0, Math.min(visibleStart, visibleCount - 1));
        visibleEnd = Math.max(visibleStart, Math.min(visibleEnd, visibleCount - 1));
      }
    }
    
    editor.bracketGuides.beginRenderFrame(windowStart, windowEnd, visibleStart, visibleEnd);
    editor.bracketGuides.setFrameFastScroll(fastScroll);
    
    boolean shouldDrawBracketGuides = editor.bracketGuides.isBracketGuidesEnabled && (editor.bracketGuides.showGuidesDuringFastScroll || !fastScroll);
    
    if (editor.wordWrap.isWordWrapEnabled) {
      // Corrected: wrapped drawing logic
      editor.textRender.drawlineNumbersCachedWrapped(canvas, firstVisibleIndex, lastVisibleIndex);
      // Main wrapped text rendering usually happens in TextRender but depends on implementation
      // For now, assume it's part of drawTextContent or similar
      drawContentUnfolded(canvas, shouldDrawBracketGuides); 
      editor.bracketGuides.endRenderFrameMaybeLog();
      return;
    }
    drawContentUnfolded(canvas, shouldDrawBracketGuides);
    editor.bracketGuides.endRenderFrameMaybeLog();
  }
  
  private void drawContentUnfolded(Canvas canvas, boolean drawBracketGuides) {
    final boolean drawDecorations = editor.zoom.shouldDrawDecorations();
    
    int firstVisibleIndex = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5;

    int firstVisibleLine;
    int lastVisibleLine;

    if (editor.wordWrap.isWordWrapEnabled) {
      firstVisibleLine = editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleLine = editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
    } else if (editor.codeFold.isCodeFoldingEnabled) {
      int visibleCount = editor.codeFold.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
    } else {
      firstVisibleLine = firstVisibleIndex;
      lastVisibleLine = lastVisibleIndex;
    }

    drawBaseLine = firstVisibleLine;
    float baseY = firstVisibleIndex * editor.textRender.lineHeight;
    float translateY = -editor.scroll.scrollY + baseY;

    if (editor.lineNumber.showLineNumbers) {
      canvas.drawRect(editor.lineNumber.getGutterStartX(), 0, editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth, editor.getHeight(), editor.lineNumber.gutterPaint);
      float separatorLeft = editor.textRender.isRtl ? editor.lineNumber.getGutterStartX() : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.gutterSeparatorWidth;
      canvas.drawRect(separatorLeft, 0, separatorLeft + editor.lineNumber.gutterSeparatorWidth, editor.getHeight(), editor.lineNumber.gutterSeparatorPaint);
    }

    if (editor.lineNumber.showLineNumbers) {
      editor.textRender.drawlineNumbersCachedUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (editor.codeFold.isCodeFoldingEnabled && drawDecorations) {
        editor.codeFoldRender.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    // --- Current Line Highlight ---
    if (editor.currentLineHighlight.highlightCurrentLine && !editor.selection.hasSelection) {
      float animatedIndex = editor.currentLineHighlight.getAnimatedVisualIndex();
      // Use absolute screen coordinates (not relative to firstVisibleIndex)
      float top = animatedIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
      float bottom = top + editor.textRender.lineHeight;
      canvas.drawRect(editor.lineNumber.lineNumbersGutterWidth, top, editor.getWidth(), bottom, editor.currentLineHighlight.currentLinePaint);
    }

    canvas.save();
    canvas.clipRect(editor.lineNumber.lineNumbersGutterWidth, 0, editor.getWidth(), editor.getHeight());
    canvas.translate(editor.lineNumber.lineNumbersGutterWidth - editor.scroll.getEffectiveScrollX(), translateY);
    
    boolean shouldLog = editor.DEBUG_RENDER_LOGS && (frameCounter++ % 120 == 0);
    if (shouldLog) {
        android.util.Log.d("codefold", "Visible: " + firstVisibleIndex + "-" + lastVisibleIndex);
    }

    drawTextContent(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine, drawDecorations, drawBracketGuides, shouldLog);
    canvas.restore();
    
    if (!editor.selection.hasSelection) {
        editor.caret.drawCaret(canvas);
        // Draw cursor handle when focused and no selection
        if (editor.isFocused()) {
            editor.cursorHandle.drawCursorHandle(canvas);
        }
    }
    
    if (editor.selection.hasSelection) {
        editor.selectionHandles.drawHandles(canvas);
    }

    // Draw popup if it's supposed to be shown, even if no selection
    if (editor.popup.showPopup && editor.popup.popupAlpha > 0f) {
        editor.popup.drawPopup(canvas);
    }  }

  private void drawTextContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                int firstVisibleLine, int lastVisibleLine, boolean drawDecorations, boolean drawBracketGuides, boolean shouldLog) {
    Paint selPaint = null;
    if (editor.selection.hasSelection) {
        selPaint = editor.selection.selectionPaint;
    }

    HashMap<Integer, String> directLines = null;
    if (editor.fileIO.sourceFile != null) {
      int winStart = editor.windowRender.windowStartLine;
      int winEnd = winStart + editor.windowRender.linesWindow.size() - 1;
      java.util.HashSet<Integer> needed = new java.util.HashSet<>();
      for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
          int gl = editor.codeFold.mapVisibleIndexToGlobal(v);
          needed.add(gl);
          if (editor.codeFold.isCodeFoldingEnabled) {
              CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(gl);
              if (range != null && range.collapsed) {
                  needed.add(range.endLine);
              }
          }
      }

      boolean needDirect = false;
      for (Integer gl : needed) { if (gl < winStart || gl > winEnd) { needDirect = true; break; } }

      if (needDirect) {
          editor.windowRender.directLinesTmp.clear();
          directLines = editor.windowRender.directLinesTmp;
          for (Integer gl : needed) {
              if (gl < winStart || gl > winEnd) {
                  editor.fileIO.populateDirectLinesForRange(gl, gl, directLines);
              }
          }
      }
    }

    BracketMatch bracketMatchResult = null;
    if (editor.bracketMatchManager.isBracketMatchingEnabled) {
      bracketMatchResult = editor.bracketMatchManager.findAndCacheBracketMatch(firstVisibleLine, lastVisibleLine, directLines);
    }

    if (editor.codeFold.isCodeFoldingEnabled) {
      editor.codeFoldRender.drawFoldedContent(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine,
                        directLines, selPaint, bracketMatchResult, drawDecorations, drawBracketGuides, null);
    } else {
      // Unfolded content rendering - draw selection highlights first, then text
      if (editor.selection.hasSelection && selPaint != null) {
          // Normalize selection bounds
          int selStartLine = editor.selection.selStartLine;
          int selStartChar = editor.selection.selStartChar;
          int selEndLine = editor.selection.selEndLine;
          int selEndChar = editor.selection.selEndChar;
          if (editor.editOperators.comparePos(selStartLine, selStartChar, selEndLine, selEndChar) > 0) {
              int tmpL = selStartLine;
              int tmpC = selStartChar;
              selStartLine = selEndLine;
              selStartChar = selEndChar;
              selEndLine = tmpL;
              selEndChar = tmpC;
          }

          // Canvas is already translated, so use relative coordinates
          float textStartX = editor.layout.getTextStartX() - editor.lineNumber.lineNumbersGutterWidth;

          for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
              if (i >= selStartLine && i <= selEndLine) {
                  String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
                  if (line != null) {
                      float lineTop = editor.textRender.getDrawLineTop(i);
                      float lineBottom = lineTop + editor.textRender.lineHeight;

                      int startChar = (i == selStartLine) ? selStartChar : 0;
                      int endChar = (i == selEndLine) ? selEndChar : line.length();

                      float startX = editor.textRender.measureTextWithVisualSpaces(line, 0, startChar, editor.textRender.paint);
                      float endX = editor.textRender.measureTextWithVisualSpaces(line, 0, endChar, editor.textRender.paint);

                      float left = textStartX + startX;
                      float top = lineTop;
                      float right = textStartX + endX;
                      float bottom = lineBottom;

                      // Determine which corners should be rounded based on selection boundaries
                      boolean isFirstLine = (i == selStartLine);
                      boolean isLastLine = (i == selEndLine);
                      boolean isSingleLine = (selStartLine == selEndLine);

                      if (isSingleLine) {
                          // Single line selection: round all corners
                          editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                              true, true, true, true, selPaint);
                      } else if (isFirstLine) {
                          // First line of multi-line selection: round top corners only
                          editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                              true, true, false, false, selPaint);
                      } else if (isLastLine) {
                          // Last line of multi-line selection: round bottom corners only
                          editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                              false, false, true, true, selPaint);
                      } else {
                          // Middle lines: no rounded corners
                          editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                              false, false, false, false, selPaint);
                      }
                  }
              }
          }
      }
      
      for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
          String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
          if ((line == null || line.isEmpty())
              && editor.cursor != null
              && editor.cursor.cursorLine == i
              && editor.cursor.cursorChar > 0
              && com.yn.sodiumeditor.SodiumEditor.DEBUG_RENDER_LOGS) {
              android.util.Log.d(
                  "SodiumRender",
                  "draw empty line at cursor line="
                      + i
                      + " ch="
                      + editor.cursor.cursorChar
                      + " windowStart="
                      + editor.windowRender.windowStartLine
                      + " windowSize="
                      + editor.windowRender.linesWindow.size());
          }
          float y = (i - firstVisibleLine) * editor.textRender.lineHeight + editor.textRender.lineHeight - editor.textRender.paint.descent();
          editor.textRender.drawHighlightedLine(canvas, line, i, y);
          // Draw whitespace guides and indent guides after text
          if (drawDecorations) {
              editor.textRender.drawWhitespaceGuidesForLine(canvas, line, i, y);
              editor.indentGuides.drawIndentGuidesForLine(canvas, line, i);
          }
          // Draw bracket match highlight
          if (drawDecorations && bracketMatchResult != null) {
              editor.bracketMatchManager.drawBracketMatchForLine(canvas, line, i, bracketMatchResult);
          }
      }

      // Bracket guides are drawn per-line inside drawFoldedContent when code folding is enabled.
      // Only draw batch bracket guides when code folding is NOT enabled.
      if (!editor.codeFold.isCodeFoldingEnabled && editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
          editor.bracketGuides.drawBracketGuidesForVisibleRange(canvas, firstVisibleLine, lastVisibleLine);
      }
    }
  }

  public void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine) {
      editor.colorCodeHighlight.drawColorCodeBackgrounds(canvas, line, globalLine);
  }

  public void drawSearchHighlightsForLine(Canvas canvas, String line, int globalLine, float top, float bottom) {
      editor.search.drawSearchHighlightsForLine(canvas, line, globalLine, top, bottom);
  }

  public void drawSelectionForLine(Canvas canvas, int globalLine, String line, float baseX, float width, Paint selPaint) {
      // Logic for selection highlight per line
      float lineTop = editor.textRender.getDrawLineTop(globalLine);
      float lineBottom = lineTop + editor.textRender.lineHeight;
      editor.selection.selectionHighlightRect.set(baseX, lineTop, baseX + width, lineBottom);
      canvas.drawRect(editor.selection.selectionHighlightRect, selPaint);
  }

  public SodiumEditor getEditor() { return editor; }
}
