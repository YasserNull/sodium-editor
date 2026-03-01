package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.core.HighlightRule;
import com.yn.sodiumeditor.state.HighlightSpan;
import com.yn.sodiumeditor.state.WhitespaceGuideState;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renderer class for whitespace guides.
 * Handles drawing whitespace characters (spaces and tabs) with visual indicators.
 */
public class WhitespaceGuideRenderer {

    public final Paint whitespaceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint whitespaceGuideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float whitespaceGuideSpaceWidth = 0f;
    private float whitespaceGuideTabWidth = 0f;

    private final WhitespaceGuideState state;

    public WhitespaceGuideRenderer(WhitespaceGuideState state) {
        this.state = state;
        whitespaceGuidePaint.setStyle(Paint.Style.FILL);
        whitespaceGuidePaint.setUnderlineText(false);
        whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
        whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void initPaints(int color) {
        whitespaceGuidePaint.setColor(color);
        whitespaceGuideDotPaint.setColor(color);
        float dotSize = Math.max(1f, whitespaceGuidePaint.getTextSize() / 7f);
        whitespaceGuideDotPaint.setStrokeWidth(dotSize);
    }

    public void setColor(int color) {
        whitespaceGuidePaint.setColor(color);
        whitespaceGuideDotPaint.setColor(color);
    }

    public void updateMetrics(Paint basePaint, String spaceGlyph, String tabGlyph) {
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

    public void updateTypeface(Paint basePaint) {
        whitespaceGuidePaint.setTypeface(basePaint.getTypeface());
    }

    public void updateRuleTextSize(float sizePx, HighlightRule stringRule, HighlightRule commentRule) {
        if (stringRule != null) stringRule.updateTextSize(sizePx);
        if (commentRule != null) commentRule.updateTextSize(sizePx);
    }

    public void updateRuleTypeface(Typeface baseTypeface, HighlightRule stringRule, HighlightRule commentRule) {
        if (stringRule != null) stringRule.updateTypeface(baseTypeface);
        if (commentRule != null) commentRule.updateTypeface(baseTypeface);
    }

    public float getSpaceWidth() {
        return whitespaceGuideSpaceWidth;
    }

    public float getTabWidth() {
        return whitespaceGuideTabWidth;
    }

    public float getVisualSpaceWidth(Paint p) {
        return p.measureText(" ");
    }

    public float getVisualTabWidth(Paint p, int tabSpaces) {
        return getVisualSpaceWidth(p) * tabSpaces;
    }

    public float getCharAdvanceWidth(char c, float measuredWidth, Paint p, int tabSpaces) {
        if (c == ' ') {
            return measuredWidth;
        }
        if (c == '\t') {
            return getVisualTabWidth(p, tabSpaces);
        }
        return measuredWidth;
    }

    public float measureTextWithVisualSpaces(
            SodiumEditorView view, String text, int start, int end, Paint p) {
        if (text == null) return 0f;
        start = Math.max(0, Math.min(start, text.length()));
        end = Math.max(start, Math.min(end, text.length()));
        if (start >= end) return 0f;

        if (text.indexOf('\t', start) < 0) {
            return p.measureText(text, start, end);
        }

        int len = end - start;
        float[] widths = state.ensureMeasureWidthBuffer(len);
        p.getTextWidths(text, start, end, widths);
        float total = 0f;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(start + i);
            total += getCharAdvanceWidth(c, widths[i], p, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
        }
        return total;
    }

    public List<HighlightSpan> calculateSyntaxSpansForLine(
            SodiumEditorView view, String line, int globalLine) {
        if (view.getLogicalLineLength(globalLine, line) > view.highlightState.maxSyntaxLineLength) {
            return Collections.emptyList();
        }
        if (line.isEmpty()) {
            return Collections.emptyList();
        }

        com.yn.sodiumeditor.state.HighlightLineState startState =
                view.highlightState.getLineStateAtStart(globalLine);
        com.yn.sodiumeditor.core.HighlightParser.LineParseResult parseResult =
                view.highlightParser.parseLineForSyntax(
                        line,
                        startState.inBlockComment,
                        startState.stringState,
                        view.highlightState.stringHighlightRule,
                        view.highlightState.blockCommentHighlightRule,
                        false);

        if (globalLine >= view.windowStartLine
                && globalLine < view.windowStartLine + view.linesWindow.size()) {
            if (view.isBlockCommentsEnabledForBracket()) {
                view.highlightState.blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
            }
            view.highlightState.stringEndStateCache.put(globalLine, parseResult.endsInStringState);
        }

        List<HighlightSpan> spans = parseResult.spans;
        if (spans.size() > 1) {
            Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
        }
        return spans;
    }

    public List<HighlightSpan> getWhitespaceGuideSyntaxSpans(
            SodiumEditorView view, String line, int globalLine) {
        HighlightRule stringRule = view.highlightState.stringHighlightRule;
        HighlightRule commentRule = view.highlightState.blockCommentHighlightRule;
        if (stringRule == null && commentRule == null) {
            return calculateSyntaxSpansForLine(view, line, globalLine);
        }

        List<HighlightSpan> spans = view.highlightState.highlightCache.get(globalLine);
        if (spans == null) {
            spans = view.highlightRenderer.calculateSpansForLine(line, globalLine);
            view.highlightState.highlightCache.put(globalLine, spans);
        }
        if (spans.isEmpty()) return Collections.emptyList();

        Paint stringPaint = (stringRule != null) ? stringRule.paint : null;
        Paint commentPaint = (commentRule != null) ? commentRule.paint : null;
        if (stringPaint == null && commentPaint == null) return Collections.emptyList();

        ArrayList<HighlightSpan> syntaxSpans = null;
        for (HighlightSpan span : spans) {
            if (span.paint == stringPaint || span.paint == commentPaint) {
                if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
                syntaxSpans.add(span);
            }
        }
        return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
    }

    public void drawWhitespaceGuidesForSegment(
            SodiumEditorView view,
            Canvas canvas,
            String line,
            int globalLine,
            int start,
            int end,
            float y) {
        if (!view.isWhitespaceGuidesEnabledForBracket()
                || view.isHeavyDrawSuppressedForBracket()
                || line == null
                || line.isEmpty())
            return;
        if (view.isRtl) {
            drawWhitespaceGuidesForRangeRtl(view, canvas, line, globalLine, start, end, y);
            return;
        }
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return;
        if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

        List<HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(view, line, globalLine);
        boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
        state.resetSyntaxIndex();
        boolean mirrorRtl = view.isRtl && !view.isMixedDirectionText(line, start, end);
        float rtlWidth = mirrorRtl ? view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, start, end) : 0f;

        List<HighlightSpan> visualSpans = view.highlightState.highlightCache.get(globalLine);
        if (visualSpans == null) {
            visualSpans = view.highlightRenderer.calculateSpansForLine(line, globalLine);
            view.highlightState.highlightCache.put(globalLine, visualSpans);
        }
        boolean hasVisualSpans = !visualSpans.isEmpty();

        int len = end - start;
        float[] widths = state.ensureWhitespaceWidthBuffer(len);
        float[] dots = state.ensureWhitespaceDotBuffer(len);
        whitespaceGuidePaint.getTextWidths(line, start, end, widths);

        float x = mirrorRtl ? rtlWidth : 0f;
        int spaceCount = 0;

        for (int i = 0; i < len; i++) {
            char c = line.charAt(start + i);
            if (c == ' ' || c == '\t') {
                boolean inStringOrComment = false;
                if (hasSyntaxSpans) {
                    while (state.getSyntaxIndex() < syntaxSpans.size()) {
                        HighlightSpan span = syntaxSpans.get(state.getSyntaxIndex());
                        if (span.end <= start + i) {
                            state.incrementSyntaxIndex();
                        } else if (span.start > start + i) {
                            break;
                        } else {
                            inStringOrComment = true;
                            break;
                        }
                    }
                }
                if (hasVisualSpans && !inStringOrComment) {
                    for (HighlightSpan span : visualSpans) {
                        if (span.end <= start + i) continue;
                        if (span.start > start + i) break;
                        inStringOrComment = true;
                        break;
                    }
                }
                if (!inStringOrComment) {
                    float w = widths[i];
                    if (c == '\t') {
                        w = getVisualTabWidth(whitespaceGuidePaint, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
                    }
                    dots[spaceCount] = w;
                    spaceCount++;
                }
            }
            x += widths[i];
        }

        x = mirrorRtl ? rtlWidth : 0f;
        int dotIndex = 0;
        for (int i = 0; i < len; i++) {
            char c = line.charAt(start + i);
            if (c == ' ' || c == '\t') {
                boolean inStringOrComment = false;
                if (hasSyntaxSpans) {
                    while (state.getSyntaxIndex() < syntaxSpans.size()) {
                        HighlightSpan span = syntaxSpans.get(state.getSyntaxIndex());
                        if (span.end <= start + i) {
                            state.incrementSyntaxIndex();
                        } else if (span.start > start + i) {
                            break;
                        } else {
                            inStringOrComment = true;
                            break;
                        }
                    }
                }
                if (hasVisualSpans && !inStringOrComment) {
                    for (HighlightSpan span : visualSpans) {
                        if (span.end <= start + i) continue;
                        if (span.start > start + i) break;
                        inStringOrComment = true;
                        break;
                    }
                }
                if (!inStringOrComment && dotIndex < spaceCount) {
                    float w = dots[dotIndex];
                    float cx = x + w / 2f;
                    if (c == ' ') {
                        if (state.getSpaceStep() > 1 && (spaceCount % state.getSpaceStep()) != 0) {
                            // Skip drawing dot
                        } else {
                            canvas.drawCircle(cx, y, w / 6f, whitespaceGuideDotPaint);
                        }
                    } else {
                        float tabX = x;
                        int tabSpaces = com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
                        for (int t = 0; t < tabSpaces; t++) {
                            float segmentW = w / tabSpaces;
                            float segmentCx = tabX + segmentW / 2f;
                            if (state.getSpaceStep() > 1 && (t % state.getSpaceStep()) != 0) {
                                // Skip drawing dot
                            } else {
                                canvas.drawCircle(segmentCx, y, segmentW / 6f, whitespaceGuideDotPaint);
                            }
                            tabX += segmentW;
                        }
                    }
                    dotIndex++;
                }
            }
            x += widths[i];
        }
    }

    public void drawWhitespaceGuidesForRangeRtl(
            SodiumEditorView view,
            Canvas canvas,
            String line,
            int globalLine,
            int start,
            int end,
            float y) {
        if (!view.isWhitespaceGuidesEnabledForBracket()
                || view.isHeavyDrawSuppressedForBracket()
                || line == null
                || line.isEmpty())
            return;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return;
        if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

        Bidi bidi = new Bidi(line, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
        int len = end - start;
        float[] widths = state.ensureWhitespaceWidthBuffer(len);
        whitespaceGuidePaint.getTextWidths(line, start, end, widths);

        float totalWidth = 0f;
        for (int i = 0; i < len; i++) {
            totalWidth += widths[i];
        }

        float x = totalWidth;
        for (int i = 0; i < len; i++) {
            char c = line.charAt(start + i);
            if (c == ' ') {
                float cx = x - widths[i] / 2f;
                if (state.getSpaceStep() > 1 && (i % state.getSpaceStep()) != 0) {
                    // Skip drawing dot
                } else {
                    canvas.drawCircle(cx, y, widths[i] / 6f, whitespaceGuideDotPaint);
                }
            } else if (c == '\t') {
                float tabW = getVisualTabWidth(whitespaceGuidePaint, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
                float tabX = x - tabW;
                int tabSpaces = com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
                for (int t = 0; t < tabSpaces; t++) {
                    float segmentW = tabW / tabSpaces;
                    float segmentCx = tabX + segmentW * (t + 0.5f);
                    if (state.getSpaceStep() > 1 && (t % state.getSpaceStep()) != 0) {
                        // Skip drawing dot
                    } else {
                        canvas.drawCircle(segmentCx, y, segmentW / 6f, whitespaceGuideDotPaint);
                    }
                }
            }
            x -= widths[i];
        }
    }
}
