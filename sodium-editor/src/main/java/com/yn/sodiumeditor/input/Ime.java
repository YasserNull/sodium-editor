package com.yn.sodiumeditor.input;

import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOperators;
/**
 * Ime handles all Input Method Editor (IME) logic for SodiumEditor.
 * This includes:
 * - InputConnection implementation
 * - Composing text management
 * - IME context building
 * - Text extraction for IME
 * - Selection synchronization with IME
 */
public class Ime {

  // IME context constants
  public static final int IME_CONTEXT_BEFORE_CHARS = 2048;
  public static final int IME_CONTEXT_AFTER_CHARS = 2048;

  // IME editable
  public final Editable imeEditable = android.text.Editable.Factory.getInstance().newEditable("");

  // Composing state
  public boolean hasComposing = false;
  public int composingLine = 0, composingOffset = 0, composingLength = 0;
  public int composingStartLine = -1;
  public int composingStartChar = 0;
  public boolean composingStartActive = false;
  @Nullable public EditOperators.EditOp composingPendingOp = null;
  @Nullable public String lastComposingTextForCharAnim;

  // IME commit state
  @Nullable public String lastImeCommitText;
  public long lastImeCommitUptime = 0L;
  public boolean suppressNextCommitText = false;

  // IME extracted text monitoring
  public boolean imeExtractedTextMonitor = false;
  public int imeExtractedTextToken = 0;
  public int imeExtractedBeforeChars = IME_CONTEXT_BEFORE_CHARS;
  public int imeExtractedAfterChars = IME_CONTEXT_AFTER_CHARS;

  private final SodiumEditor editor;

  public Ime(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Create InputConnection for IME
   */
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
    
    ImeContext ctx = buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);
    ExtractedText et = buildExtractedTextFromContext(ctx);
    outAttrs.initialSelStart = et.selectionStart;
    outAttrs.initialSelEnd = et.selectionEnd;

    return new BaseInputConnection(editor, true) {
      @Override
      public Editable getEditable() {
        return imeEditable;
      }

      @Override
      public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (editor.isDisabled || editor.isReadOnly) return null;
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
        ImeContext ctx = buildImeContext(before, after);
        return buildExtractedTextFromContext(ctx);
      }

      @Override
      public CharSequence getTextBeforeCursor(int length, int flags) {
        if (editor.isDisabled || editor.isReadOnly) return "";
        return getImeTextBeforeCursor(length);
      }

      @Override
      public CharSequence getTextAfterCursor(int length, int flags) {
        if (editor.isDisabled || editor.isReadOnly) return "";
        return getImeTextAfterCursor(length);
      }

      @Override
      public CharSequence getSelectedText(int flags) {
        if (editor.isDisabled || editor.isReadOnly) return "";
        return editor.selection.getSelectedText();
      }

      @Override
      public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        if (editor.isDisabled || editor.isReadOnly) return null;
        int before = Math.max(0, beforeLength);
        int after = Math.max(0, afterLength);
        ImeContext ctx = buildImeContext(before, after);
        int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
        int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
        if (editor.selection.hasSelection) {
          sLine = editor.selection.selStartLine;
          sChar = editor.selection.selStartChar;
          eLine = editor.selection.selEndLine;
          eChar = editor.selection.selEndChar;
          if (comparePos(sLine, sChar, eLine, eChar) > 0) {
            int tL = sLine, tC = sChar;
            sLine = eLine;
            sChar = eChar;
            eLine = tL;
            eChar = tC;
          }
        }
        int selStart = lineCharToOffsetInContext(ctx, sLine, sChar);
        int selEnd = lineCharToOffsetInContext(ctx, eLine, eChar);
        return new SurroundingText(ctx.text, selStart, selEnd, 0);
      }

      @Override
      public int getCursorCapsMode(int reqModes) {
        CharSequence before = getTextBeforeCursor(2048, 0);
        int len = (before == null) ? 0 : before.length();
        return TextUtils.getCapsMode(before, len, reqModes);
      }

      @Override
      public boolean setSelection(int start, int end) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        ImeContext ctx = buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
        if (ctx.text.isEmpty()) return true;
        int textLen = ctx.text.length();
        int sOff = Math.max(0, Math.min(start, textLen));
        int eOff = Math.max(0, Math.min(end, textLen));
        int cursorOff = lineCharToOffsetInContext(ctx, editor.cursor.cursorLine, editor.cursor.cursorChar);
        if (sOff == eOff && sOff == cursorOff && !editor.selection.hasSelection) return true;
        if (sOff == 0 && eOff == 0 && cursorOff > 0 && !editor.selection.hasSelection) return true;
        CursorTarget s = offsetToLineCharInContext(ctx, sOff);
        CursorTarget e = offsetToLineCharInContext(ctx, eOff);
        editor.selection.setSelectionInternal(s.line, s.ch, e.line, e.ch);
        editor.cursor.cursorLine = e.line;
        editor.cursor.cursorChar = e.ch;
        editor.caret.resetBlink();
        editor.invalidate();
        editor.autoCompletion.updateSuggestion();
        return true;
      }

      @Override
      public boolean setComposingRegion(int start, int end) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (start > end) {
          int t = start;
          start = end;
          end = t;
        }
        ImeContext ctx = buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
        if (ctx.text.isEmpty()) return true;
        int textLen = ctx.text.length();
        int sOff = Math.max(0, Math.min(start, textLen));
        int eOff = Math.max(0, Math.min(end, textLen));
        CursorTarget s = offsetToLineCharInContext(ctx, sOff);
        CursorTarget e = offsetToLineCharInContext(ctx, eOff);
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

      @Override
      public boolean finishComposingText() {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (lastComposingTextForCharAnim != null && !lastComposingTextForCharAnim.isEmpty()) {
          markImeCommit(lastComposingTextForCharAnim);
        }
        commitComposing(true);
        return true;
      }

      @Override
      public boolean commitCompletion(CompletionInfo text) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (text == null || text.getText() == null) return true;
        if (!hasComposing && !editor.selection.hasSelection && replaceWordAtCursorWith(text.getText())) {
          markImeCommit(text.getText());
          return true;
        }
        return commitText(text.getText(), 1);
      }

      @Override
      public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
        if (!hasComposing
            && !editor.selection.hasSelection
            && replaceWordAtCursorWith(correctionInfo.getNewText())) {
          markImeCommit(correctionInfo.getNewText());
          return true;
        }
        return commitText(correctionInfo.getNewText(), 1);
      }

      @Override
      public boolean commitText(CharSequence text, int newCursorPosition) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        if (text == null) return super.commitText(text, newCursorPosition);

        String str = text.toString();
        if ("\n".equals(str)) {
          editor.insertNewlineAtCursor();
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
              String line = editor.getLineTextForRender(editor.cursor.cursorLine);
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
          boolean recentIme =
              suppressNextCommitText
                  || (lastImeCommitText != null && (now - lastImeCommitUptime) < 700);
          if (recentIme) {
            int anchorLen = Math.max(str.length(), (lastImeCommitText == null) ? 0 : lastImeCommitText.length());
            int beforeLen = Math.max(32, Math.min(256, anchorLen + 4));
            String before = getImeTextBeforeCursor(beforeLen);

            if (before != null && !before.isEmpty()) {
              if (!str.isEmpty() && before.endsWith(str)) {
                suppressNextCommitText = false;
                return true;
              }
              if (lastImeCommitText != null
                  && !lastImeCommitText.isEmpty()
                  && before.endsWith(lastImeCommitText)) {
                if (str.equals(lastImeCommitText)) {
                  suppressNextCommitText = false;
                  return true;
                }
                if (str.startsWith(lastImeCommitText)) {
                  String suffix = str.substring(lastImeCommitText.length());
                  suppressNextCommitText = false;
                  if (!suffix.isEmpty()) {
                    String trimmed = suffix.trim();
                    if (trimmed.equals(lastImeCommitText)) {
                      return true;
                    }
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

      @Override
      public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;
        if (text == null) return true;

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

      @Override
      public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (editor.isDisabled || editor.isReadOnly) return true;
        if (editor.zoom.isZoomGestureActive()) return true;

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
    };
  }

  /**
   * Update IME selection
   */
  public void updateImeSelection() {
    if (editor.isDisabled || editor.isReadOnly) return;
    if (!editor.isFocused()) return;
    InputMethodManager imm =
        (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm == null || !imm.isActive(editor)) return;

    ImeContext ctx = buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);

    int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
    int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
    if (editor.selection.hasSelection) {
      sLine = editor.selection.selStartLine;
      sChar = editor.selection.selStartChar;
      eLine = editor.selection.selEndLine;
      eChar = editor.selection.selEndChar;
      if (comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar;
        sLine = eLine;
        sChar = eChar;
        eLine = tL;
        eChar = tC;
      }
    }

    int selStart = lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = lineCharToOffsetInContext(ctx, eLine, eChar);
    int compStart = -1;
    int compEnd = -1;
    if (hasComposing) {
      compStart = lineCharToOffsetInContext(ctx, composingLine, composingOffset);
      compEnd = lineCharToOffsetInContext(ctx, composingLine, composingOffset + composingLength);
    }
    imm.updateSelection(editor, selStart, selEnd, compStart, compEnd);
    if (imeExtractedTextMonitor) {
      ExtractedText et = buildExtractedTextFromContext(ctx);
      imm.updateExtractedText(editor, imeExtractedTextToken, et);
    }
  }

  /**
   * Commit composing text
   */
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

  /**
   * Replace composing text with new text
   */
  public void replaceComposingWith(CharSequence textSeq) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    editor.editOperators.editVersion.incrementAndGet();

    editor.fileIO.ensureLineInWindow(composingLine, true);
    if (editor.fileIO.isWindowLoading
        && (composingLine < editor.textRender.windowStartLine
            || composingLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
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
          String removed = null;
          int at = start;
          if (oldComposing.startsWith(newComposing)) {
            removed = oldComposing.substring(newComposing.length());
            at = start + newComposing.length();
          } else if (oldComposing.endsWith(newComposing)) {
            removed = oldComposing.substring(0, oldComposing.length() - newComposing.length());
            at = start;
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
      editor.invalidateHighlightCacheForLine(composingLine);
      editor.lineNumber.invalidateLineNumberCache();
      composingLength = textSeq.length();
      editor.cursor.cursorLine = composingLine;
      editor.cursor.cursorChar = composingOffset + composingLength;
      editor.computeWidthForLine(composingLine, newLine);
      editor.recalculateMaxLineWidth();
      editor.invalidateLineGlobal(composingLine);
      editor.keepCursorVisibleHorizontally();
      editor.invalidate();
    }
    editor.autoCompletion.updateSuggestion();
  }

  /**
   * Delete composing text
   */
  public void deleteComposing() {
    if (!hasComposing) return;
    replaceComposingWith("");
    hasComposing = false;
    composingLength = 0;
    composingStartActive = false;
    lastComposingTextForCharAnim = null;
  }

  /**
   * Update composing pending operation
   */
  public void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    if (!hasComposing) return;
    if (text == null) text = "";
    if (text.length() > EditOperators.UNDO_TEXT_LIMIT) return;

    int startLine = composingStartActive ? composingStartLine : composingLine;
    int startChar = composingStartActive ? composingStartChar : composingOffset;

    if (composingPendingOp == null) {
      if (text.isEmpty()) return;
      EditOperators.EditOp op = new EditOperators.EditOp();
      op.startLine = startLine;
      op.startChar = startChar;
      op.endLine = startLine;
      op.endChar = startChar;
      op.removedText = "";
      op.insertedText = text;
      EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = editor.cursor.cursorLine;
      op.cursorCharAfter = editor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      editor.editOperators.lineCountDelta += editor.editOperators.countNewlines(text);
      composingPendingOp = op;
      editor.editOperators.undoStack.addLast(op);
      while (editor.editOperators.undoStack.size() > EditOperators.UNDO_STACK_LIMIT) {
        editor.editOperators.undoStack.removeFirst();
      }
      editor.editOperators.redoStack.clear();
      editor.editOperators.pendingEdits.addLast(op);
      editor.editOperators.pendingRedo.clear();
      editor.editOperators.lastEditTimestamp = op.timestamp;
      Log.d(
          "SodiumEditorCompose",
          "start composing op s=" + startLine + ":" + startChar + " textLen=" + text.length());
      return;
    }

    String prev = composingPendingOp.insertedText == null ? "" : composingPendingOp.insertedText;
    int prevNewlines = editor.editOperators.countNewlines(prev);
    int newNewlines = editor.editOperators.countNewlines(text);
    editor.editOperators.lineCountDelta += (newNewlines - prevNewlines);

    composingPendingOp.insertedText = text;
    EditOperators.CursorTarget insertedEnd = editor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
    composingPendingOp.insertedEndLine = insertedEnd.line;
    composingPendingOp.insertedEndChar = insertedEnd.ch;
    composingPendingOp.cursorLineAfter = editor.cursor.cursorLine;
    composingPendingOp.cursorCharAfter = editor.cursor.cursorChar;
    composingPendingOp.timestamp = System.currentTimeMillis();
    editor.editOperators.lastEditTimestamp = composingPendingOp.timestamp;

    Log.d("SodiumEditorCompose", "update composing op textLen=" + text.length());

    if (text.isEmpty()) {
      // Remove it from pending/history because composing ended with empty.
      editor.editOperators.pendingEdits.remove(composingPendingOp);
      editor.editOperators.undoStack.remove(composingPendingOp);
      composingPendingOp = null;
      Log.d("SodiumEditorCompose", "remove composing op (empty)");
    }
  }

  /**
   * Build IME context
   */
  public ImeContext buildImeContext(int beforeChars, int afterChars) {
    int before = Math.max(0, beforeChars);
    int after = Math.max(0, afterChars);
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -before, raf);
      CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, after, raf);
      String text = buildRangeTextForIme(start, end, raf);
      return new ImeContext(start.line, start.ch, text);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * Build extracted text from context
   */
  public ExtractedText buildExtractedTextFromContext(ImeContext ctx) {
    ExtractedText et = new ExtractedText();
    et.text = ctx.text;
    et.startOffset = 0;
    et.partialStartOffset = -1;
    et.partialEndOffset = -1;

    int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
    int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
    if (editor.selection.hasSelection) {
      sLine = editor.selection.selStartLine;
      sChar = editor.selection.selStartChar;
      eLine = editor.selection.selEndLine;
      eChar = editor.selection.selEndChar;
      if (comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar;
        sLine = eLine;
        sChar = eChar;
        eLine = tL;
        eChar = tC;
      }
    }
    int selStart = lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = lineCharToOffsetInContext(ctx, eLine, eChar);
    et.selectionStart = selStart;
    et.selectionEnd = selEnd;
    return et;
  }

  /**
   * Get IME text before cursor
   */
  public String getImeTextBeforeCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      CursorTarget start = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, -length, raf);
      return buildRangeTextForIme(start, new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * Get IME text after cursor
   */
  public String getImeTextAfterCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      CursorTarget end = moveCursorByCharsForIme(editor.cursor.cursorLine, editor.cursor.cursorChar, length, raf);
      return buildRangeTextForIme(new CursorTarget(editor.cursor.cursorLine, editor.cursor.cursorChar), end, raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * Mark IME commit
   */
  public void markImeCommit(CharSequence textSeq) {
    if (textSeq == null) return;
    lastImeCommitText = textSeq.toString();
    lastImeCommitUptime = android.os.SystemClock.uptimeMillis();
    suppressNextCommitText = true;
  }

  /**
   * Replace word at cursor with text
   */
  public boolean replaceWordAtCursorWith(CharSequence textSeq) {
    if (textSeq == null) return false;
    String insert = textSeq.toString();
    if (insert.isEmpty()) return false;

    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(insert);
      return true;
    }
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = editor.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    editor.selection.setSelectionInternal(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
    editor.selection.replaceSelectionWithText(insert);
    return true;
  }

  /**
   * Try to replace word from IME commit
   */
  public boolean tryReplaceWordFromImeCommit(String insert) {
    if (editor.selection.hasSelection || hasComposing) return false;
    if (insert == null || insert.isEmpty()) return false;
    if (insert.length() <= 1) return false;
    int end = insert.length();
    while (end > 0 && Character.isWhitespace(insert.charAt(end - 1))) end--;
    String trailing = (end < insert.length()) ? insert.substring(end) : "";
    String core = insert.substring(0, end);
    if (core.length() <= 1) return false;
    for (int i = 0; i < core.length(); i++) {
      if (Character.isWhitespace(core.charAt(i))) return false;
    }
    int[] bounds = getWordBoundsAtCursor();
    if (bounds == null) return false;
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
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

  /**
   * Get word bounds at cursor
   */
  @Nullable
  public int[] getWordBoundsAtCursor() {
    String line = editor.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return null;
    int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return null;
    if (Character.isWhitespace(line.charAt(pos))) return null;
    return editor.computeWordBounds(line, pos);
  }

  /**
   * Open IME random access file
   */
  @Nullable
  private RandomAccessFile openImeRandomAccessFile() {
    if (!editor.fileIO.isIndexReady || editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) return null;
    try {
      return new RandomAccessFile(editor.fileIO.sourceFile, "r");
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Get line text for IME scan
   */
  private String getLineTextForImeScan(int line, @Nullable RandomAccessFile raf) {
    if (line < 0) return "";
    String mod = editor.textRender.modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= editor.textRender.windowStartLine && line < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      String text = editor.getLineFromWindowLocal(line - editor.textRender.windowStartLine);
      return (text != null) ? text : "";
    }
    if (raf != null && editor.fileIO.isIndexReady) {
      long offset;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (line < 0 || line >= editor.fileIO.lineOffsets.length) return "";
        offset = editor.fileIO.lineOffsets[line];
      }
      try {
        return editor.fileIO.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return "";
      }
    }
    return "";
  }

  /**
   * Clamp line/char to document bounds
   */
  private CursorTarget clampLineCharToDocument(int line, int ch, @Nullable RandomAccessFile raf) {
    int total = editor.getLinesCount();
    if (total <= 0) return new CursorTarget(0, 0);
    int clampedLine = Math.max(0, Math.min(line, total - 1));
    String ln = getLineTextForImeScan(clampedLine, raf);
    int len = (ln == null) ? 0 : ln.length();
    int clampedChar = Math.max(0, Math.min(ch, len));
    return new CursorTarget(clampedLine, clampedChar);
  }

  /**
   * Move cursor by characters for IME
   */
  private CursorTarget moveCursorByCharsForIme(int line, int ch, int delta, @Nullable RandomAccessFile raf) {
    CursorTarget base = clampLineCharToDocument(line, ch, raf);
    int curLine = base.line;
    int curChar = base.ch;
    int totalLines = editor.getLinesCount();
    if (totalLines <= 0) totalLines = 1;
    if (delta == 0) return base;

    if (delta < 0) {
      int remaining = -delta;
      while (remaining > 0) {
        String ln = getLineTextForImeScan(curLine, raf);
        int len = (ln == null) ? 0 : ln.length();
        curChar = Math.min(curChar, len);
        if (curChar >= remaining) {
          curChar -= remaining;
          remaining = 0;
          break;
        }
        remaining -= curChar;
        if (curLine <= 0) {
          curChar = 0;
          remaining = 0;
          break;
        }
        // Move across the newline to the previous line.
        remaining -= 1;
        curLine--;
        ln = getLineTextForImeScan(curLine, raf);
        curChar = (ln == null) ? 0 : ln.length();
        if (remaining < 0) remaining = 0;
      }
      return new CursorTarget(curLine, curChar);
    }

    int remaining = delta;
    while (remaining > 0) {
      String ln = getLineTextForImeScan(curLine, raf);
      int len = (ln == null) ? 0 : ln.length();
      curChar = Math.min(curChar, len);
      int available = len - curChar;
      if (available >= remaining) {
        curChar += remaining;
        remaining = 0;
        break;
      }
      remaining -= available;
      if (curLine >= totalLines - 1) {
        curChar = len;
        remaining = 0;
        break;
      }
      // Move across the newline to the next line.
      remaining -= 1;
      curLine++;
      curChar = 0;
      if (remaining < 0) remaining = 0;
    }
    return new CursorTarget(curLine, curChar);
  }

  /**
   * Build range text for IME
   */
  private String buildRangeTextForIme(CursorTarget start, CursorTarget end, @Nullable RandomAccessFile raf) {
    int sL = start.line, sC = start.ch, eL = end.line, eC = end.ch;
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    StringBuilder sb = new StringBuilder();
    for (int line = sL; line <= eL; line++) {
      String ln = getLineTextForImeScan(line, raf);
      if (ln == null) ln = "";
      int from = (line == sL) ? Math.min(sC, ln.length()) : 0;
      int to = (line == eL) ? Math.min(eC, ln.length()) : ln.length();
      if (from < to) sb.append(ln, from, to);
      if (line < eL) sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * Convert offset to line/char in context
   */
  private CursorTarget offsetToLineCharInContext(ImeContext ctx, int offset) {
    int safeOffset = Math.max(0, Math.min(offset, ctx.text.length()));
    int line = ctx.startLine;
    int ch = ctx.startChar;
    for (int i = 0; i < safeOffset; i++) {
      char c = ctx.text.charAt(i);
      if (c == '\n') {
        line++;
        ch = 0;
      } else {
        ch++;
      }
    }
    return new CursorTarget(line, ch);
  }

  /**
   * Convert line/char to offset in context
   */
  private int lineCharToOffsetInContext(ImeContext ctx, int line, int ch) {
    int offset = 0;
    int curLine = ctx.startLine;
    int curChar = ctx.startChar;
    int len = ctx.text.length();
    for (int i = 0; i < len; i++) {
      if (curLine == line && curChar == ch) return offset;
      char c = ctx.text.charAt(i);
      if (c == '\n') {
        curLine++;
        curChar = 0;
      } else {
        curChar++;
      }
      offset++;
    }
    return Math.max(0, Math.min(offset, len));
  }

  /**
   * Compare two line/char positions
   */
  private int comparePos(int lineA, int charA, int lineB, int charB) {
    if (lineA != lineB) return Integer.compare(lineA, lineB);
    return Integer.compare(charA, charB);
  }

  /**
   * IME context class
   */
  private static class ImeContext {
    final int startLine;
    final int startChar;
    final String text;

    ImeContext(int startLine, int startChar, String text) {
      this.startLine = startLine;
      this.startChar = startChar;
      this.text = (text == null) ? "" : text;
    }
  }

  /**
   * Cursor target class
   */
  private static class CursorTarget {
    final int line;
    final int ch;

    CursorTarget(int line, int ch) {
      this.line = line;
      this.ch = ch;
    }
  }

  // Getters and Setters

  public void setImeExtractedTextMonitor(boolean enabled) {
    this.imeExtractedTextMonitor = enabled;
  }

  public void setImeExtractedTextToken(int token) {
    this.imeExtractedTextToken = token;
  }

  public void setImeContextSize(int beforeChars, int afterChars) {
    this.imeExtractedBeforeChars = Math.max(0, beforeChars);
    this.imeExtractedAfterChars = Math.max(0, afterChars);
  }

  public boolean hasComposing() {
    return hasComposing;
  }

  public void clearComposing() {
    deleteComposing();
  }

  public void restartInput() {
    InputMethodManager imm =
        (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(editor);
    }
  }

  public void hideKeyboard() {
    InputMethodManager imm =
        (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }
  }

  public void showKeyboard() {
    InputMethodManager imm =
        (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(editor, 0);
    }
  }
}
