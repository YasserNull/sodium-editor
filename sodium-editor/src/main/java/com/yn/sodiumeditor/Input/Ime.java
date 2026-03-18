package com.yn.sodiumeditor.Input;

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
import com.yn.sodiumeditor.EditOperators;
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

  private final SodiumEditor sodiumeditor;

  public Ime(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Create InputConnection for IME
   */
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return null;
    
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

    return new BaseInputConnection(sodiumeditor, true) {
      @Override
      public Editable getEditable() {
        return imeEditable;
      }

      @Override
      public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return null;
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
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return "";
        return getImeTextBeforeCursor(length);
      }

      @Override
      public CharSequence getTextAfterCursor(int length, int flags) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return "";
        return getImeTextAfterCursor(length);
      }

      @Override
      public CharSequence getSelectedText(int flags) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return "";
        return sodiumeditor.selection.getSelectedText();
      }

      @Override
      public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return null;
        int before = Math.max(0, beforeLength);
        int after = Math.max(0, afterLength);
        ImeContext ctx = buildImeContext(before, after);
        int sLine = sodiumeditor.cursor.cursorLine, sChar = sodiumeditor.cursor.cursorChar;
        int eLine = sodiumeditor.cursor.cursorLine, eChar = sodiumeditor.cursor.cursorChar;
        if (sodiumeditor.selection.hasSelection) {
          sLine = sodiumeditor.selection.selStartLine;
          sChar = sodiumeditor.selection.selStartChar;
          eLine = sodiumeditor.selection.selEndLine;
          eChar = sodiumeditor.selection.selEndChar;
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
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        ImeContext ctx = buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
        if (ctx.text.isEmpty()) return true;
        int textLen = ctx.text.length();
        int sOff = Math.max(0, Math.min(start, textLen));
        int eOff = Math.max(0, Math.min(end, textLen));
        int cursorOff = lineCharToOffsetInContext(ctx, sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar);
        if (sOff == eOff && sOff == cursorOff && !sodiumeditor.selection.hasSelection) return true;
        if (sOff == 0 && eOff == 0 && cursorOff > 0 && !sodiumeditor.selection.hasSelection) return true;
        CursorTarget s = offsetToLineCharInContext(ctx, sOff);
        CursorTarget e = offsetToLineCharInContext(ctx, eOff);
        sodiumeditor.selection.setSelectionInternal(s.line, s.ch, e.line, e.ch);
        sodiumeditor.cursor.cursorLine = e.line;
        sodiumeditor.cursor.cursorChar = e.ch;
        sodiumeditor.caret.resetBlink();
        sodiumeditor.invalidate();
        sodiumeditor.updateSuggestion();
        return true;
      }

      @Override
      public boolean setComposingRegion(int start, int end) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
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
          sodiumeditor.selection.setSelectionInternal(s.line, s.ch, e.line, e.ch);
          sodiumeditor.cursor.cursorLine = e.line;
          sodiumeditor.cursor.cursorChar = e.ch;
          sodiumeditor.caret.resetBlink();
          sodiumeditor.invalidate();
          sodiumeditor.updateSuggestion();
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
        sodiumeditor.invalidate();
        sodiumeditor.updateSuggestion();
        return true;
      }

      @Override
      public boolean finishComposingText() {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (lastComposingTextForCharAnim != null && !lastComposingTextForCharAnim.isEmpty()) {
          markImeCommit(lastComposingTextForCharAnim);
        }
        commitComposing(true);
        return true;
      }

      @Override
      public boolean commitCompletion(CompletionInfo text) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (text == null || text.getText() == null) return true;
        if (!hasComposing && !sodiumeditor.selection.hasSelection && replaceWordAtCursorWith(text.getText())) {
          markImeCommit(text.getText());
          return true;
        }
        return commitText(text.getText(), 1);
      }

      @Override
      public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
        if (!hasComposing
            && !sodiumeditor.selection.hasSelection
            && replaceWordAtCursorWith(correctionInfo.getNewText())) {
          markImeCommit(correctionInfo.getNewText());
          return true;
        }
        return commitText(correctionInfo.getNewText(), 1);
      }

      @Override
      public boolean commitText(CharSequence text, int newCursorPosition) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (sodiumeditor.zoom.isZoomGestureActive()) return true;
        if (text == null) return super.commitText(text, newCursorPosition);

        String str = text.toString();
        if ("\n".equals(str)) {
          sodiumeditor.insertNewlineAtCursor();
          commitComposing(true);
          sodiumeditor.charAnimation.startCharAnimationFromText(text);
          sodiumeditor.updateSuggestion();
          return true;
        }

        if (tryReplaceWordFromImeCommit(str)) {
          sodiumeditor.updateSuggestion();
          return true;
        }

        if (!hasComposing && !sodiumeditor.selection.hasSelection && lastImeCommitText != null) {
          long now = android.os.SystemClock.uptimeMillis();
          if (now - lastImeCommitUptime < 700 && str.trim().isEmpty()) {
            int[] bounds = getWordBoundsAtCursor();
            if (bounds != null) {
              String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
              if (line != null && bounds[0] < bounds[1] && bounds[1] <= line.length()) {
                String word = line.substring(bounds[0], bounds[1]);
                if (!word.isEmpty() && lastImeCommitText.startsWith(word)) {
                  if (!word.equals(lastImeCommitText)) {
                    sodiumeditor.selection.setSelectionInternal(sodiumeditor.cursor.cursorLine, bounds[0], sodiumeditor.cursor.cursorLine, bounds[1]);
                    sodiumeditor.selection.replaceSelectionWithText(lastImeCommitText);
                  }
                  sodiumeditor.editOperators.insertTextAtCursor(str);
                  suppressNextCommitText = false;
                  return true;
                }
              }
            }
          }
        }

        if (!hasComposing && !sodiumeditor.selection.hasSelection) {
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
                    sodiumeditor.editOperators.insertTextAtCursor(suffix);
                    commitComposing(true);
                    sodiumeditor.charAnimation.startCharAnimationFromText(suffix);
                    sodiumeditor.handleAutoPairing(suffix);
                    sodiumeditor.updateSuggestion();
                  }
                  return true;
                }
              }
            }
            suppressNextCommitText = false;
          }
        }

        if (sodiumeditor.selection.hasSelection) {
          sodiumeditor.selection.replaceSelectionWithText(str);
          commitComposing(true);
          sodiumeditor.charAnimation.startCharAnimationFromText(text);
          sodiumeditor.handleAutoPairing(str);
          sodiumeditor.updateSuggestion();
          return true;
        }

        if (hasComposing) {
          int startLine = composingStartActive ? composingStartLine : composingLine;
          int startChar = composingStartActive ? composingStartChar : composingOffset;
          replaceComposingWith(text);
          updateComposingPendingOp(str, startLine, startChar);
          commitComposing(true);
          markImeCommit(str);
          sodiumeditor.charAnimation.startCharAnimationFromText(text);
          sodiumeditor.handleAutoPairing(str);
          sodiumeditor.updateSuggestion();
          return true;
        }

        sodiumeditor.editOperators.insertTextAtCursor(str);
        commitComposing(true);
        sodiumeditor.charAnimation.startCharAnimationFromText(text);
        sodiumeditor.handleAutoPairing(str);

        sodiumeditor.updateSuggestion();
        return true;
      }

      @Override
      public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (sodiumeditor.zoom.isZoomGestureActive()) return true;
        if (text == null) return true;

        if (sodiumeditor.selection.hasSelection) {
          sodiumeditor.selection.replaceSelectionWithText(text.toString());
          sodiumeditor.charAnimation.startCharAnimationFromText(text);
          sodiumeditor.updateSuggestion();
          return true;
        }

        sodiumeditor.ensureLineInWindow(sodiumeditor.cursor.cursorLine, true);
        if (!hasComposing) {
          composingLine = sodiumeditor.cursor.cursorLine;
          composingOffset = sodiumeditor.cursor.cursorChar;
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
        if (shouldAnim) sodiumeditor.charAnimation.startCharAnimationFromText(newText);
        sodiumeditor.updateSuggestion();
        return true;
      }

      @Override
      public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return true;
        if (sodiumeditor.zoom.isZoomGestureActive()) return true;

        if (sodiumeditor.selection.hasSelection) {
          sodiumeditor.selection.replaceSelectionWithText("");
          sodiumeditor.updateSuggestion();
          return true;
        }
        for (int i = 0; i < beforeLength; i++) sodiumeditor.editOperators.deleteCharAtCursor();
        for (int i = 0; i < afterLength; i++) sodiumeditor.editOperators.deleteForwardAtCursor();
        sodiumeditor.updateSuggestion();
        return true;
      }
    };
  }

  /**
   * Update IME selection
   */
  public void updateImeSelection() {
    if (sodiumeditor.isDisabled || sodiumeditor.isReadOnly) return;
    if (!sodiumeditor.isFocused()) return;
    InputMethodManager imm =
        (InputMethodManager) sodiumeditor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm == null || !imm.isActive(sodiumeditor)) return;

    ImeContext ctx = buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);

    int sLine = sodiumeditor.cursor.cursorLine, sChar = sodiumeditor.cursor.cursorChar;
    int eLine = sodiumeditor.cursor.cursorLine, eChar = sodiumeditor.cursor.cursorChar;
    if (sodiumeditor.selection.hasSelection) {
      sLine = sodiumeditor.selection.selStartLine;
      sChar = sodiumeditor.selection.selStartChar;
      eLine = sodiumeditor.selection.selEndLine;
      eChar = sodiumeditor.selection.selEndChar;
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
    imm.updateSelection(sodiumeditor, selStart, selEnd, compStart, compEnd);
    if (imeExtractedTextMonitor) {
      ExtractedText et = buildExtractedTextFromContext(ctx);
      imm.updateExtractedText(sodiumeditor, imeExtractedTextToken, et);
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
    sodiumeditor.invalidate();
    sodiumeditor.updateSuggestion();
  }

  /**
   * Replace composing text with new text
   */
  public void replaceComposingWith(CharSequence textSeq) {
    if (sodiumeditor.isReadOnly) return;
    sodiumeditor.invalidatePendingIOForEdit();
    sodiumeditor.editOperators.editVersion.incrementAndGet();

    sodiumeditor.ensureLineInWindow(composingLine, true);
    if (sodiumeditor.isWindowLoading
        && (composingLine < sodiumeditor.windowStartLine
            || composingLine >= sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size())) {
      sodiumeditor.post(() -> replaceComposingWith(textSeq));
      return;
    }
    int local = composingLine - sodiumeditor.windowStartLine;
    synchronized (sodiumeditor.linesWindow) {
      String base = sodiumeditor.getLineFromWindowLocal(local);
      if (base == null) base = "";
      int start = Math.max(0, Math.min(composingOffset, base.length()));
      int end = Math.max(0, Math.min(composingOffset + composingLength, base.length()));
      if (sodiumeditor.charAnimation.isCharAnimationEnabled) {
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
            android.graphics.Paint p = sodiumeditor.textRender.getPaintForChar(composingLine, at, base);
            sodiumeditor.charAnimation.startDeleteAnimation(composingLine, at, removed, p);
          }
        }
      }
      String newLine = base.substring(0, start) + textSeq + base.substring(end);
      sodiumeditor.updateLocalLine(local, newLine);
      sodiumeditor.modifiedLines.put(composingLine, newLine);
      composingLength = textSeq.length();
      sodiumeditor.cursor.cursorLine = composingLine;
      sodiumeditor.cursor.cursorChar = composingOffset + composingLength;
      sodiumeditor.computeWidthForLine(composingLine, newLine);
      sodiumeditor.recalculateMaxLineWidth();
      sodiumeditor.invalidate();
    }
    sodiumeditor.updateSuggestion();
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
      EditOperators.CursorTarget insertedEnd = sodiumeditor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = sodiumeditor.cursor.cursorLine;
      op.cursorCharAfter = sodiumeditor.cursor.cursorChar;
      op.timestamp = System.currentTimeMillis();
      sodiumeditor.editOperators.lineCountDelta += sodiumeditor.editOperators.countNewlines(text);
      composingPendingOp = op;
      sodiumeditor.editOperators.undoStack.addLast(op);
      while (sodiumeditor.editOperators.undoStack.size() > EditOperators.UNDO_STACK_LIMIT) {
        sodiumeditor.editOperators.undoStack.removeFirst();
      }
      sodiumeditor.editOperators.redoStack.clear();
      sodiumeditor.editOperators.pendingEdits.addLast(op);
      sodiumeditor.editOperators.pendingRedo.clear();
      sodiumeditor.editOperators.lastEditTimestamp = op.timestamp;
      Log.d(
          "SodiumEditorCompose",
          "start composing op s=" + startLine + ":" + startChar + " textLen=" + text.length());
      return;
    }

    String prev = composingPendingOp.insertedText == null ? "" : composingPendingOp.insertedText;
    int prevNewlines = sodiumeditor.editOperators.countNewlines(prev);
    int newNewlines = sodiumeditor.editOperators.countNewlines(text);
    sodiumeditor.editOperators.lineCountDelta += (newNewlines - prevNewlines);

    composingPendingOp.insertedText = text;
    EditOperators.CursorTarget insertedEnd = sodiumeditor.editOperators.computeCursorAfterInsert(startLine, startChar, text);
    composingPendingOp.insertedEndLine = insertedEnd.line;
    composingPendingOp.insertedEndChar = insertedEnd.ch;
    composingPendingOp.cursorLineAfter = sodiumeditor.cursor.cursorLine;
    composingPendingOp.cursorCharAfter = sodiumeditor.cursor.cursorChar;
    composingPendingOp.timestamp = System.currentTimeMillis();
    sodiumeditor.editOperators.lastEditTimestamp = composingPendingOp.timestamp;

    Log.d("SodiumEditorCompose", "update composing op textLen=" + text.length());

    if (text.isEmpty()) {
      // Remove it from pending/history because composing ended with empty.
      sodiumeditor.editOperators.pendingEdits.remove(composingPendingOp);
      sodiumeditor.editOperators.undoStack.remove(composingPendingOp);
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
      CursorTarget start = moveCursorByCharsForIme(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar, -before, raf);
      CursorTarget end = moveCursorByCharsForIme(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar, after, raf);
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

    int sLine = sodiumeditor.cursor.cursorLine, sChar = sodiumeditor.cursor.cursorChar;
    int eLine = sodiumeditor.cursor.cursorLine, eChar = sodiumeditor.cursor.cursorChar;
    if (sodiumeditor.selection.hasSelection) {
      sLine = sodiumeditor.selection.selStartLine;
      sChar = sodiumeditor.selection.selStartChar;
      eLine = sodiumeditor.selection.selEndLine;
      eChar = sodiumeditor.selection.selEndChar;
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
      CursorTarget start = moveCursorByCharsForIme(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar, -length, raf);
      return buildRangeTextForIme(start, new CursorTarget(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar), raf);
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
      CursorTarget end = moveCursorByCharsForIme(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar, length, raf);
      return buildRangeTextForIme(new CursorTarget(sodiumeditor.cursor.cursorLine, sodiumeditor.cursor.cursorChar), end, raf);
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

    if (sodiumeditor.selection.hasSelection) {
      sodiumeditor.selection.replaceSelectionWithText(insert);
      return true;
    }
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(sodiumeditor.cursor.cursorChar, line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = sodiumeditor.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    sodiumeditor.selection.setSelectionInternal(sodiumeditor.cursor.cursorLine, bounds[0], sodiumeditor.cursor.cursorLine, bounds[1]);
    sodiumeditor.selection.replaceSelectionWithText(insert);
    return true;
  }

  /**
   * Try to replace word from IME commit
   */
  public boolean tryReplaceWordFromImeCommit(String insert) {
    if (sodiumeditor.selection.hasSelection || hasComposing) return false;
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
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null || bounds[0] >= bounds[1] || bounds[1] > line.length()) return false;
    String word = line.substring(bounds[0], bounds[1]);
    if (word.isEmpty() || word.equals(core)) return false;
    sodiumeditor.selection.setSelectionInternal(sodiumeditor.cursor.cursorLine, bounds[0], sodiumeditor.cursor.cursorLine, bounds[1]);
    sodiumeditor.selection.replaceSelectionWithText(core);
    if (!trailing.isEmpty()) sodiumeditor.editOperators.insertTextAtCursor(trailing);
    markImeCommit(insert);
    sodiumeditor.charAnimation.startCharAnimationFromText(insert);
    return true;
  }

  /**
   * Get word bounds at cursor
   */
  @Nullable
  public int[] getWordBoundsAtCursor() {
    String line = sodiumeditor.getLineTextForRender(sodiumeditor.cursor.cursorLine);
    if (line == null || line.isEmpty()) return null;
    int pos = Math.max(0, Math.min(sodiumeditor.cursor.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return null;
    if (Character.isWhitespace(line.charAt(pos))) return null;
    return sodiumeditor.computeWordBounds(line, pos);
  }

  /**
   * Open IME random access file
   */
  @Nullable
  private RandomAccessFile openImeRandomAccessFile() {
    if (!sodiumeditor.isIndexReady || sodiumeditor.sourceFile == null || !sodiumeditor.sourceFile.exists()) return null;
    try {
      return new RandomAccessFile(sodiumeditor.sourceFile, "r");
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Get line text for IME scan
   */
  private String getLineTextForImeScan(int line, @Nullable RandomAccessFile raf) {
    if (line < 0) return "";
    String mod = sodiumeditor.modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= sodiumeditor.windowStartLine && line < sodiumeditor.windowStartLine + sodiumeditor.linesWindow.size()) {
      String text = sodiumeditor.getLineFromWindowLocal(line - sodiumeditor.windowStartLine);
      return (text != null) ? text : "";
    }
    if (raf != null && sodiumeditor.isIndexReady) {
      long offset;
      synchronized (sodiumeditor.lineOffsetsLock) {
        if (line < 0 || line >= sodiumeditor.lineOffsets.length) return "";
        offset = sodiumeditor.lineOffsets[line];
      }
      try {
        return sodiumeditor.readLineUtf8AtByte(raf, offset);
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
    int total = sodiumeditor.getLinesCount();
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
    int totalLines = sodiumeditor.getLinesCount();
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
        (InputMethodManager) sodiumeditor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(sodiumeditor);
    }
  }

  public void hideKeyboard() {
    InputMethodManager imm =
        (InputMethodManager) sodiumeditor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(sodiumeditor.getWindowToken(), 0);
    }
  }

  public void showKeyboard() {
    InputMethodManager imm =
        (InputMethodManager) sodiumeditor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(sodiumeditor, 0);
    }
  }
}
