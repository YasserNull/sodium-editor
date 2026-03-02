package com.yn.sodiumeditor.input;

import android.content.Context;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

public final class InputMethodHandler {
  private final SodiumEditor view;
  private final ImeTextHelper textHelper;
  private boolean imeExtractedTextMonitor = false;
  private int imeExtractedTextToken = 0;
  private int imeExtractedBeforeChars = ImeTextHelper.IME_CONTEXT_BEFORE_CHARS;
  private int imeExtractedAfterChars = ImeTextHelper.IME_CONTEXT_AFTER_CHARS;

  public InputMethodHandler(SodiumEditor view) {
    this.view = view;
    this.textHelper = new ImeTextHelper(view);
  }

  public boolean onCheckIsTextEditor() {
    return !view.editorConfig.behaviorConfig.isDisabled && !view.editorConfig.behaviorConfig.isReadOnly;
  }

  public void restartInput() {
    android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(view);
    }
  }

  public void showKeyboard() {
    if (view.editorConfig.behaviorConfig.isReadOnly) return;
    view.requestFocus();
    android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(view, 0);
  }

  public void updateImeSelection() {
    if (view.editorConfig.behaviorConfig.isDisabled || view.editorConfig.behaviorConfig.isReadOnly) return;
    if (!view.isFocused()) return;
    android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm == null || !imm.isActive(view)) return;

    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);

    int sLine = view.cursorState.getCursorLine(), sChar = view.cursorState.getCursorChar(), eLine = view.cursorState.getCursorLine(), eChar = view.cursorState.getCursorChar();
    if (view.selectionState.hasSelection()) {
      sLine = view.selectionState.selStartLine;
      sChar = view.selectionState.selStartChar;
      eLine = view.selectionState.selEndLine;
      eChar = view.selectionState.selEndChar;
      if (view.comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar;
        sLine = eLine;
        sChar = eChar;
        eLine = tL;
        eChar = tC;
      }
    }

    int selStart = textHelper.lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = textHelper.lineCharToOffsetInContext(ctx, eLine, eChar);
    int compStart = -1;
    int compEnd = -1;
    if (view.cursorState.hasComposing()) {
      compStart = textHelper.lineCharToOffsetInContext(ctx, view.cursorState.getComposingLine(), view.cursorState.getComposingOffset());
      compEnd =
          textHelper.lineCharToOffsetInContext(
              ctx, view.cursorState.getComposingLine(), view.cursorState.getComposingOffset() + view.cursorState.getComposingLength());
    }
    imm.updateSelection(view, selStart, selEnd, compStart, compEnd);
    if (imeExtractedTextMonitor) {
      ExtractedText et = textHelper.buildExtractedTextFromContext(ctx);
      imm.updateExtractedText(view, imeExtractedTextToken, et);
    }
  }

  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (view.editorConfig.behaviorConfig.isDisabled || view.editorConfig.behaviorConfig.isReadOnly) return null;
    outAttrs.inputType =
        EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    outAttrs.imeOptions =
        EditorInfo.IME_ACTION_NONE
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);
    ExtractedText et = textHelper.buildExtractedTextFromContext(ctx);
    outAttrs.initialSelStart = et.selectionStart;
    outAttrs.initialSelEnd = et.selectionEnd;

    return new EditorInputConnection(view, this, textHelper);
  }

  void setExtractedTextMonitor(boolean monitor) {
    this.imeExtractedTextMonitor = monitor;
  }

  void setExtractedTextToken(int token) {
    this.imeExtractedTextToken = token;
  }

  void setExtractedBeforeChars(int before) {
    this.imeExtractedBeforeChars = before;
  }

  void setExtractedAfterChars(int after) {
    this.imeExtractedAfterChars = after;
  }

  boolean tryReplaceWordFromImeCommit(String insert) {
    if (view.selectionState.hasSelection() || view.cursorState.hasComposing()) return false;
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
    int[] bounds = textHelper.getWordBoundsAtCursor();
    if (bounds == null) return false;
    String line = view.getLineTextForRender(view.cursorState.getCursorLine());
    if (line == null || bounds[0] >= bounds[1] || bounds[1] > line.length()) return false;
    String word = line.substring(bounds[0], bounds[1]);
    if (word.isEmpty() || word.equals(core)) return false;
    view.setSelectionInternal(view.cursorState.getCursorLine(), bounds[0], view.cursorState.getCursorLine(), bounds[1]);
    view.replaceSelectionWithText(core);
    if (!trailing.isEmpty()) view.insertTextAtCursor(trailing);
    markImeCommit(insert);
    view.charAnimator.startCharAnimationFromText(insert);
    return true;
  }

  void markImeCommit(CharSequence textSeq) {
    if (textSeq == null) return;
    view.lastImeCommitText = textSeq.toString();
    view.lastImeCommitUptime = SystemClock.uptimeMillis();
    view.suppressNextCommitText = true;
  }
}
