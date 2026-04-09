package com.yn.sodiumeditor.core;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.LoadingCircleAnimation;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;
import android.os.Handler;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * LoadingCircle handles the loading circle for SodiumEditor.
 * This includes:
 * - Loading circle visibility
 * - Loading circle rendering
 * - Delegates animation to LoadingCircleAnimation
 */
public class LoadingCircle {

    // Paint and rects
    public final Paint loadingCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF loadingCircleRect = new RectF();

    public boolean showLoadingCircle = false;
    public float loadingCircleRadius = 40f;
    public int loadingCircleColor = 0xFF3F51B5;
    public float loadingCircleStrokeWidth = 8f;

    // Animation delegate
    public final LoadingCircleAnimation animation;

    public boolean showLoadingOnFileOpen = true;
    public boolean isInitialFileOpenLoading = false;
    public int initialFileOpenToken = 0;
    @Nullable public Runnable initialFileOpenShowSpinner;
    public final java.util.ArrayList<Runnable> initialLoadCallbacks = new java.util.ArrayList<>();
    public int maxWidthRecalcToken = 0;
    public static final int LARGE_EDIT_LINES = 8000; // show spinner/disable for very large edits
    public final AtomicInteger largeEditUiToken = new AtomicInteger(0);
    public final Runnable largeEditUiWatchdog =
        new Runnable() {
          @Override
          public void run() {
            // Safety: never allow spinner/disable to get stuck forever
            endLargeEditUi(false);
          }
        };

    private final SodiumEditor editor;

    public LoadingCircle(SodiumEditor editor) {
        this.editor = editor;
        this.animation = new LoadingCircleAnimation(editor);

        // Initialize paint
        loadingCirclePaint.setStyle(Paint.Style.STROKE);
        loadingCirclePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Show or hide loading circle
     */
    public void setShow(boolean show) {
        showLoadingCircle = show;
        if (show) {
            animation.startRotation();
        } else {
            animation.stopRotation();
        }
        editor.invalidate();
    }

    /**
     * Draw loading circle on canvas
     */
    public void drawLoadingCircle(Canvas canvas) {
        if (!showLoadingCircle) {
            return;
        }

        float centerX = canvas.getWidth() * 0.5f;
        float centerY = canvas.getHeight() * 0.5f;

        loadingCirclePaint.setColor(loadingCircleColor);
        loadingCirclePaint.setStrokeWidth(loadingCircleStrokeWidth);

        canvas.save();
        canvas.rotate(animation.loadingCircleRotation, centerX, centerY);

        loadingCircleRect.set(
            centerX - loadingCircleRadius,
            centerY - loadingCircleRadius,
            centerX + loadingCircleRadius,
            centerY + loadingCircleRadius);

        canvas.drawArc(loadingCircleRect, 0, 270, false, loadingCirclePaint);
        canvas.restore();
    }

    /**
     * Check if loading circle is visible
     */
    public boolean isVisible() {
        return showLoadingCircle;
    }

    /**
     * Cancel animation and cleanup (delegated)
     */
    public void cancel() {
        animation.cancel();
    }

    // Getters and Setters

    public void setLoadingCircleRadius(float radius) {
        if (radius <= 0f) return;
        loadingCircleRadius = radius;
    }

    public void setLoadingCircleColor(int color) {
        loadingCircleColor = color;
    }

    public void setLoadingCircleStrokeWidth(float width) {
        if (width <= 0f) return;
        loadingCircleStrokeWidth = width;
    }

    public boolean isAnimating() {
        return animation.isAnimating();
    }

    public float getRotation() {
        return animation.getRotation();
    }

    public void showLoadingCircle(boolean show) {
        showLoadingCircle = show;
        if (show) {
            animation.startRotation();
        } else {
            animation.stopRotation();
        }
        editor.invalidate();
    }

    public void setShowLoadingOnFileOpen(boolean enabled) {
        showLoadingOnFileOpen = enabled;
    }

    public boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike) {
        int span = Math.abs(eL - sL) + 1;
        return isSelectAllLike || span >= LARGE_EDIT_LINES;
    }

    public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
        if (!enable) return;
        if (!shouldShowLargeEditUi(sL, eL, isSelectAllLike)) return;

        final int token = largeEditUiToken.incrementAndGet();
        editor.setDisable(true);
        showLoadingCircle(true);

        // Watchdog: force hide after a short time in case any path forgets to hide.
        editor.caret.mainHandler.removeCallbacks(largeEditUiWatchdog);
        editor.caret.mainHandler.postDelayed(largeEditUiWatchdog, 1500);

        // Also ensure token validity for later hides.
        editor.post(
            () -> {
              if (token != largeEditUiToken.get()) return;
            });
    }

    public void endLargeEditUi(boolean invalidate) {
        // Advance token so any pending watchdog is ignored, then hide.
        largeEditUiToken.incrementAndGet();
        editor.caret.mainHandler.removeCallbacks(largeEditUiWatchdog);
        editor.setDisable(false);
        showLoadingCircle(false);
        if (invalidate) editor.invalidate();
    }
}
