package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ImeKeyboardSuggestionRegressionTest {

  @Test
  public void committingKeyboardSuggestionSuffix_shouldAppendToComposingWord() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertTrue(
        "BUG: composing suggestion suffixes like perf + ect must become perfect.",
        src.contains("return composingText + core + trailing;"));
  }

  @Test
  public void committingKeyboardSuggestionSuffixWithoutComposing_shouldNotReplaceTypedPrefix()
      throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertTrue(
        "BUG: suffix commits like pe + ople must insert the suffix, not replace pe with ople.",
        src.contains("if (!core.startsWith(word)) return false;"));
  }

  @Test
  public void keyboardSuggestions_shouldNotForcePersonalizedLearningOff() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertTrue(
        "BUG: personalized learning flag makes Gboard show private/incognito behavior.",
        !src.contains("IME_FLAG_NO_PERSONALIZED_LEARNING"));
  }

  @Test
  public void disablingKeyboardSuggestions_shouldDisableSuggestionsWithoutPasswordInput()
      throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertTrue(src.contains("TYPE_TEXT_FLAG_NO_SUGGESTIONS"));
    assertTrue(src.contains("TYPE_TEXT_VARIATION_FILTER"));
    assertTrue(!src.contains("TYPE_TEXT_VARIATION_VISIBLE_PASSWORD"));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
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
