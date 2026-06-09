package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guard against regressing file index construction back to a double file scan. */
public class FileIndexerSinglePassGuardTest {

  @Test
  public void buildIndexJava_readsFileInOnePass() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIndexer.java");
    String body = methodBody(src, "buildIndexJava");

    assertEquals(
        "BUG: index construction should open the file once and collect offsets in one scan.",
        1,
        countOccurrences(body, "new RandomAccessFile(filepath, \"r\")"));
    assertTrue(
        "BUG: one-pass indexing should store offsets in a growable primitive buffer before trimming.",
        body.contains("LongArrayBuilder"));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static String methodBody(String src, String methodName) {
    int method = src.indexOf("public long[] " + methodName + "(");
    if (method < 0) throw new IllegalStateException("Method not found: " + methodName);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + methodName);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + methodName);
  }

  private static int countOccurrences(String text, String needle) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
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
