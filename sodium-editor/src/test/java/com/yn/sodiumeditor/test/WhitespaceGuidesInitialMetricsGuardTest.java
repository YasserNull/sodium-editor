package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards whitespace guide dot metrics during initial editor construction. */
public class WhitespaceGuidesInitialMetricsGuardTest {

    @Test
    public void constructor_shouldRefreshWhitespaceMetricsAfterInitialTextPaintSetup() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/SodiumEditor.java");
        int textSize = src.indexOf("textRender.paint.setTextSize(36)");
        int lineHeight = src.indexOf("textRender.lineHeight = textRender.paint.getFontSpacing()", textSize);
        int updateMetrics = src.indexOf("whitespaceGuides.updateMetrics()", lineHeight);

        assertTrue("Expected SodiumEditor constructor to set initial text size.", textSize >= 0);
        assertTrue("Expected SodiumEditor constructor to compute line height after text size.", lineHeight > textSize);
        assertTrue(
                "BUG: whitespace guide dot metrics must be refreshed after initial text paint setup, not only after zoom.",
                updateMetrics > lineHeight);
    }

    private static String readSource(String relativePath) throws Exception {
        Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path candidate = cwd.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            Path parent = cwd.getParent();
            if (parent == null) break;
            cwd = parent;
        }

        throw new IllegalStateException("Could not locate source file: " + relativePath);
    }
}
