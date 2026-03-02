package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.HighlightSpan;
import com.yn.sodiumeditor.state.InlinePredictionState;

import java.util.List;

/**
 * Engine class for inline predictions.
 * Handles finding and suggesting words and paths.
 */
public class InlinePredictionEngine {

    private final SodiumEditor view;
    private final InlinePredictionState state;
    private final WordTrie wordTrie;
    private final PathPredictionEngine pathEngine;

    public InlinePredictionEngine(SodiumEditor view, InlinePredictionState state, WordTrie wordTrie, PathPredictionEngine pathEngine) {
        this.view = view;
        this.state = state;
        this.wordTrie = wordTrie;
        this.pathEngine = pathEngine;
    }

    public void updateSuggestion() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            view.post(this::updateSuggestion);
            return;
        }
        view.imeManager.updateImeSelection();
        long now = android.os.SystemClock.uptimeMillis();
        if (now - state.lastSuggestionUpdateUptime < state.getSuggestionUpdateDebounceMs()) {
            if (!state.suggestionUpdateScheduled) {
                state.suggestionUpdateScheduled = true;
                view.mainHandler.postDelayed(() -> {
                    state.suggestionUpdateScheduled = false;
                    state.lastSuggestionUpdateUptime = android.os.SystemClock.uptimeMillis();
                    updateSuggestionInternal();
                }, state.getSuggestionUpdateDebounceMs());
            }
            return;
        }
        state.lastSuggestionUpdateUptime = now;
        updateSuggestionInternal();
    }

    private void updateSuggestionInternal() {
        String line = view.getLineTextForRender(view.cursorState.getCursorLine());
        if (line == null) {
            state.clearActiveSuggestion();
            return;
        }

        if (!state.isAutoPathCompletionEnabled() && !state.isAutoCompletionEnabled()) {
            state.clearActiveSuggestion();
            return;
        }

        // Do not show suggestions if the cursor is in the middle of a word
        if (view.cursorState.getCursorChar() < line.length()
                && Character.isLetterOrDigit(line.charAt(view.cursorState.getCursorChar()))) {
            state.clearActiveSuggestion();
            return;
        }

        // Do not show suggestions if there is non-whitespace text after the cursor
        if (view.cursorState.getCursorChar() < line.length()
                && !line.substring(view.cursorState.getCursorChar()).trim().isEmpty()) {
            state.clearActiveSuggestion();
            return;
        }

        String pathFragment = "";
        String wordFragment = "";
        if (state.isAutoPathCompletionEnabled()) {
            pathFragment = getCurrentPathFragment(line);
        }
        if (state.isAutoCompletionEnabled() && pathFragment.isEmpty()) {
            wordFragment = getCurrentWordFragment(line);
        }
        if (pathFragment.isEmpty() && wordFragment.isEmpty()) {
            state.clearActiveSuggestion();
            return;
        }

        // Prevent suggestions inside syntax highlighting (expensive).
        List<HighlightSpan> spans =
                view.highlightState.highlightCache.get(view.cursorState.getCursorLine());
        if (spans == null) {
            spans = view.highlightRenderer.calculateSpansForLine(line, view.cursorState.getCursorLine());
            view.highlightState.highlightCache.put(view.cursorState.getCursorLine(), spans);
        }
        for (HighlightSpan span : spans) {
            if (view.cursorState.getCursorChar() > span.start
                    && view.cursorState.getCursorChar() <= span.end) {
                state.clearActiveSuggestion();
                return;
            }
        }

        if (!pathFragment.isEmpty()) {
            String suggestion = pathEngine.findPathSuggestion(pathFragment, new String[]{state.lastPathQuery, state.lastPathSuggestion});
            if (suggestion != null && suggestion.length() > pathFragment.length()) {
                state.activeSuggestion = suggestion.substring(pathFragment.length());
                state.activeSuggestionLine = view.cursorState.getCursorLine();
                state.activeSuggestionCharStart = view.cursorState.getCursorChar() - pathFragment.length();
                state.activeSuggestionWordFragment = pathFragment;
                state.activeSuggestionIsPath = true;
                state.lastPathQuery = pathFragment;
                state.lastPathSuggestion = suggestion;
            } else {
                state.clearActiveSuggestion();
            }
            view.invalidate();
            return;
        }

        if (wordFragment.isEmpty()) {
            state.clearActiveSuggestion();
            return;
        }

        String suggestion = wordTrie.findFirstSuggestion(wordFragment);
        if (suggestion != null && suggestion.length() > wordFragment.length()) {
            state.activeSuggestion = suggestion.substring(wordFragment.length());
            state.activeSuggestionLine = view.cursorState.getCursorLine();
            state.activeSuggestionCharStart = view.cursorState.getCursorChar() - wordFragment.length();
            state.activeSuggestionWordFragment = wordFragment;
            state.activeSuggestionIsPath = false;
        } else {
            state.clearActiveSuggestion();
        }
        view.invalidate();
    }

    private String getCurrentWordFragment(String line) {
        if (view.cursorState.getCursorChar() == 0
                || view.cursorState.getCursorChar() > line.length()) {
            return "";
        }
        int start = view.cursorState.getCursorChar();
        while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
            start--;
        }
        return line.substring(start, view.cursorState.getCursorChar());
    }

    private String getCurrentPathFragment(String line) {
        return pathEngine.getCurrentPathFragment(line, view.cursorState.getCursorChar());
    }

    public void setSuggestions(@Nullable java.util.List<String> keywords, int color) {
        wordTrie.clear();
        if (keywords != null) {
            for (String word : keywords) {
                wordTrie.insert(word);
            }
        }
        state.clearActiveSuggestion();
    }

    public void acceptAutoCompletion() {
        if (!state.hasActiveSuggestion()) return;
        if (state.activeSuggestionIsPath && !state.isAutoPathCompletionEnabled()) return;
        if (!state.activeSuggestionIsPath && !state.isAutoCompletionEnabled()) return;

        view.cursorState.setCursorPosition(view.cursorState.getCursorLine(), view.cursorState.getCursorChar());
        view.imeCompositionHandler.commitComposing(false);

        state.suggestionAcceptedThisTouch = true;

        String textToInsert = state.activeSuggestion;
        state.clearActiveSuggestion();
        view.selectionState.clearSelectionKeepLineNumberState();
        view.insertStringAtCursorForSuggestion(textToInsert);
        view.restartInputForSuggestion();
    }

    public boolean maybeAcceptSuggestionTap(float ex, float ey, int line, boolean isEmptyArea) {
        boolean allowSuggestionTap =
                state.activeSuggestionIsPath ? state.isAutoPathCompletionEnabled() : state.isAutoCompletionEnabled();
        if (!allowSuggestionTap || !state.hasActiveSuggestion() || state.activeSuggestionRect.isEmpty()) {
            return false;
        }

        if (state.activeSuggestionRect.contains(ex, ey)) {
            acceptAutoCompletion();
            return true;
        } else if (isEmptyArea && line == state.activeSuggestionLine) {
            acceptAutoCompletion();
            return true;
        }
        return false;
    }
}
