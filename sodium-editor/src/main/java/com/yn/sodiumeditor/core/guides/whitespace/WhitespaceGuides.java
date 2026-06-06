package com.yn.sodiumeditor.core.guides.whitespace; 
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.yn.sodiumeditor.renderer.TextRender;
/**
 * Manages whitespace guides for the SodiumEditor.
 * Draws visual indicators for spaces and tabs.
 */
public class WhitespaceGuides {

  // Whitespace guide constants
  public static final String WHITESPACE_GUIDE_SPACE = "·";
  public static final String WHITESPACE_GUIDE_TAB = "→";
  public static final int WHITESPACE_GUIDE_COLOR = 0xFF555555;

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
    whitespaceGuidePaint.setColor(WHITESPACE_GUIDE_COLOR);
    whitespaceGuidePaint.setStyle(Paint.Style.FILL);
    whitespaceGuidePaint.setUnderlineText(false);
    whitespaceGuideDotPaint.setColor(WHITESPACE_GUIDE_COLOR);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    updateMetrics();
  }

public void setWhitespaceGuidesEnabled(boolean enabled) {
    isWhitespaceGuidesEnabled = enabled;
    editor.bracketGuides.invalidateBracketGuideCache();
    editor.highlite.invalidateHighlightEnsureRange();
    synchronized (editor.windowRender.lineWidthCache) {
      editor.windowRender.lineWidthCache.clear();
    }
    editor.windowRender.currentMaxWindowLineWidth = 0f;
    editor.windowRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;
    editor.windowRender.recalculateMaxLineWidth();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    editor.invalidate();
  }

  public void setWhitespaceGuidesSpaceStep(int spacesPerDot) {
  int safeStep = Math.max(1, spacesPerDot);
    if (whitespaceGuideSpaceStep == safeStep) return;
    whitespaceGuideSpaceStep = safeStep;
    
    editor.bracketGuides.invalidateBracketGuideCache();
    editor.highlite.invalidateHighlightEnsureRange();
    synchronized (editor.windowRender.lineWidthCache) {
      editor.windowRender.lineWidthCache.clear();
    }
    editor.windowRender.currentMaxWindowLineWidth = 0f;
    editor.windowRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;
    editor.windowRender.recalculateMaxLineWidth();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
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
  
  
  /**
   * Updates metrics when text size changes.
   */
  public void updateMetrics() {
    whitespaceGuidePaint.setTextSize(editor.textRender.paint.getTextSize());
    whitespaceGuidePaint.setTypeface(editor.textRender.paint.getTypeface());
    whitespaceGuideSpaceWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_SPACE);
    whitespaceGuideTabWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_TAB);
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
public void drawWhitespaceGuidesForRangeRtl(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    int syntaxIndex = 0;
    com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan activeSyntax =
        hasSyntaxSpans && syntaxIndex < syntaxSpans.size() ? syntaxSpans.get(syntaxIndex) : null;

    Paint.FontMetrics dotFm = whitespaceGuidePaint.getFontMetrics();
    float dotY = y + (dotFm.ascent + dotFm.descent) * 0.5f;
    int spaceStep = getWhitespaceGuideStep();

    String sub = line.substring(start, end);
    Bidi bidi = new Bidi(sub, Bidi.DIRECTION_RIGHT_TO_LEFT);
    int runCount = bidi.getRunCount();

    float runX = 0f;
    int pointCount = 0;

    for (int run = 0; run < runCount; run++) {
      int runStart = start + bidi.getRunStart(run);
      int runLimit = start + bidi.getRunLimit(run);
      boolean runRtl = (bidi.getRunLevel(run) & 1) != 0;
      int runLen = runLimit - runStart;
      if (runLen <= 0) continue;

      if (whitespaceWidthBuffer == null || whitespaceWidthBuffer.length < runLen) {
        whitespaceWidthBuffer = new float[Math.max(runLen, 64)];
      }
      editor.textRender.paint.getTextWidths(line, runStart, runLimit, whitespaceWidthBuffer);

      float runWidth = 0f;
      for (int i = 0; i < runLen; i++) {
        char c = line.charAt(runStart + i);
        float adv =
            (c == '\t')
                ? editor.textRender.getVisualTabWidth(editor.textRender.paint)
                : editor.textRender.getCharAdvanceWidth(c, whitespaceWidthBuffer[i], editor.textRender.paint);
        runWidth += adv;
      }

      float advanceSoFar = 0f;
      int spaceSeqIndex = 0;
      for (int i = 0; i < runLen; i++) {
        int charIndex = runStart + i;
        while (activeSyntax != null && charIndex >= activeSyntax.end) {
          syntaxIndex++;
          activeSyntax = syntaxIndex < syntaxSpans.size() ? syntaxSpans.get(syntaxIndex) : null;
        }
        boolean inSyntax =
            activeSyntax != null
                && charIndex >= activeSyntax.start
                && charIndex < activeSyntax.end;

        char c = line.charAt(charIndex);
        float adv =
            (c == '\t')
                ? editor.textRender.getVisualTabWidth(editor.textRender.paint)
                : editor.textRender.getCharAdvanceWidth(c, whitespaceWidthBuffer[i], editor.textRender.paint);

        if (!inSyntax && c == ' ') {
          if (spaceStep <= 1 || (spaceSeqIndex % spaceStep) == 0) {
            float center = advanceSoFar + adv * 0.5f;
            float dotX = runRtl ? (runX + (runWidth - center)) : (runX + center);
            if (whitespaceDotBuffer == null || whitespaceDotBuffer.length < pointCount + 2) {
              float[] expanded = new float[Math.max(pointCount + 2, 64)];
              if (whitespaceDotBuffer != null && pointCount > 0) {
                System.arraycopy(whitespaceDotBuffer, 0, expanded, 0, pointCount);
              }
              whitespaceDotBuffer = expanded;
            }
            whitespaceDotBuffer[pointCount++] = dotX;
            whitespaceDotBuffer[pointCount++] = dotY;
          }
          spaceSeqIndex++;
        } else {
          spaceSeqIndex = 0;
        }

        if (!inSyntax && c == '\t') {
          float offset = Math.max(0f, (adv - whitespaceGuideTabWidth) * 0.5f);
          float glyphX =
              runRtl ? (runX + (runWidth - (advanceSoFar + adv)) + offset) : (runX + advanceSoFar + offset);
          canvas.drawText(WHITESPACE_GUIDE_TAB, glyphX, y, whitespaceGuidePaint);
        }

        advanceSoFar += adv;
      }

      runX += runWidth;
    }

    if (pointCount > 0) {
      canvas.drawPoints(whitespaceDotBuffer, 0, pointCount, whitespaceGuideDotPaint);
    }
  }
  /**
   * Draws whitespace guides for a line.
   */
  public void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
    if (!isWhitespaceGuidesEnabled || editor.isHeavyDrawSuppressed() || line.isEmpty()) return;
    if (line.indexOf(' ') < 0 && line.indexOf('\t') < 0) return;

    if (editor.textRender.isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, 0, line.length(), y);
      return;
    }

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    editor.view.whitespaceDrawState.syntaxIndex = 0;
    float rtlWidth = 0f;

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> visualSpans = editor.highlite.highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = 0;

    if (!visualSpans.isEmpty()) {
      for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan span : visualSpans) {
        if (span.start < lastEnd) continue;
        if (span.start >= line.length()) break;

        int safeSpanEnd = Math.min(span.end, line.length());
        if (span.start > lastEnd) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, lastEnd, span.start, currentX, y,
              editor.textRender.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
        }

        currentX = drawWhitespaceGuidesSegment(
            canvas, line, span.start, safeSpanEnd, currentX, y,
            span.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
        lastEnd = safeSpanEnd;
      }
    }

    if (lastEnd < line.length()) {
      drawWhitespaceGuidesSegment(
          canvas, line, lastEnd, line.length(), currentX, y,
          editor.textRender.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
    }
  }

  /**
   * Draws whitespace guides for a segment.
   */
  public void drawWhitespaceGuidesForSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (!isWhitespaceGuidesEnabled || editor.isHeavyDrawSuppressed() || line == null || line.isEmpty())
      return;
    if (editor.textRender.isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, start, end, y);
      return;
    }
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    editor.view.whitespaceDrawState.syntaxIndex = 0;
    boolean mirrorRtl = editor.textRender.isRtl && !com.yn.sodiumeditor.utils.TextArabicUtils.isMixedDirectionText(line, start, end);
    float rtlWidth = mirrorRtl ? editor.highlite.measureHighlightedSegmentWidth(line, globalLine, start, end) : 0f;

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> visualSpans = editor.highlite.highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = start;

    if (!visualSpans.isEmpty()) {
      for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan span : visualSpans) {
        if (lastEnd >= end) break;
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, lastEnd, segStart, currentX, y,
              editor.textRender.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
        }

        if (segEnd > segStart) {
          currentX = drawWhitespaceGuidesSegment(
              canvas, line, segStart, segEnd, currentX, y,
              span.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawWhitespaceGuidesSegment(
          canvas, line, lastEnd, end, currentX, y,
          editor.textRender.paint, syntaxSpans, hasSyntaxSpans, editor.view.whitespaceDrawState, rtlWidth);
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
      List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> syntaxSpans,
      boolean hasSyntaxSpans,
      com.yn.sodiumeditor.core.view.View.WhitespaceDrawState state,
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
    com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan activeSyntax =
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
        float runWidth =
            editor.textRender.getCharAdvanceWidth(c, whitespaceWidthBuffer[i], segmentPaint);
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
          char runChar = line.charAt(runCharIndex);
          if (inSyntax || runChar != ' ') break;
          runWidth += editor.textRender.getCharAdvanceWidth(runChar, whitespaceWidthBuffer[j], segmentPaint);
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
          float visualWidth =
              editor.textRender.getCharAdvanceWidth(
                  ' ', whitespaceWidthBuffer[runStart + k], segmentPaint);
          if (spaceStep <= 1 || (k % spaceStep) == 0) {
            float dotX = runCursorX + visualWidth * 0.5f;
            if (editor.textRender.isRtl && rtlWidth > 0f) {
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
        float charWidth = editor.textRender.getVisualTabWidth(segmentPaint);
        float glyphX = currentX + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        if (editor.textRender.isRtl && rtlWidth > 0f) {
          glyphX = rtlWidth - (currentX + charWidth)
              + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        }
        canvas.drawText(WHITESPACE_GUIDE_TAB, glyphX, y, whitespaceGuidePaint);
        currentX += charWidth;
        continue;
      }
      currentX += editor.textRender.getCharAdvanceWidth(c, whitespaceWidthBuffer[i], segmentPaint);
    }

    if (hasSyntaxSpans) {
      state.syntaxIndex = localSyntaxIndex;
    }
    return currentX;
  }

  /**
   * Gets syntax spans for whitespace guide rendering.
   */
  public List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> syntaxSpans = null;
    Paint stringPaint = editor.highlite.stringHighlightRule != null ? editor.highlite.stringHighlightRule.paint : null;
    Paint commentPaint = editor.highlite.blockCommentHighlightRule != null ? editor.highlite.blockCommentHighlightRule.paint : null;

    List<com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, spans);
    }

    for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightSpan span : spans) {
      if (span.paint == stringPaint || span.paint == commentPaint) {
        if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
        syntaxSpans.add(span);
      }
    }
    return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
  }
}
