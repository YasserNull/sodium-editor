package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards access-order LinkedHashMap cache iteration against ConcurrentModificationException. */
public class LinkedHashMapIterationGuardTest {

  @Test
  public void lineCacheShifter_shouldIterateSnapshotsOfAccessOrderMaps() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/LineCacheShifter.java");

    assertTrue(
        "BUG: modifiedLines is access-order LinkedHashMap; shifting must iterate a snapshot.",
        src.contains("new ArrayList<>(editor.windowRender.modifiedLines.entrySet())"));
    assertTrue(
        "BUG: highlightCache is access-order LinkedHashMap; shifting must iterate a snapshot.",
        src.contains("new ArrayList<>(editor.highlite.highlightCache.entrySet())"));
    assertTrue(
        "BUG: colorCodeBgCache is access-order LinkedHashMap; shifting must iterate a snapshot.",
        src.contains("new ArrayList<>(editor.colorCodeHighlight.colorCodeBgCache.entrySet())"));
    assertTrue(
        "BUG: urlUnderlineCache is access-order LinkedHashMap; shifting must iterate a snapshot.",
        src.contains("new ArrayList<>(editor.urlUnderline.urlUnderlineCache.entrySet())"));
    assertTrue(
        "BUG: pathUnderlineCache is access-order LinkedHashMap; shifting must iterate a snapshot.",
        src.contains("new ArrayList<>(editor.pathUnderline.pathUnderlineCache.entrySet())"));
  }

  @Test
  public void fileWindowLoader_shouldIterateModifiedLinesSnapshot() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");

    assertTrue(
        "BUG: applying modifiedLines over a reloaded window must iterate a snapshot to avoid CME.",
        src.contains("new java.util.ArrayList<>(editor.windowRender.modifiedLines.entrySet())"));
  }

  @Test
  public void windowRender_shouldIterateModifiedLineKeysSnapshot() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/WindowRender.java");
    String body = methodBody(src, "getFirstModifiedLine()");

    assertTrue(
        "BUG: modifiedLines is access-order LinkedHashMap; rendering must iterate a key snapshot to avoid CME.",
        body.contains("new java.util.ArrayList<>(modifiedLines.keySet())"));
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
