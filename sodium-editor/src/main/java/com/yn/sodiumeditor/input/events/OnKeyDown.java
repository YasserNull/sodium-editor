package com.yn.sodiumeditor.input.events;

import android.util.Log;
import android.view.KeyEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles key down events for the SodiumEditor.
 */
public class OnKeyDown {
  private static final String TAG = "SodiumSelectionEdit";
  private static final int MAX_KEY_LOGS = 240;
  public static boolean DEBUG_KEY_SELECTION_LOGS = true;

  private final SodiumEditor editor;
  private int keyLogCount = 0;

  public OnKeyDown(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Handles key down events.
   * @param keyCode The key code of the pressed key
   * @param event The KeyEvent object
   * @return true if the event was handled, false otherwise
   */
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    logKeyEvent("keyDown", keyCode, event, getPrintingText(event));
    if (editor.view.isDisabled) return true;
    
    if (editor.view.isReadOnly) {
      return handleReadOnlyKey(keyCode, event);
    }

    if (editor.selection.hasSelection && getPrintingText(event) != null) {
      return handleSelectionWithPrintingKey(event);
    }

    return handleNormalKey(keyCode, event);
  }

  /**
   * Handles key events in read-only mode.
   */
  private boolean handleReadOnlyKey(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        editor.cursor.moveCursorLeft();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        editor.cursor.moveCursorRight();
        return true;
      case KeyEvent.KEYCODE_DPAD_UP:
        editor.cursor.moveCursorUp();
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        editor.cursor.moveCursorDown();
        return true;
      case KeyEvent.KEYCODE_DEL:
      case KeyEvent.KEYCODE_FORWARD_DEL:
      case KeyEvent.KEYCODE_ENTER:
        return true;
    }
    String text = getPrintingText(event);
    if (text != null) {
      editor.editOperators.insertTextAtCursor(text);
      editor.charAnimation.startCharAnimationFromText(text);
      return true;
    }
    return false;
  }

  /**
   * Handles printing key when selection is active.
   */
  private boolean handleSelectionWithPrintingKey(KeyEvent event) {
    String text = getPrintingText(event);
    logKeyEvent("selection.printingKey", event.getKeyCode(), event, text);
    if (text != null) {
      editor.selection.replaceSelectionWithText(text);
      editor.charAnimation.startCharAnimationFromText(text);
      return true;
    }
    return true;
  }

  /**
   * Handles key events in normal mode.
   */
  private boolean handleNormalKey(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        editor.cursor.moveCursorLeft();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        editor.cursor.moveCursorRight();
        return true;
      case KeyEvent.KEYCODE_DPAD_UP:
        editor.cursor.moveCursorUp();
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        editor.cursor.moveCursorDown();
        return true;

      case KeyEvent.KEYCODE_DEL:
        if (editor.selection.hasSelection) {
          logKeyEvent("selection.del", keyCode, event, "");
          editor.selection.replaceSelectionWithText("");
        } else {
          editor.editOperators.deleteCharAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (editor.selection.hasSelection) {
          logKeyEvent("selection.forwardDel", keyCode, event, "");
          editor.selection.replaceSelectionWithText("");
        } else {
          editor.editOperators.deleteForwardAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (editor.selection.hasSelection) {
          logKeyEvent("selection.enter", keyCode, event, "\\n");
          editor.selection.replaceSelectionWithText("\n");
        } else {
          editor.autoBracketNewline.insertNewlineAtCursor();
        }
        return true;
    }
    String text = getPrintingText(event);
    if (text != null) {
      editor.editOperators.insertTextAtCursor(text);
      editor.charAnimation.startCharAnimationFromText(text);
      return true;
    }
    return false;
  }

  private String getPrintingText(KeyEvent event) {
    int uc = event.getUnicodeChar();
    if (uc != 0 && Character.isValidCodePoint(uc)) {
      return new String(Character.toChars(uc));
    }
    // Some non-ASCII input arrives via getCharacters() while getUnicodeChar() is 0.
    String chars = event.getCharacters();
    return (chars == null || chars.isEmpty()) ? null : chars;
  }

  private void logKeyEvent(String operation, int keyCode, KeyEvent event, String text) {
    if ((!DEBUG_KEY_SELECTION_LOGS && !SodiumEditor.DEBUG_LOGS) || keyLogCount >= MAX_KEY_LOGS) return;
    keyLogCount++;
    Log.d(
        TAG,
        "[SodiumEditor] operation="
            + operation
            + " count="
            + keyLogCount
            + " keyCode="
            + keyCode
            + " action="
            + (event == null ? -1 : event.getAction())
            + " unicode="
            + (event == null ? 0 : event.getUnicodeChar())
            + " text="
            + safeTextForLog(text)
            + " selection="
            + editor.selection.selStartLine
            + ":"
            + editor.selection.selStartChar
            + ".."
            + editor.selection.selEndLine
            + ":"
            + editor.selection.selEndChar
            + " hasSelection="
            + editor.selection.hasSelection
            + " stateHasSelection="
            + editor.selection.state.hasSelection
            + " cursor="
            + editor.cursor.cursorLine
            + ":"
            + editor.cursor.cursorChar
            + " thread="
            + Thread.currentThread().getName());
  }

  private String safeTextForLog(String text) {
    if (text == null) return "<null>";
    String escaped = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    int max = 120;
    if (escaped.length() > max) return escaped.substring(0, max) + "...(len=" + text.length() + ")";
    return escaped + "(len=" + text.length() + ")";
  }
}
