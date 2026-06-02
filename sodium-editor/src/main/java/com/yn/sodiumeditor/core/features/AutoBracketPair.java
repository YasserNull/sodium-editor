package com.yn.sodiumeditor.core.features; 
import com.yn.sodiumeditor.SodiumEditor;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Manages automatic bracket/quote pairing for the SodiumEditor.
 * Automatically inserts closing brackets/quotes when opening ones are typed.
 */
public class AutoBracketPair {

  private final SodiumEditor editor;

  // Auto-pairing state
  public boolean isAutoPairingEnabled = true;
  private static final int BALANCE_CACHE_LIMIT = 256;
  private final LinkedHashMap<Integer, BalanceInfo> balanceCache =
      new LinkedHashMap<Integer, BalanceInfo>(BALANCE_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BalanceInfo> eldest) {
          return size() > BALANCE_CACHE_LIMIT;
        }
      };
  private BalanceInfo windowBalanceCache;

  public AutoBracketPair(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables auto-pairing.
   */
  public void setAutoPairingEnabled(boolean enabled) {
    this.isAutoPairingEnabled = enabled;
  }

  /**
   * Handles auto-pairing for the given text.
   * Inserts closing bracket/quote if an opening one was typed.
   */
  public void handleAutoPairing(String text) {
    if (!isAutoPairingEnabled || text == null || text.length() != 1) return;

    char typedChar = text.charAt(0);
    String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) return;
    
    int pos = editor.cursor.cursorChar;
    int typedStart = Math.max(0, Math.min(pos - 1, ln.length()));
    char charAfter = (pos < ln.length()) ? ln.charAt(pos) : 0;

    // Smart Skip: If typed char is same as char after cursor (e.g. typing closing quote), 
    // just move cursor forward instead of inserting.
    if (typedChar == charAfter && isClosingPair(typedChar)) {
        // Check if we just inserted this pair or if it's already there
        // Actually, common IDE behavior is to just overtype if it's a closing bracket/quote
        editor.editOperators.deleteCharAtCursor(); // Remove the just-inserted duplicate
        editor.cursor.moveCursorRight(); // Move over existing
        return;
    }

    String closing = getClosingPair(typedChar);
    if (closing != null) {
      if (shouldSuppressAutoPair(ln, editor.cursor.cursorLine, pos, typedStart, typedChar, closing)) {
          return;
      }
      
      editor.editOperators.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        editor.cursor.moveCursorLeft();
      }
    }
  }
  
  private boolean shouldSuppressAutoPair(
      String line, int lineIndex, int pos, int typedStart, char opening, String closing) {
      if (line == null || closing == null || closing.isEmpty()) return false;
      if (pos < 0 || pos > line.length()) return false;
      BalanceInfo balance = getBalanceInfo(lineIndex, line);
      if (isQuotePair(opening)) {
          return balance.getQuoteCount(opening) > 0 && (balance.getQuoteCount(opening) % 2) == 0;
      }
      BalanceInfo windowBalance = getWindowBalanceInfo();
      if (opening == '*') {
          return windowBalance.unmatchedBlockClose > 0 || windowBalance.unmatchedBlockOpen == 0;
      }
      if (hasUnmatchedClosingForOpening(windowBalance, opening)) return true;
      if (!hasUnmatchedOpeningForOpening(windowBalance, opening)) return true;
      if (closing.length() == 1) {
          return pos < line.length() && line.charAt(pos) == closing.charAt(0);
      }
      return pos + closing.length() <= line.length() && line.regionMatches(pos, closing, 0, closing.length());
  }

  private boolean isQuotePair(char c) {
      return c == '"' || c == '\'' || c == '`';
  }

  private boolean hasUnmatchedClosingForOpening(BalanceInfo balance, char opening) {
      if (opening == '(') return balance.unmatchedParenClose > 0;
      if (opening == '{') return balance.unmatchedBraceClose > 0;
      if (opening == '[') return balance.unmatchedBracketClose > 0;
      return false;
  }

  private boolean hasUnmatchedOpeningForOpening(BalanceInfo balance, char opening) {
      if (opening == '(') return balance.unmatchedParenOpen > 0;
      if (opening == '{') return balance.unmatchedBraceOpen > 0;
      if (opening == '[') return balance.unmatchedBracketOpen > 0;
      return false;
  }

  private BalanceInfo getBalanceInfo(int lineIndex, String line) {
      int version = editor.editOperators.editVersion.get();
      int textHash = line != null ? line.hashCode() : 0;
      int textLength = line != null ? line.length() : 0;
      BalanceInfo cached = balanceCache.get(lineIndex);
      if (cached != null
          && cached.editVersion == version
          && cached.textHash == textHash
          && cached.textLength == textLength) {
          return cached;
      }
      BalanceInfo computed = BalanceInfo.compute(version, textHash, textLength, line);
      balanceCache.put(lineIndex, computed);
      return computed;
  }

  private BalanceInfo getWindowBalanceInfo() {
      int version = editor.editOperators.editVersion.get();
      int windowStart = editor.windowRender.windowStartLine;
      int textHash = 1;
      int textLength = 0;
      int lineCount;
      synchronized (editor.windowRender.linesWindow) {
          lineCount = editor.windowRender.linesWindow.size();
          for (int i = 0; i < lineCount; i++) {
              String line = editor.windowRender.linesWindow.get(i);
              textHash = 31 * textHash + (line != null ? line.hashCode() : 0);
              textLength += line != null ? line.length() : 0;
          }
          BalanceInfo cached = windowBalanceCache;
          if (cached != null
              && cached.editVersion == version
              && cached.windowStart == windowStart
              && cached.lineCount == lineCount
              && cached.textHash == textHash
              && cached.textLength == textLength) {
              return cached;
          }
          BalanceInfo computed = new BalanceInfo(version, textHash, textLength, windowStart, lineCount);
          for (int i = 0; i < lineCount; i++) {
              computed.scanLine(editor.windowRender.linesWindow.get(i));
          }
          windowBalanceCache = computed;
          return computed;
      }
  }

  public void clearBalanceCache() {
      balanceCache.clear();
      windowBalanceCache = null;
  }

  private static class BalanceInfo {
      final int editVersion;
      final int textHash;
      final int textLength;
      final int windowStart;
      final int lineCount;
      int doubleQuoteCount;
      int singleQuoteCount;
      int backtickQuoteCount;
      int unmatchedParenOpen;
      int unmatchedParenClose;
      int unmatchedBraceOpen;
      int unmatchedBraceClose;
      int unmatchedBracketOpen;
      int unmatchedBracketClose;
      int unmatchedBlockOpen;
      int unmatchedBlockClose;

      BalanceInfo(int editVersion, int textHash, int textLength) {
          this(editVersion, textHash, textLength, -1, 1);
      }

      BalanceInfo(int editVersion, int textHash, int textLength, int windowStart, int lineCount) {
          this.editVersion = editVersion;
          this.textHash = textHash;
          this.textLength = textLength;
          this.windowStart = windowStart;
          this.lineCount = lineCount;
      }

      int getQuoteCount(char quote) {
          if (quote == '"') return doubleQuoteCount;
          if (quote == '\'') return singleQuoteCount;
          if (quote == '`') return backtickQuoteCount;
          return 0;
      }

      static BalanceInfo compute(int editVersion, int textHash, int textLength, String line) {
          BalanceInfo info = new BalanceInfo(editVersion, textHash, textLength);
          info.scanLine(line);
          return info;
      }

      void scanLine(String line) {
          if (line == null || line.isEmpty()) return;
          for (int i = 0; i < line.length(); i++) {
              char c = line.charAt(i);
              if (isEscaped(line, i)) continue;
              if (i + 1 < line.length()) {
                  char next = line.charAt(i + 1);
                  if (c == '/' && next == '*') {
                      unmatchedBlockOpen++;
                      i++;
                      continue;
                  }
                  if (c == '*' && next == '/') {
                      if (unmatchedBlockOpen > 0) unmatchedBlockOpen--;
                      else unmatchedBlockClose++;
                      i++;
                      continue;
                  }
              }
              if (c == '"') {
                  doubleQuoteCount++;
                  continue;
              }
              if (c == '\'') {
                  singleQuoteCount++;
                  continue;
              }
              if (c == '`') {
                  backtickQuoteCount++;
                  continue;
              }
              switch (c) {
                  case '(':
                      unmatchedParenOpen++;
                      break;
                  case ')':
                      if (unmatchedParenOpen > 0) unmatchedParenOpen--;
                      else unmatchedParenClose++;
                      break;
                  case '{':
                      unmatchedBraceOpen++;
                      break;
                  case '}':
                      if (unmatchedBraceOpen > 0) unmatchedBraceOpen--;
                      else unmatchedBraceClose++;
                      break;
                  case '[':
                      unmatchedBracketOpen++;
                      break;
                  case ']':
                      if (unmatchedBracketOpen > 0) unmatchedBracketOpen--;
                      else unmatchedBracketClose++;
                      break;
              }
          }
      }

      private static boolean isEscaped(String line, int index) {
          int backslashes = 0;
          for (int i = index - 1; i >= 0; i--) {
              if (line.charAt(i) != '\\') break;
              backslashes++;
          }
          return (backslashes % 2) == 1;
      }
  }

  /**
   * Gets the closing pair for the given character.
   */
  public String getClosingPair(char c) {
    if (c == '(') return ")";
    if (c == '{') return "}";
    if (c == '[') return "]";
    if (c == '"') return "\"";
    if (c == '\'') return "'";
    if (c == '`') return "`";
    
    if (c == '*') {
      // Check for /* comment start
      if (editor.cursor.cursorChar >= 2) {
        String ln = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
        if (ln != null && ln.length() >= editor.cursor.cursorChar && 
            ln.charAt(editor.cursor.cursorChar - 2) == '/') {
          return "*/";
        }
      }
    }
    
    return null;
  }

  /**
   * Checks if a character is an opening bracket/quote.
   */
  public boolean isOpeningPair(char c) {
    return getClosingPair(c) != null;
  }

  /**
   * Checks if a character is a closing bracket/quote.
   */
  public boolean isClosingPair(char c) {
    return c == ')' || c == '}' || c == ']' || c == '"' || c == '\'' || c == '`';
  }

  /**
   * Gets the matching pair for a bracket/quote character.
   * Returns the opening character if given a closing one, and vice versa.
   */
  public char getMatchingPair(char c) {
    switch (c) {
      case '(': return ')';
      case ')': return '(';
      case '{': return '}';
      case '}': return '{';
      case '[': return ']';
      case ']': return '[';
      case '"': return '"';
      case '\'': return '\'';
      case '`': return '`';
      case '*': return '*';
      default: return c;
    }
  }

  /**
   * Inserts a bracket pair at cursor position and places cursor between them.
   */
  public void insertBracketPairAtCursor(char opening) {
    String closing = getClosingPair(opening);
    if (closing == null) return;
    
    editor.editOperators.insertTextAtCursor(opening + closing);
    editor.cursor.moveCursorLeft();
  }

  /**
   * Checks if brackets are balanced in the given text.
   */
  public boolean areBracketsBalanced(String text) {
    if (text == null || text.isEmpty()) return true;
    
    int parenCount = 0;
    int braceCount = 0;
    int bracketCount = 0;
    boolean inString = false;
    boolean inChar = false;
    char stringDelim = 0;
    
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      
      // Skip escaped characters
      if (i > 0 && text.charAt(i - 1) == '\\') continue;
      
      // Track string/char literals
      if ((c == '"' || c == '\'') && !inChar) {
        if (!inString) {
          inString = true;
          stringDelim = c;
        } else if (c == stringDelim) {
          inString = false;
          stringDelim = 0;
        }
        continue;
      }
      
      // Skip brackets inside strings
      if (inString) continue;
      
      // Count brackets
      switch (c) {
        case '(': parenCount++; break;
        case ')': parenCount--; break;
        case '{': braceCount++; break;
        case '}': braceCount--; break;
        case '[': bracketCount++; break;
        case ']': bracketCount--; break;
      }
      
      // Check for negative counts (unbalanced)
      if (parenCount < 0 || braceCount < 0 || bracketCount < 0) {
        return false;
      }
    }
    
    return parenCount == 0 && braceCount == 0 && bracketCount == 0;
  }

  /**
   * Finds the matching bracket position for a bracket at the given position.
   * Returns -1 if no matching bracket is found.
   */
  public int findMatchingBracket(String text, int position) {
    if (text == null || position < 0 || position >= text.length()) return -1;
    
    char c = text.charAt(position);
    if (!isOpeningPair(c) && !isClosingPair(c)) return -1;
    
    boolean isOpening = isOpeningPair(c);
    char matching = getMatchingPair(c);
    int direction = isOpening ? 1 : -1;
    int count = 1;
    int i = position + direction;
    
    boolean inString = false;
    boolean inChar = false;
    char stringDelim = 0;
    
    while (i >= 0 && i < text.length()) {
      char current = text.charAt(i);
      
      // Skip escaped characters
      if (i > 0 && text.charAt(i - 1) == '\\') {
        i += direction;
        continue;
      }
      
      // Track string/char literals
      if ((current == '"' || current == '\'') && !inChar) {
        if (!inString) {
          inString = true;
          stringDelim = current;
        } else if (current == stringDelim) {
          inString = false;
          stringDelim = 0;
        }
        i += direction;
        continue;
      }
      
      // Skip brackets inside strings
      if (inString) {
        i += direction;
        continue;
      }
      
      if (current == c) {
        count++;
      } else if (current == matching) {
        count--;
        if (count == 0) {
          return i;
        }
      }
      
      i += direction;
    }
    
    return -1; // No matching bracket found
  }
}
