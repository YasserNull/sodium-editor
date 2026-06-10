package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SingleCommentHighlightRegressionTest {

  @Test
  public void setSingleCommentsHighlight_shouldRegisterDelimiterAndPaintRule() throws Exception {
    String highlight =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlight.java");
    String parser =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightParser.java");

    assertTrue(highlight.contains("setSingleCommentsHighlight"));
    assertTrue(highlight.contains("isSyntaxHighlightingEnabled = true;"));
    assertTrue(highlight.contains("setSingleLineCommentSyntax(true, style, color, delimiter)"));
    assertTrue(highlight.contains("setSingleLineCommentDelimiters(delimiters)"));
    assertTrue(highlight.contains("rules.lineCommentHighlightRule = lineCommentHighlightRule"));
    assertTrue(highlight.contains("highlightRules.add(lineCommentHighlightRule)"));
    assertTrue(highlight.contains("highlightRules.remove(lineCommentHighlightRule)"));
    assertTrue(parser.contains("isLineCommentStart(line, i)"));
    assertTrue(parser.contains("highlight.rules.lineCommentHighlightRule.paint"));
    assertTrue(parser.contains("new HighlightRender.HighlightSpan(i, len, p)"));
  }

  @Test
  public void singleCommentOnlyHighlight_shouldNotBeSkippedAsEmptyRules() throws Exception {
    String rules =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightRules.java");
    String renderer =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighlightRender.java");
    String highlight =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlight.java");

    assertTrue(rules.contains("lineCommentHighlightRule == null"));
    assertTrue(renderer.contains("editor.highlight.rules.isEmpty()"));
    assertTrue(highlight.contains("rules.isEmpty()"));
    assertTrue(!renderer.contains("editor.highlight.highlightRules.isEmpty()"));
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
