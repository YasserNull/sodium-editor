package com.yn.sodiumeditor.test;

import org.junit.Before;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Regression test for the bug where committing an emoji immediately after a word
 * would cause the word to be replaced (disappear).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class EmojiBugReproductionTest {

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
    }

    @Test
    public void testTextDisappearingAfterEmojis() {
        String initialText = "Hello";
        String emoji = "😄";
        
        // 1. Commit initial text
        editor.ime.onCommitText(initialText, 1);
        assertEquals("Initial text should be 'Hello'", initialText, editor.windowRender.getLineTextForRender(0));
        
        // 2. Commit emoji immediately after
        // Ensure no composing is active to allow tryReplaceWordFromImeCommit to be tested
        editor.ime.commitComposing(true); 
        editor.ime.onCommitText(emoji, 1);
        
        String result = editor.windowRender.getLineTextForRender(0);
        
        // The fix ensures emojis are not considered "words" for replacement in tryReplaceWordFromImeCommit
        assertEquals("Text 'Hello' should NOT be replaced by emoji", initialText + emoji, result);
    }
}
