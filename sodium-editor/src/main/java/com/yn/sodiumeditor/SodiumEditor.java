package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.widget.OverScroller;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.yn.sodiumeditor.Input.events.OnScroll;
import com.yn.sodiumeditor.Input.events.OnTouch;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections; // Added for Collections.sort
// For Draw logic
// For Draw logic
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher; // Added for Matcher
import java.util.regex.Pattern; // Added for Pattern
import android.widget.Scroller;

import com.yn.sodiumeditor.Input.Ime;

public class SodiumEditor extends View {

  public static final int STYLE_NORMAL = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_BOLD_ITALIC = 3;

  // paint & metrics
  public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public Typeface baseTypeface = Typeface.DEFAULT;
  public float lineHeight;
  public float paddingLeft = 10f;
  public boolean isRtl = false;
  public final Rect textBounds = new Rect();
  public final int[] tmpLocationInWindow = new int[2];
public boolean binarySafeRenderingEnabled = false;
  // visual padding constants
  public static final float BOTTOM_SCROLL_OFFSET = 100f; // Visual padding below last line
  public static final float MIN_BOTTOM_VISIBLE_SPACE =
      50f; // Minimum space to show below last line

  // scroll state (pixels)
  

  // sliding window
  public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 30; // 2000 yyy
  public int prefetchLines = 10; // 1000 yyy

  // IO
  public final HandlerThread ioThread;
  public final Handler ioHandler;
  public BufferedReader readerForFile = null;
  public File sourceFile = null;
  public boolean isEof = false;
  public final AtomicInteger ioTaskVersion = new AtomicInteger(0);
  public boolean isFileCleared = false; // Track if the file content has been cleared

  // caches
  public final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
  public final LinkedHashMap<Integer, Float> lineWidthCache;
  public int lineWidthCacheSize = 200; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  
  public int maxSyntaxLineLength = 4096;
  public int prefetchCols = 512;
  public int colsWidthCacheSize = 256;
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
  public Charset fileCharset = StandardCharsets.UTF_8;

  // --- Cursor Blinking State ---
  public boolean isCursorVisible = true;
  public final Runnable blinkRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (isFocused() && ! selection.hasSelection) {
            isCursorVisible = !isCursorVisible;
            invalidateCursorArea();
            mainHandler.postDelayed(this, 500);
          }
        }
      };
  

 
  public static final long FLING_STOP_ANIM_DURATION_MS = 90;
  
  // Scroll for handling scroll logic
  public final Scroll scroll;

  // --- Zoom State ---
  
  
  public String searchQuery = "";
  public boolean searchUseRegex = false;
  public boolean searchCaseSensitive = false;
  public boolean searchWrap = true;
  public boolean searchHighlightEnabled = true;
  public int searchHighlightColor = 0x66FFD54F;
  public Pattern searchPattern = null;
  public String searchCacheKey = null;
  public int searchCacheEditVersion = -1;
  public final java.util.HashMap<Integer, int[]> searchMatchCache = new java.util.HashMap<>();
  public final Paint searchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public boolean mHighlightCurrentSearchMatch = false;
  public int mCurrentSearchMatchColor =
      0x9933B5E5; // A distinct default color for the current match
  public final Paint mCurrentSearchMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public ScaleGestureDetector scaleGestureDetector;
  
  // Zoom for handling zoom logic
  public final Zoom zoom;

  // Ime for handling IME logic
  public final Ime ime;

  // OnTouch for handling touch events
  public final OnTouch onTouch;

  // OnScroll for handling scroll gestures
  public final OnScroll onScroll;

  // Popup for handling popup menu logic
  public final Popup popup;

  // CursorAnimation for handling cursor movement animation
  public final CursorAnimation cursorAnimation;

  // CharAnimation for handling character fade animations
  public final CharAnimation charAnimation;

public final LineNumber lineNumber;

  // --- Search State ---

  public boolean isSearchActive() {
    if (searchQuery == null || searchQuery.isEmpty()) return false;
    if (searchUseRegex) return searchPattern != null;
    return true;
  }

  public void clearSearchMatchCache() {
    searchMatchCache.clear();
    searchCacheEditVersion = -1;
    searchCacheKey = null;
  }

  public String getSearchCacheKey() {
    return searchQuery
        + "|"
        + (searchUseRegex ? "r" : "t")
        + "|"
        + (searchCaseSensitive ? "c" : "i");
  }

  public int[] getSearchMatchSpansForLine(String line, int globalLine) {
    if (!searchHighlightEnabled || !isSearchActive() || line == null || line.isEmpty())
      return new int[0];

    int version = editVersion.get();
    String key = getSearchCacheKey();
    if (searchCacheEditVersion != version
        || (searchCacheKey != null && !searchCacheKey.equals(key))) {
      searchMatchCache.clear();
      searchCacheEditVersion = version;
      searchCacheKey = key;
    }

    int[] cached = searchMatchCache.get(globalLine);
    if (cached != null) return cached;

    ArrayList<Integer> tmp = null;
    if (searchUseRegex && searchPattern != null) {
      Matcher matcher = searchPattern.matcher(line);
      while (matcher.find()) {
        if (matcher.start() == matcher.end()) continue;
        if (tmp == null) tmp = new ArrayList<>();
        tmp.add(matcher.start());
        tmp.add(matcher.end());
      }
    } else {
      String haystack = searchCaseSensitive ? line : line.toLowerCase(java.util.Locale.ROOT);
      String needle =
          searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(java.util.Locale.ROOT);
      if (!needle.isEmpty()) {
        int idx = haystack.indexOf(needle, 0);
        while (idx >= 0) {
          if (tmp == null) tmp = new ArrayList<>();
          tmp.add(idx);
          tmp.add(idx + needle.length());
          idx = haystack.indexOf(needle, idx + Math.max(1, needle.length()));
        }
      }
    }

    int[] spans;
    if (tmp == null || tmp.isEmpty()) {
      spans = new int[0];
    } else {
      spans = new int[tmp.size()];
      for (int i = 0; i < tmp.size(); i++) spans[i] = tmp.get(i);
    }
    searchMatchCache.put(globalLine, spans);
    return spans;
  }

  public void drawSearchHighlightsForLine(
      Canvas canvas, String line, int globalLine, float top, float bottom) {
    int[] spans = getSearchMatchSpansForLine(line, globalLine);
    if (spans.length == 0) return;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int start = spans[i];
      int end = spans[i + 1];
      if (end <= start) continue;
      float left = measureText(line, start, globalLine);
      float right = measureText(line, end, globalLine);

      boolean isCurrentMatch =
          mHighlightCurrentSearchMatch
              && !selection.hasSelection
              && globalLine == cursor.cursorLine
              && cursor.cursorChar >= start
              && cursor.cursorChar <= end;

      Paint paintToUse = isCurrentMatch ? mCurrentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  public void drawSearchHighlightsForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float top,
      float bottom) {
    int[] spans = getSearchMatchSpansForLine(line, globalLine);
    if (spans.length == 0) return;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int start = spans[i];
      int end = spans[i + 1];
      if (end <= start) continue;
      int s = Math.max(segStart, start);
      int e = Math.min(segEnd, end);
      if (e <= s) continue;
      float left = measureTextWithVisualSpaces(line, segStart, s, paint);
      float right = left + measureTextWithVisualSpaces(line, s, e, paint);

      boolean isCurrentMatch =
          mHighlightCurrentSearchMatch
              && !selection.hasSelection
              && globalLine == cursor.cursorLine
              && cursor.cursorChar >= start
              && cursor.cursorChar <= end;

      Paint paintToUse = isCurrentMatch ? mCurrentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  public boolean goToSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, cursor.cursorLine);
    int startChar = Math.max(0, cursor.cursorChar);

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    ensureLineInWindow(match.line, true);
    setCursorPosition(match.line, match.start);
    return true;
  }

  public SearchMatch findNextSearchMatchFrom(int line, int charIndex) {
    int total = getLinesCount();
    if (total <= 0) return null;

    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();

    SearchMatch m = findNextSearchMatchInRange(line, total - 1, charIndex + 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line > 0) {
      return findNextSearchMatchInRange(0, line, 0, charIndex, direct);
    }
    return null;
  }

  public SearchMatch findPrevSearchMatchFrom(int line, int charIndex) {
    int total = getLinesCount();
    if (total <= 0) return null;

    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();

    SearchMatch m = findPrevSearchMatchInRange(line, 0, charIndex - 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line < total - 1) {
      return findPrevSearchMatchInRange(total - 1, line, Integer.MAX_VALUE, charIndex - 1, direct);
    }
    return null;
  }

  public SearchMatch findNextSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer maxStartInclusive,
      java.util.HashMap<Integer, String> direct) {
    int step = (startLine <= endLine) ? 1 : -1;
    int line = startLine;
    int total = getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < windowStartLine || line >= windowStartLine + linesWindow.size()) {
        if (isIndexReady && sourceFile != null && sourceFile.exists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          populateDirectLinesForRange(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = getLineTextForRenderWithDirect(line, direct);
      if (lineText == null) lineText = "";
      int from =
          (line == startLine) ? Math.max(0, Math.min(startCharExclusive, lineText.length())) : 0;
      Integer limit = (maxStartInclusive != null && line == endLine) ? maxStartInclusive : null;
      SearchMatch m = findMatchForwardInLine(lineText, from, limit);
      if (m != null) {
        m.line = line;
        return m;
      }
      if (line == endLine) break;
      line += step;
    }
    return null;
  }

  public SearchMatch findPrevSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer minStartInclusive,
      java.util.HashMap<Integer, String> direct) {
    int step = (startLine >= endLine) ? -1 : 1;
    int line = startLine;
    int total = getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < windowStartLine || line >= windowStartLine + linesWindow.size()) {
        if (isIndexReady && sourceFile != null && sourceFile.exists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          populateDirectLinesForRange(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = getLineTextForRenderWithDirect(line, direct);
      if (lineText == null) lineText = "";
      int from =
          (line == startLine)
              ? Math.max(0, Math.min(startCharExclusive, lineText.length()))
              : lineText.length();
      Integer limit = (minStartInclusive != null && line == endLine) ? minStartInclusive : null;
      SearchMatch m = findMatchBackwardInLine(lineText, from, limit);
      if (m != null) {
        m.line = line;
        return m;
      }
      if (line == endLine) break;
      line += step;
    }
    return null;
  }

  public SearchMatch findMatchForwardInLine(
      String line, int fromIndex, @Nullable Integer maxStartInclusive) {
    if (line == null || line.isEmpty()) return null;
    if (searchUseRegex && searchPattern != null) {
      Matcher m = searchPattern.matcher(line);
      if (m.find(fromIndex)) {
        if (maxStartInclusive != null && m.start() > maxStartInclusive) return null;
        return new SearchMatch(-1, m.start(), m.end());
      }
      return null;
    }
    String haystack = searchCaseSensitive ? line : line.toLowerCase(java.util.Locale.ROOT);
    String needle =
        searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(java.util.Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.indexOf(needle, fromIndex);
    if (idx < 0) return null;
    if (maxStartInclusive != null && idx > maxStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  public SearchMatch findMatchBackwardInLine(
      String line, int fromIndex, @Nullable Integer minStartInclusive) {
    if (line == null || line.isEmpty()) return null;
    if (searchUseRegex && searchPattern != null) {
      Matcher m = searchPattern.matcher(line);
      SearchMatch last = null;
      while (m.find()) {
        if (m.start() > fromIndex) break;
        if (minStartInclusive != null && m.start() < minStartInclusive) continue;
        last = new SearchMatch(-1, m.start(), m.end());
      }
      return last;
    }
    String haystack = searchCaseSensitive ? line : line.toLowerCase(java.util.Locale.ROOT);
    String needle =
        searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(java.util.Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.lastIndexOf(needle, Math.min(fromIndex, haystack.length()));
    if (idx < 0) return null;
    if (minStartInclusive != null && idx < minStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  public static class SearchMatch {
    int line;
    int start;
    int end;

    SearchMatch(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  public void applyPendingWrapPrefixUpdateIfAny() {
    if (!zoom.pendingApplyWrapPrefixUpdate) return;
    if (!isWordWrapEnabled) {
      zoom.pendingApplyWrapPrefixUpdate = false;
      zoom.pendingWrapPrefixCounts = null;
      zoom.pendingWrapPrefixPrefix = null;
      return;
    }
    if (zoom.isZoomGestureActive()) return;
    if (zoom.pendingWrapPrefixCounts == null || zoom.pendingWrapPrefixPrefix == null) {
      zoom.pendingApplyWrapPrefixUpdate = false;
      return;
    }
    // Only apply if the wrap width still matches; otherwise a new rebuild will be scheduled.
    int currentWidthPx = Math.max(1, Math.round(getWrapWidth()));
    if (zoom.pendingWrapPrefixWidthPx != currentWidthPx) {
      zoom.pendingApplyWrapPrefixUpdate = false;
      zoom.pendingWrapPrefixCounts = null;
      zoom.pendingWrapPrefixPrefix = null;
      return;
    }

    // Keep the current top visual line anchored while swapping in the new prefix arrays.
    int anchorFirstVisual = Math.max(0, (int) ( scroll.scrollY / lineHeight));
    VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    wrapLineCounts = zoom.pendingWrapPrefixCounts;
    wrapLinePrefix = zoom.pendingWrapPrefixPrefix;
    totalWrapVisualLines = zoom.pendingWrapPrefixTotalVisualLines;
    wrapMetricsWidth = zoom.pendingWrapPrefixWidthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, zoom.pendingWrapPrefixValidUpToLine);

    zoom.pendingApplyWrapPrefixUpdate = false;
    zoom.pendingWrapPrefixCounts = null;
    zoom.pendingWrapPrefixPrefix = null;

    if (anchorLine >= 0 && wrapLinePrefix != null && anchorLine < wrapLinePrefix.length) {
      int newAnchorFirstVisual = wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        scroll.scrollY += dv * lineHeight;
        scroll.clampScrollY();
      }
    }
  }

  // Cursor management
  public final Cursor cursor;
  public final Caret caret;
  public final CursorHandle cursorHandle;
  public final Selection selection;
  public final SelectionHandles selectionHandles;

  // Line number selection
  

  // touch helpers
  public boolean pointerDown = false;
  public boolean movedSinceDown = false;
  public float downX = 0f, downY = 0f;
  public final int touchSlop;

  public boolean multiTouchActive = false;
  public boolean hadMultiTouch = false;

  // auto-scroll when dragging handles
  public final Handler mainHandler = new Handler(Looper.getMainLooper());
  public float autoScrollX = 0f, autoScrollY = 0f;
  public float lastTouchX = 0f, lastTouchY = 0f;

  // keyboard awareness
  public final Rect visibleDisplayFrame = new Rect();
  public int keyboardHeight = 0;

  // selection handles
  public RectF leftHandleRect = new RectF();
  public RectF rightHandleRect = new RectF();
  public RectF cursorHandleRect = new RectF();
  public float handleRadius = 30f;
  public float cursorWidth = 6f;
  public float baseHandleRadiusPx = handleRadius;
  public float baseHandleTextSizePx = 0f;
  public float baseCursorWidthPx = cursorWidth;
  public float baseCursorTextSizePx = 0f;
  public int selectionHighlightColor = 0x8033B5E5;
  public int cursorAndHandlesColor = 0xFF2196F3;
  public int caretColor = cursorAndHandlesColor;
  public int cursorHandleColor = cursorAndHandlesColor;
  public int selectionHandleColor = cursorAndHandlesColor;
  public boolean isBracketMatchingEnabled = false;
  public final Paint bracketMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketMatchStrokeWidth = 2f;
  public float baseBracketMatchStrokeWidth = bracketMatchStrokeWidth;
  public float baseBracketMatchTextSizePx = 0f;
  public final RectF bracketMatchRect = new RectF();
  @Nullable public BracketMatch cachedBracketMatch = null;
  public int cachedBracketMatchCursorLine = -1;
  public int cachedBracketMatchCursorChar = -1;
  public int cachedBracketMatchEditVersion = -1;
  public boolean isBracketGuidesEnabled = false;
  public final Paint bracketGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float bracketGuideStrokeWidth = 2f;
  public float baseBracketGuideStrokeWidth = bracketGuideStrokeWidth;
  public float baseBracketGuideTextSizePx = 0f;
  public boolean isIndentGuidesEnabled = false;
  public final Paint indentGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float indentGuideStrokeWidth = 2f;
  public float baseIndentGuideStrokeWidth = indentGuideStrokeWidth;
  public float baseIndentGuideTextSizePx = 0f;
  public final java.util.ArrayList<int[]> indentGuideIntervals = new java.util.ArrayList<>();
  public boolean indentGuideIntervalsDirty = true;
  public float[] guideSeenXBuffer;
  public int guideSeenXCount = 0;
  public boolean isWhitespaceGuidesEnabled = false;
  public final Paint whitespaceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint whitespaceGuideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float whitespaceGuideSpaceWidth = 0f;
  public float whitespaceGuideTabWidth = 0f;
  public float[] whitespaceWidthBuffer;
  public float[] whitespaceDotBuffer;
  public float[] measureWidthBuffer;
  public int whitespaceGuideSpaceStep = 1;
  public static final int DEFAULT_TAB_SIZE_SPACES = 4;

  public static final class WhitespaceDrawState {
    int syntaxIndex;
  }

  public final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();
  public final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint loadingCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF loadingCircleRect = new RectF();
  public final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();
  public final Path teardropPath = new Path();
  public final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float foldMarkerGutterWidth = 0f;
  public float foldMarkerTextScale = 1f;
  public float foldMarkerSpacing = 0f;
  public float foldMarkerEdgePadding = 4f;
  public ValueAnimator foldRippleAnimator;
  public int foldRippleLine = -1;
  public float foldRippleRadius = 0f;
  public float foldRippleAlpha = 0f;
  public float foldRippleMaxRadius = 0f;

  // editor background
  public boolean hasEditorBackgroundColor = false;
  public int editorBackgroundColor = 0x00000000;
  @Nullable public Bitmap editorBackgroundBitmap = null;
  public final Rect editorBackgroundDst = new Rect();

  // selection drawing (rounded)
    public final RectF foldPlaceholderRect = new RectF();

  // handle dragging edge flags (to prevent horizontal autoscroll beyond line bounds)
  public boolean lastDragAtLineStart = false;
  public boolean lastDragAtLineEnd = false;

  // Drawing base to avoid float precision issues on very large line indices.
  // During onDraw, we render everything relative to the first visible line.
  public int drawBaseLine = 0;

  public float getDrawLineTop(int globalLine) {
    int drawIndex = globalLine;
    if (isCodeFoldingEnabled) {
      drawIndex = getVisibleIndexForGlobalLine(globalLine);
    }
    return (drawIndex - drawBaseLine) * lineHeight;
  }

  public float getDrawLineBottom(int globalLine) {
    return getDrawLineTop(globalLine) + lineHeight;
  }

  public float getHitTestBaseY() {
    int baseLine = (int) ( scroll.scrollY / lineHeight);
    if (baseLine < 0) baseLine = 0;
    return baseLine * lineHeight;
  }

  public final LinkedHashMap<Integer, int[]> colorCodeBgCache =
      new LinkedHashMap<Integer, int[]>(600, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
          return size() > 600;
        }
      };
  public HighlightRule whitespaceStringRule;
  public HighlightRule whitespaceCommentRule;
  public static final String WHITESPACE_GUIDE_SPACE = "\u00B7";
  public static final String WHITESPACE_GUIDE_TAB = "\u2192";
  public static final String FOLD_PLACEHOLDER_TEXT = "<—>";
  public static final String INDENT_BLOCK_UNIT = "  ";
  public static final int INDENT_FOLD_SCAN_LIMIT = 2000;
  public float foldPlaceholderCorner = 3f;
  public float foldPlaceholderPadX = 3f;
  public float foldPlaceholderPadY = 2f;
  public final java.util.HashMap<Integer, FoldRange> foldRanges = new java.util.HashMap<>();
  public final java.util.ArrayList<int[]> foldIntervals = new java.util.ArrayList<>();
  public boolean foldIntervalsDirty = true;

  // --- Current Line Highlight State ---
  public boolean highlightCurrentLine = true;
  public boolean isClickAfterEndToAddLineEnabled = false;
  public boolean isAutoPairingEnabled = false;
  public boolean isAutoBracketNewlineEnabled = false;
  public boolean isAutoBracketNewlineIndentEnabled = false;
  public boolean isAutoIndentAfterClosingBracketEnabled = false;
  public boolean isIndentationBlocksEnabled = false;
  public boolean isCodeFoldingEnabled = false;
  public int currentLineHighlightColor = 0x202196F3; // Default: translucent gray (more visible)

  public int draggingHandle = 0;
  public volatile boolean isWindowLoading = false;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;
  public final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables
  public boolean showLoadingCircle = false;
  public float loadingCircleRadius = 40f;
  public int loadingCircleColor = 0xFF3F51B5;
  public float loadingCircleRotation = 0f;
  public ValueAnimator rotationAnimator;
  public boolean showLoadingOnFileOpen = true;
  public boolean isInitialFileOpenLoading = false;
  public int initialFileOpenToken = 0;
  @Nullable public Runnable initialFileOpenShowSpinner;
  public final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
  public int maxWidthRecalcToken = 0;

  // index
  public final Object lineOffsetsLock = new Object();
  public long[] lineOffsets = new long[0];
  public volatile boolean isIndexReady = false;
  public volatile boolean isIndexBuilding = false;
  public volatile boolean isIndexDisabled = false;
  @Nullable public volatile String indexDisabledPath = null;
  public volatile long indexDisabledFileLength = -1L;
  public static final long MAX_INDEX_BYTES_HARD = 64L * 1024 * 1024;

  // edit version (to ignore old rewrite results)
  public final AtomicInteger editVersion = new AtomicInteger(0);
  public static final int UNDO_STACK_LIMIT = 200;
  public static final int UNDO_TEXT_LIMIT = 1_000_000;
  public final java.util.ArrayDeque<EditOp> undoStack = new java.util.ArrayDeque<>();
  public final java.util.ArrayDeque<EditOp> redoStack = new java.util.ArrayDeque<>();
  public final java.util.ArrayDeque<EditOp> pendingEdits = new java.util.ArrayDeque<>();
  public final java.util.ArrayDeque<EditOp> pendingRedo = new java.util.ArrayDeque<>();
  public boolean isApplyingUndoRedo = false;
  public volatile long lastEditTimestamp = 0L;
  public int lineCountDelta = 0;
  // Large edit UI (brief busy indicator)
  public static final int LARGE_EDIT_LINES = 8000; // show spinner/disable for very large edits
  public final AtomicInteger largeEditUiToken = new AtomicInteger(0);
  public final Runnable largeEditUiWatchdog =
      new Runnable() {
        @Override
        public void run() {
          // Safety: never allow spinner/disable to get stuck forever
          endLargeEditUi(false);
        }
      };

  // Direct read cache for fast fling rendering when window hasn't loaded yet (index-based)
  public final LinkedHashMap<Integer, String> directLineCache =
      new LinkedHashMap<Integer, String>(600, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
          return size() > 600;
        }
      };

  public int lastHighlightEnsureStartLine = -1;
  public int lastHighlightEnsureEndLine = -1;
  public int lastHighlightEnsureEditVersion = -1;

  public int bracketGuideCacheStartLine = -1;
  public int bracketGuideCacheEndLine = -1;
  public int bracketGuideCacheEditVersion = -1;
  public int bracketGuideCacheConfigHash = 0;
  public final java.util.ArrayList<List<BracketGuideToken>> bracketGuideTokensWindow =
      new java.util.ArrayList<>();

  // --- Syntax Highlighting State ---
  public final java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();
  @Nullable public HighlightRule lineCommentHighlightRule;
  public final List<HighlightRule> highlightRules = new ArrayList<>();
  public HighlightRule stringHighlightRule;
  public HighlightRule blockCommentHighlightRule;
  public final ArrayList<HighlightRule> regexHighlightRules = new ArrayList<>();
  public final LinkedHashMap<Integer, List<HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  // --- URL underline (decoration, not syntax) ---
  public static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");
  public boolean isUrlUnderliningEnabled = false;
  @Nullable public Pattern urlUnderlinePattern = DEFAULT_URL_UNDERLINE_PATTERN;
  public final Paint urlUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final LinkedHashMap<Integer, List<UnderlineSpan>> urlUnderlineCache =
      new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };

  // --- Path underline (decoration, not syntax) ---
  public boolean isPathUnderliningEnabled = false;
  @Nullable public Pattern pathUnderlinePattern = Pattern.compile("/[^\\s,;()'\"]+");
  public final Paint pathUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final LinkedHashMap<Integer, List<UnderlineSpan>> pathUnderlineCache =
      new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };
  // Using ConcurrentHashMap for thread-safe access from IO and UI threads.
  public final java.util.concurrent.ConcurrentHashMap<String, Boolean> pathValidationCache =
      new java.util.concurrent.ConcurrentHashMap<>();
  public final java.util.Set<String> pendingPathValidations =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  // --- Error underline (squiggle) ---
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
  public final int[] visibleCharRangeTmp = new int[2];
  public int visibleCharPadding = 2;
  public boolean isPerformanceModeEnabled = false;
  public boolean isStableGlyphPositionsEnabled = false;

  // Zoom scroll adjustment for word wrap

  public static final String RULE_STRING = "__STRING__";
  public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";
  public static final String RULE_LINE_COMMENT = "__LINE_COMMENT__";

  // --- Auto-completion State ---
  public boolean isAutoCompletionEnabled = false;
  public boolean isAutoPathCompletionEnabled = false;
  public final Paint suggestionPaint = new Paint();
  public String activeSuggestion = null;
  public int activeSuggestionLine;
  public int activeSuggestionCharStart; // character index where the word fragment starts
  public String activeSuggestionWordFragment = ""; // the part user typed
  public boolean activeSuggestionIsPath = false;
  public final Trie suggestionTrie = new Trie();
  public final RectF activeSuggestionRect = new RectF(); // For tap-to-accept
  public boolean isSuggestionTextSizeCustom =
      false; // Flag to track if suggestion text size is custom
  public float suggestionTextSizeScale = 1f;

  // Debounce suggestion updates to avoid expensive per-keystroke parsing.
  public static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;
  public long lastSuggestionUpdateUptime = 0L;
  public boolean suggestionUpdateScheduled = false;
  public final Runnable suggestionUpdateRunnable =
      () -> {
        suggestionUpdateScheduled = false;
        lastSuggestionUpdateUptime = SystemClock.uptimeMillis();
        updateSuggestionInternal();
      };
  public boolean suggestionAcceptedThisTouch =
      false; // Flag to prevent GestureDetector interference
  public String lastPathQuery = null;
  public String lastPathSuggestion = null;
  public boolean isWordWrapEnabled = false;
  public int wrapWidthPx = -1;
  public final java.util.HashMap<Integer, int[]> wrapCache = new java.util.HashMap<>();
  public volatile int[] wrapLineCounts = null;
  public volatile int[] wrapLinePrefix = null;
  public volatile int wrapPrefixValidUpToLine = -1;
  public volatile int totalWrapVisualLines = 0;
  public volatile boolean wrapMetricsReady = false;
  public volatile int wrapMetricsWidth = -1;
  public final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  public volatile boolean wrapMetricsBuilding = false;
  public final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  public volatile boolean wrapSnapshotBuilding = false;
  public volatile int wrapSnapshotWidth = -1;
  public volatile int wrapSnapshotStart = -1;
  public volatile int wrapSnapshotSize = -1;
  public final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  public volatile boolean wrapPrefixBuilding = false;
  public volatile int wrapPrefixWidth = -1;
  public volatile int wrapPrefixTargetLine = -1;
  public boolean wrapPrefixRebuildPending = false;
  public boolean isWordWrapIndicatorEnabled = false;
  public static final String WORD_WRAP_INDICATOR_TEXT = "\u21A9"; // ↩
  public final Paint wordWrapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public float wordWrapIndicatorPadPx = 0f;
  public float wordWrapIndicatorWidth = 0f;
  public float wordWrapIndicatorTextScale = 0.85f;
  
  public static class TrieNode {
    final Map<Character, TrieNode> children = new java.util.TreeMap<>();
    String word = null;
  }

  public static class Trie {
    public final TrieNode root = new TrieNode();

    public void clear() {
      root.children.clear();
      root.word = null;
    }

    public void insert(String word) {
      if (word == null || word.isEmpty()) return;
      TrieNode current = root;
      for (char l : word.toCharArray()) {
        current = current.children.computeIfAbsent(l, c -> new TrieNode());
      }
      current.word = word;
    }

    public String findFirstSuggestion(String prefix) {
      if (prefix == null || prefix.isEmpty()) return null;
      TrieNode current = root;
      for (char l : prefix.toCharArray()) {
        TrieNode node = current.children.get(l);
        if (node == null) {
          return null;
        }
        current = node;
      }
      String suggestion = findFirstWordFromNode(current);
      // Don't suggest the exact word the user has already typed.
      if (suggestion != null && suggestion.equals(prefix)) {
        return null;
      }
      return suggestion;
    }

    public String findFirstWordFromNode(TrieNode node) {
      if (node == null) return null;
      // Traverse to the first word available from this node.
      if (node.word != null) {
        return node.word;
      }
      // Using TreeMap in TrieNode makes this loop alphabetically deterministic.
      for (TrieNode childNode : node.children.values()) {
        String found = findFirstWordFromNode(childNode);
        if (found != null) {
          return found;
        }
      }
      return null;
    }
  }



  public static class HighlightSpan {
    final int start;
    final int end;
    final Paint paint;

    HighlightSpan(int start, int end, Paint paint) {
      this.start = start;
      this.end = end;
      this.paint = paint;
    }
  }

  public static class UnderlineSpan {
    final int start;
    final int end;
    final boolean isPath; // true if it's a path, false if URL

    UnderlineSpan(int start, int end, boolean isPath) {
      this.start = start;
      this.end = end;
      this.isPath = isPath;
    }
  }

  public static class ErrorUnderlineSpan {
    final int start;
    final int end;

    ErrorUnderlineSpan(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  public static class LineParseResult {
    final List<HighlightSpan> spans;
    final boolean endsInBlockComment;
    final int endsInStringState;

    LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
      this.spans = spans;
      this.endsInBlockComment = endsInBlockComment;
      this.endsInStringState = endsInStringState;
    }
  }

  public static class HighlightLineState {
    final boolean inBlockComment;
    final int stringState;

    HighlightLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public static class BracketMatch {
    final int openLine;
    final int openChar;
    final int closeLine;
    final int closeChar;

    BracketMatch(int openLine, int openChar, int closeLine, int closeChar) {
      this.openLine = openLine;
      this.openChar = openChar;
      this.closeLine = closeLine;
      this.closeChar = closeChar;
    }
  }

  public static final class FoldRange {
    final int startLine;
    final int endLine;
    final int openCharIndex;
    final char openChar;
    final char closeChar;
    final boolean isBlockComment;
    final boolean isIndentFold;
    boolean collapsed;

    FoldRange(
        int startLine,
        int endLine,
        int openCharIndex,
        char openChar,
        char closeChar,
        boolean isBlockComment,
        boolean isIndentFold) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.openCharIndex = openCharIndex;
      this.openChar = openChar;
      this.closeChar = closeChar;
      this.isBlockComment = isBlockComment;
      this.isIndentFold = isIndentFold;
      this.collapsed = false;
    }
  }

  public static class BracketToken {
    final int line;
    final int ch;
    final char bracket;

    BracketToken(int line, int ch, char bracket) {
      this.line = line;
      this.ch = ch;
      this.bracket = bracket;
    }
  }

  public static class BracketGuideState {
    boolean inBlockComment;
    int stringState;
    final java.util.ArrayDeque<BracketGuideToken> stack = new java.util.ArrayDeque<>();

    BracketGuideState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  public static class BracketGuideToken {
    final int column;
    final float x;

    BracketGuideToken(int column, float x) {
      this.column = column;
      this.x = x;
    }
  }

  public enum HighlightRuleType {
    REGEX,
    STRING,
    BLOCK_COMMENT,
    LINE_COMMENT
  }

  public static class HighlightRule {
    final HighlightRuleType type;
    final Pattern pattern;
    final Paint paint;
    final int style;
    final boolean underline;

    HighlightRule(
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
        case STYLE_BOLD:
          typefaceStyle = Typeface.BOLD;
          break;
        case STYLE_ITALIC:
          typefaceStyle = Typeface.ITALIC;
          break;
        case STYLE_BOLD_ITALIC:
          typefaceStyle = Typeface.BOLD_ITALIC;
          break;
        default:
          typefaceStyle = Typeface.NORMAL;
          break;
      }

      this.paint.setTypeface(Typeface.create(baseTypeface, typefaceStyle));
      this.paint.setUnderlineText(underline);
    }

    void updateTextSize(float size) {
      paint.setTextSize(size);
    }

    void updateTypeface(Typeface baseTypeface) {
      int typefaceStyle;
      switch (style) {
        case STYLE_BOLD:
          typefaceStyle = Typeface.BOLD;
          break;
        case STYLE_ITALIC:
          typefaceStyle = Typeface.ITALIC;
          break;
        case STYLE_BOLD_ITALIC:
          typefaceStyle = Typeface.BOLD_ITALIC;
          break;
        default:
          typefaceStyle = Typeface.NORMAL;
          break;
      }
      paint.setTypeface(Typeface.create(baseTypeface, typefaceStyle));
    }
  }

  // --- Color Code Highlighting ---
  public boolean isColorHighlightingEnabled = false;
  public boolean isMultiLineStringsEnabled = false;
  public boolean isBacktickStringsEnabled = false;
  public boolean isBlockCommentsEnabled = false;
  public boolean isTripleQuoteStringsEnabled = false;
  public final Paint colorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public static final Pattern COLOR_HEX_PATTERN =
      Pattern.compile(
          "(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))\\b|(\\b0x[a-fA-F0-9]{6,8}\\b)",
          Pattern.CASE_INSENSITIVE);

  public final Runnable delayedWindowCheck =
      new Runnable() {
        @Override
        public void run() {
          checkAndLoadWindow();
        }
      };

  public SodiumEditor(Context ctx, @Nullable AttributeSet attrs) {
    super(ctx, attrs);
    paint.setTextSize(36);
    paint.setColor(0xFF000000);
    paint.setAntiAlias(true);
    paint.setSubpixelText(true);
    paint.setHinting(Paint.HINTING_ON);
    paint.setUnderlineText(false); // Explicitly disable underlines to fix visual artifact
    baseTypeface = (paint.getTypeface() != null) ? paint.getTypeface() : Typeface.DEFAULT;
    lineHeight = paint.getFontSpacing();
    baseHandleTextSizePx = paint.getTextSize();
    baseCursorTextSizePx = paint.getTextSize();
    baseBracketMatchTextSizePx = paint.getTextSize();
    baseBracketGuideTextSizePx = paint.getTextSize();
    baseIndentGuideTextSizePx = paint.getTextSize();
    bracketMatchPaint.setColor(cursorAndHandlesColor);
    bracketMatchPaint.setStyle(Paint.Style.STROKE);
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);
    bracketGuidePaint.setColor(0xFF888888);
    bracketGuidePaint.setStyle(Paint.Style.STROKE);
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);
    indentGuidePaint.setColor(0xFF555555);
    indentGuidePaint.setStyle(Paint.Style.STROKE);
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
    whitespaceGuidePaint.setColor(0xFF555555);
    whitespaceGuidePaint.setStyle(Paint.Style.FILL);
    whitespaceGuidePaint.setUnderlineText(false);
    whitespaceGuideDotPaint.setColor(0xFF555555);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    updateWhitespaceGuideMetrics();
    whitespaceStringRule =
        new HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            paint.getTextSize(),
            paint.getTypeface(),
            false,
            HighlightRuleType.STRING);
    whitespaceCommentRule =
        new HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            paint.getTextSize(),
            paint.getTypeface(),
            false,
            HighlightRuleType.BLOCK_COMMENT);

    selectionPaint.setStyle(Paint.Style.FILL);
    caretPaint.setStyle(Paint.Style.STROKE);
    caretPaint.setStrokeCap(Paint.Cap.BUTT);
    handlePaint.setStyle(Paint.Style.FILL);
    loadingCirclePaint.setStyle(Paint.Style.STROKE);
    loadingCirclePaint.setStrokeCap(Paint.Cap.ROUND);
lineNumber = new LineNumber(this);
    float density = getContext().getResources().getDisplayMetrics().density;
    lineNumber.currentLinePaint.setColor(currentLineHighlightColor);
    mCurrentSearchMatchPaint.setColor(mCurrentSearchMatchColor);
    foldPlaceholderCorner = 6f * density;
    foldPlaceholderPadX = 6f * density;
    foldPlaceholderPadY = 2f * density;
    foldMarkerSpacing = foldMarkerSpacing * density;
    foldMarkerEdgePadding = foldMarkerEdgePadding * density;

    foldPlaceholderPaint.setColor(0xFFE0E0E0);
    foldPlaceholderPaint.setStyle(Paint.Style.FILL);
    foldMarkerPaint.setColor(0xFF888888);
    foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    foldMarkerPaint.setTextSize(paint.getTextSize());
    foldRipplePaint.setStyle(Paint.Style.FILL);

    wordWrapIndicatorPadPx = 4f * density;
    wordWrapIndicatorPaint.setColor(0xFF9E9E9E);
    wordWrapIndicatorPaint.setAlpha(180);
    wordWrapIndicatorPaint.setTextAlign(Paint.Align.LEFT);
    wordWrapIndicatorPaint.setTextSize(paint.getTextSize() * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(paint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);

    touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();


    // Initialize Scroll
    scroll = new Scroll(this);
    // Sync scroll configuration to Scroll

    // Initialize Zoom
    zoom = new Zoom(this);
    scaleGestureDetector = new ScaleGestureDetector(ctx, zoom.createScaleListener());

    // Initialize Ime
    ime = new Ime(this);

    // Initialize OnTouch
    onTouch = new OnTouch(this);

    // Initialize OnScroll
    onScroll = new OnScroll(this);
    scroll.gestureDetector = onScroll.getGestureDetector();

    // Initialize Popup
    popup = new Popup(this);
    

    // Initialize CursorAnimation
    cursorAnimation = new CursorAnimation(this);

    // Initialize CharAnimation
    charAnimation = new CharAnimation(this);

    // Initialize Cursor management
    cursor = new Cursor(this);
    caret = new Caret(this, cursor);
    cursorHandle = new CursorHandle(this, cursor, caret);
    selection = new Selection(this, cursor);
    selectionHandles = new SelectionHandles(this, selection);

    lineWidthCache =
        new LinkedHashMap<Integer, Float>(lineWidthCacheSize, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
            return size() > lineWidthCacheSize;
          }
        };

    ioThread = new HandlerThread("PopEditIO");
    ioThread.start();
    ioHandler = new Handler(ioThread.getLooper());

    setFocusable(true);
    setFocusableInTouchMode(true);

    getViewTreeObserver()
        .addOnGlobalLayoutListener(
            () -> {
              int newKeyboardHeight = 0;
              WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
              if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                int windowHeight = getRootView().getHeight();
                int imeTop = windowHeight - imeBottom;
                getLocationInWindow(tmpLocationInWindow);
                int viewBottom = tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - imeTop);
                newKeyboardHeight = Math.min(overlap, getHeight());
              } else {
                getWindowVisibleDisplayFrame(visibleDisplayFrame);
                getLocationInWindow(tmpLocationInWindow);
                int viewBottom = tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - visibleDisplayFrame.bottom);
                newKeyboardHeight = overlap;
              }

              if (newKeyboardHeight != keyboardHeight) {
                keyboardHeight = newKeyboardHeight;
                post(this::keepCursorVisibleHorizontally);
              }
            });

    // Initialize suggestion paint
    suggestionPaint.set(paint);
    suggestionPaint.setColor(0xFFAAAAAA); // Default faint gray
    suggestionPaint.setAntiAlias(true);
    suggestionPaint.setSubpixelText(true);
    suggestionPaint.setHinting(Paint.HINTING_ON);
    isSuggestionTextSizeCustom = false; // By default, suggestion size follows main text
    suggestionTextSizeScale = 1f;

    setPathUnderliningEnabled(true); // Enable path underlining by default
  }

  // --- Public APIs for Auto Completion ---

  public void setAutoCompletionEnabled(boolean enabled) {
    this.isAutoCompletionEnabled = enabled;
    if (!enabled && (!activeSuggestionIsPath || !isAutoPathCompletionEnabled)) {
      clearActiveSuggestion();
    }
    invalidate();
  }

  public void setAutoPathCompletionEnabled(boolean enabled) {
    this.isAutoPathCompletionEnabled = enabled;
    if (!enabled && (activeSuggestionIsPath || !isAutoCompletionEnabled)) {
      clearActiveSuggestion();
    }
    invalidate();
  }

  

  public void setBinarySafeRenderingEnabled(boolean enabled) {
    if (binarySafeRenderingEnabled == enabled) return;
    binarySafeRenderingEnabled = enabled;
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
    invalidate();
  }

  
  public void setHighlightCurrentSearchMatchEnabled(boolean enabled) {
    if (mHighlightCurrentSearchMatch == enabled) return;
    mHighlightCurrentSearchMatch = enabled;
    invalidate();
  }

  public void setCurrentSearchMatchColor(int color) {
    if (mCurrentSearchMatchColor == color) return;
    mCurrentSearchMatchColor = color;
    mCurrentSearchMatchPaint.setColor(color);
    if (mHighlightCurrentSearchMatch) {
      invalidate();
    }
  }

  public void setWordWrapEnabled(boolean enabled) {
    if (this.isWordWrapEnabled == enabled) return;
    this.isWordWrapEnabled = enabled;
    invalidateWrapMetrics();
    if (enabled) {
      scroll.scrollX =0f;
      scroll.clampScrollX();
      clearStreamedLineCaches();
      reloadWindowAroundVisible(false);
    }
    requestLayout();
    invalidate();
  }

  public void setWordWrapIndicatorEnabled(boolean enabled) {
    if (this.isWordWrapIndicatorEnabled == enabled) return;
    this.isWordWrapIndicatorEnabled = enabled;
    invalidate();
  }

  public void setWordWrapIndicatorColor(int color) {
    wordWrapIndicatorPaint.setColor(color);
    invalidate();
  }

  public void setVisibleCharPadding(int paddingChars) {
    int safe = Math.max(0, paddingChars);
    if (visibleCharPadding == safe) return;
    visibleCharPadding = safe;
    invalidate();
  }

  public void setStableGlyphPositionsEnabled(boolean enabled) {
    if (this.isStableGlyphPositionsEnabled == enabled) return;
    this.isStableGlyphPositionsEnabled = enabled;
    invalidate();
  }

  public void setPerformanceModeEnabled(boolean enabled) {
    if (this.isPerformanceModeEnabled == enabled) return;
    this.isPerformanceModeEnabled = enabled;
    if (enabled) {
      setUrlUnderliningEnabled(false);
      setPathUnderliningEnabled(false);
      isColorHighlightingEnabled = false;
      setBracketMatchingEnabled(false);
      setBracketGuidesEnabled(false);
      setIndentGuidesEnabled(false);
      setWhitespaceGuidesEnabled(false);
      setWordWrapIndicatorEnabled(false);
      setAutoCompletionEnabled(false);
      setAutoPathCompletionEnabled(false);
      charAnimation.setCharAnimation(false, charAnimation.charAnimationDurationMs);
      setHighlightCurrentLine(false);
      setIndentationBlocksEnabled(false);
      setCodeFoldingEnabled(false);
    }
    invalidate();
  }

  public void setWordWrapIndicatorTextSize(float sizeSp) {
    if (sizeSp <= 0f) return;
    float px = spToPx(sizeSp);
    float base = paint.getTextSize();
    if (base > 0f) {
      wordWrapIndicatorTextScale = px / base;
    } else {
      wordWrapIndicatorTextScale = 0.85f;
    }
    wordWrapIndicatorPaint.setTextSize(base * wordWrapIndicatorTextScale);
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
    invalidate();
  }

  public void setSuggestions(List<String> keywords, int color) {
    suggestionTrie.clear();
    if (keywords != null) {
      for (String word : keywords) {
        suggestionTrie.insert(word);
      }
    }
    // Only set the color. Size and style are synced automatically.
    suggestionPaint.setColor(color);
    clearActiveSuggestion();
  }

  public void acceptAutoCompletion() {
    Log.d("SodiumEditor", "acceptAutoCompletion: Entered.");
    if (activeSuggestion == null) {
      Log.d("SodiumEditor", "acceptAutoCompletion: Bailed out (disabled or no active suggestion).");
      return;
    }
    if (activeSuggestionIsPath && !isAutoPathCompletionEnabled) {
      Log.d("SodiumEditor", "acceptAutoCompletion: Bailed out (path disabled).");
      return;
    }
    if (!activeSuggestionIsPath && !isAutoCompletionEnabled) {
      Log.d("SodiumEditor", "acceptAutoCompletion: Bailed out (word disabled).");
      return;
    }

    commitComposing(false);

    // Set a flag to ignore subsequent gesture events from this touch sequence.
    suggestionAcceptedThisTouch = true;

    String textToInsert = activeSuggestion;
    clearActiveSuggestion();
    selection.hasSelection = false; // Clear selection after accepting suggestion
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    Log.d("SodiumEditor", "acceptAutoCompletion: Cleared selection flags, inserting text.");
    insertStringAtCursor(textToInsert);
    Log.d("SodiumEditor", "acceptAutoCompletion: Text inserted.");

    restartInput(); // Force IME to resync

    // The flag will be reset by the next onDown event.
  }

  public void setSuggestionTextSize(float size) {
    isSuggestionTextSizeCustom = true;
    float px = spToPx(size);
    float base = paint.getTextSize();
    if (base > 0f) {
      suggestionTextSizeScale = px / base;
    } else {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(base * suggestionTextSizeScale);
    invalidate();
  }

  // --- Core Logic for Auto Completion ---

  public void clearActiveSuggestion() {
    if (activeSuggestion != null) {
      activeSuggestion = null;
      activeSuggestionRect.setEmpty();
      activeSuggestionIsPath = false;
      invalidate();
    }
  }

  public void updateSuggestion() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      post(this::updateSuggestion);
      return;
    }
    updateImeSelection();
    long now = SystemClock.uptimeMillis();
    if (now - lastSuggestionUpdateUptime < SUGGESTION_UPDATE_DEBOUNCE_MS) {
      if (!suggestionUpdateScheduled) {
        suggestionUpdateScheduled = true;
        mainHandler.postDelayed(suggestionUpdateRunnable, SUGGESTION_UPDATE_DEBOUNCE_MS);
      }
      return;
    }
    lastSuggestionUpdateUptime = now;
    updateSuggestionInternal();
  }

  public void updateSuggestionInternal() {
    String line = getLineTextForRender(cursor.cursorLine);
    if (line == null) {
      clearActiveSuggestion();
      return;
    }

    if (!isAutoPathCompletionEnabled && !isAutoCompletionEnabled) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if the cursor is in the middle of a word
    if (cursor.cursorChar < line.length() && Character.isLetterOrDigit(line.charAt(cursor.cursorChar))) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if there is non-whitespace text after the cursor
    if (cursor.cursorChar < line.length() && !line.substring(cursor.cursorChar).trim().isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    String pathFragment = "";
    String wordFragment = "";
    if (isAutoPathCompletionEnabled) {
      pathFragment = getCurrentPathFragment();
    }
    if (isAutoCompletionEnabled && pathFragment.isEmpty()) {
      wordFragment = getCurrentWordFragment();
    }
    if (pathFragment.isEmpty() && wordFragment.isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    // Prevent suggestions inside syntax highlighting (expensive).
    List<HighlightSpan> spans = highlightCache.get(cursor.cursorLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, cursor.cursorLine);
      highlightCache.put(cursor.cursorLine, spans);
    }
    for (HighlightSpan span : spans) {
      if (cursor.cursorChar > span.start && cursor.cursorChar <= span.end) {
        clearActiveSuggestion();
        return;
      }
    }

    if (!pathFragment.isEmpty()) {
      String suggestion = findPathSuggestion(pathFragment);
      if (suggestion != null && suggestion.length() > pathFragment.length()) {
        activeSuggestion = suggestion.substring(pathFragment.length());
        activeSuggestionLine = cursor.cursorLine;
        activeSuggestionCharStart = cursor.cursorChar - pathFragment.length();
        activeSuggestionWordFragment = pathFragment;
        activeSuggestionIsPath = true;
      } else {
        clearActiveSuggestion();
      }
      invalidate();
      return;
    }

    if (wordFragment.isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    String suggestion = suggestionTrie.findFirstSuggestion(wordFragment);
    if (suggestion != null && suggestion.length() > wordFragment.length()) {
      activeSuggestion = suggestion.substring(wordFragment.length());
      activeSuggestionLine = cursor.cursorLine;
      activeSuggestionCharStart = cursor.cursorChar - wordFragment.length();
      activeSuggestionWordFragment = wordFragment;
      activeSuggestionIsPath = false;
    } else {
      clearActiveSuggestion();
    }
    invalidate();
  }

  public String getCurrentWordFragment() {
    String line = getLineTextForRender(cursor.cursorLine);
    if (cursor.cursorChar == 0 || cursor.cursorChar > line.length()) {
      return "";
    }
    int start = cursor.cursorChar;
    // A word character is a letter or a digit.
    while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
      start--;
    }
    return line.substring(start, cursor.cursorChar);
  }

  public String getCurrentPathFragment() {
    String line = getLineTextForRender(cursor.cursorLine);
    if (cursor.cursorChar == 0 || cursor.cursorChar > line.length()) {
      return "";
    }
    int start = cursor.cursorChar;
    while (start > 0 && isPathChar(line.charAt(start - 1))) {
      start--;
    }
    String fragment = line.substring(start, cursor.cursorChar);
    if (fragment.isEmpty()) return "";
    if (fragment.startsWith("/")
        || fragment.startsWith("~")
        || fragment.startsWith("./")
        || fragment.startsWith("../")
        || fragment.contains("/")) {
      return fragment;
    }
    return "";
  }

  public boolean isPathChar(char c) {
    return Character.isLetterOrDigit(c) || c == '/' || c == '.' || c == '_' || c == '-' || c == '~';
  }

  @Nullable
  public String findPathSuggestion(String fragment) {
    if (fragment.equals(lastPathQuery)) {
      return lastPathSuggestion;
    }

    String expanded = fragment;
    String home = getHomeDir();
    if (fragment.startsWith("~") && home != null) {
      if (fragment.equals("~")) {
        expanded = home;
      } else if (fragment.startsWith("~/")) {
        expanded = home + fragment.substring(1);
      }
    }

    int lastSlash = expanded.lastIndexOf('/');
    String dirPart = lastSlash >= 0 ? expanded.substring(0, lastSlash) : "";
    String prefix = lastSlash >= 0 ? expanded.substring(lastSlash + 1) : expanded;
    File dir = resolveBaseDir(expanded, fragment, dirPart, home);
    if (dir == null || !dir.exists() || !dir.isDirectory()) {
      lastPathQuery = fragment;
      lastPathSuggestion = null;
      return null;
    }

    File[] entries = dir.listFiles();
    if (entries == null || entries.length == 0) {
      lastPathQuery = fragment;
      lastPathSuggestion = null;
      return null;
    }

    List<String> matches = new ArrayList<>();
    boolean allowHidden = prefix.startsWith(".");
    for (File entry : entries) {
      String name = entry.getName();
      if (!allowHidden && name.startsWith(".")) continue;
      if (name.startsWith(prefix)) {
        matches.add(entry.isDirectory() ? name + "/" : name);
      }
    }
    String suggestion = null;
    if (!matches.isEmpty()) {
      Collections.sort(matches);
      String chosen = chooseClosestPrefix(matches);
      suggestion = fragment + chosen.substring(prefix.length());
    } else {
      String fallback = chooseClosestByCommonPrefix(entries, prefix, allowHidden);
      if (fallback != null) {
        suggestion = fragment + fallback;
      }
    }

    lastPathQuery = fragment;
    lastPathSuggestion = suggestion;
    return suggestion;
  }

  @Nullable
  public File resolveBaseDir(
      String expanded, String fragment, String dirPart, @Nullable String home) {
    if (expanded.startsWith("/")) {
      return new File(dirPart.isEmpty() ? "/" : dirPart);
    }
    if (fragment.startsWith("~") && home != null) {
      return new File(dirPart.isEmpty() ? home : dirPart);
    }
    File base = getDefaultBaseDir();
    if (base == null) return null;
    return dirPart.isEmpty() ? base : new File(base, dirPart);
  }

  @Nullable
  public File getDefaultBaseDir() {
    if (sourceFile != null) {
      File parent = sourceFile.getParentFile();
      if (parent != null) return parent;
    }
    String home = getHomeDir();
    if (home != null) return new File(home);
    return new File("/");
  }

  @Nullable
  public String getHomeDir() {
    String home = System.getenv("HOME");
    if (home == null || home.isEmpty()) {
      home = System.getProperty("user.home");
    }
    return (home == null || home.isEmpty()) ? null : home;
  }

  public String chooseClosestPrefix(List<String> matches) {
    String best = matches.get(0);
    for (String name : matches) {
      if (name.length() < best.length()) {
        best = name;
      } else if (name.length() == best.length() && name.compareTo(best) < 0) {
        best = name;
      }
    }
    return best;
  }

  @Nullable
  public String chooseClosestByCommonPrefix(File[] entries, String prefix, boolean allowHidden) {
    int bestScore = -1;
    String bestName = null;
    for (File entry : entries) {
      String name = entry.getName();
      if (!allowHidden && name.startsWith(".")) continue;
      int score = commonPrefixLength(prefix, name);
      if (score > bestScore) {
        bestScore = score;
        bestName = entry.isDirectory() ? name + "/" : name;
      } else if (score == bestScore && bestName != null && name.compareTo(bestName) < 0) {
        bestName = entry.isDirectory() ? name + "/" : name;
      }
    }
    return (bestScore <= 0) ? null : bestName;
  }

  public int commonPrefixLength(String a, String b) {
    int len = Math.min(a.length(), b.length());
    int i = 0;
    while (i < len && a.charAt(i) == b.charAt(i)) i++;
    return i;
  }

  public void insertStringAtCursor(String text) {
    if (isReadOnly) return;
    if (text == null || text.isEmpty()) return;
    if (selection.hasSelection) {
      replaceSelectionWithText(text);
      return;
    }
    if (text.contains("\n")) { // Not handled for simplicity, suggestions shouldn't have newlines.
      for (char c : text.toCharArray()) insertCharAtCursor(c);
      return;
    }
    invalidatePendingIOForEdit();
    editVersion.incrementAndGet();

    ensureLineInWindow(cursor.cursorLine, true);
    if (isWindowLoading
        && (cursor.cursorLine < windowStartLine || cursor.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertStringAtCursor(text));
      return;
    }

    int localIdx = cursor.cursorLine - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(cursor.cursorChar, base.length()));
      String modified = base.substring(0, pos) + text + base.substring(pos);
      updateLocalLine(localIdx, modified);
      modifiedLines.put(cursor.cursorLine, modified);
      invalidateHighlightCacheForLine(cursor.cursorLine);
      cursor.cursorChar += text.length();
      computeWidthForLine(cursor.cursorLine, modified);
      recalculateMaxLineWidth();
      keepCursorVisibleHorizontally();
      invalidate();
    }
  }

  public void validatePathInBackground(final String path, final int lineToInvalidate) {
    // Avoid queueing the same path if it's already being checked
    if (pendingPathValidations.contains(path)) {
      return;
    }
    pendingPathValidations.add(path);

    ioHandler.post(
        () -> {
          boolean exists = false;
          try {
            // Basic check, might need to be more robust for Android storage frameworks
            File file = new File(path);
            exists = file.exists();
          } catch (Exception e) {
            // Ignore security exceptions or other errors
          } finally {
            pathValidationCache.put(path, exists);
            pendingPathValidations.remove(path);

            if (exists) {
              // Invalidate caches for the line and trigger a redraw
              mainHandler.post(
                  () -> {
                    pathUnderlineCache.remove(lineToInvalidate);

                    // To be safe and simple, just invalidate the whole view.
                    // This ensures the line gets redrawn even if it has moved.
                    invalidate();
                  });
            }
          }
        });
  }

  public void setEditorBackgroundColor(int color) {
    hasEditorBackgroundColor = true;
    editorBackgroundColor = color;
    invalidate();
  }

  public void clearEditorBackgroundColor() {
    if (!hasEditorBackgroundColor) return;
    hasEditorBackgroundColor = false;
    invalidate();
  }

  public void setEditorBackgroundImageFromAssets(String assetPath) {
    if (assetPath == null) return;
    try (InputStream input = getContext().getAssets().open(assetPath)) {
      Bitmap bmp = BitmapFactory.decodeStream(input);
      if (bmp != null) {
        setEditorBackgroundBitmap(bmp);
      }
    } catch (Exception e) {
      Log.e("SodiumEditor", "setEditorBackgroundImageFromAssets failed: " + assetPath, e);
    }
  }

  public void setEditorBackgroundImageFromFile(String filePath) {
    if (filePath == null) return;
    try {
      Bitmap bmp = BitmapFactory.decodeFile(filePath);
      if (bmp != null) {
        setEditorBackgroundBitmap(bmp);
      }
    } catch (Exception e) {
      Log.e("SodiumEditor", "setEditorBackgroundImageFromFile failed: " + filePath, e);
    }
  }

  public void clearEditorBackgroundImage() {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = null;
    invalidate();
  }

  public void setEditorBackgroundBitmap(Bitmap bitmap) {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = bitmap;
    invalidate();
  }

  public void setSelectionHighlightColor(int color) {
    if (this.selectionHighlightColor == color) return;
    this.selectionHighlightColor = color;
    if (selection.hasSelection) invalidate();
  }

  public void setSelectionColor(int color) {
    setSelectionHighlightColor(color);
  }

  public void setCursorAndHandlesColor(int color) {
    if (this.cursorAndHandlesColor == color) return;
    this.cursorAndHandlesColor = color;
    this.caretColor = color;
    this.cursorHandleColor = color;
    this.selectionHandleColor = color;
    invalidate();
  }

  public void setCaretColor(int color) {
    if (this.caretColor == color) return;
    this.caretColor = color;
    invalidate();
  }

  public void setCursorHandleColor(int color) {
    if (this.cursorHandleColor == color) return;
    this.cursorHandleColor = color;
    invalidate();
  }

  public void setSelectionHandleColor(int color) {
    if (this.selectionHandleColor == color) return;
    this.selectionHandleColor = color;
    invalidate();
  }

  public void setSearchQuery(
      String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
    String safe = (query == null) ? "" : query;
    if (safe.equals(searchQuery)
        && searchUseRegex == useRegex
        && searchCaseSensitive == caseSensitive
        && searchWrap == wrapAround) {
      return;
    }
    searchQuery = safe;
    searchUseRegex = useRegex;
    searchCaseSensitive = caseSensitive;
    searchWrap = wrapAround;
    searchPattern = null;
    if (searchUseRegex && !searchQuery.isEmpty()) {
      int flags = Pattern.MULTILINE;
      if (!searchCaseSensitive) flags |= (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      try {
        searchPattern = Pattern.compile(searchQuery, flags);
      } catch (Exception ignored) {
        searchPattern = null;
      }
    }
    clearSearchMatchCache();
    invalidate();
  }

  public void setSearchHighlightEnabled(boolean enabled) {
    if (searchHighlightEnabled == enabled) return;
    searchHighlightEnabled = enabled;
    invalidate();
  }

  public void setSearchHighlightColor(int color) {
    searchHighlightColor = color;
    searchHighlightPaint.setColor(color);
    invalidate();
  }

  public boolean goToNextSearchMatch() {
    return goToSearchMatch(true);
  }

  public boolean goToPrevSearchMatch() {
    return goToSearchMatch(false);
  }

  public boolean selectNextSearchMatch() {
    return selectSearchMatch(true);
  }

  public boolean selectPrevSearchMatch() {
    return selectSearchMatch(false);
  }

  public boolean selectNextSearchMatchInclusive() {
    return selectSearchMatchInclusive(true);
  }

  public boolean selectPrevSearchMatchInclusive() {
    return selectSearchMatchInclusive(false);
  }

  public boolean selectSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, cursor.cursorLine);
    int startChar = Math.max(0, cursor.cursorChar);

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    ensureLineInWindow(match.line, true);
    setSelectionInternal(match.line, match.start, match.line, match.end);
    setCursorPositionNoClear(match.line, match.end);
    return true;
  }

  public boolean selectSearchMatchInclusive(boolean forward) {
    if (!isSearchActive()) return false;
    int total = getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, cursor.cursorLine);
    int startChar = Math.max(0, cursor.cursorChar);
    if (forward) {
      startChar = Math.max(-1, startChar - 1);
    } else {
      startChar = startChar + 1;
    }

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    ensureLineInWindow(match.line, true);
    setSelectionInternal(match.line, match.start, match.line, match.end);
    setCursorPositionNoClear(match.line, match.end);
    return true;
  }

  public boolean selectSearchMatchAtCursorOrNext() {
    SearchMatch atCursor = findSearchMatchAtCursor();
    if (atCursor != null) {
      ensureLineInWindow(atCursor.line, true);
      setSelectionInternal(atCursor.line, atCursor.start, atCursor.line, atCursor.end);
      setCursorPositionNoClear(atCursor.line, atCursor.end);
      return true;
    }
    return selectSearchMatchInclusive(true);
  }

  @Nullable
  public SearchMatch findSearchMatchAtCursor() {
    if (!isSearchActive()) return null;
    int line = Math.max(0, cursor.cursorLine);
    String lineText = getLineTextForRender(line);
    if (lineText == null) lineText = "";
    if (lineText.isEmpty()) return null;

    if (searchUseRegex) {
      if (searchPattern == null) return null;
      try {
        Matcher matcher = searchPattern.matcher(lineText);
        while (matcher.find()) {
          int s = matcher.start();
          int e = matcher.end();
          if (s <= cursor.cursorChar && cursor.cursorChar < e) {
            return new SearchMatch(line, s, e);
          }
        }
      } catch (Exception ignored) {
        return null;
      }
      return null;
    }

    String needle = searchQuery == null ? "" : searchQuery;
    if (needle.isEmpty()) return null;
    String haystack = lineText;
    if (!searchCaseSensitive) {
      haystack = haystack.toLowerCase(java.util.Locale.ROOT);
      needle = needle.toLowerCase(java.util.Locale.ROOT);
    }
    int idx = 0;
    while (true) {
      idx = haystack.indexOf(needle, idx);
      if (idx < 0) return null;
      int end = idx + needle.length();
      if (idx <= cursor.cursorChar && cursor.cursorChar < end) {
        return new SearchMatch(line, idx, end);
      }
      idx = idx + 1;
    }
  }

  public void setCursorPositionNoClear(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    cursor.cursorLine = targetLine;
    if (cursor.cursorLine >= windowStartLine && cursor.cursorLine < windowStartLine + linesWindow.size()) {
      String lineText = getLineTextForRender(cursor.cursorLine);
      cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursor.cursorChar = targetCol;
    }
    caret.resetBlink();
    keepCursorVisibleHorizontally();
    invalidate();
    updateImeSelection();
  }

  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }

  public void setBracketMatchingEnabled(boolean enabled) {
    if (this.isBracketMatchingEnabled == enabled) return;
    this.isBracketMatchingEnabled = enabled;
    if (!enabled) {
      cachedBracketMatch = null;
      cachedBracketMatchCursorLine = -1;
      cachedBracketMatchCursorChar = -1;
      cachedBracketMatchEditVersion = -1;
    }
    invalidate();
  }

  public void setBracketMatchColor(int color) {
    bracketMatchPaint.setColor(color);
    invalidate();
  }

  public void setBracketMatchStrokeWidth(float width) {
    if (this.bracketMatchStrokeWidth == width) return;
    this.baseBracketMatchStrokeWidth = width;
    this.baseBracketMatchTextSizePx = paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setBracketGuidesEnabled(boolean enabled) {
    if (this.isBracketGuidesEnabled == enabled) return;
    this.isBracketGuidesEnabled = enabled;
    invalidateBracketGuideCache();
    invalidate();
  }

  public void setBracketGuidesColor(int color) {
    bracketGuidePaint.setColor(color);
    invalidate();
  }

  public void setBracketGuidesStrokeWidth(float width) {
    if (this.bracketGuideStrokeWidth == width) return;
    this.baseBracketGuideStrokeWidth = width;
    this.baseBracketGuideTextSizePx = paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setIndentGuidesEnabled(boolean enabled) {
    if (this.isIndentGuidesEnabled == enabled) return;
    this.isIndentGuidesEnabled = enabled;
    invalidate();
  }

  public void setIndentGuidesColor(int color) {
    indentGuidePaint.setColor(color);
    invalidate();
  }

  public void setIndentGuidesStrokeWidth(float width) {
    if (this.indentGuideStrokeWidth == width) return;
    this.baseIndentGuideStrokeWidth = width;
    this.baseIndentGuideTextSizePx = paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setWhitespaceGuidesEnabled(boolean enabled) {
    if (this.isWhitespaceGuidesEnabled == enabled) return;
    this.isWhitespaceGuidesEnabled = enabled;
    invalidateBracketGuideCache();
    invalidateHighlightEnsureRange();
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    recalculateMaxLineWidth();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    invalidate();
  }

  public void setWhitespaceGuidesColor(int color) {
    whitespaceGuidePaint.setColor(color);
    whitespaceGuideDotPaint.setColor(color);
    if (isWhitespaceGuidesEnabled) invalidate();
  }

  public void setWhitespaceGuidesSpaceStep(int spacesPerDot) {
    int safeStep = Math.max(1, spacesPerDot);
    if (whitespaceGuideSpaceStep == safeStep) return;
    whitespaceGuideSpaceStep = safeStep;
    invalidateBracketGuideCache();
    invalidateHighlightEnsureRange();
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    recalculateMaxLineWidth();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    if (isWhitespaceGuidesEnabled) invalidate();
  }

  public void setFileCharset(@Nullable Charset charset) {
    Charset safe = (charset == null) ? StandardCharsets.UTF_8 : charset;
    if (safe.equals(fileCharset)) return;
    fileCharset = safe;
    if (readerForFile != null) {
      try {
        readerForFile.close();
      } catch (Exception ignored) {
      }
      readerForFile = null;
    }
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
    invalidate();
  }

  public void setFileEncoding(@Nullable String charsetName) {
    Charset cs = StandardCharsets.UTF_8;
    if (charsetName != null) {
      try {
        cs = Charset.forName(charsetName.trim());
      } catch (Exception ignored) {
      }
    }
    setFileCharset(cs);
  }

  public void setMaxSyntaxLineLength(int maxChars) {
    int safe = Math.max(512, maxChars);
    if (maxSyntaxLineLength == safe) return;
    maxSyntaxLineLength = safe;
    clearHighlightCaches();
    invalidate();
  }

  public void setPrefetchCols(int cols) {
    int safe = Math.max(0, cols);
    if (prefetchCols == safe) return;
    prefetchCols = safe;
    invalidate();
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
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setPrefetchLines(int lines) {
    int safe = Math.max(0, lines);
    if (prefetchLines == safe) return;
    prefetchLines = safe;
    int minWindow = computeMinWindowSize();
    if (windowSize < minWindow) windowSize = minWindow;
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
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
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public int computeMinWindowSize() {
    return computeMinWindowSizeForPrefetch(prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    if (lineHeight <= 0f || getHeight() <= 0) return 10;
    float effectiveHeight = (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(effectiveHeight / lineHeight) + 2);
    int minTotal = Math.max(visibleLines * 2, visibleLines + 6);
    int minWindow = minTotal - (Math.max(0, prefetch) * 2);
    return Math.max(10, minWindow);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    if (getWidth() == 0 || getHeight() == 0) {
      invalidate();
      return;
    }
    int firstVisibleLine = Math.max(0, getGlobalLineForY( scroll.scrollY));
    int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
    loadWindowAround(targetStart, null, recalcWidthSync);
  }

  public void setCursorWidth(float width) {
    if (this.baseCursorWidthPx == width && this.baseCursorTextSizePx == paint.getTextSize()) return;
    this.baseCursorWidthPx = width;
    this.baseCursorTextSizePx = paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setHighlightCurrentLine(boolean enabled) {
    if (this.highlightCurrentLine == enabled) return;
    this.highlightCurrentLine = enabled;
    invalidate();
  }

  public void setClickAfterEndToAddLineEnabled(boolean enabled) {
    this.isClickAfterEndToAddLineEnabled = enabled;
  }

  public void setAutoPairingEnabled(boolean enabled) {
    this.isAutoPairingEnabled = enabled;
  }

  public void setAutoBracketNewlineEnabled(boolean enabled) {
    this.isAutoBracketNewlineEnabled = enabled;
  }

  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    this.isAutoBracketNewlineIndentEnabled = enabled;
  }

  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    this.isAutoIndentAfterClosingBracketEnabled = enabled;
  }

  public void setIndentationBlocksEnabled(boolean enabled) {
    if (this.isIndentationBlocksEnabled == enabled) return;
    this.isIndentationBlocksEnabled = enabled;
    if (!enabled) {
      foldRanges.entrySet().removeIf(e -> e.getValue().isIndentFold);
    }
    indentGuideIntervalsDirty = true;
    foldIntervalsDirty = true;
    invalidate();
  }

  public void setCodeFoldingEnabled(boolean enabled) {
    if (this.isCodeFoldingEnabled == enabled) return;
    this.isCodeFoldingEnabled = enabled;
    lineNumber.invalidateLineNumberCache();
    if (!enabled) {
      foldRanges.clear();
      clearFoldRipple();
    }
    indentGuideIntervalsDirty = true;
    foldIntervalsDirty = true;
    invalidate();
  }

  public void setFoldPlaceholderColor(int color) {
    foldPlaceholderPaint.setColor(color);
    if (isCodeFoldingEnabled) invalidate();
  }

  public void setFoldMarkerColor(int color) {
    foldMarkerPaint.setColor(color);
    if (isCodeFoldingEnabled) invalidate();
  }

  public void setFoldMarkerTextSize(float size) {
    float base = paint.getTextSize();
    if (base <= 0f) return;
    foldMarkerTextScale = size / base;
    foldMarkerPaint.setTextSize(base * foldMarkerTextScale);
    requestLayout();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    invalidate();
  }

  public void setCurrentLineHighlightColor(int color) {
    this.currentLineHighlightColor = color;
    this.lineNumber.currentLinePaint.setColor(color);
    if (highlightCurrentLine) invalidate();
  }

  public void addHighlightRule(String regex, int style, int color) {
    addHighlightRule(regex, style, color, false);
  }

  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    HighlightRuleType type = HighlightRuleType.REGEX;
    if (RULE_STRING.equals(regex)) {
      type = HighlightRuleType.STRING;
    } else if (RULE_BLOCK_COMMENT.equals(regex)) {
      type = HighlightRuleType.BLOCK_COMMENT;
    } else if (isLineCommentRegex(regex)) {
      type = HighlightRuleType.LINE_COMMENT;
    }

    HighlightRule rule =
        new HighlightRule(
            regex, style, color, paint.getTextSize(), paint.getTypeface(), underline, type);
    if (type == HighlightRuleType.LINE_COMMENT) {
      ensureLineCommentDelimiter("//");
      lineCommentHighlightRule = rule;
    } else {
      highlightRules.add(rule);
      if (type == HighlightRuleType.STRING) {
        stringHighlightRule = rule;
      } else if (type == HighlightRuleType.BLOCK_COMMENT) {
        blockCommentHighlightRule = rule;
      } else {
        regexHighlightRules.add(rule);
      }
    }
    clearHighlightCaches();
    invalidate();
  }

  public void clearHighlightRules() {
    highlightRules.clear();
    stringHighlightRule = null;
    blockCommentHighlightRule = null;
    regexHighlightRules.clear();
    lineCommentHighlightRule = null;
    clearHighlightCaches();
    invalidate();
  }

  public void clearHighlightCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.clear();
    urlUnderlineCache.clear();
    pathUnderlineCache.clear();
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
  }

  public void invalidateHighlightCacheForLine(int line) {
    highlightCache.remove(line);
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.remove(line);
    urlUnderlineCache.remove(line);
    pathUnderlineCache.remove(line);
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
  }

  public void setUrlUnderliningEnabled(boolean enabled) {
    if (this.isUrlUnderliningEnabled == enabled) return;
    this.isUrlUnderliningEnabled = enabled;
    urlUnderlineCache.clear();
    invalidate();
  }

  public void setUrlUnderliningRegex(@Nullable String regex) {
    if (regex == null || regex.trim().isEmpty()) {
      this.urlUnderlinePattern = null;
    } else {
      this.urlUnderlinePattern = Pattern.compile(regex);
    }
    urlUnderlineCache.clear();
    invalidate();
  }

  public void setPathUnderliningEnabled(boolean enabled) {
    if (this.isPathUnderliningEnabled == enabled) return;
    this.isPathUnderliningEnabled = enabled;
    // Clear all caches when state changes to ensure fresh checks.
    pathUnderlineCache.clear();
    pathValidationCache.clear();
    pendingPathValidations.clear();
    invalidate();
  }

  public void setErrorUnderlineColor(int color) {
    if (this.errorUnderlineColor == color) return;
    this.errorUnderlineColor = color;
    invalidate();
  }

  public void setErrorUnderlineEnabled(boolean enabled) {
    if (errorUnderlineEnabled == enabled) return;
    errorUnderlineEnabled = enabled;
    invalidate();
  }

  public void setErrorUnderlineHeightScale(float scale) {
    float safe = Math.max(0f, scale);
    if (errorUnderlineHeightScale == safe) return;
    errorUnderlineHeightScale = safe;
    invalidate();
  }

  public void setErrorUnderlineWaveLengthScale(float scale) {
    float safe = Math.max(0.1f, scale);
    if (errorUnderlineWaveLengthScale == safe) return;
    errorUnderlineWaveLengthScale = safe;
    invalidate();
  }

  public void setErrorUnderlineStrokeScale(float scale) {
    float safe = Math.max(0f, scale);
    if (errorUnderlineStrokeScale == safe) return;
    errorUnderlineStrokeScale = safe;
    invalidate();
  }

  public void setErrorUnderlineSmoothness(float radiusPx) {
    float safe = Math.max(0f, radiusPx);
    if (errorUnderlineSmoothness == safe) return;
    errorUnderlineSmoothness = safe;
    invalidate();
  }

  public void setErrorUnderline(int line, int col, int length) {
    if (line < 0) return;
    if (length <= 0) {
      errorUnderlineMap.remove(line);
      invalidate();
      return;
    }
    int start = Math.max(0, col);
    int end = Math.max(start, start + length);
    List<ErrorUnderlineSpan> list = errorUnderlineMap.get(line);
    if (list == null) {
      list = new ArrayList<>();
      errorUnderlineMap.put(line, list);
    }
    list.add(new ErrorUnderlineSpan(start, end));
    invalidate();
  }

  public void setStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(RULE_STRING, STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    invalidate();
  }

  public void setMultiLineStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(RULE_STRING, STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (isMultiLineStringsEnabled != enabled) {
      isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    invalidate();
  }

  // Toggle background highlight for hex color literals (e.g., #RRGGBB, 0xAARRGGBB).
  public void setColorCodeHighlightingEnabled(boolean enabled) {
    if (isColorHighlightingEnabled == enabled) return;
    isColorHighlightingEnabled = enabled;
    invalidate();
  }

  public void setBacktickStringsEnabled(boolean enabled) {
    if (isBacktickStringsEnabled == enabled) return;
    isBacktickStringsEnabled = enabled;
    clearHighlightCaches();
    invalidate();
  }

  public void setMultiLineComments(boolean enabled, int style, int color) {
    boolean needsInvalidate = false;
    if (blockCommentHighlightRule == null || blockCommentHighlightRule.style != style) {
      if (blockCommentHighlightRule != null) {
        highlightRules.remove(blockCommentHighlightRule);
      }
      blockCommentHighlightRule =
          new HighlightRule(
              RULE_BLOCK_COMMENT,
              style,
              color,
              paint.getTextSize(),
              paint.getTypeface(),
              false,
              HighlightRuleType.BLOCK_COMMENT);
      highlightRules.add(blockCommentHighlightRule);
      needsInvalidate = true;
    } else {
      if (blockCommentHighlightRule.paint.getColor() != color) {
        blockCommentHighlightRule.paint.setColor(color);
        needsInvalidate = true;
      }
    }
    if (isBlockCommentsEnabled != enabled) {
      isBlockCommentsEnabled = enabled;
      needsInvalidate = true;
    }
    if (needsInvalidate) {
      clearHighlightCaches();
      invalidate();
    }
  }

  public void setSingleLineCommentDelimiters(String... delimiters) {
    lineCommentDelimiters.clear();
    if (delimiters != null) {
      for (String d : delimiters) {
        if (d == null) continue;
        String trimmed = d.trim();
        if (trimmed.isEmpty()) continue;
        if (!lineCommentDelimiters.contains(trimmed)) {
          lineCommentDelimiters.add(trimmed);
        }
      }
    }
    // Prefer longer delimiters first (e.g. '//' before '/')
    lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
    clearHighlightCaches();
    invalidate();
  }

  public void ensureLineCommentDelimiter(String delimiter) {
    if (delimiter == null) return;
    String trimmed = delimiter.trim();
    if (trimmed.isEmpty()) return;
    if (!lineCommentDelimiters.contains(trimmed)) {
      lineCommentDelimiters.add(trimmed);
      lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
      clearHighlightCaches();
      invalidate();
    }
  }

  public void setSingleLineCommentsHighlight(boolean enabled, int style, int color) {
    if (!enabled) {
      if (lineCommentHighlightRule != null) {
        lineCommentHighlightRule = null;
        clearHighlightCaches();
        invalidate();
      }
      return;
    }

    if (lineCommentHighlightRule == null || lineCommentHighlightRule.style != style) {
      lineCommentHighlightRule =
          new HighlightRule(
              "",
              style,
              color,
              paint.getTextSize(),
              paint.getTypeface(),
              false,
              HighlightRuleType.LINE_COMMENT);
    } else {
      lineCommentHighlightRule.paint.setColor(color);
    }
    clearHighlightCaches();
    invalidate();
  }

  public void setSingleLineCommentSyntax(
      boolean enabled, int style, int color, String... delimiters) {
    setSingleLineCommentDelimiters(delimiters);
    setSingleLineCommentsHighlight(enabled, style, color);
  }

  public void setTripleQuoteStringsEnabled(boolean enabled) {
    if (isTripleQuoteStringsEnabled == enabled) return;
    isTripleQuoteStringsEnabled = enabled;
    clearHighlightCaches();
    invalidate();
  }

  public void setLayoutDirection(boolean isRtl) {
    if (this.isRtl == isRtl) return;
    this.isRtl = isRtl;
    lineNumber.lineNumbersPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    lineNumber.invalidateLineNumberCache();
    requestLayout();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    scroll.maxScrollXForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.scrollX =0f;
    keepCursorVisibleHorizontally();
    invalidate();
  }

  public void setFontFromAssets(String assetPath, int style) {
    try {
      Typeface tf = Typeface.createFromAsset(getContext().getAssets(), assetPath);
      applyTypeface(tf, style);
    } catch (Exception e) {
      Log.e("SodiumEditor", "setFontFromAssets failed: " + assetPath, e);
    }
  }

  public void setFontFromFile(String filePath, int style) {
    try {
      Typeface tf = Typeface.createFromFile(filePath);
      applyTypeface(tf, style);
    } catch (Exception e) {
      Log.e("SodiumEditor", "setFontFromFile failed: " + filePath, e);
    }
  }

  public void setFont(@Nullable Typeface typeface, int style) {
    applyTypeface(typeface, style);
  }

  public void setTextSize(float size) {
    applyTextSizePx(spToPx(size));
  }

  public float getTextSizeSp() {
    float scaled = getResources().getDisplayMetrics().scaledDensity;
    if (scaled <= 0f) return paint.getTextSize();
    return paint.getTextSize() / scaled;
  }

  public int getCursorLineValue() {
    return cursor.cursorLine;
  }

  public int getCursorCharValue() {
    return cursor.cursorChar;
  }

  public boolean hasSelectionValue() {
    return selection.hasSelection;
  }

  public int getSelectionStartLineValue() {
    return selection.selStartLine;
  }

  public int getSelectionStartCharValue() {
    return selection.selStartChar;
  }

  public int getSelectionEndLineValue() {
    return selection.selEndLine;
  }

  public int getSelectionEndCharValue() {
    return selection.selEndChar;
  }

  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    int targetLine = Math.max(0, cursorLine);
    int targetChar = Math.max(0, cursor.cursorChar);
    cursorLine = targetLine;
    if (cursorLine >= windowStartLine
        && cursorLine < windowStartLine + linesWindow.size()) {
      String lineText = getLineTextForRender(cursorLine);
      this.cursor.cursorChar = Math.max(0, Math.min(targetChar, lineText.length()));
    } else {
      this.cursor.cursorChar = targetChar;
    }
    caret.resetBlink();
    invalidate();
  }

  // --- Convenience cursor/line accessors ---
  public int getCurrentlineNumber() {
    return cursor.cursorLine;
  }

  public int getCurrentColumn() {
    return cursor.cursorChar;
  }

  public String getCurrentLineText() {
    return getLineTextForRender(cursor.cursorLine);
  }

  public void insertTextAt(int line, int col, String text) {
    if (text == null) return;
    if (Looper.myLooper() != Looper.getMainLooper()) {
      post(() -> insertTextAt(line, col, text));
      return;
    }
    setCursorPosition(line, col);
    insertTextAtCursor(text);
  }

  

  public String getTextSnapshot() {
    int total = getLinesCount();
    if (total <= 0) return "";
    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      populateDirectLinesForRange(0, total - 1, direct);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < total; i++) {
      String line = getLineTextForRenderWithDirect(i, direct);
      if (line == null) line = "";
      sb.append(line);
      if (i < total - 1) sb.append('\n');
    }
    return sb.toString();
  }

  public float spToPx(float sp) {
    return sp * getResources().getDisplayMetrics().scaledDensity;
  }

  public float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }

  public void updateTextSizeDependentMetrics() {
    float sizePx = paint.getTextSize();
    handleRadius = Math.max(4f, scaleByTextSize(baseHandleRadiusPx, baseHandleTextSizePx, sizePx));
    cursorWidth = Math.max(1f, scaleByTextSize(baseCursorWidthPx, baseCursorTextSizePx, sizePx));

    bracketMatchStrokeWidth =
        Math.max(
            1f, scaleByTextSize(baseBracketMatchStrokeWidth, baseBracketMatchTextSizePx, sizePx));
    bracketMatchPaint.setStrokeWidth(bracketMatchStrokeWidth);

    bracketGuideStrokeWidth =
        Math.max(
            1f, scaleByTextSize(baseBracketGuideStrokeWidth, baseBracketGuideTextSizePx, sizePx));
    bracketGuidePaint.setStrokeWidth(bracketGuideStrokeWidth);

    indentGuideStrokeWidth =
        Math.max(
            1f, scaleByTextSize(baseIndentGuideStrokeWidth, baseIndentGuideTextSizePx, sizePx));
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  public void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    paint.setTextSize(sizePx);
    if (!isSuggestionTextSizeCustom) {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(sizePx * suggestionTextSizeScale);
    lineNumber.lineNumbersPaint.setTextSize(sizePx);
    foldMarkerPaint.setTextSize(sizePx * foldMarkerTextScale);
    wordWrapIndicatorPaint.setTextSize(sizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(paint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
    lineHeight = paint.getFontSpacing();
    updateTextSizeDependentMetrics();
    updateWhitespaceGuideMetrics();
    lineNumber.invalidateLineNumberCache();

    for (HighlightRule rule : highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (whitespaceStringRule != null) whitespaceStringRule.updateTextSize(sizePx);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTextSize(sizePx);
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTextSize(sizePx);
    clearHighlightCaches();

    // Invalidate caches and approximate new max width
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    // Scale the max width instead of recalculating it synchronously.
    // This is an approximation but avoids massive lag.
    float scale = sizePx / oldSize;
    currentMaxWindowLineWidth *= scale;
    globalMaxLineWidth *= scale;
    scroll.maxLineWidthForScroll *= scale;
    scroll.maxScrollXForScroll *= scale;
    scroll.maxTextStartXForScroll = 0f;
    if (scale < 1f) {
      scroll.maxLineWidthForScroll = 0f;
      scroll.maxScrollXForScroll = 0f;
    }

    requestLayout(); // Still needed for gutter
    if (isWordWrapEnabled) invalidateWrapMetrics(true, !deferWrapRebuild);
    requestWrapPrefixRebuild();
    invalidate();
  }

  public void applyTypeface(@Nullable Typeface typeface, int style) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      final Typeface tf = typeface;
      final int st = style;
      post(() -> applyTypeface(tf, st));
      return;
    }
    Typeface safeBase = (typeface != null) ? typeface : Typeface.DEFAULT;
    baseTypeface = safeBase;
    int typefaceStyle;
    switch (style) {
      case STYLE_BOLD:
        typefaceStyle = Typeface.BOLD;
        break;
      case STYLE_ITALIC:
        typefaceStyle = Typeface.ITALIC;
        break;
      case STYLE_BOLD_ITALIC:
        typefaceStyle = Typeface.BOLD_ITALIC;
        break;
      default:
        typefaceStyle = Typeface.NORMAL;
        break;
    }
    Typeface finalTypeface = Typeface.create(safeBase, typefaceStyle);
    paint.setTypeface(finalTypeface);
    suggestionPaint.setTypeface(finalTypeface);
    lineNumber.lineNumbersPaint.setTypeface(finalTypeface);
    foldMarkerPaint.setTypeface(finalTypeface);
    wordWrapIndicatorPaint.setTypeface(finalTypeface);
    whitespaceGuidePaint.setTypeface(finalTypeface);
    if (whitespaceStringRule != null) whitespaceStringRule.updateTypeface(safeBase);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTypeface(safeBase);
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTypeface(safeBase);
    for (HighlightRule rule : highlightRules) {
      rule.updateTypeface(safeBase);
    }
    clearHighlightCaches();

    lineHeight = paint.getFontSpacing();
    updateWhitespaceGuideMetrics();
    lineNumber.invalidateLineNumberCache();
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);

    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    recalculateMaxLineWidth();

    requestLayout();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    invalidate();
  }

  public void updateWhitespaceGuideMetrics() {
    whitespaceGuidePaint.setTextSize(paint.getTextSize());
    whitespaceGuidePaint.setTypeface(paint.getTypeface());
    whitespaceGuideSpaceWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_SPACE);
    whitespaceGuideTabWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_TAB);
    whitespaceGuideDotPaint.setColor(whitespaceGuidePaint.getColor());
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    float dotSize = Math.max(1f, paint.getTextSize() / 7f);
    whitespaceGuideDotPaint.setStrokeWidth(dotSize);

    searchHighlightPaint.setStyle(Paint.Style.FILL);
    searchHighlightPaint.setColor(searchHighlightColor);
  }

  public int writeIntToChars(int value, char[] out) {
    int v = value;
    int pos = out.length;
    if (v == 0) {
      out[--pos] = '0';
      return pos;
    }
    while (v > 0 && pos > 0) {
      int digit = v % 10;
      out[--pos] = (char) ('0' + digit);
      v /= 10;
    }
    return pos;
  }

  public void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      @Nullable java.util.HashMap<Integer, String> directLines) {
    if (highlightRules.isEmpty()) return;
    if (firstVisibleLine > lastVisibleLine) return;

    HighlightRule stringRule = stringHighlightRule;
    HighlightRule blockRule = blockCommentHighlightRule;
    boolean needSyntax = stringRule != null || blockRule != null;
    boolean needRegex = !regexHighlightRules.isEmpty();
    if (!needSyntax && !needRegex) return;

    boolean inBlock = false;
    int stringState = 0;
    final int localWindowStart = windowStartLine;
    final int localWindowEnd;
    synchronized (linesWindow) {
      localWindowEnd = localWindowStart + linesWindow.size();
    }

    if (needSyntax) {
      int prevLine = firstVisibleLine - 1;
      Boolean cachedBlockPrev = blockCommentEndStateCache.get(prevLine);
      Integer cachedStringPrev = stringEndStateCache.get(prevLine);
      if (cachedBlockPrev != null && cachedStringPrev != null) {
        inBlock = cachedBlockPrev;
        stringState = cachedStringPrev;
      } else {
        int seedStart = localWindowStart;
        int seedEnd = Math.min(firstVisibleLine, localWindowEnd);
        for (int line = seedStart; line < seedEnd; line++) {
          String seedLine = getLineTextForRenderWithDirect(line, directLines);
          if (seedLine == null) seedLine = "";
          LineParseResult seedResult =
              parseLineForSyntax(seedLine, inBlock, stringState, null, null, false);
          inBlock = seedResult.endsInBlockComment;
          stringState = seedResult.endsInStringState;
          if (line >= localWindowStart && line < localWindowEnd) {
            if (isBlockCommentsEnabled) blockCommentEndStateCache.put(line, inBlock);
            stringEndStateCache.put(line, stringState);
          }
          if (line + 1 == firstVisibleLine) break;
        }
      }
    }

    for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
      List<HighlightSpan> cachedSpans = highlightCache.get(globalLine);
      boolean hasCachedState = true;
      Boolean cachedBlock = null;
      Integer cachedString = null;
      if (needSyntax && globalLine >= localWindowStart && globalLine < localWindowEnd) {
        cachedBlock = blockCommentEndStateCache.get(globalLine);
        cachedString = stringEndStateCache.get(globalLine);
        hasCachedState = cachedBlock != null && cachedString != null;
      }
      if (cachedSpans != null && (!needSyntax || hasCachedState)) {
        if (needSyntax && cachedBlock != null && cachedString != null) {
          inBlock = cachedBlock;
          stringState = cachedString;
        }
        continue;
      }

      String line = getLineTextForRenderWithDirect(globalLine, directLines);
      if (line == null) line = "";

      List<HighlightSpan> spans;
      if (needSyntax) {
        LineParseResult parseResult =
            parseLineForSyntax(line, inBlock, stringState, stringRule, blockRule, true);
        spans = parseResult.spans;
        inBlock = parseResult.endsInBlockComment;
        stringState = parseResult.endsInStringState;
        if (globalLine >= localWindowStart && globalLine < localWindowEnd) {
          if (isBlockCommentsEnabled) blockCommentEndStateCache.put(globalLine, inBlock);
          stringEndStateCache.put(globalLine, stringState);
        }
      } else {
        spans = new ArrayList<>();
      }

      if (needRegex && !line.isEmpty()) {
        for (HighlightRule rule : regexHighlightRules) {
          Matcher matcher = rule.pattern.matcher(line);
          while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            HighlightSpan span = new HighlightSpan(matcher.start(), matcher.end(), rule.paint);
            if (hasOverlap(span, spans)) continue;
            spans.add(span);
          }
        }
      }

      if (spans.size() > 1) {
        Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
      }
      highlightCache.put(globalLine, spans);
    }
  }

  public void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    if (startLine > endLine) return;
    int v = editVersion.get();
    if (startLine == lastHighlightEnsureStartLine
        && endLine == lastHighlightEnsureEndLine
        && v == lastHighlightEnsureEditVersion) {
      return;
    }
    lastHighlightEnsureStartLine = startLine;
    lastHighlightEnsureEndLine = endLine;
    lastHighlightEnsureEditVersion = v;
    ensureHighlightCacheForVisibleRange(startLine, endLine, directLines);
  }

  public void invalidateHighlightEnsureRange() {
    lastHighlightEnsureStartLine = -1;
    lastHighlightEnsureEndLine = -1;
    lastHighlightEnsureEditVersion = -1;
  }

  // --- Layout and Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float oldGutterWidth = lineNumber.lineNumbersGutterWidth;
    if (lineNumber.showLineNumbers) {
      int maxLines = 0;
      if (isIndexReady) {
        maxLines = lineOffsets.length;
      } else if (isEof) {
        maxLines = windowStartLine + linesWindow.size();
      } else {
        maxLines = 999999; // Wider fallback for width calculation until index is ready
      }
      String maxLineNum = String.valueOf(maxLines);
      float baseWidth = lineNumber.lineNumbersPaint.measureText(maxLineNum) + (lineNumber.GUTTER_TEXT_PADDING * 2);
      if (isCodeFoldingEnabled) {
        foldMarkerGutterWidth =
            foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
      } else {
        foldMarkerGutterWidth = 0f;
      }
      lineNumber.lineNumbersGutterWidth = baseWidth + foldMarkerGutterWidth + lineNumber.gutterSeparatorWidth;
    } else {
      lineNumber.lineNumbersGutterWidth = 0f;
    }

    if (isWordWrapEnabled && Math.abs(lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
    if (Math.abs(lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      lineNumber.invalidateLineNumberCache();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != oldw || h != oldh) {
      lineNumber.invalidateLineNumberCache();
    }
    if (w != oldw) {
      scroll.maxScrollXForScroll = 0f;
      scroll.maxTextStartXForScroll = 0f;
    }
    int minWindow = computeMinWindowSize();
    if (windowSize < minWindow) {
      windowSize = minWindow;
      reloadWindowAroundVisible(false);
    }
    if (isWordWrapEnabled && w != oldw) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
  }

  public float getTextStartX() {
    return isRtl ? paddingLeft : paddingLeft + lineNumber.lineNumbersGutterWidth;
  }

  public float getEffectiveScrollX() {
    return isRtl ? -scroll.scrollX :  scroll.scrollX;
  }

  public float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - getTextStartX();
  }

  public float getTextAreaWidth() {
    return Math.max(0f, getWidth() - lineNumber.lineNumbersGutterWidth - paddingLeft);
  }

  public float getRtlLineBaseX(@Nullable String line, int globalLine) {
    if (!isRtl || line == null) return 0f;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!isRtl || line == null) return 0f;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x = measureText(line, charIndex, globalLine);
    if (!isRtl) return x;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel = measureTextWithVisualSpaces(line, segStart, charIndex, paint);
    if (!isRtl) return xRel;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  public float getWrapWidth() {
    return Math.max(1f, getWidth() - getTextStartX());
  }

  public void invalidateWrapMetrics() {
    invalidateWrapMetrics(true, true);
  }

  public void invalidateWrapMetrics(boolean clearExisting) {
    invalidateWrapMetrics(clearExisting, true);
  }

  public void invalidateWrapMetrics(boolean clearExisting, boolean scheduleFullRebuild) {
    wrapCache.clear();
    wrapWidthPx = -1;
    wrapMetricsWidth = -1;
    wrapMetricsToken.incrementAndGet();
    wrapPrefixValidUpToLine = -1;

    if (clearExisting) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
    }

    int currentLines = getLinesCount();
    if (currentLines <= 0) currentLines = windowStartLine + linesWindow.size();

    boolean sizeMismatch = (wrapLineCounts != null && wrapLineCounts.length != currentLines);
    boolean missing = (wrapLineCounts == null || wrapLinePrefix == null);

    if (clearExisting || sizeMismatch || missing) {
      // Metrics are invalid or requested to be cleared.
      // We must rebuild the visible window's metrics SYNCHRONOUSLY to avoid
      // falling back to 1:1 rendering (which causes jumps and disappearing lines).
      // This function effectively "patches" the metrics for the visible area immediately.
      buildWrapMetricsForWindowSnapshot();

      // If we still don't have metrics (e.g. empty file), mark as not ready.
      if (wrapLineCounts == null) {
        wrapMetricsReady = false;
        totalWrapVisualLines = 0;
      } else {
        wrapMetricsReady = true;
      }
    } else {
      // Keep existing metrics during minor updates to reduce visual jitter.
      wrapMetricsReady = true;
    }

    if (isWordWrapEnabled) {
      if (scheduleFullRebuild) {
        // Queue a full background rebuild to ensure off-screen lines are eventually consistent.
        scheduleWrapMetricsBuild();
      } else {
        int widthPx = Math.max(1, Math.round(getWrapWidth()));
        scheduleWrapMetricsSnapshotIfNeeded(widthPx);
        scheduleWrapPrefixRebuildUpToWindow();
      }
    }
  }

  public void requestWrapPrefixRebuild() {
    if (!isWordWrapEnabled) return;
    if (zoom.isScaling || (scaleGestureDetector != null && scaleGestureDetector.isInProgress())) {
      wrapPrefixRebuildPending = true;
      return;
    }
    scheduleWrapPrefixRebuildUpToWindow();
  }

  public void cancelWrapPrefixRebuildForInteraction() {
    if (!wrapPrefixBuilding) return;
    // Invalidate the in-flight rebuild and defer a new one to avoid scroll lock/jumps.
    wrapPrefixToken.incrementAndGet();
    wrapPrefixBuilding = false;
    wrapPrefixRebuildPending = true;
  }

  public void cancelWrapWorkForPriority() {
    if (!isWordWrapEnabled) return;
    wrapMetricsToken.incrementAndGet();
    wrapSnapshotToken.incrementAndGet();
    wrapPrefixToken.incrementAndGet();
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  public boolean shouldSuppressWrapMetricsForFastSelectAll() {
    if (!isWordWrapEnabled || (!selection.isSelectAllActive && !selection.isEntireFileSelected)) return false;
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    return !isWrapMetricsUsableForWindow(widthPx);
  }

  

  public void scheduleWrapPrefixRebuildUpToWindow() {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    int total = getLinesCount();
    if (total <= 0) return;

    int targetLine;
    synchronized (linesWindow) {
      targetLine = windowStartLine + linesWindow.size() - 1;
    }
    if (targetLine < 0) return;
    targetLine = Math.min(targetLine, total - 1);
    final int targetLineFinal = targetLine;

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wrapPrefixBuilding && wrapPrefixWidth == widthPx && wrapPrefixTargetLine >= targetLineFinal)
      return;

    wrapPrefixBuilding = true;
    wrapPrefixWidth = widthPx;
    wrapPrefixTargetLine = targetLineFinal;

    if (!scroll.scroller.isFinished()) scroll.scroller.abortAnimation();

    final int token = wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) ( scroll.scrollY / lineHeight));
    VisualLinePosition anchorPos = getVisualPositionForIndex(anchorVisualIndex);
    final int anchorLine = anchorPos.line;
    final int oldAnchorPrefix =
        (wrapLinePrefix != null && anchorLine >= 0 && anchorLine < wrapLinePrefix.length)
            ? wrapLinePrefix[anchorLine]
            : anchorLine;

    final Paint wrapPaint = new Paint(paint);

    ioHandler.post(
        () -> {
          if (token != wrapPrefixToken.get()) return;

          int[] counts;
          if (baseCounts != null) {
            counts = baseCounts;
          } else {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          }

          if (sourceFile == null || !sourceFile.exists()) {
            // In-memory only: only safe to rebuild from window start if it begins at 0.
            int start;
            java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
            synchronized (linesWindow) {
              start = windowStartLine;
              snapshot.addAll(linesWindow);
            }
            if (start == 0) {
              int end = Math.min(targetLineFinal, snapshot.size() - 1);
              for (int i = 0; i <= end; i++) {
                String line = snapshot.get(i);
                counts[i] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
              }
            } else {
              post(
                  () -> {
                    if (token != wrapPrefixToken.get()) return;
                    wrapPrefixBuilding = false;
                  });
              return;
            }
          } else {
            BufferedReader br = null;
            try {
              br = reopenReaderAtStart();
              int lineIndex = 0;
              while (lineIndex <= targetLineFinal) {
                if (token != wrapPrefixToken.get()) return;
                String fileLine = (br != null) ? br.readLine() : null;
                String line = fileLine == null ? "" : fileLine;
                String mod;
                synchronized (modifiedLines) {
                  mod = modifiedLines.get(lineIndex);
                }
                if (mod != null) line = mod;
                counts[lineIndex] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
                lineIndex++;
                if (fileLine == null && mod == null) {
                  while (lineIndex <= targetLineFinal) {
                    counts[lineIndex] = 1;
                    lineIndex++;
                  }
                  break;
                }
              }
            } catch (Exception ignored) {
              post(
                  () -> {
                    if (token != wrapPrefixToken.get()) return;
                    wrapPrefixBuilding = false;
                  });
              return;
            } finally {
              try {
                if (br != null) br.close();
              } catch (Exception ignored) {
              }
            }
          }

          int[] prefix = new int[total + 1];
          int running = 0;
          for (int i = 0; i < total; i++) {
            running += counts[i];
            prefix[i + 1] = running;
          }
          final int runningFinal = running;
          final int newAnchorPrefix =
              (anchorLine >= 0 && anchorLine < prefix.length)
                  ? prefix[anchorLine]
                  : oldAnchorPrefix;
          final int deltaPrefix = newAnchorPrefix - oldAnchorPrefix;

          post(
              () -> {
                if (token != wrapPrefixToken.get()) return;
                if (Math.max(1, Math.round(getWrapWidth())) != widthPx) {
                  wrapPrefixBuilding = false;
                  return;
                }
                wrapPrefixBuilding = false;
                if (zoom.isZoomGestureActive()) {
                  zoom.pendingWrapPrefixCounts = counts;
                  zoom.pendingWrapPrefixPrefix = prefix;
                  zoom.pendingWrapPrefixTotalVisualLines = runningFinal;
                  zoom.pendingWrapPrefixWidthPx = widthPx;
                  zoom.pendingWrapPrefixValidUpToLine =
                      Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                  zoom.pendingApplyWrapPrefixUpdate = true;
                  return;
                }
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapPrefixValidUpToLine =
                    Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                if (deltaPrefix != 0) {
                  scroll.scrollY += deltaPrefix * lineHeight;
                  scroll.clampScrollY();
                }
                postInvalidateOnAnimation();
              });
        });
  }

  public void onLineContentChanged(int globalLine, @Nullable String newText) {
    if (!isWordWrapEnabled) return;
    wrapCache.remove(globalLine);

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!wrapMetricsReady
        || wrapLineCounts == null
        || wrapLinePrefix == null
        || wrapMetricsWidth != widthPx) {
      invalidateWrapMetrics();
      return;
    }
    if (globalLine < 0 || globalLine >= wrapLineCounts.length) {
      invalidateWrapMetrics();
      return;
    }

    int newCount = computeWrapCountForLine(newText, widthPx);
    int oldCount = wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wrapLinePrefix.length; i++) {
      wrapLinePrefix[i] += delta;
    }
    totalWrapVisualLines += delta;
  }

  public void onLineCountChanged() {
    if (isWordWrapEnabled) invalidateWrapMetrics();
    lineNumber.invalidateLineNumberCache();
  }

  public void buildWrapMetricsForWindowSnapshot() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    int[] counts;

    // Preserve existing counts to avoid jumpiness when lines scroll out of window
    if (wrapLineCounts != null && wrapLineCounts.length == total) {
      counts = wrapLineCounts.clone();
    } else {
      counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = 1;
    }

    int start;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    synchronized (linesWindow) {
      start = windowStartLine;
      snapshot.addAll(linesWindow);
    }

    if (!snapshot.isEmpty()) {
      for (int i = 0; i < snapshot.size(); i++) {
        int global = start + i;
        if (global < 0 || global >= total) continue;
        String line = snapshot.get(i);
        counts[global] = computeWrapCountForLine(line, widthPx);
      }
    }

    // Rebuild prefix array from the updated counts
    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }

    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    // Mark as valid up to the end so isWrapMetricsUsableForWindow returns true
    wrapPrefixValidUpToLine = total - 1;
    wrapMetricsReady = true;
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(int widthPx) {
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    int start;
    int size;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    synchronized (linesWindow) {
      start = windowStartLine;
      size = linesWindow.size();
      if (size > 0) snapshot.addAll(linesWindow);
    }
    if (size <= 0) return;

    if (wrapSnapshotBuilding
        && wrapSnapshotWidth == widthPx
        && wrapSnapshotStart == start
        && wrapSnapshotSize == size) {
      return;
    }

    wrapSnapshotWidth = widthPx;
    wrapSnapshotStart = start;
    wrapSnapshotSize = size;
    wrapSnapshotBuilding = true;
    final int token = wrapSnapshotToken.incrementAndGet();
    final Paint wrapPaint = new Paint(paint);

    ioHandler.post(
        () -> {
          int total = getLinesCount();
          if (total <= 0) total = start + size;
          if (total <= 0) {
            post(
                () -> {
                  if (token == wrapSnapshotToken.get()) {
                    wrapMetricsReady = true;
                    wrapMetricsBuilding = false;
                    wrapSnapshotBuilding = false;
                  }
                });
            return;
          }

          int[] counts;
          boolean widthChanged = (wrapMetricsWidth != widthPx);
          // If width changed or size mismatch, we must reset.
          // Otherwise, clone existing counts to preserve off-screen metrics and avoid race
          // conditions.
          if (wrapLineCounts == null || wrapLineCounts.length != total || widthChanged) {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          } else {
            counts = wrapLineCounts.clone();
          }

          // Update counts for the current window snapshot
          for (int i = 0; i < snapshot.size(); i++) {
            int global = start + i;
            if (global < 0 || global >= total) continue;
            String line = snapshot.get(i);
            counts[global] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
          }

          // Rebuild prefix array entirely to ensure consistency
          int[] prefix = new int[total + 1];
          int running = 0;
          for (int i = 0; i < total; i++) {
            running += counts[i];
            prefix[i + 1] = running;
          }
          final int runningFinal = running;

          post(
              () -> {
                if (token != wrapSnapshotToken.get()) return;
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapSnapshotBuilding = false;
                postInvalidateOnAnimation();
              });
        });
  }

  public void scheduleWrapMetricsBuild() {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    if (getWidth() <= 0) return;
    if (sourceFile == null || !isIndexReady) {
      buildWrapMetricsInMemory();
      return;
    }
    final int token = wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(getWrapWidth()));
    final Paint wrapPaint = new Paint(paint);
    wrapMetricsBuilding = true;
    ioHandler.post(() -> buildWrapMetricsFromFile(token, widthPx, wrapPaint));
  }

  public void buildWrapMetricsInMemory() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      String line = getLineTextForRender(i);
      int c = computeWrapCountForLine(line, widthPx);
      counts[i] = c;
      running += c;
      prefix[i + 1] = running;
    }
    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = (windowStartLine == 0) ? (total - 1) : -1;
    postInvalidateOnAnimation();
  }

  public void buildWrapMetricsFromFile(int token, int widthPx, Paint wrapPaint) {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      wrapMetricsBuilding = false;
      postInvalidateOnAnimation();
      return;
    }
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    BufferedReader br = null;
    try {
      br = reopenReaderAtStart();
      int lineIndex = 0;
      while (lineIndex < total) {
        if (token != wrapMetricsToken.get()) {
          wrapMetricsBuilding = false;
          return;
        }
        String fileLine = (br != null) ? br.readLine() : null;
        String line = fileLine == null ? "" : fileLine;
        String mod;
        synchronized (modifiedLines) {
          mod = modifiedLines.get(lineIndex);
        }
        if (mod != null) line = mod;
        int c = computeWrapCountForLine(line, widthPx, wrapPaint, false);
        counts[lineIndex] = c;
        running += c;
        prefix[lineIndex + 1] = running;
        lineIndex++;
        if (fileLine == null && mod == null) {
          // Reached EOF; treat remaining lines as empty.
          while (lineIndex < total) {
            counts[lineIndex] = 1;
            running += 1;
            prefix[lineIndex + 1] = running;
            lineIndex++;
          }
          break;
        }
      }
    } catch (Exception ignored) {
      wrapMetricsBuilding = false;
      return;
    } finally {
      try {
        if (br != null) br.close();
      } catch (Exception ignored) {
      }
    }
    if (token != wrapMetricsToken.get()) {
      wrapMetricsBuilding = false;
      return;
    }
    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = total - 1;
    wrapMetricsBuilding = false;
    postInvalidateOnAnimation();
  }

  public int computeWrapCountForLine(String line, int widthPx) {
    int[] starts = computeWrapStarts(line, widthPx, paint, true);
    return Math.max(1, starts.length);
  }

  public int computeWrapCountForLine(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    int[] starts = computeWrapStarts(line, widthPx, p, useSharedBuffer);
    return Math.max(1, starts.length);
  }

  public int[] getWrapStartsForLine(int globalLine, String line) {
    if (!isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wrapWidthPx != widthPx) {
      wrapWidthPx = widthPx;
      wrapCache.clear();
    }
    boolean cacheable = isWrapCacheableForLine(globalLine);
    if (!cacheable) {
      wrapCache.remove(globalLine);
      return computeWrapStarts(line, widthPx, paint, true);
    }
    int[] cached = wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts = computeWrapStarts(line, widthPx, paint, true);
    wrapCache.put(globalLine, starts);
    return starts;
  }

  public boolean isWrapCacheableForLine(int globalLine) {
    if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) {
      return true;
    }
    synchronized (modifiedLines) {
      return modifiedLines.containsKey(globalLine);
    }
  }

  public int[] computeWrapStarts(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    if (line == null) return new int[] {0};
    int len = line.length();
    if (len == 0) return new int[] {0};
    if (shouldUseBreakTextWrap(line)) {
      return computeWrapStartsWithBreakText(line, widthPx, p);
    }
    float[] widths;
    if (useSharedBuffer) {
      if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
        measureWidthBuffer = new float[len];
      }
      widths = measureWidthBuffer;
    } else {
      widths = new float[len];
    }
    p.getTextWidths(line, 0, len, widths);
    float[] adv = new float[len];
    for (int i = 0; i < len; i++) {
      adv[i] = getCharAdvanceWidth(line.charAt(i), widths[i], p);
    }
    java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
    int i = 0;
    starts.add(0);
    while (i < len) {
      float w = 0f;
      int lastBreak = -1;
      int j = i;
      for (; j < len; j++) {
        float a = adv[j];
        if (w + a > widthPx && j > i) break;
        w += a;
        if (Character.isWhitespace(line.charAt(j))) {
          lastBreak = j;
        }
      }
      if (j >= len) break;
      int next;
      if (lastBreak >= i) {
        next = lastBreak + 1;
      } else {
        next = Math.max(i + 1, j);
      }
      if (next <= i) next = i + 1;
      starts.add(next);
      i = next;
    }
    int[] out = new int[starts.size()];
    for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
    return out;
  }

  public boolean shouldUseBreakTextWrap(String line) {
    if (getVisualSpaceScale() != 1) return false;
    return line.indexOf('\t') < 0;
  }

  public int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
    int len = line.length();
    java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
    int i = 0;
    starts.add(0);
    while (i < len) {
      int count = p.breakText(line, i, len, true, widthPx, null);
      if (count <= 0) count = 1;
      int end = i + count;
      if (end >= len) break;
      int lastBreak = -1;
      for (int j = end - 1; j >= i; j--) {
        if (Character.isWhitespace(line.charAt(j))) {
          lastBreak = j;
          break;
        }
      }
      int next;
      if (lastBreak >= i) {
        next = lastBreak + 1;
      } else {
        next = end;
      }
      if (next <= i) next = i + 1;
      starts.add(next);
      i = next;
    }
    int[] out = new int[starts.size()];
    for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
    return out;
  }

  public int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    if (starts == null || starts.length == 0) return 0;
    int idx = 0;
    for (int i = 0; i < starts.length; i++) {
      if (starts[i] <= charIndex) idx = i;
      else break;
    }
    return idx;
  }

  public int getWrapSegmentStart(int[] starts, int segIndex) {
    if (starts == null || starts.length == 0) return 0;
    if (segIndex <= 0) return starts[0];
    return starts[Math.min(segIndex, starts.length - 1)];
  }

  public int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segIndex + 1;
    if (next >= 0 && next < starts.length) return starts[next];
    return lineLength;
  }

  public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    if (text == null || text.isEmpty()) return 0;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (isRtl) {
      float baseX = getRtlSegmentBaseX(text, globalLine, start, end);
      x -= baseX;
      float w = measureHighlightedSegmentWidth(text, globalLine, start, end);
      x = w - x;
    }
    if (x <= 0f) return start;
    int len = end - start;
    if (len <= 0) return start;
    if (getVisualSpaceScale() == 1) {
      int count = paint.breakText(text, start, end, true, x, null);
      int idx = start + Math.max(0, count);
      return Math.min(idx, end);
    }
    if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
      measureWidthBuffer = new float[len];
    }
    paint.getTextWidths(text, start, end, measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < len; i++) {
      float adv = getCharAdvanceWidth(text.charAt(start + i), measureWidthBuffer[i], paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return start + i;
      if (x < current + adv) return start + i + 1;
      current += adv;
    }
    return end;
  }

  public CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    float y = viewY +  scroll.scrollY;
    int visualIndex = Math.max(0, (int) (y / lineHeight));
    VisualLinePosition pos =
        isWordWrapEnabled
            ? getVisualPositionForIndex(visualIndex)
            : new VisualLinePosition(mapVisibleIndexToGlobal(visualIndex), 0);
    String line = getLineTextForRenderWithDirect(pos.line, directLines);
    if (!isWordWrapEnabled) {
      float x = viewToTextX(viewX);
      int charIndex = getCharIndexForX(line, x, pos.line);
      int clamped = Math.max(0, Math.min(charIndex, getLogicalLineLength(pos.line, line)));
      return new CursorTarget(pos.line, clamped);
    }
    int[] starts = getWrapStartsForLine(pos.line, line);
    int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
    int segStart = getWrapSegmentStart(starts, seg);
    int segEnd = getWrapSegmentEnd(starts, seg, line.length());
    float x = viewToTextX(viewX);
    int charIndex = getCharIndexForXInRange(line, pos.line, segStart, segEnd, x);
    int clamped = Math.max(0, Math.min(charIndex, line.length()));
    return new CursorTarget(pos.line, clamped);
  }

  public int getWindowEndLine() {
    synchronized (linesWindow) {
      return Math.max(0, windowStartLine + linesWindow.size() - 1);
    }
  }

  public boolean isWrapMetricsUsableForWindow(int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLinePrefix == null || wrapLineCounts == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) return false;
    if (wrapLineCounts.length != total || wrapLinePrefix.length != total + 1) return false;
    int windowEnd = getWindowEndLine();
    return wrapPrefixValidUpToLine >= windowEnd;
  }

  public boolean isWrapMetricsUsableForLine(int line) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) return false;
    return wrapPrefixValidUpToLine >= line;
  }

  public int getTotalVisualLineCount() {
    if (!isWordWrapEnabled) return getVisibleLineCount();
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      int total = getLinesCount();
      if (total <= 0) total = windowStartLine + linesWindow.size();
      return Math.max(1, total);
    }
    return Math.max(1, totalWrapVisualLines);
  }

  public int getWrapRangeCount(int startLine, int endLine) {
    if (wrapLinePrefix == null) return 0;
    int total = wrapLinePrefix.length - 1;
    int s = Math.max(0, Math.min(startLine, total - 1));
    int e = Math.max(s, Math.min(endLine, total - 1));
    return wrapLinePrefix[e + 1] - wrapLinePrefix[s];
  }

  public static final class VisualLinePosition {
    final int line;
    final int segment;

    VisualLinePosition(int line, int segment) {
      this.line = line;
      this.segment = segment;
    }
  }

  public VisualLinePosition getVisualPositionForIndex(int visualIndex) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      if (isWordWrapEnabled) {
        return getVisualPositionForIndexFallback(visualIndex, widthPx);
      }
      int line = mapVisibleIndexToGlobal(visualIndex);
      return new VisualLinePosition(line, 0);
    }
    int maxVisual = Math.max(0, totalWrapVisualLines - 1);
    int v = Math.max(0, Math.min(visualIndex, maxVisual));
    int line = findLineForVisualIndex(v);
    int seg = v - wrapLinePrefix[line];
    return new VisualLinePosition(line, seg);
  }

  public VisualLinePosition getVisualPositionForIndexFallback(int visualIndex, int widthPx) {
    int idx = Math.max(0, visualIndex);
    int baseLine = Math.max(0, windowStartLine);
    int baseVisual = baseLine;
    if (wrapLinePrefix != null
        && wrapPrefixValidUpToLine >= baseLine
        && baseLine < wrapLinePrefix.length) {
      baseVisual = wrapLinePrefix[baseLine];
    }
    int remaining = idx - baseVisual;
    if (remaining <= 0) {
      return new VisualLinePosition(baseLine, 0);
    }

    int line = baseLine;
    int windowEnd;
    synchronized (linesWindow) {
      windowEnd = windowStartLine + linesWindow.size() - 1;
    }
    if (windowEnd < baseLine) windowEnd = baseLine;

    while (line <= windowEnd) {
      String text = getLineTextForRender(line);
      int[] starts = getWrapStartsForLine(line, text);
      int segCount = Math.max(1, starts.length);
      if (remaining < segCount) {
        return new VisualLinePosition(line, Math.max(0, Math.min(remaining, segCount - 1)));
      }
      remaining -= segCount;
      line++;
    }

    return new VisualLinePosition(windowEnd, 0);
  }

  public float getViewXForLineChar(String line, int globalLine, int ch) {
    if (line == null) line = "";
    int safeChar = Math.max(0, Math.min(ch, getLogicalLineLength(globalLine, line)));
    if (!isWordWrapEnabled) {
      return getTextStartX() + measureText(line, safeChar, globalLine) - getEffectiveScrollX();
    }
    int[] starts = getWrapStartsForLine(globalLine, line);
    int seg = getWrapSegmentIndexForChar(starts, safeChar);
    int segStart = getWrapSegmentStart(starts, seg);
    float x = measureTextWithVisualSpaces(line, segStart, safeChar, paint);
    return getTextStartX() + x - getEffectiveScrollX();
  }

  public float getViewYTopForLineChar(int globalLine, int ch) {
    int v = getVisualIndexForLineAndChar(globalLine, ch);
    return v * lineHeight -  scroll.scrollY;
  }

  public int findLineForVisualIndex(int visualIndex) {
    if (wrapLinePrefix == null || wrapLinePrefix.length == 0) return 0;
    int lo = 0;
    int hi = wrapLinePrefix.length - 1;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (wrapLinePrefix[mid] <= visualIndex) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    int line = Math.max(0, lo - 1);
    return Math.min(line, wrapLinePrefix.length - 2);
  }

  public boolean patchWrapMetricsForVisualRange(
      int firstVisualIndex,
      int lastVisualIndex,
      @Nullable java.util.Map<Integer, String> directLines,
      int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    if (wrapLineCounts.length + 1 != wrapLinePrefix.length) return false;

    final int anchorFirstVisual = firstVisualIndex;
    final VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    final int anchorLine = anchorPos.line;
    final int anchorSeg = anchorPos.segment;

    boolean changed = false;

    int v = Math.max(0, firstVisualIndex);
    int vEnd = Math.max(v, lastVisualIndex);
    for (; v <= vEnd; v++) {
      VisualLinePosition pos = getVisualPositionForIndex(v);
      int line = pos.line;
      if (line < 0 || line >= wrapLineCounts.length) break;
      String text = getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(line, text);
      int newCount = Math.max(1, starts.length);
      int oldCount = wrapLineCounts[line];
      if (newCount == oldCount) continue;

      int delta = newCount - oldCount;
      wrapLineCounts[line] = newCount;
      for (int i = line + 1; i < wrapLinePrefix.length; i++) {
        wrapLinePrefix[i] += delta;
      }
      totalWrapVisualLines += delta;
      changed = true;
    }

    if (!changed) return false;

    if (anchorLine >= 0 && anchorLine < wrapLinePrefix.length) {
      int newAnchorFirstVisual = wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        scroll.scrollY += dv * lineHeight;
        scroll.clampScrollY();
      }
    }
    return true;
  }

  public int clampLineForSelection(int line) {
    if (line < 0) return 0;
    if (isEof) {
      int last = windowStartLine + linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  public boolean isLineSelectable(int line) {
    ensureLineInWindow(line, true);
    String ln = getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }

  public final Runnable autoScrollRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (draggingHandle == 0) return;
          if (autoScrollX != 0 || autoScrollY != 0) {
            scroll.scrollY += autoScrollX;
            float nextY =  scroll.scrollY + autoScrollY;
            if (!isIndexReady && !isEof && isWindowLoading) {
              float effectiveHeight =
                  (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
              float winTop = windowStartLine * lineHeight;
              float winBottom = (windowStartLine + linesWindow.size()) * lineHeight;
              float maxY = Math.max(0f, winBottom - effectiveHeight);
              if (autoScrollY > 0 && nextY > maxY) nextY = maxY;
              if (autoScrollY < 0 && nextY < winTop) nextY = winTop;
            }
             scroll.scrollY = nextY;
            scroll.clampScrollX();
            scroll.clampScrollY();
            updateHandlePosition(lastTouchX, lastTouchY);
            checkAndLoadWindow();
            invalidate();
            mainHandler.postDelayed(this, 16);
          }
        }
      };

  public String buildFoldDisplayLine(String line, FoldRange range, int[] placeholderBoundsOut) {
    if (line == null) line = "";
    int placeholderStart = 0;
    int placeholderEnd = 0;
    String display;

    if (range.isBlockComment) {
      int safeIdx = Math.max(0, Math.min(range.openCharIndex, line.length()));
      String prefix = line.substring(0, safeIdx);
      placeholderStart = prefix.length() + 2;
      placeholderEnd = placeholderStart + FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + "/*" + FOLD_PLACEHOLDER_TEXT + "*/";
    } else if (range.isIndentFold) {
      String prefix = line;
      placeholderStart = prefix.length();
      placeholderEnd = placeholderStart + FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + FOLD_PLACEHOLDER_TEXT;
    } else {
      int safeIdx = Math.max(0, Math.min(range.openCharIndex, Math.max(0, line.length() - 1)));
      String prefix = line.substring(0, safeIdx + 1);
      placeholderStart = prefix.length();
      placeholderEnd = placeholderStart + FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + FOLD_PLACEHOLDER_TEXT + range.closeChar;
    }

    if (placeholderBoundsOut != null && placeholderBoundsOut.length >= 2) {
      placeholderBoundsOut[0] = placeholderStart;
      placeholderBoundsOut[1] = placeholderEnd;
    }
    return display;
  }

  public void drawFoldedLine(Canvas canvas, String line, int globalLine) {
    FoldRange range = foldRanges.get(globalLine);
    if (range == null) return;

    float y = Math.round(getDrawLineTop(globalLine) + lineHeight - paint.descent());
    paint.getTextBounds(FOLD_PLACEHOLDER_TEXT, 0, FOLD_PLACEHOLDER_TEXT.length(), textBounds);
    float top = Math.round(y + textBounds.top - foldPlaceholderPadY);
    float bottom = Math.round(y + textBounds.bottom + foldPlaceholderPadY);

    int prefixEnd;
    if (range.isBlockComment) {
      prefixEnd = Math.min(range.openCharIndex + 2, line.length());
    } else if (range.isIndentFold) {
      prefixEnd = line.length();
    } else {
      prefixEnd = Math.min(range.openCharIndex + 1, line.length());
    }

    float xStart = measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
    float placeholderWidth = Math.max(0f, paint.measureText(FOLD_PLACEHOLDER_TEXT));
    foldPlaceholderRect.set(xStart, top, xStart + placeholderWidth, bottom);
    canvas.drawRoundRect(
        foldPlaceholderRect, foldPlaceholderCorner, foldPlaceholderCorner, foldPlaceholderPaint);

    drawHighlightedSegment(canvas, line, globalLine, 0, prefixEnd, 0f, y);

    paint.setUnderlineText(false);
    canvas.drawText(FOLD_PLACEHOLDER_TEXT, xStart, y, paint);

    float xAfter = xStart + placeholderWidth;
    if (range.isBlockComment) {
      Paint commentPaint =
          (blockCommentHighlightRule != null) ? blockCommentHighlightRule.paint : paint;
      commentPaint.setUnderlineText(false);
      canvas.drawText("*/", xAfter, y, commentPaint);
    } else if (!range.isIndentFold) {
      canvas.drawText(String.valueOf(range.closeChar), xAfter, y, paint);
    }
  }

  public boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
    if (!isCodeFoldingEnabled) return false;
    FoldRange range = foldRanges.get(globalLine);
    if (range == null || !range.collapsed) return false;
    if (line == null) line = "";

    int prefixEnd;
    if (range.isBlockComment) {
      prefixEnd = Math.min(range.openCharIndex + 2, line.length());
    } else if (range.isIndentFold) {
      prefixEnd = line.length();
    } else {
      prefixEnd = Math.min(range.openCharIndex + 1, line.length());
    }
    float xStart = measureHighlightedSegmentWidth(line, globalLine, 0, prefixEnd);
    float placeholderWidth = Math.max(0f, paint.measureText(FOLD_PLACEHOLDER_TEXT));
    float pad = Math.max(0f, foldPlaceholderPadX);
    float left = xStart - pad;
    float right = xStart + placeholderWidth + pad;
    return localX >= left && localX <= right;
  }

  public void drawHighlightedSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float x, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    if (highlightRules.isEmpty()) {
      paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y, paint);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
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

  public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    if (line == null || line.isEmpty() || start >= end) return 0f;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return 0f;

    if (highlightRules.isEmpty()) {
      return paint.measureText(line, start, end);
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
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

  public String getFoldMarkerForLine(int line, @Nullable String lineText) {
    if (!isCodeFoldingEnabled) return null;
    FoldRange range = foldRanges.get(line);
    if (range != null) return range.collapsed ? ">" : "v";
    if (lineText == null) return null;
    boolean isIndentCandidate = isIndentationBlocksEnabled && isIndentFoldCandidate(lineText);
    if (!isIndentCandidate && !shouldShowFoldMarkerFromLine(lineText)) return null;
    FoldRange found = findFoldRangeForLine(line);
    if (found == null) return null;
    foldRanges.put(found.startLine, found);
    if (found.isIndentFold) indentGuideIntervalsDirty = true;
    foldIntervalsDirty = true;
    return "v";
  }

  public boolean isIndentFoldCandidate(String line) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = rstripWhitespace(line);
    return !trimmed.isEmpty() && trimmed.endsWith(":");
  }

  public void startFoldMarkerRipple(int line) {
    if (!isCodeFoldingEnabled || !lineNumber.showLineNumbers) return;
    foldRippleLine = line;
    float gutterWidth = foldMarkerGutterWidth;
    if (gutterWidth <= 0f) {
      gutterWidth = foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
    }
    foldRippleMaxRadius =
        Math.max(lineHeight * 0.35f, Math.min(lineHeight * 0.6f, gutterWidth * 0.6f));
    if (foldRippleAnimator != null) foldRippleAnimator.cancel();
    foldRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
    foldRippleAnimator.setDuration(220);
    foldRippleAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          foldRippleRadius = foldRippleMaxRadius * t;
          foldRippleAlpha = 0.35f * (1f - t);
          invalidate();
        });
    foldRippleAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            foldRippleAlpha = 0f;
            foldRippleRadius = 0f;
            foldRippleLine = -1;
            invalidate();
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            foldRippleAlpha = 0f;
            foldRippleRadius = 0f;
            foldRippleLine = -1;
            invalidate();
          }
        });
    foldRippleAnimator.start();
  }

  public void clearFoldRipple() {
    if (foldRippleAnimator != null) {
      foldRippleAnimator.cancel();
      foldRippleAnimator = null;
    }
    foldRippleAlpha = 0f;
    foldRippleRadius = 0f;
    foldRippleLine = -1;
  }

  public boolean shouldShowFoldMarkerFromLine(String line) {
    if (line == null || line.isEmpty()) return false;
    int blockStart = line.indexOf("/*");
    if (blockStart >= 0) {
      int blockEnd = line.indexOf("*/", blockStart + 2);
      if (blockEnd < 0) return true;
    }

    int idx = line.indexOf('{');
    if (idx >= 0 && line.indexOf('}', idx + 1) < 0) return true;
    idx = line.indexOf('(');
    if (idx >= 0 && line.indexOf(')', idx + 1) < 0) return true;
    idx = line.indexOf('[');
    if (idx >= 0 && line.indexOf(']', idx + 1) < 0) return true;
    return false;
  }

  public void drawContent(Canvas canvas) {
    if (isWordWrapEnabled) {
      drawContentWrapped(canvas);
      return;
    }
    final boolean drawDecorations = zoom.shouldDrawDecorations();

    // Calculate visible line range
    int firstVisibleIndex = (int) ( scroll.scrollY / lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(getHeight() / lineHeight) + 5;

    int firstVisibleLine = firstVisibleIndex;
    int lastVisibleLine = lastVisibleIndex;
    if (isCodeFoldingEnabled) {
      int visibleCount = getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = mapVisibleIndexToGlobal(lastVisibleIndex);
      drawBaseLine = firstVisibleIndex;
    } else {
      drawBaseLine = firstVisibleLine;
    }

    float baseY = drawBaseLine * lineHeight;
    float translateY = - scroll.scrollY + baseY;
    if (isEof) {
      synchronized (linesWindow) {
        int lastDocLine = Math.max(0, windowStartLine + linesWindow.size() - 1);
        lastVisibleLine = Math.min(lastVisibleLine, lastDocLine);
      }
    }
    if (lastVisibleLine < firstVisibleLine) lastVisibleLine = firstVisibleLine;

    maybeKickWindowLoad(firstVisibleLine);
    maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);

    // --- 1. Draw fixed gutter background ---
    if (lineNumber.showLineNumbers) {
      canvas.drawRect(
          lineNumber.getGutterStartX(),
          0,
          lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth,
          getHeight(),
          lineNumber.gutterPaint);

      // Draw separator line
      float separatorLeft;
      if (isRtl) {
        // Separator is on the left side of the gutter (inner edge)
        separatorLeft = lineNumber.getGutterStartX();
      } else {
        // Separator is on the right side of the gutter (inner edge)
        separatorLeft = lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumber.gutterSeparatorWidth,
          getHeight(),
          lineNumber.gutterSeparatorPaint);
    }

    if (lineNumber.highlightCurrentLineInGutter
        && cursor.cursorLine >= firstVisibleLine
        && cursor.cursorLine <= lastVisibleLine
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursor.cursorLine))) {
      int drawIndex = isCodeFoldingEnabled ? getVisibleIndexForGlobalLine(cursor.cursorLine) : cursor.cursorLine;
      float top = Math.round(drawIndex * lineHeight -  scroll.scrollY);
      float bottom = top + lineHeight;
      lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (lineNumber.showLineNumbers) {
      drawlineNumbersCachedUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (isCodeFoldingEnabled && drawDecorations) {
        drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    // Clip the text area so it doesn't draw over the gutter
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), translateY);
    if (zoom.pinchVisualZoomActive) {
      float pivotX = zoom.pinchFocusX - (getTextStartX() - getEffectiveScrollX());
      float pivotY = zoom.pinchFocusY - translateY;
      canvas.scale(zoom.pinchVisualScale, zoom.pinchVisualScale, pivotX, pivotY);
    }

    // --- This is the original text, selection, and handle drawing logic ---
    Paint selPaint = null;
    if (selection.hasSelection) {
      selectionPaint.setColor(selectionHighlightColor);
      selPaint = selectionPaint;
    }

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      boolean needDirect =
          (firstVisibleLine < windowStartLine)
              || (firstVisibleLine >= windowStartLine + linesWindow.size())
              || (lastVisibleLine >= windowStartLine + linesWindow.size());

      if (needDirect) {
        directLinesTmp.clear();
        directLines = directLinesTmp;
        if (firstVisibleLine < windowStartLine) {
          populateDirectLinesForRange(
              firstVisibleLine, Math.min(lastVisibleLine, windowStartLine - 1), directLines);
        }
        int winEnd = windowStartLine + linesWindow.size() - 1;
        if (lastVisibleLine > winEnd) {
          populateDirectLinesForRange(
              Math.max(firstVisibleLine, winEnd + 1), lastVisibleLine, directLines);
        }
        if (directLines.isEmpty()
            && (firstVisibleLine < windowStartLine
                || firstVisibleLine >= windowStartLine + linesWindow.size())) {
          populateDirectLinesForRange(firstVisibleLine, lastVisibleLine, directLines);
        }
      }
    }

    BracketMatch bracketMatch = null;
    if (isBracketMatchingEnabled) {
      int v = editVersion.get();
      if (cachedBracketMatch != null
          && cachedBracketMatchCursorLine == cursor.cursorLine
          && cachedBracketMatchCursorChar == cursor.cursorChar
          && cachedBracketMatchEditVersion == v) {
        bracketMatch = cachedBracketMatch;
      } else {
        bracketMatch = findBracketMatchInVisible(firstVisibleLine, lastVisibleLine, directLines);
        if (bracketMatch != null) {
          cachedBracketMatch = bracketMatch;
          cachedBracketMatchCursorLine = cursor.cursorLine;
          cachedBracketMatchCursorChar = cursor.cursorChar;
          cachedBracketMatchEditVersion = v;
        }
      }
    }

    int winEnd;
    synchronized (linesWindow) {
      winEnd = windowStartLine + linesWindow.size() - 1;
    }
    int prefetchForDraw = zoom.isZoomGestureActive() ? 0 : prefetchLines;
    int hlStart = Math.max(windowStartLine, Math.max(0, firstVisibleLine - prefetchForDraw));
    int hlEnd = Math.min(winEnd, lastVisibleLine + prefetchForDraw);
    maybeEnsureHighlightCacheForRange(hlStart, hlEnd, directLines);

    if (isBracketGuidesEnabled && drawDecorations) {
      ensureBracketGuideCacheForWindow(directLines);
    }

    if (isCodeFoldingEnabled) {
      if (indentGuideIntervalsDirty) rebuildIndentGuideIntervalsIfNeeded();
      for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
        int globalLine = mapVisibleIndexToGlobal(v);
        String line = getLineTextForRenderWithDirect(globalLine, directLines);
        FoldRange foldRange = getFoldRangeAtStart(globalLine);
        boolean isFoldStart = (foldRange != null);
        float lineBaseX = isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            isRtl
                ? measureHighlightedSegmentWidth(
                    line, globalLine, 0, getLogicalLineLength(globalLine, line))
                : 0f;

        // Highlight the current line, only if there is no selection
        if (highlightCurrentLine && globalLine == cursor.cursorLine && !selection.hasSelection) {
          float top = Math.round(getDrawLineTop(globalLine));
          float bottom = Math.round(getDrawLineBottom(globalLine));
          float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
          float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, lineNumber.currentLinePaint);
        }

        if (selection.hasSelection && selPaint != null) {
          float top = Math.round(getDrawLineTop(globalLine));
          float bottom = Math.round(getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth,  scroll.scrollX + (getWidth() - getTextStartX()));
          if (isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selection.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selection.selStartLine;
              boolean roundBottom = globalLine == selection.selEndLine;
              float leftSel = isRtl ? lineBaseX : 0f;
              float rightSel = isRtl ? (lineBaseX + lineWidth) : fullRight;
              drawSelectionSegment(
                  canvas,
                  leftSel,
                  top,
                  rightSel,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  selPaint);
            }
          } else {
            int startLine, endLine, startChar, endChar;
            if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) <= 0) {
              startLine = selection.selStartLine;
              startChar = selection.selStartChar;
              endLine = selection.selEndLine;
              endChar = selection.selEndChar;
            } else {
              startLine = selection.selEndLine;
              startChar = selection.selEndChar;
              endLine = selection.selStartLine;
              endChar = selection.selStartChar;
            }

            if (globalLine >= startLine && globalLine <= endLine) {
              float left, right;
              if (isRtl) {
                float lineLeft = lineBaseX;
                float lineRight = lineBaseX + lineWidth;
                if (startLine == endLine) {
                  float x1 =
                      getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  float x2 =
                      getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = Math.min(x1, x2);
                  right = Math.max(x1, x2);
                } else if (globalLine == startLine) {
                  float x =
                      getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  left = lineLeft;
                  right = x;
                } else if (globalLine == endLine) {
                  float x =
                      getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = x;
                  right = lineRight;
                } else {
                  left = lineLeft;
                  right = lineRight;
                }
              } else {
                if (startLine == endLine) {
                  left = measureText(line, Math.min(startChar, line.length()), globalLine);
                  right = measureText(line, Math.min(endChar, line.length()), globalLine);
                } else {
                  if (globalLine == startLine) {
                    left = measureText(line, Math.min(startChar, line.length()), globalLine);
                    right = fullRight;
                  } else if (globalLine == endLine) {
                    left = 0;
                    right = measureText(line, Math.min(endChar, line.length()), globalLine);
                    if (line.length() == 0) right = fullRight;
                  } else {
                    left = 0;
                    right = fullRight;
                  }
                }
              }
              if (right > left) {
                boolean isStart = globalLine == startLine;
                boolean isEnd = globalLine == endLine;
                boolean roundTop = isStart;
                boolean roundBottom = isEnd;
                if (!isStart && !isEnd) {
                  roundTop = false;
                  roundBottom = false;
                } else if (isStart && !isEnd) {
                  roundBottom = false;
                } else if (!isStart && isEnd) {
                  roundTop = false;
                }
                drawSelectionSegment(
                    canvas,
                    left,
                    top,
                    right,
                    bottom,
                    roundTop,
                    roundTop,
                    roundBottom,
                    roundBottom,
                    selPaint);
              }
            }
          }
        }

        float y = Math.round(getDrawLineTop(globalLine) + lineHeight - paint.descent());
        paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        if (isFoldStart) {
          if (isBracketGuidesEnabled && drawDecorations) {
            List<BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
            drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
          }
          if (drawDecorations) {
            drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
            drawIndentGuidesForLine(canvas, line, globalLine);
          }
          drawFoldedLine(canvas, line, globalLine);
          canvas.restore();
          continue;
        }

        float lineTop = Math.round(getDrawLineTop(globalLine));
        float lineBottom = Math.round(getDrawLineBottom(globalLine));
        drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (isBracketGuidesEnabled && drawDecorations) {
          List<BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
          drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          drawBracketMatchForLine(canvas, line, globalLine, bracketMatch);
        }
        canvas.restore();
      }
    } else {
      if (indentGuideIntervalsDirty) rebuildIndentGuideIntervalsIfNeeded();
      for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
        String line = getLineTextForRenderWithDirect(globalLine, directLines);
        float lineBaseX = isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            isRtl
                ? measureHighlightedSegmentWidth(
                    line, globalLine, 0, getLogicalLineLength(globalLine, line))
                : 0f;

        // Highlight the current line, only if there is no selection
        if (highlightCurrentLine && globalLine == cursor.cursorLine && !selection.hasSelection) {
          float top = Math.round(getDrawLineTop(globalLine));
          float bottom = Math.round(getDrawLineBottom(globalLine));
          float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
          float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, lineNumber.currentLinePaint);
        }

        if (selection.hasSelection && selPaint != null) {
          float top = Math.round(getDrawLineTop(globalLine));
          float bottom = Math.round(getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth,  scroll.scrollX + (getWidth() - getTextStartX()));
          if (isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selection.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selection.selStartLine;
              boolean roundBottom = globalLine == selection.selEndLine;
              float leftSel = isRtl ? lineBaseX : 0f;
              float rightSel = isRtl ? (lineBaseX + lineWidth) : fullRight;
              drawSelectionSegment(
                  canvas,
                  leftSel,
                  top,
                  rightSel,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  selPaint);
            }
          } else {
            int startLine, endLine, startChar, endChar;
            if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) <= 0) {
              startLine = selection.selStartLine;
              startChar = selection.selStartChar;
              endLine = selection.selEndLine;
              endChar = selection.selEndChar;
            } else {
              startLine = selection.selEndLine;
              startChar = selection.selEndChar;
              endLine = selection.selStartLine;
              endChar = selection.selStartChar;
            }

            if (globalLine >= startLine && globalLine <= endLine) {
              float left, right;
              if (isRtl) {
                float lineLeft = lineBaseX;
                float lineRight = lineBaseX + lineWidth;
                if (startLine == endLine) {
                  float x1 =
                      getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  float x2 =
                      getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = Math.min(x1, x2);
                  right = Math.max(x1, x2);
                } else if (globalLine == startLine) {
                  float x =
                      getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  left = lineLeft;
                  right = x;
                } else if (globalLine == endLine) {
                  float x =
                      getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = x;
                  right = lineRight;
                } else {
                  left = lineLeft;
                  right = lineRight;
                }
              } else {
                if (startLine == endLine) {
                  left = measureText(line, Math.min(startChar, line.length()), globalLine);
                  right = measureText(line, Math.min(endChar, line.length()), globalLine);
                } else {
                  if (globalLine == startLine) {
                    left = measureText(line, Math.min(startChar, line.length()), globalLine);
                    right = fullRight;
                  } else if (globalLine == endLine) {
                    left = 0;
                    right = measureText(line, Math.min(endChar, line.length()), globalLine);
                    if (line.length() == 0) right = fullRight;
                  } else {
                    left = 0;
                    right = fullRight;
                  }
                }
              }
              if (right > left) {
                boolean isStart = globalLine == startLine;
                boolean isEnd = globalLine == endLine;
                boolean roundTop = isStart;
                boolean roundBottom = isEnd;
                if (!isStart && !isEnd) {
                  roundTop = false;
                  roundBottom = false;
                } else if (isStart && !isEnd) {
                  roundBottom = false;
                } else if (!isStart && isEnd) {
                  roundTop = false;
                }
                drawSelectionSegment(
                    canvas,
                    left,
                    top,
                    right,
                    bottom,
                    roundTop,
                    roundTop,
                    roundBottom,
                    roundBottom,
                    selPaint);
              }
            }
          }
        }

        float y = Math.round(getDrawLineTop(globalLine) + lineHeight - paint.descent());
        paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        float lineTop = Math.round(getDrawLineTop(globalLine));
        float lineBottom = Math.round(getDrawLineBottom(globalLine));
        drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (isBracketGuidesEnabled && drawDecorations) {
          List<BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
          drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          drawBracketMatchForLine(canvas, line, globalLine, bracketMatch);
        }
        canvas.restore();
      }
    }
    if (isFocused()
        && !isReadOnly
        && !selection.hasSelection
        && cursor.cursorLine >= firstVisibleLine
        && cursor.cursorLine <= lastVisibleLine
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursor.cursorLine))) {
      String cursorLineText = getLineTextForRender(cursor.cursorLine);
      int safeChar = Math.min(cursor.cursorChar, getLogicalLineLength(cursor.cursorLine, cursorLineText));
      float cursorX = getCaretXForLine(cursorLineText, cursor.cursorLine, safeChar);
      float cursorY = getDrawLineTop(cursor.cursorLine);
      cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
      float drawX = cursorAnimation.cursorDrawX;
      float drawY = cursorAnimation.cursorDrawY;
      if (isCursorVisible) {
        caretPaint.setColor(caretColor);
        caretPaint.setStrokeWidth(cursorWidth);
        canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
      }
      handlePaint.setColor(cursorHandleColor);
      drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
      cursorHandleRect.set(
          drawX - handleRadius,
          drawY + lineHeight,
          drawX + handleRadius,
          drawY + lineHeight + handleRadius * 2);
    }

    if (selection.hasSelection && !isReadOnly) {
      handlePaint.setColor(selectionHandleColor);
      if (selection.selStartLine >= firstVisibleLine
          && selection.selStartLine <= lastVisibleLine
          && (!isCodeFoldingEnabled || !isLineHiddenByFold(selection.selStartLine))) {
        String startLineText = getLineTextForRender(selection.selStartLine);
        float startX =
            getCaretXForLine(
                startLineText,
                selection.selStartLine,
                Math.min(selection.selStartChar, getLogicalLineLength(selection.selStartLine, startLineText)));
        float startY = getDrawLineTop(selection.selStartLine) + lineHeight;
        drawTeardropHandle(canvas, startX, startY, handlePaint);
        if (isRtl) {
          rightHandleRect.set(
              startX - handleRadius, startY, startX + handleRadius, startY + handleRadius * 2);
        } else {
          leftHandleRect.set(
              startX - handleRadius, startY, startX + handleRadius, startY + handleRadius * 2);
        }
      } else {
        if (isRtl) rightHandleRect.setEmpty();
        else leftHandleRect.setEmpty();
      }
      if (selection.selEndLine >= firstVisibleLine
          && selection.selEndLine <= lastVisibleLine
          && (!isCodeFoldingEnabled || !isLineHiddenByFold(selection.selEndLine))) {
        String endLineText = getLineTextForRender(selection.selEndLine);
        float endX =
            getCaretXForLine(
                endLineText,
                selection.selEndLine,
                Math.min(selection.selEndChar, getLogicalLineLength(selection.selEndLine, endLineText)));
        float endY = getDrawLineTop(selection.selEndLine) + lineHeight;
        drawTeardropHandle(canvas, endX, endY, handlePaint);
        if (isRtl) {
          leftHandleRect.set(
              endX - handleRadius, endY, endX + handleRadius, endY + handleRadius * 2);
        } else {
          rightHandleRect.set(
              endX - handleRadius, endY, endX + handleRadius, endY + handleRadius * 2);
        }
      } else {
        if (isRtl) leftHandleRect.setEmpty();
        else rightHandleRect.setEmpty();
      }
    }

    canvas.restore();
    // --- End of main text content drawing ---

    // --- 4. Draw overlays ---

    popup.drawPopup(canvas);

    if (showLoadingCircle) {

      loadingCirclePaint.setColor(loadingCircleColor);
      loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircleRotation, centerX, centerY);
      loadingCircleRect.set(
          centerX - loadingCircleRadius,
          centerY - loadingCircleRadius,
          centerX + loadingCircleRadius,
          centerY + loadingCircleRadius);
      canvas.drawArc(loadingCircleRect, 0, 270, false, loadingCirclePaint);
      canvas.restore();
    }
  }

  public void drawContentWrapped(Canvas canvas) {
    int wrapWidthPx = Math.max(1, Math.round(getWrapWidth()));
    final boolean drawDecorations = zoom.shouldDrawDecorations();
    if (!zoom.isZoomGestureActive()) {
      applyPendingWrapPrefixUpdateIfAny();
    }
    if (shouldSuppressWrapMetricsForFastSelectAll()) {
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    if (!isWrapMetricsUsableForWindow(wrapWidthPx)) {
      if (!wrapMetricsReady || wrapMetricsWidth != wrapWidthPx) {
        scheduleWrapMetricsSnapshotIfNeeded(wrapWidthPx);
      }
      if (wrapPrefixValidUpToLine < getWindowEndLine()) {
        requestWrapPrefixRebuild();
      }
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
    if (totalLines <= 0) totalLines = 1;

    int totalVisual = getTotalVisualLineCount();
    int firstVisualIndex = Math.max(0, (int) ( scroll.scrollY / lineHeight));
    int lastVisualIndex =
        Math.min(totalVisual - 1, firstVisualIndex + (int) Math.ceil(getHeight() / lineHeight) + 5);
    if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

    VisualLinePosition firstPos = getVisualPositionForIndex(firstVisualIndex);
    VisualLinePosition lastPos = getVisualPositionForIndex(lastVisualIndex);

    maybeKickWindowLoad(firstPos.line);

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      directLinesTmp.clear();
      directLines = directLinesTmp;
      int rangeStart = Math.max(0, firstPos.line - 1);
      int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
      populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
    }

    // Safety: after zoom/fast scroll, wrapLineCounts might be stale for some visible lines.
    // Don't patch during pinch/scale: it can fight the zoom's own scroll math and cause a brief
    // "jump".
    boolean patched = false;
    if (!zoom.isZoomGestureActive()) {
      patched =
          patchWrapMetricsForVisualRange(
              firstVisualIndex, lastVisualIndex, directLines, wrapWidthPx);
    }
    if (patched) {
      totalLines = getLinesCount();
      if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
      if (totalLines <= 0) totalLines = 1;

      totalVisual = getTotalVisualLineCount();
      firstVisualIndex = Math.max(0, (int) ( scroll.scrollY / lineHeight));
      lastVisualIndex =
          Math.min(
              totalVisual - 1, firstVisualIndex + (int) Math.ceil(getHeight() / lineHeight) + 5);
      if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

      firstPos = getVisualPositionForIndex(firstVisualIndex);
      lastPos = getVisualPositionForIndex(lastVisualIndex);
      maybeKickWindowLoad(firstPos.line);

      if (directLines != null) {
        directLinesTmp.clear();
        int rangeStart = Math.max(0, firstPos.line - 1);
        int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
        populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
      }
    }

    float baseY = firstVisualIndex * lineHeight;
    float translateY = - scroll.scrollY + baseY;

    // --- 1. Draw fixed gutter background ---
    if (lineNumber.showLineNumbers) {
      canvas.drawRect(
          lineNumber.getGutterStartX(),
          0,
          lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth,
          getHeight(),
          lineNumber.gutterPaint);

      float separatorLeft;
      if (isRtl) {
        separatorLeft = lineNumber.getGutterStartX();
      } else {
        separatorLeft = lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumber.gutterSeparatorWidth,
          getHeight(),
          lineNumber.gutterSeparatorPaint);
    }

    if (lineNumber.highlightCurrentLineInGutter
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursor.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, 0);
      String cursorLineText = getLineTextForRender(cursor.cursorLine);
      int[] starts = getWrapStartsForLine(cursor.cursorLine, cursorLineText);
      int segCount = Math.max(1, starts.length);
      int lastVisualIndexForLine = currentVisualIndex + segCount - 1;
      int drawFrom = Math.max(firstVisualIndex, currentVisualIndex);
      int drawTo = Math.min(lastVisualIndex, lastVisualIndexForLine);
      for (int v = drawFrom; v <= drawTo; v++) {
        float top = Math.round(v * lineHeight -  scroll.scrollY);
        float bottom = top + lineHeight;
        lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (lineNumber.showLineNumbers) {
      drawlineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), translateY);
    if (zoom.pinchVisualZoomActive) {
      float pivotX = zoom.pinchFocusX - (getTextStartX() - getEffectiveScrollX());
      float pivotY = zoom.pinchFocusY - translateY;
      canvas.scale(zoom.pinchVisualScale, zoom.pinchVisualScale, pivotX, pivotY);
    }

    Paint selPaint = null;
    if (selection.hasSelection) {
      selectionPaint.setColor(selectionHighlightColor);
      selPaint = selectionPaint;
    }

    int startLine = selection.selStartLine;
    int startChar = selection.selStartChar;
    int endLine = selection.selEndLine;
    int endChar = selection.selEndChar;
    if (selection.hasSelection && comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
      startLine = selection.selEndLine;
      startChar = selection.selEndChar;
      endLine = selection.selStartLine;
      endChar = selection.selStartChar;
    }

    for (int v = firstVisualIndex; v <= lastVisualIndex; v++) {
      VisualLinePosition pos = getVisualPositionForIndex(v);
      String line = getLineTextForRenderWithDirect(pos.line, directLines);
      int[] starts = getWrapStartsForLine(pos.line, line);

      // Skip if visual segment index is invalid for the current wrap state (e.g. during zoom)
      if (pos.segment >= starts.length) continue;

      int segStart = getWrapSegmentStart(starts, pos.segment);
      int segEnd = getWrapSegmentEnd(starts, pos.segment, line.length());
      float segBaseX = isRtl ? getRtlSegmentBaseX(line, pos.line, segStart, segEnd) : 0f;

      float top = Math.round((v - firstVisualIndex) * lineHeight);
      float bottom = top + lineHeight;
      float y = Math.round(top + lineHeight - paint.descent());

      if (highlightCurrentLine && pos.line == cursor.cursorLine && !selection.hasSelection) {
        canvas.drawRect(
            -paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, lineNumber.currentLinePaint);
      }

      if (selection.hasSelection && selPaint != null) {
        if (pos.line >= startLine && pos.line <= endLine) {
          int lineSelStart = (pos.line == startLine) ? startChar : 0;
          int lineSelEnd = (pos.line == endLine) ? endChar : line.length();
          int segSelStart = Math.max(segStart, lineSelStart);
          int segSelEnd = Math.min(segEnd, lineSelEnd);
          if (segSelEnd > segSelStart) {
            float left;
            float right;
            if (isRtl) {
              float x1 =
                  getCaretXForSegment(
                      line, pos.line, segStart, segEnd, Math.min(segSelStart, line.length()));
              float x2 =
                  getCaretXForSegment(
                      line, pos.line, segStart, segEnd, Math.min(segSelEnd, line.length()));
              left = Math.min(x1, x2);
              right = Math.max(x1, x2);
            } else {
              boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
              float leftRel =
                  fullSegmentSelected
                      ? 0f
                      : measureTextWithVisualSpaces(line, segStart, segSelStart, paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wrapWidthPx)
                      : leftRel + measureTextWithVisualSpaces(line, segSelStart, segSelEnd, paint);
              left = leftRel + segBaseX;
              right = rightRel + segBaseX;
            }
            boolean roundTop = (pos.line == startLine && segSelStart == startChar);
            boolean roundBottom = (pos.line == endLine && segSelEnd == endChar);
            drawSelectionSegment(
                canvas,
                left,
                top,
                right,
                bottom,
                roundTop,
                roundTop,
                roundBottom,
                roundBottom,
                selPaint);
          }
        }
      }

      int segDrawEnd = segEnd;
      if (isWordWrapIndicatorEnabled && segEnd < line.length()) {
        segDrawEnd = clampSegmentEndForWrapIndicator(line, segStart, segEnd, wrapWidthPx);
      }
      canvas.save();
      if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
      drawSearchHighlightsForSegment(canvas, line, pos.line, segStart, segDrawEnd, top, bottom);
      drawHighlightedLineSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      drawErrorUnderlinesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      drawDeleteAnimationForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      if (drawDecorations) {
        drawWhitespaceGuidesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      }
      drawAutoSuggestionWrapped(canvas, line, pos.line, segStart, segDrawEnd, v, y);
      if (isWordWrapIndicatorEnabled && segEnd < line.length()) {
        float indicatorX =
            isRtl
                ? wordWrapIndicatorPadPx
                : Math.max(
                    wordWrapIndicatorPadPx,
                    wrapWidthPx - wordWrapIndicatorWidth - wordWrapIndicatorPadPx);
        canvas.drawText(WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrapIndicatorPaint);
      }
      canvas.restore();
    }

    if (isFocused() && !isReadOnly && !selection.hasSelection) {
      int cursorVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
      if (cursorVisualIndex >= firstVisualIndex && cursorVisualIndex <= lastVisualIndex) {
        String cursorLineText = getLineTextForRenderWithDirect(cursor.cursorLine, directLines);
        int[] starts = getWrapStartsForLine(cursor.cursorLine, cursorLineText);
        int seg = getWrapSegmentIndexForChar(starts, cursor.cursorChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, cursorLineText.length());
        int safeChar = Math.min(cursor.cursorChar, cursorLineText.length());
        float cursorX = getCaretXForSegment(cursorLineText, cursor.cursorLine, segStart, segEnd, safeChar);
        float cursorY = (cursorVisualIndex - firstVisualIndex) * lineHeight;
        cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
        float drawX = cursorAnimation.cursorDrawX;
        float drawY = cursorAnimation.cursorDrawY;
        if (isCursorVisible) {
          caretPaint.setColor(caretColor);
          caretPaint.setStrokeWidth(cursorWidth);
          canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
        }
        handlePaint.setColor(cursorHandleColor);
        drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
        cursorHandleRect.set(
            drawX - handleRadius,
            drawY + lineHeight,
            drawX + handleRadius,
            drawY + lineHeight + handleRadius * 2);
      } else {
        cursorHandleRect.setEmpty();
      }
    }

    if (selection.hasSelection) {
      handlePaint.setColor(selectionHandleColor);
      int startVisual = getVisualIndexForLineAndChar(selection.selStartLine, selection.selStartChar);
      if (startVisual >= firstVisualIndex && startVisual <= lastVisualIndex) {
        String startLineText = getLineTextForRenderWithDirect(selection.selStartLine, directLines);
        int[] starts = getWrapStartsForLine(selection.selStartLine, startLineText);
        int seg = getWrapSegmentIndexForChar(starts, selection.selStartChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            getCaretXForSegment(
                startLineText,
                selection.selStartLine,
                segStart,
                segEnd,
                Math.min(selection.selStartChar, startLineText.length()));
        float y = (startVisual - firstVisualIndex) * lineHeight + lineHeight;
        drawTeardropHandle(canvas, x, y, handlePaint);
        if (isRtl) {
          rightHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        } else {
          leftHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        }
      } else {
        if (isRtl) rightHandleRect.setEmpty();
        else leftHandleRect.setEmpty();
      }
      int endVisual = getVisualIndexForLineAndChar(selection.selEndLine, selection.selEndChar);
      if (endVisual >= firstVisualIndex && endVisual <= lastVisualIndex) {
        String endLineText = getLineTextForRenderWithDirect(selection.selEndLine, directLines);
        int[] starts = getWrapStartsForLine(selection.selEndLine, endLineText);
        int seg = getWrapSegmentIndexForChar(starts, selection.selEndChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            getCaretXForSegment(
                endLineText,
                selection.selEndLine,
                segStart,
                segEnd,
                Math.min(selection.selEndChar, endLineText.length()));
        float y = (endVisual - firstVisualIndex) * lineHeight + lineHeight;
        drawTeardropHandle(canvas, x, y, handlePaint);
        if (isRtl) {
          leftHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        } else {
          rightHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
        }
      } else {
        if (isRtl) leftHandleRect.setEmpty();
        else rightHandleRect.setEmpty();
      }
    }

    canvas.restore();

    if (showLoadingCircle) {
      loadingCirclePaint.setColor(loadingCircleColor);
      loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircleRotation, centerX, centerY);
      loadingCircleRect.set(
          centerX - loadingCircleRadius,
          centerY - loadingCircleRadius,
          centerX + loadingCircleRadius,
          centerY + loadingCircleRadius);
      canvas.drawArc(loadingCircleRect, 0, 270, false, loadingCirclePaint);
      canvas.restore();
    }
  }

  public void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
    int firstIndex = Math.max(0, (int) ( scroll.scrollY / lineHeight));
    int lastIndex = firstIndex + (int) Math.ceil(getHeight() / lineHeight) + 5;
    final boolean drawDecorations = zoom.shouldDrawDecorations();

    int firstLine = firstIndex;
    int lastLine = lastIndex;
    if (isCodeFoldingEnabled) {
      int visibleCount = getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstIndex = Math.max(0, Math.min(firstIndex, visibleCount - 1));
      lastIndex = Math.max(firstIndex, Math.min(lastIndex, visibleCount - 1));
      firstLine = mapVisibleIndexToGlobal(firstIndex);
      lastLine = mapVisibleIndexToGlobal(lastIndex);
    }

    maybeKickWindowLoad(firstLine);

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      directLinesTmp.clear();
      directLines = directLinesTmp;
      populateDirectLinesForRange(firstLine, lastLine, directLines);
    }

    float baseY = firstIndex * lineHeight;
    float translateY = - scroll.scrollY + baseY;

    // Draw gutter background
    if (lineNumber.showLineNumbers) {
      canvas.drawRect(
          lineNumber.getGutterStartX(),
          0,
          lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth,
          getHeight(),
          lineNumber.gutterPaint);
      float separatorLeft =
          isRtl
              ? lineNumber.getGutterStartX()
              : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.gutterSeparatorWidth;
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumber.gutterSeparatorWidth,
          getHeight(),
          lineNumber.gutterSeparatorPaint);
    }

    if (lineNumber.highlightCurrentLineInGutter
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursor.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, 0);
      if (currentVisualIndex >= firstIndex && currentVisualIndex <= lastIndex) {
        float top = Math.round(currentVisualIndex * lineHeight -  scroll.scrollY);
        float bottom = top + lineHeight;
        lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // Disable line number cache in fallback because it relies on global metrics which are likely
    // invalid here.
    // This prevents "counts wrapped line as a separate line" visual bug.
    boolean uselineNumberCache = false;

    canvas.save();
    // Translate for scrolling content
    canvas.translate(0, translateY);

    // Pre-calculate line number X position
    float lineNumX = 0f;
    if (lineNumber.showLineNumbers && !uselineNumberCache) {
      lineNumX =
          isRtl
              ? lineNumber.getGutterStartX() + lineNumber.GUTTER_TEXT_PADDING
              : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.GUTTER_TEXT_PADDING;
    }

    // Prepare text clipping
    int saveCount = canvas.save();
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), 0); // already translated by translateY

    Paint selPaint = null;
    if (selection.hasSelection) {
      selectionPaint.setColor(selectionHighlightColor);
      selPaint = selectionPaint;
    }

    int startLine = selection.selStartLine;
    int startChar = selection.selStartChar;
    int endLine = selection.selEndLine;
    int endChar = selection.selEndChar;
    if (selection.hasSelection && comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
      startLine = selection.selEndLine;
      startChar = selection.selEndChar;
      endLine = selection.selStartLine;
      endChar = selection.selStartChar;
    }

    int visualIndex = firstIndex;
    float yOffset = 0f;
    boolean cursorDrawn = false;
    int startHandleVisual = -1;
    int endHandleVisual = -1;

    for (int line = firstLine; line <= lastLine; line++) {
      if (yOffset > getHeight() + lineHeight) break;
      String text = getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(line, text);

      for (int seg = 0; seg < starts.length; seg++) {
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, text.length());
        float segBaseX = isRtl ? getRtlSegmentBaseX(text, line, segStart, segEnd) : 0f;

        float top = Math.round(yOffset);
        float bottom = top + lineHeight;
        float y = Math.round(top + lineHeight - paint.descent());

        // Draw line number ONLY for the first segment of the wrapped line
        if (lineNumber.showLineNumbers && seg == 0 && !uselineNumberCache) {
          canvas.restore(); // Exit text clip
          int start = writeIntToChars(line + 1, lineNumber.lineNumberChars);
          int count = lineNumber.lineNumberChars.length - start;
          if (line == cursor.cursorLine) {
            int originalColor = lineNumber.lineNumbersPaint.getColor();
            lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
            canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
            lineNumber.lineNumbersPaint.setColor(originalColor);
          } else {
            canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
          }
          canvas.save(); // Re-enter text clip
          if (isRtl) {
            canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
          } else {
            canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
          }
          canvas.translate(getTextStartX() - getEffectiveScrollX(), 0);
        }

        if (highlightCurrentLine && line == cursor.cursorLine && !selection.hasSelection) {
          canvas.drawRect(
              -paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, lineNumber.currentLinePaint);
        }

        if (selection.hasSelection && selPaint != null) {
          if (line >= startLine && line <= endLine) {
            int lineSelStart = (line == startLine) ? startChar : 0;
            int lineSelEnd = (line == endLine) ? endChar : text.length();
            int segSelStart = Math.max(segStart, lineSelStart);
            int segSelEnd = Math.min(segEnd, lineSelEnd);
            if (segSelEnd > segSelStart) {
              boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
              float leftRel =
                  fullSegmentSelected
                      ? 0f
                      : measureTextWithVisualSpaces(text, segStart, segSelStart, paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wrapWidthPx)
                      : leftRel + measureTextWithVisualSpaces(text, segSelStart, segSelEnd, paint);
              float left = leftRel + segBaseX;
              float right = rightRel + segBaseX;
              boolean roundTop = (line == startLine && segSelStart == startChar);
              boolean roundBottom = (line == endLine && segSelEnd == endChar);
              drawSelectionSegment(
                  canvas,
                  left,
                  top,
                  right,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  selPaint);
            }
          }
        }

        int segDrawEnd = segEnd;
        if (isWordWrapIndicatorEnabled && segEnd < text.length()) {
          segDrawEnd = clampSegmentEndForWrapIndicator(text, segStart, segEnd, wrapWidthPx);
        }
        canvas.save();
        if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
        drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
        drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForSegment(canvas, text, line, segStart, segDrawEnd, y);
        }
        drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, visualIndex, y);
        if (isWordWrapIndicatorEnabled && segEnd < text.length()) {
          float indicatorX =
              isRtl
                  ? wordWrapIndicatorPadPx
                  : Math.max(
                      wordWrapIndicatorPadPx,
                      wrapWidthPx - wordWrapIndicatorWidth - wordWrapIndicatorPadPx);
          canvas.drawText(WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrapIndicatorPaint);
        }
        canvas.restore();

        if (!cursorDrawn && isFocused() && !selection.hasSelection && line == cursor.cursorLine) {
          int cursorSeg = getWrapSegmentIndexForChar(starts, cursor.cursorChar);
          if (cursorSeg == seg) {
            int safeChar = Math.min(cursor.cursorChar, text.length());
            float cursorX = getCaretXForSegment(text, line, segStart, segEnd, safeChar);
            float cursorY = top;
            cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
            float drawX = cursorAnimation.cursorDrawX;
            float drawY = cursorAnimation.cursorDrawY;
            if (isCursorVisible) {
              caretPaint.setColor(caretColor);
              caretPaint.setStrokeWidth(cursorWidth);
              canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
            }
            handlePaint.setColor(cursorHandleColor);
            drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
            cursorHandleRect.set(
                drawX - handleRadius,
                drawY + lineHeight,
                drawX + handleRadius,
                drawY + lineHeight + handleRadius * 2);
            cursorDrawn = true;
          }
        }

        if (selection.hasSelection) {
          if (line == selection.selStartLine) {
            int selSeg = getWrapSegmentIndexForChar(starts, selection.selStartChar);
            if (selSeg == seg) startHandleVisual = visualIndex;
          }
          if (line == selection.selEndLine) {
            int selSeg = getWrapSegmentIndexForChar(starts, selection.selEndChar);
            if (selSeg == seg) endHandleVisual = visualIndex;
          }
        }

        yOffset += lineHeight;
        visualIndex++;
        if (yOffset > getHeight() + lineHeight) break;
      }
    }

    canvas.restore(); // Restore from text clip
    canvas.restore(); // Restore from translation

    if (selection.hasSelection) {
      handlePaint.setColor(selectionHandleColor);
      if (startHandleVisual >= firstIndex && startHandleVisual <= visualIndex - 1) {
        String startLineText = getLineTextForRenderWithDirect(selection.selStartLine, directLines);
        int[] starts = getWrapStartsForLine(selection.selStartLine, startLineText);
        int seg = getWrapSegmentIndexForChar(starts, selection.selStartChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            getCaretXForSegment(
                startLineText,
                selection.selStartLine,
                segStart,
                segEnd,
                Math.min(selection.selStartChar, startLineText.length()));
        float y = (startHandleVisual - firstIndex) * lineHeight + lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, handlePaint);
        leftHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
      } else {
        leftHandleRect.setEmpty();
      }

      if (endHandleVisual >= firstIndex && endHandleVisual <= visualIndex - 1) {
        String endLineText = getLineTextForRenderWithDirect(selection.selEndLine, directLines);
        int[] starts = getWrapStartsForLine(selection.selEndLine, endLineText);
        int seg = getWrapSegmentIndexForChar(starts, selection.selEndChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            getCaretXForSegment(
                endLineText,
                selection.selEndLine,
                segStart,
                segEnd,
                Math.min(selection.selEndChar, endLineText.length()));
        float y = (endHandleVisual - firstIndex) * lineHeight + lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, handlePaint);
        rightHandleRect.set(x - handleRadius, y, x + handleRadius, y + handleRadius * 2);
      } else {
        rightHandleRect.setEmpty();
      }
    }

    if (showLoadingCircle) {
      loadingCirclePaint.setColor(loadingCircleColor);
      loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircleRotation, centerX, centerY);
      loadingCircleRect.set(
          centerX - loadingCircleRadius,
          centerY - loadingCircleRadius,
          centerX + loadingCircleRadius,
          centerY + loadingCircleRadius);
      canvas.drawArc(loadingCircleRect, 0, 270, false, loadingCirclePaint);
      canvas.restore();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    drawEditorBackground(canvas);
    if (scroll.stretch.stretchOverscrollEnabled && (scroll.stretch.stretchX != 0f || scroll.stretch.stretchY != 0f)) {
      float sx = 1f + (scroll.stretch.stretchX * 0.12f * scroll.stretch.stretchOverscrollStrength);
      float sy = 1f + (scroll.stretch.stretchY * 0.12f * scroll.stretch.stretchOverscrollStrength);
      float pivotX = (scroll.stretch.stretchDirX < 0) ? 0f : (scroll.stretch.stretchDirX > 0 ? getWidth() : getWidth() * 0.5f);
      float pivotY = (scroll.stretch.stretchDirY < 0) ? 0f : (scroll.stretch.stretchDirY > 0 ? getHeight() : getHeight() * 0.5f);
      canvas.save();
      canvas.scale(sx, sy, pivotX, pivotY);
      drawContent(canvas);
      canvas.restore();
    } else {
      drawContent(canvas);
    }
    scroll.drawStretch(canvas);
    scroll.drawScrollBar(canvas);
  }

  @Override
  public void computeScroll() {
    scroll.computeScroll();
  }

  public void drawEditorBackground(Canvas canvas) {
    if (hasEditorBackgroundColor) {
      canvas.drawColor(editorBackgroundColor);
    }
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundDst.set(0, 0, getWidth(), getHeight());
      canvas.drawBitmap(editorBackgroundBitmap, null, editorBackgroundDst, null);
    }
  }

  public Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
    List<HighlightSpan> spans = highlightCache.get(lineIndex);
    if (spans == null) {
      spans = calculateSpansForLine(lineText, lineIndex);
      highlightCache.put(lineIndex, spans);
    }
    for (HighlightSpan span : spans) {
      if (charIndex >= span.start && charIndex < span.end) {
        return span.paint;
      }
    }
    return paint;
  }

  public float getAverageCharWidthForLine(String line, int lineIndex) {
    if (line == null || line.isEmpty()) return paint.measureText(" ");
    if (lineIndex >= 0) {
      synchronized (avgCharWidthCache) {
        Float cached = avgCharWidthCache.get(lineIndex);
        if (cached != null) return cached;
      }
    }
    int sampleLen = Math.min(line.length(), 256);
    float w = (sampleLen > 0) ? paint.measureText(line, 0, sampleLen) : paint.measureText(" ");
    float avg = (sampleLen > 0) ? (w / sampleLen) : w;
    if (lineIndex >= 0) {
      synchronized (avgCharWidthCache) {
        if (isStableGlyphPositionsEnabled && avgCharWidthCache.containsKey(lineIndex)) {
          return avgCharWidthCache.get(lineIndex);
        }
        avgCharWidthCache.put(lineIndex, avg);
      }
    }
    return avg;
  }

  public void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
    if (line.isEmpty()) {
      if (globalLine == charAnimation.delAnimLine
          && charAnimation.delAnimText != null
          && !charAnimation.delAnimText.isEmpty()
          && charAnimation.delAnimAlpha > 0f) {
        Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
        charAnimation.charAnimTmpPaint.set(ghostPaint);
        charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
        canvas.drawText(charAnimation.delAnimText, 0f, y, charAnimation.charAnimTmpPaint);
      }
      return;
    }

    getVisibleCharRangeForLine(line, globalLine, visibleCharRangeTmp);
    int visibleStart = visibleCharRangeTmp[0];
    int visibleEnd = visibleCharRangeTmp[1];
    int len = getLogicalLineLength(globalLine, line);
    if (len > maxSyntaxLineLength) {
      if (visibleEnd > visibleStart) {
        int sliceStart = getStreamedLineSliceStart(globalLine);
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
    if (visibleStart > 0 || visibleEnd < len) {
      drawHighlightedLineRange(canvas, line, globalLine, visibleStart, visibleEnd, y);
      return;
    }

    List<UnderlineSpan> combinedUnderlines = new ArrayList<>();

    // Fetch URL underlines
    if (isUrlUnderliningEnabled && urlUnderlinePattern != null) {
      List<UnderlineSpan> urlSpans = urlUnderlineCache.get(globalLine);
      if (urlSpans == null) {
        urlSpans = new ArrayList<>();
        Matcher m = urlUnderlinePattern.matcher(line);
        while (m.find()) {
          int start = m.start();
          int end = m.end();
          end = trimUrlUnderlineEnd(line, start, end);
          if (end > start) {
            urlSpans.add(new UnderlineSpan(start, end, false));
          }
        }
        urlUnderlineCache.put(globalLine, urlSpans);
      }
      combinedUnderlines.addAll(urlSpans);
    }

    // Fetch Path underlines
    if (isPathUnderliningEnabled && pathUnderlinePattern != null) {
      List<UnderlineSpan> pathSpans = pathUnderlineCache.get(globalLine);
      if (pathSpans == null) {
        pathSpans = new ArrayList<>();
        Matcher m = pathUnderlinePattern.matcher(line);
        while (m.find()) {
          String potentialPath = m.group();
          if (potentialPath != null && !potentialPath.isEmpty()) {
            Boolean exists = pathValidationCache.get(potentialPath);
            if (Boolean.TRUE.equals(exists)) {
              pathSpans.add(new UnderlineSpan(m.start(), m.end(), true));
            } else if (exists == null) {
              validatePathInBackground(potentialPath, globalLine);
            }
          }
        }
        pathUnderlineCache.put(globalLine, pathSpans);
      }
      combinedUnderlines.addAll(pathSpans);
    }

    // Sort combined underlines by start position
    if (!combinedUnderlines.isEmpty()) {
      Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (globalLine == charAnimation.charAnimLine
        && charAnimation.charAnimEndChar > charAnimation.charAnimStartChar
        && charAnimation.charAnimAlpha < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimation.charAnimStartChar, line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimation.charAnimEndChar, line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimation.charAnimAlpha));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = getDrawLineTop(globalLine);
    float lineBottom = lineTop + lineHeight;

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          0,
          line.length(),
          0f,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
      if (globalLine == charAnimation.delAnimLine
          && charAnimation.delAnimText != null
          && !charAnimation.delAnimText.isEmpty()
          && charAnimation.delAnimAlpha > 0f) {
        int at = Math.max(0, Math.min(charAnimation.delAnimAtChar, line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
        charAnimation.charAnimTmpPaint.set(ghostPaint);
        charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
        canvas.drawText(charAnimation.delAnimText, x, y, charAnimation.charAnimTmpPaint);
      }
      drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          0,
          line.length(),
          0f,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
      if (globalLine == charAnimation.delAnimLine
          && charAnimation.delAnimText != null
          && !charAnimation.delAnimText.isEmpty()
          && charAnimation.delAnimAlpha > 0f) {
        int at = Math.max(0, Math.min(charAnimation.delAnimAtChar, line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
        charAnimation.charAnimTmpPaint.set(ghostPaint);
        charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
        canvas.drawText(charAnimation.delAnimText, x, y, charAnimation.charAnimTmpPaint);
      }
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
        currentX +=
            drawTextSegmentWithFadeAndUnderlines(
                canvas,
                line,
                lastEnd,
                span.start,
                currentX,
                y,
                paint,
                fadeStart,
                fadeEnd,
                fadeAlpha,
                combinedUnderlines,
                lineTop,
                lineBottom);
      }

      currentX +=
          drawTextSegmentWithFadeAndUnderlines(
              canvas,
              line,
              span.start,
              safeSpanEnd,
              currentX,
              y,
              span.paint,
              fadeStart,
              fadeEnd,
              fadeAlpha,
              combinedUnderlines,
              lineTop,
              lineBottom);
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < line.length()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          lastEnd,
          line.length(),
          currentX,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
    }

    if (globalLine == charAnimation.delAnimLine
        && charAnimation.delAnimText != null
        && !charAnimation.delAnimText.isEmpty()
        && charAnimation.delAnimAlpha > 0f) {
      int at = Math.max(0, Math.min(charAnimation.delAnimAtChar, line.length()));
      float x = measureText(line, at, globalLine);
      Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
      charAnimation.charAnimTmpPaint.set(ghostPaint);
      charAnimation.charAnimTmpPaint.setUnderlineText(false);
      int baseAlpha = ghostPaint.getAlpha();
      charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
      canvas.drawText(charAnimation.delAnimText, x, y, charAnimation.charAnimTmpPaint);
    }
    drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
  }

  public void drawHighlightedLineRange(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (line == null || line.isEmpty()) return;
    int len = line.length();
    start = Math.max(0, Math.min(start, len));
    end = Math.max(start, Math.min(end, len));
    if (start >= end) return;

    List<UnderlineSpan> combinedUnderlines = new ArrayList<>();
    if (isUrlUnderliningEnabled && urlUnderlinePattern != null) {
      List<UnderlineSpan> urlSpans = urlUnderlineCache.get(globalLine);
      if (urlSpans == null) {
        urlSpans = new ArrayList<>();
        Matcher m = urlUnderlinePattern.matcher(line);
        while (m.find()) {
          int s = m.start();
          int e = trimUrlUnderlineEnd(line, s, m.end());
          if (e > s) {
            urlSpans.add(new UnderlineSpan(s, e, false));
          }
        }
        urlUnderlineCache.put(globalLine, urlSpans);
      }
      combinedUnderlines.addAll(urlSpans);
    }
    if (isPathUnderliningEnabled && pathUnderlinePattern != null) {
      List<UnderlineSpan> pathSpans = pathUnderlineCache.get(globalLine);
      if (pathSpans == null) {
        pathSpans = new ArrayList<>();
        Matcher m = pathUnderlinePattern.matcher(line);
        while (m.find()) {
          String potentialPath = m.group();
          if (potentialPath != null && !potentialPath.isEmpty()) {
            Boolean exists = pathValidationCache.get(potentialPath);
            if (Boolean.TRUE.equals(exists)) {
              pathSpans.add(new UnderlineSpan(m.start(), m.end(), true));
            } else if (exists == null) {
              validatePathInBackground(potentialPath, globalLine);
            }
          }
        }
        pathUnderlineCache.put(globalLine, pathSpans);
      }
      combinedUnderlines.addAll(pathSpans);
    }
    if (!combinedUnderlines.isEmpty()) {
      Collections.sort(combinedUnderlines, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (charAnimation.isCharAnimationEnabled
        && globalLine == charAnimation.charAnimLine
        && charAnimation.charAnimEndChar > charAnimation.charAnimStartChar
        && charAnimation.charAnimAlpha < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimation.charAnimStartChar, line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimation.charAnimEndChar, line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimation.charAnimAlpha));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = getDrawLineTop(globalLine);
    float lineBottom = lineTop + lineHeight;
    float currentX = measureText(line, start, globalLine);
    int lastEnd = start;

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          start,
          end,
          currentX,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          combinedUnderlines,
          lineTop,
          lineBottom);
    } else {
      List<HighlightSpan> spans = highlightCache.get(globalLine);
      if (spans == null) {
        spans = calculateSpansForLine(line, globalLine);
        highlightCache.put(globalLine, spans);
      }
      for (HighlightSpan span : spans) {
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  combinedUnderlines,
                  lineTop,
                  lineBottom);
        }
        if (segEnd > segStart) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  combinedUnderlines,
                  lineTop,
                  lineBottom);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
      if (lastEnd < end) {
        drawTextSegmentWithFadeAndUnderlines(
            canvas,
            line,
            lastEnd,
            end,
            currentX,
            y,
            paint,
            fadeStart,
            fadeEnd,
            fadeAlpha,
            combinedUnderlines,
            lineTop,
            lineBottom);
      }
    }

    if (charAnimation.isCharAnimationEnabled
        && globalLine == charAnimation.delAnimLine
        && charAnimation.delAnimText != null
        && !charAnimation.delAnimText.isEmpty()
        && charAnimation.delAnimAlpha > 0f) {
      int at = Math.max(0, Math.min(charAnimation.delAnimAtChar, line.length()));
      if (at >= start && at <= end) {
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
        charAnimation.charAnimTmpPaint.set(ghostPaint);
        charAnimation.charAnimTmpPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
        canvas.drawText(charAnimation.delAnimText, x, y, charAnimation.charAnimTmpPaint);
      }
    }
    drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
  }

  public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
    if (line == null || out == null || out.length < 2) return;
    int len = getLogicalLineLength(globalLine, line);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    if (len > maxSyntaxLineLength) {
      getVisibleCharRangeForLineFast(line, globalLine, len, out);
      return;
    }
    if (isStableGlyphPositionsEnabled) {
      out[0] = 0;
      out[1] = len;
      return;
    }
    float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
    float leftX = viewLeft + getEffectiveScrollX() - getTextStartX();
    float rightX = viewRight + getEffectiveScrollX() - getTextStartX();

    int start = getCharIndexForX(line, leftX, globalLine);
    int end = getCharIndexForX(line, rightX, globalLine);
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

  public void getVisibleCharRangeForLineFast(
      String line, int globalLine, int lineLength, int[] out) {
    int len = Math.max(0, lineLength);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    float avg = getAverageCharWidthForLine(line, globalLine);
    if (avg <= 0f) {
      out[0] = 0;
      out[1] = Math.min(len, Math.max(0, prefetchCols));
      return;
    }
    float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
    float leftX = viewLeft + getEffectiveScrollX() - getTextStartX();
    float rightX = viewRight + getEffectiveScrollX() - getTextStartX();
    if (isRtl) {
      float w = avg * len;
      float baseX = getTextAreaWidth() - w;
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
    int pad = visibleCharPadding + Math.max(0, prefetchCols);
    start = Math.max(0, start - pad);
    end = Math.min(len, end + pad);
    out[0] = start;
    out[1] = end;
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
    if (avg <= 0f) avg = paint.measureText(" ");
    float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
    float leftX = viewLeft + getEffectiveScrollX() - getTextStartX();
    float rightX = viewRight + getEffectiveScrollX() - getTextStartX();
    if (isRtl) {
      float w = avg * len;
      float baseX = getTextAreaWidth() - w;
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
    int maxExtra = Math.max(0, colsWidthCacheSize - visibleLen);
    int extraPad = Math.min(Math.max(0, prefetchCols), maxExtra / 2);
    start = Math.max(0, start - extraPad);
    end = Math.min(len, end + extraPad);
    out[0] = start;
    out[1] = end;
  }

  public int getInitialStreamedSliceSize() {
    int base = Math.max(128, colsWidthCacheSize);
    int pad = Math.max(0, prefetchCols) * 2;
    return Math.max(base, pad);
  }

  public boolean shouldUselineNumberCache() {
    return lineNumber.showLineNumbers && lineNumber.lineNumbersGutterWidth > 0f && getHeight() > 0;
  }

  public void ensurelineNumberCacheBitmap(int width, int height) {
    if (lineNumber.lineNumberCacheBitmap != null
        && lineNumber.lineNumberCacheWidth == width
        && lineNumber.lineNumberCacheHeight == height) {
      return;
    }
    lineNumber.lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    lineNumber.lineNumberCacheCanvas = new Canvas(lineNumber.lineNumberCacheBitmap);
    lineNumber.lineNumberCacheWidth = width;
    lineNumber.lineNumberCacheHeight = height;
  }

  public void drawlineNumbersCachedUnwrapped(
      Canvas canvas,
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstVisibleLine,
      int lastVisibleLine) {
    if (!shouldUselineNumberCache()) {
      drawlineNumbersDirectUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      return;
    }

    int drawLastIndex = lastVisibleIndex;
    int drawLastLine = lastVisibleLine;
    if (isCodeFoldingEnabled) {
      int visibleCount = getVisibleLineCount();
      if (visibleCount > 0) {
        drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
      }
    } else {
      int total = getLinesCount();
      if (total > 0) {
        drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
      }
    }

    int gutterWidth = Math.max(1, Math.round(lineNumber.lineNumbersGutterWidth));
    float padPx = lineHeight;
    int height = getHeight() + Math.round(padPx * 2f);
    float baseScrollY = (float) Math.floor( scroll.scrollY / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumber.lineNumberCacheBitmap == null
            || lineNumber.lineNumberCacheWidth != gutterWidth
            || lineNumber.lineNumberCacheHeight != height
            || lineNumber.lineNumberCacheFirstIndex != firstVisibleIndex
            || lineNumber.lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumber.lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumber.lineNumberCacheTextSize != lineNumber.lineNumbersPaint.getTextSize()
            || lineNumber.lineNumberCacheTypeface != lineNumber.lineNumbersPaint.getTypeface()
            || lineNumber.lineNumberCacheRtl != isRtl
            || lineNumber.lineNumberCacheWrapped
            || lineNumber.lineNumberCacheCodeFolding != isCodeFoldingEnabled
            || Math.abs(lineNumber.lineNumberCacheGutterWidth - lineNumber.lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumber.lineNumberCacheFoldMarkerWidth - foldMarkerGutterWidth) > 0.1f
            || Math.abs(lineNumber.lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumber.lineNumberCacheColor != lineNumber.lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensurelineNumberCacheBitmap(gutterWidth, height);
      lineNumber.lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          isRtl
              ? lineNumber.getGutterStartX()
                  + lineNumber.GUTTER_TEXT_PADDING
                  + (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
              : lineNumber.getGutterStartX()
                  + lineNumber.lineNumbersGutterWidth
                  - (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
                  - lineNumber.GUTTER_TEXT_PADDING;
      float lineNumXLocal = lineNumX - lineNumber.getGutterStartX();

      if (isCodeFoldingEnabled) {
        for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
          int i = mapVisibleIndexToGlobal(v);
          int start = writeIntToChars(i + 1, lineNumber.lineNumberChars);
          int count = lineNumber.lineNumberChars.length - start;
          float y = Math.round(v * lineHeight - baseScrollY + lineHeight - paint.descent());
          lineNumber.lineNumberCacheCanvas.drawText(
              lineNumber.lineNumberChars, start, count, lineNumXLocal, y, lineNumber.lineNumbersPaint);
        }
      } else {
        for (int i = firstVisibleLine; i <= drawLastLine; i++) {
          int start = writeIntToChars(i + 1, lineNumber.lineNumberChars);
          int count = lineNumber.lineNumberChars.length - start;
          float y = Math.round(i * lineHeight - baseScrollY + lineHeight - paint.descent());
          lineNumber.lineNumberCacheCanvas.drawText(
              lineNumber.lineNumberChars, start, count, lineNumXLocal, y, lineNumber.lineNumbersPaint);
        }
      }

      lineNumber.lineNumberCacheFirstIndex = firstVisibleIndex;
      lineNumber.lineNumberCacheLastIndex = drawLastIndex;
      lineNumber.lineNumberCacheBaseScrollY = baseScrollY;
      lineNumber.lineNumberCacheTextSize = lineNumber.lineNumbersPaint.getTextSize();
      lineNumber.lineNumberCacheTypeface = lineNumber.lineNumbersPaint.getTypeface();
      lineNumber.lineNumberCacheRtl = isRtl;
      lineNumber.lineNumberCacheWrapped = false;
      lineNumber.lineNumberCacheCodeFolding = isCodeFoldingEnabled;
      lineNumber.lineNumberCacheGutterWidth = lineNumber.lineNumbersGutterWidth;
      lineNumber.lineNumberCacheFoldMarkerWidth = foldMarkerGutterWidth;
      lineNumber.lineNumberCacheLineHeight = lineHeight;
      lineNumber.lineNumberCacheColor = lineNumber.lineNumbersPaint.getColor();
    }

    float offsetY = lineNumber.lineNumberCacheBaseScrollY -  scroll.scrollY;
    canvas.drawBitmap(lineNumber.lineNumberCacheBitmap, lineNumber.getGutterStartX(), offsetY, null);
    drawCurrentlineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
  }

  public void drawlineNumbersCachedWrapped(
      Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!shouldUselineNumberCache()) {
      drawlineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
      return;
    }

    int drawLastIndex = lastVisualIndex;
    int totalVisual = getTotalVisualLineCount();
    if (totalVisual > 0) {
      drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
    }

    int gutterWidth = Math.max(1, Math.round(lineNumber.lineNumbersGutterWidth));
    float padPx = lineHeight;
    int height = getHeight() + Math.round(padPx * 2f);
    float baseScrollY = (float) Math.floor( scroll.scrollY / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumber.lineNumberCacheBitmap == null
            || lineNumber.lineNumberCacheWidth != gutterWidth
            || lineNumber.lineNumberCacheHeight != height
            || lineNumber.lineNumberCacheFirstIndex != firstVisualIndex
            || lineNumber.lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumber.lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumber.lineNumberCacheTextSize != lineNumber.lineNumbersPaint.getTextSize()
            || lineNumber.lineNumberCacheTypeface != lineNumber.lineNumbersPaint.getTypeface()
            || lineNumber.lineNumberCacheRtl != isRtl
            || !lineNumber.lineNumberCacheWrapped
            || lineNumber.lineNumberCacheCodeFolding != isCodeFoldingEnabled
            || Math.abs(lineNumber.lineNumberCacheGutterWidth - lineNumber.lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumber.lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumber.lineNumberCacheColor != lineNumber.lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensurelineNumberCacheBitmap(gutterWidth, height);
      lineNumber.lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          isRtl
              ? lineNumber.getGutterStartX() + lineNumber.GUTTER_TEXT_PADDING
              : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.GUTTER_TEXT_PADDING;
      float lineNumXLocal = lineNumX - lineNumber.getGutterStartX();

      for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
        VisualLinePosition pos = getVisualPositionForIndex(v);
        if (pos.segment != 0) continue;
        int start = writeIntToChars(pos.line + 1, lineNumber.lineNumberChars);
        int count = lineNumber.lineNumberChars.length - start;
        float y = Math.round(v * lineHeight - baseScrollY + lineHeight - paint.descent());
        lineNumber.lineNumberCacheCanvas.drawText(
            lineNumber.lineNumberChars, start, count, lineNumXLocal, y, lineNumber.lineNumbersPaint);
      }

      lineNumber.lineNumberCacheFirstIndex = firstVisualIndex;
      lineNumber.lineNumberCacheLastIndex = drawLastIndex;
      lineNumber.lineNumberCacheBaseScrollY = baseScrollY;
      lineNumber.lineNumberCacheTextSize = lineNumber.lineNumbersPaint.getTextSize();
      lineNumber.lineNumberCacheTypeface = lineNumber.lineNumbersPaint.getTypeface();
      lineNumber.lineNumberCacheRtl = isRtl;
      lineNumber.lineNumberCacheWrapped = true;
      lineNumber.lineNumberCacheCodeFolding = isCodeFoldingEnabled;
      lineNumber.lineNumberCacheGutterWidth = lineNumber.lineNumbersGutterWidth;
      lineNumber.lineNumberCacheFoldMarkerWidth = foldMarkerGutterWidth;
      lineNumber.lineNumberCacheLineHeight = lineHeight;
      lineNumber.lineNumberCacheColor = lineNumber.lineNumbersPaint.getColor();
    }

    float offsetY = lineNumber.lineNumberCacheBaseScrollY -  scroll.scrollY;
    canvas.drawBitmap(lineNumber.lineNumberCacheBitmap, lineNumber.getGutterStartX(), offsetY, null);
    drawCurrentlineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
  }

  public void drawlineNumbersDirectUnwrapped(
      Canvas canvas,
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstVisibleLine,
      int lastVisibleLine) {
    int drawLastIndex = lastVisibleIndex;
    int drawLastLine = lastVisibleLine;
    if (isCodeFoldingEnabled) {
      int visibleCount = getVisibleLineCount();
      if (visibleCount > 0) drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
    } else {
      int total = getLinesCount();
      if (total > 0) drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
    }

    float lineNumX =
        isRtl
            ? lineNumber.getGutterStartX()
                + lineNumber.GUTTER_TEXT_PADDING
                + (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
            : lineNumber.getGutterStartX()
                + lineNumber.lineNumbersGutterWidth
                - (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
                - lineNumber.GUTTER_TEXT_PADDING;

    if (isCodeFoldingEnabled) {
      for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
        int i = mapVisibleIndexToGlobal(v);
        int start = writeIntToChars(i + 1, lineNumber.lineNumberChars);
        int count = lineNumber.lineNumberChars.length - start;
        float y = Math.round(v * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
        if (i == cursor.cursorLine) {
          int originalColor = lineNumber.lineNumbersPaint.getColor();
          lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
          canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
          lineNumber.lineNumbersPaint.setColor(originalColor);
        } else {
          canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
        }
      }
    } else {
      for (int i = firstVisibleLine; i <= drawLastLine; i++) {
        int start = writeIntToChars(i + 1, lineNumber.lineNumberChars);
        int count = lineNumber.lineNumberChars.length - start;
        float y = Math.round(i * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
        if (i == cursor.cursorLine) {
          int originalColor = lineNumber.lineNumbersPaint.getColor();
          lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
          canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
          lineNumber.lineNumbersPaint.setColor(originalColor);
        } else {
          canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
        }
      }
    }
  }

  public void drawlineNumbersDirectWrapped(
      Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    float lineNumX =
        isRtl
            ? lineNumber.getGutterStartX() + lineNumber.GUTTER_TEXT_PADDING
            : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.GUTTER_TEXT_PADDING;

    int drawLastIndex = lastVisualIndex;
    int totalVisual = getTotalVisualLineCount();
    if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

    for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
      VisualLinePosition pos = getVisualPositionForIndex(v);
      if (pos.segment != 0) continue;
      int start = writeIntToChars(pos.line + 1, lineNumber.lineNumberChars);
      int count = lineNumber.lineNumberChars.length - start;
      float y = Math.round(v * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
      if (pos.line == cursor.cursorLine) {
        int originalColor = lineNumber.lineNumbersPaint.getColor();
        lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
        canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
        lineNumber.lineNumbersPaint.setColor(originalColor);
      } else {
        canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
      }
    }
  }

  public void drawCurrentlineNumberUnwrapped(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!lineNumber.showLineNumbers) return;
    if (isCodeFoldingEnabled && isLineHiddenByFold(cursor.cursorLine)) return;

    int visibleIndex = isCodeFoldingEnabled ? getVisibleIndexForGlobalLine(cursor.cursorLine) : cursor.cursorLine;
    if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

    float lineNumX =
        isRtl
            ? lineNumber.getGutterStartX()
                + lineNumber.GUTTER_TEXT_PADDING
                + (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
            : lineNumber.getGutterStartX()
                + lineNumber.lineNumbersGutterWidth
                - (isCodeFoldingEnabled ? foldMarkerGutterWidth : 0f)
                - lineNumber.GUTTER_TEXT_PADDING;
    int start = writeIntToChars(cursor.cursorLine + 1, lineNumber.lineNumberChars);
    int count = lineNumber.lineNumberChars.length - start;
    float y = Math.round(visibleIndex * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
    int originalColor = lineNumber.lineNumbersPaint.getColor();
    lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
    canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
    lineNumber.lineNumbersPaint.setColor(originalColor);
  }

  public void drawCurrentlineNumberWrapped(
      Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!lineNumber.showLineNumbers) return;
    int visualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, 0);
    if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

    float lineNumX =
        isRtl
            ? lineNumber.getGutterStartX() + lineNumber.GUTTER_TEXT_PADDING
            : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.GUTTER_TEXT_PADDING;
    int start = writeIntToChars(cursor.cursorLine + 1, lineNumber.lineNumberChars);
    int count = lineNumber.lineNumberChars.length - start;
    float y = Math.round(visualIndex * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
    int originalColor = lineNumber.lineNumbersPaint.getColor();
    lineNumber.lineNumbersPaint.setColor(lineNumber.currentLineNumberColor);
    canvas.drawText(lineNumber.lineNumberChars, start, count, lineNumX, y, lineNumber.lineNumbersPaint);
    lineNumber.lineNumbersPaint.setColor(originalColor);
  }

  public void drawFoldMarkersForVisibleLines(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!isCodeFoldingEnabled) return;

    float markerX =
        isRtl
            ? (lineNumber.getGutterStartX() + lineNumber.gutterSeparatorWidth + foldMarkerEdgePadding)
            : (lineNumber.getGutterStartX()
                + lineNumber.lineNumbersGutterWidth
                - lineNumber.gutterSeparatorWidth
                - foldMarkerEdgePadding);

    for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
      int line = mapVisibleIndexToGlobal(v);
      String marker = getFoldMarkerForLine(line, getLineTextForRender(line));
      if (marker == null) continue;
      float y = Math.round(v * lineHeight -  scroll.scrollY + lineHeight - paint.descent());
      if (line == foldRippleLine && foldRippleAlpha > 0f) {
        int base = foldMarkerPaint.getColor();
        int alpha = Math.min(255, Math.max(0, (int) (255f * foldRippleAlpha)));
        foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
        float centerY = Math.round(v * lineHeight -  scroll.scrollY + lineHeight * 0.5f);
        canvas.drawCircle(markerX, centerY, foldRippleRadius, foldRipplePaint);
      }
      canvas.drawText(marker, markerX, y, foldMarkerPaint);
    }
  }

  public void drawHighlightedLineSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float y,
      float lineTop,
      float lineBottom) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;

    final List<UnderlineSpan> urlUnderlines = getUrlUnderlineSpansForLine(line, globalLine);

    int fadeStart = -1;
    int fadeEnd = -1;
    float fadeAlpha = 1f;
    if (charAnimation.isCharAnimationEnabled
        && globalLine == charAnimation.charAnimLine
        && charAnimation.charAnimEndChar > charAnimation.charAnimStartChar
        && charAnimation.charAnimAlpha < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimation.charAnimStartChar, line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimation.charAnimEndChar, line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimation.charAnimAlpha));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    if (highlightRules.isEmpty()) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          start,
          end,
          0f,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          urlUnderlines,
          lineTop,
          lineBottom);
      return;
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
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
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  urlUnderlines,
                  lineTop,
                  lineBottom);
        }

        if (segEnd > segStart) {
          currentX +=
              drawTextSegmentWithFadeAndUnderlines(
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  fadeStart,
                  fadeEnd,
                  fadeAlpha,
                  urlUnderlines,
                  lineTop,
                  lineBottom);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawTextSegmentWithFadeAndUnderlines(
          canvas,
          line,
          lastEnd,
          end,
          currentX,
          y,
          paint,
          fadeStart,
          fadeEnd,
          fadeAlpha,
          urlUnderlines,
          lineTop,
          lineBottom);
    }
  }

  public void drawDeleteAnimationForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
    if (!charAnimation.isCharAnimationEnabled) return;
    if (globalLine != charAnimation.delAnimLine
        || charAnimation.delAnimText == null
        || charAnimation.delAnimText.isEmpty()
        || charAnimation.delAnimAlpha <= 0f) return;
    if (line == null) line = "";
    int at = Math.max(0, Math.min(charAnimation.delAnimAtChar, line.length()));
    if (at < segStart || at > segEnd) return;
    float x = measureTextWithVisualSpaces(line, segStart, at, paint);
    Paint ghostPaint = (charAnimation.delAnimPaint != null) ? charAnimation.delAnimPaint : paint;
    charAnimation.charAnimTmpPaint.set(ghostPaint);
    charAnimation.charAnimTmpPaint.setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimation.delAnimAlpha))));
    canvas.drawText(charAnimation.delAnimText, x, y, charAnimation.charAnimTmpPaint);
  }

  public void drawWhitespaceGuidesForSegment(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (!isWhitespaceGuidesEnabled || isHeavyDrawSuppressed() || line == null || line.isEmpty())
      return;
    if (isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, start, end, y);
      return;
    }
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    whitespaceDrawState.syntaxIndex = 0;
    boolean mirrorRtl = isRtl && !isMixedDirectionText(line, start, end);
    float rtlWidth =
        mirrorRtl ? measureHighlightedSegmentWidth(line, globalLine, start, end) : 0f;

    List<HighlightSpan> visualSpans = highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = start;

    if (!visualSpans.isEmpty()) {
      for (HighlightSpan span : visualSpans) {
        if (lastEnd >= end) break;
        if (span.end <= start) continue;
        if (span.start >= end) break;

        int segStart = Math.max(start, span.start);
        int segEnd = Math.min(end, span.end);

        if (segStart > lastEnd) {
          currentX =
              drawWhitespaceGuidesSegment(
                  canvas,
                  line,
                  lastEnd,
                  segStart,
                  currentX,
                  y,
                  paint,
                  syntaxSpans,
                  hasSyntaxSpans,
                  whitespaceDrawState,
                  rtlWidth);
        }

        if (segEnd > segStart) {
          currentX =
              drawWhitespaceGuidesSegment(
                  canvas,
                  line,
                  segStart,
                  segEnd,
                  currentX,
                  y,
                  span.paint,
                  syntaxSpans,
                  hasSyntaxSpans,
                  whitespaceDrawState,
                  rtlWidth);
        }
        lastEnd = Math.max(lastEnd, segEnd);
      }
    }

    if (lastEnd < end) {
      drawWhitespaceGuidesSegment(
          canvas,
          line,
          lastEnd,
          end,
          currentX,
          y,
          paint,
          syntaxSpans,
          hasSyntaxSpans,
          whitespaceDrawState,
          rtlWidth);
  }
  }

  public void drawAutoSuggestionWrapped(
      Canvas canvas,
      String lineContent,
      int globalLine,
      int segStart,
      int segEnd,
      int visualIndex,
      float textBaselineY) {
    boolean allowSuggestion =
        activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

    float suggestionStartX_canvas =
        measureTextWithVisualSpaces(lineContent, segStart, cursorPositionInLine, paint);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);

    float left_view = suggestionStartX_canvas + getTextStartX() - getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (isRtl) {
      float baseX = getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = visualIndex * lineHeight -  scroll.scrollY;
    float bottom_view = top_view + lineHeight;

    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  public void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
    if (!isWhitespaceGuidesEnabled || isHeavyDrawSuppressed() || line.isEmpty()) return;
    if (line.indexOf(' ') < 0 && line.indexOf('\t') < 0) return;
    if (isRtl) {
      drawWhitespaceGuidesForRangeRtl(canvas, line, globalLine, 0, line.length(), y);
      return;
    }

    List<HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    whitespaceDrawState.syntaxIndex = 0;
    float rtlWidth = 0f;

    List<HighlightSpan> visualSpans = highlightCache.get(globalLine);
    if (visualSpans == null) {
      visualSpans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, visualSpans);
    }

    float currentX = 0f;
    int lastEnd = 0;

    if (!visualSpans.isEmpty()) {
      for (HighlightSpan span : visualSpans) {
        if (span.start < lastEnd) continue;
        if (span.start >= line.length()) break;

        int safeSpanEnd = Math.min(span.end, line.length());
        if (span.start > lastEnd) {
          currentX =
              drawWhitespaceGuidesSegment(
                  canvas,
                  line,
                  lastEnd,
                  span.start,
                  currentX,
                  y,
                  paint,
                  syntaxSpans,
                  hasSyntaxSpans,
                  whitespaceDrawState,
                  rtlWidth);
        }

        currentX =
            drawWhitespaceGuidesSegment(
                canvas,
                line,
                span.start,
                safeSpanEnd,
                currentX,
                y,
                span.paint,
                syntaxSpans,
                hasSyntaxSpans,
                whitespaceDrawState,
                rtlWidth);
        lastEnd = safeSpanEnd;
      }
    }

    if (lastEnd < line.length()) {
      drawWhitespaceGuidesSegment(
          canvas,
          line,
          lastEnd,
          line.length(),
          currentX,
          y,
          paint,
          syntaxSpans,
          hasSyntaxSpans,
          whitespaceDrawState,
          rtlWidth);
    }
  }

  public void drawWhitespaceGuidesForRangeRtl(
      Canvas canvas, String line, int globalLine, int start, int end, float y) {
    if (line == null || line.isEmpty() || start >= end) return;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return;
    if (line.indexOf(' ', start) < 0 && line.indexOf('\t', start) < 0) return;

    List<HighlightSpan> syntaxSpans = getWhitespaceGuideSyntaxSpans(line, globalLine);
    boolean hasSyntaxSpans = !syntaxSpans.isEmpty();
    int syntaxIndex = 0;
    HighlightSpan activeSyntax =
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
      paint.getTextWidths(line, runStart, runLimit, whitespaceWidthBuffer);

      float runWidth = 0f;
      for (int i = 0; i < runLen; i++) {
        char c = line.charAt(runStart + i);
        float adv =
            (c == '\t')
                ? getVisualTabWidth(paint)
                : getCharAdvanceWidth(c, whitespaceWidthBuffer[i], paint);
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
                ? getVisualTabWidth(paint)
                : getCharAdvanceWidth(c, whitespaceWidthBuffer[i], paint);

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

  public float drawTextSegmentWithFade(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha) {
    if (start >= end) return 0f;
    boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
    if (hasFade && containsArabicScript(line, start, end)) {
      int spaceScale = getVisualSpaceScale();
      if (spaceScale > 1 || line.indexOf('\t', start) >= 0) {
        return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
      }
      canvas.drawText(line, start, end, x, y, segmentPaint);
      return segmentPaint.measureText(line, start, end);
    }
    final int spaceScale = getVisualSpaceScale();
    if (spaceScale > 1) {
      if (!hasFade || end <= fadeStart || start >= fadeEnd) {
        return drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, segmentPaint, 1f);
      }

      float currentX = x;

      int beforeEnd = Math.min(end, fadeStart);
      if (start < beforeEnd) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
                canvas, line, start, beforeEnd, currentX, y, segmentPaint, 1f);
      }

      int fadeSegStart = Math.max(start, fadeStart);
      int fadeSegEnd = Math.min(end, fadeEnd);
      if (fadeSegStart < fadeSegEnd) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
                canvas, line, fadeSegStart, fadeSegEnd, currentX, y, segmentPaint, fadeAlpha);
      }

      int afterStart = Math.max(start, fadeEnd);
      if (afterStart < end) {
        currentX +=
            drawTextSegmentWithVisualSpaces(
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
      charAnimation.charAnimTmpPaint.set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      canvas.drawText(line, fadeSegStart, fadeSegEnd, currentX, y, charAnimation.charAnimTmpPaint);
      currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
      currentX += segmentPaint.measureText(line, afterStart, end);
    }

    return currentX - x;
  }

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

  public boolean isRtlScriptBlock(Character.UnicodeBlock block) {
    return block == Character.UnicodeBlock.ARABIC
        || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
        || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
        || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
        || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
        || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS
        || block == Character.UnicodeBlock.HEBREW;
  }

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

  public float drawTextSegmentWithFadeAndUnderlines(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha,
      @Nullable List<UnderlineSpan> underlines,
      float lineTop,
      float lineBottom) {
    if (start >= end) return 0f;
    // Check if any underlining is active based on both URL and Path flags
    boolean anyUnderliningActive =
        (isUrlUnderliningEnabled && urlUnderlinePattern != null)
            || (isPathUnderliningEnabled && pathUnderlinePattern != null);
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
        currentX +=
            drawTextSegmentWithFade(
                canvas,
                line,
                pos,
                plainEnd,
                currentX,
                y,
                segmentPaint,
                fadeStart,
                fadeEnd,
                fadeAlpha);
        pos = plainEnd;
      }

      int underlineStart = Math.max(pos, span.start);
      int underlineEnd = Math.min(end, span.end);
      if (underlineStart < underlineEnd) {
        float underlineXStart = currentX;
        currentX +=
            drawTextSegmentWithFade(
                canvas,
                line,
                underlineStart,
                underlineEnd,
                currentX,
                y,
                segmentPaint,
                fadeStart,
                fadeEnd,
                fadeAlpha);
        drawUnderlineSegmentWithFade(
            canvas,
            line,
            underlineStart,
            underlineEnd,
            underlineXStart,
            y,
            lineTop,
            lineBottom,
            segmentPaint,
            fadeStart,
            fadeEnd,
            fadeAlpha,
            span.isPath // Pass the isPath flag
            );
        pos = underlineEnd;
      }
    }

    if (pos < end) {
      currentX +=
          drawTextSegmentWithFade(
              canvas, line, pos, end, currentX, y, segmentPaint, fadeStart, fadeEnd, fadeAlpha);
    }

    return currentX - x;
  }

  public void drawUnderlineSegmentWithFade(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float baselineY,
      float lineTop,
      float lineBottom,
      Paint textPaint,
      int fadeStart,
      int fadeEnd,
      float fadeAlpha,
      boolean isPath // New parameter
      ) {
    if (start >= end) return;

    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float underlineY = baselineY + (fm.descent * 0.5f);
    underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

    float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
    thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

    // Select the correct temporary paint based on isPath flag
    Paint tmpPaintToUse = isPath ? pathUnderlineTmpPaint : urlUnderlineTmpPaint;
    tmpPaintToUse.set(textPaint);
    tmpPaintToUse.setStyle(Paint.Style.STROKE);
    tmpPaintToUse.setStrokeWidth(thickness);
    tmpPaintToUse.setUnderlineText(false);

    boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
    if (!hasFade || end <= fadeStart || start >= fadeEnd) {
      float w = measureTextWithVisualSpaces(line, start, end, textPaint);
      if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, tmpPaintToUse);
      return;
    }

    float currentX = x;
    int baseAlpha = textPaint.getAlpha();

    int beforeEnd = Math.min(end, fadeStart);
    if (start < beforeEnd) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = measureTextWithVisualSpaces(line, start, beforeEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int fadeSegStart = Math.max(start, fadeStart);
    int fadeSegEnd = Math.min(end, fadeEnd);
    if (fadeSegStart < fadeSegEnd) {
      tmpPaintToUse.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      float w = measureTextWithVisualSpaces(line, fadeSegStart, fadeSegEnd, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
      currentX += w;
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      tmpPaintToUse.setAlpha(baseAlpha);
      float w = measureTextWithVisualSpaces(line, afterStart, end, textPaint);
      if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, tmpPaintToUse);
    }
  }

  public void drawErrorUnderlinesForLine(
      Canvas canvas,
      String line,
      int globalLine,
      float baselineY,
      float lineTop,
      float lineBottom) {
    if (!errorUnderlineEnabled) return;
    List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
    if (spans == null || spans.isEmpty()) return;
    List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
    int len = line.length();
    for (ErrorUnderlineSpan span : snapshot) {
      int start = Math.max(0, Math.min(span.start, len));
      int end = Math.max(start, Math.min(span.end, len));
      if (start >= end) continue;
      float xStart = measureText(line, start, globalLine);
      float xEnd = measureText(line, end, globalLine);
      drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
    }
  }

  public void drawErrorUnderlinesForLineRange(
      Canvas canvas,
      String line,
      int globalLine,
      int start,
      int end,
      float baselineY,
      float lineTop,
      float lineBottom) {
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
      float xStart = measureText(line, s, globalLine);
      float xEnd = measureText(line, e, globalLine);
      drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
    }
  }

  public void drawErrorUnderlinesForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float baselineY,
      float lineTop,
      float lineBottom) {
    if (!errorUnderlineEnabled) return;
    List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
    if (spans == null || spans.isEmpty()) return;
    List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
    int len = line.length();
    for (ErrorUnderlineSpan span : snapshot) {
      int start = Math.max(segStart, Math.max(0, Math.min(span.start, len)));
      int end = Math.min(segEnd, Math.max(start, Math.min(span.end, len)));
      if (start >= end) continue;
      float xStart = measureTextWithVisualSpaces(line, segStart, start, paint);
      float w = measureTextWithVisualSpaces(line, start, end, paint);
      if (w <= 0f) continue;
      drawErrorSquiggle(canvas, xStart, xStart + w, baselineY, lineTop, lineBottom);
    }
  }

  public void drawErrorSquiggle(
      Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
    if (xEnd <= xStart) return;
    float lineH = Math.max(1f, lineBottom - lineTop);
    float textSize = paint.getTextSize();
    float y = baselineY + (paint.getFontMetrics().descent * 0.55f);
    float maxY = lineBottom - 2f;
    float minY = lineTop + 1f;
    y = Math.max(minY, Math.min(y, maxY));
    float amplitude = Math.max(1f, Math.min(lineH * 0.22f, textSize * errorUnderlineHeightScale));
    float roomTop = y - minY;
    float roomBottom = maxY - y;
    float room = Math.max(0f, Math.min(roomTop, roomBottom));
    amplitude = Math.min(amplitude, Math.max(1f, room));
    float waveLen = Math.max(textSize * errorUnderlineWaveLengthScale, amplitude * 2f);
    float thickness = Math.max(1f, textSize * errorUnderlineStrokeScale);

    errorUnderlinePaint.setColor(errorUnderlineColor);
    errorUnderlinePaint.setStyle(Paint.Style.STROKE);
    errorUnderlinePaint.setStrokeWidth(thickness);
    errorUnderlinePaint.setUnderlineText(false);
    errorUnderlinePaint.setStrokeCap(Paint.Cap.ROUND);
    errorUnderlinePaint.setStrokeJoin(Paint.Join.ROUND);
    if (errorUnderlineSmoothness > 0f) {
      errorUnderlinePaint.setPathEffect(
          new android.graphics.CornerPathEffect(errorUnderlineSmoothness));
    } else {
      errorUnderlinePaint.setPathEffect(null);
    }

    errorUnderlinePath.reset();
    errorUnderlinePath.moveTo(xStart, y);
    float x = xStart;
    boolean up = true;
    while (x < xEnd) {
      float midX = Math.min(xEnd, x + waveLen * 0.5f);
      float endX = Math.min(xEnd, x + waveLen);
      float ctrlY = up ? (y - amplitude) : (y + amplitude);
      errorUnderlinePath.quadTo(midX, ctrlY, endX, y);
      up = !up;
      x = endX;
    }
    canvas.drawPath(errorUnderlinePath, errorUnderlinePaint);
  }

  @Nullable
  public List<UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
    if (!isUrlUnderliningEnabled || urlUnderlinePattern == null) return null;
    List<UnderlineSpan> cached = urlUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    ArrayList<UnderlineSpan> spans = new ArrayList<>();
    Matcher matcher = urlUnderlinePattern.matcher(line);
    while (matcher.find()) {
      int start = matcher.start();
      int end = matcher.end();
      end = trimUrlUnderlineEnd(line, start, end);
      if (end > start) {
        spans.add(new UnderlineSpan(start, end, false));
      }
    }
    urlUnderlineCache.put(globalLine, spans);
    return spans;
  }

  public static int trimUrlUnderlineEnd(String line, int start, int end) {
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

  public int getVisualSpaceScale() {
    return 1;
  }

  public int getWhitespaceGuideStep() {
    return Math.max(1, whitespaceGuideSpaceStep);
  }

  public float getVisualSpaceWidth(Paint p) {
    return p.measureText(" ");
  }

  public float getVisualTabWidth(Paint p) {
    // Treat tab as a fixed number of spaces.
    return getVisualSpaceWidth(p) * DEFAULT_TAB_SIZE_SPACES;
  }

  public float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
    if (c == ' ') {
      return measuredWidth;
    }
    if (c == '\t') {
      return getVisualTabWidth(p);
    }
    return measuredWidth;
  }

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
      charAnimation.charAnimTmpPaint.set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
      drawPaint = charAnimation.charAnimTmpPaint;
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
      float adv = getCharAdvanceWidth(c, measureWidthBuffer[i], segmentPaint);
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

  public List<HighlightSpan> calculateSyntaxSpansForLine(String line, int globalLine) {
    if (getLogicalLineLength(globalLine, line) > maxSyntaxLineLength) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    HighlightLineState startState = getLineStateAtStart(globalLine);
    LineParseResult parseResult =
        parseLineForSyntax(
            line,
            startState.inBlockComment,
            startState.stringState,
            whitespaceStringRule,
            whitespaceCommentRule,
            true);

    if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) {
      if (isBlockCommentsEnabled) {
        blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
      }
      stringEndStateCache.put(globalLine, parseResult.endsInStringState);
    }

    List<HighlightSpan> spans = parseResult.spans;
    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  public List<HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
    HighlightRule stringRule = stringHighlightRule;
    HighlightRule commentRule = blockCommentHighlightRule;
    if (stringRule == null && commentRule == null) {
      return calculateSyntaxSpansForLine(line, globalLine);
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
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

  public float drawWhitespaceGuidesSegment(
      Canvas canvas,
      String line,
      int start,
      int end,
      float x,
      float y,
      Paint segmentPaint,
      List<HighlightSpan> syntaxSpans,
      boolean hasSyntaxSpans,
      WhitespaceDrawState state,
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
    HighlightSpan activeSyntax =
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
            if (isRtl && rtlWidth > 0f) {
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
        float charWidth = getVisualTabWidth(segmentPaint);
        float glyphX = currentX + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        if (isRtl && rtlWidth > 0f) {
          glyphX = rtlWidth - (currentX + charWidth)
              + Math.max(0f, (charWidth - whitespaceGuideTabWidth) * 0.5f);
        }
        canvas.drawText(WHITESPACE_GUIDE_TAB, glyphX, y, whitespaceGuidePaint);
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

  public List<HighlightSpan> calculateSpansForLine(String line, int globalLine) {
    List<HighlightSpan> spans = new ArrayList<>();
    if (getLogicalLineLength(globalLine, line) > maxSyntaxLineLength) {
      return spans;
    }
    if (highlightRules.isEmpty()) {
      return spans;
    }

    HighlightRule stringRule = stringHighlightRule;
    HighlightRule blockCommentRule = blockCommentHighlightRule;
    List<HighlightSpan> exclusionSpans = new ArrayList<>();

    if (isBlockCommentsEnabled
        || !lineCommentDelimiters.isEmpty()
        || isMultiLineStringsEnabled
        || isTripleQuoteStringsEnabled
        || isBacktickStringsEnabled
        || stringRule != null
        || blockCommentRule != null
        || lineCommentHighlightRule != null) {
      HighlightLineState startState = getLineStateAtStart(globalLine);
      HighlightRule parseStringRule = (stringRule != null) ? stringRule : whitespaceStringRule;
      HighlightRule parseBlockRule =
          (blockCommentRule != null) ? blockCommentRule : whitespaceCommentRule;
      LineParseResult parseResult =
          parseLineForSyntax(
              line,
              startState.inBlockComment,
              startState.stringState,
              parseStringRule,
              parseBlockRule,
              true);
      if (stringRule != null || blockCommentRule != null || lineCommentHighlightRule != null) {
        spans.addAll(parseResult.spans);
      } else {
        exclusionSpans.addAll(parseResult.spans);
      }

      if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) {
        if (isBlockCommentsEnabled) {
          blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
        }
        stringEndStateCache.put(globalLine, parseResult.endsInStringState);
      }
    }

    if (!regexHighlightRules.isEmpty() && !line.isEmpty()) {
      for (HighlightRule rule : regexHighlightRules) {
        Matcher matcher = rule.pattern.matcher(line);
        while (matcher.find()) {
          if (matcher.start() == matcher.end()) continue;
          HighlightSpan span = new HighlightSpan(matcher.start(), matcher.end(), rule.paint);
          if (hasOverlap(span, spans) || hasOverlap(span, exclusionSpans)) continue;
          spans.add(span);
        }
      }
    }

    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  public LineParseResult parseLineForSyntax(
      String line,
      boolean inBlockComment,
      int stringState,
      HighlightRule stringRule,
      HighlightRule blockCommentRule,
      boolean collectSpans) {
    List<HighlightSpan> spans = new ArrayList<>();
    int length = line.length();
    int i = 0;
    if (!isBlockCommentsEnabled) {
      inBlockComment = false;
    }
    if (stringState == STRING_STATE_BACKTICK && !isBacktickStringsEnabled) {
      stringState = 0;
    }
    if (stringState == STRING_STATE_TRIPLE && !isTripleQuoteStringsEnabled) {
      stringState = 0;
    }
    if (stringState != 0 && !isMultiLineStringsEnabled && stringState != STRING_STATE_TRIPLE) {
      stringState = 0;
    }

    while (i < length) {
      if (inBlockComment) {
        int end = findBlockCommentEnd(line, i);
        if (end < 0) {
          if (collectSpans && blockCommentRule != null && isBlockCommentsEnabled && length > 0) {
            spans.add(new HighlightSpan(0, length, blockCommentRule.paint));
          }
          return new LineParseResult(spans, true, 0);
        }
        if (collectSpans && blockCommentRule != null && isBlockCommentsEnabled) {
          spans.add(new HighlightSpan(0, end + 2, blockCommentRule.paint));
        }
        i = end + 2;
        inBlockComment = false;
        continue;
      }

      if (stringState != 0) {
        StringEndResult endResult = findStringEndForState(line, i, stringState);
        if (endResult.found) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighlightSpan(0, endResult.endIndex, stringRule.paint));
          }
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }
        if (collectSpans && stringRule != null && length > 0) {
          spans.add(new HighlightSpan(0, length, stringRule.paint));
        }
        return new LineParseResult(spans, false, stringState);
      }

      if (isLineCommentStart(line, i)) {
        if (collectSpans && length > i) {
          Paint commentPaint =
              (lineCommentHighlightRule != null)
                  ? lineCommentHighlightRule.paint
                  : ((blockCommentRule != null) ? blockCommentRule.paint : paint);
          spans.add(new HighlightSpan(i, length, commentPaint));
        }
        return new LineParseResult(spans, false, 0);
      }

      char c = line.charAt(i);
      if (isTripleQuoteStart(line, i) && !isEscaped(line, i)) {
        int end = findTripleQuoteEnd(line, i + 3);
        if (end >= 0) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighlightSpan(i, end + 3, stringRule.paint));
          }
          i = end + 3;
          continue;
        }
        if (isTripleQuoteStringsEnabled) {
          if (collectSpans && stringRule != null && length > 0) {
            spans.add(new HighlightSpan(i, length, stringRule.paint));
          }
          return new LineParseResult(spans, false, STRING_STATE_TRIPLE);
        }
      }

      if (isStringDelimiter(c) && !isEscaped(line, i)) {
        int end = findStringEnd(line, i + 1, c);
        if (end >= 0) {
          if (collectSpans && stringRule != null) {
            spans.add(new HighlightSpan(i, end + 1, stringRule.paint));
          }
          i = end + 1;
          continue;
        }
        if (isMultiLineStringsEnabled) {
          if (collectSpans && stringRule != null && length > 0) {
            spans.add(new HighlightSpan(i, length, stringRule.paint));
          }
          return new LineParseResult(spans, false, getStringStateForDelimiter(c));
        }
      }

      if (isBlockCommentsEnabled
          && c == '/'
          && i + 1 < length
          && line.charAt(i + 1) == '*'
          && !isTokenEscaped(line, i)) {
        int end = findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          if (collectSpans && blockCommentRule != null && length > 0) {
            spans.add(new HighlightSpan(i, length, blockCommentRule.paint));
          }
          return new LineParseResult(spans, true, 0);
        }
        if (collectSpans && blockCommentRule != null) {
          spans.add(new HighlightSpan(i, end + 2, blockCommentRule.paint));
        }
        i = end + 2;
        continue;
      }

      i++;
    }

    return new LineParseResult(spans, inBlockComment, stringState);
  }

  public HighlightLineState getLineStateAtStart(int globalLine) {
    if (globalLine <= windowStartLine) return new HighlightLineState(false, 0);
    int windowEnd = windowStartLine + linesWindow.size() - 1;
    if (globalLine > windowEnd) return new HighlightLineState(false, 0);

    Boolean cachedBlockPrev = blockCommentEndStateCache.get(globalLine - 1);
    Integer cachedStringPrev = stringEndStateCache.get(globalLine - 1);
    if (cachedBlockPrev != null && cachedStringPrev != null) {
      return new HighlightLineState(cachedBlockPrev, cachedStringPrev);
    }

    boolean inBlock = false;
    int stringState = 0;
    for (int line = windowStartLine; line < globalLine; line++) {
      Boolean cachedBlock = blockCommentEndStateCache.get(line);
      Integer cachedString = stringEndStateCache.get(line);
      if (cachedBlock != null && cachedString != null) {
        inBlock = cachedBlock;
        stringState = cachedString;
        continue;
      }
      String lineText = getLineFromWindowLocal(line - windowStartLine);
      if (lineText == null) lineText = "";
      LineParseResult result =
          parseLineForSyntax(lineText, inBlock, stringState, null, null, false);
      inBlock = result.endsInBlockComment;
      stringState = result.endsInStringState;
      blockCommentEndStateCache.put(line, inBlock);
      stringEndStateCache.put(line, stringState);
    }
    return new HighlightLineState(inBlock, stringState);
  }

  public static boolean hasOverlap(HighlightSpan span, List<HighlightSpan> spans) {
    for (HighlightSpan other : spans) {
      if (span.start < other.end && other.start < span.end) {
        return true;
      }
    }
    return false;
  }

  public static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    if (r.startsWith("//")) return true;
    if (r.startsWith("^//")) return true;
    if (r.startsWith("^\\s*//")) return true;
    if (r.startsWith("\\s*//")) return true;
    return false;
  }

  public boolean isStringDelimiter(char c) {
    if (c == '"') return true;
    if (c == '\'') return true;
    return c == '`' && isBacktickStringsEnabled;
  }

  public static boolean isTokenEscaped(String line, int index) {
    if (isEscaped(line, index)) return true;
    int next = index + 1;
    return next < line.length() && isEscaped(line, next);
  }

  public static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  public static int findStringEnd(String line, int start, char delimiter) {
    for (int i = start; i < line.length(); i++) {
      if (line.charAt(i) == delimiter && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public boolean isTripleQuoteStart(String line, int index) {
    if (!isTripleQuoteStringsEnabled) return false;
    if (index + 2 >= line.length()) return false;
    return line.charAt(index) == '"'
        && line.charAt(index + 1) == '"'
        && line.charAt(index + 2) == '"';
  }

  public static int findTripleQuoteEnd(String line, int start) {
    for (int i = start; i + 2 < line.length(); i++) {
      if (line.charAt(i) == '"'
          && line.charAt(i + 1) == '"'
          && line.charAt(i + 2) == '"'
          && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public static final int STRING_STATE_DOUBLE = 1;
  public static final int STRING_STATE_SINGLE = 2;
  public static final int STRING_STATE_BACKTICK = 3;
  public static final int STRING_STATE_TRIPLE = 4;

  public int getStringStateForDelimiter(char delimiter) {
    if (delimiter == '"') return STRING_STATE_DOUBLE;
    if (delimiter == '\'') return STRING_STATE_SINGLE;
    return STRING_STATE_BACKTICK;
  }

  public StringEndResult findStringEndForState(String line, int start, int state) {
    if (state == STRING_STATE_TRIPLE) {
      int end = findTripleQuoteEnd(line, start);
      return new StringEndResult(end >= 0, end >= 0 ? end + 3 : start);
    }
    char delimiter = '"';
    if (state == STRING_STATE_SINGLE) delimiter = '\'';
    if (state == STRING_STATE_BACKTICK) delimiter = '`';
    int end = findStringEnd(line, start, delimiter);
    return new StringEndResult(end >= 0, end >= 0 ? end + 1 : start);
  }

  public static class StringEndResult {
    final boolean found;
    final int endIndex;

    StringEndResult(boolean found, int endIndex) {
      this.found = found;
      this.endIndex = endIndex;
    }
  }

  public static int findBlockCommentEnd(String line, int start) {
    for (int i = start; i + 1 < line.length(); i++) {
      if (line.charAt(i) == '*' && line.charAt(i + 1) == '/' && !isTokenEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean isBracketChar(char c) {
    return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
  }

  public static boolean isOpeningBracket(char c) {
    return c == '(' || c == '[' || c == '{';
  }

  public static boolean isClosingBracket(char c) {
    return c == ')' || c == ']' || c == '}';
  }

  public static char matchingBracket(char c) {
    switch (c) {
      case '(':
        return ')';
      case ')':
        return '(';
      case '[':
        return ']';
      case ']':
        return '[';
      case '{':
        return '}';
      case '}':
        return '{';
      default:
        return 0;
    }
  }

  public boolean isLineCommentStart(String line, int index) {
    if (index < 0 || index >= line.length()) return false;
    if (lineCommentDelimiters.isEmpty()) return false;
    for (int t = 0; t < lineCommentDelimiters.size(); t++) {
      String token = lineCommentDelimiters.get(t);
      int len = token.length();
      if (len == 0) continue;
      if (index + len > line.length()) continue;
      boolean match;
      if (len == 1) {
        match = line.charAt(index) == token.charAt(0);
      } else {
        match = line.regionMatches(index, token, 0, len);
      }
      if (match && !isTokenEscaped(line, index)) {
        return true;
      }
    }
    return false;
  }

  public BracketMatch findBracketMatchInVisible(
      int firstVisibleLine, int lastVisibleLine, java.util.HashMap<Integer, String> directLines) {
    if (!isBracketMatchingEnabled) return null;
    if (cursor.cursorLine < firstVisibleLine || cursor.cursorLine > lastVisibleLine) return null;

    String cursorLineText = getLineTextForRenderWithDirect(cursor.cursorLine, directLines);
    if (cursorLineText == null) return null;

    int targetIndex = -1;
    char targetChar = 0;
    if (cursor.cursorChar > 0 && cursor.cursorChar - 1 < cursorLineText.length()) {
      char c = cursorLineText.charAt(cursor.cursorChar - 1);
      if (isBracketChar(c)) {
        targetIndex = cursor.cursorChar - 1;
        targetChar = c;
      }
    }
    if (targetIndex < 0 && cursor.cursorChar < cursorLineText.length()) {
      char c = cursorLineText.charAt(cursor.cursorChar);
      if (isBracketChar(c)) {
        targetIndex = cursor.cursorChar;
        targetChar = c;
      }
    }
    if (targetIndex < 0) return null;

    HighlightLineState startState = getLineStateAtStart(firstVisibleLine);
    boolean inBlockComment = startState.inBlockComment && isBlockCommentsEnabled;
    int stringState = startState.stringState;
    if (!isBlockCommentsEnabled) inBlockComment = false;
    if (!isMultiLineStringsEnabled && stringState != STRING_STATE_TRIPLE) stringState = 0;
    if (!isBacktickStringsEnabled && stringState == STRING_STATE_BACKTICK) stringState = 0;
    if (!isTripleQuoteStringsEnabled && stringState == STRING_STATE_TRIPLE) stringState = 0;

    java.util.ArrayDeque<BracketToken> stack = new java.util.ArrayDeque<>();

    for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
      String text = getLineTextForRenderWithDirect(line, directLines);
      if (text == null) text = "";
      int len = text.length();
      int i = 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = findBlockCommentEnd(text, i);
          int endPos = (end < 0) ? len : end + 2;
          if (line == cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          StringEndResult endResult = findStringEndForState(text, i, stringState);
          int endPos = endResult.found ? endResult.endIndex : len;
          if (line == cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (isLineCommentStart(text, i)) {
          if (line == cursor.cursorLine && targetIndex >= i) return null;
          inLineComment = true;
          break;
        }

        if (isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !isTokenEscaped(text, i)) {
          int end = findBlockCommentEnd(text, i + 2);
          int endPos = (end < 0) ? len : end + 2;
          if (line == cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (isTripleQuoteStart(text, i) && !isEscaped(text, i)) {
          int end = findTripleQuoteEnd(text, i + 3);
          int endPos = end >= 0 ? end + 3 : len;
          if (line == cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (isTripleQuoteStringsEnabled) {
              stringState = STRING_STATE_TRIPLE;
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (isStringDelimiter(c) && !isEscaped(text, i)) {
          int end = findStringEnd(text, i + 1, c);
          int endPos = end >= 0 ? end + 1 : len;
          if (line == cursor.cursorLine && targetIndex >= i && targetIndex < endPos) return null;
          if (end < 0) {
            if (isMultiLineStringsEnabled) {
              stringState = getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (isBracketChar(c) && !isEscaped(text, i)) {
          BracketToken token = new BracketToken(line, i, c);
          if (isOpeningBracket(c)) {
            stack.push(token);
          } else if (isClosingBracket(c)) {
            if (!stack.isEmpty() && stack.peek().bracket == matchingBracket(c)) {
              BracketToken open = stack.pop();
              if (line == cursor.cursorLine && i == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
              if (open.line == cursor.cursorLine && open.ch == targetIndex) {
                return new BracketMatch(open.line, open.ch, line, i);
              }
            }
          }
        }

        i++;
      }
    }
    return new BracketMatch(cursor.cursorLine, targetIndex, cursor.cursorLine, targetIndex);
  }

  public void drawBracketMatchForLine(
      Canvas canvas, String line, int globalLine, BracketMatch match) {
    if (match == null) return;
    if (globalLine != match.openLine && globalLine != match.closeLine) return;
    if (line == null || line.isEmpty()) return;

    if (match.openLine == match.closeLine) {
      if (match.openChar == match.closeChar) {
        drawBracketBox(canvas, line, globalLine, match.openChar);
        return;
      }

      // If the matching brackets are adjacent (e.g. "{}" or "[]"), draw a single box to avoid
      // the seam line in the middle.
      if (Math.abs(match.openChar - match.closeChar) == 1) {
        int leftIndex = Math.min(match.openChar, match.closeChar);
        int rightIndex = Math.max(match.openChar, match.closeChar);
        drawBracketBoxRange(canvas, line, globalLine, leftIndex, rightIndex);
      } else {
        drawBracketBox(canvas, line, globalLine, match.openChar);
        drawBracketBox(canvas, line, globalLine, match.closeChar);
      }
      return;
    }

    int index = (globalLine == match.openLine) ? match.openChar : match.closeChar;
    drawBracketBox(canvas, line, globalLine, index);
  }

  public void drawBracketBox(Canvas canvas, String line, int globalLine, int index) {
    if (index < 0 || index >= line.length()) return;

    float left = measureText(line, index, globalLine);
    float right = measureText(line, index + 1, globalLine);
    if (right <= left) right = left + measureTextWithVisualSpaces(line, index, index + 1, paint);

    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  public void drawBracketBoxRange(
      Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
    if (startIndex < 0 || endIndex < 0) return;
    if (startIndex >= line.length()) return;
    if (endIndex >= line.length()) endIndex = line.length() - 1;
    if (endIndex < startIndex) return;

    float left = measureText(line, startIndex, globalLine);
    float right = measureText(line, endIndex + 1, globalLine);
    if (right <= left)
      right = left + measureTextWithVisualSpaces(line, startIndex, endIndex + 1, paint);
    drawBracketBoxRect(canvas, globalLine, left, right);
  }

  public void drawBracketBoxRect(Canvas canvas, int globalLine, float left, float right) {
    final float padding = 1f;
    final float top = getDrawLineTop(globalLine) + padding;
    final float bottom = top + lineHeight - (padding * 2f);

    float l = left - padding;
    float r = right + padding;
    if (r <= l) return;

    bracketMatchRect.set(l, top, r, bottom);
    float radius = Math.max(2f, bracketMatchStrokeWidth + 1f);
    canvas.drawRoundRect(bracketMatchRect, radius, radius, bracketMatchPaint);
  }

  public List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = getFirstNonSpaceIndex(line);
    // Draw guides based on the stack at the *start* of the line.
    // This avoids incorrectly removing guides when a block opens and closes on the same line (e.g.
    // "if {}"),
    // and keeps the guide visible on the closing-brace line.
    List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = findBlockCommentEnd(line, i);
        if (end < 0) return tokensToDraw;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        StringEndResult endResult = findStringEndForState(line, i, state.stringState);
        if (!endResult.found) return tokensToDraw;
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !isTokenEscaped(line, i)) {
        int end = findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          return tokensToDraw;
        }
        i = end + 2;
        continue;
      }

      if (isTripleQuoteStart(line, i) && !isEscaped(line, i)) {
        int end = findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (isTripleQuoteStringsEnabled) {
            state.stringState = STRING_STATE_TRIPLE;
          }
          return tokensToDraw;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (isStringDelimiter(c) && !isEscaped(line, i)) {
        int end = findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (isMultiLineStringsEnabled) {
            state.stringState = getStringStateForDelimiter(c);
          }
          return tokensToDraw;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}') && !isEscaped(line, i)) {
        if (c == '{') {
          int column = getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace);
          float x = getGuideXForColumn(line, column, globalLine);
          state.stack.push(new BracketGuideToken(column, x));
        } else if (c == '}') {
          if (!state.stack.isEmpty()) {
            state.stack.pop();
          }
        }
      }

      i++;
    }

    return tokensToDraw;
  }

  public void advanceBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = getFirstNonSpaceIndex(line);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = findBlockCommentEnd(line, i);
        if (end < 0) return;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        StringEndResult endResult = findStringEndForState(line, i, state.stringState);
        if (!endResult.found) return;
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !isTokenEscaped(line, i)) {
        int end = findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          return;
        }
        i = end + 2;
        continue;
      }

      if (isTripleQuoteStart(line, i) && !isEscaped(line, i)) {
        int end = findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (isTripleQuoteStringsEnabled) {
            state.stringState = STRING_STATE_TRIPLE;
          }
          return;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (isStringDelimiter(c) && !isEscaped(line, i)) {
        int end = findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (isMultiLineStringsEnabled) {
            state.stringState = getStringStateForDelimiter(c);
          }
          return;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}') && !isEscaped(line, i)) {
        if (c == '{') {
          int column = getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace);
          float x = getGuideXForColumn(line, column, globalLine);
          state.stack.push(new BracketGuideToken(column, x));
        } else if (c == '}') {
          if (!state.stack.isEmpty()) {
            state.stack.pop();
          }
        }
      }

      i++;
    }
  }

  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuideToken> guideTokens) {
    if (!isBracketGuidesEnabled
        || isHeavyDrawSuppressed()
        || guideTokens == null
        || guideTokens.isEmpty()) return;
    if (line == null) line = "";
    guideSeenXCount = 0;
    float top = getDrawLineTop(globalLine);
    float bottom = top + lineHeight;
    int firstNonSpace = getFirstNonSpaceIndex(line);
    boolean adjustTopGuideToClosingBrace =
        (firstNonSpace >= 0 && line.charAt(firstNonSpace) == '}');
    float closingBraceX =
        adjustTopGuideToClosingBrace ? getGuideXForColumn(line, firstNonSpace, globalLine) : 0f;

    int tokenIndex = 0;
    for (BracketGuideToken token : guideTokens) {
      float x = (adjustTopGuideToClosingBrace && tokenIndex == 0) ? closingBraceX : token.x;
      tokenIndex++;
      boolean seen = false;
      for (int i = 0; i < guideSeenXCount; i++) {
        if (Math.abs(guideSeenXBuffer[i] - x) <= 0.5f) {
          seen = true;
          break;
        }
      }
      if (seen) continue;
      if (guideSeenXBuffer == null || guideSeenXBuffer.length < guideSeenXCount + 1) {
        float[] next = new float[Math.max(16, guideSeenXCount + 8)];
        if (guideSeenXBuffer != null && guideSeenXCount > 0) {
          System.arraycopy(guideSeenXBuffer, 0, next, 0, guideSeenXCount);
        }
        guideSeenXBuffer = next;
      }
      guideSeenXBuffer[guideSeenXCount++] = x;

      if (!isWhitespaceAtX(line, globalLine, x)) continue;
      canvas.drawLine(x, top, x, bottom, bracketGuidePaint);
    }
  }

  public void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
    if (!isIndentGuidesEnabled || !isIndentationBlocksEnabled || isHeavyDrawSuppressed()) return;
    if (!isLineInIndentBlock(globalLine)) return;
    if (line == null || line.isEmpty()) return;
    int unitSpaces = INDENT_BLOCK_UNIT.length();
    if (unitSpaces <= 0) return;

    float top = getDrawLineTop(globalLine);
    float bottom = top + lineHeight;
    int columns = 0;
    int nextGuide = unitSpaces;
    float x = 0f;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c != ' ' && c != '\t') break;
      float adv = measureTextWithVisualSpaces(line, i, i + 1, paint);
      if (c == '\t') {
        columns += DEFAULT_TAB_SIZE_SPACES;
      } else {
        columns += 1;
      }
      x += adv;
      while (columns >= nextGuide) {
        if (isWhitespaceAtX(line, globalLine, x)) {
          canvas.drawLine(x, top, x, bottom, indentGuidePaint);
        }
        nextGuide += unitSpaces;
      }
    }
  }

  public boolean isLineInIndentBlock(int globalLine) {
    if (!isIndentationBlocksEnabled) return false;
    rebuildIndentGuideIntervalsIfNeeded();
    if (indentGuideIntervals.isEmpty()) return false;
    int lo = 0;
    int hi = indentGuideIntervals.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int[] interval = indentGuideIntervals.get(mid);
      if (globalLine < interval[0]) {
        hi = mid - 1;
      } else if (globalLine > interval[1]) {
        lo = mid + 1;
      } else {
        return true;
      }
    }
    return false;
  }

  public void rebuildIndentGuideIntervalsIfNeeded() {
    if (!indentGuideIntervalsDirty) return;
    indentGuideIntervalsDirty = false;
    indentGuideIntervals.clear();
    if (!isIndentationBlocksEnabled || foldRanges.isEmpty()) return;
    for (FoldRange range : foldRanges.values()) {
      if (!range.isIndentFold) continue;
      int start = range.startLine + 1;
      int end = range.endLine;
      if (end < start) continue;
      indentGuideIntervals.add(new int[] {start, end});
    }
    if (indentGuideIntervals.isEmpty()) return;
    Collections.sort(indentGuideIntervals, (a, b) -> Integer.compare(a[0], b[0]));
    int write = 0;
    int[] cur = indentGuideIntervals.get(0);
    for (int i = 1; i < indentGuideIntervals.size(); i++) {
      int[] nxt = indentGuideIntervals.get(i);
      if (nxt[0] <= cur[1] + 1) {
        cur[1] = Math.max(cur[1], nxt[1]);
      } else {
        indentGuideIntervals.set(write++, cur);
        cur = nxt;
      }
    }
    indentGuideIntervals.set(write++, cur);
    while (indentGuideIntervals.size() > write)
      indentGuideIntervals.remove(indentGuideIntervals.size() - 1);
  }

  public static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }

  public int getBraceGuideColumnForLine(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    int column = (firstNonSpace >= 0) ? firstNonSpace : braceIndex;
    if (firstNonSpace >= 0 && braceIndex > firstNonSpace) {
      char first = line.charAt(firstNonSpace);
      if (first == ')' || first == ']') {
        int prevIndent = getPreviousNonEmptyIndentColumn(globalLine - 1);
        if (prevIndent >= 0) {
          column = prevIndent;
        }
      }
    }
    return column;
  }

  public int getPreviousNonEmptyIndentColumn(int line) {
    for (int l = line; l >= 0; l--) {
      String prev = getLineTextForRender(l);
      if (prev == null) continue;
      int idx = getFirstNonSpaceIndex(prev);
      if (idx >= 0) return idx;
    }
    return -1;
  }

  public static List<BracketGuideToken> getGuideTokensFromStack(
      java.util.ArrayDeque<BracketGuideToken> stack) {
    List<BracketGuideToken> tokens = new ArrayList<>();
    for (BracketGuideToken token : stack) {
      tokens.add(token);
    }
    return tokens;
  }

  public int getBracketGuideCacheConfigHash() {
    int h = 17;
    h = 31 * h + (isBlockCommentsEnabled ? 1 : 0);
    h = 31 * h + (isMultiLineStringsEnabled ? 1 : 0);
    h = 31 * h + (isBacktickStringsEnabled ? 1 : 0);
    h = 31 * h + (isTripleQuoteStringsEnabled ? 1 : 0);
    for (int i = 0; i < lineCommentDelimiters.size(); i++) {
      h = 31 * h + lineCommentDelimiters.get(i).hashCode();
    }
    h = 31 * h + (isWhitespaceGuidesEnabled ? 1 : 0);
    h = 31 * h + whitespaceGuideSpaceStep;
    h = 31 * h + Float.floatToIntBits(paint.getTextSize());
    h = 31 * h + (isRtl ? 1 : 0);
    return h;
  }

  public void invalidateBracketGuideCache() {
    bracketGuideCacheStartLine = -1;
    bracketGuideCacheEndLine = -1;
    bracketGuideCacheEditVersion = -1;
    bracketGuideCacheConfigHash = 0;
    bracketGuideTokensWindow.clear();
  }

  public void ensureBracketGuideCacheForWindow(
      @Nullable java.util.Map<Integer, String> directLines) {
    int start = windowStartLine;
    int end;
    synchronized (linesWindow) {
      end = windowStartLine + linesWindow.size() - 1;
    }
    if (start < 0 || end < start) {
      invalidateBracketGuideCache();
      return;
    }
    int v = editVersion.get();
    int cfg = getBracketGuideCacheConfigHash();
    if (start == bracketGuideCacheStartLine
        && end == bracketGuideCacheEndLine
        && v == bracketGuideCacheEditVersion
        && cfg == bracketGuideCacheConfigHash) {
      return;
    }

    HighlightLineState guideStart = getLineStateAtStart(start);
    boolean guideBlock = guideStart.inBlockComment && isBlockCommentsEnabled;
    int guideString = guideStart.stringState;
    if (!isBlockCommentsEnabled) guideBlock = false;
    if (!isMultiLineStringsEnabled && guideString != STRING_STATE_TRIPLE) guideString = 0;
    if (!isBacktickStringsEnabled && guideString == STRING_STATE_BACKTICK) guideString = 0;
    if (!isTripleQuoteStringsEnabled && guideString == STRING_STATE_TRIPLE) guideString = 0;

    BracketGuideState state = new BracketGuideState(guideBlock, guideString);
    bracketGuideTokensWindow.clear();
    bracketGuideTokensWindow.ensureCapacity(end - start + 1);

    for (int line = start; line <= end; line++) {
      String text = getLineTextForRenderWithDirect(line, directLines);
      List<BracketGuideToken> tokens = updateBracketGuideStateForLine(text, line, state);
      bracketGuideTokensWindow.add(tokens);
    }

    bracketGuideCacheStartLine = start;
    bracketGuideCacheEndLine = end;
    bracketGuideCacheEditVersion = v;
    bracketGuideCacheConfigHash = cfg;
  }

  public List<BracketGuideToken> getBracketGuideTokensForLine(int globalLine) {
    if (!isBracketGuidesEnabled) return java.util.Collections.emptyList();
    int start = bracketGuideCacheStartLine;
    int end = bracketGuideCacheEndLine;
    if (start < 0 || globalLine < start || globalLine > end)
      return java.util.Collections.emptyList();
    int idx = globalLine - start;
    if (idx < 0 || idx >= bracketGuideTokensWindow.size()) return java.util.Collections.emptyList();
    List<BracketGuideToken> tokens = bracketGuideTokensWindow.get(idx);
    return (tokens != null) ? tokens : java.util.Collections.emptyList();
  }

  public float getGuideXForColumn(String line, int column, int globalLine) {
    if (line == null) line = "";
    if (column <= line.length()) {
      return measureText(line, column, globalLine);
    }
    float base = measureText(line, line.length(), globalLine);
    float spaceWidth = getVisualSpaceWidth(paint);
    return base + spaceWidth * (column - line.length());
  }

  public boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    // Fast hit-test using per-char advances (avoids O(n^2) measureText calls),
    // but respects syntax styles (bold/italic) so guide X aligns with text width.
    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    boolean prevWhitespace = false;
    final float eps = 0.25f; // boundary tolerance (px)

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          if (hitTestWhitespaceSegment(
              line,
              pos,
              Math.min(span.start, len),
              globalLine,
              x,
              paint,
              eps,
              currentX,
              prevWhitespace)) {
            if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
            return true;
          }
          HitAdvance a = lastHitAdvance;
          if (a.hit) return a.isWhitespace;
          currentX = a.x;
          prevWhitespace = a.prevWhitespace;
          pos = a.pos;
        }
        int segStart = Math.max(pos, span.start);
        int segEnd = Math.min(len, span.end);
        if (segEnd > segStart) {
          if (hitTestWhitespaceSegment(
              line, segStart, segEnd, globalLine, x, span.paint, eps, currentX, prevWhitespace)) {
            if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
            return true;
          }
          HitAdvance a = lastHitAdvance;
          if (a.hit) return a.isWhitespace;
          currentX = a.x;
          prevWhitespace = a.prevWhitespace;
          pos = a.pos;
        }
      }
    }

    if (pos < len) {
      if (hitTestWhitespaceSegment(
          line, pos, len, globalLine, x, paint, eps, currentX, prevWhitespace)) {
        if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
        return true;
      }
      HitAdvance a = lastHitAdvance;
      if (a.hit) return a.isWhitespace;
    }

    // Beyond the end of text is treated as whitespace.
    return true;
  }

  public boolean isGuideHitOnWhitespaceBoundary(String line, float x) {
    if (!lastHitAdvance.hit || !lastHitAdvance.isWhitespace) return false;
    // If we're right at the end of a whitespace run and next char is text,
    // treat as non-whitespace so guides don't cut through letters.
    final float boundaryEps = 0.6f;
    if (lastHitAdvance.hitCharEndX - x > boundaryEps) return false;
    int next = lastHitAdvance.pos + 1;
    if (next >= line.length()) return false;
    return !Character.isWhitespace(line.charAt(next));
  }

  public static final class HitAdvance {
    boolean hit;
    boolean isWhitespace;
    float x;
    float hitCharEndX;
    int pos;
    boolean prevWhitespace;
  }

  public final HitAdvance lastHitAdvance = new HitAdvance();

  public boolean hitTestWhitespaceSegment(
      String line,
      int start,
      int end,
      int globalLine,
      float x,
      Paint p,
      float eps,
      float startX,
      boolean prevWhitespace) {
    lastHitAdvance.hit = false;
    lastHitAdvance.isWhitespace = false;
    lastHitAdvance.x = startX;
    lastHitAdvance.pos = start;
    lastHitAdvance.prevWhitespace = prevWhitespace;

    if (start >= end) return false;

    int segLen = end - start;
    if (measureWidthBuffer == null || measureWidthBuffer.length < segLen) {
      measureWidthBuffer = new float[Math.max(segLen, 64)];
    }
    p.getTextWidths(line, start, end, measureWidthBuffer);

    float currentX = startX;
    boolean prevWs = prevWhitespace;
    for (int i = 0; i < segLen; i++) {
      int idx = start + i;
      char c = line.charAt(idx);
      float adv = getCharAdvanceWidth(c, measureWidthBuffer[i], p);
      float nextX = currentX + adv;

      if (x <= nextX + eps) {
        // Treat boundary-at-start as part of the current glyph to avoid drawing guides through
        // text.
        boolean ws = Character.isWhitespace(c);
        lastHitAdvance.hit = true;
        lastHitAdvance.isWhitespace = ws;
        lastHitAdvance.x = currentX;
        lastHitAdvance.hitCharEndX = nextX;
        lastHitAdvance.pos = idx;
        lastHitAdvance.prevWhitespace = prevWs;
        return ws;
      }
      currentX = nextX;
      prevWs = Character.isWhitespace(c);
    }

    lastHitAdvance.x = currentX;
    lastHitAdvance.pos = end;
    lastHitAdvance.prevWhitespace = prevWs;
    return false;
  }

  public void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine) {
    if (!isColorHighlightingEnabled || line.isEmpty()) {
      return;
    }

    if (line.indexOf('#') < 0 && line.indexOf('0') < 0) return;

    int[] triples = colorCodeBgCache.get(globalLine);
    if (triples == null) {
      ArrayList<Integer> tmp = null;
      Matcher matcher = COLOR_HEX_PATTERN.matcher(line);
      while (matcher.find()) {
        String colorString = matcher.group(0);
        if (colorString == null || colorString.isEmpty()) continue;

        int color;
        try {
          if (colorString.startsWith("0x") || colorString.startsWith("0X")) {
            String hex = colorString.substring(2);
            if (hex.length() == 6) hex = "FF" + hex;
            color = (int) Long.parseLong(hex, 16);
          } else {
            color = android.graphics.Color.parseColor(colorString);
          }
        } catch (Exception e) {
          continue;
        }

        int backgroundColor = (color & 0x00FFFFFF) | (0xC0 << 24);
        if (tmp == null) tmp = new ArrayList<>();
        tmp.add(matcher.start());
        tmp.add(matcher.end());
        tmp.add(backgroundColor);
      }
      if (tmp == null || tmp.isEmpty()) {
        triples = new int[0];
      } else {
        triples = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) triples[i] = tmp.get(i);
      }
      colorCodeBgCache.put(globalLine, triples);
    }

    if (triples.length == 0) return;

    float top = getDrawLineTop(globalLine);
    float bottom = top + lineHeight;
    colorOverlayPaint.setStyle(Paint.Style.FILL);
    for (int i = 0; i + 2 < triples.length; i += 3) {
      int start = triples[i];
      int end = triples[i + 1];
      int backgroundColor = triples[i + 2];

      float left = measureText(line, start, globalLine);
      float right = measureText(line, end, globalLine);
      colorOverlayPaint.setColor(backgroundColor);
      canvas.drawRect(left, top, right, bottom, colorOverlayPaint);
    }
  }

  public float measureText(String line, int length, int globalLine) {
    int logicalLen = getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));
    if (logicalLen > maxSyntaxLineLength) {
      float avg = getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return measureTextWithVisualSpaces(line, 0, safeLen, paint);
    }

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
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

      // Measure part before the span
      if (span.start > lastEnd) {
        int measureEnd = Math.min(span.start, safeLen);
        totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd, paint);
      }

      lastEnd = span.start;

      // Measure the span itself
      int measureEnd = Math.min(span.end, safeLen);
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd, span.paint);

      lastEnd = span.end;
    }

    // Measure remaining part
    if (lastEnd < safeLen) {
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, safeLen, paint);
    }

    return totalWidth;
  }

  public static final class StreamedSliceRequest {
    final int line;
    final int start;
    final int end;

    StreamedSliceRequest(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  public static final class LineScanResult {
    final long length;
    final boolean reachedEof;

    LineScanResult(long length, boolean reachedEof) {
      this.length = length;
      this.reachedEof = reachedEof;
    }
  }

  public LineScanResult scanLineLength(RandomAccessFile raf) throws Exception {
    byte[] buf = new byte[8192];
    long lineLen = 0L;
    int prev = -1;
    while (true) {
      long chunkStart = raf.getFilePointer();
      int n = raf.read(buf);
      if (n <= 0) {
        return new LineScanResult(lineLen, true);
      }
      for (int i = 0; i < n; i++) {
        int b = buf[i] & 0xFF;
        if (b == '\n') {
          if (prev == '\r' && lineLen > 0L) lineLen -= 1L;
          long nextPos = chunkStart + i + 1;
          raf.seek(nextPos);
          return new LineScanResult(lineLen, false);
        }
        lineLen++;
        prev = b;
      }
    }
  }

  public void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
    if (isWordWrapEnabled) return;
    if (!isIndexReady || sourceFile == null || !sourceFile.exists()) return;
    if (isWindowLoading) return;

    ArrayList<StreamedSliceRequest> requests = new ArrayList<>();
    synchronized (linesWindow) {
      int winStart = windowStartLine;
      int winEnd = windowStartLine + linesWindow.size() - 1;
      int start = Math.max(firstVisibleLine, winStart);
      int end = Math.min(lastVisibleLine, winEnd);
      if (start > end) return;
      for (int line = start; line <= end; line++) {
        if (modifiedLines.containsKey(line)) continue;
        int len = getStreamedLineLength(line);
        if (len <= 0) continue;
        String slice = linesWindow.get(line - winStart);
        int sliceStart = getStreamedLineSliceStart(line);
        int sliceEnd = sliceStart + ((slice == null) ? 0 : slice.length());
        computeStreamedSliceBounds(slice, line, len, streamedSliceTmp);
        int desiredStart = streamedSliceTmp[0];
        int desiredEnd = streamedSliceTmp[1];
        if (sliceStart <= desiredStart && sliceEnd >= desiredEnd) continue;
        requests.add(new StreamedSliceRequest(line, desiredStart, desiredEnd));
      }
    }

    if (requests.isEmpty()) return;
    if (streamedSliceUpdatePending) return;
    streamedSliceUpdatePending = true;
    final int token = ++streamedSliceUpdateToken;
    final int taskVersion = ioTaskVersion.get();

    ioHandler.post(
        () -> {
          if (token != streamedSliceUpdateToken) return;
          if (taskVersion != ioTaskVersion.get()) return;
          if (sourceFile == null || !sourceFile.exists()) {
            post(() -> streamedSliceUpdatePending = false);
            return;
          }
          LinkedHashMap<Integer, String> results = new LinkedHashMap<>();
          SparseIntArray starts = new SparseIntArray();
          try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
            long fileLen = raf.length();
            for (StreamedSliceRequest req : requests) {
              long lineStart;
              synchronized (lineOffsetsLock) {
                if (req.line < 0 || req.line >= lineOffsets.length) continue;
                lineStart = lineOffsets[req.line];
              }
              if (isSingleByteCharset()) {
                long lineByteLen = getLineByteLengthFromIndex(raf, req.line, fileLen);
                String slice =
                    readLineSliceAtByte(raf, lineStart, lineByteLen, req.start, req.end);
                results.put(req.line, slice);
                starts.put(req.line, req.start);
              } else {
                StreamedCharSlice slice =
                    readLineSliceByChars(raf, lineStart, req.start, req.end, false);
                results.put(req.line, slice.text);
                starts.put(req.line, req.start);
              }
            }
          } catch (Exception ignored) {
          }

          post(
              () -> {
                if (token != streamedSliceUpdateToken) {
                  streamedSliceUpdatePending = false;
                  return;
                }
                if (taskVersion != ioTaskVersion.get()) {
                  streamedSliceUpdatePending = false;
                  return;
                }
                synchronized (linesWindow) {
                  int winStart = windowStartLine;
                  int winEnd = windowStartLine + linesWindow.size() - 1;
                  for (Map.Entry<Integer, String> e : results.entrySet()) {
                    int line = e.getKey();
                    if (line < winStart || line > winEnd) continue;
                    if (modifiedLines.containsKey(line)) continue;
                    int local = line - winStart;
                    if (local < 0 || local >= linesWindow.size()) continue;
                    linesWindow.set(local, (e.getValue() == null) ? "" : e.getValue());
                    int len = getStreamedLineLength(line);
                    if (len > 0) {
                      setStreamedLineInfo(line, len, starts.get(line));
                    }
                  }
                }
                streamedSliceUpdatePending = false;
                invalidate();
              });
        });
  }

  public void maybeKickWindowLoad(int firstVisibleLine) {
    if (zoom.isZoomGestureActive()) return;
    if (sourceFile == null || isFileCleared) {
      // in-memory only: no need
      return;
    }
    if (isWindowLoading) return;

    boolean inside =
        firstVisibleLine >= windowStartLine
            && firstVisibleLine < windowStartLine + linesWindow.size();
    if (!inside) {
      int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
      loadWindowAround(targetStart, null, false);
    }
  }

  public void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
    Paint.Style prevStyle = paint.getStyle();
    float prevStroke = paint.getStrokeWidth();
    Paint.Cap prevCap = paint.getStrokeCap();

    paint.setStyle(Paint.Style.FILL);
    teardropPath.reset();
    teardropPath.addOval(
        cx - handleRadius, cy, cx + handleRadius, cy + handleRadius * 2, Path.Direction.CW);
    canvas.drawPath(teardropPath, paint);

    paint.setStyle(prevStyle);
    paint.setStrokeWidth(prevStroke);
    paint.setStrokeCap(prevCap);
  }

  public boolean shouldHideCopyCutForSelection() {
    if (!selection.hasSelection) return true;

    int sL = selection.selStartLine, eL = selection.selEndLine;
    if (sL > eL) {
      int t = sL;
      sL = eL;
      eL = t;
    }
    long lines = (long) eL - (long) sL + 1L;
    return lines > hideCopyCutMaxLines;
  }

  public void setCopyCutMaxLines(long maxLines) {
    copyCutMaxLines = Math.max(1L, maxLines);
  }

  public void setCopyCutMaxChars(int maxChars) {
    copyCutMaxChars = Math.max(1, maxChars);
  }

  public void setHideCopyCutMaxLines(int maxLines) {
    hideCopyCutMaxLines = Math.max(1, maxLines);
  }

  public int getReplaceAllMaxCount() {
    return replaceAllMaxCount;
  }

  public void setHideKeyboardOnFocusLoss(boolean enabled) {
    hideKeyboardOnFocusLoss = enabled;
  }


  public void checkAndLoadWindow() {
    if (sourceFile == null || isFileCleared) return;
    if (getWidth() == 0 || getHeight() == 0) return;
    if (isWindowLoading) return;

    int firstVisibleIndex = (int) ( scroll.scrollY / lineHeight);
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(getHeight() / lineHeight);
    int firstVisibleLine;
    int lastVisibleLine;
    if (isWordWrapEnabled) {
      firstVisibleLine = getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleLine = getVisualPositionForIndex(lastVisibleIndex).line;
    } else {
      firstVisibleLine = mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = mapVisibleIndexToGlobal(lastVisibleIndex);
    }
    firstVisibleLine = Math.max(0, firstVisibleLine);
    lastVisibleLine = Math.max(firstVisibleLine, lastVisibleLine);
    int winEnd;
    synchronized (linesWindow) {
      winEnd = windowStartLine + linesWindow.size() - 1;
    }

    int topMargin = Math.max(0, prefetchLines);
    int bottomMargin = Math.max(0, prefetchLines);

    boolean needTop = windowStartLine > 0 && firstVisibleLine < windowStartLine + topMargin;
    boolean needBottom = !isEof && lastVisibleLine > winEnd - bottomMargin;
    boolean outside = firstVisibleLine < windowStartLine || firstVisibleLine > winEnd;

    if (needTop || needBottom || outside) {
      int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
      loadWindowAround(targetStart, null, false);
    }
  }

  public void loadWindowAround(int startLine, @Nullable Runnable onComplete) {
    loadWindowAround(startLine, onComplete, true);
  }

  public void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
    if (isWindowLoading) return;
    // Cancel any in-flight async width calculation for a previous window.
    maxWidthRecalcToken++;

    // FIX: If the file has been "cleared" (e.g., via select-all -> delete),
    // the editor is in a pure in-memory state. The `linesWindow` holds the
    // entire document. There is nothing to "load" from a file, so we should
    // simply do nothing. The previous logic incorrectly reset the window.
    if (isFileCleared) {
      if (onComplete != null) {
        post(onComplete);
      }
      return;
    }

    if (sourceFile == null) {
      if (onComplete != null) post(onComplete);
      return;
    }

    isWindowLoading = true;
    final int taskVersion = ioTaskVersion.incrementAndGet();
    final int requestedStart = Math.max(0, startLine);

    ioHandler.post(
        () -> {
          try {
            if (taskVersion != ioTaskVersion.get()) {
              post(
                  () -> {
                    isWindowLoading = false;
                    checkAndLoadWindow();
                  });
              return;
            }

            int actualStart = requestedStart;

            // If we have an index, clamp the window start to the last existing line.
            if (isIndexReady) {
              synchronized (lineOffsetsLock) {
                if (lineOffsets.length > 0 && actualStart >= lineOffsets.length) {
                  actualStart = Math.max(0, lineOffsets.length - 1);
                }
              }
            }

            List<String> newWin = new ArrayList<>();
            SparseIntArray newStreamedLengths = new SparseIntArray();
            SparseIntArray newStreamedSliceStarts = new SparseIntArray();
            boolean fileEndsWithNewline = false;
            boolean reachedEof = false;
            boolean trailingEmptyFromIndex = false;

            if (isIndexReady) {
              try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                long fileLen = raf.length();
                if (fileLen > 0) {
                  raf.seek(fileLen - 1);
                  fileEndsWithNewline = (raf.read() == '\n');
                }
                int limit = windowSize + (prefetchLines * 2);
                int lineIndex = actualStart;
                int maxLine;
                synchronized (lineOffsetsLock) {
                  maxLine = lineOffsets.length;
                }
                while (newWin.size() < limit) {
                  if (lineIndex >= maxLine) {
                    reachedEof = true;
                    break;
                  }
                  long lineStart;
                  synchronized (lineOffsetsLock) {
                    lineStart = lineOffsets[lineIndex];
                  }
                  long lineByteLen = getLineByteLengthFromIndex(raf, lineIndex, fileLen);
                  int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                  if (shouldStreamLineLength(lineLen)) {
                    int sliceStart = 0;
                    int sliceEnd =
                        Math.max(1, Math.min(lineLen, getInitialStreamedSliceSize()));
                    if (isSingleByteCharset()) {
                      String slice =
                          readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, getInitialStreamedSliceSize());
                      StreamedCharSlice slice =
                          readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                      newWin.add(slice.text);
                      newStreamedLengths.put(lineIndex, slice.length);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    }
                  } else {
                    String ln = readLineUtf8AtByte(raf, lineStart);
                    newWin.add(ln);
                  }
                  lineIndex++;
                }
                if (fileEndsWithNewline) {
                  synchronized (lineOffsetsLock) {
                    trailingEmptyFromIndex =
                        lineOffsets.length > 0 && lineOffsets[lineOffsets.length - 1] == fileLen;
                  }
                }
              }
            } else {
              // fallback: sequential scan without building full lines in memory
              try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                long fileLen = raf.length();
                if (fileLen > 0) {
                  raf.seek(fileLen - 1);
                  fileEndsWithNewline = (raf.read() == '\n');
                }
                raf.seek(0);
                int skipped = 0;
                while (skipped < actualStart) {
                  LineScanResult scan = scanLineLength(raf);
                  if (scan.reachedEof) break;
                  skipped++;
                }
                actualStart = skipped;

                int limit = windowSize + (prefetchLines * 2);
                int lineIndex = actualStart;
                while (newWin.size() < limit) {
                  long lineStart = raf.getFilePointer();
                  if (lineStart >= fileLen) {
                    reachedEof = true;
                    break;
                  }
                  LineScanResult scan = scanLineLength(raf);
                  long afterPos = raf.getFilePointer();
                  long lineByteLen = scan.length;
                  int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                  if (shouldStreamLineLength(lineLen)) {
                    int sliceStart = 0;
                    int sliceEnd =
                        Math.max(1, Math.min(lineLen, getInitialStreamedSliceSize()));
                    if (isSingleByteCharset()) {
                      String slice =
                          readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, getInitialStreamedSliceSize());
                      StreamedCharSlice slice =
                          readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                      newWin.add(slice.text);
                      newStreamedLengths.put(lineIndex, slice.length);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    }
                  } else {
                    raf.seek(lineStart);
                    byte[] buf = new byte[lineLen];
                    if (lineLen > 0) raf.readFully(buf);
                    String ln =
                        (lineLen > 0)
                            ? (binarySafeRenderingEnabled
                                ? bytesToControlVisible(buf, buf.length)
                                : new String(buf, fileCharset))
                            : "";
                    newWin.add(ln);
                  }
                  raf.seek(afterPos);
                  if (scan.reachedEof) {
                    reachedEof = true;
                    break;
                  }
                  lineIndex++;
                }
              } catch (Exception ignored) {
              }
            }

            // Guarantee a non-empty window.
            if (newWin.isEmpty()) {
              newWin.add("");
              actualStart = 0;
            }
            if (reachedEof && fileEndsWithNewline && !trailingEmptyFromIndex) {
              newWin.add("");
            }

            boolean eof = newWin.size() < windowSize + (prefetchLines * 2);

            // Overlay in-memory edits for currently loaded lines
            synchronized (modifiedLines) {
              for (int i = 0; i < newWin.size(); i++) {
                int globalLineNum = actualStart + i;
                if (modifiedLines.containsKey(globalLineNum)) {
                  String modifiedLine = modifiedLines.get(globalLineNum);
                  if (modifiedLine != null) newWin.set(i, modifiedLine);
                  newStreamedLengths.delete(globalLineNum);
                  newStreamedSliceStarts.delete(globalLineNum);
                }
              }
            }

            if (taskVersion != ioTaskVersion.get()) {
              post(
                  () -> {
                    isWindowLoading = false;
                    checkAndLoadWindow();
                  });
              return;
            }

            final int finalStart = actualStart;
            final SparseIntArray finalStreamedLengths = newStreamedLengths;
            final SparseIntArray finalStreamedSliceStarts = newStreamedSliceStarts;
            post(
                () -> {
                  isWindowLoading = false;
                  if (taskVersion != ioTaskVersion.get()) {
                    checkAndLoadWindow();
                    return;
                  }
                  synchronized (linesWindow) {
                    linesWindow.clear();
                    linesWindow.addAll(newWin);
                    windowStartLine = finalStart;
                    isEof = eof;
                  }
                  synchronized (streamedLinesLock) {
                    streamedLineLengths.clear();
                    streamedLineSliceStarts.clear();
                    for (int i = 0; i < finalStreamedLengths.size(); i++) {
                      int key = finalStreamedLengths.keyAt(i);
                      streamedLineLengths.put(key, finalStreamedLengths.valueAt(i));
                      streamedLineSliceStarts.put(
                          key, finalStreamedSliceStarts.get(key, 0));
                    }
                  }
                  lineNumber.invalidateLineNumberCache();
                  invalidateHighlightEnsureRange();
                  invalidateBracketGuideCache();
                  if (recalculateWidthSync) {
                    recalculateMaxLineWidth();
                  } else {
                    synchronized (lineWidthCache) {
                      lineWidthCache.clear();
                    }
                    currentMaxWindowLineWidth = 0f;
                    globalMaxLineWidth = 0f;
                    recalculateMaxLineWidthAsync();
                  }
                  if (isWordWrapEnabled) {
                    if (shouldSuppressWrapMetricsForFastSelectAll()) {
                      wrapMetricsReady = false;
                    } else {
                      if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null) {
                        if (getWidth() > 0) {
                          buildWrapMetricsForWindowSnapshot();
                        }
                      }
                      scheduleWrapMetricsSnapshotIfNeeded(Math.max(1, Math.round(getWrapWidth())));
                      requestWrapPrefixRebuild();
                    }
                  }
                  invalidate();
                  if (onComplete != null) onComplete.run();
                });
          } catch (Exception e) {
            e.printStackTrace();
            post(
                () -> {
                  isWindowLoading = false;
                  if (onComplete != null) onComplete.run();
                });
          }
        });
  }

  public void finishInitialFileOpenWarmup(final int token) {
    if (!isInitialFileOpenLoading) return;
    if (token != initialFileOpenToken) return;
    if (getHeight() <= 0 || lineHeight <= 0f) {
      postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
      return;
    }

    int firstVisibleLine = Math.max(0, getGlobalLineForY( scroll.scrollY));
    int viewHeight = getHeight() - keyboardHeight;
    if (viewHeight <= 0) viewHeight = getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(viewHeight / lineHeight) + 2);
    int lastVisibleLine = firstVisibleLine + visibleLines;

    ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, null);
    isInitialFileOpenLoading = false;
    if (initialFileOpenShowSpinner != null) {
      mainHandler.removeCallbacks(initialFileOpenShowSpinner);
      initialFileOpenShowSpinner = null;
    }
    setDisable(false);
    showLoadingCircle(false);
    invalidate();

    java.util.ArrayList<Runnable> callbacks;
    synchronized (initialLoadCallbacks) {
      if (initialLoadCallbacks.isEmpty()) return;
      callbacks = new java.util.ArrayList<>(initialLoadCallbacks);
      initialLoadCallbacks.clear();
    }
    for (Runnable cb : callbacks) {
      post(cb);
    }
  }

  public void runAfterInitialLoad(@Nullable Runnable action) {
    if (action == null) return;
    if (!isInitialFileOpenLoading) {
      post(action);
      return;
    }
    synchronized (initialLoadCallbacks) {
      initialLoadCallbacks.add(action);
    }
  }

  public void recalculateMaxLineWidthAsync() {
    final int token = ++maxWidthRecalcToken;
    final int startLine;
    final ArrayList<String> snapshot;
    synchronized (linesWindow) {
      startLine = windowStartLine;
      snapshot = new ArrayList<>(linesWindow);
    }
    if (snapshot.isEmpty()) return;

    final int chunkSize = 120;
    post(
        new Runnable() {
          int index = 0;
          float mx = 0f;

          @Override
          public void run() {
            if (token != maxWidthRecalcToken) return;
            int end = Math.min(snapshot.size(), index + chunkSize);
            for (int i = index; i < end; i++) {
              String line = snapshot.get(i);
              if (line == null) line = "";
              float w = getWidthForLine(startLine + i, line);
              synchronized (lineWidthCache) {
                lineWidthCache.put(startLine + i, w);
              }
              if (w > mx) mx = w;
            }
            currentMaxWindowLineWidth = mx;
            globalMaxLineWidth = Math.max(globalMaxLineWidth, mx);
            index = end;
            if (index < snapshot.size()) {
              post(this);
            } else {
              scroll.clampScrollX();
              invalidate();
            }
          }
        });
  }

  public void buildFileIndex() {
    if (sourceFile == null || !sourceFile.exists()) {
      isIndexReady = false;
      isIndexBuilding = false;
      return;
    }
    if (isIndexDisabled) {
      String path = sourceFile.getAbsolutePath();
      long len = sourceFile.length();
      if (path.equals(indexDisabledPath) && len == indexDisabledFileLength) {
        isIndexReady = false;
        isIndexBuilding = false;
        return;
      }
      isIndexDisabled = false;
      indexDisabledPath = null;
      indexDisabledFileLength = -1L;
    }
    isIndexBuilding = true;
    final int taskVersion = ioTaskVersion.get();
    ioHandler.post(
        () -> {
          long[] offsets = buildIndexJava(sourceFile.getAbsolutePath());
          if (taskVersion != ioTaskVersion.get()) {
            isIndexBuilding = false;
            return;
          }
          if (offsets != null) {
            synchronized (lineOffsetsLock) {
              if (taskVersion == ioTaskVersion.get()) {
                lineOffsets = offsets;
                isIndexReady = true;
                // When index is ready, we know the true line count.
                // We must re-measure to calculate the correct gutter width.
                post(SodiumEditor.this::requestLayout);
                if (isWordWrapEnabled) post(SodiumEditor.this::scheduleWrapMetricsBuild);
              }
            }
          } else {
            synchronized (lineOffsetsLock) {
              isIndexReady = false;
            }
          }
          isIndexBuilding = false;
        });
  }

  public void invalidatePendingIO() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    clearHighlightCaches();
    if (isWordWrapEnabled) invalidateWrapMetrics();
    if (isCodeFoldingEnabled) {
      foldRanges.clear();
      foldIntervalsDirty = true;
    }
  }

  public void invalidatePendingIOForEdit() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    clearHighlightCaches();
    if (isCodeFoldingEnabled) {
      foldRanges.clear();
      foldIntervalsDirty = true;
      indentGuideIntervalsDirty = true;
    }
  }

  public void clearContent() {
    invalidatePendingIOForEdit();
    sourceFile = null;
    isFileCleared = true;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    // Force clear wrap metrics as content is being cleared
    wrapMetricsReady = false;
    wrapLineCounts = null;
    wrapLinePrefix = null;
    totalWrapVisualLines = 0;
    wrapPrefixValidUpToLine = -1;

    synchronized (linesWindow) {
      linesWindow.clear();
      linesWindow.add("");
    }
    synchronized (modifiedLines) {
      modifiedLines.clear();
    }
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    clearStreamedLineCaches();
    clearHighlightCaches();
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;

    cursor.cursorLine = 0;
    cursor.cursorChar = 0;
    isEof = true;
     scroll.scrollY =0;
    scroll.scrollX =0;

    recalculateMaxLineWidth();
    requestLayout();
    invalidate();
  }

  public void loadFromFile(final File file) {
    invalidatePendingIOForEdit();
    isFileCleared = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    lineNumber.invalidateLineNumberCache();

    // Force clear wrap metrics for new file
    wrapMetricsReady = false;
    wrapLineCounts = null;
    wrapLinePrefix = null;
    totalWrapVisualLines = 0;
    wrapPrefixValidUpToLine = -1;

    final int token = ++initialFileOpenToken;
    isInitialFileOpenLoading = true;
    if (showLoadingOnFileOpen) {
      if (initialFileOpenShowSpinner != null) {
        mainHandler.removeCallbacks(initialFileOpenShowSpinner);
      }
      initialFileOpenShowSpinner =
          () -> {
            if (!showLoadingOnFileOpen) return;
            if (!isInitialFileOpenLoading) return;
            if (token != initialFileOpenToken) return;
            setDisable(true);
            showLoadingCircle(true);
          };
      mainHandler.postDelayed(initialFileOpenShowSpinner, 80);
    }

    sourceFile = file;
    windowStartLine = 0;
    synchronized (linesWindow) {
      linesWindow.clear();
    }
    synchronized (modifiedLines) {
      modifiedLines.clear();
    }
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    clearStreamedLineCaches();
    clearHighlightCaches();
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
    synchronized (lineOffsetsLock) {
      lineOffsets = new long[0];
    }
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    cursor.cursorLine = 0;
    cursor.cursorChar = 0;
    isEof = false;
     scroll.scrollY =0;
    scroll.scrollX =0;
    lineCountDelta = 0;

    loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
    ioHandler.post(this::buildFileIndex);
    requestLayout();
    invalidate();
  }

  public void updateSourceFile(File file) {
    sourceFile = file;
  }

  public int getEditVersionValue() {
    return editVersion.get();
  }

  public void refreshlineNumberCache() {
    lineNumber.invalidateLineNumberCache();
    requestLayout();
    invalidate();
  }

  public void setTextColor(int color) {
    paint.setColor(color);
    invalidate();
  }

  
  public void setReadOnly(boolean readOnly) {
    if (this.isReadOnly == readOnly) return;
    this.isReadOnly = readOnly;
    if (readOnly) {
      clearActiveSuggestion();
      selection.hasSelection = false;
      selection.isSelectAllActive = false;
      selection.isEntireFileSelected = false;
      InputMethodManager imm =
          (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
    restartInput();
    invalidate();
  }

  public void setDisable(boolean disable) {
    this.isDisabled = disable;
    // The keyboard should not be hidden automatically when the view is disabled
    // for background operations, as this provides a poor user experience for
    // quick operations like 'select all' -> 'delete'. The modal loading
    // indicator is sufficient to block interaction.
    // if (disable) {
    //     InputMethodManager imm = (InputMethodManager)
    // getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    //     if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    // }
  }

  public void restartInput() {
    InputMethodManager imm =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(this);
    }
  }

  public void showLoadingCircle(boolean show) {
    showLoadingCircle = show;
    if (show) {
      if (rotationAnimator == null) {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(1000);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.addUpdateListener(
            animation -> {
              loadingCircleRotation = (float) animation.getAnimatedValue();
              invalidate();
            });
      }
      if (!rotationAnimator.isRunning()) rotationAnimator.start();
    } else {
      if (rotationAnimator != null && rotationAnimator.isRunning()) rotationAnimator.cancel();
      loadingCircleRotation = 0f;
    }
    invalidate();
  }

  public void setShowLoadingOnFileOpen(boolean enabled) {
    showLoadingOnFileOpen = enabled;
  }

  public boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike) {
    int span = Math.abs(eL - sL) + 1;
    return isSelectAllLike || span >= LARGE_EDIT_LINES;
  }

  public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
    if (!enable) return;
    if (!shouldShowLargeEditUi(sL, eL, isSelectAllLike)) return;

    final int token = largeEditUiToken.incrementAndGet();
    setDisable(true);
    showLoadingCircle(true);

    // Watchdog: force hide after a short time in case any path forgets to hide.
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    mainHandler.postDelayed(largeEditUiWatchdog, 1500);

    // Also ensure token validity for later hides.
    post(
        () -> {
          if (token != largeEditUiToken.get()) return;
        });
  }

  public void endLargeEditUi(boolean invalidate) {
    // Advance token so any pending watchdog is ignored, then hide.
    largeEditUiToken.incrementAndGet();
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    setDisable(false);
    showLoadingCircle(false);
    if (invalidate) invalidate();
  }

  public static final int LARGE_PASTE_LINES = 1500;
  public static final int LARGE_PASTE_CHARS = 200_000;

  public static boolean isLargePasteText(String text) {
    if (text == null) return false;
    if (text.length() >= LARGE_PASTE_CHARS) return true;
    int newLines = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n' && ++newLines >= LARGE_PASTE_LINES) return true;
    }
    return false;
  }

  public void goToLine(int line) {
    goToLine(line, 1);
  }

  public void goToLine(int line, int col) {
    final int currentGoToLineVersion = goToLineVersion.incrementAndGet();
    setDisable(true);
    showLoadingCircle(true);

    if (selection.hasSelection) {
      selection.hasSelection = false;
      selection.isSelectAllActive = false;
      selection.isEntireFileSelected = false;
      selection.selecting = false;
    }

    final int requestedLine = Math.max(0, line - 1);
    final int requestedCol = Math.max(0, col - 1);

    Integer knownTotal = null;

    if (sourceFile == null || isFileCleared) {
      // In-memory mode: the "document" is exactly what we have in memory.
      synchronized (linesWindow) {
        knownTotal = Math.max(1, windowStartLine + linesWindow.size());
      }
    } else if (isIndexReady) {
      synchronized (lineOffsetsLock) {
        knownTotal = Math.max(1, lineOffsets.length);
      }
    } else if (isEof) {
      synchronized (linesWindow) {
        knownTotal = Math.max(1, windowStartLine + linesWindow.size());
      }
    }

    if (knownTotal != null) {
      int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
      proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      // Index not ready and not at EOF: count lines once to clamp the target line.
      countTotalLines(
          totalLines -> {
            if (currentGoToLineVersion != goToLineVersion.get()) return;
            int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
            int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
            proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
          });
    }
  }

  public void proceedGoToLineClamped(
      final int currentGoToLineVersion, final int targetLine, final int targetCol) {
    // If a window load is already in progress, retry shortly to avoid getting stuck in a
    // disabled/loading state.
    if (isWindowLoading
        && sourceFile != null
        && !(targetLine >= windowStartLine && targetLine < windowStartLine + linesWindow.size())) {
      mainHandler.postDelayed(
          () -> {
            if (currentGoToLineVersion != goToLineVersion.get()) return;
            proceedGoToLineClamped(currentGoToLineVersion, targetLine, targetCol);
          },
          30);
      return;
    }

    Runnable completionAction =
        () -> {
          if (currentGoToLineVersion != goToLineVersion.get()) return;

          cursor.cursorLine = targetLine;

          if (cursor.cursorLine >= windowStartLine
              && cursor.cursorLine < windowStartLine + linesWindow.size()) {
            String lineText = getLineTextForRender(cursor.cursorLine);
            cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (isEof) {
            int lastLineInDoc = windowStartLine + linesWindow.size() - 1;
            if (cursor.cursorLine > lastLineInDoc) cursor.cursorLine = Math.max(0, lastLineInDoc);
            String lineText = getLineTextForRender(cursor.cursorLine);
            cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else {
            cursor.cursorChar = 0;
          }

          keepCursorVisibleHorizontally();
          setDisable(false);
          showLoadingCircle(false);

          requestFocus();
          post(
              () -> {
                showKeyboard();
                requestFocus();
                InputMethodManager imm =
                    (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.restartInput(this);
              });
        };

    // In-memory mode (sourceFile == null): no window loads.
    if (isFileCleared
        || sourceFile == null
        || (targetLine >= windowStartLine && targetLine < windowStartLine + linesWindow.size())) {
      completionAction.run();
    } else {
      int targetStart = Math.max(0, targetLine - prefetchLines);
      loadWindowAround(targetStart, completionAction);
    }
  }

  public void insertCharAtCursor(char c) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    editVersion.incrementAndGet();

    // FIX: لو فيه تحديد، لازم يكون استبدال ذري (خصوصاً خارج الشاشة)
    if (selection.hasSelection) {
      replaceSelectionWithText(String.valueOf(c));
      return;
    }

    if (ime.hasComposing) {
      ime.hasComposing = false;
      ime.composingLength = 0;
    }

    final int beforeLine = cursor.cursorLine;
    final int beforeChar = cursor.cursorChar;

    ensureLineInWindow(cursor.cursorLine, true);
    if (isWindowLoading
        && (cursor.cursorLine < windowStartLine || cursor.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = cursor.cursorLine - windowStartLine;
    if (localIdx < 0 || localIdx >= linesWindow.size()) {
      synchronized (linesWindow) {
        if (linesWindow.isEmpty()) linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, linesWindow.size() - 1));
    }

    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        int oldLineCount = getLinesCount();
        String before = base.substring(0, Math.min(cursor.cursorChar, base.length()));
        String after = base.substring(Math.min(cursor.cursorChar, base.length()));
        Float oldWidth = lineWidthCache.get(cursor.cursorLine);

        updateLocalLine(localIdx, before);
        linesWindow.add(localIdx + 1, after);

        modifiedLines.put(cursor.cursorLine, before);
        modifiedLines.put(cursor.cursorLine + 1, after);

        computeWidthForLine(cursor.cursorLine, before);
        computeWidthForLine(cursor.cursorLine + 1, after);

        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        clearHighlightCaches();
        cursor.cursorLine++;
        cursor.cursorChar = 0;
        lineCountDelta += 1;

        int newLineCount = getLinesCount();
        if (lineNumber.showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        onLineCountChanged();
      } else {
        int pos = Math.max(0, Math.min(cursor.cursorChar, base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursor.cursorLine, modified);
        invalidateHighlightCacheForLine(cursor.cursorLine);
        cursor.cursorChar++;
        float newWidth = measureTextWithVisualSpaces(modified, 0, modified.length(), paint);
        synchronized (lineWidthCache) {
          lineWidthCache.put(cursor.cursorLine, newWidth);
        }
        currentMaxWindowLineWidth = Math.max(currentMaxWindowLineWidth, newWidth);
        globalMaxLineWidth = Math.max(globalMaxLineWidth, currentMaxWindowLineWidth);
      }
      invalidate();
      keepCursorVisibleHorizontally();
    }
    updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    CursorTarget insertedEnd = computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = cursor.cursorLine;
    op.cursorCharAfter = cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (isReadOnly) return;
    if (selection.hasSelection) {
      replaceSelectionWithText("\n");
      return;
    }

    BracketPairType pairType = getCursorBracketPairType();
    if (isAutoBracketNewlineEnabled && pairType != BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (isAutoBracketNewlineIndentEnabled) {
        baseIndent = getLineLeadingWhitespace(cursor.cursorLine);
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = cursor.cursorLine + 1;
      int targetChar = innerIndent.length();
      insertTextAtCursor(insertText);

      cursor.cursorLine = targetLine;
      cursor.cursorChar = targetChar;
      caret.resetBlink();
      keepCursorVisibleHorizontally();
      invalidate();
      updateSuggestion();
      return;
    }

    if (isAutoIndentAfterClosingBracketEnabled) {
      String ln = getLineTextForRender(cursor.cursorLine);
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursor.cursorChar, ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = getLineLeadingWhitespace(cursor.cursorLine);
          int baseWidth = getIndentWidth(baseIndent);
          int unit = INDENT_BLOCK_UNIT.length();
          int targetWidth = baseWidth;
          if (c == '{') {
            int firstNonSpace = getFirstNonSpaceIndex(before);
            boolean startsWithClosingParenOrBracket =
                firstNonSpace >= 0
                    && (before.charAt(firstNonSpace) == ')' || before.charAt(firstNonSpace) == ']');
            if (!startsWithClosingParenOrBracket) {
              targetWidth = baseWidth + unit;
            }
          } else {
            targetWidth = Math.max(0, baseWidth - unit);
          }
          insertTextAtCursor("\n" + buildIndentFromWidth(targetWidth));
          return;
        }
      }
    }

    if (isIndentationBlocksEnabled) {
      String ln = getLineTextForRender(cursor.cursorLine);
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursor.cursorChar, ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = getLineLeadingWhitespace(cursor.cursorLine);
      String extraIndent = trimmed.endsWith(":") ? INDENT_BLOCK_UNIT : "";
      insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (isAutoBracketNewlineIndentEnabled) {
      String baseIndent = getLineLeadingWhitespace(cursor.cursorLine);
      insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }

  public BracketPairType getCursorBracketPairType() {
    String ln = getLineTextForRender(cursor.cursorLine);
    if (ln == null) return BracketPairType.NONE;
    if (cursor.cursorChar <= 0 || cursor.cursorChar >= ln.length()) return BracketPairType.NONE;

    char left = ln.charAt(cursor.cursorChar - 1);
    char right = ln.charAt(cursor.cursorChar);
    if (left == '{' && right == '}') return BracketPairType.CURLY;
    if (left == '(' && right == ')') return BracketPairType.ROUND;
    if (left == '[' && right == ']') return BracketPairType.SQUARE;
    return BracketPairType.NONE;
  }

  public static String rstripWhitespace(String text) {
    if (text == null || text.isEmpty()) return "";
    int end = text.length();
    while (end > 0) {
      char c = text.charAt(end - 1);
      if (c != ' ' && c != '\t') break;
      end--;
    }
    return (end == text.length()) ? text : text.substring(0, end);
  }

  public static int findPrevNonWhitespaceIndex(String text, int start) {
    if (text == null || text.isEmpty()) return -1;
    for (int i = Math.min(start, text.length() - 1); i >= 0; i--) {
      if (!Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }

  public static String buildIndentFromWidth(int width) {
    if (width <= 0) return "";
    char[] buf = new char[width];
    for (int i = 0; i < width; i++) buf[i] = ' ';
    return new String(buf);
  }

  public int getIndentWidth(String line) {
    if (line == null || line.isEmpty()) return 0;
    int width = 0;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == ' ') {
        width++;
      } else if (c == '\t') {
        width += DEFAULT_TAB_SIZE_SPACES;
      } else {
        break;
      }
    }
    return width;
  }

  public String getLineLeadingWhitespace(int line) {
    String ln = getLineTextForRender(line);
    if (ln == null || ln.isEmpty()) return "";
    int i = 0;
    while (i < ln.length()) {
      char c = ln.charAt(i);
      if (c != ' ' && c != '\t') break;
      i++;
    }
    return (i == 0) ? "" : ln.substring(0, i);
  }

  public enum BracketPairType {
    NONE,
    CURLY,
    ROUND,
    SQUARE
  }

  public void deleteCharAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    editVersion.incrementAndGet();
    clearActiveSuggestion(); // Clear suggestion on delete

    if (ime.hasComposing) {
      deleteComposing();
      return;
    }

    final int beforeLine = cursor.cursorLine;
    final int beforeChar = cursor.cursorChar;

    ensureLineInWindow(cursor.cursorLine, true);
    if (isWindowLoading
        && (cursor.cursorLine < windowStartLine || cursor.cursorLine >= windowStartLine + linesWindow.size())) {
      post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = cursor.cursorLine - windowStartLine;
    if (localIdx < 0 || localIdx >= linesWindow.size()) return;

    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursor.cursorChar > 0) {
        Float oldWidth = lineWidthCache.get(cursor.cursorLine);
        int safeStart = Math.max(0, cursor.cursorChar - 1);
        String removed = base.substring(safeStart, Math.min(cursor.cursorChar, base.length()));
        boolean atLineEnd = cursor.cursorChar >= base.length();
        if (atLineEnd) {
          Paint p = getPaintForChar(cursor.cursorLine, safeStart, base);
          charAnimation.startDeleteAnimation(cursor.cursorLine, safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(cursor.cursorChar);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursor.cursorLine, modified);
        invalidateHighlightCacheForLine(cursor.cursorLine);
        cursor.cursorChar = safeStart;
        computeWidthForLine(cursor.cursorLine, modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursor.cursorLine);

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = safeStart;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeStart;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = cursor.cursorLine;
        op.cursorCharAfter = cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else if (cursor.cursorLine > 0) {
        int oldLineCount = getLinesCount();
        int prevGlobal = cursor.cursorLine - 1;
        ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - windowStartLine;
        if (prevLocal < 0 || prevLocal >= linesWindow.size()) return;

        String prev = getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        updateLocalLine(prevLocal, merged);
        modifiedLines.put(prevGlobal, merged);
        clearHighlightCaches();

        if (localIdx < linesWindow.size()) linesWindow.remove(localIdx);

        recalculateMaxLineWidth();
        cursor.cursorLine = prevGlobal;
        cursor.cursorChar = prev.length();
        computeWidthForLine(prevGlobal, merged);
        lineCountDelta -= 1;

        int newLineCount = getLinesCount();
        if (lineNumber.showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        onLineCountChanged();
        invalidate();

        EditOp op = new EditOp();
        op.startLine = prevGlobal;
        op.startChar = prev.length();
        op.endLine = beforeLine;
        op.endChar = 0;
        op.removedText = "\n";
        op.insertedText = "";
        op.insertedEndLine = prevGlobal;
        op.insertedEndChar = prev.length();
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = cursor.cursorLine;
        op.cursorCharAfter = cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
    }
    updateSuggestion(); // Update suggestion after deletion
  }

  public void deleteForwardAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    editVersion.incrementAndGet();
    clearActiveSuggestion(); // Clear suggestion on delete forward

    if (ime.hasComposing) {
      deleteComposing();
      return;
    }

    final int beforeLine = cursor.cursorLine;
    final int beforeChar = cursor.cursorChar;

    ensureLineInWindow(cursor.cursorLine, true);
    if (isWindowLoading
        && (cursor.cursorLine < windowStartLine || cursor.cursorLine >= windowStartLine + linesWindow.size())) {
      post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = cursor.cursorLine - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursor.cursorChar < base.length()) {
        Float oldWidth = lineWidthCache.get(cursor.cursorLine);
        String removed = base.substring(cursor.cursorChar, Math.min(cursor.cursorChar + 1, base.length()));
        boolean atLineEnd = cursor.cursorChar == base.length() - 1;
        if (atLineEnd) {
          Paint p = getPaintForChar(cursor.cursorLine, cursor.cursorChar, base);
          charAnimation.startDeleteAnimation(cursor.cursorLine, cursor.cursorChar, removed, p);
        }
        String modified = base.substring(0, cursor.cursorChar) + base.substring(cursor.cursorChar + 1);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursor.cursorLine, modified);
        computeWidthForLine(cursor.cursorLine, modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursor.cursorLine);

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar + 1;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = beforeChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = cursor.cursorLine;
        op.cursorCharAfter = cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else {
        int nextGlobal = cursor.cursorLine + 1;
        if (isEof && nextGlobal >= windowStartLine + linesWindow.size()) return;

        ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - windowStartLine;
        if (nextLocal >= 0 && nextLocal < linesWindow.size()) {
          String next = getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          updateLocalLine(localIdx, merged);
          linesWindow.remove(nextLocal);
          modifiedLines.put(cursor.cursorLine, merged);
          recalculateMaxLineWidth();
          computeWidthForLine(cursor.cursorLine, merged);
          onLineCountChanged();
          invalidate();
          lineCountDelta -= 1;

          EditOp op = new EditOp();
          op.startLine = beforeLine;
          op.startChar = base.length();
          op.endLine = nextGlobal;
          op.endChar = 0;
          op.removedText = "\n";
          op.insertedText = "";
          op.insertedEndLine = beforeLine;
          op.insertedEndChar = base.length();
          op.cursorLineBefore = beforeLine;
          op.cursorCharBefore = beforeChar;
          op.cursorLineAfter = cursor.cursorLine;
          op.cursorCharAfter = cursor.cursorChar;
          op.timestamp = System.currentTimeMillis();
          recordEdit(op);
        }
      }
    }
    updateSuggestion(); // Update suggestion after delete forward
  }

  public void commitComposing(boolean keepInText) {
    ime.commitComposing(keepInText);
  }

  public void replaceComposingWith(CharSequence textSeq) {
    ime.replaceComposingWith(textSeq);
  }

  public void deleteComposing() {
    ime.deleteComposing();
  }

  public int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  public void setSelectionRange(int sLine, int sChar, int eLine, int eChar) {
    setSelectionInternal(sLine, sChar, eLine, eChar);
    invalidate();
  }

  public static final long COPY_CUT_MAX_LINES = 20000L;
  public static final int COPY_CUT_MAX_CHARS = 8_000_000; // safety cap
  public long copyCutMaxLines = COPY_CUT_MAX_LINES;
  public int copyCutMaxChars = COPY_CUT_MAX_CHARS;
  public int hideCopyCutMaxLines = 20000;
  public int replaceAllMaxCount = 100000;
  public boolean hideKeyboardOnFocusLoss = true;

  public String getSelectedText() {
    if (!selection.hasSelection) return null;
    if (shouldHideCopyCutForSelection()) return null;

    int sL = selection.selStartLine, sC = selection.selStartChar, eL = selection.selEndLine, eC = selection.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    return buildSelectedTextBlocking(sL, sC, eL, eC, copyCutMaxChars);
  }

  public void copySelectionToClipboard() {
    copyOrCutSelection(false);
  }

  public void actionCopy() {
    copySelectionToClipboard();
  }

  public void cutSelectionToClipboard() {
    copyOrCutSelection(true);
  }

  public void actionCut() {
    cutSelectionToClipboard();
  }

  public void copyOrCutSelection(final boolean cut) {
    if (!selection.hasSelection) return;
    clearActiveSuggestion(); // Clear suggestion when copying/cutting

    // Hidden/disabled for huge selections (requested behavior)
    if (shouldHideCopyCutForSelection()) return;

    int sL = selection.selStartLine, sC = selection.selStartChar, eL = selection.selEndLine, eC = selection.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    long lines = (long) eL - (long) sL + 1L;
    if (lines > copyCutMaxLines) return;

    final int fsL = sL, fsC = sC, feL = eL, feC = eC;

    // Fast path: selection fully inside current window -> copy on UI thread.
    boolean fullyInWindow =
        (fsL >= windowStartLine) && (feL < windowStartLine + linesWindow.size());
    if (fullyInWindow) {
      String text = buildSelectedTextFromWindow(fsL, fsC, feL, feC, copyCutMaxChars);
      ClipboardManager cm =
          (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
      if (cut) {
        deleteSelection();
      }
      return;
    }

    if (isWordWrapEnabled) {
      cancelWrapWorkForPriority();
    }

    ioHandler.post(
        () -> {
          final String text = buildSelectedTextBlocking(fsL, fsC, feL, feC, copyCutMaxChars);
          post(
              () -> {
                ClipboardManager cm =
                    (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null)
                  cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));

                if (cut) {
                  deleteSelection();
                }
              });
        });
  }

  public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    // In-memory (no file backing): build from render-safe access
    if (sourceFile == null || isFileCleared) {
      return buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // If the selection is fully inside the current window, prefer the window snapshot to avoid
    // stale file reads while edits are pending.
    boolean fullyInWindow = (sL >= windowStartLine) && (eL < windowStartLine + linesWindow.size());
    if (fullyInWindow) {
      return buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // File-backed: sequential read from start line, overriding with modifiedLines when available
    try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
      long startByte;
      if (isIndexReady) {
        synchronized (lineOffsetsLock) {
          if (sL >= 0 && sL < lineOffsets.length) startByte = lineOffsets[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = findLineStartByteByScanning(raf, sL);
      }

      raf.seek(startByte);
      try (BufferedReader br =
          new BufferedReader(
              new java.io.InputStreamReader(new FileInputStream(raf.getFD()), fileCharset), 8192)) {

        StringBuilder sb = new StringBuilder();
        for (int L = sL; L <= eL; L++) {
          String fileLine = br.readLine();
          if (fileLine == null) fileLine = "";

          String ln;
          synchronized (modifiedLines) {
            ln = modifiedLines.containsKey(L) ? modifiedLines.get(L) : fileLine;
          }
          if (ln == null) ln = "";

          int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
          int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
          if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
          if (L < eL) sb.append('\n');

          if (sb.length() > maxChars) return sb.substring(0, maxChars);
        }
        return sb.toString();
      }
    } catch (Exception e) {
      return null;
    }
  }

  public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
    StringBuilder sb = new StringBuilder();
    synchronized (linesWindow) {
      for (int L = sL; L <= eL; L++) {
        int local = L - windowStartLine;
        String ln = (local >= 0 && local < linesWindow.size()) ? linesWindow.get(local) : "";
        if (ln == null) ln = "";
        int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
        int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
        if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
        if (L < eL) sb.append('\n');

        if (sb.length() > maxChars) return sb.substring(0, maxChars);
      }
    }
    return sb.toString();
  }

  public void pasteFromClipboard() {
    invalidatePendingIOForEdit();
    editVersion.incrementAndGet();
    clearActiveSuggestion(); // Clear suggestion when pasting

    ClipboardManager cm =
        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(getContext());
    if (txt == null) return;
    insertTextAtCursor(txt.toString());
    updateSuggestion(); // Update suggestion after pasting
  }

  public void actionPaste() {
    pasteFromClipboard();
  }

  interface LineCountCallback {
    void onResult(int count);
  }

  public void countTotalLines(LineCountCallback callback) {
    final int taskVersion = ioTaskVersion.get();
    ioHandler.post(
        () -> {
          if (taskVersion != ioTaskVersion.get()) {
            post(() -> callback.onResult(-1));
            return;
          }
          if (isIndexReady && sourceFile != null) {
            synchronized (lineOffsetsLock) {
              post(() -> callback.onResult(lineOffsets.length));
            }
            return;
          }
          int count = 0;
          if (sourceFile != null && sourceFile.exists()) {
            try (FileInputStream is = new FileInputStream(sourceFile)) {
              byte[] buffer = new byte[8192];
              int len;
              boolean empty = true;
              while ((len = is.read(buffer)) != -1) {
                empty = false;
                for (int i = 0; i < len; i++) if (buffer[i] == '\n') count++;
              }
              if (!empty) count++;
            } catch (Exception e) {
              count = -1;
            }
          }
          final int finalCount = count;
          new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalCount));
        });
  }

  
  
  // ==============================
  // DELETE/REPLACE SELECTION (FIXED)
  // ==============================
  public void deleteSelection() {
    clearActiveSuggestion(); // Clear suggestion when deleting selection
    replaceSelectionWithText("");
  }

  public void actionDelete() {
    deleteSelection();
  }

  public static final class CursorTarget {
    public final int line;
    public final int ch;

    public CursorTarget(int line, int ch) {
      this.line = line;
      this.ch = ch;
    }
  }

  public static final class EditOp {
    public int startLine;
    public int startChar;
    public int endLine;
    public int endChar;
    public int insertedEndLine;
    public int insertedEndChar;
    public String removedText;
    public String insertedText;
    public int cursorLineBefore;
    public int cursorCharBefore;
    public int cursorLineAfter;
    public int cursorCharAfter;
    public long timestamp;
  }

  public static JSONObject editOpToJson(EditOp op) throws Exception {
    JSONObject obj = new JSONObject();
    obj.put("startLine", op.startLine);
    obj.put("startChar", op.startChar);
    obj.put("endLine", op.endLine);
    obj.put("endChar", op.endChar);
    obj.put("insertedEndLine", op.insertedEndLine);
    obj.put("insertedEndChar", op.insertedEndChar);
    obj.put("removedText", op.removedText == null ? JSONObject.NULL : op.removedText);
    obj.put("insertedText", op.insertedText == null ? JSONObject.NULL : op.insertedText);
    obj.put("cursorLineBefore", op.cursorLineBefore);
    obj.put("cursorCharBefore", op.cursorCharBefore);
    obj.put("cursorLineAfter", op.cursorLineAfter);
    obj.put("cursorCharAfter", op.cursorCharAfter);
    obj.put("timestamp", op.timestamp);
    return obj;
  }

  public static EditOp editOpFromJson(JSONObject obj) throws Exception {
    EditOp op = new EditOp();
    op.startLine = obj.optInt("startLine", 0);
    op.startChar = obj.optInt("startChar", 0);
    op.endLine = obj.optInt("endLine", 0);
    op.endChar = obj.optInt("endChar", 0);
    op.insertedEndLine = obj.optInt("insertedEndLine", 0);
    op.insertedEndChar = obj.optInt("insertedEndChar", 0);
    op.removedText = obj.isNull("removedText") ? null : obj.optString("removedText", "");
    op.insertedText = obj.isNull("insertedText") ? null : obj.optString("insertedText", "");
    op.cursorLineBefore = obj.optInt("cursorLineBefore", 0);
    op.cursorCharBefore = obj.optInt("cursorCharBefore", 0);
    op.cursorLineAfter = obj.optInt("cursorLineAfter", 0);
    op.cursorCharAfter = obj.optInt("cursorCharAfter", 0);
    op.timestamp = obj.optLong("timestamp", 0L);
    return op;
  }

  public static JSONArray editOpDequeToJson(java.util.ArrayDeque<EditOp> deque) throws Exception {
    JSONArray arr = new JSONArray();
    for (EditOp op : deque) {
      arr.put(editOpToJson(op));
    }
    return arr;
  }

  public static java.util.ArrayList<EditOp> editOpListFromJson(JSONArray arr) throws Exception {
    java.util.ArrayList<EditOp> list = new java.util.ArrayList<>();
    if (arr == null) return list;
    for (int i = 0; i < arr.length(); i++) {
      Object item = arr.opt(i);
      if (item instanceof JSONObject) {
        list.add(editOpFromJson((JSONObject) item));
      }
    }
    return list;
  }

  public String exportEditCacheJson() {
    try {
      JSONObject root = new JSONObject();
      root.put("undo", editOpDequeToJson(undoStack));
      root.put("redo", editOpDequeToJson(redoStack));
      root.put("pending", editOpDequeToJson(pendingEdits));
      root.put("pendingRedo", editOpDequeToJson(pendingRedo));
      root.put("dirty", !pendingEdits.isEmpty());
      root.put("cursorLine", cursor.cursorLine);
      root.put("cursorChar", cursor.cursorChar);
      root.put("selStartLine", selection.selStartLine);
      root.put("selStartChar", selection.selStartChar);
      root.put("selEndLine", selection.selEndLine);
      root.put("selEndChar", selection.selEndChar);
      root.put("hasSelection", selection.hasSelection);
      return root.toString();
    } catch (Exception e) {
      return "";
    }
  }

  public boolean importEditCacheJson(String json, boolean applyPendingEdits) {
    if (json == null || json.isEmpty()) return false;
    try {
      JSONObject root = new JSONObject(json);
      java.util.ArrayList<EditOp> undo = editOpListFromJson(root.optJSONArray("undo"));
      java.util.ArrayList<EditOp> redo = editOpListFromJson(root.optJSONArray("redo"));
      java.util.ArrayList<EditOp> pending = editOpListFromJson(root.optJSONArray("pending"));
      java.util.ArrayList<EditOp> pendingRedoList =
          editOpListFromJson(root.optJSONArray("pendingRedo"));

      if (applyPendingEdits) {
        isApplyingUndoRedo = true;
        for (EditOp op : pending) {
          applyEditForUndoRedo(
              op.startLine,
              op.startChar,
              op.endLine,
              op.endChar,
              op.insertedText == null ? "" : op.insertedText,
              op.cursorLineAfter,
              op.cursorCharAfter);
        }
        isApplyingUndoRedo = false;
      }

      undoStack.clear();
      redoStack.clear();
      pendingEdits.clear();
      pendingRedo.clear();
      for (EditOp op : undo) undoStack.addLast(op);
      for (EditOp op : redo) redoStack.addLast(op);
      for (EditOp op : pending) pendingEdits.addLast(op);
      for (EditOp op : pendingRedoList) pendingRedo.addLast(op);

      if (root.has("cursorLine") && root.has("cursorChar")) {
        int cLine = root.optInt("cursorLine", cursor.cursorLine);
        int cChar = root.optInt("cursorChar", cursor.cursorChar);
        if (root.optBoolean("hasSelection", false)) {
          int sL = root.optInt("selStartLine", cLine);
          int sC = root.optInt("selStartChar", cChar);
          int eL = root.optInt("selEndLine", cLine);
          int eC = root.optInt("selEndChar", cChar);
          restoreSelection(sL, sC, eL, eC, cLine, cChar);
        } else {
          setCursorPosition(cLine, cChar);
        }
      }

      editVersion.incrementAndGet();
      lineNumber.invalidateLineNumberCache();
      invalidate();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean hasPendingEdits() {
    return !pendingEdits.isEmpty();
  }

  public CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
    if (insertText == null) insertText = "";
    int newLines = 0;

    int lastNl = insertText.lastIndexOf('\n');
    if (lastNl >= 0) {
      for (int i = 0; i < insertText.length(); i++) {
        if (insertText.charAt(i) == '\n') newLines++;
      }
      int lastSegLen = insertText.length() - lastNl - 1;
      return new CursorTarget(baseLine + newLines, lastSegLen);
    }
    return new CursorTarget(baseLine, baseChar + insertText.length());
  }

  public int countNewlines(@Nullable String text) {
    if (text == null || text.isEmpty()) return 0;
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    return count;
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public int getUndoStackSize() {
    return undoStack.size();
  }

  public int getPendingEditsCount() {
    return pendingEdits.size();
  }

  public void clearUndoRedoHistory() {
    undoStack.clear();
    redoStack.clear();
    pendingEdits.clear();
    pendingRedo.clear();
  }

  public long getLastEditTimestamp() {
    return lastEditTimestamp;
  }

  public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    if (sourceFile == null) {
      if (onComplete != null) post(onComplete);
      return;
    }
    if (ime.hasComposing) {
      Log.d("SodiumEditorSave", "commitComposing before save");
      commitComposing(true);
    }
    final java.util.ArrayList<EditOp> ops = new java.util.ArrayList<>();
    synchronized (pendingEdits) {
      Log.d("SodiumEditorSave", "pendingEdits.size=" + pendingEdits.size());
      ops.addAll(pendingEdits);
      pendingEdits.clear();
      pendingRedo.clear();
    }
    if (ops.isEmpty()) {
      if (onComplete != null) post(onComplete);
      return;
    }
    Log.d("SodiumEditorSave", "Saving pending ops=" + ops.size());
    ioHandler.post(
        () -> {
          boolean ok = true;
          for (EditOp op : ops) {
            Log.d(
                "SodiumEditorSave",
                "Op s="
                    + op.startLine
                    + ":"
                    + op.startChar
                    + " e="
                    + op.endLine
                    + ":"
                    + op.endChar
                    + " insertLen="
                    + (op.insertedText == null ? 0 : op.insertedText.length())
                    + " removeLen="
                    + (op.removedText == null ? 0 : op.removedText.length()));
            if (!rewriteReplaceRangeBlocking(
                sourceFile, op.startLine, op.startChar, op.endLine, op.endChar, op.insertedText)) {
              ok = false;
              break;
            }
          }
          final boolean success = ok;
          post(
              () -> {
                if (!success) {
                  // If save failed, mark dirty so user can retry.
                  Log.d("SodiumEditorSave", "Save failed");
                  pendingEdits.addAll(ops);
                } else {
                  Log.d("SodiumEditorSave", "Save success");
                  synchronized (modifiedLines) {
                    modifiedLines.clear();
                  }
                  lineCountDelta = 0;
                  lineNumber.invalidateLineNumberCache();
                  requestLayout();
                  invalidate();
                }
                if (onComplete != null) onComplete.run();
              });
        });
  }

  public boolean rewriteReplaceRangeBlocking(
      File inFile, int sL, int sC, int eL, int eC, @Nullable String insertText) {
    if (inFile == null || !inFile.exists()) return false;
    try {
      RangeBytes range = computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
      if (range == null) return false;
      byte[] insertBytes =
          (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);
      final int BUF_SIZE = 1024 * 1024;

      try (RandomAccessFile raf = new RandomAccessFile(inFile, "rw");
          FileChannel ch = raf.getChannel()) {

        long fileLen = ch.size();
        long startByte = Math.max(0, Math.min(range.startByte, fileLen));
        long endByte = Math.max(0, Math.min(range.endByte, fileLen));
        if (endByte < startByte) {
          long t = startByte;
          startByte = endByte;
          endByte = t;
        }

        long removeLen = endByte - startByte;
        long diff = (long) insertBytes.length - removeLen;

        if (diff > 0) {
          raf.setLength(fileLen + diff);
          ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
          for (long pos = fileLen; pos > endByte; ) {
            long readPos = Math.max(endByte, pos - BUF_SIZE);
            int size = (int) (pos - readPos);
            buf.clear();
            buf.limit(size);
            ch.read(buf, readPos);
            buf.flip();
            ch.write(buf, readPos + diff);
            pos = readPos;
          }
        } else if (diff < 0) {
          ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
          for (long pos = endByte; pos < fileLen; ) {
            int size = (int) Math.min(BUF_SIZE, fileLen - pos);
            buf.clear();
            buf.limit(size);
            ch.read(buf, pos);
            buf.flip();
            ch.write(buf, pos + diff);
            pos += size;
          }
          raf.setLength(fileLen + diff);
        }

        if (insertBytes.length > 0) {
          ch.write(ByteBuffer.wrap(insertBytes), startByte);
        }
        ch.force(true);
      }

      sourceFile = inFile;
      synchronized (lineOffsetsLock) {
        lineOffsets = new long[0];
      }
      isIndexReady = false;
      isIndexBuilding = false;
      isIndexDisabled = false;
      indexDisabledPath = null;
      indexDisabledFileLength = -1L;
      ioHandler.post(this::buildFileIndex);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void recordEdit(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    boolean tooLarge =
        (op.removedText != null && op.removedText.length() > UNDO_TEXT_LIMIT)
            || (op.insertedText != null && op.insertedText.length() > UNDO_TEXT_LIMIT);
    if (tooLarge) {
      recordEditNoUndo(op);
      return;
    }

    boolean insertOnly =
        (op.removedText == null || op.removedText.isEmpty())
            && op.insertedText != null
            && !op.insertedText.isEmpty();

    if (insertOnly) {
      EditOp lastPending = pendingEdits.peekLast();
      if (lastPending != null
          && (lastPending.removedText == null || lastPending.removedText.isEmpty())
          && lastPending.insertedText != null
          && !lastPending.insertedText.isEmpty()
          && lastPending.insertedEndLine == op.startLine
          && lastPending.insertedEndChar == op.startChar) {
        Log.d(
            "SodiumEditorEdit",
            "merge insert start="
                + op.startLine
                + ":"
                + op.startChar
                + " addLen="
                + op.insertedText.length());
        String beforeText = lastPending.insertedText;
        lastPending.insertedText = lastPending.insertedText + op.insertedText;
        CursorTarget newEnd =
            computeCursorAfterInsert(
                lastPending.startLine, lastPending.startChar, lastPending.insertedText);
        lastPending.insertedEndLine = newEnd.line;
        lastPending.insertedEndChar = newEnd.ch;
        lastPending.cursorLineAfter = op.cursorLineAfter;
        lastPending.cursorCharAfter = op.cursorCharAfter;
        lastPending.timestamp = op.timestamp;

        EditOp lastUndo = undoStack.peekLast();
        if (lastUndo != null
            && lastUndo.startLine == lastPending.startLine
            && lastUndo.startChar == lastPending.startChar
            && lastUndo.endLine == lastPending.endLine
            && lastUndo.endChar == lastPending.endChar
            && lastUndo.insertedText != null
            && lastUndo.insertedText.equals(beforeText)) {
          lastUndo.insertedText = lastPending.insertedText;
          lastUndo.insertedEndLine = lastPending.insertedEndLine;
          lastUndo.insertedEndChar = lastPending.insertedEndChar;
          lastUndo.cursorLineAfter = lastPending.cursorLineAfter;
          lastUndo.cursorCharAfter = lastPending.cursorCharAfter;
          lastUndo.timestamp = lastPending.timestamp;
        }

        redoStack.clear();
        pendingRedo.clear();
        lastEditTimestamp = op.timestamp;
        return;
      }
    }

    undoStack.addLast(op);
    while (undoStack.size() > UNDO_STACK_LIMIT) {
      undoStack.removeFirst();
    }
    redoStack.clear();
    pendingEdits.addLast(op);
    pendingRedo.clear();
    lastEditTimestamp = op.timestamp;
    Log.d(
        "SodiumEditorEdit",
        "record op s="
            + op.startLine
            + ":"
            + op.startChar
            + " e="
            + op.endLine
            + ":"
            + op.endChar
            + " insertLen="
            + (op.insertedText == null ? 0 : op.insertedText.length())
            + " removeLen="
            + (op.removedText == null ? 0 : op.removedText.length())
            + " pending="
            + pendingEdits.size());
  }

  public void recordEditNoUndo(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    // Save-only record for very large edits or unknown removed text.
    pendingEdits.addLast(op);
    pendingRedo.clear();
    redoStack.clear();
    lastEditTimestamp = op.timestamp;
    Log.d(
        "SodiumEditorEdit",
        "record save-only op s="
            + op.startLine
            + ":"
            + op.startChar
            + " e="
            + op.endLine
            + ":"
            + op.endChar
            + " insertLen="
            + (op.insertedText == null ? 0 : op.insertedText.length())
            + " removeLen="
            + (op.removedText == null ? 0 : op.removedText.length())
            + " pending="
            + pendingEdits.size());
  }

  public void recordReplaceSelectionEdit(
      int sL,
      int sC,
      int eL,
      int eC,
      @Nullable String removedText,
      @Nullable String insertText,
      int beforeLine,
      int beforeChar) {
    String insert = (insertText == null) ? "" : insertText;
    if (removedText == null) {
      EditOp op = new EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      CursorTarget insertedEnd = computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = cursor.cursorLine;
      op.cursorCharAfter = cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      recordEditNoUndo(op);
      return;
    }
    if (removedText.length() > UNDO_TEXT_LIMIT || insert.length() > UNDO_TEXT_LIMIT) {
      EditOp op = new EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      CursorTarget insertedEnd = computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = cursor.cursorLine;
      op.cursorCharAfter = cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      recordEditNoUndo(op);
      return;
    }
    EditOp op = new EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = removedText;
    op.insertedText = insert;
    CursorTarget insertedEnd = computeCursorAfterInsert(sL, sC, insert);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = cursor.cursorLine;
    op.cursorCharAfter = cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void undo() {
    if (undoStack.isEmpty()) return;
    EditOp op = undoStack.removeLast();
    redoStack.addLast(op);
    if (!pendingEdits.isEmpty()) {
      pendingEdits.removeLast();
      pendingRedo.addLast(op);
    }
    isApplyingUndoRedo = true;
    applyEditForUndoRedo(
        op.startLine,
        op.startChar,
        op.insertedEndLine,
        op.insertedEndChar,
        op.removedText == null ? "" : op.removedText,
        op.cursorLineBefore,
        op.cursorCharBefore);
    isApplyingUndoRedo = false;
  }

  public void redo() {
    if (redoStack.isEmpty()) return;
    EditOp op = redoStack.removeLast();
    undoStack.addLast(op);
    if (!pendingRedo.isEmpty()) {
      pendingRedo.removeLast();
      pendingEdits.addLast(op);
    }
    isApplyingUndoRedo = true;
    applyEditForUndoRedo(
        op.startLine,
        op.startChar,
        op.endLine,
        op.endChar,
        op.insertedText == null ? "" : op.insertedText,
        op.cursorLineAfter,
        op.cursorCharAfter);
    isApplyingUndoRedo = false;
  }

  public void applyEditForUndoRedo(
      int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    replaceSelectionWithText(text);
    setCursorPosition(cursorLine, cursorChar);
    if (isWordWrapEnabled) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
    lineNumber.invalidateLineNumberCache();
    invalidate();
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }
    selection.selStartLine = startL;
    selection.selStartChar = Math.max(0, startC);
    selection.selEndLine = endL;
    selection.selEndChar = Math.max(0, endC);
    selection.hasSelection = !(selection.selStartLine == selection.selEndLine && selection.selStartChar == selection.selEndChar);
    selection.selecting = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
  }

  public void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    ime.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  public String readRangeText(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }

    if (startL >= windowStartLine && endL < windowStartLine + linesWindow.size()) {
      StringBuilder sb = new StringBuilder();
      for (int line = startL; line <= endL; line++) {
        String ln = getLineFromWindowLocal(line - windowStartLine);
        if (ln == null) ln = "";
        int from = (line == startL) ? Math.min(startC, ln.length()) : 0;
        int to = (line == endL) ? Math.min(endC, ln.length()) : ln.length();
        if (from < to) sb.append(ln, from, to);
        if (line < endL) sb.append('\n');
      }
      return sb.toString();
    }

    if (sourceFile == null || !sourceFile.exists()) return "";
    RangeBytes range = computeByteRangeFastOrScan(sourceFile, startL, startC, endL, endC);
    if (range == null) return "";
    try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
      long len = raf.length();
      long startByte = Math.max(0, Math.min(range.startByte, len));
      long endByte = Math.max(0, Math.min(range.endByte, len));
      if (endByte < startByte) {
        long t = startByte;
        startByte = endByte;
        endByte = t;
      }
      int size = (int) Math.min(Integer.MAX_VALUE, endByte - startByte);
      byte[] buf = new byte[size];
      raf.seek(startByte);
      raf.readFully(buf);
      return new String(buf, fileCharset);
    } catch (Exception ignore) {
      return "";
    }
  }

  public void replaceSelectionWithText(String insertText) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    final int opToken = editVersion.incrementAndGet();
    clearActiveSuggestion(); // Clear suggestion when replacing selection

    if (insertText == null) insertText = "";

    if (!selection.hasSelection) {
      if (!insertText.isEmpty()) insertTextAtCursor(insertText);
      // No selection means no large edit UI was started for it.
      updateSuggestion();
      return;
    }

    // Normalize selection
    int sL = selection.selStartLine, sC = selection.selStartChar, eL = selection.selEndLine, eC = selection.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = cursor.cursorLine;
    final int beforeChar = cursor.cursorChar;
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > UNDO_TEXT_LIMIT) {
        removedText = null;
      }
    }
    int removedNewlines = countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = countNewlines(insertText);

    final boolean selectAllLike = selection.isSelectAllActive || selection.isEntireFileSelected;
    beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    // This is the critical fix: The "Select All" path now correctly cleans up and finalizes the UI
    // state.
    if (selectAllLike) {
      // Reset all data structures to represent an empty document.
      synchronized (linesWindow) {
        linesWindow.clear();
        linesWindow.add("");
        windowStartLine = 0;
        isEof = true;
      }
      synchronized (modifiedLines) {
        modifiedLines.clear();
      }
      synchronized (lineWidthCache) {
        lineWidthCache.clear();
      }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;

      // Transition to in-memory mode for cleared content.
      isFileCleared = true;
      synchronized (lineOffsetsLock) {
        lineOffsets = new long[0];
      }
      isIndexReady = false;
      isIndexBuilding = false;
      isIndexDisabled = false;
      indexDisabledPath = null;
      indexDisabledFileLength = -1L;

      // Reset cursor, selection, and scroll position.
      cursor.cursorLine = 0;
      cursor.cursorChar = 0;
      selection.selStartLine = 0;
      selection.selEndLine = 0;
      selection.selStartChar = 0;
      selection.selEndChar = 0;
       scroll.scrollY =0;
      scroll.scrollX =0;
      clearSelectionStateAfterDelete();

      // Perform insertion if replacing text.
      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (linesWindow) {
          linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            linesWindow.add(i, newLines[i]);
          }
        }
        CursorTarget newPos = computeCursorAfterInsert(0, 0, insertText);
        cursor.cursorLine = newPos.line;
        cursor.cursorChar = newPos.ch;
      }

      // Crucially, end the large edit UI and force a redraw.
      onLineCountChanged();
      endLargeEditUi(true);
      recalculateMaxLineWidth();
      keepCursorVisibleHorizontally();
      requestLayout(); // Request layout to update gutter width after content cleared
      updateSuggestion();
      lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // same line + no '\n' => window-only fast path
    if (sL == eL && insertText.indexOf('\n') < 0) {
      ensureLineInWindow(sL, true);
      if (isWindowLoading && (sL < windowStartLine || sL >= windowStartLine + linesWindow.size())) {
        final String txtFinal = insertText;
        post(() -> replaceSelectionWithText(txtFinal));
        return;
      }

      int local = sL - windowStartLine;
      if (local >= 0 && local < linesWindow.size()) {
        synchronized (linesWindow) {
          String line = getLineFromWindowLocal(local);
          if (line == null) line = "";

          int a = Math.max(0, Math.min(sC, line.length()));
          int b = Math.max(0, Math.min(eC, line.length()));
          if (b < a) {
            int t = a;
            a = b;
            b = t;
          }

          String merged = line.substring(0, a) + insertText + line.substring(b);
          updateLocalLine(local, merged);
          modifiedLines.put(sL, merged);

          cursor.cursorLine = sL;
          cursor.cursorChar = a + insertText.length();

          computeWidthForLine(sL, merged);
          recalculateMaxLineWidth();
        }
      }

      clearSelectionStateAfterDelete();
      invalidate();
      keepCursorVisibleHorizontally();
      endLargeEditUi(false);
      updateSuggestion();
      lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // multi-line or inserted text contains '\n'
    final CursorTarget target = computeCursorAfterInsert(sL, sC, insertText);

    // Optional immediate UI update if fully in window
    boolean fullyInWindow = (sL >= windowStartLine) && (eL < windowStartLine + linesWindow.size());
    if (fullyInWindow) {
      applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
    } else {
      cursor.cursorLine = sL;
      cursor.cursorChar = sC;
    }

    clearSelectionStateAfterDelete();
    keepCursorVisibleHorizontally(); // This scrolls to the new cursor and invalidates.
    endLargeEditUi(false);

    if (sourceFile == null || isFileCleared) {
      if (!fullyInWindow) {
        ensureLineInWindow(sL, true);
        ensureLineInWindow(eL, true);
        applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      updateSuggestion();
      lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = sourceFile;
    // ابدأ إعادة كتابة الملف في الخلفية بدون تعطيل الواجهة وبدون دائرة تحميل.
    rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    updateSuggestion();
    lineCountDelta += (insertedNewlines - removedNewlines);
    recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  public void applyMultiLineReplaceInWindowNow(
      int sL, int sC, int eL, int eC, String insertText, CursorTarget target) {
    synchronized (linesWindow) {
      int oldLineCount = getLinesCount();
      int sLocal = sL - windowStartLine;
      int eLocal = eL - windowStartLine;
      if (sLocal < 0 || eLocal < 0 || sLocal >= linesWindow.size() || eLocal >= linesWindow.size())
        return;
      if (sLocal > eLocal) {
        int t = sLocal;
        sLocal = eLocal;
        eLocal = t;
      }

      String startLine = linesWindow.get(sLocal);
      String endLine = linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String mergedText = left + (insertText == null ? "" : insertText) + right;
      String[] parts = mergedText.split("\n", -1);

      linesWindow.set(sLocal, parts[0]);
      if (eLocal >= sLocal + 1) {
        linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      if (parts.length > 1) {
        List<String> toInsert = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) toInsert.add(parts[i]);
        linesWindow.addAll(sLocal + 1, toInsert);
      }

      cursor.cursorLine = Math.max(0, target.line);
      cursor.cursorChar = Math.max(0, target.ch);

      int newLineCount = getLinesCount();
      if (lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      onLineCountChanged();

      recalculateMaxLineWidth();
    }
  }

  public void rewriteReplaceRangeAsync(
      int opToken,
      File inFile,
      int sL,
      int sC,
      int eL,
      int eC,
      String insertText,
      CursorTarget target,
      boolean finishLargeEditUi) {
    ioHandler.post(
        () -> {
          try {
            if (inFile == null || !inFile.exists()) {
              post(
                  () -> {
                    if (finishLargeEditUi) endLargeEditUi(true);
                  });
              return;
            }

            RangeBytes range = computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
            if (range == null) {
              post(
                  () -> {
                    if (finishLargeEditUi) endLargeEditUi(true);
                  });
              return;
            }

            File outFile = File.createTempFile("popedit_", ".tmp", getContext().getCacheDir());
            byte[] insertBytes =
                (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);

            try (RandomAccessFile rafIn = new RandomAccessFile(inFile, "r");
                FileChannel inCh = rafIn.getChannel();
                RandomAccessFile rafOut = new RandomAccessFile(outFile, "rw");
                FileChannel outCh = rafOut.getChannel()) {

              long fileLen = rafIn.length();
              long startByte = Math.max(0, Math.min(range.startByte, fileLen));
              long endByte = Math.max(0, Math.min(range.endByte, fileLen));
              if (endByte < startByte) {
                long t = startByte;
                startByte = endByte;
                endByte = t;
              }

              transferRange(inCh, outCh, 0, startByte);

              if (insertBytes.length > 0) {
                outCh.write(ByteBuffer.wrap(insertBytes));
              }

              transferRange(inCh, outCh, endByte, fileLen - endByte);
              outCh.force(true);
            }

            post(
                () -> {
                  if (opToken != editVersion.get()) return;

                  invalidatePendingIO();

                  if (inFile != null) {
                    try (FileInputStream fis = new FileInputStream(outFile);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(inFile)) {
                      byte[] buf = new byte[8192];
                      int r;
                      while ((r = fis.read(buf)) > 0) {
                        fos.write(buf, 0, r);
                      }
                      fos.flush();
                    } catch (Exception ignore) {
                    }
                    outFile.delete();
                    sourceFile = inFile;
                  } else {
                    sourceFile = outFile;
                  }
                  isFileCleared = false;

                  synchronized (modifiedLines) {
                    modifiedLines.clear();
                  }
                  synchronized (lineWidthCache) {
                    lineWidthCache.clear();
                  }
                  currentMaxWindowLineWidth = 0f;
                  globalMaxLineWidth = 0f;
                  scroll.maxLineWidthForScroll = 0f;
                  scroll.maxTextStartXForScroll = 0f;
                  scroll.maxScrollXForScroll = 0f;
                  lineCountDelta = 0;

                  synchronized (lineOffsetsLock) {
                    lineOffsets = new long[0];
                  }
                  isIndexReady = false;
                  isIndexBuilding = false;
                  isIndexDisabled = false;
                  indexDisabledPath = null;
                  indexDisabledFileLength = -1L;
                  isEof = false;

                  ioHandler.post(this::buildFileIndex);
                  onLineCountChanged();

                  cursor.cursorLine = Math.max(0, target.line);
                  cursor.cursorChar = Math.max(0, target.ch);

                  // لا تعمل "Reload" للنافذة بعد الحذف/الاستبدال إذا كانت النتيجة ضمن النافذة
                  // الحالية.
                  // هذا يمنع دائرة التحميل ويمنع القفز/الزمن الطويل مع الملفات الضخمة.
                  boolean cursorInsideWindow =
                      (cursor.cursorLine >= windowStartLine
                          && cursor.cursorLine < windowStartLine + linesWindow.size());

                  if (cursorInsideWindow) {
                    // النافذة الحالية تم تعديلها مسبقاً (fast path)، فقط أعد حساب العرض وحدث الرسم.
                    synchronized (linesWindow) {
                      isEof = linesWindow.size() < windowSize + (prefetchLines * 2);
                    }
                    recalculateMaxLineWidth();
                    requestFocus();
                    InputMethodManager imm =
                        (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(this);
                    if (finishLargeEditUi) endLargeEditUi(false);
                    invalidate();
                  } else {
                    int targetStart = Math.max(0, cursor.cursorLine - prefetchLines);
                    loadWindowAround(
                        targetStart,
                        () -> {
                          String ln = getLineTextForRender(cursor.cursorLine);
                          cursor.cursorChar = Math.min(cursor.cursorChar, ln.length());
                          scroll.clampScrollY();
                          keepCursorVisibleHorizontally();
                          requestFocus();
                          InputMethodManager imm =
                              (InputMethodManager)
                                  getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                          if (imm != null) imm.restartInput(this);
                          if (finishLargeEditUi) endLargeEditUi(false);
                          invalidate();
                        });
                  }
                });

          } catch (Exception ex) {
            ex.printStackTrace();
            post(
                () -> {
                  if (finishLargeEditUi) endLargeEditUi(true);
                });
          }
        });
  }

  public RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tl = sL, tc = sC;
      sL = eL;
      sC = eC;
      eL = tl;
      eC = tc;
    }

    if (isIndexReady && file != null) {
      RangeBytes fast = computeByteRangeUsingIndex(file, sL, sC, eL, eC);
      if (fast != null) return fast;
    }

    return computeByteRangeByScanning(file, sL, sC, eL, eC);
  }

  public RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long startLineByte, endLineByte;
      synchronized (lineOffsetsLock) {
        if (!isIndexReady) return null;
        if (sL < 0 || eL < 0) return null;
        if (sL >= lineOffsets.length || eL >= lineOffsets.length) return null;
        startLineByte = lineOffsets[sL];
        endLineByte = lineOffsets[eL];
      }

      String startLineText = readLineUtf8AtByte(raf, startLineByte);
      String endLineText = (eL == sL) ? startLineText : readLineUtf8AtByte(raf, endLineByte);

      long startByte = startLineByte + computeByteOffsetInLineUtf8(startLineText, sC);
      long endByte = endLineByte + computeByteOffsetInLineUtf8(endLineText, eC);

      return new RangeBytes(startByte, endByte);
    } catch (Exception ignore) {
      return null;
    }
  }

  public void applyMultiLineDeleteInWindowNow(int sL, int sC, int eL, int eC) {
    synchronized (linesWindow) {
      int oldLineCount = getLinesCount();
      int sLocal = sL - windowStartLine;
      int eLocal = eL - windowStartLine;
      if (sLocal < 0 || eLocal >= linesWindow.size() || sLocal > eLocal) return;

      String startLine = linesWindow.get(sLocal);
      String endLine = linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String merged = left + right;

      linesWindow.set(sLocal, merged);
      if (eLocal > sLocal) {
        linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      modifiedLines.put(windowStartLine + sLocal, merged);
      for (int i = sLocal + 1; i < linesWindow.size(); i++) {
        modifiedLines.put(windowStartLine + i, linesWindow.get(i));
      }

      cursor.cursorLine = sL;
      cursor.cursorChar = left.length();

      recalculateMaxLineWidth();
      int newLineCount = getLinesCount();
      if (oldLineCount != newLineCount) {
        onLineCountChanged();
      }
    }
  }

  public void clearSelectionStateAfterDelete() {
    selection.hasSelection = false;
    selection.selecting = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    caret.resetBlink();
  }

  public static final class RangeBytes {
    final long startByte, endByte;

    RangeBytes(long s, long e) {
      startByte = s;
      endByte = e;
    }
  }

  public void transferRange(FileChannel inCh, FileChannel outCh, long position, long count)
      throws Exception {
    long remaining = count;
    long pos = position;
    while (remaining > 0) {
      long sent = inCh.transferTo(pos, remaining, outCh);
      if (sent <= 0) break;
      pos += sent;
      remaining -= sent;
    }
  }

  public RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tl = sL, tc = sC;
      sL = eL;
      sC = eC;
      eL = tl;
      eC = tc;
    }

    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long[] starts = findTwoLineStartBytesByScanning(raf, sL, eL);
      long startLineByte = starts[0];
      long endLineByte = starts[1];

      String startLineText = readLineUtf8AtByte(raf, startLineByte);
      String endLineText = (eL == sL) ? startLineText : readLineUtf8AtByte(raf, endLineByte);

      long startByte = startLineByte + computeByteOffsetInLineUtf8(startLineText, sC);
      long endByte = endLineByte + computeByteOffsetInLineUtf8(endLineText, eC);

      return new RangeBytes(startByte, endByte);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Fallback helper used when the line index is not ready. Returns the byte offset at which the
   * given 0-based line starts. This scans the file sequentially (O(n)) so it should only be used
   * for occasional operations like copy/cut when index isn't available.
   */
  public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    if (targetLine <= 0) return 0L;
    long[] starts = findTwoLineStartBytesByScanning(raf, targetLine, targetLine);
    return (starts != null && starts.length > 0) ? starts[0] : 0L;
  }

  public long[] findTwoLineStartBytesByScanning(RandomAccessFile raf, int lineA, int lineB)
      throws Exception {
    if (lineA < 0) lineA = 0;
    if (lineB < 0) lineB = 0;

    int a = Math.min(lineA, lineB);
    int b = Math.max(lineA, lineB);

    long offA = (a == 0) ? 0L : -1L;
    long offB = (b == 0) ? 0L : -1L;

    raf.seek(0);
    byte[] buf = new byte[8192];
    long pos = 0;
    int line = 0;

    while (true) {
      int n = raf.read(buf);
      if (n <= 0) break;

      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n') {
          line++;
          long nextLineStart = pos + i + 1;

          if (line == a && offA < 0) offA = nextLineStart;
          if (line == b && offB < 0) offB = nextLineStart;

          if (offA >= 0 && offB >= 0) {
            if (lineA <= lineB) return new long[] {offA, offB};
            return new long[] {offB, offA};
          }
        }
      }
      pos += n;
    }

    long len = raf.length();
    if (offA < 0) offA = len;
    if (offB < 0) offB = len;

    if (lineA <= lineB) return new long[] {offA, offB};
    return new long[] {offB, offA};
  }

  public String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
    raf.seek(byteOffset);
    ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
    byte[] buf = new byte[1024];
    boolean seenAny = false;

    while (true) {
      int n = raf.read(buf);
      if (n <= 0) break;

      int stop = -1;
      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n') {
          stop = i;
          break;
        }
      }

      if (stop >= 0) {
        seenAny = true;
        if (stop > 0 && buf[stop - 1] == '\r') {
          baos.write(buf, 0, stop - 1);
        } else {
          baos.write(buf, 0, stop);
        }
        break;
      } else {
        seenAny = true;
        baos.write(buf, 0, n);
      }

      if (baos.size() > 2_000_000) break;
    }

    if (!seenAny) return "";
    if (binarySafeRenderingEnabled) {
      byte[] data = baos.toByteArray();
      return bytesToControlVisible(data, data.length);
    }
    return baos.toString(fileCharset.name());
  }

  public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
    long start;
    long end;
    synchronized (lineOffsetsLock) {
      if (line < 0 || line >= lineOffsets.length) return 0L;
      start = lineOffsets[line];
      end = (line + 1 < lineOffsets.length) ? lineOffsets[line + 1] : fileLen;
    }
    long len = Math.max(0L, end - start);
    if (len <= 0L) return 0L;
    if (line + 1 < lineOffsets.length) {
      len -= 1L; // drop '\n'
      if (len > 0L) {
        raf.seek(Math.max(start, end - 2));
        int last = raf.read();
        if (last == '\r') {
          len -= 1L; // drop '\r' in CRLF
        }
      }
    }
    return Math.max(0L, len);
  }

  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
      throws Exception {
    int safeStart = Math.max(0, Math.min(startChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
    int safeEnd = Math.max(safeStart, Math.min(endChar, (int) Math.min(Integer.MAX_VALUE, lineByteLen)));
    int len = safeEnd - safeStart;
    if (len <= 0) return "";
    long startByte = lineStart + safeStart;
    raf.seek(startByte);
    byte[] buf = new byte[len];
    raf.readFully(buf);
    if (binarySafeRenderingEnabled) {
      return bytesToControlVisible(buf, buf.length);
    }
    return new String(buf, fileCharset);
  }

  public static final class StreamedCharSlice {
    final String text;
    final int length;

    StreamedCharSlice(String text, int length) {
      this.text = text;
      this.length = length;
    }
  }

  public StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
      throws Exception {
    int safeStart = Math.max(0, startChar);
    int safeEnd = Math.max(safeStart, endChar);
    CharsetDecoder decoder = fileCharset.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPLACE);
    decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

    StringBuilder sb = new StringBuilder(Math.max(0, safeEnd - safeStart));
    byte[] buf = new byte[8192];
    CharBuffer charBuf = CharBuffer.allocate(4096);
    int charIndex = 0;
    boolean done = false;
    raf.seek(lineStart);

    while (!done) {
      int n = raf.read(buf);
      if (n <= 0) break;

      int limit = n;
      boolean hitNewline = false;
      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n') {
          limit = i;
          if (limit > 0 && buf[limit - 1] == '\r') limit -= 1;
          hitNewline = true;
          break;
        }
      }

      ByteBuffer byteBuf = ByteBuffer.wrap(buf, 0, limit);
      while (true) {
        CoderResult cr = decoder.decode(byteBuf, charBuf, hitNewline);
        charBuf.flip();
        int remaining = charBuf.remaining();
        for (int i = 0; i < remaining; i++) {
          char c = charBuf.get();
          if (charIndex >= safeStart && charIndex < safeEnd) {
            sb.append(c);
          }
          charIndex++;
        }
        charBuf.clear();
        if (!cr.isOverflow()) break;
      }

      if (hitNewline) {
        done = true;
      } else if (!needTotalLength && charIndex >= safeEnd) {
        // We have enough chars for the slice; stop early if length isn't needed.
        return new StreamedCharSlice(sb.toString(), -1);
      }
    }

    decoder.flush(charBuf);
    charBuf.flip();
    while (charBuf.hasRemaining()) {
      char c = charBuf.get();
      if (charIndex >= safeStart && charIndex < safeEnd) {
        sb.append(c);
      }
      charIndex++;
    }

    return new StreamedCharSlice(sb.toString(), charIndex);
  }

  public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    if (lineText == null) return 0L;
    int safe = Math.max(0, Math.min(charIndex, lineText.length()));
    if (safe == 0) return 0L;
    return lineText.substring(0, safe).getBytes(fileCharset).length;
  }

  public int getCharIndexForX(String text, float x, int globalLine) {
    if (text == null || text.isEmpty()) return 0;
    if (isRtl) {
      float baseX = getRtlLineBaseX(text, globalLine);
      x -= baseX;
      float w =
          measureHighlightedSegmentWidth(
              text, globalLine, 0, getLogicalLineLength(globalLine, text));
      x = w - x;
    }
    if (x <= 0f) return 0;

    int len = getLogicalLineLength(globalLine, text);
    if (len > maxSyntaxLineLength) {
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

      // Choose nearest boundary between (count-1) and count based on midpoint of last glyph.
      float wPrev = (count > 1) ? paint.measureText(text, 0, count - 1) : 0f;
      float wCount = paint.measureText(text, 0, count);
      float mid = wPrev + (wCount - wPrev) * 0.5f;
      return (x < mid) ? (count - 1) : count;
    }

    if (measureWidthBuffer == null || measureWidthBuffer.length < textLen) {
      measureWidthBuffer = new float[textLen];
    }
    paint.getTextWidths(text, 0, textLen, measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < textLen; i++) {
      float adv = getCharAdvanceWidth(text.charAt(i), measureWidthBuffer[i], paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return i;
      if (x < current + adv) return i + 1;
      current += adv;
    }
    return textLen;
  }

  public int[] computeWordBounds(String line, int pos) {
    pos = Math.max(0, Math.min(pos, line.length()));
    if (line.length() == 0) return new int[] {0, 0};
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    if (Character.isWhitespace(line.charAt(pos))) {
      int i = pos;
      while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
      if (i >= line.length()) {
        i = pos - 1;
        while (i >= 0 && Character.isWhitespace(line.charAt(i))) i--;
      }
      if (i < 0) return new int[] {pos, pos};
      pos = i;
    }
    int start = pos;
    int end = pos;
    while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
    while (end < line.length() - 1 && !Character.isWhitespace(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  public boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  public int[] computeWordBoundsSmart(String line, int pos) {
    if (line == null || line.isEmpty()) return new int[] {0, 0};
    int len = line.length();
    int idx = Math.max(0, Math.min(pos, len - 1));
    if (!isWordChar(line.charAt(idx))) {
      if (idx > 0 && isWordChar(line.charAt(idx - 1))) {
        idx = idx - 1;
      } else if (idx + 1 < len && isWordChar(line.charAt(idx + 1))) {
        idx = idx + 1;
      } else {
        return new int[] {idx, idx};
      }
    }
    int start = idx;
    int end = idx;
    while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
    while (end < len - 1 && isWordChar(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  public static final String[] CONTROL_TOKENS =
      new String[] {
        "<NUL>", "<SOH>", "<STX>", "<ETX>", "<EOT>", "<ENQ>", "<ACK>", "<BEL>",
        "<BS>", "<TAB>", "<LF>", "<VT>", "<FF>", "<CR>", "<SO>", "<SI>",
        "<DLE>", "<DC1>", "<DC2>", "<DC3>", "<DC4>", "<NAK>", "<SYN>", "<ETB>",
        "<CAN>", "<EM>", "<SUB>", "<ESC>", "<FS>", "<GS>", "<RS>", "<US>"
      };

  public String bytesToControlVisible(byte[] buf, int len) {
    if (len <= 0) return "";
    StringBuilder sb = new StringBuilder(len * 2);
    for (int i = 0; i < len; i++) {
      int b = buf[i] & 0xFF;
      if (b >= 0x20 && b <= 0x7E) {
        sb.append((char) b);
      } else if (b <= 0x1F) {
        sb.append(CONTROL_TOKENS[b]);
      } else if (b == 0x7F) {
        sb.append("<DEL>");
      } else {
        sb.append("<0x");
        String hx = Integer.toHexString(b).toUpperCase();
        if (hx.length() < 2) sb.append('0');
        sb.append(hx).append('>');
      }
    }
    return sb.toString();
  }

  public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    int[] bounds = computeWordBoundsSmart(lineText, charIndex);
    ArrayList<TextRange> candidates =
        buildDoubleTapCandidates(lineText, charIndex, bounds[0], bounds[1]);
    if (candidates.isEmpty()) return false;

    boolean sameAnchor =
        line == selection.lastDoubleTapLine
            && bounds[0] == selection.lastDoubleTapWordStart
            && bounds[1] == selection.lastDoubleTapWordEnd;
    int currentIdx = findSelectionCandidateIndex(line, candidates);
    int nextIdx;
    if (sameAnchor) {
      if (currentIdx >= 0) {
        nextIdx = Math.min(currentIdx + 1, candidates.size() - 1);
      } else {
        nextIdx = Math.min(selection.lastDoubleTapStage + 1, candidates.size() - 1);
      }
    } else {
      nextIdx = 0;
    }

    TextRange pick = candidates.get(nextIdx);
    selection.selStartLine = selection.selEndLine = line;
    selection.selStartChar = pick.start;
    selection.selEndChar = pick.end;
    selection.hasSelection = true;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    selection.selecting = true;
    cursor.cursorLine = line;
    cursor.cursorChar = selection.selEndChar;
    selection.lastDoubleTapLine = line;
    selection.lastDoubleTapWordStart = bounds[0];
    selection.lastDoubleTapWordEnd = bounds[1];
    selection.lastDoubleTapStage = nextIdx;
    return true;
  }

  
  public boolean isPositionInsideSelection(int line, int ch) {
    if (!selection.hasSelection) return false;
    int sL = selection.selStartLine;
    int sC = selection.selStartChar;
    int eL = selection.selEndLine;
    int eC = selection.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selection.selEndLine;
      sC = selection.selEndChar;
      eL = selection.selStartLine;
      eC = selection.selStartChar;
    }
    if (comparePos(line, ch, sL, sC) < 0) return false;
    return comparePos(line, ch, eL, eC) <= 0;
  }

  public static final class TextRange {
    final int start;
    final int end;

    TextRange(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  public void addSelectionCandidate(List<TextRange> out, int start, int end, int lineLen) {
    if (out == null) return;
    int s = Math.max(0, Math.min(start, lineLen));
    int e = Math.max(0, Math.min(end, lineLen));
    if (e <= s) return;
    for (TextRange r : out) {
      if (r.start == s && r.end == e) return;
    }
    out.add(new TextRange(s, e));
  }

  public int findSelectionCandidateIndex(int line, List<TextRange> candidates) {
    if (!selection.hasSelection || candidates == null || candidates.isEmpty()) return -1;
    int sL = selection.selStartLine;
    int sC = selection.selStartChar;
    int eL = selection.selEndLine;
    int eC = selection.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selection.selEndLine;
      sC = selection.selEndChar;
      eL = selection.selStartLine;
      eC = selection.selStartChar;
    }
    if (sL != line || eL != line) return -1;
    for (int i = 0; i < candidates.size(); i++) {
      TextRange r = candidates.get(i);
      if (r.start == sC && r.end == eC) return i;
    }
    return -1;
  }

  public ArrayList<TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
    ArrayList<TextRange> out = new ArrayList<>(6);
    if (line == null) return out;
    int len = line.length();
    addSelectionCandidate(out, wStart, wEnd, len);

    TextRange quote = findEnclosingQuoteRange(line, charIndex);
    if (quote != null) {
      addSelectionCandidate(out, quote.start + 1, quote.end, len);
      addSelectionCandidate(out, quote.start, quote.end + 1, len);
    }

    TextRange bracket = findEnclosingBracketRange(line, charIndex);
    if (bracket != null) {
      addSelectionCandidate(out, bracket.start + 1, bracket.end, len);
      addSelectionCandidate(out, bracket.start, bracket.end + 1, len);
    }
    return out;
  }

  public boolean isQuoteChar(char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  @Nullable
  public TextRange findEnclosingQuoteRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<TextRange> ranges = new ArrayList<>();
    char current = 0;
    int start = -1;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (current == 0) {
        if (isQuoteChar(c) && !isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !isEscaped(line, i)) {
          ranges.add(new TextRange(start, i));
          current = 0;
          start = -1;
        }
      }
    }
    TextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (TextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }

  @Nullable
  public TextRange findEnclosingBracketRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<TextRange> ranges = new ArrayList<>();
    int[] stackIdx = new int[Math.max(8, len / 4)];
    char[] stackType = new char[stackIdx.length];
    int sp = 0;
    char currentQuote = 0;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (currentQuote != 0) {
        if (c == currentQuote && !isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !isEscaped(line, i)) {
        currentQuote = c;
        continue;
      }
      if (c == '(' || c == '[' || c == '{') {
        if (sp >= stackIdx.length) {
          int newSize = stackIdx.length * 2;
          int[] newIdx = new int[newSize];
          char[] newType = new char[newSize];
          System.arraycopy(stackIdx, 0, newIdx, 0, stackIdx.length);
          System.arraycopy(stackType, 0, newType, 0, stackType.length);
          stackIdx = newIdx;
          stackType = newType;
        }
        stackIdx[sp] = i;
        stackType[sp] = c;
        sp++;
        continue;
      }
      if (c == ')' || c == ']' || c == '}') {
        char want = (c == ')') ? '(' : (c == ']') ? '[' : '{';
        if (sp > 0 && stackType[sp - 1] == want) {
          int start = stackIdx[sp - 1];
          sp--;
          ranges.add(new TextRange(start, i));
        }
      }
    }
    TextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (TextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }

  public void insertTextAtCursor(String text) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    final int opToken = editVersion.incrementAndGet();

    if (text == null) return;
    if (text.isEmpty() && !selection.hasSelection) return;

    // FIX: لو فيه تحديد، لازم يكون replace ذري
    if (selection.hasSelection) {
      replaceSelectionWithText(text);
      return;
    }

    if (ime.hasComposing) {
      ime.hasComposing = false;
      ime.composingLength = 0;
    }

    if (text.isEmpty()) {
      invalidate();
      return;
    }

    final int beforeLine = cursor.cursorLine;
    final int beforeChar = cursor.cursorChar;

    // For very large pastes into a file-backed document, avoid expanding the in-memory window and
    // doing
    // expensive per-line work on the UI thread. Instead, apply the insert via the file rewrite
    // path.
    if (sourceFile != null && !isFileCleared && isLargePasteText(text)) {
      beginLargeEditUiIfNeeded(true, cursor.cursorLine, cursor.cursorLine, true);
      // Extend the watchdog for large paste operations; they can legitimately take longer than
      // the default safety timeout.
      mainHandler.removeCallbacks(largeEditUiWatchdog);
      mainHandler.postDelayed(largeEditUiWatchdog, 30_000);
      CursorTarget target = computeCursorAfterInsert(cursor.cursorLine, cursor.cursorChar, text);
      final File inFile = sourceFile;
      rewriteReplaceRangeAsync(
          opToken, inFile, cursor.cursorLine, cursor.cursorChar, cursor.cursorLine, cursor.cursorChar, text, target, true);
      updateSuggestion();
      lineCountDelta += countNewlines(text);
      if (text.length() <= UNDO_TEXT_LIMIT) {
        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = text;
        op.insertedEndLine = target.line;
        op.insertedEndChar = target.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = target.line;
        op.cursorCharAfter = target.ch;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
      return;
    }

    String[] parts = text.split("\n", -1);
    ensureLineInWindow(cursor.cursorLine, true);
    if (isWindowLoading
        && (cursor.cursorLine < windowStartLine || cursor.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertTextAtCursor(text));
      return;
    }

    int local = cursor.cursorLine - windowStartLine;
    if (local < 0 || local >= linesWindow.size()) {
      synchronized (linesWindow) {
        if (linesWindow.isEmpty()) {
          linesWindow.add("");
          local = 0;
        } else local = Math.max(0, Math.min(local, linesWindow.size() - 1));
      }
    }

    synchronized (linesWindow) {
      int oldLineCount = getLinesCount();
      String base = getLineFromWindowLocal(local);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(cursor.cursorChar, base.length()));
      String left = base.substring(0, pos);
      String right = base.substring(pos);

      if (parts.length == 1) {
        String modified = left + parts[0] + right;
        updateLocalLine(local, modified);
        modifiedLines.put(cursor.cursorLine, modified);
        lineWidthCache.remove(cursor.cursorLine);
        cursor.cursorChar += parts[0].length();
      } else {
        lineWidthCache.clear();
        String firstLine = left + parts[0];
        updateLocalLine(local, firstLine);
        modifiedLines.put(cursor.cursorLine, firstLine);

        List<String> linesToInsert = new ArrayList<>();
        for (int p = 1; p < parts.length - 1; p++) linesToInsert.add(parts[p]);

        String lastPart = parts[parts.length - 1];
        linesToInsert.add(lastPart + right);

        if (!linesToInsert.isEmpty()) linesWindow.addAll(local + 1, linesToInsert);
        for (int i = 0; i < linesToInsert.size(); i++) {
          modifiedLines.put(cursor.cursorLine + 1 + i, linesToInsert.get(i));
        }

        cursor.cursorLine += (parts.length - 1);
        cursor.cursorChar = lastPart.length();
        lineCountDelta += (parts.length - 1);
      }

      int newLineCount = getLinesCount();
      if (lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      if (parts.length > 1) {
        onLineCountChanged();
      }

      recalculateMaxLineWidth();
      keepCursorVisibleHorizontally();
      caret.resetBlink();
      invalidate();
    }
    updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = text;
    CursorTarget insertedEnd = computeCursorAfterInsert(beforeLine, beforeChar, text);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = cursor.cursorLine;
    op.cursorCharAfter = cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void ensureLineInWindow(int globalLine, boolean blockingIfAbsent) {
    clearActiveSuggestion(); // Clear suggestion when window/view changes
    if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) return;
    if (sourceFile != null) {
      int targetStart = Math.max(0, globalLine - prefetchLines);
      loadWindowAround(targetStart, null);
    }
  }

  public BufferedReader reopenReaderAtStart() {
    try {
      if (readerForFile != null) {
        try {
          readerForFile.close();
        } catch (Exception ignored) {
        }
        readerForFile = null;
      }
      if (sourceFile != null) {
        readerForFile =
            new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile), fileCharset));
        return readerForFile;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }


  public void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < linesWindow.size()) {
      linesWindow.set(localIdx, text);
      onLineContentChanged(windowStartLine + localIdx, text);
      clearStreamedLineInfo(windowStartLine + localIdx);
    }
  }

  public String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= linesWindow.size()) return null;
    return linesWindow.get(localIdx);
  }

  public int getStreamLineThreshold() {
    return Math.max(4096, maxSyntaxLineLength);
  }

  public boolean shouldStreamLineLength(int length) {
    if (isWordWrapEnabled) return false;
    return length > getStreamLineThreshold();
  }

  public int getStreamedLineLength(int globalLine) {
    synchronized (streamedLinesLock) {
      return streamedLineLengths.get(globalLine, -1);
    }
  }

  public int getStreamedLineSliceStart(int globalLine) {
    synchronized (streamedLinesLock) {
      return streamedLineSliceStarts.get(globalLine, 0);
    }
  }

  public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    synchronized (streamedLinesLock) {
      streamedLineLengths.put(globalLine, length);
      streamedLineSliceStarts.put(globalLine, sliceStart);
    }
  }

  public void clearStreamedLineInfo(int globalLine) {
    synchronized (streamedLinesLock) {
      streamedLineLengths.delete(globalLine);
      streamedLineSliceStarts.delete(globalLine);
    }
  }

  public void clearStreamedLineCaches() {
    synchronized (streamedLinesLock) {
      streamedLineLengths.clear();
      streamedLineSliceStarts.clear();
    }
    streamedSliceUpdatePending = false;
    streamedSliceUpdateToken++;
  }

  public boolean isSingleByteCharset() {
    try {
      if (binarySafeRenderingEnabled) return true;
      return fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
    } catch (Exception ignored) {
      return true;
    }
  }

  public int getLogicalLineLength(int globalLine, @Nullable String line) {
    String mod = modifiedLines.get(globalLine);
    if (mod != null) return mod.length();
    int len = (line == null) ? 0 : line.length();
    int longLen = getStreamedLineLength(globalLine);
    return (longLen > len) ? longLen : len;
  }

  public void computeWidthForLine(int globalIndex, String line) {
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
    if (logicalLen > maxSyntaxLineLength) {
      w = getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(), paint);
    }
    synchronized (lineWidthCache) {
      lineWidthCache.put(globalIndex, w);
    }
  }

  public float getWidthForLine(int globalIndex, String line) {
    synchronized (lineWidthCache) {
      Float v = lineWidthCache.get(globalIndex);
      if (v != null) return v;
    }
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
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

  public void showKeyboard() {
    if (isReadOnly) return;
    requestFocus();
    InputMethodManager imm =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(this, 0);
  }

  public void requestKeyboard() {
    showKeyboard();
  }

  public void handleAutoPairing(String text) {
    if (!isAutoPairingEnabled || text == null || text.length() == 0 || text.length() >= 100) return;

    char c = text.charAt(text.length() - 1);
    String closing = null;
    if (c == '(') closing = ")";
    else if (c == '{') closing = "}";
    else if (c == '[') closing = "]";
    else if (c == '"') closing = "\"";
    else if (c == '\'') closing = "'";
    else if (c == '`') closing = "`";
    else if (c == '*') {
      if (cursor.cursorChar >= 2) {
        String ln = getLineTextForRender(cursor.cursorLine);
        if (ln != null && ln.length() >= cursor.cursorChar && ln.charAt(cursor.cursorChar - 2) == '/') {
          closing = "*/";
        }
      }
    }

    if (closing != null) {
      insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        moveCursorLeft();
      }
    }
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return !isDisabled && !isReadOnly;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return ime.onCreateInputConnection(outAttrs);
  }

  public void updateImeSelection() {
    ime.updateImeSelection();
  }

  public boolean replaceWordAtCursorWith(CharSequence textSeq) {
    return ime.replaceWordAtCursorWith(textSeq);
  }

  public boolean tryReplaceWordFromImeCommit(String insert) {
    return ime.tryReplaceWordFromImeCommit(insert);
  }

  @Nullable
  public int[] getWordBoundsAtCursor() {
    return ime.getWordBoundsAtCursor();
  }

  public void markImeCommit(CharSequence textSeq) {
    ime.markImeCommit(textSeq);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return onTouch.onTouchEvent(event);
  }

  public boolean onTouchEventSuper(MotionEvent event) {
    return super.onTouchEvent(event);
  }

  public void updateHandlePosition(float touchX, float touchY) {
    onTouch.updateHandlePosition(touchX, touchY);
  }

  public void drawSelectionSegment(
      Canvas canvas,
      float left,
      float top,
      float right,
      float bottom,
      boolean roundTopLeft,
      boolean roundTopRight,
      boolean roundBottomRight,
      boolean roundBottomLeft,
      Paint paint) {
    onTouch.drawSelectionSegment(canvas, left, top, right, bottom, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, paint);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isDisabled) return true;
    if (isReadOnly) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          moveCursorLeft();
          return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          moveCursorRight();
          return true;
        case KeyEvent.KEYCODE_DPAD_UP:
          moveCursorUp();
          return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          moveCursorDown();
          return true;
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_FORWARD_DEL:
        case KeyEvent.KEYCODE_ENTER:
          return true;
      }
      if (event.isPrintingKey()) return true;
    }

    if (selection.hasSelection && event.isPrintingKey()) {
      int uc = event.getUnicodeChar();
      if (uc != 0) {
        String s = String.valueOf((char) uc);
        replaceSelectionWithText(s);
        charAnimation.startCharAnimationFromText(s);
      } else {
        replaceSelectionWithText("");
      }
      return true;
    }

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        moveCursorLeft();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        moveCursorRight();
        return true;
      case KeyEvent.KEYCODE_DPAD_UP:
        moveCursorUp();
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        moveCursorDown();
        return true;

      case KeyEvent.KEYCODE_DEL:
        if (selection.hasSelection) replaceSelectionWithText("");
        else deleteCharAtCursor();
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (selection.hasSelection) replaceSelectionWithText("");
        else deleteForwardAtCursor();
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (selection.hasSelection) replaceSelectionWithText("\n");
        else insertNewlineAtCursor();
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void moveCursorLeft() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selection.hasSelection) {
      int sL = selection.selStartLine, sC = selection.selStartChar;
      if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
        sL = selection.selEndLine;
        sC = selection.selEndChar;
      }
      cursor.cursorLine = sL;
      cursor.cursorChar = sC;
    } else if (cursor.cursorChar > 0) cursor.cursorChar--;
    else if (cursor.cursorLine > 0) {
      cursor.cursorLine--;
      String ln = getLineTextForRender(cursor.cursorLine);
      cursor.cursorChar = ln.length();
    }
    selection.hasSelection = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    caret.resetBlink();
    invalidate();
    keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorRight() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selection.hasSelection) {
      int eL = selection.selEndLine, eC = selection.selEndChar;
      if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
        eL = selection.selStartLine;
        eC = selection.selStartChar;
      }
      cursor.cursorLine = eL;
      cursor.cursorChar = eC;
    } else {
      String ln = getLineTextForRender(cursor.cursorLine);
      if (cursor.cursorChar < ln.length()) cursor.cursorChar++;
      else {
        int next = cursor.cursorLine + 1;
        if (!isEof || next < windowStartLine + linesWindow.size()) {
          cursor.cursorLine = next;
          cursor.cursorChar = 0;
        }
      }
    }
    selection.hasSelection = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    caret.resetBlink();
    invalidate();
    keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorUp() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selection.hasSelection) {
      int sL = selection.selStartLine, sC = selection.selStartChar;
      if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
        sL = selection.selEndLine;
        sC = selection.selEndChar;
      }
      cursor.cursorLine = sL;
      cursor.cursorChar = sC;
    }
    if (cursor.cursorLine > 0) {
      cursor.cursorLine--;
      String ln = getLineTextForRender(cursor.cursorLine);
      cursor.cursorChar = Math.min(cursor.cursorChar, ln.length());
    }
    selection.hasSelection = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    caret.resetBlink();
    invalidate();
    keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  public void moveCursorDown() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selection.hasSelection) {
      int eL = selection.selEndLine, eC = selection.selEndChar;
      if (comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
        eL = selection.selStartLine;
        eC = selection.selStartChar;
      }
      cursor.cursorLine = eL;
      cursor.cursorChar = eC;
    }
    int next = cursor.cursorLine + 1;
    if (!isEof || next < windowStartLine + linesWindow.size()) {
      cursor.cursorLine = next;
      String ln = getLineTextForRender(cursor.cursorLine);
      cursor.cursorChar = Math.min(cursor.cursorChar, ln.length());
    }
    selection.hasSelection = false;
    selection.isSelectAllActive = false;
    selection.isEntireFileSelected = false;
    caret.resetBlink();
    invalidate();
    keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    clearActiveSuggestion(); // Clear suggestion on focus change
    InputMethodManager imm =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (focused) {
      if (imm != null) imm.restartInput(this);
      caret.resetBlink();
    } else {
      if (hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
      mainHandler.removeCallbacks(blinkRunnable);
      isCursorVisible = true; // Make sure it's visible when not focused
      ime.hasComposing = false;
      selection.hasSelection = false;
    }
  }

  public void invalidateLineGlobal(int globalLine) {
    if (isWordWrapEnabled) {
      invalidate();
      return;
    }
    int idx = isCodeFoldingEnabled ? getVisibleIndexForGlobalLine(globalLine) : globalLine;
    float top = (idx * lineHeight) -  scroll.scrollY;
    invalidate(0, (int) Math.floor(top), getWidth(), (int) Math.ceil(top + lineHeight));
  }

  public void invalidateCursorArea() {
    if (isWordWrapEnabled) {
      invalidate();
      return;
    }
    invalidateLineGlobal(cursor.cursorLine);
  }

  public boolean isHeavyDrawSuppressed() {
    return false;
  }

  public int getLinesCount() {
    if (isFileCleared) {
      return Math.max(1, windowStartLine + linesWindow.size());
    }
    int windowCount = windowStartLine + linesWindow.size();
    if (isIndexReady && lineOffsets.length > 0) {
      boolean hasEdits;
      synchronized (modifiedLines) {
        hasEdits = !modifiedLines.isEmpty();
      }
      if (!hasEdits && lineCountDelta == 0) {
        return lineOffsets.length;
      }
      int count = lineOffsets.length + lineCountDelta;
      if (count < 1) count = 1;
      return Math.max(count, windowCount);
    }
    if (isEof) return windowStartLine + linesWindow.size();
    if (!linesWindow.isEmpty()) return windowStartLine + linesWindow.size();
    return -1;
  }

  public boolean isLineHiddenByFold(int line) {
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
    rebuildFoldIntervalsIfNeeded();
    for (int[] interval : foldIntervals) {
      if (line < interval[0]) return false;
      if (line <= interval[1]) return true;
    }
    return false;
  }

  public FoldRange getFoldRangeAtStart(int line) {
    if (!isCodeFoldingEnabled) return null;
    FoldRange range = foldRanges.get(line);
    return (range != null && range.collapsed) ? range : null;
  }

  public void rebuildFoldIntervalsIfNeeded() {
    if (!foldIntervalsDirty) return;
    foldIntervalsDirty = false;
    foldIntervals.clear();
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return;

    for (FoldRange range : foldRanges.values()) {
      if (!range.collapsed) continue;
      int start = range.startLine + 1;
      int end = range.endLine;
      if (end < start) continue;
      foldIntervals.add(new int[] {start, end});
    }
    if (foldIntervals.isEmpty()) return;

    Collections.sort(foldIntervals, (a, b) -> Integer.compare(a[0], b[0]));
    int write = 0;
    int[] cur = foldIntervals.get(0);
    for (int i = 1; i < foldIntervals.size(); i++) {
      int[] nxt = foldIntervals.get(i);
      if (nxt[0] <= cur[1] + 1) {
        cur[1] = Math.max(cur[1], nxt[1]);
      } else {
        foldIntervals.set(write++, cur);
        cur = nxt;
      }
    }
    foldIntervals.set(write++, cur);
    while (foldIntervals.size() > write) foldIntervals.remove(foldIntervals.size() - 1);
  }

  public int getHiddenLineCount() {
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return 0;
    rebuildFoldIntervalsIfNeeded();
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    int hidden = 0;
    for (int[] interval : foldIntervals) {
      int s = interval[0];
      int e = Math.min(interval[1], total - 1);
      if (e >= s) hidden += (e - s + 1);
    }
    return hidden;
  }

  public int getVisibleLineCount() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    int visible = Math.max(1, total - getHiddenLineCount());
    return visible;
  }

  public int getVisualIndexForLineAndChar(int line, int ch) {
    if (!isWrapMetricsUsableForLine(line)) {
      if (isCodeFoldingEnabled) return getVisibleIndexForGlobalLine(line);
      return Math.max(0, line);
    }
    int totalLines = wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = getLineTextForRender(safeLine);
    int[] starts = getWrapStartsForLine(safeLine, text);
    int seg = getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    return wrapLinePrefix[safeLine] + seg;
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    if (!isCodeFoldingEnabled) return visibleIndex;
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    int visibleTotal = getVisibleLineCount();
    int clamped = Math.max(0, Math.min(visibleIndex, Math.max(0, visibleTotal - 1)));
    int global = clamped;
    rebuildFoldIntervalsIfNeeded();
    for (int[] interval : foldIntervals) {
      if (global < interval[0]) break;
      global += (interval[1] - interval[0] + 1);
    }
    return Math.max(0, Math.min(global, total - 1));
  }

  public int getVisibleIndexForGlobalLine(int globalLine) {
    if (!isCodeFoldingEnabled) return globalLine;
    rebuildFoldIntervalsIfNeeded();
    int visible = globalLine;
    for (int[] interval : foldIntervals) {
      if (globalLine < interval[0]) break;
      if (globalLine <= interval[1]) return Math.max(0, interval[0] - 1);
      visible -= (interval[1] - interval[0] + 1);
    }
    return Math.max(0, visible);
  }

  public int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / lineHeight));
    if (isWordWrapEnabled) {
      return getVisualPositionForIndex(idx).line;
    }
    return mapVisibleIndexToGlobal(idx);
  }

  public boolean toggleFoldAtLine(int line) {
    if (!isCodeFoldingEnabled) return false;
    FoldRange existing = foldRanges.get(line);
    if (existing != null) {
      existing.collapsed = !existing.collapsed;
      foldIntervalsDirty = true;
      invalidate();
      return true;
    }

    FoldRange created = findFoldRangeForLine(line);
    if (created == null) return false;
    created.collapsed = true;
    foldRanges.put(created.startLine, created);
    if (created.isIndentFold) indentGuideIntervalsDirty = true;
    foldIntervalsDirty = true;
    invalidate();
    return true;
  }

  public FoldRange findFoldRangeForLine(int line) {
    if (!isCodeFoldingEnabled) return null;
    if (line < 0) return null;

    RandomAccessFile raf = null;
    try {
      if (sourceFile != null && isIndexReady) {
        raf = new RandomAccessFile(sourceFile, "r");
      }

      String ln = getLineTextForFoldScan(line, raf);
      if (ln == null) return null;

      HighlightLineState startState = getLineStateAtStart(line);
      boolean inBlockComment = startState.inBlockComment && isBlockCommentsEnabled;
      int stringState = startState.stringState;
      if (!isBlockCommentsEnabled) inBlockComment = false;
      if (!isMultiLineStringsEnabled && stringState != STRING_STATE_TRIPLE) stringState = 0;
      if (!isBacktickStringsEnabled && stringState == STRING_STATE_BACKTICK) stringState = 0;
      if (!isTripleQuoteStringsEnabled && stringState == STRING_STATE_TRIPLE) stringState = 0;

      if (inBlockComment || stringState != 0) return null;

      if (isIndentationBlocksEnabled && isIndentFoldCandidate(ln)) {
        FoldRange indentRange = findIndentFoldRangeForLine(line, raf);
        if (indentRange != null) return indentRange;
      }

      int scanIndex = 0;
      while (true) {
        FoldToken token = findFoldTokenInLine(ln, scanIndex);
        if (token == null) return null;

        if (token.isBlockComment) {
          int endLine = findBlockCommentEndLine(line, token.index, raf);
          if (endLine > line) {
            return new FoldRange(line, endLine, token.index, '/', '/', true, false);
          }
          scanIndex = token.index + 2;
          continue;
        }

        FoldMatch match = findMatchingBracketFrom(line, token.index, token.openChar, raf);
        if (match != null && match.endLine > line) {
          return new FoldRange(
              line, match.endLine, token.index, token.openChar, match.closeChar, false, false);
        }

        scanIndex = token.index + 1;
        if (scanIndex >= ln.length()) return null;
      }
    } catch (Exception ignored) {
      return null;
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public String getLineTextForFoldScan(int line, @Nullable RandomAccessFile raf) {
    if (line < 0) return null;
    String mod = modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }
    if (raf != null && isIndexReady) {
      long offset;
      synchronized (lineOffsetsLock) {
        if (line < 0 || line >= lineOffsets.length) return null;
        offset = lineOffsets[line];
      }
      try {
        return readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  public FoldRange findIndentFoldRangeForLine(int line, @Nullable RandomAccessFile raf) {
    if (!isIndentationBlocksEnabled) return null;
    String ln = getLineTextForFoldScan(line, raf);
    if (ln == null) return null;
    String trimmed = rstripWhitespace(ln);
    if (trimmed.isEmpty() || !trimmed.endsWith(":")) return null;

    int baseIndent = getIndentWidth(ln);
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = Math.max(line + 1, windowStartLine + linesWindow.size());

    int endLine = -1;
    int scanEnd = Math.min(totalLines, line + INDENT_FOLD_SCAN_LIMIT);
    for (int i = line + 1; i < scanEnd; i++) {
      String next = getLineTextForFoldScan(i, raf);
      if (next == null) break;
      String nextTrimmed = rstripWhitespace(next);
      if (nextTrimmed.isEmpty()) continue;
      int indent = getIndentWidth(next);
      if (indent <= baseIndent) {
        endLine = i - 1;
        break;
      }
      endLine = i;
    }

    if (endLine > line) {
      int openIdx = Math.max(0, trimmed.length() - 1);
      return new FoldRange(line, endLine, openIdx, ':', ':', false, true);
    }
    return null;
  }

  public static final class FoldToken {
    final int index;
    final boolean isBlockComment;
    final char openChar;

    FoldToken(int index, boolean isBlockComment, char openChar) {
      this.index = index;
      this.isBlockComment = isBlockComment;
      this.openChar = openChar;
    }
  }

  public static final class FoldMatch {
    final int endLine;
    final char closeChar;

    FoldMatch(int endLine, char closeChar) {
      this.endLine = endLine;
      this.closeChar = closeChar;
    }
  }

  public FoldToken findFoldTokenInLine(String line, int startIndex) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    int i = Math.max(0, startIndex);
    boolean inLineComment = false;
    boolean inBlockComment = false;
    int stringState = 0;

    while (i < len) {
      if (inLineComment) break;

      if (inBlockComment) {
        int end = findBlockCommentEnd(line, i);
        if (end < 0) return null;
        i = end + 2;
        inBlockComment = false;
        continue;
      }

      if (stringState != 0) {
        StringEndResult endResult = findStringEndForState(line, i, stringState);
        if (!endResult.found) return null;
        i = endResult.endIndex;
        stringState = 0;
        continue;
      }

      if (isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (isBlockCommentsEnabled
          && i + 1 < len
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !isTokenEscaped(line, i)) {
        return new FoldToken(i, true, '/');
      }

      if (isTripleQuoteStart(line, i) && !isEscaped(line, i)) {
        int end = findTripleQuoteEnd(line, i + 3);
        if (end < 0) return null;
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (isStringDelimiter(c) && !isEscaped(line, i)) {
        int end = findStringEnd(line, i + 1, c);
        if (end < 0) return null;
        i = end + 1;
        continue;
      }

      if (isOpeningBracket(c) && !isEscaped(line, i)) {
        if (c == '{') return new FoldToken(i, false, c);
      }
      i++;
    }
    for (int j = Math.max(0, startIndex); j < len; j++) {
      char c = line.charAt(j);
      if ((c == '(' || c == '[') && !isEscaped(line, j)) {
        return new FoldToken(j, false, c);
      }
    }
    return null;
  }

  public int findBlockCommentEndLine(
      int startLine, int startIndex, @Nullable RandomAccessFile raf) {
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = Math.max(startLine + 1, windowStartLine + linesWindow.size());

    for (int line = startLine; line < totalLines; line++) {
      String text = getLineTextForFoldScan(line, raf);
      if (text == null) break;
      int from = (line == startLine) ? Math.min(startIndex + 2, text.length()) : 0;
      int end = findBlockCommentEnd(text, from);
      if (end >= 0) return line;
    }
    return -1;
  }

  public FoldMatch findMatchingBracketFrom(
      int startLine, int startIndex, char openChar, @Nullable RandomAccessFile raf) {
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = Math.max(startLine + 1, windowStartLine + linesWindow.size());

    HighlightLineState startState = getLineStateAtStart(startLine);
    boolean inBlockComment = startState.inBlockComment && isBlockCommentsEnabled;
    int stringState = startState.stringState;
    if (!isBlockCommentsEnabled) inBlockComment = false;
    if (!isMultiLineStringsEnabled && stringState != STRING_STATE_TRIPLE) stringState = 0;
    if (!isBacktickStringsEnabled && stringState == STRING_STATE_BACKTICK) stringState = 0;
    if (!isTripleQuoteStringsEnabled && stringState == STRING_STATE_TRIPLE) stringState = 0;

    if (inBlockComment || stringState != 0) return null;

    int depth = 1;
    char closeChar = matchingBracket(openChar);

    for (int line = startLine; line < totalLines; line++) {
      String text = getLineTextForFoldScan(line, raf);
      if (text == null) break;
      int len = text.length();
      int i = (line == startLine) ? Math.min(startIndex + 1, len) : 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = findBlockCommentEnd(text, i);
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          StringEndResult endResult = findStringEndForState(text, i, stringState);
          if (!endResult.found) break;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (isLineCommentStart(text, i)) {
          inLineComment = true;
          break;
        }

        if (isBlockCommentsEnabled
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !isTokenEscaped(text, i)) {
          int end = findBlockCommentEnd(text, i + 2);
          if (end < 0) {
            inBlockComment = true;
            break;
          }
          i = end + 2;
          continue;
        }

        if (isTripleQuoteStart(text, i) && !isEscaped(text, i)) {
          int end = findTripleQuoteEnd(text, i + 3);
          if (end < 0) {
            if (isTripleQuoteStringsEnabled) {
              stringState = STRING_STATE_TRIPLE;
            }
            break;
          }
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (isStringDelimiter(c) && !isEscaped(text, i)) {
          int end = findStringEnd(text, i + 1, c);
          if (end < 0) {
            if (isMultiLineStringsEnabled) {
              stringState = getStringStateForDelimiter(c);
            }
            break;
          }
          i = end + 1;
          continue;
        }

        if (!isEscaped(text, i)) {
          if (c == openChar) depth++;
          else if (c == closeChar) {
            depth--;
            if (depth == 0) return new FoldMatch(line, closeChar);
          }
        }
        i++;
      }
    }
    return null;
  }

  

  

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

  public int clampSegmentEndForWrapIndicator(
      String line, int segStart, int segEnd, int wrapWidthPx) {
    if (segEnd <= segStart) return segEnd;
    float reserved = wordWrapIndicatorWidth + (wordWrapIndicatorPadPx * 2f);
    float available = wrapWidthPx - reserved;
    if (available <= 0f) return segStart;
    float width = measureTextWithVisualSpaces(line, segStart, segEnd, paint);
    if (width <= available) return segEnd;
    int end = segEnd;
    while (end > segStart) {
      end--;
      float w = measureTextWithVisualSpaces(line, segStart, end, paint);
      if (w <= available) break;
    }
    return end;
  }

  public float getBottomBarrierPadding() {
    float base = BOTTOM_SCROLL_OFFSET;
    float minSpace = MIN_BOTTOM_VISIBLE_SPACE;
    if (lineHeight > 0f) {
      base = Math.max(base, lineHeight * 2f);
      minSpace = Math.max(minSpace, lineHeight * 2f);
    }
    return Math.max(base, minSpace);
  }

  public float getKeyboardBarrierPadding() {
    if (keyboardHeight <= 0) return 0f;
    float minPad = (lineHeight > 0f) ? lineHeight * 2f : MIN_BOTTOM_VISIBLE_SPACE;
    float maxPad = (lineHeight > 0f) ? lineHeight * 3.5f : BOTTOM_SCROLL_OFFSET;
    float kbPad = keyboardHeight * 0.4f;
    return Math.max(minPad, Math.min(maxPad, kbPad));
  }

  public void keepCursorVisibleHorizontally() {
    if (scaleGestureDetector != null
        && (zoom.isScaling || scaleGestureDetector.isInProgress() || multiTouchActive)) {
      return;
    }
    int cursorVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    float cursorYTop = cursorVisualIndex * lineHeight;
    float cursorYBottom = cursorYTop + lineHeight;
    int viewHeight = getHeight() - keyboardHeight;
    if (viewHeight <= 0) viewHeight = getHeight();

    float bottomPadding =
        (keyboardHeight > 0) ? getKeyboardBarrierPadding() : getBottomBarrierPadding();
    float effectiveVisibleHeight = Math.max(0f, viewHeight - bottomPadding);
    float visibleTop =  scroll.scrollY;
    float visibleBottom =  scroll.scrollY + effectiveVisibleHeight;

    if (cursorYBottom > visibleBottom)  scroll.scrollY =cursorYBottom - (viewHeight - bottomPadding);
    else if (cursorYTop < visibleTop)  scroll.scrollY = cursorYTop;

    if (keyboardHeight > 0) {
      float keyboardTop = getHeight() - keyboardHeight;
      // Keep caret/handles above the keyboard with a stable cushion
      float paddingAboveKeyboard = getKeyboardBarrierPadding();
      float currentCursorViewY = cursorYBottom -  scroll.scrollY;
      if (currentCursorViewY >= keyboardTop - paddingAboveKeyboard) {
         scroll.scrollY =cursorYBottom - (getHeight() - keyboardHeight - paddingAboveKeyboard);
      }
    }
    scroll.clampScrollY();

    if (!isWordWrapEnabled) {
      String line = getLineTextForRender(cursor.cursorLine);
      int safeChar = Math.min(cursor.cursorChar, getLogicalLineLength(cursor.cursorLine, line));
      float cursorX = getCaretXForLine(line, cursor.cursorLine, safeChar);

      float viewLeft = isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
      float viewRight = isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
      float scrollMargin = 50f;
      float effectiveScrollX = getEffectiveScrollX();
      float cursorViewX = getTextStartX() + cursorX - effectiveScrollX;
      float minView = viewLeft + scrollMargin;
      float maxView = viewRight - scrollMargin;
      if (cursorViewX < minView) {
        effectiveScrollX = getTextStartX() + cursorX - minView;
      } else if (cursorViewX > maxView) {
        effectiveScrollX = getTextStartX() + cursorX - maxView;
      }
      float max = scroll.getMaxScrollXForClamp();
      float minEffective = isRtl ? -max : 0f;
      float maxEffective = isRtl ? 0f : max;
      if (effectiveScrollX < minEffective) effectiveScrollX = minEffective;
      if (effectiveScrollX > maxEffective) effectiveScrollX = maxEffective;
      scroll.scrollX =isRtl ? -effectiveScrollX : effectiveScrollX;
    } else {
      scroll.scrollX =0f;
    }

    scroll.clampScrollX();
    invalidate();
  }

  public void drawAutoSuggestion(
      Canvas canvas, String lineContent, int globalLine, float textBaselineY) {
    boolean allowSuggestion =
        activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine > lineContent.length()) {
      clearActiveSuggestion();
      return;
    }

    // Calculate X position where the suggestion starts
    float suggestionStartX_canvas = measureText(lineContent, cursorPositionInLine, globalLine);

    // Draw the suggestion text
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    // Calculate and store the tap area in VIEW coordinates
    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);

    // The canvas is translated by (getTextStartX() - effectiveScrollX, - scroll.scrollY)
    // To get view coordinates:
    // viewX = canvasX + (effectiveScrollX - getTextStartX())
    // viewY = canvasY +  scroll.scrollY

    float left_view = suggestionStartX_canvas + getTextStartX() - getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (isRtl) {
      float baseX = getRtlLineBaseX(lineContent, globalLine);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = globalLine * lineHeight -  scroll.scrollY;
    float bottom_view = (globalLine + 1) * lineHeight -  scroll.scrollY;

    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  public void populateDirectLinesForRange(
      int startLine, int endLineInclusive, java.util.Map<Integer, String> out) {
    if (out == null) return;
    if (sourceFile == null || !sourceFile.exists()) return;
    if (!isIndexReady) return;

    int start = Math.max(0, startLine);
    int end = Math.max(start, endLineInclusive);

    int maxLine = -1;
    synchronized (lineOffsetsLock) {
      maxLine = lineOffsets.length - 1;
    }
    if (maxLine < 0) return;
    if (start > maxLine) return;
    if (end > maxLine) end = maxLine;

    // Ensure we don't drop visible lines; range is already caller-bounded.

    // If cached, fill quickly first.
    synchronized (directLineCache) {
      for (int l = start; l <= end; l++) {
        String c = directLineCache.get(l);
        if (c != null) out.put(l, c);
      }
    }

    // Read missing contiguous segments in one go.
    int l = start;
    while (l <= end) {
      if (out.containsKey(l)) {
        l++;
        continue;
      }

      int segStart = l;
      int segEnd = l;
      while (segEnd + 1 <= end && !out.containsKey(segEnd + 1)) segEnd++;

      try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
        long fileLen = raf.length();
        for (int cur = segStart; cur <= segEnd; cur++) {
          long lineStart;
          synchronized (lineOffsetsLock) {
            if (cur >= lineOffsets.length) break;
            lineStart = lineOffsets[cur];
          }
          long lineByteLen = getLineByteLengthFromIndex(raf, cur, fileLen);
          int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
          String ln;
          if (shouldStreamLineLength(lineLen)) {
            computeStreamedSliceBounds(null, cur, lineLen, streamedSliceTmp);
            int sliceStart = streamedSliceTmp[0];
            int sliceEnd = streamedSliceTmp[1];
            if (isSingleByteCharset()) {
              ln = readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
              setStreamedLineInfo(cur, lineLen, sliceStart);
            } else {
              StreamedCharSlice slice =
                  readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
              ln = slice.text;
              setStreamedLineInfo(cur, slice.length, sliceStart);
            }
          } else {
            ln = readLineUtf8AtByte(raf, lineStart);
          }
          out.put(cur, (ln == null) ? "" : ln);
        }
      } catch (Exception ignored) {
        // ignore: fallback to blank
      }

      l = segEnd + 1;
    }

    // Update cache
    synchronized (directLineCache) {
      for (java.util.Map.Entry<Integer, String> e : out.entrySet()) {
        if (e.getKey() >= start && e.getKey() <= end) {
          directLineCache.put(e.getKey(), (e.getValue() == null) ? "" : e.getValue());
        }
      }
    }
  }

  public String getLineTextForRenderWithDirect(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    if (line < 0) return "";

    // Window first
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = getLineFromWindowLocal(line - windowStartLine);
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
    synchronized (directLineCache) {
      String c = directLineCache.get(line);
      if (c != null) return c;
    }

    return "";
  }

  // Render-safe line getter (NO file random read here)
  public String getLineTextForRender(int line) {
    if (line < 0) return "";
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }
    String mod = modifiedLines.get(line);
    return (mod != null) ? mod : "";
  }

  public long[] buildIndexJava(String filepath) {
    long numNewlines = 0;
    long fileLength = 0;

    try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
      fileLength = raf.length();
      if (fileLength == 0) {
        return new long[0]; // Empty file has no lines
      }

      byte[] buffer = new byte[8192];
      long currentReadPos = 0;
      while (currentReadPos < fileLength) {
        raf.seek(currentReadPos);
        int bytesRead = raf.read(buffer);
        if (bytesRead == -1) break; // EOF

        for (int i = 0; i < bytesRead; i++) {
          if (buffer[i] == '\n') {
            numNewlines++;
          }
        }
        currentReadPos += bytesRead;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null; // Return null on error
    }

    // The number of lines is (number of newlines) + 1.
    // So the array size needs to be numNewlines + 1.
    if (numNewlines >= Integer.MAX_VALUE - 1) {
      isIndexDisabled = true;
      indexDisabledPath = filepath;
      indexDisabledFileLength = fileLength;
      return null;
    }
    long lines = numNewlines + 1;
    long bytesRequired = lines * (long) Long.BYTES;
    long maxMemory = Runtime.getRuntime().maxMemory();
    long maxIndexBytes = Math.min(MAX_INDEX_BYTES_HARD, Math.max(16L * 1024 * 1024, maxMemory / 6));
    if (bytesRequired > maxIndexBytes) {
      isIndexDisabled = true;
      indexDisabledPath = filepath;
      indexDisabledFileLength = fileLength;
      return null;
    }

    long[] offsetsArray = new long[(int) lines];
    int currentOffsetIndex = 0;

    try (RandomAccessFile raf = new RandomAccessFile(filepath, "r")) {
      offsetsArray[currentOffsetIndex++] = 0L; // First line starts at offset 0
      long currentPos = 0;
      byte[] buffer = new byte[8192];
      while (currentPos < fileLength) {
        raf.seek(currentPos);
        int bytesRead = raf.read(buffer);
        if (bytesRead == -1) break; // EOF

        for (int i = 0; i < bytesRead; i++) {
          if (buffer[i] == '\n') {
            // Store the offset of the character *after* the newline
            long nextStart = currentPos + i + 1;
            if (currentOffsetIndex < offsetsArray.length) {
              offsetsArray[currentOffsetIndex++] = nextStart;
            } else {
              // This should not happen if the first pass line counting is correct.
              // But as a safeguard, if we somehow counted more newlines than array size, break.
              break;
            }
          }
        }
        currentPos += bytesRead;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null; // Return null on error
    }

    return offsetsArray;
  }

  public void cancelAndCloseReader() {
    ioHandler.post(
        () -> {
          try {
            if (readerForFile != null) {
              readerForFile.close();
              readerForFile = null;
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }

  public boolean hasSelection() {
    return selection.hasSelection;
  }

  public void setCursorPosition(int line, int ch) {
    cursor.setCursorPosition(line, ch);
  }

  public void clearSelection() {
    selection.clearSelection();
  }

  public void selectAll() {
    selection.selectAll();
  }

  public void release() {
    cancelAndCloseReader();
    if (charAnimation.charAnimAnimator != null) charAnimation.charAnimAnimator.cancel();
    if (charAnimation.delAnimAnimator != null) charAnimation.delAnimAnimator.cancel();
    removeCallbacks(cursorAnimation.cursorAnimStep);
    ioThread.quitSafely();
  }
}
