package com.yn.sodiumeditor.input;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.renderer.FoldRenderer;
import com.yn.sodiumeditor.state.FoldRange;
import com.yn.sodiumeditor.state.FoldState;

/**
 * Input handler for code folding touch interactions.
 * Handles fold toggling and placeholder hit testing.
 */
public class FoldTouchHandler {

    private final SodiumEditorView view;
    private final FoldState state;
    private final FoldRenderer renderer;

    public FoldTouchHandler(SodiumEditorView view, FoldState state, FoldRenderer renderer) {
        this.view = view;
        this.state = state;
        this.renderer = renderer;
    }

    public boolean toggleFoldAtLine(int line) {
        if (!state.isCodeFoldingEnabled()) return false;
        FoldRange existing = state.getFoldRange(line);
        if (existing != null) {
            existing.collapsed = !existing.collapsed;
            state.foldIntervalsDirty = true;
            view.invalidate();
            return true;
        }

        FoldRange created = view.foldEngine.findFoldRangeForLine(line);
        if (created == null) return false;
        created.collapsed = true;
        state.putFoldRange(created.startLine, created);
        if (created.isIndentFold) view.indentGuideEngine.markIntervalsDirty();
        view.invalidate();
        return true;
    }

    public boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
        return renderer.isFoldPlaceholderHit(globalLine, line, localX);
    }

    public void startFoldMarkerRipple(int line) {
        renderer.startFoldMarkerRipple(line);
    }

    public void clearFoldRipple() {
        state.clearFoldRipple();
    }

    public void removeIndentFolds() {
        state.removeIndentFolds();
    }

    public void clearAllFolds() {
        state.clear();
    }

    public boolean hasFoldRanges() {
        return state.hasFoldRanges();
    }
}
