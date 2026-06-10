package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class PendingEditDirtyTrackingTest {

  @Test
  public void editOperators_shouldExposePendingEditState() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditOperators.java");

    assertTrue(src.contains("public final java.util.ArrayDeque<EditOp> pendingEdits"));
    assertTrue(src.contains("public int getPendingEditsCount()"));
    assertTrue(src.contains("history.getPendingSize()"));
  }

  @Test
  public void insertDeleteUndoRedo_shouldFlowThroughTrackedComponents() throws Exception {
    String editOperators =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditOperators.java");
    String recorder =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditRecordManager.java");
    String undo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");
    String redo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Redo.java");

    assertTrue(editOperators.contains("actions.insertTextAtCursor(text)"));
    assertTrue(editOperators.contains("actions.deleteCharAtCursor()"));
    assertTrue(recorder.contains("pendingEdits"));
    assertTrue(undo.contains("pendingEdits"));
    assertTrue(redo.contains("pendingEdits"));
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
