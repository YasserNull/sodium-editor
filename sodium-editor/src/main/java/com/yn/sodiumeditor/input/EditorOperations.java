package com.yn.sodiumeditor.input;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.*;
import com.yn.sodiumeditor.core.EditOp;
import com.yn.sodiumeditor.utils.BracketFinder;

public final class EditorOperations {
  private final SodiumEditor view;

  public EditorOperations(SodiumEditor view) {
    this.view = view;
  }

  public void insertCharAtCursor(char c) {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.editorIO.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();

    if (view.cursorState.hasComposing()) {
      view.imeCompositionHandler.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorState.getCursorLine();
    final int beforeChar = view.cursorState.getCursorChar();

    view.scrollManager.ensureLineInWindow(view.cursorState.getCursorLine(), true);
    if (view.isWindowLoading
        && (view.cursorState.getCursorLine() < view.windowStartLine || view.cursorState.getCursorLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = view.cursorState.getCursorLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) view.linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, view.linesWindow.size() - 1));
    }

    synchronized (view.linesWindow) {
      String base = view.viewRender.textRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        int oldLineCount = view.viewRender.textRender.getLinesCount();
        String before = base.substring(0, Math.min(view.cursorState.getCursorChar(), base.length()));
        String after = base.substring(Math.min(view.cursorState.getCursorChar(), base.length()));
        Float oldWidth = view.lineWidthCache.get(view.cursorState.getCursorLine());

        view.viewRender.textRender.updateLocalLine(localIdx, before);
        view.linesWindow.add(localIdx + 1, after);

        view.modifiedLines.put(view.cursorState.getCursorLine(), before);
        view.modifiedLines.put(view.cursorState.getCursorLine() + 1, after);

        view.viewRender.textRender.computeWidthForLine(view.cursorState.getCursorLine(), before);
        view.viewRender.textRender.computeWidthForLine(view.cursorState.getCursorLine() + 1, after);

        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.viewRender.textRender.recalculateMaxLineWidthAsync();
        view.highlightState.clearHighlightCaches();
        view.cursorState.setCursorPosition(view.cursorState.getCursorLine() + 1, 0);
        view.history.addLineCountDelta(1);

        int newLineCount = view.viewRender.textRender.getLinesCount();
        if (view.lineNumberState.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wrapWordBuilder.onLineCountChanged(view);
      } else {
        int pos = Math.max(0, Math.min(view.cursorState.getCursorChar(), base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        view.viewRender.textRender.updateLocalLine(localIdx, modified);
        view.modifiedLines.put(view.cursorState.getCursorLine(), modified);
        view.highlightState.invalidateHighlightCacheForLine(view.cursorState.getCursorLine());
        view.cursorState.moveCharDelta(1);
        float newWidth =
            view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
                view, modified, 0, modified.length(), view.editorConfig.paint);
        synchronized (view.lineWidthCache) {
          view.lineWidthCache.put(view.cursorState.getCursorLine(), newWidth);
        }
        view.currentMaxWindowLineWidth = Math.max(view.currentMaxWindowLineWidth, newWidth);
        view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, view.currentMaxWindowLineWidth);
      }
      view.invalidate();
      view.scrollManager.keepCursorVisibleHorizontally();
    }
    view.inlinePredictionEngine.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget insertedEnd = view.editorTextInserter.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = view.cursorState.getCursorLine();
    op.cursorCharAfter = view.cursorState.getCursorChar();
    op.timestamp = System.currentTimeMillis();
    view.history.recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    if (view.selectionState.hasSelection()) {
      view.editorTextInserter.insertTextAtCursor("\n");
      return;
    }

    BracketFinder.BracketPairType pairType = BracketFinder.getBracketPairAt(view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine()), view.cursorState.getCursorChar());
    if (view.isAutoBracketNewlineEnabled && pairType != BracketFinder.BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (view.isAutoBracketNewlineIndentEnabled) {
        baseIndent = com.yn.sodiumeditor.core.IndentGuideEngine.getLineLeadingWhitespace(view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine()));
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == BracketFinder.BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = view.cursorState.getCursorLine() + 1;
      int targetChar = innerIndent.length();
      view.editorTextInserter.insertTextAtCursor(insertText);

      view.cursorState.setCursorPosition(targetLine, targetChar);
      view.cursorAnimator.resetCursorBlink();
      view.scrollManager.keepCursorVisibleHorizontally();
      view.invalidate();
      view.inlinePredictionEngine.updateSuggestion();
      return;
    }

    if (view.isAutoIndentAfterClosingBracketEnabled) {
      String ln = view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorState.getCursorChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = com.yn.sodiumeditor.core.IndentGuideEngine.getLineLeadingWhitespace(view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine()));
          int baseWidth = view.getIndentWidth(baseIndent);
          int unit = SodiumEditor.INDENT_BLOCK_UNIT.length();
          int targetWidth = baseWidth;
          if (c == '{') {
            int firstNonSpace = getFirstNonSpaceIndex(before);
            boolean startsWithClosingParenOrBracket =
                firstNonSpace >= 0
                    && (before.charAt(firstNonSpace) == ')' || before.charAt(firstNonSpace) == ']');
            if (!startsWithClosingParenOrBracket) {
              targetWidth = baseWidth + unit;
            }
          } else {
            targetWidth = Math.max(0, baseWidth - unit);
          }
          view.editorTextInserter.insertTextAtCursor("\n" + buildIndentFromWidth(targetWidth));
          return;
        }
      }
    }

    if (view.isIndentationBlocksEnabled) {
      String ln = view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorState.getCursorChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = com.yn.sodiumeditor.core.IndentGuideEngine.getLineLeadingWhitespace(view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine()));
      String extraIndent = trimmed.endsWith(":") ? SodiumEditor.INDENT_BLOCK_UNIT : "";
      view.editorTextInserter.insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (view.isAutoBracketNewlineIndentEnabled) {
      String baseIndent = view.getLineLeadingWhitespace(view.cursorState.getCursorLine());
      view.insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }

  public void deleteCharAtCursor() {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.editorIO.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();
    view.inlinePredictionState.clearActiveSuggestion();

    if (view.cursorState.hasComposing()) {
      view.imeCompositionHandler.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorState.getCursorLine();
    final int beforeChar = view.cursorState.getCursorChar();

    view.scrollManager.ensureLineInWindow(view.cursorState.getCursorLine(), true);
    if (view.isWindowLoading
        && (view.cursorState.getCursorLine() < view.windowStartLine || view.cursorState.getCursorLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = view.cursorState.getCursorLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) return;

    synchronized (view.linesWindow) {
      String base = view.viewRender.textRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorState.getCursorChar() > 0) {
        Float oldWidth = view.lineWidthCache.get(view.cursorState.getCursorLine());
        int safeStart = Math.max(0, view.cursorState.getCursorChar() - 1);
        String removed = base.substring(safeStart, Math.min(view.cursorState.getCursorChar(), base.length()));
        boolean atLineEnd = view.cursorState.getCursorChar() >= base.length();
        if (view.charAnimationConfig.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightRenderer.getPaintForChar(view.cursorState.getCursorLine(), safeStart, base);
          view.charAnimator.startDeleteAnimation(view.cursorState.getCursorLine(), safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(view.cursorState.getCursorChar());
        view.viewRender.textRender.updateLocalLine(localIdx, modified);
        view.modifiedLines.put(view.cursorState.getCursorLine(), modified);
        view.highlightState.invalidateHighlightCacheForLine(view.cursorState.getCursorLine());
        view.cursorState.setCursorChar(safeStart);
        view.viewRender.textRender.computeWidthForLine(view.cursorState.getCursorLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.viewRender.textRender.recalculateMaxLineWidthAsync();
        view.viewRender.textRender.invalidateLineGlobal(view.cursorState.getCursorLine());

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = safeStart;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeStart;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorState.getCursorLine();
        op.cursorCharAfter = view.cursorState.getCursorChar();
        op.timestamp = System.currentTimeMillis();
        view.history.recordEdit(op);
      } else if (view.cursorState.getCursorLine() > 0) {
        int oldLineCount = view.viewRender.textRender.getLinesCount();
        int prevGlobal = view.cursorState.getCursorLine() - 1;
        view.scrollManager.ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - view.windowStartLine;
        if (prevLocal < 0 || prevLocal >= view.linesWindow.size()) return;

        String prev = view.viewRender.textRender.getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        view.viewRender.textRender.updateLocalLine(prevLocal, merged);
        view.modifiedLines.put(prevGlobal, merged);
        view.highlightState.clearHighlightCaches();

        if (localIdx < view.linesWindow.size()) view.linesWindow.remove(localIdx);

        view.viewRender.textRender.recalculateMaxLineWidth();
        view.cursorState.setCursorPosition(prevGlobal, prev.length());
        view.viewRender.textRender.computeWidthForLine(prevGlobal, merged);
        view.history.addLineCountDelta(-1);

        int newLineCount = view.viewRender.textRender.getLinesCount();
        if (view.lineNumberState.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wrapWordBuilder.onLineCountChanged(view);
        view.invalidate();

        EditOp op = new EditOp();
        op.startLine = prevGlobal;
        op.startChar = prev.length();
        op.endLine = beforeLine;
        op.endChar = 0;
        op.removedText = "\n";
        op.insertedText = "";
        op.insertedEndLine = prevGlobal;
        op.insertedEndChar = prev.length();
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorState.getCursorLine();
        op.cursorCharAfter = view.cursorState.getCursorChar();
        op.timestamp = System.currentTimeMillis();
        view.history.recordEdit(op);
      }
    }
    view.inlinePredictionEngine.updateSuggestion();
  }

  public void deleteForwardAtCursor() {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.editorIO.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();
    view.inlinePredictionState.clearActiveSuggestion();

    if (view.cursorState.hasComposing()) {
      view.imeCompositionHandler.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorState.getCursorLine();
    final int beforeChar = view.cursorState.getCursorChar();

    view.scrollManager.ensureLineInWindow(view.cursorState.getCursorLine(), true);
    if (view.isWindowLoading
        && (view.cursorState.getCursorLine() < view.windowStartLine || view.cursorState.getCursorLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = view.cursorState.getCursorLine() - view.windowStartLine;
    synchronized (view.linesWindow) {
      String base = view.viewRender.textRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorState.getCursorChar() < base.length()) {
        Float oldWidth = view.lineWidthCache.get(view.cursorState.getCursorLine());
        String removed = base.substring(view.cursorState.getCursorChar(), Math.min(view.cursorState.getCursorChar() + 1, base.length()));
        boolean atLineEnd = view.cursorState.getCursorChar() == base.length() - 1;
        if (view.charAnimationConfig.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightRenderer.getPaintForChar(view.cursorState.getCursorLine(), view.cursorState.getCursorChar(), base);
          view.charAnimator.startDeleteAnimation(view.cursorState.getCursorLine(), view.cursorState.getCursorChar(), removed, p);
        }
        String modified = base.substring(0, view.cursorState.getCursorChar()) + base.substring(view.cursorState.getCursorChar() + 1);
        view.viewRender.textRender.updateLocalLine(localIdx, modified);
        view.modifiedLines.put(view.cursorState.getCursorLine(), modified);
        view.viewRender.textRender.computeWidthForLine(view.cursorState.getCursorLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.viewRender.textRender.recalculateMaxLineWidthAsync();
        view.viewRender.textRender.invalidateLineGlobal(view.cursorState.getCursorLine());

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar + 1;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = beforeChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = view.cursorState.getCursorLine();
        op.cursorCharAfter = view.cursorState.getCursorChar();
        op.timestamp = System.currentTimeMillis();
        view.history.recordEdit(op);
      } else {
        int nextGlobal = view.cursorState.getCursorLine() + 1;
        if (view.isEof && nextGlobal >= view.windowStartLine + view.linesWindow.size()) return;

        view.scrollManager.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - view.windowStartLine;
        if (nextLocal >= 0 && nextLocal < view.linesWindow.size()) {
          String next = view.viewRender.textRender.getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          view.viewRender.textRender.updateLocalLine(localIdx, merged);
          view.linesWindow.remove(nextLocal);
          view.modifiedLines.put(view.cursorState.getCursorLine(), merged);
          view.viewRender.textRender.recalculateMaxLineWidth();
          view.viewRender.textRender.computeWidthForLine(view.cursorState.getCursorLine(), merged);
          view.wrapWordBuilder.onLineCountChanged(view);
          view.invalidate();
          view.history.addLineCountDelta(-1);

          EditOp op = new EditOp();
          op.startLine = beforeLine;
          op.startChar = base.length();
          op.endLine = nextGlobal;
          op.endChar = 0;
          op.removedText = "\n";
          op.insertedText = "";
          op.insertedEndLine = beforeLine;
          op.insertedEndChar = base.length();
          op.cursorLineBefore = beforeLine;
          op.cursorCharBefore = beforeChar;
          op.cursorLineAfter = view.cursorState.getCursorLine();
          op.cursorCharAfter = view.cursorState.getCursorChar();
          op.timestamp = System.currentTimeMillis();
          view.history.recordEdit(op);
        }
      }
    }
    view.inlinePredictionEngine.updateSuggestion();
  }

  public void replaceSelectionWithText(String insertText) {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.editorIO.invalidatePendingIOForEdit();
    final int opToken = view.history.incrementEditVersion();
    view.inlinePredictionState.clearActiveSuggestion();

    if (insertText == null) insertText = "";

    if (!view.selectionState.hasSelection()) {
      if (!insertText.isEmpty()) view.cursorState.setCursorPosition(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
      view.inlinePredictionEngine.updateSuggestion();
      return;
    }

    int sL = view.selectionState.selStartLine, sC = view.selectionState.selStartChar, eL = view.selectionState.selEndLine, eC = view.selectionState.selEndChar;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = view.cursorState.getCursorLine();
    final int beforeChar = view.cursorState.getCursorChar();
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = view.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > view.history.getUndoTextLimit()) {
        removedText = null;
      }
    }
    int removedNewlines = view.editorTextInserter.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = view.editorTextInserter.countNewlines(insertText);

    final boolean selectAllLike =
        view.selectionState.isSelectAllActive() || view.selectionState.isEntireFileSelected();
    view.beginLargeEditUiIfNeeded(true, sL, eL, selectAllLike);

    if (selectAllLike) {
      synchronized (view.linesWindow) {
        view.linesWindow.clear();
        view.linesWindow.add("");
        view.windowStartLine = 0;
        view.isEof = true;
      }
      synchronized (view.modifiedLines) {
        view.modifiedLines.clear();
      }
      synchronized (view.lineWidthCache) {
        view.lineWidthCache.clear();
      }
      view.currentMaxWindowLineWidth = 0f;
      view.globalMaxLineWidth = 0f;
      view.scrollManager.maxLineWidthForScroll = 0f;
      view.scrollManager.maxTextStartXForScroll = 0f;
      view.scrollManager.maxScrollXForScroll = 0f;

      view.fileManager.setFileCleared(true);
      synchronized (view.fileManager.lineOffsetsLock) {
        view.fileManager.setLineOffsets(new long[0]);
      }
      view.fileManager.isIndexReady = false;
      view.fileManager.isIndexBuilding = false;
      view.fileManager.isIndexDisabled = false;
      view.fileManager.indexDisabledPath = null;
      view.fileManager.indexDisabledFileLength = -1L;
      view.fileManager.syncIndexFieldsToView();

      view.cursorState.setCursorPosition(0, 0);
      view.selectionState.setSelection(0, 0, 0, 0, false);
      view.scrollManager.scrollY = 0;
      view.scrollManager.scrollX = 0;
      view.clearSelectionStateAfterDelete();

      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (view.linesWindow) {
          view.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            view.linesWindow.add(i, newLines[i]);
          }
        }
        com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget newPos = view.computeCursorAfterInsert(0, 0, insertText);
        view.cursorState.setCursorPosition(newPos.line, newPos.ch);
      }

      view.wrapWordBuilder.onLineCountChanged(view);
      view.viewRender.textRender.recalculateMaxLineWidth();
    } else {
      try {
        view.fileManager.rewriteReplaceRangeAsync(opToken, view.fileManager.getSourceFile(), sL, sC, eL, eC, insertText, view.computeCursorAfterInsert(sL, sC, insertText), false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    view.history.addLineCountDelta((insertedNewlines - removedNewlines));
    view.recordReplaceSelectionEdit(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
    view.inlinePredictionEngine.updateSuggestion();
  }

  public void insertStringAtCursor(String text) {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.editorIO.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();

    if (view.cursorState.hasComposing()) {
      view.imeCompositionHandler.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorState.getCursorLine();
    final int beforeChar = view.cursorState.getCursorChar();

    view.scrollManager.ensureLineInWindow(view.cursorState.getCursorLine(), true);
    if (view.isWindowLoading
        && (view.cursorState.getCursorLine() < view.windowStartLine || view.cursorState.getCursorLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(() -> insertStringAtCursor(text));
      return;
    }

    int localIdx = view.cursorState.getCursorLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) view.linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, view.linesWindow.size() - 1));
    }

    synchronized (view.linesWindow) {
      String base = view.viewRender.textRender.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      int pos = Math.max(0, Math.min(view.cursorState.getCursorChar(), base.length()));
      String modified = base.substring(0, pos) + text + base.substring(pos);
      view.viewRender.textRender.updateLocalLine(localIdx, modified);
      view.modifiedLines.put(view.cursorState.getCursorLine(), modified);
      view.highlightState.invalidateHighlightCacheForLine(view.cursorState.getCursorLine());
      
      com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget insertedEnd = view.computeCursorAfterInsert(beforeLine, beforeChar, text);
      view.cursorState.setCursorPosition(insertedEnd.line, insertedEnd.ch);
      
      float newWidth =
          view.whitespaceGuideRenderer.measureTextWithVisualSpaces(
              view, modified, 0, modified.length(), view.editorConfig.paint);
      synchronized (view.lineWidthCache) {
        view.lineWidthCache.put(view.cursorState.getCursorLine(), newWidth);
      }
      view.currentMaxWindowLineWidth = Math.max(view.currentMaxWindowLineWidth, newWidth);
      view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, view.currentMaxWindowLineWidth);
      
      view.invalidate();
      view.scrollManager.keepCursorVisibleHorizontally();
    }
    view.inlinePredictionEngine.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = text;
    com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget insertedEnd = view.computeCursorAfterInsert(beforeLine, beforeChar, text);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = view.cursorState.getCursorLine();
    op.cursorCharAfter = view.cursorState.getCursorChar();
    op.timestamp = System.currentTimeMillis();
    view.history.recordEdit(op);
  }

  public static final int LARGE_PASTE_LINES = 1500;
  public static final int LARGE_PASTE_CHARS = 200_000;

  public static boolean isLargePasteText(String text) {
    if (text == null) return false;
    if (text.length() >= LARGE_PASTE_CHARS) return true;
    int newLines = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n' && ++newLines >= LARGE_PASTE_LINES) return true;
    }
    return false;
  }

  public void handleAutoPairing(String text) {
    if (!view.isAutoPairingEnabled || text == null || text.length() == 0 || text.length() >= 100) return;

    char c = text.charAt(text.length() - 1);
    String closing = null;
    if (c == '(') closing = ")";
    else if (c == '{') closing = "}";
    else if (c == '[') closing = "]";
    else if (c == '"') closing = "\"";
    else if (c == '\'') closing = "'";
    else if (c == '`') closing = "`";
    else if (c == '*') {
      if (view.cursorState.getCursorChar() >= 2) {
        String ln = view.viewRender.textRender.getLineTextForRender(view.cursorState.getCursorLine());
        if (ln != null && ln.length() >= view.cursorState.getCursorChar() && ln.charAt(view.cursorState.getCursorChar() - 2) == '/') {
          closing = "*/";
        }
      }
    }

    if (closing != null) {
      view.editorTextInserter.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        view.cursorNavigation.moveCursorLeft();
      }
    }
  }

  private static String rstripWhitespace(String text) {
    if (text == null || text.isEmpty()) return "";
    int end = text.length();
    while (end > 0) {
      char c = text.charAt(end - 1);
      if (c != ' ' && c != '\t') break;
      end--;
    }
    return (end == text.length()) ? text : text.substring(0, end);
  }

  private static int findPrevNonWhitespaceIndex(String text, int start) {
    if (text == null || text.isEmpty()) return -1;
    for (int i = Math.min(start, text.length() - 1); i >= 0; i--) {
      if (!Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }

  private static String buildIndentFromWidth(int width) {
    if (width <= 0) return "";
    char[] buf = new char[width];
    for (int i = 0; i < width; i++) buf[i] = ' ';
    return new String(buf);
  }

  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }
}
