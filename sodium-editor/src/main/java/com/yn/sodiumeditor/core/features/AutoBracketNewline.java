package com.yn.sodiumeditor.core.features;

import com.yn.sodiumeditor.SodiumEditor;

/** Compatibility facade for automatic bracket newline behavior. */
public class AutoBracketNewline extends AutoBracketNewlineIndent {
  private final SodiumEditor editor;

  public boolean isAutoBracketNewlineEnabled = true;

  public AutoBracketNewline(SodiumEditor editor) {
    super(editor);
    this.editor = editor;
  }

  public void setAutoBracketNewlineEnabled(boolean enabled) {
    isAutoBracketNewlineEnabled = enabled;
    setAutoBracketNewlineIndentEnabled(enabled);
  }

  @Override
  public void setAutoBracketNewlineIndentEnabled(boolean enabled) {
    super.setAutoBracketNewlineIndentEnabled(enabled);
    isAutoBracketNewlineEnabled = enabled;
  }

  public void setAutoIndentAfterClosingBracketEnabled(boolean enabled) {
    editor.autoIndentAfterClosingBracket.setAutoIndentAfterClosingBracketEnabled(enabled);
  }
}
