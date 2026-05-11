package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards caret positioning when the cursor is visually inside a collapsed fold line. */
public class CodeFoldCaretGuardTest {

  @Test
  public void caretDocumentX_shouldAccountForCollapsedFoldPlaceholderAndSuffix() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Caret.java");

    String getCaretXBody = methodBody(src, "getCaretDocumentX()");
    assertTrue(
        "BUG: caret X must route collapsed-fold cursor positions through dedicated collapsed-fold geometry instead of measuring the hidden end line directly.",
        getCaretXBody.contains("getCollapsedFoldCaretDocumentX(hidden, cursor.cursorChar)")
            && getCaretXBody.contains("getCollapsedFoldCaretDocumentX(start, cursor.cursorChar)"));

    String collapsedBody = methodBody(src, "getCollapsedFoldCaretDocumentX(CodeFold.FoldRange fold, int cursorChar)");
    assertTrue(
        "BUG: collapsed-fold caret X must include the fold placeholder width before placing the caret after the folded region.",
        collapsedBody.contains("CodeFold.FOLD_PLACEHOLDER_TEXT")
            && collapsedBody.contains("measureText(CodeFold.FOLD_PLACEHOLDER_TEXT)"));
    assertTrue(
        "BUG: collapsed-fold caret X must resolve the closing token location before computing caret placement after the fold.",
        collapsedBody.contains("resolveCloseCharIndex")
            && collapsedBody.contains("closeIdx"));
    assertTrue(
        "BUG: collapsed-fold caret X must account for suffix text after the closing token, otherwise positions like '{<->} hello' render the caret at the wrong X.",
        collapsedBody.contains("suffixStart")
            && collapsedBody.contains("measureHighlightedSegmentWidth")
            && collapsedBody.contains("endLineText"));
  }

  @Test
  public void caretDocumentY_shouldMapHiddenCollapsedFoldCursorToVisibleStartLine() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Caret.java");

    String getCaretYBody = methodBody(src, "getCaretDocumentY()");
    assertTrue(
        "BUG: caret Y must map hidden collapsed-fold cursor lines back to the fold start line, or the caret will render on a visually hidden line.",
        getCaretYBody.contains("lineForVisual = hidden.startLine;")
            && getCaretYBody.contains("lineForVisual = start.startLine;"));
  }

  @Test
  public void moveCursorToFoldEnd_shouldSnapCursorAnimationBeforeTyping() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");

    String body = methodBody(src, "moveCursorToFoldEnd(CodeFold.FoldRange fold)");
    assertTrue(
        "BUG: moving the cursor to the visible end of a collapsed fold before editing must snap cursor animation immediately, or fast typing after the fold lags behind the caret.",
        body.contains("editor.cursorAnimation.snapToPosition(")
            && body.contains("editor.caret.getCaretDocumentX()")
            && body.contains("editor.caret.getCaretDocumentY()"));
  }

  @Test
  public void keepCursorVisibleHorizontally_shouldUseCaretDocumentXForCollapsedFoldCaret() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/scroll/Scroll.java");

    String body = methodBody(src, "keepCursorVisibleHorizontally()");
    assertTrue(
        "BUG: horizontal cursor visibility must use caret.getCaretDocumentX() so caret scrolling matches collapsed-fold visual geometry and preserves a right-edge safety margin.",
        body.contains("float cX = editor.caret.getCaretDocumentX();")
            && !body.contains("editor.caret.getCaretXForLine("));
    assertTrue(
        "BUG: collapsed-fold typing must derive the visible row from caret.getCaretDocumentY(), otherwise hidden end-line cursors return no visible index and horizontal autoscroll exits before scrolling.",
        body.contains("int vIdx = Math.max(0, Math.round(editor.caret.getCaretDocumentY() / editor.textRender.lineHeight));")
            && !body.contains("getVisibleIndexForGlobalLine(editor.cursor.cursorLine)")
            && !body.contains("getVisualIndexForLineAndChar(editor.cursor.cursorLine, editor.cursor.cursorChar)"));
  }

  @Test
  public void singleTapOnCollapsedFoldSuffix_shouldKeepAnimatedCursorPlacement() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnSingleTapUp.java");

    String body = methodBody(src, "onSingleTapUp(MotionEvent e)");
    assertTrue(
        "BUG: tapping inside the visible suffix after a collapsed fold should preserve normal cursor transition animation, not force a snap.",
        body.contains("finishTapCursorPlacement(false);"));
  }

  @Test
  public void singleTapAfterEnd_shouldSnapCursorPlacement() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnSingleTapUp.java");

    String body = methodBody(src, "onSingleTapUp(MotionEvent e)");
    assertTrue(
        "BUG: tapping after EOF must keep snap-based cursor placement so moving back to the last visible character does not lag behind the tap.",
        body.contains("finishTapCursorPlacement(afterEnd);"));

    String finishBody = methodBody(src, "finishTapCursorPlacement(boolean snapCursorAnimation)");
    assertTrue(
        "BUG: tap placement helper must snap cursor animation only when explicitly requested.",
        finishBody.contains("if (snapCursorAnimation)")
            && finishBody.contains("editor.cursorAnimation.snapToPosition("));
  }

  @Test
  public void newlineAfterCollapsedFoldSuffix_shouldStayOutsideCollapsedRange() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");

    String newlineBody = methodBody(src, "handleCodeFoldNewline(int beforeLine, int beforeChar)");
    assertTrue(
        "BUG: pressing newline after the closing token on a collapsed fold end line must treat the new line as outside the fold, otherwise typed suffix lines disappear while collapsed.",
        newlineBody.contains("CodeFold.FoldRange endingFold = findFoldEndingBeforeSuffixBreak(beforeLine, beforeChar);")
            && newlineBody.contains("editor.codeFold.adjustFoldRangesForLineEdit(beforeLine + 1, 1);")
            && newlineBody.contains("editor.codeFold.adjustFoldRangesForLineEdit(beforeLine, 1);"));

    String suffixBody = methodBody(src, "findFoldEndingBeforeSuffixBreak(int beforeLine, int beforeChar)");
    assertTrue(
        "BUG: suffix-break detection must resolve the fold closing token and only classify newline as outside the fold after that token.",
        suffixBody.contains("resolveCloseCharIndex")
            && suffixBody.contains("closeEnd")
            && suffixBody.contains("beforeChar >= closeEnd"));
  }

  @Test
  public void collapsedFoldSuffixSelection_shouldUseFoldAwareHandleGeometry() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionHandles.java");

    String charXBody = methodBody(src, "getCharX(int line, int ch)");
    assertTrue(
        "BUG: selection handles for suffix text after a collapsed fold must map hidden end-line positions through fold-aware X geometry, or the handles render far below or away from the visible suffix.",
        charXBody.contains("getCollapsedRangeContainingLine(line)")
            && charXBody.contains("getCollapsedFoldCharDocumentX(hidden, ch)")
            && charXBody.contains("getCollapsedFoldCharDocumentX(start, ch)"));

    String lineYBody = methodBody(src, "getLineY(int line)");
    assertTrue(
        "BUG: selection handles for collapsed-fold suffix text must map hidden end-line Y back to the fold start line.",
        lineYBody.contains("lineForVisual = hidden.startLine;"));
  }

  @Test
  public void collapsedFoldSuffixSelection_shouldDrawVisibleHighlightOnFoldedLine() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/CodeFoldRender.java");

    String body =
        methodBody(
            src,
            "drawFoldedContent(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,");
    assertTrue(
        "BUG: selecting suffix text after a collapsed fold must render the blue highlight on the visible folded line instead of only on the hidden end line.",
        body.contains("selectionTouchesSuffix")
            && body.contains("selEndLine >= foldRange.endLine")
            && body.contains("selStartLine <= foldRange.endLine")
            && body.contains("suffixBase")
            && body.contains("drawSelectionSegment("));
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
