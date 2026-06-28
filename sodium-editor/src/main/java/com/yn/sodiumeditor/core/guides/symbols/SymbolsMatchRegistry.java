package com.yn.sodiumeditor.core.guides.symbols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SymbolsMatchRegistry {
  private static final SymbolsMatchSet BRACE_MATCH = new SymbolsMatchSet("\\{", "\\}");
  private static final SymbolsMatchSet PAREN_MATCH = new SymbolsMatchSet("\\(", "\\)");
  private static final SymbolsMatchSet BRACKET_MATCH = new SymbolsMatchSet("\\[", "\\]");
  private static final SymbolsMatchSet DOUBLE_QUOTE_MATCH = new SymbolsMatchSet("\"", "\"");
  private static final SymbolsMatchSet SINGLE_QUOTE_MATCH = new SymbolsMatchSet("'", "'");
  private static final SymbolsMatchSet BACKTICK_MATCH = new SymbolsMatchSet("`", "`");
  private static final SymbolsMatchSet JAVA_BLOCK_COMMENT_MATCH =
      new SymbolsMatchSet("/\\*", "\\*/");

  private final SymbolsMatch owner;

  public SymbolsMatchRegistry(SymbolsMatch owner) {
    this.owner = owner;
  }

  public List<SymbolsMatchSet> getEnabledSymbolsMatchSets() {
    ArrayList<SymbolsMatchSet> sets = new ArrayList<>();
    if (owner.BracketsMatch) {
      sets.add(BRACE_MATCH);
      sets.add(PAREN_MATCH);
      sets.add(BRACKET_MATCH);
    }
    if (owner.StringsMatch) {
      sets.add(DOUBLE_QUOTE_MATCH);
      sets.add(SINGLE_QUOTE_MATCH);
    }
    if (owner.TupleStringsMatch) {
      sets.add(BACKTICK_MATCH);
    }
    if (owner.JavaCommentsMatch) {
      sets.add(JAVA_BLOCK_COMMENT_MATCH);
    }
    sets.addAll(owner.customSymbolsMatchSets);
    return Collections.unmodifiableList(sets);
  }
}
