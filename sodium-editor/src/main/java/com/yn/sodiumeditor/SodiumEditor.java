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
import com.yn.sodiumeditor.Input.events.OnKeyDown;
import com.yn.sodiumeditor.ColorCodeHighlight;
import com.yn.sodiumeditor.BracketGuides;
import com.yn.sodiumeditor.WhitespaceGuides;
import com.yn.sodiumeditor.UrlUnderline;
import com.yn.sodiumeditor.Highlite;
import com.yn.sodiumeditor.PathUnderline;
import com.yn.sodiumeditor.IndentGuides;
import com.yn.sodiumeditor.AutoBracketPair;
import com.yn.sodiumeditor.AutoBracketNewline;
import com.yn.sodiumeditor.Search;
import com.yn.sodiumeditor.BinaryRender;
import com.yn.sodiumeditor.LoadingCircle;
import com.yn.sodiumeditor.TextRender;
import com.yn.sodiumeditor.BracketMatchManager;
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
  
  
  
  
  
  public final int[] tmpLocationInWindow = new int[2];
  // visual padding constants
  
  // scroll state (pixels)
  

  // sliding window
  public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 1000; // 2000 yyy
  public int prefetchLines = 1000; // 1000 yyy

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
  public int lineWidthCacheSize = 1000; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  
  public int maxSyntaxLineLength = 4096;
  public int prefetchCols = 1000;
  public int colsWidthCacheSize = 1000;
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
  
  

 
  
  // Scroll for handling scroll logic
  public final Scroll scroll;

  // --- Zoom State ---

  public ScaleGestureDetector scaleGestureDetector;
  
  // Zoom for handling zoom logic
  public final Zoom zoom;

  // Ime for handling IME logic
  public final Ime ime;

  // OnTouch for handling touch events
  public final OnTouch onTouch;

  // OnScroll for handling scroll gestures
  public final OnScroll onScroll;

  // OnKeyDown for handling key down events
  public final OnKeyDown onKeyDown;



  // ColorCodeHighlight for handling color code highlighting
  public final ColorCodeHighlight colorCodeHighlight;

  // BracketGuides for handling bracket guides
  public final BracketGuides bracketGuides;

  // BracketMatch for handling bracket matching
  public final BracketMatchManager bracketMatchManager;

  // WhitespaceGuides for handling whitespace guides
  public final WhitespaceGuides whitespaceGuides;

  // UrlUnderline for handling URL underlining
  public final UrlUnderline urlUnderline;

  // PathUnderline for handling path underlining
  public final PathUnderline pathUnderline;

  // IndentGuides for handling indent guides
  public final IndentGuides indentGuides;

  // AutoBracketPair for handling automatic bracket pairing
  public final AutoBracketPair autoBracketPair;

  // AutoBracketNewline for handling automatic bracket newline
  public final AutoBracketNewline autoBracketNewline;

  // Search for handling search functionality
  public final Search search;

  // BinaryRender for handling binary-safe rendering
  public final BinaryRender binaryRender;

  // Popup for handling popup menu logic
  public final Popup popup;
public final TextRender textRender;
  // CursorAnimation for handling cursor movement animation
  public final CursorAnimation cursorAnimation;

  // CharAnimation for handling character fade animations
  public final CharAnimation charAnimation;

public final LineNumber lineNumber;

public final LoadingCircle loadingCircle;
  // --- Search State ---

  public boolean isSearchActive() {
    return search.isSearchActive();
  }

  public void clearSearchMatchCache() {
    search.clearSearchMatchCache();
  }

  public String getSearchCacheKey() {
    return search.getSearchCacheKey();
  }

  public int[] getSearchMatchSpansForLine(String line, int globalLine) {
    return search.getSearchMatchSpansForLine(line, globalLine);
  }

  public void drawSearchHighlightsForLine(
      Canvas canvas, String line, int globalLine, float top, float bottom) {
    search.drawSearchHighlightsForLine(canvas, line, globalLine, top, bottom);
  }

  public void drawSearchHighlightsForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float top,
      float bottom) {
    search.drawSearchHighlightsForSegment(canvas, line, globalLine, segStart, segEnd, top, bottom);
  }

  public boolean goToSearchMatch(boolean forward) {
    return search.goToSearchMatch(forward);
  }

  public Search.SearchMatch findNextSearchMatchFrom(int line, int charIndex) {
    return search.findNextSearchMatchFrom(line, charIndex);
  }

  public Search.SearchMatch findPrevSearchMatchFrom(int line, int charIndex) {
    return search.findPrevSearchMatchFrom(line, charIndex);
  }

  public Search.SearchMatch findNextSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer maxStartInclusive,
      java.util.HashMap<Integer, String> direct) {
    return search.findNextSearchMatchInRange(startLine, endLine, startCharExclusive, maxStartInclusive, direct);
  }

  public Search.SearchMatch findPrevSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer minStartInclusive,
      java.util.HashMap<Integer, String> direct) {
    return search.findPrevSearchMatchInRange(startLine, endLine, startCharExclusive, minStartInclusive, direct);
  }

  public Search.SearchMatch findMatchForwardInLine(
      String line, int fromIndex, @Nullable Integer maxStartInclusive) {
    return search.findMatchForwardInLine(line, fromIndex, maxStartInclusive);
  }

  public Search.SearchMatch findMatchBackwardInLine(
      String line, int fromIndex, @Nullable Integer minStartInclusive) {
    return search.findMatchBackwardInLine(line, fromIndex, minStartInclusive);
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
    if (!wordWrap.isWordWrapEnabled) {
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
    int anchorFirstVisual = Math.max(0, (int) ( scroll.scrollY / textRender.lineHeight));
    WordWrap.VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    wordWrap.wrapLineCounts = zoom.pendingWrapPrefixCounts;
    wordWrap.wrapLinePrefix = zoom.pendingWrapPrefixPrefix;
    wordWrap.totalWrapVisualLines = zoom.pendingWrapPrefixTotalVisualLines;
    wordWrap.wrapMetricsWidth = zoom.pendingWrapPrefixWidthPx;
    wordWrap.wrapMetricsReady = true;
    wordWrap.wrapPrefixValidUpToLine = Math.max(wordWrap.wrapPrefixValidUpToLine, zoom.pendingWrapPrefixValidUpToLine);

    zoom.pendingApplyWrapPrefixUpdate = false;
    zoom.pendingWrapPrefixCounts = null;
    zoom.pendingWrapPrefixPrefix = null;

    if (anchorLine >= 0 && wordWrap.wrapLinePrefix != null && anchorLine < wordWrap.wrapLinePrefix.length) {
      int newAnchorFirstVisual = wordWrap.wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        scroll.scrollY += dv * textRender.lineHeight;
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

  public float autoScrollX = 0f, autoScrollY = 0f;
  public float lastTouchX = 0f, lastTouchY = 0f;

  // keyboard awareness
  public final Rect visibleDisplayFrame = new Rect();
  public int keyboardHeight = 0;

  // selection handles





  public int drawBaseLine = 0;

  

  

  
  // Code fold manager
  public final CodeFold codeFold;

  // Current line highlight manager
  public final CurrentLineHighlight currentLineHighlight;

  // Click after end to add line manager
  public final ClickAfterEndToAddLine clickAfterEndToAddLine;

  // --- Current Line Highlight State ---
  public boolean isIndentationBlocksEnabled = false;


  public volatile boolean isWindowLoading = false;

  // Bracket cache for fast fold and bracket matching
  public final BracketCache bracketCache;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;
  public final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables

  // Edit operators manager
  public final EditOperators editOperators;

  // index
  public final Object lineOffsetsLock = new Object();
  public long[] lineOffsets = new long[0];
  public volatile boolean isIndexReady = false;
  public volatile boolean isIndexBuilding = false;
  public volatile boolean isIndexDisabled = false;
  @Nullable public volatile String indexDisabledPath = null;
  public volatile long indexDisabledFileLength = -1L;
  public static final long MAX_INDEX_BYTES_HARD = 64L * 1024 * 1024;

  // edit version (to ignore old rewrite results) - delegated to editOperators
  public AtomicInteger getEditVersion() { return editOperators.editVersion; }
  public int getLineCountDelta() { return editOperators.lineCountDelta; }
  public void setLineCountDelta(int delta) { editOperators.lineCountDelta = delta; }
  public boolean isApplyingUndoRedo() { return editOperators.isApplyingUndoRedo; }
  public void setApplyingUndoRedo(boolean applying) { editOperators.isApplyingUndoRedo = applying; }
  public long getLastEditTimestamp() { return editOperators.lastEditTimestamp; }
  public void setLastEditTimestamp(long ts) { editOperators.lastEditTimestamp = ts; }

  // Large edit UI (brief busy indicator)
  
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

  // Syntax highlighting manager
  public final Highlite highlite;

  // --- Syntax Highlighting State ---
  // Deprecated: Use highlite instead
  @Deprecated public final java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();
  @Deprecated @Nullable public TextRender.HighlightRule lineCommentHighlightRule;
  @Deprecated public final List<TextRender.HighlightRule> highlightRules = new ArrayList<>();
  @Deprecated public TextRender.HighlightRule stringHighlightRule;
  @Deprecated public TextRender.HighlightRule blockCommentHighlightRule;
  @Deprecated public final ArrayList<TextRender.HighlightRule> regexHighlightRules = new ArrayList<>();
  @Deprecated public final LinkedHashMap<Integer, List<TextRender.HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<TextRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<TextRender.HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  @Deprecated public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  @Deprecated public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  
  
  
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

  // Word wrap manager
  public final WordWrap wordWrap;
  
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

  

  
   

    


  public final Runnable delayedWindowCheck =
      new Runnable() {
        @Override
        public void run() {
          checkAndLoadWindow();
        }
      };

  public SodiumEditor(Context ctx, @Nullable AttributeSet attrs) {
    super(ctx, attrs);

    textRender = new TextRender(this);
    lineNumber = new LineNumber(this);
    currentLineHighlight = new CurrentLineHighlight(this);
    codeFold = new CodeFold(this);
    clickAfterEndToAddLine = new ClickAfterEndToAddLine(this);
    highlite = new Highlite(this);

    float density = getContext().getResources().getDisplayMetrics().density;

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

    // Initialize OnKeyDown
    onKeyDown = new OnKeyDown(this);

 

    // Initialize ColorCodeHighlight
    colorCodeHighlight = new ColorCodeHighlight(this);

    // Initialize BracketGuides
    bracketGuides = new BracketGuides(this);

    // Initialize BracketMatch
    bracketMatchManager = new BracketMatchManager(this);

    // Initialize WhitespaceGuides
    whitespaceGuides = new WhitespaceGuides(this);

    // Initialize UrlUnderline
    urlUnderline = new UrlUnderline(this);

    // Initialize PathUnderline
    pathUnderline = new PathUnderline(this);

    // Initialize IndentGuides
    indentGuides = new IndentGuides(this);

    // Initialize AutoBracketPair
    autoBracketPair = new AutoBracketPair(this);

    // Initialize AutoBracketNewline
    autoBracketNewline = new AutoBracketNewline(this);

    // Initialize Search
    search = new Search(this);

    // Initialize BinaryRender
    binaryRender = new BinaryRender(this);

    // Initialize Popup
    popup = new Popup(this);
    loadingCircle = new LoadingCircle(this);

    // Initialize EditOperators
    editOperators = new EditOperators(this);

    // Initialize CursorAnimation
    cursorAnimation = new CursorAnimation(this);

    // Initialize CharAnimation
    charAnimation = new CharAnimation(this);

    // Initialize bracket cache
    bracketCache = new BracketCache(this);

    // Initialize Cursor management
    cursor = new Cursor(this);
    caret = new Caret(this, cursor);
    cursorHandle = new CursorHandle(this, cursor, caret);
    selection = new Selection(this, cursor);
    selectionHandles = new SelectionHandles(this, selection);

    // Initialize WordWrap
    wordWrap = new WordWrap(this);
    
    
    textRender.paint.setTextSize(36);
    textRender.paint.setColor(0xFF000000);
    textRender.paint.setAntiAlias(true);
    textRender.paint.setSubpixelText(true);
    textRender.paint.setHinting(Paint.HINTING_ON);
    textRender.paint.setUnderlineText(false); // Explicitly disable underlines to fix visual artifact
    textRender.baseTypeface = (textRender.paint.getTypeface() != null) ? textRender.paint.getTypeface() : Typeface.DEFAULT;
    textRender.lineHeight = textRender.paint.getFontSpacing();
    lineNumber.lineNumbersPaint.setTextSize(36);
    selectionHandles.baseHandleTextSizePx = textRender.paint.getTextSize();
    cursor.baseCursorTextSizePx = textRender.paint.getTextSize();
    textRender.whitespaceStringRule =
        new TextRender.HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            TextRender.HighlightRuleType.STRING);
    textRender.whitespaceCommentRule =
        new TextRender.HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            TextRender.HighlightRuleType.BLOCK_COMMENT);

    selection.selectionPaint.setStyle(Paint.Style.FILL);
    caret.caretPaint.setStyle(Paint.Style.STROKE);
    caret.caretPaint.setStrokeCap(Paint.Cap.BUTT);
    selectionHandles.handlePaint.setStyle(Paint.Style.FILL);
    loadingCircle.loadingCirclePaint.setStyle(Paint.Style.STROKE);
    loadingCircle.loadingCirclePaint.setStrokeCap(Paint.Cap.ROUND);


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
    suggestionPaint.set(textRender.paint);
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
    binaryRender.setBinarySafeRenderingEnabled(enabled);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
    invalidate();
  }


  public void setHighlightCurrentSearchMatchEnabled(boolean enabled) {
    search.setCurrentSearchMatchHighlightEnabled(enabled);
  }

  public void setCurrentSearchMatchColor(int color) {
    search.setCurrentSearchMatchHighlightColor(color);
  }

  public void setWordWrapEnabled(boolean enabled) {
    wordWrap.setWordWrapEnabled(enabled);
  }

  public void setWordWrapIndicatorEnabled(boolean enabled) {
    wordWrap.setWordWrapIndicatorEnabled(enabled);
  }

  public void setWordWrapIndicatorColor(int color) {
    wordWrap.setWordWrapIndicatorColor(color);
  }

  public void setWordWrapIndicatorTextSize(float sizeSp) {
    wordWrap.setWordWrapIndicatorTextSize(sizeSp);
  }

  public void setVisibleCharPadding(int paddingChars) {
    int safe = Math.max(0, paddingChars);
    if (textRender.visibleCharPadding == safe) return;
    textRender.visibleCharPadding = safe;
    invalidate();
  }

  public void setStableGlyphPositionsEnabled(boolean enabled) {
    if (this.textRender.isStableGlyphPositionsEnabled == enabled) return;
    this.textRender.isStableGlyphPositionsEnabled = enabled;
    invalidate();
  }

  public void setPerformanceModeEnabled(boolean enabled) {
    if (this.textRender.isPerformanceModeEnabled == enabled) return;
    this.textRender.isPerformanceModeEnabled = enabled;
    if (enabled) {
      setUrlUnderliningEnabled(false);
      setPathUnderliningEnabled(false);
      colorCodeHighlight.setColorCodeHighlightingEnabled(false);
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

  // WordWrap delegation methods
  public boolean isWordWrapEnabled() {
    return wordWrap.isWordWrapEnabled;
  }

  public float getWrapWidth() {
    return wordWrap.getWrapWidth();
  }

  public int getTotalVisualLineCount() {
    return wordWrap.getTotalVisualLineCount();
  }

  public WordWrap.VisualLinePosition getVisualPositionForIndex(int visualIndex) {
    return wordWrap.getVisualPositionForIndex(visualIndex);
  }

  public void invalidateWrapMetrics() {
    wordWrap.invalidateWrapMetrics();
  }

  public void invalidateWrapMetrics(boolean clearExisting) {
    wordWrap.invalidateWrapMetrics(clearExisting);
  }

  public void invalidateWrapMetrics(boolean clearExisting, boolean scheduleFullRebuild) {
    wordWrap.invalidateWrapMetrics(clearExisting, scheduleFullRebuild);
  }

  public void requestWrapPrefixRebuild() {
    wordWrap.requestWrapPrefixRebuild();
  }

  public void cancelWrapPrefixRebuildForInteraction() {
    wordWrap.cancelWrapPrefixRebuildForInteraction();
  }

  public void cancelWrapWorkForPriority() {
    wordWrap.cancelWrapWorkForPriority();
  }

  public boolean shouldSuppressWrapMetricsForFastSelectAll() {
    return wordWrap.shouldSuppressWrapMetricsForFastSelectAll();
  }

  public void scheduleWrapPrefixRebuildUpToWindow() {
    wordWrap.scheduleWrapPrefixRebuildUpToWindow();
  }

  public void onLineContentChanged(int globalLine, @Nullable String newText) {
    wordWrap.onLineContentChanged(globalLine, newText);
  }

  public void onLineCountChanged() {
    wordWrap.onLineCountChanged();
  }

  public void buildWrapMetricsForWindowSnapshot() {
    wordWrap.buildWrapMetricsForWindowSnapshot();
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(int widthPx) {
    wordWrap.scheduleWrapMetricsSnapshotIfNeeded(widthPx);
  }

  public void scheduleWrapMetricsBuild() {
    wordWrap.scheduleWrapMetricsBuild();
  }

  public void buildWrapMetricsInMemory() {
    wordWrap.buildWrapMetricsInMemory();
  }

  public void buildWrapMetricsFromFile(int token, int widthPx, Paint wrapPaint) {
    wordWrap.buildWrapMetricsFromFile(token, widthPx, wrapPaint);
  }

  public int computeWrapCountForLine(String line, int widthPx) {
    return wordWrap.computeWrapCountForLine(line, widthPx);
  }

  public int computeWrapCountForLine(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    return wordWrap.computeWrapCountForLine(line, widthPx, p, useSharedBuffer);
  }

  public int[] getWrapStartsForLine(int globalLine, String line) {
    return wordWrap.getWrapStartsForLine(globalLine, line);
  }

  public boolean isWrapCacheableForLine(int globalLine) {
    return wordWrap.isWrapCacheableForLine(globalLine);
  }

  public int[] computeWrapStarts(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    return wordWrap.computeWrapStarts(line, widthPx, p, useSharedBuffer);
  }

  public boolean shouldUseBreakTextWrap(String line) {
    return wordWrap.shouldUseBreakTextWrap(line);
  }

  public int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
    return wordWrap.computeWrapStartsWithBreakText(line, widthPx, p);
  }

  public int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    return wordWrap.getWrapSegmentIndexForChar(starts, charIndex);
  }

  public int getWrapSegmentStart(int[] starts, int segIndex) {
    return wordWrap.getWrapSegmentStart(starts, segIndex);
  }

  public int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    return wordWrap.getWrapSegmentEnd(starts, segIndex, lineLength);
  }

  public boolean isWrapMetricsUsableForWindow(int widthPx) {
    return wordWrap.isWrapMetricsUsableForWindow(widthPx);
  }

  public boolean isWrapMetricsUsableForLine(int line) {
    return wordWrap.isWrapMetricsUsableForLine(line);
  }

  public int getWrapRangeCount(int startLine, int endLine) {
    return wordWrap.getWrapRangeCount(startLine, endLine);
  }

  public int findLineForVisualIndex(int visualIndex) {
    return wordWrap.findLineForVisualIndex(visualIndex);
  }

  public WordWrap.VisualLinePosition getVisualPositionForIndexFallback(int visualIndex, int widthPx) {
    return wordWrap.getVisualPositionForIndexFallback(visualIndex, widthPx);
  }

  public boolean patchWrapMetricsForVisualRange(
      int firstVisualIndex,
      int lastVisualIndex,
      @Nullable java.util.Map<Integer, String> directLines,
      int widthPx) {
    return wordWrap.patchWrapMetricsForVisualRange(firstVisualIndex, lastVisualIndex, directLines, widthPx);
  }

  public int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd) {
    return wordWrap.clampSegmentEndForWrapIndicator(line, segStart, segEnd);
  }

  public int getWindowEndLine() {
    synchronized (linesWindow) {
      return Math.max(0, windowStartLine + linesWindow.size() - 1);
    }
  }

  // WordWrap field accessors
  public boolean isWrapMetricsReady() {
    return wordWrap.wrapMetricsReady;
  }

  public int[] getWrapLinePrefix() {
    return wordWrap.wrapLinePrefix;
  }

  public int[] getWrapLineCounts() {
    return wordWrap.wrapLineCounts;
  }

  public int getTotalWrapVisualLines() {
    return wordWrap.totalWrapVisualLines;
  }

  public int getWrapPrefixValidUpToLine() {
    return wordWrap.wrapPrefixValidUpToLine;
  }

  public boolean isWrapPrefixBuilding() {
    return wordWrap.wrapPrefixBuilding;
  }

  public boolean isWrapPrefixRebuildPending() {
    return wordWrap.wrapPrefixRebuildPending;
  }

  public int getWrapMetricsWidth() {
    return wordWrap.wrapMetricsWidth;
  }

  public Paint getWordWrapIndicatorPaint() {
    return wordWrap.indicator.wordWrapIndicatorPaint;
  }

  public float getWordWrapIndicatorWidth() {
    return wordWrap.indicator.wordWrapIndicatorWidth;
  }

  public float getWordWrapIndicatorTextScale() {
    return wordWrap.indicator.wordWrapIndicatorTextScale;
  }

  public boolean isWordWrapIndicatorEnabled() {
    return wordWrap.indicator.isWordWrapIndicatorEnabled;
  }

  public String getWordWrapIndicatorText() {
    return WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT;
  }

  public int getTabSize() {
    return TextRender.DEFAULT_TAB_SIZE_SPACES;
  }

  public float getViewXForLineChar(String line, int globalLine, int ch) {
    if (line == null) line = "";
    int safeChar = Math.max(0, Math.min(ch, getLogicalLineLength(globalLine, line)));
    if (!wordWrap.isWordWrapEnabled) {
      return getTextStartX() + measureText(line, safeChar, globalLine) - getEffectiveScrollX();
    }
    int[] starts = wordWrap.getWrapStartsForLine(globalLine, line);
    int seg = wordWrap.getWrapSegmentIndexForChar(starts, safeChar);
    int segStart = wordWrap.getWrapSegmentStart(starts, seg);
    float x = measureTextWithVisualSpaces(line, segStart, safeChar,textRender.paint);
    return getTextStartX() + x - getEffectiveScrollX();
  }

  public float getViewYTopForLineChar(int globalLine, int ch) {
    int v = getVisualIndexForLineAndChar(globalLine, ch);
    return v * textRender.lineHeight - scroll.scrollY;
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
    editOperators.insertStringAtCursor(textToInsert);
    Log.d("SodiumEditor", "acceptAutoCompletion: Text inserted.");

    restartInput(); // Force IME to resync

    // The flag will be reset by the next onDown event.
  }

  public void setSuggestionTextSize(float size) {
    isSuggestionTextSizeCustom = true;
    float px = spToPx(size);
    float base = textRender.paint.getTextSize();
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
        caret.mainHandler.postDelayed(suggestionUpdateRunnable, SUGGESTION_UPDATE_DEBOUNCE_MS);
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
    List<TextRender.HighlightSpan> spans = highlightCache.get(cursor.cursorLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, cursor.cursorLine);
      highlightCache.put(cursor.cursorLine, spans);
    }
    for (TextRender.HighlightSpan span : spans) {
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



  public boolean containsBracketChars(String text) {
    if (text == null || text.isEmpty()) return false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']' ||
          c == '"' || c == '\'' || c == '`' || c == '\\') {
        return true;
      }
    }
    return false;
  }

  public void validatePathInBackground(final String path, final int lineToInvalidate) {
    pathUnderline.validatePathInBackground(path, lineToInvalidate);
  }

  public void setEditorBackgroundColor(int color) {
    textRender.hasEditorBackgroundColor = true;
    textRender.editorBackgroundColor = color;
    invalidate();
  }

  public void clearEditorBackgroundColor() {
    if (!textRender.hasEditorBackgroundColor) return;
    textRender.hasEditorBackgroundColor = false;
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
    if (textRender.editorBackgroundBitmap != null && !textRender.editorBackgroundBitmap.isRecycled()) {
      textRender.editorBackgroundBitmap.recycle();
    }
    textRender.editorBackgroundBitmap = null;
    invalidate();
  }

  public void setEditorBackgroundBitmap(Bitmap bitmap) {
    if (textRender.editorBackgroundBitmap != null && !textRender.editorBackgroundBitmap.isRecycled()) {
      textRender.editorBackgroundBitmap.recycle();
    }
    textRender.editorBackgroundBitmap = bitmap;
    invalidate();
  }

  

  public void setSelectionColor(int color) {
    selection.setSelectionHighlightColor(color);
  }


  

  

  

  public void setSearchQuery(
      String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
    search.setSearchQuery(query, useRegex, caseSensitive, wrapAround);
  }

  public void setSearchHighlightEnabled(boolean enabled) {
    search.setSearchHighlightEnabled(enabled);
  }

  public void setSearchHighlightColor(int color) {
    search.setSearchHighlightColor(color);
  }

  public boolean goToNextSearchMatch() {
    return search.goToNextSearchMatch();
  }

  public boolean goToPrevSearchMatch() {
    return search.goToPreviousSearchMatch();
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
    return search.selectSearchMatch(forward);
  }

  public boolean selectSearchMatchInclusive(boolean forward) {
    return search.selectSearchMatchInclusive(forward);
  }

  public boolean selectSearchMatchAtCursorOrNext() {
    return search.selectSearchMatchAtCursorOrNext();
  }

  @Nullable
  public Search.SearchMatch findSearchMatchAtCursor() {
    return search.findSearchMatchAtCursor();
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

  

  public void setBracketMatchingEnabled(boolean enabled) {
    bracketMatchManager.setBracketMatchingEnabled(enabled);
  }

  public void setBracketMatchColor(int color) {
    bracketMatchManager.setBracketMatchColor(color);
  }

  public void setBracketMatchStrokeWidth(float width) {
    bracketMatchManager.setBracketMatchStrokeWidth(width);
  }

  public void setBracketGuidesEnabled(boolean enabled) {
    bracketGuides.setBracketGuidesEnabled(enabled);
  }

  public void setBracketGuidesColor(int color) {
    bracketGuides.setBracketGuidesColor(color);
  }

  public void setBracketGuidesStrokeWidth(float width) {
    bracketGuides.setBracketGuidesStrokeWidth(width);
  }

  public void setIndentGuidesEnabled(boolean enabled) {
    indentGuides.setIndentGuidesEnabled(enabled);
  }

  public void setIndentGuidesColor(int color) {
    indentGuides.setIndentGuidesColor(color);
  }

  public void setIndentGuidesStrokeWidth(float width) {
    indentGuides.setIndentGuidesStrokeWidth(width);
  }

  public void setWhitespaceGuidesEnabled(boolean enabled) {
    whitespaceGuides.setWhitespaceGuidesEnabled(enabled);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    invalidate();
  }

  public void setWhitespaceGuidesColor(int color) {
    whitespaceGuides.setWhitespaceGuidesColor(color);
    invalidate();
  }

  public void setWhitespaceGuidesSpaceStep(int spacesPerDot) {
    whitespaceGuides.setWhitespaceGuidesSpaceStep(spacesPerDot);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    invalidate();
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public int computeMinWindowSize() {
    return computeMinWindowSizeForPrefetch(prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    if (textRender.lineHeight <= 0f || getHeight() <= 0) return 10;
    float effectiveHeight = (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(effectiveHeight / textRender.lineHeight) + 2);
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
    if (this.cursor.baseCursorWidthPx == width && this.cursor.baseCursorTextSizePx == textRender.paint.getTextSize()) return;
    this.cursor.baseCursorWidthPx = width;
    this.cursor.baseCursorTextSizePx = textRender.paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setHighlightCurrentLine(boolean enabled) {
    currentLineHighlight.setHighlightCurrentLine(enabled);
  }

  public void setClickAfterEndToAddLineEnabled(boolean enabled) {
    clickAfterEndToAddLine.setClickAfterEndToAddLineEnabled(enabled);
  }

  public void setAutoPairingEnabled(boolean enabled) {
    autoBracketPair.setAutoPairingEnabled(enabled);
  }

  public void setAutoBracketNewlineEnabled(boolean enabled) {
    autoBracketNewline.setAutoBracketNewlineEnabled(enabled);
  }

  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    autoBracketNewline.setAutoBracketNewlineIndentEnabled(enabled);
  }

  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    autoBracketNewline.setAutoIndentAfterClosingBracketEnabled(enabled);
  }

  public void setIndentationBlocksEnabled(boolean enabled) {
    if (this.isIndentationBlocksEnabled == enabled) return;
    this.isIndentationBlocksEnabled = enabled;
    if (!enabled) {
      codeFold.foldRanges.entrySet().removeIf(e -> e.getValue().isIndentFold);
    }
    indentGuides.markIntervalsDirty();
    codeFold.foldIntervalsDirty = true;
    invalidate();
  }

  public void setCodeFoldingEnabled(boolean enabled) {
    codeFold.setCodeFoldingEnabled(enabled);
  }

  public void setFoldPlaceholderColor(int color) {
    codeFold.foldPlaceholderPaint.setColor(color);
    if (codeFold.isCodeFoldingEnabled) invalidate();
  }

  public void setFoldMarkerColor(int color) {
    codeFold.foldMarkerPaint.setColor(color);
    if (codeFold.isCodeFoldingEnabled) invalidate();
  }

  public void setFoldMarkerTextSize(float size) {
    float base = textRender.paint.getTextSize();
    if (base <= 0f) return;
    codeFold.foldMarkerTextScale = size / base;
    codeFold.foldMarkerPaint.setTextSize(base * codeFold.foldMarkerTextScale);
    requestLayout();
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    invalidate();
  }

  public void setCurrentLineHighlightColor(int color) {
    currentLineHighlight.setCurrentLineHighlightColor(color);
  }

  public void addHighlightRule(String regex, int style, int color) {
    highlite.addHighlightRule(regex, style, color);
    invalidate();
  }

  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    highlite.addHighlightRule(regex, style, color, underline);
    invalidate();
  }

  public void clearHighlightRules() {
    highlite.clearHighlightRules();
    invalidate();
  }

  public void clearHighlightCaches() {
    highlite.clearHighlightCaches();
    colorCodeHighlight.clearColorCodeCaches();
    urlUnderline.clearUrlUnderlineCache();
    pathUnderline.clearPathUnderlineCache();
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
  }

  public void invalidateHighlightCacheForLine(int line) {
    highlite.invalidateHighlightCacheForLine(line);
    colorCodeHighlight.clearColorCodeCacheForLine(line);
    urlUnderline.clearUrlUnderlineCacheForLine(line);
    pathUnderline.clearPathUnderlineCacheForLine(line);
    invalidateHighlightEnsureRange();
    invalidateBracketGuideCache();
  }

  public void setUrlUnderliningEnabled(boolean enabled) {
    urlUnderline.setUrlUnderliningEnabled(enabled);
  }

  public void setUrlUnderliningRegex(@Nullable String regex) {
    urlUnderline.setUrlUnderliningRegex(regex);
  }

  public void setPathUnderliningEnabled(boolean enabled) {
    pathUnderline.setPathUnderliningEnabled(enabled);
  }

  public void setErrorUnderlineColor(int color) {
    if (textRender.errorUnderlineColor == color) return;
    textRender.errorUnderlineColor = color;
    invalidate();
  }

  public void setErrorUnderlineEnabled(boolean enabled) {
    if (textRender.errorUnderlineEnabled == enabled) return;
    textRender.errorUnderlineEnabled = enabled;
    invalidate();
  }

  public void setErrorUnderlineHeightScale(float scale) {
    float safe = Math.max(0f, scale);
    if (textRender.errorUnderlineHeightScale == safe) return;
    textRender.errorUnderlineHeightScale = safe;
    invalidate();
  }

  public void setErrorUnderlineWaveLengthScale(float scale) {
    float safe = Math.max(0.1f, scale);
    if (textRender.errorUnderlineWaveLengthScale == safe) return;
    textRender.errorUnderlineWaveLengthScale = safe;
    invalidate();
  }

  public void setErrorUnderlineStrokeScale(float scale) {
    float safe = Math.max(0f, scale);
    if (textRender.errorUnderlineStrokeScale == safe) return;
    textRender.errorUnderlineStrokeScale = safe;
    invalidate();
  }

  public void setErrorUnderlineSmoothness(float radiusPx) {
    float safe = Math.max(0f, radiusPx);
    if (textRender.errorUnderlineSmoothness == safe) return;
    textRender.errorUnderlineSmoothness = safe;
    invalidate();
  }

  public void setErrorUnderline(int line, int col, int length) {
    if (line < 0) return;
    if (length <= 0) {
      textRender.errorUnderlineMap.remove(line);
      invalidate();
      return;
    }
    int start = Math.max(0, col);
    int end = Math.max(start, start + length);
    List<TextRender.ErrorUnderlineSpan> list = textRender.errorUnderlineMap.get(line);
    if (list == null) {
      list = new ArrayList<>();
      textRender.errorUnderlineMap.put(line, list);
    }
    list.add(new TextRender.ErrorUnderlineSpan(start, end));
    invalidate();
  }

  public void setStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(Highlite.RULE_STRING, STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (highlite.isMultiLineStringsEnabled != enabled) {
      highlite.isMultiLineStringsEnabled = enabled;
      highlite.isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    invalidate();
  }

  public void setMultiLineStringsHighlight(boolean enabled, int color) {
    if (stringHighlightRule == null) {
      addHighlightRule(Highlite.RULE_STRING, STYLE_NORMAL, color);
    }
    if (stringHighlightRule != null && stringHighlightRule.paint.getColor() != color) {
      stringHighlightRule.paint.setColor(color);
    }
    if (highlite.isMultiLineStringsEnabled != enabled) {
      highlite.isMultiLineStringsEnabled = enabled;
      highlite.isMultiLineStringsEnabled = enabled;
    }
    clearHighlightCaches();
    invalidate();
  }

  // Toggle background highlight for hex color literals (e.g., #RRGGBB, 0xAARRGGBB).
  public void setColorCodeHighlightingEnabled(boolean enabled) {
    colorCodeHighlight.setColorCodeHighlightingEnabled(enabled);
  }

  public void setBacktickStringsEnabled(boolean enabled) {
    if (highlite.isBacktickStringsEnabled == enabled) return;
    highlite.isBacktickStringsEnabled = enabled;
    highlite.isBacktickStringsEnabled = enabled;
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
          new TextRender.HighlightRule(
              Highlite.RULE_BLOCK_COMMENT,
              style,
              color,
              textRender.paint.getTextSize(),
              textRender.paint.getTypeface(),
              false,
              TextRender.HighlightRuleType.BLOCK_COMMENT);
      highlightRules.add(blockCommentHighlightRule);
      needsInvalidate = true;
    } else {
      if (blockCommentHighlightRule.paint.getColor() != color) {
        blockCommentHighlightRule.paint.setColor(color);
        needsInvalidate = true;
      }
    }
    if (highlite.isBlockCommentsEnabled != enabled) {
      highlite.isBlockCommentsEnabled = enabled;
      highlite.isBlockCommentsEnabled = enabled;
      needsInvalidate = true;
    }
    if (needsInvalidate) {
      clearHighlightCaches();
      invalidate();
    }
  }

  public void setSingleLineCommentDelimiters(String... delimiters) {
    lineCommentDelimiters.clear();
    highlite.lineCommentDelimiters.clear();
    if (delimiters != null) {
      for (String d : delimiters) {
        if (d == null) continue;
        String trimmed = d.trim();
        if (trimmed.isEmpty()) continue;
        if (!lineCommentDelimiters.contains(trimmed)) {
          lineCommentDelimiters.add(trimmed);
          highlite.lineCommentDelimiters.add(trimmed);
        }
      }
    }
    // Prefer longer delimiters first (e.g. '//' before '/')
    lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
    highlite.lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
    clearHighlightCaches();
    invalidate();
  }

  public void ensureLineCommentDelimiter(String delimiter) {
    if (delimiter == null) return;
    String trimmed = delimiter.trim();
    if (trimmed.isEmpty()) return;
    if (!lineCommentDelimiters.contains(trimmed)) {
      lineCommentDelimiters.add(trimmed);
      highlite.lineCommentDelimiters.add(trimmed);
      lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
      highlite.lineCommentDelimiters.sort((a, b) -> Integer.compare(b.length(), a.length()));
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
          new TextRender.HighlightRule(
              "",
              style,
              color,
              textRender.paint.getTextSize(),
              textRender.paint.getTypeface(),
              false,
              TextRender.HighlightRuleType.LINE_COMMENT);
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
    if (highlite.isTripleQuoteStringsEnabled == enabled) return;
    highlite.isTripleQuoteStringsEnabled = enabled;
    highlite.isTripleQuoteStringsEnabled = enabled;
    clearHighlightCaches();
    invalidate();
  }

  public void setLayoutDirection(boolean isRtl) {
    if (textRender.isRtl == isRtl) return;
    textRender.isRtl = isRtl;
    lineNumber.lineNumbersPaint.setTextAlign(textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    codeFold.foldMarkerPaint.setTextAlign(textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    lineNumber.invalidateLineNumberCache();
    requestLayout();
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
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
    if (scaled <= 0f) return textRender.paint.getTextSize();
    return textRender.paint.getTextSize() / scaled;
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
    editOperators.insertTextAtCursor(text);
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
    float sizePx = textRender.paint.getTextSize();
    selectionHandles.handleRadius = Math.max(4f, scaleByTextSize(selectionHandles.baseHandleRadiusPx, selectionHandles.baseHandleTextSizePx, sizePx));
    cursor.cursorWidth = Math.max(1f, scaleByTextSize(cursor.baseCursorWidthPx, cursor.baseCursorTextSizePx, sizePx));

    indentGuides.updateStrokeWidth();
  }

  public void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = textRender.paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    textRender.paint.setTextSize(sizePx);
    if (!isSuggestionTextSizeCustom) {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(sizePx * suggestionTextSizeScale);
    lineNumber.lineNumbersPaint.setTextSize(sizePx);
    codeFold.foldMarkerPaint.setTextSize(sizePx * codeFold.foldMarkerTextScale);
    wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(sizePx * wordWrap.indicator.wordWrapIndicatorTextScale);
    wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(textRender.paint.getTypeface());
    wordWrap.indicator.wordWrapIndicatorWidth = wordWrap.indicator.wordWrapIndicatorPaint.measureText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);
    textRender.lineHeight = textRender.paint.getFontSpacing();
    updateTextSizeDependentMetrics();
    updateWhitespaceGuideMetrics();
    lineNumber.invalidateLineNumberCache();

    for (TextRender.HighlightRule rule : highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (textRender.whitespaceStringRule != null) textRender.whitespaceStringRule.updateTextSize(sizePx);
    if (textRender.whitespaceCommentRule != null) textRender.whitespaceCommentRule.updateTextSize(sizePx);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true, !deferWrapRebuild);
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
    textRender.baseTypeface = safeBase;
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
    textRender.paint.setTypeface(finalTypeface);
    suggestionPaint.setTypeface(finalTypeface);
    lineNumber.lineNumbersPaint.setTypeface(finalTypeface);
    codeFold.foldMarkerPaint.setTypeface(finalTypeface);
    wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(finalTypeface);
    if (textRender.whitespaceStringRule != null) textRender.whitespaceStringRule.updateTypeface(safeBase);
    if (textRender.whitespaceCommentRule != null) textRender.whitespaceCommentRule.updateTypeface(safeBase);
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTypeface(safeBase);
    for (TextRender.HighlightRule rule : highlightRules) {
      rule.updateTypeface(safeBase);
    }
    clearHighlightCaches();

    textRender.lineHeight = textRender.paint.getFontSpacing();
    updateWhitespaceGuideMetrics();
    lineNumber.invalidateLineNumberCache();
    wordWrap.indicator.wordWrapIndicatorWidth = wordWrap.indicator.wordWrapIndicatorPaint.measureText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);

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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    invalidate();
  }

  public void updateWhitespaceGuideMetrics() {
    whitespaceGuides.updateMetrics();
  }

  

  public void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      @Nullable java.util.HashMap<Integer, String> directLines) {
    highlite.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, directLines);
  }

  public void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    if (startLine > endLine) return;
    int v = editOperators.editVersion.get();
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
      if (codeFold.isCodeFoldingEnabled) {
        codeFold.foldMarkerGutterWidth =
            codeFold.foldMarkerPaint.measureText("v") + codeFold.foldMarkerSpacing + codeFold.foldMarkerEdgePadding;
      } else {
        codeFold.foldMarkerGutterWidth = 0f;
      }
      lineNumber.lineNumbersGutterWidth = baseWidth + codeFold.foldMarkerGutterWidth + lineNumber.gutterSeparatorWidth;
    } else {
      lineNumber.lineNumbersGutterWidth = 0f;
    }

    if (wordWrap.isWordWrapEnabled && Math.abs(lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
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
    if (wordWrap.isWordWrapEnabled && w != oldw) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
  }

  public float getTextStartX() {
    return textRender.isRtl ? textRender.paddingLeft : textRender.paddingLeft + lineNumber.lineNumbersGutterWidth;
  }

  public float getEffectiveScrollX() {
    return textRender.isRtl ? -scroll.scrollX :  scroll.scrollX;
  }

  public float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - getTextStartX();
  }

  public float getTextAreaWidth() {
    return Math.max(0f, getWidth() - lineNumber.lineNumbersGutterWidth - textRender.paddingLeft);
  }

  public float getRtlLineBaseX(@Nullable String line, int globalLine) {
    if (!textRender.isRtl || line == null) return 0f;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!textRender.isRtl || line == null) return 0f;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x = measureText(line, charIndex, globalLine);
    if (!textRender.isRtl) return x;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel = measureTextWithVisualSpaces(line, segStart, charIndex,textRender.paint);
    if (!textRender.isRtl) return xRel;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  
  public final Runnable autoScrollRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (selectionHandles.draggingHandle == 0) return;
          if (autoScrollX != 0 || autoScrollY != 0) {
            scroll.scrollY += autoScrollX;
            float nextY =  scroll.scrollY + autoScrollY;
            if (!isIndexReady && !isEof && isWindowLoading) {
              float effectiveHeight =
                  (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
              float winTop = windowStartLine * textRender.lineHeight;
              float winBottom = (windowStartLine + linesWindow.size()) * textRender.lineHeight;
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
            caret.mainHandler.postDelayed(this, 16);
          }
        }
      };

  public boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
    if (!codeFold.isCodeFoldingEnabled) return false;
    CodeFold.FoldRange range = codeFold.foldRanges.get(globalLine);
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
    float placeholderWidth = Math.max(0f, textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
    float pad = Math.max(0f, codeFold.foldPlaceholderPadX);
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
      textRender.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y,textRender.paint);
      return;
    }

    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      textRender.paint.setUnderlineText(false);
      canvas.drawText(line, start, end, x, y,textRender.paint);
      return;
    }

    float currentX = x;
    int lastEnd = start;

    for (TextRender.HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        textRender.paint.setUnderlineText(false);
        canvas.drawText(line, lastEnd, span.start, currentX, y,textRender.paint);
        currentX += textRender.paint.measureText(line, lastEnd, span.start);
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
      textRender.paint.setUnderlineText(false);
      canvas.drawText(line, lastEnd, end, currentX, y,textRender.paint);
    }
  }

  public float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
    if (line == null || line.isEmpty() || start >= end) return 0f;
    start = Math.max(0, Math.min(start, line.length()));
    end = Math.max(start, Math.min(end, line.length()));
    if (start >= end) return 0f;

    if (highlightRules.isEmpty()) {
      return textRender.paint.measureText(line, start, end);
    }

    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return textRender.paint.measureText(line, start, end);
    }

    float total = 0f;
    int lastEnd = start;

    for (TextRender.HighlightSpan span : spans) {
      if (lastEnd >= end) break;
      if (span.start >= end) break;
      if (span.start < lastEnd) continue;

      if (span.start > lastEnd) {
        total += textRender.paint.measureText(line, lastEnd, span.start);
      }

      int safeSpanEnd = Math.min(span.end, end);
      if (safeSpanEnd > span.start) {
        total += span.paint.measureText(line, span.start, safeSpanEnd);
      }
      lastEnd = safeSpanEnd;
    }

    if (lastEnd < end) {
      total += textRender.paint.measureText(line, lastEnd, end);
    }

    return total;
  }

  public boolean isIndentFoldCandidate(String line) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = rstripWhitespace(line);
    return !trimmed.isEmpty() && trimmed.endsWith(":");
  }

  public void clearFoldRipple() {
    codeFold.foldRippleAnimator.cancel();
    codeFold.foldRippleAlpha = 0f;
    codeFold.foldRippleRadius = 0f;
    codeFold.foldRippleLine = -1;
  }

  public void drawContent(Canvas canvas) {
    if (wordWrap.isWordWrapEnabled) {
      drawContentWrapped(canvas);
      return;
    }
    final boolean drawDecorations = zoom.shouldDrawDecorations();

    // Calculate visible line range
    int firstVisibleIndex = (int) ( scroll.scrollY / textRender.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(getHeight() / textRender.lineHeight) + 5;

    int firstVisibleLine = firstVisibleIndex;
    int lastVisibleLine = lastVisibleIndex;
    if (codeFold.isCodeFoldingEnabled) {
      int visibleCount = codeFold.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
      drawBaseLine = firstVisibleIndex;
    } else {
      drawBaseLine = firstVisibleLine;
    }

    float baseY = drawBaseLine * textRender.lineHeight;
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
      if (textRender.isRtl) {
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

    if (currentLineHighlight.highlightCurrentLineInGutter
        && cursor.cursorLine >= firstVisibleLine
        && cursor.cursorLine <= lastVisibleLine
        && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(cursor.cursorLine))) {
      int drawIndex = codeFold.isCodeFoldingEnabled ? codeFold.getVisibleIndexForGlobalLine(cursor.cursorLine) : cursor.cursorLine;
      float top = Math.round(drawIndex * textRender.lineHeight -  scroll.scrollY);
      float bottom = top + textRender.lineHeight;
      lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (lineNumber.showLineNumbers) {
      textRender.drawlineNumbersCachedUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (codeFold.isCodeFoldingEnabled && drawDecorations) {
        codeFold.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    // Clip the text area so it doesn't draw over the gutter
    if (textRender.isRtl) {
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
      selection.selectionPaint.setColor(selection.selectionHighlightColor);
      selPaint = selection.selectionPaint;
    }

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      boolean needDirect =
          (firstVisibleLine < windowStartLine)
              || (firstVisibleLine >= windowStartLine + linesWindow.size())
              || (lastVisibleLine >= windowStartLine + linesWindow.size());

      if (needDirect) {
        textRender.directLinesTmp.clear();
        directLines = textRender.directLinesTmp;
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

    SodiumEditor.BracketMatch bracketMatchResult = null;
    if (bracketMatchManager.isBracketMatchingEnabled) {
      bracketMatchResult = bracketMatchManager.findAndCacheBracketMatch(firstVisibleLine, lastVisibleLine, directLines);
    }

    int winEnd;
    synchronized (linesWindow) {
      winEnd = windowStartLine + linesWindow.size() - 1;
    }
    int prefetchForDraw = zoom.isZoomGestureActive() ? 0 : prefetchLines;
    int hlStart = Math.max(windowStartLine, Math.max(0, firstVisibleLine - prefetchForDraw));
    int hlEnd = Math.min(winEnd, lastVisibleLine + prefetchForDraw);
    maybeEnsureHighlightCacheForRange(hlStart, hlEnd, directLines);

    if (bracketGuides.isBracketGuidesEnabled && drawDecorations) {
      ensureBracketGuideCacheForWindow(directLines);
    }

    if (codeFold.isCodeFoldingEnabled) {
      if (indentGuides.indentGuideIntervalsDirty) indentGuides.rebuildIndentGuideIntervalsIfNeeded();
      for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
        int globalLine = codeFold.mapVisibleIndexToGlobal(v);
        String line = getLineTextForRenderWithDirect(globalLine, directLines);
        CodeFold.FoldRange foldRange = codeFold.getFoldRangeAtStart(globalLine);
        boolean isFoldStart = (foldRange != null);
        float lineBaseX = textRender.isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            textRender.isRtl
                ? measureHighlightedSegmentWidth(
                    line, globalLine, 0, getLogicalLineLength(globalLine, line))
                : 0f;

        // Highlight the current line, only if there is no selection
        if (currentLineHighlight.highlightCurrentLine && globalLine == cursor.cursorLine && !selection.hasSelection) {
          float top = Math.round(textRender.getDrawLineTop(globalLine));
          float bottom = Math.round(textRender.getDrawLineBottom(globalLine));
          float viewLeft = textRender.isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
          float viewRight = textRender.isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, currentLineHighlight.currentLinePaint);
        }

        if (selection.hasSelection && selPaint != null) {
          float top = Math.round(textRender.getDrawLineTop(globalLine));
          float bottom = Math.round(textRender.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth,  scroll.scrollX + (getWidth() - getTextStartX()));
          if (textRender.isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selection.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selection.selStartLine;
              boolean roundBottom = globalLine == selection.selEndLine;
              float leftSel = textRender.isRtl ? lineBaseX : 0f;
              float rightSel = textRender.isRtl ? (lineBaseX + lineWidth) : fullRight;
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
            if (editOperators.comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) <= 0) {
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
              if (textRender.isRtl) {
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

        float y = Math.round(textRender.getDrawLineTop(globalLine) + textRender.lineHeight - textRender.paint.descent());
        textRender.paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        if (isFoldStart) {
          if (bracketGuides.isBracketGuidesEnabled && drawDecorations) {
            List<BracketGuides.BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
            drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
          }
          if (drawDecorations) {
            textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
            drawIndentGuidesForLine(canvas, line, globalLine);
          }
          codeFold.drawFoldedLine(canvas, line, globalLine);
          canvas.restore();
          continue;
        }

        float lineTop = Math.round(textRender.getDrawLineTop(globalLine));
        float lineBottom = Math.round(textRender.getDrawLineBottom(globalLine));
        drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        textRender.drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (bracketGuides.isBracketGuidesEnabled && drawDecorations) {
          List<BracketGuides.BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
          drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          bracketMatchManager.drawBracketMatchForLine(canvas, line, globalLine, bracketMatchResult);
        }
        canvas.restore();
      }
    } else {
      if (indentGuides.indentGuideIntervalsDirty) indentGuides.rebuildIndentGuideIntervalsIfNeeded();
      for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
        String line = getLineTextForRenderWithDirect(globalLine, directLines);
        float lineBaseX = textRender.isRtl ? getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            textRender.isRtl
                ? measureHighlightedSegmentWidth(
                    line, globalLine, 0, getLogicalLineLength(globalLine, line))
                : 0f;

        // Highlight the current line, only if there is no selection
        if (currentLineHighlight.highlightCurrentLine && globalLine == cursor.cursorLine && !selection.hasSelection) {
          float top = Math.round(textRender.getDrawLineTop(globalLine));
          float bottom = Math.round(textRender.getDrawLineBottom(globalLine));
          float viewLeft = textRender.isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
          float viewRight = textRender.isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, currentLineHighlight.currentLinePaint);
        }

        if (selection.hasSelection && selPaint != null) {
          float top = Math.round(textRender.getDrawLineTop(globalLine));
          float bottom = Math.round(textRender.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth,  scroll.scrollX + (getWidth() - getTextStartX()));
          if (textRender.isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selection.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selection.selStartLine;
              boolean roundBottom = globalLine == selection.selEndLine;
              float leftSel = textRender.isRtl ? lineBaseX : 0f;
              float rightSel = textRender.isRtl ? (lineBaseX + lineWidth) : fullRight;
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
            if (editOperators.comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) <= 0) {
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
              if (textRender.isRtl) {
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

        float y = Math.round(textRender.getDrawLineTop(globalLine) + textRender.lineHeight - textRender.paint.descent());
        textRender.paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        float lineTop = Math.round(textRender.getDrawLineTop(globalLine));
        float lineBottom = Math.round(textRender.getDrawLineBottom(globalLine));
        drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        textRender.drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          textRender.drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (bracketGuides.isBracketGuidesEnabled && drawDecorations) {
          List<BracketGuides.BracketGuideToken> guideTokens = getBracketGuideTokensForLine(globalLine);
          drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          bracketMatchManager.drawBracketMatchForLine(canvas, line, globalLine, bracketMatchResult);
        }
        canvas.restore();
      }
    }
    if (isFocused()
        && !isReadOnly
        && !selection.hasSelection
        && cursor.cursorLine >= firstVisibleLine
        && cursor.cursorLine <= lastVisibleLine
        && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(cursor.cursorLine))) {
      String cursorLineText = getLineTextForRender(cursor.cursorLine);
      int safeChar = Math.min(cursor.cursorChar, getLogicalLineLength(cursor.cursorLine, cursorLineText));
      float cursorX = getCaretXForLine(cursorLineText, cursor.cursorLine, safeChar);
      float cursorY = textRender.getDrawLineTop(cursor.cursorLine);
      cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
      float drawX = cursorAnimation.cursorDrawX;
      float drawY = cursorAnimation.cursorDrawY;
      if (caret.isCursorVisible) {
        caret.caretPaint.setColor(caret.caretColor);
        caret.caretPaint.setStrokeWidth(cursor.cursorWidth);
        canvas.drawLine(drawX, drawY, drawX, drawY + textRender.lineHeight, caret.caretPaint);
      }
      selectionHandles.handlePaint.setColor(cursorHandle.cursorHandleColor);
      drawTeardropHandle(canvas, drawX, drawY + textRender.lineHeight, selectionHandles.handlePaint);
      cursorHandle.cursorHandleRect.set(
          drawX - selectionHandles.handleRadius,
          drawY + textRender.lineHeight,
          drawX + selectionHandles.handleRadius,
          drawY + textRender.lineHeight + selectionHandles.handleRadius * 2);
    }

    if (selection.hasSelection && !isReadOnly) {
      selectionHandles.handlePaint.setColor(selectionHandles.selectionHandleColor);
      if (selection.selStartLine >= firstVisibleLine
          && selection.selStartLine <= lastVisibleLine
          && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(selection.selStartLine))) {
        String startLineText = getLineTextForRender(selection.selStartLine);
        float startX =
            getCaretXForLine(
                startLineText,
                selection.selStartLine,
                Math.min(selection.selStartChar, getLogicalLineLength(selection.selStartLine, startLineText)));
        float startY = textRender.getDrawLineTop(selection.selStartLine) + textRender.lineHeight;
        drawTeardropHandle(canvas, startX, startY, selectionHandles.handlePaint);
        if (textRender.isRtl) {
          selectionHandles.rightHandleRect.set(
              startX - selectionHandles.handleRadius, startY, startX + selectionHandles.handleRadius, startY + selectionHandles.handleRadius * 2);
        } else {
          selectionHandles.leftHandleRect.set(
              startX - selectionHandles.handleRadius, startY, startX + selectionHandles.handleRadius, startY + selectionHandles.handleRadius * 2);
        }
      } else {
        if (textRender.isRtl) selectionHandles.rightHandleRect.setEmpty();
        else selectionHandles.leftHandleRect.setEmpty();
      }
      if (selection.selEndLine >= firstVisibleLine
          && selection.selEndLine <= lastVisibleLine
          && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(selection.selEndLine))) {
        String endLineText = getLineTextForRender(selection.selEndLine);
        float endX =
            getCaretXForLine(
                endLineText,
                selection.selEndLine,
                Math.min(selection.selEndChar, getLogicalLineLength(selection.selEndLine, endLineText)));
        float endY = textRender.getDrawLineTop(selection.selEndLine) + textRender.lineHeight;
        drawTeardropHandle(canvas, endX, endY, selectionHandles.handlePaint);
        if (textRender.isRtl) {
          selectionHandles.leftHandleRect.set(
              endX - selectionHandles.handleRadius, endY, endX + selectionHandles.handleRadius, endY + selectionHandles.handleRadius * 2);
        } else {
          selectionHandles.rightHandleRect.set(
              endX - selectionHandles.handleRadius, endY, endX + selectionHandles.handleRadius, endY + selectionHandles.handleRadius * 2);
        }
      } else {
        if (textRender.isRtl) selectionHandles.leftHandleRect.setEmpty();
        else selectionHandles.rightHandleRect.setEmpty();
      }
    }

    canvas.restore();
    // --- End of main text content drawing ---

    // --- 4. Draw overlays ---

    popup.drawPopup(canvas);

    if (loadingCircle.showLoadingCircle) {

      loadingCircle.loadingCirclePaint.setColor(loadingCircle.loadingCircleColor);
      loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircle.loadingCircleRotation, centerX, centerY);
      loadingCircle.loadingCircleRect.set(
          centerX - loadingCircle.loadingCircleRadius,
          centerY - loadingCircle.loadingCircleRadius,
          centerX + loadingCircle.loadingCircleRadius,
          centerY + loadingCircle.loadingCircleRadius);
      canvas.drawArc(loadingCircle.loadingCircleRect, 0, 270, false, loadingCircle.loadingCirclePaint);
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
      if (!wordWrap.wrapMetricsReady || wordWrap.wrapMetricsWidth != wrapWidthPx) {
        scheduleWrapMetricsSnapshotIfNeeded(wrapWidthPx);
      }
      if (wordWrap.wrapPrefixValidUpToLine < getWindowEndLine()) {
        requestWrapPrefixRebuild();
      }
      drawContentWrappedFallback(canvas, wordWrap.wrapWidthPx);
      return;
    }
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
    if (totalLines <= 0) totalLines = 1;

    int totalVisual = getTotalVisualLineCount();
    int firstVisualIndex = Math.max(0, (int) ( scroll.scrollY / textRender.lineHeight));
    int lastVisualIndex =
        Math.min(totalVisual - 1, firstVisualIndex + (int) Math.ceil(getHeight() / textRender.lineHeight) + 5);
    if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

    WordWrap.VisualLinePosition firstPos = getVisualPositionForIndex(firstVisualIndex);
    WordWrap.VisualLinePosition lastPos = getVisualPositionForIndex(lastVisualIndex);

    maybeKickWindowLoad(firstPos.line);

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      textRender.directLinesTmp.clear();
      directLines = textRender.directLinesTmp;
      int rangeStart = Math.max(0, firstPos.line - 1);
      int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
      populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
    }

    // Safety: after zoom/fast scroll, wordWrap.wrapLineCounts might be stale for some visible lines.
    // Don't patch during pinch/scale: it can fight the zoom's own scroll math and cause a brief
    // "jump".
    boolean patched = false;
    if (!zoom.isZoomGestureActive()) {
      patched =
          patchWrapMetricsForVisualRange(
              firstVisualIndex, lastVisualIndex, directLines, wordWrap.wrapWidthPx);
    }
    if (patched) {
      totalLines = getLinesCount();
      if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
      if (totalLines <= 0) totalLines = 1;

      totalVisual = getTotalVisualLineCount();
      firstVisualIndex = Math.max(0, (int) ( scroll.scrollY / textRender.lineHeight));
      lastVisualIndex =
          Math.min(
              totalVisual - 1, firstVisualIndex + (int) Math.ceil(getHeight() / textRender.lineHeight) + 5);
      if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

      firstPos = getVisualPositionForIndex(firstVisualIndex);
      lastPos = getVisualPositionForIndex(lastVisualIndex);
      maybeKickWindowLoad(firstPos.line);

      if (directLines != null) {
        textRender.directLinesTmp.clear();
        int rangeStart = Math.max(0, firstPos.line - 1);
        int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
        populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
      }
    }

    float baseY = firstVisualIndex * textRender.lineHeight;
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
      if (textRender.isRtl) {
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

    if (currentLineHighlight.highlightCurrentLineInGutter
        && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(cursor.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, 0);
      String cursorLineText = getLineTextForRender(cursor.cursorLine);
      int[] starts = getWrapStartsForLine(cursor.cursorLine, cursorLineText);
      int segCount = Math.max(1, starts.length);
      int lastVisualIndexForLine = currentVisualIndex + segCount - 1;
      int drawFrom = Math.max(firstVisualIndex, currentVisualIndex);
      int drawTo = Math.min(lastVisualIndex, lastVisualIndexForLine);
      for (int v = drawFrom; v <= drawTo; v++) {
        float top = Math.round(v * textRender.lineHeight -  scroll.scrollY);
        float bottom = top + textRender.lineHeight;
        lineNumber.drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (lineNumber.showLineNumbers) {
      textRender.drawlineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    if (textRender.isRtl) {
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
      selection.selectionPaint.setColor(selection.selectionHighlightColor);
      selPaint = selection.selectionPaint;
    }

    int startLine = selection.selStartLine;
    int startChar = selection.selStartChar;
    int endLine = selection.selEndLine;
    int endChar = selection.selEndChar;
    if (selection.hasSelection && editOperators.comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
      startLine = selection.selEndLine;
      startChar = selection.selEndChar;
      endLine = selection.selStartLine;
      endChar = selection.selStartChar;
    }

    for (int v = firstVisualIndex; v <= lastVisualIndex; v++) {
      WordWrap.VisualLinePosition pos = getVisualPositionForIndex(v);
      String line = getLineTextForRenderWithDirect(pos.line, directLines);
      int[] starts = getWrapStartsForLine(pos.line, line);

      // Skip if visual segment index is invalid for the current wrap state (e.g. during zoom)
      if (pos.segment >= starts.length) continue;

      int segStart = getWrapSegmentStart(starts, pos.segment);
      int segEnd = getWrapSegmentEnd(starts, pos.segment, line.length());
      float segBaseX = textRender.isRtl ? getRtlSegmentBaseX(line, pos.line, segStart, segEnd) : 0f;

      float top = Math.round((v - firstVisualIndex) * textRender.lineHeight);
      float bottom = top + textRender.lineHeight;
      float y = Math.round(top + textRender.lineHeight - textRender.paint.descent());

      if (currentLineHighlight.highlightCurrentLine && pos.line == cursor.cursorLine && !selection.hasSelection) {
        canvas.drawRect(
            -textRender.paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, currentLineHighlight.currentLinePaint);
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
            if (textRender.isRtl) {
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
                      : measureTextWithVisualSpaces(line, segStart, segSelStart,textRender.paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wordWrap.wrapWidthPx)
                      : leftRel + measureTextWithVisualSpaces(line, segSelStart, segSelEnd,textRender.paint);
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
      if (wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        segDrawEnd = clampSegmentEndForWrapIndicator(line, segStart, segEnd, wordWrap.wrapWidthPx);
      }
      canvas.save();
      if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
      drawSearchHighlightsForSegment(canvas, line, pos.line, segStart, segDrawEnd, top, bottom);
      textRender.drawHighlightedLineSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      textRender.drawErrorUnderlinesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      textRender.drawDeleteAnimationForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      if (drawDecorations) {
        textRender.drawWhitespaceGuidesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      }
      textRender.drawAutoSuggestionWrapped(canvas, line, pos.line, segStart, segDrawEnd, v, y);
      if (wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        float indicatorX =
            textRender.isRtl
                ? wordWrap.indicator.wordWrapIndicatorPadPx
                : Math.max(
                    wordWrap.indicator.wordWrapIndicatorPadPx,
                    wordWrap.wrapWidthPx - wordWrap.indicator.wordWrapIndicatorWidth - wordWrap.indicator.wordWrapIndicatorPadPx);
        canvas.drawText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrap.indicator.wordWrapIndicatorPaint);
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
        float cursorY = (cursorVisualIndex - firstVisualIndex) * textRender.lineHeight;
        cursorAnimation.updateCursorDrawPosition(cursorX, cursorY);
        float drawX = cursorAnimation.cursorDrawX;
        float drawY = cursorAnimation.cursorDrawY;
        if (caret.isCursorVisible) {
          caret.caretPaint.setColor(caret.caretColor);
          caret.caretPaint.setStrokeWidth(cursor.cursorWidth);
          canvas.drawLine(drawX, drawY, drawX, drawY + textRender.lineHeight, caret.caretPaint);
        }
        selectionHandles.handlePaint.setColor(cursorHandle.cursorHandleColor);
        drawTeardropHandle(canvas, drawX, drawY + textRender.lineHeight, selectionHandles.handlePaint);
        cursorHandle.cursorHandleRect.set(
            drawX - selectionHandles.handleRadius,
            drawY + textRender.lineHeight,
            drawX + selectionHandles.handleRadius,
            drawY + textRender.lineHeight + selectionHandles.handleRadius * 2);
      } else {
        cursorHandle.cursorHandleRect.setEmpty();
      }
    }

    if (selection.hasSelection) {
      selectionHandles.handlePaint.setColor(selectionHandles.selectionHandleColor);
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
        float y = (startVisual - firstVisualIndex) * textRender.lineHeight + textRender.lineHeight;
        drawTeardropHandle(canvas, x, y, selectionHandles.handlePaint);
        if (textRender.isRtl) {
          selectionHandles.rightHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
        } else {
          selectionHandles.leftHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
        }
      } else {
        if (textRender.isRtl) selectionHandles.rightHandleRect.setEmpty();
        else selectionHandles.leftHandleRect.setEmpty();
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
        float y = (endVisual - firstVisualIndex) * textRender.lineHeight + textRender.lineHeight;
        drawTeardropHandle(canvas, x, y, selectionHandles.handlePaint);
        if (textRender.isRtl) {
          selectionHandles.leftHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
        } else {
          selectionHandles.rightHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
        }
      } else {
        if (textRender.isRtl) selectionHandles.leftHandleRect.setEmpty();
        else selectionHandles.rightHandleRect.setEmpty();
      }
    }

    canvas.restore();

    if (loadingCircle.showLoadingCircle) {
      loadingCircle.loadingCirclePaint.setColor(loadingCircle.loadingCircleColor);
      loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircle.loadingCircleRotation, centerX, centerY);
      loadingCircle.loadingCircleRect.set(
          centerX - loadingCircle.loadingCircleRadius,
          centerY - loadingCircle.loadingCircleRadius,
          centerX + loadingCircle.loadingCircleRadius,
          centerY + loadingCircle.loadingCircleRadius);
      canvas.drawArc(loadingCircle.loadingCircleRect, 0, 270, false, loadingCircle.loadingCirclePaint);
      canvas.restore();
    }
  }

  public void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
    int firstIndex = Math.max(0, (int) ( scroll.scrollY / textRender.lineHeight));
    int lastIndex = firstIndex + (int) Math.ceil(getHeight() / textRender.lineHeight) + 5;
    final boolean drawDecorations = zoom.shouldDrawDecorations();

    int firstLine = firstIndex;
    int lastLine = lastIndex;
    if (codeFold.isCodeFoldingEnabled) {
      int visibleCount = codeFold.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstIndex = Math.max(0, Math.min(firstIndex, visibleCount - 1));
      lastIndex = Math.max(firstIndex, Math.min(lastIndex, visibleCount - 1));
      firstLine = codeFold.mapVisibleIndexToGlobal(firstIndex);
      lastLine = codeFold.mapVisibleIndexToGlobal(lastIndex);
    }

    maybeKickWindowLoad(firstLine);

    java.util.HashMap<Integer, String> directLines = null;
    if (isIndexReady && sourceFile != null && sourceFile.exists()) {
      textRender.directLinesTmp.clear();
      directLines = textRender.directLinesTmp;
      populateDirectLinesForRange(firstLine, lastLine, directLines);
    }

    float baseY = firstIndex * textRender.lineHeight;
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
          textRender.isRtl
              ? lineNumber.getGutterStartX()
              : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.gutterSeparatorWidth;
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumber.gutterSeparatorWidth,
          getHeight(),
          lineNumber.gutterSeparatorPaint);
    }

    if (currentLineHighlight.highlightCurrentLineInGutter
        && (!codeFold.isCodeFoldingEnabled || !codeFold.isLineHiddenByFold(cursor.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, 0);
      if (currentVisualIndex >= firstIndex && currentVisualIndex <= lastIndex) {
        float top = Math.round(currentVisualIndex * textRender.lineHeight -  scroll.scrollY);
        float bottom = top + textRender.lineHeight;
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
          textRender.isRtl
              ? lineNumber.getGutterStartX() + lineNumber.GUTTER_TEXT_PADDING
              : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - lineNumber.GUTTER_TEXT_PADDING;
    }

    // Prepare text clipping
    int saveCount = canvas.save();
    if (textRender.isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), 0); // already translated by translateY

    Paint selPaint = null;
    if (selection.hasSelection) {
      selection.selectionPaint.setColor(selection.selectionHighlightColor);
      selPaint = selection.selectionPaint;
    }

    int startLine = selection.selStartLine;
    int startChar = selection.selStartChar;
    int endLine = selection.selEndLine;
    int endChar = selection.selEndChar;
    if (selection.hasSelection && editOperators.comparePos(selection.selStartLine, selection.selStartChar, selection.selEndLine, selection.selEndChar) > 0) {
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
      if (yOffset > getHeight() + textRender.lineHeight) break;
      String text = getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(line, text);

      for (int seg = 0; seg < starts.length; seg++) {
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, text.length());
        float segBaseX = textRender.isRtl ? getRtlSegmentBaseX(text, line, segStart, segEnd) : 0f;

        float top = Math.round(yOffset);
        float bottom = top + textRender.lineHeight;
        float y = Math.round(top + textRender.lineHeight - textRender.paint.descent());

        // Draw line number ONLY for the first segment of the wrapped line
        if (lineNumber.showLineNumbers && seg == 0 && !uselineNumberCache) {
          canvas.restore(); // Exit text clip
          int start = textRender.writeIntToChars(line + 1, lineNumber.lineNumberChars);
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
          if (textRender.isRtl) {
            canvas.clipRect(0, 0, getWidth() - lineNumber.lineNumbersGutterWidth, getHeight());
          } else {
            canvas.clipRect(lineNumber.lineNumbersGutterWidth, 0, getWidth(), getHeight());
          }
          canvas.translate(getTextStartX() - getEffectiveScrollX(), 0);
        }

        if (currentLineHighlight.highlightCurrentLine && line == cursor.cursorLine && !selection.hasSelection) {
          canvas.drawRect(
              -textRender.paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, currentLineHighlight.currentLinePaint);
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
                      : measureTextWithVisualSpaces(text, segStart, segSelStart,textRender.paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wordWrap.wrapWidthPx)
                      : leftRel + measureTextWithVisualSpaces(text, segSelStart, segSelEnd,textRender.paint);
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
        if (wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          segDrawEnd = clampSegmentEndForWrapIndicator(text, segStart, segEnd, wordWrap.wrapWidthPx);
        }
        canvas.save();
        if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
        drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
        textRender.drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        textRender.drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        textRender.drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
        if (drawDecorations) {
          textRender.drawWhitespaceGuidesForSegment(canvas, text, line, segStart, segDrawEnd, y);
        }
        textRender.drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, visualIndex, y);
        if (wordWrap.indicator.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          float indicatorX =
              textRender.isRtl
                  ? wordWrap.indicator.wordWrapIndicatorPadPx
                  : Math.max(
                      wordWrap.indicator.wordWrapIndicatorPadPx,
                      wordWrap.wrapWidthPx - wordWrap.indicator.wordWrapIndicatorWidth - wordWrap.indicator.wordWrapIndicatorPadPx);
          canvas.drawText(WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrap.indicator.wordWrapIndicatorPaint);
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
            if (caret.isCursorVisible) {
              caret.caretPaint.setColor(caret.caretColor);
              caret.caretPaint.setStrokeWidth(cursor.cursorWidth);
              canvas.drawLine(drawX, drawY, drawX, drawY + textRender.lineHeight, caret.caretPaint);
            }
            selectionHandles.handlePaint.setColor(cursorHandle.cursorHandleColor);
            drawTeardropHandle(canvas, drawX, drawY + textRender.lineHeight, selectionHandles.handlePaint);
            cursorHandle.cursorHandleRect.set(
                drawX - selectionHandles.handleRadius,
                drawY + textRender.lineHeight,
                drawX + selectionHandles.handleRadius,
                drawY + textRender.lineHeight + selectionHandles.handleRadius * 2);
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

        yOffset += textRender.lineHeight;
        visualIndex++;
        if (yOffset > getHeight() + textRender.lineHeight) break;
      }
    }

    canvas.restore(); // Restore from text clip
    canvas.restore(); // Restore from translation

    if (selection.hasSelection) {
      selectionHandles.handlePaint.setColor(selectionHandles.selectionHandleColor);
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
        float y = (startHandleVisual - firstIndex) * textRender.lineHeight + textRender.lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, selectionHandles.handlePaint);
        selectionHandles.leftHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
      } else {
        selectionHandles.leftHandleRect.setEmpty();
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
        float y = (endHandleVisual - firstIndex) * textRender.lineHeight + textRender.lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, selectionHandles.handlePaint);
        selectionHandles.rightHandleRect.set(x - selectionHandles.handleRadius, y, x + selectionHandles.handleRadius, y + selectionHandles.handleRadius * 2);
      } else {
        selectionHandles.rightHandleRect.setEmpty();
      }
    }

    if (loadingCircle.showLoadingCircle) {
      loadingCircle.loadingCirclePaint.setColor(loadingCircle.loadingCircleColor);
      loadingCircle.loadingCirclePaint.setStrokeWidth(8f);
      float centerX = getWidth() / 2f;
      float centerY = getHeight() / 2f;
      canvas.save();
      canvas.rotate(loadingCircle.loadingCircleRotation, centerX, centerY);
      loadingCircle.loadingCircleRect.set(
          centerX - loadingCircle.loadingCircleRadius,
          centerY - loadingCircle.loadingCircleRadius,
          centerX + loadingCircle.loadingCircleRadius,
          centerY + loadingCircle.loadingCircleRadius);
      canvas.drawArc(loadingCircle.loadingCircleRect, 0, 270, false, loadingCircle.loadingCirclePaint);
      canvas.restore();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    textRender.drawEditorBackground(canvas);
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
  
  

  
  public static int trimUrlUnderlineEnd(String line, int start, int end) {
    return UrlUnderline.trimUrlUnderlineEnd(line, start, end);
  }

  public int getVisualSpaceScale() {
    return 1;
  }

  public int getWhitespaceGuideStep() {
    return whitespaceGuides.getWhitespaceGuideStep();
  }

  public float getVisualSpaceWidth(Paint p) {
    return p.measureText(" ");
  }

  public float getVisualTabWidth(Paint p) {
    // Treat tab as a fixed number of spaces.
    return getVisualSpaceWidth(p) * TextRender.DEFAULT_TAB_SIZE_SPACES;
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
    if (textRender.measureWidthBuffer == null || textRender.measureWidthBuffer.length < len) {
      textRender.measureWidthBuffer = new float[len];
    }
    p.getTextWidths(text, start, end, textRender.measureWidthBuffer);
    float total = 0f;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(start + i);
      total += getCharAdvanceWidth(c, textRender.measureWidthBuffer[i], p);
    }
    return total;
  }

  

  public List<TextRender.HighlightSpan> calculateSyntaxSpansForLine(String line, int globalLine) {
    if (getLogicalLineLength(globalLine, line) > maxSyntaxLineLength) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    TextRender.HighlightLineState startState = getLineStateAtStart(globalLine);
    TextRender.LineParseResult parseResult =
        parseLineForSyntax(
            line,
            startState.inBlockComment,
            startState.stringState,
            textRender.whitespaceStringRule,
            textRender.whitespaceCommentRule,
            true);

    if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) {
      if (highlite.isBlockCommentsEnabled) {
        blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
      }
      stringEndStateCache.put(globalLine, parseResult.endsInStringState);
    }

    List<TextRender.HighlightSpan> spans = parseResult.spans;
    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  public List<TextRender.HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
    TextRender.HighlightRule stringRule = stringHighlightRule;
    TextRender.HighlightRule commentRule = blockCommentHighlightRule;
    if (stringRule == null && commentRule == null) {
      return calculateSyntaxSpansForLine(line, globalLine);
    }

    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }
    if (spans.isEmpty()) return Collections.emptyList();

    Paint stringPaint = (stringRule != null) ? stringRule.paint : null;
    Paint commentPaint = (commentRule != null) ? commentRule.paint : null;
    if (stringPaint == null && commentPaint == null) return Collections.emptyList();

    ArrayList<TextRender.HighlightSpan> syntaxSpans = null;
    for (TextRender.HighlightSpan span : spans) {
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
      List<TextRender.HighlightSpan> syntaxSpans,
      boolean hasSyntaxSpans,
      TextRender.WhitespaceDrawState state,
      float rtlWidth) {
    return whitespaceGuides.drawWhitespaceGuidesSegment(canvas, line, start, end, x, y, segmentPaint, syntaxSpans, hasSyntaxSpans, state, rtlWidth);
  }

  public List<TextRender.HighlightSpan> calculateSpansForLine(String line, int globalLine) {
    return highlite.calculateSpansForLine(line, globalLine);
  }

  public TextRender.LineParseResult parseLineForSyntax(
      String line,
      boolean inBlockComment,
      int stringState,
      TextRender.HighlightRule stringRule,
      TextRender.HighlightRule blockCommentRule,
      boolean collectSpans) {
    return highlite.parseLineForSyntax(line, inBlockComment, stringState, stringRule, blockCommentRule, collectSpans);
  }

  public TextRender.HighlightLineState getLineStateAtStart(int globalLine) {
    return highlite.getLineStateAtStart(globalLine);
  }

  public static boolean hasOverlap(TextRender.HighlightSpan span, List<TextRender.HighlightSpan> spans) {
    return Highlite.hasOverlap(span, spans);
  }

  public static boolean isLineCommentRegex(String regex) {
    return Highlite.isLineCommentRegex(regex);
  }

  public boolean isStringDelimiter(char c) {
    return highlite.isStringDelimiter(c);
  }

  public static boolean isTokenEscaped(String line, int index) {
    return Highlite.isTokenEscaped(line, index);
  }

  public static boolean isEscaped(String line, int index) {
    return Highlite.isEscaped(line, index);
  }

  public static int findStringEnd(String line, int start, char delimiter) {
    return Highlite.findStringEnd(line, start, delimiter);
  }

  public boolean isTripleQuoteStart(String line, int index) {
    return highlite.isTripleQuoteStart(line, index);
  }

  public static int findTripleQuoteEnd(String line, int start) {
    return Highlite.findTripleQuoteEnd(line, start);
  }

  // String state constants - deprecated, use Highlite constants
  @Deprecated public static final int STRING_STATE_DOUBLE = Highlite.STRING_STATE_DOUBLE;
  @Deprecated public static final int STRING_STATE_SINGLE = Highlite.STRING_STATE_SINGLE;
  @Deprecated public static final int STRING_STATE_BACKTICK = Highlite.STRING_STATE_BACKTICK;
  @Deprecated public static final int STRING_STATE_TRIPLE = Highlite.STRING_STATE_TRIPLE;

  public int getStringStateForDelimiter(char delimiter) {
    return highlite.getStringStateForDelimiter(delimiter);
  }

  public StringEndResult findStringEndForState(String line, int start, int state) {
    return highlite.findStringEndForState(line, start, state);
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
    return Highlite.findBlockCommentEnd(line, start);
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
    return highlite.isLineCommentStart(line, index);
  }

  public void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
    indentGuides.drawIndentGuidesForLine(canvas, line, globalLine);
  }

  public boolean isLineInIndentBlock(int globalLine) {
    return indentGuides.isLineInIndentBlock(globalLine);
  }

  public void rebuildIndentGuideIntervalsIfNeeded() {
    indentGuides.rebuildIndentGuideIntervalsIfNeeded();
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

  public static List<BracketGuides.BracketGuideToken> getGuideTokensFromStack(
      java.util.ArrayDeque<BracketGuides.BracketGuideToken> stack) {
    return BracketGuides.getGuideTokensFromStack(stack);
  }

  public int getBracketGuideCacheConfigHash() {
    return bracketGuides.getBracketGuideCacheConfigHash();
  }

  public void invalidateBracketGuideCache() {
    bracketGuides.invalidateBracketGuideCache();
  }

  public void ensureBracketGuideCacheForWindow(
      @Nullable java.util.Map<Integer, String> directLines) {
    bracketGuides.ensureBracketGuideCacheForWindow(windowStartLine, windowStartLine + linesWindow.size() - 1, directLines);
  }

  public List<BracketGuides.BracketGuideToken> getBracketGuideTokensForLine(int globalLine) {
    return bracketGuides.getBracketGuideTokensForLine(globalLine);
  }

  public float getGuideXForColumn(String line, int column, int globalLine) {
    if (line == null) line = "";
    if (column <= line.length()) {
      return measureText(line, column, globalLine);
    }
    float base = measureText(line, line.length(), globalLine);
    float spaceWidth = getVisualSpaceWidth(textRender.paint);
    return base + spaceWidth * (column - line.length());
  }

  public void drawBracketGuidesForLine(
      Canvas canvas, String line, int globalLine, List<BracketGuides.BracketGuideToken> guideTokens) {
    bracketGuides.drawBracketGuidesForLine(canvas, line, globalLine, guideTokens);
  }

  public boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    // Fast hit-test using per-char advances (avoids O(n^2) measureText calls),
    // but respects syntax styles (bold/italic) so guide X aligns with text width.
    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
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
      for (TextRender.HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          if (hitTestWhitespaceSegment(
              line,
              pos,
              Math.min(span.start, len),
              globalLine,
              x,
              textRender.paint,
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
          line, pos, len, globalLine, x,textRender.paint, eps, currentX, prevWhitespace)) {
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
    if (textRender.measureWidthBuffer == null || textRender.measureWidthBuffer.length < segLen) {
      textRender.measureWidthBuffer = new float[Math.max(segLen, 64)];
    }
    p.getTextWidths(line, start, end, textRender.measureWidthBuffer);

    float currentX = startX;
    boolean prevWs = prevWhitespace;
    for (int i = 0; i < segLen; i++) {
      int idx = start + i;
      char c = line.charAt(idx);
      float adv = getCharAdvanceWidth(c, textRender.measureWidthBuffer[i], p);
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
    colorCodeHighlight.drawColorCodeBackgrounds(canvas, line, globalLine);
  }

  public float measureText(String line, int length, int globalLine) {
    int logicalLen = getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));
    if (logicalLen > maxSyntaxLineLength) {
      float avg = textRender.getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return measureTextWithVisualSpaces(line, 0, safeLen,textRender.paint);
    }

    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return measureTextWithVisualSpaces(line, 0, safeLen,textRender.paint);
    }

    float totalWidth = 0;
    int lastEnd = 0;

    for (TextRender.HighlightSpan span : spans) {
      if (lastEnd >= safeLen) break;
      if (span.start >= safeLen) break;
      if (span.start < lastEnd) continue;

      // Measure part before the span
      if (span.start > lastEnd) {
        int measureEnd = Math.min(span.start, safeLen);
        totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd,textRender.paint);
      }

      lastEnd = span.start;

      // Measure the span itself
      int measureEnd = Math.min(span.end, safeLen);
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, measureEnd, span.paint);

      lastEnd = span.end;
    }

    // Measure remaining part
    if (lastEnd < safeLen) {
      totalWidth += measureTextWithVisualSpaces(line, lastEnd, safeLen,textRender.paint);
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
    if (wordWrap.isWordWrapEnabled) return;
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
        textRender.computeStreamedSliceBounds(slice, line, len, streamedSliceTmp);
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
    Paint.Style prevStyle = textRender.paint.getStyle();
    float prevStroke = textRender.paint.getStrokeWidth();
    Paint.Cap prevCap = textRender.paint.getStrokeCap();

    textRender.paint.setStyle(Paint.Style.FILL);
    textRender.teardropPath.reset();
    textRender.teardropPath.addOval(
        cx - selectionHandles.handleRadius, cy, cx + selectionHandles.handleRadius, cy + selectionHandles.handleRadius * 2, Path.Direction.CW);
    canvas.drawPath(textRender.teardropPath,textRender.paint);

    textRender.paint.setStyle(prevStyle);
    textRender.paint.setStrokeWidth(prevStroke);
    textRender.paint.setStrokeCap(prevCap);
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
    return lines > selection.copyCutMaxLines;
  }

  public void setCopyCutMaxLines(long maxLines) {
    selection.copyCutMaxLines = Math.max(1L, maxLines);
  }

  public void setCopyCutMaxChars(int maxChars) {
    selection.copyCutMaxChars = Math.max(1, maxChars);
  }

  public void setHideCopyCutMaxLines(int maxLines) {
    selection.copyCutMaxLines = Math.max(1, maxLines);
  }

  public int getReplaceAllMaxCount() {
    return selection.replaceAllMaxCount;
  }

  public void setHideKeyboardOnFocusLoss(boolean enabled) {
    selection.hideKeyboardOnFocusLoss = enabled;
  }


  public void checkAndLoadWindow() {
    if (sourceFile == null || isFileCleared) return;
    if (getWidth() == 0 || getHeight() == 0) return;
    if (isWindowLoading) return;

    int firstVisibleIndex = (int) ( scroll.scrollY / textRender.lineHeight);
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(getHeight() / textRender.lineHeight);
    int firstVisibleLine;
    int lastVisibleLine;
    if (wordWrap.isWordWrapEnabled) {
      firstVisibleLine = getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleLine = getVisualPositionForIndex(lastVisibleIndex).line;
    } else {
      firstVisibleLine = codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
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
    loadingCircle.maxWidthRecalcToken++;

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
                        Math.max(1, Math.min(lineLen, textRender.getInitialStreamedSliceSize()));
                    if (isSingleByteCharset()) {
                      String slice =
                          readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, textRender.getInitialStreamedSliceSize());
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
                        Math.max(1, Math.min(lineLen, textRender.getInitialStreamedSliceSize()));
                    if (isSingleByteCharset()) {
                      String slice =
                          readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, textRender.getInitialStreamedSliceSize());
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
                            ? binaryRender.bytesToControlVisible(buf, buf.length)
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
                  if (wordWrap.isWordWrapEnabled) {
                    if (shouldSuppressWrapMetricsForFastSelectAll()) {
                      wordWrap.wrapMetricsReady = false;
                    } else {
                      if (!wordWrap.wrapMetricsReady || wordWrap.wrapLineCounts == null || wordWrap.wrapLinePrefix == null) {
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
    if (!loadingCircle.isInitialFileOpenLoading) return;
    if (token != loadingCircle.initialFileOpenToken) return;
    if (getHeight() <= 0 || textRender.lineHeight <= 0f) {
      postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
      return;
    }

    int firstVisibleLine = Math.max(0, getGlobalLineForY( scroll.scrollY));
    int viewHeight = getHeight() - keyboardHeight;
    if (viewHeight <= 0) viewHeight = getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(viewHeight / textRender.lineHeight) + 2);
    int lastVisibleLine = firstVisibleLine + visibleLines;

    ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, null);
    
    // Scan brackets for fold detection BEFORE showing the file
    if (codeFold.isCodeFoldingEnabled && bracketCache != null) {
      // Start scan and wait for it to complete
      bracketCache.scanFileAsync();
      // Poll until scan is complete (max 5 seconds)
      pollScanCompletion(token, 0);
    } else {
      finishFileOpen(token);
    }
  }

  private void pollScanCompletion(final int token, int attempts) {
    if (token != loadingCircle.initialFileOpenToken) return;
    if (attempts > 300) { // 5 seconds max
      finishFileOpen(token);
      return;
    }
    if (!bracketCache.isScanning()) {
      finishFileOpen(token);
      return;
    }
    ioHandler.postDelayed(() -> pollScanCompletion(token, attempts + 1), 16);
  }

  private void finishFileOpen(final int token) {
    if (token != loadingCircle.initialFileOpenToken) return;
    
    loadingCircle.isInitialFileOpenLoading = false;
    if (loadingCircle.initialFileOpenShowSpinner != null) {
      caret.mainHandler.removeCallbacks(loadingCircle.initialFileOpenShowSpinner);
      loadingCircle.initialFileOpenShowSpinner = null;
    }
    setDisable(false);
    loadingCircle.showLoadingCircle(false);
    invalidate();

    java.util.ArrayList<Runnable> callbacks;
    synchronized (loadingCircle.initialLoadCallbacks) {
      if (loadingCircle.initialLoadCallbacks.isEmpty()) return;
      callbacks = new java.util.ArrayList<>(loadingCircle.initialLoadCallbacks);
      loadingCircle.initialLoadCallbacks.clear();
    }
    for (Runnable cb : callbacks) {
      post(cb);
    }
  }

  public void runAfterInitialLoad(@Nullable Runnable action) {
    if (action == null) return;
    if (!loadingCircle.isInitialFileOpenLoading) {
      post(action);
      return;
    }
    synchronized (loadingCircle.initialLoadCallbacks) {
      loadingCircle.initialLoadCallbacks.add(action);
    }
  }

  public void recalculateMaxLineWidthAsync() {
    final int token = ++loadingCircle.maxWidthRecalcToken;
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
            if (token != loadingCircle.maxWidthRecalcToken) return;
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
                if (wordWrap.isWordWrapEnabled) post(SodiumEditor.this::scheduleWrapMetricsBuild);
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
    if (wordWrap.isWordWrapEnabled) invalidateWrapMetrics();
    if (codeFold.isCodeFoldingEnabled) {
      codeFold.foldIntervalsDirty = true;
    }
  }

  public void invalidatePendingIOForEdit() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    clearHighlightCaches();
    if (codeFold.isCodeFoldingEnabled) {
      codeFold.foldIntervalsDirty = true;
      indentGuides.markIntervalsDirty();
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

    // Clear bracket cache and folds
    if (codeFold.isCodeFoldingEnabled) {
      bracketCache.clear();
      codeFold.clearAllFolds();
    }

    // Force clear wrap metrics as content is being cleared
    wordWrap.wrapMetricsReady = false;
    wordWrap.wrapLineCounts = null;
    wordWrap.wrapLinePrefix = null;
    wordWrap.totalWrapVisualLines = 0;
    wordWrap.wrapPrefixValidUpToLine = -1;

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
    wordWrap.wrapMetricsReady = false;
    wordWrap.wrapLineCounts = null;
    wordWrap.wrapLinePrefix = null;
    wordWrap.totalWrapVisualLines = 0;
    wordWrap.wrapPrefixValidUpToLine = -1;

    final int token = ++loadingCircle.initialFileOpenToken;
    loadingCircle.isInitialFileOpenLoading = true;
    if (loadingCircle.showLoadingOnFileOpen) {
      if (loadingCircle.initialFileOpenShowSpinner != null) {
        caret.mainHandler.removeCallbacks(loadingCircle.initialFileOpenShowSpinner);
      }
      loadingCircle.initialFileOpenShowSpinner =
          () -> {
            if (!loadingCircle.showLoadingOnFileOpen) return;
            if (!loadingCircle.isInitialFileOpenLoading) return;
            if (token != loadingCircle.initialFileOpenToken) return;
            setDisable(true);
            loadingCircle.showLoadingCircle(true);
          };
      caret.mainHandler.postDelayed(loadingCircle.initialFileOpenShowSpinner, 80);
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
    editOperators.lineCountDelta = 0;

    loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
    ioHandler.post(this::buildFileIndex);
    requestLayout();
    invalidate();
  }

  public void updateSourceFile(File file) {
    sourceFile = file;
  }

  public int getEditVersionValue() {
    return editOperators.editVersion.get();
  }

  public void refreshlineNumberCache() {
    lineNumber.invalidateLineNumberCache();
    requestLayout();
    invalidate();
  }

  public void setTextColor(int color) {
    textRender.paint.setColor(color);
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

  

  

 

  public void goToLine(int line) {
    goToLine(line, 1);
  }

  public void goToLine(int line, int col) {
    final int currentGoToLineVersion = goToLineVersion.incrementAndGet();
    setDisable(true);
    loadingCircle.showLoadingCircle(true);

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
      caret.mainHandler.postDelayed(
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
          loadingCircle.showLoadingCircle(false);

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

  
  public void insertNewlineAtCursor() {
    autoBracketNewline.insertNewlineAtCursor();
  }

  public AutoBracketNewline.BracketPairType getCursorBracketPairType() {
    return autoBracketNewline.getCursorBracketPairType();
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
        width += TextRender.DEFAULT_TAB_SIZE_SPACES;
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

  

  

  public void commitComposing(boolean keepInText) {
    ime.commitComposing(keepInText);
  }

  public void replaceComposingWith(CharSequence textSeq) {
    ime.replaceComposingWith(textSeq);
  }

  public void deleteComposing() {
    ime.deleteComposing();
  }



  public void setSelectionRange(int sLine, int sChar, int eLine, int eChar) {
    selection.setSelectionInternal(sLine, sChar, eLine, eChar);
    invalidate();
  }

  

  
  public void copySelectionToClipboard() {
    selection.copyOrCutSelection(false);
  }

  public void actionCopy() {
    copySelectionToClipboard();
  }

  public void cutSelectionToClipboard() {
    selection.copyOrCutSelection(true);
  }

  public void actionCut() {
    cutSelectionToClipboard();
  }

  


  public void pasteFromClipboard() {
    invalidatePendingIOForEdit();
    editOperators.editVersion.incrementAndGet();
    clearActiveSuggestion(); // Clear suggestion when pasting

    ClipboardManager cm =
        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(getContext());
    if (txt == null) return;
    editOperators.insertTextAtCursor(txt.toString());
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
  

  public void actionDelete() {
    selection.deleteSelection();
  }

  

  public boolean canUndo() {
    return !editOperators.undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !editOperators.redoStack.isEmpty();
  }

  public int getUndoStackSize() {
    return editOperators.undoStack.size();
  }

  public int getPendingEditsCount() {
    return editOperators.pendingEdits.size();
  }



  

  public void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    ime.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  public String readRangeText(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (editOperators.comparePos(startL, startC, endL, endC) > 0) {
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
    EditOperators.RangeBytes range = editOperators.computeByteRangeFastOrScan(sourceFile, startL, startC, endL, endC);
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

  

  public void applyMultiLineReplaceInWindowNow(
      int sL, int sC, int eL, int eC, String insertText, EditOperators.CursorTarget target) {
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

  
  public static final class RangeBytes {
    final long startByte, endByte;

    RangeBytes(long s, long e) {
      startByte = s;
      endByte = e;
    }
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
    if (binaryRender.isBinarySafeRenderingEnabled()) {
      byte[] data = baos.toByteArray();
      return binaryRender.bytesToControlVisible(data, data.length);
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
    return binaryRender.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar, fileCharset);
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
    return binaryRender.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength, fileCharset);
  }

  public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    if (lineText == null) return 0L;
    int safe = Math.max(0, Math.min(charIndex, lineText.length()));
    if (safe == 0) return 0L;
    return lineText.substring(0, safe).getBytes(fileCharset).length;
  }

  public int getCharIndexForX(String text, float x, int globalLine) {
    if (text == null || text.isEmpty()) return 0;
    if (textRender.isRtl) {
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
      float avg = textRender.getAverageCharWidthForLine(text, globalLine);
      if (avg <= 0f) return 0;
      int idx = (int) Math.round(x / avg);
      return Math.max(0, Math.min(idx, len));
    }
    int textLen = text.length();
    if (getVisualSpaceScale() == 1) {
      int count = textRender.paint.breakText(text, true, x, null);
      if (count <= 0) return 0;
      if (count >= textLen) return textLen;

      // Choose nearest boundary between (count-1) and count based on midpoint of last glyph.
      float wPrev = (count > 1) ? textRender.paint.measureText(text, 0, count - 1) : 0f;
      float wCount = textRender.paint.measureText(text, 0, count);
      float mid = wPrev + (wCount - wPrev) * 0.5f;
      return (x < mid) ? (count - 1) : count;
    }

    if (textRender.measureWidthBuffer == null || textRender.measureWidthBuffer.length < textLen) {
      textRender.measureWidthBuffer = new float[textLen];
    }
    textRender.paint.getTextWidths(text, 0, textLen, textRender.measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < textLen; i++) {
      float adv = getCharAdvanceWidth(text.charAt(i), textRender.measureWidthBuffer[i],textRender.paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return i;
      if (x < current + adv) return i + 1;
      current += adv;
    }
    return textLen;
  }

  public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    return wordWrap.getCharIndexForXInRange(text, globalLine, start, end, x);
  }

  public EditOperators.CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    return wordWrap.getCursorTargetForPosition(viewX, viewY, directLines);
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
    return binaryRender.bytesToControlVisible(buf, len);
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
    if (wordWrap.isWordWrapEnabled) return false;
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
      if (binaryRender.isBinarySafeRenderingEnabled()) return true;
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
      w = textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(),textRender.paint);
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
      w = textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(),textRender.paint);
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
    autoBracketPair.handleAutoPairing(text);
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
    onTouch.drawSelectionSegment(canvas, left, top, right, bottom, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft,textRender.paint);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (onKeyDown.onKeyDown(keyCode, event)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void moveCursorLeft() {
    cursor.moveCursorLeft();
  }

  public void moveCursorRight() {
    cursor.moveCursorRight();
  }

  public void moveCursorUp() {
    cursor.moveCursorUp();
  }

  public void moveCursorDown() {
    cursor.moveCursorDown();
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
      if (selection.hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
      caret.mainHandler.removeCallbacks(caret.blinkRunnable);
      caret.isCursorVisible = true; // Make sure it's visible when not focused
      ime.hasComposing = false;
      selection.hasSelection = false;
    }
  }

  public void invalidateLineGlobal(int globalLine) {
    if (wordWrap.isWordWrapEnabled) {
      invalidate();
      return;
    }
    int idx = codeFold.isCodeFoldingEnabled ? codeFold.getVisibleIndexForGlobalLine(globalLine) : globalLine;
    float top = (idx * textRender.lineHeight) -  scroll.scrollY;
    invalidate(0, (int) Math.floor(top), getWidth(), (int) Math.ceil(top + textRender.lineHeight));
  }

  public void invalidateCursorArea() {
    if (wordWrap.isWordWrapEnabled) {
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
      if (!hasEdits && editOperators.lineCountDelta == 0) {
        return lineOffsets.length;
      }
      int count = lineOffsets.length + editOperators.lineCountDelta;
      if (count < 1) count = 1;
      return Math.max(count, windowCount);
    }
    if (isEof) return windowStartLine + linesWindow.size();
    if (!linesWindow.isEmpty()) return windowStartLine + linesWindow.size();
    return -1;
  }

  public int getVisualIndexForLineAndChar(int line, int ch) {
    if (!isWrapMetricsUsableForLine(line)) {
      if (codeFold.isCodeFoldingEnabled) return codeFold.getVisibleIndexForGlobalLine(line);
      return Math.max(0, line);
    }
    int totalLines = wordWrap.wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = getLineTextForRender(safeLine);
    int[] starts = getWrapStartsForLine(safeLine, text);
    int seg = getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    return wordWrap.wrapLinePrefix[safeLine] + seg;
  }

  public int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / textRender.lineHeight));
    if (wordWrap.isWordWrapEnabled) {
      return getVisualPositionForIndex(idx).line;
    }
    return codeFold.mapVisibleIndexToGlobal(idx);
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
    float reserved = wordWrap.indicator.wordWrapIndicatorWidth + (wordWrap.indicator.wordWrapIndicatorPadPx * 2f);
    float available = wrapWidthPx - reserved;
    if (available <= 0f) return segStart;
    float width = measureTextWithVisualSpaces(line, segStart, segEnd,textRender.paint);
    if (width <= available) return segEnd;
    int end = segEnd;
    while (end > segStart) {
      end--;
      float w = measureTextWithVisualSpaces(line, segStart, end,textRender.paint);
      if (w <= available) break;
    }
    return end;
  }

  public float getBottomBarrierPadding() {
    float base = TextRender.BOTTOM_SCROLL_OFFSET;
    float minSpace = TextRender.MIN_BOTTOM_VISIBLE_SPACE;
    if (textRender.lineHeight > 0f) {
      base = Math.max(base, textRender.lineHeight * 2f);
      minSpace = Math.max(minSpace, textRender.lineHeight * 2f);
    }
    return Math.max(base, minSpace);
  }

  public float getKeyboardBarrierPadding() {
    if (keyboardHeight <= 0) return 0f;
    float minPad = (textRender.lineHeight > 0f) ? textRender.lineHeight * 2f : TextRender.MIN_BOTTOM_VISIBLE_SPACE;
    float maxPad = (textRender.lineHeight > 0f) ? textRender.lineHeight * 3.5f : TextRender.BOTTOM_SCROLL_OFFSET;
    float kbPad = keyboardHeight * 0.4f;
    return Math.max(minPad, Math.min(maxPad, kbPad));
  }

  public void keepCursorVisibleHorizontally() {
    if (scaleGestureDetector != null
        && (zoom.isScaling || scaleGestureDetector.isInProgress() || multiTouchActive)) {
      return;
    }
    int cursorVisualIndex = getVisualIndexForLineAndChar(cursor.cursorLine, cursor.cursorChar);
    float cursorYTop = cursorVisualIndex * textRender.lineHeight;
    float cursorYBottom = cursorYTop + textRender.lineHeight;
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

    if (!wordWrap.isWordWrapEnabled) {
      String line = getLineTextForRender(cursor.cursorLine);
      int safeChar = Math.min(cursor.cursorChar, getLogicalLineLength(cursor.cursorLine, line));
      float cursorX = getCaretXForLine(line, cursor.cursorLine, safeChar);

      float viewLeft = textRender.isRtl ? 0f : lineNumber.lineNumbersGutterWidth;
      float viewRight = textRender.isRtl ? (getWidth() - lineNumber.lineNumbersGutterWidth) : getWidth();
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
      float minEffective = textRender.isRtl ? -max : 0f;
      float maxEffective = textRender.isRtl ? 0f : max;
      if (effectiveScrollX < minEffective) effectiveScrollX = minEffective;
      if (effectiveScrollX > maxEffective) effectiveScrollX = maxEffective;
      scroll.scrollX =textRender.isRtl ? -effectiveScrollX : effectiveScrollX;
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
    if (textRender.isRtl) {
      float baseX = getRtlLineBaseX(lineContent, globalLine);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = globalLine * textRender.lineHeight -  scroll.scrollY;
    float bottom_view = (globalLine + 1) * textRender.lineHeight -  scroll.scrollY;

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
            textRender.computeStreamedSliceBounds(null, cur, lineLen, streamedSliceTmp);
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
