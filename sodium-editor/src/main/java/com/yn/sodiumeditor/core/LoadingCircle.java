package com.yn.sodiumeditor.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.LoadingCircleAnimation;

/**
 * Main facade for loading circle management. Delegates logic to LoadingCircleAnimation.
 */
public class LoadingCircle {
    public final LoadingCircleAnimation animation;
    private final SodiumEditor editor;

    // --- State (Delegated or aliased for compatibility) ---
    public final Paint loadingCirclePaint;
    public boolean showLoadingCircle;
    public float loadingCircleRadius;
    public int loadingCircleColor;
    public float loadingCircleStrokeWidth;
    
    public boolean showLoadingOnFileOpen;
    public boolean isInitialFileOpenLoading;
    public int initialFileOpenToken;
    public int maxWidthRecalcToken;
    public static final int LARGE_EDIT_LINES = LoadingCircleAnimation.LARGE_EDIT_LINES;

    public LoadingCircle(SodiumEditor editor) {
        this.editor = editor;
        this.animation = new LoadingCircleAnimation(editor);
        this.loadingCirclePaint = animation.paint;
        syncFromAnimation();
    }

    private void syncFromAnimation() {
        showLoadingCircle = animation.showLoadingCircle;
        loadingCircleRadius = animation.radius;
        loadingCircleColor = animation.color;
        loadingCircleStrokeWidth = animation.strokeWidth;
        showLoadingOnFileOpen = animation.showLoadingOnFileOpen;
        isInitialFileOpenLoading = animation.isInitialFileOpenLoading;
        initialFileOpenToken = animation.initialFileOpenToken;
        maxWidthRecalcToken = animation.maxWidthRecalcToken;
    }

    private void syncToAnimation() {
        animation.showLoadingCircle = showLoadingCircle;
        animation.radius = loadingCircleRadius;
        animation.color = loadingCircleColor;
        animation.strokeWidth = loadingCircleStrokeWidth;
        animation.showLoadingOnFileOpen = showLoadingOnFileOpen;
        animation.isInitialFileOpenLoading = isInitialFileOpenLoading;
        animation.initialFileOpenToken = initialFileOpenToken;
        animation.maxWidthRecalcToken = maxWidthRecalcToken;
    }

    // ==============================
    // Bridge Methods
    // ==============================

    public void setShow(boolean show) { showLoadingCircle(show); }
    public void showLoadingCircle(boolean show) {
        showLoadingCircle = show; syncToAnimation();
        if (show) animation.startRotation();
        else animation.stopRotation();
        editor.invalidate();
    }

    public void drawLoadingCircle(Canvas canvas) { syncToAnimation(); animation.draw(canvas); }
    public boolean isVisible() { return showLoadingCircle; }
    public void cancel() { animation.cancel(); syncFromAnimation(); }
    public boolean isAnimating() { return animation.isAnimating(); }
    public float getRotation() { return animation.rotation; }

    public void beginLargeEditUiIfNeeded(boolean enable, int sL, int eL, boolean isSelectAllLike) {
        syncToAnimation();
        animation.beginLargeEditUiIfNeeded(enable, sL, eL, isSelectAllLike);
        syncFromAnimation();
    }

    public void endLargeEditUi(boolean invalidate) {
        animation.endLargeEditUi(invalidate);
        syncFromAnimation();
    }

    // Setters
    public void setLoadingCircleRadius(float r) { if (r > 0) loadingCircleRadius = r; syncToAnimation(); }
    public void setLoadingCircleColor(int c) { loadingCircleColor = c; syncToAnimation(); }
    public void setLoadingCircleStrokeWidth(float w) { if (w > 0) loadingCircleStrokeWidth = w; syncToAnimation(); }
    public void setShowLoadingOnFileOpen(boolean e) { showLoadingOnFileOpen = e; syncToAnimation(); }
}
