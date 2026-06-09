package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards tunable performance limits called out in optimize.md. */
public class OptimizeMdTunableLimitsGuardTest {

  @Test
  public void directLineCache_hasTunableLimit() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    assertTrue(
        "BUG: direct line cache should expose a tunable max size.",
        src.contains("directLineCacheMaxSize") && src.contains("setDirectLineCacheMaxSize"));
    assertFalse(
        "BUG: direct line cache should not use an anonymous local hard-coded maxCacheSize.",
        src.contains("int maxCacheSize = 250"));
  }

  @Test
  public void searchSelectAll_hasTunableMatchLimit() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/search/Search.java");
    String body = methodBody(src, "public boolean selectAllSearchMatches(");
    assertTrue(
        "BUG: select-all search should use a configurable match limit.",
        src.contains("searchSelectAllMaxMatches") && body.contains("searchSelectAllMaxMatches"));
    assertFalse(
        "BUG: select-all search should not stop at a hidden 1000-match constant.",
        body.contains("1000"));
  }

  @Test
  public void commonFileScans_useLargeIoBuffer() throws Exception {
    String fileIO = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String countBody = methodBody(fileIO, "public void countTotalLines(");
    String lineBody = methodBody(fileIO, "public String readLineUtf8AtByte(");
    String metadata = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileMetadata.java");
    String scanBody = methodBody(metadata, "public LineScanResult scanLineLength(");
    String loader = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    String tailBody = methodBody(loader, "private TailWindowResult loadTailWindowInternal(");

    assertTrue(countBody.contains("FILE_IO_BUFFER_SIZE"));
    assertTrue(lineBody.contains("FILE_IO_BUFFER_SIZE"));
    assertTrue(scanBody.contains("FileIO.FILE_IO_BUFFER_SIZE"));
    assertTrue(tailBody.contains("FileIO.FILE_IO_BUFFER_SIZE"));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static String methodBody(String src, String signaturePrefix) {
    int method = src.indexOf(signaturePrefix);
    if (method < 0) throw new IllegalStateException("Method not found: " + signaturePrefix);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + signaturePrefix);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + signaturePrefix);
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
