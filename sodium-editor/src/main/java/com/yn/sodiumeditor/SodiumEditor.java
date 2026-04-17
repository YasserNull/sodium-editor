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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.Scroller;
import com.yn.sodiumeditor.input.events.OnScroll;
import com.yn.sodiumeditor.input.events.OnTouch;
import com.yn.sodiumeditor.input.events.OnKeyDown;
import com.yn.sodiumeditor.core.autocompletion.*;
import com.yn.sodiumeditor.core.binary.*;
import com.yn.sodiumeditor.core.cursor.*;
import com.yn.sodiumeditor.core.features.*;
import com.yn.sodiumeditor.core.search.*;
import com.yn.sodiumeditor.core.fold.*;
import com.yn.sodiumeditor.core.guides.indent.*;
import com.yn.sodiumeditor.core.guides.bracket.*;
import com.yn.sodiumeditor.core.guides.whitespace.*;
import com.yn.sodiumeditor.core.highlight.*;
import com.yn.sodiumeditor.core.linenumber.*;
import com.yn.sodiumeditor.core.scroll.*;
import com.yn.sodiumeditor.core.selection.*;
import com.yn.sodiumeditor.core.view.*;
import com.yn.sodiumeditor.core.view.events.*;
import com.yn.sodiumeditor.core.wordwrap.*;
import com.yn.sodiumeditor.core.zoom.*;
import com.yn.sodiumeditor.renderer.animation.*;
import com.yn.sodiumeditor.io.*;
import com.yn.sodiumeditor.renderer.*;
import com.yn.sodiumeditor.input.Ime;

public class SodiumEditor extends View {

  public static final boolean DEBUG_RENDER_LOGS = true;

  private final java.util.HashMap<String, Long> renderLogLast = new java.util.HashMap<>();

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
  public final WindowRender windowRender;
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
  public final com.yn.sodiumeditor.core.view.View view;
  // SearchMatch moved to com.yn.sodiumeditor.core.SearchMatch

  public final Cursor cursor;
  public final Caret caret;
  public final CursorHandle cursorHandle;
  public final Selection selection;
  public final SelectionHandles selectionHandles;

  // moved to OnTouch / Scroll / core.View / IndentGuides
  
  public final CodeFold codeFold;
  public final CodeFoldRender codeFoldRender;
  public final CurrentLineHighlight currentLineHighlight;
  public final ClickAfterEndToAddLine clickAfterEndToAddLine;
  public final BracketCache bracketCache;
  public final EditOperators editOperators;
  public final ViewRender viewRender;
  public final WordWrap wordWrap;

  // BracketMatch moved to com.yn.sodiumeditor.core.BracketMatch

  // BracketToken moved to com.yn.sodiumeditor.core.BracketToken

  public SodiumEditor(Context ctx, @Nullable AttributeSet attrs) {
  super(ctx, attrs);

  float density = getContext().getResources().getDisplayMetrics().density;
  binaryRender = new BinaryRender(this);
  textRender = new TextRender(this);
  windowRender = new WindowRender(this);
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
  view = new com.yn.sodiumeditor.core.view.View(this);
  errorUnderline = new ErrorUnderline(this);
  scroll = new Scroll(this);
  layout = new Layout(this);
  zoom = new Zoom(this);
  scaleGestureDetector = new ScaleGestureDetector(ctx, zoom.createScaleListener());

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
  bracketMatchManager = new BracketMatchManager(this);
  whitespaceGuides = new WhitespaceGuides(this);
  urlUnderline = new UrlUnderline(this);

  pathUnderline = new PathUnderline(this);
  indentGuides = new IndentGuides(this);

  autoBracketPair = new AutoBracketPair(this);

  autoBracketNewline = new AutoBracketNewline(this);

  search = new Search(this);

  popup = new Popup(this);
  autoCompletion = new AutoCompletion(this);
  autoPathCompletion = new AutoPathCompletion(this);
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
  textRender.paint.setTypeface(Typeface.MONOSPACE);
  textRender.paint.setColor(0xFF000000);
  textRender.paint.setAntiAlias(true);
  textRender.paint.setSubpixelText(true);
  textRender.paint.setHinting(Paint.HINTING_ON);
  textRender.paint.setUnderlineText(false); 
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
      com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
      0xFF000000,
      textRender.paint.getTextSize(),
      textRender.paint.getTypeface(),
      false,
      HighliteRender.HighlightRuleType.STRING);
  highlightRules.whitespaceCommentRule =
    new HighliteRender.HighlightRule(
      "",
      com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
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

  autoCompletion.suggestionPaint.set(textRender.paint);
  autoCompletion.suggestionPaint.setColor(0xFFAAAAAA); 
  autoCompletion.suggestionPaint.setAntiAlias(true);
  autoCompletion.suggestionPaint.setSubpixelText(true);
  autoCompletion.suggestionPaint.setHinting(Paint.HINTING_ON);
  autoCompletion.isSuggestionTextSizeCustom = false; 
  }


  




  // moved to core.View (editor.view.heavyFeaturesThreshold)


  
public void logRender(String key, String msg, long intervalMs) {
  if (!DEBUG_RENDER_LOGS) return;
  long now = SystemClock.uptimeMillis();
  Long last = renderLogLast.get(key);
  if (last != null && intervalMs > 0 && now - last < intervalMs) return;
  renderLogLast.put(key, now);
  Log.d("SodiumRender", msg);
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

  // getTextStartX/getTextAreaWidth moved to renderer.Layout

  
  
  

  

  
  


  // lastHitAdvance moved to renderer.Layout

  

// readLineSliceByChars moved to FileIO

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
  onFocusChanged.onFocusChanged(focused, direction, previouslyFocusedRect);
  }

   public boolean callSuperOnTouchEvent(android.view.MotionEvent event) {
  return super.onTouchEvent(event);
  }
public boolean isHeavyDrawSuppressed() {
  return false;
  }
  @Override
  protected void onDraw(Canvas canvas) {
  super.onDraw(canvas);
  onDraw.onDraw(canvas);
  }
}
