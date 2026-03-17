package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages whitespace guides for the SodiumEditor.
 * Draws visual indicators for spaces and tabs.
 */
public class WhitespaceGuides {

  private final SodiumEditor editor;

  // Whitespace guides state
  public boolean isWhitespaceGuidesEnabled = true;
  public final Paint whitespaceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint whitespaceGuideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float whitespaceGuideSpaceWidth = 0f;
  public float whitespaceGuideTabWidth = 0f;
  public int whitespaceGuideSpaceStep = 1;

  // Buffers for drawing
  public float[] whitespaceWidthBuffer;
  public float[] whitespaceDotBuffer;

  public WhitespaceGuides(SodiumEditor editor) {
    this.editor = editor;
    initPaint();
  }

  private void initPaint() {
    whitespaceGuidePaint.setColor(0xFF555555);
    whitespaceGuidePaint.setStyle(Paint.Style.FILL);
    whitespaceGuidePaint.setUnderlineText(false);
    whitespaceGuideDotPaint.setColor(0xFF555555);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    updateMetrics();
  }

  /**
   * Enables or disables whitespace guides.
   */
  public void setWhitespaceGuidesEnabled(boolean enabled) {
    if (this.isWhitespaceGuidesEnabled == enabled) return;
    this.isWhitespaceGuidesEnabled = enabled;
    editor.invalidate();
  }

  /**
   * Sets the whitespace guides color.
   */
  public void setWhitespaceGuidesColor(int color) {
    whitespaceGuidePaint.setColor(color);
    whitespaceGuideDotPaint.setColor(color);
    if (isWhitespaceGuidesEnabled) editor.invalidate();
  }

  /**
   * Sets the space step (spaces per dot).
   */
  public void setWhitespaceGuidesSpaceStep(int spacesPerDot) {
    int safeStep = Math.max(1, spacesPerDot);
    if (whitespaceGuideSpaceStep == safeStep) return;
    whitespaceGuideSpaceStep = safeStep;
    if (isWhitespaceGuidesEnabled) editor.invalidate();
  }

  /**
   * Updates metrics when text size changes.
   */
  public void updateMetrics() {
    whitespaceGuidePaint.setTextSize(editor.paint.getTextSize());
    whitespaceGuidePaint.setTypeface(editor.paint.getTypeface());
    whitespaceGuideSpaceWidth = whitespaceGuidePaint.measureText(SodiumEditor.WHITESPACE_GUIDE_SPACE);
    whitespaceGuideTabWidth = whitespaceGuidePaint.measureText(SodiumEditor.WHITESPACE_GUIDE_TAB);
    whitespaceGuideDotPaint.setColor(whitespaceGuidePaint.getColor());
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    float dotSize = Math.max(1f, whitespaceGuidePaint.getTextSize() * 0.15f);
    whitespaceGuideDotPaint.setStrokeWidth(dotSize);
  }

  /**
   * Gets the whitespace guide step.
   */
  public int getWhitespaceGuideStep() {
    return Math.max(1, whitespaceGuideSpaceStep);
  }

  /**
   * Draws whitespace guides for a line.
   */
  public void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
    if (!isWhitespaceGuidesEnabled || editor.isHeavyDrawSuppressed() || line.isEmpty()) return;
    if (line.indexOf(' ') < 0 && line.indexOf('\t') < 0) return;
    
    if (editor.isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, 0, line.length(), y);
      return;
    }

    List<SodiumEditor.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    editor.whitespaceDrawState.syntaxIndex = 0;
    float rtlWidth = 0f;

    List<SodiumEditor.HighlightSpan> visualSpans = editor.highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = editor.calculateSpansForLine(line, globalLine);
      editor.highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = 0;

    if (!visualSpans.isEmpty()) {
      for (SodiumEditor.HighlightSpan span : visualSpans) {
        if (span.start < lastEnd) continue;
        if (span.start >= line.length()) break;

        int safeSpanEnd = Math.min(span.end, line.length());
        if (span.start > lastEnd) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, lastEnd, span.start, currentX, y,
              editor.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
        }

        currentX = drawWhitespaceGuidesSegment(
            canvas, line, span.start, safeSpanEnd, currentX, y,
            span.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
        lastEnd = safeSpanEnd;
      }
    }

    if (lastEnd < line.length()) {
      drawWhitespaceGuidesSegment(
          canvas, line, lastEnd, line.length(), currentX, y,
          editor.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
    }
  }

  /**
   * Draws whitespace guides for a segment.
   */
  public void drawWhitespaceGuidesForSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (!isWhitespaceGuidesEnabled || editor.isHeavyDrawSuppressed() || line == null || line.isEmpty())
      return;
    if (editor.isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, start, end, y);
      return;
    }
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<SodiumEditor.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    editor.whitespaceDrawState.syntaxIndex = 0;
    boolean mirrorRtl = editor.isRtl && !editor.isMixedDirectionText(line, start, end);
    float rtlWidth = mirrorRtl ? editor.measureHighlightedSegmentWidth(line, globalLine, start, end) : 0f;

    List<SodiumEditor.HighlightSpan> visualSpans = editor.highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = editor.calculateSpansForLine(line, globalLine);
      editor.highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = start;

    if (!visualSpans.isEmpty()) {
      for (SodiumEditor.HighlightSpan span : visualSpans) {
        if (lastEnd >= end) break;
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, lastEnd, segStart, currentX, y,
              editor.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
        }

        if (segEnd > segStart) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, segStart, segEnd, currentX, y,
              span.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawWhitespaceGuidesSegment(
          canvas, line, lastEnd, end, currentX, y,
          editor.paint, syntaxSpans, hasSyntaxSpans, editor.whitespaceDrawState, rtlWidth);
    }
  }

  /**
   * Draws whitespace guides for RTL range.
   */
  public void drawWhitespaceGuidesForRangeRtl(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    List<SodiumEditor.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    editor.whitespaceDrawState.syntaxIndex = 0;

    float rtlWidth = editor.measureHighlightedSegmentWidth(line, globalLine, start, end);
    float currentX = 0f;

    Paint.FontMetrics dotFm = whitespaceGuidePaint.getFontMetrics();
    float dotY = y + (dotFm.ascent + dotFm.descent) * 0.5f;

    int spaceStep = getWhitespaceGuideStep();
    int segLen = end - start;
    if (whitespaceWidthBuffer == null || whitespaceWidthBuffer.length < segLen) {
      whitespaceWidthBuffer = new float[segLen];
    }
    editor.paint.getTextWidths(line, start, end, whitespaceWidthBuffer);

    int localSyntaxIndex = hasSyntaxSpans ? editor.whitespaceDrawState.syntaxIndex : 0;
    SodiumEditor.HighlightSpan activeSyntax =
        hasSyntaxSpans && localSyntaxIndex < syntaxSpans.size()
            ? syntaxSpans.get(localSyntaxIndex)
            : null;

    for (int i = 0; i < segLen; i++) {
      int charIndex = start + i;
      while (activeSyntax != null && charIndex >= activeSyntax.end) {
        localSyntaxIndex++;
        activeSyntax =
            localSyntaxIndex < syntaxSpans.size() ? syntaxSpans.get(localSyntaxIndex) : null;
      }

      boolean isInSyntaxSpan =
          activeSyntax != null && charIndex >= activeSyntax.start && charIndex < activeSyntax.end;
      char c = line.charAt(charIndex);
      
      if (!isInSyntaxSpan && c == ' ') {
        int runStart = i;
        int runEnd = i + 1;
        float runWidth = whitespaceWidthBuffer[i];
        for (int j = i + 1; j < segLen; j++) {
          int runCharIndex = start + j;
          while (activeSyntax != null && runCharIndex >= activeSyntax.end) {
            localSyntaxIndex++;
            activeSyntax =
                localSyntaxIndex < syntaxSpans.size() ? syntaxSpans.get(localSyntaxIndex) : null;
          }
          boolean inSyntax =
              activeSyntax != null
                  && runCharIndex >= activeSyntax.start
                  && runCharIndex < activeSyntax.end;
          if (inSyntax || line.charAt(runCharIndex) != ' ') break;
          runWidth += whitespaceWidthBuffer[j];
          runEnd = j + 1;
        }

        int spacesInRun = runEnd - runStart;
        float runCursorX = currentX;
        int needed = spacesInRun * 2;
        if (whitespaceDotBuffer == null || whitespaceDotBuffer.length < needed) {
          whitespaceDotBuffer = new float[Math.max(needed, 64)];
        }
        int pointCount = 0;
        for (int k = 0; k < spacesInRun; k++) {
          float visualWidth = whitespaceWidthBuffer[runStart + k];
          if (spaceStep <= 1 || (k % spaceStep) == 0) {
            float dotX = rtlWidth - (runCursorX + visualWidth * 0.5f);
            whitespaceDotBuffer[pointCount++] = dotX;
            whitespaceDotBuffer[pointCount++] = dotY;
          }
          runCursorX += visualWidth;
        }
        if (pointCount > 0) {
          canvas.drawPoints(whitespaceDotBuffer, 0, pointCount, whitespaceGuideDotPaint);
        }

        currentX += runWidth;
        i = runEnd - 1;
        continue;
      }

      if (!isInSyntaxSpan && c == '\t') {
        float charWidth = editor.getVisualTabWidth(editor.paint);
        float glyphX = rtlWidth - (currentX + charWidth)
            + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        canvas.drawText(SodiumEditor.WHITESPACE_GUIDE_TAB, glyphX, y, whitespaceGuidePaint);
        currentX += charWidth;
        continue;
      }
      currentX += whitespaceWidthBuffer[i];
    }

    if (hasSyntaxSpans) {
      editor.whitespaceDrawState.syntaxIndex = localSyntaxIndex;
    }
  }

  /**
   * Draws whitespace guides for a segment with syntax awareness.
   */
  public float drawWhitespaceGuidesSegment(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      List<SodiumEditor.HighlightSpan> syntaxSpans,
      boolean hasSyntaxSpans,
      SodiumEditor.WhitespaceDrawState state,
      float rtlWidth) {
    if (start >= end) return x;
    int segLen = end - start;
    if (whitespaceWidthBuffer == null || whitespaceWidthBuffer.length < segLen) {
      whitespaceWidthBuffer = new float[segLen];
    }
    segmentPaint.getTextWidths(line, start, end, whitespaceWidthBuffer);

    final int spaceStep = getWhitespaceGuideStep();
    float currentX = x;
    int localSyntaxIndex = hasSyntaxSpans ? state.syntaxIndex : 0;
    SodiumEditor.HighlightSpan activeSyntax =
        hasSyntaxSpans && localSyntaxIndex < syntaxSpans.size()
            ? syntaxSpans.get(localSyntaxIndex)
            : null;
    Paint.FontMetrics dotFm = whitespaceGuidePaint.getFontMetrics();
    float dotY = y + (dotFm.ascent + dotFm.descent) * 0.5f;

    for (int i = 0; i < segLen; i++) {
      int charIndex = start + i;
      while (activeSyntax != null && charIndex >= activeSyntax.end) {
        localSyntaxIndex++;
        activeSyntax =
            localSyntaxIndex < syntaxSpans.size() ? syntaxSpans.get(localSyntaxIndex) : null;
      }

      boolean isInSyntaxSpan =
          activeSyntax != null && charIndex >= activeSyntax.start && charIndex < activeSyntax.end;
      char c = line.charAt(charIndex);
      
      if (!isInSyntaxSpan && c == ' ') {
        int runStart = i;
        int runEnd = i + 1;
        float runWidth = whitespaceWidthBuffer[i];
        for (int j = i + 1; j < segLen; j++) {
          int runCharIndex = start + j;
          while (activeSyntax != null && runCharIndex >= activeSyntax.end) {
            localSyntaxIndex++;
            activeSyntax =
                localSyntaxIndex < syntaxSpans.size() ? syntaxSpans.get(localSyntaxIndex) : null;
          }
          boolean inSyntax =
              activeSyntax != null
                  && runCharIndex >= activeSyntax.start
                  && runCharIndex < activeSyntax.end;
          if (inSyntax || line.charAt(runCharIndex) != ' ') break;
          runWidth += whitespaceWidthBuffer[j];
          runEnd = j + 1;
        }

        int spacesInRun = runEnd - runStart;
        float runCursorX = currentX;
        int needed = spacesInRun * 2;
        if (whitespaceDotBuffer == null || whitespaceDotBuffer.length < needed) {
          whitespaceDotBuffer = new float[Math.max(needed, 64)];
        }
        int pointCount = 0;
        for (int k = 0; k < spacesInRun; k++) {
          float visualWidth = whitespaceWidthBuffer[runStart + k];
          if (spaceStep <= 1 || (k % spaceStep) == 0) {
            float dotX = runCursorX + visualWidth * 0.5f;
            if (editor.isRtl && rtlWidth > 0f) {
              dotX = rtlWidth - dotX;
            }
            whitespaceDotBuffer[pointCount++] = dotX;
            whitespaceDotBuffer[pointCount++] = dotY;
          }
          runCursorX += visualWidth;
        }
        if (pointCount > 0) {
          canvas.drawPoints(whitespaceDotBuffer, 0, pointCount, whitespaceGuideDotPaint);
        }

        currentX += runWidth;
        i = runEnd - 1;
        continue;
      }

      if (!isInSyntaxSpan && c == '\t') {
        float charWidth = editor.getVisualTabWidth(segmentPaint);
        float glyphX = currentX + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        if (editor.isRtl && rtlWidth > 0f) {
          glyphX = rtlWidth - (currentX + charWidth)
              + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        }
        canvas.drawText(SodiumEditor.WHITESPACE_GUIDE_TAB, glyphX, y, whitespaceGuidePaint);
        currentX += charWidth;
        continue;
      }
      currentX += whitespaceWidthBuffer[i];
    }

    if (hasSyntaxSpans) {
      state.syntaxIndex = localSyntaxIndex;
    }
    return currentX;
  }

  /**
   * Gets syntax spans for whitespace guide rendering.
   */
  public List<SodiumEditor.HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
    List<SodiumEditor.HighlightSpan> syntaxSpans = null;
    Paint stringPaint = editor.stringHighlightRule != null ? editor.stringHighlightRule.paint : null;
    Paint commentPaint = editor.blockCommentHighlightRule != null ? editor.blockCommentHighlightRule.paint : null;

    List<SodiumEditor.HighlightSpan> spans = editor.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.calculateSpansForLine(line, globalLine);
      editor.highlightCache.put(globalLine, spans);
    }

    for (SodiumEditor.HighlightSpan span : spans) {
      if (span.paint == stringPaint || span.paint == commentPaint) {
        if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
        syntaxSpans.add(span);
      }
    }
    return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
  }
}
