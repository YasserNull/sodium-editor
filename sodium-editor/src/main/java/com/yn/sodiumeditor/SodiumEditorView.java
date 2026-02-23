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
import com.yn.sodiumeditor.CursorManager.BracketPairType;
import com.yn.sodiumeditor.input.InputManager;
import com.yn.sodiumeditor.input.InputMethodHandler;
import com.yn.sodiumeditor.renderer.ViewRender;
import com.yn.sodiumeditor.renderer.TextRender;

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
  public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 30; // 2000 yyy
  public int prefetchLines = 10; // 1000 yyy

  // IO
  private final HandlerThread ioThread;
  public final Handler ioHandler;
  public final FileManager fileManager;
  public volatile boolean isEof = false;
  public final AtomicInteger ioTaskVersion = new AtomicInteger(0);
  public File sourceFile = null;
  public boolean isFileCleared = false;
  public BufferedReader readerForFile = null;

  // caches
  public final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
  public final LinkedHashMap<Integer, Float> lineWidthCache;
  public int lineWidthCacheSize = 200; // 2000 yyy
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  
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
  // Charset is now managed by FileManager

  // --- Cursor Animation State (moved to CursorAnimationManager) ---
  private final InputManager inputManager;
  @Nullable ValueAnimator flingStopAnimator;
  static final long FLING_STOP_ANIM_DURATION_MS = 90;
  public final InputMethodHandler imeManager;
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
  public final WordWrapManager wordWrapManager = new WordWrapManager();
  // Search logic moved to SearchManager.
  // Search logic moved to SearchManager.



  // selection
  public int lastDoubleTapLine = -1;
  public int lastDoubleTapWordStart = -1;
  public int lastDoubleTapWordEnd = -1;
  public int lastDoubleTapStage = 0;

  // touch helpers
  public boolean pointerDown = false;
  public boolean movedSinceDown = false;
  public float downX = 0f, downY = 0f;
  public final int touchSlop;
  // Zoom multi-touch state moved to ZoomManager.

  // auto-scroll when dragging handles
  public final Handler mainHandler = new Handler(Looper.getMainLooper());

  // keyboard awareness
  private final Rect visibleDisplayFrame = new Rect();
  public int keyboardHeight = 0;

  // typed-character and deleted-character animations moved to CharAnimationManager.
  public boolean suppressNextCommitText = false;
  @Nullable public String lastImeCommitText;
  public long lastImeCommitUptime = 0L;

  // caret movement animation moved to CursorAnimationManager.

  // popup menu moved to PopupMenuManager.

  // selection handles
  private float baseCursorTextSizePx = 0f;
  public final IndentGuideManager indentGuideManager;

  public final WhitespaceGuideManager whitespaceGuideManager = new WhitespaceGuideManager();
  
  public final HandlesManager handlesManager = new HandlesManager(this);
  public final CursorManager cursorManager = new CursorManager(this);
  public final SelectionManager selectionManager = new SelectionManager(this);
  public final HighlightManager highlightManager = new HighlightManager(this);
  public final LineNumberManager lineNumberManager = new LineNumberManager(this);
  public final BracketGuideManager bracketGuideManager = new BracketGuideManager(this);
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
  public static final String INDENT_BLOCK_UNIT = "  ";
  static final int INDENT_FOLD_SCAN_LIMIT = 2000;



  // dragging handle state moved to HandlesManager
  public volatile boolean isWindowLoading = false;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;
  private final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables
  // loading circle state moved to LoadingCircleManager
  private boolean showLoadingOnFileOpen = true;
  public boolean isInitialFileOpenLoading = false;
  public int initialFileOpenToken = 0;
  public Runnable initialFileOpenShowSpinner = null;
  public final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
  public int maxWidthRecalcToken = 0;

  // index
  public final Object lineOffsetsLock = new Object();
  public long[] lineOffsets = new long[0];
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
  public boolean isStableGlyphPositionsEnabled = false;
  private boolean isClickAfterEndToAddLineEnabled = false;
  public boolean isAutoPairingEnabled = true;
  public boolean isAutoBracketNewlineEnabled = true;
  public boolean isAutoBracketNewlineIndentEnabled = true;
  public boolean isAutoIndentAfterClosingBracketEnabled = true;
  public boolean isIndentationBlocksEnabled = false;

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
    imeManager = new InputMethodHandler(this);
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
    viewRender.reloadWindowAroundVisible(false);
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
    inputManager.replaceSelectionWithText(text == null ? "" : text);
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
    return viewRender.computeMinWindowSizeForPrefetch(prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    return viewRender.computeMinWindowSizeForPrefetch(prefetch);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    viewRender.reloadWindowAroundVisible(recalcWidthSync);
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
    highlightManager.setBacktickStringsEnabled(enabled);
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
    selectionManager.restoreSelection(sL, sC, eL, eC, cursorLine, cursorChar);
  }

  public void showSelectionPopup() {
    popupMenuManager.showSelectionPopup();
  }

  // --- Convenience cursor/line accessors ---






  public void insertTextAt(int line, int col, String text) {
    cursorManager.insertTextAt(line, col, text);
  }

  public String getTextSnapshot() {
    return fileManager.getTextSnapshot();
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

  public void resetScrollLockAxisForInput() {
    scrollManager.scrollLockAxis = 0;
  }

  public void setJustFinishedScaleForInput(boolean finished) {
    zoomManager.setJustFinishedScale(finished);
  }

  public void abortScrollerForInput() {
    scrollManager.abortScroller();
  }

  public void setDownForInput(float x, float y) {
    downX = x;
    downY = y;
  }

  public void setMovedSinceDown(boolean moved) {
    movedSinceDown = moved;
  }

  public boolean isMovedSinceDown() {
    return movedSinceDown;
  }

  public boolean isLineNumberSelectionEnabledForInput() {
    return lineNumberManager.isLineNumberSelectionEnabled();
  }

  public boolean isInLineNumberGutterForInput(float x) {
    return isInLineNumberGutter(x);
  }

  public float getScrollYForInput() {
    return scrollManager.scrollY;
  }

  public void beginLineNumberSelectionForInput(int line) {
    beginLineNumberSelection(line);
  }

  public CursorTarget getCursorTargetForInput(float x, float y) {
    return getCursorTargetForPosition(x, y, null);
  }

  public CursorTarget getCursorTargetForHandles(float x, float y) {
    return getCursorTargetForPosition(x, y, null);
  }

  public HandlesManager getHandlesManagerForCursor() {
    return handlesManager;
  }

  public void ensureLineInWindowForInput(int line, boolean reload) {
    scrollManager.ensureLineInWindow(line, reload);
  }

  public String getLineFromWindowLocalForInput(int index) {
    return getLineFromWindowLocal(index);
  }

  public int getWindowStartLineForInput() {
    return windowStartLine;
  }

  public boolean applySmartDoubleTapSelectionForInput(int line, int ch, String ln) {
    return applySmartDoubleTapSelection(line, ch, ln);
  }

  public void clearSelectionForInput() {
    if (selectionManager.hasSelection()) {
      selectionManager.clearSelectionKeepLineNumberState();
    }
  }

  public boolean isCodeFoldingEnabledForInput() {
    return foldManager.isCodeFoldingEnabled;
  }

  public void startFoldMarkerRippleForInput(int line) {
    startFoldMarkerRipple(line);
  }

  public float getLineHeightForInput() {
    return lineHeight;
  }

  public int getTotalVisualLineCountForInput() {
    return wordWrapManager.getTotalVisualLineCount(this);
  }

  public int getVisibleLineCountForInput() {
    return getVisibleLineCount();
  }

  public float viewToTextXForInput(float x) {
    return viewToTextX(x);
  }

  public float measureTextWithVisualSpacesForInput(String s, int start, int end) {
    return whitespaceGuideManager.measureTextWithVisualSpaces(this, s, start, end, paint);
  }

  public boolean isFoldPlaceholderHitForInput(int line, String ln, float x) {
    return isFoldPlaceholderHit(line, ln, x);
  }

  public boolean isEofForInput() {
    return isEof;
  }

  public int getLinesWindowSizeForInput() {
    return linesWindow.size();
  }

  public boolean isLinesWindowEmptyForInput() {
    return linesWindow.isEmpty();
  }

  public boolean isClickAfterEndToAddLineEnabledForInput() {
    return isClickAfterEndToAddLineEnabled;
  }

  public void setCursorPositionForInput(int line, int ch) {
    cursorManager.setLineAndChar(line, ch);
  }

  public void insertTextAtCursorForInput(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  void insertStringAtCursorForSuggestion(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  public void setSelectingForInput(boolean selectingNow) {
    selectionManager.setSelecting(selectingNow);
  }

  public void updateSuggestionForInput() {
    autoSuggestionManager.updateSuggestion();
  }

  void restartInputForInput() {
    restartInput();
  }

  public void restartInputForSuggestion() {
    restartInput();
  }

  public boolean handleScrollFromInput(MotionEvent e2, float distanceX, float distanceY) {
    return scrollManager.onScroll(e2, distanceX, distanceY);
  }

  public boolean handleFlingFromInput(float velocityX, float velocityY) {
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

  public float viewToTextXPublic(float viewX) {
    return viewToTextX(viewX);
  }

  public float getTextAreaWidth() {
    return lineNumberManager.getTextAvailableWidth(getWidth(), paddingLeft);
  }

  public float getRtlLineBaseX(@Nullable String line, int globalLine) {
    return viewRender.textRender.getRtlLineBaseX(line, globalLine);
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    return viewRender.textRender.getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
  }

  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    return viewRender.textRender.getCaretXForLine(line, globalLine, charIndex);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    return viewRender.textRender.getCaretXForSegment(line, globalLine, segStart, segEnd, charIndex);
  }

  private int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    return viewRender.textRender.getCharIndexForXInRange(text, globalLine, start, end, x);
  }

  private CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    return viewRender.textRender.getCursorTargetForPosition(viewX, viewY, directLines);
  }

  public int getWindowEndLine() {
    synchronized (linesWindow) {
      return Math.max(0, windowStartLine + linesWindow.size() - 1);
    }
  }

  public static final class VisualLinePosition {
    public final int line;
    public final int segment;

    public VisualLinePosition(int line, int segment) {
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

  private void beginLineNumberSelection(int line) {
    int clamped = selectionManager.clampLineForSelection(this, line);
    if (!selectionManager.isLineSelectable(this, clamped)) return;
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
    int clamped = selectionManager.clampLineForSelection(this, line);
    if (!selectionManager.isLineSelectable(this, clamped)) return;
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
    viewRender.textRender.getVisibleCharRangeForLine(line, globalLine, out);
  }

  private void getVisibleCharRangeForLineFast(
      String line, int globalLine, int lineLength, int[] out) {
    viewRender.textRender.getVisibleCharRangeForLineFast(line, globalLine, lineLength, out);
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

  public int getInitialStreamedSliceSize() {
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

  public int getVisualSpaceScale() {
    return 1;
  }

  private boolean isWhitespaceAtX(String line, int globalLine, float x) {
    return viewRender.textRender.isWhitespaceAtX(line, globalLine, x);
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

  public static final class LineScanResult {
    public final long length;
    public final boolean reachedEof;

    public LineScanResult(long length, boolean reachedEof) {
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
    viewRender.maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);
  }

  public void maybeKickWindowLoad(int firstVisibleLine) {
    if (zoomManager.isZoomGestureActive()) return;
    if (fileManager.getSourceFile() == null || fileManager.isFileCleared()) {
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

  void checkAndLoadWindow() {
    viewRender.checkAndLoadWindow();
  }

  public void loadWindowAround(int startLine, @Nullable Runnable onComplete) {
    viewRender.loadWindowAround(startLine, onComplete);
  }

  public void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
    viewRender.loadWindowAround(startLine, onComplete, recalculateWidthSync);
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
    largeEditUiToken.incrementAndGet();
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    setDisable(false);
    loadingCircleManager.show(false);
    if (invalidate) invalidate();
  }

  public void endLargeEditUiPublic(boolean invalidate) {
    endLargeEditUi(invalidate);
  }

  public void clearSelectionStateAfterDeletePublic() {
    selectionManager.clearSelectionStateAfterDelete(this);
  }

  public void recordReplaceSelectionEditPublic(int sL, int sC, int eL, int eC, String removedText, String insertText, int beforeLine, int beforeChar) {
    undoRedo.recordReplaceSelectionEdit(
        sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  public void rewriteReplaceRangeAsyncPublic(int opToken, File inFile, int sL, int sC, int eL, int eC, String insertText, CursorTarget target, boolean finishLargeEditUi) {
    fileManager.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    selectionManager.setSelectionInternal(sL, sC, eL, eC);
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
      cursorManager.proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      countTotalLines(
          totalLines -> {
            if (currentGoToLineVersion != goToLineVersion.get()) return;
            int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
            int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
            cursorManager.proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
          });
    }
  }

  public void insertCharAtCursor(char c) {
    inputManager.insertCharAtCursor(c);
  }

  public void insertNewlineAtCursor() {
    inputManager.insertNewlineAtCursor();
  }

  public void deleteCharAtCursor() {
    inputManager.deleteCharAtCursor();
  }

  public void deleteForwardAtCursor() {
    inputManager.deleteForwardAtCursor();
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

  public int getIndentWidth(String line) {
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
    return selectionManager.buildSelectedTextBlocking(this, sL, sC, eL, eC, copyCutMaxChars);
  }

  public void copySelectionToClipboard() {
    selectionManager.copyOrCutSelection(this, false);
  }

  public void actionCopy() {
    copySelectionToClipboard();
  }

  public void cutSelectionToClipboard() {
    selectionManager.copyOrCutSelection(this, true);
  }

  public void actionCut() {
    cutSelectionToClipboard();
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
    fileManager.countTotalLines((total) -> callback.onResult(total));
  }

  public String readRangeText(int sL, int sC, int eL, int eC) {
    return fileManager.readRangeText(sL, sC, eL, eC);
  }

  public long computeByteRangeFastOrScanPublic(File file, int sL, int sC, int eL, int eC) {
    FileManager.RangeBytes range = fileManager.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
    return (range != null) ? range.startByte : 0;
  }

  public FileManager.RangeBytes computeByteRangeFastOrScanPublicFull(File file, int sL, int sC, int eL, int eC) {
    return fileManager.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public boolean isIndexBuildingPublic() {
    return isIndexBuilding;
  }

  public boolean isIndexDisabledPublic() {
    return isIndexDisabled;
  }

  public void selectAll() {
    selectionManager.selectAll(this);
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

  public static final class CursorTarget {
    public final int line;
    public final int ch;

    public CursorTarget(int line, int ch) {
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

  public void undo() {
    undoRedo.undo();
  }

  public void redo() {
    undoRedo.redo();
  }

  void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    undoRedo.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  public void replaceSelectionWithText(String insertText) {
    inputManager.replaceSelectionWithText(insertText);
  }

  private FileManager.RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    return fileManager.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  FileManager.RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
    return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  Handler getIoHandlerForUndo() {
    return ioHandler;
  }

  void onUndoRedoRewriteSuccess(File inFile) {
    fileManager.onUndoRedoRewriteSuccess(inFile);
  }

  private long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    return fileManager.findLineStartByteByScanning(raf, targetLine);
  }

  public String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
    return fileManager.readLineUtf8AtByte(raf, byteOffset);
  }

  public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
    return fileManager.getLineByteLengthFromIndex(raf, line, fileLen);
  }

  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
      throws Exception {
    return fileManager.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar);
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
    return fileManager.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength);
  }

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return fileManager.computeByteOffsetInLineUtf8(lineText, charIndex);
  }

  private int getCharIndexForX(String text, float x, int globalLine) {
    return viewRender.textRender.getCharIndexForX(text, x, globalLine);
  }

  public int getCharIndexForXPublic(String text, float x, int globalLine) {
    return getCharIndexForX(text, x, globalLine);
  }

  public int[] computeWordBounds(String line, int pos) {
    return viewRender.textRender.computeWordBounds(line, pos);
  }

  private boolean isWordChar(char c) {
    return viewRender.textRender.isWordChar(c);
  }

  private int[] computeWordBoundsSmart(String line, int pos) {
    return viewRender.textRender.computeWordBoundsSmart(line, pos);
  }

  public String bytesToControlVisible(byte[] buf, int len) {
    return fileManager.bytesToControlVisible(buf, len);
  }

  private boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    return viewRender.textRender.applySmartDoubleTapSelection(line, charIndex, lineText);
  }

  private boolean isPositionInsideSelection(int line, int ch) {
    return selectionManager.isPositionInsideSelection(line, ch);
  }

  public static final class TextRange {
    public final int start;
    public final int end;

    public TextRange(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  private void addSelectionCandidate(List<TextRange> out, int start, int end, int lineLen) {
    viewRender.textRender.addSelectionCandidate(out, start, end, lineLen);
  }

  private int findSelectionCandidateIndex(int line, List<TextRange> candidates) {
    return viewRender.textRender.findSelectionCandidateIndex(line, candidates);
  }

  private ArrayList<TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
    return viewRender.textRender.buildDoubleTapCandidates(line, charIndex, wStart, wEnd);
  }

  private boolean isQuoteChar(char c) {
    return viewRender.textRender.isQuoteChar(c);
  }

  @Nullable
  private TextRange findEnclosingQuoteRange(String line, int index) {
    return viewRender.textRender.findEnclosingQuoteRange(line, index);
  }

  @Nullable
  private TextRange findEnclosingBracketRange(String line, int index) {
    return viewRender.textRender.findEnclosingBracketRange(line, index);
  }

  public void insertTextAtCursor(String text) {
    cursorManager.insertTextAtCursor(text);
  }

  BufferedReader reopenReaderAtStart() {
    return fileManager.reopenReaderAtStart();
  }


  private void updateLocalLine(int localIdx, String text) {
    viewRender.textRender.updateLocalLine(localIdx, text);
  }

  public String getLineFromWindowLocal(int localIdx) {
    return viewRender.textRender.getLineFromWindowLocal(localIdx);
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
    viewRender.textRender.computeWidthForLine(globalIndex, line);
  }

  private float getWidthForLine(int globalIndex, String line) {
    return viewRender.textRender.getWidthForLine(globalIndex, line);
  }

  public void handleAutoPairing(String text) {
    inputManager.handleAutoPairing(text);
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





  
  public void superOnDraw(Canvas canvas) {
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

  public void invalidateLineGlobal(int globalLine) {
    viewRender.textRender.invalidateLineGlobal(globalLine);
  }



  boolean isHeavyDrawSuppressed() {
    return viewRender.textRender.isHeavyDrawSuppressed();
  }


  public long[] buildIndexJava(String path) {
    return viewRender.buildIndexJava(path);
  }

  public void buildFileIndex() {
    fileManager.buildFileIndex();
  }

  public void recalculateMaxLineWidth() {
    viewRender.textRender.recalculateMaxLineWidth();
  }

  public void recalculateMaxLineWidthAsync() {
    viewRender.textRender.recalculateMaxLineWidthAsync();
  }

  public void invalidatePendingIO() {
    invalidatePendingIOForEdit();
  }

  public String getLineTextForRender(int line) {
    return viewRender.textRender.getLineTextForRender(line);
  }

  @Nullable
  public String getLineTextForRenderWithDirect(int line, @Nullable java.util.Map<Integer, String> direct) {
    return viewRender.textRender.getLineTextForRenderWithDirect(line, direct);
  }


  public int getGlobalLineForY(float y) {
    return viewRender.textRender.getGlobalLineForY(y);
  }

  boolean isOpeningBracket(char c) {
    return BracketMatchManager.isOpeningBracket(c);
  }

  char matchingBracket(char c) {
    return BracketMatchManager.matchingBracket(c);
  }

  public void populateDirectLinesForRange(int startLine, int endLine, java.util.Map<Integer, String> direct) {
    viewRender.textRender.populateDirectLinesForRange(startLine, endLine, direct);
  }


  public int getVisibleLineCount() {
    return viewRender.textRender.getVisibleLineCount();
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    return foldManager.mapVisibleIndexToGlobal(visibleIndex, getLinesCount());
  }

  public int getVisibleIndexForGlobalLine(int globalLine) {
    return foldManager.getVisibleIndexForGlobalLine(globalLine);
  }

  public int getVisualIndexForLineAndChar(int line, int ch) {
    return viewRender.textRender.getVisualIndexForLineAndChar(line, ch);
  }

  public int getLinesCount() {
    return viewRender.textRender.getLinesCount();
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

  public int getGoToLineVersion() {
    return goToLineVersion.get();
  }

  public int getCopyCutMaxChars() {
    return copyCutMaxChars;
  }

  public long getCopyCutMaxLines() {
    return copyCutMaxLines;
  }

  public long findLineStartByteByScanningPublic(RandomAccessFile raf, int targetLine) throws Exception {
    return findLineStartByteByScanning(raf, targetLine);
  }
}
