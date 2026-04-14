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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher; // Added for Matcher
import java.util.regex.Pattern; // Added for Pattern
import android.widget.Scroller;
import com.yn.sodiumeditor.input.events.OnScroll;
import com.yn.sodiumeditor.input.events.OnTouch;
import com.yn.sodiumeditor.input.events.OnKeyDown;
import com.yn.sodiumeditor.core.*; 
import com.yn.sodiumeditor.renderer.animation.*;
import com.yn.sodiumeditor.io.*;
import com.yn.sodiumeditor.renderer.*;
import com.yn.sodiumeditor.input.Ime;

public class SodiumEditor extends View {

  public static final boolean DEBUG_RENDER_LOGS = true;

  private final java.util.HashMap<String, Long> renderLogLast = new java.util.HashMap<>();

  public static final int STYLE_NORMAL = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_BOLD_ITALIC = 3;

  public final int[] tmpLocationInWindow = new int[2];
  
  public final FileIO fileIO;
  public final Scroll scroll;
  public ScaleGestureDetector scaleGestureDetector;
  public final Zoom zoom;
  public final Ime ime;
  public final OnTouch onTouch;
  public final OnScroll onScroll;
  public final OnKeyDown onKeyDown;
  public final ColorCodeHighlight colorCodeHighlight;
  public final BracketGuides bracketGuides;
  public final BracketMatchManager bracketMatchManager;
  public final WhitespaceGuides whitespaceGuides;
  public final UrlUnderline urlUnderline;
  public final PathUnderline pathUnderline;
  public final IndentGuides indentGuides;
  public final AutoBracketPair autoBracketPair;
  public final AutoBracketNewline autoBracketNewline;
  public final Search search;
  public final BinaryRender binaryRender;
  public final Popup popup;
  public final TextRender textRender;
  public final HighliteRender highliteRender;
  public final Highlite highlite;
  public final AutoCompletion autoCompletion;
  public final AutoPathCompletion autoPathCompletion;
  public final ErrorUnderline errorUnderline;
  public final CursorAnimation cursorAnimation;
  public final CharAnimation charAnimation;
  public final LineNumber lineNumber;
  public final LoadingCircle loadingCircle;
  public final com.yn.sodiumeditor.core.TextRange textRange;
  public final com.yn.sodiumeditor.renderer.draw.TextLineDraw textLineDraw;
  public final HighlightRules highlightRules;
  public final com.yn.sodiumeditor.core.View view;
  
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

  // Code fold renderer
  public final CodeFoldRender codeFoldRender;

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

  // Loading circle variables

  // Edit operators manager
  public final EditOperators editOperators;

  // View renderer for handling all drawing operations
  public final ViewRender viewRender;

  

  // edit version (to ignore old rewrite results) - delegated to editOperators
  
  // Large edit UI (brief busy indicator)
  
  // Direct read cache for fast fling rendering when window hasn't loaded yet (index-based)
  
  
  
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

    // Initialize BinaryRender early so TextRender can use it safely.
    binaryRender = new BinaryRender(this);
    textRender = new TextRender(this);
    textRange = new com.yn.sodiumeditor.core.TextRange(this);
    textLineDraw = new com.yn.sodiumeditor.renderer.draw.TextLineDraw(this);
    highliteRender = new HighliteRender(this);
    lineNumber = new LineNumber(this);
    currentLineHighlight = new CurrentLineHighlight(this);
    codeFold = new CodeFold(this);
    codeFoldRender = new CodeFoldRender(this);
    clickAfterEndToAddLine = new ClickAfterEndToAddLine(this);
    highlite = new Highlite(this);
    highlightRules = new HighlightRules(this, highlite);
    view = new com.yn.sodiumeditor.core.View(this);
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

    // Initialize Popup
    popup = new Popup(this);
    autoCompletion = new AutoCompletion(this);
    autoPathCompletion = new AutoPathCompletion(this);
    loadingCircle = new LoadingCircle(this);

    // Initialize EditOperators
    editOperators = new EditOperators(this);

    // Initialize ViewRender
    viewRender = new ViewRender(this);

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
    codeFold.animation.foldMarkerTextScale = 1f;
    codeFold.animation.foldMarkerPaint.setTextSize(textRender.paint.getTextSize());
    textRender.baseTypeface = (textRender.paint.getTypeface() != null) ? textRender.paint.getTypeface() : Typeface.MONOSPACE;
    textRender.lineHeight = textRender.paint.getFontSpacing();
    lineNumber.lineNumbersPaint.setTextSize(36);
    selectionHandles.baseHandleTextSizePx = textRender.paint.getTextSize();
    cursor.baseCursorTextSizePx = textRender.paint.getTextSize();
    highlightRules.whitespaceStringRule =
        new HighliteRender.HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            HighliteRender.HighlightRuleType.STRING);
    highlightRules.whitespaceCommentRule =
        new HighliteRender.HighlightRule(
            "",
            STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            HighliteRender.HighlightRuleType.BLOCK_COMMENT);

    selection.selectionPaint.setStyle(Paint.Style.FILL);
    caret.caretPaint.setStyle(Paint.Style.STROKE);
    caret.caretPaint.setStrokeCap(Paint.Cap.BUTT);
    selectionHandles.handlePaint.setStyle(Paint.Style.FILL);
    loadingCircle.loadingCirclePaint.setStyle(Paint.Style.STROKE);
    loadingCircle.loadingCirclePaint.setStrokeCap(Paint.Cap.ROUND);



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
                
                post(() -> scroll.keepCursorVisibleHorizontally());
                
              }
            });

    // Initialize suggestion paint
    autoCompletion.suggestionPaint.set(textRender.paint);
    autoCompletion.suggestionPaint.setColor(0xFFAAAAAA); // Default faint gray
    autoCompletion.suggestionPaint.setAntiAlias(true);
    autoCompletion.suggestionPaint.setSubpixelText(true);
    autoCompletion.suggestionPaint.setHinting(Paint.HINTING_ON);
    autoCompletion.isSuggestionTextSizeCustom = false; // By default, suggestion size follows main text
  }

  // --- Public APIs for Auto Completion ---

  
  

  


  

  public int heavyFeaturesThreshold = 50000;

  

  public int getWindowEndLine() {
    synchronized (textRender.linesWindow) {
      return Math.max(0, textRender.windowStartLine + textRender.linesWindow.size() - 1);
    }
  }


  public float getViewXForLineChar(String line, int globalLine, int ch) {
    if (line == null) line = "";
    int safeChar = Math.max(0, Math.min(ch, getLogicalLineLength(globalLine, line)));
    if (!wordWrap.isWordWrapEnabled) {
      return getTextStartX() + measureText(line, safeChar, globalLine) - scroll.getEffectiveScrollX();
    }
    int[] starts = wordWrap.getWrapStartsForLine(globalLine, line);
    int seg = wordWrap.getWrapSegmentIndexForChar(starts, safeChar);
    int segStart = wordWrap.getWrapSegmentStart(starts, seg);
    float x = measureTextWithVisualSpaces(line, segStart, safeChar,textRender.paint);
    return getTextStartX() + x - scroll.getEffectiveScrollX();
  }

  public float getViewYTopForLineChar(int globalLine, int ch) {
    int v = wordWrap.getVisualIndexForLineAndChar(globalLine, ch);
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
        codeFold.animation.foldMarkerGutterWidth =
            codeFold.animation.foldMarkerPaint.measureText("v") + codeFold.animation.foldMarkerSpacing + codeFold.animation.foldMarkerEdgePadding;
      } else {
        codeFold.animation.foldMarkerGutterWidth = 0f;
      }
      lineNumber.lineNumbersGutterWidth = baseWidth + codeFold.animation.foldMarkerGutterWidth + lineNumber.gutterSeparatorWidth;
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
    float w = highlite.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!textRender.isRtl || line == null) return 0f;
    float w = highlite.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = getTextAreaWidth();
    return area - w;
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

  
  
  
  

  
  public int getVisualSpaceScale() {
    return 1;
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
      String prev = textRender.getLineTextForRender(l);
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

  

  public boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    // Fast hit-test using per-char advances (avoids O(n^2) measureText calls),
    // but respects syntax styles (bold/italic) so guide X aligns with text width.
    List<HighliteRender.HighlightSpan> spans = highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlite.calculateSpansForLine(line, globalLine);
      highlite.highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    boolean prevWhitespace = false;
    final float eps = 0.25f; // boundary tolerance (px)

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighliteRender.HighlightSpan span : spans) {
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

  public float measureText(String line, int length, int globalLine) {
    int logicalLen = getLogicalLineLength(globalLine, line);
    int safeLen = Math.max(0, Math.min(length, logicalLen));

    if (binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = binaryRender.getBinaryTokenSpans(globalLine);
      float padX = binaryRender.binaryCaretNotationEnabled ? 0f : binaryRender.binaryTokenPaddingX;
      
      if (spans != null && spans.length > 0) {
        // FAST PATH: Use cached spans but with simplified calculation
        return binaryRender.getXForCharBinary(line, safeLen, textRender.paint, spans, padX);
      } else {
        // SUPER FAST PATH: Estimate using average width to keep scrolling smooth
        // The shift bug happens when this estimate differs from actual draw width.
        // We use a stable average that includes typical padding density.
        float baseWidth = textRender.paint.measureText("M");
        // In binary mode, tokens are frequent. We assume a 20% token density for the estimate.
        float effectiveAvgWidth = baseWidth + (padX * 2f * 0.2f); 
        return safeLen * effectiveAvgWidth;
      }
    }

    if (logicalLen > highliteRender.maxSyntaxLineLength) {
      float avg = textRender.getAverageCharWidthForLine(line, globalLine);
      return avg * safeLen;
    }
    if (highlightRules.isEmpty() || line.isEmpty() || safeLen == 0) {
      return measureTextWithVisualSpaces(line, 0, safeLen,textRender.paint);
    }

    List<HighliteRender.HighlightSpan> spans = highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlite.calculateSpansForLine(line, globalLine);
      highlite.highlightCache.put(globalLine, spans);
    }

    if (spans.isEmpty()) {
      return measureTextWithVisualSpaces(line, 0, safeLen,textRender.paint);
    }

    float totalWidth = 0;
    int lastEnd = 0;

    for (HighliteRender.HighlightSpan span : spans) {
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

  
public StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
      throws Exception {
    return binaryRender.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength, fileIO.fileCharset);
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
  
  public void setSelectionAnimationEnabled(boolean enabled) {
    selection.setSelectionAnimationEnabled(enabled);
    selectionHandles.setHandleMoveAnimationEnabled(enabled);
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
    String ln = textRender.getLineTextForRender(line);
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
      int sL, int sC, int eL, int eC, String insertText, EditOp.CursorTarget target) {
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
          highlite.measureHighlightedSegmentWidth(
              text, globalLine, 0, getLogicalLineLength(globalLine, text));
      x = w - x;
    }
    if (x <= 0f) return 0;
    if (binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = binaryRender.getBinaryTokenSpans(globalLine);
      float padX = binaryRender.binaryCaretNotationEnabled ? 0f : binaryRender.binaryTokenPaddingX;
      float charWidth = textRender.paint.measureText("M");

      if (spans != null && spans.length > 0) {
        return binaryRender.getCharIndexForXBinary(
            text, 0, text.length(), x, textRender.paint, spans, padX);
      } else {
        // Match the "SUPER FAST PATH" estimation used in measureText
        float effectiveAvgWidth = charWidth + (padX * 2f * 0.2f);
        if (effectiveAvgWidth <= 0f) return 0;
        int idx = (int) Math.round(x / effectiveAvgWidth);
        return Math.max(0, Math.min(idx, text.length()));
      }
    }

    int len = getLogicalLineLength(globalLine, text);
    if (len > highliteRender.maxSyntaxLineLength) {
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
    int globalLine = textRender.windowStartLine + localIdx;
    // CRITICAL: Check modifiedLines first to get the latest edited content
    synchronized (textRender.modifiedLines) {
      String mod = textRender.modifiedLines.get(globalLine);
      if (mod != null) return mod;
    }
    return textRender.linesWindow.get(localIdx);
  }

  public int getStreamLineThreshold() {
    return Math.max(4096, highliteRender.maxSyntaxLineLength);
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
    if (logicalLen > highliteRender.maxSyntaxLineLength) {
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
    if (logicalLen > highliteRender.maxSyntaxLineLength) {
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

  

  public void recalculateMaxLineWidth() {
    // Reset and let getMaxScrollXForClamp() recalculate on demand
    textRender.currentMaxWindowLineWidth = 0f;
    textRender.globalMaxLineWidth = 0f;
    scroll.maxLineWidthForScroll = 0f;
    scroll.maxTextStartXForScroll = 0f;
    scroll.maxScrollXForScroll = 0f;
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
      viewRender.drawContent(canvas);
      canvas.restore();
    } else {
      viewRender.drawContent(canvas);
    }
    scroll.drawStretch(canvas);
    scroll.drawEdge(canvas);
    scroll.drawScrollBar(canvas);
  }
}
