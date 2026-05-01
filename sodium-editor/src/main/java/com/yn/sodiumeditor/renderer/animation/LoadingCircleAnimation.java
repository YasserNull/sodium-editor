package com.yn.sodiumeditor.renderer.animation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the rendering and animation logic for the loading circle.
 */
public class LoadingCircleAnimation {
    private final SodiumEditor editor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF rect = new RectF();

    public boolean showLoadingCircle = false;
    public float radius = 40f;
    public int color = 0xFF3F51B5;
    public float strokeWidth = 8f;
    public float rotation = 0f;

    private boolean isAnimating = false;
    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAnimating) return;
            rotation = (rotation + 8f) % 360f;
            editor.invalidate();
            mainHandler.postDelayed(this, 16);
        }
    };

    public boolean showLoadingOnFileOpen = true;
    public boolean isInitialFileOpenLoading = false;
    public int initialFileOpenToken = 0;
    public int maxWidthRecalcToken = 0;
    public static final int LARGE_EDIT_LINES = 8000;
    public final AtomicInteger largeEditUiToken = new AtomicInteger(0);

    private final Runnable largeEditUiWatchdog = () -> endLargeEditUi(false);

    public LoadingCircleAnimation(SodiumEditor editor) {
        FunctionLog.f("LoadingCircleAnimation", "LoadingCircleAnimation", editor);
        this.editor = editor;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void startRotation() {
        FunctionLog.f("LoadingCircleAnimation", "startRotation");
        if (isAnimating) return;
        isAnimating = true;
        mainHandler.post(animationRunnable);
    }

    public void stopRotation() {
        FunctionLog.f("LoadingCircleAnimation", "stopRotation");
        isAnimating = false;
        mainHandler.removeCallbacks(animationRunnable);
    }

    public void draw(Canvas canvas) {
        FunctionLog.f("LoadingCircleAnimation", "draw", canvas);
        if (!showLoadingCircle) return;
        rect.set(editor.getWidth() / 2f - radius, editor.getHeight() / 2f - radius, editor.getWidth() / 2f + radius, editor.getHeight() / 2f + radius);
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawArc(rect, rotation, 270f, false, paint);
    }

    public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
        FunctionLog.f("LoadingCircleAnimation", "beginLargeEditUiIfNeeded", enable, sL, eL, isSelectAllLike);
        if (!enable) return;
        if (eL - sL > LARGE_EDIT_LINES || isSelectAllLike) {
            showLoadingCircle = true;
            startRotation();
        }
    }

    public void endLargeEditUi(boolean invalidate) {
        FunctionLog.f("LoadingCircleAnimation", "endLargeEditUi", invalidate);
        largeEditUiToken.incrementAndGet();
        showLoadingCircle = false;
        stopRotation();
        if (invalidate) editor.invalidate();
    }

    public void cancel() {
        FunctionLog.f("LoadingCircleAnimation", "cancel");
        stopRotation();
        editor.caret.mainHandler.removeCallbacks(largeEditUiWatchdog);
    }

    public boolean isAnimating() { 
        FunctionLog.f("LoadingCircleAnimation", "isAnimating");
        return isAnimating; 
    }
}
