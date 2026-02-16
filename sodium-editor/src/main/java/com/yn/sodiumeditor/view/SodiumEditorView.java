package com.yn.sodiumeditor.view;

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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface; // Added for Typeface
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
// For Draw logic
// For Draw logic
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher; // Added for Matcher
import com.yn.sodiumeditor.view.CursorManager.BracketPairType;

public class SodiumEditorView extends View {

  public static final int STYLE_NORMAL = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_BOLD_ITALIC = 3;

  // paint & metrics
  public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Typeface baseTypeface = Typeface.DEFAULT;
  public float lineHeight;
  public float paddingLeft = 10f; // Made non-final for line numbers

  // --- Line Number State ---
  public boolean isRtl = false;
  final Rect textBounds = new Rect();
  private final int[] tmpLocationInWindow = new int[2];

  // visual padding constants
  static final float BOTTOM_SCROLL_OFFSET = 100f; // Visual padding below last line
  private static final float MIN_BOTTOM_VISIBLE_SPACE =
      50f; // Minimum space to show below last line

  // sliding window
  final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 30; // 2000 yyy
  public int prefetchLines = 10; // 1000 yyy

  // IO
  private final HandlerThread ioThread;
  final Handler ioHandler;
  public final FileManager fileManager;
  public volatile boolean isEof = false;
  public final AtomicInteger ioTaskVersion = new AtomicInteger(0);
  public File sourceFile = null;
  public boolean isFileCleared = false;
  public BufferedReader readerForFile = null;

  // caches
  final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
  public final LinkedHashMap<Integer, Float> lineWidthCache;
  public int lineWidthCacheSize = 200; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  float globalMaxLineWidth = 0f;
  
  public int prefetchCols = 512;
  public int colsWidthCacheSize = 256;
  final LinkedHashMap<Integer, Float> avgCharWidthCache =
      new LinkedHashMap<Integer, Float>(colsWidthCacheSize, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
          return size() > colsWidthCacheSize;
        }
      };
  private final Object streamedLinesLock = new Object();
  private final SparseIntArray streamedLineLengths = new SparseIntArray();
  private final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
  private boolean streamedSliceUpdatePending = false;
  private int streamedSliceUpdateToken = 0;
  private final int[] streamedSliceTmp = new int[2];
  // Charset is now managed by FileManager

  // --- Cursor Animation State (moved to CursorAnimationManager) ---
  private final InputManager inputManager;
  @Nullable ValueAnimator flingStopAnimator;
  static final long FLING_STOP_ANIM_DURATION_MS = 90;
  final IMEManager imeManager;
  public final ScrollManager scrollManager;
  public final ZoomManager zoomManager;
  public final UndoRedo undoRedo;
  public final SearchManager searchManager;
  public final CursorAnimationManager cursorAnimationManager;
  public final CharAnimationManager charAnimationManager;
  public final PopupMenuManager popupMenuManager;
  public final AutoSuggestionManager autoSuggestionManager = new AutoSuggestionManager(this);

  // --- Search State (moved to SearchManager) ---
  // --- Zoom State (moved to ZoomManager) ---
  final WordWrapManager wordWrapManager = new WordWrapManager();
  // Search logic moved to SearchManager.
  // Search logic moved to SearchManager.



  // selection
  public int lastDoubleTapLine = -1;
  public int lastDoubleTapWordStart = -1;
  public int lastDoubleTapWordEnd = -1;
  public int lastDoubleTapStage = 0;

  // touch helpers
  boolean pointerDown = false;
  boolean movedSinceDown = false;
  private float downX = 0f, downY = 0f;
  private final int touchSlop;
  // Zoom multi-touch state moved to ZoomManager.

  // auto-scroll when dragging handles
  final Handler mainHandler = new Handler(Looper.getMainLooper());

  // keyboard awareness
  private final Rect visibleDisplayFrame = new Rect();
  int keyboardHeight = 0;

  // typed-character and deleted-character animations moved to CharAnimationManager.
  boolean suppressNextCommitText = false;
  @Nullable String lastImeCommitText;
  long lastImeCommitUptime = 0L;

  // caret movement animation moved to CursorAnimationManager.

  // popup menu moved to PopupMenuManager.

  // selection handles
  private float baseCursorTextSizePx = 0f;
  public final IndentGuideManager indentGuideManager;

  public final WhitespaceGuideManager whitespaceGuideManager = new WhitespaceGuideManager();
  
  public final HandlesManager handlesManager = new HandlesManager(this);
  public final CursorManager cursorManager = new CursorManager(this);
  public final SelectionManager selectionManager = new SelectionManager();
  public final HighlightManager highlightManager = new HighlightManager(this);
  public final LineNumberManager lineNumberManager = new LineNumberManager(this);
  final BracketGuideManager bracketGuideManager = new BracketGuideManager(this);
  public final BracketMatchManager bracketMatchManager = new BracketMatchManager(this);
  public final LoadingCircleManager loadingCircleManager;
  public final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();
  public final FoldManager foldManager = new FoldManager(this);

  // editor background
  public boolean hasEditorBackgroundColor = false;
  public int editorBackgroundColor = 0x00000000;
  @Nullable public Bitmap editorBackgroundBitmap = null;
  public final Rect editorBackgroundDst = new Rect();

  // selection drawing moved to SelectionManager

  // handle dragging edge flags moved to HandlesManager

  // Drawing base to avoid float precision issues on very large line indices.
  // During onDraw, we render everything relative to the first visible line.
  public int drawBaseLine = 0;

  private final ViewRender viewRender = new ViewRender(this);

  static final String WHITESPACE_GUIDE_SPACE = "\u00B7";
  static final String WHITESPACE_GUIDE_TAB = "\u2192";
  static final String FOLD_PLACEHOLDER_TEXT = "<—>";
  private static final String INDENT_BLOCK_UNIT = "  ";
  static final int INDENT_FOLD_SCAN_LIMIT = 2000;



  // dragging handle state moved to HandlesManager
  public volatile boolean isWindowLoading = false;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;
  private final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables
  // loading circle state moved to LoadingCircleManager
  private boolean showLoadingOnFileOpen = true;
  private boolean isInitialFileOpenLoading = false;
  private int initialFileOpenToken = 0;
  @Nullable private Runnable initialFileOpenShowSpinner;
  public final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
  private int maxWidthRecalcToken = 0;

  // index
  final Object lineOffsetsLock = new Object();
  long[] lineOffsets = new long[0];
  public volatile boolean isIndexReady = false;
  private volatile boolean isIndexBuilding = false;
  private volatile boolean isIndexDisabled = false;
  @Nullable private volatile String indexDisabledPath = null;
  private volatile long indexDisabledFileLength = -1L;
  private static final long MAX_INDEX_BYTES_HARD = 64L * 1024 * 1024;

  // edit version + undo/redo state moved to UndoRedo.

  // Large edit UI (brief busy indicator)
  private static final int LARGE_EDIT_LINES = 8000; // show spinner/disable for very large edits
  private static final int HIDE_COPY_CUT_LINES = 20000;
  private final AtomicInteger largeEditUiToken = new AtomicInteger(0);
  public final Runnable largeEditUiWatchdog =
      new Runnable() {
        @Override
        public void run() {
          // Safety: never allow spinner/disable to get stuck forever
          endLargeEditUi(false);
        }
      };

  // Direct read cache for fast fling rendering when window hasn't loaded yet (index-based)
  private final LinkedHashMap<Integer, String> directLineCache =
      new LinkedHashMap<Integer, String>(600, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
          return size() > 600;
        }
      };

  final int[] visibleCharRangeTmp = new int[2];
  public int visibleCharPadding = 2;
  private boolean isPerformanceModeEnabled = false;
  boolean isStableGlyphPositionsEnabled = false;
  private boolean isClickAfterEndToAddLineEnabled = false;
  private boolean isAutoPairingEnabled = true;
  private boolean isAutoBracketNewlineEnabled = true;
  private boolean isAutoBracketNewlineIndentEnabled = true;
  private boolean isAutoIndentAfterClosingBracketEnabled = true;
  boolean isIndentationBlocksEnabled = false;

  // Zoom scroll adjustment for word wrap


  // --- Auto-suggestion State (moved to AutoSuggestionManager) ---


  public boolean binarySafeRenderingEnabled = false;

  // --- Color Code Highlighting ---
  boolean isMultiLineStringsEnabled = false;
  boolean isBacktickStringsEnabled = false;
  boolean isBlockCommentsEnabled = false;
  boolean isTripleQuoteStringsEnabled = false;

  final Runnable delayedWindowCheck =
      new Runnable() {
        @Override
        public void run() {
          checkAndLoadWindow();
        }
      };

  public SodiumEditorView(Context ctx, @Nullable AttributeSet attrs) {
    super(ctx, attrs);
    paint.setTextSize(36);
    paint.setColor(0xFF000000);
    paint.setAntiAlias(true);
    paint.setSubpixelText(true);
    paint.setHinting(Paint.HINTING_ON);
    paint.setUnderlineText(false); // Explicitly disable underlines to fix visual artifact
    baseTypeface = (paint.getTypeface() != null) ? paint.getTypeface() : Typeface.DEFAULT;
    lineHeight = paint.getFontSpacing();
    handlesManager.initBaseHandleTextSize(paint.getTextSize());
    baseCursorTextSizePx = paint.getTextSize();
    indentGuideManager = new IndentGuideManager(this, paint);
    bracketMatchManager.setColor(handlesManager.getCursorAndHandlesColor());
    bracketMatchManager.setBaseTextSizePx(paint.getTextSize());
    bracketGuideManager.setBaseTextSizePx(paint.getTextSize());
    whitespaceGuideManager.initPaints(0xFF555555);
    updateWhitespaceGuideMetrics();
    whitespaceGuideManager.ensureRules(paint.getTextSize(), paint.getTypeface());

    selectionManager.initPaints();

    // Initialization for line numbers
    float density = getContext().getResources().getDisplayMetrics().density;
    lineNumberManager.initDefaults(paint, density);
    foldManager.foldPlaceholderCorner = 6f * density;
    foldManager.foldPlaceholderPadX = 6f * density;
    foldManager.foldPlaceholderPadY = 2f * density;
    foldManager.foldMarkerSpacing = foldManager.foldMarkerSpacing * density;
    foldManager.foldMarkerEdgePadding = foldManager.foldMarkerEdgePadding * density;

    popupMenuManager = new PopupMenuManager(this);
    loadingCircleManager = new LoadingCircleManager(this);

    foldManager.foldPlaceholderPaint.setColor(0xFFE0E0E0);
    foldManager.foldPlaceholderPaint.setStyle(Paint.Style.FILL);
    foldManager.foldMarkerPaint.setColor(0xFF888888);
    foldManager.foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    foldManager.foldMarkerPaint.setTextSize(paint.getTextSize());
    foldManager.foldRipplePaint.setStyle(Paint.Style.FILL);

    wordWrapManager.initIndicatorPaint(paint, density);

    touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
    imeManager = new IMEManager(this);
    scrollManager = new ScrollManager(this);
    zoomManager = new ZoomManager(this, ctx);
    undoRedo = new UndoRedo(this);
    searchManager = new SearchManager(this);
    cursorAnimationManager = new CursorAnimationManager(this);
    charAnimationManager = new CharAnimationManager(this);
    inputManager = new InputManager(this, ctx);

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
    fileManager = new FileManager(this);

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
                post(scrollManager::keepCursorVisibleHorizontally);
              }
            });

    autoSuggestionManager.initPaints(paint);

    highlightManager.setPathUnderliningEnabled(true); // Enable path underlining by default
  }

  // --- Public APIs for Auto Completion ---





  public void setBinarySafeRenderingEnabled(boolean enabled) {
    if (binarySafeRenderingEnabled == enabled) return;
    binarySafeRenderingEnabled = enabled;
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
    invalidateHighlightEnsureRange();
    bracketGuideManager.invalidateCache();
    if (wordWrapManager.isWordWrapEnabled) wordWrapManager.invalidateWrapMetrics(this, true);
    wordWrapManager.requestWrapPrefixRebuild(this);
    reloadWindowAroundVisible(false);
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
      highlightManager.setUrlUnderliningEnabled(false);
      highlightManager.setPathUnderliningEnabled(false);
      highlightManager.isColorHighlightingEnabled = false;
      bracketMatchManager.setBracketMatchingEnabled(this, false);
      bracketGuideManager.setBracketGuidesEnabled(this, false);
      indentGuideManager.setIndentGuidesEnabled(false);
      whitespaceGuideManager.setWhitespaceGuidesEnabled(this, false);
      wordWrapManager.setWordWrapIndicatorEnabled(this, false);
      autoSuggestionManager.setAutoCompletionEnabled(false);
      autoSuggestionManager.setAutoPathCompletionEnabled(false);
      charAnimationManager.setEnabled(false, 0);
      highlightManager.setHighlightCurrentLine(false);
      setIndentationBlocksEnabled(false);
      foldManager.setCodeFoldingEnabled(false);
    }
    invalidate();
  }









  private void insertStringAtCursor(String text) {
    cursorManager.insertTextAtCursor(text);
  }


  // --- Public APIs for Line Numbers ---

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
      Log.e("SodiumEditorView", "setEditorBackgroundImageFromAssets failed: " + assetPath, e);
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
      Log.e("SodiumEditorView", "setEditorBackgroundImageFromFile failed: " + filePath, e);
    }
  }

  public void clearEditorBackgroundImage() {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = null;
    invalidate();
  }

  private void setEditorBackgroundBitmap(Bitmap bitmap) {
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundBitmap.recycle();
    }
    editorBackgroundBitmap = bitmap;
    invalidate();
  }










































  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }



















  public void setFileCharset(@Nullable Charset charset) {
    fileManager.setFileCharset(charset);
  }

  public void setFileEncoding(@Nullable String charsetName) {
    fileManager.setFileEncoding(charsetName);
  }

  public void setMaxSyntaxLineLength(int maxChars) {
    highlightManager.setMaxSyntaxLineLength(maxChars);
  }

  public void setPrefetchCols(int cols) {
    viewRender.setPrefetchCols(cols);
  }

  public void setColsWidthCacheSize(int size) {
    viewRender.setColsWidthCacheSize(size);
  }


  public void setWindowSize(int size) {
    viewRender.setWindowSize(size);
  }

  public void setPrefetchLines(int lines) {
    viewRender.setPrefetchLines(lines);
  }

  public void setLineWidthCacheSize(int size) {
    viewRender.setLineWidthCacheSize(size);
  }

  public void setRenderWindow(int windowSize, int prefetchLines) {
    viewRender.setRenderWindow(windowSize, prefetchLines);
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
    int firstVisibleLine = Math.max(0, getGlobalLineForY(scrollManager.scrollY));
    int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
    loadWindowAround(targetStart, null, recalcWidthSync);
  }

  public void setCursorWidth(float width) {
    if (handlesManager.getBaseCursorWidthPx() == width && baseCursorTextSizePx == paint.getTextSize()) return;
    handlesManager.setBaseCursorWidthPx(width);
    this.baseCursorTextSizePx = paint.getTextSize();
    updateTextSizeDependentMetrics();
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
      foldManager.removeIndentFolds();
    }
    indentGuideManager.markIntervalsDirty();
    foldManager.markIntervalsDirty();
    invalidate();
  }






































  public void setBacktickStringsEnabled(boolean enabled) {
    if (isBacktickStringsEnabled == enabled) return;
    isBacktickStringsEnabled = enabled;
    highlightManager.clearHighlightCaches();
    invalidate();
  }













  public void setLayoutDirection(boolean isRtl) {
    if (this.isRtl == isRtl) return;
    this.isRtl = isRtl;
    lineNumberManager.setTextAlign(isRtl);
    foldManager.foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    lineNumberManager.invalidateCache();
    requestLayout();
    if (wordWrapManager.isWordWrapEnabled) wordWrapManager.invalidateWrapMetrics(this, true);
    scrollManager.maxScrollXForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.scrollX = 0f;
    scrollManager.keepCursorVisibleHorizontally();
    invalidate();
  }


  public void setPopupLabels(
      String copy, String cut, String paste, String delete, String selectAll) {
    popupMenuManager.setPopupLabels(copy, cut, paste, delete, selectAll);
  }

  public void setFontFromAssets(String assetPath, int style) {
    try {
      Typeface tf = Typeface.createFromAsset(getContext().getAssets(), assetPath);
      applyTypeface(tf, style);
    } catch (Exception e) {
      Log.e("SodiumEditorView", "setFontFromAssets failed: " + assetPath, e);
    }
  }

  public void setFontFromFile(String filePath, int style) {
    try {
      Typeface tf = Typeface.createFromFile(filePath);
      applyTypeface(tf, style);
    } catch (Exception e) {
      Log.e("SodiumEditorView", "setFontFromFile failed: " + filePath, e);
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


  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    cursorManager.setPositionNoClear(cursorLine, cursorChar);
  }

  public void showSelectionPopup() {
    if (selectionManager.hasSelection()) {
      popupMenuManager.showPopupAtSelection();
    }
  }

  // --- Convenience cursor/line accessors ---






  public void insertTextAt(int line, int col, String text) {
    if (text == null) return;
    if (Looper.myLooper() != Looper.getMainLooper()) {
      post(() -> insertTextAt(line, col, text));
      return;
    }
    cursorManager.setPosition(line, col);
    cursorManager.insertTextAtCursor(text);
  }


  public String getTextSnapshot() {
    int total = getLinesCount();
    if (total <= 0) return "";
    java.util.HashMap<Integer, String> direct = new java.util.HashMap<>();
    if (fileManager.isIndexReady() && fileManager.getSourceFile() != null && fileManager.getSourceFile().exists()) {
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

  float spToPx(float sp) {
    return sp * getResources().getDisplayMetrics().scaledDensity;
  }

  float spToPxForZoom(float sp) {
    return spToPx(sp);
  }

  private float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }

  void updateTextSizeDependentMetrics() {
    float sizePx = paint.getTextSize();
    handlesManager.setHandleRadius(
        Math.max(
            4f,
            scaleByTextSize(
                handlesManager.getBaseHandleRadiusPx(),
                handlesManager.getBaseHandleTextSizePx(),
                sizePx)));
    handlesManager.setCursorWidth(
        Math.max(1f, scaleByTextSize(handlesManager.getBaseCursorWidthPx(), baseCursorTextSizePx, sizePx)));

    bracketMatchManager.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketMatchManager.getBaseStrokeWidth(), bracketMatchManager.getBaseTextSizePx(), sizePx)));

    bracketGuideManager.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketGuideManager.getBaseStrokeWidth(), bracketGuideManager.getBaseTextSizePx(), sizePx)));
    indentGuideManager.updateForTextSize(sizePx);
  }

  private void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  private void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    paint.setTextSize(sizePx);
    autoSuggestionManager.onTextSizeChanged(sizePx);
    lineNumberManager.setTextSize(sizePx);
    foldManager.foldMarkerPaint.setTextSize(sizePx * foldManager.foldMarkerTextScale);
    wordWrapManager.updateIndicatorPaintForTextSize(sizePx, paint);
    lineHeight = paint.getFontSpacing();
    updateTextSizeDependentMetrics();
    updateWhitespaceGuideMetrics();
    lineNumberManager.invalidateCache();

    for (HighlightManager.HighlightRule rule : highlightManager.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    whitespaceGuideManager.updateRuleTextSize(sizePx);
    if (highlightManager.lineCommentHighlightRule != null) highlightManager.lineCommentHighlightRule.updateTextSize(sizePx);
    highlightManager.clearHighlightCaches();

    // Invalidate caches and approximate new max width
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    // Scale the max width instead of recalculating it synchronously.
    // This is an approximation but avoids massive lag.
    float scale = sizePx / oldSize;
    currentMaxWindowLineWidth *= scale;
    globalMaxLineWidth *= scale;
    scrollManager.maxLineWidthForScroll *= scale;
    scrollManager.maxScrollXForScroll *= scale;
    scrollManager.maxTextStartXForScroll = 0f;
    if (scale < 1f) {
      scrollManager.maxLineWidthForScroll = 0f;
      scrollManager.maxScrollXForScroll = 0f;
    }

    requestLayout(); // Still needed for gutter
    if (wordWrapManager.isWordWrapEnabled) wordWrapManager.invalidateWrapMetrics(this, true, !deferWrapRebuild);
    wordWrapManager.requestWrapPrefixRebuild(this);
    invalidate();
  }

  void applyZoomTextSizePx(float sizePx) {
    applyTextSizePx(sizePx);
  }

  void applyZoomTextSizePx(float sizePx, boolean deferWrapRebuild) {
    applyTextSizePx(sizePx, deferWrapRebuild);
  }

  float getPaintTextSizePxForZoom() {
    return paint.getTextSize();
  }

  float getPaintFontSpacingPxForZoom() {
    return paint.getFontSpacing();
  }

  int getEditVersionForSearch() {
    return undoRedo.getEditVersion();
  }

  float measureTextForSearch(String line, int ch, int globalLine) {
    return highlightManager.measureText(line, ch, globalLine);
  }

  float measureTextWithVisualSpacesForSearch(String line, int start, int end) {
    return whitespaceGuideManager.measureTextWithVisualSpaces(this, line, start, end, paint);
  }

  void ensureLineInWindowForSearch(int line, boolean immediate) {
    scrollManager.ensureLineInWindow(line, immediate);
  }

  int getWindowStartLineForSearch() {
    return windowStartLine;
  }

  int getWindowSizeForSearch() {
    return linesWindow.size();
  }

  boolean isIndexReadyForSearch() {
    return isIndexReady;
  }

  boolean getSourceFileForSearchExists() {
    return fileManager.getSourceFile() != null && fileManager.getSourceFile().exists();
  }

  void populateDirectLinesForRangeForSearch(
      int startLine, int endLine, java.util.Map<Integer, String> direct) {
    populateDirectLinesForRange(startLine, endLine, direct);
  }

  String getLineTextForRenderWithDirectForSearch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  int getWindowStartLineForBracket() {
    return windowStartLine;
  }

  int getWindowEndLineForBracket() {
    synchronized (linesWindow) {
      return windowStartLine + linesWindow.size() - 1;
    }
  }

  int getEditVersionForBracket() {
    return undoRedo.getEditVersion();
  }

  String getLineTextForRenderWithDirectForMatch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  int getEditVersionForMatch() {
    return undoRedo.getEditVersion();
  }

  boolean isBlockCommentsEnabledForMatch() {
    return isBlockCommentsEnabled;
  }

  boolean isMultiLineStringsEnabledForMatch() {
    return isMultiLineStringsEnabled;
  }

  boolean isBacktickStringsEnabledForMatch() {
    return isBacktickStringsEnabled;
  }

  boolean isTripleQuoteStringsEnabledForMatch() {
    return isTripleQuoteStringsEnabled;
  }

  int getStringStateTripleForMatch() {
    return HighlightManager.STRING_STATE_TRIPLE;
  }

  int getStringStateBacktickForMatch() {
    return HighlightManager.STRING_STATE_BACKTICK;
  }



  float getDrawLineTopForMatch(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  float getLineHeightForMatch() {
    return lineHeight;
  }

  float getPaintTextSizeForMatch() {
    return paint.getTextSize();
  }

  String getLineTextForRenderWithDirectForBracket(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  boolean isBlockCommentsEnabledForBracket() {
    return isBlockCommentsEnabled;
  }

  boolean isMultiLineStringsEnabledForBracket() {
    return isMultiLineStringsEnabled;
  }

  boolean isBacktickStringsEnabledForBracket() {
    return isBacktickStringsEnabled;
  }

  boolean isTripleQuoteStringsEnabledForBracket() {
    return isTripleQuoteStringsEnabled;
  }

  int getStringStateTripleForBracket() {
    return HighlightManager.STRING_STATE_TRIPLE;
  }

  int getStringStateBacktickForBracket() {
    return HighlightManager.STRING_STATE_BACKTICK;
  }


  boolean isWhitespaceGuidesEnabledForBracket() {
    return whitespaceGuideManager.isWhitespaceGuidesEnabled();
  }

  int getWhitespaceGuideSpaceStepForBracket() {
    return whitespaceGuideManager.getSpaceStep();
  }

  float getPaintTextSizeForBracket() {
    return paint.getTextSize();
  }

  boolean isRtlForBracket() {
    return isRtl;
  }


  boolean isHeavyDrawSuppressedForBracket() {
    return isHeavyDrawSuppressed();
  }

  float getDrawLineTopForBracket(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  float getLineHeightForBracket() {
    return lineHeight;
  }


  int getBraceGuideColumnForLineForBracket(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    return getBraceGuideColumnForLine(line, globalLine, braceIndex, firstNonSpace);
  }

  void resetScrollLockAxisForInput() {
    scrollManager.scrollLockAxis = 0;
  }

  void setJustFinishedScaleForInput(boolean finished) {
    zoomManager.setJustFinishedScale(finished);
  }

  void abortScrollerForInput() {
    if (!scrollManager.scroller.isFinished()) {
      scrollManager.scroller.computeScrollOffset();
      scrollManager.scrollX = scrollManager.scroller.getCurrX();
      scrollManager.scrollY = scrollManager.scroller.getCurrY();
      scrollManager.scroller.abortAnimation();
    }
  }

  void setDownForInput(float x, float y) {
    downX = x;
    downY = y;
  }

  void setMovedSinceDown(boolean moved) {
    movedSinceDown = moved;
  }

  boolean isMovedSinceDown() {
    return movedSinceDown;
  }

  boolean isLineNumberSelectionEnabledForInput() {
    return lineNumberManager.isLineNumberSelectionEnabled();
  }

  boolean isInLineNumberGutterForInput(float x) {
    return isInLineNumberGutter(x);
  }

  float getScrollYForInput() {
    return scrollManager.scrollY;
  }

  void beginLineNumberSelectionForInput(int line) {
    beginLineNumberSelection(line);
  }

  CursorTarget getCursorTargetForInput(float x, float y) {
    return getCursorTargetForPosition(x, y, null);
  }

  CursorTarget getCursorTargetForHandles(float x, float y) {
    return getCursorTargetForPosition(x, y, null);
  }

  HandlesManager getHandlesManagerForCursor() {
    return handlesManager;
  }

  void ensureLineInWindowForInput(int line, boolean reload) {
    scrollManager.ensureLineInWindow(line, reload);
  }

  String getLineFromWindowLocalForInput(int index) {
    return getLineFromWindowLocal(index);
  }

  int getWindowStartLineForInput() {
    return windowStartLine;
  }

  boolean applySmartDoubleTapSelectionForInput(int line, int ch, String ln) {
    return applySmartDoubleTapSelection(line, ch, ln);
  }

  void clearSelectionForInput() {
    if (selectionManager.hasSelection()) {
      selectionManager.clearSelectionKeepLineNumberState();
    }
  }

  boolean isCodeFoldingEnabledForInput() {
    return foldManager.isCodeFoldingEnabled;
  }

  void startFoldMarkerRippleForInput(int line) {
    startFoldMarkerRipple(line);
  }

  float getLineHeightForInput() {
    return lineHeight;
  }

  int getTotalVisualLineCountForInput() {
    return wordWrapManager.getTotalVisualLineCount(this);
  }

  int getVisibleLineCountForInput() {
    return getVisibleLineCount();
  }

  float viewToTextXForInput(float x) {
    return viewToTextX(x);
  }

  float measureTextWithVisualSpacesForInput(String s, int start, int end) {
    return whitespaceGuideManager.measureTextWithVisualSpaces(this, s, start, end, paint);
  }

  boolean isFoldPlaceholderHitForInput(int line, String ln, float x) {
    return isFoldPlaceholderHit(line, ln, x);
  }

  boolean isEofForInput() {
    return isEof;
  }

  int getLinesWindowSizeForInput() {
    return linesWindow.size();
  }

  boolean isLinesWindowEmptyForInput() {
    return linesWindow.isEmpty();
  }

  boolean isClickAfterEndToAddLineEnabledForInput() {
    return isClickAfterEndToAddLineEnabled;
  }

  void setCursorPositionForInput(int line, int ch) {
    cursorManager.setLineAndChar(line, ch);
  }

  void insertTextAtCursorForInput(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  void insertStringAtCursorForSuggestion(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  void setSelectingForInput(boolean selectingNow) {
    selectionManager.setSelecting(selectingNow);
  }

  void updateSuggestionForInput() {
    autoSuggestionManager.updateSuggestion();
  }

  void restartInputForInput() {
    restartInput();
  }

  void restartInputForSuggestion() {
    restartInput();
  }

  boolean handleScrollFromInput(MotionEvent e2, float distanceX, float distanceY) {
    return scrollManager.onScroll(e2, distanceX, distanceY);
  }

  boolean handleFlingFromInput(float velocityX, float velocityY) {
    return scrollManager.onFling(velocityX, velocityY);
  }

  private void applyTypeface(@Nullable Typeface typeface, int style) {
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
    autoSuggestionManager.onEditorTypefaceChanged(finalTypeface);
    lineNumberManager.setTypeface(finalTypeface);
    foldManager.foldMarkerPaint.setTypeface(finalTypeface);
    wordWrapManager.updateIndicatorTypeface(paint);
    whitespaceGuideManager.updateTypeface(paint);
    popupMenuManager.onEditorTypefaceChanged(finalTypeface);
    whitespaceGuideManager.updateRuleTypeface(safeBase);
    if (highlightManager.lineCommentHighlightRule != null) highlightManager.lineCommentHighlightRule.updateTypeface(safeBase);
    for (HighlightManager.HighlightRule rule : highlightManager.highlightRules) {
      rule.updateTypeface(safeBase);
    }
    highlightManager.clearHighlightCaches();

    lineHeight = paint.getFontSpacing();
    updateWhitespaceGuideMetrics();
    lineNumberManager.invalidateCache();
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
    recalculateMaxLineWidth();

    requestLayout();
    if (wordWrapManager.isWordWrapEnabled) wordWrapManager.invalidateWrapMetrics(this, true);
    wordWrapManager.requestWrapPrefixRebuild(this);
    invalidate();
  }

  private void updateWhitespaceGuideMetrics() {
    whitespaceGuideManager.updateMetrics(paint, WHITESPACE_GUIDE_SPACE, WHITESPACE_GUIDE_TAB);

  }

  public void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      @Nullable java.util.HashMap<Integer, String> directLines) {
    highlightManager.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, directLines);
  }

  public void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    highlightManager.maybeEnsureHighlightCacheForRange(startLine, endLine, directLines);
  }

  public void invalidateHighlightEnsureRange() {
    highlightManager.resetEnsureRange();
  }

  // --- Layout and Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float oldGutterWidth = lineNumberManager.getGutterWidth();
    if (lineNumberManager.isShowLineNumbers()) {
      int maxLines;
      if (isIndexReady) {
        maxLines = lineOffsets.length;
      } else if (isEof) {
        maxLines = windowStartLine + linesWindow.size();
      } else {
        maxLines = 999999; // Wider fallback for width calculation until index is ready
      }
      if (foldManager.isCodeFoldingEnabled) {
        foldManager.foldMarkerGutterWidth =
            foldManager.foldMarkerPaint.measureText("v") + foldManager.foldMarkerSpacing + foldManager.foldMarkerEdgePadding;
      } else {
        foldManager.foldMarkerGutterWidth = 0f;
      }
      lineNumberManager.setGutterWidth(
          lineNumberManager.computeGutterWidth(
              maxLines, foldManager.isCodeFoldingEnabled, foldManager.foldMarkerGutterWidth));
    } else {
      lineNumberManager.setGutterWidth(0f);
    }

    if (wordWrapManager.isWordWrapEnabled && Math.abs(lineNumberManager.getGutterWidth() - oldGutterWidth) > 0.1f) {
      wordWrapManager.invalidateWrapMetrics(this, true);
      wordWrapManager.requestWrapPrefixRebuild(this);
    }
    if (Math.abs(lineNumberManager.getGutterWidth() - oldGutterWidth) > 0.1f) {
      lineNumberManager.invalidateCache();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != oldw || h != oldh) {
      lineNumberManager.invalidateCache();
    }
    if (w != oldw) {
      scrollManager.maxScrollXForScroll = 0f;
      scrollManager.maxTextStartXForScroll = 0f;
    }
    int minWindow = computeMinWindowSize();
    if (windowSize < minWindow) {
      windowSize = minWindow;
      reloadWindowAroundVisible(false);
    }
    if (wordWrapManager.isWordWrapEnabled && w != oldw) {
      wordWrapManager.invalidateWrapMetrics(this, true);
      wordWrapManager.requestWrapPrefixRebuild(this);
    }
  }

  public float getTextStartX() {
    return lineNumberManager.getTextStartX(paddingLeft, isRtl);
  }

  public float getEffectiveScrollX() {
    return isRtl ? -scrollManager.scrollX : scrollManager.scrollX;
  }

  private float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - getTextStartX();
  }

  public float getTextAreaWidth() {
    return lineNumberManager.getTextAvailableWidth(getWidth(), paddingLeft);
  }

  public float getRtlLineBaseX(@Nullable String line, int globalLine) {
    if (!isRtl || line == null) return 0f;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = highlightManager.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!isRtl || line == null) return 0f;
    float w = highlightManager.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = getTextAreaWidth();
    return area - w;
  }

  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x = highlightManager.measureText(line, charIndex, globalLine);
    if (!isRtl) return x;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = highlightManager.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, segStart, charIndex, paint);
    if (!isRtl) return xRel;
    float w = highlightManager.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  










  private int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    if (text == null || text.isEmpty()) return 0;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (isRtl) {
      float baseX = getRtlSegmentBaseX(text, globalLine, start, end);
      x -= baseX;
      float w = highlightManager.measureHighlightedSegmentWidth(text, globalLine, start, end);
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
    float[] widths = whitespaceGuideManager.ensureMeasureWidthBuffer(len);
    paint.getTextWidths(text, start, end, widths);
    float current = 0f;
    for (int i = 0; i < len; i++) {
      float adv = whitespaceGuideManager.getCharAdvanceWidth(text.charAt(start + i), widths[i], paint, WordWrapManager.DEFAULT_TAB_SIZE_SPACES);
      float mid = current + adv * 0.5f;
      if (x < mid) return start + i;
      if (x < current + adv) return start + i + 1;
      current += adv;
    }
    return end;
  }

  private CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    float y = viewY + scrollManager.scrollY;
    int visualIndex = Math.max(0, (int) (y / lineHeight));
    VisualLinePosition pos =
        wordWrapManager.isWordWrapEnabled
            ? wordWrapManager.getVisualPositionForIndex(this, visualIndex)
            : new VisualLinePosition(mapVisibleIndexToGlobal(visualIndex), 0);
    String line = getLineTextForRenderWithDirect(pos.line, directLines);
    if (!wordWrapManager.isWordWrapEnabled) {
      float x = viewToTextX(viewX);
      int charIndex = getCharIndexForX(line, x, pos.line);
      int clamped = Math.max(0, Math.min(charIndex, getLogicalLineLength(pos.line, line)));
      return new CursorTarget(pos.line, clamped);
    }
    int[] starts = wordWrapManager.getWrapStartsForLine(this, pos.line, line);
    int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
    int segStart = wordWrapManager.getWrapSegmentStart(starts, seg);
    int segEnd = wordWrapManager.getWrapSegmentEnd(starts, seg, line.length());
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

  static final class VisualLinePosition {
    final int line;
    final int segment;

    VisualLinePosition(int line, int segment) {
      this.line = line;
      this.segment = segment;
    }
  }

  public float getGutterStartX() {
    return lineNumberManager.getLineNumberViewLeft(getWidth(), isRtl);
  }

  private boolean isInLineNumberGutter(float x) {
    return lineNumberManager.isInLineNumberGutter(x, getGutterStartX());
  }

  private int clampLineForSelection(int line) {
    if (line < 0) return 0;
    if (isEof) {
      int last = windowStartLine + linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  private boolean isLineSelectable(int line) {
    scrollManager.ensureLineInWindow(line, true);
    String ln = getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }

  private void beginLineNumberSelection(int line) {
    int clamped = clampLineForSelection(line);
    if (!isLineSelectable(clamped)) return;
    autoSuggestionManager.clearActiveSuggestion();
    selectionManager.setLineNumberSelecting(true, clamped);
    selectionManager.setSelectAllState(false, false);
    String lineText = getLineTextForRender(clamped);
    selectionManager.setSelection(clamped, 0, clamped, lineText.length(), true);
    cursorManager.setLineAndChar(clamped, selectionManager.selEndChar);
    popupMenuManager.hidePopup();
    cursorAnimationManager.resetCursorBlink();
    invalidate();
  }

  private void updateLineNumberSelection(int line) {
    if (!selectionManager.isLineNumberSelecting()) return;
    int clamped = clampLineForSelection(line);
    if (!isLineSelectable(clamped)) return;
    int anchorLine = selectionManager.getLineNumberSelectAnchorLine();
    int startLine = Math.min(anchorLine, clamped);
    int endLine = Math.max(anchorLine, clamped);
    scrollManager.ensureLineInWindow(endLine, true);
    String endLineText = getLineTextForRender(endLine);
    selectionManager.setSelection(startLine, 0, endLine, endLineText.length(), true);
    cursorManager.setLineAndChar(endLine, selectionManager.selEndChar);
    selectionManager.setLineNumberSelecting(true, anchorLine);
    popupMenuManager.hidePopup();
    invalidate();
  }

  private String buildFoldDisplayLine(String line, FoldManager.FoldRange range, int[] placeholderBoundsOut) {
    return foldManager.buildFoldDisplayLine(line, range, placeholderBoundsOut);
  }

  String buildFoldDisplayLineInternal(String line, FoldManager.FoldRange range, int[] placeholderBoundsOut) {
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
    foldManager.drawFoldedLine(canvas, line, globalLine);
  }

  private boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
    return foldManager.isFoldPlaceholderHit(globalLine, line, localX);
  }

  private String getFoldMarkerForLine(int line, @Nullable String lineText) {
    return foldManager.getFoldMarkerForLine(line, lineText);
  }

  String getFoldMarkerForLineInternal(int line, @Nullable String lineText) {
    return foldManager.getFoldMarkerForLine(line, lineText);
  }

  private boolean isIndentFoldCandidate(String line) {
    return foldManager.isIndentFoldCandidate(line);
  }

  private void startFoldMarkerRipple(int line) {
    foldManager.startFoldMarkerRipple(line);
  }

  private void clearFoldRipple() {
    foldManager.clearFoldRipple();
  }

  private boolean shouldShowFoldMarkerFromLine(String line) {
    return foldManager.shouldShowFoldMarkerFromLine(line);
  }



  public boolean superOnKeyDown(int keyCode, KeyEvent event) {
    return super.onKeyDown(keyCode, event);
  }





  void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
    viewRender.getVisibleCharRangeForLine(line, globalLine, out);
  }

  private void getVisibleCharRangeForLineFast(
      String line, int globalLine, int lineLength, int[] out) {
    viewRender.getVisibleCharRangeForLineFast(line, globalLine, lineLength, out);
  }

  private void computeStreamedSliceBounds(
      @Nullable String lineText, int globalLine, int lineLength, int[] out) {
    if (out == null || out.length < 2) return;
    int len = Math.max(0, lineLength);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    float avg = highlightManager.getAverageCharWidthForLine((lineText == null) ? "" : lineText, globalLine);
    if (avg <= 0f) avg = paint.measureText(" ");
    float viewLeft = lineNumberManager.getContentViewLeft(isRtl);
    float viewRight = lineNumberManager.getContentViewRight(getWidth(), isRtl);
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

  private int getInitialStreamedSliceSize() {
    int base = Math.max(128, colsWidthCacheSize);
    int pad = Math.max(0, prefetchCols) * 2;
    return Math.max(base, pad);
  }

  public void drawFoldMarkersForVisibleLines(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    foldManager.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
  }

  public void drawDeleteAnimationForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
    if (!charAnimationManager.isEnabled()) return;
    if (globalLine != charAnimationManager.getDelAnimLine()
        || charAnimationManager.getDelAnimText() == null
        || charAnimationManager.getDelAnimText().isEmpty()
        || charAnimationManager.getDelAnimAlpha() <= 0f) return;
    if (line == null) line = "";
    int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
    if (at < segStart || at > segEnd) return;
    float x = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, segStart, at, paint);
    Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
    charAnimationManager.getTempPaint().set(ghostPaint);
    charAnimationManager.getTempPaint().setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
    canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
  }



  boolean isMixedDirectionText(CharSequence text, int start, int end) {
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

  private boolean isRtlScriptBlock(Character.UnicodeBlock block) {
    return block == Character.UnicodeBlock.ARABIC
        || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
        || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
        || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
        || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
        || block == Character.UnicodeBlock.ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS
        || block == Character.UnicodeBlock.HEBREW;
  }

  private boolean isLatinScriptBlock(Character.UnicodeBlock block) {
    return block == Character.UnicodeBlock.BASIC_LATIN
        || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
        || block == Character.UnicodeBlock.LATIN_EXTENDED_A
        || block == Character.UnicodeBlock.LATIN_EXTENDED_B
        || block == Character.UnicodeBlock.LATIN_EXTENDED_C
        || block == Character.UnicodeBlock.LATIN_EXTENDED_D
        || block == Character.UnicodeBlock.LATIN_EXTENDED_E
        || block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL;
  }

  int getVisualSpaceScale() {
    return 1;
  }

  private boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    List<HighlightManager.HighlightSpan> spans = highlightManager.highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlightManager.calculateSpansForLine(line, globalLine);
      highlightManager.highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    final float eps = 0.25f;

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighlightManager.HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          for (int i = pos; i < Math.min(span.start, len); i++) {
            float adv = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, i, i + 1, paint);
            if (x >= currentX - eps && x <= currentX + adv + eps) {
              return Character.isWhitespace(line.charAt(i));
            }
            currentX += adv;
          }
        }
        int start = Math.max(pos, span.start);
        int end = Math.min(len, span.end);
        for (int i = start; i < end; i++) {
          float adv = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, i, i + 1, paint);
          if (x >= currentX - eps && x <= currentX + adv + eps) {
            return Character.isWhitespace(line.charAt(i));
          }
          currentX += adv;
        }
        pos = Math.max(pos, end);
      }
    }

    if (pos < len) {
      for (int i = pos; i < len; i++) {
        float adv = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, i, i + 1, paint);
        if (x >= currentX - eps && x <= currentX + adv + eps) {
          return Character.isWhitespace(line.charAt(i));
        }
        currentX += adv;
      }
    }

    return true;
  }

  boolean isIndentationBlocksEnabledForIndentGuides() {
    return isIndentationBlocksEnabled;
  }

  boolean isHeavyDrawSuppressedForIndentGuides() {
    return isHeavyDrawSuppressed();
  }

  float getIndentGuideLineTop(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  float getIndentGuideLineHeight() {
    return lineHeight;
  }

  int getIndentGuideTabSize() {
    return WordWrapManager.DEFAULT_TAB_SIZE_SPACES;
  }

  String getIndentGuideUnit() {
    return INDENT_BLOCK_UNIT;
  }

  float measureTextWithVisualSpacesForIndentGuides(String line, int start, int end) {
    return whitespaceGuideManager.measureTextWithVisualSpaces(this, line, start, end, paint);
  }

  boolean isWhitespaceAtXForIndentGuides(String line, int globalLine, float x) {
    return isWhitespaceAtX(line, globalLine, x);
  }

  boolean hasIndentGuideFoldRanges() {
    return foldManager.hasFoldRanges();
  }

  Iterable<FoldManager.FoldRange> getIndentGuideFoldRanges() {
    return foldManager.getFoldRanges();
  }

  float getIndentGuideTextSizePx() {
    return paint.getTextSize();
  }


  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }

  private int getBraceGuideColumnForLine(
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

  private int getPreviousNonEmptyIndentColumn(int line) {
    for (int l = line; l >= 0; l--) {
      String prev = getLineTextForRender(l);
      if (prev == null) continue;
      int idx = getFirstNonSpaceIndex(prev);
      if (idx >= 0) return idx;
    }
    return -1;
  }





  private static final class StreamedSliceRequest {
    final int line;
    final int start;
    final int end;

    StreamedSliceRequest(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  private static final class LineScanResult {
    final long length;
    final boolean reachedEof;

    LineScanResult(long length, boolean reachedEof) {
      this.length = length;
      this.reachedEof = reachedEof;
    }
  }

  private LineScanResult scanLineLength(RandomAccessFile raf) throws Exception {
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
    if (wordWrapManager.isWordWrapEnabled) return;
    if (!fileManager.isIndexReady() || fileManager.getSourceFile() == null || !fileManager.getSourceFile().exists()) return;
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
          if (fileManager.getSourceFile() == null || !fileManager.getSourceFile().exists()) {
            post(() -> fileManager.streamedSliceUpdatePending = false);
            return;
          }
          LinkedHashMap<Integer, String> results = new LinkedHashMap<>();
          SparseIntArray starts = new SparseIntArray();
          try (RandomAccessFile raf = new RandomAccessFile(fileManager.getSourceFile(), "r")) {
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
    if (zoomManager.isZoomGestureActive()) return;
    if (fileManager.getSourceFile() == null || fileManager.isFileCleared()) {
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

  boolean shouldHideCopyCutForSelection() {
    if (!selectionManager.hasSelection()) return true;

    int sL = selectionManager.selStartLine, eL = selectionManager.selEndLine;
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

  public void setReplaceAllMaxCount(int maxCount) {
    replaceAllMaxCount = Math.max(1, maxCount);
  }

  public int getReplaceAllMaxCount() {
    return replaceAllMaxCount;
  }

  public void setHideKeyboardOnFocusLoss(boolean enabled) {
    hideKeyboardOnFocusLoss = enabled;
  }

  private void startFlingStopAnimation(float targetX, float targetY) {
    scrollManager.startFlingStopAnimation(targetX, targetY);
  }

  private void cancelFlingStopAnimation() {
    scrollManager.cancelFlingStopAnimation();
  }

  @Override
  public void computeScroll() {
    scrollManager.computeScroll();
  }

  private int getFlingOverScrollX() {
    return scrollManager.getFlingOverScrollX();
  }

  private int getFlingOverScrollY() {
    return scrollManager.getFlingOverScrollY();
  }


  private float getMaxScrollYForClamp() {
    return scrollManager.getMaxScrollYForClamp();
  }

  void clampScrollY() {
    scrollManager.clampScrollY();
  }

  void abortScrollAnimationForZoom() {
    if (!scrollManager.scroller.isFinished()) {
      scrollManager.scroller.abortAnimation();
    }
  }

  void checkAndLoadWindow() {
    if (fileManager.getSourceFile() == null || fileManager.isFileCleared()) return;
    if (getWidth() == 0 || getHeight() == 0) return;
    if (isWindowLoading) return;

    int firstVisibleIndex = (int) (scrollManager.scrollY / lineHeight);
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(getHeight() / lineHeight);
    int firstVisibleLine;
    int lastVisibleLine;
    if (wordWrapManager.isWordWrapEnabled) {
      firstVisibleLine = wordWrapManager.getVisualPositionForIndex(this, firstVisibleIndex).line;
      lastVisibleLine = wordWrapManager.getVisualPositionForIndex(this, lastVisibleIndex).line;
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

  void loadWindowAround(int startLine, @Nullable Runnable onComplete) {
    loadWindowAround(startLine, onComplete, true);
  }

  void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
    if (isWindowLoading) return;
    // Cancel any in-flight async width calculation for a previous window.
    maxWidthRecalcToken++;

    // FIX: If the file has been "cleared" (e.g., via select-all -> delete),
    // the editor is in a pure in-memory state. The `linesWindow` holds the
    // entire document. There is nothing to "load" from a file, so we should
    // simply do nothing. The previous logic incorrectly reset the window.
    if (fileManager.isFileCleared()) {
      if (onComplete != null) {
        post(onComplete);
      }
      return;
    }

    if (fileManager.getSourceFile() == null) {
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

            if (fileManager.isIndexReady()) {
              try (RandomAccessFile raf = new RandomAccessFile(fileManager.getSourceFile(), "r")) {
                long fileLen = raf.length();
                if (fileLen > 0) {
                  raf.seek(fileLen - 1);
                  fileEndsWithNewline = (raf.read() == '\n');
                }
                int limit = windowSize + (prefetchLines * 2);
                int lineIndex = actualStart;
                int maxLine;
                synchronized (fileManager.lineOffsetsLock) {
                  maxLine = fileManager.getLineOffsets().length;
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
                  synchronized (fileManager.lineOffsetsLock) {
                    trailingEmptyFromIndex =
                        fileManager.getLineOffsets().length > 0 && fileManager.getLineOffsets()[fileManager.getLineOffsets().length - 1] == fileLen;
                  }
                }
              }
            } else {
              // fallback: sequential scan without building full lines in memory
              try (RandomAccessFile raf = new RandomAccessFile(fileManager.getSourceFile(), "r")) {
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
                  if (fileManager.shouldStreamLineLength(lineLen)) {
                    int sliceStart = 0;
                    int sliceEnd =
                        Math.max(1, Math.min(lineLen, getInitialStreamedSliceSize()));
                    if (fileManager.isSingleByteCharset()) {
                      String slice =
                          fileManager.readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, getInitialStreamedSliceSize());
                      FileManager.StreamedCharSlice slice =
                          fileManager.readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
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
                                : new String(buf, fileManager.fileCharset))
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
                  lineNumberManager.invalidateCache();
                  invalidateHighlightEnsureRange();
                  bracketGuideManager.invalidateCache();
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
                  if (wordWrapManager.isWordWrapEnabled) {
                    if (wordWrapManager.shouldSuppressWrapMetricsForFastSelectAll(this)) {
                      wordWrapManager.wrapMetricsReady = false;
                    } else {
                      if (!wordWrapManager.wrapMetricsReady || wordWrapManager.wrapLineCounts == null || wordWrapManager.wrapLinePrefix == null) {
                        if (getWidth() > 0) {
                          wordWrapManager.buildWrapMetricsForWindowSnapshot(this);
                        }
                      }
                      wordWrapManager.scheduleWrapMetricsSnapshotIfNeeded(this, Math.max(1, Math.round(wordWrapManager.getWrapWidth(this))));
                      wordWrapManager.requestWrapPrefixRebuild(this);
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

  private void finishInitialFileOpenWarmup(final int token) {
    if (!isInitialFileOpenLoading) return;
    if (token != initialFileOpenToken) return;
    if (getHeight() <= 0 || lineHeight <= 0f) {
      postDelayed(() -> finishInitialFileOpenWarmup(token), 16);
      return;
    }

    int firstVisibleLine = Math.max(0, getGlobalLineForY(scrollManager.scrollY));
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
    loadingCircleManager.show(false);
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

  public void recalculateMaxLineWidth() {
    recalculateMaxLineWidthAsync();
  }

  private void recalculateMaxLineWidthAsync() {
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
              scrollManager.clampScrollX();
              invalidate();
            }
          }
        });
  }

  private void buildFileIndex() {
    if (fileManager.getSourceFile() == null || !fileManager.getSourceFile().exists()) {
      fileManager.isIndexReady = false;
      fileManager.isIndexBuilding = false;
      return;
    }
    if (fileManager.isIndexDisabled()) {
      String path = fileManager.getSourceFile().getAbsolutePath();
      long len = fileManager.getSourceFile().length();
      if (path.equals(fileManager.indexDisabledPath) && len == fileManager.indexDisabledFileLength) {
        fileManager.isIndexReady = false;
        fileManager.isIndexBuilding = false;
        return;
      }
      fileManager.setIndexDisabled(false);
      fileManager.indexDisabledPath = null;
      fileManager.indexDisabledFileLength = -1L;
    }
    fileManager.isIndexBuilding = true;
    final int taskVersion = ioTaskVersion.get();
    ioHandler.post(
        () -> {
          long[] offsets = buildIndexJava(fileManager.getSourceFile().getAbsolutePath());
          if (taskVersion != ioTaskVersion.get()) {
            fileManager.isIndexBuilding = false;
            return;
          }
          if (offsets != null) {
            synchronized (fileManager.lineOffsetsLock) {
              if (taskVersion == ioTaskVersion.get()) {
                fileManager.setLineOffsets(offsets);
                fileManager.isIndexReady = true;
                // When index is ready, we know the true line count.
                // We must re-measure to calculate the correct gutter width.
                post(SodiumEditorView.this::requestLayout);
                if (wordWrapManager.isWordWrapEnabled) post(() -> wordWrapManager.scheduleWrapMetricsBuild(this));
              }
            }
          } else {
            synchronized (fileManager.lineOffsetsLock) {
              fileManager.isIndexReady = false;
            }
          }
          fileManager.isIndexBuilding = false;
        });
  }

  private void invalidatePendingIO() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    highlightManager.clearHighlightCaches();
    if (wordWrapManager.isWordWrapEnabled) wordWrapManager.invalidateWrapMetrics(this);
    if (foldManager.isCodeFoldingEnabled) {
      foldManager.clearAllFolds();
    }
  }

  public void invalidatePendingIOForEdit() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    highlightManager.clearHighlightCaches();
    if (foldManager.isCodeFoldingEnabled) {
      foldManager.clearAllFolds();
      indentGuideManager.markIntervalsDirty();
    }
  }

  public void clearContent() {
    fileManager.clearContent();
  }

  public void loadFromFile(final File file) {
    fileManager.loadFromFile(file);
  }

  public void updateSourceFile(File file) {
    fileManager.updateSourceFile(file);
  }

  public int getEditVersionValue() {
    return undoRedo.getEditVersion();
  }

  public void refreshLineNumberCache() {
    lineNumberManager.invalidateCache();
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
      autoSuggestionManager.clearActiveSuggestion();
      selectionManager.clearSelectionKeepLineNumberState();
      popupMenuManager.hidePopup();
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

  private void restartInput() {
    imeManager.restartInput();
  }

  public void setShowLoadingOnFileOpen(boolean enabled) {
    fileManager.setShowLoadingOnFileOpen(enabled);
  }

  private boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike) {
    int span = Math.abs(eL - sL) + 1;
    return isSelectAllLike || span >= LARGE_EDIT_LINES;
  }

  public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
    if (!enable) return;
    if (!shouldShowLargeEditUi(sL, eL, isSelectAllLike)) return;

    final int token = largeEditUiToken.incrementAndGet();
    setDisable(true);
    loadingCircleManager.show(true);

    // Watchdog: force hide after a short time in case any path forgets to hide.
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    mainHandler.postDelayed(largeEditUiWatchdog, 1500);

    // Also ensure token validity for later hides.
    post(
        () -> {
          if (token != largeEditUiToken.get()) return;
        });
  }

  private void endLargeEditUi(boolean invalidate) {
    // Advance token so any pending watchdog is ignored, then hide.
    largeEditUiToken.incrementAndGet();
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    setDisable(false);
    loadingCircleManager.show(false);
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
    loadingCircleManager.show(true);

    if (selectionManager.hasSelection()) {
      selectionManager.clearSelectionKeepLineNumberState();
      selectionManager.setSelecting(false);
      popupMenuManager.hidePopup();
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

  private void proceedGoToLineClamped(
      final int currentGoToLineVersion, final int targetLine, final int targetCol) {
    // If a window load is already in progress, retry shortly to avoid getting stuck in a
    // disabled/loading state.
    if (isWindowLoading
        && fileManager.getSourceFile() != null
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

          int finalLine = targetLine;
          int finalChar;
          if (finalLine >= windowStartLine && finalLine < windowStartLine + linesWindow.size()) {
            String lineText = getLineTextForRender(finalLine);
            finalChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (isEof) {
            int lastLineInDoc = windowStartLine + linesWindow.size() - 1;
            if (finalLine > lastLineInDoc) finalLine = Math.max(0, lastLineInDoc);
            String lineText = getLineTextForRender(finalLine);
            finalChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else {
            finalChar = 0;
          }
          cursorManager.setLineAndChar(finalLine, finalChar);

          scrollManager.keepCursorVisibleHorizontally();
          setDisable(false);
          loadingCircleManager.show(false);

          requestFocus();
          post(
              () -> {
                imeManager.showKeyboard();
                requestFocus();
                InputMethodManager imm =
                    (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.restartInput(this);
              });
        };

    // In-memory mode (sourceFile == null): no window loads.
    if (fileManager.isFileCleared()
        || fileManager.getSourceFile() == null
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
    undoRedo.incrementEditVersion();

    // FIX: لو فيه تحديد، لازم يكون استبدال ذري (خصوصاً خارج الشاشة)
    if (cursorManager.getHasComposing()) {
      cursorManager.setHasComposing(false);
      cursorManager.setComposingLength(0);
    }

    final int beforeLine = cursorManager.getLine();
    final int beforeChar = cursorManager.getChar();

    scrollManager.ensureLineInWindow(cursorManager.getLine(), true);
    if (isWindowLoading
        && (cursorManager.getLine() < windowStartLine || cursorManager.getLine() >= windowStartLine + linesWindow.size())) {
      post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = cursorManager.getLine() - windowStartLine;
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
        String before = base.substring(0, Math.min(cursorManager.getChar(), base.length()));
        String after = base.substring(Math.min(cursorManager.getChar(), base.length()));
        Float oldWidth = lineWidthCache.get(cursorManager.getLine());

        updateLocalLine(localIdx, before);
        linesWindow.add(localIdx + 1, after);

        modifiedLines.put(cursorManager.getLine(), before);
        modifiedLines.put(cursorManager.getLine() + 1, after);

        computeWidthForLine(cursorManager.getLine(), before);
        computeWidthForLine(cursorManager.getLine() + 1, after);

        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        highlightManager.clearHighlightCaches();
        cursorManager.setLineAndChar(cursorManager.getLine() + 1, 0);
        undoRedo.addLineCountDelta(1);

        int newLineCount = getLinesCount();
        if (lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        wordWrapManager.onLineCountChanged(this);
      } else {
        int pos = Math.max(0, Math.min(cursorManager.getChar(), base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.getLine(), modified);
        highlightManager.invalidateHighlightCacheForLine(cursorManager.getLine());
        cursorManager.moveCharDelta(1);
        float newWidth =
            whitespaceGuideManager.measureTextWithVisualSpaces(
                this, modified, 0, modified.length(), paint);
        synchronized (lineWidthCache) {
          lineWidthCache.put(cursorManager.getLine(), newWidth);
        }
        currentMaxWindowLineWidth = Math.max(currentMaxWindowLineWidth, newWidth);
        globalMaxLineWidth = Math.max(globalMaxLineWidth, currentMaxWindowLineWidth);
      }
      invalidate();
      scrollManager.keepCursorVisibleHorizontally();
    }
    autoSuggestionManager.updateSuggestion();

    UndoRedo.EditOp op = new UndoRedo.EditOp();
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
    op.cursorLineAfter = cursorManager.getLine();
    op.cursorCharAfter = cursorManager.getChar();
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (isReadOnly) return;
    if (selectionManager.hasSelection()) {
      replaceSelectionWithText("\n");
      return;
    }

    BracketPairType pairType = cursorManager.getCursorBracketPairType();
    if (isAutoBracketNewlineEnabled && pairType != BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (isAutoBracketNewlineIndentEnabled) {
        baseIndent = getLineLeadingWhitespace(cursorManager.getLine());
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = cursorManager.getLine() + 1;
      int targetChar = innerIndent.length();
      insertTextAtCursor(insertText);

      cursorManager.setLineAndChar(targetLine, targetChar);
      cursorAnimationManager.resetCursorBlink();
      scrollManager.keepCursorVisibleHorizontally();
      invalidate();
      autoSuggestionManager.updateSuggestion();
      return;
    }

    if (isAutoIndentAfterClosingBracketEnabled) {
      String ln = getLineTextForRender(cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = getLineLeadingWhitespace(cursorManager.getLine());
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
      String ln = getLineTextForRender(cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = getLineLeadingWhitespace(cursorManager.getLine());
      String extraIndent = trimmed.endsWith(":") ? INDENT_BLOCK_UNIT : "";
      insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (isAutoBracketNewlineIndentEnabled) {
      String baseIndent = getLineLeadingWhitespace(cursorManager.getLine());
      insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }



  private static String rstripWhitespace(String text) {
    if (text == null || text.isEmpty()) return "";
    int end = text.length();
    while (end > 0) {
      char c = text.charAt(end - 1);
      if (c != ' ' && c != '\t') break;
      end--;
    }
    return (end == text.length()) ? text : text.substring(0, end);
  }

  private static int findPrevNonWhitespaceIndex(String text, int start) {
    if (text == null || text.isEmpty()) return -1;
    for (int i = Math.min(start, text.length() - 1); i >= 0; i--) {
      if (!Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }

  private static String buildIndentFromWidth(int width) {
    if (width <= 0) return "";
    char[] buf = new char[width];
    for (int i = 0; i < width; i++) buf[i] = ' ';
    return new String(buf);
  }

  int getIndentWidth(String line) {
    if (line == null || line.isEmpty()) return 0;
    int width = 0;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == ' ') {
        width++;
      } else if (c == '\t') {
        width += wordWrapManager.DEFAULT_TAB_SIZE_SPACES;
      } else {
        break;
      }
    }
    return width;
  }

  private String getLineLeadingWhitespace(int line) {
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



  public void deleteCharAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion on delete

    if (cursorManager.getHasComposing()) {
      cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = cursorManager.getLine();
    final int beforeChar = cursorManager.getChar();

    scrollManager.ensureLineInWindow(cursorManager.getLine(), true);
    if (isWindowLoading
        && (cursorManager.getLine() < windowStartLine || cursorManager.getLine() >= windowStartLine + linesWindow.size())) {
      post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = cursorManager.getLine() - windowStartLine;
    if (localIdx < 0 || localIdx >= linesWindow.size()) return;

    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursorManager.getChar() > 0) {
        Float oldWidth = lineWidthCache.get(cursorManager.getLine());
        int safeStart = Math.max(0, cursorManager.getChar() - 1);
        String removed = base.substring(safeStart, Math.min(cursorManager.getChar(), base.length()));
        boolean atLineEnd = cursorManager.getChar() >= base.length();
        if (charAnimationManager.isEnabled() && atLineEnd) {
          Paint p = highlightManager.getPaintForChar(cursorManager.getLine(), safeStart, base);
          charAnimationManager.startDeleteAnimation(cursorManager.getLine(), safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(cursorManager.getChar());
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.getLine(), modified);
        highlightManager.invalidateHighlightCacheForLine(cursorManager.getLine());
        cursorManager.setChar(safeStart);
        computeWidthForLine(cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursorManager.getLine());

        UndoRedo.EditOp op = new UndoRedo.EditOp();
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
        op.cursorLineAfter = cursorManager.getLine();
        op.cursorCharAfter = cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else if (cursorManager.getLine() > 0) {
        int oldLineCount = getLinesCount();
        int prevGlobal = cursorManager.getLine() - 1;
        scrollManager.ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - windowStartLine;
        if (prevLocal < 0 || prevLocal >= linesWindow.size()) return;

        String prev = getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        updateLocalLine(prevLocal, merged);
        modifiedLines.put(prevGlobal, merged);
        highlightManager.clearHighlightCaches();

        if (localIdx < linesWindow.size()) linesWindow.remove(localIdx);

        recalculateMaxLineWidth();
        cursorManager.setLineAndChar(prevGlobal, prev.length());
        computeWidthForLine(prevGlobal, merged);
        undoRedo.addLineCountDelta(-1);

        int newLineCount = getLinesCount();
        if (lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        wordWrapManager.onLineCountChanged(this);
        invalidate();

        UndoRedo.EditOp op = new UndoRedo.EditOp();
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
        op.cursorLineAfter = cursorManager.getLine();
        op.cursorCharAfter = cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
    }
    autoSuggestionManager.updateSuggestion(); // Update suggestion after deletion
  }

  public void deleteForwardAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion on delete forward

    if (cursorManager.getHasComposing()) {
      cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = cursorManager.getLine();
    final int beforeChar = cursorManager.getChar();

    scrollManager.ensureLineInWindow(cursorManager.getLine(), true);
    if (isWindowLoading
        && (cursorManager.getLine() < windowStartLine || cursorManager.getLine() >= windowStartLine + linesWindow.size())) {
      post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = cursorManager.getLine() - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursorManager.getChar() < base.length()) {
        Float oldWidth = lineWidthCache.get(cursorManager.getLine());
        String removed = base.substring(cursorManager.getChar(), Math.min(cursorManager.getChar() + 1, base.length()));
        boolean atLineEnd = cursorManager.getChar() == base.length() - 1;
        if (charAnimationManager.isEnabled() && atLineEnd) {
          Paint p = highlightManager.getPaintForChar(cursorManager.getLine(), cursorManager.getChar(), base);
          charAnimationManager.startDeleteAnimation(cursorManager.getLine(), cursorManager.getChar(), removed, p);
        }
        String modified = base.substring(0, cursorManager.getChar()) + base.substring(cursorManager.getChar() + 1);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.getLine(), modified);
        computeWidthForLine(cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursorManager.getLine());

        UndoRedo.EditOp op = new UndoRedo.EditOp();
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
        op.cursorLineAfter = cursorManager.getLine();
        op.cursorCharAfter = cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else {
        int nextGlobal = cursorManager.getLine() + 1;
        if (isEof && nextGlobal >= windowStartLine + linesWindow.size()) return;

        scrollManager.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - windowStartLine;
        if (nextLocal >= 0 && nextLocal < linesWindow.size()) {
          String next = getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          updateLocalLine(localIdx, merged);
          linesWindow.remove(nextLocal);
          modifiedLines.put(cursorManager.getLine(), merged);
          recalculateMaxLineWidth();
          computeWidthForLine(cursorManager.getLine(), merged);
          wordWrapManager.onLineCountChanged(this);
          invalidate();
          undoRedo.addLineCountDelta(-1);

          UndoRedo.EditOp op = new UndoRedo.EditOp();
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
          op.cursorLineAfter = cursorManager.getLine();
          op.cursorCharAfter = cursorManager.getChar();
          op.timestamp = System.currentTimeMillis();
          recordEdit(op);
        }
      }
    }
    autoSuggestionManager.updateSuggestion(); // Update suggestion after delete forward
  }







  public int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  public void setSelectionRange(int sLine, int sChar, int eLine, int eChar) {
    setSelectionInternal(sLine, sChar, eLine, eChar);
    invalidate();
  }

  private static final long COPY_CUT_MAX_LINES = 20000L;
  private static final int COPY_CUT_MAX_CHARS = 8_000_000; // safety cap
  private long copyCutMaxLines = COPY_CUT_MAX_LINES;
  private int copyCutMaxChars = COPY_CUT_MAX_CHARS;
  private int hideCopyCutMaxLines = HIDE_COPY_CUT_LINES;
  private int replaceAllMaxCount = 100000;
  private boolean hideKeyboardOnFocusLoss = true;

  public String getSelectedText() {
    if (!selectionManager.hasSelection()) return null;
    if (shouldHideCopyCutForSelection()) return null;

    int sL = selectionManager.selStartLine, sC = selectionManager.selStartChar, eL = selectionManager.selEndLine, eC = selectionManager.selEndChar;
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

  private void copyOrCutSelection(final boolean cut) {
    if (!selectionManager.hasSelection()) return;
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when copying/cutting

    // Hidden/disabled for huge selections (requested behavior)
    if (shouldHideCopyCutForSelection()) return;

    int sL = selectionManager.selStartLine, sC = selectionManager.selStartChar, eL = selectionManager.selEndLine, eC = selectionManager.selEndChar;
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

    if (wordWrapManager.isWordWrapEnabled) {
      wordWrapManager.cancelWrapWorkForPriority(this);
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

  private String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    // In-memory (no file backing): build from render-safe access
    if (fileManager.getSourceFile() == null || fileManager.isFileCleared()) {
      return buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // If the selection is fully inside the current window, prefer the window snapshot to avoid
    // stale file reads while edits are pending.
    boolean fullyInWindow = (sL >= windowStartLine) && (eL < windowStartLine + linesWindow.size());
    if (fullyInWindow) {
      return buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // File-backed: sequential read from start line, overriding with modifiedLines when available
    try (RandomAccessFile raf = new RandomAccessFile(fileManager.getSourceFile(), "r")) {
      long startByte;
      if (fileManager.isIndexReady()) {
        synchronized (fileManager.lineOffsetsLock) {
          if (sL >= 0 && sL < fileManager.getLineOffsets().length) startByte = fileManager.getLineOffsets()[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = findLineStartByteByScanning(raf, sL);
      }

      raf.seek(startByte);
      try (BufferedReader br =
          new BufferedReader(
              new java.io.InputStreamReader(new FileInputStream(raf.getFD()), fileManager.fileCharset), 8192)) {

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

  private String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
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
    undoRedo.incrementEditVersion();
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when pasting

    ClipboardManager cm =
        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(getContext());
    if (txt == null) return;
    cursorManager.insertTextAtCursor(txt.toString());
    autoSuggestionManager.updateSuggestion(); // Update suggestion after pasting
  }

  public void actionPaste() {
    pasteFromClipboard();
  }

  interface LineCountCallback {
    void onResult(int count);
  }

  private void countTotalLines(LineCountCallback callback) {
    final int taskVersion = ioTaskVersion.get();
    ioHandler.post(
        () -> {
          if (taskVersion != ioTaskVersion.get()) {
            post(() -> callback.onResult(-1));
            return;
          }
          if (fileManager.isIndexReady() && fileManager.getSourceFile() != null) {
            synchronized (fileManager.lineOffsetsLock) {
              post(() -> callback.onResult(fileManager.getLineOffsets().length));
            }
            return;
          }
          int count = 0;
          if (fileManager.getSourceFile() != null && fileManager.getSourceFile().exists()) {
            try (FileInputStream is = new FileInputStream(fileManager.getSourceFile())) {
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

  public void selectAll() {
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when selectionManager.selecting all
    final boolean keyboardWasVisible = keyboardHeight > 0;
    if (wordWrapManager.isWordWrapEnabled) {
      // Free the IO thread from wrap rebuilds so select-all can jump to end quickly.
      int widthPx = Math.max(1, Math.round(wordWrapManager.getWrapWidth(this)));
      if (wordWrapManager.isWrapMetricsUsableForWindow(this, widthPx)) {
        wordWrapManager.cancelWrapWorkForPriority(this);
      }
    }
    setDisable(true);
    loadingCircleManager.show(true);

    selectionManager.setSelectAllState(true, true);
    selectionManager.setSelection(0, 0, 0, 0, false);
    popupMenuManager.hidePopup();

    // =========================
    // In-memory mode (no file):
    // - Happens after "select all -> delete" (file cleared), then user types new text
    // - Also covers scenarios where content is edited but not persisted to disk
    // =========================
    if (sourceFile == null || isFileCleared) {
      synchronized (linesWindow) {
        if (linesWindow.isEmpty()) linesWindow.add("");
        // With no file backing, treat current window as the whole document.
        if (windowStartLine != 0) windowStartLine = 0;
        isEof = true;
      }

      selectionManager.selEndLine = Math.max(0, windowStartLine + linesWindow.size() - 1);
      String lastLineText = getLineTextForRender(selectionManager.selEndLine);
      selectionManager.selEndChar = lastLineText.length();
      cursorManager.setLineAndChar(selectionManager.selEndLine, selectionManager.selEndChar);

      scrollManager.scrollToLineFastForSelectAll(selectionManager.selEndLine, selectionManager.selEndChar);

      setDisable(false);
      loadingCircleManager.show(false);
      invalidate();
      requestFocus();
      popupMenuManager.showPopupAtSelection();

      post(
          () -> {
            requestFocus();
            if (keyboardWasVisible) imeManager.showKeyboard();
            InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(SodiumEditorView.this);
          });
      return;
    }

    // If we're already at EOF, we can select to the current visible logical end
    // without waiting for the index (important when user appended lines after EOF).
    if (isEof) {
      int windowLast = Math.max(0, windowStartLine + linesWindow.size() - 1);
      selectionManager.selEndLine = windowLast;
      String lastLineText = getLineTextForRender(windowLast);
      selectionManager.selEndChar = lastLineText.length();
      cursorManager.setLineAndChar(windowLast, selectionManager.selEndChar);

      scrollManager.scrollToLineFastForSelectAll(windowLast, selectionManager.selEndChar);

      setDisable(false);
      loadingCircleManager.show(false);
      invalidate();
      requestFocus();
      popupMenuManager.showPopupAtSelection();

      post(
          () -> {
            requestFocus();
            if (keyboardWasVisible) imeManager.showKeyboard();
            InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(SodiumEditorView.this);
          });
      return;
    }

    // الأفضل: لو index جاهز نروح نهاية الملف بدقة (بدون قفزة غلط)
    Runnable goToEndUsingIndex =
        () -> {
          if (!isIndexReady || sourceFile == null) return;

          int fileLastLine;
          synchronized (lineOffsetsLock) {
            fileLastLine = Math.max(0, lineOffsets.length - 1);
          }

          // If the current window actually goes beyond file end (due to appended in-memory lines),
          // prefer the window end and DO NOT reload from file (reload would drop the appended
          // lines).
          if (isEof) {
            int windowLast = Math.max(0, windowStartLine + linesWindow.size() - 1);
            if (windowLast > fileLastLine) {
              selectionManager.selEndLine = windowLast;
              String lastLineText = getLineTextForRender(windowLast);
              selectionManager.selEndChar = lastLineText.length();
              cursorManager.setLineAndChar(windowLast, selectionManager.selEndChar);

              scrollManager.scrollToLineFastForSelectAll(windowLast, selectionManager.selEndChar);

              setDisable(false);
              loadingCircleManager.show(false);
              invalidate();
              requestFocus();
              popupMenuManager.showPopupAtSelection();

              post(
                  () -> {
                    requestFocus();
                    if (keyboardWasVisible) imeManager.showKeyboard();
                    InputMethodManager imm =
                        (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(SodiumEditorView.this);
                  });
              return;
            }
          }

          selectionManager.selEndLine = fileLastLine;

          int targetStart = Math.max(0, fileLastLine - prefetchLines);

          loadWindowAround(
              targetStart,
              () ->
                  post(
                      () -> {
                        String lastLineText = getLineTextForRender(fileLastLine);
                        selectionManager.selEndChar = lastLineText.length();
                        cursorManager.setLineAndChar(fileLastLine, selectionManager.selEndChar);

                        scrollManager.scrollToLineFastForSelectAll(fileLastLine, selectionManager.selEndChar);

                        setDisable(false);
                        loadingCircleManager.show(false);
                        invalidate();
                        requestFocus();
                        popupMenuManager.showPopupAtSelection();

                        post(
                            () -> {
                              requestFocus();
                              if (keyboardWasVisible) imeManager.showKeyboard();
                              InputMethodManager imm =
                                  (InputMethodManager)
                                      getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                              if (imm != null) imm.restartInput(SodiumEditorView.this);
                            });
                      }));
        };

    if (isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    // لو index مو جاهز: ابدأ بناءه ثم انتظر جاهزيته (بدل "قرب النهاية" الغلط)
    if (!isIndexBuilding && !isIndexDisabled) ioHandler.post(this::buildFileIndex);

    // نحدد selectionManager.selEndLine مؤقتاً للهايلايت بواسطة countTotalLines (سريع)
    countTotalLines(
        totalLines -> {
          int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
          selectionManager.selEndLine = Math.max(0, lastLine);

          Runnable goToEndWithoutIndex =
              () -> {
                int targetStart = Math.max(0, selectionManager.selEndLine - prefetchLines);
                loadWindowAround(
                    targetStart,
                    () ->
                        post(
                            () -> {
                              String lastLineText = getLineTextForRender(selectionManager.selEndLine);
                              selectionManager.selEndChar = lastLineText.length();
                              cursorManager.setLineAndChar(selectionManager.selEndLine, selectionManager.selEndChar);

                              scrollManager.scrollToLineFastForSelectAll(selectionManager.selEndLine, selectionManager.selEndChar);

                              setDisable(false);
                              loadingCircleManager.show(false);
                              invalidate();
                              requestFocus();
                              popupMenuManager.showPopupAtSelection();

                              post(
                                  () -> {
                                    requestFocus();
                                    if (keyboardWasVisible) imeManager.showKeyboard();
                                    InputMethodManager imm =
                                        (InputMethodManager)
                                            getContext()
                                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.restartInput(SodiumEditorView.this);
                                  });
                            }));
              };

          if (isIndexDisabled) {
            goToEndWithoutIndex.run();
            return;
          }

          final int ticket = undoRedo.incrementEditVersion();
          Runnable poll =
              new Runnable() {
                @Override
                public void run() {
                  if (ticket != undoRedo.getEditVersion()) return;

                  // Important: if file became unavailable (e.g. cleared and switched to memory),
                  // stop waiting to avoid infinite spinner.
                  if (fileManager.getSourceFile() == null) {
                    setDisable(false);
                    loadingCircleManager.show(false);
                    invalidate();
                    popupMenuManager.showPopupAtSelection();
                    if (keyboardWasVisible) imeManager.showKeyboard();
                    return;
                  }

                  if (isIndexDisabled) {
                    goToEndWithoutIndex.run();
                  } else if (isIndexReady) {
                    goToEndUsingIndex.run();
                  } else {
                    mainHandler.postDelayed(this, 80);
                  }
                }
              };
          mainHandler.post(poll);
        });
  }

  public void actionSelectAll() {
    selectAll();
  }

  // ==============================
  // DELETE/REPLACE SELECTION (FIXED)
  // ==============================
  public void deleteSelection() {
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when deleting selection
    replaceSelectionWithText("");
  }

  public void actionDelete() {
    deleteSelection();
  }

  static final class CursorTarget {
    final int line;
    final int ch;

    CursorTarget(int line, int ch) {
      this.line = line;
      this.ch = ch;
    }
  }

  // Undo/redo helpers moved to UndoRedo.

  public String exportEditCacheJson() {
    return undoRedo.exportEditCacheJson();
  }

  public boolean importEditCacheJson(String json, boolean applyPendingEdits) {
    return undoRedo.importEditCacheJson(json, applyPendingEdits);
  }

  public boolean hasPendingEdits() {
    return undoRedo.hasPendingEdits();
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

  CursorTarget computeCursorAfterInsertForUndo(int baseLine, int baseChar, String insertText) {
    return computeCursorAfterInsert(baseLine, baseChar, insertText);
  }

  int countNewlinesForUndo(@Nullable String text) {
    return countNewlines(text);
  }

  public boolean canUndo() {
    return undoRedo.canUndo();
  }

  public boolean canRedo() {
    return undoRedo.canRedo();
  }

  public int getUndoStackSize() {
    return undoRedo.getUndoStackSize();
  }

  public int getPendingEditsCount() {
    return undoRedo.getPendingEditsCount();
  }

  public void clearUndoRedoHistory() {
    undoRedo.clearUndoRedoHistory();
  }

  public long getLastEditTimestamp() {
    return undoRedo.getLastEditTimestamp();
  }

  public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    undoRedo.applyPendingEditsToFileAsync(onComplete);
  }

  // rewriteReplaceRangeBlocking moved to UndoRedo.

  public void recordEdit(UndoRedo.EditOp op) {
    undoRedo.recordEdit(op);
  }

  private void recordEditNoUndo(UndoRedo.EditOp op) {
    undoRedo.recordEditNoUndo(op);
  }

  private void recordReplaceSelectionEdit(
      int sL,
      int sC,
      int eL,
      int eC,
      @Nullable String removedText,
      @Nullable String insertText,
      int beforeLine,
      int beforeChar) {
    undoRedo.recordReplaceSelectionEdit(
        sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  public void undo() {
    undoRedo.undo();
  }

  public void redo() {
    undoRedo.redo();
  }

  private void applyEditForUndoRedo(
      int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    replaceSelectionWithText(text);
    cursorManager.setPosition(cursorLine, cursorChar);
    if (wordWrapManager.isWordWrapEnabled) {
      wordWrapManager.invalidateWrapMetrics(this, true);
      wordWrapManager.requestWrapPrefixRebuild(this);
    }
    lineNumberManager.invalidateCache();
    invalidate();
  }

  void setSelectionInternal(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }
    selectionManager.setSelection(
        startL,
        Math.max(0, startC),
        endL,
        Math.max(0, endC),
        false);
    selectionManager.setSelectAllState(false, false);
    popupMenuManager.hidePopup();
  }

  void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    undoRedo.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  private String readRangeText(int sL, int sC, int eL, int eC) {
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

    if (fileManager.getSourceFile() == null || !fileManager.getSourceFile().exists()) return "";
    RangeBytes range = computeByteRangeFastOrScan(fileManager.getSourceFile(), startL, startC, endL, endC);
    if (range == null) return "";
    try (RandomAccessFile raf = new RandomAccessFile(fileManager.getSourceFile(), "r")) {
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
      return new String(buf, fileManager.fileCharset);
    } catch (Exception ignore) {
      return "";
    }
  }

  void replaceSelectionWithText(String insertText) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    final int opToken = undoRedo.incrementEditVersion();
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when replacing selection

    if (insertText == null) insertText = "";

    if (!selectionManager.hasSelection()) {
      if (!insertText.isEmpty()) cursorManager.insertTextAtCursor(insertText);
      // No selection means no large edit UI was started for it.
      autoSuggestionManager.updateSuggestion();
      return;
    }

    // Normalize selection
    int sL = selectionManager.selStartLine, sC = selectionManager.selStartChar, eL = selectionManager.selEndLine, eC = selectionManager.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = cursorManager.getLine();
    final int beforeChar = cursorManager.getChar();
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > undoRedo.getUndoTextLimit()) {
        removedText = null;
      }
    }
    int removedNewlines = countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = countNewlines(insertText);

    final boolean selectAllLike =
        selectionManager.isSelectAllActive() || selectionManager.isEntireFileSelected();
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
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;

      // Transition to in-memory mode for cleared content.
      fileManager.setFileCleared(true);
      synchronized (fileManager.lineOffsetsLock) {
        fileManager.setLineOffsets(new long[0]);
      }
      fileManager.isIndexReady = false;
      fileManager.isIndexBuilding = false;
      fileManager.isIndexDisabled = false;
      fileManager.indexDisabledPath = null;
      fileManager.indexDisabledFileLength = -1L;

      // Reset cursor, selection, and scroll position.
      cursorManager.setLineAndChar(0, 0);
      selectionManager.setSelection(0, 0, 0, 0, false);
      scrollManager.scrollY = 0;
      scrollManager.scrollX = 0;
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
        cursorManager.setLineAndChar(newPos.line, newPos.ch);
      }

      // Crucially, end the large edit UI and force a redraw.
      wordWrapManager.onLineCountChanged(this);
      endLargeEditUi(true);
      recalculateMaxLineWidth();
      scrollManager.keepCursorVisibleHorizontally();
      requestLayout(); // Request layout to update gutter width after content cleared
      autoSuggestionManager.updateSuggestion();
      undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // same line + no '\n' => window-only fast path
    if (sL == eL && insertText.indexOf('\n') < 0) {
      scrollManager.ensureLineInWindow(sL, true);
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

          cursorManager.setLineAndChar(sL, a + insertText.length());

          computeWidthForLine(sL, merged);
          recalculateMaxLineWidth();
        }
      }

      clearSelectionStateAfterDelete();
      invalidate();
      scrollManager.keepCursorVisibleHorizontally();
      endLargeEditUi(false);
      autoSuggestionManager.updateSuggestion();
      undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
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
      cursorManager.setLineAndChar(sL, sC);
    }

    clearSelectionStateAfterDelete();
    scrollManager.keepCursorVisibleHorizontally(); // This scrolls to the new cursor and invalidates.
    endLargeEditUi(false);

    if (fileManager.getSourceFile() == null || fileManager.isFileCleared()) {
      if (!fullyInWindow) {
        scrollManager.ensureLineInWindow(sL, true);
        scrollManager.ensureLineInWindow(eL, true);
        applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      autoSuggestionManager.updateSuggestion();
      undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = fileManager.getSourceFile();
    // ابدأ إعادة كتابة الملف في الخلفية بدون تعطيل الواجهة وبدون دائرة تحميل.
    rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    autoSuggestionManager.updateSuggestion();
    undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
    recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  private void applyMultiLineReplaceInWindowNow(
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

      cursorManager.setLineAndChar(Math.max(0, target.line), Math.max(0, target.ch));

      int newLineCount = getLinesCount();
      if (lineNumberManager.isShowLineNumbers()
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      wordWrapManager.onLineCountChanged(this);

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
                  if (opToken != undoRedo.getEditVersion()) return;

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
                    fileManager.updateSourceFile(inFile);
                  } else {
                    fileManager.updateSourceFile(outFile);
                  }
                  fileManager.setFileCleared(false);

                  synchronized (modifiedLines) {
                    modifiedLines.clear();
                  }
                  synchronized (lineWidthCache) {
                    lineWidthCache.clear();
                  }
                  currentMaxWindowLineWidth = 0f;
                  globalMaxLineWidth = 0f;
                  scrollManager.maxLineWidthForScroll = 0f;
                  scrollManager.maxTextStartXForScroll = 0f;
                  scrollManager.maxScrollXForScroll = 0f;
                  undoRedo.resetLineCountDelta();

                  synchronized (lineOffsetsLock) {
                    lineOffsets = new long[0];
                  }
                  fileManager.isIndexReady = false;
                  fileManager.isIndexBuilding = false;
                  fileManager.isIndexDisabled = false;
                  fileManager.indexDisabledPath = null;
                  fileManager.indexDisabledFileLength = -1L;
                  fileManager.setEof(false);

                  ioHandler.post(this::buildFileIndex);
                  wordWrapManager.onLineCountChanged(this);

                  cursorManager.setLineAndChar(Math.max(0, target.line), Math.max(0, target.ch));

                  // لا تعمل "Reload" للنافذة بعد الحذف/الاستبدال إذا كانت النتيجة ضمن النافذة
                  // الحالية.
                  // هذا يمنع دائرة التحميل ويمنع القفز/الزمن الطويل مع الملفات الضخمة.
                  boolean cursorInsideWindow =
                      (cursorManager.getLine() >= windowStartLine
                          && cursorManager.getLine() < windowStartLine + linesWindow.size());

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
                    int targetStart = Math.max(0, cursorManager.getLine() - prefetchLines);
                    loadWindowAround(
                        targetStart,
                        () -> {
                          String ln = getLineTextForRender(cursorManager.getLine());
                          cursorManager.clampCharToLineLength(cursorManager.getLine());
                          clampScrollY();
                          scrollManager.keepCursorVisibleHorizontally();
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

  private RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
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

  RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
    return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  Handler getIoHandlerForUndo() {
    return ioHandler;
  }

  void onUndoRedoRewriteSuccess(File inFile) {
    fileManager.updateSourceFile(inFile);
    synchronized (fileManager.lineOffsetsLock) {
      fileManager.setLineOffsets(new long[0]);
    }
    fileManager.isIndexReady = false;
    fileManager.isIndexBuilding = false;
    fileManager.isIndexDisabled = false;
    fileManager.indexDisabledPath = null;
    fileManager.indexDisabledFileLength = -1L;
    ioHandler.post(this::buildFileIndex);
  }

  private RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long startLineByte, endLineByte;
      synchronized (fileManager.lineOffsetsLock) {
        if (!fileManager.isIndexReady()) return null;
        if (sL < 0 || eL < 0) return null;
        if (sL >= fileManager.getLineOffsets().length || eL >= fileManager.getLineOffsets().length) return null;
        startLineByte = fileManager.getLineOffsets()[sL];
        endLineByte = fileManager.getLineOffsets()[eL];
      }

      String startLineText = fileManager.readLineUtf8AtByte(raf, startLineByte);
      String endLineText = (eL == sL) ? startLineText : fileManager.readLineUtf8AtByte(raf, endLineByte);

      long startByte = startLineByte + fileManager.computeByteOffsetInLineUtf8(startLineText, sC);
      long endByte = endLineByte + fileManager.computeByteOffsetInLineUtf8(endLineText, eC);

      return new RangeBytes(startByte, endByte);
    } catch (Exception ignore) {
      return null;
    }
  }

  private void applyMultiLineDeleteInWindowNow(int sL, int sC, int eL, int eC) {
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

      cursorManager.setLineAndChar(sL, left.length());

      recalculateMaxLineWidth();
      int newLineCount = getLinesCount();
      if (oldLineCount != newLineCount) {
        wordWrapManager.onLineCountChanged(this);
      }
    }
  }

  private void clearSelectionStateAfterDelete() {
    selectionManager.clearSelection();
    popupMenuManager.hidePopup();
    cursorAnimationManager.resetCursorBlink();
  }

  static final class RangeBytes {
    final long startByte, endByte;

    RangeBytes(long s, long e) {
      startByte = s;
      endByte = e;
    }
  }

  private void transferRange(FileChannel inCh, FileChannel outCh, long position, long count)
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

  private RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
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
  private long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    if (targetLine <= 0) return 0L;
    long[] starts = findTwoLineStartBytesByScanning(raf, targetLine, targetLine);
    return (starts != null && starts.length > 0) ? starts[0] : 0L;
  }

  private long[] findTwoLineStartBytesByScanning(RandomAccessFile raf, int lineA, int lineB)
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

  String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
    return fileManager.readLineUtf8AtByte(raf, byteOffset);
  }

  private long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
    long start;
    long end;
    synchronized (fileManager.lineOffsetsLock) {
      if (line < 0 || line >= fileManager.getLineOffsets().length) return 0L;
      start = fileManager.getLineOffsets()[line];
      end = (line + 1 < fileManager.getLineOffsets().length) ? fileManager.getLineOffsets()[line + 1] : fileLen;
    }
    long len = Math.max(0L, end - start);
    if (len <= 0L) return 0L;
    if (line + 1 < fileManager.getLineOffsets().length) {
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

  private String readLineSliceAtByte(
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
    return new String(buf, fileManager.fileCharset);
  }

  private static final class StreamedCharSlice {
    final String text;
    final int length;

    StreamedCharSlice(String text, int length) {
      this.text = text;
      this.length = length;
    }
  }

  private StreamedCharSlice readLineSliceByChars(
      RandomAccessFile raf, long lineStart, int startChar, int endChar, boolean needTotalLength)
      throws Exception {
    int safeStart = Math.max(0, startChar);
    int safeEnd = Math.max(safeStart, endChar);
    CharsetDecoder decoder = fileManager.fileCharset.newDecoder();
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

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return fileManager.computeByteOffsetInLineUtf8(lineText, charIndex);
  }

  private int getCharIndexForX(String text, float x, int globalLine) {
    return viewRender.getCharIndexForX(text, x, globalLine);
  }

  int[] computeWordBounds(String line, int pos) {
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

  private boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  private int[] computeWordBoundsSmart(String line, int pos) {
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

  private static final String[] CONTROL_TOKENS =
      new String[] {
        "<NUL>", "<SOH>", "<STX>", "<ETX>", "<EOT>", "<ENQ>", "<ACK>", "<BEL>",
        "<BS>", "<TAB>", "<LF>", "<VT>", "<FF>", "<CR>", "<SO>", "<SI>",
        "<DLE>", "<DC1>", "<DC2>", "<DC3>", "<DC4>", "<NAK>", "<SYN>", "<ETB>",
        "<CAN>", "<EM>", "<SUB>", "<ESC>", "<FS>", "<GS>", "<RS>", "<US>"
      };

  private String bytesToControlVisible(byte[] buf, int len) {
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

  private boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    int[] bounds = computeWordBoundsSmart(lineText, charIndex);
    ArrayList<TextRange> candidates =
        buildDoubleTapCandidates(lineText, charIndex, bounds[0], bounds[1]);
    if (candidates.isEmpty()) return false;

    boolean sameAnchor =
        line == lastDoubleTapLine
            && bounds[0] == lastDoubleTapWordStart
            && bounds[1] == lastDoubleTapWordEnd;
    int currentIdx = findSelectionCandidateIndex(line, candidates);
    int nextIdx;
    if (sameAnchor) {
      if (currentIdx >= 0) {
        nextIdx = Math.min(currentIdx + 1, candidates.size() - 1);
      } else {
        nextIdx = Math.min(lastDoubleTapStage + 1, candidates.size() - 1);
      }
    } else {
      nextIdx = 0;
    }

    TextRange pick = candidates.get(nextIdx);
    selectionManager.setSelection(line, pick.start, line, pick.end, true);
    selectionManager.setSelectAllState(false, false);
    cursorManager.setLineAndChar(line, selectionManager.selEndChar);
    lastDoubleTapLine = line;
    lastDoubleTapWordStart = bounds[0];
    lastDoubleTapWordEnd = bounds[1];
    lastDoubleTapStage = nextIdx;
    return true;
  }

  void cancelStretchRelease() {
    if (scrollManager.stretchReleaseAnimator != null) {
      scrollManager.stretchReleaseAnimator.cancel();
      scrollManager.stretchReleaseAnimator = null;
    }
  }

  void releaseStretch() {
    if (!scrollManager.stretchOverscrollEnabled) return;
    if (scrollManager.stretchX == 0f && scrollManager.stretchY == 0f) return;
    cancelStretchRelease();
    final float startX = scrollManager.stretchX;
    final float startY = scrollManager.stretchY;
    scrollManager.stretchReleaseAnimator = ValueAnimator.ofFloat(0f, 1f);
    scrollManager.stretchReleaseAnimator.setDuration(220);
    scrollManager.stretchReleaseAnimator.setInterpolator(new DecelerateInterpolator());
    scrollManager.stretchReleaseAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          float inv = 1f - t;
          scrollManager.stretchX = startX * inv;
          scrollManager.stretchY = startY * inv;
          if (scrollManager.stretchX == 0f) scrollManager.stretchDirX = 0;
          if (scrollManager.stretchY == 0f) scrollManager.stretchDirY = 0;
          invalidate();
        });
    scrollManager.stretchReleaseAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            scrollManager.stretchReleaseAnimator = null;
            scrollManager.stretchX = 0f;
            scrollManager.stretchY = 0f;
            scrollManager.stretchDirX = 0;
            scrollManager.stretchDirY = 0;
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            scrollManager.stretchReleaseAnimator = null;
          }
        });
    scrollManager.stretchReleaseAnimator.start();
  }


  void pullStretchX(float deltaPx, boolean toRight) {
    if (!scrollManager.stretchOverscrollEnabled || wordWrapManager.isWordWrapEnabled) return;
    if (getWidth() <= 0) return;
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) getWidth();
    float gain = norm * 0.6f * scrollManager.stretchOverscrollStrength;
    scrollManager.stretchDirX = toRight ? 1 : -1;
    scrollManager.stretchX = Math.min(1f, scrollManager.stretchX + gain);
  }

  void pullStretchY(float deltaPx, boolean toBottom) {
    if (!scrollManager.stretchOverscrollEnabled) return;
    if (getHeight() <= 0) return;
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) getHeight();
    float gain = norm * 0.6f * scrollManager.stretchOverscrollStrength;
    scrollManager.stretchDirY = toBottom ? 1 : -1;
    scrollManager.stretchY = Math.min(1f, scrollManager.stretchY + gain);
  }


  private void absorbStretchX(float velocityPxPerSec, boolean toRight) {
    if (!scrollManager.stretchOverscrollEnabled || wordWrapManager.isWordWrapEnabled) return;
    cancelStretchRelease();
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    scrollManager.stretchDirX = toRight ? 1 : -1;
    scrollManager.stretchX = Math.min(1f, scrollManager.stretchX + v * 0.8f * scrollManager.stretchOverscrollStrength);
  }

  private void absorbStretchY(float velocityPxPerSec, boolean toBottom) {
    if (!scrollManager.stretchOverscrollEnabled) return;
    cancelStretchRelease();
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    scrollManager.stretchDirY = toBottom ? 1 : -1;
    scrollManager.stretchY = Math.min(1f, scrollManager.stretchY + v * 0.8f * scrollManager.stretchOverscrollStrength);
  }

  private boolean isPositionInsideSelection(int line, int ch) {
    if (!selectionManager.hasSelection()) return false;
    int sL = selectionManager.selStartLine;
    int sC = selectionManager.selStartChar;
    int eL = selectionManager.selEndLine;
    int eC = selectionManager.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selectionManager.selEndLine;
      sC = selectionManager.selEndChar;
      eL = selectionManager.selStartLine;
      eC = selectionManager.selStartChar;
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

  private void addSelectionCandidate(List<TextRange> out, int start, int end, int lineLen) {
    if (out == null) return;
    int s = Math.max(0, Math.min(start, lineLen));
    int e = Math.max(0, Math.min(end, lineLen));
    if (e <= s) return;
    for (TextRange r : out) {
      if (r.start == s && r.end == e) return;
    }
    out.add(new TextRange(s, e));
  }

  private int findSelectionCandidateIndex(int line, List<TextRange> candidates) {
    if (!selectionManager.hasSelection() || candidates == null || candidates.isEmpty()) return -1;
    int sL = selectionManager.selStartLine;
    int sC = selectionManager.selStartChar;
    int eL = selectionManager.selEndLine;
    int eC = selectionManager.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selectionManager.selEndLine;
      sC = selectionManager.selEndChar;
      eL = selectionManager.selStartLine;
      eC = selectionManager.selStartChar;
    }
    if (sL != line || eL != line) return -1;
    for (int i = 0; i < candidates.size(); i++) {
      TextRange r = candidates.get(i);
      if (r.start == sC && r.end == eC) return i;
    }
    return -1;
  }

  private ArrayList<TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
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

  private boolean isQuoteChar(char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  @Nullable
  private TextRange findEnclosingQuoteRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<TextRange> ranges = new ArrayList<>();
    char current = 0;
    int start = -1;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (current == 0) {
        if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !HighlightManager.isEscaped(line, i)) {
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
  private TextRange findEnclosingBracketRange(String line, int index) {
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
        if (c == currentQuote && !HighlightManager.isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
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

  void insertTextAtCursor(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  BufferedReader reopenReaderAtStart() {
    try {
      if (readerForFile != null) {
        try {
          readerForFile.close();
        } catch (Exception ignored) {
        }
        readerForFile = null;
      }
      if (fileManager.getSourceFile() != null) {
        readerForFile =
            new BufferedReader(new InputStreamReader(new FileInputStream(fileManager.getSourceFile()), fileManager.fileCharset));
        return readerForFile;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }


  private void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < linesWindow.size()) {
      linesWindow.set(localIdx, text);
      wordWrapManager.onLineContentChanged(this, windowStartLine + localIdx, text);
      clearStreamedLineInfo(windowStartLine + localIdx);
    }
  }

  String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= linesWindow.size()) return null;
    return linesWindow.get(localIdx);
  }

  private int getStreamLineThreshold() {
    return Math.max(4096, highlightManager.maxSyntaxLineLength);
  }

  private boolean shouldStreamLineLength(int length) {
    return fileManager.shouldStreamLineLength(length);
  }

  private int getStreamedLineLength(int globalLine) {
    return fileManager.getStreamedLineLength(globalLine);
  }

  int getStreamedLineSliceStart(int globalLine) {
    return fileManager.getStreamedLineSliceStart(globalLine);
  }

  private void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    fileManager.setStreamedLineInfo(globalLine, length, sliceStart);
  }

  public void clearStreamedLineInfo(int globalLine) {
    fileManager.clearStreamedLineInfo(globalLine);
  }

  public void clearStreamedLineCaches() {
    fileManager.clearStreamedLineCaches();
  }

  private boolean isSingleByteCharset() {
    return fileManager.isSingleByteCharset();
  }

  public int getLogicalLineLength(int globalLine, @Nullable String line) {
    return fileManager.getLogicalLineLength(globalLine, line);
  }

  private void computeWidthForLine(int globalIndex, String line) {
    viewRender.computeWidthForLine(globalIndex, line);
  }

  private float getWidthForLine(int globalIndex, String line) {
    return viewRender.getWidthForLine(globalIndex, line);
  }

  void handleAutoPairing(String text) {
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
      if (cursorManager.getChar() >= 2) {
        String ln = getLineTextForRender(cursorManager.getLine());
        if (ln != null && ln.length() >= cursorManager.getChar() && ln.charAt(cursorManager.getChar() - 2) == '/') {
          closing = "*/";
        }
      }
    }

    if (closing != null) {
      cursorManager.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        cursorManager.moveCursorLeft();
      }
    }
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return imeManager.onCheckIsTextEditor();
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return imeManager.onCreateInputConnection(outAttrs);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return inputManager.handleTouchEvent(event);
  }


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return inputManager.handleKeyDown(keyCode, event);
  }





  
  protected void superOnDraw(Canvas canvas) {
    super.onDraw(canvas);
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion on focus change
    InputMethodManager imm =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (focused) {
      if (imm != null) imm.restartInput(this);
      cursorAnimationManager.onFocusChanged(true);
    } else {
      if (hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
      cursorAnimationManager.onFocusChanged(false);
      cursorManager.setHasComposing(false);
      selectionManager.clearSelectionKeepLineNumberState();
      popupMenuManager.hidePopup();
    }
  }

  void invalidateLineGlobal(int globalLine) {
    viewRender.invalidateLineGlobal(globalLine);
  }



  boolean isHeavyDrawSuppressed() {
    return viewRender.isHeavyDrawSuppressed();
  }


  public long[] buildIndexJava(String path) {
    return viewRender.buildIndexJava(path);
  }


  public String getLineTextForRender(int line) {
    return viewRender.getLineTextForRender(line);
  }

  @Nullable
  public String getLineTextForRenderWithDirect(int line, @Nullable java.util.Map<Integer, String> direct) {
    return viewRender.getLineTextForRenderWithDirect(line, direct);
  }


  int getGlobalLineForY(float y) {
    return viewRender.getGlobalLineForY(y);
  }

  boolean isOpeningBracket(char c) {
    return c == '{' || c == '(' || c == '[';
  }

  char matchingBracket(char c) {
    if (c == '{') return '}';
    if (c == '(') return ')';
    if (c == '[') return ']';
    if (c == '}') return '{';
    if (c == ')') return '(';
    if (c == ']') return '[';
    return c;
  }

  public void populateDirectLinesForRange(int startLine, int endLine, java.util.Map<Integer, String> direct) {
    if (direct == null) return;
    int s = Math.max(0, Math.min(startLine, endLine));
    int e = Math.max(startLine, endLine);
    for (int line = s; line <= e; line++) {
      if (direct.containsKey(line)) continue;
      String text = getLineTextForRender(line);
      if (text == null) text = "";
      direct.put(line, text);
    }
  }

  
  public int getVisibleLineCount() {
    return viewRender.getVisibleLineCount();
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    return foldManager.mapVisibleIndexToGlobal(visibleIndex, total);
  }

  public int getVisibleIndexForGlobalLine(int globalLine) {
    return foldManager.getVisibleIndexForGlobalLine(globalLine);
  }

  int getVisualIndexForLineAndChar(int line, int ch) {
    if (!wordWrapManager.isWrapMetricsUsableForLine(this, line)) {
      if (foldManager.isCodeFoldingEnabled) return getVisibleIndexForGlobalLine(line);
      return Math.max(0, line);
    }
    int totalLines = wordWrapManager.wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = getLineTextForRender(safeLine);
    int[] starts = wordWrapManager.getWrapStartsForLine(this, safeLine, text);
    int seg = wordWrapManager.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    return wordWrapManager.wrapLinePrefix[safeLine] + seg;
  }

  public int getLinesCount() {
    return viewRender.getLinesCount();
  }

  public void clearComposingPendingOpPublic() {
    undoRedo.clearComposingPendingOp();
  }

  public int incrementEditVersionPublic() {
    return undoRedo.incrementEditVersion();
  }

  public void invalidatePendingIOForEditPublic() {
    invalidatePendingIOForEdit();
  }

  public void updateLocalLinePublic(int localIdx, String text) {
    updateLocalLine(localIdx, text);
  }

  public void computeWidthForLinePublic(int globalIndex, String line) {
    computeWidthForLine(globalIndex, line);
  }

  public void updateComposingPendingOpPublic(@Nullable String text, int beforeLine, int beforeChar) {
    undoRedo.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  public void cancelFlingStopAnimationPublic() {
    cancelFlingStopAnimation();
  }

  public float getDownXPublic() {
    return downX;
  }

  public void setDownXPublic(float value) {
    downX = value;
  }

  public float getDownYPublic() {
    return downY;
  }

  public void setDownYPublic(float value) {
    downY = value;
  }

  public float getMaxScrollYForClampPublic() {
    return getMaxScrollYForClamp();
  }

  public void startFlingStopAnimationPublic(float targetX, float targetY) {
    startFlingStopAnimation(targetX, targetY);
  }

  public ValueAnimator getFlingStopAnimatorPublic() {
    return flingStopAnimator;
  }

  public int getTouchSlopPublic() {
    return touchSlop;
  }

  public void updateLineNumberSelectionPublic(int line) {
    updateLineNumberSelection(line);
  }

  public CursorTarget getCursorTargetForPositionPublic(float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    return getCursorTargetForPosition(viewX, viewY, directLines);
  }

  public void restartInputPublic() {
    restartInput();
  }

  public boolean superOnTouchEventPublic(MotionEvent event) {
    return super.onTouchEvent(event);
  }
}
