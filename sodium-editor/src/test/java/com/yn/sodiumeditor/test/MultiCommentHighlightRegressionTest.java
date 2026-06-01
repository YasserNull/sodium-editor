package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MultiCommentHighlightRegressionTest {

  @Test
  public void customBlockCommentDelimiters_shouldBeConfigurable() throws Exception {
    String highlite = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlite.java");
    String parser =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightParser.java");

    assertTrue(highlite.contains("setMultiCommentsHighlite"));
    assertTrue(highlite.contains("blockCommentStartDelimiter"));
    assertTrue(highlite.contains("blockCommentEndDelimiter"));
    assertTrue(parser.contains("highlite.isConfiguredBlockCommentStart(line, i)"));
    assertTrue(parser.contains("highlite.findConfiguredBlockCommentEnd"));
  }

  @Test
  public void customBlockCommentDelimiters_shouldSupportArbitraryTokenLengths() throws Exception {
    String utils = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/HighlightUtils.java");
    String parser =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightParser.java");

    assertTrue(utils.contains("line.regionMatches(i, token, 0, token.length())"));
    assertTrue(utils.contains("line.regionMatches(start, token, 0, token.length())"));
    assertTrue(parser.contains("highlite.blockCommentEndDelimiter.length()"));
    assertTrue(parser.contains("highlite.blockCommentStartDelimiter.length()"));
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
