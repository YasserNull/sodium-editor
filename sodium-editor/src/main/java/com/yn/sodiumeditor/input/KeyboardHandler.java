package com.yn.sodiumeditor.input;

import android.view.KeyEvent;
import com.yn.sodiumeditor.*;

public final class KeyboardHandler {
  private final SodiumEditorView view;
  private final EditorOperations editorOps;

  public KeyboardHandler(SodiumEditorView view, EditorOperations editorOps) {
    this.view = view;
    this.editorOps = editorOps;
  }

  public boolean handleKeyDown(int keyCode, KeyEvent event) {
    if (view.isDisabled) return true;
    if (view.isReadOnly) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          view.cursorNavigation.moveCursorLeft();
          return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          view.cursorNavigation.moveCursorRight();
          return true;
        case KeyEvent.KEYCODE_DPAD_UP:
          view.cursorNavigation.moveCursorUp();
          return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          view.cursorNavigation.moveCursorDown();
          return true;
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_FORWARD_DEL:
        case KeyEvent.KEYCODE_ENTER:
          return true;
      }
      if (event.isPrintingKey()) return true;
    }

    if (view.selectionState.hasSelection() && event.isPrintingKey()) {
      int uc = event.getUnicodeChar();
      if (uc != 0) {
        String s = String.valueOf((char) uc);
        view.replaceSelectionWithText(s);
        view.charAnimationManager.startCharAnimationFromText(s);
      } else {
        view.replaceSelectionWithText("");
      }
      return true;
    }

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        view.cursorNavigation.moveCursorLeft();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        view.cursorNavigation.moveCursorRight();
        return true;
      case KeyEvent.KEYCODE_DPAD_UP:
        view.cursorNavigation.moveCursorUp();
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        view.cursorNavigation.moveCursorDown();
        return true;

      case KeyEvent.KEYCODE_DEL:
        if (view.selectionState.hasSelection()) view.replaceSelectionWithText("");
        else editorOps.deleteCharAtCursor();
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (view.selectionState.hasSelection()) view.replaceSelectionWithText("");
        else editorOps.deleteForwardAtCursor();
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (view.selectionState.hasSelection()) view.replaceSelectionWithText("\n");
        else editorOps.insertNewlineAtCursor();
        return true;
    }
    return view.superOnKeyDown(keyCode, event);
  }

  public boolean handleKeyUp(int keyCode, KeyEvent event) {
    return view.superOnKeyDown(keyCode, event);
  }
}
