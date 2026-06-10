package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.core.highlight.UrlUnderline;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import org.junit.Test;

/** Guards URL underline drawing behavior. */
public class UrlUnderlineGuardTest {

  @Test
  public void urlUnderline_shouldMatchHttpUrlsAndTrimTrailingPunctuation() {
    String line = "see https://example.com/a(b)?q=1), next";
    Matcher matcher = UrlUnderline.DEFAULT_URL_UNDERLINE_PATTERN.matcher(line);

    assertTrue("BUG: default URL underline pattern must match http/https URLs.", matcher.find());

    int trimmedEnd = UrlUnderline.trimUrlUnderlineEnd(line, matcher.start(), matcher.end());

    assertTrue(
        "BUG: URL underline must exclude trailing punctuation from the drawn underline.",
        line.substring(matcher.start(), trimmedEnd).equals("https://example.com/a(b)?q=1"));
  }

  @Test
  public void urlUnderline_shouldCreateNonPathUnderlineSpans() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/UrlUnderline.java");
    String body = methodBody(src, "getUrlUnderlineSpansForLine(String line, int globalLine)");

    assertTrue(
        "BUG: URL underline drawing must create underline spans after trimming the URL end.",
        body.contains("trimUrlUnderlineEnd(line, start, end)"));
    assertTrue(
        "BUG: URL underline spans must be marked as non-path spans so drawing can style them"
            + " correctly.",
        body.contains("new TextRender.UnderlineSpan(start, end, false)"));
    assertTrue(
        "BUG: URL underline spans must be cached per global line.",
        body.contains("urlUnderlineCache.put(globalLine, spans)"));
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
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
