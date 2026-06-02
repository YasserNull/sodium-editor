package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.guides.bracket.BracketMatch;
import com.yn.sodiumeditor.core.wordwrap.WordWrap;
import com.yn.sodiumeditor.core.guides.bracket.BracketGuides;
import java.util.List;
import java.util.HashMap;

public class ViewRender {
  private static final String TAG = "SodiumCharAnim";
  private static final int MAX_VIEW_TRACE_LOGS = 260;

  private final SodiumEditor editor;
  public int drawBaseLine = 0;
  private final Paint selectionPaint;
  private final RectF tempRectF;
  private int viewTraceLogCount = 0;

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
    if (false) {
      int visibleCount = Math.max(1, editor.view.getLinesCount());
      if (visibleCount > 0) {
        visibleStart = Math.max(0, Math.min(visibleStart, visibleCount - 1));
        visibleEnd = Math.max(visibleStart, Math.min(visibleEnd, visibleCount - 1));
      }
    }
    
    editor.bracketGuides.beginRenderFrame(windowStart, windowEnd, visibleStart, visibleEnd);
    editor.bracketGuides.setFrameFastScroll(fastScroll);
    logViewTrace(
        "VIEW_DRAW_CONTENT",
        "window="
            + windowStart
            + ".."
            + windowEnd
            + " visual="
            + visibleStart
            + ".."
            + visibleEnd
            + " fastScroll="
            + fastScroll);
    
    boolean shouldDrawBracketGuides = editor.bracketGuides.isBracketGuidesEnabled && (editor.bracketGuides.showGuidesDuringFastScroll || !fastScroll);
    
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.textRender.drawlineNumbersCachedWrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }
    editor.bracketErrorScanner.scanForErrors();
    drawContentInternal(canvas, shouldDrawBracketGuides);
    editor.scroll.bar.draw(canvas);
  }
  
  private void drawContentInternal(Canvas canvas, boolean drawBracketGuides) {
    final boolean drawDecorations = editor.zoom.shouldDrawDecorations();
    
    int firstVisibleIndex = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 5;

    int firstVisibleLine;
    int lastVisibleLine;

    if (editor.wordWrap.isWordWrapEnabled) {
      firstVisibleLine = editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleLine = editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
    } else if (false) {
      int totalLines = Math.max(1, editor.view.getLinesCount());
      int visibleCount = Math.min(Math.max(1, editor.view.getLinesCount()), totalLines);
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = Math.max(0, Math.min(firstVisibleIndex, totalLines - 1));
      lastVisibleLine = Math.max(firstVisibleLine, Math.min(lastVisibleIndex, totalLines - 1));
    } else {
      int totalLines = Math.max(1, editor.view.getLinesCount());
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, totalLines - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, totalLines - 1));
      firstVisibleLine = firstVisibleIndex;
      lastVisibleLine = lastVisibleIndex;
    }

    drawBaseLine = firstVisibleLine;
    float baseY = firstVisibleIndex * editor.textRender.lineHeight;
    float translateY = -editor.scroll.scrollY + baseY;
    logViewTrace(
        "VIEW_DRAW_INTERNAL",
        "firstVisibleIndex="
            + firstVisibleIndex
            + " lastVisibleIndex="
            + lastVisibleIndex
            + " lines="
            + firstVisibleLine
            + ".."
            + lastVisibleLine
            + " lineHeight="
            + editor.textRender.lineHeight
            + " translateY="
            + translateY
            + " wordWrap="
            + editor.wordWrap.isWordWrapEnabled);

    if (editor.lineNumber.showLineNumbers) {
      canvas.drawRect(editor.lineNumber.getGutterStartX(), 0, editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth, editor.getHeight(), editor.lineNumber.gutterPaint);
      float separatorLeft = editor.textRender.isRtl ? editor.lineNumber.getGutterStartX() : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.gutterSeparatorWidth;
      canvas.drawRect(separatorLeft, 0, separatorLeft + editor.lineNumber.gutterSeparatorWidth, editor.getHeight(), editor.lineNumber.gutterSeparatorPaint);
    }

    if (editor.lineNumber.showLineNumbers) {
      editor.textRender.drawlineNumbersCachedUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
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
    clipTextArea(canvas);
    canvas.translate(editor.lineNumber.lineNumbersGutterWidth - editor.scroll.getEffectiveScrollX(), translateY);
    
    drawTextContent(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine, drawDecorations, drawBracketGuides);
    canvas.restore();
    
    canvas.save();
    clipTextArea(canvas);
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
    canvas.restore();

    // Draw popup if it's supposed to be shown, even if no selection
    if (editor.popup.showPopup && editor.popup.popupAlpha > 0f) {
        editor.popup.drawPopup(canvas);
    }  }

  private void clipTextArea(Canvas canvas) {
    float gutterWidth =
        editor.lineNumber.showLineNumbers ? editor.lineNumber.lineNumbersGutterWidth : 0f;
    if (gutterWidth <= 0f) {
      canvas.clipRect(0, 0, editor.getWidth(), editor.getHeight());
      return;
    }
    if (editor.textRender.isRtl) {
      canvas.clipRect(0, 0, Math.max(0f, editor.getWidth() - gutterWidth), editor.getHeight());
    } else {
      canvas.clipRect(gutterWidth, 0, editor.getWidth(), editor.getHeight());
    }
  }

  private void drawTextContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
                                int firstVisibleLine, int lastVisibleLine, boolean drawDecorations, boolean drawBracketGuides) {
    Paint selPaint = null;
    if (editor.selection.state.animation.shouldDrawSelectionHighlight()) {
        selPaint = editor.selection.selectionPaint;
        int baseAlpha = editor.selection.selectionColor >>> 24;
        float alphaProgress = Math.max(0f, Math.min(1f, editor.selection.state.getSelectionAlpha()));
        selPaint.setAlpha((int) (baseAlpha * alphaProgress));
    }

    HashMap<Integer, String> directLines = null;
    if (editor.fileIO.sourceFile != null) {
      int winStart = editor.windowRender.windowStartLine;
      int winEnd = winStart + editor.windowRender.linesWindow.size() - 1;
      if (firstVisibleLine < winStart || lastVisibleLine > winEnd) {
        editor.windowRender.directLinesTmp.clear();
        directLines = editor.windowRender.directLinesTmp;
        int directStart = Math.max(0, firstVisibleLine);
        int directEnd = Math.max(directStart, lastVisibleLine);
        editor.fileIO.populateDirectLinesForRange(directStart, directEnd, directLines);
      }
    }

    BracketMatch bracketMatchResult = null;
    if (editor.bracketMatchManager.isBracketMatchingEnabled) {
      bracketMatchResult = editor.bracketMatchManager.findAndCacheBracketMatch(firstVisibleLine, lastVisibleLine, directLines);
    }

    if (editor.selection.state.animation.shouldDrawSelectionHighlight() && selPaint != null) {
        boolean drawingFadeOut = editor.selection.state.animation.isDrawingFadeOutSelection();
        int selStartLine =
            drawingFadeOut ? editor.selection.state.animation.fadeOutStartLine : editor.selection.selStartLine;
        int selStartChar =
            drawingFadeOut ? editor.selection.state.animation.fadeOutStartChar : editor.selection.selStartChar;
        int selEndLine =
            drawingFadeOut ? editor.selection.state.animation.fadeOutEndLine : editor.selection.selEndLine;
        int selEndChar =
            drawingFadeOut ? editor.selection.state.animation.fadeOutEndChar : editor.selection.selEndChar;
        if (editor.editOperators.comparePos(selStartLine, selStartChar, selEndLine, selEndChar) > 0) {
            int tmpL = selStartLine;
            int tmpC = selStartChar;
            selStartLine = selEndLine;
            selStartChar = selEndChar;
            selEndLine = tmpL;
            selEndChar = tmpC;
        }

        float textStartX = editor.layout.getTextStartX() - editor.lineNumber.lineNumbersGutterWidth;
        float viewportLeft = editor.scroll.getEffectiveScrollX();
        float viewportRight = viewportLeft + editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth;

        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            if (i >= selStartLine && i <= selEndLine) {
                String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
                if (line != null) {
                    float lineTop = editor.textRender.getDrawLineTop(i);
                    float lineBottom = lineTop + editor.textRender.lineHeight;
                    int startChar = (i == selStartLine) ? selStartChar : 0;
                    int endChar = (i == selEndLine) ? selEndChar : line.length();
                    boolean isFirstLine = (i == selStartLine);
                    boolean isLastLine = (i == selEndLine);
                    boolean isSingleLine = (selStartLine == selEndLine);
                    boolean fillsWholeLine = !isSingleLine && !isFirstLine && !isLastLine;
                    float startX = textStartX + editor.textRender.measureTextWithVisualSpaces(line, 0, startChar, editor.textRender.paint);
                    float endX = textStartX + editor.textRender.measureTextWithVisualSpaces(line, 0, endChar, editor.textRender.paint);
                    float visibleSelectionStartX = Math.max(textStartX, viewportLeft);
                    float left = isSingleLine
                            ? startX
                            : isFirstLine && !isSingleLine
                            ? startX
                            : visibleSelectionStartX;
                    float top = lineTop;
                    float right = (isFirstLine && !isSingleLine) || fillsWholeLine
                            ? viewportRight
                            : endX;
                    float bottom = lineBottom;
                    float progress = Math.max(0f, Math.min(1f, editor.selection.state.getSelectionGeometryProgress()));
                    float anchor = isLastLine && !isSingleLine ? right : left;
                    if (progress < 0.999f) {
                        if (anchor == left) {
                            right = left + ((right - left) * progress);
                        } else {
                            left = right - ((right - left) * progress);
                        }
                    }

                    if (isSingleLine) {
                        editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                            true, true, true, true, selPaint);
                    } else if (isFirstLine) {
                        editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                            true, true, false, false, selPaint);
                    } else if (isLastLine) {
                        editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                            false, false, true, true, selPaint);
                    } else {
                        editor.onTouch.drawSelectionSegment(canvas, left, top, right, bottom,
                            false, false, false, false, selPaint);
                    }
                }
            }
        }
    }
    
    canvas.save();
    canvas.translate(editor.layout.paddingLeft, 0f);
    for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
        String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
        float y = (i - firstVisibleLine) * editor.textRender.lineHeight + editor.textRender.lineHeight - editor.textRender.paint.descent();
        if (i == editor.charAnimation.charAnimLine
            && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
            && editor.charAnimation.charAnimAlpha < 1f) {
            logViewTraceAlways(
                "VIEW_DRAW_ANIM_LINE",
                "line="
                    + i
                    + " textLen="
                    + (line == null ? -1 : line.length())
                    + " y="
                    + y
                    + " animRange="
                    + editor.charAnimation.charAnimStartChar
                    + ".."
                    + editor.charAnimation.charAnimEndChar
                    + " alpha="
                    + editor.charAnimation.charAnimAlpha
                    + " directLines="
                    + (directLines != null));
        }
        if (line != null) editor.colorCodeHighlight.drawColorCodeBackgrounds(canvas, line, i);
        editor.textRender.drawHighlightedLine(canvas, line, i, y);
        if (editor.autoCompletion != null && line != null) {
            editor.autoCompletion.drawAutoSuggestion(canvas, line, i, y);
        }
        if (i == editor.charAnimation.charAnimLine
            && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
            && editor.charAnimation.charAnimAlpha < 1f) {
            logViewTraceAlways(
                "VIEW_AFTER_HIGHLIGHT_LINE",
                "line="
                    + i
                    + " textLen="
                    + (line == null ? -1 : line.length())
                    + " alpha="
                    + editor.charAnimation.charAnimAlpha);
        }
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

    // Bracket guides
    if (editor.bracketGuides.isBracketGuidesEnabled && drawDecorations && drawBracketGuides) {
        editor.bracketGuides.drawBracketGuidesForVisibleRange(canvas, firstVisibleLine, lastVisibleLine);
    }
    canvas.restore();
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

  public SodiumEditor getEditor() { 
      return editor; 
  }

  private void logViewTrace(String phase, String details) {
    if (!SodiumEditor.DEBUG_LOGS) return;
    if (viewTraceLogCount >= MAX_VIEW_TRACE_LOGS) return;
    if (editor.charAnimation.charAnimLine < 0
        || editor.charAnimation.charAnimEndChar <= editor.charAnimation.charAnimStartChar
        || editor.charAnimation.charAnimAlpha >= 1f) {
      return;
    }
    viewTraceLogCount++;
    Log.d(
        TAG,
        phase
            + " animLine="
            + editor.charAnimation.charAnimLine
            + " animRange="
            + editor.charAnimation.charAnimStartChar
            + ".."
            + editor.charAnimation.charAnimEndChar
            + " alpha="
            + editor.charAnimation.charAnimAlpha
            + " "
            + details);
  }

  private void logViewTraceAlways(String phase, String details) {
    if (!SodiumEditor.DEBUG_LOGS) return;
    Log.d(
        TAG,
        phase
            + " animLine="
            + editor.charAnimation.charAnimLine
            + " animRange="
            + editor.charAnimation.charAnimStartChar
            + ".."
            + editor.charAnimation.charAnimEndChar
            + " alpha="
            + editor.charAnimation.charAnimAlpha
            + " "
            + details);
  }

}
