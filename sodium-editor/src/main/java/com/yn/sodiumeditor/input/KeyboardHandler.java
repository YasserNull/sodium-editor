package com.yn.sodiumeditor.input;

import android.view.KeyEvent;
import com.yn.sodiumeditor.*;

public final class KeyboardHandler {
  private final SodiumEditor view;
  private final EditorOperations editorOps;

  public KeyboardHandler(SodiumEditor view, EditorOperations editorOps) {
    this.view = view;
    this.editorOps = editorOps;
  }

  public boolean handleKeyDown(int keyCode, KeyEvent event) {
    if (view.editorConfig.behaviorConfig.isDisabled) return true;
    if (view.editorConfig.behaviorConfig.isReadOnly) {
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
        view.editorTextInserter.insertTextAtCursor(s);
        view.charAnimator.startCharAnimationFromText(s);
      } else {
        view.editorTextInserter.insertTextAtCursor("");
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
        if (view.selectionState.hasSelection()) {
          
          view.editorTextInserter.insertTextAtCursor("");
        } else {
          editorOps.deleteCharAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (view.selectionState.hasSelection()) {
          
          view.editorTextInserter.insertTextAtCursor("");
        } else {
          editorOps.deleteForwardAtCursor();
        }
        return true;

      case KeyEvent.KEYCODE_ENTER:
        if (view.selectionState.hasSelection()) {
          
          view.editorTextInserter.insertTextAtCursor("\n");
        } else {
          editorOps.insertNewlineAtCursor();
        }
        return true;
    }
    return view.superOnKeyDown(keyCode, event);
  }

  public boolean handleKeyUp(int keyCode, KeyEvent event) {
    return view.superOnKeyDown(keyCode, event);
  }
}
