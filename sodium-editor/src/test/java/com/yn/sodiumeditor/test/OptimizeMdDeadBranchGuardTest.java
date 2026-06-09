package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards cleanup items called out in optimize.md. */
public class OptimizeMdDeadBranchGuardTest {

  private static final String[] FILES = {
    "sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/core/scroll/ScrollBounds.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/core/wordwrap/WordWrap.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Cursor.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/TextRender.java",
    "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/LineNumberRender.java"
  };

  @Test
  public void optimizeMdFiles_doNotContainConstantFalseBranches() throws Exception {
    for (String rel : FILES) {
      String src = readSource(rel);
      assertFalse(rel + " should not contain disabled ternary branches.", src.contains("false ?"));
      assertFalse(rel + " should not contain disabled if branches.", src.contains("if (false)"));
    }
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
