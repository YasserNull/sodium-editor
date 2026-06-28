package com.yn.sodiumeditor.core.guides.symbols;

import androidx.annotation.Nullable;

public final class SymbolsMatchCache {
  @Nullable public SymbolsMatchResult cachedSymbolsMatch = null;
  public int cachedSymbolsMatchCursorLine = -1;
  public int cachedSymbolsMatchCursorChar = -1;
  public int cachedSymbolsMatchEditVersion = -1;

  public void clear() {
    cachedSymbolsMatch = null;
    cachedSymbolsMatchCursorLine = -1;
    cachedSymbolsMatchCursorChar = -1;
    cachedSymbolsMatchEditVersion = -1;
  }

  public boolean matches(int cursorLine, int cursorChar, int editVersion) {
    return cachedSymbolsMatch != null
        && cachedSymbolsMatchCursorLine == cursorLine
        && cachedSymbolsMatchCursorChar == cursorChar
        && cachedSymbolsMatchEditVersion == editVersion;
  }

  public void store(SymbolsMatchResult match, int cursorLine, int cursorChar, int editVersion) {
    cachedSymbolsMatch = match;
    cachedSymbolsMatchCursorLine = cursorLine;
    cachedSymbolsMatchCursorChar = cursorChar;
    cachedSymbolsMatchEditVersion = editVersion;
  }
}
