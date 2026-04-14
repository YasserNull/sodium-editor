package com.yn.sodiumeditor.core; 
import com.yn.sodiumeditor.SodiumEditor;
/**
 * Manages automatic bracket/quote pairing for the SodiumEditor.
 * Automatically inserts closing brackets/quotes when opening ones are typed.
 */
public class AutoBracketPair {

  private final SodiumEditor editor;

  // Auto-pairing state
  public boolean isAutoPairingEnabled = true;

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
    String ln = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
    if (ln == null) return;
    
    int pos = editor.cursor.cursorChar;
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
      // Smart Quote: Don't add pair if we are inside a string or it looks like a closing quote
      if ((typedChar == '"' || typedChar == '\'') && isInsideQuotes(ln, pos - 1)) {
          return; 
      }
      
      editor.editOperators.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        editor.cursor.moveCursorLeft();
      }
    }
  }
  
  private boolean isInsideQuotes(String line, int pos) {
      int quotes = 0;
      for (int i = 0; i < pos; i++) {
          if (line.charAt(i) == '"' && (i == 0 || line.charAt(i-1) != '\\')) quotes++;
      }
      return (quotes % 2) != 0;
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
        String ln = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
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
