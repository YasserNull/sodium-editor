package com.yn.sodiumeditor.core.view;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;

public class ViewNavigation {
  private final EditorView view;

  public final Runnable delayedWindowCheck;

  ViewNavigation(EditorView view) {
    this.view = view;
    this.delayedWindowCheck = () -> view.editor.fileIO.checkAndLoadWindow();
  }

  public void goToLine(int line) {
    goToLine(line, 1);
  }

  public void goToLine(int line, int col) {
    final int currentGoToLineVersion = view.goToLineVersion.incrementAndGet();
    view.setDisable(true);
    view.editor.loadingCircle.showLoadingCircle(true);

    if (view.editor.selection.hasSelection) {
      view.editor.selection.hasSelection = false;
      view.editor.selection.isSelectAllActive = false;
      view.editor.selection.isEntireFileSelected = false;
      view.editor.selection.selecting = false;
    }

    final int requestedLine = Math.max(0, line - 1);
    final int requestedCol = Math.max(0, col - 1);

    Integer knownTotal = null;

    if (view.editor.fileIO.sourceFile == null || view.editor.fileIO.isFileCleared) {
      synchronized (view.editor.windowRender.linesWindow) {
        knownTotal =
            Math.max(
                1,
                view.editor.windowRender.windowStartLine
                    + view.editor.windowRender.linesWindow.size());
      }
    } else if (view.editor.fileIO.isIndexReady) {
      synchronized (view.editor.fileIO.lineOffsetsLock) {
        knownTotal = Math.max(1, view.editor.fileIO.lineOffsets.length);
      }
    } else if (view.editor.fileIO.isEof) {
      synchronized (view.editor.windowRender.linesWindow) {
        knownTotal =
            Math.max(
                1,
                view.editor.windowRender.windowStartLine
                    + view.editor.windowRender.linesWindow.size());
      }
    }

    if (knownTotal != null) {
      int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
      proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
    } else {
      view.editor.fileIO.countTotalLines(
          totalLines -> {
            if (currentGoToLineVersion != view.goToLineVersion.get()) return;
            int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
            int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
            proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
          });
    }
  }

  public void proceedGoToLineClamped(
      final int currentGoToLineVersion, final int targetLine, final int targetCol) {

    if (view.editor.fileIO.isWindowLoading
        && view.editor.fileIO.sourceFile != null
        && !(targetLine >= view.editor.windowRender.windowStartLine
            && targetLine
                < view.editor.windowRender.windowStartLine
                    + view.editor.windowRender.linesWindow.size())) {
      view.editor.caret.mainHandler.postDelayed(
          () -> {
            if (currentGoToLineVersion != view.goToLineVersion.get()) return;
            proceedGoToLineClamped(currentGoToLineVersion, targetLine, targetCol);
          },
          30);
      return;
    }

    Runnable afterLoadAction =
        () -> {
          if (currentGoToLineVersion != view.goToLineVersion.get()) return;

          view.editor.cursor.cursorLine = targetLine;

          if (view.editor.cursor.cursorLine >= view.editor.windowRender.windowStartLine
              && view.editor.cursor.cursorLine
                  < view.editor.windowRender.windowStartLine
                      + view.editor.windowRender.linesWindow.size()) {
            String lineText =
                view.editor.windowRender.getLineTextForRender(view.editor.cursor.cursorLine);
            view.editor.cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else if (view.editor.fileIO.isEof) {
            int lastLineInDoc =
                view.editor.windowRender.windowStartLine
                    + view.editor.windowRender.linesWindow.size()
                    - 1;
            if (view.editor.cursor.cursorLine > lastLineInDoc)
              view.editor.cursor.cursorLine = Math.max(0, lastLineInDoc);
            String lineText =
                view.editor.windowRender.getLineTextForRender(view.editor.cursor.cursorLine);
            view.editor.cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
          } else {
            view.editor.cursor.cursorChar = 0;
          }

          view.editor.scroll.keepCursorVisibleHorizontally();
          view.setDisable(false);
          view.editor.loadingCircle.showLoadingCircle(false);

          view.editor.requestFocus();
          view.editor.post(
              () -> {
                view.editor.ime.showKeyboard();
                view.editor.requestFocus();
                InputMethodManager imm =
                    (InputMethodManager)
                        view.editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.restartInput(view.editor);
              });
        };

    if (view.editor.fileIO.isFileCleared
        || view.editor.fileIO.sourceFile == null
        || (targetLine >= view.editor.windowRender.windowStartLine
            && targetLine
                < view.editor.windowRender.windowStartLine
                    + view.editor.windowRender.linesWindow.size())) {
      afterLoadAction.run();
    } else {
      int targetStart = Math.max(0, targetLine - view.editor.windowRender.prefetchLines);
      view.editor.fileIO.loadWindowAround(targetStart, afterLoadAction, false);
    }
  }
}
