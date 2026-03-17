package com.yn.sodiumeditor;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileInputStream;

import android.graphics.Paint;
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
  public int selectionHandleColor = 0xFF33B5E5;
  public int selectionHighlightColor = 0x8033B5E5;
  
  
  private final SodiumEditor sodiumeditor;
  private final Cursor cursor;
public final RectF selectionRectTmp = new RectF();
  public final Path selectionPathTmp = new Path();
  public final float[] selectionRadiiTmp = new float[8];
  
public boolean isLineNumberSelecting = false;
  public int lineNumberSelectAnchorLine = -1;
  
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
  
  public Selection(SodiumEditor sodiumeditor, Cursor cursor) {
    this.sodiumeditor = sodiumeditor;
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
    
    // Sync with cursor
    selStartLine = sodiumeditor.cursor.cursorLine;
    selStartChar = sodiumeditor.cursor.cursorChar;
    selEndLine = sodiumeditor.cursor.cursorLine;
    selEndChar = sodiumeditor.cursor.cursorChar;
  }

  /**
   * Select all text
   */
  public void selectAll() {
    sodiumeditor.clearActiveSuggestion(); // Clear suggestion when selecting all
    final boolean keyboardWasVisible = sodiumeditor.keyboardHeight > 0;
    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      // Free the IO thread from wrap rebuilds so select-all can jump to end quickly.
      int widthPx = Math.max(1, Math.round(sodiumeditor.getWrapWidth()));
      if (sodiumeditor.isWrapMetricsUsableForWindow(widthPx)) {
        sodiumeditor.cancelWrapWorkForPriority();
      }
    }
    sodiumeditor.setDisable(true);
    sodiumeditor.showLoadingCircle(true);

    isSelectAllActive = true;
    isEntireFileSelected = true;
    hasSelection = true;

    selStartLine = 0;
    selStartChar = 0;
    sodiumeditor.popup.hidePopup();

    // =========================
    // In-memory mode (no file):
    // - Happens after "select all -> delete" (file cleared), then user types new text
    // - Also covers scenarios where content is edited but not persisted to disk
    // =========================
    if (sodiumeditor.sourceFile == null || sodiumeditor.isFileCleared) {
      synchronized (sodiumeditor.linesWindow) {
        if (sodiumeditor.linesWindow.isEmpty()) sodiumeditor.linesWindow.add("");
        // With no file backing, treat current window as the whole document.
        if (sodiumeditor.windowStartLine != 0) sodiumeditor.windowStartLine = 0;
        sodiumeditor.isEof = true;
      }

      selEndLine = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
      String lastLineText = sodiumeditor.getLineTextForRender(selEndLine);
      selEndChar = lastLineText.length();
      sodiumeditor.cursor.cursorLine = selEndLine;
      sodiumeditor.cursor.cursorChar = selEndChar;

      sodiumeditor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

      sodiumeditor.setDisable(false);
      sodiumeditor.showLoadingCircle(false);
      sodiumeditor.invalidate();
      sodiumeditor.requestFocus();
      sodiumeditor.popup.showPopupAtSelection();

      sodiumeditor.post(
          () -> {
            sodiumeditor.requestFocus();
            if (keyboardWasVisible) sodiumeditor.showKeyboard();
            InputMethodManager imm = (InputMethodManager) sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(sodiumeditor);
          });
      return;
    }

    // If we're already at EOF, we can select to the current visible logical end
    // without waiting for the index (important when user appended lines after EOF).
    if (sodiumeditor.isEof) {
      int windowLast = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
      selEndLine = windowLast;
      String lastLineText = sodiumeditor.getLineTextForRender(windowLast);
      selEndChar = lastLineText.length();
      sodiumeditor.cursor.cursorLine = windowLast;
      sodiumeditor.cursor.cursorChar = selEndChar;

      sodiumeditor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

      sodiumeditor.setDisable(false);
      sodiumeditor.showLoadingCircle(false);
      sodiumeditor.invalidate();
      sodiumeditor.requestFocus();
      sodiumeditor.popup.showPopupAtSelection();

      sodiumeditor.post(
          () -> {
            sodiumeditor.requestFocus();
            if (keyboardWasVisible) sodiumeditor.showKeyboard();
            InputMethodManager imm = (InputMethodManager) sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(sodiumeditor);
          });
      return;
    }

    // الأفضل: لو index جاهز نروح نهاية الملف بدقة (بدون قفزة غلط)
    Runnable goToEndUsingIndex =
        () -> {
          if (!sodiumeditor.isIndexReady || sodiumeditor.sourceFile == null) return;

          int fileLastLine;
          synchronized (sodiumeditor.lineOffsetsLock) {
            fileLastLine = Math.max(0, sodiumeditor.lineOffsets.length - 1);
          }

          // If the current window actually goes beyond file end (due to appended in-memory lines),
          // prefer the window end and DO NOT reload from file (reload would drop the appended
          // lines).
          if (sodiumeditor.isEof) {
            int windowLast = Math.max(0, sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1);
            if (windowLast > fileLastLine) {
              selEndLine = windowLast;
              String lastLineText = sodiumeditor.getLineTextForRender(windowLast);
              selEndChar = lastLineText.length();
              sodiumeditor.cursor.cursorLine = windowLast;
              sodiumeditor.cursor.cursorChar = selEndChar;

              sodiumeditor.scroll.scrollToLineFastForSelectAll(windowLast, selEndChar);

              sodiumeditor.setDisable(false);
              sodiumeditor.showLoadingCircle(false);
              sodiumeditor.invalidate();
              sodiumeditor.requestFocus();
              sodiumeditor.popup.showPopupAtSelection();

              sodiumeditor.post(
                  () -> {
                    sodiumeditor.requestFocus();
                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                    InputMethodManager imm = (InputMethodManager) sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(sodiumeditor);
                  });
              return;
            }
          }

          selEndLine = fileLastLine;

          int targetStart = Math.max(0, fileLastLine - sodiumeditor.prefetchLines);

          sodiumeditor.loadWindowAround(
              targetStart,
              () ->
                  sodiumeditor.post(
                      () -> {
                        String lastLineText = sodiumeditor.getLineTextForRender(fileLastLine);
                        selEndChar = lastLineText.length();
                        sodiumeditor.cursor.cursorLine = fileLastLine;
                        sodiumeditor.cursor.cursorChar = selEndChar;

                        sodiumeditor.scroll.scrollToLineFastForSelectAll(fileLastLine, selEndChar);

                        sodiumeditor.setDisable(false);
                        sodiumeditor.showLoadingCircle(false);
                        sodiumeditor.invalidate();
                        sodiumeditor.requestFocus();
                        sodiumeditor.popup.showPopupAtSelection();

                        sodiumeditor.post(
                            () -> {
                              sodiumeditor.requestFocus();
                              if (keyboardWasVisible) sodiumeditor.showKeyboard();
                              InputMethodManager imm = (InputMethodManager) sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                              if (imm != null) imm.restartInput(sodiumeditor);
                            });
                      }));
        };

    if (sodiumeditor.isIndexReady) {
      goToEndUsingIndex.run();
      return;
    }

    // لو index مو جاهز: ابدأ بناءه ثم انتظر جاهزيته (بدل "قرب النهاية" الغلط)
    if (!sodiumeditor.isIndexBuilding && !sodiumeditor.isIndexDisabled) {
      sodiumeditor.ioHandler.post(sodiumeditor::buildFileIndex);
    }

    // نحدد selEndLine مؤقتاً للهايلايت بواسطة countTotalLines (سريع)
    sodiumeditor.countTotalLines(
        totalLines -> {
          int lastLine = (totalLines > 0) ? totalLines - 1 : 0;
          selEndLine = Math.max(0, lastLine);

          Runnable goToEndWithoutIndex =
              () -> {
                int targetStart = Math.max(0, selEndLine - sodiumeditor.prefetchLines);
                sodiumeditor.loadWindowAround(
                    targetStart,
                    () ->
                        sodiumeditor.post(
                            () -> {
                              String lastLineText = sodiumeditor.getLineTextForRender(selEndLine);
                              selEndChar = lastLineText.length();
                              sodiumeditor.cursor.cursorLine = selEndLine;
                              sodiumeditor.cursor.cursorChar = selEndChar;

                              sodiumeditor.scroll.scrollToLineFastForSelectAll(selEndLine, selEndChar);

                              sodiumeditor.setDisable(false);
                              sodiumeditor.showLoadingCircle(false);
                              sodiumeditor.invalidate();
                              sodiumeditor.requestFocus();
                              sodiumeditor.popup.showPopupAtSelection();

                              sodiumeditor.post(
                                  () -> {
                                    sodiumeditor.requestFocus();
                                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                                    InputMethodManager imm = (InputMethodManager) sodiumeditor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.restartInput(sodiumeditor);
                                  });
                            }));
              };

          if (sodiumeditor.isIndexDisabled) {
            goToEndWithoutIndex.run();
            return;
          }

          final int ticket = sodiumeditor.editVersion.incrementAndGet();
          Runnable poll =
              new Runnable() {
                @Override
                public void run() {
                  if (ticket != sodiumeditor.editVersion.get()) return;

                  // Important: if file became unavailable (e.g. cleared and switched to memory),
                  // stop waiting to avoid infinite spinner.
                  if (sodiumeditor.sourceFile == null) {
                    sodiumeditor.setDisable(false);
                    sodiumeditor.showLoadingCircle(false);
                    sodiumeditor.invalidate();
                    sodiumeditor.popup.showPopupAtSelection();
                    if (keyboardWasVisible) sodiumeditor.showKeyboard();
                    return;
                  }

                  if (sodiumeditor.isIndexDisabled) {
                    goToEndWithoutIndex.run();
                  } else if (sodiumeditor.isIndexReady) {
                    goToEndUsingIndex.run();
                  } else {
                    sodiumeditor.caret.mainHandler.postDelayed(this, 80);
                  }
                }
              };
          sodiumeditor.caret.mainHandler.post(poll);
        });
  }


  /**
   * Select word at cursor
   */
  public void selectWordAtCursor() {
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return;
    
    int pos = Math.max(0, Math.min(sodiumeditor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return;
    if (Character.isWhitespace(line.charAt(pos))) return;
    
    int[] bounds = sodiumeditor.computeWordBounds(line, pos);
    if (bounds != null && bounds[0] != bounds[1]) {
      setSelection(sodiumeditor.cursor.cursorLine, bounds[0], sodiumeditor.cursor.cursorLine, bounds[1]);
    }
  }

  /**
   * Select line at cursor
   */
  public void selectLineAtCursor() {
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null) return;
    
    setSelection(sodiumeditor.cursor.cursorLine, 0, sodiumeditor.cursor.cursorLine, line.length());
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
      String lineText = sodiumeditor.getLineTextForRender(line);
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
        if (isQuoteChar(c) && !sodiumeditor.isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !sodiumeditor.isEscaped(line, i)) {
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
        if (c == currentQuote && !sodiumeditor.isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !sodiumeditor.isEscaped(line, i)) {
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
  public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    int[] bounds = sodiumeditor.computeWordBoundsSmart(lineText, charIndex);
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
    selStartLine = selEndLine = line;
    selStartChar = pick.start;
    selEndChar = pick.end;
    hasSelection = true;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    selecting = true;
    sodiumeditor.cursor.cursorLine = line;
    sodiumeditor.cursor.cursorChar = selEndChar;
    lastDoubleTapLine = line;
    lastDoubleTapWordStart = bounds[0];
    lastDoubleTapWordEnd = bounds[1];
    lastDoubleTapStage = nextIdx;
    return true;
  }
  public void clearSelectionStateAfterDelete() {
    hasSelection = false;
    selecting = false;
    isSelectAllActive = false;
    isEntireFileSelected = false;
    sodiumeditor.caret.resetBlink();
  }
  public void setSelectionHighlightColor(int color) {
    if (this.selectionHighlightColor == color) return;
    this.selectionHighlightColor = color;
    if (hasSelection) sodiumeditor.invalidate();
  }
  public void replaceSelectionText(String text) {
    replaceSelectionWithText(text == null ? "" : text);
  }
  public void restoreSelection(int sL, int sC, int eL, int eC, int cursorLine, int cursorChar) {
    setSelectionInternal(sL, sC, eL, eC);
    int targetLine = Math.max(0, cursorLine);
    int targetChar = Math.max(0, sodiumeditor.cursor.cursorChar);
    cursorLine = targetLine;
    if (cursorLine >= sodiumeditor.windowStartLine
        && cursorLine < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size()) {
      String lineText = sodiumeditor.getLineTextForRender(cursorLine);
      this.sodiumeditor.cursor.cursorChar = Math.max(0, Math.min(targetChar, lineText.length()));
    } else {
      this.sodiumeditor.cursor.cursorChar = targetChar;
    }
    sodiumeditor.caret.resetBlink();
    sodiumeditor.invalidate();
  }
  
  public int clampLineForSelection(int line) {
    if (line < 0) return 0;
    if (sodiumeditor.isEof) {
      int last = sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size() - 1;
      if (last < 0) return 0;
      return Math.min(line, last);
    }
    return line;
  }

  public boolean isLineSelectable(int line) {
    sodiumeditor.ensureLineInWindow(line, true);
    String ln = sodiumeditor.getLineTextForRender(line);
    return ln != null && ln.length() > 0;
  }
  public void copyOrCutSelection(final boolean cut) {
    if (!hasSelection) return;
    sodiumeditor.clearActiveSuggestion(); // Clear suggestion when copying/cutting

    // Hidden/disabled for huge selections (requested behavior)
    if (sodiumeditor.shouldHideCopyCutForSelection()) return;

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
        (fsL >= sodiumeditor.windowStartLine) && (feL < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size());
    if (fullyInWindow) {
      String text = buildSelectedTextFromWindow(fsL, fsC, feL, feC, copyCutMaxChars);
      ClipboardManager cm = (ClipboardManager) sodiumeditor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));
      if (cut) {
        deleteSelection();
      }
      return;
    }

    if (sodiumeditor.wordWrap.isWordWrapEnabled) {
      sodiumeditor.cancelWrapWorkForPriority();
    }

    sodiumeditor.ioHandler.post(
        () -> {
          final String text = buildSelectedTextBlocking(fsL, fsC, feL, feC, copyCutMaxChars);
          sodiumeditor.post(
              () -> {
                ClipboardManager cm = (ClipboardManager) sodiumeditor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null)
                  cm.setPrimaryClip(ClipData.newPlainText("text", (text == null) ? "" : text));

                if (cut) {
                  deleteSelection();
                }
              });
        });
  }
  public void deleteSelection() {
    sodiumeditor.clearActiveSuggestion(); // Clear suggestion when deleting selection
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
      SodiumEditor.EditOp op = new SodiumEditor.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      SodiumEditor.CursorTarget insertedEnd = sodiumeditor.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = sodiumeditor.cursor.cursorLine;
      op.cursorCharAfter = sodiumeditor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      sodiumeditor.recordEditNoUndo(op);
      return;
    }
    if (removedText.length() > SodiumEditor.UNDO_TEXT_LIMIT || insert.length() > SodiumEditor.UNDO_TEXT_LIMIT) {
      SodiumEditor.EditOp op = new SodiumEditor.EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      SodiumEditor.CursorTarget insertedEnd = sodiumeditor.computeCursorAfterInsert(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = sodiumeditor.cursor.cursorLine;
      op.cursorCharAfter = sodiumeditor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      sodiumeditor.recordEditNoUndo(op);
      return;
    }
    SodiumEditor.EditOp op = new SodiumEditor.EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = removedText;
    op.insertedText = insert;
    SodiumEditor.CursorTarget insertedEnd = sodiumeditor.computeCursorAfterInsert(sL, sC, insert);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = sodiumeditor.cursor.cursorLine;
    op.cursorCharAfter = sodiumeditor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    sodiumeditor.recordEdit(op);
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
  }
  
  
  public void replaceSelectionWithText(String insertText) {
    if (sodiumeditor.isReadOnly) return;
    sodiumeditor.invalidatePendingIOForEdit();
    final int opToken = sodiumeditor.editVersion.incrementAndGet();
    sodiumeditor.clearActiveSuggestion(); // Clear suggestion when replacing selection

    if (insertText == null) insertText = "";

    if (!hasSelection) {
      if (!insertText.isEmpty()) sodiumeditor.insertTextAtCursor(insertText);
      // No selection means no large edit UI was started for it.
      sodiumeditor.updateSuggestion();
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
    final int beforeLine = sodiumeditor.cursor.cursorLine;
    final int beforeChar = sodiumeditor.cursor.cursorChar;
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = sodiumeditor.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > SodiumEditor.UNDO_TEXT_LIMIT) {
        removedText = null;
      }
    }
    int removedNewlines = sodiumeditor.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = sodiumeditor.countNewlines(insertText);

    final boolean selectAllLike = isSelectAllActive || isEntireFileSelected;
    sodiumeditor.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    // This is the critical fix: The "Select All" path now correctly cleans up and finalizes the UI
    // state.
    if (selectAllLike) {
      // Reset all data structures to represent an empty document.
      synchronized (sodiumeditor.linesWindow) {
        sodiumeditor.linesWindow.clear();
        sodiumeditor.linesWindow.add("");
        sodiumeditor.windowStartLine = 0;
        sodiumeditor.isEof = true;
      }
      synchronized (sodiumeditor.modifiedLines) {
        sodiumeditor.modifiedLines.clear();
      }
      synchronized (sodiumeditor.lineWidthCache) {
        sodiumeditor.lineWidthCache.clear();
      }
    sodiumeditor.currentMaxWindowLineWidth = 0f;
    sodiumeditor.globalMaxLineWidth = 0f;
    sodiumeditor.scroll.maxLineWidthForScroll = 0f;
    sodiumeditor.scroll.maxTextStartXForScroll = 0f;
    sodiumeditor.scroll.maxScrollXForScroll = 0f;

      // Transition to in-memory mode for cleared content.
      sodiumeditor.isFileCleared = true;
      synchronized (sodiumeditor.lineOffsetsLock) {
        sodiumeditor.lineOffsets = new long[0];
      }
      sodiumeditor.isIndexReady = false;
      sodiumeditor.isIndexBuilding = false;
      sodiumeditor.isIndexDisabled = false;
      sodiumeditor.indexDisabledPath = null;
      sodiumeditor.indexDisabledFileLength = -1L;

      // Reset cursor, selection, and scroll position.
      sodiumeditor.cursor.cursorLine = 0;
      sodiumeditor.cursor.cursorChar = 0;
      selStartLine = 0;
      selEndLine = 0;
      selStartChar = 0;
      selEndChar = 0;
       sodiumeditor.scroll.scrollY =0;
      sodiumeditor.scroll.scrollX =0;
      clearSelectionStateAfterDelete();

      // Perform insertion if replacing text.
      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (sodiumeditor.linesWindow) {
          sodiumeditor.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            sodiumeditor.linesWindow.add(i, newLines[i]);
          }
        }
        SodiumEditor.CursorTarget newPos = sodiumeditor.computeCursorAfterInsert(0, 0, insertText);
        sodiumeditor.cursor.cursorLine = newPos.line;
        sodiumeditor.cursor.cursorChar = newPos.ch;
      }

      // Crucially, end the large edit UI and force a redraw.
      sodiumeditor.onLineCountChanged();
      sodiumeditor.endLargeEditUi(true);
      sodiumeditor.recalculateMaxLineWidth();
      sodiumeditor.keepCursorVisibleHorizontally();
      sodiumeditor.requestLayout(); // Request layout to update gutter width after content cleared
      sodiumeditor.updateSuggestion();
      sodiumeditor.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // same line + no '\n' => window-only fast path
    if (sL == eL && insertText.indexOf('\n') < 0) {
      sodiumeditor.ensureLineInWindow(sL, true);
      if (sodiumeditor.isWindowLoading && (sL < sodiumeditor.windowStartLine || sL >= sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size())) {
        final String txtFinal = insertText;
        sodiumeditor.post(() -> replaceSelectionWithText(txtFinal));
        return;
      }

      int local = sL - sodiumeditor.windowStartLine;
      if (local >= 0 && local < sodiumeditor.linesWindow.size()) {
        synchronized (sodiumeditor.linesWindow) {
          String line = sodiumeditor.getLineFromWindowLocal(local);
          if (line == null) line = "";

          int a = Math.max(0, Math.min(sC, line.length()));
          int b = Math.max(0, Math.min(eC, line.length()));
          if (b < a) {
            int t = a;
            a = b;
            b = t;
          }

          String merged = line.substring(0, a) + insertText + line.substring(b);
          sodiumeditor.updateLocalLine(local, merged);
          sodiumeditor.modifiedLines.put(sL, merged);

          sodiumeditor.cursor.cursorLine = sL;
          sodiumeditor.cursor.cursorChar = a + insertText.length();

          sodiumeditor.computeWidthForLine(sL, merged);
          sodiumeditor.recalculateMaxLineWidth();
        }
      }

      clearSelectionStateAfterDelete();
      sodiumeditor.invalidate();
      sodiumeditor.keepCursorVisibleHorizontally();
      sodiumeditor.endLargeEditUi(false);
      sodiumeditor.updateSuggestion();
      sodiumeditor.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    // multi-line or inserted text contains '\n'
    final SodiumEditor.CursorTarget target = sodiumeditor.computeCursorAfterInsert(sL, sC, insertText);

    // Optional immediate UI update if fully in window
    boolean fullyInWindow = (sL >= sodiumeditor.windowStartLine) && (eL < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size());
    if (fullyInWindow) {
      sodiumeditor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
    } else {
      sodiumeditor.cursor.cursorLine = sL;
      sodiumeditor.cursor.cursorChar = sC;
    }

    clearSelectionStateAfterDelete();
    sodiumeditor.keepCursorVisibleHorizontally(); // This scrolls to the new cursor and sodiumeditor.invalidates.
    sodiumeditor.endLargeEditUi(false);

    if (sodiumeditor.sourceFile == null || sodiumeditor.isFileCleared) {
      if (!fullyInWindow) {
        sodiumeditor.ensureLineInWindow(sL, true);
        sodiumeditor.ensureLineInWindow(eL, true);
        sodiumeditor.applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target);
      }
      sodiumeditor.updateSuggestion();
      sodiumeditor.lineCountDelta += (insertedNewlines - removedNewlines);
      recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
      return;
    }

    final File inFile = sodiumeditor.sourceFile;
    // ابدأ إعادة كتابة الملف في الخلفية بدون تعطيل الواجهة وبدون دائرة تحميل.
    sodiumeditor.rewriteReplaceRangeAsync(opToken, inFile, sL, sC, eL, eC, insertText, target, false);
    sodiumeditor.updateSuggestion();
    sodiumeditor.lineCountDelta += (insertedNewlines - removedNewlines);
    recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
  }
  
  public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
    StringBuilder sb = new StringBuilder();
    synchronized (sodiumeditor.linesWindow) {
      for (int L = sL; L <= eL; L++) {
        int local = L - sodiumeditor.windowStartLine;
        String ln = (local >= 0 && local < sodiumeditor.linesWindow.size()) ? sodiumeditor.linesWindow.get(local) : "";
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
    if (sodiumeditor.sourceFile == null || sodiumeditor.isFileCleared) {
      return sodiumeditor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // If the selection is fully inside the current window, prefer the window snapshot to avoid
    // stale file reads while edits are pending.
    boolean fullyInWindow = (sL >= sodiumeditor.windowStartLine) && (eL < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size());
    if (fullyInWindow) {
      return sodiumeditor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // File-backed: sequential read from start line, overriding with sodiumeditor.modifiedLines when available
    try (RandomAccessFile raf = new RandomAccessFile(sodiumeditor.sourceFile, "r")) {
      long startByte;
      if (sodiumeditor.isIndexReady) {
        synchronized (sodiumeditor.lineOffsetsLock) {
          if (sL >= 0 && sL < sodiumeditor.lineOffsets.length) startByte = sodiumeditor.lineOffsets[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = sodiumeditor.findLineStartByteByScanning(raf, sL);
      }

      raf.seek(startByte);
      try (BufferedReader br =
          new BufferedReader(
              new java.io.InputStreamReader(new FileInputStream(raf.getFD()), sodiumeditor.fileCharset), 8192)) {

        StringBuilder sb = new StringBuilder();
        for (int L = sL; L <= eL; L++) {
          String fileLine = br.readLine();
          if (fileLine == null) fileLine = "";

          String ln;
          synchronized (sodiumeditor.modifiedLines) {
            ln = sodiumeditor.modifiedLines.containsKey(L) ? sodiumeditor.modifiedLines.get(L) : fileLine;
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

}
