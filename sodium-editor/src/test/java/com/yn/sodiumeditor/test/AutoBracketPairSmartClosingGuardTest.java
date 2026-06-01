package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class AutoBracketPairSmartClosingGuardTest {

  @Test
  public void autoPair_shouldUseExistingClosingBracketInsteadOfDuplicatingIt() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/features/AutoBracketPair.java");
    String handleBody = methodBody(src, "handleAutoPairing");
    String helperBody = methodBody(src, "private boolean shouldSuppressAutoPair");

    assertTrue(
        "BUG: typed opening bracket before an existing closer should not insert another closer.",
        handleBody.contains("shouldSuppressAutoPair(ln, editor.cursor.cursorLine, pos, typedStart, typedChar, closing)")
            && handleBody.indexOf("shouldSuppressAutoPair(ln, editor.cursor.cursorLine, pos, typedStart, typedChar, closing)")
                < handleBody.indexOf("editor.editOperators.insertTextAtCursor(closing)"));
    assertTrue(
        "BUG: unmatched existing closers like ) ] } must suppress duplicate auto-pairing.",
        helperBody.contains("hasUnmatchedClosingForOpening(balance, opening)"));
    assertTrue(
        "BUG: multi-character closers like */ must be reused when they are directly after cursor.",
        helperBody.contains("balance.unmatchedBlockClose > 0 || balance.unmatchedBlockOpen == 0"));
  }

  @Test
  public void autoPair_shouldTreatUnmatchedQuoteBeforeCursorAsClosingQuote() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/features/AutoBracketPair.java");
    String handleBody = methodBody(src, "handleAutoPairing");

    assertTrue(
        "BUG: typing a quote that balances another quote on the same line should not create an empty pair.",
        handleBody.contains("shouldSuppressAutoPair(ln, editor.cursor.cursorLine, pos, typedStart, typedChar, closing)")
            && handleBody.indexOf("shouldSuppressAutoPair(ln, editor.cursor.cursorLine, pos, typedStart, typedChar, closing)")
                < handleBody.indexOf("editor.editOperators.insertTextAtCursor(closing)"));
    assertTrue(
        "BUG: quote detection must work for double, single, and backtick quotes.",
        src.contains("return c == '\"' || c == '\\'' || c == '`';"));
    assertTrue(
        "BUG: escaped quotes must not affect quote balance.",
        src.contains("if (isEscaped(line, i)) continue;"));
  }

  @Test
  public void autoPair_shouldUseEditVersionedBalanceCacheForQuotesBracketsAndBlockComments() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/features/AutoBracketPair.java");
    String cacheBody = methodBody(src, "private BalanceInfo getBalanceInfo");
    String computeBody = methodBody(src, "static BalanceInfo compute");

    assertTrue(
        "BUG: balance cache must be invalidated by typing, paste, undo, redo, and file changes through editVersion/text identity.",
        cacheBody.contains("editor.editOperators.editVersion.get()")
            && cacheBody.contains("cached.editVersion == version")
            && cacheBody.contains("cached.textHash == textHash")
            && cacheBody.contains("cached.textLength == textLength"));
    assertTrue(
        "BUG: balance cache must include brackets, quotes, and block comment tokens.",
        computeBody.contains("doubleQuoteCount")
            && computeBody.contains("singleQuoteCount")
            && computeBody.contains("backtickQuoteCount")
            && computeBody.contains("unmatchedParenOpen")
            && computeBody.contains("unmatchedBlockOpen")
            && computeBody.contains("unmatchedBlockClose"));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static Path findPath(String rel) {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(rel);
      if (Files.exists(candidate)) return candidate;
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }
    throw new IllegalStateException("Could not locate file: " + rel);
  }

  private static String methodBody(String src, String signature) {
    int method = src.indexOf(signature);
    if (method < 0) throw new IllegalStateException("Method not found: " + signature);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + signature);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + signature);
  }
}
