package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards tapping below the last visible folded line. */
public class CodeFoldAfterEndTapGuardTest {

  @Test
  public void tapAfterEnd_shouldNotHitTestCollapsedFoldPlaceholder() throws Exception {
    String tapSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnSingleTapUp.java");

    String tapBody = methodBody(tapSrc, "onSingleTapUp(MotionEvent e)");
    assertTrue(
        "BUG: taps below the last visible folded line must be handled as after-end before collapsed-fold hit testing, otherwise the caret can jump behind the opening brace.",
        tapBody.contains("boolean afterEnd = editor.clickAfterEndToAddLine.isClickAfterEnd(visibleIndex, totalVisible)")
            && tapBody.indexOf("if (afterEnd)")
                < tapBody.indexOf("EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition"));
    assertTrue(
        "BUG: after-end tap placement must use the last visible line from the tap handler; delegating to ClickAfterEndToAddLine can skip a trailing empty line and jump to a folded close brace.",
        tapBody.contains("placeCursorAtLastVisibleLineEnd(totalVisible)")
            && !tapBody.contains("handleDefaultAfterEnd()")
            && !tapBody.contains("handleClickAfterEnd(visibleIndex, totalVisible)"));

    String clickSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/features/ClickAfterEndToAddLine.java");
    String afterEndBody = methodBody(clickSrc, "isClickAfterEnd(int visibleIndex, int totalVisible)");
    String defaultBody = methodBody(clickSrc, "handleDefaultAfterEnd()");
    String lastLineBody = methodBody(clickSrc, "int getLastVisibleContentLine(int totalVisible)");
    String mapBody = methodBody(clickSrc, "mapVisibleIndexToLine(int visibleIndex)");
    String textBody = methodBody(clickSrc, "getLineTextForAfterEnd(int line)");
    String moveBody = methodBody(clickSrc, "moveCursorToEndOfLastLine(int lastLineIndex)");
    assertTrue(
        "BUG: after-end detection must be based on the visible document length only; the loaded file window is not the visible EOF when large folds are collapsed.",
        afterEndBody.contains("visibleIndex >= totalVisible")
            && !afterEndBody.contains("windowStartLine")
            && !afterEndBody.contains("linesWindow.size()"));
    assertTrue(
        "BUG: default after-end placement must use the last visible content line, not the logical file tail hidden inside a collapsed fold.",
        defaultBody.contains("getLastVisibleContentLine(totalVisible)"));
    assertTrue(
        "BUG: after-end placement must skip trailing empty visible lines while preserving collapsed fold starts as valid visible content.",
        lastLineBody.contains("lastVisibleIndex > 0")
            && lastLineBody.contains("mapVisibleIndexToLine(lastVisibleIndex)")
            && lastLineBody.contains("!getLineTextForAfterEnd(line).isEmpty() || isCollapsedFoldStart(line)")
            && lastLineBody.contains("lastVisibleIndex--"));
    assertTrue(
        "BUG: after-end placement must map visible indices through word-wrap/fold mapping instead of using raw global line indices.",
        mapBody.contains("editor.wordWrap.getVisualPositionForIndex(visibleIndex).line")
            && mapBody.contains("editor.codeFold.mapVisibleIndexToGlobal(visibleIndex)"));
    assertTrue(
        "BUG: trailing-empty detection must use direct line lookup when the file tail is outside the loaded render window.",
        textBody.contains("populateDirectLinesForRange(line, line, direct)")
            && textBody.contains("getLineTextForRenderWithDirect(line, direct)"));
    assertTrue(
        "BUG: after-end placement on a collapsed final fold must map hidden last lines to the fold end line and place the caret after the real end-line text.",
        moveBody.contains("getCollapsedRangeContainingLine(line)")
            && moveBody.contains("line = hidden.endLine")
            && moveBody.contains("getEndLineTextForFold(hidden)")
            && moveBody.contains("editor.cursor.cursorChar = lastLineText.length()"));
  }

  private static String methodBody(String src, String methodName) {
    int method = src.indexOf(methodName);
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
