package com.yn.sodiumeditor.input;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.state.CursorState;

/**
 * Handler class for IME composing text.
 * Manages composing text insertion, replacement, and commit operations.
 */
public final class ImeCompositionHandler {

    private final CursorState state;
    private final CompositionCallback callback;

    public interface CompositionCallback {
        boolean isReadOnly();
        void invalidatePendingIOForEdit();
        int incrementEditVersion();
        void ensureLineInWindow(int line, boolean center);
        boolean isWindowLoading();
        int getWindowStartLine();
        java.util.List<String> getLinesWindow();
        String getLineFromWindowLocal(int local);
        void updateLocalLine(int local, String newLine);
        java.util.HashMap<Integer, String> getModifiedLines();
        void computeWidthForLine(int line, String lineText);
        void recalculateMaxLineWidth();
        void invalidate();
        void autoSuggestionUpdate();
        void clearComposingPendingOp();
        void clearLastComposingTextForCharAnim();
        Paint getPaintForChar(int line, int at, String base);
        void startDeleteAnimation(int line, int at, String removed, Paint paint);
        boolean isCharAnimationEnabled();
        CursorState getCursorState();
        void setCursorPosition(int line, int ch);
    }

    public ImeCompositionHandler(CursorState state, CompositionCallback callback) {
        this.state = state;
        this.callback = callback;
    }

    public void deleteComposing() {
        if (!state.hasComposing()) return;
        replaceComposingWith("");
        state.setHasComposing(false);
        state.setComposingLength(0);
        state.setComposingStartActive(false);
        callback.clearLastComposingTextForCharAnim();
    }

    public void commitComposing(boolean keepInText) {
        if (!state.hasComposing()) return;
        state.setHasComposing(false);
        state.setComposingLength(0);
        state.setComposingStartActive(false);
        callback.clearComposingPendingOp();
        callback.clearLastComposingTextForCharAnim();
        callback.invalidate();
        callback.autoSuggestionUpdate();
    }

    public void replaceComposingWith(@Nullable CharSequence textSeq) {
        if (callback.isReadOnly()) return;
        callback.invalidatePendingIOForEdit();
        callback.incrementEditVersion();

        int composingLine = state.getComposingLine();
        callback.ensureLineInWindow(composingLine, true);
        
        if (callback.isWindowLoading()
                && (composingLine < callback.getWindowStartLine() || composingLine >= callback.getWindowStartLine() + callback.getLinesWindow().size())) {
            // Post to main thread to retry after window is loaded
            return;
        }
        
        int local = composingLine - callback.getWindowStartLine();
        synchronized (callback.getLinesWindow()) {
            String base = callback.getLineFromWindowLocal(local);
            if (base == null) base = "";
            int start = Math.max(0, Math.min(state.getComposingOffset(), base.length()));
            int end = Math.max(0, Math.min(state.getComposingOffset() + state.getComposingLength(), base.length()));
            
            if (callback.isCharAnimationEnabled()) {
                String oldComposing = base.substring(start, end);
                String newComposing = (textSeq == null) ? "" : textSeq.toString();
                if (newComposing.length() < oldComposing.length()) {
                    String removed = null;
                    int at = start;
                    if (oldComposing.startsWith(newComposing)) {
                        removed = oldComposing.substring(newComposing.length());
                        at = start + newComposing.length();
                    } else if (oldComposing.endsWith(newComposing)) {
                        removed = oldComposing.substring(0, oldComposing.length() - newComposing.length());
                        at = start;
                    }

                    if (removed != null && !removed.isEmpty()) {
                        Paint p = callback.getPaintForChar(composingLine, at, base);
                        callback.startDeleteAnimation(composingLine, at, removed, p);
                    }
                }
            }
            
            String newLine = base.substring(0, start) + (textSeq != null ? textSeq.toString() : "") + base.substring(end);
            callback.updateLocalLine(local, newLine);
            callback.getModifiedLines().put(composingLine, newLine);
            state.setComposingLength(textSeq != null ? textSeq.length() : 0);
            callback.setCursorPosition(composingLine, state.getComposingOffset() + state.getComposingLength());
            callback.computeWidthForLine(composingLine, newLine);
            callback.recalculateMaxLineWidth();
            callback.invalidate();
        }
        callback.autoSuggestionUpdate();
    }
}
