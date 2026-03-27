package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.CodeFold;
import com.yn.sodiumeditor.core.WordWrap;
import com.yn.sodiumeditor.core.highlite.WordWrapIndicator;
import com.yn.sodiumeditor.core.highlite.BracketGuides;
import java.util.List;
import java.util.HashMap;
import com.yn.sodiumeditor.SodiumEditor;

public class ViewRender {

  // Reference to the main SodiumEditor view
  private final SodiumEditor editor;

  // Cached Paint for selection drawing
  private final Paint selectionPaint;

  // Temporary RectF for reuse during drawing
  private final RectF tempRectF;

  public ViewRender(SodiumEditor editor) {
    this.editor = editor;
    this.selectionPaint = new Paint();
    this.selectionPaint.setStyle(Paint.Style.FILL);
    this.tempRectF = new RectF();
  }

  // ============================================================================
  // Main Draw Methods
  // ============================================================================

  public void drawContent(Canvas canvas) {
    // Track current window and visible lines for bracket guides
    int windowStart = editor.textRender.windowStartLine;
    int windowEnd = windowStart + editor.textRender.linesWindow.size() - 1;
    boolean fastScroll = editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null;
    
    // Calculate visible lines (what's actually on screen)
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
    
    // Check if bracket guides can be drawn (cache is valid)
    boolean shouldDrawBracketGuides = editor.bracketGuides.isBracketGuidesEnabled
        && (editor.bracketGuides.showGuidesDuringFastScroll || !fastScroll);
    
    if (editor.wordWrap.isWordWrapEnabled) {
      drawContentWrapped(canvas, shouldDrawBracketGuides);
      editor.bracketGuides.endRenderFrameMaybeLog();
      return;
    }
    drawContentUnfolded(canvas, shouldDrawBracketGuides);
    editor.bracketGuides.endRenderFrameMaybeLog();
  }
  
  private void drawContentUnfolded(Canvas canvas, boolean drawBracketGuides) {
    final boolean drawDecorations = editor.zoom.shouldDrawDecorations();
    editor.logRender(
        "bracketGuidesFlags",
        "bracketGuides enabled=" + editor.bracketGuides.isBracketGuidesEnabled
            + " drawDecorations=" + drawDecorations
            + " drawGuides=" + drawBracketGuides
            + " fastScroll=" + (editor.scroll.scrollerIsScrolling || editor.scroll.flingStopAnimator != null),
        500);

    // Calculate visible line range
    int firstVisibleIndex = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5;

    int firstVisibleLine = firstVisibleIndex;
    int lastVisibleLine = lastVisibleIndex;
    if (editor.codeFold.isCodeFoldingEnabled) {
      int visibleCount = editor.codeFold.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = editor.codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
      editor.drawBaseLine = firstVisibleIndex;
    } else {
      editor.drawBaseLine = firstVisibleLine;
    }

    float baseY = editor.drawBaseLine * editor.textRender.lineHeight;
    float translateY = -editor.scroll.scrollY + baseY;
    if (editor.fileIO.isEof) {
      synchronized (editor.textRender.linesWindow) {
        int lastDocLine = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
        lastVisibleLine = Math.min(lastVisibleLine, lastDocLine);
      }
    }
    if (lastVisibleLine < firstVisibleLine) lastVisibleLine = firstVisibleLine;

    editor.logRender(
        "drawContent",
        "drawContent firstLine=" + firstVisibleLine
            + " lastLine=" + lastVisibleLine
            + " windowStart=" + editor.textRender.windowStartLine
            + " windowSize=" + editor.textRender.linesWindow.size()
            + " isIndexReady=" + editor.fileIO.isIndexReady
            + " isWindowLoading=" + editor.fileIO.isWindowLoading
            + " scrollX=" + editor.scroll.scrollX
            + " scrollY=" + editor.scroll.scrollY
            + " effectiveScrollX=" + editor.getEffectiveScrollX()
            + " textStartX=" + editor.getTextStartX()
            + " gutter=" + editor.lineNumber.lineNumbersGutterWidth,
        500);

    maybeKickWindowLoad(firstVisibleLine);
    maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);

    // --- 1. Draw fixed gutter background ---
    if (editor.lineNumber.showLineNumbers) {
      canvas.drawRect(
          editor.lineNumber.getGutterStartX(),
          0,
          editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth,
          editor.getHeight(),
          editor.lineNumber.gutterPaint);

      // Draw separator line
      float separatorLeft;
      if (editor.textRender.isRtl) {
        separatorLeft = editor.lineNumber.getGutterStartX();
      } else {
        separatorLeft = editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + editor.lineNumber.gutterSeparatorWidth,
          editor.getHeight(),
          editor.lineNumber.gutterSeparatorPaint);
    }

    if (editor.currentLineHighlight.highlightCurrentLineInGutter
        && editor.cursor.cursorLine >= firstVisibleLine
        && editor.cursor.cursorLine <= lastVisibleLine
        && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine))) {
      int drawIndex = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine) : editor.cursor.cursorLine;
      float top = Math.round(drawIndex * editor.textRender.lineHeight - editor.scroll.scrollY);
      float bottom = top + editor.textRender.lineHeight;
      editor.lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (editor.lineNumber.showLineNumbers) {
      editor.textRender.drawlineNumbersCachedUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (editor.codeFold.isCodeFoldingEnabled && drawDecorations) {
        editor.codeFold.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    if (editor.textRender.isRtl) {
      canvas.clipRect(0, 0, editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth, editor.getHeight());
    } else {
      canvas.clipRect(editor.lineNumber.lineNumbersGutterWidth, 0, editor.getWidth(), editor.getHeight());
    }
    canvas.translate(editor.getTextStartX() - editor.getEffectiveScrollX(), translateY);
    if (editor.zoom.pinchVisualZoomActive) {
      float pivotX = editor.zoom.pinchFocusX - (editor.getTextStartX() - editor.getEffectiveScrollX());
      float pivotY = editor.zoom.pinchFocusY - translateY;
      canvas.scale(editor.zoom.pinchVisualScale, editor.zoom.pinchVisualScale, pivotX, pivotY);
    }

    drawTextContent(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine, drawDecorations, drawBracketGuides);

    canvas.restore();
    // --- End of main text content drawing ---

    // --- 4. Draw overlays ---
    drawOverlays(canvas);
  }

  private void drawTextContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                int firstVisibleLine, int lastVisibleLine, boolean drawDecorations, boolean drawBracketGuides) {
    Paint selPaint = null;
    if (editor.selection.hasSelection) {
      editor.selection.selectionPaint.setColor(editor.selection.selectionHighlightColor);
      int baseAlpha = editor.selection.selectionPaint.getAlpha();
      int alpha = (int) (baseAlpha * Math.max(0f, Math.min(1f, editor.selection.selectionAlpha)));
      editor.selection.selectionPaint.setAlpha(alpha);
      selPaint = editor.selection.selectionPaint;
    }

    HashMap<Integer, String> directLines = null;
    if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      boolean needDirect =
          (firstVisibleLine < editor.textRender.windowStartLine)
              || (firstVisibleLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())
              || (lastVisibleLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size());

      if (needDirect) {
        editor.textRender.directLinesTmp.clear();
        directLines = editor.textRender.directLinesTmp;
        int winStart = editor.textRender.windowStartLine;
        int winEnd = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
        if (editor.codeFold.isCodeFoldingEnabled) {
          java.util.HashSet<Integer> needed = new java.util.HashSet<>();
          for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
            int gl = editor.codeFold.mapVisibleIndexToGlobal(v);
            if (gl < winStart || gl > winEnd) {
              needed.add(gl);
            }
          }
          for (Integer gl : needed) {
            editor.fileIO.populateDirectLinesForRange(gl, gl, directLines);
          }
        } else {
          if (firstVisibleLine < winStart) {
            editor.fileIO.populateDirectLinesForRange(
                firstVisibleLine, Math.min(lastVisibleLine, winStart - 1), directLines);
          }
          if (lastVisibleLine > winEnd) {
            editor.fileIO.populateDirectLinesForRange(
                Math.max(firstVisibleLine, winEnd + 1), lastVisibleLine, directLines);
          }
          if (directLines.isEmpty()
              && (firstVisibleLine < winStart
                  || firstVisibleLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
            editor.fileIO.populateDirectLinesForRange(firstVisibleLine, lastVisibleLine, directLines);
          }
        }
      }
    }

    SodiumEditor.BracketMatch bracketMatchResult = null;
    if (editor.bracketMatchManager.isBracketMatchingEnabled) {
      bracketMatchResult = editor.bracketMatchManager.findAndCacheBracketMatch(firstVisibleLine, lastVisibleLine, directLines);
    }

    int winEnd;
    int winStart;
    synchronized (editor.textRender.linesWindow) {
      winStart = editor.textRender.windowStartLine;
      winEnd = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
    }
    int prefetchForDraw = editor.zoom.isZoomGestureActive() ? 0 : editor.textRender.prefetchLines;
    int hlStart = Math.max(0, firstVisibleLine - prefetchForDraw);
    int hlEnd = Math.min(editor.getLinesCount() - 1, lastVisibleLine + prefetchForDraw);
    
    // Ensure directLines covers the full scan range if prefetch extends outside window.
    // This is required for bracket state when opening braces are off-screen but within prefetch.
    if (editor.fileIO.isIndexReady && (hlStart < winStart || hlEnd > winEnd)) {
      if (directLines == null) {
        editor.textRender.directLinesTmp.clear();
        directLines = editor.textRender.directLinesTmp;
      }
      editor.fileIO.populateDirectLinesForRange(hlStart, hlEnd, directLines);
    } else if (directLines != null && editor.fileIO.isIndexReady) {
      // If directLines already exists, keep it filled for the scan range
      editor.fileIO.populateDirectLinesForRange(hlStart, hlEnd, directLines);
    }

    maybeEnsureHighlightCacheForRange(Math.max(editor.textRender.windowStartLine, hlStart), Math.min(winEnd, hlEnd), directLines);
    // Ensure bracket guide checkpoints are built for synchronous rendering
    // This is needed for efficient bracket guide state calculation during rendering
    if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
      editor.bracketGuides.ensureBracketGuideCheckpointsUpTo(hlEnd, directLines, null);
      editor.bracketGuides.ensureBracketGuideSpanCacheForWindow(hlStart, hlEnd, firstVisibleLine, lastVisibleLine, directLines);
    }

    // Calculate initial bracket guide state for synchronous rendering
    // This allows bracket guides to be drawn together with lines using the same algorithm
    BracketGuides.BracketGuideState initialBracketState = null;

    if (editor.codeFold.isCodeFoldingEnabled) {
      drawFoldedContent(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine,
                       directLines, selPaint, bracketMatchResult, drawDecorations, drawBracketGuides, initialBracketState);
    } else {
      drawUnfoldedContent(canvas, firstVisibleLine, lastVisibleLine, directLines, selPaint, bracketMatchResult, drawDecorations, drawBracketGuides, initialBracketState);
    }

    drawCursorAndHandles(canvas, firstVisibleLine, lastVisibleLine, directLines);
  }

  private void drawFoldedContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                  int firstVisibleLine, int lastVisibleLine,
                                  HashMap<Integer, String> directLines, Paint selPaint,
                                  SodiumEditor.BracketMatch bracketMatchResult, boolean drawDecorations, boolean drawBracketGuides,
                                  BracketGuides.BracketGuideState initialBracketState) {
    if (editor.indentGuides.indentGuideIntervalsDirty) editor.indentGuides.rebuildIndentGuideIntervalsIfNeeded();

    // Track bracket guide state for synchronous rendering
    BracketGuides.BracketGuideState bracketState = initialBracketState;
    int windowStart = editor.textRender.windowStartLine;
    int windowEnd = windowStart + editor.textRender.linesWindow.size() - 1;

    for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
      int globalLine = editor.codeFold.mapVisibleIndexToGlobal(v);
      String line = getLineTextForRenderWithDirect(globalLine, directLines);
      CodeFold.FoldRange foldRange = editor.codeFold.getFoldRangeAtStart(globalLine);
      boolean isFoldStart = (foldRange != null);
      float lineBaseX = editor.textRender.isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
      float lineWidth =
          editor.textRender.isRtl
              ? measureHighlightedSegmentWidth(
                  line, globalLine, 0, getLogicalLineLength(globalLine, line))
              : 0f;

      // Highlight the current line, only if there is no selection
      if (editor.currentLineHighlight.highlightCurrentLine && globalLine == editor.cursor.cursorLine && !editor.selection.hasSelection) {
        float top = Math.round(editor.textRender.getDrawLineTop(globalLine));
        float bottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
        float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
        float left = viewLeft + editor.getEffectiveScrollX() - editor.getTextStartX();
        float right = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();
        canvas.drawRect(left, top, right, bottom, editor.currentLineHighlight.currentLinePaint);
      }

      if (editor.selection.hasSelection && selPaint != null) {
        drawSelectionForLine(canvas, globalLine, line, lineBaseX, lineWidth, selPaint);
      }

      float y = Math.round(editor.textRender.getDrawLineTop(globalLine) + editor.textRender.lineHeight - editor.textRender.paint.descent());
      editor.textRender.paint.setUnderlineText(false);

      canvas.save();
      if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

      // Draw color code backgrounds underneath the text
      drawColorCodeBackgrounds(canvas, line, globalLine);

      if (isFoldStart) {
        // For folded lines, we need to process all hidden lines to maintain correct bracket state
        if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
          // Calculate bracket state if needed
          if (bracketState == null) {
            bracketState = editor.bracketGuides.calculateBracketGuideStateFromWindowStart(
                globalLine, windowStart, windowEnd, directLines);
          }
          // Get tokens from state BEFORE processing this line
          editor.bracketGuides.drawBracketGuidesForLineFromStack(canvas, line, globalLine, bracketState.stack);
          // Then: update state for THIS line
          bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(line, globalLine, bracketState);
        }
        
        // Process all hidden lines to maintain correct bracket state
        if (foldRange != null && foldRange.endLine > globalLine) {
          for (int hiddenLine = globalLine + 1; hiddenLine <= foldRange.endLine; hiddenLine++) {
            String hiddenLineText = getLineTextForRenderWithDirect(hiddenLine, directLines);
            bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(hiddenLineText, hiddenLine, bracketState);
          }
        }
        
        if (drawDecorations) {
          editor.textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          editor.indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
        }
        editor.codeFold.drawFoldedLine(canvas, line, globalLine);
        canvas.restore();
        continue;
      }

      float lineTop = Math.round(editor.textRender.getDrawLineTop(globalLine));
      float lineBottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
      drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
      editor.textRender.drawHighlightedLine(canvas, line, globalLine, y);
      if (drawDecorations) {
        editor.textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
        editor.indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
      }

      // Draw auto-completion suggestion
      editor.autoCompletion.drawAutoSuggestion(canvas, line, globalLine, y);

      // Draw bracket guides synchronously with line rendering
      if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
        // Calculate bracket state if needed
        if (bracketState == null) {
          bracketState = editor.bracketGuides.calculateBracketGuideStateFromWindowStart(
              globalLine, windowStart, windowEnd, directLines);
        }

        // Get tokens from the state BEFORE processing this line
        // This gives us the bracket guides that should be drawn for this line
        editor.bracketGuides.drawBracketGuidesForLineFromStack(canvas, line, globalLine, bracketState.stack);

        // Update state for next line (process this line's brackets)
        bracketState = editor.bracketGuides.calculateBracketGuideStateForLine(line, globalLine, bracketState);
      }

      if (drawDecorations) {
        editor.bracketMatchManager.drawBracketMatchForLine(canvas, line, globalLine, bracketMatchResult);
      }
      canvas.restore();
    }
  }

  private void drawUnfoldedContent(Canvas canvas, int firstVisibleLine, int lastVisibleLine,
                                    HashMap<Integer, String> directLines, Paint selPaint,
                                    SodiumEditor.BracketMatch bracketMatchResult, boolean drawDecorations, boolean drawBracketGuides,
                                    BracketGuides.BracketGuideState initialBracketState) {
    if (editor.indentGuides.indentGuideIntervalsDirty) editor.indentGuides.rebuildIndentGuideIntervalsIfNeeded();

    // Fast span-based guides (only for unfolded content)
    if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides && !editor.codeFold.isCodeFoldingEnabled) {
      editor.bracketGuides.drawBracketGuidesForVisibleRange(canvas, firstVisibleLine, lastVisibleLine);
    }
    
    for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
      String line = getLineTextForRenderWithDirect(globalLine, directLines);
      float lineBaseX = editor.textRender.isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
      float lineWidth =
          editor.textRender.isRtl
              ? measureHighlightedSegmentWidth(
                  line, globalLine, 0, getLogicalLineLength(globalLine, line))
              : 0f;

      // Highlight the current line, only if there is no selection
      if (editor.currentLineHighlight.highlightCurrentLine && globalLine == editor.cursor.cursorLine && !editor.selection.hasSelection) {
        float top = Math.round(editor.textRender.getDrawLineTop(globalLine));
        float bottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
        float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
        float left = viewLeft + editor.getEffectiveScrollX() - editor.getTextStartX();
        float right = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();
        canvas.drawRect(left, top, right, bottom, editor.currentLineHighlight.currentLinePaint);
      }

      if (editor.selection.hasSelection && selPaint != null) {
        drawSelectionForLine(canvas, globalLine, line, lineBaseX, lineWidth, selPaint);
      }

      float y = Math.round(editor.textRender.getDrawLineTop(globalLine) + editor.textRender.lineHeight - editor.textRender.paint.descent());
      editor.textRender.paint.setUnderlineText(false);

      canvas.save();
      if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

      // Draw color code backgrounds underneath the text
      drawColorCodeBackgrounds(canvas, line, globalLine);

      float lineTop = Math.round(editor.textRender.getDrawLineTop(globalLine));
      float lineBottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
      drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
      editor.textRender.drawHighlightedLine(canvas, line, globalLine, y);
      if (drawDecorations) {
        editor.textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
        editor.indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
      }

      // Draw auto-completion suggestion
      editor.autoCompletion.drawAutoSuggestion(canvas, line, globalLine, y);

      if (drawDecorations) {
        editor.bracketMatchManager.drawBracketMatchForLine(canvas, line, globalLine, bracketMatchResult);
      }
      canvas.restore();
    }
  }

  private void drawSelectionForLine(Canvas canvas, int globalLine, String line, float lineBaseX, float lineWidth, Paint selPaint) {
    float top = Math.round(editor.textRender.getDrawLineTop(globalLine));
    float bottom = Math.round(editor.textRender.getDrawLineBottom(globalLine));
    float fullRight =
        Math.max(editor.textRender.currentMaxWindowLineWidth, editor.scroll.scrollX + (editor.getWidth() - editor.getTextStartX()));
    if (editor.textRender.isRtl) {
      fullRight = lineBaseX + lineWidth;
    }

    if (editor.selection.isSelectAllActive) {
      boolean lineExists =
          (editor.fileIO.isEof) ? (globalLine <= editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1) : true;
      if (lineExists) {
        boolean roundTop = globalLine == editor.selection.selStartLine;
        boolean roundBottom = globalLine == editor.selection.selEndLine;
        float leftSel = editor.textRender.isRtl ? lineBaseX : 0f;
        float rightSel = editor.textRender.isRtl ? (lineBaseX + lineWidth) : fullRight;
        editor.onTouch.drawSelectionSegment(
            canvas,
            leftSel,
            top,
            rightSel,
            bottom,
            roundTop,
            roundTop,
            roundBottom,
            roundBottom,
            selPaint);
      }
    } else {
      int startLine, endLine, startChar, endChar;
      if (editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, editor.selection.selEndLine, editor.selection.selEndChar) <= 0) {
        startLine = editor.selection.selStartLine;
        startChar = editor.selection.selStartChar;
        endLine = editor.selection.selEndLine;
        endChar = editor.selection.selEndChar;
      } else {
        startLine = editor.selection.selEndLine;
        startChar = editor.selection.selEndChar;
        endLine = editor.selection.selStartLine;
        endChar = editor.selection.selStartChar;
      }

      if (globalLine >= startLine && globalLine <= endLine) {
        float left, right;
        if (editor.textRender.isRtl) {
          float lineLeft = lineBaseX;
          float lineRight = lineBaseX + lineWidth;
          if (startLine == endLine) {
            float x1 = editor.getCaretXForLine(line, globalLine, Math.min(startChar, line.length()));
            float x2 = editor.getCaretXForLine(line, globalLine, Math.min(endChar, line.length()));
            left = Math.min(x1, x2);
            right = Math.max(x1, x2);
          } else if (globalLine == startLine) {
            float x = editor.getCaretXForLine(line, globalLine, Math.min(startChar, line.length()));
            left = lineLeft;
            right = x;
          } else if (globalLine == endLine) {
            float x = editor.getCaretXForLine(line, globalLine, Math.min(endChar, line.length()));
            left = x;
            right = lineRight;
          } else {
            left = lineLeft;
            right = lineRight;
          }
        } else {
          if (startLine == endLine) {
            left = editor.measureText(line, Math.min(startChar, line.length()), globalLine);
            right = editor.measureText(line, Math.min(endChar, line.length()), globalLine);
          } else {
            if (globalLine == startLine) {
              left = editor.measureText(line, Math.min(startChar, line.length()), globalLine);
              right = fullRight;
            } else if (globalLine == endLine) {
              left = 0;
              right = editor.measureText(line, Math.min(endChar, line.length()), globalLine);
              if (line.length() == 0) right = fullRight;
            } else {
              left = 0;
              right = fullRight;
            }
          }
        }
        if (right > left) {
          boolean isStart = globalLine == startLine;
          boolean isEnd = globalLine == endLine;
          boolean roundTop = isStart;
          boolean roundBottom = isEnd;
          if (!isStart && !isEnd) {
            roundTop = false;
            roundBottom = false;
          } else if (isStart && !isEnd) {
            roundBottom = false;
          } else if (!isStart && isEnd) {
            roundTop = false;
          }
          editor.onTouch.drawSelectionSegment(
              canvas,
              left,
              top,
              right,
              bottom,
              roundTop,
              roundTop,
              roundBottom,
              roundBottom,
              selPaint);
        }
      }
    }
  }

  private void drawCursorAndHandles(Canvas canvas, int firstVisibleLine, int lastVisibleLine, HashMap<Integer, String> directLines) {
    if (editor.isFocused()
        && !editor.isReadOnly
        && !editor.selection.hasSelection
        && editor.cursor.cursorLine >= firstVisibleLine
        && editor.cursor.cursorLine <= lastVisibleLine
        && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine))) {
      String cursorLineText = getLineTextForRender(editor.cursor.cursorLine);
      int safeChar = Math.min(editor.cursor.cursorChar, getLogicalLineLength(editor.cursor.cursorLine, cursorLineText));
      float cursorX = editor.getCaretXForLine(cursorLineText, editor.cursor.cursorLine, safeChar);
      float cursorY = editor.textRender.getDrawLineTop(editor.cursor.cursorLine);
      editor.cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
      float drawX = editor.cursorAnimation.cursorDrawX;
      float drawY = editor.cursorAnimation.cursorDrawY;
      if (editor.caret.isCursorVisible) {
        editor.caret.caretPaint.setColor(editor.caret.caretColor);
        editor.caret.caretPaint.setStrokeWidth(editor.cursor.cursorWidth);
        canvas.drawLine(drawX, drawY, drawX, drawY + editor.textRender.lineHeight, editor.caret.caretPaint);
      }
      editor.selectionHandles.handlePaint.setColor(editor.cursorHandle.cursorHandleColor);
      editor.selectionHandles.handlePaint.setAlpha(255);
      drawTeardropHandle(canvas, drawX, drawY + editor.textRender.lineHeight, editor.selectionHandles.handlePaint);
      editor.cursorHandle.cursorHandleRect.set(
          drawX - editor.selectionHandles.handleRadius,
          drawY + editor.textRender.lineHeight,
          drawX + editor.selectionHandles.handleRadius,
          drawY + editor.textRender.lineHeight + editor.selectionHandles.handleRadius * 2);
    } else if (editor.isFocused()
        && !editor.isReadOnly
        && !editor.selection.hasSelection
        && editor.codeFold.isCodeFoldingEnabled
        && editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine)) {
      CodeFold.FoldRange hiddenRange = editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
      if (hiddenRange != null) {
        String startLineText = getLineTextForRender(hiddenRange.startLine);
        String endLineText = getLineTextForRender(editor.cursor.cursorLine);
        if (startLineText != null && endLineText != null) {
          int prefixEnd;
          if (hiddenRange.isBlockComment) {
            prefixEnd = Math.min(hiddenRange.openCharIndex + 2, startLineText.length());
          } else if (hiddenRange.isIndentFold) {
            prefixEnd = startLineText.length();
          } else {
            prefixEnd = Math.min(hiddenRange.openCharIndex + 1, startLineText.length());
          }
          float prefixWidth =
              editor.measureHighlightedSegmentWidth(startLineText, hiddenRange.startLine, 0, prefixEnd);
          float placeholderWidth =
              Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
          float closeWidth =
              hiddenRange.isBlockComment
                  ? editor.textRender.paint.measureText("*/")
                  : editor.textRender.paint.measureText(String.valueOf(hiddenRange.closeChar));
          int closeIdx = editor.codeFold.resolveCloseCharIndex(hiddenRange, endLineText);
          int suffixStart =
              hiddenRange.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : endLineText.length())
                  : (closeIdx >= 0 ? closeIdx + 1 : endLineText.length());
          int caretChar = Math.max(suffixStart, editor.cursor.cursorChar);
          float suffixWidth =
              editor.measureHighlightedSegmentWidth(
                  endLineText,
                  editor.cursor.cursorLine,
                  Math.min(suffixStart, endLineText.length()),
                  Math.min(caretChar, endLineText.length()));
          float cursorX = prefixWidth + placeholderWidth + closeWidth + suffixWidth;
          float cursorY = editor.textRender.getDrawLineTop(hiddenRange.startLine);
          editor.cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
          float drawX = editor.cursorAnimation.cursorDrawX;
          float drawY = editor.cursorAnimation.cursorDrawY;
          if (editor.caret.isCursorVisible) {
            editor.caret.caretPaint.setColor(editor.caret.caretColor);
            editor.caret.caretPaint.setStrokeWidth(editor.cursor.cursorWidth);
            canvas.drawLine(drawX, drawY, drawX, drawY + editor.textRender.lineHeight, editor.caret.caretPaint);
          }
          editor.selectionHandles.handlePaint.setColor(editor.cursorHandle.cursorHandleColor);
          editor.selectionHandles.handlePaint.setAlpha(255);
          drawTeardropHandle(canvas, drawX, drawY + editor.textRender.lineHeight, editor.selectionHandles.handlePaint);
          editor.cursorHandle.cursorHandleRect.set(
              drawX - editor.selectionHandles.handleRadius,
              drawY + editor.textRender.lineHeight,
              drawX + editor.selectionHandles.handleRadius,
              drawY + editor.textRender.lineHeight + editor.selectionHandles.handleRadius * 2);
        }
      }
    }

    if (editor.selection.hasSelection) {
      editor.selectionHandles.handlePaint.setColor(editor.selectionHandles.selectionHandleColor);
      int hAlpha = (int) (255f * Math.max(0f, Math.min(1f, editor.selection.handleAlpha)));
      editor.selectionHandles.handlePaint.setAlpha(hAlpha);
      if (editor.selection.selStartLine >= firstVisibleLine
          && editor.selection.selStartLine <= lastVisibleLine
          && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.selection.selStartLine))) {
        String startLineText = getLineTextForRender(editor.selection.selStartLine);
        float startX =
            editor.getCaretXForLine(
                startLineText,
                editor.selection.selStartLine,
                Math.min(editor.selection.selStartChar, getLogicalLineLength(editor.selection.selStartLine, startLineText)));
        float startY = editor.textRender.getDrawLineTop(editor.selection.selStartLine) + editor.textRender.lineHeight;
        float[] startAnim = editor.selectionHandles.getAnimatedHandlePosition(true, startX, startY);
        drawTeardropHandle(canvas, startAnim[0], startAnim[1], editor.selectionHandles.handlePaint);
        if (editor.textRender.isRtl) {
          editor.selectionHandles.rightHandleRect.set(
              startAnim[0] - editor.selectionHandles.handleRadius,
              startAnim[1],
              startAnim[0] + editor.selectionHandles.handleRadius,
              startAnim[1] + editor.selectionHandles.handleRadius * 2);
        } else {
          editor.selectionHandles.leftHandleRect.set(
              startAnim[0] - editor.selectionHandles.handleRadius,
              startAnim[1],
              startAnim[0] + editor.selectionHandles.handleRadius,
              startAnim[1] + editor.selectionHandles.handleRadius * 2);
        }
      } else {
        if (editor.textRender.isRtl) editor.selectionHandles.rightHandleRect.setEmpty();
        else editor.selectionHandles.leftHandleRect.setEmpty();
      }
      if (editor.selection.selEndLine >= firstVisibleLine
          && editor.selection.selEndLine <= lastVisibleLine
          && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.selection.selEndLine))) {
        String endLineText = getLineTextForRender(editor.selection.selEndLine);
        float endX =
            editor.getCaretXForLine(
                endLineText,
                editor.selection.selEndLine,
                Math.min(editor.selection.selEndChar, getLogicalLineLength(editor.selection.selEndLine, endLineText)));
        float endY = editor.textRender.getDrawLineTop(editor.selection.selEndLine) + editor.textRender.lineHeight;
        float[] endAnim = editor.selectionHandles.getAnimatedHandlePosition(false, endX, endY);
        drawTeardropHandle(canvas, endAnim[0], endAnim[1], editor.selectionHandles.handlePaint);
        if (editor.textRender.isRtl) {
          editor.selectionHandles.leftHandleRect.set(
              endAnim[0] - editor.selectionHandles.handleRadius,
              endAnim[1],
              endAnim[0] + editor.selectionHandles.handleRadius,
              endAnim[1] + editor.selectionHandles.handleRadius * 2);
        } else {
          editor.selectionHandles.rightHandleRect.set(
              endAnim[0] - editor.selectionHandles.handleRadius,
              endAnim[1],
              endAnim[0] + editor.selectionHandles.handleRadius,
              endAnim[1] + editor.selectionHandles.handleRadius * 2);
        }
      } else {
        if (editor.textRender.isRtl) editor.selectionHandles.leftHandleRect.setEmpty();
        else editor.selectionHandles.rightHandleRect.setEmpty();
      }
    }
  }

  private void drawOverlays(Canvas canvas) {
    editor.popup.drawPopup(canvas);

    if (editor.loadingCircle.showLoadingCircle) {
      editor.loadingCircle.loadingCirclePaint.setColor(editor.loadingCircle.loadingCircleColor);
      editor.loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = editor.getWidth() / 2f;
      float centerY = editor.getHeight() / 2f;
      canvas.save();
      canvas.rotate(editor.loadingCircle.loadingCircleRotation, centerX, centerY);
      editor.loadingCircle.loadingCircleRect.set(
          centerX - editor.loadingCircle.loadingCircleRadius,
          centerY - editor.loadingCircle.loadingCircleRadius,
          centerX + editor.loadingCircle.loadingCircleRadius,
          centerY + editor.loadingCircle.loadingCircleRadius);
      canvas.drawArc(editor.loadingCircle.loadingCircleRect, 0, 270, false, editor.loadingCircle.loadingCirclePaint);
      canvas.restore();
    }
  }

  // ============================================================================
  // Wrapped Content Draw Methods
  // ============================================================================

  public void drawContentWrapped(Canvas canvas, boolean drawBracketGuides) {
    int wrapWidthPx = Math.max(1, Math.round(editor.wordWrap.getWrapWidth()));
    final boolean drawDecorations = editor.zoom.shouldDrawDecorations();
    if (!editor.zoom.isZoomGestureActive()) {
      editor.applyPendingWrapPrefixUpdateIfAny();
    }
    if (editor.wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) {
      drawContentWrappedFallback(canvas, wrapWidthPx, drawBracketGuides);
      return;
    }
    if (!editor.wordWrap.isWrapMetricsUsableForWindow(wrapWidthPx)) {
      if (!editor.wordWrap.wrapMetricsReady || editor.wordWrap.wrapMetricsWidth != wrapWidthPx) {
        editor.wordWrap.scheduleWrapMetricsSnapshotIfNeeded(wrapWidthPx);
      }
      if (editor.wordWrap.wrapPrefixValidUpToLine < editor.getWindowEndLine()) {
        editor.wordWrap.requestWrapPrefixRebuild();
      }
      drawContentWrappedFallback(canvas, editor.wordWrap.wrapWidthPx, drawBracketGuides);
      return;
    }
    int totalLines = editor.getLinesCount();
    if (totalLines <= 0) totalLines = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
    if (totalLines <= 0) totalLines = 1;

    int totalVisual = editor.wordWrap.getTotalVisualLineCount();
    int firstVisualIndex = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
    int lastVisualIndex =
        Math.min(totalVisual - 1, firstVisualIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5);
    if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

    WordWrap.VisualLinePosition firstPos = editor.wordWrap.getVisualPositionForIndex(firstVisualIndex);
    WordWrap.VisualLinePosition lastPos = editor.wordWrap.getVisualPositionForIndex(lastVisualIndex);

    maybeKickWindowLoad(firstPos.line);

    HashMap<Integer, String> directLines = null;
    if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      editor.textRender.directLinesTmp.clear();
      directLines = editor.textRender.directLinesTmp;
      int rangeStart = Math.max(0, firstPos.line - 1);
      int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
      editor.fileIO.populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
    }

    boolean patched = false;
    if (!editor.zoom.isZoomGestureActive()) {
      patched =
          editor.wordWrap.patchWrapMetricsForVisualRange(
              firstVisualIndex, lastVisualIndex, directLines, editor.wordWrap.wrapWidthPx);
    }
    if (patched) {
      totalLines = editor.getLinesCount();
      if (totalLines <= 0) totalLines = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
      if (totalLines <= 0) totalLines = 1;

      totalVisual = editor.wordWrap.getTotalVisualLineCount();
      firstVisualIndex = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
      lastVisualIndex =
          Math.min(
              totalVisual - 1, firstVisualIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5);
      if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

      firstPos = editor.wordWrap.getVisualPositionForIndex(firstVisualIndex);
      lastPos = editor.wordWrap.getVisualPositionForIndex(lastVisualIndex);
      maybeKickWindowLoad(firstPos.line);

      if (directLines != null) {
        editor.textRender.directLinesTmp.clear();
        int rangeStart = Math.max(0, firstPos.line - 1);
        int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
        editor.fileIO.populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
      }
    }

    float baseY = firstVisualIndex * editor.textRender.lineHeight;
    float translateY = -editor.scroll.scrollY + baseY;

    // --- 1. Draw fixed gutter background ---
    if (editor.lineNumber.showLineNumbers) {
      canvas.drawRect(
          editor.lineNumber.getGutterStartX(),
          0,
          editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth,
          editor.getHeight(),
          editor.lineNumber.gutterPaint);

      float separatorLeft;
      if (editor.textRender.isRtl) {
        separatorLeft = editor.lineNumber.getGutterStartX();
      } else {
        separatorLeft = editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + editor.lineNumber.gutterSeparatorWidth,
          editor.getHeight(),
          editor.lineNumber.gutterSeparatorPaint);
    }

    if (editor.currentLineHighlight.highlightCurrentLineInGutter
        && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine))) {
      int currentVisualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
      String cursorLineText = getLineTextForRender(editor.cursor.cursorLine);
      int[] starts = editor.wordWrap.getWrapStartsForLine(editor.cursor.cursorLine, cursorLineText);
      int segCount = Math.max(1, starts.length);
      int lastVisualIndexForLine = currentVisualIndex + segCount - 1;
      int drawFrom = Math.max(firstVisualIndex, currentVisualIndex);
      int drawTo = Math.min(lastVisualIndex, lastVisualIndexForLine);
      for (int v = drawFrom; v <= drawTo; v++) {
        float top = Math.round(v * editor.textRender.lineHeight - editor.scroll.scrollY);
        float bottom = top + editor.textRender.lineHeight;
        editor.lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (editor.lineNumber.showLineNumbers) {
      editor.textRender.drawlineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    if (editor.textRender.isRtl) {
      canvas.clipRect(0, 0, editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth, editor.getHeight());
    } else {
      canvas.clipRect(editor.lineNumber.lineNumbersGutterWidth, 0, editor.getWidth(), editor.getHeight());
    }
    canvas.translate(editor.getTextStartX() - editor.getEffectiveScrollX(), translateY);
    if (editor.zoom.pinchVisualZoomActive) {
      float pivotX = editor.zoom.pinchFocusX - (editor.getTextStartX() - editor.getEffectiveScrollX());
      float pivotY = editor.zoom.pinchFocusY - translateY;
      canvas.scale(editor.zoom.pinchVisualScale, editor.zoom.pinchVisualScale, pivotX, pivotY);
    }

    drawWrappedTextContent(canvas, firstVisualIndex, lastVisualIndex, directLines);

    canvas.restore();

    drawWrappedOverlays(canvas);
  }

  private void drawWrappedTextContent(Canvas canvas, int firstVisualIndex, int lastVisualIndex, HashMap<Integer, String> directLines) {
    Paint selPaint = null;
    if (editor.selection.hasSelection) {
      editor.selection.selectionPaint.setColor(editor.selection.selectionHighlightColor);
      selPaint = editor.selection.selectionPaint;
    }

    SodiumEditor.BracketMatch bracketMatchResult = null;
    if (editor.bracketMatchManager.isBracketMatchingEnabled) {
      WordWrap.VisualLinePosition firstPos = editor.wordWrap.getVisualPositionForIndex(firstVisualIndex);
      WordWrap.VisualLinePosition lastPos = editor.wordWrap.getVisualPositionForIndex(lastVisualIndex);

      // Calculate hlStart and hlEnd based on global line numbers using the now-defined firstPos and lastPos
      int hlStart = Math.max(0, firstPos.line - editor.textRender.prefetchLines);
      int hlEnd = Math.min(editor.getLinesCount() - 1, lastPos.line + editor.textRender.prefetchLines);

      editor.bracketGuides.ensureBracketGuideCacheForWindow(hlStart, hlEnd, firstPos.line, lastPos.line, directLines);
      int rangeStart = Math.max(0, firstPos.line - 1);
      int rangeEnd = Math.min(editor.getLinesCount() - 1, lastPos.line + 1);
      bracketMatchResult = editor.bracketMatchManager.findAndCacheBracketMatch(rangeStart, rangeEnd, directLines);
    }

    int startLine = editor.selection.selStartLine;
    int startChar = editor.selection.selStartChar;
    int endLine = editor.selection.selEndLine;
    int endChar = editor.selection.selEndChar;
    if (editor.selection.hasSelection && editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
      startLine = editor.selection.selEndLine;
      startChar = editor.selection.selEndChar;
      endLine = editor.selection.selStartLine;
      endChar = editor.selection.selStartChar;
    }

    for (int v = firstVisualIndex; v <= lastVisualIndex; v++) {
      WordWrap.VisualLinePosition pos = editor.wordWrap.getVisualPositionForIndex(v);
      String line = getLineTextForRenderWithDirect(pos.line, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(pos.line, line);

      if (pos.segment >= starts.length) continue;

      int segStart = editor.wordWrap.getWrapSegmentStart(starts, pos.segment);
      int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, pos.segment, line.length());
      float segBaseX = editor.textRender.isRtl ? getRtlSegmentBaseX(line, pos.line, segStart, segEnd) : 0f;

      float top = Math.round((v - firstVisualIndex) * editor.textRender.lineHeight);
      float bottom = top + editor.textRender.lineHeight;
      float y = Math.round(top + editor.textRender.lineHeight - editor.textRender.paint.descent());

      if (editor.currentLineHighlight.highlightCurrentLine && pos.line == editor.cursor.cursorLine && !editor.selection.hasSelection) {
        canvas.drawRect(
            -editor.textRender.paddingLeft, top, Math.max(editor.wordWrap.getWrapWidth(), editor.getWidth()), bottom, editor.currentLineHighlight.currentLinePaint);
      }

      if (editor.selection.hasSelection && selPaint != null) {
        drawWrappedSelection(canvas, pos, line, segStart, segEnd, segBaseX, startLine, startChar, endLine, endChar, selPaint, top, bottom);
      }

      int segDrawEnd = segEnd;
      if (editor.wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        segDrawEnd = clampSegmentEndForWrapIndicator(line, segStart, segEnd, editor.wordWrap.wrapWidthPx);
      }
      canvas.save();
      if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
      drawSearchHighlightsForSegment(canvas, line, pos.line, segStart, segDrawEnd, top, bottom);
      editor.textRender.drawHighlightedLineSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      editor.errorUnderline.drawErrorUnderlinesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      editor.textRender.drawDeleteAnimationForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      if (editor.zoom.shouldDrawDecorations()) {
        editor.textRender.drawWhitespaceGuidesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
        editor.bracketMatchManager.drawBracketMatchForSegment(canvas, line, pos.line, segStart, segEnd, segBaseX, top, bracketMatchResult);
      }
      editor.autoCompletion.drawAutoSuggestionWrapped(canvas, line, pos.line, segStart, segDrawEnd, v, y);
      if (editor.wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        float indicatorX =
            editor.textRender.isRtl
                ? editor.wordWrap.indicator.wordWrapIndicatorPadPx
                : Math.max(
                    editor.wordWrap.indicator.wordWrapIndicatorPadPx,
                    editor.wordWrap.wrapWidthPx - editor.wordWrap.indicator.wordWrapIndicatorWidth - editor.wordWrap.indicator.wordWrapIndicatorPadPx);
        canvas.drawText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, editor.wordWrap.indicator.wordWrapIndicatorPaint);
      }
      canvas.restore();
    }

    drawWrappedCursorAndHandles(canvas, firstVisualIndex, lastVisualIndex, directLines);
  }

  private void drawWrappedSelection(Canvas canvas, WordWrap.VisualLinePosition pos, String line,
                                     int segStart, int segEnd, float segBaseX,
                                     int startLine, int startChar, int endLine, int endChar, Paint selPaint,
                                     float top, float bottom) {
    if (pos.line >= startLine && pos.line <= endLine) {
      int lineSelStart = (pos.line == startLine) ? startChar : 0;
      int lineSelEnd = (pos.line == endLine) ? endChar : line.length();
      int segSelStart = Math.max(segStart, lineSelStart);
      int segSelEnd = Math.min(segEnd, lineSelEnd);
      if (segSelEnd > segSelStart) {
        float left;
        float right;
        if (editor.textRender.isRtl) {
          float x1 = editor.getCaretXForSegment(line, pos.line, segStart, segEnd, Math.min(segSelStart, line.length()));
          float x2 = editor.getCaretXForSegment(line, pos.line, segStart, segEnd, Math.min(segSelEnd, line.length()));
          left = Math.min(x1, x2);
          right = Math.max(x1, x2);
        } else {
          boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
          float leftRel = fullSegmentSelected ? 0f : editor.measureTextWithVisualSpaces(line, segStart, segSelStart, editor.textRender.paint);
          float rightRel = fullSegmentSelected ? Math.max(0f, editor.wordWrap.wrapWidthPx) : leftRel + editor.measureTextWithVisualSpaces(line, segSelStart, segSelEnd, editor.textRender.paint);
          left = leftRel + segBaseX;
          right = rightRel + segBaseX;
        }
        boolean roundTop = (pos.line == startLine && segSelStart == startChar);
        boolean roundBottom = (pos.line == endLine && segSelEnd == endChar);
        editor.onTouch.drawSelectionSegment(
            canvas,
            left,
            top,
            right,
            bottom,
            roundTop,
            roundTop,
            roundBottom,
            roundBottom,
            selPaint);
      }
    }
  }

  private void drawWrappedCursorAndHandles(Canvas canvas, int firstVisualIndex, int lastVisualIndex, HashMap<Integer, String> directLines) {
    if (editor.isFocused() && !editor.isReadOnly && !editor.selection.hasSelection) {
      int cursorVisualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
      if (cursorVisualIndex >= firstVisualIndex && cursorVisualIndex <= lastVisualIndex) {
        String cursorLineText = getLineTextForRenderWithDirect(editor.cursor.cursorLine, directLines);
        int[] starts = editor.wordWrap.getWrapStartsForLine(editor.cursor.cursorLine, cursorLineText);
        int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.cursor.cursorChar);
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, cursorLineText.length());
        int safeChar = Math.min(editor.cursor.cursorChar, cursorLineText.length());
        float cursorX = editor.getCaretXForSegment(cursorLineText, editor.cursor.cursorLine, segStart, segEnd, safeChar);
        float cursorY = (cursorVisualIndex - firstVisualIndex) * editor.textRender.lineHeight;
        editor.cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
        float drawX = editor.cursorAnimation.cursorDrawX;
        float drawY = editor.cursorAnimation.cursorDrawY;
        if (editor.caret.isCursorVisible) {
          editor.caret.caretPaint.setColor(editor.caret.caretColor);
          editor.caret.caretPaint.setStrokeWidth(editor.cursor.cursorWidth);
          canvas.drawLine(drawX, drawY, drawX, drawY + editor.textRender.lineHeight, editor.caret.caretPaint);
        }
        editor.selectionHandles.handlePaint.setColor(editor.cursorHandle.cursorHandleColor);
        drawTeardropHandle(canvas, drawX, drawY + editor.textRender.lineHeight, editor.selectionHandles.handlePaint);
        editor.cursorHandle.cursorHandleRect.set(
            drawX - editor.selectionHandles.handleRadius,
            drawY + editor.textRender.lineHeight,
            drawX + editor.selectionHandles.handleRadius,
            drawY + editor.textRender.lineHeight + editor.selectionHandles.handleRadius * 2);
      } else {
        editor.cursorHandle.cursorHandleRect.setEmpty();
      }
    }

    if (editor.selection.hasSelection) {
      drawWrappedHandles(canvas, firstVisualIndex, lastVisualIndex, directLines);
    }
  }

  private void drawWrappedHandles(Canvas canvas, int firstVisualIndex, int lastVisualIndex, HashMap<Integer, String> directLines) {
    editor.selectionHandles.handlePaint.setColor(editor.selectionHandles.selectionHandleColor);
    int startVisual = editor.getVisualIndexForLineAndChar(editor.selection.selStartLine, editor.selection.selStartChar);
    if (startVisual >= firstVisualIndex && startVisual <= lastVisualIndex) {
      String startLineText = getLineTextForRenderWithDirect(editor.selection.selStartLine, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(editor.selection.selStartLine, startLineText);
      int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selStartChar);
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, startLineText.length());
      float x =
          editor.getCaretXForSegment(
              startLineText,
              editor.selection.selStartLine,
              segStart,
              segEnd,
              Math.min(editor.selection.selStartChar, startLineText.length()));
      float y = (startVisual - firstVisualIndex) * editor.textRender.lineHeight + editor.textRender.lineHeight;
      drawTeardropHandle(canvas, x, y, editor.selectionHandles.handlePaint);
      if (editor.textRender.isRtl) {
        editor.selectionHandles.rightHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
      } else {
        editor.selectionHandles.leftHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
      }
    } else {
      if (editor.textRender.isRtl) editor.selectionHandles.rightHandleRect.setEmpty();
      else editor.selectionHandles.leftHandleRect.setEmpty();
    }
    int endVisual = editor.getVisualIndexForLineAndChar(editor.selection.selEndLine, editor.selection.selEndChar);
    if (endVisual >= firstVisualIndex && endVisual <= lastVisualIndex) {
      String endLineText = getLineTextForRenderWithDirect(editor.selection.selEndLine, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(editor.selection.selEndLine, endLineText);
      int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selEndChar);
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, endLineText.length());
      float x =
          editor.getCaretXForSegment(
              endLineText,
              editor.selection.selEndLine,
              segStart,
              segEnd,
              Math.min(editor.selection.selEndChar, endLineText.length()));
      float y = (endVisual - firstVisualIndex) * editor.textRender.lineHeight + editor.textRender.lineHeight;
      drawTeardropHandle(canvas, x, y, editor.selectionHandles.handlePaint);
      if (editor.textRender.isRtl) {
        editor.selectionHandles.leftHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
      } else {
        editor.selectionHandles.rightHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
      }
    } else {
      if (editor.textRender.isRtl) editor.selectionHandles.leftHandleRect.setEmpty();
      else editor.selectionHandles.rightHandleRect.setEmpty();
    }
  }

  private void drawWrappedOverlays(Canvas canvas) {
    if (editor.loadingCircle.showLoadingCircle) {
      editor.loadingCircle.loadingCirclePaint.setColor(editor.loadingCircle.loadingCircleColor);
      editor.loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = editor.getWidth() / 2f;
      float centerY = editor.getHeight() / 2f;
      canvas.save();
      canvas.rotate(editor.loadingCircle.loadingCircleRotation, centerX, centerY);
      editor.loadingCircle.loadingCircleRect.set(
          centerX - editor.loadingCircle.loadingCircleRadius,
          centerY - editor.loadingCircle.loadingCircleRadius,
          centerX + editor.loadingCircle.loadingCircleRadius,
          centerY + editor.loadingCircle.loadingCircleRadius);
      canvas.drawArc(editor.loadingCircle.loadingCircleRect, 0, 270, false, editor.loadingCircle.loadingCirclePaint);
      canvas.restore();
    }
  }

  // ============================================================================
  // Fallback Wrapped Content Draw Methods
  // ============================================================================

  public void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx, boolean drawBracketGuides) {
    int firstIndex = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
    int lastIndex = firstIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5;
    final boolean drawDecorations = editor.zoom.shouldDrawDecorations();

    int firstLine = firstIndex;
    int lastLine = lastIndex;
    if (editor.codeFold.isCodeFoldingEnabled) {
      int visibleCount = editor.codeFold.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstIndex = Math.max(0, Math.min(firstIndex, visibleCount - 1));
      lastIndex = Math.max(firstIndex, Math.min(lastIndex, visibleCount - 1));
      firstLine = editor.codeFold.mapVisibleIndexToGlobal(firstIndex);
      lastLine = editor.codeFold.mapVisibleIndexToGlobal(lastIndex);
    }

    maybeKickWindowLoad(firstLine);

    HashMap<Integer, String> directLines = null;
    if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
      editor.textRender.directLinesTmp.clear();
      directLines = editor.textRender.directLinesTmp;
      editor.fileIO.populateDirectLinesForRange(firstLine, lastLine, directLines);
    }

    float baseY = firstIndex * editor.textRender.lineHeight;
    float translateY = -editor.scroll.scrollY + baseY;

    // Draw gutter background
    if (editor.lineNumber.showLineNumbers) {
      canvas.drawRect(
          editor.lineNumber.getGutterStartX(),
          0,
          editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth,
          editor.getHeight(),
          editor.lineNumber.gutterPaint);
      float separatorLeft =
          editor.textRender.isRtl
              ? editor.lineNumber.getGutterStartX()
              : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.gutterSeparatorWidth;
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + editor.lineNumber.gutterSeparatorWidth,
          editor.getHeight(),
          editor.lineNumber.gutterSeparatorPaint);
    }

    if (editor.currentLineHighlight.highlightCurrentLineInGutter
        && (!editor.codeFold.isCodeFoldingEnabled || !editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine))) {
      int currentVisualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
      if (currentVisualIndex >= firstIndex && currentVisualIndex <= lastIndex) {
        float top = Math.round(currentVisualIndex * editor.textRender.lineHeight - editor.scroll.scrollY);
        float bottom = top + editor.textRender.lineHeight;
        editor.lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    boolean uselineNumberCache = false;

    canvas.save();
    canvas.translate(0, translateY);

    float lineNumX = 0f;
    if (editor.lineNumber.showLineNumbers && !uselineNumberCache) {
      lineNumX =
          editor.textRender.isRtl
              ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
              : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.GUTTER_TEXT_PADDING;
    }

    int saveCount = canvas.save();
    if (editor.textRender.isRtl) {
      canvas.clipRect(0, 0, editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth, editor.getHeight());
    } else {
      canvas.clipRect(editor.lineNumber.lineNumbersGutterWidth, 0, editor.getWidth(), editor.getHeight());
    }
    canvas.translate(editor.getTextStartX() - editor.getEffectiveScrollX(), 0);

    Paint selPaint = null;
    if (editor.selection.hasSelection) {
      editor.selection.selectionPaint.setColor(editor.selection.selectionHighlightColor);
      selPaint = editor.selection.selectionPaint;
    }

    int startLine = editor.selection.selStartLine;
    int startChar = editor.selection.selStartChar;
    int endLine = editor.selection.selEndLine;
    int endChar = editor.selection.selEndChar;
    if (editor.selection.hasSelection && editor.editOperators.comparePos(editor.selection.selStartLine, editor.selection.selStartChar, editor.selection.selEndLine, editor.selection.selEndChar) > 0) {
      startLine = editor.selection.selEndLine;
      startChar = editor.selection.selEndChar;
      endLine = editor.selection.selStartLine;
      endChar = editor.selection.selStartChar;
    }

    SodiumEditor.BracketMatch bracketMatchResult = null;
    if (editor.bracketMatchManager.isBracketMatchingEnabled) {
      bracketMatchResult = editor.bracketMatchManager.findAndCacheBracketMatch(firstLine, lastLine, directLines);
    }

    int visualIndex = firstIndex;
    float yOffset = 0f;
    boolean cursorDrawn = false;
    int startHandleVisual = -1;
    int endHandleVisual = -1;

    for (int line = firstLine; line <= lastLine; line++) {
      if (yOffset > editor.getHeight() + editor.textRender.lineHeight) break;
      String text = getLineTextForRenderWithDirect(line, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(line, text);

      for (int seg = 0; seg < starts.length; seg++) {
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, text.length());
        float segBaseX = editor.textRender.isRtl ? getRtlSegmentBaseX(text, line, segStart, segEnd) : 0f;

        float top = Math.round(yOffset);
        float bottom = top + editor.textRender.lineHeight;
        float y = Math.round(top + editor.textRender.lineHeight - editor.textRender.paint.descent());

        if (editor.lineNumber.showLineNumbers && seg == 0 && !uselineNumberCache) {
          canvas.restore();
          int start = editor.textRender.writeIntToChars(line + 1, editor.lineNumber.lineNumberChars);
          int count = editor.lineNumber.lineNumberChars.length - start;
          if (line == editor.cursor.cursorLine) {
            int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
            editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
            canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
            editor.lineNumber.lineNumbersPaint.setColor(originalColor);
          } else {
            canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
          }
          canvas.save();
          if (editor.textRender.isRtl) {
            canvas.clipRect(0, 0, editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth, editor.getHeight());
          } else {
            canvas.clipRect(editor.lineNumber.lineNumbersGutterWidth, 0, editor.getWidth(), editor.getHeight());
          }
          canvas.translate(editor.getTextStartX() - editor.getEffectiveScrollX(), 0);
        }

        if (editor.currentLineHighlight.highlightCurrentLine && line == editor.cursor.cursorLine && !editor.selection.hasSelection) {
          canvas.drawRect(
              -editor.textRender.paddingLeft, top, Math.max(editor.wordWrap.getWrapWidth(), editor.getWidth()), bottom, editor.currentLineHighlight.currentLinePaint);
        }

        if (editor.selection.hasSelection && selPaint != null) {
          drawFallbackSelection(canvas, line, text, segStart, segEnd, segBaseX, startLine, startChar, endLine, endChar, selPaint, top, bottom);
        }

        int segDrawEnd = segEnd;
        if (editor.wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          segDrawEnd = clampSegmentEndForWrapIndicator(text, segStart, segEnd, editor.wordWrap.wrapWidthPx);
        }
        canvas.save();
        if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
        drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
        editor.textRender.drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        editor.errorUnderline.drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        editor.textRender.drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
        if (drawDecorations) {
          editor.textRender.drawWhitespaceGuidesForSegment(canvas, text, line, segStart, segDrawEnd, y);
          editor.bracketMatchManager.drawBracketMatchForSegment(canvas, text, line, segStart, segEnd, segBaseX, top, bracketMatchResult);
        }
        editor.autoCompletion.drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, visualIndex, y);
        if (editor.wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          float indicatorX =
              editor.textRender.isRtl
                  ? editor.wordWrap.indicator.wordWrapIndicatorPadPx
                  : Math.max(
                      editor.wordWrap.indicator.wordWrapIndicatorPadPx,
                      editor.wordWrap.wrapWidthPx - editor.wordWrap.indicator.wordWrapIndicatorWidth - editor.wordWrap.indicator.wordWrapIndicatorPadPx);
          canvas.drawText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, editor.wordWrap.indicator.wordWrapIndicatorPaint);
        }
        canvas.restore();

        if (!cursorDrawn && editor.isFocused() && !editor.selection.hasSelection && line == editor.cursor.cursorLine) {
          int cursorSeg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.cursor.cursorChar);
          if (cursorSeg == seg) {
            int safeChar = Math.min(editor.cursor.cursorChar, text.length());
            float cursorX = editor.getCaretXForSegment(text, line, segStart, segEnd, safeChar);
            float cursorY = top;
            editor.cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
            float drawX = editor.cursorAnimation.cursorDrawX;
            float drawY = editor.cursorAnimation.cursorDrawY;
            if (editor.caret.isCursorVisible) {
              editor.caret.caretPaint.setColor(editor.caret.caretColor);
              editor.caret.caretPaint.setStrokeWidth(editor.cursor.cursorWidth);
              canvas.drawLine(drawX, drawY, drawX, drawY + editor.textRender.lineHeight, editor.caret.caretPaint);
            }
            editor.selectionHandles.handlePaint.setColor(editor.cursorHandle.cursorHandleColor);
            drawTeardropHandle(canvas, drawX, drawY + editor.textRender.lineHeight, editor.selectionHandles.handlePaint);
            editor.cursorHandle.cursorHandleRect.set(
                drawX - editor.selectionHandles.handleRadius,
                drawY + editor.textRender.lineHeight,
                drawX + editor.selectionHandles.handleRadius,
                drawY + editor.textRender.lineHeight + editor.selectionHandles.handleRadius * 2);
            cursorDrawn = true;
          }
        }

        if (editor.selection.hasSelection) {
          if (line == editor.selection.selStartLine) {
            int selSeg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selStartChar);
            if (selSeg == seg) startHandleVisual = visualIndex;
          }
          if (line == editor.selection.selEndLine) {
            int selSeg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selEndChar);
            if (selSeg == seg) endHandleVisual = visualIndex;
          }
        }

        yOffset += editor.textRender.lineHeight;
        visualIndex++;
        if (yOffset > editor.getHeight() + editor.textRender.lineHeight) break;
      }
    }

    canvas.restore();
    canvas.restore();

    if (editor.selection.hasSelection) {
      drawFallbackHandles(canvas, firstIndex, visualIndex - 1, startHandleVisual, endHandleVisual, directLines, translateY);
    }

    if (editor.loadingCircle.showLoadingCircle) {
      editor.loadingCircle.loadingCirclePaint.setColor(editor.loadingCircle.loadingCircleColor);
      editor.loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = editor.getWidth() / 2f;
      float centerY = editor.getHeight() / 2f;
      canvas.save();
      canvas.rotate(editor.loadingCircle.loadingCircleRotation, centerX, centerY);
      editor.loadingCircle.loadingCircleRect.set(
          centerX - editor.loadingCircle.loadingCircleRadius,
          centerY - editor.loadingCircle.loadingCircleRadius,
          centerX + editor.loadingCircle.loadingCircleRadius,
          centerY + editor.loadingCircle.loadingCircleRadius);
      canvas.drawArc(editor.loadingCircle.loadingCircleRect, 0, 270, false, editor.loadingCircle.loadingCirclePaint);
      canvas.restore();
    }
  }

  private void drawFallbackSelection(Canvas canvas, int line, String text, int segStart, int segEnd, float segBaseX,
                                      int startLine, int startChar, int endLine, int endChar, Paint selPaint,
                                      float top, float bottom) {
    if (line >= startLine && line <= endLine) {
      int lineSelStart = (line == startLine) ? startChar : 0;
      int lineSelEnd = (line == endLine) ? endChar : text.length();
      int segSelStart = Math.max(segStart, lineSelStart);
      int segSelEnd = Math.min(segEnd, lineSelEnd);
      if (segSelEnd > segSelStart) {
        boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
        float leftRel = fullSegmentSelected ? 0f : editor.measureTextWithVisualSpaces(text, segStart, segSelStart, editor.textRender.paint);
        float rightRel = fullSegmentSelected ? Math.max(0f, editor.wordWrap.wrapWidthPx) : leftRel + editor.measureTextWithVisualSpaces(text, segSelStart, segSelEnd, editor.textRender.paint);
        float left = leftRel + segBaseX;
        float right = rightRel + segBaseX;
        boolean roundTop = (line == startLine && segSelStart == startChar);
        boolean roundBottom = (line == endLine && segSelEnd == endChar);
        editor.onTouch.drawSelectionSegment(
            canvas,
            left,
            top,
            right,
            bottom,
            roundTop,
            roundTop,
            roundBottom,
            roundBottom,
            selPaint);
      }
    }
  }

  private void drawFallbackHandles(Canvas canvas, int firstIndex, int lastVisualIndex, int startHandleVisual, int endHandleVisual,
                                    HashMap<Integer, String> directLines, float translateY) {
    editor.selectionHandles.handlePaint.setColor(editor.selectionHandles.selectionHandleColor);
    if (startHandleVisual >= firstIndex && startHandleVisual <= lastVisualIndex) {
      String startLineText = getLineTextForRenderWithDirect(editor.selection.selStartLine, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(editor.selection.selStartLine, startLineText);
      int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selStartChar);
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, startLineText.length());
      float x =
          editor.getCaretXForSegment(
              startLineText,
              editor.selection.selStartLine,
              segStart,
              segEnd,
              Math.min(editor.selection.selStartChar, startLineText.length()));
      float y = (startHandleVisual - firstIndex) * editor.textRender.lineHeight + editor.textRender.lineHeight + translateY;
      drawTeardropHandle(canvas, x, y, editor.selectionHandles.handlePaint);
      editor.selectionHandles.leftHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
    } else {
      editor.selectionHandles.leftHandleRect.setEmpty();
    }

    if (endHandleVisual >= firstIndex && endHandleVisual <= lastVisualIndex) {
      String endLineText = getLineTextForRenderWithDirect(editor.selection.selEndLine, directLines);
      int[] starts = editor.wordWrap.getWrapStartsForLine(editor.selection.selEndLine, endLineText);
      int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, editor.selection.selEndChar);
      int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
      int segEnd = editor.wordWrap.getWrapSegmentEnd(starts, seg, endLineText.length());
      float x =
          editor.getCaretXForSegment(
              endLineText,
              editor.selection.selEndLine,
              segStart,
              segEnd,
              Math.min(editor.selection.selEndChar, endLineText.length()));
      float y = (endHandleVisual - firstIndex) * editor.textRender.lineHeight + editor.textRender.lineHeight + translateY;
      drawTeardropHandle(canvas, x, y, editor.selectionHandles.handlePaint);
      editor.selectionHandles.rightHandleRect.set(x - editor.selectionHandles.handleRadius, y, x + editor.selectionHandles.handleRadius, y + editor.selectionHandles.handleRadius * 2);
    } else {
      editor.selectionHandles.rightHandleRect.setEmpty();
    }
  }

  // ============================================================================
  // Helper Methods - Delegated to SodiumEditor
  // ============================================================================

  private void maybeKickWindowLoad(int line) {
    editor.maybeKickWindowLoad(line);
  }

  private void maybeUpdateStreamedSlicesForVisibleRange(int first, int last) {
    editor.maybeUpdateStreamedSlicesForVisibleRange(first, last);
  }

  private void maybeEnsureHighlightCacheForRange(int start, int end, HashMap<Integer, String> direct) {
    editor.maybeEnsureHighlightCacheForRange(start, end, direct);
  }

  private String getLineTextForRender(int line) {
    return editor.getLineTextForRender(line);
  }

  private String getLineTextForRenderWithDirect(int line, HashMap<Integer, String> direct) {
    return editor.getLineTextForRenderWithDirect(line, direct);
  }

  private int getLogicalLineLength(int line, String lineText) {
    return editor.getLogicalLineLength(line, lineText);
  }

  private float getRtlLineBaseX(String line, int globalLine) {
    return editor.getRtlLineBaseX(line, globalLine);
  }

  private float getRtlSegmentBaseX(String line, int globalLine, int segStart, int segEnd) {
    return editor.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
  }

  private float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    return editor.measureHighlightedSegmentWidth(line, globalLine, start, end);
  }

  private void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine) {
    editor.colorCodeHighlight.drawColorCodeBackgrounds(canvas, line, globalLine);
  }

  private void drawBracketGuidesForLine(Canvas canvas, String line, int globalLine, List<BracketGuides.BracketGuideToken> tokens) {
    editor.bracketGuides.drawBracketGuidesForLine(canvas, line, globalLine, tokens);
  }

  private void drawSearchHighlightsForLine(Canvas canvas, String line, int globalLine, float top, float bottom) {
    editor.drawSearchHighlightsForLine(canvas, line, globalLine, top, bottom);
  }

  private void drawSearchHighlightsForSegment(Canvas canvas, String line, int globalLine, int segStart, int segEnd, float top, float bottom) {
    editor.drawSearchHighlightsForSegment(canvas, line, globalLine, segStart, segEnd, top, bottom);
  }

  private int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd, int wrapWidth) {
    return editor.clampSegmentEndForWrapIndicator(line, segStart, segEnd, wrapWidth);
  }

  private void drawTeardropHandle(Canvas canvas, float x, float y, Paint paint) {
    editor.drawTeardropHandle(canvas, x, y, paint);
  }

  // ============================================================================
  // Getters
  // ============================================================================

  public SodiumEditor getEditor() {
    return editor;
  }
}
