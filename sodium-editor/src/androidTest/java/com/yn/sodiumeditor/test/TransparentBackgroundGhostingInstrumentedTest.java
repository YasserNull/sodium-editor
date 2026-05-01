package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransparentBackgroundGhostingInstrumentedTest {
    private SodiumEditor editor;
    private Context context;
    private File logFile;

    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        logFile = new File(context.getFilesDir(), "test-logs/transparent-bg-ghosting.log");
        File parent = logFile.getParentFile();
        if (parent != null) parent.mkdirs();
        writeUtf8(logFile, "");

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor = new SodiumEditor(context, null);
                            editor.layout(0, 0, 600, 300);
                            editor.fileIO.clearContent(); // no file IO, window-only
                            editor.lineNumber.setShowLineNumbers(false);
                            editor.currentLineHighlight.setHighlightCurrentLine(false);
                            editor.currentLineHighlight.setCurrentLineGutterHighlightEnabled(false);
                        });
        log("LOG_FILE=" + logFile.getAbsolutePath());
    }

    @Test
    public void transparentBackground_doesNotGhostDeletedTextPixels() throws Exception {
        final Bitmap bitmap = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        // 1) Draw with text present.
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor.cursor.setCursorPosition(0, 0);
                            editor.editOperators.insertTextAtCursor("BBB");
                            editor.onDraw.onDraw(canvas);
                        });

        int before = countNonTransparentPixels(bitmap, 0, 0, bitmap.getWidth(), (int) Math.ceil(editor.textRender.lineHeight));
        log("nonTransparent(before)=" + before);
        assertTrue("Expected some pixels for text render", before > 0);

        // 2) Delete text so the line is empty, redraw onto the SAME bitmap (simulates surface reuse).
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            editor.cursor.setCursorPosition(0, 3);
                            editor.editOperators.deleteCharAtCursor();
                            editor.editOperators.deleteCharAtCursor();
                            editor.editOperators.deleteCharAtCursor();
                            editor.onDraw.onDraw(canvas);
                        });

        int after = countNonTransparentPixels(bitmap, 0, 0, bitmap.getWidth(), (int) Math.ceil(editor.textRender.lineHeight));
        log("nonTransparent(after)=" + after);

        // With transparent background, if the canvas isn't cleared, the old glyph pixels remain.
        // Expect a large drop after deletion.
        assertTrue("Expected deleted text pixels to be cleared (no ghosting)", after < (before / 4));
    }

    private static int countNonTransparentPixels(Bitmap bmp, int x, int y, int w, int h) {
        int maxX = Math.min(bmp.getWidth(), x + w);
        int maxY = Math.min(bmp.getHeight(), y + h);
        int count = 0;
        for (int yy = y; yy < maxY; yy++) {
            for (int xx = x; xx < maxX; xx++) {
                int c = bmp.getPixel(xx, yy);
                if (Color.alpha(c) != 0) count++;
            }
        }
        return count;
    }

    private void log(String msg) throws Exception {
        appendUtf8(logFile, msg + "\n");
    }

    private static void writeUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    private static void appendUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }
}

