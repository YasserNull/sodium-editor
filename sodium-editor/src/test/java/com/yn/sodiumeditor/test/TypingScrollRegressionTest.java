package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import com.yn.sodiumeditor.SodiumEditor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Reproduces the "typing does not move scroll" bug. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class TypingScrollRegressionTest {

  private SodiumEditor editor;

  @Before
  public void setup() {
    try {
      editor = new SodiumEditor(RuntimeEnvironment.getApplication(), null);
    } catch (UnsatisfiedLinkError e) {
      assumeNoException(e);
      return;
    }
    editor.layout(0, 0, 480, 800);
    editor.windowRender.windowStartLine = 0;
    editor.windowRender.linesWindow.clear();
    editor.windowRender.linesWindow.add("");
    editor.wordWrap.setWordWrapEnabled(false);
    editor.cursorAnimation.setCursorAnimationEnabled(false);
  }

  @Test
  public void typingLongLine_shouldChangeHorizontalScrollInLtr() {
    editor.textRender.isRtl = false;
    editor.layout.setLayoutDirection(false);

    float beforeX = editor.scroll.getEffectiveScrollX();
    float beforeY = editor.scroll.scrollY;

    editor.editOperators.insertTextAtCursor(repeat('x', 200));

    float afterX = editor.scroll.getEffectiveScrollX();
    float afterY = editor.scroll.scrollY;

    assertTrue(
        "BUG: typing a long LTR line should move horizontal scroll from its initial value."
            + " beforeX="
            + beforeX
            + " afterX="
            + afterX
            + " beforeY="
            + beforeY
            + " afterY="
            + afterY,
        Math.abs(afterX - beforeX) > 0.5f);
    assertNotEquals(
        "Sanity: cursor should have advanced after typing.", 0, editor.cursor.cursorChar);
  }

  @Test
  public void typingLongLine_shouldChangeHorizontalScrollInRtl() {
    editor.textRender.isRtl = true;
    editor.layout.setLayoutDirection(true);

    float beforeX = editor.scroll.getEffectiveScrollX();
    float beforeY = editor.scroll.scrollY;

    editor.editOperators.insertTextAtCursor(repeat('س', 200));

    float afterX = editor.scroll.getEffectiveScrollX();
    float afterY = editor.scroll.scrollY;

    assertTrue(
        "BUG: typing a long RTL line should move effective horizontal scroll from its initial"
            + " value. beforeX="
            + beforeX
            + " afterX="
            + afterX
            + " beforeY="
            + beforeY
            + " afterY="
            + afterY,
        Math.abs(afterX - beforeX) > 0.5f);
    assertNotEquals(
        "Sanity: cursor should have advanced after typing.", 0, editor.cursor.cursorChar);
  }

  private static String repeat(char ch, int count) {
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) sb.append(ch);
    return sb.toString();
  }
}
