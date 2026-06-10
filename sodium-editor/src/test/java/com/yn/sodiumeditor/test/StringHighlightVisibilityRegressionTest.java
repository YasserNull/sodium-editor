package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class StringHighlightVisibilityRegressionTest {

  @Test
  public void rendererCache_shouldNotPaintStringsWithBlackWhitespaceFallback() throws Exception {
    String highlight =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlight.java");
    String cache =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighlightCacheManager.java");

    assertTrue(
        "BUG: quote text disappears when normal string spans use the black whitespace fallback"
            + " paint.",
        highlight.contains("HighlightRender.HighlightRule sRule = rules.stringHighlightRule;"));
    assertTrue(
        "BUG: block comments without a configured paint should still parse state but not draw black"
            + " spans.",
        highlight.contains("HighlightRender.HighlightRule bRule = rules.blockCommentHighlightRule;"));
    assertTrue(
        "BUG: visible-range cache must follow the same no-fallback rule as direct line parsing.",
        cache.contains("HighlightRender.HighlightRule sRule = highlight.rules.stringHighlightRule;")
            && cache.contains(
                "HighlightRender.HighlightRule bRule = highlight.rules.blockCommentHighlightRule;"));
    assertTrue(!highlight.contains("? rules.stringHighlightRule : rules.whitespaceStringRule"));
    assertTrue(
        !cache.contains(
            "? highlight.rules.stringHighlightRule : highlight.rules.whitespaceStringRule"));
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
}
