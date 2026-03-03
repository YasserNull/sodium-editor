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
  public final com.yn.sodiumeditor.input.EditorOperations editorOperations;
  @Nullable ValueAnimator flingStopAnimator;
  public final InputMethodHandler imeManager;
  public final ScrollEngine scrollManager;
  public final com.yn.sodiumeditor.input.FocusChangeHandler focusChangeHandler;
  public final com.yn.sodiumeditor.input.TypefaceManager typefaceManager;

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
  public final com.yn.sodiumeditor.renderer.CharAnimationRenderer charAnimationRenderer;
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

  public final ViewRender viewRender = new ViewRender(this);

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
    lineNumberRenderer.initDefaults(editorConfig.paint, density);

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
    wrapWordIndicatorRender.init(editorConfig.paint, density);

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
    charAnimationRenderer = new com.yn.sodiumeditor.renderer.CharAnimationRenderer(this, charAnimator);
    inputManager = new InputManager(this, ctx);
    editorOperations = new com.yn.sodiumeditor.input.EditorOperations(this);
    focusChangeHandler = new com.yn.sodiumeditor.input.FocusChangeHandler(
        this, cursorState, selectionState, inlinePredictionState, popupMenuState, cursorAnimator);
    typefaceManager = new com.yn.sodiumeditor.input.TypefaceManager(this);

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
                getLocationInWindow(editorInputState.tmpLocationInWindow);
                int viewBottom = editorInputState.tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - imeTop);
                newKeyboardHeight = Math.min(overlap, getHeight());
              } else {
                getWindowVisibleDisplayFrame(editorInputState.visibleDisplayFrame);
                getLocationInWindow(editorInputState.tmpLocationInWindow);
                int viewBottom = editorInputState.tmpLocationInWindow[1] + getHeight();
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
    inlinePredictionRenderer.initPaints(editorConfig.paint);

    pathUnderlineRenderer.setPathUnderliningEnabled(true); // Enable path underlining by default
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
    return whitespaceGuideRenderer.measureTextWithVisualSpaces(this, s, start, end, editorConfig.paint);
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

  public void applyTypeface(@Nullable Typeface typeface, int style) {
    typefaceManager.applyTypeface(typeface, style);
  }

  public void updateWhitespaceGuideMetrics() {
    whitespaceGuideRenderer.updateMetrics(editorConfig.paint, editorConfig.visualConfig.WHITESPACE_GUIDE_SPACE, editorConfig.visualConfig.WHITESPACE_GUIDE_TAB);

  }

  public void ensureHighlightCacheForVisibleRange(
      int firstVisibleLine,
      int lastVisibleLine,
      @Nullable java.util.HashMap<Integer, String> directLines) {
    highlightRenderer.ensureHighlightCacheForVisibleRange(firstVisibleLine, lastVisibleLine, directLines);
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
    viewRender.onMeasure();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    viewRender.onSizeChanged(w, h, oldw, oldh);
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

  public void beginLineNumberSelection(int line) {
    selectionHandler.beginLineNumberSelection(line);
  }

  public void updateLineNumberSelection(int line) {
    selectionHandler.updateLineNumberSelection(line);
  }

  public String buildFoldDisplayLineInternal(String line, com.yn.sodiumeditor.state.FoldRange range, int[] placeholderBoundsOut) {
    return foldRenderer.buildFoldDisplayLineInternal(line, range, placeholderBoundsOut);
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
    highlightRenderer.computeStreamedSliceBounds(lineText, globalLine, lineLength, out);
  }

  public int getInitialStreamedSliceSize() {
    return highlightRenderer.getInitialStreamedSliceSize();
  }

  public void drawDeleteAnimationForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
    charAnimationRenderer.drawDeleteAnimationForSegment(canvas, line, globalLine, segStart, segEnd, y);
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
    return whitespaceGuideRenderer.measureTextWithVisualSpaces(this, line, start, end, editorConfig.paint);
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

  public int getBraceGuideColumnForLineForBracket(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    return indentGuideEngine.getBraceGuideColumnForLine(line, globalLine, braceIndex, firstNonSpace);
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

  public void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
    viewRender.maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);
  }

  public void maybeKickWindowLoad(int firstVisibleLine) {
    viewRender.maybeKickWindowLoad(firstVisibleLine);
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
    return selectionHandler.shouldHideCopyCutForSelection();
  }

  public void setCopyCutMaxLines(long maxLines) {
    editorLoadingState.copyCutMaxLines = Math.max(1L, maxLines);
  }

  public void setCopyCutMaxChars(int maxChars) {
    editorLoadingState.copyCutMaxChars = Math.max(1, maxChars);
  }

  public void setHideCopyCutMaxLines(int maxLines) {
    editorLoadingState.hideCopyCutMaxLines = Math.max(1, maxLines);
  }

  public void setReplaceAllMaxCount(int maxCount) {
    editorLoadingState.replaceAllMaxCount = Math.max(1, maxCount);
  }

  public int getReplaceAllMaxCount() {
    return editorLoadingState.replaceAllMaxCount;
  }

  public void setHideKeyboardOnFocusLoss(boolean enabled) {
    focusChangeHandler.setHideKeyboardOnFocusLoss(enabled);
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
    scrollManager.abortScrollAnimationForZoom();
  }

  public void invalidatePendingIOForEdit() {
    editorIO.invalidatePendingIOForEdit(this);
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
    editorConfig.behaviorConfig.setReadOnly(readOnly, this);
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

  public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
    loadingCircleAnimator.beginLargeEditUiIfNeeded(
        enable, sL, eL, isSelectAllLike,
        editorLoadingState.LARGE_EDIT_LINES,
        mainHandler,
        largeEditUiWatchdog,
        this,
        editorLoadingState.largeEditUiToken);
  }

  public void endLargeEditUi(boolean invalidate) {
    loadingCircleAnimator.endLargeEditUi(
        invalidate,
        this,
        editorLoadingState.largeEditUiToken,
        mainHandler,
        largeEditUiWatchdog);
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

  public static boolean isLargePasteText(String text) {
    return com.yn.sodiumeditor.input.EditorOperations.isLargePasteText(text);
  }

  public void goToLine(int line) {
    goToLine(line, 1);
  }

  public void goToLine(int line, int col) {
    cursorNavigation.goToLine(line, col, new GoToLineWithUICallbackImpl());
  }

  private class GoToLineWithUICallbackImpl implements com.yn.sodiumeditor.core.CursorNavigation.GoToLineWithUICallback {
    @Override
    public int incrementGoToLineVersion() {
      return editorIndexState.incrementGoToLineVersion();
    }

    @Override
    public void setDisable(boolean disable) {
      SodiumEditor.this.setDisable(disable);
    }

    @Override
    public void showLoadingCircle(boolean show) {
      loadingCircleAnimator.show(show);
    }

    @Override
    public boolean hasSelection() {
      return selectionState.hasSelection();
    }

    @Override
    public void clearSelection() {
      selectionState.clearSelectionKeepLineNumberState();
    }

    @Override
    public void setSelecting(boolean selecting) {
      selectionState.setSelecting(selecting);
    }

    @Override
    public void hidePopup() {
      popupTouchHandler.hidePopup();
    }

    @Override
    public Integer getKnownTotalLines() {
      return editorIO.getKnownTotalLines(SodiumEditor.this);
    }

    @Override
    public void countTotalLines(com.yn.sodiumeditor.core.CursorNavigation.GoToLineCallback.LineCountCallback callback) {
      editorIO.lineIndex.countTotalLines(total -> callback.onResult(total));
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
    return com.yn.sodiumeditor.core.IndentGuideEngine.getLineLeadingWhitespace(ln);
  }

  public int getIndentWidth(String line) {
    return com.yn.sodiumeditor.core.IndentGuideEngine.getIndentWidth(line);
  }







  public int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  public void setSelectionRange(int sLine, int sChar, int eLine, int eChar) {
    setSelectionInternal(sLine, sChar, eLine, eChar);
    invalidate();
  }

  public String getSelectedText() {
    return selectionHandler.getSelectedText();
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
    selectionHandler.pasteFromClipboard();
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
    com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target = editorTextInserter.computeCursorAfterInsert(baseLine, baseChar, insertText);
    return new CursorTarget(target.line, target.ch);
  }

  public int countNewlines(@Nullable String text) {
    return editorTextInserter.countNewlines(text);
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
    focusChangeHandler.onFocusChanged(focused);
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
    return editorLoadingState.copyCutMaxChars;
  }

  public long getCopyCutMaxLines() {
    return editorLoadingState.copyCutMaxLines;
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
      return editorLoadingState.copyCutMaxChars;
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

    @Override
    public void invalidatePendingIOForEdit() {
      SodiumEditor.this.invalidatePendingIOForEdit();
    }

    @Override
    public void insertTextAtCursor(String text) {
      editorTextInserter.insertTextAtCursor(text);
    }

    @Override
    public void inlinePredictionUpdate() {
      inlinePredictionEngine.updateSuggestion();
    }

    @Override
    public int getHideCopyCutMaxLines() {
      return editorLoadingState.hideCopyCutMaxLines;
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
      return editorLoadingState.copyCutMaxChars;
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
      return viewRender.textRender.getLineLength(line);
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
      return com.yn.sodiumeditor.core.HighlightParser.isLineCommentStart(line, index, highlightState.lineCommentDelimiters);
    }
  }
}
