package com.yn.sodiumeditor.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.core.WordWrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * TextRender handles all text rendering algorithms for SodiumEditor.
 * This includes drawing text, handling syntax highlighting, underlines,
 * whitespace guides, and other visual text rendering operations.
 */
public class TextRender {

    // Reference to the parent SodiumEditor
    private final SodiumEditor editor;
    private static final ThreadLocal<ArrayList<UnderlineSpan>> TL_UNDERLINES =
        ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Paint.FontMetrics> TL_FONT_METRICS =
        ThreadLocal.withInitial(Paint.FontMetrics::new);
    private final RectF binaryTokenRect = new RectF();
public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 250; // 2000 yyy
  public int prefetchLines = 150; // 1000 yyy
    public final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
  public final LinkedHashMap<Integer, Float> lineWidthCache;
  public int lineWidthCacheSize = 400; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  
  public int maxSyntaxLineLength = 4096;
  public int prefetchCols = 100;
  public int colsWidthCacheSize = 400;
  public final LinkedHashMap<Integer, Float> avgCharWidthCache =
      new LinkedHashMap<Integer, Float>(colsWidthCacheSize, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
          return size() > colsWidthCacheSize;
        }
      };
  public final Object streamedLinesLock = new Object();
  public final SparseIntArray streamedLineLengths = new SparseIntArray();
  public final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
  public boolean streamedSliceUpdatePending = false;
  public int streamedSliceUpdateToken = 0;
  public final int[] streamedSliceTmp = new int[2];
  // Additional locks and arrays for streamed lines operations
  public final Object streamedLinesLockLinesLock = new Object();
  public final SparseIntArray streamedLinesLockLineLengths = new SparseIntArray();
  public final SparseIntArray streamedLinesLockLineSliceStarts = new SparseIntArray();
  public boolean streamedLinesLockSliceUpdatePending = false;
  public int streamedLinesLockSliceUpdateToken = 0;
  
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
    



    // Visible character range
    public final int[] visibleCharRangeTmp = new int[2];
    public int visibleCharPadding = 2;
    public boolean isPerformanceModeEnabled = true;
    public boolean isStableGlyphPositionsEnabled = true;


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
        this.lineWidthCache = new LinkedHashMap<Integer, Float>(lineWidthCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
                return size() > lineWidthCacheSize;
            }
        };
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
            spans = editor.highlite.calculateSpansForLine(lineText, lineIndex);
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
            synchronized (editor.textRender.avgCharWidthCache) {
                Float cached = editor.textRender.avgCharWidthCache.get(lineIndex);
                if (cached != null) return cached;
            }
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? paint.measureText(line, 0, sampleLen) : paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            synchronized (editor.textRender.avgCharWidthCache) {
                if (isStableGlyphPositionsEnabled && editor.textRender.avgCharWidthCache.containsKey(lineIndex)) {
                    return editor.textRender.avgCharWidthCache.get(lineIndex);
                }
                editor.textRender.avgCharWidthCache.put(lineIndex, avg);
            }
        }
        return avg;
    }

    /**
     * Draw a highlighted line with syntax highlighting, underlines, and animations
     */
    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        getVisibleCharRangeForLine(line, globalLine, visibleCharRangeTmp);
        int visibleStart = visibleCharRangeTmp[0];
        int visibleEnd   = visibleCharRangeTmp[1];
        if (visibleEnd > visibleStart) {
            int sliceStart = editor.getStreamedLineSliceStart(globalLine);
            int sliceEnd   = sliceStart + line.length();
            int drawStart  = Math.max(visibleStart, sliceStart);
            int drawEnd    = Math.min(visibleEnd,   sliceEnd);
            if (drawEnd > drawStart) {
                int relStart = Math.max(0, drawStart - sliceStart);
                int relEnd   = Math.max(relStart, Math.min(line.length(), drawEnd - sliceStart));
                int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
                int[] tokenSpan = new int[2];
                if (spans != null && editor.binaryRender.findBinaryTokenSpanInSpans(spans, relStart, tokenSpan)) {
                    relStart = Math.min(relStart, tokenSpan[0]);
                }
                if (spans != null && editor.binaryRender.findBinaryTokenSpanInSpans(spans, Math.max(relStart, relEnd - 1), tokenSpan)) {
                    relEnd = Math.max(relEnd, tokenSpan[1]);
                    relEnd = Math.min(relEnd, line.length());
                }

                float x = (relStart > 0) ? paint.measureText(line, 0, relStart) : 0f;
                if (spans == null || spans.length == 0) {
                    canvas.drawText(line, relStart, relEnd, x, y, paint);
                    return;
                }

                Paint.FontMetrics fm = TL_FONT_METRICS.get();
                paint.getFontMetrics(fm);
                float boxTop = y + fm.ascent - editor.binaryRender.binaryTokenPaddingY;
                float boxBottom = y + fm.descent + editor.binaryRender.binaryTokenPaddingY;

                int idx = relStart;
                for (int sIdx = 0; sIdx + 1 < spans.length; sIdx += 2) {
                    int s = spans[sIdx];
                    int e = spans[sIdx + 1];
                    if (e <= relStart) continue;
                    if (s >= relEnd) break;
                    if (s > idx) {
                        canvas.drawText(line, idx, s, x, y, paint);
                        x += paint.measureText(line, idx, s);
                    }
                    float tokenWidth = paint.measureText(line, s, e);
                    float padX = editor.binaryRender.binaryTokenPaddingX;
                    if (editor.binaryRender.binaryTokenBoxEnabled) {
                        float left = x;
                        float right = x + tokenWidth + (padX * 2f);
                        float radius = editor.binaryRender.binaryTokenCornerRadius;
                        binaryTokenRect.set(left, boxTop, right, boxBottom);
                        if (radius > 0f) {
                            canvas.drawRoundRect(binaryTokenRect, radius, radius, editor.binaryRender.getBinaryTokenFillPaint());
                            canvas.drawRoundRect(binaryTokenRect, radius, radius, editor.binaryRender.getBinaryTokenStrokePaint());
                        } else {
                            canvas.drawRect(binaryTokenRect, editor.binaryRender.getBinaryTokenFillPaint());
                            canvas.drawRect(binaryTokenRect, editor.binaryRender.getBinaryTokenStrokePaint());
                        }
                    }
                    float textX = x + padX;
                    canvas.drawText(line, s, e, textX, y, paint);
                    x += tokenWidth + (padX * 2f);
                    idx = e;
                }
                if (idx < relEnd) {
                    canvas.drawText(line, idx, relEnd, x, y, paint);
                }
            }
        }
        return;
    }
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
        if (len > editor.textRender.maxSyntaxLineLength) {
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
        ArrayList<UnderlineSpan> combinedUnderlines = TL_UNDERLINES.get();
        combinedUnderlines.clear();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
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
        ArrayList<UnderlineSpan> combinedUnderlines = TL_UNDERLINES.get();
        combinedUnderlines.clear();
        if (editor.urlUnderline.isUrlUnderliningActive()) {
            List<UnderlineSpan> urlSpans = editor.urlUnderline.getUrlUnderlineSpansForLine(line, globalLine);
            if (urlSpans != null) combinedUnderlines.addAll(urlSpans);
        }
        if (editor.pathUnderline.isPathUnderliningActive()) {
            List<UnderlineSpan> pathSpans = editor.pathUnderline.getPathUnderlineSpansForLine(line, globalLine);
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
                spans = editor.highlite.calculateSpansForLine(line, globalLine);
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
        editor.errorUnderline.drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
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
    if (len <= 0) { out[0] = 0; out[1] = 0; return; }

    if (len > editor.textRender.maxSyntaxLineLength) {
        getVisibleCharRangeForLineFast(line, globalLine, len, out);
        return;
    }

    // CRITICAL FIX: binary mode must NEVER skip culling.
    // isStableGlyphPositionsEnabled was designed for normal text glyph stability,
    // but binary tokens are pure ASCII — no shaping, no reordering needed.
    // Skipping culling here means a 1000-byte line = 5000-char string drawn in full every frame.
    boolean skipCull = isStableGlyphPositionsEnabled
                       && !editor.binaryRender.isBinarySafeRenderingEnabled();
    if (skipCull) {
        out[0] = 0;
        out[1] = len;
        return;
    }

    float viewLeft  = isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
    float viewRight = isRtl
        ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth)
        : editor.getWidth();
    float leftX  = viewLeft  + editor.getEffectiveScrollX() - editor.getTextStartX();
    float rightX = viewRight + editor.getEffectiveScrollX() - editor.getTextStartX();

    int start, end;

    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        // Binary fast path: all tokens are fixed-width ASCII — use avg char width
        float avg = editor.textRender.paint.measureText("X"); // monospace: single char is enough
        if (avg <= 0f) avg = paint.measureText(" ");
        start = (int) Math.floor(leftX  / avg);
        end   = (int) Math.ceil (rightX / avg);
    } else {
        start = editor.getCharIndexForX(line, leftX,  globalLine);
        end   = editor.getCharIndexForX(line, rightX, globalLine);
    }

    if (end < start) { int t = start; start = end; end = t; }
    int pad = visibleCharPadding;
    out[0] = Math.max(0,   start - pad);
    out[1] = Math.min(len, end   + pad);
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
            out[1] = Math.min(len, Math.max(0, editor.textRender.prefetchCols));
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
        int pad = visibleCharPadding + Math.max(0, editor.textRender.prefetchCols);
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        out[0] = start;
        out[1] = end;
        if (globalLine <= 2 && (start > 0 || end < len)) {
            editor.logRender(
                "visibleRangeFast",
                "visibleRangeFast line=" + globalLine
                    + " len=" + len
                    + " start=" + start
                    + " end=" + end
                    + " scrollX=" + editor.scroll.scrollX
                    + " effectiveScrollX=" + editor.getEffectiveScrollX()
                    + " textStartX=" + editor.getTextStartX(),
                200);
        }
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
        int maxExtra = Math.max(0, editor.textRender.colsWidthCacheSize - visibleLen);
        int extraPad = Math.min(Math.max(0, editor.textRender.prefetchCols), maxExtra / 2);
        start = Math.max(0, start - extraPad);
        end = Math.min(len, end + extraPad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Get initial streamed slice size
     */
    public int getInitialStreamedSliceSize() {
        int base = Math.max(128, editor.textRender.colsWidthCacheSize);
        int pad = Math.max(0, editor.textRender.prefetchCols) * 2;
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
if (hasFade
        && !editor.binaryRender.isBinarySafeRenderingEnabled()
        && containsArabicScript(line, start, end)) {
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

        Paint.FontMetrics fm = TL_FONT_METRICS.get();
        textPaint.getFontMetrics(fm);
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
        int totalVisual = editor.wordWrap.getTotalVisualLineCount();
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
                WordWrap.VisualLinePosition pos = editor.wordWrap.getVisualPositionForIndex(v);
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
        int totalVisual = editor.wordWrap.getTotalVisualLineCount();
        if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

        for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
            WordWrap.VisualLinePosition pos = editor.wordWrap.getVisualPositionForIndex(v);
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

  public void setColsWidthCacheSize(int size) {
    int safe = Math.max(16, size);
    if (colsWidthCacheSize == safe) return;
    colsWidthCacheSize = safe;
    synchronized (avgCharWidthCache) {
      if (avgCharWidthCache.size() > colsWidthCacheSize) {
        Iterator<Map.Entry<Integer, Float>> it = avgCharWidthCache.entrySet().iterator();
        while (avgCharWidthCache.size() > colsWidthCacheSize && it.hasNext()) {
          it.next();
          it.remove();
        }
      }
    }
  }


  public void setWindowSize(int size) {
    int safe = Math.max(10, size);
    int minWindow = computeMinWindowSize();
    if (safe < minWindow) safe = minWindow;
    if (windowSize == safe) return;
    windowSize = safe;
    editor.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setPrefetchLines(int lines) {
    int safe = Math.max(0, lines);
    if (prefetchLines == safe) return;
    prefetchLines = safe;
    int minWindow = computeMinWindowSize();
    if (windowSize < minWindow) windowSize = minWindow;
    editor.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setLineWidthCacheSize(int size) {
    int safe = Math.max(10, size);
    if (lineWidthCacheSize == safe) return;
    lineWidthCacheSize = safe;
    synchronized (lineWidthCache) {
      if (lineWidthCache.size() > lineWidthCacheSize) {
        Iterator<Map.Entry<Integer, Float>> it = lineWidthCache.entrySet().iterator();
        while (lineWidthCache.size() > lineWidthCacheSize && it.hasNext()) {
          it.next();
          it.remove();
        }
      }
    }
  }

  public void setRenderWindow(int windowSize, int prefetchLines) {
    int safeWindow = Math.max(10, windowSize);
    int safePrefetch = Math.max(0, prefetchLines);
    int minWindow = computeMinWindowSizeForPrefetch(safePrefetch);
    if (safeWindow < minWindow) safeWindow = minWindow;
    if (this.windowSize == safeWindow && this.prefetchLines == safePrefetch) return;
    this.windowSize = safeWindow;
    this.prefetchLines = safePrefetch;
    editor.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public int computeMinWindowSize() {
    return computeMinWindowSizeForPrefetch(prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    if (editor.textRender.lineHeight <= 0f || editor.getHeight() <= 0) return 10;
    float effectiveHeight = editor.getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(effectiveHeight / editor.textRender.lineHeight) + 2);
    int minTotal = Math.max(visibleLines * 2, visibleLines + 6);
    int minWindow = minTotal - (Math.max(0, prefetch) * 2);
    return Math.max(10, minWindow);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    if (editor.getWidth() == 0 || editor.getHeight() == 0) {
      editor.invalidate();
      return;
    }
    int firstVisibleLine = Math.max(0, editor.getGlobalLineForY(editor.scroll.scrollY));
    int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
    editor.fileIO.loadWindowAround(targetStart, null, recalcWidthSync);
  }

  // ========================================================================
  // Background Methods
  // ========================================================================

  /**
   * Set editor background color
   */
  public void setEditorBackgroundColor(int color) {
    hasEditorBackgroundColor = true;
    editorBackgroundColor = color;
    editor.invalidate();
  }

  /**
   * Clear editor background color
   */
  public void clearEditorBackgroundColor() {
    hasEditorBackgroundColor = false;
    editor.invalidate();
  }

  /**
   * Set editor background bitmap
   */
  public void setEditorBackgroundBitmap(android.graphics.Bitmap bitmap) {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = bitmap;
    editor.invalidate();
  }

  /**
   * Clear editor background image
   */
  public void clearEditorBackgroundImage() {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = null;
    editor.invalidate();
  }

  // ========================================================================
  // Font and Typeface Methods
  // ========================================================================

  /**
   * Apply typeface to editor
   */
  public void applyTypeface(@Nullable android.graphics.Typeface typeface, int style) {
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
      final android.graphics.Typeface tf = typeface;
      final int st = style;
      editor.post(() -> applyTypeface(tf, st));
      return;
    }
    android.graphics.Typeface safeBase = (typeface != null) ? typeface : android.graphics.Typeface.DEFAULT;
    baseTypeface = safeBase;
    int typefaceStyle;
    switch (style) {
      case SodiumEditor.STYLE_BOLD:
        typefaceStyle = android.graphics.Typeface.BOLD;
        break;
      case SodiumEditor.STYLE_ITALIC:
        typefaceStyle = android.graphics.Typeface.ITALIC;
        break;
      case SodiumEditor.STYLE_BOLD_ITALIC:
        typefaceStyle = android.graphics.Typeface.BOLD_ITALIC;
        break;
      default:
        typefaceStyle = android.graphics.Typeface.NORMAL;
        break;
    }
    android.graphics.Typeface finalTypeface = android.graphics.Typeface.create(safeBase, typefaceStyle);
    paint.setTypeface(finalTypeface);
    editor.autoCompletion.suggestionPaint.setTypeface(finalTypeface);
    editor.lineNumber.lineNumbersPaint.setTypeface(finalTypeface);
    editor.codeFold.foldMarkerPaint.setTypeface(finalTypeface);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(finalTypeface);
    if (editor.textRender.whitespaceStringRule != null)
      editor.textRender.whitespaceStringRule.updateTypeface(safeBase);
    if (editor.textRender.whitespaceCommentRule != null)
      editor.textRender.whitespaceCommentRule.updateTypeface(safeBase);
    if (editor.highlite.lineCommentHighlightRule != null)
      editor.highlite.lineCommentHighlightRule.updateTypeface(safeBase);
    for (TextRender.HighlightRule rule : editor.highlite.highlightRules) {
      rule.updateTypeface(safeBase);
    }
    editor.highlite.clearHighlightCaches();

    lineHeight = paint.getFontSpacing();
    editor.updateWhitespaceGuideMetrics();
    editor.lineNumber.invalidateLineNumberCache();
    editor.wordWrap.indicator.wordWrapIndicatorWidth =
        editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            com.yn.sodiumeditor.core.highlite.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);

    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    editor.textRender.currentMaxWindowLineWidth = 0f;
    editor.textRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;
    editor.recalculateMaxLineWidth();

    editor.requestLayout();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    editor.invalidate();
  }

  /**
   * Apply text size to editor
   */
  public void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  /**
   * Apply text size to editor with defer option
   */
  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    paint.setTextSize(sizePx);
    if (!editor.autoCompletion.isSuggestionTextSizeCustom) {
      editor.autoCompletion.suggestionTextSizeScale = 1f;
    }
    editor.autoCompletion.suggestionPaint.setTextSize(sizePx * editor.autoCompletion.suggestionTextSizeScale);
    editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
    editor.codeFold.foldMarkerPaint.setTextSize(sizePx * editor.codeFold.foldMarkerTextScale);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
        sizePx * editor.wordWrap.indicator.wordWrapIndicatorTextScale);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(paint.getTypeface());
    editor.wordWrap.indicator.wordWrapIndicatorWidth =
        editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            com.yn.sodiumeditor.core.highlite.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);
    lineHeight = paint.getFontSpacing();
    editor.updateTextSizeDependentMetrics();
    editor.updateWhitespaceGuideMetrics();
    editor.lineNumber.invalidateLineNumberCache();

    for (TextRender.HighlightRule rule : editor.highlite.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (editor.textRender.whitespaceStringRule != null)
      editor.textRender.whitespaceStringRule.updateTextSize(sizePx);
    if (editor.textRender.whitespaceCommentRule != null)
      editor.textRender.whitespaceCommentRule.updateTextSize(sizePx);
    if (editor.highlite.lineCommentHighlightRule != null)
      editor.highlite.lineCommentHighlightRule.updateTextSize(sizePx);
    editor.highlite.clearHighlightCaches();

    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    float scale = sizePx / oldSize;
    editor.textRender.currentMaxWindowLineWidth *= scale;
    editor.textRender.globalMaxLineWidth *= scale;
    editor.scroll.maxLineWidthForScroll *= scale;
    editor.scroll.maxScrollXForScroll *= scale;
    editor.scroll.maxTextStartXForScroll = 0f;
    if (scale < 1f) {
      editor.scroll.maxLineWidthForScroll = 0f;
      editor.scroll.maxScrollXForScroll = 0f;
    }

    editor.requestLayout();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true, !deferWrapRebuild);
    editor.wordWrap.requestWrapPrefixRebuild();
    editor.invalidate();
  }

  // ========================================================================
  // Line Text Access Methods
  // ========================================================================

  /**
   * Get line text for render with direct lines support
   */
  public String getLineTextForRenderWithDirect(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    if (line < 0) return "";

    // Window first
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = editor.getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }

    // Modified lines (recent edits)
    String mod = modifiedLines.get(line);
    if (mod != null) return mod;

    // Direct batch (during fast fling)
    if (direct != null) {
      String d = direct.get(line);
      if (d != null) return d;
    }

    // Cache
    synchronized (editor.fileIO.directLineCache) {
      String c = editor.fileIO.directLineCache.get(line);
      if (c != null) return c;
    }

    return "";
  }

  /**
   * Get line text for render (render-safe, no file random read)
   */
  public String getLineTextForRender(int line) {
    if (line < 0) return "";
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = editor.getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }
    String mod = modifiedLines.get(line);
    if (mod != null) return mod;
    editor.logRender(
        "line-miss",
        "lineMiss line=" + line
            + " windowStart=" + windowStartLine
            + " windowSize=" + linesWindow.size()
            + " isIndexReady=" + editor.fileIO.isIndexReady
            + " isWindowLoading=" + editor.fileIO.isWindowLoading,
        1000);
    return "";
  }

  // ========================================================================
  // Text Measurement Methods
  // ========================================================================

  /**
   * Get character advance width
   */
  public float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
    if (c == ' ') {
      return measuredWidth;
    }
    if (c == '\t') {
      return getVisualTabWidth(p);
    }
    return measuredWidth;
  }

  /**
   * Get visual tab width
   */
  public float getVisualTabWidth(Paint p) {
    return getVisualSpaceWidth(p) * DEFAULT_TAB_SIZE_SPACES;
  }

  /**
   * Get visual space width
   */
  public float getVisualSpaceWidth(Paint p) {
    return p.measureText(" ");
  }

  /**
   * Measure text with visual spaces
   */
  public float measureTextWithVisualSpaces(String text, int start, int end, Paint p) {
    if (text == null) return 0f;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (start >= end) return 0f;

    if (text.indexOf('\t', start) < 0) {
      return p.measureText(text, start, end);
    }

    int len = end - start;
    if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
      measureWidthBuffer = new float[len];
    }
    p.getTextWidths(text, start, end, measureWidthBuffer);
    float total = 0f;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(start + i);
      total += getCharAdvanceWidth(c, measureWidthBuffer[i], p);
    }
    return total;
  }

  /**
   * Measure text
   */
  public float measureText(String line, int length, int globalLine) {
    int logicalLen = editor.getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));
    if (logicalLen > maxSyntaxLineLength) {
      float avg = getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (editor.highlite.highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return measureTextWithVisualSpaces(line, 0, safeLen, paint);
    }

    List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return measureTextWithVisualSpaces(line, 0, safeLen, paint);
    }

    float totalWidth = 0;
    int lastEnd = 0;

    for (HighlightSpan span : spans) {
      if (lastEnd >= safeLen) break;
      if (span.start >= safeLen) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        int measureEnd = Math.min(span.start, safeLen);
        totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd, paint);
      }

      lastEnd = span.start;

      int measureEnd = Math.min(span.end, safeLen);
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd, span.paint);

      lastEnd = span.end;
    }

    if (lastEnd < safeLen) {
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, safeLen, paint);
    }

    return totalWidth;
  }

  /**
   * Measure highlighted segment width
   */
  public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    if (line == null || line.isEmpty() || start >= end) return 0f;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return 0f;

    if (editor.highlite.highlightRules.isEmpty()) {
      return paint.measureText(line, start, end);
    }

    List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return paint.measureText(line, start, end);
    }

    float total = 0f;
    int lastEnd = start;

    for (HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        total += paint.measureText(line, lastEnd, span.start);
      }

      int safeSpanEnd = Math.min(span.end, end);
      if (safeSpanEnd > span.start) {
        total += span.paint.measureText(line, span.start, safeSpanEnd);
      }
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < end) {
      total += paint.measureText(line, lastEnd, end);
    }

    return total;
  }

  /**
   * Draw highlighted segment
   */
  public void drawHighlightedSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float x, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    if (editor.highlite.highlightRules.isEmpty()) {
      paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, paint);
      return;
    }

    List<HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, paint);
      return;
    }

    float currentX = x;
    int lastEnd = start;

    for (HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        paint.setUnderlineText(false);
        canvas.drawText(line, lastEnd, span.start, currentX, y, paint);
        currentX += paint.measureText(line, lastEnd, span.start);
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
      paint.setUnderlineText(false);
      canvas.drawText(line, lastEnd, end, currentX, y, paint);
    }
  }

  /**
   * Compute width for line
   */
  public void computeWidthForLine(int globalIndex, String line) {
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = editor.getLogicalLineLength(globalIndex, safe);
    if (logicalLen > maxSyntaxLineLength) {
      w = getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(), paint);
    }
    synchronized (lineWidthCache) {
      lineWidthCache.put(globalIndex, w);
    }
  }

  /**
   * Get width for line
   */
  public float getWidthForLine(int globalIndex, String line) {
    synchronized (lineWidthCache) {
      Float v = lineWidthCache.get(globalIndex);
      if (v != null) return v;
    }
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = editor.getLogicalLineLength(globalIndex, safe);
    if (logicalLen > maxSyntaxLineLength) {
      w = getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(), paint);
    }
    synchronized (lineWidthCache) {
      lineWidthCache.put(globalIndex, w);
    }
    return w;
  }

  /**
   * Recalculate max line width
   */
  public void recalculateMaxLineWidth() {
    float mx = 0f;
    synchronized (linesWindow) {
      for (int i = 0; i < linesWindow.size(); i++) {
        String line = linesWindow.get(i);
        mx = Math.max(mx, getWidthForLine(windowStartLine + i, line));
      }
    }
    currentMaxWindowLineWidth = mx;
    globalMaxLineWidth = Math.max(globalMaxLineWidth, currentMaxWindowLineWidth);
  }

}
