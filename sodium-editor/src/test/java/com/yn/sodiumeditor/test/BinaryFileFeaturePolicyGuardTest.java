package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards automatic feature shutdown for files detected as binary. */
public class BinaryFileFeaturePolicyGuardTest {

  @Test
  public void fileOpen_shouldApplyBinaryFeaturePolicyBeforeBracketWarmup() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String body = methodBody(src, "loadFromFile(File file)");

    assertTrue(
        "BUG: binary detection must happen before bracket warmup is decided.",
        body.indexOf("final boolean binaryFile = metadata.isBinaryFile(file)")
            < body.indexOf("final boolean needsBracketWarmup = shouldWarmBracketIndexForOpen()"));
    assertTrue(
        "BUG: binary files must apply the binary feature policy during open.",
        body.contains("editor.binaryRender.applyBinaryFileFeaturePolicy(binaryFile)"));
    assertTrue(
        "BUG: binary files must not still trigger async bracket warmup from the old feature state.",
        body.indexOf("applyBinaryFileFeaturePolicy(binaryFile)")
            < body.indexOf("shouldWarmBracketIndexForOpen()"));
  }

  @Test
  public void binaryDetection_shouldRecognizeElfMagicAndNulBytes() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileMetadata.java");
    String detectBody = methodBody(src, "isBinaryFile(File file)");
    String magicBody = methodBody(src, "hasKnownBinaryMagic(byte[] buffer, int len)");

    assertTrue(
        "BUG: ELF files must be treated as binary by magic bytes, independent of threshold"
            + " heuristics.",
        magicBody.contains("b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F'"));
    assertTrue(
        "BUG: binary detection must check magic bytes before printable-ratio heuristics.",
        detectBody.indexOf("hasKnownBinaryMagic(buffer, bytesRead)")
            < detectBody.indexOf("int nonPrintableCount"));
    assertTrue(
        "BUG: NUL bytes in ELF/object files must count as non-printable, not be ignored.",
        detectBody.contains(
            "if (b == 0 || b < 9 || (b > 13 && b < 32) || b == 127) nonPrintableCount++"));
  }

  @Test
  public void binaryFeaturePolicy_shouldDisableDecorationsAndAnalysisFeatures() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/BinaryRender.java");
    String body = methodBody(src, "applyBinaryFileFeaturePolicy(boolean active)");

    assertTrue(body.contains("setBinarySafeRenderingEnabled(false, false)"));
    assertTrue(body.contains("editor.highlight.isSyntaxHighlightingEnabled = false"));
    assertTrue(body.contains("editor.colorCodeHighlight.setColorCodeHighlightingEnabled(false)"));
    assertTrue(body.contains("editor.urlUnderline.setUrlUnderliningEnabled(false)"));
    assertTrue(body.contains("editor.pathUnderline.setPathUnderliningEnabled(false)"));
    assertTrue(body.contains("editor.errorUnderline.setErrorUnderlineEnabled(false)"));
    assertTrue(body.contains("editor.symbolsMatch.setSymbolsMatchingEnabled(false)"));
    assertTrue(body.contains("editor.bracketGuides.setBracketGuidesEnabled(false)"));
    assertTrue(body.contains("editor.indentGuides.setIndentGuidesEnabled(false)"));
    assertTrue(body.contains("editor.whitespaceGuides.setWhitespaceGuidesEnabled(false)"));
    assertTrue(body.contains("editor.autoCompletion.setAutoCompletionEnabled(false)"));
    assertTrue(body.contains("editor.autoPathCompletion.setAutoPathCompletionEnabled(false)"));
    assertTrue(body.contains("editor.autoBracketPair.setAutoPairingEnabled(false)"));
    assertTrue(body.contains("editor.autoBracketNewline.setAutoBracketNewlineEnabled(false)"));
    assertTrue(body.contains("editor.currentLineHighlight.setHighlightCurrentLine(false)"));
  }

  @Test
  public void highlight_shouldKeepRulesButReturnNoSpansWhenSyntaxHighlightingDisabled()
      throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlight.java");
    String calculateBody = methodBody(src, "calculateSpansForLine(String line, int gl)");
    String getBody = methodBody(src, "getHighlightSpansForLine(String line, int gl)");

    assertTrue(
        "BUG: binary policy should disable syntax highlighting without clearing user highlight"
            + " rules.",
        src.contains("public boolean isSyntaxHighlightingEnabled = true"));
    assertTrue(calculateBody.contains("if (!isSyntaxHighlightingEnabled) return spans"));
    assertTrue(getBody.contains("if (!isSyntaxHighlightingEnabled) return new ArrayList<>()"));
  }

  @Test
  public void clearContent_shouldRestoreFeaturePolicyAfterBinaryFile() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String body = methodBody(src, "clearContent()");

    assertTrue(
        "BUG: clearing a binary file should restore saved feature states.",
        body.contains("editor.binaryRender.applyBinaryFileFeaturePolicy(false)"));
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
