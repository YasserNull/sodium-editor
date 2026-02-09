package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WhitespaceGuideManager {
  final Paint whitespaceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint whitespaceGuideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  float whitespaceGuideSpaceWidth = 0f;
  float whitespaceGuideTabWidth = 0f;
  float[] whitespaceWidthBuffer;
  float[] whitespaceDotBuffer;
  float[] measureWidthBuffer;
  int whitespaceGuideSpaceStep = 1;

  static final class WhitespaceDrawState {
    int syntaxIndex;
  }

  final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();
  HighlightManager.HighlightRule whitespaceStringRule;
  HighlightManager.HighlightRule whitespaceCommentRule;

  void initPaints(int color) {
    whitespaceGuidePaint.setColor(color);
    whitespaceGuidePaint.setStyle(Paint.Style.FILL);
    whitespaceGuidePaint.setUnderlineText(false);
    whitespaceGuideDotPaint.setColor(color);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  void setColor(int color) {
    whitespaceGuidePaint.setColor(color);
    whitespaceGuideDotPaint.setColor(color);
  }

  void updateMetrics(Paint basePaint, String spaceGlyph, String tabGlyph) {
    whitespaceGuidePaint.setTextSize(basePaint.getTextSize());
    whitespaceGuidePaint.setTypeface(basePaint.getTypeface());
    whitespaceGuideSpaceWidth = whitespaceGuidePaint.measureText(spaceGlyph);
    whitespaceGuideTabWidth = whitespaceGuidePaint.measureText(tabGlyph);
    whitespaceGuideDotPaint.setColor(whitespaceGuidePaint.getColor());
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    float dotSize = Math.max(1f, basePaint.getTextSize() / 7f);
    whitespaceGuideDotPaint.setStrokeWidth(dotSize);
  }

  void updateTypeface(Paint basePaint) {
    whitespaceGuidePaint.setTypeface(basePaint.getTypeface());
  }

  int getSpaceStep() {
    return whitespaceGuideSpaceStep;
  }

  boolean setSpaceStep(int step) {
    if (whitespaceGuideSpaceStep == step) return false;
    whitespaceGuideSpaceStep = step;
    return true;
  }

  void updateRuleTextSize(float sizePx) {
    if (whitespaceStringRule != null) whitespaceStringRule.updateTextSize(sizePx);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTextSize(sizePx);
  }

  void ensureRules(float textSizePx, Typeface typeface) {
    if (whitespaceStringRule == null) {
      whitespaceStringRule =
          new HighlightManager.HighlightRule(
              "",
              SodiumEditorView.STYLE_NORMAL,
              0xFF000000,
              textSizePx,
              typeface,
              false,
              HighlightManager.HighlightRuleType.STRING);
    }
    if (whitespaceCommentRule == null) {
      whitespaceCommentRule =
          new HighlightManager.HighlightRule(
              "",
              SodiumEditorView.STYLE_NORMAL,
              0xFF000000,
              textSizePx,
              typeface,
              false,
              HighlightManager.HighlightRuleType.BLOCK_COMMENT);
    }
  }

  void updateRuleTypeface(Typeface baseTypeface) {
    if (whitespaceStringRule != null) whitespaceStringRule.updateTypeface(baseTypeface);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTypeface(baseTypeface);
  }

  Paint getGuidePaint() {
    return whitespaceGuidePaint;
  }

  Paint getDotPaint() {
    return whitespaceGuideDotPaint;
  }

  float getSpaceWidth() {
    return whitespaceGuideSpaceWidth;
  }

  float getTabWidth() {
    return whitespaceGuideTabWidth;
  }

  WhitespaceDrawState getDrawState() {
    return whitespaceDrawState;
  }

  HighlightManager.HighlightRule getStringRule() {
    return whitespaceStringRule;
  }

  HighlightManager.HighlightRule getCommentRule() {
    return whitespaceCommentRule;
  }

  float[] ensureMeasureWidthBuffer(int len) {
    if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
      measureWidthBuffer = new float[len];
    }
    return measureWidthBuffer;
  }

  float[] ensureWhitespaceWidthBuffer(int len) {
    if (whitespaceWidthBuffer == null || whitespaceWidthBuffer.length < len) {
      whitespaceWidthBuffer = new float[len];
    }
    return whitespaceWidthBuffer;
  }

  float[] ensureWhitespaceDotBuffer(int len) {
    if (whitespaceDotBuffer == null || whitespaceDotBuffer.length < len) {
      float[] expanded = new float[len];
      if (whitespaceDotBuffer != null && whitespaceDotBuffer.length > 0) {
        System.arraycopy(
            whitespaceDotBuffer, 0, expanded, 0, Math.min(whitespaceDotBuffer.length, len));
      }
      whitespaceDotBuffer = expanded;
    }
    return whitespaceDotBuffer;
  }

  float getVisualSpaceWidth(Paint p) {
    return p.measureText(" ");
  }

  float getVisualTabWidth(Paint p, int tabSpaces) {
    return getVisualSpaceWidth(p) * tabSpaces;
  }

  float getCharAdvanceWidth(char c, float measuredWidth, Paint p, int tabSpaces) {
    if (c == ' ') {
      return measuredWidth;
    }
    if (c == '\t') {
      return getVisualTabWidth(p, tabSpaces);
    }
    return measuredWidth;
  }

  float measureTextWithVisualSpaces(
      SodiumEditorView view, String text, int start, int end, Paint p) {
    if (text == null) return 0f;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (start >= end) return 0f;

    if (text.indexOf('\t', start) < 0) {
      return p.measureText(text, start, end);
    }

    int len = end - start;
    float[] widths = ensureMeasureWidthBuffer(len);
    p.getTextWidths(text, start, end, widths);
    float total = 0f;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(start + i);
      total +=
          getCharAdvanceWidth(
              c, widths[i], p, view.getDefaultTabSizeSpacesForWhitespace());
    }
    return total;
  }

  List<HighlightManager.HighlightSpan> calculateSyntaxSpansForLine(
      SodiumEditorView view, String line, int globalLine) {
    if (view.getLogicalLineLengthForWhitespace(globalLine, line)
        > view.getMaxSyntaxLineLengthForWhitespace()) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    HighlightManager.HighlightLineState startState =
        view.getLineStateAtStartForWhitespace(globalLine);
    HighlightManager.LineParseResult parseResult =
        view.parseLineForSyntaxForWhitespace(
            line,
            startState.inBlockComment,
            startState.stringState,
            getStringRule(),
            getCommentRule());

    if (globalLine >= view.getWindowStartLineForWhitespace()
        && globalLine < view.getWindowStartLineForWhitespace() + view.getWindowLineCountForWhitespace()) {
      if (view.isBlockCommentsEnabledForWhitespace()) {
        view.cacheBlockCommentEndStateForWhitespace(globalLine, parseResult.endsInBlockComment);
      }
      view.cacheStringEndStateForWhitespace(globalLine, parseResult.endsInStringState);
    }

    List<HighlightManager.HighlightSpan> spans = parseResult.spans;
    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  List<HighlightManager.HighlightSpan> getWhitespaceGuideSyntaxSpans(
      SodiumEditorView view, String line, int globalLine) {
    HighlightManager.HighlightRule stringRule = view.getStringHighlightRuleForWhitespace();
    HighlightManager.HighlightRule commentRule = view.getBlockCommentHighlightRuleForWhitespace();
    if (stringRule == null && commentRule == null) {
      return calculateSyntaxSpansForLine(view, line, globalLine);
    }

    List<HighlightManager.HighlightSpan> spans = view.getHighlightCacheForWhitespace(globalLine);
    if (spans == null) {
      spans = view.calculateSpansForLineForWhitespace(line, globalLine);
      view.putHighlightCacheForWhitespace(globalLine, spans);
    }
    if (spans.isEmpty()) return Collections.emptyList();

    Paint stringPaint = (stringRule != null) ? stringRule.paint : null;
    Paint commentPaint = (commentRule != null) ? commentRule.paint : null;
    if (stringPaint == null && commentPaint == null) return Collections.emptyList();

    ArrayList<HighlightManager.HighlightSpan> syntaxSpans = null;
    for (HighlightManager.HighlightSpan span : spans) {
      if (span.paint == stringPaint || span.paint == commentPaint) {
        if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
        syntaxSpans.add(span);
      }
    }
    return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
  }

  void drawWhitespaceGuidesForSegment(
      SodiumEditorView view,
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float y) {
    if (!view.isWhitespaceGuidesEnabledForWhitespace()
        || view.isHeavyDrawSuppressedForWhitespace()
        || line == null
        || line.isEmpty())
      return;
    if (view.isRtlForWhitespace()) {
      drawWhitespaceGuidesForRangeRtl(view, canvas, line, globalLine, start, end, y);
      return;
    }
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<HighlightManager.HighlightSpan> syntaxSpans =
        getWhitespaceGuideSyntaxSpans(view, line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    getDrawState().syntaxIndex = 0;
    boolean mirrorRtl =
        view.isRtlForWhitespace() && !view.isMixedDirectionTextForWhitespace(line, start, end);
    float rtlWidth =
        mirrorRtl
            ? view.measureHighlightedSegmentWidthForWhitespace(line, globalLine, start, end)
            : 0f;

    List<HighlightManager.HighlightSpan> visualSpans =
        view.getHighlightCacheForWhitespace(globalLine);
    if (visualSpans == null) {
      visualSpans = view.calculateSpansForLineForWhitespace(line, globalLine);
      view.putHighlightCacheForWhitespace(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = start;

    if (!visualSpans.isEmpty()) {
      for (HighlightManager.HighlightSpan span : visualSpans) {
        if (lastEnd >= end) break;
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX =
              drawWhitespaceGuidesSegment(
                  view,
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  view.getBasePaintForWhitespace(),
                  syntaxSpans,
                  hasSyntaxSpans,
                  getDrawState(),
                  rtlWidth);
        }

        if (segEnd > segStart) {
          currentX =
              drawWhitespaceGuidesSegment(
                  view,
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  syntaxSpans,
                  hasSyntaxSpans,
                  getDrawState(),
                  rtlWidth);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawWhitespaceGuidesSegment(
          view,
          canvas,
          line,
          lastEnd,
          end,
          currentX,
          y,
          view.getBasePaintForWhitespace(),
          syntaxSpans,
          hasSyntaxSpans,
          getDrawState(),
          rtlWidth);
    }
  }

  void drawWhitespaceGuidesForLine(
      SodiumEditorView view, Canvas canvas, String line, int globalLine, float y) {
    if (!view.isWhitespaceGuidesEnabledForWhitespace()
        || view.isHeavyDrawSuppressedForWhitespace()
        || line.isEmpty()) return;
    if (line.indexOf(' ') < 0 && line.indexOf('\t') < 0) return;
    if (view.isRtlForWhitespace()) {
      drawWhitespaceGuidesForRangeRtl(view, canvas, line, globalLine, 0, line.length(), y);
      return;
    }

    List<HighlightManager.HighlightSpan> syntaxSpans =
        getWhitespaceGuideSyntaxSpans(view, line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    getDrawState().syntaxIndex = 0;
    float rtlWidth = 0f;

    List<HighlightManager.HighlightSpan> visualSpans =
        view.getHighlightCacheForWhitespace(globalLine);
    if (visualSpans == null) {
      visualSpans = view.calculateSpansForLineForWhitespace(line, globalLine);
      view.putHighlightCacheForWhitespace(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = 0;

    if (!visualSpans.isEmpty()) {
      for (HighlightManager.HighlightSpan span : visualSpans) {
        if (span.start < lastEnd) continue;
        if (span.start >= line.length()) break;

        int safeSpanEnd = Math.min(span.end, line.length());
        if (span.start > lastEnd) {
          currentX =
              drawWhitespaceGuidesSegment(
                  view,
                  canvas,
                  line,
                  lastEnd,
                  span.start,
                  currentX,
                  y,
                  view.getBasePaintForWhitespace(),
                  syntaxSpans,
                  hasSyntaxSpans,
                  getDrawState(),
                  rtlWidth);
        }

        currentX =
            drawWhitespaceGuidesSegment(
                view,
                canvas,
                line,
                span.start,
                safeSpanEnd,
                currentX,
                y,
                span.paint,
                syntaxSpans,
                hasSyntaxSpans,
                getDrawState(),
                rtlWidth);
        lastEnd = safeSpanEnd;
      }
    }

    if (lastEnd < line.length()) {
      drawWhitespaceGuidesSegment(
          view,
          canvas,
          line,
          lastEnd,
          line.length(),
          currentX,
          y,
          view.getBasePaintForWhitespace(),
          syntaxSpans,
          hasSyntaxSpans,
          getDrawState(),
          rtlWidth);
    }
  }

  private void drawWhitespaceGuidesForRangeRtl(
      SodiumEditorView view,
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<HighlightManager.HighlightSpan> syntaxSpans =
        getWhitespaceGuideSyntaxSpans(view, line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    int syntaxIndex = 0;
    HighlightManager.HighlightSpan activeSyntax =
        hasSyntaxSpans && syntaxIndex < syntaxSpans.size() ? syntaxSpans.get(syntaxIndex) : null;

    Paint.FontMetrics dotFm = getGuidePaint().getFontMetrics();
    float dotY = y + (dotFm.ascent + dotFm.descent) * 0.5f;
    int spaceStep = view.getWhitespaceGuideStepForWhitespace();
    int tabSpaces = view.getDefaultTabSizeSpacesForWhitespace();
    String tabGlyph = view.getWhitespaceGuideTabGlyphForWhitespace();

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

      float[] widths = ensureWhitespaceWidthBuffer(Math.max(runLen, 64));
      view.getBasePaintForWhitespace().getTextWidths(line, runStart, runLimit, widths);

      float runWidth = 0f;
      for (int i = 0; i < runLen; i++) {
        char c = line.charAt(runStart + i);
        float adv =
            (c == '\t')
                ? getVisualTabWidth(view.getBasePaintForWhitespace(), tabSpaces)
                : getCharAdvanceWidth(c, widths[i], view.getBasePaintForWhitespace(), tabSpaces);
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
                ? getVisualTabWidth(view.getBasePaintForWhitespace(), tabSpaces)
                : getCharAdvanceWidth(c, widths[i], view.getBasePaintForWhitespace(), tabSpaces);

        if (!inSyntax && c == ' ') {
          if (spaceStep <= 1 || (spaceSeqIndex % spaceStep) == 0) {
            float center = advanceSoFar + adv * 0.5f;
            float dotX = runRtl ? (runX + (runWidth - center)) : (runX + center);
            float[] dots = ensureWhitespaceDotBuffer(Math.max(pointCount + 2, 64));
            dots[pointCount++] = dotX;
            dots[pointCount++] = dotY;
          }
          spaceSeqIndex++;
        } else {
          spaceSeqIndex = 0;
        }

        if (!inSyntax && c == '\t') {
          float offset = Math.max(0f, (adv - getTabWidth()) * 0.5f);
          float glyphX =
              runRtl ? (runX + (runWidth - (advanceSoFar + adv)) + offset) : (runX + advanceSoFar + offset);
          canvas.drawText(tabGlyph, glyphX, y, getGuidePaint());
        }

        advanceSoFar += adv;
      }

      runX += runWidth;
    }

    if (pointCount > 0) {
      canvas.drawPoints(ensureWhitespaceDotBuffer(pointCount), 0, pointCount, getDotPaint());
    }
  }

  private float drawWhitespaceGuidesSegment(
      SodiumEditorView view,
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      List<HighlightManager.HighlightSpan> syntaxSpans,
      boolean hasSyntaxSpans,
      WhitespaceDrawState state,
      float rtlWidth) {
    if (start >= end) return x;
    int segLen = end - start;
    float[] widths = ensureWhitespaceWidthBuffer(segLen);
    segmentPaint.getTextWidths(line, start, end, widths);

    final int spaceStep = view.getWhitespaceGuideStepForWhitespace();
    int tabSpaces = view.getDefaultTabSizeSpacesForWhitespace();
    String tabGlyph = view.getWhitespaceGuideTabGlyphForWhitespace();
    float currentX = x;
    int localSyntaxIndex = hasSyntaxSpans ? state.syntaxIndex : 0;
    HighlightManager.HighlightSpan activeSyntax =
        hasSyntaxSpans && localSyntaxIndex < syntaxSpans.size()
            ? syntaxSpans.get(localSyntaxIndex)
            : null;
    Paint.FontMetrics dotFm = getGuidePaint().getFontMetrics();
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
        float runWidth = widths[i];
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
          runWidth += widths[j];
          runEnd = j + 1;
        }

        int spacesInRun = runEnd - runStart;
        float runCursorX = currentX;
        int needed = spacesInRun * 2;
        float[] dots = ensureWhitespaceDotBuffer(Math.max(needed, 64));
        int pointCount = 0;
        for (int k = 0; k < spacesInRun; k++) {
          float visualWidth = widths[runStart + k];
          if (spaceStep <= 1 || (k % spaceStep) == 0) {
            float dotX = runCursorX + visualWidth * 0.5f;
            if (view.isRtlForWhitespace() && rtlWidth > 0f) {
              dotX = rtlWidth - dotX;
            }
            dots[pointCount++] = dotX;
            dots[pointCount++] = dotY;
          }
          runCursorX += visualWidth;
        }
        if (pointCount > 0) {
          canvas.drawPoints(dots, 0, pointCount, getDotPaint());
        }

        currentX += runWidth;
        i = runEnd - 1;
        continue;
      }

      if (!isInSyntaxSpan && c == '\t') {
        float charWidth = getVisualTabWidth(segmentPaint, tabSpaces);
        float glyphX = currentX + Math.max(0f, (charWidth - getTabWidth()) * 0.5f);
        if (view.isRtlForWhitespace() && rtlWidth > 0f) {
          glyphX = rtlWidth - (currentX + charWidth)
              + Math.max(0f, (charWidth - getTabWidth()) * 0.5f);
        }
        canvas.drawText(tabGlyph, glyphX, y, getGuidePaint());
        currentX += charWidth;
        continue;
      }
      currentX += widths[i];
    }

    if (hasSyntaxSpans) {
      state.syntaxIndex = localSyntaxIndex;
    }
    return currentX;
  }
}
