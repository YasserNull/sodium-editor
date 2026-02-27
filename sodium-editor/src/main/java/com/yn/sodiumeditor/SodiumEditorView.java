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
import com.yn.sodiumeditor.utils.BracketFinder.BracketPairType;
import com.yn.sodiumeditor.core.EditOp;
import com.yn.sodiumeditor.input.InputManager;
import com.yn.sodiumeditor.input.InputMethodHandler;
import com.yn.sodiumeditor.io.Document;
import com.yn.sodiumeditor.io.LineIndex;
import com.yn.sodiumeditor.io.TextIO;
import com.yn.sodiumeditor.renderer.ViewRender;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.state.History;

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
  public final Document document;
  public final LineIndex lineIndex;
  public final TextIO textIO;
  // Legacy reference for backward compatibility
  public final Document fileManager;
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
  // Charset is now managed by Document

  // --- Cursor Animation State ---
  private final InputManager inputManager;
  @Nullable ValueAnimator flingStopAnimator;
  static final long FLING_STOP_ANIM_DURATION_MS = 90;
  public final InputMethodHandler imeManager;
  public final ScrollEngine scrollManager;

  // Zoom Components
  public final com.yn.sodiumeditor.config.ZoomConfig zoomConfig = new com.yn.sodiumeditor.config.ZoomConfig();
  public com.yn.sodiumeditor.input.ZoomGestureHandler zoomGestureHandler;
  public com.yn.sodiumeditor.core.ZoomEngine zoomEngine;
  public com.yn.sodiumeditor.renderer.ZoomPreviewRender zoomPreviewRender;

  public final History history;
  
  // Search Components
  public final com.yn.sodiumeditor.config.SearchConfig searchConfig = new com.yn.sodiumeditor.config.SearchConfig();
  public com.yn.sodiumeditor.core.SearchEngine searchEngine;
  public com.yn.sodiumeditor.input.SearchHandler searchHandler;
  public com.yn.sodiumeditor.renderer.SearchRenderer searchRenderer;
  
  public final com.yn.sodiumeditor.renderer.animation.CursorAnimator cursorAnimator;
  public final com.yn.sodiumeditor.renderer.animation.CharAnimator charAnimator;
  public final com.yn.sodiumeditor.config.CursorAnimationConfig cursorAnimationConfig;
  public final com.yn.sodiumeditor.config.CharAnimationConfig charAnimationConfig;
  public final PopupMenuManager popupMenuManager;
  public final AutoSuggestionManager autoSuggestionManager = new AutoSuggestionManager(this);

  // --- Zoom State (moved to ZoomManager) ---

  // Word Wrap Components
  public final com.yn.sodiumeditor.state.WrapWordState wrapWordState = new com.yn.sodiumeditor.state.WrapWordState();
  public final com.yn.sodiumeditor.state.WrapWordMetrics wrapWordMetrics = new com.yn.sodiumeditor.state.WrapWordMetrics();
  public com.yn.sodiumeditor.core.WrapWordEngine wrapWordEngine;
  public com.yn.sodiumeditor.core.WrapWordBuilder wrapWordBuilder;
  public com.yn.sodiumeditor.core.WrapWordMapper wrapWordMapper;
  public final com.yn.sodiumeditor.renderer.WrapWordIndicatorRender wrapWordIndicatorRender = new com.yn.sodiumeditor.renderer.WrapWordIndicatorRender();
  public com.yn.sodiumeditor.io.WrapWordDocument wrapWordDocument;



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

  // typed-character and deleted-character animations managed by CharAnimator.
  public boolean suppressNextCommitText = false;
  @Nullable public String lastImeCommitText;
  public long lastImeCommitUptime = 0L;

  // caret movement animation managed by CursorAnimator.

  // popup menu moved to PopupMenuManager.

  // selection handles
  private float baseCursorTextSizePx = 0f;
  public final IndentGuideManager indentGuideManager;

  public final WhitespaceGuideManager whitespaceGuideManager = new WhitespaceGuideManager();

  public final HandlesManager handlesManager = new HandlesManager(this);
  
  // Cursor Components
  public final com.yn.sodiumeditor.config.CursorConfig cursorConfig = new com.yn.sodiumeditor.config.CursorConfig();
  public com.yn.sodiumeditor.core.CursorNavigation cursorNavigation;
  public com.yn.sodiumeditor.renderer.CursorRenderer cursorRenderer;
  public com.yn.sodiumeditor.input.ImeCompositionHandler imeCompositionHandler;
  public com.yn.sodiumeditor.core.EditorTextInserter editorTextInserter;
  public final com.yn.sodiumeditor.state.CursorState cursorState = new com.yn.sodiumeditor.state.CursorState();

  // Selection Components
  public final com.yn.sodiumeditor.config.SelectionConfig selectionConfig = new com.yn.sodiumeditor.config.SelectionConfig();
  public com.yn.sodiumeditor.core.SelectionTextBuilder selectionTextBuilder;
  public com.yn.sodiumeditor.input.SelectionHandler selectionHandler;
  public com.yn.sodiumeditor.renderer.SelectionRenderer selectionRenderer;
  public final com.yn.sodiumeditor.state.SelectionState selectionState = new com.yn.sodiumeditor.state.SelectionState();
  
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
  public volatile boolean isIndexBuilding = false;
  public volatile boolean isIndexDisabled = false;
  @Nullable public volatile String indexDisabledPath = null;
  public volatile long indexDisabledFileLength = -1L;
  private static final long MAX_INDEX_BYTES_HARD = 64L * 1024 * 1024;

  // edit version + undo/redo state moved to History.

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

    selectionConfig.initPaints();
    selectionTextBuilder = new com.yn.sodiumeditor.core.SelectionTextBuilder(new SelectionTextBuilderCallback());
    selectionHandler = new com.yn.sodiumeditor.input.SelectionHandler(selectionConfig, selectionState, selectionTextBuilder, new SelectionHandlerCallback());
    selectionRenderer = new com.yn.sodiumeditor.renderer.SelectionRenderer(selectionConfig);

    // Initialize Cursor components
    cursorNavigation = new com.yn.sodiumeditor.core.CursorNavigation(cursorState, new CursorNavigationCallback());
    cursorRenderer = new com.yn.sodiumeditor.renderer.CursorRenderer(cursorConfig, cursorState, new CursorRendererCallback());
    imeCompositionHandler = new com.yn.sodiumeditor.input.ImeCompositionHandler(cursorState, new ImeCompositionCallback());
    editorTextInserter = new com.yn.sodiumeditor.core.EditorTextInserter(cursorState, new EditorTextInserterCallback());

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

    // Initialize Word Wrap components
    wrapWordDocument = new com.yn.sodiumeditor.io.WrapWordDocument(modifiedLines);
    wrapWordEngine = new com.yn.sodiumeditor.core.WrapWordEngine(wrapWordMetrics, whitespaceGuideManager);
    wrapWordMapper = new com.yn.sodiumeditor.core.WrapWordMapper(wrapWordMetrics, wrapWordEngine);
    wrapWordBuilder = new com.yn.sodiumeditor.core.WrapWordBuilder(wrapWordMetrics, wrapWordState, wrapWordEngine, wrapWordMapper, wrapWordDocument);
    wrapWordIndicatorRender.init(paint, density);

    touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
    imeManager = new InputMethodHandler(this);
    scrollManager = new ScrollEngine(this);

    // Initialize Zoom components
    zoomEngine = new com.yn.sodiumeditor.core.ZoomEngine();
    zoomPreviewRender = new com.yn.sodiumeditor.renderer.ZoomPreviewRender();
    zoomGestureHandler = new com.yn.sodiumeditor.input.ZoomGestureHandler(this, zoomConfig, zoomEngine, zoomPreviewRender, ctx);

    history = new History(this);
    
    // Initialize Search components
    searchEngine = new com.yn.sodiumeditor.core.SearchEngine(searchConfig, new SearchEngineCallback());
    searchHandler = new com.yn.sodiumeditor.input.SearchHandler(searchConfig, searchEngine, new SearchHandlerCallback());
    searchRenderer = new com.yn.sodiumeditor.renderer.SearchRenderer(searchConfig, searchEngine, new SearchRendererCallback());

    cursorAnimationConfig = new com.yn.sodiumeditor.config.CursorAnimationConfig();
    charAnimationConfig = new com.yn.sodiumeditor.config.CharAnimationConfig();
    cursorAnimator = new com.yn.sodiumeditor.renderer.animation.CursorAnimator(this, cursorAnimationConfig);
    charAnimator = new com.yn.sodiumeditor.renderer.animation.CharAnimator(this, charAnimationConfig);
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
    document = new Document(this);
    lineIndex = new LineIndex(this);
    textIO = new TextIO(this);
    fileManager = document; // Legacy reference for backward compatibility

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
    if (wrapWordState.isWordWrapEnabled) wrapWordBuilder.invalidate(true, true);
    wrapWordBuilder.requestPrefixRebuild(this);
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
      wrapWordIndicatorRender.setEnabled(false);
      autoSuggestionManager.setAutoCompletionEnabled(false);
      autoSuggestionManager.setAutoPathCompletionEnabled(false);
      charAnimationConfig.setEnabled(false);
      highlightManager.setHighlightCurrentLine(false);
      setIndentationBlocksEnabled(false);
      foldManager.setCodeFoldingEnabled(false);
    }
    invalidate();
  }









  private void insertStringAtCursor(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
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
    document.setFileCharset(charset);
  }

  public void setFileEncoding(@Nullable String charsetName) {
    document.setFileEncoding(charsetName);
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
    if (wrapWordState.isWordWrapEnabled) wrapWordBuilder.invalidate(true, true);
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
    selectionHandler.restoreSelection(sL, sC, eL, eC, cursorLine, cursorChar);
  }

  public void showSelectionPopup() {
    popupMenuManager.showSelectionPopup();
  }

  // --- Convenience cursor/line accessors ---






  public void insertTextAt(int line, int col, String text) {
    editorTextInserter.insertTextAt(line, col, text);
  }

  public String getTextSnapshot() {
    return textIO.getTextSnapshot();
  }

  float spToPx(float sp) {
    return sp * getResources().getDisplayMetrics().scaledDensity;
  }

  public float spToPxForZoom(float sp) {
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
    wrapWordIndicatorRender.updatePaintForTextSize(sizePx, paint);
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
    if (wrapWordState.isWordWrapEnabled) wrapWordBuilder.invalidate(true, !deferWrapRebuild);
    wrapWordBuilder.requestPrefixRebuild(this);
    invalidate();
  }

  public void applyZoomTextSizePx(float sizePx) {
    applyTextSizePx(sizePx);
  }

  public void applyZoomTextSizePx(float sizePx, boolean deferWrapRebuild) {
    applyTextSizePx(sizePx, deferWrapRebuild);
  }

  public float getPaintTextSizePxForZoom() {
    return paint.getTextSize();
  }

  public float getPaintFontSpacingPxForZoom() {
    return paint.getFontSpacing();
  }

  int getEditVersionForSearch() {
    return history.getEditVersion();
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
    return document.getSourceFile() != null && document.getSourceFile().exists();
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
    return history.getEditVersion();
  }

  String getLineTextForRenderWithDirectForMatch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  int getEditVersionForMatch() {
    return history.getEditVersion();
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
    zoomGestureHandler.setJustFinishedScale(finished);
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
    if (selectionState.hasSelection()) {
      selectionState.clearSelectionKeepLineNumberState();
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
    return wrapWordMapper.getTotalVisualLineCount(this, getVisibleLineCount());
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
    cursorState.setCursorPosition(line, ch);
  }

  public void insertTextAtCursorForInput(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  void insertStringAtCursorForSuggestion(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  public void setSelectingForInput(boolean selectingNow) {
    selectionState.setSelecting(selectingNow);
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
    wrapWordIndicatorRender.updateTypeface(paint);
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
    if (wrapWordState.isWordWrapEnabled) wrapWordBuilder.invalidate(true, true);
    wrapWordBuilder.requestPrefixRebuild(this);
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

    if (wrapWordState.isWordWrapEnabled && Math.abs(lineNumberManager.getGutterWidth() - oldGutterWidth) > 0.1f) {
      wrapWordBuilder.invalidate(true, true);
      wrapWordBuilder.requestPrefixRebuild(this);
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
    if (wrapWordState.isWordWrapEnabled && w != oldw) {
      wrapWordBuilder.invalidate(true, true);
      wrapWordBuilder.requestPrefixRebuild(this);
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
    int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(line, isEof, windowStartLine, linesWindow.size());
    if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(getLineTextForRender(clamped))) return;
    autoSuggestionManager.clearActiveSuggestion();
    selectionState.setLineNumberSelecting(true, clamped);
    selectionState.setSelectAllState(false, false);
    String lineText = getLineTextForRender(clamped);
    selectionState.setSelection(clamped, 0, clamped, lineText.length(), true);
    cursorState.setCursorPosition(clamped, selectionState.selEndChar);
    popupMenuManager.hidePopup();
    cursorAnimator.resetCursorBlink();
    invalidate();
  }

  private void updateLineNumberSelection(int line) {
    if (!selectionState.isLineNumberSelecting()) return;
    int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(line, isEof, windowStartLine, linesWindow.size());
    if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(getLineTextForRender(clamped))) return;
    int anchorLine = selectionState.getLineNumberSelectAnchorLine();
    int startLine = Math.min(anchorLine, clamped);
    int endLine = Math.max(anchorLine, clamped);
    scrollManager.ensureLineInWindow(endLine, true);
    String endLineText = getLineTextForRender(endLine);
    selectionState.setSelection(startLine, 0, endLine, endLineText.length(), true);
    cursorState.setCursorPosition(endLine, selectionState.selEndChar);
    selectionState.setLineNumberSelecting(true, anchorLine);
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
    if (!charAnimationConfig.isEnabled()) return;
    if (globalLine != charAnimator.getDelAnimLine()
        || charAnimator.getDelAnimText() == null
        || charAnimator.getDelAnimText().isEmpty()
        || charAnimator.getDelAnimAlpha() <= 0f) return;
    if (line == null) line = "";
    int at = Math.max(0, Math.min(charAnimator.getDelAnimAtChar(), line.length()));
    if (at < segStart || at > segEnd) return;
    float x = whitespaceGuideManager.measureTextWithVisualSpaces(this, line, segStart, at, paint);
    Paint ghostPaint = (charAnimator.getDelAnimPaint() != null) ? charAnimator.getDelAnimPaint() : paint;
    Paint tempPaint = charAnimator.getTempPaint();
    tempPaint.set(ghostPaint);
    tempPaint.setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimator.getDelAnimAlpha()))));
    canvas.drawText(charAnimator.getDelAnimText(), x, y, tempPaint);
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
    return com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
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
    if (zoomGestureHandler.isZoomGestureActive()) return;
    if (document.getSourceFile() == null || document.isFileCleared()) {
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
    if (!selectionState.hasSelection()) return true;

    int sL = selectionState.selStartLine, eL = selectionState.selEndLine;
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

  public void clampScrollY() {
    scrollManager.clampScrollY();
  }

  public void abortScrollAnimationForZoom() {
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
    document.clearContent();
  }

  public void loadFromFile(final File file) {
    document.loadFromFile(file);
  }

  public void updateSourceFile(File file) {
    document.updateSourceFile(file);
  }

  public int getEditVersionValue() {
    return history.getEditVersion();
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
      selectionState.clearSelectionKeepLineNumberState();
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
    document.setShowLoadingOnFileOpen(enabled);
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
    selectionHandler.clearSelectionStateAfterDelete();
  }

  public void recordReplaceSelectionEditPublic(int sL, int sC, int eL, int eC, String removedText, String insertText, int beforeLine, int beforeChar) {
    history.recordReplaceSelectionEdit(
        sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  public void rewriteReplaceRangeAsyncPublic(int opToken, File inFile, int sL, int sC, int eL, int eC, String insertText, CursorTarget target, boolean finishLargeEditUi) {
    textIO.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    selectionHandler.setSelectionInternal(sL, sC, eL, eC);
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

    if (selectionState.hasSelection()) {
      selectionState.clearSelectionKeepLineNumberState();
      selectionState.setSelecting(false);
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
      cursorNavigation.proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      countTotalLines(
          totalLines -> {
            if (currentGoToLineVersion != goToLineVersion.get()) return;
            int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
            int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
            cursorNavigation.proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
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
        width += com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
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
    if (!selectionState.hasSelection()) return null;
    if (shouldHideCopyCutForSelection()) return null;

    int sL = selectionState.selStartLine, sC = selectionState.selStartChar, eL = selectionState.selEndLine, eC = selectionState.selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    return selectionTextBuilder.buildSelectedTextBlocking(sL, sC, eL, eC);
  }

  public void copySelectionToClipboard() {
    selectionHandler.copyOrCutSelection(false);
  }

  public void actionCopy() {
    copySelectionToClipboard();
  }

  public void cutSelectionToClipboard() {
    selectionHandler.copyOrCutSelection(true);
  }

  public void actionCut() {
    cutSelectionToClipboard();
  }

  public void pasteFromClipboard() {
    invalidatePendingIOForEdit();
    history.incrementEditVersion();
    autoSuggestionManager.clearActiveSuggestion(); // Clear suggestion when pasting

    ClipboardManager cm =
        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(getContext());
    if (txt == null) return;
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(txt.toString());
    autoSuggestionManager.updateSuggestion(); // Update suggestion after pasting
  }

  public void actionPaste() {
    pasteFromClipboard();
  }

  interface LineCountCallback {
    void onResult(int count);
  }

  private void countTotalLines(LineCountCallback callback) {
    lineIndex.countTotalLines((total) -> callback.onResult(total));
  }

  public String readRangeText(int sL, int sC, int eL, int eC) {
    return textIO.readRangeText(sL, sC, eL, eC);
  }

  public long computeByteRangeFastOrScanPublic(File file, int sL, int sC, int eL, int eC) {
    LineIndex.RangeBytes range = lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
    return (range != null) ? range.startByte : 0;
  }

  public LineIndex.RangeBytes computeByteRangeFastOrScanPublicFull(File file, int sL, int sC, int eL, int eC) {
    return lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public boolean isIndexBuildingPublic() {
    return isIndexBuilding;
  }

  public boolean isIndexDisabledPublic() {
    return isIndexDisabled;
  }

  public void selectAll() {
    selectionHandler.selectAll();
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

  // Undo/redo helpers moved to History.

  public String exportEditCacheJson() {
    return history.exportEditCacheJson();
  }

  public boolean importEditCacheJson(String json, boolean applyPendingEdits) {
    return history.importEditCacheJson(json, applyPendingEdits);
  }

  public boolean hasPendingEdits() {
    return history.hasPendingEdits();
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

  public CursorTarget computeCursorAfterInsertForUndo(int baseLine, int baseChar, String insertText) {
    return computeCursorAfterInsert(baseLine, baseChar, insertText);
  }

  public int countNewlinesForUndo(@Nullable String text) {
    return countNewlines(text);
  }

  public boolean canUndo() {
    return history.canUndo();
  }

  public boolean canRedo() {
    return history.canRedo();
  }

  public int getUndoStackSize() {
    return history.getUndoStackSize();
  }

  public int getPendingEditsCount() {
    return history.getPendingEditsCount();
  }

  public void clearUndoRedoHistory() {
    history.clearUndoRedoHistory();
  }

  public long getLastEditTimestamp() {
    return history.getLastEditTimestamp();
  }

  public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    history.applyPendingEditsToFileAsync(onComplete);
  }

  // rewriteReplaceRangeBlocking moved to FileBufferModifier.

  public void recordEdit(EditOp op) {
    history.recordEdit(op);
  }

  private void recordEditNoUndo(EditOp op) {
    history.recordEditNoUndo(op);
  }

  public void undo() {
    history.undo();
  }

  public void redo() {
    history.redo();
  }

  void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    history.updateComposingPendingOp(text, beforeLine, beforeChar);
  }

  public void replaceSelectionWithText(String insertText) {
    inputManager.replaceSelectionWithText(insertText);
  }

  private LineIndex.RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    return lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  LineIndex.RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
    return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public Handler getIoHandlerForUndo() {
    return ioHandler;
  }

  public void onUndoRedoRewriteSuccess(File inFile) {
    textIO.onUndoRedoRewriteSuccess(inFile);
  }

  private long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    return lineIndex.findLineStartByteByScanning(raf, targetLine);
  }

  public String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
    return textIO.readLineUtf8AtByte(raf, byteOffset);
  }

  public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
    return lineIndex.getLineByteLengthFromIndex(raf, line, fileLen);
  }

  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
      throws Exception {
    return textIO.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar);
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
    return textIO.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength);
  }

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return textIO.computeByteOffsetInLineUtf8(lineText, charIndex);
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
    return textIO.bytesToControlVisible(buf, len);
  }

  private boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    return viewRender.textRender.applySmartDoubleTapSelection(line, charIndex, lineText);
  }

  private boolean isPositionInsideSelection(int line, int ch) {
    return selectionHandler.isPositionInsideSelection(line, ch);
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
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  BufferedReader reopenReaderAtStart() {
    return document.reopenReaderAtStart();
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
    return textIO.shouldStreamLineLength(length);
  }

  private int getStreamedLineLength(int globalLine) {
    return textIO.getStreamedLineLength(globalLine);
  }

  int getStreamedLineSliceStart(int globalLine) {
    return textIO.getStreamedLineSliceStart(globalLine);
  }

  private void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    textIO.setStreamedLineInfo(globalLine, length, sliceStart);
  }

  public void clearStreamedLineInfo(int globalLine) {
    textIO.clearStreamedLineInfo(globalLine);
  }

  public void clearStreamedLineCaches() {
    textIO.clearStreamedLineCaches();
  }

  private boolean isSingleByteCharset() {
    return document.isSingleByteCharset();
  }

  public int getLogicalLineLength(int globalLine, @Nullable String line) {
    return textIO.getLogicalLineLength(globalLine, line);
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
      cursorAnimator.onFocusChanged(true);
    } else {
      if (hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
      cursorAnimator.onFocusChanged(false);
      cursorState.setHasComposing(false);
      selectionState.clearSelectionKeepLineNumberState();
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
    document.buildFileIndex();
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
    history.clearComposingPendingOp();
  }

  public int incrementEditVersionPublic() {
    return history.incrementEditVersion();
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
    history.updateComposingPendingOp(text, beforeLine, beforeChar);
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

  // Search Engine Callback
  private class SearchEngineCallback implements com.yn.sodiumeditor.core.SearchEngine.SearchCallback {
    @Override
    public int getEditVersionForSearch() {
      return getEditVersionForSearch();
    }

    @Override
    public int getLinesCount() {
      return getLinesCount();
    }

    @Override
    public int getWindowStartLineForSearch() {
      return getWindowStartLineForSearch();
    }

    @Override
    public int getWindowSizeForSearch() {
      return getWindowSizeForSearch();
    }

    @Override
    public boolean isIndexReadyForSearch() {
      return isIndexReadyForSearch();
    }

    @Override
    public boolean getSourceFileForSearchExists() {
      return getSourceFileForSearchExists();
    }

    @Override
    public void populateDirectLinesForRangeForSearch(int start, int end, java.util.HashMap<Integer, String> direct) {
      populateDirectLinesForRangeForSearch(start, end, direct);
    }

    @Override
    public String getLineTextForRenderWithDirectForSearch(int line, java.util.HashMap<Integer, String> direct) {
      return getLineTextForRenderWithDirectForSearch(line, direct);
    }

    @Override
    public String getLineTextForRender(int line) {
      return getLineTextForRender(line);
    }
  }

  // Search Handler Callback
  private class SearchHandlerCallback implements com.yn.sodiumeditor.input.SearchHandler.SearchInteractionCallback {
    @Override
    public int getCursorLine() {
      return cursorState.getCursorLine();
    }

    @Override
    public int getCursorChar() {
      return cursorState.getCursorChar();
    }

    @Override
    public void ensureLineInWindowForSearch(int line, boolean center) {
      ensureLineInWindowForSearch(line, center);
    }

    @Override
    public void setCursorPosition(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }

    @Override
    public void setSelectionInternal(int startLine, int startCh, int endLine, int endCh) {
      setSelectionInternal(startLine, startCh, endLine, endCh);
    }

    @Override
    public void setCursorPositionNoClear(int line, int ch) {
      cursorNavigation.setPositionNoClear(line, ch);
    }

    @Override
    public boolean hasSelection() {
      return selectionState.hasSelection();
    }

    @Override
    public int comparePos(int line1, int ch1, int line2, int ch2) {
      return comparePos(line1, ch1, line2, ch2);
    }

    @Override
    public void invalidate() {
      SodiumEditorView.this.invalidate();
    }
  }

  // Search Renderer Callback
  private class SearchRendererCallback implements com.yn.sodiumeditor.renderer.SearchRenderer.RenderCallback {
    @Override
    public float measureTextForSearch(String line, int index, int globalLine) {
      return measureTextForSearch(line, index, globalLine);
    }

    @Override
    public float measureTextWithVisualSpacesForSearch(String line, int start, int end) {
      return measureTextWithVisualSpacesForSearch(line, start, end);
    }

    @Override
    public int getCursorLine() {
      return cursorState.getCursorLine();
    }

    @Override
    public int getCursorChar() {
      return cursorState.getCursorChar();
    }

    @Override
    public boolean hasSelection() {
      return selectionState.hasSelection();
    }
  }

  // Selection Handler Callback
  private class SelectionHandlerCallback implements com.yn.sodiumeditor.input.SelectionHandler.SelectionInteractionCallback {
    @Override
    public int comparePos(int sL, int sC, int eL, int eC) {
      return SodiumEditorView.this.comparePos(sL, sC, eL, eC);
    }

    @Override
    public void invalidate() {
      SodiumEditorView.this.invalidate();
    }

    @Override
    public void setSelection(int startLine, int startChar, int endLine, int endChar, boolean selecting) {
      selectionState.setSelection(startLine, startChar, endLine, endChar, selecting);
    }

    @Override
    public void setSelectAllState(boolean selectAll, boolean entireFile) {
      selectionState.setSelectAllState(selectAll, entireFile);
    }

    @Override
    public void setSelecting(boolean selecting) {
      selectionState.setSelecting(selecting);
    }

    @Override
    public void setLineNumberSelecting(boolean enabled, int anchorLine) {
      selectionState.setLineNumberSelecting(enabled, anchorLine);
    }

    @Override
    public void clearSelectionKeepLineNumberState() {
      selectionState.clearSelectionKeepLineNumberState();
    }

    @Override
    public boolean shouldHideCopyCutForSelection() {
      return shouldHideCopyCutForSelection();
    }

    @Override
    public void deleteSelection() {
      SodiumEditorView.this.deleteSelection();
    }

    @Override
    public void post(Runnable r) {
      SodiumEditorView.this.post(r);
    }

    @Override
    public void postDelayed(Runnable r, long delayMillis) {
      SodiumEditorView.this.postDelayed(r, delayMillis);
    }

    @Override
    public Context getContext() {
      return SodiumEditorView.this.getContext();
    }

    @Override
    public boolean isFileCleared() {
      return isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return sourceFile;
    }

    @Override
    public boolean isIndexReady() {
      return isIndexReady;
    }

    @Override
    public long[] getLineOffsets() {
      return lineOffsets;
    }

    @Override
    public Object getLineOffsetsLock() {
      return lineOffsetsLock;
    }

    @Override
    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception {
      return findLineStartByteByScanning(raf, line);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return modifiedLines;
    }

    @Override
    public int getWindowStartLine() {
      return windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return linesWindow;
    }

    @Override
    public String getLineTextForRender(int line) {
      return getLineTextForRender(line);
    }

    @Override
    public int getCopyCutMaxChars() {
      return copyCutMaxChars;
    }

    @Override
    public java.nio.charset.Charset getFileCharset() {
      return document.fileCharset;
    }

    @Override
    public void setDisable(boolean disabled) {
      setDisable(disabled);
    }

    @Override
    public void setCursorLineAndChar(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }

    @Override
    public void scrollToLineFastForSelectAll(int line, int ch) {
      scrollManager.scrollToLineFastForSelectAll(line, ch);
    }

    @Override
    public void showLoadingCircle(boolean show) {
      loadingCircleManager.show(show);
    }

    @Override
    public void showPopupAtSelection() {
      popupMenuManager.showPopupAtSelection();
    }

    @Override
    public void hidePopup() {
      popupMenuManager.hidePopup();
    }

    @Override
    public void requestFocus() {
      SodiumEditorView.this.requestFocus();
    }

    @Override
    public void showKeyboard() {
      imeManager.showKeyboard();
    }

    @Override
    public void restartInput() {
      imeManager.restartInput();
    }

    @Override
    public void clearActiveSuggestion() {
      autoSuggestionManager.clearActiveSuggestion();
    }

    @Override
    public int getPrefetchLines() {
      return prefetchLines;
    }

    @Override
    public boolean isEof() {
      return isEof;
    }

    @Override
    public boolean isIndexBuilding() {
      return isIndexBuildingPublic();
    }

    @Override
    public boolean isIndexDisabled() {
      return isIndexDisabledPublic();
    }

    @Override
    public void buildFileIndex() {
      SodiumEditorView.this.buildFileIndex();
    }

    @Override
    public void loadWindowAround(int targetStart, Runnable onComplete) {
      SodiumEditorView.this.loadWindowAround(targetStart, onComplete);
    }

    @Override
    public void countTotalLines(com.yn.sodiumeditor.input.SelectionHandler.OnTotalLinesCounted callback) {
      fileManager.countTotalLines(callback::onCounted);
    }

    @Override
    public int incrementEditVersion() {
      return history.incrementEditVersion();
    }

    @Override
    public int getEditVersion() {
      return history.getEditVersion();
    }

    @Override
    public int getWidth() {
      return SodiumEditorView.this.getWidth();
    }

    @Override
    public float getTextStartX() {
      return SodiumEditorView.this.getTextStartX();
    }

    @Override
    public boolean isWordWrapEnabled() {
      return wrapWordState.isWordWrapEnabled;
    }

    @Override
    public void cancelWrapWordWorkForPriority() {
      wrapWordBuilder.cancelWorkForPriority();
    }

    @Override
    public boolean isWrapWordMetricsUsableForWindow(int widthPx) {
      return wrapWordBuilder.isMetricsUsableForWindow(SodiumEditorView.this, widthPx);
    }

    @Override
    public int getCursorLine() {
      return cursorState.getCursorLine();
    }

    @Override
    public int getCursorChar() {
      return cursorState.getCursorChar();
    }

    @Override
    public int getSelectionHandleColor() {
      return handlesManager.getSelectionHandleColor();
    }

    @Override
    public void setSelectionHandleColor(int color) {
      handlesManager.setSelectionHandleColor(color);
    }

    @Override
    public void setCursorPositionNoClear(int line, int ch) {
      cursorNavigation.setPositionNoClear(line, ch);
    }
  }

  // Selection Text Builder Callback
  private class SelectionTextBuilderCallback implements com.yn.sodiumeditor.core.SelectionTextBuilder.SelectionCallback {
    @Override
    public int comparePos(int sL, int sC, int eL, int eC) {
      return SodiumEditorView.this.comparePos(sL, sC, eL, eC);
    }

    @Override
    public boolean isFileCleared() {
      return isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return sourceFile;
    }

    @Override
    public boolean isIndexReady() {
      return isIndexReady;
    }

    @Override
    public long[] getLineOffsets() {
      return lineOffsets;
    }

    @Override
    public Object getLineOffsetsLock() {
      return lineOffsetsLock;
    }

    @Override
    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception {
      return findLineStartByteByScanning(raf, line);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return modifiedLines;
    }

    @Override
    public int getWindowStartLine() {
      return windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return linesWindow;
    }

    @Override
    public String getLineTextForRender(int line) {
      return getLineTextForRender(line);
    }

    @Override
    public int getCopyCutMaxChars() {
      return copyCutMaxChars;
    }

    @Override
    public java.nio.charset.Charset getFileCharset() {
      return document.fileCharset;
    }
  }

  // Cursor Navigation Callback
  private class CursorNavigationCallback implements com.yn.sodiumeditor.core.CursorNavigation.NavigationCallback {
    @Override
    public @Nullable String getLineTextForRender(int line) {
      return getLineTextForRender(line);
    }

    @Override
    public boolean isEof() {
      return isEof;
    }

    @Override
    public int getWindowStartLine() {
      return windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return linesWindow;
    }

    @Override
    public void setCursorPosition(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }

    @Override
    public void clearSelectionKeepLineNumberState() {
      selectionState.clearSelectionKeepLineNumberState();
    }

    @Override
    public void hidePopup() {
      popupMenuManager.hidePopup();
    }

    @Override
    public void resetCursorBlink() {
      cursorAnimator.resetCursorBlink();
    }

    @Override
    public void invalidate() {
      SodiumEditorView.this.invalidate();
    }

    @Override
    public void keepCursorVisibleHorizontally() {
      scrollManager.keepCursorVisibleHorizontally();
    }

    @Override
    public void autoSuggestionUpdate() {
      autoSuggestionManager.updateSuggestion();
    }

    @Override
    public boolean hasSelection() {
      return selectionState.hasSelection();
    }

    @Override
    public int getSelectionStartLine() {
      return selectionState.selStartLine;
    }

    @Override
    public int getSelectionStartChar() {
      return selectionState.selStartChar;
    }

    @Override
    public int getSelectionEndLine() {
      return selectionState.selEndLine;
    }

    @Override
    public int getSelectionEndChar() {
      return selectionState.selEndChar;
    }

    @Override
    public int comparePos(int sL, int sC, int eL, int eC) {
      return SodiumEditorView.this.comparePos(sL, sC, eL, eC);
    }
  }

  // Cursor Renderer Callback
  private class CursorRendererCallback implements com.yn.sodiumeditor.renderer.CursorRenderer.RenderCallback {
    @Override
    public float getCursorDrawX() {
      return cursorAnimator.getCursorDrawX();
    }

    @Override
    public float getCursorDrawY() {
      return cursorAnimator.getCursorDrawY();
    }

    @Override
    public boolean isCursorVisible() {
      return cursorAnimator.isCursorVisible();
    }

    @Override
    public int getCaretColor() {
      return handlesManager.getCaretColor();
    }

    @Override
    public float getCursorWidth() {
      return handlesManager.getCursorWidth();
    }

    @Override
    public float getLineHeight() {
      return lineHeight;
    }
  }

  // IME Composition Callback
  private class ImeCompositionCallback implements com.yn.sodiumeditor.input.ImeCompositionHandler.CompositionCallback {
    @Override
    public boolean isReadOnly() {
      return isReadOnly;
    }

    @Override
    public void invalidatePendingIOForEdit() {
      invalidatePendingIOForEdit();
    }

    @Override
    public int incrementEditVersion() {
      return history.incrementEditVersion();
    }

    @Override
    public void ensureLineInWindow(int line, boolean center) {
      scrollManager.ensureLineInWindow(line, center);
    }

    @Override
    public boolean isWindowLoading() {
      return isWindowLoading;
    }

    @Override
    public int getWindowStartLine() {
      return windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return linesWindow;
    }

    @Override
    public String getLineFromWindowLocal(int local) {
      return getLineFromWindowLocal(local);
    }

    @Override
    public void updateLocalLine(int local, String newLine) {
      updateLocalLinePublic(local, newLine);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return modifiedLines;
    }

    @Override
    public void computeWidthForLine(int line, String lineText) {
      computeWidthForLinePublic(line, lineText);
    }

    @Override
    public void recalculateMaxLineWidth() {
      recalculateMaxLineWidth();
    }

    @Override
    public void invalidate() {
      SodiumEditorView.this.invalidate();
    }

    @Override
    public void autoSuggestionUpdate() {
      autoSuggestionManager.updateSuggestion();
    }

    @Override
    public void clearComposingPendingOp() {
      clearComposingPendingOpPublic();
    }

    @Override
    public void clearLastComposingTextForCharAnim() {
      charAnimator.clearLastComposingTextForCharAnim();
    }

    @Override
    public Paint getPaintForChar(int line, int at, String base) {
      return highlightManager.getPaintForChar(line, at, base);
    }

    @Override
    public void startDeleteAnimation(int line, int at, String removed, Paint paint) {
      charAnimator.startDeleteAnimation(line, at, removed, paint);
    }

    @Override
    public boolean isCharAnimationEnabled() {
      return charAnimationConfig.isEnabled();
    }

    @Override
    public com.yn.sodiumeditor.state.CursorState getCursorState() {
      return cursorState;
    }

    @Override
    public void setCursorPosition(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }
  }

  // Editor Text Inserter Callback
  private class EditorTextInserterCallback implements com.yn.sodiumeditor.core.EditorTextInserter.InsertionCallback {
    @Override
    public boolean isReadOnly() {
      return isReadOnly;
    }

    @Override
    public void invalidatePendingIOForEdit() {
      invalidatePendingIOForEdit();
    }

    @Override
    public int incrementEditVersion() {
      return history.incrementEditVersion();
    }

    @Override
    public boolean isFileCleared() {
      return isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return sourceFile;
    }

    @Override
    public boolean isLargePasteText(String text) {
      return SodiumEditorView.isLargePasteText(text);
    }

    @Override
    public void beginLargeEditUiIfNeeded(boolean isSelectAll, int sL, int eL, boolean selectAllLike) {
      beginLargeEditUiIfNeeded(isSelectAll, sL, eL, selectAllLike);
    }

    @Override
    public Handler getMainHandler() {
      return mainHandler;
    }

    @Override
    public Runnable getLargeEditUiWatchdog() {
      return largeEditUiWatchdog;
    }

    @Override
    public void postDelayed(Runnable r, long delayMillis) {
      SodiumEditorView.this.postDelayed(r, delayMillis);
    }

    @Override
    public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget computeCursorAfterInsert(int line, int ch, String text) {
      SodiumEditorView.CursorTarget target = SodiumEditorView.this.computeCursorAfterInsertForUndo(line, ch, text);
      return new com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget(target.line, target.ch);
    }

    @Override
    public void rewriteReplaceRangeAsync(int opToken, java.io.File inFile, int sL, int sC, int eL, int eC, String text, com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target, boolean finishLargeEdit) {
      rewriteReplaceRangeAsyncPublic(opToken, inFile, sL, sC, eL, eC, text, new SodiumEditorView.CursorTarget(target.line, target.ch), finishLargeEdit);
    }

    @Override
    public void autoSuggestionUpdate() {
      autoSuggestionManager.updateSuggestion();
    }

    @Override
    public void addLineCountDelta(int delta) {
      history.addLineCountDelta(delta);
    }

    @Override
    public int getUndoTextLimit() {
      return history.getUndoTextLimit();
    }

    @Override
    public void recordEdit(com.yn.sodiumeditor.core.EditOp op) {
      recordEdit(op);
    }

    @Override
    public void ensureLineInWindow(int line, boolean center) {
      scrollManager.ensureLineInWindow(line, center);
    }

    @Override
    public boolean isWindowLoading() {
      return isWindowLoading;
    }

    @Override
    public int getWindowStartLine() {
      return windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return linesWindow;
    }

    @Override
    public String getLineFromWindowLocal(int local) {
      return getLineFromWindowLocal(local);
    }

    @Override
    public void updateLocalLine(int local, String newLine) {
      updateLocalLinePublic(local, newLine);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return modifiedLines;
    }

    @Override
    public void removeLineWidthCache(int line) {
      lineWidthCache.remove(line);
    }

    @Override
    public void clearLineWidthCache() {
      lineWidthCache.clear();
    }

    @Override
    public void addLinesWindowAll(int index, java.util.List<String> lines) {
      linesWindow.addAll(index, lines);
    }

    @Override
    public void setCursorPosition(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }

    @Override
    public int getCursorLine() {
      return cursorState.getCursorLine();
    }

    @Override
    public int getCursorChar() {
      return cursorState.getCursorChar();
    }

    @Override
    public void moveCharDelta(int delta) {
      cursorState.moveCharDelta(delta);
    }

    @Override
    public void setLineAndChar(int line, int ch) {
      cursorState.setCursorPosition(line, ch);
    }

    @Override
    public int getLinesCount() {
      return getLinesCount();
    }

    @Override
    public boolean isShowLineNumbers() {
      return lineNumberManager.isShowLineNumbers();
    }

    @Override
    public void requestLayout() {
      requestLayout();
    }

    @Override
    public void onLineCountChanged(int delta) {
      wrapWordBuilder.onLineCountChanged(SodiumEditorView.this);
    }

    @Override
    public void recalculateMaxLineWidth() {
      recalculateMaxLineWidth();
    }

    @Override
    public void keepCursorVisibleHorizontally() {
      scrollManager.keepCursorVisibleHorizontally();
    }

    @Override
    public void resetCursorBlink() {
      cursorAnimator.resetCursorBlink();
    }

    @Override
    public void invalidate() {
      SodiumEditorView.this.invalidate();
    }

    @Override
    public int getLineLength(int line) {
      String ln = getLineTextForRender(line);
      return ln != null ? ln.length() : 0;
    }
  }
}
