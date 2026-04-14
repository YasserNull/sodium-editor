package com.yn.sodiumeditor.input;

import android.text.Editable;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.io.EditOp;

/**
 * Ime handles all Input Method Editor (IME) logic for SodiumEditor.
 */
public class Ime {

  public static final int IME_CONTEXT_BEFORE_CHARS = 2048;
  public static final int IME_CONTEXT_AFTER_CHARS = 2048;

  public final Editable imeEditable = android.text.Editable.Factory.getInstance().newEditable("");

  public boolean hasComposing = false;
  public int composingLine = 0, composingOffset = 0, composingLength = 0;
  public int composingStartLine = -1;
  public int composingStartChar = 0;
  public boolean composingStartActive = false;
  @Nullable public EditOp composingPendingOp = null;
  @Nullable public String lastComposingTextForCharAnim;

  @Nullable public String lastImeCommitText;
  public long lastImeCommitUptime = 0L;
  public boolean suppressNextCommitText = false;

  public boolean imeExtractedTextMonitor = false;
  public int imeExtractedTextToken = 0;
  public int imeExtractedBeforeChars = IME_CONTEXT_BEFORE_CHARS;
  public int imeExtractedAfterChars = IME_CONTEXT_AFTER_CHARS;

  final SodiumEditor editor;
  final ImeScanner scanner;

  public Ime(SodiumEditor editor) {
    this.editor = editor;
    this.scanner = new ImeScanner(editor);
  }

  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (editor.isDisabled || editor.isReadOnly) return null;
    
    outAttrs.inputType =
        EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    outAttrs.imeOptions =
        EditorInfo.IME_ACTION_NONE
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    
    ImeContext ctx = scanner.buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);
    ExtractedText et = scanner.buildExtractedTextFromContext(ctx);
    outAttrs.initialSelStart = et.selectionStart;
    outAttrs.initialSelEnd = et.selectionEnd;

    return new SodiumInputConnection(editor, this);
  }

  // --- Methods called by SodiumInputConnection ---

  public ExtractedText onGetExtractedText(ExtractedTextRequest request, int flags) {
    int before = IME_CONTEXT_BEFORE_CHARS;
    int after = IME_CONTEXT_AFTER_CHARS;
    if (request != null && request.hintMaxChars > 0) {
      int half = Math.max(1, request.hintMaxChars / 2);
      before = Math.min(before, half);
      after = Math.min(after, Math.max(0, request.hintMaxChars - before));
    }
    if (request != null && (flags & InputConnection.GET_EXTRACTED_TEXT_MONITOR) != 0) {
      imeExtractedTextMonitor = true;
      imeExtractedTextToken = request.token;
    }
    imeExtractedBeforeChars = before;
    imeExtractedAfterChars = after;
    ImeContext ctx = scanner.buildImeContext(before, after);
    return scanner.buildExtractedTextFromContext(ctx);
  }

  public boolean onSetSelection(int start, int end) {
    ImeContext ctx = scanner.buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
    if (ctx.text.isEmpty()) return true;
    int textLen = ctx.text.length();
    int sOff = Math.max(0, Math.min(start, textLen));
    int eOff = Math.max(0, Math.min(end, textLen));
    int cursorOff = scanner.lineCharToOffsetInContext(ctx, editor.cursor.cursorLine, editor.cursor.cursorChar);
    if (sOff == eOff && sOff == cursorOff && !editor.selection.hasSelection) return true;
    if (sOff == 0 && eOff == 0 && cursorOff > 0 && !editor.selection.hasSelection) return true;
    CursorTarget s = scanner.offsetToLineCharInContext(ctx, sOff);
    CursorTarget e = scanner.offsetToLineCharInContext(ctx, eOff);
    editor.selection.setSelectionInternal(s.line, s.ch, e.line, e.ch);
    editor.cursor.cursorLine = e.line;
    editor.cursor.cursorChar = e.ch;
    editor.caret.resetBlink();
    editor.invalidate();
    editor.autoCompletion.updateSuggestion();
    return true;
  }

  public boolean onSetComposingRegion(int start, int end) {
    if (start > end) { int t = start; start = end; end = t; }
    ImeContext ctx = scanner.buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
    if (ctx.text.isEmpty()) return true;
    int textLen = ctx.text.length();
    int sOff = Math.max(0, Math.min(start, textLen));
    int eOff = Math.max(0, Math.min(end, textLen));
    CursorTarget s = scanner.offsetToLineCharInContext(ctx, sOff);
    CursorTarget e = scanner.offsetToLineCharInContext(ctx, eOff);
    if (s.line != e.line) {
      editor.selection.setSelectionInternal(s.line, s.ch, e.line, e.ch);
      editor.cursor.cursorLine = e.line;
      editor.cursor.cursorChar = e.ch;
      editor.caret.resetBlink();
      editor.invalidate();
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    composingLine = s.line;
    composingOffset = s.ch;
    composingLength = Math.max(0, e.ch - s.ch);
    hasComposing = true;
    composingStartLine = composingLine;
    composingStartChar = composingOffset;
    composingStartActive = true;
    lastComposingTextForCharAnim = null;
    editor.invalidate();
    editor.autoCompletion.updateSuggestion();
    return true;
  }

  public void onFinishComposingText() {
    if (lastComposingTextForCharAnim != null && !lastComposingTextForCharAnim.isEmpty()) {
      markImeCommit(lastComposingTextForCharAnim);
    }
    commitComposing(true);
  }

  public boolean onCommitCompletion(CharSequence text) {
    if (!hasComposing && !editor.selection.hasSelection && replaceWordAtCursorWith(text)) {
      markImeCommit(text);
      return true;
    }
    return onCommitText(text, 1);
  }

  public boolean onCommitCorrection(CharSequence text) {
    if (!hasComposing && !editor.selection.hasSelection && replaceWordAtCursorWith(text)) {
      markImeCommit(text);
      return true;
    }
    return onCommitText(text, 1);
  }

  public boolean onCommitText(CharSequence text, int newCursorPosition) {
    String str = text.toString();
    if ("\n".equals(str)) {
      editor.autoBracketNewline.insertNewlineAtCursor();
      commitComposing(true);
      editor.charAnimation.startCharAnimationFromText(text);
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    if (tryReplaceWordFromImeCommit(str)) {
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    if (!hasComposing && !editor.selection.hasSelection && lastImeCommitText != null) {
      long now = android.os.SystemClock.uptimeMillis();
      if (now - lastImeCommitUptime < 700 && str.trim().isEmpty()) {
        int[] bounds = getWordBoundsAtCursor();
        if (bounds != null) {
          String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
          if (line != null && bounds[0] < bounds[1] && bounds[1] <= line.length()) {
            String word = line.substring(bounds[0], bounds[1]);
            if (!word.isEmpty() && lastImeCommitText.startsWith(word)) {
              if (!word.equals(lastImeCommitText)) {
                editor.selection.setSelectionInternal(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
                editor.selection.replaceSelectionWithText(lastImeCommitText);
              }
              editor.editOperators.insertTextAtCursor(str);
              suppressNextCommitText = false;
              return true;
            }
          }
        }
      }
    }
    if (!hasComposing && !editor.selection.hasSelection) {
      long now = android.os.SystemClock.uptimeMillis();
      boolean recentIme = suppressNextCommitText || (lastImeCommitText != null && (now - lastImeCommitUptime) < 700);
      if (recentIme) {
        int anchorLen = Math.max(str.length(), (lastImeCommitText == null) ? 0 : lastImeCommitText.length());
        int beforeLen = Math.max(32, Math.min(256, anchorLen + 4));
        String before = scanner.getImeTextBeforeCursor(beforeLen);
        if (before != null && !before.isEmpty()) {
          if (!str.isEmpty() && before.endsWith(str)) {
            suppressNextCommitText = false;
            return true;
          }
          if (lastImeCommitText != null && !lastImeCommitText.isEmpty() && before.endsWith(lastImeCommitText)) {
            if (str.equals(lastImeCommitText)) {
              suppressNextCommitText = false;
              return true;
            }
            if (str.startsWith(lastImeCommitText)) {
              String suffix = str.substring(lastImeCommitText.length());
              suppressNextCommitText = false;
              if (!suffix.isEmpty()) {
                String trimmed = suffix.trim();
                if (trimmed.equals(lastImeCommitText)) return true;
                editor.editOperators.insertTextAtCursor(suffix);
                commitComposing(true);
                editor.charAnimation.startCharAnimationFromText(suffix);
                editor.autoBracketPair.handleAutoPairing(suffix);
                editor.autoCompletion.updateSuggestion();
              }
              return true;
            }
          }
        }
        suppressNextCommitText = false;
      }
    }
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(str);
      commitComposing(true);
      editor.charAnimation.startCharAnimationFromText(text);
      editor.autoBracketPair.handleAutoPairing(str);
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    if (hasComposing) {
      int startLine = composingStartActive ? composingStartLine : composingLine;
      int startChar = composingStartActive ? composingStartChar : composingOffset;
      replaceComposingWith(text);
      updateComposingPendingOp(str, startLine, startChar);
      commitComposing(true);
      markImeCommit(str);
      editor.charAnimation.startCharAnimationFromText(text);
      editor.autoBracketPair.handleAutoPairing(str);
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    editor.editOperators.insertTextAtCursor(str);
    commitComposing(true);
    editor.charAnimation.startCharAnimationFromText(text);
    editor.autoBracketPair.handleAutoPairing(str);
    editor.autoCompletion.updateSuggestion();
    return true;
  }

  public boolean onSetComposingText(CharSequence text, int newCursorPosition) {
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(text.toString());
      editor.charAnimation.startCharAnimationFromText(text);
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (!hasComposing) {
      composingLine = editor.cursor.cursorLine;
      composingOffset = editor.cursor.cursorChar;
      composingLength = 0;
      hasComposing = true;
      composingStartLine = composingLine;
      composingStartChar = composingOffset;
      composingStartActive = true;
    }
    String newText = text.toString();
    String oldText = (lastComposingTextForCharAnim == null) ? "" : lastComposingTextForCharAnim;
    boolean shouldAnim = newText.length() >= oldText.length() && !newText.equals(oldText);
    replaceComposingWith(newText);
    updateComposingPendingOp(newText, composingStartLine, composingStartChar);
    lastComposingTextForCharAnim = newText;
    if (shouldAnim) editor.charAnimation.startCharAnimationFromText(newText);
    editor.autoCompletion.updateSuggestion();
    return true;
  }

  public boolean onDeleteSurroundingText(int beforeLength, int afterLength) {
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText("");
      editor.autoCompletion.updateSuggestion();
      return true;
    }
    for (int i = 0; i < beforeLength; i++) editor.editOperators.deleteCharAtCursor();
    for (int i = 0; i < afterLength; i++) editor.editOperators.deleteForwardAtCursor();
    editor.autoCompletion.updateSuggestion();
    return true;
  }

  // --- End of methods called by SodiumInputConnection ---

  public void updateImeSelection() {
    if (editor.isDisabled || editor.isReadOnly) return;
    if (!editor.isFocused()) return;
    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm == null || !imm.isActive(editor)) return;

    ImeContext ctx = scanner.buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);
    int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
    int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
    if (editor.selection.hasSelection) {
      sLine = editor.selection.selStartLine; sChar = editor.selection.selStartChar;
      eLine = editor.selection.selEndLine; eChar = editor.selection.selEndChar;
      if (scanner.comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar; sLine = eLine; sChar = eChar; eLine = tL; eChar = tC;
      }
    }

    int selStart = scanner.lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = scanner.lineCharToOffsetInContext(ctx, eLine, eChar);
    int compStart = -1, compEnd = -1;
    if (hasComposing) {
      compStart = scanner.lineCharToOffsetInContext(ctx, composingLine, composingOffset);
      compEnd = scanner.lineCharToOffsetInContext(ctx, composingLine, composingOffset + composingLength);
    }
    imm.updateSelection(editor, selStart, selEnd, compStart, compEnd);
    if (imeExtractedTextMonitor) {
      ExtractedText et = scanner.buildExtractedTextFromContext(ctx);
      imm.updateExtractedText(editor, imeExtractedTextToken, et);
    }
  }

  public void commitComposing(boolean keepInText) {
    if (!hasComposing) return;
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    composingPendingOp = null;
    lastComposingTextForCharAnim = null;
    editor.invalidate();
    editor.autoCompletion.updateSuggestion();
  }

  public void replaceComposingWith(CharSequence textSeq) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    editor.editOperators.editVersion.incrementAndGet();
    editor.fileIO.ensureLineInWindow(composingLine, true);
    if (editor.fileIO.isWindowLoading && (composingLine < editor.textRender.windowStartLine || composingLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(() -> replaceComposingWith(textSeq));
      return;
    }
    int local = composingLine - editor.textRender.windowStartLine;
    synchronized (editor.textRender.linesWindow) {
      String base = editor.getLineFromWindowLocal(local);
      if (base == null) base = "";
      int start = Math.max(0, Math.min(composingOffset, base.length()));
      int end = Math.max(0, Math.min(composingOffset + composingLength, base.length()));
      if (editor.charAnimation.isCharAnimationEnabled) {
        String oldComposing = base.substring(start, end);
        String newComposing = (textSeq == null) ? "" : textSeq.toString();
        if (newComposing.length() < oldComposing.length()) {
          String removed = null; int at = start;
          if (oldComposing.startsWith(newComposing)) {
            removed = oldComposing.substring(newComposing.length()); at = start + newComposing.length();
          } else if (oldComposing.endsWith(newComposing)) {
            removed = oldComposing.substring(0, oldComposing.length() - newComposing.length()); at = start;
          }
          if (removed != null && !removed.isEmpty()) {
            android.graphics.Paint p = editor.textRender.getPaintForChar(composingLine, at, base);
            editor.charAnimation.startDeleteAnimation(composingLine, at, removed, p);
          }
        }
      }
      String newLine = base.substring(0, start) + textSeq + base.substring(end);
      editor.updateLocalLine(local, newLine);
      editor.textRender.modifiedLines.put(composingLine, newLine);
      editor.wordWrap.onLineContentChanged(composingLine, newLine);
      editor.clearStreamedLineInfo(composingLine);
      editor.highlite.invalidateHighlightCacheForLine(composingLine);
      editor.lineNumber.invalidateLineNumberCache();
      composingLength = textSeq.length();
      editor.cursor.cursorLine = composingLine;
      editor.cursor.cursorChar = composingOffset + composingLength;
      editor.computeWidthForLine(composingLine, newLine);
      editor.recalculateMaxLineWidth();
      editor.invalidateLineGlobal(composingLine);
      editor.scroll.keepCursorVisibleHorizontally();
      editor.invalidate();
    }
    editor.autoCompletion.updateSuggestion();
  }

  public void deleteComposing() {
    if (!hasComposing) return;
    replaceComposingWith("");
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    lastComposingTextForCharAnim = null;
  }

  public void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    if (!hasComposing) return;
    if (text == null) text = "";
    if (text.length() > EditOperators.UNDO_TEXT_LIMIT) return;
    int startLine = composingStartActive ? composingStartLine : composingLine;
    int startChar = composingStartActive ? composingStartChar : composingOffset;

    if (composingPendingOp == null) {
      if (text.isEmpty()) return;
      EditOp op = new EditOp();
      op.startLine = startLine; op.startChar = startChar; op.endLine = startLine; op.endChar = startChar;
      op.removedText = ""; op.insertedText = text;
      EditOp.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
      op.insertedEndLine = insertedEnd.line; op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine; op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine; op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.lineCountDelta += editor.editOperators.countNewlines(text);
      composingPendingOp = op;
      editor.editOperators.undoStack.addLast(op);
      while (editor.editOperators.undoStack.size() > EditOperators.UNDO_STACK_LIMIT) editor.editOperators.undoStack.removeFirst();
      editor.editOperators.redoStack.clear();
      editor.editOperators.pendingEdits.addLast(op);
      editor.editOperators.pendingRedo.clear();
      editor.editOperators.lastEditTimestamp = op.timestamp;
      return;
    }

    String prev = composingPendingOp.insertedText == null ? "" : composingPendingOp.insertedText;
    int prevNewlines = editor.editOperators.countNewlines(prev);
    int newNewlines = editor.editOperators.countNewlines(text);
    editor.editOperators.lineCountDelta += (newNewlines - prevNewlines);
    composingPendingOp.insertedText = text;
    EditOp.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
    composingPendingOp.insertedEndLine = insertedEnd.line; composingPendingOp.insertedEndChar = insertedEnd.ch;
    composingPendingOp.cursorLineAfter = editor.cursor.cursorLine; composingPendingOp.cursorCharAfter = editor.cursor.cursorChar;
    composingPendingOp.timestamp = System.currentTimeMillis();
    editor.editOperators.lastEditTimestamp = composingPendingOp.timestamp;
    if (text.isEmpty()) {
      editor.editOperators.pendingEdits.remove(composingPendingOp);
      editor.editOperators.undoStack.remove(composingPendingOp);
      composingPendingOp = null;
    }
  }

  public void markImeCommit(CharSequence textSeq) {
    if (textSeq == null) return;
    lastImeCommitText = textSeq.toString();
    lastImeCommitUptime = android.os.SystemClock.uptimeMillis();
    suppressNextCommitText = true;
  }

  public boolean replaceWordAtCursorWith(CharSequence textSeq) {
    if (textSeq == null) return false;
    String insert = textSeq.toString();
    if (insert.isEmpty()) return false;
    if (editor.selection.hasSelection) { editor.selection.replaceSelectionWithText(insert); return true; }
    String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = editor.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    editor.selection.setSelectionInternal(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
    editor.selection.replaceSelectionWithText(insert);
    return true;
  }

  public boolean tryReplaceWordFromImeCommit(String insert) {
    if (editor.selection.hasSelection || hasComposing) return false;
    if (insert == null || insert.isEmpty() || insert.length() <= 1) return false;
    int end = insert.length();
    while (end > 0 && Character.isWhitespace(insert.charAt(end - 1))) end--;
    String core = insert.substring(0, end);
    String trailing = insert.substring(end);
    if (core.length() <= 1) return false;
    for (int i = 0; i < core.length(); i++) if (Character.isWhitespace(core.charAt(i))) return false;
    int[] bounds = getWordBoundsAtCursor();
    if (bounds == null) return false;
    String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || bounds[0] >= bounds[1] || bounds[1] > line.length()) return false;
    String word = line.substring(bounds[0], bounds[1]);
    if (word.isEmpty() || word.equals(core)) return false;
    editor.selection.setSelectionInternal(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
    editor.selection.replaceSelectionWithText(core);
    if (!trailing.isEmpty()) editor.editOperators.insertTextAtCursor(trailing);
    markImeCommit(insert);
    editor.charAnimation.startCharAnimationFromText(insert);
    return true;
  }

  @Nullable
  public int[] getWordBoundsAtCursor() {
    String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return null;
    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length() || Character.isWhitespace(line.charAt(pos))) return null;
    return editor.computeWordBounds(line, pos);
  }

  public void setImeExtractedTextMonitor(boolean enabled) { this.imeExtractedTextMonitor = enabled; }
  public void setImeExtractedTextToken(int token) { this.imeExtractedTextToken = token; }
  public void setImeContextSize(int beforeChars, int afterChars) {
    this.imeExtractedBeforeChars = Math.max(0, beforeChars);
    this.imeExtractedAfterChars = Math.max(0, afterChars);
  }
  public boolean hasComposing() { return hasComposing; }
  public void clearComposing() { deleteComposing(); }

  public void restartInput() {
    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.restartInput(editor);
  }

  public void hideKeyboard() {
    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
  }

  public void showKeyboard() {
    InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(editor, 0);
  }
}
