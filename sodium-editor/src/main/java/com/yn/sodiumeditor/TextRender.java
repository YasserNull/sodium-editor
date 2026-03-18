package com.yn.sodiumeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.WordWrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TextRender handles all text rendering algorithms for SodiumEditor.
 * This includes drawing text, handling syntax highlighting, underlines,
 * whitespace guides, and other visual text rendering operations.
 */
public class TextRender {

    // Reference to the parent SodiumEditor
    private final SodiumEditor editor;

    // Paint and metrics (delegated from SodiumEditor)
    public final Paint paint;
    public Typeface baseTypeface;
    public float lineHeight;
    public float paddingLeft;
    public boolean isRtl;
    public final Rect textBounds;

    // Visual padding constants
    public static final float BOTTOM_SCROLL_OFFSET = 100f;
    public static final float MIN_BOTTOM_VISIBLE_SPACE = 50f;

    // Whitespace drawing state
    public float[] guideSeenXBuffer;

    public float[] whitespaceWidthBuffer;
    public float[] whitespaceDotBuffer;
    public float[] measureWidthBuffer;
    public static final int DEFAULT_TAB_SIZE_SPACES = 4;

    public static final class WhitespaceDrawState {
        public int syntaxIndex;
    }

    public final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();

    // Temporary maps for direct lines
    public final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();

    // Fold marker path and scale
    public final Path teardropPath = new Path();

    // Editor background
    public boolean hasEditorBackgroundColor = false;
    public int editorBackgroundColor = 0x00000000;
    @Nullable public Bitmap editorBackgroundBitmap = null;
    public final Rect editorBackgroundDst = new Rect();


    // Whitespace guide constants
    public HighlightRule whitespaceStringRule;
    public HighlightRule whitespaceCommentRule;
    
    // Error underline
    public int errorUnderlineColor = 0xFFE53935;
    public boolean errorUnderlineEnabled = true;
    public final Paint errorUnderlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Path errorUnderlinePath = new Path();
    public float errorUnderlineHeightScale = 0.18f;
    public float errorUnderlineWaveLengthScale = 0.70f;
    public float errorUnderlineStrokeScale = 0.08f;
    public float errorUnderlineSmoothness = 3f;
    public final LinkedHashMap<Integer, List<ErrorUnderlineSpan>> errorUnderlineMap =
            new LinkedHashMap<Integer, List<ErrorUnderlineSpan>>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<ErrorUnderlineSpan>> eldest) {
                    return size() > 2000;
                }
            };

    // Visible character range
    public final int[] visibleCharRangeTmp = new int[2];
    public int visibleCharPadding = 2;
    public boolean isPerformanceModeEnabled = false;
    public boolean isStableGlyphPositionsEnabled = false;


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

    // Underline span class
    public static class UnderlineSpan {
        public final int start;
        public final int end;
        public final boolean isPath;

        public UnderlineSpan(int start, int end, boolean isPath) {
            this.start = start;
            this.end = end;
            this.isPath = isPath;
        }
    }

    // Error underline span class
    public static class ErrorUnderlineSpan {
        public final int start;
        public final int end;

        public ErrorUnderlineSpan(int start, int end) {
            this.start = start;
            this.end = end;
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

    public TextRender(SodiumEditor editor) {
        this.editor = editor;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.baseTypeface = Typeface.DEFAULT;
        this.lineHeight = 0f;
        this.paddingLeft = 0f;
        this.isRtl = false;
        this.textBounds = new Rect();
    }

    // ========================================================================
    // Core Drawing Methods
    // ========================================================================

    /**
     * Draw the editor background (color or bitmap)
     */
    public void drawEditorBackground(Canvas canvas) {
        if (hasEditorBackgroundColor) {
            canvas.drawColor(editorBackgroundColor);
        }
        if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
            editorBackgroundDst.set(0, 0, editor.getWidth(), editor.getHeight());
            canvas.drawBitmap(editorBackgroundBitmap, null, editorBackgroundDst, null);
        }
    }

    /**
     * Get paint for a specific character based on syntax highlighting
     */
    public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
        List<HighlightSpan> spans = editor.highlite.highlightCache.get(lineIndex);
        if (spans == null) {
            spans = editor.calculateSpansForLine(lineText, lineIndex);
            editor.highlite.highlightCache.put(lineIndex, spans);
        }
        for (HighlightSpan span : spans) {
            if (charIndex >= span.start && charIndex < span.end) {
                return span.paint;
            }
        }
        return paint;
    }

    /**
     * Get average character width for a line
     */
    public float getAverageCharWidthForLine(String line, int lineIndex) {
        if (line == null || line.isEmpty()) return paint.measureText(" ");
        if (lineIndex >= 0) {
            synchronized (editor.avgCharWidthCache) {
                Float cached = editor.avgCharWidthCache.get(lineIndex);
                if (cached != null) return cached;
            }
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? paint.measureText(line, 0, sampleLen) : paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            synchronized (editor.avgCharWidthCache) {
                if (isStableGlyphPositionsEnabled && editor.avgCharWidthCache.containsKey(lineIndex)) {
                    return editor.avgCharWidthCache.get(lineIndex);
                }
                editor.avgCharWidthCache.put(lineIndex, avg);
            }
        }
        return avg;
    }

    /**
     * Draw a highlighted line with syntax highlighting, underlines, and animations
     */
    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
        if (line.isEmpty()) {
            // Handle delete animation for empty lines
            if (globalLine == editor.charAnimation.delAnimLine
                    && editor.charAnimation.delAnimText != null
                    && !editor.charAnimation.delAnimText.isEmpty()
                    && editor.charAnimation.delAnimAlpha > 0f) {
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, 0f, y, editor.charAnimation.charAnimTmpPaint);
            }
            return;
        }

        // Get visible character range
        getVisibleCharRangeForLine(line, globalLine, visibleCharRangeTmp);
        int visibleStart = visibleCharRangeTmp[0];
        int visibleEnd = visibleCharRangeTmp[1];
        int len = editor.getLogicalLineLength(globalLine, line);

        // Handle lines exceeding max syntax length
        if (len > editor.maxSyntaxLineLength) {
            if (visibleEnd > visibleStart) {
                int sliceStart = editor.getStreamedLineSliceStart(globalLine);
                int sliceEnd = sliceStart + line.length();
                int drawStart = Math.max(visibleStart, sliceStart);
                int drawEnd = Math.min(visibleEnd, sliceEnd);
                if (drawEnd > drawStart) {
                    float avg = getAverageCharWidthForLine(line, globalLine);
                    float x = avg * drawStart;
                    canvas.drawText(line, drawStart - sliceStart, drawEnd - sliceStart, x, y, paint);
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
        List<UnderlineSpan> combinedUnderlines = new ArrayList<>();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
            if (pathSpans != null) combinedUnderlines.addAll(pathSpans);
        }
        if (!combinedUnderlines.isEmpty()) {
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

        float lineTop = getDrawLineTop(globalLine);
        float lineBottom = lineTop + lineHeight;

        // Draw with or without syntax highlighting
        if (editor.highlite.highlightRules.isEmpty()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);

            // Draw delete animation
            if (globalLine == editor.charAnimation.delAnimLine
                    && editor.charAnimation.delAnimText != null
                    && !editor.charAnimation.delAnimText.isEmpty()
                    && editor.charAnimation.delAnimAlpha > 0f) {
                int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
                float x = editor.measureText(line, at, globalLine);
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
            drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
            return;
        }

        // Get syntax highlight spans
        List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
        if (spans == null) {
            spans = editor.calculateSpansForLine(line, globalLine);
            editor.highlite.highlightCache.put(globalLine, spans);
        }

        if (spans.isEmpty()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, 0, line.length(), 0f, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);

            if (globalLine == editor.charAnimation.delAnimLine
                    && editor.charAnimation.delAnimText != null
                    && !editor.charAnimation.delAnimText.isEmpty()
                    && editor.charAnimation.delAnimAlpha > 0f) {
                int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
                float x = editor.measureText(line, at, globalLine);
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
            drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
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
                currentX += drawTextSegmentWithFadeAndUnderlines(
                        canvas, line, lastEnd, span.start, currentX, y, paint,
                        fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            }

            currentX += drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, span.start, safeSpanEnd, currentX, y, span.paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
            lastEnd = safeSpanEnd;
        }

        if (lastEnd < line.length()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, lastEnd, line.length(), currentX, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
        }

        // Draw delete animation
        if (globalLine == editor.charAnimation.delAnimLine
                && editor.charAnimation.delAnimText != null
                && !editor.charAnimation.delAnimText.isEmpty()
                && editor.charAnimation.delAnimAlpha > 0f) {
            int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
            float x = editor.measureText(line, at, globalLine);
            Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
            editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
            editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
            int baseAlpha = ghostPaint.getAlpha();
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
            canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
        }
        drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
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
        List<UnderlineSpan> combinedUnderlines = new ArrayList<>();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
            if (pathSpans != null) combinedUnderlines.addAll(pathSpans);
        }
        if (!combinedUnderlines.isEmpty()) {
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

        float lineTop = getDrawLineTop(globalLine);
        float lineBottom = lineTop + lineHeight;
        float currentX = editor.measureText(line, start, globalLine);
        int lastEnd = start;

        if (editor.highlite.highlightRules.isEmpty()) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, start, end, currentX, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
        } else {
            List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
            if (spans == null) {
                spans = editor.calculateSpansForLine(line, globalLine);
                editor.highlite.highlightCache.put(globalLine, spans);
            }
            for (HighlightSpan span : spans) {
                if (span.end <= start) continue;
                if (span.start >= end) break;

                int segStart = Math.max(start, span.start);
                int segEnd = Math.min(end, span.end);

                if (segStart > lastEnd) {
                    currentX += drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, lastEnd, segStart, currentX, y, paint,
                            fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
                }
                if (segEnd > segStart) {
                    currentX += drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, segStart, segEnd, currentX, y, span.paint,
                            fadeStart, fadeEnd, fadeAlpha, combinedUnderlines, lineTop, lineBottom);
                }
                lastEnd = Math.max(lastEnd, segEnd);
            }
            if (lastEnd < end) {
                drawTextSegmentWithFadeAndUnderlines(
                        canvas, line, lastEnd, end, currentX, y, paint,
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
                Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
                editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
                editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
                int baseAlpha = ghostPaint.getAlpha();
                editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
                canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
            }
        }
        drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
    }

    // ========================================================================
    // Visible Character Range Methods
    // ========================================================================

    /**
     * Get visible character range for a line
     */
    public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
        if (line == null || out == null || out.length < 2) return;
        int len = editor.getLogicalLineLength(globalLine, line);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        if (len > editor.maxSyntaxLineLength) {
            getVisibleCharRangeForLineFast(line, globalLine, len, out);
            return;
        }
        if (isStableGlyphPositionsEnabled) {
            out[0] = 0;
            out[1] = len;
            return;
        }
        float viewLeft = isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
        float leftX = viewLeft + editor.getEffectiveScrollX() - editor.getTextStartX();
        float rightX = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();

        int start = editor.getCharIndexForX(line, leftX, globalLine);
        int end = editor.getCharIndexForX(line, rightX, globalLine);
        if (end < start) {
            int t = start;
            start = end;
            end = t;
        }

        int pad = visibleCharPadding;
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Get visible character range for a line (fast version for long lines)
     */
    public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out) {
        int len = Math.max(0, lineLength);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        float avg = getAverageCharWidthForLine(line, globalLine);
        if (avg <= 0f) {
            out[0] = 0;
            out[1] = Math.min(len, Math.max(0, editor.prefetchCols));
            return;
        }
        float viewLeft = isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
        float leftX = viewLeft + editor.getEffectiveScrollX() - editor.getTextStartX();
        float rightX = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();
        if (isRtl) {
            float w = avg * len;
            float baseX = editor.getTextAreaWidth() - w;
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
        int pad = visibleCharPadding + Math.max(0, editor.prefetchCols);
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Compute streamed slice bounds
     */
    public void computeStreamedSliceBounds(@Nullable String lineText, int globalLine, int lineLength, int[] out) {
        if (out == null || out.length < 2) return;
        int len = Math.max(0, lineLength);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        float avg = getAverageCharWidthForLine((lineText == null) ? "" : lineText, globalLine);
        if (avg <= 0f) avg = paint.measureText(" ");
        float viewLeft = isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();
        float leftX = viewLeft + editor.getEffectiveScrollX() - editor.getTextStartX();
        float rightX = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();
        if (isRtl) {
            float w = avg * len;
            float baseX = editor.getTextAreaWidth() - w;
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
        int pad = Math.max(0, visibleCharPadding);
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        int visibleLen = Math.max(0, end - start);
        int maxExtra = Math.max(0, editor.colsWidthCacheSize - visibleLen);
        int extraPad = Math.min(Math.max(0, editor.prefetchCols), maxExtra / 2);
        start = Math.max(0, start - extraPad);
        end = Math.min(len, end + extraPad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Get initial streamed slice size
     */
    public int getInitialStreamedSliceSize() {
        int base = Math.max(128, editor.colsWidthCacheSize);
        int pad = Math.max(0, editor.prefetchCols) * 2;
        return Math.max(base, pad);
    }

    // ========================================================================
    // Text Drawing with Visual Spaces and Fade Effects
    // ========================================================================

    /**
     * Draw text segment with fade effect
     */
    public float drawTextSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha) {
        if (start >= end) return 0f;
        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (hasFade && containsArabicScript(line, start, end)) {
            int spaceScale = editor.getVisualSpaceScale();
            if (spaceScale > 1 || line.indexOf('\t', start) >= 0) {
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }
            canvas.drawText(line, start, end, x, y, segmentPaint);
            return segmentPaint.measureText(line, start, end);
        }
        final int spaceScale = editor.getVisualSpaceScale();
        if (spaceScale > 1) {
            if (!hasFade || end <= fadeStart || start >= fadeEnd) {
                return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
            }

            float currentX = x;

            int beforeEnd = Math.min(end, fadeStart);
            if (start < beforeEnd) {
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
            }

            int fadeSegStart = Math.max(start, fadeStart);
            int fadeSegEnd = Math.min(end, fadeEnd);
            if (fadeSegStart < fadeSegEnd) {
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
            }

            int afterStart = Math.max(start, fadeEnd);
            if (afterStart < end) {
                currentX += drawTextSegmentWithVisualSpaces(
                        canvas, line, afterStart, end, currentX, y, segmentPaint, 1f);
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
            editor.charAnimation.charAnimTmpPaint.set(segmentPaint);
            int baseAlpha = segmentPaint.getAlpha();
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            canvas.drawText(line, fadeSegStart, fadeSegEnd, currentX, y, editor.charAnimation.charAnimTmpPaint);
            currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
            currentX += segmentPaint.measureText(line, afterStart, end);
        }

        return currentX - x;
    }

    /**
     * Check if text contains Arabic script
     */
    public boolean containsArabicScript(CharSequence text, int start, int end) {
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

    /**
     * Check if text is mixed direction
     */
    public boolean isMixedDirectionText(CharSequence text, int start, int end) {
        if (text == null || start >= end) return false;
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(text.length(), end);
        boolean hasRtl = false;
        boolean hasLtr = false;
        for (int i = safeStart; i < safeEnd; ) {
            int codePoint = Character.codePointAt(text, i);
            i += Character.charCount(codePoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == null) continue;
            if (isRtlScriptBlock(block)) {
                hasRtl = true;
            } else if (isLatinScriptBlock(block)) {
                hasLtr = true;
            }
            if (hasRtl && hasLtr) return true;
        }
        return false;
    }

    /**
     * Check if block is RTL script
     */
    public boolean isRtlScriptBlock(Character.UnicodeBlock block) {
        return block == Character.UnicodeBlock.ARABIC
                || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS
                || block == Character.UnicodeBlock.HEBREW;
    }

    /**
     * Check if block is Latin script
     */
    public boolean isLatinScriptBlock(Character.UnicodeBlock block) {
        return block == Character.UnicodeBlock.BASIC_LATIN
                || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.LATIN_EXTENDED_B
                || block == Character.UnicodeBlock.LATIN_EXTENDED_C
                || block == Character.UnicodeBlock.LATIN_EXTENDED_D
                || block == Character.UnicodeBlock.LATIN_EXTENDED_E
                || block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL;
    }

    /**
     * Draw text segment with fade and underlines
     */
    public float drawTextSegmentWithFadeAndUnderlines(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha,
            @Nullable List<UnderlineSpan> underlines, float lineTop, float lineBottom) {
        if (start >= end) return 0f;
        boolean anyUnderliningActive = editor.urlUnderline.isUrlUnderliningActive()
                || editor.pathUnderline.isPathUnderliningActive();
        if (underlines == null || underlines.isEmpty() || !anyUnderliningActive) {
            return drawTextSegmentWithFade(
                    canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        float currentX = x;
        int pos = start;

        for (UnderlineSpan span : underlines) {
            if (span.end <= pos) continue;
            if (span.start >= end) break;

            int plainEnd = Math.min(end, Math.max(pos, span.start));
            if (pos < plainEnd) {
                currentX += drawTextSegmentWithFade(
                        canvas, line, pos, plainEnd, currentX, y, segmentPaint,
                        fadeStart, fadeEnd, fadeAlpha);
                pos = plainEnd;
            }

            int underlineStart = Math.max(pos, span.start);
            int underlineEnd = Math.min(end, span.end);
            if (underlineStart < underlineEnd) {
                float underlineXStart = currentX;
                currentX += drawTextSegmentWithFade(
                        canvas, line, underlineStart, underlineEnd, currentX, y, segmentPaint,
                        fadeStart, fadeEnd, fadeAlpha);
                drawUnderlineSegmentWithFade(
                        canvas, line, underlineStart, underlineEnd, underlineXStart, y,
                        lineTop, lineBottom, segmentPaint, fadeStart, fadeEnd, fadeAlpha, span.isPath);
                pos = underlineEnd;
            }
        }

        if (pos < end) {
            currentX += drawTextSegmentWithFade(
                    canvas, line, pos, end, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        }

        return currentX - x;
    }

    /**
     * Draw underline segment with fade
     */
    public void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float baselineY,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha, boolean isPath) {
        if (start >= end) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float underlineY = baselineY + (fm.descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
        thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

        Paint tmpPaintToUse = isPath ? editor.pathUnderline.getPathUnderlinePaint() : editor.urlUnderline.urlUnderlineTmpPaint;
        tmpPaintToUse.set(textPaint);
        tmpPaintToUse.setStyle(Paint.Style.STROKE);
        tmpPaintToUse.setStrokeWidth(thickness);
        tmpPaintToUse.setUnderlineText(false);

        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            float w = editor.measureTextWithVisualSpaces(line, start, end, textPaint);
            if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
            return;
        }

        float currentX = x;
        int baseAlpha = textPaint.getAlpha();

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = editor.measureTextWithVisualSpaces(line, start, beforeEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            float w = editor.measureTextWithVisualSpaces(line, fadeSegStart, fadeSegEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
            currentX += w;
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            tmpPaintToUse.setAlpha(baseAlpha);
            float w = editor.measureTextWithVisualSpaces(line, afterStart, end, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
        }
    }

    // ========================================================================
    // Error Underline Methods
    // ========================================================================

    /**
     * Draw error underlines for a line
     */
    public void drawErrorUnderlinesForLine(
            Canvas canvas, String line, int globalLine, float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        for (ErrorUnderlineSpan span : snapshot) {
            int start = Math.max(0, Math.min(span.start, len));
            int end = Math.max(start, Math.min(span.end, len));
            if (start >= end) continue;
            float xStart = editor.measureText(line, start, globalLine);
            float xEnd = editor.measureText(line, end, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error underlines for a line range
     */
    public void drawErrorUnderlinesForLineRange(
            Canvas canvas, String line, int globalLine, int start, int end,
            float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (start >= end) return;
        for (ErrorUnderlineSpan span : snapshot) {
            int s = Math.max(start, Math.max(0, Math.min(span.start, len)));
            int e = Math.min(end, Math.max(s, Math.min(span.end, len)));
            if (s >= e) continue;
            float xStart = editor.measureText(line, s, globalLine);
            float xEnd = editor.measureText(line, e, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error underlines for a segment
     */
    public void drawErrorUnderlinesForSegment(
            Canvas canvas, String line, int globalLine, int start, int end, float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (start >= end) return;
        for (ErrorUnderlineSpan span : snapshot) {
            int s = Math.max(start, Math.max(0, Math.min(span.start, len)));
            int e = Math.min(end, Math.max(s, Math.min(span.end, len)));
            if (s >= e) continue;
            float xStart = editor.measureTextWithVisualSpaces(line, s, e, paint);
            float xEnd = editor.measureTextWithVisualSpaces(line, s, e, paint);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error squiggle
     */
    public void drawErrorSquiggle(Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
        if (xEnd <= xStart) return;

        errorUnderlinePaint.setColor(errorUnderlineColor);
        errorUnderlinePaint.setStyle(Paint.Style.STROKE);
        errorUnderlinePaint.setStrokeWidth(Math.max(1f, paint.getTextSize() * errorUnderlineStrokeScale));
        errorUnderlinePaint.setAntiAlias(true);

        float underlineY = baselineY + (paint.getFontMetrics().descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float waveHeight = (lineBottom - lineTop) * errorUnderlineHeightScale;
        float wavelength = (xEnd - xStart) * errorUnderlineWaveLengthScale;
        int waves = Math.max(1, (int) ((xEnd - xStart) / wavelength));

        errorUnderlinePath.reset();
        errorUnderlinePath.moveTo(xStart, underlineY);

        for (int i = 0; i <= waves; i++) {
            float x = xStart + (i * wavelength);
            float y = underlineY + ((i % 2 == 0) ? waveHeight : -waveHeight);
            errorUnderlinePath.quadTo(x - (wavelength / 2), underlineY, x, y);
        }

        canvas.drawPath(errorUnderlinePath, errorUnderlinePaint);
    }

    // ========================================================================
    // Helper Methods (delegated to SodiumEditor)
    // ========================================================================

    /**
     * Draw text segment with visual spaces
     */
    
public float drawTextSegmentWithVisualSpaces(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      float alphaMultiplier) {
    if (start >= end) return 0f;

    Paint drawPaint = segmentPaint;
    if (alphaMultiplier < 1f) {
      editor.charAnimation.charAnimTmpPaint.set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
      drawPaint = editor.charAnimation.charAnimTmpPaint;
    }

    int len = end - start;
    if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
      measureWidthBuffer = new float[len];
    }
    segmentPaint.getTextWidths(line, start, end, measureWidthBuffer);

    float currentX = x;
    int runStart = start;
    float runX = currentX;

    for (int i = 0; i < len; i++) {
      int charIndex = start + i;
      char c = line.charAt(charIndex);
      float adv = editor.getCharAdvanceWidth(c, measureWidthBuffer[i], segmentPaint);
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
    /**
     * Get URL underline spans for a line
     */
    

    // ========================================================================
    // Line Number Cache Methods
    // ========================================================================

    /**
     * Check if line number cache should be used
     */
    public boolean shouldUselineNumberCache() {
        return editor.lineNumber.showLineNumbers
                && editor.lineNumber.lineNumbersGutterWidth > 0f
                && editor.getHeight() > 0;
    }

    /**
     * Ensure line number cache bitmap exists
     */
    public void ensurelineNumberCacheBitmap(int width, int height) {
        if (editor.lineNumber.lineNumberCacheBitmap != null
                && editor.lineNumber.lineNumberCacheWidth == width
                && editor.lineNumber.lineNumberCacheHeight == height) {
            return;
        }
        editor.lineNumber.lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        editor.lineNumber.lineNumberCacheCanvas = new Canvas(editor.lineNumber.lineNumberCacheBitmap);
        editor.lineNumber.lineNumberCacheWidth = width;
        editor.lineNumber.lineNumberCacheHeight = height;
    }

    /**
     * Write integer to chars buffer
     */
    public int writeIntToChars(int value, char[] chars) {
        if (chars == null || chars.length == 0) return 0;
        if (value == 0) {
            chars[chars.length - 1] = '0';
            return chars.length - 1;
        }

        int negative = value < 0 ? 1 : 0;
        value = Math.abs(value);

        int len = 0;
        int temp = value;
        while (temp > 0) {
            len++;
            temp /= 10;
        }
        len += negative;

        int start = chars.length - len;
        int idx = chars.length - 1;

        while (value > 0) {
            chars[idx--] = (char) ('0' + (value % 10));
            value /= 10;
        }

        if (negative > 0) {
            chars[idx] = '-';
        }

        return start;
    }

    /**
     * Draw line numbers cached (unwrapped)
     */
    public void drawlineNumbersCachedUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        if (!shouldUselineNumberCache()) {
            drawlineNumbersDirectUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
            return;
        }

        int drawLastIndex = lastVisibleIndex;
        int drawLastLine = lastVisibleLine;
        if (editor.codeFold.isCodeFoldingEnabled) {
            int visibleCount = editor.codeFold.getVisibleLineCount();
            if (visibleCount > 0) {
                drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
            }
        } else {
            int total = editor.getLinesCount();
            if (total > 0) {
                drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
            }
        }

        int gutterWidth = Math.max(1, Math.round(editor.lineNumber.lineNumbersGutterWidth));
        float padPx = lineHeight;
        int height = editor.getHeight() + Math.round(padPx * 2f);
        float baseScrollY = (float) Math.floor(editor.scroll.scrollY / lineHeight) * lineHeight - padPx;

        boolean needsRebuild = editor.lineNumber.lineNumberCacheBitmap == null
                || editor.lineNumber.lineNumberCacheWidth != gutterWidth
                || editor.lineNumber.lineNumberCacheHeight != height
                || editor.lineNumber.lineNumberCacheFirstIndex != firstVisibleIndex
                || editor.lineNumber.lineNumberCacheLastIndex != drawLastIndex
                || Math.abs(editor.lineNumber.lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
                || editor.lineNumber.lineNumberCacheTextSize != editor.lineNumber.lineNumbersPaint.getTextSize()
                || editor.lineNumber.lineNumberCacheTypeface != editor.lineNumber.lineNumbersPaint.getTypeface()
                || editor.lineNumber.lineNumberCacheRtl != isRtl
                || editor.lineNumber.lineNumberCacheWrapped
                || editor.lineNumber.lineNumberCacheCodeFolding != editor.codeFold.isCodeFoldingEnabled
                || Math.abs(editor.lineNumber.lineNumberCacheGutterWidth - editor.lineNumber.lineNumbersGutterWidth) > 0.1f
                || Math.abs(editor.lineNumber.lineNumberCacheFoldMarkerWidth - editor.codeFold.foldMarkerGutterWidth) > 0.1f
                || Math.abs(editor.lineNumber.lineNumberCacheLineHeight - lineHeight) > 0.1f
                || editor.lineNumber.lineNumberCacheColor != editor.lineNumber.lineNumbersPaint.getColor();

        if (needsRebuild) {
            ensurelineNumberCacheBitmap(gutterWidth, height);
            editor.lineNumber.lineNumberCacheBitmap.eraseColor(0);

            float lineNumX = isRtl
                    ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                    + (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                    : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth
                    - (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                    - editor.lineNumber.GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - editor.lineNumber.getGutterStartX();

            if (editor.codeFold.isCodeFoldingEnabled) {
                for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
                    int i = editor.codeFold.mapVisibleIndexToGlobal(v);
                    int start = writeIntToChars(i + 1, editor.lineNumber.lineNumberChars);
                    int count = editor.lineNumber.lineNumberChars.length - start;
                    float y = Math.round(v * lineHeight - baseScrollY + lineHeight - paint.descent());
                    editor.lineNumber.lineNumberCacheCanvas.drawText(
                            editor.lineNumber.lineNumberChars, start, count, lineNumXLocal, y, editor.lineNumber.lineNumbersPaint);
                }
            } else {
                for (int i = firstVisibleLine; i <= drawLastLine; i++) {
                    int start = writeIntToChars(i + 1, editor.lineNumber.lineNumberChars);
                    int count = editor.lineNumber.lineNumberChars.length - start;
                    float y = Math.round(i * lineHeight - baseScrollY + lineHeight - paint.descent());
                    editor.lineNumber.lineNumberCacheCanvas.drawText(
                            editor.lineNumber.lineNumberChars, start, count, lineNumXLocal, y, editor.lineNumber.lineNumbersPaint);
                }
            }

            editor.lineNumber.lineNumberCacheFirstIndex = firstVisibleIndex;
            editor.lineNumber.lineNumberCacheLastIndex = drawLastIndex;
            editor.lineNumber.lineNumberCacheBaseScrollY = baseScrollY;
            editor.lineNumber.lineNumberCacheTextSize = editor.lineNumber.lineNumbersPaint.getTextSize();
            editor.lineNumber.lineNumberCacheTypeface = editor.lineNumber.lineNumbersPaint.getTypeface();
            editor.lineNumber.lineNumberCacheRtl = isRtl;
            editor.lineNumber.lineNumberCacheWrapped = false;
            editor.lineNumber.lineNumberCacheCodeFolding = editor.codeFold.isCodeFoldingEnabled;
            editor.lineNumber.lineNumberCacheGutterWidth = editor.lineNumber.lineNumbersGutterWidth;
            editor.lineNumber.lineNumberCacheFoldMarkerWidth = editor.codeFold.foldMarkerGutterWidth;
            editor.lineNumber.lineNumberCacheLineHeight = lineHeight;
            editor.lineNumber.lineNumberCacheColor = editor.lineNumber.lineNumbersPaint.getColor();
        }

        float offsetY = editor.lineNumber.lineNumberCacheBaseScrollY - editor.scroll.scrollY;
        canvas.drawBitmap(editor.lineNumber.lineNumberCacheBitmap, editor.lineNumber.getGutterStartX(), offsetY, null);
        drawCurrentlineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    /**
     * Draw line numbers cached (wrapped)
     */
    public void drawlineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!shouldUselineNumberCache()) {
            drawlineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
            return;
        }

        int drawLastIndex = lastVisualIndex;
        int totalVisual = editor.getTotalVisualLineCount();
        if (totalVisual > 0) {
            drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
        }

        int gutterWidth = Math.max(1, Math.round(editor.lineNumber.lineNumbersGutterWidth));
        float padPx = lineHeight;
        int height = editor.getHeight() + Math.round(padPx * 2f);
        float baseScrollY = (float) Math.floor(editor.scroll.scrollY / lineHeight) * lineHeight - padPx;

        boolean needsRebuild = editor.lineNumber.lineNumberCacheBitmap == null
                || editor.lineNumber.lineNumberCacheWidth != gutterWidth
                || editor.lineNumber.lineNumberCacheHeight != height
                || editor.lineNumber.lineNumberCacheFirstIndex != firstVisualIndex
                || editor.lineNumber.lineNumberCacheLastIndex != drawLastIndex
                || Math.abs(editor.lineNumber.lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
                || editor.lineNumber.lineNumberCacheTextSize != editor.lineNumber.lineNumbersPaint.getTextSize()
                || editor.lineNumber.lineNumberCacheTypeface != editor.lineNumber.lineNumbersPaint.getTypeface()
                || editor.lineNumber.lineNumberCacheRtl != isRtl
                || !editor.lineNumber.lineNumberCacheWrapped
                || editor.lineNumber.lineNumberCacheCodeFolding != editor.codeFold.isCodeFoldingEnabled
                || Math.abs(editor.lineNumber.lineNumberCacheGutterWidth - editor.lineNumber.lineNumbersGutterWidth) > 0.1f
                || Math.abs(editor.lineNumber.lineNumberCacheLineHeight - lineHeight) > 0.1f
                || editor.lineNumber.lineNumberCacheColor != editor.lineNumber.lineNumbersPaint.getColor();

        if (needsRebuild) {
            ensurelineNumberCacheBitmap(gutterWidth, height);
            editor.lineNumber.lineNumberCacheBitmap.eraseColor(0);

            float lineNumX = isRtl
                    ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                    : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - editor.lineNumber.getGutterStartX();

            for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
                WordWrap.VisualLinePosition pos = editor.getVisualPositionForIndex(v);
                if (pos.segment != 0) continue;
                int start = writeIntToChars(pos.line + 1, editor.lineNumber.lineNumberChars);
                int count = editor.lineNumber.lineNumberChars.length - start;
                float y = Math.round(v * lineHeight - baseScrollY + lineHeight - paint.descent());
                editor.lineNumber.lineNumberCacheCanvas.drawText(
                        editor.lineNumber.lineNumberChars, start, count, lineNumXLocal, y, editor.lineNumber.lineNumbersPaint);
            }

            editor.lineNumber.lineNumberCacheFirstIndex = firstVisualIndex;
            editor.lineNumber.lineNumberCacheLastIndex = drawLastIndex;
            editor.lineNumber.lineNumberCacheBaseScrollY = baseScrollY;
            editor.lineNumber.lineNumberCacheTextSize = editor.lineNumber.lineNumbersPaint.getTextSize();
            editor.lineNumber.lineNumberCacheTypeface = editor.lineNumber.lineNumbersPaint.getTypeface();
            editor.lineNumber.lineNumberCacheRtl = isRtl;
            editor.lineNumber.lineNumberCacheWrapped = true;
            editor.lineNumber.lineNumberCacheCodeFolding = editor.codeFold.isCodeFoldingEnabled;
            editor.lineNumber.lineNumberCacheGutterWidth = editor.lineNumber.lineNumbersGutterWidth;
            editor.lineNumber.lineNumberCacheFoldMarkerWidth = editor.codeFold.foldMarkerGutterWidth;
            editor.lineNumber.lineNumberCacheLineHeight = lineHeight;
            editor.lineNumber.lineNumberCacheColor = editor.lineNumber.lineNumbersPaint.getColor();
        }

        float offsetY = editor.lineNumber.lineNumberCacheBaseScrollY - editor.scroll.scrollY;
        canvas.drawBitmap(editor.lineNumber.lineNumberCacheBitmap, editor.lineNumber.getGutterStartX(), offsetY, null);
        drawCurrentlineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw line numbers direct (unwrapped)
     */
    public void drawlineNumbersDirectUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        int drawLastIndex = lastVisibleIndex;
        int drawLastLine = lastVisibleLine;
        if (editor.codeFold.isCodeFoldingEnabled) {
            int visibleCount = editor.codeFold.getVisibleLineCount();
            if (visibleCount > 0) drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
        } else {
            int total = editor.getLinesCount();
            if (total > 0) drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
        }

        float lineNumX = isRtl
                ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                + (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth
                - (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                - editor.lineNumber.GUTTER_TEXT_PADDING;

        if (editor.codeFold.isCodeFoldingEnabled) {
            for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
                int i = editor.codeFold.mapVisibleIndexToGlobal(v);
                int start = writeIntToChars(i + 1, editor.lineNumber.lineNumberChars);
                int count = editor.lineNumber.lineNumberChars.length - start;
                float y = Math.round(v * lineHeight - editor.scroll.scrollY + lineHeight - paint.descent());
                if (i == editor.cursor.cursorLine) {
                    int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
                    editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
                    canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
                    editor.lineNumber.lineNumbersPaint.setColor(originalColor);
                } else {
                    canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
                }
            }
        } else {
            for (int i = firstVisibleLine; i <= drawLastLine; i++) {
                int start = writeIntToChars(i + 1, editor.lineNumber.lineNumberChars);
                int count = editor.lineNumber.lineNumberChars.length - start;
                float y = Math.round(i * lineHeight - editor.scroll.scrollY + lineHeight - paint.descent());
                if (i == editor.cursor.cursorLine) {
                    int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
                    editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
                    canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
                    editor.lineNumber.lineNumbersPaint.setColor(originalColor);
                } else {
                    canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
                }
            }
        }
    }

    /**
     * Draw line numbers direct (wrapped)
     */
    public void drawlineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        float lineNumX = isRtl
                ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.GUTTER_TEXT_PADDING;

        int drawLastIndex = lastVisualIndex;
        int totalVisual = editor.getTotalVisualLineCount();
        if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

        for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
            WordWrap.VisualLinePosition pos = editor.getVisualPositionForIndex(v);
            if (pos.segment != 0) continue;
            int start = writeIntToChars(pos.line + 1, editor.lineNumber.lineNumberChars);
            int count = editor.lineNumber.lineNumberChars.length - start;
            float y = Math.round(v * lineHeight - editor.scroll.scrollY + lineHeight - paint.descent());
            if (pos.line == editor.cursor.cursorLine) {
                int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
                editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
                canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
                editor.lineNumber.lineNumbersPaint.setColor(originalColor);
            } else {
                canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
            }
        }
    }

    /**
     * Draw current line number (unwrapped)
     */
    public void drawCurrentlineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!editor.lineNumber.showLineNumbers) return;
        if (editor.codeFold.isCodeFoldingEnabled && editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine)) return;

        int visibleIndex = editor.codeFold.isCodeFoldingEnabled
                ? editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine)
                : editor.cursor.cursorLine;
        if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

        float lineNumX = isRtl
                ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                + (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth
                - (editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.foldMarkerGutterWidth : 0f)
                - editor.lineNumber.GUTTER_TEXT_PADDING;
        int start = writeIntToChars(editor.cursor.cursorLine + 1, editor.lineNumber.lineNumberChars);
        int count = editor.lineNumber.lineNumberChars.length - start;
        float y = Math.round(visibleIndex * lineHeight - editor.scroll.scrollY + lineHeight - paint.descent());
        int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
        editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
        canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
        editor.lineNumber.lineNumbersPaint.setColor(originalColor);
    }

    /**
     * Draw current line number (wrapped)
     */
    public void drawCurrentlineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!editor.lineNumber.showLineNumbers) return;
        int visualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
        if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

        float lineNumX = isRtl
                ? editor.lineNumber.getGutterStartX() + editor.lineNumber.GUTTER_TEXT_PADDING
                : editor.lineNumber.getGutterStartX() + editor.lineNumber.lineNumbersGutterWidth - editor.lineNumber.GUTTER_TEXT_PADDING;
        int start = writeIntToChars(editor.cursor.cursorLine + 1, editor.lineNumber.lineNumberChars);
        int count = editor.lineNumber.lineNumberChars.length - start;
        float y = Math.round(visualIndex * lineHeight - editor.scroll.scrollY + lineHeight - paint.descent());
        int originalColor = editor.lineNumber.lineNumbersPaint.getColor();
        editor.lineNumber.lineNumbersPaint.setColor(editor.lineNumber.currentLineNumberColor);
        canvas.drawText(editor.lineNumber.lineNumberChars, start, count, lineNumX, y, editor.lineNumber.lineNumbersPaint);
        editor.lineNumber.lineNumbersPaint.setColor(originalColor);
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

        final List<UnderlineSpan> urlUnderlines = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);

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
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, start, end, 0f, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
            return;
        }

        List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
        if (spans == null) {
            spans = editor.calculateSpansForLine(line, globalLine);
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
                    currentX += drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, lastEnd, segStart, currentX, y, paint,
                            fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
                }

                if (segEnd > segStart) {
                    currentX += drawTextSegmentWithFadeAndUnderlines(
                            canvas, line, segStart, segEnd, currentX, y, span.paint,
                            fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
                }
                lastEnd = Math.max(lastEnd, segEnd);
            }
        }

        if (lastEnd < end) {
            drawTextSegmentWithFadeAndUnderlines(
                    canvas, line, lastEnd, end, currentX, y, paint,
                    fadeStart, fadeEnd, fadeAlpha, urlUnderlines, lineTop, lineBottom);
        }
    }

    /**
     * Draw delete animation for segment
     */
    public void drawDeleteAnimationForSegment(Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        if (!editor.charAnimation.isCharAnimationEnabled) return;
        if (globalLine != editor.charAnimation.delAnimLine
                || editor.charAnimation.delAnimText == null
                || editor.charAnimation.delAnimText.isEmpty()
                || editor.charAnimation.delAnimAlpha <= 0f) return;
        if (line == null) line = "";
        int at = Math.max(0, Math.min(editor.charAnimation.delAnimAtChar, line.length()));
        if (at < segStart || at > segEnd) return;
        float x = editor.measureTextWithVisualSpaces(line, segStart, at, paint);
        Paint ghostPaint = (editor.charAnimation.delAnimPaint != null) ? editor.charAnimation.delAnimPaint : paint;
        editor.charAnimation.charAnimTmpPaint.set(ghostPaint);
        editor.charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, editor.charAnimation.delAnimAlpha))));
        canvas.drawText(editor.charAnimation.delAnimText, x, y, editor.charAnimation.charAnimTmpPaint);
    }

    /**
     * Draw whitespace guides for segment
     */
    public void drawWhitespaceGuidesForSegment(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        editor.whitespaceGuides.drawWhitespaceGuidesForSegment(canvas, line, globalLine, start, end, y);
    }

    /**
     * Draw auto suggestion (wrapped)
     */
    public void drawAutoSuggestionWrapped(
            Canvas canvas, String lineContent, int globalLine, int segStart, int segEnd,
            int visualIndex, float textBaselineY) {
        boolean allowSuggestion = editor.activeSuggestionIsPath
                ? editor.isAutoPathCompletionEnabled
                : editor.isAutoCompletionEnabled;
        if (!allowSuggestion || editor.activeSuggestion == null || globalLine != editor.activeSuggestionLine) {
            return;
        }

        int cursorPositionInLine = editor.activeSuggestionCharStart + editor.activeSuggestionWordFragment.length();
        if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

        float suggestionStartX_canvas = editor.measureTextWithVisualSpaces(lineContent, segStart, cursorPositionInLine, paint);
        canvas.drawText(editor.activeSuggestion, suggestionStartX_canvas, textBaselineY, editor.suggestionPaint);

        float suggestionTextWidth = editor.suggestionPaint.measureText(editor.activeSuggestion);

        float left_view = suggestionStartX_canvas + editor.getTextStartX() - editor.getEffectiveScrollX();
        float right_view = left_view + suggestionTextWidth;
        if (isRtl) {
            float baseX = editor.getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);
            left_view += baseX;
            right_view += baseX;
        }
        float top_view = visualIndex * lineHeight - editor.scroll.scrollY;
        float bottom_view = top_view + lineHeight;

        editor.activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
    }

    /**
     * Draw whitespace guides for line
     */
    public void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
        editor.whitespaceGuides.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
    }

    /**
     * Get draw line top position
     */
    public float getDrawLineTop(int globalLine) {
        int drawIndex = globalLine;
        if (editor.codeFold.isCodeFoldingEnabled) {
            drawIndex = editor.codeFold.getVisibleIndexForGlobalLine(globalLine);
        }
        return (drawIndex - editor.drawBaseLine) * lineHeight;
    }

    /**
     * Get draw line bottom position
     */
    public float getDrawLineBottom(int globalLine) {
        return getDrawLineTop(globalLine) + lineHeight;
    }

    /**
     * Get hit test base Y
     */
    public float getHitTestBaseY() {
        int baseLine = (int) (editor.scroll.scrollY / lineHeight);
        if (baseLine < 0) baseLine = 0;
        return baseLine * lineHeight;
    }
}
