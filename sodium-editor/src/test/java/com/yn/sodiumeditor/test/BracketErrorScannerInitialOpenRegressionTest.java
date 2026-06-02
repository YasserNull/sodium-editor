package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
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

/** Guards bracket error underlines when a file window appears after the first scan. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class BracketErrorScannerInitialOpenRegressionTest {

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
        editor.textRender.lineHeight = 40f;
        editor.windowRender.windowStartLine = 0;
        editor.windowRender.linesWindow.clear();
        editor.fileIO.isEof = false;
    }

    @Test
    public void scanForErrors_shouldRetrySameEditVersionAfterInitialWindowLoads() {
        assertTrue("Sanity: empty initial window should report no loaded line count.", editor.view.getLinesCount() <= 0);

        editor.bracketErrorScanner.scanForErrors();

        editor.windowRender.linesWindow.add("function broken() {");
        editor.fileIO.isEof = true;

        assertTrue(editor.errorUnderline.isErrorUnderlineEnabled());
        assertTrue(editor.bracketErrorScanner.unclosedBracketUnderlineEnabled);
        assertTrue("Sanity: loaded window should report one visible line.", editor.view.getLinesCount() == 1);
        assertTrue(editor.windowRender.getLineTextForRender(0).contains("{"));
        assertTrue("Sanity: scanner should start at the first loaded line.", editor.viewRender.drawBaseLine == 0);

        editor.bracketErrorScanner.scanForErrors();

        assertFalse(
                "BUG: initial file-open scan must not cache the edit version before file-backed lines are loaded.",
                editor.errorUnderline.errorUnderlineMap.isEmpty());
        assertTrue(editor.errorUnderline.errorUnderlineMap.containsKey(0));
    }
}
