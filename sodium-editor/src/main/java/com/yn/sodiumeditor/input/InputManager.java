package com.yn.sodiumeditor.input;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.yn.sodiumeditor.*;

public final class InputManager {
  private final SodiumEditorView view;
  private final TouchHandler touchHandler;
  private final KeyboardHandler keyboardHandler;
  private final EditorOperations editorOperations;

  public InputManager(SodiumEditorView view, Context context) {
    this.view = view;
    this.editorOperations = new EditorOperations(view);
    this.touchHandler = new TouchHandler(view, context);
    this.keyboardHandler = new KeyboardHandler(view, editorOperations);
  }

  public boolean handleTouchEvent(MotionEvent event) {
    return touchHandler.handleTouchEvent(event);
  }

  public boolean handleKeyDown(int keyCode, KeyEvent event) {
    return keyboardHandler.handleKeyDown(keyCode, event);
  }

  public boolean handleKeyUp(int keyCode, KeyEvent event) {
    return keyboardHandler.handleKeyUp(keyCode, event);
  }

  public void insertCharAtCursor(char c) {
    editorOperations.insertCharAtCursor(c);
  }

  public void insertNewlineAtCursor() {
    editorOperations.insertNewlineAtCursor();
  }

  public void deleteCharAtCursor() {
    editorOperations.deleteCharAtCursor();
  }

  public void deleteForwardAtCursor() {
    editorOperations.deleteForwardAtCursor();
  }

  public void replaceSelectionWithText(String insertText) {
    editorOperations.replaceSelectionWithText(insertText);
  }

  public void handleAutoPairing(String text) {
    editorOperations.handleAutoPairing(text);
  }
}
