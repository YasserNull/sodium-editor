package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for stale selection handle animation state after scroll.
 */
public class SelectionHandleScrollSnapGuardTest {

    @Test
    public void selectionHandleScrollBypass_shouldSnapAnimationStateToCurrentTarget() throws Exception {
        String handlesSrc =
                readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionHandles.java");
        int at = handlesSrc.indexOf("boolean scrollChanged");
        assertTrue("Expected scrollChanged logic in SelectionHandles.", at >= 0);
        String around = handlesSrc.substring(at, Math.min(handlesSrc.length(), at + 1400));

        assertTrue(
                "BUG: scroll-triggered animation bypass must snap the left handle animation state to the current target.",
                around.contains("boolean bypassLeftAnimation = scrollChanged;")
                        && around.contains("animation.snapHandlePosition(true, startX, leftTargetY);"));

        assertTrue(
                "BUG: scroll-triggered animation bypass must snap the right handle animation state to the current target.",
                around.contains("boolean bypassRightAnimation = scrollChanged;")
                        && around.contains("animation.snapHandlePosition(false, endX, rightTargetY);"));

        String animSrc =
                readSource(
                        "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/SelectionHandlesAnimation.java");
        int snapAt = animSrc.indexOf("public void snapHandlePosition(boolean isLeft, float targetX, float targetY)");
        assertTrue("Expected snapHandlePosition in SelectionHandlesAnimation.", snapAt >= 0);
        String snapAround = animSrc.substring(snapAt, Math.min(animSrc.length(), snapAt + 1800));

        assertTrue(
                "BUG: snapHandlePosition must overwrite animated coordinates and targets for the left handle.",
                snapAround.contains("leftStartX = targetX;")
                        && snapAround.contains("leftTargetX = targetX;")
                        && snapAround.contains("animLeftX = targetX;")
                        && snapAround.contains("animLeftY = targetY;"));

        assertTrue(
                "BUG: snapHandlePosition must overwrite animated coordinates and targets for the right handle.",
                snapAround.contains("rightStartX = targetX;")
                        && snapAround.contains("rightTargetX = targetX;")
                        && snapAround.contains("animRightX = targetX;")
                        && snapAround.contains("animRightY = targetY;"));
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

        Path fallback =
                new File(".")
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .resolve(relativePath.replace("sodium-editor/", ""));
        if (Files.exists(fallback)) {
            return new String(Files.readAllBytes(fallback), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Could not locate source file: " + relativePath);
    }
}
