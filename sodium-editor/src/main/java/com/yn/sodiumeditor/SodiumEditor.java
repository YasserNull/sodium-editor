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
import com.yn.sodiumeditor.core.CursorNavigation;
import com.yn.sodiumeditor.core.EditOp;
import com.yn.sodiumeditor.input.InputManager;
import com.yn.sodiumeditor.input.InputMethodHandler;
import com.yn.sodiumeditor.io.Document;
import com.yn.sodiumeditor.io.LineIndex;
import com.yn.sodiumeditor.io.TextIO;
import com.yn.sodiumeditor.renderer.ViewRender;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.state.FoldRange;
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
  @Nullable public ValueAnimator flingStopAnimator;
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
          viewRender.checkAndLoadWindow();
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
    highlightParser = new com.yn.sodiumeditor.core.HighlightParser(new com.yn.sodiumeditor.core.HighlightParser.HighlightParserCallback() {
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
    });
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
    selectionTextBuilder = new com.yn.sodiumeditor.core.SelectionTextBuilder(new com.yn.sodiumeditor.core.SelectionTextBuilder.SelectionCallback() {
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
    });
    selectionHandler = new com.yn.sodiumeditor.input.SelectionHandler(selectionConfig, selectionState, selectionTextBuilder, new com.yn.sodiumeditor.input.SelectionHandler.SelectionInteractionCallback() {
      @Override
      public int comparePos(int sL, int sC, int eL, int eC) {
        return SodiumEditor.this.comparePos(sL, sC, eL, eC);
      }

      @Override
      public void invalidate() {
        SodiumEditor.this.invalidate();
      }

      @Override
      public void invalidatePendingIOForEdit() {
        editorIO.invalidatePendingIOForEdit();
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
        return editorIndexState.isIndexBuilding();
      }

      @Override
      public boolean isIndexDisabled() {
        return editorIndexState.isIndexDisabled();
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

      @Override
      public void loadWindowAround(int targetStart, Runnable onComplete) {
        viewRender.loadWindowAround(targetStart, onComplete);
      }

      @Override
      public void buildFileIndex() {
        editorIO.document.buildFileIndex();
      }
    });
    selectionRenderer = new com.yn.sodiumeditor.renderer.SelectionRenderer(selectionConfig);

    // Initialize Cursor components
    cursorNavigation = new com.yn.sodiumeditor.core.CursorNavigation(cursorState, new com.yn.sodiumeditor.core.CursorNavigation.NavigationCallback() {
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
    });
    cursorRenderer = new com.yn.sodiumeditor.renderer.CursorRenderer(cursorConfig, cursorState, new com.yn.sodiumeditor.renderer.CursorRenderer.RenderCallback() {
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
    });
    imeCompositionHandler = new com.yn.sodiumeditor.input.ImeCompositionHandler(cursorState, new com.yn.sodiumeditor.input.ImeCompositionHandler.CompositionCallback() {
      @Override
      public boolean isReadOnly() {
        return editorConfig.behaviorConfig.isReadOnly;
      }

      @Override
      public void invalidatePendingIOForEdit() {
        editorIO.invalidatePendingIOForEdit();
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
        return viewRender.textRender.getLineFromWindowLocal(local);
      }

      @Override
      public void updateLocalLine(int local, String newLine) {
        viewRender.textRender.updateLocalLine(local, newLine);
      }

      @Override
      public java.util.HashMap<Integer, String> getModifiedLines() {
        return editorState.modifiedLines;
      }

      @Override
      public void computeWidthForLine(int line, String lineText) {
        viewRender.textRender.computeWidthForLine(line, lineText);
      }

      @Override
      public void recalculateMaxLineWidth() {
        viewRender.textRender.recalculateMaxLineWidth();
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
        history.clearComposingPendingOp();
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
    });
    editorTextInserter = new com.yn.sodiumeditor.core.EditorTextInserter(cursorState, new com.yn.sodiumeditor.core.EditorTextInserter.InsertionCallback() {
      @Override
      public boolean isReadOnly() {
        return editorConfig.behaviorConfig.isReadOnly;
      }

      @Override
      public void invalidatePendingIOForEdit() {
        editorIO.invalidatePendingIOForEdit();
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
        com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target = SodiumEditor.this.computeCursorAfterInsert(line, ch, text);
        return new com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget(target.line, target.ch);
      }

      @Override
      public void rewriteReplaceRangeAsync(int opToken, java.io.File inFile, int sL, int sC, int eL, int eC, String text, com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target, boolean finishLargeEdit) {
        rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, text, new com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget(target.line, target.ch), finishLargeEdit);
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
        history.recordEdit(op);
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
        return viewRender.textRender.getLineFromWindowLocal(local);
      }

      @Override
      public void updateLocalLine(int local, String newLine) {
        viewRender.textRender.updateLocalLine(local, newLine);
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
        return viewRender.textRender.getLinesCount();
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
        viewRender.textRender.recalculateMaxLineWidth();
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
    });

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
    searchEngine = new com.yn.sodiumeditor.core.SearchEngine(searchConfig, new com.yn.sodiumeditor.core.SearchEngine.SearchCallback() {
      @Override
      public int getEditVersionForSearch() {
        return getEditVersionForSearch();
      }

      @Override
      public int getLinesCount() {
        return viewRender.textRender.getLinesCount();
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
    });
    searchHandler = new com.yn.sodiumeditor.input.SearchHandler(searchConfig, searchEngine, new com.yn.sodiumeditor.input.SearchHandler.SearchInteractionCallback() {
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
    });
    searchRenderer = new com.yn.sodiumeditor.renderer.SearchRenderer(searchConfig, searchEngine, new com.yn.sodiumeditor.renderer.SearchRenderer.RenderCallback() {
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
    });

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

  public void restartInput() {
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

  public void clearSelectionStateAfterDelete() {
    selectionHandler.clearSelectionStateAfterDelete();
  }

  public void recordReplaceSelectionEdit(int sL, int sC, int eL, int eC, String removedText, String insertText, int beforeLine, int beforeChar) {
    history.recordReplaceSelectionEdit(
        sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }

  public void rewriteReplaceRangeAsync(int opToken, File inFile, int sL, int sC, int eL, int eC, String insertText, com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target, boolean finishLargeEditUi) {
    editorIO.textIO.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, finishLargeEditUi);
  }

  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    selectionHandler.setSelectionInternal(sL, sC, eL, eC);
  }

  public static boolean isLargePasteText(String text) {
    return com.yn.sodiumeditor.input.EditorOperations.isLargePasteText(text);
  }

  public void goToLine(int line, int col) {
    cursorNavigation.goToLine(line, col, new CursorNavigation.GoToLineWithUICallback() {
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
    });
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

  public void cutSelectionToClipboard() {
    selectionHandler.copyOrCutSelection(true);
  }

  public void pasteFromClipboard() {
    selectionHandler.pasteFromClipboard();
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

  public boolean isIndexBuilding() {
    return editorIndexState.isIndexBuilding;
  }

  public boolean isIndexDisabled() {
    return editorIndexState.isIndexDisabled;
  }

  public void selectAll() {
    selectionHandler.selectAll();
  }

  // ==============================
  // DELETE/REPLACE SELECTION (FIXED)
  // ==============================
  public void deleteSelection() {
    inlinePredictionState.clearActiveSuggestion(); // Clear suggestion when deleting selection
    inputManager.replaceSelectionWithText("");
  }

  // Undo/redo helpers moved to History.

  public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
    com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target = editorTextInserter.computeCursorAfterInsert(baseLine, baseChar, insertText);
    return new com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget(target.line, target.ch);
  }

  public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget computeCursorAfterInsertForUndo(int baseLine, int baseChar, String insertText) {
    return computeCursorAfterInsert(baseLine, baseChar, insertText);
  }

  public int countNewlinesForUndo(@Nullable String text) {
    return editorTextInserter.countNewlines(text);
  }

  // rewriteReplaceRangeBlocking moved to FileBufferModifier.

  private void recordEditNoUndo(EditOp op) {
    history.recordEditNoUndo(op);
  }

  private LineIndex.RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    return editorIO.lineIndex.computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  LineIndex.RangeBytes computeByteRangeFastOrScanForUndo(File file, int sL, int sC, int eL, int eC) {
    return computeByteRangeFastOrScan(file, sL, sC, eL, eC);
  }

  private long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    return editorIO.lineIndex.findLineStartByteByScanning(raf, targetLine);
  }

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return editorIO.textIO.computeByteOffsetInLineUtf8(lineText, charIndex);
  }

  private boolean isWordChar(char c) {
    return viewRender.textRender.isWordChar(c);
  }

  private int[] computeWordBoundsSmart(String line, int pos) {
    return viewRender.textRender.computeWordBoundsSmart(line, pos);
  }

  private boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    return viewRender.textRender.applySmartDoubleTapSelection(line, charIndex, lineText);
  }

  private boolean isPositionInsideSelection(int line, int ch) {
    return selectionHandler.isPositionInsideSelection(line, ch);
  }

  private void addSelectionCandidate(java.util.List<com.yn.sodiumeditor.utils.TextRange> out, int start, int end, int lineLen) {
    viewRender.textRender.addSelectionCandidate(out, start, end, lineLen);
  }

  private int findSelectionCandidateIndex(int line, java.util.List<com.yn.sodiumeditor.utils.TextRange> candidates) {
    return viewRender.textRender.findSelectionCandidateIndex(line, candidates);
  }

  private java.util.ArrayList<com.yn.sodiumeditor.utils.TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
    return viewRender.textRender.buildDoubleTapCandidates(line, charIndex, wStart, wEnd);
  }

  private boolean isQuoteChar(char c) {
    return viewRender.textRender.isQuoteChar(c);
  }

  @Nullable
  private com.yn.sodiumeditor.utils.TextRange findEnclosingQuoteRange(String line, int index) {
    return viewRender.textRender.findEnclosingQuoteRange(line, index);
  }

  @Nullable
  private com.yn.sodiumeditor.utils.TextRange findEnclosingBracketRange(String line, int index) {
    return viewRender.textRender.findEnclosingBracketRange(line, index);
  }

  public void insertTextAtCursor(String text) {
    cursorState.setCursorPosition(cursorState.getCursorLine(), cursorState.getCursorChar());
    editorTextInserter.insertTextAtCursor(text);
  }

  BufferedReader reopenReaderAtStart() {
    return editorIO.document.reopenReaderAtStart();
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

  private void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    editorIO.textIO.setStreamedLineInfo(globalLine, length, sliceStart);
  }

  private boolean isSingleByteCharset() {
    return editorIO.document.isSingleByteCharset();
  }

  private float getWidthForLine(int globalIndex, String line) {
    return viewRender.textRender.getWidthForLine(globalIndex, line);
  }

  // --- Essential methods (not aliases) ---
  public float getTextStartX() {
    return lineNumberRenderer.getTextStartX(editorConfig.paddingLeft, isRtl);
  }

  public float getEffectiveScrollX() {
    return isRtl ? -scrollManager.scrollX : scrollManager.scrollX;
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

  public com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    return viewRender.textRender.getCursorTargetForPosition(viewX, viewY, directLines);
  }

  public void updateWhitespaceGuideMetrics() {
    whitespaceGuideRenderer.updateMetrics(editorConfig.paint, editorConfig.visualConfig.WHITESPACE_GUIDE_SPACE, editorConfig.visualConfig.WHITESPACE_GUIDE_TAB);
  }

  public void invalidateHighlightEnsureRange() {
    highlightState.resetEnsureRange();
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

  public void applyTypeface(@Nullable android.graphics.Typeface typeface, int style) {
    typefaceManager.applyTypeface(typeface, style);
  }

  public int getVisualSpaceScale() {
    return 1;
  }

  public int getInitialStreamedSliceSize() {
    return 2048;
  }

  public boolean isMixedDirectionText(String line, int start, int end) {
    return false;
  }

  public float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - getTextStartX();
  }

  // --- More essential methods ---
  public float getIndentGuideTextSizePx() {
    return editorConfig.paint.getTextSize();
  }

  public boolean isIndentationBlocksEnabledForIndentGuides() {
    return editorConfig.behaviorConfig.isIndentationBlocksEnabled;
  }

  public boolean isHeavyDrawSuppressedForIndentGuides() {
    return viewRender.textRender.isHeavyDrawSuppressed();
  }

  public String getIndentGuideUnit() {
    return editorConfig.visualConfig.INDENT_BLOCK_UNIT;
  }

  public float getIndentGuideLineHeight() {
    return editorConfig.lineHeight;
  }

  public int getIndentGuideTabSize() {
    return 4;
  }

  public boolean isWhitespaceAtXForIndentGuides(String line, int globalLine, float x) {
    return false;
  }

  public boolean hasIndentGuideFoldRanges() {
    return false;
  }

  // --- Essential delegate methods ---
  public int getLinesCount() {
    return viewRender.textRender.getLinesCount();
  }

  public String getLineTextForRender(int line) {
    return viewRender.textRender.getLineTextForRender(line);
  }

  @Nullable
  public String getLineTextForRenderWithDirect(int line, @Nullable java.util.Map<Integer, String> direct) {
    return viewRender.textRender.getLineTextForRenderWithDirect(line, direct);
  }

  public int getWindowEndLine() {
    synchronized (editorState.linesWindow) {
      return Math.max(0, editorState.windowStartLine + editorState.linesWindow.size() - 1);
    }
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    return foldState.mapVisibleIndexToGlobal(visibleIndex, getLinesCount());
  }

  public int getGoToLineVersion() {
    return editorIndexState.getGoToLineVersion();
  }

  public void superOnDraw(Canvas canvas) {
    super.onDraw(canvas);
  }

  public boolean superOnTouchEvent(MotionEvent event) {
    return super.onTouchEvent(event);
  }

  public boolean superOnKeyDown(int keyCode, KeyEvent event) {
    return super.onKeyDown(keyCode, event);
  }

  public void handleAutoPairing(String text) {
    inputManager.handleAutoPairing(text);
  }

  public final com.yn.sodiumeditor.input.EditorOperations editorOps = new com.yn.sodiumeditor.input.EditorOperations(this);
}
