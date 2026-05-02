package com.yn.sodiumeditor.input.events;

import android.view.KeyEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Handles key down events for the SodiumEditor.
 */
public class OnKeyDown {

  private final SodiumEditor editor;

  public OnKeyDown(SodiumEditor editor) {
    FunctionLog.f("OnKeyDown", "OnKeyDown", editor);
    this.editor = editor;
  }

  /**
   * Handles key down events.
   * @param keyCode The key code of the pressed key
   * @param event The KeyEvent object
   * @return true if the event was handled, false otherwise
   */
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    FunctionLog.f("OnKeyDown", "onKeyDown", keyCode, event);
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
    FunctionLog.f("OnKeyDown", "handleReadOnlyKey", keyCode, event);
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
      return true;
    }
    return false;
  }

  /**
   * Handles printing key when selection is active.
   */
  private boolean handleSelectionWithPrintingKey(KeyEvent event) {
    FunctionLog.f("OnKeyDown", "handleSelectionWithPrintingKey", event);
    String text = getPrintingText(event);
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
    FunctionLog.f("OnKeyDown", "handleNormalKey", keyCode, event);
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
          editor.selection.replaceSelectionWithText("");
        } else {
          editor.editOperators.deleteCharAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (editor.selection.hasSelection) {
          editor.selection.replaceSelectionWithText("");
        } else {
          editor.editOperators.deleteForwardAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (editor.selection.hasSelection) {
          editor.selection.replaceSelectionWithText("\n");
        } else {
          editor.autoBracketNewline.insertNewlineAtCursor();
        }
        return true;
    }
    String text = getPrintingText(event);
    if (text != null) {
      editor.editOperators.insertTextAtCursor(text);
      return true;
    }
    return false;
  }

  private String getPrintingText(KeyEvent event) {
    FunctionLog.f("OnKeyDown", "getPrintingText", event);
    int uc = event.getUnicodeChar();
    if (uc != 0 && Character.isValidCodePoint(uc)) {
      return new String(Character.toChars(uc));
    }
    // Some non-ASCII input arrives via getCharacters() while getUnicodeChar() is 0.
    String chars = event.getCharacters();
    return (chars == null || chars.isEmpty()) ? null : chars;
  }
}
