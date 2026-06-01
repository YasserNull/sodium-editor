package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import com.yn.sodiumeditor.SodiumEditor;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class ImeKeyboardSuggestionRegressionTest {

  private SodiumEditor editor;

  @Before
  public void setup() {
    try {
      editor = new SodiumEditor(RuntimeEnvironment.getApplication(), null);
    } catch (UnsatisfiedLinkError e) {
      Assume.assumeNoException(e);
      return;
    }
    editor.windowRender.windowStartLine = 0;
    editor.windowRender.linesWindow.clear();
    editor.windowRender.linesWindow.add("");
    editor.cursor.setCursorPosition(0, 0);
  }

  @Test
  public void committingKeyboardSuggestionSuffix_shouldAppendToComposingWord() {
    editor.ime.onSetComposingText("perf", 1);

    editor.ime.onCommitText("ect", 1);

    assertEquals("perfect", editor.windowRender.getLineTextForRender(0));
    assertEquals(0, editor.cursor.cursorLine);
    assertEquals(7, editor.cursor.cursorChar);
    assertFalse(editor.ime.hasComposing());
  }

  @Test
  public void committingKeyboardSuggestionSuffixWithoutComposing_shouldNotReplaceTypedPrefix() {
    editor.ime.onCommitText("p", 1);
    editor.ime.onCommitText("e", 1);

    editor.ime.onCommitText("ople ", 1);

    assertEquals("people ", editor.windowRender.getLineTextForRender(0));
    assertEquals(0, editor.cursor.cursorLine);
    assertEquals(7, editor.cursor.cursorChar);
    assertFalse(editor.ime.hasComposing());
  }

  @Test
  public void keyboardSuggestions_shouldNotForcePersonalizedLearningOff() {
    EditorInfo info = new EditorInfo();

    editor.ime.onCreateInputConnection(info);

    assertEquals(0, info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    assertEquals(0, info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
  }

  @Test
  public void disablingKeyboardSuggestions_shouldOnlyDisableSuggestions() {
    EditorInfo info = new EditorInfo();

    editor.setKeyboardSuggestionsEnabled(false);
    editor.ime.onCreateInputConnection(info);

    assertEquals(0, info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    assertEquals(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
  }
}
