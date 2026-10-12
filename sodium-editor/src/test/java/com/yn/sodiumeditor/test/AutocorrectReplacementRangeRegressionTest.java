package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.utils.AutocorrectRangeUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Guards Gboard autocorrect: a correction must replace only the misspelled word, never the
 * preceding word on the same line.
 */
public class AutocorrectReplacementRangeRegressionTest {

  @Test
  public void autocorrect_shouldTargetOnlyMisspelledWord_notPreviousWord() {
    int[] range = AutocorrectRangeUtil.findRangeForText("open codt", 9, "codt");
    assertArrayEquals(
        "BUG: autocorrect range must be the misspelled word only, not the whole line.", new int[] {5, 9}, range);
  }

  @Test
  public void autocorrect_shouldNotWidenToStartOfLine_whenCursorAtColumnZero() {
    int[] range = AutocorrectRangeUtil.findRangeForText("open codt", 0, "codt");
    assertArrayEquals(
        "BUG: a stale/zero cursor must not make autocorrect wipe the previous word.", new int[] {5, 9}, range);
  }

  @Test
  public void autocorrect_afterSpace_shouldStillTargetTheWord() {
    int[] range = AutocorrectRangeUtil.findRangeForText("open codt ", 10, "codt");
    assertArrayEquals(new int[] {5, 9}, range);
  }

  @Test
  public void autocorrect_returnsNullWhenReplacedTextIsAbsent() {
    assertNull(AutocorrectRangeUtil.findRangeForText("open codt", 9, "xyz"));
  }

  @Test
  public void gboardAutocorrect_cursorOnTrailingSpace_mustNotReplacePreviousWord() {
    // After deleteSurroundingText removes the typo, Gboard sends commitCorrection(old="", new=word)
    // with the cursor sitting on the space left behind; the previous word must be left alone.
    assertEquals(-1, AutocorrectRangeUtil.replaceableWordPos("open ", 5));
    assertEquals(-1, AutocorrectRangeUtil.replaceableWordPos("open ", 4));
    assertEquals(-1, AutocorrectRangeUtil.replaceableWordPos("open codt ", 10));
  }

  @Test
  public void replaceableWordPos_targetsTheWordUnderTheCursor() {
    assertEquals(8, AutocorrectRangeUtil.replaceableWordPos("open codt", 9));
    assertEquals(3, AutocorrectRangeUtil.replaceableWordPos("open", 4));
    assertEquals(5, AutocorrectRangeUtil.replaceableWordPos("open codt", 5));
  }

  @Test
  public void commitCorrection_shouldPassImeOldTextSoRangeIsExact() throws Exception {
    String connection =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/SodiumInputConnection.java");
    assertTrue(
        "BUG: commitCorrection must forward CorrectionInfo.getOldText() so the replaced range is exact.",
        connection.contains("ime.onCommitCorrection(correctionInfo.getOldText()"));
  }

  @Test
  public void ime_shouldReplaceTheImeReportedRangeForCorrections() throws Exception {
    String ime = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");
    assertTrue(
        "BUG: Ime must handle commitCorrection with the IME-reported old/new text.",
        ime.contains("public boolean onCommitCorrection(CharSequence oldText, CharSequence newText)"));
  }

  @Test
  public void commitCorrection_mustNotReinsertTextDeliveredByTheFollowingCommitText() throws Exception {
    String ime = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");
    assertTrue(
        "BUG: commitCorrection must not re-insert text; Gboard delivers the corrected word via a"
            + " following commitText, so applying it here duplicates it.",
        !ime.contains("return onCommitText(newText, 1);"));
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
