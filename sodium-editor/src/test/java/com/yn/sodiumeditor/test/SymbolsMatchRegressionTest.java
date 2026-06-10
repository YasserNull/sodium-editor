package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.yn.sodiumeditor.core.guides.SymbolsMatch;
import com.yn.sodiumeditor.core.guides.SymbolsMatch.SymbolsMatchResult;
import com.yn.sodiumeditor.core.guides.SymbolsMatch.SymbolsMatchSet;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Guards regex-backed symbol matching beyond single-character brackets. */
public class SymbolsMatchRegressionTest {

  @Test
  public void regexSets_shouldMatchMultiCharacterCommentsExactly() {
    SymbolsMatchSet comment = new SymbolsMatchSet("/\\*", "\\*/");

    SymbolsMatchResult match =
        SymbolsMatch.findMatchInLines(
            Collections.singletonList("a /* body */ z"),
            0,
            3,
            Collections.singletonList(comment));

    assertNotNull("BUG: regex-backed block comment symbols must be matchable.", match);
    assertEquals(2, match.openChar);
    assertEquals(10, match.closeChar);
    assertEquals(2, match.openLength);
    assertEquals(2, match.closeLength);

    SymbolsMatchResult falsePositive =
        SymbolsMatch.findMatchInLines(
            Collections.singletonList("a //* body */ z"),
            0,
            3,
            Collections.singletonList(comment));

    assertNull("BUG: regex /* must not match the // prefix in //*.", falsePositive);
  }

  @Test
  public void sameStartEndRegex_shouldPairQuoteDelimiters() {
    SymbolsMatchSet singleQuote = new SymbolsMatchSet("'", "'");

    SymbolsMatchResult match =
        SymbolsMatch.findMatchInLines(
            Arrays.asList("first 'value'", "next"),
            0,
            7,
            Collections.singletonList(singleQuote));

    assertNotNull("BUG: equal start/end symbols like quotes must be paired.", match);
    assertEquals(6, match.openChar);
    assertEquals(12, match.closeChar);
  }
}
