package com.yn.sodiumeditor.utils;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.BracketGuideState;
import com.yn.sodiumeditor.core.BracketGuideToken;
import com.yn.sodiumeditor.core.Highlite;
import com.yn.sodiumeditor.renderer.draw.BracketGuideDraw;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans lines for bracket guide tokens and span calculation.
 */
public class BracketGuideScanner {
  private final SodiumEditor editor;
  private final BracketGuideDraw bracketGuideDraw;

  // Span stack for building (moved from BracketGuideSpanCache to avoid circular dependency)
  public static final class BracketSpanStack {
    int[] columns = new int[32];
    int[] startLines = new int[32];
    int[] openLines = new int[32];
    char[] brackets = new char[32];
    public int size = 0;

    public void clear() { size = 0; }

    public void push(int column, int guideStartLine, int openLine, char bracket) {
      if (size >= columns.length) {
        int newCap = Math.max(32, size * 2);
        int[] newCols = new int[newCap];
        int[] newStarts = new int[newCap];
        int[] newOpens = new int[newCap];
        char[] newBrackets = new char[newCap];
        System.arraycopy(columns, 0, newCols, 0, size);
        System.arraycopy(startLines, 0, newStarts, 0, size);
        System.arraycopy(openLines, 0, newOpens, 0, size);
        System.arraycopy(brackets, 0, newBrackets, 0, size);
        columns = newCols;
        startLines = newStarts;
        openLines = newOpens;
        brackets = newBrackets;
      }
      columns[size] = column;
      startLines[size] = guideStartLine;
      openLines[size] = openLine;
      brackets[size] = bracket;
      size++;
    }

    public void pop() { if (size > 0) size--; }

    public int topColumn() { return columns[size - 1]; }
    public int topStartLine() { return startLines[size - 1]; }
    public int topOpenLine() { return openLines[size - 1]; }
    public char topBracket() { return brackets[size - 1]; }

    public int findNearestOpenParenIndex() {
      for (int i = size - 1; i >= 0; i--) {
        char b = brackets[i];
        if (b == '(' || b == '[') {
          return i;
        }
      }
      return -1;
    }
  }

  // Scan state for span building
  public static final class BracketSpanScanState {
    public boolean inBlockComment;
    public int stringState;
    public final BracketSpanStack stack = new BracketSpanStack();
    public boolean pendingParen;
    public int pendingParenOpenLine = -1;
    public int pendingParenCloseLine = -1;
    public int pendingParenColumn = -1;
  }

  public BracketGuideScanner(SodiumEditor editor, BracketGuideDraw bracketGuideDraw) {
    this.editor = editor;
    this.bracketGuideDraw = bracketGuideDraw;
  }

  /**
   * Updates bracket guide state for a line.
   */
  public List<BracketGuideToken> updateBracketGuideStateForLine(
      String line, int globalLine, BracketGuideState state) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    if (state.stringState != 0 && !editor.highlite.isMultiLineStringsEnabled && state.stringState != Highlite.STRING_STATE_TRIPLE) {
      state.stringState = 0;
    }

    List<BracketGuideToken> tokensToDraw = getGuideTokensFromStack(state.stack);

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = SodiumEditor.findBlockCommentEnd(line, i);
        if (end < 0) break;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) {
          i = length;
          break;
        }
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (editor.highlite.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (editor.highlite.isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !Highlite.isTokenEscaped(line, i)) {
        int end = SodiumEditor.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          break;
        }
        i = end + 2;
        continue;
      }

      if (editor.highlite.isTripleQuoteStart(line, i) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (editor.highlite.isTripleQuoteStringsEnabled) {
            state.stringState = SodiumEditor.STRING_STATE_TRIPLE;
          }
          break;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (editor.highlite.isMultiLineStringsEnabled) {
            state.stringState = editor.getStringStateForDelimiter(c);
          }
          break;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') && !Highlite.isEscaped(line, i)) {
        if (c == '{' || c == '(' || c == '[') {
          int column = (c == '{') ? editor.getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace) : i;
          float x = bracketGuideDraw.getGuideX(line, column, globalLine);
          state.stack.push(new BracketGuideToken(column, x, c));
        } else {
          char open = (c == '}') ? '{' : (c == ')' ? '(' : '[');
          // Only pop if the top of stack matches
          if (!state.stack.isEmpty() && state.stack.peek().bracket == open) {
            state.stack.pop();
          }
        }
      }

      i++;
    }

    return tokensToDraw;
  }

  /**
   * Scans a line for spans (used by span cache).
   */
  public void scanLineForSpans(String line, int globalLine, BracketSpanScanState state, SpanCollector collector) {
    if (line == null) line = "";
    int length = line.length();
    int firstNonSpace = editor.getFirstNonSpaceIndex(line);

    if (state.stringState != 0 && !editor.highlite.isMultiLineStringsEnabled && state.stringState != Highlite.STRING_STATE_TRIPLE) {
      state.stringState = 0;
    }

    int i = 0;
    boolean inLineComment = false;

    while (i < length) {
      if (inLineComment) break;

      if (state.inBlockComment) {
        int end = SodiumEditor.findBlockCommentEnd(line, i);
        if (end < 0) break;
        i = end + 2;
        state.inBlockComment = false;
        continue;
      }

      if (state.stringState != 0) {
        SodiumEditor.StringEndResult endResult = editor.findStringEndForState(line, i, state.stringState);
        if (!endResult.found) {
          i = length;
          break;
        }
        i = endResult.endIndex;
        state.stringState = 0;
        continue;
      }

      if (editor.highlite.isLineCommentStart(line, i)) {
        inLineComment = true;
        break;
      }

      if (editor.highlite.isBlockCommentsEnabled
          && i + 1 < length
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !Highlite.isTokenEscaped(line, i)) {
        int end = SodiumEditor.findBlockCommentEnd(line, i + 2);
        if (end < 0) {
          state.inBlockComment = true;
          break;
        }
        i = end + 2;
        continue;
      }

      if (editor.highlite.isTripleQuoteStart(line, i) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findTripleQuoteEnd(line, i + 3);
        if (end < 0) {
          if (editor.highlite.isTripleQuoteStringsEnabled) {
            state.stringState = SodiumEditor.STRING_STATE_TRIPLE;
          }
          break;
        }
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (editor.highlite.isStringDelimiter(c) && !Highlite.isEscaped(line, i)) {
        int end = Highlite.findStringEnd(line, i + 1, c);
        if (end < 0) {
          if (editor.highlite.isMultiLineStringsEnabled) {
            state.stringState = editor.getStringStateForDelimiter(c);
          }
          break;
        }
        i = end + 1;
        continue;
      }

      if ((c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') && !Highlite.isEscaped(line, i)) {
        if (state.pendingParen && c != '{') {
          int spanStart = state.pendingParenOpenLine + 1;
          int spanEnd = state.pendingParenCloseLine - 1;
          if (spanStart <= spanEnd) {
            collector.add(state.pendingParenColumn, spanStart, spanEnd, '(');
          }
          state.pendingParen = false;
        }
        if (c == '{' || c == '(' || c == '[') {
          int column;
          if (c == '{') {
            column = editor.getBraceGuideColumnForLine(line, globalLine, i, firstNonSpace);
          } else {
            column = (firstNonSpace >= 0) ? firstNonSpace : i;
          }
          int openLine = globalLine;
          int guideStartLine;
          if (c == '{') {
            if (state.pendingParen) {
              guideStartLine = state.pendingParenOpenLine + 1;
              column = (state.pendingParenColumn >= 0) ? state.pendingParenColumn : column;
              state.pendingParen = false;
            } else {
              guideStartLine = globalLine + 1;
            }
          } else {
            guideStartLine = globalLine + 1;
          }
          state.stack.push(column, guideStartLine, openLine, c);
        } else {
          char open = (c == '}') ? '{' : (c == ')' ? '(' : '[');
          if (state.stack.size > 0 && state.stack.topBracket() == open) {
            int column = state.stack.topColumn();
            int guideStart = state.stack.topStartLine();
            int openLine = state.stack.topOpenLine();
            state.stack.pop();
            if (open == '(') {
              state.pendingParen = true;
              state.pendingParenOpenLine = openLine;
              state.pendingParenCloseLine = globalLine;
              state.pendingParenColumn = column;
            } else {
              int spanStart = guideStart;
              int spanEnd = globalLine - 1;
              if (spanStart <= spanEnd) {
                collector.add(column, spanStart, spanEnd, open);
              }
            }
          }
        }
      }

      i++;
    }
  }

  /**
   * Gets guide tokens from stack.
   */
  public static List<BracketGuideToken> getGuideTokensFromStack(
      java.util.ArrayDeque<BracketGuideToken> stack) {
    List<BracketGuideToken> tokens = new ArrayList<>();
    for (BracketGuideToken token : stack) {
      tokens.add(token);
    }
    return tokens;
  }

  /**
   * Copy bracket guide state.
   */
  public static BracketGuideState copyState(BracketGuideState src) {
    BracketGuideState out = new BracketGuideState(src.inBlockComment, src.stringState);
    for (BracketGuideToken token : src.stack) {
      out.stack.addLast(new BracketGuideToken(token.column, 0f, token.bracket));
    }
    return out;
  }

  /**
   * Gets line text for guide scanning.
   */
  public String getLineTextForGuideScan(
      int line, java.util.Map<Integer, String> directLines, java.io.RandomAccessFile raf) {
    if (directLines != null) {
      String direct = directLines.get(line);
      if (direct != null) return direct;
    }
    String mod = editor.textRender.modifiedLines.get(line);
    if (mod != null) return mod;
    int winStart = editor.textRender.windowStartLine;
    int winEnd = winStart + editor.textRender.linesWindow.size();
    if (line >= winStart && line < winEnd) {
      String w = editor.getLineFromWindowLocal(line - winStart);
      if (w != null) return w;
    }
    if (raf != null && editor.fileIO.isIndexReady) {
      long offset;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (line < 0 || line >= editor.fileIO.lineOffsets.length) return "";
        offset = editor.fileIO.lineOffsets[line];
      }
      try {
        return editor.fileIO.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return "";
      }
    }
    return "";
  }

  /**
   * Span collector for building span cache.
   */
  public static final class SpanCollector {
    public int[] columns;
    public int[] startLines;
    public int[] endLines;
    public char[] brackets;
    public int count;

    public SpanCollector(int initialCap) {
      int cap = Math.max(32, initialCap);
      columns = new int[cap];
      startLines = new int[cap];
      endLines = new int[cap];
      brackets = new char[cap];
      count = 0;
    }

    public void add(int column, int startLine, int endLine, char bracket) {
      if (startLine > endLine) return;
      if (count >= columns.length) {
        int newCap = columns.length * 2;
        columns = java.util.Arrays.copyOf(columns, newCap);
        startLines = java.util.Arrays.copyOf(startLines, newCap);
        endLines = java.util.Arrays.copyOf(endLines, newCap);
        brackets = java.util.Arrays.copyOf(brackets, newCap);
      }
      columns[count] = column;
      startLines[count] = startLine;
      endLines[count] = endLine;
      brackets[count] = bracket;
      count++;
    }
  }
}
