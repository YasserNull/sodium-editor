package com.yn.sodiumeditor.core.view.events;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;

public class onDraw {
    private static final String TAG = "SodiumCharAnim";
    private static final int MAX_DRAW_FRAME_LOGS = 220;
    private final SodiumEditor editor;
    private int drawFrameLogCount = 0;

    public onDraw(SodiumEditor editor) {
        this.editor = editor;
    }

    static boolean shouldClearBeforeDraw(boolean hasEditorBackgroundColor, Object editorBackgroundBitmap) {
        return !hasEditorBackgroundColor && editorBackgroundBitmap == null;
    }

    public void onDraw(Canvas canvas) {
        if (SodiumEditor.DEBUG_LOGS
                && editor.charAnimation.charAnimLine >= 0
                && editor.charAnimation.charAnimEndChar > editor.charAnimation.charAnimStartChar
                && editor.charAnimation.charAnimAlpha < 1f
                && drawFrameLogCount < MAX_DRAW_FRAME_LOGS) {
            drawFrameLogCount++;
            Log.d(
                    TAG,
                    "ON_DRAW frame line="
                            + editor.charAnimation.charAnimLine
                            + " range="
                            + editor.charAnimation.charAnimStartChar
                            + ".."
                            + editor.charAnimation.charAnimEndChar
                            + " alpha="
                            + editor.charAnimation.charAnimAlpha
                            + " size="
                            + editor.getWidth()
                            + "x"
                            + editor.getHeight()
                            + " scroll="
                            + editor.scroll.getEffectiveScrollX()
                            + ","
                            + editor.scroll.scrollY);
        }
        // If the editor background is transparent, previously-rendered glyphs can "ghost"
        // when a line becomes empty (we skip drawing text for empty lines). CLEAR ensures
        // the current clip is wiped every frame before drawing.
        if (shouldClearBeforeDraw(editor.view.hasEditorBackgroundColor, editor.view.editorBackgroundBitmap)) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        editor.view.drawEditorBackground(canvas);
        if (editor.scroll.stretch.stretchOverscrollEnabled && (editor.scroll.stretch.stretchX != 0f || editor.scroll.stretch.stretchY != 0f)) {
            float sx = 1f + (editor.scroll.stretch.stretchX * 0.18f * editor.scroll.stretch.stretchOverscrollStrength);
            float sy = 1f + (editor.scroll.stretch.stretchY * 0.18f * editor.scroll.stretch.stretchOverscrollStrength);
            float pivotX = (editor.scroll.stretch.stretchDirX < 0) ? 0f : (editor.scroll.stretch.stretchDirX > 0 ? editor.getWidth() : editor.getWidth() * 0.5f);
            float pivotY = (editor.scroll.stretch.stretchDirY < 0) ? 0f : (editor.scroll.stretch.stretchDirY > 0 ? editor.getHeight() : editor.getHeight() * 0.5f);
            canvas.save();
            canvas.scale(sx, sy, pivotX, pivotY);
            editor.viewRender.drawContent(canvas);
            canvas.restore();
        } else {
            editor.viewRender.drawContent(canvas);
        }
        editor.scroll.drawStretch(canvas);
        editor.scroll.drawEdge(canvas);
        editor.scroll.drawScrollBar(canvas);
        editor.loadingCircle.drawLoadingCircle(canvas);
    }
}
