package com.yn.sodiumeditor.input;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.*;
import com.yn.sodiumeditor.core.EditOp;

public final class EditorOperations {
  private final SodiumEditorView view;

  public EditorOperations(SodiumEditorView view) {
    this.view = view;
  }

  public void insertCharAtCursor(char c) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.setHasComposing(false);
      view.cursorManager.setComposingLength(0);
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) {
      synchronized (view.linesWindow) {
        if (view.linesWindow.isEmpty()) view.linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, view.linesWindow.size() - 1));
    }

    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        int oldLineCount = view.getLinesCount();
        String before = base.substring(0, Math.min(view.cursorManager.getChar(), base.length()));
        String after = base.substring(Math.min(view.cursorManager.getChar(), base.length()));
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());

        view.updateLocalLinePublic(localIdx, before);
        view.linesWindow.add(localIdx + 1, after);

        view.modifiedLines.put(view.cursorManager.getLine(), before);
        view.modifiedLines.put(view.cursorManager.getLine() + 1, after);

        view.computeWidthForLinePublic(view.cursorManager.getLine(), before);
        view.computeWidthForLinePublic(view.cursorManager.getLine() + 1, after);

        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.highlightManager.clearHighlightCaches();
        view.cursorManager.setLineAndChar(view.cursorManager.getLine() + 1, 0);
        view.history.addLineCountDelta(1);

        int newLineCount = view.getLinesCount();
        if (view.lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wordWrapManager.onLineCountChanged(view);
      } else {
        int pos = Math.max(0, Math.min(view.cursorManager.getChar(), base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.highlightManager.invalidateHighlightCacheForLine(view.cursorManager.getLine());
        view.cursorManager.moveCharDelta(1);
        float newWidth =
            view.whitespaceGuideManager.measureTextWithVisualSpaces(
                view, modified, 0, modified.length(), view.paint);
        synchronized (view.lineWidthCache) {
          view.lineWidthCache.put(view.cursorManager.getLine(), newWidth);
        }
        view.currentMaxWindowLineWidth = Math.max(view.currentMaxWindowLineWidth, newWidth);
        view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, view.currentMaxWindowLineWidth);
      }
      view.invalidate();
      view.scrollManager.keepCursorVisibleHorizontally();
    }
    view.autoSuggestionManager.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    SodiumEditorView.CursorTarget insertedEnd = view.computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = view.cursorManager.getLine();
    op.cursorCharAfter = view.cursorManager.getChar();
    op.timestamp = System.currentTimeMillis();
    view.recordEdit(op);
  }

  public void insertNewlineAtCursor() {
    if (view.isReadOnly) return;
    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText("\n");
      return;
    }

    CursorManager.BracketPairType pairType = view.cursorManager.getCursorBracketPairType();
    if (view.isAutoBracketNewlineEnabled && pairType != CursorManager.BracketPairType.NONE) {
      String baseIndent = "";
      String innerIndent = "";
      if (view.isAutoBracketNewlineIndentEnabled) {
        baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
        innerIndent = baseIndent + "  ";
      }

      String closeIndent = (pairType == CursorManager.BracketPairType.CURLY) ? baseIndent : innerIndent;
      String insertText = "\n" + innerIndent + "\n" + closeIndent;

      int targetLine = view.cursorManager.getLine() + 1;
      int targetChar = innerIndent.length();
      view.insertTextAtCursor(insertText);

      view.cursorManager.setLineAndChar(targetLine, targetChar);
      view.cursorAnimationManager.resetCursorBlink();
      view.scrollManager.keepCursorVisibleHorizontally();
      view.invalidate();
      view.autoSuggestionManager.updateSuggestion();
      return;
    }

    if (view.isAutoIndentAfterClosingBracketEnabled) {
      String ln = view.getLineTextForRender(view.cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      int prevNonWs = findPrevNonWhitespaceIndex(before, before.length() - 1);
      if (prevNonWs >= 0) {
        char c = before.charAt(prevNonWs);
        if (c == '{' || c == '}') {
          String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
          int baseWidth = view.getIndentWidth(baseIndent);
          int unit = SodiumEditorView.INDENT_BLOCK_UNIT.length();
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
          view.insertTextAtCursor("\n" + buildIndentFromWidth(targetWidth));
          return;
        }
      }
    }

    if (view.isIndentationBlocksEnabled) {
      String ln = view.getLineTextForRender(view.cursorManager.getLine());
      if (ln == null) ln = "";
      int safeChar = Math.max(0, Math.min(view.cursorManager.getChar(), ln.length()));
      String before = ln.substring(0, safeChar);
      String trimmed = rstripWhitespace(before);
      String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
      String extraIndent = trimmed.endsWith(":") ? SodiumEditorView.INDENT_BLOCK_UNIT : "";
      view.insertTextAtCursor("\n" + baseIndent + extraIndent);
      return;
    }

    if (view.isAutoBracketNewlineIndentEnabled) {
      String baseIndent = view.getLineLeadingWhitespace(view.cursorManager.getLine());
      view.insertTextAtCursor("\n" + baseIndent);
      return;
    }

    insertCharAtCursor('\n');
  }

  public void deleteCharAtCursor() {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) return;

    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorManager.getChar() > 0) {
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());
        int safeStart = Math.max(0, view.cursorManager.getChar() - 1);
        String removed = base.substring(safeStart, Math.min(view.cursorManager.getChar(), base.length()));
        boolean atLineEnd = view.cursorManager.getChar() >= base.length();
        if (view.charAnimationManager.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightManager.getPaintForChar(view.cursorManager.getLine(), safeStart, base);
          view.charAnimationManager.startDeleteAnimation(view.cursorManager.getLine(), safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(view.cursorManager.getChar());
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.highlightManager.invalidateHighlightCacheForLine(view.cursorManager.getLine());
        view.cursorManager.setChar(safeStart);
        view.computeWidthForLinePublic(view.cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.invalidateLineGlobal(view.cursorManager.getLine());

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
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      } else if (view.cursorManager.getLine() > 0) {
        int oldLineCount = view.getLinesCount();
        int prevGlobal = view.cursorManager.getLine() - 1;
        view.scrollManager.ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - view.windowStartLine;
        if (prevLocal < 0 || prevLocal >= view.linesWindow.size()) return;

        String prev = view.getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        view.updateLocalLinePublic(prevLocal, merged);
        view.modifiedLines.put(prevGlobal, merged);
        view.highlightManager.clearHighlightCaches();

        if (localIdx < view.linesWindow.size()) view.linesWindow.remove(localIdx);

        view.recalculateMaxLineWidth();
        view.cursorManager.setLineAndChar(prevGlobal, prev.length());
        view.computeWidthForLinePublic(prevGlobal, merged);
        view.history.addLineCountDelta(-1);

        int newLineCount = view.getLinesCount();
        if (view.lineNumberManager.isShowLineNumbers()
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          view.requestLayout();
        }
        view.wordWrapManager.onLineCountChanged(view);
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
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      }
    }
    view.autoSuggestionManager.updateSuggestion();
  }

  public void deleteForwardAtCursor() {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    view.history.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (view.cursorManager.getHasComposing()) {
      view.cursorManager.deleteComposing();
      return;
    }

    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (view.isWindowLoading
        && (view.cursorManager.getLine() < view.windowStartLine || view.cursorManager.getLine() >= view.windowStartLine + view.linesWindow.size())) {
      view.mainHandler.post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = view.cursorManager.getLine() - view.windowStartLine;
    synchronized (view.linesWindow) {
      String base = view.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (view.cursorManager.getChar() < base.length()) {
        Float oldWidth = view.lineWidthCache.get(view.cursorManager.getLine());
        String removed = base.substring(view.cursorManager.getChar(), Math.min(view.cursorManager.getChar() + 1, base.length()));
        boolean atLineEnd = view.cursorManager.getChar() == base.length() - 1;
        if (view.charAnimationManager.isEnabled() && atLineEnd) {
          android.graphics.Paint p = view.highlightManager.getPaintForChar(view.cursorManager.getLine(), view.cursorManager.getChar(), base);
          view.charAnimationManager.startDeleteAnimation(view.cursorManager.getLine(), view.cursorManager.getChar(), removed, p);
        }
        String modified = base.substring(0, view.cursorManager.getChar()) + base.substring(view.cursorManager.getChar() + 1);
        view.updateLocalLinePublic(localIdx, modified);
        view.modifiedLines.put(view.cursorManager.getLine(), modified);
        view.computeWidthForLinePublic(view.cursorManager.getLine(), modified);
        if (oldWidth != null && oldWidth >= view.currentMaxWindowLineWidth)
          view.recalculateMaxLineWidthAsync();
        view.invalidateLineGlobal(view.cursorManager.getLine());

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
        op.cursorLineAfter = view.cursorManager.getLine();
        op.cursorCharAfter = view.cursorManager.getChar();
        op.timestamp = System.currentTimeMillis();
        view.recordEdit(op);
      } else {
        int nextGlobal = view.cursorManager.getLine() + 1;
        if (view.isEof && nextGlobal >= view.windowStartLine + view.linesWindow.size()) return;

        view.scrollManager.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - view.windowStartLine;
        if (nextLocal >= 0 && nextLocal < view.linesWindow.size()) {
          String next = view.getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          view.updateLocalLinePublic(localIdx, merged);
          view.linesWindow.remove(nextLocal);
          view.modifiedLines.put(view.cursorManager.getLine(), merged);
          view.recalculateMaxLineWidth();
          view.computeWidthForLinePublic(view.cursorManager.getLine(), merged);
          view.wordWrapManager.onLineCountChanged(view);
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
          op.cursorLineAfter = view.cursorManager.getLine();
          op.cursorCharAfter = view.cursorManager.getChar();
          op.timestamp = System.currentTimeMillis();
          view.recordEdit(op);
        }
      }
    }
    view.autoSuggestionManager.updateSuggestion();
  }

  public void replaceSelectionWithText(String insertText) {
    if (view.isReadOnly) return;
    view.invalidatePendingIOForEdit();
    final int opToken = view.history.incrementEditVersion();
    view.autoSuggestionManager.clearActiveSuggestion();

    if (insertText == null) insertText = "";

    if (!view.selectionManager.hasSelection()) {
      if (!insertText.isEmpty()) view.cursorManager.insertTextAtCursor(insertText);
      view.autoSuggestionManager.updateSuggestion();
      return;
    }

    int sL = view.selectionManager.selStartLine, sC = view.selectionManager.selStartChar, eL = view.selectionManager.selEndLine, eC = view.selectionManager.selEndChar;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    final int beforeLine = view.cursorManager.getLine();
    final int beforeChar = view.cursorManager.getChar();
    String removedText = null;
    if (Math.abs(eL - sL) <= 5000) {
      removedText = view.readRangeText(sL, sC, eL, eC);
      if (removedText != null && removedText.length() > view.history.getUndoTextLimit()) {
        removedText = null;
      }
    }
    int removedNewlines = view.countNewlines(removedText);
    if (removedText == null && eL >= sL) {
      removedNewlines = Math.max(0, eL - sL);
    }
    int insertedNewlines = view.countNewlines(insertText);

    final boolean selectAllLike =
        view.selectionManager.isSelectAllActive() || view.selectionManager.isEntireFileSelected();
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

      view.cursorManager.setLineAndChar(0, 0);
      view.selectionManager.setSelection(0, 0, 0, 0, false);
      view.scrollManager.scrollY = 0;
      view.scrollManager.scrollX = 0;
      view.clearSelectionStateAfterDeletePublic();

      if (!insertText.isEmpty()) {
        String[] newLines = insertText.split("\n", -1);
        synchronized (view.linesWindow) {
          view.linesWindow.set(0, newLines[0]);
          for (int i = 1; i < newLines.length; i++) {
            view.linesWindow.add(i, newLines[i]);
          }
        }
        SodiumEditorView.CursorTarget newPos = view.computeCursorAfterInsert(0, 0, insertText);
        view.cursorManager.setLineAndChar(newPos.line, newPos.ch);
      }

      view.wordWrapManager.onLineCountChanged(view);
      view.recalculateMaxLineWidth();
    } else {
      view.fileManager.rewriteReplaceRangeAsync(opToken, view.fileManager.getSourceFile(), sL, sC, eL, eC, insertText, view.computeCursorAfterInsert(sL, sC, insertText), false);
    }

    view.history.addLineCountDelta((insertedNewlines - removedNewlines));
    view.recordReplaceSelectionEditPublic(sL, sC, eL, eC, removedText, insertText, beforeLine, beforeChar);
    view.autoSuggestionManager.updateSuggestion();
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
      if (view.cursorManager.getChar() >= 2) {
        String ln = view.getLineTextForRender(view.cursorManager.getLine());
        if (ln != null && ln.length() >= view.cursorManager.getChar() && ln.charAt(view.cursorManager.getChar() - 2) == '/') {
          closing = "*/";
        }
      }
    }

    if (closing != null) {
      view.cursorManager.insertTextAtCursor(closing);
      for (int i = 0; i < closing.length(); i++) {
        view.cursorManager.moveCursorLeft();
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
