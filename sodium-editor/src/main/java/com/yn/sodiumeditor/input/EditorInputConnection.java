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
    int sLine = view.cursorManager.getLine(), sChar = view.cursorManager.getChar(), eLine = view.cursorManager.getLine(), eChar = view.cursorManager.getChar();
    if (view.selectionManager.hasSelection()) {
      sLine = view.selectionManager.selStartLine;
      sChar = view.selectionManager.selStartChar;
      eLine = view.selectionManager.selEndLine;
      eChar = view.selectionManager.selEndChar;
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
    int cursorOff = textHelper.lineCharToOffsetInContext(ctx, view.cursorManager.getLine(), view.cursorManager.getChar());
    if (sOff == eOff && sOff == cursorOff && !view.selectionManager.hasSelection()) return true;
    if (sOff == 0 && eOff == 0 && cursorOff > 0 && !view.selectionManager.hasSelection()) return true;
    SodiumEditorView.CursorTarget s = textHelper.offsetToLineCharInContext(ctx, sOff);
    SodiumEditorView.CursorTarget e = textHelper.offsetToLineCharInContext(ctx, eOff);
    view.setSelectionInternal(s.line, s.ch, e.line, e.ch);
    view.cursorManager.setLineAndChar(e.line, e.ch);
    view.cursorAnimationManager.resetCursorBlink();
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
      view.cursorManager.setLineAndChar(e.line, e.ch);
      view.cursorAnimationManager.resetCursorBlink();
      view.invalidate();
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }
    view.cursorManager.setComposingLine(s.line);
    view.cursorManager.setComposingOffset(s.ch);
    view.cursorManager.setComposingLength(Math.max(0, e.ch - s.ch));
    view.cursorManager.setHasComposing(true);
    view.cursorManager.setComposingStartLine(view.cursorManager.getComposingLine());
    view.cursorManager.setComposingStartChar(view.cursorManager.getComposingOffset());
    view.cursorManager.setComposingStartActive(true);
    view.charAnimationManager.clearLastComposingTextForCharAnim();
    view.invalidate();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean finishComposingText() {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.charAnimationManager.getLastComposingTextForCharAnim() != null
        && !view.charAnimationManager.getLastComposingTextForCharAnim().isEmpty()) {
      manager.markImeCommit(view.charAnimationManager.getLastComposingTextForCharAnim());
    }
    view.cursorManager.commitComposing(true);
    return true;
  }

  @Override
  public boolean commitCompletion(CompletionInfo text) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (text == null || text.getText() == null) return true;
    if (!view.cursorManager.getHasComposing() && !view.selectionManager.hasSelection() && replaceWordAtCursorWith(text.getText())) {
      manager.markImeCommit(text.getText());
      return true;
    }
    return commitText(text.getText(), 1);
  }

  @Override
  public boolean commitCorrection(CorrectionInfo correctionInfo) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
    if (!view.cursorManager.getHasComposing()
        && !view.selectionManager.hasSelection()
        && replaceWordAtCursorWith(correctionInfo.getNewText())) {
      manager.markImeCommit(correctionInfo.getNewText());
      return true;
    }
    return commitText(correctionInfo.getNewText(), 1);
  }

  @Override
  public boolean commitText(CharSequence text, int newCursorPosition) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomManager.isZoomGestureActive()) return true;
    if (text == null) return super.commitText(text, newCursorPosition);

    String str = text.toString();
    if ("\n".equals(str)) {
      view.insertNewlineAtCursor();
      view.cursorManager.commitComposing(true);
      view.charAnimationManager.startCharAnimationFromText(text);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (manager.tryReplaceWordFromImeCommit(str)) {
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (!view.cursorManager.getHasComposing() && !view.selectionManager.hasSelection() && view.lastImeCommitText != null) {
      long now = SystemClock.uptimeMillis();
      if (now - view.lastImeCommitUptime < 700 && str.trim().isEmpty()) {
        int[] bounds = textHelper.getWordBoundsAtCursor();
        if (bounds != null) {
          String line = view.getLineTextForRender(view.cursorManager.getLine());
          if (line != null && bounds[0] < bounds[1] && bounds[1] <= line.length()) {
            String word = line.substring(bounds[0], bounds[1]);
            if (!word.isEmpty() && view.lastImeCommitText.startsWith(word)) {
              if (!word.equals(view.lastImeCommitText)) {
                view.setSelectionInternal(view.cursorManager.getLine(), bounds[0], view.cursorManager.getLine(), bounds[1]);
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

    if (!view.cursorManager.getHasComposing() && !view.selectionManager.hasSelection()) {
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
                view.cursorManager.commitComposing(true);
                view.charAnimationManager.startCharAnimationFromText(suffix);
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

    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText(str);
      view.cursorManager.commitComposing(true);
      view.charAnimationManager.startCharAnimationFromText(text);
      view.handleAutoPairing(str);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    if (view.cursorManager.getHasComposing()) {
      int startLine = view.cursorManager.getComposingStartActive() ? view.cursorManager.getComposingStartLine() : view.cursorManager.getComposingLine();
      int startChar = view.cursorManager.getComposingStartActive() ? view.cursorManager.getComposingStartChar() : view.cursorManager.getComposingOffset();
      view.cursorManager.replaceComposingWith(text);
      view.updateComposingPendingOpPublic(str, startLine, startChar);
      view.cursorManager.commitComposing(true);
      manager.markImeCommit(str);
      view.charAnimationManager.startCharAnimationFromText(text);
      view.handleAutoPairing(str);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    view.insertTextAtCursor(str);
    view.cursorManager.commitComposing(true);
    view.charAnimationManager.startCharAnimationFromText(text);
    view.handleAutoPairing(str);

    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean setComposingText(CharSequence text, int newCursorPosition) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomManager.isZoomGestureActive()) return true;
    if (text == null) return true;

    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText(text.toString());
      view.charAnimationManager.startCharAnimationFromText(text);
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }

    view.scrollManager.ensureLineInWindow(view.cursorManager.getLine(), true);
    if (!view.cursorManager.getHasComposing()) {
      view.cursorManager.setComposingLine(view.cursorManager.getLine());
      view.cursorManager.setComposingOffset(view.cursorManager.getChar());
      view.cursorManager.setComposingLength(0);
      view.cursorManager.setHasComposing(true);
      view.cursorManager.setComposingStartLine(view.cursorManager.getComposingLine());
      view.cursorManager.setComposingStartChar(view.cursorManager.getComposingOffset());
      view.cursorManager.setComposingStartActive(true);
    }
    String newText = text.toString();
    String oldText =
        (view.charAnimationManager.getLastComposingTextForCharAnim() == null)
            ? ""
            : view.charAnimationManager.getLastComposingTextForCharAnim();
    boolean shouldAnim = newText.length() >= oldText.length() && !newText.equals(oldText);
    view.cursorManager.replaceComposingWith(newText);
    view.updateComposingPendingOpPublic(newText, view.cursorManager.getComposingStartLine(), view.cursorManager.getComposingStartChar());
    view.charAnimationManager.setLastComposingTextForCharAnim(newText);
    if (shouldAnim) view.charAnimationManager.startCharAnimationFromText(newText);
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  @Override
  public boolean deleteSurroundingText(int beforeLength, int afterLength) {
    if (view.isDisabled || view.isReadOnly) return true;
    if (view.zoomManager.isZoomGestureActive()) return true;

    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText("");
      view.autoSuggestionManager.updateSuggestion();
      return true;
    }
    for (int i = 0; i < beforeLength; i++) view.deleteCharAtCursor();
    for (int i = 0; i < afterLength; i++) view.deleteForwardAtCursor();
    view.autoSuggestionManager.updateSuggestion();
    return true;
  }

  private boolean replaceWordAtCursorWith(CharSequence textSeq) {
    if (textSeq == null) return false;
    String insert = textSeq.toString();
    if (insert.isEmpty()) return false;
    if (view.selectionManager.hasSelection()) {
      view.replaceSelectionWithText(insert);
      return true;
    }
    String line = view.getLineTextForRender(view.cursorManager.getLine());
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(view.cursorManager.getChar(), line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = view.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    view.setSelectionInternal(view.cursorManager.getLine(), bounds[0], view.cursorManager.getLine(), bounds[1]);
    view.replaceSelectionWithText(insert);
    return true;
  }
}
