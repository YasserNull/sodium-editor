package com.yn.sodiumeditor.input.events;

import android.view.KeyEvent;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles key down events for the SodiumEditor.
 */
public class OnKeyDown {

  private final SodiumEditor editor;

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
    if (editor.isDisabled) return true;
    
    if (editor.isReadOnly) {
      return handleReadOnlyKey(keyCode, event);
    }

    if (editor.selection.hasSelection && event.isPrintingKey()) {
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
    if (event.isPrintingKey()) return true;
    return false;
  }

  /**
   * Handles printing key when selection is active.
   */
  private boolean handleSelectionWithPrintingKey(KeyEvent event) {
    int uc = event.getUnicodeChar();
    if (uc != 0) {
      String s = String.valueOf((char) uc);
      editor.selection.replaceSelectionWithText(s);
      editor.charAnimation.startCharAnimationFromText(s);
    } else {
      editor.selection.replaceSelectionWithText("");
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
          editor.insertNewlineAtCursor();
        }
        return true;
    }
    return false;
  }
}
