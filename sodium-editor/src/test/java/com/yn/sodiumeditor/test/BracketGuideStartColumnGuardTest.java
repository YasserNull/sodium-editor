package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards bracket guide start columns for non-leading bracket pairs. */
public class BracketGuideStartColumnGuardTest {

    @Test
    public void mainScanner_shouldAnchorAllOpeningBracketGuidesToFirstNonSpaceColumn() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/BracketGuideScanner.java");
        int methodStart = src.indexOf("public List<BracketGuideToken> updateBracketGuideStateForLine");
        int methodEnd = src.indexOf("/**\n   * Scans a line for spans", methodStart);
        assertTrue("Expected updateBracketGuideStateForLine in BracketGuideScanner.", methodStart >= 0);
        assertTrue("Expected scanLineForSpans to follow updateBracketGuideStateForLine.", methodEnd > methodStart);

        String method = src.substring(methodStart, methodEnd);
        assertTrue(
                "BUG: main/fallback bracket guide cache must anchor every opening bracket type "
                        + "({, (, [) to the first non-space column, not to the bracket column.",
                method.contains("getOpeningBracketGuideColumn(i, firstNonSpace)"));
    }

    @Test
    public void spanScanner_shouldUseSameOpeningBracketGuideColumnRuleAsMainScanner() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/BracketGuideScanner.java");
        int methodStart = src.indexOf("public void scanLineForSpans");
        int methodEnd = src.indexOf("/**\n   * Gets guide tokens from stack", methodStart);
        assertTrue("Expected scanLineForSpans in BracketGuideScanner.", methodStart >= 0);
        assertTrue("Expected getGuideTokensFromStack to follow scanLineForSpans.", methodEnd > methodStart);

        String method = src.substring(methodStart, methodEnd);
        assertTrue(
                "BUG: span-cache bracket guide columns must use the same first-non-space rule "
                        + "as main/fallback rendering.",
                method.contains("getOpeningBracketGuideColumn(i, firstNonSpace)"));
    }

    @Test
    public void openingBracketGuideColumnRule_shouldPreferFirstNonSpaceOverBracketIndex() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/BracketGuideScanner.java");
        int methodStart = src.indexOf("private int getOpeningBracketGuideColumn(int bracketIndex, int firstNonSpace)");
        int methodEnd = src.indexOf("/**\n   * Gets guide tokens from stack", methodStart);
        assertTrue("Expected getOpeningBracketGuideColumn helper in BracketGuideScanner.", methodStart >= 0);
        assertTrue("Expected getGuideTokensFromStack to follow getOpeningBracketGuideColumn.", methodEnd > methodStart);

        String method = src.substring(methodStart, methodEnd);
        assertTrue(
                "BUG: bracket guides for lines like `hello(() {` must start at `h`, "
                        + "not under the later bracket character.",
                method.contains("return (firstNonSpace >= 0) ? firstNonSpace : bracketIndex;"));
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
