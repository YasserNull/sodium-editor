package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards save after large deletion so stale rendered window text cannot survive the rewrite. */
public class SaveReloadsWindowAfterRewriteGuardTest {

  @Test
  public void applyPendingEditsToFileAsync_shouldReloadWindowAfterSuccessfulRewrite() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileEditHandler.java");
    String body = methodBody(src, "applyPendingEditsToFileAsync(@Nullable Runnable onComplete)");

    int successBranch = body.indexOf("if (!success)");
    assertTrue("Expected save success branch in FileEditHandler.", successBranch >= 0);

    int clearModified = body.indexOf("editor.windowRender.modifiedLines.clear()", successBranch);
    assertTrue("Expected save to clear modifiedLines after a successful rewrite.", clearModified >= 0);

    int reloadWindow = body.indexOf("editor.fileIO.loadWindowAround(", clearModified);
    assertTrue(
        "BUG: after saving a structural delete, linesWindow still contains old file text unless it is reloaded.",
        reloadWindow >= 0);
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
