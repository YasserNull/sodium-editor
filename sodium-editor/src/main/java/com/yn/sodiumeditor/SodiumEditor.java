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
import com.yn.sodiumeditor.input.events.OnScroll;
import com.yn.sodiumeditor.input.events.OnTouch;
import com.yn.sodiumeditor.input.events.OnKeyDown;

import com.yn.sodiumeditor.core.features.*;
import com.yn.sodiumeditor.core.autocompletion.*;
import com.yn.sodiumeditor.core.selection.*;
import com.yn.sodiumeditor.core.cursor.*;
import com.yn.sodiumeditor.core.highlite.*;
import com.yn.sodiumeditor.core.*;
import com.yn.sodiumeditor.core.cache.*;




import com.yn.sodiumeditor.io.*;
import com.yn.sodiumeditor.renderer.*;
//import com.yn.sodiumeditor.renderer.animation.*;
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

import com.yn.sodiumeditor.input.Ime;

public class SodiumEditor extends View {

  public static final boolean DEBUG_RENDER_LOGS = true;

  private final java.util.HashMap<String, Long> renderLogLast = new java.util.HashMap<>();

  public static final int STYLE_NORMAL = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_BOLD_ITALIC = 3;

  // paint & metrics
  
  
  
  
  
  public final int[] tmpLocationInWindow = new int[2];
  // visual padding constants
  
  // scroll state (pixels)
  

  // sliding window
  
  // File I/O manager
  public final FileIO fileIO;

  // caches

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
public final AutoCompletion autoCompletion;
public final AutoPathCompletion autoPathCompletion;
public final ErrorUnderline errorUnderline;
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
    public int line;
    public int start;
    public int end;

    public SearchMatch(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  public void applyPendingWrapPrefixUpdateIfAny() {
    wordWrap.applyPendingWrapPrefixUpdateIfAny();
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


 
  // Bracket cache for fast fold and bracket matching
  public final BracketCache bracketCache;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;
  public final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables

  // Edit operators manager
  public final EditOperators editOperators;

  // View renderer for handling all drawing operations
  public final ViewRender viewRenderer;

  

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
  
  public int lastHighlightEnsureStartLine = -1;
  public int lastHighlightEnsureEndLine = -1;
  public int lastHighlightEnsureEditVersion = -1;
  private long lastHighlightInvalidateMs = 0L;
  private static final long HIGHLIGHT_ENSURE_THROTTLE_MS = 50L;
  private static final long HIGHLIGHT_INVALIDATE_THROTTLE_MS = 50L;
  private long lastTypingMs = 0L;
  private static final long HIGHLIGHT_TYPING_WINDOW_MS = 180L;

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


  // Word wrap manager
  public final WordWrap wordWrap;
  
  

  
  
  

  public static class BracketMatch {
    public final int openLine;
    public final int openChar;
    public final int closeLine;
    public final int closeChar;

    public BracketMatch(int openLine, int openChar, int closeLine, int closeChar) {
      this.openLine = openLine;
      this.openChar = openChar;
      this.closeLine = closeLine;
      this.closeChar = closeChar;
    }
  }

  public static class BracketToken {
    public final int line;
    public final int ch;
    public final char bracket;

    public BracketToken(int line, int ch, char bracket) {
      this.line = line;
      this.ch = ch;
      this.bracket = bracket;
    }
  }

  

  
   

    


  public final Runnable delayedWindowCheck =
      new Runnable() {
        @Override
        public void run() {
          fileIO.checkAndLoadWindow();
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
errorUnderline = new ErrorUnderline(this);
    float density = getContext().getResources().getDisplayMetrics().density;

    touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();


    // Initialize Scroll
    scroll = new Scroll(this);
    // Sync scroll configuration to Scroll
    scroll.edge.setEdgeEffectColor(0x80808080); // لون رمادي

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
    autoCompletion = new AutoCompletion(this);
    autoPathCompletion = new AutoPathCompletion(this);
    loadingCircle = new LoadingCircle(this);

    // Initialize EditOperators
    editOperators = new EditOperators(this);

    // Initialize ViewRender
    viewRenderer = new ViewRender(this);

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

    // Initialize FileIO
    fileIO = new FileIO(this);


    textRender.paint.setTextSize(36);
    textRender.paint.setTypeface(Typeface.MONOSPACE);
    textRender.paint.setColor(0xFF000000);
    textRender.paint.setAntiAlias(true);
    textRender.paint.setSubpixelText(true);
    textRender.paint.setHinting(Paint.HINTING_ON);
    textRender.paint.setUnderlineText(false); // Explicitly disable underlines to fix visual artifact
    codeFold.foldMarkerTextScale = 1f;
    codeFold.foldMarkerPaint.setTextSize(textRender.paint.getTextSize());
    textRender.baseTypeface = (textRender.paint.getTypeface() != null) ? textRender.paint.getTypeface() : Typeface.MONOSPACE;
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

    int primaryBlue = 0xFF2196F3;
    caret.caretColor = primaryBlue;
    cursorHandle.cursorHandleColor = primaryBlue;
    selectionHandles.selectionHandleColor = primaryBlue;
    selection.selectionHandleColor = primaryBlue;

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
    autoCompletion.suggestionPaint.set(textRender.paint);
    autoCompletion.suggestionPaint.setColor(0xFFAAAAAA); // Default faint gray
    autoCompletion.suggestionPaint.setAntiAlias(true);
    autoCompletion.suggestionPaint.setSubpixelText(true);
    autoCompletion.suggestionPaint.setHinting(Paint.HINTING_ON);
    autoCompletion.isSuggestionTextSizeCustom = false; // By default, suggestion size follows main text
    autoCompletion.suggestionTextSizeScale = 1f;

    setPathUnderliningEnabled(true); // Enable path underlining by default
  }

  // --- Public APIs for Auto Completion ---

  
  

  


  

  public int heavyFeaturesThreshold = 50000;

  public void setPerformanceModeEnabled(boolean enabled) {
    if (this.textRender.isPerformanceModeEnabled == enabled) return;
    this.textRender.isPerformanceModeEnabled = enabled;
    if (enabled) {
      setUrlUnderliningEnabled(false);
      setPathUnderliningEnabled(false);
      colorCodeHighlight.setColorCodeHighlightingEnabled(false);
      bracketMatchManager.setBracketMatchingEnabled(false);
      bracketGuides.setBracketGuidesEnabled(false);
      indentGuides.setIndentGuidesEnabled(false);
      whitespaceGuides.setWhitespaceGuidesEnabled(false);
      wordWrap.setWordWrapIndicatorEnabled(false);
      autoCompletion.setAutoCompletionEnabled(false);
      autoPathCompletion.setAutoPathCompletionEnabled(false);
      charAnimation.setCharAnimation(false, charAnimation.charAnimationDurationMs);
      currentLineHighlight.setHighlightCurrentLine(false);
      setIndentationBlocksEnabled(false);
      setCodeFoldingEnabled(false);
    }
    invalidate();
  }

  

  public int getWindowEndLine() {
    synchronized (textRender.linesWindow) {
      return Math.max(0, textRender.windowStartLine + textRender.linesWindow.size() - 1);
    }
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

  

  public void setEditorBackgroundColor(int color) {
    textRender.setEditorBackgroundColor(color);
  }

  public void clearEditorBackgroundColor() {
    textRender.clearEditorBackgroundColor();
  }

  public void setEditorBackgroundImageFromAssets(String assetPath) {
    if (assetPath == null) return;
    try (InputStream input = getContext().getAssets().open(assetPath)) {
      Bitmap bmp = BitmapFactory.decodeStream(input);
      if (bmp != null) {
        textRender.setEditorBackgroundBitmap(bmp);
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
        textRender.setEditorBackgroundBitmap(bmp);
      }
    } catch (Exception e) {
      Log.e("SodiumEditor", "setEditorBackgroundImageFromFile failed: " + filePath, e);
    }
  }

  public void clearEditorBackgroundImage() {
    textRender.clearEditorBackgroundImage();
  }

  public void loadFromFile(File file) {
    fileIO.loadFromFile(file);
  }

  public void setEditorBackgroundBitmap(Bitmap bitmap) {
    textRender.setEditorBackgroundBitmap(bitmap);
  }

  

  

  public void setCursorPositionNoClear(int line, int col) {
    cursor.setCursorPositionNoClear(line, col);
  }

  

  
  
  
  public void setCursorWidth(float width) {
    if (this.cursor.baseCursorWidthPx == width && this.cursor.baseCursorTextSizePx == textRender.paint.getTextSize()) return;
    this.cursor.baseCursorWidthPx = width;
    this.cursor.baseCursorTextSizePx = textRender.paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }

  
  public void setIndentationBlocksEnabled(boolean enabled) {
    if (this.isIndentationBlocksEnabled == enabled) return;
    this.isIndentationBlocksEnabled = enabled;
    codeFold.setIndentationBlocksEnabled(enabled);
  }

  public void setCodeFoldingEnabled(boolean enabled) {
    codeFold.setCodeFoldingEnabled(enabled);
    if (enabled && !lineNumber.showLineNumbers) {
      codeFold.setCodeFoldingEnabled(false);
    }
  }

  public void setFoldPlaceholderColor(int color) {
    codeFold.setFoldPlaceholderColor(color);
  }

  public void setFoldMarkerColor(int color) {
    codeFold.setFoldMarkerColor(color);
  }

  public void setFoldMarkerTextSize(float size) {
    codeFold.setFoldMarkerTextSize(size);
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
  }

  public void invalidateHighlightCacheForLine(int line) {
    highlite.invalidateHighlightCacheForLine(line);
    colorCodeHighlight.clearColorCodeCacheForLine(line);
    urlUnderline.clearUrlUnderlineCacheForLine(line);
    pathUnderline.clearPathUnderlineCacheForLine(line);
    invalidateHighlightEnsureRange();
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
    if (wordWrap.isWordWrapEnabled) wordWrap.invalidateWrapMetrics(true);
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


  public void insertTextAt(int line, int col, String text) {
    if (text == null) return;
    if (Looper.myLooper() != Looper.getMainLooper()) {
      post(() -> insertTextAt(line, col, text));
      return;
    }
    cursor.setCursorPosition(line, col);
    editOperators.insertTextAtCursor(text);
  }

  

  public String getTextSnapshot() {
    return fileIO.getTextSnapshot();
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

    codeFold.foldMarkerTextScale = 1f;
    codeFold.foldMarkerPaint.setTextSize(sizePx);
    indentGuides.updateStrokeWidth();
    bracketGuides.updateStrokeWidth();
    bracketMatchManager.updateStrokeWidth();
  }

  public void applyTypeface(@Nullable Typeface typeface, int style) {
    textRender.applyTypeface(typeface, style);
  }

  public void applyTextSizePx(float sizePx) {
    textRender.applyTextSizePx(sizePx);
  }

  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    textRender.applyTextSizePx(sizePx, deferWrapRebuild);
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
    long now = android.os.SystemClock.uptimeMillis();
    if (v != lastHighlightEnsureEditVersion
        && (now - lastTypingMs) < HIGHLIGHT_TYPING_WINDOW_MS) {
      int line = Math.max(0, cursor.cursorLine);
      startLine = line;
      endLine = line;
    }
    if (v != lastHighlightEnsureEditVersion
        && (now - lastHighlightInvalidateMs) < HIGHLIGHT_ENSURE_THROTTLE_MS) {
      return;
    }
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
    long now = android.os.SystemClock.uptimeMillis();
    if ((now - lastHighlightInvalidateMs) < HIGHLIGHT_INVALIDATE_THROTTLE_MS) {
      return;
    }
    lastHighlightEnsureStartLine = -1;
    lastHighlightEnsureEndLine = -1;
    lastHighlightEnsureEditVersion = -1;
    lastHighlightInvalidateMs = now;
    if (DEBUG_RENDER_LOGS) {
      android.util.Log.d("SodiumRender", "highlightEnsureInvalidate");
    }
  }

  public void markTyping() {
    lastTypingMs = android.os.SystemClock.uptimeMillis();
  }

  // --- Layout and Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float oldGutterWidth = lineNumber.lineNumbersGutterWidth;
    if (lineNumber.showLineNumbers) {
      int maxLines = 0;
      if (fileIO.isIndexReady) {
        maxLines = fileIO.lineOffsets.length;
      } else if (fileIO.isEof) {
        maxLines = textRender.windowStartLine + textRender.linesWindow.size();
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
      wordWrap.invalidateWrapMetrics(true);
      wordWrap.requestWrapPrefixRebuild();
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
    int minWindow = textRender.computeMinWindowSize();
    if (textRender.windowSize < minWindow) {
      textRender.windowSize = minWindow;
      textRender.reloadWindowAroundVisible(false);
    }
    if (wordWrap.isWordWrapEnabled && w != oldw) {
      wordWrap.invalidateWrapMetrics(true);
      wordWrap.requestWrapPrefixRebuild();
    }
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0) {
      if (event.getAction() == MotionEvent.ACTION_SCROLL) {
        float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (hScroll != 0f || vScroll != 0f) {
          // Use standard multiplier for scroll wheel
          float mult = 64f * getResources().getDisplayMetrics().density;
          // distanceX and distanceY are added to scrollX and scrollY in handleScroll.
          // For wheel, vScroll > 0 is AWAY (up), we want scrollY to decrease.
          // For wheel, hScroll > 0 is RIGHT, we want scrollX to increase.
          scroll.handleScroll(null, event, hScroll * mult, -vScroll * mult);
          return true;
        }
      }
    }
    return super.onGenericMotionEvent(event);
  }

  public float getTextStartX() {
    return textRender.isRtl ? textRender.paddingLeft : textRender.paddingLeft + lineNumber.lineNumbersGutterWidth;
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
            if (!fileIO.isIndexReady && !fileIO.isEof && fileIO.isWindowLoading) {
              float effectiveHeight =
                  (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
              float winTop = textRender.windowStartLine * textRender.lineHeight;
              float winBottom = (textRender.windowStartLine + textRender.linesWindow.size()) * textRender.lineHeight;
              float maxY = Math.max(0f, winBottom - effectiveHeight);
              if (autoScrollY > 0 && nextY > maxY) nextY = maxY;
              if (autoScrollY < 0 && nextY < winTop) nextY = winTop;
            }
             scroll.scrollY = nextY;
            scroll.clampScrollX();
            scroll.clampScrollY();
            onTouch.updateHandlePosition(lastTouchX, lastTouchY);
            fileIO.checkAndLoadWindow();
            invalidate();
            caret.mainHandler.postDelayed(this, 16);
          }
        }
      };

  public boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
    return codeFold.isFoldPlaceholderHit(globalLine, line, localX);
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
      spans = highlite.calculateSpansForLine(line, globalLine);
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
      spans = highlite.calculateSpansForLine(line, globalLine);
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
    return codeFold.isIndentFoldCandidate(line);
  }

  public void clearFoldRipple() {
    codeFold.clearFoldRipple();
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
    if (getLogicalLineLength(globalLine, line) > textRender.maxSyntaxLineLength) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    TextRender.HighlightLineState startState = highlite.getLineStateAtStart(globalLine);
    TextRender.LineParseResult parseResult =
        highlite.parseLineForSyntax(
            line,
            startState.inBlockComment,
            startState.stringState,
            textRender.whitespaceStringRule,
            textRender.whitespaceCommentRule,
            true);

    if (globalLine >= textRender.windowStartLine && globalLine < textRender.windowStartLine + textRender.linesWindow.size()) {
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
      spans = highlite.calculateSpansForLine(line, globalLine);
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

  // String state constants - deprecated, use Highlite constants
  @Deprecated public static final int STRING_STATE_DOUBLE = Highlite.STRING_STATE_DOUBLE;
  @Deprecated public static final int STRING_STATE_SINGLE = Highlite.STRING_STATE_SINGLE;
  @Deprecated public static final int STRING_STATE_BACKTICK = Highlite.STRING_STATE_BACKTICK;
  @Deprecated public static final int STRING_STATE_TRIPLE = Highlite.STRING_STATE_TRIPLE;

  public int getStringStateForDelimiter(char delimiter) {
    return highlite.getStringStateForDelimiter(delimiter);
  }
public static class StringEndResult {
    public final boolean found;
    public final int endIndex;

   public StringEndResult(boolean found, int endIndex) {
      this.found = found;
      this.endIndex = endIndex;
    }
  }
  public StringEndResult findStringEndForState(String line, int start, int state) {
    return highlite.findStringEndForState(line, start, state);
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
      spans = highlite.calculateSpansForLine(line, globalLine);
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
    public boolean hit;
    public boolean isWhitespace;
    public float x;
    public float hitCharEndX;
    public int pos;
    public boolean prevWhitespace;
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
    if (logicalLen > textRender.maxSyntaxLineLength) {
      float avg = textRender.getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return measureTextWithVisualSpaces(line, 0, safeLen,textRender.paint);
    }

    List<TextRender.HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlite.calculateSpansForLine(line, globalLine);
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
    public final int line;
    public final int start;
    public final int end;

    public StreamedSliceRequest(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  

  public void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
    if (wordWrap.isWordWrapEnabled) return;
    if (!fileIO.isIndexReady || fileIO.sourceFile == null || !fileIO.sourceFile.exists()) return;
    if (fileIO.isWindowLoading) return;

    ArrayList<StreamedSliceRequest> requests = new ArrayList<>();
    synchronized (textRender.linesWindow) {
      int winStart = textRender.windowStartLine;
      int winEnd = textRender.windowStartLine + textRender.linesWindow.size() - 1;
      int start = Math.max(firstVisibleLine, winStart);
      int end = Math.min(lastVisibleLine, winEnd);
      if (start > end) return;
      for (int line = start; line <= end; line++) {
        if (textRender.modifiedLines.containsKey(line)) continue;
        int len = getStreamedLineLength(line);
        if (len <= 0) continue;
        String slice = textRender.linesWindow.get(line - winStart);
        int sliceStart = getStreamedLineSliceStart(line);
        int sliceEnd = sliceStart + ((slice == null) ? 0 : slice.length());
        textRender.computeStreamedSliceBounds(slice, line, len, textRender.streamedSliceTmp);
        int desiredStart = textRender.streamedSliceTmp[0];
        int desiredEnd = textRender.streamedSliceTmp[1];
        if (sliceStart <= desiredStart && sliceEnd >= desiredEnd) continue;
        requests.add(new StreamedSliceRequest(line, desiredStart, desiredEnd));
      }
    }

    if (requests.isEmpty()) return;
    logRender(
        "streamed-slice-req",
        "streamedSliceRequests count=" + requests.size()
            + " first=" + firstVisibleLine
            + " last=" + lastVisibleLine,
        500);
    if (textRender.streamedLinesLockSliceUpdatePending) return;
    textRender.streamedLinesLockSliceUpdatePending = true;
    final int token = ++textRender.streamedLinesLockSliceUpdateToken;
    final int taskVersion = fileIO.ioTaskVersion.get();

    fileIO.ioHandler.post(
        () -> {
          if (token != textRender.streamedLinesLockSliceUpdateToken) return;
          if (taskVersion != fileIO.ioTaskVersion.get()) return;
          if (fileIO.sourceFile == null || !fileIO.sourceFile.exists()) {
            post(() -> textRender.streamedLinesLockSliceUpdatePending = false);
            return;
          }
          LinkedHashMap<Integer, String> results = new LinkedHashMap<>();
          SparseIntArray starts = new SparseIntArray();
          try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
            long fileLen = raf.length();
            for (StreamedSliceRequest req : requests) {
              long lineStart;
              synchronized (fileIO.lineOffsetsLock) {
                if (req.line < 0 || req.line >= fileIO.lineOffsets.length) continue;
                lineStart = fileIO.lineOffsets[req.line];
              }
              if (isSingleByteCharset()) {
                long lineByteLen = fileIO.getLineByteLengthFromIndex(raf, req.line, fileLen);
                String slice =
                    fileIO.readLineSliceAtByte(raf, lineStart, lineByteLen, req.start, req.end);
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
                if (token != textRender.streamedLinesLockSliceUpdateToken) {
                  textRender.streamedLinesLockSliceUpdatePending = false;
                  return;
                }
                if (taskVersion != fileIO.ioTaskVersion.get()) {
                  textRender.streamedLinesLockSliceUpdatePending = false;
                  return;
                }
                synchronized (textRender.linesWindow) {
                  int winStart = textRender.windowStartLine;
                  int winEnd = textRender.windowStartLine + textRender.linesWindow.size() - 1;
                  for (Map.Entry<Integer, String> e : results.entrySet()) {
                    int line = e.getKey();
                    if (line < winStart || line > winEnd) continue;
                    if (textRender.modifiedLines.containsKey(line)) continue;
                    int local = line - winStart;
                    if (local < 0 || local >= textRender.linesWindow.size()) continue;
                    textRender.linesWindow.set(local, (e.getValue() == null) ? "" : e.getValue());
                    int len = getStreamedLineLength(line);
                    if (len > 0) {
                      setStreamedLineInfo(line, len, starts.get(line));
                    }
                  }
                }
                textRender.streamedLinesLockSliceUpdatePending = false;
                logRender(
                    "streamed-slice-apply",
                    "streamedSliceApplied count=" + results.size(),
                    500);
                invalidate();
              });
        });
  }

  public void maybeKickWindowLoad(int firstVisibleLine) {
    if (zoom.isZoomGestureActive()) return;
    if (fileIO.sourceFile == null || fileIO.isFileCleared) {
      // in-memory only: no need
      return;
    }
    if (fileIO.isWindowLoading) return;

    if (getWidth() == 0 || getHeight() == 0) return;
    int firstVisibleIndex = Math.max(0, (int) (scroll.scrollY / textRender.lineHeight));
    int lastVisibleIndex =
        firstVisibleIndex + (int) Math.ceil(getHeight() / textRender.lineHeight);
    int firstVisibleGlobal;
    int lastVisibleGlobal;
    if (wordWrap.isWordWrapEnabled) {
      firstVisibleGlobal = wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleGlobal = wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
    } else {
      firstVisibleGlobal = codeFold.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleGlobal = codeFold.mapVisibleIndexToGlobal(lastVisibleIndex);
    }
    firstVisibleGlobal = Math.max(0, firstVisibleGlobal);
    lastVisibleGlobal = Math.max(firstVisibleGlobal, lastVisibleGlobal);

    int winStart = textRender.windowStartLine;
    int winEnd = winStart + textRender.linesWindow.size() - 1;
    int buffer = Math.max(0, textRender.prefetchLines / 2);
    boolean outside = firstVisibleGlobal < winStart || firstVisibleGlobal > winEnd;
    boolean nearTop = winStart > 0 && firstVisibleGlobal < winStart + buffer;
    boolean nearBottom = !fileIO.isEof && lastVisibleGlobal > winEnd - buffer;
    if (outside || nearTop || nearBottom) {
      int targetStart = Math.max(0, firstVisibleGlobal - textRender.prefetchLines);
      fileIO.loadWindowAround(targetStart, null, false);
    }
  }

  public void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
    // Save paint state
    Paint.Style prevStyle = paint.getStyle();
    int prevColor = paint.getColor();
    float prevStroke = paint.getStrokeWidth();
    Paint.Cap prevCap = paint.getStrokeCap();

    paint.setStyle(Paint.Style.FILL);
    textRender.teardropPath.reset();
    textRender.teardropPath.addOval(
        cx - selectionHandles.handleRadius, cy, cx + selectionHandles.handleRadius, cy + selectionHandles.handleRadius * 2, Path.Direction.CW);
    canvas.drawPath(textRender.teardropPath, paint);

    // Restore paint state
    paint.setStyle(prevStyle);
    paint.setColor(prevColor);
    paint.setStrokeWidth(prevStroke);
    paint.setStrokeCap(prevCap);
  }

  public boolean shouldHideCopyCutForSelection() {
    return selection.shouldHideCopyCutForSelection();
  }

  public void pasteFromClipboard() {
    selection.pasteFromClipboard();
  }

  






  
  public void setReadOnly(boolean readOnly) {
    if (this.isReadOnly == readOnly) return;
    this.isReadOnly = readOnly;
    if (readOnly) {
      autoCompletion.clearActiveSuggestion();
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

  public void setSelectionAnimationEnabled(boolean enabled) {
    selection.setSelectionAnimationEnabled(enabled);
    selectionHandles.setHandleMoveAnimationEnabled(enabled);
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

    if (fileIO.sourceFile == null || fileIO.isFileCleared) {
      // In-memory mode: the "document" is exactly what we have in memory.
      synchronized (textRender.linesWindow) {
        knownTotal = Math.max(1, textRender.windowStartLine + textRender.linesWindow.size());
      }
    } else if (fileIO.isIndexReady) {
      synchronized (fileIO.lineOffsetsLock) {
        knownTotal = Math.max(1, fileIO.lineOffsets.length);
      }
    } else if (fileIO.isEof) {
      synchronized (textRender.linesWindow) {
        knownTotal = Math.max(1, textRender.windowStartLine + textRender.linesWindow.size());
      }
    }

    if (knownTotal != null) {
      int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
      proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      // Index not ready and not at EOF: count lines once to clamp the target line.
      fileIO.countTotalLines(
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
    if (fileIO.isWindowLoading
        && fileIO.sourceFile != null
        && !(targetLine >= textRender.windowStartLine && targetLine < textRender.windowStartLine + textRender.linesWindow.size())) {
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

          if (cursor.cursorLine >= textRender.windowStartLine
              && cursor.cursorLine < textRender.windowStartLine + textRender.linesWindow.size()) {
            String lineText = getLineTextForRender(cursor.cursorLine);
            cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (fileIO.isEof) {
            int lastLineInDoc = textRender.windowStartLine + textRender.linesWindow.size() - 1;
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

    // In-memory mode (fileIO.sourceFile == null): no window loads.
    if (fileIO.isFileCleared
        || fileIO.sourceFile == null
        || (targetLine >= textRender.windowStartLine && targetLine < textRender.windowStartLine + textRender.linesWindow.size())) {
      completionAction.run();
    } else {
      int targetStart = Math.max(0, targetLine - textRender.prefetchLines);
      fileIO.loadWindowAround(targetStart, completionAction, false);
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

  

  

  


  public void applyMultiLineReplaceInWindowNow(
      int sL, int sC, int eL, int eC, String insertText, EditOperators.CursorTarget target) {
    synchronized (textRender.linesWindow) {
      int oldLineCount = getLinesCount();
      int sLocal = sL - textRender.windowStartLine;
      int eLocal = eL - textRender.windowStartLine;
      if (sLocal < 0 || eLocal < 0 || sLocal >= textRender.linesWindow.size() || eLocal >= textRender.linesWindow.size())
        return;
      if (sLocal > eLocal) {
        int t = sLocal;
        sLocal = eLocal;
        eLocal = t;
      }

      String startLine = textRender.linesWindow.get(sLocal);
      String endLine = textRender.linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String mergedText = left + (insertText == null ? "" : insertText) + right;
      String[] parts = mergedText.split("\n", -1);

      textRender.linesWindow.set(sLocal, parts[0]);
      if (eLocal >= sLocal + 1) {
        textRender.linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      if (parts.length > 1) {
        List<String> toInsert = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) toInsert.add(parts[i]);
        textRender.linesWindow.addAll(sLocal + 1, toInsert);
      }

      cursor.cursorLine = Math.max(0, target.line);
      cursor.cursorChar = Math.max(0, target.ch);

      int newLineCount = getLinesCount();
      if (lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      wordWrap.onLineCountChanged();

      recalculateMaxLineWidth();
    }
  }


 
  public void applyMultiLineDeleteInWindowNow(int sL, int sC, int eL, int eC) {
    synchronized (textRender.linesWindow) {
      int oldLineCount = getLinesCount();
      int sLocal = sL - textRender.windowStartLine;
      int eLocal = eL - textRender.windowStartLine;
      if (sLocal < 0 || eLocal >= textRender.linesWindow.size() || sLocal > eLocal) return;

      String startLine = textRender.linesWindow.get(sLocal);
      String endLine = textRender.linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String merged = left + right;

      textRender.linesWindow.set(sLocal, merged);
      if (eLocal > sLocal) {
        textRender.linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      textRender.modifiedLines.put(textRender.windowStartLine + sLocal, merged);
      for (int i = sLocal + 1; i < textRender.linesWindow.size(); i++) {
        textRender.modifiedLines.put(textRender.windowStartLine + i, textRender.linesWindow.get(i));
      }

      cursor.cursorLine = sL;
      cursor.cursorChar = left.length();

      recalculateMaxLineWidth();
      int newLineCount = getLinesCount();
      if (oldLineCount != newLineCount) {
        wordWrap.onLineCountChanged();
      }
    }
  }

  
  public static final class RangeBytes {
    public final long startByte, endByte;

    public RangeBytes(long s, long e) {
      startByte = s;
      endByte = e;
    }
  }

  



  public static final class StreamedCharSlice {
    public final String text;
    public final int length;

    public StreamedCharSlice(String text, int length) {
      this.text = text;
      this.length = length;
    }
  }

  public StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
      throws Exception {
    return binaryRender.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength, fileIO.fileCharset);
  }

  public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    if (lineText == null) return 0L;
    int safe = Math.max(0, Math.min(charIndex, lineText.length()));
    if (safe == 0) return 0L;
    return lineText.substring(0, safe).getBytes(fileIO.fileCharset).length;
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
    if (len > textRender.maxSyntaxLineLength) {
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

  public void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < textRender.linesWindow.size()) {
      textRender.linesWindow.set(localIdx, text);
      wordWrap.onLineContentChanged(textRender.windowStartLine + localIdx, text);
      clearStreamedLineInfo(textRender.windowStartLine + localIdx);
    }
  }

  public String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= textRender.linesWindow.size()) return null;
    return textRender.linesWindow.get(localIdx);
  }

  public int getStreamLineThreshold() {
    return Math.max(4096, textRender.maxSyntaxLineLength);
  }

  public boolean shouldStreamLineLength(int length) {
    if (wordWrap.isWordWrapEnabled) return false;
    if (binaryRender.isBinarySafeRenderingEnabled()) {
      return length > Math.max(256, textRender.getInitialStreamedSliceSize());
    }
    if (!fileIO.isIndexReady) return false;
    return length > getStreamLineThreshold();
  }

  public int getStreamedLineLength(int globalLine) {
    synchronized (textRender.streamedLinesLockLinesLock) {
      int v = textRender.streamedLinesLockLineLengths.get(globalLine, -1);
      if (v >= 0) return v;
    }
    synchronized (textRender.streamedLinesLock) {
      return textRender.streamedLineLengths.get(globalLine, -1);
    }
  }

  public int getStreamedLineSliceStart(int globalLine) {
    synchronized (textRender.streamedLinesLockLinesLock) {
      int v = textRender.streamedLinesLockLineSliceStarts.get(globalLine, 0);
      if (v != 0) return v;
    }
    synchronized (textRender.streamedLinesLock) {
      return textRender.streamedLineSliceStarts.get(globalLine, 0);
    }
  }

  public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    synchronized (textRender.streamedLinesLockLinesLock) {
      textRender.streamedLinesLockLineLengths.put(globalLine, length);
      textRender.streamedLinesLockLineSliceStarts.put(globalLine, sliceStart);
    }
  }

  public void clearStreamedLineInfo(int globalLine) {
    synchronized (textRender.streamedLinesLockLinesLock) {
      textRender.streamedLinesLockLineLengths.delete(globalLine);
      textRender.streamedLinesLockLineSliceStarts.delete(globalLine);
    }
  }

  public void clearStreamedLineCaches() {
    synchronized (textRender.streamedLinesLockLinesLock) {
      textRender.streamedLinesLockLineLengths.clear();
      textRender.streamedLinesLockLineSliceStarts.clear();
    }
    synchronized (textRender.streamedLinesLock) {
      textRender.streamedLineLengths.clear();
      textRender.streamedLineSliceStarts.clear();
    }
    textRender.streamedLinesLockSliceUpdatePending = false;
    textRender.streamedLinesLockSliceUpdateToken++;
  }

  public boolean isSingleByteCharset() {
    try {
      if (binaryRender.isBinarySafeRenderingEnabled()) return true;
      return fileIO.fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
    } catch (Exception ignored) {
      return true;
    }
  }

  public int getLogicalLineLength(int globalLine, @Nullable String line) {
    String mod = textRender.modifiedLines.get(globalLine);
    if (mod != null) return mod.length();
    int len = (line == null) ? 0 : line.length();
    int longLen = getStreamedLineLength(globalLine);
    return (longLen > len) ? longLen : len;
  }

  public void computeWidthForLine(int globalIndex, String line) {
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
    if (logicalLen > textRender.maxSyntaxLineLength) {
      w = textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(),textRender.paint);
    }
    synchronized (textRender.lineWidthCache) {
      textRender.lineWidthCache.put(globalIndex, w);
    }
  }

  public float getWidthForLine(int globalIndex, String line) {
    synchronized (textRender.lineWidthCache) {
      Float v = textRender.lineWidthCache.get(globalIndex);
      if (v != null) return v;
    }
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
    if (logicalLen > textRender.maxSyntaxLineLength) {
      w = textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = measureTextWithVisualSpaces(safe, 0, safe.length(),textRender.paint);
    }
    synchronized (textRender.lineWidthCache) {
      textRender.lineWidthCache.put(globalIndex, w);
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

  public void logRender(String key, String msg, long intervalMs) {
    if (!DEBUG_RENDER_LOGS) return;
    long now = SystemClock.uptimeMillis();
    Long last = renderLogLast.get(key);
    if (last != null && intervalMs > 0 && now - last < intervalMs) return;
    renderLogLast.put(key, now);
    Log.d("SodiumRender", msg);
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return !isDisabled && !isReadOnly;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return ime.onCreateInputConnection(outAttrs);
  }

  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (onKeyDown.onKeyDown(keyCode, event)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return onTouch.onTouchEvent(event);
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    autoCompletion.clearActiveSuggestion(); // Clear suggestion on focus change
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
    cursor.invalidateCursorArea();
  }

  public boolean isHeavyDrawSuppressed() {
    return false;
  }

  public int getLinesCount() {
    if (fileIO.isFileCleared) {
      return Math.max(1, textRender.windowStartLine + textRender.linesWindow.size());
    }
    int windowCount = textRender.windowStartLine + textRender.linesWindow.size();
    if (fileIO.isIndexReady && fileIO.lineOffsets.length > 0) {
      boolean hasEdits;
      synchronized (textRender.modifiedLines) {
        hasEdits = !textRender.modifiedLines.isEmpty();
      }
      if (!hasEdits && editOperators.lineCountDelta == 0) {
        return fileIO.lineOffsets.length;
      }
      int count = fileIO.lineOffsets.length + editOperators.lineCountDelta;
      if (count < 1) count = 1;
      return Math.max(count, windowCount);
    }
    if (fileIO.isEof) return textRender.windowStartLine + textRender.linesWindow.size();
    if (!textRender.linesWindow.isEmpty()) return textRender.windowStartLine + textRender.linesWindow.size();
    return -1;
  }

  public int getVisualIndexForLineAndChar(int line, int ch) {
    return wordWrap.getVisualIndexForLineAndChar(line, ch);
  }

  public int getGlobalLineForY(float y) {
    return wordWrap.getGlobalLineForY(y);
  }

  public void recalculateMaxLineWidth() {
    textRender.recalculateMaxLineWidth();
  }

  public int clampSegmentEndForWrapIndicator(
      String line, int segStart, int segEnd, int wrapWidthPx) {
    return wordWrap.clampSegmentEndForWrapIndicator(line, segStart, segEnd, wrapWidthPx);
  }

  public float getBottomBarrierPadding() {
    return scroll.getBottomBarrierPadding();
  }

  public float getKeyboardBarrierPadding() {
    return scroll.getKeyboardBarrierPadding();
  }

  public void keepCursorVisibleHorizontally() {
    scroll.keepCursorVisibleHorizontally();
  }

  public float getEffectiveScrollX() {
    return scroll.getEffectiveScrollX();
  }

  public float viewToTextX(float viewX) {
    return scroll.viewToTextX(viewX);
  }

  
  
  public String getLineTextForRenderWithDirect(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return textRender.getLineTextForRenderWithDirect(line, direct);
  }

  public String getLineTextForRender(int line) {
    return textRender.getLineTextForRender(line);
  }

  
  

  
  public void release() {
    fileIO.cancelAndCloseReader();
    if (charAnimation.charAnimAnimator != null) charAnimation.charAnimAnimator.cancel();
    if (charAnimation.delAnimAnimator != null) charAnimation.delAnimAnimator.cancel();
    removeCallbacks(cursorAnimation.cursorAnimStep);
    fileIO.ioThread.quitSafely();
  }

  // Helper method for OnTouch to call super.onTouchEvent()
  public boolean callSuperOnTouchEvent(android.view.MotionEvent event) {
    return super.onTouchEvent(event);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    textRender.drawEditorBackground(canvas);
    if (scroll.stretch.stretchOverscrollEnabled && (scroll.stretch.stretchX != 0f || scroll.stretch.stretchY != 0f)) {
      float sx = 1f + (scroll.stretch.stretchX * 0.18f * scroll.stretch.stretchOverscrollStrength);
      float sy = 1f + (scroll.stretch.stretchY * 0.18f * scroll.stretch.stretchOverscrollStrength);
      float pivotX = (scroll.stretch.stretchDirX < 0) ? 0f : (scroll.stretch.stretchDirX > 0 ? getWidth() : getWidth() * 0.5f);
      float pivotY = (scroll.stretch.stretchDirY < 0) ? 0f : (scroll.stretch.stretchDirY > 0 ? getHeight() : getHeight() * 0.5f);
      canvas.save();
      canvas.scale(sx, sy, pivotX, pivotY);
      viewRenderer.drawContent(canvas);
      canvas.restore();
    } else {
      viewRenderer.drawContent(canvas);
    }
    scroll.drawStretch(canvas);
    scroll.drawEdge(canvas);
    scroll.drawScrollBar(canvas);
  }
}
