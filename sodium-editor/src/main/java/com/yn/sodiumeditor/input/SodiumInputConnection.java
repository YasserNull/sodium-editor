package com.yn.sodiumeditor.input;

import android.text.Editable;
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.SurroundingText;
import com.yn.sodiumeditor.SodiumEditor;

/** Implementation of InputConnection for SodiumEditor. */
public class SodiumInputConnection extends BaseInputConnection {
  private static final String FOLD_TYPING_PERF = "FoldTypingPerf";
  private static final String TAG = "SodiumSelectionEdit";
  private static final int MAX_IME_LOGS = 240;
  public static boolean DEBUG_IME_SELECTION_LOGS = false;
  private final SodiumEditor editor;
  private final Ime ime;
  private int imeLogCount = 0;

  public SodiumInputConnection(SodiumEditor editor, Ime ime) {
    super(editor, true);
    this.editor = editor;
    this.ime = ime;
  }

  @Override
  public Editable getEditable() {
    return ime.imeEditable;
  }

  @Override
  public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return null;
    long startMs = android.os.SystemClock.uptimeMillis();
    ExtractedText result = ime.onGetExtractedText(request, flags);
    return result;
  }

  @Override
  public CharSequence getTextBeforeCursor(int length, int flags) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return "";
    long startMs = android.os.SystemClock.uptimeMillis();
    CharSequence result = ime.scanner.getImeTextBeforeCursor(length);
    return result;
  }

  @Override
  public CharSequence getTextAfterCursor(int length, int flags) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return "";
    long startMs = android.os.SystemClock.uptimeMillis();
    CharSequence result = ime.scanner.getImeTextAfterCursor(length);
    return result;
  }

  @Override
  public CharSequence getSelectedText(int flags) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return "";
    long startMs = android.os.SystemClock.uptimeMillis();
    CharSequence result = editor.selection.getSelectedText();
    return result;
  }

  @Override
  public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return null;
    int before = Math.max(0, beforeLength);
    int after = Math.max(0, afterLength);
    long startMs = android.os.SystemClock.uptimeMillis();
    long ctxStartMs = startMs;
    ImeContext ctx = ime.scanner.buildImeContext(before, after);
    long ctxMs = android.os.SystemClock.uptimeMillis() - ctxStartMs;

    int sLine = editor.cursor.cursorLine, sChar = editor.cursor.cursorChar;
    int eLine = editor.cursor.cursorLine, eChar = editor.cursor.cursorChar;
    if (editor.selection.hasSelection) {
      sLine = editor.selection.selStartLine;
      sChar = editor.selection.selStartChar;
      eLine = editor.selection.selEndLine;
      eChar = editor.selection.selEndChar;
      if (ime.scanner.comparePos(sLine, sChar, eLine, eChar) > 0) {
        int tL = sLine, tC = sChar;
        sLine = eLine;
        sChar = eChar;
        eLine = tL;
        eChar = tC;
      }
    }
    int selStart = ime.scanner.lineCharToOffsetInContext(ctx, sLine, sChar);
    int selEnd = ime.scanner.lineCharToOffsetInContext(ctx, eLine, eChar);
    SurroundingText result = new SurroundingText(ctx.text, selStart, selEnd, 0);
    return result;
  }

  @Override
  public int getCursorCapsMode(int reqModes) {
    CharSequence before = getTextBeforeCursor(2048, 0);
    int len = (before == null) ? 0 : before.length();
    return TextUtils.getCapsMode(before, len, reqModes);
  }

  @Override
  public boolean setSelection(int start, int end) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    return ime.onSetSelection(start, end);
  }

  @Override
  public boolean setComposingRegion(int start, int end) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    return ime.onSetComposingRegion(start, end);
  }

  @Override
  public boolean finishComposingText() {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    ime.onFinishComposingText();
    return true;
  }

  @Override
  public boolean commitCompletion(CompletionInfo text) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (text == null || text.getText() == null) return true;
    return ime.onCommitCompletion(text.getText());
  }

  @Override
  public boolean commitCorrection(CorrectionInfo correctionInfo) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (correctionInfo == null || correctionInfo.getNewText() == null) return true;
    return ime.onCommitCorrection(correctionInfo.getNewText());
  }

  @Override
  public boolean commitText(CharSequence text, int newCursorPosition) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (editor.zoom.isZoomGestureActive()) return true;
    if (text == null) return super.commitText(text, newCursorPosition);
    int beforeLine = editor.cursor.cursorLine;
    int beforeChar = editor.cursor.cursorChar;
    long startMs = android.os.SystemClock.uptimeMillis();
    boolean result = ime.onCommitText(text, newCursorPosition);
    long totalMs = android.os.SystemClock.uptimeMillis() - startMs;
    return result;
  }

  @Override
  public boolean setComposingText(CharSequence text, int newCursorPosition) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (editor.zoom.isZoomGestureActive()) return true;
    if (text == null) return true;
    return ime.onSetComposingText(text, newCursorPosition);
  }

  @Override
  public boolean deleteSurroundingText(int beforeLength, int afterLength) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (editor.zoom.isZoomGestureActive()) return true;
    return ime.onDeleteSurroundingText(beforeLength, afterLength);
  }

  @Override
  public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
    if (editor.view.isDisabled || editor.view.isReadOnly) return true;
    if (editor.zoom.isZoomGestureActive()) return true;
    return ime.onDeleteSurroundingTextInCodePoints(beforeLength, afterLength);
  }
}
