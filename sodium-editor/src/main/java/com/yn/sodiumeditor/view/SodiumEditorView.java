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
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface; // Added for Typeface
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
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

public class SodiumEditorView extends View {

  public static final int STYLE_NORMAL = 0;
  public static final int STYLE_BOLD = 1;
  public static final int STYLE_ITALIC = 2;
  public static final int STYLE_BOLD_ITALIC = 3;

  // paint & metrics
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Typeface baseTypeface = Typeface.DEFAULT;
  float lineHeight;
  private float paddingLeft = 10f; // Made non-final for line numbers

  // --- Line Number State ---
  private boolean showLineNumbers = false;
  private boolean highlightCurrentLineInGutter = true;
  boolean isRtl = false;
  private final Rect textBounds = new Rect();
  private final int[] tmpLocationInWindow = new int[2];
  private static final float GUTTER_TEXT_PADDING = 20f;

  // visual padding constants
  static final float BOTTOM_SCROLL_OFFSET = 100f; // Visual padding below last line
  private static final float MIN_BOTTOM_VISIBLE_SPACE =
      50f; // Minimum space to show below last line

  // sliding window
  final List<String> linesWindow = new ArrayList<>();
  int windowStartLine = 0;
  private int windowSize = 30; // 2000 yyy
  int prefetchLines = 10; // 1000 yyy

  // IO
  private final HandlerThread ioThread;
  private final Handler ioHandler;
  private BufferedReader readerForFile = null;
  File sourceFile = null;
  boolean isEof = false;
  private final AtomicInteger ioTaskVersion = new AtomicInteger(0);
  private boolean isFileCleared = false; // Track if the file content has been cleared

  // caches
  final LinkedHashMap<Integer, String> modifiedLines = new LinkedHashMap<>();
  private final LinkedHashMap<Integer, Float> lineWidthCache;
  private int lineWidthCacheSize = 200; // 2000 yyy
  private float currentMaxWindowLineWidth = 0f;
  private float globalMaxLineWidth = 0f;
  private int maxSyntaxLineLength = 4096;
  private int prefetchCols = 512;
  private int colsWidthCacheSize = 256;
  private final LinkedHashMap<Integer, Float> avgCharWidthCache =
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
  private Charset fileCharset = StandardCharsets.UTF_8;

  // --- Cursor Animation State (moved to CursorAnimationManager) ---
  private final InputManager inputManager;
  @Nullable ValueAnimator flingStopAnimator;
  static final long FLING_STOP_ANIM_DURATION_MS = 90;
  private final IMEManager imeManager;
  private final ScrollManager scrollManager;
  private final ZoomManager zoomManager;
  private final UndoRedo undoRedo;
  private final SearchManager searchManager;
  private final CursorAnimationManager cursorAnimationManager;
  private final CharAnimationManager charAnimationManager;
  private final PopupMenuManager popupMenuManager;

  // --- Search State (moved to SearchManager) ---
  // --- Zoom State (moved to ZoomManager) ---
  private final WordWrapManager wordWrapManager = new WordWrapManager();
  private boolean pendingApplyWrapPrefixUpdate = false;
  boolean isZoomGestureActive() {
    return zoomManager.isZoomGestureActive();
  }

  ZoomManager getZoomManager() {
    return zoomManager;
  }

  private void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
    if (!showLineNumbers || !highlightCurrentLineInGutter || lineNumberManager.lineNumbersGutterWidth <= 0f) return;
    float left = getGutterStartX();
    float right = left + lineNumberManager.lineNumbersGutterWidth;
    float sep = lineNumberManager.gutterSeparatorWidth;
    if (sep > 0f) {
      if (isRtl) {
        left = Math.min(right, left + sep);
      } else {
        right = Math.max(left, right - sep);
      }
    }
    if (right <= left) return;
    canvas.drawRect(left, top, right, bottom, currentLinePaint);
  }

  // Search logic moved to SearchManager.

  private void applyPendingWrapPrefixUpdateIfAny() {
    if (!pendingApplyWrapPrefixUpdate) return;
    if (!isWordWrapEnabled) {
      pendingApplyWrapPrefixUpdate = false;
      wordWrapManager.pendingWrapPrefixCounts = null;
      wordWrapManager.pendingWrapPrefixPrefix = null;
      return;
    }
    if (isZoomGestureActive()) return;
    if (wordWrapManager.pendingWrapPrefixCounts == null || wordWrapManager.pendingWrapPrefixPrefix == null) {
      pendingApplyWrapPrefixUpdate = false;
      return;
    }
    // Only apply if the wrap width still matches; otherwise a new rebuild will be scheduled.
    int currentWidthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wordWrapManager.pendingWrapPrefixWidthPx != currentWidthPx) {
      pendingApplyWrapPrefixUpdate = false;
      wordWrapManager.pendingWrapPrefixCounts = null;
      wordWrapManager.pendingWrapPrefixPrefix = null;
      return;
    }

    // Keep the current top visual line anchored while swapping in the new prefix arrays.
    int anchorFirstVisual = Math.max(0, (int) (scrollManager.scrollY / lineHeight));
    VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    wordWrapManager.wrapLineCounts = wordWrapManager.pendingWrapPrefixCounts;
    wordWrapManager.wrapLinePrefix = wordWrapManager.pendingWrapPrefixPrefix;
    wordWrapManager.totalWrapVisualLines = wordWrapManager.pendingWrapPrefixTotalVisualLines;
    wordWrapManager.wrapMetricsWidth = wordWrapManager.pendingWrapPrefixWidthPx;
    wordWrapManager.wrapMetricsReady = true;
    wordWrapManager.wrapPrefixValidUpToLine = Math.max(wordWrapManager.wrapPrefixValidUpToLine, wordWrapManager.pendingWrapPrefixValidUpToLine);

    pendingApplyWrapPrefixUpdate = false;
    wordWrapManager.pendingWrapPrefixCounts = null;
    wordWrapManager.pendingWrapPrefixPrefix = null;

    if (anchorLine >= 0 && wordWrapManager.wrapLinePrefix != null && anchorLine < wordWrapManager.wrapLinePrefix.length) {
      int newAnchorFirstVisual = wordWrapManager.wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        scrollManager.scrollY += dv * lineHeight;
        clampScrollY();
      }
    }
  }

  void applyPendingWrapPrefixUpdateForZoom() {
    applyPendingWrapPrefixUpdateIfAny();
  }

  // caret
      // composing
  boolean hasComposing = false;
  int composingLine = 0, composingOffset = 0, composingLength = 0;
  int composingStartLine = -1;
  int composingStartChar = 0;
  boolean composingStartActive = false;
  // composing pending op moved to UndoRedo.

  // selection
  private boolean pendingPopupAfterDoubleTap = false;
  private int lastDoubleTapLine = -1;
  private int lastDoubleTapWordStart = -1;
  private int lastDoubleTapWordEnd = -1;
  private int lastDoubleTapStage = 0;

  // touch helpers
  boolean pointerDown = false;
  boolean movedSinceDown = false;
  private float downX = 0f, downY = 0f;
  private final int touchSlop;
  // Zoom multi-touch state moved to ZoomManager.

  // auto-scroll when dragging handles
  final Handler mainHandler = new Handler(Looper.getMainLooper());
  private float lastTouchX = 0f, lastTouchY = 0f;

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
  private float baseHandleTextSizePx = 0f;
  private float baseCursorTextSizePx = 0f;
  private int selectionHighlightColor = 0x8033B5E5;
  private boolean isIndentGuidesEnabled = false;
  private final Paint indentGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float indentGuideStrokeWidth = 2f;
  private float baseIndentGuideStrokeWidth = indentGuideStrokeWidth;
  private float baseIndentGuideTextSizePx = 0f;
  private final java.util.ArrayList<int[]> indentGuideIntervals = new java.util.ArrayList<>();
  private boolean indentGuideIntervalsDirty = true;
  private boolean isWhitespaceGuidesEnabled = false;
  private final Paint whitespaceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint whitespaceGuideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float whitespaceGuideSpaceWidth = 0f;
  private float whitespaceGuideTabWidth = 0f;
  private float[] whitespaceWidthBuffer;
  private float[] whitespaceDotBuffer;
  private float[] measureWidthBuffer;
  private int whitespaceGuideSpaceStep = 1;
  private static final int DEFAULT_TAB_SIZE_SPACES = 4;

  private static final class WhitespaceDrawState {
    int syntaxIndex;
  }

  private final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();
  private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final HandlesManager handlesManager = new HandlesManager();
  private final CursorManager cursorManager = new CursorManager();
  private final SelectionManager selectionManager = new SelectionManager();
  private final LineNumberManager lineNumberManager = new LineNumberManager(this);
  private final BracketGuideManager bracketGuideManager = new BracketGuideManager(this);
  private final BracketMatchManager bracketMatchManager = new BracketMatchManager(this);
  private final Paint loadingCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF loadingCircleRect = new RectF();
  private final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();
  private final Path teardropPath = new Path();
  private final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path popupRippleClipPath = new Path();
  private final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float foldMarkerGutterWidth = 0f;
  private float foldMarkerTextScale = 1f;
  private float foldMarkerSpacing = 0f;
  private float foldMarkerEdgePadding = 4f;
  private ValueAnimator foldRippleAnimator;
  private int foldRippleLine = -1;
  private float foldRippleRadius = 0f;
  private float foldRippleAlpha = 0f;
  private float foldRippleMaxRadius = 0f;

  // editor background
  private boolean hasEditorBackgroundColor = false;
  private int editorBackgroundColor = 0x00000000;
  @Nullable private Bitmap editorBackgroundBitmap = null;
  private final Rect editorBackgroundDst = new Rect();

  // selection drawing (rounded)
  private final RectF selectionRectTmp = new RectF();
  private final Path selectionPathTmp = new Path();
  private final float[] selectionRadiiTmp = new float[8];
  private final RectF foldPlaceholderRect = new RectF();

  // handle dragging edge flags (to prevent horizontal autoscroll beyond line bounds)
  private boolean lastDragAtLineStart = false;
  private boolean lastDragAtLineEnd = false;

  // Drawing base to avoid float precision issues on very large line indices.
  // During onDraw, we render everything relative to the first visible line.
  int drawBaseLine = 0;

  private final LinkedHashMap<Integer, int[]> colorCodeBgCache =
      new LinkedHashMap<Integer, int[]>(600, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
          return size() > 600;
        }
      };
  private HighlightRule whitespaceStringRule;
  private HighlightRule whitespaceCommentRule;
  private static final String WHITESPACE_GUIDE_SPACE = "\u00B7";
  private static final String WHITESPACE_GUIDE_TAB = "\u2192";
  private static final String FOLD_PLACEHOLDER_TEXT = "<—>";
  private static final String INDENT_BLOCK_UNIT = "  ";
  private static final int INDENT_FOLD_SCAN_LIMIT = 2000;
  private float foldPlaceholderCorner = 3f;
  private float foldPlaceholderPadX = 3f;
  private float foldPlaceholderPadY = 2f;
  private final java.util.HashMap<Integer, FoldRange> foldRanges = new java.util.HashMap<>();
  private final java.util.ArrayList<int[]> foldIntervals = new java.util.ArrayList<>();
  private boolean foldIntervalsDirty = true;

  // --- Current Line Highlight State ---
  private boolean highlightCurrentLine = true;
  private boolean isClickAfterEndToAddLineEnabled = false;
  private boolean isAutoPairingEnabled = false;
  private boolean isAutoBracketNewlineEnabled = false;
  private boolean isAutoBracketNewlineIndentEnabled = false;
  private boolean isAutoIndentAfterClosingBracketEnabled = false;
  private boolean isIndentationBlocksEnabled = false;
  boolean isCodeFoldingEnabled = false;
  private int currentLineHighlightColor = 0x202196F3; // Default: translucent gray (more visible)
  private int currentLineNumberColor = 0xFF2196F3; // Default: same as cursor/handles color
  private final Paint currentLinePaint = new Paint();

  private int draggingHandle = 0;
  volatile boolean isWindowLoading = false;

  boolean isDisabled = false;
  boolean isReadOnly = false;
  private final AtomicInteger goToLineVersion = new AtomicInteger(0);

  // Loading circle variables
  private boolean showLoadingCircle = false;
  private float loadingCircleRadius = 40f;
  private int loadingCircleColor = 0xFF3F51B5;
  private float loadingCircleRotation = 0f;
  private ValueAnimator rotationAnimator;
  private boolean showLoadingOnFileOpen = true;
  private boolean isInitialFileOpenLoading = false;
  private int initialFileOpenToken = 0;
  @Nullable private Runnable initialFileOpenShowSpinner;
  private final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
  private int maxWidthRecalcToken = 0;

  // index
  final Object lineOffsetsLock = new Object();
  long[] lineOffsets = new long[0];
  volatile boolean isIndexReady = false;
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
  private final Runnable largeEditUiWatchdog =
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

  private int lastHighlightEnsureStartLine = -1;
  private int lastHighlightEnsureEndLine = -1;
  private int lastHighlightEnsureEditVersion = -1;


  // --- Syntax Highlighting State ---
  private final java.util.ArrayList<String> lineCommentDelimiters = new java.util.ArrayList<>();
  @Nullable private HighlightRule lineCommentHighlightRule;
  private final List<HighlightRule> highlightRules = new ArrayList<>();
  private HighlightRule stringHighlightRule;
  private HighlightRule blockCommentHighlightRule;
  private final ArrayList<HighlightRule> regexHighlightRules = new ArrayList<>();
  private final LinkedHashMap<Integer, List<HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  private final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  private final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  // --- URL underline (decoration, not syntax) ---
  private static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");
  private boolean isUrlUnderliningEnabled = false;
  @Nullable private Pattern urlUnderlinePattern = DEFAULT_URL_UNDERLINE_PATTERN;
  private final Paint urlUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final LinkedHashMap<Integer, List<UnderlineSpan>> urlUnderlineCache =
      new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };

  // --- Path underline (decoration, not syntax) ---
  private boolean isPathUnderliningEnabled = false;
  @Nullable private Pattern pathUnderlinePattern = Pattern.compile("/[^\\s,;()'\"]+");
  private final Paint pathUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final LinkedHashMap<Integer, List<UnderlineSpan>> pathUnderlineCache =
      new LinkedHashMap<Integer, List<UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };
  // Using ConcurrentHashMap for thread-safe access from IO and UI threads.
  private final java.util.concurrent.ConcurrentHashMap<String, Boolean> pathValidationCache =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Set<String> pendingPathValidations =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  // --- Error underline (squiggle) ---
  private int errorUnderlineColor = 0xFFE53935;
  private boolean errorUnderlineEnabled = true;
  private final Paint errorUnderlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path errorUnderlinePath = new Path();
  private float errorUnderlineHeightScale = 0.18f;
  private float errorUnderlineWaveLengthScale = 0.70f;
  private float errorUnderlineStrokeScale = 0.08f;
  private float errorUnderlineSmoothness = 3f;
  private final LinkedHashMap<Integer, List<ErrorUnderlineSpan>> errorUnderlineMap =
      new LinkedHashMap<Integer, List<ErrorUnderlineSpan>>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<ErrorUnderlineSpan>> eldest) {
          return size() > 2000;
        }
      };
  private final int[] visibleCharRangeTmp = new int[2];
  private int visibleCharPadding = 2;
  private boolean isPerformanceModeEnabled = false;
  private boolean isStableGlyphPositionsEnabled = false;

  // Zoom scroll adjustment for word wrap

  public static final String RULE_STRING = "__STRING__";
  public static final String RULE_BLOCK_COMMENT = "__BLOCK_COMMENT__";
  public static final String RULE_LINE_COMMENT = "__LINE_COMMENT__";

  // --- Auto-completion State ---
  private boolean isAutoCompletionEnabled = false;
  private boolean isAutoPathCompletionEnabled = false;
  private final Paint suggestionPaint = new Paint();
  private String activeSuggestion = null;
  private int activeSuggestionLine;
  private int activeSuggestionCharStart; // character index where the word fragment starts
  private String activeSuggestionWordFragment = ""; // the part user typed
  private boolean activeSuggestionIsPath = false;
  private final Trie suggestionTrie = new Trie();
  private final RectF activeSuggestionRect = new RectF(); // For tap-to-accept
  private boolean isSuggestionTextSizeCustom =
      false; // Flag to track if suggestion text size is custom
  private float suggestionTextSizeScale = 1f;

  // Debounce suggestion updates to avoid expensive per-keystroke parsing.
  private static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;
  private long lastSuggestionUpdateUptime = 0L;
  private boolean suggestionUpdateScheduled = false;
  private final Runnable suggestionUpdateRunnable =
      () -> {
        suggestionUpdateScheduled = false;
        lastSuggestionUpdateUptime = SystemClock.uptimeMillis();
        updateSuggestionInternal();
      };
  boolean suggestionAcceptedThisTouch =
      false; // Flag to prevent GestureDetector interference
  private String lastPathQuery = null;
  private String lastPathSuggestion = null;
  boolean isWordWrapEnabled = false;
  private static final String WORD_WRAP_INDICATOR_TEXT = "\u21A9"; // ↩
  private boolean binarySafeRenderingEnabled = false;
  private static class TrieNode {
    final Map<Character, TrieNode> children = new java.util.TreeMap<>();
    String word = null;
  }

  private static class Trie {
    private final TrieNode root = new TrieNode();

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

    private String findFirstWordFromNode(TrieNode node) {
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

  static class HighlightSpan {
    final int start;
    final int end;
    final Paint paint;

    HighlightSpan(int start, int end, Paint paint) {
      this.start = start;
      this.end = end;
      this.paint = paint;
    }
  }

  private static class UnderlineSpan {
    final int start;
    final int end;
    final boolean isPath; // true if it's a path, false if URL

    UnderlineSpan(int start, int end, boolean isPath) {
      this.start = start;
      this.end = end;
      this.isPath = isPath;
    }
  }

  private static class ErrorUnderlineSpan {
    final int start;
    final int end;

    ErrorUnderlineSpan(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  private static class LineParseResult {
    final List<HighlightSpan> spans;
    final boolean endsInBlockComment;
    final int endsInStringState;

    LineParseResult(List<HighlightSpan> spans, boolean endsInBlockComment, int endsInStringState) {
      this.spans = spans;
      this.endsInBlockComment = endsInBlockComment;
      this.endsInStringState = endsInStringState;
    }
  }

  static class HighlightLineState {
    final boolean inBlockComment;
    final int stringState;

    HighlightLineState(boolean inBlockComment, int stringState) {
      this.inBlockComment = inBlockComment;
      this.stringState = stringState;
    }
  }

  private static final class FoldRange {
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

  private enum HighlightRuleType {
    REGEX,
    STRING,
    BLOCK_COMMENT,
    LINE_COMMENT
  }

  private static class HighlightRule {
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
  private boolean isColorHighlightingEnabled = false;
  private boolean isMultiLineStringsEnabled = false;
  private boolean isBacktickStringsEnabled = false;
  private boolean isBlockCommentsEnabled = false;
  private boolean isTripleQuoteStringsEnabled = false;
  private final Paint colorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final Pattern COLOR_HEX_PATTERN =
      Pattern.compile(
          "(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))\\b|(\\b0x[a-fA-F0-9]{6,8}\\b)",
          Pattern.CASE_INSENSITIVE);

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
    baseHandleTextSizePx = paint.getTextSize();
    baseCursorTextSizePx = paint.getTextSize();
    baseIndentGuideTextSizePx = paint.getTextSize();
    bracketMatchManager.setColor(handlesManager.getCursorAndHandlesColor());
    bracketMatchManager.setBaseTextSizePx(paint.getTextSize());
    bracketGuideManager.setBaseTextSizePx(paint.getTextSize());
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

    // Initialization for line numbers
    lineNumberManager.lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
    lineNumberManager.lineNumbersPaint.setColor(0xFF888888); // Default gray color
    lineNumberManager.lineNumbersPaint.setTextSize(paint.getTextSize());
    lineNumberManager.lineNumbersPaint.setTypeface(paint.getTypeface());
    lineNumberManager.gutterPaint.setColor(0xFFFAFAFA); // Default light gray background

    float density = getContext().getResources().getDisplayMetrics().density;
    lineNumberManager.gutterSeparatorWidth = 4 * density;
    lineNumberManager.gutterSeparatorPaint.setColor(0xFF555555);
    currentLinePaint.setColor(currentLineHighlightColor);
    foldPlaceholderCorner = 6f * density;
    foldPlaceholderPadX = 6f * density;
    foldPlaceholderPadY = 2f * density;
    foldMarkerSpacing = foldMarkerSpacing * density;
    foldMarkerEdgePadding = foldMarkerEdgePadding * density;

    popupMenuManager = new PopupMenuManager(this);

    foldPlaceholderPaint.setColor(0xFFE0E0E0);
    foldPlaceholderPaint.setStyle(Paint.Style.FILL);
    foldMarkerPaint.setColor(0xFF888888);
    foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    foldMarkerPaint.setTextSize(paint.getTextSize());
    foldRipplePaint.setStyle(Paint.Style.FILL);

    wordWrapManager.initIndicatorPaint(paint, density, WORD_WRAP_INDICATOR_TEXT);

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

  public ScrollManager getScrollManager() {
    return scrollManager;
  }

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
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
    invalidate();
  }

  public void setHighlightCurrentSearchMatchEnabled(boolean enabled) {
    searchManager.setHighlightCurrentSearchMatchEnabled(enabled);
  }

  public void setCurrentSearchMatchColor(int color) {
    searchManager.setCurrentSearchMatchColor(color);
  }

  public void setWordWrapEnabled(boolean enabled) {
    if (this.isWordWrapEnabled == enabled) return;
    this.isWordWrapEnabled = enabled;
    invalidateWrapMetrics();
    if (enabled) {
      scrollManager.scrollX = 0f;
      clampScrollX();
      clearStreamedLineCaches();
      reloadWindowAroundVisible(false);
    }
    requestLayout();
    invalidate();
  }

  public void setWordWrapIndicatorEnabled(boolean enabled) {
    if (this.wordWrapManager.isWordWrapIndicatorEnabled == enabled) return;
    this.wordWrapManager.isWordWrapIndicatorEnabled = enabled;
    invalidate();
  }

  public void setWordWrapIndicatorColor(int color) {
    wordWrapManager.setIndicatorColor(color);
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
      setCharAnimation(false, 0);
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
    wordWrapManager.updateIndicatorTextSize(base, px, WORD_WRAP_INDICATOR_TEXT);
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
    Log.d("SodiumEditorView", "acceptAutoCompletion: Entered.");
    if (activeSuggestion == null) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (disabled or no active suggestion).");
      return;
    }
    if (activeSuggestionIsPath && !isAutoPathCompletionEnabled) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (path disabled).");
      return;
    }
    if (!activeSuggestionIsPath && !isAutoCompletionEnabled) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (word disabled).");
      return;
    }

    commitComposing(false);

    // Set a flag to ignore subsequent gesture events from this touch sequence.
    suggestionAcceptedThisTouch = true;

    String textToInsert = activeSuggestion;
    clearActiveSuggestion();
    selectionManager.hasSelection = false; // Clear selection after accepting suggestion
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    Log.d("SodiumEditorView", "acceptAutoCompletion: Cleared selection flags, inserting text.");
    insertStringAtCursor(textToInsert);
    Log.d("SodiumEditorView", "acceptAutoCompletion: Text inserted.");

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

  void clearActiveSuggestion() {
    if (activeSuggestion != null) {
      activeSuggestion = null;
      activeSuggestionRect.setEmpty();
      activeSuggestionIsPath = false;
      invalidate();
    }
  }

  void updateSuggestion() {
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

  private void updateSuggestionInternal() {
    String line = getLineTextForRender(cursorManager.cursorLine);
    if (line == null) {
      clearActiveSuggestion();
      return;
    }

    if (!isAutoPathCompletionEnabled && !isAutoCompletionEnabled) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if the cursor is in the middle of a word
    if (cursorManager.cursorChar < line.length() && Character.isLetterOrDigit(line.charAt(cursorManager.cursorChar))) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if there is non-whitespace text after the cursor
    if (cursorManager.cursorChar < line.length() && !line.substring(cursorManager.cursorChar).trim().isEmpty()) {
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
    List<HighlightSpan> spans = highlightCache.get(cursorManager.cursorLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, cursorManager.cursorLine);
      highlightCache.put(cursorManager.cursorLine, spans);
    }
    for (HighlightSpan span : spans) {
      if (cursorManager.cursorChar > span.start && cursorManager.cursorChar <= span.end) {
        clearActiveSuggestion();
        return;
      }
    }

    if (!pathFragment.isEmpty()) {
      String suggestion = findPathSuggestion(pathFragment);
      if (suggestion != null && suggestion.length() > pathFragment.length()) {
        activeSuggestion = suggestion.substring(pathFragment.length());
        activeSuggestionLine = cursorManager.cursorLine;
        activeSuggestionCharStart = cursorManager.cursorChar - pathFragment.length();
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
      activeSuggestionLine = cursorManager.cursorLine;
      activeSuggestionCharStart = cursorManager.cursorChar - wordFragment.length();
      activeSuggestionWordFragment = wordFragment;
      activeSuggestionIsPath = false;
    } else {
      clearActiveSuggestion();
    }
    invalidate();
  }

  private String getCurrentWordFragment() {
    String line = getLineTextForRender(cursorManager.cursorLine);
    if (cursorManager.cursorChar == 0 || cursorManager.cursorChar > line.length()) {
      return "";
    }
    int start = cursorManager.cursorChar;
    // A word character is a letter or a digit.
    while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
      start--;
    }
    return line.substring(start, cursorManager.cursorChar);
  }

  private String getCurrentPathFragment() {
    String line = getLineTextForRender(cursorManager.cursorLine);
    if (cursorManager.cursorChar == 0 || cursorManager.cursorChar > line.length()) {
      return "";
    }
    int start = cursorManager.cursorChar;
    while (start > 0 && isPathChar(line.charAt(start - 1))) {
      start--;
    }
    String fragment = line.substring(start, cursorManager.cursorChar);
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

  private boolean isPathChar(char c) {
    return Character.isLetterOrDigit(c) || c == '/' || c == '.' || c == '_' || c == '-' || c == '~';
  }

  @Nullable
  private String findPathSuggestion(String fragment) {
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
  private File resolveBaseDir(
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
  private File getDefaultBaseDir() {
    if (sourceFile != null) {
      File parent = sourceFile.getParentFile();
      if (parent != null) return parent;
    }
    String home = getHomeDir();
    if (home != null) return new File(home);
    return new File("/");
  }

  @Nullable
  private String getHomeDir() {
    String home = System.getenv("HOME");
    if (home == null || home.isEmpty()) {
      home = System.getProperty("user.home");
    }
    return (home == null || home.isEmpty()) ? null : home;
  }

  private String chooseClosestPrefix(List<String> matches) {
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
  private String chooseClosestByCommonPrefix(File[] entries, String prefix, boolean allowHidden) {
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

  private int commonPrefixLength(String a, String b) {
    int len = Math.min(a.length(), b.length());
    int i = 0;
    while (i < len && a.charAt(i) == b.charAt(i)) i++;
    return i;
  }

  private void insertStringAtCursor(String text) {
    if (isReadOnly) return;
    if (text == null || text.isEmpty()) return;
    if (selectionManager.hasSelection) {
      replaceSelectionWithText(text);
      return;
    }
    if (text.contains("\n")) { // Not handled for simplicity, suggestions shouldn't have newlines.
      for (char c : text.toCharArray()) insertCharAtCursor(c);
      return;
    }
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();

    scrollManager.ensureLineInWindow(cursorManager.cursorLine, true);
    if (isWindowLoading
        && (cursorManager.cursorLine < windowStartLine || cursorManager.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertStringAtCursor(text));
      return;
    }

    int localIdx = cursorManager.cursorLine - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(cursorManager.cursorChar, base.length()));
      String modified = base.substring(0, pos) + text + base.substring(pos);
      updateLocalLine(localIdx, modified);
      modifiedLines.put(cursorManager.cursorLine, modified);
      invalidateHighlightCacheForLine(cursorManager.cursorLine);
      cursorManager.cursorChar += text.length();
      computeWidthForLine(cursorManager.cursorLine, modified);
      recalculateMaxLineWidth();
      scrollManager.keepCursorVisibleHorizontally();
      invalidate();
    }
  }

  private void validatePathInBackground(final String path, final int lineToInvalidate) {
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

  // --- Public APIs for Line Numbers ---

  public void setShowLineNumbers(boolean show) {
    if (this.showLineNumbers == show) return;
    this.showLineNumbers = show;
    scrollManager.maxTextStartXForScroll = 0f;
    invalidateLineNumberCache();
    requestLayout(); // Recalculate gutter width
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    invalidate(); // Redraw
  }

  public void setLineNumberColor(int color) {
    lineNumberManager.lineNumbersPaint.setColor(color);
    lineNumberManager.lineNumberCacheColor = color;
    invalidateLineNumberCache();
    if (showLineNumbers) invalidate();
  }

  public void setCurrentLineGutterHighlightEnabled(boolean enabled) {
    if (highlightCurrentLineInGutter == enabled) return;
    highlightCurrentLineInGutter = enabled;
    invalidate();
  }

  public void setLineNumberSelectionEnabled(boolean enabled) {
    if (lineNumberManager.lineNumberSelectionEnabled == enabled) return;
    lineNumberManager.lineNumberSelectionEnabled = enabled;
    if (!enabled && selectionManager.isLineNumberSelecting) {
      selectionManager.isLineNumberSelecting = false;
      selectionManager.lineNumberSelectAnchorLine = -1;
    }
  }

  public void setGutterBackgroundColor(int color) {
    lineNumberManager.gutterPaint.setColor(color);
    if (showLineNumbers) invalidate();
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

  public void setSelectionHighlightColor(int color) {
    if (this.selectionHighlightColor == color) return;
    this.selectionHighlightColor = color;
    if (selectionManager.hasSelection) invalidate();
  }

  public void setSelectionColor(int color) {
    setSelectionHighlightColor(color);
  }

  public void setCursorAndHandlesColor(int color) {
    if (handlesManager.getCursorAndHandlesColor() == color) return;
    handlesManager.setCursorAndHandlesColor(color);
    handlesManager.setCaretColor(color);
    handlesManager.setCursorHandleColor(color);
    handlesManager.setSelectionHandleColor(color);
    invalidate();
  }

  public void setCaretColor(int color) {
    if (handlesManager.getCaretColor() == color) return;
    handlesManager.setCaretColor(color);
    invalidate();
  }

  public void setCursorHandleColor(int color) {
    if (handlesManager.getCursorHandleColor() == color) return;
    handlesManager.setCursorHandleColor(color);
    invalidate();
  }

  public void setSelectionHandleColor(int color) {
    if (handlesManager.getSelectionHandleColor() == color) return;
    handlesManager.setSelectionHandleColor(color);
    invalidate();
  }

  public void setSearchQuery(
      String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
    searchManager.setSearchQuery(query, useRegex, caseSensitive, wrapAround);
  }

  public void setSearchHighlightEnabled(boolean enabled) {
    searchManager.setSearchHighlightEnabled(enabled);
  }

  public void setSearchHighlightColor(int color) {
    searchManager.setSearchHighlightColor(color);
  }

  public boolean goToNextSearchMatch() {
    return searchManager.goToNextSearchMatch();
  }

  public boolean goToPrevSearchMatch() {
    return searchManager.goToPrevSearchMatch();
  }

  public boolean selectNextSearchMatch() {
    return searchManager.selectNextSearchMatch();
  }

  public boolean selectPrevSearchMatch() {
    return searchManager.selectPrevSearchMatch();
  }

  public boolean selectNextSearchMatchInclusive() {
    return searchManager.selectNextSearchMatchInclusive();
  }

  public boolean selectPrevSearchMatchInclusive() {
    return searchManager.selectPrevSearchMatchInclusive();
  }

  public boolean selectSearchMatchAtCursorOrNext() {
    return searchManager.selectSearchMatchAtCursorOrNext();
  }

  private void setCursorPositionNoClear(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    cursorManager.cursorLine = targetLine;
    if (cursorManager.cursorLine >= windowStartLine && cursorManager.cursorLine < windowStartLine + linesWindow.size()) {
      String lineText = getLineTextForRender(cursorManager.cursorLine);
      cursorManager.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorManager.cursorChar = targetCol;
    }
    resetCursorBlink();
    scrollManager.keepCursorVisibleHorizontally();
    invalidate();
    updateImeSelection();
  }

  void setCursorPositionNoClearForSearch(int line, int col) {
    setCursorPositionNoClear(line, col);
  }

  @Nullable
  String getLastComposingTextForCharAnim() {
    return charAnimationManager.getLastComposingTextForCharAnim();
  }

  void setLastComposingTextForCharAnim(@Nullable String text) {
    charAnimationManager.setLastComposingTextForCharAnim(text);
  }

  void clearLastComposingTextForCharAnim() {
    charAnimationManager.clearLastComposingTextForCharAnim();
  }

  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }

  public void setBracketMatchingEnabled(boolean enabled) {
    bracketMatchManager.setEnabled(enabled);
    invalidate();
  }

  public void setBracketMatchColor(int color) {
    bracketMatchManager.setColor(color);
    invalidate();
  }

  public void setBracketMatchStrokeWidth(float width) {
    bracketMatchManager.setStrokeWidth(width);
    updateTextSizeDependentMetrics();
    invalidate();
  }

  public void setBracketGuidesEnabled(boolean enabled) {
    bracketGuideManager.setEnabled(enabled);
    invalidate();
  }

  public void setBracketGuidesColor(int color) {
    bracketGuideManager.setColor(color);
    invalidate();
  }

  public void setBracketGuidesStrokeWidth(float width) {
    bracketGuideManager.setStrokeWidth(width);
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
    bracketGuideManager.invalidateCache();
    invalidateHighlightEnsureRange();
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
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
    bracketGuideManager.invalidateCache();
    invalidateHighlightEnsureRange();
    synchronized (lineWidthCache) {
      lineWidthCache.clear();
    }
    currentMaxWindowLineWidth = 0f;
    globalMaxLineWidth = 0f;
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
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
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
    invalidateHighlightEnsureRange();
    bracketGuideManager.invalidateCache();
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
    bracketGuideManager.invalidateCache();
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
    bracketGuideManager.invalidateCache();
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
    bracketGuideManager.invalidateCache();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  private int computeMinWindowSize() {
    return computeMinWindowSizeForPrefetch(prefetchLines);
  }

  private int computeMinWindowSizeForPrefetch(int prefetch) {
    if (lineHeight <= 0f || getHeight() <= 0) return 10;
    float effectiveHeight = (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(effectiveHeight / lineHeight) + 2);
    int minTotal = Math.max(visibleLines * 2, visibleLines + 6);
    int minWindow = minTotal - (Math.max(0, prefetch) * 2);
    return Math.max(10, minWindow);
  }

  private void reloadWindowAroundVisible(boolean recalcWidthSync) {
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
    invalidateLineNumberCache();
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
    this.currentLinePaint.setColor(color);
    if (highlightCurrentLine) invalidate();
  }

  public void setGutterSeparatorColor(int color) {
    lineNumberManager.gutterSeparatorPaint.setColor(color);
    if (showLineNumbers) {
      invalidate();
    }
  }

  public void setGutterSeparatorWidth(float width) {
    float safe = Math.max(0f, width);
    if (lineNumberManager.gutterSeparatorWidth == safe) return;
    lineNumberManager.gutterSeparatorWidth = safe;
    requestLayout();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    if (showLineNumbers) invalidate();
  }

  public void setCurrentLineNumberColor(int color) {
    if (this.currentLineNumberColor == color) return;
    this.currentLineNumberColor = color;
    if (showLineNumbers) invalidate();
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

  private void clearHighlightCaches() {
    highlightCache.clear();
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.clear();
    urlUnderlineCache.clear();
    pathUnderlineCache.clear();
    invalidateHighlightEnsureRange();
    bracketGuideManager.invalidateCache();
  }

  private void invalidateHighlightCacheForLine(int line) {
    highlightCache.remove(line);
    blockCommentEndStateCache.clear();
    stringEndStateCache.clear();
    colorCodeBgCache.remove(line);
    urlUnderlineCache.remove(line);
    pathUnderlineCache.remove(line);
    invalidateHighlightEnsureRange();
    bracketGuideManager.invalidateCache();
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

  private void setSingleLineCommentDelimiters(String... delimiters) {
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

  private void ensureLineCommentDelimiter(String delimiter) {
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

  private void setSingleLineCommentsHighlight(boolean enabled, int style, int color) {
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
    lineNumberManager.lineNumbersPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    foldMarkerPaint.setTextAlign(isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    invalidateLineNumberCache();
    requestLayout();
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    scrollManager.maxScrollXForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.scrollX = 0f;
    scrollManager.keepCursorVisibleHorizontally();
    invalidate();
  }

  public void setPopupBackgroundColor(int color) {
    popupMenuManager.setPopupBackgroundColor(color);
  }

  public void setPopupTextColor(int color) {
    popupMenuManager.setPopupTextColor(color);
  }

  public void setPopupTextSize(float sp) {
    popupMenuManager.setPopupTextSize(sp);
  }

  public void setPopupTextSizePx(float sizePx) {
    popupMenuManager.setPopupTextSizePx(sizePx);
  }

  private void applyPopupConfig() {
    popupMenuManager.applyPopupConfig();
  }

  public void setPopupTextFollowsEditorTypeface(boolean follow) {
    popupMenuManager.setPopupTextFollowsEditorTypeface(follow);
  }

  public void setPopupTextTypeface(@Nullable Typeface typeface) {
    popupMenuManager.setPopupTextTypeface(typeface);
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

  public int getCursorLineValue() {
    return cursorManager.cursorLine;
  }

  public int getCursorCharValue() {
    return cursorManager.cursorChar;
  }

  int getCursorLine() {
    return cursorManager.cursorLine;
  }

  int getCursorChar() {
    return cursorManager.cursorChar;
  }

  void setCursorLine(int line) {
    cursorManager.cursorLine = line;
  }

  void setCursorChar(int ch) {
    cursorManager.cursorChar = ch;
  }

  public boolean hasSelectionValue() {
    return selectionManager.hasSelection;
  }

  public int getSelectionStartLineValue() {
    return selectionManager.selStartLine;
  }

  public int getSelectionStartCharValue() {
    return selectionManager.selStartChar;
  }

  public int getSelectionEndLineValue() {
    return selectionManager.selEndLine;
  }

  public int getSelectionEndCharValue() {
    return selectionManager.selEndChar;
  }

  boolean isSelectAllActiveValue() {
    return selectionManager.isSelectAllActive;
  }

  boolean isEntireFileSelectedValue() {
    return selectionManager.isEntireFileSelected;
  }

  boolean isSelectingValue() {
    return selectionManager.selecting;
  }

  boolean isLineNumberSelectingValue() {
    return selectionManager.isLineNumberSelecting;
  }

  int getLineNumberSelectAnchorLineValue() {
    return selectionManager.lineNumberSelectAnchorLine;
  }

  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    int targetLine = Math.max(0, cursorLine);
    int targetChar = Math.max(0, cursorChar);
    this.cursorManager.cursorLine = targetLine;
    if (this.cursorManager.cursorLine >= windowStartLine
        && this.cursorManager.cursorLine < windowStartLine + linesWindow.size()) {
      String lineText = getLineTextForRender(this.cursorManager.cursorLine);
      this.cursorManager.cursorChar = Math.max(0, Math.min(targetChar, lineText.length()));
    } else {
      this.cursorManager.cursorChar = targetChar;
    }
    resetCursorBlink();
    invalidate();
  }

  public void showSelectionPopup() {
    if (selectionManager.hasSelection) {
      showPopupAtSelection();
    }
  }

  // --- Convenience cursor/line accessors ---
  public int getCurrentLineNumber() {
    return cursorManager.cursorLine;
  }

  public int getCurrentColumn() {
    return cursorManager.cursorChar;
  }

  public String getCurrentLineText() {
    return getLineTextForRender(cursorManager.cursorLine);
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

  public void setCursorPosition(int line, int col) {
    int targetLine = Math.max(0, line);
    int targetCol = Math.max(0, col);
    if (selectionManager.hasSelection) {
      selectionManager.hasSelection = false;
      selectionManager.isSelectAllActive = false;
      selectionManager.isEntireFileSelected = false;
      selectionManager.selecting = false;
      hidePopup();
    }
    cursorManager.cursorLine = targetLine;
    if (cursorManager.cursorLine >= windowStartLine && cursorManager.cursorLine < windowStartLine + linesWindow.size()) {
      String lineText = getLineTextForRender(cursorManager.cursorLine);
      cursorManager.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
    } else {
      cursorManager.cursorChar = targetCol;
    }
    resetCursorBlink();
    scrollManager.keepCursorVisibleHorizontally();
    invalidate();
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

  private float spToPx(float sp) {
    return sp * getResources().getDisplayMetrics().scaledDensity;
  }

  float spToPxForZoom(float sp) {
    return spToPx(sp);
  }

  private float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }

  private void updateTextSizeDependentMetrics() {
    float sizePx = paint.getTextSize();
    handlesManager.setHandleRadius(
        Math.max(4f, scaleByTextSize(handlesManager.getBaseHandleRadiusPx(), baseHandleTextSizePx, sizePx)));
    handlesManager.setCursorWidth(
        Math.max(1f, scaleByTextSize(handlesManager.getBaseCursorWidthPx(), baseCursorTextSizePx, sizePx)));

    bracketMatchManager.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketMatchManager.getBaseStrokeWidth(), bracketMatchManager.getBaseTextSizePx(), sizePx)));

    bracketGuideManager.applyScaledStrokeWidth(
        Math.max(1f, scaleByTextSize(bracketGuideManager.getBaseStrokeWidth(), bracketGuideManager.getBaseTextSizePx(), sizePx)));

    indentGuideStrokeWidth =
        Math.max(
            1f, scaleByTextSize(baseIndentGuideStrokeWidth, baseIndentGuideTextSizePx, sizePx));
    indentGuidePaint.setStrokeWidth(indentGuideStrokeWidth);
  }

  private void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  private void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    paint.setTextSize(sizePx);
    if (!isSuggestionTextSizeCustom) {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(sizePx * suggestionTextSizeScale);
    lineNumberManager.lineNumbersPaint.setTextSize(sizePx);
    foldMarkerPaint.setTextSize(sizePx * foldMarkerTextScale);
    wordWrapManager.updateIndicatorPaintForTextSize(sizePx, paint, WORD_WRAP_INDICATOR_TEXT);
    lineHeight = paint.getFontSpacing();
    updateTextSizeDependentMetrics();
    updateWhitespaceGuideMetrics();
    invalidateLineNumberCache();

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
    scrollManager.maxLineWidthForScroll *= scale;
    scrollManager.maxScrollXForScroll *= scale;
    scrollManager.maxTextStartXForScroll = 0f;
    if (scale < 1f) {
      scrollManager.maxLineWidthForScroll = 0f;
      scrollManager.maxScrollXForScroll = 0f;
    }

    requestLayout(); // Still needed for gutter
    if (isWordWrapEnabled) invalidateWrapMetrics(true, !deferWrapRebuild);
    requestWrapPrefixRebuild();
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
    return measureText(line, ch, globalLine);
  }

  float measureTextWithVisualSpacesForSearch(String line, int start, int end) {
    return measureTextWithVisualSpaces(line, start, end, paint);
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
    return sourceFile != null && sourceFile.exists();
  }

  void populateDirectLinesForRangeForSearch(
      int startLine, int endLine, java.util.Map<Integer, String> direct) {
    populateDirectLinesForRange(startLine, endLine, direct);
  }

  String getLineTextForRenderWithDirectForSearch(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    return getLineTextForRenderWithDirect(line, direct);
  }

  float spToPxForPopup(float sp) {
    return spToPx(sp);
  }

  @Nullable
  Typeface getEditorTypefaceForPopup() {
    return paint.getTypeface();
  }

  boolean shouldHideCopyCutForPopup() {
    return shouldHideCopyCutForSelection();
  }

  float getViewXForPopup(String lineText, int line, int ch) {
    return getViewXForLineChar(lineText, line, ch);
  }

  float getViewYTopForPopup(int line, int ch) {
    return getViewYTopForLineChar(line, ch);
  }

  boolean isPopupVisibleForScroll() {
    return popupMenuManager.isPopupVisible();
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

  HighlightLineState getLineStateAtStartForBracket(int line) {
    return getLineStateAtStart(line);
  }

  HighlightLineState getLineStateAtStartForMatch(int line) {
    return getLineStateAtStart(line);
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
    return STRING_STATE_TRIPLE;
  }

  int getStringStateBacktickForMatch() {
    return STRING_STATE_BACKTICK;
  }

  boolean isLineCommentStartForMatch(String line, int index) {
    return isLineCommentStart(line, index);
  }

  boolean isTokenEscapedForMatch(String line, int index) {
    return isTokenEscaped(line, index);
  }

  boolean isEscapedForMatch(String line, int index) {
    return isEscaped(line, index);
  }

  boolean isTripleQuoteStartForMatch(String line, int index) {
    return isTripleQuoteStart(line, index);
  }

  boolean isStringDelimiterForMatch(char c) {
    return isStringDelimiter(c);
  }

  int findBlockCommentEndForMatch(String line, int start) {
    return findBlockCommentEnd(line, start);
  }

  int findTripleQuoteEndForMatch(String line, int start) {
    return findTripleQuoteEnd(line, start);
  }

  StringEndResult findStringEndForStateForMatch(String line, int start, int state) {
    return findStringEndForState(line, start, state);
  }

  int findStringEndForMatch(String line, int start, char delimiter) {
    return findStringEnd(line, start, delimiter);
  }

  int getStringStateForDelimiterForMatch(char delimiter) {
    return getStringStateForDelimiter(delimiter);
  }

  float measureTextForMatch(String line, int index, int globalLine) {
    return measureText(line, index, globalLine);
  }

  float measureTextWithVisualSpacesForMatch(String line, int start, int end) {
    return measureTextWithVisualSpaces(line, start, end, paint);
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
    return STRING_STATE_TRIPLE;
  }

  int getStringStateBacktickForBracket() {
    return STRING_STATE_BACKTICK;
  }

  java.util.List<String> getLineCommentDelimitersForBracket() {
    return lineCommentDelimiters;
  }

  boolean isWhitespaceGuidesEnabledForBracket() {
    return isWhitespaceGuidesEnabled;
  }

  int getWhitespaceGuideSpaceStepForBracket() {
    return whitespaceGuideSpaceStep;
  }

  float getPaintTextSizeForBracket() {
    return paint.getTextSize();
  }

  boolean isRtlForBracket() {
    return isRtl;
  }

  float measureTextForBracket(String line, int index, int globalLine) {
    return measureText(line, index, globalLine);
  }

  float getVisualSpaceWidthForBracket() {
    return getVisualSpaceWidth(paint);
  }

  List<HighlightSpan> getHighlightSpansForBracket(int line) {
    return highlightCache.get(line);
  }

  void putHighlightSpansForBracket(int line, List<HighlightSpan> spans) {
    highlightCache.put(line, spans);
  }

  List<HighlightSpan> calculateSpansForLineForBracket(String line, int globalLine) {
    return calculateSpansForLine(line, globalLine);
  }

  float measureTextWithVisualSpacesForBracket(String line, int start, int end) {
    return measureTextWithVisualSpaces(line, start, end, paint);
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

  int findBlockCommentEndForBracket(String line, int start) {
    return findBlockCommentEnd(line, start);
  }

  StringEndResult findStringEndForStateForBracket(String line, int start, int state) {
    return findStringEndForState(line, start, state);
  }

  boolean isLineCommentStartForBracket(String line, int index) {
    return isLineCommentStart(line, index);
  }

  boolean isTokenEscapedForBracket(String line, int index) {
    return isTokenEscaped(line, index);
  }

  boolean isTripleQuoteStartForBracket(String line, int index) {
    return isTripleQuoteStart(line, index);
  }

  boolean isEscapedForBracket(String line, int index) {
    return isEscaped(line, index);
  }

  int findTripleQuoteEndForBracket(String line, int start) {
    return findTripleQuoteEnd(line, start);
  }

  boolean isStringDelimiterForBracket(char c) {
    return isStringDelimiter(c);
  }

  int findStringEndForBracket(String line, int start, char delimiter) {
    return findStringEnd(line, start, delimiter);
  }

  int getStringStateForDelimiterForBracket(char delimiter) {
    return getStringStateForDelimiter(delimiter);
  }

  int getBraceGuideColumnForLineForBracket(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    return getBraceGuideColumnForLine(line, globalLine, braceIndex, firstNonSpace);
  }

  boolean isSuggestionAcceptedThisTouch() {
    return suggestionAcceptedThisTouch;
  }

  void clearSuggestionAcceptedThisTouch() {
    suggestionAcceptedThisTouch = false;
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

  boolean isZoomMultiTouchBlockedForInput() {
    return zoomManager.isMultiTouchActive() || zoomManager.hadMultiTouch();
  }

  boolean isPopupVisibleForInput() {
    return popupMenuManager.isPopupVisible();
  }

  int getPopupActionAtForInput(float x, float y) {
    return getPopupActionAt(x, y);
  }

  void setPopupPressedActionForInput(int action) {
    popupMenuManager.setPressedAction(action);
  }

  void startPopupRippleHoldForInput(int action, float x, float y) {
    startPopupRippleHold(action, x, y);
  }

  boolean isLineNumberSelectionEnabledForInput() {
    return lineNumberManager.lineNumberSelectionEnabled;
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
    if (selectionManager.hasSelection) {
      selectionManager.hasSelection = false;
      selectionManager.isSelectAllActive = false;
      selectionManager.isEntireFileSelected = false;
    }
  }

  boolean isCodeFoldingEnabledForInput() {
    return isCodeFoldingEnabled;
  }

  boolean toggleFoldAtLineForInput(int line) {
    return toggleFoldAtLine(line);
  }

  void startFoldMarkerRippleForInput(int line) {
    startFoldMarkerRipple(line);
  }

  float getLineHeightForInput() {
    return lineHeight;
  }

  boolean isWordWrapEnabledForInput() {
    return isWordWrapEnabled;
  }

  int getTotalVisualLineCountForInput() {
    return getTotalVisualLineCount();
  }

  int getVisibleLineCountForInput() {
    return getVisibleLineCount();
  }

  float viewToTextXForInput(float x) {
    return viewToTextX(x);
  }

  int[] getWrapStartsForLineForInput(int line, String ln) {
    return getWrapStartsForLine(line, ln);
  }

  int getWrapSegmentIndexForCharForInput(int[] starts, int ch) {
    return getWrapSegmentIndexForChar(starts, ch);
  }

  int getWrapSegmentStartForInput(int[] starts, int seg) {
    return getWrapSegmentStart(starts, seg);
  }

  float measureTextWithVisualSpacesForInput(String s, int start, int end) {
    return measureTextWithVisualSpaces(s, start, end, paint);
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
    cursorManager.cursorLine = line;
    cursorManager.cursorChar = ch;
  }

  void insertTextAtCursorForInput(String text) {
    insertTextAtCursor(text);
  }

  void setSelectingForInput(boolean selectingNow) {
    selectionManager.selecting = selectingNow;
  }

  void updateSuggestionForInput() {
    updateSuggestion();
  }

  void showKeyboardForInput() {
    showKeyboard();
  }

  void restartInputForInput() {
    restartInput();
  }

  boolean handleScrollFromInput(MotionEvent e2, float distanceX, float distanceY) {
    return scrollManager.onScroll(e2, distanceX, distanceY);
  }

  boolean handleFlingFromInput(float velocityX, float velocityY) {
    return scrollManager.onFling(velocityX, velocityY);
  }

  void setPendingPopupAfterDoubleTap(boolean pending) {
    pendingPopupAfterDoubleTap = pending;
  }

  boolean isPendingPopupAfterDoubleTap() {
    return pendingPopupAfterDoubleTap;
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
    suggestionPaint.setTypeface(finalTypeface);
    lineNumberManager.lineNumbersPaint.setTypeface(finalTypeface);
    foldMarkerPaint.setTypeface(finalTypeface);
    wordWrapManager.updateIndicatorTypeface(paint, WORD_WRAP_INDICATOR_TEXT);
    whitespaceGuidePaint.setTypeface(finalTypeface);
    popupMenuManager.onEditorTypefaceChanged(finalTypeface);
    if (whitespaceStringRule != null) whitespaceStringRule.updateTypeface(safeBase);
    if (whitespaceCommentRule != null) whitespaceCommentRule.updateTypeface(safeBase);
    if (lineCommentHighlightRule != null) lineCommentHighlightRule.updateTypeface(safeBase);
    for (HighlightRule rule : highlightRules) {
      rule.updateTypeface(safeBase);
    }
    clearHighlightCaches();

    lineHeight = paint.getFontSpacing();
    updateWhitespaceGuideMetrics();
    invalidateLineNumberCache();
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
    if (isWordWrapEnabled) invalidateWrapMetrics(true);
    requestWrapPrefixRebuild();
    invalidate();
  }

  private void updateWhitespaceGuideMetrics() {
    whitespaceGuidePaint.setTextSize(paint.getTextSize());
    whitespaceGuidePaint.setTypeface(paint.getTypeface());
    whitespaceGuideSpaceWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_SPACE);
    whitespaceGuideTabWidth = whitespaceGuidePaint.measureText(WHITESPACE_GUIDE_TAB);
    whitespaceGuideDotPaint.setColor(whitespaceGuidePaint.getColor());
    whitespaceGuideDotPaint.setStrokeCap(Paint.Cap.ROUND);
    whitespaceGuideDotPaint.setStyle(Paint.Style.STROKE);
    float dotSize = Math.max(1f, paint.getTextSize() / 7f);
    whitespaceGuideDotPaint.setStrokeWidth(dotSize);

  }

  private void ensureHighlightCacheForVisibleRange(
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

  private void maybeEnsureHighlightCacheForRange(
      int startLine, int endLine, @Nullable java.util.HashMap<Integer, String> directLines) {
    if (startLine > endLine) return;
    int v = undoRedo.getEditVersion();
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

  private void invalidateHighlightEnsureRange() {
    lastHighlightEnsureStartLine = -1;
    lastHighlightEnsureEndLine = -1;
    lastHighlightEnsureEditVersion = -1;
  }

  // --- Layout and Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float oldGutterWidth = lineNumberManager.lineNumbersGutterWidth;
    if (showLineNumbers) {
      int maxLines = 0;
      if (isIndexReady) {
        maxLines = lineOffsets.length;
      } else if (isEof) {
        maxLines = windowStartLine + linesWindow.size();
      } else {
        maxLines = 999999; // Wider fallback for width calculation until index is ready
      }
      String maxLineNum = String.valueOf(maxLines);
      float baseWidth = lineNumberManager.lineNumbersPaint.measureText(maxLineNum) + (GUTTER_TEXT_PADDING * 2);
      if (isCodeFoldingEnabled) {
        foldMarkerGutterWidth =
            foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
      } else {
        foldMarkerGutterWidth = 0f;
      }
      lineNumberManager.lineNumbersGutterWidth = baseWidth + foldMarkerGutterWidth + lineNumberManager.gutterSeparatorWidth;
    } else {
      lineNumberManager.lineNumbersGutterWidth = 0f;
    }

    if (isWordWrapEnabled && Math.abs(lineNumberManager.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
    if (Math.abs(lineNumberManager.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
      invalidateLineNumberCache();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != oldw || h != oldh) {
      invalidateLineNumberCache();
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
    if (isWordWrapEnabled && w != oldw) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
  }

  float getTextStartX() {
    return isRtl ? paddingLeft : paddingLeft + lineNumberManager.lineNumbersGutterWidth;
  }

  float getEffectiveScrollX() {
    return isRtl ? -scrollManager.scrollX : scrollManager.scrollX;
  }

  private float viewToTextX(float viewX) {
    return viewX + getEffectiveScrollX() - getTextStartX();
  }

  private float getTextAreaWidth() {
    return Math.max(0f, getWidth() - lineNumberManager.lineNumbersGutterWidth - paddingLeft);
  }

  private float getRtlLineBaseX(@Nullable String line, int globalLine) {
    if (!isRtl || line == null) return 0f;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = getTextAreaWidth();
    return area - w;
  }

  private float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!isRtl || line == null) return 0f;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = getTextAreaWidth();
    return area - w;
  }

  float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x = measureText(line, charIndex, globalLine);
    if (!isRtl) return x;
    int logicalLen = getLogicalLineLength(globalLine, line);
    float w = measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  private float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel = measureTextWithVisualSpaces(line, segStart, charIndex, paint);
    if (!isRtl) return xRel;
    float w = measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  float getWrapWidth() {
    return Math.max(1f, getWidth() - getTextStartX());
  }

  private void invalidateWrapMetrics() {
    invalidateWrapMetrics(true, true);
  }

  private void invalidateWrapMetrics(boolean clearExisting) {
    invalidateWrapMetrics(clearExisting, true);
  }

  private void invalidateWrapMetrics(boolean clearExisting, boolean scheduleFullRebuild) {
    wordWrapManager.wrapCache.clear();
    wordWrapManager.wrapWidthPx = -1;
    wordWrapManager.wrapMetricsWidth = -1;
    wordWrapManager.wrapMetricsToken.incrementAndGet();
    wordWrapManager.wrapPrefixValidUpToLine = -1;

    if (clearExisting) {
      wordWrapManager.wrapLineCounts = null;
      wordWrapManager.wrapLinePrefix = null;
    }

    int currentLines = getLinesCount();
    if (currentLines <= 0) currentLines = windowStartLine + linesWindow.size();

    boolean sizeMismatch = (wordWrapManager.wrapLineCounts != null && wordWrapManager.wrapLineCounts.length != currentLines);
    boolean missing = (wordWrapManager.wrapLineCounts == null || wordWrapManager.wrapLinePrefix == null);

    if (clearExisting || sizeMismatch || missing) {
      // Metrics are invalid or requested to be cleared.
      // We must rebuild the visible window's metrics SYNCHRONOUSLY to avoid
      // falling back to 1:1 rendering (which causes jumps and disappearing lines).
      // This function effectively "patches" the metrics for the visible area immediately.
      buildWrapMetricsForWindowSnapshot();

      // If we still don't have metrics (e.g. empty file), mark as not ready.
      if (wordWrapManager.wrapLineCounts == null) {
        wordWrapManager.wrapMetricsReady = false;
        wordWrapManager.totalWrapVisualLines = 0;
      } else {
        wordWrapManager.wrapMetricsReady = true;
      }
    } else {
      // Keep existing metrics during minor updates to reduce visual jitter.
      wordWrapManager.wrapMetricsReady = true;
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

  void invalidateWrapMetricsForUndo(boolean clearExisting) {
    invalidateWrapMetrics(clearExisting);
  }

  void requestWrapPrefixRebuildForUndo() {
    requestWrapPrefixRebuild();
  }

  private void requestWrapPrefixRebuild() {
    if (!isWordWrapEnabled) return;
    if (zoomManager.isScaling() || zoomManager.isScaleInProgress()) {
      wordWrapManager.wrapPrefixRebuildPending = true;
      return;
    }
    scheduleWrapPrefixRebuildUpToWindow();
  }

  void cancelWrapPrefixRebuildForInteraction() {
    if (!wordWrapManager.wrapPrefixBuilding) return;
    // Invalidate the in-flight rebuild and defer a new one to avoid scroll lock/jumps.
    wordWrapManager.wrapPrefixToken.incrementAndGet();
    wordWrapManager.wrapPrefixBuilding = false;
    wordWrapManager.wrapPrefixRebuildPending = true;
  }

  private void cancelWrapWorkForPriority() {
    if (!isWordWrapEnabled) return;
    wordWrapManager.wrapMetricsToken.incrementAndGet();
    wordWrapManager.wrapSnapshotToken.incrementAndGet();
    wordWrapManager.wrapPrefixToken.incrementAndGet();
    wordWrapManager.wrapMetricsBuilding = false;
    wordWrapManager.wrapSnapshotBuilding = false;
    wordWrapManager.wrapPrefixBuilding = false;
  }

  private boolean shouldSuppressWrapMetricsForFastSelectAll() {
    if (!isWordWrapEnabled || (!selectionManager.isSelectAllActive && !selectionManager.isEntireFileSelected)) return false;
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    return !isWrapMetricsUsableForWindow(widthPx);
  }

  void scheduleWrapPrefixRebuildUpToWindow() {
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
    if (wordWrapManager.wrapPrefixBuilding && wordWrapManager.wrapPrefixWidth == widthPx && wordWrapManager.wrapPrefixTargetLine >= targetLineFinal)
      return;

    wordWrapManager.wrapPrefixBuilding = true;
    wordWrapManager.wrapPrefixWidth = widthPx;
    wordWrapManager.wrapPrefixTargetLine = targetLineFinal;

    if (!scrollManager.scroller.isFinished()) scrollManager.scroller.abortAnimation();

    final int token = wordWrapManager.wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (wordWrapManager.wrapLineCounts != null && wordWrapManager.wrapLineCounts.length == total) ? wordWrapManager.wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) (scrollManager.scrollY / lineHeight));
    VisualLinePosition anchorPos = getVisualPositionForIndex(anchorVisualIndex);
    final int anchorLine = anchorPos.line;
    final int oldAnchorPrefix =
        (wordWrapManager.wrapLinePrefix != null && anchorLine >= 0 && anchorLine < wordWrapManager.wrapLinePrefix.length)
            ? wordWrapManager.wrapLinePrefix[anchorLine]
            : anchorLine;

    final Paint wrapPaint = new Paint(paint);

    ioHandler.post(
        () -> {
          if (token != wordWrapManager.wrapPrefixToken.get()) return;

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
                    if (token != wordWrapManager.wrapPrefixToken.get()) return;
                    wordWrapManager.wrapPrefixBuilding = false;
                  });
              return;
            }
          } else {
            BufferedReader br = null;
            try {
              br = reopenReaderAtStart();
              int lineIndex = 0;
              while (lineIndex <= targetLineFinal) {
                if (token != wordWrapManager.wrapPrefixToken.get()) return;
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
                    if (token != wordWrapManager.wrapPrefixToken.get()) return;
                    wordWrapManager.wrapPrefixBuilding = false;
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
                if (token != wordWrapManager.wrapPrefixToken.get()) return;
                if (Math.max(1, Math.round(getWrapWidth())) != widthPx) {
                  wordWrapManager.wrapPrefixBuilding = false;
                  return;
                }
                wordWrapManager.wrapPrefixBuilding = false;
                if (isZoomGestureActive()) {
                  wordWrapManager.pendingWrapPrefixCounts = counts;
                  wordWrapManager.pendingWrapPrefixPrefix = prefix;
                  wordWrapManager.pendingWrapPrefixTotalVisualLines = runningFinal;
                  wordWrapManager.pendingWrapPrefixWidthPx = widthPx;
                  wordWrapManager.pendingWrapPrefixValidUpToLine =
                      Math.max(wordWrapManager.wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                  pendingApplyWrapPrefixUpdate = true;
                  return;
                }
                wordWrapManager.wrapLineCounts = counts;
                wordWrapManager.wrapLinePrefix = prefix;
                wordWrapManager.totalWrapVisualLines = runningFinal;
                wordWrapManager.wrapMetricsWidth = widthPx;
                wordWrapManager.wrapMetricsReady = true;
                wordWrapManager.wrapPrefixValidUpToLine =
                    Math.max(wordWrapManager.wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                if (deltaPrefix != 0) {
                  scrollManager.scrollY += deltaPrefix * lineHeight;
                  clampScrollY();
                }
                postInvalidateOnAnimation();
              });
        });
  }

  private void onLineContentChanged(int globalLine, @Nullable String newText) {
    if (!isWordWrapEnabled) return;
    wordWrapManager.wrapCache.remove(globalLine);

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!wordWrapManager.wrapMetricsReady
        || wordWrapManager.wrapLineCounts == null
        || wordWrapManager.wrapLinePrefix == null
        || wordWrapManager.wrapMetricsWidth != widthPx) {
      invalidateWrapMetrics();
      return;
    }
    if (globalLine < 0 || globalLine >= wordWrapManager.wrapLineCounts.length) {
      invalidateWrapMetrics();
      return;
    }

    int newCount = computeWrapCountForLine(newText, widthPx);
    int oldCount = wordWrapManager.wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    wordWrapManager.wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wordWrapManager.wrapLinePrefix.length; i++) {
      wordWrapManager.wrapLinePrefix[i] += delta;
    }
    wordWrapManager.totalWrapVisualLines += delta;
  }

  private void onLineCountChanged() {
    if (isWordWrapEnabled) invalidateWrapMetrics();
    invalidateLineNumberCache();
  }

  private void buildWrapMetricsForWindowSnapshot() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wordWrapManager.wrapLineCounts = null;
      wordWrapManager.wrapLinePrefix = null;
      wordWrapManager.totalWrapVisualLines = 0;
      wordWrapManager.wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    int[] counts;

    // Preserve existing counts to avoid jumpiness when lines scroll out of window
    if (wordWrapManager.wrapLineCounts != null && wordWrapManager.wrapLineCounts.length == total) {
      counts = wordWrapManager.wrapLineCounts.clone();
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

    wordWrapManager.wrapLineCounts = counts;
    wordWrapManager.wrapLinePrefix = prefix;
    wordWrapManager.totalWrapVisualLines = running;
    wordWrapManager.wrapMetricsWidth = widthPx;
    // Mark as valid up to the end so isWrapMetricsUsableForWindow returns true
    wordWrapManager.wrapPrefixValidUpToLine = total - 1;
    wordWrapManager.wrapMetricsReady = true;
  }

  private void scheduleWrapMetricsSnapshotIfNeeded(int widthPx) {
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

    if (wordWrapManager.wrapSnapshotBuilding
        && wordWrapManager.wrapSnapshotWidth == widthPx
        && wordWrapManager.wrapSnapshotStart == start
        && wordWrapManager.wrapSnapshotSize == size) {
      return;
    }

    wordWrapManager.wrapSnapshotWidth = widthPx;
    wordWrapManager.wrapSnapshotStart = start;
    wordWrapManager.wrapSnapshotSize = size;
    wordWrapManager.wrapSnapshotBuilding = true;
    final int token = wordWrapManager.wrapSnapshotToken.incrementAndGet();
    final Paint wrapPaint = new Paint(paint);

    ioHandler.post(
        () -> {
          int total = getLinesCount();
          if (total <= 0) total = start + size;
          if (total <= 0) {
            post(
                () -> {
                  if (token == wordWrapManager.wrapSnapshotToken.get()) {
                    wordWrapManager.wrapMetricsReady = true;
                    wordWrapManager.wrapMetricsBuilding = false;
                    wordWrapManager.wrapSnapshotBuilding = false;
                  }
                });
            return;
          }

          int[] counts;
          boolean widthChanged = (wordWrapManager.wrapMetricsWidth != widthPx);
          // If width changed or size mismatch, we must reset.
          // Otherwise, clone existing counts to preserve off-screen metrics and avoid race
          // conditions.
          if (wordWrapManager.wrapLineCounts == null || wordWrapManager.wrapLineCounts.length != total || widthChanged) {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          } else {
            counts = wordWrapManager.wrapLineCounts.clone();
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
                if (token != wordWrapManager.wrapSnapshotToken.get()) return;
                wordWrapManager.wrapLineCounts = counts;
                wordWrapManager.wrapLinePrefix = prefix;
                wordWrapManager.totalWrapVisualLines = runningFinal;
                wordWrapManager.wrapMetricsWidth = widthPx;
                wordWrapManager.wrapMetricsReady = true;
                wordWrapManager.wrapSnapshotBuilding = false;
                postInvalidateOnAnimation();
              });
        });
  }

  private void scheduleWrapMetricsBuild() {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    if (getWidth() <= 0) return;
    if (sourceFile == null || !isIndexReady) {
      buildWrapMetricsInMemory();
      return;
    }
    final int token = wordWrapManager.wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(getWrapWidth()));
    final Paint wrapPaint = new Paint(paint);
    wordWrapManager.wrapMetricsBuilding = true;
    ioHandler.post(() -> buildWrapMetricsFromFile(token, widthPx, wrapPaint));
  }

  private void buildWrapMetricsInMemory() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wordWrapManager.wrapLineCounts = null;
      wordWrapManager.wrapLinePrefix = null;
      wordWrapManager.totalWrapVisualLines = 0;
      wordWrapManager.wrapMetricsReady = true;
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
    wordWrapManager.wrapLineCounts = counts;
    wordWrapManager.wrapLinePrefix = prefix;
    wordWrapManager.totalWrapVisualLines = running;
    wordWrapManager.wrapMetricsWidth = widthPx;
    wordWrapManager.wrapMetricsReady = true;
    wordWrapManager.wrapPrefixValidUpToLine = (windowStartLine == 0) ? (total - 1) : -1;
    postInvalidateOnAnimation();
  }

  private void buildWrapMetricsFromFile(int token, int widthPx, Paint wrapPaint) {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) {
      wordWrapManager.wrapLineCounts = null;
      wordWrapManager.wrapLinePrefix = null;
      wordWrapManager.totalWrapVisualLines = 0;
      wordWrapManager.wrapMetricsReady = true;
      wordWrapManager.wrapMetricsBuilding = false;
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
        if (token != wordWrapManager.wrapMetricsToken.get()) {
          wordWrapManager.wrapMetricsBuilding = false;
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
      wordWrapManager.wrapMetricsBuilding = false;
      return;
    } finally {
      try {
        if (br != null) br.close();
      } catch (Exception ignored) {
      }
    }
    if (token != wordWrapManager.wrapMetricsToken.get()) {
      wordWrapManager.wrapMetricsBuilding = false;
      return;
    }
    wordWrapManager.wrapLineCounts = counts;
    wordWrapManager.wrapLinePrefix = prefix;
    wordWrapManager.totalWrapVisualLines = running;
    wordWrapManager.wrapMetricsWidth = widthPx;
    wordWrapManager.wrapMetricsReady = true;
    wordWrapManager.wrapPrefixValidUpToLine = total - 1;
    wordWrapManager.wrapMetricsBuilding = false;
    postInvalidateOnAnimation();
  }

  private int computeWrapCountForLine(String line, int widthPx) {
    int[] starts = computeWrapStarts(line, widthPx, paint, true);
    return Math.max(1, starts.length);
  }

  private int computeWrapCountForLine(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    int[] starts = computeWrapStarts(line, widthPx, p, useSharedBuffer);
    return Math.max(1, starts.length);
  }

  private int[] getWrapStartsForLine(int globalLine, String line) {
    if (!isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wordWrapManager.wrapWidthPx != widthPx) {
      wordWrapManager.wrapWidthPx = widthPx;
      wordWrapManager.wrapCache.clear();
    }
    boolean cacheable = isWrapCacheableForLine(globalLine);
    if (!cacheable) {
      wordWrapManager.wrapCache.remove(globalLine);
      return computeWrapStarts(line, widthPx, paint, true);
    }
    int[] cached = wordWrapManager.wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts = computeWrapStarts(line, widthPx, paint, true);
    wordWrapManager.wrapCache.put(globalLine, starts);
    return starts;
  }

  private boolean isWrapCacheableForLine(int globalLine) {
    if (globalLine >= windowStartLine && globalLine < windowStartLine + linesWindow.size()) {
      return true;
    }
    synchronized (modifiedLines) {
      return modifiedLines.containsKey(globalLine);
    }
  }

  private int[] computeWrapStarts(String line, int widthPx, Paint p, boolean useSharedBuffer) {
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

  private boolean shouldUseBreakTextWrap(String line) {
    if (getVisualSpaceScale() != 1) return false;
    return line.indexOf('\t') < 0;
  }

  private int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
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

  private int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    if (starts == null || starts.length == 0) return 0;
    int idx = 0;
    for (int i = 0; i < starts.length; i++) {
      if (starts[i] <= charIndex) idx = i;
      else break;
    }
    return idx;
  }

  private int getWrapSegmentStart(int[] starts, int segIndex) {
    if (starts == null || starts.length == 0) return 0;
    if (segIndex <= 0) return starts[0];
    return starts[Math.min(segIndex, starts.length - 1)];
  }

  private int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segIndex + 1;
    if (next >= 0 && next < starts.length) return starts[next];
    return lineLength;
  }

  private int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
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

  private CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    float y = viewY + scrollManager.scrollY;
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

  private int getWindowEndLine() {
    synchronized (linesWindow) {
      return Math.max(0, windowStartLine + linesWindow.size() - 1);
    }
  }

  private boolean isWrapMetricsUsableForWindow(int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wordWrapManager.wrapMetricsReady || wordWrapManager.wrapLinePrefix == null || wordWrapManager.wrapLineCounts == null) return false;
    if (wordWrapManager.wrapMetricsWidth != widthPx) return false;
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    if (total <= 0) return false;
    if (wordWrapManager.wrapLineCounts.length != total || wordWrapManager.wrapLinePrefix.length != total + 1) return false;
    int windowEnd = getWindowEndLine();
    return wordWrapManager.wrapPrefixValidUpToLine >= windowEnd;
  }

  private boolean isWrapMetricsUsableForLine(int line) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) return false;
    return wordWrapManager.wrapPrefixValidUpToLine >= line;
  }

  int getTotalVisualLineCount() {
    if (!isWordWrapEnabled) return getVisibleLineCount();
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      int total = getLinesCount();
      if (total <= 0) total = windowStartLine + linesWindow.size();
      return Math.max(1, total);
    }
    return Math.max(1, wordWrapManager.totalWrapVisualLines);
  }

  private int getWrapRangeCount(int startLine, int endLine) {
    if (wordWrapManager.wrapLinePrefix == null) return 0;
    int total = wordWrapManager.wrapLinePrefix.length - 1;
    int s = Math.max(0, Math.min(startLine, total - 1));
    int e = Math.max(s, Math.min(endLine, total - 1));
    return wordWrapManager.wrapLinePrefix[e + 1] - wordWrapManager.wrapLinePrefix[s];
  }

  static final class VisualLinePosition {
    final int line;
    final int segment;

    VisualLinePosition(int line, int segment) {
      this.line = line;
      this.segment = segment;
    }
  }

  private VisualLinePosition getVisualPositionForIndex(int visualIndex) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      if (isWordWrapEnabled) {
        return getVisualPositionForIndexFallback(visualIndex, widthPx);
      }
      int line = mapVisibleIndexToGlobal(visualIndex);
      return new VisualLinePosition(line, 0);
    }
    int maxVisual = Math.max(0, wordWrapManager.totalWrapVisualLines - 1);
    int v = Math.max(0, Math.min(visualIndex, maxVisual));
    int line = findLineForVisualIndex(v);
    int seg = v - wordWrapManager.wrapLinePrefix[line];
    return new VisualLinePosition(line, seg);
  }

  private VisualLinePosition getVisualPositionForIndexFallback(int visualIndex, int widthPx) {
    int idx = Math.max(0, visualIndex);
    int baseLine = Math.max(0, windowStartLine);
    int baseVisual = baseLine;
    if (wordWrapManager.wrapLinePrefix != null
        && wordWrapManager.wrapPrefixValidUpToLine >= baseLine
        && baseLine < wordWrapManager.wrapLinePrefix.length) {
      baseVisual = wordWrapManager.wrapLinePrefix[baseLine];
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

  private float getViewXForLineChar(String line, int globalLine, int ch) {
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

  private float getViewYTopForLineChar(int globalLine, int ch) {
    int v = getVisualIndexForLineAndChar(globalLine, ch);
    return v * lineHeight - scrollManager.scrollY;
  }

  private int findLineForVisualIndex(int visualIndex) {
    if (wordWrapManager.wrapLinePrefix == null || wordWrapManager.wrapLinePrefix.length == 0) return 0;
    int lo = 0;
    int hi = wordWrapManager.wrapLinePrefix.length - 1;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (wordWrapManager.wrapLinePrefix[mid] <= visualIndex) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    int line = Math.max(0, lo - 1);
    return Math.min(line, wordWrapManager.wrapLinePrefix.length - 2);
  }

  private boolean patchWrapMetricsForVisualRange(
      int firstVisualIndex,
      int lastVisualIndex,
      @Nullable java.util.Map<Integer, String> directLines,
      int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wordWrapManager.wrapMetricsReady || wordWrapManager.wrapLineCounts == null || wordWrapManager.wrapLinePrefix == null) return false;
    if (wordWrapManager.wrapMetricsWidth != widthPx) return false;
    if (wordWrapManager.wrapLineCounts.length + 1 != wordWrapManager.wrapLinePrefix.length) return false;

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
      if (line < 0 || line >= wordWrapManager.wrapLineCounts.length) break;
      String text = getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(line, text);
      int newCount = Math.max(1, starts.length);
      int oldCount = wordWrapManager.wrapLineCounts[line];
      if (newCount == oldCount) continue;

      int delta = newCount - oldCount;
      wordWrapManager.wrapLineCounts[line] = newCount;
      for (int i = line + 1; i < wordWrapManager.wrapLinePrefix.length; i++) {
        wordWrapManager.wrapLinePrefix[i] += delta;
      }
      wordWrapManager.totalWrapVisualLines += delta;
      changed = true;
    }

    if (!changed) return false;

    if (anchorLine >= 0 && anchorLine < wordWrapManager.wrapLinePrefix.length) {
      int newAnchorFirstVisual = wordWrapManager.wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        scrollManager.scrollY += dv * lineHeight;
        clampScrollY();
      }
    }
    return true;
  }

  private float getGutterStartX() {
    return isRtl ? getWidth() - lineNumberManager.lineNumbersGutterWidth : 0;
  }

  private boolean isInLineNumberGutter(float x) {
    if (!showLineNumbers || lineNumberManager.lineNumbersGutterWidth <= 0f) return false;
    float start = getGutterStartX();
    return x >= start && x <= start + lineNumberManager.lineNumbersGutterWidth;
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
    clearActiveSuggestion();
    selectionManager.isLineNumberSelecting = true;
    selectionManager.lineNumberSelectAnchorLine = clamped;
    selectionManager.hasSelection = true;
    selectionManager.selecting = true;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    String lineText = getLineTextForRender(clamped);
    selectionManager.selStartLine = clamped;
    selectionManager.selStartChar = 0;
    selectionManager.selEndLine = clamped;
    selectionManager.selEndChar = lineText.length();
    cursorManager.cursorLine = clamped;
    cursorManager.cursorChar = selectionManager.selEndChar;
    hidePopup();
    resetCursorBlink();
    invalidate();
  }

  private void updateLineNumberSelection(int line) {
    if (!selectionManager.isLineNumberSelecting) return;
    int clamped = clampLineForSelection(line);
    if (!isLineSelectable(clamped)) return;
    int startLine = Math.min(selectionManager.lineNumberSelectAnchorLine, clamped);
    int endLine = Math.max(selectionManager.lineNumberSelectAnchorLine, clamped);
    scrollManager.ensureLineInWindow(endLine, true);
    String endLineText = getLineTextForRender(endLine);
    selectionManager.selStartLine = startLine;
    selectionManager.selStartChar = 0;
    selectionManager.selEndLine = endLine;
    selectionManager.selEndChar = endLineText.length();
    cursorManager.cursorLine = endLine;
    cursorManager.cursorChar = selectionManager.selEndChar;
    selectionManager.hasSelection = true;
    selectionManager.selecting = true;
    hidePopup();
    invalidate();
  }

  private final Runnable autoScrollRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (draggingHandle == 0) return;
          if (scrollManager.autoScrollX != 0 || scrollManager.autoScrollY != 0) {
            scrollManager.scrollX += scrollManager.autoScrollX;
            float nextY = scrollManager.scrollY + scrollManager.autoScrollY;
            if (!isIndexReady && !isEof && isWindowLoading) {
              float effectiveHeight =
                  (keyboardHeight > 0) ? getHeight() - keyboardHeight : getHeight();
              float winTop = windowStartLine * lineHeight;
              float winBottom = (windowStartLine + linesWindow.size()) * lineHeight;
              float maxY = Math.max(0f, winBottom - effectiveHeight);
              if (scrollManager.autoScrollY > 0 && nextY > maxY) nextY = maxY;
              if (scrollManager.autoScrollY < 0 && nextY < winTop) nextY = winTop;
            }
            scrollManager.scrollY = nextY;
            clampScrollX();
            clampScrollY();
            updateHandlePosition(lastTouchX, lastTouchY);
            if (draggingHandle == 1 || draggingHandle == 2) {
              showPopupAtSelection();
            }
            checkAndLoadWindow();
            invalidate();
            mainHandler.postDelayed(this, 16);
          }
        }
      };

  private String buildFoldDisplayLine(String line, FoldRange range, int[] placeholderBoundsOut) {
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

  private void drawFoldedLine(Canvas canvas, String line, int globalLine) {
    FoldRange range = foldRanges.get(globalLine);
    if (range == null) return;

    float y = Math.round(scrollManager.getDrawLineTop(globalLine) + lineHeight - paint.descent());
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

  private boolean isFoldPlaceholderHit(int globalLine, @Nullable String line, float localX) {
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

  private void drawHighlightedSegment(
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

  private float measureHighlightedSegmentWidth(String line, int globalLine, int start, int end) {
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

  private String getFoldMarkerForLine(int line, @Nullable String lineText) {
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

  private boolean isIndentFoldCandidate(String line) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = rstripWhitespace(line);
    return !trimmed.isEmpty() && trimmed.endsWith(":");
  }

  private void startFoldMarkerRipple(int line) {
    if (!isCodeFoldingEnabled || !showLineNumbers) return;
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

  private void clearFoldRipple() {
    if (foldRippleAnimator != null) {
      foldRippleAnimator.cancel();
      foldRippleAnimator = null;
    }
    foldRippleAlpha = 0f;
    foldRippleRadius = 0f;
    foldRippleLine = -1;
  }

  private boolean shouldShowFoldMarkerFromLine(String line) {
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

  private void drawContent(Canvas canvas) {
    if (isWordWrapEnabled) {
      drawContentWrapped(canvas);
      return;
    }
    final boolean drawDecorations = zoomManager.shouldDrawDecorations();

    // Calculate visible line range
    int firstVisibleIndex = (int) (scrollManager.scrollY / lineHeight);
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
    float translateY = -scrollManager.scrollY + baseY;
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
    if (showLineNumbers) {
      canvas.drawRect(
          getGutterStartX(),
          0,
          getGutterStartX() + lineNumberManager.lineNumbersGutterWidth,
          getHeight(),
          lineNumberManager.gutterPaint);

      // Draw separator line
      float separatorLeft;
      if (isRtl) {
        // Separator is on the left side of the gutter (inner edge)
        separatorLeft = getGutterStartX();
      } else {
        // Separator is on the right side of the gutter (inner edge)
        separatorLeft = getGutterStartX() + lineNumberManager.lineNumbersGutterWidth - lineNumberManager.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumberManager.gutterSeparatorWidth,
          getHeight(),
          lineNumberManager.gutterSeparatorPaint);
    }

    if (highlightCurrentLineInGutter
        && cursorManager.cursorLine >= firstVisibleLine
        && cursorManager.cursorLine <= lastVisibleLine
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursorManager.cursorLine))) {
      int drawIndex = isCodeFoldingEnabled ? getVisibleIndexForGlobalLine(cursorManager.cursorLine) : cursorManager.cursorLine;
      float top = Math.round(drawIndex * lineHeight - scrollManager.scrollY);
      float bottom = top + lineHeight;
      drawCurrentLineHighlightInGutter(canvas, top, bottom);
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (showLineNumbers) {
      lineNumberManager.drawLineNumbersCachedUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (isCodeFoldingEnabled && drawDecorations) {
        drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    // Clip the text area so it doesn't draw over the gutter
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumberManager.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumberManager.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), translateY);
    if (zoomManager.isPinchVisualZoomActive()) {
      float pivotX = zoomManager.getPinchFocusX() - (getTextStartX() - getEffectiveScrollX());
      float pivotY = zoomManager.getPinchFocusY() - translateY;
      canvas.scale(zoomManager.getPinchVisualScale(), zoomManager.getPinchVisualScale(), pivotX, pivotY);
    }

    // --- This is the original text, selection, and handle drawing logic ---
    Paint selPaint = null;
    if (selectionManager.hasSelection) {
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

    BracketMatchManager.BracketMatch bracketMatch =
        bracketMatchManager.getMatch(firstVisibleLine, lastVisibleLine, directLines);

    int winEnd;
    synchronized (linesWindow) {
      winEnd = windowStartLine + linesWindow.size() - 1;
    }
    int prefetchForDraw = isZoomGestureActive() ? 0 : prefetchLines;
    int hlStart = Math.max(windowStartLine, Math.max(0, firstVisibleLine - prefetchForDraw));
    int hlEnd = Math.min(winEnd, lastVisibleLine + prefetchForDraw);
    maybeEnsureHighlightCacheForRange(hlStart, hlEnd, directLines);

    if (bracketGuideManager.isEnabled() && drawDecorations) {
      bracketGuideManager.ensureCacheForWindow(directLines);
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
        if (highlightCurrentLine && globalLine == cursorManager.cursorLine && !selectionManager.hasSelection) {
          float top = Math.round(scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
          float viewLeft = isRtl ? 0f : lineNumberManager.lineNumbersGutterWidth;
          float viewRight = isRtl ? (getWidth() - lineNumberManager.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, currentLinePaint);
        }

        if (selectionManager.hasSelection && selPaint != null) {
          float top = Math.round(scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth, scrollManager.scrollX + (getWidth() - getTextStartX()));
          if (isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selectionManager.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selectionManager.selStartLine;
              boolean roundBottom = globalLine == selectionManager.selEndLine;
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
            if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) <= 0) {
              startLine = selectionManager.selStartLine;
              startChar = selectionManager.selStartChar;
              endLine = selectionManager.selEndLine;
              endChar = selectionManager.selEndChar;
            } else {
              startLine = selectionManager.selEndLine;
              startChar = selectionManager.selEndChar;
              endLine = selectionManager.selStartLine;
              endChar = selectionManager.selStartChar;
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

        float y = Math.round(scrollManager.getDrawLineTop(globalLine) + lineHeight - paint.descent());
        paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        if (isFoldStart) {
          if (bracketGuideManager.isEnabled() && drawDecorations) {
            List<BracketGuideManager.BracketGuideToken> guideTokens = bracketGuideManager.getTokensForLine(globalLine);
            bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
          }
          if (drawDecorations) {
            drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
            drawIndentGuidesForLine(canvas, line, globalLine);
          }
          drawFoldedLine(canvas, line, globalLine);
          canvas.restore();
          continue;
        }

        float lineTop = Math.round(scrollManager.getDrawLineTop(globalLine));
        float lineBottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
        searchManager.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (bracketGuideManager.isEnabled() && drawDecorations) {
          List<BracketGuideManager.BracketGuideToken> guideTokens = bracketGuideManager.getTokensForLine(globalLine);
          bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
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
        if (highlightCurrentLine && globalLine == cursorManager.cursorLine && !selectionManager.hasSelection) {
          float top = Math.round(scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
          float viewLeft = isRtl ? 0f : lineNumberManager.lineNumbersGutterWidth;
          float viewRight = isRtl ? (getWidth() - lineNumberManager.lineNumbersGutterWidth) : getWidth();
          float left = viewLeft + getEffectiveScrollX() - getTextStartX();
          float right = viewRight + getEffectiveScrollX() - getTextStartX();
          canvas.drawRect(left, top, right, bottom, currentLinePaint);
        }

        if (selectionManager.hasSelection && selPaint != null) {
          float top = Math.round(scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(currentMaxWindowLineWidth, scrollManager.scrollX + (getWidth() - getTextStartX()));
          if (isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (selectionManager.isSelectAllActive) {
            boolean lineExists =
                (isEof) ? (globalLine <= windowStartLine + linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == selectionManager.selStartLine;
              boolean roundBottom = globalLine == selectionManager.selEndLine;
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
            if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) <= 0) {
              startLine = selectionManager.selStartLine;
              startChar = selectionManager.selStartChar;
              endLine = selectionManager.selEndLine;
              endChar = selectionManager.selEndChar;
            } else {
              startLine = selectionManager.selEndLine;
              startChar = selectionManager.selEndChar;
              endLine = selectionManager.selStartLine;
              endChar = selectionManager.selStartChar;
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

        float y = Math.round(scrollManager.getDrawLineTop(globalLine) + lineHeight - paint.descent());
        paint.setUnderlineText(false); // Force disable underline before drawing

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        // Draw color code backgrounds underneath the text
        drawColorCodeBackgrounds(canvas, line, globalLine);

        float lineTop = Math.round(scrollManager.getDrawLineTop(globalLine));
        float lineBottom = Math.round(scrollManager.getDrawLineBottom(globalLine));
        searchManager.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForLine(canvas, line, globalLine, y);
          drawIndentGuidesForLine(canvas, line, globalLine);
        }

        // Draw auto-completion suggestion
        drawAutoSuggestion(canvas, line, globalLine, y);

        if (bracketGuideManager.isEnabled() && drawDecorations) {
          List<BracketGuideManager.BracketGuideToken> guideTokens = bracketGuideManager.getTokensForLine(globalLine);
          bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
        }
        canvas.restore();
      }
    }
    if (isFocused()
        && !isReadOnly
        && !selectionManager.hasSelection
        && cursorManager.cursorLine >= firstVisibleLine
        && cursorManager.cursorLine <= lastVisibleLine
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursorManager.cursorLine))) {
      String cursorLineText = getLineTextForRender(cursorManager.cursorLine);
      int safeChar = Math.min(cursorManager.cursorChar, getLogicalLineLength(cursorManager.cursorLine, cursorLineText));
      float cursorX = getCaretXForLine(cursorLineText, cursorManager.cursorLine, safeChar);
      float cursorY = scrollManager.getDrawLineTop(cursorManager.cursorLine);
      updateCursorDrawPosition(cursorX, cursorY);
      float drawX = cursorAnimationManager.getCursorDrawX();
      float drawY = cursorAnimationManager.getCursorDrawY();
      if (cursorAnimationManager.isCursorVisible()) {
        caretPaint.setColor(handlesManager.getCaretColor());
        caretPaint.setStrokeWidth(handlesManager.getCursorWidth());
        canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
      }
      handlePaint.setColor(handlesManager.getCursorHandleColor());
      drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
      handlesManager.getCursorHandleRect().set(
          drawX - handlesManager.getHandleRadius(),
          drawY + lineHeight,
          drawX + handlesManager.getHandleRadius(),
          drawY + lineHeight + handlesManager.getHandleRadius() * 2);
    }

    if (selectionManager.hasSelection && !isReadOnly) {
      handlePaint.setColor(handlesManager.getSelectionHandleColor());
      if (selectionManager.selStartLine >= firstVisibleLine
          && selectionManager.selStartLine <= lastVisibleLine
          && (!isCodeFoldingEnabled || !isLineHiddenByFold(selectionManager.selStartLine))) {
        String startLineText = getLineTextForRender(selectionManager.selStartLine);
        float startX =
            getCaretXForLine(
                startLineText,
                selectionManager.selStartLine,
                Math.min(selectionManager.selStartChar, getLogicalLineLength(selectionManager.selStartLine, startLineText)));
        float startY = scrollManager.getDrawLineTop(selectionManager.selStartLine) + lineHeight;
        drawTeardropHandle(canvas, startX, startY, handlePaint);
        if (isRtl) {
          handlesManager.getRightHandleRect().set(
              startX - handlesManager.getHandleRadius(), startY, startX + handlesManager.getHandleRadius(), startY + handlesManager.getHandleRadius() * 2);
        } else {
          handlesManager.getLeftHandleRect().set(
              startX - handlesManager.getHandleRadius(), startY, startX + handlesManager.getHandleRadius(), startY + handlesManager.getHandleRadius() * 2);
        }
      } else {
        if (isRtl) handlesManager.getRightHandleRect().setEmpty();
        else handlesManager.getLeftHandleRect().setEmpty();
      }
      if (selectionManager.selEndLine >= firstVisibleLine
          && selectionManager.selEndLine <= lastVisibleLine
          && (!isCodeFoldingEnabled || !isLineHiddenByFold(selectionManager.selEndLine))) {
        String endLineText = getLineTextForRender(selectionManager.selEndLine);
        float endX =
            getCaretXForLine(
                endLineText,
                selectionManager.selEndLine,
                Math.min(selectionManager.selEndChar, getLogicalLineLength(selectionManager.selEndLine, endLineText)));
        float endY = scrollManager.getDrawLineTop(selectionManager.selEndLine) + lineHeight;
        drawTeardropHandle(canvas, endX, endY, handlePaint);
        if (isRtl) {
          handlesManager.getLeftHandleRect().set(
              endX - handlesManager.getHandleRadius(), endY, endX + handlesManager.getHandleRadius(), endY + handlesManager.getHandleRadius() * 2);
        } else {
          handlesManager.getRightHandleRect().set(
              endX - handlesManager.getHandleRadius(), endY, endX + handlesManager.getHandleRadius(), endY + handlesManager.getHandleRadius() * 2);
        }
      } else {
        if (isRtl) handlesManager.getLeftHandleRect().setEmpty();
        else handlesManager.getRightHandleRect().setEmpty();
      }
    }

    canvas.restore();
    // --- End of main text content drawing ---

    // --- 4. Draw overlays (popups, loading circle, etc.) ---
    if (popupMenuManager.isPopupVisible()) popupMenuManager.drawPopup(canvas);

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

  private void drawContentWrapped(Canvas canvas) {
    int wrapWidthPx = Math.max(1, Math.round(getWrapWidth()));
    final boolean drawDecorations = zoomManager.shouldDrawDecorations();
    if (!isZoomGestureActive()) {
      applyPendingWrapPrefixUpdateIfAny();
    }
    if (shouldSuppressWrapMetricsForFastSelectAll()) {
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    if (!isWrapMetricsUsableForWindow(wrapWidthPx)) {
      if (!wordWrapManager.wrapMetricsReady || wordWrapManager.wrapMetricsWidth != wrapWidthPx) {
        scheduleWrapMetricsSnapshotIfNeeded(wrapWidthPx);
      }
      if (wordWrapManager.wrapPrefixValidUpToLine < getWindowEndLine()) {
        requestWrapPrefixRebuild();
      }
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    int totalLines = getLinesCount();
    if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
    if (totalLines <= 0) totalLines = 1;

    int totalVisual = getTotalVisualLineCount();
    int firstVisualIndex = Math.max(0, (int) (scrollManager.scrollY / lineHeight));
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

    // Safety: after zoom/fast scroll, wordWrapManager.wrapLineCounts might be stale for some visible lines.
    // Don't patch during pinch/scale: it can fight the zoom's own scroll math and cause a brief
    // "jump".
    boolean patched = false;
    if (!isZoomGestureActive()) {
      patched =
          patchWrapMetricsForVisualRange(
              firstVisualIndex, lastVisualIndex, directLines, wrapWidthPx);
    }
    if (patched) {
      totalLines = getLinesCount();
      if (totalLines <= 0) totalLines = windowStartLine + linesWindow.size();
      if (totalLines <= 0) totalLines = 1;

      totalVisual = getTotalVisualLineCount();
      firstVisualIndex = Math.max(0, (int) (scrollManager.scrollY / lineHeight));
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
    float translateY = -scrollManager.scrollY + baseY;

    // --- 1. Draw fixed gutter background ---
    if (showLineNumbers) {
      canvas.drawRect(
          getGutterStartX(),
          0,
          getGutterStartX() + lineNumberManager.lineNumbersGutterWidth,
          getHeight(),
          lineNumberManager.gutterPaint);

      float separatorLeft;
      if (isRtl) {
        separatorLeft = getGutterStartX();
      } else {
        separatorLeft = getGutterStartX() + lineNumberManager.lineNumbersGutterWidth - lineNumberManager.gutterSeparatorWidth;
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumberManager.gutterSeparatorWidth,
          getHeight(),
          lineNumberManager.gutterSeparatorPaint);
    }

    if (highlightCurrentLineInGutter
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursorManager.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursorManager.cursorLine, 0);
      String cursorLineText = getLineTextForRender(cursorManager.cursorLine);
      int[] starts = getWrapStartsForLine(cursorManager.cursorLine, cursorLineText);
      int segCount = Math.max(1, starts.length);
      int lastVisualIndexForLine = currentVisualIndex + segCount - 1;
      int drawFrom = Math.max(firstVisualIndex, currentVisualIndex);
      int drawTo = Math.min(lastVisualIndex, lastVisualIndexForLine);
      for (int v = drawFrom; v <= drawTo; v++) {
        float top = Math.round(v * lineHeight - scrollManager.scrollY);
        float bottom = top + lineHeight;
        drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // --- 2. Draw line numbers (vertically scrolled) ---
    if (showLineNumbers) {
      lineNumberManager.drawLineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    // --- 3. Draw main text content (scrolled) ---
    canvas.save();
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumberManager.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumberManager.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), translateY);
    if (zoomManager.isPinchVisualZoomActive()) {
      float pivotX = zoomManager.getPinchFocusX() - (getTextStartX() - getEffectiveScrollX());
      float pivotY = zoomManager.getPinchFocusY() - translateY;
      canvas.scale(zoomManager.getPinchVisualScale(), zoomManager.getPinchVisualScale(), pivotX, pivotY);
    }

    Paint selPaint = null;
    if (selectionManager.hasSelection) {
      selectionPaint.setColor(selectionHighlightColor);
      selPaint = selectionPaint;
    }

    int startLine = selectionManager.selStartLine;
    int startChar = selectionManager.selStartChar;
    int endLine = selectionManager.selEndLine;
    int endChar = selectionManager.selEndChar;
    if (selectionManager.hasSelection && comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
      startLine = selectionManager.selEndLine;
      startChar = selectionManager.selEndChar;
      endLine = selectionManager.selStartLine;
      endChar = selectionManager.selStartChar;
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

      if (highlightCurrentLine && pos.line == cursorManager.cursorLine && !selectionManager.hasSelection) {
        canvas.drawRect(
            -paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, currentLinePaint);
      }

      if (selectionManager.hasSelection && selPaint != null) {
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
      if (wordWrapManager.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        segDrawEnd = clampSegmentEndForWrapIndicator(line, segStart, segEnd, wrapWidthPx);
      }
      canvas.save();
      if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
      searchManager.drawSearchHighlightsForSegment(canvas, line, pos.line, segStart, segDrawEnd, top, bottom);
      drawHighlightedLineSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      drawErrorUnderlinesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      drawDeleteAnimationForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      if (drawDecorations) {
        drawWhitespaceGuidesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      }
      drawAutoSuggestionWrapped(canvas, line, pos.line, segStart, segDrawEnd, v, y);
      if (wordWrapManager.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        float indicatorX =
            isRtl
                ? wordWrapManager.wordWrapIndicatorPadPx
                : Math.max(
                    wordWrapManager.wordWrapIndicatorPadPx,
                    wrapWidthPx - wordWrapManager.wordWrapIndicatorWidth - wordWrapManager.wordWrapIndicatorPadPx);
        canvas.drawText(WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrapManager.wordWrapIndicatorPaint);
      }
      canvas.restore();
    }

    if (isFocused() && !isReadOnly && !selectionManager.hasSelection) {
      int cursorVisualIndex = getVisualIndexForLineAndChar(cursorManager.cursorLine, cursorManager.cursorChar);
      if (cursorVisualIndex >= firstVisualIndex && cursorVisualIndex <= lastVisualIndex) {
        String cursorLineText = getLineTextForRenderWithDirect(cursorManager.cursorLine, directLines);
        int[] starts = getWrapStartsForLine(cursorManager.cursorLine, cursorLineText);
        int seg = getWrapSegmentIndexForChar(starts, cursorManager.cursorChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, cursorLineText.length());
        int safeChar = Math.min(cursorManager.cursorChar, cursorLineText.length());
        float cursorX = getCaretXForSegment(cursorLineText, cursorManager.cursorLine, segStart, segEnd, safeChar);
        float cursorY = (cursorVisualIndex - firstVisualIndex) * lineHeight;
        updateCursorDrawPosition(cursorX, cursorY);
        float drawX = cursorAnimationManager.getCursorDrawX();
        float drawY = cursorAnimationManager.getCursorDrawY();
        if (cursorAnimationManager.isCursorVisible()) {
          caretPaint.setColor(handlesManager.getCaretColor());
          caretPaint.setStrokeWidth(handlesManager.getCursorWidth());
          canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
        }
        handlePaint.setColor(handlesManager.getCursorHandleColor());
        drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
        handlesManager.getCursorHandleRect().set(
            drawX - handlesManager.getHandleRadius(),
            drawY + lineHeight,
            drawX + handlesManager.getHandleRadius(),
            drawY + lineHeight + handlesManager.getHandleRadius() * 2);
      } else {
        handlesManager.getCursorHandleRect().setEmpty();
      }
    }

    if (selectionManager.hasSelection) {
      handlePaint.setColor(handlesManager.getSelectionHandleColor());
      int startVisual = getVisualIndexForLineAndChar(selectionManager.selStartLine, selectionManager.selStartChar);
      if (startVisual >= firstVisualIndex && startVisual <= lastVisualIndex) {
        String startLineText = getLineTextForRenderWithDirect(selectionManager.selStartLine, directLines);
        int[] starts = getWrapStartsForLine(selectionManager.selStartLine, startLineText);
        int seg = getWrapSegmentIndexForChar(starts, selectionManager.selStartChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            getCaretXForSegment(
                startLineText,
                selectionManager.selStartLine,
                segStart,
                segEnd,
                Math.min(selectionManager.selStartChar, startLineText.length()));
        float y = (startVisual - firstVisualIndex) * lineHeight + lineHeight;
        drawTeardropHandle(canvas, x, y, handlePaint);
        if (isRtl) {
          handlesManager.getRightHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
        } else {
          handlesManager.getLeftHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
        }
      } else {
        if (isRtl) handlesManager.getRightHandleRect().setEmpty();
        else handlesManager.getLeftHandleRect().setEmpty();
      }
      int endVisual = getVisualIndexForLineAndChar(selectionManager.selEndLine, selectionManager.selEndChar);
      if (endVisual >= firstVisualIndex && endVisual <= lastVisualIndex) {
        String endLineText = getLineTextForRenderWithDirect(selectionManager.selEndLine, directLines);
        int[] starts = getWrapStartsForLine(selectionManager.selEndLine, endLineText);
        int seg = getWrapSegmentIndexForChar(starts, selectionManager.selEndChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            getCaretXForSegment(
                endLineText,
                selectionManager.selEndLine,
                segStart,
                segEnd,
                Math.min(selectionManager.selEndChar, endLineText.length()));
        float y = (endVisual - firstVisualIndex) * lineHeight + lineHeight;
        drawTeardropHandle(canvas, x, y, handlePaint);
        if (isRtl) {
          handlesManager.getLeftHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
        } else {
          handlesManager.getRightHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
        }
      } else {
        if (isRtl) handlesManager.getLeftHandleRect().setEmpty();
        else handlesManager.getRightHandleRect().setEmpty();
      }
    }

    canvas.restore();

    if (popupMenuManager.isPopupVisible()) popupMenuManager.drawPopup(canvas);

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

  private void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
    int firstIndex = Math.max(0, (int) (scrollManager.scrollY / lineHeight));
    int lastIndex = firstIndex + (int) Math.ceil(getHeight() / lineHeight) + 5;
    final boolean drawDecorations = zoomManager.shouldDrawDecorations();

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
    float translateY = -scrollManager.scrollY + baseY;

    // Draw gutter background
    if (showLineNumbers) {
      canvas.drawRect(
          getGutterStartX(),
          0,
          getGutterStartX() + lineNumberManager.lineNumbersGutterWidth,
          getHeight(),
          lineNumberManager.gutterPaint);
      float separatorLeft =
          isRtl
              ? getGutterStartX()
              : getGutterStartX() + lineNumberManager.lineNumbersGutterWidth - lineNumberManager.gutterSeparatorWidth;
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + lineNumberManager.gutterSeparatorWidth,
          getHeight(),
          lineNumberManager.gutterSeparatorPaint);
    }

    if (highlightCurrentLineInGutter
        && (!isCodeFoldingEnabled || !isLineHiddenByFold(cursorManager.cursorLine))) {
      int currentVisualIndex = getVisualIndexForLineAndChar(cursorManager.cursorLine, 0);
      if (currentVisualIndex >= firstIndex && currentVisualIndex <= lastIndex) {
        float top = Math.round(currentVisualIndex * lineHeight - scrollManager.scrollY);
        float bottom = top + lineHeight;
        drawCurrentLineHighlightInGutter(canvas, top, bottom);
      }
    }

    // Disable line number cache in fallback because it relies on global metrics which are likely
    // invalid here.
    // This prevents "counts wrapped line as a separate line" visual bug.
    boolean useLineNumberCache = false;

    canvas.save();
    // Translate for scrolling content
    canvas.translate(0, translateY);

    // Pre-calculate line number X position
    float lineNumX = 0f;
    if (showLineNumbers && !useLineNumberCache) {
      lineNumX =
          isRtl
              ? getGutterStartX() + GUTTER_TEXT_PADDING
              : getGutterStartX() + lineNumberManager.lineNumbersGutterWidth - GUTTER_TEXT_PADDING;
    }

    // Prepare text clipping
    int saveCount = canvas.save();
    if (isRtl) {
      canvas.clipRect(0, 0, getWidth() - lineNumberManager.lineNumbersGutterWidth, getHeight());
    } else {
      canvas.clipRect(lineNumberManager.lineNumbersGutterWidth, 0, getWidth(), getHeight());
    }
    canvas.translate(getTextStartX() - getEffectiveScrollX(), 0); // already translated by translateY

    Paint selPaint = null;
    if (selectionManager.hasSelection) {
      selectionPaint.setColor(selectionHighlightColor);
      selPaint = selectionPaint;
    }

    int startLine = selectionManager.selStartLine;
    int startChar = selectionManager.selStartChar;
    int endLine = selectionManager.selEndLine;
    int endChar = selectionManager.selEndChar;
    if (selectionManager.hasSelection && comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
      startLine = selectionManager.selEndLine;
      startChar = selectionManager.selEndChar;
      endLine = selectionManager.selStartLine;
      endChar = selectionManager.selStartChar;
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
        if (showLineNumbers && seg == 0 && !useLineNumberCache) {
          canvas.restore(); // Exit text clip
          lineNumberManager.drawLineNumber(
              canvas, line, lineNumX, y, currentLineNumberColor, line == cursorManager.cursorLine);
          canvas.save(); // Re-enter text clip
          if (isRtl) {
            canvas.clipRect(0, 0, getWidth() - lineNumberManager.lineNumbersGutterWidth, getHeight());
          } else {
            canvas.clipRect(lineNumberManager.lineNumbersGutterWidth, 0, getWidth(), getHeight());
          }
          canvas.translate(getTextStartX() - getEffectiveScrollX(), 0);
        }

        if (highlightCurrentLine && line == cursorManager.cursorLine && !selectionManager.hasSelection) {
          canvas.drawRect(
              -paddingLeft, top, Math.max(getWrapWidth(), getWidth()), bottom, currentLinePaint);
        }

        if (selectionManager.hasSelection && selPaint != null) {
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
        if (wordWrapManager.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          segDrawEnd = clampSegmentEndForWrapIndicator(text, segStart, segEnd, wrapWidthPx);
        }
        canvas.save();
        if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
        searchManager.drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
        drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
        if (drawDecorations) {
          drawWhitespaceGuidesForSegment(canvas, text, line, segStart, segDrawEnd, y);
        }
        drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, visualIndex, y);
        if (wordWrapManager.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          float indicatorX =
              isRtl
                  ? wordWrapManager.wordWrapIndicatorPadPx
                  : Math.max(
                      wordWrapManager.wordWrapIndicatorPadPx,
                      wrapWidthPx - wordWrapManager.wordWrapIndicatorWidth - wordWrapManager.wordWrapIndicatorPadPx);
          canvas.drawText(WORD_WRAP_INDICATOR_TEXT, indicatorX, y, wordWrapManager.wordWrapIndicatorPaint);
        }
        canvas.restore();

        if (!cursorDrawn && isFocused() && !selectionManager.hasSelection && line == cursorManager.cursorLine) {
          int cursorSeg = getWrapSegmentIndexForChar(starts, cursorManager.cursorChar);
          if (cursorSeg == seg) {
            int safeChar = Math.min(cursorManager.cursorChar, text.length());
            float cursorX = getCaretXForSegment(text, line, segStart, segEnd, safeChar);
            float cursorY = top;
            updateCursorDrawPosition(cursorX, cursorY);
            float drawX = cursorAnimationManager.getCursorDrawX();
            float drawY = cursorAnimationManager.getCursorDrawY();
            if (cursorAnimationManager.isCursorVisible()) {
              caretPaint.setColor(handlesManager.getCaretColor());
              caretPaint.setStrokeWidth(handlesManager.getCursorWidth());
              canvas.drawLine(drawX, drawY, drawX, drawY + lineHeight, caretPaint);
            }
            handlePaint.setColor(handlesManager.getCursorHandleColor());
            drawTeardropHandle(canvas, drawX, drawY + lineHeight, handlePaint);
            handlesManager.getCursorHandleRect().set(
                drawX - handlesManager.getHandleRadius(),
                drawY + lineHeight,
                drawX + handlesManager.getHandleRadius(),
                drawY + lineHeight + handlesManager.getHandleRadius() * 2);
            cursorDrawn = true;
          }
        }

        if (selectionManager.hasSelection) {
          if (line == selectionManager.selStartLine) {
            int selSeg = getWrapSegmentIndexForChar(starts, selectionManager.selStartChar);
            if (selSeg == seg) startHandleVisual = visualIndex;
          }
          if (line == selectionManager.selEndLine) {
            int selSeg = getWrapSegmentIndexForChar(starts, selectionManager.selEndChar);
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

    if (selectionManager.hasSelection) {
      handlePaint.setColor(handlesManager.getSelectionHandleColor());
      if (startHandleVisual >= firstIndex && startHandleVisual <= visualIndex - 1) {
        String startLineText = getLineTextForRenderWithDirect(selectionManager.selStartLine, directLines);
        int[] starts = getWrapStartsForLine(selectionManager.selStartLine, startLineText);
        int seg = getWrapSegmentIndexForChar(starts, selectionManager.selStartChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            getCaretXForSegment(
                startLineText,
                selectionManager.selStartLine,
                segStart,
                segEnd,
                Math.min(selectionManager.selStartChar, startLineText.length()));
        float y = (startHandleVisual - firstIndex) * lineHeight + lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, handlePaint);
        handlesManager.getLeftHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
      } else {
        handlesManager.getLeftHandleRect().setEmpty();
      }

      if (endHandleVisual >= firstIndex && endHandleVisual <= visualIndex - 1) {
        String endLineText = getLineTextForRenderWithDirect(selectionManager.selEndLine, directLines);
        int[] starts = getWrapStartsForLine(selectionManager.selEndLine, endLineText);
        int seg = getWrapSegmentIndexForChar(starts, selectionManager.selEndChar);
        int segStart = getWrapSegmentStart(starts, seg);
        int segEnd = getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            getCaretXForSegment(
                endLineText,
                selectionManager.selEndLine,
                segStart,
                segEnd,
                Math.min(selectionManager.selEndChar, endLineText.length()));
        float y = (endHandleVisual - firstIndex) * lineHeight + lineHeight + translateY;
        drawTeardropHandle(canvas, x, y, handlePaint);
        handlesManager.getRightHandleRect().set(x - handlesManager.getHandleRadius(), y, x + handlesManager.getHandleRadius(), y + handlesManager.getHandleRadius() * 2);
      } else {
        handlesManager.getRightHandleRect().setEmpty();
      }
    }

    if (popupMenuManager.isPopupVisible()) popupMenuManager.drawPopup(canvas);

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
    if (scrollManager.stretchOverscrollEnabled && (scrollManager.stretchX != 0f || scrollManager.stretchY != 0f)) {
      float sx = 1f + (scrollManager.stretchX * 0.12f * scrollManager.stretchOverscrollStrength);
      float sy = 1f + (scrollManager.stretchY * 0.12f * scrollManager.stretchOverscrollStrength);
      float pivotX = (scrollManager.stretchDirX < 0) ? 0f : (scrollManager.stretchDirX > 0 ? getWidth() : getWidth() * 0.5f);
      float pivotY = (scrollManager.stretchDirY < 0) ? 0f : (scrollManager.stretchDirY > 0 ? getHeight() : getHeight() * 0.5f);
      canvas.save();
      canvas.scale(sx, sy, pivotX, pivotY);
      drawContent(canvas);
      canvas.restore();
    } else {
      drawContent(canvas);
    }
    drawScrollBar(canvas);
  }

  private void drawEditorBackground(Canvas canvas) {
    if (hasEditorBackgroundColor) {
      canvas.drawColor(editorBackgroundColor);
    }
    if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
      editorBackgroundDst.set(0, 0, getWidth(), getHeight());
      canvas.drawBitmap(editorBackgroundBitmap, null, editorBackgroundDst, null);
    }
  }

  private Paint getPaintForChar(int lineIndex, int charIndex, String lineText) {
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

  private float getAverageCharWidthForLine(String line, int lineIndex) {
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

  private void drawHighlightedLine(Canvas canvas, String line, int globalLine, float y) {
    if (line.isEmpty()) {
      if (charAnimationManager.isEnabled()
          && globalLine == charAnimationManager.getDelAnimLine()
          && charAnimationManager.getDelAnimText() != null
          && !charAnimationManager.getDelAnimText().isEmpty()
          && charAnimationManager.getDelAnimAlpha() > 0f) {
        Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
        charAnimationManager.getTempPaint().set(ghostPaint);
        charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(charAnimationManager.getDelAnimText(), 0f, y, charAnimationManager.getTempPaint());
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
    if (charAnimationManager.isEnabled()
        && globalLine == charAnimationManager.getCharAnimLine()
        && charAnimationManager.getCharAnimEndChar() > charAnimationManager.getCharAnimStartChar()
        && charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimationManager.getCharAnimAlpha()));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = scrollManager.getDrawLineTop(globalLine);
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
      if (charAnimationManager.isEnabled()
          && globalLine == charAnimationManager.getDelAnimLine()
          && charAnimationManager.getDelAnimText() != null
          && !charAnimationManager.getDelAnimText().isEmpty()
          && charAnimationManager.getDelAnimAlpha() > 0f) {
        int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
        charAnimationManager.getTempPaint().set(ghostPaint);
        charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
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
      if (charAnimationManager.isEnabled()
          && globalLine == charAnimationManager.getDelAnimLine()
          && charAnimationManager.getDelAnimText() != null
          && !charAnimationManager.getDelAnimText().isEmpty()
          && charAnimationManager.getDelAnimAlpha() > 0f) {
        int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
        charAnimationManager.getTempPaint().set(ghostPaint);
        charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
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

    if (charAnimationManager.isEnabled()
        && globalLine == charAnimationManager.getDelAnimLine()
        && charAnimationManager.getDelAnimText() != null
        && !charAnimationManager.getDelAnimText().isEmpty()
        && charAnimationManager.getDelAnimAlpha() > 0f) {
      int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
      float x = measureText(line, at, globalLine);
      Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
      charAnimationManager.getTempPaint().set(ghostPaint);
      charAnimationManager.getTempPaint().setUnderlineText(false);
      int baseAlpha = ghostPaint.getAlpha();
      charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
      canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
    }
    drawErrorUnderlinesForLine(canvas, line, globalLine, y, lineTop, lineBottom);
  }

  private void drawHighlightedLineRange(
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
    if (charAnimationManager.isEnabled()
        && globalLine == charAnimationManager.getCharAnimLine()
        && charAnimationManager.getCharAnimEndChar() > charAnimationManager.getCharAnimStartChar()
        && charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimationManager.getCharAnimAlpha()));
      if (fadeEnd <= fadeStart) {
        fadeStart = -1;
        fadeEnd = -1;
      }
    }

    float lineTop = scrollManager.getDrawLineTop(globalLine);
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

    if (charAnimationManager.isEnabled()
        && globalLine == charAnimationManager.getDelAnimLine()
        && charAnimationManager.getDelAnimText() != null
        && !charAnimationManager.getDelAnimText().isEmpty()
        && charAnimationManager.getDelAnimAlpha() > 0f) {
      int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
      if (at >= start && at <= end) {
        float x = measureText(line, at, globalLine);
        Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
        charAnimationManager.getTempPaint().set(ghostPaint);
        charAnimationManager.getTempPaint().setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
        canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
      }
    }
    drawErrorUnderlinesForLineRange(canvas, line, globalLine, start, end, y, lineTop, lineBottom);
  }

  private void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
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
    float viewLeft = isRtl ? 0f : lineNumberManager.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumberManager.lineNumbersGutterWidth) : getWidth();
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

  private void getVisibleCharRangeForLineFast(
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
    float viewLeft = isRtl ? 0f : lineNumberManager.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumberManager.lineNumbersGutterWidth) : getWidth();
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

  private void computeStreamedSliceBounds(
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
    float viewLeft = isRtl ? 0f : lineNumberManager.lineNumbersGutterWidth;
    float viewRight = isRtl ? (getWidth() - lineNumberManager.lineNumbersGutterWidth) : getWidth();
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

  private void drawFoldMarkersForVisibleLines(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!isCodeFoldingEnabled) return;

    float markerX =
        isRtl
            ? (getGutterStartX() + lineNumberManager.gutterSeparatorWidth + foldMarkerEdgePadding)
            : (getGutterStartX()
                + lineNumberManager.lineNumbersGutterWidth
                - lineNumberManager.gutterSeparatorWidth
                - foldMarkerEdgePadding);

    for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
      int line = mapVisibleIndexToGlobal(v);
      String marker = getFoldMarkerForLine(line, getLineTextForRender(line));
      if (marker == null) continue;
      float y = Math.round(v * lineHeight - scrollManager.scrollY + lineHeight - paint.descent());
      if (line == foldRippleLine && foldRippleAlpha > 0f) {
        int base = foldMarkerPaint.getColor();
        int alpha = Math.min(255, Math.max(0, (int) (255f * foldRippleAlpha)));
        foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
        float centerY = Math.round(v * lineHeight - scrollManager.scrollY + lineHeight * 0.5f);
        canvas.drawCircle(markerX, centerY, foldRippleRadius, foldRipplePaint);
      }
      canvas.drawText(marker, markerX, y, foldMarkerPaint);
    }
  }

  private void drawHighlightedLineSegment(
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
    if (charAnimationManager.isEnabled()
        && globalLine == charAnimationManager.getCharAnimLine()
        && charAnimationManager.getCharAnimEndChar() > charAnimationManager.getCharAnimStartChar()
        && charAnimationManager.getCharAnimAlpha() < 1f) {
      fadeStart = Math.max(0, Math.min(charAnimationManager.getCharAnimStartChar(), line.length()));
      fadeEnd = Math.max(0, Math.min(charAnimationManager.getCharAnimEndChar(), line.length()));
      fadeAlpha = Math.max(0f, Math.min(1f, charAnimationManager.getCharAnimAlpha()));
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

  private void drawDeleteAnimationForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
    if (!charAnimationManager.isEnabled()) return;
    if (globalLine != charAnimationManager.getDelAnimLine()
        || charAnimationManager.getDelAnimText() == null
        || charAnimationManager.getDelAnimText().isEmpty()
        || charAnimationManager.getDelAnimAlpha() <= 0f) return;
    if (line == null) line = "";
    int at = Math.max(0, Math.min(charAnimationManager.getDelAnimAtChar(), line.length()));
    if (at < segStart || at > segEnd) return;
    float x = measureTextWithVisualSpaces(line, segStart, at, paint);
    Paint ghostPaint = (charAnimationManager.getDelAnimPaint() != null) ? charAnimationManager.getDelAnimPaint() : paint;
    charAnimationManager.getTempPaint().set(ghostPaint);
    charAnimationManager.getTempPaint().setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, charAnimationManager.getDelAnimAlpha()))));
    canvas.drawText(charAnimationManager.getDelAnimText(), x, y, charAnimationManager.getTempPaint());
  }

  private void drawWhitespaceGuidesForSegment(
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

  private void drawAutoSuggestionWrapped(
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
    float top_view = visualIndex * lineHeight - scrollManager.scrollY;
    float bottom_view = top_view + lineHeight;

    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  private void drawWhitespaceGuidesForLine(Canvas canvas, String line, int globalLine, float y) {
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

  private void drawWhitespaceGuidesForRangeRtl(
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

  private float drawTextSegmentWithFade(
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
      charAnimationManager.getTempPaint().set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
      canvas.drawText(line, fadeSegStart, fadeSegEnd, currentX, y, charAnimationManager.getTempPaint());
      currentX += segmentPaint.measureText(line, fadeSegStart, fadeSegEnd);
    }

    int afterStart = Math.max(start, fadeEnd);
    if (afterStart < end) {
      canvas.drawText(line, afterStart, end, currentX, y, segmentPaint);
      currentX += segmentPaint.measureText(line, afterStart, end);
    }

    return currentX - x;
  }

  private boolean containsArabicScript(CharSequence text, int start, int end) {
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

  private boolean isMixedDirectionText(CharSequence text, int start, int end) {
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

  private float drawTextSegmentWithFadeAndUnderlines(
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

  private void drawUnderlineSegmentWithFade(
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

  private void drawErrorUnderlinesForLine(
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

  private void drawErrorUnderlinesForLineRange(
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

  private void drawErrorUnderlinesForSegment(
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

  private void drawErrorSquiggle(
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
  private List<UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
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

  private static int trimUrlUnderlineEnd(String line, int start, int end) {
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

  private int getVisualSpaceScale() {
    return 1;
  }

  private int getWhitespaceGuideStep() {
    return Math.max(1, whitespaceGuideSpaceStep);
  }

  private float getVisualSpaceWidth(Paint p) {
    return p.measureText(" ");
  }

  private float getVisualTabWidth(Paint p) {
    // Treat tab as a fixed number of spaces.
    return getVisualSpaceWidth(p) * DEFAULT_TAB_SIZE_SPACES;
  }

  private float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
    if (c == ' ') {
      return measuredWidth;
    }
    if (c == '\t') {
      return getVisualTabWidth(p);
    }
    return measuredWidth;
  }

  private float measureTextWithVisualSpaces(String text, int start, int end, Paint p) {
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

  private float drawTextSegmentWithVisualSpaces(
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
      charAnimationManager.getTempPaint().set(segmentPaint);
      int baseAlpha = segmentPaint.getAlpha();
      charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, alphaMultiplier))));
      drawPaint = charAnimationManager.getTempPaint();
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

  private List<HighlightSpan> calculateSyntaxSpansForLine(String line, int globalLine) {
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

  private List<HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
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

  private float drawWhitespaceGuidesSegment(
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

  private List<HighlightSpan> calculateSpansForLine(String line, int globalLine) {
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

  private LineParseResult parseLineForSyntax(
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

  private HighlightLineState getLineStateAtStart(int globalLine) {
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

  private static boolean hasOverlap(HighlightSpan span, List<HighlightSpan> spans) {
    for (HighlightSpan other : spans) {
      if (span.start < other.end && other.start < span.end) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLineCommentRegex(String regex) {
    if (regex == null) return false;
    String r = regex.trim();
    if (r.startsWith("//")) return true;
    if (r.startsWith("^//")) return true;
    if (r.startsWith("^\\s*//")) return true;
    if (r.startsWith("\\s*//")) return true;
    return false;
  }

  private boolean isStringDelimiter(char c) {
    if (c == '"') return true;
    if (c == '\'') return true;
    return c == '`' && isBacktickStringsEnabled;
  }

  private static boolean isTokenEscaped(String line, int index) {
    if (isEscaped(line, index)) return true;
    int next = index + 1;
    return next < line.length() && isEscaped(line, next);
  }

  private static boolean isEscaped(String line, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0; i--) {
      if (line.charAt(i) != '\\') break;
      backslashes++;
    }
    return (backslashes % 2) == 1;
  }

  private static int findStringEnd(String line, int start, char delimiter) {
    for (int i = start; i < line.length(); i++) {
      if (line.charAt(i) == delimiter && !isEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  private boolean isTripleQuoteStart(String line, int index) {
    if (!isTripleQuoteStringsEnabled) return false;
    if (index + 2 >= line.length()) return false;
    return line.charAt(index) == '"'
        && line.charAt(index + 1) == '"'
        && line.charAt(index + 2) == '"';
  }

  private static int findTripleQuoteEnd(String line, int start) {
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

  private static final int STRING_STATE_DOUBLE = 1;
  private static final int STRING_STATE_SINGLE = 2;
  private static final int STRING_STATE_BACKTICK = 3;
  private static final int STRING_STATE_TRIPLE = 4;

  private int getStringStateForDelimiter(char delimiter) {
    if (delimiter == '"') return STRING_STATE_DOUBLE;
    if (delimiter == '\'') return STRING_STATE_SINGLE;
    return STRING_STATE_BACKTICK;
  }

  private StringEndResult findStringEndForState(String line, int start, int state) {
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

  static class StringEndResult {
    final boolean found;
    final int endIndex;

    StringEndResult(boolean found, int endIndex) {
      this.found = found;
      this.endIndex = endIndex;
    }
  }

  private static int findBlockCommentEnd(String line, int start) {
    for (int i = start; i + 1 < line.length(); i++) {
      if (line.charAt(i) == '*' && line.charAt(i + 1) == '/' && !isTokenEscaped(line, i)) {
        return i;
      }
    }
    return -1;
  }

  private boolean isLineCommentStart(String line, int index) {
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

  private boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    List<HighlightSpan> spans = highlightCache.get(globalLine);
    if (spans == null) {
      spans = calculateSpansForLine(line, globalLine);
      highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    final float eps = 0.25f;

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          for (int i = pos; i < Math.min(span.start, len); i++) {
            float adv = measureTextWithVisualSpaces(line, i, i + 1, paint);
            if (x >= currentX - eps && x <= currentX + adv + eps) {
              return Character.isWhitespace(line.charAt(i));
            }
            currentX += adv;
          }
        }
        int start = Math.max(pos, span.start);
        int end = Math.min(len, span.end);
        for (int i = start; i < end; i++) {
          float adv = measureTextWithVisualSpaces(line, i, i + 1, paint);
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
        float adv = measureTextWithVisualSpaces(line, i, i + 1, paint);
        if (x >= currentX - eps && x <= currentX + adv + eps) {
          return Character.isWhitespace(line.charAt(i));
        }
        currentX += adv;
      }
    }

    return true;
  }

private void drawIndentGuidesForLine(Canvas canvas, String line, int globalLine) {
    if (!isIndentGuidesEnabled || !isIndentationBlocksEnabled || isHeavyDrawSuppressed()) return;
    if (!isLineInIndentBlock(globalLine)) return;
    if (line == null || line.isEmpty()) return;
    int unitSpaces = INDENT_BLOCK_UNIT.length();
    if (unitSpaces <= 0) return;

    float top = scrollManager.getDrawLineTop(globalLine);
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

  private boolean isLineInIndentBlock(int globalLine) {
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

  private void rebuildIndentGuideIntervalsIfNeeded() {
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
    java.util.Collections.sort(indentGuideIntervals, (a, b) -> Integer.compare(a[0], b[0]));
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

  private void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine) {
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

    float top = scrollManager.getDrawLineTop(globalLine);
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

  private float measureText(String line, int length, int globalLine) {
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

  private void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
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

  void maybeKickWindowLoad(int firstVisibleLine) {
    if (isZoomGestureActive()) return;
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

  private void drawTeardropHandle(Canvas canvas, float cx, float cy, Paint paint) {
    Paint.Style prevStyle = paint.getStyle();
    float prevStroke = paint.getStrokeWidth();
    Paint.Cap prevCap = paint.getStrokeCap();

    paint.setStyle(Paint.Style.FILL);
    teardropPath.reset();
    teardropPath.addOval(
        cx - handlesManager.getHandleRadius(), cy, cx + handlesManager.getHandleRadius(), cy + handlesManager.getHandleRadius() * 2, Path.Direction.CW);
    canvas.drawPath(teardropPath, paint);

    paint.setStyle(prevStyle);
    paint.setStrokeWidth(prevStroke);
    paint.setStrokeCap(prevCap);
  }

  private void drawPopup(Canvas canvas) {
    popupMenuManager.drawPopup(canvas);
  }

  private boolean shouldHideCopyCutForSelection() {
    if (!selectionManager.hasSelection) return true;

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

  private RectF getPopupRectForAction(int action) {
    return popupMenuManager.getPopupRectForAction(action);
  }

  private String getPopupLabelForAction(int action) {
    return popupMenuManager.getPopupLabelForAction(action);
  }

  private int getPopupActionAt(float x, float y) {
    return popupMenuManager.getPopupActionAt(x, y);
  }

  private void drawButton(
      Canvas canvas, RectF r, String label, Paint txtPaint, float maxTextWidth) {
    String drawLabel = label;
    if (maxTextWidth > 0f) {
      TextPaint ellipsizePaint =
          (txtPaint instanceof TextPaint) ? (TextPaint) txtPaint : new TextPaint(txtPaint);
      drawLabel =
          TextUtils.ellipsize(label, ellipsizePaint, maxTextWidth, TextUtils.TruncateAt.END)
              .toString();
    }
    float textWidth = txtPaint.measureText(drawLabel);
    float cx = r.centerX();
    float cy = r.centerY() - ((txtPaint.descent() + txtPaint.ascent()) / 2f);
    canvas.drawText(drawLabel, cx - textWidth / 2f, cy, txtPaint);
  }

  private void startPopupRipple(int action, float x, float y) {
    popupMenuManager.startPopupRipple(action, x, y);
  }

  private void startPopupRippleHold(int action, float x, float y) {
    popupMenuManager.startPopupRippleHold(action, x, y);
  }

  private void cancelPopupRipple() {
    popupMenuManager.cancelPopupRipple();
  }

  private void showMinimalPopupAtCursor() {
    if (selectionManager.hasSelection) return;
    popupMenuManager.showMinimalPopupAtCursor();
  }

  void showPopupAtSelection() {
    if (!selectionManager.hasSelection) return;
    popupMenuManager.showPopupAtSelection();
  }

  void hidePopup() {
    popupMenuManager.hidePopup();
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

  private void clampScrollY() {
    scrollManager.clampScrollY();
  }

  void clampScrollYForZoom() {
    clampScrollY();
  }

  float getScrollXForZoom() {
    return scrollManager.scrollX;
  }

  float getScrollYForZoom() {
    return scrollManager.scrollY;
  }

  void setScrollXForZoom(float x) {
    scrollManager.scrollX = x;
  }

  void setScrollYForZoom(float y) {
    scrollManager.scrollY = y;
  }

  void abortScrollAnimationForZoom() {
    if (!scrollManager.scroller.isFinished()) {
      scrollManager.scroller.abortAnimation();
    }
  }

  void checkAndLoadWindow() {
    if (sourceFile == null || isFileCleared) return;
    if (getWidth() == 0 || getHeight() == 0) return;
    if (isWindowLoading) return;

    int firstVisibleIndex = (int) (scrollManager.scrollY / lineHeight);
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
                  invalidateLineNumberCache();
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
                  if (isWordWrapEnabled) {
                    if (shouldSuppressWrapMetricsForFastSelectAll()) {
                      wordWrapManager.wrapMetricsReady = false;
                    } else {
                      if (!wordWrapManager.wrapMetricsReady || wordWrapManager.wrapLineCounts == null || wordWrapManager.wrapLinePrefix == null) {
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
              clampScrollX();
              invalidate();
            }
          }
        });
  }

  private void buildFileIndex() {
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
                post(SodiumEditorView.this::requestLayout);
                if (isWordWrapEnabled) post(SodiumEditorView.this::scheduleWrapMetricsBuild);
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

  private void invalidatePendingIO() {
    ioTaskVersion.incrementAndGet();
    ioHandler.removeCallbacksAndMessages(null);
    clearHighlightCaches();
    if (isWordWrapEnabled) invalidateWrapMetrics();
    if (isCodeFoldingEnabled) {
      foldRanges.clear();
      foldIntervalsDirty = true;
    }
  }

  private void invalidatePendingIOForEdit() {
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
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    // Force clear wrap metrics as content is being cleared
    wordWrapManager.wrapMetricsReady = false;
    wordWrapManager.wrapLineCounts = null;
    wordWrapManager.wrapLinePrefix = null;
    wordWrapManager.totalWrapVisualLines = 0;
    wordWrapManager.wrapPrefixValidUpToLine = -1;

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
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;

    cursorManager.cursorLine = 0;
    cursorManager.cursorChar = 0;
    isEof = true;
    scrollManager.scrollY = 0;
    scrollManager.scrollX = 0;

    recalculateMaxLineWidth();
    requestLayout();
    invalidate();
  }

  public void loadFromFile(final File file) {
    invalidatePendingIOForEdit();
    isFileCleared = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    invalidateLineNumberCache();

    // Force clear wrap metrics for new file
    wordWrapManager.wrapMetricsReady = false;
    wordWrapManager.wrapLineCounts = null;
    wordWrapManager.wrapLinePrefix = null;
    wordWrapManager.totalWrapVisualLines = 0;
    wordWrapManager.wrapPrefixValidUpToLine = -1;

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
    scrollManager.maxLineWidthForScroll = 0f;
    scrollManager.maxTextStartXForScroll = 0f;
    scrollManager.maxScrollXForScroll = 0f;
    synchronized (lineOffsetsLock) {
      lineOffsets = new long[0];
    }
    isIndexReady = false;
    isIndexDisabled = false;
    indexDisabledPath = null;
    indexDisabledFileLength = -1L;

    cursorManager.cursorLine = 0;
    cursorManager.cursorChar = 0;
    isEof = false;
    scrollManager.scrollY = 0;
    scrollManager.scrollX = 0;
    undoRedo.resetLineCountDelta();

    loadWindowAround(0, () -> finishInitialFileOpenWarmup(token), false);
    ioHandler.post(this::buildFileIndex);
    requestLayout();
    invalidate();
  }

  public void updateSourceFile(File file) {
    sourceFile = file;
  }

  public int getEditVersionValue() {
    return undoRedo.getEditVersion();
  }

  public void refreshLineNumberCache() {
    invalidateLineNumberCache();
    requestLayout();
    invalidate();
  }

  public void setTextColor(int color) {
    paint.setColor(color);
    invalidate();
  }

  public void setZoomEnabled(boolean enabled) {
    zoomManager.setZoomEnabled(enabled);
  }

  // When enabled (default), pinch-zoom with word wrap avoids reflow during the gesture and
  // rebuilds wrapping once the user releases their fingers.
  public void setDeferWordWrapReflowDuringZoom(boolean enabled) {
    zoomManager.setDeferWordWrapReflowDuringZoom(enabled);
  }

  public void setZoomTextSizeRange(float minSp, float maxSp) {
    zoomManager.setZoomTextSizeRange(minSp, maxSp);
  }

  public void setZoomStepClamp(float maxStep) {
    zoomManager.setZoomStepClamp(maxStep);
  }

  public void setZoomFocusSmoothing(float alpha) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setZoomLockToInitialFocus(boolean enabled) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setZoomScaleSmoothing(float alpha) {
    // No-op: non-wrap zoom has been removed.
  }

  public void setHideDecorationsWhileZooming(boolean enabled) {
    zoomManager.setHideDecorationsWhileZooming(enabled);
    invalidate();
  }

  public void setReadOnly(boolean readOnly) {
    if (this.isReadOnly == readOnly) return;
    this.isReadOnly = readOnly;
    if (readOnly) {
      clearActiveSuggestion();
      selectionManager.hasSelection = false;
      selectionManager.isSelectAllActive = false;
      selectionManager.isEntireFileSelected = false;
      hidePopup();
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

  private boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike) {
    int span = Math.abs(eL - sL) + 1;
    return isSelectAllLike || span >= LARGE_EDIT_LINES;
  }

  private void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
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

  private void endLargeEditUi(boolean invalidate) {
    // Advance token so any pending watchdog is ignored, then hide.
    largeEditUiToken.incrementAndGet();
    mainHandler.removeCallbacks(largeEditUiWatchdog);
    setDisable(false);
    showLoadingCircle(false);
    if (invalidate) invalidate();
  }

  private static final int LARGE_PASTE_LINES = 1500;
  private static final int LARGE_PASTE_CHARS = 200_000;

  private static boolean isLargePasteText(String text) {
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

    if (selectionManager.hasSelection) {
      selectionManager.hasSelection = false;
      selectionManager.isSelectAllActive = false;
      selectionManager.isEntireFileSelected = false;
      selectionManager.selecting = false;
      hidePopup();
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

          cursorManager.cursorLine = targetLine;

          if (cursorManager.cursorLine >= windowStartLine
              && cursorManager.cursorLine < windowStartLine + linesWindow.size()) {
            String lineText = getLineTextForRender(cursorManager.cursorLine);
            cursorManager.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (isEof) {
            int lastLineInDoc = windowStartLine + linesWindow.size() - 1;
            if (cursorManager.cursorLine > lastLineInDoc) cursorManager.cursorLine = Math.max(0, lastLineInDoc);
            String lineText = getLineTextForRender(cursorManager.cursorLine);
            cursorManager.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else {
            cursorManager.cursorChar = 0;
          }

          scrollManager.keepCursorVisibleHorizontally();
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
    undoRedo.incrementEditVersion();

    // FIX: لو فيه تحديد، لازم يكون استبدال ذري (خصوصاً خارج الشاشة)
    if (selectionManager.hasSelection) {
      replaceSelectionWithText(String.valueOf(c));
      return;
    }

    if (hasComposing) {
      hasComposing = false;
      composingLength = 0;
    }

    final int beforeLine = cursorManager.cursorLine;
    final int beforeChar = cursorManager.cursorChar;

    scrollManager.ensureLineInWindow(cursorManager.cursorLine, true);
    if (isWindowLoading
        && (cursorManager.cursorLine < windowStartLine || cursorManager.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = cursorManager.cursorLine - windowStartLine;
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
        String before = base.substring(0, Math.min(cursorManager.cursorChar, base.length()));
        String after = base.substring(Math.min(cursorManager.cursorChar, base.length()));
        Float oldWidth = lineWidthCache.get(cursorManager.cursorLine);

        updateLocalLine(localIdx, before);
        linesWindow.add(localIdx + 1, after);

        modifiedLines.put(cursorManager.cursorLine, before);
        modifiedLines.put(cursorManager.cursorLine + 1, after);

        computeWidthForLine(cursorManager.cursorLine, before);
        computeWidthForLine(cursorManager.cursorLine + 1, after);

        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        clearHighlightCaches();
        cursorManager.cursorLine++;
        cursorManager.cursorChar = 0;
        undoRedo.addLineCountDelta(1);

        int newLineCount = getLinesCount();
        if (showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        onLineCountChanged();
      } else {
        int pos = Math.max(0, Math.min(cursorManager.cursorChar, base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.cursorLine, modified);
        invalidateHighlightCacheForLine(cursorManager.cursorLine);
        cursorManager.cursorChar++;
        float newWidth = measureTextWithVisualSpaces(modified, 0, modified.length(), paint);
        synchronized (lineWidthCache) {
          lineWidthCache.put(cursorManager.cursorLine, newWidth);
        }
        currentMaxWindowLineWidth = Math.max(currentMaxWindowLineWidth, newWidth);
        globalMaxLineWidth = Math.max(globalMaxLineWidth, currentMaxWindowLineWidth);
      }
      invalidate();
      scrollManager.keepCursorVisibleHorizontally();
    }
    updateSuggestion();

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
    op.cursorLineAfter = cursorManager.cursorLine;
    op.cursorCharAfter = cursorManager.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (isReadOnly) return;
    if (selectionManager.hasSelection) {
      replaceSelectionWithText("\n");
      return;
    }

    BracketPairType pairType = getCursorBracketPairType();
    if (isAutoBracketNewlineEnabled && pairType != BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (isAutoBracketNewlineIndentEnabled) {
        baseIndent = getLineLeadingWhitespace(cursorManager.cursorLine);
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = cursorManager.cursorLine + 1;
      int targetChar = innerIndent.length();
      insertTextAtCursor(insertText);

      cursorManager.cursorLine = targetLine;
      cursorManager.cursorChar = targetChar;
      resetCursorBlink();
      scrollManager.keepCursorVisibleHorizontally();
      invalidate();
      updateSuggestion();
      return;
    }

    if (isAutoIndentAfterClosingBracketEnabled) {
      String ln = getLineTextForRender(cursorManager.cursorLine);
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursorManager.cursorChar, ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = getLineLeadingWhitespace(cursorManager.cursorLine);
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
      String ln = getLineTextForRender(cursorManager.cursorLine);
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(cursorManager.cursorChar, ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = getLineLeadingWhitespace(cursorManager.cursorLine);
      String extraIndent = trimmed.endsWith(":") ? INDENT_BLOCK_UNIT : "";
      insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (isAutoBracketNewlineIndentEnabled) {
      String baseIndent = getLineLeadingWhitespace(cursorManager.cursorLine);
      insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }

  private BracketPairType getCursorBracketPairType() {
    String ln = getLineTextForRender(cursorManager.cursorLine);
    if (ln == null) return BracketPairType.NONE;
    if (cursorManager.cursorChar <= 0 || cursorManager.cursorChar >= ln.length()) return BracketPairType.NONE;

    char left = ln.charAt(cursorManager.cursorChar - 1);
    char right = ln.charAt(cursorManager.cursorChar);
    if (left == '{' && right == '}') return BracketPairType.CURLY;
    if (left == '(' && right == ')') return BracketPairType.ROUND;
    if (left == '[' && right == ']') return BracketPairType.SQUARE;
    return BracketPairType.NONE;
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

  private int getIndentWidth(String line) {
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

  private enum BracketPairType {
    NONE,
    CURLY,
    ROUND,
    SQUARE
  }

  public void deleteCharAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();
    clearActiveSuggestion(); // Clear suggestion on delete

    if (hasComposing) {
      deleteComposing();
      return;
    }

    final int beforeLine = cursorManager.cursorLine;
    final int beforeChar = cursorManager.cursorChar;

    scrollManager.ensureLineInWindow(cursorManager.cursorLine, true);
    if (isWindowLoading
        && (cursorManager.cursorLine < windowStartLine || cursorManager.cursorLine >= windowStartLine + linesWindow.size())) {
      post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = cursorManager.cursorLine - windowStartLine;
    if (localIdx < 0 || localIdx >= linesWindow.size()) return;

    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursorManager.cursorChar > 0) {
        Float oldWidth = lineWidthCache.get(cursorManager.cursorLine);
        int safeStart = Math.max(0, cursorManager.cursorChar - 1);
        String removed = base.substring(safeStart, Math.min(cursorManager.cursorChar, base.length()));
        boolean atLineEnd = cursorManager.cursorChar >= base.length();
        if (charAnimationManager.isEnabled() && atLineEnd) {
          Paint p = getPaintForChar(cursorManager.cursorLine, safeStart, base);
          startDeleteAnimation(cursorManager.cursorLine, safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(cursorManager.cursorChar);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.cursorLine, modified);
        invalidateHighlightCacheForLine(cursorManager.cursorLine);
        cursorManager.cursorChar = safeStart;
        computeWidthForLine(cursorManager.cursorLine, modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursorManager.cursorLine);

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
        op.cursorLineAfter = cursorManager.cursorLine;
        op.cursorCharAfter = cursorManager.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else if (cursorManager.cursorLine > 0) {
        int oldLineCount = getLinesCount();
        int prevGlobal = cursorManager.cursorLine - 1;
        scrollManager.ensureLineInWindow(prevGlobal, true);
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
        cursorManager.cursorLine = prevGlobal;
        cursorManager.cursorChar = prev.length();
        computeWidthForLine(prevGlobal, merged);
        undoRedo.addLineCountDelta(-1);

        int newLineCount = getLinesCount();
        if (showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          requestLayout();
        }
        onLineCountChanged();
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
        op.cursorLineAfter = cursorManager.cursorLine;
        op.cursorCharAfter = cursorManager.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
    }
    updateSuggestion(); // Update suggestion after deletion
  }

  public void deleteForwardAtCursor() {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();
    clearActiveSuggestion(); // Clear suggestion on delete forward

    if (hasComposing) {
      deleteComposing();
      return;
    }

    final int beforeLine = cursorManager.cursorLine;
    final int beforeChar = cursorManager.cursorChar;

    scrollManager.ensureLineInWindow(cursorManager.cursorLine, true);
    if (isWindowLoading
        && (cursorManager.cursorLine < windowStartLine || cursorManager.cursorLine >= windowStartLine + linesWindow.size())) {
      post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = cursorManager.cursorLine - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (cursorManager.cursorChar < base.length()) {
        Float oldWidth = lineWidthCache.get(cursorManager.cursorLine);
        String removed = base.substring(cursorManager.cursorChar, Math.min(cursorManager.cursorChar + 1, base.length()));
        boolean atLineEnd = cursorManager.cursorChar == base.length() - 1;
        if (charAnimationManager.isEnabled() && atLineEnd) {
          Paint p = getPaintForChar(cursorManager.cursorLine, cursorManager.cursorChar, base);
          startDeleteAnimation(cursorManager.cursorLine, cursorManager.cursorChar, removed, p);
        }
        String modified = base.substring(0, cursorManager.cursorChar) + base.substring(cursorManager.cursorChar + 1);
        updateLocalLine(localIdx, modified);
        modifiedLines.put(cursorManager.cursorLine, modified);
        computeWidthForLine(cursorManager.cursorLine, modified);
        if (oldWidth != null && oldWidth >= currentMaxWindowLineWidth)
          recalculateMaxLineWidthAsync();
        invalidateLineGlobal(cursorManager.cursorLine);

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
        op.cursorLineAfter = cursorManager.cursorLine;
        op.cursorCharAfter = cursorManager.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else {
        int nextGlobal = cursorManager.cursorLine + 1;
        if (isEof && nextGlobal >= windowStartLine + linesWindow.size()) return;

        scrollManager.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - windowStartLine;
        if (nextLocal >= 0 && nextLocal < linesWindow.size()) {
          String next = getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          updateLocalLine(localIdx, merged);
          linesWindow.remove(nextLocal);
          modifiedLines.put(cursorManager.cursorLine, merged);
          recalculateMaxLineWidth();
          computeWidthForLine(cursorManager.cursorLine, merged);
          onLineCountChanged();
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
          op.cursorLineAfter = cursorManager.cursorLine;
          op.cursorCharAfter = cursorManager.cursorChar;
          op.timestamp = System.currentTimeMillis();
          recordEdit(op);
        }
      }
    }
    updateSuggestion(); // Update suggestion after delete forward
  }

  void commitComposing(boolean keepInText) {
    if (!hasComposing) return;
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    undoRedo.clearComposingPendingOp();
    charAnimationManager.clearLastComposingTextForCharAnim();
    invalidate();
    updateSuggestion();
  }

  void replaceComposingWith(CharSequence textSeq) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    undoRedo.incrementEditVersion();

    scrollManager.ensureLineInWindow(composingLine, true);
    if (isWindowLoading
        && (composingLine < windowStartLine
            || composingLine >= windowStartLine + linesWindow.size())) {
      post(() -> replaceComposingWith(textSeq));
      return;
    }
    int local = composingLine - windowStartLine;
    synchronized (linesWindow) {
      String base = getLineFromWindowLocal(local);
      if (base == null) base = "";
      int start = Math.max(0, Math.min(composingOffset, base.length()));
      int end = Math.max(0, Math.min(composingOffset + composingLength, base.length()));
      if (charAnimationManager.isEnabled()) {
        String oldComposing = base.substring(start, end);
        String newComposing = (textSeq == null) ? "" : textSeq.toString();
        if (newComposing.length() < oldComposing.length()) {
          String removed = null;
          int at = start;
          if (oldComposing.startsWith(newComposing)) {
            removed = oldComposing.substring(newComposing.length());
            at = start + newComposing.length();
          } else if (oldComposing.endsWith(newComposing)) {
            removed = oldComposing.substring(0, oldComposing.length() - newComposing.length());
            at = start;
          }

          if (removed != null && !removed.isEmpty()) {
            Paint p = getPaintForChar(composingLine, at, base);
            startDeleteAnimation(composingLine, at, removed, p);
          }
        }
      }
      String newLine = base.substring(0, start) + textSeq + base.substring(end);
      updateLocalLine(local, newLine);
      modifiedLines.put(composingLine, newLine);
      composingLength = textSeq.length();
      cursorManager.cursorLine = composingLine;
      cursorManager.cursorChar = composingOffset + composingLength;
      computeWidthForLine(composingLine, newLine);
      recalculateMaxLineWidth();
      invalidate();
    }
    updateSuggestion();
  }

  private void deleteComposing() {
    if (!hasComposing) return;
    replaceComposingWith("");
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    charAnimationManager.clearLastComposingTextForCharAnim();
  }

  int comparePos(int lineA, int charA, int lineB, int charB) {
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
    if (!selectionManager.hasSelection) return null;
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
    if (!selectionManager.hasSelection) return;
    clearActiveSuggestion(); // Clear suggestion when copying/cutting

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

  private String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
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

  private void countTotalLines(LineCountCallback callback) {
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

  public void selectAll() {
    clearActiveSuggestion(); // Clear suggestion when selectionManager.selecting all
    final boolean keyboardWasVisible = keyboardHeight > 0;
    if (isWordWrapEnabled) {
      // Free the IO thread from wrap rebuilds so select-all can jump to end quickly.
      int widthPx = Math.max(1, Math.round(getWrapWidth()));
      if (isWrapMetricsUsableForWindow(widthPx)) {
        cancelWrapWorkForPriority();
      }
    }
    setDisable(true);
    showLoadingCircle(true);

    selectionManager.isSelectAllActive = true;
    selectionManager.isEntireFileSelected = true;
    selectionManager.hasSelection = true;

    selectionManager.selStartLine = 0;
    selectionManager.selStartChar = 0;
    hidePopup();

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
      cursorManager.cursorLine = selectionManager.selEndLine;
      cursorManager.cursorChar = selectionManager.selEndChar;

      scrollManager.scrollToLineFastForSelectAll(selectionManager.selEndLine, selectionManager.selEndChar);

      setDisable(false);
      showLoadingCircle(false);
      invalidate();
      requestFocus();
      showPopupAtSelection();

      post(
          () -> {
            requestFocus();
            if (keyboardWasVisible) showKeyboard();
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
      cursorManager.cursorLine = windowLast;
      cursorManager.cursorChar = selectionManager.selEndChar;

      scrollManager.scrollToLineFastForSelectAll(windowLast, selectionManager.selEndChar);

      setDisable(false);
      showLoadingCircle(false);
      invalidate();
      requestFocus();
      showPopupAtSelection();

      post(
          () -> {
            requestFocus();
            if (keyboardWasVisible) showKeyboard();
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
              cursorManager.cursorLine = windowLast;
              cursorManager.cursorChar = selectionManager.selEndChar;

              scrollManager.scrollToLineFastForSelectAll(windowLast, selectionManager.selEndChar);

              setDisable(false);
              showLoadingCircle(false);
              invalidate();
              requestFocus();
              showPopupAtSelection();

              post(
                  () -> {
                    requestFocus();
                    if (keyboardWasVisible) showKeyboard();
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
                        cursorManager.cursorLine = fileLastLine;
                        cursorManager.cursorChar = selectionManager.selEndChar;

                        scrollManager.scrollToLineFastForSelectAll(fileLastLine, selectionManager.selEndChar);

                        setDisable(false);
                        showLoadingCircle(false);
                        invalidate();
                        requestFocus();
                        showPopupAtSelection();

                        post(
                            () -> {
                              requestFocus();
                              if (keyboardWasVisible) showKeyboard();
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
                              cursorManager.cursorLine = selectionManager.selEndLine;
                              cursorManager.cursorChar = selectionManager.selEndChar;

                              scrollManager.scrollToLineFastForSelectAll(selectionManager.selEndLine, selectionManager.selEndChar);

                              setDisable(false);
                              showLoadingCircle(false);
                              invalidate();
                              requestFocus();
                              showPopupAtSelection();

                              post(
                                  () -> {
                                    requestFocus();
                                    if (keyboardWasVisible) showKeyboard();
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
                  if (sourceFile == null) {
                    setDisable(false);
                    showLoadingCircle(false);
                    invalidate();
                    showPopupAtSelection();
                    if (keyboardWasVisible) showKeyboard();
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
    clearActiveSuggestion(); // Clear suggestion when deleting selection
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

  private CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
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

  private int countNewlines(@Nullable String text) {
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

  private void recordEdit(UndoRedo.EditOp op) {
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
    setCursorPosition(cursorLine, cursorChar);
    if (isWordWrapEnabled) {
      invalidateWrapMetrics(true);
      requestWrapPrefixRebuild();
    }
    invalidateLineNumberCache();
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
    selectionManager.selStartLine = startL;
    selectionManager.selStartChar = Math.max(0, startC);
    selectionManager.selEndLine = endL;
    selectionManager.selEndChar = Math.max(0, endC);
    selectionManager.hasSelection = !(selectionManager.selStartLine == selectionManager.selEndLine && selectionManager.selStartChar == selectionManager.selEndChar);
    selectionManager.selecting = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
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

  void replaceSelectionWithText(String insertText) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    final int opToken = undoRedo.incrementEditVersion();
    clearActiveSuggestion(); // Clear suggestion when replacing selection

    if (insertText == null) insertText = "";

    if (!selectionManager.hasSelection) {
      if (!insertText.isEmpty()) insertTextAtCursor(insertText);
      // No selection means no large edit UI was started for it.
      updateSuggestion();
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
    final int beforeLine = cursorManager.cursorLine;
    final int beforeChar = cursorManager.cursorChar;
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

    final boolean selectAllLike = selectionManager.isSelectAllActive || selectionManager.isEntireFileSelected;
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
      cursorManager.cursorLine = 0;
      cursorManager.cursorChar = 0;
      selectionManager.selStartLine = 0;
      selectionManager.selEndLine = 0;
      selectionManager.selStartChar = 0;
      selectionManager.selEndChar = 0;
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
        cursorManager.cursorLine = newPos.line;
        cursorManager.cursorChar = newPos.ch;
      }

      // Crucially, end the large edit UI and force a redraw.
      onLineCountChanged();
      endLargeEditUi(true);
      recalculateMaxLineWidth();
      scrollManager.keepCursorVisibleHorizontally();
      requestLayout(); // Request layout to update gutter width after content cleared
      updateSuggestion();
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

          cursorManager.cursorLine = sL;
          cursorManager.cursorChar = a + insertText.length();

          computeWidthForLine(sL, merged);
          recalculateMaxLineWidth();
        }
      }

      clearSelectionStateAfterDelete();
      invalidate();
      scrollManager.keepCursorVisibleHorizontally();
      endLargeEditUi(false);
      updateSuggestion();
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
      cursorManager.cursorLine = sL;
      cursorManager.cursorChar = sC;
    }

    clearSelectionStateAfterDelete();
    scrollManager.keepCursorVisibleHorizontally(); // This scrolls to the new cursor and invalidates.
    endLargeEditUi(false);

    if (sourceFile == null || isFileCleared) {
      if (!fullyInWindow) {
        scrollManager.ensureLineInWindow(sL, true);
        scrollManager.ensureLineInWindow(eL, true);
        applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      updateSuggestion();
      undoRedo.addLineCountDelta((insertedNewlines - removedNewlines));
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = sourceFile;
    // ابدأ إعادة كتابة الملف في الخلفية بدون تعطيل الواجهة وبدون دائرة تحميل.
    rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    updateSuggestion();
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

      cursorManager.cursorLine = Math.max(0, target.line);
      cursorManager.cursorChar = Math.max(0, target.ch);

      int newLineCount = getLinesCount();
      if (showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      onLineCountChanged();

      recalculateMaxLineWidth();
    }
  }

  private void rewriteReplaceRangeAsync(
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
                  scrollManager.maxLineWidthForScroll = 0f;
                  scrollManager.maxTextStartXForScroll = 0f;
                  scrollManager.maxScrollXForScroll = 0f;
                  undoRedo.resetLineCountDelta();

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

                  cursorManager.cursorLine = Math.max(0, target.line);
                  cursorManager.cursorChar = Math.max(0, target.ch);

                  // لا تعمل "Reload" للنافذة بعد الحذف/الاستبدال إذا كانت النتيجة ضمن النافذة
                  // الحالية.
                  // هذا يمنع دائرة التحميل ويمنع القفز/الزمن الطويل مع الملفات الضخمة.
                  boolean cursorInsideWindow =
                      (cursorManager.cursorLine >= windowStartLine
                          && cursorManager.cursorLine < windowStartLine + linesWindow.size());

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
                    int targetStart = Math.max(0, cursorManager.cursorLine - prefetchLines);
                    loadWindowAround(
                        targetStart,
                        () -> {
                          String ln = getLineTextForRender(cursorManager.cursorLine);
                          cursorManager.cursorChar = Math.min(cursorManager.cursorChar, ln.length());
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
  }

  private RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
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

      cursorManager.cursorLine = sL;
      cursorManager.cursorChar = left.length();

      recalculateMaxLineWidth();
      int newLineCount = getLinesCount();
      if (oldLineCount != newLineCount) {
        onLineCountChanged();
      }
    }
  }

  private void clearSelectionStateAfterDelete() {
    selectionManager.hasSelection = false;
    selectionManager.selecting = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
    resetCursorBlink();
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

  private long getLineByteLengthFromIndex(RandomAccessFile raf, int line, long fileLen)
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
    return new String(buf, fileCharset);
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

  private long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    if (lineText == null) return 0L;
    int safe = Math.max(0, Math.min(charIndex, lineText.length()));
    if (safe == 0) return 0L;
    return lineText.substring(0, safe).getBytes(fileCharset).length;
  }

  private int getCharIndexForX(String text, float x, int globalLine) {
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
    selectionManager.selStartLine = selectionManager.selEndLine = line;
    selectionManager.selStartChar = pick.start;
    selectionManager.selEndChar = pick.end;
    selectionManager.hasSelection = true;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    selectionManager.selecting = true;
    cursorManager.cursorLine = line;
    cursorManager.cursorChar = selectionManager.selEndChar;
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
    if (!scrollManager.stretchOverscrollEnabled || isWordWrapEnabled) return;
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
    if (!scrollManager.stretchOverscrollEnabled || isWordWrapEnabled) return;
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
    if (!selectionManager.hasSelection) return false;
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

  private static final class TextRange {
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
    if (!selectionManager.hasSelection || candidates == null || candidates.isEmpty()) return -1;
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

  void insertTextAtCursor(String text) {
    if (isReadOnly) return;
    invalidatePendingIOForEdit();
    final int opToken = undoRedo.incrementEditVersion();

    if (text == null) return;
    if (text.isEmpty() && !selectionManager.hasSelection) return;

    // FIX: لو فيه تحديد، لازم يكون replace ذري
    if (selectionManager.hasSelection) {
      replaceSelectionWithText(text);
      return;
    }

    if (hasComposing) {
      hasComposing = false;
      composingLength = 0;
    }

    if (text.isEmpty()) {
      invalidate();
      return;
    }

    final int beforeLine = cursorManager.cursorLine;
    final int beforeChar = cursorManager.cursorChar;

    // For very large pastes into a file-backed document, avoid expanding the in-memory window and
    // doing
    // expensive per-line work on the UI thread. Instead, apply the insert via the file rewrite
    // path.
    if (sourceFile != null && !isFileCleared && isLargePasteText(text)) {
      beginLargeEditUiIfNeeded(true, cursorManager.cursorLine, cursorManager.cursorLine, true);
      // Extend the watchdog for large paste operations; they can legitimately take longer than
      // the default safety timeout.
      mainHandler.removeCallbacks(largeEditUiWatchdog);
      mainHandler.postDelayed(largeEditUiWatchdog, 30_000);
      CursorTarget target = computeCursorAfterInsert(cursorManager.cursorLine, cursorManager.cursorChar, text);
      final File inFile = sourceFile;
      rewriteReplaceRangeAsync(
          opToken, inFile, cursorManager.cursorLine, cursorManager.cursorChar, cursorManager.cursorLine, cursorManager.cursorChar, text, target, true);
      updateSuggestion();
      undoRedo.addLineCountDelta(countNewlines(text));
      if (text.length() <= undoRedo.getUndoTextLimit()) {
        UndoRedo.EditOp op = new UndoRedo.EditOp();
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
    scrollManager.ensureLineInWindow(cursorManager.cursorLine, true);
    if (isWindowLoading
        && (cursorManager.cursorLine < windowStartLine || cursorManager.cursorLine >= windowStartLine + linesWindow.size())) {
      post(() -> insertTextAtCursor(text));
      return;
    }

    int local = cursorManager.cursorLine - windowStartLine;
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
      int pos = Math.max(0, Math.min(cursorManager.cursorChar, base.length()));
      String left = base.substring(0, pos);
      String right = base.substring(pos);

      if (parts.length == 1) {
        String modified = left + parts[0] + right;
        updateLocalLine(local, modified);
        modifiedLines.put(cursorManager.cursorLine, modified);
        lineWidthCache.remove(cursorManager.cursorLine);
        cursorManager.cursorChar += parts[0].length();
      } else {
        lineWidthCache.clear();
        String firstLine = left + parts[0];
        updateLocalLine(local, firstLine);
        modifiedLines.put(cursorManager.cursorLine, firstLine);

        List<String> linesToInsert = new ArrayList<>();
        for (int p = 1; p < parts.length - 1; p++) linesToInsert.add(parts[p]);

        String lastPart = parts[parts.length - 1];
        linesToInsert.add(lastPart + right);

        if (!linesToInsert.isEmpty()) linesWindow.addAll(local + 1, linesToInsert);
        for (int i = 0; i < linesToInsert.size(); i++) {
          modifiedLines.put(cursorManager.cursorLine + 1 + i, linesToInsert.get(i));
        }

        cursorManager.cursorLine += (parts.length - 1);
        cursorManager.cursorChar = lastPart.length();
        undoRedo.addLineCountDelta((parts.length - 1));
      }

      int newLineCount = getLinesCount();
      if (showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        requestLayout();
      }
      if (parts.length > 1) {
        onLineCountChanged();
      }

      recalculateMaxLineWidth();
      scrollManager.keepCursorVisibleHorizontally();
      resetCursorBlink();
      invalidate();
    }
    updateSuggestion();

    UndoRedo.EditOp op = new UndoRedo.EditOp();
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
    op.cursorLineAfter = cursorManager.cursorLine;
    op.cursorCharAfter = cursorManager.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  private BufferedReader reopenReaderAtStart() {
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


  private void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < linesWindow.size()) {
      linesWindow.set(localIdx, text);
      onLineContentChanged(windowStartLine + localIdx, text);
      clearStreamedLineInfo(windowStartLine + localIdx);
    }
  }

  String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= linesWindow.size()) return null;
    return linesWindow.get(localIdx);
  }

  private int getStreamLineThreshold() {
    return Math.max(4096, maxSyntaxLineLength);
  }

  private boolean shouldStreamLineLength(int length) {
    if (isWordWrapEnabled) return false;
    return length > getStreamLineThreshold();
  }

  private int getStreamedLineLength(int globalLine) {
    synchronized (streamedLinesLock) {
      return streamedLineLengths.get(globalLine, -1);
    }
  }

  private int getStreamedLineSliceStart(int globalLine) {
    synchronized (streamedLinesLock) {
      return streamedLineSliceStarts.get(globalLine, 0);
    }
  }

  private void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    synchronized (streamedLinesLock) {
      streamedLineLengths.put(globalLine, length);
      streamedLineSliceStarts.put(globalLine, sliceStart);
    }
  }

  private void clearStreamedLineInfo(int globalLine) {
    synchronized (streamedLinesLock) {
      streamedLineLengths.delete(globalLine);
      streamedLineSliceStarts.delete(globalLine);
    }
  }

  private void clearStreamedLineCaches() {
    synchronized (streamedLinesLock) {
      streamedLineLengths.clear();
      streamedLineSliceStarts.clear();
    }
    streamedSliceUpdatePending = false;
    streamedSliceUpdateToken++;
  }

  private boolean isSingleByteCharset() {
    try {
      if (binarySafeRenderingEnabled) return true;
      return fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
    } catch (Exception ignored) {
      return true;
    }
  }

  int getLogicalLineLength(int globalLine, @Nullable String line) {
    String mod = modifiedLines.get(globalLine);
    if (mod != null) return mod.length();
    int len = (line == null) ? 0 : line.length();
    int longLen = getStreamedLineLength(globalLine);
    return (longLen > len) ? longLen : len;
  }

  private void computeWidthForLine(int globalIndex, String line) {
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

  private float getWidthForLine(int globalIndex, String line) {
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

  private void showKeyboard() {
    imeManager.showKeyboard();
  }

  public void requestKeyboard() {
    showKeyboard();
  }

  public void setCharAnimation(boolean enabled, int durationMs) {
    charAnimationManager.setEnabled(enabled, durationMs);
  }

  void startCharAnimationFromText(CharSequence committedText) {
    charAnimationManager.startCharAnimationFromText(committedText);
  }

  private void startDeleteAnimation(
      int targetLine, int atChar, @Nullable String removedText, @Nullable Paint paintToUse) {
    charAnimationManager.startDeleteAnimation(targetLine, atChar, removedText, paintToUse);
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
      if (cursorManager.cursorChar >= 2) {
        String ln = getLineTextForRender(cursorManager.cursorLine);
        if (ln != null && ln.length() >= cursorManager.cursorChar && ln.charAt(cursorManager.cursorChar - 2) == '/') {
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
    return imeManager.onCheckIsTextEditor();
  }

  private void updateImeSelection() {
    imeManager.updateImeSelection();
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return imeManager.onCreateInputConnection(outAttrs);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return inputManager.handleTouchEvent(event);
  }

  boolean handleTouchEventFromInput(MotionEvent event) {
    if (isDisabled) return true;

    int action = event.getActionMasked();
    int pointerCount = event.getPointerCount();

    if (action == MotionEvent.ACTION_DOWN) {
      zoomManager.resetMultiTouchState();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      zoomManager.onPointerDown();
      pointerDown = false;
      movedSinceDown = false;
      draggingHandle = 0;
      scrollManager.dragMaxScrollX = -1f;
      selectionManager.selecting = false;
      selectionManager.isLineNumberSelecting = false;
      selectionManager.lineNumberSelectAnchorLine = -1;
      mainHandler.removeCallbacks(autoScrollRunnable);
      if (!scrollManager.scroller.isFinished()) {
        scrollManager.scroller.computeScrollOffset();
        scrollManager.scrollX = scrollManager.scroller.getCurrX();
        scrollManager.scrollY = scrollManager.scroller.getCurrY();
        scrollManager.scroller.abortAnimation();
      }
      cancelFlingStopAnimation();
    }

    if (action == MotionEvent.ACTION_POINTER_UP) {
      zoomManager.onPointerUp(pointerCount - 1);
      if (pointerCount - 1 <= 1) scrollManager.dragMaxScrollX = -1f;
    }

    zoomManager.onScaleTouchEvent(event);

    if (zoomManager.isScaleInProgress()
        || zoomManager.isMultiTouchActive()
        || pointerCount > 1
        || zoomManager.isScaling()
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      return true;
    }

    if (zoomManager.hadMultiTouch()
        && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
      pointerDown = false;
      draggingHandle = 0;
      selectionManager.selecting = false;
      selectionManager.isLineNumberSelecting = false;
      selectionManager.lineNumberSelectAnchorLine = -1;
      mainHandler.removeCallbacks(autoScrollRunnable);
      scrollManager.dragMaxScrollX = -1f;
      return true;
    }

    float ex = event.getX(), ey = event.getY();
    lastTouchX = ex;
    lastTouchY = ey;

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        resetCursorBlink();
        if (!isFocused()) requestFocus();
        pointerDown = true;
        downX = ex;
        downY = ey;
        movedSinceDown = false;
        suggestionAcceptedThisTouch = false; // Reset flag for new touch sequence
        scrollManager.dragMaxScrollX = isWordWrapEnabled ? -1f : getMaxScrollXForClamp();

        showScrollBar();
        if (scrollManager.scrollBarEnabled) {
          float maxScroll = getMaxScrollYForClamp();
          if (maxScroll > 0f && scrollManager.scrollBarThumbRect.contains(ex, ey)) {
            scrollManager.draggingScrollBar = true;
            scrollManager.scrollBarDragOffset = ey - scrollManager.scrollBarThumbRect.top;
            showScrollBar();
            return true;
          }
        }

        if (popupMenuManager.isPopupVisible()) {
          int hitAction = getPopupActionAt(ex, ey);
          if (hitAction != 0) {
            popupMenuManager.setPressedAction(hitAction);
            startPopupRipple(hitAction, ex, ey);
            return true;
          }
        }

        if (!scrollManager.scroller.isFinished()) {
          scrollManager.scroller.computeScrollOffset();
          float targetX = scrollManager.scroller.getCurrX();
          float targetY = scrollManager.scroller.getCurrY();
          scrollManager.scroller.abortAnimation();
          startFlingStopAnimation(targetX, targetY);
        } else {
          cancelFlingStopAnimation();
        }

        // FIX: Use getTextStartX() to correctly calculate touch coordinates relative to the text
        // area.
        float gx = ex + getEffectiveScrollX() - getTextStartX();
        float gy = ey + scrollManager.scrollY - scrollManager.getHitTestBaseY();
        if (selectionManager.hasSelection && handlesManager.getLeftHandleRect().contains(gx, gy)) {
          draggingHandle = 1;
          return true;
        } else if (selectionManager.hasSelection && handlesManager.getRightHandleRect().contains(gx, gy)) {
          draggingHandle = 2;
          return true;
        } else if (isFocused() && !selectionManager.hasSelection && handlesManager.getCursorHandleRect().contains(gx, gy)) {
          draggingHandle = 3;
          return true;
        }

        inputManager.onGestureEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (flingStopAnimator != null) cancelFlingStopAnimation();
        if (Math.abs(ex - downX) > touchSlop || Math.abs(ey - downY) > touchSlop)
          movedSinceDown = true;

        if (scrollManager.draggingScrollBar) {
          float maxScroll = getMaxScrollYForClamp();
          if (maxScroll > 0f) {
            float h = getHeight();
            float trackHeight = h;
            float contentHeight = maxScroll + h;
            float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
            if (thumbHeight < scrollManager.scrollBarMinThumbPx) thumbHeight = scrollManager.scrollBarMinThumbPx;
            if (thumbHeight > trackHeight) thumbHeight = trackHeight;
            float thumbRange = Math.max(1f, trackHeight - thumbHeight);
            float targetTop = Math.max(0f, Math.min(trackHeight - thumbHeight, ey - scrollManager.scrollBarDragOffset));
            scrollManager.scrollY = (targetTop / thumbRange) * maxScroll;
            clampScrollY();
            invalidate();
          }
          showScrollBar();
          return true;
        }

        if (popupMenuManager.getPressedAction() != 0) {
          int pressed = popupMenuManager.getPressedAction();
          RectF r = getPopupRectForAction(pressed);
          if (!r.contains(ex, ey)) {
            popupMenuManager.clearPressedAction();
            cancelPopupRipple();
          }
          return true;
        }

        if (selectionManager.isLineNumberSelecting) {
          float y = ey + scrollManager.scrollY;
          int line = getGlobalLineForY(y);
          updateLineNumberSelection(line);
          return true;
        }

        if (draggingHandle != 0) {
          updateHandlePosition(ex, ey);
          if (draggingHandle == 1 || draggingHandle == 2) showPopupAtSelection();

          float scrollMargin = lineHeight * 2f;
          float scrollSpeed = Math.max(4f, lineHeight * 0.35f);
          scrollManager.autoScrollY = 0;
          scrollManager.autoScrollX = 0;
          if (ey < scrollMargin) scrollManager.autoScrollY = -scrollSpeed;
          else if (ey > (getHeight() - keyboardHeight) - scrollMargin) scrollManager.autoScrollY = scrollSpeed;
          if (ex < scrollMargin) scrollManager.autoScrollX = -scrollSpeed;
          else if (ex > getWidth() - scrollMargin) scrollManager.autoScrollX = scrollSpeed;
          if (isRtl && !isWordWrapEnabled) scrollManager.autoScrollX = -scrollManager.autoScrollX;

          // Prevent horizontal auto-scroll when the handle is already at the line boundary.
          if (scrollManager.autoScrollX > 0 && lastDragAtLineEnd) scrollManager.autoScrollX = 0;
          if (scrollManager.autoScrollX < 0 && lastDragAtLineStart) scrollManager.autoScrollX = 0;

          if (scrollManager.autoScrollX != 0 || scrollManager.autoScrollY != 0) mainHandler.post(autoScrollRunnable);
          else mainHandler.removeCallbacks(autoScrollRunnable);

          invalidate();
          return true;
        }

        inputManager.onGestureEvent(event);
        return true;

      case MotionEvent.ACTION_UP:
        mainHandler.removeCallbacks(autoScrollRunnable);
        scrollManager.dragMaxScrollX = -1f;

        if (scrollManager.draggingScrollBar) {
          scrollManager.draggingScrollBar = false;
          showScrollBar();
          return true;
        }

        if (popupMenuManager.getPressedAction() != 0) {
          int actionForTap = popupMenuManager.getPressedAction();
          popupMenuManager.clearPressedAction();
          RectF r = getPopupRectForAction(actionForTap);
          if (popupMenuManager.isPopupVisible() && r.contains(ex, ey)) {
            if (isReadOnly
                && (actionForTap == PopupMenuManager.POPUP_ACTION_CUT
                    || actionForTap == PopupMenuManager.POPUP_ACTION_PASTE
                    || actionForTap == PopupMenuManager.POPUP_ACTION_DELETE)) {
              hidePopup();
              return true;
            }
            if (actionForTap == PopupMenuManager.POPUP_ACTION_COPY) {
              copySelectionToClipboard();
              selectionManager.hasSelection = false;
              selectionManager.isSelectAllActive = false;
              hidePopup();
              invalidate();
            } else if (actionForTap == PopupMenuManager.POPUP_ACTION_SELECT_ALL) {
              if (!selectionManager.isSelectAllActive) selectAll();
              else hidePopup();
            } else {
              popupMenuManager.performPopupAction(actionForTap);
            }
          } else {
            cancelPopupRipple();
          }
          if (popupMenuManager.isPopupRippleHoldActive()) {
            cancelPopupRipple();
          }
          return true;
        }

        if (selectionManager.isLineNumberSelecting) {
          selectionManager.isLineNumberSelecting = false;
          selectionManager.lineNumberSelectAnchorLine = -1;
          selectionManager.selecting = false;
          pointerDown = false;
          if (selectionManager.hasSelection) showPopupAtSelection();
          return true;
        }

        // --- Check for tap on suggestion FIRST and consume if it's a clean tap ---
        CursorTarget target = getCursorTargetForPosition(event.getX(), event.getY(), null);
        int line = target.line;

        // Get line text safely
        String ln = getLineFromWindowLocal(line - windowStartLine);
        if (ln == null) ln = getLineTextForRender(line);

        int charIndex = Math.max(0, Math.min(target.ch, ln.length()));

        // Check if the long press was on an "empty" area
        boolean isEmptyArea = false;
        if (ln.isEmpty()) {
          isEmptyArea = true;
        } else if (charIndex >= ln.length()) {
          isEmptyArea = true; // Tapped on empty space after the text on a line
        }

        boolean allowSuggestionTap =
            activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
        if (!movedSinceDown
            && allowSuggestionTap
            && activeSuggestion != null
            && !activeSuggestionRect.isEmpty()) {

          if (activeSuggestionRect.contains(ex, ey)) {
            Log.d(
                "SodiumEditorView",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            acceptAutoCompletion(); // Call synchronously
            pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          } else if (isEmptyArea && line == cursorManager.cursorLine) {
            Log.d(
                "SodiumEditorView",
                "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
            acceptAutoCompletion(); // Call synchronously
            pointerDown = false; // Reset pointerDown state
            Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Suggestion accepted, returning true.");
            return true; // Consume the event, preventing further processing
          }
        }
        // --- END Check ---

        pointerDown = false;
        // clearActiveSuggestion();

        if (draggingHandle != 0) {
          if (draggingHandle == 1 || draggingHandle == 2) showPopupAtSelection();
          draggingHandle = 0;
          invalidate();
          return true;
        }

        if (movedSinceDown && scrollManager.scroller.isFinished()) { // Just finished a scroll/drag
          if (selectionManager.hasSelection) showPopupAtSelection();
          restartInput(); // Sync IME state
          Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Scroll/Zoom ended, restarted input.");
          if (isWordWrapEnabled && wordWrapManager.wrapPrefixRebuildPending && !wordWrapManager.wrapPrefixBuilding) {
            wordWrapManager.wrapPrefixRebuildPending = false;
            scheduleWrapPrefixRebuildUpToWindow();
          }
        }

        Log.d("SodiumEditorView", "onTouchEvent.ACTION_UP: Passing to GestureDetector.ACTION_UP.");
        inputManager.onGestureEvent(event);
        if (selectionManager.hasSelection && !popupMenuManager.isPopupVisible()) {
          showPopupAtSelection();
        }
        return true;

      case MotionEvent.ACTION_CANCEL:
        mainHandler.removeCallbacks(autoScrollRunnable);
        pointerDown = false;
        draggingHandle = 0;
        selectionManager.selecting = false;
        selectionManager.isLineNumberSelecting = false;
        selectionManager.lineNumberSelectAnchorLine = -1;
        popupMenuManager.clearPressedAction();
        cancelPopupRipple();
        clearActiveSuggestion(); // Clear suggestion on touch cancel
        scrollManager.dragMaxScrollX = -1f;
        scrollManager.draggingScrollBar = false;
        if (scrollManager.scrollBarFadeEnabled) {
          mainHandler.removeCallbacks(scrollManager.scrollBarHideRunnable);
        }
        Log.d("SodiumEditorView", "onTouchEvent.ACTION_CANCEL: Passing to GestureDetector.");
        inputManager.onGestureEvent(event);
        return true;
    }

    return super.onTouchEvent(event);
  }

  private void updateHandlePosition(float touchX, float touchY) {
    // FIX: Any manual adjustment of the selection handles must deactivate ALL "Select All" flags.
    // This prevents the editor from deleting all content when the user has reduced the selection.
    if (selectionManager.isSelectAllActive || selectionManager.isEntireFileSelected) {
      selectionManager.isSelectAllActive = false;
      selectionManager.isEntireFileSelected = false;
      // The popup needs to be redrawn as "Copy" and "Cut" might become available again.
      showPopupAtSelection();
    }

    // Correctly calculate X coordinate relative to the text area, accounting for the gutter.
    CursorTarget target = getCursorTargetForPosition(touchX, touchY, null);
    int line = target.line;

    if (isEof) {
      int lastValidLine = windowStartLine + linesWindow.size() - 1;
      if (line > lastValidLine) line = lastValidLine;
    }

    scrollManager.ensureLineInWindow(line, true);
    String ln = getLineTextForRender(line);
    int clamped = Math.max(0, Math.min(target.ch, ln.length()));
    lastDragAtLineStart = clamped == 0;
    lastDragAtLineEnd = clamped == ln.length();

    if (draggingHandle == 1) {
      if (isRtl) {
        selectionManager.selEndLine = line;
        selectionManager.selEndChar = clamped;
      } else {
        selectionManager.selStartLine = line;
        selectionManager.selStartChar = clamped;
      }
    } else if (draggingHandle == 2) {
      if (isRtl) {
        selectionManager.selStartLine = line;
        selectionManager.selStartChar = clamped;
      } else {
        selectionManager.selEndLine = line;
        selectionManager.selEndChar = clamped;
      }
    } else if (draggingHandle == 3) {
      cursorManager.cursorLine = line;
      cursorManager.cursorChar = clamped;
      scrollManager.keepCursorVisibleHorizontally();
    }
  }

  private void drawSelectionSegment(
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
    if (right <= left || bottom <= top) return;

    float radius = Math.min(12f, Math.max(2f, lineHeight * 0.22f));
    // Keep vertical edges flush between lines to avoid "seam" lines when selectionManager.selecting multiple lines.
    float insetX = 0.5f;
    selectionRectTmp.set(left + insetX, top, right - insetX, bottom);

    if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
      canvas.drawRect(selectionRectTmp, paint);
      return;
    }

    float tl = roundTopLeft ? radius : 0f;
    float tr = roundTopRight ? radius : 0f;
    float br = roundBottomRight ? radius : 0f;
    float bl = roundBottomLeft ? radius : 0f;

    selectionRadiiTmp[0] = tl;
    selectionRadiiTmp[1] = tl;
    selectionRadiiTmp[2] = tr;
    selectionRadiiTmp[3] = tr;
    selectionRadiiTmp[4] = br;
    selectionRadiiTmp[5] = br;
    selectionRadiiTmp[6] = bl;
    selectionRadiiTmp[7] = bl;

    selectionPathTmp.reset();
    selectionPathTmp.addRoundRect(selectionRectTmp, selectionRadiiTmp, Path.Direction.CW);
    canvas.drawPath(selectionPathTmp, paint);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return inputManager.handleKeyDown(keyCode, event);
  }

  boolean handleKeyDownFromInput(int keyCode, KeyEvent event) {
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

    if (selectionManager.hasSelection && event.isPrintingKey()) {
      int uc = event.getUnicodeChar();
      if (uc != 0) {
        String s = String.valueOf((char) uc);
        replaceSelectionWithText(s);
        startCharAnimationFromText(s);
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
        if (selectionManager.hasSelection) replaceSelectionWithText("");
        else deleteCharAtCursor();
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (selectionManager.hasSelection) replaceSelectionWithText("");
        else deleteForwardAtCursor();
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (selectionManager.hasSelection) replaceSelectionWithText("\n");
        else insertNewlineAtCursor();
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  private void moveCursorLeft() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selectionManager.hasSelection) {
      int sL = selectionManager.selStartLine, sC = selectionManager.selStartChar;
      if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
        sL = selectionManager.selEndLine;
        sC = selectionManager.selEndChar;
      }
      cursorManager.cursorLine = sL;
      cursorManager.cursorChar = sC;
    } else if (cursorManager.cursorChar > 0) cursorManager.cursorChar--;
    else if (cursorManager.cursorLine > 0) {
      cursorManager.cursorLine--;
      String ln = getLineTextForRender(cursorManager.cursorLine);
      cursorManager.cursorChar = ln.length();
    }
    selectionManager.hasSelection = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
    resetCursorBlink();
    invalidate();
    scrollManager.keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  private void moveCursorRight() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selectionManager.hasSelection) {
      int eL = selectionManager.selEndLine, eC = selectionManager.selEndChar;
      if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
        eL = selectionManager.selStartLine;
        eC = selectionManager.selStartChar;
      }
      cursorManager.cursorLine = eL;
      cursorManager.cursorChar = eC;
    } else {
      String ln = getLineTextForRender(cursorManager.cursorLine);
      if (cursorManager.cursorChar < ln.length()) cursorManager.cursorChar++;
      else {
        int next = cursorManager.cursorLine + 1;
        if (!isEof || next < windowStartLine + linesWindow.size()) {
          cursorManager.cursorLine = next;
          cursorManager.cursorChar = 0;
        }
      }
    }
    selectionManager.hasSelection = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
    resetCursorBlink();
    invalidate();
    scrollManager.keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  private void moveCursorUp() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selectionManager.hasSelection) {
      int sL = selectionManager.selStartLine, sC = selectionManager.selStartChar;
      if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
        sL = selectionManager.selEndLine;
        sC = selectionManager.selEndChar;
      }
      cursorManager.cursorLine = sL;
      cursorManager.cursorChar = sC;
    }
    if (cursorManager.cursorLine > 0) {
      cursorManager.cursorLine--;
      String ln = getLineTextForRender(cursorManager.cursorLine);
      cursorManager.cursorChar = Math.min(cursorManager.cursorChar, ln.length());
    }
    selectionManager.hasSelection = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
    resetCursorBlink();
    invalidate();
    scrollManager.keepCursorVisibleHorizontally();
    updateSuggestion(); // Update suggestion after cursor move
  }

  private void moveCursorDown() {
    clearActiveSuggestion(); // Clear suggestion when cursor moves
    if (selectionManager.hasSelection) {
      int eL = selectionManager.selEndLine, eC = selectionManager.selEndChar;
      if (comparePos(selectionManager.selStartLine, selectionManager.selStartChar, selectionManager.selEndLine, selectionManager.selEndChar) > 0) {
        eL = selectionManager.selStartLine;
        eC = selectionManager.selStartChar;
      }
      cursorManager.cursorLine = eL;
      cursorManager.cursorChar = eC;
    }
    int next = cursorManager.cursorLine + 1;
    if (!isEof || next < windowStartLine + linesWindow.size()) {
      cursorManager.cursorLine = next;
      String ln = getLineTextForRender(cursorManager.cursorLine);
      cursorManager.cursorChar = Math.min(cursorManager.cursorChar, ln.length());
    }
    selectionManager.hasSelection = false;
    selectionManager.isSelectAllActive = false;
    selectionManager.isEntireFileSelected = false;
    hidePopup();
    resetCursorBlink();
    invalidate();
    scrollManager.keepCursorVisibleHorizontally();
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
      cursorAnimationManager.onFocusChanged(true);
    } else {
      if (hideKeyboardOnFocusLoss && imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
      cursorAnimationManager.onFocusChanged(false);
      hasComposing = false;
      selectionManager.hasSelection = false;
      hidePopup();
    }
  }

  private void invalidateLineGlobal(int globalLine) {
    if (isWordWrapEnabled) {
      invalidate();
      return;
    }
    int idx = isCodeFoldingEnabled ? getVisibleIndexForGlobalLine(globalLine) : globalLine;
    float top = (idx * lineHeight) - scrollManager.scrollY;
    invalidate(0, (int) Math.floor(top), getWidth(), (int) Math.ceil(top + lineHeight));
  }

  void invalidateLineGlobalForCharAnim(int globalLine) {
    invalidateLineGlobal(globalLine);
  }

  Paint getPaintForCharAnim() {
    return paint;
  }

  private void invalidateCursorArea() {
    if (isWordWrapEnabled) {
      invalidate();
      return;
    }
    invalidateLineGlobal(cursorManager.cursorLine);
  }

  void invalidateCursorAreaForCursor() {
    invalidateCursorArea();
  }

  private boolean isHeavyDrawSuppressed() {
    return false;
  }

  private void invalidateLineNumberCache() {
    lineNumberManager.invalidateCache();
  }

  void invalidateLineNumberCacheForUndo() {
    invalidateLineNumberCache();
  }

  boolean isShowLineNumbersForLineNumbers() {
    return showLineNumbers;
  }

  boolean isCodeFoldingEnabledForLineNumbers() {
    return isCodeFoldingEnabled;
  }

  int getVisibleLineCountForLineNumbers() {
    return getVisibleLineCount();
  }

  int getLinesCountForLineNumbers() {
    return getLinesCount();
  }

  float getLineHeightForLineNumbers() {
    return lineHeight;
  }

  float getScrollYForLineNumbers() {
    return scrollManager.scrollY;
  }

  boolean isRtlForLineNumbers() {
    return isRtl;
  }

  float getFoldMarkerGutterWidthForLineNumbers() {
    return foldMarkerGutterWidth;
  }

  float getLineNumbersGutterWidthForScroll() {
    return lineNumberManager.lineNumbersGutterWidth;
  }

  float getGutterStartXForLineNumbers() {
    return getGutterStartX();
  }

  float getGutterTextPaddingForLineNumbers() {
    return GUTTER_TEXT_PADDING;
  }

  float getTextPaintDescentForLineNumbers() {
    return paint.descent();
  }

  int mapVisibleIndexToGlobalForLineNumbers(int visibleIndex) {
    return mapVisibleIndexToGlobal(visibleIndex);
  }

  int getTotalVisualLineCountForLineNumbers() {
    return getTotalVisualLineCount();
  }

  VisualLinePosition getVisualPositionForIndexForLineNumbers(int visualIndex) {
    return getVisualPositionForIndex(visualIndex);
  }

  int getCursorLineForLineNumbers() {
    return cursorManager.cursorLine;
  }

  int getCurrentLineNumberColorForLineNumbers() {
    return currentLineNumberColor;
  }

  boolean isLineHiddenByFoldForLineNumbers(int line) {
    return isLineHiddenByFold(line);
  }

  int getVisibleIndexForGlobalLineForLineNumbers(int line) {
    return getVisibleIndexForGlobalLine(line);
  }

  int getVisualIndexForLineAndCharForLineNumbers(int line, int ch) {
    return getVisualIndexForLineAndChar(line, ch);
  }

  boolean isWrapPrefixRebuildPendingForScroll() {
    return wordWrapManager.wrapPrefixRebuildPending;
  }

  void clearWrapPrefixRebuildPendingForScroll() {
    wordWrapManager.wrapPrefixRebuildPending = false;
  }

  boolean isWrapPrefixBuildingForScroll() {
    return wordWrapManager.wrapPrefixBuilding;
  }

  boolean isWrapMetricsReadyForScroll() {
    return wordWrapManager.wrapMetricsReady;
  }

  int[] getWrapLinePrefixForScroll() {
    return wordWrapManager.wrapLinePrefix;
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
      if (!hasEdits && undoRedo.getLineCountDelta() == 0) {
        return lineOffsets.length;
      }
      int count = lineOffsets.length + undoRedo.getLineCountDelta();
      if (count < 1) count = 1;
      return Math.max(count, windowCount);
    }
    if (isEof) return windowStartLine + linesWindow.size();
    if (!linesWindow.isEmpty()) return windowStartLine + linesWindow.size();
    return -1;
  }

  private boolean isLineHiddenByFold(int line) {
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
    rebuildFoldIntervalsIfNeeded();
    for (int[] interval : foldIntervals) {
      if (line < interval[0]) return false;
      if (line <= interval[1]) return true;
    }
    return false;
  }

  private FoldRange getFoldRangeAtStart(int line) {
    if (!isCodeFoldingEnabled) return null;
    FoldRange range = foldRanges.get(line);
    return (range != null && range.collapsed) ? range : null;
  }

  private void rebuildFoldIntervalsIfNeeded() {
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

  private int getHiddenLineCount() {
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

  int getVisibleLineCount() {
    int total = getLinesCount();
    if (total <= 0) total = windowStartLine + linesWindow.size();
    int visible = Math.max(1, total - getHiddenLineCount());
    return visible;
  }

  int getVisualIndexForLineAndChar(int line, int ch) {
    if (!isWrapMetricsUsableForLine(line)) {
      if (isCodeFoldingEnabled) return getVisibleIndexForGlobalLine(line);
      return Math.max(0, line);
    }
    int totalLines = wordWrapManager.wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = getLineTextForRender(safeLine);
    int[] starts = getWrapStartsForLine(safeLine, text);
    int seg = getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    return wordWrapManager.wrapLinePrefix[safeLine] + seg;
  }

  private int mapVisibleIndexToGlobal(int visibleIndex) {
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

  int getVisibleIndexForGlobalLine(int globalLine) {
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

  int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / lineHeight));
    if (isWordWrapEnabled) {
      return getVisualPositionForIndex(idx).line;
    }
    return mapVisibleIndexToGlobal(idx);
  }

  private boolean toggleFoldAtLine(int line) {
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

  private FoldRange findFoldRangeForLine(int line) {
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

  private String getLineTextForFoldScan(int line, @Nullable RandomAccessFile raf) {
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

  private FoldRange findIndentFoldRangeForLine(int line, @Nullable RandomAccessFile raf) {
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

  private static final class FoldToken {
    final int index;
    final boolean isBlockComment;
    final char openChar;

    FoldToken(int index, boolean isBlockComment, char openChar) {
      this.index = index;
      this.isBlockComment = isBlockComment;
      this.openChar = openChar;
    }
  }

  private static final class FoldMatch {
    final int endLine;
    final char closeChar;

    FoldMatch(int endLine, char closeChar) {
      this.endLine = endLine;
      this.closeChar = closeChar;
    }
  }

  private FoldToken findFoldTokenInLine(String line, int startIndex) {
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

  private int findBlockCommentEndLine(
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

  private FoldMatch findMatchingBracketFrom(
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

  private float getMaxScrollXForClamp() {
    return scrollManager.getMaxScrollXForClamp();
  }

  private void clampScrollX() {
    scrollManager.clampScrollX();
  }

  void clampScrollXForZoom() {
    clampScrollX();
  }

  private void drawScrollBar(Canvas canvas) {
    scrollManager.drawScrollBar(canvas);
  }

  private void showScrollBar() {
    scrollManager.showScrollBar();
  }

  private void startScrollBarFadeOut() {
    scrollManager.startScrollBarFadeOut();
  }

  private void cancelScrollBarFade() {
    scrollManager.cancelScrollBarFade();
  }

  private void recalculateMaxLineWidth() {
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

  float getMaxLineWidthInWindow() {
    // This is the core fix for horizontal scrolling. The scroll range must be based on the
    // longest line seen anywhere in the file, not just the current visible window.
    return globalMaxLineWidth;
  }

  private int clampSegmentEndForWrapIndicator(
      String line, int segStart, int segEnd, int wrapWidthPx) {
    if (segEnd <= segStart) return segEnd;
    float reserved = wordWrapManager.wordWrapIndicatorWidth + (wordWrapManager.wordWrapIndicatorPadPx * 2f);
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

  float getBottomBarrierPadding() {
    float base = BOTTOM_SCROLL_OFFSET;
    float minSpace = MIN_BOTTOM_VISIBLE_SPACE;
    if (lineHeight > 0f) {
      base = Math.max(base, lineHeight * 2f);
      minSpace = Math.max(minSpace, lineHeight * 2f);
    }
    return Math.max(base, minSpace);
  }

  float getKeyboardBarrierPadding() {
    if (keyboardHeight <= 0) return 0f;
    float minPad = (lineHeight > 0f) ? lineHeight * 2f : MIN_BOTTOM_VISIBLE_SPACE;
    float maxPad = (lineHeight > 0f) ? lineHeight * 3.5f : BOTTOM_SCROLL_OFFSET;
    float kbPad = keyboardHeight * 0.4f;
    return Math.max(minPad, Math.min(maxPad, kbPad));
  }

  private void drawAutoSuggestion(
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

    // The canvas is translated by (getTextStartX() - effectiveScrollX, -scrollManager.scrollY)
    // To get view coordinates:
    // viewX = canvasX + (effectiveScrollX - getTextStartX())
    // viewY = canvasY + scrollManager.scrollY

    float left_view = suggestionStartX_canvas + getTextStartX() - getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (isRtl) {
      float baseX = getRtlLineBaseX(lineContent, globalLine);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = globalLine * lineHeight - scrollManager.scrollY;
    float bottom_view = (globalLine + 1) * lineHeight - scrollManager.scrollY;

    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  private void populateDirectLinesForRange(
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

  private String getLineTextForRenderWithDirect(
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
  String getLineTextForRender(int line) {
    if (line < 0) return "";
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text = getLineFromWindowLocal(line - windowStartLine);
      return (text != null) ? text : "";
    }
    String mod = modifiedLines.get(line);
    return (mod != null) ? mod : "";
  }

  private long[] buildIndexJava(String filepath) {
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

  private void cancelAndCloseReader() {
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

  public void setCursorAnimationEnabled(boolean enabled) {
    cursorAnimationManager.setCursorAnimationEnabled(enabled);
  }

  private void updateCursorDrawPosition(float targetX, float targetY) {
    cursorAnimationManager.updateCursorDrawPosition(targetX, targetY);
  }

  void resetCursorBlink() {
    cursorAnimationManager.resetCursorBlink();
  }

  public void release() {
    cancelAndCloseReader();
    charAnimationManager.release();
    cursorAnimationManager.release();
    ioThread.quitSafely();
  }
  private static boolean isOpeningBracket(char c) {
    return c == '(' || c == '[' || c == '{';
  }

  private static boolean isClosingBracket(char c) {
    return c == ')' || c == ']' || c == '}';
  }

  private static char matchingBracket(char c) {
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

}
