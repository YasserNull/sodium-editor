package com.yn.sodiumeditor.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.os.SystemClock;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import androidx.annotation.Nullable;
import java.io.RandomAccessFile;

final class IMEManager {
  private static final int IME_CONTEXT_BEFORE_CHARS = 2048;
  private static final int IME_CONTEXT_AFTER_CHARS = 2048;

  private final SodiumEditorView view;
  private final Editable imeEditable = Editable.Factory.getInstance().newEditable("");
  private boolean imeExtractedTextMonitor = false;
  private int imeExtractedTextToken = 0;
  private int imeExtractedBeforeChars = IME_CONTEXT_BEFORE_CHARS;
  private int imeExtractedAfterChars = IME_CONTEXT_AFTER_CHARS;

  IMEManager(SodiumEditorView view) {
    this.view = view;
  }

  boolean onCheckIsTextEditor() {
    return !view.isDisabled && !view.isReadOnly;
  }

  void restartInput() {
    InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(view);
    }
  }

  void showKeyboard() {
    if (view.isReadOnly) return;
    view.requestFocus();
    InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(view, 0);
  }

  void updateImeSelection() {
    if (view.isDisabled || view.isReadOnly) return;
    if (!view.isFocused()) return;
    InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm == null || !imm.isActive(view)) return;

    ImeContext ctx = buildImeContext(imeExtractedBeforeChars, imeExtractedAfterChars);

    int sLine = view.cursorLine, sChar = view.cursorChar, eLine = view.cursorLine, eChar = view.cursorChar;
    if (view.hasSelection) {
      sLine = view.selStartLine;
      sChar = view.selStartChar;
      eLine = view.selEndLine;
      eChar = view.selEndChar;
      if (view.comparePos(sLine, sChar, eLine, eChar) > 0) {
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
    if (view.hasComposing) {
      compStart = lineCharToOffsetInContext(ctx, view.composingLine, view.composingOffset);
      compEnd =
          lineCharToOffsetInContext(
              ctx, view.composingLine, view.composingOffset + view.composingLength);
    }
    imm.updateSelection(view, selStart, selEnd, compStart, compEnd);
    if (imeExtractedTextMonitor) {
      ExtractedText et = buildExtractedTextFromContext(ctx);
      imm.updateExtractedText(view, imeExtractedTextToken, et);
    }
  }

  InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (view.isDisabled || view.isReadOnly) return null;
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

    return new BaseInputConnection(view, true) {
      @Override
      public Editable getEditable() {
        return imeEditable;
      }

      @Override
      public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (view.isDisabled || view.isReadOnly) return null;
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
        if (view.isDisabled || view.isReadOnly) return "";
        return getImeTextBeforeCursor(length);
      }

      @Override
      public CharSequence getTextAfterCursor(int length, int flags) {
        if (view.isDisabled || view.isReadOnly) return "";
        return getImeTextAfterCursor(length);
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
        ImeContext ctx = buildImeContext(before, after);
        int sLine = view.cursorLine, sChar = view.cursorChar, eLine = view.cursorLine, eChar = view.cursorChar;
        if (view.hasSelection) {
          sLine = view.selStartLine;
          sChar = view.selStartChar;
          eLine = view.selEndLine;
          eChar = view.selEndChar;
          if (view.comparePos(sLine, sChar, eLine, eChar) > 0) {
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
        if (view.isDisabled || view.isReadOnly) return true;
        ImeContext ctx = buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
        if (ctx.text.isEmpty()) return true;
        int textLen = ctx.text.length();
        int sOff = Math.max(0, Math.min(start, textLen));
        int eOff = Math.max(0, Math.min(end, textLen));
        int cursorOff = lineCharToOffsetInContext(ctx, view.cursorLine, view.cursorChar);
        if (sOff == eOff && sOff == cursorOff && !view.hasSelection) return true;
        if (sOff == 0 && eOff == 0 && cursorOff > 0 && !view.hasSelection) return true;
        SodiumEditorView.CursorTarget s = offsetToLineCharInContext(ctx, sOff);
        SodiumEditorView.CursorTarget e = offsetToLineCharInContext(ctx, eOff);
        view.setSelectionInternal(s.line, s.ch, e.line, e.ch);
        view.cursorLine = e.line;
        view.cursorChar = e.ch;
        view.resetCursorBlink();
        view.invalidate();
        view.updateSuggestion();
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
        ImeContext ctx = buildImeContext(IME_CONTEXT_BEFORE_CHARS, IME_CONTEXT_AFTER_CHARS);
        if (ctx.text.isEmpty()) return true;
        int textLen = ctx.text.length();
        int sOff = Math.max(0, Math.min(start, textLen));
        int eOff = Math.max(0, Math.min(end, textLen));
        SodiumEditorView.CursorTarget s = offsetToLineCharInContext(ctx, sOff);
        SodiumEditorView.CursorTarget e = offsetToLineCharInContext(ctx, eOff);
        if (s.line != e.line) {
          view.setSelectionInternal(s.line, s.ch, e.line, e.ch);
          view.cursorLine = e.line;
          view.cursorChar = e.ch;
          view.resetCursorBlink();
          view.invalidate();
          view.updateSuggestion();
          return true;
        }
        view.composingLine = s.line;
        view.composingOffset = s.ch;
        view.composingLength = Math.max(0, e.ch - s.ch);
        view.hasComposing = true;
        view.composingStartLine = view.composingLine;
        view.composingStartChar = view.composingOffset;
        view.composingStartActive = true;
        view.clearLastComposingTextForCharAnim();
        view.invalidate();
        view.updateSuggestion();
        return true;
      }

      @Override
      public boolean finishComposingText() {
        if (view.isDisabled || view.isReadOnly) return true;
        if (view.getLastComposingTextForCharAnim() != null
            && !view.getLastComposingTextForCharAnim().isEmpty()) {
          markImeCommit(view.getLastComposingTextForCharAnim());
        }
        view.commitComposing(true);
        return true;
      }

      @Override
      public boolean commitCompletion(android.view.inputmethod.CompletionInfo text) {
        if (view.isDisabled || view.isReadOnly) return true;
        if (text == null || text.getText() == null) return true;
        if (!view.hasComposing && !view.hasSelection && replaceWordAtCursorWith(text.getText())) {
          markImeCommit(text.getText());
          return true;
        }
        return commitText(text.getText(), 1);
      }

      @Override
      public boolean commitCorrection(android.view.inputmethod.CorrectionInfo correctionInfo) {
        if (view.isDisabled || view.isReadOnly) return true;
        if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
        if (!view.hasComposing
            && !view.hasSelection
            && replaceWordAtCursorWith(correctionInfo.getNewText())) {
          markImeCommit(correctionInfo.getNewText());
          return true;
        }
        return commitText(correctionInfo.getNewText(), 1);
      }

      @Override
      public boolean commitText(CharSequence text, int newCursorPosition) {
        if (view.isDisabled || view.isReadOnly) return true;
        if (view.isZoomGestureActive()) return true;
        if (text == null) return super.commitText(text, newCursorPosition);

        String str = text.toString();
        if ("\n".equals(str)) {
          view.insertNewlineAtCursor();
          view.commitComposing(true);
          view.startCharAnimationFromText(text);
          view.updateSuggestion();
          return true;
        }

        if (tryReplaceWordFromImeCommit(str)) {
          view.updateSuggestion();
          return true;
        }

        if (!view.hasComposing && !view.hasSelection && view.lastImeCommitText != null) {
          long now = SystemClock.uptimeMillis();
          if (now - view.lastImeCommitUptime < 700 && str.trim().isEmpty()) {
            int[] bounds = getWordBoundsAtCursor();
            if (bounds != null) {
              String line = view.getLineTextForRender(view.cursorLine);
              if (line != null && bounds[0] < bounds[1] && bounds[1] <= line.length()) {
                String word = line.substring(bounds[0], bounds[1]);
                if (!word.isEmpty() && view.lastImeCommitText.startsWith(word)) {
                  if (!word.equals(view.lastImeCommitText)) {
                    view.setSelectionInternal(view.cursorLine, bounds[0], view.cursorLine, bounds[1]);
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

        if (!view.hasComposing && !view.hasSelection) {
          long now = SystemClock.uptimeMillis();
          boolean recentIme =
              view.suppressNextCommitText
                  || (view.lastImeCommitText != null && (now - view.lastImeCommitUptime) < 700);
          if (recentIme) {
            int anchorLen =
                Math.max(str.length(), (view.lastImeCommitText == null) ? 0 : view.lastImeCommitText.length());
            int beforeLen = Math.max(32, Math.min(256, anchorLen + 4));
            String before = getImeTextBeforeCursor(beforeLen);

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
                    view.commitComposing(true);
                    view.startCharAnimationFromText(suffix);
                    view.handleAutoPairing(suffix);
                    view.updateSuggestion();
                  }
                  return true;
                }
              }
            }
            view.suppressNextCommitText = false;
          }
        }

        if (view.hasSelection) {
          view.replaceSelectionWithText(str);
          view.commitComposing(true);
          view.startCharAnimationFromText(text);
          view.handleAutoPairing(str);
          view.updateSuggestion();
          return true;
        }

        if (view.hasComposing) {
          int startLine = view.composingStartActive ? view.composingStartLine : view.composingLine;
          int startChar = view.composingStartActive ? view.composingStartChar : view.composingOffset;
          view.replaceComposingWith(text);
          view.updateComposingPendingOp(str, startLine, startChar);
          view.commitComposing(true);
          markImeCommit(str);
          view.startCharAnimationFromText(text);
          view.handleAutoPairing(str);
          view.updateSuggestion();
          return true;
        }

        view.insertTextAtCursor(str);
        view.commitComposing(true);
        view.startCharAnimationFromText(text);
        view.handleAutoPairing(str);

        view.updateSuggestion();
        return true;
      }

      @Override
      public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (view.isDisabled || view.isReadOnly) return true;
        if (view.isZoomGestureActive()) return true;
        if (text == null) return true;

        if (view.hasSelection) {
          view.replaceSelectionWithText(text.toString());
          view.startCharAnimationFromText(text);
          view.updateSuggestion();
          return true;
        }

        view.getScrollManager().ensureLineInWindow(view.cursorLine, true);
        if (!view.hasComposing) {
          view.composingLine = view.cursorLine;
          view.composingOffset = view.cursorChar;
          view.composingLength = 0;
          view.hasComposing = true;
          view.composingStartLine = view.composingLine;
          view.composingStartChar = view.composingOffset;
          view.composingStartActive = true;
        }
        String newText = text.toString();
        String oldText =
            (view.getLastComposingTextForCharAnim() == null)
                ? ""
                : view.getLastComposingTextForCharAnim();
        boolean shouldAnim = newText.length() >= oldText.length() && !newText.equals(oldText);
        view.replaceComposingWith(newText);
        view.updateComposingPendingOp(newText, view.composingStartLine, view.composingStartChar);
        view.setLastComposingTextForCharAnim(newText);
        if (shouldAnim) view.startCharAnimationFromText(newText);
        view.updateSuggestion();
        return true;
      }

      @Override
      public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (view.isDisabled || view.isReadOnly) return true;
        if (view.isZoomGestureActive()) return true;

        if (view.hasSelection) {
          view.replaceSelectionWithText("");
          view.updateSuggestion();
          return true;
        }
        for (int i = 0; i < beforeLength; i++) view.deleteCharAtCursor();
        for (int i = 0; i < afterLength; i++) view.deleteForwardAtCursor();
        view.updateSuggestion();
        return true;
      }
    };
  }

  private static final class ImeContext {
    final int startLine;
    final int startChar;
    final String text;

    ImeContext(int startLine, int startChar, String text) {
      this.startLine = startLine;
      this.startChar = startChar;
      this.text = (text == null) ? "" : text;
    }
  }

  @Nullable
  private RandomAccessFile openImeRandomAccessFile() {
    if (!view.isIndexReady || view.sourceFile == null || !view.sourceFile.exists()) return null;
    try {
      return new RandomAccessFile(view.sourceFile, "r");
    } catch (Exception ignored) {
      return null;
    }
  }

  private String getLineTextForImeScan(int line, @Nullable RandomAccessFile raf) {
    if (line < 0) return "";
    String mod = view.modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= view.windowStartLine && line < view.windowStartLine + view.linesWindow.size()) {
      String text = view.getLineFromWindowLocal(line - view.windowStartLine);
      return (text != null) ? text : "";
    }
    if (raf != null && view.isIndexReady) {
      long offset;
      synchronized (view.lineOffsetsLock) {
        if (line < 0 || line >= view.lineOffsets.length) return "";
        offset = view.lineOffsets[line];
      }
      try {
        return view.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return "";
      }
    }
    return "";
  }

  private SodiumEditorView.CursorTarget clampLineCharToDocument(
      int line, int ch, @Nullable RandomAccessFile raf) {
    int total = view.getLinesCount();
    if (total <= 0) return new SodiumEditorView.CursorTarget(0, 0);
    int clampedLine = Math.max(0, Math.min(line, total - 1));
    String ln = getLineTextForImeScan(clampedLine, raf);
    int len = (ln == null) ? 0 : ln.length();
    int clampedChar = Math.max(0, Math.min(ch, len));
    return new SodiumEditorView.CursorTarget(clampedLine, clampedChar);
  }

  private SodiumEditorView.CursorTarget moveCursorByCharsForIme(
      int line, int ch, int delta, @Nullable RandomAccessFile raf) {
    SodiumEditorView.CursorTarget base = clampLineCharToDocument(line, ch, raf);
    int curLine = base.line;
    int curChar = base.ch;
    int totalLines = view.getLinesCount();
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
        remaining -= 1;
        curLine--;
        ln = getLineTextForImeScan(curLine, raf);
        curChar = (ln == null) ? 0 : ln.length();
        if (remaining < 0) remaining = 0;
      }
      return new SodiumEditorView.CursorTarget(curLine, curChar);
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
      remaining -= 1;
      curLine++;
      curChar = 0;
      if (remaining < 0) remaining = 0;
    }
    return new SodiumEditorView.CursorTarget(curLine, curChar);
  }

  private String buildRangeTextForIme(
      SodiumEditorView.CursorTarget start,
      SodiumEditorView.CursorTarget end,
      @Nullable RandomAccessFile raf) {
    int sL = start.line, sC = start.ch, eL = end.line, eC = end.ch;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
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

  private ImeContext buildImeContext(int beforeChars, int afterChars) {
    int before = Math.max(0, beforeChars);
    int after = Math.max(0, afterChars);
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget start =
          moveCursorByCharsForIme(view.cursorLine, view.cursorChar, -before, raf);
      SodiumEditorView.CursorTarget end =
          moveCursorByCharsForIme(view.cursorLine, view.cursorChar, after, raf);
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

  private ExtractedText buildExtractedTextFromContext(ImeContext ctx) {
    ExtractedText et = new ExtractedText();
    et.text = ctx.text;
    et.startOffset = 0;
    et.partialStartOffset = -1;
    et.partialEndOffset = -1;

    int sLine = view.cursorLine, sChar = view.cursorChar, eLine = view.cursorLine, eChar = view.cursorChar;
    if (view.hasSelection) {
      sLine = view.selStartLine;
      sChar = view.selStartChar;
      eLine = view.selEndLine;
      eChar = view.selEndChar;
      if (view.comparePos(sLine, sChar, eLine, eChar) > 0) {
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

  private SodiumEditorView.CursorTarget offsetToLineCharInContext(ImeContext ctx, int offset) {
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
    return new SodiumEditorView.CursorTarget(line, ch);
  }

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

  private String getImeTextBeforeCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget start =
          moveCursorByCharsForIme(view.cursorLine, view.cursorChar, -length, raf);
      return buildRangeTextForIme(start, new SodiumEditorView.CursorTarget(view.cursorLine, view.cursorChar), raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private String getImeTextAfterCursor(int length) {
    if (length <= 0) return "";
    RandomAccessFile raf = openImeRandomAccessFile();
    try {
      SodiumEditorView.CursorTarget end =
          moveCursorByCharsForIme(view.cursorLine, view.cursorChar, length, raf);
      return buildRangeTextForIme(new SodiumEditorView.CursorTarget(view.cursorLine, view.cursorChar), end, raf);
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private boolean replaceWordAtCursorWith(CharSequence textSeq) {
    if (textSeq == null) return false;
    String insert = textSeq.toString();
    if (insert.isEmpty()) return false;
    if (view.hasSelection) {
      view.replaceSelectionWithText(insert);
      return true;
    }
    String line = view.getLineTextForRender(view.cursorLine);
    if (line == null || line.isEmpty()) return false;
    int pos = Math.max(0, Math.min(view.cursorChar, line.length()));
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    int[] bounds = view.computeWordBounds(line, pos);
    if (bounds[0] == bounds[1]) return false;
    view.setSelectionInternal(view.cursorLine, bounds[0], view.cursorLine, bounds[1]);
    view.replaceSelectionWithText(insert);
    return true;
  }

  private boolean tryReplaceWordFromImeCommit(String insert) {
    if (view.hasSelection || view.hasComposing) return false;
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
    String line = view.getLineTextForRender(view.cursorLine);
    if (line == null || bounds[0] >= bounds[1] || bounds[1] > line.length()) return false;
    String word = line.substring(bounds[0], bounds[1]);
    if (word.isEmpty() || word.equals(core)) return false;
    view.setSelectionInternal(view.cursorLine, bounds[0], view.cursorLine, bounds[1]);
    view.replaceSelectionWithText(core);
    if (!trailing.isEmpty()) view.insertTextAtCursor(trailing);
    markImeCommit(insert);
    view.startCharAnimationFromText(insert);
    return true;
  }

  @Nullable
  private int[] getWordBoundsAtCursor() {
    String line = view.getLineTextForRender(view.cursorLine);
    if (line == null || line.isEmpty()) return null;
    int pos = Math.max(0, Math.min(view.cursorChar, line.length()));
    if (pos == line.length() && pos > 0) pos--;
    if (pos < 0 || pos >= line.length()) return null;
    if (Character.isWhitespace(line.charAt(pos))) return null;
    return view.computeWordBounds(line, pos);
  }

  private void markImeCommit(CharSequence textSeq) {
    if (textSeq == null) return;
    view.lastImeCommitText = textSeq.toString();
    view.lastImeCommitUptime = SystemClock.uptimeMillis();
    view.suppressNextCommitText = true;
  }
}
