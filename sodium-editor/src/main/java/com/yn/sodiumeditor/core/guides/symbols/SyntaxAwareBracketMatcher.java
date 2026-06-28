package com.yn.sodiumeditor.core.guides.symbols;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.HashMap;

public final class SyntaxAwareBracketMatcher {
  private final SymbolsMatch symbolsMatch;

  public SyntaxAwareBracketMatcher(SymbolsMatch symbolsMatch) {
    this.symbolsMatch = symbolsMatch;
  }

  public SymbolsMatchResult findInVisible(
      int firstVisibleLine, int lastVisibleLine, @Nullable HashMap<Integer, String> directLines) {
    return symbolsMatch.findSyntaxAwareSymbolsMatchInVisible(
        firstVisibleLine, lastVisibleLine, directLines);
  }

  public SymbolsMatchResult findInDocument() {
    return symbolsMatch.findSyntaxAwareSymbolsMatchInDocument();
  }

  public SodiumEditor getEditor() {
    return symbolsMatch.editor;
  }
}
