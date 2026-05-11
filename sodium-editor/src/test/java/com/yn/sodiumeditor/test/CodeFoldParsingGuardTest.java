package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards code-fold parsing so escaped or commented/quoted brackets do not create folds. */
public class CodeFoldParsingGuardTest {

  @Test
  public void detector_shouldTreatCommentsAndEscapesAsNonStructural() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFoldDetector.java");

    String helperBody = methodBody(src, "isLineCommentStartForFold(String line, int index)");
    assertTrue(
        "BUG: fold detection must explicitly treat both '//' and '#' as line comments before considering brackets structural.",
        helperBody.contains("line.charAt(index) == '#'")
            && helperBody.contains("line.charAt(index) == '/'")
            && helperBody.contains("line.charAt(index + 1) == '/'")
            && helperBody.contains("isEscaped(line, index)"));

    String tokenBody = methodBody(src, "findLastUnclosedFoldTokenInLine(String line, int startIndex)");
    assertTrue(
        "BUG: fold-start scanning must ignore brackets inside block comments, line comments, triple quotes, and quoted strings.",
        tokenBody.contains("isLineCommentStartForFold(line, i)")
            && tokenBody.contains("line.charAt(i) == '/'")
            && tokenBody.contains("line.charAt(i + 1) == '*'")
            && tokenBody.contains("tripleQuoteDelimiter")
            && tokenBody.contains("c == '\\'' || c == '\"' || c == '`'"));
    assertTrue(
        "BUG: fold-start scanning must ignore escaped brackets like '\\\\{' instead of pushing them onto the fold stack.",
        tokenBody.contains("!editor.highlite.isEscaped(line, i)"));

    String matchBody =
        methodBody(
            src,
            "findMatchingBracketFrom(int startLine, int startChar, char openBracket, @Nullable RandomAccessFile raf)");
    assertTrue(
        "BUG: matching-bracket scanning must preserve the same non-structural rules as fold-start scanning.",
        matchBody.contains("isLineCommentStartForFold(line, i)")
            && matchBody.contains("tripleQuoteDelimiter")
            && matchBody.contains("c == '\\'' || c == '\"' || c == '`'")
            && matchBody.contains("!editor.highlite.isEscaped(line, i)"));
  }

  @Test
  public void utils_shouldResolveClosingBracketWithSameParsingRules() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/CodeFoldUtils.java");

    String closeBody =
        methodBody(
            src,
            "findClosingBracketInLine(String line, int startChar, char openBracket, char closeBracket)");
    assertTrue(
        "BUG: close-bracket resolution must ignore brackets inside comments, quotes, triple quotes, and escaped sequences.",
        closeBody.contains("isLineCommentStartForFold(line, i)")
            && closeBody.contains("tripleQuoteDelimiter")
            && closeBody.contains("c == '\\'' || c == '\"' || c == '`'")
            && closeBody.contains("!isTokenEscaped(line, i)"));

    String helperBody = methodBody(src, "isLineCommentStartForFold(String line, int index)");
    assertTrue(
        "BUG: close-bracket resolution must treat both '//' and '#' as line comments regardless of syntax-highlighting configuration.",
        helperBody.contains("line.charAt(index) == '#'")
            && helperBody.contains("line.charAt(index) == '/'")
            && helperBody.contains("line.charAt(index + 1) == '/'"));
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
    Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
    if (Files.exists(candidate)) return candidate;
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
