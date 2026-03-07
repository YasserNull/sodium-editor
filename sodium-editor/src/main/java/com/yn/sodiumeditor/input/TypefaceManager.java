package com.yn.sodiumeditor.input;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.config.EditorConfig;
import com.yn.sodiumeditor.core.HighlightRule;
import com.yn.sodiumeditor.renderer.FoldRenderer;
import com.yn.sodiumeditor.renderer.LineNumberRenderer;
import com.yn.sodiumeditor.renderer.PopupMenuRenderer;
import com.yn.sodiumeditor.renderer.WrapWordIndicatorRender;
import com.yn.sodiumeditor.renderer.WhitespaceGuideRenderer;
import com.yn.sodiumeditor.renderer.InlinePredictionRenderer;
import com.yn.sodiumeditor.state.HighlightState;

/**
 * Manager class for typeface operations.
 * Handles applying typeface changes across all renderers and components.
 */
public final class TypefaceManager {

    private final SodiumEditor view;
    private final Handler mainHandler;

    public TypefaceManager(SodiumEditor view) {
        this.view = view;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Applies a typeface to the editor and all related components.
     * 
     * @param typeface the typeface to apply (null for default)
     * @param style the style (bold, italic, etc.)
     */
    public void applyTypeface(@Nullable Typeface typeface, int style) {
        // Check if we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final Typeface tf = typeface;
            final int st = style;
            mainHandler.post(() -> applyTypeface(tf, st));
            return;
        }

        // Determine base typeface
        Typeface safeBase = (typeface != null) ? typeface : Typeface.DEFAULT;
        view.editorConfig.baseTypeface = safeBase;

        // Convert style to Typeface style
        int typefaceStyle = getTypefaceStyle(style);

        // Create final typeface and apply to paint
        Typeface finalTypeface = Typeface.create(safeBase, typefaceStyle);
        view.editorConfig.paint.setTypeface(finalTypeface);

        // Update all renderers
        updateRenderers(finalTypeface, safeBase);

        // Update highlight rules
        updateHighlightRules(safeBase);

        // Clear caches and recalculate metrics
        clearCachesAndRecalculate();

        // Request layout and invalidate
        view.requestLayout();
        rebuildWordWrapIfNeeded();
        view.invalidate();
    }

    private int getTypefaceStyle(int style) {
        switch (style) {
            case EditorConfig.STYLE_BOLD:
                return Typeface.BOLD;
            case EditorConfig.STYLE_ITALIC:
                return Typeface.ITALIC;
            case EditorConfig.STYLE_BOLD_ITALIC:
                return Typeface.BOLD_ITALIC;
            default:
                return Typeface.NORMAL;
        }
    }

    private void updateRenderers(Typeface finalTypeface, Typeface safeBase) {
        // Update inline prediction renderer
        view.inlinePredictionRenderer.onEditorTypefaceChanged(finalTypeface);

        // Update line number renderer
        view.lineNumberRenderer.setTypeface(finalTypeface);

        // Update fold renderer
        view.foldRenderer.foldMarkerPaint.setTypeface(finalTypeface);

        // Update wrap word indicator
        view.wrapWordIndicatorRender.updateTypeface(view.editorConfig.paint);

        // Update whitespace guide renderer
        view.whitespaceGuideRenderer.updateTypeface(view.editorConfig.paint);

        // Update popup menu renderer
        view.popupMenuRenderer.onEditorTypefaceChanged(finalTypeface);

        // Update whitespace guide rule typeface
        view.whitespaceGuideRenderer.updateRuleTypeface(
            safeBase,
            view.highlightState.stringHighlightRule,
            view.highlightState.blockCommentHighlightRule);
    }

    private void updateHighlightRules(Typeface safeBase) {
        // Update line comment highlight rule
        if (view.highlightState.lineCommentHighlightRule != null) {
            view.highlightState.lineCommentHighlightRule.updateTypeface(safeBase);
        }

        // Update all highlight rules
        for (HighlightRule rule : view.highlightState.highlightRules) {
            rule.updateTypeface(safeBase);
        }

        // Clear highlight caches
        view.highlightState.clearHighlightCaches();
    }

    private void clearCachesAndRecalculate() {
        // Update line height
        view.editorConfig.lineHeight = view.editorConfig.paint.getFontSpacing();

        // Update whitespace guide metrics
        view.whitespaceGuideRenderer.updateMetrics(view.editorConfig.paint, view.editorConfig.visualConfig.WHITESPACE_GUIDE_SPACE, view.editorConfig.visualConfig.WHITESPACE_GUIDE_TAB);

        // Invalidate line number cache
        view.lineNumberRenderer.invalidateCache();

        // Clear line width cache
        synchronized (view.editorState.lineWidthCache) {
            view.editorState.lineWidthCache.clear();
        }

        // Reset max width metrics
        view.editorState.currentMaxWindowLineWidth = 0f;
        view.editorState.globalMaxLineWidth = 0f;
        view.scrollManager.maxLineWidthForScroll = 0f;
        view.scrollManager.maxTextStartXForScroll = 0f;
        view.scrollManager.maxScrollXForScroll = 0f;

        // Recalculate max line width
        view.viewRender.textRender.recalculateMaxLineWidth();
    }

    private void rebuildWordWrapIfNeeded() {
        if (view.wrapWordState.isWordWrapEnabled) {
            view.wrapWordBuilder.invalidate(true, true);
            view.wrapWordBuilder.requestPrefixRebuild(view);
        }
    }
}
