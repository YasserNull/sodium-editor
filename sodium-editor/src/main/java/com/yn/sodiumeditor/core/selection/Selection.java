package com.yn.sodiumeditor.core.selection;
import java.io.File;
import com.yn.sodiumeditor.io.EditOperators;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileInputStream;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.cursor.Cursor;
import android.graphics.Paint;
import android.animation.ValueAnimator;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
   import android.graphics.RectF;
     import android.graphics.Path;
     import android.view.inputmethod.InputMethodManager;
     import android.content.Context;
/**
 * Selection handles text selection logic for SodiumEditor.
 * This includes:
 * - Selection state (start/end positions)
 * - Selection range operations
 * - Select all functionality
 */
public class Selection {

  // Selection state
  public boolean hasSelection = false;
  public int selStartLine = 0, selStartChar = 0;
  public int selEndLine = 0, selEndChar = 0;
  public boolean selecting = false;
  public boolean isSelectAllActive = false;
  public boolean isEntireFileSelected = false;
  public final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF selectionHighlightRect = new RectF();
  // Selection appearance
  public int selectionColor = 0x4033B5E5;
  public int selectionHighlightColor = 0x8033B5E5;
  public int selectionHandleColor = 0xFF2196F3; // Blue
  public boolean selectionAnimationEnabled = true;
  public float selectionAlpha = 1f;
  public float handleAlpha = 1f;
  private ValueAnimator selectionFadeAnimator;
  private boolean lastHasSelection = false;
  
  
  private final SodiumEditor editor;
  private final Cursor cursor;
public final RectF selectionRectTmp = new RectF();
  public final Path selectionPathTmp = new Path();
  public final float[] selectionRadiiTmp = new float[8];
  
  public boolean isLineNumberSelecting = false;
  public int lineNumberSelectAnchorLine = -1;
  public boolean longPressSelecting = false;
  public int longPressAnchorLine = -1;
  public int longPressAnchorChar = -1;
  
  // Selection state
        
         // Double tap selection state
       
       public int lastDoubleTapLine = -1;                              
         public int lastDoubleTapWordStart = -1;
      public int lastDoubleTapWordEnd = -1;
      public int lastDoubleTapStage = 0;
     public static final long COPY_CUT_MAX_LINES = 20000L;
  public static final int COPY_CUT_MAX_CHARS = 8_000_000; // safety cap
  public long copyCutMaxLines = COPY_CUT_MAX_LINES;
  public int copyCutMaxChars = COPY_CUT_MAX_CHARS;
  public int hideCopyCutMaxLines = 20000;
  public int replaceAllMaxCount = 100000;
  public boolean hideKeyboardOnFocusLoss = true;
  
  public Selection(SodiumEditor editor, Cursor cursor) {
    this.editor = editor;
    this.cursor = cursor;
  }

  /**
   * Set selection range
   */
  public void setSelection(int startLine, int startChar, int endLine, int endChar) {
    selStartLine = startLine;
    selStartChar = startChar;
    selEndLine = endLine;
    selEndChar = endChar;
    hasSelection = !(selStartLine == selEndLine && selStartChar == selEndChar);
    selecting = false;
  }

  /**
   * Clear selection
   */
  public void clearSelection() {
    hasSelection = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = false;
    longPressSelecting = false;
    longPressAnchorLine = -1;
    longPressAnchorChar = -1;
    
    // Sync with cursor
    selStartLine = editor.cursor.cursorLine;
    selStartChar = editor.cursor.cursorChar;
    selEndLine = editor.cursor.cursorLine;
    selEndChar = editor.cursor.cursorChar;
    updateSelectionVisibility(false);
  }

  /**
   * Select all text
   */
  public void selectAll() {
    editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion when selecting all
    final boolean keyboardWasVisible = editor.keyboardHeight > 0;
    if (editor.wordWrap.isWordWrapEnabled) {
      // Free the IO thread from wrap rebuilds so select-all can jump to end quickly.
      int widthPx = Math.max(1, Math.round(editor.wordWrap.getWrapWidth()));
      if (editor.wordWrap.isWrapMetricsUsableForWindow(widthPx)) {
        editor.wordWrap.cancelWrapWorkForPriority();
      }
    }
    editor.setDisable(true);
    editor.loadingCircle.showLoadingCircle(true);

    isSelectAllActive = true;
    isEntireFileSelected = true;
    hasSelection = true;

    selStartLine = 0;
    selStartChar = 0;
    editor.popup.hidePopup();

    // =========================
    // In-memory mode (no file):
    // - Happens after "select all -> delete" (file cleared), then user types new text
    // - Also covers scenarios where content is edited but not persisted to disk
    // =========================
    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      synchronized (editor.textRender.linesWindow) {
        if (editor.textRender.linesWindow.isEmpty()) editor.textRender.linesWindow.add("");
        // With no file backing, treat current window as the whole document.
        if (editor.textRender.windowStartLine != 0) editor.textRender.windowStartLine = 0;
        editor.fileIO.isEof = true;
      }

      selEndLine = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
      String lastLineText = editor.getLineTextForRender(selEndLine);
      selEndChar = lastLineText.length();
      editor.cursor.cursorLine = selEndLine;
      editor.cursor.cursorChar = selEndChar;

      editor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

      editor.setDisable(false);
      editor.loadingCircle.showLoadingCircle(false);
      editor.invalidate();
      editor.requestFocus();
      editor.popup.showPopupAtSelection();

      editor.post(
          () -> {
            editor.requestFocus();
            if (keyboardWasVisible) editor.showKeyboard();
            InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(editor);
          });
      return;
    }

    // If we're already at EOF, we can select to the current visible logical end
    // without waiting for the index (important when user appended lines after EOF).
    if (editor.fileIO.isEof) {
      int windowLast = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
      selEndLine = windowLast;
      String lastLineText = editor.getLineTextForRender(windowLast);
      selEndChar = lastLineText.length();
      editor.cursor.cursorLine = windowLast;
      editor.cursor.cursorChar = selEndChar;

      editor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

      editor.setDisable(false);
      editor.loadingCircle.showLoadingCircle(false);
      editor.invalidate();
      editor.requestFocus();
      editor.popup.showPopupAtSelection();

      editor.post(
          () -> {
            editor.requestFocus();
            if (keyboardWasVisible) editor.showKeyboard();
            InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(editor);
          });
      return;
    }

    // الأفضل: لو index جاهز نروح نهاية الملف بدقة (بدون قفزة غلط)
    Runnable goToEndUsingIndex =
        () -> {
          if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null) return;

          int fileLastLine;
          synchronized (editor.fileIO.lineOffsetsLock) {
            fileLastLine = Math.max(0, editor.fileIO.lineOffsets.length - 1);
          }

          // If the current window actually goes beyond file end (due to appended in-memory lines),
          // prefer the window end and DO NOT reload from file (reload would drop the appended
          // lines).
          if (editor.fileIO.isEof) {
            int windowLast = Math.max(0, editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1);
            if (windowLast > fileLastLine) {
              selEndLine = windowLast;
              String lastLineText = editor.getLineTextForRender(windowLast);
              selEndChar = lastLineText.length();
              editor.cursor.cursorLine = windowLast;
              editor.cursor.cursorChar = selEndChar;

              editor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

              editor.setDisable(false);
              editor.loadingCircle.showLoadingCircle(false);
              editor.invalidate();
              editor.requestFocus();
              editor.popup.showPopupAtSelection();

              editor.post(
                  () -> {
                    editor.requestFocus();
                    if (keyboardWasVisible) editor.showKeyboard();
                    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(editor);
                  });
              return;
            }
          }

          selEndLine = fileLastLine;

          int targetStart = Math.max(0, fileLastLine - editor.textRender.prefetchLines);

          editor.fileIO.loadWindowAround(
              targetStart,
              () ->
                  editor.post(
                      () -> {
                        String lastLineText = editor.getLineTextForRender(fileLastLine);
                        selEndChar = lastLineText.length();
                        editor.cursor.cursorLine = fileLastLine;
                        editor.cursor.cursorChar = selEndChar;

                        editor.scroll.scrollToLineFastForSelectAll(fileLastLine, selEndChar);

                        editor.setDisable(false);
                        editor.loadingCircle.showLoadingCircle(false);
                        editor.invalidate();
                        editor.requestFocus();
                        editor.popup.showPopupAtSelection();

                        editor.post(
                            () -> {
                              editor.requestFocus();
                              if (keyboardWasVisible) editor.showKeyboard();
                              InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                              if (imm != null) imm.restartInput(editor);
                            });
                      }),
              false);
        };

    if (editor.fileIO.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    // لو index مو جاهز: ابدأ بناءه ثم انتظر جاهزيته (بدل "قرب النهاية" الغلط)
    if (!editor.fileIO.isIndexBuilding && !editor.fileIO.isIndexDisabled) {
      editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
    }

    // نحدد selEndLine مؤقتاً للهايلايت بواسطة countTotalLines (سريع)
    editor.fileIO.countTotalLines(
        totalLines -> {
          int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
          selEndLine = Math.max(0, lastLine);

          Runnable goToEndWithoutIndex =
              () -> {
                int targetStart = Math.max(0, selEndLine - editor.textRender.prefetchLines);
                editor.fileIO.loadWindowAround(
                    targetStart,
                    () ->
                        editor.post(
                            () -> {
                              String lastLineText = editor.getLineTextForRender(selEndLine);
                              selEndChar = lastLineText.length();
                              editor.cursor.cursorLine = selEndLine;
                              editor.cursor.cursorChar = selEndChar;

                              editor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

                              editor.setDisable(false);
                              editor.loadingCircle.showLoadingCircle(false);
                              editor.invalidate();
                              editor.requestFocus();
                              editor.popup.showPopupAtSelection();

                              editor.post(
                                  () -> {
                                    editor.requestFocus();
                                    if (keyboardWasVisible) editor.showKeyboard();
                                    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.restartInput(editor);
                                  });
                            }),
                    false);
              };

          if (editor.fileIO.isIndexDisabled) {
            goToEndWithoutIndex.run();
            return;
          }

          final int ticket = editor.editOperators.editVersion.incrementAndGet();
          Runnable poll =
              new Runnable() {
                @Override
                public void run() {
                  if (ticket != editor.editOperators.editVersion.get()) return;

                  // Important: if file became unavailable (e.g. cleared and switched to memory),
                  // stop waiting to avoid infinite spinner.
                  if (editor.fileIO.sourceFile == null) {
                    editor.setDisable(false);
                    editor.loadingCircle.showLoadingCircle(false);
                    editor.invalidate();
                    editor.popup.showPopupAtSelection();
                    if (keyboardWasVisible) editor.showKeyboard();
                    return;
                  }

                  if (editor.fileIO.isIndexDisabled) {
                    goToEndWithoutIndex.run();
                  } else if (editor.fileIO.isIndexReady) {
                    goToEndUsingIndex.run();
                  } else {
                    editor.caret.mainHandler.postDelayed(this, 80);
                  }
                }
              };
          editor.caret.mainHandler.post(poll);
        });
  }


  /**
   * Select word at cursor
   */
  public void selectWordAtCursor() {
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return;
    
    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return;
    if (Character.isWhitespace(line.charAt(pos))) return;
    
    int[] bounds = editor.computeWordBounds(line, pos);
    if (bounds != null && bounds[0] != bounds[1]) {
      setSelection(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
    }
  }

  /**
   * Select line at cursor
   */
  public void selectLineAtCursor() {
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null) return;
    
    setSelection(editor.cursor.cursorLine, 0, editor.cursor.cursorLine, line.length());
  }

  /**
   * Get selected text
   */
  public String getSelectedText() {
    if (!hasSelection) return null;
    
    int sL = selStartLine, sC = selStartChar;
    int eL = selEndLine, eC = selEndChar;
    
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    
    StringBuilder sb = new StringBuilder();
    for (int line = sL; line <= eL; line++) {
      String lineText = editor.getLineTextForRender(line);
      if (lineText == null) lineText = "";
      
      int from = (line == sL) ? Math.max(0, Math.min(sC, lineText.length())) : 0;
      int to = (line == eL) ? Math.max(0, Math.min(eC, lineText.length())) : lineText.length();
      
      if (from < to) {
        sb.append(lineText, from, to);
      }
      if (line < eL) {
        sb.append('\n');
      }
    }
    
    return sb.toString();
  }

  /**
   * Check if position is within selection
   */
  public boolean contains(int line, int ch) {
    if (!hasSelection) return false;
    
    int sL = selStartLine, sC = selStartChar;
    int eL = selEndLine, eC = selEndChar;
    
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    
    if (line < sL || line > eL) return false;
    if (line == sL && ch < sC) return false;
    if (line == eL && ch > eC) return false;
    
    return true;
  }

  /**
   * Get selection start line
   */
  public int getStartLine() {
    return selStartLine;
  }

  /**
   * Get selection start character
   */
  public int getStartChar() {
    return selStartChar;
  }

  /**
   * Get selection end line
   */
  public int getEndLine() {
    return selEndLine;
  }

  /**
   * Get selection end character
   */
  public int getEndChar() {
    return selEndChar;
  }

  /**
   * Get selection line count
   */
  public int getLineCount() {
    if (!hasSelection) return 0;
    return Math.abs(selEndLine - selStartLine) + 1;
  }

  /**
   * Check if selection is empty
   */
  public boolean isEmpty() {
    return !hasSelection || (selStartLine == selEndLine && selStartChar == selEndChar);
  }

  /**
   * Compare two positions
   */
  public int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  // Getters and Setters

  public void setSelectionColor(int color) {
    selectionColor = color;
  }

  public void setSelectionHandleColor(int color) {
    selectionHandleColor = color;
  }

  public boolean hasSelection() {
    return hasSelection;
  }

  public boolean isSelectAll() {
    return isSelectAllActive || isEntireFileSelected;
  }
  
  

  public static final class TextRange {
    final int start;
    final int end;

    TextRange(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  
  public boolean isQuoteChar(char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  @Nullable
  public TextRange findEnclosingQuoteRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<TextRange> ranges = new ArrayList<>();
    char current = 0;
    int start = -1;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (current == 0) {
        if (isQuoteChar(c) && !editor.highlite.isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !editor.highlite.isEscaped(line, i)) {
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
  public TextRange findEnclosingBracketRange(String line, int index) {
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
        if (c == currentQuote && !editor.highlite.isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !editor.highlite.isEscaped(line, i)) {
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
  public boolean isPositionInsideSelection(int line, int ch) {
    if (!hasSelection) return false;
    int sL = selStartLine;
    int sC = selStartChar;
    int eL = selEndLine;
    int eC = selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selEndLine;
      sC = selEndChar;
      eL = selStartLine;
      eC = selStartChar;
    }
    if (comparePos(line, ch, sL, sC) < 0) return false;
    return comparePos(line, ch, eL, eC) <= 0;
  }
  public void addSelectionCandidate(List<TextRange> out, int start, int end, int lineLen) {
    if (out == null) return;
    int s = Math.max(0, Math.min(start, lineLen));
    int e = Math.max(0, Math.min(end, lineLen));
    if (e <= s) return;
    for (TextRange r : out) {
      if (r.start == s && r.end == e) return;
    }
    out.add(new TextRange(s, e));
  }

  public int findSelectionCandidateIndex(int line, List<TextRange> candidates) {
    if (!hasSelection || candidates == null || candidates.isEmpty()) return -1;
    int sL = selStartLine;
    int sC = selStartChar;
    int eL = selEndLine;
    int eC = selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      sL = selEndLine;
      sC = selEndChar;
      eL = selStartLine;
      eC = selStartChar;
    }
    if (sL != line || eL != line) return -1;
    for (int i = 0; i < candidates.size(); i++) {
      TextRange r = candidates.get(i);
      if (r.start == sC && r.end == eC) return i;
    }
    return -1;
  }

  public ArrayList<TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
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

  private boolean isSmartWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  private boolean isSmartSeparator(char c) {
    switch (c) {
      case '.':
      case '٫':
      case ':':
      case '؛':
      case '-':
      case '+':
      case '\\':
      case '|':
      case ',':
      case '،':
        return true;
      default:
        return false;
    }
  }

  private int[] findWordBounds(String line, int pos) {
    int len = line.length();
    int idx = Math.max(0, Math.min(pos, len - 1));
    if (!isSmartWordChar(line.charAt(idx))) return new int[] {idx, idx};
    int start = idx;
    int end = idx;
    while (start > 0 && isSmartWordChar(line.charAt(start - 1))) start--;
    while (end < len - 1 && isSmartWordChar(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  private void addCandidateUnique(ArrayList<TextRange> out, int start, int end, int len) {
    int s = Math.max(0, Math.min(start, len));
    int e = Math.max(0, Math.min(end, len));
    if (e <= s) return;
    for (int i = 0; i < out.size(); i++) {
      TextRange r = out.get(i);
      if (r.start == s && r.end == e) return;
    }
    out.add(new TextRange(s, e));
  }

  private ArrayList<TextRange> buildSmartWordCandidates(String line, int charIndex) {
    ArrayList<TextRange> out = new ArrayList<>(4);
    if (line == null || line.isEmpty()) return out;
    int len = line.length();
    int idx = Math.max(0, Math.min(charIndex, len - 1));
    char c = line.charAt(idx);

    int[] base = null;
    if (isSmartWordChar(c)) {
      base = findWordBounds(line, idx);
    } else {
      int left = idx - 1;
      int right = idx + 1;
      while (left >= 0 && Character.isWhitespace(line.charAt(left))) left--;
      while (right < len && Character.isWhitespace(line.charAt(right))) right++;
      if (left >= 0 && isSmartWordChar(line.charAt(left))) {
        base = findWordBounds(line, left);
      } else if (right < len && isSmartWordChar(line.charAt(right))) {
        base = findWordBounds(line, right);
      } else {
        return out;
      }
    }

    addCandidateUnique(out, base[0], base[1], len);

    int curStart = base[0];
    int curEnd = base[1];

    // Expand to the right step by step.
    while (curEnd < len && isSmartSeparator(line.charAt(curEnd))) {
      int rightWordStart = curEnd + 1;
      while (rightWordStart < len && Character.isWhitespace(line.charAt(rightWordStart))) rightWordStart++;
      int rightWordEnd = rightWordStart;
      while (rightWordEnd < len && isSmartWordChar(line.charAt(rightWordEnd))) rightWordEnd++;
      if (rightWordEnd <= rightWordStart) break;
      curEnd = rightWordEnd;
      addCandidateUnique(out, curStart, curEnd, len);
    }

    // Then expand to the left step by step.
    while (curStart > 0 && isSmartSeparator(line.charAt(curStart - 1))) {
      int leftWordEnd = curStart - 1;
      int leftWordStart = leftWordEnd - 1;
      while (leftWordStart >= 0 && isSmartWordChar(line.charAt(leftWordStart))) leftWordStart--;
      leftWordStart += 1;
      if (leftWordStart >= leftWordEnd) break;
      curStart = leftWordStart;
      addCandidateUnique(out, curStart, curEnd, len);
    }

    return out;
  }
  public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    ArrayList<TextRange> candidates = buildSmartWordCandidates(lineText, charIndex);
    if (candidates.isEmpty()) return false;
    int wStart = candidates.get(0).start;
    int wEnd = candidates.get(0).end;

    TextRange quote = findEnclosingQuoteRange(lineText, charIndex);
    if (quote != null) {
      addCandidateUnique(candidates, quote.start + 1, quote.end, lineText.length());
      addCandidateUnique(candidates, quote.start, quote.end + 1, lineText.length());
    }

    TextRange bracket = findEnclosingBracketRange(lineText, charIndex);
    if (bracket != null) {
      addCandidateUnique(candidates, bracket.start + 1, bracket.end, lineText.length());
      addCandidateUnique(candidates, bracket.start, bracket.end + 1, lineText.length());
    }
    if (candidates.isEmpty()) return false;

    boolean sameAnchor =
        line == lastDoubleTapLine
            && wStart == lastDoubleTapWordStart
            && wEnd == lastDoubleTapWordEnd;
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
    selStartLine = selEndLine = line;
    selStartChar = pick.start;
    selEndChar = pick.end;
    hasSelection = true;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = true;
    editor.cursor.cursorLine = line;
    editor.cursor.cursorChar = selEndChar;
    lastDoubleTapLine = line;
    lastDoubleTapWordStart = wStart;
    lastDoubleTapWordEnd = wEnd;
    lastDoubleTapStage = nextIdx;
    return true;
  }
  public void clearSelectionStateAfterDelete() {
    hasSelection = false;
    selecting = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    editor.caret.resetBlink();
  }
  public void setSelectionHighlightColor(int color) {
    if (this.selectionHighlightColor == color) return;
    this.selectionHighlightColor = color;
    if (hasSelection) editor.invalidate();
  }
  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }
  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    int targetLine = Math.max(0, cursorLine);
    int targetChar = Math.max(0, editor.cursor.cursorChar);
    cursorLine = targetLine;
    if (cursorLine >= editor.textRender.windowStartLine
        && cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      String lineText = editor.getLineTextForRender(cursorLine);
      this.editor.cursor.cursorChar = Math.max(0, Math.min(targetChar, lineText.length()));
    } else {
      this.editor.cursor.cursorChar = targetChar;
    }
    editor.caret.resetBlink();
    editor.invalidate();
  }
  
  public int clampLineForSelection(int line) {
    if (line < 0) return 0;
    if (editor.fileIO.isEof) {
      int last = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  public boolean isLineSelectable(int line) {
    editor.fileIO.ensureLineInWindow(line, true);
    String ln = editor.getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }
  public void copyOrCutSelection(final boolean cut) {
    if (!hasSelection) return;
    editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion when copying/cutting

    // Hidden/disabled for huge selections (requested behavior)
    if (editor.shouldHideCopyCutForSelection()) return;

    int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
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
        (fsL >= editor.textRender.windowStartLine) && (feL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
    if (fullyInWindow) {
      String text = buildSelectedTextFromWindow(fsL, fsC, feL, feC, copyCutMaxChars);
      ClipboardManager cm = (ClipboardManager) editor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
      if (cut) {
        deleteSelection();
      }
      return;
    }

    if (editor.wordWrap.isWordWrapEnabled) {
      editor.wordWrap.cancelWrapWorkForPriority();
    }

    editor.fileIO.ioHandler.post(
        () -> {
          final String text = buildSelectedTextBlocking(fsL, fsC, feL, feC, copyCutMaxChars);
          editor.post(
              () -> {
                ClipboardManager cm = (ClipboardManager) editor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null)
                  cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));

                if (cut) {
                  deleteSelection();
                }
              });
        });
  }
  public void deleteSelection() {
    editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion when deleting selection
    replaceSelectionWithText("");
  }
  public void recordReplaceSelectionEdit(
      int sL,
      int sC,
      int eL,
      int eC,
      @Nullable String removedText,
      @Nullable String insertText,
      int beforeLine,
      int beforeChar) {
    String insert = (insertText == null) ? "" : insertText;
    if (removedText == null) {
      EditOperators.EditOp op = new EditOperators.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine;
      op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.recordEditNoUndo(op);
      return;
    }
    if (removedText.length() > EditOperators.UNDO_TEXT_LIMIT || insert.length() > EditOperators.UNDO_TEXT_LIMIT) {
      EditOperators.EditOp op = new EditOperators.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine;
      op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.recordEditNoUndo(op);
      return;
    }
    EditOperators.EditOp op = new EditOperators.EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = removedText;
    op.insertedText = insert;
    EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(sL, sC, insert);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    editor.editOperators.recordEdit(op);
  }
  public void setSelectionInternal(int sL, int sC, int eL, int eC) {
    int startL = sL, startC = sC, endL = eL, endC = eC;
    if (comparePos(startL, startC, endL, endC) > 0) {
      int tL = startL, tC = startC;
      startL = endL;
      startC = endC;
      endL = tL;
      endC = tC;
    }
    selStartLine = startL;
    selStartChar = Math.max(0, startC);
    selEndLine = endL;
    selEndChar = Math.max(0, endC);
    hasSelection = !(selStartLine == selEndLine && selStartChar == selEndChar);
    selecting = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    updateSelectionVisibility(hasSelection);
  }

  public void setSelectionAnimationEnabled(boolean enabled) {
    if (selectionAnimationEnabled == enabled) return;
    selectionAnimationEnabled = enabled;
    if (!selectionAnimationEnabled) {
      selectionAlpha = hasSelection ? 1f : 0f;
      handleAlpha = hasSelection ? 1f : 0f;
      if (selectionFadeAnimator != null) {
        selectionFadeAnimator.cancel();
        selectionFadeAnimator = null;
      }
      editor.invalidate();
    }
  }

  private void updateSelectionVisibility(boolean nowHasSelection) {
    if (nowHasSelection == lastHasSelection) return;
    lastHasSelection = nowHasSelection;
    if (!selectionAnimationEnabled) {
      selectionAlpha = nowHasSelection ? 1f : 0f;
      handleAlpha = nowHasSelection ? 1f : 0f;
      return;
    }
    if (selectionFadeAnimator != null) {
      selectionFadeAnimator.cancel();
    }
    float start = nowHasSelection ? 0f : 1f;
    float end = nowHasSelection ? 1f : 0f;
    selectionFadeAnimator = ValueAnimator.ofFloat(start, end);
    selectionFadeAnimator.setDuration(140);
    selectionFadeAnimator.addUpdateListener(a -> {
      float v = (float) a.getAnimatedValue();
      selectionAlpha = v;
      handleAlpha = v;
      editor.invalidate();
    });
    selectionFadeAnimator.start();
  }

  public void beginLongPressSelection(int line, int ch) {
    longPressSelecting = true;
    longPressAnchorLine = Math.max(0, line);
    longPressAnchorChar = Math.max(0, ch);
  }

  public void updateLongPressSelection(int line, int ch) {
    if (!longPressSelecting) return;
    setSelectionInternal(longPressAnchorLine, longPressAnchorChar, line, ch);
    selecting = true;
  }

  public void endLongPressSelection() {
    longPressSelecting = false;
    selecting = false;
  }
  
  
  public void replaceSelectionWithText(String insertText) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    final int opToken = editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion(); // Clear suggestion when replacing selection

    if (insertText == null) insertText = "";

    if (!hasSelection) {
      if (!insertText.isEmpty()) editor.editOperators.insertTextAtCursor(insertText);
      // No selection means no large edit UI was started for it.
      editor.autoCompletion.updateSuggestion();
      return;
    }

    // Normalize selection
    int sL = selStartLine, sC = selStartChar, eL = selEndLine, eC = selEndChar;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = editor.fileIO.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > EditOperators.UNDO_TEXT_LIMIT) {
        removedText = null;
      }
    }
    int removedNewlines = editor.editOperators.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = editor.editOperators.countNewlines(insertText);

    final boolean selectAllLike = isSelectAllActive || isEntireFileSelected;
    editor.loadingCircle.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    // This is the critical fix: The "Select All" path now correctly cleans up and finalizes the UI
    // state.
    if (selectAllLike) {
      // Reset all data structures to represent an empty document.
      synchronized (editor.textRender.linesWindow) {
        editor.textRender.linesWindow.clear();
        editor.textRender.linesWindow.add("");
        editor.textRender.windowStartLine = 0;
        editor.fileIO.isEof = true;
      }
      synchronized (editor.textRender.modifiedLines) {
        editor.textRender.modifiedLines.clear();
      }
      synchronized (editor.textRender.lineWidthCache) {
        editor.textRender.lineWidthCache.clear();
      }
    editor.textRender.currentMaxWindowLineWidth = 0f;
    editor.textRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;

      // Transition to in-memory mode for cleared content.
      editor.fileIO.isFileCleared = true;
      synchronized (editor.fileIO.lineOffsetsLock) {
        editor.fileIO.lineOffsets = new long[0];
      }
      editor.fileIO.isIndexReady = false;
      editor.fileIO.isIndexBuilding = false;
      editor.fileIO.isIndexDisabled = false;
      editor.fileIO.indexDisabledPath = null;
      editor.fileIO.indexDisabledFileLength = -1L;

      // Reset cursor, selection, and scroll position.
      editor.cursor.cursorLine = 0;
      editor.cursor.cursorChar = 0;
      selStartLine = 0;
      selEndLine = 0;
      selStartChar = 0;
      selEndChar = 0;
       editor.scroll.scrollY =0;
      editor.scroll.scrollX =0;
      clearSelectionStateAfterDelete();

      // Perform insertion if replacing text.
      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (editor.textRender.linesWindow) {
          editor.textRender.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            editor.textRender.linesWindow.add(i, newLines[i]);
          }
        }
        EditOperators.CursorTarget newPos = editor.editOperators.computeCursorAfterInsert(0, 0, insertText);
        editor.cursor.cursorLine = newPos.line;
        editor.cursor.cursorChar = newPos.ch;
      }

      // Crucially, end the large edit UI and force a redraw.
      editor.wordWrap.onLineCountChanged();
      editor.loadingCircle.endLargeEditUi(true);
      editor.recalculateMaxLineWidth();
      editor.keepCursorVisibleHorizontally();
      editor.requestLayout(); // Request layout to update gutter width after content cleared
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // same line + no '\n' => window-only fast path
    if (sL == eL && insertText.indexOf('\n') < 0) {
      editor.fileIO.ensureLineInWindow(sL, true);
      if (editor.fileIO.isWindowLoading && (sL < editor.textRender.windowStartLine || sL >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
        final String txtFinal = insertText;
        editor.post(() -> replaceSelectionWithText(txtFinal));
        return;
      }

      int local = sL - editor.textRender.windowStartLine;
      if (local >= 0 && local < editor.textRender.linesWindow.size()) {
        synchronized (editor.textRender.linesWindow) {
          String line = editor.getLineFromWindowLocal(local);
          if (line == null) line = "";

          int a = Math.max(0, Math.min(sC, line.length()));
          int b = Math.max(0, Math.min(eC, line.length()));
          if (b < a) {
            int t = a;
            a = b;
            b = t;
          }

          String merged = line.substring(0, a) + insertText + line.substring(b);
          editor.updateLocalLine(local, merged);
          editor.textRender.modifiedLines.put(sL, merged);

          editor.cursor.cursorLine = sL;
          editor.cursor.cursorChar = a + insertText.length();

          editor.computeWidthForLine(sL, merged);
          editor.recalculateMaxLineWidth();
        }
      }

      clearSelectionStateAfterDelete();
      editor.invalidate();
      editor.keepCursorVisibleHorizontally();
      editor.loadingCircle.endLargeEditUi(false);
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // multi-line or inserted text contains '\n'
    final EditOperators.CursorTarget target = editor.editOperators.computeCursorAfterInsert(sL, sC, insertText);

    // Optional immediate UI update if fully in window
    boolean fullyInWindow = (sL >= editor.textRender.windowStartLine) && (eL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
    if (fullyInWindow) {
      editor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
    } else {
      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = sC;
    }

    clearSelectionStateAfterDelete();
    editor.keepCursorVisibleHorizontally(); // This scrolls to the new cursor and editor.invalidates.
    editor.loadingCircle.endLargeEditUi(false);

    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      if (!fullyInWindow) {
        editor.fileIO.ensureLineInWindow(sL, true);
        editor.fileIO.ensureLineInWindow(eL, true);
        editor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      editor.autoCompletion.updateSuggestion();
      editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = editor.fileIO.sourceFile;
    // ابدأ إعادة كتابة الملف في الخلفية بدون تعطيل الواجهة وبدون دائرة تحميل.
    editor.editOperators.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    editor.autoCompletion.updateSuggestion();
    editor.editOperators.lineCountDelta += (insertedNewlines - removedNewlines);
    recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }
  
  public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
    StringBuilder sb = new StringBuilder();
    synchronized (editor.textRender.linesWindow) {
      for (int L = sL; L <= eL; L++) {
        int local = L - editor.textRender.windowStartLine;
        String ln = (local >= 0 && local < editor.textRender.linesWindow.size()) ? editor.textRender.linesWindow.get(local) : "";
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
  
  public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    // In-memory (no file backing): build from render-safe access
    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      return editor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // If the selection is fully inside the current window, prefer the window snapshot to avoid
    // stale file reads while edits are pending.
    boolean fullyInWindow = (sL >= editor.textRender.windowStartLine) && (eL < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
    if (fullyInWindow) {
      return editor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // File-backed: sequential read from start line, overriding with editor.textRender.modifiedLines when available
    try (RandomAccessFile raf = new RandomAccessFile(editor.fileIO.sourceFile, "r")) {
      long startByte;
      if (editor.fileIO.isIndexReady) {
        synchronized (editor.fileIO.lineOffsetsLock) {
          if (sL >= 0 && sL < editor.fileIO.lineOffsets.length) startByte = editor.fileIO.lineOffsets[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = editor.editOperators.findLineStartByteByScanning(raf, sL);
      }

      raf.seek(startByte);
      try (BufferedReader br =
          new BufferedReader(
              new java.io.InputStreamReader(new FileInputStream(raf.getFD()), editor.fileIO.fileCharset), 8192)) {

        StringBuilder sb = new StringBuilder();
        for (int L = sL; L <= eL; L++) {
          String fileLine = br.readLine();
          if (fileLine == null) fileLine = "";

          String ln;
          synchronized (editor.textRender.modifiedLines) {
            ln = editor.textRender.modifiedLines.containsKey(L) ? editor.textRender.modifiedLines.get(L) : fileLine;
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

  // ========================================================================
  // Selection Helper Methods
  // ========================================================================

  /**
   * Check if copy/cut should be hidden for current selection
   */
  public boolean shouldHideCopyCutForSelection() {
    if (!hasSelection) return true;

    int sL = selStartLine, eL = selEndLine;
    if (sL > eL) {
      int t = sL;
      sL = eL;
      eL = t;
    }
    long lines = (long) eL - (long) sL + 1L;
    return lines > copyCutMaxLines;
  }

  /**
   * Paste text from clipboard
   */
  public void pasteFromClipboard() {
    editor.fileIO.invalidatePendingIOForEdit();
    editor.editOperators.editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    android.content.ClipboardManager cm =
        (android.content.ClipboardManager) editor.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip()) return;
    android.content.ClipData cd = cm.getPrimaryClip();
    if (cd == null || cd.getItemCount() == 0) return;
    CharSequence txt = cd.getItemAt(0).coerceToText(editor.getContext());
    if (txt == null) return;
    editor.editOperators.insertTextAtCursor(txt.toString());
    editor.autoCompletion.updateSuggestion();
  }

}
