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
import com.yn.sodiumeditor.core.wordwrap.WordWrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import com.yn.sodiumeditor.core.StreamedSliceRequest;
import com.yn.sodiumeditor.renderer.WindowRender;
import com.yn.sodiumeditor.utils.FunctionLog;
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

  
  private float cachedSpaceWidth = -1f;
  private float cachedTabWidth = -1f;
  private int cachedBaseIndex = -1;
  private float cachedBaseIndexResult = 0f;
  private int lastFrameBaseLine = -1;


  
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
    public static final int DEFAULT_TAB_SIZE_SPACES = 4;

    public final int[] visibleCharRangeTmp = new int[2];

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
        FunctionLog.f("TextRender", "TextRender", editor);
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
     * Get paint for a specific character based on syntax highlighting
     */
    public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
        FunctionLog.f("TextRender", "getPaintForChar", lineIndex, charIndex, lineText);
        return editor.highliteRender.getPaintForChar(lineIndex, charIndex, lineText);
    }

    /**
     * Get average character width for a line
     */
    public float getAverageCharWidthForLine(String line, int lineIndex) {
        FunctionLog.f("TextRender", "getAverageCharWidthForLine", line, lineIndex);
        if (line == null || line.isEmpty()) return paint.measureText(" ");
        if (lineIndex >= 0) {
            Float cached = editor.windowRender.avgCharWidthCache.get(lineIndex);
            if (cached != null) return cached;
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? paint.measureText(line, 0, sampleLen) : paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            if (editor.view.isStableGlyphPositionsEnabled && editor.windowRender.avgCharWidthCache.get(lineIndex) != null) {
                return editor.windowRender.avgCharWidthCache.get(lineIndex);
            }
            if (editor.windowRender.avgCharWidthCache.size() > 400) editor.windowRender.avgCharWidthCache.clear();
            editor.windowRender.avgCharWidthCache.put(lineIndex, avg);
        }
        return avg;
    }

    /**
     * Draw a highlighted line with syntax highlighting, underlines, and animations
     */
    public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
        FunctionLog.f("TextRender", "drawHighlightedLine", canvas, line, globalLine, y);
        editor.highliteRender.drawHighlightedLine(canvas, line, globalLine, y);
    }

    /**
     * Draw a highlighted line range
     */
    public void drawHighlightedLineRange(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        FunctionLog.f("TextRender", "drawHighlightedLineRange", canvas, line, globalLine, start, end, y);
        editor.highliteRender.drawHighlightedLineRange(canvas, line, globalLine, start, end, y);
    }

    // ========================================================================
    // Visible Character Range Methods
    // ========================================================================

    /**
     * Get visible character range for a line (delegated)
     */
    public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
        FunctionLog.f("TextRender", "getVisibleCharRangeForLine", line, globalLine, out);
        editor.textRange.getVisibleCharRangeForLine(line, globalLine, out, isRtl, editor.view.isStableGlyphPositionsEnabled);
    }

    /**
     * Get visible character range for a line (fast version for long lines) (delegated)
     */
    public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out) {
        FunctionLog.f("TextRender", "getVisibleCharRangeForLineFast", line, globalLine, lineLength, out);
        editor.textRange.getVisibleCharRangeForLineFast(line, globalLine, lineLength, out, isRtl, editor.view.isStableGlyphPositionsEnabled);
    }

    /**
     * Compute streamed slice bounds (delegated)
     */
    public void computeStreamedSliceBounds(@Nullable String lineText, int globalLine, int lineLength, int[] out) {
        FunctionLog.f("TextRender", "computeStreamedSliceBounds", lineText, globalLine, lineLength, out);
        editor.textRange.computeStreamedSliceBounds(lineText, globalLine, lineLength, out, isRtl);
    }

    /**
     * Get initial streamed slice size (delegated)
     */
    public int getInitialStreamedSliceSize() {
        FunctionLog.f("TextRender", "getInitialStreamedSliceSize");
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
        FunctionLog.f("TextRender", "drawTextSegmentWithFade", canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
        return editor.textLineDraw.drawTextSegmentWithFade(canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
    }

    /**
     * Draw text segment with fade and underlines (delegated)
     */
    public float drawTextSegmentWithFadeAndUnderlines(
            Canvas canvas, String line, int start, int end, float x, float y,
            Paint segmentPaint, int fadeStart, int fadeEnd, float fadeAlpha,
            @Nullable List<UnderlineSpan> underlines, float lineTop, float lineBottom) {
        FunctionLog.f("TextRender", "drawTextSegmentWithFadeAndUnderlines", canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha, underlines, lineTop, lineBottom);
        return editor.textLineDraw.drawTextSegmentWithFadeAndUnderlines(canvas, line, start, end, x, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha, underlines, lineTop, lineBottom);
    }

    /**
     * Draw underline segment with fade (delegated)
     */
    public void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float baselineY,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha, boolean isPath) {
        FunctionLog.f("TextRender", "drawUnderlineSegmentWithFade", canvas, line, start, end, x, baselineY, lineTop, lineBottom, textPaint, fadeStart, fadeEnd, fadeAlpha, isPath);
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
        FunctionLog.f("TextRender", "drawTextSegmentWithVisualSpaces", canvas, line, start, end, x, y, segmentPaint, alphaMultiplier);
        return editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, alphaMultiplier);
    }

    /**
     * Draw delete animation for segment (delegated)
     */
    public void drawDeleteAnimationForSegment(Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        FunctionLog.f("TextRender", "drawDeleteAnimationForSegment", canvas, line, globalLine, segStart, segEnd, y);
        editor.textLineDraw.drawDeleteAnimationForSegment(canvas, line, globalLine, segStart, segEnd, y);
    }
    

    // ========================================================================
    // Line Number Cache Methods
    // ========================================================================

    /**
     * Check if line number cache should be used (delegated)
     */
    public boolean shouldUselineNumberCache() {
        FunctionLog.f("TextRender", "shouldUselineNumberCache");
        return editor.lineNumber.shouldUseLineNumberCache();
    }

    /**
     * Ensure line number cache bitmap exists (delegated)
     */
    public void ensurelineNumberCacheBitmap(int width, int height) {
        FunctionLog.f("TextRender", "ensurelineNumberCacheBitmap", width, height);
        editor.lineNumber.ensureLineNumberCacheBitmap(width, height);
    }

    /**
     * Write integer to chars buffer (delegated)
     */
    public int writeIntToChars(int value, char[] chars) {
        FunctionLog.f("TextRender", "writeIntToChars", value, chars);
        return editor.lineNumber.writeIntToChars(value, chars);
    }

    /**
     * Draw line numbers cached (unwrapped) (delegated)
     */
    public void drawlineNumbersCachedUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        FunctionLog.f("TextRender", "drawlineNumbersCachedUnwrapped", canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
        editor.lineNumber.drawLineNumbersCachedUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers cached (wrapped) (delegated)
     */
    public void drawlineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        FunctionLog.f("TextRender", "drawlineNumbersCachedWrapped", canvas, firstVisualIndex, lastVisualIndex);
        editor.lineNumber.drawLineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw line numbers direct (unwrapped) (delegated)
     */
    public void drawlineNumbersDirectUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        FunctionLog.f("TextRender", "drawlineNumbersDirectUnwrapped", canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
        editor.lineNumber.drawLineNumbersDirectUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers direct (wrapped) (delegated)
     */
    public void drawlineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        FunctionLog.f("TextRender", "drawlineNumbersDirectWrapped", canvas, firstVisualIndex, lastVisualIndex);
        editor.lineNumber.drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw current line number (unwrapped) (delegated)
     */
    public void drawCurrentlineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        FunctionLog.f("TextRender", "drawCurrentlineNumberUnwrapped", canvas, firstVisibleIndex, lastVisibleIndex);
        editor.lineNumber.drawCurrentlineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    /**
     * Draw current line number (wrapped) (delegated)
     */
    public void drawCurrentlineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        FunctionLog.f("TextRender", "drawCurrentlineNumberWrapped", canvas, firstVisualIndex, lastVisualIndex);
        editor.lineNumber.drawCurrentlineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw highlighted line segment
     */
    public void drawHighlightedLineSegment(
            Canvas canvas, String line, int globalLine, int start, int end, float y, float lineTop, float lineBottom) {
        FunctionLog.f("TextRender", "drawHighlightedLineSegment", canvas, line, globalLine, start, end, y, lineTop, lineBottom);
        editor.highliteRender.drawHighlightedLineSegment(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
    }

    /**
     * Draw whitespace guides for segment
     */
    public void drawWhitespaceGuidesForSegment(Canvas canvas, String line, int globalLine, int start, int end, float y) {
        FunctionLog.f("TextRender", "drawWhitespaceGuidesForSegment", canvas, line, globalLine, start, end, y);
        editor.whitespaceGuides.drawWhitespaceGuidesForSegment(canvas, line, globalLine, start, end, y);
    }

    /**
     * Draw auto suggestion (wrapped)
     */
    
    /**
     * Draw whitespace guides for line
     */
    public void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
        FunctionLog.f("TextRender", "drawWhitespaceGuidesForLine", canvas, line, globalLine, y);
        editor.whitespaceGuides.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
    }

    /**
     * Get draw line top position.
     * Coordinate system is consistent with drawing translation in ViewRender.
     */
    public float getDrawLineTop(int globalLine) {
        FunctionLog.f("TextRender", "getDrawLineTop", globalLine);
        int drawIndex = globalLine;
        if (editor.codeFold.isCodeFoldingEnabled) {
            drawIndex = editor.codeFold.getVisibleIndexForGlobalLine(globalLine);
            if (lastFrameBaseLine != editor.viewRender.drawBaseLine) {
                cachedBaseIndex = editor.codeFold.getVisibleIndexForGlobalLine(editor.viewRender.drawBaseLine);
                lastFrameBaseLine = editor.viewRender.drawBaseLine;
            }
            return (drawIndex - cachedBaseIndex) * lineHeight;
        }
        return (drawIndex - editor.viewRender.drawBaseLine) * lineHeight;
    }

    /**
     * Get draw line bottom position
     */
    public float getDrawLineBottom(int globalLine) {
        FunctionLog.f("TextRender", "getDrawLineBottom", globalLine);
        return getDrawLineTop(globalLine) + lineHeight;
    }

    /**
     * Get hit test base Y
     */
    public float getHitTestBaseY() {
        FunctionLog.f("TextRender", "getHitTestBaseY");
        int baseLine = (int) (editor.scroll.scrollY / lineHeight);
        if (baseLine < 0) baseLine = 0;
        return baseLine * lineHeight;
    }
    public void setMaxSyntaxLineLength(int maxChars) {
    FunctionLog.f("TextRender", "setMaxSyntaxLineLength", maxChars);
    editor.highliteRender.setMaxSyntaxLineLength(maxChars);
    }

    public void setPrefetchCols(int cols) {
    FunctionLog.f("TextRender", "setPrefetchCols", cols);
    editor.highliteRender.setPrefetchCols(cols);
    }

    public void setColsWidthCacheSize(int size) {
    FunctionLog.f("TextRender", "setColsWidthCacheSize", size);
    // This could also move to highliteRender if it's considered part of prefetch/window logic

    // For now keeping it here or just updating it.
    // Actually TextRender still uses avgCharWidthCache.
    }
















  // ========================================================================
  // Background Methods
  // ========================================================================

  /**
   * Set editor background color
   */
  public void setEditorBackgroundColor(int color) {
    FunctionLog.f("TextRender", "setEditorBackgroundColor", color);
    editor.view.hasEditorBackgroundColor = true;
    editor.view.editorBackgroundColor = color;
    editor.invalidate();
  }

  /**
   * Clear editor background color
   */
  public void clearEditorBackgroundColor() {
    FunctionLog.f("TextRender", "clearEditorBackgroundColor");
    editor.view.hasEditorBackgroundColor = false;
    editor.invalidate();
  }

  /**
   * Set editor background bitmap
   */
  public void setEditorBackgroundBitmap(android.graphics.Bitmap bitmap) {
    FunctionLog.f("TextRender", "setEditorBackgroundBitmap", bitmap);
    if (editor.view.editorBackgroundBitmap != null && !editor.view.editorBackgroundBitmap.isRecycled()) {
      editor.view.editorBackgroundBitmap.recycle();
    }
    editor.view.editorBackgroundBitmap = bitmap;
    editor.invalidate();
  }

  /**
   * Clear editor background image
   */
  public void clearEditorBackgroundImage() {
    FunctionLog.f("TextRender", "clearEditorBackgroundImage");
    if (editor.view.editorBackgroundBitmap != null && !editor.view.editorBackgroundBitmap.isRecycled()) {
      editor.view.editorBackgroundBitmap.recycle();
    }
    editor.view.editorBackgroundBitmap = null;
    editor.invalidate();
  }

  public void clearCachesOnTypefaceChange() {
    FunctionLog.f("TextRender", "clearCachesOnTypefaceChange");
    cachedSpaceWidth = -1f;
    cachedTabWidth = -1f;
    cachedBaseIndex = -1;
    lastFrameBaseLine = -1;
  }

  // ========================================================================
  // Line Text Access Methods
  // ========================================================================





  // ========================================================================
  // Text Measurement Methods
  // ========================================================================

  public int getVisualSpaceScale() {
    FunctionLog.f("TextRender", "getVisualSpaceScale");
    return 1;
  }

  public float getVisualSpaceWidth(Paint p) {
    FunctionLog.f("TextRender", "getVisualSpaceWidth", p);
    if (cachedSpaceWidth < 0f) {
      cachedSpaceWidth = p.measureText(" ");
    }
    return cachedSpaceWidth;
  }

  /**
   * Get character advance width
   */
  public float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
    FunctionLog.f("TextRender", "getCharAdvanceWidth", c, measuredWidth, p);
    if (c == ' ') {
      return measuredWidth;
    }
    if (c == '\t') {
      return getVisualTabWidth(p);
    }
    return measuredWidth;
  }
  
  public float getVisualTabWidth(Paint p) {
    FunctionLog.f("TextRender", "getVisualTabWidth", p);
    if (cachedTabWidth < 0f) {
      cachedTabWidth = getVisualSpaceWidth(p) * DEFAULT_TAB_SIZE_SPACES;
    }
    return cachedTabWidth;
  }

  public float measureTextWithVisualSpaces(String text, int start, int end, Paint p) {
    FunctionLog.f("TextRender", "measureTextWithVisualSpaces", text, start, end, p);
    if (text == null) return 0f;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (start >= end) return 0f;

    if (text.indexOf('\t', start) < 0) {
      return p.measureText(text, start, end);
    }

    int len = end - start;
    if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < len) {
      editor.view.measureWidthBuffer = new float[Math.max(len, 64)];
    }
    p.getTextWidths(text, start, end, editor.view.measureWidthBuffer);
    float total = 0f;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(start + i);
      total += getCharAdvanceWidth(c, editor.view.measureWidthBuffer[i], p);
    }
    return total;
  }

  public float measureText(String line, int length, int globalLine) {
    FunctionLog.f("TextRender", "measureText", line, length, globalLine);
    int logicalLen = editor.view.getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));

    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      float padX =
          editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;

      if (spans != null && spans.length > 0) {
        return editor.binaryRender.getXForCharBinary(line, safeLen, paint, spans, padX);
      } else {
        float charWidth = paint.measureText("M");
        float effectiveAvgWidth = charWidth + (padX * 2f * 0.2f);
        return effectiveAvgWidth * safeLen;
      }
    }

    if (safeLen > editor.highliteRender.maxSyntaxLineLength) {
      float avg = getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }

    java.util.List<HighliteRender.HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = editor.highlite.calculateSpansForLine(line, globalLine);
      editor.highlite.highlightCache.put(globalLine, spans);
    }

    float totalWidth = 0f;
    int lastEnd = 0;

    for (HighliteRender.HighlightSpan span : spans) {
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

  public int getCharIndexForX(String text, float x, int globalLine) {
    FunctionLog.f("TextRender", "getCharIndexForX", text, x, globalLine);
    if (text == null || text.isEmpty()) return 0;
    if (isRtl) {
      float baseX = editor.layout.getRtlLineBaseX(text, globalLine);
      x -= baseX;
      float w =
          editor.highlite.measureHighlightedSegmentWidth(
              text, globalLine, 0, editor.view.getLogicalLineLength(globalLine, text));
      x = w - x;
    }
    if (x <= 0f) return 0;
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      float padX =
          editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
      float charWidth = paint.measureText("M");

      if (spans != null && spans.length > 0) {
        return editor.binaryRender.getCharIndexForXBinary(text, 0, text.length(), x, paint, spans, padX);
      } else {
        float effectiveAvgWidth = charWidth + (padX * 2f * 0.2f);
        if (effectiveAvgWidth <= 0f) return 0;
        int idx = (int) Math.round(x / effectiveAvgWidth);
        return Math.max(0, Math.min(idx, text.length()));
      }
    }

    int len = editor.view.getLogicalLineLength(globalLine, text);
    if (len > editor.highliteRender.maxSyntaxLineLength) {
      float avg = getAverageCharWidthForLine(text, globalLine);
      if (avg <= 0f) return 0;
      int idx = (int) Math.round(x / avg);
      return Math.max(0, Math.min(idx, len));
    }
    int textLen = text.length();
    if (getVisualSpaceScale() == 1) {
      int count = paint.breakText(text, true, x, null);
      if (count <= 0) return 0;
      if (count >= textLen) return textLen;

      float wPrev = (count > 1) ? paint.measureText(text, 0, count - 1) : 0f;
      float wCount = paint.measureText(text, 0, count);
      float mid = wPrev + (wCount - wPrev) * 0.5f;
      return (x < mid) ? (count - 1) : count;
    }

    if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < textLen) {
      editor.view.measureWidthBuffer = new float[Math.max(textLen, 64)];
    }
    paint.getTextWidths(text, 0, textLen, editor.view.measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < textLen; i++) {
      float adv = getCharAdvanceWidth(text.charAt(i), editor.view.measureWidthBuffer[i], paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return i;
      if (x < current + adv) return i + 1;
      current += adv;
    }
    return textLen;
  }



















  // ========================================================================
  // Window Management Methods
  // ========================================================================
}
