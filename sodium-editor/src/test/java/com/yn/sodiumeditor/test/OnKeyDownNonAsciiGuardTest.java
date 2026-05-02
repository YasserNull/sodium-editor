package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Detects missing non-ASCII support in the KeyEvent path.
 *
 * Some keyboards (or injected key events) deliver non-ASCII characters via KeyEvent.getCharacters()
 * while getUnicodeChar() returns 0. Without handling getCharacters(), non-ASCII input can be lost.
 *
 * This is a static test so it can run in plain JVM unit tests on Termux.
 */
public class OnKeyDownNonAsciiGuardTest {

    @Test
    public void onKeyDown_shouldHandleKeyEventCharactersFallback() throws Exception {
        Path path = findPath("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnKeyDown.java");
        String src = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        int at = src.indexOf("getUnicodeChar");
        assertTrue("Expected OnKeyDown to use getUnicodeChar()", at >= 0);

        // Expect getCharacters() fallback near getUnicodeChar usage.
        String around = src.substring(Math.max(0, at - 900), Math.min(src.length(), at + 900));
        boolean hasCharactersFallback = around.contains("getCharacters()");
        assertTrue(
                "BUG: OnKeyDown lacks KeyEvent.getCharacters() fallback; non-ASCII input may be dropped.",
                hasCharactersFallback);
    }

    @Test
    public void onKeyDown_shouldNotTruncateSupplementaryUnicodeCodePoints() throws Exception {
        Path path = findPath("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnKeyDown.java");
        String src = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(
                "BUG: casting getUnicodeChar() to char truncates emoji and other supplementary code points.",
                src.contains("Character.toChars(uc)"));
    }

    private static Path findPath(String rel) {
        Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path candidate = cwd.resolve(rel);
            if (Files.exists(candidate)) return candidate;
            Path parent = cwd.getParent();
            if (parent == null) break;
            cwd = parent;
        }
        Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException("Could not locate file: " + rel);
    }
}
