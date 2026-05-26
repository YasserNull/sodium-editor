package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
public class SelectionKeyboardDeleteRegressionTest {

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
        editor.windowRender.linesWindow.add("// hello world");
        editor.cursor.setCursorPosition(0, 14);
    }

    @Test
    public void imeBackspaceWithWorldSelectedDeletesOnlyWorld() {
        editor.cursor.setCursorPosition(0, 14);
        editor.selection.setSelection(0, 9, 0, 14);

        editor.ime.onDeleteSurroundingText(1, 0);

        assertEquals("// hello ", editor.windowRender.getLineTextForRender(0));
        assertEquals(0, editor.cursor.cursorLine);
        assertEquals(9, editor.cursor.cursorChar);
        assertFalse(editor.selection.hasSelection);
        assertFalse(editor.selection.state.hasSelection);
    }

    @Test
    public void imeBackspaceWithHelloAndSpaceSelectedLeavesWorldOnly() {
        editor.cursor.setCursorPosition(0, 9);
        editor.selection.setSelection(0, 3, 0, 9);

        editor.ime.onDeleteSurroundingText(1, 0);

        assertEquals("// world", editor.windowRender.getLineTextForRender(0));
        assertEquals(0, editor.cursor.cursorLine);
        assertEquals(3, editor.cursor.cursorChar);
        assertFalse(editor.selection.hasSelection);
        assertFalse(editor.selection.state.hasSelection);
    }
}
