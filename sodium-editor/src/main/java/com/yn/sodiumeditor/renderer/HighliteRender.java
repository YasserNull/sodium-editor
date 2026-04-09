package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HighliteRender handles syntax highlighting rendering logic for SodiumEditor.
 */
public class HighliteRender {

    private final SodiumEditor editor;
    
    // Moved from TextRender
    public final RectF binaryTokenRect = new RectF();
    public int maxSyntaxLineLength = 4096;
    public int prefetchCols = 100;
    public int visibleCharPadding = 2;

    // Highlight span class
    public static class HighlightSpan {
        public final int start;
        public final int end;
        public final Paint paint;

        public HighlightSpan(int start, int end, Paint paint) {
            this.start = start;
            this.end = end;
            this.paint = paint;
        }
    }

    // Highlight rule class
    public static class HighlightRule {
        public final HighlightRuleType type;
        public final Pattern pattern;
        public final Paint paint;
        public final int style;
        public final boolean underline;

        public HighlightRule(
                String regex,
                int style,
                int color,
                float baseTextSize,
                Typeface baseTypeface,
                boolean underline,
                HighlightRuleType type) {
            this.type = type;
            if (type == HighlightRuleType.REGEX) {
                this.pattern = Pattern.compile(regex);
            } else {
                this.pattern = null;
            }
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setColor(color);
            this.paint.setTextSize(baseTextSize);
            this.style = style;
            this.underline = underline;

            int typefaceStyle;
            switch (style) {
                case SodiumEditor.STYLE_BOLD:
                    typefaceStyle = Typeface.BOLD;
                    break;
                case SodiumEditor.STYLE_ITALIC:
                    typefaceStyle = Typeface.ITALIC;
                    break;
                case SodiumEditor.STYLE_BOLD_ITALIC:
                    typefaceStyle = Typeface.BOLD_ITALIC;
                    break;
                default:
                    typefaceStyle = Typeface.NORMAL;
                    break;
            }

            this.paint.setTypeface(Typeface.create(baseTypeface, typefaceStyle));
            this.paint.setUnderlineText(underline);
        }

        public void updateTextSize(float size) {
            paint.setTextSize(size);
        }

        public void updateTypeface(Typeface baseTypeface) {
            int typefaceStyle;
            switch (style) {
                case SodiumEditor.STYLE_BOLD:
                    typefaceStyle = Typeface.BOLD;
                    break;
                case SodiumEditor.STYLE_ITALIC:
                    typefaceStyle = Typeface.ITALIC;
                    break;
                case SodiumEditor.STYLE_BOLD_ITALIC:
                    typefaceStyle = Typeface.BOLD_ITALIC;
                    break;
                default:
                    typefaceStyle = Typeface.NORMAL;
                    break;
            }
            paint.setTypeface(Typeface.create(baseTypeface, typefaceStyle));
        }
    }

    // Highlight rule type enum
    public enum HighlightRuleType {
        REGEX,
        STRING,
        BLOCK_COMMENT,
        LINE_COMMENT
    }

    // Line parse result
    public static class LineParseResult {
        public final List<HighlightSpan> spans;
        public final boolean endsInBlockComment;
        public final int endsInStringState;

        public LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
            this.spans = spans;
            this.endsInBlockComment = endsInBlockComment;
            this.endsInStringState = endsInStringState;
        }
    }

    // Highlight line state
    public static class HighlightLineState {
        public final boolean inBlockComment;
        public final int stringState;

        public HighlightLineState(boolean inBlockComment, int stringState) {
            this.inBlockComment = inBlockComment;
            this.stringState = stringState;
        }
    }

    public HighliteRender(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Get paint for a specific character based on syntax highlighting
     */
    public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
        List<HighlightSpan> spans = editor.highlite.highlightCache.get(lineIndex);
        if (spans == null) {
            spans = editor.highlite.calculateSpansForLine(lineText, lineIndex);
            editor.highlite.highlightCache.put(lineIndex, spans);
        }
        for (HighlightSpan span : spans) {
            if (charIndex >= span.start && charIndex < span.end) {
                return span.paint;
            }
        }
        return editor.textRender.paint;
    }

    /**
     * Draw a highlighted line with syntax highlighting, underlines, and animations
     */
    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
        // Fast path for binary rendering - completely bypass normal highlighting
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
            editor.textRender.getVisibleCharRangeForLine(line, globalLine, editor.textRender.visibleCharRangeTmp);
            int visibleStart = editor.textRender.visibleCharRangeTmp[0];
            int visibleEnd   = editor.textRender.visibleCharRangeTmp[1];
            if (visibleEnd > visibleStart) {
                int sliceStart = editor.getStreamedLineSliceStart(globalLine);
                int sliceEnd   = sliceStart + line.length();
                int drawStart  = Math.max(visibleStart, sliceStart);
                int drawEnd    = Math.min(visibleEnd,   sliceEnd);
                if (drawEnd > drawStart) {
                    int relStart = Math.max(0, drawStart - sliceStart);
                    int relEnd   = Math.max(relStart, Math.min(line.length(), drawEnd - sliceStart));
                    
                    // Use optimized binary line drawing
                    editor.binaryRender.drawBinaryLineSlice(
                        canvas, line, globalLine, relStart, relEnd, sliceStart, y, editor.textRender.paint);
                }
            }
            // Draw error underlines even in binary mode
            editor.errorUnderline.drawErrorUnderlinesForLine(canvas, line, globalLine, y, 
                editor.textRender.getDrawLineTop(globalLine), editor.textRender.getDrawLineBottom(globalLine));
            return;
        }
        if (line.isEmpty()) {
            // Avoid drawing delete-animation ghosts on fully empty lines.
            return;
        }

        // Get visible character range
        editor.textRender.getVisibleCharRangeForLine(line, globalLine, editor.textRender.visibleCharRangeTmp);
        int visibleStart = editor.textRender.visibleCharRangeTmp[0];
        int visibleEnd = editor.textRender.visibleCharRangeTmp[1];
        int len = editor.getLogicalLineLength(globalLine, line);

        // Handle lines exceeding max syntax length
        if (len > maxSyntaxLineLength) {
            if (visibleEnd > visibleStart) {
                int sliceStart = editor.getStreamedLineSliceStart(globalLine);
                int sliceEnd = sliceStart + line.length();
                int drawStart = Math.max(visibleStart, sliceStart);
                int drawEnd = Math.min(visibleEnd, sliceEnd);
                if (drawEnd > drawStart) {
                    float avg = editor.textRender.getAverageCharWidthForLine(line, globalLine);
                    float x = avg * drawStart;
                    canvas.drawText(line, drawStart - sliceStart, drawEnd - sliceStart, x, y, editor.textRender.paint);
                }
            }
            return;
        }

        // Handle partial visibility
        if (visibleStart > 0 || visibleEnd < len) {
            drawHighlightedLineRange(canvas, line, globalLine, visibleStart, visibleEnd, y);
            return;
        }

        // Collect underlines
        ArrayList<TextRender.UnderlineSpan> combinedUnderlines = TextRender.TL_UNDERLINES.get();
        combinedUnderlines.clear();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<TextRender.UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<TextRender.UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
            if (pathSpans != null) combinedUnderlines.addAll(pathSpans);
        }
        if (combinedUnderlines.size() > 1) {
            Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
        }

        // Handle fade animation
        int fadeStart = -1;
        int fadeEnd = -1;
        float fadeAlpha = 1f;
        if (globalLine == editor.charAnimation.charAnimLine
                && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
                && editor.charAnimation.charAnimAlpha < 1f) {
            fadeStart = Math.max(0, Math.min(editor.charAnimation.charAnimStartChar, line.length()));
            fadeEnd = Math.max(0, Math.min(editor.charAnimation.charAnimEndChar, line.length()));
            fadeAlpha = Math.max(0f, Math.min(1f, editor.charAnimation.charAnimAlpha));
            if (fadeEnd <= fadeStart) {
                fadeStart = -1;
                fadeEnd = -1;
            }
        }

        float lineTop = editor.textRender.getDrawLineTop(globalLine);
        float lineBottom = lineTop + editor.textRender.lineHeight;

        // Draw with or without syntax highlighting
        if (editor.highlite.highlightRules.isEmpty()) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);

            // Draw delete animation
            if (globalLine == editor.charAnimation.delAnimLine
                    && editor.charAnimation.delAnimText != null
                    && !editor.charAnimation.delAnimText.isEmpty()
                    && editor.charAnimation.delAnimAlpha > 0f) {
                int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
                float x = editor.measureText(line, at, globalLine);
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : editor.textRender.paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
            editor.errorUnderline.drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
            return;
        }

        // Get syntax highlight spans
        List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
        if (spans == null) {
            spans = editor.highlite.calculateSpansForLine(line, globalLine);
            editor.highlite.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);

            if (globalLine == editor.charAnimation.delAnimLine
                    && editor.charAnimation.delAnimText != null
                    && !editor.charAnimation.delAnimText.isEmpty()
                    && editor.charAnimation.delAnimAlpha > 0f) {
                int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
                float x = editor.measureText(line, at, globalLine);
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : editor.textRender.paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
            editor.errorUnderline.drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
            return;
        }

        // Draw with syntax highlighting
        float currentX = 0f;
        int lastEnd = 0;

        for (HighlightSpan span : spans) {
            if (span.start < lastEnd) continue;
            if (span.start >= line.length()) break;
            int safeSpanEnd = Math.min(span.end, line.length());

            if (span.start > lastEnd) {
                currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                        canvas, line, lastEnd, span.start, currentX, y, editor.textRender.paint,
                        fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            }

            currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, span.start, safeSpanEnd, currentX, y, span.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            lastEnd = safeSpanEnd;
        }

        if (lastEnd < line.length()) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, lastEnd, line.length(), currentX, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
        }

        // Draw delete animation
        if (globalLine == editor.charAnimation.delAnimLine
                && editor.charAnimation.delAnimText != null
                && !editor.charAnimation.delAnimText.isEmpty()
                && editor.charAnimation.delAnimAlpha > 0f) {
            int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
            float x = editor.measureText(line, at, globalLine);
            Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : editor.textRender.paint;
            editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
            editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
            int baseAlpha = ghostPaint.getAlpha();
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
            canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
        }
        editor.errorUnderline.drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
    }

    /**
     * Draw a highlighted line range
     */
    public void drawHighlightedLineRange(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        if (line == null || line.isEmpty()) return;
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (start >= end) return;

        // Collect underlines
        ArrayList<TextRender.UnderlineSpan> combinedUnderlines = TextRender.TL_UNDERLINES.get();
        combinedUnderlines.clear();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<TextRender.UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<TextRender.UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
            if (pathSpans != null) combinedUnderlines.addAll(pathSpans);
        }
        if (combinedUnderlines.size() > 1) {
            Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
        }

        // Handle fade animation
        int fadeStart = -1;
        int fadeEnd = -1;
        float fadeAlpha = 1f;
        if (editor.charAnimation.isCharAnimationEnabled
                && globalLine == editor.charAnimation.charAnimLine
                && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
                && editor.charAnimation.charAnimAlpha < 1f) {
            fadeStart = Math.max(0, Math.min(editor.charAnimation.charAnimStartChar, line.length()));
            fadeEnd = Math.max(0, Math.min(editor.charAnimation.charAnimEndChar, line.length()));
            fadeAlpha = Math.max(0f, Math.min(1f, editor.charAnimation.charAnimAlpha));
            if (fadeEnd <= fadeStart) {
                fadeStart = -1;
                fadeEnd = -1;
            }
        }

        float lineTop = editor.textRender.getDrawLineTop(globalLine);
        float lineBottom = lineTop + editor.textRender.lineHeight;
        float currentX = editor.measureText(line, start, globalLine);
        int lastEnd = start;

        if (editor.highlite.highlightRules.isEmpty()) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, start, end, currentX, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
        } else {
            List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
            if (spans == null) {
                spans = editor.highlite.calculateSpansForLine(line, globalLine);
                editor.highlite.highlightCache.put(globalLine, spans);
            }
            for (HighlightSpan span : spans) {
                if (span.end <= start) continue;
                if (span.start >= end) break;

                int segStart = Math.max(start, span.start);
                int segEnd = Math.min(end, span.end);

                if (segStart > lastEnd) {
                    currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, lastEnd, segStart, currentX, y, editor.textRender.paint,
                            fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
                }
                if (segEnd > segStart) {
                    currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, segStart, segEnd, currentX, y, span.paint,
                            fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
                }
                lastEnd = Math.max(lastEnd, segEnd);
            }
            if (lastEnd < end) {
                editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                        canvas, line, lastEnd, end, currentX, y, editor.textRender.paint,
                        fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            }
        }

        // Draw delete animation
        if (editor.charAnimation.isCharAnimationEnabled
                && globalLine == editor.charAnimation.delAnimLine
                && editor.charAnimation.delAnimText != null
                && !editor.charAnimation.delAnimText.isEmpty()
                && editor.charAnimation.delAnimAlpha > 0f) {
            int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
            if (at >= start && at <= end) {
                float x = editor.measureText(line, at, globalLine);
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : editor.textRender.paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
        }
        editor.errorUnderline.drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
    }

    /**
     * Draw highlighted line segment
     */
    public void drawHighlightedLineSegment(
            Canvas canvas, String line, int globalLine, int start, int end, float y, float lineTop, float lineBottom) {
        if (line == null || line.isEmpty() || start >= end) return;
        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));
        if (start >= end) return;

        final List<TextRender.UnderlineSpan> urlUnderlines = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);

        int fadeStart = -1;
        int fadeEnd = -1;
        float fadeAlpha = 1f;
        if (editor.charAnimation.isCharAnimationEnabled
                && globalLine == editor.charAnimation.charAnimLine
                && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
                && editor.charAnimation.charAnimAlpha < 1f) {
            fadeStart = Math.max(0, Math.min(editor.charAnimation.charAnimStartChar, line.length()));
            fadeEnd = Math.max(0, Math.min(editor.charAnimation.charAnimEndChar, line.length()));
            fadeAlpha = Math.max(0f, Math.min(1f, editor.charAnimation.charAnimAlpha));
            if (fadeEnd <= fadeStart) {
                fadeStart = -1;
                fadeEnd = -1;
            }
        }

        if (editor.highlite.highlightRules.isEmpty()) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, start, end, 0f, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
            return;
        }

        List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
        if (spans == null) {
            spans = editor.highlite.calculateSpansForLine(line, globalLine);
            editor.highlite.highlightCache.put(globalLine, spans);
        }

        float currentX = 0f;
        int lastEnd = start;

        if (!spans.isEmpty()) {
            for (HighlightSpan span : spans) {
                if (lastEnd >= end) break;
                if (span.end <= start) continue;
                if (span.start >= end) break;

                int segStart = Math.max(start, span.start);
                int segEnd = Math.min(end, span.end);

                if (segStart > lastEnd) {
                    currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, lastEnd, segStart, currentX, y, editor.textRender.paint,
                            fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
                }

                if (segEnd > segStart) {
                    currentX += editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, segStart, segEnd, currentX, y, span.paint,
                            fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
                }
                lastEnd = Math.max(lastEnd, segEnd);
            }
        }

        if (lastEnd < end) {
            editor.textRender.drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, lastEnd, end, currentX, y, editor.textRender.paint,
                    fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
        }
    }
    
    public void setMaxSyntaxLineLength(int maxChars) {
        int safe = Math.max(512, maxChars);
        if (maxSyntaxLineLength == safe) return;
        maxSyntaxLineLength = safe;
        editor.clearHighlightCaches();
        editor.invalidate();
    }

    public void setPrefetchCols(int cols) {
        int safe = Math.max(0, cols);
        if (prefetchCols == safe) return;
        prefetchCols = safe;
        editor.invalidate();
    }
}
