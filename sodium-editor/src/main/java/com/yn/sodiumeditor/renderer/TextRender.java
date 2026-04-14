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
    public static final ThreadLocal<ArrayList<UnderlineSpan>> TL_UNDERLINES =
        ThreadLocal.withInitial(ArrayList::new);
    public static final ThreadLocal<Paint.FontMetrics> TL_FONT_METRICS =
        ThreadLocal.withInitial(Paint.FontMetrics::new);

    public final int[] binaryTokenSpanTmp = new int[2];
public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 100; // 2000 yyy
  public int prefetchLines = 100; // 1000 yyy
  public final java.util.LinkedHashMap<Integer, String> modifiedLines =
      new java.util.LinkedHashMap<Integer, String>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
          return size() > 1000;
        }
      };
  public final android.util.SparseArray<Float> lineWidthCache = new android.util.SparseArray<>(400);
  public int lineWidthCacheSize = 100; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  
  public final android.util.SparseArray<Float> avgCharWidthCache = new android.util.SparseArray<>(400);
  
  private float cachedSpaceWidth = -1f;
  private float cachedTabWidth = -1f;
  private int cachedBaseIndex = -1;
  private float cachedBaseIndexResult = 0f;
  private int lastFrameBaseLine = -1;

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


    // Visible character range
    public final int[] visibleCharRangeTmp = new int[2];
    public boolean isPerformanceModeEnabled = false;
    public boolean isStableGlyphPositionsEnabled = true;


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

    public TextRender(SodiumEditor editor) {
        this.editor = editor;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.baseTypeface = Typeface.DEFAULT;
        this.lineHeight = 0f;
        this.paddingLeft = 0f;
        this.isRtl = false;
        this.textBounds = new Rect();
        
        // Initialize binary render cached character width
        editor.binaryRender.updateCachedCharWidth(paint);
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
        return editor.highliteRender.getPaintForChar(lineIndex, charIndex, lineText);
    }

    /**
     * Get average character width for a line
     */
    public float getAverageCharWidthForLine(String line, int lineIndex) {
        if (line == null || line.isEmpty()) return paint.measureText(" ");
        if (lineIndex >= 0) {
            Float cached = avgCharWidthCache.get(lineIndex);
            if (cached != null) return cached;
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? paint.measureText(line, 0, sampleLen) : paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            if (isStableGlyphPositionsEnabled && avgCharWidthCache.get(lineIndex) != null) {
                return avgCharWidthCache.get(lineIndex);
            }
            if (avgCharWidthCache.size() > 400) avgCharWidthCache.clear();
            avgCharWidthCache.put(lineIndex, avg);
        }
        return avg;
    }

    /**
     * Draw a highlighted line with syntax highlighting, underlines, and animations
     */
    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
        editor.highliteRender.drawHighlightedLine(canvas, line, globalLine, y);
    }

    /**
     * Draw a highlighted line range
     */
    public void drawHighlightedLineRange(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        editor.highliteRender.drawHighlightedLineRange(canvas, line, globalLine, start, end, y);
    }

    // ========================================================================
    // Visible Character Range Methods
    // ========================================================================

    /**
     * Get visible character range for a line (delegated)
     */
    public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
        editor.textRange.getVisibleCharRangeForLine(line, globalLine, out, isRtl, isStableGlyphPositionsEnabled);
    }

    /**
     * Get visible character range for a line (fast version for long lines) (delegated)
     */
    public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out) {
        editor.textRange.getVisibleCharRangeForLineFast(line, globalLine, lineLength, out, isRtl, isStableGlyphPositionsEnabled);
    }

    /**
     * Compute streamed slice bounds (delegated)
     */
    public void computeStreamedSliceBounds(@Nullable String lineText, int globalLine, int lineLength, int[] out) {
        editor.textRange.computeStreamedSliceBounds(lineText, globalLine, lineLength, out, isRtl);
    }

    /**
     * Get initial streamed slice size (delegated)
     */
    public int getInitialStreamedSliceSize() {
        return editor.textRange.getInitialStreamedSliceSize();
    }

    // ========================================================================
    // Text Drawing with Visual Spaces and Fade Effects
    // ========================================================================

    /**
     * Draw text segment with fade effect (delegated)
     */
    public float drawTextSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha) {
        return editor.textLineDraw.drawTextSegmentWithFade(canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
    }

    /**
     * Draw text segment with fade and underlines (delegated)
     */
    public float drawTextSegmentWithFadeAndUnderlines(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha,
            @Nullable List<UnderlineSpan> underlines, float lineTop, float lineBottom) {
        return editor.textLineDraw.drawTextSegmentWithFadeAndUnderlines(canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha, underlines, lineTop, lineBottom);
    }

    /**
     * Draw underline segment with fade (delegated)
     */
    public void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float baselineY,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha, boolean isPath) {
        editor.textLineDraw.drawUnderlineSegmentWithFade(canvas, line, start, end, x, baselineY, lineTop, lineBottom, textPaint, fadeStart, fadeEnd, fadeAlpha, isPath);
    }

    /**
     * Draw text segment with visual spaces (delegated)
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
        return editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, alphaMultiplier);
    }

    /**
     * Draw delete animation for segment (delegated)
     */
    public void drawDeleteAnimationForSegment(Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        editor.textLineDraw.drawDeleteAnimationForSegment(canvas, line, globalLine, segStart, segEnd, y);
    }
    

    // ========================================================================
    // Line Number Cache Methods
    // ========================================================================

    /**
     * Check if line number cache should be used (delegated)
     */
    public boolean shouldUselineNumberCache() {
        return editor.lineNumber.shouldUseLineNumberCache();
    }

    /**
     * Ensure line number cache bitmap exists (delegated)
     */
    public void ensurelineNumberCacheBitmap(int width, int height) {
        editor.lineNumber.ensureLineNumberCacheBitmap(width, height);
    }

    /**
     * Write integer to chars buffer (delegated)
     */
    public int writeIntToChars(int value, char[] chars) {
        return editor.lineNumber.writeIntToChars(value, chars);
    }

    /**
     * Draw line numbers cached (unwrapped) (delegated)
     */
    public void drawlineNumbersCachedUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        editor.lineNumber.drawLineNumbersCachedUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers cached (wrapped) (delegated)
     */
    public void drawlineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        editor.lineNumber.drawLineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw line numbers direct (unwrapped) (delegated)
     */
    public void drawlineNumbersDirectUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        editor.lineNumber.drawLineNumbersDirectUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers direct (wrapped) (delegated)
     */
    public void drawlineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        editor.lineNumber.drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw current line number (unwrapped) (delegated)
     */
    public void drawCurrentlineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        editor.lineNumber.drawCurrentlineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    /**
     * Draw current line number (wrapped) (delegated)
     */
    public void drawCurrentlineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        editor.lineNumber.drawCurrentlineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw highlighted line segment
     */
    public void drawHighlightedLineSegment(
            Canvas canvas, String line, int globalLine, int start, int end, float y, float lineTop, float lineBottom) {
        editor.highliteRender.drawHighlightedLineSegment(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
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
     * Get draw line top position.
     * Coordinate system is consistent with drawing translation in ViewRender.
     */
    public float getDrawLineTop(int globalLine) {
        int drawIndex = globalLine;
        if (editor.codeFold.isCodeFoldingEnabled) {
            drawIndex = editor.codeFold.getVisibleIndexForGlobalLine(globalLine);
            if (lastFrameBaseLine != editor.drawBaseLine) {
                cachedBaseIndex = editor.codeFold.getVisibleIndexForGlobalLine(editor.drawBaseLine);
                lastFrameBaseLine = editor.drawBaseLine;
            }
            return (drawIndex - cachedBaseIndex) * lineHeight;
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
    editor.highliteRender.setMaxSyntaxLineLength(maxChars);
  }

  public void setPrefetchCols(int cols) {
    editor.highliteRender.setPrefetchCols(cols);
  }

  public void setColsWidthCacheSize(int size) {
    // This could also move to highliteRender if it's considered part of prefetch/window logic
    // For now keeping it here or just updating it.
    // Actually TextRender still uses avgCharWidthCache.
  }


  public void setWindowSize(int size) {
    int safe = Math.max(10, size);
    int minWindow = computeMinWindowSize();
    if (safe < minWindow) safe = minWindow;
    if (windowSize == safe) return;
    windowSize = safe;
    editor.highlite.invalidateHighlightEnsureRange();
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
    editor.highlite.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setLineWidthCacheSize(int size) {
    int safe = Math.max(10, size);
    if (lineWidthCacheSize == safe) return;
    lineWidthCacheSize = safe;
    if (lineWidthCache.size() > lineWidthCacheSize) {
      int excess = lineWidthCache.size() - lineWidthCacheSize;
      for (int i = lineWidthCache.size() - 1; i >= 0 && excess > 0; i--) {
        lineWidthCache.removeAt(i);
        excess--;
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
    editor.highlite.invalidateHighlightEnsureRange();
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
    int firstVisibleLine = Math.max(0, editor.wordWrap.getGlobalLineForY(editor.scroll.scrollY));
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
    editor.codeFold.animation.foldMarkerPaint.setTypeface(finalTypeface);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(finalTypeface);
    if (editor.highlightRules.whitespaceStringRule != null)
      editor.highlightRules.whitespaceStringRule.updateTypeface(safeBase);
    if (editor.highlightRules.whitespaceCommentRule != null)
      editor.highlightRules.whitespaceCommentRule.updateTypeface(safeBase);
    if (editor.highlightRules.lineCommentHighlightRule != null)
      editor.highlightRules.lineCommentHighlightRule.updateTypeface(safeBase);
    for (HighliteRender.HighlightRule rule : editor.highlite.highlightRules) {
      rule.updateTypeface(safeBase);
    }
    editor.highlite.clearHighlightCaches();

    lineHeight = paint.getFontSpacing();
    editor.whitespaceGuides.updateMetrics();
    editor.lineNumber.invalidateLineNumberCache();
    editor.wordWrap.indicator.wordWrapIndicatorWidth =
        editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            com.yn.sodiumeditor.core.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);

    cachedSpaceWidth = -1f;
    cachedTabWidth = -1f;
    cachedBaseIndex = -1;
    lastFrameBaseLine = -1;
    lineWidthCache.clear();
    avgCharWidthCache.clear();
    
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
    
    // Update binary render cached character width
    editor.binaryRender.updateCachedCharWidth(paint);
    
    if (!editor.autoCompletion.isSuggestionTextSizeCustom) {
      editor.autoCompletion.suggestionTextSizeScale = 1f;
    }
    editor.autoCompletion.suggestionPaint.setTextSize(sizePx * editor.autoCompletion.suggestionTextSizeScale);
    editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
    editor.codeFold.animation.foldMarkerPaint.setTextSize(sizePx * editor.codeFold.animation.foldMarkerTextScale);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
        sizePx * editor.wordWrap.indicator.wordWrapIndicatorTextScale);
    editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(paint.getTypeface());
    editor.wordWrap.indicator.wordWrapIndicatorWidth =
        editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            com.yn.sodiumeditor.core.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);
    lineHeight = paint.getFontSpacing();
    editor.recalculateMaxLineWidth();
    editor.whitespaceGuides.updateMetrics();
    editor.lineNumber.invalidateLineNumberCache();

    cachedSpaceWidth = -1f;
    cachedTabWidth = -1f;
    cachedBaseIndex = -1;
    lastFrameBaseLine = -1;
    lineWidthCache.clear();
    avgCharWidthCache.clear();

    for (HighliteRender.HighlightRule rule : editor.highlite.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (editor.highlightRules.whitespaceStringRule != null)
      editor.highlightRules.whitespaceStringRule.updateTextSize(sizePx);
    if (editor.highlightRules.whitespaceCommentRule != null)
      editor.highlightRules.whitespaceCommentRule.updateTextSize(sizePx);
    if (editor.highlightRules.lineCommentHighlightRule != null)
      editor.highlightRules.lineCommentHighlightRule.updateTextSize(sizePx);
    editor.highlite.clearHighlightCaches();

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

    // Modified lines first — always the most recent edits
    String mod = modifiedLines.get(line);
    if (mod != null) return mod;

    // Then the window
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = editor.getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }

    // Direct batch (during fast fling)
    if (direct != null) {
      String d = direct.get(line);
      if (d != null) return d;
    }

    // Cache
    String c = editor.fileIO.directLineCache.get(line);
    if (c != null) return c;

    return "";
  }

  /**
   * Get line text for render (render-safe, no file random read)
   */
  public String getLineTextForRender(int line) {
    if (line < 0) return "";

    // Modified lines first — always the most recent edits
    String mod = modifiedLines.get(line);
    if (mod != null) return mod;

    // Then the window
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = editor.getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }

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
  
  public float getVisualTabWidth(Paint p) {
    if (cachedTabWidth < 0f) {
      cachedTabWidth = p.measureText(" ") * DEFAULT_TAB_SIZE_SPACES;
    }
    return cachedTabWidth;
  }
}
