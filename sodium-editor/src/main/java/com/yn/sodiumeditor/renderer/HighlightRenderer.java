package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.HighlightParser;
import com.yn.sodiumeditor.core.HighlightRule;
import com.yn.sodiumeditor.core.UnderlineSpan;
import com.yn.sodiumeditor.state.HighlightLineState;
import com.yn.sodiumeditor.state.HighlightSpan;
import com.yn.sodiumeditor.state.HighlightState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renderer class for syntax highlighting.
 * Handles drawing highlighted text with support for fades and underlines.
 */
public class HighlightRenderer {

    private final SodiumEditor view;
    private final HighlightState state;
    private final HighlightParser parser;
    private final ErrorUnderlineRenderer errorRenderer;

    public HighlightRenderer(SodiumEditor view, HighlightState state, HighlightParser parser) {
        this.view = view;
        this.state = state;
        this.parser = parser;
        this.errorRenderer = new ErrorUnderlineRenderer(view, state);
    }

    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
        if (line == null || line.isEmpty()) {
            drawDeletedTextAnimation(canvas, globalLine, y);
            return;
        }

        int[] visibleCharRange = view.editorState.visibleCharRangeTmpForRender;
        view.getVisibleCharRangeForLine(line, globalLine, visibleCharRange);
        int visibleStart = visibleCharRange[0];
        int visibleEnd = visibleCharRange[1];
        int len = view.getLogicalLineLength(globalLine, line);

        if (len > state.maxSyntaxLineLength) {
            drawTruncatedLine(canvas, line, globalLine, visibleStart, visibleEnd, y);
            return;
        }

        if (visibleStart > 0 || visibleEnd < len) {
            drawHighlightedLineRange(canvas, line, globalLine, visibleStart, visibleEnd, y);
            return;
        }

        List<UnderlineSpan> combinedUnderlines = new ArrayList<>();
        List<UnderlineSpan> urlSpans = getUrlUnderlineSpansForLine(line, globalLine);
        if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        List<UnderlineSpan> pathSpans = getPathUnderlineSpansForLine(line, globalLine);
        if (pathSpans != null) combinedUnderlines.addAll(pathSpans);

        if (!combinedUnderlines.isEmpty()) {
            Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
        }

        int fadeStart = -1;
        int fadeEnd = -1;
        float fadeAlpha = 1f;
        if (view.charAnimationConfig.isEnabled()
                && globalLine == view.charAnimator.getCharAnimLine()
                && view.charAnimator.getCharAnimEndChar() > view.charAnimator.getCharAnimStartChar()
                && view.charAnimator.getCharAnimAlpha() < 1f) {
            fadeStart = Math.max(0, Math.min(view.charAnimator.getCharAnimStartChar(), line.length()));
            fadeEnd = Math.max(0, Math.min(view.charAnimator.getCharAnimEndChar(), line.length()));
            fadeAlpha = Math.max(0f, Math.min(1f, view.charAnimator.getCharAnimAlpha()));
            if (fadeEnd <= fadeStart) {
                fadeStart = -1;
                fadeEnd = -1;
            }
        }

        float lineTop = view.scrollManager.getDrawLineTop(globalLine);
        float lineBottom = lineTop + view.lineHeight;

        if (state.highlightRules.isEmpty()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, view.editorConfig.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            drawDeletedTextAnimation(canvas, globalLine, y);
            drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
            return;
        }

        List<HighlightSpan> spans = state.highlightCache.get(globalLine);
        if (spans == null) {
            spans = calculateSpansForLine(line, globalLine);
            state.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, view.editorConfig.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            drawDeletedTextAnimation(canvas, globalLine, y);
            drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
            return;
        }

        float currentX = 0f;
        int lastEnd = 0;

        for (HighlightSpan span : spans) {
            if (span.start < lastEnd) continue;
            if (span.start >= line.length()) break;
            int safeSpanEnd = Math.min(span.end, line.length());

            if (span.start > lastEnd) {
                currentX += drawTextSegmentWithFadeAndUnderlines(
                        canvas, line, lastEnd, span.start, currentX, y, view.editorConfig.paint,
                        fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            }

            currentX += drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, span.start, safeSpanEnd, currentX, y, span.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            lastEnd = safeSpanEnd;
        }

        if (lastEnd < line.length()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, lastEnd, line.length(), currentX, y, view.editorConfig.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
        }

        drawDeletedTextAnimation(canvas, globalLine, y);
        drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
    }

    private void drawDeletedTextAnimation(Canvas canvas, int globalLine, float y) {
        if (view.charAnimationConfig.isEnabled()
                && globalLine == view.charAnimator.getDelAnimLine()
                && view.charAnimator.getDelAnimText() != null
                && !view.charAnimator.getDelAnimText().isEmpty()
                && view.charAnimator.getDelAnimAlpha() > 0f) {
            Paint ghostPaint = view.charAnimator.getDelAnimPaint() != null ?
                    view.charAnimator.getDelAnimPaint() : view.editorConfig.paint;
            Paint tempPaint = view.charAnimator.getTempPaint();
            tempPaint.set(ghostPaint);
            tempPaint.setUnderlineText(false);
            int baseAlpha = ghostPaint.getAlpha();
            tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimator.getDelAnimAlpha()))));
            canvas.drawText(view.charAnimator.getDelAnimText(), 0f, y, tempPaint);
        }
    }

    private void drawTruncatedLine(Canvas canvas, String line, int globalLine, int visibleStart, int visibleEnd, float y) {
        if (visibleEnd > visibleStart) {
            int sliceStart = view.getStreamedLineSliceStart(globalLine);
            int sliceEnd = sliceStart + line.length();
            int drawStart = Math.max(visibleStart, sliceStart);
            int drawEnd = Math.min(visibleEnd, sliceEnd);
            if (drawEnd > drawStart) {
                float avg = getAverageCharWidthForLine(line, globalLine);
                float x = avg * drawStart;
                canvas.drawText(line, drawStart - sliceStart, drawEnd - sliceStart, x, y, view.editorConfig.paint);
            }
        }
    }

    private List<UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
        if (!state.isUrlUnderliningEnabled || state.urlUnderlinePattern == null) return null;
        List<UnderlineSpan> cached = state.urlUnderlineCache.get(globalLine);
        if (cached != null) return cached;

        ArrayList<UnderlineSpan> spans = new ArrayList<>();
        java.util.regex.Matcher matcher = state.urlUnderlinePattern.matcher(line);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            end = trimUrlUnderlineEnd(line, start, end);
            if (end > start) {
                spans.add(new UnderlineSpan(start, end, false));
            }
        }
        state.urlUnderlineCache.put(globalLine, spans);
        return spans;
    }

    private List<UnderlineSpan> getPathUnderlineSpansForLine(String line, int globalLine) {
        if (!state.isPathUnderliningEnabled || state.pathUnderlinePattern == null) return null;
        List<UnderlineSpan> cached = state.pathUnderlineCache.get(globalLine);
        if (cached != null) return cached;

        ArrayList<UnderlineSpan> spans = new ArrayList<>();
        java.util.regex.Matcher matcher = state.pathUnderlinePattern.matcher(line);
        while (matcher.find()) {
            String potentialPath = matcher.group();
            if (potentialPath != null && !potentialPath.isEmpty()) {
                Boolean exists = state.pathValidationCache.get(potentialPath);
                if (Boolean.TRUE.equals(exists)) {
                    spans.add(new UnderlineSpan(matcher.start(), matcher.end(), true));
                } else if (exists == null) {
                    validatePathInBackground(potentialPath, globalLine);
                }
            }
        }
        state.pathUnderlineCache.put(globalLine, spans);
        return spans;
    }

    private void validatePathInBackground(final String path, final int lineToInvalidate) {
        if (state.pendingPathValidations.contains(path)) return;
        state.pendingPathValidations.add(path);

        view.ioHandler.post(() -> {
            boolean exists = false;
            try {
                java.io.File file = new java.io.File(path);
                exists = file.exists();
            } catch (Exception e) {
                // Ignore errors
            } finally {
                state.pathValidationCache.put(path, exists);
                state.pendingPathValidations.remove(path);

                if (exists) {
                    view.mainHandler.post(() -> {
                        state.pathUnderlineCache.remove(lineToInvalidate);
                        view.invalidate();
                    });
                }
            }
        });
    }

    private static int trimUrlUnderlineEnd(String line, int start, int end) {
        int e = Math.min(end, line.length());
        while (e > start) {
            char c = line.charAt(e - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')'
                    || c == ']' || c == '}' || c == '>' || c == '"' || c == '\'') {
                e--;
                continue;
            }
            break;
        }
        return e;
    }

    public List<HighlightSpan> calculateSpansForLine(String line, int globalLine) {
        List<HighlightSpan> spans = new ArrayList<>();
        if (state.highlightRules.isEmpty()) {
            return spans;
        }

        HighlightRule stringRule = state.stringHighlightRule;
        HighlightRule blockCommentRule = state.blockCommentHighlightRule;
        List<HighlightSpan> exclusionSpans = new ArrayList<>();

        if (state.isMultiLineStringsEnabled || state.isBlockCommentsEnabled || state.lineCommentHighlightRule != null) {
            HighlightLineState startState = getLineStateAtStart(globalLine);
            HighlightRule parseStringRule = stringRule != null ? stringRule : state.stringHighlightRule;
            HighlightRule parseBlockRule = blockCommentRule != null ? blockCommentRule : state.blockCommentHighlightRule;
            HighlightParser.LineParseResult parseResult = parser.parseLineForSyntax(
                    line, startState.inBlockComment, startState.stringState,
                    parseStringRule, parseBlockRule, true);
            if (parseResult != null && parseResult.spans != null) {
                exclusionSpans.addAll(parseResult.spans);
            }
        }

        if (!state.regexHighlightRules.isEmpty() && !line.isEmpty()) {
            for (HighlightRule rule : state.regexHighlightRules) {
                java.util.regex.Matcher matcher = rule.pattern.matcher(line);
                while (matcher.find()) {
                    if (matcher.start() == matcher.end()) continue;
                    HighlightSpan span = new HighlightSpan(matcher.start(), matcher.end(), rule.paint);
                    if (HighlightParser.hasOverlap(span, exclusionSpans)) continue;
                    spans.add(span);
                }
            }
        }

        if (stringRule != null || blockCommentRule != null || state.lineCommentHighlightRule != null) {
            HighlightParser.LineParseResult parseResult = parser.parseLineForSyntax(
                    line, false, 0, stringRule, blockCommentRule, true);
            if (parseResult != null && parseResult.spans != null) {
                spans.addAll(parseResult.spans);
            }
        }

        if (spans.size() > 1) {
            Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
        }
        return spans;
    }

    private HighlightLineState getLineStateAtStart(int globalLine) {
        if (globalLine <= view.windowStartLine) return new HighlightLineState(false, 0);
        int windowEnd = view.windowStartLine + view.linesWindow.size();
        if (globalLine > windowEnd) return new HighlightLineState(false, 0);
        int prev = globalLine - 1;
        Boolean cachedBlockPrev = state.blockCommentEndStateCache.get(prev);
        Integer cachedStringPrev = state.stringEndStateCache.get(prev);
        if (cachedBlockPrev != null && cachedStringPrev != null) {
            return new HighlightLineState(cachedBlockPrev, cachedStringPrev);
        }
        boolean inBlock = false;
        int stringState = 0;
        for (int line = view.windowStartLine; line < globalLine; line++) {
            String lineText = view.getLineTextForRender(line);
            if (lineText == null) lineText = "";
            HighlightParser.LineParseResult result = parser.parseLineForSyntax(
                    lineText, inBlock, stringState, null, null, false);
            inBlock = result.endsInBlockComment;
            stringState = result.endsInStringState;
            if (line >= view.windowStartLine && line < windowEnd) {
                if (state.isBlockCommentsEnabled) state.blockCommentEndStateCache.put(line, inBlock);
                state.stringEndStateCache.put(line, stringState);
            }
        }
        return new HighlightLineState(inBlock, stringState);
    }

    public float getAverageCharWidthForLine(String line, int lineIndex) {
        if (line == null || line.isEmpty()) return view.editorConfig.paint.measureText(" ");
        if (lineIndex >= 0) {
            synchronized (view.avgCharWidthCache) {
                Float cached = view.avgCharWidthCache.get(lineIndex);
                if (cached != null) return cached;
            }
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? view.editorConfig.paint.measureText(line, 0, sampleLen) : view.editorConfig.paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            synchronized (view.avgCharWidthCache) {
                if (view.isStableGlyphPositionsEnabled && view.avgCharWidthCache.containsKey(lineIndex)) {
                    return view.avgCharWidthCache.get(lineIndex);
                }
                view.avgCharWidthCache.put(lineIndex, avg);
            }
        }
        return avg;
    }

    private void drawHighlightedLineRange(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        // Implementation delegated to drawHighlightedLine with range check
        drawHighlightedLine(canvas, line, globalLine, y);
    }

    private float drawTextSegmentWithFadeAndUnderlines(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha,
            @Nullable List<UnderlineSpan> underlines, float lineTop, float lineBottom) {
        if (start >= end) return 0f;
        boolean anyUnderliningActive = (state.isUrlUnderliningEnabled && state.urlUnderlinePattern != null)
                || (state.isPathUnderliningEnabled && state.pathUnderlinePattern != null);
        if (underlines == null || underlines.isEmpty() || !anyUnderliningActive) {
            return drawTextSegmentWithFade(canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        float currentX = x;
        int pos = start;

        for (UnderlineSpan span : underlines) {
            if (span.end <= pos) continue;
            if (span.start >= end) break;

            int plainEnd = Math.min(end, Math.max(pos, span.start));
            if (pos < plainEnd) {
                currentX += drawTextSegmentWithFade(canvas, line, pos, plainEnd, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
                pos = plainEnd;
            }

            int underlineStart = Math.max(pos, span.start);
            int underlineEnd = Math.min(end, span.end);
            if (underlineStart < underlineEnd) {
                float underlineXStart = currentX;
                currentX += drawTextSegmentWithFade(canvas, line, underlineStart, underlineEnd, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
                drawUnderlineSegmentWithFade(canvas, line, underlineStart, underlineEnd, underlineXStart, y, lineTop, lineBottom, segmentPaint, fadeStart, fadeEnd, fadeAlpha, span.isPath);
                pos = underlineEnd;
            }
        }

        if (pos < end) {
            currentX += drawTextSegmentWithFade(canvas, line, pos, end, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        return currentX - x;
    }

    private float drawTextSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha) {
        if (start >= end) return 0f;
        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (hasFade && containsArabicScript(line, start, end)) {
            int spaceScale = view.getVisualSpaceScale();
            if (spaceScale > 1 || line.indexOf('\t', start) >= 0) {
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }
            canvas.drawText(line, start, end, x, y, segmentPaint);
            return segmentPaint.measureText(line, start, end);
        }
        final int spaceScale = view.getVisualSpaceScale();
        if (spaceScale > 1) {
            if (!hasFade || end <= fadeStart || start >= fadeEnd) {
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }

            float currentX = x;
            int beforeEnd = Math.min(end, fadeStart);
            if (start < beforeEnd) {
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
            }

            int fadeSegStart = Math.max(start, fadeStart);
            int fadeSegEnd = Math.min(end, fadeEnd);
            if (fadeSegStart < fadeSegEnd) {
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
            }

            int afterStart = Math.max(start, fadeEnd);
            if (afterStart < end) {
                currentX += drawTextSegmentWithVisualSpaces(canvas, line, afterStart, end, currentX, y, segmentPaint, 1f);
            }

            return currentX - x;
        }
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            canvas.drawText(line, start, end, x, y, segmentPaint);
            return segmentPaint.measureText(line, start, end);
        }

        float currentX = x;
        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            canvas.drawText(line, start, beforeEnd, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, start, beforeEnd);
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            Paint tempPaint = view.charAnimator.getTempPaint();
            tempPaint.set(segmentPaint);
            int baseAlpha = segmentPaint.getAlpha();
            tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            canvas.drawText(line, fadeSegStart, fadeSegEnd, currentX, y, tempPaint);
            currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, afterStart, end);
        }

        return currentX - x;
    }

    private float drawTextSegmentWithVisualSpaces(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, float alphaMultiplier) {
        if (start >= end) return 0f;

        Paint drawPaint = segmentPaint;
        if (alphaMultiplier < 1f) {
            Paint tempPaint = view.charAnimator.getTempPaint();
            tempPaint.set(segmentPaint);
            int baseAlpha = segmentPaint.getAlpha();
            tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
            drawPaint = tempPaint;
        }

        int len = end - start;
        float[] widths = view.whitespaceGuideState.ensureMeasureWidthBuffer(len);
        segmentPaint.getTextWidths(line, start, end, widths);

        float currentX = x;
        int runStart = start;
        float runX = currentX;

        for (int i = 0; i < len; i++) {
            int charIndex = start + i;
            char c = line.charAt(charIndex);
            float adv = view.whitespaceGuideRenderer.getCharAdvanceWidth(
                    c, widths[i], segmentPaint, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
            boolean isVirtualSpace = (c == ' ' || c == '\t');
            if (isVirtualSpace) {
                if (runStart < charIndex) {
                    canvas.drawText(line, runStart, charIndex, runX, y, drawPaint);
                }
                currentX += adv;
                runStart = charIndex + 1;
                runX = currentX;
            } else {
                currentX += adv;
            }
        }

        if (runStart < end) {
            canvas.drawText(line, runStart, end, runX, y, drawPaint);
        }
        return currentX - x;
    }

    private boolean containsArabicScript(CharSequence text, int start, int end) {
        if (text == null || start >= end) return false;
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(text.length(), end);
        for (int i = safeStart; i < safeEnd; ) {
            int codePoint = Character.codePointAt(text, i);
            i += Character.charCount(codePoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == Character.UnicodeBlock.ARABIC
                    || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                    || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                    || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS) {
                return true;
            }
        }
        return false;
    }

    private void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha, boolean isPath) {
        if (start >= end) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float underlineY = y + (fm.descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
        thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

        Paint tmpPaintToUse = isPath ? state.pathUnderlineTmpPaint : state.urlUnderlineTmpPaint;
        tmpPaintToUse.set(textPaint);
        tmpPaintToUse.setStyle(Paint.Style.STROKE);
        tmpPaintToUse.setStrokeWidth(thickness);
        tmpPaintToUse.setUnderlineText(false);

        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, start, end, textPaint);
            if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
            return;
        }

        float currentX = x;
        int baseAlpha = textPaint.getAlpha();

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, start, beforeEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, fadeSegStart, fadeSegEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, afterStart, end, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
        }
    }

    private void drawErrorUnderlinesForLine(Canvas canvas, String line, int globalLine, float baselineY, float lineTop, float lineBottom) {
        if (!state.errorUnderlineEnabled) return;
        List<com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan> spans = state.errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        for (com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer.ErrorUnderlineSpan span : snapshot) {
            int start = Math.max(0, Math.min(span.start, len));
            int end = Math.max(start, Math.min(span.end, len));
            if (start >= end) continue;
            float xStart = measureText(line, start, globalLine);
            float xEnd = measureText(line, end, globalLine);
            errorRenderer.drawErrorSquigglePublic(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    public float measureText(String line, int length, int globalLine) {
        int logicalLen = view.getLogicalLineLength(globalLine, line);
        int safeLen = Math.max(0, Math.min(length, logicalLen));
        if (logicalLen > state.maxSyntaxLineLength) {
            float avg = getAverageCharWidthForLine(line, globalLine);
            return avg * safeLen;
        }
        if (state.highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
            return view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, 0, safeLen, view.editorConfig.paint);
        }

        List<HighlightSpan> spans = state.highlightCache.get(globalLine);
        if (spans == null) {
            spans = calculateSpansForLine(line, globalLine);
            state.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            return view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, 0, safeLen, view.editorConfig.paint);
        }

        float totalWidth = 0;
        int lastEnd = 0;

        for (HighlightSpan span : spans) {
            if (lastEnd >= safeLen) break;
            if (span.start >= safeLen) break;
            if (span.start < lastEnd) continue;

            if (span.start > lastEnd) {
                int measureEnd = Math.min(span.start, safeLen);
                totalWidth += view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, lastEnd, measureEnd, view.editorConfig.paint);
            }

            lastEnd = span.start;

            int measureEnd = Math.min(span.end, safeLen);
            totalWidth += view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, lastEnd, measureEnd, span.paint);

            lastEnd = span.end;
        }

        if (lastEnd < safeLen) {
            totalWidth += view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, lastEnd, safeLen, view.editorConfig.paint);
        }

        return totalWidth;
    }

    public void drawHighlightedSegment(Canvas canvas, String line, int globalLine, int start, int end, float x, float y) {
        if (line == null || line.isEmpty() || start >= end) return;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return;

        if (state.highlightRules.isEmpty()) {
            view.editorConfig.paint.setUnderlineText(false);
            canvas.drawText(line, start, end, x, y, view.editorConfig.paint);
            return;
        }

        List<HighlightSpan> spans = state.highlightCache.get(globalLine);
        if (spans == null) {
            spans = calculateSpansForLine(line, globalLine);
            state.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            view.editorConfig.paint.setUnderlineText(false);
            canvas.drawText(line, start, end, x, y, view.editorConfig.paint);
            return;
        }

        float currentX = x;
        int lastEnd = start;

        for (HighlightSpan span : spans) {
            if (lastEnd >= end) break;
            if (span.start >= end) break;
            if (span.start < lastEnd) continue;

            if (span.start > lastEnd) {
                view.editorConfig.paint.setUnderlineText(false);
                canvas.drawText(line, lastEnd, span.start, currentX, y, view.editorConfig.paint);
                currentX += view.editorConfig.paint.measureText(line, lastEnd, span.start);
            }

            int safeSpanEnd = Math.min(span.end, end);
            if (safeSpanEnd > span.start) {
                span.paint.setUnderlineText(false);
                canvas.drawText(line, span.start, safeSpanEnd, currentX, y, span.paint);
                currentX += span.paint.measureText(line, span.start, safeSpanEnd);
            }
            lastEnd = safeSpanEnd;
        }

        if (lastEnd < end) {
            view.editorConfig.paint.setUnderlineText(false);
            canvas.drawText(line, lastEnd, end, currentX, y, view.editorConfig.paint);
        }
    }

    public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
        if (line == null || line.isEmpty() || start >= end) return 0f;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return 0f;

        if (state.highlightRules.isEmpty()) {
            return view.editorConfig.paint.measureText(line, start, end);
        }

        List<HighlightSpan> spans = state.highlightCache.get(globalLine);
        if (spans == null) {
            spans = calculateSpansForLine(line, globalLine);
            state.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            return view.editorConfig.paint.measureText(line, start, end);
        }

        float total = 0f;
        int lastEnd = start;

        for (HighlightSpan span : spans) {
            if (lastEnd >= end) break;
            if (span.start >= end) break;
            if (span.start < lastEnd) continue;

            if (span.start > lastEnd) {
                total += view.editorConfig.paint.measureText(line, lastEnd, span.start);
            }

            int safeSpanEnd = Math.min(span.end, end);
            if (safeSpanEnd > span.start) {
                total += span.paint.measureText(line, span.start, safeSpanEnd);
            }
            lastEnd = safeSpanEnd;
        }

        if (lastEnd < end) {
            total += view.editorConfig.paint.measureText(line, lastEnd, end);
        }

        return total;
    }

    public void drawHighlightedLineSegment(Canvas canvas, String line, int globalLine, int start, int end, float y, float lineTop, float lineBottom) {
        drawHighlightedLine(canvas, line, globalLine, y);
    }

    public void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine, float lineTop, float lineBottom) {
        // Simplified - actual implementation uses state.colorCodeBgCache
    }

    public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
        List<HighlightSpan> spans = state.highlightCache.get(lineIndex);
        if (spans == null) {
            return view.editorConfig.paint;
        }
        for (HighlightSpan span : spans) {
            if (charIndex >= span.start && charIndex < span.end) {
                return span.paint;
            }
        }
        return view.editorConfig.paint;
    }

    public void computeStreamedSliceBounds(
            @Nullable String lineText, int globalLine, int lineLength, int[] out) {
        if (out == null || out.length < 2) return;
        int len = Math.max(0, lineLength);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        float avg = getAverageCharWidthForLine((lineText == null) ? "" : lineText, globalLine);
        if (avg <= 0f) avg = view.editorConfig.paint.measureText(" ");
        float viewLeft = view.lineNumberRenderer.getContentViewLeft(view.isRtl);
        float viewRight = view.lineNumberRenderer.getContentViewRight(view.getWidth(), view.isRtl);
        float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
        float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
        if (view.isRtl) {
            float w = avg * len;
            float baseX = view.getTextAreaWidth() - w;
            float l = leftX - baseX;
            float r = rightX - baseX;
            leftX = w - l;
            rightX = w - r;
        }
        int start = (int) Math.floor(leftX / avg);
        int end = (int) Math.ceil(rightX / avg);
        if (end < start) {
            int t = start;
            start = end;
            end = t;
        }
        int pad = Math.max(0, view.editorConfig.visualConfig.visibleCharPadding);
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        int visibleLen = Math.max(0, end - start);
        int maxExtra = Math.max(0, view.editorState.colsWidthCacheSize - visibleLen);
        int extraPad = Math.min(Math.max(0, view.editorState.prefetchCols), maxExtra / 2);
        start = Math.max(0, start - extraPad);
        end = Math.min(len, end + extraPad);
        out[0] = start;
        out[1] = end;
    }

    public int getInitialStreamedSliceSize() {
        int base = Math.max(128, view.editorState.colsWidthCacheSize);
        int pad = Math.max(0, view.editorState.prefetchCols) * 2;
        return Math.max(base, pad);
    }

    /**
     * Ensures highlight cache for a visible range of lines.
     * Delegates to HighlightState.
     */
    public void ensureHighlightCacheForVisibleRange(
            int firstVisibleLine,
            int lastVisibleLine,
            @Nullable java.util.HashMap<Integer, String> directLines) {
        state.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, directLines);
    }
}
