package com.yn.sodiumeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.yn.sodiumeditor.core.autosuggestion.*;
import com.yn.sodiumeditor.core.binary.*;
import com.yn.sodiumeditor.core.cursor.*;
import com.yn.sodiumeditor.core.features.AutoBracketNewline;
import com.yn.sodiumeditor.core.features.AutoBracketNewlineIndent;
import com.yn.sodiumeditor.core.features.AutoPair;
import com.yn.sodiumeditor.core.features.AutoIndentAfterClosingBracket;
import com.yn.sodiumeditor.core.features.BaseIndentOnNewline;
import com.yn.sodiumeditor.core.features.ClickAfterEndToAddLine;
import com.yn.sodiumeditor.core.guides.bracket.*;
import com.yn.sodiumeditor.core.guides.SymbolsMatch;
import com.yn.sodiumeditor.core.guides.indent.*;
import com.yn.sodiumeditor.core.guides.whitespace.*;
import com.yn.sodiumeditor.core.highlight.*;
import com.yn.sodiumeditor.core.linenumber.*;
import com.yn.sodiumeditor.core.scroll.*;
import com.yn.sodiumeditor.core.search.*;
import com.yn.sodiumeditor.core.selection.*;
import com.yn.sodiumeditor.core.view.*;
import com.yn.sodiumeditor.core.view.events.*;
import com.yn.sodiumeditor.core.wordwrap.*;
import com.yn.sodiumeditor.core.zoom.*;
import com.yn.sodiumeditor.input.Ime;
import com.yn.sodiumeditor.input.events.OnKeyDown;
import com.yn.sodiumeditor.input.events.OnScroll;
import com.yn.sodiumeditor.input.events.OnTouch;
import com.yn.sodiumeditor.io.*;
import com.yn.sodiumeditor.renderer.*;
import com.yn.sodiumeditor.renderer.animation.*;

public class SodiumEditor extends View {
  public static boolean DEBUG_LOGS = false;

  public final FileIO fileIO;
  public final Scroll scroll;
  public final Layout layout;
  public ScaleGestureDetector scaleGestureDetector;
  public final Zoom zoom;
  public final Ime ime;
  public final OnTouch onTouch;
  public final OnScroll onScroll;
  public final OnKeyDown onKeyDown;
  public final onDraw onDraw;
  public final onMeasure onMeasure;
  public final onSizeChanged onSizeChanged;
  public final onFocusChanged onFocusChanged;
  public final onGenericMotionEvent onGenericMotionEvent;
  public final ColorCodeHighlight colorCodeHighlight;
  public final BracketGuides bracketGuides;
  public final SymbolsMatch symbolsMatch;
  public final WhitespaceGuides whitespaceGuides;
  public final UrlUnderline urlUnderline;
  public final PathUnderline pathUnderline;
  public final IndentGuides indentGuides;
  public final AutoPair autoPair;
  public final AutoBracketNewline autoBracketNewline;
  public final AutoBracketNewlineIndent autoBracketNewlineIndent;
  public final AutoIndentAfterClosingBracket autoIndentAfterClosingBracket;
  public final BaseIndentOnNewline baseIndentOnNewline;
  public final Search search;
  public final BinaryRender binaryRender;
  public final Popup popup;
  public final TextRender textRender;
  public final WindowRender windowRender;
  public final HighlightRender highlightRender;
  public final Highlight highlight;
  public final AutoSuggestion autoSuggestion;
  public final AutoPathSuggestion autoPathSuggestion;
  public final ErrorUnderline errorUnderline;
  public final CursorAnimation cursorAnimation;
  public final CharAnimation charAnimation;
  public final LineNumber lineNumber;
  public final LoadingCircle loadingCircle;
  public final com.yn.sodiumeditor.core.TextRange textRange;
  public final com.yn.sodiumeditor.renderer.draw.TextLineDraw textLineDraw;
  public final HighlightRules highlightRules;
  public final com.yn.sodiumeditor.core.view.EditorView view;
  public final Cursor cursor;
  public final Caret caret;
  public final CursorHandle cursorHandle;
  public final Selection selection;
  public final SelectionHandles selectionHandles;
  public final CurrentLineHighlight currentLineHighlight;
  public final ClickAfterEndToAddLine clickAfterEndToAddLine;
  public final BracketCache bracketCache;
  public final EditOperators editOperators;
  public final ViewRender viewRender;
  public final WordWrap wordWrap;

  public SodiumEditor(Context ctx, @Nullable AttributeSet attrs) {
    super(ctx, attrs);

    float density = getContext().getResources().getDisplayMetrics().density;
    binaryRender = new BinaryRender(this);
    textRender = new TextRender(this);
    windowRender = new WindowRender(this);
    textRange = new com.yn.sodiumeditor.core.TextRange(this);
    textLineDraw = new com.yn.sodiumeditor.renderer.draw.TextLineDraw(this);
    highlightRender = new HighlightRender(this);
    lineNumber = new LineNumber(this);
    currentLineHighlight = new CurrentLineHighlight(this);
    clickAfterEndToAddLine = new ClickAfterEndToAddLine(this);
    highlight = new Highlight(this);
    highlightRules = new HighlightRules(this, highlight);
    view = new com.yn.sodiumeditor.core.view.EditorView(this);
    errorUnderline = new ErrorUnderline(this);
    scroll = new Scroll(this);
    layout = new Layout(this);
    zoom = new Zoom(this);
    scaleGestureDetector = new ScaleGestureDetector(ctx, zoom.createScaleListener());
    scaleGestureDetector.setQuickScaleEnabled(false);
    ime = new Ime(this);
    onTouch = new OnTouch(this);
    onScroll = new OnScroll(this);
    scroll.gestureDetector = onScroll.getGestureDetector();
    onKeyDown = new OnKeyDown(this);
    onDraw = new onDraw(this);
    onMeasure = new onMeasure(this);
    onSizeChanged = new onSizeChanged(this);
    onFocusChanged = new onFocusChanged(this);
    onGenericMotionEvent = new onGenericMotionEvent(this);
    colorCodeHighlight = new ColorCodeHighlight(this);
    bracketGuides = new BracketGuides(this);
    symbolsMatch = new SymbolsMatch(this);
    whitespaceGuides = new WhitespaceGuides(this);
    urlUnderline = new UrlUnderline(this);
    pathUnderline = new PathUnderline(this);
    indentGuides = new IndentGuides(this);
    autoPair = new AutoPair(this);
    autoIndentAfterClosingBracket = new AutoIndentAfterClosingBracket(this);
    baseIndentOnNewline = new BaseIndentOnNewline(this);
    autoBracketNewline = new AutoBracketNewline(this);
    autoBracketNewlineIndent = autoBracketNewline;
    search = new Search(this);
    popup = new Popup(this);
    autoSuggestion = new AutoSuggestion(this);
    autoPathSuggestion = new AutoPathSuggestion(this);
    loadingCircle = new LoadingCircle(this);
    editOperators = new EditOperators(this);
    viewRender = new ViewRender(this);
    cursorAnimation = new CursorAnimation(this);
    charAnimation = new CharAnimation(this);
    bracketCache = new BracketCache(this);
    cursor = new Cursor(this);
    caret = new Caret(this, cursor);
    cursorHandle = new CursorHandle(this, cursor, caret);
    selection = new Selection(this, cursor);
    selectionHandles = new SelectionHandles(this, selection);
    wordWrap = new WordWrap(this);
    fileIO = new FileIO(this);

    textRender.paint.setTextSize(36);
    textRender.paint.setTypeface(Typeface.DEFAULT);
    textRender.paint.setColor(0xFF000000);
    textRender.paint.setAntiAlias(true);
    textRender.paint.setSubpixelText(true);
    textRender.paint.setHinting(Paint.HINTING_ON);
    textRender.paint.setUnderlineText(false);
    textRender.baseTypeface =
        (textRender.paint.getTypeface() != null)
            ? textRender.paint.getTypeface()
            : Typeface.DEFAULT;
    textRender.lineHeight = textRender.paint.getFontSpacing();
    whitespaceGuides.updateMetrics();
    lineNumber.lineNumbersPaint.setTextSize(36);
    selectionHandles.baseHandleTextSizePx = textRender.paint.getTextSize();
    selectionHandles.updateHandleMetricsForTextSize(textRender.paint.getTextSize());
    cursorHandle.baseCursorHandleTextSizePx = textRender.paint.getTextSize();
    cursorHandle.updateHandleMetricsForTextSize(textRender.paint.getTextSize());
    highlightRules.whitespaceStringRule =
        new HighlightRender.HighlightRule(
            "",
            com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            HighlightRender.HighlightRuleType.STRING);
    highlightRules.whitespaceCommentRule =
        new HighlightRender.HighlightRule(
            "",
            com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
            0xFF000000,
            textRender.paint.getTextSize(),
            textRender.paint.getTypeface(),
            false,
            HighlightRender.HighlightRuleType.BLOCK_COMMENT);

    selection.selectionPaint.setStyle(Paint.Style.FILL);
    caret.caretPaint.setStyle(Paint.Style.FILL);
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
                getLocationInWindow(view.tmpLocationInWindow);
                int viewBottom = view.tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - imeTop);
                newKeyboardHeight = Math.min(overlap, getHeight());
              } else {
                getWindowVisibleDisplayFrame(view.visibleDisplayFrame);
                getLocationInWindow(view.tmpLocationInWindow);
                int viewBottom = view.tmpLocationInWindow[1] + getHeight();
                int overlap = Math.max(0, viewBottom - view.visibleDisplayFrame.bottom);
                newKeyboardHeight = overlap;
              }

              if (newKeyboardHeight != view.keyboardHeight) {
                view.keyboardHeight = newKeyboardHeight;

                post(() -> scroll.keepCursorVisibleHorizontally());
              }
            });

    autoSuggestion.suggestionPaint.set(textRender.paint);
    autoSuggestion.suggestionPaint.setColor(0xFFAAAAAA);
    autoSuggestion.suggestionPaint.setAntiAlias(true);
    autoSuggestion.suggestionPaint.setSubpixelText(true);
    autoSuggestion.suggestionPaint.setHinting(Paint.HINTING_ON);
    autoSuggestion.isSuggestionTextSizeCustom = false;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    onMeasure.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    onSizeChanged.onSizeChanged(w, h, oldw, oldh);
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (onGenericMotionEvent.onGenericMotionEvent(event)) return true;
    return super.onGenericMotionEvent(event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (onKeyDown.onKeyDown(keyCode, event)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return !view.isDisabled && !view.isReadOnly;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return ime.onCreateInputConnection(outAttrs);
  }

  public void setKeyboardSuggestionsEnabled(boolean enabled) {
    ime.setKeyboardSuggestionsEnabled(enabled);
  }

  public boolean isKeyboardSuggestionsEnabled() {
    return ime.isKeyboardSuggestionsEnabled();
  }

  public void setVisualSpaceScale(int scale) {
    textRender.setVisualSpaceScale(scale);
  }

  public int getVisualSpaceScale() {
    return textRender.getVisualSpaceScale();
  }

  public void setSingleCommentsHighlight(String delimiter, int color, int style) {
    highlight.setSingleCommentsHighlight(delimiter, color, style);
  }

  public void setMultiCommentsHighlight(
      String startDelimiter, String endDelimiter, int color, int style) {
    highlight.setMultiCommentsHighlight(startDelimiter, endDelimiter, color, style);
  }

  public void setStringsHighlight(String delimiter, boolean multiLine, int color, int style) {
    highlight.setStringsHighlight(delimiter, multiLine, color, style);
  }

  public void clearStringsHighlight() {
    highlight.clearStringsHighlight();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return onTouch.onTouchEvent(event);
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    onFocusChanged.onFocusChanged(focused, direction, previouslyFocusedRect);
  }

  public boolean callSuperOnTouchEvent(android.view.MotionEvent event) {
    return super.onTouchEvent(event);
  }

  public boolean isHeavyDrawSuppressed() {
    boolean fastScroll = scroll.scrollerIsScrolling || scroll.flingStopAnimator != null;
    boolean zooming = zoom.isZoomGestureActive() || zoom.isScaling;
    boolean hugeFile = view.getLinesCount() > view.heavyFeaturesThreshold;
    boolean loading = fileIO.isWindowLoading;
    return zooming || loading || (fastScroll && hugeFile);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    onDraw.onDraw(canvas);
  }

  @Override
  public void computeScroll() {
    scroll.computeScroll();
  }
}
