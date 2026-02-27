package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import com.yn.sodiumeditor.config.CursorConfig;
import com.yn.sodiumeditor.state.CursorState;

/**
 * Renderer class for cursor functionality.
 * Handles drawing the cursor caret on the canvas.
 */
public final class CursorRenderer {

    private final CursorConfig config;
    private final CursorState state;
    private final RenderCallback callback;

    public interface RenderCallback {
        float getCursorDrawX();
        float getCursorDrawY();
        boolean isCursorVisible();
        int getCaretColor();
        float getCursorWidth();
        float getLineHeight();
    }

    public CursorRenderer(CursorConfig config, CursorState state, RenderCallback callback) {
        this.config = config;
        this.state = state;
        this.callback = callback;
    }

    public void drawCaret(Canvas canvas, float cursorX, float cursorY) {
        callback.getCursorDrawX();
        callback.getCursorDrawY();
        
        if (callback.isCursorVisible()) {
            config.getCaretPaint().setColor(callback.getCaretColor());
            config.getCaretPaint().setStrokeWidth(callback.getCursorWidth());
            canvas.drawLine(cursorX, cursorY, cursorX, cursorY + callback.getLineHeight(), config.getCaretPaint());
        }
    }

    public void setCaretColor(int color) {
        config.getCaretPaint().setColor(color);
    }
}
