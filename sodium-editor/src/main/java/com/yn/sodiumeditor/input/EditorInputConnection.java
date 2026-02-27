package com.yn.sodiumeditor.input;

import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import com.yn.sodiumeditor.SodiumEditorView;

final class EditorInputConnection extends BaseInputConnection {
  private final SodiumEditorView view;
  private final InputMethodHandler manager;
  private final ImeTextHelper textHelper;
  private final Editable imeEditable = Editable.Factory.getInstance().newEditable("");

  EditorInputConnection(SodiumEditorView targetView, InputMethodHandler manager, ImeTextHelper textHelper) {
    super(targetView, true);
    this.view = targetView;
    this.manager = manager;
    this.textHelper = textHelper;
  }

  @Override
  public Editable getEditable() {
    return imeEditable;
  }

  @Override
  public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
    if (view.isDisabled || view.isReadOnly) return null;
    int before = ImeTextHelper.IME_CONTEXT_BEFORE_CHARS;
    int after = ImeTextHelper.IME_CONTEXT_AFTER_CHARS;
    if (request != null && request.hintMaxChars > 0) {
      int half = Math.max(1, request.hintMaxChars / 2);
      before = Math.min(before, half);
      after = Math.min(after, Math.max(0, request.hintMaxChars - before));
    }
    if (request != null && (flags & InputConnection.GET_EXTRACTED_TEXT_MONITOR) != 0) {
      manager.setExtractedTextMonitor(true);
      manager.setExtractedTextToken(request.token);
    }
    manager.setExtractedBeforeChars(before);
    manager.setExtractedAfterChars(after);
    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(before, after);
    return textHelper.buildExtractedTextFromContext(ctx);
  }

  @Override
  public CharSequence getTextBeforeCursor(int length, int flags) {
    if (view.isDisabled || view.isReadOnly) return "";
    return textHelper.getImeTextBeforeCursor(length);
  }

  @Override
  public CharSequence getTextAfterCursor(int length, int flags) {
    if (view.isDisabled || view.isReadOnly) return "";
    return textHelper.getImeTextAfterCursor(length);
  }

  @Override
  public CharSequence getSelectedText(int flags) {
    if (view.isDisabled || view.isReadOnly) return "";
    return view.getSelectedText();
  }

  @Override
  public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
    if (view.isDisabled || view.isReadOnly) return null;
    int before = Math.max(0, beforeLength);
    int after = Math.max(0, afterLength);
    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(before, after);
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
    if (view.isDisabled || view.isReadOnly) return true;
    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(ImeTextHelper.IME_CONTEXT_BEFORE_CHARS, ImeTextHelper.IME_CONTEXT_AFTER_CHARS);
    if (ctx.text.isEmpty()) return true;
    int textLen = ctx.text.length();
    int sOff = Math.max(0, Math.min(start, textLen));
    int eOff = Math.max(0, Math.min(end, textLen));
    int cursorOff = textHelper.lineCharToOffsetInContext(ctx, view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
    if (sOff == eOff && sOff == cursorOff && !view.selectionState.hasSelection()) return true;
    if (sOff == 0 && eOff == 0 && cursorOff > 0 && !view.selectionState.hasSelection()) return true;
    SodiumEditorView.CursorTarget s = textHelper.offsetToLineCharInContext(ctx, sOff);
    SodiumEditorView.CursorTarget e = textHelper.offsetToLineCharInContext(ctx, eOff);
    view.setSelectionInternal(s.line, s.ch, e.line, e.ch);
    view.cursorState.setCursorPosition(e.line, e.ch);
    view.cursorAnimator.resetCursorBlink();
    view.invalidate();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean setComposingRegion(int start, int end) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (start > end) {
      int t = start;
      start = end;
      end = t;
    }
    ImeTextHelper.ImeContext ctx = textHelper.buildImeContext(ImeTextHelper.IME_CONTEXT_BEFORE_CHARS, ImeTextHelper.IME_CONTEXT_AFTER_CHARS);
    if (ctx.text.isEmpty()) return true;
    int textLen = ctx.text.length();
    int sOff = Math.max(0, Math.min(start, textLen));
    int eOff = Math.max(0, Math.min(end, textLen));
    SodiumEditorView.CursorTarget s = textHelper.offsetToLineCharInContext(ctx, sOff);
    SodiumEditorView.CursorTarget e = textHelper.offsetToLineCharInContext(ctx, eOff);
    if (s.line != e.line) {
      view.setSelectionInternal(s.line, s.ch, e.line, e.ch);
      view.cursorState.setCursorPosition(e.line, e.ch);
      view.cursorAnimator.resetCursorBlink();
      view.invalidate();
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }
    view.cursorState.setComposingLine(s.line);
    view.cursorState.setComposingOffset(s.ch);
    view.cursorState.setComposingLength(Math.max(0, e.ch - s.ch));
    view.cursorState.setHasComposing(true);
    view.cursorState.setComposingStartLine(view.cursorState.getComposingLine());
    view.cursorState.setComposingStartChar(view.cursorState.getComposingOffset());
    view.cursorState.setComposingStartActive(true);
    view.charAnimator.clearLastComposingTextForCharAnim();
    view.invalidate();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean finishComposingText() {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.charAnimator.getLastComposingTextForCharAnim() != null
        && !view.charAnimator.getLastComposingTextForCharAnim().isEmpty()) {
      manager.markImeCommit(view.charAnimator.getLastComposingTextForCharAnim());
    }
    view.cursorState.setHasComposing(false);
    view.cursorState.setComposingLength(0);
    view.cursorState.setComposingStartActive(false);
    view.clearComposingPendingOpPublic();
    view.charAnimator.clearLastComposingTextForCharAnim();
    view.invalidate();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean commitCompletion(CompletionInfo text) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (text == null || text.getText() == null) return true;
    if (!view.cursorState.hasComposing() && !view.selectionState.hasSelection() && replaceWordAtCursorWith(text.getText())) {
      manager.markImeCommit(text.getText());
      return true;
    }
    return commitText(text.getText(), 1);
  }

  @Override
  public boolean commitCorrection(CorrectionInfo correctionInfo) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
    if (!view.cursorState.hasComposing()
        && !view.selectionState.hasSelection()
        && replaceWordAtCursorWith(correctionInfo.getNewText())) {
      manager.markImeCommit(correctionInfo.getNewText());
      return true;
    }
    return commitText(correctionInfo.getNewText(), 1);
  }

  @Override
  public boolean commitText(CharSequence text, int newCursorPosition) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomGestureHandler.isZoomGestureActive()) return true;
    if (text == null) return super.commitText(text, newCursorPosition);

    String str = text.toString();
    if ("\n".equals(str)) {
      view.insertNewlineAtCursor();
      view.cursorState.setHasComposing(false);
      view.cursorState.setComposingLength(0);
      view.cursorState.setComposingStartActive(false);
      view.clearComposingPendingOpPublic();
      view.charAnimator.startCharAnimationFromText(text);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (manager.tryReplaceWordFromImeCommit(str)) {
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (!view.cursorState.hasComposing() && !view.selectionState.hasSelection() && view.lastImeCommitText != null) {
      long now = SystemClock.uptimeMillis();
      if (now - view.lastImeCommitUptime < 700 && str.trim().isEmpty()) {
        int[] bounds = textHelper.getWordBoundsAtCursor();
        if (bounds != null) {
          String line = view.getLineTextForRender(view.cursorState.getCursorLine());
          if (line != null && bounds[0] < bounds[1] && bounds[1] <= line.length()) {
            String word = line.substring(bounds[0], bounds[1]);
            if (!word.isEmpty() && view.lastImeCommitText.startsWith(word)) {
              if (!word.equals(view.lastImeCommitText)) {
                view.setSelectionInternal(view.cursorState.getCursorLine(), bounds[0], view.cursorState.getCursorLine(), bounds[1]);
                view.replaceSelectionWithText(view.lastImeCommitText);
              }
              view.insertTextAtCursor(str);
              view.suppressNextCommitText = false;
              return true;
            }
          }
        }
      }
    }

    if (!view.cursorState.hasComposing() && !view.selectionState.hasSelection()) {
      long now = SystemClock.uptimeMillis();
      boolean recentIme =
          view.suppressNextCommitText
              || (view.lastImeCommitText != null && (now - view.lastImeCommitUptime) < 700);
      if (recentIme) {
        int anchorLen =
            Math.max(str.length(), (view.lastImeCommitText == null) ? 0 : view.lastImeCommitText.length());
        int beforeLen = Math.max(32, Math.min(256, anchorLen + 4));
        String before = textHelper.getImeTextBeforeCursor(beforeLen);

        if (before != null && !before.isEmpty()) {
          if (!str.isEmpty() && before.endsWith(str)) {
            view.suppressNextCommitText = false;
            return true;
          }
          if (view.lastImeCommitText != null
              && !view.lastImeCommitText.isEmpty()
              && before.endsWith(view.lastImeCommitText)) {
            if (str.equals(view.lastImeCommitText)) {
              view.suppressNextCommitText = false;
              return true;
            }
            if (str.startsWith(view.lastImeCommitText)) {
              String suffix = str.substring(view.lastImeCommitText.length());
              view.suppressNextCommitText = false;
              if (!suffix.isEmpty()) {
                String trimmed = suffix.trim();
                if (trimmed.equals(view.lastImeCommitText)) {
                  return true;
                }
                view.insertTextAtCursor(suffix);
                view.cursorState.setHasComposing(false);
                view.cursorState.setComposingLength(0);
                view.cursorState.setComposingStartActive(false);
                view.clearComposingPendingOpPublic();
                view.charAnimator.startCharAnimationFromText(suffix);
                view.handleAutoPairing(suffix);
                view.autoSuggestionManager.updateSuggestion();
              }
              return true;
            }
          }
        }
        view.suppressNextCommitText = false;
      }
    }

    if (view.selectionState.hasSelection()) {
      view.replaceSelectionWithText(str);
      view.cursorState.setHasComposing(false);
      view.cursorState.setComposingLength(0);
      view.cursorState.setComposingStartActive(false);
      view.clearComposingPendingOpPublic();
      view.charAnimator.startCharAnimationFromText(text);
      view.handleAutoPairing(str);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (view.cursorState.hasComposing()) {
      int startLine = view.cursorState.isComposingStartActive() ? view.cursorState.getComposingStartLine() : view.cursorState.getComposingLine();
      int startChar = view.cursorState.isComposingStartActive() ? view.cursorState.getComposingStartChar() : view.cursorState.getComposingOffset();
      view.imeCompositionHandler.replaceComposingWith(text);
      view.updateComposingPendingOpPublic(str, startLine, startChar);
      view.cursorState.setHasComposing(false);
      view.cursorState.setComposingLength(0);
      view.cursorState.setComposingStartActive(false);
      view.clearComposingPendingOpPublic();
      manager.markImeCommit(str);
      view.charAnimator.startCharAnimationFromText(text);
      view.handleAutoPairing(str);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    view.insertTextAtCursor(str);
    view.cursorState.setHasComposing(false);
    view.cursorState.setComposingLength(0);
    view.cursorState.setComposingStartActive(false);
    view.clearComposingPendingOpPublic();
    view.charAnimator.startCharAnimationFromText(text);
    view.handleAutoPairing(str);

    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean setComposingText(CharSequence text, int newCursorPosition) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomGestureHandler.isZoomGestureActive()) return true;
    if (text == null) return true;

    if (view.selectionState.hasSelection()) {
      view.replaceSelectionWithText(text.toString());
      view.charAnimator.startCharAnimationFromText(text);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    view.scrollManager.ensureLineInWindow(view.cursorState.getCursorLine(), true);
    if (!view.cursorState.hasComposing()) {
      view.cursorState.setComposingLine(view.cursorState.getCursorLine());
      view.cursorState.setComposingOffset(view.cursorState.getCursorChar());
      view.cursorState.setComposingLength(0);
      view.cursorState.setHasComposing(true);
      view.cursorState.setComposingStartLine(view.cursorState.getComposingLine());
      view.cursorState.setComposingStartChar(view.cursorState.getComposingOffset());
      view.cursorState.setComposingStartActive(true);
    }
    String newText = text.toString();
    String oldText =
        (view.charAnimator.getLastComposingTextForCharAnim() == null)
            ? ""
            : view.charAnimator.getLastComposingTextForCharAnim();
    boolean shouldAnim = newText.length() >= oldText.length() && !newText.equals(oldText);
    view.imeCompositionHandler.replaceComposingWith(newText);
    view.updateComposingPendingOpPublic(newText, view.cursorState.getComposingStartLine(), view.cursorState.getComposingStartChar());
    view.charAnimator.setLastComposingTextForCharAnim(newText);
    if (shouldAnim) view.charAnimator.startCharAnimationFromText(newText);
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean deleteSurroundingText(int beforeLength, int afterLength) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomGestureHandler.isZoomGestureActive()) return true;

    if (view.selectionState.hasSelection()) {
      view.replaceSelectionWithText("");
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }
    for (int i = 0; i < beforeLength; i++) view.cursorNavigation.moveCursorLeft();
    for (int i = 0; i < afterLength; i++) view.cursorNavigation.moveCursorRight();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  private boolean replaceWordAtCursorWith(CharSequence textSeq) {
    if (textSeq == null) return false;
    String insert = textSeq.toString();
    if (insert.isEmpty()) return false;
    if (view.selectionState.hasSelection()) {
      view.replaceSelectionWithText(insert);
      return true;
    }
    String line = view.getLineTextForRender(view.cursorState.getCursorLine());
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(view.cursorState.getCursorChar(), line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = view.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    view.setSelectionInternal(view.cursorState.getCursorLine(), bounds[0], view.cursorState.getCursorLine(), bounds[1]);
    view.replaceSelectionWithText(insert);
    return true;
  }
}
