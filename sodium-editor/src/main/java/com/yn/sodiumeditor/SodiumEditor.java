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

public class SodiumEditor extends View {

  // Editor Configuration
  public final com.yn.sodiumeditor.config.EditorConfig editorConfig = new com.yn.sodiumeditor.config.EditorConfig();

  // Editor State Managers
  public final com.yn.sodiumeditor.core.EditorStateManager editorState = new com.yn.sodiumeditor.core.EditorStateManager();
  public final com.yn.sodiumeditor.state.EditorInputState editorInputState = new com.yn.sodiumeditor.state.EditorInputState();
  public final com.yn.sodiumeditor.state.EditorIndexState editorIndexState = new com.yn.sodiumeditor.state.EditorIndexState();
  public final com.yn.sodiumeditor.state.EditorLoadingState editorLoadingState = new com.yn.sodiumeditor.state.EditorLoadingState();

  // IO Manager
  public final com.yn.sodiumeditor.io.EditorIOManager editorIO;

  // --- Cursor Animation State ---
  private final InputManager inputManager;
  @Nullable ValueAnimator flingStopAnimator;
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
  
  // Popup Menu components
  public final com.yn.sodiumeditor.config.PopupConfig popupConfig = new com.yn.sodiumeditor.config.PopupConfig();
  public final com.yn.sodiumeditor.state.PopupMenuState popupMenuState = new com.yn.sodiumeditor.state.PopupMenuState();
  public com.yn.sodiumeditor.renderer.PopupMenuRenderer popupMenuRenderer;
  public com.yn.sodiumeditor.input.PopupTouchHandler popupTouchHandler;

  // Inline prediction components
  public final com.yn.sodiumeditor.state.InlinePredictionState inlinePredictionState = new com.yn.sodiumeditor.state.InlinePredictionState();
  public com.yn.sodiumeditor.renderer.InlinePredictionRenderer inlinePredictionRenderer;
  public com.yn.sodiumeditor.core.InlinePredictionEngine inlinePredictionEngine;
  public com.yn.sodiumeditor.core.WordTrie inlinePredictionTrie;
  public com.yn.sodiumeditor.core.PathPredictionEngine inlinePredictionPathEngine;

  // --- Zoom State (moved to ZoomManager) ---

  // Word Wrap Components
  public final com.yn.sodiumeditor.state.WrapWordState wrapWordState = new com.yn.sodiumeditor.state.WrapWordState();
  public final com.yn.sodiumeditor.state.WrapWordMetrics wrapWordMetrics = new com.yn.sodiumeditor.state.WrapWordMetrics();
  public com.yn.sodiumeditor.core.WrapWordEngine wrapWordEngine;
  public com.yn.sodiumeditor.core.WrapWordBuilder wrapWordBuilder;
  public com.yn.sodiumeditor.core.WrapWordMapper wrapWordMapper;
  public final com.yn.sodiumeditor.renderer.WrapWordIndicatorRender wrapWordIndicatorRender = new com.yn.sodiumeditor.renderer.WrapWordIndicatorRender();
  public com.yn.sodiumeditor.io.WrapWordDocument wrapWordDocument;





  // auto-scroll when dragging handles
  public final Handler mainHandler = new Handler(Looper.getMainLooper());





  // Indent guide components
  public final com.yn.sodiumeditor.state.IndentGuideState indentGuideState = new com.yn.sodiumeditor.state.IndentGuideState();
  public com.yn.sodiumeditor.renderer.IndentGuideRenderer indentGuideRenderer;
  public com.yn.sodiumeditor.core.IndentGuideEngine indentGuideEngine;

  // Whitespace guide components
  public final com.yn.sodiumeditor.state.WhitespaceGuideState whitespaceGuideState = new com.yn.sodiumeditor.state.WhitespaceGuideState();
  public com.yn.sodiumeditor.renderer.WhitespaceGuideRenderer whitespaceGuideRenderer;

  // Handle components
  public final com.yn.sodiumeditor.state.HandleState handleState = new com.yn.sodiumeditor.state.HandleState();
  public com.yn.sodiumeditor.renderer.HandleRenderer handleRenderer;
  public com.yn.sodiumeditor.input.HandleDragHandler handleDragHandler;
  
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

  // Highlight components
  public final com.yn.sodiumeditor.state.HighlightState highlightState = new com.yn.sodiumeditor.state.HighlightState();
  public com.yn.sodiumeditor.core.HighlightParser highlightParser;
  public com.yn.sodiumeditor.renderer.HighlightRenderer highlightRenderer;
  public com.yn.sodiumeditor.renderer.UrlUnderlineRenderer urlUnderlineRenderer;
  public com.yn.sodiumeditor.renderer.PathUnderlineRenderer pathUnderlineRenderer;
  public com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer errorUnderlineRenderer;

  // Line number components
  public final com.yn.sodiumeditor.config.LineNumberConfig lineNumberConfig = new com.yn.sodiumeditor.config.LineNumberConfig();
  public final com.yn.sodiumeditor.state.LineNumberState lineNumberState = new com.yn.sodiumeditor.state.LineNumberState();
  public com.yn.sodiumeditor.renderer.LineNumberRenderer lineNumberRenderer;

  // Bracket guide components
  public final com.yn.sodiumeditor.state.BracketGuideState bracketGuideState = new com.yn.sodiumeditor.state.BracketGuideState();
  public com.yn.sodiumeditor.renderer.BracketGuideRenderer bracketGuideRenderer;
  public com.yn.sodiumeditor.core.BracketGuideParser bracketGuideParser;

  // Bracket match components
  public final com.yn.sodiumeditor.state.BracketMatchState bracketMatchState = new com.yn.sodiumeditor.state.BracketMatchState();
  public com.yn.sodiumeditor.renderer.BracketMatchRenderer bracketMatchRenderer;
  public com.yn.sodiumeditor.core.BracketMatchEngine bracketMatchEngine;

  // Loading circle components
  public final com.yn.sodiumeditor.state.LoadingCircleState loadingCircleState = new com.yn.sodiumeditor.state.LoadingCircleState();
  public com.yn.sodiumeditor.renderer.LoadingCircleRenderer loadingCircleRenderer;
  public com.yn.sodiumeditor.renderer.animation.LoadingCircleAnimator loadingCircleAnimator;

  public final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();
  
  // Fold components
  public final com.yn.sodiumeditor.state.FoldState foldState = new com.yn.sodiumeditor.state.FoldState();
  public com.yn.sodiumeditor.renderer.FoldRenderer foldRenderer;
  public com.yn.sodiumeditor.input.FoldTouchHandler foldTouchHandler;
  public com.yn.sodiumeditor.core.FoldEngine foldEngine;

  // handle dragging edge flags moved to HandleDragHandler

  private final ViewRender viewRender = new ViewRender(this);

  // Visible char range temp arrays
  public final int[] visibleCharRangeTmp = new int[2];
  public final int[] visibleCharRangeTmpForRender = new int[2];

  // Double tap selection fields
  public int lastDoubleTapLine = -1;
  public int lastDoubleTapWordStart = -1;
  public int lastDoubleTapWordEnd = -1;
  public int lastDoubleTapStage = 0;

  // Paint alias for convenience
  public final Paint paint = editorConfig.paint;

  // Location in window temp array
  private final int[] tmpLocationInWindow = new int[2];

  // Fling stop animation duration
  public static final long FLING_STOP_ANIM_DURATION_MS = 90;

  // Delegate fields for backward compatibility - these mirror the state objects
  public float lineHeight;
  public int prefetchLines;
  public boolean isRtl;
  public boolean isEof;
  public float currentMaxWindowLineWidth;
  public float globalMaxLineWidth;
  public boolean isWindowLoading;
  public int windowStartLine;
  public java.util.List<String> linesWindow;
  public int keyboardHeight;
  public int drawBaseLine;
  public java.util.LinkedHashMap<Integer, String> modifiedLines;
  public boolean pointerDown = false;
  public boolean movedSinceDown = false;
  public String lastImeCommitText = "";
  public long lastImeCommitUptime = 0;
  public boolean suppressNextCommitText = false;
  
  // File manager delegate (for backward compatibility)
  public com.yn.sodiumeditor.io.EditorIOManager fileManager;
  public long[] lineOffsets;
  public Object lineOffsetsLock;
  public int windowSize;
  
  // TextIO delegate (for backward compatibility with Document.java)
  public com.yn.sodiumeditor.io.TextIO textIO;
  public com.yn.sodiumeditor.io.LineIndex lineIndex;
  
  // Index state fields (for backward compatibility with Document.java)
  public boolean isIndexReady;
  public boolean isIndexBuilding;
  public boolean isIndexDisabled;
  public String indexDisabledPath;
  public long indexDisabledFileLength;
  
  // Performance cache fields
  public int lineWidthCacheSize = 200;
  public int colsWidthCacheSize = 256;
  public int prefetchCols = 512;
  public final java.util.LinkedHashMap<Integer, Float> lineWidthCache = new java.util.LinkedHashMap<>(200, 0.75f, true) {
    @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Float> eldest) {
      return size() > lineWidthCacheSize;
    }
  };
  public final java.util.LinkedHashMap<Integer, Float> avgCharWidthCache = new java.util.LinkedHashMap<>(256, 0.75f, true) {
    @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Float> eldest) {
      return size() > colsWidthCacheSize;
    }
  };
  
  // Streamed lines fields
  public final android.util.SparseIntArray streamedLineLengths = new android.util.SparseIntArray();
  public final android.util.SparseIntArray streamedLineSliceStarts = new android.util.SparseIntArray();
  public boolean streamedSliceUpdatePending = false;
  public int streamedSliceUpdateToken = 0;
  public final int[] streamedSliceTmp = new int[2];
  public final Object streamedLinesLock = new Object();
  
  // IO handler and task version (initialized after editorIO is created)
  public android.os.Handler ioHandler;
  public java.util.concurrent.atomic.AtomicInteger ioTaskVersion;
  
  // Reader for file delegate
  public java.io.BufferedReader readerForFile;

  // Max width recalc token
  public int maxWidthRecalcToken = 0;
  
  // Behavior config fields
  public boolean isIndentationBlocksEnabled;
  public boolean isMultiLineStringsEnabled;
  public boolean isBacktickStringsEnabled;
  public boolean isTripleQuoteStringsEnabled;
  public boolean isStableGlyphPositionsEnabled;
  public boolean binarySafeRenderingEnabled;
  public boolean isAutoPairingEnabled;
  public boolean isAutoBracketNewlineEnabled;
  public boolean isAutoBracketNewlineIndentEnabled;
  public boolean isAutoIndentAfterClosingBracketEnabled;
  
  // File charset
  public java.nio.charset.Charset fileCharset = java.nio.charset.StandardCharsets.UTF_8;
  
  // File cleared state
  public boolean isFileCleared = false;
  
  // Initial load callbacks
  public final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
  
  // Visual config fields (delegates)
  public float paddingLeft = 10f;
  
  // Visual config fields
  public final android.graphics.Rect textBounds = editorConfig.textBounds;
  
  // Source file delegate
  public java.io.File sourceFile;
  public java.io.File getSourceFile() {
    return editorIO.sourceFile;
  }
  public void setSourceFile(java.io.File file) {
    sourceFile = file;
    editorIO.sourceFile = file;
  }

  // Large edit UI (brief busy indicator)
  public final Runnable largeEditUiWatchdog =
      new Runnable() {
        @Override
        public void run() {
          // Safety: never allow spinner/disable to get stuck forever
          endLargeEditUi(false);
        }
      };

  // Bottom scroll offset constant
  public static final float BOTTOM_SCROLL_OFFSET = 100f;
  
  // Fold placeholder text constant
  public static final String FOLD_PLACEHOLDER_TEXT = "\u27F6";
  
  // Indent block unit constant
  public static final String INDENT_BLOCK_UNIT = "  ";

  // Zoom scroll adjustment for word wrap


  // --- Inline Prediction State (moved to InlinePredictionState, InlinePredictionRenderer, InlinePredictionEngine) ---

  final Runnable delayedWindowCheck =
      new Runnable() {
        @Override
        public void run() {
          checkAndLoadWindow();
        }
      };

  public SodiumEditor(Context ctx, @Nullable AttributeSet attrs) {
    super(ctx, attrs);
    editorConfig.initPaint(36, 0xFF000000);
    editorConfig.visualConfig.paintTextSize = 36;
    editorConfig.visualConfig.paintColor = 0xFF000000;
    editorConfig.visualConfig.paddingLeft = 10f;
    editorConfig.performanceConfig.lineWidthCacheSize = 200;
    editorConfig.performanceConfig.colsWidthCacheSize = 256;
    editorConfig.baseTypeface = (editorConfig.paint.getTypeface() != null) ? editorConfig.paint.getTypeface() : Typeface.DEFAULT;
    editorConfig.lineHeight = editorConfig.paint.getFontSpacing();
    editorConfig.visualConfig.baseCursorTextSizePx = editorConfig.paint.getTextSize();
    editorConfig.updateLineHeight();
    
    // Initialize delegate fields
    lineHeight = editorConfig.lineHeight;
    prefetchLines = editorConfig.performanceConfig.prefetchLines;
    isRtl = editorConfig.visualConfig.isRtl;
    paddingLeft = editorConfig.paddingLeft;
    isEof = editorState.isEof;
    currentMaxWindowLineWidth = editorState.currentMaxWindowLineWidth;
    globalMaxLineWidth = editorState.globalMaxLineWidth;
    isWindowLoading = editorState.isWindowLoading;
    windowStartLine = editorState.windowStartLine;
    linesWindow = editorState.linesWindow;
    keyboardHeight = editorState.keyboardHeight;
    drawBaseLine = editorState.drawBaseLine;
    modifiedLines = editorState.modifiedLines;
    windowSize = editorState.windowSize;

    // Initialize index state fields
    isIndexReady = editorIndexState.isIndexReady;
    isIndexBuilding = editorIndexState.isIndexBuilding;
    isIndexDisabled = editorIndexState.isIndexDisabled;
    indexDisabledPath = editorIndexState.indexDisabledPath;
    indexDisabledFileLength = editorIndexState.indexDisabledFileLength;
    
    // Initialize behavior config fields
    isIndentationBlocksEnabled = editorConfig.behaviorConfig.isIndentationBlocksEnabled;
    isMultiLineStringsEnabled = editorConfig.behaviorConfig.isMultiLineStringsEnabled;
    isBacktickStringsEnabled = editorConfig.behaviorConfig.isBacktickStringsEnabled;
    isTripleQuoteStringsEnabled = editorConfig.behaviorConfig.isTripleQuoteStringsEnabled;
    isStableGlyphPositionsEnabled = editorConfig.performanceConfig.isStableGlyphPositionsEnabled;
    binarySafeRenderingEnabled = editorConfig.behaviorConfig.binarySafeRenderingEnabled;
    isAutoPairingEnabled = editorConfig.behaviorConfig.isAutoPairingEnabled;
    isAutoBracketNewlineEnabled = editorConfig.behaviorConfig.isAutoBracketNewlineEnabled;
    isAutoBracketNewlineIndentEnabled = editorConfig.behaviorConfig.isAutoBracketNewlineIndentEnabled;
    isAutoIndentAfterClosingBracketEnabled = editorConfig.behaviorConfig.isAutoIndentAfterClosingBracketEnabled;
    
    indentGuideRenderer = new com.yn.sodiumeditor.renderer.IndentGuideRenderer(this, indentGuideState);
    indentGuideEngine = new com.yn.sodiumeditor.core.IndentGuideEngine(this, indentGuideState);
    
    // Initialize Handle components
    handleRenderer = new com.yn.sodiumeditor.renderer.HandleRenderer();
    handleDragHandler = new com.yn.sodiumeditor.input.HandleDragHandler(this, handleState, mainHandler);

    // Initialize Highlight components
    highlightParser = new com.yn.sodiumeditor.core.HighlightParser(new HighlightParserCallbackImpl());
    highlightRenderer = new com.yn.sodiumeditor.renderer.HighlightRenderer(this, highlightState, highlightParser);
    urlUnderlineRenderer = new com.yn.sodiumeditor.renderer.UrlUnderlineRenderer(this, highlightState);
    pathUnderlineRenderer = new com.yn.sodiumeditor.renderer.PathUnderlineRenderer(this, highlightState);
    errorUnderlineRenderer = new com.yn.sodiumeditor.renderer.ErrorUnderlineRenderer(this, highlightState);

    bracketMatchRenderer = new com.yn.sodiumeditor.renderer.BracketMatchRenderer(this, bracketMatchState);
    bracketMatchRenderer.setBaseTextSizePx(editorConfig.paint.getTextSize());
    bracketMatchEngine = new com.yn.sodiumeditor.core.BracketMatchEngine(this, bracketMatchState);
    bracketGuideRenderer = new com.yn.sodiumeditor.renderer.BracketGuideRenderer(this, bracketGuideState);
    bracketGuideRenderer.setBaseTextSizePx(editorConfig.paint.getTextSize());
    bracketGuideParser = new com.yn.sodiumeditor.core.BracketGuideParser(this, bracketGuideState, bracketGuideRenderer);
    whitespaceGuideRenderer = new com.yn.sodiumeditor.renderer.WhitespaceGuideRenderer(whitespaceGuideState);
    whitespaceGuideRenderer.initPaints(0xFF555555);
    updateWhitespaceGuideMetrics();

    selectionConfig.initPaints();
    selectionTextBuilder = new com.yn.sodiumeditor.core.SelectionTextBuilder(new SelectionTextBuilderCallback());
    selectionHandler = new com.yn.sodiumeditor.input.SelectionHandler(selectionConfig, selectionState, selectionTextBuilder, new SelectionHandlerCallback());
    selectionRenderer = new com.yn.sodiumeditor.renderer.SelectionRenderer(selectionConfig);

    // Initialize Cursor components
    cursorNavigation = new com.yn.sodiumeditor.core.CursorNavigation(cursorState, new CursorNavigationCallback());
    cursorRenderer = new com.yn.sodiumeditor.renderer.CursorRenderer(cursorConfig, cursorState, new CursorRendererCallback());
    imeCompositionHandler = new com.yn.sodiumeditor.input.ImeCompositionHandler(cursorState, new ImeCompositionCallback());
    editorTextInserter = new com.yn.sodiumeditor.core.EditorTextInserter(cursorState, new EditorTextInserterCallback());

    // Initialize Line number components
    float density = getContext().getResources().getDisplayMetrics().density;
    lineNumberRenderer = new com.yn.sodiumeditor.renderer.LineNumberRenderer(this, lineNumberState, lineNumberConfig);
    lineNumberRenderer.initDefaults(paint, density);
    
    // Initialize Fold components
    foldRenderer = new com.yn.sodiumeditor.renderer.FoldRenderer(this, foldState);
    foldRenderer.init(density);
    foldTouchHandler = new com.yn.sodiumeditor.input.FoldTouchHandler(this, foldState, foldRenderer);
    foldEngine = new com.yn.sodiumeditor.core.FoldEngine(this);

    popupMenuRenderer = new com.yn.sodiumeditor.renderer.PopupMenuRenderer(this, popupConfig, popupMenuState);
    popupTouchHandler = new com.yn.sodiumeditor.input.PopupTouchHandler(this, popupMenuState);
    loadingCircleRenderer = new com.yn.sodiumeditor.renderer.LoadingCircleRenderer(this, loadingCircleState);
    loadingCircleAnimator = new com.yn.sodiumeditor.renderer.animation.LoadingCircleAnimator(this, loadingCircleState);

    // Initialize Word Wrap components
    wrapWordDocument = new com.yn.sodiumeditor.io.WrapWordDocument(editorState.modifiedLines);
    wrapWordEngine = new com.yn.sodiumeditor.core.WrapWordEngine(wrapWordMetrics, whitespaceGuideState);
    wrapWordMapper = new com.yn.sodiumeditor.core.WrapWordMapper(wrapWordMetrics, wrapWordEngine);
    wrapWordBuilder = new com.yn.sodiumeditor.core.WrapWordBuilder(wrapWordMetrics, wrapWordState, wrapWordEngine, wrapWordMapper, wrapWordDocument);
    wrapWordIndicatorRender.init(paint, density);

    editorInputState.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();

    // Initialize IO Manager
    editorIO = new com.yn.sodiumeditor.io.EditorIOManager(this);
    ioHandler = editorIO.ioHandler;
    ioTaskVersion = editorIO.ioTaskVersion;
    
    // Initialize file manager delegate
    fileManager = editorIO;
    lineOffsets = editorIndexState.lineOffsets;
    lineOffsetsLock = editorIndexState.lineOffsetsLock;
    textIO = editorIO.textIO;
    lineIndex = editorIO.lineIndex;
    
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
                getWindowVisibleDisplayFrame(editorInputState.visibleDisplayFrame);
                getLocationInWindow(tmpLocationInWindow);
                int viewBottom = tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - editorInputState.visibleDisplayFrame.bottom);
                newKeyboardHeight = overlap;
              }

              if (newKeyboardHeight != editorState.keyboardHeight) {
                editorState.keyboardHeight = newKeyboardHeight;
                post(scrollManager::keepCursorVisibleHorizontally);
              }
            });

    inlinePredictionTrie = new com.yn.sodiumeditor.core.WordTrie();
    inlinePredictionPathEngine = new com.yn.sodiumeditor.core.PathPredictionEngine();
    inlinePredictionEngine = new com.yn.sodiumeditor.core.InlinePredictionEngine(this, inlinePredictionState, inlinePredictionTrie, inlinePredictionPathEngine);
    inlinePredictionRenderer = new com.yn.sodiumeditor.renderer.InlinePredictionRenderer(this, inlinePredictionState);
    inlinePredictionRenderer.initPaints(paint);

    pathUnderlineRenderer.setPathUnderliningEnabled(true); // Enable path underlining by default
  }

  // --- Public APIs for Auto Completion ---





  public void setBinarySafeRenderingEnabled(boolean enabled) {
    if (editorConfig.behaviorConfig.binarySafeRenderingEnabled == enabled) return;
    editorConfig.behaviorConfig.binarySafeRenderingEnabled = enabled;
    synchronized (editorState.lineWidthCache) {
      editorState.lineWidthCache.clear();
    }
    editorState.currentMaxWindowLineWidth = 0f;
    editorState.globalMaxLineWidth = 0f;
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
    invalidateHighlightEnsureRange();
    bracketGuideRenderer.invalidateCache();
    if (wrapWordState.isWordWrapEnabled) wrapWordBuilder.invalidate(true, true);
    wrapWordBuilder.requestPrefixRebuild(this);
    viewRender.reloadWindowAroundVisible(false);
    invalidate();
  }

  public void setVisibleCharPadding(int paddingChars) {
    int safe = Math.max(0, paddingChars);
    if (editorConfig.visualConfig.visibleCharPadding == safe) return;
    editorConfig.visualConfig.visibleCharPadding = safe;
    invalidate();
  }

  public void setStableGlyphPositionsEnabled(boolean enabled) {
    if (this.editorConfig.performanceConfig.isStableGlyphPositionsEnabled == enabled) return;
    this.editorConfig.performanceConfig.isStableGlyphPositionsEnabled = enabled;
    invalidate();
  }

  public void setPerformanceModeEnabled(boolean enabled) {
    if (this.editorConfig.performanceConfig.isPerformanceModeEnabled == enabled) return;
    this.editorConfig.performanceConfig.isPerformanceModeEnabled = enabled;
    if (enabled) {
      urlUnderlineRenderer.setUrlUnderliningEnabled(false);
      pathUnderlineRenderer.setPathUnderliningEnabled(false);
      highlightState.isColorHighlightingEnabled = false;
      bracketMatchState.setEnabled(false);
      bracketGuideRenderer.setEnabled(false);
      indentGuideRenderer.setIndentGuidesEnabled(false);
      whitespaceGuideState.setWhitespaceGuidesEnabled(false);
      wrapWordIndicatorRender.setEnabled(false);
      inlinePredictionState.setAutoCompletionEnabled(false);
      inlinePredictionState.setAutoPathCompletionEnabled(false);
      charAnimationConfig.setEnabled(false);
      highlightState.setHighlightCurrentLine(false);
      setIndentationBlocksEnabled(false);
      foldState.setCodeFoldingEnabled(false);
    }
    invalidate();
  }









  private void insertStringAtCursor(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }


  // --- Public APIs for Line Numbers ---

  public void setEditorBackgroundColor(int color) {
    editorConfig.visualConfig.hasEditorBackgroundColor = true;
    editorConfig.visualConfig.editorBackgroundColor = color;
    invalidate();
  }

  public void clearEditorBackgroundColor() {
    if (!editorConfig.visualConfig.hasEditorBackgroundColor) return;
    editorConfig.visualConfig.hasEditorBackgroundColor = false;
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
    if (editorConfig.visualConfig.editorBackgroundBitmap != null && !editorConfig.visualConfig.editorBackgroundBitmap.isRecycled()) {
      editorConfig.visualConfig.editorBackgroundBitmap.recycle();
    }
    editorConfig.visualConfig.editorBackgroundBitmap = null;
    invalidate();
  }

  private void setEditorBackgroundBitmap(Bitmap bitmap) {
    if (editorConfig.visualConfig.editorBackgroundBitmap != null && !editorConfig.visualConfig.editorBackgroundBitmap.isRecycled()) {
      editorConfig.visualConfig.editorBackgroundBitmap.recycle();
    }
    editorConfig.visualConfig.editorBackgroundBitmap = bitmap;
    invalidate();
  }










































  public void replaceSelectionText(String text) {
    inputManager.replaceSelectionWithText(text == null ? "" : text);
  }



















  public void setFileCharset(@Nullable Charset charset) {
    editorIO.document.setFileCharset(charset);
  }

  public void setFileEncoding(@Nullable String charsetName) {
    editorIO.document.setFileEncoding(charsetName);
  }

  public void setMaxSyntaxLineLength(int maxChars) {
    highlightState.setMaxSyntaxLineLength(maxChars);
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
    return viewRender.computeMinWindowSizeForPrefetch(editorState.prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    return viewRender.computeMinWindowSizeForPrefetch(prefetch);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    viewRender.reloadWindowAroundVisible(recalcWidthSync);
  }

  public void setCursorWidth(float width) {
    if (handleRenderer.getBaseCursorWidthPx() == width && editorConfig.visualConfig.baseCursorTextSizePx == editorConfig.paint.getTextSize()) return;
    handleRenderer.setBaseCursorWidthPx(width);
    editorConfig.visualConfig.baseCursorTextSizePx = editorConfig.paint.getTextSize();
    updateTextSizeDependentMetrics();
    invalidate();
  }



  public void setClickAfterEndToAddLineEnabled(boolean enabled) {
    this.editorConfig.performanceConfig.isClickAfterEndToAddLineEnabled = enabled;
  }

  public void setAutoPairingEnabled(boolean enabled) {
    this.editorConfig.behaviorConfig.isAutoPairingEnabled = enabled;
  }

  public void setAutoBracketNewlineEnabled(boolean enabled) {
    this.editorConfig.behaviorConfig.isAutoBracketNewlineEnabled = enabled;
  }

  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    this.editorConfig.behaviorConfig.isAutoBracketNewlineIndentEnabled = enabled;
  }

  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    this.editorConfig.behaviorConfig.isAutoIndentAfterClosingBracketEnabled = enabled;
  }

  public void setIndentationBlocksEnabled(boolean enabled) {
    if (this.editorConfig.behaviorConfig.isIndentationBlocksEnabled == enabled) return;
    this.editorConfig.behaviorConfig.isIndentationBlocksEnabled = enabled;
    if (!enabled) {
      foldTouchHandler.removeIndentFolds();
    }
    indentGuideEngine.markIntervalsDirty();
    foldState.foldIntervalsDirty = true;
    invalidate();
  }






































  public void setBacktickStringsEnabled(boolean enabled) {
    highlightState.setBacktickStringsEnabled(enabled);
  }













  public void setLayoutDirection(boolean isRtl) {
    if (this.editorConfig.isRtl() == isRtl) return;
    this.editorConfig.setRtl(isRtl);
    lineNumberRenderer.setTextAlign(editorConfig.isRtl());
    foldRenderer.foldMarkerPaint.setTextAlign(editorConfig.isRtl() ? Paint.Align.LEFT : Paint.Align.RIGHT);
    lineNumberRenderer.invalidateCache();
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
    popupConfig.setPopupLabels(copy, cut, paste, delete, selectAll);
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
    if (scaled <= 0f) return editorConfig.paint.getTextSize();
    return editorConfig.paint.getTextSize() / scaled;
  }


  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    selectionHandler.restoreSelection(sL, sC, eL, eC, cursorLine, cursorChar);
  }

  public void showSelectionPopup() {
    popupTouchHandler.showPopupAtSelection();
  }

  // --- Convenience cursor/line accessors ---






  public void insertTextAt(int line, int col, String text) {
    editorTextInserter.insertTextAt(line, col, text);
  }

  public String getTextSnapshot() {
    return editorIO.textIO.getTextSnapshot();
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
    float sizePx = editorConfig.paint.getTextSize();
    handleRenderer.setHandleRadius(
        Math.max(
            4f,
            scaleByTextSize(
                handleRenderer.getBaseHandleRadiusPx(),
                editorConfig.visualConfig.baseCursorTextSizePx,
                sizePx)));
    handleRenderer.setCursorWidth(
        Math.max(1f, scaleByTextSize(handleRenderer.getBaseCursorWidthPx(), editorConfig.visualConfig.baseCursorTextSizePx, sizePx)));

    bracketMatchRenderer.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketMatchRenderer.getBaseStrokeWidth(), bracketMatchRenderer.getBaseTextSizePx(), sizePx)));

    bracketGuideRenderer.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketGuideRenderer.getBaseStrokeWidth(), bracketGuideRenderer.getBaseTextSizePx(), sizePx)));
    indentGuideRenderer.updateForTextSize(sizePx);
  }

  private void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  private void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = editorConfig.paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    editorConfig.paint.setTextSize(sizePx);
    inlinePredictionRenderer.onTextSizeChanged(sizePx);
    lineNumberRenderer.setTextSize(sizePx);
    foldRenderer.foldMarkerPaint.setTextSize(sizePx * foldRenderer.foldMarkerTextScale);
    wrapWordIndicatorRender.updatePaintForTextSize(sizePx, paint);
    editorConfig.lineHeight = editorConfig.paint.getFontSpacing();
    updateTextSizeDependentMetrics();
    updateWhitespaceGuideMetrics();
    lineNumberRenderer.invalidateCache();

    for (com.yn.sodiumeditor.core.HighlightRule rule : highlightState.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    whitespaceGuideRenderer.updateRuleTextSize(sizePx, highlightState.stringHighlightRule, highlightState.blockCommentHighlightRule);
    if (highlightState.lineCommentHighlightRule != null) highlightState.lineCommentHighlightRule.updateTextSize(sizePx);
    highlightState.clearHighlightCaches();

    // Invalidate caches and approximate new max width
    synchronized (editorState.lineWidthCache) {
      editorState.lineWidthCache.clear();
    }
    // Scale the max width instead of recalculating it synchronously.
    // This is an approximation but avoids massive lag.
    float scale = sizePx / oldSize;
    editorState.currentMaxWindowLineWidth *= scale;
    editorState.globalMaxLineWidth *= scale;
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
    return editorConfig.paint.getTextSize();
  }

  public float getPaintFontSpacingPxForZoom() {
    return editorConfig.paint.getFontSpacing();
  }

  int getEditVersionForSearch() {
    return history.getEditVersion();
  }

  float measureTextForSearch(String line, int ch, int globalLine) {
    return highlightRenderer.measureText(line, ch, globalLine);
  }

  float measureTextWithVisualSpacesForSearch(String line, int start, int end) {
    return whitespaceGuideRenderer.measureTextWithVisualSpaces(this, line, start, end, paint);
  }

  void ensureLineInWindowForSearch(int line, boolean immediate) {
    scrollManager.ensureLineInWindow(line, immediate);
  }

  int getWindowStartLineForSearch() {
    return editorState.windowStartLine;
  }

  int getWindowSizeForSearch() {
    return editorState.linesWindow.size();
  }

  boolean isIndexReadyForSearch() {
    return editorIndexState.isIndexReady;
  }

  boolean getSourceFileForSearchExists() {
    return editorIO.document.getSourceFile() != null && editorIO.document.getSourceFile().exists();
  }

  void populateDirectLinesForRangeForSearch(
      int startLine, int endLine, java.util.Map<Integer, String> direct) {
    populateDirectLinesForRange(startLine, endLine, direct);
  }

  String getLineTextForRenderWithDirectForSearch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  public int getWindowStartLineForBracket() {
    return editorState.windowStartLine;
  }

  public int getWindowEndLineForBracket() {
    synchronized (editorState.linesWindow) {
      return editorState.windowStartLine + editorState.linesWindow.size() - 1;
    }
  }

  public int getEditVersionForBracket() {
    return history.getEditVersion();
  }

  public String getLineTextForRenderWithDirectForMatch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  public int getEditVersionForMatch() {
    return history.getEditVersion();
  }

  public boolean isBlockCommentsEnabledForMatch() {
    return editorConfig.behaviorConfig.isBlockCommentsEnabled;
  }

  public boolean isMultiLineStringsEnabledForMatch() {
    return editorConfig.behaviorConfig.isMultiLineStringsEnabled;
  }

  public boolean isBacktickStringsEnabledForMatch() {
    return editorConfig.behaviorConfig.isBacktickStringsEnabled;
  }

  public boolean isTripleQuoteStringsEnabledForMatch() {
    return editorConfig.behaviorConfig.isTripleQuoteStringsEnabled;
  }

  public int getStringStateTripleForMatch() {
    return com.yn.sodiumeditor.state.HighlightState.STRING_STATE_TRIPLE;
  }

  public int getStringStateBacktickForMatch() {
    return com.yn.sodiumeditor.state.HighlightState.STRING_STATE_BACKTICK;
  }



  public float getDrawLineTopForMatch(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  public float getLineHeightForMatch() {
    return editorConfig.lineHeight;
  }

  public float getPaintTextSizeForMatch() {
    return editorConfig.paint.getTextSize();
  }

  public String getLineTextForRenderWithDirectForBracket(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  public boolean isBlockCommentsEnabledForBracket() {
    return editorConfig.behaviorConfig.isBlockCommentsEnabled;
  }

  public boolean isMultiLineStringsEnabledForBracket() {
    return editorConfig.behaviorConfig.isMultiLineStringsEnabled;
  }

  public boolean isBacktickStringsEnabledForBracket() {
    return editorConfig.behaviorConfig.isBacktickStringsEnabled;
  }

  public boolean isTripleQuoteStringsEnabledForBracket() {
    return editorConfig.behaviorConfig.isTripleQuoteStringsEnabled;
  }

  public int getStringStateTripleForBracket() {
    return com.yn.sodiumeditor.state.HighlightState.STRING_STATE_TRIPLE;
  }

  public int getStringStateBacktickForBracket() {
    return com.yn.sodiumeditor.state.HighlightState.STRING_STATE_BACKTICK;
  }


  public boolean isWhitespaceGuidesEnabledForBracket() {
    return whitespaceGuideState.isWhitespaceGuidesEnabled();
  }

  public int getWhitespaceGuideSpaceStepForBracket() {
    return whitespaceGuideState.getSpaceStep();
  }

  public float getPaintTextSizeForBracket() {
    return editorConfig.paint.getTextSize();
  }

  public boolean isRtlForBracket() {
    return editorConfig.isRtl();
  }


  public boolean isHeavyDrawSuppressedForBracket() {
    return isHeavyDrawSuppressed();
  }

  public float getDrawLineTopForBracket(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  public float getLineHeightForBracket() {
    return editorConfig.lineHeight;
  }


  public int getBraceGuideColumnForLineForBracket(
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
    editorInputState.downX = x;
    editorInputState.downY = y;
  }

  public void setMovedSinceDown(boolean moved) {
    editorInputState.movedSinceDown = moved;
  }

  public boolean isMovedSinceDown() {
    return editorInputState.movedSinceDown;
  }

  public boolean isLineNumberSelectionEnabledForInput() {
    return lineNumberState.isLineNumberSelectionEnabled();
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

  public void ensureLineInWindowForInput(int line, boolean reload) {
    scrollManager.ensureLineInWindow(line, reload);
  }

  public String getLineFromWindowLocalForInput(int index) {
    return getLineFromWindowLocal(index);
  }

  public int getWindowStartLineForInput() {
    return editorState.windowStartLine;
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
    return foldState.isCodeFoldingEnabled;
  }

  public void startFoldMarkerRippleForInput(int line) {
    startFoldMarkerRipple(line);
  }

  public float getLineHeightForInput() {
    return editorConfig.lineHeight;
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
    return whitespaceGuideRenderer.measureTextWithVisualSpaces(this, s, start, end, paint);
  }

  public boolean isFoldPlaceholderHitForInput(int line, String ln, float x) {
    return isFoldPlaceholderHit(line, ln, x);
  }

  public boolean isEofForInput() {
    return editorState.isEof;
  }

  public int getLinesWindowSizeForInput() {
    return editorState.linesWindow.size();
  }

  public boolean isLinesWindowEmptyForInput() {
    return editorState.linesWindow.isEmpty();
  }

  public boolean isClickAfterEndToAddLineEnabledForInput() {
    return editorConfig.performanceConfig.isClickAfterEndToAddLineEnabled;
  }

  public void setCursorPositionForInput(int line, int ch) {
    cursorState.setCursorPosition(line, ch);
  }

  public void insertTextAtCursorForInput(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  public void insertStringAtCursorForSuggestion(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  public void setSelectingForInput(boolean selectingNow) {
    selectionState.setSelecting(selectingNow);
  }

  public void updateSuggestionForInput() {
    inlinePredictionEngine.updateSuggestion();
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
    editorConfig.baseTypeface = safeBase;
    int typefaceStyle;
    switch (style) {
      case com.yn.sodiumeditor.config.EditorConfig.STYLE_BOLD:
        typefaceStyle = Typeface.BOLD;
        break;
      case com.yn.sodiumeditor.config.EditorConfig.STYLE_ITALIC:
        typefaceStyle = Typeface.ITALIC;
        break;
      case com.yn.sodiumeditor.config.EditorConfig.STYLE_BOLD_ITALIC:
        typefaceStyle = Typeface.BOLD_ITALIC;
        break;
      default:
        typefaceStyle = Typeface.NORMAL;
        break;
    }
    Typeface finalTypeface = Typeface.create(safeBase, typefaceStyle);
    editorConfig.paint.setTypeface(finalTypeface);
    inlinePredictionRenderer.onEditorTypefaceChanged(finalTypeface);
    lineNumberRenderer.setTypeface(finalTypeface);
    foldRenderer.foldMarkerPaint.setTypeface(finalTypeface);
    wrapWordIndicatorRender.updateTypeface(paint);
    whitespaceGuideRenderer.updateTypeface(paint);
    popupMenuRenderer.onEditorTypefaceChanged(finalTypeface);
    whitespaceGuideRenderer.updateRuleTypeface(safeBase, highlightState.stringHighlightRule, highlightState.blockCommentHighlightRule);
    if (highlightState.lineCommentHighlightRule != null) highlightState.lineCommentHighlightRule.updateTypeface(safeBase);
    for (com.yn.sodiumeditor.core.HighlightRule rule : highlightState.highlightRules) {
      rule.updateTypeface(safeBase);
    }
    highlightState.clearHighlightCaches();

    editorConfig.lineHeight = editorConfig.paint.getFontSpacing();
    updateWhitespaceGuideMetrics();
    lineNumberRenderer.invalidateCache();
    synchronized (editorState.lineWidthCache) {
      editorState.lineWidthCache.clear();
    }
    editorState.currentMaxWindowLineWidth = 0f;
    editorState.globalMaxLineWidth = 0f;
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
    whitespaceGuideRenderer.updateMetrics(paint, editorConfig.visualConfig.WHITESPACE_GUIDE_SPACE, editorConfig.visualConfig.WHITESPACE_GUIDE_TAB);

  }

  public void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      @Nullable java.util.HashMap<Integer, String> directLines) {
    highlightState.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, directLines);
  }

  public void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    highlightState.maybeEnsureHighlightCacheForRange(startLine, endLine, directLines);
  }

  public void invalidateHighlightEnsureRange() {
    highlightState.resetEnsureRange();
  }

  // --- Layout and Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float oldGutterWidth = lineNumberState.getLineNumbersGutterWidth();
    if (lineNumberState.isShowLineNumbers()) {
      int maxLines;
      if (editorIndexState.isIndexReady) {
        maxLines = editorIndexState.lineOffsets.length;
      } else if (editorState.isEof) {
        maxLines = editorState.windowStartLine + editorState.linesWindow.size();
      } else {
        maxLines = 999999; // Wider fallback for width calculation until index is ready
      }
      if (foldState.isCodeFoldingEnabled) {
        foldRenderer.foldMarkerGutterWidth =
            foldRenderer.foldMarkerPaint.measureText("v") + foldRenderer.foldMarkerSpacing + foldRenderer.foldMarkerEdgePadding;
      } else {
        foldRenderer.foldMarkerGutterWidth = 0f;
      }
      lineNumberState.setLineNumbersGutterWidth(
          lineNumberRenderer.computeGutterWidth(
              maxLines, foldState.isCodeFoldingEnabled, foldRenderer.foldMarkerGutterWidth));
    } else {
      lineNumberState.setLineNumbersGutterWidth(0f);
    }

    if (wrapWordState.isWordWrapEnabled && Math.abs(lineNumberState.getLineNumbersGutterWidth() - oldGutterWidth) > 0.1f) {
      wrapWordBuilder.invalidate(true, true);
      wrapWordBuilder.requestPrefixRebuild(this);
    }
    if (Math.abs(lineNumberState.getLineNumbersGutterWidth() - oldGutterWidth) > 0.1f) {
      lineNumberRenderer.invalidateCache();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != oldw || h != oldh) {
      lineNumberRenderer.invalidateCache();
    }
    if (w != oldw) {
      scrollManager.maxScrollXForScroll = 0f;
      scrollManager.maxTextStartXForScroll = 0f;
    }
    int minWindow = computeMinWindowSize();
    if (editorState.windowSize < minWindow) {
      editorState.windowSize = minWindow;
      reloadWindowAroundVisible(false);
    }
    if (wrapWordState.isWordWrapEnabled && w != oldw) {
      wrapWordBuilder.invalidate(true, true);
      wrapWordBuilder.requestPrefixRebuild(this);
    }
  }

  public float getTextStartX() {
    return lineNumberRenderer.getTextStartX(editorConfig.paddingLeft, isRtl);
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
    return lineNumberRenderer.getTextAvailableWidth(getWidth(), editorConfig.paddingLeft);
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
    synchronized (editorState.linesWindow) {
      return Math.max(0, editorState.windowStartLine + editorState.linesWindow.size() - 1);
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
    return lineNumberRenderer.getLineNumberViewLeft(getWidth(), isRtl);
  }

  private boolean isInLineNumberGutter(float x) {
    return lineNumberRenderer.isInLineNumberGutter(x, getGutterStartX());
  }

  private void beginLineNumberSelection(int line) {
    int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(line, editorState.isEof, editorState.windowStartLine, editorState.linesWindow.size());
    if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(getLineTextForRender(clamped))) return;
    inlinePredictionState.clearActiveSuggestion();
    selectionState.setLineNumberSelecting(true, clamped);
    selectionState.setSelectAllState(false, false);
    String lineText = getLineTextForRender(clamped);
    selectionState.setSelection(clamped, 0, clamped, lineText.length(), true);
    cursorState.setCursorPosition(clamped, selectionState.selEndChar);
    popupTouchHandler.hidePopup();
    cursorAnimator.resetCursorBlink();
    invalidate();
  }

  private void updateLineNumberSelection(int line) {
    if (!selectionState.isLineNumberSelecting()) return;
    int clamped = com.yn.sodiumeditor.utils.SelectionUtils.clampLineForSelection(line, editorState.isEof, editorState.windowStartLine, editorState.linesWindow.size());
    if (!com.yn.sodiumeditor.utils.SelectionUtils.isLineSelectable(getLineTextForRender(clamped))) return;
    int anchorLine = selectionState.getLineNumberSelectAnchorLine();
    int startLine = Math.min(anchorLine, clamped);
    int endLine = Math.max(anchorLine, clamped);
    scrollManager.ensureLineInWindow(endLine, true);
    String endLineText = getLineTextForRender(endLine);
    selectionState.setSelection(startLine, 0, endLine, endLineText.length(), true);
    cursorState.setCursorPosition(endLine, selectionState.selEndChar);
    selectionState.setLineNumberSelecting(true, anchorLine);
    popupTouchHandler.hidePopup();
    invalidate();
  }

  private String buildFoldDisplayLine(String line, com.yn.sodiumeditor.state.FoldRange range, int[] placeholderBoundsOut) {
    return foldRenderer.buildFoldDisplayLine(line, range, placeholderBoundsOut);
  }

  public String buildFoldDisplayLineInternal(String line, com.yn.sodiumeditor.state.FoldRange range, int[] placeholderBoundsOut) {
    if (line == null) line = "";
    int placeholderStart = 0;
    int placeholderEnd = 0;
    String display;

    if (range.isBlockComment) {
      int safeIdx = Math.max(0, Math.min(range.openCharIndex, line.length()));
      String prefix = line.substring(0, safeIdx);
      placeholderStart = prefix.length() + 2;
      placeholderEnd = placeholderStart + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + "/*" + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT + "*/";
    } else if (range.isIndentFold) {
      String prefix = line;
      placeholderStart = prefix.length();
      placeholderEnd = placeholderStart + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT;
    } else {
      int safeIdx = Math.max(0, Math.min(range.openCharIndex, Math.max(0, line.length() - 1)));
      String prefix = line.substring(0, safeIdx + 1);
      placeholderStart = prefix.length();
      placeholderEnd = placeholderStart + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT.length();
      display = prefix + editorConfig.visualConfig.FOLD_PLACEHOLDER_TEXT + range.closeChar;
    }

    if (placeholderBoundsOut != null && placeholderBoundsOut.length >= 2) {
      placeholderBoundsOut[0] = placeholderStart;
      placeholderBoundsOut[1] = placeholderEnd;
    }
    return display;
  }

  public void drawFoldedLine(Canvas canvas, String line, int globalLine) {
    foldRenderer.drawFoldedLine(canvas, line, globalLine);
  }

  private boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
    return foldTouchHandler.isFoldPlaceholderHit(globalLine, line, localX);
  }

  private String getFoldMarkerForLine(int line, @Nullable String lineText) {
    return foldRenderer.getFoldMarkerForLine(line, lineText);
  }

  String getFoldMarkerForLineInternal(int line, @Nullable String lineText) {
    return foldRenderer.getFoldMarkerForLine(line, lineText);
  }

  private boolean isIndentFoldCandidate(String line) {
    return foldRenderer.isIndentFoldCandidate(line);
  }

  private void startFoldMarkerRipple(int line) {
    foldTouchHandler.startFoldMarkerRipple(line);
  }

  private void clearFoldRipple() {
    foldTouchHandler.clearFoldRipple();
  }

  private boolean shouldShowFoldMarkerFromLine(String line) {
    return foldRenderer.shouldShowFoldMarkerFromLine(line);
  }



  public boolean superOnKeyDown(int keyCode, KeyEvent event) {
    return super.onKeyDown(keyCode, event);
  }

  public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
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
    float avg = highlightRenderer.getAverageCharWidthForLine((lineText == null) ? "" : lineText, globalLine);
    if (avg <= 0f) avg = paint.measureText(" ");
    float viewLeft = lineNumberRenderer.getContentViewLeft(isRtl);
    float viewRight = lineNumberRenderer.getContentViewRight(getWidth(), isRtl);
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
    int pad = Math.max(0, editorConfig.visualConfig.visibleCharPadding);
    start = Math.max(0, start - pad);
    end = Math.min(len, end + pad);
    int visibleLen = Math.max(0, end - start);
    int maxExtra = Math.max(0, editorState.colsWidthCacheSize - visibleLen);
    int extraPad = Math.min(Math.max(0, editorState.prefetchCols), maxExtra / 2);
    start = Math.max(0, start - extraPad);
    end = Math.min(len, end + extraPad);
    out[0] = start;
    out[1] = end;
  }

  public int getInitialStreamedSliceSize() {
    int base = Math.max(128, editorState.colsWidthCacheSize);
    int pad = Math.max(0, editorState.prefetchCols) * 2;
    return Math.max(base, pad);
  }

  public void drawFoldMarkersForVisibleLines(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    foldRenderer.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
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
    float x = whitespaceGuideRenderer.measureTextWithVisualSpaces(this, line, segStart, at, paint);
    Paint ghostPaint = (charAnimator.getDelAnimPaint() != null) ? charAnimator.getDelAnimPaint() : paint;
    Paint tempPaint = charAnimator.getTempPaint();
    tempPaint.set(ghostPaint);
    tempPaint.setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimator.getDelAnimAlpha()))));
    canvas.drawText(charAnimator.getDelAnimText(), x, y, tempPaint);
  }



  public boolean isMixedDirectionText(CharSequence text, int start, int end) {
    return com.yn.sodiumeditor.utils.TextUtils.isMixedDirectionText(text, start, end);
  }

  public int getVisualSpaceScale() {
    return 1;
  }

  private boolean isWhitespaceAtX(String line, int globalLine, float x) {
    return viewRender.textRender.isWhitespaceAtX(line, globalLine, x);
  }

  public boolean isIndentationBlocksEnabledForIndentGuides() {
    return editorConfig.behaviorConfig.isIndentationBlocksEnabled;
  }

  public boolean isHeavyDrawSuppressedForIndentGuides() {
    return isHeavyDrawSuppressed();
  }

  public float getIndentGuideLineTop(int globalLine) {
    return scrollManager.getDrawLineTop(globalLine);
  }

  public float getIndentGuideLineHeight() {
    return editorConfig.lineHeight;
  }

  public int getIndentGuideTabSize() {
    return com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES;
  }

  public String getIndentGuideUnit() {
    return editorConfig.visualConfig.INDENT_BLOCK_UNIT;
  }

  public float measureTextWithVisualSpacesForIndentGuides(String line, int start, int end) {
    return whitespaceGuideRenderer.measureTextWithVisualSpaces(this, line, start, end, paint);
  }

  public boolean isWhitespaceAtXForIndentGuides(String line, int globalLine, float x) {
    return isWhitespaceAtX(line, globalLine, x);
  }

  public boolean hasIndentGuideFoldRanges() {
    return foldState.hasFoldRanges();
  }

  public Iterable<com.yn.sodiumeditor.state.FoldRange> getIndentGuideFoldRanges() {
    return foldState.getFoldRanges();
  }

  public float getIndentGuideTextSizePx() {
    return editorConfig.paint.getTextSize();
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
    if (editorIO.document.getSourceFile() == null || editorIO.isFileCleared()) {
      return;
    }
    if (editorState.isWindowLoading) return;

    boolean inside =
        firstVisibleLine >= editorState.windowStartLine
            && firstVisibleLine < editorState.windowStartLine + editorState.linesWindow.size();
    if (!inside) {
      int targetStart = Math.max(0, firstVisibleLine - editorState.prefetchLines);
      loadWindowAround(targetStart, null, false);
    }
  }

  public void checkAndLoadWindow() {
    viewRender.checkAndLoadWindow();
  }

  public void loadWindowAround(int startLine, @Nullable Runnable onComplete) {
    viewRender.loadWindowAround(startLine, onComplete);
  }

  public void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
    viewRender.loadWindowAround(startLine, onComplete, recalculateWidthSync);
  }

  public boolean shouldHideCopyCutForSelection() {
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
    editorIO.ioTaskVersion.incrementAndGet();
    editorIO.ioHandler.removeCallbacksAndMessages(null);
    highlightState.clearHighlightCaches();
    if (foldState.isCodeFoldingEnabled) {
      foldTouchHandler.clearAllFolds();
      indentGuideEngine.markIntervalsDirty();
    }
  }

  public void clearContent() {
    editorIO.document.clearContent();
  }

  public void loadFromFile(final File file) {
    editorIO.document.loadFromFile(file);
  }

  public void updateSourceFile(File file) {
    editorIO.document.updateSourceFile(file);
  }

  public int getEditVersionValue() {
    return history.getEditVersion();
  }

  public void refreshLineNumberCache() {
    lineNumberRenderer.invalidateCache();
    requestLayout();
    invalidate();
  }

  public void setTextColor(int color) {
    editorConfig.paint.setColor(color);
    invalidate();
  }

  public void setReadOnly(boolean readOnly) {
    if (editorConfig.behaviorConfig.isReadOnly == readOnly) return;
    editorConfig.behaviorConfig.isReadOnly = readOnly;
    if (readOnly) {
      inlinePredictionState.clearActiveSuggestion();
      selectionState.clearSelectionKeepLineNumberState();
      popupTouchHandler.hidePopup();
      InputMethodManager imm =
          (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
    restartInput();
    invalidate();
  }

  public void setDisable(boolean disable) {
    editorConfig.behaviorConfig.isDisabled = disable;
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
    editorIO.document.setShowLoadingOnFileOpen(enabled);
  }

  private boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike) {
    int span = Math.abs(eL - sL) + 1;
    return isSelectAllLike || span >= editorLoadingState.LARGE_EDIT_LINES;
  }

  public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
    if (!enable) return;
    if (!shouldShowLargeEditUi(sL, eL, isSelectAllLike)) return;

    final int token = editorLoadingState.largeEditUiToken.incrementAndGet();
    setDisable(true);
    loadingCircleAnimator.show(true);

    // Watchdog: force hide after a short time in case any path forgets to hide.
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    mainHandler.postDelayed(largeEditUiWatchdog, 1500);

    // Also ensure token validity for later hides.
    post(
        () -> {
          if (token != editorLoadingState.largeEditUiToken.get()) return;
        });
  }

  private void endLargeEditUi(boolean invalidate) {
    editorLoadingState.largeEditUiToken.incrementAndGet();
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    setDisable(false);
    loadingCircleAnimator.show(false);
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
    editorIO.textIO.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
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
    final int currentGoToLineVersion = editorIndexState.incrementGoToLineVersion();
    setDisable(true);
    loadingCircleAnimator.show(true);

    if (selectionState.hasSelection()) {
      selectionState.clearSelectionKeepLineNumberState();
      selectionState.setSelecting(false);
      popupTouchHandler.hidePopup();
    }

    final int requestedLine = Math.max(0, line - 1);
    final int requestedCol = Math.max(0, col - 1);

    Integer knownTotal = null;

    if (editorIO.sourceFile == null || editorIO.isFileCleared) {
      // In-memory mode: the "editorIO.document" is exactly what we have in memory.
      synchronized (editorState.linesWindow) {
        knownTotal = Math.max(1, editorState.windowStartLine + editorState.linesWindow.size());
      }
    } else if (editorIndexState.isIndexReady) {
      synchronized (editorIndexState.lineOffsetsLock) {
        knownTotal = Math.max(1, editorIndexState.lineOffsets.length);
      }
    } else if (editorState.isEof) {
      synchronized (editorState.linesWindow) {
        knownTotal = Math.max(1, editorState.windowStartLine + editorState.linesWindow.size());
      }
    }

    if (knownTotal != null) {
      int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
      cursorNavigation.proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      countTotalLines(
          totalLines -> {
            if (currentGoToLineVersion != editorIndexState.getGoToLineVersion()) return;
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
  private int hideCopyCutMaxLines = editorLoadingState.HIDE_COPY_CUT_LINES;
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
    inlinePredictionState.clearActiveSuggestion(); // Clear suggestion when pasting

    ClipboardManager cm =
        (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(getContext());
    if (txt == null) return;
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(txt.toString());
    inlinePredictionEngine.updateSuggestion(); // Update suggestion after pasting
  }

  public void actionPaste() {
    pasteFromClipboard();
  }

  interface LineCountCallback {
    void onResult(int count);
  }

  private void countTotalLines(LineCountCallback callback) {
    editorIO.lineIndex.countTotalLines((total) -> callback.onResult(total));
  }

  public String readRangeText(int sL, int sC, int eL, int eC) {
    return editorIO.textIO.readRangeText(sL, sC, eL, eC);
  }

  public long computeByteRangeFastOrScanPublic(File file, int sL, int sC, int eL, int eC) {
    LineIndex.RangeBytes range = editorIO.lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
    return (range != null) ? range.startByte : 0;
  }

  public LineIndex.RangeBytes computeByteRangeFastOrScanPublicFull(File file, int sL, int sC, int eL, int eC) {
    return editorIO.lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public boolean isIndexBuildingPublic() {
    return editorIndexState.isIndexBuilding;
  }

  public boolean isIndexDisabledPublic() {
    return editorIndexState.isIndexDisabled;
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
    inlinePredictionState.clearActiveSuggestion(); // Clear suggestion when deleting selection
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
    return editorIO.lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  LineIndex.RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
    return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  public Handler getIoHandlerForUndo() {
    return editorIO.ioHandler;
  }

  public void onUndoRedoRewriteSuccess(File inFile) {
    editorIO.textIO.onUndoRedoRewriteSuccess(inFile);
  }

  private long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    return editorIO.lineIndex.findLineStartByteByScanning(raf, targetLine);
  }

  public String readLineUtf8AtByte(RandomAccessFile raf, long byteOffset) throws Exception {
    return editorIO.textIO.readLineUtf8AtByte(raf, byteOffset);
  }

  public long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
      throws Exception {
    return editorIO.lineIndex.getLineByteLengthFromIndex(raf, line, fileLen);
  }

  public String readLineSliceAtByte(
      RandomAccessFile raf, long lineStart, long lineByteLen, int startChar, int endChar)
      throws Exception {
    return editorIO.textIO.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar);
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
    return editorIO.textIO.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength);
  }

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return editorIO.textIO.computeByteOffsetInLineUtf8(lineText, charIndex);
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
    return editorIO.textIO.bytesToControlVisible(buf, len);
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
    return editorIO.document.reopenReaderAtStart();
  }


  private void updateLocalLine(int localIdx, String text) {
    viewRender.textRender.updateLocalLine(localIdx, text);
  }

  public String getLineFromWindowLocal(int localIdx) {
    return viewRender.textRender.getLineFromWindowLocal(localIdx);
  }

  private int getStreamLineThreshold() {
    return Math.max(4096, highlightState.maxSyntaxLineLength);
  }

  private boolean shouldStreamLineLength(int length) {
    return editorIO.textIO.shouldStreamLineLength(length);
  }

  private int getStreamedLineLength(int globalLine) {
    return editorIO.textIO.getStreamedLineLength(globalLine);
  }

  public int getStreamedLineSliceStart(int globalLine) {
    return editorIO.textIO.getStreamedLineSliceStart(globalLine);
  }

  private void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    editorIO.textIO.setStreamedLineInfo(globalLine, length, sliceStart);
  }

  public void clearStreamedLineInfo(int globalLine) {
    editorIO.textIO.clearStreamedLineInfo(globalLine);
  }

  public void clearStreamedLineCaches() {
    editorIO.textIO.clearStreamedLineCaches();
  }

  private boolean isSingleByteCharset() {
    return editorIO.document.isSingleByteCharset();
  }

  public int getLogicalLineLength(int globalLine, @Nullable String line) {
    return editorIO.textIO.getLogicalLineLength(globalLine, line);
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
    inlinePredictionState.clearActiveSuggestion(); // Clear suggestion on focus change
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
      popupTouchHandler.hidePopup();
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
    editorIO.document.buildFileIndex();
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
    return com.yn.sodiumeditor.core.BracketMatchEngine.isOpeningBracket(c);
  }

  char matchingBracket(char c) {
    return com.yn.sodiumeditor.core.BracketMatchEngine.matchingBracket(c);
  }

  public void populateDirectLinesForRange(int startLine, int endLine, java.util.Map<Integer, String> direct) {
    viewRender.textRender.populateDirectLinesForRange(startLine, endLine, direct);
  }


  public int getVisibleLineCount() {
    return viewRender.textRender.getVisibleLineCount();
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    return foldState.mapVisibleIndexToGlobal(visibleIndex, getLinesCount());
  }

  public int getVisibleIndexForGlobalLine(int globalLine) {
    return foldState.getVisibleIndexForGlobalLine(globalLine);
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
    return editorInputState.downX;
  }

  public void setDownXPublic(float value) {
    editorInputState.downX = value;
  }

  public float getDownYPublic() {
    return editorInputState.downY;
  }

  public void setDownYPublic(float value) {
    editorInputState.downY = value;
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
    return editorInputState.touchSlop;
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
    return editorIndexState.getGoToLineVersion();
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
      return editorIndexState.isIndexReady;
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
      SodiumEditor.this.invalidate();
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
      return SodiumEditor.this.comparePos(sL, sC, eL, eC);
    }

    @Override
    public void invalidate() {
      SodiumEditor.this.invalidate();
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
      SodiumEditor.this.deleteSelection();
    }

    @Override
    public void post(Runnable r) {
      SodiumEditor.this.post(r);
    }

    @Override
    public void postDelayed(Runnable r, long delayMillis) {
      SodiumEditor.this.postDelayed(r, delayMillis);
    }

    @Override
    public Context getContext() {
      return SodiumEditor.this.getContext();
    }

    @Override
    public boolean isFileCleared() {
      return editorIO.isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return editorIO.sourceFile;
    }

    @Override
    public boolean isIndexReady() {
      return editorIndexState.isIndexReady;
    }

    @Override
    public long[] getLineOffsets() {
      return editorIndexState.lineOffsets;
    }

    @Override
    public Object getLineOffsetsLock() {
      return editorIndexState.lineOffsetsLock;
    }

    @Override
    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception {
      return findLineStartByteByScanning(raf, line);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return editorState.modifiedLines;
    }

    @Override
    public int getWindowStartLine() {
      return editorState.windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return editorState.linesWindow;
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
      return editorIO.document.fileCharset;
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
      loadingCircleAnimator.show(show);
    }

    @Override
    public void showPopupAtSelection() {
      popupTouchHandler.showPopupAtSelection();
    }

    @Override
    public void hidePopup() {
      popupTouchHandler.hidePopup();
    }

    @Override
    public void requestFocus() {
      SodiumEditor.this.requestFocus();
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
      inlinePredictionState.clearActiveSuggestion();
    }

    @Override
    public int getPrefetchLines() {
      return editorState.prefetchLines;
    }

    @Override
    public boolean isEof() {
      return editorState.isEof;
    }

    @Override
    public boolean isIndexBuilding() {
      return editorIndexState.isIndexBuildingPublic();
    }

    @Override
    public boolean isIndexDisabled() {
      return editorIndexState.isIndexDisabledPublic();
    }

    @Override
    public void buildFileIndex() {
      SodiumEditor.this.buildFileIndex();
    }

    @Override
    public void loadWindowAround(int targetStart, Runnable onComplete) {
      SodiumEditor.this.loadWindowAround(targetStart, onComplete);
    }

    @Override
    public void countTotalLines(com.yn.sodiumeditor.input.SelectionHandler.OnTotalLinesCounted callback) {
      editorIO.fileManager.countTotalLines(callback::onCounted);
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
      return SodiumEditor.this.getWidth();
    }

    @Override
    public float getTextStartX() {
      return SodiumEditor.this.getTextStartX();
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
      return wrapWordBuilder.isMetricsUsableForWindow(SodiumEditor.this, widthPx);
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
      return handleRenderer.getSelectionHandleColor();
    }

    @Override
    public void setSelectionHandleColor(int color) {
      handleRenderer.setSelectionHandleColor(color);
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
      return SodiumEditor.this.comparePos(sL, sC, eL, eC);
    }

    @Override
    public boolean isFileCleared() {
      return editorIO.isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return editorIO.sourceFile;
    }

    @Override
    public boolean isIndexReady() {
      return editorIndexState.isIndexReady;
    }

    @Override
    public long[] getLineOffsets() {
      return editorIndexState.lineOffsets;
    }

    @Override
    public Object getLineOffsetsLock() {
      return editorIndexState.lineOffsetsLock;
    }

    @Override
    public long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception {
      return findLineStartByteByScanning(raf, line);
    }

    @Override
    public java.util.HashMap<Integer, String> getModifiedLines() {
      return editorState.modifiedLines;
    }

    @Override
    public int getWindowStartLine() {
      return editorState.windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return editorState.linesWindow;
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
      return editorIO.document.fileCharset;
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
      return editorState.isEof;
    }

    @Override
    public int getWindowStartLine() {
      return editorState.windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return editorState.linesWindow;
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
      popupTouchHandler.hidePopup();
    }

    @Override
    public void resetCursorBlink() {
      cursorAnimator.resetCursorBlink();
    }

    @Override
    public void invalidate() {
      SodiumEditor.this.invalidate();
    }

    @Override
    public void keepCursorVisibleHorizontally() {
      scrollManager.keepCursorVisibleHorizontally();
    }

    @Override
    public void inlinePredictionUpdate() {
      inlinePredictionEngine.updateSuggestion();
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
      return SodiumEditor.this.comparePos(sL, sC, eL, eC);
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
      return handleRenderer.getCaretColor();
    }

    @Override
    public float getCursorWidth() {
      return handleRenderer.getCursorWidth();
    }

    @Override
    public float getLineHeight() {
      return editorConfig.lineHeight;
    }
  }

  // IME Composition Callback
  private class ImeCompositionCallback implements com.yn.sodiumeditor.input.ImeCompositionHandler.CompositionCallback {
    @Override
    public boolean isReadOnly() {
      return editorConfig.behaviorConfig.isReadOnly;
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
      return editorState.isWindowLoading;
    }

    @Override
    public int getWindowStartLine() {
      return editorState.windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return editorState.linesWindow;
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
      return editorState.modifiedLines;
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
      SodiumEditor.this.invalidate();
    }

    @Override
    public void inlinePredictionUpdate() {
      inlinePredictionEngine.updateSuggestion();
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
      return highlightRenderer.getPaintForChar(line, at, base);
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
      return editorConfig.behaviorConfig.isReadOnly;
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
      return editorIO.isFileCleared;
    }

    @Override
    public @Nullable java.io.File getSourceFile() {
      return editorIO.sourceFile;
    }

    @Override
    public boolean isLargePasteText(String text) {
      return SodiumEditor.isLargePasteText(text);
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
      SodiumEditor.this.postDelayed(r, delayMillis);
    }

    @Override
    public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget computeCursorAfterInsert(int line, int ch, String text) {
      SodiumEditor.CursorTarget target = SodiumEditor.this.computeCursorAfterInsertForUndo(line, ch, text);
      return new com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget(target.line, target.ch);
    }

    @Override
    public void rewriteReplaceRangeAsync(int opToken, java.io.File inFile, int sL, int sC, int eL, int eC, String text, com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target, boolean finishLargeEdit) {
      rewriteReplaceRangeAsyncPublic(opToken, inFile, sL, sC, eL, eC, text, new SodiumEditor.CursorTarget(target.line, target.ch), finishLargeEdit);
    }

    @Override
    public void inlinePredictionUpdate() {
      inlinePredictionEngine.updateSuggestion();
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
      return editorState.isWindowLoading;
    }

    @Override
    public int getWindowStartLine() {
      return editorState.windowStartLine;
    }

    @Override
    public java.util.List<String> getLinesWindow() {
      return editorState.linesWindow;
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
      return editorState.modifiedLines;
    }

    @Override
    public void removeLineWidthCache(int line) {
      editorState.lineWidthCache.remove(line);
    }

    @Override
    public void clearLineWidthCache() {
      editorState.lineWidthCache.clear();
    }

    @Override
    public void addLinesWindowAll(int index, java.util.List<String> lines) {
      editorState.linesWindow.addAll(index, lines);
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
      return lineNumberState.isShowLineNumbers();
    }

    @Override
    public void requestLayout() {
      requestLayout();
    }

    @Override
    public void onLineCountChanged(int delta) {
      wrapWordBuilder.onLineCountChanged(SodiumEditor.this);
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
      SodiumEditor.this.invalidate();
    }

    @Override
    public int getLineLength(int line) {
      String ln = getLineTextForRender(line);
      return ln != null ? ln.length() : 0;
    }
  }

  private class HighlightParserCallbackImpl implements com.yn.sodiumeditor.core.HighlightParser.HighlightParserCallback {
    @Override
    public boolean isTripleQuoteStringsEnabled() {
      return editorConfig.behaviorConfig.isTripleQuoteStringsEnabled;
    }

    @Override
    public boolean isBacktickStringsEnabled() {
      return editorConfig.behaviorConfig.isBacktickStringsEnabled;
    }

    @Override
    public boolean isBlockCommentsEnabled() {
      return editorConfig.behaviorConfig.isBlockCommentsEnabled;
    }

    @Override
    public java.util.List<String> getLineCommentDelimiters() {
      return highlightState.lineCommentDelimiters;
    }

    @Override
    public boolean isLineCommentStart(String line, int index) {
      if (index < 0 || index >= line.length()) return false;
      for (String token : highlightState.lineCommentDelimiters) {
        int len = token.length();
        if (len == 0) continue;
        if (index + len > line.length()) continue;
        if (len == 1) {
          if (line.charAt(index) == token.charAt(0) && !com.yn.sodiumeditor.core.HighlightParser.isTokenEscaped(line, index)) {
            return true;
          }
        } else {
          if (line.regionMatches(index, token, 0, len) && !com.yn.sodiumeditor.core.HighlightParser.isTokenEscaped(line, index)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
