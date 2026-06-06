package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Guard against the visual glitch where a fresh selection briefly renders handles at the previous
 * selection location before the new location settles.
 */
public class SelectionHandleFreshSelectionGuardTest {

  @Test
  public void newSelection_shouldRenderHandlesAtFreshLocationFromFirstFrame() throws Exception {
    String stateSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionState.java");
    String animSrc =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/SelectionHandlesAnimation.java");
    String smartSelectionSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SmartSelection.java");
    String searchSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/search/Search.java");
    String actionHandlerSrc =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");

    String setSelectionAround =
        methodBody(
            stateSrc,
            "setSelection(int startLine, int startChar, int endLine, int endChar)");
    assertTrue(
        "BUG: starting a new selection must clear stale handle animation state before the next frame is computed.",
        setSelectionAround.contains("editor.selectionHandles.animation.resetAnimationState();")
            && setSelectionAround.contains("editor.selectionHandles.updateHandlesPosition();")
            && setSelectionAround.contains("editor.invalidate();"));

    String setSelectionInternalAround =
        methodBody(stateSrc, "setSelectionInternal(int sL, int sC, int eL, int eC)");
    assertTrue(
        "BUG: replacing one selection with another must clear stale handle animation state before visibility/geometry updates.",
        setSelectionInternalAround.contains("editor.selectionHandles.animation.resetAnimationState();")
            && setSelectionInternalAround.contains("editor.selectionHandles.updateHandlesPosition();")
            && setSelectionInternalAround.contains("editor.invalidate();")
            && setSelectionInternalAround.indexOf(
                    "editor.selectionHandles.animation.resetAnimationState();")
                < setSelectionInternalAround.indexOf("updateSelectionVisibility(hasSelection);")
            && setSelectionInternalAround.indexOf("editor.selectionHandles.updateHandlesPosition();")
                < setSelectionInternalAround.indexOf("updateSelectionVisibility(hasSelection);"));

    String resetAround = methodBody(animSrc, "resetAnimationState()");
    assertTrue(
        "BUG: resetting handle animation state must discard stale animated draw positions and the X start anchors used to redirect from the previous selection.",
        resetAround.contains("animLeftX = Float.NaN;")
            && resetAround.contains("animLeftY = Float.NaN;")
            && resetAround.contains("animRightX = Float.NaN;")
            && resetAround.contains("animRightY = Float.NaN;")
            && resetAround.contains("leftStartX = Float.NaN;")
            && resetAround.contains("rightStartX = Float.NaN;")
            && resetAround.contains("leftAnimDuration = ANIM_DURATION;")
            && resetAround.contains("rightAnimDuration = ANIM_DURATION;"));

    int animatedAt =
        animSrc.indexOf(
            "public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY)");
    assertTrue("Expected getAnimatedHandlePosition in SelectionHandlesAnimation.", animatedAt >= 0);
    String animatedAround = animSrc.substring(animatedAt, Math.min(animSrc.length(), animatedAt + 2600));
    assertTrue(
        "BUG: after resetAnimationState, the first frame of a fresh selection must initialize from the new target itself, not from stale previous coordinates.",
        animatedAround.contains("float drawX = Float.isNaN(currentDrawX) ? targetX : currentDrawX;")
            && animatedAround.contains("float drawY = Float.isNaN(currentDrawY) ? targetY : currentDrawY;")
            && animatedAround.contains("leftStartX = drawX;")
            && animatedAround.contains("leftTargetX = targetX;")
            && animatedAround.contains("rightStartX = drawX;")
            && animatedAround.contains("rightTargetX = targetX;"));

    int doubleTapAt =
        smartSelectionSrc.indexOf(
            "public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText)");
    assertTrue("Expected applySmartDoubleTapSelection in SmartSelection.", doubleTapAt >= 0);
    String doubleTapAround =
        smartSelectionSrc.substring(
            doubleTapAt, Math.min(smartSelectionSrc.length(), doubleTapAt + 2200));
    assertTrue(
        "BUG: smart double-tap selection must go through selection.setSelection(...) so handle reset logic runs before the next frame.",
        doubleTapAround.contains("selection.setSelection(line, pick.start, line, pick.end);")
            && !doubleTapAround.contains("selection.selStartLine = selection.selEndLine = line;")
            && !doubleTapAround.contains("selection.hasSelection = true;"));

    int selectAllSearchAt = searchSrc.indexOf("public boolean selectAllSearchMatches()");
    assertTrue("Expected selectAllSearchMatches in Search.", selectAllSearchAt >= 0);
    String selectAllSearchAround =
        searchSrc.substring(
            selectAllSearchAt, Math.min(searchSrc.length(), selectAllSearchAt + 1800));
    assertTrue(
        "BUG: selecting all search matches must go through selection.setSelection(...) so a fresh search selection cannot reuse stale handle positions.",
        selectAllSearchAround.contains(
                "editor.selection.setSelection(first.line, first.start, last.line, last.end);")
            && !selectAllSearchAround.contains("editor.selection.selStartLine = first.line;")
            && !selectAllSearchAround.contains("editor.selection.hasSelection = true;"));

    int selectAllAt = actionHandlerSrc.indexOf("public void selectAll()");
    assertTrue("Expected selectAll in SelectionActionHandler.", selectAllAt >= 0);
    String selectAllAround =
        actionHandlerSrc.substring(
            selectAllAt, Math.min(actionHandlerSrc.length(), selectAllAt + 9000));
    assertTrue(
        "BUG: selectAll completion paths must build the final selection via selection.setSelection(...) so handle positions refresh.",
        selectAllAround.contains("selection.setSelection(0, 0, endLine, endChar);")
            && selectAllAround.contains("selection.setSelection(0, 0, winLast, endChar);")
            && selectAllAround.contains("selection.setSelection(0, 0, fileLast, endChar);")
            && selectAllAround.contains("complete-no-index")
            && selectAllAround.contains("selection.syncToState();"));
  }

  private static String readSource(String relativePath) throws Exception {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(relativePath);
      if (Files.exists(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
      }
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }

    Path fallback =
        new File(".")
            .toPath()
            .toAbsolutePath()
            .normalize()
            .resolve(relativePath.replace("sodium-editor/", ""));
    if (Files.exists(fallback)) {
      return new String(Files.readAllBytes(fallback), StandardCharsets.UTF_8);
    }
    throw new IllegalStateException("Could not locate source file: " + relativePath);
  }

  private static String methodBody(String src, String methodSignatureFragment) {
    int method = src.indexOf(methodSignatureFragment);
    if (method < 0) {
      throw new IllegalStateException("Method not found: " + methodSignatureFragment);
    }
    int start = src.indexOf('{', method);
    if (start < 0) {
      throw new IllegalStateException("Method body not found: " + methodSignatureFragment);
    }
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) {
          return src.substring(start, i + 1);
        }
      }
    }
    throw new IllegalStateException("Unclosed method body: " + methodSignatureFragment);
  }
}
