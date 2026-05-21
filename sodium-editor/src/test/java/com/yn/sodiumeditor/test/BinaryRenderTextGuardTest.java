package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.core.binary.BinaryTokenConverter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards binary-safe conversion so normal Unicode text is not rendered as byte tokens. */
public class BinaryRenderTextGuardTest {

  @Test
  public void bytesToControlVisible_shouldRenderArabicTextNormally() {
    BinaryTokenConverter converter = new BinaryTokenConverter();
    byte[] bytes = "مرحبا".getBytes(StandardCharsets.UTF_8);

    assertEquals("مرحبا", converter.bytesToControlVisible(bytes, bytes.length, StandardCharsets.UTF_8));
  }

  @Test
  public void bytesToControlVisible_shouldOnlyTokenizeHiddenControlCharacters() {
    BinaryTokenConverter converter = new BinaryTokenConverter();
    byte[] bytes = "A\u0000ب\u007F".getBytes(StandardCharsets.UTF_8);

    assertEquals("ANULبDEL", converter.bytesToControlVisible(bytes, bytes.length, StandardCharsets.UTF_8));
  }

  @Test
  public void bytesToControlVisibleAndCacheSpans_shouldOnlySpanControlTokens() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/binary/BinaryTokenConverter.java");
    String body =
        methodBody(src, "charsToControlVisibleAndCacheSpans(\n            String text, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans)");

    assertTrue(
        "BUG: binary spans should only be added for hidden control characters, not every non-ASCII character.",
        body.contains("if (needsEscaping(c))"));
    assertTrue(
        "BUG: normal Unicode characters should be appended as text, not converted to byte tokens.",
        body.contains("String.valueOf(c)"));
  }

  @Test
  public void fileReaders_shouldPassCharsetToBinarySafeConversion() throws Exception {
    String windowLoader =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    String fileIO = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String binaryFileReader =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/BinaryFileReader.java");

    assertTrue(
        "BUG: window loading must decode bytes using the file charset before tokenizing hidden controls.",
        windowLoader.contains(
            "bytesToControlVisibleAndCacheSpans(buf, buf.length, lineIdx, fileIO.fileCharset)"));
    assertTrue(
        "BUG: direct line reads must pass fileCharset to binary-safe conversion.",
        fileIO.contains("bytesToControlVisible(data, data.length, fileCharset)")
            && fileIO.contains("bytesToControlVisible(buf, buf.length, fileCharset)"));
    assertTrue(
        "BUG: BinaryFileReader must pass fileCharset to binary-safe conversion.",
        binaryFileReader.contains("bytesToControlVisible(sink, used, fileCharset)")
            && binaryFileReader.contains("bytesToControlVisible(buf, len, fileCharset)"));
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
